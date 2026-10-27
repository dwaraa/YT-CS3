package com.youtube.feed

import android.util.Log
import com.lagradost.cloudstream3.*
import com.youtube.HomeRowType
import com.youtube.SubscribedChannel
import com.youtube.YoutubePreferences
import com.youtube.extractor.YoutubeEpisodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

class YoutubeFeedHandler(
    private val mainApi: MainAPI,
    private val defaultIcon: String,
    private val errorHandler: (Throwable) -> String
) {
    companion object {
        private const val TAG = "YoutubeFeedHandler"

        private val channelVideosCache = ConcurrentHashMap<String, Pair<Long, List<SearchResponse>>>()
        private var combinedSubCache: Pair<Long, List<SearchResponse>>? = null

        private val failedChannelsCache = ConcurrentHashMap<String, Pair<Long, String>>()
        private const val FAILURE_BACKOFF_MS = 60 * 1000L

        fun clearFeedCache() {
            channelVideosCache.clear()
            combinedSubCache = null
            failedChannelsCache.clear()
            Log.d(TAG, "In-memory feed caches cleared")
        }
    }

    init {
        YoutubePreferences.registerCacheClearListener {
            clearFeedCache()
        }
    }

    private val service = ServiceList.YouTube
    private val mainPagePaginationCache = mutableMapOf<String, org.schabi.newpipe.extractor.Page?>()

    fun getCustomHomeRows(): List<MainPageData> {
        val rows = mutableListOf<MainPageData>()

        // 1. Combined Subscriptions Row
        if (YoutubePreferences.isRowEnabled(HomeRowType.COMBINED_SUBSCRIPTIONS)) {
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
        if (YoutubePreferences.isRowEnabled(HomeRowType.INDIVIDUAL_CHANNELS)) {
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

        // 3. Built-in Category Rows
        if (YoutubePreferences.isRowEnabled(HomeRowType.TRENDING)) {
            rows.add(MainPageData("Trending", "kiosk:Trending", horizontalImages = true))
        }
        if (YoutubePreferences.isRowEnabled(HomeRowType.MOVIES_AND_SHOWS)) {
            rows.add(MainPageData("Movies & Shows", "kiosk:trending_movies_and_shows", horizontalImages = true))
        }
        if (YoutubePreferences.isRowEnabled(HomeRowType.MUSIC)) {
            rows.add(MainPageData("Music", "kiosk:trending_music", horizontalImages = true))
        }
        if (YoutubePreferences.isRowEnabled(HomeRowType.GAMING)) {
            rows.add(MainPageData("Gaming", "kiosk:trending_gaming", horizontalImages = true))
        }
        if (YoutubePreferences.isRowEnabled(HomeRowType.PODCASTS)) {
            rows.add(MainPageData("Podcasts", "kiosk:trending_podcasts_episodes", horizontalImages = true))
        }
        if (YoutubePreferences.isRowEnabled(HomeRowType.LIVE_STREAMS)) {
            rows.add(MainPageData("Live Streams", "kiosk:live", horizontalImages = true))
        }

        if (rows.isEmpty()) {
            rows.add(MainPageData("Trending", "kiosk:Trending", horizontalImages = true))
        }

        return rows
    }

    suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
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
        if (page == 1) mainPagePaginationCache.remove(dataKey)

        val extractor = getKioskExtractor(kioskId)

        val pageData = try {
            if (page == 1) {
                extractor.fetchPage()
                extractor.initialPage.also {
                    mainPagePaginationCache[dataKey] = it.nextPage
                }
            } else {
                val next = mainPagePaginationCache[dataKey] ?: return newHomePageResponse(emptyList(), false)
                extractor.getPage(next).also {
                    mainPagePaginationCache[dataKey] = it.nextPage
                }
            }
        } catch (e: Exception) {
            val err = errorHandler(e)
            Log.e(TAG, "Failed to load kiosk '$dataKey': $err", e)
            val errorItem = with(mainApi) {
                newMovieSearchResponse(
                    name = "⚠️ Couldn't load ${request.name} ($err)",
                    url = "https://www.youtube.com",
                    type = TvType.Others
                ) {
                    this.posterUrl = defaultIcon
                }
            }
            return newHomePageResponse(
                listOf(HomePageList(request.name, listOf(errorItem), isHorizontalImages = true)),
                false
            )
        }

        val results = pageData.items.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }

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

    private suspend fun getCombinedSubscriptionsFeed(headerTitle: String): HomePageResponse {
        val now = System.currentTimeMillis()
        val cacheTtl = YoutubePreferences.cacheTtlMs
        val cached = combinedSubCache

        if (cached != null && (now - cached.first) < cacheTtl && cached.second.isNotEmpty()) {
            return newHomePageResponse(
                listOf(HomePageList(headerTitle, cached.second, isHorizontalImages = true)),
                hasNext = false
            )
        }

        val channels = YoutubePreferences.getSubscribedChannels()
        if (channels.isEmpty()) {
            val placeholder = with(mainApi) {
                newMovieSearchResponse(
                    name = "ℹ️ No Subscribed Channels (Tap Settings ⚙️ to add channels)",
                    url = "https://www.youtube.com",
                    type = TvType.Others
                ) {
                    this.posterUrl = defaultIcon
                }
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

        val timeoutMs = YoutubePreferences.rowTimeoutMs
        val semaphore = Semaphore(5)

        val rawItemsFromChannels: List<List<StreamInfoItem>> = coroutineScope {
            channels.map { channel ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            withTimeoutOrNull(timeoutMs) {
                                fetchChannelRawStreamItems(channel.url, limit = 15)
                            } ?: run {
                                Log.w(TAG, "Timed out fetching videos for channel ${channel.name} (${channel.url}) after ${timeoutMs}ms")
                                emptyList()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error fetching channel ${channel.name}: ${e.message}")
                            emptyList()
                        }
                    }
                }
            }.awaitAll()
        }

        val flatItems = rawItemsFromChannels.flatten()

        val sortedItems = flatItems.sortedByDescending {
            YoutubeEpisodeMapper.extractUploadEpochMs(it, now)
        }

        val searchResponses = sortedItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }
        val cappedItems = YoutubeEpisodeMapper.deduplicateAndCap(
            searchResponses,
            YoutubePreferences.maxCombinedVideos
        )

        if (cappedItems.isNotEmpty()) {
            combinedSubCache = Pair(now, cappedItems)
        }

        val finalItems = if (cappedItems.isEmpty()) {
            listOf(
                with(mainApi) {
                    newMovieSearchResponse(
                        name = "⚠️ Couldn't load latest uploads (Tap to retry)",
                        url = "https://www.youtube.com",
                        type = TvType.Others
                    ) {
                        this.posterUrl = defaultIcon
                    }
                }
            )
        } else {
            cappedItems
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

            val retryCard = with(mainApi) {
                newMovieSearchResponse(
                    name = "⚠️ Couldn't load $channelName ($reason) - Tap to retry",
                    url = channelUrl,
                    type = TvType.Others
                ) {
                    this.posterUrl = defaultIcon
                }
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

    private suspend fun fetchChannelRawStreamItems(channelUrl: String, limit: Int = 15): List<StreamInfoItem> {
        val now = System.currentTimeMillis()

        val failureInfo = failedChannelsCache[channelUrl]
        if (failureInfo != null && (now - failureInfo.first) < FAILURE_BACKOFF_MS) {
            val remainingSec = (FAILURE_BACKOFF_MS - (now - failureInfo.first)) / 1000
            Log.d(TAG, "Skipping channel $channelUrl in backoff ($remainingSec s remaining): ${failureInfo.second}")
            return emptyList()
        }

        return try {
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()

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
            val rawItems = page.items.filterIsInstance<StreamInfoItem>().take(limit)

            failedChannelsCache.remove(channelUrl)
            rawItems
        } catch (e: Exception) {
            val errorMsg = errorHandler(e)
            Log.e(TAG, "Failed to fetch stream items for channel $channelUrl: $errorMsg", e)
            failedChannelsCache[channelUrl] = Pair(now, errorMsg)
            emptyList()
        }
    }

    suspend fun fetchRecentChannelVideos(channelUrl: String, limit: Int = 20): List<SearchResponse> {
        val now = System.currentTimeMillis()
        val cacheTtl = YoutubePreferences.cacheTtlMs

        val cached = channelVideosCache[channelUrl]
        if (cached != null && (now - cached.first) < cacheTtl && cached.second.isNotEmpty()) {
            return cached.second
        }

        val rawItems = fetchChannelRawStreamItems(channelUrl, limit)
        val list = rawItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }

        if (list.isNotEmpty()) {
            channelVideosCache[channelUrl] = Pair(now, list)
        }
        return list
    }

    private fun getKioskExtractor(kioskId: String?): KioskExtractor<out InfoItem> {
        return if (kioskId.isNullOrBlank()) {
            service.kioskList.getDefaultKioskExtractor(null)
        } else {
            service.kioskList.getExtractorById(kioskId, null)
        }
    }
}
