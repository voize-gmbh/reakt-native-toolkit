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

var exceptionInterceptor: ExceptionInterceptor = { emptyMap() }