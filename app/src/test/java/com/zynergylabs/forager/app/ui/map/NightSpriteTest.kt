package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.STREET_SPRITE_ICON_NAMES
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bundled night sheets (`app/src/main/assets/night-sprite/v5`, dispatch 2026-10-22-06 Amendment 1, RECORD -1566)
 * are the night rules ([nightSpritePixelsOf]) applied to sprite v5's day sheets, pixel for pixel, at 1x and @2x, with
 * the day index unchanged.
 *
 * This test is also how the sheets are made: when a committed sheet is missing or differs, it writes the sheets the
 * rules give to `app/build/test-results/testDebugUnitTest/night-sprite-generated/` (uploaded by CI with the test
 * report) and fails. Copying those files into the assets directory is the whole build step, and this test then holds
 * them. Reproducible means pixels and index, not PNG bytes.
 */
class NightSpriteTest {

    private val dayDir = File("src/test/resources/sprite-v5-day")
    private val nightDir = File("src/main/assets/$NIGHT_SPRITE_ASSET_DIR")
    private val generatedDir = File("build/test-results/testDebugUnitTest/night-sprite-generated")

    /**
     * Sprite v5 as R2 serves it: Aspen's sums from the upload's read-back (RECORD -1564; Forager-cartography
     * `docs/audits/2026-10-22-sprite-v5-upload.md`), which the live files also gave on 2026-10-22.
     */
    private val r2Sums = mapOf(
        "light.json" to "5d4f7869f8826454411d5fe02d9746b8cb46b0dfb4ccb634f6ad79c0729173e4",
        "light.png" to "6a1ddbf336980f4506d0f006072a22cb37ba966feda63fc94d1319731d458622",
        "light@2x.json" to "073f4064c08a35008afbdb2a0b8c19e1962be4906b905890e35f6e30fca2db9b",
        "light@2x.png" to "215987d8e70482f03350af734f347704d80a8c5dd553998af8b87b32414fc838",
    )

    private val densities = listOf("", "@2x")

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun index(file: File): JsonObject = Json.parseToJsonElement(file.readText()).jsonObject

    private fun pixels(image: BufferedImage, x: Int, y: Int, w: Int, h: Int): IntArray = image.getRGB(x, y, w, h, null, 0, w)

    /** The night sheet the rules give for [suffix]: every index entry's day pixels through [nightSpritePixelsOf]. */
    private fun expectedNightSheet(suffix: String): BufferedImage {
        val day = ImageIO.read(File(dayDir, "light$suffix.png"))
        val night = BufferedImage(day.width, day.height, BufferedImage.TYPE_INT_ARGB)
        for ((name, entry) in index(File(dayDir, "light$suffix.json"))) {
            val e = entry.jsonObject
            val (x, y, w, h) = listOf("x", "y", "width", "height").map { e.getValue(it).jsonPrimitive.int }
            night.setRGB(x, y, w, h, nightSpritePixelsOf(name, pixels(day, x, y, w, h)), 0, w)
        }
        return night
    }

    @Test
    fun `the committed day sheets are sprite v5 as R2 serves it`() {
        assertEquals(r2Sums, r2Sums.keys.associateWith { sha256(File(dayDir, it)) })
    }

    @Test
    fun `each night sheet is the night rules applied to the day sheet, pixel for pixel, at 1x and 2x, with the day index`() {
        val problems = mutableListOf<String>()
        for (suffix in densities) {
            val expected = expectedNightSheet(suffix)
            val dayIndexFile = File(dayDir, "light$suffix.json")
            val png = File(nightDir, "light$suffix.png")
            val json = File(nightDir, "light$suffix.json")
            val sheetProblems = mutableListOf<String>()
            if (!png.exists() || !json.exists()) {
                sheetProblems += "light$suffix: no committed night sheet"
            } else {
                if (index(json) != index(dayIndexFile)) sheetProblems += "light$suffix.json: not the day index"
                val actual = ImageIO.read(png)
                if (actual.width != expected.width || actual.height != expected.height) {
                    sheetProblems += "light$suffix.png: ${actual.width}x${actual.height}, expected ${expected.width}x${expected.height}"
                } else {
                    val differing = index(dayIndexFile).filter { (_, entry) ->
                        val e = entry.jsonObject
                        val (x, y, w, h) = listOf("x", "y", "width", "height").map { e.getValue(it).jsonPrimitive.int }
                        !pixels(actual, x, y, w, h).contentEquals(pixels(expected, x, y, w, h))
                    }.keys
                    if (differing.isNotEmpty()) sheetProblems += "light$suffix.png: night pixels differ from the rules for $differing"
                    val whole = pixels(actual, 0, 0, actual.width, actual.height).contentEquals(pixels(expected, 0, 0, expected.width, expected.height))
                    if (!whole && differing.isEmpty()) sheetProblems += "light$suffix.png: pixels outside the index entries are not empty"
                }
            }
            if (sheetProblems.isNotEmpty()) {
                generatedDir.mkdirs()
                ImageIO.write(expected, "png", File(generatedDir, "light$suffix.png"))
                dayIndexFile.copyTo(File(generatedDir, "light$suffix.json"), overwrite = true)
                problems += sheetProblems
            }
        }
        assertEquals("the rules' sheets are written to ${generatedDir.absolutePath}", emptyList<String>(), problems)
    }

    @Test
    fun `the night set is the 61 images of sprite v5, every icon the style records and both patterns`() {
        for (suffix in densities) {
            val names = index(File(dayDir, "light$suffix.json")).keys
            assertEquals(61, names.size)
            assertEquals(STREET_SPRITE_ICON_NAMES.toSet() + "tree_dark_irregular", names)
            assertEquals(STREET_NIGHT_PATTERN_PAINTS.keys, names.filter { it in STREET_NIGHT_PATTERN_PAINTS }.toSet())
            assertEquals(STREET_NIGHT_ICON_RINGS.keys, names.filter { it in STREET_NIGHT_ICON_RINGS }.toSet())
        }
    }
}
