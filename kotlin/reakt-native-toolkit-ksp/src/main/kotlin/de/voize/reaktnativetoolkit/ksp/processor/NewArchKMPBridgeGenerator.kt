package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

internal class NewArchKMPBridgeGenerator(
    private val codeGenerator: CodeGenerator,
    private val config: NewArchConfig,
    private val logger: KSPLogger,
) {
    private val coroutineScopeClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
    private val supervisorJobClassName = ClassName("kotlinx.coroutines", "SupervisorJob")
    private val dispatchersClassName = ClassName("kotlinx.coroutines", "Dispatchers")
    private val launchMember = MemberName("kotlinx.coroutines", "launch")
    private val jsonClassName = ClassName("kotlinx.serialization.json", "Json")
    private val decodeFromStringMember = MemberName("kotlinx.serialization", "decodeFromString")
    private val encodeToStringMember = MemberName("kotlinx.serialization", "encodeToString")

    private val onSuccessType = LambdaTypeName.get(
        parameters = arrayOf(ClassName("kotlin", "Any").copy(nullable = true)),
        returnType = UNIT
    )
    private val onErrorType = LambdaTypeName.get(
        parameters = arrayOf(STRING),
        returnType = UNIT
    )

    fun generate(rnModule: ReactNativeModuleGenerator.RNModule) {
        val packageName = rnModule.wrappedClassDeclaration.packageName.asString()
        val moduleName = rnModule.moduleName
        val wrappedClassName = rnModule.wrappedClassDeclaration.simpleName.asString()
        val bridgeObjectName = "${moduleName}Bridge"
        val isInternal = rnModule.isInternal

        val bridgeObject = buildBridgeObject(
            moduleName = moduleName,
            wrappedClassName = wrappedClassName,
            packageName = packageName,
            methods = rnModule.reactNativeMethods,
            isInternal = isInternal,
        )

        val topLevelFunctions = rnModule.reactNativeMethods.map { method ->
            buildTopLevelFunction(bridgeObjectName, method, isInternal)
        }

        val fileSpec = FileSpec.builder(packageName, bridgeObjectName)
            .addType(bridgeObject)
            .apply {
                topLevelFunctions.forEach { addFunction(it) }
                rnModule.wrappedClassDeclaration.containingFile?.let {
                }
            }
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun buildBridgeObject(
        moduleName: String,
        wrappedClassName: String,
        packageName: String,
        methods: List<KSFunctionDeclaration>,
        isInternal: Boolean,
    ): TypeSpec {
        val bridgeObjectName = "${moduleName}Bridge"

        return TypeSpec.objectBuilder(bridgeObjectName).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            addProperty(
                PropertySpec.builder("scope", coroutineScopeClassName)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(
                        "%T(%T() + %T.Default)",
                        coroutineScopeClassName,
                        supervisorJobClassName,
                        dispatchersClassName
                    )
                    .build()
            )

            addProperty(
                PropertySpec.builder("json", jsonClassName)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T { ignoreUnknownKeys = true }", jsonClassName)
                    .build()
            )

            addProperty(
                PropertySpec.builder("wrappedModule", ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE)
                    .delegate(
                        buildCodeBlock {
                            add("lazy { %T() }", ClassName(packageName, wrappedClassName))
                        }
                    )
                    .build()
            )

            methods.forEach { method ->
                addFunction(buildBridgeMethod(method))
            }
        }.build()
    }

    private fun buildBridgeMethod(method: KSFunctionDeclaration): FunSpec {
        val methodName = method.simpleName.asString()
        val parameters = method.parameters
        val isSuspend = method.modifiers.contains(Modifier.SUSPEND)
        val returnType = method.returnType?.toTypeName()
        val hasReturnValue = returnType != null && returnType != UNIT

        return FunSpec.builder(methodName).apply {
            parameters.forEach { param ->
                val paramName = param.name?.asString() ?: "param"
                val paramType = param.type.toTypeName()

                val bridgeParamName = if (isComplexType(paramType)) {
                    "json${paramName.replaceFirstChar { it.uppercase() }}"
                } else {
                    paramName
                }

                val bridgeParamType = if (isComplexType(paramType)) STRING else paramType

                addParameter(bridgeParamName, bridgeParamType)
            }

            addParameter(
                ParameterSpec.builder("onSuccess", onSuccessType).build()
            )
            addParameter(
                ParameterSpec.builder("onError", onErrorType).build()
            )

            addCode(buildCodeBlock {
                addStatement("scope.%M {", launchMember)
                indent()
                addStatement("try {")
                indent()

                parameters.forEach { param ->
                    val paramName = param.name?.asString() ?: "param"
                    val paramType = param.type.toTypeName()

                    if (isComplexType(paramType)) {
                        val bridgeParamName = "json${paramName.replaceFirstChar { it.uppercase() }}"
                        addStatement(
                            "val %L = json.%M<%T>(%L)",
                            paramName,
                            decodeFromStringMember,
                            paramType,
                            bridgeParamName
                        )
                    }
                }

                val paramList = parameters.joinToString(", ") { param ->
                    param.name?.asString() ?: "param"
                }

                if (hasReturnValue) {
                    addStatement("val result = wrappedModule.%L(%L)", methodName, paramList)
                    val resolvedReturnType = method.returnType?.toTypeName()
                    if (resolvedReturnType != null && isComplexType(resolvedReturnType)) {
                        addStatement("onSuccess(json.%M(result))", encodeToStringMember)
                    } else {
                        addStatement("onSuccess(result)")
                    }
                } else {
                    addStatement("wrappedModule.%L(%L)", methodName, paramList)
                    addStatement("onSuccess(null)")
                }

                unindent()
                addStatement("} catch (e: Exception) {")
                indent()
                addStatement("onError(e.message ?: %S)", "Unknown error")
                unindent()
                addStatement("}")
                unindent()
                addStatement("}")
            })
        }.build()
    }

    private fun buildTopLevelFunction(
        bridgeObjectName: String,
        method: KSFunctionDeclaration,
        isInternal: Boolean,
    ): FunSpec {
        val methodName = method.simpleName.asString()
        val functionName = "${methodName}FromJS"
        val parameters = method.parameters

        return FunSpec.builder(functionName).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            parameters.forEach { param ->
                val paramName = param.name?.asString() ?: "param"
                val paramType = param.type.toTypeName()

                val bridgeParamName = if (isComplexType(paramType)) {
                    "json${paramName.replaceFirstChar { it.uppercase() }}"
                } else {
                    paramName
                }

                val bridgeParamType = if (isComplexType(paramType)) STRING else paramType

                addParameter(bridgeParamName, bridgeParamType)
            }

            addParameter(
                ParameterSpec.builder("onSuccess", onSuccessType).build()
            )
            addParameter(
                ParameterSpec.builder("onError", onErrorType).build()
            )

            val paramList = buildList {
                parameters.forEach { param ->
                    val paramName = param.name?.asString() ?: "param"
                    val paramType = param.type.toTypeName()

                    if (isComplexType(paramType)) {
                        add("json${paramName.replaceFirstChar { it.uppercase() }}")
                    } else {
                        add(paramName)
                    }
                }
                add("onSuccess")
                add("onError")
            }.joinToString(", ")

            addStatement("%L.%L(%L)", bridgeObjectName, methodName, paramList)
        }.build()
    }

    private fun isComplexType(type: com.squareup.kotlinpoet.TypeName): Boolean {
        val primitiveTypes = setOf(
            "kotlin.String",
            "kotlin.Boolean",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Float",
            "kotlin.Double",
            "kotlin.Unit",
        )

        return when (type) {
            is ClassName -> type.canonicalName !in primitiveTypes
            is ParameterizedTypeName -> true // Collections are always complex
            else -> true
        }
    }
}
