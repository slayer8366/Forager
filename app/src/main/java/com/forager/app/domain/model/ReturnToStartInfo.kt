package com.forager.app.domain.model

/**
 * What the return-to-vehicle/return-to-start screen (Phase 1c) shows: bearing, straight-line
 * distance, and elevation difference back to a track's start point. Deliberately **not** an ETA —
 * a time estimate over a straight line across terrain this app has no data for is exactly the
 * fabricated-plausible-value failure CLAUDE.md forbids, per the plan's own explicit cut of this
 * feature. [bearingDegrees] is true-north; [com.forager.app.domain.CompassProvider.heading] is
 * magnetic-north-relative, and reconciling the two is
 * [com.forager.app.domain.ComputeTrueHeadingUseCase]'s job (HUD-foundations dispatch, Item 2) —
 * this used to say "a UI-layer concern", and nothing in the UI ever did it.
 *
 * [elevationDifferenceMeters] is `start altitude - current altitude`: positive means the start
 * point is higher than where you are now. `null` whenever either point lacks a reported altitude,
 * same "unsupported, not guessed" rule every other altitude field in this app follows.
 */
data class ReturnToStartInfo(
    val bearingDegrees: Double,
    val distanceMeters: Double,
    val elevationDifferenceMeters: Double?,
)
