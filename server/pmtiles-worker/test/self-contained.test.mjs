// The Worker folder stands alone (dispatch 2026-11-01-coder-11; the owner: "may as well get it modular to work around these
// situations in the future"). Cloudflare Workers Builds deploys main's server/pmtiles-worker, and a Worker release is a
// worker/* pull request that makes main's folder equal pre-main's, with nothing outside it. So nothing in the folder may
// read a file outside it: main's other folders are an older release's. The checks that the Worker's bundled copies equal
// the rest of the repository's live in test/cross-folder.test.mjs, the one file allowed to look outside, and they run only
// where the rest of the repository is there.
//
// Run: node --test test/self-contained.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const folder = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
/** The one file allowed to read outside the folder. */
const CROSS_FOLDER = path.join(folder, "test", "cross-folder.test.mjs");

function sources(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return ["node_modules", "dist", "fixtures"].includes(e.name) ? [] : sources(p);
    return /\.(mjs|js|ts)$/.test(e.name) ? [p] : [];
  });
}

/** Each relative path a source names in a quoted string (one or more ../ steps), with the line it is on. */
function relativePaths(file) {
  return fs.readFileSync(file, "utf8").split("\n").flatMap((line, i) =>
    [...line.matchAll(/["'`]((?:\.\.\/)+[^"'`]*)["'`]/g)].map((m) => ({ ref: m[1], line: i + 1 })));
}

test("no source or test in the Worker folder reads a file outside it, except test/cross-folder.test.mjs", () => {
  const outside = [];
  for (const file of sources(folder)) {
    if (file === CROSS_FOLDER) continue;
    for (const { ref, line } of relativePaths(file)) {
      // A test reads its paths through here(p) = new URL(p, import.meta.url), relative to its own file.
      const resolved = path.resolve(path.dirname(file), ref);
      if (!resolved.startsWith(folder + path.sep)) outside.push(`${path.relative(folder, file)}:${line} ${ref}`);
    }
  }
  assert.deepEqual(outside, []);
});
