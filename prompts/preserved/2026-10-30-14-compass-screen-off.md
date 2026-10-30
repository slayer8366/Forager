# Dispatch 2026-10-30-14: stop the compass while the screen is off or the app is hidden (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1857. Cut from **pre-main b493cffeb**; verify that is still the remote head.

**Authorisation.** The owner, verbatim: "Yes, dispatch a fix (Recommended)", whose option read: "Stop reading the compass while the screen is off or the app is hidden, and pick it up again on return. It starts with a look-only first stop, and must not change how the compass behaves on screen."

**Read first:** docs/audits/2026-10-30-s22-ci-build-and-redraws.md on origin/s22-ci-build-redraws (8527a76fd), Part 2, and RECORD -1856.

## What exists (re-check each line)
- `rememberTrueHeading` collects the compass inside `produceState` (ui/map/TrueHeading.kt:97-100). It is called from AvailabilityCompactMapUi.kt:417.
- The compass listener is registered at SENSOR_DELAY_UI (AndroidCompassProvider.kt:151), 15 Hz.
- The headings are read by the strip (CompassElevationStrip.kt:126) and the map's location-dot compass engine (SightingsMap.kt:283-287).
- Nothing stops the collection when the activity stops. With the screen off, each heading asks for a frame on a surface that isn't there: about 11% of a core while recording with the app in front.

## Stop first (look only, no code)
Write docs/audits/2026-10-30-compass-screen-off-first-stop.md covering:
- (a) every compass consumer, and whether any must keep running with the screen off. In particular, does navigation, the off-track alert, the walk-back or any service read the heading in the background? A background reader must keep working.
- (b) the proposed mechanism, such as lifecycle-aware collection (collect only while STARTED), and where it goes;
- (c) what the first heading after the screen comes back looks like: no stale arrow, no jump the user would read as broken;
- (d) tests through real entry points (the lifecycle moving to STOPPED and back), plus how a Robolectric test can prove collection stops;
- (e) files over 800 lines touched.
Push it and wait for the planner's amendment.

## Rules
- Branch `compass-screen-off`, worktree ~/.cache/forager-wt/compass-screen-off.
- CI only, no Gradle on the laptop, no adb.
- Scratch goes in /mnt/work/scratch/compass-screen-off/.
- gh is logged in; run gh commands bare.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- Ambiguity: stop and report.
