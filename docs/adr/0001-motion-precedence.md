# ADR 0001: Motion precedence order, and the confidence/harm exclusion

## Status

Accepted for the precedence order and degradation model. The two
constants flagged "provisional" below (marker clustering threshold,
CODEOWNERS reviewer) are explicitly not yet accepted — see
[`docs/motion-spec.md` §6](../motion-spec.md#6-open-questions).

## Context

Forager is used outdoors, one-handed, in direct sun or deep shade, on
mid-range hardware, sometimes offline. Motion has to compete with all of
that before it can be judged on how it looks. A prior draft of this
spec also modeled per-observation identification confidence and harm as
inputs to motion emphasis; that draft was rejected (see "Rejected scope"
below) before any of it was implemented.

## Decision: precedence order

When motion principles conflict, resolve in this order:

1. **Legibility** — readable under glare, wet screens, gloves, one-handed
   while walking.
2. **Performance** — battery, frame rate, offline resilience.
3. **Calm / ecological metaphor** — organic motion, peripheral awareness.

Rationale: a legible-but-inelegant treatment still does its job outdoors;
an elegant-but-illegible one does not. A smooth-but-battery-draining
treatment degrades into an illegible one anyway once the frame rate or
the battery gives out, so performance is a precondition for legibility
holding up over a session, not a peer concern to it. Calm/ecological
motion is real product identity, but it is the layer that gets minimized
first, since minimizing it never makes the app harder to use — it just
makes it plainer.

**Rejected alternative:** calm/ecological-metaphor-first, treating
outdoor conditions as a degradation case rather than the baseline. This
project's whole premise is outdoor field use; treating outdoor legibility
as an edge case rather than the default gets the design brief backwards.

**Rejected alternative:** performance-first with no explicit legibility
priority. Performance without a legibility floor can still ship an
animation that is smooth and unreadable in direct sun — the budget
doesn't guarantee the result is usable, only that it's cheap.

## Decision: degradation order

Under load or battery-saver, degrade in this order: decorative continuous
loops → route progressive detail → marker entrance animations → location
indicator. The location indicator is the one thing this spec allows to
run continuously and it degrades last, because losing it removes the
answer to "where am I," which is a legibility failure, not a calm one.

**Rejected alternative:** degrade uniformly (reduce every animated
element's fidelity by the same amount under load) rather than in a fixed
order. A uniform reduction has no floor — every element gets slightly
worse together, including the location indicator, rather than protecting
the one thing legibility depends on. An ordered list gives a single place
(`MotionPrecedence.DEGRADATION_ORDER`) that degradation logic reads from,
per the precedence order above.

## Decision: taxon/observation state stays out of motion

**Rejected scope:** an identification-confidence and harm-potential state
machine (model score, community confirmations, look-alike lookup →
resolved confidence/harm → motion emphasis), from an earlier draft of
this spec.

Rejected because it conflicts with two decisions already recorded in this
repository, not because it was infeasible to build:

- `MushroomLogEntry.kt`'s doc comment records a stated safety property
  from the project owner: the type never identifies the mushroom, and no
  field is or feeds a species suggestion, candidate list, "likely," or
  confidence score.
- `docs/plans/forager-navigator-plan.md` §7 lists "AI identification or
  edibility advice" under "Deferred indefinitely."

Building a confidence/harm resolver — even as inert, unwired scaffolding
— would have encoded that feature's data model ahead of an actual product
decision to build it. This spec and the code implementing it define
motion behavior only: durations, easings, degradation, and Reduce Motion
mapping. It carries no field that represents identification confidence or
per-observation harm, and takes no such field as input.

## Provisional constants

Two values in `MotionPrecedence.kt` are named constants today but are
explicitly not yet validated, and are called out as open in
`docs/motion-spec.md` §6 rather than presented as settled:

- `MARKER_CLUSTERING_THRESHOLD` — set to the low end of the 8–12
  continuous-animation budget as a starting point for tuning, not a
  measured value. Requires validation against dense-map fixtures on
  target hardware before it should be treated as final.
- The `CODEOWNERS` reviewer for `ui/motion/**` and `docs/motion-spec.md`
  is a placeholder handle, not an assigned person or team.

Both must be resolved by a human before this ADR's status can move from
"Accepted (precedence/degradation only)" to fully accepted.
