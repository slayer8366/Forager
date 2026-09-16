# Capture-orientation diagnostics into the Diagnostics store: pre-build report

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Base:** `dc9278e` ·
**Status:** reconnaissance complete, nothing built yet

**The dispatch.** Put the `Shot:` line and the orientation-tag outcome into the same store as the
sweep count and the StrictMode violations — debug only, per capture — recording the requested target
rotation, the request degrees, the branch that fired, and the reason on a decline. Keep the existing
`Log.i` calls. Report before building.

**One-paragraph outcome.** The route does not exist today and the one that does is the right one:
`DebugDiagnostics` already spans both build types with a real debug class and a `= Unit` release twin,
and `recordSweep` is the pattern to copy. The session can reach it, and more cheaply than expected —
`InAppCameraHost.kt:49` already passes `LocalContext.current.applicationContext`, so the session's
`appContext` **is** the `Application` and the cast is a null-check rather than a gamble. The 1 MB
rotation question has a clear answer: a twenty-shot session writes about **4.9 KB, under half a
percent of one generation**, and cannot displace the sweep or StrictMode entries. One correction to
the dispatch's wording is carried forward (four outcomes, not two), and one consequence is named that
nobody has stated yet: **the panel reads only the current generation**, so a rotation — whenever it
happens, from any writer — moves earlier entries out of in-app view.

---

## §1 — Confirmed, with lines

- **The seam exists and spans both variants.** `DebugDiagnostics` is a real class in
  `app/src/debug/.../diagnostics/DebugDiagnostics.kt:58` and a no-op twin in
  `app/src/release/.../diagnostics/DebugDiagnostics.kt:13` whose `recordSweep(deleted) = Unit` (`:16`).
  The release doc states the reason plainly: the twin exists so the call sites in `src/main` compile
  in both build types **without a `BuildConfig.DEBUG` branch**, which with `isMinifyEnabled = false`
  would have shipped the debug classes into the release APK unreachable rather than absent. Adding a
  method means adding it to both; that is the whole cost of the chosen seam.
- **The writer pattern to copy.** `recordSweep` (`debug:67`) is one line:
  `executor.execute { log.append(...) }`. The executor is a single worker, core size zero, five-second
  keep-alive (`:newExecutor`), chosen so writes never happen on the thread under the StrictMode policy
  and so a per-install thread does not accumulate across a 1,400-test Robolectric run. A capture
  recorder uses the same executor and inherits both properties.
- **The session can reach the Application.** `CameraXCaptureSession`'s first constructor parameter is
  `private val appContext: Context` (`main/.../photo/CameraXCaptureSession.kt:155`), and its only
  construction site passes `LocalContext.current.applicationContext`
  (`main/.../ui/log/InAppCameraHost.kt:49`). `ForagerApplication.diagnostics` is a
  `lateinit var … private set` (`:23`) assigned at `onCreate` (`:37`) **before** `container` is built.
- **Ordering is safe.** A camera session is constructed by a user action inside a composed screen,
  which cannot run before `Application.onCreate` has returned. The `lateinit` is therefore set by the
  time any session exists. Read from the two lines above, not assumed.
- **The four outcomes and their current log lines.** `Rewritten` (`:349`), `Kept` (`:351`), `Declined`
  (`:353`, carrying `outcome.reason`), `Failed` (`:355`, carrying `outcome.error`). There is a fifth
  path worth recording that is not an outcome at all: `intended == null` returns early at `:341-344`
  with its own warning, and a shot that takes it produces no outcome line today.
- **The `Shot:` values.** `:315-318` logs `deviceRotation`, `capture.targetRotation` read back,
  `intended?.rotationDegrees`, `intended?.resolution`.

## §2 — The 1 MB rotation cap, answered

`DiagnosticsLog.append` rotates when `file.length() > maxBytes` (`debug:63`), `maxBytes` defaulting to
`1L shl 20` (`:106`). Rotation renames the current file to `<name>.1`, deleting the previous `.1`
(`:83-90`). Each entry is an ISO-8601 instant plus a space plus the summary plus a newline.

Measured against the real line formats, worst case per outcome:

| | bytes |
|---|---|
| timestamp + separator | 25 |
| `Shot:` entry | 111 |
| outcome entry (worst of the four, `Rewritten`) | 134 |
| **per capture** | **245** |
| **twenty-shot session** | **4,900 — 0.47% of one generation** |
| captures needed to fill one generation | ~4,279 |

For scale, `DiagnosticsLog`'s own doc puts one generation at roughly 300 full-stack StrictMode
entries, i.e. ~3.5 KB each; **one capture pair is about 7% of a single StrictMode stack entry**. The
cap is dominated by StrictMode stacks, not by captures, and a multi-shot session does not move it.

**What this does to the sweep and StrictMode entries: nothing, at this volume.** They share one file,
so capture entries consume the same budget, but 4.9 KB per session against a 1 MB cap cannot
meaningfully advance a rotation.

**The consequence that is real, and is not new to this change.** When a rotation does happen, the
panel stops showing what rotated out: `DiagnosticsPanel` reads `log.read()` and `log.sizeBytes()` and
shares `log.file` (`debug/ui/diagnostics/DiagnosticsPanel.kt:160,226,271`) — **`rotatedFile` appears
nowhere in it**. The previous generation survives on disk and is invisible in-app. Capture entries make
the file fill somewhat sooner and so make this reachable sooner; they do not create it. Recorded here
because a device check that reads the panel after a long session should know that a missing early entry
may be rotated rather than absent. Not fixed: changing what the panel reads is outside this dispatch.

## §3 — Inferred, and could not determine

- **Inferred:** that `appContext as? ForagerApplication` succeeds in production. `getApplicationContext()`
  returns the `Application` instance, and `ForagerApplication` is this app's registered application
  class — but that is reasoning about the platform, not a line I read in this repo. The safe cast plus
  a logged no-op is what makes the inference not matter.
- **Could not determine:** whether any real device path constructs a `CameraXCaptureSession` with a
  context whose `applicationContext` is not `ForagerApplication`. There is exactly one construction
  site today (`InAppCameraHost.kt:52`); a future second one is what the safe cast protects against.
- **Not verified, and only a device can:** that the recorded values are the ones the device produces.
  This dispatch moves existing values to a second destination; it does not change them, and the
  device check is still what establishes what the HAL does.

## §4 — Premises corrected

- **"The rewritten-or-kept outcome" is two of four.** The code has `Rewritten`, `Kept`, `Declined` and
  `Failed`, and the dispatch's own follow-up accepted the correction. `Declined` carries a `reason` and
  `Failed` carries an `error`; recording either without its reason would lose the part that
  distinguishes a HAL that rotated the pixels from a file that could not be written.
- **A fifth path exists that is not an outcome.** `intended == null` (`:341`) returns before the
  `when`, so a shot with no resolution info currently produces a `Shot:` line and no outcome line.
  Recording it is a small scope question, flagged in §5 rather than decided here.
- **The seam choice was revised by the owner** from a constructor parameter to a method on
  `DebugDiagnostics`, on the reasoning that it follows the one seam that already exists, keeps release
  a no-op with no main-side branching, and does not push an `Application` lookup into
  `InAppCameraHost`. Recorded so the rejected option is not re-proposed: the testability argument for
  the constructor was considered and judged to buy back a test only for the wiring, which is
  device-covered anyway.

## §5 — The plan, and what it will not touch

**Add** `recordCaptureOrientation(...)` to both `DebugDiagnostics` classes — real in debug (one
`executor.execute { log.append(...) }`), `= Unit` in release. **Call** it from
`CameraXCaptureSession` at the two existing sites (`:315` and the `when` at `:347`), alongside — never
instead of — the `Log.i` calls. **Reach** it with
`(appContext as? ForagerApplication)?.diagnostics`, resolved once and, when null, logged once with the
reason and thereafter a no-op; never a crash, never a per-shot log storm.

**Not touched:** the capture path's own values, `effectiveDeviceRotation`, the window lock, the
control-angle path, `IntendedOrientation` itself, the scrub, the sweep, the persist path, the
existing `Log.i` calls, `DiagnosticsLog`, and what the panel reads.

**Open, for the owner, not decided here:** whether the `intended == null` early return (§4) should also
record. It is one line and one more `= Unit`; it is also a path nobody has seen fire.

**Device check.** The existing check reads `adb logcat -s CameraXCaptureSession`. It gains a line
stating explicitly that **it must be run on a debug build** — the store, the panel and StrictMode are
all debug-only by source set, so on a release build these entries do not exist. That assumption has
been implicit and is now load-bearing.
