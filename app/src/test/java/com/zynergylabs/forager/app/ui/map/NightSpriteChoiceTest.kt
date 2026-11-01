package com.zynergylabs.forager.app.ui.map

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bundled night sheets are chosen by the loaded style's own `sprite` (dispatch 2026-10-31-coder-07 Part 3; the owner:
 * "New icon set v6 (Recommended)"): Street v4 names sprite v6 and takes `night-sprite/v6`; v1 to v3, and any style that
 * names no sprite or another one, keep `night-sprite/v5`, as every style did before.
 */
class NightSpriteChoiceTest {

    private val worker = "https://forager-pmtiles.brandonlee1-894.workers.dev"

    private fun styleNaming(sprite: String?): String =
        if (sprite == null) """{"version":8,"sources":{},"layers":[]}""" else """{"version":8,"sprite":"$sprite","sources":{},"layers":[]}"""

    @Test
    fun `a style naming sprite v6 takes the v6 night sheets`() {
        assertEquals("night-sprite/v6", nightSpriteAssetDirFor(styleNaming("$worker/sprites/v6/light")))
        assertEquals("night-sprite/v6", nightSpriteAssetDirFor(styleNaming("http://localhost:8765/assets/sprites/v6/light")))
    }

    @Test
    fun `sprite v5, v4, another sprite, no sprite or unreadable JSON keep the v5 night sheets`() {
        for (sprite in listOf("$worker/sprites/v5/light", "$worker/sprites/v4/light", "$worker/sprites/v6/dark", "$worker/sprites/v66/light", null)) {
            assertEquals("$sprite", "night-sprite/v5", nightSpriteAssetDirFor(styleNaming(sprite)))
        }
        assertEquals("night-sprite/v5", nightSpriteAssetDirFor("""{"version":8,"sprite":[{"id":"x","url":"$worker/sprites/v6/light"}]}"""))
        assertEquals("night-sprite/v5", nightSpriteAssetDirFor("not json"))
    }

    @Test
    fun `the committed styles take the night sheets of the sprite they name`() {
        for (version in listOf("v1", "v2", "v3")) {
            assertEquals(version, "night-sprite/v5", nightSpriteAssetDirFor(File("../map-style/forager-street-$version.json").readText()))
        }
        assertEquals("night-sprite/v6", nightSpriteAssetDirFor(File("../map-style/forager-street-v4.json").readText()))
    }

    @Test
    fun `each night sheet folder the choice can name is bundled`() {
        for (dir in listOf("night-sprite/v5", "night-sprite/v6")) {
            for (name in listOf("light.json", "light.png", "light@2x.json", "light@2x.png")) {
                assertEquals("$dir/$name", true, File("src/main/assets/$dir/$name").isFile)
            }
        }
    }
}
