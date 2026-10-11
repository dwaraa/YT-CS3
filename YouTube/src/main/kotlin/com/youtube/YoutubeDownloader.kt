package com.youtube

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class YoutubeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val data = request.dataToSend()
        val body = if (data != null && data.isNotEmpty()) {
            data.toRequestBody(null, 0, data.size)
        } else null

        val builder = okhttp3.Request.Builder()
            .url(request.url())
            .method(httpMethod, body)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")

        request.headers().forEach { (key, values) ->
            values.forEach { builder.addHeader(key, it) }
        }

        val response = client.newCall(builder.build()).execute()
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
