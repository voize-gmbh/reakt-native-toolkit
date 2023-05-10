package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.CoroutineScope
import react_native.*

actual interface ReactNativeModuleProvider {
    fun getModule(lifecycleScope: CoroutineScope): RCTBridgeModuleProtocol
}

fun List<ReactNativeModuleProvider>.getModules(
    lifecycleScope: CoroutineScope,
): List<RCTBridgeModuleProtocol> {
    return map { it.getModule(lifecycleScope) }
}
