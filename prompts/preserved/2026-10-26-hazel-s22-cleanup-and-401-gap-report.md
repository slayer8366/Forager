# Hazel: S22 cleanup, and PR #401's S22 run, 2026-10-26

Hazel (app coder, Opus). Work for Rowan's two items after the account switch (RECORD -1639, the owner: "Clean up now (Recommended)", "Both, as queued (Recommended)"). This file does not record any positions, place names or screenshots. The screenshots and logs are on the laptop under `~/.cache/hazel-s22/`.

## 1. S22 cleanup

**What was on the phone (observed 02:19 UTC, before I changed anything):**
- Build 1.0.3958+g1cda6c46, debuggable. Room schema `user_version` 22. Stay-awake 7. Units Imperial.
- `TrackRecordingService` was in the foreground, created 13 h 41 min earlier. Track `c4c04ee6…` had started 2026-10-25 12:38:29 UTC with no end, 884 points, and its Start waypoint. The way home was showing (the HUD read "by trail" and showed Stop navigating).
- **Test providers: already gone.** The location log shows "gps/network provider removed mock provider override" at 17:06:37 UTC, 3 s after the drop. It looks like they went when adb lost the device. So the track's points after 17:06 are the phone's real fixes. Nothing to remove; I confirmed it in `dumpsys location`.
- Night Maps off; basemap key `street` (Trail, `MapMode.kt:44`).
- **Not from the test, left alone:** offline areas Region 1 (id 2, downloaded 2026-10-22, Area 1) and Region 2 (id 8, 2026-10-24 15:48 UTC); two tracks from 2026-10-24 (16:55 to 19:20 and 19:27 to 21:43 UTC) with their Start/End waypoints; "Waypoint 1" (2026-10-23). I don't know whose the 10-24 tracks are, so they are listed here for the owner or Rowan.

**What I removed, all through the app:** Stop recording (which also ended navigation), then Journal → Records → Tracks → that track → Delete. Deleting the track left its two waypoints (Start 5:38 AM, End 7:21 PM, both Oct 25) with no track, so I swiped each away in the Records list. Afterwards the database read 2 tracks and 5 waypoints, both regions unchanged, schema 22. Stay-awake 0. I confirmed Night Maps off and Trail.

**One slip, disclosed.** My first tap on Records landed on a Facebook heads-up notification and opened its login screen. I typed nothing, backed out and went home.

## 2. PR #401

- **Evidence comment (Sorrel's 401-1):** https://github.com/slayer8366/Forager/pull/401#issuecomment-6406850098. It gives the fail-first, revert and green runs, each with 0 compile errors and its failures read from the XML. It also states that the first 32-switch batch is not evidence: its log has 0 "skipped" lines and 32 style loads.
- **S22 run:** https://github.com/slayer8366/Forager/pull/401#issuecomment-6407532027. It has the full table; the gist is below.

The build was this PR's CI APK, byte-identical to run 40192329788's artifact. Pid 27408 the whole time, logcat captured 02:28:12 to 03:24:29 UTC from a cleared buffer.

| 2.6 item | Result | Counts from the log |
|---|---|---|
| 1. Guard fired while returning | **Met, only under rapid switching.** Paced changes never hit the gap. | Paced: 44 changes, 31 loads, 0 skips. Rapid (Layers sheet open, Topo/OSM taps about 70 ms apart, offline style): 240 taps, 162 loads, 2 clusters in which all seven style-keyed readers skipped, including 2 route and 2 overlay skips |
| 2. Catch-up | **Met.** The route and breadcrumbs are drawn after the skip at 02:41:30.583 | screenshots before and after (on the laptop) |
| 3. Remembered locate | **Two exact triples, with caveats.** Both came from the app's own no-signal switch, not a user's basemap or Night Maps tap; both came after the way home had ended (02:44:41), with the recording still on; following afterwards was not shown | 72 timed taps: 2 hits, each exactly 1 skipped / 1 remembered / 1 carried out; 0 of 36 while returning |
| 4. No crash | **Met** | 0 FATAL, 0 "Calling … newer style", 0 `E AndroidRuntime` |

**Why the paced gap was never hit.** A style change took 50 to 180 ms from the change to "activated on a style load", and input events were mostly handled only after the load. So a tap through the sheet or drawer, which covers the locate button, cannot land inside the change.

**Phone state changed for the run, and restored:** stay-awake 7 then 0; gps and network test providers added then removed; the shell's mock-location appop allowed then set back to `default` (its record showed a reject 5 days earlier, so it was not allowed before); wifi off and on 37 times, left on; overlay switches toggled in pairs. That last one leaves `offline-region-circles-layer.visible = true` stored explicitly, where before the key was absent; the default is the same value (`MapLayerState.kt:14`). Basemap Trail, Night Maps off, the run's test track and its two waypoints deleted through the app. Final database: as after item 1.

## Unverified, and for the owner

- **Unverified:** that a remembered locate re-centres a map that was not following. That needs a hit after a pan: 0 in 27 such tries.
- **Unverified:** a remembered locate from a user's own basemap or Night Maps tap. The way the controls are laid out appears to make that window unreachable by hand.
- **For the owner or Rowan:** does #401's S22 condition count as met, given caveats 1 to 3 above? Sorrel's 2.6 asked for a locate "within the same second as a basemap or Night Maps tap".
- **For the owner:** the two 2026-10-24 tracks, and Region 2. Both are on the phone, neither is from this test, and I don't know who made them.
