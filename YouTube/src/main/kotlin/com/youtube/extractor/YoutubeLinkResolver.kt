package com.youtube.extractor

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

object YoutubeLinkResolver {
    private const val TAG = "YoutubeLinkResolver"

    data class ParsedAudioTrack(
        val stream: AudioStream,
        val languageName: String,
        val trackTypeTag: String,
        val bitrateKbps: String,
        val codec: String,
        val fullLabel: String,
        val isOriginal: Boolean,
        val isDubbed: Boolean
    )

    fun extractPixelHeight(resolutionStr: String?, heightInt: Int = 0): Int {
        if (heightInt > 0) return heightInt
        if (resolutionStr.isNullOrBlank()) return 0
        val cleaned = resolutionStr.trim().lowercase()
        return when {
            cleaned.contains("4320") || cleaned.contains("8k") -> 4320
            cleaned.contains("2160") || cleaned.contains("4k") -> 2160
            cleaned.contains("1440") || cleaned.contains("2k") -> 1440
            cleaned.contains("1080") -> 1080
            cleaned.contains("720") -> 720
            cleaned.contains("480") -> 480
            cleaned.contains("360") -> 360
            cleaned.contains("240") -> 240
            cleaned.contains("144") -> 144
            else -> {
                val match = Regex("""(\d{3,4})""").find(cleaned)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }
        }
    }

    fun parsePreferredTargetHeight(pref: String?): Int? {
        if (pref.isNullOrBlank() || pref.equals("Auto", ignoreCase = true) || pref.contains("auto", ignoreCase = true)) {
            return null
        }
        val height = extractPixelHeight(pref)
        return if (height > 0) height else null
    }

    fun heightToQuality(height: Int): Int {
        return when {
            height >= 2160 -> 2160
            height >= 1440 -> 1440
            height >= 1080 -> 1080
            height >= 720 -> 720
            height >= 480 -> 480
            height >= 360 -> 360
            height >= 240 -> 240
            else -> 144
        }
    }

    fun formatResolutionLabel(height: Int): String {
        return when (height) {
            4320 -> "4320p (8K)"
            2160 -> "2160p (4K)"
            1440 -> "1440p (2K)"
            1080 -> "1080p"
            720 -> "720p"
            480 -> "480p"
            360 -> "360p"
            240 -> "240p"
            144 -> "144p"
            else -> if (height > 0) "${height}p" else "Video"
        }
    }

    fun parseAudioStream(stream: AudioStream): ParsedAudioTrack {
        val lang = stream.audioTrackName?.takeIf { it.isNotBlank() }
            ?: stream.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
            ?: "English"

        val typeTag = when (stream.audioTrackType) {
            AudioTrackType.ORIGINAL -> " [ORIGINAL]"
            AudioTrackType.DUBBED -> " [DUBBED]"
            AudioTrackType.DESCRIPTIVE -> " [DESCRIPTIVE]"
            AudioTrackType.SECONDARY -> " [SECONDARY]"
            else -> ""
        }

        val br = if (stream.averageBitrate > 0) stream.averageBitrate else stream.bitrate
        val kbps = if (br > 1000) "${br / 1000}kbps" else if (br > 0) "${br}kbps" else ""
        val codec = (stream.format?.name ?: stream.codec ?: "M4A").uppercase()
        val audioDetails = listOfNotNull(kbps.takeIf { it.isNotBlank() }, codec.takeIf { it.isNotBlank() }).joinToString(" ")
        val fullLabel = "$lang$typeTag" + (if (audioDetails.isNotBlank()) " ($audioDetails)" else "")

        return ParsedAudioTrack(
            stream = stream,
            languageName = lang,
            trackTypeTag = typeTag,
            bitrateKbps = kbps,
            codec = codec,
            fullLabel = fullLabel,
            isOriginal = stream.audioTrackType == AudioTrackType.ORIGINAL,
            isDubbed = stream.audioTrackType == AudioTrackType.DUBBED
        )
    }

    fun selectBestAudio(
        audioList: List<ParsedAudioTrack>,
        preferredLang: String?
    ): ParsedAudioTrack? {
        if (audioList.isEmpty()) return null

        // 1. If preferred language matches and is not None / Auto
        if (!preferredLang.isNullOrBlank() && !preferredLang.equals("None", ignoreCase = true) && !preferredLang.equals("Auto", ignoreCase = true)) {
            val matchingPref = audioList.filter { it.languageName.contains(preferredLang, ignoreCase = true) }
            if (matchingPref.isNotEmpty()) {
                return matchingPref.maxByOrNull {
                    if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
                }
            }
        }

        // 2. Original track
        val originalTracks = audioList.filter { it.isOriginal }
        if (originalTracks.isNotEmpty()) {
            return originalTracks.maxByOrNull {
                if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
            }
        }

        // 3. Prefer M4A / AAC with highest bitrate (best ExoPlayer hardware decoder compatibility)
        val m4aTracks = audioList.filter { it.codec.contains("M4A") || it.codec.contains("AAC") || it.codec.contains("MP4") }
        if (m4aTracks.isNotEmpty()) {
            return m4aTracks.maxByOrNull {
                if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
            }
        }

        // 4. Any track with highest bitrate
        return audioList.maxByOrNull {
            if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
        }
    }

    suspend fun resolveLinks(
        providerName: String,
        info: StreamInfo,
        preferredQuality: String?,
        preferredSubtitleLang: String?,
        defaultHeaders: Map<String, String>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val targetHeight = parsePreferredTargetHeight(preferredQuality)
        val generatedLinks = mutableListOf<ExtractorLink>()

        // A. Live Streams: HLS Master Playlist
        try {
            val isLiveStream = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
            if (isLiveStream && !info.hlsUrl.isNullOrBlank()) {
                generatedLinks.add(
                    newExtractorLink(
                        source = providerName,
                        name = "$providerName Live (Adaptive HLS)",
                        url = info.hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.headers = defaultHeaders
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve live HLS stream: ${t.message}")
        }

        // B. Process Audio Streams
        val rawAudioStreams = info.audioStreams.orEmpty().filter { !it.content.isNullOrBlank() }
        val parsedAudioTracks = rawAudioStreams.map { parseAudioStream(it) }
        val primaryAudio = selectBestAudio(parsedAudioTracks, preferredSubtitleLang)
        val distinctAudioTracks = parsedAudioTracks.distinctBy { Pair(it.languageName, it.trackTypeTag) }

        // C. Adaptive DASH Manifest Stream (Full HD, 2K, 4K with synchronized audio)
        try {
            if (!info.dashMpdUrl.isNullOrBlank()) {
                val dashQuality = targetHeight ?: 1080
                val link = newExtractorLink(
                    source = providerName,
                    name = "$providerName Adaptive (DASH)",
                    url = info.dashMpdUrl,
                    type = ExtractorLinkType.DASH
                ) {
                    this.headers = defaultHeaders
                    this.quality = dashQuality
                }
                generatedLinks.add(link)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DASH stream generation failed: ${t.message}")
        }

        // D. Progressive Streams (pre-muxed audio + video - 100% reliable on all Android devices)
        val supportsSeparateAudio = AudioTrackHelper.isSupported()
        val allAudioTrackPairs = distinctAudioTracks.map { it.stream.content to defaultHeaders }

        try {
            val rawVideoStreams = info.videoStreams.orEmpty().filter { !it.content.isNullOrBlank() }
            for (stream in rawVideoStreams) {
                val height = extractPixelHeight(stream.resolution, stream.height)
                if (height <= 0) continue
                val resLabel = formatResolutionLabel(height)
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val formatTag = (stream.format?.name ?: "MP4").uppercase()
                val qualityNum = heightToQuality(height)

                val link = newExtractorLink(
                    source = providerName,
                    name = "$providerName $resLabel$fpsTag ($formatTag)",
                    url = stream.content,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.headers = defaultHeaders
                    this.quality = qualityNum
                }

                if (supportsSeparateAudio && allAudioTrackPairs.isNotEmpty()) {
                    AudioTrackHelper.attachAudioTracks(link, allAudioTrackPairs)
                }
                generatedLinks.add(link)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Progressive stream generation failed: ${t.message}")
        }

        // E. Video-Only Streams (handled safely: attach audio if supported; prioritize WebM/VP9 to prevent MediaCodec ERROR_CODE_DECODER_INIT_FAILED 4001)
        try {
            val rawVideoOnlyStreams = info.videoOnlyStreams.orEmpty().filter { !it.content.isNullOrBlank() }
            for (stream in rawVideoOnlyStreams) {
                val height = extractPixelHeight(stream.resolution, stream.height)
                if (height <= 0) continue

                val formatRaw = (stream.format?.name ?: stream.codec ?: "MP4").uppercase()
                val isWebmOrVp9 = formatRaw.contains("WEBM") || formatRaw.contains("VP9") ||
                    formatRaw.contains("VP09") || (stream.codec?.contains("vp", ignoreCase = true) == true) ||
                    (stream.codec?.contains("av01", ignoreCase = true) == true) ||
                    (stream.codec?.contains("av1", ignoreCase = true) == true)
                val isMp4 = !isWebmOrVp9 && (formatRaw.contains("MP4") || formatRaw.contains("AVC") ||
                    formatRaw.contains("H264") || (stream.codec?.contains("avc", ignoreCase = true) == true))

                val resLabel = formatResolutionLabel(height)
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val qualityNum = heightToQuality(height)
                val muteTag = if (!supportsSeparateAudio) " [Video Only]" else ""

                // Fragmented MP4 video-only chunks from YouTube lack progressive SPS/PPS container headers
                // and trigger ERROR_CODE_DECODER_INIT_FAILED (4001) in ExoPlayer MediaCodecVideoRenderer.
                // WebM (VP9) streams have valid container keyframes and initialize MediaCodec reliably on all devices.
                // We mark MP4 video-only streams with [External/VLC] so ExoPlayer chooses WebM,
                // while users using external VLC can still access the MP4 link.
                val formatLabel = if (isWebmOrVp9) "WebM" else "MPEG-4"
                val vlcTag = if (isMp4) " [External/VLC]" else ""

                val primaryLinkName = "$providerName $resLabel$fpsTag ($formatLabel)$muteTag$vlcTag"
                val link = newExtractorLink(
                    source = providerName,
                    name = primaryLinkName,
                    url = stream.content,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.headers = defaultHeaders
                    this.quality = qualityNum
                }

                if (supportsSeparateAudio && allAudioTrackPairs.isNotEmpty()) {
                    AudioTrackHelper.attachAudioTracks(link, allAudioTrackPairs)
                }
                generatedLinks.add(link)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Video-only stream processing skipped or failed: ${t.message}")
        }

        // F. Audio-Only / Background Playback Streams (Music & Podcasts)
        try {
            for (audio in parsedAudioTracks) {
                val audioUrl = audio.stream.content
                if (audioUrl.isNullOrBlank()) continue
                val audioLink = newExtractorLink(
                    source = providerName,
                    name = "$providerName [Audio Only] ${audio.fullLabel}",
                    url = audioUrl,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.headers = defaultHeaders
                    this.quality = Qualities.Unknown.value
                }
                generatedLinks.add(audioLink)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Audio-only stream extraction failed: ${t.message}")
        }

        // G. Prioritize & Sort Links
        // Strictly honor user preferred resolution:
        // - In Audio-Only mode, Audio links receive top priority (300,000)
        // - Exact match in WebM gets highest priority
        // - Adaptive DASH manifest prioritized for Auto
        // - MP4 video-only streams placed below WebM to avoid decoder init crash in ExoPlayer
        // - Lower resolutions are safe fallbacks
        val isAudioOnlyMode = com.youtube.YoutubePreferences.audioOnlyMode
        fun scoreLink(link: ExtractorLink): Int {
            val isAudio = link.name.contains("[Audio Only]")
            if (isAudioOnlyMode && isAudio) {
                return 300000 + (if (link.name.contains("M4A")) 1000 else 500)
            }
            if (isAudio) return 1000

            if (link.type == ExtractorLinkType.M3U8) return 200000
            val isMute = link.name.contains("[Video Only]")
            val isExternalVlc = link.name.contains("[External/VLC]")
            val isWebm = link.name.contains("(WebM)")
            val q = link.quality

            // Video-only streams without audio rank lowest
            if (isMute) return q

            // MP4 video-only streams trigger ERROR_CODE_DECODER_INIT_FAILED (4001) in ExoPlayer,
            // so rank them below ExoPlayer-compatible WebM and DASH streams
            if (isExternalVlc) return 5000 + q

            if (targetHeight == null) {
                // Auto preference: Adaptive DASH manifest first, then highest WebM
                return when {
                    link.type == ExtractorLinkType.DASH -> 150000
                    isWebm -> 20000 + q
                    else -> 10000 + q
                }
            }

            // User chose a specific preferred resolution (e.g. 720p, 1080p, 1440p, 2160p):
            return when {
                // Exact resolution match in WebM gets top priority
                isWebm && q == targetHeight -> 100000
                // DASH adaptive is safe second priority
                link.type == ExtractorLinkType.DASH -> 95000
                // Exact resolution match non-WebM (progressive muxed)
                q == targetHeight -> 90000
                // Lower WebM resolutions are prioritized fallbacks
                isWebm && q < targetHeight -> 50000 + q
                // Other lower resolutions
                q < targetHeight -> 40000 + q
                // Higher than target WebM
                isWebm -> 20000 - q
                // Other higher resolutions
                else -> 10000 - q
            }
        }

        val sortedLinks = generatedLinks.sortedByDescending { scoreLink(it) }
        sortedLinks.forEach { callback(it) }

        // H. SponsorBlock Subtitle Markers
        if (com.youtube.YoutubePreferences.enableSponsorBlock) {
            try {
                val videoId = com.youtube.loader.YoutubeLoaders.extractVideoId(info.url ?: "")
                    ?: com.youtube.loader.YoutubeLoaders.extractVideoId(info.originalUrl ?: "")
                if (!videoId.isNullOrBlank()) {
                    val sponsorSegments = com.youtube.power.SponsorBlockHelper.getSegments(videoId)
                    val vttDataUrl = com.youtube.power.SponsorBlockHelper.generateWebVttDataUrl(sponsorSegments)
                    if (vttDataUrl.isNotBlank()) {
                        subtitleCallback(
                            newSubtitleFile(
                                lang = "⚡ SponsorBlock Markers",
                                url = vttDataUrl
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed generating SponsorBlock subtitle marker: ${e.message}")
            }
        }

        // I. Seekable Chapters as WebVTT Subtitle Track
        try {
            val segments = info.streamSegments
            if (!segments.isNullOrEmpty()) {
                val sb = java.lang.StringBuilder("WEBVTT\n\n")
                for (i in segments.indices) {
                    val seg = segments[i]
                    val startSec = seg.startTimeSeconds.toDouble()
                    val endSec = if (i + 1 < segments.size) {
                        segments[i + 1].startTimeSeconds.toDouble()
                    } else if (info.duration > 0) {
                        info.duration.toDouble()
                    } else {
                        startSec + 30.0
                    }

                    fun formatVttTime(seconds: Double): String {
                        val totalMs = (seconds * 1000).toLong()
                        val hrs = totalMs / 3600000
                        val mins = (totalMs % 3600000) / 60000
                        val secs = (totalMs % 60000) / 1000
                        val ms = totalMs % 1000
                        return String.format("%02d:%02d:%02d.%03d", hrs, mins, secs, ms)
                    }

                    sb.append("${i + 1}\n")
                    sb.append("${formatVttTime(startSec)} --> ${formatVttTime(endSec)}\n")
                    sb.append("${seg.title?.trim() ?: "Chapter ${i + 1}"}\n\n")
                }
                val encoded = java.net.URLEncoder.encode(sb.toString(), "UTF-8").replace("+", "%20")
                val chaptersDataUrl = "data:text/vtt;charset=utf-8,$encoded"
                subtitleCallback(
                    newSubtitleFile(
                        lang = "📖 Seekable Chapters",
                        url = chaptersDataUrl
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed generating chapters subtitle track: ${t.message}")
        }

        // J. Subtitles & Closed Captions
        try {
            val preferredSub = preferredSubtitleLang?.takeIf { !it.equals("None", ignoreCase = true) }
            val sortedSubtitles = info.subtitles.orEmpty().sortedWith(
                compareByDescending<org.schabi.newpipe.extractor.stream.SubtitlesStream> { sub ->
                    if (preferredSub != null && !preferredSub.equals("Auto", ignoreCase = true)) {
                        val subLang = sub.displayLanguageName ?: sub.languageTag ?: ""
                        subLang.contains(preferredSub, ignoreCase = true)
                    } else false
                }.thenBy { it.isAutoGenerated }
            )

            sortedSubtitles.forEach { sub ->
                val langName = sub.displayLanguageName ?: sub.languageTag ?: "Unknown"
                val autoTag = if (sub.isAutoGenerated) " (Auto-generated)" else ""
                val fullLangLabel = "$langName$autoTag"

                subtitleCallback(
                    newSubtitleFile(
                        lang = fullLangLabel,
                        url = sub.content
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Subtitle extraction failed: ${t.message}")
        }
    }
}

