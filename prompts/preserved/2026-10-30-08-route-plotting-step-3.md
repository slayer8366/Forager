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
