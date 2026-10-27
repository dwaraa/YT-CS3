package com.youtube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson

data class SubscribedChannel(
    val id: String,
    val name: String,
    val url: String,
    val avatarUrl: String = ""
)

enum class HomeRowType(val prefKey: String, val displayName: String, val defaultEnabled: Boolean) {
    COMBINED_SUBSCRIPTIONS("show_combined_subscriptions", "Subscribed: Latest Videos", true),
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
                    avatarUrl = ""
                ),
                SubscribedChannel(
                    id = "@veritasium",
                    name = "Veritasium",
                    url = "https://www.youtube.com/@veritasium",
                    avatarUrl = ""
                ),
                SubscribedChannel(
                    id = "@kurzgesagt",
                    name = "Kurzgesagt – In a Nutshell",
                    url = "https://www.youtube.com/@kurzgesagt",
                    avatarUrl = ""
                )
            )
            saveSubscribedChannels(defaults)
        }
    }

    // Clean up any previously stored truncated or placeholder avatars from early builds
    private fun cleanTruncatedAvatars() {
        val channels = getSubscribedChannels()
        var modified = false
        val cleaned = channels.map { ch ->
            if (ch.avatarUrl.contains("yt3.googleusercontent.com/ytc/AIdro_k6QG_81F4n3yC") ||
                ch.avatarUrl.contains("AIdro_k6QG_81F4n3yC-G1l9jX3b4_wX2D8r_E_r_q") ||
                ch.avatarUrl.length < 30
            ) {
                modified = true
                ch.copy(avatarUrl = "")
            } else {
                ch
            }
        }
        if (modified) {
            Log.d(TAG, "Cleaned legacy truncated avatar URLs; will resolve dynamically at runtime")
            saveSubscribedChannels(cleaned)
        }
    }

    fun getSubscribedChannels(): List<SubscribedChannel> {
        val raw = prefs?.getString(KEY_SUBSCRIPTIONS, null) ?: return emptyList()
        return try {
            parseJson<List<SubscribedChannel>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse subscribed channels JSON: ${e.message}", e)
            emptyList()
        }
    }

    fun saveSubscribedChannels(channels: List<SubscribedChannel>) {
        val json = channels.toJson()
        prefs?.edit()?.putString(KEY_SUBSCRIPTIONS, json)?.apply()
    }

    fun addChannel(channel: SubscribedChannel) {
        val current = getSubscribedChannels().toMutableList()
        if (current.none { it.url.equals(channel.url, ignoreCase = true) || it.id.equals(channel.id, ignoreCase = true) }) {
            current.add(0, channel)
            saveSubscribedChannels(current)
        }
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
            parseJson<Map<String, String>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached cookies JSON: ${e.message}")
            emptyMap()
        }
    }

    fun saveCachedCookies(cookies: Map<String, String>) {
        try {
            val json = cookies.toJson()
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

    var showCombinedSubscriptions: Boolean
        get() = isRowEnabled(HomeRowType.COMBINED_SUBSCRIPTIONS)
        set(value) = setRowEnabled(HomeRowType.COMBINED_SUBSCRIPTIONS, value)

    var showIndividualChannelRows: Boolean
        get() = isRowEnabled(HomeRowType.INDIVIDUAL_CHANNELS)
        set(value) = setRowEnabled(HomeRowType.INDIVIDUAL_CHANNELS, value)

    var showTrending: Boolean
        get() = isRowEnabled(HomeRowType.TRENDING)
        set(value) = setRowEnabled(HomeRowType.TRENDING, value)

    var showMusic: Boolean
        get() = isRowEnabled(HomeRowType.MUSIC)
        set(value) = setRowEnabled(HomeRowType.MUSIC, value)

    var showGaming: Boolean
        get() = isRowEnabled(HomeRowType.GAMING)
        set(value) = setRowEnabled(HomeRowType.GAMING, value)

    var showMovies: Boolean
        get() = isRowEnabled(HomeRowType.MOVIES_AND_SHOWS)
        set(value) = setRowEnabled(HomeRowType.MOVIES_AND_SHOWS, value)

    var showPodcasts: Boolean
        get() = isRowEnabled(HomeRowType.PODCASTS)
        set(value) = setRowEnabled(HomeRowType.PODCASTS, value)

    var showLive: Boolean
        get() = isRowEnabled(HomeRowType.LIVE_STREAMS)
        set(value) = setRowEnabled(HomeRowType.LIVE_STREAMS, value)

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

    // Preferred video resolution: "Auto", "2160p (4K)", "1440p (2K)", "1080p", "720p", "480p", "360p"
    var preferredQuality: String
        get() = prefs?.getString(KEY_PREFERRED_QUALITY, "1080p") ?: "1080p"
        set(value) { prefs?.edit()?.putString(KEY_PREFERRED_QUALITY, value)?.apply() }

    // Preferred subtitle language: "English", "Spanish", "Tamil", "Hindi", "None", "Auto"
    var preferredSubtitle: String
        get() = prefs?.getString(KEY_PREFERRED_SUBTITLE, "English") ?: "English"
        set(value) { prefs?.edit()?.putString(KEY_PREFERRED_SUBTITLE, value)?.apply() }

    fun resetToDefaults() {
        prefs?.edit()?.clear()?.apply()
        seedDefaultsIfEmpty()
        notifyCacheCleared()
    }
}
