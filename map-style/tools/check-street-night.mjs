// Checks forager-street-v2.json (or, with the argument v4, forager-street-v4.json: the fill-in) (dispatch 2026-10-18-05 Part 2; v2 since dispatch 2026-10-22-03, RECORD -1561): it
// is valid style-spec JSON, it is the same file the Worker bundles, v1 is still byte for byte what phones with older
// apps and older downloads load, and the day colours the owner's night ruling depends on (-1288: "the day palette is
// chosen so roads and paths still stand out after inversion") hold their contrast after the app's V1 inversion, with
// the night exceptions held to their ruled limits. Run: npm run check:street. Exits non-zero, saying what failed.
//
// V1 is the app's night transform, per channel: night = clamp(c + 1 - 2 * mean(r, g, b))
// (app/src/main/java/com/zynergylabs/forager/app/ui/map/NightColour.kt, nightColorOf). Contrast is the
// WCAG ratio of relative luminances. The thresholds are this check's own operating limits, not a
// standard for maps: a path or casing line at 3:1 against the ground reads at arm's length; text at 4.5:1.
import crypto from "node:crypto";
import fs from "node:fs";
import { validateStyleMin } from "@maplibre/maplibre-gl-style-spec";
import { COLOURS, NIGHT_ICON_RINGS, NIGHT_LAYER_BANDS, NIGHT_LAYER_COLOURS as NIGHT_LAYER_COLOURS_V2 } from "../overrides/forager-street.mjs";
import { CHARACTER_COLOURS, NIGHT_FILL_COLOURS as NIGHT_FILL_COLOURS_V2, NIGHT_LIMITS, NIGHT_PATTERN_COLOURS } from "../overrides/forager-character.mjs";
import { FILL_IN_COLOURS, NIGHT_FILL_COLOURS_V4, NIGHT_LAYER_COLOURS_V4, NIGHT_LINE_COLOURS_V4 } from "../overrides/forager-fill-in.mjs";

const here = (path) => new URL(`../${path}`, import.meta.url);
const problems = [];

// `node tools/check-street-night.mjs v4` checks forager-street-v4.json (the fill-in) against v4's own night tables; v4
// is not on the Worker yet, so there is no bundled copy to compare. With no argument it checks v2, as before.
const V4 = process.argv[2] === "v4";
const STYLE = V4 ? "v4" : "v2";
const NIGHT_LAYER_COLOURS = V4 ? NIGHT_LAYER_COLOURS_V4 : NIGHT_LAYER_COLOURS_V2;
const NIGHT_FILL_COLOURS = V4 ? NIGHT_FILL_COLOURS_V4 : NIGHT_FILL_COLOURS_V2;
const text = fs.readFileSync(here(`forager-street-${STYLE}.json`), "utf8");
if (!V4) {
  const bundled = fs.readFileSync(here("../server/pmtiles-worker/src/street-style-v2.json"), "utf8");
  if (text !== bundled) problems.push("server/pmtiles-worker/src/street-style-v2.json differs from forager-street-v2.json: run npm run generate:street");
}
// The style's own metadata must carry the tables checked here: the app reads its copy against it.
const meta = JSON.parse(text).metadata;
if (JSON.stringify(meta["forager:night-layers"]) !== JSON.stringify(NIGHT_LAYER_COLOURS)) problems.push(`forager-street-${STYLE}.json's forager:night-layers differs from its design table: run npm run generate:street`);
if (JSON.stringify(meta["forager:night-fills"]) !== JSON.stringify(NIGHT_FILL_COLOURS)) problems.push(`forager-street-${STYLE}.json's forager:night-fills differs from its design table: run npm run generate:street`);
const errors = validateStyleMin(JSON.parse(text));
for (const e of errors) problems.push(`style spec: ${e.message}`);

// v1 is frozen (RECORD -1561): what it was at pre-main 563e9b756, byte for byte, in all three copies.
const V1_SHA256 = {
  "forager-street-v1.json": "b2ec485ddbe65635a1b2a0f1ef5b51bbb86f6432eec1fd6896bcf4c8979bd6da",
  "forager-street-v1.preview.json": "4fc2e14feeecfdf86bc7a4f6d7597eb997732ee97620388fba69ea379926dc88",
  "../server/pmtiles-worker/src/street-style-v1.json": "b2ec485ddbe65635a1b2a0f1ef5b51bbb86f6432eec1fd6896bcf4c8979bd6da",
};
for (const [path, sum] of Object.entries(V1_SHA256)) {
  const actual = crypto.createHash("sha256").update(fs.readFileSync(here(path))).digest("hex");
  if (actual !== sum) problems.push(`${path} changed (sha256 ${actual}): v1 is frozen, the design ships as v2`);
}

const channels = (hex) => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
const toHex = (c) => "#" + c.map((x) => Math.round(Math.min(1, Math.max(0, x)) * 255).toString(16).padStart(2, "0")).join("");
export const night = (hex) => {
  const c = channels(hex);
  const mean = (c[0] + c[1] + c[2]) / 3;
  return toHex(c.map((x) => x + 1 - 2 * mean));
};
const luminance = (hex) => {
  const [r, g, b] = channels(hex).map((x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

const GROUND = "#e2dfda"; // the earth fill, unchanged from Protomaps light
const checks = [
  // [what, colour, against, minimum]
  ["footpath on ground", COLOURS.footpath, GROUND, 3],
  ["footpath on forest", COLOURS.footpath, COLOURS.forest, 2],
  ["track on ground", COLOURS.track, GROUND, 3],
  ["track on forest", COLOURS.track, COLOURS.forest, 2],
  ["highway casing on ground", COLOURS.highwayCasing, GROUND, 2],
  ["major road casing on ground", COLOURS.majorCasing, GROUND, 2],
  ["minor road casing on ground", COLOURS.minorCasing, GROUND, 2],
  ["park outline on ground", COLOURS.parkOutline, GROUND, 3],
  ["label text on its halo", COLOURS.text, COLOURS.halo, 4.5],
  ["water label text on its halo", COLOURS.waterText, COLOURS.halo, 4.5],
  ["forest on ground", COLOURS.forest, GROUND, 1.2],
  // Hard trails (dispatch 2026-10-19-41): the warning colour by day, and its plain inversion at night.
  ["hard trail on ground", COLOURS.hardPath, GROUND, 3],
  ["hard trail on forest", COLOURS.hardPath, COLOURS.forest, 2],
];
for (const [what, colour, against, min] of checks) {
  const day = contrast(colour, against);
  const dark = contrast(night(colour), night(against));
  const line = `${what}: day ${colour} ${day.toFixed(2)}:1, night ${night(colour)} on ${night(against)} ${dark.toFixed(2)}:1 (minimum ${min})`;
  console.log(line);
  if (day < min || dark < min) problems.push(line);
}
// Day only, for colours whose night is a layer exception (checked below), not their inversion.
const dayOnly = [
  ["informal path on ground", COLOURS.informalPath, GROUND, 3],
  ["informal path on forest", COLOURS.informalPath, COLOURS.forest, 2],
];
for (const [what, colour, against, min] of dayOnly) {
  const day = contrast(colour, against);
  const line = `${what}: day ${colour} ${day.toFixed(2)}:1 (minimum ${min}; night is its layer exception)`;
  console.log(line);
  if (day < min) problems.push(line);
}
// Night exceptions (RECORD -1343, -1344): colours the app draws at night instead of the inversion. Checked as night
// colours as they are, against the night grounds they sit on. Water's own rule is below (the ceiling and 1.05:1).
const exceptions = [];
// -1345: roads, railways and boundaries at 2.5:1 (the owner accepted the service edge at 2.65 and the major road
// centre at 2.95 on the night forest); the icon rings at 3:1. Every layer named must exist in the style.
// A layer named in NIGHT_LAYER_BANDS takes its band on that one ground instead of the 2.5 floor (dispatch 2026-10-19-41:
// informal paths on the night forest, the owner's "Accept the range (Recommended)"); every other pair keeps the floor.
const styleLayers = new Set(JSON.parse(text).layers.map((l) => l.id));
const NIGHT_FLOOR = 2.5;
// The night forest is its fill exception (#446835, dispatch 2026-10-22-03), not the inversion: every floor on it is
// checked against what the app draws.
const nightForest = NIGHT_FILL_COLOURS[COLOURS.forest];
if (!nightForest) problems.push(`no night fill exception for the forest ${COLOURS.forest}`);
const nightGrounds = { forest: nightForest, grass: night(COLOURS.grass), ground: night(GROUND) };
const banded = [];
for (const [layer, bands] of Object.entries(NIGHT_LAYER_BANDS)) {
  if (!(layer in NIGHT_LAYER_COLOURS)) problems.push(`night band for a layer with no night exception: ${layer}`);
  for (const name of Object.keys(bands)) {
    if (!(name in nightGrounds)) problems.push(`night band for ${layer} on an unknown ground: ${name}`);
  }
}
for (const [layer, colour] of Object.entries(NIGHT_LAYER_COLOURS)) {
  if (!styleLayers.has(layer)) problems.push(`night exception for a layer the style does not have: ${layer}`);
  for (const [name, ground] of Object.entries(nightGrounds)) {
    const band = NIGHT_LAYER_BANDS[layer]?.[name];
    if (band) banded.push([layer, name, colour, ground, band]);
    else exceptions.push([`night ${layer} on night ${name}`, colour, ground, NIGHT_FLOOR]);
  }
}
for (const [layer, name, colour, ground, { min, max, belowNightOf }] of banded) {
  const ratio = contrast(colour, ground);
  const ceiling = contrast(night(COLOURS[belowNightOf]), ground);
  const line = `night ${layer} on night ${name}: ${colour} on ${ground} ${ratio.toFixed(2)}:1 (band ${min} to ${max}, below night ${belowNightOf} ${ceiling.toFixed(2)})`;
  console.log(line);
  if (ratio < min || ratio > max || ratio >= ceiling) problems.push(line);
}
for (const [icon, { night: colour }] of Object.entries(NIGHT_ICON_RINGS)) {
  for (const [name, ground] of Object.entries(nightGrounds)) {
    exceptions.push([`night ${icon} ring on night ${name}`, colour, ground, 3]);
  }
}
// --- Dispatch 2026-10-22-03: the ruled night limits ("Rules from your colours", NIGHT_LIMITS) -----------------------
const style2 = JSON.parse(text);
/** Every colour literal in a style value, as #rrggbb (rgba() strings with alpha 1 too). */
function colourLiterals(value) {
  if (typeof value === "string") {
    if (/^#[0-9a-f]{6}$/i.test(value)) return [value.toLowerCase()];
    const m = /^rgba?\(\s*(\d+),\s*(\d+),\s*(\d+)(?:,\s*1)?\s*\)$/.exec(value);
    return m ? ["#" + m.slice(1, 4).map((c) => Number(c).toString(16).padStart(2, "0")).join("")] : [];
  }
  return Array.isArray(value) ? value.flatMap(colourLiterals) : [];
}
// 1. The night water: at most the ruled luminance and blue channel. Today's #8ec5f0 (-1343) fails this.
const water = COLOURS.nightWater;
const waterLine = `night water ${water}: luminance ${luminance(water).toFixed(4)} (at most ${NIGHT_LIMITS.waterLuminanceMax}), blue ${channels(water)[2] * 255} (at most ${NIGHT_LIMITS.waterBlueMax})`;
console.log(waterLine);
if (luminance(water) > NIGHT_LIMITS.waterLuminanceMax || channels(water)[2] * 255 > NIGHT_LIMITS.waterBlueMax) problems.push(waterLine);
// 2. The night water against every night land fill the style draws: every fill and background colour outside the water
//    source layer, at its night (the fill exception where one is named, else the inversion).
const landFills = new Map();
for (const layer of style2.layers) {
  if (!["fill", "background"].includes(layer.type) || layer["source-layer"] === "water") continue;
  for (const day of colourLiterals(layer.paint?.["fill-color"] ?? layer.paint?.["background-color"])) {
    landFills.set(day, [...(landFills.get(day) ?? []), layer.id]);
  }
}
for (const [day, ids] of landFills) {
  const land = NIGHT_FILL_COLOURS[day] ?? night(day);
  exceptions.push([`night water on night ${day} (${[...new Set(ids)].join(", ")})`, water, land, NIGHT_LIMITS.waterOnLandMin]);
}
// 3. Each fill exception names a day colour the style draws, so none is dead; each pattern painted at night is an image
//    the style draws with fill-pattern; and every fill-pattern image is in the sprite.
for (const day of Object.keys(NIGHT_FILL_COLOURS)) {
  if (!landFills.has(day)) problems.push(`night fill exception for ${day}, a colour no fill layer draws`);
}
const patterns = new Set(style2.layers.map((l) => l.paint?.["fill-pattern"]).filter(Boolean));
const sprite = JSON.parse(fs.readFileSync(here(`${style2.sprite.replace(/^https?:\/\/[^/]+\//, "assets/").replace(/^assets\/assets\//, "assets/")}.json`), "utf8"));
for (const name of patterns) if (!(name in sprite)) problems.push(`fill-pattern ${name} is not in the sprite`);
for (const name of Object.keys(NIGHT_PATTERN_COLOURS)) if (!patterns.has(name)) problems.push(`night pattern ${name} is drawn by no layer`);
// 4. The tree glyph against the night forest.
exceptions.push(["night tree glyph on night forest", NIGHT_PATTERN_COLOURS.tree_dark_irregular, nightForest, NIGHT_LIMITS.treeOnForestMin]);
// The other new fills (wetland, bare rock) and the tufts take the floor the ground fills have: none. They are listed so
// the figures are in the output, not gated (the owner ruled the colours from renders).
for (const [what, colour, against] of [
  ["night wetland on night ground", CHARACTER_COLOURS.nightWetland, night(GROUND)],
  ["night tufts (inverted) on night wetland", night(CHARACTER_COLOURS.tufts), CHARACTER_COLOURS.nightWetland],
  ["night bare rock on night ground", CHARACTER_COLOURS.nightBareRock, night(GROUND)],
]) console.log(`${what}: ${colour} on ${against} ${contrast(colour, against).toFixed(2)}:1 (recorded, not gated)`);

// 5. v4's ramps (the fill-in rulings, "Ramps", the owner: "Approve the fix (Recommended)"): roads_major draws the trunk to
//    tertiary ramps, and each takes its road's day colour, so at night it takes the same inversion ("Invert them too").
//    roads_link and roads_bridges_link draw only motorway ramps, which take the motorways' colours (6).
if (V4) {
  for (const id of ["roads_major", "roads_bridges_major"]) {
    const value = style2.layers.find((l) => l.id === id)?.paint?.["line-color"];
    // Since the low-zoom roads, a zoom cross-fade between two matches on kind_detail: each ramp matches its road in both.
    const matches = Array.isArray(value) && value[0] === "interpolate" ? value.slice(3).filter((_, i) => i % 2 === 1) : [value];
    for (const match of matches) {
      if (!Array.isArray(match) || match[0] !== "match" || JSON.stringify(match[1]) !== '["get","kind_detail"]') {
        problems.push(`${id}'s line-color is not a match on kind_detail`);
        continue;
      }
      const colourOf = (kind) => {
        for (let i = 2; i < match.length - 1; i += 2) if ([match[i]].flat().includes(kind)) return match[i + 1];
        return match[match.length - 1];
      };
      for (const road of ["trunk", "primary", "secondary", "tertiary"]) {
        const line = `${id}: ${road}_link draws ${colourOf(`${road}_link`)}, its road ${road} ${colourOf(road)}`;
        console.log(line);
        if (colourOf(`${road}_link`) !== colourOf(road)) problems.push(line);
      }
    }
  }
}

// 6. v4's motorways (the fill-in rulings, "Motorways": the owner, "A: OSM pink (Recommended)" by day and "Invert like OSM
//    (Recommended)" at night): OSM Carto's fill and casing (style/road-colors-generated.mss at 9295745d, lines 11 and 7),
//    on the motorways and their ramps; tunnels keep their grey. No night exception, so each takes the plain inversion.
if (V4) {
  // The ruled colour is the day colour from z12.5 up: since the low-zoom roads, the last stop of a zoom cross-fade (9).
  const dayOf = (id) => {
    const v = style2.layers.find((l) => l.id === id)?.paint?.["line-color"];
    return Array.isArray(v) && v[0] === "interpolate" ? v[v.length - 1] : v;
  };
  const ruled = {
    "#e892a2": ["roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link"],
    "#dc2a67": ["roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing", "roads_link_casing", "roads_bridges_link_casing"],
  };
  for (const [colour, ids] of Object.entries(ruled)) {
    for (const id of ids) {
      const line = `${id}: day ${dayOf(id)}, ruled ${colour}; night ${NIGHT_LAYER_COLOURS[id] ?? `the inversion ${night(colour)}`}`;
      console.log(line);
      if (dayOf(id) !== colour || id in NIGHT_LAYER_COLOURS) problems.push(line);
    }
  }
  for (const id of ["roads_tunnels_highway", "roads_tunnels_link"]) {
    if (dayOf(id) !== "#d5d5d5") problems.push(`${id}: day ${dayOf(id)}, tunnels keep #d5d5d5`);
  }
}

// 7. v4's night tables are the ruled ones (the fill-in rulings' table, "Motorways" and "Night tertiary and rail"), by hand,
//    so a design table that drifts with its metadata still fails here (Sorrel's should-fix 2: before this, dropping any
//    settled fill's night colour passed). Every fill-in day colour needs its night fill; the major roads, the motorways
//    and their ramps, and the rail take the inversion (no layer exception), and the tertiary's day white is #333333.
if (V4) {
  const RULED_FILLS = { residential: "#2d2a25", commercial: "#362824", railway: "#2b2829", farmland: "#2e2a10", parks: "#2c461e" };
  for (const [kind, colour] of Object.entries(RULED_FILLS)) {
    const day = FILL_IN_COLOURS[kind];
    if (NIGHT_FILL_COLOURS[day] !== colour) problems.push(`night fill for ${kind} ${day} is ${NIGHT_FILL_COLOURS[day]}, ruled ${colour}`);
  }
  const inverted = ["roads_major", "roads_bridges_major", "roads_highway", "roads_bridges_highway", "roads_link", "roads_bridges_link", "roads_rail"];
  for (const id of inverted) if (id in NIGHT_LAYER_COLOURS) problems.push(`${id} has a night exception ${NIGHT_LAYER_COLOURS[id]}; ruled: the inversion`);
  const lines = JSON.parse(text).metadata["forager:night-line-colours"];
  if (JSON.stringify(lines) !== JSON.stringify(NIGHT_LINE_COLOURS_V4)) problems.push(`forager-street-v4.json's forager:night-line-colours differs from its design table: run npm run generate:street`);
  // Night line colours by day colour (the rulings' "Night tertiary and rail", then "Low-zoom roads, night borders": the
  // minor roads' white takes the tertiary's #333333 and its band; landuse_edges take Sage's REC darker rims, recorded
  // in the output, not gated).
  const NIGHT_RIMS = {
    "#94b988": "#38552b", "#c2cdaa": "#1d240b", "#b4caa8": "#203216", "#88b99d": "#255239", "#adc8bc": "#203c36",
    "#b0aba4": "#5d636c", "#c3bfba": "#1d1b18", "#d0bdb9": "#241a18", "#c4bfc2": "#1a1919", "#ccceb4": "#131207",
    "#c9c3bd": "#3b352e", "#c9c0bf": "#3c3231", "#c6b3c3": "#382735",
    // Orchards, plant nurseries and vineyards (11).
    "#b0c286": "#3a4b14",
  };
  const RULED_LINES = {
    roads_major: { "#ffffff": "#333333" },
    roads_bridges_major: { "#ffffff": "#333333" },
    roads_minor: { "#ffffff": "#333333" },
    landuse_edges: NIGHT_RIMS,
  };
  if (JSON.stringify(NIGHT_LINE_COLOURS_V4) !== JSON.stringify(RULED_LINES)) problems.push(`v4's night line colours are ${JSON.stringify(NIGHT_LINE_COLOURS_V4)}, ruled ${JSON.stringify(RULED_LINES)}`);
  // Each named day colour is one its layer draws, so none is dead.
  for (const [id, colours] of Object.entries(NIGHT_LINE_COLOURS_V4)) {
    const drawn = colourLiterals(style2.layers.find((l) => l.id === id)?.paint?.["line-color"]);
    for (const day of Object.keys(colours)) if (!drawn.includes(day)) problems.push(`night line colour for ${id} ${day}, a colour it does not draw`);
  }
  // The night tertiary's band (Aspen's call, the rulings' "Night tertiary and rail"): on the night forest 1.7 to 2.3:1 and
  // below the night footpath; on the night grass and ground at most the night footpath's figure. Trails stay the
  // strongest line.
  const tertiary = RULED_LINES.roads_major["#ffffff"];
  const footpath = night(COLOURS.footpath);
  for (const [name, ground] of Object.entries(nightGrounds)) {
    const ratio = contrast(tertiary, ground);
    const ceiling = contrast(footpath, ground);
    const inBand = name === "forest" ? ratio >= 1.7 && ratio <= 2.3 && ratio < ceiling : ratio <= ceiling;
    const line = `night tertiary ${tertiary} on night ${name} ${ground}: ${ratio.toFixed(2)}:1 (${name === "forest" ? "band 1.7 to 2.3, " : ""}night footpath ${footpath} ${ceiling.toFixed(2)})`;
    console.log(line);
    if (!inBand) problems.push(line);
  }
}

// 8. v4's borders, industrial and buildings (the fill-in rulings, "Borders, industrial and buildings": "Faint", "No edge
//    at night", "Lilac, like OSM", "Tan, like OSM", then "OSM's #b9a99c", "Keep OSM's steps", "One step darker #ccc0b7",
//    "OSM's #c6b3c3"). By hand, from the rulings. Night is the plain inversion (no exception); the night water floor on the
//    new fills is held above with every land fill.
if (V4) {
  const layerOf = (id) => style2.layers.find((l) => l.id === id);
  const order = style2.layers.map((l) => l.id);
  const at = order.indexOf("landuse_pier");
  if (order[at + 1] !== "landuse_edges" || order[at + 2] !== "water_edges") problems.push(`landuse_edges and water_edges are not right after landuse_pier: ${order.slice(at, at + 3)}`);
  const RULED_EDGES = {
    "#94b988": ["forest", "wood"],
    "#c2cdaa": ["scrub", "grassland", "grass", "meadow", "heath", "cemetery", "golf_course"],
    "#b4caa8": ["park", "recreation_ground", "garden", "dog_park", "pitch"],
    "#88b99d": ["allotments", "village_green", "playground"],
    "#adc8bc": ["wetland"],
    "#b0aba4": ["bare_rock"],
    "#c3bfba": ["residential"],
    "#d0bdb9": ["commercial"],
    "#c4bfc2": ["railway"],
    "#ccceb4": ["farmland"],
    "#c9c3bd": ["school", "university", "college"],
    "#c9c0bf": ["hospital"],
    "#c6b3c3": ["industrial"],
  };
  const WIDTH = '["interpolate",["linear"],["zoom"],13,0.5,15,0.75,17,1]';
  const edges = layerOf("landuse_edges");
  const water = layerOf("water_edges");
  if (!edges || !water) problems.push("v4 has no landuse_edges or water_edges");
  else {
    // Since the orchards (11), the kind match is the fallback of a case on the three kinds' kind_detail.
    const color = Array.isArray(edges.paint["line-color"]) && edges.paint["line-color"][0] === "case" ? edges.paint["line-color"].at(-1) : edges.paint["line-color"];
    const edgeOf = (kind) => {
      for (let i = 2; i < color.length - 1; i += 2) if ([color[i]].flat().includes(kind)) return color[i + 1];
      return null;
    };
    const kinds = Object.values(RULED_EDGES).flat();
    if (JSON.stringify(edges.filter) !== JSON.stringify(["in", ["get", "kind"], ["literal", kinds]])) problems.push(`landuse_edges' filter is ${JSON.stringify(edges.filter)}`);
    for (const [colour, ks] of Object.entries(RULED_EDGES)) for (const k of ks) if (edgeOf(k) !== colour) problems.push(`landuse_edges: ${k} edge ${edgeOf(k)}, ruled ${colour}`);
    for (const l of [edges, water]) {
      if (l.minzoom !== 13 || JSON.stringify(l.paint["line-width"]) !== WIDTH) problems.push(`${l.id}: minzoom ${l.minzoom}, width ${JSON.stringify(l.paint["line-width"])}; ruled 13 and ${WIDTH}`);
      if (l.id in NIGHT_LAYER_COLOURS || (l.id === "water_edges" && l.id in NIGHT_LINE_COLOURS_V4)) problems.push(`${l.id} has a night exception; ruled: none (landuse_edges' darker rims are in the line table, 7)`);
    }
    const opacity = '["step",["zoom"],["match",["get","kind"],["residential","commercial","railway"],0,1],14,1]';
    if (JSON.stringify(edges.paint["line-opacity"]) !== opacity) problems.push(`landuse_edges' opacity ${JSON.stringify(edges.paint["line-opacity"])}: residential, commercial and railway land start at z14`);
    if (water["source-layer"] !== "water" || water.paint["line-color"] !== "#71c3ce" || JSON.stringify(water.filter) !== '["==",["geometry-type"],"Polygon"]') problems.push(`water_edges: ${water["source-layer"]} ${water.paint["line-color"]} ${JSON.stringify(water.filter)}; ruled: water polygons, #71c3ce`);
    if (edges["source-layer"] !== "landuse") problems.push(`landuse_edges reads ${edges["source-layer"]}`);
  }
  const industrial = layerOf("landuse_industrial")?.paint?.["fill-color"];
  if (industrial !== "#ebdbe8") problems.push(`landuse_industrial is ${industrial}, ruled #ebdbe8 (Carto style/landcover.mss:22)`);
  const fill = JSON.stringify(layerOf("buildings")?.paint?.["fill-color"]);
  if (fill !== '["step",["zoom"],"#d9d0c9",14,"#ccc0b7",15,"#d9d0c9"]') problems.push(`buildings fill ${fill}; ruled tan #d9d0c9, #ccc0b7 at z14`);
  const outline = layerOf("buildings_outline")?.paint?.["line-color"];
  if (outline !== "#b9a99c") problems.push(`buildings_outline ${outline}, ruled #b9a99c`);
  if (!JSON.stringify(layerOf("landuse_park_outline")).includes('"#3e7a3a"')) problems.push("landuse_park_outline changed; ruled: unchanged");
}

// 9. v4's low-zoom roads (the fill-in rulings, "Low-zoom roads, night borders, notices route": "A: OSM's treatment
//    (Recommended)"), by hand from the rulings: casings off below z11, the majors thinner by class, the motorway
//    widths, Carto's low-zoom colours cross-fading 10.5 to 11.5, and the minor roads a faint texture from z10.
if (V4) {
  const layerOf = (id) => style2.layers.find((l) => l.id === id);
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const byClass = (t, p, s2, te, o) => ["match", ["get", "kind_detail"], ["trunk", "trunk_link"], t, ["primary", "primary_link"], p, ["secondary", "secondary_link"], s2, ["tertiary", "tertiary_link"], te, o];
  const MAJOR = ["interpolate", ["exponential", 1.6], ["zoom"],
    8, byClass(0.9, 0.9, 0.5, 0, 0.5), 9, byClass(1.1, 1.1, 0.7, 0.4, 0.7), 10, byClass(1.3, 1.3, 0.9, 0.6, 0.9),
    11, byClass(1.6, 1.6, 1.2, 0.9, 1.2), 12, byClass(1.9, 1.9, 1.7, 1.5, 1.7), 15, 3, 18, 13];
  const MOTORWAY = ["interpolate", ["exponential", 1.6], ["zoom"], 6, 0.8, 8, 1.0, 9, 1.3, 10, 1.7, 11, 2.0, 12, 2.4, 15, 5, 18, 15];
  const EARLY_CASING = ["interpolate", ["linear"], ["zoom"], 10.9, 0, 11, 0.3, 12, 0.5];
  const MAJOR_COLOUR = ["interpolate", ["linear"], ["zoom"],
    10.5, byClass("#eeab4f", "#efc46c", "#ecd58c", "#bdb7ad", "#efc46c"),
    11.5, byClass("#f2bf72", "#f5d28c", "#f8e3ae", "#ffffff", "#f6d68f")];
  const MOTORWAY_CASING_COLOUR = ["interpolate", ["linear"], ["zoom"], 11.5, "#c24e6b", 12.5, "#dc2a67"];
  const MINOR = ["interpolate", ["exponential", 1.6], ["zoom"], 10, 0.4, 11, 0.6, 12, 0.8, 15, 2, 18, 11];
  const ruled = [
    ...["roads_major", "roads_bridges_major"].flatMap((id) => [[id, "line-width", MAJOR], [id, "line-color", MAJOR_COLOUR]]),
    ...["roads_major_casing_early", "roads_major_casing_late", "roads_bridges_major_casing"].map((id) => [id, "line-gap-width", MAJOR]),
    ["roads_major_casing_early", "line-width", EARLY_CASING],
    ["roads_major_casing_late", "line-width", ["interpolate", ["linear"], ["zoom"], 12, 0.5, 13, 0.75, 15, 1]],
    ["roads_bridges_major_casing", "line-width", ["interpolate", ["linear"], ["zoom"], 12, 0.6, 13, 1, 15, 1.5]],
    ...["roads_highway", "roads_bridges_highway"].flatMap((id) => [[id, "line-width", MOTORWAY], [id, "line-color", ["interpolate", ["linear"], ["zoom"], 10.5, "#e66e89", 11.5, "#e892a2"]]]),
    ...["roads_highway_casing_early", "roads_highway_casing_late", "roads_bridges_highway_casing"].flatMap((id) => [[id, "line-gap-width", MOTORWAY], [id, "line-color", MOTORWAY_CASING_COLOUR]]),
    ["roads_highway_casing_early", "line-width", EARLY_CASING],
    ["roads_highway_casing_late", "line-width", ["interpolate", ["linear"], ["zoom"], 12, 0.5, 13, 0.75, 15, 1, 20, 15]],
    ["roads_minor", "line-width", MINOR],
    ["roads_minor", "line-color", ["interpolate", ["linear"], ["zoom"], 11.5, "#c4beb4", 12, "#ffffff"]],
    ["roads_minor", "line-opacity", ["interpolate", ["linear"], ["zoom"], 10, 0.7, 11, 1]],
    ["roads_minor_casing", "line-gap-width", MINOR],
    ["roads_minor_casing", "line-width", ["interpolate", ["linear"], ["zoom"], 11.5, 0, 12, 0.5]],
  ];
  for (const [id, prop, want] of ruled) {
    const have = layerOf(id)?.paint?.[prop];
    if (!same(have, want)) problems.push(`${id} ${prop} is ${JSON.stringify(have)}; ruled ${JSON.stringify(want)}`);
  }
  // The late casings stay from z12 and the early ones below it, so no casing draws below z11.
  for (const [id, key, z] of [["roads_major_casing_late", "minzoom", 12], ["roads_highway_casing_late", "minzoom", 12], ["roads_major_casing_early", "maxzoom", 12], ["roads_highway_casing_early", "maxzoom", 12]]) {
    if (layerOf(id)?.[key] !== z) problems.push(`${id} ${key} is ${layerOf(id)?.[key]}, expected ${z}`);
  }
  // The night rims, printed against the night grounds they sit on: recorded, not gated.
  for (const [day, rim] of Object.entries(NIGHT_LINE_COLOURS_V4.landuse_edges ?? {})) {
    console.log(`night rim for day edge ${day}: ${rim}, on night ground ${contrast(rim, night(GROUND)).toFixed(2)}:1 (recorded, not gated)`);
  }
}

// 10. v4's creeks under the water (the fill-in rulings, "Water fill to the creek colour", superseded: the owner, "Creeks
//     under water (Recommended)"): water_stream and water_river draw just before the water fill, so a lake or river
//     polygon covers the line inside it and the creek shows everywhere else. Nothing else moves.
if (V4) {
  const order = style2.layers.map((l) => l.id);
  const at = order.indexOf("water");
  const before = order.slice(at - 2, at);
  if (JSON.stringify(before) !== JSON.stringify(["water_stream", "water_river"])) problems.push(`the two layers before the water fill are ${JSON.stringify(before)}; ruled ["water_stream","water_river"]`);
  const v3order = JSON.parse(fs.readFileSync(here("forager-street-v3.json"), "utf8")).layers.map((l) => l.id).filter((id) => id !== "water_stream" && id !== "water_river");
  const v4rest = order.filter((id) => id !== "water_stream" && id !== "water_river" && v3order.includes(id));
  if (JSON.stringify(v4rest) !== JSON.stringify(v3order)) problems.push("apart from the two creek layers, v4 moves a layer v3 has");
}

// 11. v4's orchards, plant nurseries and vineyards (the fill-in rulings, "Orchards, plant nurseries and vineyards filled":
//     "Fill + pattern per kind (Recommended)", fill #c8dd98, "Standard night (Recommended)"), by hand from Sage's sage-10
//     spec: one fill right after landuse_settled for kind farmland with kind_detail orchard, plant_nursery or vineyard
//     (basemap patch 0006), opacity 0.6 at z6 to 1 at z9, no minzoom; night the plain inversion #495e19 (no fill
//     exception); the three in landuse_edges at #b0c286, night rim #3a4b14. The patterns are a later step.
if (V4) {
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const order = style2.layers.map((l) => l.id);
  const fill = style2.layers.find((l) => l.id === "landuse_orchards");
  const KINDS = ["orchard", "plant_nursery", "vineyard"];
  const FILTER = ["all", ["==", ["get", "kind"], "farmland"], ["in", ["get", "kind_detail"], ["literal", KINDS]]];
  if (!fill) problems.push("v4 has no landuse_orchards");
  else {
    if (order[order.indexOf("landuse_settled") + 1] !== "landuse_orchards") problems.push(`landuse_orchards is not right after landuse_settled: ${order.slice(order.indexOf("landuse_settled"), order.indexOf("landuse_settled") + 2)}`);
    if (fill.type !== "fill" || fill["source-layer"] !== "landuse" || "minzoom" in fill || "maxzoom" in fill) problems.push(`landuse_orchards: ${fill.type} on ${fill["source-layer"]}, minzoom ${fill.minzoom}; ruled a fill on landuse, no minzoom`);
    if (!same(fill.filter, FILTER)) problems.push(`landuse_orchards' filter is ${JSON.stringify(fill.filter)}; ruled ${JSON.stringify(FILTER)}`);
    if (fill.paint["fill-color"] !== "#c8dd98") problems.push(`landuse_orchards is ${JSON.stringify(fill.paint["fill-color"])}, ruled #c8dd98`);
    if (!same(fill.paint["fill-opacity"], ["interpolate", ["linear"], ["zoom"], 6, 0.6, 9, 1])) problems.push(`landuse_orchards' opacity is ${JSON.stringify(fill.paint["fill-opacity"])}; ruled 0.6 at z6 to 1 at z9`);
  }
  if ("#c8dd98" in NIGHT_FILL_COLOURS) problems.push(`#c8dd98 has a night fill exception ${NIGHT_FILL_COLOURS["#c8dd98"]}; ruled: the plain inversion`);
  if (night("#c8dd98") !== "#495e19") problems.push(`the inversion of #c8dd98 is ${night("#c8dd98")}, the ruling's #495e19`);
  const edgeColour = style2.layers.find((l) => l.id === "landuse_edges")?.paint?.["line-color"];
  const EDGE_CASE = ["case", FILTER, "#b0c286"];
  if (!Array.isArray(edgeColour) || !same(edgeColour.slice(0, 3), EDGE_CASE) || edgeColour.length !== 4) problems.push(`landuse_edges' line-color does not start ${JSON.stringify(EDGE_CASE)}: ${JSON.stringify(edgeColour)?.slice(0, 160)}`);
  if (NIGHT_LINE_COLOURS_V4.landuse_edges?.["#b0c286"] !== "#3a4b14") problems.push(`the orchards' night rim is ${NIGHT_LINE_COLOURS_V4.landuse_edges?.["#b0c286"]}, ruled #3a4b14`);
  console.log(`orchards: fill #c8dd98, night ${night("#c8dd98")} on night ground ${contrast(night("#c8dd98"), night(GROUND)).toFixed(2)}:1; edge #b0c286, night rim #3a4b14 on it ${contrast("#3a4b14", night("#c8dd98")).toFixed(2)}:1 (recorded, not gated)`);
}

for (const [what, colour, against, min] of exceptions) {
  const ratio = contrast(colour, against);
  const line = `${what}: ${colour} on ${against} ${ratio.toFixed(2)}:1 (minimum ${min})`;
  console.log(line);
  if (ratio < min) problems.push(line);
}

if (problems.length) {
  console.log("PROBLEMS:\n  " + problems.join("\n  "));
  process.exit(1);
}
console.log(`street style ${STYLE} valid${V4 ? "" : ", bundled copy identical"}, v1 unchanged, day and night contrast and the night limits held`);
