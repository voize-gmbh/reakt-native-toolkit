package com.myrnproject.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import react_native.RCTBridgeModuleProtocol

class IOSRNModules {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return listOf(
            NameManagerIOS(coroutineScope, PersistentConfig(PersistentConfigInitContext()))
        )
    }
}
