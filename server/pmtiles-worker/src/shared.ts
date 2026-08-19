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
