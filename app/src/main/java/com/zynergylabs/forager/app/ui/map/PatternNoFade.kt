package com.zynergylabs.forager.app.ui.map

/**
 * A workaround for a MapLibre Native 13.5.0 fault: map patterns (fill-pattern: Trail Map's tree glyphs and wetland tufts)
 * go missing on the tiles that survive a style reload (a Night Maps switch, by day or by night). Dispatches 2026-10-27-11
 * and -13, RECORD -1742, -1746 and -1748; the cause is in `docs/audits/2026-10-27-glyph-cause.md` on branch `glyph-logging`.
 *
 * What was measured on the S22 (scratch builds, RECORD -1748): clearing and restoring the pattern layers failed when the
 * clear ran +0 to +250 ms after the style callback and the sprite were both done, and worked from +300 ms, by day and
 * night. MapLibre 13.5.0 treats fill-pattern as a cross-faded paint property that transitions
 * (`fill_layer_properties.hpp:40`, `properties.hpp:31-64`), and a style that sets no `transition`, as street-v3 does not,
 * gets 300 ms (`parser.hpp:40`, `constants.hpp:59`). Changing a pattern, or laying a kept tile out, during that 300 ms loses
 * the pattern on the kept tiles (inferred from the threshold matching the default).
 *
 * So the fade is taken out: every fill layer the style holds with a pattern, read from the style (not listed here), gets
 * `fill-pattern-transition` `{duration: 0, delay: 0}` at the top of the `setStyle` callback, before the night step
 * switches any pattern name. No other layer and no other property is touched; the style-wide transition stays as
 * `PlacementTransitions.kt` leaves it. One log line per style load.
 *
 * **It can go** once MapLibre fixes the fault upstream (the draft report is
 * `docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md`); the check is the same S22 colour count, run
 * with this call removed.
 *
 * Pure Kotlin: the live style is behind [PatternFadeTarget] (`PatternNoFadeApply.kt`), so the selection and the log are
 * asserted headless (`PatternNoFadeTest`).
 */
internal fun turnOffPatternFade(target: PatternFadeTarget, log: (String) -> Unit) {
    val layers = try {
        target.fillLayers().filter { it.pattern != null && it.pattern.isNotEmpty() }.map { it.layerId }
    } catch (e: RuntimeException) {
        log("Pattern fade: the style's layers could not be read (${e.javaClass.simpleName}: ${e.message}); the fade is left on.")
        return
    }
    if (layers.isEmpty()) {
        log("Pattern fade: the style has no pattern layers; nothing to change.")
        return
    }
    val failed = mutableListOf<String>()
    val done = layers.filter { id ->
        val ok = try {
            target.setPatternTransitionOff(id)
        } catch (e: RuntimeException) {
            failed += "$id (${e.javaClass.simpleName}: ${e.message})"
            return@filter false
        }
        if (!ok) failed += "$id (no such fill layer)"
        ok
    }
    val failedText = if (failed.isEmpty()) "" else "; could not set ${failed.joinToString()}"
    log("Pattern fade: fill-pattern-transition 0/0 on ${done.size} of ${layers.size} pattern layers $done$failedText.")
}

/** A fill layer as the style holds it: its fill-pattern as text (a name, or an expression's JSON), `null` when unset. */
internal data class FillLayerPattern(val layerId: String, val pattern: String?)

/** The loaded style as [turnOffPatternFade] sees it: the live style on a phone, a fake in the tests. */
internal interface PatternFadeTarget {
    /** Every fill layer in the style, in layer order, with its fill-pattern; [turnOffPatternFade] picks the ones that draw one. */
    fun fillLayers(): List<FillLayerPattern>

    /** Sets [layerId]'s fill-pattern-transition to duration 0, delay 0; `false` when there is no such fill layer. */
    fun setPatternTransitionOff(layerId: String): Boolean
}
