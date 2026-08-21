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

All durations and easings live in `MotionTokens.kt` as named constants.
No magic numbers at call sites.

| Category | Behavior |
|---|---|
| Feedback motion | 150–400 ms, ease-out |
| Narrative reveals | Up to 800–1200 ms, interruptible |
| Map markers | Soft scale + fade entrance when density and performance allow; otherwise cross-fade or instant. Clustering fans out with staggered timing, not uniform snaps |
| Selection emphasis | Low-amplitude breathing pulse, stops once the detail panel opens. Selection emphasis only — this spec defines no other kind of emphasis |
| User location | Animate only on meaningful GPS change; avoid jitter. During slow lock, show an explicit acquiring state — never a falsely precise pin |
| Routes | Progressive reveal at human-scale pace for longer corridors, but always provide immediate full-path display when the user needs it. Recalculation morphs existing segments rather than redrawing |
| Panels and navigation | Ease-out, grounded; no springy overshoot |
| Data layer overlays | Cross-fade or gentle radial growth. No particle systems |

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

Encoded as `LocalReduceMotion`, `isReduceMotionEnabled()`, and
`reducedMotionEquivalent()` in `ReduceMotion.kt`.

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
2. **Named owner role for `CODEOWNERS` and PR sign-off.** Not yet
   assigned — `.github/CODEOWNERS` currently names a placeholder handle
   that must be replaced with a real reviewer before it takes effect.
   `scripts/verify-codeowners-placeholders.sh` fails loudly as a reminder
   until that happens; see the script's header for why it exists and why
   it isn't wired into `ci.yml`.
