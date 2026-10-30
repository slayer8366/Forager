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
import { NIGHT_FILL_COLOURS_V4, NIGHT_LAYER_COLOURS_V4 } from "../overrides/forager-fill-in.mjs";

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
//    roads_link and roads_bridges_link draw only motorway ramps and keep v3's amber night exception.
if (V4) {
  for (const id of ["roads_major", "roads_bridges_major"]) {
    const match = style2.layers.find((l) => l.id === id)?.paint?.["line-color"];
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
  for (const id of ["roads_link", "roads_bridges_link"]) {
    if (NIGHT_LAYER_COLOURS[id] !== NIGHT_LAYER_COLOURS_V2[id]) problems.push(`${id}'s night ${NIGHT_LAYER_COLOURS[id]} is not v3's ${NIGHT_LAYER_COLOURS_V2[id]}`);
  }
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
