# Understory — an M3 Expressive design system for Forager

Status: **proposal**. Nothing in here is implemented. Two steps are
explicitly blocked on an owner decision (see "Open items").

Read against `claude/l4c-serialized-editing-state` at `353d256`. Every
count, line number and file reference below came from reading that tree
directly. Version and API facts came from Google's release notes, the
Compose BOM mapping table, and the AndroidX sources on `androidx-main`.
**No build was run and no test was executed** — the six checks in §4S are
specified, not written.

A rendered version of this document exists as an artifact:
<https://claude.ai/code/artifact/6fbeb724-c642-45d9-ad60-2a8e8b522d55>

---

## Why 5S, and not a numbered plan

Forager does not have a design system that needs redecorating. It has
twelve colour roles, no type scale, no shape scale, no motion scheme, and
seven colour roles the UI reads but the theme never sets. Styling on top
of that paints over the defects rather than removing them.

5S — Sort, Set in Order, Shine, Standardize, Sustain — came out of the
Toyota Production System to remove *muda*, work that adds no value. Seven
untokenised map colours with no dark-mode variant are muda in the
original sense: every future change has to touch all seven. So are six
animation specs that exist only to be asserted by their own unit test.
The five sections below are the implementation order, not a metaphor laid
over an ordinary plan.

## The finding that reshapes the brief

An earlier draft of this design treated "remove tween" as a risky
supersession of an accepted ADR, and proposed a chrome-versus-map split to
contain the blast radius. That caution was misplaced.

`MotionTokens.kt` defines eight `tween`-based `FiniteAnimationSpec`s.
Exactly one — `panelMotionSpec` — is referenced anywhere in
`app/src/main` outside its own file. The other six
(`feedbackMotionSpec`, `narrativeRevealSpec`, `markerEntranceSpec`,
`selectionPulseHalfCycleSpec`, `locationIndicatorMoveSpec`,
`dataLayerOverlaySpec`) are reachable only from `MotionTokensTest.kt`. So
is every duration constant except `PANEL_MOTION_DURATION_MS` and
`LOCATION_INDICATOR_MOVE_DURATION_MS`.

Removing tween from this app is a four-call-site change in production
code — all four in `AvailabilityScreen.kt`, lines 4067–4092 — plus a
rewrite of the token file, its test, and two documents.

### Three reversals from the earlier draft

**REV 01 — target `material3 1.5.0-alpha26` directly, pinned outside the
BOM.** The earlier draft argued for hand-building expressive components on
1.4.0 stable and swapping later, on the grounds that the alpha line was
churning. On `androidx-main` today, `FloatingToolbar`,
`FloatingActionButtonMenu`, `SplitButton` and `ButtonGroup` carry no
`@ExperimentalMaterial3ExpressiveApi` annotation at all; only
`LoadingIndicator` and `MaterialShapes` still do. Hand-building four
stable components to avoid an alpha they are already stable in is the
churn, not the protection against it.

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
| 06 | **One file holds 68 of the app's 115 composables.** 4,830 lines; the entry composable takes 45 parameters, 41 of them `on*` callbacks; fifteen test files point at it. Not a styling problem, but the largest single tax on the work below. | `AvailabilityScreen.kt:347` |
| 07 | **A dead parameter, documented and left in.** `drawerSheetContent`'s `showCloseButton` is always false in practice — one call site, the permanent drawer, which is never "closed." Correctly out of scope for the L4c workstream; in scope for a Sort pass. | `AvailabilityScreen.kt:637–653` |
| 08 | **Two accent greens, one collision.** `MossGreen` is `primary` in dark *and* `tertiary` in both themes, so in dark theme `primary` and `tertiary` are the same colour and any component pairing them has no contrast at all. | `Theme.kt:9–41` |
| 09 | **Six of eight motion specs are dead, and a test enforces that they stay tweens.** `MotionTokensTest.kt:37–48` asserts every spec *is* a `TweenSpec`, failure message "spring-based specs can overshoot"; `:52–56` casts `panelMotionSpec` to `TweenSpec` to read its duration. The tween-only rule is mechanically pinned, not merely documented. | `MotionTokens.kt`; `MotionTokensTest.kt`; production call sites `AvailabilityScreen.kt:4067, :4068, :4085–4087, :4090–4092` |
| 10 | **Sixteen `BackHandler`s, zero predictive back.** The app targets SDK 37 and calls `enableEdgeToEdge()`, so the system's predictive-back animation is on — but every back site uses `BackHandler`, which consumes the gesture with no progress callback, so the user gets no peek at the destination anywhere. `PredictiveBackHandler` appears nowhere. | `AvailabilityScreen.kt` (16 sites), `JournalTab.kt:128`, `LogPanel.kt:116`; `MainActivity.kt:169`; `app/build.gradle.kts:123` |

**What Sort does not touch.** The map layer's raw ints (tag 04) stay raw —
`SightingsMap.kt` documents why its overlay list must remain
basemap-agnostic, and a native canvas cannot read a Compose `ColorScheme`.
The fix in 2S is a mapping object, not a rewrite of the draw path. Tag 06
is named and left alone; see Open items.

---

## 2S — Set in Order (*Seiton*): a place for everything

### Colour — the full role set, both themes

Two recorded decisions in `Color.kt` are honoured, not overridden: dark
surfaces stay neutral grey (the warm brown was rejected because dark mode
was asked to match Android's own convention), and `Cream` stays a tonal
step, not the background.

The two new hues are not invented — they are the map's existing
planned-trip blue and search-centre red, promoted out of `SightingsMap.kt`
into `tertiary` and `error`. That closes tags 04 and 08 in one move.

| Role | Light | Dark | Note |
|---|---|---|---|
| `primary` / `onPrimary` | `#2E5339` / `#FBF8F1` | `#A8CBA0` / `#16301D` | Dark primary lightened so it no longer equals tertiary |
| `primaryContainer` / on— | `#D9E8D2` / `#16301D` | `#3D5A40` / `#D9E8D2` | Already exist — unchanged |
| `secondary` / `onSecondary` | `#A2612C` / `#FFFFFF` | `#E5A76B` / `#452507` | Mushroom deepened for light so it can carry text |
| `secondaryContainer` / on— | `#F6DFC8` / `#3A2008` | `#6B421A` / `#F6DFC8` | New |
| `tertiary` / `onTertiary` | `#3B6EA5` / `#FFFFFF` | `#A8C4E4` / `#12283F` | Closes tag 08; promoted from the map layer |
| `tertiaryContainer` / on— | `#D7E3F2` / `#12283F` | `#2B4763` / `#D7E3F2` | Closes tag 01 |
| `error` / `onError` | `#B33B3B` / `#FFFFFF` | `#E89A9A` / `#4A1212` | Closes tag 01 — 13 read sites were on baseline red |
| `errorContainer` / on— | `#F5DCDC` / `#3A0F0F` | `#6B2222` / `#F5DCDC` | Closes tag 01 |
| `surface` / `onSurface` | `#FAF8F3` / `#3B2E24` | `#1B1B1B` / `#EDE3D0` | Unchanged |
| `surfaceVariant` / on— | `#EDE3D0` / `#5B5347` | `#2C2C2C` / `#CFC9BE` | Unchanged |
| `surfaceContainerLowest`→`Highest` | `#FFFFFF` → `#E2DCCC` | `#101010` → `#373737` | Closes tag 01 — the bottom nav reads the middle step |
| `outline` / `outlineVariant` | `#8C8577` / `#D5CEBF` | `#8E8E8E` / `#444444` | Closes tag 01 |

**The map layer keeps its raw ints but stops owning them.** A single
`MapPalette` object, given the resolved `ColorScheme`, returns the seven
ints. The map gains a dark variant, and the mapping becomes unit-testable
headlessly.

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
app is operated with cold hands and gloves.

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
| Permanent drawer (medium + expanded) | Same search controls, always visible, 360dp | **Misplaced** — on a 600dp window that is 60% of the screen given to controls set once a session |

The last row is the one structural change this design proposes; it needs
sign-off (see Open items).

### Motion — the fifth token axis

Motion becomes a theme token like colour and shape. `MaterialTheme.motionScheme`
supplies six specs; `MotionTokens.kt` stops defining animation curves and
becomes a thin map from this app's named categories onto those six, so
call sites keep referring to *what is moving* rather than to a raw spring.

Values read from `ExpressiveMotionTokens.kt` on `androidx-main`:

| Spec | Damping | Stiffness | Overshoots? |
|---|---|---|---|
| `fastSpatialSpec` | 0.6 | 800 | yes, most |
| `defaultSpatialSpec` | 0.8 | 380 | yes, mild |
| `slowSpatialSpec` | 0.8 | 200 | yes, mild |
| `fastEffectsSpec` | 1.0 | 3800 | **never** |
| `defaultEffectsSpec` | 1.0 | 1600 | **never** |
| `slowEffectsSpec` | 1.0 | 800 | **never** |

**The half of the motion scheme that settles the old objection.** Every
*effects* spec is defined at `dampingRatio = 1.0` — critically damped,
which is the definition of a spring that reaches its target in minimum
time and does not cross it. "Spring" and "overshoot" are not synonyms, and
the earlier draft's chrome-versus-map split was built on treating them as
if they were. Anything that must not overshoot uses an effects spec;
anything that should uses a spatial one.

---

## 3S — Shine (*Seiso*): clean, and inspect by cleaning

### The motion replacement, in full

| Surface | Was | Becomes | Why |
|---|---|---|---|
| Buttons, chips, FAB, icon stack | tween 200 ease-out | `fastSpatialSpec` | Press feedback wants the overshoot |
| Nav bar, nav rail, tab switch | never specced | `defaultSpatialSpec` | Chrome; no positional truth to distort |
| Sheets, drawers, dialogs, panels | tween 300 ease-out | `slowSpatialSpec` | The only tween with production call sites |
| Marker entrance & clustering | tween 250 + 40ms stagger | `defaultSpatialSpec` + 40ms stagger | Scale and bounds change, so spatial. The stagger is a delay, not a curve |
| Selection pulse | tween 1600 linear loop | `slowSpatialSpec`, 0.96–1.04 | Amplitude bounds unchanged |
| Data-layer overlays | tween 450 ease-out | `defaultEffectsSpec` | Alpha only — must not flash past full opacity |
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

### ADR-0002, superseding part of ADR-0001

ADR-0001 is accepted, and `docs/motion-spec.md` §2 says "Panels and
navigation — ease-out, grounded; no springy overshoot" in as many words.
That cannot be quietly overwritten by a styling pass. It gets a
supersession with its rejected alternatives written down, never an edit to
the original.

- **Changes:** §2's "no springy overshoot" line for panels and navigation,
  and the tween-only rule in `MotionTokens.kt`'s class comment.
- **Unchanged:** the precedence order (legibility → performance → calm),
  the 8–12 continuous-animation budget, the four-tier degradation order,
  and the whole Reduce Motion mapping table. `MotionPrecedence.kt` needs
  no change at all.
- **Unchanged:** the scope boundary. No confidence score, no candidate
  list, no per-observation harm assessment enters the motion layer.
- **Adds:** a Reduce Motion mapping for springs. `ReducedMotionTreatment`
  already maps every treatment to a still-visible equivalent rather than
  to nothing; springs map the same way, to `snap()` or a cross-fade, never
  to a silently dropped state change.
- **Rejected alternative:** the chrome-versus-map split. It protected a
  puck that was never spring-driven, and would have left two motion
  vocabularies in a codebase that currently uses one call site's worth of
  either.
- **Rejected alternative:** `MotionScheme.standard()` instead of
  `expressive()`. Standard's spatial springs sit at damping 0.9 against
  expressive's 0.6–0.8 — calmer, and defensible for an outdoor instrument.
  Rejected because it is the choice to make after seeing expressive on
  hardware, not before; the switch is one argument.

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
- **Medium (600–840dp) — one structural change.** The six bottom-nav
  destinations become a `NavigationRail`, and the 360dp permanent drawer
  becomes modal, opened from it. **This re-opens a settled scope
  decision** — see Open items.
- **Expanded (≥ 840dp) — tokens only.** The `PermanentNavigationDrawer` +
  `CombinedResultsPane` layout is kept structurally intact, exactly as
  `map-redesign.md` requires. A navigation rail is added left of the
  permanent drawer so the six destinations are reachable without the
  drawer carrying them.

### Predictive back

Tag 10's fix is a swap at sites that already exist. Every `BackHandler`
guarding a *dismissible* surface — drawer, fullscreen map, offline-maps
panel, crash-log panel, each picker phase — becomes a
`PredictiveBackHandler` whose progress flow drives the same expressive
spec that opens the surface. Back-to-exit-a-tab handlers, which navigate
rather than dismiss, stay as they are: there is nothing to peek at.

A drawer that springs shut on `slowSpatialSpec` and a drawer that follows
the user's back-swipe are the same animation driven from two sources,
which is why this belongs in the motion step and not in its own.

### The inspection that Shine forces

Three times now this project has shipped a layout over the map that
silently swallowed the map's own touches — the compass strip, then the
return-to-vehicle row, then a `horizontalScroll` modifier that installed a
pointer handler at zero scroll range. All three were caught green-to-failing
by a Robolectric test driving the real screen's long-press.

Every new element in this design that sits over the map is in that blast
radius: the floating toolbar's pill container, the FAB menu's expanded
scrim, the compass strip's new container, and the medium-window rail. Each
needs a long-press test before it is called done. Visual review is
precisely what missed this twice.

### Components — one stage, on 1.5.0-alpha26

| Where | Component | Status | Replaces |
|---|---|---|---|
| Map icon stack | `VerticalFloatingToolbar` | stable in 1.5.0 | `MapIconStack`, `AvailabilityScreen.kt:3678` |
| Add action | `FloatingActionButtonMenu` | stable in 1.5.0 | `ThreeWayActionDialog` |
| Category filter | `ButtonGroup` | stable in 1.5.0 | A plain `Row` of `FilterChip` |
| Save / Save & close | `SplitButton` | stable in 1.5.0 | Two adjacent buttons in the Journal editor |
| Search loading | `LoadingIndicator` | **experimental — hold** | Nothing yet; keep `CircularProgressIndicator` |
| Marker shapes | `MaterialShapes` | **experimental — hold** | Nothing yet; the waypoint pin stays a hand-drawn `Path` |
| Theme entry point | `MaterialExpressiveTheme` | stable in 1.5.0 | `MaterialTheme(colorScheme)`, `Theme.kt:50` |

**Verify the resolved version before writing any of this.** Compose BOM
2026.08.00 resolves material3 to 1.4.0; the expressive components sit in
the 1.5.0 line, whose head is `1.5.0-alpha26` (12 Aug 2026). Since
`libs.versions.toml` declares `androidx-material3` with no `version.ref`,
the pin is a one-line override on that entry, which wins over the BOM for
that artifact and leaves every other Compose artifact BOM-managed. Both
version facts were read off Google's pages and the statuses off
`androidx-main` — *not* off `./gradlew :app:dependencies`, which is the
only authority on what actually resolves. Re-check the two experimental
rows against the pinned alpha rather than `androidx-main`; they are not
the same tree.

---

## 4S — Standardize (*Seiketsu*): make the cleaned state the normal state

Six checks. Five headless unit tests, one shell script following the
precedent `scripts/verify-codeowners-placeholders.sh` already set. All six
fail on the code as it stands today, which is the point.

| Check | Asserts | Catches |
|---|---|---|
| `ThemeCompletenessTest` | Every colour role read anywhere in `ui/` is explicitly set in both `LightColors` and `DarkColors`, and no role equals its Material baseline default | Tag 01, and its recurrence — which has already happened once |
| `ThemeContrastTest` | Every on/container pair clears WCAG AA at its intended size; pure Kotlin, no device | A token edit that quietly drops text below legible outdoors |
| `MapPaletteTest` | The seven map ints differ between light and dark, and each traces to a named role | Tag 04 — the map having no dark mode at all |
| `ExpressiveThemeTest` | `ForagerTheme` supplies non-default `Typography`, `Shapes` and `MotionScheme`, and the scheme is `expressive()` not `standard()` | Tags 02, 03, and the motion scheme silently reverting |
| `MotionTokensTest` (inverted) | No exposed spec is a `TweenSpec`; each maps to the `MotionScheme` spec its category calls for; effects-category specs have `dampingRatio >= 1.0` | Tag 09, and a spring with overshoot landing on an overlay's alpha |
| `verify-design-tokens.sh` | No `Color(0x` literal outside `ui/theme/`; no palette constant imported outside the theme package; no `tween(` in `ui/` | Tag 05, the next twenty like it, and tween creeping back one call site at a time |

**One existing test must be inverted by hand.** `MotionTokensTest.kt:37–48`
asserts every spec *is* a `TweenSpec`; `:52–56` casts `panelMotionSpec` to
`TweenSpec` to read its duration. Both fail the moment tween goes —
correctly, and they are the proof this change is real rather than
cosmetic. Rewrite them to assert the new property; do not delete them, and
do not weaken them to a type-agnostic check, or the file stops
discriminating anything.

### The standing rule this design adds

`CLAUDE.md` already carries the touch-interception pitfall. This design
promotes it from a warning into an entry condition, because it now applies
to four new elements at once.

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

Each step is only safe once the one above has landed. Steps 1–3 are worth
landing on their own even if the rest is never built.

| # | Step | Gates |
|---|---|---|
| 1 | Fill the seven unset colour roles; add `ThemeCompletenessTest` and `ThemeContrastTest` | Nothing — ships alone on 1.4.0, fixes visible baseline leakage today |
| 2 | Pin material3 to `1.5.0-alpha26`; convert `ForagerTheme` to `MaterialExpressiveTheme` with `Typography`, `Shapes` and `MotionScheme.expressive()`; add `ExpressiveThemeTest` | Step 1; verify with `./gradlew :app:dependencies` first |
| 3 | Extract `MapPalette`; move `Spacing` into the theme; add `verify-design-tokens.sh` | Step 1 (independent of step 2) |
| 4 | Write ADR-0002; delete the six dead specs; rewrite `MotionTokens.kt` onto the motion scheme; invert `MotionTokensTest`; update motion-spec §2 | Step 2; **owner sign-off on superseding ADR-0001** |
| 5 | Compact finish pass — floating toolbar, FAB menu, button group, split button, compass-strip figures | Steps 2–4, plus a long-press test per element |
| 6 | Swap `BackHandler` → `PredictiveBackHandler` at the dismissible surfaces | Step 4 |
| 7 | Medium-window navigation rail | **Owner sign-off on re-opening the compact-only scope decision** |

### Before any of it — the base branch

`main` and the design branch `claude/forager-m3-expressive-design-dguw4e`
are the same commit, and both sit 9 commits behind
`claude/l4c-serialized-editing-state`, with 1 commit L4c does not have.
Everything in this document was read against L4c at `353d256`, not against
`main`.

`CLAUDE.md` records why this is not pedantry: PR #26 and the Phase 1
branch each independently declared `ForagerDatabase.version = 5` with
different migration bodies from the same base — a collision that merges
cleanly and produces a broken result. A design-token change carries the
same hazard class: two branches can each add a colour role, merge without
conflict, and leave one silently overwriting the other. Cut from L4c, and
check the token file against its current state rather than the state you
started from.

---

## Open items

Kept separate from the plan on purpose. `CLAUDE.md` is explicit that a
report which only reassures has failed at its job.

**Needs your decision — the medium-window rail re-opens a settled scope.**
`docs/plans/map-redesign.md` records "This phase is compact-only, same as
Phase 1 — MEDIUM/EXPANDED's PermanentNavigationDrawer +
CombinedResultsPane path is untouched." Step 7 contradicts that in as many
words. Flagged rather than assumed.

**Needs your decision — ADR-0002 supersedes an accepted ADR.** Until it is
written, the tween-only rule stands as the governing decision and step 4
should not start.

**Reversible, one argument — `expressive()` rather than `standard()`.**
Standard's spatial springs sit at damping 0.9 against expressive's
0.6–0.8. You asked for Expressive and this design gives you Expressive,
but the switch is `motionScheme = MotionScheme.standard()` and nothing
else. Worth knowing before hardware, not after.

**Needs verifying in the build — the resolved material3 version.** Neither
the BOM mapping nor the alpha head was read off
`./gradlew :app:dependencies`. The stable/experimental statuses come from
`androidx-main`, which is not the same tree as the pinned alpha tag.

**Not verifiable headlessly — everything about how this reads outdoors.**
Contrast ratios can be asserted in a unit test. Whether a 20dp radius
helps with gloves, whether tabular figures actually stop the compass strip
wrapping on a real phone, and whether a damping-0.6 press feedback feels
right or feels loose while walking are hardware questions. The README's
"Not yet verified" section is where they belong.

**Left undone — the 4,830-line screen file.** Tag 06 is real and no fix is
proposed. Splitting it is a refactor with its own risk profile and fifteen
test files pointed at it. It belongs in its own workstream, not smuggled
into a design pass.

**Left undone — no screen-by-screen redesign.** This document specifies a
token layer, a motion system, a component mapping and an implementation
order. It does not draw every screen. Compact's structure is deliberately
kept; what is missing is the medium-window rail layout, which is also the
step waiting on sign-off.

**Not run — no build, no test run.** Every count, line number and API
status above is a static read. The six checks in 4S are specified, not
written.
