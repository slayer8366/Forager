package com.zynergylabs.forager.app.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No up or down arrow on a fold-open or drop-down list anywhere in the app (dispatch 2026-10-28-02, step 9; the owner:
 * "Remove all Chevrons from the drop down lists, those are banned from the app."). A source check: none of the arrow icons a
 * fold or a dropdown field draws is named anywhere in `app/src/main`. The photo viewer's left and right arrows are not
 * drop-downs and are not matched. Each list still opens and closes on its header: Tools' two folds in
 * `MapLayersToolsSectionTest`, a walk's Details in `TrackSheetDataTest`, an entry's contents groups in `EntryDataScreensTest`,
 * the Month field in `MapChromeOverMapTest`, Recent searches in `SearchClearAndFrameTest`.
 *
 * The count of files read is checked too, so this cannot pass by reading nothing (CLAUDE.md, "A check that passes because it
 * never saw the data that could fail it").
 */
class NoDropDownChevronsTest {

    private val banned = listOf("ExpandMore", "ExpandLess", "ArrowDropDown", "ArrowDropUp", "ExposedDropdownMenuDefaults.TrailingIcon", "KeyboardArrowDown", "KeyboardArrowUp")

    @Test
    fun `no fold or drop-down arrow is named in the app's sources`() {
        // Gradle runs unit tests from the module's directory; the fallback covers a run from the repository root.
        val main = listOf(File("src/main/java"), File("app/src/main/java")).first { it.isDirectory }
        val files = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("read ${files.size} source files, expected the app's several hundred", files.size > 500)
        assertTrue("the photo viewer's arrows are in the sample", files.any { it.name == "PhotoViewerDialog.kt" })
        val hits = files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line -> banned.firstOrNull { it in line }?.let { "${file.path}:${i + 1}: $it" } }
        }
        assertEquals("arrows on drop-down lists: $hits", emptyList<String>(), hits)
    }
}
