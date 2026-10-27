package com.youtube.ui

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
import com.youtube.ui.SettingsTheme.COLOR_CARD_BG
import com.youtube.ui.SettingsTheme.COLOR_CARD_BORDER
import com.youtube.ui.SettingsTheme.COLOR_DIVIDER
import com.youtube.ui.SettingsTheme.COLOR_PRIMARY
import com.youtube.ui.SettingsTheme.COLOR_PRIMARY_LIGHT
import com.youtube.ui.SettingsTheme.COLOR_PRIMARY_TINT
import com.youtube.ui.SettingsTheme.COLOR_RIPPLE
import com.youtube.ui.SettingsTheme.COLOR_SHEET_BG
import com.youtube.ui.SettingsTheme.COLOR_TEXT_PRIMARY
import com.youtube.ui.SettingsTheme.COLOR_TEXT_SECONDARY
import com.youtube.ui.SettingsTheme.createRippleDrawable
import com.youtube.ui.SettingsTheme.createRoundedDrawable
import com.youtube.ui.SettingsTheme.createSwitchThumbTint
import com.youtube.ui.SettingsTheme.createSwitchTrackTint
import com.youtube.ui.SettingsTheme.dp

object SettingsComponents {

    fun createCardContainer(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(COLOR_CARD_BG, 14f, COLOR_CARD_BORDER, 1f, context)
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 12) }
        }
    }

    fun createDivider(context: Context): View {
        return View(context).apply {
            background = ColorDrawable(COLOR_DIVIDER)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, 1)
            ).apply {
                marginStart = dp(context, 16)
                marginEnd = dp(context, 16)
            }
        }
    }

    fun createSectionHeader(context: Context, title: String): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 6))
        }
        val textView = TextView(context).apply {
            text = title
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        row.addView(textView)
        return row
    }

    fun createSectionHeaderWithBadge(
        context: Context,
        title: String,
        badgeCount: Int
    ): Pair<LinearLayout, TextView> {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
        row.addView(textView)
        row.addView(badge)
        return Pair(row, badge)
    }

    fun createSettingRow(
        context: Context,
        title: String,
        description: String,
        badgeText: String,
        onClick: (badgeView: TextView) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRippleDrawable(context, Color.TRANSPARENT, COLOR_RIPPLE, 14f)
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 12)
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

        val badge = TextView(context).apply {
            text = "$badgeText  ›"
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = createRoundedDrawable(COLOR_PRIMARY_TINT, 10f, COLOR_PRIMARY, 1f, context)
            setPadding(dp(context, 10), dp(context, 5), dp(context, 10), dp(context, 5))
        }
        row.addView(badge)

        row.setOnClickListener { onClick(badge) }
        return row
    }

    fun createSwitchRow(
        context: Context,
        title: String,
        description: String,
        initialChecked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRippleDrawable(context, Color.TRANSPARENT, COLOR_RIPPLE, 14f)
            setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 12)
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

    fun createActionItemRow(
        context: Context,
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
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(context, 12)
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

    fun showCloudStreamSelectionDialog(
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
                    dp(context, 20).toFloat(), dp(context, 20).toFloat(),
                    dp(context, 20).toFloat(), dp(context, 20).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 22))
        }

        val handle = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 14)
            }
        }
        pickerRoot.addView(handle)

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
                background = createRippleDrawable(context, bgColor, COLOR_RIPPLE, 12f, borderColor, if (isSelected) 1.5f else 1f)
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
}
