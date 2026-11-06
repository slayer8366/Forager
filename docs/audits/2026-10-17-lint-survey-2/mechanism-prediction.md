# Mechanism prediction, written before the CI run (dispatch 2026-10-17-03, Phase 1)

Hazel Cairnwright, committed before `gh workflow run` was issued. Not edited after the run; the
report compares against it.

Base: origin/pre-main `b221e85b` (PR #245 still open; `git merge-tree` of pre-main and main gives
pre-main's own tree, `2cc55a92`, so the merge would change no file).

1. **Why the total moves, and which way.** `app/src/debug` is gone from pre-main (`app/src` holds
   `androidTest`, `main`, `test`, `testDebug`). October's 18 NewApi errors, 41 LogNotTimber,
   StaticFieldLeak and UsableSpace were all in that folder, so those 61 issues leave the debug count
   by removal, not by fixing. Against that, `src/main` gained logging: a plain regex count of
   `Log.v/d/i/w/e/wtf(` in `app/src/main` gives 230 at `921aa9c7` (lint said 231) and 258 at
   `b221e85b`. So LogNotTimber should read about 259 (all in main), still the largest id.
   Predicted debug total: roughly 340 to 360, a little *below* October's 376, with errors down from
   29 to about 11 (the 7 shipped-code errors, the 4 test-only UnrememberedMutableState, plus any
   new ones). This disagrees with the planner's "near October's" only in direction and detail.
2. **The seven shipped-code errors** (NonObservableLocale 5, Range 1, SuspiciousIndentation 1) are
   still there, because no commit since `921aa9c7` touched them (inventory section 2), possibly
   with one more SuspiciousIndentation at `AvailabilityMapControlsUi.kt:848`. DataExtractionRules
   is still there (`allowBackup="false"`, no rules file).
3. **Release lint needs no secret.** The release build type's `signingConfig` is null when no
   identity is set (`app/build.gradle.kts:295`), and the only thing that fails on that is
   `verifyReleaseNeverSignsWithDebugKeystore`, wired as a dependency of `assembleRelease` and
   `bundleRelease` only. Lint analyses sources and does not package or sign, so `lintRelease`
   should run to the end. There is no `src/release` folder, so "release-only sources" do not
   exist on pre-main; release lint should differ from debug only by dropping `src/testDebug`
   and debug-only dependencies (`debugImplementation(libs.androidx.ui.tooling)`), giving the same
   or a slightly smaller count.
4. **The ExperimentalCamera2Interop opt-in.** The marker is `androidx.annotation.RequiresOptIn`,
   which the Kotlin compiler does not enforce; lint's `UnsafeOptInUsageError` does. Both RAW
   files (`CameraXRawCapture.kt:62, :143`, `CameraXCapabilityReader.kt:83, :88`) carry
   `@OptIn(markerClass = [ExperimentalCamera2Interop::class])`, so the honest run should show
   **no** opt-in finding there. An absence cannot tell "enforced and satisfied" from "not
   checked", so I plan one positive-control run on the same scratch branch afterwards with the
   opt-in at `CameraXRawCapture.kt:62` removed; prediction: one or more `UnsafeOptInUsageError`
   (Error) at `Camera2Interop.Extender(builder)` (`:68`). That control is a call beyond the
   dispatch and is restored from a saved copy afterwards.
5. **New ids** from the RAW camera code and other new code: a few (InlinedApi or NewApi on
   camera2 constants is the likeliest), not dozens.
