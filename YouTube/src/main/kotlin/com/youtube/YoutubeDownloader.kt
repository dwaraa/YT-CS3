package com.youtube

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
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        // Pre-seeded consent & preference cookies to bypass Google's consent wall and bot hurdles
        private val cookies = ConcurrentHashMap<String, String>().apply {
            put("SOCS", "CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg")
            put("CONSENT", "YES+cb.20230531-04-p0.en+FX+999")
            put("PREF", "f6=40000000&hl=en&f2=8000000")
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

        val response = client.newCall(builder.build()).execute()

        // Capture Set-Cookie headers from YouTube responses to maintain session state
        response.headers("Set-Cookie").forEach { rawCookie ->
            val cookiePart = rawCookie.substringBefore(";")
            val eqIdx = cookiePart.indexOf("=")
            if (eqIdx > 0) {
                val cookieName = cookiePart.substring(0, eqIdx).trim()
                val cookieValue = cookiePart.substring(eqIdx + 1).trim()
                cookies[cookieName] = cookieValue
            }
        }

        val responseBody = response.body?.string().orEmpty()

        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBody,
            response.request.url.toString()
        )
    }
}
