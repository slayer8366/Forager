# Pre-registration — source package rename: the §6 prediction

**Written before any test run on the renamed tree.** §6 requires the Windows-failure prediction be
worked out and written down first, and that this commit precede the run is the evidence it was.

**Base:** stacked on `claude/gpx-namespace-domain` (PR #92 head `84f5437`) — see the completion
report's disclosure for why, rather than merging #92 first as §7.1 orders.

---

## The question §6 asks

> whether `namespace` contributes to that path independently of `applicationId`, or whether
> `applicationId` already accounts for it.

## The answer, and the reasoning, before the data

**`applicationId` already accounts for it. `namespace` contributes nothing to that path.**

The failing path is a **database** path, obtained from `context.getDatabasePath(name)` inside the
migration tests. Under Robolectric that resolves beneath the application's data directory, which is
keyed on **`context.getPackageName()`** — and `getPackageName()` returns the **`applicationId`** from
the merged manifest, not the Gradle `namespace`.

`namespace` is a build-time construct: it sets the R and BuildConfig package and the package
attribute used during compilation. It is not what the platform reports at runtime. That is precisely
the distinction PR #92's report relied on when it left `namespace` alone while moving `applicationId`
— and the controlled perturbation there confirmed the runtime path tracks `applicationId`, because
moving `applicationId` by +12 characters moved the boundary by exactly 12.

`applicationId` is **unchanged** by this dispatch (§3 lists it under "explicitly not changing").

## The prediction

> **No change. The Windows local failure count stays at 12** — the 11 migration classes plus
> `AvailabilityScreenSettingsPanelTest`'s FileProvider test.

Two independent reasons, either sufficient:

1. **The path does not grow**, because the component that determines it (`applicationId`) does not
   change.
2. **Even if it grew, nothing is left to flip.** All 11 migration classes already fail after PR #92.
   There is no class below the boundary remaining to cross it.

Reason 2 is why this prediction is weaker evidence than PR #92's was, and that is worth saying in
advance: **a "no change" outcome is consistent with both "namespace contributes nothing" and
"namespace contributes but there was nothing left to flip."** This run cannot separate those. Only an
outcome of *more* than 12 would be discriminating, and it would falsify the prediction.

## What each outcome would mean, fixed now

| outcome | reading |
|---|---|
| **12 failures** | consistent with the prediction, but **not confirmation** — see reason 2. Report as "as predicted, and non-discriminating." |
| **more than 12** | **prediction wrong.** `namespace` reaches the runtime path by some route not accounted for here. A real finding; report it, do not reconcile it. |
| **fewer than 12** | prediction wrong in the other direction, and surprising — the path would have to have *shrunk*. Report and investigate rather than accept. |

## Unchanged claim

**The threshold value is still unmeasured.** The mechanism is established by PR #92's perturbation;
that the limit is 260 is not, and this dispatch does not upgrade it.
