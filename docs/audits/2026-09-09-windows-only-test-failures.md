# Findings: ten test failures that appear only on the owner's Windows machine

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
that would normally identify the cause — are not in hand. `app/src/test/java/com/forager/app/data/
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
  `dbFile.delete()` (`app/src/test/java/com/forager/app/data/local/MushroomLogMigrationTest.kt`,
  `setUp`/`tearDown`). Robolectric supplies that path from a temp directory it creates.
- The FileProvider path resolves a `content://` URI for a file under `filesDir/captures/` through
  `androidx.core.content.FileProvider` and `res/xml/file_paths.xml`
  (`com.forager.app.photo.CameraCaptureFiles`; the test classes that construct it are the six under
  `app/src/test/java/com/forager/app/ui/log/`).

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

Both were read off the eleven test classes in `app/src/test/java/com/forager/app/data/local/` on
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
