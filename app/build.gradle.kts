plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.osmdroid.android)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
