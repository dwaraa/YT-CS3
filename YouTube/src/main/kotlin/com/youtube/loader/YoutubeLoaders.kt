package com.youtube.loader

import com.lagradost.cloudstream3.*
import com.youtube.YoutubePreferences
import com.youtube.extractor.YoutubeEpisodeMapper
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

object YoutubeLoaders {
    private const val TAG = "YoutubeLoaders"
    private val service = ServiceList.YouTube

    enum class UrlType {
        Video, Channel, Playlist, Unknown
    }

    fun getUrlType(url: String): UrlType {
        return when {
            url.contains("/watch") || url.contains("youtu.be/") || url.contains("/shorts/") || url.contains("/embed/") -> UrlType.Video
            url.contains("/channel/") || url.contains("/@") || url.contains("/c/") || url.contains("/user/") -> UrlType.Channel
            url.contains("/playlist?list=") -> UrlType.Playlist
            else -> UrlType.Video
        }
    }

    fun sanitizeUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            !url.startsWith("http://") && !url.startsWith("https://") -> "https://$url"
            else -> url
        }
    }

    fun sanitizeToWatchUrl(url: String): String {
        val clean = sanitizeUrl(url)
        return when {
            clean.contains("youtu.be/") -> {
                val videoId = clean.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                "https://www.youtube.com/watch?v=$videoId"
            }
            clean.contains("/shorts/") -> {
                val videoId = clean.substringAfter("/shorts/").substringBefore("?").substringBefore("&")
                "https://www.youtube.com/watch?v=$videoId"
            }
            clean.contains("/embed/") -> {
                val videoId = clean.substringAfter("/embed/").substringBefore("?").substringBefore("&")
                "https://www.youtube.com/watch?v=$videoId"
            }
            else -> clean
        }
    }

    suspend fun loadVideo(api: MainAPI, url: String): LoadResponse {
        val watchUrl = sanitizeToWatchUrl(url)
        val info = StreamInfo.getInfo(service, watchUrl)
        val isLiveStream = info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM
        val isPostLive = info.streamType == StreamType.POST_LIVE_STREAM
        val videoTitle = info.name ?: "YouTube Video"
        val rawDescription = info.description?.content.toString()
        val poster = info.thumbnails.lastOrNull()?.url

        val isUpcoming = isLiveStream && info.hlsUrl.isNullOrBlank() &&
                info.videoStreams.isNullOrEmpty() && info.videoOnlyStreams.isNullOrEmpty()

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

        return with(api) {
            newMovieLoadResponse(
                videoTitle,
                url,
                if (isLiveStream) TvType.Live else TvType.Others,
                url
            ) {
                this.plot = enrichedPlot
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.duration = durationMinutes

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
    }

    suspend fun loadChannel(api: MainAPI, url: String, maxPagesToLoad: Int = 15): LoadResponse {
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
        episodes.addAll(page.items.mapIndexed { idx, item ->
            YoutubeEpisodeMapper.toEpisode(api, item, idx + 1)
        })

        var pagesLoaded = 1
        while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
            page = videosExtractor.getPage(page.nextPage)
            val currentCount = episodes.size
            episodes.addAll(page.items.mapIndexed { idx, item ->
                YoutubeEpisodeMapper.toEpisode(api, item, currentCount + idx + 1)
            })
            pagesLoaded++
        }

        return with(api) {
            newTvSeriesLoadResponse(
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
    }

    suspend fun loadPlaylist(api: MainAPI, url: String, maxPagesToLoad: Int = 15): LoadResponse {
        val extractor = service.getPlaylistExtractor(url)
        extractor.fetchPage()

        val playlistName = extractor.name ?: "YouTube Playlist"
        val playlistDescription = extractor.description?.content.toString()
        val playlistThumbnail = extractor.thumbnails.lastOrNull()?.url
        val uploaderName = extractor.uploaderName ?: ""

        val episodes = mutableListOf<Episode>()

        var page = extractor.initialPage
        episodes.addAll(page.items.mapIndexed { idx, item ->
            YoutubeEpisodeMapper.toEpisode(api, item, idx + 1)
        })

        var pagesLoaded = 1
        while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
            page = extractor.getPage(page.nextPage)
            val currentCount = episodes.size
            episodes.addAll(page.items.mapIndexed { idx, item ->
                YoutubeEpisodeMapper.toEpisode(api, item, currentCount + idx + 1)
            })
            pagesLoaded++
        }

        return with(api) {
            newTvSeriesLoadResponse(
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
    }
}
