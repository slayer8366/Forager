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

## E13: H10 and H7 refined again (a fresh session, the same day)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E13: "H10 and H7 refined again ... in the same PR by the same practice, until
full CI is green." Marks as above: [observed] is a CI run's XML or log read this session (each log checked for compile errors first, and
each run's head SHA checked against the commit), [read] is code read at the commit named, [inferred] is worked out and not measured.

### The rulings (verbatim)

- H10, the owner: "The more urgent one (Recommended)". The option read: "Where status words take their own line (large text), only one
  shows: "No fix 6 min" ahead of "No compass". The card stays one line taller at most, and the route card keeps its buttons. At normal
  text both show as now." The planner's reading: "under H8, one status line at most, by the existing priority. Fix-age warnings come
  ahead of the compass notice, which comes ahead of other words."
- H7 refined again (the planner's call): "whether the heading stays on the strip is decided on its widest reading, so it never appears
  and drops out as the phone turns. At 320 dp it stays off, as before this dispatch."

### Base and commits

- Checked against the remote at the start [observed]: `origin/nav-card-kind-line` ba0724ead, `origin/pre-main` 61f99fbe2, already merged
  in; worktree clean. The other Hazel's branch `next-turn` touches no file this section edits (its diff from its base read: `domain/nextturn/`,
  AppContainer, the two use cases, WayHomeRouting).

| Commit | What |
|---|---|
| e9f38eac6 | Tests first: H10 (`NavigationCardOneStatusLineTest`, new) and H7 refined again (the 320 dp strip case's claim). |
| 160d4c6b3 | H10 (`oneStatusLine`, the readout's `statusIsFixAge`) and H7 refined again (`headingFitPx`). |
| 5427954d2 | Three route-card preconditions' claims, now that the 640 x 360 evening line shows (below). |

### What was built [read, at 5427954d2]

- **H10** (`NavigationCardStatusPlace.kt`, `oneStatusLine`; `NavigationCardRightPart.kt`, `StatusSpace(oneLine)`; `NavigationInfo.kt`): where
  H8 puts the status words under the parts, one line shows: the status words alone where they warn of the fix's age (stale or lost,
  `NavigationHudReadout.statusIsFixAge`, set in `NavigationReadout.kt` as `freshness != FRESH`); else the compass notice where there is one;
  else the status words. In the right part (normal text) both still show. The status words are already the readout's one status, with a
  fix's age ahead of every other word, so "the existing priority" is that order plus the notice between.
- **H7 refined again** (`AvailabilityMapControlsUi.kt`): what the strip keeps (`readoutsFitBeside`) counts a heading reading, a value or the
  dash, at the widest value (`stripHeadingValuesPx`, as the windows already size it); the compass words and the crossed-out compass, which
  do not change as the phone turns, are counted as drawn. Everything else in the decision is as before (the squeezed gaps, RECORD -713's
  order).

### Evidence

- **Red, tests first**: run 42278345300 at e9f38eac6 [observed], 6957 tests, 13 failed: the 9 failing at ba0724ead (run 42264328834, read
  first: 6953, 9 failed, 24 skipped) and the 4 new claims, each for its own reason: the three H10 cases ("the status space shows [No fix
  for 6 min, No compass], not [No fix for 6 min] or [No fix 6 min]"; "[Approaching, No compass], not [No compass]"), and 320 dp ("facing
  89°: the heading <89° E> is drawn"; "the phone turning: <compass-strip-elevation> moved from 84.5.dp to 113.83333.dp"). The font 1.0
  control (both lines in the right part) passed, as designed: H10 does not touch it, so it passes before and after and proves only that
  normal text is unchanged.
- **Green**: run 42278603468 at 160d4c6b3 (a throwaway ref, so as not to cancel the red run): the four new claims pass and six of the nine
  route-card reds clear. Then run 42282412164 at 5427954d2, the branch's full CI: **6957 tests, 6931 passed, 24 skipped, 2 failed**, the
  two stops below. "Build, test, publish APK" and "The app builds and its tests pass with the Street map deleted" fail on those two; lint,
  release compile, styles, Worker, route cutter and the debug-sources check pass.
- **Against pre-main** (run 42245483101 at 61f99fbe2, 6838 tests, 0 failed), by name: 36 names only on pre-main (renamed or replaced earlier
  in this PR, as the G and H section says), 155 only here, **0 names whose outcome differs**. Both failures are names that exist only on
  this branch.
- **Reverts**, each a one-line edit in a throwaway worktree, its diff checked to be that one line, restored from a copy saved before the
  edit (never from git), the forward line confirmed present in the branch worktree afterwards (grep, 1 match each), and each log checked
  for compile errors (0) before its XML was read. Each over 5427954d2, 367 tests in the NavigationCard, RouteCard, CompassStrip,
  AvailabilityScreenNavigationView and AvailabilityScreenMapIconStack classes:

| Revert | Edit | Run | Fails |
|---|---|---|---|
| H10 | `StatusSpace(readout, oneLine = true)` back to `StatusSpace(readout)` | 42282443780 | 14: H10's 3 cases ("[No fix for 6 min, No compass]", "[Approaching, No compass]"), the 9 route-card cases back to two lines under the parts ("leg row 0 shows, height 0.0.dp"; "sundown line ... expected:<1> but was:<0>"), and the two standing stops |
| H7 refined again | `listOf(headingFitPx, elevationPx)` back to `listOf(headingPx, elevationPx)` | 42282464055 | 2: the 320 dp case ("facing 89°: the heading <89° E> is drawn") and the standing 823 x 384 stop; the 360 dp case passes again, so its failure (stop 1) is this ruling's |

### Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

- `CompassStripWindowsTest`, 320 dp: now "the heading stays off as the phone turns, and nothing else moves" (drawn in no state, facing 281°
  or 89°, at either height; the height and the grid reference do not move as the phone turns). Was "short of room, the line keeps the
  height and the grid reference as the heading changes", which claimed only those two were kept. The 360 dp case gained a print of what
  the line keeps; its claim is unchanged (and is stop 1).
- `RouteCardOnNavigationCardShortFont20EveningTest`, `sundownLineShown = false` to `true`; `RouteCardSundownStepsAsideShortTest` and
  `...ShortNoElevationTest`, `lineExpected = false` to `true`. With H10 the rig's lost fix and missing compass take one line under the parts,
  not two, so the evening line now fits at 640 x 360, font 2.0, in the evening, by RECORD -955's rule, and D42 steps it aside when the route
  card opens. Each old claim is quoted in its doc. Note for the first: with the line shown, the class's expanded-top check is the evening
  one already used by every other evening class there ("rises over the strip"), not the noon one ("to 8 dp from the top"), which that file
  records as waiting on the owner.

### Stops (reported, not decided)

1. **H7 refined again takes "Alt" off the strip at 360 dp, in every state** [observed, run 42278603468: "the line keeps [heading, elevation,
   coordinates]" facing 281°, facing 89° and at 3000 m]. Counted at its widest, the heading leaves no room for the label there. Before
   this dispatch "Alt" came and went at 360 dp with the heading's width; at ba0724ead it was kept in every measured state. The ruling
   names only the heading and 320 dp, so whether "Alt" may go at 360 dp is not mine to decide. Built as stated (stable: nothing comes and
   goes as the phone turns); the 360 dp test still claims "Alt" and is red. **For the owner:** "On a 360 dp phone, the strip can keep the
   heading steady only if the "Alt" label goes for good (the height itself stays). Should it: (a) drop "Alt" there, so nothing changes as
   you turn; or (b) keep "Alt" where the heading as shown leaves room, so "Alt" comes and goes as you turn, as before this update?" One
   line either way (a: the test's claim; b: the labels decided on the heading as drawn).
2. **823 x 384, font 2.0, no compass, "Dark since": the evening line never settles** [observed]. The premise "all one case: font 2.0,
   landscape, no fix and no compass" was wrong for this one: its fix is fresh (the rig's clock is the fix's time + 1 s); the eight
   route-card cases do have a lost fix. H10 cannot touch it (only one line, "No compass", is under the parts). A diagnostic throwaway build
   (run 42278700471, the decision's inputs printed) shows a four-frame cycle: with the evening line shown, the kind takes its own line and
   the left part is 342 px tall, so the card needs 458 px, its top at 694 against a limit of 756: hidden. Hidden, the kind goes beside the
   distance, the left part is 244 px, the card needs 360, its top at 792: shown. The margin (12 px) is far smaller than the 98 px swing.
   The decision reads the left part's height, which the decision itself changes (the comment at `NavigationInfo.kt:110` says its inputs are
   "parts the evening stack does not change"; since G3 and A3 the kind's step depends on the evening line, so that no longer holds). At
   73b5c0812 the same case had no evening line at all ("0 evening pieces drawn"); H9's shorter notice moved it to the edge. On a phone this
   would be the evening line flickering every frame. A fix has to choose what shows there: (a) decide on the left part as it would be with
   the evening line, so here the line stays off (the test's positive control, which claims the line shows, would then need its claim
   changed); or (b) something else gives way so the line fits. **For the owner:** "In landscape at the largest text, with no compass and
   after sunset, there isn't room for both "Dark since…" and "by trail" on its own line. Should the card (a) leave out the "Dark since"
   line there, or (b) keep it and put "by trail" back beside the distance?"

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base; the nine reds at ba0724ead; each new claim red for its own reason; both reverts failing only what
  they can cause, with clean compile logs; the full run's counts and the name comparison with pre-main; the cause of stop 2 (printed).
- **Inferred**: that on a phone stop 2 is a visible flicker (Robolectric shows only a layout that never settles); that stop 1 does not reach
  the S22, which is 384 dp wide (the 384 dp case keeps the heading in every state [observed], but no test here reads "Alt" at 384 dp).
- **Could not determine**: font 1.3 (not in these classes for H10); the landscape card under H10 through a unit test of its own (covered
  only through the route-card classes, which are landscape at 640 x 360 and 823 x 384).
- **Premises that were wrong**: "all one case: font 2.0, landscape, no fix and no compass" (the 823 x 384 case has a fresh fix, and a
  different cause); the hand-off's "9 tests are red" was right (6953, 9 failed, 24 skipped).

### Decided beyond the dispatch

- What "fix-age warnings" covers: a stale fix as well as a lost one ("Last fix 45 s ago", "Approaching · last fix 45 s ago"), as both carry
  the fix's age.
- The compass words and the crossed-out compass counted as drawn in H7 refined again, since they do not change as the phone turns.
- This section in the companion report, as the G and H section was (the main report is 734 lines).

### Device check (S22, for the planner), cheapest first

A combined build with this branch at its head, green on CI apart from the two stops, before the phone is asked for.
1. **H10, font 2.0, portrait then landscape**: cover the compass (or use a calibrating state) while approaching a point; under the card,
   one line, "No compass", never two. Then let the fix go stale or lost (cover the sky): one line, the fix's age ("No fix 6 min" or "No fix
   for 6 min"), no "No compass" beside or under it. Evidence: a screenshot each; the card is at most one line taller than with no status.
2. **H10, normal font**: the same two states: both lines show, as before. Evidence: a screenshot.
3. **H10, landscape font 2.0, with a route card open** (two stops or more): Save and Undo whole, a leg row shows when expanded. Evidence: a
   screenshot.
4. **H7 refined again, portrait**: navigating, turn slowly through a full circle. On the S22 (384 dp) the heading and the height stay,
   nothing comes and goes; note whether "Alt" shows (not measured at 384 dp). At font 1.0 with display size raised to give 360 or 320 dp, if that is allowed: at 320 the heading never
   shows in any direction; at 360 (stop 1) note whether "Alt" shows. Evidence: a short screen recording each.
5. **Landscape font 2.0, no compass, after sunset** (stop 2): observation only, not a gate. Note whether the "Dark since" line flickers.

## E14: H11 and H12 (a fresh session, the same day)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E14: "H11 and H12, in the same PR by the same practice (tests first, one
revert per ruling, claims changed never silenced), until full CI is green." Marks as above: [observed] is a CI run's XML or log read this
session (each log checked for compile errors first, and each run's head SHA checked against the commit), [read] is code read at the
commit named, [inferred] is worked out and not measured.

### The rulings (verbatim)

- H11, the owner: "(b) Keep "Alt" when room". The option read: ""Alt" shows when the heading leaves room, so it comes and goes as you
  turn, as it did before this update". The amendment: "the widest-reading rule decides the heading, but doesn't take "Alt" off at 360 dp;
  "Alt" shows whenever the heading as drawn leaves room."
- H12, the owner: "(a) Leave out "Dark since"". The option read: "In that one case the evening line doesn't show; "by trail" keeps its
  own line". The planner's reading: "the decision must settle, never loop. Where showing the evening line would push "by trail" onto its
  own line and the card then no longer fits, the evening line is left out and stays out."

### Base and commits

- Checked against the remote at the start [observed]: `origin/nav-card-kind-line` 03930b1e3, `origin/pre-main` 61f99fbe2 merged in,
  worktree clean. Neither `next-turn` nor `home-values-out` touches a file this section edits (their diffs from pre-main read).
- Mid-task the planner reported pre-main moved (PR #501, a5a2a392f; index row b516d520a) and the laptop's root drive full until about
  21:10Z. Checked: `git fsck` clean, worktree clean, both commits below compiled on CI; the scratch files written in that window (pre-main's
  XML at about 20:50Z) were checked against outside counts (999 XML files, totals 6838 / 24 skipped, as the XML headers sum). pre-main
  merged in (0b79b9a85, a clean merge of its 43 files).

| Commit | What |
|---|---|
| 851cd0540 | Tests first: H12's settling test (new), the 823 x 384 turning case's no-compass claim, the 360 dp case's doc. |
| 916a9c9f6 | H11 (`labelsShown`, AvailabilityMapControlsUi.kt) and H12 (`fit`, NavigationInfo.kt). |
| 0b79b9a85 | pre-main (b516d520a) merged in. |

### What was built [read, at 916a9c9f6]

- **H11** (`AvailabilityMapControlsUi.kt`): what the strip keeps is still decided with the heading at its widest (H7 refined again), so
  the heading never comes and goes. The labels ("Alt" and the heading's) are then shown wherever every readout is kept and the same
  squeezed line, with the heading as drawn, holds them (`readoutsFitBeside` on `headingPx`). So at 360 dp "Alt" comes and goes with the
  heading's width, as before this dispatch; at 320 dp the heading is off and the labels with it (RECORD -713's order: labels go first).
- **H12** (`NavigationInfo.kt`): the card is measured twice, with the evening pieces as held (as before) and with the evening line as it
  would show. Where the two put the kind on different steps and the line is not stepping aside for the route card (D42), the card is laid
  out as with the line: the kind keeps its step ("by trail" on its own line) and the right part keeps the line's width. So the left part's
  height, which the evening decision reads, is the same whether or not the line shows, and the decision cannot feed back into itself:
  here it leaves the line out and it stays out. Where the line does not change the kind's step, nothing changes. The comment at
  `NavigationInfo.kt:110` (inputs "the evening stack does not change") now notes why it holds again.

### Evidence

- **Red, tests first**: run 42291488615 at 851cd0540 [observed], 6958 tests, 3 failed, each for its ruling's reason: 360 dp ("facing
  281°, the line keeps [compass-strip-heading, compass-strip-elevation, compass-strip-coordinates], not [..., navigation-label]", the same
  facing 89° and at 3000 m); the settling test ("frame 2: 1 evening pieces drawn", "frame 4: the kind ... is not on its own line",
  "frame 4: moved from ..."), which reads the loop as a card that moves rather than as a stalled run; and the turning case (AppNotIdle, as
  in E13).
- **Green**: run 42294888310 at 916a9c9f6, the branch's full CI [observed]: **6958 tests, 6934 passed, 24 skipped, 0 failed**, every
  job green. By name against pre-main's 42245483101 at 61f99fbe2 (6838, 0 failed): 36 names only on pre-main (as in E13), 156 only here
  (E13's 155 and the settling test; the turning case renamed in place), **0 names whose outcome differs**. Against the red run: the same
  6958 names, the 3 above the only ones changed.
- **Final, on the new base**: run 42298513392 at 0b79b9a85, compared with pre-main's 42292140229 at a5a2a392f (6838, 0 failed; b516d520a
  is `[skip ci]`, so a5a2a392f is the nearest run). pre-main's two runs have the same 6838 names and outcomes [observed]. **6958
  tests, 6934 passed, 24 skipped, 0 failed**, every job green [observed]; by name against 42292140229: 36 only on pre-main, 156 only
  here, 0 outcomes differ; against 42294888310, the same 6958 names and outcomes.
- **Reverts**, each a one-line edit in a throwaway worktree at 916a9c9f6, its diff checked to be that one line, restored from a copy saved
  before the edit (never from git, the restore checked with `cmp`), the forward line confirmed present in the branch worktree afterwards
  (grep, 1 match each), each log checked for compile errors (0) before its XML was read. 385 tests each, in the NavigationCard, RouteCard,
  CompassStrip, AvailabilityScreenNavigationView and AvailabilityScreenMapIconStack classes:

| Revert | Edit | Run | Fails |
|---|---|---|---|
| H11 | `val labelsShown = fit.labels \|\| (...)` to `val labelsShown = fit.labels` | 42294942957 | 1: the 360 dp case, the same three states as the red run |
| H12 | `val fit = if (...) withEveningFit else heldFit` to `val fit = heldFit` | 42294951110 | 2: the settling test ("frame 2: 1 evening pieces drawn" ...) and the 823 x 384 turning case (AppNotIdle) |

### Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

- `NavigationCardKindLineLargeTextTest`, the 823 x 384 turning case, renamed "... by trail keeps its step as the phone turns, the
  evening line left out with no compass": with no compass the claim is now no evening piece, "by trail" on its own line, and the right part
  keeping "Dark since 5:23 PM"'s width (`topLine(eveningLeftOut = ...)` in `NavigationCardTopLineRules`), with a positive control that the
  piece left out is the one drawn with the compass. Was: the evening line drawn in all three facings. Every other claim of the case is
  unchanged (one width and one needle place as the phone turns, the step held, OWN_LINE).
- New: "823 x 384, font 2,0, Dark since - with no compass the evening line stays out and the card stays still across frames". Settled
  with the compass (positive controls: the line shows, the kind on its own line), then the compass goes, 3 s of frames, then each of 60
  frames read with the clock stepped by hand (`framesAfterTheCompassGoes`): in every frame no evening piece and the kind on its own line,
  and the card, the distance, the kind, the divider and the top line where they were the frame before; the left part's width as with the
  line.
- `CompassStripWindowsTest`, 360 dp: claim unchanged ("Alt" and all three values kept facing 281°, 89° and at 3000 m); its doc now names
  H11 as what it holds.

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base; each new or changed claim red for its ruling's reason; both reverts failing only what they can
  cause, with clean compile logs; the full run's counts and the name comparison with pre-main; that pre-main's runs before and after
  PR #501 carry the same names and outcomes.
- **Inferred**: that on a phone H12's case no longer flickers (Robolectric shows a layout that settles); that the first frame or two still
  show the evening line before it leaves when the card first appears in that state, as RECORD -955's decision has always done before the
  card is measured (the settling test reads from 3 s after, not the first frames).
- **Could not determine**: whether any heading at 360 dp is wide enough to take "Alt" off (the test faces only 281° and 89°, and both
  keep it); how often "Alt" comes and goes on a phone at 360 dp; any landscape case where the evening line is left out for room and the
  kind's step differs other than the one tested (no other test changed outcome, so none in the suite).
- **Premises that were wrong**: none found. The dispatch's state (03930b1e3; 61f99fbe2 merged in; run 42282412164's 6957 tests, 2 red)
  matched the remote.

### Decided beyond the dispatch

- Under H12 the right part keeps the evening line's width where the line is left out, not only the kind its step, so that nothing else
  moves as the line goes (G3, H5's reading: "nothing moves when the evening line comes"). The option text says only that "by trail" keeps
  its own line.
- The rule is general: wherever the evening line would change the kind's step and is left out for room (RECORD -955 or -770), not only at
  823 x 384. Stepping aside for the route card (D42) is excluded, so the route card's room is as before.
- H11's labels are tested only on whether the heading as drawn leaves room with every readout kept; with the heading off (320 dp) labels
  stay off, by RECORD -713's order.
- The merge of pre-main (0b79b9a85) went with git's default message, so it lacks the Co-Authored-By line; not amended (Rules).
- The two revert refs were pushed before their scan; scanned right after, 0 each.

### Device check (S22, for the planner), cheapest first

The E13 steps above stand. Added:
1. **H11, portrait, font 1.0, display size raised to give 360 dp (if allowed)**: navigating, turn slowly through a full circle. The
   heading and the height stay throughout; "Alt" shows wherever there is room and may come and go as you turn, and the line around it
   may shift a few dp when it does. At the S22's own 384 dp, nothing comes and goes. Evidence: a short screen recording.
2. **H12, landscape, largest text, no compass (cover it or use a calibrating state), after sunset**: the card shows "No compass" under the
   parts, no "Dark since" line, and "by trail" on its own line under the distance; it stays still (no flicker, no line coming and going)
   for at least half a minute. Then let the compass come back: "Dark since" returns and "No compass" goes (the card one line shorter); within the card the distance and
   "by trail" stay where they were.
   Evidence: a short screen recording.

## E15: H14 (a fresh session, 2026-11-06/07)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E15: "H14, in the same PR by the same practice". Marks as above:
[observed] is a CI run's XML or log read this session (each log checked for compile errors first, each run's head SHA checked against
the commit), [read] is code read at the commit named, [inferred] is worked out and not measured.

### The ruling (verbatim)

- H14, the owner: "There's a space between the 1.5 mi and the by trail. It used to not be there before 498 to 500. Please put it back
  to where they're next to each other". To "How should "by trail" sit next to the distance on the navigation card?": "(a) Fixed,
  distance pushed right" (the option read: "The distance hugs the right edge of its own window and "by trail" sits right after it.
  They're always side by side, and "by trail" never moves; a short distance leaves room on the left instead"). For "straight": "Yes,
  same". The planner's reading: the window keeps its width for the widest distance; the distance is end-aligned in it; the kind
  follows the window's end with the normal word gap and never moves; the pair stays where the top line places it today; own line
  (H8, H12) unchanged; Arrived and the dash (B3, B4) keep their centring.

### Base and commits

- Checked against the remote at the start [observed]: `origin/nav-card-kind-line` 1718f7d1c, `b516d520a` merged at 0b79b9a85, worktree
  clean; `origin/pre-main` b6f0b464d (PRs #501 to #503). pre-main merged in (9a9a80b71, a clean merge). Its 96 files and this branch's
  64 share none (`comm -12`, C locale). `next-turn` (PR #500) and `place-name-out-2` (PR #504) touch no file this section edits.

| Commit | What |
|---|---|
| 9a9a80b71 | pre-main (b6f0b464d) merged in. |
| b9fad7604 | Tests first: the rules' beside and stacked claims changed to H14's; `NavigationCardDistanceBesideKindTest` new; three countdown states in the rig. |
| f9cfc9ec0 | H14 (`NavigationCardTopLine.kt`, the distance's and the kind's x). |

### What was built [read, at f9cfc9ec0]

- `NavigationCardTopLine.kt`: beside and stacked, the distance's x is `meeting.figureEndPx - figure.width` (it ends at its window's end,
  the meeting point), was centred in the window; the kind's (or the stack's) x is `meeting.kindStartPx`, the word gap (Spacing.xs, 4 dp)
  after it, was centred in the kind's space. The window and the step are unchanged (`TopLineMeeting`, `kindPlace`): the window is the
  left half less half the gap, or the widest figure ("8888 ft" / "88.8 mi"; "≈ 8888 ft" straight) where that is wider, so its end is one
  place for the walk. Own line, Arrived and the dash unchanged.

### Measured placements [observed, run 42313208350, `PLACEMENT` lines]

The window's width, the gap, and where the pair sits in the left part (from its left edge to the kind's right), dp; daytime, no evening
line, where the kind is beside:

| Window, font | Left part | By trail: window | "1.5 mi" / "1280 ft" / "98 ft": pair in the left part | Straight: window | "≈ 1250 ft" / "≈ 100 ft": pair |
|---|---|---|---|---|---|
| 384 portrait, 1.0 | 207.6 | 101.6 (the half) | 30.6 / 21.0 / 48.3 .. 149.3 | 101.6 (the half) | 0.6 / 14.3 .. 155.0 |
| 823 x 384, 1.0 | 176.3 | 86.0 (the longest) | 15.0 / 5.3 / 32.6 .. 133.6 | 101.0 (the longest) | 0.0 / 13.6 .. 154.3 |
| 360 portrait, 2.0 | 291.0 | 143.3 (the half) | 37.0 / 21.6 / 63.0 .. 233.6 | 151.6 (the longest) | 0.0 / 20.6 .. 253.0 |
| 823 x 384, 2.0 | 263.0 | 129.3 (the half) | 23.0 / 7.6 / 49.0 .. 219.6 | 151.6 (the longest) | 0.0 / 20.6 .. 253.0 |

- The gap is 4.0 dp in every case; the kind's left edge and the distance's right edge are one place for every figure in each window.
- Free on the right of the kind: by trail 58.3 (384/1.0), 42.6 (823/1.0), 57.3 (360/2.0), 43.3 (823/2.0); straight 52.6, 22.0, 38.0, 10.0.
- Stacked (320 portrait, 1.0, "Dark since 10:23 PM"; run 42313208350, `RULES` line): left part 125.7, window 84.7 (the longest),
  gap 4, stack 25.7 wide starting 4 dp after the window, 11.3 free on its right.
- At 384 portrait, font 2.0, in daytime, the kind is on its own line (H8 does not apply there), so 360 dp is the portrait case at 2.0.

### Evidence

- **Red, tests first**: run 42310945664 at b9fad7604 [observed; log 0 compile errors; head SHA checked]: 6966 tests, 27 failed, 24
  skipped, all in the top-line classes, every failing line one of H14's: "does not end at its window's end" (123), "does not start at its
  space's start" (174) or "... the kind's space's start" (6, the stack), "not the word gap" (60, e.g. "<1.5 mi> ... and <by trail> ... are
  37.3 dp apart, not the word gap (4.0 dp)" at 823 x 384, 2.0), "the distance's right edge moved as it counted down" (24); no positive
  control failed (the three new figures read as the rig predicts).
- **Green**: run 42313208350 at f9cfc9ec0, the branch's full CI [observed; 0 compile errors; head SHA checked]: **6966 tests, 6942 passed,
  24 skipped, 0 failed**, every job green. By name against pre-main's run 42308597327 at b6f0b464d (6838, 0 failed, 24 skipped): 36 names
  only on pre-main, 164 only here (E14's 156 and the 8 new), **0 names whose outcome differs**.
- **Revert**: one line in a throwaway worktree over f9cfc9ec0 (ref `nav-card-e15-revert`, f93987792), its diff checked to be that one
  line: `else maxOf(0, meeting.figureEndPx - figure.width)` to `else maxOf(0, (meeting.figureEndPx - figure.width) / 2)` (the distance
  centred in its window again, the kind left where H14 puts it). The file saved before the edit and copied back after (never from git);
  the forward line confirmed present in the branch worktree (grep, 1 match). Run 42313321355 (workflow_dispatch, the NavigationCard,
  RouteCard, CompassStrip, AvailabilityScreenNavigationView, AvailabilityScreenMapIconStack and NavigationInfoLayout classes, 393 tests):
  log 0 compile errors, head SHA f93987792; **27 failed**, the same 27 names as the red run, and only with the distance's own messages:
  "does not end at its window's end" (123), "not the word gap" (48), "the distance's right edge moved" (24). None of "does not start at
  its space's start": the kind stayed where H14 puts it, as this revert should leave it. So the failures are this edit's.

### Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

- `NavigationCardTopLineRules`, beside: "the distance ... does not end at its window's end" (was "... is not centred in its space") and
  "the kind ... does not start at its space's start, the word gap after the distance's window" (was "... is not centred in its space");
  the same 0.5 dp tolerance. Stacked: the stack starts at the kind's space's start, each word centred in the stack, "trail" starting
  there too (was each word centred in the kind's space). Every class built on the rules (Portrait, Landscape, Evening, LargeText,
  Stack) checks these; their own claims (step, shift, baseline) are unchanged.
- New, `NavigationCardDistanceBesideKindTest`, 8 cases (384 portrait and 823 x 384 at 1.0, 360 portrait and 823 x 384 at 2.0; by trail
  "1.5 mi" > "1280 ft" > "98 ft", straight "≈ 1250 ft" > "≈ 100 ft"): the 4 dp gap; the distance's right edge and the kind's left edge
  one place across the countdown; a positive control that the widths differ. The kind's edge staying put passed before H14 too (G3
  centred it in a fixed space), so it is a control, not evidence; the gap and the distance's edge are what turned red.
- The rig (`NavigationCardKindLineScreen`): `TRAIL_MILES`, `TRAIL_SHORT` (the way home's route 2420 m and 30 m) and `STRAIGHT_SHORT` (the
  fix 30 m short of the waypoint); the made-up point P only.

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base and the merge; red for H14's reasons only; green full CI with 0 outcomes differing from pre-main by
  name; the revert failing only the distance's own claims, with a clean compile log; the placements above.
- **Inferred**: that the S22 draws within about 1% of these (as the earlier sections found), so the pair sits about where the table says.
- **Could not determine**: the S22's own placements (no phone this session; the S22 check running now is on a build without H14); font
  1.3; kilometres (the rig is in miles).
- **Premises that were wrong**: none found. The dispatch's state (1718f7d1c; b516d520a merged at 0b79b9a85; pre-main at b6f0b464d)
  matched the remote.

### Decided beyond the dispatch

- "The pair as a whole stays where the top line places it today" read as: the window's end stays the meeting point (G3's divide: the
  middle less half the gap, or the widest figure's width), so the kind starts where its space started. The pair is therefore not
  centred in the left part: at 384 dp, font 1.0, "98 ft by trail" has 48.3 dp free on its left and 58.3 on its right, and the kind's left
  edge is the middle plus 2 dp. The other reading, the window sized to the widest figure exactly and the pair centred in the left part,
  would move the kind left by up to about 15 dp at 384 dp and differ between by trail and straight; not built. **For the owner, if the
  phone shows it reading oddly:** "With "98 ft by trail" there's room on both sides; should the pair stay where it is (the kind at the
  middle), or move so the widest distance and "by trail" are centred together?"
- Stacked: the stack starts the word gap after the window's end; its two words stay centred in it (G3's choice), so "trail", the wider,
  starts there and "by" sits centred over it. H14 names only "by trail" beside.
- The gap is Spacing.xs (4 dp), the meeting's gap since A2 ("a word's space apart"), not the font's own space character.
- The merge of pre-main (9a9a80b71) went with git's default message, so it lacks the Co-Authored-By line; not amended (Rules).
- Not changed: the KDoc and test names of the Portrait, Landscape, Evening and LargeText classes still say "in their halves" / "each
  centred in its own space" (G3); their claims come from the rules, which now say H14. Renaming them would break the by-name comparison;
  a follow-up if wanted.

### Device check (S22, for the planner), cheapest first

The E13 and E14 steps stand. Base step 7 now reads:
7. **The fixed places (H14)**: portrait and landscape, at font 1.0 and font 2.0 (at 2.0 in portrait the kind is on its own line at the
   S22's 384 dp in daytime, which is unchanged; note it). Navigate the way home and to a waypoint (straight). The distance and "by trail"
   (or "straight") side by side with only the word gap between them, as the distance counts down (miles to feet, e.g. "1.5 mi" then
   "1280 ft" then under 100 ft): the kind's left edge never moves, and a short distance leaves room on its left. Arrived and the dash
   centred (B3, B4); a note that only says why leaves no status line (F2). Evidence: a screenshot at a long and a short distance in each
   orientation, the kind's left edge sampled in each.

### Clean-up

- Throwaway ref `nav-card-e15-revert` and its worktree deleted after its run. Scratch `/mnt/work/scratch/hazel-e15/` deleted at the end.
- No index row added to `docs/audits/README.md`. No coordinate beyond the made-up P.

## E16: H15, stopped before building (a fresh session, 2026-11-06)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E16. Marks as above. **Nothing of H15 is built**: the branch carries only
pre-main merged in. This section records a stop, a wrong premise, for the owner.

### Base

- Checked against the remote at the start [observed]: `origin/nav-card-kind-line` 6cc38fdda (code f9cfc9ec0; b6f0b464d merged at
  9a9a80b71), worktree clean; `origin/pre-main` 26a5c5955 (PR #504). pre-main's 5 files since b6f0b464d share none with this branch's;
  `next-turn` shares none either. pre-main merged in at ae6bda12b (a clean merge, with a message and the Co-Authored-By line).

### The stop: H15 moves "by trail" right, where the option the owner chose said left

- H15's option read: "The widest distance plus "by trail" are centred as one block, so "by trail" sits up to about 15 dp further left,
  slightly differently for "by trail" and "straight"". That figure came from E15's own section ("would move the kind left by up to
  about 15 dp at 384 dp"), and it is wrong in direction and size.
- Why [inferred from observed widths]: under H14 the distance's window starts at the left part's left edge in every case (run
  42313208350, `PLACEMENT` lines: "window <left edge>.."), so all the free room is on the kind's right. Centring the block (the window
  sized to the widest distance, 4 dp, the kind) can only move it right. Where the kind starts, from the left part's left edge, dp:

| Window, font | Left part | Kind | Widest distance (window) | H14 (today) | H15, block centred | Move |
|---|---|---|---|---|---|---|
| 384 portrait, 1.0, by trail | 207.7 | 43.7 | 84.7 | 105.7 | 126.3 | 20.7 right |
| 384 portrait, 1.0, straight | 207.7 | 49.3 | 101.0 | 105.7 | 131.7 | 26.0 right |
| 823 x 384, 1.0, by trail | 176.3 | 43.7 | 84.7 | 90.0 | 110.7 | 20.7 right |
| 823 x 384, 1.0, straight | 176.3 | 49.3 | 101.0 | 105.0 | 116.0 | 11.0 right |
| 360 portrait, 2.0, by trail | 291.0 | 86.3 | 127.0 | 147.3 | 167.8 | 20.5 right |
| 360 portrait, 2.0, straight | 291.0 | 97.3 | 151.7 | 155.7 | 174.7 | 19.0 right |
| 823 x 384, 2.0, by trail | 263.0 | 86.3 | 127.0 | 133.3 | 153.8 | 20.5 right |
| 823 x 384, 2.0, straight | 263.0 | 97.3 | 151.7 | 155.7 | 160.7 | 5.0 right |

- Widths [observed]: run 42313208350 at f9cfc9ec0, `NavigationCardDistanceBesideKindTest`'s `RULES` lines (kind, longest, left part)
  and `PLACEMENT` lines (H14's kind start). The H15 column is worked out from them, not drawn: (left part - (widest + 4 + kind)) / 2 +
  widest + 4.
- E15's table is corrected here on one point: at 823 x 384, font 1.0, by trail, the 86.0 dp window is the half (176.3 / 2 - 2), not
  the longest; the widest by-trail distance is 84.7 dp.
- So with "98 ft by trail" at 384 dp, font 1.0, H15 puts the kind 126.3..170.0 (the left part's middle is 103.8), about 22 dp into the
  right half, and the distance's left edge at 69.0. The owner chose an option that promised the opposite move.

### Question for the owner

"Centring the widest distance and "by trail" together moves "by trail" about 20 dp to the right (5 to 26 dp, depending on the window
and the kind), not 15 dp to the left as the option said. Should the pair be centred together anyway, or stay as it is now (by trail
starting just right of the middle)?"

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base, the merge, and every width above from run 42313208350's XML.
- **Inferred**: the H15 places (worked out from those widths, to the pixel rounding the card uses, not drawn).
- **Could not determine**: how the S22 would draw them (no phone this session, and none was to be touched).
- **Premises that were wrong**: H15's option text, "about 15 dp further left"; it is 5 to 26 dp further right.

### Decided beyond the dispatch

- Stopped before the tests-first commit rather than building H15 and asking after, since the owner's choice may rest on the direction
  the option named. Nothing else changed.

### Clean-up

- Scratch `/mnt/work/scratch/hazel-e16/` deleted at the end. No throwaway refs made. No index row added.

## E16: H15 built (the same session, after the owner's answer)

The stop above went to the owner through the planner. Rowan's message, verbatim in its parts: to "With the correct figures, where
should "98 ft by trail" sit?", the owner chose **"Centre them together"** (the option: "The widest distance plus "by trail" centred as
one block: "by trail" moves about 20 dp right (5 to 26 dp depending on screen and font), the distance starts further in from the
left"), recorded at 7430085b9 on records-after-173; "Build E16 as you laid out ... centre the stacked case the same way and disclose
it." The section above stands as the record of the stop.

### Commits

| Commit | What |
|---|---|
| ae6bda12b | pre-main (26a5c5955) merged in. |
| 3bbf5ab98 | [skip ci] The stop, above. |
| 92415b4c7 | Tests first: three tests' claims changed to H15's. |
| a17bfb954 | H15 (`NavigationCardTopLine.kt`, the window's end; `NavigationCardTopLineFit.kt`, `topLineWindowEndPx`, new). |

### What was built [read, at a17bfb954]

- `topLineWindowEndPx(width, gap, longest, kind) = max(0, (width - (longest + gap + kind)) / 2) + longest`: the window is the widest
  distance exactly (the shown figure where wider, as before), and the window, the 4 dp gap and the kind as drawn (the whole kind, or
  the stack) are one block centred in the left part. `CardTopLine` places the distance ending there and the kind the gap after it, so
  the kind and the distance's right edge stay put as the distance counts down.
- Unchanged: the step (`TopLineMeeting`, `kindPlace`; so nothing moves between beside, stacked and own line), the kind on its own line
  (H8, H12), Arrived and the dash (B3, B4).

### Measured placements [observed, run 42326998277, `PLACEMENT` lines], dp from the left part's left edge, daytime, kind beside

| Window, font | Left part | By trail: widest, block, kind | "1.5 mi" / "1280 ft" / "98 ft" start | Straight: widest, block, kind | "≈ 1250 ft" / "≈ 100 ft" start |
|---|---|---|---|---|---|
| 384 portrait, 1.0 | 207.7 | 84.7; 37.7..170.0; 126.3 | 51.3 / 41.7 / 69.0 | 101.0; 26.7..181.0; 131.7 | 26.7 / 40.3 |
| 823 x 384, 1.0 | 176.3 | 84.7; 22.0..154.3; 110.7 | 35.7 / 26.0 / 53.3 | 101.0; 11.0..165.3; 116.0 | 11.0 / 24.7 |
| 360 portrait, 2.0 | 291.0 | 127.0; 36.7..254.0; 167.7 | 57.3 / 42.0 / 83.3 | 151.7; 19.0..272.0; 174.7 | 19.0 / 39.7 |
| 823 x 384, 2.0 | 263.0 | 127.0; 22.7..240.0; 153.7 | 43.3 / 28.0 / 69.3 | 151.7; 5.0..258.0; 160.7 | 5.0 / 25.7 |

- Room either side of the block: equal within 0.4 dp in every case (37.7 / 37.7, 26.7 / 26.7, 22.0 / 22.0, 11.0 / 11.0, 36.7 / 37.0,
  19.0 / 19.0, 22.7 / 23.0, 5.0 / 5.0). The gap is 4.0 dp in every case.
- The distance's "start" columns are each line's "room left of it" field: the distance's left edge from the left part's left edge.
  "≈ 1250 ft" is as wide as the widest straight figure, so it starts at the block's left end.
- The kind's start matches the stop's worked-out column to 0.1 dp in all eight cases (126.3, 131.7, 110.7, 116.0, 167.7, 174.7, 153.7,
  160.7), so it moved right by 5.0 to 26.0 dp from H14's place.
- Stacked (320 portrait, 1.0, "Dark since 10:23 PM"): `NavigationCardKindLineStackTest` checks the stack starts the gap after the
  centred block's window and passed. Its place is [inferred] from the run's `RULES` widths (left part 125.7, widest 84.7, stack 25.7):
  block 5.7..119.7, the stack from 94.3, 5.7 dp right of H14's 88.7.

### Evidence

- **Red, tests first**: run 42325029243 at 92415b4c7 [observed; head SHA checked; failed-job logs 0 compile errors]: 6966 tests, 27
  failed, 24 skipped. Every failing line is one of H15's: "does not end at the centred block's window end" (59), "does not start at
  ..., the word gap after the centred block's window" (60, kind and stack), "the block ... is not centred in the left part" (20, the
  eight new-claim cases across their states), "the figure ... ends right of the left part's middle" (1, `NavigationInfoLayoutTest`).
  No other message. Classes: DistanceBesideKind 8, Portrait 4, Evening 4, Stack 4, LargeText 4, Landscape 2, NavigationInfoLayout 1.
- **Green**: run 42326998277 at a17bfb954, full CI [observed; head SHA checked; every job green]: **6966 tests, 0 failed, 24
  skipped**. By name against pre-main's latest CI run, 42315368330 at 3542a0c30 (26a5c5955 is [skip ci]): 6838 there; 36 names only
  on pre-main and 164 only here (the same sets as E15's comparison), **0 names whose outcome differs**.
- **Revert**: one line in a throwaway worktree over a17bfb954 (ref `nav-card-e16-revert`, 4ececdcb1; its diff checked to be that line):
  `val windowEndPx = if (longestFigurePx != null && kindPlaceable != null) topLineWindowEndPx(...) else meeting.figureEndPx` to
  `val windowEndPx = meeting.figureEndPx` (H14's window end again). The file was saved before the edit and copied back from that copy,
  never from git; the forward line confirmed present in the branch worktree afterwards (grep, 1 match; branch worktree clean). Run
  42327025888 (workflow_dispatch, the NavigationCard, RouteCard, CompassStrip, AvailabilityScreenNavigationView,
  AvailabilityScreenMapIconStack and NavigationInfoLayout classes): head SHA 4ececdcb1, failed-job logs 0 compile errors, 397 tests;
  **27 failed, the same 27 names as the red run** (set equal), with only H15's messages in the same counts (59, 60, 20, 1). The revert
  puts back exactly H14's placement, so matching the red run is what it should do; the run is its own (397 tests, its own SHA).

### Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

- `NavigationCardTopLineRules`, beside and stacked: the window end is the centred block's (`(width - (widest + gap + kind)) / 2 +
  widest`, the stack standing for the kind when stacked); was the H14 meeting point. Same 0.5 dp tolerance. The step's own arithmetic
  is unchanged.
- `NavigationCardDistanceBesideKindTest` (E15's eight cases): new claim, the block from the distance's right edge less the widest
  distance to the kind's right edge has its centre at the left part's middle, within 0.5 dp. Worked out from the measured type, not
  from the rules' window.
- `NavigationInfoLayoutTest` "... either side of the left part's middle": was `figure.right <= middle - 2 dp` (G3's halves); now the
  kind starts 4 dp after the figure, the figure ends right of the middle, and (unchanged) the kind starts right of the middle + 2 dp.
  The name is kept so the by-name comparison holds; it now overstates "either side" (a follow-up if wanted).

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base and merge; red for H15's reasons only; green full CI, 0 outcomes differing by name from pre-main;
  the revert failing only H15's claims with a clean compile log; the eight-case placements above.
- **Inferred**: the stacked case's place (worked out from widths; its claim passed, which is the check); that the S22 draws within
  about 1% of these, as earlier sections found.
- **Could not determine**: the S22's own placements (no phone); font 1.3; kilometres (the rig is in miles).
- **Premises that were wrong**: the original H15 option's "about 15 dp further left" (the stop above); corrected by the owner's second
  answer. Nothing else: the dispatch's state matched the remote.

### Decided beyond the dispatch

- Stacked: the stack is the kind in the centred block (the planner's message: "centre the stacked case the same way and disclose it").
- The step stays decided on H14's meeting arithmetic, so no case changes between beside, stacked and own line. Under centring the kind
  would fit beside in a few more cases (the block needs widest + gap + kind, the meeting needs max(half, widest) + gap + kind);
  changing the step would be visible beyond H15, so not built. For the owner if wanted.
- Where the shown figure is wider than the widest counted (a trip in another area), the window is that figure, as before.

### Device check (S22, for the planner), cheapest first

The E13 and E14 steps stand. Base step 7 now reads:
7. **The fixed places (H14, H15)**: portrait and landscape, at font 1.0 and font 2.0 (at 2.0 in portrait the kind is on its own line
   at the S22's 384 dp in daytime, unchanged; note it). Navigate the way home and to a waypoint (straight). The distance and "by trail"
   (or "straight") side by side with only the word gap between them, the pair centred in the left part as one block with the widest
   distance (equal room either side of where "8888 ft" would sit with its kind; expected at 384 dp, font 1.0: "by trail" starting about
   126 dp in, "straight" about 132), and the kind still as the distance counts down (miles to feet, e.g. "1.5 mi", "1280 ft", under
   100 ft): only the distance's left edge moves. Arrived and the dash centred (B3, B4); a note that only says why leaves no status line
   (F2). Evidence: a screenshot at a long and a short distance in each orientation and font, the kind's left edge sampled in each.
   Needs a fresh build from a17bfb954 or later.

### Clean-up

- Throwaway ref `nav-card-e16-revert` (local and remote) and its worktree deleted after its run. Scratch `/mnt/work/scratch/hazel-e16/`
  deleted at the end. No index row added. No coordinate beyond the made-up P.

## E17: the greyed needle clear of "Target" (two sessions, 2026-11-07)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E17. The tests (03625fcdf) and the fix (ea4b6e7e8) were pushed by one
Hazel, stopped before its revert check and report; this section, the reading of both runs, a fresh revert and the comparison by name are
a second, fresh session's ("Fresh coder finishes it", the owner, relayed by the planner). Marks as above: [observed] is a CI run's XML or
log read this session (each log checked for compile errors first, each run's head SHA checked against the commit), [read] is code read
at the commit named, [inferred] is worked out and not measured.

### The ruling (verbatim)

- E17, from the S22 check on build 4825: "at font 2.0, portrait, after 5 minutes without a fix (the card's "—" and "Target"), the greyed
  needle overlaps "Target" by about 8 dp". The owner: **"Fix it in #498 first"** ("A coder moves the needle clear of "Target" in that
  case (nothing else changes), tests and CI, then #498 is ready for your merge word"). Build: "the needle and "Target" never overlap, in
  every orientation and font the card supports; everything else unchanged; if clearing it needs a visible choice (shrinking, moving
  "Target", wrapping), that's a stop with measured widths."

### Base and commits

- Checked against the remote at this session's start [observed]: `origin/nav-card-kind-line` ea4b6e7e8, the branch worktree clean at
  ea4b6e7e8; `origin/pre-main` 1f6901a11 (PR #505), merged in by the first session at c76416976.

| Commit | What |
|---|---|
| c76416976 | pre-main (1f6901a11) merged in. |
| 03625fcdf | Tests first: `NavigationCardNeedleClearOfWordTest` new (21 cases); the rig gains `LOST` (the last fix 6 minutes old) and `NO_TARGET` (the way home with no start). |
| ea4b6e7e8 | E17 (`RoundedMapCardFit.kt`, `needleShiftPx`; `NavigationInfo.kt`, the turn's width measured; `NavigationCardParts.kt`, the needle drawn at that offset). |

### What was built [read, at ea4b6e7e8]

- Why it overlapped: G3 gives the turn line a fixed space sized for the widest degrees ("0°" to "180°"), centres whatever is in it, and
  puts the needle a gap after the space. "Target" and "No target" are wider than that space (at font 2.0: 78.3 and 119.0 dp against
  54.7), so the centred word ran half its overhang past the space's end, through the 4 dp gap and into the needle.
- The fix: `needleShiftPx(turnTextPx, turnSpacePx) = max(0, (turnTextPx - turnSpacePx + 1) / 2)`, the half the word overhangs, and the
  needle is drawn that far right with `Modifier.offset`. An offset moves the drawing, not the layout, so the row, the word (still centred
  where the degrees are) and everything beside the turn line keep their places. With degrees the shift is 0 (the space is the widest
  degrees), so the needle keeps G3's fixed place. The word is not shrunk, moved or wrapped.

### Measured placements [observed, run 42341392562, `NEEDLE` lines], dp from the window's left edge

| Window, font | Left part | Degrees' space | "Target" | Needle (was) | "No target" | Needle (was) | Gap |
|---|---|---|---|---|---|---|---|
| 320, 1.0 | 4.0..147.7 | 28.0 | 43.3..83.0 | 87.0..109.0 (81.0) | 33.3..93.0 | 97.0..119.0 (81.0) | 4.0 |
| 320, 1.3 | 4.0..120.0 | 35.0 | 24.0..74.3 | 78.3..100.3 (70.7) | 11.3..87.3 | 91.3..113.3 (70.7) | 4.0 |
| 320, 2.0 | 4.0..255.0 | 54.7 | 77.7..156.0 | 160.0..182.0 (148.0) | 57.3..176.3 | 180.3..202.3 (148.0) | 4.0 |
| 360, 1.0 | 4.0..187.7 | 28.0 | 63.3..103.0 | 107.0..129.0 (101.0) | 53.3..113.0 | 117.0..139.0 (101.0) | 4.0 |
| 360, 1.3 | 4.0..160.0 | 35.0 | 44.0..94.3 | 98.3..120.3 (90.7) | 31.3..107.3 | 111.3..133.3 (90.7) | 4.0 |
| 360, 2.0 | 4.0..295.0 | 54.7 | 97.7..176.0 | 180.0..202.0 (168.0) | 77.3..196.3 | 200.3..222.3 (168.0) | 4.0 |
| 384, 1.0 | 4.0..211.7 | 28.0 | 75.3..115.0 | 119.0..141.0 (113.0) | 65.3..125.0 | 129.0..151.0 (113.0) | 4.0 |
| 384, 1.3 | 4.0..184.0 | 35.0 | 56.0..106.3 | 110.3..132.3 (102.7) | 43.3..119.3 | 123.3..145.3 (102.7) | 4.0 |
| 384, 2.0 | 4.0..155.7 | 54.7 | 28.0..106.3 | 110.3..132.3 (98.3) | 7.7..126.7 | 130.7..152.7 (98.3) | 4.0 |
| 412, 1.0 | 4.0..134.0 | 28.0 | 36.3..76.0 | 80.0..102.0 (74.0) | 26.3..86.0 | 90.0..112.0 (74.0) | 4.0 |
| 412, 1.3 | 4.0..212.0 | 35.0 | 70.0..120.3 | 124.3..146.3 (116.7) | 57.3..133.3 | 137.3..159.3 (116.7) | 4.0 |
| 412, 2.0 | 4.0..155.7 | 54.7 | 28.0..106.3 | 110.3..132.3 (98.3) | 7.7..126.7 | 130.7..152.7 (98.3) | 4.0 |
| 480, 1.0 | 4.0..202.0 | 28.0 | 70.3..110.0 | 114.0..136.0 (108.0) | 60.3..120.0 | 124.0..146.0 (108.0) | 4.0 |
| 480, 1.3 | 4.0..280.0 | 35.0 | 104.0..154.3 | 158.3..180.3 (150.7) | 91.3..167.3 | 171.3..193.3 (150.7) | 4.0 |
| 480, 2.0 | 4.0..203.3 | 54.7 | 51.7..130.0 | 134.0..156.0 (122.0) | 31.3..150.3 | 154.3..176.3 (122.0) | 4.0 |
| 780 x 360, 1.0 | 351.3..527.7 | 28.0 | 407.0..446.7 | 450.7..472.7 (444.7) | 397.0..456.7 | 460.7..482.7 (444.7) | 4.0 |
| 780 x 360, 1.3 | 363.0..500.0 | 35.0 | 393.3..443.7 | 447.7..469.7 (440.0) | 380.7..456.7 | 460.7..482.7 (440.0) | 4.0 |
| 780 x 360, 2.0 | 342.7..605.7 | 54.7 | 422.3..500.7 | 504.7..526.7 (492.7) | 402.0..521.0 | 525.0..547.0 (492.7) | 4.0 |
| 823 x 384, 1.0 | 394.3..570.7 | 28.0 | 450.0..489.7 | 493.7..515.7 (487.7) | 440.0..499.7 | 503.7..525.7 (487.7) | 4.0 |
| 823 x 384, 1.3 | 363.0..543.0 | 35.0 | 415.0..465.3 | 469.3..491.3 (461.7) | 402.3..478.3 | 482.3..504.3 (461.7) | 4.0 |
| 823 x 384, 2.0 | 385.7..648.7 | 54.7 | 465.3..543.7 | 547.7..569.7 (535.7) | 445.0..564.0 | 568.0..590.0 (535.7) | 4.0 |

- "(was)" is the needle's left edge in the red run (42339957138, the same tests before the fix), from its failure messages; the word's
  place is the same in both runs, to the 0.1 dp shown.
- The gap between the word's laid-out right edge and the needle's square is 4.0 dp (the line's own gap, Spacing.xs) in all 42 cases.
- The tightest room on the right: 384 and 412 dp at font 2.0, "No target", the needle ending at 152.7 with the left part ending at
  155.7, 3.0 dp spare; then 320 dp at font 1.3, "No target", 6.7 dp. Everywhere else at least 17 dp.

### Evidence

- **Red, tests first**: run 42339957138 at 03625fcdf (pull_request; head SHA checked; failed-job logs 0 compile errors) [observed]: 6995
  test cases in the XML, 21 failed, 24 skipped. All 21 are `NavigationCardNeedleClearOfWordTest`'s, every one of its cases, and every
  failing line is the overlap claim; no positive control and no centring control failed, no other class failed. The message at the
  reported case, 384 dp, font 2.0: "384/2.0 LOST: the needle 98.333336.dp..120.333336.dp overlaps <Target> 28.0.dp..106.333336.dp by
  8.0.dp" (and "384/2.0 NO_TARGET: the needle ... overlaps <No target> 7.6666665.dp..126.666664.dp by 28.333328.dp"). The overlap at
  font 2.0 is 8.0 dp in every window, the S22's "about 8 dp"; 3.7 at 1.3 and 2.0 at 1.0. The second job that runs the tests (the Street
  map deleted) failed on the same 21 names and nothing else.
- **Green**: run 42341392562 at ea4b6e7e8 (pull_request, the merge of ea4b6e7e8 into 1f6901a11; head SHA checked; logs 0 compile
  errors; every job green) [observed]: **6995 test cases, 6971 passed, 24 skipped, 0 failed**. Red to green by name: the same 6995 names,
  and the only outcomes that differ are the 21 above.
- **By name against pre-main**: pre-main's latest CI run, 42337576510 at 1f6901a11 (the base merged in; every job green): 6846 test
  cases, 0 failed, 24 skipped. 36 names only on pre-main and 185 only here (E16's 164 and the 21 new), **0 names whose outcome differs**.
- **Revert**: one line in a throwaway worktree over ea4b6e7e8 (ref `nav-card-e17b-revert`, 7286e8ceb; its diff checked to be that
  line): in `navigationInfoFit`, `needleShiftPx = needleShiftPx(w.turnTextPx, w.turnSpacePx),` to `needleShiftPx = 0,` (the needle at
  G3's fixed place again, the measuring and the offset left in). The file was saved before the edit and copied back from that copy,
  never from git; the forward line confirmed present in the branch worktree afterwards (grep, 1 match; branch worktree clean at
  ea4b6e7e8). Run 42379914039 (workflow_dispatch, the NavigationCard, RouteCard, CompassStrip, AvailabilityScreenNavigationView,
  AvailabilityScreenMapIconStack, NavigationInfoLayout and RoundedMapCardFit classes) [observed]: head SHA 7286e8ceb, logs 0 compile
  errors, 425 tests; **21 failed, the same 21 names as the red run with the same messages, character for character** (e.g. "384/2.0
  LOST: the needle 98.333336.dp..120.333336.dp overlaps <Target> 28.0.dp..106.333336.dp by 8.0.dp"), and only the overlap claim; the
  Street-map job, which runs every test, failed the same 21 and no other (6941 tests). Matching the red run exactly is what this revert
  should do, since the shift is the whole fix; the run is its own (its SHA, 425 tests). The first session had made the same one-line
  revert (ref `nav-card-e17-revert`, 562764a02, run 42341410140, 418 tests, the same 21 failures); it agrees, and is not the evidence
  cited here, since its saving and restoring were not this session's to check.

### Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

- None changed. New, `NavigationCardNeedleClearOfWordTest`, 21 cases: portrait 320, 360, 384, 412 and 480 dp and landscape 780 x 360 and
  823 x 384, each at fonts 1.0, 1.3 and 2.0, with "Target" (the way home 6 minutes after the last fix) and "No target" (the way home with
  no start). Claims: the word's laid-out right edge at or left of the needle's square's left edge (stricter than ink, both being boxes);
  controls, which held before E17 too and so are not evidence: the word centred where "169°" is, within 0.5 dp, and the needle inside the
  left part. A positive control reads the turn line's words first ("169°", "Target", "No target").
- The rig (`NavigationCardKindLineScreen`): `LOST` (the fix's time 6 minutes back, past the 5 minute `LOST_AFTER_MILLIS`) and
  `NO_TARGET` (no navigation target). No new point; P only.

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base; red for E17's reason only (the overlap, 8.0 dp at font 2.0, in all 21 cases, nothing else red);
  green full CI, 0 outcomes differing by name from pre-main's run at the base; the revert failing only the overlap claim, with a clean
  compile log; the placements above, with 4.0 dp between word and needle in every case.
- **Inferred**: "everything else unchanged" rests on two things, not on a test written for it: the offset cannot move layout (read at
  ea4b6e7e8), and every other card, strip and navigation test passed unchanged by name. That the S22 draws within about 1% of these,
  as earlier sections found.
- **Could not determine**: the S22's own placements (no phone this session, and none was to be touched); which density the 34-p20-stale
  screenshot was taken at (its pixel figures fit about 3.5 px per dp, the rig's 384 and 412 dp rows at font 2.0 are identical, so the
  prediction below is the same either way); "No target" on the phone (the check named "Target" only); kilometres and other locales'
  words (the rig is English, miles).
- **Premises that were wrong**: none found. The dispatch's state (ea4b6e7e8) matched the remote.

### Decided beyond the dispatch

- The first session's choice, kept: the needle moves, the word does not. The ruling says "moves the needle clear of "Target"", and it
  lists shrinking, moving "Target" and wrapping as the visible choices that would be a stop; moving the needle is the one the owner
  named, so it was built without a stop. **What a user sees:** when "Target" or "No target" replaces the degrees, the greyed needle sits
  about 6 to 32 dp further right than it does beside degrees (half the word's overhang: about 6 dp at font 1.0 with "Target", 12 at 2.0,
  32 with "No target" at 2.0), and comes back when degrees return. That is a jump G2/G3 otherwise avoid ("Fixed spaces, so nothing
  jumps"); it happens only at the change between degrees and a word, both of which already change what is shown there. For the owner if
  it reads oddly: the other way to clear it is a turn space sized for "No target" in every state, which moves the needle right for good
  and narrows the left part; not built.
- "No target" was covered as well as "Target": it is the other word drawn in the degrees' space, and wider.
- The ink overlap on the phone (needle 361-430 px, "Target" 102-391 px) is measured on drawn pixels; the tests measure laid-out boxes,
  which are wider than ink, so a pass here is at least as strict.

### Device check (S22, for the planner), cheapest first

The E13 to E16 steps stand. Add:
8. **The needle clear of "Target" (E17)**: font 2.0, portrait. Navigate the way home, then go 5 minutes without a fix (airplane mode
   with location off, or indoors) until the card shows "—" with "Target" and the needle greys. Expected: the needle's left edge is
   clear of "Target"'s right edge, by about 4 dp of laid-out box (more in ink); the needle about 12 dp right of where it sat beside
   degrees, and inside the card's left part, short of the divider. Then get a fix back: the degrees return and the needle goes back to
   its fixed place. Repeat at font 1.0, and in landscape at font 2.0. Evidence: a screenshot of each, the needle's and "Target"'s x
   sampled. If convenient, "No target" (navigating the way home with no start) at font 2.0, the widest case, about 3 dp short of the
   divider in the rig. Needs a build from ea4b6e7e8 or later.

### Clean-up

- This session's throwaway ref `nav-card-e17b-revert` (local and remote) and its worktree deleted after its run; scratch
  `/mnt/work/scratch/hazel-e17b/` deleted at the end. No index row added. No coordinate beyond the made-up P.
- Left by the first session, not deleted here (not this session's to delete), for the planner: the ref `nav-card-e17-revert`
  (562764a02, local and on the remote), its worktree at `/mnt/work/scratch/hazel-e17/revert` (its one changed file restored to the
  forward line, uncommitted), and that scratch folder (a saved copy of the file, the red run's log and XML, and notes).

## E18: the needle never moves, stopped before building (a fresh session, 2026-11-07)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E18. Marks as above: [observed] is a CI run's XML or log read this
session (its head SHA checked, its log checked for compile errors first), [read] is code read at the commit named, [inferred] is worked
out and not measured.

### The ruling (verbatim)

- E18: "E17's fix moves the needle 6 to 32 dp right when "Target" or "No target" replaces the degrees. The owner, to "Keep that, or
  size the space for "No target" all the time so the needle never moves?": **"Never moves"** ("The degrees' space is always as wide as
  "No target", so the needle stays put; the card's left part is a little tighter in every case"). **Build:** the degrees' space is sized
  for the widest of its contents ("No target" included) in every state, so the needle never moves; the word stays clear of the needle
  (E17's claim stands). Report the left part's measured room in every case; if any case no longer fits, that's a stop with widths."

### Base and commits

- At the start [observed]: `origin/nav-card-kind-line` 746bb8df2 (code ea4b6e7e8), the worktree clean; `origin/pre-main` 1f6901a11,
  already merged in (c76416976), so no merge.

| Commit | What |
|---|---|
| f1a0a3bbd | Tests first: `NavigationCardNeedleNeverMovesTest` new (21 cases); `NavigationCardTopLineRules`' turn-line model takes E18's space (claim changed). |
| df72cf128 | On the separate ref `nav-card-e18-wip`, not this branch, not run: the fix, held for the question below. |

### Red, tests first [observed, run 42383172696 at f1a0a3bbd]

- Failed-job log: 0 compile errors. 7016 test cases, 45 failed, 24 skipped. Every failing line is the needle's place, E18's reason:
  - `NavigationCardNeedleNeverMovesTest`, 21 of 21: "the needle's left edge ... is not where it is beside the degrees ... (moved ...)"
    for "Target" and "No target" (42 lines), and "... is not where a degrees' space sized for "No target" puts it" (63 lines: all four
    states, the needle being at G3's degrees-only place);
  - `NavigationCardLoneWordTest` 14, `NavigationCardKindLinePortraitTest` 6, `NavigationCardKindLineLandscapeTest` 4: the rules' positive
    control "the needle ... is not where the centred turn line puts it" in Arrived and dash cases (the changed model).
  - No other message, no other class.

### The left part's room with E18's space [observed widths from the run's `ROOM` lines; the E18 needle place inferred from them]

dp. "Needle" is its left edge, G3's place now and E18's; "spare" is from the needle's right edge to the left part's right edge with E18.

| Window, font | Left part | Space (now -> E18) | Turn line (now -> E18) | Widest figure | Needle (now -> E18) | Spare |
|---|---|---|---|---|---|---|
| 320, 1.0 | 143.7 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 80.8 -> 96.7 | 29.0 |
| 320, 1.3 | 116.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 70.5 -> 91.0 | 7.0 |
| 320, 2.0 | 251.0 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 147.8 -> 180.0 | 53.0 |
| 360, 1.0 | 183.7 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 100.8 -> 116.7 | 49.0 |
| 360, 1.3 | 156.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 90.5 -> 111.0 | 27.0 |
| 360, 2.0 | 291.0 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 167.8 -> 200.0 | 73.0 |
| 384, 1.0 | 207.7 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 112.8 -> 128.7 | 61.0 |
| 384, 1.3 | 180.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 102.5 -> 123.0 | 39.0 |
| 384, 2.0 | 151.7 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 98.2 -> 130.3 | 3.3 |
| 412, 1.0 | 130.0 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 74.0 -> 89.8 | 22.2 |
| 412, 1.3 | 208.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 116.5 -> 137.0 | 53.0 |
| 412, 2.0 | 151.7 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 98.2 -> 130.3 | 3.3 |
| 480, 1.0 | 198.0 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 108.0 -> 123.8 | 56.2 |
| 480, 1.3 | 276.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 150.5 -> 171.0 | 87.0 |
| 480, 2.0 | 199.3 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 122.0 -> 154.2 | 27.2 |
| 780 x 360, 1.0 | 176.3 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 444.5 -> 460.3 | about 45 |
| 780 x 360, 1.3 | 137.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 440.0 -> 460.5 | 17.5 |
| 780 x 360, 2.0 | 263.0 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 492.5 -> 524.7 | 59.0 |
| 823 x 384, 1.0 | 176.3 | 28.0 -> 59.7 | 54.0 -> 85.7 | 101.0 | 487.5 -> 503.3 | about 45 |
| 823 x 384, 1.3 | 180.0 | 35.0 -> 76.0 | 61.0 -> 102.0 | 111.0 | 461.5 -> 482.0 | 39.0 |
| 823 x 384, 2.0 | 263.0 | 54.7 -> 119.0 | 80.7 -> 145.0 | 151.7 | 535.5 -> 567.7 | 59.0 |

- Every case fits: the E18 turn line (85.7, 102.0, 145.0 dp by font) stays narrower than the widest figure (101.0, 111.0, 151.7), so
  the left part's need, and with it the left part's width and the distance/kind block (H14, H15), are unchanged in all 21. The
  needle sits 15.8 (font 1.0), 20.5 (1.3) or 32.2 dp (2.0) right of today's degrees place, for good. Tightest: 384 and 412 dp at
  font 2.0, 3.3 dp spare (E17 already had 3.0 there, with "No target").
- The "now" needle places match E17's measured column within 0.5 dp (e.g. 320/1.0: 80.8 against 81.0), which checks the arithmetic.

### Why this stops: one decision E18 changes beyond the needle

- [read, ea4b6e7e8] The before-sunset evening line ("Sunset: 4:52 PM ▪ Dark: 5:23 PM", H5, H6) stays one line only where it fits
  beside the left part's widest need **with the next turn's space reserved** beside the needle (`rememberCardEvening`,
  `nextTurnReservePx`: a gap, the arrow, a gap and "88.8 mi"). That need counts the turn line, so it grows with E18's space.
- [observed, `ROOM` 412/1.0] At **412 dp, font 1.0** the room beside the one-line evening line is 356.0 - 13.0 - 213.0 = **130.0 dp**.
  The need with the reserve is **129.3 dp** today (fits, by 0.7 dp) and **161.0 dp** with E18's space (does not). In the other 20
  cases the decision is the same either way (ROOM lines: "evening breaks" equal; "status below" (H8) equal in all 21).
- So, counted honestly, E18 makes that one window split the before-sunset line at the ▪ (as 320, 360 and 384 dp at font 1.0 already
  do). The right part then keeps the broken width (107.3 dp) instead of 213.0, so the left part grows from 130.0 to about 235.7 dp in
  that window, by day and evening alike [inferred], and the distance/kind block re-centres in it. That is a visible change E18 did not
  rule on, so it is a question for the owner, not a coder's choice.

### Question for the owner (options; nothing built on this branch)

At 412 dp and normal text, the "Sunset ▪ Dark" line only fits on one line today because the room kept for the coming next-turn
arrow is measured with the narrow degrees' space. With the needle never moving (the wider space), which should it be?

- **(a) Count the wider space (the honest room).** On 412 dp phones at normal text, the before-sunset line splits into two at the ▪,
  as it already does on 320 to 384 dp phones, and the left part gets wider there (the distance and "by trail" move to its new centre).
  When the next-turn arrow arrives it has its room. Nothing else changes. (This is what the WIP commit df72cf128 does.)
- **(b) Keep today's room.** Nothing changes on screen beyond the needle. But at 412 dp, normal text, the next-turn arrow (when built)
  would be about 31.7 dp short beside a one-line evening line, so that decision comes back with the next-turn work. One line differs
  from (a): `rememberCardEvening`'s turn line stays on the degrees alone.

The S22 (384 dp) is not in this case either way.

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: the base; the red run, every failure the needle's place (E18's reason), 0 compile errors; the room
  widths above; the evening decision flipping at 412/1.0 only, of the 21 cases.
- **Inferred**: E18's needle places and spare (from the measured widths; not yet drawn); the 412/1.0 left part of about 235.7 dp
  under (a).
- **Could not determine**: widths between the tested ones (a phone between 384 and 412 dp, or near 412, may sit on either side);
  kilometres ("88.8 km" is narrower than "88.8 mi" in most fonts, unmeasured); the phone's own figures.
- **Premises that were wrong**: "the card's left part is a little tighter in every case" (the owner's option text): the left part's
  width does not change in any of the 21 cases (the widest figure is wider than the new turn line); what gets tighter is the room
  inside it right of the needle, and, through the reserve, the one evening decision above.

### Decided beyond the dispatch

- None built. The fix drops E17's needle offset (`needleShiftPx`; with the space holding every word it is always 0) and names the two
  words once (`TURN_LINE_LOST_WORD`, `TURN_LINE_NO_TARGET_WORD`, used by the readout and the space), so the space cannot drift from
  them; both are on the WIP ref only.

### State left

- PR #498 at f1a0a3bbd is red on purpose (the tests first); it is not ready to merge until E18 is built.
- Ref `nav-card-e18-wip` (df72cf128, on the remote): the fix as option (a); to be merged in or deleted by whoever finishes E18.
- Scratch `/mnt/work/scratch/hazel-e18/` kept for the next session: the red run's XML, `ROOM` lines and the fix as a patch.
- Not touched: `nav-card-e17-revert` and `/mnt/work/scratch/hazel-e17/` (not this session's).

## E18: built as option (a), green (a fresh session, 2026-11-07)

Written by Hazel (Opus), dispatch 2026-11-05-10, Amendment 1, E18 and the owner's answer to its stop. Marks as above.

### The ruling (verbatim)

- The owner, to the stop above: **"(a) Count the wider space"** ("The sunset line splits at ▪ into two lines there (as on smaller
  phones), and the turn arrow will have its room"). Amendment 1: "Build: finish E18 from ref nav-card-e18-wip (df72cf128, written as
  (a)), green, one revert, full CI; then E19."

### Base and commits

- At the start [observed]: `origin/nav-card-kind-line` 03fa6e82e, ref `nav-card-e18-wip` df72cf128 (parent f1a0a3bbd), worktree
  clean; `origin/pre-main` 1f6901a11, already merged in, so no merge.

| Commit | What |
|---|---|
| 59a9acfee | The ref **merged** (not re-applied), so df72cf128 stays in the branch's history as written. Four app files, no conflict. |
| 88453efe2 | A test for (a): `NavigationCardEveningLineTest`, "E18 (a), 412 dp, font 1,0 - before sunset the line is broken at the square ...". No test pinned that decision before (the stop's ROOM lines were printed, not asserted). |

- [read, 59a9acfee] It is option (a): `NavigationInfo.kt`'s `rememberCardEvening` measures the turn line for the next-turn reserve
  with `widestTurnSpacePx` (the widest of "0°" to "180°", "Target" and "No target"), the same space the needle's line is laid in
  (`measuredNavigationInfoFit`). E17's needle offset (`needleShiftPx`) is gone; the two words are named once
  (`TURN_LINE_LOST_WORD`, `TURN_LINE_NO_TARGET_WORD`) and read by both the readout and the space.

### Green [observed, run 42386534571 at 88453efe2]

- Every job green. 7017 test cases, 0 failed, 24 skipped (the 412 dp test above passes: before sunset the card reads "Sunset: 8:28 PM"
  and "Dark: 9:00 PM" as two pieces). Run 42386063693 at 59a9acfee was cancelled by the push of 88453efe2 and is not cited.
- By name against E17's green run (42341392562 at ea4b6e7e8, 6995): 0 names gone, 22 new (`NavigationCardNeedleNeverMovesTest` 21,
  the test above 1), **0 names whose outcome changed**.
- By name against pre-main's latest run (42337576510 at 1f6901a11, 6846 tests, 0 failed): 36 names only on pre-main (the same 36
  renamed or replaced in earlier phases, listed in the E13 and E15 sections), 207 only here, all passing, **0 names whose outcome
  differs**.

### The revert [observed, run 42386577696 at b636747be, throwaway ref `hazel-e18-revert`, deleted]

- One line: `rememberCardEvening`'s `widestTurnSpacePx` back to `widestTurnDegreesPx` (option (b)'s line). The file was copied
  before the edit; the revert lived in its own worktree, the forward branch untouched.
- Failed-job log read first: 0 compile errors ("e: " lines none). Then: 6993 tests completed, **1 failed**, in both the build job and
  the no-Street-map job: the test above, "412/1.0: before sunset the card reads [Sunset: 8:28 PM ▪︎ Dark: 9:00 PM], not [Sunset: 8:28
  PM, Dark: 9:00 PM]". That is the one decision (a) changes; no other class failed.
- Restored from the saved copy; the forward line confirmed afterwards on the branch (`widestTurnSpacePx` 3 times in
  `NavigationInfo.kt`, the file byte-identical to the copy).
- The needle's own claim (never moves) was shown red before the fix by the tests-first run 42383172696 (above) and is green here; it
  was not reverted a second time (one revert per the dispatch).

### Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]: (a) is what was built (code read, and the 412 dp test red with (b)'s line, green with (a)'s); full CI
  green; 0 outcome changes by name against E17's green run and against pre-main.
- **Inferred**: the room table in the stop section stands for (a) except 412/1.0, whose left part grows to about 235.7 dp (not
  measured on its own line here; the card's left part at 412/1.0 is not asserted by a test).
- **Could not determine**: widths between the tested ones; the phone's own figures (no phone in this dispatch).
- **Premises that were wrong**: none found.

### Decided beyond the dispatch

- The 412 dp evening test, added so (a) is held by a test rather than by a printed line. Nothing a user sees beyond E18.

### S22 step (E18)

- Portrait, font 1.0, navigating: watch the needle while the line reads degrees, then turn the compass off (or wait 5 minutes with no
  fix): the needle stays exactly where it was while the degrees give way to "Target", and with no target to "No target"; the word never
  touches the needle.
- At 412 dp (not the S22's 384 dp; a 412 dp phone or display size), font 1.0, before sunset: the sunset line reads on two lines,
  "Sunset: ..." over "Dark: ...", split at the ▪.
