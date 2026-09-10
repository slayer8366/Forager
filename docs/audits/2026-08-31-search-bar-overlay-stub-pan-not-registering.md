# Composing SearchEntryBar as a new sibling inside the shared Map-tab tree breaks stub-pan-button clicks under Robolectric

**Status:** open. Scope: the test harness disagreement only, for seven of the eight tests below —
confirmed working on a real device for the ones checked (see "What's confirmed on a real device").
The eighth, "Log a find," is a separate case — see that section below. Not blocking; the eight
affected tests are `@Ignore`d with this document as their record, added under the owner's own
explicit direction after the harness/no-emulator constraint below was disclosed.

**Closing this out is two changes, not one.** Un-`@Ignore`ing these tests once they pass again is
only half of it — `.github/workflows/ci.yml`'s "Summarize the test results" step also carries a
named `SKIPPED_TESTS_ALLOWLIST` for exactly these `(classname, name)` pairs. That allowlist checks
by exact set membership in both directions: an entry with no matching actual skip fails the build
just as an unlisted skip does. So the moment any of these eight tests stops being skipped, its
entry must be removed from `SKIPPED_TESTS_ALLOWLIST` in the same change — CI will fail and say so
if it isn't.

## The claim under investigation

The search-bar opacity/gap dispatch (2026-08-31) moved `SearchEntryBar` so it composes as a real
overlay inside the same `Box` that hosts the map's `AndroidView` content — first as a sibling of
`CompactMapTab`'s own call (in `compactMainScaffold`'s outer `Box`), then, after the opacity issue
below, as a `searchBarSlot` parameter composed *inside* `CompactMapTab`'s own `Box`, directly after
`mapSlot(...)`. Both versions produce the identical set of eight new Robolectric-only failures;
moving the bar one Box level deeper changed nothing about which tests fail, only (per real-device
testing, see below) whether the opacity itself renders correctly.

Eight tests fail as a direct result — none of them assertions about the search bar itself. All
eight share a stub map slot with a button that calls the real `MapSlot.onCameraIdle` callback
(`{ location -> cameraCenter = location }` in `CompactMapTab`), simulating a real pan. Clicking that
button, via `performClick()`, no longer causes `cameraCenter` to update.

## What was confirmed directly (not inferred)

Live instrumentation, added temporarily and fully reverted before this doc was written (never
committed):

- A debug `Text` reading `cameraCenter`'s live value, placed inside `CompactMapTab`'s own `Box`,
  showed it never changing from `uiState.region`'s value even after the stub's pan button was
  clicked, `mainClock.advanceTimeBy(5000)`, and `waitForIdle()`.
- A `remember(displayRegion) { ... }` reset counter, exposed the same way, stayed flat across the
  same window — ruling out "the remember key reset and orphaned the write" as the mechanism.
- **The most direct evidence:** a counter incremented inside the stub button's own `onClick` —
  `debugStubButtonClickCount++`, a plain top-level Kotlin `var`, not Compose state, not subject to
  any snapshot/recomposition timing at all — stayed at `0` after `performClick()` on that exact
  button. `performClick()` itself did not throw, and a companion probe confirmed exactly one
  "Simulate pan to test location" node existed in the semantics tree (no ambiguous match). The
  button's own click handler — a plain JVM method call — did not run, despite the test framework
  reporting a successful click on a uniquely-matched node.
- Isolation experiment: removing the unrelated `topInset` parameter (also added by the same
  dispatch, for compass-strip/bubble/filter-chip positioning) did not change the failure — ruling
  that out as the cause. The trigger is specifically that `SearchEntryBar` now composes as a new
  sibling somewhere in this shared tree (`compactMainScaffold`'s outer `Box`, or `CompactMapTab`'s
  own `Box` — both reproduce it identically), not that specific level of nesting.

This is the same *shape* of failure as
`docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md`: a new/changed sibling composed
into a shared parent silently breaks an unrelated interaction elsewhere in that same tree, with no
exception thrown. The exact Compose-internals mechanism was not found there either, after
considerably more bisection than was repeated here — this document does not claim to have found it
this time either.

## What's confirmed on a real device

The owner tested the version of this change that shipped as CI build 332 (branch `HEAD` at the
time: `16d80a8`, the outer-`Box` version, before the `searchBarSlot` restructuring) on real
hardware:

- Tapping a sighting dot to open the observation bubble, panning, and dismissing all worked
  correctly. This covers the five `AvailabilityScreenMapIconStackTest` entries below.
- "Plan a trip" and "Drop a waypoint" — pan-and-confirm flows structurally identical to the failing
  tests — both saved at the correct panned-to location.

The `searchBarSlot` restructuring (this document's final form, fixing the opacity issue separately)
was **not** independently re-verified on a real device before this document was written — no
working Android emulator is available in the environment that made this change (checked: no
hardware virtualization, and the installed emulator refuses cross-architecture software emulation
outright), so real-device testing depends entirely on the owner's own device. The `searchBarSlot`
version produces the identical Robolectric failure set as the version that was verified, which is
evidence for, but not proof of, the same real-device outcome.

## Log a find is not the same case

`AvailabilityScreenTripPlanningFlowTest`'s "choosing Log a find calls onStartLogEntry with the
picked location instead of planning a trip" fails in Robolectric with the same
`cameraCenter`-never-updates symptom as its two siblings ("Plan a trip", "Drop a waypoint"). But
the owner separately found, on a real device, that this specific flow has its own confirmed bug:
the entry is not recorded, the app lands on what looks like an already-finished entry rather than a
fresh draft, nothing saves — not even a draft — and switching back to the Map tab shows the live
GPS location instead of the panned-to one.

This is very likely a pre-existing bug, unrelated to this dispatch:

- `onLogFindHere` (`compactMainScaffold`'s own lambda: `compactTab = CompactTab.JOURNAL;
  onStartLogEntry(location, LocalDate.now())`) is the only one of the three chooser flows that
  switches tabs as part of confirming — "Plan a trip" and "Drop a waypoint" both stay on the Map
  tab. `onStartLogEntry` (`MushroomLogViewModel.onStartNewEntry`) creates the entry asynchronously
  (`viewModelScope.launch { editingEntryMutex.withLock { ... } }`), while `compactTab` switches
  synchronously in the same lambda, immediately before it — a plausible race between the tab switch
  and the entry actually becoming available for `JournalTab` to open.
- `MushroomLogViewModel.kt`'s own history has no commits from this session's work at all (last
  touched by `641eb30`, unrelated to search-bar/map work). Nothing in this dispatch's diff touches
  `onLogFindHere`, `onStartLogEntry`, `MushroomLogViewModel`, or `JournalTab`.

**This `@Ignore` covers only the Robolectric-side symptom (wrong `LatLng`) for CI-greening
purposes, at the owner's own explicit direction.** It does not resolve, and should not be read as
resolving, the real bug described above. That bug needs its own separate investigation — out of
scope for the dispatch that produced this document.

## Affected tests

`AvailabilityScreenTripPlanningFlowTest`:
- `choosing Log a find calls onStartLogEntry with the picked location instead of planning a trip`
  — see "Log a find is not the same case" above; do not treat this `@Ignore` as clearing that flow.
- `choosing Plan a trip, panning to a location, and confirming saves a planned trip at that
  location` — confirmed working on a real device (see above).

`AvailabilityScreenWaypointFlowTest`:
- `choosing Drop a waypoint, panning to a location, and confirming a name drops a waypoint at that
  location` — confirmed working on a real device (see above).

`AvailabilityScreenMapIconStackTest` (all five confirmed working on a real device, see above):
- `dismissing the bubble via its close icon does not let a later pan bring it back`
- `after a 90 degree map rotation the observation bubble sits below the tapped dot`
- `tapping elsewhere on the map dismisses the observation bubble`
- `a pan while the bubble is still showing keeps it glued to its marker`
- `at bearing zero the observation bubble sits above the tapped dot`

## What's still open

The exact Compose-internals mechanism, same as the prior investigation this one mirrors. Also
open: independent real-device confirmation of the final `searchBarSlot` structure specifically
(only the earlier outer-`Box` structure was directly verified), and the separate "Log a find" bug
described above, which this document deliberately does not attempt to diagnose further — it is a
different problem from the one this document is about.
