package com.hydro.reedance.media

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
class Speech(context: Context) {
    private var ready = false
    private val engine = TextToSpeech(context) { ready = it == TextToSpeech.SUCCESS }
    fun say(text: String, locale: String): Boolean {
        if (!ready) return false
        val lang = Locale.forLanguageTag(locale)
        val voice = engine.voices?.firstOrNull { it.locale.language == lang.language && !it.isNetworkConnectionRequired } ?: return false
        engine.voice = voice
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reedance") == TextToSpeech.SUCCESS
    }
    fun close() { engine.stop(); engine.shutdown() }
}
