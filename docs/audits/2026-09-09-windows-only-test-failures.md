# Findings: ten test failures that appear only on the owner's Windows machine

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

**Date:** 2026-09-09. **Dispatch:** B4+ (task 3), written by the coder session from evidence the
planner supplied plus this session's own run. **Status:** a findings note, not an audit of the
tests themselves. Nothing was changed to make anything pass; no test was skipped, ignored or
weakened (CLAUDE.md forbids that in a dispatch that did not ask for it, and this one did not).

## What was reported

The owner ran the JVM unit-test suite on their Windows machine and got **ten failures**:

- **nine Room migration tests**, and
- **one FileProvider test**.

The individual test names and failure messages were **not** relayed to this session. That matters
for what follows: everything below reasons from the *categories*, because the messages — the thing
that would normally identify the cause — are not in hand. `app/src/test/java/com/zynergylabs/forager/app/data/
local/` holds eleven `*MigrationTest.kt` classes; which nine of them failed is unknown here.

> **AMENDMENT, 2026-09-09 (backup-ruling docs dispatch).** The names and messages *are* now in
> hand, and they change what this note can claim. See "**Amendment: the failure messages**" at the
> end — the paragraph above is left standing because it was true when written, and because the
> reasoning that follows it was built on the gap it describes. Read the amendment before acting on
> the middle of this note.

## What was verified, and by whom

| Claim | Host | Result | Verified by |
|---|---|---|---|
| `claude/path-home-join` at its own head | Linux | 1295 tests, 0 failures, 0 errors | planner, reported to this session |
| `main` | Linux | 1277 tests, 1 failure, described as an unrelated flake | planner, reported to this session |
| `claude/backup-and-signing-notes` (this branch, cut from `699efa3` = `main`, manifest + comment changes only) | Linux | 166 suites, 1277 tests, 0 failures, 0 errors, 24 skipped, on two of three full runs; all eleven `*MigrationTest` classes green on all three | this session |

This session did **not** re-run either of the planner's two runs and does not restate their numbers
as its own. What this session can add independently: on this Linux container, on a branch whose
content differs from `main` only by an `AndroidManifest.xml` attribute and two comments, all eleven
Room migration classes passed on three consecutive full runs. Ten failures on one host and zero of
them on another is the whole of the evidence for "host-only".

**One caveat, stated because it is inconvenient.** The third run of that suite (the first this
session made) had a single failure — `JournalTabTest > From Album on the edit form opens the picker
and pulls the selected photo into the entry`, a Compose "component ... is not displayed" assertion.
`JournalTabTest` is one of the six classes that construct `CameraCaptureFiles`, i.e. it is in the
same broad family as the owner's one FileProvider failure. It did not reproduce: the class passed
alone (14/14) and the full suite passed twice afterwards, and the failure is a display assertion,
not a file or URI error. So it is recorded as a flake and **not** as a Linux instance of the
Windows failure — but a reader who later gets the Windows failure messages should check whether the
Windows FileProvider failure is this same test, because if it is, "does not reproduce on Linux"
becomes "reproduces on Linux about one run in three" and this note's decision would need revisiting.

> **Answered by the amendment at the end of this note (2026-09-09):** it is **not** the same test.
> The Windows FileProvider failure is `AvailabilityScreenSettingsPanelTest > tapping a track's
> share action starts a real ACTION_SEND chooser for a GPX file`, failing with
> `java.lang.IllegalArgumentException` at `FileProvider.java:911`. So this note's decision does not
> need revisiting on that ground. The `JournalTabTest` flake is a separate, Linux-reproducing,
> full-suite-only problem, and it now has its own note:
> `docs/audits/2026-09-09-journaltabtest-photo-pull-flake.md`. That note also corrects the "flake,
> did not reproduce" reading above — by the time it was written this was the third sighting on the
> third distinct tree, and it is unexplained rather than dismissed.

## The inference — INFERRED, NOT PROVEN

Both failing categories are the ones that touch **real files and real temp directories**:

- The migration tests build a real on-disk SQLite file and delete it around each test —
  e.g. `MushroomLogMigrationTest.setUp` does
  `ApplicationProvider.getApplicationContext<Application>().getDatabasePath(TEST_DB_NAME)` and then
  `dbFile.delete()` (`app/src/test/java/com/zynergylabs/forager/app/data/local/MushroomLogMigrationTest.kt`,
  `setUp`/`tearDown`). Robolectric supplies that path from a temp directory it creates.
- The FileProvider path resolves a `content://` URI for a file under `filesDir/captures/` through
  `androidx.core.content.FileProvider` and `res/xml/file_paths.xml`
  (`com.zynergylabs.forager.app.photo.CameraCaptureFiles`; the test classes that construct it are the six under
  `app/src/test/java/com/zynergylabs/forager/app/ui/log/`).

So the hypothesis is **Windows path handling under Robolectric** — path separators, drive letters,
path length, or a file lock that prevents the delete/reopen these tests do (Windows refuses to
delete a file that still has an open handle, where POSIX does not). That is a plausible common
cause for exactly these two categories and no others.

**It is an inference and nothing here proves it.** What would be needed to promote it to proven,
none of which this session has:

1. The actual failure messages and stack traces from the Windows run.
2. A reproduction on a Windows host — this container is Linux only.
3. A named mechanism traced to a line, rather than a category that "looks like" file handling.

Two alternative explanations are not excluded by the evidence: a different JDK or Robolectric
sandbox-cache state on the owner's machine, and a stale/partial build on that machine (a Gradle
build directory carried across a branch switch). Neither has been checked. Note also that the
correlation runs the other way from what is usually wanted: these are the categories that touch
files *and* they are the categories that failed, but nobody has confirmed that every file-touching
test failed or that no non-file-touching test failed — the ten-failure breakdown is all that was
relayed, so the sample this inference rests on is a summary, not a list.

## Decision

**CI/Linux is the authority. This is not a beta blocker. Do not spend beta time on it.**

Reasoning: the artifact that ships is built and verified on Linux, the same suite passes there on
three separate branches, and no production code path is implicated by any of the evidence — the
suspicion is about the test harness's file handling on one developer machine. Spending closed-test
days chasing a host-specific harness issue costs the thing the beta exists for (field data) and buys
nothing a Linux run does not already give.

**Alternative rejected:** treat the ten failures as real and block the beta until they are green on
Windows. Rejected because there is no evidence they describe the app rather than the host, and
because acting on the inference above as if it were established is precisely the error CLAUDE.md's
"a check that passes because it never saw the data that could fail it" family warns about, run in
reverse — accepting a diagnosis whose sample was never inspected.

**Explicitly not decided:** whether the failures are worth fixing *after* the beta. If the owner
wants Windows to be a supported development host, this needs the failure messages first; that is a
post-beta dispatch, and its first step is capturing the Windows run's output, not reading code.

---

# Amendment: the failure messages, 2026-09-09

Added by the backup-ruling docs dispatch, after the messages this note said it did not have turned
out to be recorded on another branch. Nothing above was deleted; this section says which of it
still stands.

**Source.** `docs/audits/2026-09-08-path-home-monotonicity-amendment-completion-report.md` (on this
tree; written by the `claude/path-home-join` session, which ran the suite **on the owner's Windows
machine**). Its "Ten pre-existing failures" list names all ten tests and both messages. The
identification of that run with the ten failures this note describes rests on the same host, the
same count, and the same 9-migration/1-FileProvider split, plus the planner's relay saying so — it
was not re-run here, and no run log from the owner's machine was read directly.

## Now ESTABLISHED

**The nine Room migration failures are `android.database.sqlite.SQLiteCantOpenDatabaseException`.**
That is a filesystem-level error: the database file never opened. **So those nine tests are not
exercising migration logic at all when they fail** — they die before any `Migration` body runs.
Whatever this is, it is not a defect in a migration, and a reader should stop treating "nine
migration tests fail" as a signal about the migrations.

**Which nine.** `CartographyEntryMigrationTest`, `DayScopedIndexMigrationTest`,
`MushroomLogDraftMigrationTest`, `MushroomLogEntryMigrationTest`, `OfflineRegionMigrationTest`,
`TrackOriginWaypointMigrationTest`, `TrackPointSpeedMigrationTest`, `TrackWaypointMigrationTest`,
`WaypointDesignationMigrationTest`. The note above said this was unknown; it is now known, and it
means **two of the eleven classes passed on that host**: `LogPhotoMigrationTest` and
`MushroomLogMigrationTest`.

**The tenth failure is not `JournalTabTest`.** It is `AvailabilityScreenSettingsPanelTest > tapping
a track's share action starts a real ACTION_SEND chooser for a GPX file`, failing with
`java.lang.IllegalArgumentException` at `FileProvider.java:911` — the "failed to find configured
root" shape. That closes the open question flagged earlier in this note.

## Still INFERRED — why the open fails on that host

The messages say *what* failed, not *why*. "Windows path handling under Robolectric" remains the
hypothesis and remains unproven; a `SQLiteCantOpenDatabaseException` is equally consistent with a
permissions problem, a missing parent directory, a path too long for the host, and a file the host
refuses to reopen. None of those has been distinguished. The three things listed above as needed to
promote it (real stack traces, a Windows reproduction, a mechanism traced to a line) are still all
needed — a message is not a mechanism.

## Two observations from this tree that a future investigation should start from

Both were read off the eleven test classes in `app/src/test/java/com/zynergylabs/forager/app/data/local/` on
this tree. Both are **hypothesis-generating, not established**, and the second especially is the
kind of correlation CLAUDE.md warns about — recorded so it can be checked cheaply, not believed.

1. **The two passing classes are structurally identical to the nine failing ones.** Every one of
   the eleven has the same `setUp`/`tearDown`: `getDatabasePath(TEST_DB_NAME)` then `dbFile.delete()`
   (compare `MushroomLogMigrationTest.kt` and `LogPhotoMigrationTest.kt`, both passing, against
   `CartographyEntryMigrationTest.kt`, failing — same four lines). So "these tests touch real files
   and those don't" cannot be the discriminator: all eleven touch real files the same way. Each
   class also uses its **own** distinct `TEST_DB_NAME` (eleven different `.db` names), so
   contention over one shared file is ruled out as well.

2. **The pass/fail split separates perfectly by database file-name length.** Passing:
   `log-photo-migration-test.db` (27 characters), `mushroom-log-migration-test.db` (30). Failing:
   every one of the other nine, at 32-39 characters, with the shortest failing name
   (`offline-region-migration-test.db` and `track-waypoint-migration-test.db`, 32) longer than the
   longest passing one. The two passers being exactly the two shortest names has about a 1-in-55
   chance under a random split of 9 failures among 11 classes, and alphabetical execution order
   does **not** separate them (the two passers sit third and sixth alphabetically, interleaved with
   failures), so order is not an obvious confound. That is consistent with a total path length
   limit on the Windows host — Robolectric's temp root plus the file name against `MAX_PATH` — and
   it predicts a cheap, decisive test.

**The one cheap next step, before any code is read:** get the *full* exception text from the
Windows run, including the path in the message, and measure that path's length. If it lands near
260 characters, this is a host path-length limit and the remedy is a shorter Robolectric temp root
(e.g. `-Drobolectric.dependency.dir` / a shorter Gradle build dir) or shorter `TEST_DB_NAME`
values — not a change to any migration. If the path is short, hypothesis 2 is dead and the
permission/handle explanations are what is left. Either way this costs one message paste and no
investigation.

## What this amendment does NOT change

The decision above stands unchanged: **CI/Linux is the authority, this is not a beta blocker, do
not spend beta time on it.** Knowing that the nine failures never reach migration code makes that
decision safer, not weaker — the failing tests are not telling us anything about the shipped
database. Nothing was skipped, ignored, weakened or added to any allowlist by this amendment
either; it is documentation only.

---

## Merge note, 2026-09-09 — a second amendment folded in here

This file was written twice, independently, from the same base note: once on
`claude/backup-and-signing-notes` (the amendment above) and once on
`claude/audit-index-catchup`, which reached `main` as PR #83. Merging integration into `main`
produced an add/add conflict between them. **The amendment above was kept in full** — it is the
better of the two, and one of its findings is absent from the other: that a
`SQLiteCantOpenDatabaseException` means those nine tests die before any `Migration` body runs, so
"nine migration tests fail" is not a signal about the migrations.

Everything in the PR #83 version is already stated above, except these two items, preserved here so
the merge drops nothing:

**The tenth failure's message verbatim**, as the concrete form of the "failed to find configured
root" shape named above — a Windows backslash path that FileProvider's configured roots do not
match:

```
Failed to find configured root that contains C:\Users\metal\AppData\Local\Temp\
robolectric-AvailabilityScreenSettingsPanelTest_..._for_a_GPX_file<digits>\
com.zynergylabs.forager.app-dataDir\cache
```

**A standing instruction from the owner (2026-09-09): do not upgrade this note's claim.** The
pattern is confirmed host-specific — the same commits are clean on Linux CI. The *mechanism* is
not proven, and neither amendment proves it. A later session must not read either one as having
established the cause. Confirming a pattern is not proving a mechanism.

---

## Amendment, 2026-09-09 (second) — the length boundary is now visible in the *behaviour*, and the check this note predicted is not available

Found incidentally by Phase 0 of the `JournalTabTest` comparison
(`2026-09-09-journaltabtest-flake-comparison-preregistration.md`), which ran ten consecutive local
full-suite runs and recorded every one.

**1. The check this note named as decisive cannot be run as specified.** It predicted "measure the
path length in the real exception text." The exception carries **no path**: the message is
`android.database.sqlite.SQLiteCantOpenDatabaseException: unable to open database file (code 14
SQLITE_CANTOPEN)`, and no drive-letter path appears anywhere in the JUnit XML, message or stack.
Recorded so the next session does not spend time looking for it.

**2. But the boundary showed itself another way — the failing set is not fixed, and *which* classes
flip is the signal.** Across ten runs the count was 10, 10, 10, 10, 10, 10, **9**, **9**, 10, **9**,
and the class that survived was not the same one each time. Cross-referencing every migration test
against its own database filename length:

| db filename length | classes | outcome over 10 runs |
|---|---|---|
| 27 | `LogPhotoMigrationTest` | **passed 10/10** |
| 30 | `MushroomLogMigrationTest` | **passed 10/10** |
| **32** | `OfflineRegionMigrationTest`, `TrackWaypointMigrationTest` | **intermittent — 3 passes in 20 class-runs** |
| 34–39 | the other seven | **failed 70/70 class-runs** |

**The two intermittent classes are exactly the two at the boundary, and nothing above or below it
ever flipped.** That is the signature a `MAX_PATH` ceiling produces when the run's randomized
Robolectric temp-directory name varies in length between runs: the same filename lands on either
side of 260 depending on its prefix, so only names sitting within the prefix's own variation flip.
A clean deterministic split — the earlier "27/30 pass, 32–39 fail" — is *weaker* evidence for the
length hypothesis than this is, because a deterministic split is equally consistent with any
per-class difference.

**3. Still inferred, not proven, and this note's standing instruction not to upgrade the claim is
unchanged.** No path was measured; a boundary in behaviour is not a boundary in bytes. What would
prove it is one instrumented print of `context.getDatabasePath(name)` from inside a migration test,
on a run where the 32-length class passes and one where it fails — a **test change**, belonging to
this investigation and explicitly not made by the flake dispatch that noticed it (§6: report, do not
fix). Nothing here was skipped, ignored, weakened, or allowlisted.
