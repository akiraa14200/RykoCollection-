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
        const val EXTRA_THUMB = "tube_video_thumb"
        const val EXTRA_AVATAR = "tube_video_avatar"
        const val EXTRA_RECOMMEND_IDS = "tube_recommend_ids"
        const val EXTRA_RECOMMEND_TITLES = "tube_recommend_titles"
        const val EXTRA_RECOMMEND_CREATORS = "tube_recommend_creators"
        const val EXTRA_RECOMMEND_THUMBS = "tube_recommend_thumbs"
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
    private var currentThumb = ""
    private var currentAvatar = ""
    private var recommendIds = arrayListOf<String>()
    private var recommendTitles = arrayListOf<String>()
    private var recommendCreators = arrayListOf<String>()
    private var recommendThumbs = arrayListOf<String>()
    private var likeAction: TextView? = null
    private var dislikeAction: TextView? = null
    private var subscribeAction: TextView? = null
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
        currentThumb = intent.getStringExtra(EXTRA_THUMB).orEmpty()
        currentAvatar = intent.getStringExtra(EXTRA_AVATAR).orEmpty()
        recommendIds = intent.getStringArrayListExtra(EXTRA_RECOMMEND_IDS) ?: arrayListOf()
        recommendTitles = intent.getStringArrayListExtra(EXTRA_RECOMMEND_TITLES) ?: arrayListOf()
        recommendCreators = intent.getStringArrayListExtra(EXTRA_RECOMMEND_CREATORS) ?: arrayListOf()
        recommendThumbs = intent.getStringArrayListExtra(EXTRA_RECOMMEND_THUMBS) ?: arrayListOf()
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
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(8, 8, 8))
        }
        details.addView(TextView(this).apply { text = currentTitle.ifBlank { "YouTube video" }; setTextColor(Color.WHITE); setTextSize(21f); setTypeface(null, android.graphics.Typeface.BOLD); maxLines = 3 })
        details.addView(TextView(this).apply { text = "@" + currentCreator.ifBlank { "RykoCollection" } + "   •   YouTube"; setTextColor(Color.rgb(208,190,220)); setTextSize(13f); setPadding(0,dp(8),0,dp(14)) })
        val channelRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val avatarView = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.DKGRAY) }; clipToOutline = true }
        channelRow.addView(avatarView, LinearLayout.LayoutParams(dp(48), dp(48)))
        if (currentAvatar.isNotBlank()) loadImage(currentAvatar, avatarView)
        channelRow.addView(TextView(this).apply { text = currentCreator.ifBlank { "RykoCollection" }; setTextColor(Color.WHITE); textSize = 14f; setTypeface(null,android.graphics.Typeface.BOLD); setPadding(dp(10),0,dp(8),0) }, LinearLayout.LayoutParams(0,dp(48),1f))
        subscribeAction = actionLabel("Subscribe",13f).apply { background = android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(24).toFloat(); setColor(Color.WHITE) }; setTextColor(Color.BLACK); setOnClickListener { val prefs=getSharedPreferences(PREFS,MODE_PRIVATE); val k="sub_"+currentCreator; val next=!prefs.getBoolean(k,false); prefs.edit().putBoolean(k,next).apply(); text=if(next) "Subscribed" else "Subscribe" } }
        channelRow.addView(subscribeAction,LinearLayout.LayoutParams(dp(118),dp(42))); details.addView(channelRow)
        val actions = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(12),0,dp(10)) }
        likeAction = actionLabel("♡  Like",14f).apply { setOnClickListener { val prefs=getSharedPreferences(PREFS,MODE_PRIVATE); val next=!prefs.getBoolean("like_"+currentVideoId,false); prefs.edit().putBoolean("like_"+currentVideoId,next).apply(); text=if(next) "♥  Liked" else "♡  Like"; dislikeAction?.text="♧  Dislike" }; text=if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("like_"+currentVideoId,false)) "♥  Liked" else "♡  Like" }
        dislikeAction = actionLabel("♧  Dislike",14f).apply { setOnClickListener { val next=!getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("dislike_"+currentVideoId,false); getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("dislike_"+currentVideoId,next).apply(); text=if(next) "Disliked" else "♧  Dislike"; likeAction?.text="♡  Like" } }
        actions.addView(likeAction,LinearLayout.LayoutParams(0,dp(46),1f)); actions.addView(dislikeAction,LinearLayout.LayoutParams(0,dp(46),1f))
        actions.addView(actionLabel("↗",23f).apply { setOnClickListener { shareVideo() } },LinearLayout.LayoutParams(dp(48),dp(46)))
        saveAction=actionLabel(if(isSaved()) "▣" else "♧",22f).apply { setOnClickListener { toggleSaved(); text=if(isSaved()) "▣" else "♧" } }; actions.addView(saveAction,LinearLayout.LayoutParams(dp(48),dp(46)))
        actions.addView(actionLabel("⋮",23f).apply { setOnClickListener { showMoreMenu(it) } },LinearLayout.LayoutParams(dp(42),dp(46))); details.addView(actions)
        details.addView(TextView(this).apply { text = "Up next"; setTextColor(Color.WHITE); textSize = 19f; setTypeface(null, android.graphics.Typeface.BOLD); setPadding(0, dp(18), 0, dp(8)) })
        recommendIds.indices.forEach { index ->
            val item = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(7), 0, dp(7)) }
            val thumb = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP; background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(9).toFloat(); setColor(Color.DKGRAY) }; clipToOutline = true }
            item.addView(thumb, LinearLayout.LayoutParams(dp(144), dp(82)))
            recommendThumbs.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { loadImage(it, thumb) }
            val labels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            labels.addView(TextView(this).apply { text = recommendTitles.getOrNull(index).orEmpty().ifBlank { "Anime video" }; setTextColor(Color.WHITE); textSize = 14f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            labels.addView(TextView(this).apply { text = recommendCreators.getOrNull(index).orEmpty(); setTextColor(Color.LTGRAY); textSize = 12f; setPadding(0, dp(5), 0, 0) })
            item.addView(labels, LinearLayout.LayoutParams(0, dp(82), 1f))
            item.setOnClickListener { val next = Intent(this, TubePlayerActivity::class.java).apply { putExtra(EXTRA_VIDEO_ID, recommendIds[index]); putExtra(EXTRA_TITLE, recommendTitles.getOrNull(index).orEmpty()); putExtra(EXTRA_CREATOR, recommendCreators.getOrNull(index).orEmpty()); putExtra(EXTRA_THUMB, recommendThumbs.getOrNull(index).orEmpty()); putStringArrayListExtra(EXTRA_RECOMMEND_IDS, ArrayList(recommendIds)); putStringArrayListExtra(EXTRA_RECOMMEND_TITLES, ArrayList(recommendTitles)); putStringArrayListExtra(EXTRA_RECOMMEND_CREATORS, ArrayList(recommendCreators)); putStringArrayListExtra(EXTRA_RECOMMEND_THUMBS, ArrayList(recommendThumbs)) }; startActivity(next); finish() }
            details.addView(item)
        }
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
                html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}.stack{position:absolute;inset:0;overflow:hidden;background:#000}.ambient{position:absolute;width:132%;height:132%;left:-16%;top:-16%;border:0;filter:blur(38px) saturate(1.8) brightness(1.35);opacity:.72;transform:scale(1.08);pointer-events:none;z-index:1}.main{position:absolute;inset:0;width:100%;height:100%;border:0;z-index:2;background:#000}
              </style>
            </head><body>
              <div class="stack">
                <iframe class="ambient" src="https://www.youtube.com/embed/$currentVideoId?autoplay=1&mute=1&controls=0&disablekb=1&playsinline=1&rel=0&origin=$appOrigin&enablejsapi=1" title="Ambient player" tabindex="-1" aria-hidden="true" referrerpolicy="origin" allow="autoplay; encrypted-media"></iframe>
                <iframe class="main" src="https://www.youtube.com/embed/$currentVideoId?autoplay=1&playsinline=${if (isShort) 0 else 1}&controls=1&rel=0&origin=$appOrigin&enablejsapi=1" title="YouTube video player" referrerpolicy="origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
              </div>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("$appOrigin/", html, "text/html", "UTF-8", null)
    }

    private fun loadImage(url: String, target: android.widget.ImageView) {
        Thread {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "RykoCollection Android")
                val bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                connection.disconnect()
                if (bitmap != null) runOnUiThread { if (!isFinishing) target.setImageBitmap(bitmap) }
            } catch (error: Exception) { Log.w(TAG, "Image load failed", error) }
        }.start()
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
