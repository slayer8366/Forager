// The fixture square and its routes (test/fixtures/rd5/, see README.md there), and a cut of it on disk.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

import { cutRd5 } from "../../src/rd5/cut.ts";

const here = (p) => new URL(p, import.meta.url);

export const FIXTURE_DIR = path.dirname(new URL("../fixtures/rd5/W75_N40.rd5", import.meta.url).pathname);
export const SQUARE = fs.readFileSync(here("../fixtures/rd5/W75_N40.rd5"));
export const ROUTES = JSON.parse(fs.readFileSync(here("../fixtures/rd5/routes.json"), "utf8"));

// A RangeReader over bytes in memory; the Worker's will be over R2. Records every read, so a test can see
// the cutter asked only for what it needed.
export function bytesReader(bytes) {
  const reads = [];
  return {
    size: bytes.byteLength,
    reads,
    async read(offset, length) {
      if (offset < 0 || length < 0 || offset + length > bytes.byteLength) {
        throw new RangeError(`read past the end: ${offset}+${length} of ${bytes.byteLength}`);
      }
      reads.push([offset, length]);
      return bytes.subarray(offset, offset + length);
    },
  };
}

// Cuts the fixture to the routes' rectangle and writes it, named for its square, into a fresh directory
// BRouter can use as its segment directory.
export async function cutToDir(marginCells) {
  const bytes = await cutRd5(bytesReader(SQUARE), ROUTES.square, ROUTES.rectangle, marginCells);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "rd5-cut-"));
  fs.writeFileSync(path.join(dir, `${ROUTES.square}.rd5`), bytes);
  return { dir, bytes };
}
