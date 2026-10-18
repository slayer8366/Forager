// Checks forager-street-v1.json (dispatch 2026-10-18-05 Part 2): it is valid style-spec JSON, it is the
// same file the Worker bundles, and the day colours the owner's night ruling depends on (-1288: "the day
// palette is chosen so roads and paths still stand out after inversion") hold their contrast after the
// app's V1 inversion. Run: npm run check:street. Exits non-zero, saying what failed.
//
// V1 is the app's night transform, per channel: night = clamp(c + 1 - 2 * mean(r, g, b))
// (app/src/main/java/com/zynergylabs/forager/app/ui/map/NightColour.kt, nightColorOf). Contrast is the
// WCAG ratio of relative luminances. The thresholds are this check's own operating limits, not a
// standard for maps: a path or casing line at 3:1 against the ground reads at arm's length; text at 4.5:1.
import fs from "node:fs";
import { validateStyleMin } from "@maplibre/maplibre-gl-style-spec";
import { COLOURS } from "../overrides/forager-street.mjs";

const here = (path) => new URL(`../${path}`, import.meta.url);
const problems = [];

const text = fs.readFileSync(here("forager-street-v1.json"), "utf8");
const bundled = fs.readFileSync(here("../server/pmtiles-worker/src/street-style-v1.json"), "utf8");
if (text !== bundled) problems.push("server/pmtiles-worker/src/street-style-v1.json differs from forager-street-v1.json: run npm run generate:street");
const errors = validateStyleMin(JSON.parse(text));
for (const e of errors) problems.push(`style spec: ${e.message}`);

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
];
for (const [what, colour, against, min] of checks) {
  const day = contrast(colour, against);
  const dark = contrast(night(colour), night(against));
  const line = `${what}: day ${colour} ${day.toFixed(2)}:1, night ${night(colour)} on ${night(against)} ${dark.toFixed(2)}:1 (minimum ${min})`;
  console.log(line);
  if (day < min || dark < min) problems.push(line);
}
if (problems.length) {
  console.log("PROBLEMS:\n  " + problems.join("\n  "));
  process.exit(1);
}
console.log("street style valid, bundled copy identical, day and night contrast held");
