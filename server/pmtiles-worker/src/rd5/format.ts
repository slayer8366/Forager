// The parts of a BRouter .rd5 square a cutter reads and rewrites, from BRouter v1.7.10's reader
// (PhysicalFile.java, OsmFile.java) and writer (WayLinker.java:436-560). Big-endian throughout.
//
//   header  bytes 0-199: 25 longs, one per one-degree tile (index lonMod5 * 5 + latMod5). Low 48 bits: the
//           absolute end offset of the tile; tile 0 starts at 200 and each later tile where the one before
//           ends. Top 16 bits: the lookup version, or in long 1 the minor version.
//   tile    a 4,096-byte index of 1,024 ints, each a cell's cumulative end relative to the tile start (the
//           first cell starts at 4,096), then the cells. A tile whose start equals its end is empty.
//   cell    encoded bytes plus their own checksum, Crc32 ^ 2. Never touched by the cutter.
//   footer  at the end of tile 24: an 8-byte timestamp, Crc32(header) ^ 2, 25 Crc32s of the tile indexes
//           (0 for an empty tile), one elevation-type byte.
import { brouterCrc } from "./crc32.ts";

export const HEADER_BYTES = 200;
export const TILE_INDEX_BYTES = 4096;
export const CELLS_PER_TILE = 1024;
export const CELLS_PER_DEGREE = 32;
const FOOTER_MIN_BYTES = 8 + 26 * 4; // PhysicalFile.java:113
const FOOTER_BYTES = FOOTER_MIN_BYTES + 1; // with the elevation byte, as WayLinker writes it
const FORMAT_MARKER = 2; // the "^ 2": 32 cells per degree (PhysicalFile.java:131-137)

/** The square is valid but in a form this cutter does not handle. Never guessed around. */
export class Rd5UnsupportedError extends Error {}
/** The square fails BRouter's own checks; cutting it would only copy the damage. */
export class Rd5ChecksumError extends Error {}

export interface Rd5Header {
  bytes: Uint8Array;
  /** Absolute end offset of each of the 25 tiles. */
  ends: number[];
  /** The top 16 bits of each header long, carried into the cut unchanged. */
  versionBits: number[];
}

export interface Rd5Footer {
  creationTime: Uint8Array;
  tileIndexCrcs: number[];
  elevationType: number;
}

export function readHeader(bytes: Uint8Array): Rd5Header {
  const view = new DataView(bytes.buffer, bytes.byteOffset, HEADER_BYTES);
  const ends: number[] = [];
  const versionBits: number[] = [];
  for (let i = 0; i < 25; i++) {
    versionBits.push(view.getUint16(i * 8));
    ends.push(view.getUint16(i * 8 + 2) * 2 ** 32 + view.getUint32(i * 8 + 4));
  }
  return { bytes: bytes.subarray(0, HEADER_BYTES), ends, versionBits };
}

/** Called after the header checksum has verified, so damage is reported as a checksum error first. */
export function checkTileOrder(header: Rd5Header): void {
  let start = HEADER_BYTES;
  for (const end of header.ends) {
    if (end < start) throw new Rd5UnsupportedError("unsupported rd5: tile offsets go backwards");
    start = end;
  }
}

export const tileStart = (ends: number[], tile: number): number => (tile === 0 ? HEADER_BYTES : ends[tile - 1]);

/** How many bytes follow the last tile, or an error if there is no footer this cutter can read. */
export function footerLength(header: Rd5Header, fileSize: number): number {
  const length = fileSize - header.ends[24];
  if (length === 0) throw new Rd5UnsupportedError("unsupported rd5: no footer (old format)");
  if (length < FOOTER_MIN_BYTES) throw new Rd5ChecksumError(`rd5 footer too short: ${length} bytes`);
  if (length > 4096) throw new Rd5UnsupportedError(`unsupported rd5: ${length}-byte footer`);
  return length;
}

export function readFooter(bytes: Uint8Array, header: Rd5Header): Rd5Footer {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const stored = view.getInt32(8);
  const computed = brouterCrc(header.bytes);
  if (stored === computed) {
    throw new Rd5UnsupportedError("unsupported rd5: 80-cells-per-degree format, not 32");
  }
  if ((stored ^ FORMAT_MARKER) !== computed) throw new Rd5ChecksumError("rd5 header checksum error");
  const tileIndexCrcs: number[] = [];
  for (let i = 0; i < 25; i++) tileIndexCrcs.push(view.getInt32(12 + i * 4));
  // BRouter defaults to type 3 when the byte is absent (PhysicalFile.java:27, :142-144).
  const elevationType = bytes.byteLength > FOOTER_MIN_BYTES ? bytes[FOOTER_MIN_BYTES] : 3;
  return { creationTime: bytes.slice(0, 8), tileIndexCrcs, elevationType };
}

/** A tile's 1,024 cell ends, after checking the index against its footer checksum. */
export function readTileIndex(bytes: Uint8Array, expectedCrc: number, tile: number): Int32Array {
  if (brouterCrc(bytes) !== expectedCrc) throw new Rd5ChecksumError(`rd5 tile ${tile} index checksum error`);
  const view = new DataView(bytes.buffer, bytes.byteOffset, TILE_INDEX_BYTES);
  const ends = new Int32Array(CELLS_PER_TILE);
  let previous = TILE_INDEX_BYTES;
  for (let i = 0; i < CELLS_PER_TILE; i++) {
    ends[i] = view.getInt32(i * 4);
    if (ends[i] < previous) throw new Rd5ChecksumError(`rd5 tile ${tile} index goes backwards`);
    previous = ends[i];
  }
  return ends;
}

export function writeTileIndex(cellEnds: Int32Array): Uint8Array {
  const out = new Uint8Array(TILE_INDEX_BYTES);
  const view = new DataView(out.buffer);
  for (let i = 0; i < CELLS_PER_TILE; i++) view.setInt32(i * 4, cellEnds[i]);
  return out;
}

export function writeHeader(ends: number[], versionBits: number[]): Uint8Array {
  const out = new Uint8Array(HEADER_BYTES);
  const view = new DataView(out.buffer);
  for (let i = 0; i < 25; i++) {
    view.setUint16(i * 8, versionBits[i]);
    view.setUint16(i * 8 + 2, Math.floor(ends[i] / 2 ** 32));
    view.setUint32(i * 8 + 4, ends[i] % 2 ** 32);
  }
  return out;
}

export function writeFooter(header: Uint8Array, footer: Rd5Footer): Uint8Array {
  const out = new Uint8Array(FOOTER_BYTES);
  const view = new DataView(out.buffer);
  out.set(footer.creationTime, 0);
  view.setInt32(8, brouterCrc(header) ^ FORMAT_MARKER);
  for (let i = 0; i < 25; i++) view.setInt32(12 + i * 4, footer.tileIndexCrcs[i]);
  out[FOOTER_MIN_BYTES] = footer.elevationType;
  return out;
}
