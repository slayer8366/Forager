# Capture-orientation diagnostics into the debug store: completion report

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Commit:** `a9587d7`

## What landed

- **`a9587d7`** — the recorders, both variants, seven call sites, four tests, and the pre-build
  report. Tree clean, pushed.

Pre-build report: `2026-09-15-capture-orientation-diagnostics-prebuild-report.md`, **written and
committed before any code**, with a §6 addendum added after the build for the two things the build
changed. Read that first; this report does not repeat its reconnaissance.

## Verification before building, and what it changed

Three findings preceded the code and two of them changed it.

1. **The route did not exist**, and how to make one was an architectural decision rather than an
   implementation detail. Surfaced as a stop-and-ask with three options and their costs. The owner
   chose the `DebugDiagnostics` method over a constructor parameter, revising an earlier instruction,
   on the reasoning that it follows the one seam that already exists and keeps the `Application`
   lookup out of `InAppCameraHost`.
2. **The session's context is already the Application.** `InAppCameraHost.kt:49` passes
   `LocalContext.current.applicationContext`, so the cast is a null-check rather than a gamble. This
   was not known when the seam question was asked.
3. **The path count was wrong, twice.** The dispatch said "rewritten or kept"; there are four
   outcomes. The pre-build report then said a fifth path exists; **the build found a sixth** — the
   `if (saved.isFailure)` early return, where the capture itself failed and no file was written. Both
   corrections are recorded in the pre-build report's §4 and §6 rather than silently absorbed.

## What was built

Two methods on `DebugDiagnostics`, real in `src/debug` and `= Unit` in `src/release`, with identical
signatures. `recordCaptureShot` takes the four `Shot:` values; `recordCaptureOrientation` takes the
file name, the branch that fired, and the optional values that distinguish it.

**Values, not a formatted string.** Every argument already exists at the call site and none is an
interpolation, so a release build evaluates nothing it then discards. This follows `recordSweep`,
which takes an `Int` for the same reason.

**Seven call sites in `capture()`**: one `Shot:` entry, then exactly one outcome entry on each of six
mutually exclusive paths — `Rewritten`, `Kept`, `Declined`, `Failed`, the `intended == null` early
return, and the failed-capture early return. **Two entries per capture is the invariant**, which makes
an odd count in the panel a signal by itself.

**Declines carry `reason`, failures carry `error`,** with the throwable's stack as the entry's detail
in the same shape a StrictMode entry uses. Those two are the distinction the instrument exists for: a
HAL that rotated the pixels in memory is a decline and is correct; a tag that could not be written is
a failure and is not.

**The store is reached with a safe cast**, `(appContext as? ForagerApplication)?.diagnostics`,
resolved once through `by lazy` so the reason is logged at most once per session rather than once per
shot. This departs deliberately from `MainActivity.kt:43` and `TrackRecordingService.kt:110`, which
hard-cast `application`: those run inside components the platform built from this manifest, while a
session sits behind a `Context` a caller hands in. An observation surface that can take down the thing
it observes is worse than none.

**Not touched**, per the dispatch: the capture path's own values, `effectiveDeviceRotation`, the window
lock, the control-angle path, `IntendedOrientation`, the scrub, the sweep, persist, the existing
`Log.i` calls (every one still fires, alongside the new entry and never instead of it), `DiagnosticsLog`,
and what the panel reads.

## Evidence

**Four tests**, in `DebugDiagnosticsTest`, against the real `DiagnosticsLog` on a temp directory:
the shot entry carries all four values; a decline carries its reason and the tag; a failure carries
the throwable and its stack as detail; a no-reapply path records an entry of its own.

**Four revert checks, each against committed state, each restored from a copy saved before editing —
never from `git checkout`:**

| Reverted edit | Test that failed | Message |
|---|---|---|
| `recordCaptureShot` writes a bare string | `recordCaptureShot writes the shot's four values…` | "expected all four values on the shot entry" |
| the decline's `reason` dropped | `a declined reapply records its reason…` | "expected the decline's own reason" |
| the failure's stack not passed as detail | `a failed reapply records the throwable and its stack…` | "expected the stack indented beneath it" |
| `recordCaptureOrientation` records nothing | three tests | "condition not met within 5000 ms" |

**A false negative in the revert runner, recorded because it is the failure CLAUDE.md warns about.**
Reverts three and four first reported "no test failed", which would have read as "this edit is not
covered". The edit had never been applied: the replacement was written through `python3 -c` inside a
shell double-quoted string, and `\$` mangled the anchor, so the file was unchanged and the run was the
previous one's. Caught by the `SyntaxWarning` in the output, not by anything in the result. Both were
redone with the edit asserted before the run — the anchor must be found exactly once, and the runner
aborts if `git diff` shows the file matching `HEAD` — and both then bit. **The results in the table
above are from the verified runs only.**

**Suite:** 198 suites, **1540 → 1544 tests** (the four above), **0 failures, 0 errors, 24 skipped
unchanged**. `scripts/compare-test-baseline.sh`: NEW 0, ABSENT 43, exit 0. `assembleDebug` exit 0;
`compileReleaseKotlin` also run, because the release twin is the half of this change that is easy to
get wrong and nothing else in the unit suite compiles it.

## Not tested, said plainly

- **The wiring is not tested and cannot be here.** That `capture()` makes both calls on every one of
  the six paths is established by reading the seven call sites, not by a test: it needs a bound
  camera, which Robolectric has no provider for. This is the cost the owner accepted in choosing this
  seam over a constructor parameter, and it was named in that decision rather than discovered after.
  The four tests cover the recorder; `DebugDiagnosticsTest`'s "a path that attempts no reapply" test
  says so in its own doc, so its name cannot be read as covering the invariant.
- **The release twin is compiled, not executed.** No unit test runs the release source set.
- **Nothing here verifies what the device does.** This dispatch moves existing values to a second
  destination; it does not change them.

## Device check

Run from the top on a build containing `a9587d7`. Two assumptions are now load-bearing and are stated
rather than left implicit:

- **This must be a debug build.** The store, the panel and StrictMode are debug-only by source set. On
  a release build `DebugDiagnostics` is a no-op, `DiagnosticsLog` does not exist, and `DiagnosticsPanel`
  is a 19-line stub — the entries below will not be absent-because-broken, they will be absent because
  the build has none of it.
- **An absent early entry may be rotated rather than missing.** The panel reads only the current log
  generation; a rotation at the 1 MB cap moves everything before it out of view while leaving it on
  disk. See `2026-09-15-diagnostics-panel-hides-the-rotated-generation.md`. If an expected entry is
  missing, check the log's size before concluding it was never written.

1. **Open the Diagnostics panel, view the log.** The `process started` line is there. Note the log's
   size; it is the baseline for the rotation caveat above.
2. **Take one photo with the in-app camera.** The panel gains **exactly two** entries: a
   `capture shot …` line with `deviceRotation`, `targetRotation`, `requestDegrees` and `resolution`,
   and one `capture orientation '<file>' <branch>` line. Record both verbatim.
3. **Read the branch.** `rewritten` confirms the inferred mechanism — the HAL tagged from its own
   sensor and the reapply corrected it. `kept` means the HAL honoured the request, and the earlier
   device result needs another explanation. **Either is a finding; neither is a failure.**
4. **`declined` is also a pass.** It means the HAL rotated the pixels in memory and CameraX's tag
   stands; the `reason=` on the entry says which check declined. Record the reason and the photo's
   dimensions.
5. **Take twenty photos in one session.** Forty entries, two per shot, and no missing partner: a
   `capture shot` with no `capture orientation` beneath it is the defect this dispatch exists to make
   visible. Confirm the log is still well under 1 MB (roughly 5 KB of capture entries).
6. **With "Lock camera to portrait" on, held landscape.** The `capture shot` entry should read
   `deviceRotation=0 targetRotation=0 requestDegrees=90`. This is the line the previous device run
   needed logcat to read; it is now in the panel.
7. **Whatever you find, the entries are the record.** Paste them rather than summarising — the branch
   and the `reason=`/`error=` payload are the parts that distinguish the cases.
