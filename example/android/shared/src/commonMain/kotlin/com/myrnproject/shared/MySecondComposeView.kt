package com.myrnproject.shared

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.voize.reaktnativetoolkit.annotation.ReactNativeProp
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager

@Composable
@ReactNativeViewManager("MySecondComposeView")
internal fun MySecondComposeView(
    @ReactNativeProp
    index: Int,
    @ReactNativeProp
    onPress: () -> Unit,
) {
    Button(onClick = onPress) {
        Text("Compose View Index: $index")
    }
}
