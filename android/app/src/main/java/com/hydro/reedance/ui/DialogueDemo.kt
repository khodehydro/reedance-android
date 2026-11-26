package com.hydro.reedance.ui

import androidx.annotation.DrawableRes
import com.hydro.reedance.R
import java.io.File

internal data class DialogueTurn(val speaker: Int, val text: String, val meaning: String, val audio: File? = null, val glosses: List<String> = emptyList(),
    val id: String = "", val startMs: Long? = null, val endMs: Long? = null)
internal enum class DialogueGender { WOMAN, MAN }
internal data class DialogueSpeaker(val name: String, val gender: DialogueGender)

internal data class DialogueDemo(val titleEn: String, val titleFa: String, val speakers: List<DialogueSpeaker>,
    val turns: List<DialogueTurn>, val learning: DialogueLearning, @DrawableRes val artwork: Int, val premium: Boolean = false,
    val id: String = titleEn, val artworkPath: String? = null)
internal data class DialogueCategory(val en: String, val fa: String, val demos: List<DialogueDemo>, @DrawableRes val artwork: Int, val premium: Boolean = false)

internal val dialogueCategories = listOf(
    DialogueCategory("Everyday life", "زندگی روزمره", listOf(DialogueDemo("At a café", "در کافه", listOf(DialogueSpeaker("Elif", DialogueGender.WOMAN), DialogueSpeaker("Deniz", DialogueGender.WOMAN)), listOf(
        DialogueTurn(0, "Merhaba, bir çay alabilir miyim?", "سلام، می‌توانم یک چای بگیرم؟", glosses = listOf("سلام", "یک", "چای", "می‌توانم بگیرم", "آیا؟")),
        DialogueTurn(1, "Tabii. Şeker ister misiniz?", "البته. شکر می‌خواهید؟", glosses = listOf("البته", "شکر", "می‌خواهید", "آیا؟")),
        DialogueTurn(0, "Hayır, teşekkür ederim.", "نه، ممنونم.", glosses = listOf("نه", "تشکر", "می‌کنم")),
        DialogueTurn(1, "Buyurun, afiyet olsun!", "بفرمایید، نوش جان!", glosses = listOf("بفرمایید", "گوارا", "باشد")),
        DialogueTurn(0, "Hesabı alabilir miyim?", "می‌توانم صورت‌حساب را بگیرم؟", glosses = listOf("صورت‌حساب را", "می‌توانم بگیرم", "آیا؟")),
        DialogueTurn(1, "Otuz lira.", "سی لیر.", glosses = listOf("سی", "لیر")),
        DialogueTurn(0, "Kartla ödeyebilir miyim?", "می‌توانم با کارت پرداخت کنم؟", glosses = listOf("با کارت", "می‌توانم پرداخت کنم", "آیا؟")),
        DialogueTurn(1, "Elbette, kartınızı okutun.", "البته، کارتتان را روی دستگاه بگیرید.", glosses = listOf("البته", "کارتتان را", "روی دستگاه بگیرید")),
        DialogueTurn(0, "İyi günler!", "روز خوبی داشته باشید!", glosses = listOf("خوب", "روزها")),
        DialogueTurn(1, "Size de!", "شما هم!", glosses = listOf("به شما", "هم"))
    ), cafeLearning, R.drawable.dialogue_art_cafe)), R.drawable.dialogue_art_daily),
    DialogueCategory("Travel", "سفر", listOf(DialogueDemo("At the station", "در ایستگاه", listOf(DialogueSpeaker("Ece", DialogueGender.WOMAN), DialogueSpeaker("Mert", DialogueGender.MAN)), listOf(
        DialogueTurn(0, "Affedersiniz, tren ne zaman kalkıyor?", "ببخشید، قطار کی حرکت می‌کند؟", glosses = listOf("ببخشید", "قطار", "چه", "زمانی", "حرکت می‌کند")),
        DialogueTurn(1, "Saat üçte kalkıyor.", "ساعت سه حرکت می‌کند.", glosses = listOf("ساعت", "در سه", "حرکت می‌کند")),
        DialogueTurn(0, "Hangi perondan?", "از کدام سکو؟", glosses = listOf("کدام", "از سکو")),
        DialogueTurn(1, "İkinci perondan.", "از سکوی دوم.", glosses = listOf("دوم", "از سکو")),
        DialogueTurn(0, "Perona nasıl giderim?", "چطور به سکو بروم؟", glosses = listOf("به سکو", "چطور", "بروم")),
        DialogueTurn(1, "Merdivenlerden aşağı inin.", "از پله‌ها پایین بروید.", glosses = listOf("از پله‌ها", "به پایین", "بروید")),
        DialogueTurn(0, "Asansör var mı?", "آسانسور هست؟", glosses = listOf("آسانسور", "هست", "آیا؟")),
        DialogueTurn(1, "Evet, sağda.", "بله، سمت راست است.", glosses = listOf("بله", "سمت راست")),
        DialogueTurn(0, "Yardımınız için teşekkür ederim.", "از کمکتان ممنونم.", glosses = listOf("کمکتان", "برای", "تشکر", "می‌کنم")),
        DialogueTurn(1, "Rica ederim.", "خواهش می‌کنم.", glosses = listOf("خواهش", "می‌کنم"))
    ), stationLearning, R.drawable.dialogue_art_station, premium = true)), R.drawable.dialogue_art_travel, premium = true),
    DialogueCategory("Shopping", "خرید", emptyList(), R.drawable.dialogue_art_shopping),
    DialogueCategory("Work", "محیط کار", emptyList(), R.drawable.dialogue_art_work),
    DialogueCategory("Food & drink", "غذا و نوشیدنی", emptyList(), R.drawable.dialogue_art_food),
    DialogueCategory("Asking directions", "پرسیدن مسیر", emptyList(), R.drawable.dialogue_art_directions)
)

internal enum class SpeakerMode(val audio: Boolean, val messages: Boolean) {
    BOTH(true, true), MUTE(false, true), LISTEN(true, false), ROLE(false, false);
    fun next() = entries[(ordinal + 1) % entries.size]
}
