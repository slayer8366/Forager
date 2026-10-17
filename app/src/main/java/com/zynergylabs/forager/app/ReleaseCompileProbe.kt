package com.zynergylabs.forager.app

// SCRATCH BRANCH ONLY -- positive control for dispatch 2026-10-17-04. A src/main reference to a class that only
// the debug variant has on its classpath (ui-tooling is debugImplementation in app/build.gradle.kts). Debug
// compiles; release must fail at compileReleaseKotlin. Never merged.
internal val releaseCompileProbe: Class<*> = androidx.compose.ui.tooling.PreviewActivity::class.java
