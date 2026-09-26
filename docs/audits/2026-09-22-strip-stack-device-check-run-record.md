# Device check, strip stack (#112 torch, #113 grid and level) — run record

**Date:** 2026-09-22 · **Device:** Samsung SM-S908U, Android 16, build
`BP2A.250605.031.A3.S908USQSAGZH3` · **Head under test:** `b1905e9` (#113's head; contains #112 and
#111) · **APK:** `1.0.783+gb1905e90`, and after the instrumented test was added,
`1.0.783+gb1905e90.dirty` (same app sources; the marker is the uncommitted `androidTest` files at
build time) · **Run folder:** `img/2026-09-22-strip-stack-device-check-b1905e9/` · **Dispatch:**
"device check for the strip stack", 2026-09-22.

**Outcome.** Part 1 steps 1–5, 8–12, 15–17 **pass**. Steps 6, 13 and 14 were **not run**: the
dispatch drives rotation with `adb settings`, which cannot move the camera window, and the owner
ruled (during the run) to record that and skip rather than handle the phone. Part 2 (LED, tilt,
flat hold, glyph verdict) is **not run**; it needs the phone in hand. Three things went wrong that
belong to me and not to the build: **the connected test task uninstalled the app and wiped its data
on this phone**, three taps landed in another app afterwards, and one screenshot captured personal
information (deleted, never committed). All are written up below.

---

## Part 1

| Step | What | Result | Evidence |
|---|---|---|---|
| 1 | `adb devices`, getprop, install | **pass** | one device `R5CT321008R` in state `device`; model/version/build above; install `Success` |
| 2 | Five test tags present | **pass, with a caveat** | flash `FlashChip.kt:57`/applied `:43`; grid chip `GridChip.kt:47`/`:33`; grid `GridOverlay.kt:73`/`:42`; level `LevelLine.kt:94`/`:66`; strip `CameraBands.kt:172`/`:145`. **Not reachable by UI Automator** — the app never sets `testTagsAsResourceId`. Reachable by the Compose instrumented API. This run drove the UI by content description instead. No tag was added, so no commit was needed for this step. |
| 3 | Camera opens, flash chip then grid chip | **pass (in a landscape window, not portrait)** | `01-open-landscape.png`: chips at `[109,34][177,102]` (Flash off) and `[109,192][177,260]` (Grid off) — flash first, grid second, down the punch-hole edge. Renamed from the dispatch's `01-open-portrait.png` because the window was landscape; see finding 1. |
| 4 | One tap: four lines at the thirds, white core, dark outline | **pass** | `02-grid.png`, 2316×1080. Brightest pixel at x=770 (expected 772), x=1542 (1544), y=358 (360), y=718 (720) — each within 2 px. Cross-section at y=540: 769 `(0,0,0)`, 770–773 `(255,255,255)`, 774 `(0,0,0)`. |
| 5 | Second tap: level line present | **pass** | `03-grid-level.png`; chip reads "Grid and level on"; the line is drawn and **yellow**, i.e. the phone was within 1° of level in the hold it was resting in. |
| 6 | Rotate by `settings`, window reflows | **not run** | See finding 1. |
| 7 | Restore rotation settings | **done** | `accelerometer_rotation` and `user_rotation` returned to `1` and `0`, their values at the start. |
| 8 | Torch ON then OFF, read from CameraX | **pass** | `CameraXTorchInstrumentedTest.torch_goes_on_and_off_with_the_flash_mode`, commit `930fba0`. logcat: `DEVICE-CHECK torch ON after 0ms; hasFlashUnit=true`, `DEVICE-CHECK torch OFF after 0ms`. |
| 9 | Torch out on close | **pass, in the stronger form** | `closing_with_the_torch_on_puts_it_out`. logcat: `torchState immediately after close()=0 (0=OFF)`, `OFF observed after 0ms`, `session.flashMode=Off`. **Which was measured:** the torch was already OFF at the first read after `close()` returned, not merely within a second. The ordering *inside* `close()` (torch off, then unbind) is not observable from outside the process; what is observed is that both had happened by the time it returned. |
| 10 | Reopen: flash glyph Off | **pass** | after tapping the flash chip the description read "Torch on" (`04b-torch-on.png`); after Back and reopen, "Flash off" (`05-reopen-torch-off.png`). Torch does not persist. |
| 11 | Grid + Level survives force-stop | **pass** | set to "Grid and level on", `am force-stop`, relaunch, reopen camera: chip reads "Grid and level on" and the overlay is drawn with no tap (`06-after-force-stop.png`; all four thirds carry a white-cored line). |
| 12 | Off survives force-stop | **pass** | set to "Grid off", force-stop, relaunch: chip "Grid off", and no line at any of the four thirds (`07-off-persists.png`, checked by the same pixel scan). |
| 13, 14 | Lock on, rotate | **not run** | See finding 1 and the owner's ruling. |
| 15 | logcat, level-E lines | **pass, enumerated** | `logcat-filtered.txt` (11:08:54–11:09:44, the instrumented run) and `logcat-filtered-part2.txt` (11:13:28–11:20:40, steps 10–17). 21 and 44 E lines. **None originates in the app's own code**: SurfaceFlinger `CompositionEngine` about the preview surface (26), Compose's accessibility bridge hitting blocked hidden APIs and a Samsung `libpenguin.so` probe (12), `AppOps` camera bookkeeping (4), input channels disposed by my own force-stops (2), and Samsung package services naming the package (21 in the first file). The dispatch's expected noise (`Camera2CameraImpl`, `UseCaseAttachState`) did not appear at level E at all; the noise on this device has different tags, which is why they are listed rather than waved through. |
| 16 | Frame rate, observation | **measured, with a correction** | `dumpsys gfxinfo`, 8 s each with the camera open. **Grid off:** 0 frames rendered. **Grid + Level:** 124 frames (~15.5/s), 0 janky, 50th 11 ms, 90th 12 ms, 95th 13 ms. This is the app's **UI** redraw, not the preview: the preview draws on its own surface. With no overlay the UI redraws not at all; with the level on it redraws at the sensor's rate (`SENSOR_DELAY_UI`), which is about 15 Hz and matches the count. **Preview frame rate: not measurable here** — `dumpsys SurfaceFlinger --latency` returned only a refresh period (33333332 ns, a 30 Hz display) and no frame rows. |
| 17 | Glyphs at zoom | **done** | `09-glyphs.png`, five chips cropped at 4×. |

## Part 2 — not run

The LED, the tilt and the flat hold need the phone held and turned, and the glyph verdict is the
owner's. Nothing in Part 2 was attempted, and no screenshot `10-` to `13-` exists.

**What Part 1 already says about two of them**, short of the gate:
- The LED: CameraX reported `torchState` ON and then OFF (step 8), so the request reaches the
  camera. Whether the LED lit is still unobserved.
- The level's sign: unobserved. The line was yellow in the resting hold (step 5), which says the
  snap works, not that the sign is right.

---

## Findings

**1. The dispatch's rotation method cannot drive the camera window (steps 6, 13, 14).** With "Lock
camera to portrait" off the camera requests `SCREEN_ORIENTATION_FULL_SENSOR` (the 2026-09-19 unlock
work), so the window follows the **physical** phone and ignores the system rotation lock.
**Evidence:** with `accelerometer_rotation=0` and `user_rotation=0` — the system pinned to portrait
— the app's own other screens rendered portrait (`00g-journal.png`, 1080×2316) while the camera
window was landscape (`01-open-landscape.png`, 2316×1080) on the same settings, in the same minute.
So steps 6 and 13 need a hand turning the phone. The owner ruled during the run: record and skip.
With the lock **on** the window is forced portrait regardless of the phone, so step 13's "does not
reflow" could have been shown from the adb side alone; it was skipped with the rest.

**2. `connectedAndroidTest` uninstalled the app and wiped this phone's app data. Mine, not the
build's.** Gradle's connected-test task uninstalls both APKs when it finishes. It ran twice
(11:05, 11:08). Afterwards the package was gone (`pm list packages` empty,
`ActivityManager: Invalid packageName`), taking `/data/data` with it: the Journal, the Album and
every setting shown in `00c-settings-before.png` (Imperial units, System Default theme,
"Automatically Save Location to Photos" on, Night Maps off). The Album read "No photos yet" and
Entries showed only the "+" tile beforehand, so little content was lost, but the settings were
real and are now at defaults. **For the next run:** install both APKs and drive
`am instrument` directly, or accept the uninstall knowingly.

**3. Three taps landed in another app.** After that uninstall I did not yet know the app was gone;
the three taps meant for Journal, Album and Camera went to whatever was on screen, which was a
third-party app. They were a tab bar, a row and a button position in our app's layout; what they
did in that app is unknown. I stopped as soon as the screenshot showed it.

**4. One screenshot captured personal information, and was deleted.** The screenshot taken at that
moment showed a health app and an email address. It was deleted before any commit and appears in no
commit (`git log --all --name-only` finds no such path). A second screenshot of the home screen was
deleted unviewed for the same reason. Every image in the run folder is the app's own UI or its
camera preview of the desk it was resting on.

**5. "Night Maps" was toggled on by accident and switched back.** A swipe meant to close the
settings drawer landed on that row (`00d-after-drawer-close.png` shows it checked,
`00e-nightmaps-restored.png` unchecked). It was restored within a minute — and then wiped anyway by
finding 2.

**6. The phone carried a newer build than the head under test, from a commit this repository does
not contain.** Installed before this run: versionCode 784, `1.0.784+g91cafb2a`, debuggable, same
debug signature, installed 2026-09-22 10:40. `91cafb2a` resolves in neither this repository nor
`~/Zynergy/Forager-app`. Its APK was pulled to `/tmp/installed-784-backup.apk` before the downgrade
and **reinstalled at the end of the run** on the owner's instruction; the phone now carries 784
again. Its data is gone regardless (finding 2).

**7. `Straighten` does not read as "grid and level".** At strip size it reads as a ruler
(`09-glyphs.png`). `FlashOff`, `FlashlightOn`, `GridOff` and `GridOn` each read as what they are;
`FlashOff`'s slash crowds the bolt a little. This is the agent's reading of the same image Part 2C
asks the owner to judge, not the verdict.

**8. The supersede branch ran on the device.** `CameraXCaptureSession: enableTorch(false)
superseded by a later request.` appears once in `logcat-filtered-part2.txt`. That path — a pending
`enableTorch` cancelled by a newer one, logged as superseded rather than as a failure — was written
in #112 and had never been observed running.

---

## The row

| | |
|---|---|
| Device | Samsung SM-S908U |
| Android | 16 |
| Build id | `BP2A.250605.031.A3.S908USQSAGZH3` |
| #113 head | `b1905e9` (APK `1.0.783+gb1905e90`, later `.dirty` with the instrumented test) |
| Part 1 | **pass for 1–5, 8–12, 15–17**; 6, 13, 14 not run (rotation not drivable by adb; owner ruled to skip) |
| torchState on close | **OFF already at the first read after `close()` returned** — the stronger of the two forms. The ordering inside `close()` is not observable from outside the process. |
| Frame rate | Preview: **not measurable here** (SurfaceFlinger gave only a 30 Hz refresh period). App UI redraw: 0 frames in 8 s with the grid off; 124 frames (~15.5/s), 0 janky, 50th 11 ms, with Grid + Level — the level redrawing at the sensor's rate. |
| LED | **not run** (Part 2) |
| Level sign | **not run** (Part 2). The line was yellow at rest, so the snap fires; the sign is unobserved. |
| Flat hold | **not run** (Part 2) |
| Glyph verdicts | Owner's verdict not taken. Agent's reading: FlashOff, FlashlightOn, GridOff, GridOn all read true; **Straighten reads as a ruler, not a level** — the dispatch's predicted "replace Straighten" looks right. |
| Surprises on steps that passed | Step 3's window was landscape, not portrait (finding 1). Step 5's level line was already yellow at rest. Step 16 found the UI redrawing ~15×/s only when the level is on. The app's own code emitted no error line all run, and its supersede branch was seen firing (finding 8). |
| Not the build's fault, but this run's | App uninstalled and data wiped by the connected-test task (finding 2); three taps into another app (3); one personal screenshot deleted, never committed (4); Night Maps toggled and restored (5); build 784 downgraded and restored (6). |
