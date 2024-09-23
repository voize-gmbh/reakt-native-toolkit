package de.voize.reaktnativetoolkit.ksp.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*


class ToolkitSymbolProcessor(
    codeGenerator: CodeGenerator,
    private val platforms: List<PlatformInfo>,
    options: Map<String, String>,
    logger: KSPLogger,
) : SymbolProcessor {
    private var typescriptModelsGenerationInvoked = false

    private val reactNativeModuleGenerator = ReactNativeModuleGenerator(
        codeGenerator,
        platforms,
        options,
        logger,
    )

    private val reactNativeViewManagerGenerator = ReactNativeViewManagerGenerator(
        codeGenerator,
        platforms,
        options,
        logger,
    )

    private val typescriptModelsGenerator = TypescriptModelsGenerator(
        codeGenerator,
        TypescriptConfig.fromOptions(options),
        logger,
    )

    data class ProcessResult(
        val deferredSymbols: List<KSAnnotated>,
        val types: List<KSType>,
        val originatingFiles: List<KSFile>,
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val rnModulesProcessResult = reactNativeModuleGenerator.process(resolver)
        val rnViewManagersProcessResult = reactNativeViewManagerGenerator.process(resolver)

        val exportTypescriptTypes = resolver.getSymbolsWithAnnotation(
            "$toolkitPackageName.annotation.ExportTypescriptType"
        ).map {
            when (it) {
                is KSClassDeclaration -> {
                    it.asStarProjectedType()
                }

                is KSTypeAlias -> {
                    // TODO get type of alias declaration, not the type referenced by the alias
                    error("Currently unsupported, because of missing api in KSP")
                }

                else -> throw IllegalArgumentException("ExportTypescriptType annotation can only be used on class declarations or type aliases")
            }
        }.toList()

        if (!typescriptModelsGenerationInvoked && platforms.isCommon()) {
            val (rootNamespace, typesOriginatingFiles) = TypescriptModelsNamespaceTree.build(
                rnModulesProcessResult.types + rnViewManagersProcessResult.types + exportTypescriptTypes,
            )
            typescriptModelsGenerator.createTypescriptModelsFile(
                rootNamespace,
                rnModulesProcessResult.originatingFiles + rnViewManagersProcessResult.originatingFiles + typesOriginatingFiles,
            )
            typescriptModelsGenerationInvoked = true
        }

        return rnModulesProcessResult.deferredSymbols + rnViewManagersProcessResult.deferredSymbols
    }
}

@AutoService(SymbolProcessorProvider::class)
class ToolkitSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return ToolkitSymbolProcessor(
            environment.codeGenerator,
            environment.platforms,
            environment.options,
            environment.logger
        )
    }
}