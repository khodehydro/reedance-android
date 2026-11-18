package com.hydro.reedance.ui

import com.google.gson.JsonObject
import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.data.LibraryItemContent
import com.hydro.reedance.data.s
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

internal enum class LibraryOrder { NEWEST, OLDEST, SOURCE }

internal data class LibraryRequest(val language: String, val page: Int, val query: String, val songId: String, val order: LibraryOrder)

private val accents = Regex("\\p{M}+")
private val searchBreaks = Regex("[\\s‌\\p{Punct}]+")
private fun searchText(text: String): String = accents.replace(Normalizer.normalize(text, Normalizer.Form.NFKD), "")
    .lowercase(Locale.ROOT).replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
    .replace(searchBreaks, " ").trim()

internal suspend fun prepareLibraryRows(bookmarks: List<Bookmark>, tab: String, request: LibraryRequest,
    catalog: Map<String, JsonObject>, load: suspend (Bookmark) -> LibraryItemContent?): List<Bookmark> {
    val rows = withContext(Dispatchers.Default) {
        bookmarks.filter { matchesLibraryTab(it, tab) && (request.songId.isEmpty() || it.songId == request.songId) }
    }
    val terms = searchText(request.query).split(' ').filter { it.isNotBlank() }
    fun matches(base: String, additional: String = ""): Boolean {
        val haystack = searchText("$base $additional")
        return terms.all { it in haystack }
    }
    val found = if(terms.isEmpty()) rows else buildList {
        // Group reads by song so the per-package cache is not thrashed by date order.
        rows.groupBy { it.songId }.forEach { (songId, marks) ->
            val meta = catalog[songId]
            for(mark in marks) {
                val base = "$songId ${meta?.s("title").orEmpty()} ${meta?.s("artist").orEmpty()} ${mark.itemId}"
                if(matches(base)) {
                    add(mark)
                } else if(mark.itemType != "song") {
                    val content = try { load(mark) }
                    catch(cancelled: CancellationException) { throw cancelled }
                    catch(_: Exception) { null }
                    val body = content?.item?.let { item ->
                        listOf("lemma", "text", "title", "titleFa", "titleEn", "translation", "translationFa", "translationEn",
                            "tagFa", "tagEn", "bodyFa", "bodyEn", "tipFa", "tipEn").joinToString(" ") { item.s(it) }
                    }.orEmpty()
                    if(matches(base, "${content?.title.orEmpty()} $body")) add(mark)
                }
            }
        }
    }
    return withContext(Dispatchers.Default) {
        when(request.order) {
            LibraryOrder.NEWEST -> found.sortedByDescending { it.createdAt }
            LibraryOrder.OLDEST -> found.sortedBy { it.createdAt }
            LibraryOrder.SOURCE -> {
                val names = found.map { it.songId }.distinct().associateWith { searchText(catalog[it]?.s("title").orEmpty().ifBlank { it }) }
                found.sortedWith(compareBy<Bookmark> { names[it.songId] }.thenByDescending { it.createdAt })
            }
        }
    }
}
