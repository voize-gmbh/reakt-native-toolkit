package de.voize.reaktnativetoolkit.util

import kotlinx.cinterop.*
import platform.Foundation.NSNumber
import platform.darwin.NSObject
import react_native.*

class RCTBridgeMethodWrapper(
    private val jsName: String,
    private val action: (arguments: List<*>, promise: PromiseIOS<*>) -> Unit
) : RCTBridgeMethodProtocol, NSObject() {
    private val jsMethodNamePointer = jsName.cstr.getPointer(Arena())

    override fun JSMethodName(): CPointer<ByteVar> {
        return jsMethodNamePointer
    }

    override fun functionType(): RCTFunctionType {
        return RCTFunctionType.RCTFunctionTypePromise
    }

    override fun invokeWithBridge(bridge: RCTBridge?, module: Any?, arguments: List<*>?): Any? {
        bridge ?: error("bridge is null")
        arguments ?: error("Arguments are null")
        val rejectCbId =
            (arguments.getOrNull(arguments.size - 1) ?: error("could not retrieve reject callback id")) as NSNumber
        val resolveCbId =
            (arguments.getOrNull(arguments.size - 2) ?: error("could not retrieve resolve callback id")) as NSNumber

        val reject: IOSRejectPromise = { throwable, userInfo ->
            bridge.enqueueCallback(rejectCbId, listOf(buildMap {
                put("message", throwable.message)
                put("userInfo", userInfo)
            }
            ))
        }

        val resolve: IOSResolvePromise<*> = { value ->
            bridge.enqueueCallback(resolveCbId, listOf(value))
        }

        action(arguments.subList(0, arguments.size - 2), PromiseIOS(resolve, reject))
        return null
    }
}
