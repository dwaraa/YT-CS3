package com.youtube.power

import android.util.Log
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.videoskip.SkipAPI
import com.lagradost.cloudstream3.utils.videoskip.SkipStamp
import com.youtube.YoutubePreferences
import com.youtube.loader.YoutubeLoaders

/**
 * Native CloudStream SkipAPI provider for SponsorBlock.
 *
 * Integrates directly with CloudStream 3's built-in player skip button system
 * (GeneratorPlayer.skipChapterButton and VideoSkipStamp).
 *
 * Whenever a video is played, CloudStream's PlayerGeneratorViewModel queries
 * all registered SkipAPIs for skip intervals. This class fetches SponsorBlock
 * segments from the official API and converts them into native SkipStamps.
 *
 * When playback reaches a sponsor, intro, outro, recap, or self-promotion segment,
 * CloudStream automatically displays the on-screen "Skip <Category>" button and
 * seeks to the end of the segment when tapped.
 */
class SponsorBlockSkipAPI : SkipAPI() {
    override val name: String = "SponsorBlock"

    override val supportedTypes: Set<TvType> = setOf(
        TvType.Others,
        TvType.TvSeries,
        TvType.Movie,
        TvType.Live,
        TvType.Anime,
        TvType.Cartoon,
        TvType.Documentary,
        TvType.AsianDrama
    )

    override suspend fun stamps(
        data: LoadResponse,
        episode: ResultEpisode,
        episodeDurationMs: Long
    ): List<SkipStamp> {
        if (!YoutubePreferences.enableSponsorBlock) return emptyList()

        val rawUrl = episode.data.takeIf { !it.isNullOrBlank() }
            ?: data.url.takeIf { !it.isNullOrBlank() }
            ?: return emptyList()

        val videoId = YoutubeLoaders.extractVideoId(rawUrl) ?: return emptyList()

        return try {
            val segments = SponsorBlockHelper.getSegments(videoId)
            if (segments.isEmpty()) return emptyList()
            SponsorBlockHelper.toSkipStamps(segments)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed resolving SponsorBlock skip stamps for $videoId: ${t.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "SponsorBlockSkipAPI"
        private var isRegistered = false

        /**
         * Dynamically registers SponsorBlock into CloudStream's static SkipAPI list.
         */
        @Synchronized
        fun register() {
            if (isRegistered) return
            try {
                val field = SkipAPI::class.java.getDeclaredField("skipApis")
                field.isAccessible = true
                val current = (field.get(null) as? List<*>)?.filterIsInstance<SkipAPI>() ?: emptyList()
                if (current.none { it.name == "SponsorBlock" }) {
                    val newList = current.toMutableList()
                    newList.add(SponsorBlockSkipAPI())
                    field.set(null, newList)
                    isRegistered = true
                    Log.d(TAG, "SponsorBlockSkipAPI successfully registered into CloudStream SkipAPI list (${newList.size} active)")
                } else {
                    isRegistered = true
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Could not register SponsorBlockSkipAPI into SkipAPI.skipApis: ${t.message}")
            }
        }
    }
}
