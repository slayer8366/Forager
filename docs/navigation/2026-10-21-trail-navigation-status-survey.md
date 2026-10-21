# Trail navigation: status survey, 2026-10-21

Read-only survey by Juniper Sounding (researcher, Fable), filed by the planner (Rowan Waymark). The owner asked, "Is trail navigation wired into the app yet? We should work on that next", and chose a status survey before any plan (RECORD -1494). The survey's full text was delivered to the planner by message; this file records its findings in summary, with the survey's own citations. Commits read: pre-main 663973b9, main 02a544cd, records-after-173 c00a32cb, pi-build 546a91ec, street-cloudflare 6abba53e, engine-spike 5d34f4f1.

## Short answer
Not wired. Nothing on pre-main routes along a mapped network: no BRouter code and no routing dependency. What exists:
- the way back along the walked track;
- a straight dashed line to a chosen waypoint;
- a camera "Return to Route" pill (T22);
- the walked-path off-track rule (T21).

The data side is further along than the plan's status line says. The Pi pipeline already builds BRouter routing files (185 `.rd5` squares for US and Canada) beside the tiles, but only the tiles are uploaded. The Worker has no route for the routing files.

## The nine stages
| Stage | Status | Evidence |
|---|---|---|
| 1 Engine spike | Done | `docs/navigation/2026-10-03-engine-spike-report.md`; BRouter chosen at RECORD **-484** (-1494 wrongly cited -477) |
| 2 Own-tiles survey | Done | `docs/plans/2026-10-03-own-tiles-survey.md`; -466, with item 1 superseded by -879 |
| 3 Spec | Done, ruled | `docs/plans/2026-10-04-own-map-service-spec.md` (-492, -493); its reconciliation with Navigator stages G-K was not written |
| 4 Pi pipeline | Built and run; publishes tiles only | pi-build `map-build/`; the US+Canada `us.pmtiles` is live on R2 (2026-10-21 06:04Z, per pi-build upload-prep); `.rd5` not uploaded; no elevation in the routing files |
| 5 Own basemap in the app | Built behind a switch, unmerged | PR #258, `STREET_SERVED_BY_OWN_MAP = false`; trail styling at cfbd1b68; owner's phone review pending; needs a re-merge with pre-main |
| 6 Routing along the network | Not started | no code |
| 7 Off the trail and back | Not started | `domain/OffTrackJudge.kt` is still T21's walked-path rule |
| 8 Reference layer (FS, BLM) | Not started | -1176 |
| 9 Owner's walks | Not started | the -451 Return record exists to read them by |

The plan file's status line ("Nothing is built … the engine spike, -446") dates from 2026-10-03 and was never updated.

## Navigator plan (A-K)
- **A to F:** built, apart from T20 (offline readiness), which waits for own tiles (-765).
- **G to K:** not started.
- **How the T-tasks map to the trail plan** (inferred):
  - T7 is replaced by stage 6 (T7 shipped first on the walked track, -492);
  - T8 and T9 gain a routed line in stage 6, and their step path needs re-confirming (steps 3 and 7 in `domain/WaypointNavigation.kt`);
  - T21 is replaced by stage 7 (-449);
  - T20 follows stages 5 and 6.
- **Overlaps with -880:** stage H (terrain) overlaps its terrain shading, and stage I (land context) overlaps trail stage 8 and its land-ownership layer.

## BRouter, from the spike report
- **App size:** 0.31 MB of pure Java, MIT licence, no published artifact (it would be vendored or built from v1.7.10 source).
- **Data:** a 60 × 60 km region is 0.64 MB; OR+WA whole squares are 154.6 MB.
- **Performance (S22):** median 525-605 ms for a 10 km trail route; about a third of Valhalla's memory.
- **Trail preference:** 100% on the named trail.
- **What it doesn't do:** it has no matcher (a wrong-turn alert needs Forager's own matching, -492), and it leaves the gap from an off-trail start to the app.
- **Elevation:** none without SRTM data, so climbing costs do nothing; the Pi builds carry no elevation.
- **Per-region packing:** measured as a few MB per region, but not built. The pipeline emits 5-degree squares.

## Where network routing would plug in (inferred)
1. **The way home.** Route production is one injected function, `findRouteHome` (`ui/track/TrackRecordingViewModel.kt:213`), run each tick by `TrackRecordingRouteTicks`. A network route can draw through the existing `RouteLine` and `RouteHomeLayers`. But `RouteHome.Ahead` carries a walked-track `PathHome`, and the walk-back time (T13) reads that distance.
2. **The waypoint line** is produced in the composable (`AvailabilityScreen.kt:693-703`). A routed waypoint line needs a ViewModel home (-1450) and must work without a recording.
3. **"Off route" is decided twice today:** `OffTrackJudge` in the service, and `RouteWithheldReason.OFF_ROUTE` in the route tick. Stage 7's matcher would be a third.
4. **New map treatments go in new `ui/map/Map*.kt` files** (modular-code skill).

## Files over about 800 lines that trail navigation would touch (split first, per the skill)
- `ui/track/TrackRecordingViewModel.kt` 1,029
- `MainActivity.kt` 806
- `ui/availability/AvailabilityScreen.kt` 1,324
- `ui/availability/AvailabilityCompactScaffold.kt` 1,435
- `ui/availability/AvailabilityViewModel.kt` 976

## Open questions the evidence raises
The plan's three open items are answered or partly answered: T7 shipped first (-492); the region ceiling is "Keep today's limit" (-493); coverage is US and Canada on the Pi, with nothing ruled beyond. New questions:
1. How routing data reaches the phone: whole squares, per-region cuts, or a pre-cut grid. Spec R5 asks for one action that stores map and routes.
2. Flat routing for now, or add public-domain elevation (3DEP)?
3. What Forager's own matching runs against: the tiles' `roads` layer (readable, now with trail keys) versus `.rd5` (not readable outside the engine).
4. The way back as own track to the trail, then trail to start (-445). `RouteHome` has no shape for this hybrid.
5. Which distance feeds the walk-back time and sundown alert once a network route exists.
6. "Return to Route" already names the camera pill (T22). Stage 7's wording must not collide with it.
7. Re-confirming the waypoint step path (steps 3 and 7).
8. Whether stages H and I fold into the trail plan or stay with the Navigator plan.
9. BRouter vendoring, version pinning, and `.rd5` compatibility across versions.
10. Refresh: map and routes of one snapshot for a region downloaded earlier.
11. Sequencing against #258, which edits `MapStyleReload.kt`, `NightColour.kt` and `BasemapStyles.kt` (one coder per file).

## Could not determine
- Sorrel's final verdict on #258's F1 update (the planner holds it: approved, RECORD -1482/-1483).
- Whether the Street review happened on the owner's phone.
- Whether #258 merges cleanly now.
- Whether the Pi's weekly timer is enabled.
- `.rd5` compatibility across BRouter versions.
- Whether the tiles' road features carry names a wrong-turn rule could use.
