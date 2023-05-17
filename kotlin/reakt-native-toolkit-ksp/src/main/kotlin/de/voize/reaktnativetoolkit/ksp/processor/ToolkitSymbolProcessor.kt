package de.voize.reaktnativetoolkit.ksp.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val JvmPlatform = "JVM"
private const val NativePlatform = "Native"

class ToolkitSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val platforms: List<PlatformInfo>,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val eventEmitterPropertyName = "eventEmitter"
    private val callableJSModulesPropertyName = "_callableJSModules"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val eventEmitterType =
            resolver.getClassDeclarationByName("$toolkitPackageName.util.EventEmitter")
                ?.asType(emptyList())
                ?: error("Could not find EventEmitter")
        val reactNativeModuleType =
            resolver.getClassDeclarationByName("$toolkitPackageName.annotation.ReactNativeModule")
                ?.asType(emptyList())
                ?: error("Could not find ReactNativeModule")


        val functionsByClass =
            resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeMethod")
                .map { annotatedNode ->
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

        resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeModule")
            .map { annotatedNode ->
                when (annotatedNode) {
                    is KSClassDeclaration -> annotatedNode.also {
                        if (annotatedNode.typeParameters.isNotEmpty()) {
                            error("Type parameters are not supported for ReactNativeModule")
                        }
                    }

                    else -> throw IllegalArgumentException("ReactNativeModule annotation can only be used on class declarations")
                }
            }.forEach { wrappedClassDeclaration ->
                val reactNativeModelAnnotationArguments =
                    wrappedClassDeclaration.annotations.single { it.annotationType.resolve() == reactNativeModuleType }.arguments
                val reactNativeModuleName = reactNativeModelAnnotationArguments.single {
                    it.name === resolver.getKSNameFromString("name")
                }.value as String
                val reactNativeModuleSupportedEvents = (reactNativeModelAnnotationArguments.single {
                    it.name === resolver.getKSNameFromString("supportedEvents")
                }.value as List<*>?).orEmpty().filterIsInstance<String>()

                val wrappedClassType = wrappedClassDeclaration.asType(emptyList()).toTypeName()

                val reactNativeMethods = functionsByClass[wrappedClassDeclaration].orEmpty()

                val packageName = wrappedClassDeclaration.packageName.asString()
                val wrappedClassName = wrappedClassDeclaration.simpleName.asString()

                val primaryConstructorParameters =
                    wrappedClassDeclaration.primaryConstructor?.parameters
                        ?: emptyList()
                val constructorInvocationArguments =
                    primaryConstructorParameters.map { constructorParameter ->
                        when (constructorParameter.type.resolve()) {
                            eventEmitterType -> CodeBlock.of(eventEmitterPropertyName)
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

                val requiredEventEmitter =
                    wrappedClassDeclaration.primaryConstructor?.parameters.orEmpty()
                        .any { it.type.resolve() == eventEmitterType }

                val withConstants =
                    wrappedClassDeclaration.getAllFunctions()
                        .any { it.simpleName.asString() == "getConstants" }

                val isInternal = wrappedClassDeclaration.modifiers.contains(Modifier.INTERNAL)

                val platformNames = platforms.map { it.platformName }

                if (JvmPlatform in platformNames && NativePlatform !in platformNames) {
                    createAndroidModule(
                        packageName,
                        wrappedClassName,
                        reactNativeModuleName,
                        reactNativeMethods,
                        constructorParameters,
                        constructorInvocation,
                        withConstants,
                        reactNativeModuleSupportedEvents,
                        wrappedClassDeclaration.containingFile,
                        isInternal,
                    )
                    createModuleProviderAndroid(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        isInternal,
                    )
                }

                if (NativePlatform in platformNames && JvmPlatform !in platformNames) {
                    createIOSModule(
                        packageName,
                        wrappedClassName,
                        reactNativeModuleName,
                        reactNativeMethods,
                        constructorParameters,
                        constructorInvocation,
                        withConstants,
                        requiredEventEmitter,
                        reactNativeModuleSupportedEvents,
                        wrappedClassDeclaration.containingFile,
                        isInternal,
                    )
                    createModuleProviderIOS(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        isInternal,
                    )
                }

                if (JvmPlatform in platformNames && NativePlatform in platformNames) {
                    createModuleProvider(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        isInternal,
                    )
                }
            }

        return emptyList()
    }

    private fun String.iOSModuleClassName() = this + "IOS"
    private fun String.androidModuleClassName() = this + "Android"
    private fun String.moduleProviderClassName() = this + "Provider"


    private fun createModuleProvider(
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.moduleProviderClassName()

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.EXPECT)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeModuleProviderClassName)
            if (containingFile != null) {
                addOriginatingKSFile(containingFile)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun createModuleProviderAndroid(
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.moduleProviderClassName()
        val androidModuleClassName = wrappedClassName.androidModuleClassName()

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.ACTUAL)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.ACTUAL)
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeModuleProviderClassName)

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addFunction(
                FunSpec.builder("getModule").run {
                    val reactApplicationContextVarName = "reactApplicationContext"
                    val lifecycleScopeVarName = "lifecycleScope"

                    addModifiers(KModifier.OVERRIDE)
                    addParameter(reactApplicationContextVarName, ReactApplicationContextClassName)
                    addParameter(lifecycleScopeVarName, CoroutineScopeClassName)
                    returns(ClassName(packageName, androidModuleClassName))
                    addStatement(
                        "return %T(%L)",
                        ClassName(packageName, androidModuleClassName),
                        buildList {
                            add(CodeBlock.of("%N", reactApplicationContextVarName))
                            add(CodeBlock.of("%N", lifecycleScopeVarName))
                            addAll(constructorParameters.map {
                                CodeBlock.of("%N", it.name)
                            })
                        }.joinToCode()
                    )
                }.build()
            )

            if (containingFile != null) {
                addOriginatingKSFile(containingFile)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun createModuleProviderIOS(
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.moduleProviderClassName()
        val iOSModuleClassName = wrappedClassName.iOSModuleClassName()

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.ACTUAL)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.ACTUAL)
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeModuleProviderClassName)

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addFunction(
                FunSpec.builder("getModule").run {
                    val lifecycleScopeVarName = "lifecycleScope"
                    addModifiers(KModifier.OVERRIDE)
                    addParameter(lifecycleScopeVarName, CoroutineScopeClassName)
                    returns(ClassName(packageName, iOSModuleClassName))
                    addStatement(
                        "return %T(%L)",
                        ClassName(packageName, iOSModuleClassName),
                        buildList {
                            add(CodeBlock.of("%N", lifecycleScopeVarName))
                            addAll(constructorParameters.map { CodeBlock.of("%N", it.name) })
                        }.joinToCode()

                    )
                }.build()
            )

            if (containingFile != null) {
                addOriginatingKSFile(containingFile)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun createAndroidModule(
        packageName: String,
        wrappedClassName: String,
        reactNativeModuleName: String,
        reactNativeMethods: List<KSFunctionDeclaration>,
        constructorParameters: List<ParameterSpec>,
        constructorInvocation: CodeBlock,
        withConstants: Boolean,
        supportedEvents: List<String>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.androidModuleClassName()
        val reactContextVarName = "reactContext"
        val coroutineScopeVarName = "coroutineScope"

        val functionSpecs = reactNativeMethods.map { functionDeclaration ->
            val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }
            val promiseVarName = "promise"

            FunSpec.builder(functionDeclaration.simpleName.asString())
                .addAnnotation(ReactNativeMethodAndroidClassName)
                .addParameters(parameters.map(::mapKotlinTypeToReactNativeAndroidType))
                .addParameter(promiseVarName, ClassName("com.facebook.react.bridge", "Promise"))
                .addCode(
                    promiseVarName.promiseLaunchAndroid(coroutineScopeVarName, buildCodeBlock {
                        add(
                            "%N.%N(",
                            "wrappedModule",
                            functionDeclaration.simpleName.asString()
                        )
                        add(
                            parameters.map(::transformReactNativeAndroidValueToKotlinValue)
                                .joinToCode()
                        )
                        add(")")
                    })
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
                        ReactApplicationContextClassName
                    )
                    .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                    .addParameters(constructorParameters)
                    .build()
            )
            superclass(ClassName("$toolkitPackageName.util", "ReactNativeModuleBase"))
            addSuperclassConstructorParameter("%N", reactContextVarName)
            addSuperclassConstructorParameter("%L", listOfCode(supportedEvents.map {
                CodeBlock.of("%S", it)
            }))
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
                FunSpec.builder("addListener").run {
                    val eventNameVarName = "eventName"
                    addParameter(eventNameVarName, String::class)
                    addAnnotation(ReactNativeMethodAndroidClassName)
                    addStatement(
                        "%N.%N(%N)",
                        eventEmitterPropertyName,
                        "addListener",
                        eventNameVarName,
                    )
                }.build()

            )
            addFunction(
                FunSpec.builder("removeListeners").run {
                    val countVarName = "count"
                    addParameter(countVarName, Int::class)
                    addAnnotation(ReactNativeMethodAndroidClassName)
                    addStatement(
                        "%N.%N(%N)",
                        eventEmitterPropertyName,
                        "removeListeners",
                        countVarName,
                    )
                }.build()
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
        supportedEvents: List<String>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.iOSModuleClassName()
        val coroutineScopeVarName = "coroutineScope"
        val promiseVarName = "promise"

        val functionSpecs = reactNativeMethodsWithMetadata.map { functionDeclaration ->
            val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }

            FunSpec.builder(functionDeclaration.simpleName.asString())
                .addParameters(parameters.map(::mapKotlinTypeToReactNativeIOSType))
                .addParameter(
                    promiseVarName,
                    PromiseIOSClassName.parameterizedBy(
                        functionDeclaration.returnType?.toTypeName()
                            ?: UNIT
                    )
                )
                .addCode(
                    promiseVarName.promiseLaunchIOS(coroutineScopeVarName, buildCodeBlock {
                        add("%N.%N(", "wrappedModule", functionDeclaration.simpleName.asString())
                        add(
                            parameters.map(::transformReactNativeIOSValueToKotlinValue).joinToCode()
                        )
                        add(")")
                    }
                    )
                )
                .build()
        }


        val classSpec = TypeSpec.classBuilder(className).apply {
            if (isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                    .addParameters(constructorParameters)
                    .build()
            )
            superclass(ClassName("platform.darwin", "NSObject"))
            addSuperinterface(RCTBrigdeModuleProtocolClassName)
            addProperty(
                PropertySpec.builder(coroutineScopeVarName, CoroutineScopeClassName)
                    .addModifiers(KModifier.PRIVATE).initializer(coroutineScopeVarName).build()
            )

            val eventEmitterFunctionsToExpose = if (withEventEmitter) {
                addProperty( // already part of RCTBridgeModule.h
                    PropertySpec.builder(
                        callableJSModulesPropertyName,
                        RCTCallableJSModulesClassName.copy(nullable = true)
                    )
                        .addModifiers(KModifier.PRIVATE)
                        .mutable(true)
                        .initializer(CodeBlock.of("null"))
                        .build()
                )
                addFunction(
                    FunSpec.builder("setCallableJSModules").run {
                        val callableJSModulesVarName = "callableJSModules"
                        addParameter(
                            callableJSModulesVarName,
                            RCTCallableJSModulesClassName.copy(nullable = true)
                        )
                        addModifiers(KModifier.OVERRIDE)
                        addStatement(
                            "%N = %N",
                            callableJSModulesPropertyName,
                            callableJSModulesVarName
                        )
                    }.build()
                )
                addProperty(
                    PropertySpec.builder(eventEmitterPropertyName, EventEmitterIOS)
                        .addModifiers(KModifier.PRIVATE).initializer(buildCodeBlock {
                            // TODO callableJSModules should be injected by RCTModuleData.mm, needs verification
                            add(
                                "%T({%N}, %L)",
                                EventEmitterIOS,
                                callableJSModulesPropertyName,
                                listOfCode(supportedEvents.map {
                                    CodeBlock.of("%S", it)
                                })
                            )
                        }).build()
                )
                addFunction(
                    FunSpec.builder("addListener").run {
                        val eventNameVarname = "eventName"
                        addParameter(eventNameVarname, STRING)
                        addParameter(promiseVarName, PromiseIOSClassName.parameterizedBy(UNIT))
                        addCode(
                            promiseVarName.promiseLaunchIOS(
                                coroutineScopeVarName,
                                CodeBlock.of(
                                    "%N.%N(%N)",
                                    eventEmitterPropertyName,
                                    "addListener",
                                    eventNameVarname
                                )
                            )
                        )
                    }.build()
                )
                addFunction(
                    FunSpec.builder("removeListeners").run {
                        val countVarName = "count"
                        addParameter(countVarName, DOUBLE)
                        addParameter(promiseVarName, PromiseIOSClassName.parameterizedBy(UNIT))
                        addCode(
                            promiseVarName.promiseLaunchIOS(
                                coroutineScopeVarName,
                                CodeBlock.of(
                                    "%N.%N(%N)",
                                    eventEmitterPropertyName,
                                    "removeListeners",
                                    countVarName
                                )
                            )
                        )
                    }.build()
                )

                val argsVarName = "args"
                listOf(
                    CodeBlock.of(
                        "%T(%S) { %N, %N -> %N(%N[0] as %T, %N as %T) }",
                        ClassName("$toolkitPackageName.util", "RCTBridgeMethodWrapper"),
                        "addListener",
                        argsVarName,
                        promiseVarName,
                        "addListener",
                        argsVarName,
                        STRING,
                        promiseVarName,
                        PromiseIOSClassName.parameterizedBy(UNIT),
                    ),
                    CodeBlock.of(
                        "%T(%S) { %N, %N -> %N(%N[0] as %T, %N as %T) }",
                        ClassName("$toolkitPackageName.util", "RCTBridgeMethodWrapper"),
                        "removeListeners",
                        argsVarName,
                        promiseVarName,
                        "removeListeners",
                        argsVarName,
                        DOUBLE,
                        promiseVarName,
                        PromiseIOSClassName.parameterizedBy(UNIT),
                    ),
                )
            } else {
                emptyList()
            }

            addProperty(
                PropertySpec.builder("wrappedModule", ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE).initializer(constructorInvocation).build()
            )

            val bridgeMethodWrappers = reactNativeMethodsWithMetadata.map { ksFunctionDeclaration ->
                val functionName = ksFunctionDeclaration.simpleName.asString()
                buildCodeBlock {
                    val argsVarName = "args"
                    add(
                        "%T(%S) { %N, %N -> %N(",
                        ClassName("$toolkitPackageName.util", "RCTBridgeMethodWrapper"),
                        functionName,
                        argsVarName,
                        promiseVarName,
                        functionName,
                    )
                    add((ksFunctionDeclaration.parameters.map { it.toParameterSpec() }
                        .map(::mapKotlinTypeToReactNativeIOSType)
                        .mapIndexed { index, parameter ->
                            CodeBlock.of(
                                "%N[%L] as %T",
                                argsVarName,
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
                            .parameterizedBy(
                                ClassName(
                                    reactNativeInteropNamespace,
                                    "RCTBridgeMethodProtocol"
                                )
                            )
                    )
                    .addCode(
                        CodeBlock.of(
                            "return %L",
                            listOfCode((bridgeMethodWrappers + eventEmitterFunctionsToExpose))
                        )
                    ).build()
            )


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

            addType(TypeSpec.companionObjectBuilder().apply {
                superclass(ClassName("platform.darwin", "NSObjectMeta"))
                addSuperinterface(
                    ClassName(
                        reactNativeInteropNamespace,
                        "RCTBridgeModuleProtocolMeta"
                    )
                )
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

        }.build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private fun transformReactNativeAndroidValueToKotlinValue(parameter: ParameterSpec): CodeBlock {
        val isNullable = parameter.type.isNullable
        return when (val type = parameter.type) {
            is ParameterizedTypeName -> when (type.rawType) {
                List::class.asTypeName() -> CodeBlock.of(
                    if (isNullable) "%N?.toArrayList()?.%M<%T>()" else "%N.toArrayList().%M<%T>()",
                    parameter.name,
                    MemberName("kotlin.collections", "filterIsInstance"),
                    type.typeArguments.single()
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

    private fun mapKotlinTypeToReactNativeAndroidType(parameter: ParameterSpec): ParameterSpec {
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

    private fun transformReactNativeIOSValueToKotlinValue(
        parameter: ParameterSpec,
    ): CodeBlock {
        val isNullable = parameter.type.isNullable
        return when (val type = parameter.type) {
            is ClassName -> {
                when (type.canonicalName) {
                    INT.canonicalName -> CodeBlock.of(
                        if (isNullable) "%N?.toInt()" else "%N.toInt()",
                        parameter.name
                    )

                    LONG.canonicalName -> CodeBlock.of(
                        if (isNullable) "%N?.toLong()" else "%N.toLong()",
                        parameter.name
                    )

                    FLOAT.canonicalName -> CodeBlock.of(
                        if (isNullable) "%N?.toFloat()" else "%N.toFloat()",
                        parameter.name
                    )

                    else -> CodeBlock.of("%N", parameter.name)
                }
            }

            else -> CodeBlock.of("%N", parameter.name)
        }
    }

    private fun mapKotlinTypeToReactNativeIOSType(parameter: ParameterSpec): ParameterSpec {
        val type = parameter.type
        return ParameterSpec.builder(
            parameter.name,
            when (type) {
                is ClassName -> when (type.canonicalName) {
                    INT.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    LONG.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    FLOAT.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
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
        return ToolkitSymbolProcessor(
            environment.codeGenerator,
            environment.platforms,
            environment.logger
        )
    }
}

fun KSValueParameter.toParameterSpec(): ParameterSpec {
    return ParameterSpec.builder(
        this.name?.asString() ?: error("Parameter must have a name"),
        this.type.toTypeName()
    )
        .build()
}

fun String.promiseLaunchAndroid(coroutineScopeVarName: String, block: CodeBlock) = buildCodeBlock {
    addStatement(
        "%N.%M(%N) { %L }",
        this@promiseLaunchAndroid,
        MemberName("$toolkitPackageName.util", "launch"),
        coroutineScopeVarName,
        block
    )
}

fun String.promiseLaunchIOS(coroutineScopeVarName: String, block: CodeBlock) = buildCodeBlock {
    addStatement(
        "%N.%M(%N) { %L }",
        this@promiseLaunchIOS,
        MemberName("$toolkitPackageName.util", "launch"),
        coroutineScopeVarName,
        block
    )
}

fun listOfCode(code: List<CodeBlock>) = CodeBlock.of("%M(%L)", ListOfMember, code.joinToCode())

private val reactNativeInteropNamespace = "react_native"
private val toolkitPackageName = "de.voize.reaktnativetoolkit"
private val toolkitUtilPackageName = "$toolkitPackageName.util"

private val ReactNativeMethodAndroidClassName =
    ClassName("com.facebook.react.bridge", "ReactMethod")
private val ReactApplicationContextClassName =
    ClassName("com.facebook.react.bridge", "ReactApplicationContext")
private val CoroutineScopeClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
private val PromiseIOSClassName = ClassName(toolkitUtilPackageName, "PromiseIOS")
private val EventEmitterIOS = ClassName(toolkitUtilPackageName, "EventEmitterIOS")
private val ReactNativeModuleProviderClassName =
    ClassName(toolkitUtilPackageName, "ReactNativeModuleProvider")
private val RCTCallableJSModulesClassName =
    ClassName(reactNativeInteropNamespace, "RCTCallableJSModules")
private val RCTBrigdeModuleProtocolClassName =
    ClassName(reactNativeInteropNamespace, "RCTBridgeModuleProtocol")
private val ListOfMember = MemberName("kotlin.collections", "listOf")
