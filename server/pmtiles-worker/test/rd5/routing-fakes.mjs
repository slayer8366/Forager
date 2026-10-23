// Fakes for calling the route-data handler (src/routing/handler.ts) outside a Worker: an R2 bucket over bytes in
// memory with byte-range reads, the edge cache as a Map, and rate limiters that say yes or no as told.
import { createHash } from "node:crypto";

export const TEST_APP_KEY = "test-app-key-not-the-real-one";

const sha256 = (bytes) => createHash("sha256").update(bytes).digest("hex");

// A manifest in the shape the Pi planner's upload writes (forager-cartography
// docs/audits/2026-10-23-route-squares-upload.md): paths relative to routing/.
export function manifestFor(runId, squares) {
  return JSON.stringify({
    run_id: runId,
    osm_timestamp: "2026-10-17T20:20:51Z",
    osm_sequence: 4567,
    files: Object.entries(squares).map(([name, bytes]) => ({
      path: `${runId}/segments4/${name}.rd5`,
      size: bytes.byteLength,
      sha256: sha256(bytes),
    })),
  });
}

// objects: { key: Buffer|string }. Records every get as [key, offset, length].
export function fakeBucket(objects) {
  const reads = [];
  return {
    reads,
    objects,
    async get(key, options) {
      const value = objects[key];
      const range = options?.range;
      reads.push([key, range?.offset, range?.length]);
      if (value === undefined) return null;
      const all = typeof value === "string" ? Buffer.from(value) : value;
      const bytes = range ? all.subarray(range.offset, range.offset + range.length) : all;
      const copy = () => bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
      return { size: all.byteLength, etag: "fixture", httpMetadata: {}, body: true, arrayBuffer: async () => copy(), text: async () => bytes.toString("utf8") };
    },
  };
}

export class FakeCache {
  constructor() { this.entries = new Map(); }
  async match(key) {
    const url = typeof key === "string" ? key : key.url;
    const hit = this.entries.get(url);
    return hit ? hit.clone() : undefined;
  }
  async put(key, response) {
    const url = typeof key === "string" ? key : key.url;
    this.entries.set(url, response.clone());
  }
}

// A limiter that allows `allowed` calls per key and then refuses; records every key it was asked about.
export function fakeLimiter(allowed = Infinity) {
  const counts = new Map();
  const keys = [];
  return {
    keys,
    async limit({ key }) {
      keys.push(key);
      const n = (counts.get(key) ?? 0) + 1;
      counts.set(key, n);
      return { success: n <= allowed };
    },
  };
}
