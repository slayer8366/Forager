# ADR 0002: Adopt `MaterialTheme.motionScheme`, superseding ADR-0001's tween-only line

## Status

Accepted for the decision to adopt `MotionScheme` and for every mapping
below except the panel row's damping value, which is **provisional**
pending Gate G (`docs/plans/understory-design-system.md` §5S, question 2:
"Does the drawer spring read as sloppy at `slowSpatialSpec`?"). The
amendment path for that one value is stated below rather than left
implicit.

Supersedes, not amends: [ADR-0001](0001-motion-precedence.md) stays
accepted for the precedence order and degradation model, which this ADR
does not touch. What it supersedes is narrower and named explicitly —
`docs/motion-spec.md` §2's "Panels and navigation — ease-out, grounded; no
springy overshoot" line, and the tween-only rule stated in
`MotionTokens.kt`'s own class comment. Both are rewritten, not edited
silently underneath an unchanged ADR-0001.

## Context

`MotionTokens.kt` defined eight `tween`-based `FiniteAnimationSpec`s, one
per category in `docs/motion-spec.md` §2. Two questions had to be answered
before any of them could become spring-based, since Material's spring
specs are what `MaterialTheme.motionScheme` actually supplies (`spring<Any>`
under both `standard()` and `expressive()` — there is no tween-based
`MotionScheme`, so adopting the theme's motion axis at all means adopting
springs, not a choice made independently of it):

**Can a spring overshoot under interruption?** ADR-0001 predates
`MotionScheme` and says no spring anywhere, on the grounds that panels are
the app's primary interaction surface and overshoot there reads as sloppy.
The question the design review actually settled (`understory-design-system.md`
§2S, "R1"): a critically damped spring (ζ = 1, Compose's `*EffectsSpec`
family) can **never** overshoot its own in-flight target, at any point on
its own path — the ratio of velocity to restoring force stays strictly
below the threshold that would cross zero, for every finite time since the
motion started. Interruption alone is provably safe. **Retargeting** —
redirecting an in-flight animation to a new destination, so the distance
term collapses while the velocity term does not — is the only way a
critically damped spring overshoots. That is a property of the call site
(does anything retarget an in-flight alpha to an intermediate value?), not
of the spec, so it gets a standing check rather than a one-time read: today
every animated alpha in `app/src/main` is `AnimatedVisibility`'s default
`fadeIn`/`fadeOut`, both targeting the bound (0 or 1), and
`scripts/verify-design-tokens.sh` check 4 now asserts no
`initialAlpha`/`targetAlpha` names a non-bound value, so a future retarget
to e.g. 0.55 fails the script rather than silently reintroducing the
overshoot ADR-0001 was written to prevent. **The clamp is not the backstop
here — it is what the check enforces, because the interruption argument
above does not need it.**

Spatial specs (ζ = 0.6–0.8 under `expressive()`) are a different case:
they are underdamped by construction and *do* overshoot, including from
rest. Nothing about the effects-spec argument above extends to them. That
is why the panel row's move to `slowSpatialSpec` is recorded below as a
**taste call** — accepting mild overshoot as a design decision — and not
as a consequence of the math above, which says nothing about spatial
specs at all.

**Is the 8–12 continuous-animation budget duration-based, and does that
block a spring driving it?** No, on both counts, but for two different
reasons. `MotionPrecedence.kt` has no Compose or Android import and
contains no duration of any kind — `activeTiers()` degrades tiers by
**object count**, not elapsed time, so nothing about it assumes a tween's
known end. But `activeTiers`, `shouldClusterMarkers`,
`MAX_CONTINUOUS_ANIMATED_OBJECTS` and `DEGRADATION_ORDER` have no
production caller anywhere in `app/src/main` — nothing supplies the counts
the budget degrades on. **The budget has never been enforced**, independent
of this ADR and unchanged by it. Recording it under "unchanged" without
saying so would let a future reader take that word as evidence the budget
is live, so it is stated as a condition below instead.

## Decision: adopt `MaterialTheme.motionScheme` as the motion source

`MotionTokens.kt` stops defining animation curves. It becomes a thin,
`@Composable` map from this app's named motion categories onto the six
specs `MaterialTheme.motionScheme` supplies
(`defaultSpatialSpec<T>()`/`fastSpatialSpec<T>()`/`slowSpatialSpec<T>()`/
`defaultEffectsSpec<T>()`/`fastEffectsSpec<T>()`/`slowEffectsSpec<T>()`),
so call sites keep referring to *what is moving* rather than to a raw
spring, exactly as they did with tweens. `@Composable` is not a style
choice: `MaterialTheme.motionScheme` is itself
`@Composable @ReadOnlyComposable`, reading the theme's `CompositionLocal`,
so anything that resolves a spec from it must be composable too. `ForagerTheme`
already supplies `MotionScheme.expressive()` (`Theme.kt`, step 2), so every
mapping below is against the expressive scheme unless Gate G question 4
reverses that (§2S records the `standard()` values alongside for exactly
that substitution).

### The category map

| Category (motion-spec.md §2) | `MotionTokens.kt` function | `MotionScheme` spec | Production caller today |
|---|---|---|---|
| Feedback motion | `feedbackMotionSpec<T>()` | `fastSpatialSpec` | None — scaffolding, same as before this ADR |
| Panels and navigation — panels | `panelMotionSpec<T>()` | `slowSpatialSpec` | Yes — `AddActionTile`, `AvailabilityScreen.kt`, 4 sites |
| Panels and navigation — nav chrome | `navigationMotionSpec<T>()` | `defaultSpatialSpec` | None — "never specced" before this ADR; §2's single "Panels and navigation" row is split below |
| Map markers | `markerEntranceSpec<T>()` | `defaultSpatialSpec` | None — scaffolding |
| Selection emphasis | `selectionPulseSpec<T>()` | `slowSpatialSpec` | None — scaffolding |
| Narrative reveals | `narrativeRevealSpec<T>()` | `slowEffectsSpec` | None — scaffolding |
| Data layer overlays | `dataLayerOverlaySpec<T>()` | `defaultEffectsSpec` | None — scaffolding |
| Routes | `routeRecalculationMorphSpec<T>()` | `slowEffectsSpec` | None — scaffolding |
| User location | *(none — see below)* | *(none)* | Yes — `SightingsMap.kt`, via `LOCATION_INDICATOR_MOVE_DURATION_MS` alone |

Two rows need their own explanation, because the design plan's own §3S
table (`understory-design-system.md`, "The motion replacement, in full")
does not resolve them and this ADR is where the resolution is recorded,
not left implicit in a diff:

**The single §2 row "Panels and navigation" splits into two.** §3S's table
gives panels (`slowSpatialSpec`, real caller, accepts mild overshoot) and
nav bar/rail/tab switch (`defaultSpatialSpec`, "chrome; no positional truth
to distort") different specs for a real reason — a nav switch overshooting
is a harmless flourish, a panel overshooting is felt on the app's primary
interaction surface — so one row in `MotionTokens.kt` would hide a
distinction §3S itself draws. `docs/motion-spec.md` §2 is updated to two
rows accordingly. Both are scaffolding today; the split exists so the
category is right when a caller arrives, not because a caller exists now.

**Narrative reveals has no row in §3S's table at all**, though §2 lists it
as a category on equal footing with the other seven, and it was one of the
eight original `tween`-based specs (`narrativeRevealSpec`, 900 ms). Mapped
here to `slowEffectsSpec`: "up to 800–1200 ms, interruptible" is the
longest duration among all eight categories, matching the "slow" tier by
the same relative-magnitude logic §3S applies elsewhere (compare
`markerEntranceSpec`'s old 250 ms landing on `defaultSpatialSpec`, not
`fastSpatialSpec`, despite being close in magnitude to feedback motion's
200 ms — category tier here is a taste call tied to the UI role, not a
mechanical duration-to-tier conversion). Effects, not spatial: a narrative
reveal is content fading in, not a bounds or position change, and "R1" above
is specifically the argument that licenses effects specs for this kind of
reveal. No caller exists to confirm the choice against; it is recorded as a
decision now rather than deferred to whoever writes the first caller,
consistent with this document's own treatment of the other five
scaffolding categories.

### The location puck: `locationIndicatorMoveSpec` deleted, `LOCATION_INDICATOR_MOVE_DURATION_MS` kept

**Unsupported, stated as unsupported.** `SightingsMap.kt` divides
`LOCATION_INDICATOR_MOVE_DURATION_MS` by MapLibre's own fixed internal
duration and passes the ratio to
`LocationComponentOptions.trackingAnimationDurationMultiplier` — a scalar
multiplier on a native animation, not a Compose `AnimationSpec`. There is
no interpolator to supply, so this is not a category with a spec at all;
it stays a plain duration constant, unwrapped. `locationIndicatorMoveSpec`
— the tween that resembled a driver for this but never was one — is
deleted with the other dead specs. If a future MapLibre version exposes an
interpolator, this row changes and gets recorded as an amendment here, not
a silent addition.

## Records: the panel change is a taste call, not a dissolved objection

Panels and sheets move to `slowSpatialSpec` at ζ = 0.8 (expressive), which
**does** overshoot from rest — underdamped by construction, unrelated to
the effects-spec argument above. This is a decision to **accept mild
overshoot on panels**, not a finding that ADR-0001's original concern about
panel motion was misplaced or overstated. The R1 effects-spec argument
justifies dropping the tween-only rule for alpha and colour and nothing
more; it says nothing about panels, and this ADR does not lean on it there.

**Amendment path, stated explicitly rather than left implicit.** The panel
damping value is provisional pending Gate G question 2. If the gate finds
the drawer spring reads as sloppy underfoot, the amendment is a spec
substitution on the panel row — to `defaultSpatialSpec`, or to the
`standard()` scheme's ζ = 0.9 equivalent (values below) — recorded as an
amendment to **this** ADR, not a new ADR and not a silent edit to the
table above.

## Records: `expressive()` as a reversible one-line parameter

`ForagerTheme` supplies `MotionScheme.expressive()` (`Theme.kt`). Gate G
question 4 asks whether that damping stays inside ADR-0001's precedence
order or whether `standard()` serves the calm requirement better. Both
schemes are `private object` singletons behind `MotionScheme.expressive()`/
`.standard()`, differing **only** in the spatial rows — the effects rows
are numerically identical between them:

| Spec | Expressive ζ / stiffness | Standard ζ / stiffness |
|---|---|---|
| `fastSpatialSpec` | 0.6 / 800 | 0.9 / 1400 |
| `defaultSpatialSpec` | 0.8 / 380 | 0.9 / 700 |
| `slowSpatialSpec` | 0.8 / 200 | 0.9 / 300 |
| `fastEffectsSpec` | 1.0 / 3800 | 1.0 / 3800 |
| `defaultEffectsSpec` | 1.0 / 1600 | 1.0 / 1600 |
| `slowEffectsSpec` | 1.0 / 800 | 1.0 / 800 |

So a reversal is a one-line substitution at `ForagerMotionScheme`'s
declaration (`Theme.kt`), not a re-derivation of any value in the table
above — every spatial row keeps its category mapping, only which scheme
resolves it changes.

## Restates, per R7: the continuous-animation budget

The 8–12 continuous-animation budget is a stated policy with **no
enforcement path** — unchanged in intent, unimplemented in fact, both
before this ADR and after it. `MotionPrecedence.kt` needs no code change
here and gets none. Recording it as "unchanged" alone would read as
evidence the budget is live; it is not, and this ADR states that plainly
so a future reader does not mistake silence for operation.

**Condition on first implementation, not on this modification:** whoever
writes the first caller that supplies object counts to `activeTiers()`
must derive those counts from a spring-aware running signal (e.g.
`Animatable.isRunning`), never from a timer or an elapsed duration. A
tween's "has it finished" and "has its duration elapsed" are the same
question; a spring's are not — its settling is asymptotic, not a fixed
endpoint — and a timer-derived counter would under-count long settling
tails and over-count objects that have already visually settled. Nothing
in this ADR implements that signal; it is a condition on the code that
eventually does.

## Records, per R1: the effects-spec interruption argument

Restated from Context above, as the record this ADR exists to keep:
effects specs (ζ = 1.0) cannot overshoot under interruption at all;
retargeting an in-flight animation to a new destination is the only
exposure; every animated alpha in the app today targets a bound (0 or 1);
and `scripts/verify-design-tokens.sh` check 4 now holds that property
rather than trusting it. **The clamp on `[0, 1]` is not presented as the
backstop** — the interruption proof above holds regardless of the clamp;
the clamp is what the check enforces against a *future* retarget to an
unbound intermediate value, which the proof does not cover.

## Records: the Sort asymmetry, and a correction to this document's own earlier count

`understory-design-system.md` §3S states this design "deletes six dead
animation specs." Re-verified directly against the tree at
implementation time (`grep -rn "<name>" app/src/main app/src/test`,
excluding each spec's own declaration and test file): **seven** are dead,
not six. The plan's own enumeration
(`feedbackMotionSpec`, `narrativeRevealSpec`, `markerEntranceSpec`,
`selectionPulseHalfCycleSpec`, `locationIndicatorMoveSpec`,
`dataLayerOverlaySpec`) omits `routeRecalculationMorphSpec`, which has
zero references anywhere outside `MotionTokens.kt` and
`MotionTokensTest.kt`, the same as the other six. `panelMotionSpec` is the
one spec with a real caller and is rewired rather than deleted, so the
total (8 original specs − 1 kept = 7 deleted) is internally consistent
once the omission is corrected. This is the same class of miscount this
project's design docs have produced before (see `understory-design-system.md`'s
own "`Spacing`, promoted" Decisions entry) — found by re-running the grep
against the tree directly rather than trusting the document's prior count,
per this repo's standing rule that a claim about the codebase names a file
and location or is stated as unverified.

The asymmetry itself is unchanged from what §3S already states, and this
ADR keeps it: **a curve carries no decision** — `markerEntranceSpec` being
`tween(250, EaseOut)` recorded nothing a reader needed, so deleting it (and
the other six, corrected count) loses nothing. **A precedence order does**
— `DEGRADATION_ORDER`, the 12-object ceiling, and the never-degrade rule on
the location indicator are ADR-0001's accepted decisions in executable
form, with `MotionPrecedenceTest.kt` pinning them, so `MotionPrecedence.kt`
is kept and marked unenforced (above) rather than deleted alongside the
curves. `EaseOut` (`CubicBezierEasing(0, 0, 0.2, 1)`), the easing every
deleted tween shared, is deleted with them for the same reason a curve
carries no decision — nothing references it once the two raw inline
`tween()` calls in `AddActionTile` (`AvailabilityScreen.kt`, the
`expandIn`/`shrinkOut` animations) are replaced by
`navigationMotionSpec`/`panelMotionSpec`-family calls below.

## Adds: a Reduce Motion mapping for springs

`ReducedMotionTreatment` already maps every `MotionTreatment` to a
still-visible equivalent rather than to nothing (`ReduceMotion.kt`,
`docs/motion-spec.md` §4) — `PANEL_MOTION` → `FADE_OR_INSTANT`, and so on.
That mapping is a statement about what a *reduced-motion user sees*, not
about which `AnimationSpec` drives the full-motion version, so it needs no
code change to keep covering `panelMotionSpec` now that the spec behind
`PANEL_MOTION` is a spring instead of a tween: springs map the same way a
tween did, to a still-visible equivalent, never to a silently dropped
state change. `ReduceMotionTest`'s existing mapping-table assertions
(`reduce motion mapping matches the spec table exactly`) already exercise
this and needed no change either. No `MotionTreatment` entry exists yet
for the five scaffolding categories above (none has a production caller),
so none is added — the same "don't build unwired mapping for a category
with no caller" restraint this ADR applies to the specs themselves.

## Unchanged

- **Precedence order** (legibility → performance → calm) and the
  **degradation order** — `MotionPrecedence.kt` untouched.
- **The Reduce Motion mapping table** — `ReduceMotion.kt` untouched, per
  the section above.
- **The scope boundary.** No confidence score, candidate list, species
  suggestion, or per-observation harm assessment enters the motion layer
  anywhere in this ADR or the code implementing it.

## Rejected alternative: the chrome-versus-map split

An earlier draft proposed keeping map-adjacent motion on tweens and
chrome on springs, to protect the location puck from spring overshoot.
The puck was never spring-driven — it is a MapLibre scalar multiplier, not
a Compose `AnimationSpec` (see above) — so there was nothing to protect,
and the split would have left two motion vocabularies in a codebase that,
before this ADR, used exactly one call site's worth of either.

## Rejected alternative: tween-only for panels, springs everywhere else

Coherent, and the literal reading of ADR-0001's original line. Rejected
because it makes the app's primary interaction surface the one category
that does not respond to interruption with velocity continuity — the
property springs exist for — while every scaffolding category around it
gets to use it. Rejected as a taste call, on the record, and it is exactly
what Gate G question 2 exists to check on hardware rather than settle by
argument alone.
