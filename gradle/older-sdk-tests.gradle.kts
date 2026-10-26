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
        (unlisted - scan.inherited).sorted().forEach { scan.problems += "$it sets sdk ${scan.found.getValue(it)} but is not in olderSdkTestClasses" }
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
 * kept), so an `@Config` quoted in a doc comment is not read. Where a `@Config` sits is read by brace depth, on a copy
 * with the literals blanked too, so a brace in a string does not move it:
 * - **outside every class**, it is on the next top-level class;
 * - **directly in a top-level class's body**, on a method, it is that class's, whatever nested classes (a private fake,
 *   say) sit above it (Sorrel's #410 review, should-fix 1);
 * - **on a nested class, or inside one**, it is reported with its line, because the list names top-level classes: the
 *   test is moved to a top-level class of its own, which is then listed. Rejected: listing the top-level class for it,
 *   which Gradle 9.7's class filter would honour today (a pattern with no wildcard also matches `Outer$Nested`), since
 *   that rests on a filter detail a Gradle upgrade could change silently, and no test here is shaped that way.
 *
 * A `@Config` with an older SDK on an `abstract`, `open` or `sealed` top-level class, or on one of its methods, is
 * reported too (Sorrel's #410 review, should-fix 2): Robolectric applies it to every subclass, and the list, which names
 * the class the annotation is written in, would carry the base and miss the subclasses that run.
 */
class OlderSdkScan(private val mainSdk: Int) {
    val found = sortedMapOf<String, MutableSet<Int>>()
    val problems = mutableListOf<String>()
    var configsRead = 0

    /** Classes in [found] whose `@Config` their subclasses inherit; reported once each, and not as "not listed". */
    val inherited = sortedSetOf<String>()

    fun scanFile(file: File) {
        val raw = file.readText()
        val text = blankComments(raw)
        val pkg = Regex("""(?m)^\s*package\s+([\w.]+)""").find(text)?.groupValues?.get(1)
        val constants = Regex("""const\s+val\s+(\w+)\s*(?::\s*Int\s*)?=\s*(\d+)""").findAll(text)
            .mapNotNull { m -> m.groupValues[2].toIntOrNull()?.let { m.groupValues[1] to it } }.toMap()
        val code = blankLiterals(text)
        val depth = braceDepths(code)
        val classes = classDeclarations(code, depth)
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
            val at = "${file.path}:${lineOf(text, match.range.first)}"
            val owner = when (val placed = ownerClass(code, depth, match.range.first, close, classes)) {
                is Owner.TopLevel -> placed.declaration
                is Owner.Nested -> {
                    problems += "$at: @Config(sdk = $older) is on or inside a nested class of ${placed.outer}; the older-SDK " +
                        "list names top-level classes, so move this test into a top-level class of its own and list that"
                    return@forEach
                }
                null -> {
                    problems += "$at: @Config(sdk = $older) with no class this check can name"
                    return@forEach
                }
            }
            if (pkg == null) {
                problems += "${file.path}: no package line"
                return@forEach
            }
            val name = "$pkg.${owner.name}"
            found.getOrPut(name) { sortedSetOf() }.addAll(older)
            if (owner.modifier != null && inherited.add(name)) {
                problems += "$at: $name is ${owner.modifier} and sets sdk $older; Robolectric applies its @Config to every " +
                    "subclass, which the older-SDK list cannot see, so put the @Config on each runnable subclass instead"
            }
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

    /** A class declaration: where its keyword is, its name, its brace depth, and `abstract`/`open`/`sealed` if written. */
    class Declaration(val at: Int, val name: String, val depth: Int, val modifier: String?)

    sealed interface Owner {
        class TopLevel(val declaration: Declaration) : Owner
        class Nested(val outer: String) : Owner
    }

    /** The brace depth at every offset of [code] (comments and literals already blanked). */
    private fun braceDepths(code: String): IntArray {
        val depths = IntArray(code.length + 1)
        var d = 0
        for (i in code.indices) {
            if (code[i] == '}') d = maxOf(0, d - 1)
            depths[i] = d
            if (code[i] == '{') d++
        }
        depths[code.length] = d
        return depths
    }

    private fun classDeclarations(code: String, depth: IntArray): List<Declaration> =
        Regex("""(?<!::)\bclass\s+([A-Za-z_]\w*)""").findAll(code).map { m ->
            val lineStart = code.lastIndexOf('\n', m.range.first - 1) + 1
            val modifier = Regex("""\b(abstract|open|sealed)\b""").find(code.substring(lineStart, m.range.first))?.value
            Declaration(m.range.first, m.groupValues[1], depth[m.range.first], modifier)
        }.toList()

    /**
     * The class the `@Config` from [at] to [close] belongs to. Outside every class, the next top-level class. In a
     * top-level class's body (depth 1), that class, unless what it annotates is itself a class. Deeper, a nested class.
     */
    private fun ownerClass(code: String, depth: IntArray, at: Int, close: Int, classes: List<Declaration>): Owner? {
        val topLevel = classes.filter { it.depth == 0 }
        return when (depth[at]) {
            0 -> topLevel.firstOrNull { it.at > close }?.let { Owner.TopLevel(it) }
            1 -> {
                val outer = topLevel.lastOrNull { it.at < at } ?: return null
                if (annotatesAClass(code, close + 1)) Owner.Nested(outer.name) else Owner.TopLevel(outer)
            }
            else -> topLevel.lastOrNull { it.at < at }?.let { Owner.Nested(it.name) }
        }
    }

    /**
     * Whether the declaration from [from] on is a class (or object, interface or enum) rather than a method: the
     * annotations after the `@Config` are skipped, arguments and all, and the words before the declaration's first `(`,
     * `{`, `=` or `;` are read. A Kotlin `fun` or a Java method has no class keyword there.
     */
    private fun annotatesAClass(code: String, from: Int): Boolean {
        var i = from
        while (true) {
            while (i < code.length && code[i].isWhitespace()) i++
            if (i >= code.length || code[i] != '@') break
            i++
            while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '.' || code[i] == ':')) i++
            var j = i
            while (j < code.length && code[j].isWhitespace()) j++
            if (j < code.length && code[j] == '(') i = (matchingParen(code, j) ?: return false) + 1
        }
        val end = (i until code.length).firstOrNull { code[it] in "({=;" } ?: code.length
        return Regex("""\b(class|object|interface|enum)\b""").containsMatchIn(code.substring(i, end))
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

    /** [blankComments]'s output with string, char and backtick literals blanked to spaces too, newlines kept. */
    private fun blankLiterals(src: String): String {
        val out = StringBuilder(src)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                    for (k in i until end) if (src[k] != '\n') out.setCharAt(k, ' ')
                    i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') j += if (src[j] == '\\' && c != '`') 2 else 1
                    for (k in i until minOf(j + 1, src.length)) if (src[k] != '\n') out.setCharAt(k, ' ')
                    i = j + 1
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
