package com.hydro.reedance.domain

/** An unfinished local read is never evidence that a song needs downloading. */
enum class SongEntryState { LOADING, INSTALLED, NOT_INSTALLED, ERROR }
fun songEntryState(available: Boolean, loading: Boolean, failed: Boolean): SongEntryState = when {
    failed -> SongEntryState.ERROR
    available -> SongEntryState.INSTALLED
    loading -> SongEntryState.LOADING
    else -> SongEntryState.NOT_INSTALLED
}
