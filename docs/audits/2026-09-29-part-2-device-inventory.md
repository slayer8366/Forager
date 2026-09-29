# Stage device check Part 2: the inventory of owed device checks (read-only, at `b5c5dcc7`)

**Date:** 2026-09-29.

**Recorded by:** the planner, from a read-only helper's hand-back. It is condensed; every source citation is kept.

**What it covers:**
- **Read at:** `origin/journal-redesign` `b5c5dcc7`, when RECORD.md ended at -147.
- **How to read the citations:** "RECORD:NNNN" is a line in RECORD.md at that commit. Report paths are under docs/audits/.
- **The device** is the S22, and 0/90/270 are its rotations.
- **Markers:** (DATA) means the check needs a data change, and (NET) means it needs the network.
- **Not yet checked by the planner item by item.** Each item is re-verified when the dispatch is written.

**Scope.** Part 2's scope is "M1, entry framing, Leaving-the-Journal, landscape fixes" (RECORD:3393). Part 1's dispatch (`prompts/preserved/2026-09-28-51.md:10-13`) covered only:
- L0b;
- the night region;
- T1 widths;
- T3 search.

So the rest of the "lapsed after L0a" backlog (RECORD:3395) is still owed. That includes two stages Part 2's wording does not name (group 1b).

**Built since this was read:** -137, backup and restore (dd056c6a), and its report. Items 69-76 were taken from the rulings, before that report existed. Re-check them against its "not tested / device-only" list.

## 1. Owed

### 1a. Part 2's named scope, never run on a device

**M1 glyph bubbles** (m1-glyph-bubbles-completion-report.md:491-501; RECORD:3178)
1. A native hit test per kind returns featureId. Colour-field cells open only at the point stage. The photo glyph and cells were never touched. [m1:493]
2. A tap inside an offline circle, away from its outline, falls through to what is beneath. [m1:494; RECORD:2788]
3. The bubble's tail tip sits on the glyph at the screen edges. 0/90/270. [m1:495]
4. Bubbles clear the real compass strip, the nav, the rail and the cut-out. 0/90/270. [m1:496]
5. Bubbles re-anchor on pan, zoom and rotate, and stay gone once dismissed. [m1:497-498]
6. A feature tap in fullscreen does not bring the chrome back. [m1:499]
7. Directions hands off to the navigation app. It was never touched in -102. [m1:500]
8. The find overlay, over the Journal and over a day entry. [m1:501]

**Entry framing (T2)** (track-widths-entry-frame-copy-completion-report.md:456-462, 852-856; RECORD:3293)
9. The entry is framed in the preview, in fullscreen and in landscape, on the first style load. [:458-460]
10. Rotating re-fits the view. The view stands after a pan, and after a find is opened and closed. [:461-462]
11. Track widths at zoom 13 and 11. Part 1 could not observe them, because the reticle covered the track. Also, the breadcrumb dots and gaps shrink with the width. [p1 run record:1144; :456-457]
12. Refocus after clearFocus(). The auto-scroll at a large font scale in short landscape. [:852-856]

**Leaving-the-Journal F1–F4** (leaving-the-journal-fixes-completion-report.md; RECORD:3331). At 0, 90 and 270, with real Back keys and gestures.
13. **F1:** a committed find, then Maps, Tools, and home-and-return. No "Saved to Drafts", and the find is kept. [:261]
14. **F1:** an unchanged editor's Back gives no snackbar. A changed one gives "Saved to Drafts", and Discard restores it with its photos. (DATA) [:263]
15. **F1:** a new find, Back, opened from Drafts within 4 s, Save, then Discard. The saved find stays. (DATA) [:265]
16. **F2:** a day entry in Edit, with typing, then Maps and back. The editor is there with the text. Back gives "Save your changes?"; take Discard once and Save once. (DATA) [:269]
17. **F2:** the same with a withheld waypoint, and with a new entry ("Finish entry"). (DATA) [:271]
18. **F2:** a dirty editor, a night-mode toggle (the activity is recreated), then return. The editor is there. [:272]
19. **F4:** Tools then Back closes only the drawer. On Maps, a second Back closes the state. Tried over:
    - the dropdown (Maps and Journal);
    - "Set on map";
    - the Log-a-find picker;
    - the landscape add-action menu.
    [:274-276]
20. The Journal dropdown closes on Back with no drawer open. [:277]
21. **F3:** a find's report stays open across Maps/Journal, Tools+Back and home-and-return, with no snackbar. [:457]
22. **F3:** the same from the editor with a change. The editor comes back with the change. (DATA) [:459]
23. **F3:** a changed find, then Maps, then "Log a find". "Saved to Drafts" shows and the new find opens. Repeat with an unchanged re-edit and with a viewed find: no snackbar, and no second draft. (DATA) [:460-462]
24. **F3:** as item 23, through a bubble's "Open in Journal". (DATA) [:463]
25. **F3:** the camera round trip from a find's editor. The find is still open, with the photo. (DATA) [:467]
26. **F3:** a find opened over Entries from a bubble, then Maps and Journal. It stays over Entries, and Back returns to Entries. [:470]

**Landscape L1/L2** (landscape-picker-and-report-completion-report.md:345-360; RECORD:3379)
27. The offline picker (L1) and the entry report (L2) at 90 and 270, with real insets. What remains of the scrolling side. The map's side against the cut-out and the rail. [:347-353]
28. Turning the phone with the picker open keeps its pan and zoom. [:354-355]
29. L2's opening frame on the 154 dp preview. [:356-357]
30. Whether L2's Part B cut-off control is shown. [:358]
31. A bubble taller than the 154 dp preview is not clipped. [:359-360]

### 1b. Built after L0a, in no check so far, and not named in Part 2's wording (the owner to rule)
32. **The search bar after an entry.** From a day entry (report, then editor), Maps shows the bar with no bare band, and the Journal keeps the entry. Portrait and short landscape; also from the entry map in fullscreen. [maps-search-bar-after-entry-completion-report.md:145-155; RECORD:3032]
33. **Drawer Back.** Tools then Back over each of these, at 0/90/270:
    - a day-entry report or editor, clean and dirty;
    - the Records Finds chip;
    - the Entries album;
    - Settings over an entry;
    - the states never tested: the entry map in fullscreen, the drafts list, the short-landscape search header, and the pull-photo picker.
    [drawer-back-completion-report.md:263-283; RECORD:3157]

### 1c. Map chrome and layout (-124, -146)
34. Portrait: with the legend expanded, the cluster's last row sits above the legend. Collapsed, the cluster is back in place. [RECORD:4871]
35. Portrait: Latitude, Longitude and "Search this location" sit above the keyboard. Record whether the scroll lowers the keyboard. [RECORD:4872]
36. The camera survives a tab round trip: pan, zoom, bearing, tilt and follow mode. A new search still moves the camera. [RECORD:4873]
37. The "i": in portrait and fullscreen; at 90, inboard of the rail; at 270, in the cut-out band. A touch opens attribution. [RECORD:4874]
38. At 90 the caption clears the cluster. At 90 and 270, the margin from the cluster to the search bar (about 0 px; read the pixels). [RECORD:4875-4876]
39. The short-landscape reshape at 90 and 270:
    - the pill beside the bar;
    - the 56×156 dp fill;
    - drag, snap and the handles;
    - record and return reachable while recording;
    - thumb reach, which the owner judges.
    [RECORD:4877]
40. The legend inboard of the cluster, with the cluster on each side, both collapsed and expanded. The legend against the dropdown. The one-frame jump on first compose. At 90 and 270. [part-1-layout-fixes :1562-1564]
41. **The chip row** at 90 and 270. (NET) [RECORD:4878]
    - Cases: one chip, two chips, a long label, and the cluster on the far side.
    - Check: the alignment, the width cap, the ellipsis beside the clear button, clear of the central third and the nav inset, and the 12 dp gap.
    - Portrait spacing is still 4 dp.
42. In landscape the cluster drags past the old legend bound, down to the nav inset. Portrait still stops above the legend. [RECORD:4879]
43. J8's pill touched at its edges, for the owner's feel. A full 48 dp target is revisited if it proves too small. [RECORD:4880-4881; plan:905]
44. The entry map follows the Maps basemap. Street on Maps, then Satellite on the entry: Maps is still Street. It is dark at night on all three. [RECORD:5258]
45. The search notice's top meets the strip's bottom, and the cluster sits below it. Clearing the notice restores the cluster. At 0/90/270, with the real status bar. (NET) [RECORD:5259]
46. The Maps snackbar meets the floating nav's top in portrait, takes the system-bar inset in fullscreen, and clears the rail and cut-out at 90 and 270. (DATA) [RECORD:5260]
47. The Layers sheet's map-type chips are centred, at the same height, in portrait and landscape (the 640 dp sheet). [RECORD:5261; plan:957]
48. The sheet's nav-bar band shows the map at 0.8 under the Layers sheet and the short-landscape Records sheet. Solid in portrait is correct. [map-chrome-follow-ups :104]
49. The landscape centre-pin row clears the rail and the nav bar at 90 and 270, and the pin is still at the map's centre. [:105]
50. Species suggestions: one Back closes them and leaves the search panel open. (NET) [:107]
51. The Offline maps Records sheet: the picker map shows through in short landscape, and the sheet is solid in portrait. [:108]

### 1d. The Journal on the map (J8 follow-ups) and save failures
52. **The photo bubble's lines.** (DATA) [RECORD:4195]
    - 1-3 shown entries: "In <find>", with no "Kept in".
    - More than 3: one "Kept in N".
    - No find: no line.
    - Switch off: the line as before.
53. No rings on ORIGIN and END. Whether a navigated-to ORIGIN gets one. [RECORD:4196]
54. Both chips re-measured against 0.8 (the taxon chip was never measured), and whether they still read as separate without a shadow. (NET) [RECORD:4197]
55. Rings under tracks, the recording trail, the region outline and border, the reticle, and a highlighted track's halo, for the owner to judge. Taps unchanged. [RECORD:4198-4199]
56. J8's highlight colours: the owner's full-colour review on the device. [plan:765; RECORD:4078]
57. **The seven save-failure messages as Toasts,** at 0/90/270. A refused write needs a debug hook, which is the owner's call. [RECORD:3948]
    - Each one legible, long enough, and clear of the keyboard and the cluster.
    - An off-screen failure shown at the next Journal open.

### 1e. Night
58. The white border on the night region's outline, over Topographical and Street: does it carry the edge, do the dashes read, and is 0.85 too bright? This re-checks Part 1's 5b fail. [RECORD:4156]
59. Nothing changes by day. Satellite at night. A highlighted region at night. The entry report map at night with Offline on. Taps unchanged. [RECORD:4157-4161]

### 1f. Planned trips
60. With two trips and no search, from a cold start: the flags draw by day and at night, the switch works, and a tap opens the bubble. (DATA) [RECORD:5054]

### 1g. Save to Gallery
61. A capture (no location) and an import (keeping its own metadata) appear in the "Forager" album with the right date. (DATA) [RECORD:5075]
62. The control's placement against the real status and nav bars, in portrait and landscape. [RECORD:5076]
63. No permission prompt appears. A 12 MP JPEG copies without a stall. [RECORD:5077]

### 1h. Backup and restore: the full round trip, behind a verified full copy (owner "1 A", RECORD -149)
64. A real backup to a chosen folder, then a Replace restore and a Merge restore (the owner's prompt copy). Each is compared with the copy. (DATA) [backup :125]
65. The system pickers: the offered file name, the folder picker's persisted permission, the open-file picker, and a cloud provider. [:128]
66. The schedule fires (WorkManager, under Doze) and its file lands in the folder. (DATA) [:126]
67. The Backup section in the Tools drawer, in portrait and landscape. [:130]
68. Weekly is the default, and the schedule is off by default. [plan:930]
69. A restored region shows "Not downloaded". Download again works against the real server. (DATA, NET)
70. Merge gives incoming regions new ids, and the refs follow. (DATA)
71. Unreadable photos pause with Try again / Continue without file(s) / Cancel. (DATA)
72. A failed write deletes only its own file (a real SAF delete), with Try again / Cancel. (DATA)
73. Planned trips round-trip. (DATA)
74. Restore is blocked while recording. (DATA)
75. The loading page: the pulse, then Done, then the grow-and-fade into Maps. Reduced motion goes straight to Maps. (DATA)
76. The scheduled-run notifications, and a tap opening Backup. (DATA; item 8 of -137 is not built)

## 2. Already verified elsewhere
- **L0b Layers sheet (Part 1 checks 1-3):** pass. RECORD:3670-3690.
- **Track widths at zoom 15 and above:** pass. p1 run record:1144.
- **Bar text, and the short-landscape auto-scroll:** pass. p1 run record:1102-1145.
- **Earlier fails whose fixes are now owed as re-checks:**
  - Night 5b failed; fix -91; re-owed as item 58.
  - The legend at 270, the expanded-legend overlap, and the dropdown under the keyboard failed; fix -124; re-owed as items 34, 35 and 40.
  - J8's chip over the reset button, the dead band, the ORIGIN/END rings, rings over lines, and the chip at 0.84 failed; fixes -124 and -92; re-owed as items 41, 43 and 52-55.
- **J8's migration, toggle, highlight, chip, bubble and switch:** pass. RECORD:3887-3893.
- **Map chrome at 0.80 on every surface, and legibility:** pass. RECORD:4380-4388.
- **M1 hit tests on the find, waypoint, track, region and trip glyphs:** seen in passing, not as a formal verdict. map-chrome run record:318-322.
- **Offline regions after -106:** pass. RECORD:4810-4815.
- **Tablet pane widths and the photo viewer:** J6 inputs. RECORD:3840-3853, 4092.

## 3. Could not determine
- Whether items 32-33 belong to Part 2. They are owed by the catch-up (RECORD:3395), but Part 2's wording does not name them (RECORD:3393).
- The light theme's legibility has never been checked anywhere (RECORD:4408).
- -122's unrun items, assigned to no check:
  - the P1 and P3 disagreement cases;
  - offline rendering with the network off.
  RECORD:4819.
- Tablet-only items, which cannot run on the S22:
  - the untinted wide Layers button;
  - F1 on a wide window, and F3 step 5;
  - the wide search-panel tap;
  - a restore onto the tablet as a "new phone";
  - the tablet half of -134.
- No device on hand: the API 26-28 photo folder picker, and the API 26-29 backup snapshot path.
- The second Back that did nothing in the portrait dropdown (p1 run record:1082-1086): flagged, never dispatched.
