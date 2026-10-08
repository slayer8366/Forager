# S22 device check of everything merged 2026-10-07/08 (dispatch 2026-09-28-749, RECORD -749)

Device check only. No code was changed and Gradle was not run, because a forecast job was using the laptop. Phone: the S22
(`R5CT321008R`) only. The S26 was not touched. Screenshots, UI dumps and recordings are in
`~/Zynergy/device-evidence/2026-10-08-merged-check/` and are kept off git, because they show coordinates and the area
around the phone. This report quotes UI strings but gives no coordinates. Times are the phone's local time (PDT).

## Build

- **APK:** CI's own debug APK for main `c0ec942a` (run 37838552703, conclusion `success`). It was downloaded with
  `gh run download` to `~/.cache/merged-check-apk`, and that folder is deleted after this report.
- **APK sha256:** `caf612b3b46f7c85802b0d0dacbde0d0ea7fa9a76d27a588e43d57effcd71dfb`.
- **Installed:** `1.0.3097+gc0ec942a`, with `install -r` over `1.0.3094+g6ccb7b9e`. Between those two builds, main changed
  by two documentation commits only (`git log 6ccb7b9e..c0ec942a`).
- **Database version:** the phone's database was already at version 20 (header `user_version` = 0x14), which is this
  build's schema. So the install ran no migration.

## Summary

| # | Item | Result |
|---|---|---|
| 1 | Launch gate | **Pass** |
| 2 | Search (#200, #202) | **Pass, except one fail:** a recent search far from the phone does not show its observations on the map |
| 3 | Strip (#199, #202, #205) | **Pass.** ExploreOff not produced |
| 4 | Finds and dates (#200) | **Pass.** Newest-day-first, a blank tile and the journal menu could not be checked on this phone |
| 5 | Settings moves (#201) | **Pass** |
| 6 | Basemaps and memory (#201) | **Pass** |
| 7 | Trip windows (#202) | **Pass** |
| 8 | Motion (#192, #193, #197) | **Observations**, two of them worth a look |
| 9 | Back-by (#199) | **Pass.** The alert itself was skipped, as the dispatch allowed |

## 1. Launch gate: pass

`scripts/s22-launch-check.sh ~/.cache/merged-check-apk/app-debug-apk/app-debug.apk`, exit 0:

```
Device: SM-S908U (R5CT321008R)
Install: Success
Installed: versionName=1.0.3097+gc0ec942a
compile -m verify -f: Success
dexopt: status=verify
Status: ok
LaunchState: COLD
PASS: verified (status=verify), launched, process 31902 alive after 8 s, crash buffer empty
```

At the end of the session the crash buffer was 0 bytes, logcat held no `VerifyError` or `FATAL EXCEPTION` line for the
app, and the app's process was alive.

## 2. Search: pass, with one fail

The pass conditions are the dispatch's list and the search-keyboard report's device steps
(`docs/ui/2026-10-08-search-keyboard-report.md`, "Device only").

**Dropdown order: pass.** From the top, the dropdown shows:
- the Latitude and Longitude fields;
- "Search coordinates" (a text button under the fields);
- Month;
- "Search radius: 5 mi";
- "Recent searches";
- "Set on map" on the left and "Search" on the right.

The UI dump has no node containing "advanced", so there is **no "Advanced search"**.

**The keyboard and the bottom row: pass.**
- When the bar is tapped, the dropdown opens and the keyboard rises. The bottom row ends just above the keyboard:
  "Search" sits at y 1673-1748 px and the keyboard starts at about 1857 px.
- After the panel was dragged up to the coordinates and Latitude was tapped, the panel stayed where it had been dragged
  (search-keyboard step 2). Its foot was then capped at the keyboard's top, and the bottom row, which scrolls with the
  panel, sat under the keyboard until scrolled to. That matches step 2 as written.

**A recent re-runs with its own month and radius: pass.**
- Before tapping a recent, Month was set to May and the radius to 15 mi, so the bar read "May · Search a location".
- Tapping a recent saved as "Fungi · October" at 5 mi gave a bar reading "October · 5 mi", with observations on the map.
- Reopening the dropdown showed Month "October" and "Search radius: 5 mi".

**A different recent, far from the phone: FAIL** (search-keyboard step 3, second half: "its observations are on the
map").
- What happened: tapping a recent about 160 km from the phone loaded that search. The List tab read "Based on 212
  historical iNaturalist observations of Fungi within 5 mi for October." with a different species list. "View on Map"
  showed the chip "Showing: Fly Agaric (18)". But the map stayed on the phone's live position, so no observations were
  in view.
- I tried dragging the map before tapping the recent. The map was again on the live position afterwards.
- **Cause, from the code, not instrumented:** `SightingsMap.kt:880-899` does not move the camera to the search region
  while GPS tracking owns the camera (`isGpsTracking`, `shouldMoveCameraToTarget`). That gate is older than these PRs.
- The search-keyboard report anticipated this: "If step 3's second half fails, the owner's report has a second cause
  this build did not reproduce."
- The planner relayed the owner's ruling: a search should move the map there. That goes into a separate fix build.

**A suggestion tap fills in and keeps search and keyboard open: pass.**
- I typed "chanterel" and the suggestion list appeared.
- Tapping "Chanterelle violette" (*Gomphus clavatus*) closed the list. The panel and keyboard stayed (`mInputShown=true`),
  and the bar read "Chanterelle violette · October · 5 mi".
- Observation: the suggestions' common names came back in French ("Chanterelle cinabre", "Chanterelle à flocons"). The
  phone is set to English (US).

**Clear clears and keeps recents: pass.**
- After Clear, the map showed no observation markers. The Latitude and Longitude fields were empty, and Recent searches
  were all still listed.
- The bar read "Chanterelle violette · October · Search a location". The location part went back to "Search a location"
  and the chosen species stayed. Per `activeSearchSummary` (`AvailabilitySearchUi.kt:614-630`), the species is part of
  the summary until another selection replaces it.

**Search at bottom right runs the current location: pass.** Tapping "Search" ran a search: the bar read "Chanterelle
violette · October · 5 mi", Clear appeared, and the Latitude and Longitude fields were filled in by it.

**Failure messages: not produced.** Producing "Location permission was denied. Tap Set on map to choose a place." or
"Couldn't find your location. Tap Set on map to choose a place." needs location permission revoked or no fix. That
means changing phone settings the dispatch did not list, so it was not done.

## 3. Strip: pass

All measurements are at density 3.75 px/dp.

**Portrait: pass.**
- The text is the 14 sp style: the readout line is 75 px, which is 20 dp, the line height that `StripHeightTest` uses.
- There is no "Facing": the strip reads "273° W · Alt 341 ft · <MGRS>".
- **The strip is 36.0 dp:** the bar's divider ends at y 271 px and the strip's fill meets the map at y 406 px, so
  135 px.
- No bar fill hangs below the divider: the fill colour is the same above and below the line, and it ends at the strip's
  foot.
- The three-dot button is at the strip's right end. Its clickable node is [1283..1440] x [249..429] px. That is a 48 dp
  touch box clipped by the screen edge, around a 20 dp icon centred at x 1372, which matches a 36 dp button flush to the
  right edge. The button's own 36 dp bounds are not exposed to uiautomator; this is inferred.

**Landscape, both rotations, with a short search showing ("October · 5 mi"): pass.**

| Rotation | Bar | Line | Strip | Heights |
|---|---|---|---|---|
| 90 (`user_rotation 1`) | from the punch-hole side to the line | x 1285-1288 px | "284° W · 319 ft · <MGRS>", whole | y 113-248 px for both (36 dp), the portrait height |
| 270 (`user_rotation 3`) | from the line to the far side, mirrored | x 1799-1802 px | the same readouts, whole | the same |

The joins are on the same pixels as the launch-verifyerror check's S22 run (1287 and 1801,
`docs/audits/2026-10-08-launch-verifyerror-report.md` section 3).

**Landscape with a long species search showing: by design.**
- With "Chanterelle violette · October · 5 mi" in the bar, the strip showed only the needle and coordinates, in both
  rotations: the join is at x 1647 px at 90 and 1439 px at 270, and the strip is about 256 dp wide.
- This is the -732 rule: "Where both cannot fit, the bar keeps its floor and the strip narrows, and readoutsFitBeside
  drops from the front" (`docs/ui/2026-10-08-landscape-bar-strip-report.md`, RECORD -732).
- Noted because a long species name drops the altitude as well as the heading.

**ExploreOff: not produced.** The compass reported a heading the whole time, and producing the icon needs a settings
change.

## 4. Finds and dates: pass, three parts not checkable

**The phone had 0 finds** ("Finds 0"). With the owner's yes, relayed by the planner ("Yes, add two (Recommended)"), I
added two finds through the app's own flow, Journal > Records > Finds > "+" then Save, with no photos:
- one named "Device check find", saved at 1:47 PM;
- one with no name, saved at 1:48 PM.

Both are dated 2026-10-08, at the phone's location, and they stay on the phone as test data.

**Finds grid: pass.**
- The "+" tile is alone in the top row.
- Below it is the day heading "Oct 8, 2026", then the tiles "Device check find" and "Found 1:48 PM".
- Both tiles have the placeholder art (no photo). Within the day, the 1:47 PM find comes first.

**Dates as "Oct 7, 2026": pass, in the places available.**

| Where | Text |
|---|---|
| Find bubble on the map | "Found 1:48 PM", then "Find on Oct 8, 2026", then "Open in Journal" |
| Records day headings | "Wed, Oct 7, 2026 · 19 records" and "Thu, Oct 8, 2026 · 4 records" |
| Records track row | "Oct 7, 2026, 5:44 PM" |
| Records waypoints from this check's recording (new automatic names) | "Start · Oct 8, 2026, 1:53 PM" and "End · Oct 8, 2026, 1:54 PM" |
| Records waypoints saved before this build | "End · Oct 7, 5:56 PM" ("names already saved keep their text") |
| Find editor title | "Found 1:47 PM" |
| Trip Windows | "Oct 8, 2026 – Oct 14, 2026" and "1.2 in, ended Sep 25, 2026" |
| Seasonal rain chart | "Sep 24, 2026" and "Oct 7, 2026" |

**Times in the phone's 12/24-hour form: pass.** The phone is on 12-hour (`time_12_24 = 12`) and every time above is
12-hour. 24-hour was not tried, because that is a phone setting the dispatch did not list.

**Not checkable on this phone:**
- **Newest day first:** both finds are from today, so there is only one day.
- **A blank tile:** a blank tile needs a find with no saved time and no photo, which is a find created for another day.
- **The map's journal menu:** it needs journal entries on the map, and the phone has none ("No entries yet. Use New
  entry to start one.").

## 5. Settings moves: pass

**The Tools drawer: pass.** It has the Sundown section under Trip Planner:
- "Sundown alerts";
- "Dark under trees" with "Woods get dark before sunset. Alerts allow this much extra.";
- the offsets "30 min", "45 min", "1 h" and "1 h 30";
- "Off-track reminder: your phone buzzes if you head away from your start."

**Settings: pass.** It has Units, Night Mode, Night Maps, Backup, Crash Logs and Diagnostics. It has no Sundown section
and no camera rows.

**The camera: pass.**
- I opened the camera from a find editor's Camera button. The chip strip reads "Flash off", "Timer off", "Grid off",
  "Save location: On", then the gear, "Camera settings".
- The gear opens a panel with "Automatically Save Location to Photos" (checked) and "Lock camera to portrait"
  (unchecked), each with its explanation.
- Back closed the panel first, with the camera still open. A second Back closed the camera.
- No photo was taken.
- Observation: the preview was black throughout, most likely because the lens was covered where the phone lay. Not
  investigated.

## 6. Basemaps and memory: pass

**Basemaps: pass.** The Layers sheet's Map type offers "Street" and "Topographical" only.

**Street survives a restart: pass.**
- I picked Street, ran `am force-stop`, then a cold `am start`. The app came back with "Layers: Street map".
- The relaunch was recorded with `screenrecord --output-format=h264` streamed to the laptop, so no file was left on the
  phone, and decoded frame by frame.
- The map area goes from the blank map background (frame 86) straight to the Street tiles (frame 87). The difference
  from the settled Street frame is 23.5 at frame 86 and 0.1 at frame 87, so no Topo frame comes between.
- Contact sheet: `s68-relaunch-sheet.png`.

**The icon bar survives a restart: pass.**
- I dragged the icon bar to the left and up (long-press, then drag). Afterwards "Fullscreen" was at [30,421] px, where it
  had been at [1230,897].
- After `am force-stop` and a cold start, it was in the same place, [30,421], not minimised (the handle reads "Hide map
  controls").
- Observation: when I put it back, a long-press drag starting 37 px from the screen edge, held 0.9 s, did not take.
  Starting at 50 px and holding 1.2 s did.

## 7. Trip windows: pass

The Trip Windows card in Tools > Trip Planner ends at its measurements: "Days after rain", "Last soaking rain", "More
rain forecast", "Soil moisture", "Soil temperature" and "Evaporated since rain". It has no guidance text.

## 8. Motion: observations only

I recorded one 24 s streamed screenrecord through these steps:
- Maps to Journal, then Entries and Records;
- List, then back to Maps;
- the Tools drawer and Settings;
- Night Mode Dark, then Light;
- Back twice.

Contact sheets: `s80` to `s83`.

**Looked right:**
- **Maps to Journal:** a crossfade, with the map fading under the Journal.
- **The Tools drawer:** it slides in over the map at the 80% fill.
- **Tools to Settings:** a push, with Settings coming in from the left as Tools leaves.
- **Entries to Records:** the switch is clean.

**Worth a look (observations, not gates):**

1. **The night blend is not a smooth fade.** Light to Dark showed one muddy grey middle state, with the text washed out,
   then Dark. Dark to Light was the same in reverse. The recording shows two large single-frame steps (frame diffs 86.5
   and 48.6), not a ramp. Motion Part 3 asked "whether the map chrome and system bars changing at the start read as a
   flash", and on this recording they may.
2. **Returning to Maps from List:**
   - For a moment the map is blank and the strip's heading shows "—".
   - Then one recorded frame (frame 286 on sheet `s83-tabs.png`) shows the map drawn hugely magnified: a blurred map
     label fills the map area. The right view follows on the next frame.
   - Seen once. Not investigated.

**Not looked at:**
- press highlights and the bounce frame by frame;
- the list close-up;
- landscape motion;
- motion with the phone's animations turned off.

## 9. Back-by: pass, the alert skipped

During a short indoor recording (started 1:53 PM, stopped about 1:55 PM):

- **The quick menu opens: pass.** It shows "Back by" with "+1 h", "+2 h", "+3 h" and "Pick a time…", then the Sundown
  rows and "Off-track reminder".
- **Back by +1 h: pass.** At 1:53 PM it set "Back by 2:53 PM", with "Clear" beside it.
- **The strip line in the last hour: pass.**
  - The strip shows a second line, "Back by 2:53 PM", under the readouts.
  - The menu button's description changes to "Quick settings, Back by set", with a dot on the button.
  - +1 h puts the back-by time inside the last hour at once.
- **Clear: pass.** The menu goes back to "Back by" with the presets. The strip's second line goes, and the button reads
  "Quick settings" again.
- **A map tap with the menu open closes it: pass.**
- **Skipped:** the alert itself, because it needs the hour to pass. The dispatch allowed this.
- Observation: during the recording the Return button read "Return: 76° E · 0 ft · -1 ft". The last figure is
  negative. Not investigated.

## Database

The database was hashed with `adb exec-out run-as com.zynergylabs.forager.app cat databases/<file> | sha256sum`
(first 16 hex digits):

| When | `forager.db` | `-wal` | `-shm` |
|---|---|---|---|
| Before install | `9a95a4d9c7cda462` | `98701f21fee5b32a` | `fd4c9fda9cd3f9ae` |
| After the launch gate | `9a95a4d9c7cda462` | `98701f21fee5b32a` | `fd4c9fda9cd3f9ae` |
| After the searches (item 2), before opening the find editor | `9a95a4d9c7cda462` | `007e37a892f8d226` | `32b01636d3af9d8f` |
| After opening the editor and camera, before saving the finds | `9a95a4d9c7cda462` | `097ad999c385c374` | `adc42f673b3584b6` |
| After the two finds | `5da8b9c4f49c8f00` | `5a3b2a614d1839e6` | `d90c1d9149624572` |
| Before the recording (item 9) | `5da8b9c4f49c8f00` | `5a3b2a614d1839e6` | `c69a9c6c805d76dc` |
| After the recording | `5da8b9c4f49c8f00` | `570369d77e66dfcf` | `ea2a61ba2762a032` |
| End | `5da8b9c4f49c8f00` | `570369d77e66dfcf` | `ea2a61ba2762a032` |

What changed, and why, inferred from the actions taken (no rows were diffed):
- **The searches (item 2)** cached each search ran in this check: two recents re-run, one species search for the
  current location, and the re-runs in item 3.
- **Opening the find editor** wrote before any Save, most likely a draft row. Not diffed.
- **The two finds**, saved, and a WAL checkpoint into `forager.db`.
- **The recording**: one track with its automatic "Start" and "End" waypoints.

Free disk on the laptop was 2.5 GB at the end.

## Phone changes, each restored and read back

| Change | Restored to | Read back |
|---|---|---|
| `user_rotation` set to 1, then 3, for item 3, several times | 0 | `accelerometer_rotation 0`, `user_rotation 0` |
| App basemap set to Street (item 6) | Topographical | "Layers: Topographical map" |
| Icon bar dragged left and up (item 6) | Right side, original height | "Fullscreen" at [1230,897], the handle at [1365,1243][1440,1513], as at the start |
| Night Mode Dark (item 8) | Light | the Light radio checked |
| Month May and radius 15 mi (item 2) | Replaced by the re-run recents (October, 5 mi); not restorable as such, since the app does not keep them across a restart | "October · Search a location" after relaunch |
| Two finds added (item 4, with the owner's yes) | Kept, as test data | Finds 2 |
| One short track, with Start and End waypoints (item 9) | Kept, as test data | Tracks 85 before, Start and End rows in Records |

Unchanged throughout, read at the start and the end: `font_scale 1.0`, `time_12_24 12`, `low_power 0`, Units "Imperial
(US)" and Night Maps off. The ringer and battery settings were not touched.

Nothing was uninstalled, no data was cleared and no file was deleted. One file was written outside the app:
`uiautomator dump` writes `/sdcard/window_dump.xml`, and it was rewritten at every UI dump. Whether it existed before this
check was not looked at. It is left in place. screenrecord streamed to the laptop, so it left no file on the phone.

## Not done

- The search failure messages (item 2).
- The ExploreOff icon (item 3).
- Item 4: newest day first, a blank tile and the journal menu.
- The back-by alert (item 9).
- Item 8: motion with animations off, and landscape motion.
- 24-hour time.
