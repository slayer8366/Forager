# Apply EXIF orientation when displaying photos: completion report

**Date:** 2026-09-13
**Dispatch:** `Dispatch — apply EXIF orientation when displaying photos` (planner: no repo access;
dated 2026-09-13; builds on `claude/new-session-vto65i`).
**Branch:** `claude/new-session-vto65i`. At §0, `origin/main` was `175b050` and the branch head was
`35585b1`, which contains all of the viewer, the Album follow-up and the three location fixes,
plus their reports. Nothing else. `35585b1` is also the head of open PR #102.
**Nothing is pre-authorized to merge.** No schema change. Display only: no file is rewritten
(see "No write path", below).

---

## §1 — the symptom, and what could and could not be confirmed here

**The mechanism is inferred, not observed.** No affected file exists in this container, so
`TAG_ORIENTATION` was not read from one and its value is not reported. What the tree shows:
`CameraCaptureFiles.kt` contains the string `exif` in 0 lines (control: `FilePhotoStore.kt`, 29 lines, case-insensitive `grep -ci`; the first draft of this sentence said 7 from memory and was corrected by counting),
so the capture is a byte copy of whatever the camera app wrote; `FilePhotoStore.kt:85` reads EXIF
only for `GalleryImportPhotoSource`, and only location and timestamp; and both display paths
decoded with `BitmapFactory`, which does not honour the tag. A tag present and ignored would
produce exactly the symptom. Whether the tag is present on the owner's captures is the device's
to say.

**"Imported photos display correctly" — a premise that does not follow from the code.** The
import path reads EXIF for location and timestamp (`FilePhotoStore.readExifData`) and never for
orientation, and its bytes are displayed through the same `BitmapFactory` decode as a capture.
So an *imported* photo carrying an orientation tag displayed sideways too, before this change.
Whether the owner's imports carry one depends on their source: a phone-camera JPEG shared to the
picker usually does; a screenshot or an edited export usually does not. If the owner observed
imports upright, that is consistent with untagged sources, not with the import path applying the
tag. This dispatch's diagnosis stands either way, because it does not rest on that contrast.

---

## What was built

### One narrow reader, two display paths

`readPhotoOrientation(file)` (`app/photo/PhotoOrientation.kt:57`) reads **only**
`TAG_ORIENTATION` (`:58`) and returns a `PhotoOrientation(rotationDegrees, mirrored)` (`:23`),
mapped from the eight defined values at `:31`. It has no way to return a location, a timestamp,
or any other tag; that is the guard against a later change widening it (§2). It is read-only:
`ExifInterface` writes only on `saveAttributes()`, which is not called. `ExifInterface` does
parse the whole APP1 segment into memory to find one tag; nothing here reads any other tag from
it, and nothing is retained. Every failure path — no EXIF, a non-JPEG, a missing file, an
unrecognised or `UNDEFINED` value — displays unrotated and never fails; an exception is logged
at WARN, a missing tag is not (the library returns the default for that).

The three display sites in §4 are two decode paths, not one or three:

- **`DecodedPhoto`** (`ui/log/DecodedPhoto.kt:69`) is the one decode every thumbnail site
  shares: the Album grid, the find's edit and read thumbnails, and three more the dispatch did
  not list (`FindsGalleryScreen` cover, `PullPhotoPickerScreen`, `CartographyEntryEditScreen`'s
  kept photos). Fixed once there: the sampled bitmap is turned by `Bitmap.oriented`
  (`PhotoOrientation.kt:73`) for every orientation value.
- **`decodeBoundedPhoto`** (`ui/log/PhotoViewerDialog.kt:332`) is the viewer's own decode. It
  now returns a `ViewerPhoto` (`:319`): the bitmap plus the clockwise rotation the layer must
  apply. A pure rotation (EXIF 1, 3, 6, 8, which is what cameras write) is left to the draw
  transform; a mirrored value (2, 4, 5, 7, editing artefacts) is baked into the bitmap at `:344`.

### Where the rotation is applied (§3)

- Thumbnails: on the decoded, sampled bitmap, via `Bitmap.createBitmap(src, …, matrix, true)`
  (`PhotoOrientation.kt:79`), the source recycled once the turned copy exists (`:80`).
- Viewer: in the `graphicsLayer` (`PhotoViewerDialog.kt:292`, `rotationZ`), together with
  `viewerRotationFit` (`:358`, applied at `:239`). `ContentScale.Fit` has already fitted the
  *unrotated* bitmap to the viewport, so for 90°/270° the layer also scales by the ratio of the
  rotated fit to the unrotated one, and the pan clamp uses the rotated dimensions. The pointer
  input sits outside the layer as before, so gestures are unchanged.

**No write path touches the photo file.** `grep` over `app/photo` and `ui/log` for
`saveAttributes|outputStream()|FileOutputStream` finds one hit, `FilePhotoStore.kt:80`, the
byte copy at persist time that existed before. `PhotoOrientationTest:104` also checks that
reading the tag leaves the file's bytes and mtime unchanged.

**Orientation values handled (§4).** All eight: rotations 90/180/270 and the flip, transpose
and transverse variants, with the decomposition Glide's `TransformationUtils` uses (rotate, then
mirror horizontally in the displayed frame), chosen over a hand-derived one because transpose and
transverse are exactly where a sign error would survive until a real photo showed it. Absent,
`UNDEFINED` (0), or an unrecognised value (9 and up): display unrotated.

### Memory (§5)

- **Thumbnails: a transient second bitmap of the same pixel count.** `DecodedPhoto` samples at
  `inSampleSize = 4`, so a 12 MP capture is ~1008×756, ~3 MB at ARGB_8888; the turned copy is
  another ~3 MB for the duration of `createBitmap`, then the source is recycled. A grid of
  twenty is twenty sequential ~3 MB peaks, not a 60 MB one. Chosen over a draw transform here
  because thumbnails are drawn with `ContentScale.Crop` into containers of several shapes, and a
  cropped fill rotated 90° leaves gaps in any non-square container.
- **Viewer: no second bitmap for what cameras write.** A rotation is a layer transform on the one
  bitmap the viewer already holds (up to ~49 MB for a 12 MP capture, ~50 MB at the 4096-px edge
  limit). Only a mirrored value goes through `Bitmap.oriented`, costing a transient second bitmap
  of viewer size, so ~100 MB peak for that case; accepted because those values come from editing
  software, not the camera, and the alternative (mirror in the layer) puts the transpose sign
  question into `scaleX = -1` composed with `rotationZ`, which has no test that can fail on it here.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (this branch at `2ed232c`, the location dispatch's full run) | 175 | 1382 | 0 | 0 | 24 |
| after (this branch at `fd7d68c`) | 176 | 1394 | 0 | 0 | 24 |

Twelve tests added: `PhotoOrientationTest` (6, new), `PhotoViewerDecodeTest` (+5),
`DecodedPhotoTest` (+1). Skip count unchanged; CI allowlist untouched. `assembleDebug`:
exit 0 on `fd7d68c`. **Container runs on Linux, not GitHub Actions.** The "before" is this branch
before this dispatch's commit, not a clean `main` worktree; the branch's own before/after against
`main` is in the viewer report.

**The 12-failure Windows path-length pool is absent**, as §6 predicts for Linux: zero failures in
either run, nothing to subtract. Reported as absent, not passed over.

### The fixture, and what the shadow does with it

The fixtures are real JPEGs: a 40×20 baseline JPEG generated once with the JDK's `ImageIO`
(the Android unit-test classpath carries no `java.awt`, which the first compile found) and
embedded as base64, then given an orientation tag by `ExifInterface.saveAttributes()` in the
test. Robolectric's legacy `BitmapFactory` shadow reads a real JPEG's dimensions and honours
`inSampleSize`, and its `Bitmap.createBitmap` honours a matrix's mapped rectangle; the pixels are
fake throughout. So every dimension assertion here ran on 40×20, not on the shadow's 100×100
fallback for arbitrary bytes, and a swap to 20×40 is a swap the code produced.

### The tests (§6: dimensions swap for 90° and 270°)

- `PhotoOrientationTest:42` — a ROTATE_90 tag reads as 90° clockwise; `:47` — all eight values
  map, the mirrored four say so; `:64` — no tag, `UNDEFINED`, an unknown 9, three raw bytes, and a
  missing file all read NORMAL; `:75` — `Bitmap.oriented` swaps 40×20 to 20×40 for 90°, 270° and
  transpose, keeps it for 180°; `:91` — NORMAL returns the same instance, anything else recycles
  the source; `:104` — reading the tag does not modify the file.
- `PhotoViewerDecodeTest:37/46` — a ROTATE_90 / ROTATE_270 fixture decodes untouched with a 90 /
  270 draw rotation and displays 20×40; `:53` — transpose is baked (bitmap 20×40, rotation 0);
  `:60` — untagged is 40×20 with rotation 0; `:68` — the fit correction is 1 for 0°/180°, and
  0.5 for a 40×20 turned 90° into a 100×30 viewport.
- `DecodedPhotoTest:130` — through the real composable: a ROTATE_90 fixture renders as a
  5×10 dp node and the untagged one as 10×5 dp (the bitmap's own sampled size, since the test
  passes no size modifier).

### Failing first

**The first run of `DecodedPhotoTest:130` failed with "Actual width is 5.0.dp, expected
20.0.dp".** The expectation had left out `DecodedPhoto`'s own sampling; 5 is exactly the turned,
sampled width, so the failure confirmed the rotation and refuted the prediction. Per CLAUDE.md
the expectation was corrected (now derived from `DECODE_SAMPLE_SIZE`, made `internal` for that),
and the test's doc comment says so.

Six one-line reverts, the runner from the viewer dispatch (saved copy, previous XML deleted,
compile-error guard, restore from the copy, byte compare); no compile errors, tree identical to
the pushed commit afterwards:

| revert | failures produced |
|---|---|
| `DecodedPhoto`: `decoded.oriented(…)` → `decoded` | 1: "Actual width is 10.0.dp, expected 5.0.dp" |
| viewer: `rotationDegrees = orientation.rotationDegrees` → `0` | 2: "expected 90 but was 0"; "expected 270 but was 0" |
| `fromExifTag`: ROTATE_90 → NORMAL | 2: "expected PhotoOrientation(90, false) but was (0, false)", twice |
| `viewerRotationFit` always 1 | 1: "expected 0.5 but was 1.0" |
| mirror baking `if (orientation.mirrored)` → `if (false)` | 1: "expected 0 but was 90" |
| `recycle()` removed | 1: "the source is released once the turned copy exists" |

---

## Disclosure

### Confirmed by observation

- The two decode paths and the six `DecodedPhoto` call sites, by grep and reading.
- `CameraCaptureFiles` handles no EXIF; `FilePhotoStore.kt:85` is the only EXIF read on
  persist and it is import-only; the one write to a photo file is the persist-time copy.
- Every count, dimension and failure message above, from XML and build logs in this container.
- That `ExifInterface` writes tags into a JPEG with no APP1 segment and reads them back (the
  fixtures depend on it, and they passed).

### Inferred, not observed

- **The §1 mechanism.** No affected file is here; the tag was not read. The chain "byte copy,
  tag never read, `BitmapFactory` ignores it" is read from the code and matches the symptom.
- **That the layer-rotated viewer looks right.** The fit correction is tested as arithmetic and
  the rotation as a decoded value; the composition of `rotationZ` with the zoom scale and pan is
  read, not rendered — Robolectric's shadow bitmap has no pixels to compare.
- **The transpose/transverse sign.** Taken from Glide's mapping; tested for the dimension swap
  only, which cannot tell a transpose from a transverse.

### Could not be determined

- **`TAG_ORIENTATION`'s value on an affected file** (§1's first confirmation): no such file here.
- **Whether the owner's imports display correctly, and what that means**: see §1 above — the
  code says an import with a tag was sideways too, so a correct-looking import says "untagged
  source", not "the import path handles it".
- **Whether a real capture now looks right**: the owner's device, per §6.

### Premises in the dispatch that were wrong

- **"Imported photos display correctly … since the import path reads EXIF."** The import path
  reads EXIF for location and time, never orientation, and displays through the same decode; a
  tagged import was sideways too. The dispatch's fallback reading ("if they are also wrong, the
  cause is elsewhere") does not follow either: both being wrong is what this mechanism predicts.
- **"The three display sites may share a decode path."** Two paths: thumbnails (all six sites)
  share one, the viewer has its own. Fixed once in each.
- **`FilePhotoStore.kt:85`** — held.
- **"Rotation allocates a second bitmap unless applied through the decode or a draw transform"**
  — held, and both routes were used, one per path, for the reasons in §5 above.

### Decided beyond scope

- **Two application strategies rather than one.** A draw transform for thumbnails would break
  `ContentScale.Crop` in non-square containers; a bitmap copy for the viewer would double a
  ~49 MB peak. Each path got the one that fits it.
- **Baking mirrored values in the viewer** (a transient viewer-sized second bitmap for EXIF
  2/4/5/7) rather than composing a flip into the layer, for the testability reason in §5.
- **`DECODE_SAMPLE_SIZE` made `internal`** so the thumbnail test derives its expected sizes from
  the value in use.
- **A tagged import now displays upright too**, because the fix is in the shared decode. Not
  asked for by name; the alternative, special-casing captures, would have needed the decode to
  know a photo's source, which it does not.

### Checks that did not fire, and empty results

- No CI run at the time of writing.
- No pixel-level check anywhere: the shadow decodes no pixels, so nothing here can tell a
  correctly turned image from one turned the wrong way by 180°; the dimension swap is the whole
  of what is testable off-device, as §6 said.
- `verify-design-tokens.sh` not run; no UI colours or motion touched.
