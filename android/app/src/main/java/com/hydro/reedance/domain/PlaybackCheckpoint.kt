package com.hydro.reedance.domain

/** Pure policy: no repeated writes while paused, periodic saves bounded to every 5 seconds. */
class PlaybackCheckpoint {
    private var lastPosition: Long? = null
    private var lastSavedAt = Long.MIN_VALUE
    fun shouldSave(positionMs: Long, nowElapsedMs: Long, force: Boolean): Boolean {
        val previous = lastPosition
        if(previous != null && kotlin.math.abs(positionMs - previous) < 500) return false
        if(!force && previous != null && nowElapsedMs - lastSavedAt < 5_000) return false
        lastPosition = positionMs
        lastSavedAt = nowElapsedMs
        return true
    }
}
