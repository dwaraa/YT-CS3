package com.youtube.search

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.youtube.extractor.YoutubeEpisodeMapper
import org.schabi.newpipe.extractor.ServiceList

object YoutubeSearchHandler {
    private const val TAG = "YoutubeSearchHandler"
    private val service = ServiceList.YouTube

    suspend fun search(
        api: MainAPI,
        query: String,
        maxPages: Int = 4,
        errorHandler: (Throwable) -> String
    ): List<SearchResponse> {
        val extractor = service.getSearchExtractor(query)
        val allItems = mutableListOf<SearchResponse>()

        try {
            extractor.fetchPage()
            var page = extractor.initialPage
            allItems.addAll(page.items.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(api, it) })

            var pagesLoaded = 1
            while (page.hasNextPage() && pagesLoaded < maxPages) {
                page = extractor.getPage(page.nextPage)
                allItems.addAll(page.items.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(api, it) })
                pagesLoaded++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search query '$query' encountered error: ${errorHandler(e)}", e)
        }

        return allItems.distinctBy { it.url }
    }

    suspend fun quickSearch(api: MainAPI, query: String): List<SearchResponse> {
        return try {
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()
            extractor.initialPage.items.take(15).mapNotNull { YoutubeEpisodeMapper.toSearchResponse(api, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Quick search for '$query' failed: ${e.message}")
            emptyList()
        }
    }
}
