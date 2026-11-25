package com.hydro.reedance.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Composable internal fun rememberDialoguePreference(key: String, default: Int, range: IntRange): ReadWriteProperty<Any?, Int> {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { context.getSharedPreferences("dialogue_preferences", Context.MODE_PRIVATE) }
    return remember(store, key, default, range) { DialoguePreference(store, key, default, range) }
}

@Composable internal fun rememberDialogueToggle(key: String, default: Boolean): ReadWriteProperty<Any?, Boolean> {
    val value = rememberDialoguePreference(key, if(default) 1 else 0, 0..1)
    return remember(value) {
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value.getValue(thisRef, property) == 1
            override fun setValue(thisRef: Any?, property: KProperty<*>, newValue: Boolean) {
                value.setValue(thisRef, property, if(newValue) 1 else 0)
            }
        }
    }
}

private class DialoguePreference(private val store: SharedPreferences, private val key: String, default: Int,
    private val range: IntRange) : ReadWriteProperty<Any?, Int> {
    private val state = mutableIntStateOf(store.getInt(key, default).takeIf { it in range } ?: default)

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = state.intValue

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        if(value !in range || state.intValue == value) return
        state.intValue = value
        store.edit().putInt(key, value).apply()
    }
}
