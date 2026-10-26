# Planner handoff, 2026-10-25 (written 02:15Z 2026-10-26, 7:15 PM PDT Sunday 2026-10-25)

Written by the outgoing planner (Rowan Waymark) at the owner's word: "Write a handoff and let's get ready to switch accounts." This replaces `2026-10-24-planner-handoff-2.md` as the starting point.

**Next free RECORD ID: -1639.** This handoff is -1638. Records worktree `~/.cache/forager-wt/records`, branch `records-after-173`.

## Read first
- `~/Zynergy/Forager/CLAUDE.md`, then the memory index.
- RECORD -1620 to -1638. They are short.
- The 2026-10-23 handoff for keys and release steps, where nothing below updates them.

## Check before acting
- **Nothing is running.** Hazel and the step 7 coder were both stopped by the owner. Every helper worktree was checked clean against its remote at handoff. The step 7 coder's local branch was 4 commits ahead (only a merge of pre-main, no amendment code); saved to `off-track-reroute-track-wip` (ddaf4e0cd).
- Run ListAgents; `git log` on origin/records-after-173, pre-main and the open PR branches; look for commits not yours (memory: check for a newer planner after reconnect).
- **The S22 dropped off USB at 17:06:34 UTC 2026-10-25, mid-check.** It still has build 1.0.3958+g1cda6c46, a test recording started 12:38 UTC (returning), the gps and network test providers set, stay-awake 7, units Imperial. Clean it up first when reconnected: stop the recording, delete the test track and its waypoints, remove the providers, stay-awake 0; confirm Night Maps off and Trail. Schema 22. Area 1 is the owner's.
- **The owner was at the OMS Fall Show** giving a talk.

## Branches
- **main:** 177215135, unchanged (#393). The Worker builds from main.
- **pre-main:** 44af58a3c, CI green (run 40235720600). Since the last handoff it gained: way home part 2 A #394, B #395, C #396 (-1626); the map crash fix #397 (-1627); step 7 PR 1 #399, the false off-track alarm (-1635); PR 2 #400, Re-center (-1635); #398, the figure after a re-route (-1634, -1637).
- **dev:** 82b55f06a, unchanged; the rebase is still parked (-1615).
- **No release until the whole trail navigation plan is done, elevation routing included** (-1628, the owner: "Everything"). This replaces -1612's "until step 7".
- Pre-main's push run after #399 (40192708715) hit OutOfMemoryError in three MapCreditCaption tests; one re-run was green (-1637). If it repeats, a coder diagnoses the memory use; no test is silenced.

## Open PRs
| PR | Branch @ head | State | What it waits for |
|---|---|---|---|
| #401 shared style fix (-1627, -1634; dispatch 2026-10-25-02 + Am. 1) | map-style-shared @ d335076ac | CI green 40192329788; Sorrel: mergeable on the code | Hazel's evidence comment (none posted, Sorrel's 401-1) and an S22 run that hits the gap (Sorrel's review §2.6: at least one "skipped" route/overlay line across ≥30 switches while returning; route and breadcrumbs back; per remembered locate exactly one skipped/remembered/carried-out triple; 0 FATAL; widen the gap with an offline style on a cold store if needed). The first batch (32 switches, 0 crashes) never ran inside a change and is not evidence. |
| #402 dotted line counts (-1635; dispatch 2026-10-25-09) | off-track-reroute-track @ e0e503245 | CI green 40192942880; Sorrel: mergeable as ruled | **Amendment 1 (-1637): a drawn line counts only while heading back along it**, covering the track-only way home too. Not started (verify first: how "heading back" is judged; stop if it needs a new figure). Then Sorrel's re-check, then the owner's merge word. |

Reviews: `prompts/preserved/2026-10-25-sorrel-398-401-402-review.md` (sorrel-398-401-402, 7ad9cf52f); `-sorrel-399-400-review.md` (sorrel-399-400); `-sorrel-397-review.md` (sorrel-397).

## Dispatched, not built
- **2026-10-25-10, the way home starts from the walker's newest point** (-1636). Hazel verified the cause (kept points flushed every 20 points or 30 s; the way home reads the stored track) and chose handing the watch the kept points via a `wayHomeOnKeptPoint`, not a flush. Start from pre-main 44af58a3c (has #398). Sorrel's bends report is the basis (`prompts/preserved/2026-10-25-07-report.md`, bends-report, 6ffac27ca): the routed trail does not cut bends; the line started up to ~42 m behind the walker.
- **2026-10-25-03, step 6 report** (navigate to a waypoint along trails). Not started; was Hazel's, after -02 and -10.

## Waiting on the owner (the list given at handoff)
1. Restart Hazel (on #401's evidence and S22, then -10, then the step 6 report) and the step 7 coder (on #402 Amendment 1), or hold. Both were stopped from the owner's side; don't restart without the owner's word.
2. Reconnect the S22.
3. Merges of #401 and #402 when ready.
4. **Step 10 rulings** (`docs/audits/2026-10-25-05-terrain-report.md`, later-steps-reports): a route height chart; slope as a readout or a layer, shape (a) compass strip or (b) centre pin; own Topo offline; a national water layer; whether Aspen's eight topo rulings go into Forager's RECORD.
5. **Step 11 rulings** (`-land-context-report.md`): private land (options A to E; research on unmerged `land-ownership-research`); default on or off; W3 or W2 for "what am I standing in"; where the readout lives; forest roads in the agency layer; one style version for steps 8 and 11.
6. **A go for step 8's app side** (ruled -1633), against a synthetic archive.
7. **For the owner to take to Aspen** (not directed by this planner): a trail-names file per area (step 7 PR 4, -1631); the agency-trails archive and its size (step 8, -1633); the elevation build (step 10).
8. **The owner's hilly walk** to log climbs (-1629). Walk build: 1.0.3955+gf5a0cfaf (run 40166931893), or name a newer pre-main build.

## Step 7, as ruled (-1631)
PRs 1 and 2 merged. PR 3 (the quiet off-trail state, "Back on trail", notice "Off trail · Return to Route along your track") waits for step 6 (shared files) and -10. PR 4 (wrong turn, named trails only) waits for the Pi names file. PR 5 (waypoint off-trail) follows step 6. Sorrel's 400-2: the old "Return to Route" comments in 16 files name the pill; tidy in PR 3.

## Follow-ups ruled for later
Landscape: the Re-center pill may cover the HUD distance figure (Hazel's S22, check on device). Sorrel's Lows in each review. -1623: E (a reach from reported accuracy) to revisit with walk data. L-394-1 waits for a walk. Earlier handoffs' list stands (known bugs 18-20, Elder's #386 and #391 items, AvailabilityViewModel split, R8 on hold, release steps on hold).

## Housekeeping done
`topo-plan-report` (the own-topo plan, -1552) had one commit never pushed; pushed as-is to `topo-plan-report` (5a9ea2d89). Four untracked data files remain beside it in `~/.cache/forager-wt/topo-plan-report`, untouched. `recording-line-clears-revert-1` has one modified file (an old revert worktree), untouched. /tmp is nearly full from other sessions; scratch goes under ~/.cache.

## People
Hazel (app coder, Opus); the step 7 coder (Opus, unnamed); Sorrel (reviewer, Fable); Juniper (researcher, Fable); Elder (UX auditor, Fable); Aspen and Willow (Pi), not directed by this planner.

## Standing rules
As in the 2026-10-23 handoff: the planner writes no code; no build without the owner's go; merges only on the owner's word; decisions via the question tool, sketches in text, step paths for behaviour; owner screenshots and phone media off GitHub; no schema downgrade; Pacific in chat, UTC in RECORD. Session limits stopped helpers three times; resume with SendMessage after a reset rather than starting fresh, unless the owner stopped them.
