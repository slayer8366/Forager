package com.zynergylabs.forager.app.ui.map

/**
 * A workaround for a MapLibre Native 13.5.0 fault: map patterns (fill-pattern: Trail Map's tree glyphs and wetland tufts)
 * go missing on the tiles that survive a style reload (dispatches 2026-10-27-11 and -13; RECORD -1742, -1746, -1748; the
 * cause is in `docs/audits/2026-10-27-glyph-cause.md` on branch `glyph-logging`, `4d9a2eb64`). When the style reloads (a
 * Night Maps switch, by day or by night), MapLibre keeps the tiles already on screen at the held zoom and lays them out
 * again; those tiles have the features, the image and the right pattern name, and draw no pattern. Any fresh layout
 * draws it. Clearing each pattern layer to `""` and restoring its value brings the patterns back, but only at the right
 * moment.
 *
 * **When, measured on the S22** (scratch builds 1.0.4103, RECORD -1748, and 1.0.4102+g0736420a, dispatch 2026-10-27-13),
 * with T0 the moment the `setStyle` callback has run and the sprite has loaded, in either order:
 * - a clear at T0 +0 to +250 ms fails (0 glyph pixels), and from +300 ms works (+300, +500, +2000 ms), by day and night;
 *   the threshold lies between +261 and +300 ms;
 * - a restore at the first full frame 2-7 ms after the clear can fail; a clear at +400 ms with the restore at the first
 *   full frame at least 100 ms after it worked 5 of 5;
 * - turning fill-pattern's own transition to 0 instead (no clear) brought the night patterns back 3 of 3 and left the day
 *   patterns at 0 3 of 3, so it is not the fix.
 * MapLibre 13.5.0 makes fill-pattern a cross-faded paint property that transitions (`fill_layer_properties.hpp:40`,
 * `properties.hpp:31-64`), and a style with no `transition` gets 300 ms (`parser.hpp:40`, `constants.hpp:59`), which
 * matches the threshold. That link is inferred, not shown.
 *
 * So, once per style load, at T0: the clear waits [clearDelayMs] (the style's transition, 300 ms when it reads 0, plus
 * [PATTERN_CLEAR_MARGIN_MS]); then every fill layer the style holds with a pattern is read from the style (not listed
 * here) and set to `""`; then each is set back to its read value at the first fully rendered frame at least
 * [PATTERN_RESTORE_MIN_GAP_MS] after the clear ([RestoreTrigger], with a timeout fallback). If another style is requested
 * at any point before the restore ([onStyleRequested]), the pending clear or restore is dropped: that style is gone.
 * Android reads an unset style transition as 0 (`native_map_view.cpp:920-930` at `android-v13.5.0`), so 0 stands for
 * "unset" here.
 *
 * **It can go** once MapLibre fixes the fault upstream (the draft report is
 * `docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md`); the check that it is no longer needed is the
 * same S22 colour count, run with this class's call removed.
 *
 * Pure Kotlin: the live style, the clock and the map's frames are behind [PatternLayerTarget], [Deferral] and
 * [RestoreTrigger] (`PatternRelayoutApply.kt`), so every case is asserted headless (`PatternRelayoutTest`). One log line
 * per style load ([log]), written when that load's relayout ends, however it ends. Main thread only, as MapLibre's own
 * callbacks are.
 */
internal class PatternRelayout<S : Any, V>(
    private val targetFor: (S) -> PatternLayerTarget<V>,
    private val deferral: Deferral,
    private val trigger: RestoreTrigger,
    private val log: (String) -> Unit,
) {
    /** Which style request this is; a clear or restore scheduled for an older one does nothing. */
    private var generation = 0
    private var loadedStyle: S? = null
    private var spriteLoaded = false
    private var relaidOut = false
    private var cancelPending: (() -> Unit)? = null

    /** What the pending step is waiting for, for the log line if a new style drops it. */
    private var pendingStep = ""

    /** A new style has been asked for (just before `setStyle`): the previous style's sprite and pending step no longer count. */
    fun onStyleRequested() {
        generation++
        loadedStyle = null
        spriteLoaded = false
        relaidOut = false
        cancelPending?.let {
            it()
            cancelPending = null
            log("Pattern relayout: a new style was requested $pendingStep; dropped.")
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

    private fun runIfReady() {
        val style = loadedStyle ?: return
        if (!spriteLoaded || relaidOut) return
        relaidOut = true
        val target = targetFor(style)
        val transition = try {
            target.transitionMs()
        } catch (e: RuntimeException) {
            log("Pattern relayout: the style's transition could not be read (${e.javaClass.simpleName}: ${e.message}); no relayout.")
            return
        }
        val delay = clearDelayMs(transition)
        val requestedFor = generation
        pendingStep = "during the $delay ms wait before the clear (nothing cleared)"
        cancelPending = deferral.after(delay) {
            cancelPending = null
            // The cancel in onStyleRequested stops this on a phone; the check holds even if a cancel were missed.
            clearThenRestore(target, requestedFor, "clear deferred $delay ms (style transition $transition ms)")
        }
    }

    private fun clearThenRestore(target: PatternLayerTarget<V>, requestedFor: Int, timing: String) {
        val layers = try {
            target.patternLayers()
        } catch (e: RuntimeException) {
            log("Pattern relayout: $timing; the style's layers could not be read (${e.javaClass.simpleName}: ${e.message}); no relayout.")
            return
        }
        if (layers.isEmpty()) {
            log("Pattern relayout: $timing; the style has no pattern layers; nothing to relay out.")
            return
        }
        val problems = mutableListOf<String>()
        val cleared = layers.filter { layer -> attempt("clear", layer.layerId, problems) { target.clearPattern(layer.layerId) } }
        val clearedText = "cleared ${cleared.size} of ${layers.size} ${cleared.map { it.layerId }}"
        if (cleared.isEmpty()) {
            log("Pattern relayout: $timing; $clearedText${problemsText(problems)}.")
            return
        }
        pendingStep = "before the restore (${cleared.size} layers left cleared; the new style brings its own)"
        cancelPending = trigger.afterRendered(PATTERN_RESTORE_MIN_GAP_MS) { how ->
            cancelPending = null
            if (requestedFor != generation) {
                log("Pattern relayout: $timing; $clearedText; the style changed before the restore ($how); nothing restored.")
                return@afterRendered
            }
            val restored = cleared.filter { layer -> attempt("restore", layer.layerId, problems) { target.restorePattern(layer.layerId, layer.value) } }
            log("Pattern relayout: $timing; $clearedText; restored ${restored.size} ${restored.map { "${it.layerId}=${it.text}" }} ($how)${problemsText(problems)}.")
        }
    }

    /** Runs [step] on [layerId]; `false`, noted in [problems] for the load's log line, when the layer is gone or MapLibre threw. */
    private fun attempt(what: String, layerId: String, problems: MutableList<String>, step: () -> Boolean): Boolean {
        val done = try {
            step()
        } catch (e: RuntimeException) {
            problems += "could not $what $layerId (${e.javaClass.simpleName}: ${e.message})"
            return false
        }
        if (!done) problems += "could not $what $layerId (no such fill layer)"
        return done
    }

    private fun problemsText(problems: List<String>) = if (problems.isEmpty()) "" else "; ${problems.joinToString("; ")}"
}

/** MapLibre's transition when a style sets none (`constants.hpp:59`, `DEFAULT_TRANSITION_DURATION`, at 13.5.0). */
internal const val MAPLIBRE_DEFAULT_TRANSITION_MS = 300L

/** How long after the style's transition the clear waits: the S22 worked from +300 ms, so 400 gives 100 ms of margin. */
internal const val PATTERN_CLEAR_MARGIN_MS = 100L

/** The least time between the clear and the restore: 2-7 ms could fail on the S22, 100 ms worked 5 of 5. */
internal const val PATTERN_RESTORE_MIN_GAP_MS = 100L

/**
 * The wait from T0 to the clear: the style's transition ([transitionMs], its duration plus its delay; 0 is taken as unset,
 * since Android reads an unset transition as 0, and becomes [MAPLIBRE_DEFAULT_TRANSITION_MS]) plus [PATTERN_CLEAR_MARGIN_MS].
 */
internal fun clearDelayMs(transitionMs: Long): Long =
    (if (transitionMs <= 0L) MAPLIBRE_DEFAULT_TRANSITION_MS else transitionMs) + PATTERN_CLEAR_MARGIN_MS

/** A fill layer that draws a pattern, with its pattern as the style holds it ([value]) and as text for the log ([text]). */
internal data class PatternLayer<V>(val layerId: String, val value: V, val text: String)

/** The loaded style as the relayout sees it: the live style on a phone, a fake in the tests. */
internal interface PatternLayerTarget<V> {
    /** The style's transition as Android reads it, duration plus delay, in ms (0 when the style sets none). */
    fun transitionMs(): Long

    /** Every fill layer in the style whose fill-pattern is set (a name or an expression), in the style's layer order. */
    fun patternLayers(): List<PatternLayer<V>>

    /** Sets [layerId]'s fill-pattern to `""`; `false` when there is no such fill layer. */
    fun clearPattern(layerId: String): Boolean

    /** Sets [layerId]'s fill-pattern back to [value]; `false` when there is no such fill layer. */
    fun restorePattern(layerId: String, value: V): Boolean
}

/** A wait on the main thread: the clock on a phone, a held list in the tests. */
internal fun interface Deferral {
    /** Calls [run] once, [ms] from now; returns a cancel. */
    fun after(ms: Long, run: () -> Unit): () -> Unit
}

/**
 * Which rendered frame the restore waits for: the first full frame at least [minGapMs] after [startMs]. Pure, so the gap
 * is asserted headless; the phone's [RestoreTrigger] (`PatternRelayoutApply.kt`) asks it of every frame MapLibre reports.
 */
internal class RestoreFrameGate(private val startMs: Long, private val minGapMs: Long) {
    /** Whether the frame finished at [nowMs], fully rendered or not ([fully]), is the one to restore at. */
    fun isRestoreFrame(fully: Boolean, nowMs: Long): Boolean = fully && nowMs - startMs >= minGapMs
}

/** When the restore runs: after the map has rendered the cleared patterns, at least a given time after the clear. */
internal fun interface RestoreTrigger {
    /** Calls [restore] once, at the first full frame at least [minGapMs] from now, with how it was reached (for the log); returns a cancel. */
    fun afterRendered(minGapMs: Long, restore: (how: String) -> Unit): () -> Unit
}
