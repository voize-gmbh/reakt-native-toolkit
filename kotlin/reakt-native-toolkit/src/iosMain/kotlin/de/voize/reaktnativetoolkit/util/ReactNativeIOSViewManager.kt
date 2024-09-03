package de.voize.reaktnativetoolkit.util

/**
 * A subclass of this interface serves as the communication between an annotated Compose view
 * and its respective RCTViewManager subclass (which is generated into Objective-C code).
 *
 * It provides the Compose view, handles Kotlin dependency injection,
 * maintains the Flows that store the RN prop values and allows wiring the callbacks.
 *
 * Methods of a class implementing this interface are called from the RCViewManager subclass from Objective-C.
 */
interface ReactNativeIOSViewManager