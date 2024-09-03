package de.voize.reaktnativetoolkit.util

import react_native.*

actual interface ReactNativeViewManagerProvider {
    fun getViewManager(): RCTViewManager
}

fun List<ReactNativeViewManagerProvider>.getViewManagers(): List<RCTViewManager> {
    return map { it.getViewManager() }
}
