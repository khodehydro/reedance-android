package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

// ReeTalk can render English, Persian and mixed text in adjacent fields. Other lessons
// retain their existing Persian-first layout until they opt into this policy.
internal val LocalReeTalkTextAlignment = staticCompositionLocalOf { false }

@Composable internal fun PersianText(text: String, modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, fillWidth: Boolean = true) {
    val width = if(fillWidth) modifier.fillMaxWidth() else modifier
    if(!LocalReeTalkTextAlignment.current) {
        Text(text, width, color = color, style = style.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right))
        return
    }
    val paragraphs = remember(text) {
        buildAnnotatedString {
            append(text)
            var start = 0
            for(line in text.split('\n')) {
                val end = minOf(start + line.length + 1, text.length)
                if(end > start) {
                    val rtl = learningCellRtl(line)
                    // Unicode bidi still orders English and Persian runs within each paragraph.
                    addStyle(ParagraphStyle(textAlign = if(rtl) TextAlign.Right else TextAlign.Left,
                        textDirection = if(rtl) TextDirection.Rtl else TextDirection.Ltr), start, end)
                }
                start = end
            }
        }
    }
    Text(paragraphs, width, color = color, style = style)
}

/** Direction for mixed table cells: explicit exporter direction wins; otherwise inspect actual text. */
internal fun learningCellRtl(text: String, explicit: String = ""): Boolean = when(explicit) {
    "rtl" -> true
    "ltr" -> false
    else -> text.any { ch ->
        val direction = Character.getDirectionality(ch)
        direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }
}

@Composable internal fun LearningCell(text: String, explicit: String, style: TextStyle, modifier: Modifier = Modifier.width(152.dp)) {
    val rtl = learningCellRtl(text, explicit)
    val directional = LocalReeTalkTextAlignment.current
    Text(text, modifier.then(if(directional) Modifier.fillMaxWidth() else Modifier).padding(12.dp),
        style = style.copy(textDirection = if(rtl) TextDirection.Rtl else TextDirection.Ltr,
            textAlign = if(!directional) TextAlign.Center else if(rtl) TextAlign.Right else TextAlign.Left))
}

/** The answer is in the song language (currently LTR), never mirrored by an RTL interface. */
@Composable internal fun PracticeTypingField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled && LocalDeckInteractive.current,
            label = { Text(tr("پاسخ", "Answer")) },
            shape = InsetControlShape, singleLine = true,
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left))
    }
}
