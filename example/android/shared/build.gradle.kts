plugins {
    kotlin("multiplatform")
    kotlin("native.cocoapods")
    id("com.android.library")
    id("com.google.devtools.ksp") version "1.8.0-1.0.8"
}

val reaktNativeToolkitVersion = "0.2.0"

kotlin {
    android()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
        }
    }

    cocoapods {
        name = "MyAppShared"
        version = "0.1.0"

        framework {
            homepage = "google.com"
            summary = "Shared Kotlin code for myapp"
            baseName = "shared"
        }
    }

    sourceSets {

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

                implementation("de.voize:reakt-native-toolkit:$reaktNativeToolkitVersion") {
                    exclude("com.facebook.react", "react-native")
                }

            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            //kotlin.srcDir("build/generated/ksp/android/androidDebug/kotlin")
            // kotlin.srcDir("build/generated/ksp/android/androidRelease/kotlin")

            dependencies {
                implementation("com.facebook.react:react-native:+")
            }
        }
        val androidTest by getting


        val iosX64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosX64/iosX64Main/kotlin")
        }
        val iosArm64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosArm64/iosArm64Main/kotlin")
        }
        val iosSimulatorArm64Main by getting {
            kotlin.srcDir("build/generated/ksp/iosSimulatorArm64/iosSimulatorArm64Main/kotlin")
        }

        val iosMain by creating {
            dependsOn(commonMain)

            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }
        val iosX64Test by getting
        val iosArm64Test by getting
        val iosSimulatorArm64Test by getting
        val iosTest by creating {
            dependsOn(commonTest)
            iosX64Test.dependsOn(this)
            iosArm64Test.dependsOn(this)
            iosSimulatorArm64Test.dependsOn(this)
        }
    }
}

dependencies {
    add("kspAndroid", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosX64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosArm64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
    add("kspIosSimulatorArm64", "de.voize:reakt-native-toolkit-ksp:$reaktNativeToolkitVersion")
}

android {
    namespace = "com.myrnproject.shared"
    compileSdk = 32
    defaultConfig {
        minSdk = 26
        targetSdk = 32
    }
}
repositories {
    mavenCentral()
}
