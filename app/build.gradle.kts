import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // KSP (for Room's compiler) is incompatible with AGP's built-in Kotlin — see the note next to
    // android.builtInKotlin in gradle.properties — so this project now needs the standalone
    // Kotlin Android plugin explicitly rather than relying on that built-in support.
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * What this build calls itself: [code] is what Android compares at install time to decide whether
 * an APK is newer than the installed one, [name] is what a human reads in the drawer footer.
 * [provisionalReason] is non-null when the identity could not be derived and is a placeholder.
 */
class BuildIdentity(val code: Int, val name: String, val provisionalReason: String?)

/**
 * Runs a git command in the project tree. Returns its trimmed stdout, or null if git is missing or
 * exits non-zero — the caller has to decide what an unanswerable question means, rather than
 * getting a plausible-looking empty string back.
 */
fun gitStdout(vararg args: String): String? {
    val output = providers.exec {
        commandLine(listOf("git") + args)
        isIgnoreExitValue = true
    }
    // A missing git binary surfaces as a thrown exception rather than a non-zero exit, so both
    // failure shapes have to be caught here. The reason is carried out to the caller, not dropped.
    return runCatching {
        if (output.result.get().exitValue != 0) null else output.standardOutput.asText.get().trim()
    }.getOrNull()
}

/**
 * The versionCode a build gets when it cannot derive a real one. AGP rejects 0 outright
 * ("versionCode is set to 0, but it should be a positive integer"), so 1 — the lowest value it
 * accepts — is the marker: a provisional build can never outrank a git-derived one, and any real
 * build will always install over it. The number alone is deliberately not the signal, since 1 is
 * exactly the value that caused this bug; the "UNVERSIONED-" versionName is, and it is what shows
 * in the drawer footer and in `aapt2 dump badging`. Failing the build outright was rejected: an
 * export with no git history should still be buildable, just unmistakably marked.
 */
val PROVISIONAL_VERSION_CODE = 1

/**
 * Derives this build's identity from git.
 *
 * versionCode is the commit count. It is monotonic along a branch's history and reproducible from
 * any checkout of a given commit, so an APK built from a later commit always outranks one built
 * from an earlier commit and Android actually replaces it. The rejected alternative was a
 * hand-bumped literal: that is what produced two different debug APKs both claiming
 * `versionCode=1`, which Android treats as a no-op install — the tester kept running the old app
 * and a full test cycle was lost. Commit count is not globally unique (two branches of equal depth
 * collide), which is why versionName also carries the commit sha; the drawer footer shows both.
 *
 * A checkout that cannot answer the question says so instead of guessing, via
 * [PROVISIONAL_VERSION_CODE], an obviously-provisional versionName, and a build-time warning.
 * Two cases qualify, and the second is the dangerous one: a tarball export has no git metadata at
 * all and fails loudly on its own, but a shallow clone answers `rev-list --count` with a
 * perfectly plausible small number that has nothing to do with the real history. Trusting that
 * would recreate the exact failure this is fixing, so shallowness is checked explicitly.
 */
fun resolveBuildIdentity(): BuildIdentity {
    val commitCount = gitStdout("rev-list", "--count", "HEAD")
    val shortSha = gitStdout("rev-parse", "--short=8", "HEAD")
    val status = gitStdout("status", "--porcelain")
    val isShallow = gitStdout("rev-parse", "--is-shallow-repository")
    if (commitCount == null || shortSha == null || status == null || isShallow == null) {
        return BuildIdentity(
            code = PROVISIONAL_VERSION_CODE,
            name = "UNVERSIONED-no-git-metadata",
            provisionalReason = "git metadata is unavailable in this checkout, so the build " +
                "cannot identify itself; installing this APK over another one may silently no-op",
        )
    }
    if (isShallow != "false") {
        return BuildIdentity(
            code = PROVISIONAL_VERSION_CODE,
            name = "UNVERSIONED-shallow-clone-g$shortSha",
            provisionalReason = "this is a shallow clone, so its commit count ($commitCount) " +
                "counts only the commits that were fetched and does not order against builds " +
                "made from a full clone",
        )
    }
    val count = commitCount.toIntOrNull()
        ?: return BuildIdentity(
            code = PROVISIONAL_VERSION_CODE,
            name = "UNVERSIONED-bad-commit-count",
            provisionalReason = "git rev-list --count returned \"$commitCount\", which is not a " +
                "number, so no versionCode could be derived from it",
        )
    // Uncommitted edits share the committed commit's versionCode, so the suffix is the only thing
    // that distinguishes them. It is in versionName, and therefore in the footer, on purpose.
    val dirtySuffix = if (status.isEmpty()) "" else ".dirty"
    return BuildIdentity(
        code = count,
        name = "1.0.$count+g$shortSha$dirtySuffix",
        provisionalReason = null,
    )
}

val buildIdentity = resolveBuildIdentity()
buildIdentity.provisionalReason?.let { reason ->
    logger.warn("WARNING: provisional build identity — $reason.")
    logger.warn(
        "WARNING: this APK reports versionCode=${buildIdentity.code} " +
            "versionName=${buildIdentity.name}.",
    )
}

android {
    namespace = "com.forager.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.forager.app"
        minSdk = 26
        targetSdk = 37
        versionCode = buildIdentity.code
        versionName = buildIdentity.name
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric needs the variant's merged manifest, compiled resources and asset
            // directory to stand up a real Android runtime in the JVM. Without this the resource
            // table is absent and anything that resolves a theme, dimension or string — which a
            // Material3 Compose tree does immediately — fails at construction.
            isIncludeAndroidResources = true
        }
    }
}

// The classic `kotlin-android` plugin (needed for Room's KSP compiler — see gradle.properties)
// defaults Kotlin's own JVM target to the Gradle daemon's JDK (21 here) rather than reading
// android.compileOptions above, which is Java-only. Left inconsistent, compileDebugKotlin and
// compileDebugJavaWithJavac disagree and AGP fails the build rather than silently picking one.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room's schema-export directory for its KSP annotation processor. Needed as of the mushroom log's
// migration (see ForagerDatabase's doc comment on exportSchema flipping to true) so a future
// migration has this version's schema history to migrate from — exportSchema = true alone only
// warns that this location is missing, it doesn't provide one.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    // osmdroid was the renderer through the maplibre-migration.md migration; MapSlot/SightingsMap
    // now host a real MapLibre MapView for every production screen, so the osmdroid dependency
    // itself is gone — see that plan doc and git history for the swap.
    implementation(libs.maplibre.android.sdk)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.nga.mgrs)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // MushroomLogMigrationTest declares its own test-only @Database (LegacyForagerDatabaseV3,
    // reusing production entity classes) to build a real version-3 database to migrate from —
    // Room's KSP compiler has to run over test sources too, or that class has no generated
    // implementation and the test fails with a ClassNotFoundException.
    kspTest(libs.androidx.room.compiler)

    // Headless layout measurement of the Compose tree, on the JVM. Both are testImplementation:
    // nothing here may reach the APK, and `verifyNothingTestOnlyReachesTheApk` below checks the
    // built artifact rather than trusting the configuration name.
    //
    // Deliberately absent: `androidx.compose.ui:ui-test-manifest`, the usual companion of
    // ui-test-junit4. It is an AAR whose entire payload is an `<activity>` entry for
    // ComponentActivity, and it is conventionally added as `debugImplementation` — which really
    // does put that entry in the debug APK handed to a tester. Adding it as `testImplementation`
    // instead was tried and does nothing: AGP merges it into
    // `packaged_manifests/debugUnitTest/`, but Robolectric reads the manifest packaged inside
    // `apk_for_local_test`, which is built from the *main* variant manifest and carries only
    // MainActivity and PreviewActivity (verified by dumping both). The host activity is therefore
    // registered at runtime by the tests themselves; see AvailabilityScreenLayoutTest.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.robolectric)
}

/**
 * Fails the build if a test-only dependency ends up in the shipped APK.
 *
 * The test dependencies above are the first in this project that could plausibly leak — Compose
 * UI Test pulls in `androidx.test.*` and `ui-test-manifest` contributes a manifest entry — and
 * "it's `testImplementation`, so it can't reach the APK" is exactly the kind of assumption
 * CLAUDE.md says to verify rather than assert. This reads the APK's own entry list and the merged
 * manifest that goes into it, so it checks the artifact rather than the build script's intent.
 *
 * Wired into `assembleDebug` (see below), so it runs as part of the normal build rather than
 * being a check somebody has to remember to invoke.
 */
val testOnlyPackagePrefixes = listOf(
    "androidx/test/",
    "androidx/compose/ui/test/",
    "org/robolectric/",
    "org/junit/",
    "junit/framework/",
)

tasks.register("verifyNothingTestOnlyReachesTheApk") {
    val apkDir = layout.buildDirectory.dir("outputs/apk/debug")
    inputs.dir(apkDir)
    doLast {
        val apk = apkDir.get().asFile.listFiles().orEmpty().firstOrNull { it.name.endsWith(".apk") }
            ?: error("No debug APK found in ${apkDir.get().asFile}; nothing was verified.")

        val leaked = mutableListOf<String>()
        // The classes are dexed, so class names are not zip entry names. Scanning the dex bytes
        // for the package prefix as an ASCII string is what actually answers the question: a
        // leaked class's descriptor is present verbatim in the dex string table.
        val dexNeedles = testOnlyPackagePrefixes.map { "L$it".toByteArray(Charsets.UTF_8) }
        // ui-test-manifest's only payload is a manifest entry, so a class scan would miss it. The
        // packaged AndroidManifest.xml is binary AXML, but every attribute value it carries is a
        // literal string in its string pool, in UTF-8 or UTF-16LE depending on the pool's encoding
        // flag — so both encodings are searched rather than guessing which aapt2 produced.
        val manifestNeedles = listOf(Charsets.UTF_8, Charsets.UTF_16LE)
            .map { "androidx.activity.ComponentActivity".toByteArray(it) }

        val zip = ZipFile(apk)
        try {
            for (entry in zip.entries()) {
                if (entry.name == "AndroidManifest.xml") {
                    val bytes = zip.getInputStream(entry).readBytes()
                    if (manifestNeedles.any { indexOfBytes(bytes, it) >= 0 }) {
                        leaked += "androidx.activity.ComponentActivity declared in the packaged " +
                            "manifest (ui-test-manifest leaked into the APK)"
                    }
                } else if (entry.name.endsWith(".dex")) {
                    val bytes = zip.getInputStream(entry).readBytes()
                    dexNeedles.forEachIndexed { index, needle ->
                        if (indexOfBytes(bytes, needle) >= 0) {
                            leaked += "${testOnlyPackagePrefixes[index]} (in ${entry.name})"
                        }
                    }
                } else {
                    testOnlyPackagePrefixes.forEach { prefix ->
                        if (entry.name.startsWith(prefix)) leaked += "${entry.name} (packaged file)"
                    }
                }
            }
        } finally {
            zip.close()
        }

        if (leaked.isNotEmpty()) {
            error(
                "Test-only code reached ${apk.name}:\n" + leaked.distinct().joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("Verified: no test-only class or manifest entry in ${apk.name}.")
    }
}

/** Naive byte-sequence search; the dex files here are a few MB, so this is fast enough. */
fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
    outer@ for (i in 0..haystack.size - needle.size) {
        for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

// `tasks.named` would resolve at configuration time, before AGP has created the variant tasks;
// matching defers until the task exists.
tasks.matching { it.name == "assembleDebug" }
    .configureEach { finalizedBy("verifyNothingTestOnlyReachesTheApk") }

