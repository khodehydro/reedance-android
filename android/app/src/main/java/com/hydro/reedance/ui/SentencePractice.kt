package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.Grade
import com.hydro.reedance.domain.normalizeAnswer

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SentenceOrder(question: JsonObject, answer: List<String>, result: Grade?, busy: Boolean, onAnswer: (List<String>) -> Unit) {
    val chunks = question.a("chunks")
    val ids = chunks.map { it.s("id") }
    val order = question.strings("presentationOrder").filter { it in ids }.distinct().let {
        if(it.size == ids.size) it else ids.reversed()
    }
    val editable = result == null && !busy
    val background = when(result?.correct) {
        true -> androidx.compose.ui.graphics.Color(0xFF176B3A)
        false -> androidx.compose.ui.graphics.Color(0xFFA82232)
        null -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val foreground = if(result != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface
    if(result == null) Text(tr("برای برگرداندن هر بخش، روی آن در جمله بزن.", "Tap a part of your sentence to return it to the options."), style = MaterialTheme.typography.bodySmall)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = background, contentColor = foreground) {
            FlowRow(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if(answer.isEmpty()) Text(tr("جمله را اینجا بساز", "Build your sentence here"), Modifier.padding(12.dp))
                answer.forEachIndexed { index, id ->
                    PressOutlinedButton({ onAnswer(answer.filterIndexed { i, _ -> i != index }) }, enabled = editable,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = foreground, disabledContentColor = foreground),
                        shape = RoundedCornerShape(12.dp)) {
                        Text(chunks.firstOrNull { it.s("id") == id }?.s("text").orEmpty())
                    }
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            order.filter { it !in answer }.forEach { id ->
                PressOutlinedButton({ onAnswer(answer + id) }, enabled = editable, shape = RoundedCornerShape(12.dp)) {
                    Text(chunks.first { it.s("id") == id }.s("text"))
                }
            }
        }
    }
}

/** Use supplied full translations; never invent a translation or reveal an explanation as a hint. */
internal fun typingSentenceMeaning(question: JsonObject, lesson: JsonObject, fa: Boolean): String {
    question.tr("translation", fa).takeIf { it.isNotBlank() }?.let { return it }
    question.tr("contextTranslation", fa).takeIf { it.isNotBlank() }?.let { return it }
    val context = question.s("context")
    val candidates = question.strings("accepted").map { context.replace(Regex("_{2,}"), it) } + context
    val normalized = candidates.map { normalizeAnswer(it) }.toSet()
    val examples = lesson.a("vocabulary").map { it.obj("example") }
    val lines = lesson.a("lyrics") + lesson.a("sentences") + lesson.a("idioms") + examples
    return lines.firstOrNull { normalizeAnswer(it.s("text")) in normalized && it.tr("translation", fa).isNotBlank() }
        ?.tr("translation", fa).orEmpty()
}
