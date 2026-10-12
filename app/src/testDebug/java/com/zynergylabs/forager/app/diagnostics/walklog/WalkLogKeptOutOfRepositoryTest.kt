package com.zynergylabs.forager.app.diagnostics.walklog

import com.zynergylabs.forager.app.diagnostics.WalkLogger
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A walk log holds positions, so it never goes into the repository (dispatch 2026-09-28-532, "Where
 * the file goes, and where it never goes"). It is written on the phone and copied to
 * `~/Zynergy/device-evidence/`, outside any clone; this is the guard for the case where one is
 * copied into a working tree anyway. The name the logger gives a file, from [WalkLogger.fileName],
 * is asked of git itself (`git check-ignore`) at several places in the tree, so the check is of
 * the real rule against the real name, not of a pattern this test restates.
 */
class WalkLogKeptOutOfRepositoryTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, ".git").exists() }

    private fun isIgnored(relativePath: String): Boolean {
        val process = ProcessBuilder("git", "check-ignore", "-q", "--no-index", relativePath)
            .directory(repoRoot)
            .redirectErrorStream(true)
            .start()
        assertTrue("git check-ignore finished", process.waitFor(30, TimeUnit.SECONDS))
        return when (val exit = process.exitValue()) {
            0 -> true
            1 -> false
            else -> throw AssertionError("git check-ignore failed with exit $exit: ${process.inputStream.bufferedReader().readText()}")
        }
    }

    @Test
    fun `the file name is walklog-, the start time in UTC, then txt`() {
        assertEquals("walklog-20231114T221320Z.txt", WalkLogger.fileName(1_700_000_000_000L))
    }

    @Test
    fun `a walk log is ignored by git wherever in the tree it lands`() {
        val name = WalkLogger.fileName(1_700_000_000_000L)
        listOf(name, "app/$name", "docs/audits/$name", "app/src/test/resources/$name").forEach { path ->
            assertTrue("git must ignore $path", isIgnored(path))
        }
    }

    @Test
    fun `positive control - the same check reports an ordinary file as not ignored`() {
        assertFalse("docs/audits/README.md is tracked, so the check can say no", isIgnored("docs/audits/README.md"))
    }
}
