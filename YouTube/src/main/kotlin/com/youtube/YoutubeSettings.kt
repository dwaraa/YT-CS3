package com.youtube

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

object YoutubeSettings {

    fun show(context: Context) {
        val options = arrayOf(
            "➕ Add Subscribed Channel",
            "📋 Manage Subscribed Channels (${YoutubePreferences.getSubscribedChannels().size})",
            "🎛️ Customize Home Screen Rows",
            "🔄 Reset to Defaults"
        )

        AlertDialog.Builder(context)
            .setTitle("YouTube Plugin Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddChannelDialog(context)
                    1 -> showManageChannelsDialog(context)
                    2 -> showCustomizeRowsDialog(context)
                    3 -> showResetConfirmDialog(context)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddChannelDialog(context: Context) {
        val input = EditText(context).apply {
            hint = "e.g. @MrBeast or https://youtube.com/@mkbhd"
            setSingleLine(true)
        }

        AlertDialog.Builder(context)
            .setTitle("Add Subscribed Channel")
            .setMessage("Enter YouTube channel handle, URL, or ID:")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotBlank()) {
                    resolveAndAddChannel(context, text)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resolveAndAddChannel(context: Context, rawInput: String) {
        Toast.makeText(context, "Resolving channel...", Toast.LENGTH_SHORT).show()

        val normalizedUrl = when {
            rawInput.startsWith("http") -> rawInput
            rawInput.startsWith("@") -> "https://www.youtube.com/$rawInput"
            rawInput.startsWith("UC") -> "https://www.youtube.com/channel/$rawInput"
            else -> "https://www.youtube.com/@$rawInput"
        }

        CoroutineScope(Dispatchers.IO).launch {
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

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Added '$channelName' to subscriptions!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showManageChannelsDialog(context: Context) {
        val channels = YoutubePreferences.getSubscribedChannels()
        if (channels.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("Subscribed Channels")
                .setMessage("No subscribed channels found. Tap 'Add Subscribed Channel' to add one.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val items = channels.map { "🗑️ ${it.name} (${it.url.substringAfterLast("/")})" }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Subscribed Channels (Tap to Remove)")
            .setItems(items) { _, which ->
                val target = channels[which]
                AlertDialog.Builder(context)
                    .setTitle("Remove Channel")
                    .setMessage("Remove '${target.name}' from subscribed channels?")
                    .setPositiveButton("Remove") { _, _ ->
                        YoutubePreferences.removeChannel(target.url)
                        Toast.makeText(context, "Removed '${target.name}'", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showCustomizeRowsDialog(context: Context) {
        val rowTitles = arrayOf(
            "Subscribed: Combined Feed",
            "Subscribed: Separate Channel Rows",
            "Trending",
            "Movies & Shows",
            "Music",
            "Gaming",
            "Podcasts",
            "Live Streams"
        )

        val checkedStates = booleanArrayOf(
            YoutubePreferences.showCombinedSubscriptions,
            YoutubePreferences.showIndividualChannelRows,
            YoutubePreferences.showTrending,
            YoutubePreferences.showMovies,
            YoutubePreferences.showMusic,
            YoutubePreferences.showGaming,
            YoutubePreferences.showPodcasts,
            YoutubePreferences.showLive
        )

        AlertDialog.Builder(context)
            .setTitle("Home Screen Rows")
            .setMultiChoiceItems(rowTitles, checkedStates) { _, which, isChecked ->
                when (which) {
                    0 -> YoutubePreferences.showCombinedSubscriptions = isChecked
                    1 -> YoutubePreferences.showIndividualChannelRows = isChecked
                    2 -> YoutubePreferences.showTrending = isChecked
                    3 -> YoutubePreferences.showMovies = isChecked
                    4 -> YoutubePreferences.showMusic = isChecked
                    5 -> YoutubePreferences.showGaming = isChecked
                    6 -> YoutubePreferences.showPodcasts = isChecked
                    7 -> YoutubePreferences.showLive = isChecked
                }
            }
            .setPositiveButton("Save") { _, _ ->
                Toast.makeText(context, "Home screen preferences saved! Refresh home to apply.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetConfirmDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Reset Settings")
            .setMessage("Reset all subscribed channels and row preferences to default?")
            .setPositiveButton("Reset") { _, _ ->
                YoutubePreferences.resetToDefaults()
                Toast.makeText(context, "Reset to defaults.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
