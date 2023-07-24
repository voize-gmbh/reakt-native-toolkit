package com.myrnproject.shared

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule

@ReactNativeModule("E2ETest")
class E2ETest {

    @ReactNativeMethod
    fun testDefaultTypes(
        string: String,
        int: Int,
        long: Long,
        float: Float,
        double: Double,
        boolean: Boolean,
        byte: Byte,
        char: Char,
        short: Short,
    ): String {
        return "Hello World"
    }

    @ReactNativeMethod
    fun testDefaultTypesNullable(
        string: String?,
        int: Int?,
        long: Long?,
        float: Float?,
        double: Double?,
        boolean: Boolean?,
        byte: Byte?,
        char: Char?,
        short: Short?,
    ): String? {
        return null
    }

    @ReactNativeMethod
    fun testListAndMap(
        list: List<String>,
        map: Map<String, String>,
        nestedList: List<List<String>>,
        nestedMap: Map<String, Map<String, String>>,
        complexList: List<Test>,
        complexMap: Map<String, Test>,
    ): List<Int> {
        return emptyList()
    }

    @ReactNativeMethod
    fun testListAndMapNullable(
        list: List<String>?,
        map: Map<String, String>?,
        nestedList: List<List<String>>?,
        nestedMap: Map<String, Map<String, String>>?,
        complexList: List<Test>?,
        complexMap: Map<String, Test>?,
        listNullable: List<String?>,
        mapNullable: Map<String, String?>,
        nestedListNullable: List<List<String?>?>,
        nestedMapNullable: Map<String, Map<String, String?>?>,
        complexListNullable: List<Test?>,
        complexMapNullable: Map<String, Test?>,
    ): List<Int?> {
        return emptyList()
    }

    @ReactNativeMethod
    fun example(input: TestSealedType, testEnum: Enum?): Test {
        return Test("Erik", listOf(), mapOf(), 30)
    }


    @ReactNativeMethod
    fun testKotlinDateTime(instant: Instant, localDateTime: LocalDateTime): Duration {
        return 5.seconds
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