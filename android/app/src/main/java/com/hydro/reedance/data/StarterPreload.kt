package com.hydro.reedance.data

import com.google.gson.JsonObject
import kotlinx.coroutines.sync.Mutex

/** Process-local immutable-by-convention snapshots; never retain an Activity/model. */
internal object StarterPreload {
    val loadMutex = Mutex()
    @Volatile var courses: JsonObject? = null
    @Volatile var settings: Settings? = null
    @Volatile var steps: List<StarterProgress>? = null
    data class Snapshot(val courses: JsonObject, val settings: Settings, val steps: List<StarterProgress>)
    fun snapshot(): Snapshot? {
        val courses = courses ?: return null
        val settings = settings ?: return null
        val steps = steps ?: return null
        return Snapshot(courses, settings, steps)
    }
}
