package com.hydro.reedance.domain

private val lyricLanguageCode = Regex("[a-z]{2,3}")

/** Missing line metadata inherits the song language; text is never used for detection. */
internal fun alternateLyricLanguage(songLanguage: String, lineLanguage: String): String? =
    lineLanguage.takeIf { lyricLanguageCode.matches(songLanguage) && lyricLanguageCode.matches(it) && it != songLanguage }
