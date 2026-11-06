# Back by: build report (dispatch 2026-09-28-645, plan task T15)

Branch `back-by`. The dispatch is `prompts/preserved/2026-10-07-04.md` (RECORD intent -645). Its amendments and the
owner's answers are in RECORD -646 to -649. The hand-over is -687, the build order -689, and the width question -690
(the owner: "Measure first, then decide"). This report covers the second coder's work: the merges, the build, the
tests, the revert checks and the measurements. The first coder's code and tests are at `29074703` and had never been
compiled.

## What is on the branch

These pieces are built and tested. They were written by the first coder and finished here:
- `BackByWatch`, driven by the recording service's 15 s tick. It needs no exact-alarm permission.
- The alert, on its own channel, with "I'm back" and "+30 min" sent straight to the service.
- The three-dot quick menu at the strip's far right, and left of the X in the navigation display.
- The "Back by" line, shown in the last hour.
- Three buzzes for Back by and for the sundown alerts.

## Merges (all of `origin/main`; no unmerged branch)

1. **`cf9c6a2c`, main at `f6fc6c91`** (failure-fixes, motion Parts 1 and 2, data parts A and C). Five files
   conflicted. Every conflict kept both sides:
   - **`AppContainer.kt` imports:** `BackByWatch` sits beside `SettingsResetNotice` and `RecordingHalts`.
   - **`TrackRecordingService.kt` imports:** `NotificationManagerCompat` stays, for the back-by cancel.
     `ContextCompat` goes, because main moved its only use.
   - **`TrackRecordingService.kt` fix path:** main's guarded sundown call stays. The back-by fix call now goes through
     `RecordingWatches.backByOnFix` under `guardWatch`, which is what RECORD -674 asked for.
   - **`TrackRecordingViewModel.kt` init:** both collectors stay. `setBackBy`, `clearBackBy` and `copyBackBy` sit
     beside `onRecordingHalted`.
   - **`NavigationHud.kt`:** the three-dot button stays left of the X, and the X is now main's `BouncingIconButton`.
   - **`AvailabilityCompactMapUi.kt`:** the display keeps `backByLine` and `quickSettings`. Its position modifier is
     main's, which motion Part 2 moved onto the slide.
2. **`4f7631ac`, main at `a71b57ac`** (data part B). This merge had no text conflicts.
3. **`a2d64279`, main at `d223728b`** (motion Part 3 and the small fixes). One conflict, in `AndroidAlertDelivery.kt`:
   - **The vibration seam:** both sides changed it. Back by made it `(Context, Alert)` so the buzz pattern can follow
     the alert's kind. The small fixes kept `(Context, Boolean)` and added the ringer and Do Not Disturb seams after it.
   - **How both are kept:** the main constructor is now private. It takes the ringer, Do Not Disturb and the
     `(Context, Alert)` vibration. The small fixes' five-argument and three-argument test constructors keep their
     `(Context, Boolean)` seam, which is adapted inside, so their tests are unchanged.
   - **Why the parameter order differs:** the private constructor's parameters are ordered apart from the
     five-argument one, because the two would otherwise clash on the JVM once their function types are erased.

## Changes in this build

- **The guard.** `RecordingWatches.backByOnFix`, called under `guardWatch("The back-by watch", …)`.
- **The three-dot button and motion Part 1.** The planner named `BouncingIconButton`. The first run showed that it
  takes away the button's corners:
  - **What happened:** a real touch 3 dp in from the top-left corner did not open the menu.
  - **Why:** `BouncingIconButton` is Material's `IconButton`, which clips itself to a circle, and a clip is part of
    hit testing.
  - **What was built instead:** the button uses Part 1's own pieces, as `MapBarIconButton` does. The `clickable` sits
    on the unclipped 36 dp square, `pressBounce` is on the icon, and `PressHighlight` draws the press as a circle.
  - **Result:** the bounce and the round press are there, and the touch area is still the whole square.
- **The "Back by" line.** On the strip and in the display it now uses the sundown line's pop-up grow and `WordSwap`.
- **Settings writes.** The menu's sundown and off-track rows call the same `AvailabilityViewModel` functions as
  Settings. Those write through `DataStoreSundownPreferencesRepository` and
  `DataStoreOffTrackReminderPreferenceRepository`, which are built on `settingsDataStore`. Back by stores nothing.
- **A skipped buzz, recorded honestly.** Back by overrides silence like the sundown alerts, so the small fixes'
  `vibrationSkipReason` gives the same answers for it:

  | Phone state | What is recorded |
  |---|---|
  | On silent | Not skipped: it plays with alarm usage |
  | Do Not Disturb, alarms only or priority | Not skipped |
  | Do Not Disturb, total silence | Skipped: "Do Not Disturb" |

  `BackByWatch`'s "partly delivered" log used to say "vibration issued" for a skipped buzz. It now says
  "skipped: <reason>", as `SundownWatch` does.

## Tests added or changed

- **`TrackRecordingServiceWatchFailureTest`.** A fourth case: a back-by watch that throws on every fix, through the
  real service. All 20 points are still stored.
- **`NavigationHudQuickSettingsWidthTest`.** It also reads the turn and the distance's kind. It adds data B's longest
  first row ("Sharp right · 169°", "1280 ft by trail"). This test is red; see the width clash below.
- **`AvailabilityScreenQuickSettingsTest`** (back-by's own; it first ran in this build):
  - **Missing import:** the `click` import was added.
  - **Touch test:** it now composes the screen afresh between samples. Under Robolectric, neither Back sent to the
    activity nor a touch on the map's window reaches the menu's popup window, so neither one closed it.
  - **The dot:** it is now read in the unmerged tree, because it sits inside the clickable square.
  - **New test:** held down, the icon dips under a round press and springs back.
- **`BackByWatchTest`.** New: a skipped buzz is logged as skipped, with its reason.
- **`BackByVibrationRecordTest`** (new). The back-by buzz's record on silent, under total silence and under alarms
  only, through the real `deliverReporting`.

## Revert checks: nine, all bite

- **How they ran:** the runner saved a copy of the file and made a one-line edit. It refused to read results if the
  compile log had an `e:` line; none did. It deleted the old JUnit XML and ran the named classes. Then it restored the
  file from the saved copy and confirmed the file matched the forward version.
- **Forward change still present afterwards:** true for all nine.

| Revert | Failures, read from the XML |
|---|---|
| R1 back-by fix call unguarded | 1: "every fix's point is stored although BACK_BY_ON_FIX threw on each expected:<20> but was:<0>" |
| R2 button clipped to a circle | 1: "a real touch at (351.0, 48.0) in DpRect(left=348.0.dp …) opened the menu" (the top-left corner) |
| R3 no press bounce on the icon | 1: the press test, "Key not present: PressBounceScale". The bounce's own semantics go with it, so the test stops before its "dipped" assertion. It still fails only because the bounce is gone. |
| R4 Back by buzzes off-track's two | 1: "Back by vibrates three pulses", expected 6 elements, got 4 |
| R5 sundown alerts buzz two | 1: "the sundown alerts vibrate three pulses", HEADS_UP expected 6, got 4 |
| R6 skipped buzz logged as issued | 1: the new skipped-buzz log test |
| R7 arrival counted without Return | 1: "without Return, being at the start does not end it": "still fires expected:<1> but was:<0>" |
| R8 +30 min counted from the old time | 2: the watch's +30 min test and the service's, through the notification's own intent |
| R9 line shown outside the last hour | 1: "the strip shows Back by … only in the last hour": "61 min away: no line" |

## The width clash (RECORD -690): measured, nothing changed

The layout is unchanged, and neither data B's tests nor the width test were touched.

**How it was measured:** the real `AvailabilityScreen`, data B's own setup and native graphics. Each text node's box
was compared with the width it needs: `maxIntrinsicWidth`, and the characters actually drawn. The baseline is the same
build with the button removed for one run, restored from a saved copy, so it stands in for main's layout. The
measuring test was temporary and is not committed. "Short" means needed width minus the box width.

### 360 dp portrait

**With no button, every line is whole.** With the button:

| Where | Line | Box / needs | Short | Shows |
|---|---|---|---|---|
| Strip (MGRS and decimal) | heading "315° NW" | 32.0 / 50.75 (decimal: 36.5) | **18.75** (decimal 14.25) | "315" (decimal "315°") |
| Strip (MGRS and decimal) | elevation "9843 ft" | 30.5 / 42.75 (decimal: 33.5) | **12.25** (decimal 9.25) | "98" (decimal "984") |
| Display, first row | kind "by trail" | 30.0 / 40.25 | **10.25** | "by " |
| Display, first row | status "≈ 1250 ft straight" | 87.0 / 102.25 | **15.25** | "≈ 1250 ft str" |

- **What stays whole:** "Sharp right · 169°", "1280 ft", the labels "Facing" and "Alt", the coordinates (MGRS and
  decimal) and the sundown line.
- **Where the button sits:** in the strip at x 324 to 360. In the display at x 256 to 292, left of the X.
- **The strip's height:** 18 dp without the button, 36 dp with it.
- **Other states that cut** (from `NavigationHudQuickSettingsWidthTest`): "Unable to calculate route" (shows "Unable
  to calculate r"), "Location services unavailable" (shows "…unava") and "Last seen 23 h ago, finding GPS…" (shows
  "…findi") are cut only with the button.
- **Cut either way:** "No origin waypoint for this track" is cut with or without the button, so the button is not
  the cause there.

### 384 dp portrait (the S22 Ultra)

**Every line is whole, with the button,** on both the strip and the display, in MGRS and decimal. The tightest
margin is 0.17 dp.

### 780 × 360 dp landscape (the S22, as data B measures it)

The small fixes already make facing and altitude give way here, so some lines are cut with no button at all.

| Where | Line | Without the button | With the button |
|---|---|---|---|
| Display | "1280 ft" | whole | **short 8.17**, shows "128" |
| Display | "by trail" | short 4.5, shows "by tr" | **box 0, nothing drawn** |
| Display | "≈ 1250 ft straight" | short 7.5, shows "≈ 1250 ft strai" | **short 55.5**, shows "≈ 125" |
| Display | heading "281° W" (MGRS) | short 1.5 | short 1.5 (same) |
| Display | elevation "9843 ft" (MGRS) | short 0.83 | short 0.83 (same) |
| Strip | heading | short 17.5 (decimal 13.17) | identical |
| Strip | elevation | short 11.5 (decimal 8.5) | identical |

**The landscape strip is identical with and without the button because the button is not there.** At 780 × 360 it is
laid out 0 dp wide at the strip's right edge (x 700). Its clipped bounds are empty, so it cannot be seen or touched.
The strip is still 36 dp tall, which shows the button was measured.

- **Why:** in landscape the strip is content-width. The column of readings is not weighted, so it takes the whole
  capped width the small fixes allow and leaves the button nothing.
- **Wider windows:** at 823 × 384 (the quick-settings landscape test) the button shows, because the cap is wider.
- **Consequence:** on a 780 dp landscape screen today there is **no way into the quick menu from the strip**. While
  navigating, the display's button does show, at x 596 to 632.

## Other existing tests this build breaks: reported, not touched

**`AvailabilityScreenMapIconStackTest`, two tests:**
- "the search dropdown starts below the compass strip, not over it": the dropdown's top is 67 dp, the strip's bottom
  85 dp.
- "the icon bar cannot be dragged above where the search dropdown would start": the bar's top is 71 dp, the strip's
  bottom 85 dp.

**Cause** (read from the code):
- The dropdown's offset (`AvailabilityCompactScaffold.kt`, `searchDropdownTopOffset`) and the icon bar's top limit
  (`AvailabilityCompactMapUi.kt`, `topLimitPx`) both assume the strip is one text line tall (`compassStripClearance`,
  the height of "Mg").
- The button's 36 dp floor makes the strip 18 dp taller than that, so both now reach up over the strip's lower half.

**Same assumption elsewhere, not checked by any failing test (read, not run):** the observation bubble's `minY` and
the taxon and journal chips' top padding also use `compassStripClearance`. They will probably sit under the strip's
lower 18 dp too.

**Not fixed here:** a fix moves other controls (the dropdown, the icon bar's limit, the chips and the bubble). The
dispatch made a change to another control's position a stop.

## Full suite

The whole of `:app:testDebugUnitTest`, read from the JUnit XML (543 files): **4,300 tests, 8 failures, 0 errors, 24
skipped**. No `@Ignore` is added on this branch. The 24 skipped are existing `@Ignore`s; I did not count main's for
comparison.

All 8 failures are the two causes above:
- **The width clash (6):**
  - the four 360 dp tests in `AvailabilityScreenNavigationWordsTest` (data B's);
  - `NavigationHudQuickSettingsWidthTest`;
  - `AvailabilityScreenReturnRouteTest > the whole sentence shows at 360 dp`: "Unable to calculate route" is cut
    beside the button. This is the same figure the width test shows ("Unable to calculate r").
- **The taller strip (2):** the two `AvailabilityScreenMapIconStackTest` tests.

None of the failing tests was touched. `AvailabilityScreenNavigationWordsLandscapeTest` passes. Its assertions do not
cover the landscape strip's missing button or the display's cut "by trail".

## Device-only, unverified

On the S22 (dispatch, "Device-only"):
- set +1 h with the time adjusted so it fires during the check;
- the alert with the phone silenced and the app swiped away;
- both buttons;
- the reminder ending on arrival after Return.

Also device-only:
- the round press and the bounce, as they look on the phone;
- whether an open menu closes on Back and on a map tap on the phone (Robolectric cannot show it);
- what the record says under each Do Not Disturb mode on the real phone.

## RECORD -709: the owner's answers to the width clash and the taller strip, built where they could be

The owner, verbatim:
1. "Coordinates first, same rule (Recommended)"
2. "Second line under the distance (Recommended)"
3. "Follow the strip's real height (Recommended)"

Item 4 is the planner's: the landscape strip's button always gets its 36 dp.

### Built

- **Item 3, things placed below the strip follow its real height**
  - **Portrait:**
    - In `CompactMapTab`, a new `compassStripBottomClearance` is the strip's measured height. The old one-line
      clearance is its floor, and it is used whenever the strip is not measured: before its first layout, while
      navigating, and in landscape, where the strip sits in the other corner.
    - The observation bubble's `minY`, the icon bar's drag limit, and the taxon and journal chips' top padding all read
      it.
    - The scaffold's search dropdown reads the same height, handed up through `searchBarSlot`.
    - **Landscape is unchanged.**
  - **New tests:** `StripRealHeightClearanceTest`, one class at 360 dp and one at 780 x 360. It has four tests: the
    dropdown, the icon bar dragged up 2000 dp, the journal chip, and a bubble. All go through the real screen.
    - **Portrait:** each must start at or below the strip's bottom.
    - **Landscape:** each must not overlap the strip.
  - **Bubble test design.** A glyph whose card would open with its top just over the strip's lower half. The card's
    layout box starts about 12 dp above the card. Under the old `minY` the card stayed above the glyph and covered the
    strip; under the strip's real height it opens below.
  - **Pins, not proof:** the landscape class passes either way, because landscape did not change.
  - **Existing tests now passing as written:** the two `AvailabilityScreenMapIconStackTest` tests.
  - **Risk carried over:** an older comment records a regression from reading a measured height into the bubble's
    `minY`. The test that caught it ("tapping elsewhere on the map dismisses the observation bubble") is `@Ignore`d
    for an unrelated harness reason, so whether that regression returns is a device item.
- **Item 4, the landscape button.**
  - **The fix:** the strip's readings column is now weighted but not filling in landscape. It is measured after the
    button, so the button keeps its 36 dp.
  - **New tests:** `LandscapeStripButtonTest` at 780 x 360 and 823 x 384. It uses data part B's live fix and the
    longest readouts, checks the button is 36 x 36 at the strip's far right, and makes five real touches across its
    square, corners included. Each opens the menu.
  - **A first version did not bite and was replaced.** It was in `AvailabilityScreenQuickSettingsTest`, with no fix,
    so the strip never ran out of width.

### Revert checks for the new pieces

- **Method:** the same runner, with the compile log checked first, each file restored from a saved copy, and the
  forward change confirmed present afterwards.
- **R10 and R11 at first:** with the change reverted, R10 ran 15 tests with 0 failures and R11 ran 8 with 0 failures.
  Those tests could not fail. R10's had no fix. R11's glyph opened its bubble below the strip either way; instrumented,
  the card measured 106 dp tall and opened below its glyph under the old `minY` too.
- **Final results:**

| Revert | Failures |
|---|---|
| R10 landscape readings column unweighted | 2: at 780 x 360 the button is "DpRect(left=700.0.dp … right=700.0.dp …)", 0.0 wide; at 823 x 384 it is 11.67 wide |
| R11 bubble minY one text line | 1: "the bubble's card [12.0, 82.0][292.0, 188.0] starts at or below the strip's bottom [0.0, 49.0][360.0, 85.0]" |
| R12 icon bar limit one text line | 1: "the icon cluster [304.0, 67.0][352.0, 447.0] …" |
| R13 chips one text line | 1: "the journal chip [162.5, 82.0][198.0, 116.0] …" |
| R14 dropdown one text line | 1: "the search dropdown [0.0, 67.0][360.0, 560.0] …" |

R12 to R14 ran before the bubble test was changed; their failing tests are unchanged since.

### Not built: two stops

**Item 1, the strip on small phones: the rule is already in force, and two of data B's tests cannot pass under it.**
- **Where the rule already applies:** the small fixes' coordinates-first rule (`stripReadoutsShown` and
  `readoutsKeptBeside`, `AvailabilityMapControlsUi.kt`) already runs in portrait. Since the back-by merge the strip's
  readings column has been measured after the three-dot button, so the button's 36 dp come out first.
- **What it does at 360 dp:** about 127 dp is left for both readouts after the coordinates and separators. Each can
  keep its 48 dp minimum, so both stay and both shorten.
  - "Facing 315° NW" needs 89.75 dp.
  - "Alt 9843 ft" needs about 63 dp.
  - Measured result: the heading's reading box is 32 dp and shows "315"; the elevation's is 30.5 dp and shows "98".
- **The conflict:** data B's "at 360 dp the strip's labelled line fits whole" and "… decimal coordinates fit whole
  beside its labels" assert that the heading and elevation are not ellipsised. That is exactly what the owner's rule
  gives up at 360 dp with the button.
- **Not touched.** The choice is between changing these two tests' claim to "coordinates whole; readouts give way
  under the rule" and some other answer. That is for the owner.

**Item 2, the display on small phones: the ruling does not fit in the row's 48 dp at the theme's line heights.**
- **Measured with a temporary test:** the first row is 48 dp. That comes from the X's 48 dp minimum touch size; its
  semantic bounds are 40 dp. The distance and status column inside it is 24 + 16 = 40 dp (titleMedium, then
  labelMedium). So 8 dp are spare.
- **What the ruling needs:**
  - A separate "by trail" or "straight" line under the distance: 24 + 16 + 16 = 56 dp, 8 dp over.
  - "Unable to calculate route" is drawn in the large distance slot, not the status line. Wrapped there it takes two
    24 dp lines, plus the 16 dp status line: 64 dp. That state also already adds a 48 dp "Try again" row (the display
    is 120 dp then, measured).
- **Options for the owner**, not chosen here:
  - (a) Tighter line heights for the three lines, so 24 + 12 + 12 fits 48. This is a type change and needs a
    large-font check.
  - (b) Let the first row grow to 56 dp, only when the line is needed.
  - (c) Put the kind at the start of the status line, which the status's own shorter form then makes room for.
  - (d) Drop the kind when it does not fit.
  - (e) For "Unable to calculate route", a smaller type in the large slot when it does not fit.
- **Not built, and the four display-side tests stay red:** data B's two display tests, `NavigationHudQuickSettingsWidthTest`
  and `AvailabilityScreenReturnRouteTest` "the whole sentence shows at 360 dp".

### Full suite after -709

`:app:testDebugUnitTest`, read from the JUnit XML (547 files): **4,310 tests, 8 failures, 0 errors, 24 skipped.**
No `@Ignore` added. None of the eight was touched.

**Item 1's stop (2):** data B's two 360 dp strip tests.

**Item 2's stop (4):** data B's two 360 dp display tests, `NavigationHudQuickSettingsWidthTest` and
`AvailabilityScreenReturnRouteTest > the whole sentence shows at 360 dp`.

**New with -709, caused by item 4 (1):** `LandscapeLargeFontTest > font 1,0 the strip and the display are whole, one
line each, and clear of the search bar` (the small fixes' test, at 823 x 384), failing with "font 1.0 <306° NW> is not
ellipsised".
- **Before -709:** the button was squeezed to 11.67 dp there, so the readouts fitted whole.
- **Now:** with its 36 dp, the heading shortens under the owner's rule. It is the same conflict as item 1, in landscape.

**New with -709, caused by item 3 (1):** `MapChromeColourPixelsLightTest > the search panel is drawn as the token at
0_8`, failing with "expected (0.957, 0.937, 0.890), read (0.961, 0.945, 0.902)".
- **Confirmed caused by the dropdown change:** with that one line reverted, the class passes.
- **Why, measured by instrumenting the test:**
  - The test reads its "bare map" reference at `root.bottom - 200 dp` (y 623 at 384 x 823).
  - The dropdown, moved down to follow the strip, now ends at 624.67. So the reference point lies inside the panel.
  - Every pixel sampled inside the panel reads the same colour as that "reference", because both are the panel.
- **Conclusion:** the panel is drawn. The test's reference point is what moved under it.
- **Not touched:** the fix would be a reference point lower down, which is a change to an existing test.

## RECORD -713: labels before values, the kind in the status line, the pixel reference

The owner, verbatim:
1. "Drop labels, then values (Recommended)"
2. "Move it into the status line (Recommended)"

Item 3, the pixel reference, is the planner's.

### Built

**1. `readoutsFitBeside` (`AvailabilityMapControlsUi.kt`).** It is used by the strip and by the display's second row.
- The coordinates are measured first and stay whole.
- Then the first of these arrangements that fits whole is used:
  - every readout with its label;
  - every readout without labels ("315° NW · 9843 ft · grid ref");
  - facing dropped, still without labels;
  - altitude dropped too.
- A value is never cut and never ends in "…".
- Unit tests: `ReadoutsFitBesideTest`, six cases.
- At 360 dp with the button, the strip now draws "315° NW · 9843 ft · 10T ER …" with no labels, every value whole.

**The old rule is no longer called.** `readoutsKeptBeside` and `stripReadoutsShown` stay, with their tests in
`StripReadoutsShownTest`. Those tests still pass, but they now exercise a rule nothing calls. Whether to remove them is
the planner's or the owner's call.

**Tests, per -713:**
- **Changed, citing -713:** data B's "at 360 dp the strip's labelled line fits whole". It is now "at 360 dp the strip
  drops its labels first and keeps its values and coordinates whole". It asserts that no "Facing" or "Alt" label is
  drawn, that the heading and elevation values are whole, and that the coordinates are whole.
- **Unchanged, because they pass as written under the rule:**
  - data B's "at 360 dp the strip's decimal coordinates fit whole beside its labels". Its assertions are values and
    coordinates whole, and it does fail when the labels are forced back (R15 below).
  - `LandscapeLargeFontTest`'s font-1.0 case. It asserts every text in the strip is one line and whole, and every shown
    readout now is.

**2. The display's first row (`NavigationHud.kt`).**
- When the figure and its kind do not fit side by side, the kind starts the status line, capitalised:
  "By trail · …".
- "Unable to calculate route" takes the status line's size when it does not fit the large slot.
- Nothing is added below the row, so it stays the X's 48 dp.
- New test: `NavigationHudKindInStatusTest`. At 360 dp with the button, "By trail" is whole, below the figure, and at
  the start of the status line. The display's height equals its height without the button, where the kind sits beside
  the figure.
- **Choice made here:** the shrinking applies only when "Unable to calculate route" does not fit. Read literally, the
  ruling could also mean always. This was not asked.

**3. `MapChromeColourPixelsTest`, changed per -713, the planner.** The bare-map reference point is now 8 dp below the
search panel's measured bottom, and is checked to sit above the bottom bar. Before, it was a fixed 200 dp up from the
bottom. The claim is unchanged.

### Revert checks (saved-copy restore, compile log checked, forward change confirmed present after each)

| Revert | Failures specific to the edit |
|---|---|
| R15 strip keeps its labels | the changed strip test: "the label <Facing> is dropped before any value"; the decimal strip test: "strip decimal coordinates … not ellipsised" |
| R16 the rule never drops labels | 4 `ReadoutsFitBesideTest` cases, e.g. expected `labels=false, shown=[true, true]`, was `labels=true, shown=[false, true]`; both strip tests: the heading node is gone |
| R17 the kind never moves | `NavigationHudKindInStatusTest`: expected "[B]y trail", was "[b]y trail" |
| R18 "Unable to calculate route" keeps the large size | `AvailabilityScreenReturnRouteTest > the whole sentence shows at 360 dp`: "not ellipsised" |

R15 and R16 runs also list the two display tests below. Those fail before any revert too, so they are not the reverts'
failures.

### Full suite after -713

`:app:testDebugUnitTest`, read from the JUnit XML (549 files): **4,317 tests, 3 failures, 0 errors, 24 skipped.** No
`@Ignore` added.

Passing again: the `AvailabilityScreenReturnRouteTest` 360 dp test, `LandscapeLargeFontTest` and the pixel test.

**The three that remain have one cause: the status line itself does not fit the first row's distance column at 360 dp
with the button.**
- **The space:** the column is 87 dp, between the turn column and the three-dot button.
  - Without the kind, "≈ 1250 ft straight" needs 102.25 dp.
  - With "By trail ·" in front, the status keeps a 34 dp box and shows "≈ 12".
- **The failing tests:**
  - data B's "at 360 dp the longest lines fit whole" and "at 360 dp the display's decimal coordinates fit whole beside
    the longest lines": both fail with "status <≈ 1250 ft straight>: not ellipsised".
  - `NavigationHudQuickSettingsWidthTest`: "Location services unavailable" shows "…unava", and "Last seen 23 h ago,
    finding GPS…" shows "…findi". Both are cut only with the button. "≈ 1250 ft straight" shows "≈ 12".
- **Why -713 doesn't fix them:** the ruling moves the kind and shrinks the large-slot word, but the status line was
  already too wide for this column. Its only fallback is the small fixes' "…" (RECORD -694). These tests' claim,
  "whole", cannot hold without another decision.
- **Possible directions (none chosen):**
  - let the status wrap within the row's 8 spare dp, which isn't a full line;
  - shorter status wordings at narrow widths;
  - a smaller status type;
  - relax these tests' claim to the status line's existing "…" rule;
  - move the three-dot button out of the first row on narrow screens.
- **None of the three tests was touched.**

## RECORD -714: the display's button moves to the second row when the first row is too narrow

The owner, verbatim: "Button moves to second row (Recommended)".

### Built

**Where the button goes, from a measured fit (`firstRowHoldsTheButton`, `NavigationHud.kt`).**
- **The reference line.** The fit is measured against one fixed reference line: data part B's longest route line.
  It is "Sharp right · 169°", then "1280 ft" with "by trail", then "≈ 1250 ft straight".
- **How the column is worked out.** The first row's distance column, with the button in it, is the display's width
  minus the row's fixed parts:
  - its padding;
  - the north arrow (22 dp) and the turn column (measured);
  - the X's 48 dp touch box and the button's 36 dp;
  - four 12 dp gaps.
- **The test.** The figure, its kind and the status are measured in their own type at the current font scale. The
  button stays left of the X only if the status is whole, either beside the figure and kind or with the kind leading it
  (RECORD -713). Otherwise the button ends the second row.
- **Why one reference, not what is on screen now:** the button does not move between rows, and the display does not
  change height, as statuses come and go during a walk.

**Where that puts it (measured):**
- 320 dp and 360 dp portrait: the second row.
- 384 dp portrait (the S22): left of the X.
- **780 × 360 and 823 × 384 landscape: the second row.** The display there is capped at 318 dp and 359 dp, and the
  distance column left beside the button is about 45 dp. **This is contrary to the planner's expectation that it stays
  left of the X in landscape.** I followed the measured fit, as asked.

**The second row with the button in it:**
- The button's 36 dp and its gap come out first. `readoutsFitBeside` then gives way within what is left.
- The row is shown even where it had nothing else to show (no fix), so the button keeps a place.
- The row becomes 36 dp tall, against the 24 dp coordinates line. So wherever the button sits there, the display is
  12 dp taller than before. In landscape the display is 108 dp, still above the central third at 120 dp.

**Tests:**
- `NavigationHudButtonPlacementTest`, two classes:
  - at 360 dp, the button ends the second row, below the X's row, and the status line reads "≈ 1250 ft straight",
    whole;
  - at 384 dp, it is left of the X in the X's row.
  - Both: 36 × 36, and five real touches across the square, corners included, open the menu.
- `NavigationHudKindInStatusTest`, my own from -713, reworked. At 360 dp the kind now fits beside the figure, so the
  "kind leads the status" case is set up at 320 dp. Its height claim is now that the status line ends inside the 48 dp
  first row. The old claim, that the display is as tall as without the button, no longer holds where the button is in
  the second row.

**Deleted (the planner's call):** `readoutsKeptBeside`, `stripReadoutsShown`, `StripReadoutsShown`,
`STRIP_READOUT_MIN_WIDTH` and `StripReadoutsShownTest`.

**Kept:** "Unable to calculate route" takes the status size only when it does not fit the large slot.

### Revert checks (saved-copy restore, compile log checked, forward change confirmed after each)

| Revert | Failures |
|---|---|
| R19 button always left of the X | the 360 dp placement test: "below the X's row: button … top=10.0 …"; `NavigationHudQuickSettingsWidthTest`, the status lines cut again ("Location services unava", "…findi") |
| R20 button always in the second row | the 384 dp placement test: "left of the X: button DpRect(left=340.0 …)" |

### Full suite after -714

Read from the JUnit XML (550 files): **4,313 tests, 4 failures, 0 errors, 24 skipped.** No `@Ignore` added. The count
is down from 4,317 because `StripReadoutsShownTest` was deleted, and the new placement tests were added.

**The status lines are now whole at 360 dp.** "Location services unavailable", "Last seen 23 h ago, finding GPS…" and
"≈ 1250 ft straight" are no longer cut with the button.

**The four failures are claims these tests make that -714 contradicts or never addressed. None was touched.**

1. **Data B's three display tests at 360 dp** expect the "Facing" and "Alt" labels in the second row:
   - "on the route the display reads the turn in words, the route by trail, the straight line straight, and labels its
     heading and altitude";
   - "at 360 dp the longest lines fit whole";
   - "at 360 dp the display's decimal coordinates fit whole beside the longest lines".

   Each fails with no node "Facing" in the display.
   - **The arithmetic:** the button's 36 dp plus its gap now come out of the second row, leaving about 300 dp.
     "Facing 281° W · Alt 9843 ft · " plus the MGRS reference needs about 311 dp. These are widths measured earlier in
     this branch, not re-measured in this configuration.
   - **So:** the owner's -713 rule drops the labels and keeps every value whole, which is what happens.
   - **The change these would need:** "labels may drop under -713" in place of "labels drawn". That is the same claim
     change as the strip test changed under -713.
2. **`NavigationHudQuickSettingsWidthTest`** now fails only on "No origin waypoint for this track", which shows "No
   origin waypoint for this tr".
   - The test files this under "clipped either way": it is cut with no button at all, so not by the button.
   - The test's claim includes that any status it lists is whole at 360 dp, button or not. That is about a state
     -714 did not touch.
   - This is back-by's own test, written by the first coder; it has never passed.
   - **The change it would need:** drop the "clipped either way" half, or decide that status separately.

## RECORD -715: test claims changed; the suite is green

The owner, verbatim: "Keep it, 12 dp taller (Recommended)". The measured placement stays as built, landscape included.

**Claims changed, each citing -715:**

1. **`AvailabilityScreenNavigationWordsTest`, data part B's three 360 dp display tests**, changed to RECORD -713's rule:
   - **The shared check, `assertDisplayWhole`.** It used to require the "Facing" and "Alt" labels drawn. It now asserts:
     - the first row's lines (the figure, its kind, the turn, the status) are whole;
     - each readout that is shown (heading, altitude) is whole, and so is each label that is drawn;
     - the coordinates are whole.
   - **Through that check:** "at 360 dp the longest lines fit whole" and "at 360 dp the display's decimal coordinates fit
     whole beside the longest lines".
   - **Renamed:** "on the route the display reads the turn in words, the route by trail, the straight line straight,
     and labels its heading and altitude" is now "… and its heading and altitude, labelled where they fit". Where a label
     is drawn, it is still checked to sit before its own reading.
2. **`NavigationHudQuickSettingsWidthTest`, its "clipped either way" half**, changed to RECORD -694's rule. A line cut
   with or without the button must be one line ending in "…". The only such line is "No origin waypoint for this
   track", which is too long for the column even with no button. A line that only the button cuts must still be whole.

**Measured at 360 dp after the change** (from the tests' own `MEASURED` lines):
- The status "≈ 1250 ft straight" is whole: box 102.5 dp, needs 102.25 dp.
- "by trail" sits beside "1280 ft", whole.
- The second row's heading "281° W" and elevation "9843 ft" are whole, and the coordinates are whole.

**Full suite:** `:app:testDebugUnitTest`, read from the JUnit XML (550 files): **4,313 tests, 0 failures, 0 errors,
24 skipped.** No `@Ignore` added; the 24 skipped are existing `@Ignore`s.
