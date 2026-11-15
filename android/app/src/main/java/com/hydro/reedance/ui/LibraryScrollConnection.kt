package com.hydro.reedance.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/** Observe actual list movement, never raw drag/overscroll distance. */
internal class LibraryScrollConnection(private val threshold: Float, private val onCompact: (Boolean) -> Unit) : NestedScrollConnection {
    private var distance = 0f
    fun reset() { distance = 0f; onCompact(false) }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if(source != NestedScrollSource.UserInput) return Offset.Zero
        val dy = consumed.y
        if(dy == 0f) {
            distance = 0f
            if(available.y > 0f) onCompact(false)
            return Offset.Zero
        }
        if(distance * dy < 0f) distance = 0f
        distance += dy
        if(abs(distance) >= threshold) {
            onCompact(distance < 0f)
            distance = 0f
        }
        return Offset.Zero
    }
}
