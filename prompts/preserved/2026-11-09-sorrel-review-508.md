# Sorrel review: PR #508 (locate-jump), merged to pre-main at 3219d80e5 (c43ec0891..1f6d34b2e)

Reviewer: Sorrel Tally, Fable 5.1. 2026-11-09. Source reading plus CI artifacts (JUnit XML via `gh api`, python3 -I). No Gradle, no phone.

## Verdict

**Pass with items.** Built as ruled (RECORD -2189, Amendment 1); the reproduction is real, both reverts fail for their own reason on compiled builds, nothing is silenced, the three new tests are exactly the delta against pre-main's baseline. No defect in the code. Items are about the merge of #511 and about what the phone check did and did not cover.

## Built as ruled

- Maps tab, "Yes, stop the jump": `LocateMeStatus.Loading` now carries `previous` (LocateMeStatus.kt), and both camera-target readers (`compactMapDisplayRegion`, `compactMapLocateFocus`) read through `lastLocateFix()`. The only other production reader of `locateMeStatus` is `MapControlToasts` (`git grep` at 3219d80e5), whose `when` names only `PermissionDenied`/`Unavailable`; `Loading` falls to `else` as before. The tap's own camera move (`CompactMapIconCluster.kt:61-66`: `onResumeTracking`/`onReturnToRoute` then `onLocateMe`) is untouched, so the glide on the Maps tab is still MapLibre's/the navigation view's own 750 ms transition with zoom kept.
- Entry map, "Glide, keep zoom, no follow": `MapDataRefresh.kt:134-143` eases via `glideToLocate` (whole end camera from `map.cameraPosition`, target replaced, `NAVIGATION_VIEW_TRANSITION_MILLIS` = 750, NavigationView.kt:330) only when `isLocatePanOnly` (same region, new non-null focus point, a previous target applied). The entry map's `trackLiveLocation` is false (asserted in the test), so no following starts.
- The glide path on the Maps tab is normally not reached: the tap turns following on before the fix lands, so `cameraTargetTakenAsApplied` absorbs it (what `LocateKeepsCameraTest` asserts). It is reached only if the user ends following (drags) during the fetch — then the fix glides at their zoom, which is the owner's "Always".

## Tests: could they fail, and do the reverts belong to them

- Run 42401031766 (54e4829ff, full CI): 6,849 tests, 0 failures, 24 skipped (same skipped count as baseline). Against pre-main baseline 42337576510 (6,846): the name-set difference is exactly the three new cases, none removed (names diffed from both JUnit sets).
- Revert 1, run 42399733173 on `locate-jump-revert1` 288503b94 (parent 8b618a777): the diff is the one line `Loading()` without `previous` (read from the commit via API). `LocateKeepsCameraTest` fails with "while the tap's fix was being fetched, the camera moved instantly (to (39.8283, -98.5795) at zoom 12.0)" — the fallback-region centre, a failure only that revert produces. `EntryMapLocateGlideTest` also red there with "zoom -1.0", which belongs to the `newLatLng` bug fixed in 54e4829ff, not to the revert; the report says so.
- Revert 2, run 42401024827 on `locate-jump-revert2b` e4c220d17 (parent 54e4829ff): `takeIf { false }`. `EntryMapLocateGlideTest` fails "moved the camera instantly (to (46.1, -121.9) at zoom 13.0) instead of gliding". Its own message.
- Pre-fix reproduction 42399012115 (d35acd0f4): same fallback-centre message as revert 1.
- Compile-error check: 0 `e: file` lines in the test jobs' logs of 42399733173, 42401024827, 42401031766 (all three test jobs each).
- Both screen tests are real touches at the locate row's centre (`performTouchInput` + `click`), through `AvailabilityScreen` with the real `AvailabilityViewModel`, and through `CartographyEntryReportScreen`; the fixture map is a real `MapLibreMap`/`Transform` over a proxied `NativeMap`. One sample point each (centre), which is fine here: the claim is the camera, not hit-testing.
- Nothing ignored, no allowlist change, no assertion weakened (the one edit to an existing test is `Loading` → `Loading()`).

## Items

1. **#511 conflicts with #508 in `CameraMapFixture.kt`, textually and semantically.** Both edit the `easeTo` proxy case and the `Transform` constructor. #508 (5cc33f864): `eases` list, every eased/flown move lands on `recording.camera`, every map gets an inert `MapView` with a real `MapChangeReceiver`. #511 (f0f94168d, base 3cfa59d61 which has no #508): `eased` single field, the camera does **not** move, the view only with `easing = true`. With #511's version alone, #508's tests would throw (null view on `easeCamera`) and `EntryMapLocateGlideTest` would not compile (`eases`). `tools/s22-combined` (c29c0956d) already carries a reconciled fixture (28d0e16b1: `eases` + `eased`, lands on camera, view always, `easing` kept as an unused parameter) and run 42610749686 is green with both PRs' tests (7,612, 0 failed; `LocateKeepsCameraTest`, `EntryMapLocateGlideTest`, all four `MapCameraRestorePaddingTest` cases pass). The Hazel merging #511 into pre-main should take that resolution, not either side. RECORD -2192 already notes "the fixture conflict with #508" is with a Hazel; this is the content of it.
2. **Pre-main has no green CI of its own containing #508 yet**: the push run on 3219d80e5 (42626564647) was cancelled, superseded by 23b1bf25c (#510) pending at the time of this review. Low risk — pre-main's changes between #508's base (82d4a091a) and the merge base touched none of #508's files, and the combined branch is green — but promotion should wait for a green pre-main run, as the rule says.
3. **The phone check I can find in the record did not run #508's four S22 steps.** RECORD -2192's passes on 5032/5054/5074 list route-card, pulse, #511 (a)-(e), ring, Re-center, font-change items; none names a locate glide or "no snap first". The combined builds contained #508, so the S22 saw the locate in passing (Re-center/locate under #511 step 5), but the report's steps 1-4 (pan + zoom then locate on the Maps tab, after a search, navigating, and on the entry map) are not recorded as run. The report itself says "Not seen on a phone by me". Owner's call whether to run them before promotion or accept CI's reading.

## Owner's calls

- Run the four S22 steps from the completion report before promotion, or accept them as checked by use on the combined builds (item 3).
- Three pre-existing moments the report lists as not built, left as is, each a snap at the region's zoom: a failed fetch after a fix (`Unavailable` drops the target to the fallback), a locate during a basemap change, the very first tap that only raises the permission dialog. None is new in #508. Also pre-existing: on the Maps tab with no search, if the user drags during the fetch, the fix's new region snaps (the region changed, not the focus point).

## Notes

- `LocateMeStatus.Loading` changed from `data object` to `data class`: a `when` branch written `LocateMeStatus.Loading ->` would no longer compile, so CI green shows every reader was updated. Equality of `Loading(previous=X)` vs `Loading()` re-keys the toast effect harmlessly (toast only on denied/unavailable).
- `LocateGlide.kt` is 32 lines, new file; `MapDataRefresh` grew by 6 net lines. Within the modular-code rule.
- The merge message's "reviewed in the combined S22 builds" is wrong, as -2192 already discloses; this review is the first.
- Interaction with #500 (now on pre-main): none in code (no shared files; #500's J8 note that "#508's glide is journal-map only" matches the code, with the drag-during-fetch exception above). With #507 (ReCenterOnLocate ring): the ring is drawn, the tap is the same `CompactMapIconCluster` call; #508 changes nothing on that path.

## Could not determine

- Whether the pre-fix jump reproduced in CI is the jump the owner saw on the phone (the report's inference; the two production details the test copies — the permission launcher's post and `SightingsMap`'s effect order — I read as described but did not run).
- The glide's look on a device; the navigating case (not tested, as the report says).
- Whether #508's four S22 steps were run on the combined builds (item 3): not in the record I can reach.
