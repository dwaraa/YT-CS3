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

object YoutubePreferences {
    private const val TAG = "YoutubePreferences"
    private const val PREFS_NAME = "youtube_cloudstream_prefs"
    private const val KEY_SUBSCRIPTIONS = "subscribed_channels_json"
    private const val KEY_SHOW_COMBINED_SUBS = "show_combined_subscriptions"
    private const val KEY_SHOW_INDIVIDUAL_CHANNELS = "show_individual_channels"
    private const val KEY_SHOW_TRENDING = "show_trending"
    private const val KEY_SHOW_MUSIC = "show_music"
    private const val KEY_SHOW_GAMING = "show_gaming"
    private const val KEY_SHOW_MOVIES = "show_movies"
    private const val KEY_SHOW_PODCASTS = "show_podcasts"
    private const val KEY_SHOW_LIVE = "show_live"
    private const val KEY_PREFERRED_QUALITY = "preferred_video_quality"
    private const val KEY_PREFERRED_SUBTITLE = "preferred_subtitle_lang"
    private const val KEY_COOKIES_JSON = "cached_cookies_json"
    private const val KEY_COOKIES_LAST_REFRESH = "cookies_last_refresh_timestamp"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            seedDefaultsIfEmpty()
            cleanTruncatedAvatars()
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

    var showCombinedSubscriptions: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_COMBINED_SUBS, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_COMBINED_SUBS, value)?.apply() }

    var showIndividualChannelRows: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_INDIVIDUAL_CHANNELS, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_INDIVIDUAL_CHANNELS, value)?.apply() }

    var showTrending: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_TRENDING, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_TRENDING, value)?.apply() }

    var showMusic: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_MUSIC, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_MUSIC, value)?.apply() }

    var showGaming: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_GAMING, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_GAMING, value)?.apply() }

    var showMovies: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_MOVIES, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_MOVIES, value)?.apply() }

    var showPodcasts: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_PODCASTS, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_PODCASTS, value)?.apply() }

    var showLive: Boolean
        get() = prefs?.getBoolean(KEY_SHOW_LIVE, true) ?: true
        set(value) { prefs?.edit()?.putBoolean(KEY_SHOW_LIVE, value)?.apply() }

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
    }
}
