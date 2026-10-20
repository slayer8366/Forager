// Checks the Street style's footpath layers against the trail spec (dispatch 2026-10-19-41, RECORD -1480): the spec is
// prompts/preserved/2026-10-20-trail-design-handover-rowan.md on pi-build, from the owner's rulings on Sage Wayfinder's
// design. Run: npm run check:street (or node tools/check-street-trails.mjs). Exits non-zero, saying what failed.
//
// The expected values below are typed from the spec, not imported from overrides/forager-street.mjs, so a change to
// the design file that strays from the spec is caught here rather than agreed with. Two parts:
//   1. Every synthetic path feature (each sac_scale group value, untagged and an unlisted value; each visibility;
//      informal yes, no and absent; each footpath kind and steps) at zooms 13 to 18 is drawn by exactly one footpath
//      layer, with the spec's colour, dash, cap and width. Filters are evaluated by MapLibre's own featureFilter.
//      None drawn, or two drawn (a dot covered by a dash), fails.
//   2. The layer list: 90 layers, the 17 footpath ids, and their order, informal below steps below unknown below
//      easy below rough below hard ("informal below hard, so a warning is never covered").
import fs from "node:fs";
import { featureFilter } from "@maplibre/maplibre-gl-style-spec";

const here = (path) => new URL(`../${path}`, import.meta.url);
const style = JSON.parse(fs.readFileSync(here("forager-street-v1.json"), "utf8"));
const problems = [];

// --- The spec, typed from the handover ----------------------------------------------------------------------------
const FOOTPATH = "#a8452a"; // Street v1's footpath colour, unchanged
const HARD = "#7a1e10";
const INFORMAL = "#a0735a";
const BASE_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 1.2, 15, 1.8, 18, 3.5]; // Street v1's
const HARD_WIDTH = ["interpolate", ["linear"], ["zoom"], 13, 1.6, 15, 2.2, 18, 3.9]; // "+0.4 width"
const SAC = {
  strolling: "easy", hiking: "easy",
  mountain_hiking: "rough", demanding_mountain_hiking: "rough",
  alpine_hiking: "hard", demanding_alpine_hiking: "hard", difficult_alpine_hiking: "hard",
};
const FAINT = ["bad", "horrible", "no"];

/** What the spec says one path draws as at zoom z. */
function expected({ kind_detail, sac_scale, trail_visibility, informal }, z) {
  if (kind_detail === "steps") return { colour: FOOTPATH, dash: [3, 1.5], cap: "butt", width: BASE_WIDTH }; // unchanged
  const group = SAC[sac_scale] ?? "unknown";
  const g = FAINT.includes(trail_visibility) ? 4 : 1.5;
  if (group === "hard") {
    if (z < 14) return { colour: HARD, dash: [4, 1.5], cap: "butt", width: BASE_WIDTH };
    if (z < 15) return { colour: HARD, dash: [4, g], cap: "butt", width: HARD_WIDTH };
    return { colour: HARD, dash: [2, g], cap: "butt", width: HARD_WIDTH };
  }
  if (informal === "yes") {
    if (z < 14) return { colour: INFORMAL, dash: [4, 1.5], cap: "butt", width: BASE_WIDTH };
    return { colour: INFORMAL, dash: [0.1, 2.2], cap: "round", width: BASE_WIDTH };
  }
  if (z < 14) return { colour: FOOTPATH, dash: [4, 1.5], cap: "butt", width: BASE_WIDTH };
  if (z < 15) return { colour: FOOTPATH, dash: [4, g], cap: "butt", width: BASE_WIDTH };
  return { colour: FOOTPATH, dash: [{ unknown: 4, easy: 8, rough: 2 }[group], g], cap: "butt", width: BASE_WIDTH };
}

// --- The style's footpath layers ----------------------------------------------------------------------------------
const pathLayers = style.layers
  .filter((l) => l.id.startsWith("roads_path_") && l.id !== "roads_path_track")
  .map((l) => ({ layer: l, filter: featureFilter(l.filter, `layers.${l.id}.filter`) }));
const drawnBy = (props, z) =>
  pathLayers.filter(({ layer, filter }) =>
    (layer.minzoom ?? 0) <= z && z < (layer.maxzoom ?? 24) && filter.filter({ zoom: z }, { type: 2, properties: props }));

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const ZOOMS = [13, 13.5, 14, 14.5, 15, 16, 18];
let features = 0;
for (const kind_detail of ["footway", "path", "bridleway", "cycleway", "steps"]) {
  for (const sac_scale of [undefined, ...Object.keys(SAC), "T3"]) {
    for (const trail_visibility of [undefined, "excellent", "good", "intermediate", ...FAINT]) {
      for (const informal of [undefined, "yes", "no"]) {
        const props = { kind: "path", kind_detail };
        if (sac_scale) props.sac_scale = sac_scale;
        if (trail_visibility) props.trail_visibility = trail_visibility;
        if (informal) props.informal = informal;
        for (const z of ZOOMS) {
          features++;
          const want = expected(props, z);
          const hits = drawnBy(props, z);
          const what = `${JSON.stringify(props)} at z${z}`;
          if (hits.length !== 1) {
            problems.push(`${what}: drawn by ${hits.length} footpath layers (${hits.map((h) => h.layer.id).join(", ") || "none"}), expected 1`);
            continue;
          }
          const { layer } = hits[0];
          const got = {
            colour: layer.paint["line-color"], dash: layer.paint["line-dasharray"],
            cap: layer.layout?.["line-cap"], width: layer.paint["line-width"],
          };
          if (!same(got, want)) problems.push(`${what}: ${layer.id} draws ${JSON.stringify(got)}, the spec says ${JSON.stringify(want)}`);
        }
      }
    }
  }
}
// Tunnels, bridges and tracks stay out of the footpath layers, as in Street v1.
for (const props of [
  { kind: "path", kind_detail: "path", is_tunnel: true }, { kind: "path", kind_detail: "path", is_bridge: true },
  { kind: "path", kind_detail: "track" }, { kind: "path", kind_detail: "path", sac_scale: "alpine_hiking", is_bridge: true },
]) {
  for (const z of ZOOMS) {
    features++;
    const hits = drawnBy(props, z);
    if (hits.length) problems.push(`${JSON.stringify(props)} at z${z}: drawn by ${hits.map((h) => h.layer.id).join(", ")}, expected none`);
  }
}
// Below z13 there are no paths (PATH_MIN_ZOOM, -1300 (4)).
for (const layer of pathLayers.map((p) => p.layer)) {
  if ((layer.minzoom ?? 0) < 13) problems.push(`${layer.id} starts at z${layer.minzoom ?? 0}, below 13`);
}

// --- The layer list -----------------------------------------------------------------------------------------------
const IDS = [
  "roads_path_informal_z13", "roads_path_informal", "roads_path_steps",
  "roads_path_unknown_z13", "roads_path_hard_z13",
  "roads_path_unknown_z14_clear", "roads_path_unknown_z14_faint", "roads_path_hard_z14_clear", "roads_path_hard_z14_faint",
  "roads_path_unknown_clear", "roads_path_unknown_faint", "roads_path_easy_clear", "roads_path_easy_faint",
  "roads_path_rough_clear", "roads_path_rough_faint", "roads_path_hard_clear", "roads_path_hard_faint",
];
if (style.layers.length !== 90) problems.push(`the style has ${style.layers.length} layers, expected 90 (Street v1's 74, one footpath layer become 17)`);
const ids = pathLayers.map((p) => p.layer.id);
if (!same(ids, IDS)) problems.push(`footpath layers, in order, are ${JSON.stringify(ids)}; expected ${JSON.stringify(IDS)}`);
// The order matters only between layers that draw at the same zoom; a z13-only layer never meets a z15 one.
const rank = (id) => ["informal", "steps", "unknown", "easy", "rough", "hard"].findIndex((g) => id.includes(`_${g}`));
const span = (l) => [l.minzoom ?? 0, l.maxzoom ?? 24];
const layersInOrder = pathLayers.map((p) => p.layer);
layersInOrder.forEach((upper, i) => layersInOrder.slice(0, i).forEach((lower) => {
  const [a, b] = [span(lower), span(upper)];
  if (a[0] < b[1] && b[0] < a[1] && rank(upper.id) < rank(lower.id)) {
    problems.push(`${upper.id} is drawn above ${lower.id} at the same zooms, against the order informal, steps, unknown, easy, rough, hard`);
  }
}));
const track = style.layers.findIndex((l) => l.id === "roads_path_track");
if (style.layers[track + 1]?.id !== IDS[0]) problems.push(`the footpath layers do not start right after roads_path_track`);

// --- Night: informal has its own colour, hard keeps the inversion ---------------------------------------------------
const nightLayers = style.metadata?.["forager:night-layers"] ?? {};
for (const id of ["roads_path_informal_z13", "roads_path_informal"]) {
  if (nightLayers[id] !== "#bb8a6f") problems.push(`${id}'s night colour is ${nightLayers[id]}, the spec says #bb8a6f`);
}
for (const id of Object.keys(nightLayers)) {
  if (id.startsWith("roads_path_") && !id.startsWith("roads_path_informal")) problems.push(`${id} has a night exception; the spec gives it the inversion`);
}

console.log(`${features} feature-zoom cases against ${pathLayers.length} footpath layers`);
if (problems.length) {
  console.log(`PROBLEMS (${problems.length}):\n  ` + problems.slice(0, 40).join("\n  ") + (problems.length > 40 ? `\n  ... and ${problems.length - 40} more` : ""));
  process.exit(1);
}
console.log("footpath layers match the trail spec");
