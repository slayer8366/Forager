package com.zynergylabs.forager.app.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * An [EntryReferenceCountSource] a test sets by hand: each flow holds its current map, and assigning a new
 * one to [waypoints], [tracks], [offlineRegions] or [photos] is a journal entry change as the live counts see it.
 */
class FakeEntryReferenceCountSource(
    waypoints: Map<String, Int> = emptyMap(),
    tracks: Map<String, Int> = emptyMap(),
    offlineRegions: Map<Long, Int> = emptyMap(),
    photos: Map<String, Int> = emptyMap(),
) : EntryReferenceCountSource {
    val waypoints = MutableStateFlow(waypoints)
    val tracks = MutableStateFlow(tracks)
    val offlineRegions = MutableStateFlow(offlineRegions)
    val photos = MutableStateFlow(photos)

    override fun waypointCounts(): Flow<Map<String, Int>> = waypoints
    override fun trackCounts(): Flow<Map<String, Int>> = tracks
    override fun offlineRegionCounts(): Flow<Map<Long, Int>> = offlineRegions
    override fun photoCounts(): Flow<Map<String, Int>> = photos
}
