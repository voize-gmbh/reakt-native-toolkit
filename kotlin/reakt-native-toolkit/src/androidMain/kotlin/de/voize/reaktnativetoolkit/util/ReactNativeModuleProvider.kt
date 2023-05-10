package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.ReactApplicationContext
import kotlinx.coroutines.CoroutineScope

actual interface ReactNativeModuleProvider {
    fun getModule(
        reactApplicationContext: ReactApplicationContext,
        lifecycleScope: CoroutineScope,
    ): ReactNativeModuleBase
}

fun List<ReactNativeModuleProvider>.getModules(
    reactApplicationContext: ReactApplicationContext,
    lifecycleScope: CoroutineScope,
): List<ReactNativeModuleBase> {
    return map { it.getModule(reactApplicationContext, lifecycleScope) }
}
