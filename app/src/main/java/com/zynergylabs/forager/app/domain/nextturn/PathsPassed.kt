package com.zynergylabs.forager.app.domain.nextturn

/**
 * Which of the data's ways count as paths passed for "take the second left" (Amendment 1, B5). The owner, to "For \"take
 * the second left\", which side ways count as paths you pass?": **"Walkable ways only (Recommended)"**, the option
 * reading "Footpaths, trails, tracks and streets count. Driveways, parking-lot lanes and motorway slips don't, so a
 * driveway never makes it \"the second left\"."
 *
 * The planner's reading for the tags: every way the route data knows counts but `highway=service` (driveways, parking
 * aisles, alleys and access roads alike), `motorway`, `motorway_link`, `trunk` and `trunk_link`. The route data's own
 * lookups (`lookups.dat`) hold each of these as its own `highway` value, so they are told apart by the tag alone.
 *
 * Only the count: whether a junction is a turn (B1) still counts every way.
 */
internal fun countsAsPath(tags: Map<String, String>): Boolean = tags["highway"] !in NOT_PATHS

/** The `highway` values that are not paths passed (B5, the planner's reading). */
internal val NOT_PATHS: Set<String> = setOf("service", "motorway", "motorway_link", "trunk", "trunk_link")
