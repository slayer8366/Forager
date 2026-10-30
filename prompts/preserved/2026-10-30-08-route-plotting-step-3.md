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
