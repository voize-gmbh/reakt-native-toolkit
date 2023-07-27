# reakt-native-toolkit

[![NPM](https://img.shields.io/npm/v/reakt-native-toolkit?color=blue)](https://www.npmjs.com/package/reakt-native-toolkit)
[![Maven Central](https://img.shields.io/maven-central/v/de.voize/reakt-native-toolkit?color=blue)](https://search.maven.org/artifact/de.voize/reakt-native-toolkit)

This toolkit allows you to combine React Native with Kotlin Multiplatform Mobile (KMM) by generating native modules for iOS and Android from Kotlin common code and supplying you with utilities to expose Kotlin Flows directly to React Native.

## Installation

**Prerequisite:** Your project must be a Kotlin Multiplatform Mobile project, see [our guide](docs/project-setup.md) on how to setup Kotlin Multiplatform Mobile in your existing React Native project.
The toolkit was tested with React Native 0.69 but may work with other versions as well.

Add the KSP gradle plugin to your multiplatform project's `build.gradle.kts` file, if you have subprojects, add it to the subject project's `build.gradle.kts` file.

```kotlin
// android/shared/build.gradle.kts

plugins {
    // from gradlePluginPortal()
    id("com.google.devtools.ksp") version "1.8.0-1.0.8"
}
```

Then add the `reakt-native-toolkit` to the `commonMain` source set dependencies.

```kotlin
// android/shared/build.gradle.kts

val commonMain by getting {
    dependencies {
        // ...
        implementation("de.voize:reakt-native-toolkit:<version>")
    }
}
```

Then add `reakt-native-toolkit-ksp` to the KSP configurations:

```kotlin
// android/shared/build.gradle.kts

dependencies {
    add("kspCommonMainMetadata", "de.voize:reakt-native-toolkit-ksp:<version>")
    add("kspAndroid", "de.voize:reakt-native-toolkit-ksp:<version>")
    add("kspIosX64", "de.voize:reakt-native-toolkit-ksp:<version>")
    add("kspIosArm64", "de.voize:reakt-native-toolkit-ksp:<version>")
    // (if needed) add("kspIosSimulatorArm64", "de.voize:reakt-native-toolkit-ksp:<version>")
}
```

And configure the ksp task dependencies and copy the generated typescript files to your react native project:

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    } else {
        finalizedBy("copyGeneratedTypescriptFiles")
    }
}

tasks.register<Copy>("copyGeneratedTypescriptFiles") {
    dependsOn("kspCommonMainKotlinMetadata")
    from("build/generated/ksp/metadata/commonMain/resources")
    into("../../src/generated")
}
```

To use the JS utilities install them with:

```bash
yarn add reakt-native-toolkit
```

## Usage

### Native Module generation

To generate a native module, annotate a Kotlin class in the `commonMain` source set with `@ReactNativeModule` and add the `@ReactNativeMethod` annotation to the methods you want to expose to React Native.

```kotlin
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule

@ReactNativeModule("Calculator")
class CalculatorRNModule {
    @ReactNativeMethod
    fun add(a: Int, b: Int): Int {
        return a + b
    }
}
```

The toolkit will generate `CalculatorRNModuleAndroid` and `CalculatorRNModuelIOS` for you.

You can now add `CalculatorRNModuleAndroid` to your native modules package in `androidMain`:

```kotlin
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import com.example.calculator.CalculatorRNModuleAndroid
import kotlinx.coroutines.CoroutineScope

class MyRNPackage(coroutineScope: CoroutineScope) : ReactPackage {
    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf<NativeModule>(
            CalculatorRNModuleAndroid(reactContext, coroutineScope),
            // ...
        )
    }
}
```

The `CalculatorRNModuleIOS` class will be compiled into your KMM projects shared framework and can be consumed in your iOS project in the `extraModules` of your `RCTBridgeDelegate`:

```swift
import shared // your KMM project's shared framework

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate {
  // ...

    func extraModules(for bridge: RCTBridge!) -> [RCTBridgeModule]! {
        return [CalculatorRNModuleIOS(...)]
    }
}
```

To do dependency injection or to supply the `coroutineScope` property you can wrap your `RNModuleIOS` classes in Kotlin in the `iosMain` source set and call constructors there:

```kotlin
import com.example.calculator.CalculatorRNModuleIOS
import kotlinx.coroutines.CoroutineScope
import react_native.RCTBridgeModuleProtocol

class MyIOSRNModules {
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return listOf(
            CalculatorRNModuleIOS(coroutineScope),
            // ...
        )
    }
}
```

And use this wrapper in Swift:

```swift
import shared // your KMM project's shared framework

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate {
    // ...

    func extraModules(for bridge: RCTBridge!) -> [RCTBridgeModule]! {
        return MyIOSRNModules().createNativeModules()
    }
}

```

#### Module Providers

For each `@ReactNativeModule` annotated class, the toolkit will generate a `ReactNativeModuleProvider` which can be used from common code to help with dependency injection and reduce code duplication.
The provider has the same constructor as the class annotated with `@ReactNativeModule`.
This allows you to create a list of all your native modules in common code and in the platform specific code you can get the actual native module instances from the provider via `getModule`.

The `getModule` function takes different parameters depending on the platform.
For android you need to pass in the `ReactApplicationContext` and the `CoroutineScope` and for iOS you need to pass in the `CoroutineScope`.

There are also helper `getModules` to convert a `List<ReactNativeModuleProvider>` to a list of native modules for each platform.
`ReactNativeModuleProvider` is very useful for large projects with many native modules.

### Expose Kotlin flows to React Native

The toolkit allows you to directly expose Kotlin Flows to React Native and supplies you with utilities to interact with them.

A flow exposed to React Native could look like this:

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import de.voize.reaktnativetoolkit.annotation.ReactNativeFlow
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule

@ReactNativeModule("Counter")
class CounterRNModule {
    private val counter = MutableStateFlow(0)

    @ReactNativeFlow
    suspend fun count(): Flow<Int> = counter

    @ReactNativeMethod
    suspend fun increment() {
        counter.update { it + 1 }
    }
}
```

A flow is exposed via a `@ReactNativeFlow` annotated function.

On the JS side we interact with this flow using the `useFlow` hook:

```typescript
import { useFlow, Next } from "reakt-native-toolkit";
import { NativeModules } from "react-native";
import { Counter } from "./modules";

function useCounter() {
  const count = useFlow(Counter.count);

  return {
    count,
    increment: Counter.increment,
  };
}
```

With `useFlow(Counter.count)` we can "subscribe" to the flow value. The hook will trigger a rerender whenever the flow value changes.

The `count` method of the `Counter` native module is typed as `Next<T>` which is a type alias for `(currentValue: string | null) => Promise<string>`.
Flow values are JSON serialized and deserialized when they are sent to and from the native module.
Although internally `Next<T>` is only operating on `string` the `useFlow` hook type is able to restore the type `T` in `Next<T>`.

#### How do `ReactNativeFlow` and `useFlow` work?

The `@ReactNativeFlow` annotation generates a native module method with an additional `previous` argument.
The generated code calls `toReact(previous)` on the returned flow and returns the result of the `toReact` call.
The extension function `Flow<T>.toReact(previous)` will JSON serialize the value of the flow and suspend until the value is different from the `previous` ([see in source](https://github.com/voize-gmbh/reakt-native-toolkit/blob/main/kotlin/reakt-native-toolkit/src/commonMain/kotlin/de/voize/reaktnativetoolkit/util/flowToReact.kt#L51)).
This is why `previous` is a string.

`useFlow` initiates an interaction loop with this suspension: It initially calls the native module method with `null` as the `previous` value and suspends until the native module responds with a new value. It then calls the native module again with the new value and suspends again until the native module responds with a new value. This loop continues until the component is unmounted.

### Using event emitter

With the toolkit you can also use event emitter in your common class module and the platform-specific event emitter setup is generated for you.

To use events in your module you only need to add a property of type `EventEmitter` to your constructor and specify the event names you want to use in the `@ReactNativeModule` annotation `supportedEvents` argument.
Then you can emit events by calling `sendEvent` on your `EventEmitter`.

```kotlin
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule
import de.voize.reaktnativetoolkit.util.EventEmitter

@ReactNativeModule("Notifications", supportedEvents = ["notify"])
class Notifications(
    private val eventEmitter: EventEmitter,
    private val someNotificationService: NotificationService,
) {
    init {
        someDependency.onNotification { message ->
            eventEmitter.sendEvent(
                "notify",
                mapOf("message" to message)
            )
        }
    }
}
```

The second argument of `sendEvent` can be null, a primitive type, a list or a map.

To consume the events in JS, use `new NativeEventEmitter` and `addListener`.

```typescript
import { NativeEventEmitter, NativeModules } from "react-native";

const Notifications = NativeModules.Notifications;

const eventEmitter = new NativeEventEmitter(Notifications);

const subscription = eventEmitter.addListener("notify", (event) => {
  console.log(event.message);
});

// ...
subscription.remove();
```

Because the toolkit generates native modules that are compatible with the standard React Native `NativeEventEmitter`, this is code is not specific to the toolkit, it is the same as in the React Native documentation (see [here](https://reactnative.dev/docs/native-modules-ios#sending-events-to-javascript) and [here](https://reactnative.dev/docs/native-modules-android#sending-events-to-javascript)).

You can also check in the react native module if a listener is registered in JS with `EventEmitter.hasListeners`.
`hasListeners` is a kotlin `Flow` which allows you to react to changes in the listener count (start publishing when the first listener is registered and stop publishing when the last listener is removed).

```kotlin
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule
import de.voize.reaktnativetoolkit.util.EventEmitter
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.CoroutineScope

@ReactNativeModule("Notifications", supportedEvents = ["notify"])
class Notifications(
    private val eventEmitter: EventEmitter,
    private val someNotificationService: NotificationService,
    private val messages: Flow<Strings>,
    private val lifecycleScope: CoroutineScope,
) {
    init {
        eventEmitter.hasListeners.transformLatest<Boolean, Unit> { hasListeners ->
            if (hasListeners) {
                // do the publishing in this coroutine scope, whihc is cancelled when the last listener is removed
                messages.collect { message ->
                    eventEmitter.sendEvent(
                        "notify",
                        mapOf("message" to message)
                    )
                }
            }
            // don't forget to handle exceptions, else it will cancel lifecycleScope and everything it contains
        }.launchIn(lifecycleScope)
    }
}
```
