# CHANGELOG

## unreleased

- Remove react-native as transitive dependency of the toolkit, consumers need to make sure to include react-native dependency in their projects

## v0.21.11

- Support kotlin.time.Instant and kotlinx-datetime 0.7.1

## v0.21.10

- Make sure value classes are serialized in ViewManagers

## v0.21.9

- Support value classes in composable view manager

## v0.21.8

- Update Nexus repository URLs for publishing

## v0.21.7

- Add support for compose multiplatform 1.8.x

## v0.21.6

- Add warning log for deferred processing of invalid React Native symbols

## v0.21.5

- Defer symbol processing until all symbols are validated

## v0.21.4

- Handle invalid method and flow symbols correctly to better report errors

## v0.21.3

- Support KSP2

## v0.21.2

- Fix crash on iOS with nullable number types in Compose view manager params

## v0.21.1

- Fix compiler error for references to external value classes
- Add KDoc for provider classes

## v0.21.0

- Allow configuring namespace to prefix generated ObjC filenames and classes to avoid symbol conflicts
- Add support for value classes
- Add check that Map keys are supported
- Fix support for primitive typealias in Kotlin

## v0.20.1

- Revert "Improve linking error message for missing RN module"

## v0.20.0

- Set originating file on correct type for ios
- Support sealed interfaces like sealed classes
- Fix handling of properties without backing fields (getters and setters only)
- Improve linking error message for missing RN module

## v0.19.5

- Update generation for compose views so props are direct values instead of flows

## v0.19.4

- Fix generation for compose views with no props and no callbacks
- Make compose views transparent on iOS

## v0.19.3

- Update iOS view manager code generation to only require interfaces for view wrapper and view wrapper factory in Obj-C code

## v0.19.2

- Fix view manager boolean react prop is always true

## v0.19.1

- Add workaround for "Cannot locate windowRecomposer" again

## v0.19.0

- Update Kotlin to 2.0.21
- Update KSP to 2.0.21-1.0.25
- Update KotlinPoet to 1.18.1
- Return interface (RCTBridgeModuleProtocol, ReactNativeModuleBase) instead of narrow type in RN module provider getModule
- Fix Compose view managers for multiple instances
- Add originating KS files for generated view manager and providers

## v0.18.0

- Update example app iOS part to correct setup for RN 0.74.0
- Implement ReactNativeViewManager and ReactNativeProp annotation to render Compose Multiplatform components in React Native with generated type-safe React Native wrapper components
- Generate TypeScript files in commonMain resources into reaktNativeToolkit/typescript directory
- Release maven artifacts with -legacy version suffix for legacy RN Android dependency
- Do not generate RN modules typescript file if there are none

## v0.17.0

## v0.16.2

- Build toolkit from source in example project by default
- Increase gradle jvm heap space in example project
- Only try to generate code for native modules without compiler errors

## v0.16.1

- Update kotlin to 1.9.23 (fix https://youtrack.jetbrains.com/issue/KT-65542/Cinterop-tasks-fails-if-Xcode-15.3-is-used)
- Update react-native to 0.74.0 in example, and get example working
- Improve error handling of native module processing
- Update android gradle plugin to 8.2.1

## v0.16.0

- Allow to reference external modules in generated TypeScript code

## v0.15.0

- Update github npm-publish action to v3
- Update actions/setup-java@v3 to v4
- Update actions/checkout@v3 to v4
- Add jvm and wasmJs kotlin targets
- Update kotlin to 1.9.22
- Update kotlinx coroutines to 1.8.0
- Update kotlinx serialization to 1.6.3

## v0.14.0

- Add ExportTypescriptType annotation to export typescript types for kotlin types manually

## v0.13.1

- Fix release pipeline

## v0.13.0

- Update kotlinx coroutines to 1.7.3

## v0.12.0

- Update kotlin to 1.9.21
- Update react-native to 0.72.7 in example
- Update jvm version to 17 in ci

## v0.11.0

- Change typescript type mapping using computed property names syntax
- Fix mapping of kotlin objects to empty objects in typescript
- Correct mapping of standalone sealed subclasses in typescript, remove discriminator key

## v0.10.2

- Replace lodash uniqueId with uuid v4
- Do not call exceptionInterceptor for internal ReactNativeUseFlowSubscriptionCancellationException

## v0.10.1

- Resubscribe in useFlow after native timeout returning previous value
- Fix that flow subscription cancellation exception is not ignored on iOS
- Pass throwable name and all messages to JS error on iOS promise rejection

## v0.10.0

- Add missing project setup instruction to README
- Cancel subscriptions of useFlow on unmount and parameter change
- The useFlow hook a new parameter "unsubscribe" to cancel subscriptions
- Add subscriptionId to the Next<T> typealias
- Add unsubscribeFromToolkitUseFlow function to generated RN modules to cancel subscriptions
- Add UseFlowErrorInterceptor to customize error handling of useFlow
- Add "flowName" to useFlow hook to improve error messages

## v0.9.0

- Generate hooks for flows in RN modules
- Map types returned by flows in JS
- Update documentation to use generated hooks instead of useFlow

## v0.8.2

- Map keys of maps correctly in JS

## v0.8.1

- Fix trigger for publish artifact job

## v0.8.0

- Add mapping kotlin date types to js support

## v0.7.1

- Fix that complex ReactNativeFlow parameters are not serialized in JS

## v0.7.0

- Add TS generation support for custom sealed class discriminator keys
- Fix that external enum classes are not stubbed with any in TS generation

## v0.6.3

- Stub all external classes with any in TypeScript generation

## v0.6.2

- Handle unused parameter in ios wrapper
- Handle external class declarations

## v0.6.1

- Fix iOS generation for @ReactNativeFlow methods

## v0.6.0

- Add @ReactNativeFlow annotation to expose Flows to RN
- Auto-generate typescript native modules and interfaces of transferred data

## v0.5.0

- Update gradle wrapper to 7.5
- Update android gradle plugin to 7.4.1
- Generate common code for ReactNativeModuleProviders

## v0.4.0

- Add ReactNativeModuleProvider
- Update dependencies
- Update to kotlin to 1.8.21
- Enable Default hierarchy for kotlin source sets
- Remove jvm target use android target instead

## v0.3.1

- Use open-ended version range instead + for react-native Android dependency

## v0.3.0

- Implement EventEmitter for IOS
- Improve EventEmitter subscriber handling
- Add documentation for EventEmitter
- Update RN to 0.69.10
- Add iosSimulatorArm64 target

## v0.2.0
