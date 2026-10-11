package com.youtube

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

object YoutubeSettings {

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun createRoundedDrawable(bgColor: Int, cornerRadiusDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = cornerRadiusDp
        }
    }

    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadii = floatArrayOf(40f, 40f, 40f, 40f, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 24))
        }

        // Top drag pill handle
        val pill = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#444444"), 8f)
            layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(context, 14)
            }
        }
        root.addView(pill)

        // Title Header
        val title = TextView(context).apply {
            text = "YouTube Extension Settings"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 16) }
        }
        root.addView(title)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 480)
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Section 1: Playback & Streaming Preferences
        content.addView(createSectionHeader(context, "PLAYBACK & STREAMS"))

        // Preferred Quality
        val qualityOptions = arrayOf("Auto (Best)", "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p")
        val qualityValues = arrayOf("Auto", "2160p", "1440p", "1080p", "720p", "480p", "360p")
        val currentQualityIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }

        val qualityRow = createSettingSelectorRow(
            context,
            title = "Preferred Video Quality",
            currentValue = qualityOptions[currentQualityIndex]
        ) { textBadge ->
            val popup = AlertDialog.Builder(context)
                .setTitle("Select Preferred Video Quality")
                .setItems(qualityOptions) { _, idx ->
                    YoutubePreferences.preferredQuality = qualityValues[idx]
                    textBadge.text = qualityOptions[idx]
                    Toast.makeText(context, "Quality set to ${qualityOptions[idx]}", Toast.LENGTH_SHORT).show()
                }
                .create()
            popup.show()
        }
        content.addView(qualityRow)

        // Preferred Subtitles
        val subtitleOptions = arrayOf("None", "English", "Spanish", "Tamil", "Hindi", "Arabic", "French", "German", "Japanese", "Auto")
        val currentSubIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }

        val subRow = createSettingSelectorRow(
            context,
            title = "Preferred Subtitle Language",
            currentValue = subtitleOptions[currentSubIndex]
        ) { textBadge ->
            val popup = AlertDialog.Builder(context)
                .setTitle("Select Preferred Subtitle Language")
                .setItems(subtitleOptions) { _, idx ->
                    YoutubePreferences.preferredSubtitle = subtitleOptions[idx]
                    textBadge.text = subtitleOptions[idx]
                    Toast.makeText(context, "Subtitles set to ${subtitleOptions[idx]}", Toast.LENGTH_SHORT).show()
                }
                .create()
            popup.show()
        }
        content.addView(subRow)

        // Section 2: Subscribed Channels
        content.addView(createSectionHeader(context, "SUBSCRIBED CHANNELS"))

        val addChannelBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 10) }
        }

        val input = EditText(context).apply {
            hint = "@handle, channel URL, or ID"
            setHintTextColor(Color.parseColor("#777777"))
            setTextColor(Color.WHITE)
            textSize = 14f
            background = createRoundedDrawable(Color.parseColor("#222222"), 16f)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
            setSingleLine(true)
        }
        addChannelBox.addView(input)

        val channelListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val addBtn = Button(context).apply {
            text = "+ Add"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(Color.parseColor("#E50914"), 16f)
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            setOnClickListener {
                val raw = input.text.toString().trim()
                if (raw.isNotBlank()) {
                    input.text.clear()
                    resolveAndAddChannel(context, raw) {
                        refreshChannelList(context, channelListContainer)
                    }
                }
            }
        }
        addChannelBox.addView(addBtn)
        content.addView(addChannelBox)

        refreshChannelList(context, channelListContainer)
        content.addView(channelListContainer)

        // Section 3: Home Screen Rows Customization
        content.addView(createSectionHeader(context, "HOME SCREEN ROWS"))

        content.addView(createSwitchRow(context, "Subscribed: Combined Feed", YoutubePreferences.showCombinedSubscriptions) {
            YoutubePreferences.showCombinedSubscriptions = it
        })
        content.addView(createSwitchRow(context, "Subscribed: Separate Channel Rows", YoutubePreferences.showIndividualChannelRows) {
            YoutubePreferences.showIndividualChannelRows = it
        })
        content.addView(createSwitchRow(context, "Trending", YoutubePreferences.showTrending) {
            YoutubePreferences.showTrending = it
        })
        content.addView(createSwitchRow(context, "Movies & Shows", YoutubePreferences.showMovies) {
            YoutubePreferences.showMovies = it
        })
        content.addView(createSwitchRow(context, "Music", YoutubePreferences.showMusic) {
            YoutubePreferences.showMusic = it
        })
        content.addView(createSwitchRow(context, "Gaming", YoutubePreferences.showGaming) {
            YoutubePreferences.showGaming = it
        })
        content.addView(createSwitchRow(context, "Podcasts", YoutubePreferences.showPodcasts) {
            YoutubePreferences.showPodcasts = it
        })
        content.addView(createSwitchRow(context, "Live Streams", YoutubePreferences.showLive) {
            YoutubePreferences.showLive = it
        })

        // Section 4: Maintenance Actions
        content.addView(createSectionHeader(context, "ACTIONS"))

        val clearCacheBtn = createActionButton(context, "⚡ Clear In-Memory Cache", Color.parseColor("#262626")) {
            YoutubeProvider.clearFeedCache()
            Toast.makeText(context, "In-memory feed cache cleared! Reloading feeds.", Toast.LENGTH_SHORT).show()
        }
        content.addView(clearCacheBtn)

        val resetBtn = createActionButton(context, "🔄 Reset Settings to Default", Color.parseColor("#262626")) {
            YoutubePreferences.resetToDefaults()
            YoutubeProvider.clearFeedCache()
            refreshChannelList(context, channelListContainer)
            Toast.makeText(context, "Reset to defaults! Reopen settings to see changes.", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        content.addView(resetBtn)

        scrollView.addView(content)
        root.addView(scrollView)

        dialog.setContentView(root)

        // Window configuration: Dock to bottom, full width, slide-up animation, dim background
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun createSectionHeader(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#E50914"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(context, 14), 0, dp(context, 6))
        }
    }

    private fun createSettingSelectorRow(
        context: Context,
        title: String,
        currentValue: String,
        onClick: (badge: TextView) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedDrawable(Color.parseColor("#1C1C1C"), 16f)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 8) }
        }

        val label = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val badge = TextView(context).apply {
            text = currentValue
            setTextColor(Color.parseColor("#E50914"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(Color.parseColor("#2A2A2A"), 10f)
            setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
        }
        row.addView(badge)

        row.setOnClickListener {
            onClick(badge)
        }

        return row
    }

    private fun createSwitchRow(
        context: Context,
        title: String,
        initialChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedDrawable(Color.parseColor("#1C1C1C"), 16f)
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 6) }
        }

        val label = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(label)

        val toggle = Switch(context).apply {
            isChecked = initialChecked
            setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(isChecked)
            }
        }
        row.addView(toggle)

        return row
    }

    private fun createActionButton(context: Context, title: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 13f
            background = createRoundedDrawable(bgColor, 16f)
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 8) }
            setOnClickListener { onClick() }
        }
    }

    private fun refreshChannelList(context: Context, container: LinearLayout) {
        container.removeAllViews()
        val channels = YoutubePreferences.getSubscribedChannels()

        if (channels.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No subscribed channels yet. Type a handle above to add one."
                setTextColor(Color.parseColor("#777777"))
                textSize = 12f
                setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 10))
            }
            container.addView(empty)
            return
        }

        for (channel in channels) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createRoundedDrawable(Color.parseColor("#1F1F1F"), 12f)
                setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 6) }
            }

            val channelInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val name = TextView(context).apply {
                text = channel.name
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
            }
            val handle = TextView(context).apply {
                text = channel.url.substringAfterLast("/")
                setTextColor(Color.parseColor("#999999"))
                textSize = 11f
            }
            channelInfo.addView(name)
            channelInfo.addView(handle)
            item.addView(channelInfo)

            val removeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(Color.parseColor("#FF4444"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                setOnClickListener {
                    YoutubePreferences.removeChannel(channel.url)
                    refreshChannelList(context, container)
                    Toast.makeText(context, "Removed '${channel.name}'", Toast.LENGTH_SHORT).show()
                }
            }
            item.addView(removeBtn)

            container.addView(item)
        }
    }

    private fun resolveAndAddChannel(context: Context, rawInput: String, onDone: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        Toast.makeText(context, "Resolving channel...", Toast.LENGTH_SHORT).show()

        val normalizedUrl = when {
            rawInput.startsWith("http") -> rawInput
            rawInput.startsWith("@") -> "https://www.youtube.com/$rawInput"
            rawInput.startsWith("UC") -> "https://www.youtube.com/channel/$rawInput"
            else -> "https://www.youtube.com/@$rawInput"
        }

        thread {
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

            handler.post {
                Toast.makeText(context, "Added '$channelName' to subscriptions!", Toast.LENGTH_SHORT).show()
                onDone()
            }
        }
    }
}
