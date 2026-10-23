// BRouter's routing engine, in-process (dispatch 2026-10-23-02; engine chosen at RECORD -484, from the spike in
// docs/navigation/2026-10-03-engine-spike-report.md). BRouter publishes no Maven artifact and JitPack's build of
// v1.7.10 fails (the spike), so the five pure-Java modules the router needs (brouter-util, -codec, -expressions,
// -mapaccess, -core) are vendored here, unmodified, from tag v1.7.10 (commit 4d2639af). They compile as one plain
// Java library with no dependencies beyond the JDK, as they did in the spike's brouter-lib. BRouter's own Gradle
// build is not used: its root build pulls in a second Android Gradle plugin.
//
// MIT licence: LICENSE beside this file, and credited in the app's open-source page (credits/software.json).
plugins {
    `java-library`
}

// BRouter's own build compiles with `options.release = 11` (buildSrc's java conventions at v1.7.10).
tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
    options.encoding = "UTF-8"
}
