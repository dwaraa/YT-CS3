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
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/YouTube_full-color_icon_%282017%29.svg/512px-YouTube_full-color_icon_%282017%29.svg.png"

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

        val defaultHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            "Referer" to "https://www.youtube.com/",
            "Origin" to "https://www.youtube.com"
        )

        return try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

            YoutubeLinkResolver.resolveLinks(
                providerName = name,
                info = info,
                preferredQuality = YoutubePreferences.preferredQuality,
                preferredSubtitleLang = YoutubePreferences.preferredSubtitle,
                defaultHeaders = defaultHeaders,
                subtitleCallback = subtitleCallback,
                callback = callback
            )

            // Also attempt external extractors if any match
            try {
                loadExtractor(watchUrl, subtitleCallback, callback)
            } catch (extractorErr: Throwable) {
                Log.d(TAG, "Secondary extractor skipped for $watchUrl: ${extractorErr.message}")
            }

            true
        } catch (e: Throwable) {
            val errorMsg = formatExtractionError(e)
            Log.e(TAG, "Failed to resolve stream links for '$watchUrl': $errorMsg", e)
            throw ErrorLoadingException(errorMsg)
        }
    }
}
