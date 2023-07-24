package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.EnumSpec
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.FunctionSpec
import io.outfoxx.typescriptpoet.InterfaceSpec
import io.outfoxx.typescriptpoet.Modifier
import io.outfoxx.typescriptpoet.ParameterSpec
import io.outfoxx.typescriptpoet.PropertySpec
import io.outfoxx.typescriptpoet.TypeAliasSpec
import io.outfoxx.typescriptpoet.TypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

private const val modelsModule = "models"

fun Resolver.generateTypescript(
    functionsByClass: Map<KSClassDeclaration, List<KSFunctionDeclaration>>,
    rnModules: List<ToolkitSymbolProcessor.RNModule>,
    codeGenerator: CodeGenerator,
    logger: KSPLogger,
) {

    // Collect all types of function parameters and return types
    val typeDeclarations = functionsByClass.values.flatten().flatMap {
        it.parameters.map { it.type } + (it.returnType ?: error("Type resolution error"))
    }.toSet().map { it.resolve() }

    val usedTypes = findAllUsedTypes(typeDeclarations)

    val customTypes = usedTypes.filter {
        val defaultTypes = setOf(
            "kotlin.Any",
            "kotlin.Boolean",
            "kotlin.Byte",
            "kotlin.Char",
            "kotlin.Double",
            "kotlin.Float",
            "kotlin.Int",
            "kotlin.Long",
            "kotlin.Short",
            "kotlin.String",
            "kotlin.Unit",
            "kotlin.collections.List",
            "kotlin.collections.Map",
            "kotlin.collections.Set",
        )
        it.qualifiedName?.asString() !in defaultTypes
    }

    val typescriptModelsFileBuilder = FileSpec.builder(modelsModule)
    customTypes.map {
        createTypescriptTypeDeclaration(it, typescriptModelsFileBuilder)
    }
    val typescriptModelsFile = typescriptModelsFileBuilder.build()

    val originatingKSFiles = rnModules.mapNotNull { it.wrappedClassDeclaration.containingFile }
    typescriptModelsFile.writeTo(codeGenerator, kspDependencies(true, originatingKSFiles))

    val rnModulesFileBuilder = FileSpec.builder("modules")
    rnModules.forEach {
        createTypescriptRNModule(it, rnModulesFileBuilder)
    }
    val rnModulesFile = rnModulesFileBuilder.build()

    rnModulesFile.writeTo(codeGenerator, kspDependencies(true, originatingKSFiles))
}

fun Resolver.createTypescriptRNModule(
    rnModule: ToolkitSymbolProcessor.RNModule,
    fileBuilder: FileSpec.Builder
) {
    val nativeModulesType = TypeName.namedImport("NativeModules", "react-native")

    val nativeInterfaceName = "Native" + rnModule.moduleName + "Interface"
    val nativeRNModuleInterface = InterfaceSpec.builder(nativeInterfaceName).apply {
        addFunctions(
            rnModule.reactNativeMethods.map { functionDeclaration ->
                val parameters = functionDeclaration.parameters.map {
                    ParameterSpec.builder(
                        it.name?.asString() ?: error("Parameter must have a name"),
                        getTypescriptSerializedTypeName(it.type.resolve())
                    )
                        .build()
                }

                FunctionSpec.builder(functionDeclaration.simpleName.asString())
                    .addModifiers(Modifier.ABSTRACT)
                    .addParameters(parameters)
                    .returns(TypeName.PROMISE.parameterized(functionDeclaration.returnType?.resolve()
                        ?.let { getTypescriptSerializedTypeName(it) } ?: TypeName.VOID))
                    .build()
            }
        )
    }.build()

    val interfaceName = rnModule.moduleName + "Interface"
    val rnModuleInterface = InterfaceSpec.builder(interfaceName).apply {
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
    }.build()

    fileBuilder.addType(nativeRNModuleInterface)
    fileBuilder.addType(rnModuleInterface)
    val nativeRNModule = "Native" + rnModule.moduleName
    fileBuilder.addCode(
        CodeBlock.of(
            "const %N = %T.%N as %T",
            nativeRNModule,
            nativeModulesType,
            rnModule.moduleName,
            TypeName.implicit(nativeInterfaceName)
        )
    )
    fileBuilder.addCode(
        CodeBlock.of(
            """
            export const %N: %T = {
                %L
            }
            """.trimIndent(),
            rnModule.moduleName,
            TypeName.implicit(interfaceName),
            rnModule.reactNativeMethods.map { functionDeclaration ->
                if (needsSerialization(functionDeclaration.parameters.map { it.type.resolve() })) {
                    val parameters = functionDeclaration.parameters.map {
                        CodeBlock.of(
                            "%N: %T",
                            it.name?.asString() ?: error("Parameter must have a name"),
                            getTypescriptTypeName(it.type.resolve()),
                        )
                    }.joinToCode()

                    val parameterSerialization = functionDeclaration.parameters.map {
                        if (needsSerialization(it.type.resolve())) {
                            CodeBlock.of(
                                "%T.%N(%N)",
                                TypescriptJsonTypeName,
                                "stringify",
                                it.name?.asString() ?: error("Parameter must have a name")
                            )
                        } else {
                            CodeBlock.of(
                                "%N",
                                it.name?.asString() ?: error("Parameter must have a name")
                            )
                        }
                    }.joinToCode()

                    val returnValueDeserialization =
                        if (functionDeclaration.returnType.let { it != null && needsSerialization(it.resolve()) }) {
                            CodeBlock.of(
                                ".%N(%T.%N)",
                                "then",
                                TypescriptJsonTypeName,
                                "parse",
                            )
                        } else {
                            CodeBlock.empty()
                        }

                    CodeBlock.of(
                        "%N: (%L) => %N.%N(%L)%L",
                        functionDeclaration.simpleName.asString(),
                        parameters,
                        nativeRNModule,
                        functionDeclaration.simpleName.asString(),
                        parameterSerialization,
                        returnValueDeserialization,
                    )
                } else {
                    CodeBlock.of(
                        "%N: %N.%N",
                        functionDeclaration.simpleName.asString(),
                        nativeRNModule,
                        functionDeclaration.simpleName.asString()
                    )
                }

            }.joinToCode(",\n    ")
        )
    )
}

/**
 * Construct the typescript name for a given declaration.
 * Nested declarations are concatenated with the parent declaration name.
 *
 * TODO this may cause name collisions
 */
private fun KSDeclaration.getTypescriptName(): String {
    return if (parentDeclaration != null) {
        "${parentDeclaration!!.getTypescriptName()}${simpleName.asString()}"
    } else {
        simpleName.asString()
    }
}

private fun Resolver.getTypescriptTypeName(ksType: KSType): TypeName {
    fun resolveTypeArgument(index: Int): TypeName {
        val argument = ksType.arguments[index]
        val type = argument.type
        if (type != null) {
            return getTypescriptTypeName(type.resolve())
        } else {
            error("Could not resolve type argument")
        }
    }
    return when (ksType.declaration.qualifiedName) {
        this.getKSNameFromString("kotlin.Any") -> TypeName.ANY
        this.getKSNameFromString("kotlin.Boolean") -> TypeName.BOOLEAN
        this.getKSNameFromString("kotlin.Byte") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.String") -> TypeName.STRING
        this.getKSNameFromString("kotlin.Int") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Long") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Float") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Double") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Unit") -> TypeName.VOID
        this.getKSNameFromString("kotlin.Array") -> TypeName.arrayType(resolveTypeArgument(0))
        this.getKSNameFromString("kotlin.collections.List") -> TypeName.arrayType(
            resolveTypeArgument(0)
        )

        this.getKSNameFromString("kotlin.collections.Set") -> TypeName.arrayType(
            resolveTypeArgument(
                0
            )
        )

        this.getKSNameFromString("kotlin.collections.Map") -> recordType(
            resolveTypeArgument(0),
            resolveTypeArgument(1),
        )

        else -> when (val declaration = ksType.declaration) {
            is KSClassDeclaration -> {
                val module = "!$modelsModule"
                when (declaration.classKind) {
                    ClassKind.INTERFACE -> error("Interfaces are not supported")
                    ClassKind.CLASS -> {
                        if (com.google.devtools.ksp.symbol.Modifier.DATA in declaration.modifiers) {
                            // data class
                            TypeName.namedImport(declaration.getTypescriptName(), module)
                        } else if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                            TypeName.namedImport(declaration.getTypescriptName(), module)
                        } else {
                            error("Only data classes and sealed classes are supported")
                        }
                    }

                    ClassKind.ENUM_CLASS -> {
                        TypeName.namedImport(declaration.getTypescriptName(), module)
                    }

                    ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                    ClassKind.OBJECT -> error("Object declarations are not supported")
                    ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                }
            }

            is KSFunctionDeclaration -> {
                error("Function declarations are not supported")
            }

            is KSTypeAlias -> {
                getTypescriptTypeName(declaration.type.resolve())
            }

            is KSPropertyDeclaration -> {
                error("Property declarations are not supported")
            }

            is KSTypeParameter -> {
                // TODO handle bounds
                TypeName.typeVariable(declaration.name.asString())
            }

            else -> {
                error("Unsupported declaration: $declaration")
            }
        }
    }.let {
        if (ksType.isMarkedNullable) {
            TypeName.unionType(it, TypeName.NULL)
        } else {
            it
        }
    }
}


private fun Resolver.getTypescriptSerializedTypeName(ksType: KSType): TypeName {
    fun resolveTypeArgument(index: Int): TypeName {
        val argument = ksType.arguments[index]
        val type = argument.type
        if (type != null) {
            return getTypescriptSerializedTypeName(type.resolve())
        } else {
            error("Could not resolve type argument")
        }
    }

    return when (ksType.declaration.qualifiedName) {
        this.getKSNameFromString("kotlin.Any") -> TypeName.ANY
        this.getKSNameFromString("kotlin.Boolean") -> TypeName.BOOLEAN
        this.getKSNameFromString("kotlin.String") -> TypeName.STRING
        this.getKSNameFromString("kotlin.Int") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Long") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Float") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Double") -> TypeName.NUMBER
        this.getKSNameFromString("kotlin.Unit") -> TypeName.VOID
        this.getKSNameFromString("kotlin.Array") -> TypeName.arrayType(resolveTypeArgument(0))
        this.getKSNameFromString("kotlin.collections.List") -> TypeName.arrayType(
            resolveTypeArgument(0)
        )

        this.getKSNameFromString("kotlin.collections.Set") -> TypeName.arrayType(
            resolveTypeArgument(
                0
            )
        )

        this.getKSNameFromString("kotlin.collections.Map") -> recordType(
            resolveTypeArgument(0),
            resolveTypeArgument(1),
        )

        else -> when (val declaration = ksType.declaration) {
            is KSClassDeclaration -> {
                when (declaration.classKind) {
                    ClassKind.INTERFACE -> error("Interfaces are not supported")
                    ClassKind.CLASS -> {
                        if (com.google.devtools.ksp.symbol.Modifier.DATA in declaration.modifiers) {
                            // data class
                            TypeName.STRING
                        } else if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                            // sealed class
                            TypeName.STRING
                        } else {
                            error("Only data classes and sealed classes are supported")
                        }
                    }

                    ClassKind.ENUM_CLASS -> TypeName.STRING
                    ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                    ClassKind.OBJECT -> error("Object declarations are not supported")
                    ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                }
            }

            is KSFunctionDeclaration -> {
                error("Function declarations are not supported")
            }

            is KSTypeAlias -> {
                getTypescriptSerializedTypeName(declaration.type.resolve())
            }

            is KSPropertyDeclaration -> {
                error("Property declarations are not supported")
            }

            is KSTypeParameter -> {
                // TODO handle bounds
                TypeName.typeVariable(declaration.name.asString())
            }

            else -> {
                error("Unsupported declaration: $declaration")
            }
        }
    }
}

private val Resolver.serialNameAnnotationType
    get() =
        this.getClassDeclarationByName("kotlinx.serialization.SerialName")
            ?.asType(emptyList())
            ?: error("Could not find SerialName annotation")

private fun Resolver.createTypescriptTypeDeclaration(
    declaration: KSDeclaration,
    typescriptFileBuilder: FileSpec.Builder,
    sealedBaseType: TypeName? = null,
    sealedSubclassDiscriminator: String? = null
) {
    val serializableAnnotationType =
        this.getClassDeclarationByName("kotlinx.serialization.Serializable")
            ?.asType(emptyList())
            ?: error("Could not find Serializable annotation")
    when (declaration.qualifiedName) {
        else -> when (declaration) {
            is KSClassDeclaration -> {
                when (declaration.classKind) {
                    ClassKind.INTERFACE -> error("Interfaces are not supported")
                    ClassKind.CLASS -> {
                        if (com.google.devtools.ksp.symbol.Modifier.DATA in declaration.modifiers) {
                            // data class
                            if (declaration.annotations.none { it.annotationType.resolve() == serializableAnnotationType }) {
                                error("Data classes must be annotated with @Serializable: $declaration")
                            }
                            val interfaceSpec =
                                InterfaceSpec.builder(declaration.getTypescriptName()).apply {
                                    addModifiers(Modifier.EXPORT)
                                    if (sealedBaseType != null) {
                                        addSuperInterface(sealedBaseType)
                                    }
                                    addProperties(
                                        declaration.getAllProperties()
                                            .map { toTypescriptPropertySpec(it) }.toList()
                                    )
                                }.build()
                            typescriptFileBuilder.addInterface(interfaceSpec)
                        } else if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                            if (sealedSubclassDiscriminator != null) {
                                error("Sealed classes as direct members of other sealed classes are not supported: $declaration")
                            }
                            if (declaration.annotations.none { it.annotationType.resolve() == serializableAnnotationType }) {
                                error("Sealed classes must be annotated with @Serializable: $declaration")
                            }
                            val subclasses = declaration.getSealedSubclasses()
                            val subclassesToDiscriminator =
                                subclasses.associateWith { subclassDeclaration ->
                                    if (subclassDeclaration.annotations.none { it.annotationType.resolve() == serialNameAnnotationType }) {
                                        error("Sealed subclasses must be annotated with @SerialName: $subclassDeclaration")
                                    }
                                    subclassDeclaration.annotations.single { it.annotationType.resolve() == serialNameAnnotationType }
                                        .arguments.single().value as String
                                }
                            val typeEnumName = declaration.getTypescriptName() + "Type"
                            val typeEnumTypeName = TypeName.implicit(typeEnumName)
                            EnumSpec.builder(typeEnumName).apply {
                                addModifiers(Modifier.EXPORT)
                                subclassesToDiscriminator.forEach { (subclassDeclaration, discriminator) ->
                                    addConstant(
                                        subclassDeclaration.getTypescriptName(),
                                        CodeBlock.of("%S", discriminator)
                                    )
                                }
                            }.build().let {
                                typescriptFileBuilder.addEnum(it)
                            }
                            val baseTypeName = declaration.getTypescriptName() + "Base"
                            val baseTypeTypeName = TypeName.implicit(baseTypeName)
                            InterfaceSpec.builder(baseTypeName).apply {
                                val typeVariable =
                                    TypeName.typeVariable(
                                        "T",
                                        TypeName.bound(typeEnumTypeName)
                                    )
                                addTypeVariable(typeVariable)
                                addProperty(
                                    PropertySpec.builder(
                                        "type",
                                        typeVariable,
                                    ).build()
                                )
                            }.build().let {
                                typescriptFileBuilder.addInterface(it)
                            }

                            subclasses.forEach { subclassDeclaration ->
                                createTypescriptTypeDeclaration(
                                    subclassDeclaration,
                                    typescriptFileBuilder,
                                    sealedBaseType = baseTypeTypeName.parameterized(
                                        typeEnumTypeName.nested(subclassDeclaration.getTypescriptName())
                                    ),
                                    sealedSubclassDiscriminator = subclassesToDiscriminator.getValue(
                                        subclassDeclaration
                                    ),
                                )
                            }
                            val sealedTypeUnion = TypeAliasSpec.builder(
                                declaration.getTypescriptName(),
                                TypeName.unionType(*subclasses.map { TypeName.implicit(it.getTypescriptName()) }
                                    .toList().toTypedArray())
                            ).addModifiers(Modifier.EXPORT).build()
                            typescriptFileBuilder.addTypeAlias(sealedTypeUnion)
                        } else {
                            error("Only data classes are supported, found: $declaration")
                        }
                    }

                    ClassKind.ENUM_CLASS -> {
                        if (declaration.annotations.none { it.annotationType.resolve() == serializableAnnotationType }) {
                            error("Enums must be annotated with @Serializable: $declaration")
                        }
                        val enumSpec = EnumSpec.builder(declaration.getTypescriptName()).apply {
                            addModifiers(Modifier.EXPORT)
                            declaration.declarations.filterIsInstance<KSClassDeclaration>()
                                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                                .forEach { enumEntry ->
                                    val name = enumEntry.simpleName.asString()
                                    addConstant(name, CodeBlock.of("%S", name))
                                }
                        }.build()
                        typescriptFileBuilder.addEnum(enumSpec)
                    }

                    ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                    ClassKind.OBJECT -> error("Object declarations are not supported")
                    ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                }
            }

            is KSFunctionDeclaration -> {
                error("Function declarations are not supported")
            }

            is KSTypeAlias -> {
                createTypescriptTypeDeclaration(
                    declaration.type.resolve().declaration,
                    typescriptFileBuilder
                )
            }

            is KSPropertyDeclaration -> {
                error("Property declarations are not supported")
            }

            is KSTypeParameter -> {
                error("Type parameter declarations are not supported")
            }

            else -> {
                error("Unsupported declaration: $declaration")
            }
        }
    }
}

private fun Resolver.needsSerialization(types: List<KSType>): Boolean {
    return types.any { needsSerialization(it) }
}

private fun Resolver.needsSerialization(type: KSType): Boolean {
    return getTypescriptTypeName(type) != getTypescriptSerializedTypeName(type)
}

private fun Resolver.toTypescriptPropertySpec(propertyDeclaration: KSPropertyDeclaration): PropertySpec {
    val name =
        propertyDeclaration.annotations.singleOrNull { it.annotationType.resolve() == serialNameAnnotationType }
            ?.let { it.arguments.single().value as String }
            ?: propertyDeclaration.simpleName.asString()
    return PropertySpec.builder(
        name,
        this.typeReferenceToTypescriptTypeName(propertyDeclaration.type)
    ).build()
}

private fun Resolver.typeReferenceToTypescriptTypeName(typeReference: KSTypeReference): TypeName {
    val resolvedType = typeReference.resolve()
    return getTypescriptTypeName(resolvedType)
}

// Breath-first search to find all used types(property types, sealed types, etc), given initial types
fun findAllUsedTypes(types: List<KSType>): MutableSet<KSDeclaration> {
    val toBeProcessed = types.toMutableList()
    val processed = mutableSetOf<KSDeclaration>()

    fun scheduleForProcessing(type: KSType) {
        toBeProcessed.add(type)
    }

    while (toBeProcessed.isNotEmpty()) {
        val current = toBeProcessed.removeAt(0)
        val declaration = current.declaration
        if (declaration !in processed) {
            processed.add(declaration)

            when (declaration) {
                is KSClassDeclaration -> {
                    if (declaration.classKind == ClassKind.CLASS) {
                        if (com.google.devtools.ksp.symbol.Modifier.DATA in declaration.modifiers) {
                            // data class
                            declaration.getAllProperties().forEach {
                                scheduleForProcessing(it.type.resolve())
                            }
                        } else if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                            // sealed class
                            declaration.getSealedSubclasses().forEach {
                                scheduleForProcessing(it.asStarProjectedType())
                            }
                        }
                    }
                }

                is KSTypeAlias -> {
                    scheduleForProcessing(declaration.type.resolve())
                }

                is KSFunctionDeclaration -> {
                    error("Function declarations are not supported")
                }

                is KSPropertyDeclaration -> {
                    scheduleForProcessing(declaration.type.resolve())
                }

                is KSTypeParameter -> {
                    error("Type parameter declarations are not supported")
                }

                else -> {
                    error("Unsupported declaration: $declaration")
                }
            }
        }
        current.arguments.forEach {
            val type = it.type
            // if not a type variable
            if (type != null) {
                scheduleForProcessing(type.resolve())
            }
        }
    }
    return processed
}

private fun kspDependencies(
    aggregating: Boolean,
    originatingKSFiles: Iterable<KSFile>,
): Dependencies = Dependencies(aggregating, *originatingKSFiles.toList().toTypedArray())

fun FileSpec.writeTo(
    codeGenerator: CodeGenerator,
    dependencies: Dependencies,
) {
    val file = codeGenerator.createNewFileByPath(dependencies, modulePath, extensionName = "ts")
    // Don't use writeTo(file) because that tries to handle directories under the hood
    OutputStreamWriter(file, StandardCharsets.UTF_8)
        .use(::writeTo)
}


private val TypescriptJsonTypeName = TypeName.implicit("JSON")
private val TypescriptRecordTypeName = TypeName.implicit("Record")
private fun recordType(key: TypeName, value: TypeName): TypeName {
    return TypeName.parameterizedType(TypescriptRecordTypeName, key, value)
}
