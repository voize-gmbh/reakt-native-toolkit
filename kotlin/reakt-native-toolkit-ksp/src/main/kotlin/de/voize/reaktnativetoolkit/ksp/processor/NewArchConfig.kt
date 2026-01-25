package de.voize.reaktnativetoolkit.ksp.processor

internal data class NewArchConfig(
    val enabled: Boolean,
    val outputPath: String,
    val errorCodePrefix: String,
    val kmpFrameworkName: String,
) {
    companion object {
        fun fromOptions(options: Map<String, String>) = NewArchConfig(
            enabled = options["reakt.native.toolkit.newArchitecture"]?.toBoolean() ?: false,
            outputPath = options["reakt.native.toolkit.iosNewArchOutputPath"] ?: "iosNewArch/",
            errorCodePrefix = options["reakt.native.toolkit.errorCodePrefix"] ?: "RN",
            kmpFrameworkName = options["reakt.native.toolkit.kmpFrameworkName"] ?: "ComposeApp",
        )
    }
}
