# FINDINGS — What the starburst GPX actually contains

**Type:** evidence analysis. **Not a dispatch. Nothing to build from this document alone.**
**Issued by:** planner
**Source file:** `forager-track-2026-09-06-124007.gpx`, exported from Records by the owner.
**Analysed:** 6 September 2026.

**Purpose:** this is the file the coder asked for in the Part A addendum — *"the one file that settles
it."* It settles it. **Commit this into the audit trail** so the next dispatch rests on evidence
rather than on the two wrong inferences it corrects.

---

## The track

17 points, 171 seconds, recorded while the owner was **stationary**.

| # | lat | lon | ele | Δt (s) | step (m) | m/s |
|---|---|---|---|---|---|---|
| 0 | 45.32616980 | -122.63380453 | 92.2870 | – | – | – |
| 1 | 45.32626210 | -122.63384690 | **111.6000** | 20.9 | 10.8 | 0.52 |
| 2 | 45.32618958 | -122.63381365 | 98.1919 | 5.1 | 8.5 | 1.67 |
| 3 | 45.32626180 | -122.63384650 | **111.6000** | 15.5 | 8.4 | 0.54 |
| 4 | 45.32618841 | -122.63381378 | 97.8967 | 5.5 | 8.6 | 1.56 |
| 5 | 45.32626200 | -122.63384700 | **111.6000** | 15.1 | 8.6 | 0.57 |
| 6 | 45.32619203 | -122.63381655 | 97.3673 | 5.9 | 8.1 | 1.38 |
| 7 | 45.32626210 | -122.63384700 | **111.6000** | 14.7 | 8.1 | 0.56 |
| 8 | 45.32619352 | -122.63381797 | 97.2798 | 5.3 | 8.0 | 1.49 |
| 9 | 45.32626210 | -122.63384690 | **111.6000** | 15.3 | 8.0 | 0.52 |
| 10 | 45.32619487 | -122.63381865 | 97.2467 | 5.7 | 7.8 | 1.36 |
| 11 | 45.32626200 | -122.63384870 | **111.6000** | 14.9 | 7.8 | 0.53 |
| 12 | 45.32619608 | -122.63381964 | 97.2764 | 5.1 | 7.7 | 1.49 |
| 13 | 45.32626190 | -122.63384670 | **111.6000** | 15.4 | 7.6 | 0.49 |
| 14 | 45.32619694 | -122.63382030 | 97.4477 | 5.6 | 7.5 | 1.35 |
| 15 | 45.32626210 | -122.63384740 | **111.6000** | 15.0 | 7.5 | 0.50 |
| 16 | 45.32619800 | -122.63382095 | 97.4801 | 6.0 | 7.4 | 1.24 |

**Total path length: 130.4 m. Straight line, first point to last: 3.4 m.**

---

## What it shows

### 1. Strict alternation between two positions

Odd points sit at **45.326262 / −122.633847**. Even points sit near **45.326195**. Back and forth,
eight times, with no drift pattern between them — two clusters, not a wander.

### 2. The odd cluster's elevation is a stored constant

**Every odd point reports `111.5999984741211`** — identical to the last bit, eight times. That is
float32 of **111.6**.

The even points report 97.2 to 98.2, **varying naturally**, as a measured altitude does.

**A fixed altitude repeated to the bit, paired with a position repeating to 1e-7 degrees, is a
database lookup and not a measurement.** This is the **network provider**.

### 3. The cadence splits the same way

- Good → bad: **~15 s**
- Bad → good: **~5 s**
- Sum: **~20 s**

**Two listeners at different intervals.** GPS at roughly 5 s, network at roughly 20 s. Consistent
with the shared-listener registration the earlier pulse quoted: GPS and network on one listener at a
1-second floor, with each provider delivering at its own rate.

### 4. The scale of the corruption

**130.4 m of recorded path over 171 s, with 3.4 m of real movement.**

That is roughly **2.7 km/h of phantom distance while standing still** — about **11 km fabricated over
a four-hour trip.** Every derived figure inherits it: track statistics, the trip report, Cartography's
distance snapshot, and the path-length-home the turnaround alert would have depended on.

---

## Two inferences this corrects

**Both were the planner's, and both were wrong.**

### The spokes are not multi-point

The planner examined the screenshots and told the coder the spokes were chains of many points. **They
are not.** Each spoke is **one network fix out and one GPS fix back** — a single-sample excursion.
The dots along each line are the **polyline's rendering style**, not separate points.

**Consequence:** the coder's implied-speed analysis in the Part A addendum was defeated by a shape
that does not exist. The analysis itself was sound and its conclusion — that a speed bound is a rule
about the user rather than about the fix, and would catch a jogger or an honest drive between sites —
**still stands on its own merits** and remains the reason not to build one.

### Rule one would not have caught this either

The excursion is only **7.5 to 10.8 m**. Rule one's threshold was **the sum of the two points'
reported accuracies**. Two fixes of even modest reported accuracy sum to well above 11 m, so **the
rule would have passed every one of these points.**

The starburst **looks** dramatic because the map is zoomed in, not because the displacement is large.

**Consequence:** Part A as scoped would have shipped a filter that passes its own tests and leaves the
photographed bug on screen. **Holding Part A was correct**, for a better reason than the one it was
held for.

---

## What this means for the fix

**This is a source-side problem, not a display-filter problem.**

The two positions are not noise around one truth. **One of them is a different instrument**, reporting
a cached point with a stored altitude, and the app is treating its output as interchangeable with
GPS.

**A filter at the read seam cannot cleanly separate them**, because the discriminator is not
displacement, not accuracy, and not implied speed. It is **which provider produced the fix** — and the
pulse established that **provider is not persisted anywhere**, so no display-time filter on stored
data can see it.

**Preferring GPS when a usable GPS fix is available is the cheaper and more direct fix**, and it is a
different dispatch from the one Part A described.

**That dispatch is not written yet and nothing here authorises building it.** Points it will have to
settle, recorded now so they are not rediscovered:

- What "a usable GPS fix is available" means, and what happens when it is not.
- Whether the network listener is dropped entirely or kept as a fallback with a timeout.
- What this does to first-fix latency, which is the reason a network provider is usually registered
  at all.
- Whether existing recorded tracks can be repaired at all, given provider was never stored. **Probably
  not by provider** — but the float32 altitude signature in this file suggests they may be
  identifiable another way, and **that is worth investigating rather than assuming.**
- Whether persisting provider on `TrackPoint` now has a reader, which would resolve the
  no-dormant-columns objection that deferred it.

---

## Provenance and limits

- **One track, one location, 171 seconds.** The mechanism is unambiguous in this file; **its
  prevalence across devices and places is not established.**
- The earlier device test where **radios off still produced a ~40 m excursion** was at a **different
  location and a different session**, and is **not** refuted by this file. There may be a second
  mechanism — multipath at that site remains plausible. **Do not treat this file as explaining every
  starburst ever seen.**
- **Accuracy is not present in GPX export**, so the reported accuracy of each point here is unknown.
  The claim that rule one would have passed these points rests on the displacement being 7.5–10.8 m
  and on any plausible pair of accuracies summing above that. **It is an inference, not a
  measurement**, and the database would settle it.
- The float32 altitude signature is **strong evidence**, not proof, that the odd cluster is the
  network provider. **The fix-logging work would confirm it directly** by recording the provider per
  fix.

---

## Coder's note on filing (added when this was committed to the audit trail)

The document above is the planner's, filed verbatim. Two of its claims are checkable from the
repository without the device, and both check:

- **`111.5999984741211` is float32 of 111.6.** `struct.unpack('f', struct.pack('f', 111.6))`
  gives exactly `111.5999984741211`. The app stores altitude as a `Double` taken straight from
  `Location.getAltitude()` (`location/AndroidLocationTracker.kt:71`, `if (hasAltitude()) altitude
  else null`), so a provider handing back a float32 constant would land in `track_points.altitude`
  bit-identical, eight times, exactly as the GPX shows. That is also why the "identifiable another
  way" question in the findings is worth asking of the database rather than dismissing: the
  signature survives storage.
- **The shared listener at a one-second floor is as the pulse described.**
  `AndroidLocationTracker.kt:53–56` requests updates from `GPS_PROVIDER` and `NETWORK_PROVIDER` on
  one listener with `MIN_UPDATE_INTERVAL_MILLIS = 1_000L` (`:80`) and no minimum distance, and
  nothing tags a fix with its provider before it reaches the sampler.

For the record of the Part A addendum's reasoning: its implied-speed conclusion was argued against
a shape (a many-dot chain) that this file shows does not exist. The conclusion stands for the
reason the planner gives — a speed bound is a rule about the user — and the recommendation to hold
stands for the stronger reason the findings give: the discriminator is the provider, which the
store never saw. Nothing is built from this file; the fix it points at is a separate dispatch.
