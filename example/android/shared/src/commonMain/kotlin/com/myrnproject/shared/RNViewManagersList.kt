package com.myrnproject.shared

import de.voize.reaktnativetoolkit.util.ReactNativeViewManagerProvider

fun getReactNativeViewManagerProviders(
    persistentConfig: PersistentConfig,
): List<ReactNativeViewManagerProvider> {
    return listOf(
        MyComposeViewRNViewManagerProvider(persistentConfig),
        MySecondComposeViewRNViewManagerProvider(),
    )
}
