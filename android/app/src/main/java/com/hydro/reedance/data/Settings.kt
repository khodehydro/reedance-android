package com.hydro.reedance.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
private val Context.settingsStore by preferencesDataStore("settings")
data class Settings(val language: String = "", val theme: String = "system", val textSize: Int = 1, val reduceMotion: Boolean = false, val rating: Int = 0)
class SettingsRepository(private val context: Context) {
    private val language = stringPreferencesKey("language")
    private val theme = stringPreferencesKey("theme")
    private val size = intPreferencesKey("text_size")
    private val motion = booleanPreferencesKey("reduce_motion")
    private val rating = intPreferencesKey("rating")
    val flow = context.settingsStore.data.map { Settings(it[language] ?: "", it[theme] ?: "system", it[size] ?: 1, it[motion] ?: false, it[rating] ?: 0) }
    suspend fun language(value: String) { require(value in listOf("en", "fa")); context.settingsStore.edit { it[language] = value } }
    suspend fun theme(value: String) { context.settingsStore.edit { it[theme] = value } }
    suspend fun size(value: Int) { context.settingsStore.edit { it[size] = value.coerceIn(0, 2) } }
    suspend fun motion(value: Boolean) { context.settingsStore.edit { it[motion] = value } }
    suspend fun rating(value: Int) { context.settingsStore.edit { it[rating] = value.coerceIn(0, 5) } }
}
