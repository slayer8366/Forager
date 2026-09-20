# The in-app camera never opened: diagnosis, failing test, restructure

**Date:** 2026-09-15.
**Request:** the owner's bug report from the device check on PR #102 — the Opening spinner
forever, every time, on a real device, with a live status bar and "No photos yet" — with a
diagnosis to verify before any code changed, a sequence, and constraints.
**Branch:** `claude/new-session-vto65i`, PR #102. From `0e83bd4` to `1797773`. No schema change.
The scrub, the sweep, `persist` and the orientation source are untouched; the interface is widened
by `open` and `close` only.

---

## §1 — The diagnosis, confirmed by reading before anything changed

On the head that shipped (`0e83bd4`), line for line:

- `InAppCameraDialog.kt:117` — `when (val state = session.state)`; `:128` `Opening ->` the
  spinner; `:133` `Ready -> viewfinder(Modifier.fillMaxSize())`. **The slot is composed only on
  `Ready`.**
- `CameraXCaptureSession.kt:96` — initial state `Opening`. `:146` `fun Viewfinder`; `:151` its
  `DisposableEffect`; `:166` `ProcessCameraProvider.getInstance` inside it; `:197`
  `state = CameraSessionState.Ready` inside it. **`Ready` is produced only inside `Viewfinder`.**
- `PhotoAcquisitionLaunchers.kt:178` — `viewfinder = { modifier -> session.Viewfinder(modifier) }`.
  **The slot is `Viewfinder`.** `:172` creates a fresh session per open, so every open starts
  `Opening`.

The dialog waited for `Ready` before composing the only thing that could produce it. The owner's
diagnosis is the mechanism; nothing in it is wrong. It was present from the first version of the
dialog, which is why it was every time.

## §2 — Why the suite missed it, and whether that is the whole reason

Three parts, and the owner named two:

1. `InAppCameraDialogTest.kt:105` — the fake defaulted to `Ready`, so the `Ready` branch composed
   at once and the gate was never seen closed.
2. `:140` — the slot was a plain `Box`, so even a test starting `Opening` would not have modelled
   "the slot's composable is what produces `Ready`".
3. **The fake's `state` was a plain `var` with no notion of being produced by opening.** A test
   could not express "`Ready` comes from being opened" because the fake had no open, and could not
   observe a state change after composition because nothing would recompose. This is the part that
   made the first two sufficient: the circularity had no representation in the test harness at all.

Behind all three: the real `Viewfinder` cannot run under Robolectric, which is structural and was
recorded. What was not recorded was that the *shape* of the state transition — inside a
composable gated on the state — was also untestable, and that is a design smell the interface
should have made visible. It now does: `open` is on the interface.

## §3 — The failing test, first

`FakeCameraCaptureSession` moved out of the test class to `app/src/test/.../photo/`, beside
`FileProviderCacheReset`, `internal`, with `state` backed by `mutableStateOf`. It starts `Opening`
when told to and becomes `Ready` only when `open` is called. The test composes the dialog with a
tagged `Box` as the slot and waits for that tag.

**On `0e83bd4`, before any production change:**

```
the viewfinder is composed once the session opens
  androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms
  at InAppCameraDialogTest.kt:144 (the waitUntil for the viewfinder tag)
the session is opened once on enter and closed once when the dialog leaves
  java.lang.AssertionError: expected:<1> but was:<0>   (openCalls)
```

Why that is the right failure: the dialog stayed on the spinner and never composed the slot,
because it never opened the session — and on that head it *could not*, since opening lived inside
the slot it was withholding. Not a compile error (the fake's `open()` was a plain member, the
interface had none, and nothing in production could reach it), not a missing method. The second
test fails on the same fact from the dialog's side: `open` was never called.

## §4 — The restructure

`CameraCaptureSession` gains `open(lifecycleOwner: LifecycleOwner)` and `close()`. Compose-free:
`LifecycleOwner` is `androidx.lifecycle`, and `Viewfinder` stays a concrete member of the
implementation for the reason its own doc gives.

`CameraXCaptureSession.open` holds the provider fetch, the bind and the orientation listener;
`close` releases them. `Viewfinder` keeps only the `PreviewView` and the attachment of its surface
provider to the already-bound `Preview`, which `Preview.setSurfaceProvider` allows at any time
after binding — that is what lets the preview be bound before there is anything to draw it into.
The dialog calls `open` on enter and `close` on leave from its own `DisposableEffect`
(`InAppCameraDialog.kt`, directly after the `rememberCoroutineScope`). Its `when` is unchanged.
State machine: `Opening` from `open()` until bound, `Ready` once bound, `Unavailable` on any
failure.

**On the alternative the owner rejected** — always composing the slot and drawing the spinner over
it — I agree with the rejection and for the reasons given, plus one: it would also put a live,
black `PreviewView` behind the `Unavailable` reason text. No different structure was better; none
was built.

### Lifecycle edges

| edge | handling | carried over or new |
|---|---|---|
| dismissed while the provider is still resolving | `close` sets `isOpen = false` and bumps `openEpoch`; the provider callback compares its captured epoch and returns without binding, logged at INFO | **new** — the old `onDispose` had no guard against a late callback binding to a dead screen |
| `close` when `open` never completed | every field nullable; `disable()` on the listener is safe whether or not `enable()` ran; `boundProvider` is null so nothing is unbound; a second `close` is a no-op on `!isOpen` | carried over in spirit (the old `onDispose` used `bound?.unbindAll()`), made explicit |
| reopen after close | production creates a fresh session per open (`PhotoAcquisitionLaunchers.kt:172`); the same instance also reopens cleanly with a new epoch, listener, fetch and bind | new (the old design had no reopen path) |
| orientation listener exactly once per open | created and enabled in `open`, disabled and dropped in `close`; a second `open` without `close` is refused and logged | carried over (enable/disable in the old effect), now with the double-open refusal |

### Permission flow

Unchanged in order. `PhotoAcquisitionLaunchers.kt:116-124`: `launchCamera` sets `isCameraOpen`
only when the grant is already held, else requests; `:98` the request callback sets
`isCameraOpen = granted`; `:170-171` `CameraDialog` composes `InAppCameraDialog` only when
`isCameraOpen`. `open()` is called by the dialog's effect, so it runs only after a grant. On a
denial the dialog is never composed and `open()` is never called: nothing is left half-open.

---

## Evidence

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`0e83bd4`) | 180 | 1452 | 0 | 0 | 24 |
| after (`1797773`) | 180 | 1454 | 0 | 0 | 24 |

1452 + 2 = **1454**, counted. Skip count unchanged; CI allowlist untouched. `assembleDebug` exit 0.
Zero `e:` lines. The two new tests pass after the fix (15/15 in the class).

**Revert check**, run against committed state, through the runner with its compile-error guard:
removing the dialog's `session.open(lifecycleOwner)` fails exactly the two new tests with
exactly the pre-fix messages — the `ComposeTimeoutException` at 5000 ms and `expected:<1> but
was:<0>`. No compile errors; tree clean after.

### Could not be verified without a device
- That the camera now opens. The fix is in the one file no test can reach; the tests prove the
  dialog opens the session and composes the viewfinder when the session says `Ready`, not that
  CameraX says it.
- That `Preview.setSurfaceProvider` after bind draws into the `PreviewView`. Documented CameraX
  behaviour; not observed here.
- Every earlier device-check item, now to be run from the top as the owner said, since the open
  path touches every step.

### Decided beyond scope
- The double-`open` refusal and its log. Not asked for; a stacked listener is the failure it prevents.
- The INFO log when a provider resolves after close. CLAUDE.md: no silent fallback.

### Consequence for PR #103
The stacked groundwork branch moved `FakeSession` with the same name and location and returns
`CaptureOutcome`; its rebase onto this will conflict on that file and on the interface (which now
also has `open`/`close`). Resolvable by reading, as the owner anticipated; #103 is untouched until
then.
