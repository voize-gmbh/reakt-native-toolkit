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

class RNPackage(private val coroutineScope: CoroutineScope) : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
            NameManagerAndroid(reactContext, coroutineScope, PersistentConfig(PersistentConfigInitContext(reactContext))),
                NotificationDemoAndroid(reactContext, coroutineScope, coroutineScope)
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<View, ReactShadowNode<*>>> {
        return emptyList()
    }
}