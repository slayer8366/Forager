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
