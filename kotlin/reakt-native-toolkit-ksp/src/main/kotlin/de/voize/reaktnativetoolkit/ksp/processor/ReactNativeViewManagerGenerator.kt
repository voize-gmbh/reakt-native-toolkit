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
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

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
            data class FlowProp(
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

    /**
     * Corresponds to de.voize.reaktnativetoolkit.util.ReactNativeIOSViewManager
     * When generating Obj-C code that references this interface we only plain pointers with a
     * comment hint reference this interface (id</*ReactNativeIOSViewManager*/>) so that
     * reakt-native-toolkit types do not have to be exposed into the shared framework of the host project.
     */
    private val iosViewManagerTypeName = "ReactNativeIOSViewManager"

    private fun String.androidViewManagerClassName() = this + "RNViewManagerAndroid"
    private fun String.iOSViewManagerClassName() = this + "RNViewManagerIOS"
    private fun String.iOSViewManagerObjcClassName()= this + "RNViewManagerObjCIos"
    private fun String.viewManagerProviderClassName() = this + "RNViewManagerProvider"
    private fun String.toRNViewManagerPropSetter() = "set${this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }}"

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
                generateIOSViewManager(rnViewManager)
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
        if (invalidRNViewManagerSymbols.isEmpty() && !objcGenerationInvoked && platforms.isCommon()) {
            val objcViewManagersCode = rnViewManagers.map {
                generateIOSViewManagerObjcCode(it)
            }.toList()
            generateObjcReactNativeViewManagersFiles(objcViewManagersCode)
            objcGenerationInvoked = true
        }

        if (invalidRNViewManagerSymbols.isEmpty() && !typescriptGenerationInvoked && platforms.isCommon()) {
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
                    is RNViewManager.ReactNativeProp.FlowProp -> listOf(it.typeArgument)
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
     * ) : SimpleViewManager<ComposeView>() {
     *     override fun getName() = "<view manager name specified in annotation>"
     *
     *     private val <prop name> = MutableSharedFlow<type of prop>()
     *
     *     private fun <lambda prop name>(context: ReactContext, id: Int) {
     *          context.getJSModule(RCTEventEmitter::class.java).receiveEvent(id, "<lambda prop name>", null)
     *     }
     *
     *     @ReactProp(name = "<prop name>")
     *     fun set<prop name>(view: ComposeView, value: <type of prop>) {
     *          <prop name>.tryEmit(value)
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
     *                        <prop value>,
     *                        <lambda prop name>(reactContext as ReactContext, id),
     *                        <... compose function parameters>,
     *                   )
     *              }
     *         }
     *     }
     * }
     * ```
     */
    private fun generateAndroidViewManager(rnViewManager: RNViewManager) {
        val wrappedFunctionName = rnViewManager.wrappedFunctionDeclaration.simpleName.asString()
        val viewManagerClassName = wrappedFunctionName.androidViewManagerClassName()
        val packageName = rnViewManager.wrappedFunctionDeclaration.packageName.asString()

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

            rnViewManager.reactNativeProps.forEach { prop ->
                when (prop) {
                    is RNViewManager.ReactNativeProp.FlowProp -> {
                        val setterName = prop.name.toRNViewManagerPropSetter()
                        val varName = "value"

                        addProperty(
                            PropertySpec.builder(
                                prop.name,
                                MutableSharedFlowClassName.parameterizedBy(prop.typeArgument.toTypeName())
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .initializer("%T(replay = 1)", MutableSharedFlowClassName)
                                .build()
                        )

                        addFunction(
                            FunSpec.builder(setterName)
                                .addModifiers(KModifier.PUBLIC)
                                .addAnnotation(
                                    AnnotationSpec.builder(ReactPropClassName)
                                        .addMember("name = %S", prop.name)
                                        .build()
                                )
                                .addParameter(
                                    ParameterSpec.builder("view", ComposeViewClassName).build()
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
                                    "%L.tryEmit(%L)",
                                    prop.name,
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
                            rnViewManager.reactNativeProps.forEach { prop ->
                                when (prop) {
                                    is RNViewManager.ReactNativeProp.FlowProp -> {
                                        add("%L = %L,\n", prop.name, prop.name)
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
     * generates a React Native View Manager for iOS that renders the annotated compose function.
     *
     * ```kotlin
     * package <package of annotated compose function>
     *
     * import androidx.compose.ui.window.ComposeUIViewController
     * import de.voize.reaktnativetoolkit.util.ReactNativeIOSViewManager
     * import kotlinx.coroutines.flow.MutableSharedFlow
     * import platform.UIKit.UIView
     *
     * class <class name of annotated compose function>RNViewManagerIOS(
     *   <... compose function parameters>
     * ) : ReactNativeIOSViewManager() {
     *     private val <flow prop name>: MutableSharedFlow<<type of flow prop>> = MutableSharedFlow(replay = 1)
     *
     *     public fun set<flow prop name>(value: <type of flow prop>) {
     *         <flow prop name>.tryEmit(value)
     *     }
     *
     *     private lateinit var <function prop name>: (args: Map<String, Any>) -> Unit
     *
     *     public fun set<function prop name>(value: (args: Map<String, Any>) -> Unit) {
     *          <function prop name> = value
     *     }
     *
     *     public fun view(): UIView = ComposeUIViewController {
     *         <class name of annotated compose function>(
     *              <prop name> = <prop name>,
     *              <function prop name> = { arg0, arg1 ->
     *                  <function prop name>(mapOf("args" to listOf(arg0, arg1)))
     *              },
     *              <... compose function parameters>,
     *          )
     *     }.view
     * }
     * ```
     */
    private fun generateIOSViewManager(rnViewManager: RNViewManager) {
        val viewManagerClassName = rnViewManager.functionName.iOSViewManagerClassName()
        val constructorParameters = rnViewManager.restParameters.map { it.toParameterSpec() }

        val classSpec = TypeSpec.classBuilder(viewManagerClassName).apply {
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

            addSuperinterface(ReactNativeIOSViewManagerClassName)

            rnViewManager.reactNativeProps.forEach { prop ->
                when (prop) {
                    is RNViewManager.ReactNativeProp.FlowProp -> {
                        val varName = "value"

                        addProperty(
                            PropertySpec.builder(
                                prop.name,
                                MutableSharedFlowClassName.parameterizedBy(prop.typeArgument.toTypeName())
                            )
                                .addModifiers(KModifier.PRIVATE)
                                .initializer("%T(replay = 1)", MutableSharedFlowClassName)
                                .build()
                        )

                        addFunction(
                            FunSpec.builder(prop.name.toRNViewManagerPropSetter())
                                .addParameter(
                                    varName,
                                    if (prop.typeArgument.declaration.requiresSerialization()) {
                                        STRING
                                    } else prop.typeArgument.toTypeName(),
                                )
                                .addStatement(
                                    "${prop.name}.tryEmit(%L)",
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
                        addProperty(
                            // (Map<String, Any?>) -> Unit
                            PropertySpec.builder(
                                prop.name,
                                LambdaTypeName.get(
                                    receiver = null,
                                    parameters = listOf(ParameterSpec.builder("args", Map::class.parameterizedBy(String::class, Any::class)).build()),
                                    returnType = UNIT
                                )
                            )
                                .mutable(true)
                                .addModifiers(KModifier.PRIVATE, KModifier.LATEINIT)
                                .build()
                        )

                        addFunction(
                            FunSpec.builder(prop.name.toRNViewManagerPropSetter())
                                .addParameter("value", LambdaTypeName.get(
                                    receiver = null,
                                    parameters = listOf(ParameterSpec.builder("args", Map::class.parameterizedBy(String::class, Any::class)).build()),
                                    returnType = UNIT
                                ))
                                .addStatement("%L = value", prop.name)
                                .build()
                        )
                    }
                }
            }

            addFunction(
                FunSpec.builder("view")
                    .addModifiers(KModifier.PUBLIC)
                    .returns(UIViewClassName)
                    .addStatement(
                        """
                            return %T {
                                %T(%L)
                            }.view
                            """.trimIndent(),
                        ComposeUIViewControllerClassName,
                        ClassName(rnViewManager.packageName, rnViewManager.functionName),
                        CodeBlock.builder().apply {
                            rnViewManager.reactNativeProps.forEach { prop ->
                                when (prop) {
                                    is RNViewManager.ReactNativeProp.FlowProp -> {
                                        add("%L = %L,\n", prop.name, prop.name)
                                    }
                                    is RNViewManager.ReactNativeProp.FunctionProp -> {
                                        add(
                                            "%L = { %L -> %L(mapOf(\"args\" to %L)) },\n",
                                            prop.name,
                                            prop.parameters
                                                .withIndex()
                                                .joinToString(", ") { "arg${it.index}" },
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
        }.build()

        val fileSpec = FileSpec.builder(rnViewManager.packageName, viewManagerClassName)
            .addType(classSpec)
            .build()

        fileSpec.writeTo(codeGenerator, false)
    }

    private data class RNViewManagerObjC(
        val viewManager: RNViewManager,
        val implementationCode: String,
    )

    private fun generateObjcReactNativeViewManagersFiles(rnViewManagers: List<RNViewManagerObjC>) {
        val objcReactNativeViewManagersFileName = "ReactNativeViewManagers"

        val headerCode = """
// Generated by reakt-native-toolkit. Do not modify.

#import <shared/shared.h>

@interface ReactNativeViewManagers : NSObject

+ (NSArray<id<RCTBridgeModule>>*)getRNViewManagers:(NSDictionary<NSString*, id/*<$iosViewManagerTypeName>*/>*)viewManagers;

@end
        """.trimIndent()

        val implementationCode = """
// Generated by reakt-native-toolkit. Do not modify.
            
#import <React/RCTViewManager.h>
#import <shared/shared.h>
#import "$objcReactNativeViewManagersFileName.h"

${rnViewManagers.joinToString("\n") { it.implementationCode }}

@implementation ReactNativeViewManagers

+ (NSArray<id<RCTBridgeModule>>*)getRNViewManagers:(NSDictionary<NSString*, id/*<$iosViewManagerTypeName>*/>*)viewManagers
{
    return @[
        ${rnViewManagers.joinToString(",\n") { 
            "[[${it.viewManager.functionName.iOSViewManagerObjcClassName()} alloc] initWithViewManager:viewManagers[@\"${it.viewManager.moduleName}\"]]" 
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

    private fun generateIOSViewManagerObjcCode(rnViewManager: RNViewManager): RNViewManagerObjC {
        val className = rnViewManager.functionName.iOSViewManagerObjcClassName()
        val specificIosViewManagerTypeName = "Shared${rnViewManager.functionName.iOSViewManagerClassName()}"
        val iosViewClassName = "${className}View"

        val implementationCode = """
@interface $iosViewClassName : UIView

${rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().joinToString("\n") { prop ->
"@property (nonatomic, copy) RCTBubblingEventBlock ${prop.name};"
}}

- (instancetype)initWithComposeView:(UIView *)composeView;

@end

@implementation $iosViewClassName : UIView

- (instancetype)initWithComposeView:(UIView *)composeView
{
    self = [super init];
    if (self) {
        [self addSubview:composeView];
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

@property (nonatomic, strong) $specificIosViewManagerTypeName *viewManager;

- (instancetype)initWithViewManager:(id/*<$iosViewManagerTypeName>*/)viewManager;

@end

@implementation $className


+ (NSString *)moduleName
{
    return @"${rnViewManager.moduleName}";
}

${rnViewManager.reactNativeProps.map { prop ->
    when (prop) {
        is RNViewManager.ReactNativeProp.FlowProp -> {
            val valueVarName = "json"
            val nsTypeName = prop.typeArgument.toNSTypeName()
            val conversion = when (prop.typeArgument.declaration.qualifiedName?.asString()) {
                "kotlin.Int" -> "[$valueVarName intValue]"
                "kotlin.Long" -> "[$valueVarName longLongValue]"
                "kotlin.Float" -> "[$valueVarName floatValue]"
                "kotlin.Double" -> "[$valueVarName doubleValue]"
                else -> valueVarName
            }
            
            """
RCT_CUSTOM_VIEW_PROPERTY(${prop.name}, $nsTypeName, UIView)
{
    [self.viewManager ${prop.name.toRNViewManagerPropSetter()}Value:$conversion];
}
        """.trimIndent()
        }

        is RNViewManager.ReactNativeProp.FunctionProp -> """
RCT_EXPORT_VIEW_PROPERTY(${prop.name}, RCTBubblingEventBlock)
        """.trimIndent()
    }
}.joinToString("\n")}

- (instancetype)initWithViewManager:(id/*<$iosViewManagerTypeName>*/)viewManager
{
    self = [super init];
    if (self) {
        self.viewManager = ($specificIosViewManagerTypeName*)viewManager;
    }
    return self;
}

- (UIView *)view
{
    $iosViewClassName *view = [[${iosViewClassName} alloc] initWithComposeView:[self.viewManager view]];
    
     ${rnViewManager.reactNativeProps.filterIsInstance<RNViewManager.ReactNativeProp.FunctionProp>().map { prop ->
"""
[self.viewManager ${prop.name.toRNViewManagerPropSetter()}Value:^(NSDictionary *args) {
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
     * public actual class <class name of annotated compose function>RNView ManagerProvider actual constructor(
     *   <... compose function parameters>
     * ): ReactNativeViewManagerProvider {
     *   public override fun getViewManager(): RCTViewManager = <class name of annotated compose function>RNViewManagerIOS(
     *      <... compose function parameters>
     *   )
     * }
     * ```
     */
    private fun generateIOSViewManagerProvider(rnViewManager: RNViewManager) {
        val className = rnViewManager.functionName.viewManagerProviderClassName()
        val iOSViewManagerClassName = rnViewManager.functionName.iOSViewManagerClassName()
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
                FunSpec.builder("getViewManager").run {
                    addModifiers(KModifier.OVERRIDE)
                    returns(
                        ClassName("kotlin", "Pair").parameterizedBy(
                            STRING,
                            ReactNativeIOSViewManagerClassName
                        )
                    )
                    addStatement(
                        // we use `Pair` instead of `to` since kotlinpoet may introduce line breaks
                        // which break the syntax of the generated code
                        "return Pair(%S, %T(%L))",
                        rnViewManager.moduleName,
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
                    val parameterDeclaration = parameterType.declaration
                    val name = (parameter.name ?: error("Prop name is required")).asString()

                    if (parameterType.declaration.qualifiedName?.asString() == "kotlinx.coroutines.flow.Flow") {
                        val typeArgument = parameterType.resolveTypeArgument(0)

                        check (typeArgument.declaration.qualifiedName?.asString() != "kotlin.Long") {
                            "Flow<Long> is not supported. Use Flow<Int> instead."
                        }

                        RNViewManager.ReactNativeProp.FlowProp(
                            name,
                            typeArgument,
                        )
                    } else if (parameterType.isFunctionType) {
                        RNViewManager.ReactNativeProp.FunctionProp(
                            name,
                            parameterType.arguments
                                .dropLast(1) // remove Unit type argument
                                .map { it.type!!.resolve() }
                        )
                    } else {
                        error("Unsupported prop type: $parameterType. Prop must either be a Flow<T> or a Function.")
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
        "kotlin.Boolean"  -> "NSNumber"
        "kotlin.Long" -> "NSNumber"
        "kotlin.Float" -> "NSNumber"
        "kotlin.Double" -> "NSNumber"
        "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set" -> "NSString" // is serialized
        else -> when (val declaration = this.declaration) {
            is KSClassDeclaration -> {
                when (declaration.classKind) {
                    ClassKind.INTERFACE -> error("Interfaces are not supported")
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
        ClassKind.OBJECT -> true
        ClassKind.ENUM_CLASS -> true
        else -> false
    })
}

private fun KSType.resolveTypeArgument(index: Int): KSType {
    val argument = arguments[index]
    val type = argument.type ?: error("Could not resolve type argument")
    return type.resolve()
}

private val ArgumentsClassName = ClassName("com.facebook.react.bridge", "Arguments")
private val MutableSharedFlowClassName = ClassName("kotlinx.coroutines.flow", "MutableSharedFlow")
private val KotlinFlowClassName = ClassName("kotlinx.coroutines.flow", "Flow")
private val ReactPropClassName = ClassName("com.facebook.react.uimanager.annotations", "ReactProp")

private val ReactNativeViewManagerProviderClassName =
    ClassName(toolkitUtilPackageName, "ReactNativeViewManagerProvider")
private val ReactNativeIOSViewManagerClassName = ClassName(toolkitUtilPackageName, "ReactNativeIOSViewManager")

private val ReactViewManagerClassName = ClassName("com.facebook.react.uimanager", "ViewManager")
private val RCTEventEmitterClassName = ClassName("com.facebook.react.uimanager.events", "RCTEventEmitter")
private val ReactSimpleViewManagerClassName =
    ClassName("com.facebook.react.uimanager", "SimpleViewManager")
private val ReactContextClassName =
    ClassName("com.facebook.react.bridge", "ReactContext")
private val ReactThemedReactContextClassName =
    ClassName("com.facebook.react.uimanager", "ThemedReactContext")
private val ComposeUIViewControllerClassName = ClassName("androidx.compose.ui.window", "ComposeUIViewController")
private val ComposeViewClassName = ClassName("androidx.compose.ui.platform", "ComposeView")
private val RCTViewManagerClassName = ClassName(reactNativeInteropNamespace, "RCTViewManager")
private val RCTViewManagerMetaClassName = ClassName(reactNativeInteropNamespace, "RCTViewManagerMeta")
private val UIViewClassName = ClassName("platform.UIKit", "UIView")
private val CGRectMakeClassName = ClassName("platform.CoreGraphics", "CGRectMake")
