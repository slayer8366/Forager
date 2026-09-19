# `JournalTabTest`'s "From Album" assertion: an intermittent CI failure, eleven recorded occurrences, twice on `main`

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Found at:** `a5c4467` ·
**Scope:** record and diagnose, **not fix** · **Host:** native Ubuntu 26.04, toolchain matched to CI

**One-paragraph outcome.** One test has been failing CI intermittently since 2026-08-25 and has never
been recorded. `JournalTabTest#From Album on the edit form opens the picker and pulls the selected
photo into the entry` fails on its final assertion with the same message every time — the `Log photo`
node **exists and is not displayed** — and passes on a re-run of the identical tree. **It has failed
twice on `main`, so it is not a branch artifact**, and it is not in CI's `SKIPPED_TESTS_ALLOWLIST`, so
every occurrence has been adjudicated by whoever was in front of it, each one looking like a one-off.
Eleven occurrences are recorded below across five branches. Of the 27 failed CI runs whose artifacts
are still readable, **10 feature this test, and in 9 of those 10 it was the only failing test in the
run** — which makes it the single most frequent cause of a red CI run in the readable window. What is
established is the signature and the frequency; **the mechanism is not established**, and §4 names the
evidence that would settle it. Nothing was fixed and no test was touched: per CLAUDE.md, diagnosing is
the job and deciding to reduce coverage is the owner's.

---

## §1 — The failure, exactly

```
java.lang.AssertionError: Assert failed:
The component with ContentDescription = 'Log photo' (ignoreCase: false) is not displayed!
  at androidx.compose.ui.test.AssertionsKt.assertIsDisplayed(Assertions.kt:34)
  at ...ui.log.JournalTabTest.From Album ...(JournalTabTest.kt:375)
```

The failing line is the test's **final** assertion. Its line number drifts with the file (293 → 374 →
375 across the occurrences below); the assertion is the same one throughout, and the message is
byte-identical in every occurrence checked.

**The node existed.** The throw comes from `AssertionsKt.assertIsDisplayed` *after* node lookup — a
missing node raises a different error ("could not find any node"). This is a visibility failure, not a
missing-state failure. That distinction is what rules out the obvious first guess.

**It is fast.** 0.055s at `a5c4467`, and 0.062–0.146s across the other occurrences. This is not the
`runTest` poll-loop stall family, and not a timeout.

## §2 — Frequency, and what the denominator actually is

| sha | branch | date (UTC) |
|---|---|---|
| `f23b60f9` | `claude/forager-m3-expressive-design-l4c` | 2026-08-25 |
| `5b7023fd` | `claude/forager-m3-expressive-design-l4c` | 2026-08-26 |
| `6d99a989` | `claude/forager-m3-expressive-design-l4c` | 2026-08-26 |
| `a207f9d3` | `claude/forager-m3-expressive-design-l4c` | 2026-08-26 |
| `df039321` | `claude/mapicon-pill-anchor-oonnkj` | 2026-08-30 |
| `0ca4eee7` | `claude/mapicon-pill-anchor-oonnkj` | 2026-08-30 |
| `4236e6b8` | **`main`** | 2026-09-09 |
| `bddbb2a9` | **`main`** | 2026-09-10 |
| `459b930c` | `claude/new-session-vto65i` | 2026-09-14 |
| `a4524e99` | `claude/new-session-vto65i` | 2026-09-15 |
| `a5c4467` | `claude/new-session-vto65i` | 2026-09-16 |

Dates are the runs' own UTC timestamps, which is why the last is a day ahead of this document.

**The measurable rate, stated as what it is.** Of the **27** failed runs whose `unit-test-report`
artifact is still unexpired and readable, **10 feature this test** — and in **9 of those 10 it was the
only failing test in the run**. That ratio is the useful number for "how often does CI go red because
of this", and it is a **floor**: **18 of the 45 failed runs had expired artifacts and could not be
read at all**, so occurrences in those runs are unknown, not absent.

**What not to do with these numbers.** Dividing occurrences by the 531 total runs in the window gives
about two percent, and that figure should not be treated as a rate. Its numerator is a floor drawn
from a partial sample and its denominator counts runs whose results were never inspected. It is an
order of magnitude, useful only for saying that catching a handful of instances deliberately would
take on the order of a hundred-plus runs — which is why chasing the mechanism by repetition is its own
dispatch and not a detour.

**The count is split by the package rename.** Occurrences before the rename record the class as
`com.forager.app.ui.log.JournalTabTest` and after it as
`com.zynergylabs.forager.app.ui.log.JournalTabTest`. A search on either name alone finds 7 or 3 and
undercounts. They were confirmed to be one defect rather than one name by comparing the failure
message and content description across occurrences, not by matching the method name.

**It is not allowlisted.** This test is absent from CI's `SKIPPED_TESTS_ALLOWLIST` (24 entries, which
the summarize step enforces as a two-directional identity). It is an unmanaged intermittent failure,
not a known-silenced one.

## §3 — What is established, and what is not

**Read, from the archived JUnit XML and the source:**

- The node exists and is not visible (§1).
- **Not a coroutine race.** The harness's `onPullPhoto` (`JournalTabTest.kt:149-158`) is a plain
  synchronous `uiState` mutation — no dispatcher, no suspension. The state was present.
- **Not an unwaited load.** The test waits where the author knew it had to —
  `waitUntil(timeoutMillis = 5_000)` for the picker's photos — and the failure is past that point, on
  the return path to the edit form.
- The class carries `@Config(sdk = [36])` and **no `qualifiers`**, so it renders at Robolectric's
  default screen size.

**Observed:** the identical tree passes. CI attempt 2 on `a5c4467` is green. Locally the full suite is
green 5/5 and the class alone 15/15 — **this machine does not reproduce it**, which bounds what a
local green proves about this test and is itself worth knowing.

**Not established: the mechanism.** Why the node is not visible is unknown. Two hypotheses are open
and are not distinguished by anything measured so far:

1. **Off-screen** — the pulled-in photo lands outside the viewport on the default Robolectric screen,
   and the margin is thin enough to tip on a slower or differently-loaded host.
2. **Covered** — the node is within the viewport but something overlaps it.

**A resemblance, and explicitly not evidence.** Hypothesis 2 has the same shape as the covered-control
problem CLAUDE.md already records on the map surface, where the icon-bar drag handle covered the
locate row's whole centre for several dispatches while a semantic click would have passed. That makes
it the right first place to look. It is a resemblance between symptoms, not a finding about this test,
and it should not be cited as support for hypothesis 2 until §4's measurement exists.

## §4 — What would close it

The mechanism dispatch should not start by trying to reproduce the failure. Reproduction is expensive
here (§2) and unnecessary for the first discriminating measurement, which can be taken **on a passing
run**:

- **The node's measured bounds** at the point of the final assertion —
  `fetchSemanticsNode().boundsInRoot` and `boundsInWindow` for the `Log photo` node.
- **The viewport size** for the same run — the root node's bounds, and the display metrics Robolectric
  is using for `@Config(sdk = [36])` with no `qualifiers`.
- **Whether any other node overlaps those bounds**, and what it is.

What each outcome means, so the run cannot be inconclusive:

- Bounds **outside or flush against** the viewport on a pass → hypothesis 1. The test is passing by a
  thin margin, and the question becomes what varies. Both answers are informative: a comfortable
  margin argues *against* hypothesis 1 as much as a thin one argues for it.
- Bounds **inside** the viewport with an overlapping node → hypothesis 2, and the overlapping
  composable is named.
- Bounds inside, nothing overlapping, comfortable margin → **both hypotheses are wrong**, and the
  dispatch has learned the most useful thing available before spending a single repetition.

This measurement does not require changing the committed test. It can be taken in a scratch harness or
a throwaway branch; adding a probe, a wait, a scroll, or a `qualifiers` line to the test as it stands
is a coverage decision and belongs to the owner, not to the investigation.

## §5 — What was and was not done

- **Recorded, not fixed.** No production change, no test change, no skip, no allowlist entry. `git
  diff --stat` on `app/` is empty for this change.
- **Not fixed deliberately.** A `waitUntil`, a `performScrollTo`, or a `qualifiers` line would each
  make this test pass, and at least one would do so without establishing why it failed. Per CLAUDE.md,
  "diagnosing the cause is the job; deciding to reduce coverage belongs to the owner."
- **Not attempted:** reproduction by repetition. At the rate in §2 this needs on the order of a
  hundred-plus runs, and with no local reproduction it is open-ended. Scoped out as its own dispatch
  rather than run as a detour before a merge that is otherwise clear.
- **Method note, so the figures are checkable.** The occurrence table was built by downloading every
  unexpired `unit-test-report` artifact for every failed run in the window and parsing the JUnit XML,
  not by reading run pages. The first parse returned nothing because it selected the failure element
  with `tc.find("failure") or tc.find("error")`: an XML element carrying text but no child elements is
  falsy in Python, so the expression fell through to `find("error")` and yielded `None`. Corrected to
  an explicit `is not None` test. **Every figure in this document comes from the corrected parse** —
  the bug silently undercounted, and would have made this look rarer than it is.
- **Not verified:** the contents of the 18 failed runs whose artifacts have expired. They are counted
  as unknown throughout, never as clean.
