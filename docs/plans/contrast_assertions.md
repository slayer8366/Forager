# Deferred: night-mode marker work

Tracks code that used to run and doesn't any more — some removed twice over
(first the marker icon-shape swap, then the two contrast assertions it
made moot) — kept here rather than only in git history, so a later reviver
doesn't have to reconstruct it from scratch or dig through commits.

## The night-mode marker icon-shape swap (removed 2026-08-27)

**Removed on direct request, not a bug fix.** Night mode's icon-shape
markers (a target ring for the search centre, a plain dot for sightings, a
rounded-square badge for area markers — see `MapPalette.NIGHT`'s "Fifth
pass" for why they existed) were appearing over search-result observations
and foraging-area markers on real hardware, replacing the day markers
those are meant to look like — never the intent. `SightingsMap.kt`'s
markers now render as [MapPalette.DAY] unconditionally; night mode no
longer has any visible effect on sightings, area markers, the search
centre, planned trips, or waypoints. It still affects the basemap tiles
themselves (`BasemapStyles.kt`'s `NIGHT_RASTER_PAINT`: `raster-saturation`
`-0.35`, `raster-contrast` `0.1`) — that wasn't reported as a problem and
wasn't touched.

`MapPalette.NIGHT`/`NIGHT_WARM`/`NIGHT_INK`/`forMode` were **not** deleted
— they still exist in `ui/theme/MapPalette.kt` and are still exercised
directly by `MapPaletteTest`, the same "scaffolding with no production
caller" pattern `MotionTokens.kt`'s own unwired categories already use in
this codebase. Only `SightingsMap.kt`'s call site (`MapPalette.forMode(night
= nightMode)` → `MapPalette.DAY`) and `initializeOverlayLayers`'s
night/day branching were changed.

**What was deleted from `SightingsMap.kt`** (present at commit `26f4d89`,
the tip of the merged Understory PR, if the exact former code is needed —
`git show 26f4d89:app/src/main/java/com/forager/app/ui/map/SightingsMap.kt`):

- `initializeOverlayLayers`'s `night: Boolean` parameter and its three
  `if (night) { SymbolLayer + icon bitmap } else { CircleLayer }` branches
  for the search-centre, sighting, and area-marker layers — each now just
  the `CircleLayer` (day) branch, unconditionally.
- `nightHaloColor` (`withAlpha(palette.sightingDotStroke,
  NIGHT_ICON_HALO_ALPHA)` when `night`, else `null`) and `withAlpha` itself.
- Three night-only bitmap functions: `sightingDotBitmap` (filled circle +
  ink ring), `searchCentreTargetBitmap` (ink ring around a smaller filled
  dot — a target/bullseye), `areaMarkerBadgeBitmap` (rounded-square badge
  behind the numbered-area label).
- `plannedTripDiamondBitmap`/`waypointPinBitmap` (always called, day or
  night) lost their `haloColor: Int? = null` parameter and the
  halo-drawing branch inside each, since it was only ever non-null on the
  now-removed night path — their day-mode diamond/pin shapes are
  unchanged.
- Constants: `SIGHTING_DOT_ICON_ID`, `SEARCH_CENTER_ICON_ID`,
  `AREA_MARKER_ICON_ID`, `SIGHTING_DOT_ICON_SIZE_DP`,
  `SIGHTING_DOT_ICON_STROKE_WIDTH_DP`, `SEARCH_CENTER_ICON_SIZE_DP`,
  `SEARCH_CENTER_ICON_RING_WIDTH_DP`, `SEARCH_CENTER_ICON_DOT_RATIO`,
  `AREA_MARKER_ICON_SIZE_DP`, `AREA_MARKER_ICON_CORNER_RADIUS_DP`,
  `NIGHT_ICON_HALO_ALPHA`, `NIGHT_ICON_INNER_SCALE`,
  `NIGHT_ICON_HALO_MARGIN_DP`.

**To revive:** restore the git-history version of the code above (or
recreate it from `MapPalette.NIGHT`'s own doc comment, which still
describes the intended look in full), re-add the `night` parameter to
`initializeOverlayLayers`, and change `SightingsMap.kt`'s `mapPalette` back
to `MapPalette.forMode(night = nightMode)`. Whether it's still wanted at
all — and whether the "markers replacing the ones I want" complaint was
really about something fixable (e.g. an unintended trigger) rather than
the feature itself — is a call for whoever picks this up, not assumed
here.

## The night-marker tile-contrast assertions (removed earlier, 2026-08-26)

Two `MapPaletteTest` assertions that used to run, superseded even further
by the removal above — they checked `NIGHT_WARM` against a tile reference,
which no longer matters at all now that night mode has no marker-rendering
path to check. Recorded before the icon-shape swap above was known to be
unwanted; kept for the same "don't lose it, don't reconstruct it later"
reason.

## What they assert

```kotlin
// A floor: NIGHT_WARM must clear 4.0:1 against NIGHT_TILE_REFERENCE.
val ratio = contrastRatio(MapPalette.NIGHT_WARM, MapPalette.NIGHT_TILE_REFERENCE)
assert(ratio >= 4.0)

// A ratchet: night must be no less legible against its ground than day's
// weakest marker (1.79:1) is against DAY_TILE_REFERENCE.
```

## Why they're not active tests right now

Night mode's basemap dimming was removed outright (`BasemapStyles.kt`'s
`NIGHT_RASTER_PAINT` doc comment, "Dimming removed," 2026-08-26) — the old
`raster-brightness-max` 0.32 cap made the night ground read *lighter* than
the app's own dark-theme chrome (0.099 relative luminance against
0.005–0.038), fighting Android's own display handling rather than
complementing it. That leaves `MapPalette.NIGHT_TILE_REFERENCE` equal to
`DAY_TILE_REFERENCE` — a light tile — while `NIGHT_WARM` is itself a light
colour, never retuned for a light ground. Both assertions above would fail
today for that understood reason (measured ≈1.26:1, under both the 4.0:1
floor and day's 1.79:1 weakest-marker ratchet), not because of a defect.

**Not `@Ignore`d.** Tried first, rejected: this repo's CI
(`.github/workflows/ci.yml`, "Summarize the test results") fails the whole
build on *any* skipped test, by design — so `@Ignore` isn't a quiet way to
keep a dead-but-useful assertion around here. Removed instead, with the
exact code preserved as a comment in `MapPaletteTest.kt` (right after
`` `every NIGHT field is NIGHT_WARM or NIGHT_INK, nothing else` ``) and here.

At the time these were removed, night-mode markers stayed legible a
different way — icon shape (`SightingsMap.kt`'s `*Bitmap` functions) plus a
darkened, semi-transparent halo drawn behind each icon — rather than tile
contrast. That marker-rendering path was itself removed since (see the
section above), so this contrast question no longer applies to anything
currently rendered; it would only matter again if the icon-shape swap is
revived first.

## What revives them

`docs/plans/map-redesign.md`'s "Deferred: night-mode colour inversion"
section: real tile colour inversion (pale background → dark, dark linework
→ pale) needs new infrastructure — MapLibre 13.5.0's raster paint
properties have no per-pixel invert (`opacity`/`hue-rotate`/
`brightness-min`/`brightness-max`/`saturation`/`contrast`/`resampling`/
`fade-duration` only, confirmed via `javap` against the pinned AAR), so
this means intercepting and transforming tile images before MapLibre
renders them — a custom `Source`/tile pipeline or a raw-tile-byte
post-process step, not a paint-property tweak.

Once that lands and gives night mode a genuinely dark ground again:

1. Retune `NIGHT_WARM` (and `NIGHT_INK` if needed) against the new dark
   `NIGHT_TILE_REFERENCE`.
2. Paste the two assertions above back into `MapPaletteTest.kt` as real
   `@Test` functions.
3. Delete the removed-test comment they're currently recorded as, and this
   file.

Whether inversion is worth doing at all, and whether it's worth reverting
the icon-shape marker work to go back toward per-marker hue
differentiation once a dark ground gives more contrast headroom, is a call
for whoever picks this up — see `map-redesign.md`'s own section for the
full framing. This file only tracks the two specific assertions, not that
larger decision.
