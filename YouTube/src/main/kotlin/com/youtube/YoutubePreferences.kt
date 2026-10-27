package com.youtube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class SubscribedChannel(
    val id: String,
    val name: String,
    val url: String,
    val avatarUrl: String = ""
)

enum class HomeRowType(val prefKey: String, val displayName: String, val defaultEnabled: Boolean) {
    COMBINED_SUBSCRIPTIONS("show_combined_subscriptions", "Subscribed: Latest Videos", true),
    SUBSCRIBED_CHANNELS("show_subscribed_channels", "Subscribed Channels (Vertical Posters)", true),
    SHORTS("show_shorts", "YouTube Shorts", true),
    INDIVIDUAL_CHANNELS("show_individual_channels", "Individual Subscribed Channels", true),
    TRENDING("show_trending", "Trending", true),
    MOVIES_AND_SHOWS("show_movies", "Movies & Shows", true),
    MUSIC("show_music", "Music", true),
    GAMING("show_gaming", "Gaming", true),
    PODCASTS("show_podcasts", "Podcasts", true),
    LIVE_STREAMS("show_live", "Live Streams", true);

    companion object {
        fun fromPrefKey(key: String): HomeRowType? = entries.firstOrNull { it.prefKey == key }
    }
}

enum class ShortsMode(val prefValue: String, val displayName: String) {
    SHOW_INLINE("inline", "Show in feeds"),
    SEPARATE_ROW("separate_row", "Dedicated Shorts row"),
    HIDE("hide", "Hide all Shorts");

    companion object {
        fun fromPrefValue(value: String?): ShortsMode = entries.firstOrNull { it.prefValue == value } ?: SHOW_INLINE
    }
}

enum class SearchContentFilter(val prefValue: String, val displayName: String) {
    ALL("all", "All Results"),
    VIDEOS_ONLY("videos", "Videos Only"),
    CHANNELS_ONLY("channels", "Channels Only"),
    PLAYLISTS_ONLY("playlists", "Playlists Only");

    companion object {
        fun fromPrefValue(value: String?): SearchContentFilter = entries.firstOrNull { it.prefValue == value } ?: ALL
    }
}

enum class SearchSortOrder(val prefValue: String, val displayName: String) {
    RELEVANCE("relevance", "Relevance (Default)"),
    UPLOAD_DATE("date", "Upload Date (Latest)"),
    VIEW_COUNT("views", "View Count (Most Viewed)"),
    RATING("rating", "Rating (Top Rated)");

    companion object {
        fun fromPrefValue(value: String?): SearchSortOrder = entries.firstOrNull { it.prefValue == value } ?: RELEVANCE
    }
}

object YoutubePreferences {
    private const val TAG = "YoutubePreferences"
    private const val PREFS_NAME = "youtube_cloudstream_prefs"
    private const val KEY_SUBSCRIPTIONS = "subscribed_channels_json"
    private const val KEY_PREFERRED_QUALITY = "preferred_video_quality"
    private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle_lang"
    private const val KEY_COOKIES_JSON = "cached_cookies_json"
    private const val KEY_COOKIES_LAST_REFRESH = "cookies_last_refresh_timestamp"
    private const val KEY_CACHE_TTL_MINUTES = "cache_ttl_minutes"
    private const val KEY_ROW_TIMEOUT_SECONDS = "row_timeout_seconds"
    private const val KEY_MAX_COMBINED_VIDEOS = "max_combined_videos"

    // Stage 4 Keys
    private const val KEY_SHORTS_MODE = "shorts_handling_mode"
    private const val KEY_HIDE_WATCHED_IN_SUBS = "hide_watched_in_subscriptions"
    private const val KEY_ITEMS_PER_ROW_LIMIT = "items_per_row_limit"
    private const val KEY_ROW_ORDER = "home_row_order_json"
    private const val KEY_TRENDING_REGION = "trending_region_code"
    private const val KEY_CONTENT_LANGUAGE = "content_language_code"
    private const val KEY_SEARCH_CONTENT_FILTER = "search_content_filter"
    private const val KEY_SEARCH_SORT_ORDER = "search_sort_order"
    private const val KEY_SEARCH_HISTORY = "search_history_json"
    private const val KEY_WATCHED_VIDEO_IDS = "watched_video_ids_set"

    val mapper = jacksonObjectMapper()

    inline fun <reified T> fromJson(json: String): T = mapper.readValue(json)
    fun <T> toRawJson(obj: T): String = mapper.writeValueAsString(obj)

    private var prefs: SharedPreferences? = null
    private val cacheClearListeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            seedDefaultsIfEmpty()
            cleanTruncatedAvatars()
        }
    }

    fun registerCacheClearListener(listener: () -> Unit) {
        if (!cacheClearListeners.contains(listener)) {
            cacheClearListeners.add(listener)
        }
    }

    fun notifyCacheCleared() {
        for (listener in cacheClearListeners) {
            try {
                listener.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "Error in cache clear listener: ${e.message}")
            }
        }
    }

    private fun seedDefaultsIfEmpty() {
        val current = getSubscribedChannels()
        if (current.isEmpty()) {
            val defaults = listOf(
                SubscribedChannel(
                    id = "@mkbhd",
                    name = "Marques Brownlee",
                    url = "https://www.youtube.com/@mkbhd",
                    avatarUrl = "https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqd8vNRB0T5_U1QzB2gG-1T3Q=s800-c-k-c0x00ffffff-no-rj"
                ),
                SubscribedChannel(
                    id = "@veritasium",
                    name = "Veritasium",
                    url = "https://www.youtube.com/@veritasium",
                    avatarUrl = "https://yt3.googleusercontent.com/mDsnmfcHgd1fh7whK-hRer1vh3p03-7yYvOtd9-i3y0dM983J4Z04o8s4sYx1L5oKj9y1_01=s800-c-k-c0x00ffffff-no-rj"
                ),
                SubscribedChannel(
                    id = "@kurzgesagt",
                    name = "Kurzgesagt – In a Nutshell",
                    url = "https://www.youtube.com/@kurzgesagt",
                    avatarUrl = "https://yt3.googleusercontent.com/ytc/AIdro_n42Cg8gG3yK_U8K89y6f7j63H5y7m8L5h4D1=s800-c-k-c0x00ffffff-no-rj"
                )
            )
            saveSubscribedChannels(defaults)
        }
    }

    private fun cleanTruncatedAvatars() {
        val channels = getSubscribedChannels()
        var modified = false
        val cleaned = channels.map { ch ->
            if (ch.avatarUrl.contains("yt3.googleusercontent.com/ytc/AIdro_k6QG_81F4n3yC") ||
                ch.avatarUrl.contains("AIdro_k6QG_81F4n3yC-G1l9jX3b4_wX2D8r_E_r_q") ||
                (ch.avatarUrl.isNotBlank() && ch.avatarUrl.length < 30)
            ) {
                modified = true
                val restored = when {
                    ch.id.equals("@mkbhd", ignoreCase = true) || ch.url.contains("@mkbhd", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqd8vNRB0T5_U1QzB2gG-1T3Q=s800-c-k-c0x00ffffff-no-rj"
                    ch.id.equals("@veritasium", ignoreCase = true) || ch.url.contains("@veritasium", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/mDsnmfcHgd1fh7whK-hRer1vh3p03-7yYvOtd9-i3y0dM983J4Z04o8s4sYx1L5oKj9y1_01=s800-c-k-c0x00ffffff-no-rj"
                    ch.id.equals("@kurzgesagt", ignoreCase = true) || ch.url.contains("@kurzgesagt", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/ytc/AIdro_n42Cg8gG3yK_U8K89y6f7j63H5y7m8L5h4D1=s800-c-k-c0x00ffffff-no-rj"
                    else -> ""
                }
                ch.copy(avatarUrl = restored)
            } else if (ch.avatarUrl.isBlank()) {
                val restored = when {
                    ch.id.equals("@mkbhd", ignoreCase = true) || ch.url.contains("@mkbhd", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqd8vNRB0T5_U1QzB2gG-1T3Q=s800-c-k-c0x00ffffff-no-rj"
                    ch.id.equals("@veritasium", ignoreCase = true) || ch.url.contains("@veritasium", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/mDsnmfcHgd1fh7whK-hRer1vh3p03-7yYvOtd9-i3y0dM983J4Z04o8s4sYx1L5oKj9y1_01=s800-c-k-c0x00ffffff-no-rj"
                    ch.id.equals("@kurzgesagt", ignoreCase = true) || ch.url.contains("@kurzgesagt", ignoreCase = true) ->
                        "https://yt3.googleusercontent.com/ytc/AIdro_n42Cg8gG3yK_U8K89y6f7j63H5y7m8L5h4D1=s800-c-k-c0x00ffffff-no-rj"
                    else -> ""
                }
                if (restored.isNotBlank()) {
                    modified = true
                    ch.copy(avatarUrl = restored)
                } else ch
            } else {
                ch
            }
        }
        if (modified) {
            Log.d(TAG, "Restored valid channel avatar URLs")
            saveSubscribedChannels(cleaned)
        }
    }

    fun getSubscribedChannels(): List<SubscribedChannel> {
        val raw = prefs?.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return try {
            fromJson<List<SubscribedChannel>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscribed channels JSON: ${e.message}", e)
            emptyList()
        }
    }

    fun saveSubscribedChannels(channels: List<SubscribedChannel>) {
        val json = toRawJson(channels)
        prefs?.edit()?.putString(KEY_SUBSCRIPTIONS, json)?.apply()
    }

    fun addChannel(channel: SubscribedChannel) {
        val current = getSubscribedChannels().toMutableList()
        if (current.none { it.url.equals(channel.url, ignoreCase = true) || it.id.equals(channel.id, ignoreCase = true) }) {
            current.add(0, channel)
            saveSubscribedChannels(current)
        }
    }

    fun importChannels(newChannels: List<SubscribedChannel>): Int {
        val current = getSubscribedChannels().toMutableList()
        var addedCount = 0
        for (channel in newChannels) {
            if (current.none { it.url.equals(channel.url, ignoreCase = true) || it.id.equals(channel.id, ignoreCase = true) }) {
                current.add(channel)
                addedCount++
            }
        }
        if (addedCount > 0) {
            saveSubscribedChannels(current)
            notifyCacheCleared()
        }
        return addedCount
    }

    fun removeChannel(idOrUrl: String) {
        val current = getSubscribedChannels().toMutableList()
        current.removeAll { it.id.equals(idOrUrl, ignoreCase = true) || it.url.equals(idOrUrl, ignoreCase = true) }
        saveSubscribedChannels(current)
    }

    fun updateChannelAvatar(idOrUrl: String, newAvatarUrl: String) {
        if (newAvatarUrl.isBlank()) return
        val current = getSubscribedChannels().toMutableList()
        var updated = false
        for (i in current.indices) {
            val ch = current[i]
            if (ch.id.equals(idOrUrl, ignoreCase = true) || ch.url.equals(idOrUrl, ignoreCase = true)) {
                if (ch.avatarUrl != newAvatarUrl) {
                    current[i] = ch.copy(avatarUrl = newAvatarUrl)
                    updated = true
                }
            }
        }
        if (updated) {
            Log.d(TAG, "Updated channel avatar for $idOrUrl -> $newAvatarUrl")
            saveSubscribedChannels(current)
        }
    }

    fun getCachedCookies(): Map<String, String> {
        val raw = prefs?.getString(KEY_COOKIES_JSON, null) ?: return emptyMap()
        return try {
            fromJson<Map<String, String>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached cookies JSON: ${e.message}")
            emptyMap()
        }
    }

    fun saveCachedCookies(cookies: Map<String, String>) {
        try {
            val json = toRawJson(cookies)
            prefs?.edit()?.putString(KEY_COOKIES_JSON, json)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize cached cookies: ${e.message}")
        }
    }

    fun getCookiesLastRefresh(): Long {
        return prefs?.getLong(KEY_COOKIES_LAST_REFRESH, 0L) ?: 0L
    }

    fun saveCookiesLastRefresh(timestamp: Long) {
        prefs?.edit()?.putLong(KEY_COOKIES_LAST_REFRESH, timestamp)?.apply()
    }

    // --- Typed Home Row Settings ---

    fun isRowEnabled(rowType: HomeRowType): Boolean {
        return prefs?.getBoolean(rowType.prefKey, rowType.defaultEnabled) ?: rowType.defaultEnabled
    }

    fun setRowEnabled(rowType: HomeRowType, enabled: Boolean) {
        prefs?.edit()?.putBoolean(rowType.prefKey, enabled)?.apply()
    }

    var showCombinedSubscriptions: Boolean get() = isRowEnabled(HomeRowType.COMBINED_SUBSCRIPTIONS); set(v) = setRowEnabled(HomeRowType.COMBINED_SUBSCRIPTIONS, v)
    var showShortsRow: Boolean get() = isRowEnabled(HomeRowType.SHORTS); set(v) = setRowEnabled(HomeRowType.SHORTS, v)
    var showIndividualChannelRows: Boolean get() = isRowEnabled(HomeRowType.INDIVIDUAL_CHANNELS); set(v) = setRowEnabled(HomeRowType.INDIVIDUAL_CHANNELS, v)
    var showTrending: Boolean get() = isRowEnabled(HomeRowType.TRENDING); set(v) = setRowEnabled(HomeRowType.TRENDING, v)
    var showMusic: Boolean get() = isRowEnabled(HomeRowType.MUSIC); set(v) = setRowEnabled(HomeRowType.MUSIC, v)
    var showGaming: Boolean get() = isRowEnabled(HomeRowType.GAMING); set(v) = setRowEnabled(HomeRowType.GAMING, v)
    var showMovies: Boolean get() = isRowEnabled(HomeRowType.MOVIES_AND_SHOWS); set(v) = setRowEnabled(HomeRowType.MOVIES_AND_SHOWS, v)
    var showPodcasts: Boolean get() = isRowEnabled(HomeRowType.PODCASTS); set(v) = setRowEnabled(HomeRowType.PODCASTS, v)
    var showLive: Boolean get() = isRowEnabled(HomeRowType.LIVE_STREAMS); set(v) = setRowEnabled(HomeRowType.LIVE_STREAMS, v)

    // Configurable Cache TTL in minutes (e.g. 1, 5, 15, 30, 60; default 5)
    var cacheTtlMinutes: Int
        get() = prefs?.getInt(KEY_CACHE_TTL_MINUTES, 5) ?: 5
        set(value) { prefs?.edit()?.putInt(KEY_CACHE_TTL_MINUTES, value.coerceAtLeast(1))?.apply() }

    val cacheTtlMs: Long
        get() = cacheTtlMinutes * 60 * 1000L

    // Per-channel/row timeout in seconds (default 6)
    var rowTimeoutSeconds: Int
        get() = prefs?.getInt(KEY_ROW_TIMEOUT_SECONDS, 6) ?: 6
        set(value) { prefs?.edit()?.putInt(KEY_ROW_TIMEOUT_SECONDS, value.coerceIn(2, 30))?.apply() }

    val rowTimeoutMs: Long
        get() = rowTimeoutSeconds * 1000L

    // Max items in combined subscriptions feed (default 50)
    var maxCombinedVideos: Int
        get() = prefs?.getInt(KEY_MAX_COMBINED_VIDEOS, 50) ?: 50
        set(value) { prefs?.edit()?.putInt(KEY_MAX_COMBINED_VIDEOS, value.coerceIn(10, 200))?.apply() }

    // Preferred video resolution
    var preferredQuality: String
        get() = prefs?.getString(KEY_PREFERRED_QUALITY, "1080p") ?: "1080p"
        set(value) { prefs?.edit()?.putString(KEY_PREFERRED_QUALITY, value)?.apply() }

    // Preferred subtitle language
    var preferredSubtitle: String
        get() = prefs?.getString(KEY_PREFERRED_SUBTITLE, "English") ?: "English"
        set(value) { prefs?.edit()?.putString(KEY_PREFERRED_SUBTITLE, value)?.apply() }

    // --- Stage 4: Core User Features Preferences ---

    // 1. Shorts Handling Mode
    var shortsMode: ShortsMode
        get() = ShortsMode.fromPrefValue(prefs?.getString(KEY_SHORTS_MODE, ShortsMode.SHOW_INLINE.prefValue))
        set(value) {
            prefs?.edit()?.putString(KEY_SHORTS_MODE, value.prefValue)?.apply()
            notifyCacheCleared()
        }

    // 2. Hide Watched / Unwatched Only in Subscription Feeds
    var hideWatchedInSubscriptions: Boolean
        get() = prefs?.getBoolean(KEY_HIDE_WATCHED_IN_SUBS, false) ?: false
        set(value) {
            prefs?.edit()?.putBoolean(KEY_HIDE_WATCHED_IN_SUBS, value)?.apply()
            notifyCacheCleared()
        }

    // 3. Per-Row Item Limits (Default: 20)
    var itemsPerRowLimit: Int
        get() = prefs?.getInt(KEY_ITEMS_PER_ROW_LIMIT, 20) ?: 20
        set(value) {
            prefs?.edit()?.putInt(KEY_ITEMS_PER_ROW_LIMIT, value.coerceIn(5, 50))?.apply()
            notifyCacheCleared()
        }

    // 4. Home Row Order
    fun getRowOrder(): List<HomeRowType> {
        val raw = prefs?.getString(KEY_ROW_ORDER, null) ?: return defaultRowOrder
        return try {
            val keys = fromJson<List<String>>(raw)
            val parsed = keys.mapNotNull { HomeRowType.fromPrefKey(it) }.toMutableList()
            if (!parsed.contains(HomeRowType.SUBSCRIBED_CHANNELS)) {
                val subIdx = parsed.indexOf(HomeRowType.COMBINED_SUBSCRIPTIONS)
                if (subIdx >= 0) {
                    parsed.add(subIdx + 1, HomeRowType.SUBSCRIBED_CHANNELS)
                } else {
                    parsed.add(0, HomeRowType.SUBSCRIBED_CHANNELS)
                }
            }
            val missing = defaultRowOrder.filterNot { parsed.contains(it) }
            parsed + missing
        } catch (_: Exception) {
            defaultRowOrder
        }
    }

    fun saveRowOrder(order: List<HomeRowType>) {
        val keys = order.map { it.prefKey }
        prefs?.edit()?.putString(KEY_ROW_ORDER, toRawJson(keys))?.apply()
    }

    val defaultRowOrder: List<HomeRowType> = listOf(
        HomeRowType.COMBINED_SUBSCRIPTIONS,
        HomeRowType.SUBSCRIBED_CHANNELS,
        HomeRowType.SHORTS,
        HomeRowType.INDIVIDUAL_CHANNELS,
        HomeRowType.TRENDING,
        HomeRowType.MOVIES_AND_SHOWS,
        HomeRowType.MUSIC,
        HomeRowType.GAMING,
        HomeRowType.PODCASTS,
        HomeRowType.LIVE_STREAMS
    )

    // 5. Trending Region & Content Language
    var trendingRegion: String
        get() = prefs?.getString(KEY_TRENDING_REGION, "US") ?: "US"
        set(value) {
            prefs?.edit()?.putString(KEY_TRENDING_REGION, value.uppercase())?.apply()
            notifyCacheCleared()
        }

    var contentLanguage: String
        get() = prefs?.getString(KEY_CONTENT_LANGUAGE, "en") ?: "en"
        set(value) {
            prefs?.edit()?.putString(KEY_CONTENT_LANGUAGE, value.lowercase())?.apply()
            notifyCacheCleared()
        }

    // 6. Search Filters & Sort
    var searchContentFilter: SearchContentFilter
        get() = SearchContentFilter.fromPrefValue(prefs?.getString(KEY_SEARCH_CONTENT_FILTER, SearchContentFilter.ALL.prefValue))
        set(value) { prefs?.edit()?.putString(KEY_SEARCH_CONTENT_FILTER, value.prefValue)?.apply() }

    var searchSortOrder: SearchSortOrder
        get() = SearchSortOrder.fromPrefValue(prefs?.getString(KEY_SEARCH_SORT_ORDER, SearchSortOrder.RELEVANCE.prefValue))
        set(value) { prefs?.edit()?.putString(KEY_SEARCH_SORT_ORDER, value.prefValue)?.apply() }

    // 7. Search History
    fun getSearchHistory(): List<String> {
        val raw = prefs?.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            fromJson<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSearchHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getSearchHistory().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(20)
        prefs?.edit()?.putString(KEY_SEARCH_HISTORY, toRawJson(capped))?.apply()
    }

    fun clearSearchHistory() {
        prefs?.edit()?.remove(KEY_SEARCH_HISTORY)?.apply()
    }

    // 8. Watched Tracking & Check
    fun isWatched(urlOrId: String): Boolean {
        val clean = urlOrId.trim()
        val watchedSet = prefs?.getStringSet(KEY_WATCHED_VIDEO_IDS, null) ?: emptySet()
        val videoId = if (clean.contains("v=")) {
            clean.substringAfter("v=").substringBefore("&")
        } else if (clean.contains("/shorts/")) {
            clean.substringAfter("/shorts/").substringBefore("?")
        } else {
            clean.substringAfterLast("/")
        }

        if (watchedSet.contains(clean) || watchedSet.contains(videoId)) {
            return true
        }

        // Check CloudStream's native resume status via reflection (if running in app)
        try {
            val helperClass = Class.forName("com.lagradost.cloudstream3.utils.DataStoreHelper")
            val getViewResumeMethod = helperClass.getMethod("getViewResume", java.lang.Integer::class.java)
            val resumeStatus = getViewResumeMethod.invoke(null, clean.hashCode())
            if (resumeStatus != null) {
                val progressField = resumeStatus.javaClass.getDeclaredField("progress").apply { isAccessible = true }
                val durationField = resumeStatus.javaClass.getDeclaredField("duration").apply { isAccessible = true }
                val progress = (progressField.get(resumeStatus) as? Number)?.toLong() ?: 0L
                val duration = (durationField.get(resumeStatus) as? Number)?.toLong() ?: 0L
                if (duration > 0 && (progress.toFloat() / duration.toFloat()) >= 0.9f) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    fun markWatched(urlOrId: String, watched: Boolean) {
        val clean = urlOrId.trim()
        val currentSet = (prefs?.getStringSet(KEY_WATCHED_VIDEO_IDS, null) ?: emptySet()).toMutableSet()
        val videoId = if (clean.contains("v=")) {
            clean.substringAfter("v=").substringBefore("&")
        } else if (clean.contains("/shorts/")) {
            clean.substringAfter("/shorts/").substringBefore("?")
        } else {
            clean.substringAfterLast("/")
        }

        if (watched) {
            currentSet.add(clean)
            if (videoId.isNotBlank()) currentSet.add(videoId)
        } else {
            currentSet.remove(clean)
            if (videoId.isNotBlank()) currentSet.remove(videoId)
        }
        prefs?.edit()?.putStringSet(KEY_WATCHED_VIDEO_IDS, currentSet)?.apply()
        notifyCacheCleared()
    }

    fun clearWatchHistory() {
        prefs?.edit()?.remove(KEY_WATCHED_VIDEO_IDS)?.apply()
        notifyCacheCleared()
    }

    fun resetToDefaults() {
        prefs?.edit()?.clear()?.apply()
        seedDefaultsIfEmpty()
        notifyCacheCleared()
    }
}
