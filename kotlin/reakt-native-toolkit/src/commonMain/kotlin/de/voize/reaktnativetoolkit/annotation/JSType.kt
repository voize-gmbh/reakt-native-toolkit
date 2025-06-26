package de.voize.reaktnativetoolkit.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)
annotation class JSType(val identifier: String)
