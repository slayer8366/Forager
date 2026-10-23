# Follow-up review of PR #380: Amendments 2 and 3 (H1/M1/L1/M2 fixes; the grey chip)

Reviewer: Sorrel, on Claude Fable 5.1. Asked by Rowan (planner). Written 2026-10-23, about 02:30 PDT (09:30 UTC).
Coder: Hazel (Claude Opus). PR #380, branch `night-icon-set` at `8028cfa6a` (`origin/night-icon-set`, the worktree
clean at the same commit), into `pre-main`. The first review is `docs/audits/2026-10-22-review-pr380.md` at
`73630eb15`; this one covers only what landed after it.

Review only: no code changed, nothing merged, nothing posted to GitHub, nothing deployed. Hazel's worktree
(`~/.cache/forager-wt/night-icon-set`) and the revert worktrees were read, not written. The one script I ran is a
JUnit XML summer over downloaded CI artifacts, in my scratchpad.

Sources read: dispatch 2026-10-22-06 with Amendments 1 to 3 and RECORD -1567 (`origin/records-after-173`); the nine
commits named below, each in full; `NightImageSwitch.kt`, `NightImageApply.kt`, `MapModeChips.kt`,
`NoSignalTrailMap.kt`, `OwnStreetFallback.kt`, the `MapLayersSheet.kt` and `CartographyEntryReportScreen.kt` diffs;
`NightImageSwitchTest.kt`, `StyleExpressionEval.kt`, `MapLayersSheetOfflineChipTest.kt`; the served v1 and v2 style
JSON (`server/pmtiles-worker/src/street-style-v{1,2}.json`); Hazel's completion report, Amendment 2 and 3 sections
and the audits index rows; the PR body; MapLibre Native `match.cpp` and `coercion.cpp` at `android-v13.5.0` (the
copies I fetched for the first review); the seven revert branches by `git diff` against their bases; and the CI runs
39895598132 (R6b), 39895603297 (R8b), 39905102839 (R9), 39913056473 (R12) and 39913054537 (green), by their console
logs and, for four of them, the `unit-test-report` artifact.

## Verdict

**Mergeable into pre-main.** H1, M1, L1 and M2 are closed, each by code I read and by evidence that could have
failed: the walk keeps every zoom `step` on top and changes only output positions (checked index by index against
all ten v1 and v2 references), the switched count now comes from reading each property back, and the owner's S22 run
on 1.0.3833 read "6 switched, 0 refused" online, "4 switched, 0 refused" offline, with no `Error setting` line and
dark town dots below zoom 8. The grey chip is decided by the map's own `drawsFromDownloads`, with the fallback's own
Compose state for the Trail case, and the tests read the drawn pixel through the real `MapLayersSheet` inside the
app's own theme root. The revert checks I spot-checked each built with zero Kotlin compile errors, failed only on
the assertion their one-line edit reaches, with a message specific to that edit, and the forward lines are all on
the branch head. Three Low notes remain; none blocks.

Device evidence quoted below is as relayed by Rowan (RECORD -1567 and the dispatch's Amendment 3). I did not see the
logcat or the phone; I say "relayed" where I lean on it.

## Findings

Severity: High (blocks merge), Medium (merge, but gate or fix before promotion), Low (note).

| # | Severity | Where | Finding |
|---|---|---|---|
| L4 | Low | `MapModeChips.kt:52-58`, `:80-82`; `NoSignalTrailMap.kt:61-65`; `SightingsMap.kt:590` | The chip shares the map's pure rule (`drawsFromDownloads`) but not the map's reader of it. The map calls `rememberDrawsFromDownloads(useOfflineTiles, basemap, fallback)`, which reads `LocalNoSignal` (returning `useOfflineTiles` when none is provided) and `fallback.ownStreet()`. The chip re-implements that glue: its own `rememberNoSignal()` (false when none is provided) and its own `fallback.ownStreet()` read, then the pure function. Equivalent today: with no provider the chip computes `drawsFromDownloads(offlineTiles, basemap, false, trailMapDraws) = offlineTiles`, which is the map's early return. But "single source of truth" is one function call away, and a future change to the map's reader (a different default, a different flow) would not reach the chip. Not a defect; a one-line tidy for a later PR. |
| L5 | Low | `CartographyEntryReportScreen.kt:631`, `:659`; `MapLayersSheet.kt`, `offlineTiles` | The offline-tiles grey has no reachable production path: the only caller that passes `offlineTiles` is the entry map, which hides its Layers button while `useOfflineTiles` is on (`mapModePickerEnabled = !useOfflineTiles`), and the sheet is modal, so `offlineTiles` is always `false` whenever the sheet is open. Hazel stated this plainly ("wired and tested but cannot be seen on a phone") and the owner ruled "Leave it". Recorded here so the next reader of `MapLayersSheetOfflineChipTest:146-159` knows that test holds a rule the phone cannot show today, by the owner's choice, not an oversight. |
| L6 | Low | `MapModeChips.kt:41-50`; `BasemapStyles.kt`, `ownStreetStyleSourceFor` (`:265`, read in the first review); test `MapLayersSheetOfflineChipTest:193-197` | One substitution the owner's rulings do not name: Topographical at night after the -1513 fallback draws the topo raster over OpenStreetMap instead of over Trail Map (`OwnStreetFallback.kt:17-19`). The Topo chip stays normal, and the test pins that. Topographical is still what is shown, so normal is the sensible reading; I name it only so "grey = chosen, but something else is shown" is not later read as covering the underlay. Not a question that needs the owner before merge. |
| L7 | Low | `NightImageSwitch.kt:237`; `NightImageApply.kt:67` | `added` is the count handed to `Style.addImages`, not read back; the SDK's `addImages` returns nothing. The switched count is now evidence (M1); the added count is still a count of calls. A `style.getImage(nightImageName(name))` read after the add would make the first figure as good as the second, for a few lines. The 2026-10-22-05 diagnosis already used `getImage` in the callback. The S22's dark icons on 1.0.3833 show the adds took on that phone; this is about the log line as evidence, not the behaviour. |

Earlier findings, status:

| # | Status | Evidence |
|---|---|---|
| H1 | **Closed.** | The walk (`NightImageSwitch.kt:148-167`) recurses into `match`/`case`/`step` outputs only (`isOutput`, `:134-142`) and wraps a computed output, never a value whole. `places_locality` becomes `["step", ["zoom"], ["case", …, "night/capital", "night/townspot"], 8, ""]`, pinned by `NightImageSwitchTest:143-148`; `zoomRuleViolations` (`StyleExpressionEval.kt:87-101`) holds the parser's rule on every switched value at v1 and v2 (`NightImageSwitchTest:124-131`). Device, relayed: no `Error setting` line in about 210k lines of logcat on 1.0.3833; town dots dark below zoom 8. |
| M1 | **Closed.** | `NightImageTarget.readImageReference` (`NightImageSwitch.kt:191`), on the phone `SymbolLayer.iconImage` / `FillLayer.fillPattern` as text (`NightImageApply.kt:86-93`); a reference counts only if the text read back holds `night/`, else it is named under `refused` (`:234-235`, `:202-207`). The fake that refuses `places_locality` yields `5 switched, 1 refused: [places_locality/icon-image]` (`NightImageSwitchTest:168-184`). Device, relayed: "6 image references switched, 0 refused by MapLibre" online, "4 switched, 0 refused" offline, which is also the first on-phone confirmation that the getter returns text the prefix check can read, the thing the first review listed as unverified. |
| L1 | **Closed, and better than "kept".** | In the new form `pois`'s `["get", "kind"]` sits in an output position of the wrapper, which itself sits in an output position of the day `match`. MapLibre parses a `match`'s outputs at the expected type when one is given (`outputType` from `ctx.getExpected()` at `match.cpp:245-249`, each output parsed with it at `:283`), and `icon-image` is parsed at `image`, so the `get` takes an image coercion exactly as by day; the wrapper's input is parsed at `Value` (`match.cpp:295`), so no string assertion is inserted anywhere; and a non-string input at run time goes to `otherwise` (`match.cpp:82-95`), where the `get` is coerced (`coercion.cpp:147-149`). The typing difference the first review found is gone, not moved. Not device-tested, as Hazel says; by construction. |
| M2 | **Closed.** | The widened checklist (report, "Device checklist, widened") has the four items: full-logcat read naming `Error setting property`, place dots below 8, offline night with a region in aeroplane mode, Topographical night at 9 to 9.5. The owner ran it on 1.0.3833 (RECORD -1567). The regions on the S22 held v1, so the offline step covered v1's four references and not v2's six; v2 offline is covered by the unit test (`NightImageSwitchTest:107-121` models the offline load identically, since the switch reads only the style JSON) and by the online v2 run, not by a phone. I would not hold the PR for it. |
| L2, L3 | Unchanged. | Rowan accepted L2; L3 was not a defect. |

## What was checked, in the order asked

### 1. The expression walk, on every v1 and v2 reference

I printed every `icon-image`, `fill-pattern`, `line-pattern` and `background-pattern` value in both served styles
(there are no `line-pattern` or `background-pattern` values; v2 has six references, v1 four), and walked the index
arithmetic of `isOutput` (`NightImageSwitch.kt:134-142`) by hand against each:

- **`match`** (`index >= 3 && (odd || last)`): v2 `pois` has 9 elements; outputs at 3, 5, 7 and the `otherwise` at
  8; the labels at 2 (an array), 4 (`"trailhead"`) and 6 (`"viewpoint"`) are untouched, so a label that is also a
  sprite name is never rewritten. `roads_shields` (7 elements): outputs 3, 5 and 6, labels 2 and 4. v1 `pois`
  (5 elements): output 3, `otherwise` 4.
- **`case`** (`index >= 2 && (even || last)`): the 4-element `case` inside `places_locality`: outputs 2 and 3; the
  condition at 1 untouched.
- **`step`** (`index >= 2 && even`): `places_locality`: input `["zoom"]` at 1 untouched, outputs at 2 (the `case`,
  recursed into) and 4 (`""`, returned as is at `:150`); the stop `8` at 3 untouched.
- **Literals**: `tree_dark_irregular`, `tufts`, `arrow` take their night names (`:99-106`).

Every element of every day value is either kept or, at an output position, replaced; nothing is dropped, and
`NightImageSwitchTest:134-165` pins the exact result for the three expression references at v2. The v1 shapes are
the same operators at the same indexes; `:200-210` asserts what each v1 reference draws through the evaluator, and
`:124-131` holds the zoom rule at both versions. The bug test (`:107-121`) samples fourteen features including both
`places_locality` outputs at zoom 6, so H1's case is now in the sample that could fail it.

`zoomRuleViolations` is stricter than MapLibre (no `let`/`coalesce` look-through); no served reference uses either.
A future value using `coalesce`, `let` or `image` at an output position would be wrapped whole and could fail the
parser again; the zoom test would catch a zoom under it at the served styles, and on a phone the read-back now names
it. That is the right failure path.

### 2. The read-back count

`applyNightImages` (`:215-238`) counts `switched` only when `readImageReference` returns text containing `night/`;
`refused` collects the rest; `left` keeps the plan's own reasons plus "no such layer" and a thrown `RuntimeException`.
`switched + refused + left-from-setting = plan.switches.size`, and the log line carries all three with the names
(`:202-207`). Day names never contain `night/` (checked: no `night/` in either style). The prefix test is a substring
test on the SDK's `Expression.toString()` or the constant's `toString()`; on the phone it read six of six and four of
four (relayed), which is the sample that could have failed it. L7 above is the one count the line still does not
read back.

### 3. The chip's rule, and its edge cases

`chosenMapModeStandsAside` (`MapModeChips.kt:41-50`):

```
fromDownloads = drawsFromDownloads(offlineTiles, basemap, noSignal, trailMapDraws)
Trail chosen   → grey iff !trailMapDraws && !fromDownloads
Topo/OSM chosen → grey iff fromDownloads
```

against the map's `drawsFromDownloads` (`NoSignalTrailMap.kt:45-46`):
`useOfflineTiles || (noSignal && (basemap != TRAIL || !trailMapDraws))`.

| Chosen | Signal | Offline tiles | Fallen back | Map draws (`mapStyleSourceFor`, read in the first review) | Chip | Test |
|---|---|---|---|---|---|---|
| Topo/OSM | on | off | either | the chosen map (Topo night: over Trail, or over OSM after a fallback; L6) | normal | `:118-121`, `:193-197` |
| Topo/OSM | off | off | either | Trail Map from the downloads | grey | `:123-128`, `:137-143` |
| Topo/OSM | on | on | either | Trail Map from the downloads | grey (L5: unreachable on a phone) | `:146-159` |
| Trail | on | off | no | Trail Map | normal | `:171`, `:208-215` |
| Trail | on | off | yes | OpenStreetMap's raster | grey | `:167-181` |
| Trail | off | off | yes | Trail Map from the downloads (-1554 Amendment 3 rule) | normal | `:184-190` |
| Trail | off | off | no | Trail Map | normal | `:208-215` |
| Trail | either | on | either | Trail Map from the downloads | normal | `:200-205` |

Every row agrees with what the map draws, and every row has a test. The signal state is `LocalNoSignal`, which is
the post-hysteresis flow the map reads (`NoSignalTrailMap.kt:53`, provided at `MainActivity.kt:363`), so the chip
and the map flip on the same tick. The fallback flag is Compose state (`OwnStreetFallback.kt:29`), so the chip
follows it without a recomposition trigger of its own. The stored choice is never written: every test asserts
`choices` empty, and the tap test (`:218-225`) shows a tap under no signal still writes one (a semantic click, which
is right for a wiring claim). L4 is the one structural note.

Both `MapLayersSheet` call sites (`CompactMapPickers.kt:99`, `CartographyEntryReportScreen.kt:649`) are inside the
theme root, so both get the shared state; the Maps tab leaves `offlineTiles` at its default, and I found no
`MapRenderMode` built with `useOfflineTiles = true` outside the entry map (grep of `app/src/main`).

### 4. The tests read the drawn colour through the real sheet

`MapLayersSheetOfflineChipTest` composes `MapLayersSheet` itself, with `MAPS_TAB_OVERLAYS` and a default
`MapLayersState`, inside `ForagerThemeWithNoSignal` (`:76-96`), which is `MainActivity`'s root; it drives the real
`OwnStreetFallback.onStyleLoadFailed` with a `MapStyleRequest` for `STREET_STYLE_URL` online (`:162-164`), the call
`MapListeners` makes. `drawnFill` (`:99-104`) is `captureToImage()` on the chip's node, pixel at 3 dp in, half way
down, so inside the fill and clear of the corner radius and the label; the recorded fill is read beside it. That the
pixel read sees the chip and not the sheet is shown by the failing-first and revert runs: they read `#6b421a`
(`secondaryContainer` dark, `Theme.kt:77`) where `#444444` (`OutlineVariantDark`, `Color.kt:160`) was expected, and
the inverse for R9, so both colours are the chip's own. Both themes are covered for the no-signal, offline-tiles and
fallback cases. No existing test in `MapLayersSheetTest` was touched (`git log` on that file since the base is empty),
and the chip row moved with no change to its selected/label/onClick wiring.

### 5. Revert runs, spot-checked (four of the seven), plus the green run

Each revert branch is one line off its stated base, by `git diff` against the merge base: R6b, R7b and R8b from
`fd08a531d`; R9 and R10 from `f67877619`; R11 from `004ebd064`; R12 from `cde423dcd`. Each run was a
`workflow_dispatch` on its own branch, "Build the debug APK" `BUILD SUCCESSFUL`, and a strict count of Kotlin
`e: ` lines in each console log is **0** (R6b, R8b, R9, R12). The failure messages are from each run's
`unit-test-report` artifact (R6b, R9, R12) or the console (R8b):

| Run | Edit | Tests | Failed, and the message |
|---|---|---|---|
| R6b 39895598132 | `step` out of `IMAGE_OUTPUT_OPERATORS` | 26, 5 failed | bug test: `5 image references switched, 1 refused by MapLibre: [places_locality/icon-image]`; zoom rule: `v1 places_locality/icon-image … "zoom" used under a top-level "match"`; structure: `["match",["step",["zoom"],…` where `["step",["zoom"],["case",…,"night/capital","night/townspot"],8,""]`; v1 test: `3 … switched, 1 refused`; missing-copy: switched `4` where `5` |
| R8b 39895603297 | read-back replaced by `switched++` | 26, 1 failed | the refused test at `NightImageSwitchTest.kt:181` (`assertEquals(5, outcome.switched)`), nothing else |
| R9 39905102839 | Trail exclusion dropped | 29, 1 failed | `a chosen Trail chip is unchanged with no signal`: drawn `#444444` where `#6b421a` |
| R12 39913056473 | Trail branch set to `false` | 42, 1 failed | `once Trail Map has fallen back …`: `dark, fallen back: Trail drawn grey` expected `#444444`, was `#6b421a` |

Each message is one the edit alone produces: R6b's five are exactly a whole-wrapped zoom step seen from five
tests; R8b's one is exactly the count no longer reading back; R9 and R12 each fail the one test their line decides.
The forward lines are on `origin/night-icon-set` at `8028cfa6a` (`git grep`: `setOf("match", "case", "step")`,
`held.contains(NIGHT_IMAGE_PREFIX)`, `!trailMapDraws && !fromDownloads`). R7b, R10 and R11 I did not open; Hazel's
table for them is consistent with their edits.

**Green run 39913054537** (`pull_request`, head `cde423dcd`, all five jobs green): I summed its 709 JUnit XML files
myself: **5295 tests, 0 failures, 0 errors, 24 skipped**, matching the job's own summary table and the report.
Against the first review's 5282: +4 (Amendment 2's reworked `NightImageSwitchTest`), +4, +2 and +3 (the chip
tests), as the report's running counts say.

### 6. CLAUDE.md

- **File sizes:** `MapModeChips.kt` 106, `NightImageSwitch.kt` 238, `NightImageApply.kt` 112, `StyleExpressionEval.kt`
  101, `MapLayersSheetOfflineChipTest.kt` 226, `NightImageSwitchTest.kt` 268; `MapLayersSheet.kt` 544 (smaller than
  before); `CartographyEntryReportScreen.kt` 784 with a one-line addition, under the modular-code skill's 800.
- **No silent fallbacks:** every path that leaves a reference in day images is named in the log line, now including
  MapLibre's refusals; the sheet-read failure logs and leaves all in day images (`NightImageApply.kt:101-111`); the
  no-signal switch logs each change (`NoSignalTrailMap.kt:71-83`); the fallback logs once (`OwnStreetFallback.kt:49`).
  The chip's "no provider" default is the same deliberate one the map uses, documented at `NoSignalTrailMap.kt:48-53`.
- **Domain logic headless:** the switch and the walk are pure Kotlin over JSON; the chip's rule is a pure function
  with the composable reading state around it.
- **`.github`:** diff against the base is empty (checked).
- **Record:** the audits index has a row per amendment (`docs/audits/README.md:393-396`); the completion report
  appends and supersedes by name, nothing above edited.

## Not checked

- **Nothing on a device by me.** The S22 night check, the offline v1 run and the no-signal grey step are relayed
  (RECORD -1567; Rowan's 1.0.3837 run). The fallback grey has not been seen on a phone by anyone; Hazel's proposed
  step (block the Worker's host with a cold cache) changes the network and waits for a go. The unit test drives the
  fallback's real entry point, which is the best headless evidence available.
- **v2 offline on a phone:** the S22's regions hold v1 (relayed), so the offline step exercised four references, not
  six. See M2's status above.
- **The SDK getter's exact text** for an expression: not read from the SDK source; the phone's "6 switched, 0 refused"
  is the evidence it carries the prefix.
- **R7b, R10, R11:** not opened.
- **`SightingsMap.kt` and `MapListeners`** beyond the lines cited: the map's reader and the fallback's caller were
  read at the call sites named, not in full.

## For Rowan

1. Merge into pre-main as it stands. Nothing here blocks, and the S22 gate for the night set has been run.
2. L7 (read `getImage` back after the add) and L4 (call `rememberDrawsFromDownloads` from the chip) are small and
   can ride a later PR; neither is worth reopening this one.
3. L6 is a naming of an edge, not a question that needs an answer before merge; raise it with the owner only if the
   Topo-night-over-OSM case ever shows up on a phone.
4. The fallback-grey device step stays open on the record, as Hazel has it.
