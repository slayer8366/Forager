# Dispatch 2026-11-05-11: the owner's home location out of the public repository (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2182. Every fact below was read at pre-main 779599c3f while this was written, and is a premise to re-check.

## Authorisation
The planner told the owner: "the repository on GitHub is public, and it contains coordinates matching your home to about 10 metres. They're in about 98 files on pre-main and 91 on main: test fixtures, two source files and some audit documents. Removing them would mean swapping in made-up test points across all those files, as a separate dispatch, and the old values would stay in git history. Do you want that done, or left as it is?"

The owner, verbatim: **"Replace them in the repo"**.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

**Nothing you push, and nothing in your report, may carry the home's coordinates, grid references, tile numbers or the ranges around them.** The search patterns live only in a private file on this laptop, outside every repository. The planner gives you its path. Never copy its contents into a commit, a PR, a report or a message. Refer to matches by kind and count only.

## What counts
Anything that places the owner's home to within about 2 km, by the private file's patterns:
- **(a)** decimal coordinates;
- **(b)** MGRS grid references;
- **(c)** map tile numbers at zoom 13 or more.
- **(d) Out of scope, but listed with counts for the owner:** street, neighbourhood and park names near home, and house numbers.

## Phase 1: inventory, read-only, report to the planner by message
Build nothing.
1. **Count every occurrence of (a) to (d)** on origin/pre-main and on origin/records-after-173, by file and by kind:
   - source;
   - test fixture;
   - test expected value (a distance, MGRS string, tile count or bearing asserted from it);
   - docs/audits;
   - scripts;
   - map-style or server files;
   - RECORD.md and prompts/preserved (records-after-173 only; the planner handles those).
   Give totals that add up, against the grep's own counts.
2. **For each source-file occurrence:** what it does (a default map centre, a doc example, a constant), and whether changing it changes anything a user sees. Anything visible is a stop for the owner.
3. **For tests,** propose the made-up point or points:
   - in the same country, map data area and kind of terrain where a test depends on it (routing, MGRS zone, tile coverage, a downloaded region, data present);
   - far enough from home to reveal nothing;
   - one shared fixture where possible.
   Say which expected values must be recomputed and how. Recompute, never loosen: no tolerance widened, no assertion dropped.
4. **Docs and audits on pre-main:** propose the replacement form. Recommended: a neutral marker such as "[a point near the owner's home; removed, 2026-11-05]", or the made-up point where a doc works through arithmetic on it.
5. **Overlap** with branches in flight: `edge-shade` (PR #495), `trip-download-maps` and the navigation card's coming branch. Propose the timing that avoids conflicts.
6. **main** changes only by promotion from pre-main (CLAUDE.md); say what the next promotion carries.
7. Any premise above that is wrong.

## Phase 2: on the planner's go
A branch from pre-main, with tests first where an expected value changes. CI green with counts from the JUnit XML, compared by test name with pre-main's run, so no test is lost. A final search shows zero occurrences of (a) to (c) outside git history. A PR into pre-main, ready, not merged.

## Stops
Stop and report on:
- anything a user would see;
- a test whose meaning depends on the real place and can't move;
- a need to touch another coder's open branch;
- a wrong premise;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, merged on the owner's word. The planner sweeps RECORD.md and prompts/preserved on records-after-173 to match.
