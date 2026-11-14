package com.hydro.reedance.media

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/** Lazily created by AppModel. Voice lookup / binder work never blocks Compose. */
class Speech(context: Context) {
    private val initialized = CompletableDeferred<Boolean>()
    private val engine = TextToSpeech(context.applicationContext) { initialized.complete(it == TextToSpeech.SUCCESS) }
    private val mutex = Mutex()
    private val selectedVoices = mutableMapOf<String, Voice>()
    suspend fun say(text: String, locale: String): Boolean {
        if(withTimeoutOrNull(3_000) { initialized.await() } != true) return false
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                ensureActive()
                val requested = Locale.forLanguageTag(locale)
                val voice = selectedVoices[locale] ?: engine.voices
                    ?.filter { it.locale.language == requested.language && !it.isNetworkConnectionRequired }
                    ?.let { available -> available.firstOrNull { it.locale.country == requested.country } ?: available.firstOrNull() }
                    ?.also { selectedVoices[locale] = it } ?: return@withContext false
                engine.voice = voice
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reedance") == TextToSpeech.SUCCESS
            }
        }
    }
    fun close() { initialized.cancel(); engine.stop(); engine.shutdown() }
}
