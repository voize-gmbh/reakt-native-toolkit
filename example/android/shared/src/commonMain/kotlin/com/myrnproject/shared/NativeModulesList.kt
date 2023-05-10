package com.myrnproject.shared

import de.voize.reaktnativetoolkit.util.ReactNativeModuleProvider
import kotlinx.coroutines.CoroutineScope

fun getNativeModuleProviders(
    lifecycleScope: CoroutineScope,
    persistentConfig: PersistentConfig,
): List<ReactNativeModuleProvider> {
    return listOf(
        NameManagerProvider(persistentConfig),
        NotificationDemoProvider(lifecycleScope),
    )
}