package com.hydro.reedance.domain

data class GameWordDefinition(
    val targetLanguage: String,
    val songId: String,
    val lexemeId: String,
    val songTitle: String,
    val lemma: String,
    val letterAnswer: String,
    val translationFa: String,
    val translationEn: String
)
