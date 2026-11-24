package com.hydro.reedance.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import java.util.Locale

internal enum class WordClass { Noun, Verb, Adjective, Adverb, Pronoun, Preposition, Conjunction, Interjection, Other }

/** Match the whole label, not substrings (adverb must never be classified as verb). */
internal fun wordClass(label: String): WordClass = when(label.trim().lowercase(Locale.ROOT)
    .replace('ي', 'ی').replace('ك', 'ک').removeSuffix(".")) {
    "اسم", "نام", "noun", "n", "isim", "ad" -> WordClass.Noun
    "فعل", "verb", "v", "fiil", "eylem" -> WordClass.Verb
    "صفت", "adjective", "adj", "sıfat" -> WordClass.Adjective
    "قید", "adverb", "adv", "zarf", "belirteç" -> WordClass.Adverb
    "ضمیر", "pronoun", "pron", "zamir" -> WordClass.Pronoun
    "حرف اضافه", "preposition", "postposition", "prep", "edat", "ilgeç" -> WordClass.Preposition
    "حرف ربط", "conjunction", "conj", "bağlaç" -> WordClass.Conjunction
    "حرف ندا", "صوت", "interjection", "interj", "ünlem" -> WordClass.Interjection
    else -> WordClass.Other
}

@Composable internal fun PartOfSpeechBadge(label: String) {
    val kind = wordClass(label)
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    // Each pair is a deliberately paired background/foreground, not a tinted label
    // over an arbitrary theme surface. Unknown/compound labels stay neutral.
    val (background, foreground) = when(kind) {
        WordClass.Noun -> if(dark) 0xFF203D5B to 0xFFD1E5FF else 0xFFE3EFFF to 0xFF214C78
        WordClass.Verb -> if(dark) 0xFF214536 to 0xFFC4EED7 else 0xFFE1F3E8 to 0xFF22553B
        WordClass.Adjective -> if(dark) 0xFF563047 to 0xFFFFD8EA else 0xFFFFE5F0 to 0xFF7B3458
        WordClass.Adverb -> if(dark) 0xFF403259 to 0xFFE9DAFF else 0xFFF0E7FF to 0xFF584078
        WordClass.Pronoun -> if(dark) 0xFF19484A to 0xFFC1ECEC else 0xFFDFF3F3 to 0xFF24595B
        WordClass.Preposition -> if(dark) 0xFF514023 to 0xFFFFE5B4 else 0xFFFFF0D5 to 0xFF71501A
        WordClass.Conjunction -> if(dark) 0xFF51372A to 0xFFFFDFCA else 0xFFFFEBDC to 0xFF77482B
        WordClass.Interjection -> if(dark) 0xFF542F31 to 0xFFFFDADC else 0xFFFFE6E7 to 0xFF7B353B
        WordClass.Other -> if(dark) 0xFF383C43 to 0xFFE0E3E8 else 0xFFEBEDF1 to 0xFF454A54
    }
    Surface(shape = DetailShape, color = Color(background), contentColor = Color(foreground)) {
        PersianText(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium, fillWidth = false)
    }
}
