# Pulse: PR #95, before it ships underneath #96

**Date:** 2026-09-12. **Subject:** PR #95 at head `a5668b4`. **Read only** — nothing was changed,
fixed or proposed. This document is the report; the pulse said commit nothing, and the owner
afterwards ruled that a report living only in a transcript is the thing `CLAUDE.md` warns against.

**Why it existed.** #96 is stacked on #95, so merging the stop fix merges the sundown work with it.
There is no bundle carrying one without the other short of a rebase or a cherry-pick, both ruled out
here. #95 was shipping unreviewed.

---

## What it is

**29 files, 22 commits, +3363 / −12.** Twelve main-source files, six test files, ten audit documents
plus the index and the privacy policy.

The sundown countdown in five slices: `SunCrossing` (sunset by bisecting the existing solar-altitude
function), `ComputeSundownCountdownUseCase`, the `SundownCountdown` state, a DataStore preferences
repository, the `TrackRecordingUiState` field, `AlertKind.TURNAROUND`/`SUNSET` with their own
notification channel, and `DecideSundownAlertUseCase`.

It is a partial step, **and the PR description says so** — it names the on-screen row and the service
move as not included. Description and diff agree.

## The poll loop

- Interval **`POLL_INTERVAL_MILLIS = 15_000L`** (`TrackRecordingViewModel`, companion object, :588).
- `updateSundown()` is called at **:322, inside the `.onSuccess` of the
  `trackRepository.getById(trackId)` read the loop already made.** It adds no read of its own.
- **Pure computation.** Zero matches for `repository|dao\.|http|retrofit|okhttp|File\(` across
  `ComputeSundownCountdownUseCase`, `SunCrossing` and `CivilTwilight`; no `suspend` in the chain. The
  zero was validated against a known positive first — the same detector scores 10 on
  `TrackRecordingViewModel`.
- **Cost bound, derived from the constants rather than estimated.** `nextDescendingCrossing` scans at
  `COARSE_STEP_MILLIS` = 10 min across `SEARCH_WINDOW_MILLIS` = 24 h, so at most 144 altitude
  samples, then bisects to `PRECISION_MILLIS` = 1 s over a 10-minute bracket, roughly 10 more. Called
  **twice** per tick, sunset and then civil dusk from sunset. **At most ~308 `sunAltitudeDegrees`
  evaluations per 15 s tick**, fewer when the bracket is found early.
- **No short-circuit, and it cannot conflate.** `Known.nowEpochMillis` is part of the value, so every
  tick produces a distinct state and the `StateFlow` emits every time.
- **Allocates per tick:** a `LatLng`, a `SundownCountdown`, and the `UiState.copy`.

No battery figure is given. Nobody here can measure one, and the walk's number is already known to be
inflated by the duplicate location registration.

## Finding 1: `sundownCountdown` is never cleared, and the omission propagated by extraction

It appears exactly twice in `app/src/main`: its declaration (`TrackRecordingUiState:114`) and its
assignment (`TrackRecordingViewModel:360`). `stopRecording()`'s `copy(...)` at `:260` names six
fields and this is not among them. It is not cleared by `stopRecording()`, not by `stopReturn()`,
and **not by #96's `clearRecordingState()` either.**

**Owner ruling: audit row, not a fix.** It is invisible today because nothing renders the field. It
becomes a real bug the moment something does, which is the screen row.

### The general form, which supersedes the instance

The owner's first ruling asked for a row noting that `clearRecordingState()` inherited the omission.
On writing it down the mechanism turned out to be the general thing, and the owner ruled that the
generalization supersedes: *"'One field was missed' describes an instance. 'A refactor whose entire
guarantee is that nothing changes cannot surface what the original forgot' describes the class, and
it predicts recurrence rather than just recording one occurrence."*

> **Correctness by construction preserves omissions with exactly the fidelity it preserves
> behaviour, and the stronger the guarantee, the more faithfully it carries them.**

`clearRecordingState()` was created by extracting `stopRecording()`'s six-field `copy(...)` verbatim,
*precisely so the two could not drift*. That is a real guarantee and it did its job. What it
guarantees is that the extracted code does what the original did — which includes not clearing a
field the original never cleared — and it then gave that omission a second caller. **Nothing in the
extraction could have surfaced the missing field, because its entire guarantee is that nothing
changes.**

This is distinct from the families already recorded here. Those are checks decoupled from what they
check by a step nobody traced, or a red that could not have been green. This one is a change that
was *correct*, by a method chosen for its strength, and the strength is what carried the defect
forward intact. The usual defence — "the refactor is behaviour-preserving, so it cannot introduce a
bug" — is true and is not the claim that matters. It did not introduce one. It propagated one, and
propagation is what a second caller is.

**The practical form:** when extracting shared code to guarantee two callers cannot drift, the
extraction is the one moment the original's *contents* are being read as a list. Read them as a list.
Ask what is not in it. That question has no other natural occasion, which is why the omission
survives every later reading of either caller.

## Finding 2: on #95 alone the poll loop never stops after a stop from the shade

`beginPolling` is `while (true)` bounded by `pollingJob`, which only `TrackRecordingViewModel
.stopRecording()` cancels — and the notification shade never calls it. So on #95 by itself, after a
stop from the shade, the loop keeps performing a Room read and the sundown computation **every 15
seconds, indefinitely.** `updateSundown()` can and does run with `activeTrack` null.

**Owner ruling: this is the strongest argument for merging the two together rather than either
alone.** #96's `clearRecordingState()` cancels `pollingJob`, so the stacked configuration is the safe
one and #95 alone is not.

## Finding 3: an empty "Sundown alert" notification channel

`AndroidAlertDelivery`'s `init` creates the channel at process start, so a tester sees a category
named "Sundown alert" — confirmed on hardware in check 2 — controlling alerts that cannot fire,
because `DecideSundownAlertUseCase` has no caller. A visible artifact of an invisible feature.

**Owner ruling: cosmetic, and mid-beta is the wrong time to touch channel creation.**

## Tests

Five new classes — `SunCrossingTest` 7, `ComputeSundownCountdownUseCaseTest` 10,
`DecideSundownAlertUseCaseTest` 6, `DataStoreSundownPreferencesRepositoryTest` 7,
`TrackRecordingSundownTest` 4 — plus `TrackRecordingViewModelTest` at +5/−2.

**No test covers the sundown field's lifecycle across a stop.** The four ViewModel-level sundown
tests all compute the countdown; the only `stopRecording()` in that class is the harness cleanup at
`:69`. Validated against a positive: the same grep finds 33 in `TrackRecordingViewModelTest`.

**Suite on `a5668b4`: 172 suites, 1345 tests, 0 failures, 0 errors, 24 skipped.** Skip count reported
and never adjusted. **The 12-failure Windows pool is absent**, which is the correct result on Linux
rather than a surprise: it is a `%TEMP%` path-length property, threshold bounded to [212, 260], that
cannot be reached in a container.

## What a tester actually gets

**The sundown countdown ships as infrastructure with no user-facing surface.** Nothing renders the
field, no alert can fire.

**The part a tester experiences is the off-track silence reversal** — `overridesSilence` true to
false — a change to already-shipped behaviour, confirmed on hardware the same night in a paired run:
audible with two vibration pulses on a normal ringer, silent and still in full Mute.

**Owner ruling: this is the thing to watch for in the beta, and it belongs in the release notes**, so
reports come back attributable rather than as unexplained quiet.

---

## The framing this pulse was commissioned on was wrong, and the owner named it

The pulse described #95 as a sundown field plus a poll-loop call, and asked for reconnaissance on
that basis. It is 29 files and 22 commits, and the one part a tester experiences — the off-track
silence reversal — was not in the description at all.

The owner's own account, recorded because it is the more useful half: *"I carried a side observation
made for a different purpose and let it stand as a characterization. That's the provenance pattern
again, and this time I'm the one who did it while holding the rule."*

That is the derived-figure family: a claim correct about the thing it was derived from, which stops
being correct the moment it is quoted about something larger, with nothing in the quoting marking the
difference. The side observation was accurate for its original purpose — establishing that #96 was
independent of #95 — and inaccurate as a description of #95. Two of the three carried premises were
correct; the incomplete one was the characterization.

### What this says about the procedure, which is the part worth keeping

The pulse's own §6 required all three carried premises to be re-derived rather than trusted. **That
requirement is the only reason the incomplete framing surfaced at all.** Without it the executor
would have read the diff looking for a sundown field and a poll-loop call, found both, and reported
back a confirmation. The off-track silence reversal — the one part of #95 a tester experiences —
would have gone unmentioned in a document written specifically to decide whether #95 should ship.

The owner's ruling on what to take from that: *"The framing was mine and wrong; the procedure caught
it anyway. That's a useful thing to know about the procedure — it doesn't depend on the planner being
right, which is the property you actually want from a check."*

Stated as the property: **a check that only works when the person who commissioned it was already
correct is not a check.** The instruction "re-derive rather than trust, and report which premises
were wrong" costs one line in a dispatch and is what makes the difference between reconnaissance and
confirmation. It is cheap enough to be standard, and its value is highest exactly when the planner is
most confident, because that is when the carried claim is least likely to be questioned.

---

## Disclosure

### Confirmed by observation
Every file, line, count and constant above. Every reported zero was validated against a known
positive before being reported.

### Could not be determined
Any battery cost, deliberately. Whether the per-tick `StateFlow` emission causes measurable
recomposition — nothing reads the field, but the `UiState` identity changes every tick and that was
not measured.

### Premises in the pulse that were wrong
One of three. "Touches `TrackRecordingViewModel.kt` and `TrackRecordingUiState.kt`" is true and
substantially incomplete. "Does not touch `stopRecording()`, the service, or `MainActivity`" is
correct on all three, re-derived. "Sits inside the poll loop" is correct, and more precisely inside
the `.onSuccess` of a read that loop already made.

### Decided beyond scope
Nothing. No code was changed and no fix was proposed.
