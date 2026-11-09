package com.zynergylabs.forager.app.ui.map.layers

import com.zynergylabs.forager.app.ui.theme.MapPalette

/**
 * The map layer model, as plain data: map layers L0a (`prompts/preserved/2026-09-27-29.md`, with the
 * owner's rulings in `prompts/preserved/2026-09-27-30.md`).
 *
 * Nothing in this package names a MapLibre or Android type, so all of it runs headless
 * (`MapLayerRegistryTest`, `MapLayerStateTest`, `TapPrecedenceTest`). `SightingsMap` is still the
 * only place a [MapLayerSpec] becomes a native layer: it walks [orderedLayers] and adds one native
 * layer per spec, in that order, so **the draw order is this registry's**, not the order of any
 * hand-written sequence of `addLayer` calls.
 *
 * Named `layers` rather than something broader: it describes the layers on top of the basemap and
 * their state, and nothing about the basemap itself (that stays `Basemap`/`BasemapStyles`).
 */

/** What a layer draws, as the owner's tap ruling and the layer sheet (L0b) talk about it. */
enum class LayerKind { COLOUR_FIELD, AREA, LINE, MARKER }

/**
 * The z-bands above the basemap, **bottom to top in declaration order**: colour fields < areas <
 * decorations < lines < markers. The ordinal is the order; [orderedLayers] and [registryProblems] both
 * read it, so reordering these constants reorders the map.
 *
 * Four bands came from the L0a dispatch, A1. [DECORATIONS] came from dispatch `2026-09-28-100`, on the
 * owner's ruling: "Option C: decorations to keep it separate. We can change it if the forecast layering
 * needs changes". It holds the J8 marker rings, kept separate from the lines band, where the J8 follow-ups
 * had put them at the bottom. It sits exactly there, so no layer moved on screen. The ruling and the
 * planner's reading of it are recorded in `docs/plans/journal-redesign.md`, "A decorations band for the rings".
 */
enum class ZGroup { COLOUR_FIELDS, AREAS, DECORATIONS, LINES, MARKERS }

/**
 * Which kind of native layer [SightingsMap][com.zynergylabs.forager.app.ui.map.SightingsMap] builds
 * for a spec. Separate from [LayerKind] because one kind has two renderers today (a marker is a
 * circle, the sighting dot, or a symbol, every bitmap marker), and each renderer has its own opacity
 * property ([OpacityProperty.renderer]).
 */
enum class LayerRenderer { FILL, LINE, CIRCLE, SYMBOL }

/**
 * The tap precedence the owner chose (L0 design rulings, 3: "Markers, then lines, then colour"):
 * lower [precedence] wins. [NONE] is a layer a tap never lands on: a casing (decoration under its
 * own track, which is itself tappable), and the offline region's fill (owner's ruling 4 on
 * 2026-09-27-30: the outline is tappable, taps on the fill fall through).
 */
enum class TapGroup(val precedence: Int?) {
    MARKER(0),
    LINE(1),
    COLOUR_FIELD(2),
    NONE(null),
}

/**
 * The [MapPalette] role a layer is drawn in. Each role reads the one [MapPalette] field of the same
 * name, so the registry names a colour without holding one. `MapLayerRegistryTest` checks every
 * current layer's role against the colour `SightingsMap` actually builds it with.
 */
enum class PaletteRole(val colourOf: (MapPalette) -> Int) {
    OFFLINE_REGION(MapPalette::offlineRegion),
    CASING(MapPalette::casing),
    SEARCH_CENTRE(MapPalette::searchCentre),
    SIGHTING_DOT(MapPalette::sightingDot),
    BREADCRUMB(MapPalette::breadcrumb),
    KEPT_TRACK(MapPalette::keptTrack),
    PLANNED_TRIP(MapPalette::plannedTrip),
    WAYPOINT(MapPalette::waypoint),
    FIND(MapPalette::find),
    PHOTO(MapPalette::photo),
    /** J8: the halo under a record kept by an entry shown on the map. */
    JOURNAL_ENTRY(MapPalette::journalEntry),
    /** The night border under the offline region's dashed outline (dispatch `2026-09-28-79`). */
    OFFLINE_REGION_BORDER(MapPalette::offlineRegionBorder),
}

/** An opacity paint property, named as MapLibre names it, and the renderer it belongs to. */
enum class OpacityProperty(val styleName: String, val renderer: LayerRenderer) {
    FILL("fill-opacity", LayerRenderer.FILL),
    LINE("line-opacity", LayerRenderer.LINE),
    CIRCLE("circle-opacity", LayerRenderer.CIRCLE),
    CIRCLE_STROKE("circle-stroke-opacity", LayerRenderer.CIRCLE),
    ICON("icon-opacity", LayerRenderer.SYMBOL),
}

/** One of a layer's opacity properties at the value it draws with when its opacity setting is 1. */
data class BaseOpacity(val property: OpacityProperty, val base: Float)

/**
 * One map layer, described without constructing it.
 *
 * - [userToggleable], [userOpacity] and [userReorderable] say what the Layers sheet (L0b) may offer
 *   for this layer. Nothing in L0a reads them to decide what is drawn: [layerPaintFor] applies
 *   whatever state it is given. Their values here are provisional and listed in the L0a completion
 *   report for L0b to confirm.
 * - [baseOpacities] are today's values: `SightingsMap` builds each layer with exactly these (at
 *   state opacity 1), so the registry is the one place a layer's opacity is written down.
 * - [stateOwnerId] names the layer whose visibility and opacity this one follows. A casing follows
 *   its own track and the offline region's outline follows its fill, because to a user they are one
 *   overlay; `null` means the layer has its own state.
 * - [credit] is the attribution text this layer adds to the map's caption while it is visible
 *   ([activeLayerCredits]). No current layer has one; the forecast layers (L0b and later) will.
 */
data class MapLayerSpec(
    val id: String,
    val kind: LayerKind,
    val renderer: LayerRenderer,
    val sourceId: String,
    val zGroup: ZGroup,
    /** `null` for a layer whose colours come from its own data (a colour field, L0b). */
    val paletteRole: PaletteRole?,
    val userToggleable: Boolean,
    val userOpacity: Boolean,
    val userReorderable: Boolean,
    val tapGroup: TapGroup,
    val baseOpacities: List<BaseOpacity>,
    val stateOwnerId: String? = null,
    val credit: String? = null,
    /**
     * J8: the record layer this one decorates (a halo beneath it), or `null`. A decoration is drawn
     * only while that layer is drawn as well as while its own governing state is visible (planner,
     * continuation `2026-09-28-53`, Q4: "A record whose own overlay is switched off gets no
     * highlight"), sits below that layer and takes no taps; [registryProblems] checks all three.
     */
    val drawnWith: String? = null,
)

/**
 * Every overlay layer id. Fixed strings, referenced from the registry, from `SightingsMap` and from
 * whatever receives a tap ([TapHit.layerId]), so a typo is a compile error, not a missing layer.
 * The values are the ones `SightingsMap` has always used.
 */
object MapLayerIds {
    const val OFFLINE_REGION_FILL = "offline-region-circles-layer"
    const val OFFLINE_REGION_OUTLINE = "offline-region-circles-outline-layer"

    // The night border under the offline outline's dashes (dispatch 2026-09-28-79).
    const val OFFLINE_REGION_BORDER = "offline-region-circles-border-layer"
    const val SEARCH_CENTRE = "search-center-layer"
    const val SIGHTINGS = "sightings-layer"
    const val BREADCRUMB_CASING = "breadcrumb-trail-casing-layer"
    const val BREADCRUMB = "breadcrumb-trail-layer"
    const val KEPT_TRACKS_CASING = "kept-tracks-casing-layer"
    const val KEPT_TRACKS = "kept-tracks-layer"
    const val PLANNED_TRIPS = "planned-trips-layer"
    const val WAYPOINTS = "waypoints-layer"
    const val FINDS = "find-markers-layer"
    const val PHOTOS = "photo-markers-layer"
    const val FORECAST_CHANTERELLES = "forecast-chanterelles-layer"
    const val FORECAST_CHICKEN_OF_THE_WOODS = "forecast-chicken-of-the-woods-layer"

    // J8: the halos under records kept by an entry shown on the map, one per record kind.
    const val JOURNAL_ENTRY_REGIONS = "journal-entry-regions-layer"
    const val JOURNAL_ENTRY_TRACKS = "journal-entry-tracks-layer"
    const val JOURNAL_ENTRY_WAYPOINTS = "journal-entry-waypoints-layer"
    const val JOURNAL_ENTRY_FINDS = "journal-entry-finds-layer"
    const val JOURNAL_ENTRY_PHOTOS = "journal-entry-photos-layer"
}

/** Every overlay GeoJSON source id, the values `SightingsMap` has always used. */
object MapSourceIds {
    const val OFFLINE_REGIONS = "offline-region-circles"
    const val SEARCH_CENTRE = "search-center"
    const val SIGHTINGS = "sightings"
    const val BREADCRUMB = "breadcrumb-trail"
    const val KEPT_TRACKS = "kept-tracks"
    const val PLANNED_TRIPS = "planned-trips"
    const val WAYPOINTS = "waypoints"
    const val FINDS = "find-markers"
    const val PHOTOS = "photo-markers"
    const val FORECAST_CHANTERELLES = "forecast-chanterelles"
    const val FORECAST_CHICKEN_OF_THE_WOODS = "forecast-chicken-of-the-woods"

    // J8: one source per halo layer, so a symbol layer is never fed a line.
    const val JOURNAL_ENTRY_REGIONS = "journal-entry-regions"
    const val JOURNAL_ENTRY_TRACKS = "journal-entry-tracks"
    const val JOURNAL_ENTRY_WAYPOINTS = "journal-entry-waypoints"
    const val JOURNAL_ENTRY_FINDS = "journal-entry-finds"
    const val JOURNAL_ENTRY_PHOTOS = "journal-entry-photos"
}

/**
 * J8: the layer whose state is the Layers sheet's "Journal entries" switch. The other four halos
 * follow it ([MapLayerSpec.stateOwnerId]), so the one switch hides or shows every highlight together,
 * and its stored choice persists by this id like every other overlay switch.
 */
const val JOURNAL_ENTRIES_SWITCH_LAYER_ID = MapLayerIds.JOURNAL_ENTRY_TRACKS

private val LINE_OPACITY = listOf(BaseOpacity(OpacityProperty.LINE, 1f))
private val ICON_OPACITY = listOf(BaseOpacity(OpacityProperty.ICON, 1f))

/** The offline region's fill opacity, today's value (Journal Stage 2d). */
internal const val OFFLINE_REGION_FILL_OPACITY = 0.2f

/**
 * The line opacity of the border under the offline region's dashed outline (dispatch `2026-09-28-79`),
 * white at night. The coder's proposal, for the owner to judge on the phone: the one white stroke the
 * night map already drew is the sighting ring, at [SIGHTING_DOT_STROKE_OPACITY], 0.85, and this matches
 * it. `MapPaletteTest` holds the figures at this value: the dispatch's target, 3:1 against Part 1's
 * darkest night ground on both sides of the edge, as a bar, and a ratchet per night ground cluster (over
 * the darkest, `#020302`, 14.636:1 outside the edge and 14.267:1 inside; the black dashes 14.877:1 or
 * more against the border on every cluster). By day the border's colour is fully transparent, so this
 * value draws nothing.
 */
internal const val OFFLINE_REGION_BORDER_OPACITY = 0.85f

/** The sighting dot's fill opacity, today's value: about the deleted osmdroid version's 0xB3 alpha. */
private const val SIGHTING_DOT_OPACITY = 0.7f

/**
 * The sighting dot's ring opacity, today's value: a light, near-opaque stroke (not translucent like
 * the fill) so each dot's boundary stays crisp in a dense cluster.
 */
private const val SIGHTING_DOT_STROKE_OPACITY = 0.85f

private fun marker(id: String, sourceId: String, role: PaletteRole, toggleable: Boolean, tapGroup: TapGroup = TapGroup.MARKER) = MapLayerSpec(
    id = id,
    kind = LayerKind.MARKER,
    renderer = LayerRenderer.SYMBOL,
    sourceId = sourceId,
    zGroup = ZGroup.MARKERS,
    paletteRole = role,
    userToggleable = toggleable,
    userOpacity = false,
    userReorderable = false,
    tapGroup = tapGroup,
    baseOpacities = ICON_OPACITY,
)

private fun line(id: String, sourceId: String, role: PaletteRole, tapGroup: TapGroup, owner: String?) = MapLayerSpec(
    id = id,
    kind = LayerKind.LINE,
    renderer = LayerRenderer.LINE,
    sourceId = sourceId,
    zGroup = ZGroup.LINES,
    paletteRole = role,
    // A layer that follows another's state is not offered on its own.
    userToggleable = owner == null,
    userOpacity = false,
    userReorderable = false,
    tapGroup = tapGroup,
    baseOpacities = LINE_OPACITY,
    stateOwnerId = owner,
)

/**
 * J8: a journal-entry halo under [decorates], in [PaletteRole.JOURNAL_ENTRY], taking no taps, and
 * following the "Journal entries" switch ([JOURNAL_ENTRIES_SWITCH_LAYER_ID]); the switch's own layer
 * is the one halo not following another. See [MapLayerSpec.drawnWith].
 *
 * A halo is in [zGroup]. A line's halo stays in the lines band, directly beneath its own line's casing,
 * which is the default. The three marker rings are in [ZGroup.DECORATIONS] (dispatch `2026-09-28-100`; the
 * owner, "Option C"), the band just below the lines, so no ring covers a line (J8 follow-ups, continuation
 * `2026-09-28-87`, item 4; the owner, "1 A", to "Move marker rings below all lines"). A ring is still a
 * symbol layer below its own marker; a ring left in the markers band could not sit below a line.
 */
private fun journalHalo(
    id: String,
    sourceId: String,
    kind: LayerKind,
    renderer: LayerRenderer,
    decorates: String,
    zGroup: ZGroup = ZGroup.LINES,
): MapLayerSpec {
    val switch = id == JOURNAL_ENTRIES_SWITCH_LAYER_ID
    return MapLayerSpec(
        id = id,
        kind = kind,
        renderer = renderer,
        sourceId = sourceId,
        zGroup = zGroup,
        paletteRole = PaletteRole.JOURNAL_ENTRY,
        userToggleable = switch,
        userOpacity = false,
        userReorderable = false,
        tapGroup = TapGroup.NONE,
        baseOpacities = if (renderer == LayerRenderer.SYMBOL) ICON_OPACITY else LINE_OPACITY,
        stateOwnerId = if (switch) null else JOURNAL_ENTRIES_SWITCH_LAYER_ID,
        drawnWith = decorates,
    )
}

/**
 * A colour field's registry entry (map layers L0b, B7): switchable, with an opacity slider, reorderable,
 * credited while visible, and tappable in [TapGroup.COLOUR_FIELD] since M1 (the cell readout bubble).
 * L0b left it untappable because a tappable cell under nearly every tap kept `resolveTap` from reaching
 * its box stage for a near miss on a line or marker; M1's `resolveTap` holds cells back until both
 * stages find nothing else, so that rule still stands. Its colours come from its own data, through
 * [ColourFieldSpec.ramp], so no palette role.
 *
 * Base fill opacity [COLOUR_FIELD_FILL_OPACITY], the value L0a's own tests used for a colour field
 * (`TapPrecedenceTest`, `MapLayerStateTest`): at the default 100% the basemap still shows through, and two
 * stacked fields both read. The slider multiplies it, as it does every layer's base (L0a ruling 3).
 */
private fun colourField(field: ColourFieldSpec) = MapLayerSpec(
    id = field.layerId,
    kind = LayerKind.COLOUR_FIELD,
    renderer = LayerRenderer.FILL,
    sourceId = field.sourceId,
    zGroup = ZGroup.COLOUR_FIELDS,
    paletteRole = null,
    userToggleable = true,
    userOpacity = true,
    userReorderable = true,
    tapGroup = TapGroup.COLOUR_FIELD,
    baseOpacities = listOf(BaseOpacity(OpacityProperty.FILL, COLOUR_FIELD_FILL_OPACITY)),
    credit = SYNTHETIC_DATA_CREDIT,
)

/** A colour field's base fill opacity; see [colourField]. */
private const val COLOUR_FIELD_FILL_OPACITY = 0.6f

/**
 * Today's overlay layers, bottom to top. The colour-field group holds L0b's two synthetic test layers
 * ([COLOUR_FIELDS], in that list's order: chanterelles drawn on top by default), below every other layer,
 * so a cell never covers a marker, a line or an offline region. A release build lists them too, as data,
 * but its store never has data for them, so they are hidden in every state a map is given
 * (`withUnavailableColourFieldsHidden`) and never offered, drawn or credited.
 *
 * Within each group the order is the one `SightingsMap` drew before L0a, and the reasons recorded
 * there still hold: the offline region's fill is lowest, so a coverage circle never covers a
 * marker; each casing is directly below its own track; planned trips, then waypoints, then finds
 * and photos, so a pin never sits under a sibling marker at the same point.
 *
 * **One visible change, accepted by the owner** (ruling 1 on `prompts/preserved/2026-09-27-30.md`,
 * "Accept: markers above lines"): the search centre and the sighting dots used to be added between
 * the offline outline and the breadcrumb casing, so every track line drew over them. As markers
 * they now draw above every line, the convention point markers follow elsewhere, and the order a
 * finger wins in ([tapWinner]) is now the order things are drawn in.
 *
 * `userToggleable` follows the owner's overlay list for the Layers sheet (layer ruling 3: finds,
 * waypoints, tracks, planned trips, offline maps, journal entries); photos are counted with finds as
 * part of an entry.
 *
 * **J8's five journal-entry halos** (`prompts/preserved/2026-09-28-52.md`, J8-2) each sit below the
 * record layer they decorate, so the halo shows as a ring around the record's own outline. The two line
 * halos sit directly below their record's casing: the offline region's under its outline's border, the
 * kept tracks' under their casing, both in the lines band. The three marker rings (waypoints, finds,
 * photos) are the decorations band, below every line (J8 follow-ups, continuation `2026-09-28-87`, item 4;
 * the owner, "1 A", to "Move marker rings below all lines"; the band itself from dispatch `2026-09-28-100`,
 * the owner, "Option C"), so a ring never covers a track, the offline outline or any other line. They were
 * at the bottom of the lines band first, and the band put them exactly where they already drew. J8 had
 * put each directly under its own marker, above every line and the search-centre
 * reticle, and continuation `-87` reports rings covering parts of a kept track and of the reticle. A
 * halo takes no taps, so M1's tap
 * routing is unchanged, and it is drawn only while its record's own switch and the "Journal entries"
 * switch are both on ([MapLayerSpec.drawnWith]). The sighting dots and the search centre are not in that list, so they are not
 * toggleable here. `userOpacity` is offered for colour fields only (layer ruling 1: "each with its
 * own opacity slider"); the multiplier itself applies to any layer. Both are for L0b to confirm.
 *
 * **The offline outline's border** (`prompts/preserved/2026-09-28-79.md`; owner: "The outline should
 * have a white border", "Yes the night outline only") sits directly below the outline, as a casing sits
 * below its track, and follows the fill's switch like the outline. It takes no taps, so what a tap
 * reaches is unchanged. Its role is white at night and fully transparent by day, so by day it draws
 * nothing and only the night map changes.
 */
val MAP_LAYER_REGISTRY: List<MapLayerSpec> = COLOUR_FIELDS.map(::colourField) + listOf(
    MapLayerSpec(
        id = MapLayerIds.OFFLINE_REGION_FILL,
        kind = LayerKind.AREA,
        renderer = LayerRenderer.FILL,
        sourceId = MapSourceIds.OFFLINE_REGIONS,
        zGroup = ZGroup.AREAS,
        paletteRole = PaletteRole.OFFLINE_REGION,
        userToggleable = true,
        userOpacity = false,
        userReorderable = false,
        // Owner's ruling 4: taps on the fill fall through; the outline is what a finger hits.
        tapGroup = TapGroup.NONE,
        baseOpacities = listOf(BaseOpacity(OpacityProperty.FILL, OFFLINE_REGION_FILL_OPACITY)),
    ),
    // J8 follow-ups, item 4 (the owner, "1 A"): the three marker rings, below every line, in the
    // decorations band (dispatch 2026-09-28-100; the owner, "Option C").
    journalHalo(MapLayerIds.JOURNAL_ENTRY_WAYPOINTS, MapSourceIds.JOURNAL_ENTRY_WAYPOINTS, LayerKind.MARKER, LayerRenderer.SYMBOL, decorates = MapLayerIds.WAYPOINTS, zGroup = ZGroup.DECORATIONS),
    journalHalo(MapLayerIds.JOURNAL_ENTRY_FINDS, MapSourceIds.JOURNAL_ENTRY_FINDS, LayerKind.MARKER, LayerRenderer.SYMBOL, decorates = MapLayerIds.FINDS, zGroup = ZGroup.DECORATIONS),
    journalHalo(MapLayerIds.JOURNAL_ENTRY_PHOTOS, MapSourceIds.JOURNAL_ENTRY_PHOTOS, LayerKind.MARKER, LayerRenderer.SYMBOL, decorates = MapLayerIds.PHOTOS, zGroup = ZGroup.DECORATIONS),
    journalHalo(MapLayerIds.JOURNAL_ENTRY_REGIONS, MapSourceIds.JOURNAL_ENTRY_REGIONS, LayerKind.LINE, LayerRenderer.LINE, decorates = MapLayerIds.OFFLINE_REGION_OUTLINE),
    line(MapLayerIds.OFFLINE_REGION_BORDER, MapSourceIds.OFFLINE_REGIONS, PaletteRole.OFFLINE_REGION_BORDER, TapGroup.NONE, owner = MapLayerIds.OFFLINE_REGION_FILL)
        .copy(baseOpacities = listOf(BaseOpacity(OpacityProperty.LINE, OFFLINE_REGION_BORDER_OPACITY))),
    line(MapLayerIds.OFFLINE_REGION_OUTLINE, MapSourceIds.OFFLINE_REGIONS, PaletteRole.CASING, TapGroup.LINE, owner = MapLayerIds.OFFLINE_REGION_FILL),
    line(MapLayerIds.BREADCRUMB_CASING, MapSourceIds.BREADCRUMB, PaletteRole.CASING, TapGroup.NONE, owner = MapLayerIds.BREADCRUMB),
    // M1 (owner's ruling 4, "Not tappable"): the recording trail is not a record, so it takes no taps.
    line(MapLayerIds.BREADCRUMB, MapSourceIds.BREADCRUMB, PaletteRole.BREADCRUMB, TapGroup.NONE, owner = null),
    journalHalo(MapLayerIds.JOURNAL_ENTRY_TRACKS, MapSourceIds.JOURNAL_ENTRY_TRACKS, LayerKind.LINE, LayerRenderer.LINE, decorates = MapLayerIds.KEPT_TRACKS),
    line(MapLayerIds.KEPT_TRACKS_CASING, MapSourceIds.KEPT_TRACKS, PaletteRole.CASING, TapGroup.NONE, owner = MapLayerIds.KEPT_TRACKS),
    line(MapLayerIds.KEPT_TRACKS, MapSourceIds.KEPT_TRACKS, PaletteRole.KEPT_TRACK, TapGroup.LINE, owner = null),
    // M1 (owner's ruling 4, "Not tappable"): the search-centre reticle is not a record either.
    marker(MapLayerIds.SEARCH_CENTRE, MapSourceIds.SEARCH_CENTRE, PaletteRole.SEARCH_CENTRE, toggleable = false, tapGroup = TapGroup.NONE),
    MapLayerSpec(
        id = MapLayerIds.SIGHTINGS,
        kind = LayerKind.MARKER,
        renderer = LayerRenderer.CIRCLE,
        sourceId = MapSourceIds.SIGHTINGS,
        zGroup = ZGroup.MARKERS,
        paletteRole = PaletteRole.SIGHTING_DOT,
        userToggleable = false,
        userOpacity = false,
        userReorderable = false,
        tapGroup = TapGroup.MARKER,
        baseOpacities = listOf(
            BaseOpacity(OpacityProperty.CIRCLE, SIGHTING_DOT_OPACITY),
            BaseOpacity(OpacityProperty.CIRCLE_STROKE, SIGHTING_DOT_STROKE_OPACITY),
        ),
    ),
    marker(MapLayerIds.PLANNED_TRIPS, MapSourceIds.PLANNED_TRIPS, PaletteRole.PLANNED_TRIP, toggleable = true),
    marker(MapLayerIds.WAYPOINTS, MapSourceIds.WAYPOINTS, PaletteRole.WAYPOINT, toggleable = true),
    marker(MapLayerIds.FINDS, MapSourceIds.FINDS, PaletteRole.FIND, toggleable = true),
    marker(MapLayerIds.PHOTOS, MapSourceIds.PHOTOS, PaletteRole.PHOTO, toggleable = true),
)

/**
 * What is wrong with [registry] as a registry, one line per problem; empty when nothing is. Checks
 * that ids are unique, that the groups run bottom to top in [ZGroup] order, that every
 * [MapLayerSpec.stateOwnerId] names another layer that owns its own state, that only colour
 * fields are reorderable, and that every base opacity belongs to its layer's renderer. Since J8, also
 * that every [MapLayerSpec.drawnWith] names a layer in the registry listed above the decoration, and
 * that a decoration takes no taps.
 */
fun registryProblems(registry: List<MapLayerSpec>): List<String> = buildList {
    registry.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys.forEach {
        add("layer id $it appears more than once")
    }
    registry.zipWithNext().forEach { (lower, upper) ->
        if (upper.zGroup.ordinal < lower.zGroup.ordinal) {
            add("${upper.id} (${upper.zGroup}) is listed after ${lower.id} (${lower.zGroup}), a group it belongs below")
        }
    }
    val byId = registry.associateBy { it.id }
    registry.forEach { spec ->
        spec.stateOwnerId?.let { ownerId ->
            val owner = byId[ownerId]
            when {
                owner == null -> add("${spec.id} follows $ownerId, which is not in the registry")
                owner.stateOwnerId != null -> add("${spec.id} follows $ownerId, which itself follows ${owner.stateOwnerId}")
            }
        }
        if (spec.userReorderable && spec.zGroup != ZGroup.COLOUR_FIELDS) {
            add("${spec.id} is reorderable but is in ${spec.zGroup}; only colour fields are")
        }
        spec.baseOpacities.filter { it.property.renderer != spec.renderer }.forEach {
            add("${spec.id} is a ${spec.renderer} layer but sets ${it.property.styleName}")
        }
        spec.drawnWith?.let { decoratedId ->
            val decoratedIndex = registry.indexOfFirst { it.id == decoratedId }
            when {
                decoratedIndex < 0 -> add("${spec.id} is drawn with $decoratedId, which is not in the registry")
                decoratedIndex < registry.indexOfFirst { it.id == spec.id } ->
                    add("${spec.id} decorates $decoratedId but is listed above it; a decoration belongs below what it decorates")
            }
            if (spec.tapGroup != TapGroup.NONE) add("${spec.id} decorates $decoratedId but takes taps (${spec.tapGroup}); a decoration takes none")
        }
    }
}

/**
 * [registry] in draw order, bottom to top: the groups in [ZGroup] order and, inside a group whose
 * layers are reorderable, the order [state] holds ([MapLayersState.reorderableOrder]); every other
 * layer keeps its registry position within its group.
 */
fun orderedLayers(registry: List<MapLayerSpec>, state: MapLayersState): List<MapLayerSpec> {
    val userRank = state.reorderableOrder.withIndex().associate { it.value to it.index }
    return ZGroup.entries.flatMap { group ->
        val members = registry.filter { it.zGroup == group }
        val (reorderable, fixed) = members.partition { it.userReorderable }
        if (reorderable.isEmpty()) {
            members
        } else {
            // sortedBy is stable, so layers the stored order does not name keep their registry order.
            reorderable.sortedBy { userRank[it.id] ?: Int.MAX_VALUE } + fixed
        }
    }
}
