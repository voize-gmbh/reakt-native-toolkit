package de.voize.reaktnativetoolkit.util

sealed interface StateOrInitial<out T> {
    data object Initial : StateOrInitial<Nothing>
    data class State<T>(val value: T) : StateOrInitial<T>
}