package com.youtube.feed

import android.util.Log
import com.lagradost.cloudstream3.*
import com.youtube.HomeRowType
import com.youtube.ShortsMode
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
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.localization.Localization
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
        private var shortsFeedCache: Pair<Long, List<SearchResponse>>? = null
        private val cachedShortsPool = ConcurrentHashMap.newKeySet<StreamInfoItem>()
        private val channelTabPaginationCache = ConcurrentHashMap<String, Pair<ChannelTabExtractor, org.schabi.newpipe.extractor.Page?>>()

        private val failedChannelsCache = ConcurrentHashMap<String, Pair<Long, String>>()
        private const val FAILURE_BACKOFF_MS = 60 * 1000L

        fun clearFeedCache() {
            channelVideosCache.clear()
            combinedSubCache = null
            shortsFeedCache = null
            cachedShortsPool.clear()
            channelTabPaginationCache.clear()
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
        val rowOrder = YoutubePreferences.getRowOrder()

        for (rowType in rowOrder) {
            if (!YoutubePreferences.isRowEnabled(rowType)) continue

            when (rowType) {
                HomeRowType.COMBINED_SUBSCRIPTIONS -> {
                    val subCount = YoutubePreferences.getSubscribedChannels().size
                    val unwatchedSuffix = if (YoutubePreferences.hideWatchedInSubscriptions) " • Unwatched Only" else ""
                    val label = if (subCount > 0) {
                        "Subscribed: Latest Videos ($subCount Channels$unwatchedSuffix)"
                    } else {
                        "Subscribed: Latest Videos"
                    }
                    rows.add(MainPageData(name = label, data = "feed:subscriptions_combined", horizontalImages = true))
                }

                HomeRowType.SUBSCRIBED_CHANNELS -> {
                    val subCount = YoutubePreferences.getSubscribedChannels().size
                    val label = if (subCount > 0) "Subscribed Channels ($subCount)" else "Subscribed Channels"
                    rows.add(MainPageData(name = label, data = "feed:subscribed_channels", horizontalImages = false))
                }

                HomeRowType.SHORTS -> {
                    if (YoutubePreferences.shortsMode != ShortsMode.HIDE) {
                        rows.add(MainPageData(name = "YouTube Shorts", data = "feed:shorts", horizontalImages = false))
                    }
                }

                HomeRowType.INDIVIDUAL_CHANNELS -> {
                    val subscribedChannels = YoutubePreferences.getSubscribedChannels()
                    for (channel in subscribedChannels) {
                        rows.add(MainPageData(name = "Channel: ${channel.name}", data = "feed:channel:${channel.url}", horizontalImages = true))
                    }
                }

                HomeRowType.TRENDING -> rows.add(MainPageData("Trending (${YoutubePreferences.trendingRegion})", "kiosk:Trending", horizontalImages = true))
                HomeRowType.MOVIES_AND_SHOWS -> rows.add(MainPageData("Movies & Shows", "kiosk:trending_movies_and_shows", horizontalImages = true))
                HomeRowType.MUSIC -> rows.add(MainPageData("Music", "kiosk:trending_music", horizontalImages = true))
                HomeRowType.GAMING -> rows.add(MainPageData("Gaming", "kiosk:trending_gaming", horizontalImages = true))
                HomeRowType.PODCASTS -> rows.add(MainPageData("Podcasts", "kiosk:trending_podcasts_episodes", horizontalImages = true))
                HomeRowType.LIVE_STREAMS -> rows.add(MainPageData("Live Streams", "kiosk:live", horizontalImages = true))
            }
        }

        if (rows.isEmpty()) {
            rows.add(MainPageData("Trending", "kiosk:Trending", horizontalImages = true))
        }

        return rows
    }

    suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val dataKey = request.data

        if (dataKey == "feed:subscriptions_combined") {
            return getCombinedSubscriptionsFeed(request.name)
        }

        if (dataKey == "feed:subscribed_channels") {
            return getSubscribedChannelsFeed(request.name)
        }

        if (dataKey == "feed:shorts") {
            return getShortsFeed(request.name)
        }

        if (dataKey.startsWith("feed:channel:")) {
            val channelUrl = dataKey.removePrefix("feed:channel:")
            return getSingleChannelFeed(request.name, channelUrl, page)
        }

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
            val errorMsg = errorHandler(e)
            Log.e(TAG, "Failed to load kiosk page for '$dataKey': $errorMsg", e)
            val errorCard = createInfoCard("$errorMsg (Tap to retry)")
            return newHomePageResponse(
                listOf(HomePageList(request.name, listOf(errorCard), isHorizontalImages = true)),
                hasNext = false
            )
        }

        val responses = pageData.items.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }
        return newHomePageResponse(listOf(HomePageList(request.name, responses, isHorizontalImages = true)), hasNext = pageData.hasNextPage())
    }

    private fun getSubscribedChannelsFeed(headerTitle: String): HomePageResponse {
        val channels = YoutubePreferences.getSubscribedChannels()
        if (channels.isEmpty()) {
            val placeholder = createInfoCard("No Subscribed Channels (Use Settings to add channels)")
            return newHomePageResponse(listOf(HomePageList(headerTitle, listOf(placeholder), isHorizontalImages = false)), hasNext = false)
        }

        val channelCards = channels.map { channel ->
            with(mainApi) {
                newTvSeriesSearchResponse(channel.name, channel.url, TvType.TvSeries) {
                    this.posterUrl = channel.avatarUrl.takeIf { it.isNotBlank() } ?: defaultIcon
                }
            }
        }

        return newHomePageResponse(listOf(HomePageList(headerTitle, channelCards, isHorizontalImages = false)), hasNext = false)
    }

    private suspend fun getShortsFeed(headerTitle: String): HomePageResponse {
        val now = System.currentTimeMillis()
        val cacheTtl = YoutubePreferences.cacheTtlMs
        val cached = shortsFeedCache

        if (cached != null && (now - cached.first) < cacheTtl && cached.second.isNotEmpty()) {
            return newHomePageResponse(
                listOf(HomePageList(headerTitle, cached.second, isHorizontalImages = false)),
                hasNext = false
            )
        }

        val shortsItems = mutableListOf<SearchResponse>()
        val rowLimit = YoutubePreferences.itemsPerRowLimit

        for (item in cachedShortsPool) {
            YoutubeEpisodeMapper.toSearchResponse(mainApi, item)?.let { shortsItems.add(it) }
            if (shortsItems.size >= rowLimit) break
        }

        if (shortsItems.size < 10) {
            try {
                val searchExtractor = service.getSearchExtractor("#shorts")
                searchExtractor.fetchPage()
                val discovered = searchExtractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter { YoutubeEpisodeMapper.isShort(it) }
                    .mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }
                shortsItems.addAll(discovered)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load trending shorts: ${e.message}")
            }
        }

        val capped = YoutubeEpisodeMapper.deduplicateAndCap(shortsItems, rowLimit)
        if (capped.isNotEmpty()) {
            shortsFeedCache = Pair(now, capped)
        }

        val finalItems = capped.ifEmpty { listOf(createInfoCard("No Shorts Available")) }

        return newHomePageResponse(
            listOf(HomePageList(headerTitle, finalItems, isHorizontalImages = false)),
            hasNext = false
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
            val placeholder = createInfoCard("No Subscribed Channels (Use Settings to add channels)")
            return newHomePageResponse(
                listOf(HomePageList("$headerTitle (Empty)", listOf(placeholder), isHorizontalImages = true)),
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

        for (item in flatItems) {
            if (YoutubeEpisodeMapper.isShort(item)) {
                cachedShortsPool.add(item)
            }
        }

        val shortsMode = YoutubePreferences.shortsMode
        val filteredByShorts = when (shortsMode) {
            ShortsMode.HIDE, ShortsMode.SEPARATE_ROW -> flatItems.filterNot { YoutubeEpisodeMapper.isShort(it) }
            ShortsMode.SHOW_INLINE -> flatItems
        }

        val sortedItems = filteredByShorts.sortedByDescending {
            YoutubeEpisodeMapper.extractUploadEpochMs(it, now)
        }

        var searchResponses = sortedItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }

        val hideWatched = YoutubePreferences.hideWatchedInSubscriptions
        if (hideWatched) {
            searchResponses = searchResponses.filterNot { YoutubePreferences.isWatched(it.url) }
        }

        val rowLimit = YoutubePreferences.itemsPerRowLimit.coerceAtLeast(10)
        val maxLimit = minOf(rowLimit, YoutubePreferences.maxCombinedVideos)
        val cappedItems = YoutubeEpisodeMapper.deduplicateAndCap(searchResponses, maxLimit)

        if (cappedItems.isNotEmpty()) {
            combinedSubCache = Pair(now, cappedItems)
        }

        val finalItems = if (cappedItems.isEmpty()) {
            val emptyMsg = if (hideWatched && sortedItems.isNotEmpty()) {
                "All Caught Up (All latest videos watched)"
            } else {
                "Couldn't load latest uploads (Tap to retry)"
            }
            listOf(createInfoCard(emptyMsg))
        } else {
            cappedItems
        }

        return newHomePageResponse(
            listOf(HomePageList(headerTitle, finalItems, isHorizontalImages = true)),
            hasNext = false
        )
    }

    private suspend fun getSingleChannelFeed(
        headerTitle: String,
        channelUrl: String,
        pageNumber: Int = 1
    ): HomePageResponse {
        val channelName = headerTitle.removePrefix("Channel: ").trim()

        if (pageNumber > 1) {
            val cachedPagination = channelTabPaginationCache[channelUrl]
            val tabExtractor = cachedPagination?.first
            val nextPage = cachedPagination?.second

            if (tabExtractor != null && nextPage != null) {
                try {
                    val pageData = tabExtractor.getPage(nextPage)
                    channelTabPaginationCache[channelUrl] = Pair(tabExtractor, pageData.nextPage)

                    var items = pageData.items.filterIsInstance<StreamInfoItem>().mapNotNull {
                        YoutubeEpisodeMapper.toSearchResponse(mainApi, it)
                    }
                    if (YoutubePreferences.shortsMode == ShortsMode.HIDE) {
                        items = items.filterNot { YoutubeEpisodeMapper.isShortUrl(it.url) }
                    }
                    if (YoutubePreferences.hideWatchedInSubscriptions) {
                        items = items.filterNot { YoutubePreferences.isWatched(it.url) }
                    }

                    return newHomePageResponse(
                        listOf(HomePageList(headerTitle, items, isHorizontalImages = true)),
                        hasNext = pageData.hasNextPage()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load channel page $pageNumber for $channelUrl: ${e.message}")
                    return newHomePageResponse(emptyList(), hasNext = false)
                }
            }
            return newHomePageResponse(emptyList(), hasNext = false)
        }

        val (rawItems, hasNext) = fetchChannelRawStreamItemsWithPaging(channelUrl)
        var items = rawItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }

        if (YoutubePreferences.shortsMode == ShortsMode.HIDE) {
            items = items.filterNot { YoutubeEpisodeMapper.isShortUrl(it.url) }
        }
        if (YoutubePreferences.hideWatchedInSubscriptions) {
            items = items.filterNot { YoutubePreferences.isWatched(it.url) }
        }

        if (items.isEmpty()) {
            val failureInfo = failedChannelsCache[channelUrl]
            val reason = failureInfo?.second ?: "No videos or all watched"
            val retryCard = createInfoCard("$channelName ($reason)", channelUrl)
            return newHomePageResponse(
                listOf(HomePageList(headerTitle, listOf(retryCard), isHorizontalImages = true)),
                hasNext = false
            )
        }

        return newHomePageResponse(
            listOf(HomePageList(headerTitle, items, isHorizontalImages = true)),
            hasNext = hasNext
        )
    }

    private suspend fun fetchChannelRawStreamItemsWithPaging(channelUrl: String): Pair<List<StreamInfoItem>, Boolean> {
        val now = System.currentTimeMillis()
        val failureInfo = failedChannelsCache[channelUrl]
        if (failureInfo != null && (now - failureInfo.first) < FAILURE_BACKOFF_MS) {
            return Pair(emptyList(), false)
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
                failedChannelsCache[channelUrl] = Pair(now, "No videos tab found")
                return Pair(emptyList(), false)
            }

            val videosExtractor = service.getChannelTabExtractor(videosTab)
            videosExtractor.fetchPage()
            val page = videosExtractor.initialPage
            val rawItems = page.items.filterIsInstance<StreamInfoItem>()

            channelTabPaginationCache[channelUrl] = Pair(videosExtractor, page.nextPage)
            failedChannelsCache.remove(channelUrl)

            Pair(rawItems, page.hasNextPage())
        } catch (e: Exception) {
            val errorMsg = errorHandler(e)
            Log.e(TAG, "Failed to fetch stream items with paging for $channelUrl: $errorMsg", e)
            failedChannelsCache[channelUrl] = Pair(now, errorMsg)
            Pair(emptyList(), false)
        }
    }

    private suspend fun fetchChannelRawStreamItems(channelUrl: String, limit: Int = 15): List<StreamInfoItem> {
        val (items, _) = fetchChannelRawStreamItemsWithPaging(channelUrl)
        return items.take(limit)
    }

    suspend fun fetchRecentChannelVideos(channelUrl: String, limit: Int = 20): List<SearchResponse> {
        val now = System.currentTimeMillis()
        val cacheTtl = YoutubePreferences.cacheTtlMs

        val cached = channelVideosCache[channelUrl]
        if (cached != null && (now - cached.first) < cacheTtl && cached.second.isNotEmpty()) {
            return cached.second.take(limit)
        }

        val rawItems = fetchChannelRawStreamItems(channelUrl, limit)
        val list = rawItems.mapNotNull { YoutubeEpisodeMapper.toSearchResponse(mainApi, it) }

        if (list.isNotEmpty()) {
            channelVideosCache[channelUrl] = Pair(now, list)
        }
        return list
    }

    private fun createInfoCard(title: String, url: String = "https://www.youtube.com"): SearchResponse {
        return with(mainApi) {
            newMovieSearchResponse(name = title, url = url, type = TvType.Others).apply {
                this.posterUrl = defaultIcon
            }
        }
    }

    private fun getKioskExtractor(kioskId: String?): KioskExtractor<out InfoItem> {
        val localization = try {
            Localization(
                YoutubePreferences.contentLanguage,
                YoutubePreferences.trendingRegion
            )
        } catch (_: Throwable) {
            null
        }

        return if (kioskId.isNullOrBlank()) {
            service.kioskList.getDefaultKioskExtractor(null, localization)
        } else {
            service.kioskList.getExtractorById(kioskId, null, localization)
        }
    }
}
