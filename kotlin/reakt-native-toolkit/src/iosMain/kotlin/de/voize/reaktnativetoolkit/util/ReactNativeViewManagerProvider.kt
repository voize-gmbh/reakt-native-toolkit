package de.voize.reaktnativetoolkit.util

actual interface ReactNativeViewManagerProvider {
    fun getViewManager(): Pair<String, ReactNativeIOSViewManager>
}

fun List<ReactNativeViewManagerProvider>.getViewManagers(): Map<String, ReactNativeIOSViewManager> {
    return associate { it.getViewManager() }
}
