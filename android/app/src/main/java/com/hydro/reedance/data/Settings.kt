package com.hydro.reedance.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
private val Context.settingsStore by preferencesDataStore("settings")
data class Settings(val language: String = "en", val theme: String = "dark", val textSize: Int = 1, val reduceMotion: Boolean = false, val rating: Int = 0, val targetLanguage: String = "en", val reminders: Boolean = false, val permissionsSetup: Boolean = false, val lastVisit: Long = 0L, val lastReminder: Long = 0L, val lyricsOverlay: Boolean = false, val lyricsOverlayMode: Int = 0)
class SettingsRepository(private val context: Context) {
    private val lyricsOverlay = booleanPreferencesKey("lyrics_overlay")
    private val lyricsOverlayMode = intPreferencesKey("lyrics_overlay_mode")
    private val reminders = booleanPreferencesKey("reminders")
    private val setup = booleanPreferencesKey("permissions_setup")
    private val visit = longPreferencesKey("last_visit")
    private val sent = longPreferencesKey("last_reminder")
    private val language = stringPreferencesKey("language")
    private val targetLanguage = stringPreferencesKey("target_language")
    private val theme = stringPreferencesKey("theme")
    private val size = intPreferencesKey("text_size")
    private val motion = booleanPreferencesKey("reduce_motion")
    private val rating = intPreferencesKey("rating")
    val flow = context.settingsStore.data.map { Settings(it[language]?.takeIf { code -> code == "fa" } ?: "en", if(it[theme] == "light") "light" else "dark", it[size] ?: 1, false, it[rating] ?: 0, it[targetLanguage]?.takeIf { code -> code in listOf("en", "es", "ko", "tr") } ?: "en", it[reminders] ?: false, it[setup] ?: false, it[visit] ?: 0L, it[sent] ?: 0L, it[lyricsOverlay] ?: false, (it[lyricsOverlayMode] ?: 0).coerceIn(0, 2)) }
    suspend fun lyricsOverlay(value: Boolean) { context.settingsStore.edit { it[lyricsOverlay] = value } }
    suspend fun lyricsOverlayMode(value: Int) { context.settingsStore.edit { it[lyricsOverlayMode] = value.coerceIn(0, 2) } }
    suspend fun reminders(value: Boolean) { context.settingsStore.edit { it[reminders] = value } }
    suspend fun completeSetup() { context.settingsStore.edit { it[setup] = true } }
    suspend fun visited() { context.settingsStore.edit { it[visit] = System.currentTimeMillis() } }
    suspend fun reminded(now: Long) { context.settingsStore.edit { it[sent] = now } }
    suspend fun language(value: String) { require(value in listOf("en", "fa")); context.settingsStore.edit { it[language] = value } }
    suspend fun targetLanguage(value: String) { require(value in listOf("en", "es", "ko", "tr")); context.settingsStore.edit { it[targetLanguage] = value } }
    suspend fun theme(value: String) { require(value in listOf("light", "dark")); context.settingsStore.edit { it[theme] = value } }
    suspend fun size(value: Int) { context.settingsStore.edit { it[size] = value.coerceIn(0, 2) } }
    suspend fun motion(value: Boolean) { context.settingsStore.edit { it[motion] = value } }
    suspend fun rating(value: Int) { context.settingsStore.edit { it[rating] = value.coerceIn(0, 5) } }
}
