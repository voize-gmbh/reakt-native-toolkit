# Setup your project to work with `reakt-native-toolkit`

This guide helps you to make your existing React Native project ready to use the `reakt-native-toolkit` library.

## Step 1: Add Kotlin Multiplatform to your project

To use the `reakt-native-toolkit` library your project needs to support [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html).

Adding KMP to your project is very straightforward. You create a new module in your `/android` project which is a so-called _KMP shared module_. Please refer to [Kotlin's official guide on how to do this](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-integrate-in-existing-app.html#create-a-shared-module-for-cross-platform-code). The shared module will contain cross-platform code written in Kotlin and we will be able to expose classes within this code to React Native as native modules.

The shared module should be in a separate namespace like `com.example.shared`. Going forward we assume that your shared module's directory is called `shared`.

Make sure that in your `settings.gradle` in the root directory the shared module is included. This should have been automatically added by the KMP wizard.

```groovy
// settings.gradle

// ...
include ':shared'
```

## Step 2: Configure the shared module

For your shared module to use `reakt-native-toolkit` we need to add it to its dependencies and setup KSP as described in the README. Make sure that you have completed the setup described there before continuing.

### Configure cocoapods for the shared module

Since RN projects come with an iOS project that uses Cocoapods we configure our shared module to provide the compiled cross-platform Kotlin code to iOS via a Cocoapod.

First add the `cocoapods` plugin to your shared module:

```kotlin
// android/shared/build.gradle.kts

plugins {
    // ...
    kotlin("native.cocoapods")
}
```

Configure the `cocoapods` plugin:

```kotlin
// android/shared/build.gradle.kts

kotlin {
    // ...
    cocoapods {
        name = "MyAppShared"
        version = "0.1.0"

        framework {
            homepage = "..."
            summary = "Shared Kotlin code for myapp"
            baseName = "shared"
        }
    }
}
```

This will generate a `MyAppShared.podspec` which we include in our iOS project:

```ruby
# ios/Podfile

target 'MyRNProject' do
    # ...
    pod 'MyAppShared', :path => '../android/shared/MyAppShared.podspec'
end
```

After that run `pod install` in the `ios` directory.

## Step 3: Include the shared module in your app module

To register the generated native modules e.g. in your app's `MainApplication` you need to include the shared module in your app module:

```gradle
// android/app/build.gradle

dependencies {
    implementation project(':shared')
}
```

## Step 4: Setup an Android RN package

If you do not have one already, create a new RN package in your app module. This package will be used to register the generated native modules.

```kotlin
// android/app/src/main/java/com/example/MyRNPackage.kt

import com.myrnproject.shared.MyFirstRNModuleAndroid

class MyRNPackage : ReactPackage {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        return listOf(
            MyFirstRNModuleAndroid(reactContext, coroutineScope)
        )
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<View, ReactShadowNode<*>>> {
        return emptyList()
    }
}
```

Add this package to `getPackages` in your `MainApplication`:

```kotlin
// android/app/src/main/java/com/example/MainApplication.kt

override fun getPackages(): List<ReactPackage> {
    val packages: MutableList<ReactPackage> = PackageList(this).packages
    // ...
    packages.add(MyRNPackage())
    return packages
}
```

## Step 5: Add the generated native modules to your iOS project

It is easier to wrap your generated IOS RN native modules in a Kotlin class in the `iosMain` source set of the shared module. This way to you can do dependency injection more easily and setup the coroutine scope directly from Kotlin.

```kotlin
// android/shared/src/iosMain/kotlin/com/example/shared/IOSRNModules.kt

import com.myrnproject.shared.MyFirstRNModuleIOS

class IOSRNModules {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun createNativeModules(): List<RCTBridgeModuleProtocol> {
        return listOf(
            MyFirstRNModuleIOS(coroutineScope),
        )
    }
}
```

Then you can use this class to register the native modules in your iOS project using `extraModulesForBridge`:

```objc
// ios/MyRNProject/AppDelegate.mm

#import <shared/shared.h>

@implementation AppDelegate

// ...

- (NSArray<id<RCTBridgeModule>> *)extraModulesForBridge:(RCTBridge *)bridge
{
    return [[[SharedIOSRNModules alloc] init] createNativeModules];
}
```
