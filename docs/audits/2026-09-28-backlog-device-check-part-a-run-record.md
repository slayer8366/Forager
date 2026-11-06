# Backlog device check, Part A (the Journal in portrait), on the S22 Ultra: run record

**Status: complete, in two sessions.** Every item has a verdict. The first session (04:58Z to 05:22Z) stopped on four
conflicts in the dispatch and held the items they touched; the second (09:22Z to 09:39Z), under the planner's rulings
in `prompts/preserved/2026-09-28-12.md`, ran them. Results written after each session; the pre-registration committed at `b04dbb2`, before any item ran, is kept unchanged as the Appendix.

**Continuation (dispatch `prompts/preserved/2026-09-28-12.md`, from 09:22 UTC).** The planner ruled on the four
questions; the held items are pre-registered in Appendix C, committed before they ran. The results of this
second session are added below as they are run. The planner's message, quoted verbatim, is in Appendix C.

**Date:** 2026-09-28, from 04:58 UTC. The phone's clock shows UTC-7, so its local date is 2026-09-27
until 07:00Z.
**Device:** Samsung SM-S908U, serial `R5CT321008R`, the only device attached (`adb devices -l`).
`getprop` at 04:58Z: `ro.product.model=SM-S908U`, `ro.build.id=BP2A.250605.031.A3`.
**Build under test:** the installed build, `versionName=1.0.1192+g24589349`, `versionCode=1192`
(`01-dumpsys-package-start.txt`). Nothing installed; no Gradle, no emulator.
**Dispatch:** `prompts/preserved/2026-09-28-04.md`; the intent is `2026-09-28-04` in `RECORD.md` (the planner's).
**Base:** `origin/journal-redesign` at `46186bb` (fetched 04:58Z). This branch, `device-backlog-a-2026-09-28`,
is cut from it.
**Code citations** are at the installed build's commit `2458934`. `git diff --stat 2458934 46186bb -- app/src/main`
touches 17 files, none under `ui/log/`; the one Journal-adjacent file it touches, `ui/availability/AvailabilityScreen.kt`,
is cited at `2458934` (`git show 2458934:…`).
**Evidence:** outside the repository, in `/home/zynergy-labs/Zynergy/device-evidence/2026-09-28-backlog-a/`, cited by
name with sha256 in the Evidence index. No screenshot, dump, coordinate, note text, place name or photo is in this file.
**dp to px:** `wm density` is 450, so 1 dp = 2.8125 px. `wm size` is 1080 x 2316 px, so **the portrait width is
384 dp, not the 360 dp several sources assume.** Nothing emulates 360 dp here.
**Navigation mode:** `settings get secure navigation_mode` = `0`, three-button navigation. Its bar is
`[0,2181][1080,2316]` (135 px, 48 dp); the status bar is `[0,0][1080,75]`; the cut-out is `Rect(512, 0 - 568, 75)`
(`11-dumpsys-window.txt`).

## Outcome

**Status: complete.** The second session ran every held item under the planner's rulings (Appendix C) and pre-registered
them first (`e5ceee7`). It ended with the phone on the Maps tab, as it began, with no new crash, every setting read back
at its start value, and the created data listed under Created data: five labelled drafts, one blank saved entry and one
labelled draft find. Undo brought the saved entry back both times it was deleted.

**First session (kept as written): partial, stopped on four conflicts in the dispatch.** Every item the conflicts do not touch was run, and the
phone was left on the Maps tab, where it was found (the Journal's own state moved: on arrival it had the L0a entry's
report open, which I closed, and it is now on Entries). The items that need this run's own entries, or whose touches would create data the Data
section does not allow, are **held**: none of them was started, and no entry was created. See "Needs a decision".

The table below carries both sessions: a held item's row now gives its second-session verdict. Items 12, 22 and 31
are still **not run** for want of data, since the new entries add no month, no long grid and no large log.

| Item | What | Verdict |
|---|---|---|
| 1 | Build, baseline, inventory | **pass**: `1.0.1192+g24589349`; crash buffer empty at 04:58Z, 05:21Z, 09:22Z and 09:39Z |
| 13 | Back from a single-type chip | **pass**: Finds, then Back to All, then Back to Entries |
| P:320 | Draft Continue | **pass**: with one draft, Continue opened it in the editor; with five, it opened the drafts list, and Back returned to Entries |
| P:321 | The map's "Log a find" routing | **pass**: "+", Find, the pin picker, OK, and the app was on Journal, Records, Finds, with the find form open and its location filled. Back kept the draft and showed "Saved to Drafts" |
| 14 | "✎ New entry" | **pass**: 5 of 5 taps across it each opened a new draft (0 to 5), and the L0a card beside it opened its report. The label and the clearance were read in the first session |
| 18 | "📷 Add photo" in the album | **pass**: 5 of 5 taps across it opened the menu; the menu is inside the window; Take photo opened the app's camera and Import the system picker. See the rotation and permission side effects under the item |
| 8, 45 | Records chip row at 1.0 and 1.15 | **pass** at both: one-line whole labels, and the row scrolls sideways (it overflows at both scales) |
| 15 | Drafts banner one-line fit | **pass** at 1.0, 1.15 and 1.3, with "5 unfinished entries"; at 1.3 the Continue button ends 10 px (3.6 dp) inside the banner |
| 24 | Collapsed row height | **pass**: 48.0, 53.7 and 59.4 dp at 1.0, 1.15 and 1.3, against 64 dp. It shows the day, the weekday and "Nothing kept" |
| 30 | Two-stage swipe | **pass**: opens 144.0 dp (405 px); a 600 px slow swipe opened it without deleting; a tap on the open row closed it and a tap on the closed row opened the entry; a full swipe deleted with a snackbar, and Undo restored it. RTL and the Waypoints scroll **not run** (dispatch; planner) |
| 29 | Night-mode toggle mid-snackbar | **pass**: after the recreation the entry was still out of the list and the snackbar showed again; Undo brought it back |
| 31 | Long-press on a tile near the bottom edge | **not run**: no tile reaches the bottom edge (3 album photos in one row and a half; 2 finds) |
| 10 | Night-mode toggle on Records with a non-default chip | **pass** both ways: Journal, Records and the same chip came back |
| 17 | Night-mode toggle on the album with the drafts list open | **pass** (second session): the Journal tab and the open drafts list came back, and Back from the list showed the Album view |
| 20 | Seasonal after a toggle, then a new search | **pass** |
| 9 | Chip colours (observation) | light and dark recorded, with contrast ratios. **Understory not run: this build has no Understory theme choice** |
| 22 | Sticky header over cards in each theme | **not run**: the entries span one month |
| 11 | The All list with real data (observation) | recorded. Long region *names* **not run** (the one name is short) |
| 19 | Badges at a tile (observation) | recorded. **The journal-entry badge is missing** on the one photo the L0a entry keeps |
| 21 | Cards with real data (observation) | recorded. The offline-map stat's pill is invisible on the card, in both themes |
| 23 | Thumbnails at 56 and 40 dp (observation) | both measured at their sizes and legible, by my reading |
| 28 | The region row's surface (observation) | recorded. It could not show a difference here (see the item) |
| 47 | Details sheet top in portrait | **pass**, but the check could not fail at this data (see the item) |
| 52 | Directions and Share from the sheet | **pass**: `com.google.android.apps.maps`, and `com.android.intentresolver` |
| 19, 26, 32, 50 | TalkBack, partial, from dumps | **partial**: descriptions and flags recorded. Custom actions and click labels cannot be read from a dump. 26's snackbar half (second session): the message "Entry deleted" and a clickable "Undo"; whether it is announced is the owner's |
| 12 | Scrolling performance | **not run**: All holds 7 records |
| 30 "feel", 51, spoken TalkBack, folds, wide tree, Part B, 16, 25, 33 | Owner's, or superseded | **not attempted**, as the dispatch says |

- **No new Forager crash.** `logcat -b crash -d` was empty at the start (`02-crash-start.txt`, 04:58Z) and at the end
  (`121-crash-end.txt`, 05:21:55Z). The app kept one process, PID 19584, throughout, the same one as in L0a.
  `adb logcat -c` was not run.
- **Every setting is back at its start value** (see Settings). One setting not on the dispatch's list changed as a
  side effect and was restored: `user_rotation` (see item 18 and Decisions).
- **The app's data:** unchanged, except for one new `cached_searches` row from item 20's search and one GPX file in
  the app's cache from item 52's Share (see Created data).
- **Planner's predictions:**
  1. Held: Back went from a chip to All, then to Entries.
  2. Held: the row scrolls sideways at 1.15 (and at 1.0); nothing clips.
  3. Held: at least four items are not run for want of data (12, 22, 31, 17), and parts of 11 and 9. One of 11, 19
     and 28 was not wholly unrun: each was run on the data there is.
  4. Held: no crash.

## Items

The pass conditions are those committed at `b04dbb2` before the items ran. They are repeated, unchanged, in the
Appendix at the end.

### Item 1: build, baseline, inventory: **pass**
- `01-dumpsys-package-start.txt` (04:58Z): `versionCode=1192`, `versionName=1.0.1192+g24589349`,
  `lastUpdateTime=2026-09-27 19:18:29`, `firstInstallTime=2026-09-22 11:15:05`. The same at the end
  (`122-dumpsys-package-end.txt`).
- `02-crash-start.txt` is empty (0 bytes), so there was no Forager line at the start.
- The inventory is above. The phone was awake, unlocked (`isKeyguardShowing=false`), with `MainActivity` in focus on the
  Maps tab.

### Item 13: Back from a single-type chip: **pass**
- **Start:** Journal, Records (the L0a run had left it on the Offline maps chip, `22-records-all.xml`). I tapped the
  Finds chip at (455,463), inside its bounds `[309,396][602,531]`. `23-chip-finds.xml`: Finds `checked=true`.
- **Back 1** (`input keyevent KEYCODE_BACK`, 05:05:36Z). `24-after-back1.xml`: still on Records (the Records segment
  `checked=true`), and **All** `checked=true`, Finds `checked=false`.
- **Back 2** (05:05:41Z). `25-after-back2.xml`: **Entries** `checked=true`, with the timeline and "New entry" showing.
  Its dump is byte-identical to `21-entries.xml`, the Entries screen before Records was opened.
- **What each Back did:** the first changed the chip and nothing else; the second changed the Journal's top tab and
  nothing else. No third Back was pressed.

### Item 14: "✎ New entry": the bounds, read without a touch (first session; the touches are under "Second session")
The touches are held under conflict 1. What a dump shows, without touching the button (`21-entries.xml`,
`90-timeline-light.png`):
- **Bounds** `[626,1753][1035,1911]`, 409 × 158 px, which is 145.4 × 56.2 dp. The label node reads "New entry" with
  bounds `[784,1804][979,1861]`, inside the button's.
- **The label is whole, by my reading of `90-timeline-light.png`:** a pencil icon and "New entry", with no ellipsis.
  The code draws `Icons.Filled.Edit` and "New entry" (`CartographyScreen.kt:497-505`); the "✎" in the dispatch is
  that icon, not a character in the label.
- **Clearance:** its bottom (y 1911) is 45 px (16 dp) above the bottom navigation's top (y 1956), and 270 px (96 dp)
  above the system navigation bar's top (y 2181; `11-dumpsys-window.txt`, `navigationBars frame=[0,2181][1080,2316]`,
  three-button navigation). So it clears both in portrait. It was not checked in gesture navigation, which the
  dispatch does not allow me to switch to.
- **"The card beside it still opens":** the phone has one card, `[45,645][1035,1497]`, which ends 256 px above the
  button, so no card sits beside it. That half lacks data.

### Item 18: "📷 Add photo" in the album: **pass**
- **Label** (`80-album.xml`, `80-crop-addphoto.png`): the button is `[622,1753][1035,1911]`, 413 × 158 px, which is
  146.8 × 56.2 dp. Its text node reads "Add photo" whole, `[780,1804][979,1861]`. By my reading of the crop, a
  camera-plus icon and "Add photo", nothing cut off. The "📷" in the dispatch is the `AddAPhoto` icon
  (`EntriesAlbum.kt:204`).
- **Five taps across it,** at y 1832 and x 632, 724, 828, 932 and 1025 px: both ends (10 px inside each edge), the
  centre, and two between. Each opened the menu, "Take photo" at `[758,1499][968,1556]` and "Import" at
  `[758,1634][892,1691]` (`81-addphoto-tap1.xml` to `-tap5.xml`). Each was closed with Back before the next, and the
  album stayed open (`81-addphoto-after.xml`: Album view `checked=true`, no menu).
- **The menu stays on screen:** its popup is `[622,1437][1002,1753]`, inside the window `[0,0][1080,2316]`, above the
  button, and 428 px above the navigation bar.
- **Take photo** (05:16:17Z): the app's own camera opened (`82-camera.xml`: "Flash off", "Timer off", "Take photo",
  "No photos yet"), in the window of `MainActivity`. Left with Back. No photo was taken: `files/photos` still holds the
  same three files, and `files/captures` is empty.
- **A side effect of the camera: the phone was left in landscape.** The camera window follows the sensor, and the phone
  lies in a landscape pose (as in the 2026-09-26 strip run). After Back, the display stayed at `ROTATION_90`, and
  `settings get system user_rotation` read **1**, where it read 0 at the start (`03-settings-start.txt`). I restored it
  with `settings put system user_rotation 0` at 05:17:00Z. The display returned to `ROTATION_0` (`84-portrait-restored.xml`).
  `user_rotation` is not on the dispatch's list of settings I may change; see Decisions.
- **Import** (05:17:14Z): the system photo picker opened, `com.google.android.photopicker/com.android.photopicker.MainActivity`
  (`dumpsys activity activities`, topResumedActivity). Left with Back, nothing picked.
- **A second side effect: a permission prompt.** Behind the picker was the system prompt "Allow Forager to access photos
  and videos on this device?", from `com.google.android.permissioncontroller` (`87-permission-dialog.xml`). This is the
  `ACCESS_MEDIA_LOCATION` request the app fires with every Import (`PhotoAcquisitionLaunchers.kt:131-138`). I dismissed it
  with Back, choosing none of its buttons. `dumpsys package` read `ACCESS_MEDIA_LOCATION: granted=false` with the same
  flags before and after, with no `USER_SET` flag added. See Decisions.
- **Clears the gesture bar:** the same clearance as "New entry": 45 px above the bottom navigation and 270 px above the
  system bar.

### Items 8 (portrait half) and 45 (portrait): the chip row at 1.0 and 1.15: **pass**
The screen is 384 dp wide, not 360 dp.

| Chip | Width at 1.0 (px / dp) | Width at 1.15 (px / dp) |
|---|---|---|
| All 7 | 241 / 85.7 | 250 / 88.9 |
| Finds 2 | 293 / 104.2 | 313 / 111.3 |
| Tracks 1 | 312 / 110.9 | 336 / 119.5 |
| Waypoints 3 | 391 / 139.0 | 428 / 152.2 |
| Offline maps 1 | 433 / 154.0 | 479 / 170.3 |
| Row content, with 8 dp gaps and 16 dp ends | 657.8 dp | 706.2 dp |

- **Sources:** 1.0: `30-all-fs100.xml`, `31-chips-scrolled-fs100.xml`. 1.15: `34-chips-start-fs115.xml`, `33-chips-end-fs115.xml`.
- **One line and whole, by my reading of the crops:** at both scales each label and its count sit on one line, and none is
  ellipsized (`30-…-crop-chips.png`, `31-…-crop-chips.png`, `34-33-crop-chips-fs115.png`). Each label's text node is
  one line high: 57 px (20.3 dp) at 1.0, 66 px (23.5 dp) at 1.15. Each chip's touch height stays 135 px (48 dp).
- **It scrolls, and nothing clips:** at both scales the row overflows 384 dp by about 274 dp and 322 dp. At 1.0, Waypoints
  started at x 960, partly off screen (`30`). One swipe on the row, from (1000,463) to (150,463), brought Offline maps
  fully on screen, ending at x 1035 with the row's 16 dp end padding after it (`31`). At 1.15 the same swipe did the
  same (`33`), and a swipe back returned All to x 45 (`34`). So the 25 dp overflow Robolectric found at 1.15 is a scroll
  on the device, and the row overflows at 1.0 too.
- The font scale change recreated the Activity, and the Journal came back on Records, All (`32-all-fs115.xml`).
- Font scale was restored to 1.0 at 05:07:10Z (`35-after-fs-restore.xml`).

### Item 31: long-press on a tile near the bottom edge: **not run**
- **Missing data:** a grid long enough that a tile sits near the bottom edge.
- The album holds 3 photos, in one row of one tile and one of two, ending at y 1353 (`80-album.xml`). The lowest
  Finds tile ends at y 1235, and the bottom navigation starts at 1956.

### Items 10 and 17: night-mode toggles
**Item 10: pass.**
- **Setup:** Records, with the Offline maps chip selected (`50-dark-offline.xml`), in dark mode.
- **`cmd uimode night no`** at 05:10:40Z. The focused window changed from `dc4446e` to `a5c4c5e` (`dumpsys window`), so
  the Activity was recreated. It was the same process, PID 19584.
  - `51-light-after-toggle.xml`: the Journal tab `selected=true`, Records `checked=true`, **Offline maps** `checked=true`.
- **Back to dark,** with the **Tracks** chip selected. `cmd uimode night yes` at 05:11:53Z; the window changed to
  `658f58e`.
  - `53-dark-after-toggle.xml`: the Journal, Records, and Tracks `checked=true`.
- **What came back, both times:** the bottom-navigation tab, the Journal's top tab and the chip. That is what the code
  says should survive (`compactTab`, `JournalScreenState`), and not J1's "back on Maps".
- **The fold half:** not run (the S22 does not fold).

**Item 17: not run in the first session; run in the second (see "Second session").**
- **Missing data:** the drafts list opens only with two or more drafts (`CartographyScreen.kt:398-404`). The phone has none,
  and the Data section allows me one.
- **Seen in passing:** a toggle to light at 05:18:14Z with the album open. The album view came back
  (`89-album-light.xml`: Album view `checked=true`). This is not item 17, since the drafts list was not open.

### Item 20: Seasonal after a night-mode toggle, then a new search: **pass**
- **Seasonal in dark,** at 05:19Z (`100-seasonal.xml`). The pattern reads "Estimate from 129 observations …" and "Based on
  129 of 139 total observations …".
- **The toggles:**
  - `night no` at 05:19:50Z. Seasonal is still the selected tab (`sel=true`), with the same pattern (`101-seasonal-light.xml`).
  - `night yes` at 05:19:56Z: the same again (`102-seasonal-dark.xml`).
  - So the tab survived both recreations, with its content.
- **A new search.** I opened the search options and changed the month from September to August (05:20:34Z), then closed them.
  - The header read "August · 5 mi". Seasonal showed a new pattern: "Estimate from 78 observations …", "Based on 78 of 89 …"
    (`107-seasonal-after-close.xml`). So Seasonal reloaded for the new key.
  - The loading state was not caught: my first read came after the dropdown closed.
- **Restored:** the month was set back to September at 05:21:27Z. The header reads "September · 5 mi", and the pattern is
  129 of 139 again (`110-seasonal-restored.xml`).
- **The searches' side effect:** `cached_searches` went from 1 row to 2 (see Created data).

### Items 9 and 22: theme observations
**The app's theme choices at this build** are Light, Dark and System Default (`41-settings.xml`; `domain/model/AppThemeMode.kt`).
The app was on **System Default**, and I left it there. Light and dark were reached with `cmd uimode night`.
**There is no Understory choice.** The Understory colours are the Light and Dark schemes themselves
(`ui/theme/Color.kt:48` onwards). So the third theme was **not run**; see "Premises that were wrong".

**Item 9: chip fill against label, read from the screenshots** (`chipcol.py`; the most common pixel colour in the chip's
fill band, and the dominant far colour in its text box). The ratios are WCAG contrast (`contrast.py`). Evidence:
`50-dark-*.png`, `52-light-*.png`, and the montages `50-dark-chips-montage.png` and `52-light-chips-montage.png`.

| Chip | Dark, selected: fill / label (ratio) | Light, selected: fill / label (ratio) | Unselected, icon on the page |
|---|---|---|---|
| All | `#6B421A` / `#F6DFC8` (6.74) | `#F6DFC8` / `#3A2008` (11.73) | icon `#A8CBA0` dark, `#2E5339` light |
| Finds | `#6B421A` / `#E5A76B` (**4.16**) | `#F6DFC8` / `#A2612C` (**3.81**) | icon `#E5A76B` (8.26 on `#1B1B1B`), `#A2612C` (4.62 on `#FAF8F3`) |
| Tracks | `#2B4763` / `#A8C4E4` (5.35) | `#D7E3F2` / `#3B6EA5` (**4.08**) | icon `#A8C4E4` (9.58), `#3B6EA5` (5.00) |
| Waypoints | `#3D5A40` / `#A8CBA0` (**4.28**) | `#D9E8D2` / `#2E5339` (6.81) | icon `#A8CBA0` (9.61), `#2E5339` (8.20) |
| Offline maps | `#373737` / `#CFC9BE` (7.23) | `#E2DCCC` / `#5B5347` (5.53) | icon `#CFC9BE`, `#5B5347` |
| Unselected (any) | page `#1B1B1B` / label `#CFC9BE` (10.46) | page `#FAF8F3` / label `#5B5347` (7.13) | outline, no fill |

- **Below 4.5:1, WCAG AA for normal text:** four selected pairs, bold in the table. The chip label is Material's
  `labelLarge`, 14 sp, which is not large text. They are dark Finds 4.16 and Waypoints 4.28, and light Finds 3.81 and
  Tracks 4.08.
- **Too close to read?** By my reading of the montages, none is unreadable. The light selected Finds label
  (`#A2612C` on `#F6DFC8`) is the weakest to my eye.
- **All and Finds share one selected fill,** `#6B421A` in dark and `#F6DFC8` in light. Selected, they differ only in label
  and icon colour. An observation, not judged.

**Item 22: not run.** It needs entries in two or more months, and every entry is in 2026-09. One sticky header, "SEPTEMBER 2026",
is drawn (`21-entries.xml`, `[45,542][1035,599]`). With one card, there was nothing to scroll under it.

### Items 11, 19, 21, 23 and 28: real-data observations
No content is quoted. The sizes are from the dumps.

**11: the All list** (`30-all-fs100.xml`, `30-all-fs100-half.png`, dark, font 1.0).
- **One day header, then the rows.**
- **The two find tiles sit two to a row:**
  - `[45,667][528,1235]` and `[551,667][1035,1236]`, each 483 × 568 px (171.7 × 202 dp), with 23 px (8 dp) between them;
  - one shows its photo, and the other the no-photo placeholder;
  - each has a caption line.
- **The type badges:**
  - 79 px (28.1 dp) circles;
  - on the tiles, 11 px (3.9 dp) in from the tile's top-start corner, over the photo's corner. By my reading, they cover
    nothing but the corner of the image, and not the caption;
  - on the region and waypoint rows, a badge sits in front of the row, vertically centred;
  - the descriptions are "Find", "Offline map" and "Waypoint".
- **The region row** is `[45,1259][1035,1678]`, 419 px (149 dp) tall:
  - its title is short, so the long-*name* wrap **lacks data**;
  - its status text wraps to 7 lines (2 for the summary and 5 for the "Ready to zoom 15" note), which is what makes it tall.
- **The waypoint row** is a card, 217 px (77 dp).
- **Nothing looked wrong by my reading,** beyond the region row's height. That height comes from a long fixed note, not from
  a name.

**19: badges at a tile** (`80-album.xml`, `89-album-light.xml`; `80-89-badge-crops.png`).
- **The tile** is 325 × 325 px, which is 115.6 dp at 384 dp, not the 107 dp the source assumed for 360 dp. The gap is 3 dp.
- **The badge:**
  - one badge shows, "Attached to a find", `[56,859][118,921]`: 62 px (22.0 dp), 11 px (3.9 dp) in from the bottom-start corner;
  - its colours are `#6B421A` with an `#E5A76B` icon in dark, and `#F6DFC8` with `#A2612C` in light;
  - by my reading it is clear over the photo's light-grey corner in both themes.
- **The entry badge is missing.** This photo is the one the L0a entry keeps: `cartography_entry_photo_refs` holds 1 row, for this
  photo, with the entry `isDraft=0`. By the code, the photo should also carry the journal-entry badge
  (`EntriesAlbum.kt:264-268`, `:303-312`), and it does not, in either theme. I did not look for the cause; see Flags.
- **The other two photos** carry no badge, as expected (no links).
- **"Over dark photos"** lacks data: the only badged photo's corner is light.

**21: cards with real data** (`21-crop-card-dark.png`, `90-crop-card-light.png`; the one card is the L0a entry).
- **The card** is `[45,645][1035,1497]`, 990 × 852 px (352 × 303 dp).
- **The day numeral** sits beside a title that wraps to two lines, and nothing overlaps.
- **Species and stats:**
  - two species chips, one per line;
  - the stats wrap to two lines: finds and track, then waypoints and offline maps.
- **The hero** is `[45,645][1035,1039]`, 394 px (140.1 dp) tall and full width. By my reading, it looks slightly soft but not
  blurred; the photo is a close shot and is itself soft.
- **The offline-map stat has no visible pill.** Its container is `surfaceContainerHighest` (`RecordTypeStyle.kt:46`), and the card's
  colour is the same:
  - `#373737` on `#373737` in dark;
  - `#E2DCCC` on `#E2DCCC` in light.
  - So "1 offline map" reads as bare text where the other three stats sit in pills (see Flags).
- **A long timeline:** not seen. There is one card.

**23: thumbnails.**
- **The card thumbnail** is `[854,1062][1012,1220]`, 158 px (56.2 dp). Its stroke is `#A8C4E4` on the card's `#373737`, 6.62:1
  in dark, and `#3B6EA5` on `#E2DCCC`, 3.87:1 in light.
- **The Tracks row thumbnail** is `[45,564][158,677]`, 113 px (40.2 dp). Its stroke is `#A8C4E4` on `#1B1B1B`, 9.58:1
  (`53-crop-track-row-x2.png`).
- **Legibility:** by my reading, both show the track's shape clearly at their sizes. The light card's 3.87:1 is the weakest,
  and still readable to my eye.

**28: the region row's surface** (`70-offline-scrolled.png`, `30-all-fs100.png`).
- **The colours match:** the region row's background and the list's background read the same, `#1B1B1B` (86% of the row's pixels;
  the rest is text), in the Offline maps chip and in All.
- **Why no difference could show:** the swipe row paints its content on `surface` (`TwoStageSwipe.kt`, the content `Box`), and in
  portrait the list behind it is also `surface`. So the case the source asks about, a region row on some other surface, does not
  occur on this screen.
- **A check that could not fail here:** recorded as an observation, not a pass.

### Item 47 (portrait half): the details sheet's top: **pass**, on data that could not fail it
- **Opened** by a tap on a row's body, for each of the three types:

  | Row type | Evidence | Sheet top (y) | Drag handle |
  |---|---|---|---|
  | Track | `60-track-sheet.xml`, `60-track-sheet-small.png` | 1042 px | `[495,1104][585,1115]` |
  | Waypoint | `65-waypoint-sheet.xml` | about 1415 px (its handle's touch box starts there) | — |
  | Region | `71-region-sheet.xml` | about 1210 px | — |

- **Each top is far below** the status bar and the cut-out, which end at y 75. Each sheet's bottom is at y 2181, the top of the
  navigation bar.
- **Dismissed** with Back each time (`63-sheet-dismissed.xml`: no drag handle).
- **What the check could not see:** every sheet is shorter than half the screen here, so its top never comes near the status bar.
  Whether the sheet's top respects the status-bar inset when its content is tall enough to reach it was not exercised. That needs a
  record with more fields than these, or a short window (Part B).

### Item 52: Directions and Share from the details sheet: **pass**
- **Share,** from the track's sheet (05:12:37Z). The system share sheet opened:
  `com.android.intentresolver/.ChooserActivityLauncher`, titled "1 item", with a `.gpx` file (`61-share-sheet.png`). It was left
  with Back, and no target was touched. The sheet lists contacts and apps. Nothing was shared, to iNaturalist or anywhere.
- **Directions,** from the "DEVICE CHECK waypoint" sheet (05:13:45Z). The phone's maps app opened,
  `com.google.android.apps.maps/com.google.android.maps.MapsActivity`, on a place card for the waypoint (`66-directions-small.png`,
  by my reading). No route or navigation was started.
  - Leaving Maps took two Backs. The first closed its place card (`67-after-directions-back.xml`, still Maps). The second returned to
    Forager with the sheet still open (`68-after-directions-back2.xml`).

### Items 19, 26, 32 and 50: TalkBack, partial, from dumps
TalkBack was not turned on. The fields a `uiautomator dump` writes:

| Control | content-desc / text | clickable | long-clickable | Dump |
|---|---|---|---|---|
| Entries \| Records segments | text "Entries" / "Records"; `checked` marks the selected one | yes (the unselected one) | no | `21`, `22` |
| Timeline / Album toggles | "Timeline view" / "Album view", `checked` | yes | no | `21`, `80` |
| Records chips | text label and count, `checked` | yes | no | `30` |
| "New entry" button | text "New entry" | yes | no | `21` |
| "Add photo" button and its menu | text "Add photo"; items "Take photo", "Import" | yes | no | `80`, `81-*` |
| Album photo tile | "Log photo" | yes | **yes** | `80` |
| Album badge | "Attached to a find" | no | no | `80` |
| Find tile in All | its caption; photo "Log photo"; badge "Find" | yes | **yes** | `30` |
| Region row in All | its name and status text; badge "Offline map" | yes | no | `30` |
| Waypoint row | its name and grid text; badge "Waypoint"; button "Directions to <its name>" | yes | no | `30`, `64` |
| Track row | its time; button "Share track recorded <its time>" | yes | no | `53` |
| Entry card | text children only | yes | no | `21` |
| Details sheet | "Drag handle" (clickable and long-clickable); scrim "Close sheet" | — | — | `60` |

- **Not readable from a dump**, so recorded from the code only, **unverified on the device:**
  - the two-stage rows' custom actions "Edit" and "Delete" (`TwoStageSwipe.kt`, `customActions`);
  - the long-press menus' long-click label "Options for photo" (`EntriesAlbum.kt:258`) and its siblings;
  - the rows' click label "Details for <name>" (`RecordDetailsSheet.kt:113-116`);
  - the tiles' "Open full screen" click label (`EntriesAlbum.kt:252`, `:260`);
  - `uiautomator` does not write any of these.
- **Consistent with the code:** the entry card and the region and waypoint rows show `long-clickable=false`. That fits the code,
  which gives them custom actions rather than a long-click.
- **Item 26's snackbar half** ("announced, Undo reachable") needs a delete: **held** (conflict 4).
- **Whether TalkBack speaks any of this correctly** is the owner's to judge.

### Item 12: scrolling performance: **not run**
- **Missing data:** a log of 100 or more items in All. All holds 7 (`30-all-fs100.xml`: "7 records").
- `dumpsys gfxinfo … reset` was not run.

## Second session (09:22Z to 09:39Z): the held items, under continuation 2026-09-28-12

The pass conditions are those committed at `e5ceee7` before these items ran (Appendix C). At the start the phone was on
the Maps tab, in dark mode, font scale 1.0, and the app was still process 19584. The database was byte-identical to
the first session's end: `db-start2-raw/forager.db` has the same hash as `db-end-raw/forager.db`, as do the `-wal`
and the `-shm`. Nothing had happened to it between the sessions. The phone's local date was 2026-09-28, so every entry
made here is dated 2026-09-28. That day has no finds, tracks, waypoints or covering regions, so the editor offered no
candidates.

### Item 14: "✎ New entry", the touches: **pass**
- **Bounds** in a fresh dump (`211-journal.xml`, 09:27Z): `[626,1753][1035,1911]`, the same as in the first session.
  The dump is byte-identical to `21-entries.xml`.
- **Five taps at y 1832:** x 636, 728, 830, 932 and 1025. That is 10 px inside the start edge, the centre, 10 px inside
  the end edge, and two between.
  - Each tap opened the entry editor: "Your own account (optional)", "Tags", "Photos" and "Finish entry", dated
    2026-09-28, with an empty text field (`212-newentry-tap1.xml`, `218-newentry-tap2.xml` to `-tap5.xml`). The empty
    field shows that each was a new draft and not a reopened one.
  - I typed `DEVICE CHECK 2026-09-28` into each one's text field. The dumps read that string exactly
    (`213-…`, `219-draft2-labelled.xml` to `-draft5-…`), with nothing doubled.
  - After each draft I pressed Back twice, once to close the keyboard and once to leave the editor. Entries came back,
    and the banner counted 1, 2, 3, 4 and then 5 unfinished entries (`215-…`, `220-entries-after-tap2.xml` to `-tap5.xml`).
  - Tap 1 was made alone, and P:320's one-draft Continue was run before taps 2 to 5 (below).
- **The card beside it still opens:** a tap at (300,1500) on the L0a entry's card, `[45,802][1035,1654]`, opened its
  report. The report is dated 2026-09-27, with its map and no text field, so it is the VIEW mode (`222-card-opens.xml`).
  Back returned to Entries (`223-…`).
  - The card ends 99 px (35 dp) above the button, so "beside" here means above it. No card on this phone overlaps the
    button's rows.
- **Label and clearance:** as recorded in the first session (item 14 above), unchanged.

### P:320, draft Continue: **pass**
- **One draft** (09:29:13Z): the banner read "✎ 1 unfinished entry". "Continue ›" (the button `[516,541][780,654]`,
  tapped at its centre) opened that draft in the editor, with its label in the text field (`216-continue-1draft.xml`).
  Back returned to Entries (`217-…`).
- **Five drafts** (09:31:16Z): Continue opened the full-screen list "Unfinished entries", with a "Back to Entries" arrow
  and five cards. Each card reads 28, MON and the label (`224-continue-5drafts.xml`). Back returned to Entries, on the
  Timeline view, with the banner and "New entry" (`225-back-from-list.xml`).

### Item 17: a night-mode toggle on the album with the drafts list open: **pass**
- **Setup:** Entries, Album view (`226-album-5drafts.xml`: Album view `checked=true`, banner showing). Continue opened
  the drafts list (`227-album-list-open.xml`).
- **`cmd uimode night no`** at 09:31:57Z. The focused window changed from `9b1937d` to `f014b86`, so the Activity was
  recreated, in the same process, 19584.
- **What came back** (`228-light-after-toggle-list.xml`): the Journal tab `selected=true`, Entries `checked=true`, and
  the drafts list "Unfinished entries" still open, with its five cards.
- **Back from the list** (`229-light-back-from-list.xml`): Entries, with the **Album view** `checked=true` and "Add
  photo" showing. The tab, the view and the list all survived.
- **Restored:** `night yes` at 09:32:23Z, then Back to the Timeline view (`231-dark-timeline2.xml`).
- **The fold half:** not run. The S22 does not fold.

### The saved entry (for 24, 30 and 29)
- **Made** with a sixth "New entry" tap at the button's centre (830,1832) at 09:32:48Z. The editor offered no
  candidate sections, so there was nothing to withhold (`232-saved-entry-form.xml`). The text was left blank, and
  "Finish entry" was pressed at 09:32:59Z.
- **The result:** the button became "Save", which shows a committed entry (`233-after-finish.xml`). Back left the
  editor with no prompt.

### Item 24: the collapsed row's height: **pass**
- **What the collapsed row shows:** the small day numeral "28", the weekday "MON" beneath it, and "Nothing kept" beside
  them, on one line, in a card the width of the list. There is no stats pill and no title. That matches
  `CartographyEntryCard.kt:163-166`.
- **Measured** (`234-timeline-with-row-fs100.xml`, `235-timeline-fs115.xml`, `235-timeline-fs13.xml`):

  | Font scale | Row | Height | "28" / "MON" / "Nothing kept" text heights |
  |---|---|---|---|
  | 1.0 | `[45,802][1035,937]` | 135 px, **48.0 dp** (the 48 dp floor) | 24.2 / 16.0 / 16.4 dp |
  | 1.15 | `[45,823][1035,974]` | 151 px, **53.7 dp** | 27.4 / 18.5 / 18.5 dp |
  | 1.3 | `[45,849][1035,1016]` | 167 px, **59.4 dp** | 30.6 / 21.0 / 21.3 dp |

- **Against the limit:** all three are under J3's 64 dp. At 1.3 the margin is 4.6 dp.
- **By my reading of `235-crop-banner-row-montage.png`:** nothing is clipped or overlapping at any of the three scales.
- **Font scale** went to 1.15 at 09:33:39Z and to 1.3 at 09:33:47Z. It was restored to 1.0 at 09:34:14Z and read back
  as `1.0` (`236-fs-restored.xml`: the row is 48.0 dp again).

### Item 15: the drafts banner's one-line fit: **pass**
- **Same captures as item 24,** with five drafts, so the label is "✎ 5 unfinished entries".

  | Font scale | Banner | Label node | "Continue ›" button |
  |---|---|---|---|
  | 1.0 | `[45,530][1035,665]`, 48.0 dp | `[90,569][515,626]`, 20.3 dp high | `[548,541][812,654]` |
  | 1.15 | `[45,542][1035,677]`, 48.0 dp | `[90,577][590,643]`, 23.5 dp high | `[625,553][924,666]` |
  | 1.3 | `[45,558][1035,693]`, 48.0 dp | `[90,588][656,664]`, 27.0 dp high | `[693,565][1025,687]` |

- **One line:** at each scale the label's height is one line of text. It is the same height as the "·" and "Continue ›"
  nodes beside it, and the banner stays 48 dp.
- **Inside the banner:** the button sits inside it at every scale. At 1.3 its end is 10 px (3.6 dp) inside the banner's
  end, the only tight margin.
- **By my reading of the montage** (`221-crop-banner-fs100.png`, `235-crop-banner-row-montage.png`): one line and whole
  at all three.
- **Not seen:** a two-digit count ("10 unfinished entries") at 1.3, which would be about one character wider than the
  room left. See Flags.

### Item 30 (portrait): the two-stage swipe, on the saved entry's row: **pass**
- **The partial swipe** (09:34:32Z): `input swipe 1000 870 400 870 3000`, a 600 px end-to-start drag at about 71 dp/s.
  - The row settled **open** (`237-row-open.xml`). Edit is `[630,802][833,937]` (72.2 dp) and Delete is
    `[833,802][1035,937]` (71.8 dp), together **405 px, 144.0 dp**, the predicted width.
  - The content moved to end at x 630, and no snackbar showed. So a swipe of 600 px, past the open threshold at
    about 202 px, opened the row rather than deleting it.
- **A tap on the open row's body** at (300,870): the row closed ("Nothing kept" back at x 181, and no Edit or Delete in
  the dump), and the entry did not open (`238-tap-open-row.xml`).
- **A tap on the closed row** at (600,870): the entry opened, as its report, dated 2026-09-28, with no text field
  (`239-tap-closed-row.xml`). I left it with Back.
- **The delete** (09:35:27Z): `input swipe 1000 870 150 870 2000`, 850 px, past half-way from open to the row's width
  (about 698 px).
  - In a dump 3 s after the swipe ended, the row was gone from the list, and a snackbar `[34,1787][1046,1922]` read "Entry deleted", with "Undo"
    in a clickable box `[852,1787][1023,1922]` (`241-snackbar-delete1.xml`, `.png`).
  - The snackbar sits above the bottom navigation, whose top is at y 1956, and covers the "New entry" button's area
    while it shows.
- **Undo,** tapped at 09:35:32.6Z, 5 s after the swipe began. The row came back, closed, at the same bounds
  (`242-after-undo1.xml`).
- **The threshold itself** was not searched for. What was seen is one swipe on each side of it: 600 px opened the row,
  and 850 px deleted it.
- **Not run:**
  - RTL, as the dispatch says;
  - the Waypoints "vertical scroll on an open row", by the planner's ruling;
  - the J5c half on Records rows, a tap on an **open** waypoint or region row. That row would first have to be swiped
    open, and none of those rows is mine (see Decisions). The same code path (`TwoStageSwipe.kt:297-303`) was seen
    working on my own entry's row. Item 47 in the first session saw the other half: a tap on a closed Records row opens
    the details sheet.

### Item 29: a night-mode toggle mid-snackbar: **pass**
- **The delete** was a full swipe at 09:36:03.7Z. A dump at 09:36:08.5Z shows the snackbar, "Entry deleted" and
  "Undo", with the row gone (`243-snackbar-before-toggle.xml`).
- **`cmd uimode night no`** at 09:36:08.6Z. The focused window changed from `1eda140` to `368bceb`, so the Activity was
  recreated.
- **After the recreation** (`244-snackbar-after-toggle.xml`, `.png`, 09:36:13.6Z):
  - the screen is in the light scheme (page `#FAF8F3`, snackbar `#322F35`);
  - the row is still absent, and the snackbar shows again with "Undo".
  - The XML is byte-identical to `243`, because a dump carries no colour. So the evidence that this snackbar belongs to
    the recreated screen is the screenshot, by my reading and by those pixel values, together with the window change.
- **Undo** was tapped at 09:36:14.1Z. The row came back (`245-after-undo2-light.xml`).
- **Restored:** `night yes` at 09:36:42Z. The row is present in dark (`246-dark-restored.xml`).
- **The database agreed** at about 09:36:50Z (`db-mid-raw/`): 2 saved entries and 5 drafts. The saved 2026-09-28 entry has
  0-length text, and each draft holds the 23-character label.

### Item 26, the snackbar half: **partial**
- **From `241-snackbar-delete1.xml`:** the snackbar's text node reads "Entry deleted" and is not clickable. "Undo" is a
  text node inside a clickable box, `[852,1787][1023,1922]`, 171 × 135 px (60.8 × 48 dp).
- **Undo was reachable** by touch within the timeout, twice.
- **Not seen:** whether TalkBack announces the snackbar (a dump does not show live regions), and the extended timeout
  under an accessibility service. Both are the owner's.

### P:321: the map's "Log a find" routing: **pass**
- **Maps tab** (09:37:11Z): "Plan a trip or log a find here" (`[956,1291][1024,1359]`) opened the tile with Trip, Find
  and Waypoint (`251-add-tile.xml`). "Find" (`[577,1259][751,1394]`) showed the centre-pin picker: "Pin marks the
  location that will be picked", with OK `[45,1798][528,1933]` and Cancel `[551,1798][1035,1933]` (`252-picker.xml`).
- **OK** (09:37:50Z; `253-after-ok.xml`):
  - the Journal tab `selected=true`, the Records segment `checked=true`, and the **Finds** chip `checked=true`;
  - the find form "Find on 2026-09-28" is open, with a "Found at …" line and "Change Location" rather than "Add
    Location", so the location is filled. The coordinates are not recorded here;
  - the form shows Cancel, Save and "Delete this entry".
- **Labelled:** I typed `DEVICE CHECK 2026-09-28` into "Your own identification (optional)" (`254-find-labelled.xml`).
- **Back** (09:38:22Z; `255-after-find-back.xml`):
  - the form closed onto Records, Finds, on its "Log" sub-tab, with "Drafts (1)";
  - a snackbar read "Saved to Drafts", with "Discard". **Neither Cancel nor Discard was pressed.** The snackbar had gone
    by 09:38:30Z (`256-…`).
- **The database** (`db-end2-raw/`, 09:38:40Z): one draft find, dated 2026-09-28, identification
  `DEVICE CHECK 2026-09-28`, `isDraft=1`, with no `draftOfEntryId`.
- **Returned:** Back twice (Finds to All, then Records to Entries), then the Maps tab. The phone was on the Maps tab at
  09:39Z (`260-final2.xml`), as it was at 09:22Z.

### End of the second session
- **Crash buffer:** `logcat -b crash -d` read 0 bytes at 09:39Z (`261-crash-end2.txt`). There is no Forager crash, and the
  process is still 19584.
- **Build:** still `1.0.1192+g24589349` (`262-version-end2.txt`).
- **The app's log** since 09:22Z is saved (`264-logcat-app-pid-session2.txt`, 19,026 lines).
  - 15,272 of them are the MapLibre `getMetersPerPixelAtLatitude` lines.
  - The other W and E lines are graphics-allocator and back-dispatcher noise, plus 5 `Mbgl-HttpRequest … Canceled`.
  - None comes from the app's own tags.
- `adb logcat -c` was not run, and `/sdcard/bla-ui.xml` was removed.

## Settings

| Setting | Start (read) | Changes | End (read) |
|---|---|---|---|
| `settings system font_scale` | `1.0` (04:58Z) | 1.15 at 05:06:27Z; 1.0 at 05:07:10Z | `1.0` (05:21:55Z) |
| Dark mode (`cmd uimode night`; `secure ui_night_mode`) | `yes`; `2` | no 05:10:40, yes 05:11:53, no 05:18:14, yes 05:19:32, no 05:19:50, yes 05:19:56 | `yes`; `2` |
| `settings system user_rotation` (not on the dispatch's list) | `0` | **1**, a side effect of the in-app camera (read just after the camera closed, about 05:16:40Z); set back to 0 by me at 05:17:00Z | `0` |
| `settings system accelerometer_rotation` | `0` | none | `0` |
| `settings secure navigation_mode` | `0` (three-button) | none, and not allowed | `0` |
| Location (`secure location_mode`; `cmd location is-location-enabled`) | `3`; `true` | none | `3` |
| App theme (Settings, Night Mode) | System Default (`41-settings.xml`) | none | System Default (not re-read after 05:08Z; nothing touched it) |
| Search month (session state, not a setting) | September | August at 05:20:34Z, September at 05:21:27Z | September (`110-seasonal-restored.xml`) |
| `ACCESS_MEDIA_LOCATION` (a permission) | `granted=false`, flags `USER_SENSITIVE_WHEN_GRANTED\|USER_SENSITIVE_WHEN_DENIED` | the prompt was dismissed with Back | the same, read at 05:18Z |
| The app's screen at the end | the Maps tab, Topographical, Night Maps off (`10-arrival.xml`) | — | the Maps tab, "Map mode: Topographical … Night mode off." (`120-final.xml`) |

- **Location was not changed.** No item needed it.
- **`/sdcard/bla-ui.xml`,** the dump file this run wrote, was removed at the end (`ls`: no such file).
- **The logs:** read with `-d` only; no log buffer was cleared. The app's log from this process is saved
  (`124-logcat-app-pid.txt`).

### Settings, second session

| Setting | Start (read 09:27:37Z, `202-settings-start2.txt`) | Changes | End (read 09:39:05Z, `263-settings-end2.txt`) |
|---|---|---|---|
| `settings system font_scale` | `1.0` | 1.15 at 09:33:39Z, 1.3 at 09:33:47Z, 1.0 at 09:34:14Z | `1.0` |
| Dark mode (`cmd uimode night`; `secure ui_night_mode`) | `yes`; `2` | no 09:31:57Z, yes 09:32:23Z, no 09:36:08.6Z, yes 09:36:42Z | `yes`; `2` |
| `settings system user_rotation` | `0` | none (the camera was not opened this session) | `0`; display `ROTATION_0` |
| `settings system accelerometer_rotation` | `0` | none | `0` |
| `settings secure navigation_mode` | `0` (three-button) | none | `0` |
| Location (`secure location_mode`; `cmd location is-location-enabled`) | `3`; `true` | none | `3`; `true` |
| App theme | System Default | none | System Default (not touched) |
| The app's screen | the Maps tab (`210-arrival2.xml`) | — | the Maps tab, "Topographical … Night mode off." (`260-final2.xml`) |

## Created data

- **No Journal entry or draft was created.** Data (a) is unused, because of the conflicts. Nothing was deleted, and no Undo was
  needed.
- **The database,** compared start (`db-start-raw/`, 04:59Z) to end (`db-end-raw/`, 05:21Z):
  - every counted table is unchanged: entries 1, finds 2, photos 3, find-photo links 1, tracks 1, track points 23, waypoints 3,
    regions 1, planned trips 0, entry find refs 2, entry photo refs 1;
  - the only difference is **`cached_searches`, 1 row to 2.** Item 20's August search added a row, and the September search
    refreshed the existing one (`lastAccessedAtEpochMillis` changed).
  - `forager.db` is byte-identical at the two reads; the changes are in the `-wal`.
- **One file in the app's cache:** `cache/tracks/forager-track-2026-09-27-192346.gpx` (8,739 bytes, 05:12Z), written by item 52's
  Share. It was not shared.
- **No photo:** `files/photos` holds the same three files, and `files/captures` is empty.
- **DataStore files:** none has an mtime after 04:52Z. They were listed, not pulled.

### Created data, second session

All were made through the app's own UI, are left in place, and are dated 2026-09-28 on the phone. Read from
`db-end2-raw/` (09:38:40Z) against `db-start2-raw/` (09:27Z):

| What | How made | Label | Count |
|---|---|---|---|
| Journal drafts (`cartography_entries.isDraft=1`) | item 14's five taps on "New entry" | `DEVICE CHECK 2026-09-28` in the entry's text | 0 → **5** |
| A saved Journal entry (`isDraft=0`) | a sixth "New entry", then "Finish entry" (Data (a); ruling 3) | **none**: its text is blank, as ruling 3 requires; no candidates were offered | 1 → **2** |
| A draft find (`mushroom_log_entries.isDraft=1`) | P:321, the map's "Log a find" | `DEVICE CHECK 2026-09-28` in "Your own identification" | 0 → **1** |

- The draft find carries a location, the map's centre at that moment, which is the owner's area. It has no photo.
- **Deletes:** two, both of the saved entry, both ended in **Undo**, and both restored it (items 30 and 29). The entry is
  present at the end.
- **Unchanged:** saved finds 2, album photos 3, find-photo links 1, tracks 1, waypoints 3, offline regions 1,
  `cached_searches` 2, entry photo refs 1, entry waypoint refs 3.

## Decisions I made

1. **Proceeded without the kit's record steps and its structural validation.** My agent definition asks for a sweep, an intent
   and a terminal in `RECORD.md`, and validation against `.claude/kit.json`. `.claude/` does not exist at `46186bb`, and the
   dispatch says the planner writes the record. I followed the dispatch. Deciding it properly needs a ruling on whether the
   agent definition's record steps bind on this branch.
2. **Ran the unaffected items and held the rest,** instead of stopping the whole run at the first conflict. I also ran them out
   of the dispatch's cheapest-first order: steps 3, 4 and 7 to 10 were skipped over. The choice was based on this: the conflicts
   touch only items that need my own entries or the find form, and the phone is free now.
3. **Restored `user_rotation` from 1 to 0.** It is not on the dispatch's list of settings I may change. The camera changed it,
   and without restoring it the rest of a portrait run could not go on. I read the restore as undoing my own side effect.
   Deciding it properly needs the planner's word on whether side-effect restores of unlisted settings are allowed.
4. **Dismissed the `ACCESS_MEDIA_LOCATION` prompt with Back,** choosing neither Allow nor Don't allow. Back was the answer that
   changed the least (flags unchanged). The prompt is not in the dispatch.
5. **The "new search" for item 20** was a month change, September to August and back. I chose it because it is restorable and
   changes one key. It added a `cached_searches` row, which the Data section does not list.
6. **Treated item 9's "Understory" as absent at this build,** from the Settings screen and `AppThemeMode.kt`. I did not go
   looking for another switch.
7. **Used system dark mode, not the app's theme setting,** for light and dark, since the app is on System Default. So no app
   setting was changed.
8. **Recorded item 47 as a pass** while saying it could not have failed on this data. I could equally have called it an
   observation.
9. **Recorded item 14's non-touch half** (label, clearance) from a dump, without any tap on the button.
10. **The methods.** Five taps 10 px inside each end of the "Add photo" button. The colour sampling by most-common pixel in fixed
    bands, and WCAG contrast. Which rows to tap for the sheets: the L0a waypoint, the owner's track, and the L0a region. The
    track sheet's Share, rather than a region's, since only tracks have Share.
11. **Wrote the pass conditions before running** and pushed them at `b04dbb2`, so their order is checkable.
12. **Kept the database copies and the app log in the evidence directory.** They hold real locations; whether to keep them is the
    owner's call.

### Decisions I made, second session

13. **How many entries, and in what order.** I read the rulings as allowing the five drafts from item 14, plus Data
    (a)'s one saved entry. So I made the saved entry with a sixth tap and used one of the five drafts as Data (a)'s
    draft; I did not make a seventh. An alternative I rejected was finishing tap 5's draft as the saved entry, which
    would have left four drafts and five created entries, not six. Either reading needed the planner's count to be
    settled properly.
14. **Labelled every draft,** not only one. Ruling 3 says "the DEVICE CHECK label on your draft only", and the general
    rule says every item is labelled "wherever the form allows". I read "your draft only" as "not on the saved entry",
    and labelled all five drafts.
15. **Labelled the draft find in "Your own identification"** rather than "Description Notes", following the L0a finds'
    labelling. The identification field is plain text, with no taxon lookup (`LogEntryDetailScreen.kt:187-193`), so no
    iNaturalist traffic.
16. **Made tap 1 alone** and ran P:320's one-draft Continue before taps 2 to 5, so both of Continue's paths could be
    seen. The dispatch's order would have put the taps first.
17. **Did not swipe open any Records row** for the J5c "tap on an open row" half. Every waypoint and region row is the
    owner's or L0a's. A swipe open is not a delete, but a mis-swipe could be one, and deletes are allowed only on my
    entries. I saw the same code path on my own entry's row instead. Deciding this properly needed the planner's word on
    swiping rows I did not create.
18. **The swipe distances and speeds:** 600 px over 3 s for the partial swipe and 850 px over 2 s for the delete. They
    were chosen from the anchors in `TwoStageSwipe.kt` so that each lands clearly on its side of the threshold. They are
    my method; the threshold itself was not measured.
19. **Automated the Undo tap** (`undo.sh`: dump, find "Undo", screenshot, tap) to stay inside the snackbar's roughly
    10 s timeout. The tap is still a real coordinate touch taken from a fresh dump.
20. **Returned the Journal to Entries and the app to the Maps tab** at the end, to match where each session found it.

## Flags outside scope

1. **Entry reference counts on screen disagree with the database.**
   - The waypoint sheet for "DEVICE CHECK waypoint" reads "Used in: no journal entries" (`65-waypoint-sheet.xml`), but
     `cartography_entry_waypoint_refs` holds one row for it.
   - The album photo the L0a entry keeps has no journal-entry badge (item 19).
   - Both counts are loaded in memory. This process (PID 19584) has run since 02:18Z, before the L0a entry was committed at
     04:41Z. So the counts may never have been reloaded. That is **unverified**; I did not look.
2. **The offline-map stat's pill is invisible on entry cards** in both themes. Its container colour is the card's colour (item 21).
3. **Four selected-chip label pairs fall below 4.5:1** (item 9), and the light card thumbnail is 3.87:1 (item 23).
4. **Opening the in-app camera changed `user_rotation` from 0 to 1 and left the app in landscape** after Back, with auto-rotate off.
5. **Import's permission prompt is hidden behind the picker.** It appears only after the picker is left (item 18).
6. **A "Do Not Disturb is on … Notifications are off for Forager" notice** is in the dumps after each recreation (e.g. `32`, `53`,
   `90`, `100`). It was not visible in the matching screenshot `90-timeline-light.png`, so it may be transient. Not looked into.
7. **Back with the Settings drawer open may act on the Journal beneath.**
   - The Journal was on Records, All before I opened Tools, then Settings (05:08Z).
   - After one Back, a tap that I believe reopened Settings, and a scrim tap to close the drawer, the Journal was on Entries
     (`45-state.xml`).
   - One reading is that the Back stepped the Journal from Records to Entries under the drawer. **Unverified.**
8. **The MapLibre error stream continues:** 17,124 `getMetersPerPixelAtLatitude after the MapView was destroyed` lines in this
   process's log buffer (`124-logcat-app-pid.txt`), as L0a found.
9. **Share writes the GPX into `cache/tracks/`,** and it stays there after the share sheet is cancelled.

### Flags outside scope, second session

10. **The "Do Not Disturb is on …" notice shows after a recreation** in the same bottom position as the delete snackbar
    (`246-dark-restored.xml`, `[34,1693][1046,1922]`, after item 29's Undo). If it queues in the host the delete
    snackbar uses, it could hold a pending delete's snackbar back for its own duration (`PendingDeleteSnackbar.kt:128-131`
    describes that queueing). In this run the delete snackbar showed first. Whether the two share a host was not
    looked into.
11. **The delete snackbar covers the "New entry" button** while it shows (snackbar `[34,1787][1046,1922]` over the button
    `[626,1753][1035,1911]`). A tap there during those 10 s reaches Undo or the snackbar, not "New entry". This was
    observed, not judged.
12. **The drafts banner at 1.3 has 3.6 dp to spare** with a one-digit count. A two-digit count at 1.3 would probably
    wrap, since the label has no `maxLines` (`EntriesDrafts.kt:50-54`). That was not seen: it needs ten drafts.
13. **Each "New entry" tap persists a draft at once** (`CartographyViewModel.kt:115-146`). Opening the button and backing
    out, as the J2 check was worded, leaves a draft behind every time. That is by design as the code reads, but it means
    that exercising the button always creates data.

## Needs a decision

**Resolved.** The planner ruled on all four in `prompts/preserved/2026-09-28-12.md` (quoted in Appendix C): 1 (a), 2 (a),
3 (a), and 4 run, without the Waypoints scroll. Nothing from the second session needs a decision; its open points are
under Flags. The first session's questions, as asked:

The four conflicts, with the options I can see:

1. **Item 14's five touches.** Each tap on "New entry" persists a new draft that keeps the day's candidates
   (`CartographyViewModel.kt:115-146`). The options:
   - (a) allow five drafts, and say what happens to four of them (they cannot be deleted under the Data section);
   - (b) one real tap, with the other four points checked by a press-and-cancel `motionevent` (DOWN inside, MOVE outside, UP),
     which shows the ripple without clicking. That is weaker evidence, and my own method;
   - (c) one tap only, so "across its width" is not run;
2. **P:321, "Log a find".** Opening the form persists a draft find (`MushroomLogViewModel.kt:323-338`), and Back keeps it
   (`:582-630`). The options:
   - (a) allow one draft find, left in place;
   - (b) allow the form's Cancel, which deletes that draft (`:531-552`). That is a delete outside Data (a);
   - (c) check the routing only up to the location picker, and back out before its OK (no find is created, but the routing to the
     Finds form is then not seen);
   - (d) not run.
3. **Item 24 and the title.** A row collapses only with blank text, no hero and no kept track (`CartographyEntryCard.kt:291-292`).
   The form's only text field is the entry's text. The options:
   - (a) the saved entry's text left blank, not titled, with its track and photos withheld in the form (the form offers "Withhold");
   - (b) as (a), but made after 07:00Z, when the phone's date is 2026-09-28 and the day has no candidates, so nothing needs
     withholding;
   - (c) titled as the dispatch says, and item 24 not run.
   - Either way, the draft can carry the title.
4. **Once 1 and 3 are ruled,** the held items 15, 24 (at 1.0, 1.15, 1.3), 30, 29, 26's snackbar half and P:320 need one draft and
   one saved entry. Also for 30: the Waypoints chip has three rows, so the "vertical scroll on an open row" check can run. Opening a
   row is not a delete, but a missed swipe could delete an owner's waypoint and need an Undo. Deletes are allowed only on my entry.
   Should that check run?

## Premises that were wrong

- **"Understory" as a theme to switch to** (the dispatch's "App settings" and item 14 of its list, "Items 9 and 22"): this build offers Light, Dark and System
  Default only.
- **"Save nothing" for "Log a find"** (item 3): opening the form saves a draft find.
- **"Open it and back out" for "✎ New entry"** (item 4): opening it saves a draft, so backing out leaves one, and five touches
  leave five.
- **"Uses your empty saved entry"** (item 8) together with the title in (a): an entry titled in the only text field does not collapse.
- **"360 dp"** (several sources): the phone is 384 dp wide.

## Evidence index

All in `/home/zynergy-labs/Zynergy/device-evidence/2026-09-28-backlog-a/`. They show real locations, contacts and photos, and stay
outside the repository. `snaps.log` records each capture's UTC window and hash. `*-crop-*`, `*-small.png`, `*-half.png` and the
montages are derived from the screenshots listed, and are not indexed. `db-start-query/` and `db-end-query/` are working copies,
checkpointed by `sqlite3`, so their hashes are not evidence. The helpers `snap.sh`, `nodes.py`, `colours.py`, `chipcol.py` and
`contrast.py` are there too.

| File | sha256 |
|---|---|
| `01-dumpsys-package-start.txt` | `a7ec33665d91a62f3c2d9ef78054bcac1c7e5e1e509b292999ac646402feb69a` |
| `02-crash-start.txt` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `03-settings-start.txt` | `8c11f9209981b7b525d964a997f27b65b6be11f5f9fc8d5fce432dc8e633ff36` |
| `10-arrival.png` | `cfa9d379829b0f367a75b502a628b2feb6df281f28d36bebc5fb723386376a51` |
| `10-arrival.xml` | `74df0852f6db752b04d6d8cb6ddf5c3750ca70657530971faa9faf2f19cd3858` |
| `100-seasonal.png` | `db0d411b187077d99169ba8ab290681844fa96fcc94b3fa57caf4502f69c4fb1` |
| `100-seasonal.xml` | `145f5259cc0d381944d0898bd7dd0c1a13fb601a76bf0de55904a133e60b1fa2` |
| `101-seasonal-light.png` | `e695944c72f3f4da380bebd4e2e96f7ff24336798414a7ca13ab726622a816c6` |
| `101-seasonal-light.xml` | `145f5259cc0d381944d0898bd7dd0c1a13fb601a76bf0de55904a133e60b1fa2` |
| `102-seasonal-dark.png` | `cd595efd6f99cc0a0b116106bc30303e1bb54a1fc7aa5a2b5831d137aad62c5c` |
| `102-seasonal-dark.xml` | `145f5259cc0d381944d0898bd7dd0c1a13fb601a76bf0de55904a133e60b1fa2` |
| `103-search-open.png` | `ad55f941b8f73ed03bb23d51a8e436207d88947679724210aecbe998a8721d74` |
| `103-search-open.xml` | `db036f6b33546f42126df2800695ebdecbd1871cf1da03d6fd81d2b9869aec17` |
| `104-month-menu.xml` | `fc6193bca3d36fc36156e5d0999d2c51b9a0b7198d46e564da5e8f2e6fd458b1` |
| `105-after-august.png` | `4d69d42ea539a778a470fafbdd7524b5552af965a9952a8e51344e6cc5f43f2d` |
| `105-after-august.xml` | `4b690a784a5df6d39ee22eee89da2d1c5515d1d00ea6427c36556c0cdaa3fdb9` |
| `106-august-later.png` | `4d69d42ea539a778a470fafbdd7524b5552af965a9952a8e51344e6cc5f43f2d` |
| `106-august-later.xml` | `4b690a784a5df6d39ee22eee89da2d1c5515d1d00ea6427c36556c0cdaa3fdb9` |
| `107-seasonal-after-close.png` | `266db1359ef336276d8de09c7835aa12e3ddb0855064e793b1d8faaa59cabce8` |
| `107-seasonal-after-close.xml` | `a638d4f9468671e638f030df573aacbabf68ffc96030104e62846032ae8db71a` |
| `108-month-menu2.xml` | `fc6193bca3d36fc36156e5d0999d2c51b9a0b7198d46e564da5e8f2e6fd458b1` |
| `109-month-menu3.xml` | `22d6fc892f19aab0ca40ec0151c44fbb355376ccfd4f596ffd286bfd0202ffca` |
| `11-dumpsys-window.txt` | `6db29fd894a31dbc6c6facee2e40baf9c79318a759921b0eb92d41ba0288eae3` |
| `110-seasonal-restored.png` | `fc086ce7ad2c91e14abd8277b65def1a3d35ca841ae65fe08ea777bcfe402b79` |
| `110-seasonal-restored.xml` | `eddf5b5eb3713531996bb712ffc83588882e70733972e2473cda079ebfa10609` |
| `120-final.png` | `c08a498b07a3f425a84842415c2e0d3b3505fbeb88174ee3a8b45c4a711d63d3` |
| `120-final.xml` | `cc024f327cb10d9cb0e0c886d3c6bc987fdac72467b4c0d3e041dbab594af323` |
| `121-crash-end.txt` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `122-dumpsys-package-end.txt` | `d9dee23c9f0802a7aa70f26aa4fe94d5106d855c28b2fabf09e583141d3426e1` |
| `123-settings-end.txt` | `e01cf967ceff03effdc46be77e30e54cca4d9d2e16df0b332df926ad8d68bc2a` |
| `124-logcat-app-pid.txt` | `8d26f9e89cd9dd14d985068d0f342abd995bf5567a04aff272bc9052dcbb7b0d` |
| `20-journal.png` | `dbcab2eca68fcd3307a57b323923465961d3396c7f91f09c95a885846aa08d22` |
| `20-journal.xml` | `eef9254400b2081fc2ea2d5b13840e4f05835bb000cb94220b7c16eb6c18b264` |
| `21-entries.png` | `50bcda1cf6ab64634db3d8a2de7f38495128089f45c41fe59e2227450c58c452` |
| `21-entries.xml` | `e392aaa91b238a261fec01a5be2c347187172b7b8e275cf5a24ec9247ea5fddf` |
| `22-records-all.png` | `ef221fcba6ebe92ab33bca471504818de84d8f52ea516ff346ff3e6acc0e32e4` |
| `22-records-all.xml` | `9549b0782d5611d114db08ed57b56314b0863fee3da4824a4befed5716f529c8` |
| `23-chip-finds.xml` | `8ec70a3f613f2df629fd0b3b2f112dcbce33ec5683615648e3ded583c46409fb` |
| `24-after-back1.png` | `72b09d0866cf8308c60607a448d663dfe893a9d8ebead667671a14c75c938fa1` |
| `24-after-back1.xml` | `ee2898cd219fd21bc54e241513304570432aea263d9a7daad03a57d048fb3cb3` |
| `25-after-back2.png` | `50bcda1cf6ab64634db3d8a2de7f38495128089f45c41fe59e2227450c58c452` |
| `25-after-back2.xml` | `e392aaa91b238a261fec01a5be2c347187172b7b8e275cf5a24ec9247ea5fddf` |
| `30-all-fs100.png` | `72b09d0866cf8308c60607a448d663dfe893a9d8ebead667671a14c75c938fa1` |
| `30-all-fs100.xml` | `ee2898cd219fd21bc54e241513304570432aea263d9a7daad03a57d048fb3cb3` |
| `31-chips-scrolled-fs100.png` | `5b161a44452f26c36a7f66cfb0c98389f6de58338c525f98e33a3f509ee912da` |
| `31-chips-scrolled-fs100.xml` | `54059657c2abd776bfabea7c60c5436dd2db8511fd8df69082600984c903c7d9` |
| `32-all-fs115.png` | `53480ac647a9f73d9d88c5395685515b4f2410b3b67b3d749afbf752d7448a99` |
| `32-all-fs115.xml` | `6be1d7d8e2e69a992114e0f24ce9d912dac45d2cab6ef684eb5116e606d95d3d` |
| `33-chips-end-fs115.png` | `5b9aae986f85878560f517d454104c50a71b298abab68c137ac63fcf181da766` |
| `33-chips-end-fs115.xml` | `1d4029cd5d2ffc62b671846562fed054ebbed0121fa179c746341109c45f5f02` |
| `34-chips-start-fs115.png` | `6db7d429a50abd6d47b4dbfdd3ac92d6842a6ff8f57f8f1b531cca811890dcc1` |
| `34-chips-start-fs115.xml` | `57f2f40eddfaa98123a48d640fb854d39b8440aa92ca687381d0fac7597d9896` |
| `35-after-fs-restore.png` | `0cdf9aab24dddb4b175374215a2806032afee8a392b45c77d0d23ca5c41c393b` |
| `35-after-fs-restore.xml` | `cbfd0f9fed722f95f9d088e73dd05bf418330a4cbe44fa01309abd85cc2e8337` |
| `40-tools.xml` | `41fb762bae523e558c9a0c6cb0b5e73913ffbf9daacdda95cf1c0d188fda9075` |
| `41-settings.png` | `378e5acf64ad04e65259df2015adc132cb0d13811dc74b30a92b68644b4f6eb3` |
| `41-settings.xml` | `53cbb94f709ab52015f6b8dbf2eab0c8ebfa21fbda289c2f184838482c9cec3c` |
| `42-back-journal.xml` | `53cbb94f709ab52015f6b8dbf2eab0c8ebfa21fbda289c2f184838482c9cec3c` |
| `43-state.xml` | `53cbb94f709ab52015f6b8dbf2eab0c8ebfa21fbda289c2f184838482c9cec3c` |
| `44-state.png` | `82d458b1442457929d938546f82d948991809e232b94b10013baa5d5c2d3a086` |
| `45-state.xml` | `e392aaa91b238a261fec01a5be2c347187172b7b8e275cf5a24ec9247ea5fddf` |
| `50-dark-all.png` | `9f9d8218b29234386a42ff9ec6004a307e5dade96f75cc8dc99d3a68e84598c1` |
| `50-dark-finds.png` | `bb97495f8445f8e315a0dd1dc3369fea8b4859c8ad93267cacf17716510dc18f` |
| `50-dark-finds.xml` | `8ec70a3f613f2df629fd0b3b2f112dcbce33ec5683615648e3ded583c46409fb` |
| `50-dark-offline.png` | `aed2648a1bbdbcb1730e954171db6bc5c410269ac4a79292c056d7f418c344bd` |
| `50-dark-offline.xml` | `3304b707f514b7df260d79396dc3f0482adea283407e4d373e1ab7ac854860d5` |
| `50-dark-scrolled.xml` | `79dd72ecf0122612745ca3243a6b24ba5acce7695b6edeb69d817d248450e391` |
| `50-dark-tracks.png` | `889e9f83a0a58211b7dba4af5879df822a4dd3dc944bcf0e9c803c39008a417c` |
| `50-dark-tracks.xml` | `2021e3bbd8ebf1476ce2a103110fbd842f43c231b9a60a5c6b58ef2d9ff90541` |
| `50-dark-waypoints.png` | `4a53e8a9552dac9afcd11b49d27135cb7dc7a78bca8e56a1e3e4334adb705062` |
| `50-dark-waypoints.xml` | `6c84f58d26a0d7724678675aee7b26c3e28b8dc0675ad5008f26c089db340bf8` |
| `51-light-after-toggle.png` | `f0fd170e5052995c669d7788f58b47351ad7bf00ff899fde28f2268c83cac190` |
| `51-light-after-toggle.xml` | `0d2b08d972fcbc95baa9947b5720d29600a8385b0db4873b1abc30426830870d` |
| `52-light-all.png` | `71963edbf8211b0fd99b4cb7bb9a61dffbcdd0df927dc8ceef4744d163112475` |
| `52-light-all.xml` | `85bb0b8b6e6fd6493bc6b5f4dbb5f2133d2ac9d57bcabbe9511f4fed56602fba` |
| `52-light-finds.png` | `32931180112408dd5869ed226cf15632d590e6292c6f304feee42a723a361f14` |
| `52-light-finds.xml` | `8ec70a3f613f2df629fd0b3b2f112dcbce33ec5683615648e3ded583c46409fb` |
| `52-light-offline.png` | `f0fd170e5052995c669d7788f58b47351ad7bf00ff899fde28f2268c83cac190` |
| `52-light-tracks.png` | `e583b92001c5781103085a6f567792dce5739e37336216ef36b5eb57f553ef99` |
| `52-light-tracks.xml` | `2021e3bbd8ebf1476ce2a103110fbd842f43c231b9a60a5c6b58ef2d9ff90541` |
| `52-light-waypoints.png` | `31f678c066f49b37bbb5831e614333f6aa5a05f6f65714312e3a75ec2d14d36b` |
| `53-dark-after-toggle.png` | `9654431556ceeffbc5f3a517ab543666717221f414a6c1147e4ca0f438f0f3cc` |
| `53-dark-after-toggle.xml` | `b9055a2c7485f8090e28fd53350e8576339682c4ac3130b92dba7958ee8823e9` |
| `60-track-sheet.png` | `979909deea3781edc9668b53dc38a3a1be5d80d68e3a88cfcd22da5510b08189` |
| `60-track-sheet.xml` | `192c0a41f84c36d11984cd3cffa2e342bfcb8d0c538f42518b3ad608db71b5f0` |
| `61-share-sheet.png` | `cdb89eb6845a9c12c554a4a897d9aa4730f3c3b66edc1e2481af738e461833ad` |
| `61-share-sheet.xml` | `6a4109a74abcbe002b4b1bdec14f6069eb1abd93541c977d571cf3a87a1c8fca` |
| `62-after-share-back.xml` | `192c0a41f84c36d11984cd3cffa2e342bfcb8d0c538f42518b3ad608db71b5f0` |
| `63-sheet-dismissed.xml` | `2021e3bbd8ebf1476ce2a103110fbd842f43c231b9a60a5c6b58ef2d9ff90541` |
| `64-waypoints.png` | `04c0d5d9e79323d18a320d51557f187c783feab83f91f8193afba174ce9892ff` |
| `64-waypoints.xml` | `6c84f58d26a0d7724678675aee7b26c3e28b8dc0675ad5008f26c089db340bf8` |
| `65-waypoint-sheet.png` | `a05e1c8fd80424a22ca322cbf2c19197e2bb0bfacd815af97c2f1ca51dab0036` |
| `65-waypoint-sheet.xml` | `408da6e6674821d715ba9ed9fcbc00b5194a193ffcecab7e28e24990e2315621` |
| `66-directions.png` | `cdfadb95fe111db842bf5a62cad1b4dd3fdc87490f60ed83704ae0991a4f8dc3` |
| `67-after-directions-back.xml` | `0dec3b145a28c1eb6c2c16f3674876b7f341532733e0423ddab45e1b14def2bc` |
| `67b-maps-state.png` | `58db28cde2a579fcc01b2216591fc1cee976aaed6684d5968d6a2539c638ebee` |
| `68-after-directions-back2.xml` | `408da6e6674821d715ba9ed9fcbc00b5194a193ffcecab7e28e24990e2315621` |
| `69-offline-list.png` | `8e8bcd962621dfaf501967f82974acdb501a2216d21485b6b9b4823ba77937e7` |
| `69-offline-list.xml` | `3304b707f514b7df260d79396dc3f0482adea283407e4d373e1ab7ac854860d5` |
| `70-offline-scrolled.png` | `549e857698ffa222ca7f90841541a4979c83abf69f1bfa492d7becc0363dafec` |
| `70-offline-scrolled.xml` | `118271d44a699b479c08dd90d443fc5867ad3e1ebb65c9792aac3c85ab0a6605` |
| `71-region-sheet.png` | `dc6fae413868828de70485919d853775e38b7bbd3389636bae2526dd64cb8ddd` |
| `71-region-sheet.xml` | `074f3f931d0b69d3db0459222995570ed8efc5cd275e737c38640e50e41c0aca` |
| `80-album.png` | `6b6312e71f4c4c446797cc0966e01d9734e04a33d18a1afa3e0b02c2ac6289c0` |
| `80-album.xml` | `8352fec5617721686af4ceccfc0e9c583a60fe81c53d3c85aaf3132cb68d9a99` |
| `81-addphoto-after.xml` | `8352fec5617721686af4ceccfc0e9c583a60fe81c53d3c85aaf3132cb68d9a99` |
| `81-addphoto-tap1.xml` | `69ff2c6b8af78a2faf1448af0514a0ab4e51dd2b856faaac4af903d36274dd5a` |
| `81-addphoto-tap2.xml` | `69ff2c6b8af78a2faf1448af0514a0ab4e51dd2b856faaac4af903d36274dd5a` |
| `81-addphoto-tap3.xml` | `69ff2c6b8af78a2faf1448af0514a0ab4e51dd2b856faaac4af903d36274dd5a` |
| `81-addphoto-tap4.xml` | `69ff2c6b8af78a2faf1448af0514a0ab4e51dd2b856faaac4af903d36274dd5a` |
| `81-addphoto-tap5.xml` | `69ff2c6b8af78a2faf1448af0514a0ab4e51dd2b856faaac4af903d36274dd5a` |
| `82-camera.png` | `9a421063411d7d9a344aef66a2cac659c52dc33a6097308ec5c439e5b57debbd` |
| `82-camera.xml` | `b577d5a174b52e530567ba17306f4817fc710880746bdfff259a58e673f4916a` |
| `83-after-camera.xml` | `f7e8f9dbf61c9a1092713b1f9452a6aaf8f2b4418ac11ab37323ba5b5048f195` |
| `84-portrait-restored.xml` | `8352fec5617721686af4ceccfc0e9c583a60fe81c53d3c85aaf3132cb68d9a99` |
| `85-picker.png` | `7908cb7229c19bba1e7396cc4b19e03cb0bf8b431cedbbe96991d98ed2fd067e` |
| `86-after-picker.xml` | `da053ef36e44ed996b8860bc03504e87cbc51ae6cf5df46b7aef9a1af4e70ac4` |
| `87-permission-dialog.png` | `0669efa718415d0968d96e06a34c1a2f81d57919fead195fba15b5666bf83811` |
| `87-permission-dialog.xml` | `da053ef36e44ed996b8860bc03504e87cbc51ae6cf5df46b7aef9a1af4e70ac4` |
| `88-after-perm-back.xml` | `8352fec5617721686af4ceccfc0e9c583a60fe81c53d3c85aaf3132cb68d9a99` |
| `89-album-light.png` | `725b72e5947e885294357e51c2373591ca70e56d85d0ed55b0f53b073e99c4b4` |
| `89-album-light.xml` | `4079b4006210a66eeb32681f2146f16fb87a77043be2744c95d7842dfef9c637` |
| `90-timeline-light.png` | `c40a2f8ebc974cbaa3f79363e6ce74ff0f169510ace93c448c38cc611bb864b7` |
| `90-timeline-light.xml` | `f8f529884239f48b13f8b7e5cf964d98482fcc1fe753d5ebf26fd7bc2486102f` |
| `snaps.log` | `2a73eaac0982851215636e2e191883dc225c5123db830c9db7175082acce08bf` |
| `db-start-raw/forager.db` | `fe5d658f20a266183d92b391bd4c18e9b540a8384da49715e4d3d1f40f28c715` |
| `db-start-raw/forager.db-shm` | `aef8d7281136dcace2a31ffaabab6d3da2261ff1d8e953982854458c195c0f7e` |
| `db-start-raw/forager.db-wal` | `0778bf5245c0c6acede3ed544617fce3295a1ee320d6a7366bb601a6e6420cff` |
| `db-end-raw/forager.db` | `fe5d658f20a266183d92b391bd4c18e9b540a8384da49715e4d3d1f40f28c715` |
| `db-end-raw/forager.db-shm` | `98ec6c5a66981a9065ed05fc8700ab64c3ad48b2c6ae289a53807184a6541c48` |
| `db-end-raw/forager.db-wal` | `8a58ee765ad8cd6503535d1aa745761e616d6bb4c7ba0fdbfb33cf9feb02586c` |

### Evidence index, second session

Same directory. `*-crop-*`, `*-small.png` and `*-montage.png` are derived from the listed screenshots and not indexed; `db-*-query/` are working copies. `snaps.log` is hashed as it stood when this index was written.

| File | sha256 |
|---|---|
| `200-crash-start2.txt` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `201-dumpsys-package-start2.txt` | `f7e8cc80d7bb12b05f2dbd6f837fbe2022ee9f925f80edc02d22b64e8008331a` |
| `202-settings-start2.txt` | `7d5d38e99c12dbd9879b727a3884d328bc7ea5d3e3bc6563639ae1944c5e7671` |
| `210-arrival2.png` | `09c21d26492a2da23e72d91228aad2b45b182443fff3763f59426cc028779989` |
| `210-arrival2.xml` | `276a15f91037aa8b8532ab7046ed965b4d3dcf91b23b9ab91fbb77cb2719a582` |
| `211-journal.png` | `40bb7f97d87dcdb46640eeb83fff06421c0eae88dbf2b3d5bd1d6536b1190144` |
| `211-journal.xml` | `e392aaa91b238a261fec01a5be2c347187172b7b8e275cf5a24ec9247ea5fddf` |
| `212-newentry-tap1.png` | `dca24d9f959ed83d62ce5e36f422ad1a40abf260679b9b4fcd5f97e6725b009f` |
| `212-newentry-tap1.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `213-draft1-labelled.png` | `5afec683308aaff005f4a63d41bed34d19abb15b062f6647670d1cb34602da22` |
| `213-draft1-labelled.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `214-after-back-a.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `215-entries-1draft.png` | `64d9cfca368b838774c633c9c29415874cdef600ad390418e78bbb767dab7a4e` |
| `215-entries-1draft.xml` | `12d8225aa2c7878dbaefa1946b018d842ae068a4182462c3424e0f0a685166f6` |
| `216-continue-1draft.png` | `f25041057afd3c84ad1e60ed91748121ac8895187101e395f278f91c171f6d26` |
| `216-continue-1draft.xml` | `dbe3b3627a5893b9801836d8e5f586ae4f921524eb94c1ecb310a3c7424931b6` |
| `217-back-from-continue.xml` | `929331c96319e36cbbf3fb96a8190b4e4fc05af7ab9722896ec726ad54a2b02c` |
| `218-newentry-tap2.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `218-newentry-tap3.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `218-newentry-tap4.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `218-newentry-tap5.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `219-draft2-labelled.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `219-draft3-labelled.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `219-draft4-labelled.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `219-draft5-labelled.xml` | `9dce863b9227ab9220062573cc2faa8ae29abb9401ea99f3a51fe2a93ae92143` |
| `220-entries-after-tap2.xml` | `2362799fff79c7c007e7763aabed663148408e54d392113784ff1131db31a09f` |
| `220-entries-after-tap3.xml` | `e12b36783dcc00c531797f09d2194ffe74b23c8c9e35e1f7a1f4f2b2bd059d3b` |
| `220-entries-after-tap4.xml` | `99e2e4eaa82f4fc0611c47229c5e2bd30ec30cf02a00573b1aabd3eafefd49d1` |
| `220-entries-after-tap5.xml` | `bc26335fc952b75ef66b6b99580e635ba11bbae798dd7681fb467216d8802407` |
| `221-banner-fs100.png` | `8f304fb0895ad84aed8e4f59e586fdf537131949eb352e2575ac0bcb41e5949e` |
| `221-banner-fs100.xml` | `bc26335fc952b75ef66b6b99580e635ba11bbae798dd7681fb467216d8802407` |
| `222-card-opens.xml` | `eef9254400b2081fc2ea2d5b13840e4f05835bb000cb94220b7c16eb6c18b264` |
| `223-back-from-card.xml` | `6b77affd51a6f558900a4a6cc5dd4862510817dd0bd326c74bc5633fa463d791` |
| `224-continue-5drafts.png` | `22a6ed5a97ecd7b66a391489ed64275d2739e96b24856568ef9a1406ca967451` |
| `224-continue-5drafts.xml` | `75c58abccfe82fd17308ebbd65e995b95b91a8343e674cd2926a6bfe64869212` |
| `225-back-from-list.xml` | `6b77affd51a6f558900a4a6cc5dd4862510817dd0bd326c74bc5633fa463d791` |
| `226-album-5drafts.xml` | `df4f6d3db73abc46d0c309f12a62fe7dbef470dde1017922dcade56698f52e3c` |
| `227-album-list-open.png` | `22a6ed5a97ecd7b66a391489ed64275d2739e96b24856568ef9a1406ca967451` |
| `227-album-list-open.xml` | `75c58abccfe82fd17308ebbd65e995b95b91a8343e674cd2926a6bfe64869212` |
| `228-light-after-toggle-list.png` | `bae142053f75f55940ae7ac7fe7ac35023129e32b32e6295145f6a1d9ebdd8e5` |
| `228-light-after-toggle-list.xml` | `b3894e694b48bef41adcf11690642b27081e288ea2561a9d605c9bd03ac9bdf9` |
| `229-light-back-from-list.png` | `003cf4d47ad7c99e8a5529e375ec02bec2574ad8be85e610190c13b1e990868d` |
| `229-light-back-from-list.xml` | `df4f6d3db73abc46d0c309f12a62fe7dbef470dde1017922dcade56698f52e3c` |
| `230-dark-timeline.xml` | `a3c8091cb7377fe2fb86445c3ff40640fa50a8a9c2bca5d86da7aa4db08068e0` |
| `231-dark-timeline2.xml` | `6b77affd51a6f558900a4a6cc5dd4862510817dd0bd326c74bc5633fa463d791` |
| `232-saved-entry-form.png` | `ede7ad8aa76d2fdd0aa7a5cea95e262a2b9eb2d39a9da1276144ed02643a2956` |
| `232-saved-entry-form.xml` | `3dcd2f2edd7fb87a95664d84460176ca676690bd4c76a864fcd29318798a6636` |
| `233-after-finish.png` | `94ab8d80d571388ec49965b6a621016381d26222e3f5959e4e40117d2b898520` |
| `233-after-finish.xml` | `7d6a2e6361c626016c6eff69dec87745afef7c6911e2bd45077debe94812bce6` |
| `234-timeline-with-row-fs100.png` | `b03efa9570f9fdfac335792a7d2d4baa6f43b0975b1506efe75adda3754cf4b3` |
| `234-timeline-with-row-fs100.xml` | `a7250f1b45dc9ce6752688c1c42a402c9ad1554f445231ce6440785aadf45f0a` |
| `235-timeline-fs115.png` | `19bb69388736cad63c19ee9a0d50745353570d2580c67c1961c3ee2e65d9c80f` |
| `235-timeline-fs115.xml` | `48cd3e8192cee6f435b9a6b2e66539d51ed079934e750e9d48c690f05360c49a` |
| `235-timeline-fs13.png` | `a4baafb69b531c6880e4444ba5afc612e9c5fa42dfa32e9c64f9845bf26f3003` |
| `235-timeline-fs13.xml` | `83a4a7ed8dee63f3a833ceb27eb7215dfa022396fa6466d4bd0ffad2c06d4be3` |
| `236-fs-restored.png` | `fda906ebda7336d3aab71064ab1d45a5403084c86a80e75ac42f9fcdcfcd1cdc` |
| `236-fs-restored.xml` | `50bc440da84074f3682e0e1c888f00c75fcf440a2f9b982552f1f60014ef5597` |
| `237-row-open.png` | `a4d6be172229e0a80935c5f66feb592dd5305fd764fb11ba2357db92c19fa5f3` |
| `237-row-open.xml` | `3c997de17411aca395b266971bb543d9c88cd1863bf91ec7b544f32e363e7e00` |
| `237b-row-open-png.png` | `a4d6be172229e0a80935c5f66feb592dd5305fd764fb11ba2357db92c19fa5f3` |
| `238-tap-open-row.png` | `4edda48add7c845c58182d7f345aabf4db31437d752720e0580b9e737ba00963` |
| `238-tap-open-row.xml` | `a7250f1b45dc9ce6752688c1c42a402c9ad1554f445231ce6440785aadf45f0a` |
| `239-tap-closed-row.png` | `ace4d527795b14490a01ec90adbee44fb33763ad1a25aff63db3622b1c05c353` |
| `239-tap-closed-row.xml` | `85c196a53396a2e3d15e0413b7398411274e6522840d27ea5e3bea8bd5b83205` |
| `240-before-delete.xml` | `a7250f1b45dc9ce6752688c1c42a402c9ad1554f445231ce6440785aadf45f0a` |
| `241-snackbar-delete1.png` | `0a506059050171c482e5966025369b8e08c8053b5ad7971b9703dade463d9325` |
| `241-snackbar-delete1.xml` | `4c9f71e16ae11b78adffc24714957987ad5f85754817d330627e7a582e9328ff` |
| `242-after-undo1.png` | `b2485edcde8a1d6dd8168a0f319b4190911defc1fb09a6df5c0d2365ff2963c5` |
| `242-after-undo1.xml` | `a7250f1b45dc9ce6752688c1c42a402c9ad1554f445231ce6440785aadf45f0a` |
| `243-snackbar-before-toggle.xml` | `4c9f71e16ae11b78adffc24714957987ad5f85754817d330627e7a582e9328ff` |
| `244-snackbar-after-toggle.png` | `5c900c7fd3806715ca03143f1cb75ed5a7ff5fdc20b42b9be4d459d9320d5bb4` |
| `244-snackbar-after-toggle.xml` | `4c9f71e16ae11b78adffc24714957987ad5f85754817d330627e7a582e9328ff` |
| `245-after-undo2-light.png` | `be23e5f77d5ac018ae6d9899148555650c760e953865d6cb767cad89a89a0af8` |
| `245-after-undo2-light.xml` | `50bc440da84074f3682e0e1c888f00c75fcf440a2f9b982552f1f60014ef5597` |
| `246-dark-restored.png` | `7c7a9bf148e22a7579c6dcaeb52bbeb48141d97b54c5a3fa20e5d92e539f66b2` |
| `246-dark-restored.xml` | `50bc440da84074f3682e0e1c888f00c75fcf440a2f9b982552f1f60014ef5597` |
| `250-maps.xml` | `28e0caa89f7a952776c2eec32cca42e7a90902f39bf53d77fc7f57878d521a21` |
| `251-add-tile.png` | `6fa9f7dcd97e42d832f73cec9c2128d3c173261d04b921b3247cc62322c627d0` |
| `251-add-tile.xml` | `29438b7e544efff016e752ea4ee566d6d898a9c036c02a8ee3e4b04e6794023a` |
| `252-picker.png` | `cf745cf55ac07835a89df7aa6a444787ab069452b3d46421076e68edd7a3ae6b` |
| `252-picker.xml` | `dce13dbd40b5e4fa0790d2aae779f00745771d64b008d7b10ba3deabf9e3df7c` |
| `253-after-ok.png` | `e125eda3e72703e443d5521621da030d32f884bc2667475888ecca26fc21cba2` |
| `253-after-ok.xml` | `09ffda32cb0ee10c59d191bec480f268474e5428d64cf9d7f019ca6ba80ce9b2` |
| `254-find-labelled.xml` | `0e938288b31718eee75c05283828306191da37e53b7eed1e54bbe3584679495a` |
| `255-after-find-back.png` | `978ef41cb53fb752877575034070e65241f493f8fb9240274bfe661c8cff7cd8` |
| `255-after-find-back.xml` | `55dde7df257f01b20ba5f9a4d9ee18fdfa6335d7e6df2eb74d90a4cb4e6434b0` |
| `256-after-snackbar-gone.xml` | `089d3f2acb6e72e69f473c0128194d1b900301a9ddae4f6420f4535a605ab8c2` |
| `257-journal-entries.xml` | `f61150b7fbd966430f6226c3748452f71d39d4c9b9ca6a4b62d60f87755c1c01` |
| `260-final2.png` | `f35119a67fe110da354a36199f8470d627c8e96b784ed1f1d2c4857d22860958` |
| `260-final2.xml` | `28e0caa89f7a952776c2eec32cca42e7a90902f39bf53d77fc7f57878d521a21` |
| `261-crash-end2.txt` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `262-version-end2.txt` | `425b95878f3a198c34491c321e43b6c952285e034966ed8aa830ab198e003a23` |
| `263-settings-end2.txt` | `14e12eda5e60d9d4a1347323755e1e76dd1e18f9bfcb96f162d48317636db286` |
| `264-logcat-app-pid-session2.txt` | `a7bc4fc0c8512f30b8bfb40b159ce7af64a3730d2993fb8e4655487498d94834` |
| `db-start2-raw/forager.db` | `fe5d658f20a266183d92b391bd4c18e9b540a8384da49715e4d3d1f40f28c715` |
| `db-start2-raw/forager.db-shm` | `98ec6c5a66981a9065ed05fc8700ab64c3ad48b2c6ae289a53807184a6541c48` |
| `db-start2-raw/forager.db-wal` | `8a58ee765ad8cd6503535d1aa745761e616d6bb4c7ba0fdbfb33cf9feb02586c` |
| `db-mid-raw/forager.db` | `cd2cb2046a458578f0cd3d0c2f351de142222fe7961271cfb6eb6d470285d5c4` |
| `db-mid-raw/forager.db-shm` | `4f07ac7be95f0d714baa991c6f67a9efe108fb04493cdc8f66e52706adfb58fd` |
| `db-mid-raw/forager.db-wal` | `f5f7b73f45ffbc1817ba19017747991ef6b2eb152cabedc2fd909ec480d125fd` |
| `db-end2-raw/forager.db` | `d8c365df9396a7142c8a1a1c0367e7ee4824cf905b0adc800e84751148032f16` |
| `db-end2-raw/forager.db-shm` | `9cc8c596fdfe3d4ea2c22d5b274a009d359b9a529d331fdf14a046fe3869cf5a` |
| `db-end2-raw/forager.db-wal` | `b8984504cc8858873564907eebdf37ff817ee7f28e11a8bde1d4aa7e799f63ea` |
| `undo.sh` | `9807ae2d1028076749a8540b0dd9ca85c7c992582ca671e9b05cbb605965c8b6` |
| `rowbanner.py` | `34260c02c7e6dea97242a0433df5d3ab257284b44d0051121006c53e49e014ea` |
| `counts.sh` | `eb43d8d965e3099cba94cb11988ccd82a98de2803487e79c7982ad3e36c00f1b` |
| `snaps.log` | `fe0fd584310fe166bd9e3420aaa1788b0a092c0e54133facf5584ed2aafefac4` |

---

# Appendix (unchanged from `b04dbb2`, except that four headings gained an "Appendix" prefix)

## Appendix A. Planner's launch message (quoted; part of the dispatch)

> **Changes since the dispatch was written.** Each is a planner message and part of the dispatch; quote them in your report.
>
> 1. **Test data already on the phone.** The L0a check left owner-authorised test data in place:
>    - a "DEVICE CHECK waypoint";
>    - two finds, "DEVICE CHECK find 1" (with a photo) and "DEVICE CHECK find 2";
>    - the owner's kept track;
>    - an offline region "DEVICE CHECK";
>    - a committed day entry, dated 2026-09-27, whose text is "DEVICE CHECK 2026-09-28 (L0a)".
>
>    See the "Created data" section of `docs/audits/2026-09-28-l0a-device-check-run-record.md`. You may **use** these as data for your items: read them, open them, and view them in lists and sheets. Deletes stay limited to the entry you create yourself, and always end in Undo. Your inventory counts them.
>
> 2. **Do not run `adb logcat -c`.** The previous coder cleared the crash buffer that way by mistake. Read logs with `-d` only.
>
> 3. **No iNaturalist activity.** Do not sync, upload or share anything to iNaturalist. If the app offers to upload a find, decline it.

The launch message also said: "The L0a device check has finished: terminal `2026-09-28-09` at `46186bb`, with its run
record merged at `8e4fe67`. The phone is free, and you are the only coder on it. The L0b coder is building on this
machine in parallel, so: no Gradle, no emulator, no install." `git ls-remote origin device-l0a-2026-09-28` reads
`ecf429a`, the commit `8e4fe67` merged.

## Appendix A. Inventory (read-only, 04:59Z, before any item)

Read from a byte copy of the app's database (`adb exec-out run-as … cat databases/forager.db`, `-wal`, `-shm`
into `db-start-raw/`, queried only as a second copy in `db-start-query/`). Counts only.

| Data | Count |
|---|---|
| Journal entries, saved (`cartography_entries.isDraft=0`) | 1 (the L0a entry) |
| Journal entries, drafts | 0 |
| Finds (`mushroom_log_entries`), all saved (`isDraft=0`) | 2 |
| Finds with a photo (`log_entry_photos`) | 1 |
| Album photos (`log_photos`) | 3 |
| Album photos linked to a find | 1 (it is also the one photo the L0a entry keeps) |
| Album photos linked to nothing | 2 |
| Tracks | 1 (23 `track_points` rows) |
| Waypoints | 3 |
| Offline regions | 1 |
| Months the entries span | 1 (2026-09) |

All of the finds, the region, one waypoint and the entry are the L0a test data; the track and its two waypoints
are the owner's, kept by L0a.

## Appendix A. Found before running: four conflicts in the dispatch (stop-and-ask; see "Needs a decision")

Reading the code before the items turned up four places where the dispatch cannot be followed as written. The
items they touch are **held**, not run, until the planner rules. Every other item is run.

1. **Item 14's touches create entries.** "✎ New entry" calls `onStartEntry(LocalDate.now())`
   (`ui/log/CartographyScreen.kt:406`, `:497`), which **persists a new draft on every tap**, with every one of
   the day's candidates kept (`ui/log/CartographyViewModel.kt:115-146`). "At least five taps spread across the
   control's own bounds" would therefore make five drafts. The Data section allows one draft and one saved entry,
   and deletes only of those, each ending in Undo.
2. **"Log a find" (P:321) cannot be left with "Save nothing".** The map's route calls `onStartLogEntry`
   (`ui/availability/AvailabilityCompactScaffold.kt:733`), which is `MushroomLogViewModel.onStartNewEntry`: it
   **persists a draft find before the form shows** (`ui/log/MushroomLogViewModel.kt:323-338`). Back is
   `onLeaveEditingIncidentally`, which keeps a brand-new find's draft (`:582-630`); only the form's Cancel deletes
   it (`:531-552`), and a delete of a find is outside the Data section.
3. **Item 24 needs an entry the dispatch's title prevents.** A row collapses only with no hero, **blank text**
   and no kept track (`ui/log/CartographyEntryCard.kt:291-292`). The entry form has no title field (L0a record,
   step 4(e)), so "titled `DEVICE CHECK 2026-09-28`" means typing it into the entry's text, and the entry then
   draws as a card, not a collapsed row. Separately, a new entry made before 07:00Z is dated 2026-09-27 and keeps
   the day's track by default, which also stops it collapsing.
4. **Items 15, 24, 29, 30 and P:320 use entries this run has not made**, and making them depends on 1 and 3.

---

## Appendix B. Pre-registration: pass conditions and predictions, written before each item was looked at

### Item 1: build, baseline, inventory
- **Pass:** `versionName=1.0.1192+g24589349`; `logcat -b crash -d` read at the start and at the end, a new
  Forager line being a stop; the inventory above.
- **Prediction:** pass, crash buffer empty (it was empty at the end of L0a, 04:52Z).

### Item 13: Back from a single-type chip
- **Pass (from the code):** on Records with a single-type chip selected, Back selects All
  (`ui/log/RecordsTab.kt:215-217`, `BackHandler(enabled = selectedTab != ALL …) { selectTab(ALL) }`). From All,
  that handler is off and Back falls to `JournalTab`'s `selectedTopTab = CARTOGRAPHY`, i.e. Entries
  (`ui/log/JournalTab.kt:354-356`; P:302 "Back from Records steps to Entries"). A third Back, from Entries with
  nothing open, falls to the Activity-level handler (not part of this item, not pressed).
- **Observable:** the chip row's `checked`/`selected` state and the Entries | Records switch in a fresh dump after
  each `input keyevent KEYCODE_BACK`.
- **Prediction:** chip → All → Entries, as the planner predicts.

### Item 18: "📷 Add photo" in the album (portrait)
- **Pass (J2:560-566; `ui/log/EntriesAlbum.kt:195-222`):**
  - the button's text reads "Add photo" in full with its icon, not ellipsized (the dump's text node is the whole
    string and its bounds sit inside the button's);
  - each of five taps across the button's own bounds (both ends, the centre, two between) opens the menu with
    "Take photo" and "Import"; each menu is dismissed with Back before the next tap;
  - the menu's bounds lie inside the window `[0,0][1080,2316]`, and above the navigation bar (y < 2181);
  - Take photo opens the app's camera (the in-app camera window), left with Back; Import opens the system photo
    picker (`dumpsys activity` names its package), left with Back. No photo taken or imported;
  - the button's bottom is above the top of the navigation-bar inset, y = 2181 px, and above the bottom
    navigation's top.
- **Prediction:** pass. The button sits inside the Journal content above the bottom navigation
  (`CartographyScreen.kt:520-526` comment), so it clears the system bar by construction.

### Items 8 (portrait half) and 45 (portrait): the Records chip row at font scale 1.0 and 1.15
- **Pass (J1:297-300, J5:398-399; `ui/log/RecordsFilterChips.kt:68-90`, `:111`):** each chip's label is on one
  line and not ellipsized (text nodes read "All", "Finds", "Tracks", "Waypoints", "Offline maps" whole, each
  node's height one line of text); the chips' widths are recorded in dp; where the row's content is wider than
  the screen, a horizontal swipe on the row moves the chips (the last chip's bounds change, and the last chip
  comes fully on screen). Clipping with no scroll is a fail.
- **Prediction:** at 1.0 and 384 dp, the row overflows the screen (five chips with 24 dp icons and counts, 8 dp
  gaps, 16 dp ends) and scrolls; at 1.15 it overflows by more and scrolls. The planner's prediction 2 (scrolls,
  not clips) holds.

### Item 31: long-press on a tile near the bottom edge
- **Pass (J4b:325):** a long-press (`motionevent` DOWN, hold ≥ 600 ms, UP) on an album or find tile whose bounds
  come within one tile height of the bottom of the content area opens the Delete menu, whose bounds lie inside
  the window; dismissed with Back, nothing chosen.
- **Data needed:** a grid long enough that a tile sits near the bottom edge. With 3 album photos (one row) and 2
  finds, no tile can reach there.
- **Prediction:** **not run** for want of data.

### Items 10 and 17: night-mode toggles
- **Item 10 pass (from the code):** `uiMode` is not in the Activity's `configChanges`
  (`AndroidManifest.xml:160`), so a `cmd uimode night` toggle recreates it. `compactTab` is `rememberSaveable`
  (`AvailabilityScreen.kt:741` at `2458934`), and `JournalScreenState` saves the top tab, the Records filter and the
  Entries view (`ui/log/JournalScreenState.kt:85-103`). So after the toggle the app shows the Journal, on Records,
  with the same non-default chip selected. J1's "back on Maps" predates this (R:2507).
- **Prediction:** Journal, Records, the same chip, after each toggle (dark to light and back).
- **Item 17:** needs the drafts list open, which needs two or more drafts (`CartographyScreen.kt:398-404`). The
  Data section allows one draft. **Not run** for want of data; the fold half is not run on any item (the S22 does
  not fold).

### Item 20: Seasonal after a night-mode toggle, then a new search
- **Pass (J2:569-570; `AvailabilityScreen.kt:890-893` at `2458934`, `AvailabilityViewModel.kt:608-633`):** after the
  toggle the app returns on Seasonal (`selectedTab` is `rememberSaveable`, `AvailabilityScreen.kt:726`) with its
  content shown; after a search whose region, month or filter differs, Seasonal shows loading and then a new
  pattern (or an explicit error), not the old one and not a blank.
- **Prediction:** pass.
- **A choice I make here:** the only searches I can make change the owner's current search (the Maps tab's sightings
  and reticle) and upsert a `cached_searches` row (`data/local/CachedSearchDao.kt:28`, `:45`). The dispatch
  allows the network for this item; I will change the month and change it back, and record both searches. See
  Decisions.

### Items 9 and 22: theme observations
- **Item 9 (observation):** in light, dark and Understory, each chip's fill and label colour read from the
  screenshot as hex, selected and unselected, from `RecordTypeStyle` (`ui/log/RecordTypeStyle.kt:43-46`: Finds
  `secondary`/`secondaryContainer`, Tracks `tertiary`/`tertiaryContainer`, Waypoints `primary`/`primaryContainer`,
  Offline maps `onSurfaceVariant`/`surfaceContainerHighest`; All the `FilterChip` defaults). A pair "looks too
  close to read" is judged by my reading of the image, and the contrast ratio is computed.
- **Item 22:** needs entries in two or more months. The phone has one month. **Not run** for want of data.

### Items 11, 19, 21, 23 and 28: real-data observations (sizes from dumps; no content quoted)
- **11 (J1:463-464):** the All list's find tiles two to a row, the type badges' sizes and what they overlap, the
  region row's height and whether a long name wraps (the only region name is short, so the wrap part lacks data).
- **19 (J2:567-568; `EntriesAlbum.kt:296-340`):** on the 3-column album the tile is about (384 − 2×16 − 2×3)/3 ≈
  115 dp here, not 107 dp; badges are 22 dp circles with 14 dp icons, 4 dp from the tile's bottom-start corner.
  The one photo linked to both an entry and a saved find should carry both badges; the other two none.
- **21 (J3:342-348):** the L0a entry's card: day numeral beside the title, the stats row, the 140 dp hero
  (`CartographyEntryCard.kt:332`) and the track thumbnail.
- **23 (J3:351-352):** the card thumbnail 56 dp (`CartographyEntryCard.kt:358`) and the Tracks row thumbnail
  40 dp (`ui/track/TrackExportPanel.kt:193`), measured and judged legible or not by my reading of the image.
- **28 (J4:268):** the region row's own background against the list's.
- **Prediction:** the sizes match the code at this density; nothing overlaps.

### Item 47 (portrait half): the details sheet's top
- **Pass (J5c:319-320; `ui/log/RecordDetailsSheet.kt:162-181`):** a tap on a waypoint, track or region row opens
  the sheet; the sheet's topmost node (its drag handle or its top edge) is at y ≥ 75 px, below the status bar and
  the cut-out; Back dismisses it.
- **Prediction:** pass (Material 3's `ModalBottomSheet` applies the status-bar inset itself; unverified until seen).

### Item 52: Directions and Share from the details sheet
- **Pass (J5c:327):** Directions on the waypoint sheet (`RecordDetailsSheet.kt:206`, `launchDirections`) hands off to
  an installed maps app, and Share on the track sheet (`:243`, `shareTrackGpx`) opens the system share sheet; each
  target's package is read from `dumpsys activity activities`; each is left with Back without acting. Nothing is
  shared to iNaturalist or anywhere.
- **Prediction:** Directions opens the phone's mainstream maps app (`com.google.android.apps.maps`), unverified; Share opens the
  Android share sheet.

### Items 19, 26, 32 and 50: TalkBack, partial (from dumps)
- **What a dump can give:** `uiautomator dump` writes text, `content-desc`, `clickable`, `long-clickable` and
  bounds. It does not write custom accessibility actions or click and long-click labels.
- **Pass:** each named control's content description and clickable/long-clickable flags are recorded; the custom
  actions ("Edit", "Delete", `TwoStageSwipe.kt:196-212`) and the labels ("Options for …", "Details for …",
  `RecordDetailsSheet.kt:113-116`) are recorded as **not readable from a dump**, with the code's values quoted as
  the code's, not as seen.
- **Prediction:** partial.

### Item 12: scrolling performance
- **Needs** 100 or more items in All. All holds 2 + 1 + 3 + 1 = 7. **Not run.**

### Held until the planner rules (see "Found before running")
Item 14, P:320 (draft Continue), P:321 (the map's "Log a find"), item 15, item 24, item 30, item 29, and
therefore any use of the entries in Data (a).

---

## Appendix C. Continuation 2026-09-28-12: the planner's rulings, and pre-registration of the held items

### The continuation message, verbatim (`prompts/preserved/2026-09-28-12.md`, below its "verbatim prompt follows" line)

> You are resuming dispatch 2026-09-28-04, Part A. Read `prompts/preserved/2026-09-28-04.md`, the launch message quoted in your run record, and this file. Then continue in `/home/zynergy-labs/Zynergy/forager-wt/device-backlog-a` on branch `device-backlog-a-2026-09-28`, at `ec3b9c0` or later. Push at every stopping point. Quote this message verbatim.
>
> **Planner's rulings on your four questions.** They rest on the owner's authorisation of labelled test data on the S22 ("Yes, labelled test data (Recommended)", 2026-09-28). This widens the dispatch's data rule (b) as follows, and no further. Every item you create is labelled DEVICE CHECK wherever the form allows, is left in place, and is listed in Created data.
>
> 1. **Item 14:** (a). Five real taps across the button's width are allowed. The drafts they create stay.
> 2. **P:321, "Log a find":** (a). One draft find is allowed, left in place. Do not press Cancel.
> 3. **Item 24:** (a). Make a saved entry with blank text and its candidates withheld in the form. Put the DEVICE CHECK label on your draft only. Say what the collapsed row shows.
> 4. **Held items now run:** 15, 24 at 1.0, 1.15 and 1.3, 29, 30, 26's snackbar half, P:320, and item 17 (your new drafts make two or more).
>    - Deletes stay limited to entries you created, and always end in Undo.
>    - **Do not run the Waypoints "scroll on an open row" check.** The three waypoints are the owner's track waypoints plus the L0a test waypoint. Record it as not run.
>
> **Unchanged:**
> - no `logcat -c`;
> - no iNaturalist activity;
> - no install, no Gradle, no emulator;
> - restore every setting, including `user_rotation` after the camera.
>
> The L0b coder builds on this machine in parallel.

The launch message for this session said: "You are the only coder on the phone." At 09:22:45Z the phone was the only
device attached (`R5CT321008R`, `SM-S908U`, `BP2A.250605.031.A3`), unlocked (`isKeyguardShowing=false`), with
`versionName=1.0.1192+g24589349`, and `logcat -b crash -d` returned 0 bytes. The phone's local time was then
02:22 on 2026-09-28 (UTC-7), so an entry started now is dated 2026-09-28, not the L0a entry's day.

### The plan for the created data (my reading of the rulings; see Decisions)
- Five taps across "New entry" make five drafts. Each is labelled `DEVICE CHECK 2026-09-28` in the entry's only
  text field ("Your own account (optional)"), which autosaves (`CartographyViewModel.kt:256-259`), then left with Back.
- The first tap is made alone, so the one-draft Continue path (P:320) can be seen before taps 2 to 5 make it a list.
- A sixth "New entry" makes the saved entry of Data (a): text left blank, any candidate withheld, then
  "Finish entry" (`CartographyEntryEditScreen.kt:284-285`). It is the one entry deleted, each delete ending in Undo.
- One draft find from the map's "Log a find", labelled `DEVICE CHECK 2026-09-28` in "Your own identification
  (optional)" (`LogEntryDetailScreen.kt:187-193`), as the L0a finds are labelled, and left with Back. Cancel is not pressed.

### Pass conditions and predictions, written before each item was looked at (code at `2458934`)

**Item 14, "New entry" (J2:259-261).**
- **Pass:** five taps at the button's vertical centre, at x both ends (10 px inside), the centre and two between,
  from a fresh dump, each open the entry editor for a new draft ("Your own account (optional)" and "Finish entry"
  showing; `CartographyScreen.kt:406`, `:497-505`); Back from the editor returns to Entries each time; the
  L0a card, tapped, opens its report (the VIEW mode, `CartographyScreen.kt:458`). Label and clearance were
  recorded in the first session.
- **Prediction:** pass; the draft count goes 0, 1, ..., 5.

**P:320, draft Continue.**
- **Pass:** with exactly one draft, the banner's "Continue ›" opens that draft in the editor; with two or more, it
  opens the full-screen drafts list (`CartographyScreen.kt:398-405`); Back from the list returns to Entries
  (`:363`).
- **Prediction:** pass for both.

**Item 15, the drafts banner's one-line fit (J2:262), at font scale 1.0, 1.15 and 1.3, with five drafts.**
- **Pass:** the label "✎ 5 unfinished entries", the "·" and the "Continue ›" button sit on one line inside the banner:
  the label node's text is the whole string and its height is one line of `bodyMedium` at that scale, and the
  button's bounds lie inside the banner's. The label has `weight(1f, fill = false)` and no `maxLines`
  (`EntriesDrafts.kt:50-54`), so the failure would show as a wrap to two lines, not as an ellipsis.
- **Prediction:** one line at all three on this 384 dp screen, tightest at 1.3.

**Item 24, the collapsed row's height (J3:353-355), at 1.0, 1.15 and 1.3.**
- **Pass:** the blank saved entry draws as `CollapsedEntryRow` (`CartographyEntryCard.kt:145-173`, chosen by
  `isCollapsedEntry`, `:291-292`): the small day numeral and weekday and, with nothing kept, "Nothing kept", on one
  line; the row's height is 64 dp (180 px) or less at each scale, the limit J3 names.
- **Prediction:** about 48 dp at 1.0 (the `heightIn(min = 48.dp)` floor), rising at 1.3 but under 64 dp.

**Item 30 (portrait), the two-stage swipe (J4b:323-324), on the saved entry's row.**
- **Pass (`TwoStageSwipe.kt:183-306`):**
  - a slow end-to-start swipe that passes the open threshold but stops short of half-way to the delete anchor
    settles **Open**: Edit and Delete buttons show, and no snackbar;
  - the open width is min(2 x 72 dp, 60% of the row) = 144 dp, 405 px, on a 990 px row (`:184-230`, `:332-336`);
  - a tap on the open row's body closes it and does not open the entry (the overlay, `:297-303`);
  - a tap on the closed row opens the entry;
  - a swipe past half-way to the row's width deletes: the row leaves the list and a snackbar "Entry deleted" with
    "Undo" shows; Undo brings the row back, closed (`:193-199`).
- **Not run:** RTL (the dispatch); the Waypoints scroll on an open row (the planner's ruling).
- **Prediction:** pass.

**Item 29, a night-mode toggle mid-snackbar (J4:269-270).**
- **Pass:** with the entry pending delete and its snackbar showing, `cmd uimode night` recreates the Activity; after
  it, the entry is still out of the list and the snackbar shows again (`PendingDeleteSnackbar.kt:136-139`: a
  cancelled effect reports nothing and the notice shows again); Undo then brings the entry back. The entry lost
  after Undo is a **fail and a stop**.
- **Prediction:** pass.

**Item 26, the snackbar half (J4:264-265), from dumps.**
- **Pass (partial, as the TalkBack items are):** a dump while the snackbar shows records its message and an "Undo"
  node that is clickable. Whether it is announced is the owner's.
- **Prediction:** the message "Entry deleted" and a clickable "Undo".

**Item 17, a night-mode toggle on the album with the drafts list open (J2:264).**
- **Pass:** on Entries, in the Album view, with the drafts list opened by Continue, a `cmd uimode night` toggle
  brings back the Journal tab and the drafts list (`draftsListOpen` is `rememberSaveable`,
  `CartographyScreen.kt:288`); Back from the list then shows the Album view (`entriesViewState`, `:392`). The fold
  half is not run.
- **Prediction:** pass.

**P:321, the map's "Log a find" routing.**
- **Pass:** on the Maps tab, "+" then "Find" (`AvailabilityCompactMapUi.kt:1199-1202`) shows the centre-pin picker
  with OK and Cancel; OK (`:1256`) switches to the Journal tab, on Records, with Finds selected, and the find form
  open with a location filled in ("Found at", not "Add Location") (`AvailabilityCompactScaffold.kt:727-735`,
  `JournalTab.kt:291-299`, `LogEntryDetailScreen.kt:179-184`). Back leaves the form and keeps the draft
  (`MushroomLogViewModel.kt:582-630`).
- **Prediction:** pass, and a "Saved to Drafts" snackbar with "Discard" after Back (`AvailabilityScreen.kt:1045-1057`,
  if the Back takes that path; unverified), whose Discard is not pressed.
