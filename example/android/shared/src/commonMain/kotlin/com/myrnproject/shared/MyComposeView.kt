package com.myrnproject.shared

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager

@Composable
@ReactNativeViewManager("MyComposeView")
internal fun MyComposeView(persistentConfig: PersistentConfig) {
    val name by persistentConfig.getConfigAsFlow("name").collectAsState(null)

    Text("Hello from Compose, ${name ?: "unknown"}!")
}
