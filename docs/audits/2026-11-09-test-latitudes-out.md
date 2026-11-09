# Test latitudes out of seven tests (RECORD -2192)

Hazel Cairnwright (app coder), 2026-11-09. Branch `test-latitudes-out`, cut from `origin/pre-main` at
`26fddf005`; PR #513 into pre-main. No value is named in this report: a moved value is called "a latitude
in the band", with its file and line.

## What was done

The project's scanner, reading `git diff origin/main origin/pre-main`, counted 8 latitudes in the band in
seven test files under `app/src/test`. Each flagged line was found by feeding the scanner one line at a
time; the scanner itself was only run, nothing under its directory was opened.

| File | Line(s) flagged | What moved | Why the test's claim holds |
|---|---|---|---|
| `domain/GetMapRecordsUseCaseTest.kt` | 107 | The recording track's two points, both one degree south. | The test asserts which record ids reach Tracks (none while recording, the id once ended) and that no failure is reported. No assertion reads a position. |
| `domain/route/RouteLegElevationTest.kt` | 25 | Points `a`, `b`, `c` together, one degree south, so the legs keep their shapes and directions. | Every assertion is on elevation (net, climb, descent and the shown text), computed from the heights list. The leg length `GeoDistance.metersBetween` is passed in but no assertion reads it, so the small change in metres per degree of longitude at the new latitude changes no expected figure. |
| `ui/availability/AvailabilityScreenWaypointNavigateTest.kt` | 116 | The second waypoint ("Big oak"), one degree south. The creek pin (the shared made-up place), the origin and the return line stay. | Its only roles are "another waypoint in the list" and "the one left after the creek pin is deleted". The map is a stub slot whose only glyph is the creek pin, at fixed dp; no assertion reads the second waypoint's position or its distance from anything. |
| `ui/availability/StraightLineToTargetTest.kt` | 24 | The same second waypoint, one degree south. | Used once, as the target of a line that must not be kept for a different waypoint; the claim depends on it being a different waypoint, not on where it is. |
| `ui/availability/TripNavigateTest.kt` | 97 | The waypoint ("Creek pin"), one degree south. The trip stays at the shared made-up place. | The waypoint's assertions are by id (navigation resumes as the waypoint's, the pin is untouched at arrival) and the line's last point equal to `LatLng(creek.lat, creek.lng)`, read from the waypoint itself. Its map glyph is a stub at fixed dp. |
| `ui/availability/RouteCardExpandShortLandscapeTest.kt` | 38 | Not a coordinate: a KDoc quotes a card's top edge in dp from a CI run, and the scanner reads the figure as a latitude. Written as a whole number and a third of a dp, the same figure to the two places quoted. | KDoc only; no code changed. |
| `ui/map/PlottedRouteDrawingTest.kt` | 57, 61 | Every point in the file (`a`, `t1`, `t2`, `b` and the route's middle point) and the expected bounding box's north and south, all one degree south. | The frame test asserts a bounding box in degrees; every point and both box edges moved by the same step, so the box keeps its size and the fitted points keep their order. The other tests compare parts by the same point objects, and their leg lengths are constants, not computed. |

One degree south was chosen as one step for every file: well clear of the band, and a fixed translation keeps
every relative position in the files where points are drawn against each other.

## Verification

- Scanner, `git diff origin/main HEAD`: TOTAL 0 in every category (it read 8 lat at `origin/pre-main`).
- Scanner, `git diff origin/pre-main HEAD`: TOTAL 0.
- CI on the code commit and the by-name comparison against pre-main's latest green run: see the PR and the
  coder's hand-back (filled in below once the run finished).

## Confirmed vs inferred

- Confirmed by reading each file: which assertions read the moved values (listed above), and that no
  expected figure is computed in metres from a moved point.
- Confirmed by the scanner: both totals 0.
- Inferred, resting on CI: that no production path reached from these tests changes behaviour with a
  waypoint about 110 km from the walker instead of a few km (no distance cap was found by a search of
  `app/src/main` for one).

## Could not determine

- Whether the band the scanner uses is the full extent of what should be kept out; the scanner's rules
  were not read, by instruction.

## Premises that were wrong

- The dispatch counted 8 latitudes in 7 files. One of the 8 is not a latitude: it is a dp measurement in a
  KDoc. It was rewritten rather than left, so the scanner reads 0 as asked.

## Decided beyond scope

- In `PlottedRouteDrawingTest.kt` the four points `a`, `t1`, `t2`, `b`, not flagged themselves, moved with the
  flagged middle point and box, so the expected box keeps its shape.
- In the three waypoint files only the flagged waypoint moved, not the whole test, because its position is
  read by no assertion and the others are the shared made-up place; moving everything would have changed
  more lines for no gain in what the tests check. If the owner wants the waypoint's distance from the made-up
  place kept as well, that needs a whole-test translation off the made-up place.
- No row was added to `docs/audits/README.md`; the index rows for #502 to #504 were added by the planner
  after merge, and this follows that.
