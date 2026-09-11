# Way-back route: owner decisions, and two things the code already had

**Date:** 2026-09-11
**Follows:** [`2026-09-11-way-back-route-prebuild-report.md`](2026-09-11-way-back-route-prebuild-report.md)
**Status:** five of six decided. D3 is open, expanded below at the owner's request. No code written.

---

## The decisions

| # | Question | Owner's call |
|---|---|---|
| D1 | What does the needle point at? | A point **along the route**, distant but not too distant. Never the destination directly, and never off the path |
| D2 | Which destination? | **All of them, as separate modes.** Waypoint navigation is toggled *instead of* return-to-start, never alongside it, and **overrules it when active**. Waypoints are not navigated automatically; navigating to one is an option offered when selecting it from the map, or from cartography/records |
| D3 | Route distance primary, or beside the straight line? | **Open.** Expanded below |
| D4 | Recompute cadence | **5 s to start**, to see how it behaves |
| D5 | Behaviour when the walker is off the route | **Withhold.** "We don't know if it's going to be a straight line, they may not see it before they turn" |
| D6 | Draw the route when it equals the track? | **Draw it, yes** |

## Two things already in the code that change the size of this

### D2 is stage two, and stage two was left a seam

The owner's answer to D2 is larger than the question, which asked only which of three destinations
is "back". The answer is a mode system. That system was already anticipated.

`AvailabilityScreen.kt:728-734` defines the navigation predicate once and says so:

> THE navigation predicate — defined once, here, and nowhere else (navigation-chrome dispatch,
> item 1). Everything that means "a navigation mode is active" reads this: the HUD's presence, the
> compass strip's absence, and which waypoints the map shows. **Stage one has exactly one mode, the
> return leg, so this is isReturning; stage two's target picker ORs its own state into this one
> line**, and the strip, the HUD and the map cannot drift apart because none of them was ever
> written against isReturning directly.

`navigationTarget: Waypoint?` is already threaded separately (`:650`) and already decides both what
the HUD points at and which waypoints the map draws, through `mapVisibleWaypoints` (`:737-739`).

So the mode system is a one-line OR into `isNavigating` plus a picker and its entry points, not a
refactor of the chrome. "Waypoint overrules return-to-start when active" lands as: when the
picker's target is set, it wins for `navigationTarget`.

**A consequence worth stating, because it decides what follows the route and what does not.** A
waypoint the walker chose from the map may be nowhere near the recorded track, so there is no
walked path to it. Route-following therefore belongs to **return-to-start only**; waypoint
navigation stays the straight-line bearing that exists today. That is not a compromise. It is the
honest answer for each mode, and it means the existing straight-line code is kept for the mode it
serves rather than replaced.

### D1's figure has two ends already ruled on

The owner said "distant but not too distant" and named no number, correctly — nothing here measures
one. But the lookahead segment is a **straight line from the walker to a point standing in for
walked ground**, which is the exact thing `PathHome` already has thresholds for:

- Below **`HOP_ENTER_ABOVE_METERS` = 25 m** (`PathHome.kt:164`), a straight line of that length is
  inside the noise of the fixes it is measured from. A needle aimed at something that close is
  aimed at where the walker already is.
- Above **`HOP_FAR_ENTER_ABOVE_METERS` = 50 m** (`PathHome.kt:170`), a straight line "stops being a
  fair proxy for what will actually be walked". That figure is itself derived from the live-fix
  gate's ceiling, not chosen.

So the bracket is **25 m to 50 m**, and both ends are existing rulings rather than invented numbers.
At the project's own default walking pace (`DEFAULT_MOVING_SPEED_KM_PER_HOUR = 3.2`, 0.89 m/s) that
is roughly 28 to 56 seconds of lead.

**Proposed start: 25 m**, the near end, explicitly provisional and tuned from the 5 s observation
D4 asks for. The reasoning for starting near rather than far: a too-near lookahead fails visibly, as
a jittering needle, and the owner will see it within a minute. A too-far lookahead fails quietly, by
aiming across a bend into whatever is inside the corner, which on a forest path is the failure that
actually costs something. Starting at the end whose failure is loud is the cheaper experiment.

Not decided here. Put to the owner.

## D3, expanded

**The question.** The HUD's large figure is the straight line to the target
(`NavigationHud.kt:362`). The along-track figure lives in the status line as "Path home NNN m"
(`:407`) and is **evicted by "Approaching"** near the target (`:411`). Once the walker is navigating
a route rather than a bearing, which number is the headline?

### Why this is not just moving a value

`distanceText` is built by `formatDistanceWithAccuracy` (`:369`), which says "within 16 ft" inside
the fix's error circle and "≈ 10 m" beyond it. That is meaningful for one straight line from one fix
to one point. It is not meaningful for a sum over hundreds of stored points, and the code already
says so at `:405`:

> formatting, not the accuracy-aware kind: this is a sum over many stored points, not one fix's
> radius.

So the two numbers are not interchangeable presentations of the same quantity. They carry different
error semantics and are formatted differently on purpose.

### What each number actually answers

- **Straight line:** *how far away am I?* Useful for judging whether the car is close enough to see,
  and it is the one number that cannot be wrong.
- **Route distance:** *how much walking is left?* This is what the sundown countdown consumes, and
  it is what the walker is about to do.

When the two diverge sharply, the divergence is itself the information: it means the direct line is
not walkable and the long way round is the way home.

### The options

**A. No change.** Straight line stays primary, route distance stays in the status line.
Cheapest. Against it: once D1 and D6 land, the walker is following a drawn route and a needle that
tracks it, while the biggest number on screen describes a path they are explicitly not taking. On a
forest loop that number can be a third of the real walk, presented as the headline. That is the
confidently-short figure `PathHome` exists to avoid.

**B. Route distance becomes primary; the straight line moves to the status line.** Matches what is
being navigated. Costs: the primary slot loses accuracy-aware formatting, because the route distance
cannot honestly carry it.

**C. Both, side by side and labelled** — "Path 340 m · direct 111 m". Honest and complete. Costs
horizontal room in a strip already carrying heading, target, needle, distance, status, elevation and
coordinates.

**Recommendation: B**, with the straight line kept in the status line rather than dropped, and
accuracy-aware formatting staying with the straight line where it belongs.

### Two states B has to answer, and they are not details

1. **What replaces "Approaching"?** Today the along-track figure is evicted at close range. If it
   becomes primary, the eviction has to be redesigned rather than inherited.
2. **What does the primary slot show when D5 withholds the route?** It has to fall back to the
   straight line, and the walker has to be able to tell that the meaning changed. A number that
   silently switches from "your walk home" to "how far away the car is" is worse than either.

---

## Disclosure

### Confirmed by reading the code
`AvailabilityScreen.kt:728-734` and `:650`, `:737-739`. `NavigationHud.kt:362`, `:369`, `:405`,
`:407`, `:411`. `PathHome.kt:164`, `:170`. Every quoted comment is verbatim.

### Could not be determined
Whether 25 m is the right lookahead. It is a bracket derived from existing rulings, not a measured
figure, and D4's 5 s cadence exists precisely to find out.

### Premises that were wrong
Mine, in D2. It was framed as a choice between three destinations, which assumed one navigation
mode. The owner's answer is two modes, and the seam for the second was already in the code with a
comment naming it stage two. Reading the predicate's own doc before writing the question would have
framed it correctly.

### Decided beyond scope
Nothing. D3 is left open and D1's figure is proposed rather than taken.
