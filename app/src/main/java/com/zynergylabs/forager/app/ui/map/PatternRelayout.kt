package com.zynergylabs.forager.app.ui.map

/**
 * A workaround for a MapLibre Native 13.5.0 fault: map patterns (fill-pattern: Trail Map's tree glyphs and wetland tufts)
 * go missing on the tiles that survive a style reload (dispatch 2026-10-27-11, RECORD -1742; the cause is in
 * `docs/audits/2026-10-27-glyph-cause.md` on branch `glyph-logging`, `4d9a2eb64`). When the style reloads (a Night Maps
 * switch, by day or by night), MapLibre keeps the tiles already on screen at the held zoom and lays them out again;
 * those tiles have the features, the image and the right pattern name, and draw no pattern. Any fresh layout draws it.
 * On the S22, clearing each pattern layer to `""` and restoring its value brought the patterns back every time.
 *
 * So, once per style load, when both the `setStyle` callback has run ([onStyleLoaded]) and the style's sprite has
 * loaded ([onSpriteLoaded]), in either order: every fill layer the style holds with a pattern is read from the style (not
 * listed here), set to `""`, and set back to its read value once the map has rendered the cleared state ([RestoreTrigger]).
 * If another style is requested before the restore ([onStyleRequested]), nothing is restored: that style is gone.
 *
 * **It can go** once MapLibre fixes the fault upstream (the draft report is
 * `docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md`); the check that it is no longer needed is the
 * same S22 colour count, run with this class's call removed.
 *
 * Pure Kotlin: the live style and the map's frames are behind [PatternLayerTarget] and [RestoreTrigger]
 * (`PatternRelayoutApply.kt`), so every case is asserted headless (`PatternRelayoutTest`). One log line per style load
 * ([log]) and one per restore. Main thread only, as MapLibre's own callbacks are.
 */
internal class PatternRelayout<S : Any, V>(
    private val targetFor: (S) -> PatternLayerTarget<V>,
    private val trigger: RestoreTrigger,
    private val log: (String) -> Unit,
) {
    /** Which style request this is; a restore made for an older one restores nothing. */
    private var generation = 0
    private var loadedStyle: S? = null
    private var spriteLoaded = false
    private var relaidOut = false
    private var cancelPending: (() -> Unit)? = null

    /** A new style has been asked for (just before `setStyle`): the previous style's sprite and pending restore no longer count. */
    fun onStyleRequested() {
        generation++
        loadedStyle = null
        spriteLoaded = false
        relaidOut = false
        cancelPending?.let {
            it()
            cancelPending = null
            log("Pattern relayout: a new style was requested before the restore; nothing restored.")
        }
    }

    /** The `setStyle` callback has run for [style]. */
    fun onStyleLoaded(style: S) {
        loadedStyle = style
        runIfReady()
    }

    /** MapLibre reports the style's sprite loaded. */
    fun onSpriteLoaded() {
        spriteLoaded = true
        runIfReady()
    }

    /** MapLibre reports the style's sprite failed: no pattern can draw, so there is nothing to relay out. Logged, never silent. */
    fun onSpriteFailed(url: String?) {
        log("Pattern relayout: the sprite failed to load ($url); no relayout for this style.")
    }

    /** SCRATCH (never merged): defers the clear. */
    var scratchDefer: ((() -> Unit) -> Unit)? = null

    /** SCRATCH (never merged): a relayout now on [style], as the probe did. */
    fun scratchRunNow(style: S) {
        log("SCRATCH manual relayout")
        relayoutOn(style)
    }

    private fun runIfReady() {
        val style = loadedStyle ?: return
        if (!spriteLoaded || relaidOut) return
        relaidOut = true
        val defer = scratchDefer
        if (defer != null) {
            val g = generation
            defer { if (g == generation) relayoutOn(style) else log("SCRATCH deferred clear dropped") }
        } else {
            relayoutOn(style)
        }
    }

    private fun relayoutOn(style: S) {
        val target = targetFor(style)
        val layers = try {
            target.patternLayers()
        } catch (e: RuntimeException) {
            log("Pattern relayout: the style's layers could not be read (${e.javaClass.simpleName}: ${e.message}); no relayout.")
            return
        }
        if (layers.isEmpty()) {
            log("Pattern relayout: the style has no pattern layers; nothing to relay out.")
            return
        }
        val cleared = layers.filter { layer -> attempt("clear", layer.layerId) { target.clearPattern(layer.layerId) } }
        log("Pattern relayout: cleared ${cleared.size} of ${layers.size} pattern layers ${cleared.map { it.layerId }}; restoring after the next render.")
        if (cleared.isEmpty()) return
        val requestedFor = generation
        cancelPending = trigger.afterRendered { how ->
            cancelPending = null
            if (requestedFor != generation) {
                log("Pattern relayout: the style changed before the restore ($how); nothing restored.")
                return@afterRendered
            }
            val restored = cleared.filter { layer -> attempt("restore", layer.layerId) { target.restorePattern(layer.layerId, layer.value) } }
            log("Pattern relayout: restored ${restored.size} of ${cleared.size} pattern layers ${restored.map { "${it.layerId}=${it.text}" }} ($how).")
        }
    }

    /** Runs [step] on [layerId]; `false`, logged, when the layer is gone or MapLibre threw. */
    private fun attempt(what: String, layerId: String, step: () -> Boolean): Boolean {
        val done = try {
            step()
        } catch (e: RuntimeException) {
            log("Pattern relayout: could not $what $layerId (${e.javaClass.simpleName}: ${e.message}).")
            return false
        }
        if (!done) log("Pattern relayout: could not $what $layerId (no such fill layer).")
        return done
    }
}

/** A fill layer that draws a pattern, with its pattern as the style holds it ([value]) and as text for the log ([text]). */
internal data class PatternLayer<V>(val layerId: String, val value: V, val text: String)

/** The loaded style as the relayout sees it: the live style on a phone, a fake in the tests. */
internal interface PatternLayerTarget<V> {
    /** Every fill layer in the style whose fill-pattern is set (a name or an expression), in the style's layer order. */
    fun patternLayers(): List<PatternLayer<V>>

    /** Sets [layerId]'s fill-pattern to `""`; `false` when there is no such fill layer. */
    fun clearPattern(layerId: String): Boolean

    /** Sets [layerId]'s fill-pattern back to [value]; `false` when there is no such fill layer. */
    fun restorePattern(layerId: String, value: V): Boolean
}

/** When the restore runs: after the map has rendered the cleared patterns. */
internal fun interface RestoreTrigger {
    /** Calls [restore] once, later, with how the moment was reached (for the log); returns a cancel. */
    fun afterRendered(restore: (how: String) -> Unit): () -> Unit
}
