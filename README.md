# reakt-native-toolkit

[![npm package](https://badge.fury.io/js/reakt-native-toolkit.svg)](https://www.npmjs.com/package/reakt-native-toolkit)

This toolkit allows you to combine React Native with Kotlin Multiplatform Mobile (KMM) by generating native modules for iOS and Android from Kotlin common code and supplying you with utilities to expose Kotlin Flows directly to React Native.

## Installation

**Prerequisite:** Your project must be a Kotlin Multiplatform Mobile project

Add the KSP gradle plugin to your multiplatform project's `build.gradle.kts` file, if you have subprojects, add it to the subject project's `build.gradle.kts` file.

```kotlin
plugins {
    // from gradlePluginPortal()
    id("com.google.devtools.ksp") version "1.7.20-1.0.6"
}
```

Then add the `reakt-native-toolkit` to the `commonMain` source set dependencies.

```kotlin
implementation("de.voize:reakt-native-toolkit:<version>")
```

Then add `reakt-native-toolkit-ksp` to the KSP configurations:

```kotlin
dependencies {
    add("kspAndroid", "de.voize:reakt-native-toolkit-ksp:<version>")
    add("kspIosX64", "de.voize:reakt-native-toolkit-ksp:<version>")
    add("kspIosArm64", "de.voize:reakt-native-toolkit-ksp:<version>")
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
    fun add(a: Int, b: Int): Int {s
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
            ...
        )
    }
}
```
