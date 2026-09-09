# Completion report — a Stop action on the recording notification

**Dispatch:** B1a from the planner ("the smallest change that makes an orphaned recording
recoverable"). **Base:** `origin/main` at `699efa3`. **Branch:** pushed as
`claude/recording-stop-action`; built on a local worktree branch (`claude/stop-action-b1a`) for the
reason in "A collision in the shared checkout" below. **Not built:** the open-track recovery flow
— that is a separate, later dispatch, and this report says exactly what it leaves behind.

## The problem, re-verified

Every claim below was read in this branch's own files, not taken from the dispatch.

- `TrackRecordingService.buildNotification()` (`TrackRecordingService.kt:183-197` before this
  change) built the ongoing notification with `setContentTitle`, `setContentText`, `setSmallIcon`,
  a `setContentIntent` opening `MainActivity`, and `setOngoing(true)`. **No `addAction` call, no
  action button anywhere in the file.**
- `TrackRecordingUiState.isRecording` (`TrackRecordingUiState.kt:92`) is
  `get() = activeTrack != null` — derived entirely from an in-memory ViewModel field.
- `TrackRecordingViewModel`'s `init` (`TrackRecordingViewModel.kt:154-157`) is
  `{ loadWaypoints(); loadTracks() }`. Neither repopulates `activeTrack`. `loadTracks` reads the
  track list for display; nothing looks for a track with a null `endedAtEpochMillis` and resumes it.
- The service is driven *from* the UI and never back: `MainActivity.kt:263-289`, a
  `LaunchedEffect(trackUiState.activeTrack)` that sends `ACTION_START` when `activeTrack` becomes
  non-null and `ACTION_STOP` when it becomes null again. There is no channel from the service to
  the ViewModel — no binder, no repository flag, no broadcast.

So the dispatch's description holds: an Activity destroyed while the foreground service survives
leaves a recording that the reopened app reports as not running, that keeps `LocationManager`
updates registered, and that — before this change — had no off switch short of force-stopping the
app. Confirmed by reading, not by running on a device; see "What was not verified".

## What was built

**One notification action, routed to the stop path that already existed.**

`TrackRecordingService.buildNotification()`:

```kotlin
val stopIntent = PendingIntent.getService(
    this,
    REQUEST_CODE_STOP,
    Intent(this, TrackRecordingService::class.java).setAction(ACTION_STOP),
    PendingIntent.FLAG_IMMUTABLE,
)
// ... .addAction(R.drawable.ic_track_recording, getString(R.string.track_recording_notification_stop_action), stopIntent)
```

- **`getService`, not `getActivity`.** The Activity is precisely the thing that may not exist; the
  action addresses the service directly.
- **`ACTION_STOP`, not a new action.** `onStartCommand` (`TrackRecordingService.kt:95`) already
  routes it to `stopRecording()`, which flushes the pending-point buffer, calls
  `container.endTrackUseCase(trackId)`, and calls `stopSelf()` (`:142-159`). **No second stop path
  was added** — a notification stop and an in-app stop are byte-identical Intents taking the same
  code. This is what "end the track properly and stop itself" means here; it is not new behaviour,
  it is the existing behaviour finally being reachable.
- **`FLAG_IMMUTABLE`** on both PendingIntents, as the file already did for the content intent.
- **Distinct request codes** (`REQUEST_CODE_OPEN_APP = 0`, `REQUEST_CODE_STOP = 1`). The two already
  differ by target type, so the platform's `(requestCode, filterEquals(Intent))` identity rule could
  not have conflated them; the constants make that independent of the target type rather than
  resting on it. The open-app intent's literal `0` became the named constant — the only incidental
  edit in the file.
- **Icon:** `R.drawable.ic_track_recording`, the service's existing small icon. It is the only
  drawable in `app/src/main/res/drawable/` other than the two launcher assets. `NotificationCompat`
  requires an icon resource for an action even though most Android versions since API 24 do not draw
  one in the shade. A dedicated stop glyph was not drawn — that is a design decision, not a coder's.

**String:** `track_recording_notification_stop_action` = "Stop recording", added to
`app/src/main/res/values/strings.xml`. Checked first: the file has four `track_recording_*` strings
and three `off_track_*` strings, and no string containing "stop" of any kind. Nothing was reused
because there was nothing to reuse.

## The half-state this leaves, and why it is left

The dispatch asked what happens to a ViewModel that is still alive when the shade stops the
recording. It is a real inconsistency and it is **not fixed here**. Precisely:

If the Activity is *alive* and the user taps Stop in the shade, the service ends the track and stops
itself, but `TrackRecordingUiState.activeTrack` stays non-null, so:

1. The UI keeps reporting a recording that no longer exists.
2. `TrackRecordingViewModel.beginPolling` (`:274-291`) keeps polling the (now ended) track's points
   every interval, forever.
3. `TrackRecordingViewModel.beginLocationTracking` (`:303-325`) keeps its **own** collection of
   `locationTracker.fixes` open. This is the one that matters: the ViewModel holds location updates
   independently of the service, so the shade's Stop releases the service's GPS registration but not
   the ViewModel's.
4. When the user later taps stop in-app, `TrackRecordingViewModel.stopRecording` (`:223-251`) clears
   the state and creates the end waypoint at *that* moment, and `MainActivity` sends `ACTION_STOP`
   to a service that is gone — which starts a fresh service instance whose `currentTrackId` is null,
   so it takes the `else` branch and calls `stopSelf()` (`:156-158`). Harmless, and the track's
   `endedAtEpochMillis` keeps the earlier, correct value from the shade stop.

So the data is right and nothing is corrupted; what is wrong is what the user sees and one leaked
location collection.

**Why it is scoped out.** Fixing it requires a channel from the service to the ViewModel that does
not exist in this codebase — a binder, a repository-observed "recording in progress" flag, or a
recovery read of the open track on `init`. Any of those is the open-track recovery flow the dispatch
explicitly reserved for a later dispatch, and picking one is an architectural decision
(CLAUDE.md: an unmade architectural decision is a stop-and-ask, not a judgment call). Building a
narrow one here would also be the conditional-threaded-into-working-code shape CLAUDE.md's Building
section rules out.

**Why shipping it anyway is still net-positive.** The scenario the dispatch is about — Activity
destroyed, service alive — has no ViewModel to desynchronise, because the ViewModel died with the
Activity. That case is now fully recoverable and was previously unrecoverable. The case this leaves
imperfect (Activity alive, user reaches past it into the shade) was *already* the case where the
in-app stop button works, so the user is never without a working control. The change strictly
enlarges what can be stopped; it makes nothing worse.

**What the later dispatch must do:** repopulate `activeTrack` on `init` from the open track, and
give the service a way to say it has stopped. Both halves, not one — recovering the track without a
stop signal would reintroduce this same desync from the other direction.

## Tests

New: `TrackRecordingServiceTest.the ongoing notification's stop action ends the recorded track`.

It deliberately does **not** assert "an action exists" and separately "ACTION_STOP ends a track".
Those are two semantic claims about wiring, and both would pass with the action pointed at the wrong
component or carrying an Intent the platform would not deliver. Instead it:

1. Creates a real, un-ended `Track` row through the real `AppContainer.trackRepository` — the same
   instance the service reaches via `application as ForagerApplication`.
2. Starts the service through `Robolectric.buildService(...).startCommand(...)` with the real
   `ACTION_START` Intent.
3. Reads the notification the service actually handed to `startForeground`
   (`shadowOf(service).lastForegroundNotification`), finds the action by its user-visible title, and
   asserts `ShadowPendingIntent.isServiceIntent`.
4. Unwraps `ShadowPendingIntent.savedIntent` — **the exact Intent the platform would deliver on a
   tap** — and feeds *that object* back through `controller.withIntent(...).startCommand(...)`.
   Nothing about the delivered Intent is hand-built by the test.
5. Asserts on the **track row**: `endedAtEpochMillis` non-null, polled until it appears. That value
   is `EndTrackUseCase`'s write and nothing else in the flow produces it.
6. Asserts the service then **stopped itself** (`ShadowService.isStoppedBySelf`) — the second half of
   the dispatch's "end the track properly *and* stop itself". Asserted after the row, so it can never
   mask a missing end. Waiting for it is also what guarantees `stopRecording()`'s coroutine has run
   to completion before `destroy()` cancels the scope, rather than being killed mid-flight and
   leaving work on `Dispatchers.Default` for the next test class's sandbox.

The permission-granted service path had been ruled out in this class's doc comment for several
dispatches, after a first attempt at it leaked a coroutine into the *next* test class's Robolectric
sandbox. This test needs that path (there is no notification without a started recording), so it
defuses the leak rather than inheriting it: both location providers are disabled before the service
starts, so `AndroidLocationTracker.fixes` registers no listener and merely awaits close; and the
recording is stopped inside the test body by the real `ACTION_STOP`, with both assertions waiting
for that stop to land before the controller is destroyed. That is
`TrackRecordingViewModelTest.runRecordingTest`'s rule (CLAUDE.md) applied to the service. The class
doc comment was rewritten to say so rather than left standing as "no permission-granted counterpart
here", which is no longer true.

### The revert check

Three reverted variants, each applied by editing the file and restored afterwards **from a copy
saved before the edit, never from `git checkout --`** (CLAUDE.md's rule, and the reason for it). The
build log was checked for compile errors *before* results were read, every time. All three were run
against the **final, shipped** test file — after both the `isStoppedBySelf` assertion and
`drainBeforeTeardown()` were added. Two earlier sets of revert runs, against intermediate versions of
the test, are not cited here: nothing in this table rests on a file that has since changed.

| Variant | Compile | Result | Failure message |
|---|---|---|---|
| Forward change | clean | 2/2 pass | — |
| R1 — `.addAction(...)` removed from `buildNotification` | 0 `e:`/`Unresolved reference`/`Compilation error` lines in the build log | 1 failure, 6.187 s | "the ongoing notification must carry a 'Stop recording' action — it is the only way to end a recording whose Activity has been destroyed" |
| R2 — `container.endTrackUseCase(trackId)` removed from `stopRecording` | same, 0 error lines | 1 failure, 16.008 s | "the track must be ended after the notification's own stop Intent reaches onStartCommand" |
| R3 — `stopSelf()` removed from `stopRecording`'s track branch | same, 0 error lines | 1 failure, 11.297 s | "the service must stop itself once the notification's stop has ended the track" |

Each message is specific to its own edit — R1's names the notification, R2's the track row, R3's the
service — so none is a stale result from a previous run. In all three runs the class's pre-existing
permission test still passed, a second, independent sign the run was fresh: a stale XML would have
carried the forward run's two passes, not one pass and one edit-specific failure. The three variants
are the three claims the test rests on: R1 that the button exists, R2 that the delivery actually
reaches `onStartCommand` and writes the row, R3 that the service does not linger. Without R2 in
particular, a test that only inspected the `PendingIntent` would have passed with the whole delivery
path broken.

After all three, the files were restored from the saved copies and the forward change confirmed
still present — `addAction` present, all four `stopSelf()` calls present, `isStoppedBySelf` present,
`endTrackUseCase` present twice, and `git diff --stat` still showing the same three files (195
insertions, 10 deletions).

### One timing check worth recording

The new test's first run reported 9.84 s, uncomfortably close to `awaitEndedAt`'s own 10 s timeout —
which would have made a passing run indistinguishable from one that nearly did not. Measured rather
than assumed: a temporary `println` in the poll loop reported **55 ms** of actual waiting. The rest
is Robolectric sandbox construction plus the real Room database opening, which lands on whichever
test in the class runs first (the pre-existing test, running second, takes 0.16-0.22 s). The
`println` was removed before commit. R2's 15.9 s is the same setup cost plus the full 10 s timeout,
which is what a genuine "never ended" failure should cost.

### The full suite, and a flake that turned out to be mine

| # | Tree | Tests | Failures |
|---|---|---|---|
| 1 | baseline: `origin/main` @ `699efa3`, clean worktree | 1277 | 0 |
| 2 | baseline, again | 1277 | 0 |
| 3 | baseline, again | 1277 | 0 |
| 4 | branch, new test present, **no drain** | 1278 | **1** — `JournalTabTest` |
| 5 | branch, unchanged | 1278 | 0 |
| 6 | branch, + `isStoppedBySelf` | 1278 | **1** — `JournalTabTest` |
| 7 | branch, new test method **removed** (service change kept) | 1277 | 0 |
| 8 | branch, new test method removed, again | 1277 | 0 |
| 9 | branch, final (with `drainBeforeTeardown`) | 1278 | 0 |
| 10 | branch, final | 1278 | 0 |
| 11 | branch, final | 1278 | 0 |

All figures read from `app/build/test-results/testDebugUnitTest/TEST-*.xml`, summed across all 166
class files, not from a Gradle summary line. The shipped state is runs 9-11: **1278 tests, 0
failures, 0 errors, 24 skipped, across 166 classes**, three times.

The failure in runs 4 and 6 was `JournalTabTest > From Album on the edit form opens the picker and
pulls the selected photo into the entry` — `AssertionError: The component with ContentDescription =
'Log photo' ... is not displayed!`, at the assertion after the picker returns to the edit form
(`JournalTabTest.kt:374`), not at the `waitUntil` above it.

**It would have been easy, and wrong, to call this pre-existing.** `DISPATCH-REPORT.md:11` documents
this exact test as a full-suite-only flake attributed to "a leftover async job from an earlier test
bleeding into the next test's Robolectric sandbox", and `STATUS.md:267` records another sighting.
Both would have licensed a one-line dismissal. What ruled it out was counting: the baseline is green
in 3 of 3 runs, and the same branch with only the new test method removed is green in 2 of 2, while
the branch with it was green in only 1 of 3. Eleven runs is a small sample, but 5 green without the
test against 1 green in 3 with it is not a base rate this dispatch's test can hide behind. **The new
test was destabilising `JournalTabTest`.**

The plausible mechanism is the one the existing doc comment already names, and it is the reason this
test needed care in the first place: this is the only test in the repo that drives the service's
permission-granted path, which does real work on `Dispatchers.Default` — a JVM-wide pool that
outlives the Robolectric sandbox — and opens the real Room database. `controller.destroy()` only
*requests* cancellation; the continuations that carry it out, and anything `stopSelf()` posted to
the main looper, still have to run somewhere, and "somewhere" was the next test class's sandbox.

The fix is `drainBeforeTeardown()`: idle the main looper, wait, idle again, all inside this test's
own sandbox, after every assertion. With it, three consecutive full-suite runs are green.

**Stated as evidence, not as certainty:** the mechanism was *not* isolated to a specific object —
whether it is the coroutine continuations, Room's executors, or both was not determined, and doing so
would have meant instrumenting Robolectric's sandbox teardown. What is established is the
correlation across eleven runs and that the drain removes the observable effect in three. Three green
runs cannot prove a flake is gone. If `JournalTabTest` fails again on this branch, this is the first
place to look, and the honest next step is isolating which of the two leaks it is — not deleting the
drain and not touching `JournalTabTest`. Nothing was `@Ignore`d, skipped, or weakened at any point
(CLAUDE.md: silencing a test is never in scope for a dispatch that did not ask for it); the one test
that was ever removed was **this dispatch's own new test**, temporarily, as run 7-8's control.

## What was not verified

- **Nothing was run on a device.** The action's appearance in the real shade, its label rendering,
  and the behaviour of an actual Activity-destroyed-service-alive recording are unverified here.
  Robolectric confirms the notification the service posts and the Intent the action carries; it does
  not confirm the system UI draws the button, and per CLAUDE.md a green Robolectric suite is not
  evidence about anything the shade renders.
- **`setOngoing(true)` and the notification channel's `IMPORTANCE_LOW`** were not re-examined for
  whether the action is reachable on every OEM shade.
- **The ViewModel desync above is reasoned from reading, not reproduced.** Steps 1–4 were traced
  through the named files; no test drives an Activity-alive shade stop.
- No POST_NOTIFICATIONS permission behaviour was touched or checked.
- **The `JournalTabTest` destabilisation's mechanism was not isolated.** The correlation and the
  remedy are measured across eleven full-suite runs; which leak (coroutine continuations, Room's
  executors, or both) is unverified.
- **No CI run.** All figures here come from this container's `./gradlew --no-daemon
  testDebugUnitTest`, not from GitHub Actions.

## docs/audits/README.md

**Not edited**, per the dispatch — the planner is batching index rows to avoid the known parallel-
session collision (CLAUDE.md, "`docs/audits/README.md` is a serialization point"). The row that
would have been added:

```
| 2026-09-09 | Recording notification stop action | Completion report | A "Stop recording" action on the foreground-service notification, wired to the existing ACTION_STOP path, so a recording whose Activity was destroyed can still be stopped. Records the ViewModel desync it deliberately leaves. | 2026-09-09-recording-notification-stop-action-completion-report.md |
```

(Column shape copied from the existing index; the planner should reconcile it against the table's
current header rather than take it verbatim.)

## A collision in the shared checkout

Reported because it affected this dispatch's git handling and may affect another session's work.

The container's reference clone at `/home/claude/forager` is **shared with at least one other
concurrently running session**. This session cut `claude/recording-stop-action` from `origin/main`
and began editing; the other session — working the beta-consent-text dispatch — then committed its
work (`d2d685e`, "Beta consent text: say what actually leaves the phone, and how a tester joins",
touching `docs/beta/*`, `docs/legal/privacy-policy.md`, and its own completion report) onto
**this** branch, because `HEAD` had moved under it. Its own branch, `claude/beta-consent-text`,
still points at `699efa3` and does not contain that commit.

Nothing was discarded. Actions taken, in order:

1. `git branch beta-consent-text-rescue d2d685e` — a second ref so that commit cannot be lost. No
   existing ref was moved; `git branch -f` was attempted first and refused by this environment,
   which is the correct outcome given CLAUDE.md's rule against moving unpushed history.
2. The three files this dispatch had modified were returned to their `origin/main` content in the
   shared tree **by copying pristine copies in**, not by a git discard, with this session's versions
   saved outside the repo first.
3. This session's work moved to an isolated `git worktree` at `/home/claude/forager-b1a` on branch
   `claude/stop-action-b1a`, cut from `origin/main`. Its commit is therefore clean of `d2d685e`.
4. That commit is pushed to `origin` under the dispatch's assigned name,
   `refs/heads/claude/recording-stop-action`. The local branch of that name in the shared clone
   still points at the other session's commit and was left untouched.

**For the planner:** the other session's commit is safe on `beta-consent-text-rescue` and on the
local `claude/recording-stop-action`, but it is **unpushed**, and it is not on the branch that
session thinks it is on. Per CLAUDE.md's push-before-you-tidy rule that is the thing at risk here,
not this dispatch. Two coder sessions sharing one working tree is a serialization point of the same
family as `docs/audits/README.md`, and worth the same treatment.
