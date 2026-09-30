# CI flake diagnosis on `journal-redesign` (dispatch 2026-09-28-296, continued by -300 and -301)

**Status: the album-photo failures are diagnosed and confirmed.** `DecodedPhoto` swaps its placeholder for
the decoded image on a real IO thread, off the test clock. The album tile's tap and long-press sit on the
node that swap replaces, so a gesture in flight when it lands is lost. That was confirmed by a probe that
places the swap deterministically (it fails 6/6 when the swap is inside the gesture and passes 6/6 when it
is not). It was reproduced locally by starving the CPU (4 failures in 18 pinned runs, 0 in 12 unpinned).
A throwaway fix, the gesture on a stable wrapper, turned the failing probe arms green (3/3) and gave **0
failures in 18** pinned runs. The `JournalTabTest` From Album failure is the same off-clock decode read
without a wait, confirmed by the same probe. The fix is proposed, not applied. Everything here is on
`ci-flake`, and nothing leaves it.

The first session stopped at a permission refusal (section 6, kept as written). The owner then allowed the
scratch hook (`RECORD.md` -300, "Allow the temp hook") and a new scratch test file (-301, "Option 1").

- **Base:** `origin/journal-redesign` at `4f3a02ad` (head when the worktree was cut). `9c806ae1..4f3a02ad`
  changes only `RECORD.md` and `prompts/preserved/2026-09-30-13..15.md` (`git diff --stat`), so the code
  under diagnosis is `9c806ae1`'s. The branch head has since moved on (`d610d90f` at the time of writing);
  nothing here was re-checked against it.
- **Worktree / branch:** `~/Zynergy/forager-wt/ci-flake`, branch `ci-flake`. The raw data is under
  `docs/audits/data/2026-09-30-ci-flake/`.

## 1. CI data

**Sample.** `gh api …/actions/workflows/ci.yml/runs?branch=journal-redesign` (paginated) returned **480
runs**, all `pull_request`: 359 `cancelled`, 1 still running (`36762098567`, `4f3a02ad`), **120 finished**
(41 success, 79 failure). Of the 79 failures, 78 failed in `Run the unit tests`, and 1 (`36627241206`,
`dcc0275c`) failed earlier, in `Install the Android SDK…`, so it ran no tests. **The denominator is
therefore 119 runs that reached the unit tests.** All 120 finished jobs ran on GitHub-hosted `ubuntu-24.04`
(runner group "GitHub Actions"). Every one of the 120 heads contains the six album-photo tests (`git show
<head>:…JournalPendingDeleteTest.kt`, counted per head).

- **Logs:** all 79 failure-job logs were readable, and none were excluded. Every failing test in them was
  mapped to its source line **at that run's own head**, not at today's file (`ci-failures-mapped.tsv`,
  131 rows, 0 unmapped).
- **Artifacts:** the `unit-test-report` artifact (JUnit XML, full messages) was readable for **77 of the
  78** test-failure runs. The one missing is `36627241206`, the SDK-setup failure, which uploaded none
  (`artifact-status.txt`). The messages are in `ci-failures-junit-messages.tsv`.
- **Success runs** were not downloaded. A success conclusion means all tests passed or were allowlisted.

**Rates, per run (119 runs), with the Wilson 95% interval computed here, not carried from anywhere:**

| Failure | Runs | Rate | Wilson 95% |
|---|---:|---:|---|
| ≥1 album-photo test in `JournalPendingDeleteTest` | 67 | 56.3% | 47.3%–64.9% |
| `JournalTabTest` › From Album on the edit form… | 5 | 4.2% | 1.8%–9.5% |
| Either of the two | 70 | 58.8% | 49.8%–67.3% |
| Runs whose *only* failures are those two | 64 | 53.8% | 44.8%–62.5% |

**Per test (failures across the 119 runs):**

| Test (`JournalPendingDeleteTest`) | Failures | Gesture used |
|---|---:|---|
| Undo on an album photo brings it back and deletes neither row nor file | 25 | real long-press |
| a plain tap on an album photo still opens the viewer | 23 | real tap |
| a long-press anywhere on an album photo opens a menu of exactly Delete | 14 | 6 real long-presses |
| an album photo's Delete hides it and warns of its uses… | 12 | real long-press |
| an album photo has no corner delete control, and a long-press at that corner… | 9 | real long-press |
| **an album photo's Delete accessibility action and long-press label, and no Edit action** | **0** | **semantic action, no touch** |

The one album test that deletes through a semantics action, with no real touch, **has never failed** in
77 readable runs. Every album test that injects a real touch has failed at least 9 times. In the
same 77 artifacts, the failing test's position among the album tests is spread across ranks 0, 2, 3, 4
and 5 (14/9/24/12/23), so it is **not** "the first test to decode a photo in the JVM".

**What every album failure says (JUnit message, 82 album failures in 77 artifacts):**

| Message | Count | Source line (at `9c806ae1`) |
|---|---:|---|
| `The component with TestTag = 'tile-options-delete' is not displayed!` | 36 | `touchMenuItem`, `JournalPendingDeleteTest.kt:1138` |
| `Expected exactly '1' node but could not find any node that satisfies: ((OnClick is defined) && (hasAnyAncestorThat(IsPopup…` | 23 | `menuItems().assertCountEquals(1)`, `:1268`/`:1354` |
| `Expected exactly '1' node but could not find any node that satisfies: (TestTag = 'photo-viewer')` | 23 | `:1369` |
| `JournalTabTest`: `The component with ContentDescription = 'Log photo' … is not displayed!` | 5 | the test's last line (`:472` / `:495` at those heads) |

So every album failure is **a real touch on an album tile that did not produce its gesture's effect**:
either the long-press opened no menu (23), or the menu item was not displayed (36), or the tap opened no
viewer (23). None is a snackbar, Undo or delete-count assertion. The Undo test fails at `:1138`, its
first long-press, **before** Undo is ever touched. The dispatch's framing ("the Undo snackbar") is
therefore not where it fails.

**Other failures in the sample**, not album-related and not investigated here: `CompactSearchBarLocationControls*`
(3 consecutive runs, 09-28 13:35–14:06), a 25-test `…MapLayers*`/`Diagnostics…` burst in one run (`45c82cdc`),
`LeavingTheJournalFixesTest` F3 (3), `WideJournalTest` "FAILS AT BASE …" (4; test-first tests by name,
which **may** be deliberate reds, not checked), `ScheduledBackupTest` (1), `JournalEntriesOnMapFollowUpsTest`
(1). Their clustering on consecutive heads looks like ordinary red-then-fixed commits, but that was **not
verified**. Of these, `WideJournalTest`'s album long-press test (3) carries the same symptom text ("a
long-press … opens a Delete menu"), and is noted as possibly the same cause, not counted as it.

## 2. The code (read at `4f3a02ad`)

- **Album tile:** `EntriesAlbum.kt:246-268`. The tile's click and long-press, `tileClickable` →
  `combinedClickable` (`TileOptionsMenu.kt:43-49`), are put on **`DecodedPhoto`'s own modifier**
  (`EntriesAlbum.kt:256-259`), inside `LongPressOptionsBox` (`TileOptionsMenu.kt:61-`), which anchors the menu.
- **`DecodedPhoto`:** `DecodedPhoto.kt:50-82`. `remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }`,
  then a `LaunchedEffect` that decodes on **`Dispatchers.IO`** (`:57-74`). The result is **two different
  layout nodes carrying the same modifier**: a placeholder `Box(modifier…)` while `bitmap == null`
  (`:80`), and an `Image(…, modifier = modifier)` once it is set (`:78`). The branch switch replaces the
  node, so the `combinedClickable` pointer-input node is detached and a new one attached.
- **Under Robolectric the decode always "succeeds":** `DecodedPhotoTest.kt:36-43` records, from a probe,
  that the SDK 36 `BitmapFactory` shadow "fakes a successful 100x100 decode for *any* file path,
  including a missing one". So in `JournalPendingDeleteTest`, whose photos point at non-existent
  `photos/none-a.jpg`/`none-b.jpg` (`:1604-1611`), **every album tile swaps from `Box` to `Image` once,
  at a moment set by an IO thread's real time**, which the Compose test clock does not control, and
  `waitForIdle` does not wait for it.
- **The assertions:** `:1138` `onNodeWithTag(TILE_OPTIONS_DELETE_TAG).assertIsDisplayed()` (the menu
  opened by the long-press); `:1268`/`:1354` `menuItems().assertCountEquals(1)` (one clickable node inside
  a popup); `:1369` `onNodeWithTag(PHOTO_VIEWER_TAG).assertExists()` (the tap's `onOpen` set
  `viewingPhotoId`, `EntriesAlbum.kt:159,178-181`).
- **The one test that never fails** (`:1318-1334`) fetches the semantics node and calls its custom
  action on the UI thread. It never goes through pointer input.

## 3. `ci.yml` versus a local run

| Setting | CI (`.github/workflows/ci.yml`, run logs) | Local (this machine) |
|---|---|---|
| JDK | Temurin `21.0.12+1` (`setup-java`, log) | Temurin `21.0.12.1` (`~/.local/jdk/temurin-21`) |
| Command | `./gradlew --stacktrace assembleDebug`, then `./gradlew --stacktrace testDebugUnitTest` | same task, not run here |
| Gradle heap | `org.gradle.jvmargs=-Xmx2048m` (`gradle.properties`) | same file |
| Test forks / `maxParallelForks` / `forkEvery` / test heap | none set (`app/build.gradle.kts:311-319, 584-591`), so Gradle defaults: one fork, no fork-every, default test-JVM heap | same |
| Retries | none (no retry plugin, no `retry` block) | same |
| Skips | `@Ignore` only; the workflow's `SKIPPED_TESTS_ALLOWLIST` is a check on skips, and skips nothing itself | same |
| Robolectric | 4.16.1, no `robolectric.properties`, no `@GraphicsMode` on these classes | same |
| CPU | GitHub-hosted `ubuntu-24.04` on a public repo, documented as a 4-vCPU standard runner (**not read from the log**) | 8 logical, i7-10510U |
| Locale / TZ | not printed in the log; runner defaults assumed UTC (**unverified**) | `en_US.UTF-8`, PDT |
| Test order | Gradle class-scan order, not recorded on either side here | – |

Nothing in the configuration differs in a way that selects these tests. The only difference that bears on the
candidate mechanism is **machine speed and load**, which sets when an IO thread finishes relative to the
main-thread gesture. Locally that was measured (section 4a: 0/6 unpinned, 4/18 pinned to 1–2 CPUs). CI's own runner speed was not.

## 4. Reproduction

All runs are local, on this machine (8 logical CPUs, Temurin 21.0.12.1), through `/tmp/cif/loop.sh`. Before
each iteration it waits for 2048 MB of memory and disk and for no other Gradle wrapper or worker. It deletes
the previous results, runs, refuses the result if the log shows a compile error, and copies that iteration's
XML aside. Every run cited below had **0 compile errors**. Every iteration's XML carries its own fresh
`timestamp` (checked, none repeated) with no `UP-TO-DATE` test task, so no count below is a stale run. The
tallies are in `docs/audits/data/2026-09-30-ci-flake/<label>/tally.tsv`.

**Sizing** (exact binomial, ⌈ln(1−c)/ln(1−p)⌉, not `9/p`): at CI's per-run rate 0.563, 6 runs give a 99%
chance of at least one failure if the local rate matched CI's.

### 4a. `JournalPendingDeleteTest` alone (52 tests), base code plus the inert hook

| Condition | Label | Runs | Failures | Which |
|---|---|---:|---:|---|
| Unpinned (8 CPUs) | `jpdt-alone` | 6 | **0** | – |
| Pinned to 2 CPUs (`taskset -c 0,1`, `--no-daemon` so the worker inherits the pin) | `jpdt-2cpu`, `jpdt-2cpu-b` | 12 | **2** | long-press anywhere ×2, at `:1268`, CI's message |
| Pinned to 1 CPU (`taskset -c 0`) | `jpdt-1cpu` | 6 | **2** | long-press anywhere (`:1268`), Undo (`tile-options-delete … is not displayed`), both CI's messages |

- **Unpinned 0/6:** if the local rate were CI's 0.563, six clean runs would happen by chance 0.437⁶ ≈ 0.7% of
  the time. So the local rate is far below CI's. That is now measured, not inherited from the dispatch.
- **Pinned 4/18 = 22%** (Wilson 95% 9.0%–45.2%). Starving the CPU turns it on. Every pinned failure is one
  of CI's own assertions with CI's own message.
- `DrawerBackOverJournalTest` alone, unpinned: **0 in 6** (`drawer-alone`). See section 8a.

### 4b. The deterministic probe (scratch, `ci-flake` only)

`DecodedPhoto.kt` carries a scratch `DecodeProbe` (`d63e103a`): a latch each decode waits on, and a latch
it counts down when done, both `null` (inert) unless a probe sets them. The probes are in
`app/src/test/…/ui/log/probe/` (`AlbumGestureSwapProbeTest`, `JournalTabFromAlbumProbeTest`): copies of the two
classes' harnesses holding only probe tests. No existing test file is changed. (The first build of them
failed to compile, with 157 `Redeclaration` errors: private top-level fakes clash across files in one
package. No result was read from it. They were moved to the `probe` subpackage.) Each album arm first
asserts that tile A shows the placeholder, then that the swap happened, which checks reachability before
the measurement. The swap was applied in 1 frame every time.

| Arm | Predicted (written before any run) | Base: 6 runs | With the fix: 3 runs |
|---|---|---|---|
| Tap, swap between `down` and `up` | fail, CI's viewer message | **fail 6/6**, `…could not find any node… (TestTag = 'photo-viewer')` | **pass 3/3** |
| Tap, swap before `down` | pass | pass 6/6 | pass 3/3 |
| Tap, swap after `up` | pass | pass 6/6 | pass 3/3 |
| Long-press, swap between `down` and the timeout | fail, CI's menu-count message | **fail 6/6**, `Failed to assert count of nodes … IsPopup` | **pass 3/3** |
| Long-press, swap after it fired, before `up` | no prediction | pass 6/6 | pass 3/3 |
| Long-press, swap before `down` | pass | pass 6/6 | pass 3/3 |
| `JournalTabTest` From Album, the edit form's decode held at the last assertion | fail, CI's exact message | **fail 6/6**, `'Log photo' … is not displayed!`, with **0** `Log photo` nodes | fail 3/3 (the fix does not touch it, section 8) |

The failure messages were byte-identical across the 5 loop runs (md5 of the failure lines). With the fix,
the same runs also ran the whole `JournalPendingDeleteTest`: **52/52 pass** in each of the 3 runs.

### 4c. The fix under CPU starvation

The throwaway fix (`3f71086c`, re-applied as `d4558d14`, reverted in `dc5ef5c5` and `6bf115e0`), run as in 4a:

| Condition | Runs | Failures |
|---|---:|---:|
| Pinned to 2 CPUs (`fix-2cpu`) | 12 | **0** |
| Pinned to 1 CPU (`fix-1cpu` 1-3, `fix-1cpu-b` 1-3) | 6 | **0** |

**Base 4/18 against fix 0/18**, under the same pins. At the base rate, 18 clean runs by chance is
(1 − 4/18)¹⁸ ≈ 1.1%. Fisher's exact two-sided p for 4/18 vs 0/18 is about 0.10: on its own this comparison
is suggestive, not conclusive. The deterministic probe in 4b carries the confirmation. **Contention:**
`fix-1cpu-b` runs 1 and 2 overlapped -310's Gradle runs (the planner's report, and a `pgrep` seen during
run 2). Run 3's overlap is unknown. Extra load widens the race, so those clean runs are not weaker evidence.
They are still marked (`fix-runs-contention.md`). A container restart lost `fix-1cpu` 4-6 before they
started. They were re-run as `fix-1cpu-b` and are not counted twice.

### 4d. Full suites, base plus the inert hook, probes excluded

**Pending**; filled in when the three runs finish (section 8a covers the stall watchdog).

## 5. Mechanism

**The album tile's gesture node is replaced mid-gesture by `DecodedPhoto`'s placeholder-to-image swap.** A
real touch starts on the placeholder `Box`'s `combinedClickable`. The IO decode finishes, and the
recomposition that publishes the bitmap replaces the `Box` with an `Image` (`DecodedPhoto.kt:76-81`). The
pointer-input node that saw the `down` is detached, and the new node never saw one, so the tap's `up`, or
the long-press timeout, does nothing. Under Robolectric the decode always "succeeds", even for a missing
file (`DecodedPhotoTest.kt:36-43`), so the swap happens in every album test, at a moment set by an IO thread
and not by the test clock.

| Part | Status |
|---|---|
| The swap happens off the test clock, once per tile | **Observed** in code, and in the probe (`PROBE swap applied after 1 frame(s)`) |
| The gesture modifier rides on the swapped node | **Observed** in code (`EntriesAlbum.kt:256-259`) |
| A swap inside a tap or before the long-press timeout loses the gesture, and outside it does not | **Confirmed**: probe 6/6 each way (4b) |
| Moving the gesture to a stable wrapper removes it | **Confirmed** on the probe (3/3), supported by 0/18 against 4/18 pinned (4c) |
| Machine speed sets the rate | **Observed** locally: 0/6 unpinned, 4/18 pinned. CI's runner speed is not measured |
| All 82 CI album failures are this one outcome | **Observed**: the 36 `tile-options-delete … is not displayed` failures come only from the two tests that call `touchMenuItem`'s `assertIsDisplayed` straight after the long-press. In this Compose version that message is what a **missing** node gives (the `JournalTabTest` probe shows it with 0 nodes). The 23 count failures come only from the two tests that check `menuItems()` first, and the 23 viewer failures from the tap test |
| A swap after the long-press fired is harmless | **Observed**: that arm passes 6/6 |

## 6. Why this stopped

Adding that hook meant editing `app/src/main/…/DecodedPhoto.kt` on `ci-flake`, which the dispatch allows
("Instrumentation, diagnostic tests and scratch probes go on that branch"). The permission classifier
refused two of the three edits as "Modify Shared Resources". The third, an inert `DecodeProbe` object, had
already landed. It was removed with `git checkout -- DecodedPhoto.kt`, safe here because the file carried
no other change of mine, and `git status` was then clean. The dispatch says a refusal is a stop, so no
workaround was attempted.

**What resuming needs, for the planner and owner to choose:**
1. **Allow the scratch hook in `main/` on `ci-flake`** (never merged). This is the deterministic probe
   above, and the strongest confirmation available.
2. **Or a test-only probe, which weakens the confirmation.** In a scratch copy of the album tap test:
   record whether the tile's `"Log photo"` node exists just before `down`, `Thread.sleep` a few hundred ms
   with the pointer held, call `waitForIdle` (applying any pending swap), then `up`. The prediction is that
   the tap is lost exactly in the iterations where the placeholder was showing at `down`. No production
   file is touched, but the split between the two conditions is left to real time, so it needs repeats.
3. Either way, the class-alone and suite runs of section 4, when the machine is above the floor.

## 7. Proposed fix (for the owner; not applied)

**Keep the gesture on a node that does not change when the photo arrives.** In `AlbumPhotoTile`, wrap
`DecodedPhoto` in `Box(Modifier.fillMaxSize().tileClickable(onClick = onOpen, options = options,
onClickLabel = "Open full screen"))` and give `DecodedPhoto` only `Modifier.fillMaxSize()`, exactly as trial
commit `3f71086c` does. The alternative, making `DecodedPhoto` itself one stable outer node with a
switching child, covers every call site. It changes `DecodedPhotoTest`'s size expectations, though, and
was not tried: that choice belongs to the owner.

- **Why it treats the cause, not the symptom:** the race is between a touch and a decode, not between a
  test and a clock. A test-side wait for the decode before each gesture would turn CI green and leave the
  race in the app. On a device, a tap or long-press started on a tile whose thumbnail is still decoding
  would plausibly be dropped the same way (**inferred, not seen on a device**; a real photo's decode at
  `inSampleSize` 4 takes real time, which is when the window is open).
- **What would show it working:** the "between" probe arms turning from 6/6 fail to pass (done, 3/3); the
  pinned rate falling (done, 0/18 against 4/18); then on CI, album failures stopping. At CI's 0.563 per run,
  6 consecutive green runs have a 0.7% chance of happening by luck.
- **Other `DecodedPhoto` call sites with a gesture on the same modifier** were not audited. A `git grep` of
  `DecodedPhoto(` for a `clickable`/`tileClickable` modifier is the next step. Section 9 lists it as not done.

## 8. `JournalTabTest` From Album: same decode, different step. Confirmed

The test's last line asserts that a *newly composed* `DecodedPhoto` on the edit form shows its `Log photo`
image, with no wait. Its `waitUntil` covers only the picker's decode. The probe holds the edit form's decode
at that assertion. It fails **6/6 with CI's byte-identical message**, `The component with ContentDescription
= 'Log photo' (ignoreCase: false) is not displayed!`, and **0 `Log photo` nodes exist at that moment**.
So:
- **It shares the cause:** the same off-clock decode. It is **not** a lost gesture. It is an unwaited load,
  and the album fix does not touch it (the arm still fails with the fix, 3/3).
- **It overturns a prior reading.** `2026-09-15-journaltabtest-from-album-intermittent-failure.md` read the
  message as "the node exists and is not displayed", so "not an unwaited load". In this Compose version
  that message is also what a missing node gives. The 2026-09-10 archive read
  (`2026-09-10-journaltabtest-flake-origin-archive-read.md:105-178`) named this decode as its candidate, and
  this confirms it. The 09-15 report is superseded on this point, not edited.
- **Its fix is a separate owner decision:** wait for the decoded image in the test (this is a test of the
  pull, not of decode timing), or make the thumbnail's arrival observable. That was not tried.

## 8a. The -297 stall in `DrawerBackOverJournalTest` (added at the owner's request, "2 yes")

**Source:** `docs/audits/2026-09-30-delete-siblings-completion-report.md`, "The stall" (read on
`origin/journal-redesign`). One full-suite run on `delete-siblings` hung for about 17 min. A thread dump of
the test worker put the main thread in `DrawerBackOverJournalTest.touchTools` (`:420`), from the test
`portrait, Back with the drawer open over the Entries album view closes the drawer and keeps the album`
(`:538`), with about 88 s of main-thread CPU. The class alone passed 6/6 on `delete-siblings` and 6/6 on
`9c806ae1`. **The dump itself was not kept.** No file on this machine mentions `touchTools` or that class
in a thread dump (searched `/tmp`), so everything below rests on the report's one-line summary of it.

**Observed (read):**
- `:420` on `delete-siblings` (identical in `touchTools` at base) is `composeRule.waitForIdle()`, directly
  after a coordinate click on the **Tools nav item**, not on an album tile (`:414-422`). So the main
  thread was not stuck in a gesture. It was in a wait for Compose to go idle that did not end.
- 88 s of CPU in about 17 min of wall time means the main thread was mostly **not** running. That fits a
  wait loop that sleeps between idle checks better than a tight recomposition spin (inferred from the two
  figures, not from the dump).
- The test's data loads synchronously: Room with direct query and transaction executors and main-thread
  queries (`DrawerBackOverJournalTest.kt:202-209`). So the album's one infinite animation, the loading
  spinner at `EntriesAlbum.kt:124-126` (shown only while `isLoading && photos.isEmpty()`), has no slow load
  to wait behind here.
- The album's photo is a real file of 4 bytes (`:216-217`), so `DecodedPhoto`'s shadow decode "succeeds"
  and the tile swaps from placeholder to `Image` once, off the test clock, as in section 2.
- Within reach of that screen there are two unbounded tickers in `main/`: `NavigationHud.kt:192` (a
  one-second age ticker, composed only while the navigation HUD is showing) and
  `TrackRecordingViewModel.kt:446` (the poll loop CLAUDE.md already names as this repo's stall cause, and
  it runs only while recording). Whether either was active in that test was **not** checked.

**Does it share the album gesture-swap cause? Inferred: no, not as the same mechanism.** The swap fires once
per tile and replaces one node. That can drop a gesture in flight (a fast assertion failure, which is what
CI shows 82 times) but has no way to keep Compose busy for 17 minutes. The stalled wait also follows a
touch on the Tools item, which is not a tile. The one tie is timing: the swap, being off-clock, could land
inside that `waitForIdle`. A single extra recomposition there explains nothing about a stall, though.
**What would make it never idle is not determined.** Candidates, none checked: an animation that never
settles (the drawer's, or the album spinner if the album were somehow shown loading); a ticker left
running; or something outside Compose that the idle wait counts (an `IdlingResource`, or Robolectric's
looper-idle loop) never draining.

**How my runs will cover it** (once the planner's "go" arrives): `DrawerBackOverJournalTest` is inside the
full-suite runs of section 4. Each run gets a watchdog on **my own** test worker only: if the run's
`TEST-*.xml` stops growing for 5 minutes, it takes `jstack` of that worker (PID found by my own run's
command line) into `docs/audits/data/2026-09-30-ci-flake/`, then lets the run continue. A stall then leaves a
kept dump, which this one did not. Separately, the class alone will be run in a loop sized like section 4.
It stalled 1 time in 1 full run and 0 in 12 class-alone runs, so no rate can be derived yet, and that is
said rather than guessed.

## 9. Disclosures

PENDING
