package com.youtube.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat

object SettingsComponents {

    fun createDivider(context: Context): View {
        return View(context).apply {
            background = ColorDrawable(SettingsTheme.getDividerColor(context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                SettingsTheme.dp(context, 1)
            ).apply {
                marginStart = SettingsTheme.dp(context, 16)
                marginEnd = SettingsTheme.dp(context, 16)
            }
        }
    }

    fun createSectionHeader(context: Context, title: String): View {
        return TextView(context).apply {
            text = title.uppercase()
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 14),
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 6)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    fun createSettingRow(
        context: Context,
        title: String,
        description: String,
        valueText: String,
        onClick: (TextView) -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = SettingsTheme.getSelectableItemBackground(context)
            isClickable = true
            isFocusable = true
            setPadding(
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12),
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 12)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 15f
            setTypeface(null, Typeface.NORMAL)
        }
        textCol.addView(titleView)

        if (description.isNotBlank()) {
            val descView = TextView(context).apply {
                text = description
                setTextColor(SettingsTheme.getTextSecondaryColor(context))
                textSize = 12.5f
                setPadding(0, SettingsTheme.dp(context, 2), 0, 0)
            }
            textCol.addView(descView)
        }

        row.addView(textCol)

        val valueView = TextView(context).apply {
            text = "$valueText  ›"
            setTextColor(SettingsTheme.getTextSecondaryColor(context))
            textSize = 13.5f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        row.addView(valueView)

        row.setOnClickListener {
            onClick(valueView)
        }

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
            background = SettingsTheme.getSelectableItemBackground(context)
            isClickable = true
            isFocusable = true
            setPadding(
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12),
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 12)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 15f
        }
        textCol.addView(titleView)

        if (description.isNotBlank()) {
            val descView = TextView(context).apply {
                text = description
                setTextColor(SettingsTheme.getTextSecondaryColor(context))
                textSize = 12.5f
                setPadding(0, SettingsTheme.dp(context, 2), 0, 0)
            }
            textCol.addView(descView)
        }

        row.addView(textCol)

        val switch = SwitchCompat(context).apply {
            isChecked = initialChecked
            thumbTintList = SettingsTheme.createSwitchThumbTint(context)
            trackTintList = SettingsTheme.createSwitchTrackTint(context)
            isClickable = false // handled by row
        }
        row.addView(switch)

        row.setOnClickListener {
            val newState = !switch.isChecked
            switch.isChecked = newState
            onCheckedChange(newState)
        }

        return row
    }

    fun createActionItemRow(
        context: Context,
        title: String,
        description: String,
        buttonLabel: String,
        isDestructive: Boolean = false,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = SettingsTheme.getSelectableItemBackground(context)
            isClickable = true
            isFocusable = true
            setPadding(
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12),
                SettingsTheme.dp(context, 16),
                SettingsTheme.dp(context, 12)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = SettingsTheme.dp(context, 12)
            }
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 15f
        }
        textCol.addView(titleView)

        if (description.isNotBlank()) {
            val descView = TextView(context).apply {
                text = description
                setTextColor(SettingsTheme.getTextSecondaryColor(context))
                textSize = 12.5f
                setPadding(0, SettingsTheme.dp(context, 2), 0, 0)
            }
            textCol.addView(descView)
        }

        row.addView(textCol)

        val btn = TextView(context).apply {
            text = buttonLabel
            setTextColor(
                if (isDestructive) {
                    androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_red_light)
                } else {
                    SettingsTheme.getPrimaryColor(context)
                }
            )
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        row.addView(btn)

        row.setOnClickListener {
            onClick()
        }

        return row
    }

    fun showSingleChoiceDialog(
        context: Context,
        title: String,
        options: Array<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
