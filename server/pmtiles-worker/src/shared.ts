// Path parsing for /{name}/{z}/{x}/{y}.{ext} tile requests and /{name}.json tileset requests.
// Copied from protomaps/PMTiles serverless/shared/index.ts (MIT licensed), which the upstream
// Cloudflare Worker reference implementation imports as a sibling package in their monorepo. This
// project only needs the Cloudflare target, so it's inlined here as a single-purpose file instead
// of reproducing their multi-backend directory layout.
export const pmtilesPath = (name: string, setting?: string): string => {
  if (setting) {
    return setting.replaceAll("{name}", name);
  }
  return `${name}.pmtiles`;
};

const TILE =
  /^\/(?<NAME>[0-9a-zA-Z\/!\-_\.\*\'\(\)]+)\/(?<Z>\d+)\/(?<X>\d+)\/(?<Y>\d+).(?<EXT>[a-z]+)$/;

const TILESET = /^\/(?<NAME>[0-9a-zA-Z\/!\-_\.\*\'\(\)]+).json$/;

export const tilePath = (
  path: string
): {
  ok: boolean;
  name: string;
  tile?: [number, number, number];
  ext: string;
} => {
  const tileMatch = path.match(TILE);

  if (tileMatch) {
    const g = tileMatch.groups!;
    return { ok: true, name: g.NAME, tile: [+g.Z, +g.X, +g.Y], ext: g.EXT };
  }

  const tilesetMatch = path.match(TILESET);

  if (tilesetMatch) {
    const g = tilesetMatch.groups!;
    return { ok: true, name: g.NAME, ext: "json" };
  }

  return { ok: false, name: "", tile: [0, 0, 0], ext: "" };
};

/**
 * Web-Mercator bounds of tile z/x/y in degrees. Pure; used by the overflow gate below.
 */
export const tileBoundsDegrees = (z: number, x: number, y: number) => {
  const n = 2 ** z;
  const latOf = (yy: number) => (Math.atan(Math.sinh(Math.PI * (1 - (2 * yy) / n))) * 180) / Math.PI;
  return { west: (x / n) * 360 - 180, east: ((x + 1) / n) * 360 - 180, north: latOf(y), south: latOf(y + 1) };
};

/**
 * Whether tile z/x/y overlaps an archive's own bounds (PMTiles header minLon/minLat/maxLon/maxLat).
 * The overflow path may only reach upstream for tiles the local archive itself covers: an offline
 * download only ever asks for tiles inside the region a user picked, so a request outside the
 * archive is never a tester's, and answering it from upstream would let anyone drive Protomaps
 * traffic through this Worker for the whole planet (tile-policy pulse, 2026-09-12, finding #3).
 */
export const tileIntersectsBounds = (
  z: number,
  x: number,
  y: number,
  b: { minLon: number; minLat: number; maxLon: number; maxLat: number }
): boolean => {
  const t = tileBoundsDegrees(z, x, y);
  return t.west < b.maxLon && t.east > b.minLon && t.south < b.maxLat && t.north > b.minLat;
};
