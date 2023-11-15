package de.voize.reaktnativetoolkit.util

import platform.Foundation.NSUUID

internal actual fun uuid(): String {
    return NSUUID().UUIDString().lowercase()
}