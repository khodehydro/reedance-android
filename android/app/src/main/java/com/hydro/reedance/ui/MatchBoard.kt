package com.hydro.reedance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.matchCorrectness

/** One-to-one assignment: moving a meaning disconnects its previous word. */
internal fun connectMatch(answer: List<String>, count: Int, word: Int, meaning: String): List<String> =
    List(count) { index ->
        when {
            index == word -> meaning
            answer.getOrNull(index) == meaning -> ""
            else -> answer.getOrNull(index).orEmpty()
        }
    }

private val MatchGreen = Color(0xFF176B3A)
private val MatchRed = Color(0xFFA82232)

@Composable internal fun MatchBoard(question: JsonObject, answer: List<String>, checked: Boolean,
    enabled: Boolean, onAnswer: (List<String>) -> Unit, solution: Boolean = false, meaningIds: List<String>? = null) {
    val pairs = question.a("pairs")
    val ids = pairs.map { it.s("id") }
    val order = meaningIds ?: question.strings("meaningOrder").filter { it in ids }.distinct().let {
        if(it.size == ids.size) it else ids.reversed()
    }
    val values = if(solution) ids else answer
    val fa = LearningLanguage.persian
    val correctLinks = matchCorrectness(question, values, if(fa) "fa" else "en")
    var word by remember(question) { mutableStateOf<Int?>(null) }
    var meaning by remember(question) { mutableStateOf<String?>(null) }
    val leftBounds = remember(question) { mutableStateMapOf<Int, Rect>() }
    val rightBounds = remember(question) { mutableStateMapOf<String, Rect>() }
    val neutral = MaterialTheme.colorScheme.primary
    val editable = enabled && !checked && !solution
    fun linkColor(index: Int): Color = if(!checked && !solution) neutral
        else if(correctLinks.getOrNull(index) == true) MatchGreen else MatchRed
    fun commit(index: Int, id: String) {
        onAnswer(connectMatch(answer, pairs.size, index, id))
        word = null; meaning = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(!solution && !checked) Text(tr("یک واژه و معنی آن را انتخاب کن؛ برای تغییر، دوباره وصل کن.",
            "Choose a word and its meaning. Reconnect either box to change a match."), style = MaterialTheme.typography.bodySmall)
        // Keep the physical word/meaning columns stable; text direction is handled inside each box.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Text(tr("واژه", "Word"), Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(tr("معنی", "Meaning"), Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            Box(Modifier.fillMaxWidth()) {
                Canvas(Modifier.matchParentSize()) {
                    values.forEachIndexed { index, id ->
                        val left = leftBounds[index] ?: return@forEachIndexed
                        val right = rightBounds[id] ?: return@forEachIndexed
                        val x1 = left.right
                        val x2 = size.width - right.width
                        val y1 = left.center.y
                        val y2 = right.center.y
                        val mid = (x1 + x2) / 2f
                        val color = linkColor(index)
                        val path = Path().apply {
                            moveTo(x1, y1)
                            cubicTo(mid, y1, mid, y2, x2, y2)
                        }
                        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx()))
                        drawCircle(color, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x1, y1))
                        drawCircle(color, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(x2, y2))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        pairs.forEachIndexed { index, pair ->
                            val linked = values.getOrNull(index)?.isNotBlank() == true
                            MatchBox(pair.s("text"), word == index && editable,
                                if(linked) linkColor(index) else null, checked || solution, editable, false,
                                Modifier.onGloballyPositioned { leftBounds[index] = it.boundsInParent() }) {
                                val pending = meaning
                                if(pending != null) commit(index, pending)
                                else word = if(word == index) null else index
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        order.forEach { id ->
                            val index = values.indexOf(id)
                            val pair = pairs.first { it.s("id") == id }
                            MatchBox(pair.s(if(fa) "fa" else "en"), meaning == id && editable,
                                if(index >= 0) linkColor(index) else null, checked || solution, editable, fa,
                                Modifier.onGloballyPositioned { rightBounds[id] = it.boundsInParent() }) {
                                val pending = word
                                if(pending != null) commit(pending, id)
                                else meaning = if(meaning == id) null else id
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun MatchBox(text: String, selected: Boolean, link: Color?, checked: Boolean,
    enabled: Boolean, rtl: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val background = if(checked && link != null) link else if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val foreground = if(checked && link != null) Color.White else MaterialTheme.colorScheme.onSurface
    val state = when {
        checked && link == MatchGreen -> tr("درست", "Correct")
        checked && link == MatchRed -> tr("اشتباه", "Incorrect")
        selected -> tr("انتخاب‌شده؛ طرف مقابل را انتخاب کن", "Selected; choose the other box")
        link != null -> tr("وصل‌شده", "Connected")
        else -> tr("وصل نشده", "Not connected")
    }
    Surface(modifier.fillMaxWidth().semantics { stateDescription = state }
        .selectable(selected, enabled = enabled && LocalDeckInteractive.current, role = Role.Button, onClick = onClick),
        shape = InsetControlShape, color = background, contentColor = foreground,
        border = BorderStroke(if(selected) 2.dp else 1.dp, link ?: if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.heightIn(min = 72.dp).padding(12.dp), contentAlignment = Alignment.Center) {
            Text((if(checked && link == MatchGreen) "✓ " else if(checked && link == MatchRed) "✕ " else "") + text,
                textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(
                    textDirection = if(rtl) TextDirection.Rtl else TextDirection.Ltr))
        }
    }
}
