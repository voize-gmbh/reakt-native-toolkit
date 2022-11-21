package de.voize.core.util

typealias IOSResolvePromise<T> = (T) -> Unit
typealias IOSRejectPromise = (Throwable, Map<String, Any?>) -> Unit

data class PromiseIOS<T>(val resolve: IOSResolvePromise<T>, val reject: IOSRejectPromise)
