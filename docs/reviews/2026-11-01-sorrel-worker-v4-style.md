# Review: PR #460 `worker-street-v4-style` (the Worker's `/style/street-v4.json`)

Sorrel Tally, 2026-11-01 (evening, PST). Dispatch from Aspen. Read-only: no merge, deploy, R2 write or GitHub comment. Each claim is marked **read** (from the diff or a file), **computed** (my own run or script) or **inferred**.

## 1. Baseline (confirmed before anything else)

- PR #460 head 83cca8f58, base `pre-main`, open, mergeable (`CLEAN`); two commits, ea0922071 (tests first, failing) then 83cca8f58. **read**
- `origin/pre-main` is 5679eabac and is an ancestor of the head. **computed**
- Seven files changed: `map-style/generate-street.mjs` (3), `map-style/tools/check-street-night.mjs` (14), `server/pmtiles-worker/README.md` (1), `server/pmtiles-worker/src/index.ts` (10), the bundled `server/pmtiles-worker/src/street-style-v4.json` (new, 17,009 lines), `test/cross-folder.test.mjs` (7), `test/worker.test.mjs` (19). `map-style/forager-street-v4.json` itself is not touched. **computed**
- Head CI run 41386435069: all six jobs green, including "Tile Worker serves what its tests say". **read**

## 2. Prediction (written before running)

1. The Worker suite passes on the head once the dry-run bundle exists.
2. `check-street-night.mjs v4` now reports "bundled copy identical" and finds no problem; the no-argument (v2) run is unchanged.
3. `npm run generate:street` on the head reproduces every committed file byte for byte (the bundled copy is generated, not hand-copied).
4. Removing the route from `index.ts` fails exactly one test, the new v4 route test; the cross-folder identity test does not notice (it compares files, not routes).
5. Reverting only `toWorker: true` is invisible until v4 next changes; then the stale bundled copy is caught by the checker (Pi) and the cross-folder test (CI).

## 3. The dispatch's questions, in order

**(a) Is the Worker's bundled `street-style-v4.json` byte-identical to map-style's v4?** Yes. sha256 of both at the head: `6ce7271ed1d0da0b…`. v3's pair is likewise identical (`e2a259820bd1dda6…`) and untouched by the PR. **computed**

Regeneration: `npm run generate:street` on the head (map-style assets and node_modules symlinked from an earlier worktree) rewrote v3 and v4 and left `git status` empty: the committed bundled copy is exactly what the generator writes. **computed**

**(b) Are v3 and the other routes unchanged?** The `index.ts` delta is an import, a comment block and one new `if (url.pathname === "/style/street-v4.json")` branch placed after v3's, before the manifest path; no existing line is modified. The bundled v4 is served through the existing `streetStyleResponse` (JSON re-serialised, `Cache-Control: public, max-age=86400`, CORS from `resolveAllowedOrigin`) exactly like v1 to v3. **read**

**(c) Tests, bundle first.** `npm run build` (wrangler dry run, 1,409.69 KiB, 73.23 KiB gzipped; `dist/index.js` carries `"Forager Street v4"` once), then `npm test`:

| run | tests | pass | fail |
|---|---|---|---|
| head 83cca8f58 | 71 | 71 | 0 |

The new route test checks status 200, `application/json`, body equal to the bundled source re-serialised, `name`, the v6 sprite URL, the `dem` tile template, `protomaps.url`, the `hillshade` and `landuse_orchards_pattern` layers present, v3 still served, and no outbound fetch. The cross-folder test adds `forager-street-v4.json` to its NEEDED list and asserts the sha256 pair. **read / computed**

Checker: `node tools/check-street-night.mjs v4` → "street style v4 valid, bundled copy identical, v1 unchanged, day and night contrast and the night limits held" (closest margins unchanged from my v4 review: night water on buildings 1.08:1 against 1.05, night tree glyph on forest 1.42:1 against 1.35). The no-argument run still passes with the same message for v2. **computed**

**(d) Revert check.** Mine, not the author's:

- *Route removed* (the three `if` lines and the import deleted, bundle rebuilt): 70 pass, 1 fail, the new route test only (`worker.test.mjs:472`). Prediction 4 held. **computed**
- *`toWorker: false` restored, regenerate*: the generator reports "map-style only" and leaves the bundled copy in place, so the checker still says "identical" and the suite stays 71/71. Then, with the flag false and a one-character change to v4's `dem` attribution, regeneration updates map-style's v4 and preview but not the bundled copy: the checker reports "server/pmtiles-worker/src/street-style-v4.json differs from forager-street-v4.json: run npm run generate:street" and the cross-folder test fails (70/1). Prediction 5 held. **computed**
- Tree restored with `git checkout -- .` after each; clean. **computed**

Note from the first attempt: I ran the flag revert with a bundle still built from the route-removed `index.ts`, which produced a spurious route failure. Rebuilt and redone; the figures above are from the clean sequence.

**(e) Live effect once released.** On release the only new behaviour is the `/style/street-v4.json` route. Its sprite is `/sprites/v6/light` (live on R2, per dispatch 2026-11-01 sprite v6). Its `dem` source points at `/relief/{z}/{x}/{y}.png`, z5–11, through the generic tile path (`tilePath` accepts any extension; the content type comes from the archive's header, `image/png` for a PNG archive), so until `relief.pmtiles` is uploaded each relief tile answers the generic path's 404, which MapLibre treats as an empty raster-dem tile and the hillshade draws nothing. No other route's code or data is touched. **read / inferred** (the 404-as-empty behaviour is MapLibre's documented handling; I did not render against the live Worker, and nothing in this PR is live yet.)

## 4. Scoring the prediction

All five held. Prediction 5 needed the second step (a v4 edit) to show the guard; the flag alone is unguarded, which is harmless since a false flag with an unchanged v4 serves the right bytes.

## 5. Verdict

**Blocking:** none.

**Should fix:** none.

**Notes:**
- The `cross-folder.test.mjs` NEEDED list now includes `forager-street-v4.json`, so on `main` (whose `map-style/` holds only `.gitignore`, `README.md`, `fetch-assets.sh`) the whole cross-folder file skips as before; the v2 frozen sums are still asserted inside `worker.test.mjs`. No change in main's coverage. **read**
- The relief archive's content type on the generic path depends on `relief.pmtiles` being written with a PNG tile type in its header; worth a one-line check when that archive is built (step beyond this PR).
- Release to `main` afterwards: the worker/* guard (`check-release.sh`) will see the Worker folder differ from main by these seven files' Worker part (`server/pmtiles-worker`: 7 files, +17,078/−5 against main); I will confirm that release when dispatched.

**Merge?** Yes: mergeable into `pre-main` as it stands, on the owner's go.
