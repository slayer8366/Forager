# Dispatch 2026-11-06-04: an outline around the Re-center button (Hazel, Opus)

Written by Rowan Waymark, 2026-11-06 (Pacific). RECORD -2186. Base: origin/pre-main 26a5c5955 or later; check it against the remote.

## The owner's words
- **"Add real quick: an outline around the re-enter button"** (the map's Re-center button).
- To "What should the Re-center button's outline look like?": **"Same as the chips"**. The option read: "1 dp, Bark #3B2E24 by day, pale #EDE3D0 at night, like the selected map type chip's outline".
- To "Where should it go?": **"Its own small PR"**. The option read: "A small PR to pre-main, so it doesn't hold up #498 or #500".

## What to build
- The Re-center button gets a 1 dp outline, Bark #3B2E24 by day and #EDE3D0 at night, using the same colour source the selected map type chip's outline uses (find it; reuse, don't copy the hex). Its fill, size, place, opacity (map chrome 80%, CLAUDE.md) and touch area are unchanged.
- Find every place the Re-center button is drawn (portrait, landscape, fullscreen) and name each in the report; all get the outline.
- Tests first: the outline's width and colour in day and night, red before, green after; one one-line revert with the compile-log check and restore-from-copy; a coordinate touch on the button still reaches it (CLAUDE.md: a semantic click proves wiring, not routing). Full CI green, compared by test name with pre-main's latest run.
- New branch recenter-outline from pre-main; a completion report in docs/audits/ with the four disclosure sections; PR ready, not merged. Device check steps for the planner (day and night, the outline's colour sampled).

## Rules
- One coder per file: Hazels have PR #498 (nav-card-kind-line) and #500 (next-turn) open. `git diff --name-only origin/pre-main...origin/<each>` before each edit; a shared file is a stop.
- No Gradle on the laptop; CI only. Scratch under /mnt/work/scratch, never /tmp. Never reset, rebase or amend; merge, never rebase.
- Commits end exactly `Co-Authored-By: Claude <noreply@anthropic.com>`, no model name. Scan every push with `python3 -I ~/.forager-private/scan.py stdin-diff`; 0 required.
- Stops: anything a user would see beyond the outline; a shared file; a wrong premise; two failed attempts at one symptom.
