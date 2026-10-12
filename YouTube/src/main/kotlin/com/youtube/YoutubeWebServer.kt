package com.youtube

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Lightweight, zero-dependency embedded HTTP server.
 * Serves:
 * - HTML5 single page app on GET /
 * - REST API endpoints:
 *   - GET  /api/settings
 *   - POST /api/settings
 *   - POST /api/channels/add
 *   - POST /api/channels/remove
 *   - POST /api/cache/clear
 *   - POST /api/reset
 */
object YoutubeWebServer {
    private const val DEFAULT_PORT = 8088
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    var activePort: Int = DEFAULT_PORT
        private set

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    fun getServerUrl(): String = "http://${getLocalIpAddress()}:$activePort"
    fun getLocalhostUrl(): String = "http://127.0.0.1:$activePort"

    @Synchronized
    fun start(context: Context) {
        if (isRunning && serverSocket?.isClosed == false) return

        thread(isDaemon = true, name = "YoutubeWebServerThread") {
            try {
                // Try port 8088 up to 8098 to find an available port
                var socket: ServerSocket? = null
                for (p in DEFAULT_PORT..(DEFAULT_PORT + 10)) {
                    try {
                        socket = ServerSocket(p)
                        activePort = p
                        break
                    } catch (_: Exception) {}
                }
                if (socket == null) return@thread

                serverSocket = socket
                isRunning = true

                while (isRunning && !socket.isClosed) {
                    val client = socket.accept()
                    thread(isDaemon = true) {
                        handleClient(client)
                    }
                }
            } catch (_: Exception) {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val out = s.getOutputStream()

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                val pathWithQuery = parts[1]
                val path = pathWithQuery.substringBefore("?")

                // Read request headers
                var contentLength = 0
                while (true) {
                    val header = reader.readLine() ?: break
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                // Read request body if present
                var body = ""
                if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = reader.read(charArray, readTotal, contentLength - readTotal)
                        if (count == -1) break
                        readTotal += count
                    }
                    body = String(charArray, 0, readTotal)
                }

                // Handle CORS preflight
                if (method.equals("OPTIONS", ignoreCase = true)) {
                    sendResponse(out, "204 No Content", "text/plain", ByteArray(0))
                    return
                }

                when {
                    method == "GET" && (path == "/" || path == "/index.html") -> {
                        val html = YoutubeWebPage.getHtml(getLocalIpAddress(), activePort)
                        sendResponse(out, "200 OK", "text/html; charset=UTF-8", html.toByteArray(Charsets.UTF_8))
                    }
                    method == "GET" && path == "/api/settings" -> {
                        val json = getSettingsJson()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/settings" -> {
                        updateSettingsFromJson(body)
                        val json = getSettingsJson()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/channels/add" -> {
                        val input = JSONObject(body).optString("input", "").trim()
                        if (input.isNotEmpty()) {
                            resolveAndAddChannelSync(input)
                        }
                        val json = getSettingsJson()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/channels/remove" -> {
                        val url = JSONObject(body).optString("url", "").trim()
                        if (url.isNotEmpty()) {
                            YoutubePreferences.removeChannel(url)
                            YoutubeProvider.clearFeedCache()
                        }
                        val json = getSettingsJson()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", json.toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/cache/clear" -> {
                        YoutubeProvider.clearFeedCache()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", """{"success":true}""".toByteArray(Charsets.UTF_8))
                    }
                    method == "POST" && path == "/api/reset" -> {
                        YoutubePreferences.resetToDefaults()
                        YoutubeProvider.clearFeedCache()
                        val json = getSettingsJson()
                        sendResponse(out, "200 OK", "application/json; charset=UTF-8", json.toByteArray(Charsets.UTF_8))
                    }
                    else -> {
                        sendResponse(out, "404 Not Found", "text/plain", "Not Found".toByteArray(Charsets.UTF_8))
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, data: ByteArray) {
        val headers = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Accept\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    private fun getSettingsJson(): String {
        val root = JSONObject()
        root.put("preferredQuality", YoutubePreferences.preferredQuality)
        root.put("preferredSubtitle", YoutubePreferences.preferredSubtitle)
        root.put("showCombinedSubscriptions", YoutubePreferences.showCombinedSubscriptions)
        root.put("showIndividualChannelRows", YoutubePreferences.showIndividualChannelRows)
        root.put("showTrending", YoutubePreferences.showTrending)
        root.put("showMusic", YoutubePreferences.showMusic)
        root.put("showGaming", YoutubePreferences.showGaming)
        root.put("showMovies", YoutubePreferences.showMovies)
        root.put("showPodcasts", YoutubePreferences.showPodcasts)
        root.put("showLive", YoutubePreferences.showLive)
        root.put("localIp", getLocalIpAddress())
        root.put("port", activePort)
        root.put("serverUrl", getServerUrl())

        val channelsArray = JSONArray()
        for (channel in YoutubePreferences.getSubscribedChannels()) {
            val ch = JSONObject()
            ch.put("id", channel.id)
            ch.put("name", channel.name)
            ch.put("url", channel.url)
            ch.put("avatarUrl", channel.avatarUrl)
            channelsArray.put(ch)
        }
        root.put("channels", channelsArray)
        return root.toString()
    }

    private fun updateSettingsFromJson(body: String) {
        if (body.isBlank()) return
        val obj = JSONObject(body)
        if (obj.has("preferredQuality")) YoutubePreferences.preferredQuality = obj.getString("preferredQuality")
        if (obj.has("preferredSubtitle")) YoutubePreferences.preferredSubtitle = obj.getString("preferredSubtitle")
        if (obj.has("showCombinedSubscriptions")) YoutubePreferences.showCombinedSubscriptions = obj.getBoolean("showCombinedSubscriptions")
        if (obj.has("showIndividualChannelRows")) YoutubePreferences.showIndividualChannelRows = obj.getBoolean("showIndividualChannelRows")
        if (obj.has("showTrending")) YoutubePreferences.showTrending = obj.getBoolean("showTrending")
        if (obj.has("showMusic")) YoutubePreferences.showMusic = obj.getBoolean("showMusic")
        if (obj.has("showGaming")) YoutubePreferences.showGaming = obj.getBoolean("showGaming")
        if (obj.has("showMovies")) YoutubePreferences.showMovies = obj.getBoolean("showMovies")
        if (obj.has("showPodcasts")) YoutubePreferences.showPodcasts = obj.getBoolean("showPodcasts")
        if (obj.has("showLive")) YoutubePreferences.showLive = obj.getBoolean("showLive")
        YoutubeProvider.clearFeedCache()
    }

    private fun resolveAndAddChannelSync(rawInput: String) {
        val normalizedUrl = when {
            rawInput.startsWith("http") -> rawInput
            rawInput.startsWith("@") -> "https://www.youtube.com/$rawInput"
            rawInput.startsWith("UC") -> "https://www.youtube.com/channel/$rawInput"
            else -> "https://www.youtube.com/@$rawInput"
        }

        var channelName = rawInput.removePrefix("https://www.youtube.com/").removePrefix("@")
        var avatarUrl = ""

        try {
            val extractor = ServiceList.YouTube.getChannelExtractor(normalizedUrl)
            extractor.fetchPage()
            channelName = extractor.name.takeIf { it.isNotBlank() } ?: channelName
            avatarUrl = extractor.avatars.lastOrNull()?.url ?: ""
        } catch (_: Exception) {}

        val channel = SubscribedChannel(
            id = rawInput,
            name = channelName,
            url = normalizedUrl,
            avatarUrl = avatarUrl
        )

        YoutubePreferences.addChannel(channel)
        YoutubeProvider.clearFeedCache()
    }
}
