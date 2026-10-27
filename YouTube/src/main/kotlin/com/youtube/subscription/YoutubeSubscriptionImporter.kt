package com.youtube.subscription

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.youtube.SubscribedChannel
import java.util.regex.Pattern

data class ImportResult(
    val format: String,
    val totalFound: Int,
    val importedChannels: List<SubscribedChannel>,
    val errorMessage: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class NewPipeExportWrapper(
    @JsonProperty("app_version") val appVersion: String? = null,
    @JsonProperty("app_version_int") val appVersionInt: Int? = null,
    @JsonProperty("subscriptions") val subscriptions: List<NewPipeSubItem>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class NewPipeSubItem(
    @JsonProperty("service_id") val serviceId: Int? = 0,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("name") val name: String? = null
)

object YoutubeSubscriptionImporter {

    private val mapper = jacksonObjectMapper()

    private val OPML_OUTLINE_PATTERN = Pattern.compile(
        """<outline\b([^>]+)/?>""",
        Pattern.CASE_INSENSITIVE
    )

    private val ATTR_TITLE = Pattern.compile("""\btitle=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val ATTR_TEXT = Pattern.compile("""\btext=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val ATTR_HTML_URL = Pattern.compile("""\bhtmlUrl=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val ATTR_XML_URL = Pattern.compile("""\bxmlUrl=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
    private val CHANNEL_ID_FROM_RSS = Pattern.compile("""channel_id=([a-zA-Z0-9_-]+)""")

    /**
     * Automatically detects format (NewPipe JSON, Google Takeout CSV, or OPML XML) and extracts channels.
     */
    fun detectAndImport(rawContent: String): ImportResult {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return ImportResult("Unknown", 0, emptyList(), "Input is empty")
        }

        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                parseJson(trimmed)
            }
            trimmed.contains("<opml", ignoreCase = true) || trimmed.contains("<outline", ignoreCase = true) -> {
                parseOpml(trimmed)
            }
            trimmed.contains("Channel Id", ignoreCase = true) || trimmed.contains("Channel Url", ignoreCase = true) || trimmed.contains("youtube.com/channel/") -> {
                parseTakeoutCsv(trimmed)
            }
            else -> {
                // Fallback attempt: check if JSON anyway, else CSV
                try {
                    parseJson(trimmed)
                } catch (_: Exception) {
                    parseTakeoutCsv(trimmed)
                }
            }
        }
    }

    fun parseJson(json: String): ImportResult {
        return try {
            val trimmed = json.trim()
            val items: List<NewPipeSubItem> = if (trimmed.startsWith("[")) {
                mapper.readValue(trimmed)
            } else {
                val wrapper: NewPipeExportWrapper = mapper.readValue(trimmed)
                wrapper.subscriptions ?: emptyList()
            }

            val channels = items.mapNotNull { item ->
                val rawUrl = item.url?.trim() ?: return@mapNotNull null
                val normalizedUrl = normalizeChannelUrl(rawUrl)
                val name = item.name?.trim().takeIf { !it.isNullOrBlank() } ?: deriveNameFromUrl(normalizedUrl)
                SubscribedChannel(
                    id = deriveIdFromUrl(normalizedUrl),
                    name = name,
                    url = normalizedUrl,
                    avatarUrl = ""
                )
            }.distinctBy { it.url }

            ImportResult("NewPipe JSON", channels.size, channels)
        } catch (e: Exception) {
            ImportResult("NewPipe JSON", 0, emptyList(), "Invalid NewPipe JSON: ${e.message}")
        }
    }

    fun parseTakeoutCsv(csv: String): ImportResult {
        return try {
            val lines = csv.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()

            if (lines.isEmpty()) {
                return ImportResult("Google Takeout CSV", 0, emptyList(), "CSV is empty")
            }

            val channels = mutableListOf<SubscribedChannel>()
            var isFirstLine = true

            for (line in lines) {
                if (isFirstLine && (line.contains("Channel Id", ignoreCase = true) || line.contains("Channel Url", ignoreCase = true))) {
                    isFirstLine = false
                    continue
                }
                isFirstLine = false

                val columns = parseCsvLine(line)
                if (columns.isEmpty()) continue

                // Format 1: Channel Id, Channel Url, Channel Title
                // Format 2: Channel Url, Channel Title
                val url = columns.firstOrNull { it.contains("youtube.com/") || it.contains("youtu.be/") }
                    ?: columns.firstOrNull { it.startsWith("UC") }?.let { "https://www.youtube.com/channel/$it" }
                    ?: continue

                val normalizedUrl = normalizeChannelUrl(url)
                val title = columns.lastOrNull { it != url && !it.startsWith("UC") && it.isNotBlank() }
                    ?: deriveNameFromUrl(normalizedUrl)

                channels.add(
                    SubscribedChannel(
                        id = deriveIdFromUrl(normalizedUrl),
                        name = title,
                        url = normalizedUrl,
                        avatarUrl = ""
                    )
                )
            }

            val distinctChannels = channels.distinctBy { it.url }
            ImportResult("Google Takeout CSV", distinctChannels.size, distinctChannels)
        } catch (e: Exception) {
            ImportResult("Google Takeout CSV", 0, emptyList(), "Invalid CSV: ${e.message}")
        }
    }

    fun parseOpml(opmlContent: String): ImportResult {
        return try {
            val matcher = OPML_OUTLINE_PATTERN.matcher(opmlContent)
            val channels = mutableListOf<SubscribedChannel>()

            while (matcher.find()) {
                val outlineAttrs = matcher.group(1) ?: continue

                var htmlUrl: String? = null
                val htmlMatch = ATTR_HTML_URL.matcher(outlineAttrs)
                if (htmlMatch.find()) {
                    htmlUrl = htmlMatch.group(1)
                }

                var xmlUrl: String? = null
                val xmlMatch = ATTR_XML_URL.matcher(outlineAttrs)
                if (xmlMatch.find()) {
                    xmlUrl = xmlMatch.group(1)
                }

                val titleMatch = ATTR_TITLE.matcher(outlineAttrs)
                val textMatch = ATTR_TEXT.matcher(outlineAttrs)
                val title = when {
                    titleMatch.find() -> titleMatch.group(1)
                    textMatch.find() -> textMatch.group(1)
                    else -> null
                }

                val resolvedUrl = when {
                    !htmlUrl.isNullOrBlank() -> normalizeChannelUrl(htmlUrl)
                    !xmlUrl.isNullOrBlank() -> {
                        val idMatch = CHANNEL_ID_FROM_RSS.matcher(xmlUrl)
                        if (idMatch.find()) {
                            "https://www.youtube.com/channel/${idMatch.group(1)}"
                        } else null
                    }
                    else -> null
                } ?: continue

                val finalTitle = title?.takeIf { it.isNotBlank() } ?: deriveNameFromUrl(resolvedUrl)

                channels.add(
                    SubscribedChannel(
                        id = deriveIdFromUrl(resolvedUrl),
                        name = finalTitle,
                        url = resolvedUrl,
                        avatarUrl = ""
                    )
                )
            }

            val distinctChannels = channels.distinctBy { it.url }
            ImportResult("OPML XML", distinctChannels.size, distinctChannels)
        } catch (e: Exception) {
            ImportResult("OPML XML", 0, emptyList(), "Invalid OPML: ${e.message}")
        }
    }

    fun exportToNewPipeJson(channels: List<SubscribedChannel>): String {
        val items = channels.map { ch ->
            mapOf(
                "service_id" to 0,
                "url" to ch.url,
                "name" to ch.name
            )
        }
        val exportMap = mapOf(
            "app_version" to "0.27.0",
            "app_version_int" to 1000,
            "subscriptions" to items
        )
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportMap)
    }

    fun exportToTakeoutCsv(channels: List<SubscribedChannel>): String {
        val sb = StringBuilder()
        sb.append("Channel Id,Channel Url,Channel Title\n")
        for (channel in channels) {
            val id = deriveIdFromUrl(channel.url)
            val escapedTitle = if (channel.name.contains(",") || channel.name.contains("\"")) {
                "\"${channel.name.replace("\"", "\"\"")}\""
            } else {
                channel.name
            }
            sb.append("$id,${channel.url},$escapedTitle\n")
        }
        return sb.toString()
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false

        for (c in line) {
            when {
                c == '\"' -> insideQuotes = !insideQuotes
                c == ',' && !insideQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens.map { it.trim('"').trim() }
    }

    private fun normalizeChannelUrl(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("http://")) {
            clean = "https://" + clean.substring(7)
        } else if (!clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean
    }

    private fun deriveNameFromUrl(url: String): String {
        val lastPart = url.substringAfterLast("/")
        return if (lastPart.startsWith("@")) lastPart.substring(1) else lastPart
    }

    private fun deriveIdFromUrl(url: String): String {
        val lastPart = url.substringAfterLast("/")
        return if (lastPart.isNotBlank()) lastPart else url
    }
}
