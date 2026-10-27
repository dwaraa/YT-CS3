package com.youtube.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.youtube.SubscribedChannel
import com.youtube.YoutubePreferences
import com.youtube.YoutubeProvider
import com.youtube.subscription.YoutubeSubscriptionImporter
import com.youtube.ui.SettingsTheme.dp
import com.youtube.ui.SettingsTheme.loadAvatarInto
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

object SubscriptionDialogs {

    fun refreshChannelList(context: Context, container: LinearLayout, onChannelCountChanged: (() -> Unit)? = null) {
        container.removeAllViews()
        val channels = YoutubePreferences.getSubscribedChannels()

        val textPrimary = SettingsTheme.getTextPrimaryColor(context)
        val textSecondary = SettingsTheme.getTextSecondaryColor(context)

        if (channels.isEmpty()) {
            val emptyBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(context, 16), dp(context, 20), dp(context, 16), dp(context, 16))
            }
            val emptyTitle = TextView(context).apply {
                text = "No Subscribed Channels"
                setTextColor(textPrimary)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(context, 4))
            }
            val emptyDesc = TextView(context).apply {
                text = "Add channel handles or URLs to personalize your feed."
                setTextColor(textSecondary)
                textSize = 12f
                gravity = Gravity.CENTER_HORIZONTAL
            }
            emptyBox.addView(emptyTitle)
            emptyBox.addView(emptyDesc)
            container.addView(emptyBox)
            return
        }

        for (channel in channels) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = SettingsTheme.getSelectableItemBackground(context)
                setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Avatar Container (Circular async image)
            val avatarImageView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 38)).apply {
                    marginEnd = dp(context, 12)
                }
            }
            item.addView(avatarImageView)
            loadAvatarInto(channel.avatarUrl, avatarImageView, channel.url)

            // Channel Names
            val channelInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val nameView = TextView(context).apply {
                text = channel.name
                setTextColor(textPrimary)
                textSize = 14.5f
                setTypeface(null, Typeface.NORMAL)
                maxLines = 1
            }
            val handleView = TextView(context).apply {
                text = channel.url.substringAfterLast("/")
                setTextColor(textSecondary)
                textSize = 12f
                maxLines = 1
            }
            channelInfo.addView(nameView)
            channelInfo.addView(handleView)
            item.addView(channelInfo)

            // Remove Button
            val removeBtn = TextView(context).apply {
                text = "✕"
                setTextColor(androidx.core.content.ContextCompat.getColor(context, android.R.color.holo_red_light))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8))
                setOnClickListener {
                    AlertDialog.Builder(context)
                        .setTitle("Remove Channel")
                        .setMessage("Unsubscribe from '${channel.name}'?")
                        .setPositiveButton("Remove") { _, _ ->
                            YoutubePreferences.removeChannel(channel.url)
                            refreshChannelList(context, container, onChannelCountChanged)
                            onChannelCountChanged?.invoke()
                            Toast.makeText(context, "Removed '${channel.name}'", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            item.addView(removeBtn)

            container.addView(item)
            container.addView(SettingsComponents.createDivider(context))
        }
    }

    fun resolveAndAddChannel(context: Context, rawInput: String, onDone: () -> Unit) {
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
                        "Added '$channelName' (Avatar pending: $resolutionError)",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Added '$channelName' to subscriptions.", Toast.LENGTH_SHORT).show()
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
            setTextColor(SettingsTheme.getTextSecondaryColor(context))
            textSize = 12.5f
            setPadding(0, 0, 0, dp(context, 8))
        }
        root.addView(desc)

        val input = EditText(context).apply {
            hint = "Paste JSON, CSV, or OPML here..."
            setTextColor(SettingsTheme.getTextPrimaryColor(context))
            textSize = 13f
            minLines = 6
            maxLines = 10
            gravity = Gravity.TOP or Gravity.START
        }
        root.addView(input)

        val pasteBtn = Button(context).apply {
            text = "Paste from Clipboard"
            setTextColor(SettingsTheme.getPrimaryColor(context))
            textSize = 12f
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
                    Toast.makeText(context, "Imported $added new channels (${res.totalFound} found in ${res.format}).", Toast.LENGTH_LONG).show()
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

        val options = arrayOf("NewPipe JSON (Full Backup)", "Google Takeout CSV", "OPML XML (RSS / Feeds)")
        AlertDialog.Builder(context)
            .setTitle("Export Subscriptions (${channels.size} channels)")
            .setItems(options) { _, which ->
                val exportedText = when (which) {
                    0 -> YoutubeSubscriptionImporter.exportToNewPipeJson(channels)
                    1 -> YoutubeSubscriptionImporter.exportToTakeoutCsv(channels)
                    else -> YoutubeSubscriptionImporter.exportToOpml(channels)
                }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("YouTube Subscriptions", exportedText)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Copied ${channels.size} subscriptions to clipboard as ${options[which]}.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showReorderChannelsDialog(context: Context, onDone: () -> Unit) {
        val channels = YoutubePreferences.getSubscribedChannels().toMutableList()
        if (channels.isEmpty()) {
            Toast.makeText(context, "No channels to reorder", Toast.LENGTH_SHORT).show()
            return
        }

        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 16))
        }
        scroll.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Reorder Subscribed Channels")
            .setView(scroll)
            .setPositiveButton("Done") { _, _ -> onDone() }
            .create()

        fun rebuildList() {
            container.removeAllViews()
            val textPrimary = SettingsTheme.getTextPrimaryColor(context)
            val textSecondary = SettingsTheme.getTextSecondaryColor(context)
            val primaryColor = SettingsTheme.getPrimaryColor(context)

            val hint = TextView(context).apply {
                text = "Use ▲ and ▼ to order your channels for home posters and feeds."
                setTextColor(textSecondary)
                textSize = 12f
                setPadding(0, 0, 0, dp(context, 12))
            }
            container.addView(hint)

            for (i in channels.indices) {
                val channel = channels[i]
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(context, 8), 0, dp(context, 8))
                }

                val indexBadge = TextView(context).apply {
                    text = "#${i + 1}"
                    setTextColor(primaryColor)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(dp(context, 32), ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                row.addView(indexBadge)

                val nameCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(context, 8)
                    }
                }
                val nameView = TextView(context).apply {
                    text = channel.name
                    setTextColor(textPrimary)
                    textSize = 13.5f
                    maxLines = 1
                }
                val handleView = TextView(context).apply {
                    text = channel.url.substringAfterLast("/")
                    setTextColor(textSecondary)
                    textSize = 11.5f
                    maxLines = 1
                }
                nameCol.addView(nameView)
                nameCol.addView(handleView)
                row.addView(nameCol)

                val upBtn = Button(context).apply {
                    text = "▲"
                    setTextColor(if (i > 0) primaryColor else textSecondary)
                    isEnabled = i > 0
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(dp(context, 44), dp(context, 38))
                    setOnClickListener {
                        if (i > 0) {
                            val temp = channels[i]
                            channels[i] = channels[i - 1]
                            channels[i - 1] = temp
                            YoutubePreferences.saveSubscribedChannels(channels)
                            rebuildList()
                        }
                    }
                }
                val downBtn = Button(context).apply {
                    text = "▼"
                    setTextColor(if (i < channels.size - 1) primaryColor else textSecondary)
                    isEnabled = i < channels.size - 1
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(dp(context, 44), dp(context, 38)).apply {
                        marginStart = dp(context, 4)
                    }
                    setOnClickListener {
                        if (i < channels.size - 1) {
                            val temp = channels[i]
                            channels[i] = channels[i + 1]
                            channels[i + 1] = temp
                            YoutubePreferences.saveSubscribedChannels(channels)
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
}
