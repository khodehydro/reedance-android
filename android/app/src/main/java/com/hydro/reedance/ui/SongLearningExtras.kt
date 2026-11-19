package com.hydro.reedance.ui

import androidx.compose.runtime.Composable
import com.google.gson.JsonObject
import com.hydro.reedance.data.*

/** Only supplied text is shown; no guessed pronunciation or device TTS. */
@Composable internal fun WrittenPronunciation(item: JsonObject) {
    val fa = LearningLanguage.persian
    val readings = listOf(item.s("ipa"), item.s("reading"), if(fa) item.s("faReading") else "")
        .filter { it.isNotBlank() }.distinct()
    readings.forEach { reading ->
        if(reading == item.s("faReading")) PersianText(reading)
        else Foreign(reading)
    }
}
