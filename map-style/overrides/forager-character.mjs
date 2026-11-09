// Trail Map's character (dispatch 2026-10-22-03 and its Amendment 1, RECORD -1561): the owner's round-three rulings
// 1 to 7 and their night colours, as further edits to the Street design (overrides/forager-street.mjs), applied after
// it by generate-street.mjs. Style v2 (street-v2.json) carries them; v1 stays as it was.
//
// The rulings are recorded in slayer8366/Forager-cartography on main:
//   docs/design/2026-10-21-character-rulings.md   rulings 1-7, day values, layer ids, zooms and filters, and the
//                                                 correction entries after it (tufts from z10, night tufts #2485ad);
//   docs/design/2026-10-22-night-colours-rulings.md  night colours and the checker rule.
// The sprite images (ruling 1, 5 and 6) are in sprite v5 (tools/build-sprite-v5.mjs); ruling 8, trail names, is no
// style change (a phone check, Amendment 1).
//
// Night is the app's (-1288: inversion, with named exceptions). This file only records the day colours and the night
// exceptions the app must draw; the app holds its own copies (NightColour.kt, NightPattern.kt), checked equal to these
// through the style's metadata, and tools/check-street-night.mjs holds the ruled limits.
import { COLOURS } from "./forager-street.mjs";

/** Day colours and the ruled night colours of the character layers. */
export const CHARACTER_COLOURS = {
  // Ruling 1: the tree glyph is drawn in the pattern image (#3e7a3a); at night it is #5d804f (2026-10-22, "Darker
  // water fill and darker trees please", then the round-5 pair). 1.42:1 on the night forest below.
  treeGlyph: "#3e7a3a",
  nightTreeGlyph: "#5d804f",
  // The night forest the owner chose ("the app's palette"), in place of the inversion #436d35. It ships with the tree
  // glyph: the glyph is 1.34:1 on #436d35, below its 1.35 floor (verify-first report, item 1).
  nightForest: "#446835",
  // Ruling 2: streams by day. At night every water fill and line is COLOURS.nightWater.
  stream: "#3fa0c8",
  // Ruling 5: wetland joins the park fill; its night is #2a4f47, not the inversion #1b392c.
  wetland: "#c5e3d6",
  nightWetland: "#2a4f47",
  // Ruling 5: tufts in the stream blue, drawn in the pattern image. Night is the plain inversion #2485ad (the owner,
  // "Keep #2485ad"): no exception, so the image is on the inversion list like every icon.
  tufts: "#3fa0c8",
  // Ruling 7: bare rock joins the park fill; at night #6e7580 (the owner: "Rock night color ... good").
  bareRock: "#c9c4bb",
  nightBareRock: "#6e7580",
  // Ruling 3: area names, near-black ("areaNames: black") on a white halo; at night inverted like every label.
  areaName: "#262626",
};

/** The fill-pattern images (ruling 1 and 5). */
export const TREE_PATTERN = "tree_dark_irregular";
export const TUFTS_PATTERN = "tufts";

/** Sprite v5's new images, in the order they are laid out, at their 1x size (tools/build-sprite-v5.mjs). */
export const SPRITE_V5_ITEMS = [
  { name: "trailhead_b", size: 19 },
  { name: "viewpoint_c", size: 19 },
  { name: "parking", size: 19 },
  { name: "camp_site", size: 19 },
  { name: "picnic_site", size: 19 },
  { name: "ranger_station", size: 19 },
  { name: TREE_PATTERN, size: 64 },
  { name: TUFTS_PATTERN, size: 32 },
];

/**
 * Night fill colours by day colour (the app's `STREET_NIGHT_FILL_COLOURS`): wherever a fill layer's colour is one of
 * these day colours, literal or inside an expression, the app draws the night colour instead of the inversion.
 */
export const NIGHT_FILL_COLOURS = {
  [COLOURS.forest]: CHARACTER_COLOURS.nightForest,
  [CHARACTER_COLOURS.wetland]: CHARACTER_COLOURS.nightWetland,
  [CHARACTER_COLOURS.bareRock]: CHARACTER_COLOURS.nightBareRock,
};

/**
 * Pattern images the app paints one colour at night, alpha kept (the app's `STREET_NIGHT_PATTERN_COLOURS`), instead of
 * inverting them. Kept off the inversion list (`forager:sprite-icons`).
 */
export const NIGHT_PATTERN_COLOURS = { [TREE_PATTERN]: CHARACTER_COLOURS.nightTreeGlyph };

/**
 * The night checker's limits (the owner: "Rules from your colours"; the numbers are the planner's, night record, "The
 * planner's call: the checker numbers"). Every other layer keeps its floor in tools/check-street-night.mjs.
 */
export const NIGHT_LIMITS = {
  waterLuminanceMax: 0.05, // relative luminance of every night water fill and line
  waterBlueMax: 96, // blue channel, 0-255
  waterOnLandMin: 1.05, // night water against every night land fill
  treeOnForestMin: 1.35, // the night tree glyph against the night forest
};

/** Ruling 2's stream widths by zoom (sage-03 2(a): z13 0.6, z14 1, z15 1.5; z18 2.5). */
export const STREET_STREAM_MIN_ZOOM = 13;
const STREAM_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 0.6, 14, 1, 15, 1.5, 18, 2.5];
/** Ruling 1 and 5: where the patterns start. */
export const TREE_GLYPH_MIN_ZOOM = 12;
export const TUFTS_MIN_ZOOM = 10;

/** Ruling 3: kinds named from z8 (or their tile min_zoom if higher), forests named so from z8, the rest from z11. */
const RESERVE_KINDS = ["nature_reserve", "national_park", "protected_area"];
const NAMED_FOREST_WORDS = ["National Forest", "State Forest", "National Monument", "National Grassland", "Provincial Park"];
export const AREA_NAMES_MIN_ZOOM = 8;
const OTHER_AREA_MIN_ZOOM = 11;

/** Ruling 4: metres to feet, rounded, in a 0.85-scale line under the name. */
const FEET_PER_METRE = 3.28084;
const PEAK_HEIGHT_SCALE = 0.85;

function byId(layers, id) {
  const layer = layers.find((l) => l.id === id);
  if (!layer) throw new Error(`forager-character: there is no layer "${id}" to edit; the pinned style changed`);
  return layer;
}

function insertAt(layers, index, newLayer) {
  if (layers.some((l) => l.id === newLayer.id)) throw new Error(`forager-character: layer "${newLayer.id}" already exists`);
  layers.splice(index, 0, newLayer);
}

const indexOf = (layers, id) => layers.indexOf(byId(layers, id));

/** Applies rulings 1-7 to the Street design's layers, in place, and returns them. */
export function applyCharacter(layers) {
  // --- Rulings 5 and 7: wetland and bare rock join the park fill -------------------------------
  const park = byId(layers, "landuse_park");
  park.filter = [...park.filter, "wetland", "bare_rock"];
  const colour = park.paint["fill-color"];
  if (colour[0] !== "case") throw new Error("forager-character: landuse_park's fill-color is no longer a case");
  park.paint["fill-color"] = [
    ...colour.slice(0, -1),
    ["==", ["get", "kind"], "wetland"], CHARACTER_COLOURS.wetland,
    ["==", ["get", "kind"], "bare_rock"], CHARACTER_COLOURS.bareRock,
    colour[colour.length - 1],
  ];

  // --- Ruling 1: tree glyphs on forest and wood; ruling 5: wetland tufts ------------------------
  insertAt(layers, indexOf(layers, "landuse_park") + 1, {
    id: "landuse_forest_glyphs",
    type: "fill",
    source: "protomaps",
    "source-layer": "landuse",
    minzoom: TREE_GLYPH_MIN_ZOOM,
    filter: ["in", ["get", "kind"], ["literal", ["forest", "wood"]]],
    paint: { "fill-pattern": TREE_PATTERN },
  });
  insertAt(layers, indexOf(layers, "landuse_forest_glyphs") + 1, {
    id: "landuse_wetland_tufts",
    type: "fill",
    source: "protomaps",
    "source-layer": "landuse",
    minzoom: TUFTS_MIN_ZOOM,
    filter: ["==", ["get", "kind"], "wetland"],
    paint: { "fill-pattern": TUFTS_PATTERN },
  });

  // --- Ruling 2: streams from z13, the label from the line's zoom ------------------------------
  const stream = byId(layers, "water_stream");
  stream.minzoom = STREET_STREAM_MIN_ZOOM;
  stream.paint["line-color"] = CHARACTER_COLOURS.stream;
  stream.paint["line-width"] = STREAM_WIDTH;
  const streamLabel = byId(layers, "water_waterway_label");
  if (streamLabel.minzoom !== STREET_STREAM_MIN_ZOOM) {
    throw new Error(`forager-character: water_waterway_label starts at z${streamLabel.minzoom}, not the stream line's z13`);
  }

  // --- Ruling 4: peak heights in feet under the name -------------------------------------------
  const pois = byId(layers, "pois");
  const name = pois.layout["text-field"];
  const feet = [
    "concat",
    ["number-format", ["round", ["*", ["to-number", ["get", "elevation"]], FEET_PER_METRE]], { locale: "en-US" }],
    " ft",
  ];
  pois.layout["text-field"] = [
    "case",
    ["all", ["==", ["get", "kind"], "peak"], ["has", "elevation"]],
    [
      "case",
      ["any", ["has", "name"], ["has", "name:en"]],
      ["format", ["coalesce", ["get", "name:en"], ["get", "name"]], {}, "\n", {}, feet, { "font-scale": PEAK_HEIGHT_SCALE }],
      ["format", feet, { "font-scale": PEAK_HEIGHT_SCALE }],
    ],
    name,
  ];

  // --- Ruling 3: area names, below the points of interest so those win a collision -------------
  const zoomFloor = ["max", AREA_NAMES_MIN_ZOOM, ["coalesce", ["get", "min_zoom"], 0]];
  const nameText = ["coalesce", ["get", "name"], ""];
  insertAt(layers, indexOf(layers, "pois"), {
    id: "pois_area_names",
    type: "symbol",
    source: "protomaps",
    "source-layer": "pois",
    minzoom: AREA_NAMES_MIN_ZOOM,
    filter: [
      "any",
      ["all", ["in", ["get", "kind"], ["literal", RESERVE_KINDS]], [">=", ["zoom"], zoomFloor]],
      ["all", ["==", ["get", "kind"], "forest"], ["any", ...NAMED_FOREST_WORDS.map((w) => ["in", w, nameText])]],
      ["all", ["in", ["get", "kind"], ["literal", ["forest", "park"]]], [">=", ["zoom"], OTHER_AREA_MIN_ZOOM]],
    ],
    layout: {
      "text-field": name,
      "text-font": ["Noto Sans Italic"],
      "text-size": ["interpolate", ["linear"], ["zoom"], 8, 12, 12, 13.5, 15, 15],
      "text-letter-spacing": 0.08,
      "text-max-width": 7,
    },
    paint: {
      "text-color": CHARACTER_COLOURS.areaName,
      "text-halo-color": COLOURS.halo,
      "text-halo-width": 1.75,
    },
  });
  return layers;
}
