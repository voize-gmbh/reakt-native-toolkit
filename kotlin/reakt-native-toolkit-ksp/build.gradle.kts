plugins {
    kotlin("jvm")
    kotlin("kapt")
}

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of("11"))
    }
}

java {
    withSourcesJar()
}

dependencies {
    implementation(libs.google.auto.service)
    kapt(libs.google.auto.service)
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.metadata)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.typescriptpoet)
    implementation(libs.kotlinx.serialization.json)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

kapt {
    correctErrorTypes = true
    includeCompileClasspath = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("reakt-native-toolkit-ksp")
                description.set("Annotation processor for reakt-native-toolkit")
            }
        }
    }
}
