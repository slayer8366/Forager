// Builds Forager's own Street style (dispatch 2026-10-18-05 Part 2) from the same pinned Protomaps light
// layers as forager-light.json, with overrides/forager-street.mjs applied. Run: npm run generate:street
//
// A separate script, not a branch inside generate.mjs: forager-light.json stays exactly what it was (the
// website and the label check read it), and the Street style is its own output.
//
// Three files come out, identical except for where fonts and icons are fetched from:
//   forager-street-v1.json            fonts and icons on the owner's Worker; what the app loads.
//   ../server/pmtiles-worker/src/street-style-v1.json   the same file, bundled into the Worker and served
//                                     at /style/street-v1.json.
//   forager-street-v1.preview.json    fonts and icons from serve.py, for the preview and tools/check-style.mjs.
import fs from "node:fs";
import { layers, namedFlavor } from "@protomaps/basemaps";
import { applyStreetDesign, COLOURS, NIGHT_ICON_RINGS, NIGHT_LAYER_BANDS, NIGHT_LAYER_COLOURS } from "./overrides/forager-street.mjs";

const here = (path) => new URL(path, import.meta.url);

/** The owner's mapping Worker: tiles, the style, fonts and icons all come from here. */
export const WORKER = "https://forager-pmtiles.brandonlee1-894.workers.dev";
/**
 * The archive the tiles come from, by its R2 name (us.pmtiles). Kept as it is served today; when a new
 * US archive replaces us.pmtiles under the same name, nothing here changes. A different name would be a
 * new style version (street-v2.json), because offline packs are keyed by the style URL.
 */
const ARCHIVE = "us";
const LOCAL = "http://localhost:8765";

// The sprite's icon names, recorded in the style's metadata. The app inverts each one for night by name
// (Style.getImage / addImage), and MapLibre has no call that lists a style's images, so the list travels
// with the style; the app's copy is checked against this one by a unit test (StreetStyleTest).
const spriteIcons = Object.keys(JSON.parse(fs.readFileSync(here("./assets/sprites/v4/light.json"), "utf8"))).sort();

const allLayers = applyStreetDesign(layers("protomaps", namedFlavor("light"), { lang: "en" }));

const style = (assetsBase) => ({
  version: 8,
  name: "Forager Street v1",
  metadata: {
    "forager:generated-from": "@protomaps/basemaps 5.7.2, flavor light, with overrides/forager-street.mjs",
    "forager:sprite-icons": spriteIcons,
    // The design's colours, day and the night exceptions (-1343), for the app's tests to read.
    "forager:palette": COLOURS,
    // Night exceptions (-1345): by layer, and for icon rings.
    "forager:night-layers": NIGHT_LAYER_COLOURS,
    "forager:night-icon-rings": NIGHT_ICON_RINGS,
    // Per-layer contrast bands that replace the night floor for one layer on one ground (dispatch 2026-10-19-41).
    "forager:night-bands": NIGHT_LAYER_BANDS,
  },
  sources: {
    protomaps: {
      type: "vector",
      url: `${WORKER}/${ARCHIVE}.json`,
      attribution: '<a href="https://github.com/protomaps/basemaps">Protomaps</a> © <a href="https://osm.org/copyright">OpenStreetMap</a>',
    },
  },
  glyphs: `${assetsBase}/fonts/{fontstack}/{range}.pbf`,
  sprite: `${assetsBase}/sprites/v4/light`,
  layers: allLayers,
});

const forWorker = JSON.stringify(style(WORKER), null, 2) + "\n";
fs.writeFileSync(here("./forager-street-v1.json"), forWorker);
fs.writeFileSync(here("../server/pmtiles-worker/src/street-style-v1.json"), forWorker);
fs.writeFileSync(here("./forager-street-v1.preview.json"), JSON.stringify(style(`${LOCAL}/assets`), null, 2) + "\n");
console.log(`forager-street-v1.json: ${allLayers.length} layers; copied to server/pmtiles-worker/src/street-style-v1.json`);
