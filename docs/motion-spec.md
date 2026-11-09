# Motion Specification

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

Version 1. Source of truth for motion behavior in the Forager app —
durations, easings, object behavior, performance budget, degradation
order, and Reduce Motion. Code under `app/src/main/java/com/zynergylabs/forager/app/ui/motion/`
implements this doc; code comments there reference this doc's section
names rather than restating its rules.

## Scope boundary

This spec governs **how motion behaves** — it does not, and must not,
encode any identification-confidence or per-observation harm judgment.

An earlier draft of this spec included a confidence/harm state machine
(model score, community confirmations, look-alike lookup → a resolved
confidence and harm state driving emphasis). That was rejected before
implementation because it conflicts with a decision already recorded
twice in this repository:

- `MushroomLogEntry.kt`'s doc comment: a stated safety property from the
  project owner that the type never identifies the mushroom, and no
  field is or feeds a species suggestion, candidate list, "likely," or
  confidence score.
- [`docs/navigation/forager-navigator-plan.md` §7](navigation/forager-navigator-plan.md):
  "AI identification or edibility advice" is listed under "Deferred
  indefinitely."

Nothing in this spec, or in the code implementing it, introduces a
confidence score, a candidate list, a species suggestion, or any
per-observation harm assessment. See that plan's §7 for the boundary
note on the one adjacent feature that *is* planned (a toxic-look-alike
reference page keyed on a search term, not an observation) and why it
sits outside this exclusion.

What follows is a specification for object motion only: how things move,
how motion degrades under load, and how it stays legible outdoors.

## 1. Precedence order

When principles conflict, resolve in this order:

1. **Legibility** — readable under glare, wet screens, gloves, one-handed
   use while walking.
2. **Performance** — battery, frame rate, offline resilience.
3. **Calm / ecological metaphor** — organic motion, peripheral awareness.

Motion that fails under outdoor load is worse than no motion. See
[`docs/adr/0001-motion-precedence.md`](adr/0001-motion-precedence.md)
for why this order was chosen and what was rejected.

Was encoded as `MotionPrecedence.Principle` / `MotionPrecedence.PRECEDENCE_ORDER`
in `MotionPrecedence.kt`. **Removed 2026-10-07** with the rest of that file by
motion Part 3's prune (dispatch 2026-09-28-676; the owner, RECORD -655: "Use it,
then prune (Recommended)"): nothing in the app read it. The order above stands
as written; it is a rule for whoever designs motion, not code.

## 2. Motion tokens and object behavior

Every category below resolves to one of `MaterialTheme.motionScheme`'s six
specs via a named `MotionTokens.kt` function — see
[`docs/adr/0002-motion-scheme-adoption.md`](adr/0002-motion-scheme-adoption.md)
for the full category-to-spec table and why each category landed where it
did. A handful of categories also keep a plain named constant alongside
their spec, for a value the spec itself doesn't carry (a stagger delay, an
amplitude bound, a rate, or — for the one category with no spec at all —
a duration). No magic numbers or raw `tween`/`spring` calls at call sites.

| Category | Behavior |
|---|---|
| Feedback motion | Spring-driven; press feedback wants the overshoot (`fastSpatialSpec`, provisional pending the device gate). The press bounce dips to `PRESS_BOUNCE_SCALE` and springs back on it; an icon swap's slight grow rides it too |
| Icon swap | A short crossfade of an icon's picture, tint, badge or disabled dimming (`iconSwapSpec`, `fastEffectsSpec`), with the incoming picture growing from `ICON_SWAP_ENTER_SCALE` on the feedback spec. Added 2026-10-07 for motion Part 1 (dispatch 2026-09-28-652; the owner's choice in RECORD -651, "Quick crossfade") |
| Narrative reveals | Up to 800–1200 ms, interruptible; spring-driven (`slowEffectsSpec`). *`narrativeRevealSpec` removed unused on 2026-10-07 (motion Part 3's prune)* |
| Map markers | Soft scale + fade entrance when density and performance allow; otherwise cross-fade or instant. Clustering fans out with staggered timing (`MARKER_CLUSTER_STAGGER_STEP_MS`), not uniform snaps. *The spec and the stagger constant were removed unused on 2026-10-07 (motion Part 3's prune); the marker fan's own timing lives in `map/fanout/MarkerFanOutState.kt`* |
| Selection emphasis | Low-amplitude breathing pulse (`SELECTION_PULSE_MIN_SCALE`–`SELECTION_PULSE_MAX_SCALE`), stops once the detail panel opens. Selection emphasis only — this spec defines no other kind of emphasis. *Spec and bounds removed unused on 2026-10-07 (motion Part 3's prune)* |
| User location | Animate only on meaningful GPS change; avoid jitter. During slow lock, show an explicit acquiring state — never a falsely precise pin. Not a `MotionScheme` category: MapLibre's puck takes a scalar duration multiplier (`LOCATION_INDICATOR_MOVE_DURATION_MS`), not a Compose `AnimationSpec` |
| Routes | Progressive reveal at human-scale pace (`ROUTE_REVEAL_MS_PER_KM`) for longer corridors, but always provide immediate full-path display when the user needs it. Recalculation morphs existing segments rather than redrawing (`slowEffectsSpec`). *`ROUTE_REVEAL_MS_PER_KM` and `routeRecalculationMorphSpec` removed unused on 2026-10-07 (motion Part 3's prune)* |
| Panels | Spring-driven, accepting mild overshoot as a taste call (`slowSpatialSpec`, provisional pending the device gate) — the app's primary interaction surface and the one category with a real production call site today |
| Navigation chrome (nav bar, nav rail, tab switch) | Spring-driven (`defaultSpatialSpec`); chrome, no positional truth to distort, so overshoot here is a harmless flourish rather than felt on a primary surface |
| Data layer overlays | Cross-fade or gentle radial growth (`defaultEffectsSpec`). No particle systems. *`dataLayerOverlaySpec` removed unused on 2026-10-07 (motion Part 3's prune)* |
| Tab switch and the bar with it | A quick crossfade from one tab to the next (`tabCrossfadeSpec`, `fastEffectsSpec`); the solid bottom bar, or the landscape rail beside the content, fades on the same spec while the Maps tab's own 80% bar fades with its tab. The outgoing tab is held at its size and place and takes no touch while it fades. Added 2026-10-07 for motion Part 2 (dispatch 2026-09-28-666; the owner's choices in RECORD -651, "Quick crossfade" and "Fade with the tab") |
| Map pop-ups | Fade (`mapPopUpFadeSpec`, `defaultEffectsSpec`) and grow from where each belongs (`mapPopUpGrowSpec`, `defaultSpatialSpec`, from `MAP_POPUP_ENTER_SCALE`), the grow drawn only, so a pop-up takes touches at its settled size from its first frame; from the moment one starts to leave it takes no touch. The compass strip's sundown line grows its strip by layout, on the same pair. Motion Part 2 ("Fade and grow", "Let taps through at once") |
| Navigation start and stop | The compass strip slides up and out while the navigation display slides down and in, and the reverse on stop, with a fade, as one timed animation on `navigationViewChromeSpec`: exactly as long as the map's tilt (`NAVIGATION_VIEW_TRANSITION_MILLIS`, `map/NavigationView.kt`, read from there). **The one tween in the app's motion, an exception to ADR-0002** (the owner, Amendment 1 to motion Part 2, RECORD -672: "Allow one exception"), because a spring has no duration and could only approximate a fixed-length camera change; design-token check 3 allows that one call site and no other. Motion Part 2 ("Move with the map") |
| Words that change | A line whose words change crossfades (`wordSwapSpec`, `fastEffectsSpec`); a change in its numbers alone is drawn at once (`WordSwap`). Motion Part 2 ("Numbers instant, words fade") |
| Pages (Journal, and wherever else a page opens on top of another: Amendment 1, RECORD -681, "Same rule everywhere") | An opened page slides in from the right over the one beneath, which stays still; Back slides it out to the right, uncovering the page beneath in place (`PageSlide`, `SlideOverPage`; `pageSlideSpec`, `defaultEffectsSpec`, critically damped so a slide-in cannot overshoot and bare the page beneath). A leaving page takes no touch, keeps only its marker in the semantics tree, and has its Back handlers off. Motion Part 3 (dispatch 2026-09-28-676; the owner, RECORD -651: "Slide in, slide back") |
| Tools drawer pages | Push from the left: the opened page enters from the drawer's left edge while the current one leaves by the right, edge to edge, never overlapping; Back reverses it (`PageSlide` with `PageSlideStyle.PUSH_FROM_LEFT`, `pageSlideSpec`). The pages draw no fill, so the drawer's own stays the single 80% layer over the map. Motion Part 3, Amendment 2 (RECORD -682, "Push slide from the left") |
| Loaded content | A spinner, empty message or error and the content it gives way to crossfade (`StateCrossfade`, `stateCrossfadeSpec`, `fastEffectsSpec`); something arriving late inside a page grows in like a list row (`GrowIn`). Amendment 2 |
| Lists | A removed row stays where it was to fade and shrink while the rows below close up; Undo turns the same row round; a new row fades and grows in; in a lazy list a row that moves glides to its place (`rememberListRows`, `ListRowMotion`; `listRowSpec`, `defaultEffectsSpec`). A leaving row takes no touch. Motion Part 3 ("Slide and close up") |
| Night mode | Every colour of the theme blends quickly from the old scheme to the new (`fadingColorScheme`, `theme/ColorSchemeFade.kt`; `nightModeFadeSpec`, `fastEffectsSpec`, read from the motion scheme the theme provides, since it fades the theme's own colours). Motion Part 3 ("Fade the colours"; RECORD -652: "fast and smooth, and not ceremonial and boring") |

Prefer `graphicsLayer` transforms and alpha. Avoid heavy path morphing
while the user is moving.

## 3. Performance budget

- Target 60 fps on mid-range hardware; never below 30 fps for interactive
  elements.
- **Maximum 8–12 simultaneously, continuously animated objects on the
  map.** Cluster or stagger beyond this.
- Continuous animation is the exception. It is reserved for the user's
  own location indicator; anything else needs justification in review.
- Degradation order under load or battery-saver: decorative continuous
  loops → route progressive detail → marker entrance animations →
  location indicator (never degrades).

Was encoded as `MotionPrecedence.MAX_CONTINUOUS_ANIMATED_OBJECTS`,
`MotionPrecedence.DegradationTier`, and `MotionPrecedence.activeTiers()`
in `MotionPrecedence.kt`, with a provisional marker-clustering threshold
(`MotionPrecedence.MARKER_CLUSTERING_THRESHOLD`, 8). **Removed 2026-10-07** by
motion Part 3's prune (see §1): nothing in the app called any of it, so the
budget was never enforced by code. The budget and order above stand as the rule.

## 4. Reduce Motion and accessibility

Read the platform setting via `Settings.Global.ANIMATOR_DURATION_SCALE` /
`TRANSITION_ANIMATION_SCALE`; expose it as a `CompositionLocal` that
motion tokens consult. This is a mapping layer, not a global kill switch (the enum that once encoded it, `MotionTreatment`, was removed unused on 2026-10-08, motion Part 3, Amendment 1)
— the state each animation communicates must remain communicated.

| Default | Reduce Motion |
|---|---|
| Route progressive draw | Instant full path |
| Marker entrance | Alpha cross-fade or instant |
| Selection pulse | Static emphasis |
| Panel motion | Fade or instant |
| Press bounce | None: the press highlight alone shows the press |
| Icon swap | Crossfade without the grow |
| Icon cluster glide after a drag | Instant, as before the glide |
| Tab crossfade, and the bar or rail fading with it | Instant: no outgoing tab is kept |
| Map pop-up fade and grow | The fade alone |
| Navigation start and stop slide | The fade alone, in place |
| Compass strip's sundown line | The fade alone; the strip takes the line's height at once |
| Snackbar height glide on the Maps tab | Instant |
| Words that change | The crossfade, as an icon swap keeps its crossfade |
| Journal page slide | Instant: no leaving page is kept |
| List rows leaving, arriving and gliding | Instant: a removed row goes and a new one is there at once |
| Night mode colour blend | Instant |
| Tools drawer push, loaded-content crossfade, late content growing in | Instant |

The press bounce, icon swap and cluster glide rows were added with motion Part 1 (dispatch 2026-09-28-652); the six after
them with motion Part 2 (dispatch 2026-09-28-666), and the last three with motion Part 3 (dispatch 2026-09-28-676). The value is provided app-wide by
`ProvideReduceMotion`, which `ForagerTheme` calls, and kept current while the app runs; the marker fan and the restore
page read it there instead of reading the settings themselves.

This table is a statement about what a Reduce-Motion user sees, not about
which `AnimationSpec` drives the full-motion version — it needed no change
when `docs/adr/0002-motion-scheme-adoption.md` moved every category from
`tween` onto `MaterialTheme.motionScheme`. A spring maps to a
still-visible equivalent the same way a tween did, never to a silently
dropped state change.

Hue is never the sole carrier of state. Every state carries redundant
encoding — outline weight, pattern, icon, or shape.

## 5. Field test conditions

All motion validated under: direct sun and deep shade; wet or dirty
screen; gloves or cold hands; one-handed use while walking; battery-saver
mode; offline or intermittent connectivity; mid-range hardware; no GPS
lock or delayed lock.

## 6. Open questions

Tracked here rather than left implicit. Answered in the PR that lands
this spec, not guessed:

1. **Marker clustering threshold.** Empirical — tuned against dense-map
   fixtures on target devices, starting at the low end of the 8–12
   budget. Currently a provisional named constant
   (`MotionPrecedence.MARKER_CLUSTERING_THRESHOLD`, removed unused on 2026-10-07); not yet validated
   against real hardware or real dense-map fixtures. Revisit before this
   spec is treated as final.
2. **Named owner role for `CODEOWNERS` and PR sign-off.**
   **Resolved 2026-08-21:** the reviewer handle is `@slayer8366`.
   `.github/CODEOWNERS` now names it in place of the former
   `@TODO-motion-owner` placeholder, and
   `scripts/verify-codeowners-placeholders.sh` passes.
