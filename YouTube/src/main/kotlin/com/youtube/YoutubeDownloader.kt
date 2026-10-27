package com.youtube

import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class YoutubeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : Downloader() {

    companion object {
        private const val TAG = "YoutubeDownloader"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        private val cookies = ConcurrentHashMap<String, String>()
        private const val COOKIE_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours

        init {
            loadInitialCookies()
        }

        private fun loadInitialCookies() {
            // 1. Try loading cached cookies from persistent storage
            val cached = try {
                YoutubePreferences.getCachedCookies()
            } catch (e: Exception) {
                Log.w(TAG, "Failed loading cached cookies: ${e.message}")
                emptyMap()
            }

            if (cached.isNotEmpty()) {
                cookies.putAll(cached)
                Log.d(TAG, "Loaded ${cached.size} cached cookies from preferences")
            } else {
                // 2. Fallback seeds if no cached cookies exist yet
                cookies["SOCS"] = "CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
                cookies["CONSENT"] = "YES+cb.20230531-04-p0.en+FX+999"
                cookies["PREF"] = "f6=40000000&hl=en&f2=8000000"
            }
        }

        @Synchronized
        fun refreshConsentCookies(client: OkHttpClient? = null, force: Boolean = false) {
            val now = System.currentTimeMillis()
            val lastRefresh = YoutubePreferences.getCookiesLastRefresh()
            if (!force && (now - lastRefresh) < COOKIE_REFRESH_INTERVAL_MS && cookies.containsKey("SOCS")) {
                return
            }

            try {
                Log.d(TAG, "Refreshing YouTube consent cookies (force=$force)...")
                val http = client ?: OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val req = okhttp3.Request.Builder()
                    .url("https://www.youtube.com/?hl=en")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                val resp = http.newCall(req).execute()
                var updated = false

                resp.headers("Set-Cookie").forEach { rawCookie ->
                    val cookiePart = rawCookie.substringBefore(";")
                    val eqIdx = cookiePart.indexOf("=")
                    if (eqIdx > 0) {
                        val cookieName = cookiePart.substring(0, eqIdx).trim()
                        val cookieValue = cookiePart.substring(eqIdx + 1).trim()
                        cookies[cookieName] = cookieValue
                        updated = true
                    }
                }

                // If not provided in Set-Cookie, guarantee required consent tokens
                if (!cookies.containsKey("CONSENT")) {
                    cookies["CONSENT"] = "YES+cb.20230531-04-p0.en+FX+999"
                    updated = true
                }
                if (!cookies.containsKey("SOCS")) {
                    cookies["SOCS"] = "CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg"
                    updated = true
                }

                if (updated) {
                    YoutubePreferences.saveCachedCookies(cookies.toMap())
                    YoutubePreferences.saveCookiesLastRefresh(now)
                    Log.i(TAG, "Successfully refreshed YouTube cookies. Keys: ${cookies.keys}")
                }
                resp.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to refresh consent cookies: ${e.message}", e)
            }
        }
    }

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()

        val method = httpMethod.uppercase()
        val body = when (method) {
            "POST", "PUT", "PATCH" -> {
                val mediaType = request.headers()?.entries
                    ?.firstOrNull { it.key.equals("content-type", ignoreCase = true) }
                    ?.value?.firstOrNull()?.toMediaTypeOrNull()
                val bytes = data ?: ByteArray(0)
                bytes.toRequestBody(mediaType)
            }
            "GET", "HEAD" -> null
            else -> {
                if (data != null && data.isNotEmpty()) {
                    data.toRequestBody(null)
                } else null
            }
        }

        val builder = okhttp3.Request.Builder()
            .url(url)
            .method(method, body)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")

        // Apply request-specific headers
        request.headers()?.forEach { (key, values) ->
            // Let OkHttp handle transparent gzip decompression
            if (key.equals("Accept-Encoding", ignoreCase = true)) return@forEach

            // Merge cookies if provided by request
            if (key.equals("Cookie", ignoreCase = true)) {
                values.forEach { cookieStr ->
                    cookieStr.split(";").forEach { c ->
                        val eq = c.indexOf("=")
                        if (eq > 0) {
                            cookies[c.substring(0, eq).trim()] = c.substring(eq + 1).trim()
                        }
                    }
                }
                return@forEach
            }

            if (values.isNotEmpty()) {
                builder.header(key, values.first())
                for (i in 1 until values.size) {
                    builder.addHeader(key, values[i])
                }
            }
        }

        // Attach merged cookies for YouTube/Google domains
        if (url.contains("youtube.com") || url.contains("googlevideo.com") || url.contains("google.com")) {
            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieHeader.isNotBlank()) {
                builder.header("Cookie", cookieHeader)
            }
        }

        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed for $url: ${e.message}", e)
            throw e
        }

        // Capture Set-Cookie headers from YouTube responses to maintain dynamic session state
        var newCookiesFound = false
        response.headers("Set-Cookie").forEach { rawCookie ->
            val cookiePart = rawCookie.substringBefore(";")
            val eqIdx = cookiePart.indexOf("=")
            if (eqIdx > 0) {
                val cookieName = cookiePart.substring(0, eqIdx).trim()
                val cookieValue = cookiePart.substring(eqIdx + 1).trim()
                cookies[cookieName] = cookieValue
                newCookiesFound = true
            }
        }
        if (newCookiesFound) {
            YoutubePreferences.saveCachedCookies(cookies.toMap())
        }

        val finalUrl = response.request.url.toString()
        // Detect bot checks, rate limiting (429), or consent redirects
        if (response.code == 429 || finalUrl.contains("consent.youtube.com") || finalUrl.contains("google.com/sorry")) {
            Log.w(TAG, "Encountered YouTube hurdle (Code: ${response.code}, FinalUrl: $finalUrl). Refreshing cookies...")
            refreshConsentCookies(client, force = true)
        }

        val responseBody = response.body?.string().orEmpty()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            finalUrl
        )
    }
}
