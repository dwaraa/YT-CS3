package com.youtube

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

/**
 * CloudStream-styled Native Settings UI for YouTube Provider.
 * Replicates CloudStream's signature app design system:
 * - Surface colors: #101115 (sheet), #181A23 (grouped cards), #13141C (inner inputs)
 * - Accent colors: #3D50FA (CloudStream signature indigo), #5062FF (hover), #8C9EFF (badge text)
 * - YouTube branding highlights: #E50914
 * - Material You / CloudStream grouped card architecture with ripple effects,
 *   subtle borders (#262836), inset dividers, and custom option selection modals.
 */
object YoutubeSettings {

    // --- Color Tokens (Matching CloudStream App Design System) ---
    private val COLOR_SHEET_BG = Color.parseColor("#101115")
    private val COLOR_CARD_BG = Color.parseColor("#181A23")
    private val COLOR_CARD_BORDER = Color.parseColor("#262836")
    private val COLOR_INNER_BG = Color.parseColor("#13141C")
    private val COLOR_DIVIDER = Color.parseColor("#222432")

    private val COLOR_PRIMARY = Color.parseColor("#3D50FA") // CloudStream Indigo
    private val COLOR_PRIMARY_LIGHT = Color.parseColor("#8C9EFF")
    private val COLOR_PRIMARY_TINT = Color.parseColor("#1E2448")

    private val COLOR_TEXT_PRIMARY = Color.parseColor("#F1F2F6")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#9296A6")
    private val COLOR_TEXT_MUTED = Color.parseColor("#646878")

    private val COLOR_YOUTUBE_RED = Color.parseColor("#E50914")
    private val COLOR_DANGER_BG = Color.parseColor("#2A161B")
    private val COLOR_DANGER_TEXT = Color.parseColor("#FF5252")

    private val COLOR_RIPPLE = Color.parseColor("#33FFFFFF")

    // In-memory avatar bitmap cache to avoid redownloading channel pictures
    private val avatarCache = mutableMapOf<String, Bitmap>()

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun createRoundedDrawable(
        bgColor: Int,
        cornerRadiusDp: Float,
        strokeColor: Int = 0,
        strokeWidthDp: Float = 0f,
        context: Context? = null
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            cornerRadius = if (context != null) dp(context, cornerRadiusDp.toInt()).toFloat() else cornerRadiusDp
            if (strokeWidthDp > 0f && strokeColor != 0) {
                val strokeWidthPx = if (context != null) dp(context, strokeWidthDp.toInt()) else strokeWidthDp.toInt()
                setStroke(strokeWidthPx, strokeColor)
            }
        }
    }

    private fun createRippleDrawable(
        context: Context,
        normalColor: Int,
        rippleColor: Int,
        cornerRadiusDp: Float,
        strokeColor: Int = 0,
        strokeWidthDp: Float = 0f
    ): Drawable {
        val content = createRoundedDrawable(normalColor, cornerRadiusDp, strokeColor, strokeWidthDp, context)
        val mask = createRoundedDrawable(Color.WHITE, cornerRadiusDp, 0, 0f, context)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    private fun createSwitchThumbTint(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(COLOR_PRIMARY, Color.parseColor("#7A7E92"))
        )
    }

    private fun createSwitchTrackTint(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(Color.parseColor("#252E63"), Color.parseColor("#252735"))
        )
    }

    fun show(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SHEET_BG)
                cornerRadii = floatArrayOf(
                    dp(context, 28).toFloat(), dp(context, 28).toFloat(),
                    dp(context, 28).toFloat(), dp(context, 28).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 20))
        }

        // Top drag handle pill
        val pill = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 14)
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
            ).apply { bottomMargin = dp(context, 14) }
        }

        // Header YouTube Icon Badge
        val logoBox = FrameLayout(context).apply {
            background = createRoundedDrawable(COLOR_YOUTUBE_RED, 10f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)).apply {
                marginEnd = dp(context, 12)
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

        // Header Title Column
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = "YouTube Settings"
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        }
        val subtitle = TextView(context).apply {
            text = "CloudStream Extension • Preferences"
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        headerBar.addView(titleCol)

        // Header Close Button
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 15f
            gravity = Gravity.CENTER
            background = createRippleDrawable(context, Color.parseColor("#20222E"), COLOR_RIPPLE, 16f, COLOR_CARD_BORDER, 1f)
            layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32))
            setOnClickListener { dialog.dismiss() }
        }
        headerBar.addView(closeBtn)
        root.addView(headerBar)

        // Status chip banner
        val statusBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedDrawable(Color.parseColor("#141620"), 10f, COLOR_CARD_BORDER, 1f, context)
            setPadding(dp(context, 12), dp(context, 7), dp(context, 12), dp(context, 7))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 14) }
        }
        val statusDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#10B981"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(context, 7), dp(context, 7)).apply {
                marginEnd = dp(context, 8)
            }
        }
        val statusText = TextView(context).apply {
            val subsCount = YoutubePreferences.getSubscribedChannels().size
            text = "CloudStream Provider Active • $subsCount Channels Subscribed"
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
        }
        statusBanner.addView(statusDot)
        statusBanner.addView(statusText)
        root.addView(statusBanner)

        // Scrollable content area
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
        content.addView(createSectionHeader(context, "PLAYBACK & STREAMING", "🎬"))

        val playbackCard = createCardContainer(context)

        // Quality Setting
        val qualityOptions = arrayOf("Auto (Best)", "2160p (4K)", "1440p (2K)", "1080p (Full HD)", "720p (HD)", "480p", "360p")
        val qualityValues = arrayOf("Auto", "2160p", "1440p", "1080p", "720p", "480p", "360p")
        val currentQualityIndex = qualityValues.indexOf(YoutubePreferences.preferredQuality).let { if (it >= 0) it else 3 }

        val qualityRow = createSettingRow(
            context = context,
            iconEmoji = "📺",
            title = "Preferred Video Quality",
            description = "Default stream resolution for playback",
            badgeText = qualityOptions[currentQualityIndex]
        ) { badgeView ->
            showCloudStreamSelectionDialog(
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
        playbackCard.addView(createDivider(context))

        // Subtitle Setting
        val subtitleOptions = arrayOf("None", "English", "Spanish", "Tamil", "Hindi", "Arabic", "French", "German", "Japanese", "Auto")
        val currentSubIndex = subtitleOptions.indexOf(YoutubePreferences.preferredSubtitle).let { if (it >= 0) it else 1 }

        val subRow = createSettingRow(
            context = context,
            iconEmoji = "💬",
            title = "Preferred Subtitle Language",
            description = "Auto-select matching subtitle track",
            badgeText = subtitleOptions[currentSubIndex]
        ) { badgeView ->
            showCloudStreamSelectionDialog(
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
        val subsHeader = createSectionHeaderWithBadge(
            context = context,
            title = "CHANNEL SUBSCRIPTIONS",
            iconEmoji = "📺",
            badgeCount = YoutubePreferences.getSubscribedChannels().size
        )
        content.addView(subsHeader.first)

        val subsCard = createCardContainer(context).apply {
            setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14))
        }

        // Add Channel Input Row
        val addBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 8) }
        }

        val input = EditText(context).apply {
            hint = "@handle, channel URL, or ID"
            setHintTextColor(COLOR_TEXT_MUTED)
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 13.5f
            background = createRoundedDrawable(COLOR_INNER_BG, 12f, COLOR_CARD_BORDER, 1f, context)
            setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
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
            background = createRippleDrawable(context, COLOR_PRIMARY, COLOR_RIPPLE, 12f)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
        }
        addBox.addView(addBtn)
        subsCard.addView(addBox)

        // Helper explanation text
        val helperText = TextView(context).apply {
            text = "Channels appear on CloudStream home screen. Add by @handle or link."
            setTextColor(COLOR_TEXT_MUTED)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 12) }
        }
        subsCard.addView(helperText)

        // Loading Bar (hidden by default)
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(context, 10)
            }
        }
        subsCard.addView(progressBar)

        addBtn.setOnClickListener {
            val raw = input.text.toString().trim()
            if (raw.isNotBlank()) {
                input.text.clear()
                addBtn.isEnabled = false
                progressBar.visibility = View.VISIBLE
                resolveAndAddChannel(context, raw) {
                    addBtn.isEnabled = true
                    progressBar.visibility = View.GONE
                    refreshChannelList(context, channelListContainer)
                    val newCount = YoutubePreferences.getSubscribedChannels().size
                    subsHeader.second.text = "$newCount"
                    statusText.text = "CloudStream Provider Active • $newCount Channels Subscribed"
                }
            }
        }

        refreshChannelList(context, channelListContainer)
        subsCard.addView(channelListContainer)
        content.addView(subsCard)

        // ==========================================
        // SECTION 3: HOME SCREEN ROWS
        // ==========================================
        content.addView(createSectionHeader(context, "HOME SCREEN FEED ROWS", "🎛"))

        val homeFeedCard = createCardContainer(context)

        homeFeedCard.addView(createSwitchRow(
            context, "🔔", "Combined Subscriptions Feed",
            "Single merged row of all your subscribed channels",
            YoutubePreferences.showCombinedSubscriptions
        ) { YoutubePreferences.showCombinedSubscriptions = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "👥", "Individual Channel Rows",
            "Separate dedicated row for each subscribed creator",
            YoutubePreferences.showIndividualChannelRows
        ) { YoutubePreferences.showIndividualChannelRows = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🔥", "Trending Feed",
            "Popular viral videos worldwide",
            YoutubePreferences.showTrending
        ) { YoutubePreferences.showTrending = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🎬", "Movies & TV Shows",
            "Free featured YouTube movies and episodes",
            YoutubePreferences.showMovies
        ) { YoutubePreferences.showMovies = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🎵", "Music Feed",
            "Trending music releases and popular audio tracks",
            YoutubePreferences.showMusic
        ) { YoutubePreferences.showMusic = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🎮", "Gaming Feed",
            "Top gaming livestreams and game clips",
            YoutubePreferences.showGaming
        ) { YoutubePreferences.showGaming = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🎙", "Podcasts Feed",
            "Popular video podcasts and creator interviews",
            YoutubePreferences.showPodcasts
        ) { YoutubePreferences.showPodcasts = it })
        homeFeedCard.addView(createDivider(context))

        homeFeedCard.addView(createSwitchRow(
            context, "🔴", "Live Streams Feed",
            "Real-time ongoing broadcasts and premieres",
            YoutubePreferences.showLive
        ) { YoutubePreferences.showLive = it })

        content.addView(homeFeedCard)

        // ==========================================
        // SECTION 4: MAINTENANCE & ACTIONS
        // ==========================================
        content.addView(createSectionHeader(context, "MAINTENANCE & STORAGE", "⚙"))

        val actionsCard = createCardContainer(context)

        // Clear Cache Row
        val clearCacheRow = createActionItemRow(
            context = context,
            iconEmoji = "⚡",
            iconBgColor = Color.parseColor("#212538"),
            title = "Clear Feed Cache",
            description = "Wipes in-memory cached feeds to force fresh results",
            buttonLabel = "Clear",
            buttonColor = COLOR_PRIMARY,
            onClick = {
                YoutubeProvider.clearFeedCache()
                Toast.makeText(context, "⚡ In-memory feed cache cleared! Feeds will reload.", Toast.LENGTH_SHORT).show()
            }
        )
        actionsCard.addView(clearCacheRow)
        actionsCard.addView(createDivider(context))

        // Reset Settings Row
        val resetRow = createActionItemRow(
            context = context,
            iconEmoji = "🔄",
            iconBgColor = COLOR_DANGER_BG,
            title = "Reset All Settings",
            description = "Reverts feed rows and subscriptions to initial defaults",
            buttonLabel = "Reset",
            buttonColor = COLOR_YOUTUBE_RED,
            onClick = {
                AlertDialog.Builder(context)
                    .setTitle("Reset YouTube Settings?")
                    .setMessage("This will restore default subscriptions and turn on all home feeds. Are you sure?")
                    .setPositiveButton("Reset") { _, _ ->
                        YoutubePreferences.resetToDefaults()
                        YoutubeProvider.clearFeedCache()
                        refreshChannelList(context, channelListContainer)
                        val newCount = YoutubePreferences.getSubscribedChannels().size
                        subsHeader.second.text = "$newCount"
                        statusText.text = "CloudStream Provider Active • $newCount Channels Subscribed"
                        Toast.makeText(context, "Settings restored to defaults!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        actionsCard.addView(resetRow)
        content.addView(actionsCard)

        // Footer version info
        val footer = TextView(context).apply {
            text = "CloudStream • YouTube Provider v1.0 • Built with NewPipe"
            setTextColor(COLOR_TEXT_MUTED)
            textSize = 11f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(context, 18), 0, dp(context, 10))
        }
        content.addView(footer)

        scrollView.addView(content)
        root.addView(scrollView)

        dialog.setContentView(root)

        // Dialog Window Attributes: Bottom Sheet modal with smooth slide animation
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

    // ==========================================
    // UI BUILDER HELPERS
    // ==========================================

    private fun createCardContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(COLOR_CARD_BG, 18f, COLOR_CARD_BORDER, 1f, context)
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 12) }
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            background = ColorDrawable(COLOR_DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1)
            ).apply {
                marginStart = dp(context, 60)
            }
        }
    }

    private fun createSectionHeader(context: Context, title: String, icon: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 6))
        }
        val iconView = TextView(context).apply {
            text = icon
            textSize = 12f
            setPadding(0, 0, dp(context, 6), 0)
        }
        val textView = TextView(context).apply {
            text = title
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        row.addView(iconView)
        row.addView(textView)
        return row
    }

    private fun createSectionHeaderWithBadge(
        context: Context,
        title: String,
        iconEmoji: String,
        badgeCount: Int
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val iconView = TextView(context).apply {
            text = iconEmoji
            textSize = 12f
            setPadding(0, 0, dp(context, 6), 0)
        }
        val textView = TextView(context).apply {
            text = title
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val badge = TextView(context).apply {
            text = "$badgeCount"
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(COLOR_PRIMARY_TINT, 10f, COLOR_PRIMARY, 1f, context)
            setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2))
        }
        row.addView(iconView)
        row.addView(textView)
        row.addView(badge)
        return Pair(row, badge)
    }

    private fun createSettingRow(
        context: Context,
        iconEmoji: String,
        title: String,
        description: String,
        badgeText: String,
        onClick: (badgeView: TextView) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRippleDrawable(context, Color.TRANSPARENT, COLOR_RIPPLE, 14f)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Left Icon Squircle
        val iconBox = FrameLayout(context).apply {
            background = createRoundedDrawable(Color.parseColor("#202334"), 12f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                marginEnd = dp(context, 12)
            }
        }
        val iconText = TextView(context).apply {
            text = iconEmoji
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        iconBox.addView(iconText)
        row.addView(iconBox)

        // Title & Description Column
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        val descView = TextView(context).apply {
            text = description
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
            setPadding(0, dp(context, 2), 0, 0)
        }
        textCol.addView(titleView)
        textCol.addView(descView)
        row.addView(textCol)

        // Right Badge
        val badge = TextView(context).apply {
            text = "$badgeText  ›"
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(COLOR_PRIMARY_TINT, 12f, COLOR_PRIMARY, 1f, context)
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        }
        row.addView(badge)

        row.setOnClickListener { onClick(badge) }
        return row
    }

    private fun createSwitchRow(
        context: Context,
        iconEmoji: String,
        title: String,
        description: String,
        initialChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRippleDrawable(context, Color.TRANSPARENT, COLOR_RIPPLE, 14f)
            setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Left Icon Squircle
        val iconBox = FrameLayout(context).apply {
            background = createRoundedDrawable(Color.parseColor("#202334"), 12f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                marginEnd = dp(context, 12)
            }
        }
        val iconText = TextView(context).apply {
            text = iconEmoji
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        iconBox.addView(iconText)
        row.addView(iconBox)

        // Text Column
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        val descView = TextView(context).apply {
            text = description
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
            setPadding(0, dp(context, 2), 0, 0)
        }
        textCol.addView(titleView)
        textCol.addView(descView)
        row.addView(textCol)

        // Switch Toggle styled with CloudStream accent
        val toggle = Switch(context).apply {
            isChecked = initialChecked
            thumbTintList = createSwitchThumbTint()
            trackTintList = createSwitchTrackTint()
            setOnCheckedChangeListener { _, checked ->
                onCheckedChange(checked)
            }
        }
        row.addView(toggle)

        row.setOnClickListener {
            toggle.isChecked = !toggle.isChecked
        }

        return row
    }

    private fun createActionItemRow(
        context: Context,
        iconEmoji: String,
        iconBgColor: Int,
        title: String,
        description: String,
        buttonLabel: String,
        buttonColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRippleDrawable(context, Color.TRANSPARENT, COLOR_RIPPLE, 14f)
            setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val iconBox = FrameLayout(context).apply {
            background = createRoundedDrawable(iconBgColor, 12f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                marginEnd = dp(context, 12)
            }
        }
        val iconText = TextView(context).apply {
            text = iconEmoji
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        iconBox.addView(iconText)
        row.addView(iconBox)

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 8)
            }
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
        }
        val descView = TextView(context).apply {
            text = description
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
            setPadding(0, dp(context, 2), 0, 0)
        }
        textCol.addView(titleView)
        textCol.addView(descView)
        row.addView(textCol)

        val btn = TextView(context).apply {
            text = buttonLabel
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createRippleDrawable(context, buttonColor, COLOR_RIPPLE, 10f)
            setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7))
        }
        row.addView(btn)

        row.setOnClickListener { onClick() }
        btn.setOnClickListener { onClick() }
        return row
    }

    // ==========================================
    // CLOUDSTREAM OPTION SELECTOR DIALOG
    // ==========================================

    private fun showCloudStreamSelectionDialog(
        context: Context,
        dialogTitle: String,
        dialogSubtitle: String,
        options: Array<String>,
        selectedIndex: Int,
        onSelect: (index: Int) -> Unit
    ) {
        val pickerDialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)

        val pickerRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SHEET_BG)
                cornerRadii = floatArrayOf(
                    dp(context, 24).toFloat(), dp(context, 24).toFloat(),
                    dp(context, 24).toFloat(), dp(context, 24).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 22))
        }

        // Pill handle
        val handle = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 14)
            }
        }
        pickerRoot.addView(handle)

        // Title
        val headText = TextView(context).apply {
            text = dialogTitle
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        pickerRoot.addView(headText)

        val subText = TextView(context).apply {
            text = dialogSubtitle
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 11.5f
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 14) }
        }
        pickerRoot.addView(subText)

        val optionScroll = ScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 340)
            )
        }

        val optionsBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (i in options.indices) {
            val isSelected = (i == selectedIndex)
            val optRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bgColor = if (isSelected) COLOR_PRIMARY_TINT else COLOR_CARD_BG
                val borderColor = if (isSelected) COLOR_PRIMARY else COLOR_CARD_BORDER
                background = createRippleDrawable(context, bgColor, COLOR_RIPPLE, 14f, borderColor, if (isSelected) 1.5f else 1f)
                setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 8) }
            }

            val optLabel = TextView(context).apply {
                text = options[i]
                setTextColor(if (isSelected) Color.WHITE else COLOR_TEXT_PRIMARY)
                textSize = 14f
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            optRow.addView(optLabel)

            if (isSelected) {
                val check = TextView(context).apply {
                    text = "✓"
                    setTextColor(COLOR_PRIMARY_LIGHT)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                }
                optRow.addView(check)
            }

            optRow.setOnClickListener {
                onSelect(i)
                pickerDialog.dismiss()
            }

            optionsBox.addView(optRow)
        }

        optionScroll.addView(optionsBox)
        pickerRoot.addView(optionScroll)

        pickerDialog.setContentView(pickerRoot)
        pickerDialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        pickerDialog.setCanceledOnTouchOutside(true)
        pickerDialog.show()
    }

    // ==========================================
    // CHANNEL SUBSCRIPTIONS MANAGEMENT
    // ==========================================

    private fun refreshChannelList(context: Context, container: LinearLayout) {
        container.removeAllViews()
        val channels = YoutubePreferences.getSubscribedChannels()

        if (channels.isEmpty()) {
            val emptyBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(context, 14), 0, dp(context, 8))
            }
            val emptyIcon = TextView(context).apply {
                text = "📺"
                textSize = 28f
                gravity = Gravity.CENTER
            }
            val emptyTitle = TextView(context).apply {
                text = "No Subscribed Channels"
                setTextColor(COLOR_TEXT_PRIMARY)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, dp(context, 4), 0, dp(context, 2))
            }
            val emptyDesc = TextView(context).apply {
                text = "Type a channel handle or URL above to add your favorite creators."
                setTextColor(COLOR_TEXT_MUTED)
                textSize = 11.5f
                gravity = Gravity.CENTER_HORIZONTAL
            }
            emptyBox.addView(emptyIcon)
            emptyBox.addView(emptyTitle)
            emptyBox.addView(emptyDesc)
            container.addView(emptyBox)
            return
        }

        for (channel in channels) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = createRoundedDrawable(COLOR_INNER_BG, 14f, COLOR_CARD_BORDER, 1f, context)
                setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(context, 8) }
            }

            // Avatar Container (Initials placeholder + async image)
            val avatarFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)).apply {
                    marginEnd = dp(context, 10)
                }
            }

            val initialsView = TextView(context).apply {
                val initials = channel.name.trim().take(2).uppercase()
                text = initials
                setTextColor(Color.WHITE)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = createInitialsGradient(channel.name)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            avatarFrame.addView(initialsView)

            val avatarImageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            avatarFrame.addView(avatarImageView)

            loadAvatarInto(channel.avatarUrl, avatarImageView, channel.url)
            item.addView(avatarFrame)

            // Channel Names
            val channelInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(context).apply {
                text = channel.name
                setTextColor(COLOR_TEXT_PRIMARY)
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
            }
            val handleView = TextView(context).apply {
                text = channel.url.substringAfterLast("/")
                setTextColor(COLOR_TEXT_SECONDARY)
                textSize = 11.5f
                maxLines = 1
            }
            channelInfo.addView(nameView)
            channelInfo.addView(handleView)
            item.addView(channelInfo)

            // Remove Button
            val removeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(COLOR_DANGER_TEXT)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = createRippleDrawable(context, COLOR_DANGER_BG, COLOR_RIPPLE, 10f, Color.parseColor("#421C22"), 1f)
                layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32))
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

    private fun createInitialsGradient(name: String): GradientDrawable {
        val hash = kotlin.math.abs(name.hashCode())
        val gradients = arrayOf(
            intArrayOf(Color.parseColor("#3D50FA"), Color.parseColor("#6979FF")),
            intArrayOf(Color.parseColor("#E50914"), Color.parseColor("#FF5252")),
            intArrayOf(Color.parseColor("#00897B"), Color.parseColor("#26A69A")),
            intArrayOf(Color.parseColor("#7B1FA2"), Color.parseColor("#AB47BC")),
            intArrayOf(Color.parseColor("#C2185B"), Color.parseColor("#EC407A")),
            intArrayOf(Color.parseColor("#F57C00"), Color.parseColor("#FFA726")),
            intArrayOf(Color.parseColor("#0288D1"), Color.parseColor("#29B6F6"))
        )
        val colors = gradients[hash % gradients.size]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = bitmap.width.coerceAtMost(bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isAntiAlias = true }
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawOval(rectF, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun loadAvatarInto(url: String, imageView: ImageView, channelUrl: String? = null) {
        if (url.isBlank()) {
            if (!channelUrl.isNullOrBlank()) {
                // Resolve channel avatar dynamically in background
                thread {
                    try {
                        val extractor = ServiceList.YouTube.getChannelExtractor(channelUrl)
                        extractor.fetchPage()
                        val resolvedUrl = extractor.avatars.lastOrNull()?.url ?: ""
                        if (resolvedUrl.isNotBlank()) {
                            YoutubePreferences.updateChannelAvatar(channelUrl, resolvedUrl)
                            Handler(Looper.getMainLooper()).post {
                                loadAvatarInto(resolvedUrl, imageView, null)
                            }
                        }
                    } catch (e: Throwable) {
                        android.util.Log.w("YoutubeSettings", "Failed to resolve channel avatar for $channelUrl: ${e.message}")
                    }
                }
            }
            return
        }
        val cached = avatarCache[url]
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }
        thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.doInput = true
                conn.connect()
                val inputStream = conn.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap != null) {
                    val circular = getCircularBitmap(bitmap)
                    avatarCache[url] = circular
                    Handler(Looper.getMainLooper()).post {
                        imageView.setImageBitmap(circular)
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("YoutubeSettings", "Failed to fetch avatar from $url: ${e.message}")
            }
        }
    }

    private fun resolveAndAddChannel(context: Context, rawInput: String, onDone: () -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        Toast.makeText(context, "Resolving channel with NewPipe...", Toast.LENGTH_SHORT).show()

        val normalizedUrl = when {
            rawInput.startsWith("http") -> rawInput
            rawInput.startsWith("@") -> "https://www.youtube.com/$rawInput"
            rawInput.startsWith("UC") -> "https://www.youtube.com/channel/$rawInput"
            else -> "https://www.youtube.com/@$rawInput"
        }

        thread {
            var channelName = rawInput.removePrefix("https://www.youtube.com/").removePrefix("@")
            var avatarUrl = ""
            var resolutionError: String? = null

            try {
                val extractor = ServiceList.YouTube.getChannelExtractor(normalizedUrl)
                extractor.fetchPage()
                channelName = extractor.name.takeIf { it.isNotBlank() } ?: channelName
                avatarUrl = extractor.avatars.lastOrNull()?.url ?: ""
            } catch (e: Exception) {
                android.util.Log.e("YoutubeSettings", "Error resolving channel $normalizedUrl: ${e.message}", e)
                resolutionError = e.localizedMessage ?: e.javaClass.simpleName
            }

            val channel = SubscribedChannel(
                id = rawInput,
                name = channelName,
                url = normalizedUrl,
                avatarUrl = avatarUrl
            )

            YoutubePreferences.addChannel(channel)
            YoutubeProvider.clearFeedCache()

            handler.post {
                if (resolutionError != null) {
                    Toast.makeText(
                        context,
                        "Added '$channelName' (Note: Could not fetch avatar: $resolutionError)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Added '$channelName' to subscriptions!", Toast.LENGTH_SHORT).show()
                }
                onDone()
            }
        }
    }
}
