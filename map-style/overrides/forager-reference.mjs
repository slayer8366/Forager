// The official-trails band (step 8, PR 8a; dispatch 2026-10-26-14 and its Amendment 1; RECORD -1633, -1677, -1678):
// the Forest Service's and BLM's trails drawn as a wide, faint violet band under Trail Map's own trail lines, with the
// Forest Service trail number along it from zoom 14. Drawn apart from the OpenStreetMap trails, never joined to them,
// and routes never follow it (-445, -1176). generate-street.mjs adds it to street-v3.json with withReferenceLayers().
//
// **Everything about the server's archive is here and nowhere else.** The archive's name, its zoom range, its
// source-layer names and its fields are the step 8 report's proposal for Aspen's archive (section 7), premises rather
// than facts until the archive exists. A different name is one line below, then `npm run generate:street`.
//
// **The tile address is in the style, not a TileJSON URL** (Amendment 1, the owner: "When Aspen's data is live
// (Recommended)"). MapLibre fetches a source's TileJSON when the style loads, and until the archive is on R2 the
// Worker answers it 404. For anything but a tile, MapLibre 13.5.0 reports a 404 as an error and never retries it
// (`http_file_source.cpp:172-177`, `http_timeout.cpp:16-32`): the app ends an area download on that error
// (`MapLibreOfflineMapRepository.kt`, `downloadErrorKind`), and online the style never counts as loaded, so no frame
// is ever full. A 404 on a tile is "no content" (`http_file_source.cpp:172`), and a source whose tiles are in the
// style is loaded at once (`tile_source.cpp:46-50`). The cost: the zoom range lives here instead of the server's
// TileJSON, so the archive must be built to these zooms (it must reach 15 in any case: the Worker advertises 15 for
// every archive and sends higher requests to its Protomaps overflow, `server/pmtiles-worker/src/index.ts`).
//
// The app does not load v3 yet (Amendment 1): it switches in its own PR once the archive is live.

/** The style's source id for the agency trails. */
export const REFERENCE_SOURCE_ID = "reference";

/** The R2 archive the Worker serves the tiles from (`reference-trails.pmtiles`), by the name its URLs use. */
export const REFERENCE_ARCHIVE = "reference-trails";

/** The archive's zoom range (report section 7: 10 to 15, the offline range; above 15 the map overzooms). */
export const REFERENCE_MIN_ZOOM = 10;
export const REFERENCE_MAX_ZOOM = 15;

/** The archive's source-layers: one per agency, never merged (report section 7). */
export const SOURCE_LAYERS = { forestService: "fs_trails", blm: "blm_trails" };

/** The Forest Service field and value that mark a snow (nordic) trail, left out (-1633, the owner: "Hide them"). */
export const FS_TRAIL_TYPE_FIELD = "trail_type";
export const FS_SNOW_TRAIL_TYPE = "SNOW";

/** The Forest Service trail number, the label along the band. BLM's numbering field is not settled, so no BLM label. */
export const FS_TRAIL_NUMBER_FIELD = "trail_no";

/**
 * The band's look (report section 2, an example the owner has not yet seen on a phone): violet, a family nothing else
 * on the map uses, at 35%, 6 dp at zoom 12 widening to 10 dp at 15, from zoom 12; the number from zoom 14.
 */
export const BAND = { colour: "#7b5ea7", opacity: 0.35, minzoom: 12, widthAtMin: 6, widthAtMax: 10, labelMinzoom: 14 };

/** Trail Map's own trail layers all start with this; the band goes below the lowest of them. */
export const TRAIL_LAYER_PREFIX = "roads_path_";

/** The vector source, with its tile address on the Worker at [worker]. */
export function referenceSource(worker) {
  return {
    type: "vector",
    tiles: [`${worker}/${REFERENCE_ARCHIVE}/{z}/{x}/{y}.mvt`],
    minzoom: REFERENCE_MIN_ZOOM,
    maxzoom: REFERENCE_MAX_ZOOM,
  };
}

const notSnow = ["!=", ["coalesce", ["get", FS_TRAIL_TYPE_FIELD], ""], FS_SNOW_TRAIL_TYPE];

const band = (id, sourceLayer, filter) => ({
  id,
  type: "line",
  source: REFERENCE_SOURCE_ID,
  "source-layer": sourceLayer,
  minzoom: BAND.minzoom,
  ...(filter ? { filter } : {}),
  layout: { "line-cap": "round", "line-join": "round" },
  paint: {
    "line-color": BAND.colour,
    "line-opacity": BAND.opacity,
    "line-width": ["interpolate", ["linear"], ["zoom"], BAND.minzoom, BAND.widthAtMin, REFERENCE_MAX_ZOOM, BAND.widthAtMax],
  },
});

/** The two bands, bottom to top. Snow trails are Forest Service only; BLM's layer has no snow type. */
export function bandLayers() {
  return [
    band("reference_fs_band", SOURCE_LAYERS.forestService, notSnow),
    band("reference_blm_band", SOURCE_LAYERS.blm, null),
  ];
}

/** The Forest Service trail number along its band, from zoom 14, snow trails left out like their band. */
export function labelLayers() {
  return [
    {
      id: "reference_fs_number",
      type: "symbol",
      source: REFERENCE_SOURCE_ID,
      "source-layer": SOURCE_LAYERS.forestService,
      minzoom: BAND.labelMinzoom,
      filter: ["all", notSnow, ["has", FS_TRAIL_NUMBER_FIELD]],
      layout: {
        "symbol-placement": "line",
        "text-field": ["get", FS_TRAIL_NUMBER_FIELD],
        "text-font": ["Noto Sans Regular"],
        "text-size": 10,
      },
      paint: { "text-color": BAND.colour, "text-halo-color": "#ffffff", "text-halo-width": 1.5 },
    },
  ];
}

/**
 * [layers] with the bands directly below the lowest Trail Map trail layer, so every trail line draws on top of its
 * band, and the number directly above the highest, so no trail line crosses it. Throws if the style has no trail
 * layers, rather than putting the band somewhere else.
 */
export function withReferenceLayers(layers) {
  const first = layers.findIndex((l) => l.id.startsWith(TRAIL_LAYER_PREFIX));
  const last = layers.findLastIndex((l) => l.id.startsWith(TRAIL_LAYER_PREFIX));
  if (first < 0) throw new Error(`No ${TRAIL_LAYER_PREFIX}* layer: nowhere to put the official-trails band`);
  return [...layers.slice(0, first), ...bandLayers(), ...layers.slice(first, last + 1), ...labelLayers(), ...layers.slice(last + 1)];
}
