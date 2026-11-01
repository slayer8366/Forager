# Dispatch 2026-11-01-01: our own topo map, a read-only plan (Juniper, Opus)

Written by Rowan Waymark, 2026-11-01. RECORD -1911. The owner: "Now we have elevation data, I think next release should contain our own topo maps"; "Size it, then decide (Recommended)"; "Yes, Juniper redoes it (Recommended)".

**Bases, as written:** pre-main d54189479, pi-build de30a84ee. Verify both, and treat every claim here as a premise to check.

**Why this is a redo:** dispatch 2026-10-21-08 (RECORD -1552) never landed its report. Only its method notes exist, on origin/topo-plan-report: docs/plans/data/2026-10-21-own-topo/method.md. Read that, and read the 10-21 dispatch for its questions. Much has changed since: the map types, the trail data now carries heights, and the height sources and credits.

**The report answers:**
1. What Topographical is today, who it reaches, and what our Trail Map already draws. Would own topo replace Topographical, sit beside it, or add contours and shading to Trail Map? Lay these out for the owner in what the user would see; recommend one.
2. Ways to build contours and hill shading, and what the map library supports, with versions read.
3. Where the heights come from. Which sources the Pi already holds, at what resolution, and whether they can feed contours and shading or only the route squares. Note that heights in the route squares are not the same thing as a height grid. Aspen is being asked; use prompts/aspen on origin/records-after-173 if a reply lands, and otherwise mark it unknown.
4. Sizes, computed: server storage for the covered area, tile counts, and what it adds to one offline area.
5. Coverage: US and Canada, and what the user sees outside them.
6. Pi disk and time, and the upload to storage, with the current limits.
7. Offline, night mode, and the zooms where contours appear.
8. Credits and licences for every source.
9. A build plan in steps with who does each (Pi or app), an honest time range, and what would hold the release. This is what the owner will decide on: whether topo holds the next release.
10. The questions the owner or Sage must decide.

**Disclosure:** every claim marked read, computed or inferred, with the four sections: confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope. Trace every claimed path to its caller.

**Output:** docs/plans/2026-11-01-own-topo-plan-report.md on a new branch topo-plan-2026-11, cut from origin/records-after-173, in its own worktree ~/.cache/forager-wt/topo-plan-2026-11, with any evidence in docs/plans/data/2026-11-01-own-topo/. Commit and push early and often: the last attempt was lost. No PR. Your final message: the answer in plain language first, then what is unknown.

## Rules
- Read only: no dataset downloads, no Pi, no phones, no app or map changes, no Gradle. Disk use small; the laptop is tight.
- Stop and report if an answer needs an account, an agreement, or a download.
- Describe products generically, except where a licence credit requires the name.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
