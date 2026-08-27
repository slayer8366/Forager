package com.forager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Guards "tag 01" from docs/plans/understory-design-system.md: colour roles the UI reads that
 * [ForagerTheme] never sets, which Compose then fills with Material3's own baseline (purple/red)
 * palette. Seven roles were in that state when this test was written, `error` among them with 13
 * read sites.
 *
 * **This scans the actual sources rather than checking a hard-coded list**, because the failure
 * being guarded is recurrence, not the original seven: a role added to a call site tomorrow and
 * not added to the scheme is the same defect, and a fixed list would not see it. The repo already
 * takes this shape elsewhere -- `scripts/verify-codeowners-placeholders.sh` greps a real file for
 * a real placeholder rather than asserting against a copy of it.
 *
 * **Why "differs from the Material baseline" is the assertion.** A [ColorScheme] instance does not
 * record which of its arguments were passed explicitly, so "was this role set?" cannot be asked
 * directly. Comparing against [lightColorScheme]/[darkColorScheme] with no arguments answers the
 * question that actually matters -- is this role rendering Material's palette or this app's -- and
 * a role deliberately set to a value that happens to equal the baseline is indistinguishable from
 * an unset one *by the only thing a user can see*, so treating both as failures is correct.
 *
 * **Why only roles the UI reads.** `onSecondary`, `onTertiary` and `onError` are all legitimately
 * pure white in this palette, which is also Material's baseline for them. Applying the
 * differs-from-baseline rule to every role would fail on three correct values. Scoping it to the
 * roles actually read keeps the rule aimed at the defect it exists for: a baseline colour reaching
 * the screen.
 */
class ThemeCompletenessTest {

    /**
     * Every role this app's UI could name, mapped to its accessor. A role read in `ui/` but absent
     * here fails the test rather than being skipped -- an unmapped read is exactly as invisible to
     * this check as an unset role is to the eye, and silently ignoring it would reopen tag 01
     * through the back door.
     */
    private val accessors: Map<String, (ColorScheme) -> Color> = mapOf(
        "primary" to { it.primary },
        "onPrimary" to { it.onPrimary },
        "primaryContainer" to { it.primaryContainer },
        "onPrimaryContainer" to { it.onPrimaryContainer },
        "inversePrimary" to { it.inversePrimary },
        "secondary" to { it.secondary },
        "onSecondary" to { it.onSecondary },
        "secondaryContainer" to { it.secondaryContainer },
        "onSecondaryContainer" to { it.onSecondaryContainer },
        "tertiary" to { it.tertiary },
        "onTertiary" to { it.onTertiary },
        "tertiaryContainer" to { it.tertiaryContainer },
        "onTertiaryContainer" to { it.onTertiaryContainer },
        "background" to { it.background },
        "onBackground" to { it.onBackground },
        "surface" to { it.surface },
        "onSurface" to { it.onSurface },
        "surfaceVariant" to { it.surfaceVariant },
        "onSurfaceVariant" to { it.onSurfaceVariant },
        "surfaceTint" to { it.surfaceTint },
        "inverseSurface" to { it.inverseSurface },
        "inverseOnSurface" to { it.inverseOnSurface },
        "error" to { it.error },
        "onError" to { it.onError },
        "errorContainer" to { it.errorContainer },
        "onErrorContainer" to { it.onErrorContainer },
        "outline" to { it.outline },
        "outlineVariant" to { it.outlineVariant },
        "scrim" to { it.scrim },
        "surfaceBright" to { it.surfaceBright },
        "surfaceDim" to { it.surfaceDim },
        "surfaceContainer" to { it.surfaceContainer },
        "surfaceContainerHigh" to { it.surfaceContainerHigh },
        "surfaceContainerHighest" to { it.surfaceContainerHighest },
        "surfaceContainerLow" to { it.surfaceContainerLow },
        "surfaceContainerLowest" to { it.surfaceContainerLowest },
    )

    private val readRolePattern = Regex("""colorScheme\s*\.\s*([a-zA-Z]+)""")

    private fun uiSourceRoot(): File {
        // Gradle runs unit tests with the module directory as the working directory, but walking
        // up a couple of levels keeps this working from the repo root too rather than failing in
        // a way that looks like "no roles are read anywhere."
        val candidates = listOf(
            "src/main/java/com/forager/app/ui",
            "app/src/main/java/com/forager/app/ui",
            "../app/src/main/java/com/forager/app/ui",
        )
        return candidates.map(::File).firstOrNull { it.isDirectory }
            ?: throw IllegalStateException(
                "Could not locate ui/ sources from ${File(".").absolutePath}. This test asserts " +
                    "against the real source tree; it must fail loudly rather than vacuously pass.",
            )
    }

    private fun rolesReadInUi(): Set<String> =
        uiSourceRoot().walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> readRolePattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()

    @Test
    fun `every colour role the UI reads is set in both schemes, not left on Material's baseline`() {
        val read = rolesReadInUi()

        // A vacuous pass here would be worse than a failure: it would report the palette as sound
        // while checking nothing. The UI read 15 distinct roles when this was written.
        assertTrue(
            "Found no colorScheme reads under ui/ — the scan is broken, not the palette.",
            read.size >= 10,
        )

        val unmapped = read.filterNot(accessors::containsKey)
        assertTrue(
            "ui/ reads colour role(s) this test does not know how to check: $unmapped. Add them " +
                "to `accessors` — an unchecked role is how tag 01 recurs.",
            unmapped.isEmpty(),
        )

        val lightBaseline = lightColorScheme()
        val darkBaseline = darkColorScheme()
        val offenders = mutableListOf<String>()

        for (role in read.sorted()) {
            val get = accessors.getValue(role)
            if (get(LightColors) == get(lightBaseline)) {
                offenders += "LightColors.$role is Material's baseline default"
            }
            if (get(DarkColors) == get(darkBaseline)) {
                offenders += "DarkColors.$role is Material's baseline default"
            }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "These roles are read by the UI but render Material3's baseline palette rather " +
                    "than this app's:\n  " + offenders.joinToString("\n  "),
            )
        }
    }
}
