# Part 2 follow-ups F1: the map and the Journal, completion report (dispatch `2026-09-28-181`)

Coder session, worktree `/home/zynergy-labs/Zynergy/forager-wt/followups-map`, branch `followups-map` (work pushed to `followups-map-wip` while unproven, to `journal-redesign` when proven). Written by the coder; the planner writes the record. This file grows in sections; nothing already in it is rewritten.

**Model.** The session is configured for `claude-sonnet-5-5` (the system prompt names it). I cannot read the serving model from inside the session, so I do not claim it.

## Governing text, quoted verbatim

`prompts/preserved/2026-09-29-32.md` (the dispatch) at `origin/journal-redesign`, read in full. Its rules for me: "Every item below is either ruled or a plain bug. Where an item says "investigate", **report, and do not build, if the fix needs a design choice.**"; "Tests first, pushed failing."; "Revert checks from saved copies, refused on compile errors."; "The full suite from a cleared results directory, at 0 failures."; "**Machine:** the no-Gradle and 2.5 GB memory checks, plus `df -m /` of at least 2048 MB. Never run `--stop`."

`prompts/preserved/2026-09-29-36.md` (continuation `2026-09-28-191`, replacing item 5), quoted whole:

> **Your stop was right,** and the premise was the planner's error (record -190). The owner, verbatim: "Option A", as put in the plan's "Track delete, built like waypoints".
>
> **Item 5, replaced:**
> 1. **Swipe to delete a track** in the Records list (the Tracks and All sub-tabs). Use the same TwoStageSwipeRow, swipeToDeleteTag and Undo pattern as waypoints (RecordsLogbookList.kt:163; TrackRecordingUiState.kt:121; TrackRecordingViewModel.kt:160, at addf7d7a).
> 2. **Delete on a track's details:** on the compact details sheet and on the tablet's details pane. It uses the same pending-delete and Undo, and waypoints' label.
> 3. **Never a recording track.** A track whose `endedAtEpochMillis` is null offers no swipe and no Delete. Test both surfaces.
> 4. **The delete itself** goes through the existing `DeleteTrackUseCase` (AppContainer.kt:329), which detaches the track's waypoints first. It runs only when the Undo window closes, as waypoints do.
> 5. **Journal entries that kept the track** follow the rule for an entry that kept a since-deleted waypoint.
>    - Find that rule in code, with file:line: what happens to a `cartography_entry_*_refs` row and any kept snapshot when its waypoint is deleted.
>    - Apply the same rule to track refs. Test an entry that kept a track, before and after the delete.
>    - **Stop** if waypoints have no such rule, or if the two ref tables differ in a way that makes "the same rule" ambiguous.
> 6. **Tests first, as for your other items.** Include real touches on the swipe and on the sheet's Delete.
>
> Quote this file in your report. Everything else in F1 stands.

The owner's rulings the dispatch names, in `docs/plans/journal-redesign.md`: "Part 2 Session 1's questions and J6a's questions" (the owner, verbatim: "I'll take your recommendations", which answers "'Download Maps' asks first" and "A track's details sheet gets a Delete"); "Tracks by zoom, revised" (verbatim: "2 A, 3 I'll take your recommendations"); "'Download Maps' asks first: the approved copy" (verbatim: "Approve the Download Maps wording as is").

The planner's messages to me, verbatim: at launch, "Base: the commit the planner names at launch: `6992bef5`"; and after my item-5 stop, "Planner [4b12e2]: your item-5 stop was right, and the premise was my error (record -190). The owner chose "Option A". Item 5 is REPLACED by prompts/preserved/2026-09-29-36.md on origin/journal-redesign; quote it in your report."

## Base and premises, checked before building

- **Base.** The launch names `6992bef5`; `git merge-base --is-ancestor 6992bef5 origin/journal-redesign` is true. The remote head when I started was `addf7d7a` ("Dispatch-note 2026-09-28-189: F1 launched"), which contains F2's work (`29d65a26`) and the planner's records; `-189` says "the coder pulls with --no-rebase". The worktree was cut from `origin/journal-redesign` at `addf7d7a`, and I merged the two later planner commits (`4d47409a`, `d4cee933`) with `git merge --no-edit` (no rebase).
- **The first launch of this session named the F2 dispatch (`-182`), which was already closed** (`RECORD.md` `-188`, "Closes: 2026-09-28-182"). I stopped and reported that; this dispatch is F1.
- **Premise wrong, item 5 (the dispatch's "the list's swipe delete" for a track).** Read at `addf7d7a`: `RecordsLogbookList.kt:153-159` renders a track row with no `TwoStageSwipeRow`; `swipeToDeleteTag` is used only for `WAYPOINTS` and `OFFLINE_MAPS` (`RecordsLogbookList.kt:163,181`, `AvailabilityTripsWaypointsUi.kt:217`, `AvailabilityOfflineMapsUi.kt:517`); the only pending-delete state is for waypoints (`TrackRecordingUiState.kt:121`, `TrackRecordingViewModel.kt:160`); `DeleteTrackUseCase` (`AppContainer.kt:329`) has no production caller (its only user is `DeleteTrackUseCaseTest`). So there was no guard to check and the dispatch's own stop rule fired. I stopped that item, told the planner, and the planner replaced it (`-36`, quoted above).
- **Machine, at the start.** `df -m /` 2401 MB, 4538 MB memory available, no Gradle process. It fell to 1948 MB, then 1861, 1784, 1610, 1430, 1368, 1304 MB while I worked (not by anything I wrote: my worktree is ~0.3 MB of source, no build). Below 2048 MB I ran no Gradle. I asked the owner what to do; the owner answered "Wait for space". **Nothing below has been compiled or run** until this line is superseded by a "Resumed" section. That includes every prediction in the next section.

## Pre-registration: tests and predictions (written after the code and before any run; a deviation, see below)

**Deviation.** The rule is to push predictions before building. I wrote the tests first and pushed them (`89782c9f`, `9212a2b9`, on `followups-map-wip`) and the production code after (`1275dd07`), but this section is written after all of it, because no run was possible; nothing has been observed, so no prediction below was informed by a result. The failing state to run is `9212a2b9` (tests plus signature stubs, and for item 8 the behaviour-preserving extraction), checked out detached, before the branch head is run.

**Failing at `9212a2b9` (each for the reason it names):**

| Test class | Predicted failing | Reason |
|---|---|---|
| `TrackWidthByZoomTest` (`app/src/test/.../ui/map/`) | the stops test, the widths test, the hold-and-linear test, the interpolation-expression test, the outline test | `TRACK_WIDTH_ZOOM_STOPS` is still `(11, 0.4), (15, 1)` (`TrackWidthByZoom.kt` at base); the casing-ratio test passes (a ratio, unchanged) |
| `OfflineDownloadConfirmationTest` (`.../ui/availability/`) | all seven | a tap on "Download Maps" calls `onDownloadOfflineMaps` at once (`AvailabilityOfflineMapsUi.kt:241-246` at base); no dialog exists, so "Download this area?" is not found |
| `LandscapeOfflinePickerTest` (`checkPinnedActions`, two tests) | both | the touch on Download Maps now must open the confirmation; at base it downloads |
| `PhotoExporterDateTakenTest` (`.../ui/log/`) | "keeps the record's time after the scan on publish replaces it", "the time is written after the row is published" | `PhotoExporter.kt:69` writes DATE_TAKEN at insert only; nothing writes it after `IS_PENDING` goes to 0. **Passing at base, controls:** "still written at insert", "a record with no time gets none" |
| `ReturnPromptRecreationTest` | "an activity recreation with a pending edit shows no prompt", "two recreations in a row" | on API 28+ `onSaveInstanceState` runs after `onStop`, so the flag ON_STOP sets is saved and the new Activity's ON_RESUME shows the prompt. **Inferred, not read** (I did not read the platform's ordering). If these pass at base, the mechanism is not reproduced in Robolectric and item 8 stops there. **Passing at base:** the three backgrounding tests |
| `MapViewportResizeTest` (`.../ui/map/`) | the two rotation tests | `onViewportResized` is a stub returning `this` at `9212a2b9`. **Passing at base:** "the first measurement is not a resize", "a recomposition with the same size reports nothing" |
| `AvailabilityScreenLayoutTest` (the new fullscreen "i" tests, two subclasses) | "in portrait fullscreen the attribution button clears the navigation bar inset" | `MapRenderMode.attributionBottomInset` is `null` (stub). **Expected passing:** the other three new tests (they pin unchanged behaviour). One risk: the existing test "bottomInset is positive and identical whether or not the map is fullscreen" (`AvailabilityScreenLayoutTest.kt:476`) asserts the caption's inset does not change in fullscreen, yet `AvailabilityCompactScaffold.kt` targets 0 there; I have not reconciled the two and will report what the run shows |
| `TrackDeleteTest` (`.../ui/log/`) | all except the recording-track "no Delete" pane test and "a details pane handed no delete" | the view-model methods are no-op stubs and `RecordsTab` ignores `onDeleteTrack` at `9212a2b9` |
| `TrackDeleteEntryRefsTest` (`.../app/domain/`) | none | **a characterisation, expected to pass at base and after:** the ref rows are never touched by either delete, so it pins the rule, and cannot bite the change. Flagged as CLAUDE.md asks |
| `WideChipRowClusterGuard*Test` (three sizes) | none expected | the dispatch says fix the placement only if it fails; a pass at base is a guard that "passes identically before and after a code change" and is reported as possibly not covering what it claims |

**Pass conditions after the build:** every test above green; the full suite at 0 failures from a cleared results directory, counts from the JUnit XML with every file newer than the run's start; each new test shown to bite by a revert of the behaviour it holds, from a copy saved before editing, refused if the build log has a compile error, with the forward change confirmed present afterwards.

**Not testable here (device-only), predicted before any device exists:** what MapLibre draws and where a finger lands for the "i" (item 2) and the re-projected bubble (item 1); the media scan's actual effect on `datetaken` (item 7); the camera preview in landscape (item 9); real system-bar insets everywhere. The list is in the device-only section, written at the end.

## What is written, and what is not proven (state at the first hand-back; nothing here has been compiled or run)

Pushed on `followups-map-wip`: tests and stubs (`89782c9f`, `9212a2b9`), production for items 1-5, 7 and 8 (`1275dd07`), this report. **Every line of Kotlin below has been read back but never compiled** (no Gradle run: `df -m /` was under 2048 MB from `1948` onward and kept falling, the owner said "Wait for space"). A background loop (`/tmp/f1-wait.sh`) waits for 2048 MB free, 2500 MB available memory and no Gradle process; the run resumes when it exits. Treat the code as a draft.

| Item | Written | Proven |
|---|---|---|
| 1 bubble on rotation | `MapViewport.kt` (`onViewportResized`), `SightingsMap.kt` (`reanchorFocusedBubble` extracted from the idle listener, called again on a resize after `mapView.post`) | nothing yet; and see the limit below |
| 2 the "i" in portrait fullscreen | `MapRenderMode.attributionBottomInset`, `AvailabilityCompactScaffold.kt` (queries `WindowInsets.navigationBars`), `SightingsMap.kt` uses it for the button's margin | nothing yet; device-only after |
| 3 track width stops | `TrackWidthByZoom.kt` (18/1.0, 16/0.67, 14/0.42, 12/0.25), doc comment cites "2 A"; `SightingsMap.kt` comment | nothing yet |
| 4 the download confirmation | `AvailabilityOfflineMapsUi.kt`: `AlertDialog`, the owner's title, body and buttons exactly; `offlineDownloadConfirmationBody` | nothing yet |
| 5 track delete (Option A) | view model, state, snackbar notice, swipe rows (Tracks and All), the details' Delete (sheet and pane), `MainActivity` wiring | nothing yet |
| 6 tablet chip guard | test only, no placement change | nothing yet (a guard; expected to pass at base) |
| 7 Gallery date taken | `PhotoExporter.kt`: DATE_TAKEN written again after the publish | nothing yet; the cause is **not confirmed** (below) |
| 8 "Welcome back" | `ReturnPromptState.kt` (extracted from `CartographyScreen.kt`) and an `isChangingConfigurations` guard on ON_STOP | nothing yet |
| 9 landscape camera | investigation only (below) | read, not run |
| 10 stacked glyphs | investigation only (below) | read, not run |

## Item 5: the rule for an entry that kept a since-deleted record, found in code

Read at `addf7d7a`. **A delete removes the record and nothing else.** `RoomWaypointRepository.delete` is `dao.deleteById(id)` (`RoomWaypointRepository.kt:34-35`, `WaypointDao.kt:44`); `RoomTrackRepository.delete` is `dao.deleteTrackAndPoints(id)` (`RoomTrackRepository.kt:83`, `TrackDao.kt:71-74`, points then the track row). Neither touches `cartography_entry_*_refs`. Those tables have no `@ForeignKey`, "by explicit standing rule", because "nothing here may change as a side effect of something happening to the referenced track, and any FK action would do exactly that" (`CartographyEntryEntity.kt:73-75` for tracks; the waypoint entity says "see [CartographyEntryTrackRefEntity]'s doc comment for the shape and reasoning this mirrors", `:92`). So the ref row survives with its snapshot and the entry shows the snapshot: the track ref keeps `name`, `distanceMeters`, `durationMillis`, `pointCount`, `kept` and "a row whose track has since been deleted has nothing to recompute from and keeps its figure" (`CartographyEntryEntity.kt:69-71`; the recompute is `CartographyViewModel.kt:200`), and the entry report lists kept tracks from those decisions (`CartographyEntryReportScreen.kt:642`) and draws the map from the live tracks only, so a deleted one draws no line (`GetCartographyEntryMapDataUseCaseTest`, "a kept track deleted from Records draws nothing and does not error"). The waypoint ref keeps `name`, `lat`, `lng` and the entry map draws its snapshot (`CartographyEntryReportScreen.kt:520`, `entryMapWaypoints`).

**The tables differ in one way, and I judged it does not make "the same rule" ambiguous:** a waypoint snapshot carries coordinates, so the entry can still draw it; a track snapshot carries no path, so the entry cannot. The rule (the ref row and its snapshot survive untouched; the entry shows the snapshot) is the same for both, and applying it to tracks needs no code, because `DeleteTrackUseCase` already leaves the refs alone. I did not stop. **This is a judgement the planner may overrule**: if "the same rule" was meant to include drawing a deleted track, that needs the path in the snapshot, which is a schema change and not built. `TrackDeleteEntryRefsTest` pins the rule with real Room, before and after, for both records.

## Item 7: why the row's date taken is NULL: not confirmed

The exporter writes `DATE_TAKEN` at insert only when the record has a time (`PhotoExporter.kt:69`), and the Session 1 photo had one (its name, `forager-photo-20260927-211632.jpg`, is built from the same `createdAtEpochMillis`, `PhotoExporter.kt:114-118`; a photo with no time is named from the clock, which would have read 2026-09-29, not 09-27). So the insert wrote a value and the device read NULL afterwards. The only thing between them in this code is the publish (`IS_PENDING` to 0). The planner's guess, that the media scan on publish re-derives `datetaken` from the file's own metadata, and a scrubbed photo has none, fits, and is **not confirmed**: the scanner's source is not readable from this machine (a fetch of the AOSP `ModernMediaScanner.java` returned HTTP 503), and Robolectric has no scan. The fix I wrote is the one a scan cannot defeat if the scan runs inside the publish call: write the time again after it. If the scan is asynchronous, that write can still lose the race, and only a device shows it. `FakeMediaProvider.scanOnPublishClearsDateTaken` models the guess so a test can bite; it is a model of the observation, not the platform.

## Item 8: is the prompt meant after a recreation?

I found **no report or ruling that says it should show after a recreation**. The prompt's trigger is ON_STOP with a dirty committed entry, then ON_RESUME (`CartographyScreen.kt` at base, now `ReturnPromptState.kt`). The only text about recreation is the code comment above the flags: they are `rememberSaveable` so that "a config change during backgrounding, or process death short of losing the process outright" still shows the prompt (`CartographyScreen.kt:318-321`). That is the case the fix keeps: a configuration change that lands while the app is backgrounded has no `isChangingConfigurations` ON_STOP. What it removes is the ON_STOP the platform runs when it rebuilds a foreground Activity (a night-mode toggle), which on API 28+ is followed by the state save, so the flag rode into the new Activity. That mechanism is **inferred**; the test that reproduces it is the first thing to run.

## Item 9: the camera preview leaves the screen in landscape (investigated; not built)

Evidence read: `AndroidManifest.xml:157-161` (MainActivity: `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`, no `screenOrientation`); `WindowOrientation.kt:114-116` (on open: save `requestedOrientation`, set `SCREEN_ORIENTATION_SENSOR` or `PORTRAIT`; on dispose: write the saved value back, which is the manifest's UNSPECIFIED because nothing else sets it, `:92-93`); `InAppCameraDialog.kt:184` (the only caller); `InAppCameraDialog.kt:262` (`viewfinder(Modifier.fillMaxSize())`) and `CameraXCaptureSession.kt:533,552,270-275` (a default `PreviewView`, a `Preview.Builder().build()` with no target rotation or aspect ratio set). Two independent device observations: `RECORD.md:1365` items (4) and (7), "the camera window is SCREEN_ORIENTATION_SENSOR and came up at ROTATION_90 on both opens ... user_rotation 3 and 0 left it at ROTATION_90" and "the system then set it to 1 when the camera window rotated"; and this run's Session 3 read-back, "`user_rotation` ... to 1 by the camera at the find's photo" (`stage-device-check-part-2-run-record.md:492`), with Session 2's "the camera surface turns the screen to landscape and leaves it there until `user_rotation` is set" (`:361`).

**What the code and the two observations support (inferred, not read from the platform):** with auto-rotate off (`accelerometer_rotation` 0) the platform writes `user_rotation` to the rotation the SENSOR-oriented camera window takes; when the camera closes the app restores UNSPECIFIED, which with auto-rotate off follows `user_rotation`, now landscape. So the "surface leaving the screen" is the window staying rotated, not a sizing defect: the surface is `fillMaxSize`, it follows the window, and the code sets no fixed size or ratio. It is then a property of a phone with auto-rotate off held (or lying) in landscape when the camera opens.

**Why I did not build a fix.** Every fix I can see is not confined to the camera screen's own handling: (a) restoring `user_rotation` needs `WRITE_SETTINGS`, which the app does not hold; (b) restoring a chosen `requestedOrientation` on dispose (for example `PORTRAIT`, or `USER`) changes what the app does on leaving the camera for every user, a design choice; (c) not using SENSOR when auto-rotate is off reverses the owner's 2026-09-19 ruling that the camera window follows the device (`WindowOrientation.kt:16-29`). And none of them can be checked here (device-only). Options for the owner: (b) with a named value, (c), or leave it as a property of forcing `user_rotation` in a test rig (a real user with auto-rotate on would not meet it, per the reading above, which is also unverified).

## Item 10: stacked glyphs (investigated; report only)

**The dispatch's description and the run record differ.** The dispatch says "a photo under a find cannot be opened". The run record says the unreachable case was "my photo glyph shares one spot with the owner's, and a tap reaches only the top glyph" (`stage-device-check-part-2-run-record.md:346`): two glyphs of the same layer, photo on photo. That matters, because the code ranks two different layers by a fixed order.

How a tap is resolved (read): `SightingsMap.kt:405-419` queries `queryRenderedFeatures` once per tappable layer, first at the point and, only if nothing is there, in a 48 dp box (`TAP_BOX_DP`), then calls `resolveTap`; `TapPrecedence.kt:47-53` returns one hit; `tapWinner` (`TapPrecedence.kt:25-35`) takes the lowest tap-group precedence and then "the layer drawn on top in [drawOrder]; within one layer, the first hit the query returned" (`minWithOrNull` keeps the first of equal elements). Markers are one group; the marker layers are `userReorderable = false` (`MapLayers.kt:226`), so their order is the registry's: sightings, planned trips, waypoints, finds, photos (`MapLayers.kt:388-409`), photos on top. So: photo over find, the photo wins and **the find is the one that cannot be reached**; photo over photo, whichever the query returned first wins and the other cannot be reached. Nothing lets a user reach the lower glyph: the photo bubble prints "In <find>" as text only (`MapBubbles.kt:301-316`) and has no open-find action (`MapBubble.kt:382` puts `onOpenFind` on the find bubble only). `TapPrecedenceTest.kt` has the marker-versus-marker layer test and no test of two hits from one layer.

Options for the owner, none chosen: a chooser listing every marker under the finger (needs `resolveTap`/`mapTapOutcome` to return a list and a new outcome); spreading a stack at high zoom; making the photo bubble's "In <find>" line open the find (helps the photo-over-find case only); cycling on repeat taps; swapping the layer order (moves the problem).

## Item 6: the tablet chip guard

`WideChipRowClusterGuardTest.kt` asserts, at `w824dp-h1318dp`, `w1280dp-h900dp` and `w1318dp-h824dp` in native graphics, with a taxon chip (from a real tap on View on Map) and "1 journal entry on map" both up, that neither chip's unclipped bounds intersect the cluster's, at rest and with the cluster dragged to its top limit by a real long-press drag on its handle. No placement change was made or is needed unless it fails on the first run. **Not run.**

## Flags outside scope, so far

- **Disk.** Free space fell 2401 to 1241 MB during this session with nothing of mine writing it (my worktree has no build). Other sessions' builds are the candidates; I did not look inside them or delete anything. It blocks every coder that keeps the 2048 MB floor.
- **Item 2's caption/button split.** The owner reversed a first version that put the caption above the navigation bar in fullscreen (`AvailabilityCompactScaffold.kt`, the comment above `animatedAttributionBottomInset`); I kept the caption at the true edge and moved only the MapLibre button. The logo (MapLibre's own, bottom start) is not moved and probably sits in the same band; not looked at.
- **The tablet's fullscreen** uses a different scaffold (`AvailabilityWideLayoutUi.kt`); the "i" fix is in the compact one only.
- **An existing test contradicts the scaffold** (`AvailabilityScreenLayoutTest.kt:476` says the caption's inset does not change across fullscreen; the scaffold targets 0 in fullscreen). Unreconciled; the first run shows which is stale.
- **Item 1's test limit.** `MapView` cannot be constructed under Robolectric (`OfflineStyleSwapTest.kt:187`), so a real configuration change can test the trigger (`onViewportResized`) and not the re-projection. The dispatch asked for a real configuration change at 0 to 90 and 90 to 0; that is what `MapViewportResizeTest` does to the trigger. Whether the bubble then lands on its glyph is device-only.

## Resumed: results (disk recovered; supersedes "nothing has been run" above)

Disk recovered to 13.4 GB free (`RECORD` -194: the drain was Claude Desktop's GPU error loop filling syslog and the journal; the owner had it stopped). Before each Gradle run I checked no Java Gradle process, memory, and `df -m /` ≥ 2048 MB. Never ran `--stop`. Branch head at the full run: `ccbbe8f2`.

**Tests first, run at the failing state.** `9212a2b9` (tests plus stubs) plus a two-file test fix (`b78bffc4`: `TrackRecordingServiceTest` needed the new constructor argument, `AvailabilityScreenLayoutTest` needed an Activity-bearing rule; the first build at `9212a2b9` alone did not compile, 8 `e:` lines, so it was not a run). Second build: 0 compile errors, results newer than the run start. Of 83 tests in the ten classes, the failures and reasons matched the pre-registration, with these differences:
- `TrackWidthByZoomTest`: the outline test also **passed** at base (it only asserts the outline is 1.5 dp at two zooms; I moved those zooms with the stops), so it does not bite; 4 of 6 failed, as the stops/widths/expression assertions name.
- `OfflineDownloadConfirmationTest` 7/7 and `LandscapeOfflinePickerTest` 2/2 failed for "Download this area?" not found. `PhotoExporterDateTakenTest` 2/4 failed, the two controls passed, exactly as predicted. `MapViewportResizeTest` 2/4 failed (0 resizes), the two controls passed. `AvailabilityScreenLayoutOnSmallDensePhoneTest`: 1 failed ("expected 48.0 but was -1.0", 96 px at 2×), the other three new tests and the old `bottomInset ... identical` test **passed** (I had flagged that old test as a risk; it does not contradict the scaffold under this harness, so that flag is closed as not reproduced, not explained). `WideChipRowClusterGuard*` 6/6 **passed at base** (a guard, reported as such). `TrackDeleteEntryRefsTest` 4/4 passed at base (characterisation, as flagged).
- `TrackDeleteTest`: 13 of 17 failed at the stubs (swipe rows absent, no Delete, no log); the 4 that passed are the "no Delete for a recording track" and "no delete handed" cases, which a stub satisfies.
- `ReturnPromptRecreationTest`: the prediction held for the two recreation tests (prompt shown after a recreation, so the mechanism reproduces under Robolectric). Two of the five failed for **test faults**, not the reason: the "no pending edit" case set the flag after the Activity was launched, and the "backgrounded then rebuilt" case hit "No compose hierarchies" at base. I fixed the first (relaunch with the flag off, `ccbbe8f2`); the second passes at head and I did not diagnose why it failed at base, so that one test has not been shown to fail for a stated reason.

**Head:** the ten classes plus `PhotoViewerSave*` (14 classes, 83+ tests): 0 failures.

**Revert checks** (each: copy saved before editing, edit, run the affected classes, read the build log for `e:` lines, read the JUnit XML with every file newer than the run's start, restore from the copy and compare sha256, confirm the working tree is clean afterwards). All nine built with 0 compile errors; all restored byte-equal; `git status` clean at the end.

| Revert | Failing, with the message specific to the edit |
|---|---|
| item 3, first stop back to `(11, 0.4)` | 4 of 6: "breadcrumb stop 0's zoom expected:<12.0> but was:<11.0>"; "breadcrumb at zoom 5 expected:<1.5> but was:<2.4>" |
| item 4, button calls the download at once | 9 of 15 (7 confirmation tests and the two landscape tests): "Download this area?" not found |
| item 7, second write carries no time | 2 of 15: "a write of the time follows the publish expected:<true> but was:<false>"; `getAsLong(...) must not be null` |
| item 8, guard removed | 2 of 5: "no Welcome back after a recreation the user did not leave the app for" |
| item 2, `attributionBottomInset = null` | 1 of 22: "expected:<48.0> but was:<-1.0>" |
| item 1, resize never reported | 2 of 4: "one resize, to the landscape viewport expected:<1> but was:<0>", and back |
| item 5, view-model recording guard off | 1 of 17: "expected null, but was:<PendingDelete(item=Track(id=t-rec ..." |
| item 5, `canBeDeleted` always true | 3 of 17: recording row has a swipe; the sheet and the pane show a Delete for a recording track |
| item 5, `commitRemoveTrack` never deletes | 3 of 17: `expected:<[detach:t-other, delete:t-other]> but was:<[]>` |

Not covered by a revert: item 1's `SightingsMap` glue (the re-anchoring itself, untestable here), item 2's `SightingsMap` margin use, the LandscapeOfflinePicker edit (covered by item 4's), `ReturnPromptState`'s extraction (behaviour-preserving; the existing `AvailabilityScreenBackNavigationTest` return-prompt tests passed in the full run).

**Full suite**, from a cleared results directory, at `ccbbe8f2`: `BUILD SUCCESSFUL`, 0 `e:` lines; JUnit XML, 365 files, **all newer than the run's start (0 stale): 3008 tests, 0 failures, 0 errors, 24 skipped.** (F2's planner suite was 2949/0/0/24, so +59.)

**Merge.** `origin/journal-redesign` had six further planner commits (docs, prompts, plan; no code). Merged with `git merge --no-edit`, no rebase.

## Decisions I made

- Item 5: read "the same rule" as "the ref row and its snapshot survive untouched", for tracks as for waypoints. The planner said build it as read; the owner has since ruled "a kept track keeps its path (Option B)" (`RECORD` -190s, F3), which is a separate continuation.
- Item 2: caption stays at the true edge; only MapLibre's "i" clears the bar; only in the compact tree.
- Item 5: a failed committed delete restores the track and shows "Couldn't delete track." above the Tracks list (`tracksErrorMessage`), which the dispatch did not spell out; it follows "failed results are reported as such".
- Item 5: the snackbar carries the entry count ("used in N journal entries") like the waypoint's, read from the existing `forTrack` count.
- Item 7: the second write's failure is logged at WARN and does not fail the save.
- Edited existing tests only where the intended behaviour breaks them: `LandscapeOfflinePickerTest`, `PhotoViewerSaveTest` (`single` → the update that carries `IS_PENDING`), four constructor sites, `TrackRecordingServiceTest`.
- Item 8: extracted the return prompt's state into `ReturnPromptState.kt` (behaviour-preserving) so a real recreation could reach it.

## Device-only list (S22 and tablet)

1. Bubble: open a photo/find bubble in portrait, rotate 0→90 and 90→0 with no pan; it sits on its glyph and clears the cluster (S22 and tablet).
2. The "i" in portrait fullscreen (S22, 3-button and gesture navigation): it sits above the navigation bar and a real tap opens the attribution dialog; out of fullscreen unchanged. Real insets; Robolectric showed only the value handed to the map.
3. Track widths at zoom 12, 14, 16, 18: look right, casing and halo follow; breadcrumb dots and gaps at low zoom.
4. Download Maps: dialog wording, radius in the units setting, Cancel/Download, in portrait and landscape 90/270.
5. Track delete: swipe in Tracks and All, Undo, timeout deletes, waypoints detached; Delete on the sheet (S22) and the pane (tablet); a track being recorded offers neither; an entry that kept the deleted track still lists it.
6. Tablet chip row against the cluster at the three sizes with both chips (the test is a guard that passed at base).
7. Gallery date taken: save a photo with a time, read `datetaken` and the Gallery's own date; if still NULL, the scan runs after the second write and the cause is different.
8. "Welcome back": toggle night mode with a dirty editor (no prompt); background the app with a dirty editor and return (prompt).
9. Camera in landscape: repeat Session 2's steps; the investigation's mechanism (the platform rewriting `user_rotation`) is unverified.
10. Stacked glyphs: covered by the owner's later ruling (`02b1a491`, fan out on tap); nothing built here.

## Flags outside scope

- The disk drain and its cause are recorded in `RECORD` -192/-194; nothing for me to add.
- `AvailabilityScreenLayoutTest`'s old caption test passing at base while the scaffold targets 0 in fullscreen is unexplained (closed as not reproduced, above).
- The tablet's fullscreen "i" (wide scaffold) and MapLibre's logo in the same band are untouched.
- `ReturnPromptRecreationTest` "backgrounded then rebuilt" was not shown to fail at base for a stated reason.
