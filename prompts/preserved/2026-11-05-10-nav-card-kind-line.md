# Dispatch 2026-11-05-10: the navigation card's distance and "by trail" on one line (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2181. Every fact below was read at pre-main 779599c3f while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim, with two screenshots of a navigation card (portrait, and landscape with the card on the bottom edge), each reading "2.2 mi", then "left 74° ▶ ◀ 206° SW" (landscape "38° ▶ ◀ 169° S"), then "by trail", beside "Dark since 5:23 PM" and the X:

**"For navigation cards have the 2.2 mi on the left half, and then move the "by trail" from the bottom to the top right half, on the same line as 2.2 mi"**

**"And do the same for landscape"**

The planner's reading, a premise for Phase 1 to check: within the card's left part (the readout, left of the divider), the distance goes on the left half of the top line and the distance kind ("by trail", or "straight", the same words at NavigationWords.kt:92-95) on the right half of that same line. The turn line follows below it. The kind's old line at the bottom goes. The right part ("Dark since …", the sun line, the X) is unchanged. The same applies to the landscape card on the bottom edge.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. No coordinates or place names in anything pushed.

## Phase 1: verify first, read-only, report to the planner by message
Build nothing. Answer each with file and line:
1. **Where the readout's lines are built:** NavigationInfo.kt (CardRouteAndStatusLines at :182 as read), NavigationReadout.kt, NavigationHudFit.kt (statusLineFit, its kindLead) and NavigationHudReadout.kt. Cover portrait and the landscape card, and the HUD strip if it is a different composable.
2. **Every state the readout's left part can show,** with its lines as they read today:
   - waypoint navigation and the way home, by trail and straight;
   - "Approaching" and "Arrived";
   - the off-trail words and "Back on trail";
   - a route message (for example "The trip is in another area · straight line");
   - "Approximate, finding GPS…";
   - Retry;
   - a stale or lost fix;
   - any other state.
   For each, say what the owner's layout would make it.
3. **The fit rules today:** when the kind is dropped or moved for room (statusLineFit's forms), at which widths and font scales. Then say what the top line's two halves need at the widest distance figure ("12.4 mi", "999 ft") and font scale 2.0.
4. **The cases the owner's words don't settle,** each in plain words with your recommendation, counting what each asks the user to notice. The owner's principle is "Don't make me think". For example:
   - the kind when the status line also carries words ("by trail · Approaching" or similar);
   - "Arrived";
   - a long route message;
   - what happens at a large font when the top line can't hold both.
5. **The tests that pin today's layout** (they will change with the owner's ruling), and which of them a Phase 2 must update rather than silence.
6. **Files and line counts** against the 400/800 rule. Any overlap with branches in flight: `edge-shade` (PR #495) and `trip-download-maps`.
7. Any premise above that is wrong.

## Phase 2: on the planner's go, after the owner rules on Phase 1's options
- A branch from pre-main as it is then.
- Tests first, through the real screen, in portrait and landscape and at font scales 1.0 and 2.0, reading the layout by bounds: the kind on the distance's line, right of it, and no third line.
- The revert check under CLAUDE.md "Testing" runner rules. Full CI green, counts from the JUnit XML. A PR into pre-main, ready, not merged.
- A short S22 check.

## Stops
Stop and report on:
- anything a user would see that the owner's words and rulings don't settle;
- a need to change words;
- a wrong premise;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, green, merged on the owner's word after an S22 check.
