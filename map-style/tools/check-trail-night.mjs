// Checks forager-street-v2.json (or, with the argument v4, forager-street-v4.json: the fill-in) (dispatch 2026-10-18-05 Part 2; v2 since dispatch 2026-10-22-03, RECORD -1561): it
// is valid style-spec JSON, it is the same file the Worker bundles, v1 is still byte for byte what phones with older
// apps and older downloads load, and the day colours the owner's night ruling depends on (-1288: "the day palette is
// chosen so roads and paths still stand out after inversion") hold their contrast after the app's V1 inversion, with
// the night exceptions held to their ruled limits. Run: npm run check:trail. Exits non-zero, saying what failed.
//
// V1 is the app's night transform, per channel: night = clamp(c + 1 - 2 * mean(r, g, b))
// (app/src/main/java/com/zynergylabs/forager/app/ui/map/NightColour.kt, nightColorOf). Contrast is the
// WCAG ratio of relative luminances. The thresholds are this check's own operating limits, not a
// standard for maps: a path or casing line at 3:1 against the ground reads at arm's length; text at 4.5:1.
import crypto from "node:crypto";
import fs from "node:fs";
import { validateStyleMin } from "@maplibre/maplibre-gl-style-spec";
import { COLOURS, NIGHT_ICON_RINGS, NIGHT_LAYER_BANDS, NIGHT_LAYER_COLOURS as NIGHT_LAYER_COLOURS_V2 } from "../overrides/forager-trail.mjs";
import { CHARACTER_COLOURS, NIGHT_FILL_COLOURS as NIGHT_FILL_COLOURS_V2, NIGHT_LIMITS, NIGHT_PATTERN_COLOURS } from "../overrides/forager-character.mjs";
import { FILL_IN_COLOURS, NIGHT_FILL_COLOURS_V4, NIGHT_LAYER_COLOURS_V4, NIGHT_LINE_COLOURS_V4 } from "../overrides/forager-fill-in.mjs";

const here = (path) => new URL(`../${path}`, import.meta.url);
const problems = [];

// `node tools/check-trail-night.mjs v4` checks forager-trail-v4.json (the fill-in) against v4's own night tables, and,
// since the switch to Street v4, its bundled copy in the Worker. With no argument it checks v2, as before.
const V4 = process.argv[2] === "v4";
const STYLE = V4 ? "v4" : "v2";
const NIGHT_LAYER_COLOURS = V4 ? NIGHT_LAYER_COLOURS_V4 : NIGHT_LAYER_COLOURS_V2;
const NIGHT_FILL_COLOURS = V4 ? NIGHT_FILL_COLOURS_V4 : NIGHT_FILL_COLOURS_V2;
// v4 is checked as trail-v4, the file the app loads (dispatch 2026-11-03-05 D1); street-v4 is frozen, held below.
const FILE = V4 ? "trail-v4" : "street-v2";
const text = fs.readFileSync(here(`forager-${FILE}.json`), "utf8");
// v4 is on the Worker too since the switch to Street v4 (dispatch 2026-11-01-coder-11 step (c)): its bundled copy is
// compared the same way.
{
  const workerCopy = V4 ? "trail-style-v4.json" : "street-style-v2.json";
  const bundled = fs.readFileSync(here(`../server/pmtiles-worker/src/${workerCopy}`), "utf8");
  if (text !== bundled) problems.push(`server/pmtiles-worker/src/${workerCopy} differs from forager-${FILE}.json: run npm run generate:trail`);
}
// The style's own metadata must carry the tables checked here: the app reads its copy against it.
const meta = JSON.parse(text).metadata;
if (JSON.stringify(meta["forager:night-layers"]) !== JSON.stringify(NIGHT_LAYER_COLOURS)) problems.push(`forager-${FILE}.json's forager:night-layers differs from its design table: run npm run generate:trail`);
if (JSON.stringify(meta["forager:night-fills"]) !== JSON.stringify(NIGHT_FILL_COLOURS)) problems.push(`forager-${FILE}.json's forager:night-fills differs from its design table: run npm run generate:trail`);
const errors = validateStyleMin(JSON.parse(text));
for (const e of errors) problems.push(`style spec: ${e.message}`);

// v1 is frozen (RECORD -1561): what it was at pre-main 563e9b756, byte for byte, in all three copies. street-v3 and
// street-v4 are frozen the same way since dispatch 2026-11-03-05 (D2, RECORD -2000): what they were at pre-main
// d88db1799, every copy, for the regions downloaded against them; a later design ships as trail-v5. v2 is held by the
// Worker's tests. street-v4 was updated in place once since, with the first zooms (dispatch 2026-11-02-coder-13; the
// owner: "Update v4 in place"), and is held here to the new files' sums; v1 and v3 are as they were. It was updated in
// place a second time for I8 (dispatch 2026-11-06-01 Amendment 1, RECORD -2183; the owner: "Update v4 in place"): the
// Forest Service trail number moved up among the labels, and nothing else (check 10 holds that), so the three street-v4
// sums below are the I8 files'. This is allowed only because Trail Map has not been released yet (the owner: "Nobody
// is using Trail maps so updating in place is permitted. Only when we release it do we need to do it properly"); after
// release a style change ships as a new version.
const V1_SHA256 = {
  "forager-street-v1.json": "b2ec485ddbe65635a1b2a0f1ef5b51bbb86f6432eec1fd6896bcf4c8979bd6da",
  "forager-street-v1.preview.json": "4fc2e14feeecfdf86bc7a4f6d7597eb997732ee97620388fba69ea379926dc88",
  "../server/pmtiles-worker/src/street-style-v1.json": "b2ec485ddbe65635a1b2a0f1ef5b51bbb86f6432eec1fd6896bcf4c8979bd6da",
  "forager-street-v3.json": "e2a259820bd1dda6bccfd7e05d5132adea6254002540cfbfdcfc04747de16b7f",
  "forager-street-v3.preview.json": "beb40e3a0e5624da44f52e98f0e85c52e3065cc87af5987396fbf983a8d71c97",
  "../server/pmtiles-worker/src/street-style-v3.json": "e2a259820bd1dda6bccfd7e05d5132adea6254002540cfbfdcfc04747de16b7f",
  "forager-street-v4.json": "8032de2c14725ef5d819eb229d1358146d5493e608ac2227e2bb79b9365e9f5c",
  "forager-street-v4.preview.json": "35572d32f1d0eb5ef629b28bf7e02cd6b94267e7a54c0e74e29d6d49987ed3c0",
  "../server/pmtiles-worker/src/street-style-v4.json": "8032de2c14725ef5d819eb229d1358146d5493e608ac2227e2bb79b9365e9f5c",
};
for (const [path, sum] of Object.entries(V1_SHA256)) {
  const actual = crypto.createHash("sha256").update(fs.readFileSync(here(path))).digest("hex");
  if (actual !== sum) problems.push(`${path} changed (sha256 ${actual}): frozen (v1 since -1561, v3 and v4 since dispatch 2026-11-03-05 D2; v4 updated in place twice, dispatch 2026-11-02-coder-13 and I8 of dispatch 2026-11-06-01, before Trail Map's release); a new design ships as a new version`);
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
// v4's first zooms (dispatch 2026-11-02-coder-13) split some layers into clones, each in the run right after its original
// (forager:layer-clones, clone id to original id; held in 14). The rulings in 8 to 12 are about the design's layers: their
// order is read without the clones, and a split layer's ruled filter is the part its clones were cut from.
const LAYER_CLONES = V4 ? style2.metadata["forager:layer-clones"] ?? {} : {};
const SPLIT = new Set(Object.values(LAYER_CLONES));
const designOrder = style2.layers.map((l) => l.id).filter((id) => !(id in LAYER_CLONES));
/** A layer's ruled filter: its own, or for a split layer (["all", ruled, ["!", ["any", ...the clones' kinds]]]) the ruled part. */
const ruledFilterOf = (layer) => (layer && SPLIT.has(layer.id) ? layer.filter[1] : layer?.filter);
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
/** Every image name a fill-pattern value can draw: a name, or each output of a match on kind_detail (v4's orchards, 12). */
const patternNames = (value) => (typeof value === "string" ? [value] : Array.isArray(value) && value[0] === "match" ? value.slice(3).filter((_, i) => i % 2 === 0 || i === value.length - 4) : []);
const patterns = new Set(style2.layers.flatMap((l) => patternNames(l.paint?.["fill-pattern"])).filter(Boolean));
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
  if (JSON.stringify(lines) !== JSON.stringify(NIGHT_LINE_COLOURS_V4)) problems.push(`forager-trail-v4.json's forager:night-line-colours differs from its design table: run npm run generate:trail`);
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
  const order = designOrder;
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
    if (JSON.stringify(ruledFilterOf(edges)) !== JSON.stringify(["in", ["get", "kind"], ["literal", kinds]])) problems.push(`landuse_edges' filter is ${JSON.stringify(edges.filter)}`);
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
//     polygon covers the line inside it and the creek shows everywhere else. Nothing else moves, but for I8's one layer.
//     I8 (dispatch 2026-11-06-01 Amendment 1, RECORD -2183; the owner: "Update v4 in place"): the Forest Service trail
//     number, reference_fs_number, moves up from just above the trail lines to the bottom of v4's top run of labels,
//     directly above the last bridge and directly under address_label, so the app's lines, which go just under that run,
//     draw under it (I5: "Under all names, trail no. too"). It is the one exception added to this check, and only for v4.
//     Changing v4 in place is allowed only because Trail Map has not been released yet (the owner: "Nobody is using
//     Trail maps so updating in place is permitted. Only when we release it do we need to do it properly"); after
//     release a style change ships as a new version.
if (V4) {
  const order = designOrder;
  const at = order.indexOf("water");
  const before = order.slice(at - 2, at);
  if (JSON.stringify(before) !== JSON.stringify(["water_stream", "water_river"])) problems.push(`the two layers before the water fill are ${JSON.stringify(before)}; ruled ["water_stream","water_river"]`);
  const MOVED = new Set(["water_stream", "water_river", "reference_fs_number"]);
  const v3order = JSON.parse(fs.readFileSync(here("forager-street-v3.json"), "utf8")).layers.map((l) => l.id).filter((id) => !MOVED.has(id));
  const v4rest = order.filter((id) => !MOVED.has(id) && v3order.includes(id));
  if (JSON.stringify(v4rest) !== JSON.stringify(v3order)) problems.push("apart from the two creek layers and I8's trail number, v4 moves a layer v3 has");
  // I8's place, read on the whole layer list: the first of the top run of symbol layers, the run the app's lines go under.
  const all = style2.layers;
  const n = all.findIndex((l) => l.id === "reference_fs_number");
  const topRun = all.findLastIndex((l) => l.type !== "symbol") + 1;
  if (n !== topRun || all[n + 1]?.id !== "address_label") {
    problems.push(`reference_fs_number is layer ${n} of ${all.length}, between ${all[n - 1]?.id} and ${all[n + 1]?.id}; I8 rules it first in the top run of labels (layer ${topRun}), directly under address_label`);
  }
}

// 11. v4's orchards, plant nurseries and vineyards (the fill-in rulings, "Orchards, plant nurseries and vineyards filled":
//     "Fill + pattern per kind (Recommended)", fill #c8dd98, "Standard night (Recommended)"), by hand from Sage's sage-10
//     spec: one fill right after landuse_settled for kind farmland with kind_detail orchard, plant_nursery or vineyard
//     (basemap patch 0006), opacity 0.6 at z6 to 1 at z9, no minzoom; night the plain inversion #495e19 (no fill
//     exception); the three in landuse_edges at #b0c286, night rim #3a4b14. The patterns are a later step. Since the
//     first zooms (14): from 3.5, the plant nurseries split off from 8.5 (the comparison's rows 6 and 7).
if (V4) {
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const order = designOrder;
  const fill = style2.layers.find((l) => l.id === "landuse_orchards");
  const KINDS = ["orchard", "plant_nursery", "vineyard"];
  const FILTER = ["all", ["==", ["get", "kind"], "farmland"], ["in", ["get", "kind_detail"], ["literal", KINDS]]];
  if (!fill) problems.push("v4 has no landuse_orchards");
  else {
    if (order[order.indexOf("landuse_settled") + 1] !== "landuse_orchards") problems.push(`landuse_orchards is not right after landuse_settled: ${order.slice(order.indexOf("landuse_settled"), order.indexOf("landuse_settled") + 2)}`);
    if (fill.type !== "fill" || fill["source-layer"] !== "landuse" || fill.minzoom !== 3.5 || "maxzoom" in fill) problems.push(`landuse_orchards: ${fill.type} on ${fill["source-layer"]}, minzoom ${fill.minzoom}; ruled a fill on landuse from 3.5 (the first zooms)`);
    if (!same(ruledFilterOf(fill), FILTER)) problems.push(`landuse_orchards' filter is ${JSON.stringify(fill.filter)}; ruled ${JSON.stringify(FILTER)}`);
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

// 12. v4's patterns on the orchards (the fill-in rulings: "Fill + pattern per kind (Recommended)", then "New icon set v6
//     (Recommended)"), by hand from Sage's sage-10 spec: v4 names sprite v6 (v5 unchanged plus the three patterns) and
//     v3 keeps v5; landuse_orchards_pattern right after landuse_orchards, minzoom 13, the same filter, fill-pattern by
//     kind_detail; the three are images v6 has (3, above) and in forager:sprite-icons, which the app's night inverts
//     (ink #8ea65c to #829a50, the plain inversion, as ruled), and none is a painted night pattern. Since the first zooms
//     (14): from 11.5 (the comparison's row 8), right after the orchards and their nursery clone.
if (V4) {
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const order = designOrder;
  if (!/\/sprites\/v6\/light$/.test(style2.sprite)) problems.push(`v4's sprite is ${style2.sprite}; ruled sprite v6`);
  const v3 = JSON.parse(fs.readFileSync(here("forager-street-v3.json"), "utf8"));
  if (!/\/sprites\/v5\/light$/.test(v3.sprite)) problems.push(`v3's sprite is ${v3.sprite}; it keeps sprite v5`);
  const pattern = style2.layers.find((l) => l.id === "landuse_orchards_pattern");
  const fill = style2.layers.find((l) => l.id === "landuse_orchards");
  const PATTERN = ["match", ["get", "kind_detail"], "orchard", "orchard_dots", "vineyard", "vineyard_rows", "plant_nursery", "nursery_dots", ""];
  if (!pattern) problems.push("v4 has no landuse_orchards_pattern");
  else {
    if (order[order.indexOf("landuse_orchards") + 1] !== "landuse_orchards_pattern") problems.push("landuse_orchards_pattern is not right after landuse_orchards");
    if (pattern.type !== "fill" || pattern["source-layer"] !== "landuse" || pattern.minzoom !== 11.5) problems.push(`landuse_orchards_pattern: ${pattern.type} on ${pattern["source-layer"]}, minzoom ${pattern.minzoom}; ruled a fill on landuse from 11.5 (the first zooms)`);
    if (!same(pattern.filter, ruledFilterOf(fill))) problems.push(`landuse_orchards_pattern's filter ${JSON.stringify(pattern.filter)} is not landuse_orchards'`);
    if (!same(pattern.paint, { "fill-pattern": PATTERN })) problems.push(`landuse_orchards_pattern's paint is ${JSON.stringify(pattern.paint)}; ruled {"fill-pattern": ${JSON.stringify(PATTERN)}}`);
  }
  const icons = JSON.parse(text).metadata["forager:sprite-icons"];
  for (const name of ["orchard_dots", "vineyard_rows", "nursery_dots"]) {
    if (!(name in sprite)) problems.push(`sprite v6 has no ${name}`);
    if (!icons.includes(name)) problems.push(`${name} is not in v4's forager:sprite-icons, so the app's night would not invert it`);
    if (name in NIGHT_PATTERN_COLOURS) problems.push(`${name} is a painted night pattern; ruled the plain inversion`);
  }
  console.log(`orchard patterns: v4 sprite ${style2.sprite}, ${Object.keys(sprite).length} images; ink #8ea65c, night ${night("#8ea65c")} (ruled #829a50)`);
  if (night("#8ea65c") !== "#829a50") problems.push(`the inversion of the ink #8ea65c is ${night("#8ea65c")}, ruled #829a50`);
}

// 13. v4's hill shading (dispatch 2026-11-01-coder-11; the owner: "It's good... Let's keep this", then "Go for all four";
//     Sage's sage-11 ALT, as on the trial), by hand from the spec: the dem source, Terrarium PNG from the Worker's relief
//     archive (z5-11, inline tiles so MapLibre never asks past z11); the hillshade layer right before water_stream, from
//     z5; its paint; and its night in forager:night-hillshade, which the app applies (the recolour never touches it).
//     v3 has none of it.
if (V4) {
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const DEM = {
    type: "raster-dem", tiles: ["https://forager-pmtiles.brandonlee1-894.workers.dev/relief/{z}/{x}/{y}.png"], encoding: "terrarium",
    tileSize: 256, minzoom: 5, maxzoom: 11, attribution: "Heights: USGS 3DEP; NRCan MRDEM",
  };
  if (!same(style2.sources.dem, DEM)) problems.push(`v4's dem source is ${JSON.stringify(style2.sources.dem)}; ruled ${JSON.stringify(DEM)}`);
  const order = style2.layers.map((l) => l.id);
  const hill = style2.layers.find((l) => l.id === "hillshade");
  const PAINT = {
    "hillshade-exaggeration": ["interpolate", ["linear"], ["zoom"], 9, 0.28, 11, 0.32, 13, 0.24, 15, 0.16, 17, 0.1],
    "hillshade-illumination-direction": 335,
    "hillshade-illumination-anchor": "map",
    "hillshade-shadow-color": "#4a3f33",
    "hillshade-highlight-color": "#ffffff",
    "hillshade-accent-color": "rgba(74,63,51,0.5)",
  };
  if (!hill) problems.push("v4 has no hillshade layer");
  else {
    if (order[order.indexOf("water_stream") - 1] !== "hillshade") problems.push(`hillshade is not right before water_stream: ${order.slice(order.indexOf("water_stream") - 2, order.indexOf("water_stream") + 1)}`);
    if (hill.type !== "hillshade" || hill.source !== "dem" || hill.minzoom !== 5 || "maxzoom" in hill) problems.push(`hillshade: ${hill.type} on ${hill.source}, minzoom ${hill.minzoom}; ruled a hillshade on dem from z5`);
    if (!same(hill.paint, PAINT)) problems.push(`hillshade's paint is ${JSON.stringify(hill.paint)}; ruled ${JSON.stringify(PAINT)}`);
  }
  const NIGHT = { shadow: "rgba(0,0,0,0.7)", highlight: "rgba(220,228,236,0.2)", accent: "rgba(0,0,0,0.35)" };
  if (!same(JSON.parse(text).metadata["forager:night-hillshade"], NIGHT)) problems.push(`forager:night-hillshade is ${JSON.stringify(JSON.parse(text).metadata["forager:night-hillshade"])}; ruled ${JSON.stringify(NIGHT)}`);
  const v3 = JSON.parse(fs.readFileSync(here("forager-street-v3.json"), "utf8"));
  if (v3.sources.dem || v3.layers.some((l) => l.type === "hillshade") || "forager:night-hillshade" in v3.metadata) problems.push("v3 has hill shading; ruled v4 only");
}

// 14. v4's first zooms (dispatch 2026-11-02-coder-13; the owner approved the zoom and chose "Update v4 in place"): the
//     layers overrides/forager-reference-zooms.mjs splits. Each clone draws as its original (type, source, source-layer,
//     paint and layout; the peak's icon alone leaves its name to its clone from 11.5), sits in the run right after its
//     original, and takes a part of the original's ruled filter: ["all", ruled, its kinds], the original keeping
//     ["all", ruled, ["!", ["any", ...the clones' kinds]]]. Night: v4's tables, and the app's copy of them, are keyed by
//     layer id and name no clone, so a clone of a layer with a named night exception draws the plain inversion until the
//     app maps the clones (recorded, not gated).
if (V4) {
  const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
  const layerOf = (id) => style2.layers.find((l) => l.id === id);
  const order = style2.layers.map((l) => l.id);
  if (!Object.keys(LAYER_CLONES).length) problems.push("v4 has no forager:layer-clones: the first zooms are missing");
  for (const [id, of] of Object.entries(LAYER_CLONES)) {
    const clone = layerOf(id);
    const orig = layerOf(of);
    if (!clone || !orig) { problems.push(`forager:layer-clones names ${id} of ${of}, and the style lacks one`); continue; }
    if (!id.startsWith(`${of}__`)) problems.push(`${id} is not named ${of}__<group>`);
    for (const k of ["type", "source", "source-layer", "paint"]) if (!same(clone[k], orig[k])) problems.push(`${id}'s ${k} is not ${of}'s`);
    if (id !== "pois__peak" && !same(clone.layout, orig.layout)) problems.push(`${id}'s layout is not ${of}'s`);
    const [i, j] = [order.indexOf(of), order.indexOf(id)];
    if (j < i || order.slice(i + 1, j).some((b) => LAYER_CLONES[b] !== of)) problems.push(`${id} is not in the run right after ${of}`);
  }
  for (const of of SPLIT) {
    const f = layerOf(of)?.filter;
    const cut = Array.isArray(f) && f[0] === "all" && f.length === 3 && Array.isArray(f[2]) && f[2][0] === "!" && Array.isArray(f[2][1]) && f[2][1][0] === "any";
    if (!cut) { problems.push(`${of}'s filter is not ["all", ruled, ["!", ["any", ...]]]: ${JSON.stringify(f)?.slice(0, 160)}`); continue; }
    const kinds = new Set(f[2][1].slice(1).map((c) => JSON.stringify(c)));
    const taken = new Set();
    for (const id of Object.keys(LAYER_CLONES).filter((c) => LAYER_CLONES[c] === of)) {
      const g = layerOf(id).filter;
      if (!Array.isArray(g) || g[0] !== "all" || g.length !== 3 || !same(g[1], f[1]) || !kinds.has(JSON.stringify(g[2]))) problems.push(`${id}'s filter is not ["all", ${of}'s ruled filter, one of the kinds ${of} leaves]`);
      else taken.add(JSON.stringify(g[2]));
    }
    if (taken.size !== kinds.size) problems.push(`${of} leaves ${kinds.size} kinds and its clones take ${taken.size}`);
  }
  const unmapped = Object.entries(LAYER_CLONES).filter(([id, of]) => !(id in NIGHT_LAYER_COLOURS) && !(id in NIGHT_LINE_COLOURS_V4) && (of in NIGHT_LAYER_COLOURS || of in NIGHT_LINE_COLOURS_V4));
  console.log(`first zooms: ${Object.keys(LAYER_CLONES).length} clones of ${SPLIT.size} layers; ${unmapped.length} clones of layers with a named night exception, drawn with the plain inversion until the app maps them (recorded, not gated): ${unmapped.map(([id]) => id).join(", ")}`);
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
console.log(`street style ${STYLE} valid, bundled copy identical, v1 unchanged, day and night contrast and the night limits held`);
