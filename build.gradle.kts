// RECORD -741 (evidence test for RECORD -739): AGP 9.3.1 bundles D8/R8 9.3.16, which produced dex code
// for CompactMapTab that ART's verifier rejects on a phone (the app could not start). This puts R8 9.3.31,
// the newest 9.3 release, on the buildscript classpath ahead of the bundled one, the override AGP's docs
// describe, pinned to an exact version. Kept only if the build it produces verifies on the S22; see
// docs/audits/2026-10-08-launch-verifyerror-report.md.
buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools:r8:9.3.31")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
