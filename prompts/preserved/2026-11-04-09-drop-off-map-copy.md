# Dispatch 2026-11-04-09: Older offline areas get the street drop-off's map data automatically (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2108 (intent), with the owner's choice in -2107. It builds on dispatch 2026-11-04-08 and its report (prompts/preserved/2026-11-04-08-report.md, RECORD -2106), on dispatch 2026-11-04-04 (the way-to street drop-off series, A1), and on S22 session 13 (-2103). Every fact below was read at pre-main 949ad8183 and records-after-173 dea53a561 while this was written, and is a premise to re-check, with file and line.

## Authorisation
**Not given yet.** The owner chose the design and the timing, which is not a go. Hazel starts only on the owner's yes to "Start Hazel on dispatch 2026-11-04-09 now?".

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules for every item
- Verify first: Phase 1 reads and reports by message, then stops. Phase 2 builds, on the planner's go.
- Tests first, shown failing, then revert checks on throwaway CI branches. Full CI green on the last real commit. CI only: no Gradle on the laptop.
- Don't touch the phone. Never read, name or quote the Restricted Object. Our own map is the "Trail Map".
- New words a person would see go back to the planner before they're built.
- Stop and report, rather than choosing, on anything the owner would see that this doesn't rule.
- Keep files small and decoupled (the modular-code skill): new capability in new files.

## Change: what the user sees

| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Tests and evidence |
| **C** | Branch, build and report |

- **A1** (2026-11-04, -2107). **The owner's words,** to "How should older offline areas get the fence data the street drop-off needs?": **"Automatic (Recommended)"**. The option read: "The app keeps its own copy of each area's close-zoom map data for the drop-off, and refreshes it by itself whenever there's signal, the way it already fetches trail routes. Old areas gain the drop-off with nothing for the user to do. Costs: some extra storage, and a bigger build." To "Does the fix go in this release, or a follow-up update?": **"This release"**. That option read: "The release waits for it: dispatch, build, tests, and a phone check."
- **A2. What the user sees afterwards.**
  - With signal, an offline area downloaded before the fence data (before about 2026-11-03, report section 4) gains the drop-off's map data by itself, with nothing to tap.
  - After that, Navigate to a waypoint in that area can choose a street drop-off or pick-up as dispatch 2026-11-04-04 rules, instead of always going by trail.
  - The map the user sees is unchanged. MapLibre's own store, and what it draws, are not touched.
- **A3. What stays:**
  - Where the app's copy doesn't hold a tile, or holds one with no barrier mark, the waypoint's end goes by trail and is logged, exactly as dispatch 2026-11-04-04 A1 rules. Nothing goes dark.
  - No new setting, prompt or button. Any new words (a status line, for example) go to the planner first.
- **A4. The "Older map style. Download again to update." notice** (`OLDER_MAP_STYLE_NOTICE`, map/TrailStyle.kt:120) is not changed by this dispatch. Report what it means once the drop-off's data refreshes by itself, and anything about it the owner should rule on (report section 6, option A). Its words are the owner's.

## Scope boundary
- **May touch:**
  - a new branch from pre-main;
  - new files for the app's own copy (a new `OfflineBasemap` implementation behind the existing interface, domain/mapdata/NearbyMapData.kt:15-21, and its fetch and store);
  - the wiring that picks which `OfflineBasemap` the way-to reads (AppContainer);
  - the existing route-fetch trigger, so the copy rides it (`onOfflineMapsOpened`, ui/availability/AvailabilityViewModelOfflineMaps.kt:58, and "Try again");
  - deleting an area, so its copy goes with it;
  - tests;
  - your completion report.
- **Must not touch:** MapLibre's store or its drawing (no invalidate, clear or reset calls); the Worker and anything on the server (stop if the design needs it); Theme.kt and Color.kt; pre-main; main; RECORD.md; the audit index; the phone.

## Baseline
pre-main 949ad8183. ForagerDatabase is at version 23 (data/local/ForagerDatabase.kt:214). The S22's two areas hold 9 and 600 zoom-15 basemap tiles (report section 5).

## Verify first (Phase 1: report these, then stop)
1. **Re-check the report's premises you build on,** with file and line. Mark which you re-read and which you take from the report:
   - the reader reads MapLibre's store by the us.json template (`MapLibreOfflineBasemap.kt:37-50`, `StoredTiles.kt:59-68`);
   - the fence mark is decided per tile (`NearbyMapData.kt:127-131`);
   - the route fetch rides opening Offline maps with signal.
2. **Where and how the copy is stored.** Files beside the route data, Room, or something else. Say why, against CLAUDE.md's "Room for data that relates; DataStore for flat settings". **A new Room version is a globally unique value: stop and report the number before using it.**
3. **What is fetched:**
   - which zoom-15 tiles: the area's, or only those the drop-off's reach can need;
   - the count and storage for the S22's two areas, worked out;
   - the request rate. CLAUDE.md, Architecture: an API's capacity is not a safe operating limit, so propose an explicit one. Also how it sits with the tile budget (6000) and the server's per-phone limits, if any (known bug 17).
4. **The freshness rule.** The Worker sends only `Cache-Control: public, max-age=86400`, with no ETag or Last-Modified (report section 2). How does the app know its copy is out of date: by age, by the mark's absence, or another way? What happens with no signal, a failed fetch, and a partial fetch? None are silent.
5. **The read order.** Does the way-to read the app's copy first, then MapLibre's store? What does it do where they disagree? Show that A3's fallback is unchanged where neither copy holds the mark.
6. **Deleting an area, and restoring one from a backup:** what happens to its copy.
7. **A4, the notice:** what it means after this.
8. **Your mechanism prediction, your test plan and your file list,** with each file's size.
9. **Anything the owner would see that this doesn't rule.**

## Predictions
- **Outcome (planner):**
  - After this, opening Offline maps with signal on the S22 fetches the zoom-15 basemap tiles of Region 1 and Region 2 into the app's own copy, within your operating limit.
  - All of those copied tiles carry the `barriers` layer, since the server has carried it since about 2026-11-03 (report section 4).
  - Navigate to Waypoint 1 then no longer logs "has no barrier mark" for tile [a zoom-15 tile; its number removed, -2174].
  - MapLibre's store keeps its old tiles, and the map looks the same.
- **Mechanism (coder):** in your Phase 1 report.

## Evidence required
- **Tests first,** each shown failing for its reason. The tests go through the real entry points: opening Offline maps with signal; the way-to's read.
- **The fallback:** a test that a tile missing from the copy, or a copy without the mark, still goes by trail with its log line.
- **Revert checks** under CLAUDE.md's runner rules: compile log first; only failures that edit could cause; restore from a saved copy; the forward change present afterwards.
- **CI counts** from the JUnit XML.
- **Device-only items,** listed separately as a device check, for the planner to run on the S22.

## Stops
Stop and report on:
- a new Room version number;
- any server or Worker change;
- new words a person would see;
- a change to MapLibre's store;
- an owner-level choice;
- a wrong premise;
- a test having to be weakened;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main from a new branch, green on CI at its last real commit, merged only on the owner's word. Then a phone check on the S22, run by the planner.

## Final message
Your completion report on the branch at `docs/audits/2026-11-04-09-drop-off-map-copy-completion-report.md`. It gives:
- commits and the PR;
- the failing-first evidence;
- each revert check and the failure it named;
- the CI counts;
- the storage and request figures;
- what is untested;
- every call beyond the dispatch;
- the device check's steps.

The planner files your Phase 1 report and the summary beside this dispatch, unedited.
