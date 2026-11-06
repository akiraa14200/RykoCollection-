package com.rykocollection.app.ui

import com.rykocollection.app.R

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
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
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
    private var likeAction: ImageView? = null
    private var dislikeAction: ImageView? = null
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
    private var saveAction: ImageView? = null
    private var ambientView: LiquidAmbientView? = null
    @Volatile private var ambientPlaybackActive = false

    private fun iconAction(resource: Int, description: String): ImageView =
        ImageView(this).apply {
            setImageResource(resource)
            contentDescription = description
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            val attrs = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
            val ta = obtainStyledAttributes(attrs)
            background = ta.getDrawable(0)
            ta.recycle()
        }
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

        root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            if (!isShort) {
                val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
                val statusInset = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else dp(24)
                setPadding(0, statusInset, 0, 0)
                clipToPadding = false
            }
        }
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
        toolbar.addView(iconAction(R.drawable.ic_arrow_back, "Back").apply {
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        toolbar.addView(actionLabel("RykoTube", 17f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        toolbar.addView(iconAction(R.drawable.ic_more, "More options").apply {
            setOnClickListener { showMoreMenu(it) }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        // Match the reference player: video begins at the very top, without a separate toolbar.

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            addJavascriptInterface(AmbientPlaybackBridge(), "ambientBridge")
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
            ).apply {
                leftMargin = -dp(1)
                rightMargin = -dp(1)
            })
        }

        // Rebuilt watch page to follow the supplied reference: purple rounded header,
        // title + metadata, creator row, engagement controls, comments preview and vertical feed.
        val details = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = false
        }
        val ambientLayer = FrameLayout(this).apply {
            clipChildren = true
            background = android.graphics.drawable.ColorDrawable(Color.BLACK)
        }
        details.addView(ambientLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220), Gravity.TOP
        ))
        startAmbientPreviewCycle(ambientLayer)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), 0)
        }
        content.addView(TextView(this).apply {
            text = currentTitle.ifBlank { "YouTube video" }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        content.addView(TextView(this).apply {
            text = "@${currentCreator.ifBlank { "RykoCollection" }}   •   YouTube   •   more"
            setTextColor(Color.rgb(224, 211, 232))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(7), 0, dp(14))
        })
        val channelRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val avatarView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.DKGRAY)
            }
            clipToOutline = true
        }
        channelRow.addView(avatarView, LinearLayout.LayoutParams(dp(32), dp(32)))
        if (currentAvatar.isNotBlank()) loadImage(currentAvatar, avatarView)
        channelRow.addView(TextView(this).apply {
            text = currentCreator.ifBlank { "RykoCollection" }
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        subscribeAction = actionLabel("Subscribe", 13f).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.WHITE)
            }
            setTextColor(Color.BLACK)
            setPadding(dp(20), 0, dp(20), 0)
            setOnClickListener {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val key = "sub_" + currentCreator
                val next = !prefs.getBoolean(key, false)
                prefs.edit().putBoolean(key, next).apply()
                text = if (next) "Subscribed" else "Subscribe"
            }
        }
        channelRow.addView(subscribeAction, LinearLayout.LayoutParams(dp(112), dp(38)))
        content.addView(channelRow)

        val actions = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        likeAction = iconAction(R.drawable.ic_thumb_up, "Like").apply {
            imageTintList = android.content.res.ColorStateList.valueOf(
                if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("like_" + currentVideoId, false)) Color.rgb(205, 185, 255) else Color.WHITE
            )
            setOnClickListener {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val next = !prefs.getBoolean("like_" + currentVideoId, false)
                prefs.edit().putBoolean("like_" + currentVideoId, next).putBoolean("dislike_" + currentVideoId, false).apply()
                imageTintList = android.content.res.ColorStateList.valueOf(if (next) Color.rgb(205, 185, 255) else Color.WHITE)
                dislikeAction?.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            }
        }
        dislikeAction = iconAction(R.drawable.ic_thumb_down, "Dislike").apply {
            setOnClickListener {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val next = !prefs.getBoolean("dislike_" + currentVideoId, false)
                prefs.edit().putBoolean("dislike_" + currentVideoId, next).putBoolean("like_" + currentVideoId, false).apply()
                imageTintList = android.content.res.ColorStateList.valueOf(if (next) Color.rgb(205, 185, 255) else Color.WHITE)
                likeAction?.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            }
        }
        actions.addView(likeAction, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(dislikeAction, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(iconAction(R.drawable.ic_share, "Share").apply { setOnClickListener { shareVideo() } }, LinearLayout.LayoutParams(0, dp(48), 1f))
        saveAction = iconAction(if (isSaved()) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark, "Save video").apply {
            imageTintList = android.content.res.ColorStateList.valueOf(if (isSaved()) Color.rgb(205, 185, 255) else Color.WHITE)
            setOnClickListener { toggleSaved() }
        }
        actions.addView(saveAction, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(iconAction(R.drawable.ic_more, "More options").apply { setOnClickListener { showMoreMenu(it) } }, LinearLayout.LayoutParams(0, dp(48), 0.8f))
        content.addView(actions)

        val commentsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(15).toFloat()
                setColor(Color.rgb(43, 34, 49))
            }
        }
        val commentsHeading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        commentsHeading.addView(TextView(this).apply {
            text = "Comments"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        commentsHeading.addView(TextView(this).apply {
            text = "   •   Join the conversation"
            setTextColor(Color.rgb(190, 178, 198))
            textSize = 12f
        })
        commentsCard.addView(commentsHeading)
        commentsCard.addView(TextView(this).apply {
            text = "Share your thoughts about this video"
            setTextColor(Color.rgb(225, 218, 231))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(2))
            maxLines = 1
        })
        content.addView(commentsCard)
        details.addView(content, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP
        ))

        // YouTube-style vertical recommendations: large thumbnail first, title and channel below.
        recommendIds.indices.forEach { index ->
            val recommendation = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(18), 0, dp(4))
            }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(28, 28, 28))
            }
            recommendation.addView(thumb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(screenWidthDp * 9 / 16)
            ))
            recommendThumbs.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { loadImage(it, thumb) }
            val metadata = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(9), dp(14), dp(10))
            }
            val smallAvatar = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.DKGRAY)
                }
                clipToOutline = true
            }
            metadata.addView(smallAvatar, LinearLayout.LayoutParams(dp(34), dp(34)))
            // The creator avatar is not supplied by this feed payload; keep a neutral channel badge.
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), 0, dp(8), 0)
            }
            labels.addView(TextView(this).apply {
                text = recommendTitles.getOrNull(index).orEmpty().ifBlank { "Anime video" }
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            labels.addView(TextView(this).apply {
                text = recommendCreators.getOrNull(index).orEmpty() + "   •   YouTube"
                setTextColor(Color.rgb(174, 174, 174))
                textSize = 11f
                setPadding(0, dp(4), 0, 0)
            })
            metadata.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            metadata.addView(iconAction(R.drawable.ic_more, "More video options").apply {
                setOnClickListener { showMoreMenu(it) }
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            recommendation.addView(metadata)
            recommendation.setOnClickListener {
                val next = Intent(this, TubePlayerActivity::class.java).apply {
                    putExtra(EXTRA_VIDEO_ID, recommendIds[index])
                    putExtra(EXTRA_TITLE, recommendTitles.getOrNull(index).orEmpty())
                    putExtra(EXTRA_CREATOR, recommendCreators.getOrNull(index).orEmpty())
                    putExtra(EXTRA_THUMB, recommendThumbs.getOrNull(index).orEmpty())
                    putStringArrayListExtra(EXTRA_RECOMMEND_IDS, ArrayList(recommendIds))
                    putStringArrayListExtra(EXTRA_RECOMMEND_TITLES, ArrayList(recommendTitles))
                    putStringArrayListExtra(EXTRA_RECOMMEND_CREATORS, ArrayList(recommendCreators))
                    putStringArrayListExtra(EXTRA_RECOMMEND_THUMBS, ArrayList(recommendThumbs))
                }
                startActivity(next)
                finish()
            }
            content.addView(recommendation)
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
                html,body{margin:0;padding:0;width:100%;height:100%;overflow:hidden;background:#000}
                .player-wrapper{position:relative;width:100%;height:100%;background:#000;overflow:hidden}
                #main-player{display:block;position:absolute;inset:0;width:100%;height:100%;border:0;background:#000}#short-playback-indicator{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:72px;height:72px;border-radius:50%;background:#0008;color:#fff;display:flex;align-items:center;justify-content:center;font:700 32px Arial;opacity:0;transition:opacity .18s;pointer-events:none}
              </style>
            </head><body>
              <div class="player-wrapper">
                ${if (isShort) '<div id="main-player"></div><div id="short-playback-indicator">Ⅱ</div>' else '<iframe id="main-player" src="https://www.youtube-nocookie.com/embed/$currentVideoId?autoplay=1&playsinline=1&rel=0&origin=$appOrigin" frameborder="0" title="YouTube video player" referrerpolicy="origin" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>'}
              </div>
              <script>
                var rykoPlayer = null;
                var indicatorTimer = null;
                var ambientApiScript = document.createElement('script');
                ambientApiScript.src = 'https://www.youtube.com/iframe_api';
                document.head.appendChild(ambientApiScript);
                function onYouTubeIframeAPIReady() {
                  ${if (isShort) "rykoPlayer = new YT.Player('main-player', { videoId: '$currentVideoId', playerVars: { autoplay: 1, controls: 0, playsinline: 1, rel: 0, fs: 1, disablekb: 1, origin: '$appOrigin' }," else "new YT.Player('main-player', {"}
                    events: { onStateChange: function(event) {
                      if (window.ambientBridge) window.ambientBridge.onPlaybackState(event.data === YT.PlayerState.PLAYING);
                      ${if (isShort) "var indicator = document.getElementById('short-playback-indicator'); if (indicator) indicator.textContent = event.data === YT.PlayerState.PLAYING ? 'Ⅱ' : '▶';" else ""}
                    }}
                  });
                }
                ${if (isShort) "document.addEventListener('click', function() { if (!rykoPlayer) return; var state = rykoPlayer.getPlayerState(); if (state === YT.PlayerState.PLAYING) rykoPlayer.pauseVideo(); else rykoPlayer.playVideo(); var indicator = document.getElementById('short-playback-indicator'); if (indicator) { indicator.style.opacity = '1'; clearTimeout(indicatorTimer); indicatorTimer = setTimeout(function(){ indicator.style.opacity = '0'; }, 650); } });" else ""}
                window.loadRykoShort = function(id) { if (rykoPlayer && id) rykoPlayer.loadVideoById({videoId:id, startSeconds:0}); };
              </script>
            </body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL("$appOrigin/", html, "text/html", "UTF-8", null)
    }


    private inner class AmbientPlaybackBridge {
        @JavascriptInterface
        fun onPlaybackState(playing: Boolean) {
            runOnUiThread {
                ambientPlaybackActive = playing
                if (playing) ambientView?.resumeFlow() else ambientView?.pauseFlow()
            }
        }
    }

    /** Preview stills are sampled for colour only; no source image is ever displayed. */
    private fun startAmbientPreviewCycle(layer: FrameLayout) {
        val glow = LiquidAmbientView()
        ambientView = glow
        layer.addView(glow, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val fade = View(this).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(8, 0, 0, 0), Color.argb(74, 0, 0, 0), Color.BLACK)
            )
            isClickable = false
        }
        layer.addView(fade, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val previewUrls = listOf(
            "https://i.ytimg.com/vi/$currentVideoId/mqdefault.jpg",
            "https://i.ytimg.com/vi/$currentVideoId/mq1.jpg",
            "https://i.ytimg.com/vi/$currentVideoId/mq2.jpg",
            "https://i.ytimg.com/vi/$currentVideoId/mq3.jpg"
        )
        Thread {
            val palette = previewUrls.mapNotNull { url -> extractPrimaryColour(url) }
            if (palette.isNotEmpty()) {
                runOnUiThread {
                    if (!isFinishing && ambientView === glow) glow.setPalette(palette)
                }
            } else {
                Log.w(TAG, "No preview palette could be extracted for $currentVideoId")
            }
        }.start()
    }

    private fun extractPrimaryColour(url: String): Int? {
        var connection: java.net.HttpURLConnection? = null
        var bitmap: android.graphics.Bitmap? = null
        return try {
            connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 6500
                setRequestProperty("User-Agent", "RykoCollection Android")
                doInput = true
            }
            bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            val image = bitmap ?: return null
            val counts = HashMap<Int, Int>()
            val stepX = (image.width / 36).coerceAtLeast(1)
            val stepY = (image.height / 24).coerceAtLeast(1)
            for (y in 0 until image.height step stepY) {
                for (x in 0 until image.width step stepX) {
                    val pixel = image.getPixel(x, y)
                    val r = Color.red(pixel); val g = Color.green(pixel); val b = Color.blue(pixel)
                    val max = maxOf(r, g, b); val min = minOf(r, g, b)
                    if (max < 28 || max - min < 15) continue
                    val key = Color.rgb((r / 24) * 24, (g / 24) * 24, (b / 24) * 24)
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
            counts.maxByOrNull { it.value }?.key ?: run {
                val pixel = image.getPixel(image.width / 2, image.height / 2)
                Color.rgb(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not sample preview colour: $url", error)
            null
        } finally {
            bitmap?.recycle()
            connection?.disconnect()
        }
    }

    /** Soft radial colour fields create a blurred, glowing liquid blend without showing images. */
    private inner class LiquidAmbientView : View(this@TubePlayerActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var palette = intArrayOf(Color.rgb(20, 20, 24), Color.rgb(20, 20, 24), Color.rgb(20, 20, 24), Color.rgb(20, 20, 24))
        private var phase = 0f
        private var animator: ValueAnimator? = null
        private var paletteAnimator: ValueAnimator? = null
        private var paletteFrom = palette.copyOf()
        private var paletteTarget = palette.copyOf()

        init {
            // Hardware blur softens the colour fields into one continuous glow.
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    dp(38).toFloat(), dp(38).toFloat(), Shader.TileMode.CLAMP
                ))
            }
        }

        fun setPalette(colours: List<Int>) {
            val target = IntArray(4) { index -> colours[index % colours.size] }
            paletteFrom = palette.copyOf()
            paletteTarget = target
            paletteAnimator?.cancel()
            paletteAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500L
                addUpdateListener {
                    val t = it.animatedValue as Float
                    palette = IntArray(4) { i -> blendColour(paletteFrom[i], paletteTarget[i], t) }
                    invalidate()
                }
                start()
            }
        }

        fun resumeFlow() {
            if (animator?.isStarted == true) {
                animator?.resume()
                return
            }
            animator = ValueAnimator.ofFloat(phase, phase + 1f).apply {
                duration = 30000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = (it.animatedValue as Float) % 1f
                    invalidate()
                }
                start()
            }
        }

        fun pauseFlow() { animator?.pause() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            canvas.drawColor(Color.rgb(5, 5, 8))
            // Gentle overlapping drift: the fields share one slow current and
            // merge like coloured ink instead of orbiting as separate moving spots.
            val p = phase * (Math.PI.toFloat() * 2f)
            val centers = arrayOf(
                floatArrayOf(w * (0.24f + 0.055f * kotlin.math.sin(p)), h * (0.34f + 0.045f * kotlin.math.sin(p * 0.72f + 0.4f))),
                floatArrayOf(w * (0.73f + 0.060f * kotlin.math.sin(p + 1.7f)), h * (0.31f + 0.050f * kotlin.math.cos(p * 0.78f))),
                floatArrayOf(w * (0.38f + 0.050f * kotlin.math.cos(p * 0.68f + 2.2f)), h * (0.76f + 0.035f * kotlin.math.sin(p + 2.8f))),
                floatArrayOf(w * (0.82f + 0.045f * kotlin.math.cos(p + 3.6f)), h * (0.72f + 0.040f * kotlin.math.sin(p * 0.74f + 1.2f)))
            )
            val radius = maxOf(w * 0.88f, h * 1.75f)
            centers.forEachIndexed { index, center ->
                val colour = palette[index]
                val glowColour = Color.argb(142, Color.red(colour), Color.green(colour), Color.blue(colour))
                val midColour = Color.argb(54, Color.red(colour), Color.green(colour), Color.blue(colour))
                paint.shader = RadialGradient(
                    center[0], center[1], radius,
                    intArrayOf(glowColour, midColour, Color.TRANSPARENT),
                    floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
                )
                paint.alpha = 255
                canvas.drawCircle(center[0], center[1], radius, paint)
            }
            paint.shader = null
        }

        private fun blendColour(a: Int, b: Int, t: Float): Int = Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt().coerceIn(0, 255),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt().coerceIn(0, 255),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt().coerceIn(0, 255)
        )
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

        // Reuse the initialized iframe player; switching clips does not reload the WebView.
        webView.animate().cancel()
        webView.animate()
            .translationY(if (direction > 0) -dp(42).toFloat() else dp(42).toFloat())
            .alpha(0.82f)
            .setDuration(90)
            .withEndAction {
                if (generation != shortTransitionGeneration || isFinishing) return@withEndAction
                webView.translationY = if (direction > 0) dp(42).toFloat() else -dp(42).toFloat()
                webView.evaluateJavascript("window.loadRykoShort && window.loadRykoShort('$currentVideoId')", null)
                webView.animate().translationY(0f).alpha(1f)
                    .setDuration(110)
                    .withEndAction { swipeHandled = false }
                    .start()
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
        saveAction?.apply { setImageResource(if (ids.contains(currentVideoId)) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark); imageTintList = android.content.res.ColorStateList.valueOf(if (ids.contains(currentVideoId)) Color.rgb(170,145,255) else Color.WHITE) }
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
        ambientPlaybackActive = false
        ambientView?.pauseFlow()
        ambientView = null
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
