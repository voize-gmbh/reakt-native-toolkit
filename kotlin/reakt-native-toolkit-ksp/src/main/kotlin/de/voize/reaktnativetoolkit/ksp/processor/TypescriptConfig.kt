package de.voize.reaktnativetoolkit.ksp.processor

internal data class ExternalModule(
    val moduleName: String,
    val fromReaktNativeToolkit: Boolean,
)

internal typealias ExternalTypeMapping = Map<String, ExternalModule>

internal fun ExternalTypeMapping.getExternalModule(nameAndNamespace: String): ExternalModule? {
    return filterKeys {
        nameAndNamespace.startsWith(it)
    }.values.firstOrNull()
}

internal data class TypescriptConfig(
    val defaultInstantJSType: String,
    val externalTypeMapping: ExternalTypeMapping,
) {
    companion object {
        internal fun fromOptions(options: Map<String, String>) = TypescriptConfig(
            defaultInstantJSType = options["reakt.native.toolkit.defaultInstantJsType"] ?: "string",
            externalTypeMapping = run {
                // Workaround for not allowed "," character in the value of the argument
                val externNamespaces = options["reakt.native.toolkit.externalNamespaces"]?.split(";").orEmpty()
                externNamespaces.associateWith {
                    ExternalModule(
                        moduleName = options["reakt.native.toolkit.externalNamespaceModuleName.$it"]
                            ?: error("Missing module name for $it"),
                        fromReaktNativeToolkit = options["reakt.native.toolkit.externalNamespaceGeneratedByToolkit.$it"]?.toBoolean()
                            ?: false,
                    )
                }
            }
        )
    }
}
