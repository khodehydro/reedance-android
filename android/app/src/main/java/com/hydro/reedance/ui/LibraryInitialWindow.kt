package com.hydro.reedance.ui

import com.hydro.reedance.data.Bookmark
import com.hydro.reedance.data.LibraryItemContent
import kotlinx.coroutines.CancellationException

/** Resolve a bounded first window before publishing rows: no per-row placeholder height cascade. */
internal suspend fun libraryInitialWindow(
    entries: List<Bookmark>,
    load: suspend (Bookmark) -> LibraryItemContent?
): Map<Bookmark, LibraryItemContent?> = buildMap {
    for(mark in entries.take(4)) {
        if(mark.itemType == "song") continue
        put(mark, try { load(mark) }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { null })
    }
}

internal data class PreparedLibraryEntries(
    val key: LibraryRequest,
    val entries: List<Bookmark>,
    val initialWindow: Map<Bookmark, LibraryItemContent?>
)
