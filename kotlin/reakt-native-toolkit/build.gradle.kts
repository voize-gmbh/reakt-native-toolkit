import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

val useLegacyRNDependency = findProperty("reaktNativeToolkit.useLegacyRNDependency") as String? == "true"

android {
    namespace = "de.voize.reaktnativetoolkit"
    compileSdk = 33

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
}

if (useLegacyRNDependency) {
    repositories {
        maven {
            url = uri("$rootDir/../js/node_modules/react-native/android")
        }
    }
}

kotlin {
    jvmToolchain(17)

    jvm()
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        publishLibraryVariants("release")
    }

    fun KotlinNativeTarget.configureReactNativeInterop() {
        val main by compilations.getting {
            val reactNative by cinterops.creating {
                includeDirs("src/nativeInterop/cinterop/")
                packageName("react_native")
            }
        }
    }

    iosX64 {
        configureReactNativeInterop()
    }
    iosArm64 {
        configureReactNativeInterop()
    }
    iosSimulatorArm64 {
        configureReactNativeInterop()
    }
    wasmJs {
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidMain {
            dependencies {
                val reactNativeDependency = if (useLegacyRNDependency) {
                    "com.facebook.react:react-native:[0.69.0,)"
                } else {
                    "com.facebook.react:react-android:[0.74.0,)"
                }
                implementation(reactNativeDependency)
            }
        }

        all {
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }
    }
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf("-Xexpect-actual-classes")
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("reakt-native-toolkit")
            description.set("Toolkit for combining Kotlin Multiplatform and React Native")
        }
    }
}
