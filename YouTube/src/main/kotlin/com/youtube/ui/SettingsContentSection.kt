package com.youtube.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.youtube.HomeRowType
import com.youtube.SearchContentFilter
import com.youtube.ShortsMode
import com.youtube.YoutubePreferences
import com.youtube.power.DiagnosticsHelper
import com.youtube.power.LocalPlaylistHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SettingsContentSection {

    fun buildContentCustomizationViews(
        context: Context,
        statusText: TextView
    ): List<View> {
        val views = mutableListOf<View>()

        views.add(SettingsComponents.createSectionHeader(context, "Feed & Content Customization"))

        // 1. Reorder Home Feed Sections
        val reorderRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Reorder Home Feed Sections",
            description = "Customize the order of categories on your home screen",
            valueText = "Reorder ›"
        ) {
            showReorderHomeRowsDialog(context)
        }
        views.add(reorderRow)
        views.add(SettingsComponents.createDivider(context))

        // 2. Subscribed Channels Row (Vertical Posters)
        val channelsRowToggle = SettingsComponents.createSwitchRow(
            context = context,
            title = "Subscribed Channels Row",
            description = "Show channel avatars in a vertical poster row on home screen",
            initialChecked = YoutubePreferences.isRowEnabled(HomeRowType.SUBSCRIBED_CHANNELS)
        ) { checked ->
            YoutubePreferences.setRowEnabled(HomeRowType.SUBSCRIBED_CHANNELS, checked)
            Toast.makeText(context, if (checked) "Subscribed Channels row enabled" else "Subscribed Channels row disabled", Toast.LENGTH_SHORT).show()
        }
        views.add(channelsRowToggle)
        views.add(SettingsComponents.createDivider(context))

        // 2. Shorts Handling Mode
        val shortsOptions = arrayOf("Show in feeds (Default)", "Dedicated Shorts row", "Hide all Shorts")
        val shortsValues = arrayOf(ShortsMode.SHOW_INLINE, ShortsMode.SEPARATE_ROW, ShortsMode.HIDE)
        val currentShortsIdx = shortsValues.indexOf(YoutubePreferences.shortsMode).let { if (it >= 0) it else 0 }

        val shortsRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Shorts Handling",
            description = "How vertical shorts appear on your home screen",
            valueText = shortsOptions[currentShortsIdx]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Shorts Handling",
                options = shortsOptions,
                selectedIndex = shortsValues.indexOf(YoutubePreferences.shortsMode).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.shortsMode = shortsValues[newIdx]
                badgeView.text = "${shortsOptions[newIdx]}  ›"
                Toast.makeText(context, "Shorts mode set to ${shortsOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        views.add(shortsRow)
        views.add(SettingsComponents.createDivider(context))

        // 3. Hide Watched / Unwatched Only Toggle
        val unwatchedRow = SettingsComponents.createSwitchRow(
            context = context,
            title = "Unwatched Only in Subscriptions",
            description = "Filter out completed videos from your subscription feed",
            initialChecked = YoutubePreferences.hideWatchedInSubscriptions
        ) { checked ->
            YoutubePreferences.hideWatchedInSubscriptions = checked
            Toast.makeText(context, if (checked) "Only unwatched videos shown in subscriptions" else "All subscription videos shown", Toast.LENGTH_SHORT).show()
        }
        views.add(unwatchedRow)
        views.add(SettingsComponents.createDivider(context))

        // 4. Per-Row Item Limits
        val itemLimitOptions = arrayOf("10 videos", "15 videos", "20 videos (Default)", "25 videos", "30 videos", "40 videos")
        val itemLimitValues = arrayOf(10, 15, 20, 25, 30, 40)
        val currentLimitIdx = itemLimitValues.indexOf(YoutubePreferences.itemsPerRowLimit).let { if (it >= 0) it else 2 }

        val limitRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Items Per Row Limit",
            description = "Maximum videos to display in each feed row",
            valueText = itemLimitOptions[currentLimitIdx]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Items Per Row Limit",
                options = itemLimitOptions,
                selectedIndex = itemLimitValues.indexOf(YoutubePreferences.itemsPerRowLimit).let { if (it >= 0) it else 2 }
            ) { newIdx ->
                YoutubePreferences.itemsPerRowLimit = itemLimitValues[newIdx]
                badgeView.text = "${itemLimitOptions[newIdx]}  ›"
                Toast.makeText(context, "Row limit set to ${itemLimitOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        views.add(limitRow)
        views.add(SettingsComponents.createDivider(context))

        // 5. Trending Country / Region
        val regionNames = arrayOf(
            "United States (US)", "United Kingdom (GB)", "India (IN)", "Canada (CA)",
            "Germany (DE)", "France (FR)", "Japan (JP)", "Brazil (BR)",
            "Australia (AU)", "South Korea (KR)", "Mexico (MX)", "Italy (IT)"
        )
        val regionCodes = arrayOf("US", "GB", "IN", "CA", "DE", "FR", "JP", "BR", "AU", "KR", "MX", "IT")
        val currentRegionIdx = regionCodes.indexOf(YoutubePreferences.trendingRegion).let { if (it >= 0) it else 0 }

        val regionRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Trending Region / Country",
            description = "Geographic locale for trending videos",
            valueText = regionNames[currentRegionIdx]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Trending Region",
                options = regionNames,
                selectedIndex = regionCodes.indexOf(YoutubePreferences.trendingRegion).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.trendingRegion = regionCodes[newIdx]
                badgeView.text = "${regionNames[newIdx]}  ›"
                statusText.text = "${YoutubePreferences.getSubscribedChannels().size} Channels • Region: ${regionCodes[newIdx]}"
                Toast.makeText(context, "Trending region set to ${regionNames[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        views.add(regionRow)
        views.add(SettingsComponents.createDivider(context))

        // 6. Content Language
        val langNames = arrayOf("English (en)", "Spanish (es)", "Hindi (hi)", "French (fr)", "German (de)", "Japanese (ja)", "Korean (ko)", "Portuguese (pt)", "Arabic (ar)")
        val langCodes = arrayOf("en", "es", "hi", "fr", "de", "ja", "ko", "pt", "ar")
        val currentLangIdx = langCodes.indexOf(YoutubePreferences.contentLanguage).let { if (it >= 0) it else 0 }

        val langRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Content Language",
            description = "Language localization for titles and categories",
            valueText = langNames[currentLangIdx]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Content Language",
                options = langNames,
                selectedIndex = langCodes.indexOf(YoutubePreferences.contentLanguage).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.contentLanguage = langCodes[newIdx]
                badgeView.text = "${langNames[newIdx]}  ›"
                Toast.makeText(context, "Language set to ${langNames[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        views.add(langRow)
        views.add(SettingsComponents.createDivider(context))

        return views
    }

    fun buildSearchAndHistoryViews(context: Context): List<View> {
        val views = mutableListOf<View>()

        views.add(SettingsComponents.createSectionHeader(context, "Search & History"))

        val searchFilterOptions = arrayOf("All Results (Default)", "Videos Only", "Channels Only", "Playlists Only")
        val searchFilterValues = arrayOf(SearchContentFilter.ALL, SearchContentFilter.VIDEOS_ONLY, SearchContentFilter.CHANNELS_ONLY, SearchContentFilter.PLAYLISTS_ONLY)
        val currentSearchFilterIdx = searchFilterValues.indexOf(YoutubePreferences.searchContentFilter).let { if (it >= 0) it else 0 }

        val searchFilterRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Default Search Filter",
            description = "Default category filter for search queries",
            valueText = searchFilterOptions[currentSearchFilterIdx]
        ) { badgeView ->
            SettingsComponents.showSingleChoiceDialog(
                context = context,
                title = "Default Search Filter",
                options = searchFilterOptions,
                selectedIndex = searchFilterValues.indexOf(YoutubePreferences.searchContentFilter).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.searchContentFilter = searchFilterValues[newIdx]
                badgeView.text = "${searchFilterOptions[newIdx]}  ›"
                Toast.makeText(context, "Search filter set to ${searchFilterOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        views.add(searchFilterRow)
        views.add(SettingsComponents.createDivider(context))

        val clearHistoryRow = SettingsComponents.createActionItemRow(
            context = context,
            title = "Clear Search & Watch History",
            description = "Resets search query history and unwatched flags",
            buttonLabel = "Clear",
            onClick = {
                YoutubePreferences.clearSearchHistory()
                YoutubePreferences.clearWatchHistory()
                Toast.makeText(context, "Search and watch history cleared.", Toast.LENGTH_SHORT).show()
            }
        )
        views.add(clearHistoryRow)
        views.add(SettingsComponents.createDivider(context))

        return views
    }

    fun showReorderHomeRowsDialog(context: Context) {
        val rows = YoutubePreferences.getRowOrder().toMutableList()
        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 16))
        }
        scroll.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Reorder Home Feed Sections")
            .setView(scroll)
            .setPositiveButton("Done", null)
            .setNeutralButton("Reset to Defaults") { _, _ ->
                YoutubePreferences.saveRowOrder(YoutubePreferences.defaultRowOrder)
                Toast.makeText(context, "Reset feed rows to default order", Toast.LENGTH_SHORT).show()
            }
            .create()

        fun rebuildList() {
            container.removeAllViews()
            val textPrimary = SettingsTheme.getTextPrimaryColor(context)
            val textSecondary = SettingsTheme.getTextSecondaryColor(context)
            val primaryColor = SettingsTheme.getPrimaryColor(context)

            val hint = TextView(context).apply {
                text = "Use ▲ and ▼ to order sections on your home screen."
                setTextColor(textSecondary)
                textSize = 12f
                setPadding(0, 0, 0, SettingsTheme.dp(context, 12))
            }
            container.addView(hint)

            for (i in rows.indices) {
                val rowType = rows[i]
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, SettingsTheme.dp(context, 8), 0, SettingsTheme.dp(context, 8))
                }

                val indexBadge = TextView(context).apply {
                    text = "#${i + 1}"
                    setTextColor(primaryColor)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 32), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                row.addView(indexBadge)

                val nameView = TextView(context).apply {
                    text = rowType.displayName
                    setTextColor(textPrimary)
                    textSize = 13.5f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = SettingsTheme.dp(context, 8)
                    }
                }
                row.addView(nameView)

                val upBtn = Button(context).apply {
                    text = "▲"
                    setTextColor(if (i > 0) primaryColor else textSecondary)
                    isEnabled = i > 0
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 44), SettingsTheme.dp(context, 38))
                    setOnClickListener {
                        if (i > 0) {
                            val temp = rows[i]
                            rows[i] = rows[i - 1]
                            rows[i - 1] = temp
                            YoutubePreferences.saveRowOrder(rows)
                            rebuildList()
                        }
                    }
                }
                val downBtn = Button(context).apply {
                    text = "▼"
                    setTextColor(if (i < rows.size - 1) primaryColor else textSecondary)
                    isEnabled = i < rows.size - 1
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 44), SettingsTheme.dp(context, 38)).apply {
                        marginStart = SettingsTheme.dp(context, 4)
                    }
                    setOnClickListener {
                        if (i < rows.size - 1) {
                            val temp = rows[i]
                            rows[i] = rows[i + 1]
                            rows[i + 1] = temp
                            YoutubePreferences.saveRowOrder(rows)
                            rebuildList()
                        }
                    }
                }

                row.addView(upBtn)
                row.addView(downBtn)
                container.addView(row)
                container.addView(SettingsComponents.createDivider(context))
            }
        }

        rebuildList()
        dialog.show()
    }

    // ==========================================
    // STAGE 5: POWER FEATURES VIEWS & DIALOGS
    // ==========================================

    fun buildPowerFeaturesViews(
        context: Context,
        onProfileChanged: () -> Unit
    ): List<View> {
        val views = mutableListOf<View>()

        views.add(SettingsComponents.createSectionHeader(context, "Power Features"))

        // 1. Multiple Profiles Manager
        val profileRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Active Profile",
            description = "Current workspace for subscriptions, feeds, and watch history",
            valueText = "${YoutubePreferences.activeProfile}  ›"
        ) {
            showProfileManagerDialog(context, onProfileChanged)
        }
        views.add(profileRow)
        views.add(SettingsComponents.createDivider(context))

        // 2. SponsorBlock Integration
        val sponsorBlockRow = SettingsComponents.createSwitchRow(
            context = context,
            title = "SponsorBlock Integration",
            description = "Skip sponsor segments, intro cards, and outro plugs via WebVTT markers",
            initialChecked = YoutubePreferences.enableSponsorBlock
        ) { checked ->
            YoutubePreferences.enableSponsorBlock = checked
            Toast.makeText(context, if (checked) "SponsorBlock enabled" else "SponsorBlock disabled", Toast.LENGTH_SHORT).show()
        }
        views.add(sponsorBlockRow)
        views.add(SettingsComponents.createDivider(context))

        // 3. Return YouTube Dislike (RYD)
        val rydRow = SettingsComponents.createSwitchRow(
            context = context,
            title = "Return YouTube Dislike (RYD)",
            description = "Fetch real-time dislike counts and viewer approval ratings",
            initialChecked = YoutubePreferences.enableReturnYouTubeDislike
        ) { checked ->
            YoutubePreferences.enableReturnYouTubeDislike = checked
            Toast.makeText(context, if (checked) "Return YouTube Dislike enabled" else "Return YouTube Dislike disabled", Toast.LENGTH_SHORT).show()
        }
        views.add(rydRow)
        views.add(SettingsComponents.createDivider(context))

        // 4. Audio-Only / Background Playback Mode
        val audioOnlyRow = SettingsComponents.createSwitchRow(
            context = context,
            title = "Audio-Only Mode",
            description = "Prioritize lightweight audio streams for music, lectures, and podcasts",
            initialChecked = YoutubePreferences.audioOnlyMode
        ) { checked ->
            YoutubePreferences.audioOnlyMode = checked
            Toast.makeText(context, if (checked) "Audio-only playback mode enabled" else "Normal video mode enabled", Toast.LENGTH_SHORT).show()
        }
        views.add(audioOnlyRow)
        views.add(SettingsComponents.createDivider(context))

        // 5. Local Playlists & Watch Later
        val playlistCount = LocalPlaylistHelper.getAllPlaylists().size
        val watchLaterCount = LocalPlaylistHelper.getWatchLater().size
        val playlistsRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Local Playlists & Watch Later",
            description = "$watchLaterCount queued • $playlistCount local playlists",
            valueText = "Manage  ›"
        ) {
            showLocalPlaylistsDialog(context)
        }
        views.add(playlistsRow)
        views.add(SettingsComponents.createDivider(context))

        // 6. Diagnostics & Self-Test
        val diagnosticsRow = SettingsComponents.createSettingRow(
            context = context,
            title = "Diagnostics & Self-Test",
            description = "Run extraction test, ping latencies, and copy debug report",
            valueText = "Run Test  ›"
        ) {
            showDiagnosticsDialog(context)
        }
        views.add(diagnosticsRow)
        views.add(SettingsComponents.createDivider(context))

        return views
    }

    fun showProfileManagerDialog(context: Context, onProfileChanged: () -> Unit) {
        val profiles = YoutubePreferences.getAvailableProfiles().toMutableList()
        val current = YoutubePreferences.activeProfile

        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 16))
        }
        scroll.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setTitle("User Profiles")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("+ Add Profile") { _, _ ->
                val input = EditText(context).apply {
                    hint = "Profile Name (e.g. Kids, Music, Work)"
                }
                AlertDialog.Builder(context)
                    .setTitle("New Profile")
                    .setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) {
                            if (YoutubePreferences.addProfile(name)) {
                                Toast.makeText(context, "Created and switched to '$name'", Toast.LENGTH_SHORT).show()
                                onProfileChanged()
                            } else {
                                Toast.makeText(context, "Profile already exists", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .create()

        fun rebuild() {
            container.removeAllViews()
            val textPrimary = SettingsTheme.getTextPrimaryColor(context)
            val textSecondary = SettingsTheme.getTextSecondaryColor(context)
            val primaryColor = SettingsTheme.getPrimaryColor(context)

            val hint = TextView(context).apply {
                text = "Each profile has its own subscribed channels, home feed ordering, and watched history."
                setTextColor(textSecondary)
                textSize = 12f
                setPadding(0, 0, 0, SettingsTheme.dp(context, 12))
            }
            container.addView(hint)

            for (p in profiles) {
                val isActive = p.equals(YoutubePreferences.activeProfile, ignoreCase = true)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, SettingsTheme.dp(context, 8), 0, SettingsTheme.dp(context, 8))
                }

                val title = TextView(context).apply {
                    text = if (isActive) "$p (Active)" else p
                    setTextColor(if (isActive) primaryColor else textPrimary)
                    textSize = 14f
                    setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(title)

                if (!isActive) {
                    val switchBtn = Button(context).apply {
                        text = "Switch"
                        setTextColor(primaryColor)
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 75), SettingsTheme.dp(context, 38))
                        setOnClickListener {
                            YoutubePreferences.switchProfile(p)
                            Toast.makeText(context, "Switched to profile '$p'", Toast.LENGTH_SHORT).show()
                            onProfileChanged()
                            dialog.dismiss()
                        }
                    }
                    row.addView(switchBtn)
                }

                if (!p.equals("Default", ignoreCase = true)) {
                    val deleteBtn = Button(context).apply {
                        text = "✕"
                        setTextColor(0xFFFF4444.toInt())
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 44), SettingsTheme.dp(context, 38)).apply {
                            marginStart = SettingsTheme.dp(context, 4)
                        }
                        setOnClickListener {
                            AlertDialog.Builder(context)
                                .setTitle("Delete Profile '$p'?")
                                .setMessage("This will remove this profile and all its configured subscriptions.")
                                .setPositiveButton("Delete") { _, _ ->
                                    YoutubePreferences.deleteProfile(p)
                                    profiles.remove(p)
                                    rebuild()
                                    onProfileChanged()
                                    Toast.makeText(context, "Deleted profile '$p'", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }
                    row.addView(deleteBtn)
                }

                container.addView(row)
                container.addView(SettingsComponents.createDivider(context))
            }
        }

        rebuild()
        dialog.show()
    }

    fun showLocalPlaylistsDialog(context: Context) {
        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 8), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 16))
        }
        scroll.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Watch Later & Playlists")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("+ New Playlist") { _, _ ->
                val input = EditText(context).apply {
                    hint = "Playlist Name"
                }
                AlertDialog.Builder(context)
                    .setTitle("Create Local Playlist")
                    .setView(input)
                    .setPositiveButton("Create") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotBlank()) {
                            LocalPlaylistHelper.createPlaylist(name)
                            Toast.makeText(context, "Created playlist '$name'", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .create()

        fun rebuild() {
            container.removeAllViews()
            val textPrimary = SettingsTheme.getTextPrimaryColor(context)
            val textSecondary = SettingsTheme.getTextSecondaryColor(context)
            val primaryColor = SettingsTheme.getPrimaryColor(context)

            // Watch Later Section
            val wlHeader = TextView(context).apply {
                text = "Watch Later"
                setTextColor(primaryColor)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }
            container.addView(wlHeader)

            val wlItems = LocalPlaylistHelper.getWatchLater()
            val wlSub = TextView(context).apply {
                text = "${wlItems.size} videos in Watch Later queue"
                setTextColor(textSecondary)
                textSize = 12f
                setPadding(0, 0, 0, SettingsTheme.dp(context, 8))
            }
            container.addView(wlSub)

            if (wlItems.isNotEmpty()) {
                val clearWlBtn = Button(context).apply {
                    text = "Clear Watch Later"
                    setTextColor(0xFFFF4444.toInt())
                    setOnClickListener {
                        LocalPlaylistHelper.clearWatchLater()
                        rebuild()
                        Toast.makeText(context, "Watch Later cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                container.addView(clearWlBtn)
            }

            container.addView(SettingsComponents.createDivider(context))

            // Playlists Section
            val plHeader = TextView(context).apply {
                text = "Custom Local Playlists"
                setTextColor(primaryColor)
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, SettingsTheme.dp(context, 12), 0, 0)
            }
            container.addView(plHeader)

            val playlists = LocalPlaylistHelper.getAllPlaylists()
            if (playlists.isEmpty()) {
                val emptyTxt = TextView(context).apply {
                    text = "No custom playlists created yet."
                    setTextColor(textSecondary)
                    textSize = 12f
                    setPadding(0, SettingsTheme.dp(context, 8), 0, 0)
                }
                container.addView(emptyTxt)
            } else {
                for (pl in playlists) {
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, SettingsTheme.dp(context, 8), 0, SettingsTheme.dp(context, 8))
                    }

                    val titleCol = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val plTitle = TextView(context).apply {
                        text = pl.name
                        setTextColor(textPrimary)
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                    }
                    val plInfo = TextView(context).apply {
                        text = "${pl.items.size} videos"
                        setTextColor(textSecondary)
                        textSize = 12f
                    }
                    titleCol.addView(plTitle)
                    titleCol.addView(plInfo)
                    row.addView(titleCol)

                    val delBtn = Button(context).apply {
                        text = "Delete"
                        setTextColor(0xFFFF4444.toInt())
                        textSize = 12f
                        layoutParams = LinearLayout.LayoutParams(SettingsTheme.dp(context, 75), SettingsTheme.dp(context, 38))
                        setOnClickListener {
                            LocalPlaylistHelper.deletePlaylist(pl.id)
                            rebuild()
                            Toast.makeText(context, "Deleted playlist '${pl.name}'", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(delBtn)
                    container.addView(row)
                    container.addView(SettingsComponents.createDivider(context))
                }
            }
        }

        rebuild()
        dialog.show()
    }

    fun showDiagnosticsDialog(context: Context) {
        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 12), SettingsTheme.dp(context, 16), SettingsTheme.dp(context, 16))
        }
        scroll.addView(container)

        val progress = ProgressBar(context).apply {
            isIndeterminate = true
        }
        container.addView(progress)

        val statusText = TextView(context).apply {
            text = "Running self-test & diagnostics...\nChecking NewPipeExtractor, cookies, latencies, and suggestions."
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 13f
            setPadding(0, SettingsTheme.dp(context, 8), 0, 0)
        }
        container.addView(statusText)

        var latestReport = ""

        val dialog = AlertDialog.Builder(context)
            .setTitle("Diagnostics & Self-Test")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy Report") { _, _ ->
                if (latestReport.isNotBlank()) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("YouTube Plugin Diagnostics", latestReport)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Full debug report copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        dialog.show()

        CoroutineScope(Dispatchers.IO).launch {
            val result = DiagnosticsHelper.runSelfTest(context)
            val fullReport = DiagnosticsHelper.generateMarkdownReport(result)
            latestReport = fullReport

            withContext(Dispatchers.Main) {
                progress.visibility = View.GONE
                statusText.text = """
                    • NewPipeExtractor: ${result.newpipeVersion} (${if (result.newpipeVersionOk) "✅ OK" else "⚠️ Issue"})
                    • YouTube Extraction: ${if (result.extractionOk) "✅ Passed" else "❌ Failed (${result.extractionDetails})"}
                    • Consent Cookie: ${if (result.consentCookieHealthy) "✅ Healthy" else "⚠️ Needs Refresh"}
                    • YouTube Ping: ${if (result.pingYoutubeMs >= 0) "${result.pingYoutubeMs}ms" else "Offline"}
                    • SponsorBlock Ping: ${if (result.pingSponsorBlockMs >= 0) "${result.pingSponsorBlockMs}ms" else "Offline"}
                    • RYD Ping: ${if (result.pingRydMs >= 0) "${result.pingRydMs}ms" else "Offline"}
                    • Audio Tracks Helper: ${if (result.audioTrackHelperFunctional) "✅ Functional" else "⚠️ Unavailable"}
                    • Subscriptions: ${result.activeSubscriptionsCount} channels
                    • Active Profile: ${result.activeProfile}

                    ${result.summary}
                """.trimIndent()
            }
        }
    }
}

