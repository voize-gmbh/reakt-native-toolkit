package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import io.outfoxx.typescriptpoet.CodeBlock
import io.outfoxx.typescriptpoet.CodeBlock.Companion.joinToCode
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.NameAllocator
import io.outfoxx.typescriptpoet.SymbolSpec
import io.outfoxx.typescriptpoet.TypeName
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.UUID

internal const val modelsModule = "models"
internal const val generatedTsFilePath = "$generatedCommonFilePath/typescript/"

/**
 * Used to get the symbol for a type in the "models" module or an external module.
 */
internal fun getSymbol(
    nameWithNamespace: String,
    externalTypeMapping: ExternalTypeMapping,
): SymbolSpec {
    val externalModule = externalTypeMapping.getExternalModule(nameWithNamespace)
    return if (externalModule != null) {
        SymbolSpec.importsName(nameWithNamespace, externalModule.moduleName)
    } else {
        SymbolSpec.importsName(nameWithNamespace, "!$generatedTsFilePath$modelsModule")
    }
}

internal fun getTypeName(nameWithNamespace: String, externalTypeMapping: ExternalTypeMapping): TypeName.Standard {
    return TypeName.standard(getSymbol(nameWithNamespace, externalTypeMapping))
}

internal fun getTypescriptTypeName(
    ksType: KSType,
    annotations: Sequence<KSAnnotation>? = null,
    externalTypeMapping: ExternalTypeMapping,
    defaultInstantJSType: String,
    logger: KSPLogger,
): TypeName {
    try {
        if (ksType.isError) {
            return TypeName.ANY
        }

        fun resolveTypeArgument(index: Int): TypeName {
            val argument = ksType.arguments[index]
            val type = argument.type
            if (type != null) {
                return getTypescriptTypeName(
                    type.resolve(),
                    argument.annotations,
                    externalTypeMapping,
                    defaultInstantJSType,
                    logger,
                )
            } else {
                error("Could not resolve type argument")
            }
        }

        fun isSupportedMapKeyType(ksType: KSType): Boolean {
            return when (val declaration = ksType.declaration) {
                is KSClassDeclaration -> {
                    if (
                        listOf(
                            "kotlin.String",
                            "kotlin.Char",
                            "kotlin.Int",
                            "kotlin.Long",
                            "kotlin.Short",
                            "kotlin.Float",
                            "kotlin.Double",
                            "kotlin.Number",
                            "kotlin.Boolean",
                            "kotlin.Byte",
                        ).contains(declaration.qualifiedName?.asString())
                    ) {
                        true
                    } else if (Modifier.ENUM in declaration.modifiers) {
                        true
                    } else if (Modifier.VALUE in declaration.modifiers || Modifier.INLINE in declaration.modifiers) {
                        isSupportedMapKeyType(declaration.primaryConstructor!!.parameters.first().type.resolve())
                    } else {
                        false
                    }
                }
                is KSTypeAlias -> {
                    isSupportedMapKeyType(declaration.type.resolve())
                }
                else -> false
            }
        }

        val typeName = when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Any" -> TypeName.ANY
            "kotlin.Boolean" -> TypeName.BOOLEAN
            "kotlin.Byte" -> TypeName.NUMBER
            "kotlin.Char" -> TypeName.NUMBER
            "kotlin.Double" -> TypeName.NUMBER
            "kotlin.Float" -> TypeName.NUMBER
            "kotlin.Int" -> TypeName.NUMBER
            "kotlin.Long" -> TypeName.NUMBER
            "kotlin.Number" -> TypeName.NUMBER
            "kotlin.Short" -> TypeName.NUMBER
            "kotlin.String" -> TypeName.STRING
            "kotlin.Unit" -> TypeName.VOID
            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set" -> TypeName.arrayType(
                resolveTypeArgument(0)
            )

            "kotlin.collections.Map" -> {
                val keyType = ksType.arguments.first().type!!.resolve()

                require(isSupportedMapKeyType(keyType)) {
                    "Map key type must be a primitive or assignable to a primitive in TypeScript (e.g. value class or typealias) in $ksType"
                }

                recordType(
                    resolveTypeArgument(0),
                    resolveTypeArgument(1),
                )
            }

            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.time.Duration",
            "kotlin.time.Instant",
            "kotlinx.datetime.Instant",
            "kotlinx.datetime.LocalDate",
            "kotlinx.datetime.LocalDateTime",
            "kotlinx.datetime.LocalTime" -> getTypescriptTypeNameForDateTime(
                ksType,
                annotations,
                defaultInstantJSType,
            )

            else -> {
                val declaration = ksType.declaration
                val externalModule = externalTypeMapping.getExternalModule(declaration.getTypescriptNameWithNamespace())
                if (declaration.origin != Origin.KOTLIN && externalModule == null) {
                    logger.warn("External declarations are not supported and are stubbed with any: ${declaration.qualifiedName?.asString()}")
                    TypeName.ANY
                } else when (declaration) {
                    is KSClassDeclaration -> {
                        val sealedSuperclass = declaration.getSealedSuperclass()
                        when (declaration.classKind) {
                            ClassKind.INTERFACE -> {
                                if (Modifier.SEALED in declaration.modifiers) {
                                    getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                                } else {
                                    error("Interfaces are not supported")
                                }
                            }
                            ClassKind.CLASS -> {
                                if (Modifier.DATA in declaration.modifiers) {
                                    // data class
                                    val rawTypeName = getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                                    if (sealedSuperclass != null) {
                                        rawTypeName.withoutSealedClassDiscriminator(sealedSuperclass)
                                    } else {
                                        rawTypeName
                                    }
                                } else if (Modifier.SEALED in declaration.modifiers) {
                                    getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                                } else if (Modifier.VALUE in declaration.modifiers || Modifier.INLINE in declaration.modifiers) {
                                    // value class
                                    getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                                } else {
                                    error("Only data classes, sealed classes and value classes are supported, found: $ksType")
                                }
                            }

                            ClassKind.ENUM_CLASS -> {
                                getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                            }

                            ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                            ClassKind.OBJECT -> {
                                val rawTypeName = getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
                                if (sealedSuperclass != null) {
                                    rawTypeName.withoutSealedClassDiscriminator(sealedSuperclass)
                                } else {
                                    rawTypeName
                                }
                            }

                            ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                        }
                    }

                    is KSFunctionDeclaration -> {
                        error("Function declarations are not supported")
                    }

                    is KSTypeAlias -> {
                        // TODO support type aliases
                        getTypeName(declaration.getTypescriptNameWithNamespace(), externalTypeMapping)
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

        return typeName.withNullable(ksType.isMarkedNullable)
    } catch (e: Exception) {
        throw IllegalArgumentException("Cannot get typescript type name for $ksType", e)
    }
}

internal fun getTypescriptSerializedTypeName(ksType: KSType): TypeName {
    val useJsonSerialization = true
    if (ksType.isError) {
        return TypeName.ANY
    }

    fun resolveTypeArgument(index: Int): TypeName {
        val argument = ksType.arguments[index]
        val type = argument.type
        if (type != null) {
            return getTypescriptSerializedTypeName(type.resolve())
        } else {
            error("Could not resolve type argument")
        }
    }

    return when (ksType.declaration.qualifiedName?.asString()) {
        "kotlin.Any" -> TypeName.ANY
        "kotlin.Boolean" -> TypeName.BOOLEAN
        "kotlin.Byte" -> TypeName.NUMBER
        "kotlin.Char" -> TypeName.NUMBER
        "kotlin.Double" -> TypeName.NUMBER
        "kotlin.Float" -> TypeName.NUMBER
        "kotlin.Int" -> TypeName.NUMBER
        "kotlin.Long" -> TypeName.NUMBER
        "kotlin.Number" -> TypeName.NUMBER
        "kotlin.Short" -> TypeName.NUMBER
        "kotlin.String" -> TypeName.STRING
        "kotlin.Unit" -> TypeName.VOID
        else -> null
    }?.withNullable(ksType.isMarkedNullable)
        ?: if (useJsonSerialization) {
            when (ksType.declaration.qualifiedName?.asString()) {
                "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set" -> TypeName.STRING
                "kotlin.collections.Map" -> TypeName.STRING
                else -> null
            }
        } else {
            when (ksType.declaration.qualifiedName?.asString()) {
                "kotlin.Array", "kotlin.collections.List", "kotlin.collections.Set" -> TypeName.arrayType(
                    resolveTypeArgument(0)
                )

                "kotlin.collections.Map" -> recordType(
                    resolveTypeArgument(0),
                    resolveTypeArgument(1),
                )

                else -> null
            }?.withNullable(ksType.isMarkedNullable)
        }
        ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.time.Duration" -> TypeName.STRING
            "kotlin.time.Instant" -> TypeName.STRING
            "kotlinx.datetime.Instant" -> TypeName.STRING
            "kotlinx.datetime.LocalDate" -> TypeName.STRING
            "kotlinx.datetime.LocalDateTime" -> TypeName.STRING
            "kotlinx.datetime.LocalTime" -> TypeName.STRING
            else -> when (val declaration = ksType.declaration) {
                is KSClassDeclaration -> {
                    when (declaration.classKind) {
                        ClassKind.INTERFACE -> {
                            if (Modifier.SEALED in declaration.modifiers) {
                                // sealed interface
                                TypeName.STRING
                            } else {
                                error("Interfaces are not supported")
                            }
                        }
                        ClassKind.CLASS -> {
                            if (Modifier.DATA in declaration.modifiers) {
                                // data class
                                TypeName.STRING
                            } else if (Modifier.SEALED in declaration.modifiers) {
                                // sealed class
                                TypeName.STRING
                            } else if (Modifier.VALUE in declaration.modifiers
                                || Modifier.INLINE in declaration.modifiers
                            ) {
                                // value class
                                TypeName.STRING
                            } else {
                                error("Only data classes, sealed classes and value classes are supported, found $ksType")
                            }
                        }

                        ClassKind.ENUM_CLASS -> TypeName.STRING
                        ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                        ClassKind.OBJECT -> TypeName.STRING
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

internal fun TypeName.withoutSealedClassDiscriminator(sealedSuperclass: KSDeclaration): TypeName {
    return TypescriptOmitTypeName.parameterized(
        this,
        TypeName.standard(
            CodeBlock.of("%S", sealedSuperclass.getDiscriminatorKeyForSealedClass()).toString()
        )
    )
}

internal fun KSAnnotation.getJSTypeIdentifier(): String {
    return arguments.single {
        it.name?.asString() == "identifier"
    }.value as String
}

internal fun Sequence<KSAnnotation>.getJSTypeAnnotationOrNull(): KSAnnotation? =
    singleOrNull {
        it.shortName.getShortName() == "JSType" &&
                it.annotationType.resolve().declaration.qualifiedName?.asString() == "de.voize.reaktnativetoolkit.annotation.JSType"
    }

internal fun getTypescriptTypeNameForDateTime(
    ksType: KSType,
    annotations: Sequence<KSAnnotation>?,
    defaultInstantJSType: String,
): TypeName {
    val jsTypeAnnotation = (annotations ?: ksType.annotations).getJSTypeAnnotationOrNull()
    val jsTypeIdentifier = jsTypeAnnotation?.getJSTypeIdentifier()
    return when (ksType.declaration.qualifiedName?.asString()) {
        "kotlin.time.Duration" -> TypeName.STRING
        "kotlin.time.Instant",
        "kotlinx.datetime.Instant" -> when (val identifier = jsTypeIdentifier
            ?: defaultInstantJSType) {
            "string" -> TypeName.STRING
            "date" -> TypeName.DATE
            else -> error("Unsupported JSType identifier for Instant: $identifier")
        }

        "kotlinx.datetime.LocalDate" -> TypeName.STRING
        "kotlinx.datetime.LocalDateTime" -> TypeName.STRING
        "kotlinx.datetime.LocalTime" -> TypeName.STRING
        else -> error("Unsupported declaration for date time: ${ksType.declaration}")
    }
}

/**
 * Construct the typescript name for a given declaration.
 * Nested declarations are concatenated with the parent declaration name.
 */
internal fun KSDeclaration.getTypescriptName(): String {
    return simpleName.asString()
}

internal fun KSDeclaration.getTypescriptNamespace(): String {
    val parent = parentDeclaration
    return if (parent != null) {
        "${parent.getTypescriptNamespace()}.${parent.getTypescriptName()}".removePrefix(".")
    } else {
        packageName.asString()
    }
}

internal fun KSDeclaration.getTypescriptNameWithNamespace(): String {
    return "${getTypescriptNamespace()}.${getTypescriptName()}".removePrefix(".")
}

internal fun FileSpec.writeTo(
    codeGenerator: CodeGenerator,
    dependencies: Dependencies,
    extensionName: String = "ts",
) {
    val file = codeGenerator.createNewFileByPath(dependencies, modulePath, extensionName)
    // Don't use writeTo(file) because that tries to handle directories under the hood
    OutputStreamWriter(file, StandardCharsets.UTF_8)
        .use(::writeTo)
}

internal fun KSType.needsJSSerialization(): Boolean {
    val useJsonSerialization = true
    return declaration.qualifiedName?.asString() !in setOf(
        "kotlin.Any",
        "kotlin.Boolean",
        "kotlin.Byte",
        "kotlin.Char",
        "kotlin.Double",
        "kotlin.Float",
        "kotlin.Int",
        "kotlin.Long",
        "kotlin.Number",
        "kotlin.Short",
        "kotlin.String",
        "kotlin.Unit",
    ) && (useJsonSerialization || declaration.qualifiedName?.asString() !in setOf(
        "kotlin.Array",
        "kotlin.collections.List",
        "kotlin.collections.Set",
        "kotlin.collections.Map",
    ))
}

internal fun convertJsonToType(
    json: CodeBlock,
    ksType: KSType,
    nameAllocator: NameAllocator,
    externalTypeMapping: ExternalTypeMapping,
    defaultInstantJSType: String,
    logger: KSPLogger,
    annotations: Sequence<KSAnnotation>? = null
): CodeBlock {
    if (ksType.isError) {
        return json
    }

    val jsTypeAnnotation = (annotations ?: ksType.annotations).getJSTypeAnnotationOrNull()
    val jsTypeIdentifier = jsTypeAnnotation?.getJSTypeIdentifier()

    return json.ifNotNull(
        ksType.isMarkedNullable,
        nameAllocator
    ) { nonNullVariableName, nameAllocator ->
        fun convertTypeArgument(
            argumentValue: CodeBlock,
            index: Int
        ): CodeBlock {
            val argument = ksType.arguments[index]
            val type = argument.type
            if (type != null) {
                return convertJsonToType(
                    argumentValue,
                    type.resolve(),
                    nameAllocator.copy(),
                    externalTypeMapping,
                    defaultInstantJSType,
                    logger,
                    argument.annotations,
                )
            } else {
                error("Could not convert type argument")
            }
        }

        when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Any" -> nonNullVariableName.asCodeBlock()
            "kotlin.Boolean" -> nonNullVariableName.asCodeBlock()
            "kotlin.Byte" -> nonNullVariableName.asCodeBlock()
            "kotlin.Char" -> nonNullVariableName.asCodeBlock()
            "kotlin.Double" -> nonNullVariableName.asCodeBlock()
            "kotlin.Float" -> nonNullVariableName.asCodeBlock()
            "kotlin.Int" -> nonNullVariableName.asCodeBlock()
            "kotlin.Long" -> nonNullVariableName.asCodeBlock()
            "kotlin.Number" -> nonNullVariableName.asCodeBlock()
            "kotlin.Short" -> nonNullVariableName.asCodeBlock()
            "kotlin.String" -> nonNullVariableName.asCodeBlock()
            "kotlin.Unit" -> nonNullVariableName.asCodeBlock()
            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Array",
            "kotlin.collections.List",
            "kotlin.collections.Set" -> {
                val arrayItemTag = UUID.randomUUID()
                nameAllocator.newName("it", arrayItemTag)
                CodeBlock.of(
                    "%N.map(%L)",
                    nonNullVariableName,
                    lambda(
                        args = listOf(
                            parameter(
                                nameAllocator[arrayItemTag],
                                TypeName.ANY,
                            )
                        ),
                        body = convertTypeArgument(nameAllocator[arrayItemTag].asCodeBlock(), 0)
                    )
                )
            }

            "kotlin.collections.Map" -> {
                val mapItemKeyTag = UUID.randomUUID()
                val mapItemValueTag = UUID.randomUUID()
                nameAllocator.newName("key", mapItemKeyTag)
                nameAllocator.newName("value", mapItemValueTag)
                CodeBlock.of(
                    "%Q(%Q(%N).map(%L))",
                    objectFromEntriesName,
                    objectEntriesName,
                    nonNullVariableName,
                    lambda(
                        args = listOf(
                            CodeBlock.of(
                                "[%N, %N]: [%T, %T]",
                                nameAllocator[mapItemKeyTag],
                                nameAllocator[mapItemValueTag],
                                TypeName.ANY,
                                TypeName.ANY,
                            )
                        ),
                        body = CodeBlock.of(
                            "[%L, %L]",
                            convertTypeArgument(nameAllocator[mapItemKeyTag].asCodeBlock(), 0),
                            convertTypeArgument(
                                nameAllocator[mapItemValueTag].asCodeBlock(),
                                1
                            ),
                        )
                    )
                )
            }

            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.time.Duration" -> nonNullVariableName.asCodeBlock()
            "kotlin.time.Instant",
            "kotlinx.datetime.Instant" -> when (val identifier =
                jsTypeIdentifier ?: defaultInstantJSType) {
                "string" -> nonNullVariableName.asCodeBlock()
                "date" -> CodeBlock.of(
                    "new %T(%N)",
                    TypescriptDateTypeName,
                    nonNullVariableName,
                )

                else -> error("Unsupported JSType identifier for Instant: $identifier")
            }

            "kotlinx.datetime.LocalDate" -> nonNullVariableName.asCodeBlock()
            "kotlinx.datetime.LocalDateTime" -> nonNullVariableName.asCodeBlock()
            "kotlinx.datetime.LocalTime" -> nonNullVariableName.asCodeBlock()

            else -> {
                val declaration = ksType.declaration
                if (declaration.origin != Origin.KOTLIN && externalTypeMapping.getExternalModule(declaration.getTypescriptNameWithNamespace())?.fromReaktNativeToolkit != true) {
                    logger.warn("External declarations are not supported and are not mapped: ${declaration.qualifiedName?.asString()}")
                    nonNullVariableName.asCodeBlock()
                } else when (declaration) {
                    is KSClassDeclaration -> when (declaration.classKind) {
                        ClassKind.INTERFACE -> {
                            if (Modifier.SEALED in declaration.modifiers) {
                                // sealed interface
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptFromJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else {
                                error("Interfaces are not supported")
                            }
                        }
                        ClassKind.CLASS -> {
                            if (Modifier.DATA in declaration.modifiers) {
                                // data class
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptFromJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else if (Modifier.SEALED in declaration.modifiers) {
                                // sealed class
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptFromJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else if (Modifier.VALUE in declaration.modifiers || Modifier.INLINE in declaration.modifiers) {
                                // value class
                                nonNullVariableName.asCodeBlock()
                            } else {
                                error("Only data classes, sealed classes and value classes are supported, found: $declaration")
                            }
                        }

                        ClassKind.ENUM_CLASS -> {
                            CodeBlock.of(
                                "%Q(%N)",
                                declaration.getTypescriptFromJsonFunctionNameWithNamespace(externalTypeMapping),
                                nonNullVariableName,
                            )
                        }

                        ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                        ClassKind.OBJECT -> CodeBlock.of(
                            "%Q(%N)",
                            declaration.getTypescriptFromJsonFunctionNameWithNamespace(externalTypeMapping),
                            nonNullVariableName,
                        )

                        ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                    }

                    is KSFunctionDeclaration -> {
                        error("Function declarations are not supported")
                    }

                    is KSTypeAlias -> {
                        // TODO support type aliases
                        nonNullVariableName.asCodeBlock()
                    }

                    is KSPropertyDeclaration -> {
                        error("Property declarations are not supported")
                    }

                    is KSTypeParameter -> {
                        // TODO handle bounds
                        nonNullVariableName.asCodeBlock()
                    }

                    else -> {
                        error("Unsupported declaration: $declaration")
                    }
                }
            }
        }
    }.castTo(getTypescriptTypeName(ksType, annotations, externalTypeMapping, defaultInstantJSType, logger))
}

internal fun convertTypeToJson(
    value: CodeBlock,
    ksType: KSType,
    nameAllocator: NameAllocator,
    externalTypeMapping: ExternalTypeMapping,
    defaultInstantJSType: String,
    logger: KSPLogger,
    annotations: Sequence<KSAnnotation>? = null,
): CodeBlock {
    if (ksType.isError) {
        return value
    }

    val jsTypeAnnotation = (annotations ?: ksType.annotations).getJSTypeAnnotationOrNull()
    val jsTypeIdentifier = jsTypeAnnotation?.getJSTypeIdentifier()

    return value.ifNotNull(
        ksType.isMarkedNullable,
        nameAllocator
    ) { nonNullVariableName, nameAllocator ->
        fun convertTypeArgument(
            argumentValue: CodeBlock,
            index: Int
        ): CodeBlock {
            val argument = ksType.arguments[index]
            val type = argument.type
            if (type != null) {
                return convertTypeToJson(
                    argumentValue,
                    type.resolve(),
                    nameAllocator.copy(),
                    externalTypeMapping,
                    defaultInstantJSType,
                    logger,
                    argument.annotations
                )
            } else {
                error("Could not convert type argument")
            }
        }

        when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Any" -> nonNullVariableName.asCodeBlock()
            "kotlin.Boolean" -> nonNullVariableName.asCodeBlock()
            "kotlin.Byte" -> nonNullVariableName.asCodeBlock()
            "kotlin.Char" -> nonNullVariableName.asCodeBlock()
            "kotlin.Double" -> nonNullVariableName.asCodeBlock()
            "kotlin.Float" -> nonNullVariableName.asCodeBlock()
            "kotlin.Int" -> nonNullVariableName.asCodeBlock()
            "kotlin.Long" -> nonNullVariableName.asCodeBlock()
            "kotlin.Number" -> nonNullVariableName.asCodeBlock()
            "kotlin.Short" -> nonNullVariableName.asCodeBlock()
            "kotlin.String" -> nonNullVariableName.asCodeBlock()
            "kotlin.Unit" -> nonNullVariableName.asCodeBlock()
            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.Array",
            "kotlin.collections.List",
            "kotlin.collections.Set" -> {
                val arrayItemTag = UUID.randomUUID()
                nameAllocator.newName("it", arrayItemTag)
                CodeBlock.of(
                    "%N.map(%L)",
                    nonNullVariableName,
                    lambda(
                        args = listOf(
                            parameter(
                                nameAllocator[arrayItemTag],
                                TypeName.ANY,
                            )
                        ),
                        body = convertTypeArgument(nameAllocator[arrayItemTag].asCodeBlock(), 0)
                    ),
                )
            }

            "kotlin.collections.Map" -> {
                val mapItemKeyTag = UUID.randomUUID()
                val mapItemValueTag = UUID.randomUUID()
                nameAllocator.newName("key", mapItemKeyTag)
                nameAllocator.newName("value", mapItemValueTag)
                CodeBlock.of(
                    "%Q(%Q(%N).map(%L))",
                    objectFromEntriesName,
                    objectEntriesName,
                    nonNullVariableName,
                    lambda(
                        args = listOf(
                            CodeBlock.of(
                                "[%N, %N]: [%T, %T]",
                                nameAllocator[mapItemKeyTag],
                                nameAllocator[mapItemValueTag],
                                TypeName.ANY,
                                TypeName.ANY,
                            )
                        ),
                        body = CodeBlock.of(
                            "[%L, %L]",
                            convertTypeArgument(nameAllocator[mapItemKeyTag].asCodeBlock(), 0),
                            convertTypeArgument(nameAllocator[mapItemValueTag].asCodeBlock(), 1)
                        )
                    ),
                )
            }

            else -> null
        } ?: when (ksType.declaration.qualifiedName?.asString()) {
            "kotlin.time.Duration" -> nonNullVariableName.asCodeBlock()
            "kotlin.time.Instant",
            "kotlinx.datetime.Instant" -> when (val identifier =
                jsTypeIdentifier ?: defaultInstantJSType) {
                "string" -> nonNullVariableName.asCodeBlock()
                "date" -> CodeBlock.of(
                    "%N.%N()",
                    nonNullVariableName,
                    "toISOString",
                )

                else -> error("Unsupported JSType identifier for Instant: $identifier")
            }

            "kotlinx.datetime.LocalDate" -> nonNullVariableName.asCodeBlock()
            "kotlinx.datetime.LocalDateTime" -> nonNullVariableName.asCodeBlock()
            "kotlinx.datetime.LocalTime" -> nonNullVariableName.asCodeBlock()

            else -> {
                val declaration = ksType.declaration
                if (declaration.origin != Origin.KOTLIN && externalTypeMapping.getExternalModule(declaration.getTypescriptNameWithNamespace())?.fromReaktNativeToolkit != true) {
                    logger.warn("External declarations are not supported and are not mapped: ${declaration.qualifiedName?.asString()}")
                    nonNullVariableName.asCodeBlock()
                } else when (declaration) {
                    is KSClassDeclaration -> when (declaration.classKind) {
                        ClassKind.INTERFACE -> {
                            if (Modifier.SEALED in declaration.modifiers) {
                                // sealed interface
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptToJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else {
                                error("Interfaces are not supported")
                            }
                        }
                        ClassKind.CLASS -> {
                            if (Modifier.DATA in declaration.modifiers) {
                                // data class
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptToJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else if (Modifier.SEALED in declaration.modifiers) {
                                // sealed class
                                CodeBlock.of(
                                    "%Q(%N)",
                                    declaration.getTypescriptToJsonFunctionNameWithNamespace(externalTypeMapping),
                                    nonNullVariableName,
                                )
                            } else if (Modifier.VALUE in declaration.modifiers || Modifier.INLINE in declaration.modifiers) {
                                // value class
                                nonNullVariableName.asCodeBlock()
                            } else {
                                    error("Only data classes, sealed classes and value classes are supported, found: $declaration")
                                }
                            }
                        ClassKind.ENUM_CLASS -> {
                            CodeBlock.of(
                                "%Q(%N)",
                                declaration.getTypescriptToJsonFunctionNameWithNamespace(externalTypeMapping),
                                nonNullVariableName,
                            )
                        }

                        ClassKind.ENUM_ENTRY -> error("Enum entries are not supported")
                        ClassKind.OBJECT -> CodeBlock.of(
                            "%Q(%N)",
                            declaration.getTypescriptToJsonFunctionNameWithNamespace(externalTypeMapping),
                            nonNullVariableName
                        )

                        ClassKind.ANNOTATION_CLASS -> error("Annotation classes are not supported")
                    }

                    is KSFunctionDeclaration -> {
                        error("Function declarations are not supported")
                    }

                    is KSTypeAlias -> {
                        // TODO support type aliases
                        nonNullVariableName.asCodeBlock()
                    }

                    is KSPropertyDeclaration -> {
                        error("Property declarations are not supported")
                    }

                    is KSTypeParameter -> {
                        // TODO handle bounds
                        nonNullVariableName.asCodeBlock()
                    }

                    else -> {
                        error("Unsupported declaration: $declaration")
                    }
                }
            }
        }
    }
}

internal fun KSDeclaration.getTypescriptFromJsonFunctionName(): String {
    return "fromJson${getTypescriptName()}"
}

internal fun KSDeclaration.getTypescriptFromJsonFunctionNameWithNamespace(
    externalTypeMapping: ExternalTypeMapping,
): SymbolSpec {
    return getSymbol(getTypescriptNamespace(), externalTypeMapping).nested(getTypescriptFromJsonFunctionName())
}

internal fun KSDeclaration.getTypescriptToJsonFunctionName(): String {
    return "toJson${getTypescriptName()}"
}

internal fun KSDeclaration.getTypescriptToJsonFunctionNameWithNamespace(
    externalTypeMapping: ExternalTypeMapping,
): SymbolSpec {
    return getSymbol(getTypescriptNamespace(), externalTypeMapping).nested(getTypescriptToJsonFunctionName())
}

fun KSClassDeclaration.getDeclaredBackedProperties() =
    getDeclaredProperties().filter { it.hasBackingField }


internal fun CodeBlock.ifNotNull(
    isNullable: Boolean,
    nameAllocator: NameAllocator,
    codeBlock: (nonNullVariableName: String, nameAllocator: NameAllocator) -> CodeBlock
): CodeBlock {
    val tempVariableTag = UUID.randomUUID()
    nameAllocator.newName("temp", tempVariableTag)
    return if (isNullable) {
        lambda(
            emptyList(),
            codeBlock(brackets = true) {
                add(
                    const(
                        nameAllocator[tempVariableTag],
                        this@ifNotNull
                    )
                )
                add(
                    returnStatement(
                        CodeBlock.of(
                            "%N === null ? null : (%L)",
                            nameAllocator[tempVariableTag],
                            codeBlock(nameAllocator[tempVariableTag], nameAllocator.copy()),
                        )
                    )
                )
            }
        ).inParentheses().invoke()
    } else {
        lambda(
            emptyList(),
            codeBlock(brackets = true) {
                add(
                    const(
                        nameAllocator[tempVariableTag],
                        this@ifNotNull
                    )
                )
                add(
                    returnStatement(
                        codeBlock(nameAllocator[tempVariableTag], nameAllocator.copy()),
                    )
                )
            }
        ).inParentheses().invoke()
    }
}

internal fun TypeName.withNullable(nullable: Boolean): TypeName {
    return if (nullable) {
        asNullable()
    } else {
        this
    }
}

internal fun TypeName.asNullable() = TypeName.unionType(this, TypeName.NULL)

// code generation helpers

/**
 * Convert this name into an expression.
 */
internal fun String.asCodeBlock() = CodeBlock.of("%N", this@asCodeBlock)

/**
 * Convert this symbol to an expression.
 */
internal fun SymbolSpec.asCodeBlock() = CodeBlock.of("%Q", this@asCodeBlock)

internal fun recordType(key: TypeName, value: TypeName): TypeName {
    return TypeName.parameterizedType(TypescriptRecordTypeName, key, value)
}

internal fun CodeBlock.castTo(typeName: TypeName): CodeBlock {
    return CodeBlock.of("(%L) as %T", this, typeName)
}

/**
 * A property for an object literal.
 */
internal fun property(name: String, expression: CodeBlock) =
    CodeBlock.of("[%S]: %L", name, expression)

internal fun codeBlock(brackets: Boolean, builder: CodeBlock.Builder.() -> Unit): CodeBlock {
    val codeBlock = CodeBlock.builder().apply(builder).build()
    return if (brackets) {
        block(codeBlock)
    } else {
        codeBlock
    }
}
/**
 * Simple parameter for a function. No deconstruction, no default value.
 */
internal fun parameter(name: String, typeName: TypeName) =
    CodeBlock.of("%N: %T", name, typeName)

internal fun returnStatement(expression: CodeBlock) =
    CodeBlock.of("return %L;\n", expression)

internal fun lambda(args: List<CodeBlock>, body: CodeBlock): CodeBlock {
    return CodeBlock.of("(%L) =>%W%L", args.joinToCode(), body)
}

internal fun jsxProp(name: String, value: CodeBlock) =
    CodeBlock.of("%N={%L}", name, value)

internal fun block(body: CodeBlock): CodeBlock {
    return CodeBlock.of("{\n%>%L%<}", body)
}

internal fun CodeBlock.inParentheses(): CodeBlock {
    return CodeBlock.of("(%L)", this)
}

internal fun CodeBlock.invoke(args: List<CodeBlock> = emptyList()): CodeBlock {
    return CodeBlock.of("%L(%L)", this, args.joinToCode())
}

internal fun CodeBlock.export() =
    CodeBlock.of("export %L", this)

internal fun const(name: String, expression: CodeBlock) =
    CodeBlock.of("const %N = %L;\n", name, expression)

internal fun const(name: String, typeName: TypeName, expression: CodeBlock) =
    CodeBlock.of("const %N: %T = %L;\n", name, typeName, expression)

// known type names

internal val TypescriptJsonName = SymbolSpec.implicit("JSON")
internal val jsonStringifyName = TypescriptJsonName.nested("stringify")
internal val jsonParseName = TypescriptJsonName.nested("parse")

internal val TypescriptRecordTypeName = TypeName.implicit("Record")
internal val TypescriptOmitTypeName = TypeName.implicit("Omit")

private val TypescriptObjectName = SymbolSpec.implicit("Object")
private val objectEntriesName = TypescriptObjectName.nested("entries")
private val objectFromEntriesName = TypescriptObjectName.nested("fromEntries")
private val TypescriptDateTypeName = TypeName.implicit("Date")
