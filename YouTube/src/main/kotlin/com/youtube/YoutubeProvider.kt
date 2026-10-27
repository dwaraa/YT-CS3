package com.youtube

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.youtube.extractor.YoutubeEpisodeMapper
import com.youtube.extractor.YoutubeLinkResolver
import com.youtube.feed.YoutubeFeedHandler
import com.youtube.loader.YoutubeLoaders
import com.youtube.search.YoutubeSearchHandler
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.*
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

class YoutubeProvider : MainAPI() {
    override var mainUrl = "https://www.youtube.com"
    override var name = "YouTube"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override val supportedTypes = setOf(
        TvType.Others,
        TvType.Live,
        TvType.TvSeries
    )

    companion object {
        private const val TAG = "YoutubeProvider"

        private const val DEFAULT_ICON =
            "https://raw.githubusercontent.com/dwaraa/YT-CS3/master/YouTube/icon.png"

        fun clearFeedCache() {
            YoutubeFeedHandler.clearFeedCache()
        }

        fun formatExtractionError(t: Throwable): String {
            val message = t.message ?: ""
            return when {
                t is ReCaptchaException || message.contains("recaptcha", ignoreCase = true) || message.contains("bot", ignoreCase = true) ->
                    "Bot Verification Required (reCAPTCHA hurdle)"
                t is AgeRestrictedContentException || message.contains("age", ignoreCase = true) ->
                    "Age-Restricted Video (Requires Google sign-in)"
                t is GeographicRestrictionException || message.contains("geo", ignoreCase = true) || message.contains("country", ignoreCase = true) ->
                    "Region Blocked (Unavailable in current country/IP)"
                t is PaidContentException || message.contains("paid", ignoreCase = true) || message.contains("premium", ignoreCase = true) ->
                    "Members-Only Content (Requires paid YouTube subscription)"
                message.contains("429") || message.contains("Too Many Requests", ignoreCase = true) ->
                    "Rate Limited (HTTP 429 Too Many Requests)"
                t is ContentNotAvailableException ->
                    "Content Unavailable (Video removed or set to private)"
                t is ParsingException ->
                    "YouTube Layout Parsing Error (Page structure changed)"
                else ->
                    t.localizedMessage?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
            }
        }

        // Forwarding helpers for quality and audio
        fun extractPixelHeight(resolutionStr: String?, heightInt: Int = 0): Int =
            YoutubeLinkResolver.extractPixelHeight(resolutionStr, heightInt)

        fun parsePreferredTargetHeight(pref: String?): Int? =
            YoutubeLinkResolver.parsePreferredTargetHeight(pref)

        fun heightToQuality(height: Int): Int =
            YoutubeLinkResolver.heightToQuality(height)

        fun formatResolutionLabel(height: Int): String =
            YoutubeLinkResolver.formatResolutionLabel(height)

        fun parseAudioStream(stream: AudioStream): YoutubeLinkResolver.ParsedAudioTrack =
            YoutubeLinkResolver.parseAudioStream(stream)

        fun selectBestAudio(audioList: List<YoutubeLinkResolver.ParsedAudioTrack>, preferredLang: String?): YoutubeLinkResolver.ParsedAudioTrack? =
            YoutubeLinkResolver.selectBestAudio(audioList, preferredLang)
    }

    private val feedHandler by lazy { YoutubeFeedHandler(this, DEFAULT_ICON, ::formatExtractionError) }

    init {
        ensureDownloaderInitialized()
    }

    private fun ensureDownloaderInitialized() {
        try {
            NewPipe.getDownloader()
        } catch (_: Throwable) {
            try {
                NewPipe.init(YoutubeDownloader())
            } catch (initErr: Throwable) {
                Log.e(TAG, "Failed to initialize NewPipe downloader: ${initErr.message}", initErr)
            }
        }
    }

    override val mainPage: List<MainPageData>
        get() = feedHandler.getCustomHomeRows()

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        ensureDownloaderInitialized()
        return feedHandler.getMainPage(page, request)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        ensureDownloaderInitialized()
        return YoutubeSearchHandler.search(this, query, maxPages = 4, errorHandler = ::formatExtractionError)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? {
        ensureDownloaderInitialized()
        return YoutubeSearchHandler.quickSearch(this, query)
    }

    fun getSearchSuggestions(query: String): List<String> {
        ensureDownloaderInitialized()
        return YoutubeSearchHandler.getSearchSuggestions(query)
    }

    override suspend fun load(url: String): LoadResponse {
        ensureDownloaderInitialized()
        val cleanUrl = YoutubeLoaders.sanitizeUrl(url)
        val urlType = YoutubeLoaders.getUrlType(cleanUrl)

        return try {
            when (urlType) {
                YoutubeLoaders.UrlType.Video -> YoutubeLoaders.loadVideo(this, cleanUrl)
                YoutubeLoaders.UrlType.Channel -> YoutubeLoaders.loadChannel(this, cleanUrl)
                YoutubeLoaders.UrlType.Playlist -> YoutubeLoaders.loadPlaylist(this, cleanUrl)
                YoutubeLoaders.UrlType.Unknown -> YoutubeLoaders.loadVideo(this, cleanUrl)
            }
        } catch (e: Throwable) {
            val errorMsg = formatExtractionError(e)
            Log.e(TAG, "Failed to load '$cleanUrl': $errorMsg", e)
            throw ErrorLoadingException(errorMsg)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        ensureDownloaderInitialized()
        val watchUrl = YoutubeLoaders.sanitizeToWatchUrl(data)
        var linkFound = false

        val cookieStr = YoutubeDownloader.getCookieHeader()
        val defaultHeaders = mutableMapOf(
            "User-Agent" to YoutubeDownloader.USER_AGENT,
            "Referer" to "https://www.youtube.com/",
            "Origin" to "https://www.youtube.com",
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        ).apply {
            if (cookieStr.isNotBlank()) {
                put("Cookie", cookieStr)
            }
        }

        // 1. Direct NewPipe stream resolution
        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

            YoutubeLinkResolver.resolveLinks(
                providerName = name,
                info = info,
                preferredQuality = YoutubePreferences.preferredQuality,
                preferredSubtitleLang = YoutubePreferences.preferredSubtitle,
                defaultHeaders = defaultHeaders,
                subtitleCallback = subtitleCallback,
                callback = { link ->
                    linkFound = true
                    callback(link)
                }
            )
        } catch (e: Throwable) {
            val errorMsg = formatExtractionError(e)
            Log.e(TAG, "Failed to resolve stream links for '$watchUrl': $errorMsg", e)

            if (e is org.schabi.newpipe.extractor.exceptions.ReCaptchaException ||
                errorMsg.contains("Bot", ignoreCase = true) ||
                errorMsg.contains("Rate Limited", ignoreCase = true)
            ) {
                YoutubeDownloader.refreshConsentCookies(force = true)
            }
        }

        // 2. Secondary fallback: Cloudstream built-in extractor
        if (!linkFound) {
            try {
                loadExtractor(watchUrl, subtitleCallback) { link ->
                    linkFound = true
                    callback(link)
                }
            } catch (extractorErr: Throwable) {
                Log.d(TAG, "Secondary extractor skipped for $watchUrl: ${extractorErr.message}")
            }
        }

        return linkFound
    }
}
