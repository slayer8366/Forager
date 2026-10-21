# Trail navigation: the plan updated, 2026-10-21

**Status: a plan. Nothing in it is dispatched.** Written by the planner (Rowan Waymark) on 2026-10-21 at pre-main 02fe89f1, after Phase 2 of the UI split completed. It updates `trail-navigation-and-own-tiles.md` (the plan of record, -454) from Juniper's status survey (`docs/navigation/2026-10-21-trail-navigation-status-survey.md` on records-after-173, RECORD -1495). The owner chose "Update the plan, then ask (Recommended)" (RECORD -1497). No building starts until the owner answers the questions below and gives a go.

**This is a claim about the past.** Every statement about the repository is true at 02fe89f1 or cites the survey. Each stage's dispatch re-reads what it stands on first.

## Where it stands

The table repeats the survey's findings.

| Stage of -454 | State |
|---|---|
| 1 Engine spike | Done. BRouter chosen (-484). |
| 2 Own-tiles survey | Done (-466, -879). |
| 3 Spec | Done, ruled (-492, -493). The reconciliation with Navigator stages G–K was never written; see question 7. |
| 4 Pi pipeline | Built. The Pi emits tiles and BRouter `.rd5` squares. Only the tiles are uploaded: the US+Canada `us.pmtiles` went live on R2 on 2026-10-21. |
| 5 Own basemap in the app | Built behind a switch in #258. The owner's phone review of Street with trails is pending. #258 needs a re-merge with pre-main. |
| 6 Routing along the network | Not started. |
| 7 Off the trail and back | Not started. |
| 8 Reference layer | Not started. |
| 9 Owner's walks | Not started. |

## The order from here

Each step is its own dispatch. A step is dispatched only after the step before it has reported, and each dispatch answers the modular-code skill's four questions (`.claude/skills/modular-code/SKILL.md`).

1. **Finish the own basemap (stage 5).**
   - The owner's phone review of Street with trails.
   - #258 re-merged with pre-main.
   - The owner decides when the switch flips.
   - This comes first because routing draws on the same map, and #258 edits `ui/map/` files that routing will touch too. Under one coder per file, the two are sequenced, not run side by side.
2. **Split before adding (modular-code skill).** Trail navigation touches four files over about 800 lines, and each is split first as its own pure-move PR:
   - `ui/track/TrackRecordingViewModel.kt` (1,029 lines): the route production and its decisions;
   - `MainActivity.kt` (806): the wiring where a routing engine and repository would go;
   - `ui/availability/AvailabilityViewModel.kt` (976): waypoint navigation's start and stop;
   - `ui/availability/AvailabilityScreen.kt` (1,324): the waypoint straight line is produced here in the composable, and it moves to a ViewModel home (-1450).

   `AvailabilityCompactScaffold.kt` needn't grow: a new map input becomes a field of `CompactScaffoldMapInputs` (#354).
3. **Routing data to the phone (stage 4, finished).** Route files are published beside the tiles, and an offline region's download stores map and routes together, with its size shown (spec R5). This needs question 1.
4. **The routing engine in the app (stage 6, part 1).**
   - BRouter, behind an interface Forager owns (CLAUDE.md, Architecture: external integrations sit behind an interface). Domain logic depends on that interface.
   - BRouter is vendored from v1.7.10 source, pinned (CLAUDE.md, Building), and credited under its MIT licence.
   - The routing data is credited under ODbL.
   - Nothing visible changes yet.
5. **The way home along trails (stage 6, part 2).** Questions 3 and 4.
6. **Navigate to a waypoint along trails (stage 6, part 3).** Question 6.
7. **Off the trail and back, and the wrong-turn alert (stage 7).** Forager's own matching (-492). Questions 5 and 8.
8. **The reference layer (stage 8).** Forest Service and BLM trails, drawn apart (-1176).
9. **The owner's walks (stage 9).**

## Questions for the owner

Each question is stated as behaviour. They're asked one at a time through the question tool, with sketches in the message text.

1. **Route data on the phone.** Should a region download store the trail routes for the area alongside the map?
   - Cut to the region: about 1 MB more per region. The Pi or the server cuts it.
   - Whole squares: tens of MB per region, and simpler to build.
2. **Hills.** The route files carry no elevation, so routes don't avoid steep climbs. Keep routing flat for now, or add free public elevation data so routes weigh climbing?
3. **The way back, confirmed as a step path** (ruled at -445, re-confirmed before it is built). You're off the trail → tap Return → the line follows your own track back to where you left the trail → from there it follows the trail home.
4. **The walk-back time** (the sundown warning). Should it use the distance of the route the app is showing you, or your walked track?
5. **Naming.** "Return to Route" already names the button that re-centres the map while navigating. The off-trail notice reads "Off trail · your track leads back". Keep "Return to Route" for the map button, or rename one of them?
6. **Navigate to a waypoint.**
   - Today: a straight dashed line, and no off-track alert while heading there.
   - Proposed: the line follows trails to the waypoint, and leaving the trails brings the quiet off-trail behaviour from question 3's rules.

   Re-confirm the step path.
7. **The Navigator plan's later stages.** Should H (terrain) and I (land context) fold into this plan, given they overlap the map work? Should G, J and K stay where they are?
8. **The wrong-turn alert.** It needs to know which trail you're on. That would come from the trail names in the new map data, which is readable on the phone. Confirm the alert should only fire on a named trail or road, so that unnamed paths don't trigger it.

## Constraints carried forward

These are the plan of record's constraints, unchanged:
- positions never enter the repository;
- offline regions keep working throughout;
- attribution stays visible;
- every figure is provisional, and a walk tests it;
- map chrome sits at 80% opacity.

## The owner's answers, 2026-10-21 (RECORD -1498)

1. **Route data:** "Cut to the area (Recommended)". About 1 MB extra per area, cut by the Pi.
2. **Hills:** "Flat first, size elevation in parallel (Recommended)". Trail routing ships flat. A read-only check sizes the elevation work (sources, licences, conversion, Pi time) so it can follow soon after, without blocking.
3. **The way back:** "Yes, as sketched (Recommended)". The line follows the walker's own track back to where they left the trail, then the trail route home, with no straight-line shortcut.
4. **The walk-back time:** "The route shown (Recommended)".
5. **Naming:** "Rename the current button to Re-center like it does on Google maps, and use the Return to Route for the Off trail notice." The exact wording comes back as a step path before stage 7 is built.
6. **Waypoint:** "Follow trails, quiet off-trail (Recommended)". The same treatment as the way home.
7. **Stages H and I:** "Fold H and I in, later (Recommended)". They come after the reference layer; G, J and K stay in the Navigator plan.
8. **Wrong turn:** "Named trails/roads only (Recommended)". Unnamed paths fall under the quiet off-trail behaviour.

The order above stands. Elevation becomes a parallel sizing check, not a step. H and I are added as steps 10 and 11, after the walks.
