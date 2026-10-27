package com.youtube.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import com.youtube.YoutubePreferences
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

object SettingsTheme {
    val COLOR_SHEET_BG = Color.parseColor("#101115")
    val COLOR_CARD_BG = Color.parseColor("#181A23")
    val COLOR_CARD_BORDER = Color.parseColor("#262836")
    val COLOR_INNER_BG = Color.parseColor("#13141C")
    val COLOR_DIVIDER = Color.parseColor("#222432")

    val COLOR_PRIMARY = Color.parseColor("#3D50FA") // CloudStream Indigo
    val COLOR_PRIMARY_LIGHT = Color.parseColor("#8C9EFF")
    val COLOR_PRIMARY_TINT = Color.parseColor("#1E2448")

    val COLOR_TEXT_PRIMARY = Color.parseColor("#F1F2F6")
    val COLOR_TEXT_SECONDARY = Color.parseColor("#9296A6")
    val COLOR_TEXT_MUTED = Color.parseColor("#646878")

    val COLOR_YOUTUBE_RED = Color.parseColor("#E50914")
    val COLOR_DANGER_BG = Color.parseColor("#2A161B")
    val COLOR_DANGER_TEXT = Color.parseColor("#FF5252")

    val COLOR_RIPPLE = Color.parseColor("#33FFFFFF")

    // In-memory avatar bitmap cache to avoid redownloading channel pictures
    val avatarCache = mutableMapOf<String, Bitmap>()

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    fun createRoundedDrawable(
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

    fun createRippleDrawable(
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

    fun createSwitchThumbTint(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(COLOR_PRIMARY, Color.parseColor("#7A7E92"))
        )
    }

    fun createSwitchTrackTint(): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(Color.parseColor("#252E63"), Color.parseColor("#252735"))
        )
    }

    fun createInitialsGradient(name: String): GradientDrawable {
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

    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
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

    fun loadAvatarInto(url: String, imageView: ImageView, channelUrl: String? = null) {
        if (url.isBlank()) {
            if (!channelUrl.isNullOrBlank()) {
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
                        Log.w("SettingsTheme", "Failed to resolve avatar for $channelUrl: ${e.message}")
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
                Log.w("SettingsTheme", "Failed to fetch avatar from $url: ${e.message}")
            }
        }
    }
}
