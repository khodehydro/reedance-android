package com.hydro.reedance.domain

/**
 * Splits available words into balanced rounds of 3–5. Fewer than three words
 * stay pending until another downloaded song contributes more vocabulary.
 */
fun balancedGameLevelSizes(totalWords: Int): List<Int> {
    if(totalWords < 3) return emptyList()
    val levels = (totalWords + 4) / 5
    val base = totalWords / levels
    val remainder = totalWords % levels
    return List(levels) { index -> base + if(index < remainder) 1 else 0 }
}
