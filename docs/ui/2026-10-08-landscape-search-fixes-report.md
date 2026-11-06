# Landscape search fixes, Clear resets, a search moves the map, and New find

Dispatch 2026-09-28-750 (RECORD intent -750; preserved at `prompts/preserved/2026-10-08-06.md`), with item 6 (RECORD -751,
"New find" replaces the "+" tile) relayed by the planner into the same build. Branch `landscape-search-fixes`, cut from
`origin/main` at `c0ec942a` (the base the dispatch names; fetched and confirmed before cutting). No PR. Paths are relative to
`app/src/main/java/com/zynergylabs/forager/app/ui/` unless they start with `app/` or `docs/`.

**Status: built and run.** Full suite 4,485 tests in 579 classes, 24 skipped, 0 failures, 0 errors. S22 launch check PASS. Item 4 was not reproduced, and no code changed for it (see "Premises").

## Premises that were wrong, and what could not be reproduced

- **Item 4 was not reproduced.** The owner saw the bar drop below the compass strip in landscape after a species search,
  Clear, and a turn from portrait. Under Robolectric, through the real screen and ViewModel, at 780 x 360 and 823 x 384, both
  rotations, after a turn from portrait, with a species name far longer than the bar (with no search, and with a search and
  Clear showing), the bar's top is the strip's top, its height is the strip's, and it ends exactly where the strip begins
  (for example 780, ROTATION_90: bar [0, 429.33], strip [429.33, 700]). I found no layout path in the code that puts the bar
  below the strip in a landscape window: the bar is placed at the top on the punch-hole side and the strip at the top on the
  rail side (`AvailabilityCompactScaffold.kt`, the `searchBarSlot` modifier; `AvailabilityCompactMapUi.kt`,
  `CompactMapTopStrip`). The summary already ends in "…" (`TextOverflow.Ellipsis`, `SpeciesSearchControls`' placeholder).
  So **no code changed for item 4**; its tests pass with and without this branch's changes, which means they guard the
  join but do not prove a fix. Item 3 removes the state the owner was in (a species kept after Clear). Whether the phone
  still shows the drop is a device step (below).
- Robolectric's native text measures glyphs at about 1.8 dp each here (131 characters in 236 dp), far narrower than the
  phone, so the long-name tests use a name long enough to overflow under that measure. The ellipsis is checked as the
  text's own overflow setting plus a cut line, not by reading a "…" glyph.
- The dispatch cites `SightingsMap.kt:880-899`; the following check that blocks the region move is there
  (`isGpsTracking`, and `shouldMoveCameraToTarget` at the end of the file). Confirmed as described.

## 1. The panel flush against the bar (landscape)

**Cause.** `searchDropdownTopOffset` added the compass strip's clearance under the bar in every window. In landscape the
strip is beside the bar, so that clearance became a gap. **Measured before** (revert R1): the panel's top at 72 dp against
the divider's bottom at 36 dp, a 36 dp gap, in both windows and rotations, after a turn from portrait. **After:** 0.

**Fix.** `AvailabilityCompactScaffold.kt`: in a landscape window (`landscapeSearchWidth != null`) the offset is the bar's
height alone. Portrait unchanged.

**Tests.** `LandscapeSearchFixesTest` item 1 (780 and 823, ROTATION_90 and ROTATION_270): divider bottom = bar bottom
(positive control), panel top = divider bottom, panel keeps the bar's left and right edges.

## 2. The keyboard keeps Search and Set on map in view (landscape, docked or floating)

**Cause.** Portrait's panel cap subtracts the keyboard's inset, so the keyboard shrinks the panel and the -722 keep-in-view
scrolls it to its end. The landscape cap did not subtract it, so nothing shrank and nothing scrolled.

**Fix.**
- `AvailabilityCompactScaffold.kt`: the landscape cap subtracts the keyboard's bottom inset, as portrait's does.
- The scaffold reads the keyboard from its real insets (`WindowInsets.ime` bottom > 0, or `WindowInsets.isImeVisible`)
  and passes `keyboardUp` to `SearchDropdown` in landscape. A floating keyboard reports no inset, so it shrinks nothing.
- `AvailabilitySearchUi.kt`, `SearchDropdown`: on each rise of `keyboardUp` the panel scrolls to its end. Only on a rise,
  never on a content change, so opening Recent searches still does not pull the view down. A user drag stops it, as in
  portrait (the trigger lives inside the existing `bottomRowKeptInView` effect). The panel's width and side never change.

**Tests** (keyboard insets injected into the screen's own Compose view, docked: 180 dp and visible; floating: visible with
no inset): docked, Search and Set on map end above the keyboard's top and within 24 dp of it (measured 20 dp), the panel ends
at the keyboard's top, did not move sideways, and a real touch on Search runs its search; floating, Search ends within 24 dp
of the panel's bottom, the panel's edges and height unchanged; after a user drag a floating keyboard leaves the scroll alone.
A positive control in each: before the keyboard, Search is below the panel's bottom.

## 3. Clear resets the search to its default

`AvailabilityViewModel.clearSearch`: besides the region and results (as before), the species or category selection goes
back to a fresh start's default (`TaxonFilter.FUNGI` and its `ForagingSelection`), the field's text, suggestions and
`lastTaxonSearchQuery` are cleared, and a species lookup still running is cancelled. The bar then reads "<month> · Search a
location". This replaces -728's "Clear keeps the species". **Kept** (my reading, one line each to change): recent searches
(as asked), month, radius and the coordinate fields. If month is meant to reset too, the bar's default text would also need
the current month.

**Tests.** `SearchClearAndFrameTest`: species picked through the bar, coordinates searched, Clear touched; the filter is the
default, the field and its last query are empty, the bar reads its default text, recent searches unchanged.
`LandscapeSearchFixesTest` item 3: the owner's path (species, search, Clear, then the turn) leaves the default selection and
the bar meeting the strip.

## 4. The bar stays in line with the strip

No code change (see "Premises"). Tests: `LandscapeSearchFixesTest` item 4, four per window (both rotations, with and without
a search and Clear), after a turn from portrait.

## 5. A search moves the map

- `AvailabilityUiState.searchFrameSerial`, bumped in the same update that sets the region by `searchManualCoordinates`
  (typed coordinates and Set on map, whose confirm runs it), `useCurrentLocation` (the dropdown's Search) and
  `onRecentSearchSelected`. Not by a month change, which searches the same place again and does not move a camera the user
  may have moved (my reading; one line to change).
- `MapOverlayContent.searchFrameRequestId`, handed by the Maps tab.
- `SightingsMap`: `searchFrameMove(isGpsTracking, requestId, lastAppliedId)` decides; a new id while following sets the
  camera mode to `NONE` (ends following) and frames the region at `zoomForRadiusKm`, once per id. The applied id is kept on
  `MapCameraMemory.appliedSearchFrameId`, so a map rebuilt after a tab change or the search's loading spinner does not fly
  again over a camera the user has moved. Logged under `ForagerSearchFrame`.
- Locate is unchanged: it sets `CameraMode.TRACKING` again (`resumeTrackingRequestId`).

**Tests.** `SearchClearAndFrameTest`: a recent search far away (Wellington, after a search near Portland) hands the map
that region and a new frame request, and `searchFrameMove` answers stop-following-and-frame for it; coordinates, Search and
Set on map each ask for a frame, a month change does not, and a real touch on locate resumes following; the decision table.

## 6. "New find" replaces the "+" tile (RECORD -751)

- `log/JournalNewItemButton.kt`: the Journal's extended floating button, now one component. Entries' "New entry" uses it
  (`CartographyScreen.kt`), and Finds' "New find" uses it with the Finds chip's leaf (`Icons.Filled.Eco`).
- `log/FindsGalleryScreen.kt`: the "+" tile (`AddEntryTile`) removed; the grid starts at its first date heading. The button
  sits at the grid's bottom end with `Spacing.lg` margin, as "New entry" does, and runs the tile's action. Log's grid gets
  `FAB_CLEARANCE` (88 dp) of bottom padding, as the timeline does. The page slide and list motion are unchanged.
- **Decided beyond scope:** shown on Log only, not Drafts, as the tile was (the tile's own reason, kept in the comment).
  Shown in every window, short landscape included; Entries instead puts "New entry" in its short-window header row and
  hides the floating button there. A question for the owner whether Finds should follow that in short landscape.

**Tests** (`JournalTabTest`): five real touches across the button each start a new find and open its form; no "New log
entry" node and the grid's first item is the newest day's heading; scrolled to its end, the last tile ends above the
button's top. **Existing tests changed** because the tile is gone: 24 call sites in 7 files that found the tile by its
"New log entry" description now find the button by `FINDS_FAB_TAG` (`AvailabilityScreenBackNavigationTest` 3,
`JournalTabTest` 9, `JournalPendingDeleteTest` 2, `RecordsFilterChipsTest` 3, `LeavingTheJournalFixesTest` 2,
`FindsGalleryScreenTest` 4, `JournalShortWindowCardsTest` 1), with the same assertions; one `JournalTabTest` test renamed
from "plus tile" to "New find button"; `JournalPageSlideTest`'s covered-gallery touch moved from the tile's centre to the
button's left end (12 dp in), which the arriving report reaches last.

## Revert checks

Runner: saves the file to a copy, applies the edit, runs only the named tests in a fresh capped scope, refuses results if
the build log has an `e: ` line, reads JUnit XML only if written after the run started, restores from the saved copy (never
git) and confirms the restored file is byte-identical. All compiled; all restored identical; `git status` clean after.

| # | Edit | Failed | Message |
|---|---|---|---|
| R1 | landscape offset branch removed | item 1, 4/4 | "the panel's top is the divider's bottom, no gap expected 36.0 but was 72.0" |
| R2 | landscape cap without the keyboard | item 2 docked, 4/4 | "the panel ends at or above the keyboard's top (360.0.dp vs 180.0.dp)" (384 vs 204 at 823) |
| R3 | `keyboardUp` not passed | item 2 floating, 4/4 | "Search [218.67, 422.00][413.33, 462.00] is inside the panel [0.00, 36.00][429.33, 360.00]" |
| R3b | a drag no longer stops it | drag test, 1/1 | "the user's scroll stands: Search ... is still below the panel" |
| R4 | Clear keeps the selection | 2/2 | "the selection is a fresh start's default expected IconicCategory(Fungi) but was SpecificTaxon(..." |
| R5 | recent search bumps no frame | 1/4 | "the map is handed a new frame request expected 3 but was 2" |
| R5b | following blocks the frame (old rule) | 2/4 | "stops following and frames it expected STOP_FOLLOWING_AND_FRAME but was NONE" |
| R5c | the Maps tab hands no frame id | 2/4 | "coordinates asked for a frame expected 1 but was 0" |
| R6a | no New find button | 3/4 | "could not find any node ... (TestTag = 'finds-fab')" |
| R6b | no bottom clearance | 1/1 | "the last tile's bottom 454.0.dp is above the button's top 398.0.dp" |
| R6c | the "+" tile back | 1/2 | "Did not expect any node but found '1' ... 'New log entry'" |

Item 4 has no revert check: there is no fix to revert. The drag test passes under R1 to R3 as it should (it asserts no
scroll); R3b is its own check.

## Full suite

Run 21:43 to 21:51 UTC on the code at `a1accb17` (the last commit before it touched code was the test rename; `a1accb17` adds only this report). The results directory was emptied first, so every XML is from that run: **4,485 tests in 579 classes, 24 skipped, 0 failures, 0 errors** (counted from the JUnit XML; Gradle: BUILD SUCCESSFUL). No existing test broke apart from the 24 tile call sites changed for item 6 (listed above).

## S22 launch check

`scripts/s22-launch-check.sh` on the S22 (SM-S908U, R5CT321008R), the debug APK built from `a1accb17` (installed versionName `1.0.3101+ga1accb17`), after the planner said the S22 was free: install Success, `compile -m verify -f` Success, dexopt `status=verify`, cold launch ok, **PASS**: process alive after 8 s, crash buffer empty. Nothing else was done on the phone.

## Device only

1. Landscape, both rotations: the panel meets the bar's divider with no gap.
2. Docked keyboard in landscape: Search and Set on map sit just above it. Floating keyboard: the panel scrolls to its end
   and stays on its side. **Unverified:** whether the S26's floating keyboard reports itself visible to the app
   (`isImeVisible` with no inset); if it does not, nothing tells the panel a floating keyboard is up, and there is no public
   API for its bounds.
3. Item 4: search a long species, Clear, turn to landscape (both rotations): does the bar still drop below the strip? Not
   reproduced here.
4. A recent search far away while following: the map flies there and stops following (log tag `ForagerSearchFrame`);
   locate brings following back; leaving Maps and coming back does not fly again. If the location dot is first activated
   after the search (permission granted late), its activation restores following and may pull the camera back: not
   tested, MapLibre cannot run under Robolectric.
5. Journal > Records > Finds: "New find" at the bottom right, the grid's last row clear of it.

## Gradle

- Gradle started only after `systemctl --user is-active t6b-night` read `inactive` (20:53:53 UTC); not started or stopped by me.
- Every run: `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`, `--no-daemon`, Gradle `-Xmx1536m`, Kotlin daemon 2g, Java temp `~/.cache/forager-test-tmp`, `./gradlew --stop` and the Kotlin daemons that run started stopped afterwards. No Gradle process left at the end.
- Free disk checked before each run (the runner refuses under 1.5 GB): 2.8 GB at the start, 2.4 GB at the lowest.
