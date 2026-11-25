package com.hydro.reedance.ui

import java.io.File

internal data class DialogueTurn(val speaker: Int, val text: String, val meaning: String, val audio: File? = null, val glosses: List<String> = emptyList())
internal data class DialogueDemo(val titleEn: String, val titleFa: String, val names: List<String>,
    val turns: List<DialogueTurn>, val words: List<Pair<String, String>>, val grammar: List<Pair<String, String>>,
    val phrases: List<Pair<String, String>>, val question: String, val answers: List<String>, val correct: Int)
internal data class DialogueCategory(val en: String, val fa: String, val demos: List<DialogueDemo>)

internal val dialogueCategories = listOf(
    DialogueCategory("Everyday life", "زندگی روزمره", listOf(DialogueDemo("At a café", "در کافه", listOf("Elif", "Deniz"), listOf(
        DialogueTurn(0, "Merhaba, bir çay alabilir miyim?", "سلام، می‌توانم یک چای بگیرم؟", glosses = listOf("سلام", "یک", "چای", "می‌توانم بگیرم", "آیا؟")),
        DialogueTurn(1, "Tabii. Şeker ister misiniz?", "البته. شکر می‌خواهید؟", glosses = listOf("البته", "شکر", "می‌خواهید", "آیا؟")),
        DialogueTurn(0, "Hayır, teşekkür ederim.", "نه، ممنونم.", glosses = listOf("نه", "تشکر", "می‌کنم")),
        DialogueTurn(1, "Buyurun, afiyet olsun!", "بفرمایید، نوش جان!", glosses = listOf("بفرمایید", "گوارا", "باشد"))
    ), listOf("çay" to "چای", "şeker" to "شکر"), listOf("alabilir miyim?" to "برای درخواست مؤدبانه استفاده می‌شود: آیا می‌توانم بگیرم؟"),
        listOf("Afiyet olsun!" to "نوش جان!"), "الیف چه چیزی سفارش داد؟", listOf("چای", "قهوه", "آب"), 0))),
    DialogueCategory("Travel", "سفر", listOf(DialogueDemo("At the station", "در ایستگاه", listOf("Ece", "Mert"), listOf(
        DialogueTurn(0, "Affedersiniz, tren ne zaman kalkıyor?", "ببخشید، قطار کی حرکت می‌کند؟", glosses = listOf("ببخشید", "قطار", "چه", "زمانی", "حرکت می‌کند")),
        DialogueTurn(1, "Saat üçte kalkıyor.", "ساعت سه حرکت می‌کند.", glosses = listOf("ساعت", "در سه", "حرکت می‌کند")),
        DialogueTurn(0, "Hangi perondan?", "از کدام سکو؟", glosses = listOf("کدام", "از سکو")),
        DialogueTurn(1, "İkinci perondan.", "از سکوی دوم.", glosses = listOf("دوم", "از سکو"))
    ), listOf("tren" to "قطار", "peron" to "سکو"), listOf("üçte / perondan" to "پسوند ‎-te زمان و ‎-dan مبدأ را نشان می‌دهد."),
        listOf("Affedersiniz" to "ببخشید؛ برای شروع سؤال مؤدبانه"), "قطار چه ساعتی حرکت می‌کند؟", listOf("ساعت دو", "ساعت سه", "ساعت چهار"), 1)))
)

internal enum class SpeakerMode(val audio: Boolean, val messages: Boolean) {
    BOTH(true, true), MUTE(false, true), LISTEN(true, false), ROLE(false, false);
    fun next() = entries[(ordinal + 1) % entries.size]
}
