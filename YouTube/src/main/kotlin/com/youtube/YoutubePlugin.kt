package com.youtube

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YoutubePlugin : Plugin() {
    override fun load(context: Context) {
        // Register the YouTube provider
        registerMainAPI(YoutubeProvider())
    }
}
