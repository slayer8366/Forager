# landscape-centre-dusk: B2 S10 failed in the evening (RECORD -621)

Branch `landscape-centre-dusk`, cut from `origin/main` at `4db24110`. Laptop coder, no phone, no PR.
Fix commit `fde14934`, test file only. No app code changed and no visual change.

## Cause

`AvailabilityScreenLandscapeB2Test` never passed `currentTime` to `AvailabilityScreen`. The
navigation HUD's sundown line, which is computed on screen when there is no recording
(`AvailabilityCompactMapUi.kt:1012`, `SundownLineText.kt:112`), therefore read the real clock. The
fixture's fix is near Portland, so the line is shown from 2 h 30 min before sunset (about 23:15Z)
until sunrise (`SundownLine.isShown`, `SundownLine.kt:159-168`). Under legacy graphics the HUD
grows from 124 dp to 160 dp, which crosses the central third's top at 128 dp.

Evidence comes from temporary probe tests at ROTATION_90 while navigating. The probes were not
committed, and the file was restored from a saved copy each time.

| Clock | Line | Legacy graphics | NATIVE graphics |
|---|---|---|---|
| 2026-10-06T18:00Z | none | 124 dp | 80 dp |
| 2026-10-07T01:00Z | "Sunset 6:41 PM · in 41 min · dark 7:10" | 160 dp | 96 dp |
| 2026-10-07T03:40Z | "Dark since 7:10 PM" | 160 dp | 96 dp |
| real clock, line forced to null at `:1012` | none | 124 dp, both S10 cases pass | 80 dp |
| 18:00Z, line forced to a fixed string | shown | 160 dp, both S10 cases fail | 96 dp |

The failure reproduced on unchanged main at 03:39Z: 26 tests, 2 failures, HUD
`DpRect(383,0,743,160)` at 90 and `DpRect(80,0,440,160)` at 270.

## Verdict: (A), a test defect, with a thin real margin

S10 (intent 2026-09-27-35): "no persistent chrome (search bar, chip, strip, HUD, cluster, rail)
intersects the window's central third, x 1/3-2/3 and y 1/3-2/3". The owner's words were "Nothing is in
the center area which is the focus of the map."

The S22's own dump at 90 (owner device evidence 2026-09-30-part-3, `r4.xml` and `r4-return.png`,
from before the line existed, kept off GitHub):

- The map's top edge is at 84 px, which is a 30 dp status bar.
- The HUD's drawn bottom is about 309 px, which is 110 dp. That makes the HUD 80 dp tall, the same
  as under NATIVE.

Adding the line's 16 dp puts the bottom at about 126 dp against 128 dp, so it clears by about 2 dp.
This is inferred from geometry taken before the line existed; it was not measured with the line on
a phone. The owner's ruling on the margin is "Leave it, check on the phone (Recommended)" (RECORD -622).

## Change

- Every test in the file runs at `B2_DAY_NOW` (11:00 PDT, line hidden), for both the screen and the
  fix's timestamp. The two existing navigating S10 cases also assert that no line is drawn.
- New: S10 at 90 and at 270 while navigating at dusk (`B2_DUSK_NOW`, 41 min before sunset).
  - The line is asserted present, inside the HUD, and reading "Sunset … · in 41 min · dark …".
  - The central third is then asserted clear.
  - They run under NATIVE graphics, per the chip-case ruling (-119, "Option B"), with the assertion
    unchanged. The HUD measures 96 dp there.
  - The text check matches only the countdown exactly, because the clock times follow the JVM's
    zone. This was run in PDT only; under UTC (CI) it is unverified.

## Revert checks

Each edit was restored from a saved copy, and the hash was checked against the forward file
afterwards. No run had compile errors in its log.

| Revert | Result |
|---|---|
| R1: the clock unpinned (`currentTime = { System.currentTimeMillis() }`), run at 03:48Z | 4 failures. Day cases: "at B2_DAY_NOW the HUD must carry no sundown line; found 1". Dusk cases: "… reads sunset, 41 min and dark; was <Dark since 7:10 PM>" |
| R2: NATIVE removed from the 90 dusk case | 1 failure: the central third, `navigation-hud DpRect(left=383.0.dp, top=0.0.dp, right=743.0.dp, bottom=160.0.dp)` |
| R3: the HUD's line forced to null in app code | 2 failures, both dusk cases: "… was <null>" |

## Suites

Each suite was compiled first, the Kotlin daemon was then stopped, and the run was capped at 5 GB.

- Before, with main's test file at 03:56Z: 473 suites, 3890 tests, 2 failures (the two S10
  navigating cases), 24 skipped.
- After, on `fde14934` at 03:51Z: 473 suites, 3892 tests, 0 failures, 24 skipped.

## Other tests that read the real clock through `currentTime`

For a test to flip with the hour, it must be navigating, have a position (`headingFix`), and use the
default clock. I surveyed every test file that renders `AvailabilityScreen` or names the HUD or a
navigating state.

- The navigating tests with a position all pin their clock, so none of them flips with the hour:
  ApproximatePosition, ReturnRoute, RouteDrawn, WaypointNavigate and NavigationView use `t =
  1_700_000_000_000L`; MapIconStack uses `hudClock`; SundownLine pins `now`. Whether the line shows
  at those fixed instants was not checked.
- The navigating tests without a pinned clock have no fix, so the line stays hidden (`FindingPosition`):
  BackNavigation, Layout, MapChromeColour and MapChromeOverMap.
- Not surveyed: clocks inside `SundownWatch` and the `TrackRecordingViewModel` path, which feed
  `recordingSundownLine`, not `currentTime`.

## Device items (owner, S22)

1. While navigating with the sundown line shown, at ROTATION_90 and 270: measure the HUD's drawn
   bottom against the central third's top (128 dp, 360 px on the S22 at 1080 px tall). Pass if the
   bottom is at or above it. The expected margin is about 2 dp.
2. Observation, not a gate: the same at the phone's largest font size.

The index row in `docs/audits/README.md` is left to the planner, to avoid that file's merge
conflicts.
