# Device check, camera strip Part B (#123) on the S22 Ultra: run record

**Date:** 2026-09-26, 10:09 to 10:21 UTC
**Device:** Samsung SM-S908U, serial `R5CT321008R`, Android 16. It was the only device attached (`adb devices -l`), and every adb call named it with `-s`.
**Build under test:** `429edb9` (merge of #123), APK `versionCode=899`, `versionName=1.0.899+g429edb96`.
**Dispatch:** `prompts/preserved/2026-09-26-30.md`; intent `2026-09-26-85` in `RECORD.md`.
**Items:** from `2026-09-26-strip-flash-timer-location-completion-report.md`, "Deferred device items".
**Evidence:** `assets/2026-09-26-strip-device-check/`. Timestamps are UTC. The phone's own clock showed UTC-7 and ran about 0.34 s ahead of the host.
**Owner:** away from the phone for the whole run.

## Outcome

| Item | What | Verdict |
|---|---|---|
| Install | `install -r` of 429edb9 over build 784 | **pass** |
| 1 | Four chips in portrait, clear of the cut-out | **not run**: portrait could not be reached without a hand (see item 1) |
| 2 | Four chips in both landscapes, clear of the cut-out | rotation 1: **pass**. Rotation 3: **not run**, for the same reason |
| 3 | Flash On fires on capture | **not run**: deferred to beta testing by owner ruling |
| 4 | Flash Auto fires in the dark but not in daylight | **not run**: deferred to beta testing by owner ruling |
| 5 | Torch, read from CameraX | **pass** (2/2) |
| 6 | Timer 3 s, 10 s, cancel | **pass**. The about-4 s reading of the 3 s case is **unverified** (see item 6) |
| 7 | Location chip and Settings agree | **pass**. The force-stop reading was taken on the default value (see item 7) |
| 8 | Glyph readability | **not run**: deferred to beta testing by owner ruling |

No app crash occurred: `logcat -b crash` has no Forager line. Every setting the run changed is back at its starting value (see Settings).

---

## Install

- **Pass condition:** `install -r` succeeds with no uninstall, and `dumpsys package` shows the new `versionName`.
- **Before:**
  - `versionCode=784`, `versionName=1.0.784+g91cafb2a`;
  - `lastUpdateTime=2026-09-22 11:21:50`, flag `DEBUGGABLE`;
  - app data 952 KiB (`run-as … du -sk .`).
- **Build:**
  - The worktree was checked out detached at `429edb96a11b8cd2ff29e9682d25b0ca79b004a7`, with a clean tree (`git status --porcelain` empty).
  - `./gradlew assembleDebug`: `BUILD SUCCESSFUL in 56s`, no compile errors in the log.
  - `aapt2 dump badging`: `versionCode='899' versionName='1.0.899+g429edb96'`.
  - APK sha256 `d956e0fcbb82bd1266a7b71d48db0175c18028475284a01db4d5b3c198c7bd51`.
- **Install:**
  - The first call, `adb -s R5CT321008R install -r app/build/outputs/apk/debug/app-debug.apk`, was refused by device_guard, which resolved the relative path from its own directory: *"install blocked: could not read the package name of app/build/outputs/apk/debug/app-debug.apk: … No such file or directory"*.
  - The same command with the absolute path printed `Performing Streamed Install` and `Success`, with the signatures matching.
- **After:**
  - `versionCode=899`, `versionName=1.0.899+g429edb96`;
  - `lastUpdateTime=2026-09-26 03:09:46` (phone clock);
  - `firstInstallTime=2026-09-22 11:15:05`, unchanged, so the app was not uninstalled.
- The planner predicted the name would end `+g429edb9`. It ends `+g429edb96`: the build uses an 8-character sha (`app/build.gradle.kts:73`).

## Item 1: portrait

- **Pass condition:** a `uiautomator dump` shows the four strip controls in the order Flash, Timer, Grid, Location, and none of their bounds intersects the cut-out `dumpsys window displays` reports at that rotation.
- **Verdict:** **not run**. The camera window could not be put in portrait without a hand.
- **What was observed:**
  - With "Lock camera to portrait" off, the camera window follows the physical phone: `mCurrentAppOrientation=SCREEN_ORIENTATION_SENSOR` with the camera open.
  - The phone was resting in a landscape pose. Both times the camera was opened from the Album, it came up at `mDisplayRotation=ROTATION_90`:
    - at 10:12:34;
    - at 10:20:13, when the app's own window was at `ROTATION_0` (portrait) just before the tap (dump `41-camera-second-open.xml`).
  - The system rotation settings do not move it. With the camera open, `settings put system user_rotation 3` (10:13:03) left the display at `ROTATION_90`, and so did `0` (10:13:06). The 2026-09-22 run found the same (`2026-09-22-strip-stack-device-check-run-record.md`, finding 1).
- **Why the step was not run another way:** turning "Lock camera to portrait" on would force a portrait window. That is a different configuration from the item as written, and the dispatch names the lock only for the landscape step, and only to turn it off. Choosing it was not the coder's call.

## Item 2: both landscape arrangements

- **Pass condition:** the same as item 1, at rotation 1 and at rotation 3, each against the cut-out `dumpsys` reports at that rotation.
- **Lock:** "Lock camera to portrait" was off at the start, so no change was needed.

**Rotation 1 (`ROTATION_90`): pass.** Dump `10-camera-open.xml`, 10:12:34 to 10:12:36:

| Order down the strip | content-desc | bounds (px) |
|---|---|---|
| 1 | `Flash off` | `[109,34][177,102]` |
| 2 | `Timer off` | `[109,192][177,260]` |
| 3 | `Grid and level on` | `[109,350][177,418]` |
| 4 | `Save location: On` | `[109,508][177,576]` |

- The cut-out at this rotation is `mDisplayCutout=DisplayCutout{insets=Rect(75, 0 - 0, 0) … boundingRect={Bounds=[Rect(0, 512 - 75, 568), …]} … rotation={1}` (`10-cutout-rotation1.txt`). Its `x` runs from 0 to 75, and every chip starts at `x=109`, so none intersects.
- The chip at the cut-out's height is Location, at `y` 508 to 576. It sits 34 px to the right of the cut-out.
- The strip runs down the punch-hole edge.
- Screenshot `10-camera-rotation1.png` (2316×1080) shows the four glyphs in that order, clear of the punch-hole.

**Rotation 3 (`ROTATION_270`): not run.** The phone's resting pose gives rotation 1 only, and settings cannot drive the window (see item 1).

## Item 5: torch

- **Pass condition:** the JUnit XML shows every test in `CameraXTorchInstrumentedTest` passing on `SM-S908U`. The build log has no compile errors. Afterwards the package is still installed with the same `versionName`, and its data has not dropped to near zero.
- **Command:** `ANDROID_SERIAL=R5CT321008R ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zynergylabs.forager.app.photo.CameraXTorchInstrumentedTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true`
  - It was run from the same clean detached checkout at `429edb9`.
  - `app/build/outputs/androidTest-results` was deleted first.
- **Build log:** `Finished 2 tests on SM-S908U - 16`, `BUILD SUCCESSFUL in 13s`, no compile-error lines.
- **JUnit XML** (`torch-junit.xml`): `testsuite name="com.zynergylabs.forager.app.photo.CameraXTorchInstrumentedTest" tests="2" failures="0" errors="0" skipped="0"`, device `SM-S908U - 16`. The two test cases:
  - `torch_goes_on_and_off_with_the_flash_mode`;
  - `closing_with_the_torch_on_puts_it_out`.
  - The class has two `@Test` methods, so the XML covers all of them.
- **logcat** (`torch-logcat.txt`):
  - `DEVICE-CHECK torch ON after 1ms; hasFlashUnit=true`;
  - `DEVICE-CHECK torch OFF after 0ms`;
  - `DEVICE-CHECK close: torchState immediately after close()=0 (0=OFF, 1=ON); OFF observed after 0ms; session.flashMode=Off`.
- **Afterwards:**
  - `versionName=1.0.899+g429edb96`;
  - `firstInstallTime=2026-09-22 11:15:05`, unchanged;
  - `lastUpdateTime` 03:10:09, the task's own `install -r` of the same APK;
  - data 955 KiB, against 952 before;
  - the test package `com.zynergylabs.forager.app.test` stays installed.
- **Verdict: pass.**
- **Not covered:** the completion report's item 5 also asks whether Torch plus a capture fires the flash. That needs eyes on the LED and was not observed.

## Item 6: timer

**How the timing was taken.**
- One shell command sent the shutter tap (`input tap 2034 540`, the "Take photo" bounds `[1933,439][2136,642]`) and then took screenshots back to back. It stamped the host clock before the tap and before and after each `screencap`.
- Each screenshot took about 1.25 s, and the frame is grabbed at an unknown point inside that window. So each reading is quoted as a window measured from when the tap was issued.
- The time each photo file was written (`stat` of `files/photos/*` via `run-as`) was converted to host time by the 0.34 s offset, as an independent clock.
- Following the dispatch, a reading whose window comes within 1 s of the delay boundary is **unverified**. Where a frame's content (the numeral shown) fixes which side of the boundary it is on, that is said.
- The per-frame tables are `21-t3-times.txt`, `23-t10-times.txt` and `24-cancel-times.txt`.

**3 s.**
- The chip read "Timer 3 seconds" and the count read "No photos yet" (dump `20-timer3-set.xml`). The tap was issued at host 1790417621.745.

| Frame | Window after tap | Numeral | Count | Reading |
|---|---|---|---|---|
| `21-t3-0` | 0.08–1.32 s | 3 | No photos yet | |
| `21-t3-1` | 1.33–2.56 s | 2 | No photos yet | **about 1.5 s: pass.** Count unchanged, numeral showing. The numeral "2" places the frame inside the countdown, whatever the window. |
| `21-t3-2` | 2.57–3.85 s | 1 | No photos yet | |
| `21-t3-3` | 3.85–5.09 s | none | No photos yet, shutter dimmed (capturing) | **about 4 s: unverified.** The window starts 0.85 s from the boundary. The frame shows the capture in progress with the count not yet raised. |
| `21-t3-4` | 5.10–6.34 s | none | **1 photo taken** | count +1 |
| `21-t3-5` | 6.35–7.59 s | none | 1 photo taken | still +1 |

- The photo file `e8aa8df5-….jpg` was written at 10:13:46.204 on the phone clock, which is **+4.12 s** after the tap was issued. One file.
- **Verdict: pass.** Exactly one capture, made after the 3 s delay.
- The count rises only when the capture completes. The dialog adds to the count on the capture's `onSuccess` (`InAppCameraDialog.kt`, the `photosTaken += 1` in `capture`). So at about 4 s the count had not yet risen. It had by 5.10 to 6.34 s.

**10 s.**
- The chip read "Timer 10 seconds" and the count read "1 photo taken" (`22-timer10-set.xml`). The tap was issued at host 1790417685.824.

| Frame | Window after tap | Numeral | Count | Reading |
|---|---|---|---|---|
| `23-t10-4` | 4.99–6.24 s | 6 | 1 photo taken | **about 5 s: pass.** Count unchanged, numeral showing. |
| `23-t10-8` | 9.97–11.24 s | 1 | 1 photo taken | |
| `23-t10-9` | 11.25–12.48 s | none | **2 photos taken** | **about 12 s: pass.** Count +1. The window starts 1.25 s past the boundary. |
| `23-t10-10`, `-11` | 12.49–14.91 s | none | 2 photos taken | still +1 |

- The numerals ran 10, 9, 8, 7, 6, 4, 3, 2, 1 across frames 0 to 8. The 5 fell between frames.
- The file `be011704-….jpg` was written at 10:14:57.048 on the phone clock, which is **+10.88 s** after the tap. Two files in total.
- **Verdict: pass.**

**Cancel.**
- The timer was still at 10 s. The first tap was issued at host 1790417719.210.
- A dump from +0.06 to +2.20 s (`24-cancel-during.xml`) shows the shutter's content-desc **`Cancel timer`**, the numeral `9` and the count `2 photos taken`.
- The second tap was issued at +2.20 s and returned at +2.25 s.
- Screenshots `24-cancel-0` to `-9` cover +2.27 to +14.62 s. Every one shows `2 photos taken` and no numeral. Frame `-0` shows the shutter in its pressed state. `24-cancel-8` covers +12.21 to +13.41 s.
- Afterwards `files/photos` held 2 files, and the dump at 10:15:41 (`25-after-cancel.xml`) reads `2 photos taken` and `Take photo`.
- **Verdict: pass.** The cancel added no capture.

## Item 7: location

- **Pass condition:** the chip and Settings' checkbox agree:
  - after a chip toggle, as seen in Settings;
  - after a Settings toggle, as seen on the chip;
  - after `am force-stop` and a relaunch, on both.

  The original value is then restored.
- **Evidence:** the checkbox state is the `checked` attribute of the dump's `android.widget.CheckBox` node beside "Automatically Save Location to Photos". The stored value was read with `run-as … cat files/datastore/photo_location_preferences.preferences_pb`, where the last two bytes are `08 00` for false and `08 01` for true.

| Stage | Time | Chip | Settings | DataStore |
|---|---|---|---|---|
| Start | 10:11:44 | (camera shut) | **checked** (`04-settings.xml`) | no `photo_location_preferences` file, so the default applies. `DEFAULT_AUTO_SAVE_LOCATION_TO_PHOTOS = true` (`DataStorePhotoLocationPreferenceRepository.kt:39`) |
| Camera open | 10:12:34 | `Save location: On` | | |
| Chip tapped | 10:16:16 | **`Save location: Off`** (`30-chip-off.xml`) | | file created, `… 12 02 08 00` (false) |
| Camera closed, Settings opened | 10:17:09 | | **unchecked** (`32c.xml`, `32-settings-after-chip.png`) | |
| Settings checkbox tapped | 10:17:2x | | **checked** (`33-settings-toggled.xml`, `.png`) | `… 12 02 08 01` (true) |
| Camera opened from Photo Gallery | 10:17:53 | **`Save location: On`** (`35-camera-after-settings.xml`) | | |
| `am force-stop` | 10:18:03 | | | `pidof` returned nothing (process gone) |
| Relaunch (`am start`), Settings | 10:18:5x | | **checked** (`37-settings-after-restart.xml`, `.png`) | |
| Camera opened | 10:18:52 | **`Save location: On`** (`38-camera-after-restart.xml`) | | |

- **Verdict: pass.** They agreed in both directions and after the restart.
- **Limit, stated.** Following the dispatch's order, the last value before the force-stop is On, which is also the default. So the after-restart reading cannot tell a persisted On from a lost value falling back to the default.
  - The evidence that the value persists is the DataStore file itself, read at each stage.
  - The one non-default value, Off, was never carried across a force-stop.
  - This is a check that passes on data that cannot fail it (CLAUDE.md, Testing). It is recorded, not worked around.
- **Restore:** the value ended at On, its starting value. What differs is how it is stored: before the run the key was absent and the default applied; now the key is stored explicitly as `true`.

---

## Settings changed and restored

| Setting | Start (read) | Changes | End (read) |
|---|---|---|---|
| `settings system accelerometer_rotation` (auto-rotate) | `0` (10:07) | none | `0` (10:20:53) |
| `settings system user_rotation` | `0` (10:07) | set to `3` by me at 10:13:03, back to `0` at 10:13:06 (read `0`). **Then changed to `1` without a command from me**: read `1` at about 10:19, restored to `0` at 10:19:21 (read `0` three times over 9 s). Reproduced: opening the camera at 10:20:13 made it `1` again, and it stayed `1` after the camera closed. Restored to `0` at about 10:20:25 | `0` (10:20:53) |
| "Automatically Save Location to Photos" | checked (10:11:44) | Off by the chip 10:16:16; On from Settings 10:17 | checked (10:20:3x, `42-settings-final.xml`); now stored explicitly (see item 7) |
| "Lock camera to portrait" | unchecked (10:11:44) | none | unchecked (10:20:3x, `42-settings-final.xml`) |

The final Settings read (`42-settings-final.xml`) matches the starting read (`04-settings.xml`) on every radio button and checkbox: Imperial (US), System Default, Night Maps off, Save Location on, Lock off.

## App data the run created

- The app was upgraded from `1.0.784+g91cafb2a` to `1.0.899+g429edb96`, as dispatched. Build 784 is no longer on the phone.
- Test APK `com.zynergylabs.forager.app.test` is left installed (`leaveApksInstalledAfterRun=true`).
- **Two photos**, left on the phone as the dispatch requires. Both appear in the Album (Photo Gallery):
  - `files/photos/e8aa8df5-30f1-46da-b4fc-5bd27064caf2.jpg`, 1809806 bytes, 10:13:46 UTC;
  - `files/photos/be011704-d543-4c7c-aaed-9a72739f1def.jpg`, 1857003 bytes, 10:14:57 UTC.

  Both are of a desk. The cancel run created none.
- `files/datastore/photo_location_preferences.preferences_pb`, 32 bytes, value true.
- The `forager.db` WAL was touched: the Album's photo rows.
- App data grew from 952 KiB to 4571 KiB.
- `/sdcard/sdc-ui.xml`, the last `uiautomator dump`, is left on shared storage.

## Deferred to beta testing, by owner ruling

The owner, 2026-09-26: *"Defer the ones that need me for the beta testing. Do the ones you can do without me"*. Pass conditions are from the completion report.

| # | Item | Pass condition |
|---|---|---|
| 3 | Flash On | With the chip at "Flash on", every capture fires the flash. |
| 4 | Flash Auto | With the chip at "Flash auto", a capture in the dark fires the flash and a capture in daylight does not. |
| 8 | Glyph readability | The owner's verdict on each new glyph over a live scene: `FlashAuto`, `FlashOn`, `TimerOff`, `Timer3`, `Timer10`, `LocationOn`, `LocationOff`. |

Also still open, and needing a hand:
- item 1 (portrait) and item 2 at rotation 3;
- item 5's "Torch plus a capture fires the flash?".

## Findings

1. **Opening the in-app camera changes the phone's system `user_rotation` when auto-rotate is off.**
   - Seen twice, the second time on purpose: the camera window rotates to `ROTATION_90` by sensor, and `user_rotation` then reads `1` and stays `1` after the camera closes.
   - The app's other screens then came up landscape (the wide layout with the permanent drawer, `32c.xml`) until `user_rotation` was set back to `0`.
   - Whether the app or the Samsung/Android rotation-lock logic writes it is **unverified**. Nothing in this run shows which.
   - Not fixed: out of scope.
2. **A capture's count lags its countdown by about 1 to 2 s.** The count rises on the capture's success, not at zero, so a reading at "delay + 1 s" can still show the old count (item 6, 3 s). This is expected behaviour, recorded so that a later reading is not mistaken for a missed capture.
3. **device_guard resolves a relative APK path from its own working directory**, and so refuses an install that `adb` itself would accept. Worked around by passing the absolute path, which is the same command.

## What was not committed

- The screenshot and dump of the app's launch screen and its other map views are not committed. The map screen showed the phone's grid reference.
- Burst frames that no reading cites stay out of the repository, to keep its size down: `21-t3-0`, `-5`; `23-t10-0` to `-3`, `-5` to `-7`, `-10`, `-11`; `24-cancel-0` to `-7`, `-9`. The screenshots of the camera opened for item 7 are also left out; their dumps are committed.
- All of these were described above from a direct reading and remain only in the session's `/tmp`.
