package com.hydro.reedance.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.*
import com.google.gson.*
import com.hydro.reedance.data.*

@Composable fun Panel(modifier: Modifier = Modifier, tone: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = tone, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Heading(title: String, subtitle: String = "") { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if(subtitle.isNotEmpty()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
} }
@Composable fun Foreign(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text, modifier.fillMaxWidth(), style = (if(large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge).copy(textDirection = TextDirection.Ltr))
    }
}
@Composable fun Action(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp)) { Text(text) }
}
@Composable fun ChoiceRow(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}
/** Reference CSS: enter 390ms (-15,10), exit 220ms (30,0), mirrored in RTL. */
@Composable fun Deck(cardKey: Any, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val sign = if(rtl) -1 else 1
    val sheets = remember { mutableMapOf<Any, @Composable () -> Unit>() }
    sheets[cardKey] = content
    LaunchedEffect(cardKey) {
        if(!reduced) delay(650)
        sheets.keys.retainAll(setOf(cardKey))
    }
    Box(modifier.fillMaxWidth().padding(top = 21.dp)) {
        Box(Modifier.matchParentSize().padding(horizontal = 15.dp).graphicsLayer { translationY = -21.dp.toPx(); rotationZ = 2.3f; scaleX = .973f }.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .22f)))
        Box(Modifier.matchParentSize().padding(horizontal = 8.dp).graphicsLayer { translationY = -13.dp.toPx(); rotationZ = -1.6f; scaleX = .99f }.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)))
        AnimatedContent(cardKey, transitionSpec = {
            if(reduced) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(390, 220)) + slideIn(tween(390, 220, easing = CubicBezierEasing(.19f,.85f,.3f,1f))) { IntOffset(-15 * sign, 10) } + scaleIn(tween(390, 220), initialScale = .975f)) togetherWith
                (fadeOut(tween(220)) + slideOut(tween(220, easing = FastOutLinearInEasing)) { IntOffset(30 * sign, 0) } + scaleOut(tween(220), targetScale = .96f))
        }, label = "card-deck") { key ->
            // Preserve the outgoing visual sheet, but remove its semantics and input.
            val inactive = if(key == cardKey) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(key) {
                awaitPointerEventScope {
                    while(true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
            Box(inactive) { sheets[key]?.invoke() }
        }
    }
}
@Composable fun LearningTable(table: JsonObject) {
    val fa = LocalFa.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(table.tr("title", fa), style = MaterialTheme.typography.titleSmall)
        table.tr("intro", fa).takeIf { it.isNotEmpty() }?.let { Text(it) }
        val columns = table.getAsJsonArray("columns") ?: JsonArray()
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
            Row(Modifier.background(MaterialTheme.colorScheme.secondaryContainer)) {
                columns.forEach { Text(it.cell(fa), Modifier.width(152.dp).padding(12.dp), style = MaterialTheme.typography.titleSmall) }
            }
            table.getAsJsonArray("rows")?.forEach { row ->
                HorizontalDivider()
                Row { row.asJsonArray.forEach { cell ->
                    val dir = if(cell.isJsonObject && cell.asJsonObject.s("dir") == "rtl") LayoutDirection.Rtl else if(cell.isJsonPrimitive) LayoutDirection.Ltr else LocalLayoutDirection.current
                    CompositionLocalProvider(LocalLayoutDirection provides dir) { Text(cell.cell(fa), Modifier.width(152.dp).padding(12.dp)) }
                } }
            }
        }
        table.tr("footer", fa).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
@Composable fun LearningNote(note: JsonObject, onSpeak: (String) -> Unit, save: (@Composable () -> Unit)? = null) {
    val fa = LocalFa.current
    var answer by remember(note) { mutableStateOf(false) }
    val color = when(note.s("tone")) {
        "mint" -> Color(0xFFDEEFE7); "amber" -> Color(0xFFFFEBCB); "coral" -> Color(0xFFFFE0D8); "rose" -> Color(0xFFF8DFE8); "apricot" -> Color(0xFFFFE6CF); else -> Color(0xFFE3EEFF)
    }
    Panel(tone = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, color, .18f)) {
        Text(note.tr("tag", fa), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Text(note.tr("title", fa), style = MaterialTheme.typography.titleLarge)
        Text(note.tr("body", fa).ifEmpty { note.tr("tip", fa) })
        val ex = note.get("example")
        val text = if(ex?.isJsonObject == true) ex.asJsonObject.s("text") else if(ex?.isJsonPrimitive == true) ex.asString else ""
        if(text.isNotEmpty()) {
            Foreign(text)
            Text(if(ex?.isJsonObject == true) ex.asJsonObject.tr("translation", fa) else note.tr("translation", fa))
            TextButton({ onSpeak(text) }) { Text(tr("شنیدن نمونه", "Hear example")) }
        }
        note.a("tables").forEach { LearningTable(it) }
        note.tr("takeaway", fa).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
        if(note.has("check")) {
            Text(note.obj("check").tr("prompt", fa))
            TextButton({ answer = !answer }) { Text(tr("دیدن پاسخ", "Reveal answer")) }
            AnimatedVisibility(answer, enter = if(LocalReducedMotion.current) EnterTransition.None else fadeIn(tween(260)) + expandVertically()) { Text(note.obj("check").tr("answer", fa)) }
        }
        save?.invoke()
    }
}
