package de.voize.core.toolkit.ksp.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class ToolkitSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val platforms: List<PlatformInfo>,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val eventEmitterType =
            resolver.getClassDeclarationByName("de.voize.core.util.EventEmitter")?.asType(emptyList())
                ?: error("Could not find EventEmitter")
        val reactNativeModuleType =
            resolver.getClassDeclarationByName("de.voize.core.kapt.annotation.ReactNativeModule")?.asType(emptyList())
                ?: error("Could not find ReactNativeModule")


        val functionsByClass =
            resolver.getSymbolsWithAnnotation("de.voize.core.kapt.annotation.ReactNativeMethod").map { annotatedNode ->
                when (annotatedNode) {
                    is KSFunctionDeclaration -> annotatedNode.also {
                        if (annotatedNode.typeParameters.isNotEmpty()) {
                            error("Type parameters are not supported for ReactNativeMethod")
                        }
                    }
                    else -> throw IllegalArgumentException("ReactNativeMethods annotation can only be used on function declarations")
                }
            }.groupBy { annotatedNode ->
                annotatedNode.parentDeclaration.let {
                    when (it) {
                        is KSClassDeclaration -> it
                        else -> throw IllegalArgumentException("ReactNativeMethods must be declared in a class")
                    }
                }
            }

        resolver.getSymbolsWithAnnotation("de.voize.core.kapt.annotation.ReactNativeModule").map { annotatedNode ->
            when (annotatedNode) {
                is KSClassDeclaration -> annotatedNode.also {
                    if (annotatedNode.typeParameters.isNotEmpty()) {
                        error("Type parameters are not supported for ReactNativeModule")
                    }
                }
                else -> throw IllegalArgumentException("ReactNativeModule annotation can only be used on class declarations")
            }
        }.forEach { wrappedClassDeclaration ->
            val reactNativeModuleName =
                wrappedClassDeclaration.annotations.single { it.annotationType.resolve() == reactNativeModuleType }.arguments.single().value as String
            val wrappedClassType = wrappedClassDeclaration.asType(emptyList()).toTypeName()

            val reactNativeMethods = functionsByClass[wrappedClassDeclaration].orEmpty()

            val packageName = wrappedClassDeclaration.packageName.asString()
            val wrappedClassName = wrappedClassDeclaration.simpleName.asString()

            val primaryConstructorParameters = wrappedClassDeclaration.primaryConstructor?.parameters ?: emptyList()
            val constructorInvocationArguments = primaryConstructorParameters.map { constructorParameter ->
                when (constructorParameter.type.resolve()) {
                    eventEmitterType -> CodeBlock.of("eventEmitter")
                    else -> CodeBlock.of("%N", constructorParameter.name?.asString())
                }
            }.joinToCode()
            val constructorInvocation =
                CodeBlock.of("%T(%L)", wrappedClassType, constructorInvocationArguments)

            val constructorParameters = primaryConstructorParameters.filter {
                when (it.type.resolve()) {
                    eventEmitterType -> false
                    else -> true
                }
            }.map { it.toParameterSpec() }

            val requiredEventEmitter = wrappedClassDeclaration.primaryConstructor?.parameters.orEmpty()
                .any { it.type.resolve() == eventEmitterType }

            val withConstants =
                wrappedClassDeclaration.getAllFunctions().any { it.simpleName.asString() == "getConstants" }

            val isInternal = wrappedClassDeclaration.modifiers.contains(Modifier.INTERNAL)

            val platformNames = platforms.map { it.platformName }
            if (platformNames.contains("JVM")) {
                createAndroidModule(
                    packageName,
                    wrappedClassName,
                    reactNativeModuleName,
                    reactNativeMethods,
                    constructorParameters,
                    constructorInvocation,
                    withConstants,
                    wrappedClassDeclaration.containingFile,
                    isInternal
                )
            }

            if (platformNames.contains("Native")) {
                createIOSModule(
                    packageName,
                    wrappedClassName,
                    reactNativeModuleName,
                    reactNativeMethods,
                    constructorParameters,
                    constructorInvocation,
                    withConstants,
                    requiredEventEmitter,
                    eventEmitterType,
                    wrappedClassDeclaration.containingFile,
                    isInternal
                )
            }
        }

        return emptyList()
    }

    private fun createAndroidModule(
        packageName: String,
        wrappedClassName: String,
        reactNativeModuleName: String,
        reactNativeMethods: List<KSFunctionDeclaration>,
        constructorParameters: List<ParameterSpec>,
        constructorInvocation: CodeBlock,
        withConstants: Boolean,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName + "Android"
        val reactContextVarName = "reactContext"
        val coroutineScopeVarName = "coroutineScope"

        val functionSpecs = reactNativeMethods.map { functionDeclaration ->
            val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }
            val promiseVarName = "promise"

            FunSpec.builder(functionDeclaration.simpleName.asString())
                .addAnnotation(ClassName("com.facebook.react.bridge", "ReactMethod"))
                .addParameters(parameters.map(::mapReactNativeAndroidToKotlinType))
                .addParameter(promiseVarName, ClassName("com.facebook.react.bridge", "Promise"))
                .addCode(
                    buildCodeBlock {
                        add(
                            "%N.%M(%N) {",
                            promiseVarName,
                            MemberName("de.voize.core.util", "launch"),
                            coroutineScopeVarName
                        )
                        add(
                            "%N.%N(",
                            "wrappedModule",
                            functionDeclaration.simpleName.asString()
                        )
                        add(parameters.map(::transformReactNativeAndroidToKotlinValue).joinToCode())
                        add(")")
                        addStatement("}")
                    }
                )
                .build()
        }


        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(
                        reactContextVarName,
                        ClassName("com.facebook.react.bridge", "ReactApplicationContext")
                    )
                    .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                    .addParameters(constructorParameters)
                    .build()
            )
            superclass(ClassName("de.voize.core.util", "ReactNativeModuleBase"))
            addSuperclassConstructorParameter("%N", reactContextVarName)
            addProperty(
                PropertySpec.builder(coroutineScopeVarName, CoroutineScopeClassName)
                    .addModifiers(KModifier.PRIVATE).initializer(coroutineScopeVarName).build()
            )
            addProperty(
                PropertySpec.builder("wrappedModule", ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE).initializer(constructorInvocation).build()
            )
            addFunction(
                FunSpec.builder("getName")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(String::class)
                    .addStatement("return %S", reactNativeModuleName)
                    .build()
            )
            if (withConstants) {
                addFunction(
                    FunSpec.builder("getConstants")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(ClassName("com.facebook.react.bridge", "WritableMap"))
                        .addStatement("return %N.%N()", "wrappedModule", "getConstants")
                        .build()
                )
            }
            addFunction(
                FunSpec.builder("addListener")
                    .addParameter("eventName", String::class)
                    .addAnnotation(ClassName("com.facebook.react.bridge", "ReactMethod"))
                    .addStatement(
                        "%N.%M { it + 1 }",
                        "listenerCount",
                        MemberName("kotlinx.coroutines.flow", "update")
                    )
                    .build()
            )
            addFunction(
                FunSpec.builder("removeListeners")
                    .addParameter("count", Int::class)
                    .addAnnotation(ClassName("com.facebook.react.bridge", "ReactMethod"))
                    .addStatement(
                        "%N.%M { it - count }",
                        "listenerCount",
                        MemberName("kotlinx.coroutines.flow", "update")
                    )
                    .build()
            )
            addFunctions(functionSpecs)
            if (containingFile != null) {
                addOriginatingKSFile(containingFile)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun createIOSModule(
        packageName: String,
        wrappedClassName: String,
        reactNativeModuleName: String,
        reactNativeMethodsWithMetadata: List<KSFunctionDeclaration>,
        constructorParameters: List<ParameterSpec>,
        constructorInvocation: CodeBlock,
        withConstants: Boolean,
        withEventEmitter: Boolean,
        eventEmitterType: KSType,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val reactNativeInteropNamespace = "react_native"
        val className = wrappedClassName + "IOS"
        val coroutineScopeVarName = "coroutineScope"

        val functionSpecs = reactNativeMethodsWithMetadata.map { functionDeclaration ->
            val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }
            val promiseVarName = "promise"

            FunSpec.builder(functionDeclaration.simpleName.asString())
                .addParameters(parameters.map {
                    ParameterSpec.builder(it.name, it.type.copy(annotations = emptyList()))
                        .build()
                })
                .addParameter(
                    promiseVarName,
                    PromiseIOSClassName.parameterizedBy(functionDeclaration.returnType?.toTypeName() ?: UNIT)
                )
                .addCode(
                    buildCodeBlock {
                        add(
                            "%N.%M(%N) {",
                            promiseVarName,
                            MemberName("de.voize.core.util", "launch"),
                            coroutineScopeVarName,
                        )
                        add("%N.%N(", "wrappedModule", functionDeclaration.simpleName.asString())
                        add(parameters.map { CodeBlock.of("%N", it.name) }.joinToCode())
                        add(")")
                        add("}")
                    }
                )
                .build()
        }

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal && !withEventEmitter) {
                addModifiers(KModifier.INTERNAL)
            }
            if (withEventEmitter) {
                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("eventEmitter", eventEmitterType.toTypeName())
                        .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                        .addParameters(constructorParameters)
                        .build()
                )
            } else {
                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                        .addParameters(constructorParameters)
                        .build()
                )
                superclass(ClassName("platform.darwin", "NSObject"))
                addSuperinterface(ClassName(reactNativeInteropNamespace, "RCTBridgeModuleProtocol"))
            }
            addProperty(
                PropertySpec.builder(coroutineScopeVarName, CoroutineScopeClassName)
                    .addModifiers(KModifier.PRIVATE).initializer(coroutineScopeVarName).build()
            )
            addProperty(
                PropertySpec.builder("wrappedModule", ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE).initializer(constructorInvocation).build()
            )

            if (!withEventEmitter) {
                val promiseVarName = "promise"
                val bridgeMethodWrappers = reactNativeMethodsWithMetadata.map { ksFunctionDeclaration ->
                    val functionName = ksFunctionDeclaration.simpleName.asString()
                    buildCodeBlock {
                        add(
                            "%T(%S) { args, %N -> %N(",
                            ClassName("de.voize.core.util", "RCTBridgeMethodWrapper"),
                            functionName,
                            promiseVarName,
                            functionName,
                        )
                        add((ksFunctionDeclaration.parameters.map { it.toParameterSpec() }
                            .mapIndexed { index, parameter ->
                                CodeBlock.of(
                                    "%N[%L] as %T",
                                    "args",
                                    index,
                                    parameter.type
                                )
                            } + listOf(
                            CodeBlock.of(
                                "%N as %T",
                                promiseVarName,
                                PromiseIOSClassName.parameterizedBy(
                                    ksFunctionDeclaration.returnType?.toTypeName() ?: UNIT
                                )
                            )
                        )).joinToCode())
                        add(")")
                        add("}")
                    }
                }


                addFunction(
                    FunSpec.builder("methodsToExport")
                        .addModifiers(KModifier.OVERRIDE)
                        .addAnnotation(
                            AnnotationSpec.builder(Suppress::class)
                                .addMember("%S", "UNCHECKED_CAST")
                                .build()
                        )
                        .returns(
                            List::class.asClassName()
                                .parameterizedBy(ClassName(reactNativeInteropNamespace, "RCTBridgeMethodProtocol"))
                        )
                        .addCode(
                            buildCodeBlock {
                                add("return %M(", MemberName("kotlin.collections", "listOf"))
                                add(bridgeMethodWrappers.joinToCode())
                                add(")")
                            }
                        ).build())
            }


            if (withEventEmitter) {
                addFunction(
                    FunSpec.builder("supportedEvents")
                        .returns(
                            List::class.asClassName().parameterizedBy(STRING)
                        )
                        .addCode(
                            CodeBlock.of(
                                "return %N.%N()",
                                "wrappedModule",
                                "supportedEvents"
                            )
                        ).build()
                )
            }

            if (withConstants) {
                error("constants are not implemented currently")

                addProperty(
                    PropertySpec.builder(
                        "getConstants",
                        Map::class.asClassName().parameterizedBy(ANY.copy(nullable = true), STAR)
                    )
                        .initializer(
                            CodeBlock.of(
                                "return %N.%N()",
                                "wrappedModule",
                                "getConstants"
                            )
                        ).build()
                )
            }

            addFunctions(functionSpecs)

            if (!withEventEmitter) {
                addType(TypeSpec.companionObjectBuilder().apply {
                    superclass(ClassName("platform.darwin", "NSObjectMeta"))
                    addSuperinterface(ClassName(reactNativeInteropNamespace, "RCTBridgeModuleProtocolMeta"))
                    addFunction(
                        FunSpec.builder("moduleName")
                            .addModifiers(KModifier.OVERRIDE)
                            .returns(STRING)
                            .addCode(
                                CodeBlock.of(
                                    "return %S",
                                    reactNativeModuleName
                                )
                            ).build()
                    )
                    addFunction(
                        FunSpec.builder("requiresMainQueueSetup")
                            .addModifiers(KModifier.OVERRIDE)
                            .returns(BOOLEAN)
                            .addCode(
                                CodeBlock.of("return %L", true)
                            ).build()
                    )
                    if (containingFile != null) {
                        addOriginatingKSFile(containingFile)
                    }
                }.build())
            }

        }.build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun transformReactNativeAndroidToKotlinValue(parameter: ParameterSpec): CodeBlock {
        val isNullable = parameter.type.isNullable
        return when (val type = parameter.type) {
            is ParameterizedTypeName -> when (type.rawType) {
                List::class.asTypeName() -> CodeBlock.of(
                    if (isNullable) "%N?.toArrayList()?.%M<%T>()" else "%N.toArrayList().%M<%T>()",
                    parameter.name,
                    MemberName("kotlin.collections", "filterIsInstance"),
                    type.typeArguments.first()
                )
                Map::class.asTypeName() -> CodeBlock.of(
                    if (isNullable) "(%N?.toHashMap() as %T)" else "(%N.toHashMap() as %T)",
                    parameter.name,
                    type
                )
                else -> CodeBlock.of("%N", parameter.name)
            }
            else -> CodeBlock.of("%N", parameter.name)
        }
    }

    private fun mapReactNativeAndroidToKotlinType(parameter: ParameterSpec): ParameterSpec {
        val type = parameter.type
        return ParameterSpec.builder(
            parameter.name,
            when (type) {
                is ParameterizedTypeName -> when (type.rawType) {
                    List::class.asTypeName() -> ClassName(
                        "com.facebook.react.bridge",
                        "ReadableArray"
                    ).copy(nullable = type.isNullable)
                    Map::class.asTypeName() -> ClassName(
                        "com.facebook.react.bridge",
                        "ReadableMap"
                    ).copy(nullable = type.isNullable)
                    else -> type.copy(annotations = emptyList())
                }
                else -> type.copy(annotations = emptyList())
            }
        ).build()
    }
}

@AutoService(SymbolProcessorProvider::class)
class ToolkitSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ToolkitSymbolProcessor(environment.codeGenerator, environment.platforms, environment.logger)
    }
}

fun KSValueParameter.toParameterSpec(): ParameterSpec {
    return ParameterSpec.builder(
        this.name?.asString() ?: error("Parameter must have a name"),
        this.type.toTypeName()
    )
        .build()
}

private val CoroutineScopeClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
private val PromiseIOSClassName = ClassName("de.voize.core.util", "PromiseIOS")
