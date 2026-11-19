package com.hydro.reedance.media

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player
import com.hydro.reedance.R
import com.hydro.reedance.data.LearningDatabase
import com.hydro.reedance.data.LocalContentRepository
import com.hydro.reedance.data.SettingsRepository
import com.hydro.reedance.data.a
import com.hydro.reedance.data.n
import com.hydro.reedance.data.s
import com.hydro.reedance.data.tr
import com.hydro.reedance.domain.PremiumEntitlement
import com.hydro.reedance.domain.backgroundPlaybackAllowed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class OverlayLyric(val start: Long, val end: Long?, val text: String, val translation: String, val words: String)

/** Find the latest timestamp efficiently, while respecting a lesson's explicit gaps. */
internal fun timedOverlayLyric(lines: List<OverlayLyric>, position: Long): OverlayLyric? {
    var low = 0
    var high = lines.size
    while(low < high) {
        val mid = (low + high) ushr 1
        if(lines[mid].start <= position) low = mid + 1 else high = mid
    }
    return lines.getOrNull(low - 1)?.takeIf { it.end == null || position < it.end }
}

/** A paused song stays eligible, but a prepared-only, finished or replaced track does not. */
internal fun overlaySongForPlayback(song: String?, previous: String?, playing: Boolean, state: Int): String? =
    song?.takeIf {
        it.isNotBlank() && (state == Player.STATE_READY || state == Player.STATE_BUFFERING) &&
            (playing || it == previous)
    }

/** Owned by the Media3 service, not an Activity. No extra foreground service or background launch. */
internal class PlaybackLyricsOverlay(private val context: Context, private val player: Player) :
    DefaultLifecycleObserver, Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = SettingsRepository(context.applicationContext)
    private val content by lazy { LocalContentRepository(context.applicationContext, LearningDatabase.get(context).learning()) }
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foreground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    private val overlaySong = MutableStateFlow<String?>(null)
    private val dismissed = MutableStateFlow(false)
    private val window = LyricsOverlayWindow(context)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private data class Display(val song: String, val mode: Int, val fa: Boolean)

    init {
        lifecycle.addObserver(this)
        player.addListener(this)
        updateOverlaySong()
        scope.launch {
            combine(preferences.flow, PremiumEntitlement.active, foreground, overlaySong, dismissed) { prefs, premium, inApp, song, hidden ->
                song?.takeIf { prefs.lyricsOverlay && backgroundPlaybackAllowed(premium) && !inApp && !hidden }
                    ?.let { Display(it, prefs.lyricsOverlayMode, prefs.language == "fa") }
            }.distinctUntilChanged().collectLatest { request ->
                window.hide()
                if(request == null) return@collectLatest
                try {
                    val lesson = content.installedLesson(request.song) ?: return@collectLatest
                    val lines = withContext(Dispatchers.Default) {
                        lesson.a("lyrics").map { line ->
                            OverlayLyric(line.n("startMs"), line.n("endMs").takeIf { it > line.n("startMs") },
                                line.s("text").ifBlank { line.a("tokens").joinToString(" ") { it.s("surface") } },
                                line.tr("translation", true),
                                line.a("tokens").mapNotNull { token ->
                                    val word = token.s("surface")
                                    val meaning = token.tr("translation", true)
                                    if(word.isBlank() || meaning.isBlank()) null else "$word: $meaning"
                                }.joinToString("   •   "))
                        }.sortedBy { it.start }
                    }
                    if(lines.isEmpty()) return@collectLatest
                    var displayedLine: OverlayLyric? = null
                    while(isActive) {
                        if(player.isPlaying || displayedLine == null) {
                            displayedLine = timedOverlayLyric(lines, player.currentPosition)
                        }
                        val line = displayedLine?.takeIf {
                            power?.isInteractive == true && keyguard?.isKeyguardLocked == false &&
                                AndroidSettings.canDrawOverlays(context)
                        }
                        val shown = if(line == null) { window.hide(); true } else window.show(line, request.mode, request.fa,
                            onMode = { scope.launch { preferences.lyricsOverlayMode((request.mode + 1) % 3) } },
                            onClose = { dismissed.value = true; window.hide() })
                        delay(if(shown) 240L else 1_200L)
                    }
                } catch(cancelled: CancellationException) {
                    throw cancelled
                } catch(_: Exception) {
                    // Missing or unsupported local content must not interfere with music playback.
                } finally {
                    window.hide()
                }
            }
        }
    }

    private fun updateOverlaySong() {
        overlaySong.value = overlaySongForPlayback(player.currentMediaItem?.mediaId, overlaySong.value,
            player.isPlaying, player.playbackState)
    }
    override fun onEvents(player: Player, events: Player.Events) { updateOverlaySong() }
    override fun onStart(owner: LifecycleOwner) { foreground.value = true; dismissed.value = false; window.hide() }
    override fun onStop(owner: LifecycleOwner) { foreground.value = false }

    fun close() {
        player.removeListener(this)
        lifecycle.removeObserver(this)
        scope.cancel()
        window.hide()
    }
}

/** The actual window is only the small card, so input outside it remains with the other app. */
private class LyricsOverlayWindow(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var original: TextView? = null
    private var meaning: TextView? = null
    private var last: OverlayLyric? = null
    private var width = 0
    private var layoutParams: WindowManager.LayoutParams? = null
    private val positionStore = context.applicationContext.getSharedPreferences("floating_lyrics_position", Context.MODE_PRIVATE)
    private var draggedPosition: Point? = if(positionStore.contains("x") && positionStore.contains("y"))
        Point(positionStore.getInt("x", 0), positionStore.getInt("y", 0)) else null
    private var positionChanged = false
    private var windowArea = Point()
    private var locked = false
    private var controls: LinearLayout? = null
    private var unlockButton: ImageButton? = null
    private var unlockLayout: WindowManager.LayoutParams? = null
    private var rtlControls = false
    private val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun show(line: OverlayLyric, mode: Int, fa: Boolean, onMode: () -> Unit, onClose: () -> Unit): Boolean {
        val configWidth = context.resources.configuration.screenWidthDp
        val targetWidth = dp((configWidth - 24).coerceIn(160, 400))
        if(width != targetWidth) hide()
        if(root == null) {
            windowArea = availableArea()
            val overlay = create(mode, fa, onMode, onClose)
            val initial = draggedPosition ?: Point((windowArea.x - targetWidth) / 2, dp(52))
            val params = WindowManager.LayoutParams(targetWidth.coerceAtMost(windowArea.x), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                baseFlags,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = initial.x.coerceIn(0, (windowArea.x - this.width).coerceAtLeast(0))
                y = initial.y.coerceIn(0, (windowArea.y - dp(84)).coerceAtLeast(0))
                setTitle("Reedance floating lyrics")
            }
            overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if(root === overlay) moveTo(params.x, params.y, fromDrag = false)
            }
            try {
                val wm = manager ?: return false
                wm.addView(overlay, params)
                root = overlay
                layoutParams = params
                width = targetWidth
                if(locked) setLocked(true)
            } catch(_: RuntimeException) {
                // Restricted profiles, OEM policies and permission changes can reject this window.
                hide()
                return false
            }
        }
        if(last != line) {
            last = line
            original?.text = line.text
            val second = when(mode) { 1 -> line.translation; 2 -> line.words; else -> "" }
            meaning?.apply {
                text = second
                visibility = if(second.isNotBlank()) View.VISIBLE else View.GONE
                textDirection = if(mode == 2) View.TEXT_DIRECTION_FIRST_STRONG_LTR else View.TEXT_DIRECTION_FIRST_STRONG_RTL
            }
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun availableArea(): Point {
        if(Build.VERSION.SDK_INT >= 30 && manager != null) {
            runCatching {
                val metrics = manager.currentWindowMetrics
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                return Point((metrics.bounds.width() - insets.left - insets.right).coerceAtLeast(1),
                    (metrics.bounds.height() - insets.top - insets.bottom).coerceAtLeast(1))
            }
        }
        val metrics = context.resources.displayMetrics
        return Point(metrics.widthPixels.coerceAtLeast(1), metrics.heightPixels.coerceAtLeast(1))
    }

    private fun moveTo(x: Int, y: Int, fromDrag: Boolean) {
        val card = root ?: return
        val params = layoutParams ?: return
        val left = x.coerceIn(0, (windowArea.x - params.width).coerceAtLeast(0))
        val top = y.coerceIn(0, (windowArea.y - card.height).coerceAtLeast(0))
        if(fromDrag) {
            draggedPosition = Point(left, top)
            positionChanged = true
        }
        if(params.x == left && params.y == top) return
        params.x = left
        params.y = top
        runCatching { manager?.updateViewLayout(card, params) }
        if(locked) repositionUnlockButton()
    }

    private fun setLocked(value: Boolean) {
        val card = root ?: return
        val params = layoutParams ?: return
        val wm = manager ?: return
        try {
            if(value) {
                // The caption window stops receiving touch. Only the small, separate lock button remains touchable.
                val button = iconControl(R.drawable.uicon_lock,
                    if(rtlControls) "باز کردن قفل متن شناور" else "Unlock floating lyrics") { setLocked(false) }
                val buttonParams = WindowManager.LayoutParams(dp(48), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    baseFlags, PixelFormat.TRANSLUCENT).apply {
                    gravity = Gravity.TOP or Gravity.LEFT
                    x = unlockX(params)
                    y = params.y
                    setTitle("Reedance floating lyrics unlock")
                }
                wm.addView(button, buttonParams)
                unlockButton = button
                unlockLayout = buttonParams
                params.flags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                // Android 12+ only passes touches through an application overlay below its obscuring limit.
                params.alpha = if(Build.VERSION.SDK_INT >= 31) {
                    minOf(.79f, context.getSystemService(InputManager::class.java)?.maximumObscuringOpacityForTouch ?: .79f)
                } else 1f
            } else {
                params.flags = baseFlags
                params.alpha = 1f
            }
            wm.updateViewLayout(card, params)
            locked = value
            updateLockedStyle(card, value)
            controls?.visibility = if(value) View.INVISIBLE else View.VISIBLE
            card.importantForAccessibility = if(value) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            if(!value) removeUnlockButton()
        } catch(_: RuntimeException) {
            // Never leave an untouchable card without its unlock button.
            locked = false
            removeUnlockButton()
            params.flags = baseFlags
            params.alpha = 1f
            updateLockedStyle(card, false)
            controls?.visibility = View.VISIBLE
            card.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            if(runCatching { wm.updateViewLayout(card, params) }.isFailure) hide()
        }
    }

    private fun updateLockedStyle(card: View, isLocked: Boolean) {
        // Window alpha must remain below Android's touch-through threshold. A darker backing
        // and medium-weight lettering preserve contrast without making the card touchable.
        val dimmedBySystem = isLocked && Build.VERSION.SDK_INT >= 31
        (card.background as? GradientDrawable)?.setColor(if(dimmedBySystem) Color.BLACK else 0xAD000000.toInt())
        val weight = if(dimmedBySystem) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
        original?.typeface = weight
        meaning?.typeface = weight
    }

    private fun unlockX(card: WindowManager.LayoutParams): Int {
        val inset = dp(12) + dp(32) // Same leading padding and grip width as the unlocked header.
        return card.x + if(rtlControls) (card.width - inset - dp(48)).coerceAtLeast(0) else inset
    }

    private fun repositionUnlockButton() {
        val button = unlockButton ?: return
        val params = unlockLayout ?: return
        val card = layoutParams ?: return
        params.x = unlockX(card)
        params.y = card.y
        runCatching { manager?.updateViewLayout(button, params) }
    }

    private fun removeUnlockButton() {
        unlockButton?.let { button -> runCatching { manager?.removeViewImmediate(button) } }
        unlockButton = null
        unlockLayout = null
    }

    private fun savePosition() {
        if(!positionChanged) return
        draggedPosition?.let { point -> positionStore.edit().putInt("x", point.x).putInt("y", point.y).apply() }
        positionChanged = false
    }

    /** Intercept movement anywhere in the card, but let the child buttons handle ordinary taps. */
    private inner class DraggableCard : LinearLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var touch = Point()
        private var start = Point()
        private var dragging = false
        private var pointer = -1

        private fun begin(event: MotionEvent) {
            val params = layoutParams as? WindowManager.LayoutParams ?: return
            windowArea = availableArea()
            touch = Point(event.rawX.roundToInt(), event.rawY.roundToInt())
            start = Point(params.x, params.y)
            pointer = event.getPointerId(0)
            dragging = false
        }

        private fun move(event: MotionEvent): Boolean {
            if(event.getPointerId(0) != pointer) return dragging
            val dx = event.rawX.roundToInt() - touch.x
            val dy = event.rawY.roundToInt() - touch.y
            if(kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) dragging = true
            if(dragging) moveTo(start.x + dx, start.y + dy, fromDrag = true)
            return dragging
        }

        private fun finish() {
            savePosition()
            dragging = false
            pointer = -1
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { begin(event); false }
            MotionEvent.ACTION_MOVE -> move(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { finish(); false }
            else -> false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if(pointer == -1) begin(event)
                MotionEvent.ACTION_MOVE -> move(event)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish()
            }
            return true
        }
    }

    private fun create(mode: Int, fa: Boolean, onMode: () -> Unit, onClose: () -> Unit): LinearLayout {
        val modeName = if(fa) listOf("متن اصلی", "ترجمهٔ خطی", "کلمه‌به‌کلمه")[mode]
            else listOf("Original", "Line by line", "Word by word")[mode]
        rtlControls = fa
        return DraggableCard().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(14))
            background = GradientDrawable().apply {
                setColor(0xAD000000.toInt())
                cornerRadius = dp(20).toFloat()
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = if(fa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                val grip = TextView(context).apply {
                    text = "⠿"
                    textSize = 23f
                    gravity = Gravity.CENTER_VERTICAL
                    setTextColor(Color.WHITE)
                    contentDescription = if(fa) "برای جابه‌جایی بکش" else "Drag to move floating lyrics"
                    setPadding(dp(8), 0, 0, 0)
                }
                addView(grip, LinearLayout.LayoutParams(dp(32), dp(48)))
                addView(iconControl(R.drawable.uicon_lock_open, if(fa) "قفل کردن متن شناور" else "Lock floating lyrics") { setLocked(true) },
                    LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
                val modeIcon = when(mode) {
                    1 -> R.drawable.uicon_lines
                    2 -> R.drawable.uicon_word_mode
                    else -> R.drawable.uicon_original
                }
                addView(iconControl(modeIcon, if(fa) "تغییر حالت متن: $modeName" else "Change lyric mode: $modeName", onMode),
                    LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(control("×", if(fa) "بستن متن شناور" else "Close floating lyrics", onClose).apply { textSize = 24f },
                    LinearLayout.LayoutParams(dp(48), dp(48)))
            }.also { controls = it })
            original = lyricText(18f, Color.WHITE, 3).also { addView(it) }
            meaning = lyricText(14f, Color.WHITE, 5).apply { setPadding(0, dp(6), 0, 0) }.also { addView(it) }
        }
    }

    private fun lyricText(size: Float, ink: Int, lines: Int) = TextView(context).apply {
        textSize = size
        setTextColor(ink)
        gravity = Gravity.CENTER
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun iconControl(icon: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        setColorFilter(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(14), dp(14), dp(14), dp(14))
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = description
        setOnClickListener { action() }
    }

    private fun control(title: String, description: String, action: () -> Unit) = Button(context).apply {
        text = title
        contentDescription = description
        setAllCaps(false)
        minWidth = 0
        minimumWidth = 0
        textSize = 13f
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(8), 0, dp(8), 0)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { action() }
    }

    fun hide() {
        savePosition()
        removeUnlockButton()
        controls = null
        root?.let { view -> runCatching { manager?.removeViewImmediate(view) } }
        root = null
        layoutParams = null
        original = null
        meaning = null
        last = null
    }
}
