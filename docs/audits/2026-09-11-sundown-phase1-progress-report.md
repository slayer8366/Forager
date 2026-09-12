# Sundown countdown, Phase 1: what is built, what is not

**Date:** 2026-09-11
**Branch:** `claude/beta-signup-website-g3t91u`
**Status:** four slices built and verified. The screen row is specified and not started.

---

## Built and verified

| Slice | What | Evidence |
|---|---|---|
| 1 | `SunCrossing`: sunset and civil dusk by bisecting the existing `CivilTwilight.sunAltitudeDegrees` | 7 tests. 16 Open-Meteo sunsets, 8 places, Ushuaia to Reykjavík, worst delta 13 s |
| 2 | `SundownCountdown` + `ComputeSundownCountdownUseCase` | 10 tests |
| 3 | `SundownPreferencesRepository` in DataStore: darkness margin, alerts flag | 7 tests |
| 4a | The countdown reaches `TrackRecordingUiState` via the poll loop | 4 tests through the real entry point |
| 5a | `AlertKind.TURNAROUND` / `SUNSET`, their channel, `DecideSundownAlertUseCase` | 6 tests |
| 6 | The privacy policy names the sundown alerts | this change |

**Full suite at the last complete run: 172 classes, 1345 tests, zero failures.**

Every slice took a reverted-variant check under this repo's runner rules: a copy saved before
editing rather than restored from git, the build log checked for compile errors before any result
was read, a failure message specific to that edit, and the forward change confirmed present after
restoring.

## Decisions taken, with who made them

- **Both thresholds.** Sunset at −0.833°, civil dusk at −6°, one bisection called twice.
- **Recording only**, for now.
- **One hour**, named a *darkness margin* rather than a lead time, so the walk-back allowance can
  land beside it later without the two merging into a number that double-counts.
- **Two alerts**, at the margin and at sunset.
- **Shape B**: screen countdown plus two discrete posts, not a notification re-posted every
  fifteen seconds.
- **Alerts default on.** Owner: a safety feature disabled by default is indistinguishable from one
  that does not exist.
- **Sundown alerts override silence; off-track no longer does.** Owner ruling reversing their own
  earlier one. Straying is often deliberate; being caught out after dark is not.
- **The top navigation strip is the countdown's home.** The control pill stays exactly as it is, a
  UX choice for easy recording and navigation.

## Not built, and specified

### The screen row

A shared row carrying the countdown and status glyphs, composed into both top surfaces.

The measurement that matters for whoever picks this up: **the state has five hops to travel**,
`MainActivity:448` → `AvailabilityScreen:625` → `:1706` → `:3038` → `:3726` → `:4041` → `:4057`,
to reach two call sites, `NavigationHud(` at `:3800` and `CompassElevationStripContent(` at
`:4164`, inside a file of about four thousand lines.

Two surfaces, not one, because they are mutually exclusive on a single `isNavigating` gate and the
countdown matters **before** anyone starts navigating. Putting it only in the HUD would show it
first at the moment it stops being a decision.

Three requirements that are not optional on this surface:

1. **Coordinate touches, sampled across the row's bounds.** `AvailabilityScreenMapIconStackTest`
   exists because the icon-bar drag handle covered the locate row's whole centre while a semantic
   click would have passed. The broken region was the one region every centre-point test missed.
2. **A long-press test proving map touches still pass through.** The `Surface` interception
   pitfall has hit twice in one cycle, once on this very strip.
3. **Mount ordering is load-bearing.** `NavigationHud`'s doc: after the taxon filter chip, before
   `ForagerBottomNav`, because "a later-composed nav once swallowed the picker's OK tap."

### The service move

Owner-approved and deferred to after the beta. It closes the swipe-away hole for the sundown
alerts and for off-track in one move. `AlertDelivery`'s header describes the shape.

## Device-only by construction

Robolectric reports zero window insets and this app calls `enableEdgeToEdge()`, so a green suite
here is not evidence about any of the following:

- Where the strip actually sits against the real top inset.
- That the notification channels appear and behave as two separable categories.
- That `USAGE_ALARM` genuinely gets through a silenced phone where `USAGE_NOTIFICATION` does not.
  This is the half of the silence ruling only a device can confirm.

## Disclosure

### Confirmed by observation
Every test count above was read from the JUnit XML rather than from `BUILD SUCCESSFUL`, which with
a `--tests` filter can mean zero tests ran. The five-hop path and the two call sites were read
from the file.

### Could not be determined
Whether the shared row belongs in one merged strip with two states or in two mount points. Both
were put to the owner; the merged version is the better long-term shape and the larger change.

### Premises that were wrong
Mine, twice. The countdown was first described as sitting beside a visible return-to-vehicle
strip; that strip was removed and its text is now a `contentDescription` on the control pill. And
this report's earlier sibling said a countdown in the poll loop needed no new driver, which is
true of the screen and false of the alerts.

### Decided beyond scope
Nothing. The policy line in this change was named in the Phase 1 spec as landing with the feature.
