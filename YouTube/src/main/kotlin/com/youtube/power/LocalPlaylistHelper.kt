package com.youtube.power

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class PlaylistItem(
    val url: String,
    val title: String,
    val posterUrl: String = "",
    val channelName: String = "",
    val durationText: String = "",
    val addedAt: Long = System.currentTimeMillis()
)

data class LocalPlaylist(
    val id: String,
    val name: String,
    val items: List<PlaylistItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

object LocalPlaylistHelper {
    private const val TAG = "LocalPlaylistHelper"
    private const val PREFS_NAME = "youtube_local_playlists"
    const val KEY_WATCH_LATER = "playlist_watch_later_json"
    const val KEY_CUSTOM_PLAYLISTS = "custom_playlists_json"

    val mapper = jacksonObjectMapper()
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    // --- Watch Later ---

    fun getWatchLater(): List<PlaylistItem> {
        val raw = prefs?.getString(KEY_WATCH_LATER, null) ?: return emptyList()
        return try {
            mapper.readValue(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Watch Later list: ${e.message}")
            emptyList()
        }
    }

    fun addToWatchLater(item: PlaylistItem): Boolean {
        val list = getWatchLater().toMutableList()
        if (list.any { it.url.equals(item.url, ignoreCase = true) }) {
            return false
        }
        list.add(0, item)
        saveWatchLater(list)
        return true
    }

    fun removeFromWatchLater(url: String): Boolean {
        val list = getWatchLater().toMutableList()
        val removed = list.removeAll { it.url.equals(url, ignoreCase = true) }
        if (removed) {
            saveWatchLater(list)
        }
        return removed
    }

    fun isInWatchLater(url: String): Boolean {
        return getWatchLater().any { it.url.equals(url, ignoreCase = true) }
    }

    fun clearWatchLater() {
        prefs?.edit()?.remove(KEY_WATCH_LATER)?.apply()
    }

    private fun saveWatchLater(items: List<PlaylistItem>) {
        try {
            val json = mapper.writeValueAsString(items)
            prefs?.edit()?.putString(KEY_WATCH_LATER, json)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving Watch Later: ${e.message}")
        }
    }

    // --- Custom Local Playlists ---

    fun getCustomPlaylists(): List<LocalPlaylist> {
        val raw = prefs?.getString(KEY_CUSTOM_PLAYLISTS, null) ?: return emptyList()
        return try {
            mapper.readValue(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse custom playlists: ${e.message}")
            emptyList()
        }
    }

    fun getAllPlaylists(): List<LocalPlaylist> = getCustomPlaylists()

    fun getPlaylist(id: String): LocalPlaylist? =
        getCustomPlaylists().firstOrNull { it.id == id }


    fun createPlaylist(name: String): LocalPlaylist? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val current = getCustomPlaylists().toMutableList()
        val id = "pl_" + System.currentTimeMillis()
        val pl = LocalPlaylist(id = id, name = trimmed)
        current.add(pl)
        saveCustomPlaylists(current)
        return pl
    }

    fun deletePlaylist(id: String): Boolean {
        val current = getCustomPlaylists().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            saveCustomPlaylists(current)
        }
        return removed
    }

    fun addToPlaylist(playlistId: String, item: PlaylistItem): Boolean {
        val current = getCustomPlaylists().toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false

        val pl = current[idx]
        if (pl.items.any { it.url.equals(item.url, ignoreCase = true) }) {
            return false
        }
        val updatedItems = listOf(item) + pl.items
        current[idx] = pl.copy(items = updatedItems)
        saveCustomPlaylists(current)
        return true
    }

    fun removeFromPlaylist(playlistId: String, url: String): Boolean {
        val current = getCustomPlaylists().toMutableList()
        val idx = current.indexOfFirst { it.id == playlistId }
        if (idx < 0) return false

        val pl = current[idx]
        val updatedItems = pl.items.filterNot { it.url.equals(url, ignoreCase = true) }
        current[idx] = pl.copy(items = updatedItems)
        saveCustomPlaylists(current)
        return true
    }

    private fun saveCustomPlaylists(playlists: List<LocalPlaylist>) {
        try {
            val json = mapper.writeValueAsString(playlists)
            prefs?.edit()?.putString(KEY_CUSTOM_PLAYLISTS, json)?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed saving custom playlists: ${e.message}")
        }
    }
}
