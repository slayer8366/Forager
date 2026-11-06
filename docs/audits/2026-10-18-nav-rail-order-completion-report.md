# Rail order and an untinted Tools drawer: completion report (dispatch 2026-10-18-08 and Amendment 1)

Hazel (app coder), 2026-10-18, written 20:30Z. RECORD -1312 (rail order) and -1313 (Amendment 1, the drawer's tint).
Branch `nav-rail-order` from pre-main `d942e022`, which matched `origin/pre-main` at the start. PR #260 into `pre-main`,
**not merged**: it merges on the owner's word after the phone check.

**Status: built, with tests and reverts done; full CI 39163471748 at `13ff24d6` is green, 5,115 tests, 0 failures, 24 skipped (JUnit XML). No Stop was hit for -1312 or -1313. **Amendment 2 (RECORD -1318) is stopped before building, on its first Stop; see its section at the end.** The phone steps at the
end are still to run.

## What landed

| Commit | What |
| --- | --- |
| `13ff24d6` | Rail order, the drawer scrim, the two new test files, and the two existing tests updated to the new order |
| (this commit) | This report and its index row |

- **One place sets the order:** `RAIL_ORDER_TOP_TO_BOTTOM` in `AvailabilityNavigationUi.kt`, which is
  `CompactTab.entries.reversed()`. `ForagerNavigationRail` loops over it, where it used to loop over `CompactTab.entries`.
  The bottom bar still loops over `CompactTab.entries`, so portrait is unchanged.
- **The list is reversed, not the arrangement.** So the semantics tree runs top to bottom in the same order as the
  screen. The alternative was to keep the entries and lay them out from the bottom, which would leave the tree List
  first while the screen shows List last. I rejected it: the reading order would then depend on how the platform sorts
  by position, not on this list. The reason is in the KDoc.
- **Drawer scrim:** `ModalNavigationDrawer(scrimColor = Color.Transparent)` in `AvailabilityScreen.kt`, at the drawer
  the planner found (line 1951 at base). I used Transparent, not `Color.Unspecified`. In material3 1.5.0-alpha26,
  `Scrim.kt` only draws the scrim, and only attaches its tap-to-close (`detectTapGestures`) and its "Close drawer" click
  semantics, `if (color.isSpecified)`. With Unspecified there is no scrim at all, so a tap outside the drawer no longer
  closes it. I read this from the published sources jar, and revert R3 confirms it on CI. The drawer sheet itself is
  unchanged.

## Verify first

1. **Where the rail is built.** Every rail is a `ForagerNavigationRail` (`AvailabilityNavigationUi.kt`), called from
   three places: `AvailabilityCompactScaffold.kt:969` (beside the content, rail on the left), `:1853` (beside the
   content, rail on the right), and `AvailabilityCompactMapUi.kt:1949` (over the map). `git grep` finds no other call.
   The only other rail import is unused: `AvailabilityScreen.kt:273-275` imports `NavigationRail` and
   `NavigationRailItem`, and nothing calls them. The tablet in landscape uses the same phone tree and the same rail
   (`AvailabilityScreenTabletAsPhoneTest`, `w1318dp-h824dp-land`), so the one list covers it too. All three call sites
   read the order from inside `ForagerNavigationRail` and pass no order of their own.
2. **Which side the rail is on, by turn direction.** `mainWindowRailEdgeFor` (`ui/adaptive/PortEdge.kt:43`) returns
   `ScreenEdge.Right` for any landscape window, whatever the rotation (RECORD -956). `AvailabilityScreen.kt:1485`
   passes that to every rail as `portEdge`. So the rail is on the screen's right at ROTATION_90 and at ROTATION_270.
   Unchanged. The measured rails sit at the window's right in both rotations (for example `left=743 right=823` at
   823 dp wide).
3. **Anything keyed to the rail's item positions.**
   - Two tests pinned the old order, and both now expect the new one. `AvailabilityScreenShortLandscapeTest.assertRailColumn`
     checked "below the one before" in `CompactTab` order. It now uses `RAIL_LABELS_TOP_TO_BOTTOM`, Tools to List. The
     portrait bar-row checks still use `RAIL_LABELS`, List to Tools. `NavigationRailEvenSpacingTest.labels` is now Tools
     to List. Neither assertion was loosened.
   - Nothing in main code reads where an item is: no focus order, `traversalIndex`, `FocusRequester` or position
     callback on the rail's items (grep of the three files). The rail only reports its width (`onRailWidthMeasured`).
     The Journal's search icon, the fan and the icon bar measure the rail's bounds, not its items, so they don't change.
   - **Screen reader order.** With the list reversed, the semantics tree order is Tools, Journal, Maps, Seasonal, List.
     A new test checks this (`the rail's semantics order is the screen's`). Compose orders accessibility traversal by
     position, top to bottom. Tree order and position now agree, so I expect TalkBack to read the rail top to bottom,
     Tools first and List last, the same as the screen. That is the planner's reading ("follow the visual order"), and it
     needed no added code and no choice, so I didn't stop. Not verified: what TalkBack actually says on a phone. This is
     an observation step below. The portrait bar reads List first, so moving between portrait and landscape gives a
     screen reader user the opposite order. That follows from the owner's visual ruling and is raised as an owner
     question, not as a blocker.
4. **Every drawer and scrim (Amendment 1).** `git grep` across `app/src/main` finds one `ModalNavigationDrawer` (the
   Tools drawer, `AvailabilityScreen.kt`) and one `ModalDrawerSheet` inside it. There is no `DismissibleNavigationDrawer`
   and no other `scrimColor`. The search dropdown has its own scrim (`SEARCH_DROPDOWN_SCRIM_TAG`). It isn't a drawer, so
   I didn't touch it.

## Evidence

**Filtered CI 39162247558 at `13ff24d6`** (workflow_dispatch, the five affected classes), green. The JUnit XML has 9
files and **39 tests, 0 failures, 0 errors, 0 skipped**:
NavigationRailOrderPhoneRotation90Test 6, NavigationRailOrderPhoneRotation270Test 6, NavigationRailOrderTabletTest 3,
ToolsDrawerNoTintPortraitTest 3, ToolsDrawerNoTintLandscapeTest 3, AvailabilityScreenShortLandscapeTest 11,
AvailabilityScreenPortraitBottomNavPinTest 1, NavigationRailEvenSpacing780Test 3, NavigationRailEvenSpacing823Test 3.

- **Order by item bounds, through the real `AvailabilityScreen`.** Every test first confirms the screen saw the pinned
  rotation. Over the map, and beside List, Seasonal and Journal, at ROTATION_90 and ROTATION_270 on the phone
  (823x384), each item lies wholly above the one before it, from List up to Tools. Measured, the same at both
  rotations: List 308-364, Seasonal 236-292, Maps 164-220, Journal 92-148, Tools 20-76 (dp, top-bottom). The landscape
  tablet (1318x824), over the map and beside List: List 671-747 up to Tools 77-153. Tools has no case of its own,
  because it opens a drawer over the current tab rather than a screen.
- **Real touches on List at the bottom of the rail.** Five points across List's own bounds, its centre and four
  points 30% of the way toward its corners. Each one starts on Maps, selects List, and takes the map off screen.
  First the test asserts that List is the lowest item. This runs on the phone at both rotations and on the tablet.
  The existing `NavigationRailEvenSpacingTest` real-touch test (21 touches) still passes.
- **Drawer, no tint, read from drawn pixels** (`captureToImage`, native graphics). The map is a stand-in filled with
  `#FF2E7D32`. With the drawer closed, a column of points outside the sheet is kept only where the point shows that
  exact colour: 6 of 15 in portrait (411x891, sheet 0-360), 14 of 15 in landscape (823x384, sheet 463-823). With the
  drawer open, every kept point still reads `#FF2E7D32`.
- **A real touch outside the sheet closes the drawer** (portrait at (385.5, 668.25), landscape at (231.5, 216)), and so
  does **Back**. Closed means the sheet is not displayed and Tools is not selected.

## Revert checks

Each is a one-line edit on its own branch, run on CI with the same five-class filter. For each run I read the whole
job log: `BUILD SUCCESSFUL` for the debug APK and the release compile, and no `e: ` lines. Each artifact's JUnit XML
is that run's own, with 39 tests as on the forward run. I made each branch from the committed forward head and
restored the working tree from a copy saved before editing. After each check, `cmp` against the saved copy confirmed
the forward files were intact.

| Check | Branch / commit | Edit | CI run | Result |
| --- | --- | --- | --- | --- |
| R1 | `nav-rail-order-revert-1` `f501b6ab` | `RAIL_ORDER_TOP_TO_BOTTOM = CompactTab.entries` (no `.reversed()`) | 39162870196 | 18 of 39 failed, all rail-order assertions |
| R2 | `nav-rail-order-revert-2` `671c821e` | `scrimColor = Color.Transparent` line removed (Material's default) | 39162886721 | 2 of 39 failed, both tint tests |
| R3 | `nav-rail-order-revert-3` `2f6ca416` | `Color.Transparent` → `Color.Unspecified` | 39162890429 | 2 of 39 failed, both tap-outside tests |

- **R1:** all 15 new order tests failed. That is every case in the three `NavigationRailOrder*` classes, for example
  "over the map: Seasonal (bottom 148.0.dp) lies wholly above List (top 20.0.dp)…", "List (…top=20.0.dp…) is the
  lowest item on the rail", and the semantics test, "expected:<[Tools, Journal, Maps, Seasonal, List]> but was:<[List,
  Seasonal, Maps, Journal, Tools]>". 3 `AvailabilityScreenShortLandscapeTest` cases also failed, each "Journal (top
  244.0.dp) is below Tools (top 324.0.dp)". Each of these can only come from the order being reverted. The drawer tests
  passed.
  **`NavigationRailEvenSpacingTest` passed under R1 as well as forward.** Its margin and gap checks are symmetric
  (reversed, the margins are swapped and still equal), so it doesn't test order. It was updated to keep its labels
  consistent, and I don't cite it as order evidence.
- **R2:** only the two tint tests failed: "the map at (385.5.dp, 111.375.dp) keeps its colour #FF2E7D32; it reads
  #FF1F5421" (portrait), and the same in landscape at (231.5.dp, 48.0.dp). #FF1F5421 is the map colour under the
  default scrim. Tap-outside and Back still passed, as they should with the default scrim.
- **R3:** only the two tap-outside tests failed: "The component with TestTag = 'tools-drawer-sheet' is displayed!"
  after the touch outside. The tint tests passed, because with Unspecified nothing is drawn. So the tap-outside test
  catches the one-word mistake the KDoc warns against.

## Full CI

| Run | Head | Result |
| --- | --- | --- |
| 39164638434 (pull_request) | `a52f2f0b` (report commit) | **green: 5,115 tests, 0 failures, 0 errors, 24 skipped** (JUnit XML, 689 files) |
| 39163471748 (pull_request) | `13ff24d6` | **green: 5,115 tests, 0 failures, 0 errors, 24 skipped** (JUnit XML, 689 files); build, lint and release-compile jobs green |

## Not verified

- **On a phone:** the order, touch and tint behaviour on real hardware, and what TalkBack actually reads. Robolectric
  reports zero insets (CLAUDE.md), so where the rail sits against real system bars is a device item. This change
  doesn't affect that.
- **The tint on tabs other than Maps.** The pixel test runs on the Maps tab, over the map stand-in. There is one
  scrim for every tab, so I expect the other tabs to behave the same, but that is inferred, not measured.
- **A rail with no room for its items.** `RailItemsEvenly` still stacks from the top, so if the rail is too short for
  five items, List, now last, is the one pushed off the bottom, where Tools used to be. No test window is that short.
  This is in the KDoc.

## Owner questions (none blocking)

1. **Screen reader order.** In landscape TalkBack should now read the rail Tools first and List last, matching the
   screen. In portrait it reads List first. That keeps "reading order follows what is drawn". The alternative is to
   make landscape read List first, against the visual order. I picked nothing beyond the planner's stated reading.

## Phone steps (S26 Ultra, S22 Ultra, tablet)

On the PR's debug APK. Cheap steps come first. Evidence is a description in words, not screenshots on GitHub.

1. **Portrait unchanged.** Hold the phone upright. Pass: the bottom bar reads List, Seasonal, Maps, Journal, Tools from
   left to right.
2. **Landscape, turned one way.** Turn the phone so the old bottom edge is on the right. Pass: the rail is on the right
   and reads List at the bottom, then Seasonal, Maps, Journal, and Tools at the top.
3. **Landscape, turned the other way.** Turn it so the old bottom edge is on the left. Pass: the same as step 2, with
   the rail still on the right and List still at the bottom.
4. **Each tab opens its own screen.** In landscape, tap List, Seasonal, Maps and Journal in turn. Pass: each one opens
   its own screen, and the rail keeps the same order beside each. Tap Tools: the Tools drawer opens.
5. **Drawer with no tint, portrait and landscape.** Open Tools on Maps, and again on List. Pass: the app beside the
   drawer keeps its normal colours, with no grey or dark wash.
6. **Tap outside and Back.** With the drawer open, tap the app outside the drawer. Pass: the drawer closes. Open it
   again and press Back. Pass: it closes.
7. **Tablet.** Repeat steps 1 to 6 on the tablet.
8. *Observation, not a gate:* with TalkBack on, in landscape, swipe through the rail. Note the order it reads.

## Amendment 2 (RECORD -1318): stopped before building

Added 21:10Z. I read the amendment and RECORD -1318 in full and did the verify-first steps. No code was written for it.
The space reserved for the hidden bar would make the chip line jump whenever the Offline maps chip is chosen or left.
That could read as broken, which is the amendment's first Stop, so the choice goes back to the owner.

### Verify first

- **Where the search reveal happens.** It happens only in a landscape window, phone or tablet.
  `AvailabilityCompactScaffold.kt:1073` hides the header only when
  `showRail && compactTab() == JOURNAL && !journalScreenState.searchHeaderRevealed`, and `showRail` is
  `isLandscapeWindow` (`:697`). In portrait the search bar always shows on the Journal and there is no 🔍 toggle
  (`JournalTab.kt:655-668` builds the 🔍 row only when `isLandscapeJournal()`). **So the amendment's portrait step has
  nothing to fix:** in portrait no reveal ever resizes the map.
- **How the reveal is laid out today.** The pill is an `AnimatedVisibility` in the scaffold's content column, above
  the Journal's own content (`AvailabilityCompactScaffold.kt:1080-1146`). Its enter is
  `slideInVertically + expandVertically` and its exit is `slideOutVertically + shrinkVertically`, on
  `MotionTokens.panelMotionSpec` (RECORD -1244). The Journal content below it is in the `weight(1f)` box. So while the
  pill expands, everything under it gets shorter frame by frame. That includes the Journal's 48 dp switch line, which
  carries Records' chips in landscape (RECORD -1243), and the page under it. I did not check "revert 10 on
  topup-items".
- **Which Journal screens have a map under the reveal.** Every map in the app is drawn through `mapSlot(`, called
  from `CentrePinLocationPicker.kt:207`, `CartographyEntryReportScreen.kt:548` and `AvailabilityCompactMapUi.kt:970`
  (Maps).
  - **Records → Offline maps** (`RecordsTab.kt:387`, `OfflineMapsPanel`). In landscape its map is
    `weight(1f).fillMaxHeight()` (`AvailabilityOfflineMapsUi.kt:454`), so it follows the height it is given. This is
    the owner's screen, and the only one affected.
  - **Finds → pick a location** (`JournalTab.kt:516`) and **an Entries entry's report map** (`CartographyScreen.kt:459`).
    Both show only while an entry is open. The scaffold then holds the header hidden (`journalSearchHeld`,
    `AvailabilityCompactScaffold.kt:577-595`), and the 🔍 icon is not drawn (`JournalTab.kt:660`), so neither can be
    revealed over.
  - **No other Journal screen draws a map.** The Records map and Cartography named in the amendment have no map of
    their own outside an open entry, as far as this grep shows.
- **File ownership.** `OfflineMapsPanel` is in `AvailabilityOfflineMapsUi.kt`, which the other Hazel is changing, so
  a fix has to sit outside it, in the scaffold or in `RecordsTab`.

### Why it stops

The fix that holds the map exactly still is in one place, the scaffold. While Offline maps is showing, the pill's slot
keeps the pill's full height (its landscape height, the Maps strip's height) whether the pill shows or not, and the
pill slides into that slot with no expand or shrink. The map then never moves. With the bar hidden, it looks like this:

```
 ┌───────────────────────────────────────────┬──────┐
 │  (empty band, page background, pill-high) │ Tools│
 ├───────────────────────────────────────────┤      │
 │ Entries|Records  [All][Finds]..[Offline] 🔍│ ...  │
 ├───────────────────────────────────────────┤      │
 │ list / controls      │  map               │ List │
 └───────────────────────────────────────────┴──────┘
```

Only Offline maps has a map, and "screens without a map behave as today". So the band would be there on Offline maps
and absent on the other chips. **Tapping the Offline maps chip would drop the whole switch line, chips and 🔍
included, by the band's height, and tapping any other chip would jump it back up**, with an empty strip at the top of
the screen in between. A user could read that strip and that jump as a layout glitch. I haven't measured the band's
height. It is the pill's landscape height, around the Maps compass strip's height.

The other layout the amendment allows, where the chips sit higher and the gap is between the chip line and the map,
keeps the chip line still across chips. But then the gap has to shrink by exactly what the pill grows, frame by frame,
across the scaffold and the Records page. Done through a size callback, it would lag a frame behind the slide, which
brings back the stutter the owner reported. I don't know a same-frame way to do it without moving the pill into the
Journal's own layout.

### Options for the owner (not chosen)

1. **Band on Offline maps only, as above.** The map holds still. The switch line jumps when you move between Offline
   maps and the other chips, with an empty strip at the top while the bar is hidden.
2. **Band on every Journal screen in landscape.** Nothing jumps between chips, and the map holds still. But every
   Journal screen gives up that strip of height while the bar is hidden, which is against "screens without a map
   behave as today" and against J5's reason for hiding the bar (height is scarce in landscape).
3. **On Offline maps, the bar slides in over the top of the Journal content without pushing it**, covering the
   switch line while it is open. The map holds still and nothing is reserved. The owner declined covering
   ("nothing gets covered"), so this is listed only for completeness.
