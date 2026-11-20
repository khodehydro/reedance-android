package com.hydro.reedance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.google.gson.JsonObject
import com.hydro.reedance.data.*
import com.hydro.reedance.domain.Grade

/** Explicit disabled colors keep submitted answers readable instead of fading feedback away. */
@Composable internal fun PracticeChoices(question: JsonObject, optionIds: List<String>, selected: String?, result: Grade?, busy: Boolean, onSelect: (String) -> Unit) {
    val fa = LearningLanguage.persian
    val optionsById = question.a("options").associateBy { it.s("id") }
    optionIds.forEach { id ->
        val option = optionsById.getValue(id)
        val correct = result != null && id == question.s("correctId")
        val wrong = result != null && id == selected && !correct
        val background = when {
            correct -> Color(0xFF176B3A)
            wrong -> Color(0xFFA82232)
            else -> MaterialTheme.colorScheme.surface
        }
        val foreground = if(correct || wrong) Color.White else MaterialTheme.colorScheme.onSurface
        val state = when {
            correct -> tr("پاسخ درست", "Correct answer")
            wrong -> tr("پاسخ اشتباه", "Incorrect answer")
            else -> ""
        }
        PressOutlinedButton({ onSelect(id) }, Modifier.fillMaxWidth().semantics { stateDescription = state },
            enabled = result == null && !busy,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = background, contentColor = foreground,
                disabledContainerColor = background, disabledContentColor = foreground)) {
            PersianText((if(correct) "✓ " else if(wrong) "✕ " else if(selected == id) "● " else "○ ") + option.s(if(fa) "fa" else "en"))
        }
    }
}
