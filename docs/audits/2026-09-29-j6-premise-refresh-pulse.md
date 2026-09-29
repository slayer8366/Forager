# J6 premise refresh pulse (read-only, at `db756faa`)

**Date:** 2026-09-29.

**Recorded by:** the planner, from a read-only pulse's hand-back. It is condensed, with every citation kept.

**How it was read:**
- **Only through git:** `git show db756faa:` and `git grep … db756faa`. No working tree, build, test or device was used.
- **Read in full first:**
  - the previous pulse (`2026-09-28-j6-premise-pulse.md`, at `14ea159`);
  - the plan's J6 rulings and what follows them (`docs/plans/journal-redesign.md:698-972`);
  - the tablet run record's results (TR:250-445, 853-998).

**Paths** are under `app/src/main/java/com/zynergylabs/forager/app/`. The abbreviations are the previous pulse's, plus:
- PVD = `ui/log/PhotoViewerDialog.kt`
- ASU = `ui/availability/AvailabilitySettingsUi.kt`
- ACMU = `ui/availability/AvailabilityCompactMapUi.kt`
- RDS = `ui/log/RecordDetailsSheet.kt`
- TR = `docs/audits/2026-09-28-tablet-layout-sanity-run-record.md`

**Since this read:** -137, backup and restore (the loading page and the rest), landed at `dd056c6a`. That is after `db756faa`, which is why section 5's first point found none of it.

## 1. The gap table re-verified (compact against wide)

**Still true** (the wide tree still lacks what the previous pulse said):
- **J1, the Entries/Records switch.** Wide still has the "Cartography / Records" `SecondaryTabRow`, and Back from Records goes to Cartography (LP:440-456, 339-341). Compact's `JournalSwitch` is at JT:771, called at JT:616-624.
- **J3, the album.** The view state is local, because LP passes no `entriesViewState` (CS:146; compact at JT:667). `draftFindIds` is not passed (CS:154; EA:267). Album reset to Timeline on the tablet (TR:983-985). The Photo Gallery panel is still there (LP:459-502; AS:1378, 1515-1528).
- **J4, chips and badges.** No `finds`, `onOpenFind` or `selectedTabState` is passed, so Finds has no count and All says "Finds are not listed here" (RT:123, 166, 235; RLL:124-128; LP:504-533).
- **Opening a find.** It goes straight to the editor, with no report and no "+" tile, in 3 columns (LP:374-410; the "+" is deliberately absent, LP:406-408). Compact has a report, then the editor (JT:496-521), a "+" tile (JT:539-542) and 2 columns (FGS:87).
- **J5, track thumbnails.** `tracks` is not passed (CS:152; LP:459-502).
- **J9, columns.** 3 columns, measured at 104 × 136 dp, with titles cut (TR:398; LP:409, 496, 589-590).
- **J10, state.** The holders sit above the branch (AS:795-806) but reach only compact (AS:1791-1794). `drawerPanel` is a plain `remember` (AS:977; LP:254-275).
- **J4b, delete.**
  - Find tiles: no `onDeleteEntry`/`onEditEntry` (FGS:93-95; LP:392-410).
  - Entry cards: no `onRequestDeleteEntry` (CS:161), so no swipe (CELS:161-166).
  - Album: no delete; `onDeletePhoto` is `UNUSED_PARAMETER` (EA:83, 109, 163; CS:163, 586; LP:465).
- **Leaving-the-Journal F2.** Not applied: CS takes its local `entryModeState` default (CS:192).
- **Leaving-the-Journal F3.** No `drawerPanel` write calls a leave, and LP keeps its own `findOverView` (AS:806, 1377, 1453, 1555, 1580).
- **The stale capture** in `leaveFindEditingIfNeeded`: LP:312-314, against JT:408-412 *(inferred)*.

**Present, and shared by both trees:**
- J2's drafts banner (CS:536);
- J6's colour roles (not re-read);
- J7's FAB (CS:597-604; still no wide touch test *(inferred)*);
- J8's row delete (its snackbar docks in the drawer, AS:1909-1912);
- M1's bubbles (AS:1270-1285; LP:274-283, 540-568);
- F1 (AS:1226-1246, 1447).

**Changed.** J5c's details are still a `ModalBottomSheet` (RDS:182), and their wide case is "left to J6" (RT:315-334). The sheet is 640 dp and covers the drawer and the species list, plus 11 dp of the map in portrait or 258 dp in landscape (TR:400).

**Resolved.** J8 on wide: highlights, the top-centre chip row, `VIEW_ENTRY`, and Show/Hide (AWL:302, 327-346; LP:300-304, 501; AS:1297-1307).

**New rows:**
- **Planned trips before a search.** Compact draws its map with trips before any search (ACMU:592-605, 668). Wide draws no map before a search (AWL:239-243).
- **Save to Gallery** is present on wide, through the shared viewer (PVD:172, 234-305). Its five hosts are all shared.
- **The Backup section** is reachable on wide: the Settings panel, `SettingsContent(backup=…)`, `BackupSection` (AS:1384-1401; ASU:362, 378).
- **Map chrome at 0.8** is present on wide (AWL:181, 347-390, 479; AS:1562). The compact cluster fixes do not apply there, because the wide map has only Layers, a legend and Add.
- **The chip row against Layers.** At 824.5 dp the taxon chip is 87.5 dp, under the Layers button, and its clear control has zero bounds (TR:396, 559-561). The row has no end inset for Layers (AWL:331-336).
- **Tests.** There is no MEDIUM-and-tall qualifier. Wide uses only `w840dp-h1024dp-mdpi` (4) and `w1280dp-h900dp-mdpi` (7). `LogPanelTest` has 6 tests.

## 2. The wide layout today

**How the tree is chosen.** Compact if `COMPACT || isShortWindow` (AS:1801); otherwise a `PermanentNavigationDrawer` (AS:1898-1917).
- MEDIUM is 600-839 dp; EXPANDED is 840 dp and up.
- A window is short when it is under 480 dp tall.
- The SM-X800 is wide in both orientations.

**Left column.** A 360 dp `PermanentDrawerSheet` (AWL:96; AS:1900) holding one of six `DrawerPanel`s (AS:389-403, 1335-1529). The Journal is `DrawerPanel.Log` → `LogPanel`: a header, a tab row, then `CartographyScreen` or `RecordsTab` (LP:436-535). An open entry or find replaces the list inside the same 360 dp (CS:383-443; LP:374-389, 540-568).

**Right area.** `mainScaffold` (AS:1916, 1539-1645), which does **not** change while the Journal is open. It holds:
- the search bar, the summary and the notice;
- the **List | Maps | Seasonal** row (AS:357-361, 1590-1598);
- `CombinedResultsPane` for List and for Maps, which render the identical pane (AS:1620-1640), or `SeasonalTab` (AS:1641).

**`CombinedResultsPane`** is a 360 dp `ListTab`, a divider, then the `MapTab` at `weight(1f)` (AWL:131-161). So the map is W − 721 dp wide.

| | 824.5 dp (portrait, MEDIUM) | 1317.6 dp (landscape, EXPANDED) |
|---|---|---|
| Drawer | 360 | 360 |
| Right area | 464.5 | 957.6 |
| Species list | 360 | 360 |
| Map | **103.5** (measured, TR:395) | **596.7** (measured) |

The map is 119 dp at 840 dp and nothing at 721 dp or less *(arithmetic)*.

## 3. Design options for the owner

**a. Where the detail opens**
- **a1.** Replaces the whole right area, search bar included: 464.5 × 1317.6 dp in portrait, 957.6 × 824.5 dp in landscape.
  - Cost: a branch at AS:1916, a detail composable, and splitting the report and editor out of CS:383-443 and LP:345-389. Roughly 300-500 lines plus tests.
  - The detail must take its own insets, which only the device can check.
- **a2.** Replaces only the results pane below the tab row. The List/Maps/Seasonal row stays but does nothing while a detail is open.
- **a3.** Replaces only the species list, and the map stays. The detail is 360 dp, so the report's own map stays small, but a J8 "Show on map" result is visible beside it.
- **Recommend a1.**

**b. The right area with nothing open**
- **b1.** Unchanged: the species list and the map, subject to c.
- **b2.** Map only while the Journal panel shows: 464.5 dp in portrait, 957.6 dp in landscape. About 30 lines.
- **b3.** A placeholder ("Choose an entry"). It loses the map.
- **Recommend b1 with c1, otherwise b2.**

**c. The narrow-map fix**
- **c1.** List and Maps become real tabs whenever the combined pane would leave the map under a minimum width.
  - At 824.5 dp, Maps gives a 464.5 dp map and List a 464.5 dp list. Landscape is unchanged.
  - "View on Map" already selects MAP (AS:827-830). About 60-100 lines, plus tests at `w824dp-h1318dp`.
- **c2.** A modal or collapsible drawer at MEDIUM. It conflicts with ruling 1, and it is large.
- **c3.** Stack the list over the map at MEDIUM. The map is about 464.5 × 556 dp. About 60 lines.
- **c4.** A minimum map width, with the species list as an overlay sheet. About 150 lines or more, and it needs coordinate-touch tests.
- **Recommend c1,** keyed on an explicit minimum map width rather than on MEDIUM, because EXPANDED windows of 840 to about 1000 dp are narrow too.
- **The owner picks the number.** Anchors: the bubble is 280 dp; the two-chip row is 406 dp; Layers needs 56 dp.
- **Even at 464.5 dp, a centred 406 dp chip row overlaps Layers by about 27 dp.** It needs an end inset whatever is chosen.

**d. Journal columns** (the content is 328 dp wide)
- **d1.** One column (328 dp), as compact portrait.
- **d2.** Two columns (160 dp).
- **d3.** Keep three (104 dp; truncates).
- **Recommend:** Entries in 1, Finds in 2 (the FGS default), Album in 3 (CS:588; EA:349). Constants at LP:409, 496 and 590.

**e. Back and leaving**

Today, Back depends on how the Journal was reached:
- LogPanel's own handlers run first (LP:329-341, 542-549);
- then, only if the Journal came from a map route (`isDrawerOpen = true`, AS:1283, 1305, 1605), `BackHandler(isDrawerOpen)` resets to Search (AS:1060, 1016-1024);
- otherwise Back falls to the double-back exit (AS:1121).

The options:
- **e1.** One chain whatever the route: picker → editor (the save prompts) → report → close the detail → Records to Entries → Journal panel to Search panel → exit.
- **e2.** The same chain, but Back never leaves the Journal panel.
- **Recommend e1.**

**State (J10).** Pass the existing holders (AS:795-806) into LogPanel, so a detail survives switching panels and a change between trees.

**f. Other unmade decisions**
1. **The J5c details sheet on wide.** A modal 640 dp sheet, or the right pane? *Recommend the right pane.*
2. **The photo viewer.** A full-window dialog (PVD:146-148), or the right pane?
3. **The pickers** (LP:346-373, and the offline picker): which column?
4. **The drafts list** (CS:464-480): which column?
5. **Wide finds.** Do they gain a report step and the "+" tile (JT:512-542)? The All logbook's finds depend on the answer.
6. **A map before a search on wide** (AWL:239-243; plan:782): part of J6 or not?
7. **The Records chip row** overflows 360 dp and scrolls (TR:342-343). Accept it?
8. **Header naming.** "Mushroom Log" and "Cartography" on wide (LP:585, 449), against compact's labels.
9. **Wide map chrome parity** (locate, compass, record; AWL:377-383). Out of scope unless the owner says so.

## 4. Premises that do not match
1. The plan's J6 text (plan:315-317) says "J1–J9, lowest priority". The priority is superseded, and the text omits J10, J4b, F2/F3 and M1 parity.
2. Ruling 1 names the report, a find and the editor. More opens than that: the drafts list, the details sheet, the photo viewer and three pickers. A wide find has no report at all (LP:403).
3. **Ruling 2 (the album only):** the wide album cannot delete a photo (EA:83, 109; LP:465). The Photo Gallery panel is the only delete path on wide (AS:1519-1527). Removing it without wiring the album's delete loses delete on wide.
4. **Ruling 3 ("mid-size tablets"):** EXPANDED widths of 840 to about 1000 dp are narrow too.
5. RT:330-332 says the sheet lies over the region list, not a map. On the tablet it covered 11 dp or 258 dp of the map.
6. Stale comments:
   - LP:61-63 (`ModalNavigationDrawer`);
   - LP:589 ("wider");
   - AS:1052-1055 (`isDrawerOpen`);
   - AS:1516-1517 (an OfflineMaps panel);
   - `WindowWidthClass.kt:17`.

## 5. Could not determine
- -137 is not in `db756faa`. It landed later, at dd056c6a.
- The order of Back handlers between the drawer and the content, and how two live `mapSlot` maps behave together *(inferred)*.
- The cause of the blocky portrait map (TR:555-557).
- The code sizes, which are estimates.
- Real widths from 840 to 1000 dp and from 600 to 720 dp.
- The photo viewer opened from a map bubble on the tablet (TR:866).
