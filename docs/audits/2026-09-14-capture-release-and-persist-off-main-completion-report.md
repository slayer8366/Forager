# Release a capture after persisting it, sweep orphans, persist off-main, stream the scrub: completion report

**Date:** 2026-09-14
**Request:** the owner's "if there are other things you can think of that will help in that area,
let me know", answered with two defects and one memory problem found by reading the photo path
after the multi-shot camera landed; then a pasted external review with two corrections and a
design note, all folded in; then go.
**Branch:** `claude/new-session-vto65i`, PR #102, open and not merged. `origin/main` at `175b050`;
branch from `4cbf632` to `459b930` for the code, docs following. No schema change.

---

## §1 — What was wrong, each traced to its caller before anything was built

**A capture's scratch file was never deleted after a successful persist.** `deleteCapture` had one
caller, `InAppCameraDialog.kt:193`, the failure branch. No sweep of `captures/` existed anywhere
(`grep CAPTURES_SUBDIR` finds only its own class). `CameraCaptureFiles.kt:17` said `MainActivity`
cleaned up afterward; `git log -S deleteCapture -- MainActivity.kt` is empty, so it never did. Every
photo taken lived twice on disk, forever. Pre-existing since the intent path was built; the
multi-shot camera turned one orphan per round trip into one per shot.

**`persist` ran on the main thread.** `viewModelScope.launch` with no dispatcher argument and no
scope parameter on the ViewModel, `AddPhotoToLogEntryUseCase` and `AddPhotoToGalleryUseCase` with
no switch, `FilePhotoStore.persist` with no switch, and `runCatchingCancellable` a plain try/catch
(`RunCatchingCancellable.kt:18`). A byte copy and a full JPEG rewrite per shot, on the UI thread.
**Inferred from reading every frame, not observed.** The reviewer's correction on that word is
accepted below.

**The scrub held the whole photo in an `ArrayList<Byte>`** (`PhotoMetadataScrub.kt:110`, before):
one object reference per byte, so 20 to 40 MB of heap for a 5 MB JPEG, per shot, on that same main
thread. Mine, from the previous dispatch.

## §2 — The fixes, and the two reviewer corrections folded in

**Release at the source** (reviewer's design note). `PhotoSource` gains `release()`, a no-op by
default, so the domain interface stays platform-free and the anonymous test fakes compile.
`CameraCapturePhotoSource` now carries the whole `CameraCaptureFiles.Capture` and deletes its own
file. `persist` calls `release()` in a `finally`, so the file goes whether the copy succeeded or
not; an import is safe by type, not by a branch. `File.delete()` on a file this app already knows,
rather than `ContentResolver.delete` on the URI — which sidesteps, rather than tests, the part of
the FileProvider path the reviewer expected to surface anything odd.

**Correction 1, folded in and named as what it was.** I wrote that the sweep "runs off-main" as
though `Application.onCreate` did. It runs on the main thread. That is the same class of error I
had conceded one paragraph earlier about the persist chain: a claim about where something runs,
stated as fact, from the shape of the code rather than from checking. The sweep is dispatched to
an explicit application scope on `Dispatchers.IO` (`ForagerApplication.kt`), and the doc on it
records the correction.

**Correction 2, verified rather than assumed.** A thresholdless sweep is wrong under an external
camera intent, where the platform kills this process while the camera app runs, recreates it when
the result comes back, and runs `onCreate` before the result is delivered. `grep TakePicture\|
ACTION_IMAGE_CAPTURE` over `app/src` finds comments and CameraX's own `ImageCapture.takePicture`
only; no code path launches an external camera. So the premise holds for this tree, and the sweep
deletes only files **older than the process** anyway — which is the definition of an orphan, keeps
the sweep correct if the intent path ever returns, and turned out to be load-bearing in the suite
(§3). A 2 s guard below process start covers coarse-mtime filesystems; internal storage is not one,
and an orphan is never that young.

**Main-safe `persist`.** `withContext(Dispatchers.IO)` inside `persist`. The reviewer's reason —
"repositories are main-safe; callers should not have to know" — is general Android practice, and
**it is not this repo's convention**: none of the 18 files in `data/repository/` switch
dispatchers, and the only IO switches in `main/` are four UI callers (`PhotoViewerDialog`,
`DecodedPhoto`, `CrashLogPanel`, `TrackExportPanel`). The Room-backed repositories are main-safe
because Room switches for them; the file stores with UI callers rely on the caller. `persist` was
the one file path whose callers never did. The fix location still stands, on a corrected
justification: match the main-safety Room already gives the others, since this store has no Room
under it. Recorded on the class and in its own index row.

**Streamed scrub.** A marker walk from input file to temp file through one 8 KiB buffer; the ten
existing scrub tests pass unchanged, and a revert against the streaming code fails five of them on
their own messages. The `ExifInterface.saveAttributes` reapply is a second full pass, as the
reviewer noted. A single pass by emitting a minimal APP1 during the walk was considered and **not
done**: these photos are meant to be shared outside the app, and `ExifInterface`'s writer is the
emitter other readers have been tested against. Off-main, two passes is cheap. Recorded on the
function as the option it is.

## §3 — Robolectric instantiates `ForagerApplication` for every test

The manifest names `.ForagerApplication`, so the startup sweep launches beside every test method
in the suite. A sweep test on the real `captures/` would race it: a file backdated to look like an
orphan could be deleted by the application's sweep before the test's own call. `CameraCaptureFilesTest`
runs its sweep cases on a `ContextWrapper` whose `getFilesDir` points where the application never
looks, removing the race by construction. Every other test that creates a capture is protected by
the age check: a file written during a test is younger than the process and is kept.

---

## Evidence

### Suite counts, from JUnit XML

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`4cbf632`) | 179 | 1442 | 0 | 0 | 24 |
| after (`459b930`) | 180 | 1449 | 0 | 0 | 24 |

1442 + 3 (`FilePhotoStoreTest`) + 4 (`CameraCaptureFilesTest`, a new suite) = **1449**, counted.
Skip count unchanged; CI allowlist untouched. `assembleDebug` exit 0. Build log: zero `e:` lines.
Container runs on Linux, not GitHub Actions.

### The FileProvider read-through, tested for the first time

No test in this suite had ever read through a `content://` FileProvider URI; production does on
every capture. `a persisted capture's scratch file is deleted once its bytes are copied` persists
from a real `CameraCaptureFiles.newCapture()` URI (`scheme == "content"`, asserted) and passes, so
**Robolectric's manifest-registered `FileProvider` does serve `openInputStream`**. The reviewer
predicted that and was right. `ContentResolver.delete` was not exercised, by design (§2).

### Revert checks

Six, through the runner (saved copy, XML deleted, compile guard on both streams, byte-compared
restore). No compile errors; `git diff --quiet` clean after each.

| revert | failure produced |
|---|---|
| `persist` never releases | two: "and the scratch file is gone", "released regardless" |
| release only on success (`.also`, not `finally`) | exactly one: "released regardless" |
| sweep ignores age | two: "expected:&lt;1&gt; but was:&lt;2&gt;" (the live file went), and the guard-window case |
| sweep guard set to 0 | exactly one: the guard-window case, "expected:&lt;0&gt; but was:&lt;1&gt;" |
| streaming allowlist's `else` keeps everything | five, including "the coordinate must be gone" and `TAG_MAKE` surviving as "ACME" |
| import source made destructive | exactly one: "not this app's file to delete" |

The second and fourth are the discriminating ones: each isolates a single claim ("in a `finally`",
"the 2 s guard") and fails exactly the one test that holds it.

---

## Disclosure

### Confirmed by observation

- Every caller claim in §1, by `grep` and `git log -S`; every count and message above, from XML
  and build logs in this container.
- The intent path's absence, by `grep` over `app/src`.
- The dispatcher convention: 0 of 18 repository files, 4 UI callers, by `grep`.
- Robolectric serving `openInputStream` through the app's `FileProvider`: by the passing test.
- `ForagerApplication` being the manifest's application class: by reading the manifest.

### Inferred, not observed

- **That `persist` ran on the main thread before this.** Every frame read, none switches. That is a
  proof about code. StrictMode on a debug build is the observation, and it is on the device check.
- That CameraX leaves a partial file when a capture errors mid-write. The cleanup and the fake are
  written for it; the real failure mode was not seen.

### Could not be determined

- Whether the startup sweep deletes anything on a real install. The INFO line it logs is the only
  evidence, and reading it is a device step.
- Anything about the camera itself, unchanged from the previous report.

### Premises that were wrong

- **Mine: "it runs off-main."** Correction 1. Named above as the same error class as the persist
  chain, because it is.
- **Mine: "both verified in code."** True of the call-site claims, not of the main-thread claim,
  which was inferred. Conceded before building.
- **The reviewer's: "repositories are main-safe" as this repo's convention.** Checked; it is not.
  The recommendation survived the correction, the reason did not.
- **The reviewer's: "by string check."** The old branch was `source is CameraCapturePhotoSource`, a
  type check. The design note it introduced stands regardless: the knowledge now lives on the source.

### Decided beyond scope

- **An application `CoroutineScope`.** None existed; the sweep needed one. Created in
  `ForagerApplication`, not `AppContainer`, because it is a process concern no screen asks for.
- **`File.delete()` over `ContentResolver.delete`.** §2.
- **Extracting `FileProviderCacheReset`** into a shared rule once a second class needed it, with
  the correction note left on `InAppCameraDialogTest`, where it belongs.
- **Correcting a second stale sentence in `FilePhotoStore`'s header** ("stripping GPS EXIF … is
  explicitly out of scope"), true when written and false since the scrub landed. Same family as
  the `MainActivity` claim.

### CI: one red, one green, on the same code

Written after the fact, which is why the line below it used to say "no CI run at the time of
writing".

Run 504 on `459b930` (the code commit) was **red**: `JournalTabTest > From Album on the edit form
opens the picker and pulls the selected photo into the entry`, `AssertionError at
JournalTabTest.kt:374`, 1449 tests / 1 failed / 24 skipped. Run 505 on `0f2eac9` (docs only, the
identical code) was **green**, 1449 / 0 / 24.

That is the documented photo-pull flake — same test, same line, same assertion
(`onNodeWithContentDescription("Log photo").assertIsDisplayed()`), the subject of
`2026-09-09-journaltabtest-photo-pull-flake.md`, the pre-registered controlled comparison (4 reds
in 65 CI draws with the test present, 1 in 65 with its body inert), and the 2026-09-10 origin trace;
red on `main` itself at `4236e6b` with no change to the test. And it was not called that by reflex:

- **Reachability, by grep:** `JournalTabTest` never touches `FilePhotoStore`, `persist`,
  `MushroomLogViewModel` or `AppContainer`; it drives a fake `onPullPhoto` harness. The IO switch,
  `release()` and the streamed scrub cannot reach line 374.
- **The one mechanism this push adds to every test was named, not dismissed.** `ForagerApplication`
  now launches the sweep on `Dispatchers.IO` at app creation, and Robolectric creates the
  application for every test; the flake's own inferred mechanism is a `DecodedPhoto` IO hop racing
  a recomposition. A listing of an empty temp directory, finished long before the test reaches its
  assertion, is not a plausible contender on that pool — but that is inference, and the record
  says so.
- **What settled it was counting:** one red in one draw at a ~6 % base rate is consistent with the
  baseline; a second draw of the identical code came back green. Two draws, one each way, on the
  same bytes.

Standing-down comment on the PR: `issuecomment-5671812453`, posted before run 505 finished, naming
the check, the reasons above, that no fix exists to port (the cause is marked inferred, not
executed), and the patch the inferred mechanism implies — a `waitUntil` at line 374 mirroring line
361 — **left to the owner**, because CLAUDE.md rules that a test unrelated to the dispatched task is
reported, not touched, and this one is the subject of a pre-registered measurement series a
drive-by change would contaminate. Nothing skipped, ignored, weakened or allowlisted.

### Checks that did not fire, and empty results

- The re-run that confirmed the flake was not requested; the docs-only push was a free second draw
  of the same code, and it was green.
- No test asserts the dispatcher `persist` runs on. Deliberate: a test that pins a dispatcher tests
  the pin, not the jank. StrictMode is the right instrument and it is device-only.
- **Observed and reported, not fixed:** the release-on-failure test shows that a persist failing
  *after* the copy leaves a file in `photos/` with no row behind it. Pre-existing, out of scope,
  and now on the record.
