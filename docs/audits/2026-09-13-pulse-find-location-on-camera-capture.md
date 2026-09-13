# Pulse — in-app camera capture leaves the find with no location

**Date:** 2026-09-13. **Read only; nothing changed but this report.**
**Dispatch:** `pulse-find-location-on-camera-capture.md` (planner, no repo access).

## §0 — Base

`origin/main` = **`175b050`** ("Merge pull request #100"), fetched before any read. Executing
branch `claude/ios-port-feasibility-mvsjcr` @ `f4c517d`, 24 ahead / 0 behind; it touches nothing
under `app/`, and every read below is against `origin/main` explicitly.

## The finding, in one paragraph

**A find's location is set at the moment it is started, or never — and no photo path ever touches
it.** A find started from the map ("Log a find here") is created with the tapped or centred point;
a find started from the Journal is created with `null`. The only thing that changes `foundAt`
afterwards is the "Add Location"/"Change Location" picker. Camera capture, Import, and From Album
all write only the **photo** row: the camera path *does* take a live device fix — onto
`log_photos.latitude/longitude`, where "Found at" never looks. So the two device finds differ by
where each was started, not by which photo button was pressed. The premise that Import fills the
find's location from EXIF is wrong; Import fills the photo's.

## §2 — The three paths, traced

**What creates the row, and when.** `MushroomLogViewModel.onStartNewEntry(location: LatLng?, date)`
(`:275`) → `createEntry(location, date)` → `CreateMushroomLogEntryUseCase`, which "persists it
immediately, as an uncommitted draft" (`CreateMushroomLogEntryUseCase.kt:12`) via
`MushroomLogEntry.draft(id, location, date)` — `foundAt = location` (`MushroomLogEntry.kt:89`).
**The row exists before any photo.** Two callers, wired at `MainActivity.kt:390`
(`onStartLogEntry = mushroomLogViewModel::onStartNewEntry`):

| Start point | Call | Location passed |
|---|---|---|
| Map, "Log a find here" | `AvailabilityScreen.kt:1231-1237` (and `:1638-1645`): `onStartLogEntry(location, LocalDate.now())`; the pending-action route passes `cameraCenter` (`:2872`) | **the tapped/centred point** — a map coordinate, not a device fix |
| Journal, new find | `JournalTab.kt:385`: `onStartEntry(null, LocalDate.now())` | **`null`** |

**Who ever writes `foundAt`** (`git grep foundAt`, control: the render sites at
`LogEntryDetailScreen.kt:167` and `LogEntryReportScreen.kt:156` appear): the factory above; the
picker's confirm, `onEntryChanged(editing.copy(foundAt = location))` at `JournalTab.kt:312` and
`LogPanel.kt:279`; and the Room read mapping at `RoomMushroomLogRepository.kt:322`. **Nothing else.**

**The three buttons** (`LogEntryDetailScreen.kt:248-260`):

| Button | Path | Writes a location? | Where, from what |
|---|---|---|---|
| **Camera** | `photoAcquisition.launchCamera` → `CameraCapturePhotoSource` → `onAddPhoto` (`:566`) / `onAddGalleryPhoto` (`:625`) → `patchCameraCaptureLocation(photoId)` (`:657-672`) | **photo row only** | `locationProvider.getCurrentLocation()` — a fresh one-shot after the capture returns — then `updatePhotoLocation(photoId, lat, lng)` (`MushroomLogDao.kt:70`). Fire-and-forget; `PermissionDenied`/`Unavailable` → `return@launch`, photo stays `null` |
| **Import** | `photoAcquisition.launchGallery` → `GalleryImportPhotoSource` → `FilePhotoStore.persist` (`:70`) | **photo row only** | EXIF via `ExifInterface.latLong` (`:116-120`), API 29+ only (`:112`), `null` when absent (`:124`) → `LogPhoto.latitude/longitude` (`:91-92`). `foundAt` untouched |
| **From Album** | `onPullPhoto` → `PullPhotoIntoEntryUseCase` | **nothing** | `attachPhotoToEntry(entry.id, photo.id)` then `entry.copy(photos = …)`; the photo's own lat/lng is not copied |

**Stripping, precisely.** For a capture, EXIF is **not read** (`FilePhotoStore.kt:85`:
`ExifData(null, null, null)` unless `GalleryImportPhotoSource`), and the file is a plain byte copy.
`CameraCaptureFiles.kt` contains no EXIF handling at all (grep for `exif|strip|gps|location`:
zero; control — `FilePhotoStore.kt` hits). The doc at `FilePhotoStore.kt:38-66` says the stored
copy stays free of GPS EXIF "for `GalleryImportPhotoSource` and `CameraCapturePhotoSource` alike"
by relying on platform redaction (API 29+, `setRequireOriginal`) for imports, and records that on
API 26-28 nothing redacts. For a **capture**, the destination is the camera app's own output
written into Forager's `FileProvider` file; no redaction path applies to that write. Whether a
captured file carries GPS EXIF therefore depends on the camera app, not on this code, which
neither reads nor removes it. "Stripped" is the wrong word; "not read" is the right one.

## §3 — Device location available at capture time

| Source | Reachable from the find-entry path? | Live with no recording? | Age bound |
|---|---|---|---|
| `LocationProvider.getCurrentLocation()` (one-shot) | **Yes** — `MushroomLogViewModel` receives `container.locationProvider` (`MainActivity.kt:95`), used **only** by `patchCameraCaptureLocation` (doc `:151`) | Yes — a fresh request each call | Fresh by construction: `AndroidLocationProvider` races `requestSingleUpdate` on GPS and network, 20 s timeout, **no `getLastKnownLocation`**; unchanged since `892883c`. Carries **no accuracy** (`LocationResult.Success`: lat/lng/altitude) |
| `AvailabilityViewModel.liveFix` (stream) | **Not from `ui/log`** — `liveFix`/`liveLocation` appear in 0 files there (control: 4 in `ui/availability`). **In scope at the map's creation path**: `onLogFindHere` lives in `AvailabilityScreen.kt`, the same file that reads `uiState.liveLocation` for the compass strip (`:3757`) | **Yes** — acquired on `ON_START`, released on `ON_STOP`, "deliberately not need-gating … subscribes on every tab" (`AvailabilityViewModel.onEnteredForeground` doc); independent of recording | Held indefinitely once fixes stop; `ageMillis` exists (`LocationTracker.kt:91`); the only consumer that bounds it is the HUD via `LOST_AFTER_MILLIS = 5 min` (`NavigationReadout.kt:67`) |
| `TrackRecordingViewModel`'s collector (feeds `createOriginWaypoint`) | No — a different ViewModel | **No** — `beginLocationTracking()` has one call site, `startRecording`'s success path (re-derived on this base, 2026-09-12) | n/a without a recording |

So the dispatch's §3 pointer is half right: `createOriginWaypoint`'s fixes are in hand **only while
recording**, and a find logged without recording — the common case — has no fix from that source.
The source that *is* live whenever the app is foregrounded is `AvailabilityViewModel`'s, and it is
reachable at the map's creation path but not from the Journal's, which is the path that passes
`null`.

## §4 — The no-fix case

- **Save never blocks on location.** `onSaveEntry()` (`:407`) → `commitDraftEntry(draft)`; no
  location call anywhere in it. `patchCameraCaptureLocation` is launched unawaited (doc `:151-160`:
  "never delays the capture flow"). Under canopy the photo's coordinate stays `null` and the save
  completes — the shape the dispatch requires already holds for the photo row.
- **Add/Change Location works on a null-location find.** `LogEntryDetailScreen.kt:172`:
  `Text(if (entry.foundAt != null) "Change Location" else "Add Location")`; the picker's confirm
  sets `foundAt` (`JournalTab.kt:310-312`). The picker opens on **`uiState.region ?:
  JOURNAL_PICKER_DEFAULT_REGION`** (`AvailabilityScreen.kt:1813`) — the *search* region, not the
  device's position — so a user adding a location afterwards pans from wherever they last searched.
- **Nothing distinguishes "no fix available" from "user chose not to set one."** `MushroomLogEntry`
  carries `foundAt: LatLng?` and no other location-related field (`:40`; grep for
  `location|Fix|Source` fields: one hit).
- **Can the current code attach-if-available without restructuring?** The pieces exist:
  `onStartNewEntry` already takes `LatLng?`, `foundAt` is already nullable and already written
  from a `LatLng`, and a one-shot provider is already injected into the log ViewModel and already
  used with a "null when absent" contract. That is an observation about what exists, not a design.

## §5 — Schema

**No schema change needed — confirmed from the schema file, not assumed.**
`app/schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase/15.json`, version 15:
`mushroom_log_entries.lat`/`.lng` REAL, nullable; `log_photos.latitude`/`.longitude` REAL, nullable.
Both are populated today — the entry's by the map path and the picker, the photo's by Import and
by Camera. `MIGRATION_15_16` is not on the table.

## §6 — Report

**1. Confirmed by observation (file and line, on `175b050`):** everything in the tables above; the
two creation call sites and what each passes; the three `foundAt` writers with the render sites as
the known positive; `patchCameraCaptureLocation`'s target being the photo row; `PullPhotoIntoEntry`
copying nothing; `FilePhotoStore.kt:85` skipping EXIF for captures; `CameraCaptureFiles` handling
no EXIF; `onSaveEntry` making no location call; the button label switch; the picker's region; the
v15 columns; `LOST_AFTER_MILLIS`; `liveFix` absent from `ui/log` with a control that hits.

**Inferred:** which of the two device finds was started from which surface. The code admits only
two origins for a `foundAt` — creation from the map, or the picker — and one origin for a `null`,
creation from the Journal; the device log was not read.

**2. Could not be determined:** whether a captured file carries GPS EXIF from the camera app (this
code neither reads nor removes it); the accuracy of the fix `patchCameraCaptureLocation` stored,
because `LocationResult.Success` carries none and `awaitFirstLocation` takes whichever of GPS or
network answers first — the coarse fix is the likely winner under canopy (round 4, §2a).

**3. Premises in the dispatch that were wrong:**
- "**Import from album: EXIF is read and the find's location is filled from it.**" EXIF fills the
  **photo's** `latitude/longitude`. The find's `foundAt` is never touched by an import. "Working as
  intended" was true of the photo row and read as true of the record.
- "**In-app camera capture: EXIF is stripped.**" Not stripped: not read (`:85`), and the byte copy is
  left as the camera app wrote it. Removing GPS EXIF from the destination is explicitly out of
  scope per the doc (`:59-62`).
- "**The in-app capture path … does not substitute the device's own location.**" It does — onto the
  photo row (`:657-672`). The gap is one level up: no photo path, capture included, writes the find.
- "**The gap** is the in-app capture path." The gap is the **Journal creation path**
  (`JournalTab.kt:385`, `null`) plus the fact that nothing after creation but the picker writes
  `foundAt`. A find started from the Journal and then photographed by Import shows "No location
  set." exactly as one photographed by Camera does.
- "**`createOriginWaypoint` already consumes fixes … a recent fix may already be in hand.**" Only
  while recording; the collector it reads has one call site, on `startRecording`.
- "**A recent fix is reachable**" from the find-entry code path — via a fresh one-shot, yes; the
  streaming fix is reachable at the map's creation path and not from the Journal's.
- "No schema change needed" — **right**, and confirmed.

**4. Decided beyond scope:** nothing. No code changed, no fix proposed. Empty results were reported
with their controls; no check failed to fire.
