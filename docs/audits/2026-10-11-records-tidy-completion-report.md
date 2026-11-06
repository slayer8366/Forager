# Records tidy: completion report (dispatch 2026-10-11-03; RECORD -868, -870, -871)

Built on branch `records-tidy`, cut from `main` at `af5ea9af`. `main` has not moved since; checked at 2026-10-11T19:34Z. Every build and test ran on CI through the "run this branch" trigger. No local Gradle was run, and no phone was used. **Not merged.**

The step-0 report is `docs/audits/2026-10-11-records-tidy-step0-report.md` (`e651dd12`). The rulings are RECORD -868, -870 and -871 on `records-after-173`.

## What landed

| Item | Commit | What |
|---|---|---|
| Region row (-870, -871) | `b4021c49`, `174aaf91` | The row is one line: "Region 5 · 10 mi radius · 18.8 MB · downloaded 11 hours ago". A region that isn't downloaded reads "… · 10 mi radius · Not downloaded" and keeps its Download again button. "Stale" stays beside the name. Centre, tiles and the zoom paragraph were already on the region's page (`RecordDetailsSheet.kt` `OfflineRegionDetails`). They stay there, so nothing was dropped. No place name is shown: the app stores none. |
| Finds by default, Back to Finds (-868, -871) | `ee78c5da` | `DEFAULT_RECORDS_FILTER = FINDS` (`JournalScreenState.kt`). The chip Back step targets it (`RecordsTab.kt`). Back from Finds falls through to Entries, as Back from All used to. The chip page slide's base page follows Back's target. |
| All by kind (-868, -871) | `b25192e5` | `buildRecordsLogbook` returns sections Finds, Tracks, Waypoints, Offline maps, each newest first. Empty kinds are left out. The header reads "Finds · 9" (`RecordsLogbook.kt`, `RecordsLogbookList.kt`). |
| Find page photo (-868, -871) | `30fc43b8`, `9619a71e` | The photo comes first under the title, followed by "Found at" and the identification. It shows whole, in its own shape. Several photos sit in a swiping row with the next one peeking in. Tap opens the viewer, as before. `DecodedPhoto` gained `wholePhoto`; its other callers keep the crop. |
| Tools > Offline maps, and Back (-868, -871) | `dc65ff93` | A fixed row above Settings opens Journal > Records on the Offline maps chip (`PendingJournalDestination.VIEW_OFFLINE_MAPS`). The first Back restores the page it was tapped from (tab, Journal top tab and chip, fullscreen) and reopens Tools. The second Back closes Tools. Moving to another chip or tab cancels this. The inclusion-rule comment records the exception (`AvailabilityNavigationUi.kt`). |
| Test fixes from the full run | `2711dc53` | Four failures, listed under "Assertions changed" below. |

## Evidence (CI)

Forward runs. Each `headSha` was checked against the pushed commit, and each build log was read for `e: ` lines before results were cited.

- `38166408265` at `174aaf91` (A and B, filtered): no compile errors. 10 suites, 159 tests, 0 failures.
- `38167052604` at `dc65ff93` (everything, unfiltered): no compile errors. 608 suites, 4,663 tests, 4 failures, 24 skipped. The four were fixed in `2711dc53` (below).
- `38168589182` at `2711dc53` (everything, unfiltered): no compile errors. 608 suites, 4,664 tests, 0 failures, 24 skipped; the 24 skipped matches main's count in RECORD -869. Among the 608: `FindPagePhotosTest` 4/4, `OfflineRegionRowPartsTest` 3/3, `RecordsLogbookTest` 5/5, `RecordsFilterChipsTest` 14/14, `DrawerBackOverJournalTest` 11/11, `DecodedPhotoSemanticsTest` 4/4.

Revert checks. Each ran on its own branch, cut from the forward head with one line changed, so the forward change was never touched. After the runs, all five forward lines were re-read on `records-tidy` (grep at `9619a71e`). Every run below compiled (0 `e: ` lines). Each failure message is specific to its edit.

| Item | Revert (branch, run) | Failures, with messages |
|---|---|---|
| Region row | Zoom paragraph put back in the row (`records-tidy-revert-a`, `38167152637`) | 2 of 41: `no "Ready to zoom" in All's row expected:<0> but was:<1>`; the Settings-panel test "Did not expect any node but found '1' … 'Ready to zoom 15'" |
| Finds default | `DEFAULT_RECORDS_FILTER = ALL` (`-revert-b`, `38167154728`) | 5 of 49: Finds chip `Selected = 'false'` (three tests); a missing Finds chip node in the find-in-All loop; the Entries switch `Selected = 'false'` |
| All by kind | `.filter { it.recordCount > 0 }` removed (`-revert-d`, `38167156529`) | 4 of 5: `expected:<[WAYPOINTS, OFFLINE_MAPS]> but was:<[FINDS, TRACKS, WAYPOINTS, OFFLINE_MAPS]>`; `expected:<[]> but was:<[LogbookSection(type=FINDS…` ; two `List has more than one element` from `.single()` (empty sections the revert keeps) |
| Find photo | `wholePhoto = false` (`-revert-e2`, `38168028002`, at `252e030a`) | 3 of 4: `the page's width less its margins: 200.0.dp against 328.0.dp`; `at the cap: 200.0.dp`; the swipe case. The swipe case also failed on the forward build at the time, so it is not counted as evidence; it was fixed in `2711dc53`. |
| Tools Back | `isDrawerOpen = true` removed from the Back step (`-revert-f1`, `38167161176`) | 3 of 11: `the first Back reopens Tools` (from Maps, Seasonal and the Waypoints chip) |
| Tools row | `onOpenOfflineMaps` not passed (`-revert-f2`, `38167162873`) | 5 of 11: `could not find any node … (TestTag = 'tools-offline-maps-row')` |

**What the first find-photo revert caught.** The first find-photo revert (`-revert-e`, `38167159127`, at `dc65ff93`) failed only the swipe case. The one-photo test's 2:1 shape check passed with the crop as well: forcing full width scaled the crop into the same box. So that check could not tell a crop from the whole photo. The fix (`9619a71e`) removed the forced width. That also fixed a real fault: a photo taller than the cap had fallen back to its pixel size. A tall-photo case was added, and the second revert then failed on width and height, as shown above.

## Assertions changed, each as a ruling requires

- `RecordsFilterChipsTest`
  - The default-chip test: All selected became Finds selected.
  - The Back loop: All after each Back became Finds, and its chip list became All, Tracks, Waypoints, Offline maps.
  - The find-in-All test: the second Back asserts Entries, not All.
  - The All reading order: day headers and their counts became section headers with theirs.
- `RecordsLogbookTest`: rewritten whole, because the day grouping it asserted is gone.
- `AvailabilityScreenBackNavigationTest`: two tests, All became Finds.
- `AvailabilityScreenLandscapeB3DestinationsTest`: default All became Finds.
- `JournalPageSlideTest`: the base page under a chip list, All became the Finds grid.
- `JournalShortWindowCardsTest` 753: "not on Records' default chip" became "not on the All chip".
- `AvailabilityScreenSettingsPanelTest` (testDebug): the three zoom-paragraph counts were 1 on the chip. They are now 0 on the chip and 1 on the region's page.

Each change is named in a comment at the test. Setup-only changes, with assertions kept:

- Touching All first in `RecordsFilterChipsTest`'s logbook screen, `RecordDetailsSheetTest.openRecords`, and three `AvailabilityScreenJournalShortWindowTest` cases.
- A scroll before the identification line in `LeavingTheJournalFixesTest` F3. The photo now pushes that line down.
- The Layers sheet labels in `AvailabilityScreenMapLayersSheetTest`, now looked up inside the sheet. The closed Tools drawer's "Offline maps" row matched the bare text.

`DecodedPhotoSemanticsTest` passed in run `38167052604`, so the semantics snapshot it pins did not change. Nothing was ignored, skipped or added to an allowlist.

## Decided beyond the rulings (for the owner or planner to confirm)

1. **Same-day finds order.** The planner asked for "most recently saved first". The app stores no time a find was saved, and adding one would need a Room column, which is a stop. So finds go by the time they were found, newest first (`foundAtEpochMillis`, written once at creation). A find with no time sorts after those with one.
2. **Find photo height cap, 480 dp.** A taller photo is drawn at the cap, narrower and centred, never cropped. In a short landscape window the cap is taller than the screen. The photo can then fill the view, and the find's details are reached by scrolling. Not ruled.
3. **Peek width, 32 dp a side,** with the current photo centred.
4. **The chip page slide's base page moved to Finds,** so Back from another chip slides out over the page it returns to. As a result, a find tapped in All now shows All sliding out to the right, not the report sliding in.
5. **The Tools return also restores fullscreen.** Tools cannot be opened from fullscreen through the bottom bar. It can be opened by a backup notification or the settings-reset snackbar.
6. **A find opened from All, then Back twice, leaves Records.** It used to return to All (the owner's J1 answer 2). This follows -871's "Back from Finds leaves Records". Please confirm this is what was meant.

## Not tested

- Everything a phone shows: photo sizes and memory at real decode sizes, the peek and swipe feel, the drawer's real layout, and Back through the system gesture and the 3-button bar. See the device check below.
- Robolectric graphics. The photo tests use native graphics with real PNGs. The other photo-bearing tests use the legacy shadow, which decodes every file as a 100 × 100 square.

## Device check (S22), cheap steps first

1. **Region row.** Journal > Records > All. Pass: each offline map row reads one line, name · radius · size · downloaded …, with no coordinates, tile count or zoom paragraph. Tap one. Pass: its page shows Centre, Tiles and the zoom paragraph. Evidence: what each line reads, in words.
2. **Cold start opens on Finds.** Swipe the app away, reopen it, then Journal > Records. Pass: the Finds chip is selected.
3. **Last pick held.** Pick Waypoints, go to Maps, come back to Journal > Records. Pass: still Waypoints. Then press Back. Pass: Finds. Press Back again. Pass: Entries.
4. **All by kind.** Records > All. Pass: sections in the order Finds, Tracks, Waypoints, Offline maps, each header's count matching its chip, newest first inside, and no header for a kind with nothing in it. Swipe-delete one waypoint, then Undo. Pass: it goes and comes back.
5. **Find page, one photo.** Pass: the photo is under the title, as wide as the page less margins, its whole shape visible, with "Found at" below it. Tap the photo. Pass: full screen. Back. Pass: the find page. Back. Pass: the Finds list.
6. **Find page, three photos, including one portrait.** Pass: the next photo peeks in at the side; a swipe moves one photo; the portrait one is whole, not cropped.
7. **Tools > Offline maps from Maps.** Pass: the drawer closes on Records, Offline maps chip. Back. Pass: Tools open over Maps. Back. Pass: Maps. Repeat from Seasonal and from Records > Waypoints. Use the system Back gesture, and the 3-button bar if the phone has one.
8. **Moving on.** Tools > Offline maps, then tap Tracks, then Back. Pass: Finds, with no drawer.
9. Observation, not a gate: scrolling the find page with three large photos, and whether the 480 dp cap feels right in landscape.

## Addendum: RECORD -874 (2026-10-11)

The owner's ruling and the planner's calls on the decisions above. The "Decided beyond the rulings" list above is left as it was written. Items 1 and 5 there stand. Items 2, 4 and 6 are superseded as follows:

- **Back from a find opened in All** (owner: "Find > All > Finds > out (Recommended)"), `bf4973fb`. The All logbook's find tap no longer selects the Finds chip. The find opens over Records through `JournalTab`'s find-over-the-view page, the same way the entry map's "Open find" opens one.
  - The first Back closes it to All, the second goes to Finds, and the third leaves Records.
  - Long-press Edit from All is unchanged: it still opens the form under the Finds chip, because the ruling covers opening a find, not editing one.
- **Motion** (planner): opening a find from All slides the find in over All, and All stays in place. Back slides the find out over All. This comes with the same commit, and a headless test covers it (`JournalPageSlideTest`).
- **Photo height** (planner): the cap is the smaller of 480 dp and 60% of the window's height, `9884d970`. The 32 dp peek stays.

Changed assertion: `RecordsFilterChipsTest`'s find-in-All test is now "touching a find in All opens its report over All, and Back goes to All, then Finds, then out of Records". It used to assert three things, each named at the test:
- the Finds chip selected with the report open, which became All selected and the find over the view showing;
- the F2 tile gone, which became the find over the view up, since the tile stays composed under it;
- the Finds gallery after one Back, which became All with the F2 tile, then Finds, then Entries.

CI, each run checked for compile errors (none) and for its head SHA:

- Full run `38169963103` at `9884d970`: 608 suites, 4,666 tests, 0 failures, 24 skipped. `DecodedPhotoSemanticsTest` 4/4.
- Revert for the find in All (`records-tidy-revert-g1`, `243e3fff`, the tap selecting Finds again), run `38169965476`. 2 of 24 failed:
  - `nothing is leaving: All does not slide out as if going back`
  - the All chip `Selected = 'false'`
- Revert for the cap (`records-tidy-revert-g2`, `7c3818d2`, the cap back to 480 dp), run `38169967395`. 1 of 5 failed: `capped at 60% of the window height, 230.40001.dp: 304.0.dp`.

Device check changes:
- Step 5 adds: Records > All, tap a find. Pass: it slides in over All. Back. Pass: All. Back. Pass: Finds. Back. Pass: Entries.
- Step 9 becomes: in landscape, the find page shows the title and "Found at" with the photo.

## Addendum: RECORD -876 (2026-10-11)

- **Edit from All** (owner: "Yes, Edit opens over All too (Recommended)"), `a3239bfd`. From All, long-press a find and choose Edit: the edit form now opens over All, and Back from the form returns to All. This supersedes the -874 addendum's note that Edit from All was unchanged.
  - The long-press Edit uses the same find-over-the-view page as the tap.
  - `nextFindOverView` now also shows for the draft row of the find that was asked for. A find opened straight into its form opens as its draft, whose own id is never the asked-for find's.
- **Changed assertion.** `JournalPendingDeleteTest`'s Edit-from-All test is now "… whose Edit opens its form over All, Back returning to All". It asserted the Finds chip selected after Edit; it now asserts:
  - the find over the view is up, with All selected;
  - after a Back through the Activity's real dispatcher: the form closed, All selected, and find-b's tile shown.

  The test's Delete half and the form's own assertions are unchanged. `FindOverViewTest` gains a draft case; no existing assertion there changed.
- **Revert check.** On `records-tidy-revert-h` (`1fabc38b`), Edit selects Finds again. Run `38171398253` compiled with no errors. 1 of 55 failed: the All chip `Selected = 'false'` in that test.
- **Full CI run.** It was run after this addendum was committed, so that it covers the final head. Its run number and result are in the hand-back to the planner, and the planner records them at merge.
