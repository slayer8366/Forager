// Builds Forager's own Street style (dispatch 2026-10-18-05 Part 2) from the same pinned Protomaps light
// layers as forager-light.json, with overrides/forager-street.mjs applied, and since dispatch 2026-10-22-03
// (RECORD -1561) overrides/forager-character.mjs after it. Run: npm run generate:street
//
// A separate script, not a branch inside generate.mjs: forager-light.json stays exactly what it was (the
// website and the label check read it), and the Street style is its own output.
//
// The design ships as street-v2.json (RECORD -1561, the owner: "Yes, v2"). MapLibre's offline store keys a
// downloaded region by its style URL, and an app older than v2 inverts only the icons it was built knowing, so
// v1 is never rewritten: forager-street-v1.json, its preview copy and the Worker's street-style-v1.json stay
// byte for byte what they were (tools/check-street-night.mjs holds their sha256).
//
// Since step 8a (dispatch 2026-10-26-14, RECORD -1677, -1678) it writes v3: v2 with the official-trails band
// (overrides/forager-reference.mjs), a second source added and nothing of v2 changed. v2 is frozen the same way v1 was,
// for the regions downloaded against it: its three copies are held to their sha256 by the Worker's tests
// (server/pmtiles-worker/test/worker.test.mjs), which CI runs. This script writes v3 only. The app does not load v3
// until the agency archive is live (Amendment 1); tools/check-street-*.mjs and check:street still read v2.
//
// Three files come out, identical except for where fonts and icons are fetched from:
//   forager-street-v3.json            fonts and icons on the owner's Worker; what the app will load.
//   ../server/pmtiles-worker/src/street-style-v3.json   the same file, bundled into the Worker and served
//                                     at /style/street-v3.json.
//   forager-street-v3.preview.json    fonts and icons from serve.py, for the preview and tools/check-style.mjs.
//
// Since the fill-in (sage-09, docs/design/2026-10-30-fill-in-rulings.md in Forager-cartography) it also writes v4: v3 with
// overrides/forager-fill-in.mjs applied last and its own night tables, beside v3, which is written exactly as before. v4 is
// written to map-style only (forager-street-v4.json and its preview copy): the Worker's copy, and the app's switch to it,
// are each their own step.
import fs from "node:fs";
import { layers, namedFlavor } from "@protomaps/basemaps";
import { applyStreetDesign, withBuildingOutline, COLOURS, NIGHT_ICON_RINGS, NIGHT_LAYER_BANDS, NIGHT_LAYER_COLOURS } from "./overrides/forager-street.mjs";
import { applyCharacter, NIGHT_FILL_COLOURS, NIGHT_PATTERN_COLOURS } from "./overrides/forager-character.mjs";
import { REFERENCE_SOURCE_ID, referenceSource, withReferenceLayers } from "./overrides/forager-reference.mjs";
import { applyFillIn, FILL_IN_COLOURS, NIGHT_FILL_COLOURS_V4, NIGHT_LAYER_COLOURS_V4, NIGHT_LINE_COLOURS_V4 } from "./overrides/forager-fill-in.mjs";

const here = (path) => new URL(path, import.meta.url);

/** The owner's mapping Worker: tiles, the style, fonts and icons all come from here. */
export const WORKER = "https://forager-pmtiles.brandonlee1-894.workers.dev";
/**
 * The archive the tiles come from, by its R2 name (us.pmtiles). Kept as it is served today; when a new
 * US archive replaces us.pmtiles under the same name, nothing here changes. A different name would be a
 * new style version, because offline packs are keyed by the style URL.
 */
const ARCHIVE = "us";
const LOCAL = "http://localhost:8765";
/**
 * The sprite each version names (tools/build-sprite-v5.mjs): v3 sprite v5; v4 sprite v6, v5 unchanged plus the orchard,
 * vineyard and nursery patterns (dispatch 2026-10-31-coder-07 Part 3; the owner: "New icon set v6 (Recommended)").
 */
const SPRITES = { v3: "sprites/v5/light", v4: "sprites/v6/light" };

// The sprite's images the app's night rules invert (its bundled night set since dispatch 2026-10-22-06), recorded in the style's
// metadata because MapLibre has no call that lists a style's images; the app's copy is checked against this one by
// a unit test (StreetStyleTest). The patterns the app paints one colour instead (forager:night-patterns) are left
// off it, so no image is both inverted and painted.
const spriteIconsOf = (sprite) => Object.keys(JSON.parse(fs.readFileSync(here(`./assets/${sprite}.json`), "utf8")))
  .filter((name) => !(name in NIGHT_PATTERN_COLOURS))
  .sort();

// v3's buildings, solid with an outline (dispatch 2026-10-27-02), are applied here and not in applyStreetDesign, which
// v2 was generated with.
const v3Layers = () => withReferenceLayers(withBuildingOutline(applyCharacter(applyStreetDesign(layers("protomaps", namedFlavor("light"), { lang: "en" })))));

/** What differs between the versions this script writes: v4 is v3's layers with the fill-in and v4's night tables. */
const VERSIONS = {
  v3: {
    layers: v3Layers(),
    generatedFrom: "@protomaps/basemaps 5.7.2, flavor light, with overrides/forager-street.mjs, overrides/forager-character.mjs and overrides/forager-reference.mjs",
    palette: COLOURS,
    nightLayers: NIGHT_LAYER_COLOURS,
    nightFills: NIGHT_FILL_COLOURS,
    toWorker: true,
  },
  v4: {
    layers: applyFillIn(v3Layers()),
    generatedFrom: "@protomaps/basemaps 5.7.2, flavor light, with overrides/forager-street.mjs, overrides/forager-character.mjs, overrides/forager-reference.mjs and overrides/forager-fill-in.mjs",
    palette: { ...COLOURS, fillIn: FILL_IN_COLOURS },
    nightLayers: NIGHT_LAYER_COLOURS_V4,
    nightFills: NIGHT_FILL_COLOURS_V4,
    nightLines: NIGHT_LINE_COLOURS_V4,
    toWorker: false,
  },
};

const style = (version, assetsBase) => ({
  version: 8,
  name: `Forager Street ${version}`,
  metadata: {
    "forager:generated-from": VERSIONS[version].generatedFrom,
    "forager:sprite-icons": spriteIconsOf(SPRITES[version]),
    // The design's colours, day and the night exceptions (-1343), for the app's tests to read.
    "forager:palette": VERSIONS[version].palette,
    // Night exceptions (-1345): by layer, and for icon rings.
    "forager:night-layers": VERSIONS[version].nightLayers,
    "forager:night-icon-rings": NIGHT_ICON_RINGS,
    // Per-layer contrast bands that replace the night floor for one layer on one ground (dispatch 2026-10-19-41).
    "forager:night-bands": NIGHT_LAYER_BANDS,
    // Night fill colours by day colour, and pattern images painted one colour (dispatch 2026-10-22-03).
    "forager:night-fills": VERSIONS[version].nightFills,
    "forager:night-patterns": NIGHT_PATTERN_COLOURS,
    // v4 only: night line colours by day colour, per layer (the night tertiary). Absent from v3, which stays byte for byte.
    ...(VERSIONS[version].nightLines ? { "forager:night-line-colours": VERSIONS[version].nightLines } : {}),
  },
  sources: {
    protomaps: {
      type: "vector",
      url: `${WORKER}/${ARCHIVE}.json`,
      attribution: '<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://osm.org/copyright">OpenStreetMap</a>',
    },
    // The official-trails band's tiles, by address in the style rather than a TileJSON (see forager-reference.mjs).
    [REFERENCE_SOURCE_ID]: referenceSource(WORKER),
  },
  glyphs: `${assetsBase}/fonts/{fontstack}/{range}.pbf`,
  sprite: `${assetsBase}/${SPRITES[version]}`,
  layers: VERSIONS[version].layers,
});

for (const version of Object.keys(VERSIONS)) {
  const forWorker = JSON.stringify(style(version, WORKER), null, 2) + "\n";
  fs.writeFileSync(here(`./forager-street-${version}.json`), forWorker);
  if (VERSIONS[version].toWorker) fs.writeFileSync(here(`../server/pmtiles-worker/src/street-style-${version}.json`), forWorker);
  fs.writeFileSync(here(`./forager-street-${version}.preview.json`), JSON.stringify(style(version, `${LOCAL}/assets`), null, 2) + "\n");
  const where = VERSIONS[version].toWorker ? `; copied to server/pmtiles-worker/src/street-style-${version}.json` : "; map-style only";
  console.log(`forager-street-${version}.json: ${VERSIONS[version].layers.length} layers${where}`);
}
