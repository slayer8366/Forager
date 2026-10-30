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

## Amendment 1 (2026-10-30): answers to the first stop, and the go (RECORD -1858)
The first stop is docs/audits/2026-10-30-compass-screen-off-first-stop.md (compass-screen-off, be03807ff). It is accepted as proposed: repeatOnLifecycle(STARTED) inside rememberTrueHeading, covering both production callers, and a fresh smoother on each start. Its corrected premises are accepted: SightingsMap.kt:281's own heading, and the HUD, card and facing-judge readers.

**The owner's answers:**
- **Screen back on:** "Same rule as tab returns (Recommended)". This is option C, -761's rule: keep the last heading if the screen was off under 10 s, otherwise "—" until the first reading.
- **The trust hold:** "Yes, re-check (Recommended)". Restart the trust hold after the screen comes back, through a new function on CompassTrustJudge, so a compass that was calibrating is not shown as trusted on its first reading.

**The go: build.**
- Tests fail first, then revert checks per rule. Build logs must have no compile errors, and restores come from saved copies.
- CI only. A PR into pre-main, with no merge.
- The device check comes later, on the planner's word: confirm the screen-off redraws stop while recording with the app in front, and re-measure the CPU against the 10-30 figure.

## Amendment 2 (2026-10-30): Sorrel's review of #450 (RECORD -1870)
Sorrel's review (Fable) is docs/reviews/2026-10-30-sorrel-pr450.md (sorrel-pr450, 745a5797a). It has no S findings. The owner chose all three:
- **N-1:** a test that moves the activity to STARTED, not RESUMED (a dialog or split-screen), and asserts the compass listener stays registered and the heading keeps updating. Then a revert check: STARTED → RESUMED must fail it.
- **N-2:** push the completion report under docs/audits/, with the six revert run ids from RECORD -1862, and point the PR body at it.
- **N-4, pre-existing:** the trust judge is rebuilt on every hasFix flip (TrueHeading.kt:119-120), so a calibrating compass is trusted at once after GPS is lost and regained. Apply the owner's re-check there too: a fix flip must not clear an untrusted state without the hold. Fail first, then a revert check. If it needs more than a small change, stop and report.
- Merge pre-main in if it has moved. Get CI green. No merge.

After this, the planner dispatches a combined S22 build (#447 + #440 + #450 + tools, on CI) for Sorrel's nine phone checks.
