// The route-data manifest on R2, `routing/manifest.json`, which names the current build of the route squares
// (dispatch 2026-10-23-01; RECORD -1553, -1568). Its fields, as the Pi planner's upload writes them
// (forager-cartography docs/audits/2026-10-23-route-squares-upload.md, relayed by the planner):
//
//   { "run_id": "usca-20261021T111429Z", "osm_timestamp": "2026-10-17T20:20:51Z", "osm_sequence": …,
//     "files": [ { "path": "usca-20261021T111429Z/segments4/E170_N50.rd5", "size": …, "sha256": "…" }, … ] }
//
// `path` is relative to `routing/`. Only that exact shape is read: a path under another run, another folder or
// another name is a manifest the Worker refuses, never an R2 key it follows.

export const MANIFEST_KEY = "routing/manifest.json";

const RUN_ID = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
const SQUARE_FILE = /^([WE]\d{1,3}_[NS]\d{1,2})\.rd5$/;

export interface ManifestSquare {
  /** BRouter's file name, "W125_N45.rd5". */
  fileName: string;
  /** The R2 key, "routing/<run_id>/segments4/W125_N45.rd5". */
  key: string;
  size: number;
}

export interface Manifest {
  runId: string;
  osmTimestamp: string;
  /** By square name, "W125_N45". */
  squares: Map<string, ManifestSquare>;
}

export class ManifestInvalidError extends Error {}

export const isRunId = (s: string): boolean => RUN_ID.test(s);

export function parseManifest(text: string): Manifest {
  let json: unknown;
  try {
    json = JSON.parse(text);
  } catch {
    throw new ManifestInvalidError("routing/manifest.json is not JSON");
  }
  const m = json as { run_id?: unknown; osm_timestamp?: unknown; files?: unknown };
  if (typeof m.run_id !== "string" || !isRunId(m.run_id)) throw new ManifestInvalidError("manifest run_id is missing or malformed");
  if (typeof m.osm_timestamp !== "string") throw new ManifestInvalidError("manifest osm_timestamp is missing");
  if (!Array.isArray(m.files) || m.files.length === 0) throw new ManifestInvalidError("manifest files is missing or empty");
  const prefix = `${m.run_id}/segments4/`;
  const squares = new Map<string, ManifestSquare>();
  for (const f of m.files as { path?: unknown; size?: unknown }[]) {
    if (typeof f.path !== "string" || !f.path.startsWith(prefix)) {
      throw new ManifestInvalidError(`manifest file path is not under ${prefix}: ${String(f.path)}`);
    }
    const name = SQUARE_FILE.exec(f.path.slice(prefix.length));
    if (!name) throw new ManifestInvalidError(`manifest file is not a square: ${f.path}`);
    if (typeof f.size !== "number" || !Number.isSafeInteger(f.size) || f.size <= 0) {
      throw new ManifestInvalidError(`manifest size is not a positive whole number for ${f.path}`);
    }
    squares.set(name[1], { fileName: `${name[1]}.rd5`, key: `routing/${f.path}`, size: f.size });
  }
  return { runId: m.run_id, osmTimestamp: m.osm_timestamp, squares };
}
