# ADR 0002: Adopt `MotionScheme.expressive()`, superseding ADR-0001's tween-only rule for spatial motion

## Status

Written as part of an exploratory, unmerged build-out of
`docs/plans/understory-design-system.md` (see that document's own
"Decisions" section for the gating this ADR is written under: approved to
write, not approved to start the compact finish pass until R1 and R7 were
answered — both are, below). **Gate G — the owner-run device pass this ADR's
provisional values depend on — has not been run.** This session has no
physical hardware to run it on. Every value below marked *provisional*
stays provisional until that gate runs; nothing here should be read as a
final, hardware-confirmed decision.

## Context

ADR-0001 is accepted for the precedence order and degradation model.
`docs/motion-spec.md` §2 said, in as many words, "Panels and navigation —
ease-out, grounded; no springy overshoot," and `MotionTokens.kt` enforced
it mechanically: every named spec was a `tween`, and `MotionTokensTest.kt`
asserted that directly, failure message "spring-based specs can
overshoot." That rule is not quietly overwritten by a styling pass — this
ADR is the supersession, with its rejected alternatives written down.

Two questions blocked drafting this ADR until they were answered:

### R1 — what an effects spring actually guarantees

The claim under review went through two wrong versions before landing on
the right one:

1. **First version (overstated):** "effects specs are mathematically
   incapable of overshoot." False — critical damping guarantees no
   overshoot for a step from rest, not under nonzero initial velocity.
2. **Second version (overstated the other way):** restated as
   "interruption-carried velocity is bounded by the clamp on the animated
   property." Also not quite right — this implied interruption itself was
   the hazard the clamp had to rescue.
3. **Settled version:** for a unit-mass, critically damped (`ζ = 1`)
   spring, `x(t) = (x₀ + (v₀ + ωx₀)t)e^(−ωt)`, `ω = √k`. This crosses zero
   — overshoots — only when `v₀` opposes `x₀` **and** `|v₀| > ω|x₀|`.
   Evaluated along the system's *own* from-rest trajectory
   (`v(t) = −x₀ω²t·e^(−ωt)`, `x(t) = x₀(1+ωt)e^(−ωt)`):

   ```
   |v| / (ω|x|)  =  ωt / (1 + ωt)
   ```

   which is strictly below 1 for every finite `t`. **A critically damped
   spring can therefore never overshoot its own target from any point on
   its own path.** Interrupting an in-flight effects animation and letting
   it continue toward the *same* target is provably safe, always.
   Interruption is not the hazard.

   **Retargeting is.** `x₀` is the distance to the *new* target after a
   retarget; a retarget nearby and in the direction of travel collapses
   `|x₀|` while `|v₀|` holds, so the ratio above crosses 1 easily. The
   hazard is a moving destination, not a moving object.

**Checked against the code**, since this ADR was about to lean on the
clamp argument: `app/src/main` has no `Animatable`, `animateFloatAsState`,
`animateColorAsState`, or `updateTransition` anywhere. The only animated
alpha is `AnimatedVisibility`'s fade in `AvailabilityScreen.kt`, using
default `initialAlpha`/`targetAlpha` (0 and 1) at every call site — so
every animated alpha in the app today targets a bound, and the clamp holds
for all of them. But that is a property of *today's call sites*, not of
the spec: `fadeIn(initialAlpha = …)`/`fadeOut(targetAlpha = …)` accept
non-bound values as parameters, so the first overlay anyone writes with an
intermediate target is the first chance to retarget into the unsafe case.
`verify-design-tokens.sh` gained a check for this (§4S) so the property is
asserted rather than assumed.

**Conclusion, and its actual scope:** the R1 argument justifies dropping
the tween-only rule **for alpha and colour (effects specs)**. It says
nothing about panels, and this ADR does not use it as though it did — see
"Decision," below.

### R7 — does the continuous-animation budget assume bounded durations?

`MotionPrecedence.kt` has no Compose or Android import and contains no
duration of any kind. `activeTiers()` takes a count of objects per
`DegradationTier` and drops tiers in `DEGRADATION_ORDER` until the
remaining counts fit `MAX_CONTINUOUS_ANIMATED_OBJECTS = 12`. The
accounting is over object counts, not elapsed time, so it survives this
ADR unchanged in that specific sense.

**But the budget has no production caller.** `activeTiers`,
`shouldClusterMarkers`, `MAX_CONTINUOUS_ANIMATED_OBJECTS`, and
`DEGRADATION_ORDER` are referenced only by `MotionPrecedence.kt` itself and
its own test. Nothing supplies the counts. The 8–12 budget has therefore
never been enforced. This ADR records that plainly rather than filing it
under "kept unchanged," which would read to a future reader as evidence
the budget is live:

> The 8–12 continuous-animation budget is a stated policy with no
> enforcement path today — unchanged in intent, unimplemented in fact.

**Constraint on whoever wires it first:** the count must come from a
spring-aware running signal (e.g. `Animatable.isRunning`), never from a
timer or elapsed duration. With a `tween`, "has it finished" and "has its
duration elapsed" are the same question; with a spring they are not — a
timer-derived counter would under-count long settling tails and
over-count objects that have actually settled. This is where
duration-vs-asymptote actually bites, and it bites whoever writes the
first caller, not this ADR.

## Decision

Adopt `MotionScheme.expressive()` as `ForagerTheme`'s motion scheme
(already landed — Understory step 2), and rewrite `MotionTokens.kt` as a
named map onto its six specs (Understory step 4, this ADR).

**Changes:**

- §2's "no springy overshoot" line for panels, split into two categories
  that now take different specs (`docs/motion-spec.md` §2, updated
  alongside this ADR): navigation chrome (`defaultSpatialSpec`) and
  sheets/drawers/dialogs/panels (`slowSpatialSpec`).
- The tween-only rule in `MotionTokens.kt`'s class comment — this file no
  longer defines any `tween`-based spec at all; every accessor is a
  pass-through onto `MaterialTheme.motionScheme`.

**Recorded as a taste call, not a dissolved objection.** Panels and sheets
move to `slowSpatialSpec`, expressive damping `ζ = 0.8` — which *does*
overshoot on a step from rest. This is a decision to **accept mild
overshoot on panels**, not a finding that ADR-0001's original concern was
misplaced. The R1 argument above justifies dropping the tween-only rule
for alpha and colour and nothing more; this ADR does not lean on it for
panels. **Provisional pending Gate G** (see Status). If the device gate
finds the drawer spring reads as sloppy, the amendment path is a spec
substitution on the panel row — to `defaultSpatialSpec`, or to
`standard()`'s `ζ = 0.9` equivalent — recorded as an amendment to *this*
ADR, not a new ADR and not a silent edit.

**`expressive()` over `standard()` is itself provisional**, resolved at
Gate G question 4. Recorded here as a reversible one-line parameter; the
`standard()` damping/stiffness values live alongside `expressive()`'s own
in `docs/plans/understory-design-system.md` §2S, so a reversal is a
substitution, not a re-derivation:

| Spec | Expressive ζ / k | Standard ζ / k |
|---|---|---|
| `fastSpatialSpec` | 0.6 / 800 | 0.9 / 1400 |
| `defaultSpatialSpec` | 0.8 / 380 | 0.9 / 700 |
| `slowSpatialSpec` | 0.8 / 200 | 0.9 / 300 |
| `fastEffectsSpec` | 1.0 / 3800 | 1.0 / 3800 |
| `defaultEffectsSpec` | 1.0 / 1600 | 1.0 / 1600 |
| `slowEffectsSpec` | 1.0 / 800 | 1.0 / 800 |

The two schemes differ only in the spatial rows — effects specs are
identical between them, which is why R1's argument (about effects specs
specifically) holds regardless of which scheme wins at Gate G.

**Restates rather than preserves, per R7:** the 8–12 budget is recorded as
a stated policy with no enforcement path — unchanged in intent,
unimplemented in fact. The spring-aware counting constraint above is a
condition on first implementation, not on a modification of something
already running.

**Records, per R1:** effects specs cannot overshoot under interruption at
all; retargeting is the only exposure; every animated alpha in the app
today targets a bound; `verify-design-tokens.sh` now asserts that rather
than trusting it. The clamp is **not** the backstop this ADR relies on.

**A correction this ADR makes to the design doc's own earlier count,**
verified against `MotionTokens.kt`'s actual callers rather than carried
over: the doc that proposed this rewrite counted "eight specs, six dead,
`panelMotionSpec` the one live one." `routeRecalculationMorphSpec` also
had zero production callers at that time — the true count was seven dead,
one live. Both tallies agreed on deleting the same specs in the end
(`routeRecalculationMorphSpec`'s tween is gone, replaced by
`narrativeRevealSpec`), so nothing was built differently because of the
miscount, but the record should say what was actually true rather than
what a prior pass believed was true.

**A second correction, discovered while doing the compact finish pass
(Understory step 5) rather than while drafting this ADR:** `panelMotionSpec`
itself no longer has a production call site either. Its one usage
(`AddActionTile`'s hand-built `AnimatedVisibility` scrim-and-card) was
replaced by a real `FloatingActionButtonMenu`, whose motion is
`MotionScheme.expressive()` by way of `MaterialExpressiveTheme` — not a
named spec in this file at all. Stock M3 components already read
`MaterialTheme.motionScheme` internally (`ModalNavigationDrawer`,
`AlertDialog`, `FloatingActionButtonMenu`), so most of "panels accept mild
overshoot" is now enacted by the framework automatically, the moment
`ForagerTheme` supplies `MotionScheme.expressive()` — not by
`MotionTokens.panelMotionSpec`. That name is kept anyway: it is the
documented policy for the one case framework wiring doesn't reach — a
panel, sheet, or dialog this app still hand-builds — so a future call site
reaches for a named accessor rather than an unlabelled spring growing back
one call site at a time.

**Adds:** a Reduce Motion mapping for springs. `ReducedMotionTreatment`
already maps every treatment to a still-visible equivalent rather than to
nothing (`docs/motion-spec.md` §4); springs map the same way, to `snap()`
or a cross-fade, never to a silently dropped state change. No code change
was required for this — `ReduceMotionTest.kt`'s existing mapping table
already names a reduced equivalent per `MotionTreatment`, independent of
whether the full-motion version is a tween or a spring.

**Unchanged:** the precedence order (legibility → performance → calm), the
four-tier degradation order, and the scope boundary — no confidence score,
no candidate list, no per-observation harm assessment enters the motion
layer, and nothing here takes such a field as input. `MotionPrecedence.kt`
needs no change.

**Rejected alternative: the chrome-versus-map split** from an earlier
draft of the design this ADR implements. It protected the location puck
from spring overshoot; the puck was never driven by a Compose
`AnimationSpec` in the first place (`SightingsMap.kt` divides
`LOCATION_INDICATOR_MOVE_DURATION_MS` by MapLibre's own internal duration
and passes the ratio to a native scalar-multiplier API — there is no
spring or easing to supply). Rejected because it protected an animation
that was never at risk, and would have left two motion vocabularies in a
codebase that now uses one.

**Rejected alternative: keeping tween-only for panels while adopting
springs everywhere else.** Coherent, and the more literal reading of
ADR-0001. Rejected as a taste call, on the record, subject to Gate G: it
would make the app's primary interaction surface the one thing that does
not respond to interruption with velocity continuity, which is the
property springs exist for.

## Unsupported, stated as unsupported

The location puck cannot take a spring. MapLibre's
`LocationComponentOptions.trackingAnimationDurationMultiplier` accepts a
scalar multiplier on a fixed internal duration; there is no interpolator
to supply. `LOCATION_INDICATOR_MOVE_DURATION_MS` survives as a plain
duration constant — it was never a `TweenSpec` to begin with, and the
`locationIndicatorMoveSpec` accessor that looked like it drove the puck is
deleted along with the other dead specs. If a future MapLibre version
exposes an interpolator, this row changes and gets an amendment to this
ADR, not a silent edit.
