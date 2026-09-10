# Completion report — consolidate the outstanding docs branches into one PR

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Dispatch:** "Consolidate the outstanding docs branches into one PR" (written 2026-09-07; build,
docs only, two stop-and-ask points). **Date:** 2026-09-08. **Branch:**
`claude/consolidate-docs-2026-09-08`, cut from `main` at `cb16932` (the PR #77 merge).

## §1 — Before touching anything (reported, then acted on)

| Branch | Head | Merge-base with `main` | Rebased? | Files vs `main` |
|---|---|---|---|---|
| `claude/new-session-wjlg6g` | `13a031b` | `cb16932` (= `main`; contains it via merge `13a031b`) | No — linear commits plus one merge of `main` | `CLAUDE.md`, 4 files under `docs/` (see below) |
| `claude/new-session-pb8ynb` | `a5b1916` | `cb16932` (= `main`; contains it via merge `30b1391`) | No — linear commits plus one merge of `main` | one file: 4 lines added to the path-home report |
| `claude/new-session-b7z9bg` | `8dc10d4` | `8dc10d4` (its own head; `main` is 1 ahead — the merge commit) | No | none — fully merged by PR #77 |
| `claude/new-session-3x1aba` | `ab0033f` | `8dc10d4` (contains all of #77's content through two merges of `b7z9bg`; not the `main` merge commit itself) | No — linear commits plus two merges | `CLAUDE.md`, 5 files under `app/`, 3 files under `docs/` |

Every file outside `docs/`, `CLAUDE.md` and the index is on `3x1aba` and nowhere else:
`app/src/main/java/com/zynergylabs/forager/app/MainActivity.kt` (M), `domain/PaceLog.kt` (A),
`domain/PaceLogRecord.kt` (A), `ui/track/TrackRecordingViewModel.kt` (M), and the two tests
`domain/PaceLogRecordTest.kt` (A), `ui/track/TrackRecordingViewModelTest.kt` (M). `3x1aba` is
confirmed the only branch carrying production code.

## §2 — The duplicated ruling: collapsed cleanly, no wait needed

The ε ruling (`2026-09-07-ruling-path-home-self-intersection.md`), the path-home pre-build report
and its script (`2026-09-08-path-home-ratio-discriminators.py`) reached `main` through PR #77's
`f10d8f3` and `28793d7`. `pb8ynb` merged `main` on its own branch at `30b1391` (02:25 UTC), and
its byte-identical copies collapsed there: `git diff origin/main...origin/claude/new-session-pb8ynb`
is exactly one hunk, the four-line "Spot-check, 2026-09-08" paragraph `a5b1916` added on top. No
duplicate path, no spurious conflict, nothing deleted. The dispatch's wait condition ("the same
content arrives twice") did not occur, so the merge proceeded.

## §3 — The `3x1aba` split, file by file

**Taken onto the consolidation branch, by path, in commit `docs-take` (see log):**

- `docs/audits/2026-09-08-pass2-speed-comparison-log-line-prebuild-report.md` — the report with
  its three addenda (they are sections of the one file: "Addendum", "Addendum 2", "Addendum 3").
  Byte-identical to the branch's copy (`git diff --quiet` against it).
- Its index row in `docs/audits/README.md`, appended verbatim.
- Its `CLAUDE.md` entry — the twenty-line "(4) The same family one level up …" paragraph — inserted
  where the branch put it, continuing the "check that passes because it never saw the data" bullet.
  All twenty added lines verified present by exact match.

**Left on `3x1aba`, deliberately:**

- The five `app/` files above and their tests — the Pass 2 instrumentation, non-merging by that
  dispatch's own constraint, which nothing has lifted.
- `docs/beta/README.md`'s twelve-line owner's reading note on the `ForagerPace` log line. It is
  docs, but it describes a log line `main` does not emit; carrying it without the instrumentation
  would document an instrument the shipped build lacks. Not in the dispatch's list of what moves;
  flagged here as a decision, easily reversed by taking the paragraph later.

**Why by path and not by merge commit** (decided beyond scope): a merge commit with the
instrumentation files resolved back to `main`'s versions would record `3x1aba` as an ancestor of
`main`. A later merge of the instrumentation branch would then treat those files as already
merged and silently not bring them — the same shape as the reverted-variant runner restoring the
wrong copy. Taking by path leaves `3x1aba` un-merged in git's eyes, so its code merges normally
whenever that constraint is lifted. The source commits are named in the take commit's message.

**One reconciliation, no text dropped:** `wjlg6g`'s CLAUDE.md bullet ("The cheap question — who
calls this?") called the three pre-build reports "the fourth and fifth instances"; `3x1aba`'s
paragraph numbers Pass 1's reachability check as "(4)", and that no-caller finding is one of the
three. The bullet now says "further instances … beside the reachability check written up as (4)",
so the two arrivals do not number the same instance twice. Both texts otherwise verbatim.

## §4 — Build

1. Cut from `main` `cb16932`.
2. `git merge --no-ff origin/claude/new-session-wjlg6g` → `330022d`, 0 conflicts (the branch
   already contained `main`).
3. `git merge --no-ff origin/claude/new-session-pb8ynb` → `e66d471`, 0 conflicts.
4. `3x1aba` docs taken by path (above).
5. Index resolved after each step by keeping every row: 43 rows on `main` → 44 after `wjlg6g`
   → 44 after `pb8ynb` → 45 after the Pass 2 row → 46 with this report's row. Every row from
   `main` and from each of the three source branches is present (set difference computed, 0
   missing in each direction that matters).
6. Beta README: both new location questions kept, each with its never-cut reason.
7. No conflict markers anywhere in the tree (`git grep` for the three marker forms).

**Re-count, from this tree's `docs/beta/trip-report.md`, not from any recorded number:**

| Unit | Count |
|---|---|
| Location-block questions (column-1 lines between `LOCATION` and `COMPASS`) | **5** |
| Question lines, the template's original unit (each question once however it wraps; the four indented off-track sub-questions included; the "Only if it fired" gate line excluded, as the original 22 excluded it) | **24** |
| Raw lines inside the fenced block, blanks and headers included | 63 |

The template's own "22" was 22 questions by that first unit. The 26 recorded earlier — from an
unpushed trial and then from `wjlg6g`'s pushed merge — counted the two new questions' wrapped lines
as two each; it is superseded, not confirmed. The README now states 24 questions, five in the
location block, in that unit, and says why 26 is not the figure.

## §5 — Verify

Full suite on the consolidated tree (`./gradlew --no-daemon testDebugUnitTest`, SDK installed by
`scripts/setup-android-sdk.sh`, `BUILD SUCCESSFUL in 5m 43s`):

| suites | tests | failures | errors | skipped |
|---|---|---|---|---|
| 166 | 1277 | 0 | 0 | 24 |

Identical to PR #77's recorded result. Skip identity against the CI allowlist, both directions:
CI's own summarize step, extracted verbatim from `ci.yml`, exits 0 over the JUnit XML; an
independent read of the same XML gives an actual skip set of 24, allowlist of 24, 0 skipped-but-
not-allowlisted, 0 allowlisted-but-not-skipped. Nothing adjusted. A docs-only consolidation did not
change the test outcome.

## §6 — The PR

Opened against `main`; not merged — merging is the owner's step.

## Out of scope, confirmed untouched

The keystore, the in-place update test, the Play enrolment note; the three build dispatches; the
Cloudflare Workers check; every test.

## Required disclosure

**Confirmed:** everything in the tables above was read from `git` on this checkout; the suite ran
here. **Inferred:** nothing material. **Could not determine:** the actor who closed PR #78 — the
closing comment at 00:48:09 UTC is by the owner's account with the Claude Code footer, and
`3x1aba`'s `739790c` (00:48:46) records "PR #78 closed as superseded by #77", so it was the Pass 2
coder session under the owner's account. **Premises corrected:** the 26/five figure had also been
re-derived on a pushed merge (`wjlg6g` `13a031b`), not only the unpushed trial — and it was still
the wrong unit, which is the better argument for re-counting. **Decided beyond scope:** the
by-path take of `3x1aba`'s docs; leaving the `ForagerPace` reading note behind; the one-clause
numbering reconciliation in CLAUDE.md; this report and its index row.
