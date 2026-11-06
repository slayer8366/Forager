# When a stack has more map icons than the spiral can fit: report (dispatch 2026-10-27-06)

Written by a Forager app coder (Opus) for Rowan Waymark, 2026-10-27. Report only: nothing was built, no test was run, no device was used. Dispatch: `prompts/preserved/2026-10-27-06-spiral-overflow-report.md` on `records-after-173`; RECORD -1734.

**Base.** `origin/pre-main` at `6064fef86` (the dispatch's stated base; checked to be the remote head when this was written). Every file:line below is at that commit, read with `git show`.

**The owner's ask, verbatim:** "with so many icons on the map, it goes off the edge. Can we find a way to reduce the chaos when there are more icons than the spiral can fit?" and "Report first".

---

## 1. How it works today

### How icons group

- **A stack is decided at the tap, on screen distance only.** `stackOf` (`ui/map/fanout/MarkerFanOut.kt:71-78`) takes the tapped marker plus every marker whose horizontal *and* vertical distance from it are both under `FAN_STACK_DP` = 26 dp (`MarkerFanOut.kt:30`).
- **It is not transitive** (`MarkerFanOut.kt:67-68`): a marker near a neighbour, but not near the tapped one, is left out. So a stack is "everything within a 52 dp square centred on the tapped icon".
- **Candidates are asked from the map** inside a box `2 × 48 dp` either side of the tap (`MapTapHandler.kt:207`, `STACK_QUERY_HALF_DP`, `:290`).
- **Nothing groups markers before a tap.** No source is clustered: every overlay source is a plain `GeoJsonSource` (`ui/map/OverlayLayerSetup.kt:87`), and `withCluster`, `clusterRadius` and `clusterMaxZoom` appear nowhere in `app/src/main` (searched). At country zoom, every icon draws, one on top of another.

### When a tap fans or spirals them

- **The tap path** is `MapTapHandler.onMapTap` (`MapTapHandler.kt:86-127`): resolve the tap, then `openStackAround` (`:201-214`). Two or more in the stack opens the fan (`:210-212`). One alone goes to its bubble as before.
- **Ring or spiral:** `fanOffsets` (`MarkerFanOut.kt:93`) uses a ring for up to `FAN_RING_MAX` = 8 (`:33`) and a spiral beyond (`:116-129`). The spiral is Archimedean. It starts 48 dp out, grows 48 dp a turn, and puts each icon at the first point whose 48 dp touch square clears all the others (`:89-91`, `:96`).
- **Placement:** `openFan` (`MapTapHandler.kt:217-244`) centres the ring or spiral on the stack's average spot. `fanShift` (`fanout/FanPlacement.kt:82-135`) then moves it as a whole to stay inside the map view and off the measured controls. Each icon has a leg back to its true spot (`ui/map/FanOutLayers.kt:90-96`).

### Its size limits, and what happens past them

- **There is no cap on the count.** `fanOffsets(count)` takes any count, and `openFan` passes the whole stack (`MapTapHandler.kt:228`).
- **The fan's footprint grows with the count.** I computed this by porting `ring`/`spiral` line for line to a scratch script (not committed, not a test; the 48 dp squares are included):

  | members | 8 | 12 | 16 | 20 | 25 | 30 | 35 | 40 | 50 | 60 |
  |---|---|---|---|---|---|---|---|---|---|---|
  | width × height, dp | 184×184 | 238×212 | 238×263 | 288×292 | 327×311 | 327×360 | 376×390 | 422×408 | 432×456 | 480×504 |

  A portrait phone map is roughly 380 to 410 dp wide. That figure is **unverified**: I did not measure the S22 or S26 map view. So roughly 30 to 39 icons fit across the width before the controls are taken into account. Fewer fit once the icon bar is kept clear.
- **Past the screen, it overflows on purpose, and quietly.** When the fan is wider or taller than the map view, `fanShift` centres it on that axis and sets `allOnScreen = false` (`FanPlacement.kt:99-100`). Staying on screen comes before clearing controls (`:73-74`). Nothing reads `allOnScreen` or `controlsCleared`: no file in `app/src/main` other than `FanPlacement.kt` names them. `openFan` uses only `.shift` (`MapTapHandler.kt:229-240`).
  - **So the overflow is a fallback that fires unlogged.** CLAUDE.md, "Errors and failure paths", says a fallback must be logged when it fires.
  - The icons and legs past the edge are drawn off screen and cannot be tapped. This is what the owner's screenshot shows.
- **One placement detail.** Above `PER_MARKER_LIMIT` = 60 members, the keep-out check treats the fan as one bounding box (`FanPlacement.kt:63`, `:106-107`). This only changes how the controls are avoided, not the count.

### How it differs by zoom

- **The rule does not change with zoom.** Every distance is in screen dp. Nothing in `ui/map/fanout/` reads the zoom (searched).
- **What changes is how much ground 26 dp covers.** I computed this from the web-Mercator scale with MapLibre's 512-unit tiles, at latitude 45°. The tile size is from my own knowledge and was not checked here.

  | zoom | 26 dp covers about |
  |---|---|
  | 5 (country) | 45 km |
  | 10 | 1.4 km |
  | 15 | 44 m |
  | 17 | 11 m |

  At country zoom, a whole season's records in one area fall inside one tap's 52 dp square. That is the screenshot's case.
- **Zooming folds the fan.** A gesture or an app-made camera move folds it (`MapTapHandler.kt:156-159`; `fanout/CameraMoveClassifier.kt:5-9`). Only the location follower's moves keep it open.

### The Back behaviour

- **Back folds an open fan** (`fanout/MarkerFanOutState.kt:131-136`). The handler is composed only while the fan is open, so it is asked first.
- **Some things close before the fan** (`MarkerFanOutState.kt:102-129`), and the fan stays open behind them:
  - an open bubble;
  - the Tools drawer;
  - fullscreen;
  - the add-action menu;
  - the pin pickers;
  - the search dropdown.

  So, from a find opened inside a fan, Back closes the bubble and then folds the fan.
- **Tapping a fanned icon** opens its own bubble, and the fan stays behind it (`MapTapHandler.kt:91-94`).
- **"Open in Journal" from a fanned icon, then Back:** the Maps tab reopens the same fan. `FanReopenCoordinator` (`fanout/FanReopen.kt:42-47`) does this through `openFanFor` (`MapTapHandler.kt:134-139`).
- **A tap on empty map:**
  - with a bubble up, it closes the bubble only;
  - without one, it folds the fan (`MapTapHandler.kt:96-99`).

## 2. What counts in a group

- **What fans:** `fanOutLayerIds` (`FanPlacement.kt:59-60`) takes every marker layer except the sighting dots. The registry gives four (`ui/map/layers/MapLayers.kt:469-472`):
  - planned trips;
  - waypoints;
  - finds;
  - photos.

  iNaturalist sightings never join (`FanPlacement.kt:53-58`). A layer switched off is not a member (`MapTapHandler.kt:145-148`).
- **Journal entries are not members.** A journal entry draws as a halo ring under its record's icon. The ring is in `TapGroup.NONE` and in the decorations band (`MapLayers.kt:284-308`, `:432-434`), so it is never fanned and never counted. A find in a journal entry counts once, as a find.
- **Photos:** the Maps tab draws every gallery photo that has a coordinate (`domain/GetMapRecordsUseCase.kt:97-101`). That includes photos attached to finds: `GetGalleryPhotosUseCase` returns every photo, each paired with the entries that reference it (`domain/GetGalleryPhotosUseCase.kt:5-14`). So today a find with *k* located photos near it adds 1 + *k* members to a stack.
- **What dispatch 2026-10-27-05 alone changes:** each such find becomes 1 member, because its photos no longer draw. A stack of *F* finds holding *P* located photos between them drops from *F* + *P* + *S* + *W* + *T* to *F* + *S* + *W* + *T*. Here *S* is standalone photos, *W* waypoints and *T* trips.
  - **How much that helps the screenshot cannot be said from the repository.** The screenshot is kept off GitHub, and I did not count its icons or know the owner's records.
  - If finds there average two photos each, the stack falls to about a third.
  - Whether it falls under the ~30 to 39 that fit depends on the count. At country zoom, any area with more than about 30 finds still overflows after -05. So -05 reduces the case but does not close it.

## 3. The options, priced

**Prior art in this repository, which the options must be read against.** When F4 was ruled (`docs/plans/journal-redesign.md`, "Stacked map markers fan out on tap (owner, 2026-09-29)"), the planner offered:
- A: a chooser list;
- B: clustering;
- C: fan out on tap;
- D: leave it.

The owner chose C, verbatim: "Option C / This sounds better than the other options  and honestly, it's like A, but it keeps the icons, which is more robust for UX."

That ruling was about stacks in general. A list or a clustering for the *overflow* case only is a narrower question it did not answer. It still has to be put to the owner as revisiting a declined option, not as a fresh idea. F4's completion note also flagged "no cap, so a dense dot cluster becomes a spiral running off screen" (RECORD -206). That was ruled by excluding sighting dots, not by a cap.

**Outside prior art**, from my own knowledge and not checked online: the common web-map marker-clustering library zooms to a cluster's bounds on tap and fans ("spiderfies") only at the map's closest zoom. That is option A.

### Shared definition: "fits"

"Fits" means `fanShift` reports `allOnScreen` and every control cleared, for the stack's own `fanOffsets`. That uses the live map-view bounds and the measured controls (`ui/map/MapKeepOut.kt:61-69`), not a constant. CLAUDE.md, "Robolectric reports zero window insets", explains why a constant is wrong here. A stack that fits behaves exactly as today in every option.

### A. Zoom in, then spiral

```
Tap a crowded stack > the map glides in to the stack's area > it splits into icons or small stacks
                                                          (or fits: its fan opens by itself)
   All still at one spot at closest zoom > a list (as B's) > tap a row > its bubble > Back > the list > Back > list closes
Back after the zoom > ? (see "Open questions")
```

```
before (z5)          after the tap (z11)
   [▣▣▣▣]               ●     ●  ●
   (38 icons)           ●●(3)    ●   ●
                          ●    ●●(2)
```

- **What the user must notice or decide:** very little. The map moves where they tapped, which is the familiar cluster behaviour, and they tap again.
- **The cost to the user:**
  - their view of the wider area changes, and they have to zoom back out themselves unless Back does it;
  - a stack that only overflowed because of the icon bar now zooms, where before it would have fanned.
- **Build cost: medium.**
  - A new outcome in `openStackAround` for "does not fit", passed to a new sink that fits the camera to the stack's members' bounds. `applyCameraFrame` with an `EntryMapFrame.Fit` already exists (`ui/map/MapCameraMoves.kt:168`; `domain/EntryMapFrame.kt:12-16`, padding 48 dp, closest zoom 17). The move is marked as the app's (`CameraMoveClassifier.markAppMove`).
  - Then, on camera idle, the same stack is checked again and the fan opens if it fits, through a coordinator like `FanReopenCoordinator`.
  - The closest-zoom list is B's list, so A costs B's list plus the zoom.
  - The "closest zoom" for this is a decision: 17 is the entry map's cap, not this feature's.
- **How often the list would show:** at zoom 17, 26 dp is about 11 m, so the list shows only when more than ~30 records sit within ~11 m of each other. That should be rare. It is inferred, not measured.

### B. A limit plus a list

```
Tap a crowded stack > a spiral of as many as fit, plus one "+N more" icon > tap "+N more" > a list
   > tap a row > its bubble > Back > the list > Back > the list closes, the spiral is still up > Back > it folds
```

```
     ● ● ●
   ●  ● ●  ●
  ●  ●[▣]●  [+14]
   ●  ● ●  ●
     ● ● ●
```

- **What the user must notice or decide:**
  - which icons made it into the spiral and which did not. The order today is layer then id (`MapTapHandler.kt:220`), which means nothing to a user;
  - that "+N more" is a control;
  - and they have to read a list.

  Two surfaces for one tap is more to think about than A. If the order is not meaningful, the spiral's subset reads as arbitrary.
- **Build cost: medium to high.**
  - Capacity: the largest *n* that fits, by trying `fanOffsets(n)` against `fanShift`.
  - A "+N more" member drawn in the fan's own layers, so `fanMemberAt` resolves it (`MarkerFanOut.kt:151-158`). It needs a new key kind that is not a record.
  - A new list surface over the map.
  - The list needs a row per record (name or title, kind, date, perhaps a thumbnail). The fan knows only `FanKey(layerId, featureId)` and a position (`MarkerFanOut.kt:42-61`), so each row's data has to be looked up through the map's records. That is new plumbing.
  - Back has a third layer: list, spiral, map.

### C. Group at low zoom into count bubbles (third option)

```
Country zoom > instead of 38 icons, one bubble "38" > tap it > the map zooms in (as A) > icons or smaller bubbles
```

- **What the user must notice or decide:** least of all at a glance, because the map is no longer a pile. But the icons themselves vanish at low zoom. The F4 ruling picked the fan over clustering partly because "it keeps the icons". A count bubble also hides the mix of finds, photos and waypoints unless the bubble shows it.
- **Build cost: high.**
  - MapLibre clusters per source, and the four member kinds are four sources (`MapLayers.kt:469-472`). One cross-kind count needs a merged source, or the app's own grouping in domain code.
  - It reaches the journal halos (drawn per record source), the fan's hide gate and copies (`ui/map/MapFan.kt:67-120`), the layer switches, and tap resolution, since a cluster feature has no record id.
  - It is the largest change and the one closest to a declined option.

### D. Pack the fan tighter (also suggested by the code)

- The spiral's only rule is "48 dp squares do not overlap" (`MarkerFanOut.kt:96`). A square grid obeys the same rule and packs far denser. A 384 × 700 dp map fits about 8 × 14 = 112 squares, against ~30 to 39 in the spiral.
- **To the user:** the same gesture as today, but with more room. A grid of 30+ icons with 30+ legs is still the "chaos" the owner named.
- **It only moves the limit.** It still needs A or B past it.
- **Build cost: low** (`fanOffsets` only). But it changes the look the owner approved ("a ring, or a spiral above 8").

### Recommendation

**A**, with B's list only at the closest zoom, plus logging the overflow fallback as part of whichever is built.

- A asks the user for nothing new: tap, see the map go there, tap again. It keeps the owner's fan and icons as ruled.
- It changes nothing for a stack that fits.
- It matches the cluster convention users already know.
- Its list is the rare case, so the extra surface is rarely seen.
- B makes every crowded tap a two-surface decision with an arbitrary subset.
- C and D are larger or only defer the problem.

### Open questions for the owner (not decided here)

1. **Back after A's zoom.** By "Back retraces the way in", Back would zoom back out to where the user was before the tap. That is a Back step the map does not have today. The other choice is that the zoom is not a step and Back does what it does now. This needs a step path confirmed with the owner.
2. **After the zoom, does a fitting fan open by itself, or wait for a second tap?** I sketched "by itself". It saves a tap, but it is one more automatic move.
3. **A's closest zoom** before falling back to the list: 17, as the entry map uses, or the map's own maximum.
4. **What the list rows show**, in A's rare case or B's every case.

## 4. Real touch and 80% chrome

- **Map taps are not Compose touches.** A tap on an icon or on a fan member arrives through MapLibre's click listener into `MapTapHandler`. `MapView` cannot be built under Robolectric (`MarkerFanOutState.kt:24-25`). So the fan's own tests drive `MapTapHandler` with a fake `MapProbe` (`fanout/FanOutTestScene.kt`, existing). **A** and **B**'s "+N more" icon and **C**'s bubble are tested that way. Coordinates are real there, but it is not a Compose hit test.
- **The list (A's rare case, B's every case) is new chrome over the map**, so it falls under CLAUDE.md's touch-interception pitfall: a `Surface` swallows touches across its full bounds. It needs:
  - real `performTouchInput` touches at several points across each row, to show rows take the finger;
  - a real long-press on the map outside the list's bounds, to show the map still gets it (the pattern that caught the compass-strip and return-row regressions);
  - Back tests against the real screen and dispatcher, like `AvailabilityScreenFanBackOthersTest`.

  The three existing fan Back tests already use `performTouchInput` (`AvailabilityScreenFanBackDrawerTest`, `AvailabilityScreenFanBackOthersTest`, `AvailabilityScreenFanBubbleDismissalTest`).
- **A and C's camera move** needs a test that the app's own zoom does not fold, and then reopens, the fan it is about to open. `CameraMoveClassifier` folds on an app-made move today (`CameraMoveClassifier.kt:8`).
- **80%:** the list's fill is `MAP_CHROME_OVER_MAP_ALPHA` (`ui/map/MapChrome.kt:69`, 0.8), with opaque content, as on the Layers sheet. If it is a sheet with its own scrim or nested surfaces, the layers composite to 0.8; each one does not carry it (`MapChromeAlphaTest`). If the list is ever shown where it covers no map, it stays solid. Whether it is a bottom sheet, a panel or a dialog is a placement question. Dialogs are ruled one by one in `docs/plans/journal-redesign.md`.
- **The "+N more" icon (B) and count bubbles (C)** are map icons, not chrome, on the owner's earlier distinction ("those are map icons not chrome", `journal-redesign.md`). So the 80% rule does not apply to them. Flagged for the owner to confirm rather than assumed.

## 5. Disclosure

### Confirmed vs inferred

- **Confirmed by reading the code at `6064fef86`:**
  - the stack rule, ring and spiral, placement, and that there is no cap;
  - `allOnScreen` is unread, so the overflow is unlogged;
  - no clustering anywhere;
  - zoom-independence;
  - the Back order;
  - the four member kinds; journal halos are not members;
  - find-attached photos draw as members today.
- **Computed, not run in the app:**
  - the footprint table (a line-for-line Python port of `ring`/`spiral`; not committed; not checked against the Kotlin by a test);
  - the 26 dp ground-distance table (it assumes MapLibre's 512-unit tiles and latitude 45°).
- **Inferred:**
  - how many icons fit on the owner's phones (map-view width unmeasured);
  - that A's list case is rare;
  - that -05 "reduces but does not close" the case.

### Could not determine

- How many icons were in the screenshot's stack, and how many of them were find-attached photos. The screenshot is off GitHub and not seen by me.
- The real map-view size on the S22 and S26, and how much the icon bar takes.
- Whether the legs alone, with no overflow, read as "chaos" to the owner.

### Premises that were wrong

- **The dispatch lists "journal entries" among what counts in a group.** They are not members: a journal entry is a halo under its record's icon, with no tap and no fan (`MapLayers.kt:284-308`). A find in an entry counts once.
- **"When a tap fans or spirals them" and "how it differs by zoom"** suggest the behaviour varies by zoom. It does not. Only the ground area of 26 dp varies.
- **Options B and C** are not new to the owner: a chooser list and clustering were both declined in favour of the fan on 2026-09-29. They are narrower here (overflow only), but have to be presented as revisiting that ruling.

### Decided beyond scope

- **Nothing was built.** No test, CI run or device was used.
- **I did not add a `docs/audits/README.md` row.** The coordinator's instruction was that only the report file changes. The index row is left to the planner.
- **Flagged but not fixed:** the unlogged overflow fallback (`FanPlacement.kt:99-100`, unread at `MapTapHandler.kt:229-240`). It is a CLAUDE.md "Errors" gap whatever option is chosen.
