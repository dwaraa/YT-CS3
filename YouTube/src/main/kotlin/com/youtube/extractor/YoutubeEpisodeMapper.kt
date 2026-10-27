package com.youtube.extractor

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object YoutubeEpisodeMapper {

    private val RELATIVE_TIME_PATTERN = Pattern.compile(
        """(\d+)\s*(second|minute|hour|day|week|month|year)s?\s*ago""",
        Pattern.CASE_INSENSITIVE
    )

    fun isShort(item: StreamInfoItem): Boolean {
        if (item.url?.contains("/shorts/") == true) return true
        try {
            if (item.isShortFormContent) return true
        } catch (_: Throwable) {}
        if (item.duration in 1..61 && item.name?.contains("#shorts", ignoreCase = true) == true) {
            return true
        }
        return false
    }

    fun isShortUrl(url: String): Boolean {
        return url.contains("/shorts/")
    }

    fun toSearchResponse(api: MainAPI, item: InfoItem): SearchResponse? {
        val itemUrl = item.url ?: return null
        val itemName = item.name ?: "Unknown"
        val thumb = item.thumbnails.lastOrNull()?.url

        return with(api) {
            when (item) {
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
                    val isLive = item.streamType?.name?.contains("LIVE") == true
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
    }

    fun toEpisode(api: MainAPI, item: InfoItem, episodeNum: Int? = null, seasonNum: Int? = null): Episode {
        val itemUrl = item.url ?: ""
        val itemName = item.name ?: "Unknown Video"
        val thumb = item.thumbnails.lastOrNull()?.url

        return with(api) {
            newEpisode(itemUrl) {
                this.name = itemName
                this.posterUrl = thumb
                this.data = itemUrl
                if (episodeNum != null) {
                    this.episode = episodeNum
                }
                if (seasonNum != null) {
                    this.season = seasonNum
                }
            }
        }
    }

    /**
     * Formats large view/like counts into readable strings (e.g. 1.2M, 450K).
     */
    fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            count > 0 -> count.toString()
            else -> ""
        }
    }

    /**
     * Formats seconds into HH:MM:SS or MM:SS chapter timestamps.
     */
    fun formatSecondsToTimestamp(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    /**
     * Extracts an accurate or estimated epoch millisecond timestamp for sorting videos strictly chronologically.
     */
    fun extractUploadEpochMs(item: StreamInfoItem, currentTimeMs: Long = System.currentTimeMillis()): Long {
        try {
            val dateWrapper = item.uploadDate
            if (dateWrapper != null) {
                val instant = dateWrapper.instant
                if (instant != null) {
                    return instant.toEpochMilli()
                }
                val offset = dateWrapper.offsetDateTime()
                if (offset != null) {
                    return offset.toInstant().toEpochMilli()
                }
            }
        } catch (_: Throwable) {}

        val textDate = item.textualUploadDate
        if (!textDate.isNullOrBlank()) {
            val parsedMs = parseRelativeTimeToEpochMs(textDate, currentTimeMs)
            if (parsedMs > 0L) {
                return parsedMs
            }
        }

        return 0L
    }

    /**
     * Parses textual relative time into epoch milliseconds.
     */
    fun parseRelativeTimeToEpochMs(text: String, referenceTimeMs: Long = System.currentTimeMillis()): Long {
        val matcher = RELATIVE_TIME_PATTERN.matcher(text)
        if (matcher.find()) {
            val amount = matcher.group(1)?.toLongOrNull() ?: return referenceTimeMs
            val unit = matcher.group(2)?.lowercase() ?: return referenceTimeMs

            val deltaMs = when (unit) {
                "second" -> TimeUnit.SECONDS.toMillis(amount)
                "minute" -> TimeUnit.MINUTES.toMillis(amount)
                "hour" -> TimeUnit.HOURS.toMillis(amount)
                "day" -> TimeUnit.DAYS.toMillis(amount)
                "week" -> TimeUnit.DAYS.toMillis(amount * 7)
                "month" -> TimeUnit.DAYS.toMillis(amount * 30)
                "year" -> TimeUnit.DAYS.toMillis(amount * 365)
                else -> 0L
            }
            return referenceTimeMs - deltaMs
        }
        return referenceTimeMs
    }

    fun deduplicateAndCap(
        items: List<SearchResponse>,
        cap: Int = 50
    ): List<SearchResponse> {
        return items.distinctBy { it.url }.take(cap)
    }
}
