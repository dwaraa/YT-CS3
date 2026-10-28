package com.youtube

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.schabi.newpipe.extractor.NewPipe

@CloudstreamPlugin
class YoutubePlugin : Plugin() {
    companion object {
        private const val TAG = "YoutubePlugin"
    }

    override fun load(context: Context) {
        // Initialize persistent preferences for subscribed channels, home rows & cookies
        YoutubePreferences.init(context)

        // Pre-initialize NewPipe extractor with custom connection pool downloader
        try {
            NewPipe.init(YoutubeDownloader())
            Log.d(TAG, "NewPipe extractor successfully initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize NewPipe extractor: ${e.message}", e)
        }

        // Register SponsorBlock with CloudStream's native player skip button system
        try {
            com.youtube.power.SponsorBlockSkipAPI.register()
        } catch (e: Throwable) {
            Log.w(TAG, "SponsorBlock registration skipped: ${e.message}")
        }

        // Register the YouTube provider into Cloudstream
        registerMainAPI(YoutubeProvider())

        // Connect settings action to open our interactive settings dialog
        openSettings = { ctx ->
            YoutubeSettings.show(ctx)
        }
    }
}
