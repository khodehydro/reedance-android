package com.hydro.reedance.data

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

private val Context.activityStore by preferencesDataStore("daily_activity")
const val ACTIVE_DAY_MILLIS = 120_000L

/** Local civil-day ledger; cumulative resumed time, independent of playback. */
class ActivityRepository(private val context: Context) {
    val days = context.activityStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            key.name.removePrefix("day_").toLongOrNull()?.let { it to (value as Long) }
        }.toMap()
    }
    suspend fun add(start: Long, duration: Long, zone: ZoneId = ZoneId.systemDefault()) {
        if(duration <= 0L) return
        context.activityStore.edit { prefs ->
            var cursor = start
            var remaining = duration
            while(remaining > 0L) {
                val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val part = minOf(remaining, (next - cursor).coerceAtLeast(1L))
                val key = longPreferencesKey("day_${date.toEpochDay()}")
                prefs[key] = ((prefs[key] ?: 0L) + part).coerceAtMost(ACTIVE_DAY_MILLIS)
                cursor += part
                remaining -= part
            }
        }
    }
    suspend fun complete() {
        val day = java.time.LocalDate.now().toEpochDay()
        context.activityStore.edit { it[longPreferencesKey("day_$day")] = ACTIVE_DAY_MILLIS }
    }
}
