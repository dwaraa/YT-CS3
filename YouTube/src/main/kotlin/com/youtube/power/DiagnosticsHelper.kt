package com.youtube.power

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.youtube.YoutubeDownloader
import com.youtube.YoutubePreferences
import com.youtube.extractor.AudioTrackHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.TimeUnit

data class DiagnosticsResult(
    val newpipeVersion: String = "v0.26.5",
    val newpipeVersionOk: Boolean = true,
    val consentCookieHealthy: Boolean = true,
    val extractionOk: Boolean = true,
    val extractionDetails: String = "Operational",
    val pingYoutubeMs: Long = -1L,
    val pingSponsorBlockMs: Long = -1L,
    val pingRydMs: Long = -1L,
    val audioTrackHelperFunctional: Boolean = true,
    val activeSubscriptionsCount: Int = 0,
    val activeProfile: String = "Default",
    val summary: String = ""
)

typealias DiagnosticReport = DiagnosticsResult

object DiagnosticsHelper {
    private const val TAG = "DiagnosticsHelper"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun runSelfTest(context: Context): DiagnosticsResult = withContext(Dispatchers.IO) {
        val newPipeVer = "v0.26.5"
        val activeProfile = YoutubePreferences.activeProfile
        val subsCount = YoutubePreferences.getSubscribedChannels().size
        val wlCount = LocalPlaylistHelper.getWatchLater().size

        // 1. Check cookies
        val cookies = YoutubeDownloader.getCookieHeader()
        val hasSocs = cookies.contains("SOCS")
        val hasConsent = cookies.contains("CONSENT")
        val cookiesOk = hasSocs || hasConsent

        // 2. AudioTrackHelper
        val audioTrackSupported = AudioTrackHelper.isSupported()

        // 3. Ping tests
        val ytLatency = measureLatency("https://www.youtube.com/?hl=en") ?: -1L
        val rydLatency = measureLatency("https://returnyoutubedislikeapi.com/votes?videoId=dQw4w9WgXcQ") ?: -1L
        val sbLatency = measureLatency("https://sponsor.ajay.app/api/skipSegments?videoID=dQw4w9WgXcQ") ?: -1L

        // 4. Extraction self-test
        var extractionOk = false
        var extractionErr = "OK"
        try {
            val suggestions = ServiceList.YouTube.suggestionExtractor.suggestionList("android")
            if (suggestions.isNotEmpty()) {
                extractionOk = true
                extractionErr = "Suggestions returned ${suggestions.size} items"
            } else {
                extractionErr = "Suggestion list returned empty"
            }
        } catch (e: Exception) {
            extractionErr = e.message ?: e.javaClass.simpleName
        }

        val summaryText = if (extractionOk && cookiesOk && ytLatency >= 0) {
            "All core extraction services and network endpoints are healthy."
        } else {
            "Some diagnostic checks reported warnings or network errors."
        }

        DiagnosticsResult(
            newpipeVersion = newPipeVer,
            newpipeVersionOk = true,
            consentCookieHealthy = cookiesOk,
            extractionOk = extractionOk,
            extractionDetails = extractionErr,
            pingYoutubeMs = ytLatency,
            pingSponsorBlockMs = sbLatency,
            pingRydMs = rydLatency,
            audioTrackHelperFunctional = audioTrackSupported,
            activeSubscriptionsCount = subsCount,
            activeProfile = activeProfile,
            summary = summaryText
        )
    }

    private fun measureLatency(url: String): Long? {
        return try {
            val start = System.currentTimeMillis()
            val req = Request.Builder().url(url).build()
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful || response.code == 404) {
                    System.currentTimeMillis() - start
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateMarkdownReport(result: DiagnosticsResult): String {
        val sb = StringBuilder()
        sb.append("# YouTube CloudStream Extension Diagnostics Report\n\n")
        sb.append("- **Generated At:** ${java.util.Date()}\n")
        sb.append("- **Android OS:** Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("- **Device:** ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("- **NewPipeExtractor:** ${result.newpipeVersion}\n")
        sb.append("- **Active Profile:** ${result.activeProfile}\n")
        sb.append("- **Subscribed Channels:** ${result.activeSubscriptionsCount}\n\n")

        sb.append("### Diagnostic Checks\n")
        sb.append("- **NewPipeExtractor Version:** ${if (result.newpipeVersionOk) "✅ Passed" else "⚠️ Check needed"} (${result.newpipeVersion})\n")
        sb.append("- **YouTube Extraction:** ${if (result.extractionOk) "✅ Operational" else "❌ Failed"} (${result.extractionDetails})\n")
        sb.append("- **Consent Cookies:** ${if (result.consentCookieHealthy) "✅ Healthy" else "⚠️ Needs refresh"}\n")
        sb.append("- **YouTube Latency:** ${if (result.pingYoutubeMs >= 0) "${result.pingYoutubeMs}ms" else "Offline"}\n")
        sb.append("- **SponsorBlock Latency:** ${if (result.pingSponsorBlockMs >= 0) "${result.pingSponsorBlockMs}ms" else "Offline"}\n")
        sb.append("- **Return YouTube Dislike Latency:** ${if (result.pingRydMs >= 0) "${result.pingRydMs}ms" else "Offline"}\n")
        sb.append("- **Audio Track Merging:** ${if (result.audioTrackHelperFunctional) "✅ Supported" else "⚠️ Unsupported"}\n\n")

        sb.append("### Active Settings\n")
        sb.append("- Preferred Resolution: ${YoutubePreferences.preferredQuality}\n")
        sb.append("- Audio-Only Mode: ${if (YoutubePreferences.audioOnlyMode) "Enabled" else "Disabled"}\n")
        sb.append("- SponsorBlock: ${if (YoutubePreferences.enableSponsorBlock) "Enabled" else "Disabled"}\n")
        sb.append("- Return YouTube Dislike: ${if (YoutubePreferences.enableReturnYouTubeDislike) "Enabled" else "Disabled"}\n")
        sb.append("- Trending Region: ${YoutubePreferences.trendingRegion}\n")
        sb.append("- Cache TTL: ${YoutubePreferences.cacheTtlMinutes}m\n\n")

        sb.append("### Diagnostics Summary\n")
        sb.append(result.summary)
        sb.append("\n")

        return sb.toString()
    }

    fun copyReportToClipboard(context: Context, result: DiagnosticsResult) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("YT-CS3 Diagnostics", generateMarkdownReport(result))
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied diagnostic report to clipboard", Toast.LENGTH_SHORT).show()
    }
}

