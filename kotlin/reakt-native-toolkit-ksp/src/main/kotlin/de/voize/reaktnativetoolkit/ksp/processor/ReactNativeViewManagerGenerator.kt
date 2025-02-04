package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MUTABLE_MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private const val generatedObjcFilePath = "$generatedCommonFilePath/objc/"

class ReactNativeViewManagerGenerator(
    private val codeGenerator: CodeGenerator,
    private val platforms: List<PlatformInfo>,
    private val options: Map<String, String>,
    private val logger: KSPLogger,
) {
    internal data class RNViewManager(
        val wrappedFunctionDeclaration: KSFunctionDeclaration,
        val moduleName: String,
        val isInternal: Boolean,
        val reactNativeProps: List<ReactNativeProp>,
        val restParameters: List<KSValueParameter>,
    ) {
        sealed class ReactNativeProp {
            data class ValueProp(
                val name: String,
                val typeArgument: KSType,
            ) : ReactNativeProp()

            data class FunctionProp(
                val name: String,
                val parameters: List<KSType>,
            ) : ReactNativeProp()
        }

        val packageName
            get() = wrappedFunctionDeclaration.packageName.asString()
        val functionName
            get() = wrappedFunctionDeclaration.simpleName.asString()
    }

    private var objcGenerationInvoked = false
    private var typescriptGenerationInvoked = false

    private val iosFrameworkName = "shared"
    private val iosFrameworkNameCapitalized = iosFrameworkName.replaceFirstChar { it.uppercase() }

    /**
     * Corresponds to de.voize.reaktnativetoolkit.util.ReactNativeIOSViewWrapper
     */
    private val toolkitReactNativeIOSViewWrapperInterfaceTypeName =
        "${iosFrameworkNameCapitalized}Reakt_native_toolkitReactNativeIOSViewWrapper"

    /**
     * Corresponds to de.voize.reaktnativetoolkit.util.ReactNativeIOSViewWrapperFactory
     */
    private val toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName =
        "${iosFrameworkNameCapitalized}Reakt_native_toolkitReactNativeIOSViewWrapperFactory"

    private fun String.androidViewManagerClassName() = this + "RNViewManagerAndroid"
    private fun String.iOSViewWrapperClassName() = this + "RNViewWrapperIOS"
    private fun String.iOSViewWrapperFactoryClassName() = this + "RNViewWrapperFactoryIOS"
    private fun String.iOSViewManagerObjcClassName(namespace: String) = namespace + this + "RNViewManagerObjCIos"
    private fun String.viewManagerProviderClassName() = this + "RNViewManagerProvider"
    private fun String.toRNViewManagerPropSetter() = "set${this.replaceFirstChar { it.uppercase() }}"

    internal fun process(resolver: Resolver): ToolkitSymbolProcessor.ProcessResult {
        val reactNativeViewManagerAnnotationType = resolver.getClassDeclarationByName("$toolkitPackageName.annotation.ReactNativeViewManager")
            ?.asType(emptyList())
            ?: error("Could not find ReactNativeViewManager")
        val reactNativePropAnnotationType = resolver.getClassDeclarationByName("$toolkitPackageName.annotation.ReactNativeProp")
            ?.asType(emptyList())
            ?: error("Could not find ReactNativeProp")

        // this must be lazy so that projects not using Compose do not fail on this because Composable is not found
        val composableAnnotationType by lazy {
            resolver.getClassDeclarationByName("androidx.compose.runtime.Composable")
                ?.asType(emptyList())
                ?: error("Could not find Composable")
        }

        val rnViewManagerSymbols = resolver.getSymbolsWithAnnotation("$toolkitPackageName.annotation.ReactNativeViewManager")
        val (validRNViewManagerSymbols, invalidRNViewManagerSymbols) = rnViewManagerSymbols.partition {
            it.validate()
        }

        val rnViewManagers = validRNViewManagerSymbols
            .map { annotatedNode ->
                when (annotatedNode) {
                    is KSFunctionDeclaration -> annotatedNode.also {
                        check(annotatedNode.annotations.any { it.annotationType.resolve() == composableAnnotationType }) {
                            "Function must be annotated with @Composable"
                        }
                    }

                    else -> throw IllegalArgumentException("ReactNativeViewManager annotation can only be used on function declarations")
                }
            }.map { wrappedFunctionDeclaration ->
                wrappedFunctionDeclaration.toRNViewManager(
                    reactNativeViewManagerAnnotationType,
                    reactNativePropAnnotationType,
                )
            }.toList()

        rnViewManagers.forEach { rnViewManager ->
            if (platforms.isAndroid()) {
                generateAndroidViewManager(rnViewManager)
                generateAndroidViewManagerProvider(rnViewManager)
            }

            if (platforms.isIOS()) {
                generateIOSViewWrapper(rnViewManager)
                generateIOSViewWrapperFactory(rnViewManager)
                generateIOSViewManagerProvider(rnViewManager)
            }

            if (platforms.isCommon()) {
                generateCommonViewManagerProvider(rnViewManager)
            }
        }

        // We generate Objective-C code when we are on the Multiplatform target.
        //
        // This generates them even when they are not needed (on Android) but it is the best way
        // to have a reliable location for the generated Objective-C code
        // that does not change based on the iOS target (arm64, x64, simulator arm64)
        // so the files can be referenced from the XCode project.
        if (
            invalidRNViewManagerSymbols.isEmpty() &&
            rnViewManagers.isNotEmpty() &&
            !objcGenerationInvoked &&
            platforms.isCommon()
        ) {
            val namespace = options["reakt.native.toolkit.iosObjcNamespace"] ?: ""
            val objcViewManagersCode = rnViewManagers.map {
                generateIOSViewManagerObjcCode(it, namespace)
            }.toList()
            generateObjcReactNativeViewManagersFiles(objcViewManagersCode, namespace)
            objcGenerationInvoked = true
        }

        if (
            invalidRNViewManagerSymbols.isEmpty() &&
            rnViewManagers.isNotEmpty() &&
            !typescriptGenerationInvoked &&
            platforms.isCommon()
        ) {
            ReactNativeViewManagerTypescriptGenerator(
                codeGenerator,
                TypescriptConfig.fromOptions(options),
                logger,
            ).generate(rnViewManagers)
            typescriptGenerationInvoked = true
        }

        val (types, originatingFiles) = typesFrom(rnViewManagers)

        return ToolkitSymbolProcessor.ProcessResult(
            deferredSymbols = invalidRNViewManagerSymbols,
            types = types,
            originatingFiles = originatingFiles,
        )
    }

    /**
     * Collect all types of function parameters and return types.
     */
    private fun typesFrom(
        rnViewManagers: List<RNViewManager>
    ): Pair<List<KSType>, List<KSFile>> {
        val typeDeclarations =
            rnViewManagers.flatMap { it.reactNativeProps }.flatMap {
                when (it) {
                    is RNViewManager.ReactNativeProp.ValueProp -> listOf(it.typeArgument)
                    is RNViewManager.ReactNativeProp.FunctionProp -> it.parameters
                }
            }.distinct()
        val originatingKSFiles = rnViewManagers.mapNotNull { it.wrappedFunctionDeclaration.containingFile }
        return typeDeclarations to originatingKSFiles
    }

    /**
     * Given the metadata of a compose function annotated with `@ReactNativeViewManager`
     * generates a React Native View Manager for Android that renders the annotated compose function.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * class <class name of annotated compose function>RNViewManagerAndroid(
     *     <... compose function parameters>
     * ) : SimpleViewManager<ComposeViewWrapper>() {
     *     override fun getName() = "<view manager name specified in annotation>"
     *
     *     @ReactProp(name = "<value prop name>")
     *     fun set<value prop name>(view: ComposeView, value: <type of prop>) {
     *          <value prop name>.value = StateOrInitial.State(value)
     *     }
     *
     *     private fun <lambda prop name>(context: ReactContext, id: Int) {
     *          context.getJSModule(RCTEventEmitter::class.java).receiveEvent(id, "<lambda prop name>", null)
     *     }
     *
     *     override fun getExportedCustomBubblingEventTypeConstants(): Map<String, Any> {
     *        return mapOf(
     *              "<lambda prop name>" to mapOf(
     *                    "phasedRegistrationNames" to mapOf(
     *                          "bubbled" to "<lambda prop name>"
     *                    )
     *              )
     *         )
     *     }
     *
     *     class ComposeViewWrapper(
     *          private val reactContext: ThemedReactContext,
     *          <... compose function parameters>
     *     ): AbstractComposeView(reactContext) {
     *          private val <value prop name> = MutableStateFlow<StateOrInitial<type of value prop>>(StateOrInitial.Initial)
     *
     *          init {
     *              // workaround for "Cannot locate windowRecomposer" error
     *              // when compose view is rendered within a FlatList
     *              val recomposer = Recomposer(EmptyCoroutineContext)
     *              setParentCompositionContext(recomposer)
     *              doOnAttach { setParentCompositionContext(null) }
     *          }
     *
     *          @Composable
     *          override fun Content() {
     *              val <value prop name>State by <value prop name>.collectAsState()
     *
     *              if (<value prop name>State !is StateOrInitial.State) {
     *                  return
     *              }
     *
     *              <class name of annotated compose function>(
     *                  <value prop name> = <value prop name>State.value,
     *                  <lambda prop name> = <lambda prop name>(reactContext as ReactContext, id),
     *                  <... compose function parameters>,
     *              )
     *          }
     *     }
     *
     *     override fun createViewInstance(reactContext: ThemedReactContext): ComposeViewWrapper {
     *         return ComposeViewWrapper(reactContext, <... compose function parameters>)
     *     }
     * }
     * ```
     */
    private fun generateAndroidViewManager(rnViewManager: RNViewManager) {
        val wrappedFunctionName = rnViewManager.wrappedFunctionDeclaration.simpleName.asString()
        val viewManagerClassName = wrappedFunctionName.androidViewManagerClassName()
        val packageName = rnViewManager.wrappedFunctionDeclaration.packageName.asString()
        val composeViewWrapperClassName = ClassName(
            packageName,
            viewManagerClassName,
            "ComposeViewWrapper",
        )

        val classSpec = TypeSpec.classBuilder(viewManagerClassName).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )

            superclass(ReactSimpleViewManagerClassName.parameterizedBy(composeViewWrapperClassName))

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addFunction(
                FunSpec.builder("getName")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(String::class)
                    .addStatement("return %S", rnViewManager.moduleName)
                    .build()
            )

            addType(TypeSpec.classBuilder(composeViewWrapperClassName).apply {
                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("reactContext", ReactThemedReactContextClassName)
                        .addParameters(constructorParameters)
                        .build()
                )

                superclass(AbstractComposeViewClassName)
                addSuperclassConstructorParameter("reactContext")

                addProperty(
                    PropertySpec.builder("reactContext", ReactThemedReactContextClassName)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer("reactContext")
                        .build()
                )
                constructorParameters.forEach { parameter ->
                    addProperty(
                        PropertySpec.builder(parameter.name, parameter.type)
                            .addModifiers(KModifier.PRIVATE)
                            .initializer(parameter.name)
                            .build()
                    )
                }

                rnViewManager.reactNativeProps.forEach { prop ->
                    when (prop) {
                        is RNViewManager.ReactNativeProp.ValueProp -> {
                            addProperty(
                                PropertySpec.builder(
                                    prop.name,
                                    MutableStateFlowClassName.parameterizedBy(
                                        StateOrInitialClassName.parameterizedBy(
                                            prop.typeArgument.toTypeName()
                                        )
                                    )
                                )
                                    .initializer(
                                        "%T(%T.Initial)",
                                        MutableStateFlowClassName,
                                        StateOrInitialClassName,
                                    )
                                    .build()
                            )
                        }
                        is RNViewManager.ReactNativeProp.FunctionProp -> {
                            // nothing to do here
                        }
                    }
                }

                addInitializerBlock(
                    CodeBlock.builder()
                        .addStatement(
                            """
                            // workaround for "Cannot locate windowRecomposer" error
                            // when compose view is rendered within a FlatList
                            val recomposer = %T(%T)
                            setParentCompositionContext(recomposer)
                            %T { setParentCompositionContext(null) }
                            """.trimIndent(),
                            ClassName("androidx.compose.runtime", "Recomposer"),
                            ClassName("kotlin.coroutines", "EmptyCoroutineContext"),
                            ClassName("androidx.core.view", "doOnAttach"),
                        )
                        .build()
                )

                fun String.toStateVarName() = "${this}State"

                addFunction(
                    FunSpec
                        .builder("Content")
                        .addModifiers(KModifier.OVERRIDE)
                        .addAnnotation(ClassName("androidx.compose.runtime", "Composable"))
                        .addCode(CodeBlock.Builder().apply {
                            rnViewManager.reactNativeProps.forEach { prop ->
                                when (prop) {
                                    is RNViewManager.ReactNativeProp.ValueProp -> {
                                        addStatement(
                                            "val %L = %L.%T().value",
                                            prop.name.toStateVarName(),
                                            prop.name,
                                            CollectAsStateClassName,
                                        )
                                        addStatement(
                                            "if (%L !is %T.State) { return }",
                                            prop.name.toStateVarName(),
                                            StateOrInitialClassName,
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }.build())
                        .addCode(CodeBlock.of(
                            "%T(%L)",
                            ClassName(rnViewManager.packageName, wrappedFunctionName),
                            CodeBlock.Builder().apply {
                                rnViewManager.reactNativeProps.forEach { prop ->
                                    when (prop) {
                                        is RNViewManager.ReactNativeProp.ValueProp -> {
                                            add(
                                                "%L = %L.value,\n",
                                                prop.name,
                                                prop.name.toStateVarName()
                                            )
                                        }
                                        is RNViewManager.ReactNativeProp.FunctionProp -> {
                                            add(
                                                "%L = { %L -> %L },\n",
                                                prop.name,
                                                prop.parameters
                                                    .withIndex()
                                                    .joinToString { "arg${it.index}" },
                                                generateAndroidEventLambda(prop),
                                            )
                                        }
                                    }
                                }
                                constructorParameters.forEach { parameter ->
                                    add(
                                        "%L = %L,\n",
                                        parameter.name,
                                        parameter.name,
                                    )
                                }
                            }.build()
                        ))
                        .build()
                )
            }.build())

            rnViewManager.reactNativeProps.forEach { prop ->
                when (prop) {
                    is RNViewManager.ReactNativeProp.ValueProp -> {
                        val setterName = prop.name.toRNViewManagerPropSetter()
                        val varName = "value"
                        val viewVarName = "view"

                        addFunction(
                            FunSpec.builder(setterName)
                                .addModifiers(KModifier.PUBLIC)
                                .addAnnotation(
                                    AnnotationSpec.builder(ReactPropClassName)
                                        .addMember("name = %S", prop.name)
                                        .build()
                                )
                                .addParameter(
                                    ParameterSpec.builder(
                                        viewVarName,
                                        composeViewWrapperClassName,
                                    ).build()
                                )
                                .addParameter(
                                    ParameterSpec.builder(
                                        varName,
                                        if (prop.typeArgument.declaration.requiresSerialization()) {
                                            STRING
                                        } else prop.typeArgument.toTypeName(),
                                    ).build()
                                )
                                .addStatement(
                                    "%L.%L.value = %T.State(%L)",
                                    viewVarName,
                                    prop.name,
                                    StateOrInitialClassName,
                                    if (prop.typeArgument.declaration.requiresSerialization()) {
                                        decodeFromString(CodeBlock.of("%N", varName))
                                    } else {
                                        varName
                                    }
                                )
                                .build()
                        )
                    }
                    is RNViewManager.ReactNativeProp.FunctionProp -> {
                        // nothing to do here
                    }
                }
            }

            val lambdaPropNames = rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().map { it.name }

            if (lambdaPropNames.isNotEmpty()) {
                addFunction(
                    FunSpec.builder("getExportedCustomBubblingEventTypeConstants")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(Map::class.parameterizedBy(String::class, Any::class))
                        .addStatement(
                            """
                        return mapOf(
                            %L
                        )
                        """.trimIndent(),
                            lambdaPropNames.joinToString(separator = ",\n") { propName ->
                                CodeBlock.Builder().apply {
                                    add(
                                        "%S to mapOf(\"phasedRegistrationNames\" to mapOf(\"bubbled\" to %S))",
                                        propName,
                                        propName
                                    )
                                }.build().toString()
                            }
                        )
                        .build()
                )
            }

            addFunction(
                FunSpec.builder("createViewInstance")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("reactContext", ReactThemedReactContextClassName)
                    .returns(composeViewWrapperClassName)
                    .addStatement(
                        "return %T(reactContext, %L)",
                        composeViewWrapperClassName,
                        constructorParameters.map { CodeBlock.of("%N", it) }.joinToCode()
                    )
                    .build()
            )

            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, viewManagerClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    /**
     * ```kotlin
     * (reactContext as ReactContext)
     *     .getJSModule(RCTEventEmitter::class.java)
     *     .receiveEvent(id, "<name of lambda prop>", null)
     * ```
     */
    private fun generateAndroidEventLambda(prop: RNViewManager.ReactNativeProp.FunctionProp): CodeBlock {
        return CodeBlock.builder().apply {
            add(
                """
                (reactContext as %T)
                    .getJSModule(%T::class.java)
                    .receiveEvent(
                        id, 
                        %S, 
                        %T.createMap().apply { 
                            putArray("args", %L) 
                        }
                    )
                """.trimIndent(),
                ReactContextClassName,
                RCTEventEmitterClassName,
                prop.name,
                ArgumentsClassName,
                CodeBlock.Builder().apply {
                    if (prop.parameters.isNotEmpty()) {
                        add(
                            "%T.fromList(listOf(%L))",
                            ArgumentsClassName,
                            prop.parameters.withIndex().map {
                                if (it.value.declaration.requiresSerialization()) {
                                    encodeToString(CodeBlock.of("arg${it.index}"))
                                } else {
                                    CodeBlock.of("arg${it.index}")
                                }
                            }.joinToCode(", ")
                        )
                    } else {
                        add("%T.createArray()", ArgumentsClassName)
                    }
                }.build()
            )
        }.build()
    }

    /**
     * Given the metadata of a compose function annotated with `@ReactNativeViewManager`
     * generates a ViewWrapper for iOS that renders the annotated compose function.
     * The wrapper is responsible for wiring event lambdas and
     * intermediately storing props and forwarding them to the compose function
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import androidx.compose.runtime.ExperimentalComposeApi
     * import androidx.compose.ui.window.ComposeUIViewController
     * import de.voize.reaktnativetoolkit.util.ReactNativeIOSViewWrapper
     * import kotlinx.coroutines.flow.MutableStateFlow
     * import platform.UIKit.UIView
     *
     * class <class name of annotated compose function>RNViewWrapperIOS(
     *   <... compose function parameters>
     * ) : ReactNativeIOSViewWrapper() {
     *     private val callbacks: Map<String, (args: Map<String, Any>) -> Unit> = mutableMapOf()
     *
     *     override fun registerCallback(withName: String, callback: (args: Map<String, Any>) -> Unit) {
     *         callbacks[withName] = callback
     *     }
     *
     *     private val <value prop name> = MutableStateFlow<StateOrInitial<type of value prop>>(StateOrInitial.Initial)
     *
     *     public fun setPropValue(withName: String, value: Any) {
     *         when (withName) {
     *             "<value prop name>" -> <value prop name>.value = StateOrInitial.State(value as <type of value prop>)
     *             ...
     *         }
     *     }
     *
     *     @OptIn(ExperimentalComposeApi::class)
     *     public fun view(): UIView = ComposeUIViewController({ opaque = false }) {
     *          val <value prop name>State by <value prop name>.collectAsState()
     *
     *          if (<value prop name>State !is StateOrInitial.State) {
     *              return
     *          }
     *
     *         <class name of annotated compose function>(
     *              <value prop name> = <value prop name>State.value,
     *              <function prop name> = { arg0, arg1 ->
     *                  callbacks.getValue("<function prop name>")(mapOf("args" to listOf(arg0, arg1)))
     *              },
     *              <... compose function parameters>,
     *          )
     *     }.view
     * }
     * ```
     */
    private fun generateIOSViewWrapper(rnViewManager: RNViewManager) {
        val viewWrapperClassName = rnViewManager.functionName.iOSViewWrapperClassName()
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(viewWrapperClassName).apply {
            // This class can not be internal, even when the annotated function is internal.
            // This is because the generated Objective-C code needs to
            // be able to access this class from the shared framework.

            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addSuperinterface(ReactNativeIOSViewWrapperClassName)

            val hasFunctionProps = rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().isNotEmpty()

            if (hasFunctionProps) {
                addProperty(
                    PropertySpec.builder(
                        "callbacks",
                        MUTABLE_MAP.parameterizedBy(STRING, LambdaTypeName.get(
                            receiver = null,
                            parameters = listOf(ParameterSpec.builder("args", Map::class.parameterizedBy(String::class, Any::class)).build()),
                            returnType = UNIT
                        ))
                    ).addModifiers(KModifier.PRIVATE)
                        .initializer("mutableMapOf()")
                        .build()
                )
            }

            addFunction(
                FunSpec.builder("registerCallback")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("withName", STRING)
                    .addParameter("callback", LambdaTypeName.get(
                        receiver = null,
                        parameters = listOf(ParameterSpec.builder("args", Map::class.parameterizedBy(String::class, Any::class)).build()),
                        returnType = UNIT
                    ))
                    .apply {
                        if (hasFunctionProps) {
                            addStatement("callbacks[withName] = callback")
                        } else {
                            addStatement(
                                "error(%S)",
                                "This composable has no function props",
                            )
                        }
                    }
                    .build()
            )

            val valueVarName = "value"

            addFunction(
                FunSpec.builder("setPropValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("withName", STRING)
                    .addParameter(valueVarName, Any::class)
                    .apply {
                        if (rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.ValueProp>().isNotEmpty()) {
                            addStatement(
                                """
                                when (withName) {
                                    %L
                                }
                                """.trimIndent(),
                                rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.ValueProp>().map { prop ->
                                    CodeBlock.Builder().apply {
                                        add(
                                            "%S -> %L.value = %T.State(%L)",
                                            prop.name,
                                            prop.name,
                                            StateOrInitialClassName,
                                            if (prop.typeArgument.declaration.requiresSerialization()) {
                                                decodeFromString(CodeBlock.of("%N as String", valueVarName))
                                            } else {
                                                when (prop.typeArgument.declaration.qualifiedName?.asString()) {
                                                    "kotlin.Int" -> CodeBlock.of("(%N as %T).intValue", valueVarName, NSNumberClassName)
                                                    "kotlin.Double" -> CodeBlock.of("(%N as %T).doubleValue", valueVarName, NSNumberClassName)
                                                    "kotlin.Float" -> CodeBlock.of("(%N as %T).floatValue", valueVarName, NSNumberClassName)
                                                    "kotlin.Boolean" -> CodeBlock.of("(%N as %T).boolValue", valueVarName, NSNumberClassName)
                                                    else -> CodeBlock.of("%N as %T", valueVarName, prop.typeArgument.toTypeName())
                                                }
                                            }
                                        )
                                    }.build()
                                }.joinToCode("\n")
                            )
                        } else {
                            addStatement(
                                "error(%S)",
                                "This composable has no value props",
                            )
                        }
                    }
                    .build()
            )

            rnViewManager.reactNativeProps.forEach { prop ->
                when (prop) {
                    is RNViewManager.ReactNativeProp.ValueProp -> {
                        addProperty(
                            PropertySpec.builder(
                                prop.name,
                                MutableStateFlowClassName.parameterizedBy(
                                    StateOrInitialClassName.parameterizedBy(prop.typeArgument.toTypeName())
                                ),
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .initializer(
                                    "%T(%T.Initial)",
                                    MutableStateFlowClassName,
                                    StateOrInitialClassName,
                                )
                                .build()
                        )
                    }
                    is RNViewManager.ReactNativeProp.FunctionProp -> {}
                }
            }

            fun String.toStateVarName() = "${this}State"

            addFunction(
                FunSpec.builder("view")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .addAnnotation(
                        AnnotationSpec.builder(OptInClassName)
                            .addMember("%T::class", ExperimentalComposeApiClassName)
                            .build()
                    )
                    .returns(UIViewClassName)
                    .addStatement(
                        """
                            return %T({ opaque = false }) {
                                %L
                            
                                %T(%L)
                            }.view
                            """.trimIndent(),
                        ComposeUIViewControllerClassName,
                        CodeBlock.builder().apply {
                            rnViewManager.reactNativeProps.forEach { prop ->
                                when (prop) {
                                    is RNViewManager.ReactNativeProp.ValueProp -> {
                                        addStatement(
                                            "val %L = %L.%T().value",
                                            prop.name.toStateVarName(),
                                            prop.name,
                                            CollectAsStateClassName,
                                        )
                                        addStatement(
                                            "if (%L !is %T.State) { return@%T }",
                                            prop.name.toStateVarName(),
                                            StateOrInitialClassName,
                                            ComposeUIViewControllerClassName,
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }.build(),
                        ClassName(rnViewManager.packageName, rnViewManager.functionName),
                        CodeBlock.builder().apply {
                            rnViewManager.reactNativeProps.forEach { prop ->
                                when (prop) {
                                    is RNViewManager.ReactNativeProp.ValueProp -> {
                                        add(
                                            "%L = %L.value,\n",
                                            prop.name,
                                            prop.name.toStateVarName(),
                                        )
                                    }
                                    is RNViewManager.ReactNativeProp.FunctionProp -> {
                                        add(
                                            "%L = { %L -> %L.getValue(%S)(mapOf(\"args\" to %L)) },\n",
                                            prop.name,
                                            prop.parameters
                                                .withIndex()
                                                .joinToString(", ") { "arg${it.index}" },
                                            "callbacks",
                                            prop.name,
                                            if (prop.parameters.isNotEmpty()) {
                                                CodeBlock.of(
                                                    "listOf(%L)",
                                                    prop.parameters.withIndex().map {
                                                        if (it.value.declaration.requiresSerialization()) {
                                                            encodeToString(CodeBlock.of("arg${it.index}"))
                                                        } else {
                                                            CodeBlock.of("arg${it.index}")
                                                        }
                                                    }.joinToCode(", "),
                                                )
                                            } else {
                                                "emptyList<Any>()"
                                            }
                                        )
                                    }
                                }
                            }
                            constructorParameters.forEach { parameter ->
                                add("%L = %L,\n", parameter.name, parameter.name)
                            }
                        }.build(),
                    )
                    .build()
            )

            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, viewWrapperClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    /**
     * Given the metadata of a compose function annotated with `@ReactNativeViewManager`
     * generates a factory that creates the corresponding iOS view wrapper.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import androidx.compose.ui.window.ComposeUIViewController
     * import de.voize.reaktnativetoolkit.util.ReactNativeIOSViewWrapper
     * import platform.UIKit.UIView
     *
     * class <class name of annotated compose function>RNViewWrapperFactoryIOS(
     *   <... compose function parameters>
     * ) {
     *    public override fun createViewWrapper(): <class name of annotated compose function>RNViewManagerIOS {
     *        return <class name of annotated compose function>RNViewManagerIOS(
     *            <... compose function parameters>
     *        )
     *    )
     * }
     * ```
     */
    private fun generateIOSViewWrapperFactory(rnViewManager: RNViewManager) {
        val factoryClassName = rnViewManager.functionName.iOSViewWrapperFactoryClassName()
        val packageName = rnViewManager.packageName
        val viewWrapperClassName = ClassName(packageName, rnViewManager.functionName.iOSViewWrapperClassName())
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(factoryClassName).apply {
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeIOSViewWrapperFactoryClassName)

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addFunction(
                FunSpec.builder("createViewWrapper")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(viewWrapperClassName)
                    .addStatement(
                        "return %T(%L)",
                        viewWrapperClassName,
                        constructorParameters.map { CodeBlock.of("%N", it.name) }.joinToCode()
                    )
                    .build()
            )

            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(packageName, factoryClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private data class RNViewManagerObjC(
        val viewManager: RNViewManager,
        val implementationCode: String,
    )

    private fun generateObjcReactNativeViewManagersFiles(
        rnViewManagers: List<RNViewManagerObjC>,
        namespace: String,
    ) {
        val objcReactNativeViewManagersFileName = "${namespace}ReactNativeViewManagers"

        val headerCode = """
// Generated by reakt-native-toolkit. Do not modify.

#import <shared/shared.h>

@interface ${namespace}ReactNativeViewManagers : NSObject

+ (NSArray<id<RCTBridgeModule>>*)getRNViewManagers:(NSDictionary<NSString*, id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName>>*)viewWrapperFactory;

@end
        """.trimIndent()

        val implementationCode = """
// Generated by reakt-native-toolkit. Do not modify.
            
#import <React/RCTViewManager.h>
#import <shared/shared.h>
#import "$objcReactNativeViewManagersFileName.h"

${rnViewManagers.joinToString("\n") { it.implementationCode }}

@implementation ${namespace}ReactNativeViewManagers

+ (NSArray<id<RCTBridgeModule>>*)getRNViewManagers:(NSDictionary<NSString*, id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName>>*)viewWrapperFactories
{
    return @[
        ${rnViewManagers.joinToString(",\n") { 
            "[[${it.viewManager.functionName.iOSViewManagerObjcClassName(namespace)} alloc] initWithViewWrapperFactory:viewWrapperFactories[@\"${it.viewManager.moduleName}\"]]" 
        }}
    ];
}

@end
        """.trimIndent()

        val headerFile = codeGenerator.createNewFileByPath(
            dependencies = Dependencies.ALL_FILES,
            path = "$generatedObjcFilePath$objcReactNativeViewManagersFileName",
            extensionName = "h",
        )
        OutputStreamWriter(headerFile, StandardCharsets.UTF_8).use { it.write(headerCode) }
        headerFile.close()

        val implementationFile = codeGenerator.createNewFileByPath(
            dependencies = Dependencies.ALL_FILES,
            path = "$generatedObjcFilePath$objcReactNativeViewManagersFileName",
            extensionName = "m",
        )
        OutputStreamWriter(implementationFile, StandardCharsets.UTF_8).use { it.write(implementationCode) }
        implementationFile.close()
    }

    private fun generateIOSViewManagerObjcCode(
        rnViewManager: RNViewManager,
        namespace: String,
    ): RNViewManagerObjC {
        val className = rnViewManager.functionName.iOSViewManagerObjcClassName(namespace)
        val iosViewClassName = "${className}View"

        val implementationCode = """
@interface $iosViewClassName : UIView

${rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().joinToString("\n") { prop ->
"@property (nonatomic, copy) RCTBubblingEventBlock ${prop.name};"
}}

@property (nonatomic, strong) id<$toolkitReactNativeIOSViewWrapperInterfaceTypeName> viewWrapper;

- (instancetype)initWithViewWrapper:(id<$toolkitReactNativeIOSViewWrapperInterfaceTypeName>)viewWrapper;

@end

@implementation $iosViewClassName : UIView

- (instancetype)initWithViewWrapper:(id<$toolkitReactNativeIOSViewWrapperInterfaceTypeName>)viewWrapper
{
    self = [super init];
    if (self) {
        self.viewWrapper = (id<$toolkitReactNativeIOSViewWrapperInterfaceTypeName>)viewWrapper;
        [self addSubview:self.viewWrapper.view];
    }
    return self;
}

- (void)layoutSubviews
{
    [super layoutSubviews];
    self.subviews.firstObject.frame = self.bounds;
}

@end

@interface $className : RCTViewManager

@property (nonatomic, strong) id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName> viewWrapperFactory;

- (instancetype)initWithViewWrapperFactory:(id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName>
)viewWrapperFactory;

@end

@implementation $className


+ (NSString *)moduleName
{
    return @"${rnViewManager.moduleName}";
}

${rnViewManager.reactNativeProps.map { prop ->
    when (prop) {
        is RNViewManager.ReactNativeProp.ValueProp -> {
            val valueVarName = "json"
            val nsTypeName = prop.typeArgument.toNSTypeName()
            
            """
RCT_CUSTOM_VIEW_PROPERTY(${prop.name}, $nsTypeName, $iosViewClassName)
{
    [view.viewWrapper setPropValueWithName:@"${prop.name}" value:$valueVarName];
}
        """.trimIndent()
        }

        is RNViewManager.ReactNativeProp.FunctionProp -> """
RCT_EXPORT_VIEW_PROPERTY(${prop.name}, RCTBubblingEventBlock)
        """.trimIndent()
    }
}.joinToString("\n")}

- (instancetype)initWithViewWrapperFactory:(id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName>)viewWrapperFactory
{
    self = [super init];
    if (self) {
        self.viewWrapperFactory = (id<$toolkitReactNativeIOSViewWrapperFactoryInterfaceTypeName>)viewWrapperFactory;
    }
    return self;
}

- (UIView *)view
{
    id<$toolkitReactNativeIOSViewWrapperInterfaceTypeName> viewWrapper = [self.viewWrapperFactory createViewWrapper];
    $iosViewClassName *view = [[${iosViewClassName} alloc] initWithViewWrapper:viewWrapper];
    
     ${rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().map { prop ->
"""
[viewWrapper registerCallbackWithName:@"${prop.name}" callback:^(NSDictionary *args) {
    view.${prop.name}(args);
}];
""".trimIndent()
    }.joinToString("\n")}

    return view;
}

@end
        """.trimIndent()

        return RNViewManagerObjC(rnViewManager, implementationCode)
    }

    /**
     * Generates a common code expect class `ReactNativeViewManagerProvider`
     * that abstracts the creation of view managers into common code.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import de.voize.reaktnativetoolkit.util.ReactNativeViewManagerProvider
     *
     * public expect class <class name of annotated compose function>RNViewManagerProvider(
     *   <... compose function parameters>
     * ) : ReactNativeViewManagerProvider
     * ```
     */
    private fun generateCommonViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.EXPECT)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeViewManagerProviderClassName)
            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, className)
            .addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    /**
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import com.facebook.react.uimanager.ViewManager
     * import de.voize.reaktnativetoolkit.util.ReactNativeViewManagerProvider
     *
     * public actual class <class name of annotated compose function>RNViewManagerProvider actual constructor(
     *   <... compose function parameters>
     * ) : ReactNativeViewManagerProvider {
     *   public override fun getViewManager(): ViewManager<*, *> = <class name of annotated compose function>RNViewManagerAndroid(
     *     <... compose function parameters>
     *   )
     * }
     * ```
     */
    private fun generateAndroidViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val androidViewManagerClassName = rnViewManager.functionName.androidViewManagerClassName()
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.ACTUAL)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addModifiers(KModifier.ACTUAL)
                    .addParameters(constructorParameters)
                    .build()
            )
            addSuperinterface(ReactNativeViewManagerProviderClassName)

            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )

            addFunction(
                FunSpec.builder("getViewManager").run {
                    addModifiers(KModifier.OVERRIDE)
                    returns(ReactViewManagerClassName.parameterizedBy(STAR, STAR))
                    addStatement(
                        "return %T(%L)",
                        ClassName(rnViewManager.packageName, androidViewManagerClassName),
                        constructorParameters.map {
                            CodeBlock.of("%N", it.name)
                        }.joinToCode()
                    )
                }.build()
            )

            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    /**
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import de.voize.reaktnativetoolkit.util.ReactNativeViewManagerProvider
     * import react_native.RCTViewManager
     *
     * public actual class <class name of annotated compose function>RNViewManagerProvider actual constructor(
     *   <... compose function parameters>
     * ): ReactNativeViewManagerProvider {
     *   public override fun getViewWrapperFactory(): Pair<String, ReactNativeIOSViewWrapperFactory> =
     *      Pair(
     *          "<compose view name>",
     *          <class name of annotated compose function>RNViewWrapperFactoryIOS(
     *              <... compose function parameters>
     *          )
     *      )
     * }
     * ```
     */
    private fun generateIOSViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val iOSViewWrapperFactoryClassName = rnViewManager.functionName.iOSViewWrapperFactoryClassName()
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(className).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }
            addModifiers(KModifier.ACTUAL)
            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .addModifiers(KModifier.ACTUAL)
                    .build()
            )
            addProperties(
                constructorParameters.map {
                    PropertySpec.builder(it.name, it.type).addModifiers(KModifier.PRIVATE)
                        .initializer(it.name).build()
                }
            )
            addSuperinterface(ReactNativeViewManagerProviderClassName)

            addFunction(
                FunSpec.builder("getViewWrapperFactory").run {
                    addModifiers(KModifier.OVERRIDE)
                    returns(
                        ClassName("kotlin", "Pair").parameterizedBy(
                            STRING,
                            ReactNativeIOSViewWrapperFactoryClassName,
                        )
                    )
                    addStatement(
                        // we use `Pair` instead of `to` since kotlinpoet may introduce line breaks
                        // which break the syntax of the generated code
                        "return Pair(%S, %T(%L))",
                        rnViewManager.moduleName,
                        ClassName(rnViewManager.packageName, iOSViewWrapperFactoryClassName),
                        constructorParameters.map {
                            CodeBlock.of("%N", it.name)
                        }.joinToCode()
                    )
                }.build()
            )

            rnViewManager.wrappedFunctionDeclaration.containingFile?.let {
                addOriginatingKSFile(it)
            }
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    companion object {
        private fun KSFunctionDeclaration.toRNViewManager(
            reactNativeViewManagerAnnotationType: KSType,
            reactNativePropAnnotationType: KSType
        ): RNViewManager {
            val reactNativeViewManagerAnnotationArguments = annotations.single {
                it.annotationType.resolve() == reactNativeViewManagerAnnotationType
            }.arguments

            val moduleName = reactNativeViewManagerAnnotationArguments.single {
                it.name?.asString() == "name"
            }.value as String

            val isInternal = modifiers.contains(Modifier.INTERNAL)

            return RNViewManager(
                wrappedFunctionDeclaration = this,
                moduleName = moduleName,
                isInternal = isInternal,
                reactNativeProps = parameters.filter {
                    it.annotations.any { it.annotationType.resolve() == reactNativePropAnnotationType }
                }.map { parameter ->
                    val parameterType = parameter.type.resolve()
                    val name = (parameter.name ?: error("Prop name is required")).asString()

                    if (parameterType.isFunctionType) {
                        RNViewManager.ReactNativeProp.FunctionProp(
                            name,
                            parameterType.arguments
                                .dropLast(1) // remove Unit type argument
                                .map { it.type!!.resolve() }
                        )
                    } else {
                        check (parameterType.declaration.qualifiedName?.asString() != "kotlin.Long") {
                            "Long is not supported. Use Int instead."
                        }
                        RNViewManager.ReactNativeProp.ValueProp(
                            name,
                            parameterType,
                        )
                    }
                },
                restParameters = parameters.filterNot {
                    it.annotations.any { it.annotationType.resolve() == reactNativePropAnnotationType }
                }
            )
        }
    }

    private fun KSType.toNSTypeName() = when (this.declaration.qualifiedName?.asString()) {
        "kotlin.String" -> "NSString"
        "kotlin.Int" -> "NSNumber"
        // Using BOOL here instead of NSNumber has no effect.
        // Either way you need to call `boolValue` on the NSNumber
        // otherwise Kotlin will always interpret it as "true".
        "kotlin.Boolean"  -> "NSNumber"
        "kotlin.Long" -> "NSNumber"
        "kotlin.Float" -> "NSNumber"
        "kotlin.Double" -> "NSNumber"
        "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set" -> "NSString" // is serialized
        else -> when (val declaration = this.declaration) {
            is KSClassDeclaration -> {
                when (declaration.classKind) {
                    ClassKind.INTERFACE -> {
                        if (Modifier.SEALED in declaration.modifiers) {
                            "NSString"
                        } else {
                            error("Interfaces are not supported")
                        }
                    }
                    ClassKind.CLASS -> {
                        if (Modifier.DATA in declaration.modifiers) {
                            "NSString"
                        } else if (Modifier.SEALED in declaration.modifiers) {
                            "NSString"
                        } else {
                            error("Only data classes and sealed classes are supported, found: $declaration")
                        }
                    }
                    ClassKind.OBJECT -> "NSString"
                    ClassKind.ENUM_CLASS -> "NSString"
                    ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                    ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                }
            }
            else -> error("Unsupported declaration: $declaration")
        }
    }
}

private fun KSDeclaration.requiresSerialization(): Boolean {
    val types = listOf(
        "kotlin.collections.List",
        "kotlin.collections.Map",
        "kotlin.collections.Set",
    )

    return qualifiedName?.asString() in types
            || (this is KSClassDeclaration && when (this.classKind) {
        ClassKind.CLASS -> this.origin == Origin.KOTLIN
        ClassKind.INTERFACE -> true
        ClassKind.OBJECT -> true
        ClassKind.ENUM_CLASS -> true
        else -> false
    })
}

private val ArgumentsClassName = ClassName("com.facebook.react.bridge", "Arguments")
private val MutableStateFlowClassName = ClassName("kotlinx.coroutines.flow", "MutableStateFlow")
private val ReactPropClassName = ClassName("com.facebook.react.uimanager.annotations", "ReactProp")
private val AbstractComposeViewClassName = ClassName("androidx.compose.ui.platform", "AbstractComposeView")

private val ReactNativeViewManagerProviderClassName =
    ClassName(toolkitUtilPackageName, "ReactNativeViewManagerProvider")
private val ReactNativeIOSViewWrapperClassName = ClassName(toolkitUtilPackageName, "ReactNativeIOSViewWrapper")
private val ReactNativeIOSViewWrapperFactoryClassName = ClassName(toolkitUtilPackageName, "ReactNativeIOSViewWrapperFactory")
private val StateOrInitialClassName = ClassName(toolkitUtilPackageName, "StateOrInitial")
private val CollectAsStateClassName = ClassName("androidx.compose.runtime", "collectAsState")

private val ReactViewManagerClassName = ClassName("com.facebook.react.uimanager", "ViewManager")
private val RCTEventEmitterClassName = ClassName("com.facebook.react.uimanager.events", "RCTEventEmitter")
private val ReactSimpleViewManagerClassName =
    ClassName("com.facebook.react.uimanager", "SimpleViewManager")
private val ReactContextClassName =
    ClassName("com.facebook.react.bridge", "ReactContext")
private val ReactThemedReactContextClassName =
    ClassName("com.facebook.react.uimanager", "ThemedReactContext")
private val ComposeUIViewControllerClassName = ClassName("androidx.compose.ui.window", "ComposeUIViewController")
private val UIViewClassName = ClassName("platform.UIKit", "UIView")
private val NSNumberClassName = ClassName("platform.Foundation", "NSNumber")
private val ExperimentalComposeApiClassName = ClassName("androidx.compose.runtime", "ExperimentalComposeApi")
private val OptInClassName = ClassName("kotlin", "OptIn")