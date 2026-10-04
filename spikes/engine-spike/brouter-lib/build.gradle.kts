// BRouter's routing engine, in-process (dispatch -446, item 6). BRouter is not published to Maven
// Central and JitPack's build of v1.7.10 fails, so its five pure-Java modules are compiled from a
// checkout of tag v1.7.10 (commit 4d2639af) given as -PbrouterSrc=<dir>. Its own Gradle build is
// not included: its root build file pulls in a second Android Gradle plugin (9.0.1).
plugins {
    `java-library`
}

val brouterSrc = providers.gradleProperty("brouterSrc").orNull
    ?: error("Pass -PbrouterSrc=<a checkout of abrensch/brouter at v1.7.10>")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

sourceSets {
    main {
        java.setSrcDirs(
            listOf("brouter-util", "brouter-codec", "brouter-expressions", "brouter-mapaccess", "brouter-core")
                .map { "$brouterSrc/$it/src/main/java" },
        )
    }
}
