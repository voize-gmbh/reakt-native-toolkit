package com.myrnproject

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager
import com.myrnproject.shared.*
import de.voize.reaktnativetoolkit.util.getModules
import kotlinx.coroutines.CoroutineScope

class RNPackage(private val coroutineScope: CoroutineScope) : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return getNativeModuleProviders(
            coroutineScope,
            PersistentConfig(PersistentConfigInitContext(reactContext))
        ).getModules(reactContext, coroutineScope)
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<View, ReactShadowNode<*>>> {
        return emptyList()
    }
}
