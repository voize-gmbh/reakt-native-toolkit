package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.NUMBER
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class ReactNativeModuleGenerator(
    private val codeGenerator: CodeGenerator,
    private val platforms: List<PlatformInfo>,
    private val options: Map<String, String>,
    private val logger: KSPLogger,
) {
    internal data class RNModule(
        val wrappedClassDeclaration: KSClassDeclaration,
        val moduleName: String,
        val supportedEvents: List<String>,
        val reactNativeMethods: List<KSFunctionDeclaration>,
        val reactNativeFlows: List<KSFunctionDeclaration>,
        val reactNativeEvents: List<ReactNativeEvent>,
        val isInternal: Boolean,
    )

    internal data class ReactNativeEvent(
        val functionDeclaration: KSFunctionDeclaration,
        val name: String,
    )

    private var invoked = false

    private val eventEmitterPropertyName = "eventEmitter"
    private val callableJSModulesPropertyName = "_callableJSModules"

    private fun String.iOSModuleClassName() = this + "IOS"
    private fun String.androidModuleClassName() = this + "Android"
    private fun String.moduleProviderClassName() = this + "Provider"

    internal fun process(resolver: Resolver): ToolkitSymbolProcessor.ProcessResult {
        val eventEmitterType =
            resolver.getClassDeclarationByName("$toolkitPackageName.util.EventEmitter")
                ?.asType(emptyList())
                ?: error("Could not find EventEmitter, please check that the reakt-native-toolkit dependency is added to the project")
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

        val flowsByClass =
            resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeFlow")
                .map { annotatedNode ->
                    try {
                        when (annotatedNode) {
                            is KSFunctionDeclaration -> annotatedNode.also {
                                if (annotatedNode.typeParameters.isNotEmpty()) {
                                    error("Type parameters are not supported for ReactNativeFlow")
                                }
                                val returnTypeReference = annotatedNode.returnType
                                    ?: error("Return type of ReactNativeFlow must")
                                val returnType = returnTypeReference.resolve()
                                val declaration = returnType.declaration
                                if (declaration.qualifiedName?.asString() != "kotlinx.coroutines.flow.Flow") {
                                    error("Return type of ReactNativeFlow must be kotlinx.coroutines.flow.Flow, but was ${declaration.qualifiedName?.asString()}")
                                }
                                val elementTypeReference = returnType.arguments.single().type
                                    ?: error("Element type of Flow must be specified")
                                val elementType = elementTypeReference.resolve()
                                if (elementType.declaration is KSTypeParameter) {
                                    error("Element type of Flow must not be a type parameter")
                                }
                            }

                            else -> error("ReactNativeFlow annotation can only be used on function declarations")
                        }
                    } catch (e: Throwable) {
                        throw IllegalArgumentException(
                            "Error processing $annotatedNode at ${annotatedNode.location}",
                            e
                        )
                    }
                }.groupBy { annotatedNode ->
                    annotatedNode.parentDeclaration.let {
                        when (it) {
                            is KSClassDeclaration -> it
                            else -> error("ReactNativeFlow must be declared in a class")
                        }
                    }
                }

        val eventsByClass =
            resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeEvent")
                .map { annotatedNode ->
                    try {
                        when (annotatedNode) {
                            is KSFunctionDeclaration -> annotatedNode.also {
                                if (annotatedNode.typeParameters.isNotEmpty()) {
                                    error("Type parameters are not supported for ReactNativeEvent")
                                }
                                val returnTypeReference = annotatedNode.returnType
                                    ?: error("Return type of ReactNativeEvent must be specified")
                                val returnType = returnTypeReference.resolve()
                                val declaration = returnType.declaration
                                if (declaration.qualifiedName?.asString() != "kotlinx.coroutines.flow.Flow") {
                                    error("Return type of ReactNativeEvent must be kotlinx.coroutines.flow.Flow, but was ${declaration.qualifiedName?.asString()}")
                                }
                                val elementTypeReference = returnType.arguments.single().type
                                    ?: error("Element type of Flow must be specified")
                                val elementType = elementTypeReference.resolve()
                                if (elementType.declaration is KSTypeParameter) {
                                    error("Element type of Flow must not be a type parameter")
                                }
                            }

                            else -> error("ReactNativeEvent annotation can only be used on function declarations")
                        }
                        val eventName = annotatedNode.annotations.single {
                            it.annotationType.resolve().declaration.qualifiedName?.asString() == "$toolkitPackageName.annotation.ReactNativeEvent"
                        }.arguments.single { it.name?.asString() == "name" }.value as String
                        ReactNativeEvent(annotatedNode, eventName)
                    } catch (e: Throwable) {
                        throw IllegalArgumentException(
                            "Error processing $annotatedNode at ${annotatedNode.location}",
                            e,
                        )
                    }
                }.groupBy { event ->
                    event.functionDeclaration.parentDeclaration.let {
                        when (it) {
                            is KSClassDeclaration -> it
                            else -> error("ReactNativeEvent must be declared in a class")
                        }
                    }
                }

        val rnModuleSymbols =
            resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeModule")

        val (validRNModuleSymbols, invalidRNModuleSymbols) = rnModuleSymbols.partition { it.validate() }

        val rnModules = validRNModuleSymbols
            .map { annotatedNode ->
                when (annotatedNode) {
                    is KSClassDeclaration -> annotatedNode.also {
                        if (annotatedNode.typeParameters.isNotEmpty()) {
                            error("Type parameters are not supported for ReactNativeModule")
                        }
                    }

                    else -> throw IllegalArgumentException("ReactNativeModule annotation can only be used on class declarations")
                }
            }.map { wrappedClassDeclaration ->
                val reactNativeModelAnnotationArguments =
                    wrappedClassDeclaration.annotations.single { it.annotationType.resolve() == reactNativeModuleType }.arguments
                val moduleName = reactNativeModelAnnotationArguments.single {
                    it.name?.asString() == "name"
                }.value as String
                val supportedEvents = (reactNativeModelAnnotationArguments.singleOrNull {
                    it.name?.asString() == "supportedEvents"
                }?.value as List<*>?).orEmpty().filterIsInstance<String>()
                val reactNativeMethods = functionsByClass[wrappedClassDeclaration].orEmpty()
                val reactNativeFlows = flowsByClass[wrappedClassDeclaration].orEmpty()
                val reactNativeEvents = eventsByClass[wrappedClassDeclaration].orEmpty()
                val isInternal = wrappedClassDeclaration.modifiers.contains(Modifier.INTERNAL)

                RNModule(
                    wrappedClassDeclaration = wrappedClassDeclaration,
                    moduleName = moduleName,
                    supportedEvents = supportedEvents,
                    reactNativeMethods = reactNativeMethods,
                    reactNativeFlows = reactNativeFlows,
                    reactNativeEvents = reactNativeEvents,
                    isInternal = isInternal,
                )
            }.toList()

        rnModules.forEach { rnModule ->
            try {
                val wrappedClassDeclaration = rnModule.wrappedClassDeclaration
                val wrappedClassType = wrappedClassDeclaration.asType(emptyList()).toTypeName()

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


                if (platforms.isAndroid()) {
                    createAndroidModule(
                        rnModule,
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        constructorInvocation,
                        withConstants,
                    )
                    createModuleProviderAndroid(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        rnModule.isInternal,
                    )
                }

                if (platforms.isIOS()) {
                    createIOSModule(
                        rnModule,
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        constructorInvocation,
                        withConstants,
                        requiredEventEmitter,
                    )
                    createModuleProviderIOS(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        rnModule.isInternal,
                    )
                }

                if (platforms.isCommon()) {
                    createModuleProvider(
                        packageName,
                        wrappedClassName,
                        constructorParameters,
                        wrappedClassDeclaration.containingFile,
                        rnModule.isInternal,
                    )
                }
            } catch (e: Throwable) {
                throw IllegalArgumentException(
                    "Error processing native module ${rnModule.moduleName} at ${rnModule.wrappedClassDeclaration.location}",
                    e
                )
            }
        }

        if (
            invalidRNModuleSymbols.isEmpty() &&
            rnModules.isNotEmpty() &&
            !invoked &&
            platforms.isCommon()
        ) {
            ReactNativeModuleTypescriptGenerator(
                resolver,
                codeGenerator,
                TypescriptConfig.fromOptions(options),
                logger,
            ).generate(rnModules)
            invoked = true
        }

        val (types, originatingKSFiles) = typesFrom(rnModules)

        return ToolkitSymbolProcessor.ProcessResult(
            deferredSymbols = invalidRNModuleSymbols,
            types = types,
            originatingFiles = originatingKSFiles,
        )
    }

    /**
     * Collect all types of function parameters and return types.
     */
    private fun typesFrom(rnModules: List<RNModule>): Pair<List<KSType>, List<KSFile>> {
        val typeDeclarations =
            rnModules.flatMap { it.reactNativeMethods + it.reactNativeFlows + it.reactNativeEvents.map { ev -> ev.functionDeclaration } }.flatMap {
                it.parameters.map { it.type } + (it.returnType ?: error("Type resolution error"))
            }.toSet().map { it.resolve() }
        val originatingKSFiles = rnModules.mapNotNull { it.wrappedClassDeclaration.containingFile }
        return typeDeclarations to originatingKSFiles
    }

    private fun createModuleProvider(
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        containingFile: KSFile?,
        isInternal: Boolean,
    ) {
        val className = wrappedClassName.moduleProviderClassName()

        val classSpec = TypeSpec.classBuilder(className).apply {
            addKdoc("Provider for [${packageName}.${wrappedClassName}]")
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
                    returns(ReactNativeModuleBaseClassName)
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
                    returns(RCTBridgeModuleProtocolClassName)
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
        rnModule: RNModule,
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        constructorInvocation: CodeBlock,
        withConstants: Boolean,
    ) {
        val className = wrappedClassName.androidModuleClassName()
        val reactContextVarName = "reactContext"
        val coroutineScopeVarName = "coroutineScope"
        val wrappedModuleVarName = "wrappedModule"

        val functionSpecs = rnModule.reactNativeMethods.map { functionDeclaration ->
            androidReactNativeFunctionSpec(
                functionDeclaration,
                coroutineScopeVarName,
                wrappedModuleVarName
            )
        }

        val flowFunctionSpecs = rnModule.reactNativeFlows.map { functionDeclaration ->
            androidReactNativeFunctionSpec(
                functionDeclaration,
                coroutineScopeVarName,
                wrappedModuleVarName,
                isReactNativeFlow = true,
            )
        }

        val unsubscribeFlowFunctionSpec = FunSpec.builder("unsubscribeFromToolkitUseFlow").run {
            val subscriptionIdVarName = "subscriptionId"
            addParameter(subscriptionIdVarName, String::class)
            addAnnotation(ReactNativeMethodAndroidClassName)
            addStatement(
                "%M(%N)",
                UnsubscribeFromFlowMember,
                subscriptionIdVarName,
            )
        }.build()

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (rnModule.isInternal) {
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
            addSuperclassConstructorParameter("%L", listOfCode(rnModule.supportedEvents.map {
                CodeBlock.of("%S", it)
            }))
            addProperty(
                PropertySpec.builder(coroutineScopeVarName, CoroutineScopeClassName)
                    .addModifiers(KModifier.PRIVATE).initializer(coroutineScopeVarName).build()
            )
            addProperty(
                PropertySpec.builder(wrappedModuleVarName, ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE).initializer(constructorInvocation).build()
            )
            if (rnModule.reactNativeEvents.isNotEmpty()) {
                addInitializerBlock(
                    CodeBlock.builder().apply {
                        rnModule.reactNativeEvents.forEach { event ->
                            addStatement(
                                "%N.%N().%M(%N, %S, %N)",
                                wrappedModuleVarName,
                                event.functionDeclaration.simpleName.asString(),
                                FlowToEventEmitterMember,
                                eventEmitterPropertyName,
                                event.name,
                                coroutineScopeVarName,
                            )
                        }
                    }.build()
                )
            }
            addFunction(
                FunSpec.builder("getName")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(String::class)
                    .addStatement("return %S", rnModule.moduleName)
                    .build()
            )
            if (withConstants) {
                addFunction(
                    FunSpec.builder("getConstants")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(ClassName("com.facebook.react.bridge", "WritableMap"))
                        .addStatement("return %N.%N()", wrappedModuleVarName, "getConstants")
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
            addFunctions(flowFunctionSpecs)
            addFunction(unsubscribeFlowFunctionSpec)
            val containingFile = rnModule.wrappedClassDeclaration.containingFile
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
        rnModule: RNModule,
        packageName: String,
        wrappedClassName: String,
        constructorParameters: List<ParameterSpec>,
        constructorInvocation: CodeBlock,
        withConstants: Boolean,
        withEventEmitter: Boolean,
    ) {
        val className = wrappedClassName.iOSModuleClassName()
        val coroutineScopeVarName = "coroutineScope"
        val promiseVarName = "promise"
        val wrappedModuleVarName = "wrappedModule"

        val functionSpecs = rnModule.reactNativeMethods.map { functionDeclaration ->
            iosReactNativeFunctionSpec(
                functionDeclaration,
                promiseVarName,
                coroutineScopeVarName,
                wrappedModuleVarName
            )
        }

        val flowFunctionSpecs = rnModule.reactNativeFlows.map { functionDeclaration ->
            iosReactNativeFunctionSpec(
                functionDeclaration,
                promiseVarName,
                coroutineScopeVarName,
                wrappedModuleVarName,
                isReactNativeFlow = true,
            )
        }

        val unsubscribeFlowFunctionSpec = FunSpec.builder("unsubscribeFromToolkitUseFlow").run {
            val subscriptionIdVarName = "subscriptionId"
            addParameter(subscriptionIdVarName, String::class)
            addParameter(promiseVarName, PromiseIOSClassName)
            addCode(
                promiseVarName.promiseLaunchIOS(
                    coroutineScopeVarName,
                    useJsonSerialization = true,
                    CodeBlock.of(
                        "%M(%N)",
                        UnsubscribeFromFlowMember,
                        subscriptionIdVarName,
                    )
                )
            )
        }.build()

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (rnModule.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(coroutineScopeVarName, CoroutineScopeClassName)
                    .addParameters(constructorParameters)
                    .build()
            )
            superclass(ClassName("platform.darwin", "NSObject"))
            addSuperinterface(RCTBridgeModuleProtocolClassName)
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
                                listOfCode(rnModule.supportedEvents.map {
                                    CodeBlock.of("%S", it)
                                })
                            )
                        }).build()
                )
                addFunction(
                    FunSpec.builder("addListener").run {
                        val eventNameVarname = "eventName"
                        addParameter(eventNameVarname, STRING)
                        addParameter(promiseVarName, PromiseIOSClassName)
                        addCode(
                            promiseVarName.promiseLaunchIOS(
                                coroutineScopeVarName,
                                useJsonSerialization = false,
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
                        addParameter(promiseVarName, PromiseIOSClassName)
                        addCode(
                            promiseVarName.promiseLaunchIOS(
                                coroutineScopeVarName,
                                useJsonSerialization = false,
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
                        RCTBridgeMethodWrapperClassName,
                        "addListener",
                        argsVarName,
                        promiseVarName,
                        "addListener",
                        argsVarName,
                        STRING,
                        promiseVarName,
                        PromiseIOSClassName,
                    ),
                    CodeBlock.of(
                        "%T(%S) { %N, %N -> %N(%N[0] as %T, %N as %T) }",
                        RCTBridgeMethodWrapperClassName,
                        "removeListeners",
                        argsVarName,
                        promiseVarName,
                        "removeListeners",
                        argsVarName,
                        DOUBLE,
                        promiseVarName,
                        PromiseIOSClassName,
                    ),
                )
            } else {
                emptyList()
            }

            addProperty(
                PropertySpec.builder(wrappedModuleVarName, ClassName(packageName, wrappedClassName))
                    .addModifiers(KModifier.PRIVATE).initializer(constructorInvocation).build()
            )
            if (rnModule.reactNativeEvents.isNotEmpty()) {
                addInitializerBlock(
                    CodeBlock.builder().apply {
                        rnModule.reactNativeEvents.forEach { event ->
                            addStatement(
                                "%N.%N().%M(%N, %S, %N)",
                                wrappedModuleVarName,
                                event.functionDeclaration.simpleName.asString(),
                                FlowToEventEmitterMember,
                                eventEmitterPropertyName,
                                event.name,
                                coroutineScopeVarName,
                            )
                        }
                    }.build()
                )
            }

            val bridgeMethodWrappers = rnModule.reactNativeMethods.map { ksFunctionDeclaration ->
                iosBridgeMethodWrapper(ksFunctionDeclaration, promiseVarName)
            }

            val flowBridgeMethodWrappers = rnModule.reactNativeFlows.map { ksFunctionDeclaration ->
                iosBridgeMethodWrapper(
                    ksFunctionDeclaration,
                    promiseVarName,
                    isReactNativeFlow = true
                )
            }

            val unsubscribeBridgeMethodWrapper = run {
                val argsVarName = "args"
                CodeBlock.of(
                    "%T(%S) { %N, %N -> %N(%N[0] as %T, %N as %T) }",
                    RCTBridgeMethodWrapperClassName,
                    "unsubscribeFromToolkitUseFlow",
                    argsVarName,
                    promiseVarName,
                    "unsubscribeFromToolkitUseFlow",
                    argsVarName,
                    STRING,
                    promiseVarName,
                    PromiseIOSClassName,
                )
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
                            listOfCode(
                                buildList {
                                    addAll(bridgeMethodWrappers)
                                    addAll(flowBridgeMethodWrappers)
                                    add(unsubscribeBridgeMethodWrapper)
                                    addAll(eventEmitterFunctionsToExpose)
                                }
                            )
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
                                wrappedModuleVarName,
                                "getConstants"
                            )
                        ).build()
                )
            }

            addFunctions(functionSpecs)
            addFunctions(flowFunctionSpecs)
            addFunction(unsubscribeFlowFunctionSpec)

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
                            CodeBlock.of("return %S", rnModule.moduleName)
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
            }.build())

            val containingFile = rnModule.wrappedClassDeclaration.containingFile
            if (containingFile != null) {
                addOriginatingKSFile(containingFile)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, className)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }


    private fun androidReactNativeFunctionSpec(
        functionDeclaration: KSFunctionDeclaration,
        coroutineScopeVarName: String,
        wrappedModuleVarName: String,
        isReactNativeFlow: Boolean = false,
    ): FunSpec {
        val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }
        val subscriptionIdVarName = "subscriptionId"
        val previousVarName = "previous"
        val promiseVarName = "promise"
        val useJsonSerialization = true

        return FunSpec.builder(functionDeclaration.simpleName.asString()).apply {
            addAnnotation(ReactNativeMethodAndroidClassName)
            if (isReactNativeFlow) {
                addParameter(subscriptionIdVarName, STRING)
                addParameter(previousVarName, STRING.copy(nullable = true))
            }
            addParameters(parameters.map(if (useJsonSerialization) ::mapKotlinTypeToReactNativeAndroidTypeJson else ::mapKotlinTypeToReactNativeAndroidType))
            addParameter(promiseVarName, ClassName("com.facebook.react.bridge", "Promise"))
            addCode(
                promiseVarName.promiseLaunchAndroid(
                    coroutineScopeVarName,
                    useJsonSerialization,
                    buildCodeBlock {
                        add(
                            "%N.%N(%L)",
                            wrappedModuleVarName,
                            functionDeclaration.simpleName.asString(),
                            parameters.map(if (useJsonSerialization) ::transformReactNativeAndroidValueToKotlinValueJson else ::transformReactNativeAndroidValueToKotlinValue)
                                .joinToCode()
                        )
                        if (isReactNativeFlow) {
                            add(
                                ".%M(%N, %N)",
                                FlowToReactMember,
                                subscriptionIdVarName,
                                previousVarName
                            )
                        }
                    }
                )
            )
        }.build()
    }

    private fun iosReactNativeFunctionSpec(
        functionDeclaration: KSFunctionDeclaration,
        promiseVarName: String,
        coroutineScopeVarName: String,
        wrappedModuleVarName: String,
        isReactNativeFlow: Boolean = false,
    ): FunSpec {
        val parameters = functionDeclaration.parameters.map { it.toParameterSpec() }
        val subscriptionIdVarName = "subscriptionId"
        val previousVarName = "previous"
        val useJsonSerialization = true

        return FunSpec.builder(functionDeclaration.simpleName.asString()).apply {
            if (isReactNativeFlow) {
                addParameter(subscriptionIdVarName, STRING)
                addParameter(previousVarName, STRING.copy(nullable = true))
            }
            addParameters(parameters.map(if (useJsonSerialization) ::mapKotlinTypeToReactNativeIOSTypeJson else ::mapKotlinTypeToReactNativeIOSType))
            addParameter(promiseVarName, PromiseIOSClassName)
            addCode(
                promiseVarName.promiseLaunchIOS(
                    coroutineScopeVarName,
                    useJsonSerialization,
                    buildCodeBlock {
                        add(
                            "%N.%N(%L)",
                            wrappedModuleVarName,
                            functionDeclaration.simpleName.asString(),
                            parameters.map(if (useJsonSerialization) ::transformReactNativeIOSValueToKotlinValueJson else ::transformReactNativeIOSValueToKotlinValue)
                                .joinToCode()
                        )
                        if (isReactNativeFlow) {
                            add(
                                ".%M(%N, %N)",
                                FlowToReactMember,
                                subscriptionIdVarName,
                                previousVarName
                            )
                        }
                    }
                )
            )
        }.build()
    }

    private fun iosBridgeMethodWrapper(
        ksFunctionDeclaration: KSFunctionDeclaration,
        promiseVarName: String,
        isReactNativeFlow: Boolean = false,
    ): CodeBlock {
        val useJsonSerialization = true
        val functionName = ksFunctionDeclaration.simpleName.asString()
        return buildCodeBlock {
            val argsVarName =
                if (isReactNativeFlow || ksFunctionDeclaration.parameters.isNotEmpty()) {
                    "args"
                } else {
                    "_"
                }
            add(
                "%T(%S) { %N, %N -> %N(",
                RCTBridgeMethodWrapperClassName,
                functionName,
                argsVarName,
                promiseVarName,
                functionName,
            )
            add(
                buildList {
                    if (isReactNativeFlow) {
                        // subscriptionId
                        add(
                            CodeBlock.of(
                                "%N[%L] as %T",
                                argsVarName,
                                0,
                                STRING
                            )
                        )
                        // previous
                        add(
                            CodeBlock.of(
                                "%N[%L] as %T",
                                argsVarName,
                                1,
                                STRING.copy(nullable = true)
                            )
                        )
                    }
                    addAll(ksFunctionDeclaration.parameters.map { it.toParameterSpec() }
                        .map<ParameterSpec, ParameterSpec>(if (useJsonSerialization) ::mapKotlinTypeToReactNativeIOSTypeJson else ::mapKotlinTypeToReactNativeIOSType)
                        .mapIndexed { index, parameter ->
                            CodeBlock.of(
                                "%N[%L] as %T",
                                argsVarName,
                                if (isReactNativeFlow) {
                                    index + 2
                                } else {
                                    index
                                },
                                parameter.type
                            )
                        }
                    )
                    add(CodeBlock.of("%N as %T", promiseVarName, PromiseIOSClassName))
                }.joinToCode()
            )
            add(")")
            add("}")
        }
    }

    /**
     * Transforms a React Native Android type to a Kotlin type.
     */
    private fun transformReactNativeAndroidValueToKotlinValue(parameter: ParameterSpec): CodeBlock {
        val isNullable = parameter.type.isNullable
        return when (val type = parameter.type) {
            is ClassName -> when (type.canonicalName) {
                STRING.canonicalName, BOOLEAN.canonicalName, INT.canonicalName, DOUBLE.canonicalName,
                FLOAT.canonicalName, LONG.canonicalName, NUMBER.canonicalName -> CodeBlock.of(
                    "%N",
                    parameter.name
                )

                else -> error("unsupported type $type")
            }

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

                else -> error("unsupported type $type")
            }

            else -> error("unsupported type $type")
        }
    }

    /**
     * Transforms a React Native Android type to a Kotlin type. Custom types are decoded from JSON.
     */
    private fun transformReactNativeAndroidValueToKotlinValueJson(parameter: ParameterSpec): CodeBlock {
        return when (val type = parameter.type) {
            is ClassName -> when (type.canonicalName) {
                STRING.canonicalName, BOOLEAN.canonicalName, INT.canonicalName, DOUBLE.canonicalName,
                FLOAT.canonicalName, LONG.canonicalName, NUMBER.canonicalName -> CodeBlock.of(
                    "%N",
                    parameter.name
                )

                else -> decodeFromString(CodeBlock.of("%N", parameter.name))
            }

            is ParameterizedTypeName -> decodeFromString(CodeBlock.of("%N", parameter.name))

            else -> error("unsupported type $type")
        }
    }

    /**
     * Maps kotlin type to react native android type.
     */
    private fun mapKotlinTypeToReactNativeAndroidType(parameter: ParameterSpec): ParameterSpec {
        val type = parameter.type
        return ParameterSpec.builder(
            parameter.name,
            when (type) {
                is ClassName -> when (type.canonicalName) {
                    INT.canonicalName -> INT
                    LONG.canonicalName -> LONG
                    FLOAT.canonicalName -> FLOAT
                    DOUBLE.canonicalName -> DOUBLE
                    BOOLEAN.canonicalName -> BOOLEAN
                    STRING.canonicalName -> STRING
                    else -> error("unsupported type $type")
                }.copy(nullable = type.isNullable)

                is ParameterizedTypeName -> when (type.rawType) {
                    List::class.asTypeName() -> ClassName(
                        "com.facebook.react.bridge",
                        "ReadableArray"
                    ).copy(nullable = type.isNullable)

                    Map::class.asTypeName() -> ClassName(
                        "com.facebook.react.bridge",
                        "ReadableMap"
                    ).copy(nullable = type.isNullable)

                    else -> error("unsupported type $type")
                }

                else -> error("unsupported type $type")
            }
        ).build()
    }

    /**
     * Maps kotlin type to react native android type, custom types are mapped to string because
     * they are serialized to json and deserialized back to kotlin type.
     */
    private fun mapKotlinTypeToReactNativeAndroidTypeJson(parameter: ParameterSpec): ParameterSpec {
        val type = parameter.type
        return ParameterSpec.builder(
            parameter.name,
            when (type) {
                is ClassName -> when (type.canonicalName) {
                    INT.canonicalName -> INT
                    LONG.canonicalName -> LONG
                    FLOAT.canonicalName -> FLOAT
                    DOUBLE.canonicalName -> DOUBLE
                    BOOLEAN.canonicalName -> BOOLEAN
                    STRING.canonicalName -> STRING
                    else -> null
                }?.copy(nullable = type.isNullable) ?: STRING

                is ParameterizedTypeName -> STRING

                else -> error("unsupported type $type")
            }
        ).build()
    }

    /**
     * Transforms a React Native iOS type to a Kotlin type.
     */
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

    /**
     * Transforms a React Native iOS type to a Kotlin type.
     */
    private fun transformReactNativeIOSValueToKotlinValueJson(
        parameter: ParameterSpec,
    ): CodeBlock {
        val isNullable = parameter.type.isNullable
        return when (val type = parameter.type) {
            is ClassName -> when (type.canonicalName) {
                STRING.canonicalName, BOOLEAN.canonicalName, DOUBLE.canonicalName, NUMBER.canonicalName -> CodeBlock.of(
                    "%N",
                    parameter.name
                )

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

                else -> decodeFromString(CodeBlock.of("%N", parameter.name))
            }

            is ParameterizedTypeName -> decodeFromString(CodeBlock.of("%N", parameter.name))
            else -> error("unsupported type $type")
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
                    else -> type.copy(nullable = type.isNullable)
                }

                else -> type.copy(nullable = type.isNullable)
            }
        ).build()
    }

    private fun mapKotlinTypeToReactNativeIOSTypeJson(parameter: ParameterSpec): ParameterSpec {
        val type = parameter.type
        return ParameterSpec.builder(
            parameter.name,
            when (type) {
                is ClassName -> when (type.canonicalName) {
                    DOUBLE.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    BOOLEAN.canonicalName -> BOOLEAN.copy(nullable = type.isNullable)
                    STRING.canonicalName -> STRING.copy(nullable = type.isNullable)
                    INT.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    LONG.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    FLOAT.canonicalName -> DOUBLE.copy(nullable = type.isNullable)
                    else -> STRING
                }

                is ParameterizedTypeName -> STRING

                else -> error("unsupported type $type")
            }
        ).build()
    }
}

internal fun KSValueParameter.toParameterSpec(): ParameterSpec {
    return ParameterSpec.builder(
        this.name?.asString() ?: error("Parameter must have a name"),
        try {
            this.type.toTypeName()
        } catch (e: Throwable) {
            throw IllegalArgumentException("Could get type of $this at ${this.location}", e)
        },
    ).build()
}

private fun String.promiseLaunchAndroid(
    coroutineScopeVarName: String,
    useJsonSerialization: Boolean,
    block: CodeBlock,
) = buildCodeBlock {
    addStatement(
        "%N.%M(%N) { %L }",
        this@promiseLaunchAndroid,
        MemberName(
            "$toolkitPackageName.util",
            if (useJsonSerialization) "launchJson" else "launch"
        ),
        coroutineScopeVarName,
        block
    )
}

private fun String.promiseLaunchIOS(
    coroutineScopeVarName: String,
    useJsonSerialization: Boolean,
    block: CodeBlock,
) = buildCodeBlock {
    addStatement(
        "%N.%M(%N) { %L }",
        this@promiseLaunchIOS,
        MemberName(
            "$toolkitPackageName.util",
            if (useJsonSerialization) "launchJson" else "launch"
        ),
        coroutineScopeVarName,
        block
    )
}

internal fun encodeToString(code: CodeBlock) = CodeBlock.of(
    "%T.%M(%L)",
    JsonClassName,
    EncodeToStringMember,
    code
)

internal fun decodeFromString(code: CodeBlock) = CodeBlock.of(
    "%T.%M(%L)",
    JsonClassName,
    DecodeFromStringMember,
    code
)

private fun listOfCode(code: List<CodeBlock>) = CodeBlock.of("%M(%L)", ListOfMember, code.joinToCode())

internal val reactNativeInteropNamespace = "react_native"
internal val toolkitPackageName = "de.voize.reaktnativetoolkit"
internal val toolkitUtilPackageName = "$toolkitPackageName.util"

private val ReactNativeMethodAndroidClassName =
    ClassName("com.facebook.react.bridge", "ReactMethod")
private val ReactApplicationContextClassName =
    ClassName("com.facebook.react.bridge", "ReactApplicationContext")
private val CoroutineScopeClassName = ClassName("kotlinx.coroutines", "CoroutineScope")
private val PromiseIOSClassName = ClassName(toolkitUtilPackageName, "PromiseIOS")
private val EventEmitterIOS = ClassName(toolkitUtilPackageName, "EventEmitterIOS")

private val ReactNativeModuleProviderClassName = ClassName(toolkitUtilPackageName, "ReactNativeModuleProvider")
private val ReactNativeModuleBaseClassName = ClassName(toolkitUtilPackageName, "ReactNativeModuleBase")

private val RCTCallableJSModulesClassName =
    ClassName(reactNativeInteropNamespace, "RCTCallableJSModules")
private val RCTBridgeModuleProtocolClassName =
    ClassName(reactNativeInteropNamespace, "RCTBridgeModuleProtocol")
private val ListOfMember = MemberName("kotlin.collections", "listOf")
private val JsonClassName = ClassName("kotlinx.serialization.json", "Json")
private val EncodeToStringMember = MemberName("kotlinx.serialization", "encodeToString")
private val DecodeFromStringMember = MemberName("kotlinx.serialization", "decodeFromString")
private val FlowToReactMember = MemberName(toolkitUtilPackageName, "toReact")
private val FlowToEventEmitterMember = MemberName(toolkitUtilPackageName, "toEventEmitter")
private val UnsubscribeFromFlowMember =
    MemberName(toolkitUtilPackageName, "unsubscribeFromFlow")
private val RCTBridgeMethodWrapperClassName =
    ClassName(toolkitUtilPackageName, "RCTBridgeMethodWrapper")