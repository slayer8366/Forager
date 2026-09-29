# Map icon cluster geometry pulse (read-only, at `53c79fdc`)

**Date:** 2026-09-29.

**Recorded by:** the planner, from a read-only pulse's hand-back. It is condensed, with every citation kept.
- Read through `git show` and `git grep` at `53c79fdc` only; nothing was built, run or put on a device.
- Arithmetic is the pulse's own, marked *(arith)*.

**Paths:**
- CMU = `ui/availability/AvailabilityCompactMapUi.kt`
- MCU = `ui/availability/AvailabilityMapControlsUi.kt`
- CS = `ui/availability/AvailabilityCompactScaffold.kt`
- AS = `ui/availability/AvailabilityScreen.kt`
- MC = `ui/map/MapChrome.kt`
- SW = `ui/adaptive/ShortWindow.kt`

All are under `app/src/main/java/com/zynergylabs/forager/app/`.

**Tests:**
- MIST = `AvailabilityScreenMapIconStackTest.kt`
- LFSL = `LayoutFixesShortLandscapeTest.kt`

## Headline findings
1. **The pill beside the bar, with a fill above the pill, exists only in short landscape.** Portrait always stacks the pill below the bar. The owner's screenshot is therefore a short-landscape view. That fits the owner's ruling that the L is for phone landscape only.
2. **The pill is already as thick as the bar is wide: both are 48 dp.** The owner's "same size" is therefore already true under the reading the owner accepted.
3. **The bar and the pill sit inside one rectangular container `Surface`** (CMU:1025-1041). It is measured, dragged, clamped and minimised as a unit, and it takes touches over its full bounds. An L inside it would leave a filled region, inboard of the bar and above the pill's extension, that takes touches.
4. **Alpha:** the container is 0.6 and each child 0.5, compositing to 0.8 over the children (MC:283-312). Fill-only regions read at **0.6**.
   - This supersedes `docs/audits/2026-09-28-part-1-layout-fixes-completion-report.md:1377`, which calls the fill above the pill "0.8 chrome alpha".

## 1. What draws what, and which window takes which layout
- **The pieces:**
  - the bar is `MapIconBar` (MC:381-483);
  - the pill is `ControlPill` (MCU:177-227), inside `TrailheadControls` (MCU:137-164);
  - the container is an unnamed `Surface`, tagged `map-icon-cluster` (CMU:1024-1041, :1551).
- **The switch** is at CMU:1095-1105:
  - `if (landscapeCluster) ShortLandscapeClusterRow` gives a Row: bottom-aligned, an 8 dp gap, the pill inboard (CMU:1534-1548);
  - otherwise a Column, with the pill below the bar.
- **`landscapeCluster`** is `railPortEdge != null && punchHoleEdge != null` (CMU:409). Both need `showRail = isShortLandscapeWindow` (CS:541, :868, :902), which is `isShortWindow && ORIENTATION_LANDSCAPE` (AS:1206-1207), with a short window meaning under 480 dp tall (SW:14, :25).
- **Neither fullscreen nor recording changes the layout.**

## 2. Geometry (dp)
**The bar:**
- 5 × 48 rows: fullscreen, reset-north, locate, Layers, and the "+" (a 36 dp circle).
- 4 dp of padding top and bottom, and 4 dp between rows.
- So **48 × 264** (MC:450-480; CMU:1525).

**The pill:**
- record (Stop while recording), then return (Directions);
- the same padding, so **48 × 108** *(arith)* (MCU:198-224).

**Spacing and the container:**
- The gap between them is 8 (`CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR`, CMU:1519).
- The container has no inner padding; `MAP_ICON_BAR_EDGE_INSET` is 8 (CMU:1034-1040; MC:75).

**Container size:**
- portrait **48 × 380**, measured `[328,62.3][376,442.3]` at w384 (map-chrome follow-ups report :281);
- landscape **104 × 264**, measured `[8,60][112,324]` at 90 (part-1 report :1091).

**The visible fill in landscape:** 56 × 156 above the pill, plus an 8 × 108 strip beside it.

**Shape and edges:**
- Corners are 24 everywhere.
- The bar, the pill and the container each carry a 1 dp border and a 2 dp shadow.

**The handles:**
- The minimise handle's tap box is 20 × 72 (a recorded exception), with its mark centred on the bar's outer edge at the bar's mid-height (MC:557-619; CMU:1107-1114, :993-995).
- The restore handle is 48 × 48.

## 3. Behaviours any reshape must keep
- **Drag and snap.** A long-press drag on both handles (CMU:909-930, :1011, :1113). The side flips at ±96 dp (CMU:459). Vertical drags snap, and write the remembered offset (CMU:926-928).
- **Mirroring:**
  - the Column alignment (CMU:1099);
  - the Row swaps so the pill stays inboard (CMU:1540-1546);
  - `TrailheadControls` alignment (MCU:150-153);
  - handle shapes (MC:524-528, :659-663);
  - the landscape side is stored as port or punch-hole (CMU:159-187).
- **Memory.** Portrait and landscape keep separate offset, side and minimised state, surviving tab changes but not restarts (CMU:131-169, :410, :441-442).
- **Minimise** unmounts the whole container. The restore handle uses the last measured height (CMU:481, :986-1023).
- **Vertical clamps, all using the container's measured height** (CMU:464-482):
  - the top is at or below `topInset + compassStripClearance` (CMU:794, :841-846);
  - the bottom is at or above the nav's top, or the box bottom in fullscreen (CMU:828-835);
  - the legend bound, in portrait on the right only (CMU:821-823, :856-864);
  - the notice floor (CMU:873-880);
  - an animated re-clamp when a bound changes (CMU:961-966).
- **Hit-testing.** The container takes touches over its full bounds, deliberately (CMU:1507-1518; CLAUDE.md's `Surface` pitfall). Composition order sets who wins an overlap:
  - the strip over the cluster (CMU:729-746);
  - the handle over the container (CMU:755-766);
  - the HUD (CMU:1220-1222);
  - the nav (CMU:1293-1300).
- **The handle's 20 dp width** depends on the handle sitting over row 3 of a 48 dp bar at an 8 dp inset (MC:557-573).
- **The pill's states:**
  - Stop, filled with the record accent, while recording;
  - return disabled at 0.4 while not recording;
  - return tinted error when off track, and primary while returning (MC:729-730; MCU:217-222).
- **Things keyed on the cluster's measured width** (landscape):
  - the legend's end padding, 8 + width + 8 (CMU:1272-1276);
  - the chip-row cap (CMU:1193-1201).
- **The AddActionTile anchor** is the side, plus the offset, plus the bar's centre (CMU:895-904, :1406-1411). Its y offset, `mapIconBarRowAnchorOffset(5)`, **hardcodes 5 rows of 48 + 4** (MCU:612; MC:90-95).
- **A second `MapIconBar` caller:** `ui/log/CartographyEntryReportScreen.kt:532`. Changing `MapIconBar` itself changes that screen too.

## 4. Tests that pin the geometry
**LFSL** (`w823dp-h384dp-land`, 14 tests):
- These pin today's landscape Row, and change with the L:
  - TR2, the pill inboard and bottom-aligned (:100-120);
  - TR4, the pill inboard after a snap (:142-157);
  - TR1, the height at or below the bar's span, with return at 48 (:83-97);
  - the `bar()` helper's 4 dp (:79).
- These should stay green:
  - TR3, real touches on the pill (:124-138);
  - TR5, drag, minimise and restore (:160-182);
  - T9 (:186-194).

**MIST** (`w360dp-h640dp-xhdpi`, 104 tests, 19 ignored): the five buttons, return at or above 48, the handle's placement and size, the restore handle, real touches on locate, the pill following a drag, and the pill as the lowest clamped edge (:1010-3016).

**Bounds-based tests,** probably shape-agnostic if they still pass:
- `LayoutFixesPortraitTest` (3);
- `LayoutFixesLandscapeRulingsTest` (5);
- `LayoutFixesChipRowHeldTest` (28);
- `AvailabilityScreenLandscapeB2Test` (28);
- `AvailabilityScreenShortLandscapeTest` (13);
- `AvailabilityScreenMapLayersTest` (19);
- `JournalEntriesOnMapScreenTest`;
- `MapChromeResumedTest` (10).

**Alpha:** `ui/map/MapChromeAlphaTest.kt` (3) pins 0.6, 0.5 and the 0.8 composite.

**Not tested anywhere:** that the fill takes touches.

## 5. What "shrink a little" can take from
- Of the bar's 264 dp, 24 dp is not touch target: 4 × 4 between rows, and 2 × 4 of padding (MC:451-452).
- The floor with 48 dp rows is 240 *(arith)*. Rows under 48 break `MIN_TOUCH_TARGET` (MC:66).
- The 8 dp gap is chrome, not touch target.
- A change to the row pitch must also update:
  - `mapIconBarRowAnchorOffset` (MC:90-95);
  - LFSL's `bar()` (:79);
  - TR2's "+4 dp" (:108).
- **The pill turned 90° is 108 × 48.** An L with the bar H tall stacks to H + 8 + 48: **320 at today's H = 264, or 296 at the 240 floor.** Today's landscape cluster is 264.

## 6. Could not determine
- The screenshot itself, which is outside git.
- Whether an M3 `Surface` clip, or a custom outline, limits hit-testing to the drawn shape.
- AddActionTile's footprint against an inboard pill extension.
- Stale comments:
  - CMU:1515-1517 says there are two ignored gap-touch tests; there is one (MIST:1779-1802);
  - MC:80-82 names a Cartography anchor caller that does not exist.
