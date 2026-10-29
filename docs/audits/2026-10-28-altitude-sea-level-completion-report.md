# Altitude above sea level: completion report (dispatch 2026-10-28-13)

Written by Hazel (Opus), 2026-10-29 05:34 UTC, in the session that picked up `docs/audits/2026-10-28-altitude-sea-level-handoff.md`. The dispatch is `prompts/preserved/2026-10-28-13-altitude-sea-level.md` on `records-after-173`, with Amendment 1 (RECORD -1793, GPX import and export) and Amendment 2 (RECORD -1794, the chart on phones that can't convert). PR **#440** into pre-main, from branch `altitude-sea-level`. **Not merged.**

## What landed

The code is the handoff's five commits, unchanged in this session: `78d46deca` (the ViewModel split, a pure move), `7be27057b` (the live height), `cc705bb2f` (its tests), `b292e8d19` (labels and GPX export, Amendments 1 and 2), and `1aa2a91bb` (test fixes after the first CI run). `c61ee0933` is the handoff. This report and the index row are the only commit this session adds. The base, pre-main `d100a4776`, was checked against the remote in this session and is still its head.

## Verification before anything else

- **The worktree** was at `c61ee0933`, the same as `origin/altitude-sea-level`, with a clean tree.
- **The CI run at `1aa2a91bb` was never finished.** Run 40821459878 was cancelled when the handoff was pushed. The run read is **40821535871 at `c61ee0933`**, which differs from `1aa2a91bb` only by the handoff document (`git diff --stat`: one file, 76 lines).
- **The JUnit XML of that run** (artifact `unit-test-report`, 813 files, read with a script) gives **5,928 tests, 0 failures, 0 errors, 24 skipped**. That is the same total as the failing run 40819762947 at `b292e8d19`, which is expected, since `1aa2a91bb` added no tests. All jobs passed.
- **The six layout tests the handoff named all pass.** In the XML, `MapChromeOutlineLandscapeDayTest` passed 6 of 6, `MapChromeOutlineLandscape780DayTest` 6 of 6, `LayoutFixesChipRowLandscapeTest` 28 of 28 (T7 included) and `HeadingKeptAcrossTabsTest` 3 of 3. The three `HeightLabelsSeaLevelTest` cases also pass, 3 of 3.
  - The handoff's diagnosis was that a live fix with no shown height made the strip read "Alt unavailable". It is consistent with this result, but it is not proven by it. A green run shows the fix worked. It does not show why the tests failed before, and I did not reproduce the earlier failure messages.
- **The zero sea-level offset only restores the strip text they measured before. It does not change what they measure.**
  - The reason: these fixtures build `AvailabilityUiState` directly and hand it to the screen, so they never run the live path. `withFixHeight()` (`LiveHeightTestSupport.kt`) sets `shownHeight` to `ShownHeight.Known(fix.altitude, aboveRawMeters = 0.0)`.
  - The strip prints `meters`, which is the fix's own altitude, so it shows the same characters as before this branch.
  - The offset (`aboveRawMeters`) is read only by `ShownHeight.Known.seaLevelOf`, through `withLiveHeight`, which is called only at `MainActivity.kt:669`. None of the 21 files `1aa2a91bb` touches mentions `withLiveHeight`, `seaLevelOf` or `aboveRawMeters` (grep).
  - What these tests give up: they no longer show the state of a live fix with no shown height. That state is now covered on purpose, by `AvailabilityScreenSeaLevelHeightTest`'s unsupported case.
  - A real sea-level height is about 21 m higher than raw. These fixtures still measure the width of whatever text they set, and that claim does not depend on where the number came from.

## Evidence: the 15 revert checks

**How they were run.**
- Each revert was a one-line edit on its own branch, `revert/asl-rNN`, from `c61ee0933`, made in a separate scratch worktree by a script.
- The script saved a copy of the file, made an exact replacement (refusing any old text that did not occur exactly once), committed and pushed. It then restored the file from the saved copy and checked that it equalled both the saved copy and `c61ee0933`'s version. All 15 did.
- Each branch's diff against the base was exactly its one file.
- Each ran as `gh workflow run ci.yml --ref revert/asl-rNN -f tests='<filter>'`.

**Build logs, checked before any result was read.**
- Every log names its own revert sha (20 occurrences each), and every log runs `compileDebugKotlin` and `compileDebugUnitTestKotlin`.
- No log has a compile error (`e: file:` lines, or a compile task `FAILED`). The grep is a real zero: the same logs carry about 1,462 compiler warnings as `w: file:` lines, so a compile error in the same format would have matched.
- The XML is fresh: each run holds only its filtered classes (1 or 2 files), not the 813 of a full run.

**After the checks.**
- All 15 branches are deleted from the remote and locally (`git ls-remote` lists none).
- `altitude-sea-level` is still `c61ee0933` locally and on origin, with a clean tree, so the forward change is present.

| Check | Run | Revert | Failed, as its own edit can cause |
|---|---|---|---|
| r01 | 40821993732 | `liveAltitudeMeters` reads `liveFix?.altitude` | 6 of 20. Strip: "expected 72 m but was 50 m" (×3), and the unsupported case "expected Alt unavailable but was 50 m". ViewModel: "expected 71.6 but was 50.0" (×2). |
| r02 | 40821996397 | the card's height from `fix.altitude` | 1 of 41. "the card reads the strip's height": "expected 72 m but was 50 m". |
| r03 | 40821999269 | the map reads `NoBarometer` | 2 of 5. "read the barometer, with no recording running: expected 1 but was 0", and the GPS gap "expected 80 m but was 72 m". |
| r04 | 40822002425 | `onPressure` returns `null` | 4 of 12. The GPS gap test on the screen (80 m against 72 m). In `LiveHeightTest`: the GPS gap and the pressure fall (null cast to Known), and "cannot convert even with a barometer: expected Unavailable but was null". |
| r05 | 40822005267 | no-barometer fallback returns Unavailable | 3 of 12, every one a no-barometer case. Strip: "expected 72 m but was Alt unavailable" (×2). `LiveHeightTest`: Unavailable cast to Known. |
| r06 | 40822008005 | `?: liveFix?.altitude` after the shown height | 1 of 5. Unsupported: "expected Alt unavailable but was 50 m". |
| r07 | 40822012086 | "Elevation unavailable" | 3 of 46, every one "expected [Alt] unavailable but was [Elevation] unavailable". |
| r08 | 40822016236 | the labels' offset is 0.0 | 1 of 3. "highest, 155 + 21.6: expected 1 but was 0". |
| r09 | 40822020034 | `imported = false` | 1 of 3. The imported walk's labels: "expected 1 but was 0". |
| r10 | 40822023222 | unsupported converts at 0.0 | 1 of 3. "no raw height as a label: expected 0 but was 2". |
| r11 | 40822025662 | export writes the stored track | 2 of 3. Recorded: "expected [71.5, 71.5] but was [50.0, 50.0]". Unsupported: "expected [null, null] but was [50.0, 50.0]". |
| r12 | 40822028535 | the imported early return removed | 1 of 3. Imported: "expected [50.0, 50.0] but was [71.5, 71.5]". |
| r13 | 40822031942 | `?: raw` after the Known height | 1 of 3. Unsupported: "expected [null, null] but was [50.0, 50.0]". |
| r14 | 40822034991 | `withLiveHeight` returns `this` | 2 of 2. "(40 + 21.6) - 65: expected -3.4 but was -7.0", and "expected null, but was -7.0". |
| r15 | 40822037723 | log on every call | 1 of 2. "logged once, not per fix: expected 1 but was 2". |

**Each failure matches its own edit.** Every failing case is one the handoff predicted, or a direct result of the same edit:
- r01's unsupported case: raw now stands in for Unavailable.
- r04's cannot-convert case: `onPressure` no longer returns Unavailable.
- r11's unsupported case: the raw height is written.
- r14's null case: the raw difference stays.

No failure belongs to a different rule, and in each run the classes outside the edit's reach passed.

**The new tests.** This branch adds 23 new test cases. Each passed on the full run and failed under at least one revert:

| Class | New cases |
|---|---|
| `LiveHeightTest` | 7 |
| `AvailabilityScreenSeaLevelHeightTest` | 5 |
| `HeightLabelsSeaLevelTest` | 3 |
| `TrackExportSeaLevelTest` | 3 |
| `AndroidSeaLevelConverterTest` | 2 |
| `ReturnHeightFromLiveTest` | 2 |
| `NavigationHudReadoutTest` | 1 ("the card reads the strip's height") |

Some cases are not named in any failure above: of the 7 `LiveHeightTest` cases, 4 are named (r04 and r05). No revert in this list targeted the other three, so their bite is not shown:
- "a GPS height more than 30 m off does not move the anchor";
- "on a flat walk with steady pressure the barometer steadies a wandering GPS height";
- "nothing is shown before a fix, and a fix with no height and no anchor is unavailable".

## What was built (unchanged from the handoff)

- **Sea level.** Behind `SeaLevelConverter` and `AndroidSeaLevelConverter`. Below Android 14, or when the conversion fails, the height is "unsupported", logged once.
- **The live steadying.** `LiveHeight` anchors the barometer to the converted GPS height. With no barometer, each fix's converted height is shown as it comes. In a GPS gap, the anchor holds and the reading follows the barometer. The barometer is read whenever the map's live collection runs, with or without a recording.
- **One height.** The strip, the navigation readout and the Return difference read the one shown height.
- **The wording.** "Alt unavailable" is the one phrase.
- **Saved data stays raw.** The journal's and Records' highest and lowest labels convert, and GPX export converts `<trkpt>` and `<wpt>` `<ele>`.
- **Imported tracks.** They show and export as the file gave them (Amendment 1). On a phone that can't convert, export leaves out `<ele>`, and the chart draws with no numbers (Amendment 2).
- **The premise comment.** `BarometricHeight.kt` is corrected.

**Not touched:**
- `AvailabilityCompactScaffold.kt`;
- GPX import;
- the `forager:point ele=` record, which stays the literal stored value;
- the database schema (no schema, entity, DAO or migration file differs from pre-main).

## Not tested, said plainly

- **The real platform converter.** Whether Robolectric runs the real `AltitudeConverter` is unknown. The tests use a fixed-offset converter, so the conversion itself is device-only.
- **A premise of the dispatch was wrong.** No composable draws the navigation card's `elevationText`: RECORD -950 moved height to the strip. The readout's value is tested, but nothing on screen shows it.
- **The Return difference** is tested at `withLiveHeight`, not through `MainActivity`'s call at `:669`. That call is unverified by any test.
- **"Stored points unchanged"** has no dedicated test. The recording path is unchanged code. `TrackExportSeaLevelTest` checks that the full record's raw `ele` stays raw.
- **`forager:point ele=` staying raw** has no revert, because no code converts it, so there is no line to revert.
- **Three `LiveHeightTest` cases** (two anchoring, one before the first fix) were not targeted by any revert, as above.
- **The live rule leaves out two parts of the journal's.** `LiveHeight` does not carry over `barometricHeights`'s step limit and mean pass, which look both ways in time; the report's simulation left them out too. In the first minute the anchor is the median so far, so the reading can step then.
- **The journal labels** convert each track by the offset at its first GPS height, not point by point. The geoid changes by about 0.01 m across a walk.
- **Battery and on-screen behaviour** are device-only. The steps are below.

## Device steps (S22 Ultra; the S26 Ultra only if the owner allows)

These steps use CI's debug APKs only, never a release build on the owner's phones. Swapping between the pre-main build and this branch's build does not change the database schema. That the two debug APKs share a signing key, so they install over each other, is unverified.

1. **The converter is supported.** With this branch's APK installed, open the Maps tab outdoors with a fix, and read `adb logcat -s ForagerSeaLevel`.
   - **Pass:** no "Sea-level height unsupported" line.
   - **Evidence:** the logcat output, and the phone's Android version.
   - **If a line appears:** the strip reads "Alt unavailable" on this phone. Report the line's reason.
2. **The strip reads near the known ground height.** Stand at a spot whose height above sea level is known: a survey benchmark, or a topo map's spot height. Wait two minutes with the map open.
   - **Evidence:** the strip's value, the known height and the source of that height.
   - **Pass (proposed threshold, not set by the owner):** within 10 m of the known height, and not about 20 m low.
   - For comparison, the pre-main build at the same spot should read about 21 m lower.
3. **Steady while still.** With the phone still for two minutes, note the range of the strip's values. This is an observation, not a gate.
4. **A GPS gap follows the barometer.** Walk into a building until the fix goes stale, then climb or descend a floor or two.
   - **Pass:** the strip changes by about the floors' height (about 3 m a floor), and never shows "Alt unavailable" while the barometer is present.
   - **Evidence:** before and after values, and the number of floors.
5. **The Return control.** Record a short out-and-back with a climb, then open Return.
   - **Pass:** the spoken and shown height difference has the right sign, and is roughly the climb.
6. **Labels and export.** Finish that walk, and open its journal entry or its Records sheet.
   - **Pass:** the highest and lowest labels read about 21 m above the same walk's labels in the pre-main build. Exporting GPX gives `<trkpt><ele>` about 21 m above the `forager:point ele=` value of the same point.
   - **Evidence:** the two label pairs, and one exported point's two values.
7. **Battery, 30 minutes with the map open, with and without the barometer.**
   - **There is no in-app switch for the barometer.** "Without" is the pre-main build `d100a4776`, which reads it only while recording. "With" is this branch's build, which reads it whenever the map is open.
   - So the difference measures the barometer **plus** the sea-level conversion, not the barometer alone.
   - Same phone, same spot, same brightness and screen timeout set to never, no recording, the map left still.
   - Start each run between 80 and 90% charge, unplugged. Run A then B, and if time allows A again.
   - **Evidence:** the battery percentage at the start and at 30 minutes for each run, and `dumpsys batterystats` for the app if available.
   - This is a measurement for the owner, not a pass or fail.

## For the owner

- **The battery comparison can't isolate the barometer** without a dev-only switch, which this dispatch did not ask for. Is the build-against-build comparison in step 7 enough, or should a switch be built on `dev`?
- **The 10 m pass threshold in step 2 is mine.** It needs the owner's figure, or their acceptance.
- **The known gap from Amendment 1 stands:** a file Forager exported before this change, imported again, reads about 21 m low.
