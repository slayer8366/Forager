import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    // Its own applicationId, so the Forager install and its data on the S22 are untouched (dispatch -446).
    namespace = "com.zynergylabs.enginespike"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zynergylabs.enginespike"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "spike-446"
    }

    // Item 2: what each engine adds to an APK, per ABI.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
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
    // valhalla-mobile 0.6.3 bundles Valhalla 3.6.3 (its README badge); the tiles are built with
    // pyvalhalla 3.6.3 to match. Pinned exactly.
    implementation("io.github.rallista:valhalla-mobile:0.6.3")
    implementation(project(":brouter-lib"))
}
