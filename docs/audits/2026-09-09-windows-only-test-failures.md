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
