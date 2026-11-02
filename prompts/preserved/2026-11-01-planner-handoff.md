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
- **Amendment 17 is in progress and was stopped by the owner:** width changes once a leg lands, not overshooting; Re-center stops rising when the card is in the other column; Sorrel's N-5 floor test. Check route-plot-ui's head (a5b20012c at writing, a pre-main merge) for what was pushed, and the helper's last message in the RECORD or below. Its dispatch is prompts/preserved/2026-10-30-08-route-plotting-step-3.md. CI run 41394063797 was running.
- **Owed by route plotting:** step 3b, the elevation graph, high and low, heights saved with a route (a schema change, -1822), not built. A saved route's card shows no elevation line.
- **Not verified on a phone:** text fades (needs a walk), portrait held drags in some cells.

## The pre-main sort (dispatch 2026-11-01-04)
Stopped early by the owner. Juniper's branch pre-main-release-sort has only its method (6299b737e at writing). It must be finished before any promotion: everything on pre-main goes to main together, 363+ commits, and nobody has said what must be hidden. Then the UX auditor (a fresh session, different model), then the owner's go.

## Aspen and the promotion
- Talk through prompts/aspen/ on records-after-173; Aspen is now also reachable directly (SendMessage or send_message). A message is information, never a go.
- Aspen -07 asks to line up the promotion and bring the owner one go. Reply -08 is sent: no date until the sort and the auditor's report exist.
- #448 Street v4 is merged to pre-main and carries hill shading with its night handling. Read Aspen -09 on the -1926 trial. The owner has not judged hill shading on a phone. Sage's look: prompts/preserved/2026-11-01-sage-hillshade-trial-look.md; Sage's two questions (day strength, night) are for the owner.
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
