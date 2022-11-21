# reakt-native-toolkit

This toolkit allows you to combine React Native with Kotlin Multiplatform Mobile (KMM) by generating native modules for iOS and Android from Kotlin common code and supplying you with utilities to expose Kotlin Flows directly to React Native.

## Installation

**Prerequisite:** Your project must be a Kotlin Multiplatform Mobile project

Add the following to your `shared/build.gradle.kts` as a `commonMain` dependency:

```kotlin
implementation("de.voize:reakt-native-toolkit:<version>")
```
