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

## Addendum 3 (owner, 2026-09-12): run on the host; what outcome B would count

**Decision: the experiment runs on the owner's host.** A PR whose purpose is to carry a deliberate
defect through CI creates something that looks mergeable. The host has the SDK, the experiment is
self-contained, and nothing needs to leave the machine.

**Outcome B, underlined.** If the test passes on reverted code it keeps nothing, but it would be the
**third recorded blind spot on one surface**: text metrics (`a188d57`, tab wrap), gesture routing
(`a9e8a4f`, declined as unreachable), and whatever this one turns out to be. One is an environment
quirk. Three is a property of the surface, and the count is what eventually justifies a different
kind of test rather than another attempt at the same one. Instrumented tests on a device would be
that different kind; they are a post-beta conversation.

## Where this work actually lives

The branch is `claude/ios-port-feasibility-mvsjcr`, and only the first document on it is an iOS
port report. The name is a session artifact and will make a later reader hunt, so, derived from
`git diff --name-only origin/main...HEAD` at the time of writing, the branch carries these audit
documents and nothing else outside `docs/audits/`:

- `2026-09-08-data-inventory-for-privacy-policy.md` — **not authored here**; on the branch only because §4 was superseded in place (owner-authorised, round 4)
- `2026-09-11-ios-port-feasibility-report.md`
- `2026-09-11-lifecycle-gate-and-corrections-round-3.md`
- `2026-09-11-lifecycle-gate-and-corrections-round-4.md`
- `2026-09-11-maplibre-pmtiles-policy-compliance.md`
- `2026-09-11-maplibre-pmtiles-policy-corrections-round-2.md`
- `2026-09-11-maplibre-pmtiles-policy-corrections.md`
- `2026-09-12-drag-test-disposition.md`
- `2026-09-12-recheck-against-f7c9f15.md`
- `2026-09-12-round-4-re-derived-against-5515adc.md`

The thread runs: iOS feasibility → MapLibre/PMTiles policy audit → three rounds of owner
corrections → the lifecycle gate and the pocket question → re-derivation after a stale base →
recheck after #97/#98 → this drag-test disposition. The index rows in `README.md` carry the
findings; these files carry the evidence.

## Addendum 4 (2026-09-12): constraint 2 revised, and `7aef89a` identified

**Constraint 2 is revised by the owner:** branch the experiment off **`main` at `f7c9f15`**, not off
the fix branch's head. Main carries the fix and not the trailing commits, so the revert measures
exactly one change.

**`7aef89a` is two commits, not one, and neither is app code.** `git log origin/main..
origin/claude/new-session-pd5wfd`, after deepening the fetch:

| Commit | 2026-09-12 UTC | What | Files |
|---|---|---|---|
| `e8661ed` | 12:27 | New `CLAUDE.md` Known-pitfalls entry, "a planner's picture of the repository decays," plus its index row | `CLAUDE.md` +60, `docs/audits/README.md` +1 |
| `7aef89a` | 12:41 | Second index row, "a truncated search result and an empty one are the same text" — a detector failure (`grep -r ... \| head -5` cutting the output where the answer was), given its own row on the owner's call | `docs/audits/README.md` +1 |

Both say "Documentation only. No code, test or dependency changes." Both are from the session that
wrote `a9e8a4f`. `e8661ed`'s own message: "Branch restarted from main (f7c9f15), since PR #98 is
already in." **It is not the 4:3-on-compact ruling.**

**Was it meant to ship?** By content, yes: a `CLAUDE.md` entry commissioned by the owner, on a
branch restarted for the purpose. **By state, it has not started shipping:** the GitHub API shows
**zero open pull requests in the repository**, none with this head. So it is exactly the shape the
owner named — work reachable from no merge, alive only because a branch still exists — and it is
now identified rather than mysterious, which is the cheap half. Opening the PR is the owner's or that
session's; not done here.

**It will collide with this branch.** `git merge-tree --write-tree HEAD origin/claude/new-session-pd5wfd`
(tree untouched) reports `CONFLICT (content): Merge conflict in docs/audits/README.md` — the
serialization point again, both branches appending index rows. Whichever lands second resolves by
merge and **keeps every row**; there is no case in which dropping one is right.

One thing worth knowing before either lands: `e8661ed`'s entry uses the `CivilTwilight` decay as its
worked instance, and `2026-09-12-round-4-re-derived-against-5515adc.md` on this branch records the
same instance from the other side. Two records of one event, citing different reports, is correct;
they should not be merged into one.

**Open actions are now two, unchanged in number:** the host run, and a PR for `e8661ed`/`7aef89a`
or a decision that they should not ship.

## Addendum 5 (2026-09-12): `e8661ed`/`7aef89a` merged by #99 before a PR was opened here

The owner instructed: open the PR and merge both. Before opening one, the branch state was re-read
against the remote — the rule this thread produced, applied to its own last step — and **`main` had
moved to `5494044`, "Merge pull request #99 from slayer8366/claude/new-session-pd5wfd."** Between
the zero-open-PRs check in Addendum 4 and this one, someone opened and merged #99. Confirmed:

- `git merge-base --is-ancestor e8661ed origin/main` → yes; same for `7aef89a`.
- The branch is now 0 ahead of `main`, 1 behind.
- `origin/main:CLAUDE.md:386` carries the entry: "A planner's picture of the repository is a claim
  about the past..."

**No PR was opened here.** Opening one would have created an empty duplicate. A rule that existed
only on an unmerged branch now exists on `main`, which was the point.

The owner's own note on this belongs in the record: approving a merge and not noticing it never
merged is its own small instance of the thing the entry describes.

**The index conflict landed as designed.** Merging `5494044` into this branch conflicted in
`docs/audits/README.md`: 2 rows from `main`, 9 from this branch, **11 kept, 0 dropped**, 105 rows in
total. The serialization point working, not a problem.

**Registered on its own, as the owner asked:** at the time of Addendum 4 there were zero open pull
requests in the repository. Everything then in flight — the three UI fixes, the two documentation
commits, this disposition work — sat on branches with nothing proposing them. #99 has since closed
one of those; this branch is another.

**Open actions are now one: the host run.**
