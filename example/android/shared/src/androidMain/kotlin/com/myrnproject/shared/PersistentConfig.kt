// androidMain/NameManager.kt

package com.myrnproject.shared

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

actual class PersistentConfigInitContext(val androidContext: Context)

actual class PersistentConfig actual constructor(initContext: PersistentConfigInitContext) {
    private val preferences = initContext.androidContext.getSharedPreferences("com.myrnproject.shared", Context.MODE_PRIVATE)

    actual fun setConfig(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    actual fun getConfig(key: String): String? {
        return preferences.getString(key, null)
    }

    private fun getChanges(key: String): Flow<String?> {
        return callbackFlow {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key: String? -> trySend(key) }
            preferences.registerOnSharedPreferenceChangeListener(listener)
            awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
        }.filter { it == key || it == null }
    }

    actual fun getConfigAsFlow(key: String): Flow<String?> {
        return getChanges(key).onStart { emit("trigger first load") }
            .map { getConfig(key) }.conflate().distinctUntilChanged()
    }
}