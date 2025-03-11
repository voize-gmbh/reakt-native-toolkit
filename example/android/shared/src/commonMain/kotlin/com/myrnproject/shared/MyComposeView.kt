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
    nullableBoolProp: Boolean?,
    @ReactNativeProp
    intProp: Int,
    @ReactNativeProp
    nullableIntProp: Int?,
    @ReactNativeProp
    doubleProp: Double,
    @ReactNativeProp
    floatProp: Float,
    // Nullable Double or Float do not seem to work in the Android RN bridge (but works on iOS)
    // java.lang.RuntimeException: Unrecognized type: class java.lang.Double for method: com.myrnproject.shared.MyComposeViewRNViewManagerAndroid#setNullableDoubleProp
    // java.lang.RuntimeException: Unrecognized type: class java.lang.Float for method: com.myrnproject.shared.MyComposeViewRNViewManagerAndroid#setNullableFloatProp
    //
    // @ReactNativeProp
    // nullableDoubleProp: Double?,
    // @ReactNativeProp
    // nullableFloatProp: Float?,
    @ReactNativeProp
    objectProp: MyDataClass,
    @ReactNativeProp
    nullableObjectProp: MyDataClass?,
    @ReactNativeProp
    enumProp: Enum,
    @ReactNativeProp
    nullableEnumProp: Enum?,
    @ReactNativeProp
    listProp: List<MyDataClass>,
    @ReactNativeProp
    nullableListProp: List<MyDataClass>?,
    @ReactNativeProp
    sealedInterface: TestSealedInterfaceType,
    @ReactNativeProp
    nullableSealedInterface: TestSealedInterfaceType?,
    @ReactNativeProp
    onTextFieldValueChange: (String) -> Unit,
    @ReactNativeProp
    onButtonPress: () -> Unit,
    @ReactNativeProp
    onTestParams: (
        stringParam: String,
        nullableStringParam: String?,
        boolParam: Boolean,
        nullableBoolParam: Boolean?,
        intParam: Int,
        nullableIntParam: Int?,
        doubleParam: Double,
        nullableDoubleParam: Double?,
        floatParam: Float,
        nullableFloatParam: Float?,
        objectParam: MyDataClass,
        nullableObjectParam: MyDataClass?,
        enumParam: Enum,
        nullableEnumParam: Enum?,
        listParam: List<MyDataClass>,
        nullableListParam: List<MyDataClass>?,
        sealedInterfaceParam: TestSealedInterfaceType,
        nullableSealedInterfaceParam: TestSealedInterfaceType?,
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
            "nullableBoolProp" to nullableBoolProp,
            "intProp" to intProp,
            "nullableIntProp" to nullableIntProp,
            "doubleProp" to doubleProp,
            "floatProp" to floatProp,
            "objectProp" to objectProp,
            "nullableObjectProp" to nullableObjectProp,
            "enumProp" to enumProp,
            "nullableEnumProp" to nullableEnumProp,
            "listProp" to listProp,
            "nullableListProp" to nullableListProp,
            "sealedInterfaceProp" to sealedInterface,
            "nullableSealedInterfaceProp" to nullableSealedInterface,
        ).forEach { (propName, propValue) ->
            Text("Value of prop \"$propName\": $propValue")
        }

        Button(onClick = {
            onButtonPress()
            onTestParams(
                "stringParam",
                null,
                true,
                null,
                42,
                null,
                3.14,
                null,
                2.718f,
                null,
                MyDataClass(
                    "stringProp",
                    true,
                    42,
                    3.14,
                    2.718f,
                ),
                null,
                Enum.Option1,
                null,
                listOf(
                    MyDataClass(
                        "stringProp",
                        true,
                        42,
                        3.14,
                        2.718f,
                    ),
                ),
                null,
                TestSealedInterfaceType.Option3,
                null,
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
