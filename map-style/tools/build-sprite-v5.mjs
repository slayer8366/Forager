// Builds Trail Map's sprite v5 (dispatch 2026-10-22-03 Amendment 1, RECORD -1561): Protomaps' v4 light sprite,
// unchanged, with the eight ruled cut-outs added in one row below it. Run after ./fetch-assets.sh:
//   node tools/build-sprite-v5.mjs          writes assets/sprites/v5/light{,@2x}.{json,png} and prints their sha256
//   node tools/build-sprite-v5.mjs --check  rebuilds in memory and compares with the files already in assets/sprites/v5
//
// Reproducible means pixels and the index, not bytes: a PNG's bytes depend on the zlib of the Node that wrote it, so
// --check decodes both sides and compares RGBA and JSON. Every build also re-reads what it wrote and confirms that each
// v4 icon is pixel for pixel v4's and each new image pixel for pixel its cut-out.
//
// Day images only. Night is drawn by the app: the six icons and the tufts by the inversion every icon takes
// (`STREET_SPRITE_ICON_NAMES`), the tree glyph by its own colour (`STREET_NIGHT_PATTERN_COLOURS`).
// The cut-outs are Forager's own art (the owner, 2026-10-22: "As the project's own art"); v4's icons keep their MIT
// credit (licences/sprites-tangrams-icons-MIT.md).
import fs from "node:fs";
import crypto from "node:crypto";
import { PNG } from "pngjs";
import { SPRITE_V5_ITEMS } from "../overrides/forager-character.mjs";

const here = (path) => new URL(`../${path}`, import.meta.url);
const V4 = "assets/sprites/v4/light";
const V5 = "assets/sprites/v5/light";
const ITEMS = "assets/sprite-v5-items/day";
/** One empty pixel (at 1x) between the v4 sheet and the new row, and between the new images. */
const GAP = 1;

const readPng = (path) => PNG.sync.read(fs.readFileSync(here(path)));

function blit(dst, src, x, y) {
  for (let row = 0; row < src.height; row++) {
    const from = row * src.width * 4;
    src.data.copy(dst.data, ((y + row) * dst.width + x) * 4, from, from + src.width * 4);
  }
}

function samePixels(a, ax, ay, b, bx, by, width, height) {
  for (let row = 0; row < height; row++) {
    const ia = ((ay + row) * a.width + ax) * 4;
    const ib = ((by + row) * b.width + bx) * 4;
    if (!a.data.subarray(ia, ia + width * 4).equals(b.data.subarray(ib, ib + width * 4))) return false;
  }
  return true;
}

/** The v5 sheet and index for one density, in memory. */
export function buildDensity(suffix, scale) {
  const index = JSON.parse(fs.readFileSync(here(`${V4}${suffix}.json`), "utf8"));
  const base = readPng(`${V4}${suffix}.png`);
  const items = SPRITE_V5_ITEMS.map(({ name, size }) => {
    if (name in index) throw new Error(`sprite v5: ${name} is already a v4 icon`);
    const png = readPng(`${ITEMS}/${name}${suffix}.png`);
    if (png.width !== size * scale || png.height !== size * scale) {
      throw new Error(`sprite v5: ${name}${suffix} is ${png.width}x${png.height}, expected ${size * scale}`);
    }
    return { name, size, png };
  });
  const rowY = base.height + GAP * scale;
  const rowWidth = items.reduce((w, { size }) => w + (size + GAP) * scale, 0);
  const rowHeight = Math.max(...items.map(({ size }) => size)) * scale;
  const sheet = new PNG({ width: Math.max(base.width, rowWidth), height: rowY + rowHeight });
  sheet.data.fill(0);
  blit(sheet, base, 0, 0);
  let x = 0;
  for (const { name, size, png } of items) {
    blit(sheet, png, x, rowY);
    index[name] = { height: size * scale, pixelRatio: scale, width: size * scale, x, y: rowY };
    x += (size + GAP) * scale;
  }
  return { sheet, index, base, items };
}

/** Each v4 icon is v4's, and each new image is its cut-out, in [sheet] as decoded. */
function verify(sheet, index, base, items, suffix) {
  const v4 = JSON.parse(fs.readFileSync(here(`${V4}${suffix}.json`), "utf8"));
  for (const [name, e] of Object.entries(v4)) {
    if (JSON.stringify(index[name]) !== JSON.stringify(e)) throw new Error(`sprite v5${suffix}: ${name}'s index entry changed`);
    if (!samePixels(sheet, e.x, e.y, base, e.x, e.y, e.width, e.height)) throw new Error(`sprite v5${suffix}: ${name}'s pixels changed`);
  }
  for (const { name, png } of items) {
    const e = index[name];
    if (!samePixels(sheet, e.x, e.y, png, 0, 0, png.width, png.height)) throw new Error(`sprite v5${suffix}: ${name} differs from its cut-out`);
  }
  return Object.keys(v4).length;
}

const check = process.argv.includes("--check");
const problems = [];
for (const [suffix, scale] of [["", 1], ["@2x", 2]]) {
  const { sheet, index, base, items } = buildDensity(suffix, scale);
  if (check) {
    const onDisk = readPng(`${V5}${suffix}.png`);
    const onDiskIndex = JSON.parse(fs.readFileSync(here(`${V5}${suffix}.json`), "utf8"));
    if (onDisk.width !== sheet.width || onDisk.height !== sheet.height || !onDisk.data.equals(sheet.data)) {
      problems.push(`${V5}${suffix}.png: pixels differ from a fresh build`);
    }
    if (JSON.stringify(onDiskIndex) !== JSON.stringify(index)) problems.push(`${V5}${suffix}.json differs from a fresh build`);
    verify(onDisk, onDiskIndex, base, items, suffix);
    continue;
  }
  fs.mkdirSync(here("assets/sprites/v5"), { recursive: true });
  fs.writeFileSync(here(`${V5}${suffix}.png`), PNG.sync.write(sheet));
  fs.writeFileSync(here(`${V5}${suffix}.json`), JSON.stringify(index, null, 2) + "\n");
  const written = readPng(`${V5}${suffix}.png`);
  const kept = verify(written, JSON.parse(fs.readFileSync(here(`${V5}${suffix}.json`), "utf8")), base, items, suffix);
  for (const ext of ["json", "png"]) {
    const sum = crypto.createHash("sha256").update(fs.readFileSync(here(`${V5}${suffix}.${ext}`))).digest("hex");
    console.log(`${sum}  sprites/v5/light${suffix}.${ext}`);
  }
  console.log(`light${suffix}: ${sheet.width}x${sheet.height}, ${kept} v4 icons unchanged + ${items.length} new = ${Object.keys(index).length}`);
}
if (problems.length) {
  console.log("PROBLEMS:\n  " + problems.join("\n  "));
  process.exit(1);
}
if (check) console.log("sprite v5 matches a fresh build, pixel for pixel, at both densities");
