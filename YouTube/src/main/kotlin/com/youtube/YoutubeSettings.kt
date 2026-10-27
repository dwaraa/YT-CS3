package com.youtube

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.youtube.ui.SettingsComponents
import com.youtube.ui.SettingsTheme
import com.youtube.ui.SubscriptionDialogs

object YoutubeSettings {

    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(SettingsTheme.COLOR_SHEET_BG)
                cornerRadii = floatArrayOf(
                    SettingsTheme.dp(context, 28).toFloat(), SettingsTheme.dp(context, 28).toFloat(),
                    SettingsTheme.dp(context, 28).toFloat(), SettingsTheme.dp(context, 28).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 10), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 20))
        }

        // Top drag handle pill
        val pill = View(context).apply {
            background = SettingsTheme.createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 38), SettingsTheme.dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = SettingsTheme.dp(context, 4)
                bottomMargin = SettingsTheme.dp(context, 14)
            }
        }
        root.addView(pill)

        // Top Header Bar
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = SettingsTheme.dp(context, 14) }
        }

        val logoBox = FrameLayout(context).apply {
            background = SettingsTheme.createRoundedDrawable(SettingsTheme.COLOR_YOUTUBE_RED, 10f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 36), SettingsTheme.dp(context, 36)).apply {
                marginEnd = SettingsTheme.dp(context, 12)
            }
        }
        val logoGlyph = TextView(context).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        logoBox.addView(logoGlyph)
        headerBar.addView(logoBox)

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = "YouTube Settings"
            setTextColor(SettingsTheme.COLOR_TEXT_PRIMARY)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            text = "CloudStream Extension • Preferences"
            setTextColor(SettingsTheme.COLOR_TEXT_SECONDARY)
            textSize = 11.5f
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        headerBar.addView(titleCol)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(SettingsTheme.COLOR_TEXT_PRIMARY)
            textSize = 15f
            gravity = Gravity.CENTER
            background = SettingsTheme.createRippleDrawable(context, Color.parseColor("#20222E"), SettingsTheme.COLOR_RIPPLE, 16f, SettingsTheme.COLOR_CARD_BORDER, 1f)
            layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 32), SettingsTheme.dp(context, 32))
            setOnClickListener { dialog.dismiss() }
        }
        headerBar.addView(closeBtn)
        root.addView(headerBar)

        // Status banner
        val statusBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = SettingsTheme.createRoundedDrawable(Color.parseColor("#141620"), 10f, SettingsTheme.COLOR_CARD_BORDER, 1f, context)
            setPadding(SettingsTheme.dp(context, 12), SettingsTheme.dp(context, 7), SettingsTheme.dp(context, 12), SettingsTheme.dp(context, 7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = SettingsTheme.dp(context, 14) }
        }
        val statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
            layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 7), SettingsTheme.dp(context, 7)).apply {
                marginEnd = SettingsTheme.dp(context, 8)
            }
        }
        val statusText = TextView(context).apply {
            val subsCount = YoutubePreferences.getSubscribedChannels().size
            text = "CloudStream Provider Active • $subsCount Channels Subscribed"
            setTextColor(SettingsTheme.COLOR_TEXT_SECONDARY)
            textSize = 11.5f
        }
        statusBanner.addView(statusDot)
        statusBanner.addView(statusText)
        root.addView(statusBanner)

        val displayHeight = context.resources.displayMetrics.heightPixels
        val scrollMaxHeight = (displayHeight * 0.76).toInt()

        val scrollView = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scrollMaxHeight
            )
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // ==========================================
        // SECTION 1: PLAYBACK & STREAMING
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "PLAYBACK & STREAMING", "🎬"))
        val playbackCard = SettingsComponents.createCardContainer(context)

        val qualityOptions = arrayOf("Auto (Best)", "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p")
        val qualityValues = arrayOf("Auto", "2160p", "1440p", "1080p", "720p", "480p", "360p")
        val currentQualityIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }

        val qualityRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "📺",
            title = "Preferred Video Quality",
            description = "Default stream resolution for playback",
            badgeText = qualityOptions[currentQualityIndex]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Preferred Video Quality",
                dialogSubtitle = "Choose the default resolution to stream",
                options = qualityOptions,
                selectedIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }
            ) { newIdx ->
                YoutubePreferences.preferredQuality = qualityValues[newIdx]
                badgeView.text = "${qualityOptions[newIdx]}  ›"
                Toast.makeText(context, "Quality set to ${qualityOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        playbackCard.addView(qualityRow)
        playbackCard.addView(SettingsComponents.createDivider(context))

        val subtitleOptions = arrayOf("None", "English", "Spanish", "Tamil", "Hindi", "Arabic", "French", "German", "Japanese", "Auto")
        val currentSubIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }

        val subRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "💬",
            title = "Preferred Subtitle Language",
            description = "Auto-select matching subtitle track",
            badgeText = subtitleOptions[currentSubIndex]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Preferred Subtitle Language",
                dialogSubtitle = "Select your default subtitle language",
                options = subtitleOptions,
                selectedIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }
            ) { newIdx ->
                YoutubePreferences.preferredSubtitle = subtitleOptions[newIdx]
                badgeView.text = "${subtitleOptions[newIdx]}  ›"
                Toast.makeText(context, "Subtitles set to ${subtitleOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        playbackCard.addView(subRow)
        content.addView(playbackCard)

        // ==========================================
        // SECTION 2: SUBSCRIBED CHANNELS
        // ==========================================
        val subsHeader = SettingsComponents.createSectionHeaderWithBadge(
            context = context,
            title = "CHANNEL SUBSCRIPTIONS",
            iconEmoji = "📺",
            badgeCount = YoutubePreferences.getSubscribedChannels().size
        )
        content.addView(subsHeader.first)

        val subsCard = SettingsComponents.createCardContainer(context).apply {
            setPadding(SettingsTheme.dp(context, 14), SettingsTheme.dp(context, 14), SettingsTheme.dp(context, 14), SettingsTheme.dp(context, 14))
        }

        val addBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = SettingsTheme.dp(context, 8) }
        }

        val input = EditText(context).apply {
            hint = "@handle, channel URL, or ID"
            setHintTextColor(SettingsTheme.COLOR_TEXT_MUTED)
            setTextColor(SettingsTheme.COLOR_TEXT_PRIMARY)
            textSize = 13.5f
            background = SettingsTheme.createRoundedDrawable(SettingsTheme.COLOR_INNER_BG, 12f, SettingsTheme.COLOR_CARD_BORDER, 1f, context)
            setPadding(SettingsTheme.dp(context, 12), SettingsTheme.dp(context, 10), SettingsTheme.dp(context, 12), SettingsTheme.dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 8)
            }
            setSingleLine(true)
        }
        addBox.addView(input)

        val channelListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val addBtn = Button(context).apply {
            text = "+ Add"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            background = SettingsTheme.createRippleDrawable(context, SettingsTheme.COLOR_PRIMARY, SettingsTheme.COLOR_RIPPLE, 12f)
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 10), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 10))
        }
        addBox.addView(addBtn)
        subsCard.addView(addBox)

        val helperText = TextView(context).apply {
            text = "Channels appear on CloudStream home screen. Add by @handle or link."
            setTextColor(SettingsTheme.COLOR_TEXT_MUTED)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = SettingsTheme.dp(context, 12) }
        }
        subsCard.addView(helperText)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = SettingsTheme.dp(context, 10)
            }
        }
        subsCard.addView(progressBar)

        val updateCountUi = {
            val newCount = YoutubePreferences.getSubscribedChannels().size
            subsHeader.second.text = "$newCount"
            statusText.text = "CloudStream Provider Active • $newCount Channels Subscribed"
        }

        addBtn.setOnClickListener {
            val raw = input.text.toString().trim()
            if (raw.isNotBlank()) {
                input.text.clear()
                addBtn.isEnabled = false
                progressBar.visibility = View.VISIBLE
                SubscriptionDialogs.resolveAndAddChannel(context, raw) {
                    addBtn.isEnabled = true
                    progressBar.visibility = View.GONE
                    SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
                    updateCountUi()
                }
            }
        }

        SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
        subsCard.addView(channelListContainer)
        content.addView(subsCard)

        // ==========================================
        // SECTION 3: HOME SCREEN ROWS
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "HOME SCREEN FEED ROWS", "🎛"))
        val homeFeedCard = SettingsComponents.createCardContainer(context)

        val rowToggles = listOf(
            Triple(HomeRowType.COMBINED_SUBSCRIPTIONS, "🔔", "Single merged row of all your subscribed channels"),
            Triple(HomeRowType.INDIVIDUAL_CHANNELS, "👥", "Separate dedicated row for each subscribed creator"),
            Triple(HomeRowType.TRENDING, "🔥", "Popular viral videos worldwide"),
            Triple(HomeRowType.MOVIES_AND_SHOWS, "🎬", "Free featured YouTube movies and episodes"),
            Triple(HomeRowType.MUSIC, "🎵", "Trending music releases and popular audio tracks"),
            Triple(HomeRowType.GAMING, "🎮", "Top gaming livestreams and game clips"),
            Triple(HomeRowType.PODCASTS, "🎙", "Popular video podcasts and creator interviews"),
            Triple(HomeRowType.LIVE_STREAMS, "🔴", "Real-time ongoing broadcasts and premieres")
        )

        rowToggles.forEachIndexed { idx, (rowType, emoji, desc) ->
            homeFeedCard.addView(SettingsComponents.createSwitchRow(
                context, emoji, rowType.displayName, desc,
                YoutubePreferences.isRowEnabled(rowType)
            ) { YoutubePreferences.setRowEnabled(rowType, it) })
            if (idx < rowToggles.size - 1) {
                homeFeedCard.addView(SettingsComponents.createDivider(context))
            }
        }
        content.addView(homeFeedCard)

        // ==========================================
        // SECTION 4: PERFORMANCE & CACHING
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "PERFORMANCE & CACHING", "⚡"))
        val cacheCard = SettingsComponents.createCardContainer(context)

        // Configurable Cache TTL Row
        val ttlOptions = arrayOf("1 Minute (Realtime)", "5 Minutes (Default)", "15 Minutes", "30 Minutes", "60 Minutes (Max)")
        val ttlValues = arrayOf(1, 5, 15, 30, 60)
        val currentTtlIndex = ttlValues.indexOf(YoutubePreferences.cacheTtlMinutes).let { if (it >= 0) it else 1 }

        val ttlRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "⏱️",
            title = "Feed Cache Duration (TTL)",
            description = "How long home feeds are kept before refetching",
            badgeText = ttlOptions[currentTtlIndex]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Feed Cache Duration (TTL)",
                dialogSubtitle = "Shorter duration updates faster, longer saves network",
                options = ttlOptions,
                selectedIndex = ttlValues.indexOf(YoutubePreferences.cacheTtlMinutes).let { if (it >= 0) it else 1 }
            ) { newIdx ->
                YoutubePreferences.cacheTtlMinutes = ttlValues[newIdx]
                badgeView.text = "${ttlOptions[newIdx]}  ›"
                Toast.makeText(context, "Cache duration set to ${ttlOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        cacheCard.addView(ttlRow)
        cacheCard.addView(SettingsComponents.createDivider(context))

        // Refresh Now / Clear Cache Row
        val refreshNowRow = SettingsComponents.createActionItemRow(
            context = context,
            iconEmoji = "⚡",
            iconBgColor = Color.parseColor("#212538"),
            title = "Refresh Now (Clear Cache)",
            description = "Instantly clears cached feeds and forces a fresh reload",
            buttonLabel = "Refresh",
            buttonColor = SettingsTheme.COLOR_PRIMARY,
            onClick = {
                YoutubePreferences.notifyCacheCleared()
                Toast.makeText(context, "⚡ In-memory feed cache cleared! Feeds will reload freshly.", Toast.LENGTH_SHORT).show()
            }
        )
        cacheCard.addView(refreshNowRow)
        content.addView(cacheCard)

        // ==========================================
        // SECTION 5: MAINTENANCE & RESET
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "MAINTENANCE & RESET", "⚙"))
        val actionsCard = SettingsComponents.createCardContainer(context)

        val resetRow = SettingsComponents.createActionItemRow(
            context = context,
            iconEmoji = "🔄",
            iconBgColor = SettingsTheme.COLOR_DANGER_BG,
            title = "Reset All Settings",
            description = "Reverts feed rows and subscriptions to initial defaults",
            buttonLabel = "Reset",
            buttonColor = SettingsTheme.COLOR_YOUTUBE_RED,
            onClick = {
                AlertDialog.Builder(context)
                    .setTitle("Reset YouTube Settings?")
                    .setMessage("This will restore default subscriptions and turn on all home feeds. Are you sure?")
                    .setPositiveButton("Reset") { _, _ ->
                        YoutubePreferences.resetToDefaults()
                        SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
                        updateCountUi()
                        Toast.makeText(context, "Settings restored to defaults!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        actionsCard.addView(resetRow)
        content.addView(actionsCard)

        val footer = TextView(context).apply {
            text = "CloudStream • YouTube Provider v1.3 • Stage 3 Performance Edition"
            setTextColor(SettingsTheme.COLOR_TEXT_MUTED)
            textSize = 11f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, SettingsTheme.dp(context, 18), 0, SettingsTheme.dp(context, 10))
        }
        content.addView(footer)

        scrollView.addView(content)
        root.addView(scrollView)

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }
}
