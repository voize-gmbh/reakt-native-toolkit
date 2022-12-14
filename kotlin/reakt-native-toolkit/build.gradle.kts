plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
}

android {
    compileSdk = 31
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        minSdk = 26
        targetSdk = 30
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
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of("11"))
    }

    android {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        publishLibraryVariants("release")
    }

    jvm()

    ios {
        val main by compilations.getting {
            val reactNative by cinterops.creating {
                includeDirs("src/nativeInterop/cinterop/")
                packageName("react_native")
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("com.facebook.react:react-native:0.69.4") // from node_modules
            }
        }

        all {
            languageSettings.optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
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