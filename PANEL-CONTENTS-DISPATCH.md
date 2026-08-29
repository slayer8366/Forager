# Dispatch C — panel contents

Fills the destinations Dispatch B creates. Send after B.

---

## Item 1 — Advanced search moves to the top

```
Advanced search moves to where regular search currently sits, at the
top of the app. It becomes a tappable selection that drops down the
advanced options, so search and its parameters are one place instead
of two.

1. THE DROPDOWN
   Tap the search field to expand the advanced options inline. Report
   what you chose for the collapsed state's affordance — it needs to
   read as expandable without a tap.

   This surface sits over the map when expanded. That makes it a
   floating container over the map, which means Understory's four
   rules apply as an entry condition, not a guideline: the long-press
   test, no Surface over the map, no scroll modifier at any range,
   width-bounded containers. This repo has shipped pointer
   interception four times. Extend Dispatch A's performTouchInput
   coverage to this surface.

2. LOCATION INPUT — replace manual coordinate entry
   Two controls, SIDE BY SIDE:
   - "Set on map" — uses the SAME location pin placement method
     already used elsewhere in the app (the pan-to-centre-pin plus
     confirm flow, CentrePinLocationPickerOverlay). Do not build a
     second picker.
   - "Use current location" — the existing behaviour.

3. MANUAL COORDINATES — kept, collapsed by default
   Manual entry is NOT removed. It moves into its own sub-section,
   collapsed by default so it does not clutter the panel.

   Foragers share coordinates with each other. Someone handed a
   location by another person needs to be able to type it in.

   Make it discoverable while collapsed: a LABELED row that expands,
   not a bare chevron. Someone in the field who has never seen this
   panel needs to find it first try. Report the label you chose.

4. REDUNDANT LIST
   Remove the list from the search panel. It duplicates information
   already available in the List destination.

   Before removing, confirm it is genuinely redundant rather than
   showing something the List tab does not — report what it displayed
   and what the List tab shows, then remove.
```

---

## Item 2 — Weather panel to Seasonal

**Investigate before moving.** I don't know what the Current Conditions card
pulls or from where, so this may be a move or a move plus a new data
requirement.

```
1. FIRST, REPORT WHAT EXISTS
   - What the Current Conditions card currently displays, and where
     it lives now.
   - Its data source. Which API or provider, what it returns, whether
     forecast data is already available from it or only current
     conditions.
   - Whether that source is already fetched for something else
     (fruiting-lag / rainfall history feeds the Seasonal tab — check
     whether it is the same provider).
   - Caching and offline behaviour. Weather is Pre-trip, so it is
     usually fetched with signal, but report what happens without.

   STOP AND REPORT if a day's forecast is not available from the
   existing source. That is a new data requirement and a scope
   change, not a move.

2. THEN MOVE IT
   Current conditions plus the day's forecast, in the Seasonal tab.

   Seasonal is Pre-trip and rare; weather is Pre-trip and per-trip.
   Consolidating them puts everything you check before leaving in one
   destination. Report where in the Seasonal tab it lands relative to
   the existing fruiting-lag content — weather is the more frequently
   consulted of the two.
```

---

## Standing constraints

- Re-derive every count and line reference against the tree. Multiple figures
  in this project's design documents describe superseded code.
- Anything asserted as visible gets `onNodeWithText` or a `testTag`, never
  `onNodeWithContentDescription` alone. That pattern already let an invisible
  control pass its tests for weeks.
- Any doc comment describing what you changed gets updated in the same commit.
  Three stale-comment sweeps have been needed already.
