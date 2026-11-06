# Motion Part 1: code and tests written, nothing compiled yet

Dispatch 2026-09-28-652 (RECORD intent -652; preserved at `prompts/preserved/2026-10-07-06.md` on branch
`records-after-173`). The owner's choices: RECORD -651. The scout: `docs/ui/2026-10-07-motion-scout.md` on branch
`motion-scout` (8a40c6d4). Branch `motion-part-1`, cut from `origin/main` at `aa79f25a`, which is the base the dispatch
names; `origin/main` was still `aa79f25a` when this was written (2026-10-07T20:23Z). No PR.

**Status: written, not run.** The dispatch held Gradle for the owner's go, after the back-by build (-645). Nothing on this
branch has been compiled, no test has run, and no revert check has been done. Every claim below about behaviour is what the
code is written to do, not something observed.

## What was built

All paths are relative to `app/src/main/java/com/zynergylabs/forager/app/ui/`.

1. **Reduce motion, app-wide.** `motion/ReduceMotion.kt` gains `ProvideReduceMotion`, which reads both animation settings
   through the existing `isReduceMotionEnabled`, re-reads them whenever either changes (a `ContentObserver` on both
   `Settings.Global` URIs), and provides `LocalReduceMotion`. `theme/Theme.kt`'s `ForagerTheme` wraps everything in it, so
   it is app-wide (`MainActivity` has one `ForagerTheme`, at its root). The marker fan (`map/fanout/MarkerFanOutState.kt`)
   and the restore page (`backup/RestoreLoadingPage.kt`) read the local instead of the settings.
2. **Map icon bar press highlight (B1 to B8, B16).** `map/MapChrome.kt`: `MapBarIconButton` keeps its `clickable` on the
   full 48 x 48 box with no indication of its own, and a new `PressHighlight` (`motion/PressFeedback.kt`) draws the same
   press on a separate box, clipped to `MapBarHighlight.ROUNDED_SQUARE` (40 x 40, 12 dp corners, 4 dp in) or
   `MapBarHighlight.BADGE` (the 36 dp badge circle) for Add and Record. The arithmetic for why both fit inside the bar's 24 dp
   ends in portrait and in the landscape L is on `MapBarHighlight`. The minimise and restore handles draw their press on their
   own drawn marks. The entry map's bar (B16) uses the same button.
3. **Other hard-cornered highlights.** The tappable snackbar (S7), the legend chip (M4), the species card (L1) and the
   swipe-row close tap on Journal entry cards (J16) draw their press clipped to their own shape, by the same means: the tap
   stays where it was, only the drawing moves.
4. **Press bounce.** `pressBounce` (`motion/PressFeedback.kt`) dips a map bar icon and its badge to
   `MotionTokens.PRESS_BOUNCE_SCALE` (0.88, chosen) while pressed and springs back on `feedbackMotionSpec`. Not under reduced
   motion.
5. **Icon crossfades.** `IconSwap` (`motion/IconSwap.kt`): a crossfade on a new `MotionTokens.iconSwapSpec`
   (`fastEffectsSpec`) with the incoming icon growing from `ICON_SWAP_ENTER_SCALE` (0.85, chosen) on the feedback spec; a
   plain crossfade under reduced motion. Used by every map bar row (B10 fullscreen, B11 record and stop, B12 return and
   cancel), the camera's flash, timer and grid chips, and the Journal short window's search icon (J19). In the bar the red
   badge fades with it, and B12's tint and 40% disabled dimming fade on the same spec. Content descriptions moved from the
   icons to their buttons, since both icons are composed for the length of a swap.
6. **Cluster glide (B14, B15).** `availability/AvailabilityMapIconCluster.kt`: on release the cluster is laid out where it
   lands at once, exactly as before, and only its drawing glides there from the finger (`clusterGlideDraw`, a draw-only
   translation), so its touch box never sweeps across the map. The handle side and the landscape pill's order change when it
   lands (`ClusterGlide.sideChangePending`). Released short, it glides back. Instant under reduced motion. A drag cancelled by
   the system still never changes side, as before.
7. **Search dropdown tap-through (Q5).** `availability/AvailabilityCompactScaffold.kt`: the dropdown's `AnimatedVisibility`
   carries `leavingTakesNoTouches` (`motion/LeavingTakesNoTouches.kt`). While it closes, the panel is clipped to an outline
   outside its bounds, so no new touch reaches it and every tap goes beneath; what it was drawing is recorded into a graphics
   layer and drawn in its place, so the shrink and fade still show.

`docs/motion-spec.md` §2 and §4 record the new category (icon swap) and three new reduced-motion rows.

## Verified before building, and how

Against `aa79f25a`, by reading:

- `LocalReduceMotion` was declared and never provided: `motion/ReduceMotion.kt:23`, and a repository grep found no provider.
- The fan read the settings itself (`map/fanout/MarkerFanOutState.kt:74-86`, through `isReduceMotionEnabled`, both scales);
  the restore page read the animator scale alone (`backup/RestoreLoadingPage.kt:96`).
- `MapBarIconButton` was a 48 x 48 `Box` with a plain `clickable` and no shape (`map/MapChrome.kt:801-806`); the minimise
  handle's 20 dp width is the recorded exception at `:623-664`; the landscape L's full-square hits are `fullSquareHits`.
- The dropdown's dismiss scrim is composed only while `showSearchDropdown` (`availability/AvailabilityCompactScaffold.kt:1307`)
  while its `AnimatedVisibility` animates the exit (`:1378`), so Q5's window is real in code.
- Material3 1.5.0-alpha26's `Snackbar(snackbarData, modifier)` applies `modifier.padding(12.dp)` before its surface: read
  from the library's bytecode (`javap` of `SnackbarKt`), not from memory.

## Premises that were wrong or incomplete

- **The scout's S7** inferred a sharp highlight over the snackbar's rounded corners. Incomplete: because of the 12 dp padding
  above, the old highlight was a rectangle 12 dp larger than the snackbar on every side, and **the tappable snackbar takes
  touches 12 dp beyond its drawn edge**, over the map. That touch area is unchanged here (not this dispatch's to change);
  reported as a finding.
- **"Move the marker fan and the restore page onto it, without changing what they do."** For the fan this holds. For the
  restore page it does not quite: it read only the animator scale, and `LocalReduceMotion` is true when either scale is 0, so
  with the transition scale alone at 0 the page now leaves at once where it used to animate. See stop 1.
- Not checked, from memory only: Compose on Android already shortens its own animations by the system animator duration
  scale, so under "Remove animations" (both scales 0) the new animations would finish at once even without the local; the
  local is what covers the transition-scale-only case and the grow, bounce and glide that are dropped rather than shortened.

## Stops for the owner (not decided here)

1. **Restore page, transition scale alone at 0**: now leaves at once (it animated before). Keep (matches spec §4: "either
   one alone at 0") or make the page read the animator scale only, as before?
2. **Cluster glide, side change.** To make the handle side and pill order change *when it lands* while the touch box lands at
   once, the landed box is arranged for its old side for the length of the glide. A tap on it then would reach whatever the old
   arrangement put there, so the box takes such taps and does nothing for that moment (the guard,
   `MAP_ICON_CLUSTER_GLIDE_GUARD_TAG`). That is a short-lived touch-area change. The alternative keeps touches exactly as
   today by switching the arrangement at release, so the glide would show the new side's arrangement flying across. Which?
3. **Search dropdown, a finger already down on a button when the close begins** (a second finger, or Back, while one is held)
   is not cancelled. Cancelling it needs a pointer handler on the open panel's whole box, which would change the open panel's
   touch area (its bare background passes taps to the dismiss scrim today). Leave as is?
4. **Bounce scope.** Built on the map bar's icons only (bar, pill, entry map bar). RECORD -651's summary reads "every press
   highlight and bounce". Should every icon button in the app bounce (camera chips, toolbars, sheet icons), or the map bar only?
5. **Handles' highlight shape**: drawn on the handles' marks (10 dp wide), the most literal "follows its control's shape". It
   may be hard to see under a finger. Confirm on the phone, or say if the rounded tap box is wanted instead.
6. **Record's highlight is round even when not recording** (a plain dot, no badge), so it does not change shape under the
   finger as the tap starts a recording. Confirm.
7. **Highlights left as they are**: the scout's rectangles that do not mismatch a shape (L2, M12, Q3, C6, N12, T4, T5, R3,
   R20, M10). Also not in the scout's list but the same mechanism as J16: the waypoint and offline-region swipe rows sit on
   rounded cards and keep a rectangular close highlight. Round those too?
8. **The camera's Location chip** (in the scout's K3 with flash, timer and grid) is not crossfaded, because the dispatch names
   flash, timer and grid only. Include it?

## Tests written (none run)

Under `app/src/test/java/com/zynergylabs/forager/app/ui/`:

- `motion/ProvideReduceMotionTest.kt`: the setting reaches a screen under `ForagerTheme` from `Settings.Global`: on, animator
  0, transition 0 alone, and a change while running (the test sends the provider's change notification itself).
- `availability/MapBarPressFeedbackTest.kt`, in portrait and the landscape L at 90 and 270: every highlight lies inside its
  stadium, corner arcs included; plain rows 40 dp rounded squares, Add and Record 36 dp circles; real touches in the strip
  between each row's edge and its highlight still reach that row (fullscreen, add, record, return), with the L's corners
  included; the minimise and restore handles take touches across their boxes; a long-press 6 dp beside the cluster reaches the
  map and one on a row does not; the icon dips while a finger is down and not with animations off (where the press still
  lands); mid-swap the fullscreen row carries one description.
- `availability/MapIconClusterGlideTest.kt`: dragged across, the touch box lands at once and a tap where the finger lifted
  reaches the map mid-glide; the handle keeps its side until landing, then moves; a tap on the landed box mid-glide fires
  nothing and works after landing; after a glide a long-press beside the cluster reaches the map; released short, nothing is
  guarded and the box is at rest; with animations off it lands at once.
- `availability/SearchDropdownClosingTapThroughTest.kt`: mid-close, a touch on "Use current location" reaches the map and the
  button does not fire; positive control, the same touch on the open panel fires the button.
- `availability/SpeciesCardCornerTouchTest.kt`: touches 2 dp in from each corner of a species card still open it on the map.

**Existing test this change is expected to break, untouched:** `map/fanout/MarkerFanOutHostTest`, "with the system's
animations off the markers are spread at once, and fold at once". It sets the animator scale and composes `MarkerFanOutHost`
with no `ForagerTheme` around it, so with the fan now reading `LocalReduceMotion` it sees the default, false, and animates.
The fix would be to compose it under `ForagerTheme` (or `ProvideReduceMotion`); left for the planner, per the dispatch.
`RestoreLoadingPageTest` and `RestoreReturnsToMapTest` compose under `ForagerTheme` and should keep passing.

**Revert checks planned** (each from a saved copy, build log checked for compile errors first): remove
`.leavingTakesNoTouches(...)` (the mid-close test must fail on the button or the map count); make the glide move the layout instead
of the drawing (animate the drag's own offset back to 0), so the touch box sweeps (the mid-glide map tap must fail); clip the species card before its
`clickable` (the corner test must fail); put `contentDescription` back on the bar icons (the one-description test must fail);
drop `pressBounce` (the dip test must fail); give the rounded square an 8 dp corner (the stadium test must fail in the L).

## Unverified because nothing has been compiled

Everything, and in particular: that the code compiles; that `leavingTakesNoTouches` hides a clipped subtree from hit testing
and that its recorded layer draws outside the clip, on the phone and under Robolectric; that `onPlaced` coordinates read in
the draw phase give the glide its start without a one-frame flash; the timing windows the tests catch (a press registering
within 300 ms, two frames being mid-glide and mid-close); that Robolectric delivers the settings change notification to the
observer; and every look and feel item below.

## Device checks (S22, after the build)

The highlights in portrait and landscape, light and dark, over the 80% chrome, on every row, both badges and both handles; the
bounce with the phone's animations on and with them turned down; the crossfades on the bar, the camera chips and the Journal
search icon; the cluster glide across and back, and the moment it lands; the search dropdown's close, with a tap during it.

## Amendment 1 (RECORD -657), applied 2026-10-07, also not compiled

The owner's answers to the stops above, and what changed for each. Still code and tests only: nothing compiled or run.

- **Stop 2, cluster: "At release".** The handle side and the pill's order now switch the moment the finger lifts, and the
  cluster glides there already in its new order. The guard that swallowed taps mid-glide is gone, so touches are as before the
  glide existed throughout. `MapIconClusterGlideTest` now checks that the handle is on its new side mid-glide and that a tap on
  the landed row mid-glide reaches it.
- **Stop 4, bounce: "Every icon button".** Every `IconButton` (30 calls) and `FilledIconToggleButton` (2) in the app now goes
  through `BouncingIconButton` / `BouncingFilledIconToggleButton` (`ui/motion/BouncingIconButton.kt`), which bounce their
  content. The find editor's remove glyph bounces too. Not covered: rows that pair an icon with text (Settings rows, crash-log
  rows, the swipe actions, the HUD's "Try again"), which are rows rather than icon buttons, and the camera's shutter. Test:
  `motion/BouncingIconButtonTest`.
- **The snackbar: "Trim to its visible edge".** The tap, the test tag and the chrome-colour semantics moved from the
  snackbar's modifier onto a box laid exactly over the drawn surface (12 dp in). A touch beside the drawn snackbar now reaches
  the map. This is a touch-area change, made on the owner's instruction. `COMPACT_SNACKBAR_TAG`'s bounds are now the drawn
  surface, 12 dp smaller on each side. The existing tests that read them assert inequalities that still hold, as far as I can
  tell from reading them. Test: `availability/SnackbarTrimmedTouchTest`, real touches 6 dp outside and 4 dp inside each of the
  four edges.
- **Stop 1, restore page: "Keep instant".** No change.
- **Settled by -651:**
  - The camera's Location chip now crossfades.
  - Record's highlight stays round.
  - Every swipe row's close highlight is rounded by default, and the waypoint rows take the card's own shape.
  - The other hard-cornered highlights the scout lists are rounded: L2, M12, Q3, C6, N12, T4, T5, M10, and R3 and R20 through
    `opensRecordDetails`. These use `clickableWithShapedPress` / `shapedPressLayer`, a draw-only state layer at Material's
    pressed opacity in a rounded shape, not a clipped ripple. Clipping a ripple would clip the row's content and its children's
    touches too. The taps stay where they were.
- **Stop 3**, a finger already held on a dropdown button when the close begins: left as it is.
- **`MarkerFanOutHostTest`:** its content is now composed inside `ProvideReduceMotion`. Its assertion is unchanged.

## Amendment 2 (RECORD -659), applied 2026-10-07, also not compiled

- **"Shutter yes, rows no".** The camera shutter's drawn disc bounces on a press, and not under reduced motion. The touch box,
  its circular clip, the ripple, the tag and the description stay on the outer node, so its touch is unchanged.
  `ShutterButton` is in `ui/log/InAppCameraDialog.kt`. Test: `log/InAppCameraShutterBounceTest`, through `InAppCameraDialog`
  with a real finger, run with animations on and off. Rows that pair an icon with words stay without a bounce.
- **"Rounded shade".** `clickableWithShapedPress` is kept as it is, to be judged on the S22.

## Build and test results (RECORD -664, the owner's Gradle go), 2026-10-07

Run on `motion-part-1` after merging `origin/main` (98c31b32, which then carried only a CLAUDE.md note over `aa79f25a`). Every
Gradle run went through `systemd-run --user --scope -p MemoryMax=5G -p MemorySwapMax=0`, with the Gradle heap at 1536m, the Kotlin
daemon at 2g and Java temp at `~/.cache/forager-test-tmp`. No daemon was running before the first run. Free disk stayed between
5.4 and 5.7 GB. The Kotlin daemon was stopped after compiling and before the full suite, and `./gradlew --stop` ran at the end.
No phone or emulator was used.

- **Compile.** The first compile failed on one error: `drawOutline` was not imported in `PressFeedback.kt`. The fix is
  a4dbcf3a. The second compile, main and unit tests, succeeded.
- **New tests, first run.** 63 tests, 2 failures. Both were mine, and both had the same cause. With the test clock stopped, a
  state change made outside composition (the Back press, the finger lifting) only reaches the next frame once it is applied,
  so the tests stepped frames that had not yet seen it. The fix applies the change before stepping, as the fan's tests already
  do (2082a0c0).
- **Revert checks.** Each one edits a saved copy, checks the build log for compile errors before reading any result, and
  restores from the saved copy, never from git. Every file was confirmed identical to the forward version after its restore.
  - R1: dropdown pass-through removed. Failed: "the closing panel's button did not fire, expected 0 but was 1".
  - R2: the glide moved to layout. Failed: "the finger lifted clear of the landed box", and the box was not back at rest at
    once. Both failures belong to this edit.
  - R3: species card clipped before its tap. The first attempt hit a compile error (no `clip` import), so the runner refused to
    read its results. Re-run with the import: "the touch 2 dp in from the card's top left corner did not open it on the map".
  - R4: content descriptions put back on the bar icons. **Did not fail.** The outgoing icon recomposes with the row's current
    description, so the mid-swap two-descriptions problem I designed that test against does not happen. The test is kept as a
    pin and marked in its doc as not evidence that the move was needed.
  - R5: bounce target set to 1. Failed: "dipped while held: 1.0" in the bar, `BouncingIconButtonTest` and the shutter test. A
    fourth failure in that run, in the crossfade test, came from the stopped-clock problem above, not from this edit. It is
    fixed.
  - R6: highlight inset set to 0. Failed: the stadium-fit and 40 dp-size checks in the L.
  - R7: provider removed from `ForagerTheme`. Failed: three cases, "expected true but was false".
  - R8: snackbar trim undone. Failed: "6 dp outside the bottom edge … the map took the touch".
  - R9: the change observer's update removed. Failed: "after the change".
  - The snackbar test now skips "outside" samples that fall off the screen. In portrait the drawn snackbar spans the full
    width, so only its top and bottom edges have a beside.
- **Full suite.** 497 classes, **4,015 tests, 0 failures, 24 skipped**. No existing test broke. The one I expected to break,
  `MarkerFanOutHostTest`, was wrapped in the provider by Amendment 1.
- **Not merged:** `origin/main` moved to `4f078b8b` (PR #191, failure-fixes) after this build started. This branch has not been
  merged with it or rebuilt against it.

## Merge with failure-fixes (`origin/main` 4f078b8b) and rebuild, 2026-10-07

- **Conflicts, two:**
  - `docs/audits/README.md`: both sides' index rows kept.
  - `availability/AvailabilitySearchUi.kt`, the species field's trailing icon. Failure-fixes (RECORD -658, F3) removed the
    unreachable location icon. This branch had only renamed that icon's `IconButton` to `BouncingIconButton`. Not the same
    logic changed twice: main's removal is kept, and with it the rename goes.
- **Merged cleanly, checked by reading:** `CrashLogPanel.kt` (main's guarded read; this branch's shaped presses and bouncing
  share button), `TrackExportPanel.kt` (main's partial-export Toast; this branch's bouncing share button) and
  `AvailabilitySettingsUi.kt` (main's `SettingsCheckboxRow` refactor; this branch's two shaped-press rows). Both sides' changes
  are present in each, and the logic of this branch's files is unchanged, so no new revert checks were run. None of main's new
  code adds an `IconButton`.
- **RECORD -660:** `SearchEntryBar`'s unused `onUseCurrentLocation` parameter is removed, along with its two arguments in
  `AvailabilityCompactScaffold`. The scaffold's own `onUseCurrentLocation` still feeds the dropdown's button.
- **Rebuild.** Same caps. No daemon before the run, the Kotlin daemon stopped after compiling, 5.4 GB free. Compile clean.
  Full suite: 505 classes, **4,053 tests, 0 failures, 24 skipped**. `./gradlew --stop` at the end.
