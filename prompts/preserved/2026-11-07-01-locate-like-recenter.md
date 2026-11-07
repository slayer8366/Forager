# Dispatch 2026-11-07-01: the locate icon glides and zooms like Re-center (Hazel, Opus)

Written by Rowan Waymark, 2026-11-07T16:20:51Z. RECORD -2189. Base: origin/pre-main (check against the remote).

## The owner's words
- **"The GPS icon does take us back to the center, but it snaps instantly and zooms out. Meanwhile the Re-Center button animates back to place, and stays at the same zoom level. Can you fix this?"**
- When: **"Always"**. The zoom: **"Re-center's zoom"** ("It uses whatever zoom Re-center uses during navigation, so both behave identically").
- The step path, confirmed **"Yes the path is correct. Record and build it"**: tap the locate icon ("Center on my location", the icon bar's locate row), anywhere, any time → the map glides to your position → the zoom follows exactly the rule Re-center uses during navigation → the map then follows you.

## Before building (report these first, in the report; stop if a premise is wrong)
- Trace what the locate row's tap does today (the snap and its zoom) and what Re-center's tap does (its camera move, its duration, its zoom rule, including any limit when zoomed far out), naming files and lines.
- PR #506 (journal-locate-outline) changes the locate row's tap in landscape navigation (the ringed locate calls Re-center's action). If the locate tap's files are in #506's diff, build on top of #506's branch (merge origin/journal-locate-outline in, merge never rebase) and say so; this PR then merges after #506.

## What to build
- The locate row's tap uses Re-center's camera move and zoom rule, the same code path (reuse, not a copy), on every screen the icon bar appears (Maps tab and the journal entry's full-screen map), navigating or not; then the map follows the position as it does after a locate tap today.
- Nothing else changes.
- Tests first through the real screen (a coordinate touch on the locate row; the camera's move animated, not instant; the zoom per Re-center's rule, including from far out), one revert, full CI by name, a completion report with the four disclosure sections and S22 steps. New branch locate-like-recenter; PR ready, not merged.

## Rules
No Gradle on the laptop; CI only. Scratch under /mnt/work/scratch, never /tmp. Never reset, rebase or amend; merge, never rebase. Commits end exactly `Co-Authored-By: Claude <noreply@anthropic.com>`, no model name. Scan every push; 0 required. One coder per file against nav-card-kind-line, next-turn, route-card and journal-locate-outline. Stops: a shared file not covered above; anything a user would see beyond this; a wrong premise; two failed attempts.
