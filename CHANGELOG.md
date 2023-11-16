# CHANGELOG

## unreleased

- Update kotlin to 1.9.20
- Update react-native to 0.72.7 in example

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
