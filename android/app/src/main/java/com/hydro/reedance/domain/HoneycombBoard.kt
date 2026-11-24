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

    private val reservedByCell = wordPaths.flatMap { (word, path) -> path.map { it to word } }.toMap()

    /** Alternate routes may use spare cells, but must leave every other answer intact. */
    fun canUsePath(word: String, path: List<Int>): Boolean =
        word in wordPaths && path.all { reservedByCell[it]?.let { owner -> owner == word } ?: true }

    private fun spellsPlannedWord(planned: List<Int>, path: List<Int>): Boolean =
        path.size == planned.size && path.distinct().size == path.size &&
            path.withIndex().all { (i, cell) -> letters.getOrNull(cell)?.equals(letters[planned[i]], ignoreCase = true) == true } &&
            path.zipWithNext().all { (a, b) -> areHoneycombNeighbors(a, b, columns) }

    /** Accept a correct spelling even on a route that borrows another word's cells.
     * In that case only lock the planned route, so every remaining word stays playable. */
    fun acceptedSolvedPath(word: String, drawn: List<Int>): List<Int>? {
        val planned = wordPaths[word] ?: return null
        if(!spellsPlannedWord(planned, drawn)) return null
        return if(canUsePath(word, drawn)) drawn else planned
    }

    /** Legacy saves may reference fillers that have changed or another answer's cells. */
    fun restoredSolvedPath(word: String, saved: List<Int>): List<Int> =
        acceptedSolvedPath(word, saved) ?: wordPaths[word].orEmpty()
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
    val rows = ((required + columns - 1) / columns).coerceAtLeast(2)
    val cellCount = rows * columns
    val seed = usableWords.joinToString("|") { "${it.songId}:${it.lexemeId}:${it.letterAnswer}" }.hashCode().toLong()
    val random = Random(seed)
    val lengths = usableWords.map { it.letterAnswer.length }
    val slots = variedPaths(lengths, columns, rows, random) ?: run {
        // Guaranteed packing if the bounded random search cannot fit every word.
        val route = buildList {
            val fromRight = random.nextBoolean()
            val fromBottom = random.nextBoolean()
            repeat(columns) { offset ->
                val column = if(fromRight) columns - 1 - offset else offset
                repeat(rows) { step ->
                    val row = if((offset % 2 == 1) xor fromBottom) rows - 1 - step else step
                    add(row * columns + column)
                }
            }
        }
        var cursor = random.nextInt(cellCount - letterCount - (usableWords.size - 1) + 1)
        lengths.map { length ->
            route.subList(cursor, cursor + length).also { cursor += length + 1 }
        }
    }
    val used = usableWords.flatMap { it.letterAnswer.toList() }.mapTo(mutableSetOf()) { it.uppercaseChar().lowercaseChar() }
    val nativeAlphabet = when(usableWords.first().targetLanguage) {
        "tr" -> "abcçdefgğhıijklmnoöprsştuüvyzqwx"
        "es" -> "abcdefghijklmnñopqrstuvwxyzáéíóúü"
        "ko" -> "가각간갈감강개거건게겨고공과구그기나남너노누다달더도두드라리마만머모무미바버보부비사서소수시아어오우이자저조주차카타파하해호후"
        else -> "abcdefghijklmnopqrstuvwxyz"
    }
    val spare = nativeAlphabet.toList().distinctBy { it.uppercaseChar().lowercaseChar() }.filter { it.uppercaseChar().lowercaseChar() !in used }
        .ifEmpty { ('À'..'ɏ').filter { it.isLetter() && it.uppercaseChar().lowercaseChar() !in used }.distinctBy { it.uppercaseChar().lowercaseChar() } }
        .ifEmpty { listOf('·', '•', '◇') }
    // Deal shuffled passes rather than sampling with replacement, so no spare
    // letter repeats until the available alphabet has been used once.
    var shuffled = emptyList<Char>()
    val letters = MutableList(cellCount) { ' ' }
    val paths = usableWords.mapIndexed { index, word ->
        val path = slots[index]
        path.forEachIndexed { letterIndex, cell -> letters[cell] = word.letterAnswer[letterIndex] }
        gameWordKey(word.songId, word.lexemeId) to path
    }.toMap()
    var fillerIndex = 0
    letters.indices.filter { letters[it] == ' ' }.forEach { cell ->
        if(fillerIndex % spare.size == 0) shuffled = spare.shuffled(random)
        letters[cell] = shuffled[fillerIndex++ % spare.size]
    }
    return HoneycombLetterBoard(letters, columns, paths)
}

private fun variedPaths(lengths: List<Int>, columns: Int, rows: Int, random: Random): List<List<Int>>? {
    val cells = 0 until columns * rows
    val neighbors = cells.map { cell ->
        val row = cell / columns
        val column = cell % columns
        ((row - 1).coerceAtLeast(0)..(row + 1).coerceAtMost(rows - 1)).flatMap { r ->
            ((column - 1).coerceAtLeast(0)..(column + 1).coerceAtMost(columns - 1)).map { c -> r * columns + c }
        }.filter { areHoneycombNeighbors(cell, it, columns) }
    }
    repeat(16) {
        val occupied = BooleanArray(cells.count())
        val paths = mutableMapOf<Int, List<Int>>()
        for(wordIndex in lengths.indices.shuffled(random).sortedByDescending { lengths[it] }) {
            val length = lengths[wordIndex]
            var chosen: List<Int>? = null
            for(attempt in 0 until 24) {
                val start = cells.filterNot { occupied[it] }.randomOrNull(random) ?: break
                val path = mutableListOf(start)
                while(path.size < length) {
                    val next = neighbors[path.last()].filterNot { occupied[it] || it in path }.randomOrNull(random) ?: break
                    path.add(next)
                }
                if(path.size == length && (length == 1 || path.any { it / columns != start / columns })) {
                    chosen = path
                    break
                }
            }
            if(chosen == null) break
            paths[wordIndex] = chosen
            chosen.forEach { occupied[it] = true }
        }
        if(paths.size == lengths.size) return lengths.indices.map { paths.getValue(it) }
    }
    return null
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
