# Dispatch 2026-10-30-08: route plotting step 3, plotting on the map, the live card, saving and opening a route (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1845. Cut from **pre-main b493cffeb**; verify that is still the remote head.

**Authorisation.** The owner, verbatim: "Yes, split around #447 (Recommended)", whose option read: "Step 3 now: plotting mode, the live card with distance and legs, Save, and opening saved routes. Climb, graph and high/low (with the database change) follow as step 3b once #447 merges. It starts with a look-only first stop."

**Read first** (on origin/records-after-173):
- the plan docs/plans/2026-10-29-waypoint-route-plotting-plan.md, all addenda;
- RECORD -1817 to -1845;
- dispatch 2026-10-30-02 and its amendments;
- Sorrel's review docs/reviews/2026-10-30-sorrel-pr445-pr446.md, whose N8 to N10 belong to this step.

PlotRouteUseCase and the route storage are on pre-main (#445, #446). Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## The behaviour, as the owner confirmed it
```
Maps tab, tap waypoint "Creek" ─> bubble gains [Plot route]
tap it ─> plotting mode: tap the next waypoints in turn
  each leg routes along trails (stop → trail → stop, joins thin dashed)
  a leg with no trail data: thin dashed straight line, note "No trail data, straight line"
  the same waypoint tapped twice in a row: ignored; A→B→A allowed
card peeks live from the first leg:
┌──────────────────────────────────┐
│ Creek → Ridge → Falls   2.9 mi   │
│ (per-leg rows when dragged up)   │
│ [Undo] [Cancel]          [Save]  │
└──────────────────────────────────┘
Undo = remove the last stop · Cancel = drop the route · Back = cancel, in one tap
Save ─> name, defaulting to "Creek to Falls" ("Creek and back" when it ends where it began), date optional ─> Trip Planner
```

**A saved route:**
- The pin sits at the first stop. Directions on it goes to the first stop.
- The Trip Planner row shows name, distance and climb, plus the date if set. In step 3, climb is the step-3b slot (below).
- Tapping the row, or the pin's bubble, moves the map to fit the whole route, draws its line and peeks the card. Back closes the card and the line.
- The card is a bottom sheet over the map, with its fill at 80% (CLAUDE.md, map chrome).

**Not in step 3 (step 3b, after #447 merges):** climb and descent, the elevation graph, high and low, the per-leg climb, heights saved with a route (migration 23→24), and the climb text "—" + "Re-download map for climb". In step 3, leave the card's climb row out entirely, rather than showing "—".

## Words on screen (all approved by the owner)
"Plot route" · "Undo" · "Cancel" · "Save" · "No trail data, straight line" · default name "<first> to <last>" or "<first> and back". Any other word you need, propose it and do not ship it. The planner asks the owner.

## Stop first (look only, no code)
Write docs/audits/2026-10-30-route-plotting-step-3-first-stop.md covering:
- (a) how a waypoint tap reaches plotting mode (MapTapHandler) without breaking fans, bubbles or navigation, and how Back gets its own tier (AvailabilityCompactMapUi.kt:347-353, AvailabilityScreenBackHandlers.kt);
- (b) the card: an existing bottom-sheet pattern to reuse, its peek and expanded states, landscape, and tablets;
- (c) the map layer for a route line (trail solid, joins and straight legs dashed), apart from the navigation layers;
- (d) where the plotting state lives (a ViewModel, not a composable) and what survives a tab change. The CLAUDE.md UX default is that it survives; say if you see a reason it shouldn't;
- (e) Save: name dialog and optional date, through SavePlannedTripUseCase or a new path;
- (f) N8 to N10 from Sorrel's #445/#446 review;
- (g) every file over 800 lines you would touch (AvailabilityCompactScaffold.kt is 1440; AvailabilityViewModel.kt about 797), and the split plan;
- (h) the coordinate-touch tests you will write, sampling several points across each control;
- (i) prior art: how two or three established hiking apps let you plot a route by tapping points, and anything here that departs from them.

Push it, and wait for the planner's amendment.

## Rules
- Branch `route-plot-ui`, worktree ~/.cache/forager-wt/route-plot-ui.
- CI only, no Gradle on the laptop, no adb. gh is logged in; run gh commands bare.
- Network: GitHub, plus web reading for (i) only.
- The Restricted Object: never read, name or quote it.
- Past about 200k tokens: push and hand off.
- Ambiguity: stop and report.

## Amendments, by category (arranged 2026-11-02)
The owner, 2026-11-02: "Revert the amendments into separate parts, but have them arranged different. A B C D to arrange different categories. And have the amendments add to each category. IE amendments C1 C2 C3 for the cards"; "4 isn't the limit, use as many as it takes".

Amendments 1 to 17 were written in date order; their rulings are kept here, sorted into categories and numbered in the order they were ruled. Each item names the amendment it came from, its date and its RECORD entry. A later item that replaces an earlier one says so, and the earlier one is marked. The amendments' original text is in this file's history up to f66ccf8ae.

| | Category |
|---|---|
| **A** | Plotting and saved routes: tapping, Back, Discard, Save, opening a saved route, the camera, the Trip Planner row |
| **B** | The route line on the map |
| **C** | The cards: words, rows, totals, elevation, and the card's place, shape, width and motion |
| **D** | What sits around the card: icon bar, record and return pill, legend chip, the "i", Re-center |
| **E** | Tests, reviews and checks |

**Open, to build:** C21 with C23, and D13 (landscape; portrait per D16), then D15 with D16's portrait pictures (C15 to C20 and C22 built; D12 done). Confirmed by the owner (-1955). Head as written: cd727ecce.

**For every item:** fail first, then a revert check (build log checked for compile errors, restored from a saved copy). Merge origin/pre-main in when it moves: merge, never rebase or force-push. Full CI green, with the CI run on the last real commit, never an empty one (Amendment 17). Update the PR body. Don't merge the PR (-1938). New words go back to the planner. Stop and report, don't choose, on anything a user would see that isn't ruled here.

## A. Plotting and saved routes
- **A1** (Amendment 1, 2026-10-30, -1852). The first stop (docs/audits/2026-10-30-route-plotting-step-3-first-stop.md, route-plot-ui 9d9393a8f) is accepted as proposed, including its corrected premises. The go: build step 3 (the owner's "Yes, split around #447 (Recommended)", -1845). Coordinate touches per the first stop's (h). New files stay under 400 lines. CI only. A PR into pre-main. No phone without the planner's word.
- **A2** (Amendment 1, Q4). "Yes (Recommended)": plotting is allowed while navigating or recording, and neither is touched.
- **A3** (Amendment 1, Q5). "Ask: \"Discard this route?\" (Recommended)": a saved route tapped mid-plot asks; "Discard" opens the saved route, "Keep plotting" stays.
- **A4** (Amendment 1, Q7). "Opens the route straight away (Recommended)": a route trip's pin opens it exactly as its row does: fit, line, card peek. Directions (to the first stop) sits on the card. No bubble for route trips.
- **A5** (Amendment 1, Q12). "No, keep it clean (Recommended)": route rows show name, distance, climb (step 3b) and date; no MGRS or coordinates.
- **A6** (Amendment 1, Q3). "Yes, cancel first (Recommended)": Back cancels plotting before leaving fullscreen.
- **A7** (Amendment 1). The Save dialog: title "Save route"; label "Route name"; "Add date" / "Remove date"; on failure, "Couldn't save the route. Try again.", with the dialog kept open and the name kept.
- **A8** (Amendment 1, the planner's rulings). Q6: Back on an expanded card cancels in one tap (-1817). Q5, second half: Back retraces the way in; a route opened from the Trip Planner in the Tools drawer closes its card and line on Back, and the drawer reopens. Q10: "and back" is judged by position (the last stop at the first stop's position). Q11: the plotting controls ride on MapRecordSources; AvailabilityCompactScaffold.kt is not changed. Also accepted: the one `private` → `internal`; a new fit frame with separate side padding; leaving GPS-follow when a route opens; N8 now, N9 noted, N10 measured in step 5.
- **A9** (Amendment 3, 2026-10-30, -1861). The first coder's calls beyond the dispatch (-1859) are accepted. Handoff: docs/audits/2026-10-30-route-plotting-step-3-handoff.md.
- **A10** (Amendment 5, 2026-10-30, -1879, Sorrel's S-1). "Fix: keep my view (Recommended)": a saved route fits once when opened. After that, returning to the Maps tab keeps the user's camera and leaves GPS-follow as the user set it (-750's precedent: keep the applied id in MapCameraMemory, or consume the open's serial once applied). Fail first through a real tab change, open → pan → Journal → Maps, with MapDataRefresh running.
- **A11** (Amendment 5, N-10). draft.withLeg moves inside the try, so a failed require is logged as a failed leg, never a crash.
- **A12** (Amendment 5, N-11). Deleting a route trip while it shows closes its card and line.
- **A13** (Amendment 5, N-2). "Keep plotting" after a Trip Planner row tap reopens the drawer.
- **A14** (Amendment 7, 2026-10-31, -1890). The leg stall, a defect in this PR's own code: in RoutePlottingViewModel.routeNextLeg, assign legJob before the launched body can finish (or check isActive/completion rather than non-null), so a router that answers without suspending never stalls later legs. Fail first with a test router that does not suspend, through the real entry points: three stops, every leg lands. Keep the yield() routers' tests.
- **A15** (Amendment 11, 2026-11-01, -1909). Trip Planner, "Match the card (Recommended)": a saved route's row uses the same shown-rows total as its card (PlannedRouteRow.kt:58, as of 9167cead5), so one route never shows two distances. Fail first with 0.4 + 0.4 + 0.4. If the row lacks the legs without a new query or a schema change, stop and report.

## B. The route line on the map
- **B1** (Amendment 1, Q9). "Orange (Recommended)". *Replaced by B3.*
- **B2** (Amendment 1, Q8). "Yes, small dots (Recommended)" at each stop on a saved route.
- **B3** (Amendment 2, 2026-10-30, -1853). Orange was taken (the forecast ramp, ColourFields.kt:60-75; Street v3/v4's highways and major roads, NightColour.kt:117-121). The owner: "Magenta (Recommended)". PLOTTED_ROUTE_COLOUR_DAY and _NIGHT are a magenta, checked against the track purples (MapPalette.kt), Street v3 and v4, topo and the forecast ramps, by day and night; too close to a track purple: stop and report. Report the values and contrast.
- **B4** (Amendment 3, -1861; Amendment 4, -1872). "Add a thin outline (Recommended)": the joins and straight legs get the same casing as the solid line, white by day, black by night. Accepted in Amendment 4: the dashes are outlined on their sides only.

## C. The cards
- **C1** (the dispatch; Amendment 1, Q1). "Yes, \"Tap the next waypoint\" (Recommended)": the card peeks at once with that line until the first leg exists. *Replaced by C3's hint.*
- **C2** (Amendment 5, N-9). The card handle gets a 48 dp touch target, with the same look; a coordinate-touch test at its edges.
- **C3** (Amendment 6, 2026-10-31, -1887). The card's layout, from the owner's first use on the S22: "The waypoint to waypoint line at the top is useful for the total, but seems out of place being at the top, and messy with the text"; "Yes, as sketched (Recommended)".
  - Peek, one stop: "1 Waypoint selected, tap another to route" (replaces C1). *Two lines since C15.*
  - Peek, two or more: "N Waypoints selected" with the total at the right. *Replaced by C9.*
  - Expanded: per leg, "<from> to <to>" with its distance at the right and its elevation under it; last row "N Waypoints routed" with the total and the route's elevation under it ("Yes, same form as the legs (Recommended)"); the form "Elevation: +240 ft (↑ 320 ↓ 80)" ("Both"), in the Units setting; "No trail data, straight line" stays on its leg.
  - Words, all the owner's: "1 Waypoint selected, tap another to route", "Waypoints selected", "to", "Waypoints routed", "Elevation:". Singular "Waypoint" only for 1. Text asserted literally.
  - Leave a seam, an optional elevation per leg and for the total ("Show them while plotting (Recommended)"); filled by C5.
- **C4** (Amendment 7, -1890). "Name + total, \"N Waypoints routed\" (Recommended)": a saved route's peek shows its name with the total at the right, and "N Waypoints routed" under it. The arrow chain goes. *Arranged by C9.*
- **C5** (Amendment 8, 2026-10-31, -1896). "Elevation lines into #449, then S22 (Recommended)", on #447's heights (TrailRoute.heightsMetres; a missing height is never 0). Fill C3's seam while plotting: net change last minus first; climb and descent through the journal's 4 m hysteresis (ClimbAllowance / bankedClimb; name what you reuse). A leg with no heights, a straight leg, or one in a square with no heights shows no elevation line, never "+0". A partly heighted leg: propose the rule first. The total shows only when every leg has heights. Say whether joins carry heights. Saved routes stay without elevation until step 3b. Tests: a fake router with known heights, exact text in imperial and metric; the segments-heighted fixture through PlotRouteUseCase. *The total's banking is replaced by C6.*
- **C6** (Amendment 9, 2026-10-31, -1902, F-1). "Total = sum of the legs (Recommended)": the total's net, climb and descent are the sums of the legs' own figures. Fail first with legs ↑0, ↑0, ↑14 giving ↑14.
- **C7** (Amendment 10, 2026-10-31, -1904). "Yes, add up what's shown (Recommended)": every total on the card (distance, net, climb, descent) is the sum of the rows' shown, rounded figures in the current units (0.4 + 0.4 + 0.4 shows 1.2 mi), on the routed total row and the peek.
- **C8** (Amendment 11, -1909). Mixed units, "Yes, leave it (Recommended)": stays as built.
- **C9** (Amendment 12, 2026-11-01, -1910). The owner: "Can the collapsed card have the 'X Waypoints routed' with the distance and elevation info like the expanded card? And when the card gets expanded, simply have that info stay at the bottom while the card with the info slides up, to be the total of the info". Two or more stops: the collapsed card shows "N Waypoints routed", its distance, the elevation line, then Undo, Cancel, Save. Drag up: that line stays where it is on screen and the rows slide up above it; drag down: they slide back. "Remove it (Recommended)": expanded, the top "N Waypoints selected" line is gone. "Name, then total line (Recommended)": a saved route keeps its name on top, the total line stays put below, and the rows slide up between. Replaces C3's two-or-more peek. Tests: the total line's top is the same collapsed and expanded, to the pixel, and its text literal.
- **C10** (Amendment 13, 2026-11-01, -1919). "Inset the card past the pill (Recommended)": in landscape the card starts past the pill. *Withdrawn: the owner, "Before adding it make sure the problem exists" (-1920); its commits reverted (-1922). Replaced by C11.*
- **C11** (Amendment 14, 2026-11-01, -1928). The owner: "Portrait has the same issue as landscape. Shorten the card in portrait to accommodate the map icon bar, with the card offset to the side. when the user swaps sides with the icon bar, the card simply shifts over to the other side. And then after that, make landscape card the same size as the navigation card that pops up, but have it pop up behind the navigation card and extend above it is they're already navigating". Evidence: docs/audits/2026-11-01-s22-build-449-454.md on tools/s22-combined.
  - Portrait: narrower, beside the icon bar with a small gap, anchored at the bottom; the bar crossing moves it over. A minimised or dragged bar stays exactly as set. *The gap is replaced by C17.*
  - Landscape: "Same place, swaps with the bar (Recommended)", the strip's width at the bottom, in the column away from the bar; "Rests on top of the nav card (Recommended)", rising from behind the navigation card with its lower edge tucked and all content, Save included, above it; below the strip and the search bar, rows scrolling at 5 or more stops. *Replaced by C18.*
  - Stop and report, don't choose: a line that can't fit at the narrower width at the largest font scale; Save unreachable above the navigation card in the short window; a tablet needing a different rule.
- **C12** (Amendment 15, 2026-11-01, -1929). The owner: "I have a better idea. Have the card slide in from the opposite side of the map icon bar in portrait. Landscape stays the same"; "Same card, slides in sideways (Recommended)"; "Our plan, nav-card size (Recommended)"; "Yes that's how i want it". Portrait: the card enters and leaves horizontally from the edge opposite the bar. Dragging the handle still slides the rows up above the total line. Reduced motion follows the app's rule for a sliding card. (Amendment 15 appears twice in the original; the second, with "Yes that's how i want it", is the one.)
- **C13** (Amendment 16, 2026-11-01, -1932). Width: "The card is supposed to slide from the opposite side, only give enough room to display the info": as wide as its content needs, never wider than the room beside the bar, the 640 dp cap no longer deciding. *Replaced by C16.* Minimised bar, "Keep the same width (Recommended)": stays. Large font, "Wrap to two lines (Recommended)": a line that can't fit wraps, nothing cut with an ellipsis, the distance staying at the right; 2.0 font scale asserted whole with native graphics. Long names, "Keep every word (Recommended)" (-1935). Use native graphics for every width test.
- **C14** (Amendment 17, 2026-11-01, -1937). First leg, "Change width once the leg lands (Recommended)": the card held its width while a leg was in flight, then eased once. Built at dbe3621eb (RouteCardFirstLegWidthTest). *Replaced by C16: the easing comes out with its tests, named.*
- **C15** (2026-11-02, -1946). *Built, 8a6126f9d (-1952).* The owner, from two S22 screenshots: "The cards are two sizes. Have the two sentences be on separate lines and reduce it to the other card size so both can be uniform without switching". The one-stop hint reads on two lines, "1 Waypoint selected," / "tap another to route". *To build.*
- **C16** (2026-11-02, -1946). *Built with C19, a04b55048 (-1954).* "One width, rows wrap (Recommended)": the card has one width from the first tap, the routed card's ("about 223 dp" was wrong: a two-stop card measures 230 dp at font 1.0 and needs 379 dp at 2.0, more than the portrait room, -1952; the rule for what it is fixed to waits on the owner), for the hint and every routed state, and it never changes; anything longer wraps (C13). Replaces C13's content width and C14's easing; remove what that leaves dead, and its tests, naming each. Stop and ask if one fixed width can't serve the routed card: say what you'd fix it to and why. *To build.*
- **C17** (2026-11-02, -1946). *Built, a04b55048 (-1954).* The owner: "When I meant to have it tab from the side, I meant the flat part on the side. The draggable tab remains at the top"; "Flat on the side it comes from only"; "Flush to the edge (Recommended)". Portrait: the card sits flush against the edge it slid in from, no gap; both corners on that side square; its top and bottom corners facing the map round; the handle stays on top. Today: round top corners, flat bottom (RouteCard.kt:165 at e3a500ebf). The bar moving across takes the card to the other edge, its square side with it. Whether the margin gained goes to the words or the card keeps its text width: report, don't choose. The gap is 8 dp (ROUTE_CARD_SIDE_MARGIN, RouteCardPlacement.kt:44 at 8a6126f9d), not the 16 dp the planner read from the screenshots (-1952). *To build.*
- **C18** (2026-11-02, -1947). *Built, a04b55048 (-1954).* The owner: "Portrait, but change landscape: same card size as portrait, have it set to the far left side next to where the navigation tab would be, so they're side by side when the user is navigating. Else, have it on the far right side where the navigation tab would be"; "Follow the navigation card (Recommended)"; "Touching it (Recommended)"; "Rises from the bottom, tab on top (Recommended)". Landscape, at C16's width, rising from the bottom edge, round top corners, handle on top. Not navigating: in the navigation card's spot, on the rail's side. (The planner wrote that the rail follows the phone's rotation; it doesn't: since -956 it is on the right at both rotations, mainWindowRailEdgeFor in PortEdge.kt, -1954.) Navigating: the navigation card takes that spot and the route card sits touching it, side by side, on the side away from the rail. Phone turned the other way: the same layout (see above). Expanded below the strip and the search bar; rows scroll at 5 or more stops. Replaces C11's landscape; remove what that leaves dead, and its tests, naming each. Stop and ask if the icon bar or its L sits where the card goes. *To build.*
- **C19** (2026-11-02, -1953). *Built, a04b55048 (-1954).* C16's width, the owner: "Fits a long route, per text size (Recommended)", whose option read "Wide enough for '10 Waypoints routed' and a two-digit mile total at the user's text size (about 246 dp at normal size), never wider than the room beside the bar. It doesn't change while plotting, only if the user changes text size, and normal routes never wrap." Measure the reference card's lines at the current font scale and window, capped by the room beside the bar. *To build, with C16.*
- **C20** (2026-11-02, -1953). *Built, a04b55048 (-1954).* C18, not navigating, with the L kept on the rail side, the owner: "The other bottom corner (Recommended)", whose option read "Only in that case, the card sits in the opposite bottom corner, away from the L, which is what the app does today. With the L elsewhere, or while navigating, it goes where you ruled." *To build, with C18.*

- **C21** (2026-11-02, -1955). *Built, 68ea07b82 (-1957).* Seen on the S22 (D12): while navigating in landscape the gap between the L's column and the navigation card is about 205 dp against the 247.67 dp card, so "N Waypoints routed" wraps and Save breaks as "Sav" / "e". The owner: "On top of the navigation card (Recommended)", whose option read "While navigating, the card rises from behind the navigation card and sits on it, at its width (wide enough for the card), as it did before. Not navigating stays as you ruled. On the S22 that means always on top while navigating, since side by side never fits there." Replaces C18's "touching it" while navigating; C18 and C20 stand when not navigating. Restore what C11 had for this (the tuck, the card resting on the navigation card with all content, Save included, above it), checking what C16 to C20 removed; at the navigation card's width, matching it edge to edge (the owner's option: "sits on it, at its width"). The planner first wrote "or C19's width if that is narrower", which the owner's words don't say; corrected here. So while navigating in landscape the card is wider than C19's width (about 343 dp on the S22). Stop and report if the navigation card is narrower than C19's width at some font scale. *To build.*
- **C22** (2026-11-02, -1955). C17's 8 dp, the owner: "As built (Recommended)": the card keeps C19's width; the 8 dp reaches the words only where the room limits the card. *Built, a04b55048.*
- **C23** (2026-11-02, -1956). *Built, 68ea07b82 (-1957).* C21's stop: at font 2.0 the navigation card is 329 dp against C19's 409.67 dp (Robolectric, 823 x 384 and 640 x 360; at 1.0 and 1.3 it fits). The owner: "Navigation card's width, lines wrap (Recommended)", whose option read "The route card still matches the navigation card edge to edge; its longer lines wrap onto two, as they already do elsewhere at large text. Whole words, nothing cut." *To build, with C21.*
## D. What sits around the card
- **D1** (Amendment 3, -1861). Icon cluster, "Cluster stays above card (Recommended)": its lowest position is the card's top while the card shows; the user's side and height stay remembered. *Replaced by D9.*
- **D2** (Amendment 3). Return to Route pill, "Pill sits above the card (Recommended)". *Replaced by D9.*
- **D3** (Amendment 4, 2026-10-30, -1872). "Chip rises above the card (Recommended)": the legend chip's bottom clears the card's top while the card shows. Accepted: the cluster's floor applies in every window.
- **D4** (Amendment 9, -1902). Fullscreen, "\"i\" moves above the card (Recommended)": the attribution "i" rises above the card. Fail first with a coordinate touch on Save at the "i"'s old place. *Revisited by D9: the "i" and credit keep clear of the card (-1931).*
- **D5** (Amendment 9, F-2). Check whether the cluster's bottom button overlaps the expanded card's first row at the S22's size; if so, the floor follows the expanded top. *Led to D6.*
- **D6** (Amendment 10, -1904, F-2). "Minimise the column while open (Recommended)": the column folds while the card is expanded and comes back exactly as set. *Removed by D9.*
- **D7** (Amendment 11, -1909). The pill, "Keep the pill showing (Recommended)": while expanded only the column folds; the pill stays reachable and clear of the card. *Removed by D9.*
- **D8** (Amendment 11). The restore handle, "Collapse the card (Recommended)": stays as built. *Removed with the fold by D9.*
- **D9** (Amendment 14, -1928). The owner: "The map icon bar stays the same no matter what. The card just gets resized to fit the map icon bar next to it". The card never moves, folds or raises the icon bar or the record and return pill, in either orientation, peek or expanded, plotting or saved. Removes D6's fold, D7's held pill and the raising (routeCardLift for the column and pill), and the handle's collapse with the fold; remove what has no caller and its tests, naming each. The "i" and other chrome keep clear of the card. Tests: the bar's bounds, flag, side and height unchanged by the card (assertBarUntouched asserts bounds, -1931); coordinate touches across record, return, Layers, "+" and Save, card shown and expanded, 1, 3, 5 and 6 stops, either side, recording; the phone's figures, pill [130,1179][490,1359] px at 3.75 px per dp, landscape.
- **D10** (Amendment 17, part 2, 2026-11-02, -1943). "Fix the S22 case, then check (Recommended)": Re-center rises for the card only where its horizontal span overlaps the card's; the legend chip keeps its lift. The shared-column cases (640 x 360, the 25.7 dp Robolectric overlap) are recorded, not asserted to clear the search bar. Built, e3a500ebf. On the S22 (dispatch 2026-11-02-01, Part 2): at font 1.0 Re-center stays off the search bar; from 1.3 the two share space and Re-center rises under the search bar.
- **D11** (2026-11-02, -1947). With C18, while navigating in landscape the route card goes where the S22 put Re-center, just beside the navigation card's column. Report what it covers; don't choose. *Open.*

- **D12** (2026-11-02, -1953). *Done on the S22, audit at tools/s22-combined 2312b363e (-1954): the card is cramped beside the navigation card; ruling pending.* D11, the owner: "Build, then look on the S22 (Recommended)", whose option read "Hazel builds C18 with Re-center following today's rule, puts it on the S22, and takes screenshots navigating, collapsed and expanded. You decide on Re-center and the dot from the pictures." Re-center keeps D10's rule; after C18 is green, an S22 build and screenshots in landscape while navigating, collapsed and expanded, at 1, 3 and 6 stops, both rotations: Re-center, the walker's dot and the credit line, each said covered or clear. *To do after C18.*

- **D13** (2026-11-02, -1955, the planner's call under D9). *Built for landscape, 68ea07b82 (-1957).* The credit strip is drawn over the card's button row while navigating in landscape (D12: Undo by 9 px, Save by 46 px). D9 already rules that the "i" and other chrome keep clear of the card: make the credit clear the card wherever the card is, re-checked after C21. Fail first with D12's figures. *To build.*
- **D14** (2026-11-02, -1955, the planner's call). *No longer holds after C21: the two tests are back to "stays put" (-1957).* Hazel's changes to RouteCardRecenterLandscapeTest and RouteCardRecenterTabletLandscapeTest (asserting D10's rule raises Re-center clear of a card now in its column) are accepted: they follow D10 and change nothing a user sees. Re-center at 6 stops expanded under the search bar waits for the S22 pictures after C21.
- **D15** (2026-11-02, -1955). *Done, audit at tools/s22-combined 50d501561 (-1957).* After C21 and D13 are green: an S22 build and the same pictures as D12 (navigating, landscape, 1, 3, 6 stops, collapsed and expanded), plus not navigating with the L on the rail side (C20), each with Re-center, the walker's dot and the credit line said covered or clear. *To do after C21.*
- **D16** (2026-11-02, -1956). *Seen on the S22 (-1957): the credit sits on the card, 4 dp at 1.0, covered at 2.0; so it rises above the card in portrait. To build.* D13's portrait case (Robolectric: the credit meets the card's bottom by 4 dp at font 1.0, 68 dp at 2.0; not seen in the owner's portrait screenshots at 1.0). The owner: "Check it on the S22 first (Recommended)", whose option read "The next S22 pictures include portrait at normal and largest text. If the credit really sits on the card, it rises above it, as your rule that chrome keeps clear of the card says." So D13 is built for landscape only; portrait waits for D15's pictures at font 1.0 and 2.0. "Meet" means the two rectangles overlap at the credit's resting place (the planner's call: a credit that shares only a horizontal span with a card it never touches does not rise).
## E. Tests, reviews and checks
- **E1** (Amendment 3, -1861). Finish from the handoff's list: touch tests for the Trip Planner row (Back reopening the drawer), the Discard dialog, Add date / Remove date and the failure line, landscape and tablet windows, and the Back order over a bubble, fan, drawer and fullscreen; MapChromeAlphaTest covering the card and dialogs; the bubble's wrapped row at font scale 2.0; a revert check for "taps while not plotting do nothing".
- **E2** (Amendment 4, -1872). "Apply both (Recommended)": the Fullscreen button's touch samples move to 0.2/0.8 of its bounds, as LandscapeLClusterTest's do; the two tablet window qualifiers gain `-xhdpi` (say so in the PR body, with what mdpi showed). Re-run the fullscreen-Back revert; find why the font-scale revert didn't bite and make it bite or say why it can't. Handoff: docs/audits/2026-10-30-route-plotting-step-3-amendment-3-handoff.md (213a38ebf).
- **E3** (Amendment 5, -1879). Sorrel's review docs/reviews/2026-10-30-sorrel-pr449.md (sorrel-pr449, 8d83e7b77). N-3: each approved word asserted as text in the screen tests ("Plot route", "Tap the next waypoint", Undo, Cancel, Save, "No trail data, straight line", "Discard this route?", "Discard", "Keep plotting", "Save route", "Route name", "Add date", "Remove date", "Couldn't save the route. Try again."), with one word reverted to show it bites. N-8: remove the unused imports in RoutePlottingTestFixtures.kt and RoutePlottingScreenTest.kt. N-1 and N-4 to N-7 are record only.
- **E4** (Amendment 17, -1937). Sorrel's second review, docs/reviews/2026-11-01-sorrel-pr449-2.md (sorrel-pr449-2): N-5, a direct test of the padding floor in RouteCardPlacementTest, failing with the floor removed; report the notes not taken, with why. Built, 9192fe659 (with N-4).
