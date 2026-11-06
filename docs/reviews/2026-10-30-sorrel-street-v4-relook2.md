# Re-look 2, 2026-10-30: PR #448, Street v4 at `97a26e8c7`

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-30-sorrel-street-v4-relook2.md`
in Forager-cartography. After the pi-35 review, as the dispatch ordered. Started about 22:30 PDT on 2026-10-30 (reading
only, while pi-35 ran), worked from about 22:50, finished about 23:20 PDT. Times are Pacific. Each claim is marked
**read**, **computed** or **inferred**. Renders stay on the Pi (`/tmp/sorrel4/render/shots/`).

The earlier reviews on this branch: `f228f9281` at `11959e944`, `e9be0bcdd` at `6ea1c9290`.

## Prediction (formed on reading the diff, before the rebuild, the renders and the arithmetic)

1. v4 rebuilds exactly from the rulings' option A and Sage's REC table.
2. The extended table cannot reach v1 to v3 or `StreetWithTopoOver`, for the same reason as before (an empty map by
   default); each entry applies only under its own layer id, so `#ffffff` in `roads_minor` and in `roads_major` are
   separate rows.
3. Trails stay strongest: at z13 the minor roads are narrower than the footpath and take the tertiary's dark grey.
4. Some "darker rims" will not be darker than the night fill they border: Sage's REC and ALT tables share three values
   (school, hospital, industrial), which suggests those three were not derived the same way.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `street-v4` and PR head | `97a26e8c7` | `97a26e8c703e970bb88ac8e91e485f08fd6e29bb`; three commits on `e9be0bcdd` (`307d3c2ac` should-fix 3, `5a5cbd1b6` tests first, `97a26e8c7`) |
| `pre-main` | | `1ac67f12b`, as at the first re-look; merge-base still `b493cffeb` |
| Full CI | 41172033453 | `97a26e8c7`, success, 5,970 tests, 0 failures (JUnit XML) |
| Test-first | 41171469222 | `5a5cbd1b6`, failure |
| Revert | 41172097097 | `street-v4-revert-minor-night` `3255e7c8f`, failure |
| S22 APK | 41172099022 | `street-v4-s22-debug` `fb987fe13` = `97a26e8c7` merged into the one-line debug change; success |
| v3 | unchanged | `e2a259820bd1dda6…` (both copies), `beb40e3a0e5624da…` |

**One thing the dispatch did not list:** the PR's own `pull_request` run on the same head, 41172037381, is **red**.
Its JUnit XML has 5,971 tests and one failure, `AvailabilityScreenCrowdedIconsSettingTest` "real touches across each
row pick it, and the pick survives a restart" (expected `list`, was `zoom_then_fan`), a crowded-icons setting test with
no relation to the map style. The dispatched run on the identical commit passed it. Flaky, on the evidence (inferred);
GitHub shows the PR as `UNSTABLE` until a green `pull_request` run replaces it. Should-fix 1.

## 1. Does v4 equal the rulings, every value? (computed)

**Yes.** My rebuild script, extended by hand from the rulings' "Low-zoom roads, night borders" section (the class
widths at z8 to z12 and the ruled z15/z18, the motorway widths, the early casings 0 at 10.9 / 0.3 at z11 / 0.5 at z12,
the late and bridge casings, the 10.5 → 11.5 cross-fades of Carto's low-zoom colours into the ruled ones, the motorway
casing's 11.5 → 12.5, `roads_minor`'s width, its `#c4beb4` → `#ffffff` fade over 11.5 → 12, its opacity 0.7 → 1 over
z10 → 11 and its casing 0 → 0.5 over 11.5 → 12) gives all 101 layers equal to the committed file, in order. (My
script's one difference was its own: an empty `paint` it added to the `roads_oneway` symbol layer.) `night-layers` and
`night-fills` are unchanged from `e9be0bcdd`; `forager:night-line-colours` is the four-entry table: the tertiary's two
rows, `roads_minor {#ffffff: #333333}`, and `landuse_edges` with thirteen rows equal, key for key, to the REC table in
`/tmp/sage11-work/night-edges.json`. The preview equals v4 in layers and metadata. Regeneration in my worktree at this
head is byte-identical; `check:street:v4` and `check:street` exit 0, and section 9 of the night checker now pins every
low-zoom value by hand.

Carto's `#e66e89` and `#c24e6b` are its `@motorway-low-zoom` and `@motorway-low-zoom-casing` (road-colors-generated.mss
lines 15 and 19 at `9295745d`, read at the first re-look).

## 2. The extended night-line-colours table (read)

- **Reach.** Unchanged mechanism: `tables.lineColours[property.layerId]`, consulted only after the layer table and only
  for `line-color`. `STREET_NIGHT_TABLES` still carries the default `emptyMap()`, so v1 to v3 and `StreetWithTopoOver`
  (which still resolves to it) never enter the branch. `MapStyleReload.kt` and the selector are unchanged since
  `f228f9281`; the full run's v3 suites pass.
- **Per layer.** The table is keyed by layer id first, then by day colour, so `roads_minor`'s `#ffffff` and
  `roads_major`'s are separate rows and `landuse_edges`' thirteen colours apply to no other layer. In the cross-fade
  expressions the low-zoom colours (`#bdb7ad`, `#c4beb4`, the Carto ambers and pinks) are not keys, so they invert:
  `roads_minor` at night fades from `#4a443a` (z11.5) to `#333333` (z12), the majors from the inverted Carto ambers
  to the ruled inversions, with the tertiary's row reached only at the `#ffffff` stop. Every other literal inverts, as
  before.
- **Tests.** The app test hand-writes the three road rows and the thirteen rims, checks the minor roads' night stops
  (`rgba(74, 68, 58, 1)` then `rgba(51, 51, 51, 1)`), checks both stops of the major-road fade by class and ramp, and
  the band test now reads the app's table (should-fix 3, closed). The coder's revert (`roads_minor` dropped from the
  app table) fails exactly the three tests that carry it; the test-first run fails five, each on the feature the commit
  adds (read, JUnit XML; both were filtered runs of 13 and 17 tests).

## 3. Do the trails stay the strongest line? (computed, and read from the renders)

- **z13 forest, where `roads_minor` now draws dark grey:** minor roads `#333333` on the night forest 1.97:1 at
  1.03 px, the night footpath 2.58:1 at 1.2 px, informal paths 2.13:1; by day white on the forest 1.68:1 against the
  footpath's 3.52:1. Trails stronger both ways, by colour and by width.
- **The low zooms (z8 to z11.5)** have no trails (footpaths start at z12 to 13), so the Carto ambers and their nights
  (trunk low `#a86509` at 3.46:1 on the night ground, brighter than the ruled night trunk) compete with nothing.
- **Rims** sit on area boundaries at 0.5 to 1 px; against the night ground the brightest is bare rock's at 2.65:1,
  under every trail.
- The earlier numbers (motorway casing the strongest road line at 3.98 / 3.05 / 1.59 at night, under the footpath's
  6.47 / 4.97 / 2.58) stand; nothing in this delta raises a road above the trails.

**The renders (GL JS 6.12.0 in headless Firefox 142, 412×800 at 2×, tiles from the Worker, my night emulation of
`NightColour.kt` with the four-entry line table; 16 shots: the four views, Silver Falls z13, Oregon City z15, Canby
z10.5 and z11.5 for the cross-fades; no style errors, every tile 200):**

- **z9 Canby–Silverton.** Day: the roads are thinner and have no edges now; Interstate 5 is Carto's low-zoom pink, the
  trunks and primaries a paler amber than before, the tertiaries a thin grey. It reads closer to OSM at a glance than
  the e9be0bcdd render did. Night: the motorway a dark red, the majors brown, the tertiaries a dim grey; quieter than
  before. (The light-green outlines around the state forest are v3's park outline, unchanged, not the new rims, which
  start at z13.)
- **z10.5 and z11.5 near Molalla (the fades).** Day at z10.5: the residential streets of Molalla and the farm roads
  appear as a faint grey texture (`#c4beb4` at 0.4 px), the majors in Carto's low ambers with no casing. At z11.5 the
  majors have reached the ruled colours with a hairline casing, and the streets are still grey, half way to white.
  Night: the texture is a dim grey-brown lattice at z10.5 and a slightly lighter grey at z11.5; the majors brown with
  thin amber edges from z11. Nothing jumps between the two zooms; the fades read as continuous.
- **z11 Oregon City.** Day: the street grid shows as fine grey lines across the residential grey, the pink 205 with its
  darker low-zoom casing; the fills as before. Night: a grey lattice of streets, brown majors, the motorway pink-red.
- **z12 inner Portland.** Day: the grid in white, buildings a faint tan, the three freeways pink. Night: the dense grey
  lattice with the tertiary edges, dark-brown buildings, pink motorways; unchanged in character from the first re-look.
- **z13 Silver Falls (the new case).** Day: the minor roads (Silver Falls Drive and the campground loops) are white
  lines through the forest; the trails are the dashed red-brown and plainly stronger, the 214 amber. Night: the same
  roads are dark grey (`#333333`) inside the forest, weaker than the salmon trail dashes beside them and than the
  tertiary's grey casing. z13.5 is the same picture.
- **z15 Oregon City.** Day: tan buildings with their outline, white streets, faint area edges, the trail dashes at the
  park. Night: the forest and park now have a **darker** rim than their night fill (the first re-look's lighter rim is
  gone), the residential rims are all but invisible against the night ground (1.07:1, as computed), buildings
  dark-brown with a lighter outline, the trail dashes the strongest line.


## 4. Revert checks

- **Mine, style side (computed):** the hospital rim dropped from `NIGHT_LINE_COLOURS_V4`, regenerated:
  `check-street-night.mjs v4` fails on "v4's night line colours are … ruled …" (section 7's hand table). Restored.
- **Mine, in CI:** never-merged branch `sorrel-revert-rims` at `96b9ef56e` (`97a26e8c7` with the `landuse_edges`
  rims removed from `STREET_V4_NIGHT_TABLES`), run 41174071920, full suite: 5,970 tests, exactly 2 failures, both in `NightExceptionsV4Test` and both about the rims ("line colours by day colour are the design's" and "the area edges take their darker night rims"; read from the JUnit XML). Every other suite passed, `NightExceptionsTest` included. The rims are pinned by hand and by the metadata, and nothing else depends on them.
- **The coder's** are in section 2.

## Verdict

### Blocking

None.

### Should-fix

1. **A green `pull_request` run on `97a26e8c7`** before the owner's go: the PR's own check is red on an unrelated,
   apparently flaky test; the dispatched run passed the same commit. Re-run it (or fix the flake) so the PR page is not
   `UNSTABLE` at merge time.
2. **Three "darker rims" are lighter than the night fill they border (computed):** school `#3b352e` on its fill's night
   `#28221b`, hospital `#3c3231` on `#291f1e`, industrial `#382735` on `#21111e`. The other ten are darker by 1.2 to
   1.3:1, as the option promised. Those three values are identical in Sage's REC and ALT_lighter tables, and I first
   read that as a derivation slip. **Correction (Aspen, 23:15):** Sage's 21:25 spec states it as a deliberate
   exception: "School, hospital and industrial take a lighter rim because their night fills are too dark to darken by
   1.3 without going near-black (#080706, which reached only 1.14-1.28)." So the values are intended; what remains is
   that the exception was not on the owner's options page, and Aspen is putting it to the owner. The PR holds the
   ruled table exactly; nothing for Willow unless the owner re-rules.
3. (Carried) `StreetWithTopoOver` → `streetUri` for the switch PR; (carried) a CI job for the map-style checkers.

### Notes

- The grass-kinds rim `#1d240b` is 1.00:1 against the night ground: invisible on the ground side, visible against the
  grass side (1.30:1). Recorded, as the ruling says, not gated.
- The coder's revert and test-first runs were filtered (13 and 17 tests), so "nothing else fails" there is within the
  filter; the full run on the head covers the rest.
- `sorrel-revert-rims` stays on origin for the record; delete with the others.

### Prediction, scored

1 held. 2 held. 3 held, with margin. 4 half held: three rims are lighter, as predicted from the shared values, but by Sage's stated exception, not by a slip; the prediction's mechanism was wrong.

**Is PR #448 at `97a26e8c7` mergeable as plumbing ahead of the switch?** Yes, once the PR's own check is green
(should-fix 1, a re-run). v4 is the rulings to the byte including option A and the REC rims; v1 to v3 are unchanged
by file and by every app path and the extended table cannot reach them; the trails stay the strongest line by day and
by night at every zoom they draw. Should-fix 2 is a design question for the owner, not a reason to hold the plumbing.
The S22 check of this head and the merge are the owner's go.
