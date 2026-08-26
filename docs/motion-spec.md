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

All curves live in `MotionTokens.kt` as named accessors onto
`MaterialTheme.motionScheme` (`MotionScheme.expressive()` — see
[`docs/adr/0002-motion-scheme-adoption.md`](adr/0002-motion-scheme-adoption.md)).
No magic numbers or raw `tween`/`spring` calls at call sites; a handful of
values that are durations, rates, or delays rather than curves stay plain
constants (named below), since ADR-0002 only changes the curves.

| Category | Behavior | Spec |
|---|---|---|
| Feedback motion | Buttons, chips, FAB, icon stack press feedback. Wants the overshoot | `fastSpatialSpec` |
| Narrative reveals | Interruptible reveal; also backs route recalculation's morph (the per-km reveal *pace* stays a rate, `ROUTE_REVEAL_MS_PER_KM`, not a curve) | `slowEffectsSpec` |
| Map markers | Soft scale + fade entrance when density and performance allow; otherwise cross-fade or instant. Clustering fans out with staggered timing (`MARKER_CLUSTER_STAGGER_STEP_MS`, a delay, not a curve), not uniform snaps | `defaultSpatialSpec` |
| Selection emphasis | Low-amplitude breathing pulse (`SELECTION_PULSE_MIN_SCALE`/`MAX_SCALE`, unchanged), stops once the detail panel opens. Selection emphasis only — this spec defines no other kind of emphasis | `slowSpatialSpec` |
| User location | Animate only on meaningful GPS change; avoid jitter. During slow lock, show an explicit acquiring state — never a falsely precise pin. **Unsupported as a spring**: MapLibre's `LocationComponentOptions.trackingAnimationDurationMultiplier` accepts a scalar duration multiplier and nothing else, so `LOCATION_INDICATOR_MOVE_DURATION_MS` stays a plain duration — see ADR-0002 | *(none — see behavior)* |
| Routes | Progressive reveal at human-scale pace for longer corridors (`ROUTE_REVEAL_MS_PER_KM`), but always provide immediate full-path display when the user needs it. Recalculation morphs existing segments rather than redrawing | `slowEffectsSpec` (the morph only) |
| Navigation chrome | Nav bar, nav rail, tab switch. No positional truth to distort | `defaultSpatialSpec` |
| Panels and navigation | Sheets, drawers, dialogs, panels. **Accepts mild overshoot** — ADR-0002 records this as a deliberate taste call, not a finding that the concern below was misplaced | `slowSpatialSpec` |
| Data layer overlays | Cross-fade or gentle radial growth. No particle systems. Alpha only, so critically damped — must never overshoot past full opacity | `defaultEffectsSpec` |

"Navigation chrome" and "Panels and navigation" were one category
("Panels and navigation: ease-out, grounded; no springy overshoot") before
ADR-0002. They now take different specs — chrome has no positional truth
to protect, while sheets/drawers/dialogs are where ADR-0001's original
concern actually applies and where ADR-0002 accepts it anyway — so the
table splits them rather than picking one spec for both.

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
