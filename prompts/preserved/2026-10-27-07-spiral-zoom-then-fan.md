# Dispatch 2026-10-27-07: a crowded group zooms in, then fans (a coder, Opus)

Written by Rowan Waymark, 2026-10-27. RECORD -1736. **Start only after the find-photos PR (dispatch 2026-10-27-05) has merged.** Cut from the pre-main head at that point.

**Authorisation.** The owner: "Zoom in, then fan (Recommended)"; "Open by itself (Recommended)"; "Back folds, then zooms out (Recommended)"; "Icon, name, date (Recommended)"; "Zoom 17, like the entry map (Recommended)"; "Start after find photos (Recommended)". The plan is the spiral report, docs/audits/2026-10-27-spiral-overflow-report.md on branch spiral-overflow-report (6226d7deb), option A. Its premises are to be re-checked at the base.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change (the owner's step paths)
1. Tap a group whose fan fits on screen > the fan opens as today.
2. Tap a group whose fan would not fit > the map zooms to that group's area (reuse applyCameraFrame / EntryMapFrame.Fit or the code's equivalent) until the icons split or their fan fits. When it fits, the fan opens by itself. No second tap.
3. Back > the fan folds. Back again > the map returns to where it was before the tap (camera position and zoom). Back again > the usual Back for the Maps tab.
4. Still all on one spot at zoom 17 (the closest zoom, as the entry map uses) > a list opens over the map:
   - each row shows the item's map icon, its name (species, waypoint name, or photo) and its date;
   - tap a row > it opens as from the map;
   - Back > the list;
   - Back > the list closes (then as step 3).
   - The list is map chrome at MAP_CHROME_OVER_MAP_ALPHA (0.8), with opaque content.
5. The app's own zoom must not fold the fan it is about to open.
6. **Log the overflow fallback:** FanPlacement's allOnScreen=false is read by nothing today. Whatever path still reaches it is logged.

## Verify first
Report before building if any of these is wrong:
- the report's file:line;
- "Back retraces the way in" fitting the existing Back chain (the report lists it);
- the map's zoom limits;
- the row data being reachable (the fan knows only layer, id and position).

## Tests
- **Real entry points:** through MapTapHandler with a fake map, as the report says, plus Back on the real screen.
- **Real touch on the list:** `performTouchInput` at several points across each row, and a long-press on the map outside the list.
- **The app's own zoom:** a test that the zoom does not fold the fan.
- **Fail first, then revert checks:** one revert check per rule, restored from a saved copy, with the build log checked.
- CI only. A PR into pre-main, no merge.

## Rules
- **Network:** none beyond GitHub.
- **The Restricted Object:** never read, name or quote it.
- **Size:** the modular-code rule (new files under 400 lines; over 800, split first). Watch AvailabilityViewModel.kt (793 lines).
- **Long conversation:** past about 200k tokens, push and write a handoff.
- **Ambiguity:** stop and report.

## Report
- the four disclosure sections;
- the PR number and CI runs;
- S22 steps as tap > shows > Back, including a crowded country-zoom group like the owner's screenshot.
