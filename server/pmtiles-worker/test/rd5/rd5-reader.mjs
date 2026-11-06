// An independent reader for BRouter .rd5 squares, used only by the tests to inspect what the cutter wrote.
// It is written from BRouter v1.7.10's own reader, not from src/rd5/, so a mistake in the cutter's format
// code is not repeated here and passed by its own mirror image. Line references are to v1.7.10:
//   header   PhysicalFile.java:96-107   25 big-endian longs; low 48 bits = absolute end of tile i
//   footer   PhysicalFile.java:109-145  timestamp, Crc32(header) ^ 2, 25 tile-index checksums, elevation byte
//   tile     OsmFile.java:56-76         4,096-byte index of 1,024 cumulative cell ends, relative to the tile
//   cell     OsmFile.java:101-155       bytes ending in Crc32(bytes) ^ 2
// The checksum is BRouter's Crc32 (Crc32.java:9-16): standard table, start 0xFFFFFFFF, no final inversion,
// which is the bitwise NOT of zlib's CRC-32. zlib's is used here, so the cutter's own table is not trusted.
import { crc32 } from "node:zlib";

const HEADER = 200;
const INDEX = 4096;

export const brouterCrc = (bytes) => (~crc32(bytes)) | 0;

export function readRd5(buf) {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const ends = [];
  const versions = [];
  for (let i = 0; i < 25; i++) {
    versions.push(view.getUint16(i * 8));
    ends.push(view.getUint16(i * 8 + 2) * 2 ** 32 + view.getUint32(i * 8 + 4));
  }
  const footerAt = ends[24];
  const footerCrc = view.getInt32(footerAt + 8);
  const headerCrc = brouterCrc(buf.subarray(0, HEADER));
  const tileCrcs = [];
  for (let i = 0; i < 25; i++) tileCrcs.push(view.getInt32(footerAt + 12 + i * 4));
  const footer = {
    creationTime: buf.subarray(footerAt, footerAt + 8),
    headerCrcOk: (footerCrc ^ 2) === headerCrc,
    tileCrcs,
    elevationType: buf.byteLength > footerAt + 112 ? buf[footerAt + 112] : undefined,
    length: buf.byteLength - footerAt,
  };
  const tiles = [];
  for (let t = 0; t < 25; t++) {
    const start = t === 0 ? HEADER : ends[t - 1];
    if (start === ends[t]) {
      tiles.push(null);
      continue;
    }
    const index = buf.subarray(start, start + INDEX);
    const cellEnds = [];
    for (let i = 0; i < 1024; i++) cellEnds.push(view.getInt32(start + i * 4));
    const cells = [];
    let prev = INDEX;
    for (let i = 0; i < 1024; i++) {
      const bytes = buf.subarray(start + prev, start + cellEnds[i]);
      const cellCrcOk = bytes.length === 0 ||
        (brouterCrc(bytes.subarray(0, bytes.length - 4)) ^ 2) ===
          new DataView(bytes.buffer, bytes.byteOffset + bytes.length - 4, 4).getInt32(0);
      cells.push({ bytes, cellCrcOk });
      prev = cellEnds[i];
    }
    tiles.push({
      start,
      end: ends[t],
      indexCrcOk: brouterCrc(index) === tileCrcs[t],
      lastCellEndsTile: start + prev === ends[t],
      cells,
    });
  }
  return { versions, ends, footer, tiles };
}

// Where a cell lives, from BRouter's own arithmetic: ilon = (lon + 180) * 1e6, a cell is 31,250 micro-degrees
// (OsmFile.java:48), a tile is lonMod5 * 5 + latMod5 (OsmFile.java:40-42), a cell in a tile is
// latRow * 32 + lonCol (OsmFile.java:120).
export function cellOf(globalCol, globalRow) {
  const lonDeg = Math.floor(globalCol / 32);
  const latDeg = Math.floor(globalRow / 32);
  return { tile: (lonDeg % 5) * 5 + (latDeg % 5), cell: (globalRow % 32) * 32 + (globalCol % 32) };
}

export const globalCol = (lon) => Math.floor(Math.round((lon + 180) * 1e6) / 31250);
export const globalRow = (lat) => Math.floor(Math.round((lat + 90) * 1e6) / 31250);
