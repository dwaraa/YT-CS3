package com.youtube.power

import android.util.Log
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class SponsorBlockSegment(
    @JsonProperty("category") val category: String = "sponsor",
    @JsonProperty("actionType") val actionType: String = "skip",
    @JsonProperty("segment") val segment: List<Double> = emptyList(),
    @JsonProperty("UUID") val uuid: String = ""
) {
    val startSeconds: Double get() = segment.getOrNull(0) ?: 0.0
    val endSeconds: Double get() = segment.getOrNull(1) ?: 0.0

    val categoryLabel: String
        get() = when (category.lowercase()) {
            "sponsor" -> "Sponsor"
            "selfpromo" -> "Self-Promotion"
            "interaction" -> "Interaction Reminder"
            "intro" -> "Intro / Animation"
            "outro" -> "End Credits / Outro"
            "preview" -> "Recap / Preview"
            "music_offtopic" -> "Non-Music Section"
            else -> category.replaceFirstChar { it.uppercase() }
        }
}

typealias SponsorSegment = SponsorBlockSegment

object SponsorBlockHelper {
    private const val TAG = "SponsorBlockHelper"
    private const val API_BASE = "https://sponsor.ajay.app/api/skipSegments"
    private val mapper = jacksonObjectMapper()

    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getSegments(videoId: String): List<SponsorBlockSegment> = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext emptyList()
        try {
            val categoriesJson = "[\"sponsor\",\"selfpromo\",\"interaction\",\"intro\",\"outro\",\"preview\",\"music_offtopic\"]"
            val url = "$API_BASE?videoID=$videoId&categories=${java.net.URLEncoder.encode(categoriesJson, "UTF-8")}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "YT-CS3-CloudStream-Plugin")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code != 404) {
                        Log.d(TAG, "SponsorBlock returned HTTP ${response.code} for $videoId")
                    }
                    return@withContext emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext emptyList()
                val segments: List<SponsorBlockSegment> = mapper.readValue(body)
                segments.sortedBy { it.startSeconds }
            }
        } catch (e: Exception) {
            Log.d(TAG, "SponsorBlock fetch skipped or failed for $videoId: ${e.message}")
            emptyList()
        }
    }

    fun formatSecondsToTimestamp(seconds: Double): String {
        val totalSecs = seconds.toInt()
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    /**
     * Formats SponsorBlock segments into an informative text block for the description.
     */
    fun formatSegmentsForDescription(segments: List<SponsorBlockSegment>): String? {
        if (segments.isEmpty()) return null
        val lines = segments.map { seg ->
            val startTs = formatSecondsToTimestamp(seg.startSeconds)
            val endTs = formatSecondsToTimestamp(seg.endSeconds)
            "• $startTs - $endTs: ${seg.categoryLabel}"
        }
        return "⚡ SponsorBlock Segments (${segments.size}):\n" + lines.joinToString("\n")
    }

    fun formatSegmentsDescription(segments: List<SponsorBlockSegment>): String? =
        formatSegmentsForDescription(segments)

    /**
     * Generates a WebVTT subtitle string that shows SponsorBlock segment indicators on screen.
     */
    fun generateWebVtt(segments: List<SponsorBlockSegment>): String {
        val sb = StringBuilder()
        sb.append("WEBVTT\n\n")

        fun toVttTimestamp(seconds: Double): String {
            val totalMillis = (seconds * 1000).toLong()
            val h = totalMillis / 3600000
            val m = (totalMillis % 3600000) / 60000
            val s = (totalMillis % 60000) / 1000
            val ms = totalMillis % 1000
            return String.format("%02d:%02d:%02d.%03d", h, m, s, ms)
        }

        for ((index, seg) in segments.withIndex()) {
            val startStr = toVttTimestamp(seg.startSeconds)
            val endStr = toVttTimestamp(seg.endSeconds)
            val label = seg.categoryLabel
            val startPretty = formatSecondsToTimestamp(seg.startSeconds)
            val endPretty = formatSecondsToTimestamp(seg.endSeconds)

            sb.append("${index + 1}\n")
            sb.append("$startStr --> $endStr\n")
            sb.append("⚡ [SponsorBlock] Skip $label ($startPretty - $endPretty)\n\n")
        }

        return sb.toString()
    }

    /**
     * Generates a data URI containing the WebVTT subtitle track for in-player markers.
     */
    fun generateWebVttDataUrl(segments: List<SponsorBlockSegment>): String {
        if (segments.isEmpty()) return ""
        val vtt = generateWebVtt(segments)
        val encoded = java.net.URLEncoder.encode(vtt, "UTF-8").replace("+", "%20")
        return "data:text/vtt;charset=utf-8,$encoded"
    }

    /**
     * Converts SponsorBlock segments into CloudStream native SkipStamps
     * for displaying the in-player skip button.
     */
    fun toSkipStamps(segments: List<SponsorBlockSegment>): List<com.lagradost.cloudstream3.utils.videoskip.SkipStamp> {
        return segments.mapNotNull { seg ->
            val startMs = (seg.startSeconds * 1000).toLong()
            val endMs = (seg.endSeconds * 1000).toLong()
            if (endMs <= startMs) return@mapNotNull null

            val skipType = when (seg.category.lowercase()) {
                "intro" -> com.lagradost.cloudstream3.utils.videoskip.SkipType.Intro
                "outro" -> com.lagradost.cloudstream3.utils.videoskip.SkipType.Ending
                "preview" -> com.lagradost.cloudstream3.utils.videoskip.SkipType.Preview
                "recap" -> com.lagradost.cloudstream3.utils.videoskip.SkipType.Recap
                else -> com.lagradost.cloudstream3.utils.videoskip.SkipType.Intro
            }

            com.lagradost.cloudstream3.utils.videoskip.SkipStamp(
                type = skipType,
                startMs = startMs,
                endMs = endMs,
                label = seg.categoryLabel
            )
        }
    }
}

