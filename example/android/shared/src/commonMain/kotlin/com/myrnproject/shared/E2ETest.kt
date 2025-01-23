package com.myrnproject.shared

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule
import de.voize.reaktnativetoolkit.annotation.ReactNativeFlow
import de.voize.reaktnativetoolkit.annotation.JSType
import de.voize.reaktnativetoolkit.annotation.ExportTypescriptType
import kotlinx.serialization.json.JsonClassDiscriminator

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
    fun testSealedInterface(input: TestSealedInterfaceType): TestSealedInterfaceType {
        return input
    }

    @ReactNativeMethod
    fun testSealedClassProperties(test: TestSealedClassProperties): TestSealedClassProperties {
        return test
    }


    @ReactNativeMethod
    fun testKotlinDateTime(instant: Instant, date: @JSType("date") Instant, localDateTime: LocalDateTime, test: DateTimeTest, dateOrNull: @JSType("date") Instant?): Duration {
        return 5.seconds
    }

    @ReactNativeMethod
    fun getDateTimeTest(): DateTimeTest {
        error("Not implemented")
    }

    @ReactNativeFlow
    fun testFlow(): Flow<Int> {
        return flowOf(1, 2, 3)
    }

    @ReactNativeFlow
    fun testFlowNullable(): Flow<Int?> {
        return flowOf(1, 2, null)
    }

    @ReactNativeFlow
    fun testFlowComplex(): Flow<Test> {
        return flowOf(Test("Erik", listOf(), mapOf(), 30))
    }

    @ReactNativeFlow
    fun testFlowParameterized(arg1: Int): Flow<FlowTest> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowParameterized2(arg1: Int, arg2: String): Flow<FlowTest> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowParameterizedComplex(arg1: Test): Flow<FlowTest> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowParameterizedComplex2(arg1: List<Test>, arg2: Map<String, Test>): Flow<FlowTest> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowParameterizedMany(arg1: Int, arg2: String, arg3: List<String>, arg4: Map<String, Test>): Flow<FlowTest> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowReturnInstant(): Flow<Instant> {
        return flowOf()
    }

    @ReactNativeFlow
    fun testFlowReturnInstantAsDate(): Flow<@JSType("date") Instant> {
        return flowOf()
    }

    @ReactNativeMethod
    fun testTypeAlias(test: TestTypeAlias): TestTypeAlias {
        return test
    }

    @ReactNativeMethod
    fun testSealedSubtype(test: TestSealedType.Option1): TestSealedType.Option1 {
        return test
    }

    @ReactNativeMethod
    fun testSealedCustomDiscriminator(test: TestSealedTypeWithCustomDiscriminator) {

    }

    @ReactNativeMethod
    fun testMapWithEnumKey(map: Map<Enum, String>): Map<Enum, String> {
        return map
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
data class TestSealedClassProperties(
    val sealed: TestSealedType,
    val sealedSubclassStandalone: TestSealedType.Option1,
    val sealedSubclassStandaloneObject: TestSealedType.Option3,
)

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
sealed interface TestSealedInterfaceType {
    val id: Int
    @Serializable
    @SerialName("option1")
    data class Option1(
        override val id: Int,
        val name: String,
        val nested: Nested,
    ) : TestSealedInterfaceType {
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
    ) : TestSealedInterfaceType {
        override val id: Int
            get() = number
    }

    @Serializable
    @SerialName("option3")
    object Option3 : TestSealedInterfaceType {
        override val id: Int
            get() = 3
    }
}

@Serializable
@JsonClassDiscriminator("customType")
sealed class TestSealedTypeWithCustomDiscriminator {
    @Serializable
    @SerialName("option1")
    data class Option1(
        val name: String,
        val nested: Nested,
    ) : TestSealedTypeWithCustomDiscriminator() {
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
    ) : TestSealedTypeWithCustomDiscriminator()

    @Serializable
    @SerialName("option3")
    object Option3 : TestSealedTypeWithCustomDiscriminator()
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

@Serializable
object FlowTest

typealias TestTypeAlias = Test

@Serializable
data class DateTimeTest(
    val instant: Instant,
    val date: @JSType("date") Instant,
    val dateAsString: @JSType("string") Instant,
    val localDateTime: LocalDateTime,
    val duration: Duration,
    val map: Map<String, @JSType("date") Instant>,
    val dateOrNull: @JSType("date") Instant?,
)

@Serializable
@ExportTypescriptType // export typescript type manually
data class ManuallyExportedType(
    val test: String,
    val enum: Enum,
)
