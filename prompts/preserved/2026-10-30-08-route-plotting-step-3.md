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

## Amendment 1 (2026-10-30): answers to the first stop, and the go (RECORD -1852)
The first stop is docs/audits/2026-10-30-route-plotting-step-3-first-stop.md (route-plot-ui, 9d9393a8f). It is accepted as proposed, including its corrected premises, with these answers.

**The owner's answers:**
- **Q1, start hint:** "Yes, \"Tap the next waypoint\" (Recommended)". The card peeks at once with that line until the first leg exists.
- **Q4, plotting while busy:** "Yes (Recommended)". Plotting is allowed while navigating or recording, and neither is touched.
- **Q5, a saved route tapped mid-plot:** "Ask: \"Discard this route?\" (Recommended)". The buttons are "Discard", which opens the saved route, and "Keep plotting".
- **Q7, the pin:** "Opens the route straight away (Recommended)". A route trip's pin opens it exactly as its row does: fit, line, card peek. Directions (to the first stop) sits on the card. There is no bubble for route trips.
- **Q8, stops:** "Yes, small dots (Recommended)" at each stop on a saved route.
- **Q9, colour:** "Orange (Recommended)". Check orange isn't already used by another line or layer (sundown line, tracks, forecast); if it is, stop and report.
- **Q12, route rows:** "No, keep it clean (Recommended)": name, distance, climb (step 3b) and date; no MGRS or coordinates.
- **Q3, fullscreen:** "Yes, cancel first (Recommended)". Back cancels plotting before leaving fullscreen.
- **The Save dialog:**
  - title "Save route";
  - label "Route name";
  - "Add date" / "Remove date";
  - on failure, "Couldn't save the route. Try again.", with the dialog kept open and the name kept.

**The planner's rulings:**
- **Q6:** Back on an expanded card cancels in one tap, as already ruled (-1817).
- **Q5, second half:** Back retraces the way in. A route opened from the Trip Planner in the Tools drawer closes its card and line on Back, and the drawer reopens.
- **Q10:** "and back" is judged by position. The last stop is at the first stop's position.
- **Q11:** the plotting controls ride on MapRecordSources. AvailabilityCompactScaffold.kt is not changed.
- **Also accepted:**
  - the one `private` → `internal`;
  - a new fit frame with separate side padding;
  - leaving GPS-follow when a route opens;
  - N8 now, N9 noted, N10 measured in step 5.
- **Approved words:** "Tap the next waypoint", "Discard this route?", "Discard", "Keep plotting", "Save route", "Route name", "Add date", "Remove date", "Couldn't save the route. Try again.". Anything else goes back to the planner.

**The go: build step 3** (the owner's "Yes, split around #447 (Recommended)", -1845).
- Tests fail first, then revert checks per rule, with build logs checked and restores from saved copies.
- Coordinate touches per the first stop's (h).
- New files stay under 400 lines.
- CI only. A PR into pre-main, with no merge. Merge pre-main in if it moves.
- No phone without the planner's word.

## Amendment 2 (2026-10-30): the route colour is magenta (RECORD -1853)
Hazel found orange taken: the forecast ramp (ColourFields.kt:60-75), and Street v3/v4's highways and major roads by day and night (#f0b95c, #f2bf72; NightColour.kt:117-121). The owner: "Magenta (Recommended)".
- Set PLOTTED_ROUTE_COLOUR_DAY and _NIGHT to a magenta.
- Check it against the track purples (MapPalette.kt), Street v3 and v4, the topo style and the forecast ramps, by day and night. If it sits too close to a track purple, stop and report with the colours.
- Report the chosen values and contrast against the main map backgrounds.

## Amendment 3 (2026-10-30): finishing PR #449 (RECORD -1861)
PR #449 is at route-plot-ui 044552862, green (run 41128288933). Read the handoff docs/audits/2026-10-30-route-plotting-step-3-handoff.md first. The first coder's calls beyond the dispatch (RECORD -1859) are accepted.

**The owner's answers:**
- **Icon cluster:** "Cluster stays above card (Recommended)". While the card shows, the cluster's lowest position is the card's top. When the card closes, it goes back. The user's chosen side and height stay remembered (CLAUDE.md, UX defaults).
- **The dashes:** "Add a thin outline (Recommended)". The joins and straight legs get the same casing as the solid line: white by day, black by night.
- **Return to Route pill:** "Pill sits above the card (Recommended)". It moves up to clear the card, and both stay visible and tappable.

**Also finish, from the handoff's list:**
- touch tests for the Trip Planner row (with Back reopening the drawer), the Discard dialog, Add date / Remove date and the failure line, landscape and tablet windows, and the Back order over a bubble, fan, drawer and fullscreen;
- MapChromeAlphaTest covering the card and dialogs;
- the bubble's wrapped row at font scale 2.0;
- a revert check for "taps while not plotting do nothing".

Each rule gets a test that fails first, then a revert check. Merge pre-main in if it moves. CI green. No merge.

## Amendment 4 (2026-10-30): the two test fixes and the legend chip (RECORD -1872)
Read the Amendment 3 handoff, docs/audits/2026-10-30-route-plotting-step-3-amendment-3-handoff.md on route-plot-ui (213a38ebf). The owner:
- **"Apply both (Recommended)":**
  - the Fullscreen button's touch samples move to 0.2/0.8 of its bounds, as LandscapeLClusterTest's do;
  - the two tablet window qualifiers gain `-xhdpi`.
  Say in the PR body that the tablet windows now run at xhdpi, and what was seen at mdpi (centre-only touches).
- **"Chip rises above the card (Recommended)":** the legend chip's bottom clears the card's top while the card shows, as the cluster and pill do. Fail first, then a revert check.
- **Then:**
  - re-run the fullscreen-Back revert, which is now readable;
  - find why the font-scale revert didn't bite, and make that test bite or say why it can't;
  - get full CI green on the final head.
- The calls beyond the dispatch in the Amendment 3 handoff are accepted: the cluster's floor applies in every window, and the dashes are outlined on their sides only.
- Never force-push.

## Amendment 5 (2026-10-30): Sorrel's review of #449 (RECORD -1879)
Sorrel's review (Fable) is docs/reviews/2026-10-30-sorrel-pr449.md (sorrel-pr449, 8d83e7b77). The owner:
- **S-1, "Fix: keep my view (Recommended)":** a saved route fits once when it is opened. After that, returning to the Maps tab keeps the user's camera and leaves GPS-follow as the user set it. Use -750's precedent: keep the applied id in MapCameraMemory, or consume the open's serial once applied.
  - Fail first through a real tab change: open → pan → Journal → Maps, then the camera is unchanged and following is not ended again. The test must run MapDataRefresh.
  - Then a revert check.
- **N-3:** each approved word is asserted as text in the screen tests. This covers "Plot route", "Tap the next waypoint", Undo, Cancel, Save, "No trail data, straight line", "Discard this route?", "Discard", "Keep plotting", "Save route", "Route name", "Add date", "Remove date", and "Couldn't save the route. Try again.". Revert one word to show it bites.
- **N-9:** the card handle gets a 48 dp touch target, with the same look. Do a coordinate-touch test at its edges.
- **N-10:** draft.withLeg moves inside the try, so a failed require is logged as a failed leg, never a crash.
- **N-11:** deleting a route trip while it shows closes its card and line.
- **N-2:** "Keep plotting" after a Trip Planner row tap reopens the drawer, so the user is back where they were.
- **N-8:** remove the unused imports in RoutePlottingTestFixtures.kt and RoutePlottingScreenTest.kt.

N-1 and N-4 to N-7 are record only. Each rule gets fail-first, then a revert check. Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 6 (2026-10-31): the card's layout, from the owner's first use on the S22 (RECORD -1887)
The owner tried 1.0.4275 on the S22: "The waypoint to waypoint line at the top is useful for the total, but seems out of place being at the top, and messy with the text." Then the owner's layout, confirmed as sketched ("Yes, as sketched (Recommended)"):

**Peek (while plotting):**
- one stop: "1 Waypoint selected, tap another to route" (replaces "Tap the next waypoint");
- two or more: "N Waypoints selected", with the total distance at the right, e.g. "3 Waypoints selected    1.9 mi". The arrow chain of names leaves the peek.

**Expanded list:**
- per leg, "<from name> to <to name>" with its distance at the right; under it, the leg's elevation;
- last row: "N Waypoints routed" with the total distance; under it, the whole route's elevation (owner: "Yes, same form as the legs (Recommended)");
- the elevation form (owner: "Both"): "Elevation: +240 ft (↑ 320 ↓ 80)", meaning net change, then climb and descent, in the Units setting;
- the "No trail data, straight line" note stays on its leg.

**When elevation shows** (owner: "Show them while plotting (Recommended)"): while plotting, from the trail data's heights. A saved route's elevation lines come with step 3b. Heights reach the app only through #447 (TrailRoute.heightsMetres), which isn't merged.
- So **#449 builds the layout and words now**, and shows no elevation lines.
- The elevation lines while plotting are a follow-up on top of #447 and #449, dispatched when #447 merges.
- Leave a clear seam, an optional elevation per leg and for the total, so no layout change is needed then.

Words, all the owner's: "1 Waypoint selected, tap another to route", "Waypoints selected", "to", "Waypoints routed", "Elevation:". Use singular "Waypoint" only for 1. Fail first, then revert checks; text asserted literally. CI green; no merge.

## Amendment 7 (2026-10-31): the saved route's peek, and the leg stall (RECORD -1890)
Amendment 6 is green at route-plot-ui bce9c2de0 (run 41179955025); its report is RECORD -1889.
- **The saved route's peek.** The owner: "Name + total, \"N Waypoints routed\" (Recommended)". A saved route's peek shows its name with the total distance at the right, and "N Waypoints routed" under it. The arrow chain of stops goes. Fail first, then a revert check, with the text asserted literally.
- **The leg stall** (found in Amendment 6, a defect in this PR's own code). In RoutePlottingViewModel.routeNextLeg, assign legJob before the launched body can finish (or check isActive/completion rather than non-null), so a router that answers without suspending never stalls later legs.
  - Fail first with a test router that does **not** suspend, through the real entry points: three stops, every leg lands.
  - Then a revert check.
  - Keep the yield() routers' tests as they are.
- Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 8 (2026-10-31): the elevation lines while plotting (RECORD -1896)
The owner: "Elevation lines into #449, then S22 (Recommended)". #447 (trail heights) is now on pre-main 206464058: TrailRoute.heightsMetres carries one height or null per point, and a missing height is never 0.

**Do:**
1. **Merge origin/pre-main 206464058 into route-plot-ui** (merge, never rebase or force-push).
2. **Fill Amendment 6's seam** (RouteCardContent.legElevations, totalElevation) while plotting, from each leg's route heights:
   - **net change:** the last height minus the first;
   - **climb and descent:** banked through the same 4 m hysteresis the journal's Climb uses (ClimbAllowance / bankedClimb). Name what you reuse.
   - **the total:** over the whole route, banked across legs.
   - **the form:** "Elevation: +240 ft (↑ 320 ↓ 80)", in the Units setting.
3. **Missing heights:**
   - a leg whose route points are all missing, a straight leg, or one in a square with no heights shows **no** elevation line. Never "+0". Don't invent wording.
   - a leg with only some heights: propose the rule in the PR body before building (show nothing, or show from the heighted points), and stop for the planner if it is not obvious.
   - the total shows only when every leg has heights.
4. **Joins:** do the stop → trail joins carry heights? Say what you find. If they don't, the leg's elevation uses the trail part only, and the PR says so.
5. **Saved routes stay as they are:** no elevation until step 3b stores heights (the 23→24 migration). This amendment is plotting only.

**Tests:**
- Through the real screen.
- A fake router with known heights: the exact text for one leg and for the total, in imperial and in metric, and no line for an unheighted leg.
- The real heighted fixture (segments-heighted, from #447) through PlotRouteUseCase for one leg, checking the climb against the command-line heights.
- Fail first, then revert checks.

Full CI green. Update the PR body. No merge.

## Amendment 9 (2026-10-31): from the S22 phone checks (RECORD -1902)
Read docs/audits/2026-10-31-s22-phone-checks.md on origin/s22-phone-checks (2de1cced5). The owner:
- **F-1, "Total = sum of the legs (Recommended)".** The total's net change, climb and descent are the sums of the legs' own figures, so the total always matches the rows above it. This replaces Amendment 8's one-run banking across legs.
  - The total still shows only when every leg has heights.
  - Fail first with the phone's case: legs ↑0, ↑0, ↑14 give a total of ↑14. Then a revert check.
- **Fullscreen, "\"i\" moves above the card (Recommended)".** The map's attribution "i" rises above the card while the card shows, as the cluster, pill and chip do (routeCardLift). Fail first with a coordinate touch on Save at the "i"'s old place, then a revert check.
- **F-2 (unconfirmed on the phone):** check in a test whether the cluster's bottom button overlaps the expanded card's first row, at the S22's size (portrait, xxhdpi). If it does, the cluster's floor must follow the card's expanded top, not only its peek. Fail first, then fix. If it doesn't, say so with the measured numbers.
- Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 10 (2026-10-31): F-2 and the rounding (RECORD -1904)
The owner:
- **F-2, "Minimise the column while open (Recommended)".** While the route card is **expanded**, the map's button column folds to its minimised state. When the card collapses or closes, the column comes back exactly as the user had it: side, height and minimised flag (CLAUDE.md UX defaults). It never changes the remembered state. At peek, the column stays as Amendment 3 has it, raised above the card.
  - Fail first with Amendment 9's RouteCardExpandedClusterTest numbers: no overlap expanded, and the state restored after collapse.
  - Then a revert check.
- **Rounding, "Yes, add up what's shown (Recommended)".** Every total on the card (distance, net, climb and descent) is the sum of the rows' **shown, rounded** figures in the current units, so the digits always add up (0.4 + 0.4 + 0.4 shows 1.2 mi). This applies to the routed total row and to the peek's total.
  - Fail first with the phone's distance case, then a revert check.

Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 11 (2026-11-01): the coder's calls from Amendment 10 (RECORD -1909)
Read the PR #449 body, "Calls beyond the dispatch". The owner:
- **The restore handle, "Collapse the card (Recommended)".** Stays as built. Nothing to change.
- **The pill, "Keep the pill showing (Recommended)".** While the route card is expanded, only the column folds. The record and return pill stays showing and reachable, and clear of the card. This reverses Amendment 10's folding of the pill.
  - Fail first with coordinate touches on stop and return while the card is expanded and recording, sampled across each target's bounds, at the S22's size. Assert the pill's bounds clear the card's top. Then a revert check.
  - If the pill cannot clear the card at the S22's size with four stops expanded, stop and report the numbers. Do not choose a layout.
- **Mixed units, "Yes, leave it (Recommended)".** Stays as built.
- **Trip Planner, "Match the card (Recommended)".** A saved route's row uses the same shown-rows total as its card (PlannedRouteRow.kt:58, as of 9167cead5), so one route never shows two distances. Fail first with the 0.4 + 0.4 + 0.4 case, then a revert check. If the row does not have the legs it needs without a new query or a schema change, stop and report.

Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 12 (2026-11-01): the total line stays put (RECORD -1910)
Starts after Amendment 11 is pushed, on its head. The owner: "Can the collapsed card have the 'X Waypoints routed' with the distance and elevation info like the expanded card? And when the card gets expanded, simply have that info stay at the bottom while the card with the info slides up, to be the total of the info".

**The steps, confirmed by the owner:**
- Plot two or more waypoints. The collapsed card shows "N Waypoints routed", its distance, the elevation line under it, then Undo, Cancel, Save.
- Drag up: that line stays exactly where it is on screen, and the leg rows slide up above it.
- Drag down: the rows slide back behind it.

**The rest:**
- **The top line, "Remove it (Recommended)".** Expanded, "N Waypoints selected" and its distance are gone; the rows start right under the handle. The total appears once.
- **Saved routes, "Name, then total line (Recommended)".** The name stays on top. Below it, the same total line with distance and elevation stays put, and the rows slide up between the two.
- **Unchanged:** one stop keeps "1 Waypoint selected, tap another to route". The elevation line keeps its rule: it shows only when every leg has heights. Amendment 10's rounding and Amendment 11's pill stay.
- This replaces Amendment 6's "N Waypoints selected" peek for two or more stops. The text "Waypoints selected" stays only in the one-stop hint.

**Tests:** fail first, asserting the total line's on-screen top is the same collapsed and expanded, to the pixel, and its text literally; mid-drag too if the test can hold a drag. The column, the pill and the "i" clear the taller peek at the S22's size. If the taller peek stops any of them clearing, stop and report the numbers. Then revert checks.

Merge pre-main in if it moves. Full CI green. No merge, and never force-push.

## Amendment 13 (2026-11-01): landscape, the card clears the pill (RECORD -1919)
The owner: "Inset the card past the pill (Recommended)". In landscape, the route card starts to the right of the record and return pill, so nothing overlaps, at peek and expanded. The card is narrower there. Portrait is unchanged. Amendment 12's report has the numbers: pill x 8..104, y 284..332 at 823 x 384 dp, the card starting at x 52.
- Fail first: at that size, recording, peek and expanded, the card's left edge clears the pill's right edge, and five real touches across stop and return reach them. Then a revert check.
- Also check the column on the left side in landscape, and ROTATION_270. If the pill or the column sits elsewhere there, the card must clear it too; if it can't, stop and report the numbers. Do not choose a layout.
- All the card's text still fits at the narrower width, at the largest font scale tested on this card.

pre-main moved to 9ef5c9385 (#454): merge it in. Full CI green. No merge, and never force-push.
