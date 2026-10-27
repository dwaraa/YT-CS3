package com.youtube.extractor

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.youtube.AudioHelper
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

        // B. Process Audio Streams
        val rawAudioStreams = info.audioStreams.orEmpty().filter { !it.content.isNullOrBlank() }
        val parsedAudioTracks = rawAudioStreams.map { parseAudioStream(it) }
        val primaryAudio = selectBestAudio(parsedAudioTracks, preferredSubtitleLang)
        val distinctAudioTracks = parsedAudioTracks.distinctBy { Pair(it.languageName, it.trackTypeTag) }

        // C. Adaptive Playback: Pair Video-Only Streams (4K, 1440p, 1080p, 720p, etc.) with Audio
        val rawVideoOnlyStreams = info.videoOnlyStreams.orEmpty().filter { !it.content.isNullOrBlank() }

        for (stream in rawVideoOnlyStreams) {
            val height = extractPixelHeight(stream.quality, stream.height)
            if (height <= 0) continue

            val resLabel = formatResolutionLabel(height)
            val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
            val formatTag = (stream.format?.name ?: stream.codec ?: "MP4").uppercase()
            val qualityNum = heightToQuality(height)

            val isPreferred = (targetHeight != null && height == targetHeight)
            val prefBadge = if (isPreferred) " ⭐" else ""

            val audioFile = primaryAudio?.let { AudioHelper.createAudioFile(it.stream.content, defaultHeaders) }
            val audioTracksList = if (audioFile != null) listOf(audioFile) else emptyList()

            val primaryLinkName = "$providerName $resLabel$fpsTag ($formatTag)$prefBadge"
            val link = newExtractorLink(
                source = providerName,
                name = primaryLinkName,
                url = stream.content,
                type = ExtractorLinkType.VIDEO
            ) {
                this.headers = defaultHeaders
                this.quality = qualityNum
                this.audioTracks = audioTracksList
            }
            generatedLinks.add(link)

            // Additional links for multi-language dubbed tracks
            if (height >= 1080 && distinctAudioTracks.size > 1) {
                for (dubbedTrack in distinctAudioTracks.filter { it != primaryAudio && it.isDubbed }) {
                    val dubbedAudioFile = AudioHelper.createAudioFile(dubbedTrack.stream.content, defaultHeaders)
                    val dubbedLink = newExtractorLink(
                        source = providerName,
                        name = "$providerName $resLabel$fpsTag [$formatTag] [Dub: ${dubbedTrack.languageName}]",
                        url = stream.content,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.headers = defaultHeaders
                        this.quality = qualityNum
                        this.audioTracks = listOf(dubbedAudioFile)
                    }
                    generatedLinks.add(dubbedLink)
                }
            }
        }

        // D. Progressive Streams (pre-muxed audio + video)
        val rawVideoStreams = info.videoStreams.orEmpty().filter { !it.content.isNullOrBlank() }

        for (stream in rawVideoStreams) {
            val height = extractPixelHeight(stream.quality, stream.height)
            val resLabel = formatResolutionLabel(height)
            val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
            val formatTag = (stream.format?.name ?: "MP4").uppercase()
            val qualityNum = heightToQuality(height)

            val isPreferred = (targetHeight != null && height == targetHeight)
            val prefBadge = if (isPreferred) " ⭐" else ""
            val fallbackTag = if (height <= 360) " [Muxed Fallback]" else ""

            val link = newExtractorLink(
                source = providerName,
                name = "$providerName $resLabel$fpsTag ($formatTag)$fallbackTag$prefBadge",
                url = stream.content,
                type = ExtractorLinkType.VIDEO
            ) {
                this.headers = defaultHeaders
                this.quality = qualityNum
            }
            generatedLinks.add(link)
        }

        // E. Adaptive DASH Manifest Stream
        if (!info.dashMpdUrl.isNullOrBlank()) {
            generatedLinks.add(
                newExtractorLink(
                    source = providerName,
                    name = "$providerName Adaptive (DASH)",
                    url = info.dashMpdUrl,
                    type = ExtractorLinkType.DASH
                ) {
                    this.headers = defaultHeaders
                    this.quality = Qualities.Unknown.value
                }
            )
        }

        // F. Standalone Audio Streams
        for (audioTrack in distinctAudioTracks.take(3)) {
            val audioLink = newExtractorLink(
                source = providerName,
                name = "$providerName Audio: ${audioTrack.fullLabel}",
                url = audioTrack.stream.content,
                type = ExtractorLinkType.VIDEO
            ) {
                this.headers = defaultHeaders
                this.quality = Qualities.Unknown.value
            }
            generatedLinks.add(audioLink)
        }

        // G. Prioritize & Sort Links
        val sortedLinks = generatedLinks.sortedWith(
            compareByDescending<ExtractorLink> { it.name.contains("⭐") }
                .thenByDescending {
                    when (it.type) {
                        ExtractorLinkType.M3U8 -> 10000
                        ExtractorLinkType.VIDEO -> if (it.name.startsWith("$providerName Audio:")) -100 else it.quality
                        ExtractorLinkType.DASH -> 500
                        else -> 0
                    }
                }
                .thenBy { it.name.contains("[Muxed Fallback]") }
        )

        sortedLinks.forEach { callback(it) }

        // H. Subtitles & Closed Captions
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
                SubtitleFile(
                    lang = fullLangLabel,
                    url = sub.content
                )
            )
        }
    }
}
