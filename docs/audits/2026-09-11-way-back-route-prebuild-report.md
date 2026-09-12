# Pre-build report: the way back should follow the walked path

**Date:** 2026-09-11
**Asked for:** "The way back feature takes you directly to your waypoint, which is nice for open
fields. But for forest paths, this is rarely the case. We need to have the way back feature
navigate along the path that the user traveled, using the path that actually leads back to the
start (in case they did some circles, or backtracked along the way)."
**Status:** nothing built. Every claim below names a file and a line.

---

## Headline: the route already exists and is discarded

`joinedTrackHome` (`TrackSelfJoin.kt:75`) runs Dijkstra from the most recent stored point to the
first, over a graph whose edges are the legs between consecutive points **and** the self-joins
wherever the track passes within ε of itself. It allocates a predecessor array at
`TrackSelfJoin.kt:118`, fills it at `:132`, and walks it backwards at `:143-147` to count how many
joins the route crossed. Then it returns three scalars at `:148` and the route is gone.

That route is exactly what was asked for. It follows walked ground, and where the walker looped or
doubled back it takes the shortest way home through the join rather than retracing the loop. The
loop case is not a gap to design around; it was ruled on 2026-09-07 and is implemented
(`PathHome.kt:72-86`).

**So the algorithm is done.** What is missing is that nothing carries the route out of the domain,
and nothing on screen steers by it.

## What is confirmed to exist

| Thing | Where | State |
|---|---|---|
| Distance home along the walked track | `PathHome.kt:98` | Built, called in production from `TrackRecordingViewModel.kt:374`, lands in `TrackRecordingUiState.pathHome` |
| Shortest route home through self-joins | `TrackSelfJoin.kt:75` | Built, route computed and discarded at `:148` |
| That distance reaching the HUD | `NavigationHud.kt:182`, `:407` | Built. Renders as "Path home NNN m" |
| Polyline rendering on the map | `SightingsMap.kt:641-643` (`keptTrackPolylines`), carried through `MapSlot.kt:187` | Built, and the pattern is `GeoJsonSource` + `LineLayer` |

## What does not exist

1. **Route geometry out of the domain.** `PathHome` (`PathHome.kt:140`) and `JoinedTrackHome`
   (`TrackSelfJoin.kt:152`) carry only numbers. No ordered list of positions leaves either.
2. **Any drawing of the route.** There is no route source id and no route layer in
   `SightingsMap.kt`.
3. **Any steering cue that follows it.** `navigationReadout` takes the bearing and the distance
   straight to the target waypoint, `NavigationHud.kt:361-362`, and aims the needle there at
   `:382`. That is the "111 m" and the arrow in the owner's screenshot.

One consequence worth stating plainly: **the number the walker reads and the number the walker
steers by are already two different numbers.** "Path home" is the along-track distance; the large
figure and the needle beside it are the straight line. And at `NavigationHud.kt:411` the
along-track figure is *replaced* by "Approaching" once inside the approach threshold, which is the
state the screenshot caught.

## Decisions needed before anything is built

### D1. What does the needle point at?

- **(a) The next stored point along the route.** Turn-by-turn feel. It will also be unusable:
  breadcrumbs are metres apart, so the needle would swing hard on every fix.
- **(b) Keep aiming at the destination; draw the route on the map only.** Smallest change, and the
  map carries the information.
- **(c) The first point along the route that is more than some lookahead distance ahead.** Steers
  around the corner without swinging.

**Recommendation: (c)**, with (b) as the first slice if the lookahead figure needs walking data to
settle. No lookahead figure is proposed here, because nothing in this repository measures one and
inventing it would repeat the mistake the sundown lead-time decision avoided.

### D2. Which destination? Three things are currently called "back"

- `ComputeReturnToStartUseCase.kt:9` goes to the track's **first recorded point**.
- `pathHome` adds a leg from that first point to the **origin waypoint** (`PathHome.kt:106`).
- The HUD's `target` is a **`Waypoint`** (`NavigationHud.kt:174`).

These can differ by tens of metres, and `PathHome.kt:59-62` already records that the origin
waypoint may be a network fix taken before GPS settled. One answer is needed, and it should be the
same answer in all three places.

### D3. Does the route distance replace the straight line in the readout, or sit beside it?

Today both are shown and the walker steers by the straight line. If the route becomes the thing
being navigated, the large figure arguably becomes the route distance. Against that: the straight
line is the one number that cannot be wrong, and `PathHome`'s whole design note is that its error
is never short.

### D4. Recompute cadence

`pathHome` runs on the 15 s track poll (`TrackRecordingViewModel.kt:374`). A drawn route refreshing
every 15 s is fine. A needle refreshing every 15 s is not; it will read as frozen. If D1 lands on
(c), the route geometry refreshes on the poll and the needle is re-aimed per fix against the route
last computed.

### D5. What happens when the walker is off the route?

The most-recent-point rule means the route always begins at the last stored point, and the hop to
it is a straight line. When that hop is in `HopBand.FAR` (over 50 m, `PathHome.kt:170`), the first
instruction is in effect "walk 60 m in a straight line back to where you last were". That may be
right. It may also be the moment to withhold the route and say so, which is the stance this app
takes elsewhere.

### D6. Draw the route always, or only when it differs from the track?

When `joinsOnRoute == 0` (`TrackSelfJoin.kt:158`) the route home **is** the recorded track, which
is already drawn as breadcrumbs. Drawing it again adds a second line on top of the first and no
information. The route only becomes visible information when it skips a loop, which is the case the
owner named. Drawing it only when `joinsOnRoute > 0` is defensible and is a real behaviour
decision, not an optimisation.

## Cost

Recovering the route from the Dijkstra is a predecessor walk in a loop that already exists
(`TrackSelfJoin.kt:143-147`), returning the indices it is already visiting. No new algorithm, no
new dependency, no new cost in the search itself. The join scan and search cost were measured in
the join dispatch and are unchanged.

The unmeasured cost is drawing: a several-hundred-point `LineLayer` re-sourced on a 15 s cadence on
real hardware. Nothing in the JVM suite is evidence about that.

---

## Disclosure

### Confirmed by reading the code
Every file and line above. The predecessor array, the discard at `:148`, the two callers of
`pathHome`, the straight-line bearing at `NavigationHud.kt:361`, the needle at `:382`, and the
"Approaching" substitution at `:411`.

### Could not be determined
Whether any test exercises a route with `joinsOnRoute > 0` end to end. Whether a re-sourced
`LineLayer` at this point count is cheap on the owner's device. Neither was checked and neither
should be assumed.

### Premises that were wrong
Mine, in reading the request. The request reads as "this feature does not follow the path" and the
natural inference is that the along-track route has to be built. It is already built, has been
since 2026-09-07, and the loop-and-backtrack case the owner specifically named is the case it was
designed for. Asking "who calls this, and what does it read?" before pricing the work is what
turned a feature into a plumbing change.

### Decided beyond scope
Nothing. No code written, no figure invented, and D1 through D6 are put to the owner rather than
resolved here.
