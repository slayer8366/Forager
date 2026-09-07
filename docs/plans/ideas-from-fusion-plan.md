# IDEAS — Harvested from the Fable 5.1 location-fusion plan

**Type:** ideas register. **Not a dispatch. Nothing here is authorised or scheduled.**
**Issued by:** planner
**Source:** a six-document fused-location-service plan (Goals, Input Signals, Core Architecture,
Delivery Phases, Risks/Privacy, Testing), generated on an open creative brief and **not written for
this project.**

**Purpose:** the source plan describes a commercial positioning service — 13 FTE, 30 weeks, licensed
Wi-Fi/cell databases, a backend, telemetry, crowdsourced scan uploads, BLE beacons in partner venues,
iOS, a third-party SDK. **None of that shape fits Forager**, and its central mechanism — network
positioning against a reference database — is the exact source of the track corruption found this
week.

**But several of its ideas are good, and some get better when the commercial apparatus is stripped
off.** This document records those, so they are not lost and not confused with a roadmap.

---

## The filter that decides what gets kept

Anything from the source plan that requires **a network call, a backend, a licensed database,
telemetry, an account, or an upload** is out, permanently. Forager has no account, works offline,
strips EXIF from stored photos, and sends nothing anywhere. That is not a limitation to work around;
it is the product.

Anything that runs **on device, from sensors the phone already has, with no data leaving it**, is
fair game.

---

# TIER 1 — Take these

## 1. A replay harness

**Source: §6.4. The single most valuable idea in the document.**

Record every field session as a **complete observation log** — every fix from every provider, raw
sensor batches, timestamps — and store it. Then run any filter change against the **whole corpus** in
CI, in seconds, producing accuracy figures without walking anywhere.

**Why it matters here specifically:** every location change this project has made was verified by the
owner physically walking. The timestamp rule took four exports and a week of walks. A replay corpus
turns that into a test.

**It is nearly free, because the fix-logging branch already collects most of it.** That branch is
scoped as a diagnostic; **this argues for designing its log format for replay from the start** — a
deterministic, machine-readable record rather than something a human reads once.

**What it would need:** a stable log format, a way to run the sampler and any filter over a log
offline, and the discipline of keeping logs. **The logs are location traces and must never enter the
repository** — `*.gpx` is already in `.gitignore` for the same reason.

**Depends on:** fix-logging branch. **Do before:** any Kalman work, which is otherwise untestable.

## 2. Uncertainty calibration as a measurable gate

**Source: §1.4, §6.3, §6.6.**

Their metric: **the true position falls inside the stated 95% circle between 93% and 97% of the
time**, with the mean radius also reported so that over-inflating the circle to pass is visible.

**This is the honest-precision principle expressed as a number.** This project has argued repeatedly
that the app must never show a number more confident than the data behind it — "within 16 ft", the
suppressed needle, "≈". None of it is currently measured.

**With a replay corpus and a few surveyed points, it becomes measurable.** A single known coordinate
walked past repeatedly would give a first reading.

**Honest version of their idea:** they gate releases on it via telemetry from users. Here it would be
a local metric over the owner's own corpus, and later a question in the beta report.

## 3. Process noise set by motion state

**Source: §2.6, §3.3.**

A stationary user's position uncertainty should grow **slowly**; a walking user's should grow **with
distance travelled**. Their filter sets its process noise from a motion classifier (still / walking /
vehicle).

**Directly relevant to the queued Kalman work**, and it is the piece that makes a filter behave
sensibly when someone stops to pick mushrooms — which is most of a foraging trip.

**Also relevant to the stale-fix display already shipped:** "no fix for 5 minutes" means something
different standing still than it does walking, and motion state is the input that would let the app
say so.

## 4. Stride learned from clean GNSS segments

**Source: §2.6.**

Their pedestrian dead reckoning **learns stride length per user from GNSS-validated segments** — when
the fix is good and the user is walking steadily, the distance travelled divided by steps taken gives
that person's stride.

**This is better than the pace counter currently queued**, which asks the user to define a unit and
tap manually. The phone can learn it silently from good stretches and apply it under canopy where GPS
fails.

**It does not replace the manual counter** — a deliberate pace count is a different tool, and the
owner has already decided that one goes to Room for durability. But automatic stride learning would
make dead reckoning work for someone who never opens that feature.

---

# TIER 2 — Worth doing, smaller

## 5. Provenance on every fix, shown to the user

**Source: §1.5, §3.3, §3.6.** *Their version is for backend dashboards. The honest version is
better.*

Their plan attaches to every fix a record of **which sources contributed and with what weight**, and
ships it to a quality dashboard.

**On device, with no telemetry, this becomes something the user can see**: why the app believes it
knows where they are. That is the same instinct as showing an error radius rather than a false-precise
distance.

**It would also have made this week's bug visible in minutes rather than days.** A user — or the
owner — could have seen "this fix came from the network provider" on screen.

**Open question, not a decision:** whether this is a debug affordance, a long-press detail, or
something in Tools. It should not clutter the HUD.

## 6. Cell as a sanity bound, never as a position

**Source: §2.3, §2.8.** *Inverted from their use.*

Their plan uses cell towers **as a position source** and also as a **consistency check** — a GNSS fix
5 km from the serving cell is rejected.

**Take the check, leave the positioning.** Serving-cell identity is free — the modem already tracks
it — requires no database lookup and no network call if used only as a coarse plausibility bound: *is
this fix absurdly far from where the phone's radio says it is?*

**Caveat that must be resolved before this is worth anything:** without a cell database you have no
tower coordinates, so the check reduces to "did the serving cell change while the fix moved 5 km",
which is weaker. **Establish whether that weaker form is worth having at all.** It may not be.

## 7. Barometer for altitude

**Source: §2.7.**

Elevation is currently raw GNSS altitude, which is the noisiest thing GNSS produces — routinely worse
than horizontal error by a factor of two or three, and visibly jumpy in the readout.

**A barometer gives relative altitude to sub-metre resolution**, re-anchored against GNSS altitude
when the fix is good. Most Android devices have one.

**Relevant to more than the readout:** elevation change is a real cost in return-time estimation,
which the light budget will need. Climbing back to a trailhead takes longer than the flat distance
suggests.

**Weather drift** — several hundred pascals per day — is handled by re-anchoring, and their plan says
so.

## 8. Jitter suppression from motion state

**Source: §2.8.**

**"Sensor data indicates the device is stationary while GNSS reports movement"** — reject or
down-weight the fix.

The accelerometer knows the phone is in a pocket going nowhere. Every one of the venue starbursts
happened while the owner was demonstrably still. **This is a second, independent check on the same
failure the timestamp rule catches**, and it needs no provider information at all.

**Worth noting it would also have caught the unexplained radios-off excursion**, which the timestamp
rule does not account for.

---

# TIER 3 — Interesting, probably not

## 9. Raw GNSS measurements

**Source: §2.2.** Android's `GnssMeasurement` API exposes pseudoranges, Doppler and per-satellite
C/N0 on supporting devices, enabling **multipath detection** — which is the mechanism behind the
backtrack divergence under cover that the timestamp rule cannot touch.

**Genuinely the right tool for that problem, and genuinely a large amount of work.** Satellite
elevation angles and C/N0 alone would give a **sky-visibility score** without any of the hard parts,
and that is the cheap slice worth considering first.

## 10. Wi-Fi RTT

**Source: §2.4.** 1–2 m ranging against access points that support 802.11mc. **Excellent accuracy,
and irrelevant here** — foragers are not near enterprise access points.

## 11. Particle filter with map constraints

**Source: §3.3.** Constrains position to walkable space using a floor plan. **The forest equivalent
would be trail data from OpenStreetMap**, which is interesting and mostly wrong: foragers deliberately
leave trails, and a filter that snaps them back to one would be actively harmful.

---

# EXPLICITLY REJECTED

Recorded so they are not revisited:

- **Telemetry of any kind**, aggregated or anonymised. The app sends nothing.
- **Crowdsourced observation upload.** Even opt-in, even anonymised, this is a forager's spots leaving
  their phone.
- **Wi-Fi or cell positioning against a reference database.** Requires a licensed database and a
  network call, and it is the mechanism that corrupted the tracks.
- **BLE beacons, venues, floor detection, indoor wayfinding.** No relevance.
- **iOS, and a third-party SDK.**
- **A backend of any kind** beyond the existing tile worker.

---

# CONSENT AND WHO OWNS THE CORPUS

A possible future direction has been raised: extracting the location work into a standalone
open-source positioning library — GNSS-only, no network, honest about uncertainty — as a
privacy-respecting alternative to the default stack. The replay harness (§1) becomes more valuable
under that plan, because a corpus of logged real-world sessions is what would make such a library
credible. It also creates a consent problem the rest of this document does not address, and it is
the kind of problem that is walked into by omission. Recorded here before anyone does.

## Two corpora, not one

**The owner's own logs** — his device, his walks — are unproblematic. They accumulate as a byproduct
of testing, nothing leaves anyone else's phone, and they are the asset that makes the replay harness
worth building at all.

**Beta testers' logs are a different thing**, even though the mechanism that produces them is
identical. A tester who enables logging to help debug Forager has consented to *that*. Their trace
becoming part of a public open-source project's test corpus is not covered by the same switch, and
no wording on the switch makes it so.

## The consent, if it is ever wanted, is asked separately

Not by omission, not buried in a longer agreement, and not retroactively. A second, specific ask,
naming what the file contains and where it would go. The register for that ask already exists in
this repository: the optional track-file paragraph in the beta trip report says plainly what is in
the file, does not call it anonymous, and does not claim the app removes anything. The same register
applies here, with one more sentence — where the file would end up.

## Location traces cannot be anonymised

A track shows where a person walked. Stripping identifiers does not change that: the trace itself
identifies a place, and for a forager the place is the sensitive part. There is no version of a raw
log that is safe to publish, and this document should not be read as implying one exists.

**So the realistic shape is a corpus held privately, with results published from it** — accuracy
figures over hundreds of logged hours, calibration numbers, the kind of evidence §2 describes — not
a public dump of raw logs. That still gives an extracted library its credibility without asking
anyone to hand over their spots. It is the same line the app already draws for itself: it sends
nothing, and its evidence is what it shows on screen.

## One design consequence, worth recording now

Keep the fusion logic free of Forager's domain types. The timestamp predicate, the accuracy gate, and
any future filter should take positions and timestamps rather than `TrackPoint`. If extraction ever
happens it is then mechanical rather than a rewrite, and it costs nothing today.

**Where the code stands against that, as read (not changed):** the arithmetic is already pure and
Android-free throughout, but most of it is *typed* on this app's domain classes. The network-fix
predicate (PR #73, `NetworkProviderFix.kt`) is an extension on `TrackPoint` whose body is one `Long`
expression; the live-fix accuracy gate (`LiveFixGate.acceptLiveFix`) takes `LocationFix.Update`; the
sampler (`LocationSampler.shouldAccept`) takes two `TrackPoint`s; the compass trust judge takes
`CompassReading`. Already free of domain types: `isApproaching(distanceMeters, accuracyMeters)`,
`fixFreshness(ageMillis)`, `relativeBearingDegrees`, and `HeadingSmoother.next(Float)`. So the
consequence is satisfied in substance and not in signature: each typed function reads two or three
fields and would move by changing its parameter list, not its logic. Nothing is changed for it here.

---

## Sequencing, if any of this is taken

1. **Replay harness**, designed into the fix-logging branch. Everything below is easier to verify
   afterwards and harder to verify before.
2. **Jitter suppression** (§8) — small, independent, catches a failure the timestamp rule misses.
3. **Kalman filter with motion-state process noise** (§3) — already queued; this shapes it.
4. **Stride learning** (§4) — feeds dead reckoning, already queued.
5. **Barometer** (§7) — improves the readout and the light budget's return estimate.
6. **Provenance display** (§5) — needs a design decision about where it lives.

**None of this is scheduled and none of it is pre-beta.** The pre-beta list is unchanged: the light
budget and turnaround alert, and the report template.

---

## Coder's note on filing (in `docs/plans/`, the owner's call — a register of intent, not a dated audit) — the replay-harness cross-reference the planner asked for

The planner's covering note: the replay-harness idea (§1) is time-sensitive in a way the rest is
not, because it argues the fix-logging branch's log format should be designed for machine replay
from the start, and that branch is written but not built — cheap to account for now, awkward to
retrofit. The ask was a cross-reference in the logging dispatch.

**What could and could not be done from here.** There is no fix-logging branch on the remote
(`git ls-remote --heads origin` lists none), and the logging dispatch is a chat upload, not a
repository document, so **the cross-reference cannot be placed in the dispatch from this side.** The
planner holds that document and should add it there. This note is the in-repository half: the one
place that states, in this codebase's own terms, what a replay-ready log has to contain — so whoever
builds the logging branch can check the format against it without re-deriving anything.

**What a replay-ready fix log needs, from what this project has already established:**

- **Per fix, verbatim from the `Location`:** provider name (the one field the whole timestamp
  investigation lacked); `getTime()` **with its milliseconds intact** — the discriminator in
  `NetworkProviderFix.kt` is `millis == 0`, so a format that rounds, formats to seconds, or
  re-stamps with the system clock destroys the signal; `getElapsedRealtimeNanos()` (monotonic, for
  ordering and for gaps across clock changes); latitude, longitude, altitude with `hasAltitude()`,
  accuracy with `hasAccuracy()`, and speed / bearing / vertical accuracy with their `has*()` flags
  rather than zeros — an absent value must stay absent, the same rule `TrackPoint` follows.
- **Per session:** the recording mode (`TrackRecordingMode`) in force, so the sampler can be replayed
  with the thresholds that actually applied; device make/model and Android version (the GNSS chipset
  family, the variable behind second-aligned GPS time — the same two fields the beta device report
  asks for and for the same reason).
- **Every fix the listener delivered, not only the ones the sampler kept.** The sampler drops on all
  four rules with no `else`; a log of accepted points cannot replay the sampler.
- **Deterministic and machine-readable:** one record per line, stable field order, no free text
  interleaved, so `LocationSampler.shouldAccept` and `excludeNetworkProviderFixes` — both already
  pure functions over `TrackPoint` — can be run over a log offline with no new code beyond a reader.
- **Never in the repository.** A fix log is a location trace. `*.gpx` is already ignored for the same
  reason; whatever extension the log format uses goes beside it in `.gitignore` in the same change
  that introduces the format.

**Nothing here is built or scheduled.** The register above is an ideas record; this note exists so
the one time-sensitive item in it is findable from the repository by the person it concerns.
