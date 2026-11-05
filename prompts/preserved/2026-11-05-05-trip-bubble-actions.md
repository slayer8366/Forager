# Dispatch 2026-11-05-05: The trip pin's bubble gets Navigate, Plot route and Details; trips get a Details card; Details cards get Directions beside Navigate (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2140 (decision) and -2141 (intent). Every fact below was read at pre-main ee0651fe0 while this was written, and is a premise to re-check, with file and line.

## Authorisation
- **The owner, verbatim,** with three screenshots (filed at `docs/audits/assets/2026-11-05-05-trip-bubble/` on records-after-173: trip-bubble.png, waypoint-bubble.png, waypoint-details.png): "The trip icon should show a navigate option, along with elevation data. Remove the directions from the bubble add Navigate, Plot Route Details. Remove the long/lat coordinates from the Trip icon, and keep the military style. In the Details card add the Directions button for external maps to the right of the in-app Navigate button and center the two buttons".
- **Then, to two questions:**
  - "What should the new trip Details card show? (Trips have no card today.)": **"Like the waypoint's (Recommended)"**. The option read "The trip's name, its date, MGRS, coordinates, when it was created, and which journal entries use it, the same layout as a waypoint's card. Then Navigate and Directions, centred."
  - "Does this go in this release, or the next update?": **"This release (Recommended)"**. The option read "Built now, beside the bug 14 warning, while the field check and the walk are still to do, so it shouldn't hold the release longer. Elder re-checks the added work, and it gets a phone check."

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules
- Verify first: Phase 1 reads and reports by message, then stops. Phase 2 builds, on the planner's go.
- Tests first, through real entry points and real touches where a touch is the claim, shown failing. Revert checks under CLAUDE.md's runner rules. Full CI green. CI only: no Gradle on the laptop.
- Don't touch the phone. Never read, name or quote the Restricted Object. Our own map is the "Trail Map".
- **New words a person would see go to the planner before they're built:** for example the trip card's label for the trip's date.
- Small, decoupled files (the modular-code skill). Another Hazel is on `bug-14-picker-warning`, and another on `crowded-icons-test-fix`: don't touch their branches or files.

## Change: the step path
- **A1. The trip pin's bubble.** Maps tab, tap a trip pin. The bubble shows, in order:
  - the trip's name;
  - its date;
  - the MGRS line (the "military style"), with **no latitude/longitude line**;
  - **"Elevation N ft"** where the ground height is known, as a waypoint's bubble shows it, and no line otherwise;
  - then **Navigate · Plot route · Details**, with **no Directions**.

  Today it is `MapFeatureBubble.kt:117-123`: name, date, MGRS, `content.coordinates`, then Directions only. The waypoint bubble's lines and actions are at `:87-105`, its elevation line at `:91`.
- **A2. Navigate on a trip:** in-app navigation to the trip's location, the same experience as a waypoint's Navigate, with the way-to and its drop-off series. Report how a trip's location reaches the way-to: today it is keyed on a waypoint id (`navigate(content.waypoint.id)`).
- **A3. Plot route on a trip:** route plotting starts from the trip's location, as a waypoint's Plot route does. Report how plotting takes its first stop (today a waypoint id), and what a trip as a stop means for a saved route.
- **A4. Details on a trip:** opens a new trip Details card, laid out like the waypoint's: the trip's name, its date, MGRS, coordinates, created, and used in. There is no trip card today (`MapFeatureBubble.kt:122`, "a planned trip has no sheet"; `RecordDetailsTarget.kt:21-29` has no trip target).
- **A5. Every Details card with Navigate** (the waypoint's, and the new trip's): **Navigate and Directions side by side, centred**, with Directions to the right. Directions hands the location to an outside maps app, as it does today for trips (AndroidManifest.xml:52's note; `AvailabilityTripsWaypointsUi.kt:135`). Today the waypoint's card has Navigate alone, at the left (waypoint-details.png).
- **A6. Unchanged:** the waypoint's bubble (Navigate · Plot route · Details; Directions only for a snapshot-only waypoint, RECORD -1819), and the latitude/longitude line on the Details cards.
- **A7. Map chrome:** the bubbles and cards stay at 80% over the map (CLAUDE.md, "Nothing fully obstructs the map view"), as the existing ones are.

## Scope boundary
- **May touch:** a new branch from pre-main; the bubble and its content for trips; the way-to's and route plotting's entry for a trip's location; a new trip Details target and card; the Details cards' action row; tests; your completion report.
- **Must not touch:** pre-main, main, RECORD.md, the audit index, the phone, the other two Hazels' branches, Theme.kt and Color.kt.
- A database change is a stop: a new Room version is globally unique.

## Baseline
pre-main ee0651fe0. If it has moved, say so.

## Verify first (Phase 1: report, then stop)
1. Every premise above, with file and line.
2. How a trip's location reaches Navigate (A2), Plot route (A3) and the elevation line (A1), and whether any of them needs a trip to become a waypoint or a stored stop. Don't choose that: say what each way means for the user and the data.
3. The trip card (A4): where Details cards are built, and what "used in" means for a trip.
4. The action row (A5): where the waypoint card's Navigate is, and how both cards share one row.
5. The new words needed, if any, listed for the owner.
6. Your mechanism prediction, test plan (real touches on the bubble's and card's buttons, at several points across each) and file list, with sizes.
7. Anything the owner would see that this doesn't rule.

## Predictions
- **Outcome (planner):** the trip bubble matches the waypoint bubble's lines and actions, minus Directions and latitude/longitude. Both cards show Navigate and Directions centred. No database change is needed.
- **Mechanism (coder):** in your Phase 1 report.

## Stops
Stop and report on:
- a new Room version;
- new words;
- an owner-level choice (A2, A3);
- a wrong premise;
- a weakened test;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, green, merged on the owner's word. Then the planner's S22 check, and Elder's re-check of the added work before the promotion.
