package com.myrnproject

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.myrnproject.shared.PersistentConfig
import com.myrnproject.shared.getReactNativeModuleProviders
import com.myrnproject.shared.getReactNativeViewManagerProviders
import de.voize.reaktnativetoolkit.util.getModules
import de.voize.reaktnativetoolkit.util.getViewManagers
import kotlinx.coroutines.CoroutineScope

class RNPackage(
    private val coroutineScope: CoroutineScope,
    private val persistentConfig: PersistentConfig,
) : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return getReactNativeModuleProviders(
            coroutineScope,
            persistentConfig,
        ).getModules(reactContext, coroutineScope)
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return getReactNativeViewManagerProviders(
            persistentConfig,
        ).getViewManagers()
    }
}
