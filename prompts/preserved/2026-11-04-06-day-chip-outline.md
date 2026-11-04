# Dispatch 2026-11-04-06: The selected chip's outline in day mode, the code Amendment 4 should have added (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2083. Every fact below is a premise to re-check, with file and line.

**Written against:**
- pre-main 3ffced9c0. #487 is undone there (RECORD -2081), so every map type chip is Material's default in both themes, with no outline.
- Branch delete-amendment-4 is at d82cf520e, an ancestor of pre-main. Its PR, #486, shows as merged and can't be reopened (GitHub answered HTTP 422, -2080).
- #485 (palette-rule) and #488 (revert-chip-colours) are set aside.
- The last full CI on this chip code was run 41863141684: 6,536 tests, 0 failures, 24 skipped.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees |
| **B** | Tests and evidence |
| **C** | Branch, build and report |

**For every item:**
- Verify first: read the code and report to the planner by message before writing anything.
- Then fail first, then a revert check on a throwaway CI branch: check the build log for compile errors, and restore from a saved copy.
- Full CI must be green on the last real commit. CI only: no Gradle on the laptop (coder-profile.md:30).
- Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object.
- Our own map is the "Trail Map".
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
- New user-facing words go back to the planner before they're built.
- Stop and report, don't choose, on anything the owner would see that this doesn't rule.

## A. What the user sees
- **A1** (2026-11-04, -2083). What the owner said, in order:
  - "Use 486 to the chip outline to day mode".
  - The planner asked: "In day mode, which map type chips get the 1 dp Bark (#3B2E24) outline? Night stays with no outline. Fills stay Material's default (day #F6DFC8, grey #D5CEBF when the chosen map is standing aside)." The owner chose "Selected chip only".
  - Then: "Nope. New code. It was the code that was meant to be added to begin with."

  This is the outline the owner asked for on 2026-10-28 ("Option 1, but Give it an outline.", -1771), on the pale default fill. Amendment 4 of dispatch 2026-10-28-02 should have built it then, not the red, which was a planner error (-2073). What to build:
  - **Day mode** (the app's Night Mode off): the selected map type chip, when drawn normally, gets a 1 dp outline in the theme's `onSurface`. That is Bark #3B2E24 (Theme.kt:44, Color.kt:9). Its fill stays `secondaryContainer`, #F6DFC8 (Theme.kt:28, Color.kt:87). Its label stays as it is.
  - **No outline on:** the unselected chips, which keep Material's own border; the grey "chosen, but offline" chip (its `outlineVariant` fill, MapModeChips.kt:99-103); and every chip at night.
  - **Premises to check and report before writing code:**
    - MapModeChipRow (MapModeChips.kt:82) draws the chips. Its FilterChip (:107-113) passes `colors` but no `border` today. Its only caller is MapTypePanel (MapTypePanel.kt:117). That panel is shown from the Maps tab (CompactMapPickers.kt:96) and from a journal entry's map (CartographyEntryReportScreen.kt:648). Name every place the change shows.
    - What FilterChip draws by default, selected and unselected, in material3 1.5.0-alpha26 (gradle/libs.versions.toml:19). Read it from the library, not from memory.
    - "Day mode" here means `LocalForagerDarkTheme` is false (Theme.kt:177). It is provided as `shownDark` inside NightModeBlend (:152). Confirm it follows the app's Night Mode setting on both entry points, and say what the chip shows during the night-mode fade. Don't use `isSystemInDarkTheme`; that is the bug this local fixes (Theme.kt:165-176).
  - **Reference only:** the 10-28 outline at 64aacd969 (MapModeChips.kt:147-152 there, `lightSelectedChipBorder`). That commit also carried the red fill, so don't revive it or revert it back in.
  - **Build it as a new function,** not a condition threaded into the row (CLAUDE.md, Building). MapModeChips.kt is 116 lines, so no split is needed (the modular-code skill).

## B. Tests and evidence
- **B1. Tests through the panel's real entry points:** the Maps tab and a journal entry's map. Open each by a real coordinate touch on its Layers row, inside the app's ForagerTheme. Read the outline and the fill from the drawn pixels. The removed MapTypePanelSelectedChipTest at b25806b4b has a harness and an outline check (onSurface pixels left of the fill) that passed CI on 10-28. Reuse them, or say why not. Cover:
  - day: the selected chip's fill is unchanged and an onSurface outline is drawn; the unselected chips have none;
  - night: the selected chip has no outline;
  - day, the grey chip, both with no signal and fallen back (as MapTypePanelOfflineChipTest drives them): no outline, and the fill is unchanged.
- **B2. Fail first:** put the tests in their own commit and run them on CI.
  - The day-outline cases must fail for the predicted reason: no outline is drawn.
  - The absence cases (night, grey, unselected) pass before the change by construction. Say so, and prove they bite with B3.
- **B3. Revert checks:** each is a one-line edit on a throwaway CI branch that is never merged.
  - r1, no border: the day cases fail.
  - r2, the outline at night too: the night cases fail.
  - r3, the outline on the grey chip too: the grey case fails.
  - For each check, confirm four things: no compile errors in the build log; only failures this edit could cause; the file restored from a saved copy, not from git; and the forward change still present afterwards (CLAUDE.md, Testing).
- **B4. Full CI green** on the last real commit, with suite counts. The last full run on this code was 41863141684: 6,536 tests, 0 failures, 24 skipped.

## C. Branch, build and report
- **C1. Branch:** delete-amendment-4, which is #486's branch, fast-forwarded to origin/pre-main (3ffced9c0 or later).
  - Never force push.
  - **No PR.** #486 can't be reopened, and the owner said "No mew PR".
  - Run CI with `gh workflow run ci.yml --ref delete-amendment-4` (ci.yml's workflow_dispatch trigger, RECORD -865). A `[skip ci]` in the head commit skips the push and pull_request checks, so leave it out of any head you want checked.
  - Merging waits for the owner.
- **C2. Don't touch:**
  - any chip's fill or label, in either theme (the owner: "Before any chip colour", -2078);
  - `chosenMapModeStandsAside`, and the grey chip's colours;
  - Theme.kt and Color.kt (no new colour or token);
  - StyleLoadLog, the style-load log line that came in with #483;
  - pre-main, main, palette-rule (#485), revert-chip-colours (#488), and #486's PR page;
  - RECORD.md;
  - the phone.
- **C3. Already rejected, so don't build these:**
  - the outline in both themes, as in Amendment 6: the owner said "day mode";
  - the outline on the grey chip, as in Amendment 5: the owner said "Selected chip only";
  - bringing back 64aacd969, or reverting its reverts: it carries the red;
  - any change to a fill, such as a stronger fill or Cream.

  If you think any of this is wrong, say why instead of building it.
- **C4. Report:**
  - First, the verify-first message.
  - Then a completion report at docs/audits/2026-11-04-day-chip-outline-completion-report.md on the branch, with a row in docs/audits/README.md. It gives:
    - commit hashes, CI run IDs and suite counts;
    - each revert check and the failure it named;
    - the outline's contrast against the fill and against the panel, computed from the theme colours.
  - **Conventions:** which nearby files or apps were checked for a selected-chip outline, what convention was found, and whether it was followed. "None checked" is allowed; a blank is not.
  - **Not verified:** stated plainly.

## Device only (the owner's to look at, not the coder's)
Don't touch the phone. The planner doesn't change the phone's theme for a check (-2071, -2072), so the night check is the owner's to choose.
1. Day (Night Mode off), on the Maps tab, open Layers. The selected chip is pale with a thin dark outline; the other two chips have none. If the outline is missing, A1 didn't reach that entry point.
2. The same on a journal entry's map, opened from its Layers row.
3. Night, only if the owner chooses to look: no chip has an outline. An outline here means the day check leaked into Night Mode.
