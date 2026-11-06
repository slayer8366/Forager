# Search keyboard, recent search, Clear, suggestions, guidance text

Dispatch 2026-09-28-722 (RECORD intent -722; preserved at `prompts/preserved/2026-10-08-02.md`), with four additions relayed
by the planner into the same build: RECORD -723 (recent-search bug and a Clear button), -724 (replaced by -725 before any of
it was built), -725 with its clarification -726 (species suggestions), and -727 (the Trip Windows guidance text). Branch
`search-keyboard`, cut from `origin/main` at `bfbba33f` (the base the dispatch names; fetched and confirmed before cutting).
No PR. Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/` or `docs/`.

**Status: built and run. After the -728 round (section 6), the full suite is 4,416 tests, 24 skipped, 0 failures.** The
sections below up to 5 describe the first round as reported; section 6 supersedes what it changes. Gradle started only after `systemctl --user is-active t6b-night` read `inactive` (12:14:20 UTC).
Every behaviour claim below rests on a test named beside it; what no Robolectric test can show is listed under "Device only".

## 1. The keyboard covers the dropdown's bottom row (-722)

**Cause, confirmed from history.** `0777e671` (RECORD -697) removed `coordinatesKeptInView`, the keep-in-view that scrolled
the panel to its end each time its viewport changed, together with the scroll-to-the-end on open. The panel's cap follows the
keyboard (`AvailabilityCompactScaffold.kt`, `searchDropdownImeBottom`), so with nothing scrolling it the keyboard's arrival
shrank the viewport and left the end, now Set on map and Search, under the keyboard. Confirmed by the revert check R1 below:
with the restored keep-in-view switched off, Search sits 66 dp below the panel's bottom after the shrink.

**Fix.** `AvailabilitySearchUi.kt`, `SearchDropdown`: `bottomRowKeptInView`, on from the panel's open. Each time the viewport
**shrinks** the panel scrolls to its end; the first size and any growth are left alone, so the panel still opens at its top
with the coordinates first (-697). A `DragInteraction.Start` turns it off until the next open (the panel leaves composition
on close). That is the old T3b rule.

**Tests** (`SearchDropdownKeyboardTest`, T3a/T3b restored for the new layout, T3c added):
- T3a: 800 to 360 dp; the panel scrolled; Search and Set on map each end 20 dp above the panel's bottom (16 dp padding plus
  the 4 dp a 40 dp button leaves under itself in its 48 dp touch target, measured on the first run; the first draft said 16
  and failed on that, which is how the 4 dp was found); five real coordinate touches across each button reach it.
- T3b: shrink, two real drags back to the top (Latitude 16 dp below the panel's top), then 420 and 360 again: Latitude is
  still 16 dp below the top.
- T3c (two tests): with no shrink, Latitude is 16 dp below the top at 800 dp, and at 360 dp when the panel opens that short.

## 2. A recent search tapped: "Dropdown closes, nothing new" (-723)

**Reproduced first** (`RecentSearchShowsOnMapTest`, real screen, real ViewModel via the new `RealSearchScreenRig`, real
touches in touch mode; the map's input read from the `MapSlot` the screen composes). Before the fix:
- An **earlier** recent search (A searched, then B, then A's row touched) **passed**: the map was handed A's observations.
- The recent search **already showing**, touched again, **failed**: the map was handed nothing (`expected [A's sighting] but
  was []`).

**Cause, confirmed.** `AvailabilityViewModel.refresh` clears the sightings and expects the visible tab to fetch them again.
That fetch is `AvailabilityScreen`'s `LaunchedEffect(selectedTab, region, month, filter)`. Re-running the search already
showing changes none of the four keys, so the effect never re-ran and the map stayed empty. Not the other two candidates: the
row's touch reached `onRecentSearchSelected` (the region was set), and refresh published.

**Not confirmed:** that the owner tapped the search already showing. Robolectric shows only that case failing. If they tapped
a different search and saw nothing, the cause is something this rig does not reproduce (a device-only path, for example the
live API or camera framing); the device step below separates the two.

**Fix.** `AvailabilityUiState.searchSerial`, bumped by every `refresh`, is a fifth key of that effect. Revert R4 brings the
failure back.

**Month and radius (-725 item 2).** `onRecentSearchSelected` already restored the search's own month and radius; kept, and
tested: a search saved at next month and 25 km runs with that month and 25 km (state, ranked-list fetch, sightings fetch and
the map's input). Revert R5 (current month instead) fails it: `expected 11 but was 10`.

## 3. Clear on the search bar (-723)

`SearchEntryBar`: a "Clear" `TextButton` at the bar's right end, a sibling of the field (so a tap on it never focuses the
field), shown only while `region != null`. Both bars (Maps tab and the other tabs) get it. It calls the new
`AvailabilityViewModel.clearSearch()`, wired in `MainActivity`.

**Clear's touch target is the field's height (about 29 dp) by the button's width, not 48 dp.** The first full suite found a
48 dp Clear grew the bar from 45 to 61 dp whenever a search showed, which moved the landscape L and cluster (13 failures in
`LandscapeLRulingsTest`, `LandscapeLFullscreenCycleTest`, `AvailabilityScreenTabletAsPhoneTest`,
`LayoutFixesShortLandscapeTest`, all "the L's top 45 ... the search bar's bottom 61"). The bar's height is the owner's own
earlier ask, so Clear gives way: `LocalMinimumInteractiveComponentSize` unset and `height(fieldHeight)` (`c85dcc6a`). A
smaller target than Material's 48 dp is a trade the owner may want to rule on. The 29 dp is twice labelMedium's "Mg" height,
computed, not measured.

Tests (`SearchBarClearTest`): the bar's height is the same with Clear showing as without; nothing searched, no Clear; five real touches across Clear, each after a fresh search, each
leaving the map empty, region and ranked list null, the summary reading "Search a location", recent searches unchanged, Clear
gone and no dropdown opened; and a ranked-list fetch held open when Clear is touched, then released, puts nothing back.

## 4. Species suggestions (-725, -726; -724 replaced)

-724 (auto-search on a suggestion) was never built; -725 replaced it before the code was written. Now a suggestion tap
selects the species (the bar names it), closes only the suggestion list, and leaves the search dropdown open, the field
focused and the keyboard up. No fetch runs.

- `AvailabilityViewModel.onTaxonSearchResultSelected` no longer searches again when a region is showing (it did on `main`).
- Both scaffold call sites no longer set `showSearchDropdown = false` on a suggestion.
- **Found at the build:** even with no search call, the screen's lazy-fetch effect (keyed on the filter) fetched the picked
  species' sightings for the old region, a search by another name. `onMapTabSelected` and `onSeasonalTabSelected` now read
  `activeSearch`, the region, month and filter `refresh` last ran (cleared by `clearSearch`), not the UI state's current
  filter. Revert R11 brings the extra fetch back.

Tests (`SpeciesSuggestionKeepsSearchOpenTest`, real touches inside the suggestion popup, with a search showing and with
none): suggestion list gone, dropdown displayed, field focused, keyboard up (Robolectric's shadow `InputMethodManager`, with a
positive control that it reads up after the bar's tap), filter is the species, the bar shows it; no ranked-list or sightings
fetch, and with nothing searched no location request.

## 5. Trip Windows guidance text removed (-727)

Removed: the divider and guidance section in `ui/availability/AvailabilityTripWindowsUi.kt` (`ForagingWeatherGuidanceSection`
with it), and `ForagingWeatherGuidance` (its `Guidance` and `forSelection`, the whole object) from
`domain/ForagingWeatherGuidance.kt`. No caller is left (`git grep` for `ForagingWeatherGuidance.`, `forSelection`,
`Guidance(`). Kept: `ForagingSelection` and its group, which recent searches store and restore.

`TripWindowsGuidanceTextTest` now asserts no guidance text (each heading and a phrase from each paragraph, including the two
-695 texts) for the Fungi category, a fungus, a plant and an unknown group, with the card shown. Revert R12 (both files back
as on `main`) fails three of four: fungi category, fungus, plant. The unknown group passes under the revert, as it should:
it never had text.

**Tests deleted** (they tested removed code), from `ForagingWeatherGuidanceTest`:
`fungi guidance states the rain lag pattern and hedges it as a rule of thumb`;
`fungi guidance quotes the soil temperature band from the labelled assumption`;
`under the imperial setting the fungi guidance quotes the band in Fahrenheit`;
`under the metric setting the band reads exactly as before`;
`plants guidance is not the fungi text`;
`plants guidance says plainly that there is no weather pattern to offer`;
`a category with no written guidance shows none rather than borrowing another's`;
`a specific species shows exactly its category's guidance`;
`a specific species carries no species note and nothing names it`;
`a species with no known group shows no guidance at all`;
`the lichens chip is not given the fungi fruiting pattern, or any other`;
`of the default selections, fungi and plants have guidance and lichens has none`;
`no guidance text states a score, probability or best day`.
Its three `ForagingSelection` tests are kept, unchanged, as `domain/ForagingSelectionTest.kt`.

From `TripWindowsGuidanceTextTest`, replaced by the four above: `a fungus species picked by name shows the fungi pattern and
no species note`; `the fungi category shows the fungi pattern and no species note`; `a fungus species reopened from a recent
search shows the fungi pattern and no species note`; `a species in a group with no written guidance shows no guidance block
at all`.

**Existing tests changed** (they read the removed text), `AvailabilityScreenLayoutTest`:
- `the drawer's Trip Planner section shows the trip windows card once a region is searched`: scrolled to the guidance heading
  as an anchor; now scrolls to the card's last line, then its title. Same assertions otherwise.
- `under the imperial setting ... and the guidance band read in Fahrenheit` renamed `... soil temperature reads in
  Fahrenheit`; its "roughly 50–68 °F" assertion removed.
- `under the metric setting ... and the guidance band read as before` renamed `... soil temperature reads as before`; its
  "roughly 10–20 °C" assertion removed.

**Left in place, now unread:** `FruitingPatternAssumptions.TEMPERATE_FRUITING_SOIL_TEMPERATURE_C` (`domain/
FruitingPatternAssumptions.kt:83`) had one reader, the removed fungi text. -727 says to keep anything still read; this is no
longer read. Not deleted here; the owner or planner decides.

## Decided beyond scope (each reversible, each the reading I took)

1. **What Clear clears.** The owner named the map's observations and the summary. Clearing only those would leave the List,
   rainfall, trip windows and seasonal tabs showing a search the bar says is not there, so Clear clears the region and every
   result fetched for it, and cancels fetches still running (`searchJobs`, a supervisor job all search fetches now run in).
   **Kept:** recent searches (as asked), month, radius, the species selection and the coordinate fields. The species stays,
   so after Clear the summary reads "<species> · October · Search a location" if a species was selected; with none,
   "October · Search a location". If the owner meant the species to go too, that is one line in `clearSearch`.
2. **Clear while a search is loading** shows (the region is set) and works (tested).
3. **The map follows the search last run** (`activeSearch`), not the current filter, which is what makes "no search yet"
   true; a month change still searches again, as on `main`.

## Revert checks

Runner: saves each edited file to a copy, applies the edit, runs only the named classes in a fresh capped scope, refuses
results if the build log has an `e: ` line, reads JUnit XML only if its timestamp is after the run started, restores from
the saved copy (never git) and confirms the restored file is byte-identical to the copy. `git status` was clean afterwards.
All thirteen compiled, all fresh, all restored identical; after R13 the forward change was confirmed present (`grep`) before it was committed.

| # | Edit | Class | Failed | Message (specific to the edit) |
|---|---|---|---|---|
| R1 | keep-in-view off from open | SearchDropdownKeyboardTest | 2/4 | T3a "the panel scrolled (Latitude 16.0.dp from its top)"; T3b "the shrink lifted the bottom row first expected 20 but was -66" |
| R2 | a drag no longer stops it | SearchDropdownKeyboardTest | 1/4 | T3b "still where the user put it after a later shrink expected 16 but was -70" |
| R3 | scroll on every size, not only a shrink | SearchDropdownKeyboardTest | 1/4 | T3c short open "a first size is not a shrink expected 16 but was -70" |
| R4 | `searchSerial` key removed | RecentSearchShowsOnMapTest | 1/3 | already-showing search: "expected [A's sighting] but was []" |
| R5 | current month instead of the saved one | RecentSearchShowsOnMapTest | 1/3 | "the month is the search's own expected 11 but was 10" |
| R6 | Clear does nothing | SearchBarClearTest | 2/3 | "a touch at (0.5, 0.5) of Clear left nothing on the map expected [] but was [A's sighting]"; late fetch "put no ranked list back ... but was AvailabilityForecast(...)" |
| R7 | Clear always shown | SearchBarClearTest | 2/3 | "Did not expect any node ... 'search-bar-clear'"; "Clear is gone with the search" |
| R8 | Clear cancels no fetch | SearchBarClearTest | 1/3 | "the late fetch put no ranked list back expected null, but was AvailabilityForecast(...)" |
| R9 | suggestion searches again | SpeciesSuggestionKeepsSearchOpenTest | 1/2 | "no ranked-list fetch ran expected 1 but was 2" |
| R10 | suggestion closes the dropdown | SpeciesSuggestionKeepsSearchOpenTest | 2/2 | "the search dropdown is still open" |
| R11 | map reads the current filter | SpeciesSuggestionKeepsSearchOpenTest | 1/2 | "no sightings fetch ran expected 1 but was 2" |
| R13 | Clear back to a 48 dp target | SearchBarClearTest, LandscapeLRulingsTest | 1/4, 6/18 | "the bar's height with Clear expected 45 but was 61"; "at rest the L's top 45.0 is not above the search bar's bottom 61.0" (and B1's "still rests at the search bar's bottom 61.0") |
| R12 | guidance back as on `main` | TripWindowsGuidanceTextTest | 3/4 | "found '1' node ... 'Rain and fungi: the general pattern'" (two), "... 'Rain and plants: no pattern to offer'" |

Each failure is one that its own edit could produce, and none names a test another edit would break.

## Full suite

**Final run (after `c85dcc6a`): 4,410 tests in 564 classes, 24 skipped, 2 failures, 0 errors** (counted from the JUnit XML,
which agrees with Gradle's "4410 tests completed, 2 failed, 24 skipped"). The results directory was emptied before the run,
so every XML is from it.

**The 2 failures are existing tests broken by the intended -725 change, and are not touched** (the dispatch: report any
existing test that breaks, with its reason, before touching it). Both in `AvailabilityViewModelSeasonalPatternTest`:
- `changing the category invalidates the cached seasonal pattern and refetches on next open`: it picks a species with
  `onTaxonSearchResultSelected` and expects the seasonal pattern cleared and fetched again. Since -725 a pick runs no search,
  so the pattern for the search that ran stays (`expected null, but was FruitingLagDistribution(... Fungi ...)`).
- `an IconicCategory filter and a SpecificTaxon filter both reach the seasonal fetch unchanged`: it picks a species and
  expects the seasonal fetch to see the species filter; since -725 it sees it only once a search runs with it.
Both premises are the behaviour the owner removed ("no search yet"). The likely rewrite: after the pick, run the search
(`searchManualCoordinates`), then expect the refetch with the species filter; that keeps what each test is about (a new
filter invalidates the cache; both filter kinds reach the fetch). Not done: it is the owner's or planner's call.

**First run (before `c85dcc6a`): 4,409 tests, 24 skipped, 15 failures.** The same 2, plus 13 landscape tests that my first
Clear broke by growing the bar (see section 3). Those were fixed in my code, not in the tests; all 13 pass in the final run.

Free disk: 3.6 GB at the start of the session, 3.3 GB after the final run.

## Gradle

- Waited for `t6b-night` to read `inactive` (12:14:20 UTC, about 7 minutes after the first check); did not touch it.
- Every run: `systemd-run --user --scope -q -p MemoryMax=5G -p MemorySwapMax=0`, Gradle `-Xmx1536m`, Kotlin daemon 2g, Java
  temp `~/.cache/forager-test-tmp` (via `JAVA_TOOL_OPTIONS`), a fresh daemon per scope, `./gradlew --stop` and the Kotlin
  daemon stopped at the end of each scope. Compiled first (main and tests) before any test run.
- Free disk: 3.6 GB at the start, 3.3 GB at the end, never under 1.5 GB.

## 6. RECORD -728, the second round

The planner's message, with the owner's words quoted in it. Commit `f3083b77`.

1. **The two seasonal-pattern tests** (`AvailabilityViewModelSeasonalPatternTest`): each now runs the search
   (`searchReferenceRegion()`) after the species pick, citing -725 and -728. The assertions are unchanged. Both pass.
2. **Clear's size.** The owner: "Keep 29 dp tall, wide (Recommended)". The bar's height is kept, and Clear is
   `height(fieldHeight)` and `widthIn(min = 48.dp)`. Measured: the field's height, and wider than 48 dp. It was already 58 dp
   wide, from Material's button minimum width, so the `widthIn` is a guard. Five real touches across it reach it (the
   existing test). R14 (Clear forced to 30 dp wide) fails: "Clear is at least 48 dp wide (30.0.dp)".
3. **"Facing" removed.** The owner: "Keep the the 330° NW metric, just remove the word "facing" and nothing else."
   `HEADING_LABEL` is deleted. The strip's `stripHeadingLabel` returns no label, and the navigation display's second row
   passes none. The heading value and "Alt" are unchanged. `readoutsFitBeside` is unchanged: the heading's label now costs
   0 px. Data part B's tests (`AvailabilityScreenNavigationWordsTest`) no longer reference the label. Their "Facing" checks
   became `assertNoFacing()`, citing -728, and two tests were added there.
   **The first revert (R15) did not bite.** At 360 dp and in landscape the labels drop for width anyway, so "Facing" put
   back was never drawn and those tests passed both ways. New class `NoFacingLabelTest`, at 384 dp (the S22's portrait
   width), where labels fit. Its positive control is that "Alt" is drawn. R15b fails both of its tests: the display's
   labels read `[Facing, Alt]`; on the strip, at 14 sp, the longer labelled line no longer fits, so both labels drop
   (`[]`).
4. **The hang-down, found and measured** (semantic bounds, 384 dp portrait, Maps tab with a fix). Before: the bar is 0 to
   45 dp, its divider 40 to 41 dp, and the strip starts at 45 dp. The layout bounds had no overlap and no gap. What the
   owner saw is 4 dp of the bar's own 80% fill below its divider: the Column's bottom padding (`padding(vertical = xs)`).
   The divider reads as the bar's edge, so that band read as the top of the strip. Fix: the padding moved above the
   divider (`padding(top = xs)`, `Spacer(xs * 2)`). The divider is now the bar's last 1 dp, at 44 to 45, and the strip
   starts at 45. The bar is still 45 dp, so the scaffold's `searchBarHeight` and everything placed by it are unchanged.
   `SearchBarStripSeamTest` checks divider bottom = bar bottom = strip top, and a bar of 45 dp. R16 (the padding back)
   fails: "the divider is the bar's last line ... expected 45.0 but was 41.0". Not visible to Robolectric: whether the
   divider's colour against the strip reads as one seam on a phone (device step 6).
5. **14 sp.** The owner: "Go up to 14 sp". `STRIP_READOUT_FONT_SIZE = 14.sp`, `STRIP_READOUT_LINE_HEIGHT = 20.sp` and
   `stripReadoutStyle()` are in `ui/availability/AvailabilityMapControlsUi.kt`, with the owner's words. Applied to the
   strip's readout line: heading, altitude and its label, coordinates, the dots, "Location services unavailable" and the
   position note. **Not applied** (my reading of "the strip's readout text"): the sundown and Back by lines under the
   readout, the navigation display, and the search bar. The bar's field height is still twice labelMedium's line (the
   owner's earlier ask, and the bar must stay 45 dp), so it is no longer twice the strip's text line. The strip is still
   36 dp tall (the three-dot button; checked ≥ 36 dp). `SearchBarStripSeamTest` reads 14 sp from each text's laid-out
   style. R17 (back to labelMedium) fails: "compass-strip-heading is 14 sp expected 14.0 but was 12.0".

**What the larger text drops** (measured with a probe, removed after use: native graphics, heading 315° NW, altitude
9843 ft, MGRS "10T ER 24991 40768" and decimal "45.5200, -122.6800"; "Facing" already removed in both columns). Every
coordinate stays whole, and every value shown is whole, as `readoutsFitBeside` requires.

| Width | Coordinates | 12 sp | 14 sp |
|---|---|---|---|
| 360 x 640 portrait | MGRS and decimal | heading, Alt label, altitude | heading, altitude; **the Alt label drops** |
| 384 x 823 portrait (S22) | MGRS and decimal | heading, Alt label, altitude | heading, Alt label, altitude (no change) |
| 780 x 360 landscape (S22) | MGRS | altitude only (heading dropped) | altitude only (no change) |
| 780 x 360 landscape (S22) | decimal | heading, altitude | altitude only: **the heading drops** |

So at 14 sp, two things that showed at 12 sp drop: the "Alt" label at 360 dp portrait, and, in a short landscape window
showing decimal coordinates, the heading itself (the needle still points). No test was changed for this. Every existing
fit test (data part B's 360 dp and landscape, Back by's) passes, because each asserts the drop order and wholeness, not
that a given readout is shown. Whether the landscape heading drop is acceptable is the owner's call.

**Revert checks, second round** (same runner; all compiled, all fresh, all restored byte-identical; the forward change was
committed before R15b and confirmed present after it):

| # | Edit | Class | Failed | Message |
|---|---|---|---|---|
| R14 | Clear 30 dp wide | SearchBarClearTest | 1/4 | "Clear is at least 48 dp wide (30.0.dp)" |
| R15 | "Facing" back | AvailabilityScreenNavigationWordsTest | **0/8** | did not bite: the labels drop at 360 dp and in landscape anyway |
| R15b | "Facing" back | NoFacingLabelTest | 2/2 | display labels `[Facing, Alt]`; strip labels `[]` (the labelled line no longer fits) |
| R16 | bar padding back below the divider | SearchBarStripSeamTest | 1/2 | "divider ... expected 45.0 but was 41.0" |
| R17 | strip back to 12 sp | SearchBarStripSeamTest | 1/2 | "compass-strip-heading is 14 sp expected 14.0 but was 12.0" |

**Full suite after -728: 4,416 tests in 566 classes, 24 skipped, 0 failures, 0 errors** (from the JUnit XML; Gradle "BUILD
SUCCESSFUL"; results directory emptied first). `t6b-night` read inactive before the run and was not touched. Free disk 3.3 GB.
`./gradlew --stop` at the end; no Gradle or Kotlin process left.

Device step 6: on the S22, with a fix on the Maps tab, the bar's divider meets the strip with no band of bar colour under it,
and the strip's readout is visibly larger (14 sp). In a short landscape window with decimal coordinates, the heading is not
shown (expected, above).

## Device only

Robolectric reports no keyboard, so the keep-in-view's trigger on a phone (the cap following `WindowInsets.ime`) is device
only. Steps, cheap first, on the S22:
1. Maps tab, tap the search bar. Pass: as the keyboard rises, the panel's bottom row (Set on map, Search) ends just above it.
   Evidence: a screenshot (kept off GitHub).
2. Drag the panel up to the coordinates, tap Latitude. Pass: the panel stays where it was dragged.
3. Search somewhere, then tap that same search in Recent searches. Pass: its observations are on the map. Then tap a
   **different** recent search. Pass: its observations are on the map. If step 3's second half fails, the owner's report has
   a second cause this build did not reproduce.
4. Type a species, tap a suggestion. Pass: the list closes, the panel and keyboard stay, the bar shows the species, the map
   does not change.
5. With a search showing, tap Clear. Pass: map empty, bar reads "Search a location", Recent searches still listed.

## Commits

`1426522e` (-722), `4902a2dd` (-723 recent search), `fa2795a4` (-723 Clear, -725/-726, -727), `1b403fb9` (-725 map reads the
search last run), `c85dcc6a` (Clear's height), `273f1978` (report), `f3083b77` (-728), and this report's update. Pushed to `origin/search-keyboard`. No PR.

## Not done

- **"Merge"** in the -723 relay is not done: the dispatch says "No PR", and the relay does not say what to merge into what.
  `origin/main` is still `bfbba33f`, the base, so the branch needs no merge from it. Merging into `main` is left for the
  planner's word.
- The unread constant in section 5. (The two seasonal-pattern tests were rewritten in -728, section 6.)
- The PR: the planner opens it (-728).
