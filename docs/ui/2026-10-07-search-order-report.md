# Search dropdown reordered: code and tests written, nothing compiled yet

Dispatch 2026-09-28-697 (RECORD intent -697; preserved at `prompts/preserved/2026-10-07-17.md`), with the owner's addition relayed
by the planner: "Search on the right, set on map on the left". Branch `search-order`, cut from `origin/main` at `b71c1569` after a
fresh fetch. No PR.

**Superseded in part by Amendment 2 (RECORD -700), below.** The stop on how Search reaches the current position is answered;
what Search runs, the new "Search coordinates" button and the two banners changed. The sections between here and Amendment 2 are
left as they were written, as the record of the first pass.

**Status: built and run on the planner's go (RECORD -710); see "The build" at the end, which supersedes every "not compiled or run" above.** The dispatch holds Gradle for the planner's go. Nothing on this branch has been compiled, no test has
run, and no revert check has been done. Every claim below about behaviour is what the code is written to do, not something observed.

Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/` or `docs/`.

## Premises checked

| Premise in the dispatch | Found | Where |
|---|---|---|
| The dropdown body is about `AvailabilitySearchUi.kt:405-505` | True | `ui/availability/AvailabilitySearchUi.kt:405-505` at `b71c1569` |
| "Search this location" and "Use current location" are today's two search buttons | True | same file, `:433-437` and `:498-503` at `b71c1569` |
| `coordinatesKeptInView` is the scroll-to-bottom-on-open | True, with a second part: `scrollToCoordinatesPending` (`:385`, `:473-481`) does the one scroll, `coordinatesKeptInView` (`:395-404`) keeps the end in view after it | same file |
| The coordinates are "Prefilled with the current position, as today" | **False.** Nothing fills the fields when the dropdown opens. They start empty (`ui/availability/AvailabilityUiState.kt:37-38`) and are written only by a successful "Use current location" (`ui/availability/AvailabilityViewModel.kt:485-486`), Set on map's OK (`ui/availability/AvailabilityCompactScaffold.kt:1135-1136` at `b71c1569`) and a recent search (`AvailabilityViewModel.kt:902-903`). So they hold the last of those, or nothing. | as cited |

The false premise is what the stop below is about.

## Stop: what Search does about the current position

The two paths differ, which the dispatch named as a stop.

**"Use current location"** (`MainActivity.kt:502-509`, `:281-283`; `ui/availability/AvailabilityViewModel.kt:475-498`): asks for
location permission; granted, it takes one live fix, writes it into both fields and searches there; denied, it sets
`locationPermissionDenied` and the banner reads "Location permission was denied. Open search options and enter coordinates
manually."; no fix, the banner reads "Couldn't determine your location. Enter coordinates manually instead."

**"Search this location"** (`MainActivity.kt:522`; `AvailabilityViewModel.kt:463-473`): parses the two fields; blank or out of
range, the banner reads "Enter a valid latitude (-90 to 90) and longitude (-180 to 180)." and nothing is searched; valid, it
searches there.

As written on this branch, **Search runs the second path only**, which is what the dispatch's words say ("Search searches the
coordinates in the fields"). That leaves the dropdown with no way to search where the user stands: on a first launch the fields are
blank and Search shows the error; after any search they hold that search's place. The map's locate-me button only pans the map
(`AvailabilityViewModel.kt` `locateMe`, documented as touching no search state). `SearchDropdown` keeps its `onUseCurrentLocation`
parameter, still wired by its caller and drawn by no control, so any of the options below is a small change. The options, for the
owner:

- **A. As built.** Search searches the fields as they stand. Blank fields show the validation error. The current position is reached
  only by Set on map. Simplest; it asks the user to know their coordinates or find themselves on the map.
- **B. Blank fields search the current position.** Search with both fields blank runs the "Use current location" path (permission,
  one fix, fill, search); with anything typed, the coordinate path. After any search the fields are no longer blank, so getting back
  to "where I am" means clearing both fields, which nothing on screen says.
- **C. Prefill on open from the live position.** Each time the dropdown opens, the fields are filled from the position the compass
  strip already shows (`AvailabilityUiState.liveFix`, `ui/availability/AvailabilityUiState.kt:286`), so "left as prefilled, that is
  the current position", as the dispatch describes. Typing over them stands until the dropdown closes. With no live position (no
  permission yet, or no fix) the fields would fall back to B's path or stay as they are; which one is a further choice. Cost: the
  fields no longer show the last search's place on reopening (the bar's summary and Recent searches still do).

C matches the owner's words most closely but is new behaviour, not "as today", so it is not built.

**Copy, not changed:** both banners above say "enter coordinates manually", which was also the name of the removed fold. They still
read as an instruction, and the coordinates are now the dropdown's first row. Flagged, not edited.

## What was built

Commits `0777e671` (code) and `fdcc63ab` (tests), pushed to `origin/search-order`.

`ui/availability/AvailabilitySearchUi.kt`, `SearchDropdown`, top to bottom:

1. Latitude and Longitude, side by side, no heading, no fold. Same fields, same callbacks, same validation (in the ViewModel,
   unchanged). Test tags added: `SEARCH_DROPDOWN_LATITUDE_TAG`, `SEARCH_DROPDOWN_LONGITUDE_TAG`.
2. Month (`MonthSelector`, unchanged).
3. "Search radius: …" and its slider (unchanged).
4. Recent searches, in its own fold (unchanged), after a divider.
5. After a divider, one row: **Set on map** (outlined, left; `SEARCH_DROPDOWN_SET_ON_MAP_TAG`) and **Search** (filled, magnifier
   icon, right; `SEARCH_DROPDOWN_SEARCH_TAG`), each half the width, where "Search this location" was. Set on map calls the same
   `onSetOnMap`; Search calls the same `onSearchManualCoordinates` "Search this location" called, which the caller wraps to close the
   dropdown first, as before.

Removed: "Use current location", "Search this location", the "Advanced search" and "Enter coordinates manually" folds, the scroll to
the end on open and the keep-in-view after it (`scrollToCoordinatesPending`, `coordinatesKeptInView`), the
`expandManualCoordinatesRequested`/`onManualCoordinatesExpandConsumed` parameters, and their arming in
`AvailabilityCompactScaffold.kt` (it was armed on each opening). `CollapsibleSection` lost its `expandRequested`/
`onExpandRequestConsumed` pair, whose only caller was those two folds (checked by grep across `app/src`); its log-package caller
never passed it. Unused imports went with them.

Kept: the scroll itself (`verticalScroll`, the panel can still be taller than its room) and its "a scroll lowers the keyboard"
effect; the bar's tap opening the dropdown; the closing panel taking no touch; Back; the 80% fill (the `Box` and its background are
untouched).

## Tests

New, `app/src/test/.../ui/availability/`:

- `AvailabilityScreenLayoutTest` (runs on three configurations: small dense phone, the same at 2x font, large phone):
  - `the search dropdown runs coordinates, Month, Search radius, Recent searches, then Set on map left of Search`: by laid-out
    bounds; Latitude and Longitude share a row; each group's top below the one before; Set on map and Search share a row with Set on
    map's right edge at or left of Search's left edge; "Set on map" once; "Use current location" and "Search this location" absent.
  - `real touches across the Search button run the coordinate search and close the dropdown`: five real touches at screen
    coordinates (centre and four points towards the corners), each on a freshly opened dropdown; each must call
    `onSearchManualCoordinates` once more and close the dropdown; `onUseCurrentLocation` never called.
  - `real touches across Set on map close the dropdown and open the centre-pin picker`: five sampled real touches; each closes the
    dropdown and brings up OK and Cancel; no search runs; Cancel between samples.
- `SearchDropdownSearchesTheFieldsTest` (real screen, field state held as the ViewModel holds it): Search with the fields left as
  they are records their values; typing over them and touching Search records the typed values. Its doc comment says plainly that
  the "left as they are" case starts from fields holding coordinates and is **not** evidence that Search reaches the current position.

Every changed assertion, with its reason:

| File | Was | Now | Why |
|---|---|---|---|
| `AvailabilitySearchSummaryCopyTest` helper (portrait and short-landscape tests) | "Use current location" and "Set on map" displayed (portrait) or existing (landscape, scrolled out); "Collapse Advanced search" and "Collapse Enter coordinates manually" exist; Latitude, Longitude, "Search this location" displayed | Latitude and Longitude displayed; Latitude's top exactly the panel's 16 dp padding below the panel's top (±0.5 dp), so not scrolled; Set on map and Search exist; the four removed labels absent | The labels and folds are removed; the landscape case used to assert the scroll the dispatch removes, and now asserts there is none |
| same, `in portrait the user's own collapse of the manual coordinates stands…` | the user's collapse of "Enter coordinates manually" stands | **deleted** | The fold it collapsed no longer exists; there is nothing left for it to test. Reported here as a removed test, not a silenced one |
| `SearchDropdownKeyboardTest` T3a | after a 360 dp shrink, Latitude, Longitude and "Search this location" displayed | Search and Set on map displayed at 800 dp; after the shrink, Latitude and Longitude displayed | "Search this location" is gone and the bottom row is no longer kept in view; the claim kept is the fields staying in view |
| same, T3b | after the user's drag, a later viewport change leaves the scroll alone ("Set on map" displayed, "Search this location" not) | Latitude 16 dp below the panel's top at 800, 360, 420 and 360 dp | The programmatic scroll it guarded is removed; the claim now is that nothing but the user scrolls the dropdown |
| `SearchDropdownClosingTapThroughTest` | touches "Use current location" (`onUseCurrentLocation` count) | touches Search (`onSearchManualCoordinates` count); added: the touch point is inside the closing panel when the touch lands | The button is gone. Search is now at the panel's bottom rather than its top, so the added assertion stops the mid-close test passing on a point the shrinking panel had already left |
| `AvailabilityScreenLayoutTest` Test 5 | "Collapse Advanced search", "Collapse Enter coordinates manually" exist; "Search this location" displayed | Latitude and Longitude displayed; the two fold labels absent | Folds and button removed |
| same, radius/month/location-row test | "Set on map", "Use current location", radius, Month reachable; "Advanced search" displayed and expanded; each button once | replaced by the order test above | It asserted the old structure; the order test asserts the new one |
| same, `the drawer's Use current location button calls onUseCurrentLocation` | the button calls the callback | replaced by the Search real-touch test | The button is removed |
| `searchAReferenceRegion` in `AvailabilityScreenBackNavigationTest`, `…TripPlanningFlowTest`, `…ConditionsMonthTest`, `…WaypointFlowTest`, `…MapIconStackTest` | clicks the "Search this location" text | clicks `SEARCH_DROPDOWN_SEARCH_TAG` | Navigation only; no assertion changed. These still drive the real ViewModel's coordinate search through the new button |
| `AvailabilityScreenMapIconStackTest`, the `@Ignore`d Set on map real-touch test | tapped "Advanced search" before touching Set on map | that tap removed, with a comment | The fold is gone. The `@Ignore` itself is untouched |

## Not verified, and what the go should run

- **Nothing compiled.** Kotlin errors are possible; the revert runner must read the compile log before any result.
- **The suite.** On the go: the classes above plus every class that opens the dropdown (`AvailabilityScreenAdaptiveLayoutTest`,
  `…BubbleAndDropdownBackTest`, `…DropdownCloseOutOfTouchModeTest`, `…FanBackOthersTest`, `…LandscapeB2Test`,
  `…OfflineCacheTest`, `LeavingTheJournalFixesTest`, `MapChromeColourTest`, `MapChromeColourPixelsTest`, `MapChromeOverMapTest`),
  then the full suite. `MapChromeColourPixelsTest` samples the bare map 200 dp above the screen's foot; the panel is expected to be
  no taller than before (two fold headers and one button fewer), but that is not measured.
- **Revert checks, planned, not run.** Each restores from a saved copy, reads the compile log first, and confirms the forward change
  is back afterwards: (1) swap the bottom row's two buttons: the order test must fail on "Set on map … is left of Search";
  (2) wire Search to `onUseCurrentLocation`: the Search real-touch test and `SearchDropdownSearchesTheFieldsTest` must fail on their
  counts; (3) restore the scroll to the end on open: `SearchDropdownKeyboardTest` T3b and the short-landscape summary test must fail
  on the 16 dp offset; (4) move the coordinates row below Month: the order test must fail on "Month … is below the coordinates".
- **The closing tap-through on Search** depends on the shrinking panel still covering the bottom row two frames into the close.
  The new containment assertion will say if it does not; if so the test needs a different target, which is a finding to report.
- **Device only:** with the keyboard up in portrait, whether the bottom row is reachable (the panel's cap follows the keyboard,
  `AvailabilityCompactScaffold.kt`, Robolectric shows no keyboard); how the reordered panel looks at the 80% fill.
- **The stop above is unresolved.** Until the owner chooses, the dropdown cannot search the current position.

## Amendment 2 (RECORD -700): the stop answered

The planner relayed the owner's answers, verbatim:

1. "There are no fields to fill in. Search is the same as "Use current location" just renamed and relocated." The bottom-right
   **Search** now calls `onUseCurrentLocation`, the old "Use current location" path unchanged (permission, one fix, the fields
   written, a search), and reads no field.
2. "Small button under the fields". A text button, **"Search coordinates"** (`SEARCH_DROPDOWN_SEARCH_COORDINATES_TAG`), directly
   under Latitude and Longitude, calls `onSearchManualCoordinates`, the old "Search this location" path; validation and its error
   text are unchanged.
3. "Option 1, but only refer to "Set on map" since that is right there." The banners now read exactly
   "Location permission was denied. Tap Set on map to choose a place." (`LOCATION_PERMISSION_DENIED_MESSAGE`,
   `ui/availability/AvailabilitySearchUi.kt`, read by `searchNoticeMessage`) and
   "Couldn't find your location. Tap Set on map to choose a place." (`COULD_NOT_FIND_LOCATION_MESSAGE`,
   `ui/availability/AvailabilityViewModel.kt`, set by `useCurrentLocation` on no fix). The copy flag above is closed by this.

The layout, top to bottom: Latitude and Longitude, "Search coordinates", Month, Search radius, Recent searches, then Set on map
(left) and Search (right).

**No existing test read either old banner string** (grep of `app/src/test` and `app/src/androidTest` for "permission was denied",
"determine your location" and "coordinates manually" found only the removed fold's label). So no assertion on them changed; three
new tests read the new strings.

### Tests changed by Amendment 2

| File | Was (first pass) | Now | Why |
|---|---|---|---|
| `AvailabilityScreenLayoutTest`, Search real touches | five sampled touches each call `onSearchManualCoordinates`; `onUseCurrentLocation` never | renamed `…run the current-location path…`; each touch calls `onUseCurrentLocation`; `onSearchManualCoordinates` never | Search is "Use current location" renamed (item 1) |
| same, new | — | `real touches across Search coordinates run the typed-coordinates search and close the dropdown`: five sampled touches, each calls `onSearchManualCoordinates` and closes the dropdown; `onUseCurrentLocation` never | Item 2 |
| same, order test | coordinates, Month, radius, Recent, bottom row | adds "Search coordinates" under the coordinates' bottom and above Month's top | Item 2's position |
| same, Set on map real touches | no coordinate search | also no current-location search | Set on map now sits beside a button that runs the current-location path |
| `SearchDropdownSearchesTheFieldsTest` | two tests, one centre touch on the bottom Search | both on "Search coordinates", each with five sampled touches on a freshly opened dropdown, each asserting no current-location call; new third test: the bottom Search, with a field typed over, runs the current-location path once and no coordinate search | Items 1 and 2 |
| `SearchDropdownClosingTapThroughTest` | Search counted through `onSearchManualCoordinates` | counted through `onUseCurrentLocation` | Item 1; the touch target and the containment assertion are unchanged |
| `SearchDropdownKeyboardTest` T3a | after the shrink, Latitude and Longitude displayed | also "Search coordinates" displayed | The button belongs with the fields |
| `AvailabilitySearchSummaryCopyTest` helper | Set on map and Search exist | also "Search coordinates" displayed | Item 2; this also runs in short landscape (w823dp-h384dp), where it is not measured whether the button fits below the fields without scrolling. If it fails there, that is a finding about the layout, to report rather than relax |
| `searchAReferenceRegion` helpers (BackNavigation, TripPlanningFlow, ConditionsMonth, WaypointFlow, MapIconStack) | type coordinates, click the Search tag | click the "Search coordinates" tag | Navigation only; Search no longer runs a typed search |
| `AvailabilityViewModelLocateMeTest`, three new | — | `useCurrentLocation` with no fix: the banner is the "Couldn't find your location…" string and no region is set; with `PermissionDenied`: the "Location permission was denied…" string; `onPermissionDenied()` (the OS dialog's denial): the same string | Item 3, exact strings, read through `searchNoticeMessage`, what the banner shows |

### Revert checks for Amendment 2, planned, not run

Same rules as above (saved copy, compile log first, forward change confirmed after). (5) Wire Search back to
`onSearchManualCoordinates`: the Search real-touch test, the tap-through positive control and the "bottom Search reads no field" test
must fail on their current-location counts. (6) Wire "Search coordinates" to `onUseCurrentLocation`: its real-touch test and both
fields tests must fail. (7) Restore either old banner string: the matching `AvailabilityViewModelLocateMeTest` test must fail on it.

### Unverified, added by Amendment 2

- The banner messages are tested headless (ViewModel and `searchNoticeMessage`), not through a touch on Search on the real screen:
  the screen tests wire `onUseCurrentLocation` to a recorder, and on the phone the path goes through `MainActivity`'s permission
  launcher, which no Robolectric test here drives.
- Whether "Search coordinates" fits under the fields in short landscape without scrolling (above).

## Amendment 3 (RECORD -702): data part D folded in

The owner: "All of data part D, with search (Recommended)", carrying RECORD -656's dates, Finds and times. Written on top of
`72dfa4e4`; base still `origin/main` `b71c1569` (fetched again first, unchanged; data parts A, B and C are all on it). Code and
tests only, **nothing compiled or run**.

### What changed

One shared formatter, `app/src/main/java/com/zynergylabs/forager/app/ui/format/DisplayDates.kt`: `displayDate` ("Oct 7, 2026"),
`displayTime` ("2:14 PM" / "14:14"), `displayDateTime`, and the composable `is24HourClock()` reading the phone's setting.
`ui/availability/DisplayDates.kt` (part C) and `ui/log/EntryDates.kt` (part A) are deleted and their callers moved to it.

Every ISO date and hard-coded format found by grep in `ui/` (`ofPattern`, `toString()` on a date, `"h:mm a"`), and what became of it:

| Where | Was | Now |
|---|---|---|
| `ui/map/JournalEntriesOnMap.kt` `journalEntryDateLabel` (the map's journal menu, bubble date lines, their "Open entry …" labels) | `2026-09-12` | `Sep 12, 2026` |
| `ui/map/MapBubbles.kt` find bubble | `Find on 2026-09-12` | `Find on Sep 12, 2026` |
| `ui/map/MapBubbles.kt` planned-trip bubble; `AvailabilityTripsWaypointsUi.kt` trip row and its "Remove planned trip for …" label | `Oct 3` (`TRIP_WINDOW_DATE_FORMAT`, removed) / ISO in the label | `Oct 3, 2026` |
| `ui/map/layers/MapLegend.kt` `legendDatesLine` (legend and forecast cell bubble) | `Week of 2026-09-28, weather to 2026-09-26` | `Week of Sep 28, 2026, weather to Sep 26, 2026` |
| `AvailabilityMapOverlaysUi.kt` sighting date | its own `MMM d, yyyy` copy | the shared `displayDate` (same text) |
| `ui/log/LogEntryDetailScreen.kt`, `LogEntryReportScreen.kt` find titles | `Find on 2026-08-01` | `Find on Aug 1, 2026` |
| `ui/log/CartographyEntryListScreen.kt` long-click label | `Options for entry on 2026-09-20` | `Options for entry on Sep 20, 2026` |
| `ui/track/TrackExportPanel.kt` `trackTitle`/`formatRecordTimestamp` (Records rows, track bubble, waypoint "Created", region "Downloaded", photo bubble) | always `h:mm a` | follows the phone; `formatRecordTimestamp` removed |
| `ui/log/RecordDetailsSheet.kt` `formatSheetTimestamp` (part B) | its own copy | removed; the shared `displayDateTime`. The unnamed walk's sheet title and Records row now agree on a 24-hour phone (part B's item 8) |
| `ui/crash/CrashLogPanel.kt` crash list times | always `h:mm a` | follows the phone |
| `ui/log/FindsGalleryScreen.kt` Finds grid | flat grid, tiles "Find on 2026-10-07" | a heading per day ("Oct 7, 2026"), newest day first; each tile shows the find's given name, else "Found 2:14 PM" (phone's 12/24-hour), else the placeholder below. The Records logbook's find tiles are the same tile, so they change too |

Already following the phone, unchanged: `SundownLineText.kt`, `EntryReportSummary.kt` and the alert notifications
(`DateFormat.getTimeFormat`). Not display, unchanged: stored keys, file names, GPX, crash-file and API dates.

The name on a tile is `MushroomLogEntry.ownIdentification`, the one name a user gives a find (inferred: the model has no other
name field; it is user-entered, never app-generated). A tile therefore never names a species the user did not pick.

### Stops

**Answered by the owner in Amendment 4 (RECORD -703), below; left as written here.**

1. **A find stores no time of day.** `MushroomLogEntry.foundOn` is a date (`domain/model/MushroomLogEntry.kt`); the entity has
   no time column. "Found 2:14 PM" is built from the earliest of the find's photos taken on its own day
   (`LogPhoto.createdAtEpochMillis`); a photo from another day (an import) is not used. A find with no such photo shows
   **"Unnamed find", a placeholder I chose**, to be replaced. Options: (a) keep the photo time, with copy the owner picks for
   the rest; (b) also record a found-at time on new finds (a new Room column and migration; existing finds would still have none);
   (c) something else the owner prefers.
2. **Dates with a weekday.** The Records logbook's day headers and the Album's day headers read "Sat, Sep 26, 2026"
   (`RecordsLogbookList.kt` `DAY_HEADER_FORMAT`, `EntriesAlbum.kt` `ALBUM_DAY_FORMAT`). Left unchanged: keep the weekday, or drop
   it to "Sep 26, 2026"?
3. **Automatic waypoint names**, "Start · Sep 5, 9:41 AM" (`domain/AutoWaypointName.kt`), are stored as the waypoint's name when
   a recording starts and ends: no year, always 12-hour, and in the domain layer, with no phone setting there. Left unchanged.
   Change them for new waypoints (old names stay as stored), or leave them?
4. **The find's own pages and its map bubble** still title an unnamed find "Find on Oct 7, 2026" (only the date format changed).
   -656 speaks of the tile; should the bubble and the report title follow the tile's rule?
5. **Decided by me, flagged:** the Drafts tab is grouped by day too; days are ordered newest first, as the Records logbook
   orders them (the finds table has no order of its own, `MushroomLogDao.kt:28`), so the Finds tab's tile order changes from
   storage order to by-day. The "+" tile stays first, alone on its row, above the first heading.

Not changed and not display: the `snippet` properties in `ui/map/SightingsMap.kt` (`trip.date.toString()`,
`observedOn?.toString()`) are written but no code reads them (grep for `"snippet"`).

### Tests changed by Amendment 3

| File | Was | Now | Why |
|---|---|---|---|
| `FindsGalleryScreenTest` (six lookups) | tile text `Find on 2026-08-01` / `-02` | the day heading `Aug 1, 2026` / `Aug 2, 2026` | the date moved from the tile to the heading |
| same, the bare-find test | the date alone on the tile | the heading, plus the tile's title (the placeholder) | as above |
| `JournalTabTest` (six lookups) | `Find on ${foundOn}` | `UNNAMED_FIND_NO_TIME` (the fixture has no name and no photo) | tile title |
| same, Cancel on a new entry | no "Find on" text anywhere | no placeholder tile and no heading for today | "Find on" no longer appears on any tile, so its absence would pass whatever happened |
| `JournalPageSlideTest` `findTile` | `Find on ${foundOn}` | `UNNAMED_FIND_NO_TIME` | tile title |
| `JournalPendingDeleteTest` | tiles told apart by `Find on <date>`; long-press label `Options for Find on 2026-09-20` | fixtures named "Morel" and "Oyster", tiles told apart by name; label `Options for Morel` | two unnamed finds would have the same title. **Fixture change**, not only an assertion |
| `RecordsFilterChipsTest` | `Find on ${CHIPS_FIND.foundOn}` exists; `Find on $d1` count 2 | the placeholder tile and the day heading exist; the placeholder inside each of F1's and F2's rows | tile title; the per-row check keeps the old "d1's two tiles" claim |
| `LeavingTheJournalFixesTest`, `DrawerBackOverJournalTest` `FIND_TILE_TEXT` | `Find on 2026-08-02` | `Chanterelle`, the fixture's own name | tile title |
| `MapBubblesTest` | `Find on 2026-09-12`; trip `Oct 3`; cell `Week of 2026-09-28, …`; photo date through `formatRecordTimestamp` | `Find on Sep 12, 2026`; `Oct 3, 2026`; `Week of Sep 28, 2026, weather to Sep 26, 2026`; `displayDateTime(…, false)`; every call passes `is24HourClock = false` | date format; the new required parameter |
| `MapLegendTest`, `AvailabilityScreenMapLayersTest`, `AvailabilityScreenMapBubblesTest` | `Week of 2026-09-28, weather to 2026-09-2x`; trip `Oct 3` | the same in "Sep 28, 2026" form; `Oct 3, 2026` | date format |
| `JournalEntriesOnMapTest`, `JournalEntriesChipTest`, `JournalEntriesOnMapScreenTest` | `2026-09-12`, `Open entry 2026-09-12` and the like | `Sep 12, 2026`, `Open entry Sep 12, 2026` | the journal menu and bubble dates; -656 supersedes Q3 |
| `AvailabilityScreenLayoutTest` | `Remove planned trip for ${trip.date}` (ISO) | `… ${displayDate(trip.date)}` | date format |
| `TrackSheetTilesTest` | `formatSheetTimestamp(…, is24Hour = …)` | `displayDateTime(…, is24HourClock = …)`, same expected text | the formatter moved |
| `TrackSheetDataTest`, 24-hour test | the sheet's title and Started in 24-hour time | also: the Records row for the same unnamed walk reads the same 24-hour text | part B's item 8, the mismatch this part closes |
| `ImportedTrackBubbleTest`, `EntryLabelsTest`, `SeasonalChartModelsTest` | — | the new parameter / the shared import | mechanical |

New: `FindsGroupedByDayTest`: headings newest first with each tile under its own day, by laid-out bounds; a named tile shows
its name and an unnamed one no species; "Found 2:14 PM" on a 12-hour phone and "Found 14:14" on a 24-hour one
(`Settings.System.TIME_12_24`); the earlier of two same-day photos is the time; a photo from another day gives the placeholder;
real touches at five points across each of two grouped tiles open that find through `onOpenEntry`; and headless checks of the
title rule and the grouping.

### Unverified, added by Amendment 3

- Nothing compiled; Kotlin errors are possible, especially at the new `is24HourClock` parameter's call sites.
- Under Robolectric the 12-hour default is assumed for every existing test that reads a 12-hour time (as part B's tests did).
- The Finds grid's heading row is a full-width lazy-grid item; whether a deleted find's heading disappears smoothly when its day
  empties is not checked (it goes at once; the tile fades).
- Revert checks planned, not run: (8) restore `"Find on ${entry.foundOn}"` as the tile title: `FindsGroupedByDayTest`'s name and
  "Found" tests fail; (9) drop the grouping (one flat `items`): the heading-position test fails; (10) hard-code `is24HourClock =
  false` in `trackTitle`'s caller: `TrackSheetDataTest`'s new row assertion fails; (11) restore `journalEntryDateLabel` to
  `toString()`: `JournalEntriesOnMapTest` fails on "Sep 12, 2026".

## Amendment 4 (RECORD -703): the stops answered

The owner, verbatim: (1) "Option 1, but keep it blank instead of showing "Unnamed find""; (2) "Keep the weekday there
(Recommended)"; (3) "New ones follow the new style (Recommended)"; (4) "Match the tile (Recommended)". My other calls (Drafts
grouped, newest first, "+" alone) were accepted. Code and tests only, **nothing compiled or run**.

### Database version

`origin/main` is still at 18 (`b71c1569`); `origin/guidance-text` (`450860ac`) claims 18 to 19 and is not on main, so it is
**merged** into this branch (`1b700f39`, a merge, not a rebase; the one conflict was `docs/audits/README.md`, every row kept). Before
claiming 20 I read every remote branch's `ForagerDatabase.kt`: none is above 19 and none has a `MIGRATION_19_*`. One thing that
names 20 was found and is not a claim: guidance-text's newer-backup refusal test used 20 as "a version newer than this build", the
precedent being each bump moves it up one; it is now 21 (below).

### What changed

- **The saved time.** `MushroomLogEntry.foundAtEpochMillis` and `MushroomLogEntryEntity.foundAtEpochMillis` (nullable, no
  backfill), mapped both ways in `RoomMushroomLogRepository.kt`. `CreateMushroomLogEntryUseCase` writes the moment of creation
  when the find is for the day it is created on (in the phone's zone); a find created for another day gets none, since now is not
  when it was found. Edits and Save carry it (`copy`).
- **Schema 20.** `MIGRATION_19_20` in `data/local/Migrations.kt`: a full rebuild of `mushroom_log_entries` with an explicit column
  list, as 18 to 19 and the `MIGRATION_12_13` pitfall require (the legacy fixtures declare the entity directly), both indexes
  recreated. `ForagerDatabase` at 20, `ALL_MIGRATIONS` and `SCHEMA_VERSION` updated. `app/schemas/.../20.json` is 19.json plus the
  column; **its identity hash `873e9dde…` was computed by hand** with Room 2.8.5's own rule, read from the compiler jar in the
  Gradle cache (the rule reproduces 18.json's and 19.json's hashes exactly). The build's own export must leave the file unchanged;
  if it rewrites it, that is a finding.
- **One title rule** in new `ui/log/FindTitles.kt` (`findTitle`, `foundTimeMillis`, `findBlankTitleLabel`): the given name; else
  "Found <time>" from the saved time; else from the earliest photo taken that day; else `null`, no text, with the label
  "Find, Oct 7, 2026". "Unnamed find" is gone. Used by the tile (Finds grid and Records logbook), the find's report and edit form
  headers (`LogEntryReportScreen.kt`, `LogEntryDetailScreen.kt`, tag `FIND_PAGE_TITLE_TAG`) and the map bubble
  (`MapBubbles.kt`: `title` is now nullable, with `blankTitleLabel`; `MapBubble.kt` draws no title and carries the label). A blank
  tile keeps its caption line, so tiles in a row stay the same height.
- **Weekday headers**: left as they are.
- **Automatic waypoint names**: `autoWaypointName` takes `is24HourClock` and writes "Start · Oct 7, 2026, 9:41 AM" or
  "Start · Oct 7, 2026, 09:41"; `TrackRecordingViewModel` takes `is24HourClock: () -> Boolean`, read each time it names one;
  `MainActivity` passes `DateFormat.is24HourFormat`. The VM's default, 12-hour, is for tests (as its `zone` default is). Names
  already saved keep their text. The pattern is restated in the domain file, since the domain layer does not depend on `ui`.

Interpretations, flagged: **the bubble keeps its date line** ("Find on Oct 7, 2026") under whatever title it has, because the
bubble has no day heading above it as the tile does; and **the find's own pages no longer show its date** anywhere (their header
was "Find on <date>", now the tile's title or blank). The owner may want a date line on those pages.

### Tests changed by Amendment 4

| File | Was | Now | Why |
|---|---|---|---|
| `SchemaMigrationTest` | chain 4 to 19 against 19.json | chain 4 to 20 against 20.json; new `19 to 20` test: every seeded value carried, `foundAtEpochMillis` NULL, both indexes back, the column takes a value | the new migration |
| `JournalBackupTest`, `JournalBackupTestSupport` | schema 19 in four places; the "newer" backup is 20 | 20; the newer backup is 21 | the version; guidance-text's precedent |
| `AutoWaypointNameTest` | "Start · Sep 5, 9:41 AM" (three cases) | "Start · Sep 5, 2026, 9:41 AM" etc., plus a 24-hour case "Start · Sep 5, 2026, 09:41" | item 3 |
| `TrackRecordingViewModelTest` | "Start · Jan 1, 12:00 AM", "End · Jan 1, 12:00 AM" | "… Jan 1, 1970, 12:00 AM"; new test: on a 24-hour phone the origin is "Start · Jan 1, 1970, 00:00" | item 3 |
| `JournalTabTest` (six), `JournalPageSlideTest`, `RecordsFilterChipsTest` (three), `FindsGalleryScreenTest` | the "Unnamed find" text | the "Find, <date>" screen-reader label | item 1: the placeholder is gone; these fixtures have no name, time or photo |
| `JournalTabTest`, Cancel on a new entry | no placeholder text | no "Find, <today>" label | as above |
| `MapBubblesTest`, the unnamed find | title "Find on Sep 12, 2026", no date line | no title, date line "Find on Sep 12, 2026", label "Find, Sep 12, 2026" | item 4 |
| `FindsGroupedByDayTest` | the placeholder tests and `findTileTitle` | blank-with-label; the saved time before photos; the report's title by the rule (time, name, blank with label) through `LogEntryReportScreen`; headless rule checks | items 1 and 4 |

New: `FindFoundAtTimeTest` (real use cases over a real in-memory Room repository): a find created on its day stores and reads back
its time; one created for another day stores none; the time survives a re-edit and its Save.

### Unverified, added by Amendment 4

- Nothing compiled; migration tests not run, so `MIGRATION_19_20` and `20.json` are checked by reading only.
- The bubble's blank title is tested at the content level (`MapBubblesTest`), not on screen.
- The edit form's header is not tested directly (the report's is).
- Revert checks planned: (12) drop the `foundAtEpochMillis` write in `CreateMushroomLogEntryUseCase`: `FindFoundAtTimeTest` fails;
  (13) drop it from `toEntity`: the round-trip test fails; (14) put "Unnamed find" back for `null`: the blank tests fail;
  (15) restore the old auto-name pattern: `AutoWaypointNameTest` fails.

## The build (RECORD -710)

The planner's go: "Merge search order once checks pass". Every Gradle run used `systemd-run --user --scope -q -p MemoryMax=5G
-p MemorySwapMax=0`, the Gradle heap at 1536m, the Kotlin daemon at 2g, and Java temp at `~/.cache/forager-test-tmp`. No daemon
was running before the first run. Free disk was checked before each run: 3.5 GB at the start, 3.1 GB at the lowest, never under
1.5 GB. The Kotlin daemon was stopped after every run. `./gradlew --stop` ran at the end, and no Gradle or Kotlin daemon is left.
No phone or emulator.

### Base

- `origin/main` was at 18 when the build started, as the planner said. It had moved to `d223728b` (PR #198, small-fixes-1007),
  so I merged it in first (`b73fd32d`). After the planner reported that back-by had landed (PR #199, `0f5cf0e5`, still at 18),
  I merged main again (`c2f231a5`) before the full suite. Both merges conflicted only in `docs/audits/README.md`; every row was
  kept. Back-by's changes to `MainActivity`, `AvailabilityCompactScaffold` and `TrackRecordingViewModel` merged without
  conflict and touched different lines from mine: constructor arguments next to each other, and the dropdown's top offset. No
  logic was changed on both sides.
- The chain is now main 18, then guidance-text's 18 to 19, then this branch's 19 to 20. I checked every remote branch before the
  build: none is above 19.

### Compile

The first compile had 2 errors, both mine: `TrackExportPanel.kt` still called the private `formatTrackTimestamp` I had removed.
It was used for the share button's label and an imported track's subtitle. Both now use the shared formatter with the phone's
setting, and `trackSubtitle` takes `is24HourClock`. Its two test files pass `false`, and no assertion changed (`6a37da4d`). After
that, main and unit tests compiled both times.

### Room schemas

- **`20.json`**: I moved my hand-written 19.json and 20.json aside and ran `:app:kspDebugKotlin --rerun`. Room exported a
  `20.json` that is **byte-for-byte identical** to the hand-written one (`cmp`).
- **`19.json`**: Room exports only the current version, so this run could not export 19.json to compare. I put the hand-written
  file back from the saved copy. Two things still check it: the hash script reproduces its hash, and `SchemaMigrationTest`
  validates the 18 to 19 and 19 to 20 steps against it. Both pass.

### Tests

- **Changed and new classes**: 44 classes, 670 tests, 22 skipped. The first run had 19 failures:
  - **12 in my new tests.** The sampled-touch loops in `AvailabilityScreenLayoutTest` (three configurations) and
    `SearchDropdownSearchesTheFieldsTest` reopened the dropdown after a button in it had closed it, and the reopen did nothing.
    Measured: the search field was still focused before the second touch, and after that touch no dropdown existed. Each sample
    is now its own test on a fresh screen, and each fields test makes one touch (`f775822d`). This is reported as a finding,
    below, and is not worked around in the app.
  - **1 that I missed earlier.** `JournalEntriesOnMapTest` built one expected value with `date.toString()`; it now uses
    `displayDate` (`f775822d`).
  - **7 in existing tests, not touched**: see below.
- **Full suite**, after both merges: **4,384 tests, 7 failed, 24 skipped** (554 classes, `BUILD FAILED`). The 7 are those below
  and nothing else.

### Existing tests that break, not touched (for the planner)

- **`JournalPendingDeleteTest`, five tests** (the ones with two finds), and **`LeavingTheJournalFixesTest`, the two short-landscape
  tests (F1, F3)**. They cannot find the find tile ("Morel" / "Chanterelle").
- **Cause, measured with a temporary print in the test, removed after** (window 720×1280 px): the "+" row, then the Sep 21
  heading, the Oyster tile, and the Sep 20 heading at 1240 to 1280 px, the window's bottom edge. The Morel tile is below it. The
  grid is lazy, so a tile out of view is not composed and has no node. Grouping by day adds a heading row for each day and
  pushes the second day's tile off screen. Before, the two finds shared one row. Short landscape runs out of room sooner, which
  is why only those variants of `LeavingTheJournalFixesTest` fail.
- **Possible fixes**, for the planner to choose:
  - (a) give the grid a test tag and have these tests scroll it to the tile (`performScrollToNode`) before touching or counting.
    A count of 0 must then come from scrolling to the end of the grid, not from a tile that was never composed;
  - (b) run those tests on a taller window.
  
  The behaviour itself is what -656 asked for.

### Revert checks

18 checks. For each one: edit, run the named classes, read the build log for compile errors (there were none in any check),
restore from a copy saved before the edit (byte-identical, `cmp`), and confirm the working tree was clean afterwards
(`git status` empty, so every forward change is present). Every check failed for the reason its own edit should cause:

| Check | Edit | Failed with |
|---|---|---|
| r01 | swap the bottom row | "Set on map (395.0.dp) is left of Search (16.0.dp)" |
| r03 | scroll to the end on open | short landscape: "'Latitude' … is not displayed". In portrait the panel fits, so `SearchDropdownKeyboardTest` T3b did not fail: it only shows "no scroll" where there is something to scroll, which is the short-landscape case |
| r04 | Month above the coordinates | "Month (77.0.dp) is below Search coordinates (273.0.dp)" |
| r05 | Search wired to the coordinate search | the five Search touches, the fields test's bottom-Search test and the tap-through positive control: "ran the current-location path expected:<1> but was:<0>" |
| r06 | Search coordinates wired to the current-location path | the five Search-coordinates touches and both fields tests: "expected:<[(45.5231, -122.6765)]> but was:<[]>" |
| r07a, r07b | the old banner strings | the three `AvailabilityViewModelLocateMeTest` banner tests, on the exact old text |
| r08 | tile text "Find on <date>" | `FindsGroupedByDayTest`: six tests, e.g. "'Found 2:14 PM'" not found |
| r09 | days unsorted | "the newer day (… top=318.3dp …) is above the older (… top=72.0dp …)", plus the headless grouping test |
| r10 | the Records row forced to 12-hour | `TrackSheetDataTest`: "'Sep 18, 2026, 21:13' && hasAnyAncestorThat(track-row-flat)" not found |
| r11 | `journalEntryDateLabel` back to ISO | three `JournalEntriesOnMapTest` tests: "expected:<[Sep 12, 2026]> but was:<[2026-09-12]>" |
| r12 | no found-at write on create | `FindFoundAtTimeTest`: "expected:<1791382440000> but was:<null>" (two tests) |
| r13 | found-at dropped in `toEntity` | the same two tests, the same message |
| r14 | "Unnamed find" back | three `FindsGroupedByDayTest` tests, e.g. "Text = '[Unnamed find]'" on the report title |
| r15 | the old auto-name pattern | four `AutoWaypointNameTest` tests and three `TrackRecordingViewModelTest` tests: "expected:<Start · Sep 5, [2026, ]9:41 AM> but was:<Start · Sep 5, []9:41 AM>" |
| guidance (a) | the "No weather guidance for this selection" branch back | five tests: three `ForagingWeatherGuidanceTest` unit tests (no group, a category, lichens), the default-selections test, and `TripWindowsGuidanceTextTest`'s "no guidance block at all" |
| guidance (b) | the species caveat back | four tests: two `TripWindowsGuidanceTextTest` screen tests ("No species-specific data is available for" found) and two unit tests |

Planned checks (2) and (8)–(11) in the first sections were rewritten to fit Amendments 2 and 4: (2) is now (5), and (8) is now
the tile-text check above.

The runner listed each multi-class run's results twice, because its file pattern matched every fresh result file once per class
argument. The failures above are counted once each, by name. Every failure message is specific to its own edit, and no check
reported a failure that its edit could not cause.

### Findings

1. **After a button in the search dropdown closes it, the search field keeps its focus (Robolectric), so the next tap on the bar
   does not reopen the dropdown.**
   - Measured: focused before the second touch, and the touch opened nothing.
   - Closing with Back is different: `AvailabilityScreenBubbleAndDropdownBackTest` shows a touch reopening it after that.
   - This is not new. "Search this location" and "Use current location" closed the dropdown through the same path, and no test
     had ever reopened it after a button close.
   - Whether a phone does the same is a device item. If it does, the bar needs a second tap, or a tap elsewhere first.
2. **The 7 existing-test breaks above.** Not touched; waiting for the planner.
3. **`20.json` matches Room's own export exactly.** `19.json` could not be exported for comparison (see Room schemas above).

### Still device-only

- the bar's reopen after a button close (finding 1);
- the keyboard over the dropdown's bottom row;
- the dropdown's look at the 80% fill;
- the grouped Finds grid's look, and a day's heading going at once while its last tile fades.

## After the build: the planner's answers (RECORD -717)

### 1. The 7 existing tests: option (a)

The Finds grid has a test tag, `FINDS_GRID_TAG` (`ui/log/FindsGalleryScreen.kt`). Each change below cites -717 and the
measurement: the second day's heading sat at 1240 to 1280 px of a 1280 px window, and its tile was not composed. Apart from the
scroll, every assertion is unchanged.

- **`JournalPendingDeleteTest`.**
  - `openFindReport` and `findTile` scroll the grid to the tile (`performScrollToNode`) before touching or reading it.
  - `findTiles(d).assertCountEquals(1)` became `assertTileShown(d)`: scroll to the tile, then the same count of 1.
  - `findTiles(d).assertCountEquals(0)` became `assertTileGone(d)`. It checks the grid exists, scrolls through the whole grid
    without finding the tile, and then makes the same count of 0. So "gone" cannot pass on a tile that was never composed.
  - Three gone checks and six shown checks changed.
- **`LeavingTheJournalFixesTest`.** `openFindReport` scrolls the grid to the tile before its click, which affects F1 and F3 in
  short landscape. I measured the window on `JournalPendingDeleteTest`, not on this class's short-landscape window; that the
  same cause applies here is an inference.
- **Revert check (r17), does the gone check bite?** I left a pending-deleted find visible (`MushroomLogUiState.hidingPendingDelete`
  keeps it). Four tests failed, among them "no tile for 2026-09-20 anywhere in the grid", for the tile that sits below the fold.

### 2. Finding 1 was wrong about its cause; corrected here, no app change

The build's finding said a dropdown **button** leaves the search field focused. The planner asked for a fix: any button should
clear focus, as Back does. Before writing one I measured both closes. The cause is the window's mode, not the button:

- **In touch mode** (a finger on a phone; `setInTouchMode(true)`, as `AvailabilityScreenBubbleAndDropdownBackTest` does): after
  Search coordinates, Search, or Set on map then Cancel, a tap on the bar opens the dropdown again, **on the existing code**.
  These are the new `SearchDropdownReopenTest`'s three tests, each with a positive control that the window is in touch mode.
- **Out of touch mode** (Robolectric's default, and on a phone a hardware keyboard or D-pad), measured with a temporary test
  class that I then deleted:
  - after a button closed the dropdown, the field was still focused and the tap opened nothing;
  - after **Back**, exactly the same.
  
  Every close runs the same `clearFocus`, and out of touch mode Android hands focus straight back to the first focusable view.
  The app's `appClearFocusInProgress` (dispatch -312, item 12) deliberately keeps that hand-back from reopening the dropdown.
- So any button already clears focus the same as Back, and in touch mode the bar reopens after every button. I made no app
  change, and my earlier tests' comments now give the right cause.
- **Revert check (r16):** I removed `clearFocus` from the close. All three `SearchDropdownReopenTest` tests failed with "a tap
  on the bar after the button closed it opened the dropdown again". The new test is tied to the close's focus handling.
- **Left for the owner, if wanted:** with a hardware keyboard or D-pad, after any close (Back included) a tap on the bar does
  not reopen the dropdown. Changing that would weigh against -312 item 12's ruling, so it is not mine to make.

### 3. Known gap

The scroll-to-end-on-open revert (r03) fails only the short-landscape test. In portrait the panel fits, so there is nothing to
scroll and `SearchDropdownKeyboardTest` T3b cannot fail on it. No change, as directed.

### Counts

- The affected classes after the changes: 11 classes, 258 tests, 0 failures.
- **Full suite: 4,387 tests, 0 failures, 24 skipped** (555 classes, `BUILD SUCCESSFUL`), on `5124945f`. That commit contains
  `origin/main` at `0f5cf0e5` (back-by).
- `./gradlew --stop` ran afterwards, and no Gradle or Kotlin daemon is left. Free disk stayed above 3.1 GB.
