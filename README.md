# reakt-native-toolkit

[![NPM](https://img.shields.io/npm/v/reakt-native-toolkit?color=blue)](https://www.npmjs.com/package/reakt-native-toolkit)
[![Maven Central](https://img.shields.io/maven-central/v/de.voize/reakt-native-toolkit?color=blue)](https://search.maven.org/artifact/de.voize/reakt-native-toolkit)

This toolkit allows you to combine React Native with Kotlin Multiplatform (KMP) by generating native modules for iOS and Android from Kotlin common code and supplying you with utilities to expose Kotlin Flows directly to React Native.

## Installation

**Prerequisite:** Your project must be a Kotlin Multiplatform project, see [our guide](docs/project-setup.md) on how to setup Kotlin Multiplatform in your existing React Native project.
Starting with kotlin 1.9.0 your project must have android and ios targets configured.
The toolkit was tested with React Native 0.69 and 0.72 but may work with other versions as well.

Add the KSP gradle plugin to your multiplatform project's `build.gradle.kts` file, if you have subprojects, add it to the subject project's `build.gradle.kts` file.

```kotlin
// android/shared/build.gradle.kts

plugins {
    // from gradlePluginPortal()
    id("com.google.devtools.ksp") version "1.9.23-1.0.20"
}
```

Then add the `reakt-native-toolkit` to the `commonMain` source set dependencies.
Also add the generated common source set to the `commonMain` source set:

```kotlin
// android/shared/build.gradle.kts

val commonMain by getting {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    dependencies {
        // ...
        implementation("de.voize:reakt-native-toolkit:<version>")
    }
}
```

NOTE: This artifact has an implementation dependency on the Maven Central published `react-android` module.
It can be excluded if you are using an older `react-native` dependency e.g. `exclude("com.facebook.react", "react-android")`.

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
// android/shared/build.gradle.kts
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

Add copyGeneratedTypescriptFiles task as a dependency to your react native bundle task:

```kotlin
// android/app/build.gradle
// React Native 0.71.x and above with Hermes
tasks.withType(com.facebook.react.tasks.BundleHermesCTask).configureEach {
    dependsOn(":shared:copyGeneratedTypescriptFiles")
}

// React Native 0.70.x and below
tasks.named("bundleReleaseJsAndAssets") {
    dependsOn(":shared:copyGeneratedTypescriptFiles")
}
```

In the project that consumes the module, the generated TypeScript files require the JavaScript utilities, such as `useFlow`:

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

The toolkit will generate the following for you

- `CalculatorRNModuleAndroid.kt` for `androidMain`
- `CalculatorRNModuleIOS.kt` for `iosMain`
- `Calculator` and `CalculatorInterface` Typescript code

#### Register generated Android module

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

#### Register generated iOS module

The `CalculatorRNModuleIOS` class will be compiled into your KMP projects shared framework and can be consumed in your iOS project in the `extraModules` of your `RCTBridgeDelegate`:

```swift
import shared // your KMP project's shared framework

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

And use this wrapper in Objective-C in the `extraModules` function, similar to above except calling the `createNativeModules` function:

```swift
return [[[MyIOSRNModules alloc] init] createNativeModules];
```

Or in Swift:

```swift
import shared // your KMP project's shared framework

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate {
    // ...

    func extraModules(for bridge: RCTBridge!) -> [RCTBridgeModule]! {
        return MyIOSRNModules().createNativeModules()
    }
}
```

#### Use your native module in React Native

The toolkit will generate a Typescript object for you and you can just import it from the generation destination (usually `src/generated/modules`):

```typescript
import { Calculator } from "src/generated/modules";

const result = await Calculator.add(1, 2);
```

#### Exception handling

Exceptions thrown in your native module will be propagated to JS and converted to JS errors.
You can customize the JS errors and log exception in Kotlin by implementing an ExceptionInterceptor in Kotlin:

```kotlin
import de.voize.reaktnativetoolkit.util.exceptionInterceptor

fun setExceptionInterceptor() {
    exceptionInterceptor = { exception ->
        logger.error(exception) { "Error in native module" }
        mapOf(
            "NATIVE_STACK_TRACE" to it.stackTraceToString(),
        )
    }
}
```

The return value (Map<String,Any?>) of the interceptor will be added to the `userInfo` property of the JS error.

```typescript
console.log(error.userInfo.NATIVE_STACK_TRACE);
```

Be aware Boolean values are converted to 0 or 1 in JS on iOS.

### Streamline dependency injection using Module Providers

If you want your RN module to use some external functionality you would pass it in via the constructor:

```kotlin
import de.voize.reaktnativetoolkit.annotation.ReactNativeMethod
import de.voize.reaktnativetoolkit.annotation.ReactNativeModule

@ReactNativeModule("Calculator")
class CalculatorRNModule(analytics: Analytics) {
    @ReactNativeMethod
    fun add(a: Int, b: Int): Int {
        analytics.log("add", mapOf("a" to a, "b" to b))
        return a + b
    }
}
```

The dependency must then be injected when creating the native module instance.

#### Common

To avoid duplicating your dependency injection in Android and iOS you can use the toolkits _Module Providers_. For each `@ReactNativeModule` annotated class, the toolkit will generate a `ReactNativeModuleProvider` which can be used from common code.

The provider has the same constructor as the class annotated with `@ReactNativeModule`.

```kotlin
// commonMain

fun getRNModuleProviders(analytics: Analytics): List<ReactNativeModuleProvider> {
    return listOf(
        CalculatorRNModuleProvider(analytics),
        CalendarRNModuleProvider(analytics),
        PermissionRNModuleProvider(analytics),
        // ...
    )
}
```

This allows you to create a list of all your native modules in common code and in the platform specific code you can get the actual native module instances from the provider via `getModule`.

##### Android

```kotlin
// androidMain

class MyRNPackage(coroutineScope: CoroutineScope, analytics: Analytics) : ReactPackage {
    // ...

    override fun createNativeModules(
        reactContext: ReactApplicationContext
    ): List<NativeModule> {
        return getRNModuleProviders(analytics).getModules(reactContext, coroutineScope)
    }
}
```

##### iOS

```kotlin
// iosMain

class MyIOSRNModules(analytics: Analytics) {
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return getRNModuleProviders(analytics).getModules(coroutineScope)
    }
}
```

The `getModule` function takes different parameters depending on the platform.
For android you need to pass in the `ReactApplicationContext` and the `CoroutineScope` and for iOS you need to pass in the `CoroutineScope`.

There are also helper `getModules` to convert a `List<ReactNativeModuleProvider>` to a list of native modules for each platform.
`ReactNativeModuleProvider` is very useful for large projects with many native modules.

#### Platform specific dependency injection

#### Android

```kotlin
// androidMain

class MyRNPackage(coroutineScope: CoroutineScope, analytics: Analytics) : ReactPackage {
    // ...

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf<NativeModule>(
            CalculatorRNModuleAndroid(reactContext, coroutineScope, analytics),
            CalendarRNModuleAndroid(reactContext, coroutineScope, analytics),
            PermissionRNModuleAndroid(reactContext, coroutineScope, analytics),
            // ...
        )
    }
}
```

Register the package in `MainApplication` -- in the `getPackages` function of `ReactNativeHost`, instantiate the package, passing any required dependencies, and add it.
Example:

```kotlin
add(RNPackage(coroutineScope, analyics))
```

#### iOS

```kotlin
// iosMain

class MyIOSRNModules(analytics: Analytics) {
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return listOf(
            CalculatorRNModuleIOS(coroutineScope, analytics),
            CalendarRNModuleIOS(coroutineScope, analytics),
            PermissionRNModuleIOS(coroutineScope, analytics),
            // ...
        )
    }
}
```

### Expose Kotlin flows to React Native

The toolkit allows you to directly expose Kotlin Flows to React Native and supplies you with utilities to interact with them.
This is usefull for flows which represent a state.

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
import { Counter } from "./generated/modules";

function useCounter() {
  const count = Counter.useCount();

  return {
    count,
    increment: Counter.increment,
  };
}
```

NOTE: React Native 0.74+ generates code that requires `react-native-get-random-values` to be installed.
Call `import 'react-native-get-random-values';` as early as possible in the application initialization.
For Expo users, install the `expo-standard-web-crypto` package, which includes the necessary polyfill.
Initialize it as follows, again as early as possible:

```typescript
import { polyfillWebCrypto } from "expo-standard-web-crypto";

// useFlow from the devhaus mobile-sdk uses uuid which uses crypto.getRandomValues which is not available in expo,
// so polyfill it with the expo-standard-web-crypto module
// https://github.com/expo/expo/tree/main/packages/expo-standard-web-crypto
polyfillWebCrypto();
```

With `Counter.useCount()` we can "subscribe" to the flow value. The hook will trigger a rerender whenever the flow value changes.
For each subscription the native flow is consumed multiple times, a new subscription is created after each value change. Make sure your flow returns the same value ("state") for each new subscription and does not return initial values or replay values.

`useFlow` deserializes the returned values, but does not apply the custom mapping in JS, such as mapping strings to date types.

#### How do `ReactNativeFlow` and the generated hooks work?

The `@ReactNativeFlow` annotation generates a native module method with an additional `previous` argument.
The generated code calls `toReact(previous)` on the returned flow and returns the result of the `toReact` call.
The extension function `Flow<T>.toReact(previous)` will JSON serialize the value of the flow and suspend until the value is different from the `previous` ([see in source](https://github.com/voize-gmbh/reakt-native-toolkit/blob/main/kotlin/reakt-native-toolkit/src/commonMain/kotlin/de/voize/reaktnativetoolkit/util/flowToReact.kt#L51)).
This is why `previous` is a string.

In JS for each flow a property is added to the native module, with the Type `Next<T>` which is a type alias for `(subscriptionId: string, currentValue: string | null) => Promise<string>`.
Additionally a hook is generated for each flow with the name `use{FlowName}` which uses the `useFlow` util hook internally.
The hook manages the subscription to the flow and calls the native module method with the current value of the flow.
The `useFlow` hook initiates an interaction loop with this suspension: It initially calls the native module method with `null` as the `previous` value and suspends until the native module responds with a new value. It then calls the native module again with the new value and suspends again until the native module responds with a new value. This loop continues until the component is unmounted.
Flow values are JSON serialized and deserialized when they are sent to and from the native module.
The generated hook maps the deserialized value to the correct type and returns it.

### Mapping Kotlin types to JS types

The toolkit will automatically map Kotlin types to JS types and vice versa.
Primitive types like `Int`, `Double`, `Boolean` and `String` are mapped to their JS equivalents.
`List` and `Map` are mapped to their JS equivalents.
Complex types are mapped to JS objects with matching generated Typescript interfaces.
The mapping of date time types can be configured via the `@JSType` annotation or via ksp arguments.
By default all date time types are mapped to their string representation in js.
The default for `Instant` can be overridden with the ksp argument `reakt.native.toolkit.defaultInstantJsType`.
Either `string` or `date` can be used as the value for this argument or the `@JSType` annotation.

```kotlin
// build.gradle.kts
ksp {
    arg("reakt.native.toolkit.defaultInstantJsType", "string")
}
```

```kotlin
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import de.voize.reaktnativetoolkit.annotation.JSType

@Serializable
data class Test(
    val instant: Instant, // mapped to string in js
    val date: @JSType("date") Instant, // mapped to date in js
    val string: @JSType("string") Instant, // mapped to string in js
)
```

### Configuration for multiple projects

If you have multiple projects including libraries and applications, types cannot be resolved cross project boundaries by default.
For example, if you have a library project with a data class and a consuming application project, the consuming application project cannot resolve the typescript type of the data class from the library project.
By default `any` is used as the type for unresolved types.
To get better type safety follow the steps below.

#### Library project

All projects which expose types you want to export to typescript must be explicitly annotated with `@ExportTypescriptType` annotation.

```kotlin
import de.voize.reaktnativetoolkit.annotation.ExportTypescriptType
import kotlinx.serialization.Serializable

@ExportTypescriptType
@Serializable
data class Test(
    val value: Int
)
```

Also add the `reakt-native-toolkit-ksp` dependency to the library project.
This will generate the typescript types for the library project.
You need to copy the generated typescript files to the consuming application project.

#### Consuming application project

Configure the root namespace for each library project in the consuming application project.
This is needed to resolve the typescript types based on the namespace of the library project.
For each namespace configure the module name and if the typescript was generated by the toolkit.

```kotlin
// build.gradle.kts
ksp {
    arg("reakt.native.toolkit.externalNamespaces", "org.example.lib1")
    arg("reakt.native.toolkit.externalNamespaceModuleName.org.example.lib1", "./path/to/generated/models/lib1")
    arg("reakt.native.toolkit.externalNamespaceGeneratedByToolkit.org.example.lib", "true")
}
```

### Handling exceptions thrown by flows in JS

If a flow which is used in JS throws an exception, the exception is propagated from Kotlin to the `useFlow` hook. You can set an global error interceptor for useFlow with `setErrorInterceptor`:

```typescript
import { setErrorInterceptor } from "reakt-native-toolkit";

setErrorInterceptor(async (error, flowName) => {
  console.error(`Error in flow ${flowName}: ${error}`);
});
```

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

To consume the events in JS, use `addEventListener` on the module.

```typescript
import { Notifications } from "src/generated/modules";

const subscription = Notifications.addEventListener("notify", (event) => {
  console.log(event.message);
});

// ...
subscription.remove();
```

The toolkit generates native modules that are compatible with the standard React Native `NativeEventEmitter`, so alternatively the standard React Native approach may be used (see [here](https://reactnative.dev/docs/native-modules-ios#sending-events-to-javascript) and [here](https://reactnative.dev/docs/native-modules-android#sending-events-to-javascript)). Example:

```
import { NativeEventEmitter, NativeModules } from "react-native";

const Notifications = NativeModules.Notifications;
const eventEmitter = new NativeEventEmitter(Notifications);
const subscription = eventEmitter.addListener("notify", (event) => {
  console.log(event.message);
});
```

The react native module can check if a listener is registered in JS with `EventEmitter.hasListeners`.
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

### Render Compose Multiplatform components in React Native (experimental)

The toolkit allows you to render Compose Multiplatform components in React Native by annotating any Compose component function with the `@ReactNativeViewManager` annotation. The toolkit will then generate the necessary _view managers_ for [iOS](https://reactnative.dev/docs/native-components-ios) and [Android](https://reactnative.dev/docs/native-components-android) you which allows you to render the component in React Native.

#### Prerequisites

Make sure the Kotlin multiplatform module in your project includes the `org.jetbrains.compose` Gradle plugin with a version compatible with your Kotlin version.

```kotlin
// android/shared/build.gradle.kts
plugins {
    ...
    id("org.jetbrains.compose") version "..."
}
```

Make sure you added the Compose dependencies required for you to your `commonMain` source set.

```kotlin
// android/shared/build.gradle.kts

val commonMain by getting {
    dependencies {
        ...

        implementation(compose.ui)
        implementation(compose.material)
        // ... other compose dependencies
    }
}
```

#### Usage

To render a Compose component in React Native, annotate a Compose component function in the commonMain source set with `@ReactNativeViewManager` and specify the name of the view manager in the annotation.

```kotlin
// commonMain

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.voize.reaktnativetoolkit.annotation.ReactNativeViewManager

@Composable
@ReactNativeViewManager("MyComposeView")
internal fun MyComposeView() {
    Text("Hello from Compose!")
}
```

Now you can add the generated `MyComposeViewRNViewManagerAndroid` and `MyComposeViewRNViewManagerIOS` to your native modules package in `androidMain` and your `AppDelegate`. This is similar to adding a native module.

```kotlin
// androidMain

class MyRNPackage() : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        // ... native modules
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(
            MyComposeViewRNViewManagerAndroid(reactContext),
            // ... other view managers
        )
    }
}
```

```objc
import shared // your KMP project's shared framework

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, RCTBridgeDelegate {
  // ...

    func extraModules(for bridge: RCTBridge!) -> [RCTBridgeModule]! {
        return [
            // ... native modules
            [MyComposeViewRNViewManagerIOS init]
        ]
    }
}
```

Now you can use the `MyComposeView` component in your React Native code.

```typescript
// nativeViews.tsx

import { requireNativeComponent } from "react-native";

export const MyComposeView = requireNativeComponent<any>("MyComposeView");
```

```typescript
// App.tsx

import React from "react";

import { MyComposeView } from "./nativeViews";

export const App = () => {
  return <MyComposeView />;
};
```

It is adviced to put `requireNativeComponent` so it does not interfere with hot reloading, as it will throw if the function is called more than once.

**Attention:** Props from React Native to the Compose component are not supported yet.

#### Dependency injection

Just like with native modules, you can also use the generated platform `Provider`s to streamline dependency injection in common code.

```kotlin
// commonMain

@Composable
@ReactNativeViewManager("MyComposeView")
internal fun MyComposeView(analytics: Analytics) {
    Text("Hello from Compose!")
}
```

```kotlin
// commonMain

import de.voize.reaktnativetoolkit.util.ReactNativeViewManagerProvider

fun getReactNativeViewManagerProviders(analytics: Analytics): List<ReactNativeViewManagerProvider> {
    return listOf(
        MyComposeViewRNViewManagerProvider(analytics),
    )
}
```

```kotlin
// androidMain

import com.myrnproject.shared.getReactNativeViewManagerProviders
import de.voize.reaktnativetoolkit.util.getViewManagers

class MyRNPackage(private val analytics: Analytics) : ReactPackage {

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        // ... native modules
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return getReactNativeViewManagerProviders(
            analytics
        ).getViewManagers(reactContext)
    }
}
```

```kotlin
// iosMain

import de.voize.reaktnativetoolkit.util.getModules
import de.voize.reaktnativetoolkit.util.getViewManagers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import react_native.RCTBridgeModuleProtocol

class MyIOSRNModules(private val analytics: Analytics) {
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return getReactNativeModuleProviders(
            coroutineScope,
            analytics,
        ).getModules(coroutineScope) + getReactNativeViewManagerProviders(
            analytics,
        ).getViewManagers()
    }
}
```
