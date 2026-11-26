package com.hydro.reedance.ui

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

internal data class DialogueLearning(val words: List<JsonObject>, val wordTable: JsonObject,
    val grammar: List<JsonObject>, val phrases: List<JsonObject>, val phraseTable: JsonObject,
    val questions: List<DialogueQuestion>)
internal data class DialogueQuestion(val prompt: String, val options: List<String>, val correct: Int, val explanation: String)

private fun JsonObject.localized(key: String, fa: String, en: String) {
    addProperty("${key}Fa", fa)
    addProperty("${key}En", en)
}

private fun example(text: String, fa: String, en: String) = JsonObject().apply {
    addProperty("text", text)
    localized("translation", fa, en)
}

private fun table(title: String, en: String, columnNames: List<Pair<String, String>>, vararg rows: List<String>) = JsonObject().apply {
    localized("title", title, en)
    add("columns", JsonArray().apply {
        columnNames.forEach { (fa, en) -> add(JsonObject().apply { addProperty("fa", fa); addProperty("en", en) }) }
    })
    add("rows", JsonArray().apply { rows.forEach { row -> add(JsonArray().apply { row.forEach { cell -> add(cell) } }) } })
}

private val wordColumns = listOf("واژه" to "Word", "تلفظ" to "Pronunciation", "معنی" to "Meaning")
private val patternColumns = listOf("ساختار" to "Pattern", "مثال" to "Example", "کاربرد" to "Usage")
private val phraseColumns = listOf("عبارت" to "Phrase", "معنی" to "Meaning", "موقعیت" to "Context")

private fun word(text: String, reading: String, ipa: String, fa: String, en: String, sample: JsonObject) = JsonObject().apply {
    addProperty("lemma", text)
    addProperty("faReading", reading)
    addProperty("ipa", ipa)
    localized("part", "اسم", "Noun")
    localized("translation", fa, en)
    add("example", sample)
}

private fun grammar(title: String, en: String, body: String, bodyEn: String, sample: JsonObject, table: JsonObject,
    takeaway: String, takeawayEn: String) = JsonObject().apply {
    localized("tag", "گرامر گفت‌وگو", "Dialogue grammar")
    localized("title", title, en)
    localized("body", body, bodyEn)
    localized("takeaway", takeaway, takeawayEn)
    add("example", sample)
    add("tables", JsonArray().apply { add(table) })
}

private fun phrase(text: String, reading: String, fa: String, en: String, context: String, contextEn: String, sample: JsonObject) =
    example(text, fa, en).apply {
        addProperty("faReading", reading)
        localized("context", context, contextEn)
        add("example", sample)
    }

private val teaRequest = example("Bir çay alabilir miyim?", "می‌توانم یک چای بگیرم؟", "May I have a tea?")
private val sugarOffer = example("Şeker ister misiniz?", "شکر می‌خواهید؟", "Would you like sugar?")
private val trainTime = example("Saat üçte kalkıyor.", "ساعت سه حرکت می‌کند.", "It leaves at three o'clock.")
private val trainPlatform = example("İkinci perondan kalkıyor.", "از سکوی دوم حرکت می‌کند.", "It leaves from the second platform.")

internal val cafeLearning = DialogueLearning(
    words = listOf(word("çay", "چای", "/tʃaj/", "چای", "Tea", teaRequest),
        word("şeker", "شِکِر", "/ʃeˈceɾ/", "شکر", "Sugar", sugarOffer)),
    wordTable = table("واژه‌های سفارش در کافه", "Café vocabulary", wordColumns,
        listOf("çay", "/tʃaj/", "چای"), listOf("şeker", "/ʃeˈceɾ/", "شکر")),
    grammar = listOf(
        grammar("درخواست مؤدبانه با ‎-Abil", "Polite requests with -Abil",
            "به بن فعل، پسوند توانایی ‎-abil یا ‎-ebil اضافه می‌شود. در «alabilir miyim?» فعل almak به معنی گرفتن است و ‎miyim پرسشِ اول‌شخص مفرد می‌سازد.",
            "Add -abil or -ebil to the verb stem to express ability. In alabilir miyim?, the verb means to take or get, and miyim marks a first-person question.", teaRequest,
            table("ساخت درخواست", "Making a request", patternColumns,
                listOf("al + abil + ir", "alabilir", "می‌تواند بگیرد"), listOf("alabilir + miyim?", "Bir çay alabilir miyim?", "درخواست اول‌شخص")),
            "جزء پرسشی ‎mi جدا از فعل نوشته می‌شود: alabilir miyim?", "The question particle is written separately from the verb."),
        grammar("پیشنهاد مؤدبانه با ister misiniz?", "Polite offers with ister misiniz?",
            "برای پرسیدن اینکه طرف مقابل چیزی می‌خواهد یا نه، نام آن را پیش از ister misiniz? بیاور. این ساخت برای خطاب محترمانه یا جمع است.",
            "Put the item before ister misiniz? when offering something. This form addresses one person politely or multiple people.", sugarOffer,
            table("خطاب در پیشنهاد", "Forms of address", patternColumns,
                listOf("ister misin?", "Çay ister misin?", "خطاب دوستانه"), listOf("ister misiniz?", "Şeker ister misiniz?", "خطاب مؤدبانه")),
            "‎misiniz برای خطاب محترمانه یا جمع است.", "Use misiniz for polite or plural address.")
    ),
    phrases = listOf(
        phrase("Afiyet olsun!", "آفی‌یِت اُلسون", "نوش جان!", "Enjoy your meal!", "هنگام تعارف یا سرو غذا و نوشیدنی.", "Say it when serving or offering food or drink.", example("Buyurun, afiyet olsun!", "بفرمایید، نوش جان!", "Here you are; enjoy!")),
        phrase("Teşekkür ederim.", "تِشِکّور اِدِریم", "ممنونم.", "Thank you.", "برای تشکر یا رد مؤدبانهٔ پیشنهاد.", "Use it to thank someone or politely decline an offer.", example("Hayır, teşekkür ederim.", "نه، ممنونم.", "No, thank you."))
    ), phraseTable = table("عبارت‌های کاربردی کافه", "Useful café phrases", phraseColumns,
        listOf("Afiyet olsun!", "نوش جان!", "سرو سفارش"), listOf("Teşekkür ederim.", "ممنونم.", "تشکر یا رد پیشنهاد")),
    questions = listOf(DialogueQuestion("الیف چه چیزی سفارش داد؟", listOf("چای", "قهوه", "آب"), 0, "در «bir çay»، واژهٔ çay یعنی چای."),
        DialogueQuestion("کدام عبارت برای پیشنهاد مؤدبانهٔ شکر است؟", listOf("Afiyet olsun!", "Şeker ister misiniz?", "Hayır, teşekkür ederim."), 1,
            "واژهٔ şeker یعنی شکر و ister misiniz? یعنی «می‌خواهید؟»."))
)

internal val stationLearning = DialogueLearning(
    words = listOf(word("tren", "تْرِن", "/tɾen/", "قطار", "Train", example("Tren ne zaman kalkıyor?", "قطار کی حرکت می‌کند؟", "When does the train leave?")),
        word("peron", "پِرون", "/peˈɾon/", "سکوی ایستگاه", "Platform", trainPlatform)),
    wordTable = table("واژه‌های ایستگاه", "Station vocabulary", wordColumns,
        listOf("tren", "/tɾen/", "قطار"), listOf("peron", "/peˈɾon/", "سکو")),
    grammar = listOf(
        grammar("بیان زمان با ‎-DA", "Telling time with -DA",
            "برای گفتن اینکه اتفاقی در ساعت مشخصی رخ می‌دهد، پسوند مکانی ‎-da/-de یا ‎-ta/-te را به عدد اضافه می‌کنیم. در üçte، چون ç بی‌صداست، پسوند با t شروع می‌شود.",
            "Use the locative ending -da/-de or -ta/-te on the hour. In üçte, the voiceless ç calls for a t at the start of the ending.", trainTime,
            table("ساخت ساعت", "Expressing time", patternColumns, listOf("üç + te", "saat üçte", "ساعت سه"),
                listOf("iki + de", "saat ikide", "ساعت دو")), "برای ساعت وقوع از پسوند ‎-DA استفاده کن.", "Use -DA for the time at which something happens."),
        grammar("بیان مبدأ با ‎-DAn", "Expressing origin with -DAn",
            "پسوند مبدأ به معنی «از» به اسم می‌چسبد. در perondan، آخرین واکه o است، پس شکل ‎-dan به کار می‌رود.",
            "Attach the ablative ending to a noun to mean ‘from’. The last vowel in peron is o, so the ending takes the form -dan.", trainPlatform,
            table("پرسیدن از مبدأ حرکت", "Asking about departure", patternColumns,
                listOf("peron + dan", "Hangi perondan?", "از کدام سکو؟"), listOf("ikinci + perondan", "İkinci perondan.", "از سکوی دوم")),
            "برای مبدأ حرکت ‎-DAn و برای زمان آن ‎-DA می‌آید.", "Use -DAn for origin and -DA for time.")
    ),
    phrases = listOf(phrase("Affedersiniz", "آفِدِرسینیز", "ببخشید", "Excuse me", "برای جلب توجه پیش از سؤال.", "Use it to get someone's attention politely.", example("Affedersiniz, tren ne zaman kalkıyor?", "ببخشید، قطار کی حرکت می‌کند؟", "Excuse me, when does the train leave?")),
        phrase("Hangi perondan?", "هانگی پِروندان", "از کدام سکو؟", "From which platform?", "وقتی مبدأ حرکت قطار را می‌پرسی.", "Use it to ask which platform a train leaves from.", trainPlatform)),
    phraseTable = table("عبارت‌های کاربردی ایستگاه", "Useful station phrases", phraseColumns,
        listOf("Affedersiniz", "ببخشید", "شروع سؤال"), listOf("Hangi perondan?", "از کدام سکو؟", "پرسیدن مبدأ حرکت")),
    questions = listOf(DialogueQuestion("قطار چه ساعتی حرکت می‌کند؟", listOf("ساعت دو", "ساعت سه", "ساعت چهار"), 1, "Saat üçte یعنی ساعت سه."),
        DialogueQuestion("پسوند ‎-dan در perondan چه معنایی می‌دهد؟", listOf("در", "به", "از"), 2, "‎-dan پسوند مبدأ است؛ perondan یعنی «از سکو»."))
)

/** Map validated package data into the existing chat and learning cards. */
internal fun com.hydro.reedance.install.InstalledReeTalk.toReader(persian: Boolean): DialogueDemo {
    val d = dialogue
    val speakers = d.a("speakers")
    fun JsonObject.translationOf(target: JsonObject, from: String, to: String = from) {
        val field = obj(from)
        target.localized(to, field.s("fa"), field.s("en"))
    }
    fun JsonObject.exampleCard(): JsonObject {
        val e = obj("example")
        return example(e.s("text"), e.obj("translation").s("fa"), e.obj("translation").s("en"))
    }
    fun JsonObject.copyPronunciation(target: JsonObject) {
        val pronunciation = obj("pronunciation")
        target.addProperty("faReading", pronunciation.s("faReading"))
        target.addProperty("ipa", pronunciation.s("ipa"))
    }
    val selectedLanguage = if(persian) "fa" else "en"
    val learning = d.obj("learning")
    val words = learning.a("words").map { entry -> JsonObject().apply {
        addProperty("lemma", entry.s("text"))
        entry.copyPronunciation(this)
        entry.translationOf(this, "meaning", "translation")
        add("example", entry.exampleCard())
    } }
    val phrases = learning.a("phrases").map { entry -> JsonObject().apply {
        addProperty("text", entry.s("text"))
        entry.copyPronunciation(this)
        entry.translationOf(this, "meaning", "translation")
        entry.translationOf(this, "context")
        add("example", entry.exampleCard())
    } }
    val grammar = learning.a("grammar").map { entry -> JsonObject().apply {
        localized("tag", "گرامر گفت‌وگو", "Dialogue grammar")
        entry.translationOf(this, "title")
        entry.translationOf(this, "explanation", "body")
        entry.translationOf(this, "takeaway")
        add("example", entry.exampleCard())
        add("tables", JsonArray().apply {
            entry.a("tables").forEach { source ->
                add(JsonObject().apply {
                    source.translationOf(this, "title")
                    add("columns", source.getAsJsonArray("columns"))
                    add("rows", JsonArray().apply {
                        source.getAsJsonArray("rows").forEach { row -> add(JsonArray().apply {
                            row.asJsonArray.forEach { cell ->
                                if(cell.asJsonObject.has("text")) add(cell.asJsonObject.s("text")) else add(cell)
                            }
                        }) }
                    })
                })
            }
        })
    } }
    val questions = learning.a("questions").map { q ->
        val options = q.a("options")
        DialogueQuestion(q.obj("prompt").s(selectedLanguage), options.map { it.obj("text").s(selectedLanguage) },
            options.indexOfFirst { it.s("id") == q.s("correctOptionId") }, q.obj("explanation").s(selectedLanguage))
    }
    return DialogueDemo(d.obj("title").s("en"), d.obj("title").s("fa"), speakers.map {
        DialogueSpeaker(it.s("name"), if(it.s("gender") == "woman") DialogueGender.WOMAN else DialogueGender.MAN)
    }, d.a("turns").map { turn ->
        DialogueTurn(speakers.indexOfFirst { it.s("id") == turn.s("speakerId") }, turn.s("text"),
            turn.obj("translation").s(selectedLanguage), audio,
            turn.a("tokens").map { it.obj("gloss").s(selectedLanguage) }, turn.s("id"), turn.n("startMs"), turn.n("endMs"))
    }, DialogueLearning(words,
        table("مرور واژه‌ها", "Vocabulary review", wordColumns, *words.map {
            listOf(it.s("lemma"), it.s("ipa"), it.tr("translation", persian))
        }.toTypedArray()), grammar, phrases,
        table("مرور عبارت‌ها", "Phrase review", phraseColumns, *phrases.map {
            listOf(it.s("text"), it.tr("translation", persian), it.tr("context", persian))
        }.toTypedArray()), questions), com.hydro.reedance.R.drawable.dialogue_art_daily,
        id = d.s("dialogueId"), artworkPath = cover.path)
}
