# Records tidy: step 0 report (dispatch 2026-10-11-03, RECORD -868 and -870)

Read and report only. No code was written, no Gradle build was run and no phone was touched.

## Base

- The dispatch assumed main at `bde42068`. `origin/main` is now `af5ea9af` (PR #216, RECORD -869). `git diff --stat bde42068 af5ea9af` touches only `.github/workflows/ci.yml` and `README.md`. So every app-code line cited below holds at both commits. The reading was done at `bde42068`.
- This report is on branch `records-tidy`, cut from `af5ea9af`, so the branch has the "run this branch" CI trigger.
- Dispatch read at `origin/records-after-173:prompts/preserved/2026-10-11-03.md` (6938249c). Rulings read at `RECORD.md`: -868 at `ab23d277` lines 15502-15509, and -870 (added by the coordinator mid-task) at lines 15527-15535.

## Premises: confirmed and wrong

| Premise | Finding |
|---|---|
| Tools drawer inclusion rule at `ui/availability/AvailabilityNavigationUi.kt:72-83` | **Confirmed**. Quoted below. |
| Existing Tools entries are "waypoints, trip planner, Settings" | **Wrong**. Waypoints left Tools for Journal > Records in Journal restructure Stage 1 (`AvailabilitySettingsUi.kt:576-577`). The drawer holds Trip Planner, the Sundown section and the Settings row. None of them navigates out of the drawer (details below). |
| "CLAUDE.md and RECORD: Back retraces the way in" | **Half wrong**. CLAUDE.md has no Back rule (`grep -n -i retrace CLAUDE.md` finds nothing). The rule is in RECORD: -383 (owner verbatim, "back out one step at a time, going exactly the way they came. That's how Forager defines back. Otherwise returning to map is a home function"), -618 and -672. |
| Records chip state is "session state, hoisted; not persisted" | **Mostly matches**. It is hoisted and `rememberSaveable`, not DataStore (`JournalScreenState.kt:43-103`, created at `AvailabilityScreen.kt:862`). Saveable means it also survives a system-killed process being restored. A cold start does not keep it. See item 3. |
| -01 changes "the journal entry page and the Records track sheet" | -01's branch so far changes only `domain/EntryReport.kt`, `domain/model/TrackStatistics.kt`, `EntryReportTest.kt` and docs. Its step-0 report plans more files. See the conflict section. |

## Item 1: find page photo

- **Composable**: `ui/log/LogEntryReportScreen.kt:85`. It has one caller, `JournalTab.kt:560` (`FindsPage.Report`). The find-over-the-view path from the map's "Open in Journal" draws the same pages (`JournalTab.kt:869-872`), so the change shows there too.
- **Today's order** (`LogEntryReportScreen.kt:117-193`): header row (back arrow, title, menu) at :118-160. Inside the scroll come "Found at …" at :177-181, "Your own identification" at :183-185, then the photos at :187-193 in a `FlowRow` of `ReportPhotoThumbnail`. Each thumbnail is an 88 dp square (`:335`, `:339-343`), `DecodedPhoto` with `ContentScale.Crop` (`DecodedPhoto.kt:115`). A tap opens `PhotoViewerDialog` (`:216-219`).
- **Decode**: `DecodedPhoto` decodes once, at the first laid-out size, capped at the viewer's maximum edge (`DecodedPhoto.kt:63-69`, `:81-93`). A full-width cell decodes larger than an 88 dp one. That is expected, and memory use is a device observation.
- **Unruled details (stop-and-ask)**:
  - (a) Where it goes. The ruling says "under the title". Does that mean directly under the header, above "Found at", or where the thumbnail is today, after the identification line? The owner's screenshot description puts today's photo after both lines.
  - (b) Shape. Crop to a fixed frame, and which one (square or 4:3), or show each photo at its own aspect? The last option makes a swipeable row change height from photo to photo.
  - (c) "Several photos in a centered row you can swipe". Does that mean one full-width photo per page (a pager, ideally with a page indicator), or a row of large photos with the next one peeking?
- **Tests**: `LogEntryReportScreenTest.kt:226` ("an entry with a photo still renders it"), `:258` (a coordinate touch on the thumbnail opens the viewer and the close control dismisses it), `:168` (photo-only, no empty message). None asserts size or position, so all should pass unchanged. That means none of them proves the new layout; new tests are needed. **Risk**: `DecodedPhotoSemanticsTest.kt:180-187` pins a semantics golden for the report thumbnail ("report" case). If the photo goes inside a pager, its merged semantics may change. Changing that golden would be an assertion change, so I would report it, not edit it.

## Item 2: All grouped by kind

- **Composable**: `ui/log/RecordsLogbookList.kt:84-250`, under the All chip (`RecordsTab.kt:310-336`). Grouping is the pure function `buildRecordsLogbook` (`ui/log/RecordsLogbook.kt:60-82`): days newest first, finds first within a day. The day header is drawn at `RecordsLogbookList.kt:259-275` ("EEE, MMM d, yyyy · N records", `:352`). Day rows animate in and out as blocks (`:122`, `:148`).
- **Order inside a section**. Tracks, waypoints and regions have epoch-millis times (`RecordsLogbook.kt:18-28`). A find has `foundOn` (a date) and an optional `foundAtEpochMillis` (`MushroomLogEntry.kt:41`, `:74-79`, null for older finds and for finds created for another day). So "newest first" for Finds needs a tie rule. Proposal: `foundOn` descending, then `foundAtEpochMillis` descending when present, then input order. Ask the owner to confirm.
- **Unruled**: (a) Does a kind with no records show its header with "· 0", or no section at all? (b) Does the header show a count that matches its chip, which leaves out pending deletes as the chips do (`RecordsTab.kt:290-291`)?
- **Unchanged by the ruling**: swipe-to-delete (`RecordsLogbookList.kt:190-242`), row taps (`:184`, `:214`, `:232`), and find tap selecting Finds (`RecordsTab.kt:322-325`).
- **Tests that change**:
  - `RecordsLogbookTest.kt:40`, `:57`, `:64` (the builder) are rewritten for kind order.
  - `RecordsFilterChipsTest.kt:380` (reading order and the day-header counts at `:405-407`). Its day-header assertions go, which the ruling requires, and are replaced by section-header assertions.
  - No other test reads day tags (grep for `records-logbook-day` / `dayTag` finds only that file).

## Item 3: Finds chip by default

- **State**: `JournalScreenState.recordsFilterState` (`JournalScreenState.kt:58-59`), initial value `DEFAULT_RECORDS_FILTER = RecordsSubTab.ALL` (`:109`). It is also `RecordsTab`'s local default (`RecordsTab.kt:176`). Saved by name (`:85-96`), created with `rememberSaveable` at `AvailabilityScreen.kt:862`. `RecordsTab` has one caller (`JournalTab.kt:797`).
- **The change is one value**, `DEFAULT_RECORDS_FILTER = FINDS`. Last-pick-until-restart already works this way: the state is hoisted above the tab branch. The owner's J0 ruling 4 was "Hoisted, saveable" (`JournalScreenState.kt:15-17`). A cold start gets no saved state and opens on Finds. A system-killed-and-restored process keeps the last pick. To a user that looks like resuming, not restarting (inferred from Android saved-state behaviour; not checked on a device).
- **Back conflict (stop)**: `RecordsTab.kt:259-271`. Back from any chip other than All steps to All, "the fixed default". Then Back from All goes to Entries (`JournalTab.kt:486-488`), then to Maps (`AvailabilityScreen.kt:1319-1324`). If Records opens on Finds, the first Back takes the user to All, a chip they never visited, before leaving. The All-centred page slide has the same assumption (`RecordsTab.kt:302-305`, All at depth 0). Options:
  - (a) Keep All as Back's fixed target.
  - (b) Move the fixed target to Finds, the new default.
  - (c) Back from any chip leaves Records for Entries.
- **Tests that change**:
  - `RecordsFilterChipsTest.kt:206` asserts "All selected" by default (`:219`). Its default assertion flips to Finds, as the ruling requires.
  - `RecordsFilterChipsTest.kt:276` and `AvailabilityScreenBackNavigationTest.kt:609` and `:626` (Back steps to All; Back from "Records' default chip, All" steps to Entries) depend on the Back option.
  - Tests that open Records and work in All without selecting it need a setup step that taps All. Their assertions stay. Found by grep: `RecordDetailsSheetTest.kt` "in All" cases (`:272`, `:287`, `:302`, `:421`, `:499`, `:517`), `RecordsFilterChipsTest.kt:195`, `:382`, `:465`, and likely cases in `JournalPendingDeleteTest`, `TrackDeleteTest`, `JournalPageSlideTest`, `AvailabilityScreenJournalShortWindowTest`, `MapChromeOverMapTest`, `AvailabilityScreenLandscapeB3DestinationsTest` and `RecordsChipRowScrollTest`. The exact per-test count is **unverified** until the build's first red run.

## Item 4: Offline maps in Tools

- **Composable**: `CompactToolsDrawerContent`, `ui/availability/AvailabilitySettingsUi.kt:589-667`. It is a `DrawerHeader` (`:653`), then `SearchControls` (`:654-662`), which holds Trip Planner as an in-place `CollapsibleSection` (`AvailabilitySearchUi.kt:768-770`) with the Sundown section below it (`AvailabilitySettingsUi.kt:658-661`), then the sticky `SettingsEntryRow` (`:663`, `:153-168`). Settings is a drill-in inside the drawer (`showSettings`, `:617-626`, with its own BackHandler). **No current entry leaves the drawer.**
- **Call site**: `AvailabilityScreen.kt:1923-1947`. `isDrawerOpen` is at `:1212`.
- **Precedent for "close Tools, open Journal > Records on a chip"**: the GPX import notice (`AvailabilityScreen.kt:1261-1273`). It closes the drawer, ends fullscreen, and sets `pendingJournalDestination = VIEW_IMPORTED_TRACK` and `compactTab = JOURNAL`. JournalTab consumes that by setting `topTab = RECORDS` and staging the chip in `recordsPendingSubTab` (`JournalTab.kt:424-429`), which `RecordsTab` applies through `selectTab` (`RecordsTab.kt:221-226`). A new `PendingJournalDestination` value (`JournalTab.kt:957`) follows the same shape. That is a new path, not a conditional in working code.
- **Inclusion rule** (`AvailabilityNavigationUi.kt:72-83`), quoted: *"'Tools' is deliberately a catch-all for things used but not wanted in the immediate way — per-trip and rare items that are not destinations in their own right (trip planner, waypoints, and now Settings too …). … That inclusion rule is what the next addition here has to argue against becoming a junk drawer."* **Fit**: downloading maps is per-trip, so that half fits. "Not destinations in their own right" does not fit: Offline maps is a destination (Journal > Records > Offline maps), and this button would be the drawer's first shortcut out to one. The owner ruled for it (-868), so I would record the exception in that doc comment. The comment also still lists "waypoints", which is stale (`AvailabilitySettingsUi.kt:576-577`).
- **Unruled**: where the button sits. Proposal: a sticky row above Settings, the same shape as `SettingsEntryRow`, with the Offline maps chip's icon. It should not go above Trip Planner, because six test classes read the Trip Planner header as "the drawer is open" (`AvailabilitySearchUi.kt:753-758`).
- **Edge case not covered**: a find open in its editor on the Finds chip when the button is tapped. `selectTab` fires `onFindsTabLeft`, which is an incidental exit from the edit (`RecordsTab.kt:214-217`, `JournalTab.kt:446-448`), the same as a chip tap. I would keep that unless told otherwise.

## Back on the proposed path (stop)

Mechanics today, on the GPX-import shape, for Tools > Offline maps > Records on the Offline maps chip:

1. Back goes to All (`RecordsTab.kt:269-271`).
2. Back goes to Entries (`JournalTab.kt:486-488`).
3. Back goes to Maps (`AvailabilityScreen.kt:1319-1324`).

The handler at `:1319` sends every tab other than Maps straight home. So "Back returns to where you were before opening Tools" holds today in no case: it takes three presses from Maps, and from List, Seasonal, or another Journal page it never returns there.

Meeting the ruling needs a new one-shot "came from Tools" Back step that restores the origin (`compactTab`, and when the origin was the Journal, its top tab and chip). Questions for the owner:

- (a) Does Back skip the drawer and land on the origin, as -868 says? Under -383's literal "exactly the way they came", Back would reopen the Tools drawer first.
- (b) When does the one-shot expire? For example, does it still apply after the user taps another chip, opens a region, or scrolls?
- (c) If the origin was the Journal, does Back restore its exact top tab and chip?

## Item 5: the offline-region row (RECORD -870)

- **Where the text is built**: `OfflineRegionRow`, `ui/availability/AvailabilityOfflineMapsUi.kt:602-649`. The downloaded branch builds "R around lat, lng — N tiles, X MB — downloaded T ago" (`:638-644`) and then the paragraph from `offlineRegionZoomNote` (`:645`, text at `:658-662`, which names the mapping product). The same row is used by the All logbook (`RecordsLogbookList.kt:234-240`) and the Offline maps chip list (`AvailabilityOfflineMapsUi.kt:570-577`), so both change.
- **Region page**: **it exists**. `RecordDetailsSheet`'s `OfflineRegionDetails` (`ui/log/RecordDetailsSheet.kt:505-520`) already shows Radius, Centre, Tiles, Size, Downloaded (date and relative time) and the same zoom paragraph (`:519`). It opens from a tap on the row in All and in the chip (`RecordsLogbookList.kt:232`, `AvailabilityOfflineMapsUi.kt:575`). **No detail needs moving.** The row drops it and the page keeps it. No stop.
- **Offline place name**: **none exists**. A grep of `app/src/main` for Geocoder, place or locality names, or reverse geocoding finds nothing. A region's data is name, centre, radius, zooms, tile count, size and time (`domain/OfflineMapRepository.kt:211-226`, `domain/model/Region.kt:4-8`). The name is user-typed or defaults to "Region N" (`AvailabilityViewModel.kt:1744`). So the place part is left out.
- **Unruled (ask)**:
  - (a) Wording of the area without a place. "10 mi around …" leaves "around" with nothing after it. Proposal: "Region 5 · 10 mi radius · 18.8 MB · downloaded 11 hours ago". The radius is `Region.radiusKm`, so "10 mi" is a radius, not a width.
  - (b) Keep the "Stale" label beside the name (`:622-624`)? Proposal: yes.
  - (c) The not-downloaded row (`:626-636`: "Not downloaded", radius around coordinates, "Download again") is not covered by the ruling. Proposal: leave it unchanged, or drop only its coordinates.
- **Tests asserting that text**:
  - `AvailabilityScreenSettingsPanelTest.kt` (testDebug) `:718`, "a downloaded region states it is ready to zoom 15…", asserts the zoom paragraph on the Offline maps chip at `:764-766`. It must be moved to the region page. Coverage there already exists (`RecordDetailsSheetTest.kt:259-267`, `assertRegionContent`, which also pins centre and tiles). The move is required by -870 and drops no claim.
  - `OfflineNotDownloadedRegionTest.kt:74-80` asserts absence in the not-downloaded row and passes before and after.
  - `RecordDetailsSheetTest.kt:302` and `:309` sample touches across the region row. The row gets shorter, and the touch offsets are fractions of the row, so they should hold. Unverified until run.
  - No other test asserts the row's line (grep for "tiles,", "— downloaded" and "Protomaps" in tests finds only map-credit tests that are unrelated).

## Conflict with dispatch 2026-10-11-01 (journal-entry-height)

- -01's branch (`origin/journal-entry-height`, head 9494d7ae) changes `domain/EntryReport.kt`, `domain/model/TrackStatistics.kt`, `EntryReportTest.kt`, its report and `docs/audits/README.md`. **None of those files is in this dispatch's set.**
- -01's step-0 report plans to remove the entry page's waypoint table and its hoisted open-row state. That state goes through `JournalTab.kt:306`, `:785`, `AvailabilityScreen.kt:876`, `:1839` and `AvailabilityCompactScaffold.kt:391`, `:1452`. Its test plan touches `LeavingTheJournalFixesTest.kt:805-821`. It may also touch `RecordDetailsSheet.kt` (track sheet `:424-429`, `:668-678`), depending on its stop 2.
- **Shared files**:
  - `JournalTab.kt` (I would add a `PendingJournalDestination` value and consume it next to `:424-429`) and `AvailabilityScreen.kt` (the Tools button handler next to `:1261-1281`, and the drawer call site at `:1923`). These are different hunks, so the merge should be a text merge.
  - `docs/audits/README.md`, the known serialization point.
  - Possibly `LeavingTheJournalFixesTest.kt` (it reads `recordsFilter`) and `RecordDetailsSheetTest.kt`, if the Finds default needs setup taps there.
- No ordering is needed unless -01 restructures `JournalTab`'s pending-destination block. When both land: merge, never rebase, and keep every index row.

## What only a phone can confirm (draft device check, S22)

1. Find page: a find with one photo, then a find with three. The photo shows centered and large under the title, with margins, and the three swipe sideways. Evidence: a screenshot of each, described in words, not committed.
2. On the same page, tap a photo. It opens full screen, and Back closes it. Back again returns to the Finds list.
3. Cold start (swipe the app away, then reopen), then Journal > Records. Opens on Finds.
4. Pick Waypoints, go to Maps, return to Journal > Records. Still on Waypoints.
5. Records > All. Sections in the order Finds, Tracks, Waypoints, Offline maps, each header with a count matching its chip. Swipe-delete one waypoint, then Undo.
6. From Maps: Tools > Offline maps. Records opens on the Offline maps chip with the drawer closed. Press Back once and note where it lands, using the system Back gesture and also the 3-button bar if available. Repeat from List and from Journal > Entries.
7. An offline region row reads as one line (name · area · size · downloaded …). Tap it: the region page shows centre, tiles and the zoom paragraph.
8. Observation, not a gate: scroll speed and memory on the find page with large decoded photos.

## Stops for the owner

1. Back on the Tools > Offline maps path: questions (a) to (c) under "Back on the proposed path".
2. Back from the Finds default chip: options (a) to (c) under item 3.
3. Find photo: position, shape, and pager versus row (item 1 (a) to (c)).
4. All: empty-section headers, and the find tie rule (item 2).
5. Region row: wording without a place, the Stale label, and the not-downloaded row (item 5 (a) to (c)).
6. Tools button placement (item 4).
