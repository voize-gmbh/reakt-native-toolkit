package com.myrnproject.shared

import de.voize.reaktnativetoolkit.annotation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@ReactNativeModule("TimeProvider")
class TimeProvider(
    lifecycleScope: CoroutineScope,
) {
    private val timeEverySecond = intervalFlow.map { Clock.System.now() }

    private val timeAsState = timeEverySecond.stateIn(lifecycleScope, SharingStarted.Eagerly, Clock.System.now())

    @ReactNativeFlow
    fun time(): Flow<@JSType("date") Instant> = timeEverySecond

    @ReactNativeFlow
    fun isAfter(time: String): Flow<Boolean> = timeEverySecond.map { now ->
        val time = Instant.parse(time)
        println("isAfter: $now > $time = ${now > time}")
        now > time
    }

    @ReactNativeFlow
    fun timeAsState(): Flow<@JSType("date") Instant> = timeAsState
}