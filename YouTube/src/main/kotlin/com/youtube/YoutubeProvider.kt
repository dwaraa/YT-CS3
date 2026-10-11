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
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

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

    override val mainPage = mainPageOf(
        "Trending" to "Trending",
        "trending_movies_and_shows" to "Movies & Shows",
        "trending_music" to "Music",
        "trending_gaming" to "Gaming",
        "trending_podcasts_episodes" to "Podcasts",
        "live" to "Live"
    )

    private val service = ServiceList.YouTube
    private val mainPageCache = mutableMapOf<String, Page?>()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        ensureDownloaderInitialized()
        val key = request.data
        if (page == 1) mainPageCache.remove(key)

        val extractor = getKioskExtractor(request.data)

        val pageData = try {
            if (page == 1) {
                extractor.fetchPage()
                extractor.initialPage.also {
                    mainPageCache[key] = it.nextPage
                }
            } else {
                val next = mainPageCache[key] ?: return newHomePageResponse(emptyList(), false)
                extractor.getPage(next).also {
                    mainPageCache[key] = it.nextPage
                }
            }
        } catch (e: Exception) {
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

    override suspend fun search(query: String): List<SearchResponse> {
        ensureDownloaderInitialized()
        val extractor = service.getSearchExtractor(query)

        val pageData = try {
            extractor.fetchPage()
            extractor.initialPage
        } catch (e: Exception) {
            return emptyList()
        }

        return pageData.items.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        return search(query)
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

        return newMovieSearchResponse(
            itemName,
            itemUrl,
            TvType.Others
        ) {
            this.posterUrl = thumb
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
        val videoDuration = (info.duration).coerceAtLeast(0).toInt()

        return newMovieLoadResponse(
            videoTitle,
            url,
            if (isLive) TvType.Live else TvType.Others,
            url
        ) {
            this.plot = videoDescription
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.duration = videoDuration

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
        val maxPagesToLoad = 5

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
            this.tags = listOf("Channel")
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

        // 1. First attempt: Use Cloudstream's registered YouTube extractor
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

        // 2. Second attempt: Extract directly via NewPipeExtractor
        if (!linkFound) {
            try {
                val extractor = service.getStreamExtractor(videoUrl)
                extractor.fetchPage()
                val info = StreamInfo.getInfo(extractor)

                // Live HLS stream
                val hlsUrl = info.hlsUrl
                if (!hlsUrl.isNullOrBlank()) {
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name Live (HLS)",
                            url = hlsUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.P1080.value
                        }
                    )
                    linkFound = true
                }

                // Progressive video streams (contains audio + video)
                info.videoStreams?.forEach { stream ->
                    val qualityNum = parseQuality(stream.resolution)
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name ${stream.resolution ?: ""} (${stream.format?.name ?: "MP4"})",
                            url = stream.content,
                            type = ExtractorLinkType.VIDEO
                        ) {
                            this.referer = mainUrl
                            this.quality = qualityNum
                        }
                    )
                    linkFound = true
                }

                // Subtitles
                info.subtitles?.forEach { sub ->
                    val langName = sub.locale?.displayLanguage ?: "English"
                    subtitleCallback(
                        newSubtitleFile(
                            lang = langName,
                            url = sub.content
                        )
                    )
                }
            } catch (_: Throwable) {}
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
