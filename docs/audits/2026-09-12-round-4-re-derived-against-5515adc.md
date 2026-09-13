# Round 4 re-derived against current main, and a staleness rule

**Date:** 2026-09-12
**Base: `5515adc`** (`origin/main`), verified against the remote before writing a word of this.
**Type:** re-derivation. **Supersedes** `2026-09-11-lifecycle-gate-and-corrections-round-4.md` §3a.
No app code changed.

Round 4 was written against `4957675`. This session's `origin/main` was **two merges stale** and
nothing in the session said so. Re-derived rather than amended, on the owner's instruction.

---

## 1. What the stale base hid

```
$ git log --oneline -1 origin/main      # before fetch
4957675 Merge the legal source edits into main
$ git fetch origin main
   4957675..5515adc  main -> origin/main
$ git log --oneline -1 origin/main      # after
5515adc Merge PR #96: resync recording state against the track's row, at both lifecycle edges
9f5f4d6 Merge PR #95: sundown countdown Phase 1, the off-track silence reversal, and the audits
```

Two merges. Both land squarely on what round 4 reported.

## 2. Retracted: "`CivilTwilight` has zero production callers"

**True at `4957675`. False at `5515adc`.**

```
$ git grep -l "CivilTwilight" origin/main -- 'app/src/main/**'
app/src/main/java/com/zynergylabs/forager/app/domain/CivilTwilight.kt
app/src/main/java/com/zynergylabs/forager/app/domain/ComputeSundownCountdownUseCase.kt
app/src/main/java/com/zynergylabs/forager/app/domain/SunCrossing.kt
```

Two production callers. `SunCrossing` computes sunset by bisecting the solar-altitude function
`CivilTwilight` already provided, which is the wiring round 4 said was missing.

**And every file in the handover already exists on main:**

| File | On `5515adc` |
|---|---|
| `domain/SundownPreferencesRepository.kt` | PRESENT |
| `data/repository/DataStoreSundownPreferencesRepository.kt` | PRESENT |
| `domain/SunCrossing.kt` | PRESENT |
| `domain/ComputeSundownCountdownUseCase.kt` | PRESENT |
| `domain/DecideSundownAlertUseCase.kt` | PRESENT |

So the handover's advice — "wire to `CivilTwilight` rather than rebuild it" — would have produced a
**second** `SundownPreferencesRepository` beside the one already merged. The advice was aimed at
preventing duplicate work and was itself the instruction to duplicate it. Withdrawn in full.

The round-4 *category* still stands, and #95 is its proof rather than its refutation: `CivilTwilight`
was genuinely built-and-dead for some window, and Phase 1 is what wired it. The error is the tense.
"Has no caller" was reported as a property of the codebase when it was a property of a base commit.

## 3. Survives, and one of them survives harder than round 4 knew

The three mechanism observations hold on `5515adc`, re-checked rather than assumed.

**3a. The DataStore delegate caches per process.** Unchanged, and `DataStoreSundownPreferencesRepository`
on main already follows the `PreferenceDataStoreFactory.create` pattern.

**3b. `beginLocationTracking()` has one call site — and #96 did not change that.** This is the one to
read closely, because #96's title makes it look handled.

Re-derived on `5515adc`:

```
$ git grep -n "beginLocationTracking()" origin/main -- 'app/src/main/**'
TrackRecordingViewModel.kt:213      # inside startRecording's success path
TrackRecordingViewModel.kt:504      # the declaration
```

`init` is still `{ loadWaypoints(); loadTracks() }` (line 178-181). `activeTrack` is assigned in
exactly two places: set at line 202 inside `startRecording`, cleared to `null` at line 264.

And `resyncRecordingState` (line 380) opens:

```kotlin
private suspend fun resyncRecordingState() {
    val active = uiState.value.activeTrack ?: return
```

with its own doc saying so: "Cheap, and a no-op when nothing is recording: it returns before touching
storage unless `[TrackRecordingUiState.activeTrack]` is set."

**So #96 closes the stale-active direction and not the lost-active one.** The ViewModel believing a
finished recording is live gets corrected at both lifecycle edges. The row saying a recording is live
while the ViewModel has no `activeTrack` — the state after a process kill — is not reached, because
the early return fires first and nothing re-adopts the track.

This is **not** a criticism of #96, whose dispatch was scoped to the mirrored problem and solves it
with care; its `onLeftForeground` doc even reasons explicitly about the pocketed phone and about why
the `AvailabilityViewModel` foreground gate would be a regression here. The point is narrower: a
reader who sees "resync recording state at both lifecycle edges" would reasonably assume the gap is
covered, and it is not.

**#95 raised the stakes.** The sundown alert rides the same surface, so after a process kill
mid-recording the service keeps persisting points while the off-track alert, the return HUD, and now
the sundown countdown are all silently dead for the rest of that recording. Still traced, still not
device-confirmed.

**3c. The `runTest` unbounded-delay-loop hazard.** The most load-bearing of the three, because #95
added a poll loop. `runRecordingTest`'s shape — every recording stopped inside the body, in a
`finally` — is what keeps a thrown assertion from leaving a `delay` loop scheduled for `runTest`'s
closing idle-advance to spin on. CLAUDE.md records that not firing in 77 minutes of real time.

## 4. The rule this is the third instance of

The owner names two siblings from today: "main has no Functions" and "EXPORT_TOKEN still unset."
With this one that is three, none careless, all describing a world that moved while the session was
not looking.

**Adopted as a standing line: state the base SHA, and verify it against the remote, before reporting
what does and does not exist.**

One refinement worth carrying with it, because it says where to spend the check:

**Absence claims decay; existence claims do not.** "X exists at line N" stays true as the world moves
forward — at worst it gains a line number. "X has no caller", "the file is absent", "nothing
constructs Y" are true only of a snapshot, and they invert silently, with no diff to notice and
nothing in the wording marking the difference. Round 4's §3a was three absence claims in a row. So
the base-SHA check is cheap insurance on any report, and **mandatory** on one whose findings are
phrased as absences.

That is the derived-figure family running along time instead of scope: a claim correct about the
thing it was derived from, quoted about something larger, with nothing in the quoting marking the
change. `git log --oneline -1 origin/main` after a `git fetch` costs one command, which is the same
price as the `git grep` that CLAUDE.md's reachability rule already charges.

## 5. Disclosure

**Verified against `5515adc` this session:** the fetch transcript in §1; the three `CivilTwilight`
callers; all five sundown files present; `beginLocationTracking`'s two line numbers; `init`'s two
calls; `activeTrack`'s two assignment sites; `resyncRecordingState`'s early return and its doc
comment.

**Not verified:** #95's test-count claim (1345 → 1346) and its full file list, taken from the owner;
no build was run. §3b's process-death behaviour remains traced from source, never observed on a
device.

**Merge note:** bringing `5515adc` into this branch conflicted in `docs/audits/README.md`, which is
the serialization point CLAUDE.md names. Resolved by merge, never rebase, **keeping every row** — 19
from main, 6 from this branch, 25 kept, none dropped.
