# Dispatch 2026-11-02-02: known bug 14, a warning in the Offline Maps area picker (Hazel, Opus)

Written by Rowan Waymark, 2026-11-02. RECORD -1943, -1950, -1951. Starts after dispatch 2026-10-30-08's C15 to C18 are reported, not alongside them (one Gradle at a time).

**The owner:** "Warn in the area picker (Recommended)", whose option read "Users choosing an area there are told before they download. The release isn't held."; then the wording, "Detailed maps not here yet (Recommended)", whose option read: "Detailed offline maps aren't available here yet. A download here will be blank when you're offline."

**Base:** origin/pre-main 13c872d79 as written; verify. New branch `bug-14-picker-warning`, a PR into pre-main, no merge.

**Premises to re-check, with file and line:** known bug 14 (docs/process/known-bugs.md on pre-main): downloads store zoom 10 to 15 and the map server has nothing south of its archive above zoom 8; the line is 15.92°N, from the Worker (`server/pmtiles-worker/src/index.ts`) and `OfflineMapRepository.kt`. Seen on the S22 (-1950). Find where 15.92 (or the archive's bounds) is defined and whether the app can read it, rather than copying the number.

**Verify first, then report by message before building:** where the picker draws its lines under the area; where the 15.92°N bound comes from and whether one source can feed both the Worker and the app; what "the area is in that region" should mean when an area straddles the line (stop and ask: that is the owner's).

## Amendments, by category
| | Category |
|---|---|
| **A** | The warning |
| **B** | Tests |

**For every item:** fail first, then a revert check (build log checked, restore from a saved copy, forward change confirmed after). Full CI green on the last real commit, no empty commits. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. Never read, name or quote the Restricted Object.

## A. The warning
- **A1** (2026-11-02, -1951). The step path: Tools > Offline Maps > choose an area > the pin is south of 15.92°N > under the area, the line "Detailed offline maps aren't available here yet. A download here will be blank when you're offline." > Download still works. Pin moved north of the line > the line goes. The words are the owner's, exactly. Style it as the picker's other notes; propose nothing new visually; if there is no matching style, stop and ask.

## B. Tests
- **B1** (2026-11-02, -1951). Through the real picker: the line shows, as literal text, with the pin at 14.7°N 91.5°W (the S22 case, -1950); it doesn't at [a point near the owner's home; removed 2026-11-06]; Download stays enabled in both. Fail first, then a revert check.

Known bugs row 14 is updated by the planner, not the coder.
