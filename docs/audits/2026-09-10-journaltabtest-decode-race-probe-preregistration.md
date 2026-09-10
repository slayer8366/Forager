# Pre-registration: the decode-race probe, and what a null means before anyone runs one

**Date:** 2026-09-10. **Written before any run of any probe named here.** Nothing in this document
reports an outcome, because no probe has been executed.

**Why it exists.** The candidate from
[`2026-09-10-journaltabtest-flake-origin-archive-read.md`](2026-09-10-journaltabtest-flake-origin-archive-read.md)
is cheap to probe and its cheapest probe is a local one. The owner's caution is the right one: a
local null will be read as exoneration unless what it licenses is fixed in advance. That is what
sections D and E fix.

Two premises had to be checked first, because both are load-bearing for the probe design and both
turned out to be wrong. Sections B and C are those corrections. They are stated before the design
because the design depends on them.

---

## A. What is being probed

`DecodedPhoto` publishes the `Log photo` node only after `withContext(Dispatchers.IO)` returns.
`JournalTabTest.kt:374` asserts on that node with no wait, while line 361 wraps the same lookup in
`waitUntil(5_000)`. The hypothesis is that the failure is one `Dispatchers.IO` hop losing a race
with one recomposition, and that a busy shared pool is what makes it lose.

The hypothesis has never been executed. Everything below is designed to execute it.

---

## B. CORRECTION: the flake has been observed off CI, five times, in four sessions

**This contradicts a premise that is written into the existing pre-registration and has travelled
since.** `2026-09-09-journaltabtest-flake-comparison-preregistration.md:37` says "Every sighting,
all of them, is CI." It is not true, and the counter-evidence is in this repository's own audit
folder.

| When | Where recorded | What it was |
|---|---|---|
| 2026-08-26 16:05 | commit `6d99a98` | `./gradlew assembleDebug testDebugUnitTest`, "flake reproduced once, green on rerun". Session container, not CI. |
| 2026-08-26 18:55 | commit `2876df0` | Local full suite, 732/733, the one failure being this flake. PR #44's body calls this window "verified directly and locally". |
| 2026-08-29 | `DISPATCH-REPORT.md:10-14` | 762 tests, 761 passing. "This container ships no Android SDK. I downloaded the command-line tools ... so every claim below is backed by a real `./gradlew` run." Nothing was pushed, so no CI run of this tree exists. |
| 2026-09-09 | `2026-09-09-recording-notification-stop-action-completion-report.md:199,201` | Runs 4 and 6 of 11, two reds. That report's own "what was not verified" section ends: **"No CI run. All figures here come from this container's `./gradlew --no-daemon testDebugUnitTest`, not from GitHub Actions."** |

**Where the wrong premise came from.** The flake note says the failure happens "on Linux, in
CI-equivalent full-suite runs." *CI-equivalent* is the load-bearing word and it was read as *CI*.
The draw tally that the premise rests on counts GitHub Actions draws plus one row labelled "local
Windows", so a reader scanning the table sees exactly two categories and concludes the flake lives
in one of them. The container runs are a third category that the table never had a column for.

**What "local" means in every tally in this repository:** the owner's Windows host. That figure is
0 reds in 12 or 13 draws and it is sound. It says nothing about a Linux container, which is where
five of the reds happened.

**Consequence for the probe.** A pair probe run in a session container is an instrument whose sample
has contained the thing being measured, five times. A pair probe run on the Windows host is not, and
must not be substituted for it. This is the same rule the earlier pre-registration applied correctly
to Windows and then over-generalised: confirm the sample includes the cases that could have failed
it.

---

## C. CORRECTION: the Wilson interval in the dispatch belongs to a different fraction

The dispatch states: "Wilson 95% on the earlier 6-in-79 was 2.4%-14.8%."

**2.4% to 14.8% is the Wilson interval for 4/65, which is arm A alone.** It is computed correctly in
§K of the earlier pre-registration and then carried down to §L.2, where it is attached to 6/79. The
dispatch inherited it from there.

| fraction | point | Wilson 95% | Clopper-Pearson 95% |
|---|---|---|---|
| 4/65 (arm A alone) | 6.2% | **2.4% to 14.8%** | 1.7% to 15.0% |
| 6/79 (the archive's last CI figure) | 7.6% | **3.5% to 15.6%** | 2.8% to 15.8% |
| 8/86 (the dispatch's floor, not re-derived here) | 9.3% | **4.8% to 17.3%** | 4.1% to 17.5% |

**This matters because the dispatch's whole sizing rule keys off that lower bound**, and rightly so.
The bound is too low by a third to a half, which inflates every derived n:

| sizing rate | runs for 80% chance of at least one red | for 95% |
|---|---|---|
| 2.4%, the dispatch's figure | 67 | 124 |
| 3.5%, correct for 6/79 | 45 | 84 |
| 4.8%, correct for 8/86 | 33 | 61 |

The earlier pre-registration's §L.2 table ("lower bound, 0.024 → n ≈ 375 per arm, not practical by
this method at all") rests on the same wrong figure. That conclusion may well survive recomputation,
but it has to be recomputed rather than quoted, and it is not this document's calculation to redo.

The sizing discipline itself is unaffected and is adopted here: size off the lower bound, never the
point estimate.

---

## D. PRE-REGISTERED: three probes, in order, with what each outcome licenses

Fixed before any run. The order is deliberate: probe 1 does not involve a rate at all, so it cannot
be defeated by the power problem that defeated the last comparison.

### D.1 The outcome measure, fixed now, for every probe

A **red** is: the full-suite or paired run's JUnit XML contains exactly the failure
`JournalTabTest > From Album on the edit form opens the picker and pulls the selected photo into the
entry`, `AssertionError` naming `ContentDescription = 'Log photo'`, at `JournalTabTest.kt:374`.
Read from `app/build/test-results/testDebugUnitTest/TEST-*.xml`, never from a Gradle summary line.
Any other failure in the same run is recorded and does not count as a red for this measure.

**Copy `app/build/test-results/testDebugUnitTest` aside before re-running anything.** The flake note
records that this artifact was destroyed at least three times before it was kept once.

### D.2 Probe 1: the deterministic race proof. No rate, no power calculation

**Question:** can the assertion at `:374` lose to the decode at all?

**Method:** two parts, either of which answers it.

1. **Instrument.** Add a temporary timestamped log at `DecodedPhoto`'s decode completion and at the
   assertion, and read the ordering from `ShadowLog` on a run of `JournalTabTest` alone. Confirms or
   refutes that the assertion can execute before the bitmap is assigned.
2. **Force it.** Occupy `Dispatchers.IO` with a controlled blocker before the picker click, then run
   the test. If the assertion fails while the picker's own `waitUntil` still passes, the race is
   demonstrated.

**PRE-REGISTERED reading:**

- **A failure under forced contention is an existence proof that the assertion can lose the race.**
  It needs no p-value, in the same way arm B's single red needed none.
- **It is not evidence that this is what happens in the real suite.** Forced contention is an
  artificial condition. A positive here licenses "the mechanism is real and the assertion is
  unguarded against it", and nothing about the observed rate or about which class supplies the
  contention in practice.
- **A null here is informative and is the strongest null available anywhere in this design.** If the
  assertion cannot be made to fail even with the pool deliberately saturated, the decode-race
  candidate is refuted and section 3 of the origin report should be marked withdrawn. Record it that
  way.

**Cost:** one to three local runs. No CI. This probe is why the other two may never be needed.

### D.3 Probe 2: the pair probe, in a session container

**Question:** does `DecodedPhotoTest` immediately followed by `JournalTabTest` reproduce it?

**Environment:** a Linux session container, per section B. **Not** the Windows host. If only the
Windows host is available, this probe is not run and is recorded as not run, not as green.

**PRE-REGISTERED reading, and this is the part the owner asked to fix in advance:**

- **A red is decisive and cheap.** It converts the flake into a bug with a named pair and ends the
  search. Report it immediately; do not accumulate draws first.
- **A null licenses nothing at any n below the table in section E.** Specifically, a null at fewer
  than **45 paired runs** does not distinguish "this pair is not the mechanism" from "the flake did
  not fire", and must be reported as *no measurement*, not as a negative result. The phrase to use
  is "did not reproduce in N runs, which at the lower-bound rate is what a live flake looks like
  most of the time."
- **A null at any n does not exonerate the decode-race hypothesis**, only this pair. Probe 1 is what
  tests the hypothesis; probe 2 tests one instance of it.
- **The pair is not expected to be sufficient on its own.** The mechanism says contention supplies
  the delay, and one adjacent class supplies little contention. A high-contention variant, the pair
  preceded by a known leaker such as `TrackRecordingServiceTest` with `drainBeforeTeardown()`
  temporarily removed, is the more promising arm and is pre-registered here as arm 2b. Its null
  carries the same restriction.

### D.4 Probe 3: CI draws

**Only if probes 1 and 2 both fail to discriminate.** If it is reached, it needs its own
pre-registration with n derived from the corrected lower bound in section C, and it inherits the
mechanics the earlier document already established: no `workflow_dispatch`, draws obtained by PR
plus `gh run rerun`, a 50-re-run cap, sequential draws per ref at roughly 5.5 minutes each.

**Catching a red to instrument is itself subject to the rate.** At 4.8%, seeing one red costs about
33 runs for an even-money chance and 61 for a confident one. That is the number to price before
committing, and it is the argument for exhausting probe 1 first.

---

## E. The table that decides whether a null is worth anything

Probability that N runs come back all green **while the flake is live**, at each candidate rate:

| N | at 9.3% (point, 8/86) | at 7.6% (point, 6/79) | at 4.8% (lower bound, 8/86) | at 3.5% (lower bound, 6/79) |
|---|---|---|---|---|
| 5 | 0.62 | 0.67 | 0.78 | 0.84 |
| 10 | 0.38 | 0.45 | 0.61 | 0.70 |
| 20 | 0.15 | 0.21 | 0.37 | 0.49 |
| 30 | 0.05 | 0.09 | 0.23 | 0.34 |
| 45 | 0.01 | 0.03 | 0.11 | 0.20 |
| 61 | 0.00 | 0.01 | 0.05 | 0.11 |
| 84 | 0.00 | 0.00 | 0.02 | 0.05 |

Read the fourth column, not the second. **A five-run null is 78% likely from a live flake.** That is
the number that has to be in front of anyone reporting one.

---

## F. Recorded instance: claims that propagated because nobody asked what they rested on

The owner named this as a class, alongside the 294-artifact count and the
`verify-policy-permissions.sh` tripwire. Three instances from this flake's own record, all found in
one reading pass:

1. **"Pre-existing."** Written in `6d99a98`, `2876df0` and `DISPATCH-REPORT.md:11`. Nothing precedes
   the first of them. The word appears to have meant "not caused by my diff" and was read as "older
   than this investigation". It made the flake look unboundedly old and made the origin look
   unfindable, which is the framing the origin dispatch inherited.
2. **"Every sighting is CI."** Section B. Built by reading a two-column tally as if it were
   exhaustive. It would have ruled out the cheapest probe available.
3. **"Wilson 95% on 6-in-79 was 2.4% to 14.8%."** Section C. An interval that belongs to 4/65,
   carried one section down its own document and then out into a dispatch, where it doubled a
   budget estimate.

**What they share.** None is a careless error. Each is a correct statement about a smaller thing,
re-used as a statement about a larger thing, in a document that was itself careful. The failure is
never in the original; it is in the citation. And each survived because the claim was reasonable, so
nobody asked what it rested on.

**The check that catches it** is not scepticism, it is counting. The stop-action session had every
licence to call its two reds pre-existing, and its report says so in as many words: "It would have
been easy, and wrong, to call this pre-existing. What ruled it out was counting." That session ran
eleven suites and got the right answer. It is the counter-example that shows the discipline works.

**Proposed as a `CLAUDE.md` "Known pitfalls" bullet, not added here:** when citing a figure, an
interval, or a categorical claim from another document, re-derive its denominator or quote its
scope. A number that has crossed a document boundary has lost the sentence that bounded it.

---

## G. What this document does not license

- It reports no outcome. No probe has been run.
- The decode race remains **inferred**. Every recorded property of the flake fits it and none
  contradicts it, and that is not the same as having executed it. Probe 1 is what changes that.
- Nothing here proposes changing `JournalTabTest.kt:374`. The `waitUntil` option stays what the
  origin report called it: the owner's decision, with the tradeoff stated, and not to be taken by a
  session that was not asked.
- Section C corrects an input to the earlier pre-registration's §L.2 power table. It does not
  recompute that table.

---

## Amendment, 2026-09-10: §L.2's power table recomputed, and a fourth propagation found inside it

Section C said the earlier pre-registration's §L.2 table was not this document's calculation to redo.
The owner asked for it, so it is redone here. The model is that document's own, taken from §J.5 and
verified against its published figures before use: power = P(X ≥ 6) for X ~ Bin(n, p), with the
substituted arm assumed truly clean, threshold r ≥ 6. Reproducing its p = 0.14 row exactly (n = 50
gives 0.719, n = 65 gives 0.907, 80% arrives near n = 57) confirms the model is the right one.

### The recomputed table

| sizing rate | n per arm for 80% | for 90% | α at r ≥ 6 |
|---|---|---|---|
| 9.3%, point (8/86) | 84 | 98 | 0.029 |
| **4.8%, lower bound (8/86)** | **164** | 192 | 0.030 |
| 7.6%, point (6/79) | 103 | 120 | 0.029 |
| **3.5%, lower bound (6/79)** | **225** | 263 | 0.030 |
| 2.4%, the wrong bound | 328 | 385 | 0.031 |

The threshold is stable across all of these: `2·C(n,6)/C(2n,6)` rises from 0.028 at n = 65 toward
0.031 asymptotically and never approaches 0.05, while r = 5 never clears it. The pre-registered
decision rule survives at every n in the table.

### Does "not practical by this method at all" survive?

**Not as stated.** At ~5.5 minutes per sequential draw and GitHub's 50-re-run cap:

| n per arm | workflow runs per arm | wall clock per arm, arms in parallel |
|---|---|---|
| 65, already run | 2 | ~6 h |
| 164, at the 4.8% bound | 4 | ~15 h |
| 225, at the 3.5% bound | 5 | ~21 h |
| 328, at the wrong bound | 7 | ~30 h |

164 per arm is two and a half times what has already been spent, on a public repository where
Actions minutes are free. That is a large spend and a defensible one. It is not "not practical at
all," which was a verdict about 375.

**The conclusion still stands, on the other argument.** §L.3's reasoning is untouched by any of this:
the next step is a targeted reproduction, not a rate estimate, so it inherits no interval and needs
no power calculation. That was always the stronger argument for not sizing another comparison. What
has changed is that it is now the only one.

### The fourth instance: §L.2's table is the heuristic §J.5 had already retired

§J.5 identifies `n ≈ 9/p` as the crude rule that produced the original power error, and replaces it
with an exact binomial. §L.2, four sections later, is `9/p` again:

- 9 / 0.076 = 118.4, printed as 118
- 9 / 0.024 = 375.0, printed as 375

Both are labelled "~80% power." The exact figures for 80% are 103 and 328; the printed n's actually
carry about 89%. The error is in the safe direction this time, which is why nothing caught it.

**What makes this the sharpest of the four:** the correction and the reintroduction are in the same
document, by the same author, four sections apart, and the reintroduction is not a lapse in care.
§J.6 says of the earlier fix, "no arm outcome has been read", and that discipline held. A correction
fixes the sentence it lands in. It does not sweep forward through the document it lives in unless
someone walks the document again afterwards.
