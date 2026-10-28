# Layers to Tools: completion report (dispatch 2026-10-28-02, Amendments 1 and 2)

Hazel (coder, Opus), RECORD -1764 to -1766. Base: pre-main `e2969abe4`. Branch `layers-to-tools`, PR #432 into pre-main, not
merged. Verify-first: `docs/audits/2026-10-28-layers-to-tools-verify.md` (`cdf9bfe7f`).

## What landed

| Commit | What |
|---|---|
| `cdf9bfe7f` | Verify-first report |
| `f2bbb023f` | Main code: the map type panel, Tools > Map Layers, the card and its sources fetch removed, the arrows removed |
| `e7b31ebba` | Tests rewritten for the new entry points; tests of removed things removed |
| `26becf0c1` | Anchor checks held to 2 dp; the Offline Maps row's text asserted |
| `76264595c` | A test name with a semicolon renamed (Kotlin rejects it; run 40744732186 failed to compile on it) |
| `812c1aed4` | Fixes from run 40746173252's 5 failures: a `testDebug` test still on the sheet; label checks read per switch row (the Tools row also reads "Offline Maps" now); the Settings row touched without a scroll; the legend chip found by its own tag |
| `c217480eb` | The long-press check also runs before the panel is first opened, so revert r10 can fail it specifically |
| (this commit) | This report and the audit index row |

### In behaviour
- **The Layers button opens the map type panel** (`ui/map/MapTypePanel.kt`, new, 130 lines). This is `MapModePicker`'s shape
  rebuilt on today's parts:
  - a scrim fades in, then a panel of the three map type chips (`MapModeChipRow`) fades and grows out of the Layers row, and
    shrinks back into it;
  - it runs on `MotionTokens.panelMotionSpec()`, and under reduced motion it only fades;
  - its fill is the icon bar's, at 80%.
  - A chip tap applies the type and closes the panel. The scrim, or Back, closes it unchanged.
  - Back is registered only while the panel is open, so it is asked first.
- **Anchoring** is as the verify-first report section 8 proposed:
  - The Maps tab uses the cluster's live side and drag (`CompactMapPickers.kt`), plus `MAP_TYPE_PANEL_ANCHOR_OFFSET` (52 dp,
    row 4 of 5) or `_LANDSCAPE` (48 dp, the L's gapless rows). It grows from the bar's side.
  - The entry map anchors `CenterEnd` beside its fixed bar (`CartographyEntryReportScreen.kt`).
- **Tools > Map Layers** (`ui/availability/MapLayersToolsSection.kt`, new, 267 lines) sits under Trip Planner (`SearchControls.kt`).
  - Its fold is held in `AvailabilityScreen` (`mapLayersFoldOpen`, `rememberSaveable`) and passed through the drawer host.
  - The nine switches are, in order: Finds, Photos, Waypoints, Planned Trips, Recording Tracks, Tracks, Offline Maps,
    Journal Entries, Trails. The colour fields follow.
  - Trails is always listed. It is `enabled` only while the Maps tab's map reports that it draws the band
    (`MapLayersControls.referenceTrailsShown`). Otherwise the row is disabled: TalkBack reads "disabled", and the label and
    switch draw in Material's disabled colours.
- `CollapsibleSection` has a second, hoisted form, and it draws no arrow. The arrow's spoken "Expand/Collapse <title>" moved to
  the header's click label.
- **Removed:** `MapLayersSheet` (the composable; the file keeps `MapLayersControls` and the legend chip) and
  `ReferenceLayerCard.kt`. The sources fetch went with the card: `ReferenceTrailsSources.kt`, `ReferenceTrailsManifest.kt`,
  `WorkerReferenceTrailsManifestSource.kt`, `DataStoreReferenceTrailsDatesStore.kt`, and their wiring in `AppContainer.kt` and
  `MainActivity.kt`. So did the entry map's own Trails holder and its now-unread switch callback (through `JournalTab`,
  `CartographyScreen`, and one line of `AvailabilityCompactScaffold`). Nothing cleans up the orphaned DataStore file, as
  accepted.
- **Arrows removed:** `RecordDetailsParts.kt` (Details), `EntryContentsPanel.kt` (contents groups), `SearchDropdownSections.kt`
  (the Month field's trailing icon), and `SearchControls.kt` (Trip Planner, Map Layers, Recent searches).
- **Words:**
  - `layersButtonDescription` is now "Layers: <type> map. Choose the map type."
  - `JOURNAL_ENTRIES_OVERLAY_LABEL` is "Journal Entries".
  - The two test colour fields are "Test Forecast: Chicken of the Woods (Synthetic Data)" and "Test Forecast: Chanterelles
    (Synthetic Data)". That is one `label` each, so every place that shows it (switch, legend, bubble, spoken labels) follows.
  - The Tools row is "Offline Maps".

## Verification
**CI on PR #432, head `c217480eb`: run 40749401858, success.** All five jobs passed: build, unit tests, lint, the unsigned
release compile, and the Worker and route-data jobs. The figures below were read from the run's `unit-test-report` artifact
(JUnit XML), not from the log line:
- 803 XML files: 5881 tests, 0 failures, 0 errors, 24 skipped.
- The base, pre-main run 40626169373: 807 files, 5899 tests, 24 skipped.
- The skipped 24 are unchanged; `AvailabilityScreenMapIconStackTest`'s 19 skips are the same at base.
- The net drop of 18 tests is the tests removed with what they tested (the sheet's colours, the card, the sources fetch and
  its store, the entry map's own Trails row, the sheet's chip centring) less the new ones.

The new and rewritten classes are confirmed in that sample, each 0 failures: `MapTypePanelMapsTabTest` (5),
`MapTypePanelEntryMapTest` (3), `MapTypePanelLandscapeTest` (4), `MapLayersToolsSectionTest` (3), `ReferenceTrailsSwitchTest`
(6), `AvailabilityScreenMapLayersToolsTest` (16), `NoDropDownChevronsTest` (1), `MapTypePanelOfflineChipTest` (10),
`AvailabilityScreenMapIconStackTest` (109, 19 skipped as at base), `AvailabilityScreenSettingsPanelTest` (26, in `testDebug`),
`JournalEntriesOnMapPortraitTest` (22).

Earlier runs on this PR: 40744557545 (cancelled by a newer push), 40744732186 (test compile failed: an illegal test name),
40746173252 (5 failures, all mine and fixed in `812c1aed4`). None of those five failures was in a test outside this
change's reach.

**Grep, for the record** (the same check `NoDropDownChevronsTest` makes on 752 files): none of `ExpandMore`, `ExpandLess`,
`ArrowDropDown`, `ArrowDropUp`, `ExposedDropdownMenuDefaults.TrailingIcon`, `KeyboardArrowDown` or `KeyboardArrowUp` is
in `app/src/main`. `KeyboardArrowLeft` and `KeyboardArrowRight` remain, in `PhotoViewerDialog.kt` only.

## Revert checks
Each revert check is one edit to main code on its own branch (`layers-to-tools-rNN-*`), cut from the forward head
`c217480eb`. CI ran each one through `workflow_dispatch` with a test filter. The forward branch was never edited, so nothing
needed restoring. Its head was still `c217480eb` afterwards, with the forward change in place. For every run the runner
(`reverts.py`, kept in the session scratchpad):
- read the full log for Kotlin compile errors (` e: ` and `compile…Kotlin FAILED`) and found none;
- confirmed tests ran (a non-zero count in the XML);
- read the failures from the JUnit XML.

Every failure below is one its own revert can cause.

| Check | Revert (one line) | Run | Failed with |
|---|---|---|---|
| r01 a chip tap closes the panel | drop `onDismiss()` after `onModeSelected` | 40752676936 | "TOPOGRAPHIC at (0.15, 0.5) closed the panel" |
| r02 Back closes it | drop the panel's `BackHandler` | 40752691284 | "Back closes the panel…": "closed" |
| r03 Trails grey off Trail | `enabled = true` | 40752704013 | "disabled, as TalkBack reads it" (Topo), and the OSM test |
| r04 the fold is held above the drawer | `CollapsibleSection(title = …)`, its own state | 40752718584 | "still open after a trip into Settings" |
| r05 "Recording Tracks" | label back to "Recording trail" | 40752732804 | the nine-label list, and `expected:<[Recording Tracks]> but was:<[Recording trail]>` |
| r06 no arrows | one `ExpandMore` import in `SearchControls.kt` | 40752750225 | names `SearchControls.kt:7: ExpandMore` |
| r07 portrait row-4 anchor | row 4 to row 5 | 40752766938 | "level with the row (515.67 dp vs 463.67 dp)": 52 dp, one row |
| r08 the L's own anchor | drop the L's zero row spacing | 40752780902 | all four L cases, "216.0 dp vs 212.0 dp": the 4 dp the spacing makes |
| r09 the scrim closes it | scrim `onClick = {}` | 40752796624 | "…closes the panel…": "closed", and the long-press test behind the still-open scrim |
| r10 closed, nothing takes a touch | the panel's outer box made clickable | 40752824909 | "never opened: both long-presses reached the map expected:<2> but was:<0>" (the other four fail because the box now covers the Layers row) |
| r11 Title Case test layers | Chanterelles label back to sentence case | 40752839090 | `MapLegendTest`: expected the Title Case names |
| r12 "Offline Maps" row | text back to "Offline maps" | 40752855491 | `expected:<[Offline Maps]> but was:<[Offline maps]>` |
| r13 spoken label | "…Choose the map type and overlays." | 40752871157 | the five-buttons test cannot find "Layers: Trail map. Choose the map type." |
| r14 "Journal Entries" | constant back to "Journal entries" | 40752886231 | `JournalEntriesOnMapTest`: `label=Journal entries` |

Not revert-checked: the disabled row's grey drawing (asserted as semantics only), reduced motion, and the panel's fill
alpha. The last uses the icon bar's own fill constants (`MapIconStackButtonColorDark/Light`, 0.8), which existed before the
panel, so the check is only a guard.

File sizes after the change: `MapTypePanel.kt` 130, `MapLayersToolsSection.kt` 267, `MapLayersSheet.kt` 231 (was 593),
`CartographyEntryReportScreen.kt` 778 (was 787), `MapTypePanelTest.kt` 302, `MapLayersToolsSectionTest.kt` 144,
`MapTypePanelLandscapeTest.kt` 115. All are under 400 lines except the entry screen, which shrank.

## Figures restated: the grey chip on the panel
`MapModeChips.kt`'s contrast figures were measured against the Layers sheet's colour. On the panel's fill, taken opaque (Cream
`#EDE3D0` in the light theme, Bark `#3B2E24` in the dark), the WCAG contrast ratios are computed here from the theme's hex values:

| | Light, on Cream | Dark, on Bark |
|---|---|---|
| Grey "stands aside" fill (`outlineVariant`) | 1.23:1 | 1.35:1 |
| Normal selected fill (`secondaryContainer`) | 1.01:1 | 1.51:1 |

The panel is drawn at 80% over the map, so the real background varies with the terrain. These figures are for the fill alone.
**A finding for the S22:** in the light theme the normal selected chip's fill is almost the panel's own colour (1.01:1). The
selected chip is then told apart mainly by its missing outline, since an unselected chip has one. The old `MapModePicker`
used the same default chips on the same fill. Nothing was changed for it. Whether it reads on a phone is step 2's to judge.

## Not tested, or only on a device
- The animation itself, and how the anchor looks on real insets. Robolectric reports zero insets (CLAUDE.md), so where the
  panel sits relative to the real navigation bar and rail is device-only (S22 steps 2 to 5, in the PR body).
- Reduced motion is not exercised by a test. The code path reads `LocalReduceMotion` (S22 step 8).
- TalkBack's reading of the disabled Trails row is asserted only as the `Disabled` semantics property (S22 step 13).
- The entry map's grey chip with "Use offline tiles" on stays unreachable through the real button. The component test
  `MapTypePanelOfflineChipTest` covers it, composing the panel directly, as the sheet's test did.
- The Tools drawer's Trails row over the Journal follows the Maps tab's map, by the owner's choice (Q1). No test drives a
  journal entry's map type and Tools together.

## Premises that were wrong, and decisions beyond scope
- As reported in the verify-first report, section "Premises that were wrong". No new ones.
- **For the owner's eye: the drawer now holds "Offline Maps" twice.** The Map Layers switch and the row below it that opens the
  downloaded areas both read "Offline Maps" (Amendment 2, Q3 "Match"). One is a switch and one is a link to Records. A test had
  to tell them apart by tag, which suggests a reader could need to as well ("Don't make me think"). Not changed: it is as
  ruled.
- The entry map's switch callback (`onLayerVisibilityChanged`, and the `onMapLayerVisibilityChanged` that only fed it through
  `JournalTab` and `CartographyScreen`) had no reader once its sheet went, so it was removed. One line came out of
  `AvailabilityCompactScaffold.kt`; nothing was added to that 1441-line file.
- Beyond scope, and flagged: the header rows of the folds are as tall as their text (about 24 dp), as before. Removing the
  arrow did not change that, and no 48 dp minimum was added, because that would move the layouts the existing tests measure.

## Amendment 3 (RECORD -1770): Sorrel's review, N1, N3, N5 and N7

Review: `docs/reviews/2026-10-28-sorrel-pr432.md` on branch `sorrel-pr432` (`ddd0b1c4a`). It has no S findings. The owner's
answers are in the dispatch's Amendment 3. N2 and N6 are not in this PR, and N4 needs no change.

| Commit | What |
|---|---|
| `c67ea7052` | Tests first: N1, N3 (including a rotation, saved and restored with `StateRestorationTester`) and N5 |
| `c76d1df9b` | The fixes: N1, opening Tools closes the panel (`AvailabilityCompactMapUi.kt`, a `LaunchedEffect` on `isDrawerOpen`). N3, Trip Planner's fold held in `AvailabilityScreen` as Map Layers' is (`tripPlannerFoldOpen`, through the drawer host and `CompactToolsDrawerContent` to `SearchControls`), with `CollapsibleSection`'s doc comment corrected. N7, the comments repointed |

- **N7, in eleven places, not ten.** Seven comments now cite "`mapChromeFill`'s doc, MapChrome.kt:103-109" for the
  `contentColorFor` reasoning. The two in `MapChrome.kt` itself drop the reference: one is inside that very doc, and the other
  needed none. The two in `Basemap.kt` and `MapMode.kt` are about where the map type is picked, not about colour, so they point
  at `MapTypePanel`.

**Fail-first.** Run 40763051392 ran on `c67ea7052`, which has the tests and none of the fixes, filtered to the three classes.
The log has 0 compile errors. The XML: 12 tests, 3 failures, each the one predicted:
- N1: "and the panel closed";
- N3: "still open after a trip into Settings";
- N3: "Trip Planner still open after the restore".

N5 passed there, as expected. Back already closed the panel first; the test closes Sorrel's coverage gap, and r17 below shows
it bites.

**CI on the PR, code head `c76d1df9b`:** run 40763054488, all five jobs success. From the XML: 804 files, 5885 tests (4 more
than before, which are the new tests), 0 failures, 0 errors, 24 skipped.

**Revert checks**, each a one-line edit on a branch cut from `c76d1df9b`. Each log has 0 compile errors, and each failure is
one its own revert can cause:

| Check | Revert | Run | Failed with |
|---|---|---|---|
| r15 Tools closes the panel | the `LaunchedEffect` line removed | 40763119068 | "and the panel closed" |
| r16 Trip Planner's fold held | `CollapsibleSection(title = "Trip Planner")`, its own state | 40763131110 | "still open after a trip into Settings", and "Trip Planner still open after the restore" |
| r17 fullscreen Back | the panel's `BackHandler` removed | 40763143613 | "Back closed the panel" (fullscreen on) |

**Branches.**
- Once their results were read, I deleted the fail-first branch and the r15 to r17 branches from origin.
- The r01 to r14 branches were already gone from origin when I went to delete them. I did not delete them and did not find
  out who did. Their runs and results are recorded above.
- The forward head was still `c76d1df9b` afterwards.

## Amendment 4 (RECORD -1771): the selected map type chip in the light theme

The S22 session (`docs/audits/2026-10-28-s22-pr432.md` on `s22-pr432`, `8e8165e11`) found the light theme's selected chip a
pale peach with no outline, about 1.04:1 against the panel. The owner, to "Make it stand out (Recommended)": "Option 1, but
Give it an outline."

| Commit | What |
|---|---|
| `d4f300387` | Test first: `MapTypePanelSelectedChipTest` (new, 4 tests). `MapTypePanelOfflineChipTest` and `OwnStreetFallbackRetryTest` expect the light theme's new normal selected fill. `MapModeChips.kt` gains only the `MapModeChipSelectedOutline` semantics key, recording the state at the time (no outline), so the test compiles |
| `64aacd969` | The fix, in `MapModeChips.kt`: in the light theme the normal selected chip is filled `secondary` with an `onSecondary` label and a 1 dp `onSurface` outline |
| `61bfe5335` | The outline assertion reworded after r19, below. The check is unchanged; only which message fires changed |
| `e2a4e0bfe`, `66c0457c0` | The label named as `PureWhite` rather than read as the scheme's `onSecondary`, and its comment reworded (below) |

**What it looks like now.**
- **Light theme.** The selected chip is filled `secondary` (#A2612C, the deepened Mushroom), with a white (`PureWhite`, the value of `onSecondary`)
  label and a 1 dp `onSurface` (Bark, #3B2E24) outline. The unselected chips are unchanged (no fill, with Material's default
  thin outline).
- **Dark theme.** Unchanged: `secondaryContainer` (#6B421A), no selected outline. The shared chip did not get in the way.
  The new colours sit behind `!LocalForagerDarkTheme.current`.
- **The grey "chosen, but something else is shown" chip** (`outlineVariant`) is unchanged in both themes. It gets no outline.
  That leaves the outline marking the normal "on" state only. **For the owner:** whether the grey chip should carry the
  outline too, so that "chosen" reads the same way in both states, is a choice nobody has made. I did not make it.

**Contrast, computed from the theme's hex values (WCAG 2.x).** The panel's fill is `surfaceContainer` (#F4EFE2 light,
#202020 dark) at 80% (`MapIconStackButtonColorLight/Dark`, `MapChrome.kt:54-66`). Three backgrounds are shown: the fill taken
opaque, and the fill composited at 80% over a white map and over a black map. Those are the bounds of what the terrain can do
to it. An unselected chip has no fill of its own, so it shows the panel. "Against the unselected chips" is therefore the same
column.

| Light theme | Opaque #F4EFE2 | 80% over white (#F6F2E8) | 80% over black (#C3BFB5) |
|---|---|---|---|
| New selected fill, `secondary` #A2612C | **4.27:1** | 4.39:1 | 2.67:1 |
| New selected outline, `onSurface` #3B2E24 | **11.42:1** | 11.72:1 | 7.14:1 |
| Old selected fill, `secondaryContainer` #F6DFC8 | 1.12:1 | 1.15:1 | 1.43:1 |
| Grey stand-aside fill, `outlineVariant` #D5CEBF (unchanged) | 1.36:1 | 1.40:1 | 1.17:1 |

- The label on the new fill: white on #A2612C, 4.90:1, which passes AA for text. It is the scheme's own `secondary`/
  `onSecondary` pairing, which `ThemeContrastTest` already holds.
- The outline against the fill it surrounds: 2.67:1.
- The new outline against an unselected chip's outline (if that is `outlineVariant` #D5CEBF): 8.37:1. I am inferring
  Material's default unselected border colour from the S22's description ("a thin light grey-brown line"). I did not confirm
  it from the library source, which is not on this laptop.
- The S22's 1.04:1 was measured from sampled pixels over a real map: 246,223,200 against 226,222,211. The 1.12:1 above is
  the same pair computed from the theme's colours. They differ because the 80% panel takes on the map's tint.
- Dark theme, unchanged, for comparison: `secondaryContainer` #6B421A against #202020 is 1.88:1 opaque, 2.01:1 over black
  and 1.03:1 over white. It reads clearly there by hue and lightness, as the S22 reported.

**A correction to "Figures restated: the grey chip on the panel" above.** That section took the panel's fill to be Cream
#EDE3D0 (light) and Bark #3B2E24 (dark). Since colour build C1 (dispatch 2026-09-28-210), the panel's fill has been
`surfaceContainer` (#F4EFE2 light, #202020 dark), as `MapChrome.kt:56-66` records. So the 1.01:1 and 1.23:1 (light) and the
1.51:1 and 1.35:1 (dark) given there were computed against the wrong colour. The figures in the table above supersede them.
The earlier section is left as written. Its conclusion, that the light selected fill was close to the panel's own colour,
holds either way, and the S22 confirmed it.

**Where the chip shows.** `MapModeChipRow` has one caller in main code, `MapTypePanel` (`MapTypePanel.kt:117`), and
`MapTypePanel` has two:
- The Maps tab's icon bar Layers button (`CompactMapPickers.kt:96`, from `CompactMapTab` via `ScaffoldMapTabCall.kt:125`).
  This covers portrait with the bar on either side, the landscape L, and fullscreen.
- A journal entry's fullscreen map, its Layers button (`CartographyEntryReportScreen.kt:648`, from `CartographyScreen.kt:455`).
  That button is hidden while "Use offline tiles" is on.

No other screen draws these chips. Other `FilterChip`s in the app do not use `MapModeChipRow`, so they are untouched.

**Fail-first.** Run 40766732067, on `d4f300387` (the tests, and no fix), filtered to the three classes. The log has 0
compile errors. The XML: 3 files, 17 tests, 5 failures, each the predicted one. Each expected `secondary` and was drawn as
`secondaryContainer`:
- `MapTypePanelSelectedChipTest`, the Maps tab, light: "the selected chip's drawn fill is secondary".
- `MapTypePanelSelectedChipTest`, the entry map, light: the same message.
- `MapTypePanelOfflineChipTest`, the light passes of two tests: "light, signal: Topo drawn in the normal selected fill" and
  "light, offline tiles off: Topo in the normal selected fill".
- `OwnStreetFallbackRetryTest`: "the Trail chip in its normal selected fill".

The two dark tests passed there, as they should. They guard "unchanged", and r20 shows that they bite.

**Revert checks.** Each one is a one-line edit to `MapModeChips.kt` on its own branch, run through `workflow_dispatch` with a
test filter. The forward worktree was never edited, so nothing was restored. In each log I checked for compile errors before
reading the XML, and found 0 every time.

| Check | Revert (one line) | Cut from | Run | Failed with |
|---|---|---|---|---|
| r18 light fill | `strongLight -> FilterChipDefaults.filterChipColors()` | `64aacd969` | 40767856923 | 17 tests, 5 failures: the same five as fail-first, expected `secondary`, drawn as `secondaryContainer` |
| r19 light outline | `border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)` | `64aacd969` | 40767861123 | 17 tests, 2 failures: "the fill is drawn on the middle row", on both light tests. The cause was the right one, but the message was wrong (below) |
| r19b light outline, reworded test | the same line | `61bfe5335` | 40769538142 | 4 tests, 2 failures: "an onSurface outline is drawn left of the fill", on both light tests |
| r20 dark unchanged | `val strongLight = !standsAside` (the dark check dropped) | `64aacd969` | 40767866067 | 17 tests, 10 failures, every dark-theme fill assertion: expected #6B421A, drawn as the dark `secondary` #E5A76B. The two dark guards fail, and so do the dark passes in `MapTypePanelOfflineChipTest` |

**r19, read against the edit.** With the outline removed, the fill starts at the chip node's own pixel 0. The test had
required at least one pixel to the left of the fill, and that requirement fired first. The failure was one this revert could
cause, and the edit could cause no other. But the message named the fill, not the outline. So `61bfe5335` lets the fill start
at 0 and leaves the outline check to fail, and r19b reran the revert against that test.

After each check, the forward branch still held the change: `git diff 8c3097207 -- MapModeChips.kt` showed the
`lightSelectedChipColors`/`lightSelectedChipBorder` lines.

**Caught by an existing test, and fixed without touching it.** CI run 40769534726, on `61bfe5335`, ran 5889 tests and
failed one: `ThemeCompletenessTest`, with "LightColors.onSecondary is Material's baseline default". That test scans `ui/`
for `colorScheme.<role>` reads, comments included, and fails any role that is read and whose value equals Material's
baseline. My label was the first read of `onSecondary` in `ui/`. Its value, `PureWhite`, is the baseline's white, and the
test's own doc names `onSecondary` as the case of a correct white it cannot tell from an unset role. So `e2a4e0bfe` names
`PureWhite`, the token the role holds, and the pixels are the same. `66c0457c0` then reworded my explanatory comment, which
had spelled out the scanned pattern and would have tripped the test again. The test is untouched. Run 40769534726 is the
fail state for this one: it is the build that read the role. r18, r19b and r20 ran before the label change. They assert
fills and outlines, not the label, so the change cannot alter their results.

**CI on the PR, head `66c0457c0`:** run 40772168805, all five jobs success, 0 compile errors in the log. From the XML: 805 files, 5889 tests (4 more than Amendment 3's 5885, which are `MapTypePanelSelectedChipTest`'s four), 0 failures, 0 errors, 24 skipped. `MapTypePanelSelectedChipTest` 4/0, `MapTypePanelOfflineChipTest` 10/0, `OwnStreetFallbackRetryTest` 3/0, `ThemeCompletenessTest` 1/0.

**Not tested, or only on a device.**
- How the new chip reads at arm's length, indoors and outside, over a busy map. The figures above are computed, not seen. An
  S22 step is added to the PR body.
- The 80%-over-black figure, 2.67:1, is the worst case for the fill against the panel. Over dark imagery the fill separates
  from the panel less than it does indoors. The outline (7.14:1 even there) is what still sets it apart.
- TalkBack is unchanged: the chip's selected state is what it reads, and this edit does not touch it.

**Files.** `MapModeChips.kt` is now 152 lines, and `MapTypePanelSelectedChipTest.kt` is 196. Both are under 400.

**Branches.** I deleted the r18, r19, r19b and r20 branches, both on origin and as local worktrees, once their results were read.

## Amendments 5 and 6 (RECORD -1773, -1774): one outline on every selected chip, in both themes

The owner, after Amendment 4: "Give the grey chip an outline too", then "Light theme only (Recommended)" (Amendment 5).
Then "Both themes. Make it uniform" (Amendment 6), which replaces the light-only rule. Amendment 5 was never built on its
own. Its test draft was rewritten for Amendment 6 before anything was pushed.

What changed: every selected chip carries a 1 dp outline in that theme's `onSurface`, whether it is the normal selected
chip or the grey "chosen, but something else is shown" one. That is Bark #3B2E24 in the light theme and Cream #EDE3D0 in
the dark. The fills are unchanged:
- light selected: `secondary` #A2612C, from Amendment 4;
- dark selected: `secondaryContainer` #6B421A;
- grey: `outlineVariant`, #D5CEBF in the light theme and #444444 in the dark.

The unselected chips keep Material's own outline. This supersedes Amendment 4's "dark theme unchanged" and "grey chip
unchanged" above, and that section's open question about the grey chip, which the owner has now answered.

| Commit | What |
|---|---|
| `f13e264d9` | Tests first. `MapTypePanelOfflineChipTest` gains two tests for the grey chip, both themes: one reached through no signal, one through the Trail Map style fallback (`fallback.onStyleLoadFailed`), as the class's other tests do. `MapTypePanelSelectedChipTest`'s two dark guards now expect the dark fill unchanged, plus a dark `onSurface` outline |
| `b25806b4b` | The fix in `MapModeChips.kt`. `lightSelectedChipBorder` becomes `selectedChipBorder`, used for every chip, and the recorded outline is always `onSurface` |

**Contrast of the outline**, WCAG 2.x, computed from the theme's hex values. The panel is `surfaceContainer` at 80%.

| Outline against | Light, Bark #3B2E24 | Dark, Cream #EDE3D0 |
|---|---|---|
| Panel, opaque (#F4EFE2 / #202020) | 11.42:1 | 12.80:1 |
| Panel at 80% over a white map (#F6F2E8 / #4D4D4D) | 11.72:1 | 6.64:1 |
| Panel at 80% over a black map (#C3BFB5 / #1A1A1A) | 7.14:1 | 13.67:1 |
| Selected fill (#A2612C / #6B421A) | 2.67:1 | 6.82:1 |
| Grey fill (#D5CEBF / #444444) | 8.37:1 | 7.65:1 |

Every outline clears WCAG 1.4.11's 3:1 non-text bar against the panel, in every case shown. The one figure below 3:1 is
the light outline against the light selected fill it surrounds, 2.67:1. There the outline is not what tells the chip from
the panel: the fill does that, at 4.27:1, from Amendment 4.

**Fail-first.** Run 40775654527, on `f13e264d9`, was filtered to the two classes. The log has 0 compile errors. The XML: 2
files, 16 tests, 4 failures. Each was the predicted "recorded outline" failure, expecting `onSurface` and finding
`Unspecified`:
- the two dark selected tests, expecting Cream;
- the two grey tests, expecting Bark, in their light pass, which runs first.

**Revert checks.** Each one edits the single `border =` line on its own branch, cut from `b25806b4b`. The forward tree was
not edited, and `git diff b25806b4b` was empty afterwards. Each log has 0 compile errors.

| Check | Revert (one line) | Run | Failed with |
|---|---|---|---|
| r21 grey outline | `border = if (standsAside) FilterChipDefaults.filterChipBorder(…) else selectedChipBorder(selected)` | 40776559295 | 16 tests, 2 failures: "light, no signal / light, fallen back: an onSurface outline is drawn left of the grey fill". The selected-chip tests pass |
| r22 dark outline | `border = if (isDarkTheme) FilterChipDefaults.filterChipBorder(…) else selectedChipBorder(selected)` | 40776563536 | 16 tests, 4 failures: both dark selected tests ("an onSurface outline is drawn left of the fill"), and both grey tests on their dark pass ("dark, no signal / dark, fallen back: …") |

In both reverts the recorded outline still said `onSurface`, since only the drawn border was reverted. So these failures
come from the drawn-pixel checks, which shows those checks bite independently of the record. r21 stops each grey test at its
light pass, so the grey chip's dark drawn outline is shown to bite by r22.

**CI on the PR, head `b25806b4b`:** run 40776527059, all five jobs success, 0 compile errors in the log. From the XML: 805
files, 5891 tests (5889 plus the two new grey tests), 0 failures, 0 errors, 24 skipped.

**Where it shows.** The same two places as Amendment 4: the Maps tab's icon bar Layers panel and a journal entry's
fullscreen map Layers panel. On the entry map the grey chip stays unreachable on a phone, because its Layers button is
hidden while "Use offline tiles" is on. On the Maps tab it appears with no signal over a downloaded area, or after the
Trail Map style has failed to load.

**Branches.** I deleted the r21 and r22 branches, on origin and as local worktrees, once their results were read.

**Not tested.** How the outlines look on the phone. The S22 steps 19 to 21 in the PR body cover it.
