package com.hydro.reedance.domain

import kotlin.math.abs
import kotlin.random.Random

/** Letters laid out as disjoint, connected word paths on a pointy-hex grid. */
data class HoneycombLetterBoard(
    val letters: List<Char>,
    val columns: Int,
    val wordPaths: Map<String, List<Int>>
) {
    val rows: Int get() = letters.size / columns
}

fun gameWordKey(songId: String, lexemeId: String): String = "$songId::$lexemeId"

/**
 * Places every answer as a continuous path in a single deterministic honeycomb.
 * The remaining cells are seeded distractor letters; no solution path overlaps.
 */
fun buildHoneycombBoard(words: List<GameWordDefinition>, columns: Int = 7): HoneycombLetterBoard {
    require(columns >= 2)
    val usableWords = words.filter { it.letterAnswer.isNotBlank() }
    if(usableWords.isEmpty()) return HoneycombLetterBoard(emptyList(), columns, emptyMap())

    val letterCount = usableWords.sumOf { it.letterAnswer.length }
    val distractors = maxOf(8, (letterCount * .18).toInt())
    val required = letterCount + (usableWords.size - 1) + distractors
    val rows = (required + columns - 1) / columns
    val cellCount = rows * columns
    val route = buildList {
        repeat(rows) { row ->
            if(row % 2 == 0) addAll((0 until columns).map { row * columns + it })
            else addAll((columns - 1 downTo 0).map { row * columns + it })
        }
    }

    val seed = usableWords.joinToString("|") { "${it.songId}:${it.lexemeId}:${it.letterAnswer}" }.hashCode().toLong()
    val random = Random(seed)
    val alphabet = usableWords.flatMap { it.letterAnswer.toList() }
    val letters = MutableList(cellCount) { alphabet[random.nextInt(alphabet.size)] }
    val paths = linkedMapOf<String, List<Int>>()
    var cursor = 0
    usableWords.forEachIndexed { wordIndex, word ->
        val path = word.letterAnswer.map { letter ->
            val index = route[cursor++]
            letters[index] = letter
            index
        }
        paths[gameWordKey(word.songId, word.lexemeId)] = path
        // An unused cell separates adjacent answer paths in the snake route.
        if(wordIndex < usableWords.lastIndex) cursor++
    }
    return HoneycombLetterBoard(letters, columns, paths)
}

/** Neighbor test for staggered, pointy-top hexagons arranged in offset rows. */
fun areHoneycombNeighbors(first: Int, second: Int, columns: Int): Boolean {
    if(first < 0 || second < 0 || columns < 1) return false
    val firstRow = first / columns
    val secondRow = second / columns
    val firstColumn = first % columns
    val secondColumn = second % columns
    val rowDelta = abs(firstRow - secondRow)
    if(rowDelta == 0) return abs(firstColumn - secondColumn) == 1
    if(rowDelta != 1) return false
    val firstX = firstColumn + if(firstRow % 2 == 1) .5 else 0.0
    val secondX = secondColumn + if(secondRow % 2 == 1) .5 else 0.0
    return abs(firstX - secondX) == .5
}
