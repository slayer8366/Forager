# Review: PR #513, test-latitudes-out (head e545c8dba, code 7e8c06253, into pre-main 26fddf005)

Sorrel Tally, 2026-11-09. Read-only; nothing changed, nothing pushed. No flagged value or band is written here.

## Verdict

Approve. Tests only, scanner reads 0 on both diffs, every touched test still claims what it claimed, by-name CI comparison 0 changed, nothing silenced.

## What was checked, and how

1. Scanner (`python3 -I ~/.forager-private/scan.py stdin-diff`, run only, nothing under its directory opened):
   - `git diff origin/main origin/test-latitudes-out`: TOTAL 0 in every category.
   - `git diff origin/pre-main origin/test-latitudes-out`: TOTAL 0.
   - Control: `git diff origin/main origin/pre-main` reads 8 in the lat category (the scanner does bite on the baseline, so the two zeros are not a scanner that sees nothing).
2. Files changed (`git diff --stat origin/pre-main...origin/test-latitudes-out`): seven files under `app/src/test/java/...` and `docs/audits/2026-11-09-test-latitudes-out.md`. No production source, no workflow, no script, no CI allowlist.
3. Each test's claim, by reading the diff and the uses of the moved values on the branch:
   - `domain/GetMapRecordsUseCaseTest.kt:107` — two points of the recording track moved together; assertions are on which ids reach Tracks, no position read. Holds.
   - `domain/route/RouteLegElevationTest.kt:25-27` — `a`, `b`, `c` moved together; assertions are on elevation figures from the heights list; the leg length from `GeoDistance.metersBetween` is passed but never asserted. Holds.
   - `ui/map/PlottedRouteDrawingTest.kt:20-23, 57, 61` — every point and both edges of the expected box moved by the same step; box size and point order unchanged. Holds.
   - `ui/availability/RouteCardExpandShortLandscapeTest.kt:38` — KDoc only; a dp top-edge figure the scanner read as a latitude, rewritten as a whole number and a third of a dp. Same figure to the places quoted; no code. Holds.
   - `ui/availability/StraightLineToTargetTest.kt:24` — the second waypoint is used once (line 92-93), as the target of a line that must not survive a change of target; its position is not read. Holds.
   - `ui/availability/AvailabilityScreenWaypointNavigateTest.kt:116` — the second waypoint is "another waypoint in the list" (lines 152, 154, 682) and "the one left after deletion" (line 640); no assertion reads its position or distance. The map is a stub slot with only the creek pin's glyph (line 177). Holds.
   - `ui/availability/TripNavigateTest.kt:97` — the waypoint is now about 110 km from the trip (the shared made-up place). Its assertions are by id (lines 360-366, 384) and `straight.last()` equal to the waypoint's own LatLng (366). `straightLineToTarget` (`app/src/main/.../ui/availability/NavigationArrival.kt:53`) only returns null inside the arrival radius (StraightLineToTargetTest lines 47-48 fix the boundary at tens of metres), so a far waypoint still gets its line. Holds.
   - Does any test or production path read that ~110 km distance? `git grep` of `app/src/main` on the branch for a distance cap (`maxDistance`, `MAX_*DISTANCE|METERS`, "too far", "farther than") found caps only in barometric height, live-fix accuracy, drop-off walking, way-home detour and the trip download area — none on a waypoint navigation target. I did not trace every production path the two availability-screen tests compose; that part rests on CI (the 23 + 12 cases of those classes pass), as Hazel's report also says.
4. By-name JUnit comparison, PR run 42661633867 (head 7e8c06253, success) vs pre-main run 42654949241 (head f485f0d8d, success). The only pre-main commit after f485f0d8d is 26fddf005, `docs/process/known-bugs.md` only. Artifacts `unit-test-report` fetched via `gh api .../artifacts/<id>/zip` into separate untrusted dirs under `/mnt/work/scratch/sorrel-513` (mode 700); script in a third dir, run with `python3 -I`. Both zips hold 1125 XML files.
   - 7,531 cases in each; 7,507 passed, 24 skipped in each; the skipped sets are identical.
   - 0 only-in-PR, 0 only-in-base, 0 changed outcome.
   - Touched classes in the PR run: GetMapRecordsUseCaseTest 8, RouteLegElevationTest 5, AvailabilityScreenWaypointNavigateTest 23, RouteCardExpandShortLandscapeTest 1 (+ its two subclasses AfterSunset and Evening, present), StraightLineToTargetTest 12, TripNavigateTest 12, PlottedRouteDrawingTest 6 — all passed. With the two subclasses this is Hazel's 69.
   - The zip entry counts differ by one (3436 vs 3437): only HTML report pages whose long names Gradle truncates with a per-run hash. XML counts are equal; no bearing on outcomes.
5. Nothing silenced: the diff has no `@Ignore`, `Assume`, skip or allowlist line; the skipped set is unchanged at 24.

## Defects

None.

## Owner's calls

1. Hazel's "Decided beyond scope": in the three waypoint files only the flagged waypoint moved, so in each it now sits about 110 km from the shared made-up place instead of a few km. Nothing reads that distance today (above), so the tests hold; but a future assertion on "nearest waypoint", a fit-to-all-waypoints frame, or a HUD distance to that waypoint would meet an unrealistic layout. Options: (a) accept as is; (b) a follow-up that translates those three tests whole, off the made-up place, so the relative distances come back. Not blocking for today.
2. The dp KDoc figure at `RouteCardExpandShortLandscapeTest.kt:38` is a scanner false positive, worked around in the text rather than in the scanner. Fine for the release; whether the scanner should learn to skip `dp` figures is a separate choice.

## Notes

- Hazel's report names no value and matches what I found line for line; its "Premises that were wrong" (one of eight is a dp figure, not a latitude) is correct.
- No `docs/audits/README.md` row, following the stated planner convention for #502-#504.
- Hazel's Verification section was re-derived here, not quoted: scanner totals, run heads, case counts and the pre-main drift were each checked against the source.
- Not done: no Gradle, no device; the Restricted Object not read or named.
