// The cut file, read byte by byte (dispatch 2026-10-21-07). These tests need no router: they read the cut
// with an independent reader (rd5-reader.mjs) and with BRouter's own integrity check, and compare it with
// the fixture square it was cut from.
//
//   BROUTER_HOME=<dir from fetch-brouter.sh> node --test test/rd5/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { cutRd5 } from "../../src/rd5/cut.ts";
import { integrityCheck } from "./brouter-cli.mjs";
import { SQUARE, ROUTES, bytesReader } from "./fixture.mjs";
import { readRd5, cellOf, globalCol, globalRow } from "./rd5-reader.mjs";

const RECT = ROUTES.rectangle;

// The cells a rectangle plus a ring of `margin` cells covers, as "tile:cell" keys, worked out here from
// BRouter's arithmetic rather than taken from the cutter.
function expectedCells(rect, margin) {
  const keys = new Set();
  for (let col = globalCol(rect.west) - margin; col <= globalCol(rect.east) + margin; col++) {
    for (let row = globalRow(rect.south) - margin; row <= globalRow(rect.north) + margin; row++) {
      const { tile, cell } = cellOf(col, row);
      keys.add(`${tile}:${cell}`);
    }
  }
  return keys;
}

const cut = (margin) => cutRd5(bytesReader(SQUARE), ROUTES.square, RECT, margin);

function assertCellsMatch(source, result, wanted) {
  let kept = 0;
  for (let t = 0; t < 25; t++) {
    for (let c = 0; c < 1024; c++) {
      const from = source.tiles[t] ? source.tiles[t].cells[c].bytes : new Uint8Array(0);
      const got = result.tiles[t] ? result.tiles[t].cells[c].bytes : new Uint8Array(0);
      if (wanted.has(`${t}:${c}`)) {
        assert.deepEqual(Buffer.from(got), Buffer.from(from), `cell ${t}:${c} is the source's bytes`);
        if (from.length > 0) kept++;
      } else {
        assert.equal(got.length, 0, `cell ${t}:${c}, outside the rectangle and margin, is empty`);
      }
    }
  }
  return kept;
}

test("the cut holds exactly the rectangle's cells plus a one-cell ring, each byte for byte", async () => {
  const source = readRd5(SQUARE);
  const result = readRd5(await cut());
  const wanted = expectedCells(RECT, 1);
  const kept = assertCellsMatch(source, result, wanted);
  const sourceCells = source.tiles.flatMap((t) => (t ? t.cells.filter((c) => c.bytes.length > 0) : [])).length;
  assert.ok(kept > 0, "the rectangle holds data in this fixture");
  assert.ok(kept < sourceCells, `the cut drops cells: kept ${kept} of ${sourceCells}`);
});

test("the margin is a parameter: margin 0 keeps only the rectangle's own cells", async () => {
  const result = readRd5(await cut(0));
  assertCellsMatch(readRd5(SQUARE), result, expectedCells(RECT, 0));
});

test("the cut's header, tile indexes and cells carry checksums BRouter accepts", async () => {
  const source = readRd5(SQUARE);
  const result = readRd5(await cut());
  assert.deepEqual(result.versions, source.versions, "lookup version bits are carried over unchanged");
  assert.ok(result.footer.headerCrcOk, "header checksum, with the 32-division marker");
  assert.equal(result.footer.length, 113, "footer: timestamp, header checksum, 25 tile checksums, elevation");
  assert.deepEqual(Buffer.from(result.footer.creationTime), Buffer.from(source.footer.creationTime));
  assert.equal(result.footer.elevationType, source.footer.elevationType);
  let tilesWithData = 0;
  for (let t = 0; t < 25; t++) {
    const tile = result.tiles[t];
    if (!tile) {
      assert.equal(result.footer.tileCrcs[t], 0, `empty tile ${t} has a zero checksum, as WayLinker writes`);
      continue;
    }
    tilesWithData++;
    assert.ok(tile.indexCrcOk, `tile ${t} index checksum`);
    assert.ok(tile.lastCellEndsTile, `tile ${t}'s last cell ends where the header says the tile ends`);
    assert.ok(tile.cells.every((c) => c.cellCrcOk), `tile ${t} cells keep valid checksums`);
  }
  assert.equal(tilesWithData, 2, "the rectangle crosses -74, so the cut has two one-degree tiles");
});

test("BRouter's own integrity check decodes every cell of the cut", async () => {
  const bytes = await cut();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "rd5-check-"));
  const file = path.join(dir, `${ROUTES.square}.rd5`);
  fs.writeFileSync(file, bytes);
  const { status, output } = integrityCheck(file);
  fs.rmSync(dir, { recursive: true, force: true });
  assert.equal(status, 0);
  assert.doesNotMatch(output, /Exception|checkum error|checksum error/);
  const nonEmpty = readRd5(bytes).tiles.flatMap((t) => (t ? t.cells.filter((c) => c.bytes.length > 0) : []));
  const decoded = output.match(/\*\*\* decoding cache of size/g) ?? [];
  assert.equal(decoded.length, nonEmpty.length, "BRouter decoded as many cells as the cut holds");
});

test("the cut is smaller than the square and its size is reported", async (t) => {
  const bytes = await cut();
  t.diagnostic(`fixture square ${SQUARE.byteLength} bytes; cut ${bytes.byteLength} bytes`);
  assert.ok(bytes.byteLength < SQUARE.byteLength);
});

test("an old-format square (no 32-division marker) is refused as unsupported", async () => {
  const old = Buffer.from(SQUARE);
  const view = new DataView(old.buffer, old.byteOffset, old.byteLength);
  const footerAt = readRd5(SQUARE).ends[24];
  view.setInt32(footerAt + 8, view.getInt32(footerAt + 8) ^ 2);
  await assert.rejects(cutRd5(bytesReader(old), ROUTES.square, RECT), /unsupported/i);
});

test("a square with no footer is refused as unsupported", async () => {
  const bare = SQUARE.subarray(0, readRd5(SQUARE).ends[24]);
  await assert.rejects(cutRd5(bytesReader(bare), ROUTES.square, RECT), /unsupported/i);
});

test("a damaged header is refused, not cut", async () => {
  const bad = Buffer.from(SQUARE);
  bad[15] ^= 0x01; // inside tile 1's end offset
  await assert.rejects(cutRd5(bytesReader(bad), ROUTES.square, RECT), /checksum/i);
});

test("a damaged tile index is refused, not cut", async () => {
  const bad = Buffer.from(SQUARE);
  const tile1Start = readRd5(SQUARE).ends[0];
  bad[tile1Start + 4 * 700 + 3] ^= 0x01;
  await assert.rejects(cutRd5(bytesReader(bad), ROUTES.square, RECT), /checksum/i);
});

test("a rectangle that misses the square, or a malformed square name, is refused", async () => {
  const away = { south: 10, west: 10, north: 10.1, east: 10.1 };
  await assert.rejects(cutRd5(bytesReader(SQUARE), ROUTES.square, away), /does not overlap/);
  await assert.rejects(cutRd5(bytesReader(SQUARE), "W75N40", RECT), /square name/);
  await assert.rejects(cutRd5(bytesReader(SQUARE), "W74_N40", RECT), /square name/);
  await assert.rejects(cutRd5(bytesReader(SQUARE), ROUTES.square, RECT, -1), /margin/);
});
