package com.myrnproject.shared

import de.voize.reaktnativetoolkit.util.ReactNativeIOSViewWrapperFactory
import de.voize.reaktnativetoolkit.util.getModules
import de.voize.reaktnativetoolkit.util.getViewWrapperFactories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import react_native.RCTBridgeModuleProtocol

class IOSRNModules {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val persistentConfig = PersistentConfig(PersistentConfigInitContext())

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return getReactNativeModuleProviders(
            coroutineScope,
            persistentConfig,
        ).getModules(coroutineScope)
    }

    fun createViewWrapperFactories(): Map<String, ReactNativeIOSViewWrapperFactory> {
        return getReactNativeViewManagerProviders(
            persistentConfig,
        ).getViewWrapperFactories()
    }
}
