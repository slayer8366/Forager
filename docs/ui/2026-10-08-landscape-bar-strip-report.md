# Landscape: the search bar and the strip meet at the centre

Dispatch 2026-09-28-729 (RECORD intent -729; preserved at `prompts/preserved/2026-10-08-03.md`). Branch `landscape-bar-strip`,
cut from `origin/main` at `f8739856` (PR #202 merged; fetched and confirmed before cutting). No PR. Paths are relative to
`app/src/main/java/com/zynergylabs/forager/app/ui/availability/` unless they start with `app/` or `docs/`.

**Superseded in part by RECORD -732 (section at the end): the join now moves to fit the strip.** As first built (-729):
**full suite 4,432 tests, 24 skipped, 6 failures, all six existing tests that this change breaks,
left untouched and listed below with their reasons.** Two of them (the large-font strip) are a real conflict between this
ruling and RECORD -699, and need the owner.

## Premises checked

1. **"Left half / right half" holds at ROTATION_90 only.** The bar follows the punch-hole and the strip the rail
   (`punchHoleEdgeFor`, `portEdge`); at ROTATION_270 both swap. Built mirrored. The three-dot button stays at the strip's own
   right end in both rotations (RECORD -649), so at 270 it sits at the join, beside the line. Tested at both rotations.
2. **Wrong premise: meeting the bar gives the strip no more room.** Before, the strip was already capped at the room beside
   the bar (RECORD -694), which reached to the bar's end (8 dp short of the centre at 780 dp; at 823 dp the bar stopped at
   its 384 dp cap, 27.5 dp short). Now the bar ends at the centre, so the strip is *narrower*: 310 dp at 780 (was 318) and 331.5 dp at 823 (was 359), font 1.0. The heading that 14 sp
   dropped (the dispatch's "Why") is still dropped; see Measurements.
3. **The 384 dp cap and the 8 dp gap no longer apply while not navigating.** Item 1 says the bar ends exactly at the centre;
   at 823 dp the cap would have stopped it at 384. Removed for that state only.

## What changed

- `AvailabilityCompactScaffold.kt`: in a short landscape window on the Maps tab, not navigating, `landscapeSearchWidth` is
  `maxWidth / 2 - punch-hole inset` (ends at the centre). `mapSearchBarHeight` is the strip's measured height there (else the
  bar's own `searchBarHeight`, 45 dp); everything the scaffold places by the bar's bottom on the Maps tab follows it: the
  map's top inset (chips, bubble), the L's top limit, the dropdown and its scrim. While navigating, the old capped width and
  the bar's own height (the owner: "Strip only").
- `AvailabilitySearchUi.kt`: `SearchEntryBar(landscapeHeight)`: when set, the bar is exactly that tall, the field row centred
  above its divider. `null` everywhere else (portrait, other tabs, navigating, before the strip is measured).
- `AvailabilityCompactMapUi.kt`: the landscape strip takes the rail-side half (`landscapeStripHalf`), fills it
  (`contentWidth = false`), is held at least as tall as the bar's field plus divider (`landscapeBarContentFloor`, 33 dp at
  font 1.0), and reports its height up (`onLandscapeStripHeightMeasured`). The 1 dp line (`LANDSCAPE_BAR_STRIP_LINE_TAG`),
  `mapIconStackBorderColor()` (the divider's colour pair), centred on the window, as tall as the two; only over their join,
  no pointer input, not in fullscreen or navigating.
- `LandscapeChromeWidth.kt`: `Modifier.landscapeStripHalf`, the strip's width worked out (not read back), as the room left
  after the bar's own rounded pixels, so an odd window width neither overlaps nor gaps them.
- `AvailabilityMapControlsUi.kt`: the strip's Box centres its row vertically (only visible when held taller, at large fonts).
- `AvailabilityMapIconCluster.kt`: the cluster's re-fit is also keyed on the top limit, **in landscape only**. Found by the
  first full suite: the L kept the clamp of the first frame (45 dp, before the strip was measured), so it did not follow
  the bar's new bottom, and a later notice moved it 1 dp (LandscapeLRulingsTest B1, SearchNoticeLandscapeTest). Portrait's
  limit animates with fullscreen and is deliberately not a key (CompactMapTab's `searchBarBottom` comment); unchanged.

Portrait: no code path changes (every change is gated on the landscape window or `state.landscape`); the portrait tests in
the full suite pass.

## Measurements (native graphics, xxhdpi, font 1.0; `LandscapeBarStripJoinTest` prints them)

| Window, rotation | Bar | Strip | Line | Height |
|---|---|---|---|---|
| 780 x 360, 90 | 0 to 390 | 390 to 700 (rail 700) | 389.67 to 390.67 | 36 / 36 |
| 780 x 360, 270 | 390 to 780 | 80 to 390 | 389.67 to 390.67 | 36 / 36 |
| 823 x 384, 90 | 0 to 411.67 | 411.67 to 743 | 411 to 412 | 36 / 36 |
| 823 x 384, 270 | 411.33 to 823 | 80 to 411.33 | 411 to 412 | 36 / 36 |

The bar is 36 dp, down from 45; the strip 36 dp (its button's floor). The three-dot button is 36 x 36 at the strip's right end.

**What the strip shows whole** (heading 315° NW, altitude 3,000 m = 9843 ft, Imperial default): at both windows, both
rotations, both formats: **altitude and coordinates; the heading is dropped, and the Alt label.** MGRS "10T ER 24991 40768",
decimal "45.5200, -122.6800". Every value shown is whole (not ellipsised, every character visible).

Why, from a probe of the 14 sp widths (removed after use): heading 58.3 dp, "9843 ft" 48.3, "Alt" + gap 23.0, a separator
20.3, MGRS 140.3, decimal 132.0. Room for the readouts is the strip less its button (36), padding (16) and needle (18): 240 dp
at 780, 261.5 at 823. Adding the heading needs 307.9 (MGRS) / 299.6 (decimal): short by 68 / 60 dp at 780 and 46 / 38 dp at
823. Before this change the room was 248 dp at 780 and 289 at 823, and the same readouts showed (heading dropped at both).

## Tests (`app/src/test/.../ui/availability/LandscapeBarStripJoinTest.kt`, 16 tests)

Real `AvailabilityScreen`, 780 x 360 and 823 x 384 (xxhdpi, native graphics), ROTATION_90 and 270, each proving the screen
saw the rotation:
- the join: the bar's inner edge, the strip's inner edge and the line's centre each equal the map's centre (±0.5 dp; positive
  control that the map's centre is the window's); bar at the punch-hole edge, strip at the rail's inner edge; equal tops and
  heights; strip ≥ 36 dp; line 1 dp wide, from the bar's top to its bottom;
- five real coordinate touches across the three-dot button each open the menu (screen composed afresh between touches);
- long-presses around the join reach the map: 10 points just below it at both rotations, plus 3 on the strip beside the join
  at 90 (13 sampled); at 270 those 3 land on the button and are skipped, listed in the output, and the count is asserted;
- the readouts shown are one line and whole in MGRS, then in decimal after a real touch on the coordinates (positive
  control: the text changed).

## Revert checks

Runner: saves the file, applies a one-line edit, runs the named classes in a fresh capped scope, refuses results on any `e: `
line (none), reads only JUnit XML written after the run started, restores from the saved copy (never git) and checks it
byte-identical. Run on the final code; `git status` clean after; each forward line confirmed present by grep.

| # | Edit | Class | Failed | Message |
|---|---|---|---|---|
| R1a | bar back to the capped width | LandscapeBarStripJoin780/823 | 2/8 each | "the bar's right edge is the centre expected 390.0 but was 382.0" (823: 384.0; 270: 398.0 / 439.0) |
| R1b | strip back to the room beside the bar | same | 2/8 each | "the strip's left edge is the centre expected 390.0 but was 382.0" (823: 384.0) |
| R2 | bar keeps its own height | same | 2/8 each | "the bar is the strip's height expected 36.0 but was 45.0" |
| R3 | no line | same | 2/8 each | "could not find any node ... 'landscape-bar-strip-line'" |
| R4 | cluster not re-fitted on the limit | LandscapeLRulingsTest, SearchNoticeLandscapeTest | 2/18, 1/1 | "the L did not move ... expected 45.0 but was 44.0"; "the cluster returned to where it was expected 47.0 but was 44.0" |

## Existing tests this breaks (not touched)

- `AvailabilityScreenLandscapeB2Test` S2 at 90 and 270: assert the bar's width is `min(384, centre - 8)` and ends 8 dp short
  of the centre. Item 1 replaces that (412 at 823 dp). The ruling they encode (Landscape B2 S2) is what -729 reverses.
- `AvailabilityScreenLandscapeB2Test` S1 at 90 and 270: assert the bar "stays clear of the far half" with the same 8 dp gap.
  Same reason.
- **`LandscapeLargeFontTest` "font 2,0 ... the strip stays beside the search bar on one line", at 90 and 270: the
  coordinates are ellipsised, "10T ER 24991 40768" shows 15 of 18 characters.** At font 2.0 the rail is 107 dp, so the
  strip's half is 304 dp; before it was 332 (the bar stopped at 384). RECORD -699 (the owner: "Coordinates take priority
  (Recommended)") keeps them whole. This is a real conflict between the two rulings at font 2.0 on the 823 dp window, not a
  harness artefact. Options for the owner: accept the cut at font 2.0; let the join leave the centre when the coordinates
  need it; or trim the strip's padding or needle there. Not decided here.

## Decided beyond scope (each reversible)

1. **Large fonts.** The bar's field (twice the labelMedium line) plus divider is 33 dp at font 1.0, under the strip's 36, so
   the bar follows the strip. At font 2.0 it is 65 dp, taller than the strip's row; rather than cut the field (a control
   losing its touch area, a stop), the strip is held at that height and the bar follows it. So at large fonts the strip grows
   to the bar, not the reverse.
2. **The bar's divider is kept** as its bottom edge in landscape, so a 1 dp line runs under the bar's half only. Not asked to
   remove it.
3. **Under legacy (non-native) graphics** Robolectric measures "Mg" far taller, so in those test classes the landscape bar
   and strip are 73 dp. A harness effect, shown in the B2 failures' bounds; the native-graphics tests above are the measure.
4. **Starting or stopping navigation:** the bar's width switches at once while the strip slides out (or in) at half width,
   so for the slide (the map tilt's length) the leaving strip overlaps the bar's centre end. Not tested; device step 3.
5. In fullscreen the line is not drawn and the strip keeps its half.

## Device only (S22, cheap first)

1. Landscape, Maps tab, with a fix, both rotations. Pass: bar and strip meet at the centre with one thin line between them,
   the same height, no band of fill hanging below either. Evidence: a screenshot (kept off GitHub).
2. Against the real cut-out and status bar: the bar starts at the cut-out side's controls edge and still ends at the centre.
3. Start and stop navigation: the strip slides out and in; note whether the brief overlap with the bar's end shows.
4. Tap the search bar: the dropdown opens under the bar at the bar's width, Clear works, and the bottom row lifts above the
   keyboard (Robolectric reports no keyboard).

## Gradle

Every run checked `systemctl --user is-active t6b-night` (inactive each time, not touched) and free disk (3.6 GB at the
start, 3.3 GB at the end): `systemd-run --user --scope -p MemoryMax=5G -p MemorySwapMax=0`, `--no-daemon`, Gradle
`-Xmx1536m`, Kotlin daemon 2g, Java temp `~/.cache/forager-test-tmp`, `./gradlew --stop` and the Kotlin daemon stopped after
each scope. No Gradle process left at the end.

## Commits

`dc6047d8` (the layout and its tests), `73e62bb0` (pixel rounding; the L re-fits on the limit), and this report.

## Superseding section, RECORD -732: the join moves to fit the strip

The planner (RECORD -732), with the owner's answer verbatim: "Join moves to fit the strip (Recommended)". Supersedes the
centre join above, its measurements table and its "what the strip shows" paragraph. Kept: the mirroring at 270, the
navigation and fullscreen behaviour, the equal height, the line, the L's re-fit and the rounding fix.

**A correction to the -729 width figures above.** "Adding the heading needs 307.9 / 299.6" counted three separators; the
strip's readout line has two (heading · altitude · coordinates). The right figures are 287.6 dp (MGRS) and 279.3 dp
(decimal) for the readouts, so at -729's centre join the heading was short by 47.6 / 39.3 dp at 780 and 26.1 / 17.8 dp at
823. The conclusion (the heading dropped) stands.

**Rule now** (not navigating, short landscape, Maps tab):
- The strip is as wide as its readout line needs whole, measured as the strip and readoutsFitBeside measure it: the row's
  padding (8 dp each side), the needle (18 dp), the three-dot button (36 dp), and, without labels, the heading, the altitude
  and the coordinates, each after a separator (the dot and 8 dp each side). The heading is measured at its widest value form
  ("000°" and the widest compass point, tabular figures), so the join does not move as the phone turns; while the heading
  has status words instead ("Compass unavailable", "Compass unreliable") their width is allowed for. With no fix, the no-fix
  line. `rememberLandscapeStripNeed` in `AvailabilityMapControlsUi.kt`.
- The bar takes the rest of the room, never narrower than its floor (`rememberLandscapeBarFloorPx`, `AvailabilitySearchUi.kt`):
  search icon start padding 16 + icon 18 + the field's content padding 16 + 16 + its resting text (the search summary, in
  bodyLarge) whole, plus, while a search shows, Clear (max(58 dp, the word + 12 + 12)) + 4. Measured: with a "Hericium
  erinaceus · October · 9 mi" search showing, the floor is **394.33 dp** (the bar's width where it binds, both windows).
  Where it does not bind, the floor is not printed by any test.
- Where both cannot fit, the bar keeps its floor and the strip narrows, and readoutsFitBeside drops from the front; the strip
  never goes below the coordinates alone (`landscapeStripWidthPx`), and there the bar gives way.
- The bar is placed by the strip's measured width, as padding on the rail's side, so the two meet on the same pixel. The line
  is centred on the strip's inner edge. The scaffold falls back to -729's half for the first frame before the strip is measured.

**Measurements** (native graphics, xxhdpi, font 1.0, heading 315° NW, 9843 ft; `LandscapeBarStripJoinTest`):

| Window, rotation | Bar | Strip (width) | Line | Height |
|---|---|---|---|---|
| 780 x 360, 90 | 0 to 342.33 | 342.33 to 700 (357.67) | 341.67 to 342.67 | 36 / 36 |
| 780 x 360, 270 | 437.67 to 780 | 80 to 437.67 (357.67) | 437.33 to 438.33 | 36 / 36 |
| 823 x 384, 90 | 0 to 385.33 | 385.33 to 743 (357.67) | 384.67 to 385.67 | 36 / 36 |
| 823 x 384, 270 | 437.67 to 823 | 80 to 437.67 (357.67) | 437.33 to 438.33 | 36 / 36 |

The strip shows **heading, altitude and coordinates whole** in both windows, both rotations, MGRS and decimal: "315° NW",
"9843 ft", "10T ER 24991 40768" / "45.5200, -122.6800". The bar's resting text "October · Search a location" is whole (342.33
dp bar at 780). With a long search showing (floor binds), the bar is 394.33 dp, the strip 305.67 dp at 780 and 348.67 dp at
823, showing altitude and coordinates (the heading dropped), Clear inside the bar and the summary whole.

**Tests** (`LandscapeBarStripJoinTest`, now 11 per window, 22 in all, 22 pass): the join equals the strip's measured inner edge
(±0.5 dp) and the bar's edge; the line sits on it; equal heights; the three-dot button by real touches; long-presses around
the join; heading, altitude and coordinates whole in both formats; the bar's resting text whole; with a search showing,
summary and Clear whole; with a long search, the floor kept and the heading dropped, coordinates shown. The -729 B2 tests S1
and S2 (two each) now assert the new rule, citing -732: the bar runs from the punch-hole edge to the strip's inner edge.
`LandscapeLargeFontTest` at font 2.0 passes as written (coordinates 18 of 18).

**Revert checks** (same runner; all compiled, all fresh, all restored byte-identical, `git status` clean after):

| # | Edit | Failed | Message |
|---|---|---|---|
| R5 | strip back to half the room (the fit removed) | 780: 4/11; 823: 1/11 | "MGRS: compass-strip-heading is shown" (780); "positive control: the floor bound, so the heading gave way" (823, where half the room, 371.5 dp, is wider than the need) |
| R6 | bar ignores the strip's width | 3/11 each | "the bar's right edge is the strip's inner edge expected 342.33 but was 390.0" (823: 385.33 / 411.67) |
| R7 | bar floor 0 | 1/11 each | "positive control: the floor bound, so the heading gave way ([315° NW, 9843 ft, 10T ER 24991 40768])" |
| R2 | bar keeps its own height | 2/11 each | "the bar is the strip's height expected 36.0 but was 45.0" |
| R3 | no line | 2/11 each | "could not find any node ... 'landscape-bar-strip-line'" |
| R4 | L not re-fitted on the limit | B1 2/18, notice 1/1 | "the L did not move ... expected 45.0 but was 44.0"; "expected 47.0 but was 44.0" |

**Full suite: 4,438 tests in 568 classes, 24 skipped, 3 failures, 0 errors** (JUnit XML; Gradle agrees). The three are
existing tests, not touched:
- `LandscapeLRulingsTest` "B1 at ROTATION_90 / 270 ... a notice makes room and the L does not move": "the notice [112, 36]
  [302, 116] is still wide enough to read (at least 200 dp)" (190 dp).
- `LayoutFixesChipRowLandscapeTest` "T7 with the cluster on the far side the two chips have room and stay on one line": the
  journal chip wraps under the taxon chip.

Cause: these harnesses pass no compass, so the heading reads as a status ("Compass unavailable", inferred from the default
`AndroidCompassProvider` under Robolectric, not printed). Allowing for the status's width makes the strip wider and the bar,
which the notice and the chip row take their width from, narrower (about 302 dp at 823). Confirmed by order: with the status
allowance absent, these B1 tests passed (18 of 18); adding it made B2 S5 pass (its heading node was dropped without it) and
B1 fail. So the choice is between showing a compass status whole and keeping the bar wide enough for a notice beside the L
and for two chips on one line, when the phone reports no usable compass. Options: keep the allowance (now); measure the value
form only, so a status drops first (B2 S5 then fails, the heading status no longer shows in short landscape); or add the
notice's 200 dp beside the L, and the chips, to the bar's floor. For the owner.

Free disk 3.3 GB at the end. `t6b-night` inactive before every run, not touched. `./gradlew --stop` after every scope; no
Gradle process left.

## RECORD -733: short heading words, same room (stopped at the fit check)

The planner (RECORD -733), the owner verbatim: "Short words, same room (Recommended)". Built: in a short landscape window the
heading slot reads "No compass" (no sensor) and "Compass?" (unreliable); portrait keeps "Compass unavailable" and "Compass
unreliable". The strip is sized from the heading's value form only (`landscapeHeadingValueWidthPx`); -732's status-words
allowance is removed. Commit `6e14de5b`.

**The fit check fails: neither short word fits the value's room, at either font.** Measured in the strip's own readout style
(14 sp, tabular figures), xxhdpi, `LandscapeHeadingShortWordsTest`:

| Font | Value room ("000°" + widest point) | "No compass" | "Compass?" |
|---|---|---|---|
| 1.0 | 58.33 dp (175 px) | 84.33 dp (253 px), 26 dp over | 70.33 dp (211 px), 12 dp over |
| 2.0 | 108 dp (324 px) | 155 dp (465 px), 47 dp over | 129.67 dp (389 px), 21.67 dp over |

So with no compass, or an unreliable one, readoutsFitBeside drops the heading slot: altitude and coordinates show, the status
does not. The join stays where it is with a heading (that part holds).

**Full suite at `6e14de5b`: 4,444 tests in 570 classes, 24 skipped, 7 failures, 0 errors** (JUnit XML; Gradle agrees):
- `LandscapeHeadingShortWordsFont1Test`, `...Font2Test` (new): the two rows above.
- `LandscapeBarStripJoin780Test`, `...823Test`, "with no compass or an unreliable one the short words show whole", at 90 and
  270 (new, 4): "No compass: compass-strip-heading is shown", the same cause.
- `AvailabilityScreenLandscapeB2Test` S5: no heading node (no compass in that harness, so the status is dropped).
`LandscapeLRulingsTest` B1 and `LayoutFixesChipRowLandscapeTest` T7 now pass as written.

Not done, because the behaviour they would guard is not in place: the revert check of the short words. Options for the owner:
size the room from the widest of the value and the two words (always 26 dp wider at font 1.0; the bar narrows by that, about
359 dp at 823, which wraps T7's two chips); shorter words (under 58.33 dp at 14 sp, about seven narrow characters); or a
symbol in the heading slot instead of a word.

## RECORD -734 stopped, RECORD -735: the crossed-out compass

**-734 stopped before building.** It asked for "315° NW?" when the compass is unreliable; `TrueHeadingReading.Unreliable`
(`ui/map/TrueHeading.kt:27-44`) carries no heading on purpose ("a heading the app has just said cannot be trusted is not a value
to hand anyone"), and `rememberTrueHeading` resets the smoother then ("Reset, not pause, not carry (owner decision)"). Showing
a value would reverse that ruling. Measured for the decision (probe, removed): the value form plus "?" is 65.67 dp at font 1.0
(121.67 at 2.0), 7.33 dp (13.67) wider than the value.

**-735, the owner verbatim: "Same crossed-out icon (Recommended)".** Commit `93d729ae`. In a short landscape window, no compass
and an unreliable compass both show Material's `Icons.Filled.ExploreOff` (material-icons-extended 1.7.8, already a dependency)
in the heading slot, a square the strip readout's line height: **20 dp at font 1.0, 34 dp at 2.0**. Content descriptions
"Compass unavailable" and "Compass unreliable". No value is carried; `TrueHeading` is untouched. Portrait keeps the words. The
slot stays at the value width, **58.33 dp at font 1.0, 108 dp at 2.0**, so the join is the same in every compass state
(measured: 342.33 dp at 780 / 90, 437.67 at 780 and 823 / 270, 385.33 at 823 / 90, with a heading, with no compass and with an
unreliable one). The -733 short words, `LANDSCAPE_NO_COMPASS_TEXT`, `LANDSCAPE_COMPASS_UNRELIABLE_TEXT` and
`LandscapeHeadingShortWordsTest` are removed.

**Tests.** `LandscapeHeadingIconFitTest` (fonts 1.0 and 2.0): the icon fits the value width (20 ≤ 58.33; 34 ≤ 108). In
`LandscapeBarStripJoinTest` (13 per window), at 780 and 823, both rotations: a heading first (positive control, no icon), then no
compass, then an unreliable one; the icon shows, 20 dp, whole inside the strip, labelled for its state; no heading words;
altitude and coordinates shown; the strip's edges and the bar's end where they were with a heading. `LandscapeLRulingsTest` B1
and `LayoutFixesChipRowLandscapeTest` T7 pass as written.

**Revert checks** (same runner; compiled, fresh, restored byte-identical; `git status` clean; forward lines present after):

| # | Edit | Failed | Message |
|---|---|---|---|
| R8 | words back (`headingIcon = false`) | 2/13 each window | "could not find any node ... 'compass-strip-heading-icon'" |
| R9a | slot sized from "Compass unavailable" | Join 0/13; LRulings 2/18; ChipRow 1/28 | "the notice [112, 36][302, 116] is still wide enough to read (at least 200 dp)"; "the journal chip ... not under it" |
| R9b | slot sized 0 | 6/13 each window | "MGRS: compass-strip-heading is shown" (the heading dropped), and the icon cases' positive control |

R9a passes the join tests, as it should: they hold the join unmoved across compass states, and a slot sized from the words is
also the same in every state. What catches a slot sized wider than the value is B1 and T7.

**Full suite at `93d729ae`: 4,444 tests in 570 classes, 24 skipped, 1 failure, 0 errors** (JUnit XML; Gradle agrees):
`AvailabilityScreenLandscapeB2Test` S5, "in landscape the strip's heading, elevation and coordinate text use tabular figures":
no `compass-strip-heading` node. That harness has no compass, so in landscape the slot now shows the icon (tagged
`compass-strip-heading-icon`), which has no text and no figures. Its claim needs to change: either give S5 a fixed compass so it
checks a heading value's figures (as the portrait half does), or assert the icon in the slot and check figures on elevation and
coordinates only. Not changed here.

`t6b-night` inactive before every run, not touched; `./gradlew --stop` after every scope; no Gradle process left; free disk
3.1 GB.

## S5's fixed compass, and RECORD -736: the strip keeps its portrait height

**S5** (the planner's choice, citing -735), commit `3c984eb2`: the landscape half of `AvailabilityScreenLandscapeB2Test` S5
gets a fixed 315° compass, so it still checks the heading value's tabular figures; its claim is unchanged. **Wrong premise in
that instruction:** the portrait half (`AvailabilityScreenPortraitStripTnumTest`) has no fixed compass; it passes because in
portrait the heading reads "Compass unavailable", text in the same style. Left as it is. Full suite then 4,444, 24 skipped,
0 failures.

**-736**, the owner verbatim: "Yes for landscape I'm willing to accept a shorter search bar. The compass strip must remain the
same height as portrait though and that's important". Commit `8904d8ff`. -729's decided-beyond-scope floor, which grew the
landscape strip to the bar's field height at large fonts, is removed; the strip is now exactly as tall as in portrait. The bar
takes the strip's height; in landscape its field and Clear are at most the height above its divider (`rowFieldHeight`), so at a
large font they are shorter than twice the line instead.

**Measured** (native graphics, xxhdpi, a search showing so Clear shows; `StripHeight*Test` prints them):

| | Strip, portrait | Strip, landscape | Bar | Field | Resting text | Clear |
|---|---|---|---|---|---|---|
| font 1.0 | 36 | 36 | 36 | 32 tall | 16.33 dp line, 14 of 14 shown | 58 x 32 |
| font 2.0 | 37.33 | 37.33 | 37.33 | 36.33 tall (was 64 at -729's floor) | 30.33 dp line, 14 of 14 shown | 86 x 36.33 |

At font 2.0, in both windows and rotations, nothing in the bar is cut: the field's text line (30.33 dp) fits its 36.33 dp, the
summary "October · 9 mi" shows whole, and "Clear" shows whole (its text box fills the button's 36.33 dp height). Clear's touch
target is 86 x 36.33 dp at font 2.0 (58 x 32 at font 1.0), under Material's 48 dp in height in both, as already reported for
-723. The strip at font 2.0 is 37.33 dp, not 36: its readout line is taller than the three-dot button there. Bar widths at font
2.0: 343.67 dp (780 and 823, rotation 90; 823 / 270 likewise), the strip 330.33 dp at 780 and 373.33 at 823.

**Tests** (`StripHeightTest`, file `LandscapeStripPortraitHeightTest.kt`, 10 tests): portrait at fonts 1.0 and 2.0 (the positive
control) and landscape at 780 and 823, both rotations, fonts 1.0 and 2.0: the strip equals its laid-out content height (the
larger of its button and its readout line), and in landscape the bar equals the strip. A first draft computed the expected
line from the 20 sp line height, 36 dp at font 2.0; the portrait control measured 37.33 and failed, which caught it. **Revert
R10** (the strip held at the field's height, `height(max(field + divider, 36 dp))`): 4 failures, font 2.0 only, "the strip is
its portrait height expected 37.33 but was 65.0"; font 1.0 passes, as it should (the floor is under 36 there). Compiled, fresh,
restored byte-identical.

**Full suite at `8904d8ff`: 4,454 tests in 576 classes, 24 skipped, 0 failures, 0 errors** (JUnit XML; Gradle "BUILD
SUCCESSFUL"), S5 with its fixed compass included. `t6b-night` inactive before every run, not touched; `./gradlew --stop` after
every scope; no Gradle process left; free disk 3.0 GB.
