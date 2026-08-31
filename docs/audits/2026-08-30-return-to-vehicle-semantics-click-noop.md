# Return-to-vehicle: Compose semantics-layer click no-ops, real touch does not

**Status:** open. Scope: the test harness disagreement only — the product path is confirmed
working. Not blocking; the three affected tests are `@Ignore`d with this document as their record.

**Closing this out is two changes, not one.** Un-`@Ignore`ing the three tests once they pass again
is only half of it — `.github/workflows/ci.yml`'s "Summarize the test results" step also carries a
named `SKIPPED_TESTS_ALLOWLIST` for exactly these three `(classname, name)` pairs (added
2026-08-30, see that step's own comment). That allowlist checks by exact set membership in both
directions: an entry with no matching actual skip fails the build just as an unlisted skip does.
So the moment any of these three tests stops being skipped, its entry must be removed from
`SKIPPED_TESTS_ALLOWLIST` in the same change — CI will fail and say so if it isn't. Closing this
investigation fully means the allowlist is empty (or removed outright), not just shorter.

## The claim under investigation

Three tests in `AvailabilityScreenMapIconStackTest.kt` — `tapping the control pill's
return-to-vehicle button calls onToggleReturning` and the two `a real touch at each trailhead
control's own screen coordinates reaches that control(...)` variants — fail deterministically at
current HEAD (`3df717b`), including under true `forkEvery = 1` per-test JVM isolation with a single
`--tests` selection (no other test in the run at all). The semantics node they tap is verified
correct: exactly one node for the `control-pill-return-to-vehicle` tag, `hasOnClick = true`,
`OnClick` action bound to the expected `AbstractClickableNode` lambda, merged and unmerged queries
resolve to the *same* node id and the *same* `OnClick` action identity. `performClick()`/
`performTouchInput()` complete without throwing on every retry attempt; the callback is simply
never invoked.

## What was ruled out

- **Cross-test JVM/Robolectric contamination.** The original diagnosis (see `retryClick`'s own doc
  comment, written before this investigation) held that the failure required *some other test* in
  the same JVM fork. Direct testing disproves this: `./gradlew :app:testDebugUnitTest --tests
  "...tapping the control pill's return-to-vehicle button calls onToggleReturning"` with
  `forkEvery = 1` and no other test selected still fails, deterministically, at current HEAD. The
  `forkEvery = 1` isolation infrastructure that was built on the contamination diagnosis was
  reverted (never merged) once this was confirmed.
- **The regression is real but code-side.** Bisecting the commit history: at `dc1d3e9` (last commit
  before `72f0a54`, the search-bar redesign), the identical isolated single-test run passes reliably
  (4/4 runs). At `72f0a54` and at current HEAD, it fails deterministically. Something in that commit
  changed the failure from "doesn't reproduce alone" to "always reproduces alone."
- **The compass-strip-clearance padding specifically.** `72f0a54` added a measured top-padding on
  the search dropdown's `AnimatedVisibility` to keep it clear of the compass strip. Neutralizing
  that padding (`padding(top = 0.dp)`) in isolation does not fix the failure — ruled out as the
  mechanism.
- **A moved semantics merge boundary.** Compared the unmerged and merged semantics trees around
  `ControlPill` at `dc1d3e9` and at `72f0a54` via a throwaway instrumented test run in scratch git
  worktrees (never touching this branch). Tree topology around `ControlPill` is unchanged between
  the two commits — same shape, same tag arrangement, no new merge-boundary ancestor introduced
  between the record and return-to-vehicle rows. For the `control-pill-return-to-vehicle` tag
  specifically, merged and unmerged queries agree on node id and `OnClick` action identity in both
  commits. As directly measured, "a merged node reports a different node's `OnClick`" is not what's
  happening at the tag level.
- **The product itself.** Built `assembleDebug` from unmodified current HEAD (`3df717b`), installed
  on a real (non-Robolectric) Android 10 x86_64 emulator, and issued three real `adb shell input tap`
  events at the return-to-vehicle row's actual on-screen coordinates (confirmed by screenshot, not
  assumed) — not `performClick()`, not any Compose semantics-layer dispatch. All three taps produced
  the correct visible state change (`DistanceArm` extending/retracting, icon color toggling) in the
  expected alternating pattern. A control tap on the record row in the same session confirmed
  tap-targeting itself was sound (its first read looked like a miss; a longer settle wait showed it
  had in fact registered — the emulator's software renderer was dropping 700–900ms per frame under
  pure QEMU TCG emulation with no `/dev/kvm`). The click-to-callback wiring works correctly on a
  real Android runtime.

## What's still open

Why a Robolectric-hosted Compose semantics-layer click no-ops on this specific node — reliably,
reproducibly, in true single-test isolation — while the identical production wiring fires correctly
on a real touch event on a real runtime. The merge-tree check compared node identity and `OnClick`
action identity at the `control-pill-return-to-vehicle` *tag* level only. It did not look inside
that node — `MapBarIconButton`'s own `Icon` child, or the underlying gesture-detector
(`pointerInput`) node beneath the semantics layer. That's the first place to look: whether the
*semantics* `OnClick` action and the *actual* pointer-input coroutine that's supposed to invoke it
are still the same object once composition finishes, specifically for this row, specifically in
this Robolectric/Compose version combination.

## What this leaves uncovered

`TrackRecordingViewModelTest.kt` (`startReturn is a no-op with nothing recording`, `startReturn
marks returning while actively recording, stopReturn clears it without stopping the recording`)
covers the `startReturn()`/`stopReturn()` state machine directly — the `isRecording` precondition
and the toggle-back path — bypassing the Compose semantics layer entirely. It does **not** cover
Compose's click-to-callback wiring for this row (`MapBarIconButton`'s `.clickable(...)` reaching
`onToggleReturning`). That's exactly the gap the three `@Ignore`d tests exist to close once this
is root-caused; closing this document without restoring them to real, passing coverage leaves that
gap open.

## Timeline note

Two rounds of investigation went into this before the on-device check: extensive `waitForIdle()`/
`mainClock`/Looper-drain experimentation, a `forkEvery = 1` isolation build, and a real commit-level
bisection that pinned `72f0a54` as the boundary. All of that data came from inside the same harness
under investigation — including the bisection, which made the regression look more code-real, not
less. The single tap that actually resolved the product question came from outside it. A failure
that reproduces at 100% inside a harness says nothing about whether the harness is the thing that's
wrong.
