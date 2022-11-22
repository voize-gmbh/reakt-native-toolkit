// Use buildscript because all kotlin examples use it in combination with android
buildscript {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    // all plugins must be specified here, else the override the kotlin plugin version
    dependencies {
        classpath(libs.gradle.plugin.android)
        classpath(libs.gradle.plugin.kotlin)
        classpath(libs.gradle.plugin.kotlin.serialization)
    }
}

plugins {
    id("convention.publication")
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        mavenCentral()
        google()
    }
}