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
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator

/** Standalone YouTube iframe player with a fixed YouTube-standard 16:9 viewing area. */
class TubePlayerActivity : Activity() {
    companion object {
        const val EXTRA_VIDEO_ID = "tube_video_id"
        const val EXTRA_TITLE = "tube_video_title"
        const val EXTRA_CREATOR = "tube_video_creator"
        const val EXTRA_IS_SHORT = "tube_video_is_short"
        const val EXTRA_SHORT_QUEUE = "tube_short_queue"
        const val EXTRA_SHORT_TITLES = "tube_short_titles"
        const val EXTRA_SHORT_CREATORS = "tube_short_creators"
        const val EXTRA_SHORT_INDEX = "tube_short_index"
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
    private var shortQueue = arrayListOf<String>()
    private var shortTitles = arrayListOf<String>()
    private var shortCreators = arrayListOf<String>()
    private var shortIndex = 0
    private var touchStartY = 0f
    private var touchStartX = 0f
    private var touchStartAt = 0L
    private var swipeHandled = false
    private var shortTransitionGeneration = 0
    private val playerOrigin = "https://rykocollection.vercel.app"
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
        shortQueue = intent.getStringArrayListExtra(EXTRA_SHORT_QUEUE) ?: arrayListOf()
        shortTitles = intent.getStringArrayListExtra(EXTRA_SHORT_TITLES) ?: arrayListOf()
        shortCreators = intent.getStringArrayListExtra(EXTRA_SHORT_CREATORS) ?: arrayListOf()
        shortIndex = intent.getIntExtra(EXTRA_SHORT_INDEX, 0).coerceIn(0, (shortQueue.size - 1).coerceAtLeast(0))
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
                    if (isShort && view === webView && swipeHandled) {
                        // The new iframe document is ready: bring the incoming Short into place.
                        view?.animate()
                            ?.translationY(0f)
                            ?.alpha(1f)
                            ?.scaleX(1f)
                            ?.scaleY(1f)
                            ?.setDuration(360)
                            ?.setInterpolator(OvershootInterpolator(0.72f))
                            ?.withEndAction {
                                view.translationY = 0f
                                view.alpha = 1f
                                view.scaleX = 1f
                                view.scaleY = 1f
                                swipeHandled = false
                            }
                            ?.start()
                    }
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
              <meta name="referrer" content="origin">
              <style>
                *{box-sizing:border-box}
                html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}
                iframe{display:block;border:0;width:100%;height:100%;${if (isShort) "aspect-ratio:auto" else "aspect-ratio:16/9"}}
              </style>
            </head><body>
              <iframe src="https://www.youtube.com/embed/$currentVideoId?autoplay=1&playsinline=${if (isShort) 0 else 1}&rel=0&origin=$appOrigin&enablejsapi=1"
                title="YouTube video player" referrerpolicy="origin"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("$appOrigin/", html, "text/html", "UTF-8", null)
    }

    private fun loadShortDocument() {
        val html = """
            <!doctype html><html><head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
              <meta name="referrer" content="origin">
              <style>*{box-sizing:border-box}html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}iframe{display:block;border:0;width:100%;height:100%}</style>
            </head><body>
              <iframe src="https://www.youtube.com/embed/$currentVideoId?autoplay=1&playsinline=0&rel=0&origin=$playerOrigin&enablejsapi=1"
                title="YouTube Shorts player" referrerpolicy="origin"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen></iframe>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("$playerOrigin/", html, "text/html", "UTF-8", null)
    }

    private fun moveShort(direction: Int) {
        if (shortQueue.isEmpty() || swipeHandled) return
        val next = shortIndex + direction
        if (next !in shortQueue.indices) {
            // Give the end of the current Shorts queue a small elastic boundary response.
            webView.animate().cancel()
            webView.animate()
                .translationY(if (direction > 0) -dp(28).toFloat() else dp(28).toFloat())
                .setDuration(110)
                .withEndAction {
                    webView.animate().translationY(0f).setDuration(190)
                        .setInterpolator(OvershootInterpolator(1.4f)).start()
                }
                .start()
            return
        }

        swipeHandled = true
        val generation = ++shortTransitionGeneration
        shortIndex = next
        currentVideoId = shortQueue[next]
        if (next < shortTitles.size) currentTitle = shortTitles[next]
        if (next < shortCreators.size) currentCreator = shortCreators[next]

        // Swipe up: the current Short exits upward and the next one enters from below.
        // Swipe down: reverse the direction to reveal the previous Short.
        val height = (root.height.takeIf { it > 0 } ?: heightPx()).toFloat()
        val exitY = if (direction > 0) -height * 0.82f else height * 0.82f
        val enterY = -exitY

        webView.animate().cancel()
        webView.animate()
            .translationY(exitY)
            .alpha(0.35f)
            .scaleX(0.965f)
            .scaleY(0.965f)
            .setDuration(190)
            .setInterpolator(AccelerateInterpolator(1.35f))
            .withEndAction {
                if (generation != shortTransitionGeneration || isFinishing) return@withEndAction
                webView.translationY = enterY
                webView.alpha = 0.72f
                webView.scaleX = 0.965f
                webView.scaleY = 0.965f
                loadShortDocument()
                // Safety unlock in case WebView never reports onPageFinished.
                webView.postDelayed({
                    if (generation == shortTransitionGeneration) {
                        webView.animate()
                            .translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(360)
                            .setInterpolator(OvershootInterpolator(0.72f))
                            .withEndAction { swipeHandled = false }
                            .start()
                    }
                }, 1800)
            }
            .start()
    }

    private fun heightPx(): Int = resources.displayMetrics.heightPixels

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (isShort && shortQueue.size > 1) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    touchStartX = event.rawX
                    touchStartAt = android.os.SystemClock.elapsedRealtime()
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dy = event.rawY - touchStartY
                    val dx = event.rawX - touchStartX
                    val elapsed = (android.os.SystemClock.elapsedRealtime() - touchStartAt).coerceAtLeast(1L)
                    if (kotlin.math.abs(dy) > dp(110) &&
                        kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f &&
                        elapsed < 900L
                    ) {
                        moveShort(if (dy < 0) 1 else -1)
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
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
