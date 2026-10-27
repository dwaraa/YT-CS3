package com.youtube.loader

import android.util.Log
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

        // Rich video details: views, likes, upload date, chapters
        val viewsText = if (info.viewCount > 0) "👁️ ${YoutubeEpisodeMapper.formatCount(info.viewCount)} views" else ""
        val likesText = if (info.likeCount > 0) "👍 ${YoutubeEpisodeMapper.formatCount(info.likeCount)} likes" else ""
        val dateText = info.textualUploadDate?.takeIf { it.isNotBlank() }?.let { "📅 $it" } ?: ""

        val statsLine = listOf(viewsText, likesText, dateText).filter { it.isNotBlank() }.joinToString(" • ")

        val segments = try {
            info.streamSegments
        } catch (_: Throwable) {
            null
        }

        val chaptersSection = if (!segments.isNullOrEmpty()) {
            val chapterLines = segments.joinToString("\n") { seg ->
                val ts = YoutubeEpisodeMapper.formatSecondsToTimestamp(seg.startTimeSeconds)
                "• $ts ${seg.title ?: "Chapter"}"
            }
            "📖 Chapters:\n$chapterLines\n\n"
        } else ""

        val enrichedPlot = buildString {
            when {
                isUpcoming -> append("⏳ Scheduled / Upcoming Live Stream: Broadcast has not started yet.\n\n")
                isLiveStream -> append("🔴 LIVE NOW: Real-time broadcast\n\n")
                isPostLive -> append("⏹️ Stream Ended: Broadcast has concluded and is being processed by YouTube.\n\n")
            }
            if (statsLine.isNotBlank()) {
                append("$statsLine\n\n")
            }
            if (exactDurationText.isNotBlank()) {
                append("⏱️ Runtime: $exactDurationText\n\n")
            }
            if (chaptersSection.isNotBlank()) {
                append(chaptersSection)
            }
            append(rawDescription)
        }

        val uploadYear = try {
            info.uploadDate?.offsetDateTime()?.year
        } catch (_: Throwable) {
            null
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
                this.year = uploadYear

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

                val allTags = mutableListOf<String>()
                info.category?.takeIf { it.isNotBlank() }?.let { allTags.add(it) }
                info.tags?.take(6)?.let { allTags.addAll(it) }
                this.tags = allTags.distinct()
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

        val episodes = mutableListOf<Episode>()
        val tabs = extractor.tabs

        for (tab in tabs) {
            val tabUrl = tab.url ?: ""
            val seasonNum = when {
                tabUrl.contains("/videos") -> 1
                tabUrl.contains("/shorts") -> 2
                tabUrl.contains("/streams") || tabUrl.contains("/live") -> 3
                tabUrl.contains("/playlists") -> 4
                else -> continue // Skip /community, /about, etc.
            }

            val prefix = when (seasonNum) {
                1 -> ""
                2 -> "⚡ "
                3 -> "🔴 "
                4 -> "📋 "
                else -> ""
            }

            try {
                val tabExtractor = service.getChannelTabExtractor(tab)
                tabExtractor.fetchPage()

                var page = tabExtractor.initialPage
                val tabEpisodes = mutableListOf<Episode>()

                tabEpisodes.addAll(page.items.mapIndexed { idx, item ->
                    val ep = YoutubeEpisodeMapper.toEpisode(api, item, idx + 1, seasonNum)
                    if (prefix.isNotEmpty() && !ep.name.isNullOrBlank()) {
                        ep.name = "$prefix${ep.name}"
                    }
                    ep
                })

                if (seasonNum == 1) {
                    var pagesLoaded = 1
                    while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
                        page = tabExtractor.getPage(page.nextPage)
                        val currentCount = tabEpisodes.size
                        tabEpisodes.addAll(page.items.mapIndexed { idx, item ->
                            YoutubeEpisodeMapper.toEpisode(api, item, currentCount + idx + 1, seasonNum)
                        })
                        pagesLoaded++
                    }
                }

                episodes.addAll(tabEpisodes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load channel tab '${tab.url}': ${e.message}")
            }
        }

        // Fallback if no tabs loaded
        if (episodes.isEmpty()) {
            val videosTab = tabs.firstOrNull { it.url.contains("/videos") } ?: tabs.firstOrNull()
            if (videosTab != null) {
                val videosExtractor = service.getChannelTabExtractor(videosTab)
                videosExtractor.fetchPage()
                var page = videosExtractor.initialPage
                episodes.addAll(page.items.mapIndexed { idx, item ->
                    YoutubeEpisodeMapper.toEpisode(api, item, idx + 1, 1)
                })
            }
        }

        val subscriberText = if (extractor.subscriberCount > 0) {
            "${YoutubeEpisodeMapper.formatCount(extractor.subscriberCount)} Subscribers"
        } else ""

        val channelTags = listOfNotNull(
            "Channel",
            subscriberText.takeIf { it.isNotBlank() },
            "${episodes.size} Total Items"
        )

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
                this.tags = channelTags
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
            YoutubeEpisodeMapper.toEpisode(api, item, idx + 1, 1)
        })

        var pagesLoaded = 1
        while (page.hasNextPage() && pagesLoaded < maxPagesToLoad) {
            page = extractor.getPage(page.nextPage)
            val currentCount = episodes.size
            episodes.addAll(page.items.mapIndexed { idx, item ->
                YoutubeEpisodeMapper.toEpisode(api, item, currentCount + idx + 1, 1)
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
                this.tags = if (uploaderName.isNotBlank()) listOf("Channel: $uploaderName", "${episodes.size} Videos") else listOf("Playlist", "${episodes.size} Videos")
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
