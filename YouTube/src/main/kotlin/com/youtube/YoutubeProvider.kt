package com.youtube

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
        // High-speed in-memory caches (5-minute TTL)
        private val channelVideosCache = ConcurrentHashMap<String, Pair<Long, List<SearchResponse>>>()
        private var combinedSubCache: Pair<Long, List<SearchResponse>>? = null
        private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

        fun clearFeedCache() {
            channelVideosCache.clear()
            combinedSubCache = null
        }
    }

    init {
        ensureDownloaderInitialized()
    }

    private fun ensureDownloaderInitialized() {
        try {
            NewPipe.getDownloader()
        } catch (_: Throwable) {
            try {
                NewPipe.init(YoutubeDownloader())
            } catch (_: Throwable) {}
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
        } catch (_: Exception) {
            return newHomePageResponse(emptyList(), false)
        }

        val results = pageData.items.mapNotNull { it.toSearchResponse() }

        val headerName = try {
            extractor.name.ifEmpty { request.name }
        } catch (_: Exception) {
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
            return newHomePageResponse(
                listOf(
                    HomePageList(
                        "$headerTitle (No channels added)",
                        emptyList(),
                        isHorizontalImages = true
                    )
                ),
                hasNext = false
            )
        }

        val collectedVideos: List<List<SearchResponse>> = channels.map { channel ->
            try {
                fetchRecentChannelVideos(channel.url, limit = 10)
            } catch (_: Exception) {
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
        combinedSubCache = Pair(now, distinctItems)

        return newHomePageResponse(
            listOf(
                HomePageList(
                    headerTitle,
                    distinctItems,
                    isHorizontalImages = true
                )
            ),
            hasNext = false
        )
    }

    private suspend fun getSingleChannelFeed(headerTitle: String, channelUrl: String): HomePageResponse {
        val items = fetchRecentChannelVideos(channelUrl, limit = 25)
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
        val cached = channelVideosCache[channelUrl]
        if (cached != null && (now - cached.first) < CACHE_TTL_MS && cached.second.isNotEmpty()) {
            return cached.second
        }

        return try {
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()

            val tabs = extractor.tabs
            val videosTab = tabs.firstOrNull { it.url.contains("/videos") } ?: tabs.firstOrNull()
                ?: return emptyList()

            val videosExtractor = service.getChannelTabExtractor(videosTab)
            val page = videosExtractor.initialPage
            val list = page.items.take(limit).mapNotNull { it.toSearchResponse() }
            channelVideosCache[channelUrl] = Pair(now, list)
            list
        } catch (_: Exception) {
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
        } catch (_: Exception) {}

        return allItems.distinctBy { it.url }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        ensureDownloaderInitialized()
        return try {
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items.take(15).mapNotNull { it.toSearchResponse() }
        } catch (_: Exception) {
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

        return when (urlType) {
            UrlType.Video -> loadVideo(cleanUrl)
            UrlType.Channel -> loadChannel(cleanUrl)
            UrlType.Playlist -> loadPlaylist(cleanUrl)
            UrlType.Unknown -> loadVideo(cleanUrl)
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
        val extractor = service.getStreamExtractor(url)
        extractor.fetchPage()

        val info = StreamInfo.getInfo(extractor)
        val isLive = info.streamType?.name?.contains("LIVE") == true
        val videoTitle = info.name ?: "YouTube Video"
        val videoDescription = info.description?.content.toString()
        val poster = info.thumbnails.lastOrNull()?.url

        // CRITICAL RUNTIME FIX:
        // CloudStream expects runtime in MINUTES for movies!
        // info.duration is in SECONDS. (e.g. 309 seconds = 5m 09s).
        // Passing 309 raw seconds resulted in CloudStream showing 5h 9m (309 / 60 = 5h 9m).
        // Correct conversion to minutes:
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

        var linkFound = false

        // 1. Primary: Cloudstream's native YouTube extractor engine
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
        } catch (_: Throwable) {}

        // 2. Direct NewPipeExtractor Stream Resolution
        try {
            val extractor = service.getStreamExtractor(videoUrl)
            extractor.fetchPage()
            val info = StreamInfo.getInfo(extractor)

            // Preferred settings from user
            val preferredQuality = YoutubePreferences.preferredQuality
            val preferredSubtitleLang = YoutubePreferences.preferredSubtitle

            // A. Live HLS Master Playlist
            val hlsUrl = info.hlsUrl
            if (!hlsUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name Live (Adaptive HLS)",
                        url = hlsUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                    }
                )
                linkFound = true
            }

            // B. DASH Adaptive Multi-Audio Manifest
            val dashUrl = info.dashMpdUrl
            if (!dashUrl.isNullOrBlank()) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name Adaptive (DASH Multi-Audio)",
                        url = dashUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.P1080.value
                    }
                )
                linkFound = true
            }

            // C. Progressive Video Streams (Sorted so user's preferred quality is at top)
            val sortedStreams = info.videoStreams?.sortedByDescending { stream ->
                val res = stream.resolution ?: ""
                when {
                    preferredQuality != "Auto" && res.contains(preferredQuality) -> 1000
                    res.contains("2160") -> 900
                    res.contains("1440") -> 800
                    res.contains("1080") -> 700
                    res.contains("720") -> 600
                    res.contains("480") -> 500
                    res.contains("360") -> 400
                    else -> 100
                }
            }

            sortedStreams?.forEach { stream ->
                val fpsTag = if (stream.fps > 30) " ${stream.fps}fps" else ""
                val formatTag = stream.format?.name ?: "MP4"
                val qualityNum = parseQuality(stream.resolution)
                val isPreferred = preferredQuality != "Auto" && (stream.resolution ?: "").contains(preferredQuality)
                val prefBadge = if (isPreferred) " ⭐" else ""

                callback(
                    newExtractorLink(
                        source = name,
                        name = "$name ${stream.resolution ?: ""}$fpsTag ($formatTag)$prefBadge",
                        url = stream.content,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = qualityNum
                    }
                )
                linkFound = true
            }

            // D. Multi-Language Audio Tracks
            info.audioStreams?.forEach { stream ->
                val trackName = stream.audioTrackName?.takeIf { it.isNotBlank() }
                    ?: stream.audioLocale?.displayName?.takeIf { it.isNotBlank() }
                    ?: "Original Audio"

                val audioType = if (stream.audioTrackType != null) " [${stream.audioTrackType?.name}]" else ""
                val bitrate = if (stream.averageBitrate > 0) "${stream.averageBitrate}kbps " else ""
                val formatName = stream.format?.name ?: "M4A"
                val audioLabel = "$name Audio - $trackName$audioType ($bitrate$formatName)".trim()

                callback(
                    newExtractorLink(
                        source = name,
                        name = audioLabel,
                        url = stream.content,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                linkFound = true
            }

            // E. Subtitles / Closed Captions (Sorted so user's preferred language is served first)
            val sortedSubtitles = info.subtitles?.sortedByDescending { sub ->
                val lang = sub.displayLanguageName ?: sub.languageTag ?: ""
                if (preferredSubtitleLang != "None" && preferredSubtitleLang != "Auto" && lang.contains(preferredSubtitleLang, ignoreCase = true)) {
                    100
                } else 10
            }

            sortedSubtitles?.forEach { sub ->
                val autoTag = if (sub.isAutoGenerated) " (Auto-generated)" else ""
                val langName = "${sub.displayLanguageName ?: sub.languageTag ?: "English"}$autoTag"

                subtitleCallback(
                    newSubtitleFile(
                        lang = langName,
                        url = sub.content
                    )
                )
            }
        } catch (_: Throwable) {}

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
