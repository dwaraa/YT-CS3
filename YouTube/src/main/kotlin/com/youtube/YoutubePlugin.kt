package com.youtube

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import org.schabi.newpipe.extractor.NewPipe

@CloudstreamPlugin
class YoutubePlugin : Plugin() {
    override fun load(context: Context) {
        // Initialize persistent preferences for subscribed channels & home rows
        YoutubePreferences.init(context)

        // Pre-initialize NewPipe extractor with custom connection pool downloader
        try {
            NewPipe.init(YoutubeDownloader())
        } catch (_: Throwable) {}

        // Register the YouTube provider into Cloudstream
        registerMainAPI(YoutubeProvider())

        // Start embedded web server for settings (accessible locally & remotely)
        YoutubeWebServer.start(context)

        // Connect settings action to open our interactive settings dialog
        openSettings = { ctx ->
            YoutubeSettings.show(ctx)
        }
    }
}
