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
