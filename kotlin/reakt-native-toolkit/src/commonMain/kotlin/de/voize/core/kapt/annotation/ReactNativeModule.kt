package de.voize.core.kapt.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class ReactNativeModule(val name: String)
