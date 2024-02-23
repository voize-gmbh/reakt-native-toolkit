package de.voize.reaktnativetoolkit.util

actual fun <T> toReactNativeValue(value: T): Any? {
    error("Not implemented for JS/WASM target")
}