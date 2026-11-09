# Sorrel Tally: PR #514 (promotion-2026-11-09 into main), "Motion spec compliance" checklist

Reviewer: Sorrel Tally (Fable 5.1). Read-only; nothing changed, nothing pushed. 2026-11-09.

Base read: `origin/main` vs `origin/promotion-2026-11-09` (fetched this session); `docs/motion-spec.md` on
`origin/pre-main` (its diff against main adds two rows, both for the map credit strip, and nothing else);
`.github/PULL_REQUEST_TEMPLATE.md` and `CODEOWNERS` on `origin/main` (motion owner: @slayer8366).
`scripts/verify-design-tokens.sh` was run on a `git archive` extract of the promotion tree at
`/mnt/work/scratch/sorrel-514-motion/tree` (not in CI by design; output below). No Gradle. Nothing private read.

Motion in this promotion, as found (all paths under `app/src/main/java/com/zynergylabs/forager/app/ui/`):
WordSwap.kt (changed); the selected stop's fade-and-pulse (map/RouteStopAccentMotion.kt, map/MapRouteStopAccent.kt,
map/FanRouteStopAccent.kt, all new); the route card's tuck slide and content fade (availability/RouteCardTuck.kt,
route/RouteCardFace.kt, new); the route card's rows reveal (route/RouteCardReveal.kt, new); the portrait route card's
slide (availability/RouteCardPlacement.kt:212-221, new); the landscape navigation card's J5 lift glide
(availability/CompactMapLandscapeNavigationCard.kt:134-137); the D29 side-padding shift (map/MapNavigationView.kt:84-96);
the locate glide (map/LocateGlide.kt, new); the crowded-stack ease (map/MapStackCamera.kt:64, 85, new); the credit
strip's fade (map/MapCreditStrip.kt, new). `motion/MotionTokens.kt` is unchanged by this promotion; `MotionTokensTest`
is unchanged; `motion/WordSwapTest.kt` is new.

## The nine items

### 1. Precedence order (legibility > performance > calm) — PASS, by reading; no test can cover it (spec §1: "a rule for whoever designs motion, not code")

- Every new animation is draw-only alpha/translation or a finite timeline, as §2 asks ("Prefer graphicsLayer transforms
  and alpha"): tuck content and grip on `graphicsLayer` (RouteCardFace.kt:200-203, 214-217); credit strip on
  `graphicsLayer` alpha, size and place kept (MapCreditStrip.kt:68); the highlight is one GeoJSON source's data per frame,
  no layer added or restyled (MapRouteStopAccent.kt:17-20, 58-66), and its frame loop stops at `isSettled`
  (MapRouteStopAccent.kt:49-52; RouteStopAccentMotion.kt:112-116).
- Nothing new runs continuously. The pulse is one 500 ms swell and done (RouteStopAccentMotion.kt:25-27, 55-65).
- One legibility cost shipped knowingly: known bug 24 (docs/process/known-bugs.md:48), the credit strip at font 2.0 wrapping
  into a band about 256 px tall over the record button and navigation card for its 5 s, ruled by the owner for next
  release (RECORD -2192). That is a layout defect of a motion surface, not a precedence inversion; noted so the owner
  ticks this knowing it.

### 2. Scope boundary (no confidence score, candidate list, species suggestion, per-observation harm) — PASS. Not waivable; no waiver needed.

- Every added line under `app/src` grepped for confiden|candidate|species sugg|suggest|likely|harm|edib|toxic|identif|
  look-alike|score. Hits, each read: `candidates` as address/geometry variables (domain/dropoff/StreetDropOff.kt:114 and
  a test's offset list); `likely` in comments about retry timing; `UnidentifiedFeature`/`onUnidentifiedFeature` (a map
  tap on a feature with no id); `ownIdentification` (the user's own typed name, pre-existing field); and the comment
  "species suggestion popup" with `onReopenTaxonSuggestions`, which is the iNaturalist taxon-name search's autocomplete
  and pre-exists on `origin/main` (AvailabilityScreen.kt:807, MainActivity.kt, AvailabilityScreenSearchInputs.kt) — a
  search-term lookup, not a per-observation suggestion, and not introduced here.
- Nothing in the motion code reads an observation's identity: the highlight is keyed by layer id and feature id only
  (RouteStopAccentMotion.kt:33-35; FanRouteStopAccent.kt:25).

### 3. All durations/easings from MotionTokens.kt; no magic numbers at call sites — FAIL (two findings, one at the item's letter and one at its spirit)

(a) **A raw tween with a literal at a call site, new in this promotion.** `route/RouteCardReveal.kt:27`
`private const val ROUTE_CARD_SLIDE_MILLIS = 220` and `:67` `settled.animateTo(if (expanded) 1f else 0f,
tween(ROUTE_CARD_SLIDE_MILLIS))` — the rows' height slide on expand/collapse (RECORD -1910). The file is not on
`origin/main`. `scripts/verify-design-tokens.sh` check 3 ("no tween( in ui/; one allowed: navigationViewChromeSpec")
fails on it when run on the promotion tree (the other check-3 hits, map/fanout/MarkerFanOutState.kt:87 and
backup/RestoreLoadingPage.kt:94-100, pre-exist on main and are not this promotion's). ADR-0002's amendment records the
rejection of exactly this: "Rejected: an exception for 'timed' animations in general, which would let durations back
in one plea at a time" (docs/adr/0002-motion-scheme-adoption.md:402-405). It also has no Reduce Motion branch (see 6).
**What would fix it:** `settled.animateTo(target, MotionTokens.panelMotionSpec())` (or `listRowSpec`, critically damped,
if the owner would rather the rows cannot overshoot the total line) read in composition and passed into
`RouteCardReveal`; delete the constant; a §2 row. Otherwise the owner's waiver with a reason and expiry.

(b) **A new timed category whose durations and easing live outside MotionTokens.kt.** `map/RouteStopAccentMotion.kt:22-30`
declares `ROUTE_STOP_ACCENT_FADE_MS = 200L`, `PULSE_START_MS = 150L`, `PULSE_PEAK_MS = 300L`, `PULSE_END_MS = 500L`,
`PULSE_SCALE = 1.15f`, and `:49-52` a hand-written smoothstep `ease()`. They are named constants, not literals at the
call site, and the animation is plain Kotlin on a millisecond clock driving a MapLibre source (not a Compose spec), which
is the shape the spec already accepts for the location puck — but the puck's duration was placed in MotionTokens
(`LOCATION_INDICATOR_MOVE_DURATION_MS`, MotionTokens.kt:122) and documented as the one non-spec category; this one is in
`ui/map`, the script cannot see it, and `docs/motion-spec.md` §2's "Selection emphasis" row still says its spec and
bounds were "removed unused". **What would fix it:** move the five constants to MotionTokens.kt as the selection-emphasis
category's plain constants (the pattern of `PRESS_BOUNCE_SCALE`, `MAP_POPUP_ENTER_SCALE`), import them here, and
rewrite the §2 row. Code-only move, no behaviour change; the test `RouteStopAccentPulseTest` reads the constants, so it
would still bite.

(c) **The one-tween exception now carries non-camera animations — a spirit question for the owner, not a letter fail.**
`MotionTokens.navigationViewChromeSpec()` is allowed as "exactly as long as the map's tilt" (spec §2; ADR-0002
amendment, RECORD -672). This promotion calls it for the tuck (RouteCardTuck.kt:90), the portrait route card's slide
(RouteCardPlacement.kt:214-215) and the J5 lift glide (CompactMapLandscapeNavigationCard.kt:137), none of which is timed to
a camera move. The precedent is older: the navigation cards' own slides already used it on `origin/main`
(CompactMapLandscapeNavigationCard.kt:73-74, CompactMapPortraitNavigationCard.kt:82-83). The script passes (the tween
has one definition); the spec's wording "allows that one call site and no other" is about the `tween(` line, so the
letter holds. Flagged for the owner: either the spec says the chrome tween is also "the route card's and navigation
card's timing" (a doc row), or those three move to a spring.

(d) Pass, with the spec's own precedent: the locate glide (LocateGlide.kt:31), the stack ease (MapStackCamera.kt:64, 85)
and the D29 padding shift (MapNavigationView.kt:94) are MapLibre native camera moves on `NAVIGATION_VIEW_TRANSITION_MILLIS`
(map/NavigationView.kt:330, 750 ms) — the constant the spec names as read from that file; no literal. The "750 ms" in
LocateGlide.kt:24 and MapStackCamera.kt:76 is comment text only.

(e) Pass: the credit strip's fade is `MotionTokens.mapPopUpFadeSpec()` (MapCreditStrip.kt:64); `MAP_CREDIT_STRIP_SHOWN_MILLIS
= 5_000L` (:95) is a dwell before the fade, OSMF's "five seconds", not an animation duration. WordSwap stays on
`wordSwapSpec` (WordSwap.kt:53); its change is which state is animated, not timing.

(f) Beside the checklist, same script: check 1 now fails on `map/PlottedRouteLayers.kt:77`
`internal val RouteStopDimmerCream = Color(0xFFE3D3B5)` (new; the owner's dimmer day cream, RECORD -2187) — a colour
literal outside `ui/theme`. The fix is a move to the theme package. Not a motion item; reported because the same
script that guards this item reports it.

Tests: `MotionTokensTest` (unchanged) holds every category but the chrome tween to "no TweenSpec" — it cannot see a
tween at a call site; the script can, and it is not in CI. `WordSwapTest` (new, 7 tests) covers the crossfade and the
numbers-instant rule, including the stopped-activity case the change was made for. `RouteStopAccentPulseTest:80-150`
pins ~200 ms fade, ~15% swell, ~0.5 s settle from the constants.

### 4. Performance budget under a dense-map fixture; clustering before the animated-object cap — NOT DETERMINABLE

- The spec itself records (§3) that the budget and the clustering threshold were never enforced by code (removed
  2026-10-07). This promotion adds no counter and no frame measurement, so no test can answer the item.
- By reading: the highlight animates at most two features (one arriving, any leaving) for 500 ms and then stops asking
  for frames (RouteStopAccentMotion.kt:105-116; MapRouteStopAccent.kt:49-52); the tuck, lift, slide and credit strip are
  one surface each. Clustering is the crowded stack: the fan ring caps at `FAN_RING_MAX = 8` (map/fanout/MarkerFanOut.kt:33)
  and larger stacks open the list (CrowdedStackListModeTest), both pre-existing; `CrowdedStackEaseTest` runs a
  thirty-marker fixture for the zoom and ease but asserts placement, not object counts or frame time.
- Device: Elder's audit item 12 states the pulse's 0.5 s per highlight and the two card layouts per compass reading as
  battery costs, "not a fail"; the S22 passes (-2192) report nothing about frame rate.

### 5. Degradation order implemented and tested — FAIL AS STANDING (not this promotion's doing; needs the owner's decision)

- Spec §3: the tiers "were encoded ... Removed 2026-10-07 ... nothing in the app called any of it, so the budget was never
  enforced by code." No implementation and no test exists on main, pre-main or this branch (grep for DegradationTier,
  battery-saver reads: none in `ui/`).
- Nothing new here belongs to the first tier (decorative continuous loops); the pulse is finite. So there is nothing new
  for a degradation order to act on, but the item as written cannot be ticked. The owner either waives it with an expiry
  (as every PR since 2026-10-07 would have needed) or re-words the template to match the spec's 2026-10-07 state.

### 6. Reduce Motion mappings implemented and tested; state stays communicated — PASS WITH GAPS (two animations have no mapping; the spec's §4 table is behind the code)

Implemented and tested:
- Pulse: instant, no swell, highlight kept (RouteStopAccentMotion.kt:97-99, 108, 114, 118-119). Test:
  `RouteStopAccentPulseTest:135` "under Reduce Motion the highlight is there at once, with no pulse". Seen on the S22 under
  "Remove animations" (-2192, build 5054).
- Portrait route card slide: fade only (RouteCardPlacement.kt:213-219). Test: `RouteCardSlideTest:106`.
- Crowded-stack ease: jumps (MapStackCamera / CrowdedStackEaseTest:148, 160).
- Credit strip: the fade kept, per the new §4 row. Test: `MapCreditStripFadeTest:282` with a positive control that the
  app reads the setting.
Implemented, not tested:
- Tuck: slide instant, content fade kept on `mapPopUpFadeSpec` (RouteCardTuck.kt:86-93). No test composes the tuck under
  reduce motion (`RouteCardTuckCloseTest`, `RouteCardStripSlideTests`, `AttributionWhileTuckedTest`: none read
  `LocalReduceMotion`; `RouteCardBarRig.setScreen(reduceMotion=)` exists at :121 and no tuck test passes it). Seen on the
  S22 under "Remove animations" (-2192), so device-checked, not suite-checked.
- J5 lift glide: `snap()` under reduce motion (CompactMapLandscapeNavigationCard.kt:134-137). `NavigationCardGlidesTest`
  has one test, full motion only.
- D29 padding shift: 0 ms under reduce motion (MapNavigationView.kt:88-94). Device-only by construction (MapLibre).
Not implemented:
- **Locate glide.** `glideToLocate` always eases 750 ms (LocateGlide.kt:27-32); its caller MapDataRefresh.kt:135-137 reads
  no reduce-motion value (0 occurrences in the file). The sibling camera eases both map to a jump (stack ease; D29). The
  state is still communicated (the camera arrives), so it is not a kill switch, but it is the one new camera motion with
  no mapping. **Fix:** read `LocalReduceMotion` where MapDataRefresh is composed and set `map.cameraPosition = end` when
  on, mirroring `MapStackCamera`'s "jumps when asked not to animate"; a case in `EntryMapLocateGlideTest`.
- **Route card rows reveal.** `RouteCardReveal.settle` always tweens 220 ms (RouteCardReveal.kt:66-68); no reduce-motion
  read. Fix together with 3(a).
Documentation: `docs/motion-spec.md` §4's table gained one row (credit strip). No row for the pulse, the tuck, the lift
glide, the padding shift, the locate glide, the route card's slide or its reveal. §2 likewise has no row for any of them.
The spec's own claim is "Code under .../ui/motion/ implements this doc"; for these seven, the code is ahead of the doc.
A doc-only change, low risk tonight; the owner's call whether it lands before or after the promotion.

### 7. Color is never the sole carrier of state — FAIL by the owner's own ruling; needs the owner's waiver or a shape change

- The selected stop, at rest: the same glyph in the accent colour and nothing else. `MarkerIcon.WAYPOINT_ROUTE_SELECTED`
  and `PLANNED_TRIP_ROUTE_SELECTED` differ from `WAYPOINT`/`PLANNED_TRIP` only in the colour function (MarkerGlyphs.kt:297,
  299 vs 305-306; same `MarkerGlyph`, `halo` false); the pulse returns the scale to 1 (RouteStopAccentMotion.kt:63) and
  the opacity settles at 1 over the plain pin; the card's rows "look alike ... the selection shows only by its marker on
  the map ('Only the marker')" (RouteCardStops.kt:38-40). Cream by day, rust at night (PlottedRouteLayers.kt:61-67).
- Redundancy that exists: the row carries `semantics { selected = ... }` (RouteCardStops.kt:77), so a screen reader is
  told; the Navigate button names the stop. Neither is a visual carrier of the kind §4 lists ("outline weight, pattern,
  icon, or shape"). Under glare or a wet screen (§5) the cream-on-cream of a day pin is the case the rule is for.
- This is the owner's ruling (RECORD -2187: "Accent colour", "Keep cream, judge on phone", "Only the marker"), judged on
  the S22 by day and night. The template says only the owner may waive, with a reason and expiry; if waived, the reason
  is on the record already. **What would fix it without a waiver:** a halo under the selected pin — the halo glyph
  path exists (MarkerGlyphs.kt:302-304, `halo = true`, used by J8's journal halos), so a `WAYPOINT_ROUTE_SELECTED_HALO`
  drawn beneath is a shape carrier at low cost; or a row mark in the card, which the owner rejected ("Only the marker").
- Pass elsewhere: the tucked strip has a grip bar (RouteCardFace.kt:209-224); the lifted card gets rounded corners and an
  outline (NavigationCardLift.kt:5-7); the locate ring and Re-center sit in the map-chrome outline (#506); the credit
  strip's state is presence, not hue.

### 8. Latency states (GPS acquiring, offline tiles) have explicit, non-misleading treatments — PASS for what changed (one part read, not run)

- Locate: `LocateMeStatus.Loading(previous)` (LocateMeStatus.kt:15-23, 40-45) keeps the camera on the last fix while a
  fetch runs ("stop the jump"), and the glide runs only on a new fix — `isLocatePanOnly` requires a non-null, changed
  point (LocateGlide.kt:18-20) — so no motion implies a fix that has not arrived. Covered: `LocateKeepsCameraTest` (new,
  286 lines), `AvailabilityViewModelLocateMeTest` (changed). What the ring shows during Loading (`AvailabilityLocateMe.kt`,
  `ReCenterOnLocate.kt`) I did not read in full; stated as unverified.
- Offline tiles: the credit strip never fades while OpenStreetMap's raster is drawn and treats offline as the vector
  style (MapCreditStrip.kt:84-88; `DrawsStreetRasterTest` per its comment). No other offline-tile motion changed.
- Known bug 29 (known-bugs.md:53, my own item on #512): a navigation view applied inside the locate glide's 750 ms could
  lose tilt and padding — a latency interaction, "Optional, next release". The owner ticks this knowing it.

### 9. Validated against the full Field Test Conditions list (§5) — NOT DETERMINABLE; partial on the record

- §5's list: direct sun and deep shade; wet or dirty screen; gloves or cold hands; one-handed while walking; battery-saver;
  offline or intermittent; mid-range hardware; no GPS lock or delayed lock.
- The record (-2192; Elder's audit §3) shows S22 checks on combined builds 5032, 5054, 5074, 4990: day and night, font
  scales, "Remove animations", rotation, the fan, the tuck, the pulse, the locate glide, keep-following. Not recorded for
  this release's motion: sun/shade, wet or gloved use, battery-saver, intermittent connectivity, a delayed-lock run. The
  S22 Ultra is the test phone, not mid-range hardware. Elder lists what was not seen on a phone (a plain build cold-started,
  a walked next-turn route, the tablet, RTL, the night cream on a trip flag, the mid-glide lowering case).
- Whether any earlier release validated the full list I could not determine from the records read; I make no claim about
  it. The owner either waives with an expiry or accepts the S22 checks as the validation for this release.

## verify-design-tokens.sh on the promotion tree (not in CI; run on the extract)

```
FAIL  no Color(0x literal outside ui/theme/        PlottedRouteLayers.kt:77 (new), CameraOverlay.kt:82 (pre-existing)
FAIL  no palette constant imported outside theme    (pre-existing imports; not listed here)
FAIL  no tween( in ui/ (one allowed)                RouteCardReveal.kt:67 (NEW), MarkerFanOutState.kt:87, RestoreLoadingPage.kt:94-100 (pre-existing)
PASS  no effects animation retargets alpha to a non-bound value
```

## Summary table

| # | Item | Verdict | Needs the motion owner's decision |
|---|---|---|---|
| 1 | Precedence order | Pass (by reading) | No |
| 2 | Scope boundary | Pass | No (not waivable; none needed) |
| 3 | Durations/easings from MotionTokens | **Fail**: RouteCardReveal.kt:27,67 raw tween, 220 ms; RouteStopAccentMotion.kt:22-30 timings outside MotionTokens | Fix or waive; plus the chrome-tween reuse question (3c) |
| 4 | Performance budget, dense fixture | Not determinable (never code-enforced; nothing new continuous) | Yes: tick on reading, or waive |
| 5 | Degradation order | Fail as standing (spec §3 removed it 2026-10-07) | Yes: waive with expiry or re-word the template |
| 6 | Reduce Motion | Pass with gaps: locate glide and rows reveal have no mapping; tuck/lift untested in suite; §4 table short seven rows | Fix the two gaps or waive; doc rows |
| 7 | Colour not sole carrier | **Fail** by the owner's "Only the marker"/"Accent colour" ruling (-2187) | Yes: waive with reason and expiry, or add a halo |
| 8 | Latency states | Pass for what changed (ring's Loading look unverified) | No |
| 9 | Field test conditions | Not determinable; S22 covers part of the list | Yes: waive or accept the S22 record |

Fixes that are code: 3(a) and 6's reveal (one file, `RouteCardReveal.kt`); 3(b) a constants move; 6's locate glide (a
reduce-motion read at MapDataRefresh.kt:135 and a jump branch); 7's halo if chosen; 3(f) a colour move. Fixes that are
doc: §2 and §4 rows for seven animations. None of these was built, tested or proposed as a patch here.

Disclosure: I did not read `AvailabilityLocateMe.kt`, `ReCenterOnLocate.kt` or `PlottedRouteLayers.kt` in full; the
accent-layer reading rests on lines 55-77 and 241 and on `MarkerGlyphs.kt:290-312`. I did not run any test.
