package com.hydro.reedance.ui

import androidx.annotation.DrawableRes
import com.hydro.reedance.R
import java.io.File

internal data class DialogueTurn(val speaker: Int, val text: String, val meaning: String, val audio: File? = null, val glosses: List<String> = emptyList())
internal enum class DialogueGender { WOMAN, MAN }
internal data class DialogueSpeaker(val name: String, val gender: DialogueGender)

internal data class DialogueDemo(val titleEn: String, val titleFa: String, val speakers: List<DialogueSpeaker>,
    val turns: List<DialogueTurn>, val learning: DialogueLearning, @DrawableRes val artwork: Int)
internal data class DialogueCategory(val en: String, val fa: String, val demos: List<DialogueDemo>, @DrawableRes val artwork: Int)

internal val dialogueCategories = listOf(
    DialogueCategory("Everyday life", "زندگی روزمره", listOf(DialogueDemo("At a café", "در کافه", listOf(DialogueSpeaker("Elif", DialogueGender.WOMAN), DialogueSpeaker("Deniz", DialogueGender.WOMAN)), listOf(
        DialogueTurn(0, "Merhaba, bir çay alabilir miyim?", "سلام، می‌توانم یک چای بگیرم؟", glosses = listOf("سلام", "یک", "چای", "می‌توانم بگیرم", "آیا؟")),
        DialogueTurn(1, "Tabii. Şeker ister misiniz?", "البته. شکر می‌خواهید؟", glosses = listOf("البته", "شکر", "می‌خواهید", "آیا؟")),
        DialogueTurn(0, "Hayır, teşekkür ederim.", "نه، ممنونم.", glosses = listOf("نه", "تشکر", "می‌کنم")),
        DialogueTurn(1, "Buyurun, afiyet olsun!", "بفرمایید، نوش جان!", glosses = listOf("بفرمایید", "گوارا", "باشد"))
    ), cafeLearning, R.drawable.dialogue_art_cafe)), R.drawable.dialogue_art_daily),
    DialogueCategory("Travel", "سفر", listOf(DialogueDemo("At the station", "در ایستگاه", listOf(DialogueSpeaker("Ece", DialogueGender.WOMAN), DialogueSpeaker("Mert", DialogueGender.MAN)), listOf(
        DialogueTurn(0, "Affedersiniz, tren ne zaman kalkıyor?", "ببخشید، قطار کی حرکت می‌کند؟", glosses = listOf("ببخشید", "قطار", "چه", "زمانی", "حرکت می‌کند")),
        DialogueTurn(1, "Saat üçte kalkıyor.", "ساعت سه حرکت می‌کند.", glosses = listOf("ساعت", "در سه", "حرکت می‌کند")),
        DialogueTurn(0, "Hangi perondan?", "از کدام سکو؟", glosses = listOf("کدام", "از سکو")),
        DialogueTurn(1, "İkinci perondan.", "از سکوی دوم.", glosses = listOf("دوم", "از سکو"))
    ), stationLearning, R.drawable.dialogue_art_station)), R.drawable.dialogue_art_travel)
)

internal enum class SpeakerMode(val audio: Boolean, val messages: Boolean) {
    BOTH(true, true), MUTE(false, true), LISTEN(true, false), ROLE(false, false);
    fun next() = entries[(ordinal + 1) % entries.size]
}
