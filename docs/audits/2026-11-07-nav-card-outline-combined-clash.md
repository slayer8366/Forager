# #498's outline check failing in the combined build with #506 and #507: diagnosis (Hazel, 2026-11-07)

The owner's go: "Coder diagnoses it", "Fix where it belongs". The combined build `tools/s22-combined` a2a888a3e (pre-main 82d4a091a,
#498 5232b15fa, #506 7e5ed4785, #507 1a6a69f2d, #508 1f6d34b2e), CI run 42421186668: 7,332 tests, 16 failed, all in
`NavigationCardOutlineEveryEdge{Narrow,S26}{Day,Night}Test`, all the "route card on the navigation card" cases. Each PR is green alone.

## Where the check finds no outline [read, from the run's JUnit XML]

Every one of the 16 failures has exactly one miss, at the same spot: **the navigation card's top-left corner at 6 degrees**, about
14 dp below the card's top, on its left (map-facing) edge, inside the band where the saved route's card rests on (behind) the
navigation card. The run's pixels there, from the corner's centre outward (S26, rotation 90, side bar shown):

| | by day | at night |
|---|---|---|
| the card's fill (read at 8 dp) | 0.922 | 0.141 |
| the outline over that fill, as the check expects | 0.627 | 0.486 |
| the line actually drawn at the edge (15.3 to 16 dp) | 0.546, 0.546, 0.480 | 0.569, 0.569, 0.621 |
| 16 to 17.3 dp | the bare map, 0.502 | the bare map, 0.502 |

The check's line test (`isLine`, within 0.07 of the outline over the fill) misses by 0.081 by day and 0.083 at night.

## Why [read from the screenshot the run uploaded, pixel by pixel]

- The navigation card's fill is 80% (CLAUDE.md, "Nothing fully obstructs the map"). Where the route card lies behind it, the card's top
  16 dp read lighter (0.94 by day against 0.87 over the bare map): the route card shows through.
- #507 (dispatch 2026-11-06-05, step 6 and Amendment 3, the owner: "Every edge facing the map", "Top and left here") draws a 1 dp
  `onSurface` outline on the route card's left edge, aligned with the navigation card's left edge. It runs down behind the navigation
  card to the route card's bottom, 16 dp below the card's top (screen pixels x 1231 to 1233, the route card's line dark 0.19 by day down
  to y 1088).
- So in that 16 dp band, the navigation card's own outline is drawn over the route card's dark (by day) or pale (at night) outline seen
  through the card's fill. It reads 0.546 there by day; below the band, over the bare map, the same outline reads 0.61 [read: rows
  1093 to 1150 of `landscape-rot90-bar-shown-route-card-891-day.png` and its no-route-card twin].
- At 6 degrees the corner's arc is almost vertical, so all the check's line pixels lie over the route card's line. At 0 degrees the
  reading is below the band, and from 12 degrees on part of the run lies right of the route card's 1 dp line; those pass.

**This is (b), the check's reading, not a visible gap.** The outline is drawn on the card's edge at that spot and the map beside it is
bordered: the line is darker by day, paler at night, than the check's reference, because the route card's own outline shows beneath it.
#507's outline is an owner ruling, and so is the 80% fill; the check assumed the line lies over the card's fill alone.

## Isolation [CI only; filter `*NavigationCardOutlineEveryEdge*`, 36 tests each]

| ref | contents | run | result |
|---|---|---|---|
| hazel-clash-t1 | #498 + #506 | 42423596356 | 36 passed |
| hazel-clash-t2 | #498 + #507 (which merges #506) | 42423598970 | 16 failed, all "top-left corner at 6 degrees" |
| hazel-clash-t3 | t2 with `RouteCardOutline(shape, openEdges)` removed from `RouteCard.kt` | 42423601403 | 36 passed; that run's full no-Street-map job failed exactly #507's own five outline tests (`RouteCardOutlineOnNavigationCardTest` x3, `RouteCardTuckCloseTest` x2), so the build compiled and ran |

Trigger: #507's route card outline. #506 alone does not trigger it. #508 was not needed to reproduce it.

## The correction, held and not landed [stop: shared file]

The fix belongs in this PR's check (`NavigationCardOutlineEveryEdgeTest.kt`), and it changes only the reading: where the corner's line
pixels lie inside the route card's bounds (the route card behind the card), a pixel also counts if it lies on the line's side of the fill
by more than half the line's own contrast. This is the half-gap rule the check already applies to the line over the map (K-2a). The
claim is unchanged. Estimated, not measured: the card's fill over the route card's line with no line of its own reads about 0.12 from
the fill by day and 0.10 at night, against thresholds of 0.15 and 0.17, so a missing line would still fail.

- Combined build plus the correction (hazel-clash-t4): run 42423742217, 36 passed.
- One revert, that clause removed (hazel-clash-t5): run 42423744947, 16 failed, each "top-left corner at 6 degrees". Its build compiled,
  and the failures are this edit's own.

It is **not** on this branch. `next-turn` (#500, another Hazel's) edits the same file at the same lines: the
`checkEveryMapFacingEdge` signature, and the corner test's `if (run.none ...)` line (J4's `overMapInside`). One coder per file makes that
a stop. The correction waits as ref `hazel-clash-498-fix-hold` (dafad676d, on 5232b15fa) for the owner's and planner's call.

## Disclosure

- **Confirmed:** the miss spot and pixels (XML and PNG), the trigger (t1, t2, t3), and that the correction greens the 16 and its revert
  reds them (t4, t5).
- **Inferred:** that a phone shows the route card's line faintly through the card's top 16 dp, as Robolectric does. It follows from the
  80% fill, but nobody has looked on a phone.
- **Could not determine:** whether the owner wants the route card's left line to stop at the navigation card's top rather than run on
  behind it. That would change what a user sees, so it is not proposed here as a fix.
- **Not done:** full CI on this branch with the correction, because it is not landed.
