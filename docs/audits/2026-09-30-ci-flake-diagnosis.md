# CI flake diagnosis on `journal-redesign` (dispatch 2026-09-28-296): partial, stopped at a permission refusal

**Status: stopped before reproduction.** The CI data is collected and the mechanism is narrowed to one
candidate with strong circumstantial support, but it is **not confirmed**: no local run was made, and the
probe that would confirm it was refused by the permission system (section 6). Per the dispatch ("If the
permission system refuses a command, stop and hand back"), this report records what was done and hands
back. Nothing here is a fix, and no shared code or test was changed.

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
main-thread gesture. That is inferred, not measured.

## 4. Reproduction: none run

**No local run was made.** At the first check (before any Gradle invocation), available memory was
**2008 MB** and free disk **1962 MB**, both under the dispatch's 2048 MB floor, and another dispatch's
Gradle test executor was running (a daemon in `forager-wt/delete-siblings`, -297). At the second check
memory was 2790 MB but disk had fallen to **1772 MB**. The floor was never met, so no build was started.

**Sizing, for whoever runs it** (exact binomial, not `9/p`): at CI's per-run album rate, 67/119 = 0.563,
the number of class-alone runs needed to see at least one failure is ⌈ln(1−c)/ln(1−p)⌉ = **3 / 4 / 6** for
c = 0.90 / 0.95 / 0.99. At the interval's lower bound, 0.473, it is 4 / 5 / 8. A clean result at those
sizes shows the local rate is well below CI's, which is itself a finding, not a null. **Local rates were
not counted**: the dispatch's "0-failure local suites (3173/3177/3178)" were not re-derived here.

## 5. Mechanism: candidate, not confirmed

**Candidate: the album tile's gesture node is replaced mid-gesture by `DecodedPhoto`'s placeholder-to-image
swap.** A real touch starts on the placeholder `Box`'s `combinedClickable`. The IO decode finishes, and the
recomposition that publishes the bitmap replaces the `Box` with an `Image`. The pointer-input node that saw
the `down` is detached, so the new node never saw a `down`: the tap's `up`, or the long-press timeout,
produces nothing. The window is the real time the injected gesture takes, and a long-press is the widest
(it advances the clock past the long-press timeout, running frames that apply the swap).

| Part | Status |
|---|---|
| The swap happens in every album test, once per tile, off the test clock | **Observed in code** (`DecodedPhoto.kt:57-81`) plus `DecodedPhotoTest.kt:36-43`'s recorded probe that the shadow decode always returns a bitmap |
| The branch switch replaces the node that carries `combinedClickable` | **Observed in code** (`EntriesAlbum.kt:256-259` passes the gesture modifier into `DecodedPhoto`, which puts it on either branch) |
| All 82 album failures are "a real touch had no effect", and the only no-touch album test has 0 failures in 77 | **Observed** (CI data, section 1) |
| Detaching a `combinedClickable` mid-gesture drops the click or long-press | **Inferred** from how Compose's hit path works; not demonstrated here |
| The swap lands inside the gesture on CI more often than locally because of runner speed | **Inferred**; nothing measured |
| The 36 "exists but is not displayed" failures fit the same mechanism | **Not determined.** A missing menu (23) and a missing viewer (23) fit directly. A menu item that exists but is not displayed needs a second step (e.g. the swap landing after the long-press fired but while the popup is positioned against the replaced node) that was not checked |

**The confirming probe that was refused** (section 6): a scratch-only hook in `DecodedPhoto` (a latch the
decode waits on, and one it counts down when done), with probe tests that place the swap **between `down`
and `up`**, **before `down`**, and **after `up`**. The prediction was that the first fails deterministically
with the CI message and the other two pass deterministically. The same hook would test the `JournalTabTest`
case (close the gate before the picker tile is tapped, and see whether `:last` fails with CI's exact
"exists … is not displayed" message).

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

## 7. Proposed fix, conditional on confirmation

**Keep the gesture on a node that does not change when the photo arrives.** In `AlbumPhotoTile`, put
`tileClickable` on a wrapping `Box(Modifier.fillMaxSize().tileClickable(…))` and give `DecodedPhoto` only
`Modifier.fillMaxSize()`, so the placeholder-to-image swap happens *inside* the gesture node instead of
replacing it. Alternatively, restructure `DecodedPhoto` itself to one outer node whose child switches.
That would cover every call site but moves `DecodedPhotoTest`'s size expectations, so it is the owner's
choice.

- **Why it treats the cause, not the symptom:** the race is between a user's touch and a decode. On a real
  device, a long-press or tap started on an album tile before its thumbnail finishes decoding would
  plausibly be dropped too, a production bug the test is catching by accident (**inferred, not seen on a
  device**). A test-side wait for the decode before each gesture would make CI green and leave that bug
  in place.
- **What would show it working:** the refused probe's "swap between `down` and `up`" arm turning from
  deterministic fail to deterministic pass with the fix applied, and then the CI album rate falling from
  67/119. To distinguish that from luck, around 6 consecutive green runs are needed (at p = 0.563,
  0.437⁶ ≈ 0.7% chance of 6 greens by luck).

## 8. Does `JournalTabTest` From Album share the cause?

**Same component, and probably not the same step.** That test asserts, as its last line, that a *newly
composed* `DecodedPhoto` on the edit form shows its `"Log photo"` image, with no wait. The `waitUntil`
before it covers only the picker's decode. So it reads the same off-clock IO decode, but as an unwaited
load, with no gesture involved. The 2026-09-10 archive read
(`2026-09-10-journaltabtest-flake-origin-archive-read.md:105-178`) already named `DecodedPhoto`'s
`Dispatchers.IO` decode as its candidate, and this repeats that rather than finding it.
`2026-09-15-journaltabtest-from-album-intermittent-failure.md` states it is "not an unwaited load"
because the node *exists but is not displayed*. That is consistent with the picker's decoded `Image` still
being in the tree off screen while the edit form's decode is pending, a reading that report's check (of
the picker load) would not have covered. **That is inferred and was not checked.** This sample's 5
messages are byte-identical to that report's.

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

**Confirmed (read or counted):** the CI counts and rates in section 1, and their sample; the source line
and JUnit message of every album failure; the 0-in-77 for the no-touch album test; `DecodedPhoto`'s IO
decode and branch swap; the gesture modifier passed into it; the `ci.yml` and build settings in section 3.

**Inferred, not confirmed:** that the swap detaching `combinedClickable` is what drops the gestures; that
runner speed sets the rate; that the fix in section 7 removes it; that a real device shows the same
dropped touch; that `JournalTabTest` fails through the unwaited edit-form decode.

**Could not determine:** what kept Compose from going idle in the -297 stall (section 8a; the dump was not kept); any local rate (nothing run); how the 36 "exists but not displayed" menu failures
arise; CI's locale, time zone and vCPU count from the logs; whether the `WideJournalTest` "FAILS AT BASE"
reds were deliberate.

**Premises that were wrong or imprecise:**
- The dispatch's "last 13 finished runs … 7 succeeded, 6 failed, 2 were cancelled" counts cancelled runs as
  finished. The 6 failures it lists are correct.
- The failures are not in the Undo snackbar path: the Undo test fails at its first long-press (`:1138`).
- "Both involve album photos": the common factor is `DecodedPhoto`. The `JournalTabTest` case is the find
  form's photo picker, not the album.
- "It fails more on CI than locally" was left unverified, as the dispatch said.

**Decided beyond scope:** nothing changed in shared code or tests. No CI run was started or re-run. `gh` was
used read-only (`run list`, `api …/runs`, `…/jobs`, `…/logs`, `…/artifacts`, artifact zip download). The
non-album failures in section 1 were listed, not investigated.
