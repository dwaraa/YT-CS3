package com.youtube

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.youtube.ui.SettingsComponents
import com.youtube.ui.SettingsContentSection
import com.youtube.ui.SettingsTheme
import com.youtube.ui.SubscriptionDialogs

object YoutubeSettings {

    fun show(context: Context) {
        val dialog = BottomSheetDialog(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, SettingsTheme.dp(context, 8), 0, SettingsTheme.dp(context, 20))
        }

        // Top drag handle
        val pill = View(context).apply {
            background = SettingsTheme.getSelectableItemBackground(context)
            setBackgroundColor((SettingsTheme.getTextSecondaryColor(context) and 0x00FFFFFF) or 0x44000000)
            layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 36), SettingsTheme.dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = SettingsTheme.dp(context, 4)
                bottomMargin = SettingsTheme.dp(context, 12)
            }
        }
        root.addView(pill)

        // Header Bar
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(SettingsTheme.dp(context, 16), 0, SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = "YouTube Settings"
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            text = "Preferences & Subscriptions"
            setTextColor(SettingsTheme.getTextSecondaryColor(context))
            textSize = 12f
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        headerBar.addView(titleCol)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 8))
            setOnClickListener { dialog.dismiss() }
        }
        headerBar.addView(closeBtn)
        root.addView(headerBar)
        root.addView(SettingsComponents.createDivider(context))

        val displayHeight = context.resources.displayMetrics.heightPixels
        val scrollMaxHeight = (displayHeight * 0.75).toInt()

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
        // SECTION 1: PLAYBACK & RESOLUTION
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "Playback & Resolution"))

        val qualityOptions = arrayOf("Auto (Best)", "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p")
        val qualityValues = arrayOf("Auto", "2160p", "1440p", "1080p", "720p", "480p", "360p")
        val currentQualityIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }

        val qualityRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Preferred Video Quality",
            description = "Default stream resolution for playback",
            valueText = qualityOptions[currentQualityIndex]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Preferred Video Quality",
                options = qualityOptions,
                selectedIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }
            ) { newIdx ->
                YoutubePreferences.preferredQuality = qualityValues[newIdx]
                badgeView.text = "${qualityOptions[newIdx]}  ›"
                Toast.makeText(context, "Quality set to ${qualityOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(qualityRow)
        content.addView(SettingsComponents.createDivider(context))

        val subtitleOptions = arrayOf("None", "English", "Spanish", "Tamil", "Hindi", "Arabic", "French", "German", "Japanese", "Auto")
        val currentSubIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }

        val subRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Preferred Subtitle Language",
            description = "Auto-select matching subtitle track",
            valueText = subtitleOptions[currentSubIndex]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Preferred Subtitle Language",
                options = subtitleOptions,
                selectedIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }
            ) { newIdx ->
                YoutubePreferences.preferredSubtitle = subtitleOptions[newIdx]
                badgeView.text = "${subtitleOptions[newIdx]}  ›"
                Toast.makeText(context, "Subtitle language set to ${subtitleOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(subRow)
        content.addView(SettingsComponents.createDivider(context))

        // ==========================================
        // SECTION 2: SUBSCRIBED CHANNELS
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "Subscribed Channels"))

        val channelListContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val updateCountUi: () -> Unit = {
            subtitle.text = "${YoutubePreferences.getSubscribedChannels().size} Channels • Region: ${YoutubePreferences.trendingRegion}"
        }

        // Add Channel Row
        val addRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8))
        }

        val input = EditText(context).apply {
            hint = "@creator or channel URL"
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 13.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 8)
            }
        }
        val addBtn = Button(context).apply {
            text = "Add"
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 13f
        }
        addRow.addView(input)
        addRow.addView(addBtn)
        content.addView(addRow)

        // Import & Export Row
        val importExportBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(SettingsTheme.dp(context, 16), 0, SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8))
        }
        val importBtn = Button(context).apply {
            text = "Import"
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 6)
            }
            setOnClickListener {
                SubscriptionDialogs.showImportDialog(context) {
                    SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
                    updateCountUi()
                }
            }
        }
        val reorderBtn = Button(context).apply {
            text = "Reorder"
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = SettingsTheme.dp(context, 6)
            }
            setOnClickListener {
                SubscriptionDialogs.showReorderChannelsDialog(context) {
                    SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
                    updateCountUi()
                }
            }
        }
        val exportBtn = Button(context).apply {
            text = "Export"
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = SettingsTheme.dp(context, 6)
            }
            setOnClickListener {
                SubscriptionDialogs.showExportDialog(context)
            }
        }
        importExportBox.addView(importBtn)
        importExportBox.addView(reorderBtn)
        importExportBox.addView(exportBtn)
        content.addView(importExportBox)

        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = SettingsTheme.dp(context, 8)
            }
        }
        content.addView(progressBar)

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
        content.addView(channelListContainer)

        // ==========================================
        // SECTION 3: FEED & CONTENT CUSTOMIZATION
        // ==========================================
        val contentViews = SettingsContentSection.buildContentCustomizationViews(context, subtitle)
        contentViews.forEach { content.addView(it) }

        // ==========================================
        // SECTION 4: HOME SCREEN FEED ROWS
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "Home Screen Feed Rows"))

        val rowToggles = listOf(
            Pair(HomeRowType.COMBINED_SUBSCRIPTIONS, "Merged feed of all subscribed creators"),
            Pair(HomeRowType.SUBSCRIBED_CHANNELS, "Channel avatars in a vertical poster row"),
            Pair(HomeRowType.SHORTS, "Vertical short-form videos"),
            Pair(HomeRowType.INDIVIDUAL_CHANNELS, "Separate feed row for each subscribed creator"),
            Pair(HomeRowType.TRENDING, "Trending videos in your selected region"),
            Pair(HomeRowType.MOVIES_AND_SHOWS, "Featured movies and shows"),
            Pair(HomeRowType.MUSIC, "Trending music tracks and releases"),
            Pair(HomeRowType.GAMING, "Gaming broadcasts and clips"),
            Pair(HomeRowType.PODCASTS, "Creator podcasts and talk shows"),
            Pair(HomeRowType.LIVE_STREAMS, "Real-time ongoing broadcasts")
        )

        rowToggles.forEach { (rowType, desc) ->
            content.addView(SettingsComponents.createSwitchRow(
                context, rowType.displayName, desc,
                YoutubePreferences.isRowEnabled(rowType)
            ) { YoutubePreferences.setRowEnabled(rowType, it) })
            content.addView(SettingsComponents.createDivider(context))
        }

        // ==========================================
        // SECTION 5: SEARCH & HISTORY
        // ==========================================
        val searchViews = SettingsContentSection.buildSearchAndHistoryViews(context)
        searchViews.forEach { content.addView(it) }

        // ==========================================
        // SECTION 6: CACHE & PERFORMANCE
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "Cache & Performance"))

        val ttlOptions = arrayOf("1 Minute", "5 Minutes (Default)", "15 Minutes", "30 Minutes", "60 Minutes")
        val ttlValues = arrayOf(1, 5, 15, 30, 60)
        val currentTtlIndex = ttlValues.indexOf(YoutubePreferences.cacheTtlMinutes).let { if (it >= 0) it else 1 }

        val ttlRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Feed Cache Duration",
            description = "How long home feeds are kept before refreshing",
            valueText = ttlOptions[currentTtlIndex]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Feed Cache Duration",
                options = ttlOptions,
                selectedIndex = ttlValues.indexOf(YoutubePreferences.cacheTtlMinutes).let { if (it >= 0) it else 1 }
            ) { newIdx ->
                YoutubePreferences.cacheTtlMinutes = ttlValues[newIdx]
                badgeView.text = "${ttlOptions[newIdx]}  ›"
                Toast.makeText(context, "Cache duration set to ${ttlOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        content.addView(ttlRow)
        content.addView(SettingsComponents.createDivider(context))

        val refreshNowRow = SettingsComponents.createActionItemRow(
            context = context,
            title = "Clear Cache Now",
            description = "Clears in-memory cached feeds to force a fresh reload",
            buttonLabel = "Clear",
            onClick = {
                YoutubePreferences.notifyCacheCleared()
                Toast.makeText(context, "Feed cache cleared.", Toast.LENGTH_SHORT).show()
            }
        )
        content.addView(refreshNowRow)
        content.addView(SettingsComponents.createDivider(context))

        // ==========================================
        // SECTION 7: MAINTENANCE & RESET
        // ==========================================
        content.addView(SettingsComponents.createSectionHeader(context, "Maintenance"))

        val resetRow = SettingsComponents.createActionItemRow(
            context = context,
            title = "Reset All Settings",
            description = "Restores all preferences and subscriptions to defaults",
            buttonLabel = "Reset",
            isDestructive = true,
            onClick = {
                AlertDialog.Builder(context)
                    .setTitle("Reset YouTube Settings?")
                    .setMessage("This will restore default subscriptions, reset trending region, and enable all home feeds. Are you sure?")
                    .setPositiveButton("Reset") { _, _ ->
                        YoutubePreferences.resetToDefaults()
                        SubscriptionDialogs.refreshChannelList(context, channelListContainer, updateCountUi)
                        updateCountUi()
                        Toast.makeText(context, "Settings restored to defaults.", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        content.addView(resetRow)

        val footer = TextView(context).apply {
            text = "CloudStream • YouTube Provider"
            setTextColor(SettingsTheme.getTextSecondaryColor(context))
            textSize = 11.5f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, SettingsTheme.dp(context, 16), 0, SettingsTheme.dp(context, 8))
        }
        content.addView(footer)

        scrollView.addView(content)
        root.addView(scrollView)

        dialog.setContentView(root)
        dialog.show()
    }
}
