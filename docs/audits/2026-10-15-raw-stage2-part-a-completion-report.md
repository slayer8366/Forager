# RAW stage 2, part A (the camera side): completion report

Dispatch 2026-10-15-01 with Amendment 1 (RECORD -1011), split by the owner's "Start 1 and 2" (RECORD -1012): this part is
the camera side; Forager's own DNG writer is part B (dispatch 2026-10-15-02, branch `raw-stage2-dng-writer`). Branch
`raw-stage2`, draft PR #227, left unmerged. Base verified: `origin/raw-stage2` `fbf942b4` (empty, on main `4ade21f0`).
No index row is added to `docs/audits/README.md` here: part B appends to the same index in parallel, and the planner merges
both (CLAUDE.md, "README.md is a serialization point").

## What landed

- **The format chip and its menu** (`ui/log/FormatChip.kt`): JPG and RAW, the one in use ticked, each with its approximate
  size; shown only where the stage 1 probe offers RAW+JPEG at a size within 12.6 MP (`domain/CameraFormat.kt`,
  `rawCaptureSize`). Built like the gear panel: inboard of the strip, turned with the device, closed by Back or a tap
  outside. The chip reads what the camera is actually taking shots in, so a RAW that cannot bind reads JPG.
- **The choice, remembered** (RECORD -1005): `DataStoreCameraFormatRepository`, `CameraFormatViewModel`, threaded as the
  grid mode is. Asked of the session before it opens, so a remembered RAW binds once (Amendment 1, item 1).
- **In-memory RAW+JPEG capture** (`photo/CameraXRawCapture.kt`, `CameraXCaptureSession.captureRaw`): a new use case on a
  new path; today's JPG builder moved unchanged into `jpegImageCapture()`. A RAW bind that fails, or a camera that later
  reports `ERROR_STREAM_CONFIG`, goes back to JPG, logged: never a dead camera. Device-only throughout.
- **The adapter** (`domain/RawFrameMapping.kt`, pure): camera values to the writer's `DngImage` (the contract in
  `domain/raw/DngContract.kt`, copied verbatim from 2026-10-15-02). Every key is the one AOSP `DngCreator` reads; a required
  value the camera did not give means no DNG, with the reason. The DNG's orientation is the JPG's final tag when the JPG is
  the unrotated frame (`rawOrientationFor`, Amendment 1 item 4).
- **The shot step** (`photo/RawShotSaver.kt`): low storage (below 500 MB plus the shot's worst case) never writes the DNG;
  a writer that refuses leaves no file and shows -1003's words; no DNG input shows its own message. All keep the JPG.
- **Storage**: `log_photos.rawRelativePath`, version 22, `MIGRATION_21_22` (a rebuild, as 11 to 12), `22.json` by hand
  with a script that first reproduced 19, 20 and 21's identity hashes. Persist copies the DNG beside the JPG; delete removes
  both; backup, Replace and Merge carry it; export sends it (`image/x-adobe-dng`, `.dng`).
- **Until part B merges**, production's writer is `WriterNotOnThisBranch`, which refuses every file, so a RAW shot keeps its
  JPG and says so. At the merge, `productionDngWriter` becomes `ForagerDngWriter(forbiddenStrings)`: one line.

## Evidence

CI on `355a19d8`: 4894 tests, 0 failures, 0 errors, 24 skipped (the allowlisted set), run 38670704481.

The tests were written after the implementation in this session, not before it, so failing-first is shown by reverting
the behaviour instead: twelve one-line reverts, each committed with git plumbing onto its own branch off `355a19d8` (the
working tree and `raw-stage2` untouched, so nothing to restore), each run on CI with a test filter. Every reverted build
compiled (zero `e: file` lines in each log), every run's head is the revert commit, and every failure is one that edit can
cause:

| Revert | Branch | Failed with |
|---|---|---|
| delete leaves the DNG | `raw-stage2-revert-r1-delete` | FilePhotoStoreTest "deleting a RAW photo removes everything" |
| backup skips the DNG | `-r2-backup` | three JournalBackupTest RAW tests (NoSuchElement for `photos/r1.dng`; file maps differ) |
| no low-storage check | `-r3-lowstorage` | RawShotSaverTest and CameraFormatChipTest low-storage tests |
| refused DNG not deleted | `-r4-refusal` | RawShotSaverTest "a writer that refuses leaves no file" |
| no 12.6 MP limit | `-r5-limit` | CameraFormatTest and CameraFormatChipTest limit tests |
| RAW shot not routed | `-r6-route` | five CameraFormatChipTest RAW-shot tests |
| export ignores the DNG | `-r7-export` | all three viewer RAW export tests |
| persist drops the path | `-r8-persist` | two FilePhotoStoreTest RAW tests |
| format asked after open | `-r9-beforeopen` | CameraFormatChipTest "a remembered RAW is asked for before the camera opens" |
| migration omits the column | `-r10-migration` | SchemaMigrationTest 21 to 22 and 4 to 22 (SQLiteException) |
| dynamic black level ignored | `-r11-dynblack` | RawFrameMappingTest black and white levels |
| chip shown everywhere | `-r12-chipgate` | two CameraFormatChipTest "no chip" tests |

One check did not bite: under r12, `InAppCameraCapabilitiesInvisibleTest` still passed, because the chip is then present
before the probe as well, so its before-and-after comparisons agree. The gate is held by `CameraFormatChipTest` alone.

Existing tests changed, each on purpose: the schema-version literals in `JournalBackupTest` and `SchemaMigrationTest` moved
to 22 (the newer-than-app refusal is now `SCHEMA_VERSION + 1`); `InAppCameraCapabilitiesInvisibleTest` now allows the
format chip and nothing else (its own doc asked for that update the first time a stage showed a capability); two test
camera slots gained the format parameters.

## Not tested, and why

- Everything in `CameraXCaptureSession`'s RAW path and `CameraXRawCapture.kt` except the plane and JPEG copies: Robolectric
  has no camera.
- That CameraX delivers both images to the in-memory callback, and the capture result by sensor timestamp, is read from the
  pinned 1.6.2 artifact and inferred to hold on a phone.
- The writer is faked; its own tests are part B's.
- Whether the build's own export of `22.json` matches the committed one: the export happens in the CI workspace and is not
  compared.

## Device items (the S22 Ultra and the S26 Ultra)

1. The chip appears and offers JPG and RAW, with sizes.
2. Choosing RAW: the preview blinks once and stays live. `adb logcat -s CameraXCaptureSession` shows "Capture rebound as
   Raw", and no "back to JPG".
3. A RAW shot saves. With part B merged, the journal shows it zoomable. Before that, it shows the -1003 message, by design.
4. The DNG opens in a desktop raw editor with correct colours on both phones (GRBG and GBRG).
5. The DNG and JPG are the same way up, in portrait and both landscapes.
6. The file sizes are close to the menu's estimates (about 4 MB JPG, about 18 MB RAW on the S22).
7. Export sends the DNG to the Gallery, and the Gallery accepts it. Delete removes both files.
8. A JPG shot is unchanged from before.
9. The planner reads the DNG's metadata: no make, model, time or location.

## Amendment 2 (RECORD -1024), added 2026-10-15; supersedes the sections above where they differ

The text above describes the branch before part B merged and is kept as written. These points are no longer true of it:
the writer placeholder (`WriterNotOnThisBranch`) is gone, and the -1003 message is no longer shown for every writer
failure.

- **The real writer is on.** `productionDngWriter` returns `ForagerDngWriter(forbiddenStrings)`, part B's writer, merged
  at `2057bc9f`.
- **Messages by cause.** The contract gained one field: `DngWriteResult.Failed.stage`, a nullable `DngFailureStage`
  (Refused, Writing, Check) that defaults to null. `ForagerDngWriter` sets it. Only `Check` shows -1003's words. A check
  that could not run, or a file that could not be re-read, also counts as `Check`: the file is then not confirmed clean.
  Everything else, including a writer that throws or does not say, shows RECORD -1018's words.
- **Colour.** Fallback notices have their own line, drawn in the normal colour (RECORD -1019). Real failures keep the
  error colour.
- **Adapter against part B's reading.** `cfa` and `blackLevel` are now re-phased to the active area's top-left when it
  starts on an odd row or column. On the two test phones the active array starts at 0,0, so nothing changes there. The
  black-level cell is row-major and the lens-shading layout is `(row*columns+col)*4+channel`; both already matched, and the
  test for the second passes before and after by design, as a pin.
- **Evidence.**
  - Failing-first: run 38680140863 on `bc119bbe` (tests only). 11 failed, each for its stated reason. The colour test's
    positive control passed, so the pixel check can see the error colour.
  - Fixed: run 38681188084 on `dcb4ad2d`. 4923 tests, 0 failures, 24 skipped.
  - Revert checks off `dcb4ad2d`: each compiled (zero `e: file` lines), each head was the revert commit, and each failed
    on its own tests. The branches are `raw-stage2-revert-a1-writer` through `a5-phase`. The a5 diff also drops a
    trailing blank line.
- **Index.** Both parts' rows were added to `docs/audits/README.md`. Part B has no report file, and its row says so.
