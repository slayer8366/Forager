# GPX import (plan T16): completion report

Dispatch 2026-09-28-634 (`prompts/preserved/2026-10-07-02.md` on `records-after-173`), the owner's
answers in RECORD -636 (final), the Gradle go in -635. Branch `gpx-import`, cut from `origin/main` at
`dcad84a0` (PR #188). No PR opened. No phone used.

## What landed

| Commit | What it does |
|---|---|
| `68b9628b` | The tests, with the import code present but stubbed so each test could be seen failing for its own reason (red). |
| `7e1df31c` | The import, built: the stubs replaced by the real code from saved copies. |
| this commit | This report and its two index rows. |

All pushed to `origin/gpx-import`; tree clean at each push.

**Where the work happened.** The dispatch named the worktree `/tmp/claude-1000/gpx-import`. The `/tmp`
tmpfs has a per-user quota that other worktrees in `/tmp/claude-1000` (not mine, not touched) had nearly
filled: the first baseline compile died with `java.io.IOException: Disk quota exceeded` (Kotlin daemon
log) and, before that, a 5 GB cgroup OOM kill. I removed the `/tmp` worktree (nothing built in it was
lost: the work was carried as a patch, `git apply`'d) and recreated `gpx-import` at
`~/.cache/forager-wt/gpx-import`. Gradle runs used `-Dorg.gradle.jvmargs=-Xmx1536m` and
`-Pkotlin.daemon.jvmargs=-Xmx2g` so the Gradle and Kotlin daemons fit together under the 5 GB cap
(an in-process Kotlin compile was tried and fails with a compiler-plugin `NoSuchMethodError`). The root
disk had 2.2 to 2.4 GB free throughout.

## Verification before building

The earlier coder's verify findings (RECORD -636) were re-checked against `dcad84a0`, not taken over:

- **Open with into MainActivity would touch a running recording.** Confirmed:
  `TrackRecordingViewModel.kt:268-272` (`init` calls `takeUpRunningRecording()` and the abandoned-track
  sweep) and `MainActivity.kt:434-450` (the `LaunchedEffect(trackUiState.activeTrack)` that sends
  `ACTION_START`). Hence the owner's "invisible doorway".
- **`GpxCodec.decode` has no production caller.** Confirmed: `git grep` in `app/src/main` finds only doc
  comments (`GpxDocument.kt:13,24`, `TrackPointRecord.kt:23`). It is still uncalled; import is a new
  reader (below), not a change to it.
- **Waypoints insert with REPLACE.** Confirmed, `WaypointDao.kt:40`. Hence fresh ids on import.
- **Database version.** `origin/main` declares `version = 17`. Every ref on both remotes (276) was
  searched for `MIGRATION_17_18` or `version = 18` in `ForagerDatabase.kt`: none. 18 claimed.

## What was built

**One way to import, two ways in.** Records > Tracks > "Import GPX" opens Android's file picker
(`ACTION_OPEN_DOCUMENT`, no permission) for `application/gpx+xml`, `application/octet-stream`,
`text/xml`, `application/xml`. Open with and Share reach `GpxImportActivity`, a translucent Activity with
no UI. Both save through `ImportGpxUseCase`.

**The doorway.** `GpxImportActivity` reads and saves the file (non-cancellable, so leaving it cannot cut
an import in half), then starts MainActivity with `NEW_TASK | CLEAR_TOP | SINGLE_TOP` and the outcome as
extras, and finishes. An existing MainActivity gets it through `onNewIntent`; none gets a second
instance. It builds no TrackRecordingViewModel and sends nothing to the recording service.
`noHistory`, `excludeFromRecents`, empty `taskAffinity`, and a `configChanges` list so a rotation does not
import twice. Manifest filters: VIEW of gpx+xml, text/xml, application/xml (content and file); VIEW of
octet-stream or `*/*` where the path ends `.gpx` / `.GPX`; SEND of gpx+xml, text/xml, application/xml.
SEND of octet-stream is left out: a Share has no path, so it would offer Forager for every file.

**Where it lands.** Journal > Records > the Tracks chip, with the first new track's details open (the
sheet opens once the reloaded list holds the track). The message is a Toast, as the Journal says its own
failures. The drawer closes and map fullscreen ends, as leaving the Maps tab ends it. A failed import
lands on the same Tracks list.

**The reader (`GpxImportReader`), new beside `GpxCodec`.** Each `<trk>` with points is one track,
segments joined. Routes alone give "route but no track". A DOCTYPE is refused before parsing and the
entity resolver refuses external entities (not `setFeature`, which Android's own parser throws on). A
point or waypoint with missing or out-of-range lat/lon makes the file unreadable rather than being
dropped. Times: `Z`, offsets, and zone-less (UTC, as the GPX 1.1 schema states for `<time>`).

**The use case.** 10 MiB limit (10 x 1024 x 1024), read no further than the limit. Times rounded to the
nearest second. A track missing any point time is imported as no-times: dated the import moment, points
in file order, stored one whole second apart as an ordering only. Name: the track's `<name>`, else
`<metadata><name>`, else the file name without `.gpx`; with several tracks, a fallback name gets " (n)".
Fresh ids. Loose waypoints go to the first track; no name becomes "Waypoint N" (N its place in the file);
no time takes the track's start; a Forager ORIGIN/END designation is kept as a label only. Every track
ended. Each track is written with its points in one Room transaction (`TrackDao.insertTrackWithPoints`,
`TrackRepository.createWithPoints`); a failure part way deletes what this import wrote and reports
"Couldn't save this file's tracks. Nothing was imported."

**Storage.** `MIGRATION_17_18` rebuilds `tracks` with `importedAtEpochMillis` (nullable) and
`importedWithoutTimes` (`NOT NULL DEFAULT 0`), copying existing rows as `NULL, 0`. A rebuild, not
`ADD COLUMN`, for the reason `MIGRATION_12_13` records (legacy fixtures declare `TrackEntity`).
`18.json` committed. Every legacy migration test's chain gained `MIGRATION_17_18`, and
`JournalBackupTest`'s version constants moved 17 to 18 (and "newer" 18 to 19).

**Readers of the new columns.** Records rows: the title is now `trackTitle` (name, else start time, so a
recorded walk with no name reads as before) and an imported track's subtitle is "Imported · date ·
N points". Details: an "Imported" label beside the title and an "Imported" field (the import time);
for no-times, Started, Ended and Duration read "No times in file". The map bubble's duration reads
"No times in file" too. The Journal's day reads leave out imported tracks
(`TrackDao.getTracksForDay`) and waypoints linked to one (`WaypointDao.getForDay`).

**Not touched:** recording, the service, navigation, alerts, the walk logger, `GpxCodec` and the export
format.

### Decided beyond the owner's answers (each easy to change)

1. **A file with waypoints and no track** is not imported: "This file has no track to import". No ruling
   covered it; this follows the routes answer.
2. **A save failure's message**: "Couldn't save this file's tracks. Nothing was imported."
3. **A failed Open with still opens Forager** (on Records > Tracks, with the message), so both ways in
   end in the same place.
4. **A track with some times and some gaps** is imported as no-times rather than guessing the gaps.
5. **Imported waypoints stay out of the Journal** with their track ("Records and map only"). If the
   imported track is deleted its waypoints are unlinked (existing behaviour) and then appear in the
   Journal as ordinary waypoints.
6. **The Records row title shows a track's name** (it showed the start time only); without it the
   imported name would show nowhere in the list.
7. **Imports read `<trkseg>`, not Forager's `forager:fullRecord`.** Importing the full record would show
   points the export marks excluded, once rounded.
8. **A no-times track's stored point times run forward from the import moment**, one second apart; a
   10,000-point file ends about 2.8 hours "after" its import. Nothing reads them as times (the Journal
   excludes imported tracks; every surface says "No times in file").

## Evidence

### Red, then green

Red run (`68b9628b`, stubs): the 8 classes ran 48 tests, 26 failures, each matching its stub (reader:
"expected an import, got Failed(reason=UNREADABLE)"; migration: "Migration didn't properly handle:
tracks"; button: "touch 1 ... opened the picker expected:<1> but was:<0>"; manifest: resolved `[]`; and
so on). The other 22 passed under the stubs; those are covered by the revert checks below or are pure
helpers (the Intent extras round trip, the message words) and the default reading of a recorded walk.
Green (`7e1df31c`): the new classes plus every migration, backup, derived-trip and Records test, 203
tests, 0 failures.

### New tests

- `ImportGpxUseCaseTest` (13, Room): the app's own export (`TrackGpxExporter`, with a hidden network fix
  and an ORIGIN waypoint) imported back equal in points, times, name and waypoints, with fresh ids;
  importing twice makes two tracks and overwrites nothing; shape A, B and C fixtures (other apps' exports)
  written by hand; three tracks; no times; six refused files; XXE; over 10 MB; unopenable; rollback.
- `ImportedTrackOutOfJournalTest`: the derived trip holds the recorded walk and its own and loose
  waypoints, not the imported track or its waypoints; `getAll` (Records, map) holds all.
- `GpxImportActivityTest` (8, the real application and container): the filters resolve seven real
  Intents to `GpxImportActivity` only, and two non-GPX ones to nothing; Open with and Share save and
  open MainActivity with the outcome and the three flags; a broken file stores nothing; **with a
  recording running** (an open track with points, `ReturnWatch` begun), the recording's row and points
  and the watch state are unchanged, no service Intent is sent, and a canary track left open by "an
  earlier process" stays open (the first TrackRecordingViewModel in a process would end it).
- `ContentUriGpxFileSourceTest`: limit + 1 byte is too big, the limit exactly is read whole.
- `GpxImportRecordsTest`: five real touches at different points of the "Import GPX" button's own bounds
  each open the picker with the four types; a picked file goes through the real ViewModel, file source,
  use case and Room into Records with its details open; the row and details labels; "No times in file".
- `AvailabilityScreenGpxImportTest`: from the Maps tab, a notice lands on Records > Tracks with the
  details open; three tracks Toast "Imported 3 tracks"; a failure Toasts the owner's words.
- `ImportedTrackBubbleTest`; `SchemaMigrationTest` 17 to 18 and the chain 4 to 18 against `18.json` (its
  seeding learned BLOB: 17.json is the first start with a BLOB column).

### Revert checks

Runner: save a copy, edit, compile, refuse to read results if the log has any `e:` line or the compile
failed, read only XML newer than the run's start, restore from the saved copy (never git), then confirm
the file equals HEAD. All 22 compiled, all restored equal to HEAD.

| Edit | Test | Failure |
|---|---|---|
| R1 no rounding | shape B | "both segments ... .400 rounds down, .600 up ..." |
| R2 `importedWithoutTimes = false` | no times | assertion on the flag |
| R3 waypoints unlinked | five tests | e.g. "expected:<[Car, Spring]> but was:<[]>" |
| R4 first `<trk>` only | three tracks | "expected:<[Day one, Three days (2), Day three]> but was:<[Day one]>" |
| R5 routes not told apart | refused files | "expected:<Failed(reason=ROUTE_ONLY)> ..." |
| R6 no size limit | file source | "expected:<TooBig> but was:<...Bytes...>" |
| R7 Journal track filter off | Journal | "only the recorded walk expected:<[recorded]> but was:<[..., recorded]>" |
| R8 Journal waypoint filter off | Journal | "... expected:<[Own, Loose]> but was:<[Car, Spring, Own, Loose]>" |
| R9 migration copies `1` | schema 17 to 18 | "a recorded walk has its times expected:<0> but was:<1>" |
| R10 button does nothing | touches | "touch 1 ... expected:<1> but was:<0>" |
| R11 no row label | row | "[Ridge loop,Imported · ... · 2 points]" not found |
| R12 details ignore no-times | details | "[Started,No times in file]" not found |
| R13 bubble ignores no-times | bubble | "expected:<[No times in file]> but was:<[1h 0m]>" |
| R14 no switch to the Journal | screen (3) | "(Selected = 'true')" / title not found |
| R15 drop the octet-stream data line | Activity | **no failure**: the same filter's `*/*` already matches octet-stream; the line is redundant, kept to mirror the owner's list |
| R15b drop the `.*\.gpx` path pattern | Activity | "VIEW octet-stream with a .gpx path expected:<[...GpxImportActivity]> but was:<[]>" |
| R16 `NEW_TASK` only | Activity (4) | "in Forager's own task ... expected:<872415232> but was:<268435456>" |
| R17 no rollback | rollback | "the first track, already written, is gone again" |
| R18 fault: send `ACTION_START` | recording running | "nothing sent to the recording service expected null, but was:<Intent { act=...START..." |
| R19 fault: run the abandoned-track sweep | recording running | "still open: no recording ViewModel was built expected null, but was:<...>" |
| R20 details not opened | screen (2) | title not found |
| R21 all three XXE defences removed | XXE | "expected:<Failed(reason=UNREADABLE)> but was:<Imported(...)>" |

R18 and R19 are injected faults, not reverts: there is no line to revert for "builds no ViewModel". They
show the recording-running test's assertions can fail.

### Full suite

Every run: results directory deleted, `--rerun`, compile first, the Kotlin daemon stopped, then tests,
under the 5 GB cap, Java's temp folder on disk.

- **Before** (`dcad84a0`, separate worktree): **3,932 tests, 0 failures, 0 errors, 24 skipped**, 480 XML files.
- **After** (`7e1df31c`): **3,966 tests, 0 failures, 0 errors, 24 skipped**, 487 XML files, 0 `e:`
  lines, every XML newer than the run's start. 3,966 = 3,932 + 33 new tests + 1 new
  `SchemaMigrationTest` case; 487 = 480 + 7 new classes. The skipped set is the same 24 by test name.

## Not tested, said plainly

- **Android's own XML parser.** Robolectric runs the JDK's JAXP. The reader avoids `setFeature`
  because Android's parser throws on unknown features, and uses only `isNamespaceAware`,
  `isExpandEntityReferences` and `setEntityResolver`, which Android's implementation has; not run on a
  device.
- **What real apps send.** The filters are tested against Intents I built. Which MIME types Files, Gmail,
  Drive and others actually send for a `.gpx`, and whether the picker greys out `.gpx` files on the S22,
  is device-only. A content Uri with no `.gpx` in its path and an octet-stream type will not offer
  Forager (filter 1 needs a GPX or XML type); the in-app button covers that case.
- **Back after Open with.** Starting MainActivity with `NEW_TASK` should leave the sender on Back, as
  Android does; Robolectric cannot show task stacks.
- **A real running service.** The recording-running test has an open track and a begun `ReturnWatch`,
  not the foreground service itself.
- **Fixtures are hand-written** in each app's shape, not those apps' output.
- **Speed.** A 10 MB file's parse time and memory on the phone are unmeasured.

## Device check (S22), in order

1. Records > Tracks: "Import GPX" shows above the list. Tap it; the system picker opens. Pick a `.gpx`
   exported from Forager. Pass: Records shows the track with its name and "Imported · date · N points",
   its details open with "Imported" beside the title. If a `.gpx` is greyed out in the picker, note the
   provider (Downloads, Drive): that is the type list, not the import.
2. From the Files app, Open with on a `.gpx`: Forager is offered; choosing it opens Forager on that
   track. Then Back: note where it goes (the Files app expected).
3. Share a `.gpx` from another app (Gmail attachment or Drive) to Forager. Same pass as 2. If Forager is
   not offered, note the app: it sends a type the filters do not list.
4. With a recording running, repeat 2. Pass: the recording notification and the strip are unchanged
   and the walk keeps recording; the imported track appears.
5. The imported track's map bubble > Details works as for any track. A no-times file (one drawn in a
   planner) shows "No times in file" in Started, Ended, Duration and the bubble.
6. The Journal for the imported track's date does not list it.

Observation, not a gate: how long a large file (several MB) takes to land.
