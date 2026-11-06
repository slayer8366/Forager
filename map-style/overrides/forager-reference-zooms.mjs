// Trail Map v4's first zooms: every layer's first zoom set to where the reference style first shows the same thing
// (dispatch 2026-11-02-coder-13; the owner approved the zoom from its renders and chose to update v4 in place).
// generate-trail.mjs applies it to v4's layers last, after overrides/forager-fill-in.mjs; v1 to v3 never see it.
//
// The reference style is drawn by the app as a 256 px raster, and MapLibre shows its raster level N from camera zoom
// N - 1.5; our 512 px vector tiles draw level z from camera z (the first-zoom comparison, A1b). So each layer's minzoom
// here is the reference style's N - 1.5, and patch 0008 in map-build puts the data in the tiles from level N - 2. Row
// numbers are the comparison's.
//
// Two ways a first zoom is set, because a filter's ["zoom"] is the tile's whole level, not the camera's:
//   - by kind: the layer's minzoom, splitting a layer whose kinds start at different zooms (split);
//   - by size or population, where the tiles carry each feature's level as min_zoom (its level + 1, patch 0008): one
//     layer per level, each with minzoom level + 0.5 (byLevel).
// A split layer keeps its id for one group, so what the app reads by id (night tables, pattern relayout) still finds
// it; each clone is named <id>__<group> and listed in CLONES, which the style records as forager:layer-clones. Things
// only one map draws (the comparison's B2) are left as they are.

/** A legacy filter (["in", "kind", ...], ["==", "kind", "x"], ["!has", "k"]) as an expression, so it can be combined. */
export const toExpr = (f) => {
  if (!Array.isArray(f)) return f;
  const [op, ...a] = f;
  const get = (k) => (k === "$type" ? ["geometry-type"] : ["get", k]);
  const legacyKey = typeof a[0] === "string";
  switch (op) {
    case "all":
    case "any":
      return [op, ...a.map(toExpr)];
    case "none":
      return ["!", ["any", ...a.map(toExpr)]];
    case "in":
    case "!in": {
      const legacy = legacyKey && a.length >= 2 && a.slice(1).every((x) => !Array.isArray(x));
      if (!legacy) return f;
      const e = ["in", get(a[0]), ["literal", a.slice(1)]];
      return op === "in" ? e : ["!", e];
    }
    case "==":
    case "!=":
    case "<":
    case "<=":
    case ">":
    case ">=":
      return legacyKey && !Array.isArray(a[1]) ? [op, get(a[0]), a[1]] : f;
    case "!has":
      return ["!", ["has", a[0]]];
    default:
      return f;
  }
};

export const isIn = (key, values) => ["in", ["get", key], ["literal", values]];
const minZoomAttr = (level) => ["==", ["get", "min_zoom"], level + 1];

/** clone id -> original id, filled in by split; the style records it as forager:layer-clones. */
export const CLONES = {};

const at = (layers, id) => {
  const i = layers.findIndex((l) => l.id === id);
  if (i < 0) throw new Error(`forager-reference-zooms: no layer ${id}`);
  return i;
};

/**
 * Split layer `id` by group. Each clone gets the original's filter and its group's condition, and its own minzoom;
 * the original keeps what no clone takes, at `keep.minzoom` (unchanged if undefined). Clones go right after the
 * original, in the order given: for symbols, the later layer is placed first.
 */
export function split(layers, id, keep, clones) {
  const i = at(layers, id);
  const orig = layers[i];
  const base = orig.filter === undefined ? null : toExpr(orig.filter);
  const withBase = (cond) => (base === null ? cond : ["all", base, cond]);
  const made = clones.map((c) => {
    const l = structuredClone(orig);
    l.id = `${id}__${c.name}`;
    l.filter = withBase(c.cond);
    l.minzoom = c.minzoom;
    if (c.patch) c.patch(l);
    CLONES[l.id] = id;
    return l;
  });
  orig.filter = withBase(["!", ["any", ...clones.map((c) => c.cond)]]);
  if (keep.minzoom !== undefined) orig.minzoom = keep.minzoom;
  if (keep.patch) keep.patch(orig);
  layers.splice(i + 1, 0, ...made);
}

/** A copy of layer `id` named <id>__<name>, right after it, with the same filter; each is then patched. */
export function duplicate(layers, id, name, patchClone, patchOrig) {
  const i = at(layers, id);
  const l = structuredClone(layers[i]);
  l.id = `${id}__${name}`;
  patchClone(l);
  patchOrig(layers[i]);
  CLONES[l.id] = CLONES[id] ?? id;
  layers.splice(i + 1, 0, l);
}

/** One clone per level the tiles can carry (min_zoom = level + 1), shown from level + 0.5; the original keeps `rest`. */
export function byLevel(layers, id, levels, rest, cond = null) {
  const and = (c) => (cond === null ? c : ["all", cond, c]);
  split(layers, id, { minzoom: rest + 0.5 },
    [...levels].sort((x, y) => y - x).map((z) => ({ name: `z${z}`, minzoom: z + 0.5, cond: and(minZoomAttr(z)) })));
}

export function set(layers, id, props) {
  const l = layers[at(layers, id)];
  for (const [k, v] of Object.entries(props)) {
    if (k === "line-width") l.paint["line-width"] = v;
    else l[k] = v;
  }
}

const range = (a, b) => Array.from({ length: b - a + 1 }, (_, k) => a + k);

/** v4's layers (applyFillIn's output) with the reference style's first zooms; mutates and returns the array. */
export function applyReferenceZooms(layers) {
  // Area fills and patterns (rows 1-25)
  split(layers, "landuse_park", { minzoom: 3.5 }, [ // forest, wood, scrub, grass, meadow, heath, sand, glacier, wetland, bare rock: 5
    { name: "military", minzoom: 6.5, cond: isIn("kind", ["military", "naval_base", "airfield"]) }, // row 18: 8
    { name: "cemetery_golf", minzoom: 8.5, cond: isIn("kind", ["cemetery", "golf_course"]) }, // row 13: 10
  ]);
  split(layers, "landuse_settled", { minzoom: 3.5 }, [ // farmland: 5 (row 5)
    { name: "urban", minzoom: 6.5, cond: isIn("kind", ["residential", "commercial"]) }, // row 11: 8
    { name: "railway", minzoom: 8.5, cond: isIn("kind", ["railway"]) }, // row 12: 10
  ]);
  split(layers, "landuse_orchards", { minzoom: 3.5 }, [ // orchard, vineyard: 5 (row 6)
    { name: "nursery", minzoom: 8.5, cond: isIn("kind_detail", ["plant_nursery"]) }, // row 7: 10
  ]);
  set(layers, "landuse_orchards_pattern", { minzoom: 11.5 }); // row 8: 13
  set(layers, "landuse_parks_fill", { minzoom: 8.5 }); // row 13: 10
  split(layers, "landuse_urban_green", { minzoom: 8.5 }, [ // allotments: 10 (row 13)
    { name: "village_green", minzoom: 3.5, cond: isIn("kind", ["village_green"]) }, // row 13: 5
    { name: "playground", minzoom: 11.5, cond: isIn("kind", ["playground"]) }, // row 14: 13
  ]);
  set(layers, "landuse_forest_glyphs", { minzoom: 11.5 }); // row 2: 13
  set(layers, "landuse_wetland_tufts", { minzoom: 8.5 }); // row 10: 10
  set(layers, "landuse_hospital", { minzoom: 8.5 }); // row 15: 10
  set(layers, "landuse_school", { minzoom: 8.5 }); // row 15: 10
  set(layers, "landuse_industrial", { minzoom: 6.5 }); // row 11: 8
  set(layers, "landuse_beach", { minzoom: 8.5 }); // row 16: 10
  set(layers, "landuse_aerodrome", { minzoom: 8.5 }); // row 17: 10
  set(layers, "landuse_runway", { minzoom: 12.5 }); // row 20: 14
  set(layers, "landuse_pier", { minzoom: 10.5 }); // row 21: 12
  split(layers, "landuse_pedestrian", { minzoom: 13.5 }, [ // pedestrian areas: 15 (row 23)
    { name: "dam", minzoom: 11.5, cond: isIn("kind", ["dam"]) }, // row 22: 13
  ]);
  set(layers, "buildings", { minzoom: 12.5 }); // row 24: 14
  set(layers, "buildings_outline", { minzoom: 13.5 }); // row 25: 15

  // Outlines (rows 26-29); landuse_edges' other kinds are ours alone (B2) and keep 13.
  set(layers, "landuse_park_outline", { minzoom: 6.5 }); // row 26: 8
  split(layers, "landuse_edges", {}, [
    { name: "institutions", minzoom: 11.5, cond: isIn("kind", ["hospital", "school", "university", "college"]) }, // row 27: 13
    { name: "settled", minzoom: 14.5, cond: isIn("kind", ["residential", "commercial", "industrial", "farmland", "allotments"]) }, // row 28: 16
    { name: "sport", minzoom: 13.5, cond: isIn("kind", ["pitch", "recreation_ground", "playground"]) }, // row 29: 15
  ]);

  // Water (rows 30-33)
  set(layers, "water_river", { minzoom: 6.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 6.5, 0.6, 9.5, 1, 18, 12] }); // row 32: 8
  byLevel(layers, "water_stream", [11], 10); // row 33: streams 12 (level 10, the rest), intermittent and seasonal 13 (level 11)

  // Roads and paths (rows 35-67). Bridges and tunnels keep their layers' zooms: their attributes start at level N - 2,
  // and a later minzoom would leave a gap where the plain road layer has already let them go (rows 60-63, -0.5).
  set(layers, "roads_highway", { minzoom: 4.5 }); // row 35: 6
  set(layers, "roads_highway_casing_early", { minzoom: 4.5 });
  split(layers, "roads_major", { minzoom: 4.5 }, [ // trunk: 6 (row 37)
    { name: "primary", minzoom: 6.5, cond: isIn("kind_detail", ["primary"]) }, // row 38: 8
    { name: "secondary", minzoom: 7.5, cond: isIn("kind_detail", ["secondary"]) }, // row 39: 9
    { name: "tertiary", minzoom: 8.5, cond: isIn("kind_detail", ["tertiary"]) }, // row 40: 10
    { name: "links", minzoom: 8.5, cond: isIn("kind_detail", ["trunk_link", "primary_link", "secondary_link", "tertiary_link"]) }, // row 41: 10
  ]);
  set(layers, "roads_link", { minzoom: 8.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 8.5, 0.5, 13.5, 1, 18, 11] }); // row 41: 10
  split(layers, "roads_minor", { minzoom: 10.5 }, [ // residential, unclassified, raceway: 12 (rows 42, 48)
    { name: "road", minzoom: 8.5, cond: isIn("kind_detail", ["road"]) }, // row 43: 10
  ]);
  set(layers, "roads_minor_service", { minzoom: 12.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 12.5, 0.3, 18, 8] }); // row 45: 14
  split(layers, "roads_other", { minzoom: 12.5 }, [ // pedestrian, sidewalk, crossing: 14 (rows 47, 53)
    { name: "living_street", minzoom: 11.5, cond: isIn("kind_detail", ["living_street"]) }, // row 44: 13
  ]);
  set(layers, "roads_path_track", { minzoom: 11.5 }); // row 49: 13
  for (const id of ["roads_path_informal_z13", "roads_path_unknown_z13", "roads_path_hard_z13"]) {
    split(layers, id, { minzoom: 12.5 }, [ // path, footway: 14 (rows 50, 51)
      { name: "cycle_bridle", minzoom: 11.5, cond: isIn("kind_detail", ["cycleway", "bridleway"]) }, // row 52: 13
    ]);
  }
  set(layers, "roads_path_steps", { minzoom: 12.5 }); // row 51: 14
  split(layers, "roads_rail", { minzoom: 6.5 }, [ // rail, light rail, narrow gauge, funicular: 8 (rows 56, 57)
    { name: "service", minzoom: 11.5, cond: isIn("service", ["spur", "siding", "yard"]) }, // row 59: 13
    { name: "urban", minzoom: 10.5, cond: isIn("kind_detail", ["tram", "subway", "preserved"]) }, // row 58: 12
    { name: "disused", minzoom: 13.5, cond: isIn("kind_detail", ["disused"]) }, // the reference style: 15
  ]);
  set(layers, "roads_pier", { minzoom: 10.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 10.5, 0.3, 12.5, 0.5, 20, 16] }); // row 66: 12
  set(layers, "roads_runway", { minzoom: 9.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 9.5, 0.5, 12, 4, 18, 30] }); // row 64: 11
  set(layers, "roads_taxiway", { minzoom: 9.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 9.5, 0.3, 13.5, 1, 15, 6] }); // row 65: 11
  set(layers, "roads_oneway", { minzoom: 14.5 }); // row 67: 16

  // Boundaries (rows 68-72)
  set(layers, "boundaries_country", { minzoom: 2.5 }); // row 68: 4
  split(layers, "boundaries", { minzoom: 2.5 }, [ // admin 3 and 4: 4 (row 68)
    { name: "admin5", minzoom: 6.5, cond: ["==", ["get", "kind_detail"], 5] }, // row 69: 8
    { name: "admin6", minzoom: 8.5, cond: ["==", ["get", "kind_detail"], 6] }, // row 70: 10
    { name: "admin7", minzoom: 9.5, cond: ["==", ["get", "kind_detail"], 7] }, // row 71: 11
    { name: "admin8", minzoom: 10.5, cond: [">=", ["get", "kind_detail"], 8] }, // row 72: 12
  ]);

  // Names, shields and labels (rows 73-116)
  split(layers, "roads_labels_major", { minzoom: 11.5 }, [ // motorway to secondary: 13 (rows 73, 74)
    { name: "tertiary", minzoom: 12.5, cond: isIn("kind_detail", ["tertiary"]) }, // row 75: 14
  ]);
  split(layers, "roads_labels_minor", { minzoom: 13.5 }, [ // streets, living and pedestrian streets, tracks: 15 (rows 76-78)
    {
      name: "service_paths", minzoom: 14.5, // rows 79, 80: 16
      cond: ["any", isIn("kind_detail", ["service"]),
        ["all", ["==", ["get", "kind"], "path"], ["!", isIn("kind_detail", ["track", "pedestrian"])]]],
    },
  ]);
  split(layers, "roads_shields", { minzoom: 8.5 }, [ // motorway: 10 (row 81); links keep their tiles' zooms (B2)
    { name: "trunk_primary", minzoom: 9.5, cond: isIn("kind_detail", ["trunk", "primary"]) }, // rows 82, 83: 11
    { name: "secondary", minzoom: 10.5, cond: isIn("kind_detail", ["secondary"]) }, // row 84: 12
    { name: "tertiary", minzoom: 11.5, cond: isIn("kind_detail", ["tertiary"]) }, // row 85: 13
  ]);
  split(layers, "water_waterway_label", { minzoom: 13.5 }, [ // streams: 15 (row 87)
    { name: "river", minzoom: 11.5, cond: isIn("kind", ["river"]) }, // row 86: 13
  ]);
  byLevel(layers, "water_label_lakes", range(3, 14), 15); // row 88: 5 once over 3000 px, else 17 (patch 0008's levels)
  split(layers, "water_label_ocean", {}, [ // sea and ocean names are ours alone (B2)
    { name: "bays", minzoom: 12.5, cond: isIn("kind", ["bay", "strait", "fjord"]) }, // row 89: 14
  ]);
  set(layers, "earth_label_islands", { minzoom: 2.5 }); // row 101: 4 once over 3000 px (smaller ones -0.5: no min_zoom)
  set(layers, "places_country", { minzoom: 1.5 }); // row 90: 3
  set(layers, "places_region", { minzoom: 3.5 }); // row 91: 5
  split(layers, "places_locality", {}, [
    ...[5, 4, 3, 2].map((z) => ({ name: `city_z${z}`, minzoom: z + 0.5, // row 92: 4-7 by the reference style's city score
      cond: ["all", isIn("kind_detail", ["city"]), minZoomAttr(z)] })),
    { name: "town", minzoom: 7.5, cond: isIn("kind_detail", ["town"]) }, // row 93: 9
    { name: "village", minzoom: 10.5, cond: isIn("kind_detail", ["village"]) }, // row 94: 12
    { name: "hamlet", minzoom: 12.5, cond: isIn("kind_detail", ["hamlet"]) }, // row 95: 14
    { name: "dwelling", minzoom: 13.5, cond: isIn("kind_detail", ["isolated_dwelling", "farm"]) }, // row 96: 15
    { name: "locality", minzoom: 14.5, cond: isIn("kind_detail", ["locality"]) }, // row 97: 16
  ]);
  split(layers, "places_subplace", { minzoom: 13.5 }, [ // neighbourhood: 15 (row 100)
    { name: "suburb", minzoom: 10.5, cond: isIn("kind_detail", ["suburb"]) }, // row 98: 12
    { name: "quarter", minzoom: 12.5, cond: isIn("kind_detail", ["quarter"]) }, // row 99: 14
  ]);

  // Area names (rows 102-104): the reference style's size rule is in the tiles' min_zoom (patch 0008), so v4's own zoom
  // and name rules give way to one layer per level.
  set(layers, "pois_area_names", { filter: isIn("kind", ["nature_reserve", "national_park", "protected_area", "forest", "park"]) });
  byLevel(layers, "pois_area_names", range(6, 14), 15);

  // POIs (rows 105-115). v4's zoom terms leave the filter; each family gets its own layer and minzoom.
  const POI_KINDS_A = ["trailhead", "parking", "camp_site", "picnic_site", "viewpoint", "peak", "toilets", "drinking_water",
    "ranger_station", "saddle", "shelter", "alpine_hut", "wilderness_hut"];
  const POI_KINDS_B = ["restaurant", "fast_food", "cafe", "bar", "supermarket", "convenience", "books", "clothes", "beauty",
    "electronics"];
  set(layers, "pois", { filter: isIn("kind", [...POI_KINDS_A, ...POI_KINDS_B]) });
  split(layers, "pois", {}, [ // trailheads and ranger stations are ours alone (B2) and keep the layer's zoom
    { name: "toilets", minzoom: 16.5, cond: isIn("kind", ["toilets"]) }, // row 112: 18
    { name: "food", minzoom: 15.5, cond: isIn("kind", ["restaurant", "fast_food", "cafe", "bar"]) }, // row 113: 17
    { name: "shops", minzoom: 15.5, cond: isIn("kind", ["convenience", "books", "clothes", "beauty", "electronics"]) }, // row 115: 17
    { name: "drinking_water", minzoom: 15.5, cond: isIn("kind", ["drinking_water"]) }, // row 111: 17
    { name: "parking_small", minzoom: 15.5, cond: ["all", isIn("kind", ["parking"]), ["!", minZoomAttr(12)]] }, // row 110: 17
    { name: "supermarket", minzoom: 14.5, cond: isIn("kind", ["supermarket"]) }, // row 114: 16
    { name: "camp", minzoom: 14.5, cond: isIn("kind", ["camp_site", "picnic_site", "viewpoint", "shelter"]) }, // row 109: 16
    { name: "parking_large", minzoom: 12.5, cond: ["all", isIn("kind", ["parking"]), minZoomAttr(12)] }, // row 110: 14
    { name: "huts", minzoom: 11.5, cond: isIn("kind", ["alpine_hut", "wilderness_hut"]) }, // row 107: 13
    { name: "peak", minzoom: 9.5, cond: isIn("kind", ["peak"]) }, // row 105: 11
  ]);
  set(layers, "pois", { minzoom: 12 }); // trailhead, ranger station (and saddle, which no tile holds): v4's 12
  // A peak's icon from 11 but its name and height from 13 (row 106): the icon alone from 9.5 to 11.5, then the icon
  // with its text.
  duplicate(layers, "pois__peak", "named", (named) => { named.minzoom = 11.5; },
    (icon) => { icon.maxzoom = 11.5; icon.layout["text-field"] = ""; });

  set(layers, "address_label", { minzoom: 15.5 }); // row 116: 17
  return layers;
}
