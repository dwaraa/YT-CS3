package com.youtube

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.exceptions.*
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap

class YoutubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Others,
        TvType.Live,
        TvType.TvSeries
    )

    companion object {
        private const val TAG = "YoutubeProvider"

        // High-speed in-memory caches (5-minute TTL)
        private val channelVideosCache = ConcurrentHashMap<String, Pair<Long, List<SearchResponse>>>()
        private var combinedSubCache: Pair<Long, List<SearchResponse>>? = null
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        // Negative caching & backoff for failing channels (60-second backoff)
        private val failedChannelsCache = ConcurrentHashMap<String, Pair<Long, String>>()
        private const val FAILURE_BACKOFF_MS = 60 * 1000L

        private const val DEFAULT_ICON =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/YouTube_full-color_icon_%282017%29.svg/512px-YouTube_full-color_icon_%282017%29.svg.png"

        fun clearFeedCache() {
            channelVideosCache.clear()
            combinedSubCache = null
            failedChannelsCache.clear()
        }

        fun formatExtractionError(t: Throwable): String {
            val message = t.message ?: ""
            return when {
                t is ReCaptchaException || message.contains("recaptcha", ignoreCase = true) || message.contains("bot", ignoreCase = true) ->
                    "Bot Verification Required (reCAPTCHA hurdle)"
                t is AgeRestrictedContentException || message.contains("age", ignoreCase = true) ->
                    "Age-Restricted Video (Requires Google sign-in)"
                t is GeographicRestrictionException || message.contains("geo", ignoreCase = true) || message.contains("country", ignoreCase = true) ->
                    "Region Blocked (Unavailable in current country/IP)"
                t is PaidContentException || message.contains("paid", ignoreCase = true) || message.contains("premium", ignoreCase = true) ->
                    "Members-Only Content (Requires paid YouTube subscription)"
                message.contains("429") || message.contains("Too Many Requests", ignoreCase = true) ->
                    "Rate Limited (HTTP 429 Too Many Requests)"
                t is ContentNotAvailableException ->
                    "Content Unavailable (Video removed or set to private)"
                t is ParsingException ->
                    "YouTube Layout Parsing Error (Page structure changed)"
                else ->
                    t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            }
        }

        // --- Numeric Resolution and Quality Parsing Helpers ---

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
                height >= 2160 -> Qualities.P2160.value
                height >= 1440 -> Qualities.P1440.value
                height >= 1080 -> Qualities.P1080.value
                height >= 720 -> Qualities.P720.value
                height >= 480 -> Qualities.P480.value
                height >= 360 -> Qualities.P360.value
                height >= 240 -> Qualities.P240.value
                else -> Qualities.P144.value
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
                        val b = if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
                        b
                    }
                }
            }

            // 2. Original track
            val originalTracks = audioList.filter { it.isOriginal }
            if (originalTracks.isNotEmpty()) {
                return originalTracks.maxByOrNull {
                    val b = if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
                    b
                }
            }

            // 3. Prefer M4A / AAC with highest bitrate (best ExoPlayer hardware decoder compatibility)
            val m4aTracks = audioList.filter { it.codec.contains("M4A") || it.codec.contains("AAC") || it.codec.contains("MP4") }
            if (m4aTracks.isNotEmpty()) {
                return m4aTracks.maxByOrNull {
                    val b = if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
                    b
                }
            }

            // 4. Any track with highest bitrate
            return audioList.maxByOrNull {
                val b = if (it.stream.averageBitrate > 0) it.stream.averageBitrate else it.stream.bitrate
                b
            }
        }
    }

    init {
        ensureDownloaderInitialized()
    }

    private fun ensureDownloaderInitialized() {
        try {
            NewPipe.getDownloader()
        } catch (e: Throwable) {
            try {
                NewPipe.init(YoutubeDownloader())
            } catch (initErr: Throwable) {
                Log.e(TAG, "Failed to initialize NewPipe downloader: ${initErr.message}", initErr)
            }
        }
    }

    // Dynamic home screen rows reflecting user preferences and subscribed channels
    override val mainPage: List<MainPageData>
        get() = getCustomHomeRows()

    private fun getCustomHomeRows(): List<MainPageData> {
        val rows = mutableListOf<MainPageData>()

        // 1. Combined Subscriptions Row
        if (YoutubePreferences.showCombinedSubscriptions) {
            val subCount = YoutubePreferences.getSubscribedChannels().size
            val label = if (subCount > 0) "Subscribed: Latest Videos ($subCount Channels)" else "Subscribed: Latest Videos"
            rows.add(
                MainPageData(
                    name = label,
                    data = "feed:subscriptions_combined",
                    horizontalImages = true
                )
            )
        }

        // 2. Individual Rows for Each Subscribed Channel
        if (YoutubePreferences.showIndividualChannelRows) {
            val subscribedChannels = YoutubePreferences.getSubscribedChannels()
            for (channel in subscribedChannels) {
                rows.add(
                    MainPageData(
                        name = "Channel: ${channel.name}",
                        data = "feed:channel:${channel.url}",
                        horizontalImages = true
                    )
                )
            }
        }

        // 3. Built-in Category Rows (User toggleable)
        if (YoutubePreferences.showTrending) {
            rows.add(MainPageData("Trending", "kiosk:Trending", horizontalImages = true))
        }
        if (YoutubePreferences.showMovies) {
            rows.add(MainPageData("Movies & Shows", "kiosk:trending_movies_and_shows", horizontalImages = true))
        }
        if (YoutubePreferences.showMusic) {
            rows.add(MainPageData("Music", "kiosk:trending_music", horizontalImages = true))
        }
        if (YoutubePreferences.showGaming) {
            rows.add(MainPageData("Gaming", "kiosk:trending_gaming", horizontalImages = true))
        }
        if (YoutubePreferences.showPodcasts) {
            rows.add(MainPageData("Podcasts", "kiosk:trending_podcasts_episodes", horizontalImages = true))
        }
        if (YoutubePreferences.showLive) {
            rows.add(MainPageData("Live Streams", "kiosk:live", horizontalImages = true))
        }

        if (rows.isEmpty()) {
            rows.add(MainPageData("Trending", "kiosk:Trending", horizontalImages = true))
        }

        return rows
    }

    private val service = ServiceList.YouTube
    private val mainPageCache = mutableMapOf<String, Page?>()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        ensureDownloaderInitialized()
        val dataKey = request.data

        // A. Combined Subscriptions Feed
        if (dataKey == "feed:subscriptions_combined") {
            return getCombinedSubscriptionsFeed(request.name)
        }

        // B. Individual Channel Row
        if (dataKey.startsWith("feed:channel:")) {
            val channelUrl = dataKey.removePrefix("feed:channel:")
            return getSingleChannelFeed(request.name, channelUrl)
        }

        // C. Standard NewPipe Kiosks
        val kioskId = dataKey.removePrefix("kiosk:")
        if (page == 1) mainPageCache.remove(dataKey)

        val extractor = getKioskExtractor(kioskId)

        val pageData = try {
            if (page == 1) {
                extractor.fetchPage()
                extractor.initialPage.also {
                    mainPageCache[dataKey] = it.nextPage
                }
            } else {
                val next = mainPageCache[dataKey] ?: return newHomePageResponse(emptyList(), false)
                extractor.getPage(next).also {
                    mainPageCache[dataKey] = it.nextPage
                }
            }
        } catch (e: Exception) {
            val err = formatExtractionError(e)
            Log.e(TAG, "Failed to load kiosk '$dataKey': $err", e)
            val errorItem = newMovieSearchResponse(
                name = "⚠️ Couldn't load ${request.name} ($err)",
                url = "https://www.youtube.com",
                type = TvType.Others
            ) {
                this.posterUrl = DEFAULT_ICON
            }
            return newHomePageResponse(
                listOf(HomePageList(request.name, listOf(errorItem), isHorizontalImages = true)),
                false
            )
        }

        val results = pageData.items.mapNotNull { it.toSearchResponse() }

        val headerName = try {
            extractor.name.ifEmpty { request.name }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read extractor name for $kioskId: ${e.message}")
            request.name
        }.ifEmpty { "Trending" }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    headerName,
                    results,
                    isHorizontalImages = true
                )
            ),
            pageData.hasNextPage()
        )
    }

    // High-speed cached combined subscriptions feed
    private suspend fun getCombinedSubscriptionsFeed(headerTitle: String): HomePageResponse {
        val now = System.currentTimeMillis()
        val cached = combinedSubCache
        if (cached != null && (now - cached.first) < CACHE_TTL_MS && cached.second.isNotEmpty()) {
            return newHomePageResponse(
                listOf(HomePageList(headerTitle, cached.second, isHorizontalImages = true)),
                hasNext = false
            )
        }

        val channels = YoutubePreferences.getSubscribedChannels()
        if (channels.isEmpty()) {
            val placeholder = newMovieSearchResponse(
                name = "ℹ️ No Subscribed Channels (Tap Settings ⚙️ to add channels)",
                url = "https://www.youtube.com",
                type = TvType.Others
            ) {
                this.posterUrl = DEFAULT_ICON
            }
            return newHomePageResponse(
                listOf(
                    HomePageList(
                        "$headerTitle (Empty)",
                        listOf(placeholder),
                        isHorizontalImages = true
                    )
                ),
                hasNext = false
            )
        }

        val collectedVideos: List<List<SearchResponse>> = channels.map { channel ->
            try {
                fetchRecentChannelVideos(channel.url, limit = 10)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching videos for channel ${channel.name} (${channel.url}): ${e.message}")
                emptyList()
            }
        }

        // Interleave videos from each channel
        val combinedItems = mutableListOf<SearchResponse>()
        var index = 0
        var hasMore = true
        while (hasMore) {
            hasMore = false
            for (channelList in collectedVideos) {
                if (index < channelList.size) {
                    combinedItems.add(channelList[index])
                    hasMore = true
                }
            }
            index++
        }

        val distinctItems = combinedItems.distinctBy { it.url }
        if (distinctItems.isNotEmpty()) {
            combinedSubCache = Pair(now, distinctItems)
        }

        val finalItems = if (distinctItems.isEmpty()) {
            listOf(
                newMovieSearchResponse(
                    name = "⚠️ Couldn't load latest uploads (Tap to retry)",
                    url = "https://www.youtube.com",
                    type = TvType.Others
                ) {
                    this.posterUrl = DEFAULT_ICON
                }
            )
        } else {
            distinctItems
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    headerTitle,
                    finalItems,
                    isHorizontalImages = true
                )
            ),
            hasNext = false
        )
    }

    private suspend fun getSingleChannelFeed(headerTitle: String, channelUrl: String): HomePageResponse {
        val items = fetchRecentChannelVideos(channelUrl, limit = 25)
        val channelName = headerTitle.removePrefix("Channel: ").trim()

        if (items.isEmpty()) {
            val failureInfo = failedChannelsCache[channelUrl]
            val reason = failureInfo?.second ?: "Channel returned no videos"
            Log.w(TAG, "Single channel feed empty for $channelUrl. Reason: $reason")

            val retryCard = newMovieSearchResponse(
                name = "⚠️ Couldn't load $channelName ($reason) - Tap to retry",
                url = channelUrl,
                type = TvType.Others
            ) {
                this.posterUrl = DEFAULT_ICON
            }

            return newHomePageResponse(
                listOf(
                    HomePageList(
                        headerTitle,
                        listOf(retryCard),
                        isHorizontalImages = true
                    )
                ),
                hasNext = false
            )
        }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    headerTitle,
                    items,
                    isHorizontalImages = true
                )
            ),
            hasNext = false
        )
    }

    private suspend fun fetchRecentChannelVideos(channelUrl: String, limit: Int = 20): List<SearchResponse> {
        val now = System.currentTimeMillis()

        // 1. Check in-memory success cache
        val cached = channelVideosCache[channelUrl]
        if (cached != null && (now - cached.first) < CACHE_TTL_MS && cached.second.isNotEmpty()) {
            return cached.second
        }

        // 2. Negative caching & backoff: Do not hammer failing channels repeatedly
        val failureInfo = failedChannelsCache[channelUrl]
        if (failureInfo != null && (now - failureInfo.first) < FAILURE_BACKOFF_MS) {
            val remainingSec = (FAILURE_BACKOFF_MS - (now - failureInfo.first)) / 1000
            Log.d(TAG, "Skipping channel $channelUrl in backoff ($remainingSec s remaining): ${failureInfo.second}")
            return emptyList()
        }

        return try {
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()

            // Resolve avatar at runtime if not already stored
            val channelAvatar = extractor.avatars.lastOrNull()?.url
            if (!channelAvatar.isNullOrBlank()) {
                YoutubePreferences.updateChannelAvatar(channelUrl, channelAvatar)
            }

            val tabs = extractor.tabs
            val videosTab = tabs.firstOrNull { it.url.contains("/videos") } ?: tabs.firstOrNull()
            if (videosTab == null) {
                val errorMsg = "No videos tab found on channel"
                failedChannelsCache[channelUrl] = Pair(now, errorMsg)
                return emptyList()
            }

            val videosExtractor = service.getChannelTabExtractor(videosTab)
            val page = videosExtractor.initialPage
            val list = page.items.take(limit).mapNotNull { it.toSearchResponse() }

            // Clear failure record and cache result
            failedChannelsCache.remove(channelUrl)
            channelVideosCache[channelUrl] = Pair(now, list)
            list
        } catch (e: Exception) {
            val errorMsg = formatExtractionError(e)
            Log.e(TAG, "Failed to fetch videos for channel $channelUrl: $errorMsg", e)
            failedChannelsCache[channelUrl] = Pair(now, errorMsg)
            emptyList()
        }
    }

    // Multi-page rich search returning comprehensive catalog of results
    override suspend fun search(query: String): List<SearchResponse> {
        ensureDownloaderInitialized()
        val extractor = service.getSearchExtractor(query)

        val allItems = mutableListOf<SearchResponse>()
        try {
            extractor.fetchPage()
            var page = extractor.initialPage
            allItems.addAll(page.items.mapNotNull { it.toSearchResponse() })

            // Multi-page traversal to retrieve full catalog of search results (up to 4 pages ~80 items)
            var pagesLoaded = 1
            val maxSearchPages = 4
            while (page.hasNextPage() && pagesLoaded < maxSearchPages) {
                page = extractor.getPage(page.nextPage)
                allItems.addAll(page.items.mapNotNull { it.toSearchResponse() })
                pagesLoaded++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search query '$query' encountered error: ${formatExtractionError(e)}", e)
        }

        return allItems.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        ensureDownloaderInitialized()
        return try {
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items.take(15).mapNotNull { it.toSearchResponse() }
        } catch (e: Exception) {
            Log.w(TAG, "Quick search for '$query' failed: ${e.message}")
            emptyList()
        }
    }

    private fun getKioskExtractor(kioskId: String?): KioskExtractor<out InfoItem> {
        return if (kioskId.isNullOrBlank()) {
            service.kioskList.getDefaultKioskExtractor(null)
        } else {
            service.kioskList.getExtractorById(kioskId, null)
        }
    }

    private fun InfoItem.toSearchResponse(): SearchResponse? {
        val itemUrl = url ?: return null
        val itemName = name ?: "Unknown"
        val thumb = thumbnails.lastOrNull()?.url

        return when (this) {
            is ChannelInfoItem -> {
                newTvSeriesSearchResponse(itemName, itemUrl, TvType.TvSeries) {
                    this.posterUrl = thumb
                }
            }
            is PlaylistInfoItem -> {
                newTvSeriesSearchResponse(itemName, itemUrl, TvType.TvSeries) {
                    this.posterUrl = thumb
                }
            }
            is StreamInfoItem -> {
                val isLive = streamType?.name?.contains("LIVE") == true
                newMovieSearchResponse(
                    itemName,
                    itemUrl,
                    if (isLive) TvType.Live else TvType.Others
                ) {
                    this.posterUrl = thumb
                }
            }
            else -> {
                newMovieSearchResponse(itemName, itemUrl, TvType.Others) {
                    this.posterUrl = thumb
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        ensureDownloaderInitialized()
        val cleanUrl = sanitizeUrl(url)
        val urlType = getUrlType(cleanUrl)

        return try {
            when (urlType) {
                UrlType.Video -> loadVideo(cleanUrl)
                UrlType.Channel -> loadChannel(cleanUrl)
                UrlType.Playlist -> loadPlaylist(cleanUrl)
                UrlType.Unknown -> loadVideo(cleanUrl)
            }
        } catch (e: Throwable) {
            val errorMsg = formatExtractionError(e)
            Log.e(TAG, "Failed to load '$cleanUrl': $errorMsg", e)
            throw ErrorLoadingException(errorMsg)
        }
    }

    private enum class UrlType {
        Video, Channel, Playlist, Unknown
    }

    private fun getUrlType(url: String): UrlType {
        return when {
            url.contains("/watch") || url.contains("youtu.be/") || url.contains("/shorts/") || url.contains("/embed/") -> UrlType.Video
            url.contains("/channel/") || url.contains("/@") || url.contains("/c/") || url.contains("/user/") -> UrlType.Channel
            url.contains("/playlist?list=") -> UrlType.Playlist
            else -> UrlType.Video
        }
    }

    private suspend fun loadVideo(url: String): LoadResponse {
        val watchUrl = sanitizeToWatchUrl(url)
        val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)
        val isLiveStream = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val isPostLive = info.streamType == StreamType.POST_LIVE_STREAM
        val videoTitle = info.name ?: "YouTube Video"
        val rawDescription = info.description?.content.toString()
        val poster = info.thumbnails.lastOrNull()?.url

        // Check if stream is upcoming / scheduled
        val isUpcoming = isLiveStream && info.hlsUrl.isNullOrBlank() &&
                info.videoStreams.isNullOrEmpty() && info.videoOnlyStreams.isNullOrEmpty()

        // CloudStream expects runtime in MINUTES for movies
        val durationMinutes = if (!isLiveStream && info.duration > 0) {
            Math.round(info.duration / 60.0).toInt().coerceAtLeast(1)
        } else null

        val exactDurationText = if (!isLiveStream && info.duration > 0) {
            val totalSeconds = info.duration
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) {
                "${hours}h ${minutes}m ${seconds.toString().padStart(2, '0')}s"
            } else {
                "${minutes}m ${seconds.toString().padStart(2, '0')}s"
            }
        } else ""

        val enrichedPlot = when {
            isUpcoming -> "⏳ Scheduled / Upcoming Live Stream: This broadcast has not started yet. Please check back at the scheduled start time.\n\n$rawDescription"
            isLiveStream -> "🔴 LIVE NOW: Real-time broadcast\n\n$rawDescription"
            isPostLive -> "⏹️ Stream Ended: Broadcast has concluded and is being processed by YouTube.\n\n$rawDescription"
            exactDurationText.isNotBlank() -> "⏱️ Runtime: $exactDurationText\n\n$rawDescription"
            else -> rawDescription
        }

        return newMovieLoadResponse(
            videoTitle,
            url,
            if (isLiveStream) TvType.Live else TvType.Others,
            url
        ) {
            this.plot = enrichedPlot
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.duration = durationMinutes

            // Connect channel / uploader as Person / Actor
            info.uploaderName?.takeIf { it.isNotBlank() }?.let { uploader ->
                actors = listOf(
                    ActorData(
                        Actor(
                            uploader,
                            info.uploaderAvatars.lastOrNull()?.url ?: ""
                        )
                    )
                )
            }

            tags = info.tags?.take(6)?.toList()
        }
    }

    private suspend fun loadChannel(url: String): LoadResponse {
        val extractor = service.getChannelExtractor(url)
        extractor.fetchPage()

        val channelName = extractor.name ?: "YouTube Channel"
        val channelDescription = extractor.description ?: ""
        val channelAvatar = extractor.avatars.lastOrNull()?.url
        val channelBanner = extractor.banners.lastOrNull()?.url

        if (!channelAvatar.isNullOrBlank()) {
            YoutubePreferences.updateChannelAvatar(url, channelAvatar)
        }

        val tabs = extractor.tabs
        val videosTab = tabs.firstOrNull { it.url.contains("/videos") } ?: tabs.firstOrNull()
            ?: throw RuntimeException("No videos tab found for channel")

        val videosExtractor = service.getChannelTabExtractor(videosTab)
        val episodes = mutableListOf<Episode>()

        var page = videosExtractor.initialPage
        episodes.addAll(page.items.map { item ->
            newEpisode(item.url) {
                this.name = item.name
                this.posterUrl = item.thumbnails.lastOrNull()?.url
                this.data = item.url
            }
        })

        var pagesLoaded = 1
        val maxPagesToLoad = 6

        while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
            page = videosExtractor.getPage(page.nextPage)
            episodes.addAll(page.items.map { item ->
                newEpisode(item.url) {
                    this.name = item.name
                    this.posterUrl = item.thumbnails.lastOrNull()?.url
                    this.data = item.url
                }
            })
            pagesLoaded++
        }

        return newTvSeriesLoadResponse(
            channelName,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.plot = channelDescription
            this.posterUrl = channelAvatar ?: channelBanner
            this.backgroundPosterUrl = channelBanner ?: channelAvatar
            this.tags = listOf("Channel", "${episodes.size} Videos")
            this.actors = listOf(
                ActorData(
                    Actor(
                        channelName,
                        channelAvatar ?: ""
                    )
                )
            )
        }
    }

    private suspend fun loadPlaylist(url: String): LoadResponse {
        val extractor = service.getPlaylistExtractor(url)
        extractor.fetchPage()

        val playlistName = extractor.name ?: "YouTube Playlist"
        val playlistDescription = extractor.description?.content.toString()
        val playlistThumbnail = extractor.thumbnails.lastOrNull()?.url
        val uploaderName = extractor.uploaderName ?: ""

        val episodes = mutableListOf<Episode>()

        var page = extractor.initialPage
        episodes.addAll(page.items.map { item ->
            newEpisode(item.url) {
                this.name = item.name
                this.posterUrl = item.thumbnails.lastOrNull()?.url
                this.data = item.url
            }
        })

        var pagesLoaded = 1
        val maxPagesToLoad = 5

        while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
            page = extractor.getPage(page.nextPage)
            episodes.addAll(page.items.map { item ->
                newEpisode(item.url) {
                    this.name = item.name
                    this.posterUrl = item.thumbnails.lastOrNull()?.url
                    this.data = item.url
                }
            })
            pagesLoaded++
        }

        return newTvSeriesLoadResponse(
            playlistName,
            url,
            TvType.TvSeries,
            episodes
        ) {
            this.plot = playlistDescription
            this.posterUrl = playlistThumbnail
            this.tags = if (uploaderName.isNotBlank()) listOf("Channel: $uploaderName") else listOf("Playlist")
            if (uploaderName.isNotBlank()) {
                this.actors = listOf(
                    ActorData(
                        Actor(
                            uploaderName,
                            extractor.uploaderAvatars.lastOrNull()?.url ?: ""
                        )
                    )
                )
            }
        }
    }

    private data class ScoredLink(
        val link: ExtractorLink,
        val score: Int
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureDownloaderInitialized()
        val videoUrl = sanitizeToWatchUrl(data)
        val defaultHeaders = mapOf(
            "User-Agent" to YoutubeDownloader.USER_AGENT,
            "Referer" to "https://www.youtube.com/"
        )

        var linkFound = false
        var extractionFailureReason: String? = null
        val generatedLinks = mutableListOf<ScoredLink>()

        // 1. Primary: Direct NewPipeExtractor Stream Resolution
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            // Preferred settings from user (numeric target height & preferred subtitle language)
            val preferredQualitySetting = YoutubePreferences.preferredQuality
            val targetHeight = parsePreferredTargetHeight(preferredQualitySetting)
            val preferredSubtitleLang = YoutubePreferences.preferredSubtitle

            val isLiveStream = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
            val isPostLive = info.streamType == StreamType.POST_LIVE_STREAM
            val isUpcoming = isLiveStream && info.hlsUrl.isNullOrBlank() &&
                    info.videoStreams.isNullOrEmpty() && info.videoOnlyStreams.isNullOrEmpty()

            if (isUpcoming) {
                val msg = "This live stream is scheduled and has not started yet."
                Log.w(TAG, msg)
                throw ErrorLoadingException(msg)
            }

            if (isPostLive && info.videoStreams.isNullOrEmpty() && info.videoOnlyStreams.isNullOrEmpty() && info.hlsUrl.isNullOrBlank()) {
                val msg = "Live stream has ended and is being processed by YouTube. Please check back shortly."
                Log.w(TAG, msg)
                throw ErrorLoadingException(msg)
            }

            // A. Live HLS Master Playlist (if available)
            val hlsUrl = info.hlsUrl
            if (!hlsUrl.isNullOrBlank()) {
                val liveLink = newExtractorLink(
                    source = name,
                    name = "$name Live (Adaptive HLS 1080p)",
                    url = hlsUrl,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://www.youtube.com/"
                    this.quality = Qualities.P1080.value
                    this.headers = defaultHeaders
                }
                generatedLinks.add(ScoredLink(liveLink, 100_000))
            }

            // B. Process Audio Streams
            val rawAudioStreams = info.audioStreams.orEmpty().filter { !it.content.isNullOrBlank() }
            val parsedAudioTracks = rawAudioStreams.map { parseAudioStream(it) }

            // Select primary audio track (prefer matching user preference -> original -> high bitrate M4A -> Opus)
            val primaryAudio = selectBestAudio(parsedAudioTracks, preferredSubtitleLang)

            // Distinct audio tracks for dubbed variations & audio-only streaming
            val distinctAudioTracks = parsedAudioTracks.distinctBy { Pair(it.languageName, it.trackTypeTag) }

            // C. Adaptive Playback: Pair Video-Only Streams (4K, 1440p, 1080p, 720p, etc.) with Audio
            val rawVideoOnlyStreams = info.videoOnlyStreams.orEmpty().filter { !it.content.isNullOrBlank() }

            rawVideoOnlyStreams.forEach { stream ->
                val height = extractPixelHeight(stream.quality, stream.height)
                if (height <= 0) return@forEach

                val resLabel = formatResolutionLabel(height)
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val formatTag = (stream.format?.name ?: stream.codec ?: "MP4").uppercase()
                val qualityNum = heightToQuality(height)

                val isPreferred = (targetHeight != null && height == targetHeight)
                val prefBadge = if (isPreferred) " ⭐" else ""

                // Audio pairing for high quality playback with sound
                val audioFile = primaryAudio?.let { AudioHelper.createAudioFile(it.stream.content, defaultHeaders) }
                val audioTracksList = if (audioFile != null) listOf(audioFile) else emptyList()

                val primaryLinkName = "$name $resLabel$fpsTag ($formatTag)$prefBadge"
                val link = newExtractorLink(
                    source = name,
                    name = primaryLinkName,
                    url = stream.content,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.youtube.com/"
                    this.quality = qualityNum
                    this.headers = defaultHeaders
                    this.audioTracks = audioTracksList
                }

                var score = height * 10
                if (isPreferred) score += 50_000
                if (stream.fps > 30) score += 200
                if (formatTag.contains("MP4") || formatTag.contains("AVC") || formatTag.contains("H264")) score += 100

                generatedLinks.add(ScoredLink(link, score))

                // Also generate links for alternative dubbed audio tracks if available on 1080p+ streams
                if (height >= 1080 && distinctAudioTracks.size > 1) {
                    distinctAudioTracks.filter { it.isDubbed }.forEach { dubbedTrack ->
                        val dubbedLinkName = "$name $resLabel$fpsTag ($formatTag) • ${dubbedTrack.fullLabel}"
                        val dubbedLink = newExtractorLink(
                            source = name,
                            name = dubbedLinkName,
                            url = stream.content,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = "https://www.youtube.com/"
                            this.quality = qualityNum
                            this.headers = defaultHeaders
                            this.audioTracks = listOf(AudioHelper.createAudioFile(dubbedTrack.stream.content, defaultHeaders))
                        }
                        val dubbedScore = score - 150
                        generatedLinks.add(ScoredLink(dubbedLink, dubbedScore))
                    }
                }
            }

            // D. Muxed Progressive Video Streams (Pre-combined video+audio safe fallbacks: 720p, 480p, 360p)
            val rawMuxedStreams = info.videoStreams.orEmpty().filter { !it.content.isNullOrBlank() }

            rawMuxedStreams.forEach { stream ->
                val height = extractPixelHeight(stream.quality, stream.height)
                if (height <= 0) return@forEach

                val resLabel = formatResolutionLabel(height)
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val formatTag = (stream.format?.name ?: stream.codec ?: "MP4").uppercase()
                val qualityNum = heightToQuality(height)

                val isPreferred = (targetHeight != null && height == targetHeight)
                val prefBadge = if (isPreferred) " ⭐" else ""

                val muxedLink = newExtractorLink(
                    source = name,
                    name = "$name $resLabel$fpsTag ($formatTag) [Muxed Fallback]$prefBadge",
                    url = stream.content,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.youtube.com/"
                    this.quality = qualityNum
                    this.headers = defaultHeaders
                }

                var score = (height * 10) - 50 // Slightly lower than paired high-bitrate video-only stream
                if (isPreferred) score += 40_000
                if (stream.fps > 30) score += 200
                if (formatTag.contains("MP4")) score += 100

                generatedLinks.add(ScoredLink(muxedLink, score))
            }

            // E. DASH Adaptive Multi-Audio Manifest (Dynamic streaming with ExoPlayer DashMediaSource)
            val dashUrl = info.dashMpdUrl
            if (!dashUrl.isNullOrBlank()) {
                val dashLink = newExtractorLink(
                    source = name,
                    name = "$name Adaptive (DASH Multi-Audio 1080p+)",
                    url = dashUrl,
                    type = ExtractorLinkType.DASH
                ) {
                    this.referer = "https://www.youtube.com/"
                    this.quality = Qualities.P1080.value
                    this.headers = defaultHeaders
                }
                generatedLinks.add(ScoredLink(dashLink, 15_000))
            }

            // F. Standalone Audio-Only Streams (For background playback or listening)
            distinctAudioTracks.forEach { track ->
                val audioLink = newExtractorLink(
                    source = name,
                    name = "$name Audio • ${track.fullLabel}",
                    url = track.stream.content,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "https://www.youtube.com/"
                    this.quality = Qualities.Unknown.value
                    this.headers = defaultHeaders
                }
                generatedLinks.add(ScoredLink(audioLink, 50))
            }

            // G. Subtitles / Closed Captions with Translation & Preference Honoring
            val rawSubtitles = info.subtitles.orEmpty().filter { !it.content.isNullOrBlank() }

            val sortedSubtitles = rawSubtitles.sortedByDescending { sub ->
                val displayName = sub.displayLanguageName ?: sub.languageTag ?: ""
                val isPref = preferredSubtitleLang != "None" && preferredSubtitleLang != "Auto" &&
                        displayName.contains(preferredSubtitleLang, ignoreCase = true)
                when {
                    isPref && !sub.isAutoGenerated -> 1000
                    isPref && sub.isAutoGenerated -> 900
                    !sub.isAutoGenerated -> 500
                    else -> 100
                }
            }

            sortedSubtitles.forEach { sub ->
                val displayName = sub.displayLanguageName?.takeIf { it.isNotBlank() }
                    ?: sub.languageTag?.takeIf { it.isNotBlank() }
                    ?: "English"
                val autoTag = if (sub.isAutoGenerated) " (Auto-generated)" else ""
                val langLabel = "$displayName$autoTag"

                subtitleCallback(
                    newSubtitleFile(
                        lang = langLabel,
                        url = sub.content
                    )
                )
            }

        } catch (t: Throwable) {
            extractionFailureReason = formatExtractionError(t)
            Log.e(TAG, "StreamInfo extraction failed for '$videoUrl': $extractionFailureReason", t)

            if (t is ReCaptchaException || extractionFailureReason.contains("Bot", ignoreCase = true) || extractionFailureReason.contains("Rate Limited", ignoreCase = true)) {
                YoutubeDownloader.refreshConsentCookies(force = true)
            }
        }

        // Sort generated links by score descending (Preferred & best streams first, safe 360p fallback at bottom)
        val sortedLinks = generatedLinks.sortedByDescending { it.score }
        for (item in sortedLinks) {
            callback(item.link)
            linkFound = true
        }

        // 2. Secondary Fallback: Cloudstream native extractors (only if NewPipe found no links)
        if (!linkFound) {
            try {
                val extracted = loadExtractor(
                    videoUrl,
                    subtitleCallback
                ) { link ->
                    linkFound = true
                    callback(link)
                }
                if (extracted) {
                    linkFound = true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Secondary fallback extractor failed for '$videoUrl': ${t.message}")
            }
        }

        if (!linkFound) {
            Log.w(TAG, "No playable streams extracted for '$videoUrl'. Failure cause: ${extractionFailureReason ?: "No stream formats available"}")
        }

        return linkFound
    }

    private fun sanitizeUrl(url: String): String {
        return when {
            url.startsWith("http") -> url
            url.length == 11 -> "https://www.youtube.com/watch?v=$url"
            else -> "$mainUrl/$url"
        }
    }

    private fun sanitizeToWatchUrl(data: String): String {
        val videoId = extractVideoId(data)
        return if (videoId != null) {
            "https://www.youtube.com/watch?v=$videoId"
        } else if (data.startsWith("http")) {
            data
        } else {
            "https://www.youtube.com/watch?v=$data"
        }
    }

    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            """(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})""",
            """[?&]v=([a-zA-Z0-9_-]{11})""",
            """^([a-zA-Z0-9_-]{11})$"""
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.toRegex().find(url)?.groupValues?.get(1)
        }
    }
}
