# Hygiene scout: where the app is over-engineered and where it is under-engineered (UI sweep 3)

Dispatch 2026-09-28-654 (RECORD intent -654; preserved at `prompts/preserved/2026-10-07-08.md` on
branch `records-after-173`). Read-only survey, written 2026-10-07 (UTC) against `origin/main` at
`aa79f25a` (PR #189). That matched the dispatch's stated base. No code was changed, and no Gradle,
phone or emulator was used.

## In short, for the owner

The app is in better shape than its size suggests. The foundations hold: the logic about the world
(distances, tracks, return estimates, sundown) is kept apart from the screens, and every outside
service (location, compass, camera, the map, the internet) is reached through a connector the app
owns, with stand-ins for testing. No domain file uses Android screen code.

The imbalance is this:

- **Over-engineered: machinery built ahead of need, and a few things done three or four ways.**
  - The motion system is mostly unwired. Of its twelve named styles, nine have no user anywhere in
    the app. The "reduce motion" switch is declared but never connected, and its own comment says
    it is. Motion Part 1 is already wiring it.
  - The app logs errors two ways. Saved settings are read back four different ways.
  - Several files carry long histories of past dispatches in their comments. In some places the
    comments run three or four lines to every line of code, and at least one of those histories
    is now out of date.
- **Under-engineered: the failure paths, which is where it matters most.** The most serious items:
  - One unexpected error during a recording could end the recording mid-walk. The sundown check
    beside it is guarded against exactly this; the GPS path is not.
  - Cancelling a backup or restore is reported as "failed", and a half-written backup file is
    deleted on the way.
  - Opening a crash log that can't be read could itself crash the app.
  - An offline map region can vanish from the list with no trace in the log.
  - If the list of restored offline regions fails to load, the screen shows a shorter list as if
    it were complete.
  - Photos have no size limit at capture, and the small thumbnails decode with no upper bound.
  - About a dozen places quietly fall back to a default without writing a log line, which
    CLAUDE.md does not allow.
- **A handful of very large files do many jobs at once.** The map's main drawing function is about
  1,100 lines, and the map screen's state holder is 1,718. They work and are heavily tested, but
  every change to them is a large change.

**Counts.** "Over" means too much machinery; "under" means too little.

| Area | Over | Under |
|---|---|---|
| Map and its chrome (with the motion package) | 4 | 6 |
| Journal, records, finds, camera, GPX | 4 | 9 |
| Recording, navigation, alerts, location | 1 | 10 |
| Data (Room, DataStore, backup, network, wiring) | GAP_DATA_OVER | GAP_DATA_UNDER |
| List, Seasonal, search, Settings, Tools | GAP_LIST_OVER | GAP_LIST_UNDER |
| Build and dependencies | GAP_BUILD_OVER | GAP_BUILD_UNDER |
| **Total** | GAP_TOTAL_OVER | GAP_TOTAL_UNDER |

This is a list to choose an order from. It is not a design and nothing in it is scheduled.

## How it was done, and how far to trust it

- The app was surveyed by reading code: by me, and by five read-only sub-surveys (map; journal and
  camera; recording and navigation; data, tabs and build; and a second pass on the files the first
  data survey ran out of time for).
- I re-read the code myself for every item in the top ten and for a sample of the rest. Each of
  those items is marked **re-read**. Other items rest on the sub-survey's reading and are marked
  **survey**.
- Caller counts come from `git grep -n NAME -- app/src/main`, and test counts from
  `git grep -l NAME -- app/src/test app/src/androidTest`.
- A count of zero test files means no test names the symbol. It does not rule out the behaviour
  being reached indirectly through a screen test. Where that matters it says so.
- Line numbers are as of `aa79f25a`. Paths are relative to
  `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/`, `docs/` or `.github/`.
- The CLAUDE.md rule for sorting:
  - Hand-copied logic (the same lines in several places) counts as **under**, as the dispatch lists it.
  - The same job done in several different styles counts as **over**.
- Items already recorded as deliberate are listed at the end of each area, not counted.

## The ten that matter most

1. **R1. One error on the GPS path can end a recording** (under, re-read).
   - Code: `service/TrackRecordingService.kt:164-184`.
2. **D1. Backup and restore turn a cancel into a failure** (under, re-read).
   - Code: `ui/backup/BackupViewModel.kt:239-243`, `:383-387`.
3. **J1. The crash log viewer can crash on an unreadable file** (under, re-read).
   - Code: `ui/crash/CrashLogPanel.kt:152-154`.
4. **M1. A complete offline region can drop out of the list with no log** (under, re-read).
   - Code: `map/OfflineRegionReconciliation.kt:59-72`, with its parser `map/MapLibreOfflineRegionMetadata.kt:45-63`.
5. **M2. A failed read of restored regions is shown as a complete list** (under, re-read).
   - Code: `ui/availability/AvailabilityViewModel.kt:1089-1093`.
6. **J2. Photos have no size limit at capture, and thumbnails decode unbounded** (under, re-read).
   - Code: `photo/CameraXCaptureSession.kt:271-273`, `ui/log/DecodedPhoto.kt:68`, `:107`.
7. **X1. About a dozen unlogged fallbacks** (under, re-read for most). This is the CLAUDE.md
   "no default fallback that isn't logged" rule, in several areas at once:
   - R2, R3, J4, J5, D2, D6, M1.
8. **M3. Motion machinery defined and unused, and one comment that says otherwise** (over, re-read).
   - Code: `ui/motion/ReduceMotion.kt:18-23`, `ui/motion/MotionPrecedence.kt`, `ui/motion/MotionTokens.kt`.
   - Overlaps motion Part 1.
9. **X2. Very large files doing many jobs** (under, re-read for counts):
   - `ui/map/SightingsMap.kt` (`SightingsMap()` spans `:214-1322`);
   - `ui/availability/AvailabilityViewModel.kt` (1,718 lines);
   - `ui/log/MushroomLogViewModel.kt` (1,277);
   - `ui/track/TrackRecordingViewModel.kt` (1,218);
   - `ui/availability/AvailabilityScreen.kt` (1,853).
10. **X3. Hand-copied logic in five places** (under, re-read). The copies:
    - `hasLocationPermission` (six copies);
    - three HTTP client builders;
    - a local flat-earth projection (three copies) and a second haversine;
    - four "count or zero" helpers in `MainActivity`.

The rest of this document gives each of these, and the smaller items, by area.

GAP_BODY
