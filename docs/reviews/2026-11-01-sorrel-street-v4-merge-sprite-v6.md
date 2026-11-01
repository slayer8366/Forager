# Review 2026-11-01: PR #459, sprite v6 and the orchard patterns merged into `street-v4` beside the hill shading

Sorrel Tally, the independent reviewer (Fable). About 14:35 PST on 2026-11-01. Quick; read-only. Claims marked
**read** or **computed**.

**Baseline (read):** `street-v4-merge-sprite-v6` at `5409a9df6`, a merge commit with parents `a578ecd56` (`street-v4`,
the hill shading) and `b1a373f45` (`street-v4-sprite-v6`, reviewed on 10-31), into `street-v4` (#448). PR `CLEAN`; CI on
the head green on every job that runs for it.

**The resolution (computed):** `git merge-tree` of the two parents conflicts in exactly `check-street-night.mjs` and
`check-street-trails.mjs`, and the merge commit differs from the automatic merge tree in exactly those two files and
nothing else, so every other file is the automatic union of the parents. In the night checker, blocks 10 to 13 are all
present (creeks, orchards, patterns, hill shading); in the trails checker `LAYER_COUNT` is `{ v2: 93, v4: 104 }`.

**v4 as merged (read, from the committed file):** `sprite` is `…/sprites/v6/light`; sources are `protomaps` (vector),
`reference` (vector) and `dem` (raster-dem, terrarium, 256 px, z5 to 11, `/relief/{z}/{x}/{y}.png`); 104 layers;
`landuse_orchards` is immediately followed by `landuse_orchards_pattern`, and `landuse_runway` by `hillshade` by
`water_stream`. The hillshade paint is Sage's ALT as ruled: exaggeration 9 → 0.28, 11 → 0.32, 13 → 0.24, 15 → 0.16,
17 → 0.10, illumination 335 anchored to the map, shadow `#4a3f33`, highlight white, accent `rgba(74,63,51,0.5)`.

**v3 and sprite v5 (computed):** `forager-street-v3.json` and the Worker's copy `e2a259820bd1dda6…`, the preview
`beb40e3a0e5624da…`, `sprite-v5-items.sha256` `b16aeac6…` on both parents and the head; the v5 and v6 day and night
sheets are untouched by the merge.

**Regeneration (computed):** with the v6 sprite built (`build-sprite-v5.mjs v6 --check` and the v5 `--check` both
pass), `generate-street.mjs` at the head leaves the tree byte-identical; `check:street:v4` and `check:street` exit 0,
the v4 checker printing the 104 layers, the v6 sprite with 64 images and the ruled night ink.

**Naming v6 (read):** Aspen reports v6 live on the Worker with the build's sums, and the Worker release to `main` was
confirmed earlier today; the order the v6 review required (upload, deploy, then merge) is kept.

**Merge verdict for #459: mergeable.** The merge is the owner's go.
