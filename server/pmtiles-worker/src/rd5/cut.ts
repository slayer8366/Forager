// Cuts a BRouter .rd5 square down to the cells a rectangle needs, plus a margin (dispatch 2026-10-21-07;
// docs/navigation/2026-10-21-route-cutter-phase1-report.md, Q1). The result is a valid square file of the
// same name, holding only those cells; every other tile and cell is empty, which BRouter reads as "no data
// here" (OsmFile.java:56-58, :125-127 at v1.7.10).
//
// Cells are copied byte for byte with their own checksums: a cell decodes on its own, from its row and column
// alone (MicroCache2.java:35-63), so nothing in it changes. Rewritten: the header's tile offsets (with their
// version bits kept), the touched tiles' cell indexes, and the footer's checksums. The source's creation
// timestamp and elevation type are kept, so a cut traces to the build it came from.
//
// The source is read through RangeReader, a byte-range read, so the same code serves an R2 object in the
// Worker and a file in the tests. It reads the header, the footer, each touched tile's index, and one range
// per row of wanted cells in each touched tile: cells in a row are contiguous in the file.
//
// It has no production caller yet: the server route that will call it is a later part of the navigation work.
import { type Rectangle, wantedCells } from "./cells.ts";
import { brouterCrc } from "./crc32.ts";
import {
  CELLS_PER_DEGREE,
  CELLS_PER_TILE,
  HEADER_BYTES,
  TILE_INDEX_BYTES,
  checkTileOrder,
  footerLength,
  readFooter,
  readHeader,
  readTileIndex,
  tileStart,
  writeFooter,
  writeHeader,
  writeTileIndex,
} from "./format.ts";

export type { Rectangle } from "./cells.ts";
export { Rd5ChecksumError, Rd5UnsupportedError } from "./format.ts";

export interface RangeReader {
  readonly size: number;
  read(offset: number, length: number): Promise<Uint8Array>;
}

/** One cell of margin is what waypoint matching needs at the rectangle's edge (Phase 1 report, Q5 item 4). */
export const DEFAULT_MARGIN_CELLS = 1;

export async function cutRd5(
  source: RangeReader,
  square: string,
  rectangle: Rectangle,
  marginCells: number = DEFAULT_MARGIN_CELLS,
): Promise<Uint8Array> {
  const wanted = wantedCells(square, rectangle, marginCells);
  if (source.size < HEADER_BYTES) throw new RangeError(`rd5 too short: ${source.size} bytes`);

  const header = readHeader(await source.read(0, HEADER_BYTES));
  const footerAt = header.ends[24];
  const footer = readFooter(await source.read(footerAt, footerLength(header, source.size)), header);
  checkTileOrder(header);

  const parts: Uint8Array[] = [];
  const ends: number[] = [];
  const tileIndexCrcs: number[] = new Array(25).fill(0);
  let position = HEADER_BYTES;
  for (let tile = 0; tile < 25; tile++) {
    const cells = wanted.find((w) => w.tile === tile);
    const start = tileStart(header.ends, tile);
    if (cells && start < header.ends[tile]) {
      const tileIndex = readTileIndex(
        await source.read(start, TILE_INDEX_BYTES),
        footer.tileIndexCrcs[tile],
        tile,
      );
      const newEnds = new Int32Array(CELLS_PER_TILE);
      const tileParts: Uint8Array[] = [];
      let cellEnd = TILE_INDEX_BYTES;
      let next = 0; // the next cell index still to be given an end
      for (let row = cells.firstRow; row <= cells.lastRow; row++) {
        const first = row * CELLS_PER_DEGREE + cells.firstCol;
        const last = row * CELLS_PER_DEGREE + cells.lastCol;
        for (; next < first; next++) newEnds[next] = cellEnd; // unwanted cells: empty
        const from = first === 0 ? TILE_INDEX_BYTES : tileIndex[first - 1];
        const to = tileIndex[last];
        if (to > from) tileParts.push(await source.read(start + from, to - from));
        for (; next <= last; next++) {
          cellEnd += tileIndex[next] - (next === 0 ? TILE_INDEX_BYTES : tileIndex[next - 1]);
          newEnds[next] = cellEnd;
        }
      }
      for (; next < CELLS_PER_TILE; next++) newEnds[next] = cellEnd;
      if (cellEnd > TILE_INDEX_BYTES) {
        const index = writeTileIndex(newEnds);
        tileIndexCrcs[tile] = brouterCrc(index);
        parts.push(index, ...tileParts);
        position += cellEnd;
      }
    }
    ends.push(position);
  }

  const newHeader = writeHeader(ends, header.versionBits);
  const newFooter = writeFooter(newHeader, { ...footer, tileIndexCrcs });
  const out = new Uint8Array(position + newFooter.length);
  out.set(newHeader, 0);
  let at = HEADER_BYTES;
  for (const part of parts) {
    out.set(part, at);
    at += part.length;
  }
  out.set(newFooter, at);
  return out;
}
