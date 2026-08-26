# Understory — an M3 Expressive design system for Forager

Status: **accepted as the basis for the work**, revision 3. Reviewed by the
project owner 2026-08-25 (seven corrections, three decisions) and again on
the corrections themselves (R1 sharpened, R7 restated, one finding
reopened). Nothing here is implemented.

Read against `claude/l4c-serialized-editing-state` at `353d256`. Every
count, line number and file reference below came from reading that tree
directly. Version and API facts came from Google's release notes, the
Compose BOM mapping table, and the AndroidX sources on `androidx-main`.
**No build was run and no test was executed** — the six checks in §4S are
specified, not written.

A rendered version of this document exists as an artifact:
<https://claude.ai/code/artifact/6fbeb724-c642-45d9-ad60-2a8e8b522d55>

## What review changed

| # | Correction | Gated |
|---|---|---|
| R1 | The damping-1.0 no-overshoot claim was overstated. Corrected once toward "interruption-carried velocity," which overshot in the other direction; **now settled**: a critically damped spring can never overshoot from any point on its own path, and **retargeting** is the sole exposure. §2S. | Blocked ADR-0002 |
| R2 | Promoting map accents to *be* `tertiary`/`error` re-creates tag 08's defect class semantically. `MapPalette` now **derives from** the scheme rather than being it. | Blocked step 1 |
| R3 | "Four call sites" is evidence the change is **cheap to write**, not that it is low risk. All four are `panelMotionSpec`. Separated wherever the number appears. | Wording |
| R4 | No hardware gate existed anywhere in the order. An explicit device gate now sits between steps 4 and 5, with four named questions. | Blocks step 5 |
| R5 | `MapPaletteTest` asserted difference, not legibility. As built it asserts derivation-not-identity, theme responsiveness, and a **separation ratchet** at the measured minimum; basemap legibility is recorded in the README as a hardware question. | — |
| R9 | **Implementation finding.** The basemap has no theme variant, so deriving map colours from the ambient scheme moves the mark without moving the ground. Raised, overridden by the owner, built as specified, and recorded below with the measurement it produced. | Device gate |
| R6 | The experimental-status caveat gated the wrong step. Moved from step 2 to step 5. Symbol names corrected. | Blocks step 5 |
| R7 | Stop-and-ask on the continuous-animation budget, answered before ADR-0002 is drafted: not duration-derived, but **never enforced**, so the ADR records it as unimplemented rather than "unchanged." §3S. | Blocked ADR-0002 |
| R8 | Removing the expanded navigation rail closed a finding instead of deferring it. The question of where the six destinations live in expanded is now recorded in `map-redesign.md`. | — |

---

## Why 5S, and not a numbered plan

Forager does not have a design system that needs redecorating. It has
twelve colour roles, no type scale, no shape scale, no motion scheme, and
seven colour roles the UI reads but the theme never sets. Styling on top
of that paints over the defects rather than removing them.

5S — Sort, Set in Order, Shine, Standardize, Sustain — came out of the
Toyota Production System to remove *muda*, work that adds no value. Seven
untokenised map colours with no dark-mode variant are muda in the original
sense: every future change has to touch all seven. So are six animation
specs that exist only to be asserted by their own unit test. The five
sections below are the implementation order, not a metaphor laid over an
ordinary plan.

## The finding that reshapes the brief

An earlier draft of this design treated "remove tween" as a risky
supersession of an accepted ADR, and proposed a chrome-versus-map split to
contain the blast radius. That framing was wrong, but not for the reason
the earlier draft's low call-site count suggests. Two separate claims,
kept separate from here on (**R3**):

**The change is cheap to write.** `MotionTokens.kt` defines eight
`tween`-based `FiniteAnimationSpec`s. Exactly one — `panelMotionSpec` — is
referenced anywhere in `app/src/main` outside its own file. The other six
(`feedbackMotionSpec`, `narrativeRevealSpec`, `markerEntranceSpec`,
`selectionPulseHalfCycleSpec`, `locationIndicatorMoveSpec`,
`dataLayerOverlaySpec`) are reachable only from `MotionTokensTest.kt`. So
is every duration constant except `PANEL_MOTION_DURATION_MS` and
`LOCATION_INDICATOR_MOVE_DURATION_MS`. The production diff is four call
sites, all in `AvailabilityScreen.kt:4067–4092`.

**The change is not low risk.** All four of those call sites are
`panelMotionSpec`, and `panelMotionSpec` drives sheets, drawers and panels
— the app's primary interaction surface, and precisely what ADR-0001's
"grounded; no springy overshoot" line was written about. A four-line diff
that changes how every panel in the app moves has a small footprint and a
large behavioural blast radius. Nothing about the low count argues that
the resulting motion is right; that is what the device gate in §5S is for.

### Three reversals from the earlier draft

**REV 01 — target `material3 1.5.0-alpha26` directly, pinned outside the
BOM.** The earlier draft argued for hand-building expressive components on
1.4.0 stable and swapping later, on the grounds that the alpha line was
churning. On `androidx-main`, `VerticalFloatingToolbar`,
`FloatingActionButtonMenu`, `SplitButton` and `ButtonGroup` carry no
`@ExperimentalMaterial3ExpressiveApi` annotation; only `LoadingIndicator`
and `MaterialShapes` still do. Hand-building four stable components to
avoid an alpha they are already stable in is the churn, not the protection
against it. (Status must be re-checked against the pinned tag before the
swap — see step 5's gate, **R6**.)

**REV 02 — no chrome/map split. No tween anywhere.** The split existed to
protect the location puck from spring overshoot. The puck was never driven
by a Compose `AnimationSpec`: `SightingsMap.kt:508–520` divides
`LOCATION_INDICATOR_MOVE_DURATION_MS` by MapLibre's internal
`TRANSITION_ANIMATION_DURATION_MS = 750L` and passes the ratio to
`LocationComponentOptions.trackingAnimationDurationMultiplier`. There was
nothing there to protect, and the rest of the split protected animations
that do not exist yet.

**REV 03 — `ForagerTheme` becomes a `MaterialExpressiveTheme`.** Not a
wrapper around one; the real thing, with all five token axes supplied.
Verified signature (`MaterialTheme.kt:241`, `androidx-main`):

```kotlin
MaterialExpressiveTheme(
    colorScheme: ColorScheme? = null,
    motionScheme: MotionScheme? = null,
    shapes: Shapes? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit,
)
```

`ForagerTheme` today passes `colorScheme` and nothing else
(`Theme.kt:50–58`), which is the root cause of half the red tags below.

---

## 1S — Sort (*Seiri*): remove what is not needed

Ten red tags. Tags 09 and 10 are new to this pass.

| # | Finding | Evidence |
|---|---|---|
| 01 | **Seven colour roles are read but never set.** The theme sets 12; the UI reads 15 distinct roles. Unset and in use: `error`, `errorContainer`, `onErrorContainer`, `outline`, `surfaceContainer`, `tertiaryContainer`, `onTertiaryContainer`. They fall through to Material's baseline purple/red palette — the same class of leak `Color.kt`'s own comment documents catching once before. | `Theme.kt:9–41`; `error` read at 13 sites; `surfaceContainer` is the bottom nav's own container, `AvailabilityScreen.kt:1206` |
| 02 | **No type scale exists, and 1.5.0 doubles what is missing.** No `Typography(` anywhere in `app/src/main`; all 148 `MaterialTheme.typography` reads resolve to the stock Roboto scale. The 1.5.0 `Typography` class carries fifteen additional `*Emphasized` roles, all unclaimed. | `Theme.kt:50–58`; `Typography.kt:387–429` on `androidx-main` |
| 03 | **No shape scale exists, and the scale it would inherit now has eight steps.** No `Shapes(` either. 1.5.0's scale is `extraSmall` → `extraExtraLarge` with `largeIncreased`/`extraLargeIncreased` filling the gap Expressive's larger components need. The two deliberate shapes in the app are hard-coded `CircleShape` at the call site. | `Theme.kt:50–58`; `MapStackIconButton`, `AvailabilityScreen.kt:3728`; `Shapes.kt:84–109` on `androidx-main` |
| 04 | **Seven map colours live outside the theme, with no dark variant.** In dark theme they render identically to light. | `SightingsMap.kt:756–786` — `CONNECTOR_COLOR`, `SIGHTING_DOT_COLOR`, `AREA_MARKER_BACKGROUND_COLOR`, `PLANNED_TRIP_MARKER_COLOR`, `SEARCH_CENTER_COLOR`, `BREADCRUMB_COLOR`, `WAYPOINT_MARKER_COLOR` |
| 05 | **A palette constant imported straight into a screen.** `Bark` bypasses the colour-role indirection to build `MapIconStackButtonColor`. One import — but it is the precedent that makes the next twenty look reasonable. | `AvailabilityScreen.kt:210`, consumed at `:3401` |
| 06 | **One file holds 68 of the app's 115 composables.** 4,830 lines; the entry composable's signature spans lines 347–484 and takes **60 parameters, 45 of them `on*` callbacks**; fifteen test files point at it. Not a styling problem, but the largest single tax on the work below. Out of scope — see Open items. | `AvailabilityScreen.kt:347` |
| 07 | **A dead parameter, documented and left in.** `drawerSheetContent`'s `showCloseButton` is always false in practice — one call site, the permanent drawer, which is never "closed." Correctly out of scope for the L4c workstream; in scope for a Sort pass. | `AvailabilityScreen.kt:637–653` |
| 08 | **Two accent greens, one collision.** `MossGreen` is `primary` in dark *and* `tertiary` in both themes, so in dark theme `primary` and `tertiary` are the same colour and any component pairing them has no contrast at all. | `Theme.kt:9–41` |
| 09 | **Six of eight motion specs are dead, and a test enforces that they stay tweens.** `MotionTokensTest.kt:37–48` asserts every spec *is* a `TweenSpec`, failure message "spring-based specs can overshoot"; `:52–56` casts `panelMotionSpec` to `TweenSpec` to read its duration. The tween-only rule is mechanically pinned, not merely documented. | `MotionTokens.kt`; `MotionTokensTest.kt`; production call sites `AvailabilityScreen.kt:4067, :4068, :4085–4087, :4090–4092` |
| 10 | **Sixteen `BackHandler`s, zero predictive back.** The app targets SDK 37 and calls `enableEdgeToEdge()`, so the system's predictive-back animation is on — but every back site uses `BackHandler`, which consumes the gesture with no progress callback, so the user gets no peek at the destination anywhere. `PredictiveBackHandler` appears nowhere. | `AvailabilityScreen.kt` (16 sites), `JournalTab.kt:128`, `LogPanel.kt:116`; `MainActivity.kt:169`; `app/build.gradle.kts:123` |

**A tag-09-shaped finding outside the tag list.** `MotionPrecedence.kt` has
the same defect: `activeTiers`, `shouldClusterMarkers`,
`MAX_CONTINUOUS_ANIMATED_OBJECTS` and `DEGRADATION_ORDER` are referenced
only by `MotionPrecedence.kt` itself and `MotionPrecedenceTest.kt`. The
degradation model is specified, tested, and unwired. It is not red-tagged
because unlike the six dead specs it is not superseded by this design — it
is a real mechanism waiting for a caller. See **R7** under §3S.

**What Sort does not touch.** The map layer's raw ints (tag 04) stay raw —
`SightingsMap.kt` documents why its overlay list must remain
basemap-agnostic, and a native canvas cannot read a Compose `ColorScheme`.
The fix in 2S is a mapping object, not a rewrite of the draw path.

---

## 2S — Set in Order (*Seiton*): a place for everything

### Colour — the full role set, both themes

Two recorded decisions in `Color.kt` are honoured, not overridden: dark
surfaces stay neutral grey (the warm brown was rejected because dark mode
was asked to match Android's own convention), and `Cream` stays a tonal
step, not the background.

The two new hues are not invented — they are the map's existing
planned-trip blue and search-centre red, promoted out of `SightingsMap.kt`
into `tertiary` and `error`.

**R2 — the promotion is a source, not an identity.** The earlier revision
claimed this closed tags 04 and 08 in one move. It closes 08 and would
have introduced a coupling tag 04 did not have: both colours are *still
drawn on the map*, so binding them to roles would put the search-centre pin
and all thirteen `error` read sites on one hue, and bind the planned-trip
marker to whatever `tertiary` becomes. Retuning `error` for text contrast
would then move the map. That is tag 08's defect — two things resolving to
one colour — in its semantic form.

So the corrected rule, and the thing `MapPalette` exists to enforce:

- **`MapPalette` derives from the scheme; it is not the scheme.** It takes
  the resolved `ColorScheme` and returns seven `Int`s by a named
  derivation per colour, never by returning a role's value verbatim.
- **The map blue derives from `tertiary`** — same hue family, its own tone
  and saturation — rather than equalling it.
- **The search centre gets its own derivation** and is not bound to `error`
  at all. A pin that means "you searched here" and text that means
  "something failed" have no reason to move together.
- The promotion still supplies the *hues*. What is dropped is the identity.

With that, the promotion closes tag 08, `MapPalette` closes tag 04, and
neither re-creates the other.

#### R9 — the objection to theme-derived map colours, raised and overridden

Recorded because it was raised before implementation, decided against, and
then partly borne out by measurement. It is not a request to revisit; it is
the note that makes the device-gate result interpretable either way.

**The objection.** `Basemap` offers USGS Topo, USGS Imagery Topo,
OpenTopoMap and OSM Standard. None of them changes with the device theme,
and nothing in the map layer reads `isSystemInDarkTheme`. Markers need
contrast against *raster tiles*, so varying them by theme changes the mark
without changing the ground. The axis that genuinely varies is the user's
**basemap choice** — pale tan topo against dark aerial imagery — which is a
within-theme switch `MapPalette` does not model.

**The decision.** Build it as specified: derive from the ambient scheme so
light and dark differ. Owner decision, 2026-08-25, after the objection was
put.

**What the implementation then measured.** Deriving from the ambient scheme
pushes two markers together in dark theme that the light scheme keeps
apart. `connector` derives from `secondary`, which is the deepened
`MushroomDeep` in light but the lightened `MushroomLight` in dark; the
waypoint pin is a fixed amber. In Oklab they sit **0.044 apart in dark
against 0.12+ in light** — both warm oranges, at the point where telling a
route connector from a waypoint pin is the thing the colours exist to do.

That is not a reason to reverse the decision, and it has not been treated
as one. It is a concrete thing to look at on hardware, and it is now
`MapPaletteTest`'s separation floor, so nothing can quietly make it worse.

**If the gate says it reads badly**, the fix is one line: build the palette
from a fixed reference scheme rather than the ambient one. `MapPalette.from`
already takes the scheme as a parameter, so nothing else changes.

| Role | Light | Dark | Note |
|---|---|---|---|
| `primary` / `onPrimary` | `#2E5339` / `#FBF8F1` | `#A8CBA0` / `#16301D` | Dark primary lightened so it no longer equals tertiary |
| `primaryContainer` / on— | `#D9E8D2` / `#16301D` | `#3D5A40` / `#D9E8D2` | Already exist — unchanged |
| `secondary` / `onSecondary` | `#A2612C` / `#FFFFFF` | `#E5A76B` / `#452507` | Mushroom deepened for light so it can carry text |
| `secondaryContainer` / on— | `#F6DFC8` / `#3A2008` | `#6B421A` / `#F6DFC8` | New |
| `tertiary` / `onTertiary` | `#3B6EA5` / `#FFFFFF` | `#A8C4E4` / `#12283F` | Closes tag 08. Hue sourced from the map; the map derives from this rather than reading it |
| `tertiaryContainer` / on— | `#D7E3F2` / `#12283F` | `#2B4763` / `#D7E3F2` | Closes tag 01 |
| `error` / `onError` | `#B33B3B` / `#FFFFFF` | `#E89A9A` / `#4A1212` | Closes tag 01 — 13 read sites were on baseline red. Tuned for text contrast only; the search-centre pin is **not** bound to it |
| `errorContainer` / on— | `#F5DCDC` / `#3A0F0F` | `#6B2222` / `#F5DCDC` | Closes tag 01 |
| `surface` / `onSurface` | `#FAF8F3` / `#3B2E24` | `#1B1B1B` / `#EDE3D0` | Unchanged |
| `surfaceVariant` / on— | `#EDE3D0` / `#5B5347` | `#2C2C2C` / `#CFC9BE` | Unchanged |
| `surfaceContainerLowest`→`Highest` | `#FFFFFF` → `#E2DCCC` | `#101010` → `#373737` | Closes tag 01 — the bottom nav reads the middle step |
| `outline` / `outlineVariant` | `#8C8577` / `#D5CEBF` | `#8E8E8E` / `#444444` | Closes tag 01 |

### Type — Roboto Flex, plus the fifteen emphasized roles

Expressive's typographic move is emphasis through weight and width, not a
new family — and on the 1.5.0 line that move has a real API: every role
has an `*Emphasized` twin. Roboto Flex is the carrier: one variable font,
per-role weight and width axes, already the platform default's sibling, so
no new font asset ships in the APK.

**Rejected alternative:** one `Typography` with heavier weights baked into
the base roles. That loses the pairing. Expressive's argument is that a
screen reads better when *one* element is emphasized against its
neighbours in the same role, not when everything gets heavier together. A
separate `titleLargeEmphasized` is what makes "this card's title, not the
other five" expressible at all.

**The one monospace exception** earns its place by fixing a real bug. The
compass strip renders heading, elevation, MGRS grid reference and decimal
lat/long on one line, and the file's own comments record two hardware
rounds fighting it — a two-line wrap, then an ellipsis fix, then a
touch-interception regression caused by the first attempt at a fix.
Proportional digits are part of that problem: the strip's width changes as
the digits change. Roboto Mono with tabular figures, on the coordinate
segment only, makes the strip a fixed width for a given format.

### Shape — rounder than Material's default, for a field reason

1.5.0's stock scale is 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48. Understory
raises the first five steps and keeps the top three:

| Step | Understory | M3 stock |
|---|---|---|
| `extraSmall` | 6dp | 4dp |
| `small` | 10dp | 8dp |
| `medium` | 14dp | 12dp |
| `large` | 20dp | 16dp |
| `largeIncreased` | 24dp | 20dp |
| `extraLarge` | 28dp | 28dp |
| `extraLargeIncreased` | 32dp | 32dp |
| `extraExtraLarge` | 48dp | 48dp |

The expressive read is the visible half of the reason; the field half is
that larger radii come with larger, more separable touch targets, and this
app is operated with cold hands and gloves. Whether that holds with gloves
is a device-gate question (§5S), not an asserted fact.

### Spacing — promote what already exists

`AvailabilityScreen.kt:304` already defines a private four-step `Spacing`
object with a documented rationale per step. It needs moving out of a
private scope in one screen file and into the theme, plus two larger steps
the current set improvises around: `xl` = 24dp (sheet and dialog internal
padding) and `xxl` = 32dp (empty-state and first-run breathing room).

### The UI's own places

Seiton applies to the interface, not just the token file. The organising
rule is point-of-use: the more often something is reached for, the closer
it sits to where the thumb already is.

| Place | What it owns | Verdict |
|---|---|---|
| Bottom nav (six destinations) | List · Maps · Seasonal · Journal · Album · Settings | **Correct** — every top-level destination, one tap |
| Map icon stack (fixed at five) | Recentre, layers, search, fullscreen, add | **Correct** — map-scoped verbs, on the map |
| Compass strip | Heading, elevation, coordinates, record toggle, return-to-vehicle | **Watch** — read-mostly, but carries two controls |
| Compact drawer | Search only — location, radius, month, areas layer, trip planning | **Watch** — set far less than once per search, but search is unreachable from five of six tabs (already flagged in `map-redesign.md`) |
| Permanent drawer (medium + expanded) | Same search controls, always visible, 360dp | **Known defect, deferred** — on a 600dp window that is 60% of the screen given to controls set once a session. Not approved for build; recorded in `map-redesign.md`. See Decisions |

### Motion — the fifth token axis

Motion becomes a theme token like colour and shape. `MaterialTheme.motionScheme`
supplies six specs; `MotionTokens.kt` stops defining animation curves and
becomes a thin map from this app's named categories onto those six, so
call sites keep referring to *what is moving* rather than to a raw spring.

Values read from `ExpressiveMotionTokens.kt` on `androidx-main`, with
`StandardMotionTokens.kt` alongside for the reversibility record:

| Spec | Expressive ζ / k | Standard ζ / k | Overshoot on a step from rest |
|---|---|---|---|
| `fastSpatialSpec` | 0.6 / 800 | 0.9 / 1400 | yes — largest |
| `defaultSpatialSpec` | 0.8 / 380 | 0.9 / 700 | yes — mild |
| `slowSpatialSpec` | 0.8 / 200 | 0.9 / 300 | yes — mild |
| `fastEffectsSpec` | 1.0 / 3800 | 1.0 / 3800 | none |
| `defaultEffectsSpec` | 1.0 / 1600 | 1.0 / 1600 | none |
| `slowEffectsSpec` | 1.0 / 800 | 1.0 / 800 | none |

The two schemes differ **only in the spatial rows**. Effects specs are
identical, which is why the `standard()` fallback in Decisions is a
one-parameter substitution and not a re-derivation.

#### R1 — what the effects specs actually guarantee

The first revision claimed effects specs are "mathematically incapable of
overshoot." That is overstated. The second revision corrected it toward
"interruption-carried velocity," which is overstated in the other
direction. Review supplied the sharp version:

Critical damping (ζ = 1) gives `x(t) = (x₀ + (v₀ + ωx₀)t)e^(−ωt)` with
`ω = √k` at Compose's unit mass. That crosses zero — overshoots — only when
`v₀` opposes `x₀` **and** `|v₀| > ω|x₀|`. Now evaluate that ratio along the
system's own from-rest trajectory: `v(t) = −x₀ω²t·e^(−ωt)` and
`x(t) = x₀(1+ωt)e^(−ωt)`, so

```
|v| / (ω|x|)  =  ωt / (1 + ωt)
```

which is **strictly below 1 for every finite t**, approaching it only
asymptotically. A critically damped spring therefore can never overshoot
its own target from any point on its own path. **Interrupting an in-flight
effects animation and letting it continue to the same target is provably
safe, always.** Interruption is not the hazard.

**Retargeting is.** `x₀` is the distance to the *new* target. Retarget to
something nearby in the direction of travel and `|x₀|` collapses while
`|v₀|` does not, so the ratio crosses 1 easily. The hazard is a moving
destination, not a moving object.

That narrows the clamp argument further than the second revision had it.
Clamping to `[0, 1]` rescues a retarget whose destination *is* 0 or 1. It
does nothing for an overlay fading in that gets retargeted to a partial
opacity — overshoot past that value is reachable and visible.

**Checked against the code, since the ADR was about to lean on the clamp.**
Today the app has no `Animatable`, no `animateFloatAsState`, no
`animateColorAsState` and no `updateTransition` anywhere in
`app/src/main`. The only animated alpha is `AnimatedVisibility`'s fade at
`AvailabilityScreen.kt:4065` and `:4083`, and both take `fadeIn`/`fadeOut`
with default `initialAlpha`/`targetAlpha`, so both animate between 0 and 1.
The five intermediate alpha values in the codebase (0.18, 0.32, 0.4, 0.55,
0.78 — including the add-tile scrim's 0.32) are static colour alphas
composited *under* the animated layer alpha, not animation targets. **The
clamp holds for every animated alpha in the app as it stands.**

But that is a property of the call sites, not of the spec.
`fadeIn(initialAlpha = …)` and `fadeOut(targetAlpha = …)` accept non-bound
values as parameters, and the design's data-layer-overlay row is
aspirational — `dataLayerOverlaySpec` has no caller, so the first overlay
anyone writes is the first chance to target 0.55 instead of 1. Five
plausible intermediate targets are already sitting in the file.

So the property gets an assertion rather than an assumption:
`verify-design-tokens.sh` gains a check that no effects-spec animation
names a non-bound `initialAlpha`/`targetAlpha` (§4S). **ADR-0002 must not
present the clamp as the backstop** — it records that effects specs cannot
overshoot under interruption at all, that retargeting is the only exposure,
that today every animated alpha targets a bound, and that a check now holds
that true rather than trusting it.

The conclusion survives, and is stronger than the version it replaces: this
argument licenses dropping the tween-only rule **for alpha and colour**. It
says nothing about panels, and ADR-0002 must not use it as though it did.

---

## 3S — Shine (*Seiso*): clean, and inspect by cleaning

### The motion replacement, in full

| Surface | Was | Becomes | Why |
|---|---|---|---|
| Buttons, chips, FAB, icon stack | tween 200 ease-out | `fastSpatialSpec` | Press feedback wants the overshoot. Provisional pending the device gate |
| Nav bar, nav rail, tab switch | never specced | `defaultSpatialSpec` | Chrome; no positional truth to distort |
| Sheets, drawers, dialogs, panels | tween 300 ease-out | `slowSpatialSpec` | The only tween with production call sites. **Accepts mild overshoot** — a taste call, not a dissolved objection. Provisional pending the device gate |
| Marker entrance & clustering | tween 250 + 40ms stagger | `defaultSpatialSpec` + 40ms stagger | Scale and bounds change, so spatial. The stagger is a delay, not a curve |
| Selection pulse | tween 1600 linear loop | `slowSpatialSpec`, 0.96–1.04 | Amplitude bounds unchanged |
| Data-layer overlays | tween 450 ease-out | `defaultEffectsSpec` | Alpha only. This is the row the R1 argument actually licenses |
| Route reveal & recalculation | tween 400ms/km, 500 morph | `slowEffectsSpec` + 400ms/km | The morph is a spring; the per-km rate stays a rate |
| Location puck | 350ms scalar | **stays a duration** | Not a Compose animation — see below |

**Unsupported, stated as unsupported.** The location puck cannot take a
spring. MapLibre's `trackingAnimationDurationMultiplier` accepts a scalar
multiplier on a fixed internal duration; there is no interpolator to
supply. The constant survives the tween removal — but it was never a
`TweenSpec`, and `locationIndicatorMoveSpec`, the tween that *looks* like
it drives the puck, is deleted with the other five dead specs. Recorded as
a capability limit, not designed around. If a future MapLibre version
exposes an interpolator, this row changes and the ADR gets an amendment.

### R7 — the continuous-animation budget, answered before drafting

The question was whether the 8–12 budget's accounting assumes bounded
durations, since a tween has a known end but a spring settles
asymptotically and predictive back has no duration at all.

**It does not.** `MotionPrecedence.kt` has no Compose or Android imports
and contains no duration of any kind. `activeTiers()` takes
`Map<DegradationTier, Int>` — a **count** of objects currently wanting to
animate in each tier — and drops tiers in `DEGRADATION_ORDER` until the
remaining counts sum to at most `MAX_CONTINUOUS_ANIMATED_OBJECTS = 12`.
The accounting is over object counts, not over time.

**But the budget has no production caller, which makes it more than a
second instance of tag 09.** `activeTiers`, `shouldClusterMarkers`,
`MAX_CONTINUOUS_ANIMATED_OBJECTS` and `DEGRADATION_ORDER` appear only in
`MotionPrecedence.kt` and `MotionPrecedenceTest.kt`. Nothing supplies the
counts. **The 8–12 budget has therefore never been enforced.** Listing it
under "keeps unchanged" would preserve a property that has never operated,
and a future reader would take that line as evidence the budget is live.

So ADR-0002 says it plainly instead:

> The 8–12 continuous-animation budget is a stated policy with no
> enforcement path — unchanged in intent, unimplemented in fact. The
> spring-aware counting constraint below is a condition on **first
> implementation**, not on a modification.

And the constraint itself: **the count must come from a spring-aware
running signal (e.g. `Animatable.isRunning`), never from a timer or an
elapsed duration.** With a tween, "has it finished" and "has its duration
elapsed" are the same question; with a spring they are not, and a
timer-derived counter would under-count long tails and over-count settled
objects. That is where duration-versus-asymptote actually bites, and it
bites whoever writes the first caller.

#### The asymmetry, stated rather than left incidental

This design deletes six dead animation specs (tag 09) and keeps one dead
policy file (`MotionPrecedence.kt`). That is deliberate, not an oversight
about which unused code is worth keeping:

- **A curve carries no decision.** `markerEntranceSpec` being
  `tween(250, EaseOut)` records nothing a reader needs; it is a value, and
  an unused one is just a value nobody chose. Deleting it loses nothing.
- **A precedence order does.** `DEGRADATION_ORDER`, the 12-object ceiling
  and the never-degrade rule on the location indicator are ADR-0001's
  accepted decisions in executable form, with a test pinning them. Deleting
  them would discard the decision along with the unused code.

The rule this implies, for anyone applying the same Sort pass later:
**dead code that encodes a decision gets documented as unenforced; dead
code that encodes only a value gets removed.**

Predictive back sits outside the budget entirely: motion-spec §3's budget
is scoped to simultaneously animated objects *on the map*, and a
gesture-driven back animation is chrome and singular.

### ADR-0002, superseding part of ADR-0001

ADR-0001 is accepted, and `docs/motion-spec.md` §2 says "Panels and
navigation — ease-out, grounded; no springy overshoot" in as many words.
That cannot be quietly overwritten by a styling pass. It gets a
supersession with its rejected alternatives written down, never an edit to
the original.

**Approved to write. Not approved to start step 4.** It cannot be drafted
until R1 is corrected (done, above) and R7 is answered (done, above), and
it must record the substance accurately rather than the convenient
version:

- **Changes:** §2's "no springy overshoot" line for panels and navigation,
  and the tween-only rule in `MotionTokens.kt`'s class comment.
- **Records as a taste call, not a dissolved objection.** Panels and sheets
  move to `slowSpatialSpec` at ζ = 0.8, which *does* overshoot. This is a
  decision to **accept mild overshoot on panels**, not a finding that
  ADR-0001's concern was misplaced. The R1 effects-spec argument justifies
  dropping the tween-only rule for alpha and colour and nothing more. An
  ADR that presents a taste call as a dissolved objection misrecords what
  was decided, and the record is the whole point of writing one.
- **Marks the panel damping value provisional** pending the device gate in
  §5S, and states the amendment path explicitly rather than leaving it
  implicit: if the gate finds the drawer spring reads as sloppy, the
  amendment is a spec substitution on the panel row (to `defaultSpatialSpec`,
  or to the `standard()` scheme's ζ = 0.9 equivalent) recorded as an
  amendment to this ADR, not a new ADR and not a silent edit.
- **Records `expressive()` as a reversible one-line parameter,** with the
  `standard()` damping values written down alongside (§2S table) so
  switching later is a substitution rather than a re-derivation.
- **Unchanged:** the precedence order (legibility → performance → calm),
  the four-tier degradation order, and the whole Reduce Motion mapping
  table. `MotionPrecedence.kt` needs no change.
- **Restates rather than preserves, per R7:** the 8–12 budget is recorded
  as a stated policy with no enforcement path — unchanged in intent,
  unimplemented in fact — so no reader mistakes the "unchanged" line for
  evidence it is live. The spring-aware counting constraint is a condition
  on first implementation, not on a modification.
- **Records, per R1:** effects specs cannot overshoot under interruption at
  all; retargeting is the only exposure; every animated alpha in the app
  today targets a bound; and a check now holds that true. The clamp is
  **not** presented as the backstop.
- **Records the Sort asymmetry:** six dead curves deleted, one dead policy
  file kept and marked unenforced, because a curve carries no decision and
  a precedence order does.
- **Adds:** a Reduce Motion mapping for springs. `ReducedMotionTreatment`
  already maps every treatment to a still-visible equivalent rather than
  to nothing; springs map the same way, to `snap()` or a cross-fade, never
  to a silently dropped state change.
- **Unchanged:** the scope boundary. No confidence score, no candidate
  list, no per-observation harm assessment enters the motion layer.
- **Rejected alternative:** the chrome-versus-map split. It protected a
  puck that was never spring-driven, and would have left two motion
  vocabularies in a codebase that currently uses one call site's worth of
  either.
- **Rejected alternative:** keeping tween-only for panels while adopting
  springs everywhere else. Coherent, and it is what a strict reading of
  ADR-0001 implies — but it makes the app's primary interaction surface
  the one thing that does not respond to interruption with velocity
  continuity, which is the property springs exist for. Rejected as a
  taste call, on the record, subject to the device gate.

### The three width classes

One token layer everywhere. Layout diverges only where the window changes
what is reachable one-handed.

- **Compact (< 600dp) — finish pass.** The full-bleed map, six-tab bottom
  nav, right-edge icon stack and full-width compass strip all stay; two
  hardware rounds have already tuned them. What changes is finish: the
  icon stack becomes a real `VerticalFloatingToolbar`; the three-way add
  dialog becomes a `FloatingActionButtonMenu`; the compass strip gets
  tabular figures and a token-derived scrim instead of a raw opacity; the
  category filter becomes a `ButtonGroup` with connected shape morphing.
- **Medium (600–840dp) — deferred, not built.** The finding stands and is
  recorded in `map-redesign.md` as a known defect. See Decisions.
- **Expanded (≥ 840dp) — tokens only.** The `PermanentNavigationDrawer` +
  `CombinedResultsPane` layout is kept structurally intact, exactly as
  `map-redesign.md` requires. Unchanged by the deferral above.

### Predictive back

Tag 10's fix is a swap at sites that already exist. Every `BackHandler`
guarding a *dismissible* surface — drawer, fullscreen map, offline-maps
panel, crash-log panel, each picker phase — becomes a
`PredictiveBackHandler` whose progress flow drives the same expressive
spec that opens the surface. Back-to-exit-a-tab handlers, which navigate
rather than dismiss, stay as they are: there is nothing to peek at.

A drawer that springs shut on `slowSpatialSpec` and a drawer that follows
the user's back-swipe are the same animation driven from two sources,
which is why this belongs after the motion step and not in its own.
Gesture-driven progress is not headless-testable by definition, which is
why step 6 follows the device gate rather than preceding it.

### The inspection that Shine forces

Three times now this project has shipped a layout over the map that
silently swallowed the map's own touches — the compass strip, then the
return-to-vehicle row, then a `horizontalScroll` modifier that installed a
pointer handler at zero scroll range. All three were caught green-to-failing
by a Robolectric test driving the real screen's long-press.

Every new element in this design that sits over the map is in that blast
radius: the floating toolbar's pill container, the FAB menu's expanded
scrim, and the compass strip's new container. Each needs a long-press test
before it is called done. Visual review is precisely what missed this
twice.

### Components — one stage, on 1.5.0-alpha26

| Where | Component | Status on `androidx-main` | Replaces |
|---|---|---|---|
| Map icon stack | `VerticalFloatingToolbar` | unannotated — `FloatingToolbar.kt:345`, `:439` | `MapIconStack`, `AvailabilityScreen.kt:3678` |
| Add action | `FloatingActionButtonMenu` | unannotated | `ThreeWayActionDialog` |
| Category filter | `ButtonGroup` | unannotated — `ButtonGroup.kt:129` | A plain `Row` of `FilterChip` |
| Save / Save & close | `SplitButton` | unannotated | Two adjacent buttons in the Journal editor |
| Search loading | `LoadingIndicator` | **`@ExperimentalMaterial3ExpressiveApi`** | Nothing yet; keep `CircularProgressIndicator` |
| Marker shapes | `MaterialShapes` | **`@ExperimentalMaterial3ExpressiveApi`** | Nothing yet; the waypoint pin stays a hand-drawn `Path` |
| Theme entry point | `MaterialExpressiveTheme` | unannotated | `MaterialTheme(colorScheme)`, `Theme.kt:50` |

**R6 — two different caveats, two different steps.** The earlier revision
attached both to step 2. They gate different things:

- **Step 2 needs the resolved version.** Compose BOM 2026.08.00 resolves
  material3 to 1.4.0; `libs.versions.toml` declares `androidx-material3`
  with no `version.ref`, so the pin is a one-line override on that entry,
  which wins over the BOM for that artifact and leaves every other Compose
  artifact BOM-managed. Confirm with `./gradlew :app:dependencies`, which
  is the only authority on what actually resolves.
- **Step 5 needs the annotation status in the pinned tag.** The statuses
  above were read from `androidx-main`, which is not the `1.5.0-alpha26`
  tree. Before the component swap, re-check each symbol in the resolved
  artifact — `VerticalFloatingToolbar` and `ButtonGroup` in particular,
  since those two carry the compact finish pass.

Symbol names were verified against the sources rather than inferred from
file names: `VerticalFloatingToolbar` has two `public fun` overloads at
`FloatingToolbar.kt:345` and `:439`, neither annotated; `ButtonGroup` is a
single `public fun` at `ButtonGroup.kt:129`, unannotated. "FloatingToolbar"
is the file and the component family, not a callable symbol, and earlier
prose that used it there was wrong.

---

## 4S — Standardize (*Seiketsu*): make the cleaned state the normal state

Six checks. Five headless unit tests, one shell script following the
precedent `scripts/verify-codeowners-placeholders.sh` already set. All six
fail on the code as it stands today, which is the point.

| Check | Asserts | Catches |
|---|---|---|
| `ThemeCompletenessTest` | Every colour role read anywhere in `ui/` is explicitly set in both `LightColors` and `DarkColors`, and no role equals its Material baseline default | Tag 01, and its recurrence — which has already happened once |
| `ThemeContrastTest` | Every on/container pair clears WCAG AA at its intended size; pure Kotlin, no device | A token edit that quietly drops text below legible outdoors |
| `MapPaletteTest` | See R5 below — **not** mere light/dark difference | Tag 04, and the coupling R2 removes |
| `ExpressiveThemeTest` | `ForagerTheme` supplies non-default `Typography`, `Shapes` and `MotionScheme`, and the scheme is `expressive()` not `standard()` | Tags 02, 03, and the motion scheme silently reverting |
| `MotionTokensTest` (inverted) | No exposed spec is a `TweenSpec`; each maps to the `MotionScheme` spec its category calls for; effects-category specs have `dampingRatio >= 1.0` | Tag 09, and a spring with overshoot landing on an overlay's alpha |
| `verify-design-tokens.sh` | No `Color(0x` literal outside `ui/theme/`; no palette constant imported outside the theme package; no `tween(` in `ui/`; **no effects-spec animation naming a non-bound `initialAlpha`/`targetAlpha`** | Tag 05, the next twenty like it, tween creeping back one call site at a time, and (per R1) the first overlay that retargets alpha to an intermediate value where the clamp cannot save it |

### R5 — `MapPaletteTest` must not let non-equality stand in for legibility

The earlier revision had it assert that the seven ints differ between light
and dark. That passes if both are illegible. And the map colours sit
outside `ThemeContrastTest` by construction: they are drawn over a basemap
raster, not over a theme surface role, so contrast-against-surface does not
reach them. That would leave the one colour set with no dark variant today
as the one colour set with no contrast assertion after the fix.

`MapPaletteTest` asserts three things instead:

1. **Derivation, not identity** (R2). Each of the seven ints traces to a
   named derivation from a named role, and **no returned int equals the
   role's own value**. This is the check that would fail if someone
   "simplified" `MapPalette` back into returning `colorScheme.error`.
2. **Contrast against a stated basemap luminance range**, not against a
   surface role. The range is a **provisional constant** — same status and
   same treatment as `MARKER_CLUSTERING_THRESHOLD`, declared as provisional
   in the ADR's constants section, not presented as measured.
3. Light and dark differ — kept, but demoted to a necessary condition
   rather than the property under test.

Because assertion 2's *input* is unvalidated, map legibility also goes into
the README's "Not yet verified" section as an open hardware question. The
test bounds the regression; it does not establish the property.

### The standing rule this design adds

`CLAUDE.md` already carries the touch-interception pitfall. This design
promotes it from a warning into an entry condition.

1. **Any composable drawn over the map ships with a long-press test** — a
   Robolectric test driving the real screen's long-press through the fake
   map slot, not a screenshot.
2. **No `Surface` over the map.** A `Box` with a `background()` modifier
   does not intercept pointer input; a `Surface` does, even with no
   `onClick`. Already documented inside `CompassElevationStripContent`;
   this makes it a rule.
3. **No scroll modifier over the map, at any scroll range.**
   `horizontalScroll` installs a live pointer handler even with nothing to
   scroll. Use `weight(1f, fill = false)` with `TextOverflow.Ellipsis`.
4. **Every container over the map is width-bounded** with
   `Modifier.width(IntrinsicSize.Max)` — unless full width is the
   deliberate point, as it now is for the compass strip.

---

## 5S — Sustain (*Shitsuke*): hold the standard without being told

Sustain is the step that fails. The failure mode is documented in this
repo's own history: a Phase 1 audit that was run, never committed, and
lost outright.

### Implementation order

| # | Step | Gate |
|---|---|---|
| 1 | Fill the seven unset colour roles; add `ThemeCompletenessTest` and `ThemeContrastTest` | R2 landed (derivation-not-identity is settled). Ships alone on 1.4.0 |
| 2 | Pin material3 to `1.5.0-alpha26`; convert `ForagerTheme` to `MaterialExpressiveTheme` with `Typography`, `Shapes` and `MotionScheme.expressive()`; add `ExpressiveThemeTest` | Step 1, **and** `./gradlew :app:dependencies` confirms the resolved version |
| 3 | Extract `MapPalette` (deriving, per R2); move `Spacing` into the theme; add `verify-design-tokens.sh` and `MapPaletteTest` (per R5) | Step 1 — independent of step 2 |
| 4 | Write ADR-0002; delete the six dead specs; rewrite `MotionTokens.kt` onto the motion scheme; invert `MotionTokensTest` by hand; update motion-spec §2 | Step 2, **R1 corrected**, **R7 answered**, and ADR-0002 written |
| **G** | **DEVICE GATE — owner-run on hardware.** Four named questions, below. No general "have a look." | Step 4 |
| 5 | Compact finish pass — floating toolbar, FAB menu, button group, split button, compass-strip figures | Gate G passed, **and** R6's annotation re-check against the pinned tag |
| 6 | Swap `BackHandler` → `PredictiveBackHandler` at the dismissible surfaces | Step 5 |
| — | Medium-window navigation rail | **Deferred, not sequenced.** See Decisions |

### The phase after this one: layout

Agreed 2026-08-26. Recorded here rather than left as an intention, because
this document's own gap is what prompted it.

**What this phase deliberately does not do.** Steps 1–6 are a token, motion
and component pass over an arrangement they treat as already correct. Step 5
is called a finish pass and says so in as many words: the full-bleed map, the
six-tab bottom nav, the right-edge icon stack and the full-width compass strip
all stay, and what changes is which component draws them. Nothing in the
sequence moves anything. The only structural change ever proposed was the
medium-window rail, and that is a named deferral.

That is a defensible scope — two hardware rounds tuned compact's arrangement —
but it is narrower than "changing the design entirely", and the difference
should be visible rather than discovered.

**Why layout comes after and not before**, which is the part worth arguing:

1. **Otherwise the finish pass is done twice.** Step 5 swaps hand-built
   components into existing positions. Move the positions afterward and the
   swap is redone against a layout that no longer exists.
2. **Gate G is what produces the evidence.** Nobody has yet seen this app with
   a complete token layer on hardware. Redesigning an arrangement before
   looking at its tuned form is guessing at which parts are actually wrong —
   and `map-redesign.md` already records one arrangement changed on reasoning
   and corrected twice on hardware.
3. **The deferred items are already layout-shaped.** Three findings have
   accumulated that belong to this phase and to no other: the 360dp drawer on
   a 600dp window; the open question of where `EXPANDED`'s six destinations
   live; and `map-redesign.md`'s own note that search is reachable only from
   the Maps tab. None is a token problem. All three are waiting for a phase
   that has somewhere to put them.

**The prerequisite, named now rather than hit later.** Tag 06 stops being
deferrable the moment layout is the work. `AvailabilityScreen.kt` is 4,830
lines holding 68 of the app's 115 composables behind a 60-parameter entry
point (45 of them callbacks), with fifteen test files pointed at it. Steps 1–6 barely disturb it —
they change colours and specs and swap components in place — which is exactly
why tag 06 could be named and left alone here. A layout rearrangement is the
opposite: it is precisely the work that file makes expensive, and every change
in it pays the tax.

So the layout phase either opens with that split or accepts the cost knowingly.
That is a scoping decision for whoever writes it, not something this document
should pre-empt — but it should not be a surprise, and it is the reason tag 06
is recorded as "belongs in its own workstream" rather than "not worth doing".

### Gate G — the device gate (R4)

Open items has always said the outdoor questions are not headless. The
earlier revision then ran steps 4 through 6 with no device check between
them, which was a real hole: this codebase has had three touch-interception
regressions over the map and two hardware rounds on the compass strip; step
4 changes how every panel moves; step 6 adds gesture-driven animation that
is not headless-testable by definition.

Owner-run, on a physical phone. Step 5 does not start before it. The
questions are named so the gate has a pass condition rather than an
impression:

1. **Does damping 0.6 press feedback feel loose while walking?**
   (`fastSpatialSpec` on buttons, chips, FAB, icon stack.)
2. **Does the drawer spring read as sloppy at `slowSpatialSpec`?**
   (ζ = 0.8, the accepted-overshoot taste call.)
3. **Does predictive-back progress track the gesture?**
4. **Does expressive damping stay inside ADR-0001's precedence order, or
   does `standard()` serve the calm requirement better?**
5. **In dark theme, can you still tell a route connector from a waypoint
   pin?** (R9. They are 0.044 apart in Oklab there against 0.12+ in light.
   Check on both a topo and an imagery basemap, since the ground changes
   between them and the markers do not.)

Question 4 is the one that can reverse a decision rather than tune a value.
Its answer is a one-line parameter change either way — the `standard()`
values are recorded in §2S so the switch is a substitution.

Outcomes route as amendments to ADR-0002, per its stated amendment path,
not as new ADRs and not as silent edits.

### Before any of it — the base branch

**This document sits on `claude/l4c-serialized-editing-state`, the tree it
was read against.** It is cut from `353d256`, so every file reference, line
number and count below is against the code directly beneath it rather than
against a base that does not have that code yet.

That was not true of the first attempt. The work was originally branched
from `main` and opened as PR #43 against `main` — accurate in content,
wrong in base: `main` is 9 commits behind L4c, so the line numbers cited
throughout pointed at code `main` did not contain. L4c is itself an open
PR (#42) into `main` and is not an ancestor of it, so the fix was to replay
the work onto L4c rather than to merge the two. PR #43 is superseded by the
PR for this branch.

`CLAUDE.md` records why this is not pedantry: PR #26 and the Phase 1
branch each independently declared `ForagerDatabase.version = 5` with
different migration bodies from the same base — a collision that merges
cleanly and produces a broken result. A design-token change carries the
same hazard class: two branches can each add a colour role, merge without
conflict, and leave one silently overwriting the other. Cut from L4c, and
check the token file against its current state rather than the state you
started from.

---

## Decisions

Recorded 2026-08-25 by the project owner.

### ADR-0002 — approved to write, not approved to start step 4

Drafting is unblocked now that R1 is corrected and R7 is answered. Step 4
does not start until the ADR is written. Its required content is specified
in §3S above; the two non-negotiable parts are that it records the panel
change as **a decision to accept mild overshoot** rather than as a
dissolved objection, and that it states the amendment path for the
provisional panel damping value explicitly.

### Medium-window navigation rail — not approved

The finding is correct and is recorded. It is not built now, for three
reasons:

1. It is the only step that contradicts a recorded scope decision.
2. It is the only structural layout change in a document that is otherwise
   a token and motion pass.
3. It lands in the window class that cannot be verified here — the project
   is built and tested on a phone with no emulator — which makes it
   simultaneously the change that most needs hardware and the one least
   able to get it.

It is recorded in `docs/plans/map-redesign.md` as a known defect with the
360dp-of-a-600dp-window rationale attached, so the compact-only scope stays
a deliberate decision rather than an oversight a future reader repeats or
re-discovers. It is a named deferral, not a sequenced step. Expanded keeps
its tokens-only treatment as written.

### `expressive()` over `standard()` — provisional, and now a question

Expressive goes to hardware; that is what Gate G question 4 is for. It is
recorded in ADR-0002 as a reversible one-line parameter, with the
`standard()` damping values written down alongside it (§2S) so switching
later is a substitution rather than a re-derivation.

---

## Open items

**Not verifiable headlessly — everything about how this reads outdoors.**
Contrast ratios can be asserted in a unit test. Whether a 20dp radius helps
with gloves, whether tabular figures actually stop the compass strip
wrapping on a real phone, and whether damping-0.6 press feedback feels
right or loose while walking are hardware questions. Gate G now names four
of them; the rest belong in the README's "Not yet verified" section.

**Map legibility has no headless assertion that establishes it** (R5).
`MapPaletteTest` bounds regressions against a provisional basemap
luminance range; it does not establish the property. Added to the README's
not-yet-verified section.

**The budget is unwired** (R7). `MotionPrecedence.kt` is specified, tested
and has no production caller. Not this design's job to wire, but ADR-0002
records the spring-aware-counting constraint so whoever does gets it right.

**Left undone — the 4,830-line screen file.** Tag 06 is real and no fix is
proposed. Splitting it is a refactor with its own risk profile and fifteen
test files pointed at it. It belongs in its own workstream, not smuggled
into a design pass. Confirmed out of scope at review.

**Left undone — no screen-by-screen redesign.** This document specifies a
token layer, a motion system, a component mapping and an implementation
order. It does not draw every screen. Compact's structure is deliberately
kept; the medium-window layout is deferred.

**Not run — no build, no test run.** Every count, line number and API
status above is a static read. The six checks in 4S are specified, not
written.

**Known imprecision in the 5S frame.** Shine is carrying more than
inspection-through-cleaning — it holds the motion replacement, the width
classes and the component mapping as well as the touch-interception
inspection. Noted at review and deliberately not reorganised: the churn
would cost more than the imprecision does.
