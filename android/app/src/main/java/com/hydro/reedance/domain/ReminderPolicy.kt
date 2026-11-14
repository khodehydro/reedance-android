package com.hydro.reedance.domain

object ReminderPolicy {
    const val INTERVAL = 12 * 60 * 60 * 1000L
    fun eligible(now: Long, lastVisit: Long, lastReminder: Long): Boolean =
        lastVisit > 0L && now >= lastVisit && now >= lastReminder &&
            now - lastVisit >= INTERVAL && now - lastReminder >= INTERVAL
}
