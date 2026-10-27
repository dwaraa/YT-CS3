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
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
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
        val isLive = info.streamType?.name?.contains("LIVE") == true
        val videoTitle = info.name ?: "YouTube Video"
        val videoDescription = info.description?.content.toString()
        val poster = info.thumbnails.lastOrNull()?.url

        // CloudStream expects runtime in MINUTES for movies
        val durationMinutes = if (info.duration > 0) {
            Math.round(info.duration / 60.0).toInt().coerceAtLeast(1)
        } else null

        val exactDurationText = if (info.duration > 0) {
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

        val enrichedPlot = if (exactDurationText.isNotBlank()) {
            "⏱️ Runtime: $exactDurationText\n\n$videoDescription"
        } else videoDescription

        return newMovieLoadResponse(
            videoTitle,
            url,
            if (isLive) TvType.Live else TvType.Others,
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

        // 1. Primary: Direct NewPipeExtractor Stream Resolution
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)

            // Preferred settings from user
            val preferredQuality = YoutubePreferences.preferredQuality
            val preferredSubtitleLang = YoutubePreferences.preferredSubtitle

            // A. Live HLS Master Playlist (if available)
            val hlsUrl = info.hlsUrl
            if (!hlsUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name Live (Adaptive HLS)",
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://www.youtube.com/"
                        this.quality = Qualities.P1080.value
                        this.headers = defaultHeaders
                    }
                )
                linkFound = true
            }

            // B. Progressive Video Streams (Muxed Video + Audio - rock solid playback on all devices)
            val sortedStreams = info.videoStreams?.sortedByDescending { stream ->
                val qual = stream.quality ?: ""
                when {
                    preferredQuality != "Auto" && qual.contains(preferredQuality) -> 1000
                    qual.contains("2160") || qual.contains("4K") -> 900
                    qual.contains("1440") || qual.contains("2K") -> 800
                    qual.contains("1080") -> 700
                    qual.contains("720") -> 600
                    qual.contains("480") -> 500
                    qual.contains("360") -> 400
                    else -> 100
                }
            }

            sortedStreams?.forEach { stream ->
                val streamQuality = stream.quality ?: "720p"
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val formatTag = stream.format?.name ?: "MP4"
                val qualityNum = parseQuality(streamQuality)
                val isPreferred = preferredQuality != "Auto" && streamQuality.contains(preferredQuality)
                val prefBadge = if (isPreferred) " ⭐" else ""

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name $streamQuality$fpsTag ($formatTag)$prefBadge",
                        url = stream.content,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "https://www.youtube.com/"
                        this.quality = qualityNum
                        this.headers = defaultHeaders
                    }
                )
                linkFound = true
            }

            // C. DASH Adaptive Multi-Audio Manifest (1080p, 1440p, 4K multi-audio adaptive stream)
            val dashUrl = info.dashMpdUrl
            if (!dashUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name Adaptive (DASH Multi-Audio 1080p+)",
                        url = dashUrl,
                        type = ExtractorLinkType.DASH
                    ) {
                        this.referer = "https://www.youtube.com/"
                        this.quality = Qualities.P1080.value
                        this.headers = defaultHeaders
                    }
                )
                linkFound = true
            }

            // D. Subtitles / Closed Captions
            val sortedSubtitles = info.subtitles?.sortedByDescending { sub ->
                val lang = sub.displayLanguageName ?: sub.languageTag ?: ""
                if (preferredSubtitleLang != "None" && preferredSubtitleLang != "Auto" && lang.contains(preferredSubtitleLang, ignoreCase = true)) {
                    100
                } else 10
            }

            sortedSubtitles?.forEach { sub ->
                val autoTag = if (sub.isAutoGenerated) " (Auto)" else ""
                val langName = "${sub.displayLanguageName ?: sub.languageTag ?: "English"}$autoTag"

                subtitleCallback(
                    newSubtitleFile(
                        lang = langName,
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

    private fun parseQuality(resolution: String?): Int {
        if (resolution.isNullOrBlank()) return Qualities.Unknown.value
        return when {
            resolution.contains("2160") || resolution.contains("4k", ignoreCase = true) -> Qualities.P2160.value
            resolution.contains("1440") || resolution.contains("2k", ignoreCase = true) -> Qualities.P1440.value
            resolution.contains("1080") -> Qualities.P1080.value
            resolution.contains("720") -> Qualities.P720.value
            resolution.contains("480") -> Qualities.P480.value
            resolution.contains("360") -> Qualities.P360.value
            resolution.contains("240") -> Qualities.P240.value
            resolution.contains("144") -> Qualities.P144.value
            else -> Qualities.Unknown.value
        }
    }
}
