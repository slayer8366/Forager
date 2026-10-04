// Versions pinned to the Forager app's own (gradle/libs.versions.toml at 7c072bfc), so the spike
// adds no plugin to the Gradle cache.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.10" apply false
}

// The spike's build output goes to the owner's USB drive, not the laptop (its free space is the
// constraint): pass -PspikeBuildRoot=<dir>; without it the build stays in the default place.
val spikeBuildRoot = providers.gradleProperty("spikeBuildRoot").orNull
if (spikeBuildRoot != null) {
    allprojects {
        layout.buildDirectory.set(file("$spikeBuildRoot/${project.name}"))
    }
}
