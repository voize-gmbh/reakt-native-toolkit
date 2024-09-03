package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.writeTo

class ReactNativeViewManagerGenerator(private val codeGenerator: CodeGenerator) {
    data class RNViewManager(
        val wrappedFunctionDeclaration: KSFunctionDeclaration,
        val moduleName: String,
        val isInternal: Boolean,
        val parameters: List<KSValueParameter>,
    ) {
        val packageName
            get() = wrappedFunctionDeclaration.packageName.asString()
        val functionName
            get() = wrappedFunctionDeclaration.simpleName.asString()
    }

    private fun String.androidViewManagerClassName() = this + "RNViewManagerAndroid"
    private fun String.iOSViewManagerClassName() = this + "RNViewManagerIOS"
    private fun String.viewManagerProviderClassName() = this + "RNViewManagerProvider"

    /**
     * Given the metadata of a compose function annotated with `@ReactNativeViewManager`
     * generates a React Native View Manager for Android that renders the annotated compose function.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * class <class name of annotated compose function>RNViewManagerAndroid(
     *   <... compose function parameters>
     * ) : SimpleViewManager<ComposeView>() {
     *     override fun getName() = "<view manager name specified in annotation>"
     *
     *     override fun createViewInstance(reactContext: ThemedReactContext): ComposeView {
     *         return ComposeView(reactContext).apply {
     *              // workaround for "Cannot locate windowRecomposer" error
     *              // when compose view is rendered within a FlatList
     *              val recomposer = Recomposer(EmptyCoroutineContext)
     *              setParentCompositionContext(recomposer)
     *              doOnAttach { setParentCompositionContext(null) }
     *
     *              setContent {
     *                   <class name of annotated compose function>(
     *                      <... compose function parameters>
     *                   )
     *              }
     *         }
     *     }
     * }
     * ```
     */
    fun generateAndroidViewManager(rnViewManager: RNViewManager) {
        val wrappedFunctionName = rnViewManager.wrappedFunctionDeclaration.simpleName.asString()
        val viewManagerClassName = wrappedFunctionName.androidViewManagerClassName()
        val packageName = rnViewManager.wrappedFunctionDeclaration.packageName.asString()

        val classSpec = TypeSpec.classBuilder(viewManagerClassName).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

            val constructorParameters = rnViewManager.parameters.map { it.toParameterSpec() }

            primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameters(constructorParameters)
                    .build()
            )

            superclass(ReactSimpleViewManagerClassName.parameterizedBy(ComposeViewClassName))

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

            addFunction(
                FunSpec.builder("createViewInstance")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("reactContext", ReactThemedReactContextClassName)
                    .returns(ComposeViewClassName)
                    .addCode(
                        """
                        return %T(reactContext).apply {
                            // workaround for "Cannot locate windowRecomposer" error
                            // when compose view is rendered within a FlatList
                            val recomposer = %T(%T)
                            setParentCompositionContext(recomposer)
                            %T { setParentCompositionContext(null) }

                            setContent {
                                %T(%L)
                            }
                        }
                        """.trimIndent(),
                        ComposeViewClassName,
                        ClassName("androidx.compose.runtime", "Recomposer"),
                        ClassName("kotlin.coroutines", "EmptyCoroutineContext"),
                        ClassName("androidx.core.view", "doOnAttach"),
                        ClassName(rnViewManager.packageName, wrappedFunctionName),
                        CodeBlock.Builder().apply {
                            constructorParameters.forEach { parameter ->
                                add(
                                    "%L = %L,\n",
                                    parameter.name,
                                    parameter.name,
                                )
                            }
                        }.build()
                    )
                    .build()
            )
        }.build()

        val fileSpec = FileSpec.builder(packageName, viewManagerClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    /**
     * Given the metadata of a compose function annotated with `@ReactNativeViewManager`
     * generates a React Native View Manager for iOS that renders the annotated compose function.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import androidx.compose.ui.window.ComposeUIViewController
     * import platform.UIKit.UIView
     * import react_native.RCTViewManager
     * import react_native.RCTViewManagerMeta
     *
     * class <class name of annotated compose function>RNViewManagerIOS(
     *   <... compose function parameters>
     * ) : RCTViewManager() {
     *     public override fun view(): UIView {
     *         return ComposeUIViewController {
     *             <class name of annotated compose function>(
     *               <... compose function parameters>
     *             )
     *         }.view
     *     }
     *
     *     public companion object : RCTViewManagerMeta() {
     *         public override fun moduleName(): String = "<view manager name specified in annotation>"
     *     }
     * }
     * ```
     */
    fun generateIOSViewManager(rnViewManager: RNViewManager) {
        val viewManagerClassName = rnViewManager.functionName.iOSViewManagerClassName()
        val constructorParameters = rnViewManager.parameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(viewManagerClassName).apply {
            if (rnViewManager.isInternal) {
                addModifiers(KModifier.INTERNAL)
            }

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

            superclass(RCTViewManagerClassName)

            addFunction(
                FunSpec.builder("view")
                    .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                    .returns(UIViewClassName)
                    .addStatement(
                        "return %T { %T(%L) }.view",
                        ComposeUIViewControllerClassName,
                        ClassName(rnViewManager.packageName, rnViewManager.functionName),
                        constructorParameters.map {
                            CodeBlock.of("%N", it.name)
                        }.joinToCode()
                    )
                    .build()
            )

            addType(TypeSpec.companionObjectBuilder().apply {
                superclass(RCTViewManagerMetaClassName)
                addFunction(
                    FunSpec.builder("moduleName")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .returns(String::class)
                        .addStatement("return %S", rnViewManager.moduleName)
                        .build()
                )
            }.build())
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, viewManagerClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
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
    fun generateCommonViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val constructorParameters = rnViewManager.parameters.map { it.toParameterSpec() }

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
    fun generateAndroidViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val androidViewManagerClassName = rnViewManager.functionName.androidViewManagerClassName()
        val constructorParameters = rnViewManager.parameters.map { it.toParameterSpec() }

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
     *   public override fun getViewManager(): RCTViewManager = <class name of annotated compose function>RNViewManagerIOS(
     *      <... compose function parameters>
     *   )
     * }
     * ```
     */
    fun generateIOSViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val iOSViewManagerClassName = rnViewManager.functionName.iOSViewManagerClassName()
        val constructorParameters = rnViewManager.parameters.map { it.toParameterSpec() }

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
                FunSpec.builder("getViewManager").run {
                    addModifiers(KModifier.OVERRIDE)
                    returns(RCTViewManagerClassName)
                    addStatement(
                        "return %T(%L)",
                        ClassName(rnViewManager.packageName, iOSViewManagerClassName),
                        constructorParameters.map {
                            CodeBlock.of("%N", it.name)
                        }.joinToCode()
                    )
                }.build()
            )
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, className).addType(classSpec).build()

        fileSpec.writeTo(codeGenerator, false)
    }

    companion object {
        fun KSFunctionDeclaration.toRNViewManager(reactNativeViewManagerAnnotationType: KSType): RNViewManager {
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
                parameters = parameters,
            )
        }
    }
}

private val ReactNativeViewManagerProviderClassName =
    ClassName(toolkitUtilPackageName, "ReactNativeViewManagerProvider")
private val ReactViewManagerClassName = ClassName("com.facebook.react.uimanager", "ViewManager")
private val ReactSimpleViewManagerClassName =
    ClassName("com.facebook.react.uimanager", "SimpleViewManager")
private val ReactThemedReactContextClassName =
    ClassName("com.facebook.react.uimanager", "ThemedReactContext")
private val ComposeUIViewControllerClassName = ClassName("androidx.compose.ui.window", "ComposeUIViewController")
private val ComposeViewClassName = ClassName("androidx.compose.ui.platform", "ComposeView")
private val RCTViewManagerClassName = ClassName(reactNativeInteropNamespace, "RCTViewManager")
private val RCTViewManagerMetaClassName = ClassName(reactNativeInteropNamespace, "RCTViewManagerMeta")
private val UIViewClassName = ClassName("platform.UIKit", "UIView")