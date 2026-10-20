# UI split, Phase 2: holders, so the hubs can be cut too

Written by the planner (Rowan Waymark), 2026-10-20 UTC, for the owner. Forager RECORD: decision -1434. Follows the
Phase 1 plan (`docs/plans/2026-10-19-ui-split-plan.md`, section 5) and Hazel's survey
(`docs/audits/2026-10-18-ui-split-survey.md` on branch `ui-split-survey`, sections 3, 5, 6.3, 7, 9.4). Line counts are
the planner's reading of pre-main 1ce100c4; the survey's line numbers are at 8c90626f and every dispatch re-reads them.

**The owner's go, verbatim:** "Do phase 2. Our next project touches the maps and such, and wiring even more code into it
when we already plan to decouple it is just adding more work. Let's do this and keep it going, it's worth it for our
maps project." Through the question tool: scope "Settings bundles (Recommended), Hub holders (Recommended), Recording +
Journal engines" (not the big test files); "Promote Phase 1 first (Recommended)"; "Me by adb, you spot-check
(Recommended)".

## 1. What changes, and what doesn't

- **Nothing a user sees changes.** Every PR keeps the app's behaviour; the suite is identical case by case.
- **Unlike Phase 1, these are not pure moves.** Each PR introduces a holder (a small class or bundle that carries what a
  long function remembers) and then cuts the function into files that take the holder. Code is edited, so the Phase 1
  byte-identity proof doesn't apply, and the proof is heavier (section 3).
- **The goal:** the map, the Maps tab, the main screen's engine, the frame, the main screen and the three other engines
  stop being single long functions or classes that every change passes through. The maps project (trail navigation,
  own map tiles) then lands in small files that several helpers can work on at once.

## 2. The work, in order (map project first)

| # | Hub | Now | What the PR series does | Main risk |
|---|---|---|---|---|
| 2.1 | Main screen's settings (`AvailabilityScreen` parameters, 427 lines; 81 test files call it) | 2,039 | Group the ~150 parameters into bundles by concern, as `CompactMapTabInputs.kt` did for the Maps tab (RECORD -739 to -744); one bundle per PR | Test call sites change (shape only); a default value lost in a bundle |
| 2.2 | The map (`SightingsMap`, from `:175`) | 1,466 | A map state holder (map handle, style, camera bookkeeping, cover phase, sizes, the ~20 latest-value holders); then one file per effect group: lifecycle, listeners, style reload, data refresh, fan, forecast cells, navigation view, attribution insets, layout (survey 5.1) | **No test drives the real map** (MapLibre doesn't run under Robolectric); listener registration order and the two `DisposableEffect`s' dispose order; Stage G's value-vs-getter trap |
| 2.3 | The Maps tab body (`CompactMapTab`, from `:99`) | 724 | A `CompactMapLayoutState` holder for the limits the children share (the bubble's top, the cluster's bounds, strip sizes); the children read it (survey 6.3) | Paint and touch order of the nine children stays exactly as written; the 19 real-touch guards run every PR |
| 2.4 | The main screen's engine (`AvailabilityViewModel`) | 1,888 | Sub-states per concern inside the one state value, each with its collaborator: live location, search inputs and runs, map display, planned trips, offline maps and deletes, settings, waypoint navigation (survey 5.5) | 22 UI files read the state; one private log tag used 53 times |
| 2.5 | The frame (`CompactMainScaffold`, one 1,663-line function) | 1,900 | Holders for insets and padding, the dropdown, landscape sizing; then one file per tab call (survey 5.4) | **The real screen-edge code moves**: the suite can't see it (CLAUDE.md); phone screenshots are the check |
| 2.6 | The main screen body | (2.1's file) | A screen holder; Back handlers in one file called at the same position, notices, journal return, drawer host (survey 5.3) | Seven Back handlers whose order matters; the two map memories stay above the tab (`MapCameraMemory`, `MapReturnMemory`), or the map loses its camera on every tab change |
| 2.7 | The recording engine (`TrackRecordingViewModel`) | 1,339 | Collaborators for pending deletes, lists, the way home, polling, take-up | **The poll-loop rule:** wherever a loop moves, `stopRecording()` cancels it synchronously in the ViewModel's scope (CLAUDE.md; survey 7.4) |
| 2.8 | The Journal engines (`MushroomLogViewModel`, `CartographyViewModel`) | 1,232 / 802 | Collaborators per concern | Every collaborator shares the one `editingEntryMutex` and never calls back while holding it (survey section 4) |

**Freeze:** while a hub's series is open, no other dispatch touches that file. The maps project's first dispatches wait
for 2.2 to 2.4, which is the point of doing this first.

**Parallel:** different hubs are different files, so up to three series run at once, for example 2.1, 2.2 and 2.7.
2.3 waits for 2.1 (both touch what the screen passes the Maps tab); 2.5 and 2.6 come after 2.1. S22 checks and merges
stay one at a time with the planner.

## 3. Every PR's proof (the Stage G method, plus a phone)

1. **Verify-first by message** before any edit: the holder's shape (what it carries, which values are read and which
   are written), the files it produces, and every captured state named. **Every written state gets a setter, every
   read a getter**, never a copied value: Stage G caught a value parameter that would have saved the fullscreen
   preference inverted (`docs/audits/2026-09-26-split-stage-g-completion-report.md`).
2. **The diff is explained line by line:** moved lines, plus the holder and its call sites, and nothing else.
3. **The full suite on CI, before and after, case by case** from the JUnit XML, with the stale-result guards
   (CLAUDE.md). No test file changes except, in 2.1 only, call sites changed to pass bundles.
4. **The touch guards** (the 19 real-touch classes, by concrete class) on every PR that touches the Maps tab, the frame
   or the main screen.
5. **The dex report** before and after, with every change traced.
6. **A code review by Sorrel** (the reviewer, on a different model from the coder) before the PR merges, because these
   are edits, not moves.
7. **The S22 launch check**, as in Phase 1.
8. **The phone screenshot pass** (the owner's choice: "Me by adb, you spot-check"): for any PR that touches layout,
   insets or the map, the planner takes S22 screenshots of a fixed set of screens in portrait and landscape before and
   after (section 4), compares them, and brings the owner any that differ plus the final set. Live data (location,
   tiles, the sundown line) makes some differences expected; each is named.

## 4. Before the first Phase 2 PR

- **A screenshot tool** (one Hazel task): a script beside `scripts/s22-launch-check.sh` that drives the S22 by adb
  through a fixed list of screens (Maps tab, fullscreen, search open, a bubble, the navigation card, Journal, List,
  Seasonal, Tools drawer, Settings), in portrait and landscape, and saves them to a folder outside every repository.
  The owner's screenshots stay off GitHub.
- **A fresh look at each hub** in its own verify-first, since the survey is two days and 24 merges old.

## 5. Gates and release

- Phase 2 PRs are built and proven on branches now. **None merges into pre-main until Phase 1 has been promoted to
  main** (the owner's choice). Each then merges under the standing word once proven, reviewed and S22-checked.
- Phase 2 reaches main by its own promotion, with Elder's audit.
- Any difference in the suite, the touch guards, a screenshot that can't be explained, or a review finding stops that PR
  and comes back as a finding. Nothing is absorbed.

## 6. Not verified

- The holder shapes: each is proposed in its verify-first, read against the code of its day.
- Effort: roughly two to four PRs per hub and 20 to 30 in all (planner's estimate). Each takes longer than a Phase 1
  move because of the review and the phone pass.
- Whether MapLibre's listener and dispose order survive a cut of `SightingsMap`: only the phone can show it, through
  the screenshot pass and use.
