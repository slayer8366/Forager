// Versions pinned to the Forager app's own (gradle/libs.versions.toml at f4e6726a), so the spike
// adds nothing to the Gradle cache and runs the exact MapLibre Forager ships.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
}

// Build output on the owner's USB drive: pass -PspikeBuildRoot=<dir>.
val spikeBuildRoot = providers.gradleProperty("spikeBuildRoot").orNull
if (spikeBuildRoot != null) {
    allprojects {
        layout.buildDirectory.set(file("$spikeBuildRoot/${project.name}"))
    }
}
