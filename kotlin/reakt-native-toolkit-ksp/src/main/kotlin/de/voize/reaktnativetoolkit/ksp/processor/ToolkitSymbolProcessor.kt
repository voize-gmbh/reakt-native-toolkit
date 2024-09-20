package de.voize.reaktnativetoolkit.ksp.processor

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

const val JvmPlatform = "JVM"
const val NativePlatform = "Native"

class ToolkitSymbolProcessor(
    codeGenerator: CodeGenerator,
    platforms: List<PlatformInfo>,
    options: Map<String, String>,
    logger: KSPLogger,
) : SymbolProcessor {
    private val reactNativeModuleGenerator = ReactNativeModuleGenerator(
        codeGenerator,
        platforms,
        options,
        logger,
    )

    private val reactNativeViewManagerGenerator = ReactNativeViewManagerGenerator(
        codeGenerator,
        platforms,
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val deferredSymbols = reactNativeModuleGenerator.process(resolver)
        reactNativeViewManagerGenerator.process(resolver)
        return deferredSymbols
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