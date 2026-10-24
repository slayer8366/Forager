# Report: S22 re-check of #391, the recording line clears on Stop (dispatch 2026-10-24-06)

**Answer:** pass on every step. After Stop, in the app or from the notification, the dotted recording line was gone on the first screenshot, about 0.3 s later. It stayed gone with Tracks on and off, after a tab change, and after backgrounding and reopening.

**Helper:** Hazel. **Run:** 2026-10-24, 11:00 to 11:10 UTC. **Phone:** S22 (R5CT321008R).
Screenshots stay off GitHub. No positions here; the test spot was a public downtown street, far from Area 1.

## Build and database
- **Installed:** the `app-debug-apk` artifact of run 40086109829 (PR #391), downloaded into an empty directory outside every repository, installed with `adb install -r`, no uninstall.
- **Version string:** `aapt2` on the file and `dumpsys package` after install both read **1.0.3904+gb4206e22** (versionCode 3904).
- **Why the hash isn't 3ad490b59:** b4206e22 is the commit a pull_request run builds: GitHub's merge of PR head 3ad490b59 into pre-main 073fc77bd. Its parents are 073fc77bd and 3ad490b59, it has no tree difference from 3ad490b59, and it carries the fix line.
- **Database versions:** the installed build (1.0.3901+g073fc77b) was at **22**, read from the database header on the device before installing. b4206e22 declares **22** (`ForagerDatabase.kt:206`). Same version, so the install went ahead. The header still reads 22 after the check.
- **Before starting:** installing restarted the app, so the stale line from 2026-10-24-04 was gone before the first Record. It is not evidence either way, as the dispatch said.

## Steps (gps and network test providers, whole-second `--time`, one fix a second; each recording about 44 s, three or more 15 s polls)

**1. Stop in the app: pass.**
- **Before Stop:** the dotted line was drawn from the start to near the walker.
- **After Stop:** on the first screenshot, about 0.3 s after the tap, the dotted line was gone and the record button was idle. Still gone at 1 to 4 s, 10 s and 30 s.
- **Tracks off, then on (Layers):** no dotted line either way.
- **The saved track's solid line didn't show until the tab change.** For 30 s after Stop, with Tracks on, the map showed neither line. The solid line appeared when I went to Journal and back to Maps. That matches 073fc77bd: in the 2026-10-24-04 check, run B, after the screen's Stop the solid line also first appeared at the tab change (the stale dotted line was showing in its place before). So that part is unchanged by #391. But the "brief moment with neither line" (Sorrel) lasts until a tab change here, not a moment. I report it without judging it.

**2. Stop from the notification: pass.**
- I expanded the Forager notification ("Recording a track") and tapped "Stop recording".
- On the first screenshot after the shade closed, the record button was idle and the dotted line was gone. Still gone at 10 s and 30 s, and with Tracks off and on.
- **The solid line still visible on screen was step 1's saved track**, not this recording. Step 2 started where step 1 ended, and that line already showed after the step 1 tab change.

**3. After each Stop, tab change and background-and-reopen: pass, both paths.**
- Journal → Maps: no dotted line. The saved track's solid line showed, as it does today.
- Home → reopen: same.
- After the reopen, with Tracks off, nothing was left on the map for either recording, so no dotted line was hiding under the solid one.

## The test providers vanished again
- **What happened:** both test providers were removed 4 s after I added them, at 11:00:30 UTC. `dumpsys location` logs "removed mock provider override" at a moment when no command of mine ran. This matches the removal during run D1 of 2026-10-24-04. **Cause: could not determine.**
- **What I did:** the feeder now checks each update, re-adds the providers if they are gone, and logs each re-add.
- **Log:** exactly one re-add, at the feeder's start (11:01:48), before the first Record. No re-add happened during either recording, so both runs had test fixes throughout.

## Phone left as found
- **Build:** 1.0.3904+gb4206e22 installed (same schema, as the dispatch allows).
- **Test data:** both test tracks and their three Start/End waypoints were deleted through the app. A database read afterwards showed tracks 0, track_points 0, waypoints just "Waypoint 1", offline_regions 1. The temporary database copy has been deleted.
- **Area 1:** untouched. Only its route folder is on disk.
- **Test providers removed:** "removed mock provider override" logged for both at 11:07:38 UTC. The shell's mock-location appop is back to "default", and the feeder has stopped.
- **Location:** on. A real GPS fix arrived within 45 s.
- **Night Maps:** off (checkbox unticked in Settings).

## Disclosure
- **Confirmed vs inferred.**
  - Confirmed on the device: every clear/stay observation above, the version string, both database versions, the provider events, and the database counts after cleanup.
  - Read from git: the APK's commit is the PR merge of 3ad490b59 into 073fc77bd, with no tree difference from 3ad490b59.
- **Could not determine.**
  - What removes the test providers.
  - Whether the dotted line goes in under 0.3 s; the screenshots are about 0.5 s apart.
- **Premises that were wrong.**
  - The APK is labelled with the merge commit b4206e22, not 3ad490b59. Same tree.
  - "A brief moment with neither line": after the screen's Stop it lasts until a tab change, the same as before the fix.
- **Decided beyond scope.**
  - The feeder re-adds missing test providers, and logs each re-add.
  - The network test provider follows the gps one, so the map camera stays on the test area.
