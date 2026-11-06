# Journal entry map: Navigate on waypoint bubbles (part 6, RECORD -890)

Branch `journal-entry-height`. PR #219 is held and not merged. Builds ran on CI only (run-this-branch). No phone was touched.

## What changed

- **Wiring** (`ui/log/JournalTab.kt`): the entry map's bubble sources now pass `onNavigateToWaypoint`. This covers the waypoint's bubble and the details sheet opened from it. The callback reuses today's waypoint navigation unchanged. Navigate is tagged with a new origin, `WaypointNavigationOrigin.JournalEntryMap`. Its saver key is `journal-entry:<id>`.
- **Back** (`AvailabilityScreen.kt`, `endWaypointNavigation`): Back with that origin ends the navigation and returns to the Journal's Entries, where the entry is still the open one. The bubble is not reopened, in the same way Back after a Records row reopens nothing. This is my reading of -890's "Back behaves as it does after starting navigation from Records", applied to where the walk was started (the entry).
- **Snapshot-only waypoints** (`ui/map/MapBubble.kt`): a waypoint the entry knows only from its snapshot (gone from Records) gets no Navigate. There is no record to navigate to. The Maps tab never shows snapshot waypoints, so nothing changes there.
- **Finds:** unchanged.

## Evidence

All runs used `workflow_dispatch`. For each run I checked that the headSha matched, read the build log for compile errors (none in any run), and read the JUnit XML.

| Run | Head | Result |
|---|---|---|
| 38180036116 | `ecff1d75` forward | 2 failures, a **test defect of mine**: both new tests called `setScreen()` before a helper that sets the screen again ("has already set content"). Fixed in `3d42e377`. |
| 38180440515 | `3d42e377` | 67 tests across five classes, 0 failures |
| 38180789526 | `63ba2597` revert (a): the Back branch does nothing | 1 failure, the predicted one: the Back test, which could not find "Along the creek." |
| 38181220903 | `814c239d` revert (b): the entry map passes no Navigate | 2 failures, the predicted two: both new tests, which found no `map-bubble-navigate` node |
| 38181642268 | `48ca074b` head, full suite | 612 suites, 4691 tests, 0 failures, 24 skipped (the allowlist check passed) |

Each revert was restored from a copy saved before the edit, in `bdec4d01` (a) and `48ca074b` (b). An empty `git diff 3d42e377 HEAD` confirmed part 6 is present.

**Two reverts, not one.** The coordinator asked for one revert check, and I ran two. With the wiring off, the Back test fails at the Navigate touch, before it reaches Back. So the Back branch needed its own revert to be proven.

## Assertions changed

All in `app/src/test/java/com/zynergylabs/forager/app/`.

- `ui/availability/AvailabilityScreenWaypointNavigateTest.kt`:
  - `a waypoint's bubble on a journal entry's map offers Directions, not Navigate` (dispatch -502, Amendment 1) became `from a waypoint's bubble on a journal entry's map, Navigate targets that waypoint on the Maps tab`. Its "no Navigate" assertion is inverted, as -890 rules.
  - New test: `Back from a navigation started on a journal entry's map ends it and returns to the entry`. Back goes through the real dispatcher.
  - The class doc comment is updated to match.
- `ui/map/WaypointNavigationOriginSaverTest.kt`: the round-trip list now includes `JournalEntryMap`.

## Not verified

- **On a phone:** the path end to end (Journal > entry > pin > Navigate > Maps tab navigating > Back > the entry). The test's cartography state is held by the test. On a device, the entry stays open only if the Journal keeps its open entry across the tab change. Existing behaviour says it does (the "leaving the Journal" fixes), but this was not checked here.
- **Not tested:** the touch on Navigate is a single coordinate touch at the control's centre, as the class's other Navigate tests do. Back from a navigation started from the details sheet opened on the entry map (same origin kind) is also untested.
