# Review 2026-11-01: the Worker's overflow and advertised maxzoom for the Street archive only

Sorrel Tally, the independent reviewer (Fable), for Aspen's dispatch "sorrel-worker-overflow" (the hill-shading rollout,
`docs/design/2026-10-31-global-coverage.md`, last sections). Started about 10:50 PST, finished about 11:10 PST on
2026-11-01. Quick by design: the weekly rerun is on the Pi, so only the Worker's own suite ran here. Each claim is
marked **read**, **computed** or **inferred**. No deploy. Read-only GETs against the live Worker, with one caveat
disclosed in section 1.

## Baseline (read)

| Item | Found |
|---|---|
| `worker-overflow-street-only` | `f8fa10db2`, two commits (`0e3306d3d` tests first, `f8fa10db2` the fix) on `pre-main` `9ef5c9385`, which is an ancestor |
| The delta | `index.ts` +10/−2, the test file +26, the README two rows; nothing else |
| Live Worker, probed | `/us.json` maxzoom 15; `/style/street-v3.json`, `v2`, `v1`, `offline.json` each JSON-equal to `pre-main`'s bundled copy; `/reference-trails-manifest.json` 200; sprites v4 and v5 200 with v5's recorded sums; `/sprites/v6/light.json` 404 |

So the live Worker is `pre-main`'s code without #453's v6 route (inferred from what it serves): a `pre-main` deploy
adds the v6 route, which the owner has allowed, and this change, and nothing else. `git diff origin/pre-main
origin/worker-overflow-street-only -- server/pmtiles-worker/src` is the 10 lines below; against `main` the diff is
larger only because `main` is behind `pre-main` (the v3 style, the manifest route, v6), all of which is already live
except v6.

## 1. What the change does, archive by archive (read, with one live probe)

The code: in the TileJSON route, `advertisedMaxZoom = name === STREET_ARCHIVE_NAME ? max(declared, 15) : declared`;
in the tile route, before the overflow block, `if (z > header.maxZoom && name !== STREET_ARCHIVE_NAME) return 204`.
The `z < minZoom` case, the world fill, the minLat guard and the 404 for z > 15 are untouched.

- **`us`:** unchanged. Advertises 15, z15 goes to the overflow (Protomaps live build, cached into R2), the south-bound
  guard and the world fill as before. The branch's own test pins both.
- **`reference-trails`:** its header's maxzoom is 15 (the live TileJSON says 15 and the style's source says 15), so no
  tile is above it; z15 within range answers the archive (204 for an absent tile today, probed). Before and after,
  the overflow could never be reached for it, since 15 is the overflow's own ceiling.
- **`world-south`:** built to z8 (`index.ts:291`, README). Live today, `/world-south.json` advertises **15** and
  `/world-south/9/81/183.mvt` answered **200, 219,298 B, `X-Forager-Origin: r2`**: that is the overflow serving a
  Protomaps vector tile for a z9 the world archive does not hold, from its R2 cache under `overflow/world-south/…`.
  The bug the branch fixes is live and reachable today for this archive. With the branch, `/world-south.json` would
  say 8 and that request 204. **Caveat:** my first GET of that tile may itself have been the request that wrote the
  object into R2 (the overflow writes on a miss; the second GET was an edge-cache HIT and tells me nothing about the
  first). If `overflow/world-south/9/81/183.mvt` is new in R2 today, that was my probe, and Aspen may delete it. I
  made no other request above an archive's maxzoom.
- **A future `relief.pmtiles` (z5 to 11, raster-dem PNG):** advertises 11; a `/relief/12/…png` is 204 at once, with
  no Protomaps read and no R2 write. Before the branch it would have been a vector MVT written into R2 under
  `overflow/relief/…` and handed to a raster-dem source. The test with `other.pmtiles` (maxzoom 14) stands in for it
  and asserts no fetch, no overflow read, no put.

## 2. Tests and revert checks (computed)

The suite needs wrangler's dry-run bundle first (`dist/index.js`, as CI builds it); with that built, the branch runs
**60 tests, 60 pass**. Two reverts of my own, each removing half the change:

- the TileJSON half reverted (advertise 15 for every archive): exactly one failure, "a non-Street archive's TileJSON
  advertises its own maxzoom, not the overflow's 15";
- the 204 branch removed (overflow for every archive): exactly one failure, "a non-Street archive's tile above its
  maxzoom is 204, with no Protomaps read and no R2 overflow read or write".

So the two new tests are independent and each guards its own half; the third new test pins `us` unchanged.

## 3. A `pre-main` deploy against today's Worker (read)

What would change live: the v6 sprite route (allowed) and this change. What would not: `street-v3`, `v2`, `v1`,
`offline.json` (JSON-equal to the bundled copies that `pre-main` carries and the live Worker already serves), the
reference-trails manifest route, sprites v4 and v5 (the same R2 keys, the same cache control), the tile route for
`us` in every branch. The only live behaviours that move are the ones the change names: non-Street archives'
advertised maxzoom and their above-maxzoom tiles, which today only `world-south` exercises, and there for the better.
The branch should be merged into `pre-main` and deployed from there (as the v6 review asked), not from the branch tip.

## Verdict

### Blocking

None.

### Should-fix

None in the code. One operational item: check R2 for `overflow/world-south/` objects (my probe may have added one;
earlier clients may have added others since the world fill shipped) and remove them, since nothing should ever read
them again once this is deployed.

### Notes

- The suite's `dist/index.js` prerequisite is CI's "Build the bundle (wrangler dry run)" step; a reader running
  `npm test` alone gets two "not ok" lines that look like failures and are not.
- The linked `node_modules` on this Pi differ from the branch's lockfile; wrangler's dry run and the suite ran
  anyway. CI's `npm ci` is the record.

**Is `worker-overflow-street-only` correct, and does a `pre-main` deploy change nothing else live?** Yes to both:
`us` is unchanged, every other archive advertises its own maxzoom and answers 204 above it with no overflow read or
write, the two halves are each pinned by a test my reverts confirm, and the only other live change in a `pre-main`
deploy is the allowed v6 route. The deploy is the owner's go.
