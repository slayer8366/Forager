# Dispatch 2026-10-30-06: the router keeps trail heights, and the way home uses them (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1836. Cut from **pre-main 3c5e63468**; verify that is still the remote head.

**Authorisation.** The owner, verbatim:
- "Good news, elevation is already built in"
- "Drop it, use trail data (Recommended)"
- "Yes, start (Recommended)" (step 4a now)
- "Yes, real trail heights (Recommended)", whose option read: "The climb on the trail part of the way home comes from the trail data. Where there are no heights, it falls back to your track as today. The \"start back by\" time can change slightly."

**Read first:**
- Aspen's note, Forager-cartography main 9d72724, docs/handoffs/2026-10-30-elevation-in-trail-data.md. Read it with `gh api "repos/slayer8366/Forager-cartography/contents/docs/handoffs/2026-10-30-elevation-in-trail-data.md?ref=9d72724" -q .content | base64 -d`.
- RECORD -1833 and -1835, the route plan's Addendum 3, and docs/navigation/2026-10-26-climbing-allowance.md ("For the elevation work").

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What exists (re-check each line)
- routing/BRouterTrailRouter.kt maps the engine's nodes to positions only and drops their heights.
- TrailRoute (domain/routing/TrailRouter.kt:41-45) has points and lengthMetres, and no heights.
- domain/wayhome/WayHomeHeights.kt gives the trail part a stand-in (the walker's own track) and says "the route files carry no elevation". That has been false since 2026-10-28.
- Live route squares carry 3″ heights (run usca-20261027T221400Z). Five pinned squares have none. Data downloaded before 10-28 has none.

## Change
1. **The router keeps heights.** Each route point carries the engine's height, or "no height" where the engine gives none. Never 0 for missing (Aspen: "treat missing heights as missing, not as zero").
   - TrailRoute gains the heights in a form that leaves existing callers compiling unchanged. Open PRs #445 and #446 build on TrailRoute, so don't break their code.
   - Say in the first stop how a square without heights shows up in what the engine returns.
2. **The way home's trail part uses the real heights.** Where every point of the trail part has a height, those heights replace the stand-in. Otherwise, fall back to the stand-in as today, and log that once.
   - The climbing allowance's banking (4 m hysteresis), and the no-barometer rule for the track parts, stay as they are.
   - Say in the first stop what "partly heighted" should do (some points have heights, some don't). Propose; don't decide.
3. **Fix the stale text** in WayHomeHeights.kt's header and any other comment saying the route files carry no elevation.
4. **Not in this dispatch:**
   - storing heights for saved plotted routes (that needs #445 merged and a 23→24 migration, as a later step);
   - the route card;
   - `consider_elevation`, which stays off (Aspen: changing it changes which way a route goes, and needs its own report and go).

## Stop first (look only, no code)
Write docs/audits/2026-10-30-trail-heights-first-stop.md covering:
- (a) where the engine's heights are and what a missing one looks like;
- (b) the TrailRoute change, and every caller;
- (c) the way-home change, the fallback rule and the partly-heighted proposal;
- (d) tests, including one fixture route file with heights and one without, and where they would come from;
- (e) files over 800 lines touched.
Push it, and wait for the planner's amendment.

## After the amendment
- Tests fail first, then revert checks, one per rule. Build logs must have no compile errors. Restore from a saved copy, never from git.
- New files stay under 400 lines.
- CI only. Open a PR into pre-main, with no merge.
- The device check comes later: one route on the S22 after a fresh route-data download, on the planner's word.

## Rules
- Branch `trail-heights`, worktree ~/.cache/forager-wt/trail-heights.
- Network: GitHub only. gh is logged in; run gh commands bare.
- The Restricted Object: never read, name or quote it.
- Past about 200k tokens: push and hand off.
- Ambiguity: stop and report.
