# A red that proves nothing: when seeing the test fail is not evidence

**Date:** 2026-09-12
**Found in:** the resync-recording-state dispatch
([completion report](2026-09-12-recording-state-resync-completion-report.md))
**Why its own note:** this is a new variant of the family this project keeps recording, and unlike
the others it defeats a rule that was specifically written to catch it.

---

## The rule it defeated

The dispatch said, correctly:

> Run it against the unfixed code and watch it fail. Report the failure message. **A test that has
> never been seen to fail has not been tested.**

That rule exists because a new assertion can be vacuous, and the cheapest proof it is not is to see
it go red. The rule was followed. A red was produced, reported, and cited as satisfying it.

**The red was worthless.**

## What happened

The new test asserted `TrackRecordingUiState.isRecording` after simulating a resume. Its first
version checked that state after a single `idle()` / 25 ms sleep / `idle()` cycle.

The fix under test re-reads the track's row from Room. That is a database round-trip off the main
thread. One settle cycle is not reliably enough for the read to come back and the state update to
land.

So the test failed **whether or not the fix was present**. Against the unfixed build it failed
because nothing cleared the state. Against the fixed build it would have failed because the
assertion ran before the clearing did. The red satisfied the letter of "watch it fail" and carried
no information about the defect at all.

It was reported at the time as evidence that the test bites. It was not.

## What caught it

Not suspicion, and not review. **A number that did not fit.**

After the fix landed, the test still failed. Reading the two runs side by side:

| Build | Result | Duration |
|---|---|---|
| Fix present, single-settle test | FAILED | fast |
| Fix present, polling test | passed | **0.652 s** |
| Fix reverted, polling test | FAILED | **10.599 s** — the full poll deadline, never cleared |

The 0.652 against 10.599 is the discriminator. A settling artifact cannot produce that gap; only the
state actually clearing can. Once the test polled instead of settling once, the same two builds
produced a red and a green that mean what they say.

This is the same act that caught every earlier member of the family: a count or a figure read
against something outside the check. The 289 fixes against 344 log lines. `git diff --stat` a file
short. The 251 CR lines. Here, a stopwatch.

## The companion rule

The existing rule is necessary and, on its own, insufficient. Its companion:

> **Seeing a test fail is not evidence unless it could have passed.** A red proves the assertion
> bites only if the same test, unchanged, goes green against the fixed build. If the unfixed red and
> the fixed green were produced by two different versions of the test, the red belongs to the
> version that was thrown away and says nothing about the one that shipped.

Practical form, and it costs nothing:

1. Write the test. Run it against the unfixed build. Note the failure **and its duration**.
2. Apply the fix. Run the **same, unchanged** test.
3. If it does not pass, the next edit is to the test, and **the earlier red is void** — it was
   produced by a version that no longer exists.
4. When it does pass, re-run the revert against the version that passed, from a saved copy. That
   pair is the evidence. Nothing before it is.

Step 3 is the one this dispatch got wrong. The natural move on a still-failing test is to fix the
test and carry on, quietly keeping the original red in the report. That red is now about a different
test.

## Why this one is worth a note of its own

The other instances in `CLAUDE.md` are checks decoupled from the thing they check by a step nobody
traced: a coincident default, a stale artifact, a lossy filter, an unreachable path. Those are
failures to notice a gap.

This one is different in kind. **The gap was closed by an explicit rule, the rule was followed, and
it still passed on nothing** — because the rule constrains what you must observe and not whether
what you observed could have come out otherwise. A process designed to prevent exactly this failure
produced exactly this failure, and the only thing that caught it was a duration.

## Recorded honestly

The contaminated run is left in the completion report rather than deleted, and the commit that
introduced it is not rewritten. A report that shows only the evidence that survived is a report that
cannot be audited, and the deletion would have removed the single most useful finding in the
dispatch.
