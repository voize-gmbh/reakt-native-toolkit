package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Origin

internal object TypescriptModelsNamespaceTree {
    internal data class NamespaceNode(
        val name: String,
        val children: List<NamespaceNode>,
        val declarations: List<KSDeclaration>,
    )

    internal fun build(types: List<KSType>): Pair<NamespaceNode, List<KSFile>> {
        val customTypes = filterTypesForGeneration(findAllUsedTypes(types))
        val rootNamespace = buildNamespaceTree("", customTypes)
        val typesOriginatingFiles = customTypes.mapNotNull { it.containingFile }
        return rootNamespace to typesOriginatingFiles
    }

    // Breath-first search to find all used types(property types, sealed types, etc), given initial types
    private fun findAllUsedTypes(types: List<KSType>): Set<KSDeclaration> {
        val toBeProcessed = types.toMutableList()
        val processed = mutableSetOf<KSDeclaration>()

        fun scheduleForProcessing(type: KSType) {
            toBeProcessed.add(type)
        }

        while (toBeProcessed.isNotEmpty()) {
            val current = toBeProcessed.removeAt(0)
            if (current.isError) {
                continue
            }
            val declaration = current.declaration
            if (declaration !in processed) {
                processed.add(declaration)

                when (declaration) {
                    is KSClassDeclaration -> {

                        when (declaration.classKind) {
                            ClassKind.CLASS -> {
                                if (com.google.devtools.ksp.symbol.Modifier.DATA in declaration.modifiers) {
                                    // data class
                                    declaration.getDeclaredBackedProperties().forEach {
                                        scheduleForProcessing(it.type.resolve())
                                    }
                                } else if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                                    // sealed class
                                    declaration.getSealedSubclasses().forEach {
                                        scheduleForProcessing(it.asStarProjectedType())
                                    }
                                }
                            }

                            ClassKind.INTERFACE -> {
                                if (com.google.devtools.ksp.symbol.Modifier.SEALED in declaration.modifiers) {
                                    // sealed interface
                                    declaration.getSealedSubclasses().forEach {
                                        scheduleForProcessing(it.asStarProjectedType())
                                    }
                                }
                            }

                            else -> Unit
                        }
                        declaration.superTypes.filter {
                            // only process sealed superclasses
                            com.google.devtools.ksp.symbol.Modifier.SEALED in it.resolve().declaration.modifiers
                        }.forEach {
                            scheduleForProcessing(it.resolve())
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
                        declaration.bounds.map { it.resolve() }.forEach(::scheduleForProcessing)
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

    private fun filterTypesForGeneration(types: Set<KSDeclaration>): Collection<KSDeclaration> {
        val customTypes = types.filter {
            val defaultTypes = setOf(
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
                "kotlin.collections.List",
                "kotlin.collections.Map",
                "kotlin.collections.Set",
                "kotlin.time.Duration",
                "kotlinx.datetime.Instant",
                "kotlinx.datetime.LocalDate",
                "kotlinx.datetime.LocalDateTime",
                "kotlinx.datetime.LocalTime",
            )
            it.qualifiedName?.asString() !in defaultTypes
        }

        return customTypes.filter {
            when (it) {
                is KSClassDeclaration -> it.origin == Origin.KOTLIN
                is KSTypeParameter -> false
                else -> true
            }
        }
    }

    private fun buildNamespaceTree(
        currentNamespace: String,
        declarations: Collection<KSDeclaration>
    ): NamespaceNode {
        val (children, declarationsInNamespace) = declarations.partition {
            it.qualifiedName?.asString()?.removePrefix("$currentNamespace.")?.contains('.') ?: false
        }

        return NamespaceNode(
            name = currentNamespace.substringAfterLast('.'),
            children = children.groupBy {
                it.qualifiedName?.asString()?.removePrefix("$currentNamespace.")
                    ?.substringBefore('.')
                    ?: error("Expected a qualified name for $it")
            }.map { (name, declarations) ->
                buildNamespaceTree("$currentNamespace.$name".removePrefix("."), declarations)
            },
            declarations = declarationsInNamespace,
        )
    }
}