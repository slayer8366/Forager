# Recheck against `f7c9f15`

**Date:** 2026-09-12
**Base: `f7c9f15`** (`origin/main`), fetched and verified before writing.
**Type:** recheck of standing findings after PRs #97 and #98 landed. No app code changed.

Applying the rule adopted yesterday: base SHA stated, remote fetched, absence claims re-derived.

## What moved

`5515adc..f7c9f15`: nine files, 168 insertions, all under `ui/`. Records-tab labels, the offline
picker's 4:3 viewport, and **"Map owns drags that start on it"** (`a9e8a4f`). Two of the nine are
MapLibre-coupled files this audit thread cites by line count.

## Standing findings, re-derived

| Claim | On `f7c9f15` |
|---|---|
| Attribution caption + MapLibre control both kept | **Holds.** `SightingsMap.kt:379` `setAttributionGravity`, `:575` `mapAttributionFor` |
| Process-death gap (§3b of the 09-12 re-derivation) | **Still open.** `beginLocationTracking()` one call site, line 213 |
| MapLibre-coupled LOC | **2,808**, was 2,737. `SightingsMap` 1,163 → 1,198; `CentrePinLocationPicker` 188 → 224 |
| Every other audit claim | Untouched files; no re-derivation needed |

The LOC figure is corrected here because it was quoted as exact; it changes no conclusion.

## New: the drag fix has no test of the drag

`a9e8a4f`'s purpose, in its own comment: "a drag that starts on the map pans the map, even when an
ancestor is scrolling." The mechanism is a per-`ACTION_DOWN` `requestDisallowInterceptTouchEvent`
re-asserted through an `OnTouchListener`, with the reasoning checked against the 13.5.0 and
compose-ui 1.12.0 artifacts' bytecode. That is careful work, and it was found by **the owner's
report from a device**, not by a test.

**The test changes in the same commit are `performScrollTo()` adjustments only** — existing
assertions moved below the fold by the taller viewport. Searched `app/src/test` on `f7c9f15`:

- `CentrePinLocationPickerTest.kt`: five tests, all `performClick`; its "pan" is a `"Simulate pan"`
  button, not a touch.
- The two files containing `swipe`/`moveTo` (`AvailabilityScreenLayoutTest`,
  `AvailabilityScreenMapIconStackTest`): no swipe near the picker, offline panel, or
  `CentrePinLocationPicker`.
- `requestDisallowIntercept` / `OnTouchListener` in any test: zero hits.

So the routing claim — the whole point of the change — has no coordinate-touch coverage.
`CLAUDE.md` names this exactly: "A semantic `performClick` asserts wiring, not routing," and "Any
layout composed over a map needs at least one such test, not just visual review — visual review is
exactly what missed this twice." This is the third time on this surface, and the third time a device
caught it.

**What the test would be:** `performTouchInput { down(centre of picker map); moveBy(0, -200);
up() }` inside `OfflineMapsPanel` with its `verticalScroll` Column, asserting the panel's scroll
offset did not change (or the map received the move). A drag, not a click, because a click cannot
reach `FLAG_DISALLOW_INTERCEPT` at all.

**One honest caveat before anyone writes it:** whether Robolectric propagates
`requestDisallowInterceptTouchEvent` from an `AndroidView`-hosted `MapView` through
`AndroidViewHolder` → `PointerInteropFilter` faithfully is not known here. If it does not, the test
cannot be written as a unit test, and `CLAUDE.md`'s own rule for that case applies: say so in the fix
and in the report, rather than let a green suite stand in for coverage it does not have.

## Disclosure

**Verified on `f7c9f15`:** the diff stat; the six LOC counts; the two attribution sites; the single
`beginLocationTracking()` call site; every test grep above, run against `origin/main`, not the
working tree.

**Not determined:** Robolectric's fidelity for the disallow-intercept path.

**Merge note:** `f7c9f15` merged into this branch cleanly; no index conflict this time.
