# Device check v3, checked against the tree: four corrections, three of them in step 7 and step 12

**Date:** 2026-09-16 · **Branch:** `claude/new-session-vto65i` · **Base read:** `1e7d97a`, working tree
clean · **Subject:** `2026-09-16-pr102-device-check-v3.md`

This is the superseding note that document's own verification note asks for
(`2026-09-16-pr102-device-check-v3.md:264-269`: "Steps 3, 4, 7 and the noise list were written from
dispatch reports rather than from reading the tree … If a panel label, entry wording or arrangement
detail differs from what is written here, the tree is right and this document is stale. Say so and
it gets corrected, as a superseding note rather than an edit.").

The v3 document is **not edited**. It is committed verbatim as delivered, and this note stands
beside it. The index row for v3 points here.

**Read this before running step 7 or step 12.** Two of the four corrections would cost a runner time
at the phone: one sends them looking in the panel for a string that is only ever in logcat, and the
other asks them to read a log for a number nothing writes.

---

## 1. Step 7: the panel does not contain the string `Shot:`

**The document says** (`:158`): "**Every capture produces exactly two entries:** one `Shot:` entry,
and exactly one outcome entry from the six mutually exclusive paths."

**The tree.** The entry the panel shows reads `capture shot deviceRotation=… targetRotation=…
requestDegrees=… resolution=…` — `DebugDiagnostics.recordCaptureShot` at
`app/src/debug/java/com/zynergylabs/forager/app/diagnostics/DebugDiagnostics.kt:82-85`. The outcome
entry reads `capture orientation '<filename>' <branch>` followed by whichever of ` fromTag=`,
` toTag=`, ` degrees=`, ` reason=`, ` error=` apply (`DebugDiagnostics.kt:113-121`), with a
failure's stack as the entry's indented detail.

`Shot:` is a **logcat** line — `Log.i` at
`app/src/main/java/com/zynergylabs/forager/app/photo/CameraXCaptureSession.kt:349-353`, written
alongside the diagnostics entry and never into the log the panel reads. That is deliberate and
recorded (`DebugDiagnostics.kt:73-75`: "never instead of it: logcat still works for anyone who has
it, and this is for the device check reading the panel with no cable"). Since the premise of the
whole run is a phone with no logcat, a runner scanning the panel for `Shot:` finds nothing and has
no way to tell that from the invariant failing — which is the one reading step 7 exists to make.

**What to look for instead:** entries beginning `capture shot` and `capture orientation`. The count
is what matters and the count is unaffected.

## 2. Step 7: six paths, but only five branch labels

**The document says** (`:158-161`): "exactly one outcome entry from the six mutually exclusive
paths … This invariant is established by reading the seven call sites".

**The invariant and the call-site count are correct.** Seven call sites, confirmed by reading them:
one shot at `CameraXCaptureSession.kt:354`, and six outcome calls at `:380`, `:394`, `:405`, `:409`,
`:413`, `:417`. Every path that follows a shot records an outcome, including the two that attempt no
reapply. Two entries per capture holds.

**What is stale is what the runner will see.** The six paths carry only **five distinct branch
strings**, because the two no-reapply paths share one:

| Path | Branch string | Line |
|---|---|---|
| capture failed, no file written | `not attempted` | `:382`, reason at `:383` |
| no resolution info for the shot | `not attempted` | `:396`, reason at `:397` |
| tag rewritten | `rewritten` | `:405` |
| tag already correct | `kept` | `:409` |
| reapply declined | `declined` | `:413` |
| reapply failed | `failed` | `:417` |

The two `not attempted` entries are told apart only by their `reason=` text: "the capture failed and
no file was written" (`:383`) versus "no resolution info for this shot; it keeps the HAL's tag"
(`:397`).

**Two consequences for step 7's evidence line** (`:173`, "the outcome branch and values for the
setting-on capture", and `:167`, "Note the outcome branch named and its reason if it declined"):

- The reason is load-bearing for `not attempted` as well as for `declined`. Recording the branch
  alone loses which of the two paths ran.
- Step 7's prose (`:169-171`) names *Rewritten, Kept, declined, failed* as the outcomes worth
  recording. `not attempted` is the fifth label and is not mentioned; it is also the only one
  meaning the shot never reached the reapply at all, which is a different kind of finding from the
  four that did.

## 3. Step 12: nothing in this build records bytes dropped after EOI

**The document says** (`:246`): "If the log records bytes dropped after EOI, note the number. If
not, write 'not observable in this build'."

**The tree.** The second answer is the answer, and it is fixed before the run.
`PhotoMetadataScrub.kt` logs exactly three things: a read failure (`:77`), a non-JPEG input
(`:81`), and a scrub failure (`:110`). The truncation itself is `if (marker == EOI) return true` at
`:214`; it counts nothing and reports nothing. The file has no reference to `DebugDiagnostics` at
all. This is consistent with what the instrument dispatch already recorded — the scrub was left
untouched and the trailer step got no instrument from it
(`2026-09-15-debug-diagnostics-instrument-completion-report.md`, one-paragraph outcome and §2.5).

Not a defect in the checklist, which permits that answer. Flagged because "if the log records" reads
as something to go and check, and checking means reading up to a megabyte of log for an entry that
cannot be there.

**Worth noting alongside it:** step 10 item 2 (`:225`, "Confirm the last two bytes are `FF D9` in a
hex viewer") already answers the same question directly, on the real file, with better evidence than
a log line would be. Step 12 is redundant given step 10, not merely uninstrumented.

## 4. The completion report's §2.4 is not the section step 10 comes from

**The document says** (`:30` and `:205`): that v1 step 5 is "the reference in the completion
report's §2.4", and "This is the step the completion report's §2.4 refers to."

**The tree.** §2.4 of `2026-09-15-debug-diagnostics-instrument-completion-report.md` is *"The
sweep's count, and the panel"* (heading at report line 61) — the material v3 steps **7 and 8**
exercise. The step-5 EXIF procedure is at report **line 83**, inside *"§3 — What you will see on the
phone"* (heading at line 73). The v3 document's own verification note (`:267`) cites line 83
correctly; only the two `§2.4` attributions are wrong.

This is the failure CLAUDE.md names under *a derived figure carried across a boundary keeps its
authority and loses its provenance*: a section number quoted from memory reads exactly as
authoritative as one re-derived, and nothing in the quoting marks which it was.

---

## What was checked and found correct

Everything below was read in the tree, not taken from a dispatch report.

- **Step 3 and step 4, the conditional window lock.** `SCREEN_ORIENTATION_LOCKED` with the setting
  off and `SCREEN_ORIENTATION_PORTRAIT` with it on, with the one accepted flip in the latter case:
  `WindowOrientationLock.kt`, class doc, "The value depends on the setting" section. Matches steps
  3.1–3.5 and 4.1–4.3 as written, including "no flip animation" being a failure only with the
  setting off.
- **Step 3.3, the landscape arrangement.** Shutter at `Alignment.CenterEnd`
  (`InAppCameraDialog.kt:273`), Done at `Alignment.TopStart` (`:264` landscape, `:218` portrait).
  The arrangement is read once at open from `cameraArrangement(lockToPortrait, windowIsLandscape)`
  (`:146-147`), which is why rotating afterwards moves nothing. "The shutter along the bottom of a
  landscape frame is a failure" is the exact regression that version is documented as fixing
  (`WindowOrientationLock.kt`, "step 2, case 2.4").
- **The setting's label and default.** "Lock camera to portrait"
  (`CameraOrientationPreferenceRepository.kt:4`, `InAppCameraDialog.kt:113`), default **off**
  (`DataStoreCameraOrientationPreferenceRepository.kt:37`,
  `DEFAULT_LOCK_CAMERA_TO_PORTRAIT = false`) — so step 1's "off for all four" is the shipped state,
  not something to set.
- **Noise list, the Crash Logs violation** (`:61-62`). `crashFileStore.list()` is called during
  composition at `AvailabilityScreen.kt:1107` and `:2511`. Confirmed — and note the line numbers
  have drifted: the completion report cites `:1082` and `:2449` for the same two calls, written five
  commits ago. The claim is intact; its coordinates are not.
- **Noise list, rotation** (`:63-65`). `DiagnosticsLog.rotate` at `:83-89`, cap
  `DEFAULT_MAX_LOG_BYTES = 1L shl 20` at `:106`; the panel reads `log.file` only
  (`DiagnosticsPanel.kt:160`, `:226`) and `rotatedFile` (`DiagnosticsLog.kt:53`) has no reader in
  the panel. Correct, and already carrying its own index row
  (`2026-09-15-diagnostics-panel-hides-the-rotated-generation.md`).
- **Step 8's two-second allowance** (`:186-187`). `ORPHAN_MTIME_GUARD_MILLIS = 2_000L`
  (`CameraCaptureFiles.kt:84`). Correct.
- **Step 8's sweep entry.** Reads `sweep deleted=N orphaned capture file(s)`
  (`DebugDiagnostics.kt:68`), written on every launch including zero.
- **Step 8.3's "twenty new capture entries"** for ten photos. Follows from the two-entry invariant
  in correction 2, which holds.
- **Step 9's four class names** all exist: `FilePhotoStore`, `PhotoMetadataScrub`
  (`app/src/main/java/com/zynergylabs/forager/app/photo/`), `AddPhotoToLogEntryUseCase`,
  `AddPhotoToGalleryUseCase` (`…/domain/`).
- **Step 10's two exfiltration paths.** Both confirmed in the 2026-09-16 photo-share pulse: the
  debug panel's share (`DiagnosticsPanel.kt:162` → `:284-304`) and the `photos/` FileProvider root
  that makes it work, which is **debug-only** (`app/src/debug/res/xml/file_paths.xml:26`; main's
  file declares only `captures/`, `crashes/`, `tracks/`). The `run-as` form is correct: the debug
  build is debuggable (no `isDebuggable` override in `app/build.gradle.kts:280-282`, so AGP's
  default applies) and `applicationId` carries no debug suffix
  (`app/build.gradle.kts:243`).

## What this note does not cover

Steps 1, 2, 5, 6 and 11 were not re-derived from the tree. They describe behaviour observable only
on hardware — what a gallery shows, whether a viewfinder is live, what "Don't keep activities"
does — and there is nothing in the repository that could confirm or refute them. They are neither
endorsed nor doubted here; they were simply not checked. Step 12's *instrument* is settled above;
whether a trailer exists on this device is not.
