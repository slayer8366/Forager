package com.zynergylabs.forager.app.ui.theme

/**
 * The colours the map overlay draws its markers with, as `android.graphics` ARGB ints, in a day and a
 * night variant. `SightingsMap.kt` reads [forMode] for the Night Maps toggle, on every basemap
 * (colour build C2).
 *
 * ## One role per marker
 *
 * Each marker kind has its own role, so no two kinds share a colour: [waypoint], [find], [plannedTrip],
 * [photo], [keptTrack], [breadcrumb], the centre-pin picker's [centrePin], [searchCentre],
 * [offlineRegion], [sightingDot] and J8's [journalEntry] halo, plus the two rings drawn on the sighting dot
 * ([sightingDotStroke], [sightingDotStrokeSelected]), the [casing] every other marker is outlined
 * in, and the night border under the offline region's dashed outline ([offlineRegionBorder]). Before C2 the find pin shared the offline region's colour and the photo marker the planned
 * trip's; colour told them apart only by shape, and in the find pin's case not even that.
 *
 * ## Hand-authored, not derived
 *
 * Every value here is chosen, not computed from a colour scheme. The user-mark and system-mark colours
 * are the owner's picks from the swatch board (`docs/audits/2026-09-27-marker-swatch-board.md`: every
 * row's rank-1 candidate), searched there against the measured ground of both basemaps in both modes,
 * with night measured on the V1 night transform. Three are owner overrides rather than board
 * candidates: the sighting ring stays white and the selected ring stays `#2196F3`, in both modes; the
 * sighting dot is near black by day and a muted grey at night, and those exact greys are the planner's
 * picks. `MapPaletteTest` pins every role's measured figures against that ground as ratchets.
 *
 * MapLibre renders on a native canvas and cannot read a Compose `ColorScheme`, so `SightingsMap` takes
 * ints. They live here rather than beside the layer code so that a palette change is made in one place.
 *
 * ## Why this is keyed on a map mode, not on the app theme
 *
 * An earlier revision derived these from the ambient `ColorScheme`, so the map followed the device's
 * light/dark setting. That was built, measured and abandoned, because the basemap is raster and does
 * not follow the device theme: varying the marks by theme moved the mark without moving the ground.
 * Night Maps is a property of the map, and since colour build C1 it moves the ground too (the V1
 * inversion, `BasemapStyles.kt`'s `NIGHT_RASTER_PAINT`), which is what makes a night palette
 * meaningful.
 *
 * ## History
 *
 * The palette before C2 went through five night passes (a dimmed ground, warm-shifted marks, then two
 * shared colours with night-only icon shapes) and was then held day-only on the owner's instruction.
 * Those values and the reasoning for each pass are in this file's git history, and the removed
 * night-only icon path in `docs/plans/contrast_assertions.md`. None of it describes the palette now.
 */
data class MapPalette(
    val waypoint: Int,
    val find: Int,
    val plannedTrip: Int,
    val photo: Int,
    val keptTrack: Int,
    /** The live track being recorded, drawn dashed. */
    val breadcrumb: Int,
    /** The centre-pin picker's pin (`CentrePinLocationPicker.kt`), a Compose icon rather than a map layer. */
    val centrePin: Int,
    val searchCentre: Int,
    val offlineRegion: Int,
    /** The sighting dot's fill, drawn at 0.7 opacity. */
    val sightingDot: Int,
    /** The sighting ring: the dot's own casing, white in both modes (owner override). */
    val sightingDotStroke: Int,
    /**
     * The ring of whichever sighting dot has an
     * [com.zynergylabs.forager.app.ui.availability.ObservationBubble] open on it, so the bubble's arrow
     * has an unambiguous dot to point at in a dense cluster. `#2196F3` in both modes (owner override).
     */
    val sightingDotStrokeSelected: Int,
    /** The outline every marker other than the sighting dot is drawn in: white by day, black at night. */
    val casing: Int,
    /**
     * J8: the halo drawn beneath a record kept by an entry shown on the map (owner: "Highlight in
     * place"), one colour for every shown entry. Its halo sits against the record's own [casing], so
     * `MapPaletteTest` holds it to (c) against the casing like the other fills.
     */
    val journalEntry: Int,
    /**
     * The border under the offline region's dashed outline (dispatch `2026-09-28-79`): a solid line in
     * the track casing's pattern, so that at night the region's edge carries over the darkest ground,
     * where the black dashes alone measured 1.08:1 to 1.45:1 on the phone (Part 1, check 5 (b)). The
     * owner: "The outline should have a white border", and, for night only, "Yes the night outline
     * only". Its opacity is the registry's (`OFFLINE_REGION_BORDER_OPACITY`), not this colour's alpha,
     * except by day, when this colour is fully transparent so the layer draws nothing and the day
     * outline is exactly as it was. `MapPaletteTest` holds its figures against the night ground.
     */
    val offlineRegionBorder: Int,
) {
    companion object {

        val DAY = MapPalette(
            waypoint = 0xFF350560.toInt(),
            find = 0xFFDA02AF.toInt(),
            plannedTrip = 0xFF9553A4.toInt(),
            photo = 0xFFC1154F.toInt(),
            keptTrack = 0xFFA122F8.toInt(),
            breadcrumb = 0xFF650BB1.toInt(),
            centrePin = 0xFF7D0D5B.toInt(),
            searchCentre = 0xFF000000.toInt(),
            offlineRegion = 0xFF0B0B0B.toInt(),
            // The planner's pick under the owner's "a near black color" (planner ruling, planner-log line
            // 2488): #1F1F1F, its first pick, sat 0.090 from offlineRegion in Oklab, under the 0.10 floor.
            sightingDot = 0xFF2B2B2B.toInt(),
            sightingDotStroke = 0xFFFFFFFF.toInt(),
            sightingDotStrokeSelected = 0xFF2196F3.toInt(),
            casing = 0xFFFFFFFF.toInt(),
            // J8's halo: a deep blue-teal, proposed by the J8 coder and measured in MapPaletteTest
            // (see that test's class doc); the owner judges it on the phone.
            journalEntry = 0xFF005577.toInt(),
            // Night only (owner: "Yes the night outline only"): fully transparent, so it draws nothing.
            offlineRegionBorder = 0x00FFFFFF,
        )

        val NIGHT = MapPalette(
            waypoint = 0xFFB97DF7.toInt(),
            find = 0xFFF96FAC.toInt(),
            plannedTrip = 0xFFFA01DD.toInt(),
            photo = 0xFFE8046D.toInt(),
            keptTrack = 0xFFEEA7FE.toInt(),
            breadcrumb = 0xFFB228F8.toInt(),
            centrePin = 0xFFA656A0.toInt(),
            searchCentre = 0xFFDEDEDE.toInt(),
            // The owner's pick, 2026-09-28, drawn at the fill layer's 0.2 opacity like the day fill. It was
            // #FFFFFF, which read lighter than the night ground. Asked whether to darken it or lower its
            // opacity, the owner chose "Darker shade (Recommended)", then qualified it: "Not too dark
            // since it can make it harder to read. Find a balance." Black was dropped for that reason,
            // and the owner chose "You pick from phone shots (Recommended)". Three dark greys were built
            // and shot on the S22 at night, #202020, #404040 and #606060 (capture record
            // docs/audits/2026-09-28-night-region-candidates-capture-run-record.md). The owner picked
            // #202020, the darkest, after being told it nearly vanishes over the darkest night ground
            // (0.001 Oklab ΔE as drawn over #22201C, per MapPaletteTest): "That's my pick." Over that
            // ground only the dashed casing marks the edge; how well it does is unverified on a screen.
            offlineRegion = 0xFF202020.toInt(),
            // The owner's "a mute grey color"; the exact grey is the planner's pick.
            sightingDot = 0xFF8C8C8C.toInt(),
            sightingDotStroke = 0xFFFFFFFF.toInt(),
            sightingDotStrokeSelected = 0xFF2196F3.toInt(),
            casing = 0xFF000000.toInt(),
            // J8's halo at night: a cyan in the day colour's hue family (20.1° apart), measured the same way.
            journalEntry = 0xFF00DDFF.toInt(),
            // Owner: "The outline should have a white border". Opaque white here; drawn at the
            // registry's line opacity, 0.85, the coder's proposal, for the owner to judge on the phone.
            offlineRegionBorder = 0xFFFFFFFF.toInt(),
        )

        fun forMode(night: Boolean): MapPalette = if (night) NIGHT else DAY
    }
}
