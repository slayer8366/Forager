# Dispatch 2026-11-04-07: The map type chips' outlines, and the night chip in rust red (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2090 (intent), with the design ruled in -2089. It builds on -1771 (the 10-28 outline), -2069 and -2070 (rust red at night is for night vision; the palette is a feature), and -2078 and -2079 (the full undo: "then we can start the chip colors properly"). The number -06 isn't reused: that dispatch was deleted at the owner's word (32eb6f93f, -2088).

Every fact below was read at pre-main 9b58c66a4 while this was written, and is a premise to re-check, with file and line.

## Authorisation
**Not given yet.** The owner's words were "Go ahead and write it", which is a go to write this, not to start a coder. Hazel starts only on the owner's yes to "Start Hazel on dispatch 2026-11-04-07 now?". A design answer is not a go.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules for every item
- Verify first: Phase 1 reads the code and reports to the planner by message, then stops. Phase 2 builds, on the planner's go.
- Tests first, shown failing, then revert checks on a throwaway CI branch. Full CI green on the last real commit.
- CI only: no Gradle on the laptop (coder-profile.md:30). Scratch goes in `/mnt/work/scratch/<branch>/`, never `/tmp` (:31). That disk had about 650 MB free at writing.
- Don't touch the phone. Never read, name or quote the Restricted Object. Our own map is the "Trail Map".
- New words a person would see go back to the planner before they're built.
- Stop and report, rather than choosing, on anything the owner would see that this doesn't rule.

## Change: what the user sees

| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Tests and evidence |
| **C** | Branch, build and report |

- **A1** (2026-11-04, -2089). **The owner's words,** verbatim, sent together with two screenshots of the map type panel (filed at `docs/audits/assets/2026-11-04-07-chip-outlines/`, night-0911.png and day-0930.png, on records-after-173):
  - "Hi Rowan. Let's design a dark outline for the map style chips in day mode. And have the night mode use a white outline as shown" (the night screenshot);
  - "Have the day mode be the same outline as shown here. But keep it the same color as it is now, not in the screenshot" (the day screenshot).
  - Asked four questions together, the owner chose: "Pale #F6DFC8" (the day fill); "Rust red #A2612C (Recommended)" (the night fill; the option read "As in your night screenshot, with its white label"); "As shown, #EDE3D0" (the night "white"); "No, normal chip only" (the grey chip).
  - Then: "Rust is a red color. That's meant for night vision".
- **A2. Day** (the app's Night Mode off). The normal selected chip gets a **1 dp outline in Bark #3B2E24**, the day theme's `onSurface` (Theme.kt:44; Color.kt:9). That is the outline in the day screenshot.
  - Its fill stays **#F6DFC8**, `secondaryContainer` (Theme.kt:28; Color.kt:87), and its label stays #3A2008 (Theme.kt:29; Color.kt:89). The day screenshot's rust fill and white label are not built: "not in the screenshot".
- **A3. Night.** The normal selected chip is filled **rust red #A2612C** (`MushroomDeep`, Color.kt:82), with a **white #FFFFFF label** (`PureWhite`, Color.kt:64) and a **1 dp outline in #EDE3D0**, the night theme's `onSurface` (Theme.kt:101; `Cream`, Color.kt:8). That is the night screenshot.
  - **Why rust red, in the owner's words:** "Rust is a red color. That's meant for night vision"; "Brown reduces that ease at night" (-2069). It is a night-vision feature, not a style choice. No test, contrast figure or review swaps it.
- **A4. Unchanged, both themes:**
  - the two unselected chips keep Material's own outline, `outlineVariant`: #D5CEBF by day, #444444 at night (Color.kt:158, :160);
  - the grey "chosen, but offline" chip keeps its `outlineVariant` fill and `onSurface` label (MapModeChips.kt:99-103), with no outline: "No, normal chip only".
- **A5. Where it shows:** the map type panel, opened from Layers on the Maps tab (CompactMapPickers.kt:96) and on a journal entry's map (CartographyEntryReportScreen.kt:648). Both reach `MapModeChipRow` through `MapTypePanel` (MapTypePanel.kt:117).

## Scope boundary
- **C1. May touch:**
  - a new branch, `map-type-chip-outlines`, cut from pre-main 9b58c66a4 or later (the name was free on origin at writing);
  - `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapModeChips.kt` (116 lines);
  - chip tests under `app/src/test/java/com/zynergylabs/forager/app/ui/map/`;
  - your completion report.

  Build it as new functions, not conditions threaded into the row (CLAUDE.md, Building).
- **C2. Must not touch:**
  - Theme.kt and Color.kt (no new colour or token; every colour above exists);
  - `chosenMapModeStandsAside`, the grey chip's colours, the unselected chips;
  - the style-load log line;
  - pre-main, main, and the branches delete-amendment-4, palette-rule and revert-chip-colours (the owner: "We are not using delete-amendment-4. This is a fresh start");
  - RECORD.md and docs/audits/README.md (the planner writes those);
  - the phone.
- **C3. Already ruled out, so don't build these:** an outline on the grey chip; a Cream day fill; a pure white outline; a dark brown night fill; reviving 22ae67021 or 64aacd969, or reverting their reverts. They are reference only: they carry a day fill the owner didn't choose (Cream, and red). If you think any of this is wrong, say why instead of building it.

## Baseline
pre-main 9b58c66a4. Its chip code is Material's default in both themes, with no selected outline: 3ffced9c0's code, whose own CI run 41866101093 succeeded. Run 41863141684, on the same chip code, counted 6,536 tests, 0 failures, 24 skipped (RECORD -2083).

## Verify first (Phase 1: report these, then stop)
1. `MapModeChipRow` (MapModeChips.kt:82) draws the chips. Its `FilterChip` (:107-113) passes `colors` but no `border`. Name every place the change shows.
2. What `FilterChip` draws by default in material3 1.5.0-alpha26 (libs.versions.toml:19), read from the library. The planner read `FilterChipTokens.FlatUnselectedOutlineColor` as `outlineVariant` from the cached AAR. Confirm it, and the default outline widths, selected and unselected.
3. Night is `LocalForagerDarkTheme` true (Theme.kt:177), provided as `shownDark` inside `NightModeBlend` (:151-152). Confirm it follows the app's Night Mode on both entry points, and say what the chip shows during the night-mode fade. Don't use `isSystemInDarkTheme`.
4. 22ae67021's `selectedChipBorder` and `darkSelectedChipColors` drew exactly A3's night. Say what you reuse from them and what you leave.
5. Your mechanism prediction and test plan.

## Predictions
- **Outcome (planner):**
  - day: the selected chip draws a 1 dp #3B2E24 outline around its unchanged #F6DFC8 fill;
  - night: it draws #A2612C, with a #FFFFFF label and a 1 dp #EDE3D0 outline;
  - the unselected chips and the grey chip draw exactly as on 9b58c66a4, both themes, both entry points.
  - Contrast, from the solid colours: day outline 10.18:1 on the fill and 11.42:1 on the panel colour #F4EFE2; night outline 3.85:1 on the rust red and 12.80:1 on the panel #202020; night label 4.90:1 on the rust red. The panel is at 80% over the map (MapChrome.kt:54, :66, :69), so report what you measure there too.
- **Mechanism (coder):** in your Phase 1 report.

## Evidence required
- **B1. Tests through both real entry points.** Open Layers on the Maps tab and on a journal entry's map, each by a real coordinate touch, inside `ForagerTheme`. Read fill, label and outline from the drawn pixels. The removed `MapTypePanelSelectedChipTest` (at 22ae67021) has that harness (coordinate touch, `ForagerTheme(darkTheme = dark)`, pixels) and outline cases on both entry points. Reuse them or say why not. Cover:
  - day: the outline drawn, the fill unchanged;
  - night: fill, label and outline;
  - the unselected chips, both themes;
  - the grey chip, both themes, with no signal and fallen back (as `MapTypePanelOfflineChipTest` drives them).
- **B2. Fail first.** The tests go in their own commit and run on CI. The day-outline and night cases must fail for the predicted reason. The unchanged cases pass by construction; say so, and B3 proves they bite.
- **B3. Revert checks,** each a one-line edit on a throwaway CI branch, never merged:
  - r1, no outline: the day and night outline cases fail;
  - r2, the night colours back to Material's default: the night fill and label cases fail;
  - r3, the outline on the grey chip: the grey cases fail.

  For each: no compile errors in the build log; only failures this edit could cause; the file restored from a saved copy, not from git; the forward change still present afterwards (CLAUDE.md, Testing).
- **B4. Full CI green** on the last real commit, with counts read from the JUnit XML.

## Device only (the owner's to look at, not the coder's)
The planner doesn't change the phone's theme for a check (-2071, -2072).
1. **Day, Maps tab, Layers.** Pass: the selected chip is pale with a thin dark outline, and the other two look as now. If the outline is missing, that entry point wasn't reached.
2. **Day, a journal entry's map, Layers.** Pass: the same.
3. **Night, only if the owner chooses to look.** Pass: the selected chip is rust red with a white label and a thin cream outline. Dark brown there means the night branch wasn't reached.

## Stops
Stop and report on:
- a choice the owner would see that A doesn't rule;
- a wrong premise;
- needing to touch Theme.kt, Color.kt, main or a release build;
- a test having to be weakened;
- two failed attempts at one symptom.

## Finish line
A PR from `map-type-chip-outlines` into pre-main, green on CI at its last real commit, with no `[skip ci]` on the head so the PR's own checks run. It merges only on the owner's word.

## Final message
Your completion report at `docs/audits/2026-11-04-07-chip-outlines-completion-report.md` on the branch. It gives:
- commits and the PR;
- the failing-first evidence;
- each revert check and the failure it named;
- the CI counts;
- the contrast as measured;
- which nearby files or apps were checked for convention ("none checked" is allowed, a blank is not);
- what was not verified.

The planner files your Phase 1 report and the report's summary beside this dispatch, unedited.
