package de.voize.reaktnativetoolkit.util

actual interface ReactNativeViewManagerProvider {
    fun getViewWrapperFactory(): Pair<String, ReactNativeIOSViewWrapperFactory>
}

fun List<ReactNativeViewManagerProvider>.getViewWrapperFactories(): Map<String, ReactNativeIOSViewWrapperFactory> {
    return associate { it.getViewWrapperFactory() }
}
