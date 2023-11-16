package de.voize.reaktnativetoolkit.util

fun Throwable.allMessages(): String {
    val cause = cause
    return this::class.simpleName + ": " + message + if (cause != null) {
        "\nCaused by: " + cause.allMessages()
    } else {
        ""
    }
}

typealias ExceptionInterceptor = (throwable: Throwable) -> Map<String, Any?>

fun Throwable.getJSExtraData(): Map<String, Any?> {
    // Do not call exceptionInterceptor for internal ReactNativeUseFlowSubscriptionCancellationException
    if (this is ReactNativeUseFlowSubscriptionCancellationException) {
        return emptyMap()
    }
    return exceptionInterceptor(this)
}

var exceptionInterceptor: ExceptionInterceptor = { emptyMap() }