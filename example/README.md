# reakt-native-toolkit example project

This example project should demonstrate how to setup and use the reakt-native-toolkit and demo its features.

As a basis, the project has the standard React Native project structure that you would get when running the [official project initialization](https://reactnative.dev/docs/environment-setup). Then, the [`react-native-toolkit` setup guide](https://github.com/voize-gmbh/reakt-native-toolkit/blob/main/docs/project-setup.md) was followed to add the toolkit to the project.

By default, the project uses the local artifacts for `react-native-toolkit`.
If you want to run the project against a published version of the toolkit, you can set `useLocalToolkit=false` in `android/gradle.properties`.

### Running the project

First install the dependencies:

```bash
yarn install
```

Then, you can run the project on Android or iOS:

```bash
yarn android
```

```bash
yarn ios
```

### What's in the project

The project contains the `NameManager` native module that demonstrates how to expose Kotlin flows to JavaScript and how exceptions are converted into promise rejections.

The `NotificationsDemo` native module demonstrates how event handling with `EventEmitter` can be implemented with the toolkit.
