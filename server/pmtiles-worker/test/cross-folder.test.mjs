// The Worker's copies against the rest of the repository (dispatch 2026-11-01-coder-11): the one test file allowed to
// read outside server/pmtiles-worker (test/self-contained.test.mjs). On pre-main, where map-style is the same release as
// the Worker, these always run. On main, a Worker release brings only the folder, and main's map-style is an older
// release's, so each check is skipped there with the reason, rather than failing on a file the release did not bring.
//
// Run: node --test test/cross-folder.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import fs from "node:fs";
import * as copy from "./reference-names.mjs";

const here = (p) => new URL(p, import.meta.url);
const MAP_STYLE = here("../../../map-style/");
/** The map-style files these checks read; any one missing means this checkout is not one release, and they skip. */
const NEEDED = ["overrides/forager-reference.mjs", "forager-street-v2.json", "forager-street-v2.preview.json"];
const missing = NEEDED.filter((f) => !fs.existsSync(new URL(f, MAP_STYLE)));
const skip = missing.length ? `map-style lacks ${missing.join(", ")}: a folder-only Worker release on main; checked on pre-main` : false;
if (skip) console.log(`cross-folder checks skipped: ${skip}`);
const sha256 = (url) => crypto.createHash("sha256").update(fs.readFileSync(url)).digest("hex");

test("the folder's copy of the official-trails names equals map-style's, where they are written", { skip }, async () => {
  const written = await import(new URL("overrides/forager-reference.mjs", MAP_STYLE).href);
  assert.deepEqual(
    { REFERENCE_SOURCE_ID: copy.REFERENCE_SOURCE_ID, REFERENCE_ARCHIVE: copy.REFERENCE_ARCHIVE },
    { REFERENCE_SOURCE_ID: written.REFERENCE_SOURCE_ID, REFERENCE_ARCHIVE: written.REFERENCE_ARCHIVE },
  );
});

test("v2 is frozen: the app's and the preview's copies byte for byte what pre-main c91f34c49 held, as the Worker's is", { skip }, () => {
  assert.deepEqual(
    { app: sha256(new URL("forager-street-v2.json", MAP_STYLE)), preview: sha256(new URL("forager-street-v2.preview.json", MAP_STYLE)) },
    { app: "7547507bc2b7dbdae24e8b3735eeda569c0f903c068282259b1e51b4bcc7886d", preview: "d07ce0e343c1187e607f04e5bb455898776e9acaa41c9133430f459f98fdd039" },
  );
});
