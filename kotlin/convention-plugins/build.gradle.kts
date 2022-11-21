plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.6.21")
    implementation("io.github.gradle-nexus:publish-plugin:1.1.0")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}
