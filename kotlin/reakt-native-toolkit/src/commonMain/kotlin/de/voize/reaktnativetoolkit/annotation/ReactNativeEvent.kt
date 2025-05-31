package de.voize.reaktnativetoolkit.annotation

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class ReactNativeEvent(val name: String)
