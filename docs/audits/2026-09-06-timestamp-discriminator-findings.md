# FINDINGS — A binary discriminator for the bad fixes, and the one question that could sink it

**Type:** evidence analysis, three tracks. **Not a dispatch. Nothing to build from this alone.**
**Issued by:** planner
**Supersedes nothing.** Extends `docs/audits/2026-09-06-starburst-gpx-findings.md` (`8b216bc`) with
two further exports and a stronger test than the one that document proposed.

**There is a question at the end that the coder can answer in five minutes and that decides whether
any of this is sound. Read it before drawing conclusions.**

---

## The three tracks

All exported from Records by the owner on 6 September 2026.

| File | Context | Points | Span | Path | Straight |
|---|---|---|---|---|---|
| `…124007.gpx` | Venue, **stationary** | 17 | 171 s | 130 m | 3 m |
| `…171606.gpx` | Walk with starburst | 32 | 230 s | 498 m | 27 m |
| `…172802.gpx` | **Open sky**, short walk | 8 | 50 s | 40 m | 15 m |

---

## The discriminator

**Sub-second timestamps mark the bad fixes. Whole-second timestamps mark the real ones.**

| File | Sub-second | Whole-second | Elevation, sub-second | Elevation, whole-second |
|---|---|---|---|---|
| Venue | 8 | 9 | 111.6 | 92.3 – 98.2 |
| Walk | 10 | 22 | 110.7, 110.9, 111.0, 111.4, 111.6 | 101.1 – 108.5 |
| Open sky | 2 | 6 | 110.8, 110.9 | 103.9 – 107.2 |
| **Total** | **20** | **37** | | |

**Zero elevation overlap between the two groups, in any file.** The sub-second points cluster tightly
around 110–112 m regardless of where the owner actually was; the whole-second points vary smoothly
and plausibly.

**The proposed mechanism:** GPS time is second-aligned, derived from the satellite signal. The
network provider stamps with the system clock, which lands on arbitrary milliseconds. This is the
same provider split the first findings document identified by the float32 elevation signature — but
expressed as a **binary test with no threshold, no tuning, and no accuracy arithmetic.**

### What removing them does

| File | Path as recorded | Path with sub-second points removed | Actual straight-line |
|---|---|---|---|
| Venue | 130 m | **4 m** | 3 m |
| Walk | 498 m | **81 m** | 27 m |
| Open sky | 40 m | **28 m** | 15 m |

The venue track is the clearest: **130 m of recorded movement while stationary collapses to 4 m**,
against 3 m of real displacement.

### Why the open-sky track is the most useful of the three

It is **short and undramatic** — no step exceeds 7.2 m, and its two network points happen to land
close by, so they draw no visible spike.

**The discriminator still sorts it correctly**, by the same elevation signature, on a different walk
under clear sky. **If sub-second stamps appeared on ordinary GPS fixes, this is where they would show
up.** They do not.

It also shows the rule doing something the eye cannot: **excluding two bad fixes that happened not to
be visibly wrong this time.** They come from the instrument that throws 36 m excursions elsewhere in
the same corpus.

---

## Why this matters more than the excursion rule

**Timestamps are persisted.** The store orders reads by them. So unlike provider — which was never
stored — **this test works retroactively on every track ever recorded**, at the existing read seam,
with no schema change.

That answers the open question the first findings document left: *whether existing recorded tracks
can be repaired at all, given provider was never stored.* **On this evidence, yes.**

It also removes the problem that held Part A. The excursion rule needed a threshold — a sum of
reported accuracies — and **that threshold would have passed the venue track's 7.5–11 m excursions
entirely.** A binary provider test has no threshold to get wrong.

---

## A second correction to the planner's screenshot reading

The first findings document recorded that the planner wrongly told the coder the spokes were
multi-point chains.

**The planner made the same error again** on the 5:18 PM screenshots, describing the southern spokes
as "long, multi-point". The walk track's own data shows the excursions are **single-point**: steps of
24 to 36 m out, and back, one sample each.

**The dots are the polyline's rendering style, not individual points.** Recorded here a second time
because it has now misled twice, and it will mislead a third time otherwise.

---

## THE QUESTION THAT DECIDES THIS

**Which clock does the sampler store?**

- **`Location.getTime()`** — the provider's own timestamp. If this is what is stored, the
  second-alignment is a real property of the GPS fix and the discriminator is sound.
- **`System.currentTimeMillis()`** at receipt, or any other clock read at storage time. **If this is
  what is stored, the whole-second alignment has nothing to do with GPS, the observed pattern is an
  artifact of something else, and this entire theory collapses.**

**Answer this from the code before anything is built on it.** It is a five-minute read and it is the
difference between a trivial exact fix and a wrong one.

**Also worth establishing in the same pass:** whether the timestamp written to `track_points` is the
same value GPX export writes out, or whether export transforms it. The analysis above is entirely of
exported files.

---

## Limits, stated plainly

- **Three tracks, one device, one day, two locations.** The separation is perfect across all three,
  but 57 points is a small corpus and one phone is one phone.
- **No clean control track exists.** The owner has none without spikes. The open-sky track is the
  closest thing, and it still contained two network fixes. **A walk with mobile data and Wi-Fi
  disabled is the isolating test** — it should suppress the network provider entirely, and if
  sub-second stamps still appear, the rule is wrong. **That test has not been run.**
- **The mechanism is inferred, not observed.** No code has been read for this document. The claim
  that GPS time is second-aligned and system time is not is a general property, not a measurement of
  this app.
- **The earlier device test where radios off still produced a ~40 m excursion** remains unexplained
  and is **not** accounted for by this theory. There may be a second mechanism. **Do not treat the
  timestamp rule as explaining every bad fix ever seen.**
- **The fix-logging work would confirm all of this directly** by recording the provider per fix, and
  remains the definitive answer.

---

## What this does not authorise

**Nothing.** No filter, no rule, no schema change, no sampler change.

If the clock question resolves in favour of `Location.getTime()`, the next step is a dispatch, and it
will need to settle at minimum: whether the test runs at the read seam or at the source, what happens
to a track where every point is sub-second, whether the excluded points are still counted anywhere,
and whether the first and last points are protected as the excursion rule intended.

**If the clock question resolves the other way, this document is a record of a wrong theory** and
should be marked as such rather than deleted — the elevation signature is still real and still needs
explaining.

---

## Coder's answer to the question that decides this (added when filed, read on `a2dcb21` / branch head `6bfda7a`)

**The stored clock is `Location.getTime()`, unchanged end to end.**

- `location/AndroidLocationTracker.kt:73` — `Location.toFix()` sets `timestampEpochMillis = time`,
  the `Location` object's own `getTime()`. No `System.currentTimeMillis()`, no `elapsedRealtime`,
  anywhere in the tracker, the provider, the service, the sampler or the repository (grep of those
  five files).
- `service/TrackRecordingService.kt:123` — the service copies `fix.timestampEpochMillis` into the
  `TrackPoint` it samples and persists; the sampler compares timestamps but never rewrites one.
- `data/repository/RoomTrackRepository.kt:83, :92` — the entity mapping copies the field both ways.
- `domain/GpxCodec.kt:49` — export writes `Instant.ofEpochMilli(point.timestampEpochMillis)`, whose
  `toString()` (ISO-8601 instant) prints **no fractional seconds when the millisecond part is zero
  and exactly three digits otherwise**. Checked on this JVM: `…07000L → 2026-09-06T12:40:07Z`,
  `…07123L → …07.123Z`, `…07100L → …07.100Z`. So the "whole-second / sub-second" split the planner
  read off the GPX is precisely the stored value's `millis % 1000 == 0` test — export neither adds
  nor removes the property, and the same test can be run on the table.

**So the theory survives the question it named as decisive.** What this does *not* establish, and
the code cannot: that a GPS fix's `getTime()` is second-aligned is a property of the device's GNSS
stack (fix time derived from the satellite epoch at a 1 Hz cadence), not of this app, and it is not
guaranteed across chipsets — the planner's own radios-off walk is the isolating test, and the
fix-logging branch's per-fix provider record is the direct one. Also unchanged by this answer: the
earlier radios-off ~40 m excursion is still unexplained; if it recurs with a whole-second stamp,
that is the second mechanism.

**Nothing is built from this.** The points the planner lists for the eventual dispatch — read seam
or source, a track that is all sub-second, whether excluded points count anywhere, first/last
protection — stand as the open decisions, and one more from reading the code: the sampler's own
interval rule compares consecutive *stored* timestamps, so a source-side exclusion of network
fixes would also change which GPS fixes the sampler accepts (a rejected network fix no longer
becomes `lastAccepted`); a read-seam exclusion would not. That difference belongs in the dispatch.

---

## Terminology note (added 2026-09-08, owner's request, from the GPX full-record pre-build report)

"Sub-second" throughout this document and the handoff wording that preceded it means the
**fractional-millisecond part of a point's stored timestamp** (`timestampEpochMillis % 1000 != 0`),
not a sub-second *sampling interval*. The sampler's tightest interval floor is 5 000 ms
(`TrackRecordingMode.HIGH_ACCURACY`), so no two stored points are ever under a second apart, and a
reading of the earlier record as claiming sub-second intervals was rightly refuted on that ground.
That refutation was wrong only in implying the record was mistaken: it was ambiguous. The predicate
the app now runs (`NetworkProviderFix.kt:40`) keys on exactly the millisecond field this document
describes, and on nothing else — not on accuracy, not on any interval. Recorded here, where the term
first appears, so the two readings cannot be confused again.
