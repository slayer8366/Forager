# Coder task: map redesign

Planning doc from the EGD planner session — a task spec for the coder, not a
replacement for the repo's real `CLAUDE.md`, whose standing principles govern
everything below.

**Do not dispatch or start this until `feature/mushroom-log` (Phase 1) has
merged to `main`.** Phase 1 is, as of this writing, actively editing
`AvailabilityScreen.kt` and `SightingsMap.kt` — the same files this task
rewrites — and is specifically wiring "log a find" into the map's long-press
gesture, which this task's add button needs to unify with. Starting in
parallel risks real rework or a merge conflict in these files. See
`docs/plans/README.md` for current status.

## What this is

The project owner wants the map's UI polished toward the look of a
reference outdoor-GPS app (reference screenshot reviewed live in the
planning conversation, not reproduced here — ask the owner if you need to
see it again). The map becomes the app's core, full-bleed screen rather
than one tab among equals sharing space with a search-summary bar and a
top tab row.

**This is a UI/navigation restructure, not a new data source or basemap
provider.** Everything drawn on the map today (sighting dots, foraging-area
markers, planned-trip diamonds, the dashed visiting-order connector) keeps
its existing meaning and implementation in `SightingsMap.kt` — see that
file's own doc comment for why the overlay list is basemap-agnostic and
must stay that way (`applyBasemap` deliberately never touches
`view.overlays`).

## Scope decision made after this doc was written: window-width adaptivity

Before implementation started, `AvailabilityScreen.kt` gained M3 window-width
adaptivity (`WindowWidthClass`, merged from a separate, unrelated planning
branch) that this doc's author never saw: on `COMPACT` windows the screen is
the single-pane, tab-switched layout this whole doc assumes; on `MEDIUM`/
`EXPANDED` windows it instead uses a `PermanentNavigationDrawer` (the drawer
panel always on-screen, never opened/closed) plus `CombinedResultsPane`,
which shows List and Map side by side at once — Map no longer has the
screen to itself there, and List/Map are no longer separate tabs (only
Seasonal still is).

That conflicts with this doc's core moves in ways with no obvious answer —
full-bleed map vs. Map sharing width with List; bottom nav replacing
`SecondaryTabRow` vs. the tab row's wide-window meaning (List+Map combo vs.
Seasonal); the 5-icon stack and fullscreen toggle vs. a permanent side
panel that's never "closed." Raised with the project owner, who had no
preference between scoping down or designing all three width classes.

**Decision: this entire redesign — full-bleed map, bottom nav, right-edge
icon stack, fullscreen chrome toggle, compass/elevation strip — applies to
`WindowWidthClass.COMPACT` only**, exactly as originally specified below.
`MEDIUM`/`EXPANDED` keep the `PermanentNavigationDrawer` +
`CombinedResultsPane` layout exactly as merged, untouched by any of this.

**Rejected: extending the redesign to all three width classes.** That would
require inventing answers to the conflicts above with no planning input
behind them (e.g., does full-bleed Map still share space with List on a
wide window? does the icon stack overlay a permanent drawer, or coexist
beside it?) — speculative design CLAUDE.md warns against building without
real direction. Scoping to COMPACT matches what was actually specified and
touches nothing about the just-merged wide-window path.

## Current baseline — verified by reading the code, re-verify before relying on it further

(All of this may have shifted if Phase 1 or other work touched these files
before this task starts — read them fresh.)

- Today's `Map`/`List`/`Seasonal` tabs are a `SecondaryTabRow` at the *top*
  of the content column, above `weight(1f)`-sized tab content
  (`AvailabilityScreen.kt`, the `ResultsTab` enum and the `Column` around
  line 436–487 as of `main@141de1e`). There is no bottom navigation bar
  anywhere in the app today.
- The topo/regular basemap toggle **already exists**: `MapModeToggle`
  (`AvailabilityScreen.kt`, private composable around line 950), currently
  positioned `Modifier.align(Alignment.TopEnd)` inside a `Box` inside
  `MapTab` (around line 1779) — i.e. already top-right over the map. This
  task repositions and restyles it into the new icon stack; the toggle
  logic itself (`isTopoMode` / `onToggleMapMode`, threaded from
  `AvailabilityScreen` into `MapTab`) does not need to change.
- `LocationProvider` (`domain/LocationProvider.kt`) exposes only
  `LocationResult.Success(lat, lng)` — no altitude. Extending it for the
  compass/elevation strip below is a real interface change, not a read of
  something already there. It already has an existing use — "use current
  location" for setting the *search region* (`onUseCurrentLocation`, wired
  into `SearchControls` around line 1308) — that is a different feature
  from the new locate-me map button below; don't conflate the two call
  sites, but do reuse the underlying `LocationProvider`/`AndroidLocationProvider`.
- There is **no compass or device-sensor code anywhere in the app** —
  checked (`grep -ri "compass|sensormanager|orientation|bearing"` across
  `app/src/main/java/com/forager/app`, only unrelated matches: Material's
  `tonalElevation`, doc-comment uses of the word "bearing"). This is new,
  not a restyle.
- Settings, Trip Planner, and the existing species/region search panel live
  in a `ModalNavigationDrawer` (`DrawerPanel` enum), reached today via a
  tune icon in the top app bar (`IconButton(onClick = onOpenDrawer)` around
  line 1268). Swipe-to-open is deliberately disabled — the code comment at
  the `ModalNavigationDrawer` call site explains why: the content behind it
  is a pannable map, and a horizontal drag there means "pan," not "open the
  drawer." Keep that disabled; do not reintroduce swipe-to-open just because
  the map is now full-bleed.
- Theme colors already established and reused by the map layer (raw
  `android.graphics` colors in `SightingsMap.kt`, not Compose theme tokens,
  since osmdroid draws on a raw `Canvas`): forest green
  `0xFF2E5339` (area markers), mushroom orange `0xFFC97B3D` (the visiting-
  order connector). Match the new bottom-nav "active tab" color and any new
  icon-stack styling to this existing palette rather than introducing a new
  accent color.

## Decisions made in planning (do not re-litigate; from the project owner)

Resolved via direct questions to the owner during planning — treat these as
settled, not open:

1. **Scope**: the whole visual package, not one element in isolation.
2. **Map screen goes full-bleed.** In its resting state the map fills the
   entire screen; the right-edge icon stack and top compass/elevation strip
   overlay it.
3. **Right-edge floating icon stack, top to bottom, exactly 5 icons** (dark
   translucent circles, white glyph, matching the reference app's stack
   style — the bottom one filled green instead of dark):
   1. **Fullscreen** — toggles all chrome (see #5).
   2. **GPS / locate-me** — new capability, recenters the map on the
      device's live location. Distinct from the existing "use current
      location for search region" control; needs its own explicit
      denied/unavailable UI state — no silent fallback, per `CLAUDE.md`.
   3. **Topo/plain switch** — reuses existing `MapModeToggle` logic,
      restyled/repositioned only.
   4. **Search** — opens the existing `DrawerPanel.Search` (region/radius,
      species/taxon filter, recent searches). Second entry point to the
      *same* drawer panel the app-bar tune icon already opens — not a new
      search feature, not a place-name lookup.
   5. **Add (+)** — opens the same action menu the map's long-press
      gesture opens. Today (pre-mushroom-log) that's "plan a trip." Once
      Phase 1's long-press change has merged, it's whatever menu Phase 1
      built (expected: plan-trip / log-a-find picker) — this button must
      call the *same* handler the long-press gesture calls, not a
      parallel implementation, so the two entry points can never drift
      apart. **Verify what Phase 1 actually built here before wiring this**
      — this doc was written before Phase 1 merged.
4. **Bottom nav replaces the top `SecondaryTabRow`**: `List` / `Maps` /
   `Seasonal`, icon + label, dark bar, active tab highlighted in the
   existing forest green (`0xFF2E5339` or the closest Compose theme token
   to it — check `ui/theme/Color.kt` for whether a matching token already
   exists before introducing a new raw color). Same three destinations as
   today's `ResultsTab` enum, same order — this is a visual/positional
   change, not a new destination or a removed one.
5. **Fullscreen hides all chrome**: both the top app bar and the new bottom
   nav — leaving only the map and the floating icon stack. Tapping the map
   (or the fullscreen icon again) restores chrome. In the *default* (non-
   fullscreen) resting state on the Maps tab, the existing top app bar
   (species/category search field, drawer tune icon) and the new bottom nav
   are both visible — fullscreen is an explicit opt-in, not the resting
   state.
6. **Settings/Trip-Planner/Search stay in the existing drawer**, reached via
   the existing app-bar tune icon in the default (non-fullscreen) state.
   **No 6th floating icon added** — the owner's icon list was exactly 5, and
   the drawer becomes reachable again the moment fullscreen is toggled off.
7. **Top strip: compass + elevation, combined into one bar**, positioned at
   the very top of the map (below the app bar, in the default state — above
   nothing but the map itself in fullscreen). One bar, not two separate
   widgets. Modeled on the reference app's compass-tape widget with
   elevation folded in, *not* its separate elevation/speed stats pill (that
   pill is tied to active track recording, explicitly out of scope — see
   below).
8. **Elevation source: GPS altitude**, read off the same location fix used
   for the GPS/locate-me button (#3.2) — not a network elevation lookup.
   Requires extending `LocationResult.Success` with a nullable
   `altitude: Double?` (nullable because `Location.hasAltitude()` is false
   on network-based fixes — a genuine "not provided," shown as unavailable,
   never defaulted to a guessed value or omitted silently).
9. **Explicitly out of scope — do not build**: track/route recording, the
   elevation+speed stats pill tied to an active recording, the record
   button, any "trip" concept beyond the existing `PlannedTrip`. Location
   tracking is a separate feature to be scoped later; nothing here should
   assume it's coming or leave a half-built hook for it.

## New capability: compass

No sensor code exists in this app. Needs an owned interface — e.g.
`CompassProvider` — wrapping Android's `SensorManager`
(`TYPE_ROTATION_VECTOR`, or `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` if
rotation vector isn't available on target devices; check both), per
`CLAUDE.md`'s rule that hardware integrations live behind an interface this
project owns, never named directly in domain or ViewModel code. Needs a
fake for headless testing of whatever UI reads the heading (e.g. does the
compass strip rotate/redraw correctly for a given heading value, tested
without a real sensor).

## Layout mechanics — read `SightingsMap.kt`'s doc comment before touching this

`SightingsMap.kt` already has hard-won documentation on why its `AndroidView`
needs `Modifier.clipToBounds()` — osmdroid paints tiles, markers, and
polylines well outside its own view rectangle by design, and Compose does
not clip a hosted `View` to its slot automatically. Making the map full-bleed
changes its size but not this fact: **the clip modifier stays**, and if the
full-bleed layout introduces a new resizing path (e.g. chrome
showing/hiding changes the map's measured bounds), re-read "Why the clip" in
that file and consider whether `SightingsMapBasemapSwapTest`-style coverage
needs extending, rather than re-deriving the fix from scratch.

## Tests required

- Headless: the new `LocationResult.altitude` field — present when the
  underlying fix has it, `null` when it doesn't, never defaulted.
- Headless: `CompassProvider` fake — whatever heading-dependent UI logic
  exists (e.g. rotation angle mapping) is testable without a real sensor.
- Compose/Robolectric: the icon stack's 5 buttons are present, positioned
  right-edge top-to-bottom in the specified order, and each fires the
  correct callback (mirroring how `AvailabilityScreenSettingsPanelTest`
  and `AvailabilityScreenTripPlanningFlowTest` test existing map-adjacent UI
  today).
- Compose/Robolectric: fullscreen toggle actually hides/shows the app bar
  and bottom nav; the map itself stays mounted throughout (don't tear down
  and recreate the `MapView` on a chrome toggle — that would drop
  in-progress state and refetch tiles unnecessarily).
- Compose/Robolectric: bottom nav's 3 destinations select the same
  `ResultsTab` values the old top `SecondaryTabRow` did — this is a
  positional change, existing tab-switching tests should mostly still
  apply; update rather than duplicate them.
- **Not verifiable headlessly, report as such**: live compass heading
  accuracy/smoothness on a real device, GPS altitude accuracy in practice
  (already a known limitation — report the *behavior* as tested, not the
  real-world number), how the full-bleed layout reads on very small/large
  screens or at large font scale. Add to README's "Not yet verified".

## Delivery

1. Confirm Phase 1 (`feature/mushroom-log`) is merged to `main`, and read
   what it actually did to the map's long-press handler before wiring the
   add (+) button to it.
2. Branch off current `main`.
3. Implement in roughly this order: `LocationResult.altitude` extension →
   `CompassProvider` + fake → bottom nav replacing `SecondaryTabRow` →
   full-bleed `MapTab` layout → right-edge icon stack (reusing
   `MapModeToggle` for slot 3) → fullscreen chrome toggle → top compass/
   elevation strip.
4. All tests above; `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`;
   report actual pass/fail counts.
5. Update README: "How it works" / "Project layout" for the new nav
   structure, "Not yet verified" for the device-only items above.
6. Open a PR against `main`, using the repo's PR template if one exists.
7. If anything in "Decisions made" turns out to conflict with what Phase 1
   actually shipped (not just the add-button wiring — e.g. if Phase 1 also
   touched navigation structure), stop and report rather than silently
   picking a resolution, per `CLAUDE.md`'s ambiguity rule.
