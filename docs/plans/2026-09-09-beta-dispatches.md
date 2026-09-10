# Beta dispatches, 2026-09-09

Planner handoff. Five dispatches, written so a coder session with no memory of the planning
conversation can pick any one up cold. Base for all of them: `main` at `699efa3`.

Read root `CLAUDE.md` first. Every dispatch below assumes its rules, in particular: verify the base
branch before starting; a claim about this codebase names a file and location or is stated as
unverified; check reachability before measuring behaviour; prove a new test bites by reverting from
a saved copy, never `git checkout --`; report what was skipped or left unverified.

## The constraint these are ordered against

The beta is **Play closed testing only, no sideloading** (owner ruling, 2026-09-09). Google requires
12 testers each opted in **continuously for 14 days** before production access — the clock runs per
tester, so one dropout on day 10 costs fourteen days, not four. Updates pushed to the closed track do
**not** reset anyone's clock.

That splits the work at the moment the track opens. Dispatches 1 and 2 below are the ones whose
absence could cost a tester; 3, 4 and 5 improve the reports and can land while the clock runs.

Already landed as separate branches, not repeated here: the beta consent-text correction
(`claude/beta-consent-text`), the recording-notification Stop action (`claude/recording-stop-action`),
and `allowBackup="false"` plus the Play-signing note (`claude/backup-and-signing-notes`).

---

## Dispatch 1 — Open-track recovery on launch

**Priority: before the track opens.** This is the rest of finding B1; the Stop action shipped the
cheap half.

### What is true today

`TrackRecordingUiState.kt:92` — `val isRecording: Boolean get() = activeTrack != null`, and
`activeTrack` is in-memory ViewModel state. `TrackRecordingViewModel`'s `init` calls only
`loadWaypoints()` and `loadTracks()`; nothing looks for an unfinished track. The ViewModel's own doc
comment (around `:60-68`) already names this gap and says it was deliberately scoped out.

`TrackRecordingService` and its Room row survive events that clear the ViewModel. `TrackDao.kt:42`
shows the shape of an unfinished track: `endedAtEpochMillis IS NULL`.

### The failure this closes

Activity destroyed mid-walk, service still recording. Reopened app shows *not recording*. If the user
taps record, `StartTrackUseCase` writes a **second** Track row, `MainActivity.kt:263-289` sends
`ACTION_START`, and `TrackRecordingService.kt:82` refuses it because `recordingJob != null` — so
points keep landing on the **old** track id and the new row receives none. A later stop ends the old
track and leaves the new one open forever. The tester sees an empty track and reports a GPS fault.

### The task

On ViewModel init, find any track with `endedAtEpochMillis == null` and put the UI into a state that
tells the truth about it. At minimum the user must be able to **close** such a track; **resume** is
better where the service is in fact still running.

Decisions the coder must make explicitly and record, not resolve silently:

- **How the UI learns whether the service is actually alive.** A stranded Room row and a live service
  are different states and must not be conflated — the row exists in both. There is no
  service→ViewModel channel today; `MainActivity.kt:263-289` is one-way. Picking one is this
  dispatch's architectural decision. Surface the options rather than choosing quietly (CLAUDE.md's
  ambiguity rule).
- **What happens to a track resumed after a gap.** The breadcrumb has a hole in it. Say what the
  track means afterwards and whether anything marks the gap.
- **More than one unfinished track.** Possible today via the second-track path above. Handle it or
  state why it cannot occur after this change.

### Verification that would actually count

A test driving the real entry point: seed an un-ended Track through the real repository, construct
the ViewModel, assert the recovered state. `performClick` on the recovery control asserts wiring, not
routing — if the claim is "a touch here reaches this control", use a coordinate touch, sampled at
several points across the target's bounds.

**Hazard:** `TrackRecordingViewModel` polls in an unbounded `delay` loop while recording. A test body
that throws before stopping the recording hangs `runTest` forever — `runTest`'s own timeout did not
fire in 77 minutes on that spin. Go through `TrackRecordingViewModelTest.runRecordingTest`, which
stops every recording in a `finally`.

### Out of scope

Moving the off-track alert (dispatch 2). Do not fold them together — they touch the same ViewModel
and merging them makes both harder to review.

---

## Dispatch 2 — Off-track detection into the service

**Priority: before the track opens.** Finding B2. This is the safety one.

### What is true today

The alert works, contrary to older docs. `TrackRecordingViewModel.kt:457-464` calls
`alertDelivery.deliver(Alert(OFF_TRACK, overridesSilence = true))`. `AndroidAlertDelivery.kt:90-104`
and `:132-144` post a HIGH-importance notification and an independent alarm-usage vibration that
survives a silenced phone. `POST_NOTIFICATIONS` is requested at runtime, `MainActivity.kt:406`.
`DetectOffTrackUseCase.kt:17-27` needs three return-distance readings with a net +25 m; the cooldown
is 120 s. The comment at `AndroidManifest.xml:21-23` claiming the permission is never requested is
**stale** — fix it while you are here.

`AlertDelivery.kt:38-49` documents the gap honestly: the decision to fire lives in the ViewModel.

### The failure this closes

Task swiped while recording — the exact posture the feature exists for, a pocketed phone on a long
return leg — and nothing will ever say "off track" again for that walk, while the service goes on
recording.

### The task

Move the off-track evaluation into `TrackRecordingService`, which already collects the same
`LocationTracker.fixes` stream. The service should own detection and delivery; the ViewModel should
read state rather than drive alerts.

**Second prize in the same change:** the ViewModel currently registers its *own* OS location listener
while recording, so there are two independent registrations. Its doc comment (search "two independent
OS location-listener registrations") calls this a real, accepted duplication. Collapsing it is a
battery win, and battery is the first thing the trip report asks about. If you cannot collapse it
safely in this dispatch, say so and why — do not leave it unmentioned.

### Verification

The alert must fire with no Activity in the picture. A test that only proves the ViewModel still
calls `deliver` has not tested this dispatch. State plainly which parts remain device-only:
notification posting and vibration behaviour are not observable in this environment, and a green
Robolectric suite is not evidence about them.

### Out of scope

Changing the detection thresholds or the cooldown. This dispatch moves where the decision is made,
not what it decides — keep the two separable so a later threshold change has a clean base.

---

## Dispatch 3 — Release build in CI

Finding B5. Can land while the clock runs, but every beta update before it lands is a hand build with
no gate behind it.

### What is true today

`.github/workflows/ci.yml` runs `assembleDebug` (`:96`) and `testDebugUnitTest` (`:157`) and uploads
`app-debug-apk` (`:400`). There is no `assembleRelease`, no `bundleRelease`, no `lint`, no `.aab`
anywhere in `.github/` or `scripts/`. `verifyNothingTestOnlyReachesTheApk`
(`app/build.gradle.kts:554-555`) is wired to `assembleDebug` and reads `outputs/apk/debug` only
(`:490`).

The signing guard at `app/build.gradle.kts:323-357` refuses a release signed with the debug key three
ways. `resolveBuildIdentity()` (`:71-108`) falls back to `versionCode = 1` with an `UNVERSIONED-*`
name on a shallow clone or missing git metadata, and only **warns** (`:179`).

### The task

1. A CI job that runs `bundleRelease` — Play takes AABs, not APKs — when the four signing secrets are
   present, and otherwise asserts the guard **fires**. That way the guard itself is tested on every
   PR rather than trusted. CI holds no keystore today and should not start.
2. Promote the provisional-identity fallback to a **hard failure** on the release variant. A release
   that ships as `versionCode 1` is un-updatable, and today only a log line stands between you and
   that.
3. Point `verifyNothingTestOnlyReachesTheApk` at the release artifact too, or say why it cannot.

### Do not

Enable R8. `isMinifyEnabled = false` (`app/build.gradle.kts:232`) and `proguard-rules.pro` is a
single comment, so the classic missing-keep-rule serialization crash cannot happen today. Turning
minification on would inject exactly that class of bug into the one variant nothing has ever tested.
It is a post-beta change with its own dispatch.

---

## Dispatch 4 — Merge the GPX full-record branch

Small, mechanical, and it unblocks the beta's highest-value question.

`claude/new-session-bogh3s` is **4 commits ahead of `main` and 0 behind**. It carries GPX format B1:
the raw point sequence with per-point kept/excluded verdicts in `<trk><extensions>`, and waypoints
included. It has tests.

Today `TrackGpxExporter.kt:28` hard-codes `waypoints = emptyList()`, so the car, the patch and the
origin never reach the shared file; and the export reads through `excludeNetworkProviderFixes`
(`RoomTrackRepository.kt:66-79`) with no marker for excluded points. That is why a tester's file
cannot currently answer "did the track look shorter than the walk?" — the question `docs/beta/`
calls its highest-value line.

Task: verify it still merges clean, run the full suite on the merged tree, merge it. **Merge, never
rebase.** If `docs/audits/README.md` conflicts, keep every row — there is no case where dropping one
is the right resolution.

---

## Dispatch 5 — The trust batch

Four small edits, one dispatch. None is a blocker; each makes a tester's answer wrong, which in a
beta whose entire output is tester answers is its own kind of expensive.

1. **First-run notification warning is false.** `MainActivity.kt:405-415` launches the
   POST_NOTIFICATIONS dialog and immediately calls `startRecording`, which reads
   `alertAudibility.current()` at `TrackRecordingViewModel.kt:172` while the dialog is still up.
   `AndroidAlertAudibility.kt:41` reports false pre-grant. So every Android 13+ tester's first trip
   opens with "an off-track alert won't show on screen" even when they grant it. Verified by reading;
   not device-verified.
2. **No way to delete a track.** `deleteTrackUseCase` is constructed at `AppContainer.kt:243` and
   called nowhere; `RecordsTab.kt` has deletes for waypoints (`:79`) and offline regions (`:91`) but
   not tracks. Every test walk and every accidental start accumulates permanently.
3. **`CAMERA` is declared and not needed.** `AndroidManifest.xml:10`. Capture goes through
   `ActivityResultContracts.TakePicture` (`PhotoAcquisitionLaunchers.kt:79`), which delegates to a
   camera app. Declaring the permission makes the platform *require* the grant before the capture
   intent will run, so the manifest manufactures its own prompt at `:116`. Removing both lines
   removes a permission and a prompt — verify that claim on a device or state it as unverified.
4. **Imperial doesn't reach elevation or soil temperature.** `AvailabilityScreen.kt:4332-4334` always
   prints metres; `AvailabilityResultsUi.kt:585` always °C. Acknowledged at `UnitSystem.kt:16-19`.

---

## Two standing hazards for whoever picks these up

**`docs/audits/README.md` is a serialization point.** Three dispatches ran in parallel on 2026-09-09
and were deliberately forbidden from touching it; the last one added all four rows in a single
commit. Do the same, or sequence the dispatches that touch it. This is the second time this index has
caused a parallel-session collision.

**`JournalTabTest`'s photo-pull assertion is a real flake and is still unexplained.** It failed once
in ~8 full-suite runs on 2026-09-09 across two independent sessions and two different trees, and
passed on isolated re-run every time. One session initially attributed it to its own new test and
measured 3/3 green on baseline; a later session then reproduced it on a tree with no test changes at
all, which withdraws that explanation. Treat it as unexplained, do not dismiss it as known, and do
not silence it — diagnosing it is legitimate work, deciding to reduce coverage is the owner's.
