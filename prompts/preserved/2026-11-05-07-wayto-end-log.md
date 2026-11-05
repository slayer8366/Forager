# Dispatch 2026-11-05-07: Navigate logs what the walker's end became, so the pick-up chooser can be judged (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2146. It follows the field-check write-up (prompts/preserved/2026-11-05-04-report.md, RECORD -2145), whose stop S1 this answers. Every fact below was read at pre-main ee0651fe0 while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim, to "A chosen pick-up point leaves no trace, so the field check can only confirm it at a spot prepared in advance. Should the app first record what it decided?": **"Add one log line first (Recommended)"**. The option read "A small change: each Navigate logs what your end of the route became (a pick-up point, the trail, or refused) and why. Nothing changes on screen. Then any Navigate, including the ones at home, shows whether the chooser worked, and the field check can be judged either way."

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change
- **A1.** Each way-to search logs, once, what the **walker's end** became, and why:
  - a pick-up chosen, with its rank (the private way's mouth, the addressed street, or the nearest public street) and the leg's length;
  - the engine's end kept, gated, because the walker isn't a street end, with the reason (no building within 15 m, not in built-up land);
  - by trail, with the existing reason;
  - refused, as today's line;
  - not judged, because the fix is worse than 15 m or has no accuracy, with the accuracy.
  - The waypoint's end gets the same treatment, so both ends of every search are on record.
- **A2.** Under the existing tag `FindWayToWaypoint`, at info level, in plain words. No positions, coordinates or street names: lengths, ranks and reasons only. The log is read on a phone and copied into session folders.
- **A3.** Nothing changes on screen, and no decision changes. The existing refusal and by-trail lines stay as they are, word for word.

## Scope boundary
- **May touch:** a new branch from pre-main; the way-to's ends step and use case (`domain/wayto/`) and its log sink; tests; a short completion report.
- **Must not touch:** what any end decides; the screen; pre-main; main; RECORD.md; the audit index; the phone; the other Hazels' branches (bug-14-picker-warning, crowded-icons-test-fix, the trip bubble, the edge shade).

## Verify first, briefly, then build
1. Re-check, with file and line:
   - where the walker's end is decided (WayToEnds.kt:192-207 as read);
   - the existing log lines (FindWayToWaypointUseCase.kt:92-95, :155, :164-166);
   - how a search's result reaches the use case.
2. Report the exact line formats by message, then build without waiting unless something is a stop. The words are log words, not user words, so they don't go to the owner.

## Evidence required
- Tests first, through `FindWayToWaypointUseCase` with real ends (the pattern of FindWayToWaypointDropOffTest), each case's line asserted exactly: pick-up, gated engine end, by trail, refused, not judged.
- A revert check: the new line removed, and only the new cases fail.
- Full CI green, with counts from the JUnit XML.

## Stops
Stop and report on:
- any change to a decision or the screen;
- positions in the line;
- a wrong premise;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, green, merged on the owner's word. It goes into the S22's outing build.
