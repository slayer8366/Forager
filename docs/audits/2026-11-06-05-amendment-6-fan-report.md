# Dispatch 2026-11-06-05, Amendment 6 B, built: the highlight goes into the fan

Hazel Cairnwright (Opus), coder B, 2026-11-08 UTC, on the owner's "Build it" (RECORD -2187). Branch `route-card-fan`, cut from
`origin/route-card` at `c511e430e` (as the dispatch expected). `origin/pre-main` was `1f6901a11` when I started; it moved by three
commits while I worked, and I merged nothing in (the planner merges this branch into `route-card`). Not pushed to `route-card`; no PR.

## The ruling

The owner: **"When fanning out, the highlighted icon stays put while the icon it represents is in the fan. Add the highlight to the fan
without any duplicates"**, restated and confirmed ("Yes, that's it"). While a stack is fanned, the selected stop's accent follows its pin
to its fanned place, none is left at the stack's centre, and it returns when the fan closes. Trip-stop flags the same. Day cream, night
rust as today. It still hides with its layer.

## What was built

- **The fanned copy carries the accent.** The fan draws copies of the stacked markers (`FanOutLayers.kt`). The selected stop's copy now
  uses the accent image (`WAYPOINT_ROUTE_SELECTED` for a pin, `PLANNED_TRIP_ROUTE_SELECTED` for a flag) in place of its plain one, so the
  accent moves out with it and back with it (`fanCopyIcon`, new file `ui/map/FanRouteStopAccent.kt`). The colours are those images'
  own, unchanged: cream by day, rust at night.
- **None at the centre.** The accent at the stop's own spot is a separate layer (`PlottedRouteLayers.kt`). While a stack is fanned, that
  layer hides any accent standing on a fanned marker. It uses the same filter call, at the same moment, that hides the fanned originals
  (`applyFanOutHiding`, through `fanHiddenAccentKeys`). So the accent goes when its pin goes, and comes back when the pin is shown again
  at the fold's end. To tell which marker it stands on, the accent feature now carries its marker's layer and id (`fanKey`, written as
  `layer/id`, since a waypoint and a trip may share an id).
- **The selected stop knows its id.** `SelectedStopMarker` gains `featureId` (the waypoint's or trip's own id) and `fanKey`. It is set
  where the marker is chosen (`CompactMapRouteStops.selectedStopMarker`).
- **The fan is handed the selected stop.** `SightingsMap` passes `plottedRoute?.selected?.fanKey` to `MapFan`. `MapFan` reads it live
  with the fan's own state, so a stop picked by tapping its copy in the open fan takes the accent on that copy at once.
- **It still hides with its layer.** No change there. With Waypoints (or Planned Trips) off, `selectedStopMarker` is `null`, so no copy
  gets the accent, and a marker on a hidden layer cannot be fanned anyway.

Rejected: moving the accent's own point with the fan, frame by frame. The accent and the copy would then come from two sources pushed
separately, so for a frame either could lead the other. That is the blink the fan's hide gate exists to prevent (amendment -371).

## Tests

`RouteStopAccentFanTest` (new) runs on the real compact Maps tab with the real `RoutePlottingViewModel`, the real `MapTapHandler` and the
real fan clock. Each step is a real touch at screen coordinates: the route's marker, the stack, and a fanned copy. Back goes through the
activity's dispatcher. The map is a stub, as in `AvailabilityScreenCrowdedStackTest`, because a `MapView` cannot be built under
Robolectric. It carries a copy of SightingsMap's one line that hands the fan the selected stop. The test reads what the map is given:
the copies as `fanFrameCollections` builds them, and the accent source checked against the keys the accent layer hides.

1. **Waypoint:** Creek (the first stop, selected) sits in a stack of three pins. Open the fan. Exactly one copy carries the accent,
   Creek's, at Creek's fanned place (checked to be away from the stack). The other two copies are plain, and no accent is drawn at the
   centre. Then touch Ridge's copy: Ridge becomes selected and the accent moves to Ridge's copy, Creek's copy is plain again, and there is
   still none at the centre. Back folds the fan: no copies, and the accent is back on Ridge's pin at its own spot.
2. **Trip flag:** Saddle (a trip, the first stop) sits in a stack with another trip. Its copy carries the accent flag at its fanned
   place, the other flag is plain, and none is at the centre. When the fan closes, the accent flag is back on Saddle.

Four existing tests construct `SelectedStopMarker`, so they now pass the id: `PlottedRouteSelectedStopTest`, `RouteCardNavigateTest`,
`RouteCardFullscreenBackTest` and `RouteCardTripAccentLayerTest`. Their equality checks now cover the id too. No assertion was removed.

### Runs (each on the six affected classes: 37 tests)

| run | commit | result | failures |
|---|---|---|---|
| red 42467402807 | `ad4387f77` (scaffolding: the plain icon, nothing hidden) | 2 of 37 failed | waypoint: "one copy carries the accent, Creek's expected:<[wp-creek]> but was:<[]>"; trip: "one copy carries the accent flag, Saddle's expected:<[trip-saddle]> but was:<[]>" |
| green 42468976113 | `89263f690` | 37 of 37 passed | none |
| revert 42469010793 | throwaway `hazel-rc6b-revert`, `fanHiddenAccentKeys` made `emptyList()` | 2 of 37 failed | "no accent left at the stack's centre"; "no accent flag left at the stack's centre" |

- **Red:** every precondition passed before the failure (the route opened, the right stop selected, the fan open with the expected
  member count). Each test failed at the first assertion about the accent on the copy. That is today's behaviour.
- **Revert:** I checked the compile log first and found no compile errors. Both failures are the centre assertion, which is the one this
  edit removes. They are different messages from the red run's, so they are not stale results. I restored the file from a copy saved
  before the edit, not from git, and checked that the forward line was present (`members.map { fanAccentKeyValue(it.key) }`, count 1,
  clean tree). The throwaway ref is deleted.
- In all three runs, the job "Route-data cutter routes the same as the full square" failed while fetching the pinned BRouter jar:
  `curl: (22) ... error: 504` from the external host, four tries, exit 22. That is the network. None of my files are involved, and that
  job builds nothing of the app.
- In the red and revert runs, the "Street map deleted" job ran the whole suite (6,889 tests) and found only the same two failures. In
  the green run, that job passed.

### Full CI on route-card-fan: run 42470704890 (`89263f690`, every unit test)

- 6,943 test cases: 6,919 passed, 24 skipped, 0 failed. Every job passed except "Route-data cutter routes the same as the full square",
  which failed again at "Fetch the pinned BRouter jar and profiles": four `curl: (22) ... error: 504` responses, exit 22. That is the
  same external fetch as the three runs above.
- **By name, against run 42451729839** (`route-card` at `96a7be8d5`, the PR run): 6,941 cases there. Two cases are only here, both
  `RouteStopAccentFanTest` (the new tests). No case is only there, and no case shared by both runs changed status.
- **By name, against pre-main's latest run, 42337576510** (`1f6901a11`): 6,846 cases there. 115 cases are only here and 18 only there.
  All of these differences are the route card's own work since pre-main: schema 24, Re-center and the locate ring, the card's outline,
  tab and tuck, and Navigate. They show in the 42451729839 comparison as already present. Of the 115, two are mine (the new tests); the
  four classes I changed by one line each are among them only because they are new on `route-card` since pre-main. None of the 18 is
  from a file I touched. No case shared by both runs changed status, and the skipped count is 24 in all three runs.

## Files touched

- `app/src/main/java/com/zynergylabs/forager/app/ui/map/FanRouteStopAccent.kt` (new, 46 lines)
- `app/src/main/java/com/zynergylabs/forager/app/ui/map/FanOutLayers.kt` (386 to 395)
- `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapFan.kt` (157 to 168)
- `app/src/main/java/com/zynergylabs/forager/app/ui/map/PlottedRouteLayers.kt` (223 to 233)
- `app/src/main/java/com/zynergylabs/forager/app/ui/map/SightingsMap.kt` (800 to 801, one argument added to the `MapFan` call)
- `app/src/main/java/com/zynergylabs/forager/app/ui/availability/CompactMapRouteStops.kt` (two lines changed, no growth)
- Tests: `RouteStopAccentFanTest.kt` (new); the four tests named above (one or two lines each)

None of these are coder A's. A's commits on `route-card` up to `28d3d0203` touch none of them, and `git merge-tree` of this branch with
`origin/route-card` at `28d3d0203` is clean. A's new tests construct no `SelectedStopMarker`.

## Confirmed vs inferred

- **Confirmed by CI** (Robolectric, the real compact Maps tab, a stub map), both waypoint and trip: the copy the fan is given carries the
  accent at the stop's fanned place; the accent at the centre names a marker the accent layer hides while the fan is open; and after the
  fold there are no copies and the accent is back on the pin. Also confirmed: picking another stop from the open fan moves the accent
  to that stop's copy.
- **Inferred, not run:** that MapLibre draws this on a real map. The filter on the accent layer and the accent image in the fan's icon
  layer are read from the code, and the stub map draws nothing. Also not run: the hide timing on a phone (the accent at the centre goes
  when the originals go, after the copies are reported drawn); landscape; night (the colours are the existing images', which
  `MarkerGlyphsTest` covers); a style reload with the fan open (the gate is new per style and sets the filter again, read from code).
- The test has its own copy of SightingsMap's `accent = plottedRoute?.selected?.fanKey`. It does not run `SightingsMap` itself.

## Could not determine

- Whether the accent copy and the plain copies stack in the right order where they overlap on a phone. The copy keeps its layer's sort
  key, as before. I have not seen it.

## Premises that were wrong

- None found. The branch base was `c511e430e`, as stated. The fan code and `PlottedRouteLayers.kt` were where the dispatch said.

## Decided beyond scope

- I touched files outside "the map layer and the fan code", none of them A's: `SelectedStopMarker` needed the record's id, so
  `CompactMapRouteStops.kt` passes it, four existing tests that construct it pass it too, and `SightingsMap.kt` passes the key to the
  fan. The alternative was to match the fan member to the accent by coordinates. I rejected it because a stack is exactly where two
  records can share coordinates, and the probe's coordinates come from rendered tiles.
- `SightingsMap.kt` grows from 800 to 801 lines. The modular-code rule says a file over about 800 lines is split before a feature goes
  in. I judged one argument on an existing call to be a few-line fix, not a new capability in that file: the work itself is in a new file
  and in the fan's own files. Flagged for the planner.
- Any accent standing on any fanned marker is hidden, not only the selected one, because there is only ever one accent. This also covers
  a stop picked from inside the open fan.

## S22 steps (nothing was run on a phone)

The phone will be driven by hand, by the owner or the planner. Nothing here moves on its own.

1. Make three waypoints close enough to stack at the zoom you use, and save a route whose first stop is one of them.
2. Open the route. Its first stop's pin is in the accent (cream by day).
3. Tap the stack. The fan opens. The accented pin is out in the fan, in its fanned place. Nothing accented is left at the centre, and
   there is no second accented pin.
4. Tap another stop's pin in the fan. Its bubble opens, and the accent moves to that pin in the fan. Still nothing at the centre.
5. Back (closes the bubble), Back (folds the fan). The accent is back on the selected pin, in its place, with no blink as it folds.
6. Repeat 3 to 5 at night: the accent is rust.
7. With a trip as the selected stop, stacked with another trip: the accent flag goes into the fan and back the same way.
8. Tools > Map Layers > Waypoints off, then on again: the accent goes and comes back with the pins, as before.

## Questions for the owner

- None about the ruling. For the planner: is the one-line growth of `SightingsMap.kt` past 800 acceptable, or should a split go first?

# Amendment 6, the fan's opening and the dimmer cream, built (part 1 built; part 2 found and brought back; part 3 not finished)

Hazel Cairnwright (Opus), 2026-11-08 UTC, on the owner's "Build it" for the two last "Added" sections of Amendment 6 (RECORD -2187,
-2190). Cut from `origin/route-card` at `eabf94117`, as expected. Worktree `~/.cache/forager-wt/route-card-fan2`.

## Part 1, the centre copy (defect): built

**Cause, from source.** The fan hides its originals only once the renderer reports the copies drawn (the -371 hide gate). That report is
matched by each copy's `image:featureId` (`expectedCopies`, FanOutRenderSignal.kt). Amendment 6 B drew the selected stop's copy with
the accent image (`fanCopyIcon`), but the expectation still named its layer's plain image. So with the selected stop in a stack, the
report could never complete. The hide then waited for the last resort: the fan at rest plus three rendered frames (`onRest`, with a
logged warning, "The renderer did not report the N copies drawn..."). That fits the S22 figure: all pins landed at 514 to 542 ms, and
the highlighted original went at 571 ms. Every fanned original waited this way, not only the highlighted one. The accent drawn over
them is the one that showed.

**Fix.** `expectedCopies(members, accent)` names each copy by the image it is drawn with (`fanCopyIcon`). The per-frame check is now
the pure `copiesReported`, which `awaitCopiesRendered` calls. It reads the selected stop on each frame, so a stop picked from the open
fan still counts. MapFan passes `accent = { currentAccent }` to `hideWhenCopiesDrawn`. On the fold, the accent comes back with its pin,
as before (the release shows both through the same filter call).

**Test.** `FanOpeningCentreAccentTest` (new) steps frame by frame through an opening at 120 fps on the fan's own clock (250 ms,
fast-out-slow-in). Each frame it pushes `fanFrameCollections` with the accent. The renderer reports the frame from three frames
earlier. Each reported frame goes through `copiesReported`, and the real `FanOutHideGate` hides when that check passes. The test reads
what was hidden by the first reported frame whose copies are off their spots, and the accent keys the accent layer then hides
(`fanHiddenAccentKeys`). There are three cases: a selected waypoint, a selected trip flag, and no selection. The third is a control:
it passes before and after the fix by design.

| run | commit | result | failures |
|---|---|---|---|
| red 42510947472 | `d7f329152` (plumbing only; the expectation ignores the accent) | 2 of 51 failed | "the copies were reported drawn by frame 4, the first whose copies are off their spots"; "the copies were reported drawn before the flags leave" |
| green 42512957457 | `131bfe2ca` | 51 of 51 passed | none |
| revert 42512959443 | throwaway, the one fix line put back to `markerIconForLayer(layerId)` | 2 of 51 failed | the same two as red |

All three ran on six classes: FanOpeningCentreAccentTest, FanOutRenderSignalTest, RouteStopAccentFanTest, FanCircleSymbolTest,
FanOutLayersTest and FanIconStackingOrderTest. **Revert:** the compile log had no errors, and the unit tests ran fresh (they completed at
19:08 UTC; the red run's completed at 18:43). The two failures are the same messages as red. That is the only result this revert can
produce, because it restores red's exact expectation. The control case passed. The "Street map deleted" job ran the whole suite in
that run: 6,906 tests, 2 failed, and those two are the same FanOpeningCentreAccentTest cases. The revert was made in a separate,
detached worktree, so no file in the working tree was edited or restored. Its saved copy was not needed. The forward line is present
at `131bfe2ca` (count 1). Throwaway refs are deleted.

**Full CI on route-card: run 42516850265** (`043eb296d`, dispatched by hand because the head commit is `[skip ci]`). Every job passed:
6,960 cases, 6,936 passed, 24 skipped, 0 failed. **By name, against 42504864619** (`268110904`, route-card's last green): 6,957 cases
there. Three cases are only here, the new FanOpeningCentreAccentTest. None is only there, and no shared case changed status.

## Part 2, the pause: found, not changed (it needs the owner's choice)

**What the ~0.28 s is spent on.** It is mostly the system's double-tap window, before the app hears of the tap at all.
- The app opens a fan from MapLibre's map-click listener (`MapListeners.kt:150`, `map.addOnMapClickListener` → `MapTapHandler.onMapTap`
  → `fan.open`).
- In the pinned MapLibre 13.5.0, the map click is sent from `onSingleTapConfirmed` (javap of
  `MapGestureDetector$StandardGestureListener`: `onSingleTapConfirmed` calls `notifyOnMapClickListeners`; `onSingleTapUp` only cancels
  transitions).
- MapLibre's gesture library (`maplibre-android-gestures` 0.0.4, `StandardGestureDetector`) wraps a plain `android.view.GestureDetector`
  whose listener is also a double-tap listener. Android then confirms a single tap only once the double-tap timeout has passed since
  the finger went down (`ViewConfiguration.getDoubleTapTimeout()`, 300 ms by default). Before then, a second tap would make it a
  double-tap zoom. Double-tap zoom is on: the app never turns it off (no `DoubleTap` setting in `app/src/main`).
- From finger-up, that is 300 ms less the time the finger was down, typically about 200 to 250 ms. The rest, about 30 to 80 ms, is
  inferred to be the app's own path: the tap queries, Compose's next frame, and MapLibre re-tiling the copies' source. That split was
  not measured.
- **Not the hide gate (-371).** `fan.open` sets the members and `MarkerFanOutHost` starts the progress animation at once. The gate
  delays only the hiding of the originals, never the fan's start (FanOutHideGate.kt's own record: "holding the fan's start" was
  rejected).
- **Not the route card series.** `git diff origin/pre-main...origin/route-card` touches none of the tap path (MapListeners.kt,
  fanout/, MarkerFanOutState.kt, the build files and the MapLibre pin are unchanged). The planner's S22 run agrees: pre-main 278/279 ms
  against #507 279/292 ms.

**Why it was not shortened.** Starting the fan sooner means acting on the tap before Android knows it is not the first half of a double
tap. Every way to do that is a behaviour choice:
1. **Open the fan at finger-up when the tap lands on a stack; everything else as today.** This saves about 200 ms on a stack. A
   double tap on a stack would then start the fan and fold it at once as the zoom begins: a flicker. It needs a touch detector of the
   app's own in front of MapLibre's, for stacks only.
2. **Turn off double-tap zoom on the map and take every tap at finger-up.** Every tap is about 200 ms faster (bubbles too). Double-tap
   zoom goes; pinch and the buttons remain. It still needs the app's own tap detection, because MapLibre waits for the confirmed tap
   either way.
3. **Leave it.** It is the platform's double-tap wait, and pre-main has it too.

## Part 3, the dimmer cream: not finished

The red test is committed (`2180708cc`, pushed only to the ref `hazel-fan2-cream-red`, which I left for the next coder). It is not run
and not on `route-card`. MarkerGlyphsTest and PlottedRouteSelectedStopTest now expect a day accent of #E3D3B5 (night #A2612C
unchanged), and the day accent must differ from the plain pin and the plain flag. The one-line change still to make is
`routeStopSelectedColour` in PlottedRouteLayers.kt.

**The shade picked, and why.** Before: Cream #EDE3D0 (relative luminance 0.775, HSL lightness 0.87, saturation 0.45). Proposed:
#E3D3B5, the same hue (39°) and saturation, with lightness 0.80. Its luminance is 0.663, and its contrast against the white casing is
1.47:1, up from 1.27:1. The darker warm neutrals in the palette are all greyer, so none reads as cream: SurfaceContainerHighestLight
#E2DCCC (saturation 0.27), OutlineVariantLight #D5CEBF (0.21) and NeutralGray200 #CFC9BE (0.15). Of those, #E2DCCC is the nearest
fallback if the owner prefers a palette value. The owner judges the shade on the phone.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, headless): with the accent, the copies the fan pushes now satisfy the render signal's own check, and the
  gate hides every original, and the centre accent, by the first rendered frame whose copies are off their spots. Without the fix,
  they never do.
- Inferred: the three-frame render lag (the figure FanOutRenderSignal.kt records from the probe), and that MapLibre on the phone
  behaves as the simulation does. The `MapView` frame listener and MapFan's hand-over of the accent cannot run under Robolectric.
- Part 2's split, about 200 to 250 ms of system wait plus about 30 to 80 ms of app path, is inferred from source and bytecode, not
  timed on a phone.

## Could not determine

- How long the S22 taps were held down, so the exact share of the double-tap wait in each tap.

## Premises that were wrong

- "The pause is likely introduced in the route card series" (the planner's first correction). Source and the later S22 run both say it
  was not. The planner withdrew it.

## Decided beyond scope

- `awaitCopiesRendered` and `hideWhenCopiesDrawn` gained an `accent` parameter, and the per-frame check moved into the pure
  `copiesReported` so a test can drive it.

## S22 steps (part 1; nothing was run on a phone)

1. With a saved route open and its selected stop in a stack of three, tap the stack at 120 fps capture. From the first frame a pin
   moves, there is no cream (or beige) mark at the centre. The beige mark Rowan saw on #507 is gone.
2. Fold (Back). The accent returns on its pin with no blink.
3. The same at night (rust), and with a selected trip flag.
4. In logcat (tag MarkerFanOut), there is no "did not report the N copies drawn" warning on these opens.

# Amendment 6, dimmer cream, built (part 3)

The owner's ruling (RECORD -2187): "Dimmer cream" ("A slightly darker solid cream in place of today's"), and for night's rust, "Leave
it". Built on route-card at `9201b8a3e`, on top of the red test the previous Hazel left (`2180708cc`, cherry-picked unchanged as
`97f39c0cd`; it applied cleanly to route-card's head `999495695`, whose only commits after its base were two report commits).

## What changed

- `routeStopSelectedColour` (PlottedRouteLayers.kt) returns `RouteStopDimmerCream` #E3D3B5 by day in place of `Cream` #EDE3D0. Night stays
  `MushroomDeep` #A2612C. `RouteStopDimmerCream` is a new named colour next to the function. Its comment cites RECORD -2187 and the
  owner's words, and names the palette colours that were rejected and why.
- The fan uses the same colour with no second change. `fanCopyIcon` (FanRouteStopAccent.kt) gives the selected stop's fanned copy
  `MarkerIcon.WAYPOINT_ROUTE_SELECTED` / `PLANNED_TRIP_ROUTE_SELECTED`. Those icons draw their fill from `::routeStopSelectedColour`
  (MarkerGlyphs.kt), as does the accent at its place (PlottedRouteLayers.kt, the selected-stop symbol). A grep of `app/src/main` finds
  no other copy of #EDE3D0 standing for the accent.
- **Before and after.** Before: #EDE3D0 (HSL 39°, 0.45, 0.87; relative luminance 0.775). After: #E3D3B5 (HSL 39°, 0.45, 0.80; luminance
  0.663). Contrast against the white day casing goes from 1.27:1 to 1.47:1. Against the plain pin #350560 it goes from 12.3:1 to
  10.6:1, and against the plain flag #9553A4 from 4.1:1 to 3.5:1.

## Runs

- **Red**, run 42519039246 (`97f39c0cd`, test only; filtered to MarkerGlyphsTest and PlottedRouteSelectedStopTest, 19 tests): 2 failed,
  both on the colour. `WAYPOINT_ROUTE_SELECTED fill: #FFEDE3D0 expected:<#FFE[3D3B5]> but was:<#FFE[DE3D0]>` and `the day accent is the
  dimmer cream expected:<-1846347> but was:<-1186864>` (#E3D3B5 expected, #EDE3D0 found). The same run's full-suite job (with the
  Street map deleted) had the same 2 failures out of 6906 and nothing else.
- **Revert**, run 42521087461 (`7af6f9f13` on a throwaway ref: one line, the day branch back to `Color(0xFFEDE3D0)`; filtered to the two
  classes plus FanOpeningCentreAccentTest, 22 tests). The compile log was checked first: `compileDebugUnitTestKotlin` ran, the debug APK
  built, and there were no `e:` lines. 2 failed, with the same two messages, both naming #EDE3D0, the colour the revert sets. The
  report was downloaded from this run's own artifact. The file was restored from a copy saved before the edit, and the forward
  change was confirmed present (byte-identical to the copy, and `git diff` against the forward commit empty).
- **Green, full CI on route-card**, run 42521097620 (`9201b8a3e`, pull_request): every job passed, 6960 tests, 0 failed, 24 skipped
  (the total matches CI's own summary table).
- **By name against run 42516850265** (`043eb296d`, part 1's full run): 6960 against 6960. None appeared, none dropped, none changed
  status (6936 pass and 24 skip in both). The two changed tests passed on their old values before and pass on their new values now.
  That is expected, because they were rewritten. Their red and revert runs are what shows they bite.

## Confirmed vs inferred

- Confirmed by CI (Robolectric): the day accent's pixel, read from the drawn pin and flag bitmaps, is #E3D3B5, and night stays #A2612C.
  The day accent differs from the plain pin and the plain flag. Reverting the one line brings back #EDE3D0 and fails both tests.
- Confirmed from source: the fan's copy and the accent at its place both take their colour from `routeStopSelectedColour`.
- Inferred: that #E3D3B5 still reads clearly against the light map on a phone screen. The contrast figures are computed, not seen.

## Could not determine

- How the shade looks on the S22 in daylight. No phone was used. The owner judges the shade.

## Premises that were wrong

- None found. The dispatch's premise that the palette's warm neutrals read grey holds. The ones a little darker than Cream carry 15 to
  27% saturation against Cream's 45%: NeutralGray200 #CFC9BE, OutlineVariantLight #D5CEBF and SurfaceContainerHighestLight #E2DCCC.

## Decided beyond scope

- The constant is `internal`, in PlottedRouteLayers.kt next to its only reader, and not in the theme's Color.kt. It is the accent's own
  colour, not a theme role.

## S22 step (nothing was run on a phone)

1. Day map. Open a saved route and select a stop on a waypoint, then on a trip flag. The highlighted pin or flag is a slightly darker,
   still clearly cream colour than before. It stands out from the light map and from the plain purple pins and flags.
2. Tap the selected stop's stack to fan it. The highlighted copy in the fan is the same dimmer cream.
3. Night map. The highlight is the same rust as before.

---

# Amendment 7, built

Dispatch 2026-11-06-05, Amendment 7 (RECORD -2187; prompts/preserved/2026-11-06-05-amendment-7.md on records-after-173). The owner:
"when we tap the waypoint and it highlights, have the waypoint highlight fade in and out and pulse once, the instant change makes it
look clunky. That lets you know something was selected". Restated and confirmed ("Yes, that's it"), with "Pin swells (Recommended)".
Built on route-card from `880dcbcb6`, by Hazel (Opus).

## What changed

- **The mechanism.** The accent layer (`plotted-route-selected-layer`) now reads `icon-opacity` and `icon-size` from each feature's
  `opacity` and `scale` properties (PlottedRouteLayers.kt). A frame of the motion is new data for the accent's GeoJSON source, one or two
  points, and nothing else: no layer is added, removed or restyled per frame. Feature state was not used: whether this MapLibre Android
  build exposes it was not checked, and per-feature properties are already how every marker layer here is driven.
- **The motion** is plain Kotlin on a millisecond clock (RouteStopAccentMotion.kt, new, 135 lines). It fades in over 200 ms. The swell
  starts at 150 ms, as the fade arrives, peaks at 1.15x at 300 ms, and is back to 1x at 500 ms. When another stop is picked, the old one
  fades out over 200 ms from wherever its own fade had got to, drawn under the new one. A stop picked again while it is still fading out
  arrives from that opacity, not from nothing. Under Reduce Motion the highlight is full at once with no pulse, and the old one goes at
  once. When the selection becomes none (its layer switched off, or the route closed) every highlight goes at once.
- **The driver** (MapRouteStopAccent.kt, new): `rememberRouteStopAccentFrame` runs the motion on the frame clock (`withFrameMillis`) and
  stops when it settles. `mapPlottedRoute` pushes the route as before, and pushes the highlight at each frame. The route's own push no
  longer writes the accent's source, so a route change cannot draw the accent at rest for a frame in the middle of a fade.
  SightingsMap.kt changes by the same two lines it had, so it stays at 801 lines.
- **The fan** (FanOutLayers.kt, MapFan.kt). While a highlight moves, its fanned copy is the plain copy with the accent image over it, at
  that opacity and size. The overlay sits half a place above its layer's copies in the sort order, and its offset is divided by its size,
  because `icon-offset` scales with `icon-size`. At rest the selected stop's copy is the accent copy alone, exactly as before. A highlight
  fading out is always drawn as the overlay, even at its first, still-full frame, because the hide expects a stop that is no longer
  selected by its plain image. MapFan reads the highlight's frame only while a fan has members.
- **The centre-copy fix.** The fix that matches copies by the image drawn (`expectedCopies`, FanOutRenderSignal.kt) is unchanged. The
  overlay carries the copy's feature id and the accent image, so mid-fade the copy the hide waits for is still there.

## Runs

- **Red**, run 42565996647 (`hazel-pulse-red3`: the forward code with two lines set back to today's behaviour, `frameAt` returning the
  settled accent and the fan ignoring the motion; filtered to the three classes). There were no `e:` lines and the tests ran. 6 failed:
  `the highlight starts faint, not at full (it was 1.0)`, `faint at first`, `the old highlight is still drawn, fading, for a few frames:
  [1.0, 1.0]`, the fan's two copy-shape tests (`but was:<[waypoint-pin-route-selected]>` and `but was:<[waypoint-pin]>`), and the layer
  case's precondition (`the highlight is drawn, mid-motion`). Each is a failure that snapping produces. Passed on red, as expected, and
  so these are guards rather than proof that the new tests bite: Reduce Motion (today's snap is already instant), and the two fan-opening
  mid-fade cases (today's fan has no mid-fade). The revert below is what shows that the fan-opening cases bite.
- Earlier reds, 42561630098 and 42563741461, are **not cited for RouteStopAccentPulseTest**. In the first, the test had no host activity
  (`Unable to resolve activity for Intent`). In the second, its selection writes were never applied, so nothing was ever drawn. The forward
  runs 42561645090 and 42563835284 failed the same way. Both test faults were fixed (`eb18b8327`, `3af1c6de0`).
- **Revert**, run 42563743474 (`hazel-pulse-revert2`: one line, the fan overlay's `FEATURE_ID_PROPERTY` removed; filtered). There were no
  `e:` lines and 29 tests ran. 4 failed in the fan classes, each one this revert causes: `the copies were reported drawn by frame 4,
  mid-fade`, `the copies were reported drawn before the pins leave`, and the two copy-shape tests (`but was:<[waypoint-pin]>`, the
  overlay no longer counting as the copy). Its RouteStopAccentPulseTest failures are the harness fault above, not the revert. The file was
  restored from a copy saved before the edit, and the forward line was confirmed present (2 occurrences, `git status` clean).
- **Green, full CI on route-card**: see the hand-back. The last run before it, 42565984715, failed only the layer case's precondition
  (10 frames came before the swell starts), fixed in `58e27f6b0`.

## The attribution tests (Rowan's extra item, RECORD -2187)

- The three classes assumed a 52 dp right margin for MapLibre's "i". The planner reports that the S22 shows MapLibre's 4 dp, with the
  side bar's width as the end inset. Corrected to 4 dp in AttributionClearOfLTest, AttributionUnderCardTest and
  AttributionWhileTuckedTest.
- **Red with only the margin corrected**, run 42561713129. There were no `e:` lines. Of the 11 attribution tests, 2 failed, and both were
  tucked cases, each printing `covered by [route card's strip], hidden true`. These are
  `landscape, the L left, 1 stops, peek, tucked: the spot where the card rested is clear, so the "i" comes back expected:<false> but
  was:<true>`, and the same for `portrait fullscreen, the bar left`. The corrected geometry sees the strip covering the "i".
- **Claim changes**, both in AttributionWhileTuckedTest:
  1. AttributionWhileTuckedLandscapeTest, not navigating: from "tucked the i comes back" to "tucked the strip covers the i and it stays
     hidden". The owner ruled this one: "(c) Stay hidden here".
  2. AttributionWhileTuckedPortraitTest, fullscreen with the bar left: the same change. **The owner's ruling was given for landscape.**
     Portrait fullscreen turned too, and it now asserts what the code does by D28 ("hides while any card covers that corner"). It needs
     the owner's word. To restore the old claim, drop `hiddenWhileTucked = true` from that call.
  - AttributionClearOfLTest and AttributionUnderCardTest passed with the corrected margin. Their claims are unchanged.

## Confirmed vs inferred

- Confirmed by CI (Robolectric, on the frame clock): the frames through a selection, a change of selection and Reduce Motion, as the accent's
  source receives them; the fan's copy shape mid-motion and at rest; and the fan-opening hide by the first moving frame with the highlight
  mid-fade, including with an old highlight fading out.
- Inferred, not seen: that MapLibre draws per-feature `icon-opacity` and `icon-size` smoothly at 60 fps from a GeoJSON source updated
  every frame. Also inferred: that `queryRenderedFeatures` reports a symbol at a low `icon-opacity`, which the hide relies on when a fan
  opens in the first frames of a fade.

## Could not determine

- How the motion looks and feels on the S22, and whether a per-frame `setGeoJson` on a one- or two-point source ever lags a frame
  behind the fan's own source. No phone was used.

## Premises that were wrong

- None in the amendment. The dispatch's "FanRouteStopAccent.kt draws the fan's copy" holds: the copy's image is chosen there. The drawing is
  FanOutLayers.kt's `fanFrameCollections`.

## Decided beyond scope

- **Reduce Motion is instant**, not a very short fade. The dispatch allowed either, and instant is what the app's other reduced-motion paths do.
- **The highlight animates whenever it appears on a stop**, not only on a tap. That includes opening a saved route (stop 1 selected) and
  switching its layer back on. The map cannot tell a tap from these without new plumbing. Re-selecting the same stop does not pulse again.
- **Going to none is instant** (layer off, route closed). The pin under it goes at once, so a fading accent would be left over nothing.
- The portrait attribution claim above.

## S22 step (nothing was run on a phone)

1. Day map. Open a saved route, expand the card, and tap the second stop's row. Its pin fades from purple to cream, and as it arrives it
   swells a little and settles, in about half a second. The first stop's cream fades back to purple at the same time.
2. Tap a trip-flag stop's flag on the map. It does the same, and its bubble opens.
3. Tap a stop whose pin is in a stack. The fan opens, then tap its fanned copy. The copy fades and pulses in the fan. Fold and open the fan
   again: no cream pin is left at the stack's centre once the pins leave.
4. Night map: the same, in rust.
5. Settings, Developer options, Animator duration scale off. Selecting a stop turns it cream at once, with no swell.
6. Map Layers, Waypoints off while a waypoint stop is selected: the highlight goes at once with the pins.
