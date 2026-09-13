# Set a find's location at creation: completion report

**Date:** 2026-09-13
**Dispatch:** `Dispatch — set a find's location at creation` (planner: no repo access; dated
2026-09-13; target `main` at `175b050` or later).
**Branch:** `claude/new-session-vto65i`. At §0, `origin/main` was `175b050` and the branch was four
commits ahead of it, all from the same day's full-screen-photo-viewer dispatch and its Album
follow-up; nothing else had landed. Every §1 reference was re-derived against that tree.
**Nothing is pre-authorized to merge.** No schema change: confirmed against
`app/schemas/…/ForagerDatabase/15.json`, where `mushroom_log_entries.lat/lng` and
`log_photos.latitude/longitude` are `REAL` with no `notNull`.

---

## What was built

### Fix 1 — a Journal-started find takes the device fix in hand

`MushroomLogViewModel.onStartNewEntry` (`MushroomLogViewModel.kt:289`) now creates the entry at
`location ?: freshDeviceLocation()` (`:295`). A caller that supplies a point (the map's tapped or
centred point) is unchanged; a caller that supplies `null` (the Journal's "+", `JournalTab.kt`,
`onStartEntry(null, …)`) gets what the device has in hand, or nothing.

**Source.** A new constructor parameter, `currentFix: () -> LocationFix.Update?` (`:197`), which
`MainActivity.kt:100` wires as `{ viewModel.uiState.value.liveFix }`, `AvailabilityViewModel`'s
held live fix. A plain lambda rather than the other ViewModel or its flow, the shape this class
already uses to borrow a Cartography count (`getPhotoEntryReferenceCount`). Never awaited: it is a
read of a value at one moment, so the save cannot block or fail on it. `liveFix` is in `ui/log` in
0 files before this change (control: 4 files under `ui/availability`), so this is wiring, not
capture, as the dispatch said.

**Age of the fix, and what bounds it — a decision, reported as one.** `AvailabilityUiState.liveFix`
holds the last accepted fix indefinitely once fixes stop (its own doc comment says so), and the
collector is cancelled on every background (`AvailabilityViewModel.onLeftForeground`, `:181-183`),
so the held fix can be from a walk hours ago when the app is next opened indoors with no fix yet.
No bound anywhere applied to it; the only age bound in the tree is the HUD's `LOST_AFTER_MILLIS`
(`NavigationReadout.kt:67`, 5 min), as the dispatch found. `freshDeviceLocation` (`:326-329`)
applies that same constant by reference: a fix the HUD already calls lost and withholds distance
on is not one to stamp a find with. Not tighter, because the HUD's 30 s "stale" band is a display
cue and a find logged half a minute after the last accepted fix is at the same spot. Discarding a
held fix for age is logged at INFO with its age; no fix at all is the ordinary case and is silent.
Accuracy is not bounded here because it already is upstream: the collector refuses fixes worse
than `LIVE_FIX_MAX_ACCURACY_METERS = 50f` (`LiveFixGate.kt:68`) before holding one.

`createOriginWaypoint`'s fixes were not used, per §2.

### Fix 2 — a Camera capture promotes its fix to `foundAt`, conditionally

**Where.** `onAddPhoto`'s `CameraCapturePhotoSource` branch (`:642`) now calls a new
`patchCameraCaptureLocationIntoFind(photoId, findId)` (`:722`) instead of the Album's
`patchCameraCaptureLocation`. Both share `requestAndPatchCaptureFix` (`:730`), the fix request and
photo patch that already existed; the find variant then calls `promoteCaptureFixToFind` (`:751`).
A new function on a new path, not a flag on the old one, so the Album's camera path keeps the
function it had.

**Guard 1, Camera only.** The call sits inside `if (source is CameraCapturePhotoSource)`, the same
type check that already gated the photo patch. `GalleryImportPhotoSource` never enters that
branch: its location is EXIF, read in `FilePhotoStore.persist` (`FilePhotoStore.kt:85`), and lands
on the photo row only, as before. `onPullPhoto` (From Album) has no location path at all. Nothing
was generalised across the three paths; the temptation the dispatch warns about did not arise
because the branch already existed.

**Guard 2, only when `foundAt` is null.** `promoteCaptureFixToFind` re-reads the editing entry
under `editingEntryMutex`, and returns without writing if the open entry is not the find the
capture was taken in, or if it already has a location (`:758`). The write is the entry copy plus
`saveEntry`, the way `onEntryEdited` persists a keystroke.

**One gap, by design and logged.** The provider's one-shot can take up to `LOCATION_TIMEOUT_MS =
20_000` (`AndroidLocationProvider.kt:112`). A fix that resolves after the user has closed the find
is not written anywhere and is logged (`:755`); writing to a find that is no longer open, by id
through the repository, was rejected because the user may be mid-edit on a different entry by
then and the only witness they have is the screen in front of them.

### Fix 3 — the picker opens on the device position

`findLocationPickerRegion(deviceLocation, fallback)` (new file `FindLocationPickerRegion.kt`):
the device position as a `Region` at `Region.MIN_RADIUS_KM` (1 km, zoom only, never submitted),
else the fallback, which is the search region or `JOURNAL_PICKER_DEFAULT_REGION` exactly as
before. `JournalTab` and `LogPanel` gain a defaulted `deviceLocation: LatLng?` parameter
(`JournalTab.kt:122`, `LogPanel.kt:107`) and call the function at their one
`CentrePinLocationPicker` site (`:309`, `:276`); `AvailabilityScreen.kt:1090` and `:1815` supply
`uiState.liveFix`. It was small: 30 lines and two call sites. No age bound on the picker's
position, deliberately: it frames a map the user is about to look at and confirm, so a stale one
costs a pan, not a wrong record.

**Checked before touching it:** `LogPanel`'s doc says its `region` serves both the find picker and
the Offline Maps picker (`LogPanel.kt:79-81`), but the only code use of the parameter is the
`CentrePinLocationPicker` call at `:276` (likewise `pickerRegion` in `JournalTab`, `:309` only), so
the Offline Maps picker is not moved by this change.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (this branch at `c689765`, the end of the viewer work, second full run that day) | 174 | 1370 | 0 | 0 | 24 |
| after (this branch at `2ed232c`) | 175 | 1382 | 0 | 0 | 24 |

Twelve tests added: ten in `MushroomLogViewModelTest` (`:1154-1301`), two in the new
`FindLocationPickerRegionTest`. Skip count unchanged; the allowlist in `.github/workflows/ci.yml`
untouched. `assembleDebug`: exit 0 on `2ed232c`. **Container runs on Linux, not GitHub Actions.** The
"before" here is the same branch before this dispatch's commits, not a clean `main` worktree: the
base for this dispatch is the viewer branch, and its own before/after against `main` is in
`2026-09-13-fullscreen-photo-viewer-completion-report.md`.

**The 12-failure Windows path-length pool is absent**, as §6 predicts for Linux: zero failures in
either run, nothing to subtract. Reported as absent, not passed over.

### The tests, all through the real entry points

- `a find started with no location takes the fresh device fix in hand` — `onStartNewEntry(null)`
  with a 10 s-old fix; asserts `foundAt` on the editing entry **and** on the persisted draft.
- `… no fix in hand still starts, with no location` — `foundAt` null, no error message.
- `a held fix at the lost bound is discarded, and the discard is logged` — a fix exactly
  `LOST_AFTER_MILLIS` old; `foundAt` null and a `MushroomLog` log line naming the bound.
- `a held fix just inside the lost bound is still used` — one millisecond inside.
- `a caller-supplied location wins over the device fix`.
- `a camera capture inside a find with no location promotes its fix to foundAt` — through
  `onAddPhoto(CameraCapturePhotoSource)`; asserts the editing entry, the persisted draft, and that
  the photo's own patch still happened.
- `a camera capture never overwrites a location the find already has`.
- `an Import into a find with no location leaves the find's location null` — **the negative §6
  asks for**: the imported photo carries EXIF 10.0/20.0 on its own row, the find stays null, and
  no live-fix request was made.
- `From Album into a find with no location leaves the find's location null`.
- `a capture fix that resolves after the find was closed is not written to it` — the fake
  provider is gated; the find is closed before the gate opens; no entry carries the fix, and the
  "no longer open" line is logged.
- `FindLocationPickerRegionTest` — device position → `Region(lat, lng, 1)`; none → fallback
  unchanged.

### Failing first: revert checks

The repo's established form. Each revert is one line, run through the runner from the viewer
dispatch (saved copy before editing, previous XML deleted, compile-error guard, restore from the
copy and byte compare). No compile errors; every failure names the reverted behaviour; the tree
was identical to the pushed commit afterwards.

| revert | failures produced |
|---|---|
| `createEntry(location ?: freshDeviceLocation(), …)` → `createEntry(location, …)` | 3: "expected LatLng(45.5, -122.6) but was null"; the logged-discard assertion; the inside-the-bound case |
| age check `>= LOST_AFTER_MILLIS` → `< 0` (never fires) | 1: "a fix past the bound must not become the find's location expected null, but was LatLng(45.5, -122.6)" |
| camera branch back to `patchCameraCaptureLocation(photoId)` | 2: the promotion case, "expected LatLng(45.5, -122.6) but was null"; the closed-find case (its log line never written) |
| `if (entry.foundAt != null) return` removed | 1: "expected LatLng(45.0, -122.0) but was LatLng(45.5, -122.6)" |
| picker: always `fallback` | 1: "expected Region(44.05, -121.31, 1) but was Region(45.326, -122.634, 15)" |

**Not revert-checked:** the "closed find" guard on its own (`entry == null || entry.id != findId`).
Removing the null half does not compile (smart cast), and removing only the id half leaves the
test passing through the null half, which is the path it exercises. The test's claim is therefore
"a late fix is not written after close", which the promotion revert did fail; the id-mismatch
half (a different find open by then) is covered by reading, not by a test that can fail on it.

---

## Disclosure

### Confirmed by observation

- Every line reference in this report, re-derived on this tree.
- Both creation sites and the null the Journal passes (`AvailabilityScreen.kt:1237` and `:1645` at §0,
  `:1238` and `:1646` after this change, pass the map point; `JournalTab.kt` `onStartEntry(null, LocalDate.now())`).
- `liveFix` absent from `ui/log` before this change, present in 4 `ui/availability` files.
- The schema claim, from `15.json`.
- All counts and failure messages above, from XML and build logs in this container.

### Inferred, not observed

- **That the held fix is usually fresh when a forager taps "+" in the field.** The 5 min bound is
  reasoned from how the collector runs; how often a real find is started more than five minutes
  after the last accepted fix under canopy (where the 50 m gate drops fixes) is a device question.
  If it is often, finds will save without a location more than expected and the bound is the knob.
- **That `AvailabilityScreen` actually hands the live fix to the picker.** The two lines are read
  (`:1090`, `:1815`); the picker harnesses' stub map slot ignores the region it is given, so no
  test asserts where the real picker opens.
- **Memory of the wiring in `MainActivity`.** The `currentFix` lambda (`:100`) is read, not
  tested; `MainActivity` has no test, the same precedent every earlier dispatch here records.

### Could not be determined

- **Whether a find logged from the Journal now shows a location on the owner's device.** §6 says
  the device is the authority; nothing here ran on one.
- **How old the held fix is in practice**, for the reason above.
- **Whether the pulse this dispatch was built from says what §1 says — resolved after the first
  version of this report, and the first version was wrong about where it was.** This report first
  said `docs/audits/2026-09-13-pulse-find-location-on-camera-capture.md` was "not in the tree".
  It is not on `origin/main` and not on this branch, which is what had been checked; but a
  `git fetch` of every remote head found it, with its index row, at `1522c41` on
  `origin/claude/ios-port-feasibility-mvsjcr` (2026-09-13 04:33 UTC), a branch 25 commits beyond
  `main` that has never been merged and is the head of open PR #101 (the z15 overflow worker
  fix "plus the audits behind it"), where the pulse is its most recent commit. So the pulse was written, pushed and indexed, exactly as
  reported, onto a branch that nothing downstream reads. The first check here fetched `main`
  only and listed two remote branches, and "not on either" became "not in the tree" in the
  writing. That is the derived-figure pitfall in CLAUDE.md, in a report about a missing record.
  The dispatch's §1 was checked against code either way, which is what §1 asked for.

### Premises in the dispatch that were wrong

- **`MainActivity.kt:390`** — the find creation wiring is at `:390` in the base
  (`onStartLogEntry = mushroomLogViewModel::onStartNewEntry`); it is `:393` after this change
  added three lines above it. Held at §0.
- **`LogEntryDetailScreen.kt:167` reads "Found at"** — no. `:167` is a `Modifier` chain; the
  "Found at" text is at `:178` (and the read view's at `LogEntryReportScreen.kt:161`).
- **`patchCameraCaptureLocation` at `:657-672`** — held at §0 (`:657`); it is `:709` now and is a
  one-line wrapper around the extracted `requestAndPatchCaptureFix`.
- **`PathHome.kt:59-62` "records a late first fix as normal"** — those lines record something
  adjacent: that the origin waypoint may itself be a network fix if recording began before GPS
  settled. The dispatch's constraint (never block on a fix) stands on its own; that citation does
  not carry it. **Where the reading came from, grepped after the owner named it:** the
  2026-09-11 way-back-route pre-build report (`:74`) cites the lines correctly, for the origin
  weakness; the two 2026-09-12 recording-state-resync reports (`section-1-gate.md:68`,
  `completion-report.md:29`, `:214`) cite them for a late or absent first fix, and this dispatch
  carried that reading forward. The misattribution is one day old and two documents wide, and
  those documents are not edited here (an audit is a record of its date); this line is the
  correction.
- **"`liveFix` is reachable from the log path"** — it was not, in the sense of anything in
  `ui/log` reading it; it is reachable in the sense that `MainActivity` constructs both ViewModels
  and can hand one's value to the other, which is what was done. Both readings are true; the
  dispatch's wording fits the second.
- **"The picker fix is small"** — held.
- **"The Journal passes null (`JournalTab.kt:385`)"** — the null is real; the line was `:385` at
  §0 and is `:387` after the parameter added above it.
- Everything else in §1 held as written.

### Decided beyond scope

- **The 5 min age bound on the creation fix**, reported above as the dispatch requires. The
  alternative, no bound, was rejected for the sofa-after-the-walk case; a tighter bound was
  rejected as inventing a number.
- **1 km as the picker's opening radius from a device position.** Zoom only, never submitted.
  The search region keeps 15 km. A picker opened where the user stands is for nudging by metres.
- **Logging the age discard and the closed-find skip at INFO.** CLAUDE.md: a fallback that fires
  is not silent. "No fix at all" is the ordinary case and is not logged.
- **A `now` clock parameter on `MushroomLogViewModel`** so the age check is testable, defaulted
  to `System::currentTimeMillis`.
- **`onAddPhoto`'s camera branch now targets a find-specific function.** The Album's camera path
  (`onAddGalleryPhoto`) is byte-for-byte the behaviour it had.

### Checks that did not fire, and empty results

- No CI run at the time of writing.
- `verify-design-tokens.sh` not run; no UI colours or motion touched.
- No test drives `AvailabilityScreen` for either the creation fallback or the picker position; both
  are wired there in one line each and read.
