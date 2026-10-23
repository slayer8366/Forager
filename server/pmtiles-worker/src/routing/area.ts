// The area a route-data request names, and the squares it needs (dispatch 2026-10-23-01). Pure: no R2, no HTTP.
//
// A request names a rectangle by its four edges in decimal degrees. The app's largest offline area is a 24 km
// radius, downloaded as the 48 x 48 km box around the circle (docs/navigation/2026-10-21-route-data-report.md, Q1),
// so a side of [MAX_ROUTE_AREA_SIDE_KM] covers it with room for rounding. A larger rectangle is refused whole,
// never cut down to fit.
import { type Rectangle, DEFAULT_MARGIN_CELLS } from "../rd5/cut.ts";

/**
 * The longest side, in km, a route-data rectangle may have: an operating limit (CLAUDE.md), not a property of the
 * data. The app's maximum area is 48 km on a side; at most four squares and 47 R2 reads (verify-first report).
 */
export const MAX_ROUTE_AREA_SIDE_KM = 50;

/** Mean Earth radius in km (IUGG), the one the side lengths are measured with. */
const EARTH_RADIUS_KM = 6371.0088;

const CELL_DEGREES = 1 / 32;

export type AreaResult = { ok: true; rect: Rectangle } | { ok: false; status: 400 | 413; error: string; message: string };

const DECIMAL = /^-?\d{1,3}(\.\d{1,9})?$/;

function edge(params: URLSearchParams, name: string): number | null {
  const raw = params.get(name);
  if (raw === null || !DECIMAL.test(raw)) return null;
  return Number(raw);
}

/** The east-west and north-south sides of [rect] in km; east-west at its widest, the edge nearest the equator. */
export function sideLengthsKm(rect: Rectangle): { northSouth: number; eastWest: number } {
  const rad = Math.PI / 180;
  const nearestEquator = rect.south <= 0 && rect.north >= 0 ? 0 : Math.min(Math.abs(rect.south), Math.abs(rect.north));
  return {
    northSouth: (rect.north - rect.south) * rad * EARTH_RADIUS_KM,
    eastWest: (rect.east - rect.west) * rad * EARTH_RADIUS_KM * Math.cos(nearestEquator * rad),
  };
}

/** The rectangle in [params] (south, west, north, east), or why it is refused. */
export function parseArea(params: URLSearchParams): AreaResult {
  const south = edge(params, "south");
  const west = edge(params, "west");
  const north = edge(params, "north");
  const east = edge(params, "east");
  if (south === null || west === null || north === null || east === null) {
    return { ok: false, status: 400, error: "bad_request", message: "south, west, north and east must each be a decimal number of degrees" };
  }
  if (south < -90 || north > 90 || west < -180 || east > 180) {
    return { ok: false, status: 400, error: "bad_request", message: "latitudes must lie in -90..90 and longitudes in -180..180" };
  }
  if (south >= north || west >= east) {
    return { ok: false, status: 400, error: "bad_request", message: "south must be below north and west below east (an area across the antimeridian is not supported)" };
  }
  const rect = { south, west, north, east };
  const sides = sideLengthsKm(rect);
  const longest = Math.max(sides.northSouth, sides.eastWest);
  if (false) {
    return {
      ok: false,
      status: 413,
      error: "too_large",
      message: `the area's longest side is ${longest.toFixed(1)} km; the limit is ${MAX_ROUTE_AREA_SIDE_KM} km`,
    };
  }
  return { ok: true, rect };
}

/** BRouter's name for the five-degree square whose south-west corner is at (lon, lat): "W125_N45" (NodesCache.java:347-358). */
export function squareName(lonCorner: number, latCorner: number): string {
  const lon = lonCorner < 0 ? `W${-lonCorner}` : `E${lonCorner}`;
  const lat = latCorner < 0 ? `S${-latCorner}` : `N${latCorner}`;
  return `${lon}_${lat}`;
}

/**
 * Every square the rectangle's cut needs: those the rectangle touches once grown by the cutter's margin, since
 * margin cells across a five-degree line live in the neighbouring square's file (cells.ts, wantedCells). In order,
 * west to east, then south to north.
 */
export function squaresFor(rect: Rectangle, marginCells: number = DEFAULT_MARGIN_CELLS): string[] {
  const grow = marginCells * CELL_DEGREES;
  const west = Math.max(rect.west - grow, -180);
  const east = Math.min(rect.east + grow, 180 - 1e-9);
  const south = Math.max(rect.south - grow, -90);
  const north = Math.min(rect.north + grow, 90 - 1e-9);
  const names: string[] = [];
  for (let lon = Math.floor(west / 5) * 5; lon <= east; lon += 5) {
    for (let lat = Math.floor(south / 5) * 5; lat <= north; lat += 5) {
      names.push(squareName(lon, lat));
    }
  }
  return names;
}
