// iosMain/PersistentConfig.kt

package com.myrnproject.shared

import kotlinx.coroutines.flow.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDefaultsDidChangeNotification

actual class PersistentConfigInitContext

actual class PersistentConfig actual constructor(initContext: PersistentConfigInitContext) {
    private val preferences = NSUserDefaults.standardUserDefaults()

    actual fun setConfig(key: String, value: String) {
        preferences.setObject(value, key)
    }

    actual fun getConfig(key: String): String? {
        return preferences.stringForKey(key)
    }

    private val lastChangedAt = NSNotificationCenter.defaultCenter.observerAsFlow(
        name = NSUserDefaultsDidChangeNotification,
        objectRef = preferences,
    ).stateIn(SharedCoroutineScope, SharingStarted.WhileSubscribed(), null)

    actual fun getConfigAsFlow(key: String): Flow<String?> {
        return lastChangedAt.map { getConfig(key) }.conflate().distinctUntilChanged()
    }
}