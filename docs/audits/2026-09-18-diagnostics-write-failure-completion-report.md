# A diagnostics log write failure no longer kills the app: completion report

**Dispatch:** `2026-09-17-dispatch-diagnostics-write-failure.md`, written against
`claude/new-session-vto65i` at `6bbd90b` "plus whatever the edit guard lands as".
**Base actually used:** `6bbd90b`, confirmed as the remote tip on 2026-09-18. The edit guard had
**not landed**, so this is built without it. Code is `985d323` (plus this record), on
`claude/diagnostics-write-failure`, not yet on `claude/new-session-vto65i`. See "Where this sits".

## What landed

All in the debug source set. The release twins (`app/src/release/.../DebugDiagnostics.kt`,
`app/src/release/.../DiagnosticsPanel.kt`) are untouched. Nothing about what gets logged or the
rotation threshold changed.

- `DiagnosticsLog.append` (`DiagnosticsLog.kt:91`): the whole body is in a `try`. A thrown
  `Exception` goes to `recordWriteFailure` (`:103`, `:108`), which holds a `WriteFailure` in memory:
  time of first failure, number of lost entries, and the latest error's `toString()`. The first
  failure per instance goes to logcat as well (`:116`). Every later call still tries the write, so
  if the condition clears, writing resumes; the count stays, because the lost entries stay lost.
- `DebugDiagnostics.log` is now `internal` (`DebugDiagnostics.kt:60`), so the panel can read the
  instance that writes.
- The panel (`DiagnosticsPanel.kt`):
  - Its production entry reads that instance through `writerLog` (`:93`, `:107`). If diagnostics
    was never installed, it falls back to a `forContext` instance and logs that it did (the same
    `lateinit` guard `CameraXCaptureSession.kt:212-217` uses).
  - The log row shows `Not recording: N entries could not be written since <time>. <error>` in the
    error colour when a failure is held (`:205`, `logWriteFailureText` `:368`).
  - Opening a log that cannot be read shows `Couldn't read the log: <error>` instead of throwing
    (`:259`).

## Mechanism chosen, and why

**Held in memory on the writing instance, shown on the panel's log row.** That's where the runner
already looks: the log row is the entry to the log. It sits beside the size, which is the figure
that would otherwise silently stop changing.

**Rejected: writing a gap marker into the log once writes resume.** It would put the gap in the
file too, which matters once the file is shared off the device. But it changes what gets logged,
which the dispatch excluded, and it only ever fires if the condition clears. **Consequence, stated
plainly:** a shared `diagnostics.log` does not mark a gap. The panel does.

**Rejected: a static, process-wide failure map keyed by path, or a memoised `forContext`.** Either
would let a panel-built instance see the writer's failure without the `writerLog` wiring. But
per-process caching is what CLAUDE.md records breaking Robolectric isolation, and the writer's own
instance was already reachable through `ForagerApplication.diagnostics` (`MainActivity.kt:120`
already reaches it this way).

## Verify-first answers

**1. Every path that writes to the log.** There are eight `append` call sites, all in
`DebugDiagnostics.kt`, and all running on the one diagnostics worker:
- `:69` `recordSweep`
- `:84` `recordCaptureWithoutEditingEntry`
- `:99` `recordCaptureShot`
- `:136` `recordCaptureOrientation`
- `:151` the below-API-28 "listener unsupported" line
- `:167` and `:169` `recordViolation` (first occurrence and repeat)
- `:187` the process-start line in `install`: the AVD stack.

Their production callers are `ForagerApplication.kt:68`, `MainActivity.kt:120`,
`CameraXCaptureSession.kt:354`, `:380`, `:394`, `:405`, `:409`, `:413`, `:417`, plus the StrictMode
listener. All of them funnel through `DiagnosticsLog.append`, so the handling sits there, once,
rather than at each site. Nothing outside `DebugDiagnostics` writes to the log in production; tests
call `append` directly.

**2. Whether rotation has the same exposure.**
- Rotation cannot throw. `rotate()` (`:127-133`) is `File.delete()` and `File.renameTo()`, and both
  report failure as `false`. They throw only `SecurityException` under an installed
  `SecurityManager`, which Android does not use; that point comes from the `java.io.File` contract,
  not tested here. It now sits inside the same `try` regardless.
- Its failure mode is different and was already reported: a failed rename logs to logcat (`:132`)
  and the file keeps growing. No entries are lost, so it isn't the "incomplete log" problem, but
  that warning is **logcat-only**, not visible on the panel. Left as found: the dispatch didn't ask
  for it, and it doesn't lose entries.
- **The same class of problem did turn up somewhere nobody looked: the reader.** A file the process
  cannot open for writing (the AVD condition) fails to open for reading too. `read()` (`:122`) threw
  out of the panel's `LaunchedEffect`, so opening the log to find out why it stopped would itself
  have killed the app. Shown red before the fix (below), then guarded (`DiagnosticsPanel.kt:259`).
  This goes slightly beyond "the log write", and I took it because the dispatch's own reason, an
  instrument must not kill what it instruments, covers it.

**3. Whether the recorder's own failure could recurse.** It cannot. `recordWriteFailure` writes
only to the in-memory field and to `android.util.Log` (logcat). It never calls `append`, so a
failure cannot produce another write. The panel's read-failure path likewise only sets state and
logs to logcat.

## Evidence

**The failure, seen before the fix.** A new test drives `DebugDiagnostics.install(log, executor)`
with a real single-worker executor whose thread records whatever escapes a task. The log path is a
directory, so every open fails with `FileNotFoundException`, the same exception type the device's
EACCES raised, without depending on permissions the test's own user could override. Against the
unfixed code, from fresh XML:

> `expected no exception to escape the worker, got: [java.io.FileNotFoundException: …/diagnostics.log (Is a directory), java.io.FileNotFoundException: …/diagnostics.log (Is a directory)]`

That's two exceptions: the process-start line and the sweep.

The read side, before its guard (fresh XML):

> `java.io.FileNotFoundException: …/unreadable-diagnostics/diagnostics.log (Is a directory)`

**New tests (6):**
- `DebugDiagnosticsTest`: `a log write that fails stays on the worker and is recorded, not thrown`
  (nothing escapes; `failedEntries == 2`; the error names the cause) and
  `a log whose writes land reports no failure`.
- `DiagnosticsPanelTest`:
  - `a log whose writes fail says so on its row` (exact text equals `logWriteFailureText`)
  - `a log that is writing shows no failure line`
  - `a log that cannot be read shows why when opened, instead of taking the panel down`
  - `the production panel reads the log instance the app's diagnostics writes through` (identity)

**Revert checks.** Each is a one-line edit. Every build log was checked for compile errors (none),
failures were read only from XML newer than the run, and each file was restored from a saved copy
and then compared byte for byte with `HEAD`, where it matched every time.

| Revert | Failed | Message |
|---|---|---|
| R1 `append` catches `UnsupportedOperationException` instead of `Exception` | the worker test; the row test | `expected no exception to escape the worker, got: [java.io.FileNotFoundException: … (Is a directory), …]`; the row test's own `log.append` threw `FileNotFoundException` |
| R2 panel read catches `UnsupportedOperationException` | the read test only | `java.io.FileNotFoundException: …/unreadable-diagnostics/diagnostics.log (Is a directory)` |
| R3 row never shows the failure line | the row test only | `Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'diagnostics-log-write-failure')` |
| R4 `writerLog` returns a fresh `forContext` instance | the identity test only | `expected same:<…DiagnosticsLog@6f5aa282> was not:<…DiagnosticsLog@1db6697d>` |
| R5 row always shows the line | the no-line test only | `Did not expect any node but found '1' node that satisfies: (TestTag = 'diagnostics-log-write-failure')` |

R5 exists because my first run of the row tests matched against the merged semantics tree, where
the clickable row swallows the line's tag. The row test failed loudly, but the "no line" test would
have passed on a line that was actually there, which is the "check that could not fail" family in
CLAUDE.md. Both now use `useUnmergedTree = true`, and R5 shows the negative test bites.

**Suite:** `:app:testDebugUnitTest`, executed (not up to date or from cache): 200 classes, **1,574
tests, 0 failures, 0 errors, 24 skipped**. The skips pre-date this change; the diff adds no
`@Ignore`.

## Not tested / not verified

- **Nothing was checked on a device or AVD.** The EACCES case is stood in for by a directory. Both
  raise `FileNotFoundException` from the same open, but the actual stale-uid condition wasn't
  recreated.
- The production composable line `log = remember(context) { writerLog(context) }`
  (`DiagnosticsPanel.kt:93`) isn't covered by a test. `writerLog` itself is (R4); reverting that one
  line to `forContext` would pass the suite.
- `a log whose writes land reports no failure` has no revert check.
- A plain-JVM caller of the failure path would hit `android.util.Log`'s "not mocked" throw, since
  the unit test config doesn't return default values. It's the same property `rotate()`'s existing
  warning already has; no production code is affected, and the new tests are all Robolectric.

## Open question, recorded rather than chased (per the dispatch)

**Why did `adb uninstall` leave `Android/data/com.zynergylabs.forager.app/files/diagnostics/diagnostics.log`
behind?** Observed once, on the API 36 AVD during the 2026-09-17 AVD measurement
([report](2026-09-17-avd-boot-measurement.md)): the file was owned by uid 10216, the new install
ran as 10217, and every open failed with EACCES. Uninstall normally removes that directory. Not
investigated. If it recurs, attach here: the uids, the mode (`-rw-rw---- 10216 1078`), and whether
the uninstall and reinstall happened within one boot (they did here).

## Where this sits

The base the dispatch named hasn't moved, and the edit guard it mentions hasn't landed. These files
are only the debug diagnostics files and their tests, so a conflict with the edit guard is unlikely
unless it touches diagnostics. That's unverified, since it doesn't exist yet. As with the AVD
measurement, this is pushed to its own branch because `claude/new-session-vto65i` is checked out in
the owner's main working tree; moving it onto PR #102's branch is the owner's call.
