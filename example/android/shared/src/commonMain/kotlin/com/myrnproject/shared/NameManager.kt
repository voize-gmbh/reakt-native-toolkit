// commonMain/NameManager.kt

package com.myrnproject.shared

import de.voize.reaktnativetoolkit.annotation.ReactNativeFlow
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule
import kotlinx.coroutines.flow.Flow

@ReactNativeModule("NameManager")
class NameManager(private val persistentConfig: PersistentConfig) {
    private val validNames = listOf("Erik", "Leon")
    private val persistentConfigKey = "name"

    @ReactNativeMethod
    fun setName(name: String) {
        if (name !in validNames) {
            throw IllegalArgumentException("Name \"$name\" is not valid")
        }
        persistentConfig.setConfig(persistentConfigKey, name)
    }

    @ReactNativeMethod
    fun getName(): String? {
        return persistentConfig.getConfig(persistentConfigKey)
    }

    private val nameAsFlow = persistentConfig.getConfigAsFlow(persistentConfigKey)

   @ReactNativeFlow
   suspend fun name() = nameAsFlow
}

expect class PersistentConfigInitContext

expect class PersistentConfig(initContext: PersistentConfigInitContext) {
    fun setConfig(key: String, value: String)

    fun getConfig(key: String): String?

    fun getConfigAsFlow(key: String): Flow<String?>
}

