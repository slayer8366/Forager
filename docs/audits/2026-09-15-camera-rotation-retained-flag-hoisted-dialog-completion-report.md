# The in-app camera closed on rotation: a retained flag, the dialog hoisted above the width-class branch, then `configChanges`

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Commits:** `7a1e5be` (the fix), `4c7ba23` (`configChanges`, separate on purpose), plus this report's commit · **Base for the reads:** `7716f41`.

**Finding, from the device check.** Rotating the phone with the in-app camera open closed the camera and returned to the screen underneath, in the wide layout; rotating back did not bring it back. Two screenshots: portrait with a live viewfinder, landscape showing the map with the drawer beside it.

**One-paragraph outcome.** The camera's open flag now lives in `InAppCameraViewModel`, and the dialog is composed once by `AvailabilityScreen` above its window-width branch (`InAppCameraHost`); the three screens with a Camera button call up with a target and the host routes each photo back to the consumer the target names. The decision written on the old `remember` in 2026-09-14, that a dialog reopening after the user was sent away is worse than a second tap, stays true and is now enforced by the `ViewModelStore` rather than approximated by discarding state. `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"` followed as its own commit. Suite 185/1476 → 189/1491, 0 failures, 24 skipped unchanged; `assembleDebug` exit 0; five revert checks, each failing on the test that holds its claim, including the width flip that reproduces the bug on the old placement. The rotation itself is device-only and is on the check by name.

---

## §1 — Two mechanisms, and the one everyone reasoned about first was the smaller

The verification pass before any change (this session, 2026-09-15) confirmed the mechanism the dispatch named and found a second one underneath it. Both are the reason the fix has two halves.

1. **Activity recreation.** `PhotoAcquisitionLaunchers.kt:87` (at `7716f41`) held `isCameraOpen` in `remember`, with the reasoning at `:84-86`. `AndroidManifest.xml:140-148` declared `MainActivity` with no `configChanges` and no `screenOrientation`, so a rotation recreated the Activity and the flag reset.
2. **The window-width tree swap.** `WindowWidthClass.kt:40-45` buckets the window at 600dp, and its own line 17 says MEDIUM means "phones in landscape". `AvailabilityScreen.kt:2053` composes a different tree per class, `ModalNavigationDrawer` for COMPACT and `PermanentNavigationDrawer` at `:2087` for wider. The three screens with a Camera button were composed inside those trees, at different places in each: `LogEntryDetailScreen` from `JournalTab.kt:342` and `LogPanel.kt:302`; `PhotoGalleryScreen` from `CartographyScreen.kt:321` and `AvailabilityScreen.kt:1185`; `PullPhotoPickerScreen` from `JournalTab.kt:320`, `LogPanel.kt:290` and `CartographyEntryEditScreen.kt:193`. A width flip disposes the compact tree and composes the wide one fresh. Every `remember` inside it goes, the flag, the session and the dialog, **whether or not the Activity was recreated.** The owner's phone crosses the breakpoint: the screenshot shows the wide layout.

The dispatch's own leaning, `configChanges` alone, would have addressed (1) and left (2) exactly as it was, and the owner would have shipped it and seen the same screenshot. The owner's reply on reading the verification: *"The report is right and my recommendation was wrong."* Recorded here because CLAUDE.md's derived-figure entry is about exactly this: a correct line of reasoning about one mechanism, carried onto a case a second mechanism decides.

**Premises checked and clean** (for `configChanges`, and still true): no resource directory in `app/src/main/res` is qualified by orientation, size or night; every configuration read is in composition through `LocalConfiguration` (`WindowWidthClass.kt:40`, `Theme.kt:139`, `MainActivity.kt:278`); nothing reads configuration at construction; no `onConfigurationChanged`, no `recreate()`; the four ViewModels come from `by viewModels`. The landscape layout is designed, not an artifact: README lines 109 and 512, `docs/plans/map-redesign.md:38-48`.

## §2 — Decisions

**Option three, the owner's call.** A retained flag in a ViewModel plus the dialog hoisted above the branch. *"The ViewModelStore distinction between a configuration change and a real destruction is exactly the line `:84-86` was trying to draw and could not draw from a boolean, so the original decision survives intact and becomes structural rather than a comment."* `ComponentActivity` keeps its `ViewModelStore` across a configuration change and clears it in `onDestroy` when `isChangingConfigurations()` is false, so the flag survives a rotation and dies with process death or a memory-pressure destruction. Deliberately not `SavedStateHandle`, which would restore it after process death, the case the rule exists for. The KDoc on `InAppCameraViewModel` carries the sentence and the reason it is now enforced rather than approximated.

**Hoisting, the part that needed a decision.** *"The camera is not a property of the navigation layout, and the current placement is what ties its lifetime to a breakpoint nobody meant it to depend on. One dialog, composed once, above the branch. The three screens keep their Camera buttons and call up to it."* Done as `InAppCameraHost`, composed in `AvailabilityScreen` directly before the `if (windowWidthClass == COMPACT)` branch. One dialog is also one CameraX session rather than one per screen that could offer a button.

**The target is data, not a callback.** `InAppCameraTarget { LOG_ENTRY, ALBUM, CARTOGRAPHY_ENTRY }` is what the ViewModel retains. A lambda captured from a composition that no longer exists is the one thing that must not be retained across recreation, so the routing from target to consumer happens in the host, from callbacks live in the current composition: `onAddLogPhoto`, `onAddGalleryPhoto`, `onAcquirePhotoForCartographyEntry`, all of which `AvailabilityScreen` already received.

**`configChanges` second, and separate.** The fix alone makes the camera survive; `configChanges` keeps everything else across a rotation too (the map's position, and the drawer and tab state that recreation reset) and stays its own commit so it can be backed out alone if the Compose dialog window is wrongly sized through a handled rotation on some Android version. `uiMode` and `smallestScreenSize` left out on the owner's word: night mode and folding are rare enough that a recreation is fine, and listing them widens the blast radius for no gain. `MainActivityConfigChangesTest` reads the merged manifest back through `PackageManager` and pins both directions.

**Decided beyond scope.** The three leaf screens and their two hubs lost their `cameraCaptureFiles` parameter, since none of them composes the dialog any more, and gained an open-camera callback in its place (three callbacks on `JournalTab` and `LogPanel`, two on `CartographyScreen`, one elsewhere); a dead parameter threaded through six signatures is what CLAUDE.md's no-column-without-a-reader rule is about. `InAppCameraTarget` and `InAppCameraSlot` are public rather than internal because `AvailabilityScreen` is public and exposes them; the host, the ViewModel and the CameraX slot stay internal. The `PhotoAcquisitionLaunchers` doc's line "nothing recreates the Activity now" was corrected to "no capture leaves the Activity now": a rotation does recreate it, which is this whole report.

## §3 — What changed

| File | Change |
|---|---|
| `ui/log/InAppCameraHost.kt` (new) | `InAppCameraTarget`; `InAppCameraSlot` (a slot, for the reason `MapSlot` is one); `CameraXInAppCamera` (the session-per-open moved here unchanged); `InAppCameraHost`, with the bug and the decision on it |
| `ui/log/InAppCameraViewModel.kt` (new) | the retained target, `open`/`close`, the `:84-86` sentence and why the store enforces it |
| `ui/log/PhotoAcquisitionLaunchers.kt` | the camera flag and dialog leave; `launchCamera` keeps the permission gate and calls `onOpenCamera` on a grant |
| `LogEntryDetailScreen`, `PhotoGalleryScreen`, `PullPhotoPickerScreen`, `CartographyEntryEditScreen`, `CartographyScreen`, `JournalTab`, `LogPanel` | `cameraCaptureFiles` out, open-camera callback(s) in; `photoAcquisition.CameraDialog()` gone from the three button rows |
| `ui/availability/AvailabilityScreen.kt` | `inAppCameraTarget`, `onOpenCamera`, `onCloseCamera`, `inAppCamera` parameters; the host above the branch; the three routes threaded down |
| `MainActivity.kt` | `InAppCameraViewModel by viewModels()`, collected and passed |
| `AndroidManifest.xml` (`4c7ba23`) | `configChanges` on `MainActivity`, with the reasoning as a comment |

## §4 — Evidence

| Reading | Value | Scope |
|---|---|---|
| Suite before | 185 / 1476 / 0 / 24 | JUnit XML at `7716f41`, this container |
| After the fix | 188 / 1490 / 0 / 24 | `7a1e5be` |
| After `configChanges` | 189 / 1491 / 0 / 24 | `4c7ba23` |
| `assembleDebug` | exit 0 | both commits |

Fifteen new tests. `InAppCameraViewModelTest` (3): starts closed; open records, close clears; a second open replaces. `InAppCameraHostTest` (5): no target composes nothing; each of the three targets routes a shutter-tapped photo to its own callback and nowhere else, through the real `InAppCameraDialog` over `FakeCameraCaptureSession`; Done asks the holder to close and the host owns no open state. `AvailabilityScreenInAppCameraTest` (6): through the screen's real controls, Journal → a new Cartography entry → its add-photo picker → Camera opens one dialog for `CARTOGRAPHY_ENTRY`; **the width flipped from 360 to 700dp through `LocalConfiguration`, which is the tree swap, the compact bottom nav gone, and the camera still there with its photo still routed**; flipping back keeps it; the Album's button opens for `ALBUM`; without `CAMERA` the tap asks and opens nothing; the dialog follows the holder's target. `MainActivityConfigChangesTest` (1).

**What the flip test is and is not.** It is mechanism (2): `currentWindowWidthClass` reads `LocalConfiguration.screenWidthDp`, so providing a `Configuration` with that field set moves the screen between its trees, and the assertion that "Tools" (the compact bottom nav) is gone confirms the flip happened. It is not mechanism (1): Activity recreation is the platform's contract, `createAndroidComposeRule` does not re-set content after a `recreate()`, and the test's open flag is plain state standing in for the ViewModel the same way `cartographyState` stands in for `CartographyViewModel`. Recreation is the device check's.

**Five revert checks, against committed state, restores verified.**

| Revert (one line) | Tests run | Failed | Message |
|---|---|---|---|
| host's target nulled outside COMPACT (the dialog back inside the branch) | `AvailabilityScreenInAppCameraTest` | 1 of 6 | the flip test: `Failed to assert count of nodes` — the bug, reproduced |
| `ALBUM` routed to the find's callback | host + screen tests | 2 of 11 | `expected:<1> but was:<0>` on both Album tests |
| `onOpenCamera()` not called on a grant | screen test | 4 of 6 | `expected:<[CARTOGRAPHY_ENTRY]> but was:<[]>`, `expected:<[ALBUM]> but was:<[]>`, and the two flip tests with no dialog to find |
| `close()` a no-op | `InAppCameraViewModelTest` | 1 of 3 | `expected null, but was:<LOG_ENTRY>` |
| `configChanges` attribute removed | `MainActivityConfigChangesTest` | 1 of 1 | `expected:<1440> but was:<0>` on the handled set |

## §5 — Device-only, by construction

- **Rotation with the camera open**, both directions, on a build off `4c7ba23`: the viewfinder should stay, re-drawn for the new orientation by `PreviewView`'s own display listener, and a photo taken in landscape after the rotation should be upright (the sensor listener, unchanged). This is the finding itself.
- **"Don't keep activities" with the camera open**, background and return: the camera should be **closed**, and the user taps Camera again. This is the case the fix must keep closed, and the one the owner said matters most.
- **Process death** with the camera open (or the same developer option plus a long background): closed on return, same reason.
- **The Compose dialog window through a handled rotation**: whether it stays correctly sized. Not knowable here; Robolectric resizes nothing. If it is wrong on some Android version, `4c7ba23` backs out alone.
- **Where a rotation lands mid-edit**, camera closed: by reading, on the Search drawer in landscape; see the separate finding below. Not verified on device.

## §6 — A separate finding, in its own index row

Turned up while looking at the rotation: `drawerPanel` (`AvailabilityScreen.kt:807`) and `compactTab` (`:704`) are plain `remember` with Search and Map defaults, and `JournalTab.kt:247` keeps the journal's edit mode in local state. With `configChanges` handled, the first two now survive a rotation (they sit above the branch and are no longer recreated away), but the two layouts keep separate navigation state and neither maps onto the other at the swap: a phone rotated mid-edit still lands on the Search drawer in landscape, and the journal's edit mode, inside the compact tree, is gone until rotated back. The draft itself is in the ViewModel and is not lost. Recorded in `docs/audits/README.md` as its own row, not folded into this fix, on the owner's instruction.

### Could not determine

- Whether any Android version among the owner's devices mis-sizes the Compose `Dialog` window through a handled rotation (§5).

### Premises that were wrong

- The dispatch's own candidate, `configChanges` alone, rested on Activity recreation being the mechanism. It was one of two, and not the one that fires on this phone. Named by the owner, recorded here.
