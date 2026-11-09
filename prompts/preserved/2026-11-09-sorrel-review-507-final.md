# Sorrel's review: PR #507's last changes before merge (074ce1fba..e063a2e4e)

Reviewer: Sorrel Tally (Fable 5.1), 2026-11-09. Read-only; nothing changed, nothing pushed. No Gradle run.

## Verdict: pass, ready to merge. No blocking defect.

## What was checked and how

**Merge c4bfe167a (pre-main 23b1bf25c into route-card).** `git merge-tree --write-tree` of its two parents, diffed against the
committed tree: the hand edits are exactly three files — `CompactMapLandscapeNavigationCard.kt`, `NavigationCardOutlineEveryEdgeTest.kt`,
`RouteCardTuckedStaysTest.kt`. Nothing else was touched by hand. The conflict file differs from pre-main's version only in the one
outline statement (`landscapeNavigationCardOutlineSides(...).copy(bottom = bottomRoundedAndOutlined)` replacing the inline
`landscapeColumnOutlineSides(...)` call); `RoundedMapCard.kt:287,289` passes `bottom` straight into `MapChromeOutlineSides` and computes
nothing else from it, so the two are equivalent. Both sides kept: #500's lift block, J5 `drawnLiftPx`, J4 bottom, E21's
`clipToBoundsOpenBesideRailCorner`/`navigationCardEndBesideRailCorner` and J8's resting-bounds write (`:275-281`) are present;
#507's shared outline rule (`RouteCardRailSide.kt`) is the only place the rail side is decided and is read by both cards. The two test
edits are byte-equal to `combine-500-507/test-claims.patch`.

**Merge 8282927f2 (pre-main 91f2cc237, #511).** Identical to the clean automatic merge; no hand edits.

**Band unification 34d5e002b.** Removed `railCornerBand` body: `railBottom = h + gap; from = max(railBottom - r, 0); to = min(h, railBottom);
from < to ? from..to : null`. `routeCardRailCornerRows` (`RouteCardRailEdge.kt:81-86`): the same three lines, parameter names aside. Two
call sites moved (`NavigationCardEndBesideRailCorner.kt:58,87`). No behaviour change.

**J8's route-card input (8b3764356, `RouteCardRestingTop.kt`, `CompactMapRoutePlotting.kt:303`).**
- Written only while `onNavigationCardNow && isNavigating && !tucked && state?.expanded != true`; `onNavigationCardNow` includes `!leaving`;
  cleared on dispose and set null otherwise. The reader (`NavigationDotAboveCard.kt:44`) falls back to the navigation card's resting top
  on null. Matches the recorded ruling verbatim (`prompts/preserved/2026-11-06-01-amendment-1.md:133`, RECORD -2183 line 36521: "the
  route card's top when it rests on the navigation card, otherwise the navigation card's; while the route card is expanded or elsewhere,
  D29's sideways move as today"; portrait "(a) Landscape only").
- Resting, not drawn: reads `navigationCardRestingBoundsInRoot.top` (#500's where-it-is-going rect) and the laid-out height; the card's
  `AnimatedVisibility` slide does not change the layout height.
- The formula `restingTop - controlsBottom - height` matches the layout (`CompactMapRoutePlotting.kt:308-309`: `.padding(controlsPadding)
  .padding(bottom = room.bottom)`, with `room.bottom = boxBottom - navCard.top`, `RouteCardPlacement.kt:159`).
- The two terms Hazel says never came into play: (a) `controlsBottomPx` is 0 in landscape by construction, not only under Robolectric —
  `AvailabilityCompactScaffold.kt:736-750` builds the controls padding from horizontal-only insets (cutout, navigation bars, rail) when the
  rail shows, so on a phone it is 0 too; the term is inert, and a wrong sign there could not show. (b) The cap-at-rest term (`cardMaxHeight -
  rise`) is non-zero only while the navigation card glides (J3 step 3), and the route card resting makes the evening line step aside, which
  puts the lift at `OnBottomEdge` (`CompactMapLandscapeNavigationCard.kt:171`), so at rest rise = 0. Mid-glide, lowering, the laid-out height
  clamped by the drawn cap under-reads the resting height for the frames of the glide; the dot is asked a little low, then corrects. Cosmetic,
  transient, device-only. No Play-user risk found.
- My #500 J8 review item 2.2 (the route card's columns must never exceed the navigation card's, or the across-check misses) is now claimed:
  the new test's precondition asserts left/right within 0.5 dp at 823 x 384 and 780 x 360.

**Tests and reverts.** `NavigationDotAboveRouteCardTest` runs the real Maps tab via `RouteCardBarRig`, reads the padding the navigation
view is sent and derives the dot from it, cards' bounds from the screen; a real `performTouchInput` swipe tucks. JUnit XML read from the
artifacts (`gh api` zips, python3 -I):
- Red 42627450376 at f8435bf87: 4 tests, 2 failed, both "resting" cases ("covers none of the walker's dot at y 240.0.dp" / "y 225.0.dp");
  the same two in its full-suite job (7451 tests, 2 failed). 0 ` e: ` compile errors in either failed-job log.
- Revert 1 (6ac8628aa, the write made `top.let { null }`), run 42627758433: 40 tests, 2 failed = the resting cases, red's messages;
  full-suite job 7451, the same 2. No compile errors.
- Revert 2 (f8f974fee, written while tucked/expanded too), run 42627762922: 40 tests, 2 failed = "expanded, tucked and closed", failing at
  the tucked step (`bottomPx=446.0` / `500.0` expected 0). No compile errors. Hazel's note that the expanded step cannot fail on this input
  (D29 moves the dot out of the columns) is right and disclosed.
- Both revert diffs read against 8b3764356: each is the one-line edit described, nothing else. Each fails only a failure its own edit
  could produce. Revert branches are deleted on the remote; the commits remain reachable locally.
- Nothing silenced: no `@Ignore`/`assume` added in the range's own commits or the merge's hand edits; the two CI workflow commits in the
  range (266d4031e, a56e3dfe2) are pre-main's.

**By-name comparison, full run 42632383590 at the head e063a2e4e (green, 7529 tests: 7505 pass, 24 skip).**
- vs combined build 42610749686 (c29c0956d): common 7523, outcome differs 0; only here 6 (J8's 4, #510's 2 renamed MushroomLog tests);
  only there 89 (the combine's tools-only tests, 3 MushroomLog).
- vs pre-main's latest green 42630479382 (91f2cc237, the pre-main actually merged; finished during this review): common 7334, outcome
  differs 0; only in route-card 195 (#507's tests across the whole PR plus J8's 4); only in pre-main 23. All 23 are also absent from the
  combined build at b727d1bd4, and `git diff --diff-filter=DR 074ce1fba..origin/route-card -- app/src/test` is empty, so they are tests
  #507 renamed or re-claimed before this range (RouteCardRecenter* names, ReCenterOutline*, RouteCardPlacementTest, SchemaMigrationTest
  "4 to 23" -> 24, RoutePlottingChromeTest's one case). Not this range's doing; noted so the count is read right.
- The report cites full run 42627563386 (at 8b3764356, before the #511 merge); 42632383590 is the run at the merged head and is green.

## Defects
None blocking.

## Notes (fix when next in the file; none worth a round before the merge)
1. `NavigationCardOutlineEveryEdgeTest.kt:138-141` (test-claims edit) quotes the owner as "no overlap behind it". RECORD -2187 has the
   owner's words "there is a visible overlap between the two cards" and the planner's restatement "no part behind it ... Yes, that's it".
   A paraphrase in quotation marks; the ruling it points to is real.
2. The report's "Inferred, not run" for the padding term could add that it is 0 in landscape by construction
   (`AvailabilityCompactScaffold.kt:736-750`), which closes it rather than leaves it open.
3. J8 route-card input has not been seen on a phone; the report's S22 step 26 describes the check. The dot's camera move itself is
   device-only (MapLibre).

## Owner's calls
- Whether S22 step 26 (the dot above the route card) runs before this morning's release or after. CI proves the padding asked for, not
  the camera's move; the same was accepted for #500's J8.

## Could not determine
- The camera's actual motion on the padding (device-only).
- The mid-glide lowering case's visible size (needs a device, J3 step 3 conditions: landscape, font 1.5+, evening, route card at peek).
