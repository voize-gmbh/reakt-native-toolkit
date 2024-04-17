package com.myrnproject.shared

import de.voize.reaktnativetoolkit.util.getModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import react_native.RCTBridgeModuleProtocol

class IOSRNModules {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return getNativeModuleProviders(
            coroutineScope,
            PersistentConfig(PersistentConfigInitContext())
        ).getModules(coroutineScope)
    }
}
