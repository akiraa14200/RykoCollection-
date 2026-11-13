package com.rykocollection.app.ui

import com.rykocollection.app.R

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import androidx.core.view.WindowCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import android.util.TypedValue

/** Standalone YouTube iframe player with a fixed YouTube-standard 16:9 viewing area. */
class TubePlayerActivity : ComponentActivity() {
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

    private val youtubeRatingScope = "https://www.googleapis.com/auth/youtube.force-ssl"
    private var pendingYouTubeRating: String? = null
    private val youtubeAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            pendingYouTubeRating = null
            Toast.makeText(this, "YouTube permission was not granted.", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val authorization = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(result.data)
                val token = authorization.accessToken
                val rating = pendingYouTubeRating
                pendingYouTubeRating = null
                if (token.isNullOrBlank() || rating == null) {
                    Toast.makeText(this, "YouTube authorization did not return an access token.", Toast.LENGTH_LONG).show()
                } else {
                    submitYouTubeRating(token, rating)
                }
            } catch (error: Exception) {
                pendingYouTubeRating = null
                Toast.makeText(this, error.message ?: "YouTube authorization failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private lateinit var root: FrameLayout
    private lateinit var playerView: YouTubePlayerView
    private var youTubePlayer: YouTubePlayer? = null
    private var fullscreenView: View? = null
    private var shortIsPlaying = false
    private var hasPlaybackStarted = false
    private var playbackFallbackShown = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private val playbackFallbackRunnable = Runnable {
        if (!hasPlaybackStarted && !isFinishing && !isDestroyed) showPlaybackFallback()
    }
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.TRANSPARENT
        window.statusBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
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
                val navResourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val navInset = if (navResourceId > 0) resources.getDimensionPixelSize(navResourceId) else dp(24)
                setPadding(0, statusInset, 0, navInset)
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

        playerView = YouTubePlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            enableAutomaticInitialization = false
            lifecycle.addObserver(this)
            val options = IFramePlayerOptions.Builder(this@TubePlayerActivity)
                .controls(if (isShort) 0 else 1)
                .fullscreen(1)
                .autoplay(1)
                .rel(0)
                .ivLoadPolicy(3)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(player: YouTubePlayer) {
                    youTubePlayer = player
                    player.loadVideo(currentVideoId, 0f)
                    // YouTube may block embedded playback with an account/anti-bot check.
                    // Never bypass that check; offer the official YouTube app as a fallback.
                    mainHandler.removeCallbacks(playbackFallbackRunnable)
                    mainHandler.postDelayed(playbackFallbackRunnable, 18_000L)
                }

                override fun onStateChange(player: YouTubePlayer, state: PlayerConstants.PlayerState) {
                    shortIsPlaying = state == PlayerConstants.PlayerState.PLAYING
                    if (shortIsPlaying) {
                        hasPlaybackStarted = true
                        mainHandler.removeCallbacks(playbackFallbackRunnable)
                    }
                    ambientPlaybackActive = shortIsPlaying
                    if (shortIsPlaying) ambientView?.resumeFlow() else ambientView?.pauseFlow()
                }

                override fun onError(player: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    Log.e(TAG, "YouTube player error: $error")
                    Toast.makeText(this@TubePlayerActivity, "Video unavailable or playback interrupted.", Toast.LENGTH_LONG).show()
                    showPlaybackFallback(force = true)
                }
            }, options)
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    if (this@TubePlayerActivity.fullscreenView != null) return
                    this@TubePlayerActivity.fullscreenView = fullscreenView
                    root.addView(fullscreenView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
                    ))
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
                }

                override fun onExitFullscreen() {
                    this@TubePlayerActivity.fullscreenView?.let { root.removeView(it) }
                    this@TubePlayerActivity.fullscreenView = null
                    window.decorView.systemUiVisibility = originalSystemUi
                }
            })
        }

        // The embedded video stays 16:9 instead of stretching to fill a tall phone screen.
        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val playerHeightDp = (screenWidthDp * 9f / 16f).toInt()
        if (!isShort) {
            page.addView(playerView, LinearLayout.LayoutParams(
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
        // Attach the live colour-field renderer; playback callbacks animate it only while video plays.
        startAmbientPreviewCycle(ambientLayer)
        // Keep the player backdrop neutral instead of sampling thumbnail colours.
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
            setTextColor(Color.rgb(224, 224, 224))
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
                val nextRating = if (prefs.getBoolean("like_" + currentVideoId, false)) "none" else "like"
                requestYouTubeRating(nextRating)
            }
        }
        dislikeAction = iconAction(R.drawable.ic_thumb_down, "Dislike").apply {
            setOnClickListener {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val nextRating = if (prefs.getBoolean("dislike_" + currentVideoId, false)) "none" else "dislike"
                requestYouTubeRating(nextRating)
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
                setColor(Color.rgb(28, 28, 30))
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
            setTextColor(Color.rgb(190, 190, 194))
            textSize = 12f
        })
        commentsCard.addView(commentsHeading)
        commentsCard.addView(TextView(this).apply {
            text = "Share your thoughts about this video"
            setTextColor(Color.rgb(225, 225, 228))
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
            // Expand recommendation thumbnails beyond the padded details column
            // so they reach both screen edges, matching the main RykoTube feed.
            recommendation.addView(thumb, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(screenWidthDp * 9 / 16)
            ).apply {
                leftMargin = -dp(18)
                rightMargin = -dp(18)
            })
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
            root.addView(playerView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))

            // Keep swipe/tap gestures outside the YouTube iframe. Touches delivered
            // directly to the iframe can briefly reveal its own playback overlay
            // every time a Short is changed. The transparent layer owns all Shorts
            // gestures while controls(0) keeps the iframe controls hidden.
            val shortGestureLayer = View(this).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            touchStartY = event.rawY
                            touchStartX = event.rawX
                            touchStartAt = android.os.SystemClock.elapsedRealtime()
                            true
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
                            } else if (
                                kotlin.math.abs(dy) < dp(18) &&
                                kotlin.math.abs(dx) < dp(18) &&
                                elapsed < 350L
                            ) {
                                youTubePlayer?.let { if (shortIsPlaying) it.pause() else it.play() }
                            }
                            true
                        }
                        android.view.MotionEvent.ACTION_CANCEL -> true
                        else -> true
                    }
                }
            }
            root.addView(shortGestureLayer, FrameLayout.LayoutParams(
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

    /**
     * Requests YouTube's official OAuth permission only after the user taps Like/Dislike.
     * Google Identity Services reuses a valid grant and refreshes short-lived access tokens.
     */
    private fun requestYouTubeRating(rating: String) {
        pendingYouTubeRating = rating
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(youtubeRatingScope)))
            .build()
        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { authorization ->
                if (authorization.hasResolution()) {
                    val pendingIntent = authorization.pendingIntent
                    if (pendingIntent == null) {
                        pendingYouTubeRating = null
                        Toast.makeText(this, "Google authorization could not be started.", Toast.LENGTH_LONG).show()
                    } else {
                        youtubeAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    val token = authorization.accessToken
                    pendingYouTubeRating = null
                    if (token.isNullOrBlank()) {
                        Toast.makeText(this, "Google did not return a YouTube access token.", Toast.LENGTH_LONG).show()
                    } else {
                        submitYouTubeRating(token, rating)
                    }
                }
            }
            .addOnFailureListener { error ->
                pendingYouTubeRating = null
                Toast.makeText(this, error.message ?: "Couldn't authorize YouTube access.", Toast.LENGTH_LONG).show()
            }
    }

    /** Performs the real YouTube Data API videos.rate request off the main thread. */
    private fun submitYouTubeRating(accessToken: String, rating: String) {
        val videoId = currentVideoId
        Thread {
            var connection: java.net.HttpURLConnection? = null
            try {
                val url = java.net.URL(
                    "https://www.googleapis.com/youtube/v3/videos/rate?id=" +
                        java.net.URLEncoder.encode(videoId, "UTF-8") +
                        "&rating=" + java.net.URLEncoder.encode(rating, "UTF-8")
                )
                val activeConnection = (url.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("Accept", "application/json")
                    doOutput = true
                }
                connection = activeConnection
                val status = activeConnection.responseCode
                if (status in 200..299) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                        val liked = rating == "like"
                        val disliked = rating == "dislike"
                        prefs.edit()
                            .putBoolean("like_" + videoId, liked)
                            .putBoolean("dislike_" + videoId, disliked)
                            .apply()
                        likeAction?.imageTintList = android.content.res.ColorStateList.valueOf(
                            if (liked) Color.rgb(205, 185, 255) else Color.WHITE
                        )
                        dislikeAction?.imageTintList = android.content.res.ColorStateList.valueOf(
                            if (disliked) Color.rgb(205, 185, 255) else Color.WHITE
                        )
                        Toast.makeText(
                            this,
                            when (rating) {
                                "like" -> "Liked on YouTube"
                                "dislike" -> "Disliked on YouTube"
                                else -> "YouTube rating removed"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val detail = runCatching {
                        (if (status >= 400) connection?.errorStream else connection?.inputStream)
                            ?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty()
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            val message = when (status) {
                                401 -> "YouTube authorization expired. Tap again to reconnect."
                                403 -> "YouTube could not apply this rating. The video may not allow ratings."
                                else -> "YouTube rating failed (HTTP $status)."
                            }
                            Log.w(TAG, "videos.rate failed: HTTP $status $detail")
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "YouTube rating request failed", error)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "Couldn't reach YouTube. Check your connection and try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }.start()
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
    private fun moveShort(direction: Int) {
        if (shortQueue.isEmpty() || swipeHandled) return
        val next = shortIndex + direction
        if (next !in shortQueue.indices) return
        val player = youTubePlayer ?: run {
            Toast.makeText(this, "Player is still preparing. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        swipeHandled = true
        shortIndex = next
        currentVideoId = shortQueue[next]
        hasPlaybackStarted = false
        playbackFallbackShown = false
        mainHandler.removeCallbacks(playbackFallbackRunnable)
        mainHandler.postDelayed(playbackFallbackRunnable, 18_000L)
        currentTitle = shortTitles.getOrNull(next).orEmpty().ifBlank { "Short video" }
        currentCreator = shortCreators.getOrNull(next).orEmpty()
        // Reuse the same native player instance; loadVideo switches clips without WebView reload.
        player.loadVideo(currentVideoId, 0f)
        playerView.animate().cancel()
        playerView.translationY = if (direction > 0) dp(18).toFloat() else -dp(18).toFloat()
        playerView.alpha = 0.92f
        playerView.animate().translationY(0f).alpha(1f).setDuration(120L)
            .withEndAction { swipeHandled = false }
            .start()
    }

    private fun heightPx(): Int = resources.displayMetrics.heightPixels

    // Shorts gestures are handled by the transparent layer above the iframe so
    // swipe events never reach YouTube's built-in touch controls.

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


    /**
     * YouTube sign-in / anti-bot pages are controlled by YouTube and cannot be
     * authenticated or bypassed inside this embedded player. Offer a clear,
     * supported hand-off to the official YouTube app instead.
     */
    private fun openCurrentVideoInYouTube() {
        val videoUrl = Uri.parse("https://www.youtube.com/watch?v=$currentVideoId")
        val youtubeIntent = Intent(Intent.ACTION_VIEW, videoUrl).apply {
            setPackage("com.google.android.youtube")
        }
        try {
            startActivity(youtubeIntent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, videoUrl))
            } catch (_: Exception) {
                Toast.makeText(this, "No app is available to open this YouTube video.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPlaybackFallback(force: Boolean = false) {
        if (playbackFallbackShown || isFinishing || isDestroyed || (!force && hasPlaybackStarted)) return
        playbackFallbackShown = true
        AlertDialog.Builder(this)
            .setTitle("YouTube needs verification")
            .setMessage("YouTube may require sign-in or additional verification for this video. RykoCollection cannot complete or bypass that check inside the embedded player. You can open the same video in the official YouTube app, then return here.")
            .setNegativeButton("Retry here") { _, _ ->
                playbackFallbackShown = false
                hasPlaybackStarted = false
                youTubePlayer?.loadVideo(currentVideoId, 0f)
                mainHandler.removeCallbacks(playbackFallbackRunnable)
                mainHandler.postDelayed(playbackFallbackRunnable, 18_000L)
            }
            .setPositiveButton("Open YouTube") { _, _ -> openCurrentVideoInYouTube() }
            .setOnCancelListener { playbackFallbackShown = false }
            .show()
    }

    private fun showMoreMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Save to RykoTube").setOnMenuItemClickListener { toggleSaved(); true }
            menu.add("Share").setOnMenuItemClickListener { shareVideo(); true }
            menu.add("Report").setOnMenuItemClickListener { reportVideo(); true }
            menu.add("Open in YouTube").setOnMenuItemClickListener {
                openCurrentVideoInYouTube()
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
            fullscreenView?.let { root.removeView(it) }
            fullscreenView = null
            window.decorView.systemUiVisibility = originalSystemUi
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(playbackFallbackRunnable)
        ambientPlaybackActive = false
        ambientView?.pauseFlow()
        ambientView = null
        if (::playerView.isInitialized) {
            lifecycle.removeObserver(playerView)
            playerView.release()
            (playerView.parent as? ViewGroup)?.removeView(playerView)
        }
        youTubePlayer = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
}
