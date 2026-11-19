package com.hydro.reedance.media

import android.app.KeyguardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.Player
import com.hydro.reedance.data.LearningDatabase
import com.hydro.reedance.data.LocalContentRepository
import com.hydro.reedance.data.SettingsRepository
import com.hydro.reedance.data.a
import com.hydro.reedance.data.n
import com.hydro.reedance.data.s
import com.hydro.reedance.data.tr
import com.hydro.reedance.domain.PremiumEntitlement
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

/** Owned by the Media3 service, not an Activity. No extra foreground service or background launch. */
internal class PlaybackLyricsOverlay(private val context: Context, private val player: Player) :
    DefaultLifecycleObserver, Player.Listener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = SettingsRepository(context.applicationContext)
    private val content by lazy { LocalContentRepository(context.applicationContext, LearningDatabase.get(context).learning()) }
    private val lifecycle = ProcessLifecycleOwner.get().lifecycle
    private val foreground = MutableStateFlow(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    private val playingSong = MutableStateFlow<String?>(null)
    private val dismissed = MutableStateFlow(false)
    private val window = LyricsOverlayWindow(context)
    private val power = context.getSystemService(PowerManager::class.java)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private data class Display(val song: String, val mode: Int, val fa: Boolean)

    init {
        lifecycle.addObserver(this)
        player.addListener(this)
        updatePlayingSong()
        scope.launch {
            combine(preferences.flow, PremiumEntitlement.active, foreground, playingSong, dismissed) { prefs, premium, inApp, song, hidden ->
                song?.takeIf { prefs.lyricsOverlay && premium && !inApp && !hidden }
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
                    while(isActive) {
                        val line = if(power?.isInteractive == true && keyguard?.isKeyguardLocked == false &&
                            AndroidSettings.canDrawOverlays(context)) timedOverlayLyric(lines, player.currentPosition) else null
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

    private fun updatePlayingSong() {
        playingSong.value = player.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() && player.isPlaying }
    }
    override fun onEvents(player: Player, events: Player.Events) { updatePlayingSong() }
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
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()

    fun show(line: OverlayLyric, mode: Int, fa: Boolean, onMode: () -> Unit, onClose: () -> Unit): Boolean {
        val targetWidth = dp((context.resources.configuration.screenWidthDp - 28).coerceIn(160, 380))
        if(width != targetWidth) hide()
        if(root == null) {
            val overlay = create(mode, fa, onMode, onClose)
            val params = WindowManager.LayoutParams(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(52)
                setTitle("Reedance floating lyrics")
            }
            try {
                val wm = manager ?: return false
                wm.addView(overlay, params)
                root = overlay
                width = targetWidth
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

    private fun create(mode: Int, fa: Boolean, onMode: () -> Unit, onClose: () -> Unit): LinearLayout {
        val modeName = if(fa) listOf("متن اصلی", "ترجمهٔ خطی", "کلمه‌به‌کلمه")[mode]
            else listOf("Original", "Line by line", "Word by word")[mode]
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(13))
            background = GradientDrawable().apply {
                setColor(0xF21A2340.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFF5279B5.toInt())
            }
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutDirection = if(fa) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                addView(TextView(context).apply {
                    text = if(fa) "ری‌دنس" else "Reedance"
                    setTextColor(0xFFBBD3FF.toInt()); textSize = 12f
                    maxLines = 1
                }, LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1f))
                addView(control(modeName, if(fa) "تغییر حالت متن: $modeName" else "Change lyric mode: $modeName", onMode),
                    LinearLayout.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, dp(48)))
                addView(control("×", if(fa) "بستن متن شناور" else "Close floating lyrics", onClose),
                    LinearLayout.LayoutParams(dp(48), dp(48)))
            })
            original = lyricText(18f, Color.WHITE, 3).also { addView(it) }
            meaning = lyricText(14f, 0xFFC6DBFF.toInt(), 5).apply { setPadding(0, dp(6), 0, 0) }.also { addView(it) }
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
        root?.let { view -> runCatching { manager?.removeViewImmediate(view) } }
        root = null
        original = null
        meaning = null
        last = null
    }
}
