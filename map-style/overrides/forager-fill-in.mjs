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
  // OSM Carto's industrial lilac (style/landcover.mss:22) and buildings tan (style/buildings.mss:1), in place of RECORD
  // -1733's greys; the outline is Carto's darken(fill, 15%) and the z14 step one shade darker than its darken(fill, 4%)
  // #d1c6bd, whose night is 1.001:1 on the night water (the owner: "One step darker #ccc0b7").
  industrial: "#ebdbe8",
  building: "#d9d0c9",
  buildingZ14: "#ccc0b7",
  buildingOutline: "#b9a99c",
  waterEdge: "#71c3ce",
  // Orchards, plant nurseries and vineyards (Sage's sage-10; the owner: "Fill + pattern per kind (Recommended)", "#c8dd98",
  // "Standard night (Recommended)": the plain inversion #495e19, no fill exception), and their edge.
  orchards: "#c8dd98",
  orchardsEdge: "#b0c286",
};

/**
 * The faint area edges by landuse kind (Sage's FAINT; the owner: "Faint (Recommended)"), a shade darker than each fill,
 * after OSM Carto. The industrial edge is Carto's @industrial-line (style/landcover.mss:23; "OSM's #c6b3c3").
 */
const EDGE_COLOURS = [
  [["forest", "wood"], "#94b988"],
  [["scrub", "grassland", "grass", "meadow", "heath", "cemetery", "golf_course"], "#c2cdaa"],
  [["park", "recreation_ground", "garden", "dog_park", "pitch"], "#b4caa8"],
  [["allotments", "village_green", "playground"], "#88b99d"],
  [["wetland"], "#adc8bc"],
  [["bare_rock"], "#b0aba4"],
  [["residential"], "#c3bfba"],
  [["commercial"], "#d0bdb9"],
  [["railway"], "#c4bfc2"],
  [["farmland"], "#ccceb4"],
  [["school", "university", "college"], "#c9c3bd"],
  [["hospital"], "#c9c0bf"],
  [["industrial"], "#c6b3c3"],
];
/** Settled land's edges start a zoom later, at z14, where the street grid would otherwise be crowded. */
const EDGES_FROM_Z14 = ["residential", "commercial", "railway"];
/** The edges' width: faint, 0.5 at z13 to 1 at z17. */
const EDGE_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 0.5, 15, 0.75, 17, 1];

/** The motorway layers, centres and casings, that take OSM Carto's colours; the tunnels keep their grey. */
const MOTORWAY_CENTRES = ["roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link"];
const MOTORWAY_CASINGS = ["roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing", "roads_link_casing", "roads_bridges_link_casing"];

/** Orchards, plant nurseries and vineyards: kind farmland with one of these kind_detail values (basemap patch 0006). */
const ORCHARD_KINDS = ["orchard", "plant_nursery", "vineyard"];
const IS_ORCHARD = ["all", ["==", ["get", "kind"], "farmland"], ["in", ["get", "kind_detail"], ["literal", ORCHARD_KINDS]]];

/** The landuse kinds each new fill draws. */
const SETTLED_KINDS = ["residential", "commercial", "railway", "farmland"];
const PARK_FILL_KINDS = ["park", "recreation_ground", "garden", "dog_park", "pitch"];
/** The earth fill: the settled fill's default, which no kind in its filter reaches. */
const EARTH = "#e2dfda";

// The low zooms, after OSM Carto (the fill-in rulings, "Low-zoom roads, night borders, notices route": "A: OSM's treatment
// (Recommended)", Sage's sage-11 spec): no casings below z11, the majors thinner by class, Carto's motorway widths and
// low-zoom colours fading into the ruled ones from z10.5 to z11.5, and the minor roads a faint texture from z10.

/** A value per major-road class: trunk, primary, secondary, tertiary (each with its ramps), and any other. */
const byClass = (trunk, primary, secondary, tertiary, other) => ["match", ["get", "kind_detail"], ["trunk", "trunk_link"], trunk, ["primary", "primary_link"], primary, ["secondary", "secondary_link"], secondary, ["tertiary", "tertiary_link"], tertiary, other];
/** Major-road width by zoom and class, and the casings' gap to match it; one width for all classes from z15. */
const MAJOR_WIDTH = ["interpolate", ["exponential", 1.6], ["zoom"],
  8, byClass(0.9, 0.9, 0.5, 0, 0.5), 9, byClass(1.1, 1.1, 0.7, 0.4, 0.7), 10, byClass(1.3, 1.3, 0.9, 0.6, 0.9),
  11, byClass(1.6, 1.6, 1.2, 0.9, 1.2), 12, byClass(1.9, 1.9, 1.7, 1.5, 1.7), 15, 3, 18, 13];
/** Motorway width by zoom, and its casings' gap. */
const MOTORWAY_WIDTH = ["interpolate", ["exponential", 1.6], ["zoom"], 6, 0.8, 8, 1.0, 9, 1.3, 10, 1.7, 11, 2.0, 12, 2.4, 15, 5, 18, 15];
/** The early casings (to z12): none below z11. */
const EARLY_CASING_WIDTH = ["interpolate", ["linear"], ["zoom"], 10.9, 0, 11, 0.3, 12, 0.5];
/** Minor-road width by zoom from z10, and its casing's gap. */
const MINOR_WIDTH = ["interpolate", ["exponential", 1.6], ["zoom"], 10, 0.4, 11, 0.6, 12, 0.8, 15, 2, 18, 11];
/** Carto's low-zoom road colours (style/road-colors-generated.mss, motorway-low-zoom; the others Sage's, after Carto). */
const LOW_ZOOM = { motorway: "#e66e89", motorwayCasing: "#c24e6b", trunk: "#eeab4f", primary: "#efc46c", secondary: "#ecd58c", tertiary: "#bdb7ad", other: "#efc46c", minor: "#c4beb4" };
/** A colour fading from its low-zoom value at z10.5 to its ruled value at z11.5. */
const lowZoomFade = (low, ruled, from = 10.5, to = 11.5) => ["interpolate", ["linear"], ["zoom"], from, low, to, ruled];

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
  // The minor roads' white takes the tertiary's night and its band ("Low-zoom roads"); their low-zoom grey inverts.
  roads_minor: { "#ffffff": "#333333" },
  // Darker night rims ("Darker rims (Recommended)", Sage's REC table): each edge a shade darker than its fill's night,
  // as by day; recorded by check-street-night, not gated. The water edges take the night water, so they stay off.
  landuse_edges: {
    "#94b988": "#38552b", "#c2cdaa": "#1d240b", "#b4caa8": "#203216", "#88b99d": "#255239", "#adc8bc": "#203c36",
    "#b0aba4": "#5d636c", "#c3bfba": "#1d1b18", "#d0bdb9": "#241a18", "#c4bfc2": "#1a1919", "#ccceb4": "#131207",
    "#c9c3bd": "#3b352e", "#c9c0bf": "#3c3231", "#c6b3c3": "#382735",
    // Orchards, plant nurseries and vineyards (sage-10).
    [FILL_IN_COLOURS.orchardsEdge]: "#3a4b14",
  },
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
  // Orchards, plant nurseries and vineyards over the farmland fill (sage-10); each kind's pattern is a later step.
  insertAfter(layers, "landuse_settled", {
    id: "landuse_orchards",
    type: "fill",
    source: "protomaps",
    "source-layer": "landuse",
    filter: IS_ORCHARD,
    paint: {
      "fill-color": C.orchards,
      "fill-opacity": ["interpolate", ["linear"], ["zoom"], 6, 0.6, 9, 1],
    },
  });
  insertAfter(layers, "landuse_orchards", {
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
  const classColour = lowZoomFade(
    byClass(LOW_ZOOM.trunk, LOW_ZOOM.primary, LOW_ZOOM.secondary, LOW_ZOOM.tertiary, LOW_ZOOM.other),
    byClass(C.trunk, C.primary, C.secondary, C.tertiary, C.majorOther),
  );
  for (const id of ["roads_major", "roads_bridges_major"]) {
    const layer = byId(layers, id);
    layer.paint["line-width"] = MAJOR_WIDTH;
    layer.paint["line-color"] = classColour;
  }
  for (const id of ["roads_major_casing_early", "roads_major_casing_late", "roads_bridges_major_casing"]) {
    const layer = byId(layers, id);
    layer.paint["line-gap-width"] = MAJOR_WIDTH;
    if (id === "roads_major_casing_early") layer.paint["line-width"] = EARLY_CASING_WIDTH;
    if (id === "roads_major_casing_late") layer.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 12, 0.5, 13, 0.75, 15, 1];
    if (id === "roads_bridges_major_casing") layer.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 12, 0.6, 13, 1, 15, 1.5];
    layer.paint["line-color"] = ["match", ["get", "kind_detail"], ["tertiary", "tertiary_link"], C.tertiaryCasing, C.majorCasing];
  }

  // --- Minor roads from z11, their casing from z12 ---------------------------------------------------
  const minor = byId(layers, "roads_minor");
  minor.paint["line-width"] = MINOR_WIDTH;
  minor.paint["line-color"] = lowZoomFade(LOW_ZOOM.minor, "#ffffff", 11.5, 12);
  minor.paint["line-opacity"] = ["interpolate", ["linear"], ["zoom"], 10, 0.7, 11, 1];
  const minorCasing = byId(layers, "roads_minor_casing");
  minorCasing.paint["line-gap-width"] = MINOR_WIDTH;
  minorCasing.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 11.5, 0, 12, 0.5];

  // --- Motorways and their ramps in OSM Carto's pink, with its casing ----------------------------------------
  // The ramps (roads_link) keep their own widths; the motorways take Carto's, with its low-zoom pink to z11.5 and casing
  // to z12.5, and no casing below z11.
  for (const id of MOTORWAY_CENTRES) {
    const layer = byId(layers, id);
    layer.paint["line-color"] = id.includes("highway") ? lowZoomFade(LOW_ZOOM.motorway, C.motorway) : C.motorway;
    if (id.includes("highway")) layer.paint["line-width"] = MOTORWAY_WIDTH;
  }
  for (const id of MOTORWAY_CASINGS) {
    const layer = byId(layers, id);
    if (!id.includes("highway")) { layer.paint["line-color"] = C.motorwayCasing; continue; }
    layer.paint["line-color"] = lowZoomFade(LOW_ZOOM.motorwayCasing, C.motorwayCasing, 11.5, 12.5);
    layer.paint["line-gap-width"] = MOTORWAY_WIDTH;
    if (id === "roads_highway_casing_early") layer.paint["line-width"] = EARLY_CASING_WIDTH;
    if (id === "roads_highway_casing_late") layer.paint["line-width"] = ["interpolate", ["linear"], ["zoom"], 12, 0.5, 13, 0.75, 15, 1, 20, 15];
  }

  // --- Faint area edges, after the area fills, and the water's (night: the water rule paints them the water) ------
  insertAfter(layers, "landuse_pier", {
    id: "landuse_edges",
    type: "line",
    source: "protomaps",
    "source-layer": "landuse",
    minzoom: 13,
    filter: ["in", ["get", "kind"], ["literal", EDGE_COLOURS.flatMap(([kinds]) => kinds)]],
    paint: {
      // Orchards, plant nurseries and vineyards are kind farmland: their own edge first, then the edge by kind.
      "line-color": ["case", IS_ORCHARD, C.orchardsEdge,
        ["match", ["get", "kind"], ...EDGE_COLOURS.flatMap(([kinds, colour]) => [kinds.length === 1 ? kinds[0] : kinds, colour]), "#000000"]],
      "line-width": EDGE_WIDTH,
      "line-opacity": ["step", ["zoom"], ["match", ["get", "kind"], EDGES_FROM_Z14, 0, 1], 14, 1],
    },
  });
  insertAfter(layers, "landuse_edges", {
    id: "water_edges",
    type: "line",
    source: "protomaps",
    "source-layer": "water",
    minzoom: 13,
    filter: ["==", ["geometry-type"], "Polygon"],
    paint: { "line-color": C.waterEdge, "line-width": EDGE_WIDTH },
  });

  // --- Industrial in Carto's lilac; buildings in Carto's tan, with its z14 step and outline ----------------------
  byId(layers, "landuse_industrial").paint["fill-color"] = C.industrial;
  byId(layers, "buildings").paint["fill-color"] = ["step", ["zoom"], C.building, 14, C.buildingZ14, 15, C.building];
  byId(layers, "buildings_outline").paint["line-color"] = C.buildingOutline;

  // --- Creeks under the water (the owner: "Creeks under water (Recommended)") -------------------------------------
  // water_stream and water_river draw just before the water fill, so a lake or river polygon covers the line inside it
  // and the creek shows everywhere else. Nothing else moves, and no colour changes.
  const water = byId(layers, "water");
  for (const id of ["water_stream", "water_river"]) {
    const line = layers.splice(layers.indexOf(byId(layers, id)), 1)[0];
    layers.splice(layers.indexOf(water), 0, line);
  }

  // --- Rail, darker and stronger ---------------------------------------------------------------------
  const rail = byId(layers, "roads_rail");
  rail.paint["line-opacity"] = 0.85;
  rail.paint["line-color"] = C.rail;
  return layers;
}
