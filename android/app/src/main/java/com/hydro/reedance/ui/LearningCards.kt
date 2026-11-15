package com.hydro.reedance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

/** A low-noise hierarchy using theme roles rather than fixed light-only pastel backgrounds. */
@Composable internal fun LearningSection(label: String, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        color = if(accent) colors.secondaryContainer else colors.surfaceContainerLow,
        contentColor = if(accent) colors.onSecondaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .4f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersianText(label, style = MaterialTheme.typography.labelSmall,
                color = if(accent) colors.onSecondaryContainer.copy(alpha = .8f) else colors.onSurfaceVariant)
            content()
        }
    }
}

@Composable internal fun WordLearningContent(word: JsonObject) {
    val fa = LearningLanguage.persian
    val part = word.tr("part", fa)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Foreign(word.s("lemma"), large = true) }
            if(part.isNotBlank()) Surface(shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                PersianText(part, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, fillWidth = false)
            }
        }
        word.tr("translation", fa).takeIf { it.isNotBlank() }?.let { meaning ->
            LearningSection("معنی", accent = true) {
                PersianText(meaning, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }
        if(listOf("ipa", "reading", "faReading").any { word.s(it).isNotBlank() }) {
            LearningSection("تلفظ نوشتاری") { WrittenPronunciation(word) }
        }
        LearningExample(word.obj("example"))
    }
}

@Composable internal fun LearningExample(example: JsonObject) {
    val original = example.s("text")
    val translation = example.tr("translation", LearningLanguage.persian)
    if(original.isNotBlank() || translation.isNotBlank()) LearningSection("مثال در جمله") {
        if(original.isNotBlank()) Foreign(original)
        if(original.isNotBlank() && translation.isNotBlank()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        if(translation.isNotBlank()) PersianText(translation, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun PhraseLearningContent(phrase: JsonObject) {
    val fa = LearningLanguage.persian
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if(phrase.s("text").isNotBlank()) Foreign(phrase.s("text"), large = true)
        phrase.tr("translation", fa).takeIf { it.isNotBlank() }?.let { meaning ->
            LearningSection("معنی", accent = true) { PersianText(meaning, style = MaterialTheme.typography.titleMedium) }
        }
        phrase.tr("context", fa).takeIf { it.isNotBlank() }?.let { context ->
            LearningSection("کاربرد") { PersianText(context) }
        }
        LearningExample(phrase.obj("example"))
    }
}
