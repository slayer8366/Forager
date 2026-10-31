// Builds Trail Map's sprite v5 (dispatch 2026-10-22-03 Amendment 1, RECORD -1561): Protomaps' v4 light sprite,
// unchanged, with the eight ruled cut-outs added in one row below it. Run after ./fetch-assets.sh:
//   node tools/build-sprite-v5.mjs          writes assets/sprites/v5/light{,@2x}.{json,png} and prints their sha256
//   node tools/build-sprite-v5.mjs --check  rebuilds in memory and compares with the files already in assets/sprites/v5
//
// With the argument v6 (dispatch 2026-10-31-coder-07 Part 3; the owner: "New icon set v6 (Recommended)") it builds
// sprite v6 the same way: sprite v5 as built, unchanged, with the three orchard, vineyard and nursery patterns
// (SPRITE_V6_ITEMS) in one row below it, into assets/sprites/v6. Every v5 image keeps its index entry and pixels.
//   node tools/build-sprite-v5.mjs v6 [--check]
//
// Reproducible means pixels and the index, not bytes: a PNG's bytes depend on the zlib of the Node that wrote it, so
// --check decodes both sides and compares RGBA and JSON. Every build also re-reads what it wrote and confirms that each
// v4 icon is pixel for pixel v4's and each new image pixel for pixel its cut-out.
//
// Day images only. The night copies ship in the app (app/src/main/assets/night-sprite/v5, dispatch 2026-10-22-06
// Amendment 1), generated from these day sheets by the app's night rules and held to them by NightSpriteTest.
// The cut-outs are Forager's own art (the owner, 2026-10-22: "As the project's own art"); v4's icons keep their MIT
// credit (licences/sprites-tangrams-icons-MIT.md).
import fs from "node:fs";
import crypto from "node:crypto";
import { PNG } from "pngjs";
import { SPRITE_V5_ITEMS } from "../overrides/forager-character.mjs";
import { SPRITE_V6_ITEMS } from "../overrides/forager-fill-in.mjs";

const here = (path) => new URL(`../${path}`, import.meta.url);
/** Each sprite this builds: the sheet it starts from, unchanged, and the images it adds below it. */
const BUILDS = {
  v5: { base: "v4", items: SPRITE_V5_ITEMS, itemsDir: "assets/sprite-v5-items/day" },
  v6: { base: "v5", items: SPRITE_V6_ITEMS, itemsDir: "assets/sprite-v6-items/day" },
};
const VERSION = process.argv.includes("v6") ? "v6" : "v5";
const BUILD = BUILDS[VERSION];
const V4 = `assets/sprites/${BUILD.base}/light`; // the base sheet: v4 for v5, v5 for v6
const V5 = `assets/sprites/${VERSION}/light`; // the sheet built
const ITEMS = BUILD.itemsDir;
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
  const items = BUILD.items.map(({ name, size }) => {
    if (name in index) throw new Error(`sprite ${VERSION}: ${name} is already a ${BUILD.base} image`);
    const png = readPng(`${ITEMS}/${name}${suffix}.png`);
    if (png.width !== size * scale || png.height !== size * scale) {
      throw new Error(`sprite ${VERSION}: ${name}${suffix} is ${png.width}x${png.height}, expected ${size * scale}`);
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

/** Each base image (v4's for v5, v5's for v6) is the base's, and each new image is its cut-out, in [sheet] as decoded. */
function verify(sheet, index, base, items, suffix) {
  const v4 = JSON.parse(fs.readFileSync(here(`${V4}${suffix}.json`), "utf8"));
  for (const [name, e] of Object.entries(v4)) {
    if (JSON.stringify(index[name]) !== JSON.stringify(e)) throw new Error(`sprite ${VERSION}${suffix}: ${name}'s index entry changed`);
    if (!samePixels(sheet, e.x, e.y, base, e.x, e.y, e.width, e.height)) throw new Error(`sprite ${VERSION}${suffix}: ${name}'s pixels changed`);
  }
  for (const { name, png } of items) {
    const e = index[name];
    if (!samePixels(sheet, e.x, e.y, png, 0, 0, png.width, png.height)) throw new Error(`sprite ${VERSION}${suffix}: ${name} differs from its cut-out`);
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
  fs.mkdirSync(here(`assets/sprites/${VERSION}`), { recursive: true });
  fs.writeFileSync(here(`${V5}${suffix}.png`), PNG.sync.write(sheet));
  fs.writeFileSync(here(`${V5}${suffix}.json`), JSON.stringify(index, null, 2) + "\n");
  const written = readPng(`${V5}${suffix}.png`);
  const kept = verify(written, JSON.parse(fs.readFileSync(here(`${V5}${suffix}.json`), "utf8")), base, items, suffix);
  for (const ext of ["json", "png"]) {
    const sum = crypto.createHash("sha256").update(fs.readFileSync(here(`${V5}${suffix}.${ext}`))).digest("hex");
    console.log(`${sum}  sprites/${VERSION}/light${suffix}.${ext}`);
  }
  console.log(`light${suffix}: ${sheet.width}x${sheet.height}, ${kept} ${BUILD.base} images unchanged + ${items.length} new = ${Object.keys(index).length}`);
}
if (problems.length) {
  console.log("PROBLEMS:\n  " + problems.join("\n  "));
  process.exit(1);
}
if (check) console.log(`sprite ${VERSION} matches a fresh build, pixel for pixel, at both densities`);
