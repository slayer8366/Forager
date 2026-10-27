// Forager's own Street design, as edits to Protomaps' light layers (dispatch 2026-10-18-05 Part 2).
//
// The owner's rulings this carries, each quoted at its RECORD entry on records-after-173:
//   -1284 paths: "dashed red-brown footpaths, dash-dot brown vehicle tracks, dotted faint informal paths,
//         all visible from the zoom where a trailhead fits on screen";
//   -1285 forest: "one solid green ... from the state view down", grass and scrub lighter, parks as an outline;
//   -1286 labels: "a little bigger than ... forager-light.json ... near-black on a light halo";
//   -1287 points of interest: the outdoor list from mid zoom, shops and cafes only zoomed into a town;
//   -1288 night: "pure inversion ... the day palette is chosen so roads and paths still stand out after
//         inversion" (the app inverts; this file only picks the day colours);
// as limited by -1300, the owner's acceptance of the Part 1 report
// (docs/audits/2026-10-18-street-cloudflare-part1-report.md, Q1.4):
//   (4) paths from z13 (the tiles carry none at z12), outdoor places at the zooms the tiles carry them;
//   (5) a dot and the name for the six kinds the icon set has no icon for.
// Trail difficulty, visibility and informal paths (dispatch 2026-10-19-41, RECORD -1480): Sage Wayfinder's design
// as the owner ruled it, from the own build's `sac_scale`, `trail_visibility` and `informal` fields. The spec is
// prompts/preserved/2026-10-20-trail-design-handover-rowan.md on pi-build; the rulings are in
// docs/audits/2026-10-20-cloudflare-upload-prep.md and docs/audits/2026-10-20-sage-01-trail-variants.md there.
// Archives without those fields draw every footpath as the unknown group.
//
// Why a separate file and not overrides/forager-colours.json: that file can only rename colours and hide
// layers (generate.mjs:17-50). Dashes, zoom ranges, filters, sizes and a new outline layer are layer edits.
// Every edit names the layer it changes and fails loudly if that layer is missing, like `hide` does.

/** Day colours, each chosen with its V1 night value beside it (night = c + 1 - 2 * mean(r, g, b), per channel). */
export const COLOURS = {
  // Paths: night #ed8a6f on the #24211c night ground (6.5:1), well apart from the find pin's magenta.
  footpath: "#a8452a",
  // Tracks: a browner, darker line than footpaths; night #cfaa85 (7.4:1).
  track: "#7a5530",
  // Informal paths (-1284), `informal == "yes"`: Sage's revision 1 colour, replacing the reserved #b9876b. By day
  // 3.10:1 on ground, 2.45:1 on forest. Its inversion (#9b694d) nearly vanishes on the night forest, so it has its
  // own night colour, informalPathNight.
  informalPath: "#a0735a",
  // Informal paths at night (the owner: "Own night colour (Recommended)", under -1344): 2.01:1 on the night forest,
  // inside the owner's band and below the night footpath (NIGHT_LAYER_BANDS).
  informalPathNight: "#bb8a6f",
  // Hard trails (alpine_hiking and above), darker than footpaths: a warning colour. Night is the plain inversion,
  // #ffad9f.
  hardPath: "#7a1e10",
  // Tree cover, one green at every zoom (-1285). Night #436d35.
  forest: "#a9d39b",
  // Grass, scrub, meadow: lighter than forest by day. Night #2d3911.
  grass: "#dde9c1",
  // Park and protected-area outline (-1285): a dark green line; night #9cd898.
  parkOutline: "#3e7a3a",
  // Roads: coloured fills with darker casings, so both survive inversion (Elder's night finding).
  highway: "#f0b95c",
  highwayCasing: "#a8782c",
  major: "#f6d68f",
  majorCasing: "#b4924c",
  minor: "#ffffff",
  minorCasing: "#9c9486",
  service: "#f4f0e6",
  serviceCasing: "#aaa294",
  // Water at night, every fill and line, streams and rivers included: #163f4b, the owner's dark teal (night-colours
  // record, "Darker water fill ... it lights up the display", then the round-5 pair and "Same as water"), in place of
  // -1343's #8ec5f0. Day water is Protomaps' own #80deea, unchanged. The app draws it (NightColour.kt,
  // STREET_NIGHT_WATER); this is its design value, recorded in the style's metadata and held equal to the app's by
  // NightWaterTest. Held by the ruled ceiling (luminance 0.050, blue 96) and 1.05:1 on every night land fill
  // (tools/check-street-night.mjs, NIGHT_LIMITS in overrides/forager-character.mjs): 1.08:1 on night grass at least.
  nightWater: "#163f4b",
  // Labels: near-black on a light halo (-1286); at night they invert to near-white on near-black.
  text: "#262626",
  waterText: "#1f4d86",
  halo: "#ffffff",
};

/**
 * Night exceptions by layer (RECORD -1345; -1344: "we can go outside the purity for the UX"): the night colour the app
 * draws for these layers' line colour instead of the inversion, which read at 1.0 to 1.5:1 on the night forest
 * (Part 1 report, round 1). Tunnels keep the inversion, as they keep their grey by day. The app holds its own copy
 * (NightColour.kt, STREET_NIGHT_LAYER_COLOURS), checked equal to this one through the style's metadata.
 */
export const NIGHT_LAYER_COLOURS = {
  roads_minor_casing: "#c4bcae", // minor road edge
  roads_bridges_minor_casing: "#c4bcae",
  roads_minor_service_casing: "#b3ab9d", // service road edge
  roads_major: "#e0ad4f", // major road centre (links take the major palette)
  roads_bridges_major: "#e0ad4f",
  roads_link: "#e0ad4f",
  roads_bridges_link: "#e0ad4f",
  roads_highway: "#f0c060", // highway centre
  roads_bridges_highway: "#f0c060",
  roads_rail: "#b4bec0", // railways
  boundaries: "#bdbdbd",
  boundaries_country: "#bdbdbd",
  // Informal paths (dispatch 2026-10-19-41): their own night colour, held to NIGHT_LAYER_BANDS below, not to 2.5:1.
  roads_path_informal_z13: COLOURS.informalPathNight,
  roads_path_informal: COLOURS.informalPathNight,
};

/**
 * Per-layer contrast bands on a night ground, in place of the 2.5:1 floor for that layer on that ground only
 * (tools/check-street-night.mjs). Every layer and ground not named here keeps the floor.
 *
 * Informal paths on the night forest: the owner, "Accept the range (Recommended)" (docs/audits/2026-10-20-cloudflare-
 * upload-prep.md on pi-build, "The owner's rulings on revision 1"): 1.9 to 2.3:1, and below the night footpath's own
 * contrast there (2.43:1), so an informal path still reads fainter than a footpath. A band, not a widened floor.
 */
export const NIGHT_LAYER_BANDS = {
  roads_path_informal_z13: { forest: { min: 1.9, max: 2.3, belowNightOf: "footpath" } },
  roads_path_informal: { forest: { min: 1.9, max: 2.3, belowNightOf: "footpath" } },
};

/**
 * Night exceptions for icon rings (-1345): the ring's own night colour, the rest of the icon inverted. `ring` and
 * `fill` are the day colours the sprite draws the ring and its inside with (read from the pinned sprite).
 */
export const NIGHT_ICON_RINGS = {
  townspot: { ring: "#868686", fill: "#f3f3f3", night: "#d6d6d6" }, // the place dot
  toilets: { ring: "#8780ab", fill: "#dcebfa", night: "#c6bfea" },
  drinking_water: { ring: "#8780ab", fill: "#dcebfa", night: "#c6bfea" },
};

/**
 * The design's outdoor list (-1287), in the tiles' own kind names (Part 1, Q1.3); saddles, shelters and huts joined it
 * with the character rulings (ruling 4 (e), dispatch 2026-10-22-03).
 */
export const OUTDOOR_KINDS = [
  "trailhead", "parking", "camp_site", "picnic_site", "viewpoint", "peak", "toilets", "drinking_water", "ranger_station",
  "saddle", "shelter", "alpine_hut", "wilderness_hut",
];
/**
 * Outdoor kinds no sprite image draws: a dot and the name (-1300 (5)). The six -1300 put here have their own icons
 * since sprite v5 (ruling 6, ICON_BY_KIND); ruling 4 (e)'s four kinds have none, so they take the dot.
 */
export const DOT_KINDS = ["saddle", "shelter", "alpine_hut", "wilderness_hut"];
/** Ruling 6: the sprite v5 icon for each kind whose icon is not named after it (trailhead_b, the walker; viewpoint_c). */
export const ICON_BY_KIND = { trailhead: "trailhead_b", viewpoint: "viewpoint_c" };
/** Shops and cafes, shown only zoomed into a town (-1287). */
export const TOWN_KINDS = [
  "restaurant", "fast_food", "cafe", "bar", "supermarket", "convenience", "books", "clothes", "beauty", "electronics",
];
/** "From mid zoom" (-1287): the outdoor list's floor. The tiles carry some kinds only from z15 (-1300 (4)). */
export const OUTDOOR_MIN_ZOOM = 12;
/** "Only when zoomed into a town" (-1287). */
export const TOWN_MIN_ZOOM = 16;
/** "From the zoom where a trailhead fits on screen" (-1284), limited by the tiles to z13 (-1300 (4)). */
export const PATH_MIN_ZOOM = 13;
/**
 * Tracks start a zoom before footpaths: the tiles carry them from z12 (RECORD -1506, the owner: "Style now, data
 * later"). Footpaths stay at PATH_MIN_ZOOM, because the tiles carry them only from z13 until a tile rebuild.
 */
export const TRACK_MIN_ZOOM = 12;
/**
 * Minor-road, track and trail names from z14, where the tiles carry them (RECORD -1506, the owner: "From zoom 14").
 * The pinned Protomaps layer starts them at z15.
 */
export const MINOR_ROAD_LABEL_MIN_ZOOM = 14;

const FOOT_DETAILS = ["footway", "path", "steps", "bridleway", "cycleway"];
const PARK_KINDS = ["national_park", "park", "protected_area", "nature_reserve"];
const TREE_KINDS = ["forest", "wood"];
const GRASS_KINDS = ["scrub", "grassland", "grass", "meadow", "heath", "cemetery", "golf_course"];

/** Text sizes up by this much (-1286 "a little bigger"); road and path names to at least ROAD_LABEL_SIZE. */
const LABEL_SCALE = 1.15;
const ROAD_LABEL_SIZE = 14;
const HALO_WIDTH = 1.75;

const round1 = (n) => Math.round(n * 10) / 10;

/** Scales the output values of a text-size, leaving zoom stops and conditions alone. */
export function scaleSize(value, factor) {
  if (typeof value === "number") return round1(value * factor);
  if (!Array.isArray(value)) return value;
  const [op, ...a] = value;
  if (op === "interpolate") {
    // ["interpolate", interpolation, input, stop, output, stop, output, ...]
    return [op, a[0], a[1], ...a.slice(2).map((v, i) => (i % 2 === 1 ? scaleSize(v, factor) : v))];
  }
  if (op === "step") {
    // ["step", input, output0, stop, output, ...]
    return [op, a[0], scaleSize(a[1], factor), ...a.slice(2).map((v, i) => (i % 2 === 1 ? scaleSize(v, factor) : v))];
  }
  if (op === "case") {
    // ["case", cond, output, cond, output, ..., fallback]
    return [op, ...a.map((v, i) => (i % 2 === 1 || i === a.length - 1 ? scaleSize(v, factor) : v))];
  }
  if (op === "match") {
    // ["match", input, label, output, ..., fallback]
    return [op, a[0], ...a.slice(1).map((v, i) => (i % 2 === 1 || i === a.length - 2 ? scaleSize(v, factor) : v))];
  }
  return value;
}

function byId(layers, id) {
  const layer = layers.find((l) => l.id === id);
  if (!layer) throw new Error(`forager-street: there is no layer "${id}" to edit; the pinned style changed`);
  return layer;
}

function insertAfter(layers, id, newLayer) {
  const i = layers.findIndex((l) => l.id === id);
  if (i < 0) throw new Error(`forager-street: there is no layer "${id}" to insert after`);
  layers.splice(i + 1, 0, newLayer);
}

/** The road class a road layer draws, from its id, or null for layers this palette leaves alone. */
function roadClass(id) {
  if (!id.startsWith("roads_")) return null;
  if (/runway|taxiway|pier|rail|oneway|labels|shields|other/.test(id)) return null;
  if (id.includes("minor_service")) return "service";
  if (id.includes("highway")) return "highway";
  if (id.includes("major") || id.includes("link")) return "major";
  if (id.includes("minor")) return "minor";
  return null;
}

/** Footpath widths by zoom: Street v1's, and hard trails' at +0.4 from z14 (the spec's "+0.4 width"). */
const BASE_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 1.2, 15, 1.8, 18, 3.5];
const HARD_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 1.6, 15, 2.2, 18, 3.9];

/** `sac_scale` groups (the spec's "Groups"); anything else, or none, is unknown. */
export const SAC_GROUPS = {
  easy: ["strolling", "hiking"],
  rough: ["mountain_hiking", "demanding_mountain_hiking"],
  hard: ["alpine_hiking", "demanding_alpine_hiking", "difficult_alpine_hiking"],
};
/** `trail_visibility` values that make a trail faint; everything else, untagged included, is clear. */
export const FAINT_VISIBILITY = ["bad", "horrible", "no"];
/** Dash lengths from z15, in line widths (the spec's ladder; unknown is the owner's "Longer dash (Recommended)"). */
const DASH = { unknown: 4, easy: 8, rough: 2, hard: 2 };
/** The gap, in line widths: 1.5 for clear, 4 for faint. */
const GAP = { clear: 1.5, faint: 4 };

/**
 * The footpath layers, bottom to top, in place of Street v1's one roads_path_foot (90 layers against 74).
 *
 * One layer per group because `line-dasharray` is not data-driven in MapLibre Native (style-spec 26.4.4's
 * sdk-support points Android and iOS at maplibre-native issue #744), so each dash needs its own layer.
 * Order: informal, steps, unknown, easy, rough, hard, so informal is below hard and a warning is never covered.
 * Informal (`informal == "yes"`) overrides every group except hard, and wins over faint (Sage's ruling as built).
 *   z13:        everything as unknown [4, 1.5]; hard takes only its colour; informal a plain [4, 1.5] dash.
 *   z14:        easy and rough as unknown [4, g]; hard in its colour at +0.4 width, [4, g]; informal as dots
 *               (the owner: "Show from zoom 15 (Recommended)").
 *   z15 and up: unknown [4, g], easy [8, g], rough [2, g], hard [2, g] at +0.4 width; informal dots.
 * Steps keep Street v1's [3, 1.5] and take no group.
 */
function trailLayers(pathBase) {
  const sac = ["coalesce", ["get", "sac_scale"], ""];
  const inGroup = (g) => ["in", sac, ["literal", SAC_GROUPS[g]]];
  const notHard = ["!", inGroup("hard")];
  const unknown = ["!", ["in", sac, ["literal", [...SAC_GROUPS.easy, ...SAC_GROUPS.rough, ...SAC_GROUPS.hard]]]];
  const faint = ["in", ["coalesce", ["get", "trail_visibility"], ""], ["literal", FAINT_VISIBILITY]];
  const visibility = { clear: ["!", faint], faint };
  const informal = ["==", ["get", "informal"], "yes"];
  const notInformal = ["!", informal];
  const footpaths = FOOT_DETAILS.filter((d) => d !== "steps");
  const layer = (id, { minzoom, maxzoom, filter, colour, dash, width, cap = "butt" }) => ({
    id,
    type: "line",
    source: "protomaps",
    "source-layer": "roads",
    minzoom,
    ...(maxzoom ? { maxzoom } : {}),
    filter: [
      "all", ["!", ["has", "is_tunnel"]], ["!", ["has", "is_bridge"]], ["==", ["get", "kind"], "path"],
      ["in", ["get", "kind_detail"], ["literal", footpaths]], ...filter,
    ],
    layout: { "line-cap": cap, "line-join": "round" },
    paint: { "line-color": colour, "line-dasharray": dash, "line-width": width },
  });

  const out = [
    layer("roads_path_informal_z13", { minzoom: 13, maxzoom: 14, filter: [informal, notHard],
      colour: COLOURS.informalPath, dash: [4, 1.5], width: BASE_WIDTH }),
    layer("roads_path_informal", { minzoom: 14, filter: [informal, notHard],
      colour: COLOURS.informalPath, dash: [0.1, 2.2], width: BASE_WIDTH, cap: "round" }),
    pathBase("roads_path_steps", ["steps"], COLOURS.footpath, [3, 1.5], BASE_WIDTH),
    layer("roads_path_unknown_z13", { minzoom: 13, maxzoom: 14, filter: [notInformal, notHard],
      colour: COLOURS.footpath, dash: [4, 1.5], width: BASE_WIDTH }),
    layer("roads_path_hard_z13", { minzoom: 13, maxzoom: 14, filter: [inGroup("hard")],
      colour: COLOURS.hardPath, dash: [4, 1.5], width: BASE_WIDTH }),
  ];
  for (const vis of ["clear", "faint"]) {
    out.push(layer(`roads_path_unknown_z14_${vis}`, { minzoom: 14, maxzoom: 15, filter: [notInformal, notHard, visibility[vis]],
      colour: COLOURS.footpath, dash: [4, GAP[vis]], width: BASE_WIDTH }));
  }
  for (const vis of ["clear", "faint"]) {
    out.push(layer(`roads_path_hard_z14_${vis}`, { minzoom: 14, maxzoom: 15, filter: [inGroup("hard"), visibility[vis]],
      colour: COLOURS.hardPath, dash: [4, GAP[vis]], width: HARD_WIDTH }));
  }
  for (const g of ["unknown", "easy", "rough", "hard"]) {
    for (const vis of ["clear", "faint"]) {
      const filter = [g === "unknown" ? unknown : inGroup(g), visibility[vis]];
      if (g !== "hard") filter.push(notInformal);
      out.push(layer(`roads_path_${g}_${vis}`, { minzoom: 15, filter,
        colour: g === "hard" ? COLOURS.hardPath : COLOURS.footpath,
        dash: [DASH[g], GAP[vis]],
        width: g === "hard" ? HARD_WIDTH : BASE_WIDTH }));
    }
  }
  return out;
}

/** Applies the Street design to Protomaps' light layers, in place, and returns them. */
export function applyStreetDesign(layers) {
  // --- Forest, grass and parks (-1285) ---------------------------------------------------------
  const landcover = byId(layers, "landcover");
  // landcover exists in the tiles at z0-7 only (Part 1, Q1.1); landuse takes over from z2. Both solid:
  // no fade out at 5-7 and no fade in at 6-11, so the forest never thins on the way in.
  landcover.paint["fill-opacity"] = 1;
  landcover.paint["fill-color"] = [
    "match", ["get", "kind"],
    ["grassland", "scrub"], COLOURS.grass,
    "barren", "rgba(255, 243, 215, 1)",
    "urban_area", "rgba(230, 230, 230, 1)",
    "farmland", "rgba(216, 239, 210, 1)",
    "glacier", "rgba(255, 255, 255, 1)",
    COLOURS.forest, // the unlisted default is forest in Protomaps' own match
  ];
  const park = byId(layers, "landuse_park");
  // Parks and protected areas leave the fill: they are drawn as an outline below.
  park.filter = ["in", "kind", ...TREE_KINDS, ...GRASS_KINDS, "glacier", "sand", "military", "naval_base", "airfield"];
  park.paint["fill-opacity"] = 1;
  park.paint["fill-color"] = [
    "case",
    ["in", ["get", "kind"], ["literal", TREE_KINDS]], COLOURS.forest,
    ["in", ["get", "kind"], ["literal", GRASS_KINDS]], COLOURS.grass,
    ["==", ["get", "kind"], "glacier"], "#e7e7e7",
    ["==", ["get", "kind"], "sand"], "#e2e0d7",
    ["in", ["get", "kind"], ["literal", ["military", "naval_base", "airfield"]]], "#c6dcdc",
    "#e2dfda",
  ];
  insertAfter(layers, "landuse_park", {
    id: "landuse_park_outline",
    type: "line",
    source: "protomaps",
    "source-layer": "landuse",
    minzoom: 6,
    filter: ["in", "kind", ...PARK_KINDS],
    paint: {
      "line-color": COLOURS.parkOutline,
      "line-opacity": 0.85,
      "line-width": ["interpolate", ["linear"], ["zoom"], 6, 0.6, 12, 1.2, 16, 2],
    },
  });

  // --- Paths and tracks (-1284, -1300 (4)) ------------------------------------------------------
  const other = byId(layers, "roads_other");
  other.filter = [
    "all", ["!has", "is_tunnel"], ["!has", "is_bridge"], ["in", "kind", "other", "path"],
    ["!in", "kind_detail", "pier", "track", ...FOOT_DETAILS],
  ];
  const pathBase = (id, details, colour, dash, width) => ({
    id,
    type: "line",
    source: "protomaps",
    "source-layer": "roads",
    minzoom: PATH_MIN_ZOOM,
    filter: ["all", ["!has", "is_tunnel"], ["!has", "is_bridge"], ["==", "kind", "path"], ["in", "kind_detail", ...details]],
    layout: { "line-cap": "butt", "line-join": "round" },
    paint: { "line-color": colour, "line-dasharray": dash, "line-width": width },
  });
  insertAfter(layers, "roads_other", {
    ...pathBase(
      "roads_path_track", ["track"], COLOURS.track, [4, 1.5, 1, 1.5],
      ["interpolate", ["linear"], ["zoom"], 13, 1.3, 15, 2, 18, 4],
    ),
    minzoom: TRACK_MIN_ZOOM,
  });
  let after = "roads_path_track";
  for (const layer of trailLayers(pathBase)) {
    insertAfter(layers, after, layer);
    after = layer.id;
  }

  // --- Roads: colours chosen for the inversion (-1288) ----------------------------------------
  for (const layer of layers) {
    const cls = roadClass(layer.id);
    if (!cls || layer.type !== "line") continue;
    const casing = layer.id.includes("casing");
    const key = casing ? `${cls}Casing` : cls;
    if (layer.id.includes("tunnels")) continue; // tunnels keep their grey: underground, de-emphasised
    layer.paint["line-color"] = COLOURS[key];
  }

  // --- Points of interest (-1287, -1300 (4) and (5)) -----------------------------------------
  const pois = byId(layers, "pois");
  pois.filter = [
    "any",
    ["all", ["in", ["get", "kind"], ["literal", OUTDOOR_KINDS]], [">=", ["zoom"], OUTDOOR_MIN_ZOOM]],
    ["all", ["in", ["get", "kind"], ["literal", TOWN_KINDS]], [">=", ["zoom"], ["max", TOWN_MIN_ZOOM, ["coalesce", ["get", "min_zoom"], 0]]]],
  ];
  pois.layout["icon-image"] = [
    "match", ["get", "kind"], DOT_KINDS, "townspot", ...Object.entries(ICON_BY_KIND).flat(), ["get", "kind"],
  ];
  pois.layout["symbol-sort-key"] = ["match", ["get", "kind"], OUTDOOR_KINDS, 0, 10];
  pois.layout["text-size"] = ["interpolate", ["linear"], ["zoom"], 12, 12, 16, 13, 19, 16];
  pois.paint["text-color"] = COLOURS.text;

  // --- Labels (-1286) --------------------------------------------------------------------------
  byId(layers, "roads_labels_minor").minzoom = MINOR_ROAD_LABEL_MIN_ZOOM;
  for (const layer of layers) {
    // Shields keep their own small text on the shield icon; everything else is a label.
    if (layer.type !== "symbol" || !layer.layout?.["text-field"] || layer.id === "roads_shields") continue;
    if (layer.id !== "pois") layer.layout["text-size"] = scaleSize(layer.layout["text-size"] ?? 16, LABEL_SCALE);
    if (layer.id === "roads_labels_minor" || layer.id === "roads_labels_major") layer.layout["text-size"] = ROAD_LABEL_SIZE;
    layer.paint = layer.paint ?? {};
    layer.paint["text-color"] = layer.id.startsWith("water_") ? COLOURS.waterText : COLOURS.text;
    layer.paint["text-halo-color"] = COLOURS.halo;
    layer.paint["text-halo-width"] = HALO_WIDTH;
  }
  return layers;
}

// --- Buildings solid, with an outline (dispatch 2026-10-27-02, RECORD -1730) ------------------------------------------
// The owner, on the S22: "The buildings and houses are soft against the background, and the forest also overlaps them.
// Can we have a border around the buildings and houses to separate them from the rest of the map?" Protomaps' light
// layer draws buildings #cccccc at half opacity, so the forest and its tree glyphs below show through them.
//
// The structure is the OpenStreetMap Carto style's buildings (openstreetmap-carto, style/buildings.mss; that project
// is released under CC0 1.0, its LICENSE.txt): an opaque fill, 4% darker at the lowest zoom it draws, and from z15 a
// 0.75 line 15% darker than the fill (Carto's @building-low-zoom and @building-line, both darkened from @building-fill).
// Forager keeps Protomaps' grey rather than Carto's tan: #cccccc darkened 15% is #a6a6a6. Darkened 4% would be #c2c2c2,
// but its night (#3d3d3d) is 1.047:1 against the night water, under NightWaterTest's 1.05 floor; the z14 grey is one
// shade darker, #c1c1c1 (night #3e3e3e, 1.063:1; dispatch 2026-10-27-02 Amendment 1, RECORD -1733, the owner: "A shade
// darker: #c1c1c1 (Recommended)"). Night is the app's V1 inversion with no exception (#3e3e3e, #333333, #595959;
// BuildingsNightTest in the app).
//
// A function of its own, called by generate-street.mjs for v3 only, and not part of applyStreetDesign: v2 and v1 are
// frozen for the regions downloaded against them.

/** The buildings' day colours and the outline's zoom and width. */
export const BUILDINGS = { lowZoomFill: "#c1c1c1", fill: "#cccccc", outline: "#a6a6a6", outlineMinZoom: 15, outlineWidth: 0.75 };

/** Makes Protomaps' buildings layer opaque, with Carto's low-zoom step, and adds buildings_outline directly above it. */
export function withBuildingOutline(layers) {
  const buildings = byId(layers, "buildings");
  buildings.paint = {
    "fill-color": ["step", ["zoom"], BUILDINGS.lowZoomFill, BUILDINGS.outlineMinZoom, BUILDINGS.fill],
    "fill-opacity": 1,
  };
  insertAfter(layers, "buildings", {
    id: "buildings_outline",
    type: "line",
    source: buildings.source,
    "source-layer": buildings["source-layer"],
    filter: buildings.filter,
    minzoom: BUILDINGS.outlineMinZoom,
    paint: { "line-color": BUILDINGS.outline, "line-width": BUILDINGS.outlineWidth },
  });
  return layers;
}
