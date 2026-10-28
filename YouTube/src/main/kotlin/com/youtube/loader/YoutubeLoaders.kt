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
        Video, Channel, Playlist, LocalPlaylist, Unknown
    }

    fun getUrlType(url: String): UrlType {
        return when {
            url.startsWith("local_playlist:") -> UrlType.LocalPlaylist
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

    fun extractVideoId(url: String): String? {
        val patterns = listOf(
            """(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})""",
            """[?&]v=([a-zA-Z0-9_-]{11})""",
            """^([a-zA-Z0-9_-]{11})$"""
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.toRegex().find(url.trim())?.groupValues?.get(1)
        }
    }

    fun sanitizeToWatchUrl(url: String): String {
        val trimmed = url.trim()
        val videoId = extractVideoId(trimmed)
        if (videoId != null) {
            return "https://www.youtube.com/watch?v=$videoId"
        }
        return sanitizeUrl(trimmed)
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

        // Rich video details: views, likes/dislikes (RYD), upload date, chapters
        val videoId = extractVideoId(watchUrl)

        val ryd = if (YoutubePreferences.enableReturnYouTubeDislike && videoId != null) {
            try {
                com.youtube.power.ReturnYouTubeDislikeHelper.getDislikes(videoId)
            } catch (e: Exception) {
                Log.d(TAG, "RYD fetch failed: ${e.message}")
                null
            }
        } else null

        val viewsText = if (info.viewCount > 0) "${YoutubeEpisodeMapper.formatCount(info.viewCount)} views" else ""
        val likesDislikesText = when {
            ryd != null -> ryd.formattedStats()
            info.likeCount > 0 -> "${YoutubeEpisodeMapper.formatCount(info.likeCount)} likes"
            else -> ""
        }
        val dateText = info.textualUploadDate?.takeIf { it.isNotBlank() } ?: ""

        val statsLine = listOf(viewsText, likesDislikesText, dateText).filter { it.isNotBlank() }.joinToString(" • ")

        val segments = try {
            info.streamSegments
        } catch (_: Throwable) {
            null
        }

        // SponsorBlock segments
        val sponsorBlockSection = if (YoutubePreferences.enableSponsorBlock && videoId != null) {
            try {
                val segs = com.youtube.power.SponsorBlockHelper.getSegments(videoId)
                com.youtube.power.SponsorBlockHelper.formatSegmentsForDescription(segs)
            } catch (e: Exception) {
                Log.d(TAG, "SponsorBlock fetch failed: ${e.message}")
                null
            }
        } else null

        val liveStatusHeader = when {
            isUpcoming -> "Scheduled / Upcoming Live Stream: Broadcast has not started yet."
            isLiveStream -> "LIVE NOW: Real-time broadcast"
            isPostLive -> "Stream Ended: Broadcast has concluded and is being processed by YouTube."
            else -> null
        }

        val metaParts = listOfNotNull(
            statsLine.takeIf { it.isNotBlank() },
            exactDurationText.takeIf { it.isNotBlank() }?.let { "Duration: $it" }
        )
        val unifiedMeta = metaParts.joinToString(" • ")

        val chaptersBlock = if (!segments.isNullOrEmpty()) {
            val lines = segments.map { seg ->
                val ts = YoutubeEpisodeMapper.formatSecondsToTimestamp(seg.startTimeSeconds)
                val chapterTitle = seg.title?.trim() ?: "Chapter"
                "$ts $chapterTitle"
            }
            "Chapters:\n" + lines.joinToString("\n")
        } else null

        // Subtitles / Transcripts availability overview
        val transcriptsBlock = if (!info.subtitles.isNullOrEmpty()) {
            val subNames = info.subtitles.mapNotNull { it.displayLanguageName ?: it.languageTag }.distinct()
            if (subNames.isNotEmpty()) {
                "Subtitles / Transcripts (${subNames.size}):\n" + subNames.joinToString(", ")
            } else null
        } else null

        // Top comments extraction
        val commentsBlock = try {
            val commentsInfo = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, watchUrl)
            val comments = commentsInfo?.relatedItems?.take(5)
            if (!comments.isNullOrEmpty()) {
                val lines = comments.mapNotNull { item ->
                    val author = item.uploaderName ?: "User"
                    val text = item.commentText?.content?.trim()?.replace("\r", "")?.replace("\n", " ") ?: ""
                    val likes = if (item.likeCount > 0) " (👍 ${YoutubeEpisodeMapper.formatCount(item.likeCount.toLong())})" else ""
                    if (text.isNotBlank()) "• @$author$likes: $text" else null
                }
                if (lines.isNotEmpty()) {
                    "Top Comments:\n" + lines.joinToString("\n\n")
                } else null
            } else null
        } catch (t: Throwable) {
            Log.d(TAG, "Comments extraction skipped: ${t.message}")
            null
        }

        val cleanDescText = rawDescription
            .takeIf { it.isNotBlank() && it != "null" }
            ?.replace("\r\n", "\n")
            ?.replace("\r", "\n")
            ?.replace(Regex("""\n{3,}"""), "\n\n")
            ?.trim()

        val plotSections = listOfNotNull(
            liveStatusHeader,
            unifiedMeta.takeIf { it.isNotBlank() },
            sponsorBlockSection,
            chaptersBlock,
            transcriptsBlock,
            commentsBlock,
            cleanDescText
        )

        val enrichedPlot = plotSections.joinToString("\n\n")

        val uploadYear = try {
            info.uploadDate?.offsetDateTime()?.year
        } catch (_: Throwable) {
            null
        }

        return with(api) {
            newMovieLoadResponse(
                videoTitle,
                watchUrl,
                if (isLiveStream) TvType.Live else TvType.Others,
                watchUrl
            ) {
                this.plot = enrichedPlot
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.duration = durationMinutes
                this.year = uploadYear

                if (ryd != null && ryd.rating > 0.0) {
                    this.score = Score.from5(ryd.rating)
                }

                this.recommendations = info.relatedItems?.mapNotNull {
                    YoutubeEpisodeMapper.toSearchResponse(api, it)
                }

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
        val channelDescription = extractor.description
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.replace("\r\n", "\n")
            ?.replace("\r", "\n")
            ?.replace(Regex("""\n{3,}"""), "\n\n")
            ?.trim() ?: ""
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

            val prefix = ""

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

    suspend fun loadLocalPlaylist(api: MainAPI, playlistId: String): LoadResponse {
        val playlist = com.youtube.power.LocalPlaylistHelper.getPlaylist(playlistId)
            ?: com.youtube.power.LocalPlaylistHelper.getAllPlaylists().firstOrNull { it.id == playlistId }
            ?: throw IllegalArgumentException("Local Playlist '$playlistId' not found")

        val episodes = playlist.items.mapIndexed { idx, item ->
            with(api) {
                newEpisode(item.url) {
                    this.name = item.title
                    this.posterUrl = item.posterUrl
                    this.episode = idx + 1
                    this.season = 1
                    this.data = item.url
                }
            }
        }

        return with(api) {
            newTvSeriesLoadResponse(
                playlist.name,
                "local_playlist:${playlist.id}",
                TvType.TvSeries,
                episodes
            ) {
                this.plot = "Local Playlist: ${playlist.name}\n${playlist.items.size} videos"
                this.posterUrl = playlist.items.firstOrNull()?.posterUrl
                this.tags = listOf("Local Playlist", "${playlist.items.size} Videos")
            }
        }
    }
}

