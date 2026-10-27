package com.youtube.ui

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.youtube.SearchContentFilter
import com.youtube.SearchSortOrder
import com.youtube.ShortsMode
import com.youtube.YoutubePreferences

object SettingsContentSection {

    fun buildContentCustomizationCard(
        context: Context,
        statusText: TextView
    ): Pair<LinearLayout, LinearLayout> {
        val header = SettingsComponents.createSectionHeader(context, "FEED & CONTENT CUSTOMIZATION", "🎯")
        val contentCard = SettingsComponents.createCardContainer(context)

        // 1. Shorts Handling Mode
        val shortsOptions = arrayOf("Show in feeds (Default)", "Dedicated Shorts row", "Hide all Shorts")
        val shortsValues = arrayOf(ShortsMode.SHOW_INLINE, ShortsMode.SEPARATE_ROW, ShortsMode.HIDE)
        val currentShortsIdx = shortsValues.indexOf(YoutubePreferences.shortsMode).let { if (it >= 0) it else 0 }

        val shortsRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "⚡",
            title = "Shorts Handling",
            description = "How vertical shorts appear on your home screen",
            badgeText = shortsOptions[currentShortsIdx]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Shorts Handling",
                dialogSubtitle = "Choose how YouTube Shorts should be displayed",
                options = shortsOptions,
                selectedIndex = shortsValues.indexOf(YoutubePreferences.shortsMode).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.shortsMode = shortsValues[newIdx]
                badgeView.text = "${shortsOptions[newIdx]}  ›"
                Toast.makeText(context, "Shorts mode set to ${shortsOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        contentCard.addView(shortsRow)
        contentCard.addView(SettingsComponents.createDivider(context))

        // 2. Hide Watched / Unwatched Only Toggle
        val unwatchedRow = SettingsComponents.createSwitchRow(
            context = context,
            iconEmoji = "👁️",
            title = "Unwatched Only in Subscriptions",
            description = "Filter out completed videos from your subscription feed",
            initialChecked = YoutubePreferences.hideWatchedInSubscriptions
        ) { checked ->
            YoutubePreferences.hideWatchedInSubscriptions = checked
            Toast.makeText(context, if (checked) "Only unwatched videos shown in subscriptions" else "All subscription videos shown", Toast.LENGTH_SHORT).show()
        }
        contentCard.addView(unwatchedRow)
        contentCard.addView(SettingsComponents.createDivider(context))

        // 3. Per-Row Item Limits
        val itemLimitOptions = arrayOf("10 videos", "15 videos", "20 videos (Default)", "25 videos", "30 videos", "40 videos")
        val itemLimitValues = arrayOf(10, 15, 20, 25, 30, 40)
        val currentLimitIdx = itemLimitValues.indexOf(YoutubePreferences.itemsPerRowLimit).let { if (it >= 0) it else 2 }

        val limitRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "🔢",
            title = "Items Per Row Limit",
            description = "Maximum videos to display in each feed row",
            badgeText = itemLimitOptions[currentLimitIdx]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Items Per Row Limit",
                dialogSubtitle = "Adjust the maximum videos loaded per row",
                options = itemLimitOptions,
                selectedIndex = itemLimitValues.indexOf(YoutubePreferences.itemsPerRowLimit).let { if (it >= 0) it else 2 }
            ) { newIdx ->
                YoutubePreferences.itemsPerRowLimit = itemLimitValues[newIdx]
                badgeView.text = "${itemLimitOptions[newIdx]}  ›"
                Toast.makeText(context, "Row limit set to ${itemLimitOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        contentCard.addView(limitRow)
        contentCard.addView(SettingsComponents.createDivider(context))

        // 4. Trending Country / Region
        val regionNames = arrayOf(
            "United States (US)", "United Kingdom (GB)", "India (IN)", "Canada (CA)",
            "Germany (DE)", "France (FR)", "Japan (JP)", "Brazil (BR)",
            "Australia (AU)", "South Korea (KR)", "Mexico (MX)", "Italy (IT)"
        )
        val regionCodes = arrayOf("US", "GB", "IN", "CA", "DE", "FR", "JP", "BR", "AU", "KR", "MX", "IT")
        val currentRegionIdx = regionCodes.indexOf(YoutubePreferences.trendingRegion).let { if (it >= 0) it else 0 }

        val regionRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "🌍",
            title = "Trending Region / Country",
            description = "Geographic locale for trending videos",
            badgeText = regionNames[currentRegionIdx]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Trending Region",
                dialogSubtitle = "Select the country for trending and popular feeds",
                options = regionNames,
                selectedIndex = regionCodes.indexOf(YoutubePreferences.trendingRegion).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.trendingRegion = regionCodes[newIdx]
                badgeView.text = "${regionNames[newIdx]}  ›"
                statusText.text = "Active • ${YoutubePreferences.getSubscribedChannels().size} Channels • Region: ${regionCodes[newIdx]}"
                Toast.makeText(context, "Trending region set to ${regionNames[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        contentCard.addView(regionRow)
        contentCard.addView(SettingsComponents.createDivider(context))

        // 5. Content Language
        val langNames = arrayOf("English (en)", "Spanish (es)", "Hindi (hi)", "French (fr)", "German (de)", "Japanese (ja)", "Korean (ko)", "Portuguese (pt)", "Arabic (ar)")
        val langCodes = arrayOf("en", "es", "hi", "fr", "de", "ja", "ko", "pt", "ar")
        val currentLangIdx = langCodes.indexOf(YoutubePreferences.contentLanguage).let { if (it >= 0) it else 0 }

        val langRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "🌐",
            title = "Content Language",
            description = "Language localization for titles and categories",
            badgeText = langNames[currentLangIdx]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Content Language",
                dialogSubtitle = "Choose the localization language for content",
                options = langNames,
                selectedIndex = langCodes.indexOf(YoutubePreferences.contentLanguage).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.contentLanguage = langCodes[newIdx]
                badgeView.text = "${langNames[newIdx]}  ›"
                Toast.makeText(context, "Language set to ${langNames[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        contentCard.addView(langRow)

        return Pair(header, contentCard)
    }

    fun buildSearchAndHistoryCard(context: Context): Pair<LinearLayout, LinearLayout> {
        val header = SettingsComponents.createSectionHeader(context, "SEARCH & HISTORY", "🔍")
        val searchCard = SettingsComponents.createCardContainer(context)

        val searchFilterOptions = arrayOf("All Results (Default)", "Videos Only", "Channels Only", "Playlists Only")
        val searchFilterValues = arrayOf(SearchContentFilter.ALL, SearchContentFilter.VIDEOS_ONLY, SearchContentFilter.CHANNELS_ONLY, SearchContentFilter.PLAYLISTS_ONLY)
        val currentSearchFilterIdx = searchFilterValues.indexOf(YoutubePreferences.searchContentFilter).let { if (it >= 0) it else 0 }

        val searchFilterRow = SettingsComponents.createSettingRow(
            context = context,
            iconEmoji = "🎯",
            title = "Default Search Filter",
            description = "Default category filter for search queries",
            badgeText = searchFilterOptions[currentSearchFilterIdx]
        ) { badgeView ->
            SettingsComponents.showCloudStreamSelectionDialog(
                context = context,
                dialogTitle = "Default Search Filter",
                dialogSubtitle = "Choose the default content type when searching",
                options = searchFilterOptions,
                selectedIndex = searchFilterValues.indexOf(YoutubePreferences.searchContentFilter).let { if (it >= 0) it else 0 }
            ) { newIdx ->
                YoutubePreferences.searchContentFilter = searchFilterValues[newIdx]
                badgeView.text = "${searchFilterOptions[newIdx]}  ›"
                Toast.makeText(context, "Search filter set to ${searchFilterOptions[newIdx]}", Toast.LENGTH_SHORT).show()
            }
        }
        searchCard.addView(searchFilterRow)
        searchCard.addView(SettingsComponents.createDivider(context))

        val clearHistoryRow = SettingsComponents.createActionItemRow(
            context = context,
            iconEmoji = "🧹",
            iconBgColor = Color.parseColor("#212538"),
            title = "Clear Search & Watch History",
            description = "Resets search query history and unwatched flags",
            buttonLabel = "Clear",
            buttonColor = SettingsTheme.COLOR_PRIMARY,
            onClick = {
                YoutubePreferences.clearSearchHistory()
                YoutubePreferences.clearWatchHistory()
                Toast.makeText(context, "Search and watch history cleared!", Toast.LENGTH_SHORT).show()
            }
        )
        searchCard.addView(clearHistoryRow)

        return Pair(header, searchCard)
    }
}
