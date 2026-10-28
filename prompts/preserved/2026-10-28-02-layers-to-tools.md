# Dispatch 2026-10-28-02: Map Layers into Tools, the map type panel back on the Layers button, no chevrons (a coder, Opus)

Written by Rowan Waymark, 2026-10-28. RECORD -1764. Cut from **pre-main `e2969abe4`** (PR #429's merge, 2026-10-27 20:20 PDT). Verify that is still the remote head before you start, and say so if it is not.

**Authorisation.** The owner, verbatim, with a screenshot of the Layers sheet filling the screen:
"This pull up tab is getting too big. So let's move the map layers into the Tools drawer beneath Trip Planner as Map Layers.
Keep the Trail, Topo, OSM options in the map icon bar for ease of access. The map layers themselves will be less useful but still easy to access in the Tools section.
Have the mapiconbar layers tab return to the original animated state it was prior to the map layers addition.
Remive the i from the Trails selector as it forces the switch to sit awkwardly.
Remove all Chevrons from the drop down lists, those are banned from the app."
Then: steps 1-4 "Yes"; steps 5-9 "Yes, but 6) is called Trails not Official trails."; the (i) card "Drop the card"; the entry map "Same as Maps tab (Recommended)"; the fold "Folded, remembers (Recommended)"; the Trails row "Just 'Trails', and rename to 'Recording Tracks'"; "Start a coder now".

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change (the owner's confirmed step paths)

**The Layers button**, on the Maps tab's icon bar and on a journal entry's map:
1. Tap Layers > the map dims and a small panel grows out of the button with three chips, Trail · Topo · OSM.
2. Tap a chip > the map changes and the panel shrinks back into the button. One tap.
3. Tap the dimmed map, or Back > it closes, nothing changed.
4. The grey "chosen, but Trail is showing" chip keeps its meaning (today's `MapModeChipRow`, `offlineTiles`, `OWN_STREET_FALLBACK`).

"The original animated state" is the popover the button opened before the Layers sheet: `MapModePicker`, at `347859d50^:app/src/main/java/com/zynergylabs/forager/app/ui/map/MapChrome.kt:127` (the planner's reading; L0b `347859d50` replaced it, and `78c90a405` deleted it as dead code). It had a scrim fading in, then the chip panel fading and `expandIn` from the button's anchor, shrinking back the same way, on `MotionTokens.panelMotionSpec()`, anchored with `mapIconBarRowAnchorOffset`. Rebuild that shape with today's chips, fills, motion tokens, reduced-motion handling and anchoring. Do not restore old code blindly: the bar's rows, sides and landscape L have changed since. Read them and say how the anchor follows the bar.

**The Tools drawer:**
5. Open Tools > Trip Planner, then **Map Layers** under it, folded the first time.
6. Tap Map Layers > the switches show: Finds, Photos, Waypoints, Planned trips, **Recording Tracks** (was "Recording trail"), Tracks, Offline maps, Journal entries, then **Trails** (was "Official trails"; one line, "Forest Service and BLM" goes), then any forecast colour fields with their opacity and reorder controls. There are no map type chips here. Once opened, it stays open while the app runs, across drawer closes and tab changes (CLAUDE.md, UX defaults). It is not persisted across restarts.
7. Flip a switch > the map changes behind the drawer. Close the drawer > the map is as set. The switches are the same stored state the sheet wrote, shared with the entry map, as today.
8. Trails has no (i), and the card it opened is gone (`ReferenceLayerCard`, the `REFERENCE_CARD_*` text, its sources fetch, if nothing else uses them).
9. No up or down arrow on any fold-open list: Trip Planner and Map Layers (`SearchControls.kt:92`, `CollapsibleSection`), a walk's Details (`RecordDetailsParts.kt:119`), a journal entry's contents groups (`EntryContentsPanel.kt:154`), and the Month picker (`SearchDropdownSections.kt:143`, the dropdown's trailing icon). A tap on the header still opens and closes each one. Keep their spoken labels: the Trip Planner arrow carries "Expand/Collapse …" as its description today, so move that to the row's click label. The photo viewer's left and right arrows (`PhotoViewerDialog.kt:184`, `:194`) stay. They are not drop-downs.

The Layers sheet (`MapLayersSheet`) goes once nothing calls it. The Layers button's description, "Choose the map type and overlays" (`layersButtonDescription`), changes to say map type only.

## Verify first (report before building)
- Who calls `MapLayersSheet` (`CompactMapPickers.kt:99`, `CartographyEntryReportScreen.kt:651` at the base), and everything only it uses.
- **Trails' visibility rule.** Today the row shows only while the map reports that its style draws the band (`referenceTrails`, `MapRenderMode.onReferenceTrailsShown`). Find how the Tools drawer can learn that, over the Maps tab and over the Journal. If it can't without a conditional threaded through working code, stop and propose.
- **Credits.** Confirm that the map's own attribution ("Trails: USDA Forest Service, BLM") does not depend on the card or its sources fetch. If any licence credit is carried only by the card, stop and ask. Licence credits stay as the licences require.
- **The fold's holder.** Say where Map Layers' open state is held so it survives closing the drawer and changing tabs. Say whether Trip Planner's fold, which resets today, should be left as it is: it is out of scope, so report it, and do not change it.
- Every test that asserts on the removed icons, the sheet, the (i), the card or the old labels. Name each one, and say whether it is rewritten for the new behaviour or removed with what it tested.

## Tests
- Through the real entry points, the icon bar's Layers row and the entry map's: the panel opens; a **coordinate touch** on each chip, sampled at several points across its bounds, applies the mode and closes the panel; a tap on the scrim closes it unchanged; Back closes it unchanged. With the panel closed, a long-press on the map still reaches the map (CLAUDE.md, the `Surface` pitfall).
- The grey offline-tiles chip on the entry map, as today.
- Tools: Map Layers sits under Trip Planner, folded the first time. Open it, close the drawer, change tab, come back: still open. Each switch, by a real touch on its row, changes the stored state and the map's render state. "Recording Tracks" and "Trails" read as written, and Trails is a single line with no (i).
- No `ExpandMore`, `ExpandLess` or dropdown trailing icon anywhere in `app/src/main`. A grep check in the report is fine, but each list still opens and closes on its header tap (a test per list).
- **Fail first, then revert checks:** one per rule, restored from a saved copy, with the build log checked for compile errors (CLAUDE.md).
- **Modular rule:** `MapLayersSheet.kt` is 593 lines and `CartographyEntryReportScreen.kt` 787. Put the panel and the Tools section in new files under 400 lines, and split before adding to anything that would pass 800.
- CI only. Open a PR into pre-main and do not merge it. Put the S22 steps in the PR body, the panel's animation and anchor on both sides of the bar and in the landscape L among them.

## Rules
- **Network:** none beyond GitHub.
- **The Restricted Object:** never read, name or quote it.
- **Long conversation:** past about 200k tokens, push and hand off.
- **Ambiguity:** stop and report.
- **Words on screen** are exactly the owner's: "Map Layers", "Trails", "Recording Tracks", "Trail", "Topo", "OSM". Any other new words are a stop.

## Amendment 1 (2026-10-28): the switch labels in Title Case (RECORD -1765)
The owner, verbatim: "And just have it labeled Trails and not Official trails. Capitalize the titles also. Planned trips should be Planned Trips, Offline maps should be Offline Maps, Journal entries should be Journal Entries."
- Trails: as already dispatched, "Trails", one line.
- The Map Layers switch labels read, in order: Finds, Photos, Waypoints, Planned Trips, Recording Tracks, Tracks, Offline Maps, Journal Entries, Trails. Forecast colour-field names follow the same Title Case. List each one in the report.
- Verify first: where else each label constant is shown (for example `JOURNAL_ENTRIES_OVERLAY_LABEL` outside the switch list). Change only the switch labels. If a constant is shared with another screen, split it rather than changing that screen, and name it in the report.
- Tests assert the exact new strings.

## Amendment 2 (2026-10-28): answers to the verify-first, and the go to build (RECORD -1766)
Hazel's verify-first is on branch layers-to-tools, cdf9bfe7f (`docs/audits/2026-10-28-layers-to-tools-verify.md`). Its proposals for the fold holder, the anchor, the file plan, the removals (the card, its sources fetch and its stored dates, with no cleanup of the orphaned file), and "Recent searches" losing its arrow too are accepted. The owner's answers, verbatim:
- **Q1, Trails:** "Option 1. But when selecting a map type that doesn't use Trails, just have it greyed out instead of removing it from the list". Trails follows the Maps tab's map wherever Tools opens. The owner confirmed this step path: map type Topo or OSM > Tools > Map Layers > Trails is listed but grey, showing its stored on/off, and a tap does nothing > switch back to Trail > Trails is normal and works. The row is always listed. Say how it reads to TalkBack (disabled), and test it with a real touch in both states.
- **Q2, the test layers:** "Option 2. But keep taxonomy proper (e.g. Cordyceps militaris not Cordyceps Militaris, but Lion's Mane and not Lion's mane))". Title Case everywhere the names show: switch, legend, bubble and spoken labels. Common names take Title Case ("Chicken of the Woods", "Chanterelles"). A scientific binomial keeps its genus capitalised and its species lower case. So: "Test Forecast: Chicken of the Woods (Synthetic Data)", "Test Forecast: Chanterelles (Synthetic Data)".
- **Q3:** "Match: Offline Maps (Recommended)". The Tools row that opens downloaded areas reads "Offline Maps".
- **Q4:** "…Choose the map type. (Recommended)". It reads "Layers: Trail map. Choose the map type."
- **The go:** build. CI only, then a PR into pre-main, no merge, with the S22 steps in its body. Add the audit index row with the completion report.

## Amendment 3 (2026-10-28): Sorrel's review of #432 (RECORD -1770)
Sorrel's review (Fable) is `docs/reviews/2026-10-28-sorrel-pr432.md` on branch sorrel-pr432, ddd0b1c4a, at e3221a9db. It has no S findings and nine N. The owner's answers:
- **N1, "Opening Tools closes panel (Recommended)":** landscape, panel open > tap Tools on the rail > the panel closes and the drawer opens > Back > the drawer closes > the map. Write a test that fails first, then a revert check.
- **N3, "Yes, same as Map Layers (Recommended)":** Trip Planner's fold is held the same way as Map Layers'. It survives a drawer close, a tab change, a trip into Settings and a rotation, but not a restart. Correct its doc comment at SearchControls.kt:63-65. Write a test that fails first, then a revert check.
- **N4** is settled: the owner's "Option 2" was "Title Case everywhere" in the question as asked. No change.
- **Also in this push:** N5 (a test for Back on the Maps tab with fullscreen on and the panel open) and N7 (point the ten comments at MapChrome.kt:103-109, not the deleted sheet).
- **Not in this PR:** N2 (the Worker's unused /reference-trails-manifest.json route) and N6 (the fold headers' height).
CI only. Push to #432 and report.

## Amendment 4 (2026-10-28): the selected map type chip in the light theme (RECORD -1771)
The S22 session (docs/audits/2026-10-28-s22-pr432.md on branch s22-pr432, 8e8165e11) described it this way: in the light theme the selected chip is a pale peach fill with no outline, about 1.04:1 against the panel, so it reads as "slightly warmer, not clearly on". In the dark theme it stands out clearly. The owner, to "Make it stand out (Recommended)": "Option 1, but Give it an outline."
- **Light theme, selected chip:** a clearly stronger fill than the panel, plus an outline, so it reads as "on" at a glance. Report the contrast you chose, against the panel and against the unselected chips, computed from the theme colours. The chips are shared with any other screen that uses them (MapModeChips.kt), so name every place the change shows.
- **Dark theme:** unchanged, unless the shared chip makes that impossible. If so, stop and report.
- **Tests:** assert the new fill and outline through the panel's real entry point. Write the test so it fails first, then a revert check. CI only; push to #432.

## Amendment 5 (2026-10-28): the grey chip gets the outline too (RECORD -1773)
Amendment 4 is built: 5bd69cd4c, code 66c0457c0, run 40772168805 green, 5,889 tests. The owner: "Give the grey chip an outline too", then "Light theme only (Recommended)".
- **Light theme:** the grey "chosen, but Trail is showing" chip gets the same 1 dp onSurface outline as the selected chip. Its fill is unchanged.
- **Dark theme:** unchanged for both chips.
- Test it through the real entry points (the no-signal case and the style fallback, as MapTypePanelOfflineChipTest does). Write it so it fails first, then a revert check. Report the outline's contrast against the grey fill and against the panel.
- Add an S22 step to the PR body. CI only; push to #432.

## Amendment 6 (2026-10-28): the outline in both themes, uniform (RECORD -1774)
The owner, after Amendment 5: "Both themes. Make it uniform". This replaces Amendment 5's "Light theme only".
- **Both themes:** the selected chip and the grey "chosen, but Trail is showing" chip each carry the same 1 dp onSurface outline, using that theme's onSurface. Their fills are unchanged (Amendment 4's light fill stays; dark fills as today).
- The unselected chips keep their own outline as today.
- Tests: through the real entry points, in both themes and for both chip states. Write them so they fail first, then revert checks. Amendment 4's "dark unchanged" assertions change to the new outline. Report the contrast of each outline against its fill and against the panel, in each theme.
- Update the PR body's S22 steps 19 and 20 and add the grey chip's step. CI only; push to #432.
