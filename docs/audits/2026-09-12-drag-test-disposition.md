# The drag test: finding stands, disposition is "run it against a known negative first"

**Date:** 2026-09-12
**Base: `f7c9f15`**, verified against the remote.
**Type:** owner ruling recorded, with the precedents it rests on checked against the tree. No code
changed.

## The ruling

The finding in `2026-09-12-recheck-against-f7c9f15.md` stands: `a9e8a4f` is a routing claim
verified against bytecode and never exercised by a coordinate touch. `performClick` cannot reach the
intercept flag, and a "Simulate pan" button tests the button.

**Disposition (owner): do not ship the test yet.** The sequence is: write the coordinate-drag test,
run it against the code with the fix reverted, and see it fail. If it fails for the right reason,
keep it. If it passes on reverted code, delete it and record that the environment cannot see this
either. Nobody knows which way it goes until it is run against a known negative.

The repo's rule cuts both ways here — "any layout composed over a map needs a coordinate-touch test"
against "a check that passes identically before and after is suspect" — and the revert run is the
only thing that decides between them.

## Both precedents are on the record, verbatim

**The earlier session declined explicitly.** `a9e8a4f`'s own commit message:

> Not verified by a test, and not testable here: the gesture routing itself. No test in the repo
> composes the real SightingsMap — all 17 map-bearing test files pass a stub MapSlot — so the
> AndroidView/MapLibre interop this fix operates on is unreachable from the suite by construction.
> Per the dispatch, no test was written that would pass either way. The owner's device is the
> authority.

**The tab-wrap test passed on defective code, and was removed for it.** `a188d57`'s commit message:

> passed identically on the unmodified, defective code — Robolectric's text-layout measurement in
> this project's config reports implausible glyph widths for these [...] so nothing here can ever
> be measured as wrapping regardless of the fix. Removed that test rather than ship a check that
> cannot fail either way

Same surface, same class of reason, three days apart. That is the whole case for running the
negative rather than assuming.

## One figure that does not reproduce, and one that does

"All 17 map-bearing test files pass a stub MapSlot" was checked against `f7c9f15` rather than
carried:

| Definition | Command (against `origin/main`, `app/src/test/**`) | Count |
|---|---|---|
| Test files referencing `MapSlot` or `SightingsMap` | `git grep -l 'MapSlot\|SightingsMap'` | **23** |
| Test files composing the real `SightingsMap(` | `git grep -l 'SightingsMap('` | **0** |
| Test files matching a stub/fake map-slot pattern | `git grep -il 'stub.*MapSlot\|MapSlot.*stub\|fakeMapSlot\|stubMapSlot\|mapSlot = {'` | **18** |

Neither of my definitions yields 17. **The load-bearing half — zero tests compose the real map — holds
exactly.** The integer is recorded here with its commands so the next reader knows which definition
they are getting rather than inheriting a bare number; it changes nothing about the conclusion, and
my patterns may not be the ones the earlier session used.

## The experiment cannot run in this container

JDK 21 is present. `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset, there is no `local.properties`,
and no SDK directory exists under the usual paths. Robolectric needs the Android Gradle plugin to
configure and the `android-all` jar to run; without an SDK the build does not reach the test task.
**This goes to a session with an SDK** — the owner's host or CI.

## Protocol for whoever runs it

Written out because `CLAUDE.md` records this project's revert runner producing false confirmations
twice, in two different ways.

1. **Write the test** as a real drag: `performTouchInput { down(mapCentre); moveBy(0f, -200f); up() }`
   on the picker map inside `OfflineMapsPanel`'s `verticalScroll` Column, asserting the panel's
   scroll offset is unchanged. Not `swipeUp()` alone — a drag, with the down inside the map's bounds.
   Sample more than one start point across the map's own bounds; a finger is not a point.
2. **Save a copy of `SightingsMap.kt` before editing it.** Restore from that copy, never from
   `git checkout --`.
3. **Revert the fix** by removing the `setOnTouchListener` block (or forcing it to return without
   calling `requestDisallowInterceptTouchEvent`). A one-line change that compiles.
4. **Run the affected class. Read the build log for compile errors before reading any result.** A
   JUnit XML left over from a previous run reads as a confirmation.
5. **Read the failure message and ask whether this revert could have produced it.** The right
   failure is "panel scrolled / map did not receive the drag." Any other failure is a stale run or
   a different edit.
6. **Restore from the saved copy, re-run, and confirm the forward change is still present** with
   `git diff` before citing anything.
7. **Outcome A — fails on reverted code for the right reason:** keep the test. Robolectric can see
   this path, and the earlier session's "unreachable by construction" was about stubbing, which the
   new test bypasses by composing the real map.
   **Outcome B — passes on reverted code:** delete the test and record it, exactly as `a188d57`
   did. The environment cannot see this either, and the owner's device stays the authority.

## What else this recheck re-derived

Two figures the owner named as worth the discipline even though nothing depended on them:
MapLibre-coupled LOC corrected 2,737 → 2,808 because it had been quoted as exact; the process-death
gap and the attribution handling both re-derived on `f7c9f15` rather than carried, and both stand.

## Disclosure

**Verified on `f7c9f15` / this tree:** both commit messages quoted; the three counts and their
commands; JDK version; absence of SDK env vars, `local.properties`, and SDK directories.

**Not determined:** which definition produces 17; Robolectric's fidelity for the disallow-intercept
path — which is precisely what the protocol exists to determine.

## Addendum (owner, 2026-09-12): the second count no definition reproduces

"17" is the second figure in this project that matches no definition anyone can construct. The
first was **376** — the Data safety draft's "376-artifact classpath," which resolved to 287 distinct
`group:artifact:version` pre-resolution or 157 distinct modules post-resolution, with neither
reproducing the quoted figure (`docs/audits/README.md`, 2026-09-10 row, "a count that no plausible
definition reproduces"). Same repair both times: the load-bearing conclusion re-derived and found to
hold, the figure recorded with its commands rather than as an integer.

The general form, in the owner's words: **a number carried without its definition reads as a
measurement whether or not it ever was one.**

**Where the revert experiment runs.** `.github/workflows/ci.yml` runs `testDebugUnitTest` on pushes
to `main` and on pull requests (deliberately with no base-branch filter, per its own comment about
stacked PRs). A bare branch push does not trigger it. So from a container with no SDK, the
experiment can reach CI only through a PR; absent an instruction to open one, it runs on the owner's
host. Outcome B — the test passing on reverted code — is worth writing down even though it keeps
nothing: it would be the third recorded blind spot on this one surface.

## Addendum 2 (2026-09-12): the CI route does not exist under the constraints

The owner authorised a CI run with four constraints, recorded verbatim so they travel with the
experiment wherever it runs:

1. **Name it for what it is** — e.g. `claude/experiment-revert-drag-fix-do-not-merge`. The name is
   the guard; it is what a future session sees in a branch list.
2. **The revert is the only change on it.** Branch off the current head of the fix branch, revert,
   push. No test edits, no docs, nothing else — otherwise a failure cannot be attributed.
3. **No PR, ever.** A PR is what turns a branch into something that looks mergeable.
4. **Delete the branch after the run** and record the outcome in this document rather than leaving
   the branch as the artifact. This project has already spent a day on branches nobody could
   account for.

Main is untouched and the AAB builds from main, so there is no path from the experiment to a
shipped artifact.

**Read in full, `.github/workflows/ci.yml`'s trigger block is:**

```yaml
on:
  push:
    branches: [main]
  pull_request:
```

No `workflow_dispatch`. A bare branch push does not run CI; only a push to `main` or a pull request
does. **Constraint 3 therefore closes the CI route from a container with no SDK**, exactly as the
owner anticipated: "If it's PR-only, this route doesn't exist without opening one, and then it goes
to the host instead." No branch was created. **The experiment runs on the owner's host**, under the
same four constraints and the seven-step protocol above.

**Side finding from the same read.** The fix branch `claude/new-session-pd5wfd` has a head,
`7aef89a` ("Audit index: a truncated search result and an empty one are the same text"), that is
**not contained in `main`** (`git merge-base --is-ancestor` → no). PR #98 merged an earlier point of
that branch; at least one commit landed on it afterward. Reported, not acted on — it is the class of
thing constraint 4 exists for, and whether it is unmerged work or a stray belongs to whoever owns
that branch. If the experiment branches "off the current head of the fix branch" as constraint 2
says, it inherits that commit; branching off `main` at `f7c9f15` avoids that and still carries the
fix.
