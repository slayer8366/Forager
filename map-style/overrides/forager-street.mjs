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
// Informal paths: the tiles have no field that marks one (Part 1, Q1.3), so there is no dotted layer yet.
// INFORMAL_PATH_COLOUR is reserved for it so the own build's `informal` attribute has a slot.
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
  // Reserved for informal paths when the tiles carry them (-1284); not used yet.
  informalPath: "#b9876b",
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
  // Labels: near-black on a light halo (-1286); at night they invert to near-white on near-black.
  text: "#262626",
  waterText: "#1f4d86",
  halo: "#ffffff",
};

/** The design's outdoor list (-1287), in the tiles' own kind names (Part 1, Q1.3). */
export const OUTDOOR_KINDS = [
  "trailhead", "parking", "camp_site", "picnic_site", "viewpoint", "peak", "toilets", "drinking_water", "ranger_station",
];
/** The six outdoor kinds the pinned icon set has no icon for: drawn as a dot and the name (-1300 (5)). */
export const DOT_KINDS = ["trailhead", "parking", "camp_site", "picnic_site", "viewpoint", "ranger_station"];
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
  insertAfter(layers, "roads_other", pathBase(
    "roads_path_track", ["track"], COLOURS.track, [4, 1.5, 1, 1.5],
    ["interpolate", ["linear"], ["zoom"], 13, 1.3, 15, 2, 18, 4],
  ));
  insertAfter(layers, "roads_path_track", pathBase(
    "roads_path_foot", FOOT_DETAILS, COLOURS.footpath, [3, 1.5],
    ["interpolate", ["linear"], ["zoom"], 13, 1.2, 15, 1.8, 18, 3.5],
  ));

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
  pois.layout["icon-image"] = ["match", ["get", "kind"], DOT_KINDS, "townspot", ["get", "kind"]];
  pois.layout["symbol-sort-key"] = ["match", ["get", "kind"], OUTDOOR_KINDS, 0, 10];
  pois.layout["text-size"] = ["interpolate", ["linear"], ["zoom"], 12, 12, 16, 13, 19, 16];
  pois.paint["text-color"] = COLOURS.text;

  // --- Labels (-1286) --------------------------------------------------------------------------
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
