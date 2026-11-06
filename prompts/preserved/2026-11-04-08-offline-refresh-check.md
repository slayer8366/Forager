# Dispatch 2026-11-04-08: Does "Download again" bring an older offline area's map up to date? A read-only check (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2104 (intent). It follows S22 session 13 (RECORD -2103) and dispatch 2026-11-04-04 (A1, B3). Every fact below was read at pre-main 949ad8183 and records-after-173 cc2691b68 while this was written, and is a premise to re-check, with file and line.

## Authorisation
**Not given yet.** The owner's words were "Go ahead.", to the planner's "Shall I write that dispatch?". That is a go to write it, not to start a coder. Hazel starts only on the owner's yes to "Start Hazel on dispatch 2026-11-04-08 now?".

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change
**Nothing changes.** This is a read-only check, answered by a report.

**Why it's asked.** In session 13 the S22 had a 9.9 m fix. Navigate to Waypoint 1 went by trail and logged: "the waypoint's end: tile [a zoom-15 tile; its number removed, -2174] has no barrier mark: it was built before fences were carried". That is A1 of dispatch 2026-11-04-04, working as ruled.

The live server's copy of that tile ([a zoom-15 tile; its number removed, -2174]) carries a `barriers` layer with 2 features. But a new 1 mi test area downloaded on the phone ("Test s13", MapLibre region 12, 35 tiles) kept all 17 of its basemap tiles at their old modified dates (2026-10-27 to 2026-11-03). That tile stayed at 2026-10-28 with no barrier mark. The test area was then deleted.

**The planner's inference, not confirmed:** an area downloaded before the fence data may never get it from "Download again", so the street drop-off would never appear there.

## Scope boundary
- **Read only:**
  - pre-main 949ad8183's code;
  - MapLibre Android 13.5.0 (libs.versions.toml:43), from the cached library or its published source at that tag, quoted with where it was read;
  - session 13's evidence in `~/s22-2026-11-04/s13/` (logs, and the snapshots' copies of `files/mbgl-offline.db` under `snap/*/x/`).
- **Must not:** touch the phone; write code; create a branch, commit or push; run Gradle; edit RECORD.md or the audit index. Scratch goes in `/mnt/work/scratch/offline-refresh-check/`, never `/tmp`, and is deleted at the end.
- Never read, name or quote the Restricted Object. Our own map is the "Trail Map".

## Baseline
pre-main 949ad8183; records-after-173 cc2691b68. The "Older map style" notice is `OLDER_MAP_STYLE_NOTICE` (map/TrailStyle.kt:120), and its rule is described at domain/OfflineMapRepository.kt:265 (RECORD -1294).

## Verify first: the questions (report, then stop)
1. **What "Download again to update" does,** from the notice to MapLibre, with file and line:
   - what decides the notice;
   - what the user does to download again, and what the app then calls;
   - whether anything deletes, invalidates or resets stored tiles first (for example `OfflineRegion.invalidate`, `OfflineManager.invalidateAmbientCache`, `clearAmbientCache`, `resetDatabase`, or deleting the region).
2. **What MapLibre 13.5.0 does when an offline download meets a tile it already stores:**
   - reuse it, revalidate it (by expiry, ETag or Last-Modified), or fetch it again;
   - quote the code or documentation at that version, and say what the server's `cache-control: public, max-age=86400` means for that.
3. **What deleting a region does to tiles another region still holds.** The S22 has two areas, Region 1 (MapLibre region 3, 34 tiles) and Region 2 (region 8, 1,630 tiles), which may share tiles.
4. **The map seen online:**
   - When the Trail Map is viewed online over a stored tile past its expiry, does MapLibre refresh the stored copy?
   - Would `ReadNearbyMapData` (domain/mapdata/NearbyMapData.kt) then read the refreshed one? Say which URL template and tile JSON it reads.
   - The session 13 copy of [a zoom-15 tile; its number removed, -2174] shows modified 2026-10-28 and accessed 2026-11-03: say what that implies, if anything.
5. **So, for someone whose areas were downloaded before the fence data:** which actions, if any, bring the fence mark to their phone today. Mark each as confirmed from code or library, or inferred.
6. **If none do:** the options for the owner, priced by what each asks the user to notice, decide or do ("Don't make me think", CLAUDE.md), with what each changes in the code. Don't choose; nothing is built.

## Predictions
- **Outcome (planner):**
  - MapLibre reuses a stored tile during an offline download unless it has been invalidated.
  - "Download again" deletes the region and downloads it, but tiles still held by another stored region stay old.
  - So neither of the S22's two areas can be refreshed by the app's own actions today, and the drop-off can't appear in them.
- **Mechanism (coder):** in your report, before anything else.

## Evidence required
- File and line for every claim about the app.
- For MapLibre: the source file and line at 13.5.0, or the bytecode, and say which.
- Each claim marked **read**, **observed** (from session 13's files) or **inferred**.
- Any query you run on the snapshot databases, with its output.

## Stops
Stop and report on:
- anything that needs the phone;
- a wrong premise;
- an owner-level choice;
- a question you can only answer by building or running code.

## Finish line
Your report by message, which the planner files unedited as `prompts/preserved/2026-11-04-08-report.md`. Nothing else.

## Final message
Your report, with four sections at the end:
- confirmed against inferred;
- could not determine;
- premises that were wrong;
- anything decided beyond scope.
