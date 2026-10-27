package com.youtube.extractor

import android.util.Log
import com.lagradost.cloudstream3.utils.ExtractorLink

object AudioTrackHelper {
    private const val TAG = "AudioTrackHelper"

    private val audioFileClass: Class<*>? = try {
        Class.forName("com.lagradost.cloudstream3.AudioFile")
    } catch (_: Throwable) {
        null
    }

    private val audioFileConstructor = try {
        audioFileClass?.getConstructor(String::class.java, Map::class.java)
    } catch (_: Throwable) {
        null
    }

    private val setAudioTracksMethod = try {
        ExtractorLink::class.java.getMethod("setAudioTracks", List::class.java)
    } catch (_: Throwable) {
        null
    }

    fun isSupported(): Boolean = audioFileClass != null && setAudioTracksMethod != null && audioFileConstructor != null

    fun attachAudioTrack(link: ExtractorLink, audioUrl: String, headers: Map<String, String>) {
        attachAudioTracks(link, listOf(audioUrl to headers))
    }

    fun attachAudioTracks(link: ExtractorLink, tracks: List<Pair<String, Map<String, String>>>) {
        if (!isSupported() || tracks.isEmpty()) return
        try {
            val audioObjs = tracks.mapNotNull { (url, headers) ->
                audioFileConstructor?.newInstance(url, headers)
            }
            if (audioObjs.isNotEmpty()) {
                setAudioTracksMethod?.invoke(link, audioObjs)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to attach audio tracks to link: ${t.message}")
        }
    }
}
