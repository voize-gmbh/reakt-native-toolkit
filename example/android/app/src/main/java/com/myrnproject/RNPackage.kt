package com.myrnproject

import android.view.View
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ReactShadowNode
import com.facebook.react.uimanager.ViewManager
import com.myrnproject.shared.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class RNPackage : ReactPackage {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
            NameManagerAndroid(reactContext, coroutineScope, PersistentConfig(PersistentConfigInitContext(reactContext))),
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<View, ReactShadowNode<*>>> {
        return emptyList()
    }
}