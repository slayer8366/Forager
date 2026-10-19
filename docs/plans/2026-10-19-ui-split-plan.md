# UI split plan: one part per file, so several helpers can work at once

Written by the planner (Rowan Waymark), 2026-10-19, for the owner. Forager RECORD: direction -1323, survey intent -1324,
survey outcome -1326, plan asked for in -1374. A plan only: nothing is dispatched or built until the owner says go.

**The goal, in the owner's words** (-1323 and the survey's Addendum 1): "At some point we need to decouple the UI into a
couple hundred lines per file", and "Ideally, every part should have its own file. UI fixes can have several agents
attacking their own changes. Updates will be easier to manage when we need to make it work at a certain time".

**Source.** Hazel's survey, `docs/audits/2026-10-18-ui-split-survey.md` on branch `ui-split-survey` (81db63cb), read in
full by the planner on 2026-10-19 against pre-main 8c90626f. Line numbers below are the survey's, at 8c90626f; every
dispatch re-reads them on the pre-main of its day. Pre-main is b2d2f4d5 as this is written.

---

## 1. What the app gains, and what it doesn't

- **Nothing a user sees changes.** Every step in Phase 1 moves code between files without changing a line of what it
  does. The proof for each step is described in section 4.
- **What changes is how work is shared.** Today a fix to the compass strip, the record pill and the bubble all edit
  the same 2,000-line file, so two helpers can't work on them at once without colliding. After Phase 1, most controls,
  cards and sheets have their own file (the survey's list, section 6.1).
- **What Phase 1 can't reach:** four "hub" pieces that every change still passes through, because their parts share
  what the screen remembers: the map's own body, the Maps tab's body, the frame around the tabs, and the main screen
  (with its Back handling and 150 settings passed in). And the four "engines" behind the screens (ViewModels), which
  are each one class. Splitting those is a design change, Phase 2, and each is its own decision for the owner.

## 2. Phase 1: pure moves (no behaviour change)

Twelve or so pull requests, in the survey's order of risk (section 8). Each moves one file's parts into new files in
the same folder. No test changes, because tests find code by folder, not by file.

| # | What moves | From | Size | Risk |
|---|---|---|---|---|
| 1 | The navigation card's wording and rules | NavigationHud.kt | ~570 lines | Lowest: plain rules, no screen code |
| 2 | The map's camera, credit and location-dot helpers | SightingsMap.kt | ~420 | Low; one small composable with its own touch test |
| 3 | The map's layers and data helpers | SightingsMap.kt | ~960 | Low, but the widest set of names made shared |
| 4 | The details sheet's parts | RecordDetailsSheet.kt | five files | Low |
| 5 | Tools drawer and Settings pages | AvailabilitySettingsUi.kt | four files | Low |
| 6 | The map bubble; the map doorway (two PRs) | MapBubble.kt, MapSlot.kt | three files each | Low |
| 7 | The shared look of map chrome | MapChrome.kt | six files | Medium: start-up order checked by reading |
| 8 | The compass strip, pill, "+" chooser | AvailabilityMapControlsUi.kt | several files | Medium: touch order over the map |
| 9 | The Maps tab's nine children (two or three PRs) | AvailabilityCompactMapUi.kt | ~1,400 | Medium: touch order; every coordinate-touch test run each time |
| 10 | The rest: search bar, icon bar, Download Maps, Journal files | several | several | Low to medium |

**Every PR carries the same proof** (the method that safely cut the main screen from 5,674 to 2,229 lines on
2026-09-26, Stage F, `docs/audits/2026-09-26-split-stage-f-completion-report.md`):
1. the diff shows only moved lines, apart from a listed set of `private` → `internal` changes;
2. the same set of declarations before and after;
3. each moved block byte-identical to its source, with the check first shown to fail on a deliberate one-line error;
4. the full test suite before and after, case by case, with the guards against stale results (CLAUDE.md);
5. **the S22 launch check** (`scripts/s22-launch-check.sh`), because a move changes which compiled class holds a
   function, and only a phone sees whether it still loads (the 2026-10-08 launch crash, RECORD -739 to -744, passed
   4,454 tests and CI and crashed on every phone);
6. the CI skip list and the compiled-size baseline unchanged, stated.

**What the owner is asked for in Phase 1:** the S22 plugged into the laptop for each launch check (the planner runs it
by adb), and a merge word per PR, or a standing word as -1317 gave for the launch release.

## 3. Running helpers in parallel

- **Two helpers at a time, one per folder:** one on `ui/map` (PRs 2, 3, 6, 7), one on `ui/availability` and `ui/log`
  (PRs 1, 4, 5, 8, 9, 10). Two moves in the same file never run at once.
- Builds are on CI, so the laptop carries no Gradle load. The S22 check is the one shared step, and the planner runs
  those one at a time.
- Each PR merges into pre-main with the next one merging pre-main in first (merge, never rebase).
- **Freeze rule while a file is being split:** no other change to that file is dispatched until its PR merges. Feature
  work that needs the file waits, or goes first and the split re-reads.

## 4. Gates and release

- Each PR: green CI on its head, the proof in section 2, the S22 launch check, merged into pre-main.
- The whole series reaches main only through a promotion: CI on pre-main, Elder's audit, the owner's approval (-1086).
  It lands with the next release, as the owner said (-1324: "It just won't land until next release is all").
- A move that shows **any** change in the suite, the declaration set or the phone check stops, and comes back as a
  finding. Nothing is absorbed.

## 5. Phase 2: decisions for later (not asked now)

Each is a design change, carries the Stage G method as well (every shared value passed as a getter and setter,
because Stage G caught a move that would have saved the fullscreen preference inverted), and needs a phone pass for
insets. The survey's four open questions:
1. **State holders** for the map body, the Maps tab body, the frame and the main screen, so they can be cut further.
2. **Grouping the main screen's 150 settings** into a few bundles. This changes what 81 test files call; the pattern
   exists already (`CompactMapTabInputs.kt`).
3. **Splitting the big test classes**, which moves names on the CI skip list.
4. **Removing four pieces of dead code** the survey found (no caller anywhere): `navigationChromeTransitions`,
   `ReportItem`/`ReportItemsSection`, `MapModePicker`, `MapFloatingIconButton`. Phase 1 carries them unchanged.

**The planner's recommendation:** decide Phase 2 after Phase 1 lands and the owner has seen how much parallel work it
already allows. Dead-code removal (4) is small and could be its own PR in Phase 1 if the owner prefers.

## 6. Risks named by the survey, and where each is guarded

| Risk | Guard |
|---|---|
| A move silently changes behaviour | The six proofs in section 2 |
| A touch stops reaching the map | Pure moves keep call order; the 19 coordinate-touch test classes run in every `ui/availability` PR |
| The phone refuses to load a moved class | The S22 launch check on every PR |
| The map loses its camera across tabs | The two "memories" stay where they are remembered (`AvailabilityScreen.kt:983`, `:986`); no Phase 1 PR moves them |
| A test hangs on the recording poll loop | No Phase 1 PR touches `TrackRecordingViewModel` |
| Real screen insets | No Phase 1 PR touches inset code; Phase 2 does, and needs a phone |

## 7. Not verified

- Whether each proposed file compiles with only the listed changes: checked by the survey by reading, not built.
- Whether moved code passes the phone's loader: only each PR's S22 check can say.
- Whether saved screen state survives an app update after a move (the survey's 9.3): a phone check on the upgrade
  path at the promotion would answer it, if the owner wants one.
