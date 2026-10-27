# Dispatch 2026-10-27-06: when there are more map icons than the spiral can fit, report first (a coder, Opus; report only)

Written by Rowan Waymark, 2026-10-27, against pre-main 6064fef86 or later. RECORD -1734.

**Authorisation.** The owner, from an S26 screenshot at country zoom where the fan of icons and its lines run far off the screen edges, verbatim: "with so many icons on the map, it goes off the edge. Can we find a way to reduce the chaos when there are more icons than the spiral can fit?" The owner: "Report first". Build nothing.

## Report
1. **How it works today,** with file:line:
   - how icons group;
   - when a tap fans or spirals them;
   - its size limits, and what happens past them;
   - how it differs by zoom;
   - the Back behaviour.
2. **What counts in a group:** finds, photos, waypoints, journal entries. Note that find-attached photos are leaving the map (dispatch 2026-10-27-05, running now): say how much that alone reduces the screenshot's case.
3. **Price these options,** each as a tap > shows > Back step path with a small text sketch, by what the user must notice or decide ("Don't make me think", CLAUDE.md) and then by build cost:
   - **A. Zoom in, then spiral:** tapping a crowded group zooms the map to it until it splits or fits on screen. All on one spot at full zoom > a list.
   - **B. A limit plus a list:** the spiral shows as many as fit on screen, plus "+N more" > a list > tap one > it opens > Back.
   - **Any third option** the code suggests, such as grouping at low zoom into a count bubble.
4. **Real touch:** say which option needs real-touch tests on the map (CLAUDE.md, the touch-interception pitfalls), and how 80% chrome applies to any list.
5. **The four disclosure sections.**

Write the report to docs/audits/2026-10-27-spiral-overflow-report.md on a new branch, spiral-overflow-report, and push it. No device; CI only if a test is worth running. Network: none beyond GitHub. Never read, name or quote the Restricted Object. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
