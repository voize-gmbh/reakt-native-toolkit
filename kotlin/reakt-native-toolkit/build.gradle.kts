import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

android {
    namespace = "de.voize.reaktnativetoolkit"
    compileSdk = 33
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 26
        targetSdk = 33
    }

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_1_8)
        targetCompatibility(JavaVersion.VERSION_1_8)
    }
}

repositories {
    maven {
        url = uri("$rootDir/../js/node_modules/react-native/android")
    }
}

kotlin {
    jvmToolchain(11)

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
    sourceSets {
        commonMain.configure {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        androidMain.configure {
            dependencies {
                implementation("com.facebook.react:react-native:[0.69.0,)") // from node_modules
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
                freeCompilerArgs = listOf("-Xexpect-actual-classes")
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
