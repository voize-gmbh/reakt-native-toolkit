package com.myrnproject.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.voize.reaktnativetoolkit.annotation.ReactNativeProp
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class MyDataClass(
    val stringProp: String,
    val boolProp: Boolean,
    val intProp: Int,
    val doubleProp: Double,
    val floatProp: Float,
)

@Composable
@ReactNativeViewManager("MyComposeView")
internal fun MyComposeView(
    @ReactNativeProp
    message: String,
    @ReactNativeProp
    textFieldValue: String,
    @ReactNativeProp
    nullableStringProp: String?,
    @ReactNativeProp
    boolProp: Boolean,
    @ReactNativeProp
    intProp: Int,
    @ReactNativeProp
    doubleProp: Double,
    @ReactNativeProp
    floatProp: Float,
    @ReactNativeProp
    objectProp: MyDataClass,
    @ReactNativeProp
    enumProp: Enum,
    @ReactNativeProp
    listProp: List<MyDataClass>,
    @ReactNativeProp
    sealedInterface: TestSealedInterfaceType,
    @ReactNativeProp
    onTextFieldValueChange: (String) -> Unit,
    @ReactNativeProp
    onButtonPress: () -> Unit,
    @ReactNativeProp
    onTestParams: (
        stringParam: String,
        nullableStringParam: String?,
        boolParam: Boolean,
        intParam: Int,
        doubleParam: Double,
        floatParam: Float,
        objectParam: MyDataClass,
        enumParam: Enum,
        listParam: List<MyDataClass>,
        sealedInterfaceParam: TestSealedInterfaceType,
    ) -> Unit,
    // dependency injection
    persistentConfig: PersistentConfig,
) {
    val name by persistentConfig.getConfigAsFlow("name").collectAsState(null)

    Column {
        Text("Hello from Compose, ${name ?: "unknown"}!")

        listOf(
            "message" to message,
            "nullableStringProp" to nullableStringProp,
            "boolProp" to boolProp,
            "intProp" to intProp,
            "doubleProp" to doubleProp,
            "floatProp" to floatProp,
            "objectProp" to objectProp,
            "enumProp" to enumProp,
            "listProp" to listProp,
        ).forEach { (propName, propValue) ->
            Text("Value of prop \"$propName\": $propValue")
        }

        Button(onClick = {
            onButtonPress()
            onTestParams(
                "stringParam",
                null,
                true,
                42,
                3.14,
                2.718f,
                MyDataClass(
                    "stringProp",
                    true,
                    42,
                    3.14,
                    2.718f,
                ),
                Enum.Option1,
                listOf(
                    MyDataClass(
                        "stringProp",
                        true,
                        42,
                        3.14,
                        2.718f,
                    ),
                ),
                TestSealedInterfaceType.Option3,
            )
        }) {
            Text("Click me!")
        }
        TextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            label = { Text("This text field is controlled by React Native") },
        )
    }
}
