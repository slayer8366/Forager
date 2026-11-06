# Dispatch 2026-11-05-10, Phase 3 (G and H): hand-back (Hazel Cairnwright)

Preserved by the planner from the hand-back message, verbatim. The full report is docs/audits/2026-11-05-10-nav-card-kind-line-completion-report-gh.md on branch nav-card-kind-line (PR #498). RECORD -2181.

Hazel (#498), hand-back. G2, G3 and H1 to H9 are built and pushed. PR #498 is open and ready, not merged, head ba0724ead. Full CI is not green: 9 tests still fail, all from one open stop (item 1 below). Nothing was silenced.

**Base and branch.** I started from nav-card-kind-line 3a5e66fcc and pre-main 6cfcd8d6c. Pre-main was merged in twice, never rebased: d02d2e74a (7b561c5ac) and 61f99fbe2, which brought in PR #499 (de10e9266). Worktree `~/.cache/forager-wt/nav-card-kind`. Every file was checked against home-location-out and next-turn before editing; none was shared until you handed me #499's test files.

**Commits.**
- 4316283b1 and d3d7246c1: G2, G3 and H1 to H5, tests first. bfbef08c3: two test fixes.
- 7d9fe67c4 and 73b5c0812: H7, H8 and the shared files' claims, tests first.
- fb1f8b4d7 and 221e2b2d4: H7's decision on the squeezed form. I reverted it in bbd07d18d. That revert's message has no Co-Authored-By line; it stays as pushed.
- a02aa2a1b: a test change.
- adf8301ca and f68c2648d: H9, tests first, and the squeezed-form decision put back on your word.
- 051045aec, e249e4ac1 and ba0724ead: the report.

**Red, then green** (every log checked for compile errors before its results were read):
- Red: run 42234890856 at 4316283b1 had 138 failing, each for its own ruling. Run 42247946883 at 7d9fe67c4 had 55. Run 42259918386 at adf8301ca showed H9's 4 cases red.
- Final PR run 42259891512 at f68c2648d: 6953 tests, 6920 passed, 24 skipped, 9 failed. The later commits are report-only.
- By name against pre-main's 42245483101 (61f99fbe2: 6838 tests, 0 failed):
  - 36 names only on pre-main, renamed or replaced, each old claim quoted in its test;
  - 151 names only here;
  - 8 with a different outcome, the route cards below.

**Reverts.** Each was a throwaway ref, and each commit's diff was checked against its spec. Each fails only its own cases, or a dependency the report names:

| Revert | Run | Failed |
|---|---|---|
| G2 | 42238853162 | 20 |
| G3, turn line | 42238879324 | 29 |
| G3, top line | 42241837666 | 17 |
| H1, strip | 42238928731 | 22 |
| H1, card | 42238958256 | 19 |
| H2 | 42238990725 | 17 |
| H3 | 42239241348 | 4 |
| H4 | 42241866137 | 15 |
| H4, wrap | 42239059956 | 2 |
| H5 | 42239093160 | 8 |
| H5, break | 42239124524 | 27 |
| Q3 | 42239156664 | 28 |
| H7 | 42248061849 | 3 |
| H8 | 42248091897 | 17 |
| H9 | 42259930014 | 4 |

My revert script once dropped one of two edits made to the same file. I caught it by reading the revert diffs, re-ran both checks, and cite only the corrected runs.

**Widths and heights** (dp; full tables in the report):
- Left part at font 1.0, no evening line / "Dark since": 207.7 / 197.0 at 384 portrait, 183.7 / 173.0 at 360, 143.7 / 133.0 at 320, 176.3 / 165.7 in both landscape windows. The right part keeps 107.3 in daytime.
- Font 2.0: H8 applies everywhere except 384 portrait, whose status space (163.3) is wider than the widest word ("Approaching", 157.0).
- Card height at 1.0: 61.7 portrait and 60.7 landscape, with or without status words, so two lines tall. 78.0 at 320 with "straight" on its own line.
- H5's sunset line stays on one line only in wide windows: 480 dp in the measured set. Everywhere else it is split in two.

**Room beside the needle** (left part minus the 54.0 turn line at 1.0; the next turn needs about 75):
- No evening line: 153.7 at 384, 129.7 at 360, 89.7 at 320, 122.3 in landscape.
- With "Dark since 5:23 PM": 143.0, 119.0, 79.0 and 111.7.
- 320 with "Dark since 10:23 PM": 71.7, short of 75.
- Font 2.0 (needs about 120): 71.0 wherever the left part is at its narrowest; 170 to 210 in daytime where H8 applies.

**Open:**
1. **Stop, the route cards.** Still red: RouteCardExpandShortLandscape (3), RouteCardOnNavigationCardFont20Evening and ShortFont20AfterSunset, RouteCardSundownStepsAsideShort (3), and my LargeText turning case at 823×384, 2.0, with no compass. H9 cleared only Font13Evening. The rig's card now has two lines under the parts at font 2.0 ("No fix … min", made very long by the rig's clock, and "No compass") where it had one before H3. So the evening line steps aside and the route card has no room for its rows or Save. The choice of fix is yours or the owner's.
2. H7's squeeze at 320 dp: the heading appears or drops out as the phone turns ("89° E" fits, "281° W" doesn't). At 320 and 360 a heading change moves the others by a few dp; at 384 and 412 nothing moves. This is written into device-check step 2.
3. The strip's navigating-mode code has no production caller; deletion is a follow-up, as you said (Q9).
4. `turnWords` is no longer read anywhere on screen.
5. The strip's position-note row has no windows.

**Report:** the main completion report gains a short pointer section; the detail is in `docs/audits/2026-11-05-10-nav-card-kind-line-completion-report-gh.md`, split off because the main file was 726 lines. It has the four disclosure sections, the widths, the revert table and the S22 device check, cheapest first.

**Clean-up:** every `nav-card-gh-*` ref is deleted (none left on origin). `/mnt/work/scratch/nav-card-gh/` is deleted. One file I created by mistake directly in `/mnt/work/scratch/` was moved into it, so it went too. No row was added to `docs/audits/README.md`. No coordinate other than P was added, and no grid reference was printed. No git stash: one attempt was blocked and I worked round it with a merge.
