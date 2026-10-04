import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Its own applicationId, so the Forager install and its data on the S22 are untouched (dispatch -490).
    namespace = "com.zynergylabs.labelcheck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zynergylabs.labelcheck"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "spike-490"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Exactly Forager's pin (gradle/libs.versions.toml: maplibre = "13.5.0").
    implementation("org.maplibre.gl:android-sdk:13.5.0")
}
