// Dispatch 2026-09-28-446: a separate spike project, built with the repository's own wrapper
// (`../../gradlew -p spikes/engine-spike`). Not part of the Forager build, and never merged into it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "engine-spike"
include(":app", ":brouter-lib")
