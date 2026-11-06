# Review: PR #462 `app-switch-street-v4` (Trail Map loads and downloads Street v4)

Sorrel Tally, 2026-11-01 (late evening, PST). Dispatch from Aspen. Read-only: no merge, install or deploy. Claims marked
**read**, **computed** or **inferred**.

## 1. Baseline

- Head 2d939a19b, base `pre-main` 13bcd0ebf (the #460 merge), an ancestor; two commits, 9209e5c7c (tests first, failing)
  then 2d939a19b. **computed**
- Eight files: `.github/workflows/ci.yml` (+34), `StreetStyle.kt`, `BasemapStyles.kt`, `MapStyleReload.kt`, `StreetNightTables.kt`
  (a comment), and three tests (`StreetStyleTest`, `NightExceptionsV4Test`, `OwnStreetStyleSourceTest`). **computed**
- Head CI (run 41391309596) green, including the new "Map style checkers pass, and the styles regenerate to what is
  committed" job; its log shows v3 and v4 regenerated and copied, then "street style v2 valid, bundled copy identical…" and
  "street style v4 valid, bundled copy identical…". **read**
- The live Worker already serves `/style/street-v4.json` (HTTP 200, 97,277 bytes) and it is JSON-equal to pre-main's
  `map-style/forager-street-v4.json`. **computed**

## 2. Prediction (before the runs)

1. `STREET_STYLE_URL` becomes v4's; `STREET_STYLE_V3_URL` is new; the list is v4, v3, v2, v1. A v3-only phone renders offline
   in v3 (the newest style any region holds), its regions show the older-style notice because the notice is "style URL differs
   from what a download would use now", and nothing else about them changes.
2. Night tables: `streetNightTablesFor` keys on the file name (`street-v4.json`, `forager-street-v4.json`), so v4's URL takes
   v4's tables and v3, v2, v1, the offline style and `null` keep v1 to v3's. `streetUriOf` makes Street-under-Topographical
   take the tables of the Street it draws.
3. Hill shading under Topographical is hidden by `hideHillshadeUnderTopo`, already on pre-main from the v4 merge; this PR does
   not touch it.
4. Reverting only the call site in `MapStyleReload` (back to the plain-`Uri` selector, `streetUriOf` kept) fails nothing: the new
   test holds `streetUriOf` itself, and no unit test drives `MapStyleReload`'s night pass.
5. Reverting the URL constant alone fails the URL test, the notice test and the list-order assertions in `StreetStyleTest`, the
   sprite test in `OwnStreetStyleSourceTest`, and the v4-chosen-by-name test in `NightExceptionsV4Test`.

## 3. The dispatch's checks

**v3-region phones keep working.** `offlineRenderStyleUrl` returns the first of `STREET_STYLE_URLS` that some region holds,
so a phone with v3 regions and no v4 one loads v3 offline, which the Worker still serves frozen; with any v4 region it loads
v4 and the older regions draw in it (same basemap tile URLs; their areas have no relief or band tiles, as the comment says).
`regionHasOlderMapStyle` is `isDownloaded && url != null && url != offlineDownloadStyleUrl()`, and `offlineDownloadStyleUrl()`
is now v4's, so every v3 region shows the -1294 notice, as v2's did at the last switch. The new test holds the four orderings
and the v3 notice. **read**

**Night tables for each URL.** v4's URL ends in `street-v4.json`, in `STREET_V4_STYLE_FILES`, so it takes `STREET_V4_NIGHT_TABLES`;
every other entry of the list, `null` and the offline style take `STREET_NIGHT_TABLES`, asserted by the updated
"v4 is chosen by its style's name" test (now iterating the list minus the current URL). `usesStreetSprite` accepts every list
entry, so v4 also takes the Street water exception and the night image switch. **read**

**Street under Topographical.** `ownStreetStyleSourceFor(OPEN_TOPO_MAP, night = true)` yields `StreetWithTopoOver(STREET_STYLE_URL, …)`;
`streetUriOf` returns its `streetUri`, so the night pass takes v4's tables and `applyStreetHillshadeNight` runs on the hillshade
layer; then `hideHillshadeUnderTopo` sets every hillshade layer's visibility to `none`, so OpenTopoMap's own relief is not
doubled. Both calls are in `MapStyleReload`'s style-loaded block; `hideHillshadeUnderTopo` has no unit test and is exercised
only on a device. **read**

**The map-style CI job.** `npm ci`, `fetch-assets.sh` (pinned sums), sprites v5 and v6 built, `generate:street` then
`git diff --exit-code -- . ../server/pmtiles-worker/src/`, then `check:street` and `check:street:v4`. Node 24.22.1 pinned by
version. No Gradle, 15-minute timeout. This closes my carried fix 2 from the v4 review: a hand-edited style or an
unregenerated generator change now fails CI. **read**

## 4. Revert checks

- **Willow's (0c684805a, run 41391307000), re-read from the JUnit:** URL back to v3 and `streetUriOf` ignoring Street under
  Topographical together: 143 tests in the filtered classes, 5 failures: `StreetStyleTest` × 2 (the URL test, the notice test),
  `NightExceptionsV4Test` × 2 (the Topographical test, the chosen-by-name test), `OwnStreetStyleSourceTest` × 1 (the sprite
  test). **computed**
- **Mine A, the call site only** (`sorrel-revert-462-callsite`, `MapStyleReload` back to `(source as? Uri)?.uri`, `streetUriOf`
  kept; run 41393871654, filtered to the seven style and night classes): 78 tests in the seven classes (`StreetStyleTest` 12, `NightExceptionsV4Test` 16,
  `NightExceptionsTest` 12, `OwnStreetStyleSourceTest` 9, `OwnStreetFallbackTest` 8, `PatternRelayoutTest` 11,
  `ReferenceTrailsVisibilityTest` 10), **0 failures**. The call site is unguarded (S-1). **computed**
- **Mine B, the URL constant only** (`sorrel-revert-462-url`, `STREET_STYLE_URL` back to v3, run 41393874658): the same 78 tests, **5 failures**, the same five as Willow's: `StreetStyleTest` the URL test and the notice test,
  `OwnStreetStyleSourceTest` the sprite test, `NightExceptionsV4Test` the chosen-by-name test and the Topographical test (its
  last assertions use `ownStreetStyleSourceFor(OPEN_TOPO_MAP)`, whose Street is `STREET_STYLE_URL`, so v3's URL there picks
  v1 to v3's tables). The new list-order test does not fail, because with the constant back to v3 the two constants are the
  same string and every `assertEquals` on `offlineRenderStyleUrl` still holds. **computed**

## 5. Scoring the prediction

1. Held (section 3; the new test's four orderings and the v3 notice).
2. Held (file-name keyed selection; the list minus the current URL all map to v1 to v3's).
3. Held: `hideHillshadeUnderTopo` is pre-main's, untouched here.
4. Held: 0 failures with the call site reverted (mine A).
5. Half held: the URL, notice, sprite and chosen-by-name tests fail as predicted; the list-order assertions do not (both
   constants collapse to one string), and the Topographical test fails instead, which I had not foreseen.

## 6. Verdict

**Blocking:** none.

**Should fix (can follow the merge):**

- **S-1. The carried fix's call site is unguarded.** With `MapStyleReload` reverted to the plain-`Uri` selector and `streetUriOf`
  left in place, every filtered test passes (mine A). The new Topographical test proves `streetUriOf`, not that the night pass
  uses it, so the regression the fix closes (Street v4 under the night topo drawn in v1 to v3's tables) can return silently. A
  small test that drives the night-pass selection with a `StreetWithTopoOver` source, or a pure function the pass and the test
  both call (`nightTablesFor(source)`), would hold it.

**Notes:**

- N-1. `hideHillshadeUnderTopo` and `applyStreetHillshadeNight` have no unit test either; both are a loop over `style.layers`
  on a MapLibre `Style`, which the unit suite cannot build. Device-only, as the coder's PR body for the v4 merge said.
- N-2. The first v4 download on a phone fetches relief tiles that 404 until `relief.pmtiles` is uploaded; MapLibre's offline
  store records them as missing and the region completes. Once the archive is up, those regions have no relief until
  re-downloaded. Inferred from MapLibre's offline behaviour, not tested here; the rollout plan's order (archive before the
  app switch reaches phones) avoids it if the APK ships after the upload.
- N-3. The CI job's `git diff --exit-code` covers `map-style/` and the Worker's `src/`; a drift in `app/src/main/assets`'s night
  sprite is outside it, as before.

**Merge?** Yes, with S-1 as a follow-up; nothing blocks.
