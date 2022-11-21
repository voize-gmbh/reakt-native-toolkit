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

repositories {
    mavenCentral()
}

subprojects {
    apply(plugin = "maven-publish")

    group = "de.voize"

    repositories {
        mavenCentral()
        google()
    }
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
