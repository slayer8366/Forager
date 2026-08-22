# Error Presentation Specification

**Repo:** native Kotlin/Compose Android app, `app/src/main/java/com/forager/app/...`
**Audience:** implementing engineer or coding agent.
**Status:** draft for review, v2 — revised against the 2026-08-22 pulse. Supersedes part of PR #31's rendering.

## Scope boundary — read first

This spec governs **how the app tells the user something didn't work**. It does not add, remove, or change what the app knows, computes, or fetches.

It does not touch, and must not be read as touching:

- Identification, confidence scoring, or edibility — excluded everywhere in this repo (`MushroomLogEntry.kt`, `docs/plans/forager-navigator-plan.md` §7, `docs/motion-spec.md` §6b). No message defined here says anything about a specimen.
- The permission-request flow — upfront vs. in-context is an open owner decision with its own dispatch. This spec covers what the user is *told* when a permission is absent, not *when* the app asks.
- Map waypoint interaction — tapping a waypoint marker currently does nothing. That is a missing feature with its own scope, not an error-presentation problem.

---

## The problem

Observed in field testing and confirmed by pulse:

1. **The app renders raw exception text.** The List tab showed `Unable to resolve host "api.inaturalist.org": No address associated with hostname` verbatim in a red banner — `AvailabilityViewModel.kt:393`'s `error.message ?: "Request failed..."`, where the `?:` fallback loses whenever `message` is non-null, which is nearly always.

2. **Error framing is applied to things that aren't the user's problem.** A failed rainfall fetch is reported as a failure. What the user wanted was rainfall.

3. **A failed waypoint save is completely invisible.** Confirmed by pulse: `addWaypoint`'s failure path sets `waypointsErrorMessage` and nothing else. The list is untouched (`loadWaypoints()` runs only on success), and the field is read nowhere. The user drops a waypoint, sees no pin and no row, and cannot distinguish "didn't save" from "didn't register my tap." A failed delete leaves the waypoint present with no explanation.

The owner's framing: *the honesty focus is on the data we provide that they're interested in — they're not downloading an app to see how much it can fail.* Correct, and it needs a rule sharp enough to apply, because it does **not** mean "hide everything." Item 3 is what over-hiding looks like.

---

## The rule: does this change what the user believes the app is doing for them?

**No → absorb it.** The user just doesn't have a piece of data. Render an honest empty state. No error language, no failure narration, no error color.

**Yes → tell them, as state.** The user believes something is happening that isn't. They learn *what the app is doing*, never *what went wrong inside it*.

The second category is not negotiable down to silence. A user who believes they are recording a track, or that a waypoint is saved, and is not, is carrying a false assumption into the woods.

### Absolute rule: no user-facing string is ever derived from an exception

Not at the ViewModel, not at the repository, not anywhere.

The earlier draft said "the passthrough stops at the ViewModel." The pulse showed that is insufficient: `MapLibreOfflineMapRepository.kt:253-263` constructs an `IOException` whose message concatenates MapLibre's own `OfflineRegionError.reason`/`.message` — SDK diagnostic text, manufactured a layer *below* the ViewModel. Blocking `error.message` at the ViewModel would just pass through a different technical string it didn't build.

No exception text, class name, hostname, HTTP status, or SDK diagnostic reaches the UI, at any layer.

**`MushroomLogViewModel` is the model to follow.** Pulse confirmed it already does this correctly: every `onFailure` logs the throwable (`Log.w(TAG, "...", error)`) and writes a fixed literal to state. The exception is kept for diagnosis and discarded from the user-facing string. Ten sites across `AvailabilityViewModel` and `TrackRecordingViewModel` should look like this.

Failures still need diagnosing — that is what the crash handler, `Log.w`, and `docs/audits/` are for. The user-facing surface is not a diagnostic channel.

---

## Per-field treatment

| Field | Belief-changing? | Treatment |
|---|---|---|
| `conditionsErrorMessage` | No | Empty state: "Rainfall data unavailable." Neutral color. |
| `loadErrorMessage` | No | Empty state in both log surfaces. Must not imply entries were lost — data is on disk, the read failed. |
| `saveErrorMessage` | **Yes** | Toast. Clears on dismiss or next successful save, whichever comes first. Provisional — see below. |
| `waypointsErrorMessage` | **Yes** | Needs a message. Absence is not a signal here — see below. |
| `startRecordingErrorMessage` | **Yes** | Already correct in PR #32: "Track recording needs location access." Reference example. |
| `errorMessage`, `taxonSearchErrorMessage`, `sightingsErrorMessage`, `seasonalPatternErrorMessage`, `tripWindowsErrorMessage`, `plannedTripsErrorMessage`, `offlineMapStatus` | Mixed | Already rendered; all currently carry exception passthrough. Fix the passthrough; keep existing render sites and placement. |

### `waypointsErrorMessage` needs a message

An earlier draft suggested presence might suffice — a saved waypoint appears, a failed one doesn't. **The pulse disproves this.** Absence of a pin is ambiguous with never having tapped, so silence reads as an unresponsive app rather than a failed save. Both add and delete failures need a message.

Delete is the sharper case: the waypoint stays visible, which actively signals success.

### `saveErrorMessage` — Toast, provisionally

Chosen to test whether transient dismissal is sufficient for a failed save. **Revisit if misses are observed in field use** — the alternative is an inline banner with an explicit dismiss control, which is harder to miss but adds a control to a surface that has none today.

The clearing rule (dismiss or next successful save, whichever first) is what makes this field renderable at all; it currently has five write sites and zero `= null`.

---

## Empty-state vocabulary

The pulse found three existing patterns, not two:

1. **Nothing searched yet** — neutral, "Choose a region..." (`:2759-2763`, `:2497-2500`)
2. **Sensor value unresolved** — neutral, "X unavailable" ("Compass unavailable", "Elevation unavailable", "Coordinates unavailable", "MGRS unavailable")
3. **Fetch failed** — `colorScheme.error`, "Couldn't load..." or raw exception text

"Unavailable" is therefore already this codebase's vocabulary for a missing value — but only for live-sensor gaps, styled neutral.

**This spec deliberately extends pattern 2 to cover non-belief-changing fetch failures.** "Rainfall data unavailable" is not new wording; it is an existing convention applied to a new category, replacing the error-red treatment. State this in the implementation so it reads as intentional rather than inconsistent.

Match the existing neutral styling — no `color` argument, inheriting default content color, as `WaypointsSection` (`:3853-3860`) and `MapMessage` (`:3958-3961`) already do.

---

## Wording rules

- **State, not failure.** "Not recording" over "Couldn't start recording."
- **Say what the feature needs**, when the user can act on it. A grantable permission is actionable; a DNS failure is not.
- **No apology, no blame, no internal narration.** Not "Sorry", not "We couldn't", not "The server returned".
- Empty states describe the absence, not the cause: "Rainfall data unavailable" — not "Rainfall data couldn't be loaded."
- All strings in `strings.xml`, flat feature-prefixed convention (`track_recording_needs_location` is the model).

---

## What this changes in PR #31

PR #31 renders `conditionsErrorMessage`, `loadErrorMessage`, and `waypointsErrorMessage` as inline `Text` with `colorScheme.error`. Under this spec the first two become neutral empty states, and its `startRecordingErrorMessage` Toast wiring is superseded by PR #32.

**PR #31 is rebased after this spec is implemented, not before.**

---

## Corrections to earlier drafts

- The redundant-encoding rule is `docs/motion-spec.md` **§4**, not §5. §5 is Field Test Conditions.
- `RecordToggleButton` **already satisfies** the redundant-encoding rule: state is carried by both color and icon shape (square vs. circle), plus a changing `contentDescription`. No change needed. It has no `Role` or `stateDescription` semantics — noted, not in scope here.

---

## Open questions — for the owner, do not guess

1. **`RecordToggleButton` legibility in the field** — unambiguous at a glance, in sunlight, one-handed? Code reading cannot answer this (`docs/motion-spec.md` §5, Field Test Conditions).
2. **Weather expansion** — humidity, current temperature, day forecast are available from Open-Meteo and unused. Scoped separately; noted because it changes what the conditions card's empty state is an empty state *of*.
3. **Map waypoint interaction** — tapping a marker does nothing today. Separate scope; needs a decision on what a tap should do.
