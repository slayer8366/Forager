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
| f46baf51d | **The change.** The rulings replace the strict reading in the new files. The pure fit test is updated. |
| (this report) | This file only. |

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
- **The rig, `NavigationCardKindLineScreen`** (331 lines). It gains:
  - the evening phases: none, "Dark since 5:23 PM", "Dark since 10:23 PM", before sunset, after sunset;
  - three facings: 169° off, 1° off, no compass;
  - the top line's, the X's and the stacked words' bounds;
  - text measured in each node's own type.
- **`NavigationCardTopLineRules`** (new, 194 lines): works the owner's rulings out from the drawn card's own widths, in whole pixels,
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
  - the 7 new or extended top-line classes;
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
  - it ran the 7 top-line classes and the pure fit test, 54 tests;
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


**Full CI:** FULL-CI-PLACEHOLDER

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

CLEANUP-PLACEHOLDER
