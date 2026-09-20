package com.zynergylabs.forager.app.diagnostics

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The debug build overrides `res/xml/file_paths.xml` wholesale (Android merges resources by
 * file, not by element), so a path added to main's file and forgotten in debug's would make the
 * debug build silently lose a share that release has. This reads both source files and asserts
 * debug's entries are a superset of main's, element by element. A plain JVM test over the source
 * tree: Gradle runs unit tests with the module directory as the working directory, and the test
 * fails naming that directory if the files are not where it expects rather than passing on nothing.
 */
class DebugFileProviderPathsTest {

    @Test
    fun `the debug file_paths carries every entry main declares`() {
        val main = pathsIn(File("src/main/res/xml/file_paths.xml"))
        val debug = pathsIn(File("src/debug/res/xml/file_paths.xml"))

        assertTrue("main's file_paths.xml declares nothing? parsed: $main", main.isNotEmpty())
        val missing = main - debug
        assertTrue("debug's file_paths.xml is missing entries main has: $missing (debug has $debug)", missing.isEmpty())
    }

    @Test
    fun `the debug file_paths adds the photos and diagnostics roots the panel shares from`() {
        val debug = pathsIn(File("src/debug/res/xml/file_paths.xml"))

        assertTrue(debug.contains(Triple("files-path", "photos", "photos/")))
        assertTrue(debug.contains(Triple("external-files-path", "diagnostics", "diagnostics/")))
        assertTrue(debug.contains(Triple("files-path", "diagnostics-internal", "diagnostics/")))
    }

    private fun pathsIn(file: File): Set<Triple<String, String, String>> {
        assertTrue("expected ${file.path} under the working directory ${File(".").absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val children = document.documentElement.childNodes
        return (0 until children.length)
            .map { children.item(it) }
            .filterIsInstance<Element>()
            .map { Triple(it.tagName, it.getAttribute("name"), it.getAttribute("path")) }
            .toSet()
    }
}
