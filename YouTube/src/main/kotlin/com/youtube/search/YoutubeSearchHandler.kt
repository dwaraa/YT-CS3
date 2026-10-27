package com.youtube.search

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.youtube.SearchContentFilter
import com.youtube.SearchSortOrder
import com.youtube.ShortsMode
import com.youtube.YoutubePreferences
import com.youtube.extractor.YoutubeEpisodeMapper
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YoutubeSearchHandler {
    private const val TAG = "YoutubeSearchHandler"
    private val service = ServiceList.YouTube

    data class ParsedSearchQuery(
        val cleanQuery: String,
        val contentFilter: SearchContentFilter,
        val sortOrder: SearchSortOrder,
        val isShortsOnly: Boolean = false,
        val durationFilter: String? = null // "short", "medium", "long"
    )

    fun parseQuery(rawQuery: String): ParsedSearchQuery {
        var clean = rawQuery
        var contentFilter = YoutubePreferences.searchContentFilter
        var sortOrder = YoutubePreferences.searchSortOrder
        var isShortsOnly = false
        var durationFilter: String? = null

        // Parse type:...
        val typeRegex = Regex("""\btype:(video|channel|playlist|short|shorts)\b""", RegexOption.IGNORE_CASE)
        typeRegex.find(clean)?.let { match ->
            val typeVal = match.groupValues[1].lowercase()
            when (typeVal) {
                "video" -> contentFilter = SearchContentFilter.VIDEOS_ONLY
                "channel" -> contentFilter = SearchContentFilter.CHANNELS_ONLY
                "playlist" -> contentFilter = SearchContentFilter.PLAYLISTS_ONLY
                "short", "shorts" -> isShortsOnly = true
            }
            clean = clean.replace(match.value, "").trim()
        }

        // Parse sort:...
        val sortRegex = Regex("""\bsort:(date|views|rating|relevance)\b""", RegexOption.IGNORE_CASE)
        sortRegex.find(clean)?.let { match ->
            when (match.groupValues[1].lowercase()) {
                "date" -> sortOrder = SearchSortOrder.UPLOAD_DATE
                "views" -> sortOrder = SearchSortOrder.VIEW_COUNT
                "rating" -> sortOrder = SearchSortOrder.RATING
                "relevance" -> sortOrder = SearchSortOrder.RELEVANCE
            }
            clean = clean.replace(match.value, "").trim()
        }

        // Parse duration:...
        val durationRegex = Regex("""\bduration:(short|medium|long)\b""", RegexOption.IGNORE_CASE)
        durationRegex.find(clean)?.let { match ->
            durationFilter = match.groupValues[1].lowercase()
            clean = clean.replace(match.value, "").trim()
        }

        return ParsedSearchQuery(
            cleanQuery = clean.ifBlank { rawQuery },
            contentFilter = contentFilter,
            sortOrder = sortOrder,
            isShortsOnly = isShortsOnly,
            durationFilter = durationFilter
        )
    }

    suspend fun search(
        api: MainAPI,
        query: String,
        maxPages: Int = 4,
        errorHandler: (Throwable) -> String
    ): List<SearchResponse> {
        val parsed = parseQuery(query)
        val searchQuery = if (parsed.isShortsOnly && !parsed.cleanQuery.contains("#shorts")) {
            "${parsed.cleanQuery} #shorts"
        } else {
            parsed.cleanQuery
        }

        YoutubePreferences.addSearchHistory(parsed.cleanQuery)

        val rawItems = mutableListOf<InfoItem>()

        try {
            val extractor = service.getSearchExtractor(searchQuery)
            extractor.fetchPage()
            var page = extractor.initialPage
            rawItems.addAll(page.items)

            var pagesLoaded = 1
            while (page.hasNextPage() && pagesLoaded < maxPages) {
                page = extractor.getPage(page.nextPage)
                rawItems.addAll(page.items)
                pagesLoaded++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search query '$searchQuery' encountered error: ${errorHandler(e)}", e)
        }

        // Filter items
        var filteredItems = rawItems.filter { item ->
            when {
                parsed.isShortsOnly -> {
                    item is StreamInfoItem && YoutubeEpisodeMapper.isShort(item)
                }
                parsed.contentFilter == SearchContentFilter.VIDEOS_ONLY -> {
                    item is StreamInfoItem && (YoutubePreferences.shortsMode != ShortsMode.HIDE || !YoutubeEpisodeMapper.isShort(item))
                }
                parsed.contentFilter == SearchContentFilter.CHANNELS_ONLY -> {
                    item is ChannelInfoItem
                }
                parsed.contentFilter == SearchContentFilter.PLAYLISTS_ONLY -> {
                    item is PlaylistInfoItem
                }
                else -> {
                    if (YoutubePreferences.shortsMode == ShortsMode.HIDE && item is StreamInfoItem) {
                        !YoutubeEpisodeMapper.isShort(item)
                    } else {
                        true
                    }
                }
            }
        }

        // Apply duration filter
        if (parsed.durationFilter != null) {
            filteredItems = filteredItems.filter { item ->
                if (item is StreamInfoItem) {
                    when (parsed.durationFilter) {
                        "short" -> item.duration in 1..240 // under 4 minutes
                        "medium" -> item.duration in 241..1200 // 4-20 minutes
                        "long" -> item.duration > 1200 // over 20 minutes
                        else -> true
                    }
                } else true
            }
        }

        // Apply sort order
        if (parsed.sortOrder == SearchSortOrder.UPLOAD_DATE) {
            filteredItems = filteredItems.sortedByDescending { item ->
                if (item is StreamInfoItem) {
                    YoutubeEpisodeMapper.extractUploadEpochMs(item)
                } else 0L
            }
        }

        val responses = filteredItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(api, it) }
        return responses.distinctBy { it.url }
    }

    suspend fun quickSearch(api: MainAPI, query: String): List<SearchResponse> {
        val parsed = parseQuery(query)
        return try {
            val extractor = service.getSearchExtractor(parsed.cleanQuery)
            extractor.fetchPage()
            val raw = extractor.initialPage.items.take(15)
            raw.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(api, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Quick search for '$query' failed: ${e.message}")
            emptyList()
        }
    }

    fun getSearchSuggestions(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return YoutubePreferences.getSearchHistory()
        }
        return try {
            service.suggestionExtractor.suggestionList(trimmed)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get suggestions for '$trimmed': ${e.message}")
            emptyList()
        }
    }
}
