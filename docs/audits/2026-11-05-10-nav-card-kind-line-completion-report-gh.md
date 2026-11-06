# The navigation card, Phase 3, G and H: completion report (dispatch 2026-11-05-10, Amendment 1, G2, G3, H1 to H8)

Written by Hazel (Opus), 2026-11-06, the second Phase 3 session; a companion to
`docs/audits/2026-11-05-10-nav-card-kind-line-completion-report.md`, whose Phase 3 (B3, B4, F2) it follows. Split off because that file
was 726 lines (the dispatch: "if a file passes about 800, split it before adding to it"). Amendment 1 is
`prompts/preserved/2026-11-05-10-amendment-1.md` on `origin/records-after-173` (19960fb5b at the start; H7, H8 and the planner's readings
came by message while this ran). Marks: [observed] is a CI run's XML or log read this session (each log checked for compile errors
first), [read] is code read at the commit named, [inferred] is worked out and not measured.

## The owner's words (verbatim)

- G1: "While we're at it, have all words, figures, and metrics have their own space, centered, so that jumping can be reduced. …
  Maybe remove the words "right/slight right/sharp right" and so on. The compass already points in the direction they need to go".
- G2: "Drop the words, keep the degrees (Recommended)". G3: "The turn line", "The top line", "The status line", "The evening line".
- H1: "Keep the compass strip the exact same when in navigation mode as out; so keep the north compass out of the navigation panel.
  The approaching can move one line up, and the panel can fit the middle divider neatly now."
- H2: "Under the evening line (Recommended)". H3: "In the status space (Recommended)".
- H4: "Shorten the lines to just Off trail since it's obvious what to do from there. Wrap the text to "Trail routes not" and
  "downloaded" goes on the next line."
- H5: "Shorten to Sunset: 4:52 PM ▪︎ Dark: 5:23 PM status words are on the right panel"; then "Split it at the ▪ (Recommended)".
- H7: "Info that changes and shares a line with other text should get its own window so it doesn't shift all text on the same line";
  "Card and strip now, rest after (Recommended)"; "They fit now, even at the fullest length of all text. So it shouldn't have
  problems"; then "If it's too small, then have them squeeze together to make room. It fit before, so it should still fit now".
- H8: "Own line when too narrow (Recommended)". C1: "Keep the text size the same size".

## Base and commits

- Started from `origin/nav-card-kind-line` 3a5e66fcc and `origin/pre-main` 6cfcd8d6c [observed]. pre-main moved twice and was merged
  in, never rebased: d02d2e74a (CLAUDE.md, 7b561c5ac) and 61f99fbe2 (PR #499, the home location out, de10e9266).
- Worktree `~/.cache/forager-wt/nav-card-kind`, clean at the start. Every edited file was checked against `origin/home-location-out` and
  `origin/next-turn` before editing; none was shared until #499 merged and the planner handed its nine test files to this branch.

| Commit | What |
|---|---|
| 4316283b1 | Tests first: G2, G3, H1 to H5 (and the claims that pinned A2, A4 to A6, the turn words, the card's heading). |
| d3d7246c1 | G2, G3, H1 to H5. |
| bfbef08c3 | Two test fixes (the strip-same check where the line is short; lint, UnrememberedMutableState). |
| 7d9fe67c4 | Tests first: H7, H8, and the shared files' claims (G2, H1, H3, H5). |
| 73b5c0812 | H7 (the strip's windows, squeezed) and H8 (status words under the parts at large text). |
| fb1f8b4d7, 221e2b2d4 | H7 at 360 dp: the claim, then what the line keeps decided on the squeezed form. |
| bbd07d18d, a02aa2a1b | 221e2b2d4 reverted (git revert; its message lacks the Co-Authored-By line, left as pushed), and the 360 dp claim. |

## What was built [read, at 221e2b2d4]

- **G2** (`domain/NavigationWords.kt`): `turnText` is `turnDegrees(turnFor(…))`, "169°". `turnWords` keeps the bands' words, unread on
  screen.
- **G3, the turn line** (`NavigationCardParts.kt`, `CardTurnLine`): the degrees centred in a space as wide as the widest of "0°" to
  "180°" (28.0 dp at 1.0, 54.7 at 2.0), 4 dp, the 22 dp needle; one width in every state. "Target" and "No target" are centred on the
  same space (Q5).
- **G3, the top line** (`NavigationCardTopLine.kt`): the line is divided at the half, or past it for the longest figure (A4's sizing);
  the distance is centred in its space, the kind in its; the stack is centred in the kind's space, each word centred, "trail" on the
  distance's baseline; with the kind on its own line each is centred on its line (Q4). The left part is never narrower than the widest
  figure either kind could show (`longestCardFigurePx`), so it keeps one width; the step is decided on it as drawn (D1 moot).
- **H1** (`CompactMapTopStrip.kt`): the strip is passed `navigating = false`, so it is the strip out of navigation; the card draws no
  north arrow, heading or crossed-out compass. The strip's navigating-mode code (`StripNavigatingReadout`, the `navigating` branches)
  has no production caller now; left in place so H1 reverts in one line (Q9), its deletion a follow-up.
- **H2** (`NavigationCardRightPart.kt`): the status words in the right part, under the evening lines, or on its second line with none;
  the route message and Try again keep their own line under the parts.
- **H3**: `compassNoticeText` ("Compass calibrating…", "Compass unavailable · north up") on its own line under the status words (Q6).
- **H4** (`NavigationReadout.kt`): "Off trail" on the way to a waypoint; the two notes that stay without "· straight line"; a status
  with no droppable form wraps, centred (Q8).
- **H5** (`NavigationCardEvening.kt`): before sunset "Sunset: 4:52 PM ▪︎ Dark: 5:23 PM" (the owner's U+25AA U+FE0E), no countdown;
  "Sunset: 4:52 PM" with no dusk; the phone's 24-hour form; the other forms unchanged. Broken at the square unless the one line fits
  whole beside the left part's widest need with the next-turn space reserved (4 + 22 + 4 + "88.8 mi", 75 dp at 1.0). The right part
  keeps the broken form's wider line on the phone's clock in daytime (Q3: 107.3 dp at 1.0 on a 12-hour clock).
- **H7** (`CompassStripWindows.kt`, `AvailabilityMapControlsUi.kt`): each value on the strip's line centred in a window sized for the
  widest it can hold (heading 150.3 dp at 1.0 with the compass words, or its widest value 61.3 where the crossed-out compass stands in;
  height 58.0, 81.7 with "Alt", 105.7 for "Alt unavailable"; the grid reference the wider of its forms here, 140.3 at P). What the line
  keeps is decided on the values as drawn with today's gaps (RECORD -713, as before H7). Where the line is short the
  windows squeeze in two steps: the statuses' room and the gaps (8 to 4 dp) in proportion, down to the widest values, where nothing
  depends on the values shown; then toward the values shown. The landscape strip keeps its width (RECORD -951).
- **H8** (`NavigationCardStatusPlace.kt`): per window and font, where the status space is narrower than the widest word of any status
  ("Approaching", 79.3 / 157.0 dp), the status words and the notice take their own full-width line under the parts, and the right
  part keeps no daytime width.

## Widths and heights [observed, runs 42222326188, 42235006102, 42247940356]

The left part, the right part's daytime width, and whether H8 applies (dp):

| Window | Font 1.0: left part (no evening / "Dark since") | Right part, daytime | Font 2.0: left part (no evening / "Dark since") | H8 at 2.0 |
|---|---|---|---|---|
| 384 portrait | 207.7 / 197.0 | 107.3 / 211.7 | 151.7 / 151.7 | no (space 163.3 ≥ 157.0) |
| 360 portrait | 183.7 / 173.0 | 107.3 / 0 | 291.0 / 151.7 | yes (139.3) |
| 320 portrait | 143.7 / 133.0 | 107.3 / 0 | 251.0 / 151.7 | yes (99.3) |
| 823 × 384 | 176.3 / 165.7 | 107.3 / 0 | 263.0 / 151.7 | yes (111.3) |
| 780 × 360 | 176.3 / 165.7 | 107.3 / 0 | 263.0 / 151.7 | yes (111.3) |

- H8 never applies at font 1.0 (space 107.3 ≥ 79.3). Font 1.3 was not measured.
- The kind's step at 1.0: beside everywhere but straight at 320 dp (its own line); by trail stacked at 320 dp with "Dark since 10:23 PM".
  At 2.0: beside with no evening line under H8 (straight on its own line at 320), on its own line with an evening line, and at 384 on its
  own line in daytime too (the daytime width stays there).
- Card heights at 1.0: 61.7 dp portrait and 60.7 landscape, with or without a status, before sunset too (two lines tall); 78.0 at 320
  dp straight (the kind's own line). At 2.0, 384 dp: 121.7, 136.3 before sunset; the note that stays, 3 lines, 136.3.
- H5's one line shows only where the parts are wide: at 480 dp, font 1.0, in the measured set; the broken form everywhere else.
- **Room beside the needle** (the left part less the turn line, 54.0 at 1.0, 80.7 at 2.0), for dispatch 2026-11-06-01's next turn
  (about 75 at 1.0, 120 at 2.0, its Phase 1): at 1.0, 153.7 (384), 129.7 (360), 89.7 (320), 122.3 (landscape) with no evening line;
  143.0, 119.0, 79.0, 111.7 with "Dark since 5:23 PM"; 71.7 at 320 with "Dark since 10:23 PM", short of 75. At 2.0, 71.0 wherever the left
  part is its widest figure (every evening line, and 384 in daytime): short of 120 everywhere; 170.3 to 210.3 in daytime under H8.

## Evidence

- **Red**, tests first: run 42234890856 at 4316283b1, 138 failed, each for its own ruling (the old words, arrows, placements, the card's
  heading, the countdown); run 42247946883 at 7d9fe67c4, 55 failed (H7's windows, H8's places, the route cards still red then).
- **Green**: run 42235006102 (d3d7246c1), PR run 42238811667 (bfbef08c3): 6940 tests, 46 failed, all in the then open questions; run
  42247940356 (73b5c0812): 6953 tests, 11 failed, 24 skipped (below).
- **Reverts**, each a throwaway ref over the change, its diff checked against its spec, its log free of compile errors, read against
  the green baseline. 310 tests in 26 classes for G and H1 to H5 (over bfbef08c3), 247 in 12 for H7 and H8 (over 73b5c0812):

| Revert | Edit | Run | Fails |
|---|---|---|---|
| G2 | `turnText` back to the words | 42238853162 | 20, the words; one G3 case at 2.0, where "sharp right 169°" runs off the left part and its bounds are cut |
| G3, turn line | the degrees' space unsized | 42238879324 | 29, the needle's place (and the rules' needle control) |
| G3, top line | the distance and the kind back to the meeting point | 42241837666 | 17, the centring |
| H1, strip | `navigating = isNavigating` | 42238928731 | 22, the strip's arrow and heading |
| H1, card | a north arrow back on the needle's square | 42238958256 | 19, the arrow in the card |
| H2 | the status space under the parts | 42238990725 | 17, H2's places and heights, and H4's wrap (full width holds it on one line) |
| H3 | no notice | 42239241348 | 4 |
| H4 | the words with their tails | 42241866137 | 15 |
| H4, wrap | `maxLines = 1` | 42239059956 | 2 |
| H5 | the countdown form | 42239093160 | 8, H5's and the heights that rely on its two lines |
| H5, break | never break | 42239124524 | 27, the one line's width squeezing the left part |
| Q3 | no daytime width | 42239156664 | 28, Q3's and every daytime status-space case |
| H7 | windows at the values' widths | 42248061849 | 3 |
| H8 | never under the parts | 42248091897 | 17, H8's and the route cards it turned green |

- A tool slip, caught: the revert script first dropped one of two edits to a file (G3's top line, H4); found by reading each revert's
  diff against its spec, fixed, re-run; only the corrected runs are cited.

## Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base and merges; each red for its own ruling; each revert failing its own cases (with the cross-ruling
  dependencies named above); the widths and heights above; H8's windows; the strip identical in and out of navigation.
- **Inferred**: that the S22 draws about 1% narrower, so H7's 384 dp case fits there with more room than on CI.
- **Could not determine**: the S22's own widths (no device this session); font 1.3 beyond the route-card tests.
- **Premises that were wrong**:
  - "They fit now, even at the fullest length of all text" (H7): at 384 dp the windows at their widest need 436.3 dp of 314, and at their
    widest values 323.3 with "Alt"; the owner then ruled the squeeze.
  - The one-line sunset form (H5) fits in none of the measured windows with the next turn reserved; the owner ruled the split.
  - The route-card reds were expected to clear under H8; 10 of 13 did not (open, below).

## Decided beyond the dispatch

- H7's squeeze in two steps (statuses' room and the gaps first, then toward the values shown), so nothing moves while the line holds
  the widest values; the gaps' least is Spacing.xs.
- What the strip keeps is decided as before H7 (today's gaps), not on the squeezed form the planner's reading named. Tried first
  (221e2b2d4, run 42253096525): with the gaps at their least the heading at 320 dp fits or not with its own width ("281° W" no,
  "89° E" yes), so it came and went as the phone turned, where before it stayed out; reverted. Kept as before, "Alt" at 360 dp comes and
  goes with the heading's width, as it did before H7, and the line moves when it does. A question for the planner (open item 5).
- G3: the stack's two words each centred in it (A6 had them start at the meeting point).
- The right part never narrower than H5's floor once an evening piece is wider ("Dark since 10:23 PM" widens it; reported, as D2).
- The left part sized for the widest figure either kind could show (G3), so where room is short it no longer follows the figure shown.
- This report in its own file (the size rule).

## Open

1. **The route-card reds (stopped, waiting for the planner):** RouteCardOnNavigationCardFont13Evening, Font20Evening,
   ShortFont20AfterSunset, RouteCardSundownStepsAsideShort (3), RouteCardExpandShortLandscape (3), and the LargeText turning case at
   823 × 384, 2.0, with no compass. The rig navigates with no compass and a stale fix: H3's notice now takes its own line or lines
   (under the parts at 2.0, in the status space at 1.3), so the landscape card is 132 dp at 1.3 (90 before, with the evening line) and
   182 at 2.0 (198 before, with it); the evening line steps aside (RECORD -955) and at 640 × 360 the route card has no room. Not silenced.
2. The strip's navigating-mode code with no production caller (Q9): delete after the merge.
3. `turnWords` unread on screen since G2.
5. **H7's decision rule** (for the planner): as before H7 (built: 320 stable, "Alt" at 360 coming and going with the heading's width),
   on the squeezed form (tried: the heading at 320 coming and going), or on the windows' widest values (stable everywhere, but the
   landscape strip, kept at RECORD -951's width, is 0.9 dp short of them at 823 × 384, so the heading would drop there: a new drop).
4. The position-note row on the strip (heading and "Approximate …") has no windows; H7 named the heading, the height and the grid
   reference.

## Device check (S22), cheapest first

A combined build (`tools/s22-combined` with this branch at its head), green on CI, before the phone is asked for. The phone's own font
unless a step says.
1. **The strip in and out of navigation**, portrait then landscape: the arrow, heading, "Alt", height and grid reference in the same
   places navigating and not. Evidence: two screenshots each; sample the heading's and the grid reference's edges.
2. **Turn the phone** (navigating, portrait): nothing beside the heading moves; the needle stays put as the degrees change; no turn
   words. Evidence: a short screen recording.
3. **The card two lines tall**, the divider its full height, "Approaching" under the evening line (or on the right part's second
   line in daytime), left of the X. Evidence: a screenshot.
4. **The compass notices** in the status space (cover the compass, or a phone with none). Pass: under the status words.
5. **"Off trail"** on the way to a waypoint off the line; **"Trail routes not" / "downloaded"** where a region's routes were never
   fetched (the no-changes rule: ask first).
6. **Before sunset**: "Sunset: … " / "Dark: …" on two lines, no countdown.
7. **The fixed places**: the distance and "by trail" each centred in their halves as the distance counts down; Arrived and the dash
   centred (B3, B4); a note that only says why leaves no status line (F2).
8. **Largest font**, landscape: the status words on their own line under the card, "by trail" beside the distance in daytime.
9. Observation, not a gate: font 1.3 in landscape with a struggling compass (open item 1).

## Clean-up

- Throwaway refs: every `nav-card-gh-*` ref (measure, probe, diag, green, green2, wip, and the revert refs) deleted after its run.
- Scratch: `/mnt/work/scratch/nav-card-gh/` deleted at the end.
- No index row added to `docs/audits/README.md`. No coordinate beyond the made-up P; no grid reference printed.

## Addendum: H9 and H7's decision (later the same day)

- **H9**, the owner: "Shorten to "No compass" (Recommended)". The card's north-up notice reads "No compass" (`CARD_NO_COMPASS_TEXT`);
  "Compass calibrating…" and the strip's own words unchanged. Tests first adf8301ca (red, run 42259918386: the notice's 4 cases for
  their own reason), change f68c2648d; revert (the notice back to "Compass unavailable · north up", run 42259930014) fails exactly those
  4 [observed].
- **H7**: on the planner's word, what the strip keeps is decided on the squeezed form again (bbd07d18d's revert reverted in f68c2648d).
  So "Alt" is kept at 360 dp, and at 320 dp the heading is kept where the squeezed line holds it ("89° E" yes, "281° W" no): it comes
  and goes as the phone turns there. Device check step 2 should expect, at 320 and 360 dp, a few dp of movement as the heading changes,
  and at 320 dp the heading appearing and going; at 384 and 412 dp, none.
- **Final run** 42259891512 at f68c2648d: 6953 tests, 6920 passed, 24 skipped, **9 failed**. Against pre-main's 42245483101 at 61f99fbe2
  (6838, 0 failed) by name: 36 only on pre-main (renamed or replaced, each old claim quoted in its test), 151 only here, and 8 whose
  outcome differs: the route cards below.
- **Still red, the stop stands:** RouteCardExpandShortLandscape (3), RouteCardOnNavigationCardFont20Evening and ShortFont20AfterSunset,
  RouteCardSundownStepsAsideShort (3), and NavigationCardKindLineLargeTextTest's turning case at 823 × 384, 2.0, with no compass. H9
  cleared Font13Evening only. The rig's state still has two lines under the parts at 2.0 where it had one before H3 ("No fix … min",
  which the rig's clock makes very long, and now "No compass"), so the evening line steps aside and the route card has no room. Not
  silenced; for the planner.
