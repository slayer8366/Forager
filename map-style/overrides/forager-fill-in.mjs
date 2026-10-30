// Trail Map's fill-in (Street v4): settled-area and park fills, major roads coloured by class, and the rail and minor-road
// adjustments, as further edits to the v3 layers, applied last by generate-street.mjs for v4 only. v3 and older stay as
// they were, for the regions downloaded against them.
//
// The rulings are recorded in slayer8366/Forager-cartography on main:
//   docs/design/2026-10-30-fill-in-rulings.md   the owner: "Go with the draft, white tertiary, skip the tile change";
//                                               the day values below are the draft's (mkdraft.py beside that record).
//   The night colours: sage-09's "OSM-like" set (the owner, 2026-10-30: "Keep it osm-like", then "Yes, ship it"):
//   the app's OpenStreetMap night is the plain inversion of OSM's day colours, so the major roads here take the plain
//   inversion too, and the settled fills a quieter night than their inversion.
//   Then, in the same record: ramps with their roads ("Approve the fix"); motorways in OSM Carto's pink by day ("A: OSM
//   pink") and inverted at night ("Invert like OSM"); the night tertiary dark grey #333333 and the rail inverted ("Dark
//   grey #333333", "Plain inversion": the black tertiary and the brighter rail outranked the trails in forest at night).
//
// Night is the app's (-1288: inversion, with named exceptions). The night tables below are v4's own: v3's roads_major
// keeps its amber night and its rail #b4bec0, so v4 cannot share v3's tables without changing how v3 regions draw at
// night. The app holds its own copy, chosen by the style it loads, checked equal to these through the style's metadata.
import { NIGHT_LAYER_COLOURS } from "./forager-street.mjs";
import { NIGHT_FILL_COLOURS } from "./forager-character.mjs";

/** Day colours of the fill-in (the ruled draft). */
export const FILL_IN_COLOURS = {
  residential: "#d5d1cb",
  commercial: "#e3cfca",
  railway: "#d6d1d4",
  farmland: "#e8eacd",
  parks: "#cde5bf",
  trunk: "#f2bf72",
  primary: "#f5d28c",
  secondary: "#f8e3ae",
  tertiary: "#ffffff", // the owner: "white tertiary"
  majorOther: "#f6d68f", // any other kind_detail keeps v3's major colour
  tertiaryCasing: "#a39b8d",
  majorCasing: "#b4924c",
  rail: "#8e979a",
  // OSM Carto's motorway fill and casing: openstreetmap-carto style/road-colors-generated.mss at 9295745d, lines 11 and 7.
  motorway: "#e892a2",
  motorwayCasing: "#dc2a67",
};

/** The motorway layers, centres and casings, that take OSM Carto's colours; the tunnels keep their grey. */
const MOTORWAY_CENTRES = ["roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link"];
const MOTORWAY_CASINGS = ["roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing", "roads_link_casing", "roads_bridges_link_casing"];

/** The landuse kinds each new fill draws. */
const SETTLED_KINDS = ["residential", "commercial", "railway", "farmland"];
const PARK_FILL_KINDS = ["park", "recreation_ground", "garden", "dog_park", "pitch"];
/** The earth fill: the settled fill's default, which no kind in its filter reaches. */
const EARTH = "#e2dfda";

/** Major-road width by zoom, and the casings' gap to match it. */
const MAJOR_WIDTH = ["interpolate", ["exponential", 1.6], ["zoom"], 6, 0, 8, 0.8, 10, 1.3, 12, 1.9, 15, 3, 18, 13];
/** Minor-road width by zoom, and its casing's gap. */
const MINOR_WIDTH = ["interpolate", ["exponential", 1.6], ["zoom"], 11, 0.3, 12, 0.7, 15, 2, 18, 11];

/** The lines v3 gives a night colour that v4 inverts instead, as the app's OpenStreetMap night does. */
const INVERTED_AT_NIGHT = ["roads_major", "roads_bridges_major", ...MOTORWAY_CENTRES, "roads_rail"];

/**
 * v4's night line colours by layer: v3's, except the major roads, the motorways and their ramps, and the rail, which take
 * the plain inversion (motorway #7f2939, its casing #e83673, rail #636c6f). The tertiary's night is in
 * NIGHT_LINE_COLOURS_V4.
 */
export const NIGHT_LAYER_COLOURS_V4 = Object.fromEntries(Object.entries(NIGHT_LAYER_COLOURS).filter(([id]) => !INVERTED_AT_NIGHT.includes(id)));

/**
 * v4's night fill colours by day colour: v3's, and the five new fills. Parks need theirs: the inversion (#2b431d) is
 * 1.04:1 against the night water, under the ruled 1.05. The four settled fills are quieter than their inversion,
 * close to the near-ground grey of OSM's night.
 */
export const NIGHT_FILL_COLOURS_V4 = {
  ...NIGHT_FILL_COLOURS,
  [FILL_IN_COLOURS.residential]: "#2d2a25",
  [FILL_IN_COLOURS.commercial]: "#362824",
  [FILL_IN_COLOURS.railway]: "#2b2829",
  [FILL_IN_COLOURS.farmland]: "#2e2a10",
  [FILL_IN_COLOURS.parks]: "#2c461e",
};

/**
 * v4's night line colours by day colour, per layer: where one of these layers' `line-color` draws a named day colour, as a
 * literal or inside an expression, night draws its colour instead of the inversion; every other colour in the same
 * expression inverts. Recorded in the style as `forager:night-line-colours` (v4 only).
 */
export const NIGHT_LINE_COLOURS_V4 = {
  // The tertiary's day white: dark grey at night, under the night footpath in forest (1.97:1 against its 2.58), where the
  // black inversion (3.28:1) outranked it (Sorrel's review). The other classes in the same match invert.
  roads_major: { [FILL_IN_COLOURS.tertiary]: "#333333" },
  roads_bridges_major: { [FILL_IN_COLOURS.tertiary]: "#333333" },
};

function byId(layers, id) {
  const layer = layers.find((l) => l.id === id);
  if (!layer) throw new Error(`forager-fill-in: there is no layer "${id}" to edit; the pinned style changed`);
  return layer;
}

function insertAfter(layers, id, newLayer) {
  if (layers.some((l) => l.id === newLayer.id)) throw new Error(`forager-fill-in: layer "${newLayer.id}" already exists`);
  layers.splice(layers.indexOf(byId(layers, id)) + 1, 0, newLayer);
}

/** Applies the fill-in to the v3 layers, in place, and returns them. */
export function applyFillIn(layers) {
  const C = FILL_IN_COLOURS;
  // --- Settled areas and parks, just above the landcover ---------------------------------------
  insertAfter(layers, "landcover", {
    id: "landuse_settled",
    type: "fill",
    source: "protomaps",
    "source-layer": "landuse",
    filter: ["in", ["get", "kind"], ["literal", SETTLED_KINDS]],
    paint: {
      "fill-color": ["match", ["get", "kind"], "residential", C.residential, "commercial", C.commercial, "railway", C.railway, "farmland", C.farmland, EARTH],
      "fill-opacity": ["interpolate", ["linear"], ["zoom"], 6, 0.6, 9, 1],
    },
  });
  insertAfter(layers, "landuse_settled", {
    id: "landuse_parks_fill",
    type: "fill",
    source: "protomaps",
    "source-layer": "landuse",
    filter: ["in", ["get", "kind"], ["literal", PARK_FILL_KINDS]],
    paint: { "fill-color": C.parks },
  });

  // --- Major roads by class, wider from z8 ---------------------------------------------------------
  // Each ramp takes its road's colour (the rulings' "Ramps": the owner, "Approve the fix (Recommended)"), so at night it
  // takes the same inversion. The draft matched trunk_link only; the other ramps fell to v3's amber.
  const byClass = ["match", ["get", "kind_detail"], ["trunk", "trunk_link"], C.trunk, ["primary", "primary_link"], C.primary, ["secondary", "secondary_link"], C.secondary, ["tertiary", "tertiary_link"], C.tertiary, C.majorOther];
  for (const id of ["roads_major", "roads_bridges_major"]) {
    const layer = byId(layers, id);
    layer.paint["line-width"] = MAJOR_WIDTH;
    layer.paint["line-color"] = byClass;
  }
  for (const id of ["roads_major_casing_early", "roads_major_casing_late", "roads_bridges_major_casing"]) {
    const layer = byId(layers, id);
    layer.paint["line-gap-width"] = MAJOR_WIDTH;
    if (id === "roads_major_casing_early") layer.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 7.5, 0, 8.5, 0.6, 12, 1];
    layer.paint["line-color"] = ["match", ["get", "kind_detail"], ["tertiary", "tertiary_link"], C.tertiaryCasing, C.majorCasing];
  }

  // --- Minor roads from z11, their casing from z12 ---------------------------------------------------
  byId(layers, "roads_minor").paint["line-width"] = MINOR_WIDTH;
  const minorCasing = byId(layers, "roads_minor_casing");
  minorCasing.paint["line-gap-width"] = MINOR_WIDTH;
  minorCasing.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 11, 0, 12, 0.6];

  // --- Motorways and their ramps in OSM Carto's pink, with its casing ----------------------------------------
  for (const id of MOTORWAY_CENTRES) byId(layers, id).paint["line-color"] = C.motorway;
  for (const id of MOTORWAY_CASINGS) byId(layers, id).paint["line-color"] = C.motorwayCasing;

  // --- Rail, darker and stronger ---------------------------------------------------------------------
  const rail = byId(layers, "roads_rail");
  rail.paint["line-opacity"] = 0.85;
  rail.paint["line-color"] = C.rail;
  return layers;
}
