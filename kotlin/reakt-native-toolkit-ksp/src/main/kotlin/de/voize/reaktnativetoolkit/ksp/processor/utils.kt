package de.voize.reaktnativetoolkit.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Origin
import io.outfoxx.typescriptpoet.FileSpec
import io.outfoxx.typescriptpoet.TypeName
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonClassDiscriminator
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

internal const val generatedCommonFilePath = "reaktNativeToolkit/"

internal fun kspDependencies(
    aggregating: Boolean,
    originatingKSFiles: Iterable<KSFile>,
): Dependencies = Dependencies(aggregating, *originatingKSFiles.toList().toTypedArray())

@OptIn(ExperimentalSerializationApi::class)
internal fun KSDeclaration.getDiscriminatorKeyForSealedClass(): String {
    val defaultDiscriminatorKey = "type"
    return getJsonClassDiscriminatorAnnotationOrNull()?.discriminator
        ?: defaultDiscriminatorKey
}

@OptIn(KspExperimental::class, ExperimentalSerializationApi::class)
private fun KSDeclaration.getJsonClassDiscriminatorAnnotationOrNull(): JsonClassDiscriminator? =
    getAnnotationsByType(JsonClassDiscriminator::class).singleOrNull()

internal fun KSClassDeclaration.isSealedClassSubclass() =
    this.superTypes.any { com.google.devtools.ksp.symbol.Modifier.SEALED in it.resolve().declaration.modifiers }

internal fun KSClassDeclaration.getSealedSuperclass(): KSDeclaration? {
    return if (isSealedClassSubclass()) {
        superTypes.map { it.resolve().declaration }
            .single { com.google.devtools.ksp.symbol.Modifier.SEALED in it.modifiers }
    } else null
}

internal const val JvmPlatform = "JVM"
internal const val NativePlatform = "Native"

internal fun List<PlatformInfo>.isCommon(): Boolean {
    val platformNames = this.map { it.platformName }
    return JvmPlatform in platformNames && NativePlatform in platformNames
}

internal fun List<PlatformInfo>.isIOS(): Boolean {
    val platformNames = this.map { it.platformName }
    return JvmPlatform !in platformNames && NativePlatform in platformNames
}

internal fun List<PlatformInfo>.isAndroid(): Boolean {
    val platformNames = this.map { it.platformName }
    return JvmPlatform in platformNames && NativePlatform !in platformNames
}
