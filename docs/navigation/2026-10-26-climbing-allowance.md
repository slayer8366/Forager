# The climbing allowance in the walk-back time

Dispatch 2026-10-26-19 Amendment 1 (RECORD -1687, -1689). Built by Hazel Cairnwright on pre-main c445574da (after the climb fix #420).

## What it does

The walk-back time behind the sundown line's "start back by", the heads-up and the leave-by alert is the pace-based time plus **2.2 s for every metre climbed and every metre descended on the way back** (`domain/ClimbAllowance.kt`). Back by and the HUD's distance home do not change.

## Which climb is counted

- **Before Return, and after Return when the way home is the walker's track:** the climb along the same self-joined route home the distance is measured on, from the stored points' barometric heights (`domain/PathHome.kt`). The hop to the walker's fix and the leg to the origin waypoint have no heights and count flat.
- **After Return on a way home along trails:** the two track parts carry their own stored heights (kept through `FindWayHomeUseCase`'s track parts). **The trail part's climb is a stand-in:** the walker's own track between the same two trail points, laid along the trail part in proportion to length (`domain/wayhome/WayHomeHeights.kt`; RECORD -1689, C1: "(b) Your track as a stand-in (Recommended)"). It has the same net rise, and can read short where the trail crosses a rise the walker's track went round. Since dispatch 2026-10-30-06 (RECORD -1836, -1838) the stand-in is used only where the trail data lacks a height at some route point; see "Trail-data heights" below.
- **Without a barometer** (or a track under half covered by pressure) there is no allowance, logged once per recording (C3: "No allowance there (Recommended)").

The heights are the ones the journal's Climb reads, banked through the same 4 m hysteresis. From the walker's place on the way home, the climb is counted from the last height at or before the distance's own node, so it never starts later than the distance does.

## Known and accepted

- The pace is measured over the walk so far, hills included, so on a way back that retraces a hilly way out part of the climb is counted twice. It errs long (C2: "Add it as ruled (Recommended)").
- 2.2 s per metre descended is near the bottom of the 10-24 walk's fitted interval for descents (3.19 s, 90 % 2.15 to 4.20); the owner chose one figure for both.

## Trail-data heights (built, dispatch 2026-10-30-06)

This section used to say what the elevation work would need: the routing adapter keeping the engine's per-point elevation and `TrailRoute` a field for it, with the trail part's heights then taken from those instead of the stand-in. The route squares carry 3″ heights (three arc-seconds, posts about 90 m apart) since the route build of 2026-10-28 (RECORD -1835), and that work is now built (RECORD -1836, -1838):

- The adapter keeps each route point's height, `null` where the data has none, never 0 (`routing/BRouterTrailRouter.kt`, `heightMetresOf`; `TrailRoute.heightsMetres`).
- Where every route point has a height, the trail part's heights are the trail data's (`PartHeights.TrailData`, `domain/wayhome/WayHomeHeights.kt`). One sample with no height sits between each straight joint and the route's heights, so the offset between the barometer's heights and the terrain model's is never counted as climb (the owner: "Don't count the step (Recommended)"). The climb inside each joint is counted from the walker's own barometric heights over the joint's length (RECORD -1842, the owner: "Count the joint climb").
- Where some or all route points have none (data downloaded before 2026-10-28, or one of the five squares built without heights), the stand-in as before, logged once per call with the counts (the owner: "Use your track instead (Recommended)").
- Without a barometer there is still no allowance, the trail part included (the owner: "Keep it off (Recommended)").
- The banking and the 2.2 s figure are unchanged. The route engine still ignores heights when choosing a route (`consider_elevation` stays off).
