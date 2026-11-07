# The navigation card's distance and kind on one line: completion report (dispatch 2026-11-05-10, Phase 2)

Dispatch `prompts/preserved/2026-11-05-10-nav-card-kind-line.md` (on `origin/records-after-173`), RECORD -2181. Phase 1's report is
`prompts/preserved/2026-11-05-10-phase1-report.md`. The first Phase 2 session's stop report is
`prompts/preserved/2026-11-05-10-phase2-stop-report.md`. Its pinned-test updates are listed there, old and new, and this
session left them as they were, apart from one field rename (below).

Written by Hazel (Opus), 2026-11-06, the second Phase 2 session.

Each claim is marked:
- **read**: file and line, or the named CI run's JUnit XML or log;
- **observed**: command output, or a CI reading named by its run;
- **inferred**;
- **not verified**.

## The owner's words

All are from RECORD -2181, quoted verbatim:
- "For navigation cards have the 2.2 mi on the left half, and then move the "by trail" from the bottom to the top right half, on the
  same line as 2.2 mi";
- "And do the same for landscape";
- "Keep the text size the same size";
- "Meet at the middle (Recommended)";
- "In the left half";
- "Option 1 but stack by on top of trail first, then just shorten it to trail, and then drop it down";
- "Shift the middle a little (Recommended)";
- "Left, crossing as little (Recommended)";
- "At the middle (Recommended)";
- "On the distance's line (Recommended)".

**The planner's calls in this session (2026-11-06)**, by message:
- **Step 2.** "trail" alone is kept in the code in the owner's order and pinned in the pure fit test.
- **The narrowest left part.** "Choose the step for the narrowest room the left part can have while the card shows, across every
  heading and turn word". This falls under the owner's chosen option, "Decided for the longest distance the card could show, so it
  doesn't jump between lines as you walk".
- **Sunset and dusk.** "don't chase it"; report what moves.

## Base

- **pre-main:** `origin/pre-main` is **779599c3f**. The branch was cut from it, and it had not moved at the start or at the end, so
  there was nothing to merge (observed, `git fetch` and `git ls-remote`).
- **Branch:** `nav-card-kind-line`, continued from **ae7ad700c**.
- **Worktree:** the first session's, `~/.cache/forager-wt/nav-card-kind`. It was clean and at origin's head when this session
  started (observed).
- **Untouched** (observed): the planner's worktree, `edge-shade`, `trip-download-maps`, pre-main and main. No stash was used and no
  Gradle ran on the laptop.

## Commits (this session)

| Commit | What |
|---|---|
| 3c8bd44dc | **Tests first:** the new screen classes and the rules helper. The one production line is a test tag on the top line (`NAVIGATION_HUD_TOP_LINE_TAG`), which changes no behaviour, so the tests can read the left part's exact bounds. |
| f8985f721 | **Test fixes found in the first red run** (see Evidence): the rig settles the compass heading, and the rules read the parts' width from the X's touch box. Tests only. |
| f46baf51d | **The change.** The rulings replace the strict reading in the new files. The pure fit test is updated. **The last production commit.** |
| 9c99777a1 | **Two height checks updated** that pinned the old layout (see "Pinned tests updated"). Tests only. **The last code commit.** |
| a6a2aceb5, and the final one | This report only. |

## What a user sees

All figures are in Noto Sans, the app's own type, through the real screen on CI run 42187274279 (observed, its RULES and TOPLINE
lines).
- **"Shift"** below is how far the meeting point moved right of the left part's middle.
- **"Beside"** means on the distance's line and baseline, a word's space (4 dp) after it.

**Normal text size, with "Dark since 5:23 PM"** (the owner's screenshots):

| Window | by trail | straight |
|---|---|---|
| 384 dp portrait (S22) | beside, at the middle | beside, shifted 4.7 dp |
| 360 dp | beside, shifted 0.3 dp (one pixel) | beside, shifted 16.7 dp |
| 320 dp | beside, shifted 20.3 dp, with 0.7 dp to spare | its own line under the distance, at the left part's right edge (as near the meeting point as fits) |
| 823 × 384 landscape (S22) | beside, shifted 4.0 dp | beside, shifted 20.3 dp |
| 780 × 360 landscape | beside, shifted 4.0 dp | beside, shifted 20.3 dp |

**Other cases:**
- **No evening time, normal text size:** both kinds sit beside the distance at the middle, in every window. These cases are the
  first session's, and still pass.
- **"Dark since 10:23 PM" at 320 dp:** a two-digit hour, one digit wider. By trail is **stacked**: "by" over "trail", both starting
  at the meeting point, "trail" on the distance's baseline. The stack takes no height: "by" starts at the distance's top and "trail"
  ends at its bottom. Straight takes its own line.
- **Font 2.0, no evening time:**
  - 384 dp: both beside at the middle; straight has 3.8 dp to spare.
  - 360 dp: by trail at the middle; straight beside, shifted 8.3 dp.
  - 320 dp: by trail beside, shifted 3.7 dp; straight on its own line, 2.0 dp short.
  - Landscape: by trail at the middle, with 3.5 dp to spare; straight beside, shifted 22.3 dp.
- **Font 2.0 with an evening time:** both kinds take their **own line** in every window, under the planner's call (see "Decided
  beyond the dispatch").
- **No kind ("Arrived", the dash), with "Dark since 5:23 PM":**
  - The dash ends at the middle everywhere.
  - "Arrived" ends at the middle at 384 dp.
  - It is wider than its half at 360 and 320 dp, in landscape and at font 2.0. There it starts at the left part's left edge.
- **Card height:** a kind on its own line adds one line, 16.3 dp at normal text size. At 320 dp, straight, the card is 78.0 dp on
  its own line against 61.7 dp beside (observed, TOPLINE card heights). Nothing is drawn under the turn row, as since the first
  session.

**Nothing shrinks** (observed): every kind form is drawn in the turn words' type. Each reading checks this ("Keep the text size the
same size").

**A withheld route grows the card one line more than before.** On the route the card is one line shorter. Before the first route,
and with the route withheld, it is as tall as before. So when the route goes, the card grows by that one line more. For example, the
780 × 360 landscape card with no evening time:
- on the route: 60 dp, against 76 on pre-main [observed, runs 42191218291 and 42191454743];
- before the first route: 76 dp, as on pre-main [observed on pre-main; on the branch it follows from the exact check that passes];
- withheld with Try again: 124 dp [observed]. On pre-main it is at most 124.5 dp, by that test's passing bound; that it is exactly
  124 there is not verified.

This follows from the owner's words, as Phase 1 said ("Wherever the left part sets the card's height, the card loses one line").

## Pinned tests updated (9c99777a1)

Full run 42188117750 (f46baf51d) failed three tests, all height checks. They fail the same way at the first session's head,
ae7ad700c (run 42191218291), so the first session's change caused them, not this one [observed]. Phase 1 had listed
`HudLandscapeT11*` among the tests that "Could change through card height, which only a CI run can say". Neither test was silenced
or loosened. Each
keeps its bound, measured from the state with the same lines.

**`AvailabilityScreenReturnRouteTest`**, "the HUD grows by the route message's line when a route is withheld, and by Try again's row
when it is offered".
- Old:
  - `assertEquals("pending, the HUD's height is the route's", onRoute, pending, 0.5)`;
  - `withheld > onRoute + 0.5.dp && withheld <= onRoute + line + 0.5.dp`;
  - `taller <= onRoute + row + 0.5.dp`.
- New:
  - pending equals the route plus pending's status line, exactly (±0.5 dp);
  - `withheld > pending + 0.5.dp && withheld <= pending + line + 0.5.dp`;
  - `taller <= pending + row + 0.5.dp`;
  - positive controls: the route has no status line; pending's reads "0.7 mi straight".
- Measured: 61 on the route, 77 pending.

**`HudLandscapeT11Fixes`**, item 2, at 780 and 823 ("Try again sits beside its message, the display grows by at most its row …").
- Old: `hud <= onRoute + 48.5.dp`.
- New:
  - a pending step: pending equals the route plus its status line, exactly;
  - then `hud <= pending + 48.5.dp`;
  - positive controls on both status lines.

**Both, before and after:**
- On the branch, 47 of 47 (run 42191411486).
- On pre-main with only these two files changed (run 42191454743), each fails on its new claim:
  - "pending, the HUD is the route's and its status line (16.0.dp) expected:<93.0> but was:<77.0>";
  - "before the first route the display is the route's and its status line …".
- So they read the change [observed].

## Verification before building

| Check | Result |
|---|---|
| Can step 2, "trail" alone, show? | **No** [read + observed]. `kindPlace` takes the stack when its width fits (`NavigationCardTopLineFit.kt`, `kindPlace`). The stack's width is the wider of "by" and "trail", and "trail" is the wider: 25.67 against 14.33 dp at font 1.0. The stack and "trail" alone are both 49.67 dp at 2.0 (the first session's run 42171184260). Wherever "trail" fits, the stack fits first. A user sees three steps: beside, stacked, own line. Sent to the planner by message, and accepted. |
| Does the left part's width follow the turn row? | **Yes, at font 2.0 with an evening time** [observed, run 42187206936]. The fit overflows there, and the left part is as wide as its widest line. By trail measured 196.3 dp facing 169° off ("169°", "281° W"), 143.0 dp facing 1° off ("1°", "89° E") and 121.7 dp with no compass (the figure is then the widest line). Decided on the width drawn, by trail would be stacked at 196.3 dp (65.3 dp of room against the stack's 49.7) and on its own line at the other two. Reported by message; the planner's call followed. |
| The X's bounds | The X's tagged node is its 40 dp icon, not its 48 dp touch box [observed, TOPLINE `exit` 272..312 dp at 320 dp]. The rules helper first read the parts' width from it, 4 dp too wide. Fixed in f8985f721; positive controls now tie that width to the drawn card. |

## What was built

All read at f46baf51d.
- **`NavigationCardTopLineFit.kt`** (new on this branch; 201 lines):
  - `TopLineMeeting` replaces the strict `TopLineHalves`. The figure ends at the middle less half the gap, or right of it just far
    enough for the longest figure, and never past the left part's right edge. The kind starts a gap after that.
  - `kindPlace` takes the steps on the room right of the meeting point.
  - `narrowestLeftPartPx` and `narrowestFigure` implement the planner's call.
  - `topLinePlace` measures and decides. It moved out of `NavigationInfo.kt`.
  - `TopLinePlace` carries the step and the longest figure to the drawing.
- **`NavigationCardTopLine.kt`** (new on this branch; 147 lines): it draws at `TopLineMeeting`. The kind on its own line starts
  where it would beside the distance, or as near it as fits. A word with no kind ends at the middle, or starts at the left edge
  where it is wider than its half. The doc quotes each ruling.
- **`RoundedMapCardFit.kt`:** `NavigationInfoFit.kindPlace` became `topLine: TopLinePlace`.
- **`NavigationInfo.kt`:** it calls `topLinePlace` with the measured widths, and is 300 lines.
- **No change** to words, text styles, the turn row, the status line, the evening stack or the X.

**Tests:**
- **The rig, `NavigationCardKindLineScreen`** (343 lines). It gains:
  - the evening phases: none, "Dark since 5:23 PM", "Dark since 10:23 PM", before sunset, after sunset;
  - three facings: 169° off, 1° off, no compass;
  - the top line's, the X's and the stacked words' bounds;
  - text measured in each node's own type.
- **`NavigationCardTopLineRules`** (new, 190 lines): works the owner's rulings out from the drawn card's own widths, in whole pixels,
  and lists each difference from what was drawn. It is written from the rulings, not from the app's code. Each case also names the
  step it is chosen to reach, and whether the meeting point moved, so a case that drifts to another step fails.
- **New classes:**
  - `NavigationCardKindLineEveningTest` (5 methods);
  - `NavigationCardKindLineStackTest` (4: the stack, the long evening time, and the sunset and dusk phases);
  - `NavigationCardKindLineLargeTextTest` (7: font 2.0 near the thresholds, and turning);
  - `NavigationCardLoneWordTest` (7).
  - All run portrait 384, 360 and 320 dp and landscape 823 × 384 and 780 × 360, in `ForagerTheme`, read by bounds.
- **`NavigationCardTopLineFitTest`** (21 cases):
  - the meeting point, its shift and its cap;
  - the narrowest left part;
  - every step on round numbers, step 2 included;
  - the step holding when the drawn width changes.
- **`RoundedMapCardFitTest`:** `kindPlace` became `topLine`. Its values are unchanged.

## Evidence

Each run's job log was read for compile errors before its XML. A run with any compile error was refused. Each run's head commit was
checked against the one it was meant to build (observed, `fetch.sh`).

**Red before the change:**
- **Run 42185116473** (3c8bd44dc): no compile errors. 16 of the 16 new methods that the branch could fail failed.
  - The 7 lone-word methods passed. They cannot fail on the branch, because its placeholder already matched the ruling. Revert R7
    below holds them.
  - The first session's 10 still passed.
  - Every message is specific to this change:
    - "the kind is drawn OWN_LINE, this case is chosen to reach BESIDE" (or STACKED);
    - "the distance … does not end at the meeting point";
    - "the kind on its own line … does not start at the meeting point or as near it as fits".
  - No positive control fired.
- **Run 42187206936** (f8985f721): the same after the test fixes. No compile errors; the same 16 failed, the same 17 passed (33).
  No positive control fired.
- **What the first red run caught in the tests:** its "1° off" reading had drawn heading 290° W. The heading is smoothed, 0.3 a
  reading (`HeadingSmoother.kt:23`), so one reading of 89° after 281° only moved it part of the way. The rig now reads the heading
  30 times, and the turning case checks the headings and turn words drawn.

**Green after:**
- **Run 42187274279** (f46baf51d), filtered to the 12 affected classes. No compile errors. **88 of 88 passed:**
  - the six top-line screen classes, 33 (this session's four and the first session's two);
  - `NavigationCardTopLineFitTest`, 21;
  - `AvailabilityScreenNavigationWordsTest`, 8;
  - `NavigationCardFitAcrossWidthsTest`, 12;
  - `NavigationInfoLayoutTest`, 4;
  - `NavigationHudKindInStatusTest`, 1;
  - `RoundedMapCardFitTest`, 9.
- In every reading, the step drawn is the one the rules helper works out (observed, RULES lines).

**Revert checks.** Under the runner rules in CLAUDE.md "Testing":
- **How each revert was made.** Each is one edit to the forward commit f46baf51d, made on a copy in scratch. Its commit was built
  through a temporary index and pushed to a throwaway ref. The working tree and the real index were never touched, so nothing was
  restored, from git or otherwise.
- **The forward change.** Afterwards every forward line was confirmed in the working tree and at HEAD (observed).
- **Each run:**
  - its job log had no compile errors;
  - its head was its own revert commit;
  - it ran the six top-line screen classes and the pure fit test, 54 tests;
  - every failure is one its own edit can produce, and nothing else failed (observed, `read.py` over each run's XML).

| | The edit | Run | Failed | Messages, each specific to its edit |
|---|---|---|---|---|
| R1, the kind moving up | the kind beside the distance placed back under the turn row, centred, as the old third line | 42188215292 | 21 of 54 | "the kind … is not above the turn row" (46 times); "drawn OWN_LINE; the rulings make it BESIDE"; the first session's "does not start at the middle (plus the half gap)" and "the kind's baseline … is not the distance's". Still passing: the lone word, the pure fit, and every case already on its own line. |
| R2, the middle | the figure centred in the left part again, as on pre-main | 42188219120 | 33 of 54 | "the distance … does not end at the meeting point"; "… does not end at the middle"; "<Arrived> …, wider than its half, does not start at the left part's left edge". The pure fit passes. |
| R3, the shift | the meeting point never moves right of the middle | 42188222877 | 19 of 54 | "the kind … does not start at the meeting point"; "the distance … does not end at the meeting point"; "drawn BESIDE; the rulings make it OWN_LINE"; the fit test's three shift cases. Still passing: the cases with no shift (the first session's 10) and the lone word. |
| R4, the stack | the stack step removed | 42188226133 | 4 of 54 | "the kind reads <trail>, not <by trail>"; "drawn BESIDE, this case is chosen to reach STACKED"; in the fit test, "expected:<STACKED> but was:<SHORT>". With the stack gone, step 2 ("trail" alone) appears, the only way it can. |
| R5, step 3 | the kind on its own line ending under the distance (the option "Ending under the distance") | 42188230106 | 10 of 54 | all ten: "the kind on its own line … does not start at the meeting point or as near it as fits". |
| R6, the planner's call | the step decided on the width drawn | 42188233819 | 8 of 54 | "drawn STACKED; the rulings make it OWN_LINE"; "drawn STACKED, this case is chosen to reach OWN_LINE" (font 2.0 with an evening time); the fit test's turning case. |
| R7, the lone word | a word with no kind centred (the option "Centred, as today") | 42188237814 | 17 of 54 | "does not end at the middle"; "<Arrived> …, wider than its half, does not start at the left part's left edge". This is what holds the lone-word cases, which could not fail on the branch. |


**Full CI.** Run 42192499972 at a6a2aceb5. Its code is 9c99777a1's; the difference is this report. Every job passed, the job log
has no compile errors, and the Street-map-deleted job also passed [observed]. Compared by test name with pre-main's run
42167274295 at 779599c3f (observed, `compare.py` over both runs' JUnit XML):

| | pre-main | this branch |
|---|---|---|
| tests | 6768 | 6823 |
| passed | 6744 | 6799 |
| skipped | 24 | 24, the same names |
| failed | 0 | 0 |

- **Outcome differs for:** no test name in both runs.
- **Only on pre-main:** 3 names, the first session's three renamed pinned tests:
  - `AvailabilityScreenNavigationWordsTest` "the longest lines leave the display as tall as short ones";
  - `NavigationHudKindInStatusTest` "at 320 dp the kind is a small line of its own under the turn line, whole";
  - `NavigationInfoLayoutTest` "… the left part's lines share one centre".
- **Only here:** 58 names: those three under their new names, and 55 new:
  - Evening 5, Stack 4, LargeText 7 and LoneWord 7 (this session);
  - Portrait 6 and Landscape 4 (the first session's);
  - `NavigationCardTopLineFitTest` 21;
  - `RoundedMapCardFitTest` 1.
- **Arithmetic:** 6768 − 3 + 58 = 6823.
- **The earlier full run**, 42188117750 at f46baf51d, is the one that found the three height checks: 6823 tests, 3 failed.

## Sunset and dusk: what moves

The planner: "don't chase it". These are measured readings (observed, run 42187274279, `NavigationCardKindLineStackTest`):
- **The widest evening piece:**
  - before sunset, "Sunset 8:28 PM" (95.3 dp);
  - after sunset, "Sun set 5:50 PM" (98.7 dp);
  - after dusk, "Dark since 5:23 PM" (118.0 dp).
- **320 dp portrait.** The left part is 251.0 dp with no evening time, 155.7 dp before sunset, 152.3 dp after sunset and 133.0 dp
  after dusk.
  - **By trail** stays beside throughout. Its meeting point is shifted 9.0, 10.7 and 20.3 dp in the three phases. Shifted, it sits
    at the longest figure's width from the left part's left edge, so the distance and "by trail" do not move at sunset or at dusk
    [inferred from the rule and the measured shifts].
  - **The turn row** is centred in the left part, so it moves left by half each change: 1.7 dp at sunset and 9.7 dp at dusk
    [inferred].
  - **Straight** is beside before sunset, with 1.3 dp to spare. **At sunset it drops to its own line, and the card grows from 61.7
    to 78.0 dp.** It stays on its own line after dusk.
- **823 × 384 landscape.** The left part is 283.7, 188.3, 185.0 and 165.7 dp.
  - **By trail** is at the middle before sunset and after it, so it moves 1.7 dp left with the middle at sunset. At dusk it is
    shifted 4.0 dp, and the meeting point moves 5.8 dp left [inferred from the widths].
  - **Straight** is shifted throughout (9.0, 10.7, 20.3 dp), so it holds its place. It stays beside throughout.

## Not tested

- **The S22 itself.** Real type rendering, and the phone's real insets: Robolectric reports none, and these widths do not depend on
  them [inferred]. See the device check below.
- **A screen reader on the stack.** It is the first session's: a column with the text "by trail" and two word texts, not merged.
  Whether TalkBack reads it once or three times is not verified.
- **Metric units on screen.** By trail's longest metric figure, "88.8 km", is wider than "88.8 mi" (92.3 against 84.7 dp at font 1.0,
  the first session's run 42171184260), so metric shifts further [inferred]. Only the pure fit test covers it.
- **Font scales between 1.0 and 2.0**, and windows other than the five.
- **The stack or its own line on a stale, dimmed fix.** The dimmed colour is passed to every form [read], but none is checked.
- **A figure wider than the longest** (a trip in another area, "123.4 mi"). It is measured as itself [read], but not drawn.

## Device check (S22), cheapest first

Install the APK from the PR's CI run. The phone's own font and display size, unless a step says otherwise. An evening time shows only
while recording, from the hours before sunset. Steps 3, 4 and 6 need one.

1. **Portrait, the way home along a route (by trail), any hour.**
   - Pass: "<distance> by trail" on one line and one baseline, with a word's space between them, and nothing under the turn row.
   - Evidence: a screenshot of the card.
2. **Portrait, a waypoint with no route (straight), any hour.**
   - Pass: "≈ <distance> straight" on one line, as in step 1.
   - Evidence: a screenshot.
3. **Steps 1 and 2 with an evening time showing.**
   - Pass: by trail is beside, ending at the left part's middle. Straight is beside, about 5 dp right of the middle.
   - Evidence: two screenshots.
4. **Landscape (the card on the bottom edge), steps 1 and 2 with an evening time.**
   - Pass: both are beside. "by trail" is about 4 dp right of the middle; straight is about 20 dp right.
   - Evidence: two screenshots.
5. **"Arrived"** (navigate to a waypoint where you stand).
   - Pass, in portrait: "Arrived" ends at the middle, with nothing to its right in the left part.
   - Observation, not a gate: in landscape with an evening time it starts at the left part's left edge.
   - Evidence: a screenshot.
6. **The largest font size, with an evening time.**
   - Pass: by trail is on its own line under the distance and above the turn row, and stays there as the phone turns (no flip to
     "by" over "trail").
   - Observation, not a gate: the word slides with the left part's right edge as the phone turns (see Open items).
   - Restore the font size afterwards.
   - Evidence: two screenshots, facing away from the target and towards it.

Observations, not gates:
- the sunset and dusk moments above;
- the card's height in each step.

## Decided beyond the dispatch

1. **A word with no kind ends at the middle, never at a shifted meeting point.** The owner: "In the left half"; the shift is set for a
   kind's longest figure, and a word with no kind has none. So where the meeting point had moved, the word ends up to that shift
   left of where the number ended when the number becomes "Arrived". For example, 4.7 dp at 384 dp, straight, with an evening time
   [inferred].
2. **The meeting point stops at the left part's right edge.** Where even the whole left part is narrower than the longest figure, the
   distance ends at that edge and the kind takes its own line. This is reached at font 2.0 with an evening time and no compass: a
   121.7 dp left part against a 127.0 dp longest figure [observed].
3. **"The narrowest the left part can be".** My reading of the planner's call is the widest of these three, taken where the evening
   piece leaves the left part short:
   - the narrowest figure, "8 ft" or "8 m";
   - the needle, a gap and the crossed-out compass (no turn words: on the approach, or with no compass);
   - the kind itself, which the fit still counts as a line of the left part.

   This counts the figure as well as the headings and turn words, so the step does not change as the number counts down either. At
   font 2.0 with an evening time the narrowest is the kind's own width (86.3 dp by trail, 97.3 straight), which leaves no room
   beside it [observed].
4. **The kind on its own line is placed on the width drawn**: "starting at the meeting point, or as near it as fits".
5. **A test tag on the top line in production** (3c8bd44dc). Behaviour-neutral.

## Open items for the owner (reported, not stopped for)

- **Font 2.0 with an evening time: the planner's call drops by trail to its own line where the rulings would stack it.** It is
  stacked at the drawn width facing 169° off, in all five windows (196.3 dp, 65.3 dp of room against the stack's 49.7). It is on
  its own line facing 1° off and with no compass under either reading.
  - So under this build the stack shows only at normal text size with a wide evening time on a narrow phone (320 dp, "Dark since
    10:23 PM").
  - It never shows at font 2.0 in the five windows measured [observed].
- **At font 2.0 with an evening time, the kind on its own line slides as the phone turns.** It sits at the left part's right edge,
  and the left part follows the turn row. By the formula it starts at 110.0, 56.7 and 35.3 dp from the left part's left edge at
  196.3, 143.0 and 121.7 dp [inferred]. The rulings place it "as near it as fits"; a fixed place would need a new rule.
- **Sunset at 320 dp, straight:** the kind drops to its own line and the card grows 16.3 dp (see above).

## Premises that were wrong

- **"Each step" can be tested through the screen.** Step 2 can't be reached.
- **"Each new test must be red on the branch before your change".** The lone-word tests can't be, because the first session's
  placeholder already matched "Left, crossing as little". Revert R7 holds them instead.
- **"Font 2.0 near the thresholds".** With an evening time and under the planner's call, nothing at 2.0 is near a threshold (the room
  is −4 dp). The 2.0 near-threshold cases are without one: straight 3.8 dp to spare at 384, 2.0 dp short at 320; by trail 3.5 dp to
  spare in landscape.
- **The first session's stop report:** "At font 2.0 with an evening time, by trail stacks". That holds at the drawn width with the
  longest turn words, and not under the planner's call.

## Stops

One message to the planner during the run: step 2, and the turn row at font 2.0. The answer was "Carry on", with the calls above.
Nothing else stopped the work.

## Clean-up

- **Deleted:**
  - the first session's throwaway branches `nav-card-kind-measure` and `nav-card-kind-red`, on origin and locally;
  - its worktree `~/.cache/forager-wt/nav-card-kind-measure`, which was clean;
  - its scratch `/mnt/work/scratch/nav-card-kind/`.
  
  This report cites only CI runs, which outlive their branches.
- **This session's ten throwaway refs** are deleted from origin, each after its run finished: the green ref, the seven reverts, the
  run at ae7ad700c and the pre-main height check.
- **This session's scratch**, `/mnt/work/scratch/nav-card-kind-2/`, is deleted at the end.
- **Left:** `nav-card-kind-line` and its worktree `~/.cache/forager-wt/nav-card-kind`. No index row was added to
  `docs/audits/README.md`: rows there are written at merge.
- **Location:** no coordinate, grid reference or tile number appears in anything this session pushed. The tests use the made-up
  point P, as the first session's do.

---

# Phase 3: "Arrived" and the dash centred (B3, B4), and the notes that only say why leave the card (F2)

Written by Hazel (Opus), 2026-11-06, the Phase 3 session. Amendment 1 is `prompts/preserved/2026-11-05-10-amendment-1.md` on
`origin/records-after-173` (44a6476ee when this session started; b2ef0c0c2 added G and E7 to E8 while it ran). Marks as above.

## The owner's words (Amendment 1, verbatim)

- **B3:** "Arrived is simply centered with no extras"; **B5** confirms it: "make Arrived center".
- **B4:** "Centre it like Arrived (Recommended)", the option reading "The "—" sits centred in the left part, the same as "Arrived",
  so a word or mark on its own is always centred."
- **F1:** "In the same PR, remove the lines "No trails routed here - straight line"/"The waypoint is in another area - straight
  line" the straight line is the explanation".
- **F2:** "Keep the ones asking something": "Remove the ones that only say why (no routes here, another area, far from any trail,
  no clear way in…). Keep "Trail routes not downloaded" and "Trail routes need an app update", since you could act on them."

G (fixed spaces) and H (the strip and the status words) came in while this session ran. They are not built here: see "G and H".

## Base

- `origin/nav-card-kind-line` was **50808417f** (pre-main 6cfcd8d6c merged into 7281cd357) and `origin/pre-main` **6cfcd8d6c**, as
  dispatched [observed, `git fetch`, `git rev-parse`].
- **Starting point:** the PR's run 42207744373 at 50808417f, every job green [observed].
- **Worktree:** `~/.cache/forager-wt/nav-card-kind`, clean and at 50808417f [observed]. No stash; no Gradle on the laptop.
- **One coder per file:** every file below was checked against `git diff --name-only 6cfcd8d6c origin/home-location-out`
  (4e8b01106) before it was edited. Only `TripNavigateTest.kt` was shared (home-location-out changes its line 97). That was a
  stop; the planner handed the file to this phase (Amendment 1, E8). Line 97 is untouched here.

## Commits

| Commit | What |
|---|---|
| 53d9bc0dc | **Tests first**, tests only. The lone-word claims change to centred; the claims pinning the 11 notes' words change to no status line; a new card-height test. |
| 4d4519459 | **The change.** B3 and B4 in `NavigationCardTopLine.kt`; F2 as `WAY_TO_NOTES_ON_CARD` in `NavigationHudText.kt`, read by two branches of `navigationReadout`. Docs in `NavigationCardTopLineFit.kt`. **The last code commit.** |
| (this report) | The report only. |

## What was built

- **B3 and B4** (`NavigationCardTopLine.kt`, the figure's x in `CardTopLine`) [read]:
  `if (kind == null) maxOf(0, (width - figure.width) / 2) else maxOf(0, meeting.figureEndPx - figure.width)`.
  - A figure with no kind is centred in the left part. Only two such figures exist: "Arrived" and "—" [read].
    - The "within 16 ft" figure has no kind, but it can never reach the top line. Inside the fix's circle is always inside the
      arrival radius: `hasArrived` uses max(15 m, 2 × accuracy), `domain/NavigationReadout.kt:67-68`.
  - **A word wider than the left part** starts at the left part's left edge and is cut at its right edge. The figure is measured no
    wider than the left part, so centring puts it at 0. That is what the centred column did before this dispatch (pre-main
    `NavigationCardParts.kt`: a `Column` with `CenterHorizontally`, the text `maxLines = 1`, `softWrap = false`) [read; inferred
    for the cut].
    - No measured case reaches it. The widest is "Arrived" at font 2.0 with "Dark since 5:23 PM": 132.3 dp in a 137.7 dp left part
      [observed, run 42210751662].
- **F2** (`NavigationHudText.kt`, `navigationReadout`) [read]:
  - `WAY_TO_NOTES_ON_CARD` holds ROUTES_NOT_DOWNLOADED and ROUTES_NEED_APP_UPDATE.
  - A `ReturnRoute.Straight` with any other note gives an empty status and no short form. The card then draws its zero-height
    status node (`NavigationCardLines.kt:146-149`), so there is no line.
  - **Unchanged:** the notes, their straight-line choice, `WayToRetry`'s set, the log line (which names the note only), the way
    home's notes, and the Plot Route card's.
  - **The 11 notes' words stay in the code**, in the enum and in `WayToTripNotes.kt`. No screen reads them now [read]. Their only
    path to the screen was `NavigationReadout.kt:195` and `:212`, into `NavigationCardLines.kt:144`. The card and the portrait strip
    share that path. No TalkBack announcement or notification reads them (checked with the planner, who agreed).

## What a user sees

- **"Arrived"** sits centred in the left part, over the turn row's centre, in portrait and landscape and at every text size.
  Before, it ended at the middle, or started at the left edge where it was wider than its half.
- **"—"** (no fix, no route yet, a withheld route) sits centred the same way.
- **A note that only says why** (11 of 13) leaves no status line, so the card is one line shorter there [observed, run 42210751662,
  `NavigationCardStatusNoteTest`]:

| Window | With "Trail routes not downloaded" | With "No trail routes here" (was) | Now | Line |
|---|---|---|---|---|
| 384 dp, font 1.0 | 78.0 dp | 78.0 dp | 61.7 dp | 16.3 dp |
| 320 dp, font 1.0 | 78.0 | 78.0 | 61.7 | 16.3 |
| 823 × 384, font 1.0 | 77.0 | 77.0 | 60.7 | 16.3 |
| 780 × 360, font 1.0 | 77.0 | 77.0 | 60.7 | 16.3 |
| 384 dp, font 2.0 | 121.7 | 121.7 | 89.0 | 32.7 |
| 823 × 384, font 2.0 | 120.7 | 120.7 | 88.0 | 32.7 |

  - The "was" column is the red run's (42210654926, before the change).
  - The two notes that stay still show: "Trail routes not downloaded · straight line" whole at font 1.0, the short form at 2.0.
- **Nothing else changes:** the kind, the stack and the own-line steps, the turn row, the evening stack, the X, and every other
  status ("Approaching", "Off trail", the way home's notes, the straight-line note before the first route).

## Evidence

Every run's build log was read for compile errors before its XML, and its head checked against the commit it was meant to build
(`read.py`). None had a compile error.

- **Red:** run 42210654926 at 53d9bc0dc, the 11 affected classes. **35 of 69 failed**, exactly the 35 changed or new cases:
  - lone word 14;
  - portrait 6 and landscape 4 (the lone words in them);
  - status note 6;
  - the readout's note test 3;
  - `AvailabilityScreenWaypointRouteTest` 1;
  - `TripNavigateTest` 1.

  Every message is specific to its ruling. For example:
  - "<Arrived> … is not centred in the left part: its centre 48.3 dp, the left part's middle 90.5 dp";
  - "the note that only says why still reads <No trail routes here · straight line>: F2 leaves no status line";
  - "no status line … expected:<[]> but was:<[No clear way in to the trip · straight line]>".

  No positive control fired. The new positive control, "a note that asks something still shows on the way to a trip", passed.
- **Green:** run 42210751662 at 4d4519459, the same classes, **69 of 69**.
- **Revert checks** (CLAUDE.md, "Testing").
  - **How each was made.** One edit to 4d4519459, made on a copy in scratch and committed through a temporary index to a throwaway
    ref. The working tree and the real index were never touched, so nothing was restored, from git or otherwise.
  - **Afterwards** the forward lines were confirmed in the working tree and at HEAD, which equals origin [observed].
  - **Each run:** a build log with no compile errors, its head its own revert commit, the same 11 classes (69 tests).
  - **Each failure** names only its own ruling's state [observed, `read.py`, and a check that no failing line names the other
    state or a positive control].

| Revert | The one-line edit | Run | Failed (of 69) | Messages |
|---|---|---|---|---|
| B3, "Arrived" | `if (kind == null && columnFigure(readout, fit.figureShort) != ARRIVED_TEXT)`: "Arrived" back to B1/B2's place | 42213434397 | 14: the 7 "Arrived" lone-word methods, and the 7 portrait and landscape methods holding an "Arrived" reading | only "<Arrived> … is not centred …" (14 lines); no dash, no status line |
| B4, the dash | the same line with `!= NO_HEADING_TEXT` | 42213437126 | 17: the 7 dash lone-word methods, and the 10 portrait and landscape methods holding a dash reading | only "<—> … is not centred …" (17 lines); no "Arrived", no status line |
| F2 | `WAY_TO_NOTES_ON_CARD = WayToWaypointNote.entries.toSet()`: all 13 notes' words back | 42213441190 | 11: status note 6, the readout's note test 3, the waypoint screen 1, the trip 1 | "still reads <No trail routes here · straight line>", "expected:<[]> but was:<[…]>"; nothing about centring. The note that stays still passes. |
- **Full CI:** the PR's run 42210755247 at 4d4519459. Every job passed, including the Street-map-deleted job, lint and the release
  compile. The build log has no compile errors. **6906 tests: 6882 passed, 24 skipped, 0 failed.**
  - Compared by name with pre-main's run 42202397416 at 6cfcd8d6c (6836 tests, 6812 passed, 24 skipped) [observed, `compare.py`].
  - **Outcome differs:** none of the names in both runs. The skipped names are the same.
  - **Only on pre-main:** 7 names. Three are Phase 2's renames, listed above. Four are this phase's:
    - `AvailabilityScreenWaypointRouteTest` "… with the note";
    - `NavigationWayToWaypointNoteTest` "… the note in its status line" and "the short forms drop the tail, …";
    - `TripNavigateTest` "… a note that names the waypoint names the trip".
  - **Only here:** 77 names:
    - Phase 2's 58;
    - lone word +7 (the 7 methods split into 14);
    - status note 6;
    - the readout's note test 3 (2 renamed, 1 new);
    - `AvailabilityScreenWaypointRouteTest` 1 renamed;
    - `TripNavigateTest` 2 (1 renamed, 1 new).
  - **Arithmetic:** 6836 − 7 + 77 = 6906.

## Tests whose claims changed (none silenced, no tolerance widened, no assertion dropped)

Each test keeps its old claim quoted in its doc.
- **`NavigationCardTopLineRules`, the lone-word branch:**
  - Was "ends at the middle", or "starts at the left part's left edge" where wider than its half.
  - Now "centred in the left part" (±0.5 dp, as before), with a positive control that the turn row's centre is the left part's
    middle (±1 dp).
  - The "wider than its half" positive control stays, so the cases still cover both earlier rules.
- **`NavigationCardKindLineScreen.meetsAtTheMiddle`:** a word alone now has to be centred on the middle (±1 dp, as before). The
  rig gains two states, the straight line with a note that goes and one that stays, and the status node's bounds.
- **`NavigationCardLoneWordTest`:** split into an "Arrived" and a dash method per window, 14 in all, so each revert reads by name.
- **Method names that would now be false** were renamed:
  - portrait 6 and landscape 4 ("the dash ends at the middle" became "the dash is centred", and the like);
  - `NavigationWayToWaypointNoteTest` 2;
  - `AvailabilityScreenWaypointRouteTest` 1;
  - `TripNavigateTest` 1.
- **`NavigationWayToWaypointNoteTest`:**
  - every note still makes the straight HUD;
  - the two that ask show whole, with their short forms;
  - the 11 show no status line and offer no short form;
  - new: a trip's three wordings show nothing.
- **`AvailabilityScreenWaypointRouteTest` and `TripNavigateTest`:** no status line, with a positive control that the note is still
  the one chosen. `TripNavigateTest` also gains the positive control for a note that stays.

## S22 session 20 (Amendment 1, E4): the rig's left part against the phone's

Read only; no code was changed for it. My figures come from this branch's Phase 2 RULES lines (run 42187274279) and the session's own
readings in RECORD -2181.
- **Portrait: the left parts match.**
  - The rig's is 197.0 dp at 384 dp with "Dark since 5:23 PM". The phone's runs from 3.9 to 201.5 dp, 197.6 dp. They differ by
    0.6 dp [observed on CI; the phone's from the record].
  - **The smaller shift is the figure.** "≈ 8888 ft" is 101.0 dp on CI and 99.8 dp on the phone, so the meeting point needs 1.2 dp
    less. The shift comes to 99.8 − (197.6 / 2 − 2) = 3.0 dp, against the 2.9 measured [inferred].
  - So the phone draws the type a little narrower than Robolectric's native graphics [inferred].
- **Landscape: the phone's left part is wider, and I could not tell why.**
  - Straight's 10.7 dp shift with a 99.8 dp figure puts the phone's left part at about 2 × (99.8 − 10.7 + 2) = 182 dp. The rig's is
    165.7 dp, so the phone's is about 16.5 dp wider [inferred].
  - By trail's 0 shift is consistent with that: its longest, "8888 ft", is 84.7 dp on CI, under the phone's 89 dp middle.
  - The narrower type explains at most about 1.5 dp, through the right part's widest piece.
  - So the rest is the landscape card's own width, which is the compass strip's (it hangs under the strip at the strip's width). That
    width depends on the strip's readouts and the window's insets, and Robolectric reports no insets. Which of these accounts for it,
    I could not determine.

## Device check (S22), cheapest first

The PR's own APK (run 42210755247) is versionCode 4639 [inferred from `git rev-list --count 4d4519459`]. That is below the
phone's 4703 from session 20, so installing it would be a downgrade. A combined build like session 20's (tools/s22-combined with
nav-card-kind-line at 4d4519459) is needed, made and green on CI before the phone is asked for. The phone's own font and display size
unless a step says otherwise. Steps 1 to 4 can be reached as in session 20, with "Test wp H" outside any downloaded area.
1. **"Arrived", portrait.**
   - Pass: "Arrived" is centred in the left part. Its centre is the turn row's centre (needle and north arrow), and the left part's
     right half is not empty while the left is full.
   - Evidence: a screenshot. Sample the word's left and right pixels and the divider.
2. **"Arrived", landscape** (the card on the bottom edge), with an evening time showing. Pass and evidence as step 1.
3. **"Arrived" at the largest font**, portrait. Pass as step 1. Restore the font size afterwards.
4. **"—"**, in portrait, landscape and at the largest font: the dash before the first fix or route (for example right after
   Navigate, before a fix). Pass: it is centred as "Arrived" is.
5. **A note that only says why: no status line.**
   - Navigate to "Test wp H" (outside any downloaded area: "No trail routes here").
   - Pass: "≈ <distance> straight" on the top line, the turn row under it, and nothing under the turn row. The card is one line
     shorter than session 20's straight card: about 16 dp at the phone's font.
   - Evidence: a screenshot.
6. **Optional, from far away:** a waypoint in another downloaded area ("in another area"). Pass as step 5.
7. **Observation, not a gate:** a note that stays, "Trail routes not downloaded". It needs a region whose routes were never fetched,
   so it is likely not reachable without changing the phone's data. The no-changes rule applies; ask first.

## G and H (added while this session ran): not built here

The planner's messages of 11:23 and later, and Amendment 1 at b2ef0c0c2, add:
- G2, dropping the turn words;
- G3's four fixed spaces;
- H1, the strip keeping its north arrow and heading, the card's turn line losing them;
- H2, the status words under the evening line.

They are not built in this session. The questions below are ones the planner's reading does not settle, so each is a stop (G: "Anything
this reading can't settle is a stop"). This session also reached its own working limit. The widths are in the stop message to the
planner, from throwaway run 42216121500 (a measurement test that was never merged).

Widths are in the card's own type (ForagerTheme, Noto Sans), at font 1.0 / 2.0, in dp [observed, run 42216121500]:
- **The parts:** the card's parts are 328 dp wide at 384 dp portrait, less the divider's 13 dp, leaving 315 dp. They are 264 dp at
  320 dp and 296.7 dp in both landscape windows (Phase 2's TOPLINE lines).
- **Lines:** a status line is 16.3 / 32.7 tall; the distance's line 32.7 / 49.3.
- **Figures:**
  - "≈ 8888 ft" 101.0 / 151.7;
  - "8888 ft" 80.7 / 121.7;
  - "88.8 mi" 84.7 / 127.0;
  - "616 ft" 67.0 / 101.0;
  - "Arrived" 88.7 / 132.3.
- **Kinds:** "by trail" 43.7 / 86.3; "straight" 49.3 / 97.3.
- **Turn degrees (G2):** "180°" 28.0 / 54.7. The words they replace, "sharp right 169°", were 101.0 / 200.3.
- **Evening pieces:**
  - "Dark since 5:23 PM" 118.0 / 233.7;
  - "Dark since 10:23 PM" 125.3 / 248.3;
  - "Sunset 8:28 PM" 95.3;
  - "in 2 h 28 min" 80.3;
  - "dark 9:00" 57.7;
  - "Back by 10:30 PM" 107.7.
- **Status words, shortest forms:**
  - fit the 118.0 right part: "Off trail" 48.7, "45 s old" 48.0, "Back on trail" 78.3, "Approaching" 79.3, "Return to Route"
    99.3, "Last fix 45 s ago" 100.3, "≈ 1250 ft straight" 107.3;
  - wider than it: "No trail routes here" 122.7, "Couldn't find a trail route" 158.3, "Trail routes not downloaded" 175.3, "Off
    trail · Head back to the line" 192.0 (no shorter form on the way home), "Trail routes need an app update" 198.7, "Route leaves
    your downloaded area" 222.7.

**The stops:**
1. **H1 loses the facing notices.**
   - The facing notices are "Calibrating compass" (127.0) and the north-up notice. They exist only as the card's heading label
     (`NavigationReadout.kt:57-61`, with `COMPASS_CALIBRATING_TEXT` and `COMPASS_NORTH_UP_TEXT` read nowhere else). That is
     dispatch 2026-09-28-430's ruling E: "one place, one word … this label is the notice; no separate one is drawn on the map".
   - The facing states exist only while navigating, so the strip, kept "exact same … as out", never shows them.
   - With the heading gone from the card, they would show nowhere.
   - **Options:**
     - (a) they join the status words (H2's space);
     - (b) the strip shows them in its heading's place while navigating, so the strip is not quite the same;
     - (c) they go.
2. **H2's width.**
   - The right part is as wide as its widest evening piece (118.0 at 1.0). With no evening line it has no width at all
     (`reserveRight`).
   - Six of the status words' shortest forms are wider than 118.0 (above).
   - Sizing the right part for the widest status form, 222.7, leaves the left part 315 − 222.7 = 92 dp at 384 dp. That is narrower
     than "≈ 8888 ft", 101.0. At font 2.0 nothing fits.
   - **Options:**
     - (a) only the short status words go up ("Approaching", "Off trail", "Back on trail", "Return to Route", the fix's age), while
       the notes (the way home's, and the two straight-line notes that stay) keep their own line under the card;
     - (b) a status wider than the right part takes its own line under the card, as today;
     - (c) the right part grows for the status, and the left part shrinks.
   - **Also unsettled:** what the space is sized for when no evening line shows, since the planner's reading says it "stays put
     whether or not the evening line shows".
3. **H2's height.**
   - "The card is two lines tall" holds only with a one-piece evening line ("Dark since …").
   - Before sunset the evening stack is three pieces ("Sunset 8:28 PM", "in 2 h 28 min", "dark 9:00"), each its own line (RECORD
     -941), and Back by adds more. "Under the evening line" is then the fourth line or lower.
   - **Options:**
     - (a) under the whole stack, so the card grows with it, as today;
     - (b) always the right part's second line, with the later evening pieces below it;
     - (c) the later pieces step aside while a status shows.

**Not stops, for the build:**
- **G3's evening line:** the pieces are already centred in the right part (`EveningStack`, `NavigationCardLines.kt:70` and `:78`,
  `CenterHorizontally`) [read]. The right part is sized for the widest piece, so a single piece fills it exactly. The owner's "starting
  at its left edge" may be that. A screenshot sample would settle it.
- **H's "strip hidden while navigating":** the strip is composed unconditionally on the Maps tab (`AvailabilityCompactMapUi.kt:621`)
  and stays in fullscreen (`CompactMapTopStrip`'s `isFullscreen` changes only its corners). I found nowhere that hides it while
  navigating [read, not exhaustive].
- **G2's turn words** reach only the navigation readout (`NavigationReadout.kt:159`, `ApproximatePositionHud.kt:92`) and the card's
  fit [read].


## Decided beyond the dispatch

1. **B4 is built as "every figure with no kind is centred"**, not as a list of the two words. "Arrived" and "—" are the only ones
   (above), and B4's option reads "a word or mark on its own is always centred". So each revert is a one-line edit that sends one
   word back to B1/B2's place.
2. **The 11 notes' words stay in the code** (the enum, `WayToTripNotes.kt`, `wayToWaypointNoteShortText`), unread on screen. "The
   notes themselves are unchanged", and the revert is one line. Deleting them would be a separate choice.
3. **The rig gained two states** (a note that goes, a note that stays) and the status node's bounds. These are tests only.
4. **Renamed test methods** whose names would have become false; listed above.

## Confirmed, inferred, could not determine, premises that were wrong

- **Confirmed** [observed]:
  - the base;
  - every run's head and its clean compile;
  - red 35 of 69, then green 69 of 69;
  - the three reverts, each failing only its own cases;
  - full CI 6906 tests, 0 failed, no outcome differing by name from pre-main;
  - the card heights above.
  - [read]: the 11 notes' one path to the screen, and that only "Arrived" and "—" stand alone.
- **Inferred:**
  - that a word wider than the left part is cut at its right edge, as before this dispatch (no measured case reaches it);
  - the portrait S22 explanation.
- **Could not determine:**
  - why the phone's landscape left part is about 16.5 dp wider than the rig's;
  - whether a note that stays can be reached on the S22 without changing its data.
- **Premises that were wrong:**
  - The dispatch's "home-location-out shares no file with this branch" (Amendment 1, Rules) did not hold for F2:
    `TripNavigateTest.kt` was shared. It was stopped on and handed over (E8).
  - F2's "18 lines … 15 for waypoints" had already been corrected to 13 notes and 16 wordings; the code agrees [read].

## Not tested

- The S22 itself: real type, real insets. See the device check.
- TalkBack on a card with no status line. Nothing announces the status line [read], but this was not run.
- The dimmed (stale-fix) colour on a centred "Arrived" or dash.
- A word wider than the left part (no case reaches it).

## Clean-up

- **Throwaway refs:** each was deleted after its run. They were `nav-card-p3-red`, `-green`, `-b3`, `-b4`, `-f2` and
  `-measure` (the last ran twice: the first measurement run, 42213691257, failed in its own harness, which lacked the rig's
  host-activity rule; the second, 42216121500, passed).
  This report cites only CI runs, which outlive their refs.
- **Scratch:** `/mnt/work/scratch/nav-card-phase3/` is deleted at the end.
- **No index row** was added to `docs/audits/README.md`.
- **No coordinate** appears in anything pushed beyond the made-up point P, which the rig already used.

---

# Phase 3, G and H (G2, G3, H1 to H8): see the companion report

The second Phase 3 session built G2, G3 and H1 to H8 on this PR. Its report, with the four disclosure sections, the widths and heights
in each window and font, the room beside the needle, the revert table, the open items and the device check, is in
`docs/audits/2026-11-05-10-nav-card-kind-line-completion-report-gh.md`, a file of its own because this one had passed 700 lines.
