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
        if (!isSupported()) return
        try {
            val audioObj = audioFileConstructor?.newInstance(audioUrl, headers) ?: return
            val list = listOf(audioObj)
            setAudioTracksMethod?.invoke(link, list)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to attach audio track to link: ${t.message}")
        }
    }
}
