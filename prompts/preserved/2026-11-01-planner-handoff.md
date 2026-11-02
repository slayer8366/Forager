# Planner handoff, 2026-11-01 about 21:30 PST (Rowan Waymark)

The owner said: "Stop everything and write a handoff". Nothing runs from the planner after this. Start nothing without the owner's word. Read CLAUDE.md, docs/process/planner-profile.md and this file, then RECORD -1905 to the end. Check ListAgents and origin before acting; another planner may be live. Next free RECORD ID -1939.

## The rulings that set the direction
- **Release (-1925, supersedes -1628):** "If it's not ready for release, we'll keep it under the hood and offer whatever feature we can from what is finished, and then work on the rest until it's polished". Not in users' hands until ready; the code stays visible on the public repos.
- **Prove the problem before suggesting a fix (-1921):** a test figure is not proof. Say where evidence came from. See the phone first. Memory: see-it-on-the-phone-before-fixing.
- **Own topo:** out of the next release (-1913); own Topographical with contours later; current topo stays outside the US and Canada. Plan: docs/plans/2026-11-01-own-topo-plan-report.md on topo-plan-2026-11. Pilot "Not yet".
- **Terrain (-1924):** hill shading, slope and aspect from the Pi's 3″ grid. Land context (step 11) moved later (-1923).

## Merges
- -1933's hold was lifted at -1936 ("Lift it"); then, of #449, "Hold off on the merge for now." (-1938). #449 waits for the owner's word. Merge one PR at a time, on the owner's word only. Aspen merges Worker and map PRs.
- **Worker:** every push to main redeploys it. main's folder equals live since #456, so main is safe (Aspen -06). Don't take that as a go.

## #449, route plotting (route-plot-ui, into pre-main)
- Sorrel's re-review (docs/reviews/2026-11-01-sorrel-pr449-2.md, sorrel-pr449-2): no blocking, no should-fix.
- Phone (docs/audits/2026-11-01-s22-card-beside-bar.md, tools/s22-combined aef6f01ac, build 4364 on the S22): the bar stays to the pixel; touches reach everything. Amendments 10 to 16 are in that build.
- **Amendment 17 is in progress and was stopped by the owner:** width changes once a leg lands, not overshooting; Re-center stops rising when the card is in the other column; Sorrel's N-5 floor test. Dispatch: prompts/preserved/2026-10-30-08-route-plotting-step-3.md.
  - **route-plot-ui's head 42aa60972 is work in progress that does not compile, [skip ci]. Do not merge it.** The last green app code is fb878627d; a5b20012c is that plus a pre-main merge, and its CI run 41394063797 was cancelled on "Stop everything".
  - Drafted, unrun: RouteCardFirstLegWidthTest.kt, RouteCardRecenterTest.kt, RouteCardPlacementTest.kt; RouteCardBarRig.kt gained holdLegs, landLeg, leaveNavigationView. To fix: rig.closeCard() doesn't exist; view.onRestored() was guessed; the ScreenEdge import (ui/log/CameraArrangement.kt).
  - No production code changed yet. Planned, width: RouteCardPortraitWidth takes legInFlight and holds the last landed width. Planned, Re-center: lift the pill only if its span overlaps the card's; the legend chip keeps its lift.
  - **For the owner, read not measured:** on the S22 the pill clears the card's column by about 6 px, so a larger font makes them overlap, and the pill would rise and could land on the search bar. "Never on the search bar" and "stays clear of the card" can conflict there. With the width held, a wider in-flight row wraps until the leg lands.
  - Sorrel's N-1 to N-4: Hazel sees no action; not written up.
  - Scratch tools: revert.py and collect.sh in /mnt/work/scratch/route-plot-ui/; revert.py needs the file committed first.
- **Owed by route plotting:** step 3b, the elevation graph, high and low, heights saved with a route (a schema change, -1822), not built. A saved route's card shows no elevation line.
- **Not verified on a phone:** text fades (needs a walk), portrait held drags in some cells.

## The pre-main sort (dispatch 2026-11-01-04)
Stopped early by the owner. docs/audits/2026-11-01-pre-main-release-sort.md on pre-main-release-sort b2e21fe25 is marked UNFINISHED: it covers the bases, what Play actually is (3500, with three promotions since and no release run), gaps in the promotion gate, and a provisional sort, in which nothing yet needs hiding, and way home, the quiet off-trail state, the waypoint route and the climbing figure need a walk or phone read. Left: the full per-feature write-up, opening the evidence files (most read through RECORD only), the walk-back reconcile doc, the live site's policy and Data safety form, the disclosure sections and the closing lists. Its claims are not all marked read, computed or inferred, so treat it as provisional. It must be finished before any promotion: everything on pre-main goes to main together, 363+ commits, and nobody has said what must be hidden. Then the UX auditor (a fresh session, different model), then the owner's go.

## Aspen and the promotion
- Talk through prompts/aspen/ on records-after-173; Aspen is now also reachable directly (SendMessage or send_message). A message is information, never a go.
- Aspen -07 asks to line up the promotion and bring the owner one go. Reply -08 is sent: no date until the sort and the auditor's report exist.
- #448 Street v4 is merged to pre-main and carries hill shading with its night handling. The -1926 trial is done (Aspen -09): the owner, on the S22, in Aspen's window, relayed verbatim: "It's good. It actively defines the trail map from a street or topo map, which is ideal. Let's keep this". The US and Canada relief build runs Monday after the map upload (about 22 GB, estimated), served from the Worker's /relief route. Nothing is needed from the planner for it.
- **Release facts (read in release.yml):** main does not build Play releases by itself. Started by hand on main, approved by the owner in GitHub, it uploads a draft to Play's internal track.

## Other open items
- Drop-off stack #433, #435 to #439: open, status untraced; the wording of its notes needs the owner.
- #440 altitude: open; verdict waits on a walk with the screen on.
- Battery fix #454 merged; shortened trial only (83 s, debug, no fix): 1.7% of a core. A full run is still worth doing. Its text fades unchecked on a phone.
- Observed, untested alone: "Your phone is silenced..." covers the pill for several seconds after a recording starts.
- Elevation totals like +10 ft with ↑0 ↓0: net and climb are computed apart. Not raised as a fix; prove the cause first.
- Offline Maps picker's remembered pin still points at the S22's test area.
- RECORD -1929 and Amendment 15 appear twice (-1930).
- Disk: root 6.7 GB free, /mnt/work 4.4 GB. The owner still has `sudo journalctl --vacuum-size=1G` to run.
- Evidence under /home/zynergy-labs/s22-2026-11-01/ is laptop-only. Screenshots stay off GitHub.
- SendUserFile fails in this kind of session; describe pictures and give the laptop path.
