# Review 2026-10-31: sprite v6, the orchard, vineyard and nursery patterns

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-31-sorrel-sprite-v6.md` in
Forager-cartography. Started about 12:10 PDT, finished about 12:35 PDT on 2026-10-31. Times are Pacific. Each claim
is marked **read**, **computed** or **inferred**. No R2 or Worker call but two read-only GETs; nothing deployed; the
only download was the pinned art archive from GitHub, which `fetch-assets.sh` fetches anyway.

## Prediction (formed on reading the diffs, before the builds and the pixel work)

1. v6 is v5 plus three: every v5 index entry and pixel unchanged, the three in one row below; my own build from the
   pinned art will match the committed sheets at least pixel for pixel.
2. The pattern layer matches the spec; the `""` fallback is unreachable because the layer's filter is the same
   `kind_detail` set the match enumerates, and a reached `""` draws nothing.
3. The app's choice is by the style's own `sprite` string and the cache key carries the folder, so v5 and v6 cannot mix;
   the night v6 sheets are v5's plus the three inverted to `#829a50`.
4. The Worker change is the regex only; the thing to watch is not the code but where it is deployed from, since the
   branch sits on an older base than `pre-main`.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| The art | Forager-cartography `2bdd3342` | nine files: six PNGs, `SHA256SUMS`, `make-patterns.py`, a README; the sums match `sprite-v6-items.sha256` line for line; `nursery_dots@2x` and `orchard_dots` share `5f63b3b7…` |
| Style and app | `street-v4-sprite-v6` `b1a373f45` on `street-v4` `11c31a9e0` | three commits on `11c31a9e0`; full CI 41229557604 success, 5,977 tests, 0 failures (JUnit XML) |
| Worker | `worker-sprite-v6` `220094639` | two commits on **`b493cffeb`**, not on `pre-main` (`206464058`); CI 41228391862 success |
| Selector revert | `eed189bd1`, 41229594666 | one line: the choice always v5; failure, 2 of 4 `NightSpriteChoiceTest` cases (the two that name v6), the rest pass |
| v6 sums | `c1f9b912…`, `220679a6…`, `64ad928a…`, `428573d1…` | the four files under `app/src/test/resources/sprite-v6-day/` hash to exactly these (computed) |

The live Worker today: `/sprites/v5/light.json` 200, `/sprites/v6/light.json` 404 (read, two GETs).

## 1. v6 = v5 plus three (computed)

- **Indices.** At 1x, 61 entries become 64; at 2x likewise. Every v5 entry is byte-equal in v6's index. The three
  new: `orchard_dots` 24×24 at (0, 180), `vineyard_rows` 16×16 at (25, 180), `nursery_dots` 12×12 at (42, 180) at
  1x; 48, 32 and 24 px at (0, 360), (50, 360), (84, 360) at 2x, with `pixelRatio` 2. One row, one-pixel gaps, as
  the builder documents.
- **Pixels.** The sheets grow 256×179 → 256×204 and 512×358 → 512×408; the whole v5 rectangle of each v6 sheet is
  byte-identical to the v5 sheet, so no v5 image moved or changed.
- **My own build.** `fetch-assets.sh` fetched the `2bdd3342` archive and every extracted file checked against
  `sprite-v6-items.sha256`; `build-sprite-v5.mjs v6` built v6 from v5, and its own verify passed ("61 v5 images
  unchanged + 3 new = 64"); `v6 --check` and the plain v5 `--check` pass. My sheets equal the committed
  `sprite-v6-day` files in index bytes, in pixels, **and in PNG bytes** (same Node, so the same zlib). So the four
  sums above are the sums of a reproducible artefact, not of a one-off.
- **v3 and the Worker copy.** `forager-street-v3.json` and `server/pmtiles-worker/src/street-style-v3.json` are
  `e2a259820bd1dda6…`, the preview `beb40e3a0e5624da…`, unchanged; `sprite-v5-items.sha256`, the v5 test sheets and
  `night-sprite/v5` are untouched by the diff. v3 still names `sprites/v5/light` (the checker pins it).

## 2. The pattern layer (read)

`landuse_orchards_pattern`: a fill on `landuse`, inserted directly after `landuse_orchards` (and before
`landuse_parks_fill`), `minzoom` 13, the same `IS_ORCHARD` filter as the fill (`kind == farmland` and `kind_detail`
in orchard, plant_nursery, vineyard), paint `fill-pattern: match kind_detail → orchard_dots / vineyard_rows /
nursery_dots, else ""`. Against the rulings' sage-10 spec (lines 503 to 507): "pattern layer directly after it,
minzoom 13, `fill-pattern` matches `kind_detail`", the three names, ink `#8ea65c`, night `#829a50`. All as ruled;
the checker's block 12 pins the layer, its place, its paint and the inversion, and my revert (minzoom 12) fails it.

**The `""` fallback.** Unreachable: a feature reaches the match only through the filter, and the filter admits
exactly the three `kind_detail` values the match names. If it were reached, `fill-pattern: ""` names no image; the
layer has no `fill-color`, so MapLibre draws nothing for that feature, and the fill beneath shows, which is what
Willow observed for a missing pattern. The night switch plan treats `""` as no image (the test "nothing left or
missing" passes with it), and the checker drops it from the names it requires in the sprite.

## 3. The app (read)

- **The choice.** `nightSpriteAssetDirFor(style.json)` parses the loaded style's JSON and tests its `sprite` string
  against `/sprites/v6/light$`: v6's folder for that, `night-sprite/v5` for v5, v4, another name, a sprite array, no
  sprite, or unparseable JSON (all tested). The committed v1, v2 and v3 give v5 and v4 gives v6 (tested against the
  files). `StreetWithTopoOver` builds `fromUri(streetUri)` plus a raster layer, so its loaded JSON carries the street's
  own sprite: v5 today. The offline style names no sprite and gets v5, as before; it is not a Street style and takes
  no night images anyway.
- **The cache.** Keyed by `"$dir/light$suffix"`, so a v5 sheet and a v6 sheet at the same density are different keys;
  one entry is kept, and a switch between styles reloads. v5 and v6 bitmaps cannot be served under the other's key.
- **The night sheets.** `night-sprite/v6` index equals the v6 day index at both densities; the v5 rectangle of each
  night sheet is byte-identical to `night-sprite/v5`; each of the three patterns has one day ink, `#8ea65c`, and one
  night ink, `#829a50`, and every pixel is the plain inversion of its day pixel with alpha kept (computed). The
  inversion arithmetic: (142, 166, 92) with shift 255 − 2 × 133.3 = −11.7 → (130, 154, 80) = `#829a50`.
- **Reach today.** `applyStreetNightImages` runs only for `usesStreetSprite` sources, which are v1 to v3 and the topo
  composition; none names v6, so nothing on a phone changes with this app code until the switch PR.

## 4. The Worker (read)

- **The change.** `SPRITE_PATH` from `(v4|v5)` to `(v4|v5|v6)`; `ASSET_CACHE_CONTROL` (a week) unchanged; the 404
  path for anything else under `/fonts/` or `/sprites/` unchanged and uncached; v4 and v5 served from the same keys
  as before (the new test reads v6, v5 and v4 in one run and pins the bucket reads). `/sprites/v7/…` and
  `/sprites/v6/dark.json` stay 404.
- **Where it is deployed from.** The branch is on `b493cffeb`, nine days behind `pre-main`. `pre-main` has not
  changed a Worker runtime file (`src/`, `wrangler.toml`, `package.json`) since that base, so the branch's `index.ts`
  equals `pre-main`'s plus the regex, and the seven-line change applies cleanly on `pre-main` (computed, `git apply
  --check`). The Worker README says the live Worker is built from pushes to **`main`** since 2026-10-24; the v5
  deploy record says Rowan deployed with wrangler from `pre-main`. Either way the deploy should come from a tree that
  carries the change **on top of `pre-main`**, not from the branch tip, so that what is live is what the trunk holds.
  Should-fix 1.

## 5. The order (read and inferred)

1. **R2 upload of `sprites/v6/`** (four files, the sums above). Nothing on any phone changes: no style names v6 and
   the Worker 404s it regardless.
2. **Worker deploy.** `/sprites/v6/…` starts serving. Still nothing changes: no style names v6.
3. **Merge into `street-v4`.** v4 names v6. v4 is not in `STREET_STYLE_URLS` and not on the Worker's style route, so
   still no phone loads it; when the switch PR lands, v4 loads with every v5 icon plus the three patterns.

Any other order breaks something: v4 merged before step 2 would load with a failing sprite request and lose every icon
and pattern (Willow's observation); the Worker before the upload would serve 404 for v6 (not cached, so harmless
once the files land, but pointless). The three gos in the dispatch's order are right.

**Offline regions.** MapLibre stores a region's style document by its URL and the sprite by the URL inside that
document. A v3 region stored `sprites/v5/light`; v5 is never rewritten (its records are untouched here and the
builder's verify forbids it), so a v3 region can never see a changed v5. A v4 region can exist only after the switch,
by which time v6 is served, and it stores `sprites/v6/light` itself. No path mixes them: the style carries its sprite
URL, and the night sheets follow the style's sprite. The one residual risk is operational, not in this code: v5 or v6
objects being overwritten on R2 later, which the v5 sums in `NightSpriteTest` and the v6 sums now recorded there
would catch at the next read-back.

## 6. The pin (read and computed)

`fetch-assets.sh` runs under `set -euo pipefail`; the archive is checked with `sha256sum -c` before extraction, and
each extracted file against `sprite-v6-items.sha256`, the repository's own record, never the archive's `SHA256SUMS`.
A cached archive with a wrong sum fails too (the check runs whether or not the download happened). My revert B: one
hex digit changed in the `orchard_dots.png` line → "1 computed checksum did NOT match", exit 1, before the build step.
A missing member would fail `tar`, also under `-e`. It fails closed.

## 7. Revert checks (computed)

- **Mine A:** `landuse_orchards_pattern` at `minzoom` 12 → the night checker fails on block 12 ("ruled a fill on
  landuse from z13").
- **Mine B:** a flipped sum digit → `fetch-assets.sh` fails closed (section 6).
- **The coder's:** the sheet choice forced to v5 → `NightSpriteChoiceTest` fails its two v6 cases and nothing else
  (JUnit XML, a 4-test filtered run).

## Verdict

### Blocking

None.

### Should-fix

1. **Deploy the Worker from the trunk, not the branch tip.** Merge `worker-sprite-v6` (or cherry-pick `220094639`)
   into `pre-main` first and deploy from there, by whichever path was used for v5. The runtime code is identical
   today, so this costs nothing and keeps "the live Worker is what the trunk holds" true.

### Notes

- `NightSpriteCache` keeps one decoded sheet; switching between a v5 style and a v4 style re-decodes. Fine for a
  style swap, worth knowing if the topo composition ever names v6 while Trail Map names v5.
- The art's `nursery_dots@2x` being byte-identical to `orchard_dots` (1x) is by construction: the same geometry at
  half the cell. The sheets place them separately, so nothing depends on it.
- `street-v4-revert-sprite-choice` can be deleted; its run and diff are recorded here.

### Prediction, scored

1 held, and more: byte-identical PNGs, not only pixels. 2 held. 3 held. 4 held: the code is the regex, the question
is the deploy's base.

**Is sprite v6 ready for the R2 upload, Worker deploy and merge, in that order? Yes.** v6 is v5 plus three to the
pixel and to the byte, reproducible from the pinned art; the pattern layer is the spec; the app chooses sheets by the
style's own sprite with no path that mixes v5 and v6; the Worker change is the regex alone; the order is the only
safe one and no offline region can mismatch. The three gos stay separate and the owner's; should-fix 1 is about where
the second go is run from.
