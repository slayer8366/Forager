# Dispatch 2026-10-27-14: a "Crowded icons" setting, zoom-then-fan or a list (a coder, Opus)

Written by Rowan Waymark, 2026-10-27. RECORD -1750. **Start only after PR #430 (dispatch 2026-10-27-07, the zoom-then-fan) has merged,** cut from the pre-main head at that point.

**Authorisation.** The owner, verbatim: "Have an option to show the new set up or just a list in the settings menu for simplicity". Then: "Every group"; "Zoom in, then fan (Recommended)" as the default; "Next PR after #430 (Recommended)".

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change (the owner's step paths)
- **The setting:** Tools > Settings > the map settings section > "Crowded icons", with two choices: "Zoom in, then fan" (the default on a new install, and for existing users) and "List".
  - The setting persists across restarts in DataStore, following the DataStoreMapPreferencesRepository pattern in CLAUDE.md.
  - Find where the map settings live in Settings (Night Maps is under "Night Mode"). If no map section exists, say where it fits best, and stop and ask before creating a new section.
- **Zoom in, then fan:** exactly as #430 builds it.
- **List:** a tap on any stack of two or more icons, whether or not its fan would fit:
  - the list from #430 opens over the map at once, with no zoom and no fan, and each row shows icon, name and date;
  - tap a row > that record's bubble, as from the map;
  - Back > the list;
  - Back > the list closes, and the map is where it was (no camera step, because nothing moved);
  - a tap on the map closes the list.
  - A single icon still opens its bubble directly.
- **Where it applies:** on the Maps tab only, as #430 (RECORD -1749). Elsewhere the fan stays as it is today.

## Verify first
Before building, report:
- where the Settings rows for the map live (file:line);
- how #430's list host can be opened for any stack, and its row source;
- whether the MapTapHandler path can branch on the setting without a conditional threaded through working code. CLAUDE.md: new capability is a new path. Propose the seam.

## Tests
- **The setting:** the row persists, through the real Settings screen.
- **List mode:** a stack that would fit opens the list, not a fan. A real touch at several points across each row opens its bubble. The Back chain as above. A map tap closes the list.
- **Fan mode:** unchanged; #430's tests pass as they are.
- **Fail first, then revert checks:** one per rule, restored from a saved copy, with the build log checked.
- **Modular rule:** watch SightingsMap.kt (779 lines) and AvailabilityViewModel.kt (793); split before adding if either would pass 800.
- CI only. A PR into pre-main, no merge. Put the S22 steps in the PR body.

## Rules
- **Network:** none beyond GitHub.
- **The Restricted Object:** never read, name or quote it.
- **Long conversation:** past about 200k tokens, push and hand off.
- **Ambiguity:** stop and report.
