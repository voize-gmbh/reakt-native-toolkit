package com.myrnproject.shared

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager

@Composable
@ReactNativeViewManager("MyMinimalComposeView")
internal fun MyMinimalComposeView() {
    Text("Hello from Compose")
}
