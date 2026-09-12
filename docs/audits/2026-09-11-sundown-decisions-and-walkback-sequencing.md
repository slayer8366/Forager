# Sundown countdown: owner decisions, and why walk-back time cannot come first

**Date:** 2026-09-11
**Follows:** `2026-09-11-sundown-countdown-prebuild-report.md`
**Status:** decisions recorded. One sequencing question open. No code written.

---

## Decisions taken

| ID | Decision | Answer |
|---|---|---|
| **S1** | Sunset, civil dusk, or both | **Both.** Countdown to sunset as the headline, civil dusk as the second marker. One bisection, called at 0° and at −6°. |
| **S2** | When active | **During recording only for now**, with expansion planned. |
| **S3** | Default lead time | **One hour before sunset**, presented as the default, with the reason stated: the extra time accounts for darkness arriving before sunset does. The user may ignore it and leave later. Revisit once walk-back time is measured. |
| **S4** | How many alerts | **Two**: one at the lead time, one at sunset. |
| **S5** | Delivery shape | **B.** Countdown on the recording screen, plus two discrete notification posts. A is invisible to a phone in a pocket; C re-posts a notification every 15 s for hours to show a number nobody is watching. |

The stated limit is accepted: sunset is computed for a clear, flat horizon, and the countdown
counts to sunset, not to darkness. The copy says so once and never says there is enough time.

---

## The sequencing finding

The owner's instruction was that walk-back time should be implemented first, because it is what
tells someone when to turn around. The reasoning is right and the order is backwards, for a reason
recorded in the code before this dispatch existed.

`domain/ReturnWalkingTime.kt`'s header:

> "**No surface exists yet, on purpose** (the dispatch it was built for: *a number on screen that
> nothing acts on invites the user to act on it themselves*); **the alert brings the surface.**"

The estimate was deliberately built without a caller, waiting for this alert to be the thing that
shows it. They are not two features in an order. They are one feature, and the design already
answered which half arrives first: **the alert.**

### And it cannot be validated yet

The dispatch gates the return estimate on validation against walked tracks, comparing estimates
with actual return times and stating the sample. That sample does not exist.

- **No track data in the repository.** Searched for `*.gpx`, `*walk*.log`, `*track*.json`:
  **empty result, not a clean one.**
- **The only walk on record is `2026-09-07-fix-log-walk-findings.md`: one walk, one device,
  344 unique fixes over a 4.8-minute span.** A 4.8-minute walk has no return leg, so it cannot
  produce a single estimate-versus-actual pair. It was never collected for this purpose.

So walk-back time cannot go first, not because it is unimportant but because nothing exists to
check it against, and CLAUDE.md forbids shipping an estimate on no data.

**The beta is the instrument that produces that data.** The walk-findings report already says so
about hardware coverage: "it is not yet established across hardware, and the beta remains the
place that settles that." The same holds here. The order is therefore:

1. Sundown countdown, with the fixed one-hour margin. Needs no estimate at all.
2. Beta, which produces recorded tracks with real return legs.
3. Walk-back time surfaced inside the alert, validated against those tracks.

The estimate is already built to be safe in the meantime: seven degrade reasons that render
"at least X" rather than a confident figure, and two cases where no number is honest and none is
given. On a first walk with no measured pace it reads "at least X" from a deliberately slow
default. That is the design working, not a gap.

---

## The double-count, which needs naming before either ships

`ReturnWalkingTime`'s header states the rule:

> "the estimate is systematically optimistic for someone who stops, which the owner accepted on
> the condition that it is labelled walking time and never padded — **the conservatism lives in
> the turnaround alert's margin**, which is separate work, and **a padded input plus a margin
> double-counts by an amount nobody could name.**"

The one-hour default **is** that margin. The instinct and the recorded design agree, which is
worth saying plainly.

But the hour is now being asked to do **two** jobs that are not the same job:

| Margin | What it covers | Scales with |
|---|---|---|
| **Darkness** | Useful light ends before sunset, more so under canopy or west of a ridge | Terrain and cover. Not distance. |
| **Stops** | The estimate is walking time, so it is optimistic for anyone who pauses | Trip length and how much you stop. |

A fixed hour covers both today because there is no estimate to be optimistic yet. The moment
walk-back time is surfaced, the hour absorbs both and double-counts by an unnameable amount,
which is exactly what the header warns against.

**Recommendation: name them apart now, while there is only one number.** The setting is the
*darkness margin*, and it is about light. When walk-back time lands, the alert fires at
`sunset − darknessMargin − walkingTime`, with the stop allowance handled once, where it belongs,
and visible as its own term. Calling the setting "lead time" today invites the conflation
tomorrow.

---

## Disclosure

### Confirmed by observation
`ReturnWalkingTime.kt`'s header, read in full. The absence of track data, from a filesystem
search whose empty result is reported as empty. The walk sample's size and span, quoted from
`2026-09-07-fix-log-walk-findings.md`.

### Could not be determined
Whether any walked-track data exists outside this repository, on the owner's device or elsewhere.
That is the open question below, and it is the only thing that could change the order.

### Premises that were wrong
The owner's premise that walk-back time must be implemented before the sundown alert. The
reasoning behind it is sound and the dependency runs the other way: the alert is the surface the
estimate was built to wait for, and the beta is what validates it.

### Decided beyond scope
Nothing built. The margin-naming recommendation is a recommendation.

---

## The open question

**Do walked tracks with known actual return times exist anywhere, on the owner's device, an old
export, anything?** If yes, walk-back time can be validated now and the order is the owner's to
set. If no, the order above holds and the beta is what unblocks it.
