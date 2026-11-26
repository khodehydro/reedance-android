package com.hydro.reedance.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class DialogueGuideStep(val fa: String, val en: String, val detailFa: String, val detailEn: String) {
    Play("پخش و مکث", "Play and pause", "برای شروع مکالمه این دکمه را بزن. دوباره بزن تا مکث کند.", "Tap to start the dialogue. Tap again to pause."),
    Messages("پیام‌ها", "Messages", "پیام‌ها به‌ترتیب ظاهر می‌شوند. برای بازپخش فقط یک پیام، روی همان حباب بزن.", "Messages appear in order. Tap a bubble to replay just that line."),
    FirstSpeaker("گویندهٔ اول", "First speaker", "با لمس آواتار، حالت گوینده را عوض کن: صدا و پیام، فقط پیام، فقط صدا یا نقش‌آفرینی. پیام مخفی به … تبدیل می‌شود.", "Tap the avatar to cycle through audio and messages, messages only, audio only, and role-play. Hidden messages become …."),
    SecondSpeaker("گویندهٔ دوم", "Second speaker", "حالت این گوینده جداگانه تنظیم می‌شود. در نقش‌آفرینی، جای او جواب بده.", "This speaker has independent settings. In role-play, say this speaker’s lines yourself."),
    Speed("سرعت", "Speed", "سرعت مکالمه را کم یا زیاد کن تا با ریتم خودت تمرین کنی.", "Slow down or speed up the dialogue to practice at your own pace."),
    Layout("چیدمان پیام‌ها", "Message layout", "بین لیست کامل و نمایش دو پیام آخر جابه‌جا شو.", "Switch between the full list and the last two messages."),
    Hide("فقط گوش‌دادن", "Listen without text", "همهٔ پیام‌ها را مخفی کن تا بدون متن گوش بدهی. این گزینه صدا را قطع نمی‌کند.", "Hide all messages to listen without text. This does not mute the audio."),
    Translation("ترجمه", "Translation", "بین متن اصلی، ترجمهٔ جمله و معنی واژه‌به‌واژه جابه‌جا شو.", "Switch between original text, sentence translation, and word-by-word meanings."),
    Appearance("تنظیمات ظاهر", "Appearance settings", "با چرخ‌دنده اندازهٔ متن و معنی‌ها، نور و تم پس‌زمینه را تنظیم کن.", "Open the gear to adjust text and translation size, brightness, and the background theme."),
    Tabs("تب‌های آموزشی", "Learning tabs", "با لمس تب یا سوایپ، بین چت، لغات و تلفظ، گرامر و جدول‌ها، اصطلاحات و سؤال‌ها جابه‌جا شو.", "Tap a tab or swipe to switch between chat, vocabulary and pronunciation, grammar tables, phrases, and questions.")
}

@Composable internal fun DialogueGuide(content: @Composable (showing: Boolean, highlight: (DialogueGuideStep) -> Modifier) -> Unit) {
    // Session-only: reopening a conversation starts the guide again.
    var index by rememberSaveable { mutableIntStateOf(0) }
    val steps = DialogueGuideStep.entries
    val step = steps.getOrNull(index)
    val targets = remember { mutableStateMapOf<DialogueGuideStep, Rect>() }
    fun finish() { index = steps.size }
    BackHandler(enabled = step != null) { finish() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if(step != null) Modifier.clearAndSetSemantics {} else Modifier)) {
            content(step != null) { target ->
                if(step == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
                    targets[target] = coordinates.boundsInRoot()
                }
            }
        }
        if(step != null) DialogueGuideOverlay(step, index, steps.size, targets[step], ::finish) { index++ }
    }
}

@Composable private fun DialogueGuideOverlay(step: DialogueGuideStep, index: Int, total: Int, target: Rect?,
    dismiss: () -> Unit, next: () -> Unit) {
    var region by remember { mutableStateOf<Rect?>(null) }
    val colors = MaterialTheme.colorScheme
    val top = target != null && region?.let { target.center.y > it.center.y } == true
    Box(Modifier.fillMaxSize().onGloballyPositioned { region = it.boundsInRoot() }) {
        Canvas(Modifier.matchParentSize().pointerInput(Unit) {
            awaitPointerEventScope { while(true) awaitPointerEvent().changes.forEach { it.consume() } }
        }) {
            val root = region
            val hole = if(root != null && target != null) target.translate(-root.topLeft).inflate(4.dp.toPx()) else null
            if(hole == null || hole.width <= 0f || hole.height <= 0f) {
                drawRect(Color.Black.copy(alpha = .72f))
            } else {
                val radius = 14.dp.toPx()
                val cutout = Path().apply { addRoundRect(RoundRect(hole, CornerRadius(radius))) }
                clipPath(cutout, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = .72f)) }
                drawRoundRect(colors.primary, hole.topLeft, Size(hole.width, hole.height), CornerRadius(radius), style = Stroke(2.dp.toPx()))
            }
        }
        Surface(Modifier.align(if(top) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 440.dp).fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
            shape = GroupedCardShape, color = colors.surfaceContainerHigh, contentColor = colors.onSurface) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("راهنمای ری‌تاک · ${index + 1} از $total", "ReeTalk guide · ${index + 1} of $total"),
                    style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Text(tr(step.fa, step.en), style = MaterialTheme.typography.titleLarge)
                Text(tr(step.detailFa, step.detailEn), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PressTextButton(dismiss) { Text(tr("رد کردن", "Skip")) }
                    PressTextButton(next) { Text(if(index == total - 1) tr("تمام", "Done") else tr("بعدی", "Next")) }
                }
            }
        }
    }
}
