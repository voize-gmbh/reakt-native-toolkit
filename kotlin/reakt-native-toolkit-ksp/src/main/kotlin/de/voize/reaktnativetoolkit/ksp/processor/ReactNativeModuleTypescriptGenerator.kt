package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.NameAllocator
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import java.util.Locale
import java.util.UUID

internal const val modulesModule = "modules"

internal class ReactNativeModuleTypescriptGenerator(
    private val resolver: Resolver,
    private val codeGenerator: CodeGenerator,
    private val config: TypescriptConfig,
    private val logger: KSPLogger,
) {
    private fun getTypescriptTypeName(
        ksType: KSType,
        annotations: Sequence<KSAnnotation>? = null,
    ) = getTypescriptTypeName(ksType, annotations, config.externalTypeMapping, config.defaultInstantJSType, logger)


    internal fun generate(
        rnModules: List<ReactNativeModuleGenerator.RNModule>,
    ) {
        createRNModulesFile(
            rnModules,
            rnModules.mapNotNull { it.wrappedClassDeclaration.containingFile }
        )
    }

    private fun createRNModulesFile(
        rnModules: List<ReactNativeModuleGenerator.RNModule>,
        originatingKSFiles: List<KSFile>,
    ) {
        val rnModulesFileBuilder = FileSpec.builder("$generatedTsFilePath$modulesModule")
        rnModulesFileBuilder.addComment("This file is generated by ReaktNativeToolkit. Do not edit.")
        rnModules.forEach {
            createTypescriptRNModule(it, rnModulesFileBuilder)
        }

        val rnModulesFile = rnModulesFileBuilder.build()
        rnModulesFile.writeTo(codeGenerator, kspDependencies(true, originatingKSFiles))
    }

    private fun createTypescriptRNModule(
        rnModule: ReactNativeModuleGenerator.RNModule,
        fileBuilder: FileSpec.Builder
    ) {
        val withEventEmitter = rnModule.supportedEvents.isNotEmpty()
        val unsubscribeFromToolkitUseFlow = "unsubscribeFromToolkitUseFlow"
        val subscriptionIdVarName = "subscriptionId"

        val nativeInterfaceName = "Native" + rnModule.moduleName + "Interface"
        val nativeRNModuleInterface = InterfaceSpec.builder(nativeInterfaceName).apply {
            addFunctions(
                rnModule.reactNativeMethods.map { functionDeclaration ->
                    val parameters = functionDeclaration.parameters.map {
                        ParameterSpec.builder(
                            it.name?.asString() ?: error("Parameter must have a name"),
                            getTypescriptSerializedTypeName(it.type.resolve())
                        ).build()
                    }

                    FunctionSpec.builder(functionDeclaration.simpleName.asString())
                        .addModifiers(Modifier.ABSTRACT)
                        .addParameters(parameters)
                        .returns(TypeName.PROMISE.parameterized(functionDeclaration.returnType?.resolve()
                            ?.let { getTypescriptSerializedTypeName(it) } ?: TypeName.VOID))
                        .build()
                }
            )
            addProperties(
                rnModule.reactNativeFlows.map { functionDeclaration ->
                    reactNativeFlowToNextProperty(functionDeclaration, true)
                }
            )
            addFunction(
                FunctionSpec.builder(unsubscribeFromToolkitUseFlow)
                    .addModifiers(Modifier.ABSTRACT)
                    .addParameter(
                        ParameterSpec.builder(
                            subscriptionIdVarName,
                            TypeName.STRING,
                        ).build()
                    )
                    .returns(TypeName.PROMISE.parameterized(TypeName.VOID))
                    .build()
            )
        }.build()

        val interfaceName = rnModule.moduleName + "Interface"
        val rnModuleInterface = InterfaceSpec.builder(interfaceName).apply {
            addModifiers(Modifier.EXPORT)
            addTSDoc(
                "Module generated from {@link %N}.\n",
                rnModule.wrappedClassDeclaration.qualifiedName!!.asString()
            )
            addFunctions(
                rnModule.reactNativeMethods.map { functionDeclaration ->
                    val parameters = functionDeclaration.parameters.map {
                        ParameterSpec.builder(
                            it.name?.asString() ?: error("Parameter must have a name"),
                            getTypescriptTypeName(it.type.resolve())
                        )
                            .build()
                    }

                    FunctionSpec.builder(functionDeclaration.simpleName.asString())
                        .addModifiers(Modifier.ABSTRACT)
                        .addParameters(parameters)
                        .returns(TypeName.PROMISE.parameterized(functionDeclaration.returnType?.resolve()
                            ?.let { getTypescriptTypeName(it) } ?: TypeName.VOID))
                        .build()
                }
            )
            if (withEventEmitter) {
                addFunction(
                    FunctionSpec.builder("addEventListener")
                        .addModifiers(Modifier.ABSTRACT)
                        .addParameter(
                            ParameterSpec.builder(
                                "key",
                                TypeName.STRING,
                            ).build()
                        )
                        .addParameter(
                            ParameterSpec.builder(
                                "listener",
                                TypeName.lambda(
                                    "result" to TypeName.ANY,
                                    returnType = TypeName.VOID,
                                ),
                            ).build()
                        )
                        .returns(TypeName.namedImport("EmitterSubscription", "react-native"))
                        .build()
                )
            }
            addProperties(
                rnModule.reactNativeFlows.map { functionDeclaration ->
                    reactNativeFlowToNextProperty(functionDeclaration, false)
                }
            )
            addFunctions(
                rnModule.reactNativeFlows.map { functionDeclaration ->
                    val parameters = functionDeclaration.parameters.map {
                        ParameterSpec.builder(
                            it.name?.asString() ?: error("Parameter must have a name"),
                            getTypescriptTypeName(it.type.resolve())
                        )
                            .build()
                    }

                    val (returnType, annotations) = functionDeclaration.getReturnTypeOfFlow()

                    FunctionSpec.builder(functionDeclaration.simpleName.asString().toHookName())
                        .addModifiers(Modifier.ABSTRACT)
                        .addParameters(parameters)
                        .returns(getTypescriptTypeName(returnType, annotations).asNullable())
                        .build()
                }
            )
        }.build()

        fileBuilder.addType(nativeRNModuleInterface)
        fileBuilder.addType(rnModuleInterface)
        val nativeRNModule = "Native" + rnModule.moduleName
        val nativeRNModuleSymbol = getSymbolInModule(nativeRNModule)
        val nameAllocator = NameAllocator()
        nameAllocator.newName(nativeRNModule)
        fileBuilder.addCode(
            const(
                name = nativeRNModule,
                expression = NativeModulesSymbol.nested(rnModule.moduleName).asCodeBlock()
                    .castTo(TypeName.implicit(nativeInterfaceName))
            )
        )
        val exportedRNModuleName = rnModule.moduleName
        val exportedRNModuleSymbol = getSymbolInModule(exportedRNModuleName)
        nameAllocator.newName(exportedRNModuleName)
        fileBuilder.addCode(
            const(
                name = exportedRNModuleName,
                typeName = TypeName.implicit(interfaceName),
                expression = CodeBlock.of(
                    "{\n%>...%Q,\n%L%<\n}",
                    nativeRNModuleSymbol,
                    buildList {
                        fun KSFunctionDeclaration.toParametersAndPassedValues(nameAllocator: NameAllocator): Pair<List<CodeBlock>, List<CodeBlock>> {
                            val parameterTags = parameters.map {
                                UUID.randomUUID().also { tag ->
                                    nameAllocator.newName(
                                        it.name?.asString() ?: error("Parameter must have a name"),
                                        tag
                                    )
                                }
                            }
                            return Pair(
                                parameters.zip(parameterTags).map { (parameter, tag) ->
                                    parameter(
                                        nameAllocator[tag],
                                        getTypescriptTypeName(parameter.type.resolve()),
                                    )
                                },
                                parameters.zip(parameterTags).map { (parameter, tag) ->
                                    val name = nameAllocator[tag]
                                    val type = parameter.type.resolve()
                                    val value = convertTypeToJson(
                                        name.asCodeBlock(),
                                        type,
                                        nameAllocator.copy(),
                                        config.externalTypeMapping,
                                        config.defaultInstantJSType,
                                        logger,
                                    )
                                    if (type.needsJSSerialization()) {
                                        jsonStringifyName.asCodeBlock().invoke(listOf(value))
                                    } else {
                                        value
                                    }
                                }
                            )
                        }

                        // functions
                        addAll((rnModule.reactNativeMethods).map { functionDeclaration ->
                            val functionNameAllocator = nameAllocator.copy()
                            val (parameters, parameterSerialization) = functionDeclaration.toParametersAndPassedValues(
                                functionNameAllocator
                            )

                            val returnType =
                                functionDeclaration.returnType?.resolve()
                                    ?: resolver.builtIns.unitType

                            val returnValueDeserialization =
                                if (returnType.needsJSSerialization()) {
                                    CodeBlock.of(
                                        ".%N(%Q)",
                                        "then",
                                        jsonParseName,
                                    )
                                } else {
                                    CodeBlock.empty()
                                }

                            val returnNameAllocator = functionNameAllocator.copy()
                            val resultTag = UUID.randomUUID()
                            returnNameAllocator.newName("result", resultTag)
                            val returnValueMapping =
                                CodeBlock.of(
                                    ".%N((%N) =>%W%L)",
                                    "then",
                                    returnNameAllocator[resultTag],
                                    convertJsonToType(
                                        CodeBlock.of(returnNameAllocator[resultTag]),
                                        returnType,
                                        returnNameAllocator.copy(),
                                        config.externalTypeMapping,
                                        config.defaultInstantJSType,
                                        logger,
                                    )
                                )

                            // wrap native function into arrow to prevent passing to too many arguments from resulting in an error
                            property(
                                functionDeclaration.simpleName.asString(),
                                lambda(
                                    args = parameters,
                                    body = CodeBlock.of(
                                        "%Q(%L)%L%L",
                                        nativeRNModuleSymbol.nested(functionDeclaration.simpleName.asString()),
                                        parameterSerialization.joinToCode(),
                                        returnValueDeserialization,
                                        returnValueMapping,
                                    )
                                ),
                            )
                        })
                        // flows
                        addAll((rnModule.reactNativeFlows).map { functionDeclaration ->
                            val functionNameAllocator = nameAllocator.copy()
                            val subscriptionIdTag = UUID.randomUUID()
                            val currentValueTag = UUID.randomUUID()
                            functionNameAllocator.newName("subscriptionId", subscriptionIdTag)
                            functionNameAllocator.newName("currentValue", currentValueTag)

                            val subscriptionIdParameter = parameter(
                                functionNameAllocator[subscriptionIdTag],
                                TypeName.STRING,
                            )

                            val currentValueParameter = parameter(
                                functionNameAllocator[currentValueTag],
                                TypeName.STRING.asNullable(),
                            )

                            val subscriptionIdPassedParameter =
                                functionNameAllocator[subscriptionIdTag].asCodeBlock()

                            val currentValuePassedParameter =
                                functionNameAllocator[currentValueTag].asCodeBlock()


                            val (parameters, parameterSerialization) = functionDeclaration.toParametersAndPassedValues(
                                functionNameAllocator
                            )

                            property(
                                functionDeclaration.simpleName.asString(),
                                lambda(
                                    args = (listOf(
                                        subscriptionIdParameter,
                                        currentValueParameter
                                    ) + parameters),
                                    body = nativeRNModuleSymbol.nested(functionDeclaration.simpleName.asString())
                                        .asCodeBlock().invoke(
                                            listOf(
                                                subscriptionIdPassedParameter,
                                                currentValuePassedParameter
                                            ) + parameterSerialization
                                        )
                                ),
                            )
                        })
                        // useFlow wrappers with mapping
                        addAll((rnModule.reactNativeFlows).map { functionDeclaration ->
                            val functionNameAllocator = nameAllocator.copy()
                            val valueTag = UUID.randomUUID()
                            functionNameAllocator.newName("value", valueTag)

                            val parameterTags = functionDeclaration.parameters.map {
                                UUID.randomUUID().also { tag ->
                                    nameAllocator.newName(
                                        it.name?.asString() ?: error("Parameter must have a name"),
                                        tag
                                    )
                                }
                            }

                            val wrapperParameter = functionDeclaration.parameters.zip(parameterTags)
                                .map { (parameter, tag) ->
                                    parameter(
                                        nameAllocator[tag],
                                        getTypescriptTypeName(parameter.type.resolve()),
                                    )
                                }
                            val args = parameterTags.map { tag -> nameAllocator[tag].asCodeBlock() }
                            val (returnType, typeAnnotations) = functionDeclaration.getReturnTypeOfFlow()
                            property(
                                functionDeclaration.simpleName.asString().toHookName(),
                                lambda(
                                    args = wrapperParameter,
                                    body = codeBlock(brackets = true) {
                                        add(
                                            const(
                                                functionNameAllocator[valueTag],
                                                useFlowName.asCodeBlock().invoke(
                                                    buildList {
                                                        add(
                                                            exportedRNModuleSymbol.nested(
                                                                functionDeclaration.simpleName.asString(),
                                                            ).asCodeBlock()
                                                        )
                                                        add(
                                                            nativeRNModuleSymbol.nested(
                                                                unsubscribeFromToolkitUseFlow,
                                                            ).asCodeBlock()
                                                        )
                                                        add(
                                                            CodeBlock.of(
                                                                "%S",
                                                                "${exportedRNModuleName}.${functionDeclaration.simpleName.asString()}",
                                                            )
                                                        )
                                                        addAll(args)
                                                    }
                                                )
                                            )
                                        )
                                        add(
                                            returnStatement(
                                                ReactUseMemoSymbol.asCodeBlock().invoke(
                                                    listOf(
                                                        lambda(
                                                            args = emptyList(),
                                                            body = functionNameAllocator[valueTag].asCodeBlock()
                                                                .ifNotNull(
                                                                    isNullable = true,
                                                                    functionNameAllocator
                                                                ) { value, nameAllocator ->
                                                                    convertJsonToType(
                                                                        value.asCodeBlock(),
                                                                        returnType,
                                                                        nameAllocator.copy(),
                                                                        config.externalTypeMapping,
                                                                        config.defaultInstantJSType,
                                                                        logger,
                                                                        typeAnnotations,
                                                                    )
                                                                }
                                                        ),
                                                        CodeBlock.of(
                                                            "[%N]",
                                                            functionNameAllocator[valueTag]
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    }
                                ),
                            )
                        })

                        if (withEventEmitter) {
                            val eventEmitterVarName = "eventEmitter"
                            val keyVarName = "key"
                            val listenerVarName = "listener"

                            val parameters = listOf(
                                parameter(
                                    keyVarName,
                                    TypeName.STRING,
                                ),
                                parameter(
                                    listenerVarName,
                                    TypeName.lambda(
                                        "result" to TypeName.ANY,
                                        returnType = TypeName.VOID,
                                    ),
                                ),
                            )
                            add(
                                property(
                                    "addEventListener",
                                    lambda(
                                        args = parameters,
                                        body = codeBlock(brackets = true) {
                                            add(
                                                const(
                                                    eventEmitterVarName,
                                                    CodeBlock.of(
                                                        "new %T(%L)",
                                                        NativeEventEmitterTypeName,
                                                        nativeRNModule.asCodeBlock()
                                                            .castTo(TypeName.ANY)
                                                    )
                                                )
                                            )
                                            add(
                                                returnStatement(
                                                    CodeBlock.of(
                                                        "%N.addListener(%N, %N)",
                                                        eventEmitterVarName,
                                                        keyVarName,
                                                        listenerVarName,
                                                    )
                                                )
                                            )
                                        }
                                    )
                                )
                            )
                        }
                    }.joinToCode(",\n")
                )
            ).export()
        )
    }

    private fun reactNativeFlowToNextProperty(
        functionDeclaration: KSFunctionDeclaration,
        useSerializedParameterTypes: Boolean,
    ): PropertySpec {
        val (returnType, typeAnnotations) = functionDeclaration.getReturnTypeOfFlow()
        val returnTypeName = getTypescriptTypeName(returnType, typeAnnotations)
        val parameters = functionDeclaration.parameters.map {
            if (useSerializedParameterTypes) {
                getTypescriptSerializedTypeName(it.type.resolve())
            } else {
                getTypescriptTypeName(it.type.resolve())
            }
        }

        val nextTypeName = when (parameters.size) {
            0 -> NextTypeName.parameterized(returnTypeName)
            1 -> Next1TypeName.parameterized(returnTypeName, parameters[0])
            2 -> Next2TypeName.parameterized(returnTypeName, parameters[0], parameters[1])
            else -> NextXTypeName.parameterized(returnTypeName)
        }

        return PropertySpec.builder(functionDeclaration.simpleName.asString(), nextTypeName)
            .build()
    }

    private fun String.toHookName() = "use" + this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.ROOT)
        else it.toString()
    }

    private fun KSFunctionDeclaration.getReturnTypeOfFlow(): Pair<KSType, Sequence<KSAnnotation>> {
        val flowTypeArgument = returnType!!.resolve().arguments.single()
        return Pair(
            (flowTypeArgument.type ?: error("Flow Type can not use star projection")).resolve(),
            flowTypeArgument.annotations,
        )
    }

    /**
     * Used to get the symbol for a type in the "modules" module.
     */
    private fun getSymbolInModule(name: String) = SymbolSpec.importsName(name, "!$generatedTsFilePath$modulesModule")

    private val NextTypeName = TypeName.namedImport("Next", "reakt-native-toolkit")
    private val Next1TypeName = TypeName.namedImport("Next1", "reakt-native-toolkit")
    private val Next2TypeName = TypeName.namedImport("Next2", "reakt-native-toolkit")
    private val NextXTypeName = TypeName.namedImport("NextX", "reakt-native-toolkit")
    private val useFlowName = SymbolSpec.importsName("useFlow", "reakt-native-toolkit")

    private val ReactUseMemoSymbol = SymbolSpec.importsName("useMemo", "react")
    private val NativeModulesSymbol = SymbolSpec.importsName("NativeModules", "react-native")
    private val NativeEventEmitterTypeName =
        TypeName.namedImport("NativeEventEmitter", "react-native")
}