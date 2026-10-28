package com.youtube.power

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.youtube.extractor.YoutubeEpisodeMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class RYDResponse(
    @JsonProperty("id") val id: String = "",
    @JsonProperty("likes") val likes: Long = 0L,
    @JsonProperty("dislikes") val dislikes: Long = 0L,
    @JsonProperty("rating") val rating: Double = 0.0,
    @JsonProperty("viewCount") val viewCount: Long = 0L,
    @JsonProperty("deleted") val deleted: Boolean = false
) {
    val totalVotes: Long get() = likes + dislikes
    val likePercentage: Double
        get() = if (totalVotes > 0) (likes.toDouble() / totalVotes.toDouble()) * 100.0 else 0.0

    val formattedStats: String
        get() {
            val likesFormatted = YoutubeEpisodeMapper.formatCount(likes)
            val dislikesFormatted = YoutubeEpisodeMapper.formatCount(dislikes)
            val pctFormatted = if (totalVotes > 0) String.format("%.1f%%", likePercentage) else ""
            val ratingFormatted = if (rating > 0.0) String.format("%.1f★", rating) else ""

            val parts = listOfNotNull(
                "👍 $likesFormatted".takeIf { likes > 0 },
                "👎 $dislikesFormatted".takeIf { dislikes > 0 },
                "($pctFormatted liked)".takeIf { pctFormatted.isNotBlank() && totalVotes > 0 },
                ratingFormatted.takeIf { it.isNotBlank() }
            )
            return parts.joinToString(" ")
        }

    fun formattedStats(): String = formattedStats
}

typealias DislikeData = RYDResponse

object ReturnYouTubeDislikeHelper {
    private const val TAG = "RYDHelper"
    private const val API_BASE = "https://returnyoutubedislikeapi.com/votes"
    val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getDislikes(videoId: String): RYDResponse? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        try {
            val url = "$API_BASE?videoId=$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YT-CS3-CloudStream-Plugin")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "RYD API returned HTTP ${response.code} for $videoId")
                    return@withContext null
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext null
                mapper.readValue<RYDResponse>(body)
            }
        } catch (e: Exception) {
            Log.d(TAG, "RYD fetch skipped or failed for $videoId: ${e.message}")
            null
        }
    }
}
