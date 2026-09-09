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

**Three independent sightings on 2026-09-09, on three distinct trees, by three sessions.**

| # | Tree | Where recorded | Line / message |
|---|---|---|---|
| 1 | `main` | Relayed by the planner; identified as this test in `2026-09-09-allow-backup-and-play-signing-notes.md` ("the same single unrelated flake the planner saw on `main`") | Line number **not relayed**; recorded there as 1 failure in 1277 tests |
| 2 | `claude/recording-stop-action` | `2026-09-09-recording-notification-stop-action-completion-report.md`, runs 4 and 6 of 11 | `JournalTabTest.kt:374`, message as above |
| 3 | `claude/backup-and-signing-notes` (manifest attribute + comments only — **no test changes at all**) | `2026-09-09-allow-backup-and-play-signing-notes.md`, run 1 of 3 | `JournalTabTest.kt:374`, message as above |

**It passes in isolation every time.** Both sessions that hit it re-ran the class alone immediately
afterwards: 14 tests, 0 failures, each time. Nobody has ever seen it fail outside a full-suite run.

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

## What would actually discriminate, none of which has been done

1. **Read the order.** Every full-suite run leaves `app/build/test-results/testDebugUnitTest/TEST-*.xml`
   with per-class timestamps. On a failing run, sort by timestamp and record which classes ran
   immediately before `JournalTabTest`, then compare against a passing run's order. Nobody has done
   this on a failing run — the failing runs have been re-run to green, which destroys the artifact.
   **If you hit this failure, copy the whole `test-results` directory somewhere before re-running
   anything.** That is the single cheapest thing available and it has been lost at least three
   times.
2. **Force the suspected order.** Run `JournalTabTest` immediately after the predecessor class the
   XML names, alone, repeatedly. A pair that reproduces it converts this from a flake into a bug.
3. **Vary only the order.** Gradle's test execution order across many classes is not guaranteed
   stable; running the same tree twice and diffing the class order tells you whether the order
   itself is what varies between a green run and a red one.

Until one of those is done, "ordering or inter-test interference" is a hypothesis with no evidence
naming a specific interaction, and should be described that way.

## Explicitly not in scope, and not to be done quietly

Per `CLAUDE.md` ("Silencing a test is never in scope for a dispatch that didn't ask for it"): this
test must not be `@Ignore`d, skipped, added to the CI `SKIPPED_TESTS_ALLOWLIST`, retried
automatically, or have its assertion weakened, by any dispatch that was not explicitly asked to
reduce coverage here. Diagnosing it is legitimate work available to anyone; deciding to live with
less coverage is the owner's decision, not a session's convenience. The assertion it fails on is a
real user-visible claim — that a photo picked from the album actually appears on the entry — and
`docs/audits/2026-08-31-session-handoff.md:58-64` records that the same behaviour was separately
reported broken on-device, which is a reason to treat this test as valuable rather than noisy.
