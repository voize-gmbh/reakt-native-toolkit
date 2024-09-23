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
    message: Flow<String>,
    @ReactNativeProp
    textFieldValue: Flow<String>,
    @ReactNativeProp
    nullableStringProp: Flow<String?>,
    @ReactNativeProp
    boolProp: Flow<Boolean>,
    @ReactNativeProp
    intProp: Flow<Int>,
    @ReactNativeProp
    doubleProp: Flow<Double>,
    @ReactNativeProp
    floatProp: Flow<Float>,
    @ReactNativeProp
    objectProp: Flow<MyDataClass>,
    @ReactNativeProp
    enumProp: Flow<Enum>,
    @ReactNativeProp
    listProp: Flow<List<MyDataClass>>,
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
    ) -> Unit,
    // dependency injection
    persistentConfig: PersistentConfig,
) {
    val name by persistentConfig.getConfigAsFlow("name").collectAsState(null)
    val messageValue by message.collectAsState("initial!")
    val nullableStringValue by nullableStringProp.collectAsState(null)
    val boolValue by boolProp.collectAsState(null)
    val intValue by intProp.collectAsState(null)
    val doubleValue by doubleProp.collectAsState(null)
    val floatValue by floatProp.collectAsState(null)
    val objectValue by objectProp.collectAsState(null)
    val enumValue by enumProp.collectAsState(null)
    val listValue by listProp.collectAsState(emptyList())

    Column {
        Text("Hello from Compose, ${name ?: "unknown"}!")

        listOf(
            "message" to messageValue,
            "nullableStringProp" to nullableStringValue,
            "boolProp" to boolValue,
            "intProp" to intValue,
            "doubleProp" to doubleValue,
            "floatProp" to floatValue,
            "objectProp" to objectValue,
            "enumProp" to enumValue,
            "listProp" to listValue,
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
            )
        }) {
            Text("Click me!")
        }
        TextField(
            value = textFieldValue.collectAsState("").value,
            onValueChange = onTextFieldValueChange,
            label = { Text("This text field is controlled by React Native") },
        )
    }
}
