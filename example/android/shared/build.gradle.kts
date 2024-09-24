plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose") version "1.6.10"
    id("com.google.devtools.ksp") version "1.9.23-1.0.20"
}

val reaktNativeToolkitVersion = "0.18.0"

kotlin {
    androidTarget {
        publishLibraryVariants("debug", "release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    cocoapods {
        name = "MyAppShared"
        version = "0.1.0"

        framework {
            homepage = "google.com"
            summary = "Shared Kotlin code for myapp"
            baseName = "shared"

            // without isStatic set to true you will run into linking issues
            // when using native compose views via RCTViewManager
            isStatic = true
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
        }

        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                implementation(compose.ui)
                implementation(compose.material)

                implementation("de.voize:reakt-native-toolkit:$reaktNativeToolkitVersion") {
                    exclude("com.facebook.react", "react-native")
                }
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        androidMain {
            //kotlin.srcDir("build/generated/ksp/android/androidDebug/kotlin")
            //kotlin.srcDir("build/generated/ksp/android/androidRelease/kotlin")

            dependencies {
                implementation("com.facebook.react:react-native:+")
            }
        }

        val iosX64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosX64/iosX64Main/kotlin")
        }
        val iosArm64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosArm64/iosArm64Main/kotlin")
        }
        val iosSimulatorArm64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosSimulatorArm64/iosSimulatorArm64Main/kotlin")
        }
    }
}


dependencies {
    add("kspCommonMainMetadata", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspAndroid", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosX64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosArm64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosSimulatorArm64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
}

tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>>().configureEach {
    if(name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

tasks.register<Copy>("copyGeneratedTypescriptFiles") {
    dependsOn("kspCommonMainKotlinMetadata")
    from("build/generated/ksp/metadata/commonMain/resources/reaktNativeToolkit/typescript")
    into("../../src/generated")
}

android {
    namespace = "com.myrnproject.shared"
    compileSdk = 32
    defaultConfig {
        minSdk = 26
    }
}
repositories {
    mavenCentral()
}
