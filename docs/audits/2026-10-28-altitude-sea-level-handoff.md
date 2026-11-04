# Altitude above sea level: handoff (dispatch 2026-10-28-13)

Written by Hazel (Opus) for the next session, at Rowan's request: the context had passed the dispatch's handoff mark. The dispatch is `prompts/preserved/2026-10-28-13-altitude-sea-level.md` on `records-after-173`. It has two amendments: Amendment 1 (RECORD -1793) covers GPX import and export, and Amendment 2 (RECORD -1794) covers the height labels on phones that can't convert. It builds on `docs/audits/2026-10-28-altitude-report.md` (branch `altitude-report`).

Branch `altitude-sea-level`, cut from pre-main `d100a4776` (checked against the remote at the start). Draft PR **#440** into pre-main. Not merged.

## What is built and pushed

| Commit | What |
|---|---|
| `78d46deca` | The ViewModel split, a pure move: `locateMe` and `onLocateMePermissionDenied` go to `ui/availability/AvailabilityLocateMe.kt` (Rowan accepted it). The ViewModel was at 797 lines; it is now 783. |
| `7be27057b` | The live height: `domain/SeaLevelHeight.kt` (the interface, plus `NoSeaLevelConverter`), `location/AndroidSeaLevelConverter.kt`, `domain/LiveHeight.kt` (the anchoring, plus `ShownHeight` and `NoBarometer`). `AvailabilityLiveLocation` converts each accepted fix and reads the barometer as a child of the live-fix job. `AvailabilityUiState.shownHeight` is the one height, and `liveAltitudeMeters` now reads it. The navigation card gets `heightMeters` from `uiState.liveAltitudeMeters`, and the Return control uses `ReturnToStartInfo.withLiveHeight`, in `MainActivity`. "Alt unavailable" replaces the old phrase. The premise in the `BarometricHeight.kt` doc is corrected. |
| `cc705bb2f` | `LiveHeightTest` and `AndroidSeaLevelConverterTest`. |
| `b292e8d19` | Item 5 and item 6 under Amendments 1 and 2. The journal and Records height labels go through `domain/SeaLevelLabels.kt` (`seaLevelLabels`; per-track `ProfileTrack` on `HeightProfile.Drawn`). GPX export goes through `domain/GpxSeaLevel.kt` (`gpxHeightsAboveSeaLevel`, called in `TrackExportPanel.exportAndShareTrack`). `ui/format/SeaLevelConverterLocal.kt` is provided by `ForagerThemeWithNoSignal`, which `MainActivity` passes `container.seaLevelConverter`. GPX import is unchanged. New tests: `HeightLabelsSeaLevelTest`, `TrackExportSeaLevelTest`, `ReturnHeightFromLiveTest`. |
| `1aa2a91bb` | Test fixes after the first CI run (below). |

`AvailabilityCompactScaffold.kt` is untouched.

## CI

- **Run 40819762947, at `b292e8d19`:** failed. The JUnit XML from the `unit-test-report` artifact, read with a script, shows **5,928 tests, 9 failures, 0 errors, 24 skipped**. Lint, the release compile, the tile worker and the route cutter jobs passed.
  - The 3 `HeightLabelsSeaLevelTest` cases fail with "Unable to resolve activity" (read). The test didn't declare its host activity, so this is the harness. Fixed in `1aa2a91bb`.
  - The 6 layout cases fail in `MapChromeOutlineLandscapeDayTest`, `MapChromeOutlineLandscape780DayTest`, `LayoutFixesChipRowLandscapeTest` T7 and three `HeadingKeptAcrossTabsTest` cases. **Inferred, not confirmed:** each of these builds a state with a live fix but no shown height. The strip then reads "Alt unavailable" where it used to show the fix's height, so its width changes. `1aa2a91bb` gives every `AvailabilityUiState(liveFix = fix)` in the tests, and `LAYOUT_FIXES_FIX_STATE`, the fix's height at a zero offset (`withFixHeight()` in `LiveHeightTestSupport.kt`).
- **The run at `1aa2a91bb`:** pushed with this handoff and **not yet read**. Read its XML before trusting it. If any of the six layout tests still fails, the inference above is wrong, so take more data before a second fix (CLAUDE.md).
- Of this branch's 23 new test cases, I have only confirmed that they compiled and ran in the first run, and that 20 of them passed there. Each name below appeared in the XML only as a pass, or, for the labels tests, as the harness failure.

## Failing first, and revert checks

- **No test has been shown failing first, and no revert check has run.** The tests were written with the code, so "fails first" has to be shown by the revert checks: a one-line revert of each rule, with the build log checked for compile errors and the failure read as specific to that edit.
- The planned reverts, each with its file, its exact old and new text, and its test filter, are listed below. Each assumes the code at `1aa2a91bb`.
- Run each on its own `revert/…` branch from that head:
  - with `gh workflow run ci.yml --ref <branch> -f tests='<filter>'`;
  - restore from a saved copy, never with `git checkout -- FILE`;
  - check the build log for compile errors before reading any result;
  - delete the revert branch after reading it.

| Name | File (under `app/src/main/java/com/zynergylabs/forager/app/`) | Revert | Filter | Should fail |
|---|---|---|---|---|
| r01 | `ui/availability/AvailabilityUiState.kt` | `liveAltitudeMeters` back to `liveFix?.altitude` | `*AvailabilityScreenSeaLevelHeightTest;*AvailabilityViewModelLiveFixTest` | strip "50 m" not "72 m"; 50.0 not 71.6 |
| r02 | `ui/availability/NavigationReadout.kt` | `heightMeters?.let {` back to `fix.altitude?.let {` (with `fix ->`) | `*NavigationHudReadoutTest` | "the card reads the strip's height" |
| r03 | `ui/availability/AvailabilityLiveLocation.kt` | `pressureProvider.readings` becomes `NoBarometer.readings` | `*AvailabilityScreenSeaLevelHeightTest` | barometer collected 0 times; GPS-gap test stays at "72 m" |
| r04 | `domain/LiveHeight.kt` | `onPressure` returns `null` in place of `shown()` | `*LiveHeightTest;*AvailabilityScreenSeaLevelHeightTest` | GPS-gap tests |
| r05 | `domain/LiveHeight.kt` | the last line of `shown()` returns `ShownHeight.Unavailable` | same | no-barometer tests |
| r06 | `ui/availability/AvailabilityUiState.kt` | `?: liveFix?.altitude` added after the shown height | `*AvailabilityScreenSeaLevelHeightTest` | unsupported reads "50 m" |
| r07 | `ui/availability/NavigationHudText.kt` | the text back to "Elevation unavailable" | `*AvailabilityScreenSeaLevelHeightTest;*NavigationHudReadoutTest` | the wording tests |
| r08 | `domain/SeaLevelLabels.kt` | `is SeaLevelHeight.Known -> 0.0` | `*HeightLabelsSeaLevelTest` | the recorded labels test |
| r09 | `domain/SeaLevelLabels.kt` | `imported = false,` | `*HeightLabelsSeaLevelTest` | the imported labels test |
| r10 | `domain/SeaLevelLabels.kt` | `Unsupported, NoHeight -> 0.0` | `*HeightLabelsSeaLevelTest` | the no-numbers test |
| r11 | `ui/track/TrackExportPanel.kt` | `write(track, …, waypoints = waypoints)` | `*TrackExportSeaLevelTest` | recorded and unsupported export tests |
| r12 | `domain/GpxSeaLevel.kt` | the imported early return removed | `*TrackExportSeaLevelTest` | the imported export test |
| r13 | `domain/GpxSeaLevel.kt` | `?: raw` added after the Known height | `*TrackExportSeaLevelTest` | the unsupported export test |
| r14 | `domain/model/ReturnToStartInfo.kt` | `withLiveHeight` returns `this` | `*ReturnHeightFromLiveTest` | -3.4 expected |
| r15 | `location/AndroidSeaLevelConverter.kt` | log on every call | `*AndroidSeaLevelConverterTest` | "logged once" |

- There is no revert for `forager:point ele` staying raw: no code converts it, so there is no line to revert.

## What is left, item by item

1. **Item 1 (sea level):** built. Device step only. Whether Robolectric runs the real `AltitudeConverter` is unknown, so the adapter test covers its rules, not the conversion.
2. **Item 2 (barometer):** built. `LiveHeight` leaves out `barometricHeights`'s step limit and mean pass, which look both ways in time, as the report's simulation did. That needs reporting.
3. **Item 3 (one height):** built.
   - **Premise wrong:** no composable draws the navigation card's `elevationText` (RECORD -950 moved height to the strip). The card's value now agrees with the strip's, but there's nothing on screen to test. `NavigationHudReadoutTest` covers the readout.
   - The Return difference is tested at `withLiveHeight`, not through `MainActivity`.
4. **Item 4 ("Alt unavailable"):** built.
5. **Item 5 (saved data raw, labels and GPX converted):** built.
   - The labels convert each track by the offset at its first GPS height (the geoid varies about 0.01 m across a walk), not point by point. GPX export converts every point.
   - "Stored points unchanged" has no dedicated test, because the recording path is unchanged code. The export test checks that the full record's raw `ele` stays raw.
6. **Item 6 (GPX import):** P1 is built as readers that skip imported tracks; the import itself is unchanged. The gap with re-importing an old Forager export is accepted, and nothing detects it.
7. **Item 7 (premise comment):** done.
8. **Still to do:**
   - read CI at `1aa2a91bb`;
   - run the 15 revert checks;
   - finish the PR body: test count from the XML, revert results, and the S22 steps (the strip near the known ground height; the S26 if the owner allows; battery with the map open for 30 minutes with and without the barometer);
   - mark the PR ready;
   - report to Rowan.
   - No `docs/audits/README.md` row has been added; it's a serialization point.
