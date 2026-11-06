# Settings moves: Sundown into the Tools drawer, camera settings into the camera — code and tests written, nothing compiled yet

Dispatch 2026-09-28-707 (RECORD intent -707; preserved at `prompts/preserved/2026-10-07-18.md`). Branch `settings-moves`, cut
from `origin/main` at `d223728b` after a fresh fetch, worked in `~/.cache/forager-wt/settings-moves`. No PR. Written 2026-10-08
(UTC, from the clock).

**Status: written, not run.** The dispatch holds Gradle for the planner's go, in the queue after back-by, guidance-text and
search-order. Nothing on this branch has been compiled, no test has run, and no revert check has been done. Every claim below
about behaviour is what the code is written to do, not something observed. `origin/main` has not been merged in again yet; that
happens before the build, as the dispatch says.

Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/` or `docs/`.

## Premises checked

| Premise in the dispatch | Found | Where (at `d223728b`) |
|---|---|---|
| Settings has a Sundown area with "Sundown alerts", "Dark under trees" with its explanation and four choices, and "Off-track reminder" | True | `ui/availability/AvailabilitySettingsUi.kt:349-350` (the calls), `:392-426` (the sections) |
| The two camera options are at the bottom of Settings | True in substance: they are the last two settings rows, above Backup, Crash Logs and Diagnostics | same file, `:352-353` |
| Failure-fixes' shared Settings row composables exist to reuse | True: `SettingsCheckboxRow`, `SettingsRadioRow`, `ExplainedSettingsCheckbox` (dispatch 2026-09-28-658, F6), all `private` | same file, `:497-543` |
| The values, DataStore keys and ViewModel functions can stay unchanged | True: the rows only call callbacks handed down from `AvailabilityScreen`; nothing in the rows touches a repository | `ui/availability/AvailabilityScreen.kt:1834-1863`, `ui/availability/AvailabilityViewModel.kt:1193`, `:1281` |
| The map's three-dot quick menu (back-by) keeps its own copy | **Not on `main`.** back-by is unmerged (`origin/back-by`); its `MapQuickSettings.kt` reuses `SundownSettings`, `OffTrackReminderSettings`, `SUNDOWN_ALERTS_LABEL` and `DARKNESS_MARGIN_CHOICES` from this file, all kept under the same names here, so its copy keeps working after the merge | `origin/back-by:app/src/main/java/com/zynergylabs/forager/app/ui/availability/MapQuickSettings.kt:76-87, :281-295` |
| In-app camera chips are "flash, timer and grid" | **Incomplete.** There is a fourth, the Location chip, which already shows and sets "Automatically Save Location to Photos" (decision B8 / Closed decision D). Kept as it is; the gear goes after it | `ui/log/InAppCameraDialog.kt:541-556`, `ui/log/LocationChip.kt` |
| The chips rotate with the device by `log/RotateWithDevice.kt` and bounce | True (`rotateWithDevice`, `BouncingIconButton`) | `ui/log/GridChip.kt:31-45` |
| The camera has a deliberate no-rotation-animation rule | True: the camera keeps the system's window behaviour (K1, motion Part 3) and the turn carries no animation | `ui/log/InAppCameraDialog.kt:104-107`, `docs/ui/2026-10-07-motion-part-3-report.md:276-277, :303` |
| "Lock camera to portrait" can be changed with the camera open | **New.** The production slot's comment said it could not ("in practice it cannot change while the camera is open, since the dialog covers Settings"). The gear panel makes it possible. The session, the window's orientation request and the arrangement are all already keyed on the value, so the code follows a live change; whether a real CameraX session rebinds cleanly is a device check | `ui/log/InAppCameraHost.kt:60-61` (comment rewritten), `ui/log/WindowOrientation.kt:107-117`, `ui/log/InAppCameraDialog.kt:211` |
| The camera handler for the lock exists | **No.** The camera was given only the value. Added `onLockCameraToPortraitChanged` through `InAppCameraSlot`, `CameraXInAppCamera`, `InAppCameraHost` and `InAppCameraDialog`, wired from `AvailabilityScreen`'s existing parameter, which `MainActivity.kt:547` already wires to `AvailabilityViewModel.onLockCameraToPortraitChanged` (reachability checked by grep) | as cited |

## What was written

### Sundown

- `ui/availability/AvailabilitySettingsUi.kt`: `SettingsContent`, `CompactSettingsTab` and `CompactToolsDrawerContent` no longer
  take or draw the Sundown section, the Off-track reminder or the two camera settings. The Sundown section is now
  `ToolsSundownSection`, the same rows, tags, labels, values and callbacks, with the Off-track reminder as its last row and a
  test tag on the column. `CompactToolsDrawerContent` keeps its `sundown` and `offTrackReminder` parameters and draws the section
  on the Tools page.
- `ui/availability/AvailabilitySearchUi.kt`: `SearchControls` takes a `following` slot, drawn after the Trip Planner in the same
  scroll (a second scroll on the same axis cannot lay out inside the first).
- No fill of its own: the drawer's container stays the one 80% layer over the map. The section is on the Tools page, so it moves
  with motion Part 3's push-from-left pages.

### Camera

- `ui/log/CameraSettingsChip.kt` (new): the gear chip, `Icons.Filled.Settings`, built like the Grid and Location chips
  (`BouncingIconButton`, `OverlayIcon`, `rotateWithDevice`), labelled "Camera settings". Last in the strip.
- `ui/log/CameraSettingsPanel.kt` (new): the panel and everything that closes it. Settings' own `ExplainedSettingsCheckbox`
  draws the two rows with the existing labels and explanations. A `BackHandler` composed after the camera's own closes the panel
  first. A full-window layer under the panel takes a tap anywhere outside and closes the panel, doing nothing else. The panel sits
  just inboard of the strip, inside the strip's band, so in portrait it is under the strip and in landscape beside it. The shutter
  band is on the opposite edge, so the panel cannot cover the shutter, the count or an error at a phone's size. It turns with the
  device. Its width is capped at 300 dp so that, turned a quarter, its square footprint still fits across a portrait phone. Its
  rows scroll if a large font makes it taller than the window. Opening and closing are instant.
- `ui/log/InAppCameraDialog.kt`: the open flag (`rememberSaveable`, so turning the phone to read the panel does not close it),
  the gear in `stripChips`, and the panel composed last.
- `ui/availability/AvailabilitySettingsUi.kt`: `SettingsCheckboxRow` and `ExplainedSettingsCheckbox` are `internal` (they were
  `private`), and `ExplainedSettingsCheckbox` passes an optional `tag` to its row. `PhotoLocationSection` and
  `CameraPortraitLockSection` are removed; their doc reasoning is carried into `CameraSettingsPanel.kt`.

## Decisions taken beyond the dispatch's words (for the planner and the owner)

1. **Where the Sundown section sits: under the Trip Planner's one-line header, not above it.** The Trip Planner starts collapsed,
   so the Sundown section is the second thing on the Tools page and starts in the upper third of the sheet (asserted). It is not
   first because six test classes (`LeavingTheJournalFixesTest`, `AvailabilityScreenBackNavigationTest`,
   `AvailabilityScreenAdaptiveLayoutTest`, `AvailabilityScreenDropdownCloseOutOfTouchModeTest`, `AvailabilityScreenFanBackDrawerTest`,
   `DrawerBackOverJournalTest`) use "Trip Planner displayed" as their probe for "the drawer is open". With a ~430 dp section above
   it, the header would be pushed below the fold on small test windows. The `assertIsDisplayed` probes would then fail, and the
   `assertIsNotDisplayed` probes would pass whether the drawer was open or not, which is worse. The alternative is the section
   first and those probes moved to a tag at the top of the page: one line in the composition, plus a changed assertion in each of
   six classes. Ask the owner which reads better. If the Trip Planner is expanded, the section is pushed down by its content.
2. **The panel's look is mine, not the owner's.** Its fill is the app's dark scheme `surfaceContainer` at 80% in both themes. The
   camera is always black behind it, and in the light scheme Settings' supporting line would be dark grey on black. The 80% is
   borrowed from the map chrome rule. The camera's overlay rule (`CameraOverlay.kt`: outlined glyphs, "no scrim, no gradient, no
   panel") is for the strip's glyphs. Checkbox rows and sentences are not legible that way, and the owner chose a panel. This is
   the first thing to judge on the phone.
3. **A tap outside closes the panel and does nothing else, even on the shutter.** That is the usual behaviour for a popup closed
   by a tap outside. The alternative, where the tap closes the panel and also reaches the shutter, was not taken.
4. **The panel turns with the device**, like every other control, by the owner's 2026-09-17 rule. The dispatch said this only
   for the gear.
5. **The Location chip stays.** The camera now shows "Automatically Save Location to Photos" in two places, the chip and the
   panel. Both show one value (asserted). Removing the chip was not asked for.
6. **The rows' press feedback is unchanged.** The dispatch says the section "follows motion Part 3's ... press shading". Settings'
   checkbox and radio rows were not among the rows motion Part 1 converted to the shaped press (`docs/ui/2026-10-07-motion-part-1-report.md:170-175`),
   so they keep their ripple in the drawer and the panel, as in Settings. If the dispatch meant converting them, that is one
   modifier in each shared row, and it would change Settings' rows too.
7. **The tag strings stay `settings-…`** (`SUNDOWN_ALERTS_TAG`, `OFF_TRACK_REMINDER_TAG`, `darknessMarginTag`). They are test
   hooks, and renaming them would only add churn.

## Stops

None taken. Nothing here changes a setting's value, key or meaning. The panel never covers the shutter at a phone's size (see
"Unverified" for small windows). Items 1 and 2 above are the ones the owner has not seen, and they are flagged rather than
stopped on because each is one line to change.

## Tests

### Changed assertions, each with its reason

| Test | Change | Reason |
|---|---|---|
| `SundownSettingsTest.setScreen` | Navigation is Tools only; the `Settings` tap is removed | The rows are on the Tools page now. Not an assertion; the touches and stored values asserted are unchanged |
| `OffTrackReminderSettingsTest.setScreen` | Same | Same |
| `AvailabilityScreenSettingsPanelTest`: `the photo-location checkbox starts on, explains itself, and toggles` and `the lock-camera checkbox starts off, explains that sideways photos save portrait, and toggles` | **Removed**, replaced by `Settings no longer shows the two camera settings` (label and explanation of each absent, "Night Maps" and "Crash Logs" present, nothing written) | The rows left Settings. Their claims (defaults, explanations, a touch writes the value) moved to `InAppCameraSettingsPanelTest` |
| `SettingsRowsTouchTest.every untagged settings row takes a touch anywhere across it` | The two `touchAcross` lines for photo location and camera lock are removed | Same rows. Their touches across the row are in `InAppCameraSettingsPanelTest.a real touch anywhere across each row writes the toggled value` |
| `SettingsResetSnackbarTest.the snackbar shows once, stays, and its Settings opens Settings` | Waits for "Night Maps" instead of `LOCK_CAMERA_SETTING_LABEL` | It used the lock row as proof Settings opened; that row is gone. "Night Maps" is composed only on the Settings page |
| `AvailabilityScreenInAppCameraTest`: `after a tap on the camera's chip, Settings' checkbox shows the same value` | Renamed `... the gear panel's checkbox shows the same value`; reads the panel's checkbox instead of closing the camera and opening Settings | The claim is "one value seen in two places", and the second place is the panel now |
| `AvailabilityScreenInAppCameraTest.setScreen` and its fake slot | The lock value is test state, stored by a new handler; the slot takes the new parameter | Needed for the new screen-level test; the two existing lock tests pass the same values as before |
| `InAppCameraHostTest` fake slot and host call | Takes and passes the new parameter | Compile only; no assertion changed |
| `InAppCameraDialogTest`: `in portrait all four chips ...` | Renamed `... all five chips ..., gear, ...`; the gear's tag is added to the list | The strip holds five chips. Widened, not weakened |
| `InAppCameraDialogLandscapeTest`: `at ROTATION_90 / ROTATION_270 all four chips ...` and `assertFourChipsDownTheStrip` | Same, five chips, 272 dp in a 360 dp window | Same |

### New tests

- `SundownSettingsTest`: `Tools shows the Sundown section near the top, under the Trip Planner's header, with the off-track
  reminder in it`; `Settings no longer shows the Sundown section or the off-track reminder`.
- `AvailabilityScreenInAppCameraTest`: `the gear opens the panel, its two rows write the screen's values, and Back closes only the
  panel`, run through the real screen with coordinate touches.
- `ui/log/InAppCameraSettingsPanelTest` (new class, 384 x 823 dp; landscape cases at 823 x 384 dp):
  - the gear is in the strip and the panel starts closed;
  - five real touches across the gear each open the panel;
  - both rows show their strings with `AvailabilityUiState`'s defaults (location on, lock off);
  - three touches across each row write the toggled value;
  - Back closes the panel first and the camera next;
  - a tap outside, on the shutter, closes the panel and takes no photo, with the same point taking a photo once the panel is closed
    as the positive control;
  - placement in portrait, and in landscape at `ROTATION_90` and at `ROTATION_270`;
  - the panel and the gear turn with the device.

### Revert checks (planned, not run)

To be run at the Gradle go. Each one edits a single line, saves a copy of the file first, checks the compile log before reading
results, restores the file from the saved copy, and confirms the forward change is still present:

1. `following = {}` in `CompactToolsDrawerContent`: `SundownSettingsTest` fails, no node with the section's tag.
2. `ToolsSundownSection(SundownSettings(), OffTrackReminderSettings())` added back into `SettingsContent`: `Settings no longer
   shows the Sundown section ...` fails, 1 node, not 0.
3. The gear's `add` dropped from `stripChips`: the panel tests and the five-chip tests fail, no gear node.
4. The panel's `BackHandler` removed: `Back closes the panel first ...` fails, the camera was dismissed.
5. `currentOnClose()` removed from the tap-outside layer: `a tap outside ...` fails, the panel is still open.
6. `onCheckedChange = {}` on the panel's lock row: the row-touch test fails, the call list is empty.
7. `onLockCameraToPortraitChanged = {}` at `InAppCameraHost`'s call in `AvailabilityScreen`: the screen-level gear test fails,
   `lockCameraRequests` is empty.

## Unverified

- **Everything above is uncompiled and unrun.**
- Robolectric reports zero insets, so cut-out clearance of the gear and the panel is a device item.
- Whether a real CameraX session rebinds cleanly when the lock is toggled with the camera open, and what the window does when the
  lock comes on with the phone held sideways. Device items.
- The panel's look over a real preview, in daylight, in both app themes. Owner's judgement (decision 2).
- On windows shorter than about 500 dp in portrait (none of the phones in use), the panel's square footprint could reach the
  photo count above the shutter. It never reaches the shutter band at the sizes tested. Not tested below 823 dp tall in portrait.
- Two Compose behaviours the tap-outside test rests on, from memory and not checked against source this session: overlapping
  siblings do not share a touch, and a box that only lays out children is not a hit target. The test is built to fail if either
  is wrong: the shutter would take a photo, or the panel would stay open.
- Doc comments that still say "Settings' …" about these values were left alone outside the files touched here, in
  `AvailabilityViewModel.kt`, `AvailabilityUiState.kt`, `domain/OffTrackReminder.kt`, `domain/ReturnWatch.kt`,
  `domain/CameraOrientationPreferenceRepository.kt` and others. They name the setting, not where it is drawn.

## Merge notes for the build

- back-by changes `AvailabilityScreen.kt` in a different hunk (around `:1619`). Its `MapQuickSettings.kt` doc says "Settings keeps
  the explanation line". After the merge, it is the Tools drawer that keeps it. Its quick menu also shows "Sundown alerts", so a
  screen with both composed has the label twice; the tests here use tags or `onAll…`.
- search-order changes `AvailabilitySearchUi.kt` near `SearchControls` (its `CollapsibleSection` hunk starts a few lines below
  mine). A conflict there would be textual only.
