# Elder's re-check: #391, pre-main 073fc77bd to 79a01ae1b

**Auditor:** Elder, Forager's UX auditor, a fresh session on Claude Fable 5.1 (`claude-fable-5-1`, read from this session's own environment). The coder, Hazel, ran on Opus; Sorrel's two reviews of #391 were on Fable 5.1. **Planner:** Rowan. **Brief:** `prompts/preserved/2026-10-24-08-promotion-recheck-391.md` (records-after-173 at ad73d1bba). **Written:** 2026-10-24, started 13:32 UTC, CI read at 13:50 UTC, finished about 13:55 UTC (06:55 PDT), from `date -u`.

Read-only: no code changed, nothing merged, nothing deployed, no phone touched, no Gradle. What I ran: `git` reads of origin/pre-main, origin/records-after-173 and main (381515c61, 19aa1058); `gh api` for the runs, jobs, PR #391 and the merge run's test artifact; `date -u`. Every file:line below is at `origin/pre-main` 79a01ae1b unless it says otherwise. "Read" means I read it; "relayed" means a record, a review or a report says it and I did not see it; "inferred" is marked.

Yardstick: `docs/process/qa-philosophy.md` section 3 (read from origin/pre-main; the file is not on records-after-173), verdicts in section 4; CLAUDE.md's UX defaults ("Don't make me think"); my re-check at 073fc77bd (`prompts/preserved/2026-10-24-02-promotion-recheck-388-report.md`), which this range extends.

**Range:** `git diff 073fc77bd 79a01ae1b`: one PR, #391 (merge 79a01ae1b, head f0360352e, merged 13:30:50 UTC), five commits in two pairs of test-then-fix. Five files, +98/−1: `TrackRecordingViewModel.kt` (one line changed, 852 lines before and after, counted), `AvailabilityViewModel.kt` (+10, 751 to 761, counted), `AvailabilityViewModelFactory.kt` (+2), and two test files (+33, +52). Nothing under `.github/`, `docs/`, `server/`, `README.md` or `res/` changes (diff: empty), so no CI skip list grew, no public text changed, no string was added. Room schema untouched; the upgrade check does not apply (-1133). The known-bugs list is unchanged in the range (19 rows at both ends).

---

## Verdict: pass with items

The range fixes a bug that is live in 3500 (the dotted recording trail stayed on the map after Stop) and closes the gap it uncovered (the saved track's line waited for a tab change). Both changes are the one line each that the dispatches asked for; both are reached by every Stop path the app has; both were shown to fail without the fix on builds that compiled; and both were seen working on the S22 by Hazel, on the PR's own build. CI is green at the exact merge commit, first attempt (below). After Stop the user now sees the trail turn into the saved line within about a second, with nothing to read and nothing to do, which is what "Don't make me think" asks for. Two items follow, neither of which risks records, safety or trust by the section 4 definitions: the known-bugs list wants one row for the sibling gap the owner chose not to fix now (-1605), and a failed end write leaves the user with no line and no notice, a pre-existing silence whose shape this range changes.

---

## CI

**Green at the exact head, first attempt.** Push run 40098461329 on 79a01ae1b (the merge commit, `head_sha` confirmed), `completed`, `success`, `run_attempt` 1, all five jobs success: "Build, test, publish APK" (13:30:55 to 13:49:50Z), "Android lint (debug)", "Release compiles and packages (unsigned)", "Tile Worker serves what its tests say", "Route-data cutter routes the same as the full square". Read from the API at 13:50 UTC. When I started (13:33 UTC) the run was `in_progress`, three minutes after the merge; I waited for it rather than cite the PR run (40091393614 on f0360352e), as the brief asks.

**The suite, counted by me from the run's own artifact.** `unit-test-report` (artifact 12561947851, 14.1 MB), downloaded into an empty directory under `~/.cache/elder-391-scratch/`: 726 result files; `tests` 5450, `failures` 0, `errors` 0, `skipped` 24, summed over every `<testsuite>` element. The XML timestamps run 13:36:27Z to 13:49:33Z, inside the job's window, so they are this run's. The three new test names are present: "a track end reported through TrackEnds puts the finished track in the map records with no tab change" in `AvailabilityViewModelMapLayersTest`'s XML, and both "the recording line is cleared by …" names in `TrackRecordingViewModelTest`'s. 5450 is 073fc77bd's 5447 (relayed from Hazel's first PR comment, not re-parsed) plus the three tests this range adds; the 24 skipped is the same figure as the PR run's (relayed from Sorrel, section 4), so no skip grew.

No test skipped, deleted or weakened (the only edit to an existing test is a two-line comment), no growth in CI's skip list (`.github/` untouched).

---

## What I verified about #391

1. **The clear is one line and it is the only place a Stop empties state.** `clearRecordingState()` (`TrackRecordingViewModel.kt:546-564`) cancels the poller, the route ticks and the location fixes, then one `_uiState.update` at `:562` resets `activeTrack`, the two flags, `returnToStart`, `originWaypoint`, `routeHome`, `routeLine`, `sundownLine`, `backBy` and now `breadcrumbPoints = emptyList()`. The diff of the production code is that one field added to that one `copy` (read). Both Stops reach it: the screen's through `MainActivity.kt:627` → `stopRecording()` → `clearRecordingState()` at `:643`; the notification's through the service's `TrackEnds.report` (`TrackRecordingService.kt:389`) → the collector at `:366` → `onTrackEnded` (`:411-414`) → `resyncRecordingState` → `clearRecordingState()` when the row reads ended (`:708`). The halt path reports the same way (`TrackRecordingService.kt:333`). Read, each link.

2. **The two recording tests see the data that could fail them.** Both new tests (`TrackRecordingViewModelTest.kt:350-391`) start a recording, append two points, advance one poll interval and assert the list is `[45.0, 45.001]` *before* Stop, which is the step the old test at `:306-320` never took (it now says so in a comment, `:316-317`). The screen's test calls `vm.stopRecording()`, the method `MainActivity.kt:627` calls; the notification's test never calls it: it ends the row and then calls `trackEnds.report`, the service's store-then-report order (`TrackRecordingService.kt:385-389`), and asserts `isRecording` is false before asserting the list is empty, so the empty list is shown to come from the resync. Both go through `runRecordingTest` (the stalled-run pitfall). Read. That they fail without the fix (run 40083975160, two failures, `expected:<[]> but was:<[TrackPoint(lat=45.0 …`, zero compile-error lines) and that the revert branch differs by that one line are relayed from Sorrel's first review, sections 2 and 3; I re-read the test code, not the runs.

3. **The Maps tab now hears the same report, and only reloads.** `AvailabilityViewModel` takes `trackEnds: StateFlow<String?>` defaulted to `MutableStateFlow(null)` (`:163-169`), the shape `TrackRecordingViewModel.kt:214` already has, and its `init` launches `trackEnds.collect { if (it != null) mapDisplay.loadMapRecords() }` (`:367`). The factory passes `container.trackEnds.latest` (`AvailabilityViewModelFactory.kt:63`). `loadMapRecords` (`AvailabilityMapDisplay.kt:103-108`) reads `getMapRecords()`, logs each kind that failed, and writes `mapRecords` and nothing else: no camera request, no sheet, no drawer, no layer choice, no cluster position. So it cannot reset anything the user set (CLAUDE.md UX defaults). `git grep trackEnds` over `app/src/main` finds exactly the two collectors, the two factories, the container and the two service report sites (read). The report comes only after `endTrackUseCase` succeeded (`TrackRecordingService.kt:385-389`, `:331-333`; `TrackEnds.kt` doc), and `GetMapRecordsUseCase.kt:102-103` keeps only ended tracks with points, so the reload can never draw the row still open.

4. **The map test reaches the behaviour by the real path and could fail.** `AvailabilityViewModelMapLayersTest`'s new test (`:189-204`) builds a real `TrackEnds`, passes `trackEnds.latest` to the real constructor, shows the tab while the store has no ended track and asserts the snapshot is empty, then stores the end and reports, and asserts the polyline is present with no further `onMapShown`. The records lambda reads a `var`, so the only way the track can appear is a second read triggered by the report. Read. The fail-first run (40090024619, one failure, `expected:<[RecordPolyline(recordId=track-1, …)]> but was:<[]>`, zero `e:` lines, 5450 tests) and the revert run (40091409765, the collector line alone removed, `BUILD SUCCESSFUL`, the same one failure) are relayed from Sorrel's second review, section 4, and Hazel's addendum; the two agree on run ids, counts and message.

5. **Does anything else on the Maps tab change when a track ends?** Yes, in one way, and it is freshening, not a change of state. The reload re-reads all four kinds (finds, photos, every ended track with its points, offline regions: `GetMapRecordsUseCase.kt:86-110`), so a record of any kind saved since the tab was last shown appears at a Stop. Everything that draws from the snapshot is on this tab: the map's markers, lines and circles (`AvailabilityScreen.kt:441` → `AvailabilityScreenMapInputs.kt:42` → `CompactMapMapSlot.kt:94`), the Layers sheet's record counts (`AvailabilityScreenMapInputs.kt:42`, `records = mapRecordsDrawn`) and the journal highlights (`AvailabilityScreen.kt:450-451`). No other screen reads `mapRecords` (grep). The map's refresh effect is keyed on `keptTrackPolylines` (`MapDataRefresh.kt:58-61`) and already re-runs on every breadcrumb update, roughly every 15 s while recording (`:72-74`); its camera code below moves only for a new search frame id, a camera request, or a changed region target (`:81-99`, `:100-128`), none of which a records reload touches. So the camera does not move at a Stop. Read, not run; the S22 frames in Hazel's addendum show the walker's position held across the Stop on both paths, which is consistent. In practice the only record that can have been saved without leaving the Maps tab is the track itself, since finds, photos and regions are saved from other tabs and returning to Maps already reloads; inferred.

6. **The owner's rulings.** -1450: the decision to reload stays in a ViewModel function (`AvailabilityViewModel.kt:367`; verified). -1490: `AvailabilityViewModel.kt` is at 761 lines, under the amendment's 800 and under the modular-code line, but 39 from it (Sorrel L4; counted). `TrackRecordingViewModel.kt` is still 852, as the owner accepted in -1592 (counted; the fix added no line). No `@Ignore`, `@Disabled`, `assume*` or weakened assertion anywhere in the range (grep of the diff: none; the one comment added to the old test documents a weakness, it does not add one).

7. **Nothing a user reads changes.** No string, resource, composable, layout or Manifest line in the range (file list). The 80% rule, Back, insets and A7 do not apply because no surface changed. The Stop paths show no text at all, before or after (`MainActivity.kt:626-628` calls `stopRecording()` and nothing else; no "saved" string exists in `strings.xml`, grep), see item 2.

---

## What the user sees after Stop, as a step path

Against "Don't make me think": count what the user has to notice, decide or do.

**Before this range (3500 and 073fc77bd, from Hazel's 2026-10-24-04 report, seen on the S22):** Record → walk → the dotted trail grows behind the walker, lagging by up to one 15 s poll → Stop → the Stop button goes idle, and the dotted trail stays exactly as it was, cut short of where the walker stopped → 30 s, backgrounding, even deleting the track: unchanged → only a tab change brings the saved track's solid line, drawn over the stale dotted one → only the next Record, or the process ending, removes the dotted one. The user is left looking at a trail that stops short of them and never finishes, with no sign the walk was saved. That is a state that reads as broken, which the owner names as the worst case.

**Now (79a01ae1b; the S22 evidence is Hazel's, on the PR builds 1.0.3904 and 1.0.3906, which have no tree difference from the PR heads):**

- *Stop in the app:* Record → walk → the dotted trail grows → tap Stop → in the same frame (about 0.3 s, the screenshot interval) the Stop button is idle and the dotted trail is gone → about one second later (first frame at +1.2 s) the saved track's solid line is on the map, from the start to the stop point, with no tab change → the End waypoint lands asynchronously (`:647-649`; not in the frames, not this range's subject). Between the two frames the map shows neither line, for under about a second; the frames cannot resolve it finer.
- *Stop from the notification:* expand the shade → "Stop recording" → the shade closes → in the first frame after (about +1.4 to +1.7 s) the button is idle, the dotted trail is gone and the solid line is there. On this path both come from the same report, so there is no neither-line moment to see (read from the code: the resync and the reload both collect `TrackEnds.latest`; seen on the S22 as one frame).

**Count:** nothing to read, nothing to decide, nothing to do. The picture changes from "where I have been so far" to "the walk I saved" in about a second, and the change itself tells the user the save happened. That is the right amount of thinking (none) and the right signal (the thing they were watching turns into the thing they expect to keep). A one-second blank on the in-app path is below the threshold where a user would wonder; I would not add a spinner or copy there, which would be more to notice, not less. **The S22 figures and frames are relayed; not seen on a phone by me.**

---

## Known bugs

The list at 79a01ae1b has 19 rows, unchanged in this range. Rows 1 to 19 as in my two earlier reports.

**One row is wanted** (item 1 below): the deleted track that stays on the map until a tab change. The brief asks whether the list needs it. It does, by the list's own rule ("listed and judged before release, never found first by users"), and the list already holds rows that are inferred, not seen (rows 9 and 10 are marked as such), so the marking the brief asks for fits the form.

**Where is it?** Live, by my reading: at 19aa1058 (the 3500 build) Records already offered a track delete with an Undo window (`TrackRecordingUiState.kt:160`, `:188` at 19aa1058), the map already drew saved tracks from the same snapshot, and `withoutPending` already took only find, photo and region ids (`GetMapRecordsUseCase.kt:33` at 19aa1058); `drawnMapRecords` is the same shape there and here (`DrawnMapRecords.kt:17-29`). The three delete handlers that remove a committed delete from the snapshot cover finds, photos and regions only (`AvailabilityMapDisplay.kt:71-99`). So a track deleted from Records is never taken out of the snapshot, and the draw filter never hides it; it goes at the next `onMapShown`.

**When would a user meet it?** The delete happens on the Journal tab, and moving to Maps is itself a tab change that reloads. So the line is seen only if the user reaches the Maps tab while the Undo window is still open (about 10 s: `PendingDeleteSnackbar.kt:151`, `:176`, `SnackbarDuration.Long`): the reload at the tab change still reads the row, the pending filter does not apply to tracks, and when the delete commits nothing removes it. It then stays until the next tab change. In both layouts the Maps tab is a tab (`AvailabilityScreen.kt:686`, `compactTab == CompactTab.MAP`, bottom bar or rail), so there is no layout where the Journal and the map are shown together and the reload never comes. Inferred from the code.

**Seen on a phone?** Not as such. Hazel's 2026-10-24-04 report, step 3, saw one deleted track's solid line stay on the map "for 30 s or more" after reaching Maps about 10 s after the swipe, going at the next tab change, and set it aside because the delete might not have committed yet. With a 10 s Undo window that explanation covers at most the first seconds of a 30 s stay, so the observation is consistent with this gap; but it was one instance, not reproduced on purpose, and the brief asks that it be marked "not seen on a phone as such".

**Draft row 20** (for the planner to place; wording is theirs to change):

> | 20 | A track deleted from Records can stay drawn on the map until the next tab change, if the user reaches the Maps tab within the Undo window (about 10 s) of swiping it away. Records shows it gone; only the map's line is stale. | Open (not included in #391 by the owner's choice: "deletes are not included now", RECORD -1605) | Live, inferred from the code: the same snapshot, draw filter and delete handlers at 19aa1058 (`DrawnMapRecords.kt`, `AvailabilityMapDisplay.kt:71-99`) | No | Hazel's part 1 report for dispatch 2026-10-24-07 (inferred, not reproduced); one consistent sighting in the 2026-10-24-04 S22 check, step 3 (about 30 s, not reproduced on purpose); Elder's re-check of #391, 2026-10-24, item 1. Not seen on a phone as such |

**New bugs of the fail kinds in this range:** none found.

---

## Items

### 1. Add a known-bugs row for the deleted track that stays on the map

- **Where:** `docs/process/known-bugs.md`; the gap is in `DrawnMapRecords.kt:17-29` and `AvailabilityMapDisplay.kt:71-99` (no track handler).
- **What the user meets:** a track they just deleted still drawn on the map until they change tab, in a narrow path (above). It makes them think ("did the delete fail?") until they look at Records; nothing is lost.
- **Why it matters for this promotion:** section 3 reads the known-bugs list, and this one is recorded as open in RECORD -1605 but not on the list. A listed, judged, not-serious bug may ship; an unlisted one is the thing the list exists to prevent.
- **Recommendation:** add the row before or with the promotion PR (a `[skip ci]` doc commit, as rows 18 and 19 were). Draft wording above. Fix later, with a track id in `withoutPending` and a committed-track filter, as Hazel's part 1 report sketches; not for this promotion.

### 2. A failed end write now leaves the user with no line and no notice

- **Where:** `TrackRecordingService.kt:388-392` (the end write's `onFailure` logs and reports nothing); the screen's Stop has already cleared the trail (`:643`) and the map's reload never fires.
- **What the user meets:** after Stop, the dotted trail goes at once and the solid line never comes; Records does not list the track either (the same report drives `loadTracks`). Both come right after the next app start, when the abandoned-track sweep ends the row at its last stored point (`EndAbandonedTracksUseCase.kt:20-29`, `AbandonedTrackSweepOnce.kt:54`). The points are stored; nothing is lost.
- **Why it is listed:** the silence is pre-existing (Records has had it since #364), but before this range the stale dotted trail sat in that spot, and now the spot is empty, which is the more honest picture of the two but still gives no next step (CLAUDE.md, Errors: "Partial or failed results are reported as such"). I have not seen the write fail and have no data that it does; Hazel's three S22 sessions saw none.
- **Recommendation:** none for this promotion. Not a bug to list without a sighting; a note for the recording work: a Stop whose end write fails could set the screen's one-shot notice, the shape `startRecordingErrorMessage` already has.

---

## Not items, recorded so nobody looks for them

- **Two reloads can finish out of order** (Sorrel's L1): `loadMapRecords` is read-then-write with no ordering, and the collector adds a third caller beside `onMapShown` and the restore reload. The window is a record saved between two reads milliseconds apart; deletes are protected by the draw-time filter. Agreed; nothing for this promotion.
- **The reload's cost** (Sorrel's L2): every Stop now re-reads every ended track's full point list, the same as one tab change. Not measured on a large record set; see "Not seen on a phone".
- **A replayed id at Activity recreation** runs one extra reload concurrent with the tab-shown one; both read stored state (`TrackEnds.kt` doc, lines 22-25). Harmless, as the recording screen has lived with since #364.
- **A user with the Tracks layer off** sees the trail go at Stop and nothing replace it, because they chose not to draw saved tracks. Their setting, not a bug; the Layers sheet's "Tracks" toggle is where the line is.
- **The amendment named a test class that does not exist** (Sorrel's L3; Hazel's disclosure). The test went into the class that already tests what `onMapShown` loads. The right place.
- **Suite count:** 5447 at 073fc77bd (relayed), 5449 after the two recording tests, 5450 after the map test. The merge run's own count is in the CI section.

---

## Not seen on a phone

- **The merged build itself.** Every S22 figure above is Hazel's, on the PR builds 1.0.3904+gb4206e22 (dotted line clears, both paths) and 1.0.3906+gf86ae828 (solid line in the first frame, both paths). Each is GitHub's merge of the PR head into pre-main 073fc77bd with no tree difference from the head, so the merged production code is what was run; but no build of 79a01ae1b has been on a phone.
- **The deleted-track gap** (item 1), as such.
- **The reload's cost with many tracks**, and whether a Stop ever stutters the map on a phone with a large record set (Sorrel's L2).
- **Rotation during or at a Stop**, and whether the saved line survives it. By the code the snapshot is ViewModel state and survives rotation; inferred.
- **The under-a-second blank** between the trail going and the line coming on the in-app path: the S22 frames bound it (gone at +0.3 s, present at +1.2 s); its exact length and whether it is ever visible as a flicker were not measured.
- Everything in my two earlier reports' "Not seen on a phone" still stands.

---

## What I could not check

- No phone, no emulator, no Gradle: every "passes" is CI's conclusion read from the API, plus the artifact count below.
- The fail-first and revert runs' logs: relayed from Sorrel's two reviews and Hazel's addendum, which agree with each other on run ids, counts, timestamps and messages. I re-read the test code and the production diff, not those runs.
- Whether the end write ever fails in use (item 2). No data either way.
- Whether a Room read started earlier can finish later than one started after it (the premise of Sorrel's L1). Reasoned from the code's lack of ordering.

---

## Summary for the owner

Promote. The recording trail now disappears the moment you stop, and the walk you just recorded is drawn in its place about a second later, with no tab change, on both ways of stopping; Hazel saw both on the test phone, and the automated checks for each were shown to fail without the fix. CI is green on the merged code at the first attempt. Nothing else on the map moves or resets when a recording ends; the map only becomes as fresh as a tab change would have made it. Two things to carry: the deleted-track line that stays until a tab change, which you chose not to fix in #391, is not yet on the known-bugs list and should be, with wording drafted above; and if saving a track's end ever failed, the user would now see nothing where they used to see a stale trail, and nothing tells them (it comes right at the next start). Neither is a reason to hold the promotion.
