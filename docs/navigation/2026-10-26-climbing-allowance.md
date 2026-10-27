# The climbing allowance in the walk-back time

Dispatch 2026-10-26-19 Amendment 1 (RECORD -1687, -1689). Built by Hazel Cairnwright on pre-main c445574da (after the climb fix #420).

## What it does

The walk-back time behind the sundown line's "start back by", the heads-up and the leave-by alert is the pace-based time plus **2.2 s for every metre climbed and every metre descended on the way back** (`domain/ClimbAllowance.kt`). Back by and the HUD's distance home do not change.

## Which climb is counted

- **Before Return, and after Return when the way home is the walker's track:** the climb along the same self-joined route home the distance is measured on, from the stored points' barometric heights (`domain/PathHome.kt`). The hop to the walker's fix and the leg to the origin waypoint have no heights and count flat.
- **After Return on a way home along trails:** the two track parts carry their own stored heights (kept through `FindWayHomeUseCase`'s track parts). **The trail part's climb is a stand-in:** the walker's own track between the same two trail points, laid along the trail part in proportion to length (`domain/wayhome/WayHomeHeights.kt`; RECORD -1689, C1: "(b) Your track as a stand-in (Recommended)"). It has the same net rise, and can read short where the trail crosses a rise the walker's track went round. It stays a stand-in until the trail routes carry elevation.
- **Without a barometer** (or a track under half covered by pressure) there is no allowance, logged once per recording (C3: "No allowance there (Recommended)").

The heights are the ones the journal's Climb reads, banked through the same 4 m hysteresis. From the walker's place on the way home, the climb is counted from the last height at or before the distance's own node, so it never starts later than the distance does.

## Known and accepted

- The pace is measured over the walk so far, hills included, so on a way back that retraces a hilly way out part of the climb is counted twice. It errs long (C2: "Add it as ruled (Recommended)").
- 2.2 s per metre descended is near the bottom of the 10-24 walk's fitted interval for descents (3.19 s, 90 % 2.15 to 4.20); the owner chose one figure for both.

## For the elevation work (not built)

When the route files carry elevation, the routing adapter has to keep the engine's per-point elevation (`routing/BRouterTrailRouter.kt`, where it maps the engine's nodes to positions only) and `TrailRoute` needs a field for it (`domain/routing/TrailRouter.kt`). The trail part's `PartHeights` then comes from those heights instead of the stand-in; nothing else in the allowance changes.
