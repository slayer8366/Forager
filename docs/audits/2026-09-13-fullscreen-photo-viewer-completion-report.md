# Full-screen photo viewer on tap: completion report

**Date:** 2026-09-13
**Dispatch:** `Dispatch — full-screen photo viewer on tap` (planner: no repo access; dated
2026-09-12).
**Branch:** `claude/new-session-vto65i`, cut from `origin/main` at `175b050` (verified against the
remote before any code was written, per §0; the branch and `main` were the same commit at start).
**Nothing is pre-authorized to merge.** No schema change.

---

## What was built

Tapping a find's photo thumbnail opens `PhotoViewerDialog`
(`app/src/main/java/com/zynergylabs/forager/app/ui/log/PhotoViewerDialog.kt:104`): a full-window
Compose `Dialog` (`:126`, `usePlatformDefaultWidth = false`, `decorFitsSystemWindows = false`) on a
black ground, the photo fit to screen on open, pinch to zoom up to 5× (`detectTransformGestures`,
`:257`), drag to pan while zoomed, double-tap between fit and 2.5× (`detectTapGestures`, `:266`).
Two exits: the dialog's own back handling and a "Close photo" control. Several photos step with
"Previous photo"/"Next photo" controls under a "2 / 3" counter; zoom resets on each step.

Wired at both places a find shows thumbnails:

- edit view, `LogEntryDetailScreen.kt:330` (`LogPhotoThumbnail`), state at `:251`, dialog at `:300`;
- read view, `LogEntryReportScreen.kt:323` (`ReportPhotoThumbnail`), state at `:90`, dialog at `:199`.

Both hold the open photo's **id** in `rememberSaveable`, not its index, so a removal cannot shift
the viewer onto a neighbour and a rotation (the Activity declares no `configChanges`) comes back on
the same photo.

**Why a `Dialog` and not an in-screen overlay.** Both host screens sit in a `weight(1f)` slot
under `JournalTab`'s tab row and above the bottom nav (`JournalTab.kt:340`, `:356`;
`LogPanel.kt:300`), so "full screen" from inside either would be the content slot. Covering the
display from there means hoisting a viewer state through `JournalTab`/`LogPanel`/`RecordsTab` to
the scaffold. A dialog is its own window over everything, and it owns back: the
innermost-`BackHandler`-wins convention the tabs rely on is untouched because the dialog window
consumes the press first. Alternative rejected: hoisting, for four composables' worth of plumbing
for one modal.

**Why previous/next and not swiping.** §2 made swiping optional and asked for a report if it cost
significant complexity. It does: a `HorizontalPager` and a pinch-zoom page both claim horizontal
drags, so the page must hand drags to the pager at fit scale and keep them while zoomed. That is
gesture arbitration in exactly the region §4 says the suite is blind on. Buttons give the same
reach with none of it.

**How the image is loaded, and what bounds it (§3).** `decodeBoundedPhoto` (`:307`) reads the
file's dimensions with `inJustDecodeBounds`, picks the smallest power-of-two `inSampleSize` that
brings the longest edge to at most `VIEWER_MAX_EDGE_PX = 4096` (`viewerSampleSize`, `:327`;
constant at `:343`), and decodes once, off `Dispatchers.IO` (`:213-214`). `BitmapFactory` is the
same path `DecodedPhoto` already uses; nothing reads or writes EXIF, and nothing writes the file.
One bitmap at a time: the state is keyed on the photo's path and dropped when the photo changes or
the dialog leaves composition. Worst case is one 4096-edge ARGB_8888 bitmap: a 12 MP capture
(4032×3024) decodes at full size, ~49 MB; a 50 MP one halves to 4080×3060, ~50 MB. 4096 is the
`GL_MAX_TEXTURE_SIZE` floor for OpenGL ES 3.0, past which the hardware renderer draws a bitmap as
nothing. ARGB_8888 kept over RGB_565 because colour is part of what is being inspected.

**How the tap and the `×` were separated (§3).** The photo's clickable covers the whole 88dp tile
(`LogEntryDetailScreen.kt:334`); the remove control is a 36dp square in the top-end corner
(`REMOVE_TOUCH_TARGET_DP`, `:324`; clickable at `:362`). Compose's minimum-touch-target expansion
applies only where nothing else is hit directly, and the photo is now a direct hit everywhere, so
the control's effective region is exactly its own box: x 52–88, y 0–36 of the tile. The 36 is
chosen, not inherited. Before this change the control was an `IconButton` at its 48dp Material
minimum, whose box (x 40–88, y 0–48) contains the tile's centre (44, 44), so the natural "open
this" tap in the middle of a thumbnail was a remove. That is a finding about the tree as it stood,
not caused by this dispatch; a 36dp box keeps 8dp clear of the centre on both axes and clears WCAG
2.5.8's 24dp minimum with room. The read view has no `×`, so its whole tile opens.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (clean worktree of `origin/main` at `175b050`) | 172 | 1347 | 0 | 0 | 24 |
| after, first full run (code commit `e5690ec`) | 174 | 1367 | 1 | 0 | 24 |
| after, second full run (test timing fix, see item 4 below) | 174 | 1367 | 0 | 0 | 24 |

Twenty tests added across four classes: `PhotoViewerDialogTest` (11), `PhotoViewerDecodeTest` (5),
`LogEntryDetailScreenTest` (+3), `LogEntryReportScreenTest` (+1); two new suites, two existing
ones grown. Skip count unchanged at 24; the allowlist in `.github/workflows/ci.yml` was not
touched. The one failure in the first full run was this dispatch's own stepping test, a timing
hole in the test (item 4 below), not a viewer defect. `assembleDebug` also built clean on the
same tree (`./gradlew assembleDebug`, exit 0, on `e5690ec`). **All counts come from this container's `./gradlew`, Linux, not
GitHub Actions**; the before run was a separate worktree so the base's XML could not be confused
with this branch's.

**The 12-failure Windows path-length pool is absent**, as §4 predicts for Linux: the only failure
in any run here was this dispatch's own test, so there was no pool to subtract. Reported as
absent, not passed over.

### The tests, and what each can actually fail on

Every touch is a `performTouchInput` at coordinates, not a semantic click, wherever the claim is
routing (CLAUDE.md, Testing).

- `LogEntryDetailScreenTest:335` — seven real touches inside the tile, outside the control's box
  (the centre, three corners, and one point just past the box on each axis), each must open the
  viewer and none may remove.
- `LogEntryDetailScreenTest:353` — five real touches inside the control's box (its centre and four
  points near its corners), each must remove and none may open.
- `LogEntryDetailScreenTest:369` — with three photos, touching the second opens on "2 / 3".
- `LogEntryReportScreenTest:255` — a real touch on the read view's thumbnail opens; a real touch on
  the close control dismisses.
- `PhotoViewerDialogTest:125/144/161` — pinch out zooms past fit and under the ceiling; a wide pinch
  lands exactly on 5.0×; a pinch in at fit stays at 1.0× with no pan.
- `PhotoViewerDialogTest:179/193` — double-tap toggles fit/2.5×; a drag at fit does not pan, a drag
  at 2.5× pans left by no more than the finger moved and not at all vertically.
- `PhotoViewerDialogTest:220/230` — the close control dismisses; a back key sent to the dialog
  window itself dismisses.
- `PhotoViewerDialogTest:245/269` — stepping controls, counter, zoom reset; none of it for one photo.
- `PhotoViewerDialogTest:278` — a corrupt file shows "Couldn't load this photo." and logs at WARN
  with the path, with the close control still there.
- `PhotoViewerDecodeTest` — the sample-size arithmetic, headless.

Zoom is read back from the photo node's `stateDescription` (what TalkBack announces); pan through
`PhotoViewerPanKey`, a semantics property added for exactly this (`PhotoViewerDialog.kt`, see its
doc comment): the layer's own transform is not readable from a semantics node whose modifiers sit
outside it, and pan has no user-visible text. That property is a test seam and is named as one.

### Failing first: what the first run found

The first class-only run of the new tests had three failures, and the first full-suite run a
fourth; each was worth having.

1. **`a touch on the remove control removes` failed at (54, 2) dp: the viewer opened.** The
   control's clickable box had `clip(CircleShape)` on it, and a clip clips hit-testing as well as
   drawing, so the square's corners fell through to the photo. Fixed by moving the clip and the
   ripple onto the 28dp glyph circle through its own `indication` modifier and leaving the square
   as the target (`LogEntryDetailScreen.kt:357-362`). This is the second time on this project a
   coordinate touch has caught something a semantic click would have passed over.
2. **`dragging pans only once zoomed in` failed with `expected Offset(0.0, 0.0) but was
   Offset(0.0, 0.0)`.** The clamp used `coerceIn(-max, max)` with `max = 0`, which returns `-0.0f`
   for a leftward drag, and `Offset`'s `toString` drops the sign. Harmless on screen, but it is a
   different value; the clamp now returns an explicit `0f` on an axis with no slack
   (`PhotoViewerDialog.kt:237`).
3. **`PhotoViewerDecodeTest` expected 16385 wide to need `inSampleSize = 8`; the function said 4.**
   The test was wrong, not the function: the decoder floors, so 16385 / 4 is 4096 wide and inside
   the limit, and the function's integer division matches Skia's `GetSampledDimension`. Per
   CLAUDE.md ("a failure that doesn't match the prediction means the check itself is wrong; fix
   that first"), the expectation was corrected, the floor case kept, and 16388 added as the width
   that genuinely needs 8.
4. **`several photos step with the controls` passed class-only and failed in the full suite**:
   after "Next photo", the test read the zoom label before the next photo's decode (off
   `Dispatchers.IO`, which `waitForIdle` does not wait on) had landed, and found the progress
   indicator where the photo node would be. A timing hole in the test, closed by waiting for the
   photo after each step the way `open()` already does on open; the viewer showing a spinner
   while a photo decodes is the designed behaviour. Recorded because a test that passes alone and
   fails under load is the shape of this project's `JournalTabTest` flake, and the cause here was
   found on the first read rather than called pre-existing.

### Revert checks

Six one-line reverts, each run through a runner that saves a copy of the file before editing,
deletes the previous JUnit XML before the run, refuses to read results if the build log contains a
`e:` compile error, restores from the saved copy (never from git), and compares the restored file
to the copy byte-for-byte. After all six, `git status` was empty against the pushed commit, so the
forward change was still present. No revert produced a compile error; every failure message names
the reverted behaviour and nothing else.

| revert (one line) | classes run | failures produced |
|---|---|---|
| drop the tile's `clickable` (`LogEntryDetailScreen.kt:334`) | `LogEntryDetailScreenTest` (15) | 2: "a touch at (44.0, 44.0) dp must open the viewer expected true was false"; the "2 / 3" counter assertion |
| `REMOVE_TOUCH_TARGET_DP` 36 → 48 | `LogEntryDetailScreenTest` (15) | 1: the same (44, 44) message — the centre becomes a remove |
| `nextScale = scale` (zoom never changes) | `PhotoViewerDialogTest` (11) | 2: "must zoom in past fit, was 1.0"; "expected Zoom 5.0× but was Zoom 1.0×" |
| drop the pan clamp | `PhotoViewerDialogTest` (11) | 2: "a drag at fit must not pan … was Offset(-80.0, 0.0)"; the pinch-in test's pan came back as `-0.0` (printed `Offset(0.0, 0.0)`, unequal to `Offset.Zero`) |
| `dismissOnBackPress = false` on the dialog | `PhotoViewerDialogTest` (11) | 1: back-key test, "expected 1 but was 0" |
| drop the read view's `clickable` (`LogEntryReportScreen.kt:323`) | `LogEntryReportScreenTest` (12) | 1: "TestTag = 'photo-viewer' is not displayed" |

The `dismissOnBackPress` revert is the one that matters most for what the back test claims: the
key event reaches the dialog's own back handling, not something else that happens to close it.

---

## Disclosure

### Confirmed by observation

- The two find views and their thumbnails, at the file:line references above; read, not assumed.
- Photos live at `filesDir/photos/`: `FilePhotoStore.kt:138` (`PHOTOS_SUBDIR = "photos"`),
  `LogPhoto.relativePath` documented as relative to `filesDir` (`LogPhoto.kt`).
- Every count and failure message above, from XML and build logs in this container.
- The `IconButton` geometry that put the tile's centre inside the remove target: measured under
  Robolectric by the 36→48 revert (the centre touch removed with a 48dp box), not only derived.

### Inferred, not observed

- **That the viewer's back handling is what the device's back *gesture* reaches.** The test sends
  a back key to the dialog window; on a device, a gesture reaches the same `ComponentDialog`
  dispatcher, on API 33+ through the window's `OnBackInvokedDispatcher`. Same handler, different
  entry; the device is the authority.
- **The memory figures.** Arithmetic from bitmap dimensions and 4 bytes per pixel; no heap was
  measured, and no device photo was decoded in this container (see next section).
- **That 4096 is safe on the owner's device.** It is the ES 3.0 floor; a device's actual maximum
  is usually higher, never lower for an ES 3.0 device.

### Could not be determined

- **Whether zoom feels right.** §4 says the device is the authority. Robolectric's `BitmapFactory`
  shadow fakes a 100×100 bitmap for any bytes (`DecodedPhotoTest`'s own doc comment), so every
  gesture test here ran against a 100×100 image fit into a 320×470dp window, not a capture.
- **Whether a real capture displays upright.** `BitmapFactory` does not apply EXIF orientation,
  and §3 says not to read EXIF, so the viewer shows the photo exactly as `DecodedPhoto` already
  shows its thumbnail. If thumbnails are upright on the owner's device, the viewer is; if a
  capture carries an orientation tag the thumbnail is already ignoring, both are sideways and this
  dispatch did not change that. Not checked, because it needs a device.
- **Whether the controls clear the status bar and gesture nav on device.** They are inset by
  `WindowInsets.safeDrawing` (`PhotoViewerDialog.kt:143`); Robolectric reports zero insets, so
  this is device-only by construction (CLAUDE.md, known pitfalls).
- **`decodeBoundedPhoto` end to end.** Its two-pass structure runs under Robolectric (the corrupt
  file test goes through it and fails in the first pass), but the shadow decode is fake, so no
  test here proves a large real JPEG comes out at or under 4096. The arithmetic that decides the
  sample size is tested headless instead, and the decode call is a pass-through.

### Premises in the dispatch that were wrong

- **"Both the edit view and the read view, if those differ."** They do, and the premise held; but
  `LogPanel` (the other host, `AvailabilityScreen.kt:1084`) hosts only the edit view
  (`LogPanel.kt:300`) and no report screen at all. Both wired regardless, since the wiring is on
  the screens, not the hosts.
- **"The `×` overlay is on the same tap target."** Not quite: it was a separate `IconButton`
  over the photo, so the two never competed for one node; they competed for one *area*, and the
  control's 48dp minimum already owned the tile's centre before any open action existed. The
  dispatch's caution was right; the mechanism was hit-region overlap, not a shared target.
- **"Photos live at `filesDir/photos/`."** Held.
- **"A pinch-zoom viewer sits near the Compose-interop gesture routing blind spot."** It does not,
  as built: there is no View interop in the dialog, and the pinch and drag are pure Compose
  pointer input, which Robolectric drives faithfully. The blind spot would have applied to the
  pager-plus-zoom design that was not built.

### Decided beyond scope

- **Double-tap to 2.5×.** Not asked for. Added because a 2 cm subject on a phone needs two hands
  for a pinch and one thumb for a double-tap, and it is a viewer affordance, not editing. Ten
  lines; easily removed.
- **Previous/next with a counter.** §2 named swiping as the optional form; this is a different
  form of the same reach, chosen for the reason above.
- **Replacing the remove `IconButton` with a sized `clickable` Box.** The B3 comment on that
  control deliberately left `IconButton` untouched for its 48dp target. This dispatch changed
  that, because 48dp in that corner and an open-on-tap tile cannot both stand. The glyph, scrim
  and contrast (`LogPhotoThumbnailRemoveAffordanceContrastTest`) are unchanged.
- **A semantics property for pan.** Test seam, named as such at its declaration.

### Noticed in passing, outside the question

- **The centre of an edit-view thumbnail was a remove target before this dispatch.** Recorded
  here because the audit index's 2026-09-12 row on findings made in passing says this is the
  occasion. Unverified on hardware; measured under Robolectric only.
- Thumbnails also appear on surfaces that are not a find's own views: `FindsGalleryScreen.kt:218`
  (cover), `PhotoGalleryScreen.kt:161` (album), `PullPhotoPickerScreen.kt:107` (picker),
  `CartographyEntryEditScreen.kt:449` (cartography attachments, shared with its report screen).
  None wired to the viewer; none asked for.

### Checks that did not fire, and empty results

- No lint or `verify-design-tokens.sh` run was made; the viewer uses `Color.Black`/`Color.White`
  and the theme's `scrim` role, no `Color(0x` literal and no palette import, so the script's
  checks 1 and 2 would not have gained a hit from these files, but that is read, not run.
- No CI run at the time of writing; both suite runs are container runs.
