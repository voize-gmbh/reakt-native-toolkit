package com.myrnproject.shared

import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.voize.reaktnativetoolkit.annotation.ReactNativeProp
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager
import kotlinx.coroutines.flow.Flow

@Composable
@ReactNativeViewManager("MySecondComposeView")
internal fun MySecondComposeView(
    @ReactNativeProp
    index: Flow<Int>,
    @ReactNativeProp
    onPress: () -> Unit,
) {
    val indexValue by index.collectAsState(null)

    Button(onClick = onPress) {
        Text("Compose View Index: $indexValue")
    }
}
