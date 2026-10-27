package com.youtube.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.youtube.YoutubePreferences
import org.schabi.newpipe.extractor.ServiceList
import kotlin.concurrent.thread

object SettingsTheme {
    private const val TAG = "SettingsTheme"
    val avatarCache = mutableMapOf<String, Bitmap>()

    fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    fun getAttrColor(context: Context, attrId: Int, fallback: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attrId, tv, true)) {
            if (tv.resourceId != 0) {
                try {
                    ContextCompat.getColor(context, tv.resourceId)
                } catch (_: Throwable) {
                    tv.data
                }
            } else {
                tv.data
            }
        } else {
            fallback
        }
    }

    fun getThemeNamedColor(context: Context, attrName: String, fallback: Int): Int {
        val id = context.resources.getIdentifier(attrName, "attr", context.packageName)
        return if (id != 0) getAttrColor(context, id, fallback) else fallback
    }

    fun getTextPrimaryColor(context: Context): Int {
        val themeColor = getThemeNamedColor(context, "textColor", 0)
        if (themeColor != 0) return themeColor
        return getAttrColor(context, android.R.attr.textColorPrimary, Color.WHITE)
    }

    fun getTextSecondaryColor(context: Context): Int {
        val themeColor = getThemeNamedColor(context, "grayTextColor", 0)
        if (themeColor != 0) return themeColor
        return getAttrColor(context, android.R.attr.textColorSecondary, Color.LTGRAY)
    }

    fun getPrimaryColor(context: Context): Int {
        val themeColor = getThemeNamedColor(context, "colorPrimary", 0)
        if (themeColor != 0) return themeColor
        return getAttrColor(context, androidx.appcompat.R.attr.colorPrimary, Color.parseColor("#3D50FA"))
    }

    fun getBackgroundColor(context: Context): Int {
        val themeColor = getThemeNamedColor(context, "primaryBlackBackground", 0)
        if (themeColor != 0) return themeColor
        return getAttrColor(context, android.R.attr.colorBackground, Color.parseColor("#121212"))
    }

    fun getDividerColor(context: Context): Int {
        val themeColor = getThemeNamedColor(context, "primaryGrayBackground", 0)
        if (themeColor != 0) return (themeColor and 0x00FFFFFF) or 0x33000000
        val secondary = getTextSecondaryColor(context)
        return (secondary and 0x00FFFFFF) or 0x26000000 // 15% opacity of secondary text
    }

    fun getSelectableItemBackground(context: Context): Drawable? {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) {
            return ContextCompat.getDrawable(context, tv.resourceId)
        }
        return null
    }

    fun createSwitchThumbTint(context: Context): ColorStateList {
        val primary = getPrimaryColor(context)
        val muted = getTextSecondaryColor(context)
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primary, muted)
        )
    }

    fun createSwitchTrackTint(context: Context): ColorStateList {
        val primary = getPrimaryColor(context)
        val primaryTransparent = (primary and 0x00FFFFFF) or 0x55000000
        val muted = getTextSecondaryColor(context)
        val mutedTransparent = (muted and 0x00FFFFFF) or 0x33000000
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(primaryTransparent, mutedTransparent)
        )
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
                        Log.w(TAG, "Failed to resolve avatar for $channelUrl: ${e.message}")
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
                Log.w(TAG, "Failed to fetch avatar from $url: ${e.message}")
            }
        }
    }
}
