# Walk logger: header on disk before the session is active (2026-10-11)

This branch is `walk-logger-header-first`, cut from main 65bd8ee5. The failing-first test is `b6d164e6` and the fix is `8c79652d`. The planner decided to fix the cause, not the test. The diagnosis is in `docs/navigation/2026-10-11-return-ends-on-arrival-report.md` on `return-ends-on-arrival`.

## The failure

CI run 38115289698 failed one test: `WalkLoggerServiceTest` > "switched on, the log starts with the recording…", with `NoSuchElementException` at line 148 (`file.readLines().first()`).

The cause is in the walk logger, not the test:
- `WalkLogSession.start()` set `active = true` right after opening the file.
- The header line went into the writer's buffer, and only reached the disk at the end of `start()`, at the first minute's flush.
- So `WalkLogger.isLogging` could read true while the file was still empty, and the test read it in that window.

This is debug-only code, so nothing a user sees changes.

## The fix

`start()` now writes and flushes the header (the new `WalkLogWriter.writeAndFlush`) before setting `active = true`. If the header cannot be written, the writer stops itself with its STOPPED line and the session stays inactive. The logger then lets the wake lock go, as it already does for any start that does not become active.

## Tests

**New test.** `WalkLogSessionTest` "once the session is active its header is already on disk" reads the file at the session's first call after it becomes active. The fake clock stands still, so no time-based flush can have written the header first.
- On unfixed code it fails: `the header is on disk the moment the session is active: [] expected:<# walklog version=1> but was:<null>`.
- With the fix it passes.

**Logger classes with the fix: 38 tests, 0 failures.** The classes are `WalkLogSessionTest`, `WalkLoggerServiceTest`, `WalkLogWriterTest`, `WalkLogFormatTest` and `WalkLogKeptOutOfRepositoryTest`.

**The 200 ms pause experiment.** These runs add a 200 ms pause after `active = true` to widen the race. The pause is added for the run only and restored from a saved copy, and `git diff` confirmed the file matched HEAD afterwards.

| Code | Runs of `WalkLoggerServiceTest` (6 tests each) | Failed |
|---|---|---|
| With the fix, plus the pause | 5 | 0 |
| Revert (`active = true` before the header, as before), plus the pause | 5 | 5, all `NoSuchElementException` at `WalkLoggerServiceTest.kt:148` |

**Revert check without the pause.** With only the revert, `WalkLogSessionTest` fails with the same message the new test gave on unfixed code. Each revert's build log was checked for compile errors (none). After each one the file matched the committed fix and the forward line was present.

Every run went under `systemd-run --user --scope -p MemoryMax=5G` with `-Xmx1536m`, after checking that no forecast fit was running. assembleDebug succeeded, and `./gradlew --stop` left no daemons.

## Not done

- The full suite was not run; it runs on CI.
- No device check: the logger is debug-only, and its behaviour on a phone changes only by one extra flush at start.
