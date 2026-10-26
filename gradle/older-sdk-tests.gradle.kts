import java.io.File

/**
 * The test classes that run Robolectric on an Android SDK other than [MAIN_JVM_SDK] run in their own test JVM
 * (dispatch 2026-10-26-08, RECORD -1656; the owner, on the heap report: "2: old-Android tests apart").
 *
 * Why. Robolectric keeps one decompressed framework resource table per SDK it has loaded, in static registries that
 * live as long as the JVM, and the JDK keeps each SDK's android-all jar directory open. In one shared test JVM the
 * SDKs 27, 28, 30 and 33 cost at least 132.8 MiB of the 512 MiB heap for the sake of six classes, and left the heap
 * within about 22 MiB of its limit from class 630 on (prompts/preserved/2026-10-26-04-report.md, sections 2 to 4).
 * A second JVM frees all of it in the main one without dropping a test. The heap limit is not changed: neither task
 * sets one, so both run at Gradle's default.
 *
 * How. `testDebugUnitTest` excludes [olderSdkTestClasses]; `testDebugUnitTestOlderSdks` runs exactly them, on the
 * same classpath and JVM settings, and finalizes `testDebugUnitTest`, so asking for the one task still runs every
 * test. Each task writes its own JUnit XML (`build/test-results/<task name>/`); CI's summary and report upload read
 * both.
 *
 * Rejected: a JUnit category on each class (the list would then live in six files and the build script, and a
 * forgotten category fails exactly as silently as a forgotten list entry); `forkEvery` (bounds everything but
 * reloads a table at every fork and costs a JVM start per fork); a larger heap (a heap-limit change, which is the
 * owner's, and hides the next step instead of removing this one).
 *
 * The list cannot drift silently: [verifyOlderSdkTestClassList] reads every `@Config` in the unit-test sources and
 * fails the build when a class that sets an SDK other than [MAIN_JVM_SDK] is not listed, when a listed class sets
 * none, or when it meets an `sdk` it cannot read. Both test tasks depend on it.
 *
 * Applied from app/build.gradle.kts, and kept here rather than in app/: Android lint reads every Kotlin script in the
 * module directory as a build script and crashed on this one there (CI run 40273911138, `lintAnalyzeDebug` and
 * `lintVitalAnalyzeRelease`: "findFirCompiledSymbol only works on compiled declarations"). `file(...)` below still
 * resolves against the app project, the one that applies it.
 */
val MAIN_JVM_SDK = 36

/** The one place the list is kept. Fully qualified, because the Test filters match on the class name. */
val olderSdkTestClasses = listOf(
    "com.zynergylabs.forager.app.alert.AlertDeliveryOutcomeTest", // sdk 33, one method
    "com.zynergylabs.forager.app.alert.AndroidAlertDeliveryTest", // sdk 30, two methods sdk 33
    "com.zynergylabs.forager.app.alert.AndroidBackgroundRunCheckTest", // sdk 27, one method
    "com.zynergylabs.forager.app.alert.BackByAlertDeliveryTest", // sdk 30
    "com.zynergylabs.forager.app.ui.log.PhotoViewerSaveRawToFolderTest", // sdk 28
    "com.zynergylabs.forager.app.ui.log.PhotoViewerSaveToFolderTest", // sdk 28
)

/**
 * Set by CI's "Run this branch" when it passes a `--tests` filter to both tasks: a filter that names classes in only
 * one of them would otherwise fail the other with "No tests found". CI's summary still fails a run in which no test
 * ran at all, so a filter that matches nothing anywhere is still caught.
 */
val filteredTestRun = (findProperty("forager.filteredTestRun") ?: "false").toString() == "true"

val testSourceDirs = listOf("src/test/java", "src/test/kotlin", "src/testDebug/java", "src/testDebug/kotlin")
    .map { file(it) }
val testResourceDirs = listOf("src/test/resources", "src/testDebug/resources").map { file(it) }

val verifyOlderSdkTestClassList = tasks.register("verifyOlderSdkTestClassList") {
    group = "verification"
    description = "Fails when the older-SDK test class list in gradle/older-sdk-tests.gradle.kts no longer matches the sources."
    val sourceDirs = testSourceDirs
    val resourceDirs = testResourceDirs
    val listed = olderSdkTestClasses.toSet()
    val mainSdk = MAIN_JVM_SDK
    inputs.files(sourceDirs.map { fileTree(it) })
    inputs.files(resourceDirs.map { fileTree(it) { include("**/robolectric.properties") } })
    doLast {
        val scan = OlderSdkScan(mainSdk)
        sourceDirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".kt") || it.name.endsWith(".java")) }
                .forEach { scan.scanFile(it) }
        }
        resourceDirs.filter { it.isDirectory }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.name == "robolectric.properties" }.forEach { props ->
                if (Regex("""(?m)^\s*(sdk|minSdk|maxSdk)\s*=""").containsMatchIn(props.readText())) {
                    scan.problems += "$props sets an SDK for every class; this check cannot sort classes by it"
                }
            }
        }
        val unlisted = scan.found.keys - listed
        val stale = listed - scan.found.keys
        unlisted.sorted().forEach { scan.problems += "$it sets sdk ${scan.found.getValue(it)} but is not in olderSdkTestClasses" }
        stale.sorted().forEach { scan.problems += "$it is in olderSdkTestClasses but sets no SDK other than $mainSdk" }
        if (scan.problems.isNotEmpty()) {
            throw GradleException(
                "The older-SDK test class list (gradle/older-sdk-tests.gradle.kts) does not match the sources:\n" +
                    scan.problems.joinToString("\n") { "  - $it" },
            )
        }
        logger.lifecycle("Older-SDK test classes: ${scan.found.size}, all listed (${scan.configsRead} @Config read).")
    }
}

/**
 * Reads `@Config(...)` annotations from test sources. Comments are blanked first (string and backtick literals are
 * kept), so an `@Config` quoted in a doc comment is not read. The class a `@Config` belongs to: written at the
 * start of a line, the next top-level class; indented, the top-level class it sits in. An indented `@Config` with an
 * older SDK inside a nested class is reported, not guessed, because the Test filters name top-level classes.
 */
class OlderSdkScan(private val mainSdk: Int) {
    val found = sortedMapOf<String, MutableSet<Int>>()
    val problems = mutableListOf<String>()
    var configsRead = 0

    fun scanFile(file: File) {
        val raw = file.readText()
        val text = blankComments(raw)
        val pkg = Regex("""(?m)^\s*package\s+([\w.]+)""").find(text)?.groupValues?.get(1)
        val constants = Regex("""const\s+val\s+(\w+)\s*(?::\s*Int\s*)?=\s*(\d+)""").findAll(text)
            .mapNotNull { m -> m.groupValues[2].toIntOrNull()?.let { m.groupValues[1] to it } }.toMap()
        val classes = topLevelClasses(text)
        Regex("""@Config\s*\(""").findAll(text).forEach { match ->
            configsRead++
            val open = match.range.last
            val close = matchingParen(text, open) ?: run {
                problems += "${file.path}: unbalanced @Config at offset ${match.range.first}"
                return@forEach
            }
            val args = text.substring(open + 1, close)
            if (Regex("""\b(minSdk|maxSdk)\s*=""").containsMatchIn(args)) {
                problems += "${file.path}:${lineOf(text, match.range.first)}: minSdk/maxSdk is not read by this check; sort the class by hand"
            }
            val sdks = sdkValues(args, constants) ?: run {
                problems += "${file.path}:${lineOf(text, match.range.first)}: cannot read the sdk in @Config($args)"
                return@forEach
            }
            val older = sdks.filter { it != mainSdk }
            if (older.isEmpty()) return@forEach
            val owner = ownerClass(text, match.range.first, classes) ?: run {
                problems += "${file.path}:${lineOf(text, match.range.first)}: @Config(sdk = $older) with no class this check can name"
                return@forEach
            }
            if (pkg == null) {
                problems += "${file.path}: no package line"
                return@forEach
            }
            found.getOrPut("$pkg.$owner") { sortedSetOf() }.addAll(older)
        }
    }

    /** The SDKs an `@Config`'s arguments name, `[]` when there is no `sdk`, or null when one cannot be read. */
    private fun sdkValues(args: String, constants: Map<String, Int>): List<Int>? {
        val sdk = Regex("""\bsdk\s*=\s*""").find(args) ?: return emptyList()
        val rest = args.substring(sdk.range.last + 1).trimStart()
        val body = when {
            rest.startsWith("[") -> rest.substring(1, rest.indexOf(']').takeIf { it > 0 } ?: return null)
            rest.startsWith("{") -> rest.substring(1, rest.indexOf('}').takeIf { it > 0 } ?: return null)
            rest.startsWith("intArrayOf(") -> rest.substring(11, rest.indexOf(')').takeIf { it > 0 } ?: return null)
            else -> Regex("""^[\w.]+""").find(rest)?.value ?: return null
        }
        return body.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { token ->
            token.toIntOrNull() ?: constants[token] ?: return null
        }
    }

    private fun topLevelClasses(text: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var lineStart = 0
        for (line in text.split('\n')) {
            if (line.isNotEmpty() && !line[0].isWhitespace()) {
                Regex("""(?<!::)\bclass\s+([A-Za-z_]\w*)""").find(line)?.let { result += lineStart to it.groupValues[1] }
            }
            lineStart += line.length + 1
        }
        return result
    }

    private fun ownerClass(text: String, at: Int, classes: List<Pair<Int, String>>): String? {
        val lineStart = text.lastIndexOf('\n', at - 1) + 1
        val indented = text[lineStart].isWhitespace()
        if (!indented) return classes.firstOrNull { it.first >= lineStart }?.second
        val outer = classes.lastOrNull { it.first < lineStart } ?: return null
        val between = text.substring(outer.first, lineStart).split('\n').drop(1)
        if (between.any { it.isNotEmpty() && it[0].isWhitespace() && Regex("""(?<!::)\bclass\s+\w""").containsMatchIn(it) }) {
            problems += "an indented @Config with an older SDK sits in a nested class of ${outer.second}; list it by hand"
        }
        return outer.second
    }

    private fun matchingParen(text: String, open: Int): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
        }
        return null
    }

    private fun lineOf(text: String, offset: Int) = text.substring(0, offset).count { it == '\n' } + 1

    /** Blanks `//` and (nested) block comments to spaces, keeping newlines and string, char and backtick literals. */
    private fun blankComments(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3)
                    i = if (end < 0) src.length else end + 3
                }
                c == '"' || c == '\'' || c == '`' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') j += if (src[j] == '\\' && c != '`') 2 else 1
                    i = j + 1
                }
                src.startsWith("//", i) -> {
                    while (i < src.length && src[i] != '\n') out.setCharAt(i++, ' ')
                }
                src.startsWith("/*", i) -> {
                    var depth = 0
                    while (i < src.length) {
                        if (src.startsWith("/*", i)) { depth++; out.setCharAt(i, ' '); out.setCharAt(i + 1, ' '); i += 2; continue }
                        if (src.startsWith("*/", i)) { depth--; out.setCharAt(i, ' '); out.setCharAt(i + 1, ' '); i += 2; if (depth == 0) break; continue }
                        if (src[i] != '\n') out.setCharAt(i, ' ')
                        i++
                    }
                }
                else -> i++
            }
        }
        return out.toString()
    }
}

val olderSdkTestTask = tasks.register<Test>("testDebugUnitTestOlderSdks") {
    group = "verification"
    description = "Runs the unit-test classes on Android SDKs other than $MAIN_JVM_SDK, in their own JVM."
    // Realised only when this task is, by which point AGP has registered and configured testDebugUnitTest.
    val main = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = main.testClassesDirs
    classpath = main.classpath
    javaLauncher.set(main.javaLauncher)
    useJUnit()
    jvmArgs(main.jvmArgs.orEmpty())
    jvmArgumentProviders.addAll(main.jvmArgumentProviders)
    systemProperties(main.systemProperties)
    maxHeapSize = main.maxHeapSize // null on both today, so both run at Gradle's default
    olderSdkTestClasses.forEach { filter.includeTestsMatching(it) }
    if (filteredTestRun) filter.isFailOnNoMatchingTests = false
    dependsOn(verifyOlderSdkTestClassList)
}

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        olderSdkTestClasses.forEach { filter.excludeTestsMatching(it) }
        if (filteredTestRun) filter.isFailOnNoMatchingTests = false
        dependsOn(verifyOlderSdkTestClassList)
        finalizedBy(olderSdkTestTask)
    }
}
