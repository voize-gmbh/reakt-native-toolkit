package com.myrnproject.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager

@Composable
@ReactNativeViewManager("MyTransparentComposeView")
internal fun MyTransparentComposeView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Color.hsl(0f, 0f, 0f, 0.3f)
            )
    ) {
        Text("Compose overlay", color = Color.White)
    }
}
