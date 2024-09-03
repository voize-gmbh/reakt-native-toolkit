package de.voize.reaktnativetoolkit.util

import com.facebook.react.uimanager.ViewManager

actual interface ReactNativeViewManagerProvider {
    fun getViewManager(): ViewManager<*, *>
}

fun List<ReactNativeViewManagerProvider>.getViewManagers(): List<ViewManager<*, *>> {
    return map { it.getViewManager() }
}
