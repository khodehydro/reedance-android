package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/** Paragraph base direction is Persian even if a sentence begins with a Latin word or number. */
@Composable internal fun PersianText(text: String, modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified, fillWidth: Boolean = true) {
    Text(text, if(fillWidth) modifier.fillMaxWidth() else modifier, color = color, style = style.copy(
        textDirection = TextDirection.Rtl, textAlign = TextAlign.Right))
}

/** Direction for mixed table cells: explicit exporter direction wins; otherwise inspect actual text. */
internal fun learningCellRtl(text: String, explicit: String = ""): Boolean = when(explicit) {
    "rtl" -> true
    "ltr" -> false
    else -> text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }
}

@Composable internal fun LearningCell(text: String, explicit: String, style: TextStyle) {
    val rtl = learningCellRtl(text, explicit)
    Text(text, Modifier.width(152.dp).padding(12.dp),
        style = style.copy(textDirection = if(rtl) TextDirection.Rtl else TextDirection.Ltr,
            textAlign = if(rtl) TextAlign.Right else TextAlign.Left))
}

/** The answer is in the song language (currently LTR), never mirrored by an RTL interface. */
@Composable internal fun PracticeTypingField(value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
    ) {
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text(tr("پاسخ", "Answer")) },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), singleLine = true,
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left))
    }
}
