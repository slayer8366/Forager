// The offline style with OSM's first zooms (dispatch 2026-11-02-coder-13; Aspen: "In offline-style.json, match the
// zooms of the layers it already has. Don't add labels"). Reads ../server/pmtiles-worker/src/offline-style.json, which
// stays byte for byte (/style/offline.json is untouched, -1294: MapLibre keys a downloaded pack by its style URL), and
// writes forager-offline-v2.json beside this folder, wired to nothing. Its layers are a subset of v4's; each takes v5's
// zoom from overrides/forager-osm-zooms.mjs (row numbers are Juniper's comparison's), except where its own filter holds
// more kinds than v4's (landuse_park, roads_other). Run: node tools/build-offline-v2.mjs
import fs from "node:fs";
import { split, byLevel, set, isIn, CLONES } from "../overrides/forager-osm-zooms.mjs";

const here = (p) => new URL(`../${p}`, import.meta.url);
const style = JSON.parse(fs.readFileSync(here("../server/pmtiles-worker/src/offline-style.json"), "utf8"));
const L = style.layers;

// Its own landuse_park: parks too, and the reserve fills OSM never draws (B2, unchanged).
split(L, "landuse_park", { minzoom: 3.5 }, [ // forest, wood, scrub, grass, sand, glacier: 5
  { name: "military", minzoom: 6.5, cond: isIn("kind", ["military", "naval_base", "airfield"]) }, // row 18: 8
  { name: "cemetery_golf", minzoom: 8.5, cond: isIn("kind", ["cemetery", "golf_course"]) }, // row 13: 10
  { name: "park", minzoom: 8.5, cond: isIn("kind", ["park"]) }, // row 13: 10
  { name: "reserves", minzoom: 0, cond: isIn("kind", ["national_park", "protected_area", "nature_reserve"]) }, // B2
]);
set(L, "landuse_urban_green", { minzoom: 8.5 }); // allotments 10 (row 13)
split(L, "landuse_urban_green", { minzoom: 8.5 }, [
  { name: "village_green", minzoom: 3.5, cond: isIn("kind", ["village_green"]) }, // row 13: 5
  { name: "playground", minzoom: 11.5, cond: isIn("kind", ["playground"]) }, // row 14: 13
]);
for (const [id, z] of [["landuse_hospital", 8.5], ["landuse_school", 8.5], ["landuse_industrial", 6.5], ["landuse_beach", 8.5],
  ["landuse_aerodrome", 8.5], ["landuse_runway", 12.5], ["landuse_pier", 10.5], ["buildings", 12.5]]) set(L, id, { minzoom: z }); // rows 11-24
split(L, "landuse_pedestrian", { minzoom: 13.5 }, [{ name: "dam", minzoom: 11.5, cond: isIn("kind", ["dam"]) }]); // rows 22, 23
set(L, "water_river", { minzoom: 6.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 6.5, 0.6, 9.5, 1, 18, 12] }); // row 32
set(L, "water_stream", { minzoom: 10.5 }); // row 33: 12 (offline: 14)
byLevel(L, "water_stream", [11], 10); // intermittent and seasonal: 13
set(L, "roads_highway", { minzoom: 4.5 }); // row 35
set(L, "roads_highway_casing_early", { minzoom: 4.5 });
split(L, "roads_major", { minzoom: 4.5 }, [ // rows 37-41
  { name: "primary", minzoom: 6.5, cond: isIn("kind_detail", ["primary"]) },
  { name: "secondary", minzoom: 7.5, cond: isIn("kind_detail", ["secondary"]) },
  { name: "tertiary", minzoom: 8.5, cond: isIn("kind_detail", ["tertiary"]) },
  { name: "links", minzoom: 8.5, cond: isIn("kind_detail", ["trunk_link", "primary_link", "secondary_link", "tertiary_link"]) },
]);
set(L, "roads_link", { minzoom: 8.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 8.5, 0.5, 13.5, 1, 18, 11] }); // row 41
split(L, "roads_minor", { minzoom: 10.5 }, [{ name: "road", minzoom: 8.5, cond: isIn("kind_detail", ["road"]) }]); // rows 42, 43
set(L, "roads_minor_service", { minzoom: 12.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 12.5, 0.3, 18, 8] }); // row 45
// Its own roads_other: every path class, as offline has no path layers of its own.
split(L, "roads_other", { minzoom: 12.5 }, [ // path, footway, steps, pedestrian, sidewalk, crossing: 14 (rows 47, 50, 51, 53)
  { name: "track_cycle_living", minzoom: 11.5, cond: isIn("kind_detail", ["track", "cycleway", "bridleway", "living_street"]) }, // rows 44, 49, 52: 13
]);
split(L, "roads_rail", { minzoom: 6.5 }, [ // rows 56-59
  { name: "service", minzoom: 11.5, cond: isIn("service", ["spur", "siding", "yard"]) },
  { name: "urban", minzoom: 10.5, cond: isIn("kind_detail", ["tram", "subway", "preserved"]) },
  { name: "disused", minzoom: 13.5, cond: isIn("kind_detail", ["disused"]) },
]);
set(L, "roads_pier", { minzoom: 10.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 10.5, 0.3, 12.5, 0.5, 20, 16] }); // row 66
set(L, "roads_runway", { minzoom: 9.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 9.5, 0.5, 12, 4, 18, 30] }); // row 64
set(L, "roads_taxiway", { minzoom: 9.5, "line-width": ["interpolate", ["exponential", 1.6], ["zoom"], 9.5, 0.3, 13.5, 1, 15, 6] }); // row 65
set(L, "boundaries_country", { minzoom: 2.5 }); // row 68
split(L, "boundaries", { minzoom: 2.5 }, [ // rows 68-72
  { name: "admin5", minzoom: 6.5, cond: ["==", ["get", "kind_detail"], 5] },
  { name: "admin6", minzoom: 8.5, cond: ["==", ["get", "kind_detail"], 6] },
  { name: "admin7", minzoom: 9.5, cond: ["==", ["get", "kind_detail"], 7] },
  { name: "admin8", minzoom: 10.5, cond: [">=", ["get", "kind_detail"], 8] },
]);
// The reserve fills keep no minzoom, as before.
delete L.find((l) => l.id === "landuse_park__reserves").minzoom;

style.name = "Forager offline v2";
style.metadata = { ...(style.metadata ?? {}), "forager:generated-from": "server/pmtiles-worker/src/offline-style.json with tools/build-offline-v2.mjs (OSM's first zooms)", "forager:layer-clones": { ...CLONES } };
fs.writeFileSync(here("forager-offline-v2.json"), JSON.stringify(style, null, 2) + "\n");
console.log(`forager-offline-v2.json: ${L.length} layers (${Object.keys(CLONES).length} clones); no symbol layers: ${L.every((l) => l.type !== "symbol")}`);
