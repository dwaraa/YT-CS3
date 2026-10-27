package com.youtube.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.youtube.SubscribedChannel
import com.youtube.YoutubePreferences
import com.youtube.YoutubeProvider
import com.youtube.subscription.YoutubeSubscriptionImporter
import com.youtube.ui.SettingsTheme.COLOR_CARD_BORDER
import com.youtube.ui.SettingsTheme.COLOR_DANGER_BG
import com.youtube.ui.SettingsTheme.COLOR_DANGER_TEXT
import com.youtube.ui.SettingsTheme.COLOR_INNER_BG
import com.youtube.ui.SettingsTheme.COLOR_PRIMARY
import com.youtube.ui.SettingsTheme.COLOR_RIPPLE
import com.youtube.ui.SettingsTheme.COLOR_TEXT_MUTED
import com.youtube.ui.SettingsTheme.COLOR_TEXT_PRIMARY
import com.youtube.ui.SettingsTheme.COLOR_TEXT_SECONDARY
import com.youtube.ui.SettingsTheme.createInitialsGradient
import com.youtube.ui.SettingsTheme.createRippleDrawable
import com.youtube.ui.SettingsTheme.createRoundedDrawable
import com.youtube.ui.SettingsTheme.dp
import com.youtube.ui.SettingsTheme.loadAvatarInto
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

object SubscriptionDialogs {

    fun refreshChannelList(context: Context, container: LinearLayout, onChannelCountChanged: (() -> Unit)? = null) {
        container.removeAllViews()
        val channels = YoutubePreferences.getSubscribedChannels()

        if (channels.isEmpty()) {
            val emptyBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(context, 14), 0, dp(context, 8))
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
                    refreshChannelList(context, container, onChannelCountChanged)
                    onChannelCountChanged?.invoke()
                    Toast.makeText(context, "Removed '${channel.name}'", Toast.LENGTH_SHORT).show()
                }
            }
            item.addView(removeBtn)

            container.addView(item)
        }
    }

    fun resolveAndAddChannel(context: Context, rawInput: String, onDone: () -> Unit) {
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
                android.util.Log.e("SubscriptionDialogs", "Error resolving channel $normalizedUrl: ${e.message}", e)
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

    fun showImportDialog(context: Context, onImportComplete: () -> Unit) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 8))
        }

        val desc = TextView(context).apply {
            text = "Paste your subscriptions data in NewPipe JSON, Google Takeout CSV, or OPML format below:"
            setTextColor(COLOR_TEXT_SECONDARY)
            textSize = 12f
            setPadding(0, 0, 0, dp(context, 8))
        }
        root.addView(desc)

        val input = EditText(context).apply {
            hint = "Paste JSON, CSV, or OPML here..."
            setHintTextColor(COLOR_TEXT_MUTED)
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 12f
            minLines = 6
            maxLines = 10
            gravity = Gravity.TOP or Gravity.START
            background = createRoundedDrawable(COLOR_INNER_BG, 10f, COLOR_CARD_BORDER, 1f, context)
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }
        root.addView(input)

        val pasteBtn = Button(context).apply {
            text = "Paste from Clipboard"
            setTextColor(COLOR_PRIMARY)
            textSize = 11.5f
            background = createRippleDrawable(context, COLOR_INNER_BG, COLOR_RIPPLE, 8f, COLOR_CARD_BORDER, 1f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 8) }
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (clipText.isNotBlank()) {
                    input.setText(clipText)
                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(pasteBtn)

        AlertDialog.Builder(context)
            .setTitle("Import Subscriptions")
            .setView(root)
            .setPositiveButton("Import") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(context, "No content provided to import", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val res = YoutubeSubscriptionImporter.detectAndImport(text)
                if (res.importedChannels.isEmpty()) {
                    Toast.makeText(context, res.errorMessage ?: "Could not detect channels in input", Toast.LENGTH_LONG).show()
                } else {
                    val added = YoutubePreferences.importChannels(res.importedChannels)
                    Toast.makeText(context, "Imported $added new channels (${res.totalFound} found in ${res.format})!", Toast.LENGTH_LONG).show()
                    onImportComplete()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showExportDialog(context: Context) {
        val channels = YoutubePreferences.getSubscribedChannels()
        if (channels.isEmpty()) {
            Toast.makeText(context, "No subscriptions to export", Toast.LENGTH_SHORT).show()
            return
        }

        val options = arrayOf("NewPipe JSON (Full Backup)", "Google Takeout CSV")
        AlertDialog.Builder(context)
            .setTitle("Export Subscriptions (${channels.size} channels)")
            .setItems(options) { _, which ->
                val exportedText = when (which) {
                    0 -> YoutubeSubscriptionImporter.exportToNewPipeJson(channels)
                    else -> YoutubeSubscriptionImporter.exportToTakeoutCsv(channels)
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("YouTube Subscriptions", exportedText)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Copied ${channels.size} subscriptions to clipboard as ${options[which]}.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
