# Findings: the `JournalTabTest` photo-pull flake — both halves of the boundary

**Date:** 2026-09-09. **Dispatch:** backup-ruling docs (task e). **Status:** a findings note. No
test was changed, skipped, ignored, weakened or added to any allowlist by it; this note is
documentation only. Its whole purpose is to write down two claims that are easy to collapse into
one, because collapsing them is how this stops being investigated.

**Why its own note rather than an extension of
`docs/audits/2026-09-09-windows-only-test-failures.md`:** that note is about failures seen on one
Windows host and not on Linux, and its decision ("CI/Linux is the authority") depends on that being
the shape of the problem. This flake is the opposite shape — it happens **on Linux, in CI-equivalent
full-suite runs**, and never on the host-specific list. Filing it inside a host-specific note would
put it under a decision that does not apply to it. The Windows note has been amended with a pointer
here instead, and the amendment corrects its one paragraph that recorded this as a non-reproducing
one-off.

## The test

`com.forager.app.ui.log.JournalTabTest > From Album on the edit form opens the picker and pulls the
selected photo into the entry`.

Failure, identical every sighting where the message was recorded:
`java.lang.AssertionError: Assert failed: The component with ContentDescription = 'Log photo'
(ignoreCase: false) is not displayed!`, at **`JournalTabTest.kt:374`** — the assertion after the
picker returns to the edit form, not the `waitUntil` above it.

## ESTABLISHED

**Four independent sightings on 2026-09-09, on four distinct trees, by four sessions.**

| # | Tree | Where recorded | Line / message |
|---|---|---|---|
| 1 | `main` | Relayed by the planner; identified as this test in `2026-09-09-allow-backup-and-play-signing-notes.md` ("the same single unrelated flake the planner saw on `main`") | Line number **not relayed**; recorded there as 1 failure in 1277 tests |
| 2 | `claude/recording-stop-action` | `2026-09-09-recording-notification-stop-action-completion-report.md`, runs 4 and 6 of 11 | `JournalTabTest.kt:374`, message as above |
| 3 | `claude/backup-and-signing-notes` (manifest attribute + comments only — **no test changes at all**) | `2026-09-09-allow-backup-and-play-signing-notes.md`, run 1 of 3 | `JournalTabTest.kt:374`, message as above |
| 4 | `claude/backup-ruling-docs` (this dispatch — **documentation and one comment block, no code and no test changes**), cut from `claude/integration-2026-09-09` | This note; full suite 167 suites / 1303 tests / **1 failure** / 0 errors / 24 skipped | `JournalTabTest.kt:374`, message character-for-character identical; class re-run alone immediately after: 14 tests, 0 failures |

Sighting 4 matters beyond adding to the count: this tree carries the `drainBeforeTeardown()` fix
(`app/src/test/java/com/forager/app/service/TrackRecordingServiceTest.kt:170,183`, present here via
the merge of #82) **and the flake fired anyway**. That is direct evidence the drain did not close
it — a stronger statement than anything in "half two" below, which was written before this run.
What it does *not* say is that the drain is useless: the drain addresses one specific leak out of
`TrackRecordingServiceTest`, and this failure could involve a different predecessor entirely — in the
execution order captured below, `TrackRecordingServiceTest` ran 38 classes and about 91 seconds
before `JournalTabTest`, so it is not the immediate predecessor (which does not by itself rule out
a leak that survives that long — nothing here measures how long leaked work persists).

**It passes in isolation every time.** All three sessions that hit it re-ran the class alone
immediately afterwards: 14 tests, 0 failures, each time. Nobody has ever seen it fail outside a full-suite run.

**It is older than 2026-09-09.** `docs/audits/2026-08-31-session-handoff.md:50-51` records the same
test failing in a full suite on `0ca4eee` (then at `JournalTabTest.kt:293` — the file has changed
since; same test name, same "passes alone, fails in the full suite" shape), and
`docs/audits/2026-08-29-pr52-update-report.md:48` records it as a known pre-existing full-suite-only
flake. Several completion reports since then note explicitly that it *did not* fire on their run,
which is why the base rate looks like "occasionally".

## The boundary — both halves, because one without the other is misleading

**Half one: it is NOT caused by the Stop-action work (#82).** The stop-action session measured its
own new test as the cause — baseline green 3/3, the branch with the new test green 1/3, the branch
with only that test method removed green 2/2 — and added `drainBeforeTeardown()` on that basis. That
attribution is **withdrawn**, and the thing that withdraws it is sighting 3: a later session
reproduced the identical failure at the identical line on a tree that contains **no test changes
whatsoever** (`claude/backup-and-signing-notes` differs from `main` by one manifest attribute and
two comment blocks), and sighting 1 is on `main` itself. A flake that fires on `main` cannot have
been introduced by a branch that was not merged yet.

**Half two: it is NOT exonerated by that either — and these are different claims.** "Not #82's
doing" is not "solved", and a later session reading only half one will treat this as closed. What
the stop-action measurements actually support is weaker than either reading:

- Eleven runs is a small sample of a flake whose observed rate is roughly one failure in three to
  eight full-suite runs. A 3/3 green baseline is entirely consistent with a flake at that rate
  simply not firing — 3 green runs is what you expect most of the time from a coin that lands
  "fail" one time in three.
- The three green runs *with* `drainBeforeTeardown()` are equally consistent with "the drain fixed
  it" and with "the flake did not fire". The stop-action report says exactly this itself ("Three
  green runs cannot prove a flake is gone"), and it is repeated here because that caveat is the
  part that gets dropped in summary.
- The drain has not been shown to be useless either. Sighting 3's tree predates #82 and therefore
  has no drain, so it says nothing about whether the drain helps; it only says the flake exists
  without the stop-action test. **Nothing here is an argument for removing `drainBeforeTeardown()`,
  and it should not be removed on the strength of this note.**

**So the honest state is: cause unproven.** The standing hypothesis is ordering or inter-test
interference — work leaked out of an earlier test class into `JournalTabTest`'s Robolectric sandbox
(the mechanism `DISPATCH-REPORT.md:10-14` named long before #82, and the reason the stop-action test
needed a drain in the first place). That hypothesis is unproven too: no session has isolated which
predecessor, which leaked object, or which shared state.

## What would actually discriminate — steps 1 and 3 now done, step 2 not

1. **Read the order — DONE ONCE, on sighting 4, and recorded below.** Every full-suite run leaves
   `app/build/test-results/testDebugUnitTest/TEST-*.xml` with per-class timestamps. Sightings 1-3
   were all re-run to green before anyone looked, which overwrites the artifact; this dispatch
   copied the whole directory aside first. **If you hit this failure, copy
   `app/build/test-results/testDebugUnitTest` somewhere before re-running anything.** It is the
   cheapest evidence available and it was destroyed at least three times before it was kept once.
2. **Force the suspected order.** Run `JournalTabTest` immediately after the predecessor class the
   XML names, alone, repeatedly. A pair that reproduces it converts this from a flake into a bug.
3. **Vary only the order — DONE, and the answer is below: the order does not vary.** Gradle's test
   execution order across many classes is not guaranteed stable, so the same tree was run twice
   (once red, once green) and the class order diffed. It is identical, which moves the hypothesis
   off "the order changed" and onto "something within a fixed order finishes late".

## The execution order on the failing run (sighting 4) — first time this was preserved

Read from the JUnit XML of the failing full-suite run (`timestamp` and `time` attributes of each
`TEST-*.xml`, sorted). `app/build.gradle.kts` sets no `maxParallelForks` and no `forkEvery` (its
`testOptions` block sets only `isIncludeAndroidResources`), so Gradle's default applies — a single
forked JVM — and this order is a genuine sequential order rather than an interleaving of workers.
Every XML in the run carries `hostname="vm"`, consistent with one worker.

`JournalTabTest` ran **139th of 167 classes**. The three classes immediately before it, in order:

| Position | Class | Started | Duration |
|---|---|---|---|
| 136 | `com.forager.app.ui.log.CartographyViewModelTest` | 07:16:36.827Z | 1.723 s |
| 137 | `com.forager.app.ui.log.DecodedPhotoTest` | 07:16:38.566Z | 0.506 s |
| 138 | `com.forager.app.ui.log.FindsGalleryScreenTest` | 07:16:39.082Z | 0.698 s |
| **139** | **`com.forager.app.ui.log.JournalTabTest`** | **07:16:39.800Z** | **2.405 s (1 failure)** |

`com.forager.app.service.TrackRecordingServiceTest` — the class whose leak
`drainBeforeTeardown()` addresses — ran at position 101, 07:15:08.504Z, about 91 seconds and 38
classes earlier.

**And the passing run has the same order.** The suite was re-run on the identical tree
immediately afterwards (167 suites, 1303 tests, **0 failures**, 0 errors, 24 skipped) and its class
start order was compared position by position against the failing run's: **identical, all 167
classes, no divergence at any index.** `JournalTabTest` ran 139th in both.

That does real work on the hypothesis. "Ordering or inter-test interference" was one phrase
covering two different mechanisms, and this separates them:

- **The order itself varying between runs — disconfirmed for this pair.** The same tree produced
  the same class sequence in a red run and a green run, so a reshuffle is not what distinguishes
  them. Stated with its limits: two runs, one machine, one Gradle version, and this compares
  *class* start order, not method order within a class. It is evidence, not a proof of determinism.
- **Interference within a fixed order — untouched, and now the whole of the hypothesis.** If the
  sequence is the same both times, what differs is timing or leaked state: work started by an
  earlier class that sometimes has and sometimes has not finished by the time `JournalTabTest`
  composes its edit form. That is consistent with everything recorded above, and nothing here names
  which earlier class.

So step 3 is done and step 2 is now the one that matters: take the immediate predecessors listed in
the table (and, further back, any class known to leave async work behind), run them paired with
`JournalTabTest` repeatedly, and find a pair that reproduces. The raw XML of both runs lived only
in this session's scratch directory, which does not survive the session — **the durable record is
this section**, which is why the order and the comparison are written out rather than summarised.

Until step 2 lands a reproducing pair, "inter-test interference" remains a hypothesis with no
evidence naming a specific interaction, and should be described that way.

## Explicitly not in scope, and not to be done quietly

Per `CLAUDE.md` ("Silencing a test is never in scope for a dispatch that didn't ask for it"): this
test must not be `@Ignore`d, skipped, added to the CI `SKIPPED_TESTS_ALLOWLIST`, retried
automatically, or have its assertion weakened, by any dispatch that was not explicitly asked to
reduce coverage here. Diagnosing it is legitimate work available to anyone; deciding to live with
less coverage is the owner's decision, not a session's convenience. The assertion it fails on is a
real user-visible claim — that a photo picked from the album actually appears on the entry — and
`docs/audits/2026-08-31-session-handoff.md:58-64` records that the same behaviour was separately
reported broken on-device, which is a reason to treat this test as valuable rather than noisy.
