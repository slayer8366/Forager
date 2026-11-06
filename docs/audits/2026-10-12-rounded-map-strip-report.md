# Rounded, tucked map strip: completion report (dispatch 2026-10-12-06)

RECORD -932 (intent), -934, -935, -936, -937 (the owner's answers). Branch `rounded-map-strip`, rebased onto main `a576c051`
(PR #220) with no conflicts: the two sides touched no file in common, and the rebased patch is byte-identical to the
pre-rebase one (`git diff 921aa9c7 9199b5b1` against `git diff a576c051 30e83e2b`). Not merged.

## What landed

- **Search pill** over the map (`AvailabilitySearchUi.kt`, `pillCorners`): rounded, 2 dp shadow, no divider (a 1 dp spacer keeps
  the bar's 45 dp). Full width (RECORD -934's zero side margin; the planner read the pill and strip as full width too, for
  the owner to confirm from the screenshots). Other tabs unchanged.
- **Tucked card** (`RoundedMapCard.kt`): the strip and the navigation card draw their fill 16 dp up behind the pill, less the
  pill's shape, so nothing is laid twice at 0.8; their own bounds start at the pill's bottom. No pointer input.
- **Navigation card** (`NavigationHud.kt`): three columns (figure + kind over turn + needle; "Alt" over arrow + heading;
  grid reference over a short evening line), the X above the three-dot button in a column at the right edge, then lines
  under the columns only when they say something: the grid reference (when it has left its column), the evening lines,
  the route message with Try again, the status line (everything today's carried, RECORD -935).
- **Fit** (`RoundedMapCardFit.kt`), the owner's order (RECORD -934, -936): kind, turn to degrees, "Alt", "≤ 16 ft", grid
  reference to its own line. Nothing further; an overflow is logged.
- **Turn words** (`NavigationWords.kt`): "sharp right 169°"; `turnShortForm` reads the last word (the " · " parse would have
  silently returned null).
- **Landscape**: the pill and strip meet square with no join line; navigating, the bar now takes the room up to the card.
- **Bottom bar** (Maps tab only) rounds its top corners by a clip, so the corner gaps reach the map (RECORD -937).
- **Map credit line** rounds its top-right corner only (the owner's correction to -937).

## CI

- Final full run `38225602288` at `30e83e2b`: 597 suites, 4,628 tests, **12 failures**, 24 skipped. Main `a576c051`
  (run `38223860061`): 587 suites, 4,573 tests, 0 failures, 24 skipped.
- The 12 failures are all landscape, all the display's height or width against earlier owner rulings, and are left failing
  for the owner (not edited, not ignored): `HudLandscapeT11FixesAt780Test` ×5, `HudLandscapeT11FixesAt823Test` ×3,
  `HudLandscapeT11At780Test` ×2 (positive control: the evening line left by -770), `LandscapeLargeFontTest` ×2. See
  "Stops" below.

## Revert checks

Each ran on its own `revert-check/*` branch built from committed state, never touching the working tree; each build log was
checked for compile errors before its results were read. r1 to r12 ran at `9199b5b1` (before the rebase), r1b, r11b, r13
and r14 at `30e83e2b`.

| Check | Edit | Run | Result |
|---|---|---|---|
| r1 | turn words back to " · " | 38223951325 | did not run (SDK download failed); rerun as r1b |
| r1b | same | 38225621991 | bit: NavigationWordsTest 5, e.g. `expected slight left 10° but was slight left · 10°` |
| r2 | `turnShortForm` back to the " · " parse | 38223978202 | bit: NavigationHudFitTest 2, `expected 169° but was null` |
| r3 | middle column rows swapped back | 38224003291 | bit: arrows test 4/4, `the two arrows share a line (centres 39.0 and 16.0)` |
| r4 | card fill without the pill cut out | 38224029393 | bit in dark only: `the pill's lower band ... not laid twice`; light passed (weak: see below) |
| r5 | pill square | 38224053985 | bit: `long-press at (2.0, 2.0) in the pill's rounded corner reached the map` |
| r6 | three dots above the X | 38224077900 | bit: placement 360/384 and touch test |
| r7 | no grid-reference line step | 38224099469 | bit: fit test 8/12 (grid reference ellipsised) and RoundedMapCardFitTest |
| r8 | no "≤ 16 ft" step | 38224125346 | bit: `then within 16 ft takes its short form` |
| r9 | bottom bar square | 38224151246 | bit: `long-press at (2.0, 702.0) in the bar's rounded corner` |
| r10 | credit line square | 38224174294 | bit: `top-right corner gap shows the map` |
| r11 | landscape bar back to its cap | 38224195024 | **did not bite** at 780/823: the display already filled the room there |
| r11b | same, with a 915 dp class added | 38225646748 | bit at 915 only: `bar's right edge is the card's left edge, expected 475 but was 384` |
| r12 | evening line always in the right column | 38224217166 | bit: fit test 6 (sundown cut), grow test, fit unit test |
| r13 | heading drawn with no fix | 38225670759 | bit: `no heading line` (MapIconStackTest) |
| r14 | no empty status node | 38225693221 | bit: ArrivalHoldTest `navigation-hud-status` missing |

r4 is weak: in light theme a doubled 0.8 layer is within the shadowed-colour tolerance, so only the dark case tells.

## Measured widths (run 38215191193)

Recorded in the hand-back; the fit test (`NavigationCardFitAcrossWidthsTest`) now covers 320/360/384/412 dp at fonts
1.0/1.3/2.0 and passes: nothing in the columns, the grid line or the evening lines is clipped, the arrows share a line, and
the grid reference is whole. The "within 16 ft" case can't be reached on the real screen: arrival (15 m or twice the
accuracy) always covers "within" (distance ≤ accuracy), so "≤ 16 ft" is exercised only by the pure fit test.

## Stops (owner's call)

1. **Landscape height.** With the X and the three dots stacked (76 dp) and the lines under the columns, the landscape card is
   taller: Try again's 48 dp row adds height (94 to 118 dp at 780), against -766's "no taller for Try again"; at font 2.0 it
   reaches 154 dp, past the central third (128 dp, Landscape B2 S10), over Reset north and the walker's dot (T11 item 2,
   -770); at font 1.0 with the evening line it ends 14 dp under the L's lowest top.
2. **Landscape grid reference at font 2.0, 780 dp**: the card is 292 dp wide (the room beside the capped bar, -694); the grid
   line has 236 dp; MGRS needs 239.5 dp, so it ends in "…" (against -699).
3. Portrait "No route" short form beside Try again at narrow widths (RECORD -768's form, now used in portrait too).

## Device items

The pill's top margin and fullscreen slide against the real status bar; the bottom bar's rounded corners against the real
navigation-bar inset (Robolectric reports zero insets); shadow and 80% fill over real tiles, day and night; the mockup match
at the phone's own font; a small lighter rectangle seen at the middle column's lower right in the Robolectric screenshots
(unexplained, not investigated); the credit line over the real map (MapLibre is not drawn under Robolectric, so its test
composes the caption alone).

Screenshots: `~/Zynergy/device-evidence/2026-10-12-nav-strip-mockup/robolectric-30e83e2b/` (outside the repository).

---

# Continued: dispatch 2026-10-12-08 (RECORD -945 to -952)

Base `origin/rounded-map-strip` at `4b8e5c6c`, confirmed before starting. Commits `b58f6fd5`, `1f483db5`, `9718c3c5`, `c3894087`,
`33cdb917`, all pushed. Not merged; the PR stays a draft. No Gradle on the laptop: CI only.

## What landed

- **The strip stays while navigating (-948, -950)**, in both orientations. Its line drops the north arrow and heading, leaving
  height · grid reference · ⋮ (`StripNavigatingReadout` in `AvailabilityMapControlsUi.kt`). While navigating it does not read the
  heading, so the navigation info is still the only place reading it at sensor rate. The strip's own sundown and Back by lines are not
  shown while navigating.
- **Portrait: one card.** The navigation info is drawn inside the strip's card, under a 1 dp line (`CompactMapPortraitNavigationInfo`,
  through the strip's `navigationInfo` slot): one fill, one shadow, no second tuck. It grows down from under the line on the
  navigation view's timing; under reduced motion it only fades. The chips are now composed before the strip, so while navigating the
  card covers them, as the old display did.
- **Landscape: the card hangs under the strip.** RECORD -951 restores main's sizes (-729, -732: `landscapeStripFit`, with the bar taking
  the rest of the width), replacing -940's short-side width.
  - The bar meets the strip whether navigating or not, so nothing resizes when navigation starts.
  - The card sits at the strip's measured bottom, at its exact width, tucked under it, and slides down from under it.
  - The tuck's cut-out now follows the real lower corners of the piece above (`mapCardSurface(upperCorners = …)`).
  - The card has its own keep-out id, `MapKeepOutIds.NAVIGATION_CARD`.
- **The navigation info (-945, -950)** (`NavigationInfo` in `NavigationHud.kt`):
  - Left part, centred: the distance in headlineSmall bold (24 sp, twice the 12 sp text); "ahead 1° ▲ ▲ 134° SE" on one line, with the
    two arrows 4 dp apart; then the kind word on its own line.
  - A divider at the taller part's full height. It stays when there is no evening stack (-951).
  - Right part: the evening stack, centred, with the X at the right edge.
  - No Alt, grid reference or ⋮ in the card.
  - The status line no longer carries the kind word. It leaves out the position statuses (no fix, approximate, last seen), which the
    strip shows once (-951 (a), `statusIsPositionStatus`).
- **Fit** (`navigationInfoFit`): the turn words shrink to degrees, then the distance takes "≤ 16 ft". Nothing else is trimmed. On
  overflow the left part stays whole and the evening pieces wrap; nothing is cut.
- **The ⋮ sits above the X in portrait** (-951 item 5): it is on the strip's line, and the X is below the line.
- **Lighter pill (-945, -951):** `MapPillColorLight` = `surfaceContainerLowest` (#FFFFFF) and `MapPillColorDark` =
  `surfaceContainerHighest` (#373737), both at 0.8. They apply to the Maps-tab pill and to the landscape strip. The card and the
  portrait strip keep the chrome token. Contrast of the pill's text and icon:

  | | Day | Night |
  |---|---|---|
  | Opaque | 13.1:1 | 11.9:1 |
  | Worst case at 0.8 | 8.2:1 | 6.4:1 |

  The spinner (`primary`) is 3.6:1 at its worst case.
- **Pill floor (-951 item 8):** `MAP_PILL_MIN_HEIGHT` is 40 dp, enforced in the pill and in the scaffold's `mapPillHeight`. At font
  0.85 it measures 40 dp; without the floor it was 35.3 dp. The comment that said 41 dp now says 40.
- **The strip's own evening lines (-951 item 4):** one whole line where it fits; otherwise the pieces stack at their " · " joins. Never
  "…".
- **RECORD -952:**
  - The landscape rail rounds its two corners facing the map, clipped like the bottom bar's.
  - The credit strip is a pill, with all four corners rounded.
- **Removed as dead by this change:** `NAVIGATION_HUD_EVENING_LINE_TAG`, `NAVIGATION_HUD_ELEVATION_TAG`,
  `NAVIGATION_HUD_COORDINATES_TAG`, the three-column fit, `mapCardWidth` and `rowsBottomInRootPx`.
- **Left dead for the tidiness release (-951 item 10):** `besideLandscapeSearchBar`, `railSideRoomBesideSearchBar`,
  `LANDSCAPE_HUD_MAX_WIDTH` and `firstRowFit`. `landscapeStripFit` is in use again.

## The chip row and the lighter patch

- **Chip row:** `LayoutFixesChipRowLandscapeTest` passes unchanged once main's widths are back. -940's 384 dp strip was the cause.
- **Lighter patch (-951 item 11):** the probe was re-run on the new card (412 dp portrait, run 38257949553), then removed. The same
  lighter island is drawn: x 587-649 px, y 351-372 px. Its red is 245 or more against 166-169 in the shadow's darker band under the 0.8
  fill. It is the shadow renderer's output, not an element, and stays a device item.

## Tests changed: old and new assertion, and the ruling

| Test | Old | New | Ruling |
|---|---|---|---|
| MapIconStackTest: strip absent / leaving | strip count 0 | strip 1, no strip heading, "95° E" once | -950 |
| MapIconStackTest: no fix | NO_FIX on the card's status | on the strip; card status "" | -951 (a) |
| MapIconStackTest: elevation, grid, toggle, format (4) | card's elevation/coordinates tags | strip's tags | -950 |
| ApproximatePositionTest (5 asserts) | position status on the card; strip absent | strip note once; card status "" | -951 (a) |
| QuickSettingsTest, navigating | strip hidden; ⋮ in the HUD under the X | strip shown; ⋮ on it, above the X; strip's Back by absent | -947, -951 |
| LayoutTest | strip `assertDoesNotExist` ×2 | `assertIsDisplayed` | -950 |
| NoFacingLabelTest, navigating | `[Alt]` in the card | none in the card; `[Alt]` on the strip | -950 |
| SundownLineTest, Return | "and the strip with it goes" | strip shown, its own line absent | -950 |
| NavigationWordsTest, portrait (3) | Alt, elevation and grid on the card; kind beside the figure or leading the status | strip's tags; "by trail" under the turn line | -945, -950 |
| NavigationWordsLandscapeTest | `hud.bottom <= thirdTop` | above the walker's dot by its radius; needle inside the card and uncovered | -766 via -951 |
| LandscapeLargeFontTest :185 (via :219, :261) | `hud.bottom <= thirdTop` | same rule as the row above; grid reference whole, on the strip | -766 via -951, -950 |
| B2Test S4, navigating ×2 | strip hidden; HUD at the map top; ≤ 360 dp wide | strip at the map top; card's top = strip's bottom; same edges | -946, -950, -951 |
| B2Test S4, strip ×2, and LandscapeBarStripJoinTest | -940's short-side widths | main's content width, restored | -951 |
| LandscapeBarStripJoinTest, join ×2 | main's 1 dp join line | no line | -934 |
| LandscapeCardMeetsBarTest ×2 | bar meets card at the top; short-side width | strip and bar identical navigating and not; card's edges = strip's, both rotations | -950, -951 |
| T11FixesTest, item 1 (font 2.0) | card's grid reference whole | strip's grid reference whole | -950 |
| T11FixesTest :375, :418 | `NAVIGATION_HUD_EVENING_LINE_TAG`, never drawn | `NAVIGATION_HUD_SUNDOWN_LINE_TAG` | -941 |
| T11 harness, state 01 | NO_FIX on the card's status | on the strip | -951 (a) |
| RoundedMapCardTouchTest | X above ⋮; card's grid tag | ⋮ above the line, X below; strip's grid tag | -951 item 5, -950 |
| ButtonPlacementTest | ⋮ under the X | no ⋮ in the card; X at its top right; real touches stop navigating | -947, -950 |
| QuickSettingsWidthTest | text clipped with the ⋮ vs without | figure, kind and turn whole; status whole or one line ending in "…" | -947 |
| KindInStatusTest | "By trail" leading the status line | "by trail" on its own line; status "" | -945 |
| ArrowsSideBySideTest | 1 divider, 13 dp between the arrows | 0 dividers, 4 dp | -950 |
| FitAcrossWidthsTest | grid reference drawn; elevation and Alt whole; a landscape pass | those removed; card height printed | -950, -951 |
| RoundedMapCardFitTest | three-column fit (11 tests) | two-part fit (8 tests) | -945, -950 |
| HudSundownLineGrowTest | a two-piece line | five pieces plus Back by (two pieces no longer outgrow the left part) | -945 |
| MapPopUpMotionTest | the strip leaves; the display slides in | strip stays; card grows (sampled 6 frames in); leaving X takes no touch | -948 |
| MapChromeColourTest: pill; landscape strip | chrome token at 0.8 | pill role at 0.8 | -945, -946, -951 |
| MapChromeColourPixelsTest | pill and its lower band = chrome token | pill role; pill at least 2% lighter than the strip | -945 |
| MapCreditCaptionTouchTest | top-right rounded, the other three square | all four corner gaps show the map; long-presses in all four | -952 |

**New tests:**
- `MapTopCardScreenTest`, in portrait and in landscape at 90 and 270:
  - nothing on screen repeats;
  - five real touches each across the X, the grid toggle, the ⋮ and Try again;
  - long-presses at the card's corner gaps, just below it and beside it;
  - portrait only: one card, with no shadow seam at the line;
  - landscape only: long-presses in the rail's corner gaps.
- `NavigationInfoLayoutTest` and `StripEveningLineStackTest`.
- New assertions added to `MapChromeColourTokenTest` and `SearchBarStripSeamTest`.

No test was ignored, skipped or loosened, and none was deleted without a ruling.

## Revert checks

Twenty, each run as below:
- On its own `revert-check/rms08-*` branch, built from committed state with `git commit-tree`, so the working tree was never touched.
- The build log was checked for compile errors before reading any results: every one had 0.
- Results were read from that run's own artifact.
- Afterwards the forward change was confirmed still in HEAD, and the branch was deleted.

r01-r16 ran at `c3894087`; r17-r20 at `33cdb917`.

| Check | Edit | Run | Failure it produced |
|---|---|---|---|
| r01 | the strip's line keeps its heading | 38259677067 | "the heading reads once expected 1 but was 2" |
| r02 | the strip is removed while navigating | 38259693182 | 23 failures, e.g. no `compass-strip-coordinates` |
| r03 | the info drawn as a second tucked card | 38259710140 | "no shadow cast at the line … 0.9109 against 0.9383" |
| r04 | the pill back to the chrome token | 38259727177 | pill colour wrong (2 classes) |
| r05 | the landscape strip back to the chrome token | 38259742956 | strip colour wrong, light and dark |
| r06 | no pill floor | 38259758973 | "the pill is at least 40 dp: 35.33" |
| r07 | the card shows position statuses | 38259774100 | 7 failures: "leaves it out … but was Approximate, finding GPS…" |
| r08 | no kind line | 38259791894 | 5 failures: no `navigation-hud-distance-kind` |
| r09 | the divider not full height | 38259808211 | 3 failures: divider 0 dp tall |
| r10 | the distance back to titleMedium | 38259824691 | "16.0 sp against 12.0 sp" |
| r11 | left part aligned to the start | 38259841550 | "figure 42.7 and turn line 76.7 share a centre" |
| r12 | arrows 16 dp apart | 38259855933 | "expected 4.0 but was 16.0" |
| r13 | no ⋮ on the strip while navigating | 38259871465 | "one three-dot button expected 1 but was 0" |
| r14 | the card 360 dp wide | 38259888908 | 4 failures: card edges are not the strip's |
| r15 | the bar stops meeting the strip while navigating | 38259903020 | "and so does the search bar … 411.3 …" |
| r16 | the strip's line never stacks | 38259919082 | "three pieces … but was []" |
| r17 | the info only fades in | 38261935689 | "the card … is shorter than it settles" |
| r18 | the rail's corners square | 38261877578 | rail-corner long-press, both rotations |
| r19 | the credit strip back to top-right only | 38261892496 | "bottom-right corner gap shows the map" |
| r20 | a leaving stack's width not held | 38261907942 | "caught shrinking" (frames reach 823 dp) |

Two limits on what these checks cover:
- The credit strip's long-press test cannot fail on the corner shape, because the caption takes no touches anywhere. Only its pixel test
  catches a wrong shape (r19).
- In landscape the rail's top corner lies within reach of the strip's ⋮, so the rail test samples the bottom corner only.

## CI

- Full run `38261622357` at `33cdb917`: 602 suites, 4,655 tests, **8 failures**, 24 skipped.
- Main `a576c051` (`38223860061`): 587 suites, 4,573 tests, 0 failures, 24 skipped.

## Stops: landscape height, for the owner (numbers only, nothing trimmed)

The 8 failures are in `HudLandscapeT11FixesAt780Test` and `…At823Test`, four each, against -766 and -770. In landscape the card now
starts under the 36 dp strip.

1. **-766, "no taller for Try again":** the card is 76 dp on the route and 124 dp with "Unable to calculate route" plus Try again's
   48 dp row. At 4b8e5c6c it was 90 → 116.
2. **-770 at font 1.0** (route withheld, evening lines): the card ends at 160 dp. The L's lowest top is 120 dp at 780 and 144 dp at 823,
   so the L cannot slide below the card.
3. **-770 at font 2.0** (route withheld): the card ends at 203 dp even without the evening stack. Reset north's top is at 168 dp (780)
   and 192 dp (823).
4. **The walker's dot, at the tallest state** (font 2.0): the card ends at 315 dp, against limits of 213 dp (780) and 228 dp (823). The
   evening pieces wrap at overflow.

Other measurements:
- **Landscape, 823 dp, no target:** at font 1.0 the card is 36-112 dp, ending 16 dp above the central third. At font 2.0 it is 38-156 dp,
  28 dp into the central third and 72 dp above the walker's dot, which -766 as -951 applies it allows.
- **Portrait navigation info** (`NavigationCardFitAcrossWidthsTest`, at 320 to 412 dp):

  | State | Font 1.0 | Font 1.3 | Font 2.0 |
  |---|---|---|---|
  | On the route | 76 dp | 84 dp | 118 dp |
  | With Try again | 124 dp | 132 dp | 166 dp |
  | Owner's evening example | 76 dp | 90 dp (132 dp at 320) | 198-230 dp; **422 dp at 320 dp** |

  At 320 dp and font 2.0 the pieces wrap heavily. No text is cut in any of the 132 states.

## Device items

1. **Portrait, navigating:** the card extends down as one card. Check the lighter pill and the tuck by day and at night; the bigger
   distance; the arrows side by side; "by trail"; text fitting at the phone's own font; how easy the ⋮ and the X are to hit; and
   long-presses at the corner gaps and just below the card.
2. **Landscape, navigating:** the strip neither moves nor resizes, and the card slides down at its exact width. Whether Reset north and
   the walker's dot stay clear at the phone's own font: CI says not at font 2.0 with Try again.
3. **Ending navigation:** the heading returns to the strip's line.
4. **Not navigating:** the strip's sundown line stacks only when it does not fit.
5. **A route that cannot be found:** Try again appears, then goes once a route is found.
6. **Against the real system bars and map** (Robolectric reports zero insets):
   - the pill's 40 dp;
   - the bottom bar's and the rail's rounded corners;
   - the credit pill over the real map;
   - the lighter patch;
   - the landscape pill's width beside the strip, with a real cut-out inset.

Screenshots: `~/Zynergy/device-evidence/2026-10-12-nav-strip-mockup/robolectric-33cdb917/` (outside the repository).

---

# Continued: RECORD -953 (landscape card on the bottom edge) and -954 (the "i" beside the credit strip)

Commits `0742bc31`, `ef774120`, `04144bc0`, `0f35f6c0`, `92791482`, all pushed. Not merged; still a draft.

## What landed

- **-953**, the owner: "have the card peak from the botton of the screen instead from the top"; "Landscape only. Portrait is
  dialed in"; "It stays exactly how it is now. Just flipped upside down. No rearranging."
  - In landscape the navigation card is anchored to the screen's bottom edge, in the strip's column, at the strip's exact width.
  - Its top two corners are rounded (`MapCardCorners.TOP`). Its bottom is square on the edge, with no tuck; the 0.8 fill and shadow
    stay.
  - It slides up from the bottom (`navigationChromeFromBottomTransitions`), or only fades under reduced motion.
  - Its content and order are unchanged, and the strip does not move. Portrait is unchanged.
- **Nothing else moves.** Two things that existed only for the old top position are no longer wired:
  - The landscape L is no longer told the card's edge, so it does not slide (T11 P4's slide below the display). There is no room
    below a card on the bottom edge, and sliding the L would rearrange it.
  - RECORD -770's evening-line limit is not passed. It was worked out from the L sitting below the display.
- **-954**, the owner: "Have the map i next to the open street maps logo"; "Just landscape".
  - The "i" is MapLibre's own attribution control. It is placed through `UiSettings.setAttributionGravity` (`SightingsMap.kt:785`)
    and `setAttributionMargins` (`:1288`), not as a composable.
  - So it moves through those same settings: in a short landscape window, gravity bottom-start, with a start margin of the credit
    strip's measured width plus 4 dp and a bottom margin equal to the strip's (`attributionBesideCreditMarginsPx`).
  - The flag is `MapRenderMode.attributionBesideCredit`, set by the Maps tab in landscape only. The "i" opens what it always opened.

## Collisions, reported and left in place ("No rearranging")

**Landscape, navigating, with the L on the card's side (the rail side), both rotations:**

| | 780 dp | 823 dp |
|---|---|---|
| Card at font 1.0 | 284-360 dp | 308-384 dp |
| L's Record / Return pill | 304-352 dp | 312-360 dp |
| L's bar | bottom at 296 dp: 12 dp under the card | clear |

- The card covers the Record / Return pill at both widths, and part of the bar at 780 dp.
- Real touches on Record go to the card at ROTATION_90: `item 3 … navigating every bar and pill button takes real touches` fails at
  780 and 823.
- **Not understood:** the same test passes at ROTATION_270 with a similar overlap. I have not explained this, so I am reporting it
  rather than relying on it.

**The credit strip and the "i":**
- The credit strip is about 306 dp wide (default basemap, 823 × 384 window). The "i" sits beside it at x 310-334 dp, sharing its
  bottom edge.
- At ROTATION_90 the card (x 385-743) is clear of both.
- At ROTATION_270 the card spans x 80-438 dp along the bottom, so it covers the credit strip's right part and the "i" while
  navigating.
- The rail already sits over the credit strip's first 80 dp there. That predates -953; I inferred it from the scaffold, which
  insets the "i" from the rail but not the caption, and did not measure it.

## Landscape heights (-766, -770): re-measured, nothing trimmed

The 10 failing tests in `HudLandscapeT11FixesAt780Test` and `…At823Test`, five each:

1. **-766, "no taller for Try again":** the card is 76 dp on the route and 124 dp with Try again's row. It grows upward from the
   bottom edge.
2. **-770 at font 1.0** (route withheld, evening lines): the card spans 236-360 dp at 780 and 260-384 dp at 823. It covers the L's
   bar (780: bar 36-276, overlap 40 dp; 823: bar 44-284, overlap 24 dp).
3. **-770 at font 2.0:** the card's top is 82 dp (780) and 106 dp (823), so it covers Reset north (780: 85-133; 823: 92-140).
4. **The walker's dot** (font 2.0, Try again and evening lines): the card spans 82-360 dp (780) and 106-384 dp (823), so it covers
   the dot (y 225 and 240).
5. **The L's pill:** covered on the rail side, as in the table above.

**Where it is clear:**
- With no target, the card spans 308-384 dp at font 1.0 and 266-384 dp at font 2.0 (823 dp). It is below the walker's dot (240 ± 12).
- The `LandscapeLargeFontTest` and `NavigationWordsLandscapeTest` walker's-dot checks pass.

## Tests changed by -953: old and new assertion

| Test | Old | New |
|---|---|---|
| B2Test S4, navigating ×2 | card's top = strip's bottom | card's bottom = map's bottom; same edges as the strip |
| LandscapeCardMeetsBarTest ×2 | card hangs from the strip's bottom | card on the window's bottom edge; map shows between; long-presses beside and above it |
| MapTopCardScreenTest landscape, map touches | card's bottom corners; points just below it | card's top corner gaps; points just above and beside it |
| T11 Reset north check | `reset.top >= hud.bottom`; `hud.bottom <= b.top + 48.5.dp` | Reset north does not overlap the display; the display covers none of the bar |
| T11 -770 at font 1.0 | the bar's top on the display's bottom | the display covers none of the bar |
| T11 "at its tallest", LargeFont, WordsLandscape | `hud.bottom <= walkerY - r` | no overlap with the dot's circle |
| T11 item 3, the L on the display's side ×2 | L slides below the display; pill beside "+" | L and pill stay where the user put them |
| T11 item 3, slide ends | positive control "the slide moves the bar" | the L does not move |
| T11 item 3, mid-slide touches | touches held mid-slide | "starting navigation moves the L at no frame" |
| T11 item 3, long-press beside the L | third point above the pill (now on the bar) | beside the pill |

**New tests:**
- In both landscape classes:
  - the card slides up from the bottom;
  - a pixel check that its top corners are rounded and its bottom square (a long-press cannot tell them apart; revert r22 at first
    did not bite);
  - the map is asked to put the "i" beside the credit strip in landscape only.
- `AttributionBesideCreditTest`: the margins, and the "i"'s square laid against the real credit strip.

## Revert checks

Five, built from committed state, each build log showing 0 compile errors, branches deleted afterwards.

| Check | Edit | Run | Result |
|---|---|---|---|
| r21 | card back at the top | 38297602847 | bit: 8 failures, e.g. "the card sits on the bottom edge" |
| r22 | card corners square | 38297614221 | did not bite: the long-press touches only |
| r22 (rerun after the pixel test) | same | 38300253429 | bit: "the top-left corner gap shows the map", both rotations |
| r23 | card slides down from the top | 38297628896 | bit: "mid-start the card … is below where it settles", both rotations |
| r24 | "i" flag off | 38297641550 | bit: "beside the credit strip … expected true but was false" |
| r25 | "i" start margin ignores the strip | 38297652898 | bit: "the i (4.0 dp) starts past the credit's end (306.33 dp)" |

## CI

Run `38299141514` at `92791482`: 603 suites, 4,664 tests, **10 failures** (the collisions above), 24 skipped. Main has 0 failures.

## Device items (added)

- **The "i" itself.** A real MapView cannot be built under Robolectric, so the "i" cannot be touched or drawn here. Check on the phone
  that it sits beside the credit strip and opens its attribution content.
- **The real bottom gesture bar or navigation bar** under the landscape card (Robolectric reports zero insets). The card is anchored
  to the controls padding's bottom.
- **The card over the L's pill on the rail side**, and over the credit strip and the "i" at ROTATION_270.

Screenshots: `~/Zynergy/device-evidence/2026-10-12-nav-strip-mockup/robolectric-92791482/` (outside the repository).

---

# RECORD -955, -956 and -958 (same dispatch, continued)

## What landed

| Commit | What |
|---|---|
| `240d2a22` | -955: icon bar fixed on the screen's left while landscape and navigating, never saved (`AvailabilityMapIconCluster.kt`, `fixedLeft`); C: the evening lines leave the landscape card where its top would cover Reset north or the walker's dot; D: Try again may make the card taller |
| `1622a11d` | -955 C, second build: the decision uses a height built from parts the evening stack does not change, plus a 4 dp margin once shown. The first build read the card's measured height and never settled at font 2.0 (CI `38304300338`, the T11 harness hung) |
| `a83e7704` | -956: rail on the right and search bar on the left at both landscape rotations (`PortEdge.kt` `mainWindowRailEdgeFor` / `mainWindowSearchEdgeFor`, read by `AvailabilityScreen.kt`); map controls kept clear of the navigation bar on both sides; offline-maps controls on the right; `LandscapeRotationsAlikeTest` |
| `c4b495fc` | -958: `cameraArrangement` returns `LandscapePortRight` in every landscape window; camera tests changed; a 90-vs-270 bounds equality test |
| `b14bed51`, `3e21948e` | Tests restated for -956 and -955 (below). `LandscapeRotationsAlikeTest` fixed: it set content twice per test and failed before asserting anything (CI `38307164887`) |

## The -955 C loop: confirmed fixed

Run `38307164887` at `a83e7704` finished. `HudLandscapeT11At780Test` and `HudLandscapeT11At823Test` each ran 12 cases, including all four font 2.0 cases, with 0 failures. At `240d2a22` the same harness had never finished. This was the first fix attempt, so the second-attempt stop does not apply.

## -958: placement only, capture untouched

The change is one branch in `CameraArrangement.kt` (`cameraArrangement`). The following were read and not changed:
- The photo's orientation comes from the sensor: `CameraXCaptureSession.kt` sets `targetRotation` from `deviceRotation`.
- The glyphs turn by sensor minus display (`rotateWithDevice`).
- `IntendedOrientation` does not read the arrangement.

`CameraBands.kt` pads each band by the cut-out and navigation-bar inset on its own edge, so the cut-out is still cleared on whichever side the camera falls. Nothing shared code between placement and capture, so there was nothing to stop and report.

## Collisions under -956 plus -955 (CI `38309436037`, measured by T11 fixes item 3)

The numbers are identical at ROTATION_90 and ROTATION_270, in dp.

| Window | Card | Bar (fixed left) | Pill (record, return) | Overlap |
|---|---|---|---|---|
| 823 x 384 | x 385.3 to 743, y 308 to 384 | x 8 to 56, y 64 to 304 | x 8 to 104, y 312 to 360 | none |
| 780 x 360 | x 342.3 to 700, y 284 to 360 | x 8 to 56, y 56 to 296 | x 8 to 104, y 304 to 352 | none |

The card no longer covers the L's pill or the bar.

Open item B (the card over the credit strip and the "i" at the rotation where the card was on the left) no longer arises: the card is on the right at both rotations. Whether the bottom-left credit strip reaches the card's left edge at 780 dp is **not measured**, because Robolectric cannot draw the real credit strip.

At font 2.0, with Try again and the evening lines gone (-955 C), the card still covers the walker's dot. -955 accepts this overlap ("Overlap left at font 2.0 is accepted"):
- 823: card top 218, dot at y 240 with radius 12;
- 780: card top 194, dot at y 225.

## Tests changed: old and new assertion

Each changed test names its ruling in a comment beside the old assertion.

- **-958**
  - `CameraArrangementTest`: ROTATION_270 was `LandscapePortLeft`, now `LandscapePortRight`.
  - "the two landscapes never resolve to the same arrangement" (`assertNotEquals`) is reversed to "resolve to the same".
  - `CameraEdgesTest`: the edge at 270 was `Left`, now `Right`.
  - `InAppCameraDialogLandscapeTest`, the 270 shutter test: was shutter left, `frame.left + Spacing.lg`; now one shared assertion run at both rotations.
  - `InAppCameraDialogLandscapeTest`, the 270 strip test: was flush right; now flush left.
  - New test: `the camera's controls have the same bounds at ROTATION_90 and ROTATION_270`. It opens the dialog at 90, then afresh at 270 in one composition, and compares the shutter, band, count, strip and five chips.
- **-956** (CI `38307164887` listed the mirrored cases: 58 failures, of which 5 were the broken `LandscapeRotationsAlikeTest`, 6 were -955 and 2 were the card screen's touch points). These now assert ROTATION_90's layout:
  - `AvailabilityScreenLandscapeB2Test` S1, S2, S4 x2, S6 x3, S7, and `turnTo`'s rail check;
  - the B2 side-memory case. Its landscape default can no longer tell the two memories apart, so the landscape cluster is now dragged right and the portrait side is read back;
  - `AvailabilityScreenShortLandscapeTest` (rail side, long-press x);
  - `AvailabilityScreenBubbleClearanceTest`;
  - `CompactToolsDrawerTest` (LTR and RTL);
  - `LandscapeBarStripJoinTest` (`barOnLeft`);
  - `LandscapeCardMeetsBarTest`;
  - `LandscapeLClusterTest` L1 and L3, `LandscapeLClusterPixelsTest` L5;
  - `LandscapeOfflinePickerTest`;
  - `LandscapeSearchFixesTest` (four rotation branches);
  - `LayoutFixesShortLandscapeTest` TR2. T5 was the zero end inset at 270; it is now the rail's width, as at 90;
  - `MapTopCardScreenTest` (beside-card x, rail inner edge).
- **-956, passing but testing the wrong side.** These were found by reading the passing 270 cases (the planner's answer 6), not by CI:
  - `AvailabilityScreenShortLandscapeTest` 270 no-control-under-rail dragged away from the rail;
  - `LandscapeLRulingsTest` A2 snapped to the search side; D4's probe labels were swapped;
  - `LayoutFixesLandscapeRulingsTest` T1, and Q4 x2, where the cluster never met the legend;
  - `LayoutFixesChipRowHeldTest`: three 270 cases whose snap was a no-op.

  These were read and left alone, because each reads the side from the layout or does not depend on it: `NavigationHudFitTest`, `HudLandscapeT11MeasurementTest`, `LandscapeStripPortraitHeightTest`, `MapBarPressFeedbackTest`, `LandscapeLFullscreenCycleTest`, `AvailabilityScreenLandscapeB3DestinationsTest`, `AvailabilityScreenTabletAsPhoneTest`, `LandscapeLargeFontTest`, `AvailabilityScreenJournalShortWindowTest`, and the camera's rotate-with-device and capture-rotation tests.
- **-955**
  - `HudLandscapeT11FixesTest` item 3 x4 was "the pill stays under it", `recordBefore.left == rec.left`, while navigating. It now checks:
    - the L saved on the right moves to the screen's left while navigating, at its height;
    - nothing is stored;
    - the L is back after navigation ends.
  - Item 2 "at its tallest" x2 had the positive control "the evening lines and Try again are both up", followed by the dot assertion. It now asserts that the evening lines have left. The remaining overlap is printed, not asserted, as -955 accepts it.
  - The D change ("no taller for Try again") is in `240d2a22`.

## Revert checks

Each was built from committed state, each build log showed 0 compile errors, and each failure message is specific to its edit.

| Check | Edit | Run | Result |
|---|---|---|---|
| r26 | -955 `fixedLeftNow = false` | 38307645746 | **not citable**: filter `*IconBarFixedLeftTest` matched no class ("No tests found"); rerun as r29 |
| r27 | -956 rail back on `currentWindowPortEdge()` | 38307655849 | **not citable**: the test itself was broken ("has already set content") at that base; rerun as r30 |
| r28 | -958 ROTATION_270 back to `LandscapePortLeft` | 38307666370 | bit: 6 failures, e.g. "the same at both rotations; differ: in-app-camera-shutter 552–624 vs 16–88" |
| r29 | -955 `fixedLeftNow = false` | 38309447411 | bit: 4 failures (90 and 270), "landscape and navigating: the screen's left (DpRect(left=687 …))" |
| r30 | -956 rail back on `currentWindowPortEdge()` | 38309461986 | bit: 5 of 5, rail 743–823 vs 0–80, the drawer sheet on opposite sides, attribution end inset 80 vs 0 |

## CI

Run `38309436037` at `3e21948e`: 607 suites, 4,676 tests, **0 failures**, 24 skipped.

## Device items (added)

- **-956, ROTATION_270:** the system navigation bar (3-button or gesture) sits on the port edge, now the screen's left. Check that the search bar, the L and the card stay clear of it. The controls padding includes `navigationBars` on both sides, but Robolectric reports zero insets.
- **-956, ROTATION_270:** the rail's items sit over the camera cut-out's side. Check that none is cut.
- **-956:** fullscreen insets at both rotations.
- **-958:** the in-app camera at both rotations on a phone shows the shutter on the right and the strip on the left, with the cut-out kept clear on either side. Photos taken at ROTATION_90 and at ROTATION_270 are both saved upright.
- **B:** at 780 dp, whether the credit strip reaches the card.

Screenshots: `~/Zynergy/device-evidence/2026-10-12-nav-strip-mockup/robolectric-3e21948e/` (outside the repository).
