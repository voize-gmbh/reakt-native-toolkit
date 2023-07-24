package com.myrnproject.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule

@ReactNativeModule("E2ETest")
class E2ETest {

    @ReactNativeMethod
    fun example(input: TestSealedType, testEnum: Enum?): Test {
        return Test("Erik", listOf(), mapOf(), 30)
    }
}


@Serializable
data class Test(
    val name: String,
    val list: List<Nested>,
    val map: Map<String, Nested>,
    val long: Long,
    @SerialName("bar")
    val foo: Byte = 1,
) {
    @Serializable
    data class Nested(
        val name: String,
        val age: Int
    )
}

@Serializable
sealed class TestSealedType {
    @Serializable
    @SerialName("option1")
    data class Option1(
        val name: String,
        val nested: Nested,
    ) : TestSealedType() {
        @Serializable
        data class Nested(
            val nullable: String?
        )
    }

    @Serializable
    @SerialName("option2")
    data class Option2(
        val number: Int,
        val nonNested: NonNested,
    ) : TestSealedType()

    @Serializable
    @SerialName("option3")
    object Option3 : TestSealedType()
}

@Serializable
data class NonNested(
    val bar: String,
)

@Serializable
enum class Enum {
    Option1,
    OPTION2,
    OPTION_3,
}