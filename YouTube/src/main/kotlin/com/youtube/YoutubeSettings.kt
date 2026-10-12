package com.youtube

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * Hybrid Web & Native Settings UI for YouTube Provider:
 * - Hosts an embedded HTML5/CSS3/JS Web Dashboard in an in-app WebView
 * - Launches an embedded HTTP Web Server for remote access (Phone, PC, TV)
 * - Retains full native fallback UI if WebView is unavailable on certain Android builds
 */
object YoutubeSettings {

    private val COLOR_SHEET_BG = Color.parseColor("#0B0C10")
    private val COLOR_CARD_BG = Color.parseColor("#181A23")
    private val COLOR_CARD_BORDER = Color.parseColor("#262836")
    private val COLOR_PRIMARY = Color.parseColor("#3D50FA")
    private val COLOR_PRIMARY_LIGHT = Color.parseColor("#8C9EFF")
    private val COLOR_PRIMARY_TINT = Color.parseColor("#1E2448")
    private val COLOR_TEXT_PRIMARY = Color.parseColor("#F1F2F6")
    private val COLOR_TEXT_SECONDARY = Color.parseColor("#9296A6")
    private val COLOR_TEXT_MUTED = Color.parseColor("#646878")
    private val COLOR_YOUTUBE_RED = Color.parseColor("#E50914")
    private val COLOR_RIPPLE = Color.parseColor("#33FFFFFF")

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

    /**
     * Primary entry point: Shows the Web-based Settings UI in an in-app WebView dialog,
     * while also displaying the remote Wi-Fi URL for external browser access.
     */
    fun show(context: Context) {
        // Ensure background web server is active
        YoutubeWebServer.start(context)

        try {
            showWebSettingsDialog(context)
        } catch (_: Throwable) {
            // Safe fallback to native settings if WebView initialization fails on this device
            showNativeSettings(context)
        }
    }

    private fun showWebSettingsDialog(context: Context) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(COLOR_SHEET_BG)
                cornerRadii = floatArrayOf(
                    dp(context, 24).toFloat(), dp(context, 24).toFloat(),
                    dp(context, 24).toFloat(), dp(context, 24).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(0, dp(context, 8), 0, 0)
        }

        // Top drag pill handle
        val pill = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 2)
                bottomMargin = dp(context, 10)
            }
        }
        root.addView(pill)

        // Top Header Control Bar
        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // YouTube Play Glyph Box
        val logoBox = FrameLayout(context).apply {
            background = createRoundedDrawable(COLOR_YOUTUBE_RED, 10f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)).apply {
                marginEnd = dp(context, 10)
            }
        }
        val logoGlyph = TextView(context).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        logoBox.addView(logoGlyph)
        headerBar.addView(logoBox)

        // Title Column
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = "YouTube Settings"
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        val remoteUrl = YoutubeWebServer.getServerUrl()
        val subtitle = TextView(context).apply {
            text = "Web Dashboard • $remoteUrl"
            setTextColor(COLOR_PRIMARY_LIGHT)
            textSize = 11f
        }
        titleCol.addView(title)
        titleCol.addView(subtitle)
        headerBar.addView(titleCol)

        // "Open in Browser" button
        val browserBtn = TextView(context).apply {
            text = "🌐 Browser"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            background = createRippleDrawable(context, COLOR_PRIMARY, COLOR_RIPPLE, 10f)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(context, 6) }
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(remoteUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "URL: $remoteUrl", Toast.LENGTH_LONG).show()
                }
            }
        }
        headerBar.addView(browserBtn)

        // "Copy IP" button
        val copyBtn = TextView(context).apply {
            text = "📋"
            setTextColor(COLOR_TEXT_PRIMARY)
            textSize = 13f
            gravity = Gravity.CENTER
            background = createRippleDrawable(context, Color.parseColor("#20222E"), COLOR_RIPPLE, 10f, COLOR_CARD_BORDER, 1f)
            layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)).apply {
                marginEnd = dp(context, 6)
            }
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("YouTube Settings URL", remoteUrl))
                Toast.makeText(context, "Copied remote settings URL to clipboard!", Toast.LENGTH_SHORT).show()
            }
        }
        headerBar.addView(copyBtn)

        // Close Button
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

        // WebView Container
        val displayHeight = context.resources.displayMetrics.heightPixels
        val webViewHeight = (displayHeight * 0.82).toInt()

        val webView = WebView(context).apply {
            setBackgroundColor(COLOR_SHEET_BG)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                webViewHeight
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = true
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                }
            }
        }

        // Load the single-page HTML directly with base URL pointing to the local server
        val htmlContent = YoutubeWebPage.getHtml(YoutubeWebServer.getLocalIpAddress(), YoutubeWebServer.activePort)
        val baseUrl = "http://127.0.0.1:${YoutubeWebServer.activePort}/"
        webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)

        root.addView(webView)
        dialog.setContentView(root)

        // Window bottom sheet presentation
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.65f)
            setWindowAnimations(android.R.style.Animation_InputMethod)
        }
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            try {
                webView.destroy()
            } catch (_: Exception) {}
        }
        dialog.show()
    }

    /**
     * Fallback native UI in case WebView cannot be created on certain minimal Android/TV devices.
     */
    fun showNativeSettings(context: Context) {
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

        val pill = View(context).apply {
            background = createRoundedDrawable(Color.parseColor("#3A3D4E"), 3f, 0, 0f, context)
            layoutParams = LinearLayout.LayoutParams(dp(context, 38), dp(context, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 14)
            }
        }
        root.addView(pill)

        val headerBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(context, 14) }
        }

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

        // Maintenance & Reset
        val actionsCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedDrawable(COLOR_CARD_BG, 18f, COLOR_CARD_BORDER, 1f, context)
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
        }
        val resetBtn = Button(context).apply {
            text = "Reset Settings to Default"
            setTextColor(Color.WHITE)
            background = createRippleDrawable(context, COLOR_YOUTUBE_RED, COLOR_RIPPLE, 12f)
            setOnClickListener {
                YoutubePreferences.resetToDefaults()
                YoutubeProvider.clearFeedCache()
                Toast.makeText(context, "Settings reset to defaults!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        actionsCard.addView(resetBtn)
        content.addView(actionsCard)

        scrollView.addView(content)
        root.addView(scrollView)
        dialog.setContentView(root)

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
}
