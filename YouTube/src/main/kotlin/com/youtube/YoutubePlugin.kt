package com.youtube

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YoutubePlugin : Plugin() {
    override fun load(context: Context) {
        // Initialize persistent preferences for subscribed channels & home rows
        YoutubePreferences.init(context)

        // Register the YouTube provider into Cloudstream
        registerMainAPI(YoutubeProvider())

        // Connect settings action to open our interactive settings dialog
        openSettings = { ctx ->
            YoutubeSettings.show(ctx)
        }
    }
}
