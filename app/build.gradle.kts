import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
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

/**
 * The beta-and-release signing identity — dedicated-beta-signing-identity dispatch (owner
 * decision): testers keep their data across the beta-to-release transition, so the beta build and
 * the release build carry one identity, and it is not the committed debug key.
 *
 * **Never a literal, never in the repository.** The four values come from environment variables
 * (`FORAGER_SIGNING_STORE_FILE`, `FORAGER_SIGNING_STORE_PASSWORD`, `FORAGER_SIGNING_KEY_ALIAS`,
 * `FORAGER_SIGNING_KEY_PASSWORD`), else from an untracked `signing.properties` at the repository
 * root (keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; a relative `storeFile` is
 * resolved against the root). `.gitignore` covers the properties file and every `*.jks` /
 * `*.keystore` except `app/debug.keystore`. The owner generates and holds the keystore; CI has no
 * copy and signs nothing but debug.
 *
 * **When nothing is configured** — every CI runner, every checkout without the secret — this is
 * `null`, the release build type gets **no** signing config, and `assembleRelease` /
 * `bundleRelease` fail at [verifyReleaseNeverSignsWithDebugKeystore] with a message that says
 * what to set. Debug builds and the test suite are untouched: the identity is only ever read, never
 * required, until a release artifact is asked for. There is deliberately no fallback to the debug
 * identity — that is the regression the guard exists to prevent — and no fallback to unsigned,
 * which the guard also refuses. **A half-configured identity fails at configuration time**, before
 * any task runs: some of the four set and some not is a typo in the one place a typo strands every
 * future install, so it names the missing ones and stops.
 *
 * **Play App Signing enrolment — an OPEN OWNER DECISION, not settled here.** Recorded in this file
 * because the moment it matters is a one-time, irreversible choice in the Play Console long after
 * this was written.
 *
 * *What changed.* The 2026-09-08 ruling recorded here previously said: never accept Google's
 * generated app signing key, always upload this keystore's key through the PEPK tool, because a
 * Google-generated key would make the Play release a different signing identity from the
 * **sideloaded** beta, and every tester's sideloaded install could then only be replaced by an
 * uninstall that destroys their journal. **As of the owner's ruling of 2026-09-09, distribution is
 * Play closed testing only — no sideloading.** There are therefore no sideloaded installs whose
 * update path needs preserving, and that argument — which was the whole of the reasoning for
 * PEPK — no longer applies. It is not that the conclusion was refuted; its premise was withdrawn.
 *
 * *The trade-off that actually remains*, stated so the owner can decide at enrolment:
 *
 * - **Accept Google's generated app signing key.** Google holds the app signing key; the owner
 *   never has a copy and therefore cannot lose it. This keystore then serves as the *upload* key,
 *   and an upload key that is lost or compromised can be reset by Play support — a recoverable
 *   failure. The cost: the app's Play identity is held by Google and cannot be taken off Play, so
 *   distributing a build outside Play later (sideload, another store) means a different signing
 *   identity and, for anyone who ever installs both, an uninstall to switch.
 * - **Upload your own key via PEPK.** The owner owns the app signing key, so the identity is
 *   portable — the same identity can sign builds distributed off Play. The cost: losing that
 *   keystore is permanent and unrecoverable; Play cannot reissue an app signing key the owner
 *   supplied, and no future update to the existing app listing can be signed.
 *
 * The choice is between *unloseable but not portable* and *portable but unrecoverable if lost*.
 * Nothing in the beta forces either one. Decide it at enrolment, and record the ruling here.
 *
 * *Either way, back up the keystore.* This keystore is the upload key under both options, so losing
 * it still blocks submissions — the difference is only whether that block is resettable by Play
 * (Google-generated app signing key) or terminal (PEPK). "It doesn't matter now" is not a
 * consequence of the sideloading ruling.
 *
 * Background and the owner's cited documentation for the superseded 2026-09-08 reasoning:
 * `docs/audits/2026-09-08-beta-signing-identity-completion-report.md`. The premise withdrawal is
 * recorded in `docs/audits/2026-09-09-allow-backup-and-play-signing-notes.md`.
 */
class SigningIdentity(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun resolveSigningIdentity(): SigningIdentity? {
    val propertiesFile = rootProject.file("signing.properties")
    val properties = Properties().apply { if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) } }
    fun read(environmentVariable: String, propertyKey: String): String? =
        providers.environmentVariable(environmentVariable).orNull?.takeIf { it.isNotBlank() }
            ?: properties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
    val storeFile = read("FORAGER_SIGNING_STORE_FILE", "storeFile")
    val storePassword = read("FORAGER_SIGNING_STORE_PASSWORD", "storePassword")
    val keyAlias = read("FORAGER_SIGNING_KEY_ALIAS", "keyAlias")
    val keyPassword = read("FORAGER_SIGNING_KEY_PASSWORD", "keyPassword")
    val missing = listOfNotNull(
        "FORAGER_SIGNING_STORE_FILE / storeFile".takeIf { storeFile == null },
        "FORAGER_SIGNING_STORE_PASSWORD / storePassword".takeIf { storePassword == null },
        "FORAGER_SIGNING_KEY_ALIAS / keyAlias".takeIf { keyAlias == null },
        "FORAGER_SIGNING_KEY_PASSWORD / keyPassword".takeIf { keyPassword == null },
    )
    if (missing.size == 4) return null
    if (missing.isNotEmpty()) {
        error(
            "The signing identity is half-configured: missing ${missing.joinToString(", ")}. " +
                "Set all four (environment variables, or keys in signing.properties at the repository " +
                "root) or none -- a partially set identity is the one place a typo strands every future install.",
        )
    }
    val resolvedStoreFile = File(storeFile!!).let { if (it.isAbsolute) it else rootProject.file(storeFile) }
    return SigningIdentity(resolvedStoreFile, storePassword!!, keyAlias!!, keyPassword!!)
}

val signingIdentity = resolveSigningIdentity()
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

    // A stable debug signing identity, committed at app/debug.keystore (return-estimate device
    // checks, pass 1). Without it every CI runner mints its own ~/.android/debug.keystore, so two
    // CI-built debug APKs carry different certificates and `adb install -r` of one over the other
    // fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE -- which makes an in-place upgrade, the only
    // thing a migration test on a device tests, impossible without an uninstall that destroys the
    // data under test. The key is the conventional Android debug identity (alias androiddebugkey,
    // password "android", CN=Android Debug): it signs nothing that ships, its secrecy protects
    // nothing, and committing it is the standard way to give a team one debug identity. It is
    // debug-only by construction -- the release build type has no signingConfig here and must
    // never be given this one; a release key is a separate, uncommitted decision.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The beta-and-release identity, present only where the secret is -- see
        // resolveSigningIdentity() above for the sources and what "absent" means.
        signingIdentity?.let { identity ->
            create("release") {
                storeFile = identity.storeFile
                storePassword = identity.storePassword
                keyAlias = identity.keyAlias
                keyPassword = identity.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // null when no identity is configured: the guard below then fails assembleRelease with
            // a message naming what to set. Never the debug config, never silently unsigned.
            signingConfig = signingIdentity?.let { signingConfigs.getByName("release") }
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

/**
 * The SHA-256 fingerprint of the certificate in the committed `app/debug.keystore`, as
 * `keytool -list -v -keystore app/debug.keystore -storepass android` prints it. **An independent
 * constant, recorded from keytool's own output** (dedicated-beta-signing-identity dispatch: "do
 * not derive the guard's expected value from the signing code it checks") — not computed from
 * the file at build time, so a swapped debug keystore cannot move the goalposts with it. If the
 * debug keystore is ever regenerated, this line changes with it, from keytool, by hand.
 */
val DEBUG_KEYSTORE_CERTIFICATE_SHA256 =
    "CB:2F:6D:A5:02:C3:FE:7B:EA:8D:B7:47:41:4B:ED:47:CB:C8:35:09:44:CF:82:91:E9:B0:98:06:C9:4F:16:26"

/** The SHA-256 fingerprint of [alias]'s certificate in [storeFile], in keytool's colon-separated upper-case form. */
fun certificateSha256(storeFile: File, storePassword: String, alias: String): String {
    val keyStore = listOf("PKCS12", "JKS").firstNotNullOfOrNull { type ->
        runCatching {
            KeyStore.getInstance(type).also { store -> storeFile.inputStream().use { store.load(it, storePassword.toCharArray()) } }
        }.getOrNull()
    } ?: error("Could not open keystore ${storeFile} as PKCS12 or JKS with the configured store password.")
    val certificate = keyStore.getCertificate(alias)
        ?: error("Keystore ${storeFile} has no certificate under alias '$alias'.")
    return MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString(":") { "%02X".format(it) }
}

/**
 * Fails a release build that is not signed with a real, private identity — beta-signing finding
 * on the return-estimate device checks, extended by the dedicated-beta-signing-identity dispatch.
 *
 * **Why:** Android refuses to update an installed app with a package signed by a different
 * identity (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), and the only way forward is an uninstall that
 * destroys the app's data. This session lived through that once, on one device, with an operator
 * who knew what had happened. A tester who installed a build signed with the committed debug key
 * (`app/debug.keystore` — its alias and password are printed in this file, so anyone can sign a
 * package as `com.forager.app` with it) and later received a build signed with the real key would
 * lose every track, entry and photo, with no export path and no backup — multiplied by the cohort.
 * A doc comment saying "never" is not a constraint; this is.
 *
 * **Three refusals, one pass:**
 * - **No signing config on the release build type** (nothing configured — see
 *   [resolveSigningIdentity]): fails, naming the four values to set. An unsigned release, or one
 *   AGP would sign with whatever it finds, is not a release.
 * - **The committed debug keystore, by path** (`app/debug.keystore`): fails.
 * - **The committed debug keystore's certificate, by fingerprint** — the store file is opened and
 *   its certificate hashed and compared to [DEBUG_KEYSTORE_CERTIFICATE_SHA256], so a *copy* of the
 *   debug keystore under another name or path fails too. The path check alone would pass it.
 * - Any other identity whose store file exists and opens: **passes**, and the certificate's
 *   fingerprint is printed so the owner can compare it to `apksigner verify --print-certs` on the
 *   artifact and to `keytool -list -v` on the keystore they hold.
 *
 * Reads `android.buildTypes` at task-execution time (`doLast`), after the whole script has been
 * evaluated, rather than the blocks above at configuration time — checking there would only catch
 * a mistake made in this file, not one made by a later script (a product-flavor override, a
 * variant filter) that reassigns the release signing config after this block runs. Wired as a
 * real dependency of `assembleRelease`/`bundleRelease` below, not merely `finalizedBy`, so a
 * release build cannot produce a wrongly-signed artifact even if this task is somehow skipped by
 * name — Gradle still has to run it to reach either task.
 */
tasks.register("verifyReleaseNeverSignsWithDebugKeystore") {
    doLast {
        val releaseSigningConfig = android.buildTypes.getByName("release").signingConfig
            ?: error(
                "The release build type has no signing identity. A release is never built unsigned " +
                    "and never with the debug key: set FORAGER_SIGNING_STORE_FILE, " +
                    "FORAGER_SIGNING_STORE_PASSWORD, FORAGER_SIGNING_KEY_ALIAS and " +
                    "FORAGER_SIGNING_KEY_PASSWORD (or the four keys in signing.properties at the " +
                    "repository root) to the beta/release keystore the owner holds. See " +
                    "resolveSigningIdentity() in app/build.gradle.kts.",
            )
        val storeFile = releaseSigningConfig.storeFile
            ?: error("The release signing config '${releaseSigningConfig.name}' has no store file.")
        if (!storeFile.isFile) error("The release signing keystore does not exist: ${storeFile.absolutePath}")
        val storePassword = releaseSigningConfig.storePassword
            ?: error("The release signing config '${releaseSigningConfig.name}' has no store password.")
        val keyAlias = releaseSigningConfig.keyAlias
            ?: error("The release signing config '${releaseSigningConfig.name}' has no key alias.")

        val debugKeystoreFile = file("debug.keystore").canonicalFile
        val fingerprint = certificateSha256(storeFile, storePassword, keyAlias)
        if (storeFile.canonicalFile == debugKeystoreFile || fingerprint == DEBUG_KEYSTORE_CERTIFICATE_SHA256) {
            error(
                "The release build type resolves to the committed, public debug signing identity " +
                    "(${storeFile.absolutePath}, certificate SHA-256 $fingerprint). A release build " +
                    "carrying real user data must be signed with the private beta/release key the " +
                    "owner holds, never with the debug key. See this task's own doc comment.",
            )
        }
        logger.lifecycle(
            "Verified: the release build type signs with '${releaseSigningConfig.name}' " +
                "(${storeFile.absolutePath}, alias '$keyAlias'), certificate SHA-256 $fingerprint -- not the debug identity.",
        )
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }
    .configureEach { dependsOn("verifyReleaseNeverSignsWithDebugKeystore") }

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
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)

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
 * Wires `-Pforager.generateFungiIndexDbAsset=true` through to
 * `com.forager.app.tools.GenerateFungiIndexDbAsset`'s `Assume.assumeTrue` guard (see that class's
 * doc comment) as a JVM system property, so the generator is reachable without a second, separate
 * `Test` task. A second task was tried first and dropped: copying `testDebugUnitTest`'s classpath
 * eagerly (`tasks.named<Test>(...).get()`) at the top level fails, because AGP registers that task
 * lazily and it does not exist yet at that point in script evaluation — `tasks.named { }`'s lazy
 * configuration form used below has no such ordering requirement.
 *
 * Without the flag, `./gradlew test`/`testDebugUnitTest` still reaches this class like any other
 * `@Test`, but `Assume.assumeTrue(false)` reports it skipped rather than running it — it does real
 * file-system work (rewrites a committed asset) that has no place in the ordinary, side-effect-free
 * suite. Run it deliberately, after the index JSON changes, via
 * `./gradlew testDebugUnitTest --tests "com.forager.app.tools.GenerateFungiIndexDbAsset" -Pforager.generateFungiIndexDbAsset=true`
 * (wrapped by `scripts/generate_fungi_index_db.sh`), then commit the regenerated `.db` asset.
 *
 * `tasks.withType<Test>().configureEach { }`, not `tasks.named<Test>("testDebugUnitTest") { }`:
 * AGP does not register that task until some point after this script's top-level body finishes
 * evaluating (a `tasks.named("testDebugUnitTest")` lookup this early throws `UnknownTaskException`,
 * confirmed against this project's AGP version), while `withType(...).configureEach` attaches its
 * action to every `Test` task as it is registered, whenever that happens.
 */
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        systemProperty(
            "forager.generateFungiIndexDbAsset",
            (project.findProperty("forager.generateFungiIndexDbAsset") ?: "false").toString(),
        )
    }
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

