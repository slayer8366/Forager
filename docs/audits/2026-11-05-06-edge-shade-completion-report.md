# Dispatch 2026-11-05-06, completion report: see-through map windows lose their shadow and gain the map-chrome hairline (Hazel Cairnwright, Phase 2)

Built from records-after-173 at 0d6c2832d:
- the dispatch, `prompts/preserved/2026-11-05-06-edge-shade.md`;
- Amendment 1, `prompts/preserved/2026-11-05-06-amendment-1.md` (A1 to A3, B1 to B3, C1 to C4);
- the Phase 1 report, `prompts/preserved/2026-11-05-06-phase1-report.md`, re-checked where built on;
- RECORD -945, -946, -966, -967, -2153 and -2154.

The planner's later rulings, by message: -2155 (the notice "App's own notice"; the centre-pin row "Top and both sides"), -2156 (the tail "Outline around both"; the one-row notice layout; the action's colour measured, not fixed), and PENDING_RULING_3.

Base: pre-main ee0651fe0, checked against the remote at the start. PENDING_BASE. Marks: **[read]** is source read at a file and line; **[observed]** is CI output or pixels I read; **[inferred]** is reasoning.

## Summary

- **No elevation shadow on any see-through window over the map**, and the map-chrome hairline (`MapChromeOutline.kt:34`) where a window lacked one. Each window now reads the 0.8 composite right to its edge in Robolectric, by day and at night. Every sample from 2 dp in is within 2/255, and the hairline at the edge within 3/255.
- **The bubble's card and tail are one shape** (RECORD -2156, T-B). It is filled once and outlined once: the line runs out along the tail's two sides to its tip, and there is none where the tail joins the card.
- **The notice along the bottom is the app's own** (RECORD -2155, S-C). It is Material's one-row layout, rule for rule, held to Material's own side by side in one, two and four lines. Over the map it has no shadow and has the hairline; off the map it is Material's look.
- **Failing first:** 21 reds in run 42119202488, each naming a band or a missing hairline. Two design faults in my own first version of the tests were found by the run before it (42116693802) and fixed first.
- **Revert checks:** four, each on its own branch, none with a compile error, each failing only its own cases.
- **PENDING_STATUS_LINE**

## What landed

On `edge-shade`, PR #495 (draft, into pre-main), pushed. Tree clean at each step.

| commit | what it does |
|---|---|
| 0f7c6ffea | B1, tests first. `SeeThroughEdgePixelsTest` (portrait, day and night), `SeeThroughEdgePixelsLandscapeTest`, `SeeThroughEdgeSupport.kt`, `MapChromeSnackbarParityTest`, a stub `MapChromeSnackbar` (Material's own), and `MapChromeColourPixelsTest` :207-210 corrected. |
| cd2a745e0 | Test-only fix to that commit's two design faults (see "Failing first"). |
| 8ba253226 | A1, A2: the change. |
| PENDING_C2_COMMITS | |

## Every surface changed

All are over a map.

| Surface | Site | Before | Now |
|---|---|---|---|
| Every map bubble (sighting, feature, crowded stack: `MapBubbleShell` and its callers) | `MapBubble.kt:143-188`, new `MapBubbleOutline.kt` | 6 dp shadow, no line; tail filled under the card | No shadow. Card and tail one shape, filled once (the card's Surface is transparent) and outlined once inside its edge. |
| Add tile | `AddActionTile.kt:151` | 4 dp | None (its hairline was there) |
| Map type panel (Maps tab and the entry map) | `MapTypePanel.kt:114` | 4 dp | None (hairline there) |
| Legend chip | `MapLayersSheet.kt:174` | 2 dp | None (hairline there) |
| Icon bar (Maps tab and the entry map) | `MapIconBar.kt:209, :220` | 2 dp | None (hairline there) |
| Control pill | `TrailheadControls.kt:221, :232` | 2 dp | None (hairline there) |
| Cluster container | `AvailabilityMapIconCluster.kt:616` | 2 dp | None (hairline there) |
| Search pill over the map | `AvailabilitySearchUi.kt:208` | 2 dp | None (A2). Its tuck shadow on the card (`RoundedMapCard.kt:152-163`) is untouched. |
| Compass strip, portrait navigation info, landscape navigation card | `RoundedMapCard.kt` `mapCardSurface` | 2 dp graphicsLayer | Layer removed (its only job was the shadow). Their outline was there. |
| Centre-pin OK/Cancel row | `CentrePinLocationPicker.kt` `CentrePinConfirmSurface`, `CompactMapPickers.kt` `centrePinRowOutlineSides` | 4 dp, no line | No shadow. Hairline on the top and both sides, none on the bottom (-2155). The rail's line on its rail side while the rail shows. |
| Waypoint-name dialog | `AvailabilityMapOverlaysUi.kt:224` | 4 dp, no line | None, plus the hairline |
| Save-route dialog | `RouteDialogs.kt:76` | 4 dp, no line | None, plus the hairline |
| Menus: quick settings, the journal entries chip's list | `MapQuickSettings.kt:246`, `JournalEntriesChip.kt:141` | Material's 3 dp, no line | `shadowElevation` 0 and `border` the hairline, via new `MapChromeMenuEdge.kt` |
| Menus that follow an over-map flag: the entry report's menu, the Month menu, the species suggestions | `CartographyEntryReportScreen.kt:473` (`entryMapShown`), `SearchDropdownSections.kt:152` (`overMap`), `SpeciesSearchField.kt:178` (`suggestionsOverMap`) | as above | Over a map as above; elsewhere Material's 3 dp and no line, as before |
| The bubble's entry-count menu | `MapFeatureBubble.kt` | Material's 3 dp | PENDING_C2 |
| The notice along the bottom | `ScaffoldSnackbarHost.kt:83`, new `MapChromeSnackbar.kt` | Material's `Snackbar`: 6 dp, no line, no parameter for either | The app's notice. Over the Maps tab: no shadow, the hairline. Off it: Material's 6 dp and no line. |

**How the Material pieces are done** [read, material3 1.5.0-alpha26 bytecode]:
- `DropdownMenu-IlH_yew` and `ExposedDropdownMenu-kbRbctU` take `shadowElevation` and `border`, so the menus pass 0 dp and the hairline.
- Both `Snackbar` overloads call `Surface` with `shadowElevation = SnackbarTokens.ContainerElevation` and `border = null`, and take neither as a parameter, so the notice is the app's own (-2155).

## Shared components (A3)

Where a component is shown both over a map and not, which instance changed:
- **The search pill** (`SearchEntryBar`): on List, Seasonal and Journal it is the same pill, solid. It keeps its 2 dp there (`pillCorners != null && !overMap`).
- **The notice:** one host on every tab. Off the Maps tab it keeps Material's 6 dp and no line.
- **The Month menu, the species suggestions, and the entry report's menu:** each follows its own over-map flag and is unchanged where no map is drawn.
- **Changed wherever they appear, all over a map:**
  - the icon bar and the map type panel, on the Maps tab and the Cartography entry map (`CartographyEntryReportScreen.kt:621, :652`);
  - `MapBubbleShell`, for every bubble kind on every map host;
  - `mapCardSurface`, for the strip and both navigation cards;
  - `CentrePinConfirmSurface`, on the Maps tab's pin picker. Its overlay variant has no production caller and takes the default sides.
- **Material's dialogs, sheets and drawer** have no shadow and are untouched (A3).

## The notice (RECORD -2155, -2156)

**What it is.** `MapChromeSnackbar` is Material's `Snackbar(snackbarData)` with `shadowElevation` and `border` added. Read from androidx-main `Snackbar.kt`, checked against the shipped bytecode, whose constants are 600, 30, 16, 8, 6 and 8 dp:
- the 12 dp margin;
- the message in `bodyMedium`;
- the action as a `TextButton` coloured `actionColor` in `labelLarge`;
- the dismiss as an `IconButton` with Material's tooltip and "Dismiss";
- the layout of `LegacyOneRowSnackbar`. That is what 1.5.0-alpha26 runs: `ComposeMaterial3Flags.isSnackbarStylingFixEnabled` is false, and its static initialiser never sets it [read, bytecode].

**Not built:** the action on a line of its own. Material uses it only for `actionOnNewLine = true`, which the host never passes (-2156, item 2).

**The action's colour, measured as asked (-2156, item 3)** [observed, run 42122210303, `MapChromeSnackbarParityTest`]:
- Material draws the action's text in **#D0BCFF, `inversePrimary`**, not `primary` (#2E5339). Measured from the colour its text was laid out with, on both sides.
- The host passes only `actionContentColor = primary` (`ScaffoldSnackbarHost.kt:87`), and Material colours the button with `actionColor` (androidx-main `Snackbar.kt`, the `TextButton`'s `textButtonColors(contentColor = actionColor)`). So the host's comment at :49-51, "the action `primary`", does not describe what is drawn.
- Unchanged here: the notice keeps Material's behaviour exactly, and the parity test holds the two to the same colour.

## Failing first (B1)

**Predicted before the first run** (PR #495's description): every window red from its band, plus the bubble, centre-pin row and notice red from the missing hairline. Uncertain in advance: whether Robolectric's band from a 2 dp shadow reaches 2 dp in. Parity green over the stub.

**Run 42116693802 (0f7c6ffea)** [observed]:
- No compile errors in the build job's log. 6667 tests, 21 failures, 0 errors.
- 19 of the 21 named a band or a missing hairline as predicted. Two did not, which meant my tests were wrong (CLAUDE.md, "a failure that doesn't match the prediction"):
  - **The add tile's and the map type panel's side by the icon bar lies over the bar, not the map.** By day it read right 5 dp in E2DDD1 against a map composite of DCD8CD. Fixed: each pixel is held to the panel's fill over what lay under it before the panel opened, under the scrim. The scrim model is checked at the bar's middle.
  - **The rail's side in landscape was taken from where the window sat.** That is right for the narrow card and wrong for the wide centre-pin row. Fixed: read from the rail's own bounds.

**Run 42119202488 (cd2a745e0)** [observed]:
- No compile errors. 6667 tests, 21 failures, 0 errors, 24 skipped.
- Every failure names a band or a missing hairline:
  - `SeeThroughEdgePixelsLightTest` and `DarkTest`: all 8 cases each. For example, the bubble by day reads left 2 dp in E1DDD3 against E7E3D8, top edge E5E1D6 against the hairline's A29B90; the pill reads 2 dp in EAE9E9 against F0F0EF.
  - `SeeThroughEdgePixelsLandscapeLightTest` and `DarkTest`: both cases each.
  - `MapChromeColourPixelsLightTest`'s bubble: read (0.925, 0.906, 0.863) against (0.961, 0.945, 0.898).
- **Passed before:** `MapChromeColourPixelsDarkTest`'s bubble. At night over the screen's dark background the band is within 2/255, so the corrected claim bites by day only; `SeeThroughEdgePixelsDarkTest` holds the night band, over grey.
- **Passed over the stub, as predicted:** the three parity cases (Material against Material). They are shown to bite by revert check r4.
- **Two light-theme samples of the tail test passed today by coincidence:** "where the tail joins" and "filled once". There the double fill (lighter by day) and the band (darker) cancel to within 3/255. At night both darken and both failed (242424 against 3E3E3D).

## Forward run

**Run 42122210303 (8ba253226, the PR's merge with pre-main)** [observed]:
- No compile errors. 6668 tests, 2 failures, 0 errors, 24 skipped.
- The extra test is `AvailabilityScreenCrowdedIconsSettingTest`'s new case, from pre-main (PR #492). It is the only per-suite difference from 42119202488.
- All 21 reds pass. Each class's XML has 0 failures: `SeeThroughEdgePixelsLightTest` and `DarkTest` (8 each), the landscape pair (2 each), `MapChromeSnackbarParityTest` (3), `MapChromeColourPixels` (3 each), `SnackbarTrimmedTouchTest` (2), `SettingsResetSnackbarTest` (3), `BackgroundRunPromptTest` (3), the `MapTopCard` screens (22).
- **The 2 failures:** `MapChromeOutlineLandscapeDayTest` and `MapChromeOutlineLandscape780DayTest`, "the strip's end against the rail: every run across the outline holds a line pixel; 2 of 75 do not". See "Stops". PENDING_OUTLINE_RESOLUTION

## Revert checks (B2)

Runner [read, `/mnt/work/scratch/edge-shade/tools/revert.sh`, deleted with scratch at the end]:
- one edit per branch `edge-shade-rN`, from 8ba253226, each in its own worktree, so the coder's worktree was never edited;
- the edited file copied first;
- the build job's log read for compile errors before the JUnit XML;
- the forward change confirmed present in the coder's worktree afterwards (`git status` clean; the outline call, the 68 dp and the absent shadow each grepped);
- filter: `SeeThroughEdgePixels*`, `MapChromeColourPixels*`, `MapChromeSnackbarParityTest`, 29 tests each.

| Branch, run | Edit | Predicted | Observed |
|---|---|---|---|
| r1, 42122283675 | the bubble's `shadowElevation = 6.dp` put back | bubble cases only | 6 failures, all bubble: four edges and tail, both themes; `MapChromeColourPixels` bubble, both themes. By day the four-edges case reads left 2 dp in C2BEB5 against E7E3D8. |
| r2, 42122297513 | the bubble's outline draw removed | bubble hairline samples, tail sides | 4 failures: the four edges' hairline samples only (E7E3D8 against A29B90), and the tail's sides |
| r3, 42122310038 | the card outlined alone (T-A) | the tail test only | 2 failures, the tail test in each theme: sides E7E3D8 against A29B90, and "where the tail joins" A29A8F against E7E3D8 (a line across the join) |
| r4, 42122322888 | the notice's two-line minimum 64 dp, not 68 | parity, two-line cases | 1 failure: "2 line(s), action false, dismiss false, 360 dp: the whole notice ... Material bottom=92.0.dp, own bottom=88.0.dp" |

On r1, the night bubble check failed though it passed before the change. That is a failure this revert produces: with the fill now drawn by the shell and the Surface transparent, a shadow put back on the Surface is drawn over the fill, darker than the old band.

Each failure is one that edit can produce, and none is in another surface's cases. The branches and worktrees are deleted.

## CI counts

PENDING_FINAL_CI

## Not tested, said plainly

- **The phone's band.** Robolectric draws the shadow on another path (Phase 1 report, section 1). These tests show the band gone where Robolectric drew it. That it is gone on the phone, at its width there, is the device check.
- **Menus and dialogs** are not read from pixels. They open in their own windows, which a capture of the screen's root does not composite over the map. Their parameters were read from the code; the device check covers them.
- **The notice's tooltip** (a long press on its dismiss) is Material's public `PlainTooltip`, not the internal one Material's snackbar uses. It is not compared.
- **The legend chip, the icon bar, the control pill and the cluster container** are not read at their edges by the new class (B1 lists the bubble, tile, panel, pill and navigation card). `MapChromeColourPixelsTest` still reads the icon bar with its looser "darkened by 0.90 to 1" rule, which passes with or without a shadow; that is flagged in its comment.
- **Fullscreen for the centre-pin row** (its rail side drawn once the rail has gone) is not exercised. Robolectric's zero insets leave nothing to tell it from the rail layout's geometry.
- **Font scales other than 1.0** for the notice's parity.

## Device check (B3), for the planner

Phone time is scarce, so the CI APK from the PR's last green run is ready before the phone is asked for. Day theme first, then Night, each on a plain stretch of Trail Map (flat grey or cream, no lines). Screenshots are PNG, sampled as Phase 1 sampled the owner's crop.

For each window: sample a line from the bare map, through the window's edge, to its middle, at its mid-height and at its mid-width.
- **Pass:** every sample from 1 dp inside the edge to the middle is within 3/255 of the middle (no step, no band). At the edge there is a 1 dp line (Bark at 0.4 by day, white at 0.4 at night, over the fill), except on the sides the rulings leave open. The bare map 10 dp outside the window equals the map 60 dp away, within 3/255 (no cast shadow).
- **Evidence:** the screenshot and the numbers.

Cheapest first:
1. **The search pill and the strip,** at rest. Also the pill's tuck: the card just below the pill is still darker than the card further down, as before.
2. **A bubble** (tap a waypoint). Also the tail: a line along both sides to the tip, none across where it meets the card, and the fill even where they meet.
3. **The add tile** (+).
4. **The map type panel** (Layers).
5. **A menu:** the strip's three dots.
6. **The notice:** delete a test waypoint from its bubble's details for "Undo", then Undo. Check also that the notice's words, action and close button sit where they did, and the action's colour (measured as #D0BCFF here; compare on the phone).
7. **The centre-pin row:** + then Waypoint. A line on its top and both sides; one line, not two, where it meets the bottom bar.
8. **The navigation card:** Return, in portrait (the strip) and landscape (the card on the bottom edge).
9. **Repeat 1 to 8 at Night.**

Observation, not a gate: the owner's crop had the band about 19.5 dp wide at the bubble's sides. If any band remains on the phone, its width and strength are the finding.

## Decided beyond the dispatch's letter

For the planner to accept or reject:
- **The card and the tail filled once,** not only outlined once. The sliver of tail under the card read 0.96 before (dark red run: 242424 against 3E3E3D). Under T-B the outline marks one shape, and filling it twice inside would read as a darker patch beside a line-free join.
- **A grey stand-in map,** (181, 180, 176) from the owner's crop, under every read. Over the screen's own dark background a band changes almost nothing at night, so a check there could pass with the band drawn.
- **Each panel's side by the icon bar read over the bar,** from a capture before it opened, under the scrim.
- **The notice's shadow kept off the map** (Material's 6 dp), so it is unchanged there (A3).
- **The icon bar's, control pill's and cluster's comments** say their 2 dp darkened the whole fill rather than drew a band, as Phase 1 found for narrow pieces.
- **The planner's worktree** was not touched. The Phase 1 report's figures were re-checked where built on: the site list (my `git grep` matched it), the menus' parameters and the snackbar's lack of them (bytecode).

## Premises that were wrong

- **"The action on a new line where Material puts it"** (-2155): Material does that only for `actionOnNewLine = true`, which the host never passes. Ruled in -2156: not built.
- **The host's comment that the notice's action is `primary`** (`ScaffoldSnackbarHost.kt:49-51`): it is drawn in `inversePrimary` (see "The notice"). Reported, not changed.

## Stops

PENDING_STOPS

## Could not determine

- Whether the phone takes Skia's GPU shadow path for these shapes. That is now moot for the windows changed, which draw no shadow.
- The phone's look of the tail's step (see "Stops"). The step's geometry is the same as before the change; only the outline makes it visible.
