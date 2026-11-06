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

rootProject.name = "Forager"
include(":app")

// BRouter v1.7.10's routing engine, vendored from source (dispatch 2026-10-23-02, trail navigation step 4).
// A plain Java library beside the app; third_party/brouter/README.md says where it came from.
include(":brouter")
project(":brouter").projectDir = file("third_party/brouter")
