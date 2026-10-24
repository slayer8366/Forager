// Which cells of a square a rectangle needs. BRouter's arithmetic at v1.7.10: a position is held as
// micro-degrees from the south-west corner of the world, ilon = (lon + 180) * 1e6 and ilat = (lat + 90) * 1e6;
// a cell is 1/32 degree, 31,250 micro-degrees (OsmFile.java:48); a square file is named for its 5-degree
// south-west corner (NodesCache.java:347-358); in a square, tile = lonMod5 * 5 + latMod5 (OsmFile.java:40-42)
// and in a tile, cell = latRow * 32 + lonCol (OsmFile.java:120).
import { CELLS_PER_DEGREE } from "./format.ts";

export interface Rectangle {
  south: number;
  west: number;
  north: number;
  east: number;
}

/** In one tile, the wanted cells: rows firstRow..lastRow, each from firstCol..lastCol (inclusive, 0-31). */
export interface TileCells {
  tile: number;
  firstRow: number;
  lastRow: number;
  firstCol: number;
  lastCol: number;
}

const CELLS_PER_SQUARE = 5 * CELLS_PER_DEGREE;
const SQUARE_NAME = /^([WE])(\d{1,3})_([NS])(\d{1,2})$/;

/** The square's south-west corner in global cell numbers, from a name such as "W75_N40". */
export function squareOrigin(square: string): { col: number; row: number } {
  const m = SQUARE_NAME.exec(square);
  if (!m) throw new RangeError(`not a square name: "${square}"`);
  const lon = (m[1] === "W" ? -1 : 1) * Number(m[2]);
  const lat = (m[3] === "S" ? -1 : 1) * Number(m[4]);
  if (lon % 5 !== 0 || lat % 5 !== 0 || lon < -180 || lon > 175 || lat < -90 || lat > 85) {
    throw new RangeError(`not a square name: "${square}" is not a 5-degree corner`);
  }
  return { col: (lon + 180) * CELLS_PER_DEGREE, row: (lat + 90) * CELLS_PER_DEGREE };
}

const micro = (degrees: number): number => Math.round(degrees * 1e6);
const cellOfLon = (lon: number): number => Math.floor(micro(lon + 180) / 31250);
const cellOfLat = (lat: number): number => Math.floor(micro(lat + 90) / 31250);

/**
 * The cells the rectangle touches, grown by marginCells on every side and clipped to the square, grouped by
 * one-degree tile in tile order. Cells beyond the square belong to a neighbouring square's own file.
 */
export function wantedCells(square: string, rect: Rectangle, marginCells: number): TileCells[] {
  if (!Number.isInteger(marginCells) || marginCells < 0) {
    throw new RangeError(`margin must be a whole number of cells, 0 or more: ${marginCells}`);
  }
  if (!(rect.south <= rect.north && rect.west <= rect.east)) {
    throw new RangeError("rectangle: south must not exceed north, nor west exceed east");
  }
  const origin = squareOrigin(square);
  const col0 = Math.max(cellOfLon(rect.west) - marginCells - origin.col, 0);
  const col1 = Math.min(cellOfLon(rect.east) + marginCells - origin.col, CELLS_PER_SQUARE - 1);
  const row0 = Math.max(cellOfLat(rect.south) - marginCells - origin.row, 0);
  const row1 = Math.min(cellOfLat(rect.north) + marginCells - origin.row, CELLS_PER_SQUARE - 1);
  if (col0 > col1 || row0 > row1) throw new RangeError(`rectangle does not overlap square ${square}`);

  const tiles: TileCells[] = [];
  for (let lonDeg = 0; lonDeg < 5; lonDeg++) {
    for (let latDeg = 0; latDeg < 5; latDeg++) {
      const base = (d: number) => d * CELLS_PER_DEGREE;
      const firstCol = Math.max(col0, base(lonDeg)) - base(lonDeg);
      const lastCol = Math.min(col1, base(lonDeg + 1) - 1) - base(lonDeg);
      const firstRow = Math.max(row0, base(latDeg)) - base(latDeg);
      const lastRow = Math.min(row1, base(latDeg + 1) - 1) - base(latDeg);
      if (firstCol <= lastCol && firstRow <= lastRow) {
        tiles.push({ tile: lonDeg * 5 + latDeg, firstRow, lastRow, firstCol, lastCol });
      }
    }
  }
  return tiles;
}
