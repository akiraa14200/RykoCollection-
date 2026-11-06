package com.rykocollection.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.util.Log
import android.util.TypedValue

/** Standalone YouTube iframe player with a fixed YouTube-standard 16:9 viewing area. */
class TubePlayerActivity : Activity() {
    companion object {
        const val EXTRA_VIDEO_ID = "tube_video_id"
        const val EXTRA_TITLE = "tube_video_title"
        const val EXTRA_CREATOR = "tube_video_creator"
        const val EXTRA_IS_SHORT = "tube_video_is_short"
        private const val TAG = "RykoTubePlayer"
        private const val PREFS = "ryko_tube_saved"
    }

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUi = 0
    private var currentVideoId = ""
    private var currentTitle = ""
    private var currentCreator = ""
    private var isShort = false
    private var saveAction: TextView? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun actionLabel(text: String, size: Float = 14f): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        originalSystemUi = window.decorView.systemUiVisibility
        currentVideoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        currentTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        currentCreator = intent.getStringExtra(EXTRA_CREATOR).orEmpty()
        isShort = intent.getBooleanExtra(EXTRA_IS_SHORT, false)
        if (currentVideoId.isBlank()) {
            Toast.makeText(this, "This video link is not a supported YouTube URL.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        if (isShort) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        val toolbar = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(8), 0)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        toolbar.addView(actionLabel("‹", 30f).apply {
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(actionLabel("RykoTube", 17f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(actionLabel("⋮", 25f).apply {
            setOnClickListener { showMoreMenu(it) }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        if (!isShort) page.addView(toolbar)

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "Page loaded: $url")
                }
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        Log.e(TAG, "WebView main-frame error: ${error?.description}")
                        Toast.makeText(this@TubePlayerActivity, "Player page failed to load. Check your connection.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    Log.d(TAG, "JS: ${message.message()} line ${message.lineNumber()}")
                    return true
                }
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    root.addView(view, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
                    ))
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
                }
                override fun onHideCustomView() {
                    fullscreenView?.let { root.removeView(it) }
                    fullscreenView = null
                    fullscreenCallback = null
                    window.decorView.systemUiVisibility = originalSystemUi
                }
            }
        }

        // The embedded video stays 16:9 instead of stretching to fill a tall phone screen.
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val playerHeightDp = (screenWidthDp * 9f / 16f).toInt()
        if (!isShort) {
            page.addView(webView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(playerHeightDp)
            ))
        }

        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        details.addView(TextView(this).apply {
            text = currentTitle.ifBlank { "YouTube video" }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 3
        })
        details.addView(TextView(this).apply {
            text = currentCreator
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(7), 0, dp(8))
        })
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        saveAction = actionLabel(if (isSaved()) "✓ Saved" else "＋ Save", 13f).apply {
            setOnClickListener { toggleSaved() }
        }
        actions.addView(saveAction, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(actionLabel("↗ Share", 13f).apply {
            setOnClickListener { shareVideo() }
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        actions.addView(actionLabel("⚑ Report", 13f).apply {
            setOnClickListener { reportVideo() }
        }, LinearLayout.LayoutParams(0, dp(46), 1f))
        details.addView(actions)
        if (!isShort) page.addView(details)

        if (isShort) {
            root.addView(webView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        } else {
            val scroll = ScrollView(this).apply { isFillViewport = true }
            scroll.addView(page)
            root.addView(scroll, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        setContentView(root)

        val appOrigin = "https://rykocollection.vercel.app"
        val html = """
            <!doctype html><html><head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <meta name="referrer" content="strict-origin-when-cross-origin">
              <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}
                iframe{display:block;border:0;width:100%;height:100%;${isShort ? "aspect-ratio:auto" : "aspect-ratio:16/9"}}
              </style>
            </head><body>
              <iframe src="https://www.youtube.com/embed/$currentVideoId?autoplay=1&playsinline=${if (isShort) 0 else 1}&rel=0&origin=$appOrigin"
                title="YouTube video player" referrerpolicy="strict-origin-when-cross-origin"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("$appOrigin/", html, "text/html", "UTF-8", null)
    }

    private fun isSaved(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet("ids", emptySet())?.contains(currentVideoId) == true

    private fun toggleSaved() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val ids = prefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!ids.add(currentVideoId)) ids.remove(currentVideoId)
        prefs.edit().putStringSet("ids", ids).apply()
        saveAction?.text = if (ids.contains(currentVideoId)) "✓ Saved" else "＋ Save"
        Toast.makeText(this, if (ids.contains(currentVideoId)) "Saved to your RykoTube list" else "Removed from saved videos", Toast.LENGTH_SHORT).show()
    }

    private fun shareVideo() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, currentTitle)
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$currentVideoId")
        }
        startActivity(Intent.createChooser(send, "Share video"))
    }

    private fun reportVideo() {
        val send = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:rykocollection@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "RykoTube video report: $currentTitle")
            putExtra(Intent.EXTRA_TEXT, "Please review this video in RykoTube:\nhttps://www.youtube.com/watch?v=$currentVideoId\n\nReason for report:\n")
        }
        try {
            startActivity(send)
        } catch (_: Exception) {
            Toast.makeText(this, "No email app is available to send a report.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Save to RykoTube").setOnMenuItemClickListener { toggleSaved(); true }
            menu.add("Share").setOnMenuItemClickListener { shareVideo(); true }
            menu.add("Report").setOnMenuItemClickListener { reportVideo(); true }
            menu.add("Open in YouTube").setOnMenuItemClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$currentVideoId")))
                true
            }
            menu.add("Download").setOnMenuItemClickListener {
                Toast.makeText(this@TubePlayerActivity, "Use YouTube's official Download option when available for this video.", Toast.LENGTH_LONG).show()
                true
            }
            show()
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (fullscreenView != null) {
            fullscreenCallback?.onCustomViewHidden()
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
