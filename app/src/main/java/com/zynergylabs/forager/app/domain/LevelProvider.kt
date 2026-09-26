package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * The phone's tilt for the camera's level line — the interface this project owns, per CLAUDE.md's
 * rule that a sensor is wrapped rather than depended on directly.
 *
 * Its own interface rather than a widening of [CompassProvider]: the compass exposes heading only
 * (`CompassProvider.heading`), is faked privately inside two screen tests, and the dispatch that
 * built the level said not to change it (2026-09-22).
 */
interface LevelProvider {
    /**
     * The phone's tilt about the axis through its screen, in degrees, **clockwise-positive as the
     * user looks at the screen**, `0` when held upright in its natural portrait hold, in
     * `(-180, 180]`. A quarter turn clockwise (top to the right) is `90`.
     *
     * The sensor is registered while this is collected and unregistered when collection stops, so
     * a listener lives exactly as long as whatever shows the level. Emits `null` once and stops on
     * a device with no sensor to read it from, rather than a flow that silently never emits.
     */
    val roll: Flow<Float?>
}
