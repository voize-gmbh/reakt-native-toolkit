package de.voize.reaktnativetoolkit.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ReactNativeModule(val name: String, val supportedEvents: Array<String> = [])
