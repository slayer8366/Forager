# Findings: the `JournalTabTest` flake is suite-only, and always the same assertion

**Date:** 2026-09-09. **Written by:** the bridge session that landed PRs #81–#84, after the flake
turned PR #83 red — a three-file documentation change that cannot fail a test. **Status:** a
findings note. Nothing was silenced, skipped, ignored or weakened; the skip allowlist was not
touched. **Cause is not established** — see "What is not established", which is half the point of
this note.

Companion to `2026-09-09-workers-builds-check-uncorrelated.md`. Same argument, different signal:
a check that goes red without reference to the change under review teaches reviewers to skip the
check list, and then a real failure lands in a list nobody reads. Two such signals is a pattern.

## What was measured

| run | contains PR #82's new `TrackRecordingServiceTest` case | `JournalTabTest` |
|---|---|---|
| local full suite, Windows, `claude/recording-stop-action` | yes | pass (14/14) |
| CI full suite, PR #82 head | yes | pass |
| CI full suite, PR #81 head | no | pass |
| CI full suite, PR #83 head (attempt 1) | yes, via `main` | **fail** |
| CI full suite, PR #83 head (attempt 2, re-run) | yes, via `main` | pass |
| local, **class alone**, `--tests "*JournalTabTest*"` | n/a | pass (14/14) |

Every one of those runs reports 166 suites and 24 skipped, pass or fail.

## What this narrows

**It is an ordering or interference effect, not a defect in the test's own logic.** The class passes
14/14 when run alone and has only ever failed inside a full suite. Reading the test body for a bug
is the wrong next step.

**It is always the same assertion.** `JournalTabTest.kt:374`, `java.lang.AssertionError`, in
`From Album on the edit form opens the picker and pulls the selected photo into the entry`. Line 374
is the final `composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()` — the
assertion that the pulled-in photo is showing on the edit form after the picker closes. Not the
picker opening, not the `waitUntil`, not the Camera/Import presence checks above it. Whatever the
mechanism is, it costs this one recomposition.

**It is intermittent, not deterministic.** One failure across three full-suite runs containing PR
#82's test, and the immediate re-run of the failing commit was green with identical figures
(166 suites, 1278 tests, 0 failures, 24 skipped).

## What is not established — both halves, deliberately

Two opposite over-readings are available here and **neither is supported**:

- **"It is not PR #82's doing."** The evidence for this is that if #82's service test caused the
  failure deterministically, #82's own CI run would have failed — and it passed, as did a local full
  suite with the test present. That argument rules out a *deterministic* cause. It does **not** rule
  out an intermittent one, and this failure is intermittent, so it does not settle the question.
- **"It is PR #82's doing."** `TrackRecordingServiceTest`'s `drainBeforeTeardown()` doc records this
  same photo-pull assertion failing 2 of 3 runs without the drain, versus 5 of 5 green without the
  test at all — a real measured mechanism (work escaping to `Dispatchers.Default` across Robolectric
  sandboxes). But one failure in three runs sits inside the variance that record itself describes,
  so these numbers **fail to exonerate** the drain as much as they fail to implicate it.

The honest state is: **cause unproven in both directions.** A later session should not read the
first bullet as settled — it is the more quotable of the two and the easier one to mistake for a
conclusion. The flake also predates PR #82 entirely; it is recorded in this repo as unexplained and
host-independent from before that branch existed.

## What would actually settle it

Not more full-suite runs at n=1. The distinguishing experiment is a repeated full suite with PR
#82's test present versus excluded — the same shape as the 2-of-3 / 5-of-5 measurement already in
`TrackRecordingServiceTest`'s doc, run enough times to separate a 1-in-3 rate from a 1-in-10 one.
That is a dispatch, not a side errand, and it is **not** beta-blocking.

## Standing rule for a red `JournalTabTest`

Re-run the class alone before calling anything a regression. It has passed alone every time it has
been tried. A red `JournalTabTest` on a documentation-only PR is this flake until an isolated run
says otherwise — and it is never grounds for an `@Ignore`, a widened skip allowlist, or a weakened
assertion, which CLAUDE.md forbids in any dispatch that did not ask for it.
