# Motion Specification

Version 1. Source of truth for motion behavior in the Forager app —
durations, easings, object behavior, performance budget, degradation
order, and Reduce Motion. Code under `app/src/main/java/com/forager/app/ui/motion/`
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
- [`docs/plans/forager-navigator-plan.md` §7](plans/forager-navigator-plan.md):
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

Encoded as `MotionPrecedence.Principle` / `MotionPrecedence.PRECEDENCE_ORDER`
in `MotionPrecedence.kt`.

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
| Feedback motion | Spring-driven; press feedback wants the overshoot (`fastSpatialSpec`, provisional pending the device gate) |
| Narrative reveals | Up to 800–1200 ms, interruptible; spring-driven (`slowEffectsSpec`) |
| Map markers | Soft scale + fade entrance when density and performance allow; otherwise cross-fade or instant. Clustering fans out with staggered timing (`MARKER_CLUSTER_STAGGER_STEP_MS`), not uniform snaps |
| Selection emphasis | Low-amplitude breathing pulse (`SELECTION_PULSE_MIN_SCALE`–`SELECTION_PULSE_MAX_SCALE`), stops once the detail panel opens. Selection emphasis only — this spec defines no other kind of emphasis |
| User location | Animate only on meaningful GPS change; avoid jitter. During slow lock, show an explicit acquiring state — never a falsely precise pin. Not a `MotionScheme` category: MapLibre's puck takes a scalar duration multiplier (`LOCATION_INDICATOR_MOVE_DURATION_MS`), not a Compose `AnimationSpec` |
| Routes | Progressive reveal at human-scale pace (`ROUTE_REVEAL_MS_PER_KM`) for longer corridors, but always provide immediate full-path display when the user needs it. Recalculation morphs existing segments rather than redrawing (`slowEffectsSpec`) |
| Panels | Spring-driven, accepting mild overshoot as a taste call (`slowSpatialSpec`, provisional pending the device gate) — the app's primary interaction surface and the one category with a real production call site today |
| Navigation chrome (nav bar, nav rail, tab switch) | Spring-driven (`defaultSpatialSpec`); chrome, no positional truth to distort, so overshoot here is a harmless flourish rather than felt on a primary surface |
| Data layer overlays | Cross-fade or gentle radial growth (`defaultEffectsSpec`). No particle systems |

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

Encoded as `MotionPrecedence.MAX_CONTINUOUS_ANIMATED_OBJECTS`,
`MotionPrecedence.DegradationTier`, and `MotionPrecedence.activeTiers()`
in `MotionPrecedence.kt`. The marker-clustering threshold is a separate,
provisional constant — see `MotionPrecedence.MARKER_CLUSTERING_THRESHOLD`
and the ADR's "Provisional constants" section.

## 4. Reduce Motion and accessibility

Read the platform setting via `Settings.Global.ANIMATOR_DURATION_SCALE` /
`TRANSITION_ANIMATION_SCALE`; expose it as a `CompositionLocal` that
motion tokens consult. This is a mapping layer, not a global kill switch
— the state each animation communicates must remain communicated.

| Default | Reduce Motion |
|---|---|
| Route progressive draw | Instant full path |
| Marker entrance | Alpha cross-fade or instant |
| Selection pulse | Static emphasis |
| Panel motion | Fade or instant |

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
   (`MotionPrecedence.MARKER_CLUSTERING_THRESHOLD`); not yet validated
   against real hardware or real dense-map fixtures. Revisit before this
   spec is treated as final.
2. **Named owner role for `CODEOWNERS` and PR sign-off.**
   **Resolved 2026-08-21:** the reviewer handle is `@slayer8366`.
   `.github/CODEOWNERS` now names it in place of the former
   `@TODO-motion-owner` placeholder, and
   `scripts/verify-codeowners-placeholders.sh` passes.
