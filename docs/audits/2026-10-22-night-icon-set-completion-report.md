# Night icon set: completion report (dispatch 2026-10-22-06 Amendment 1)

Coder: Hazel (Claude Opus 5.5). Planner: Rowan. Owner's decision: RECORD -1566, option D. The verify-first report
is `docs/audits/2026-10-22-night-icon-set-verify.md`, carried into this branch from `night-icon-set-verify`
(`b8e9300e6`) so the two land together.

PR #380 into pre-main, from branch `night-icon-set`, cut from pre-main `8b6f3572f`. Not merged. Nothing deployed;
nothing on R2; no Worker change.

## What landed

| Commit | What it does |
|---|---|
| `c33bd8946` | Retires the run-time repaint, and adds the night step (`NightImageSwitch.kt`, `NightImageApply.kt`), its tests, the moved night rules, and the committed day sheets. No night sheets yet, so `NightSpriteTest` fails first. |
| `a878d2753` | `NightSpriteTest` reads and writes PNGs itself: unit tests compile against Android's stubs, which have no `javax.imageio` (CI run 39886835336 stopped at that compile error). |
| `89de78796` | The night sheets, copied unchanged from CI run 39887583396's test report. |
| `938f355a6` | The verify-first report, cherry-picked from `b8e9300e6`. |
| this report's commit | This report and the audits index rows for both reports. |

## What changed

**On the phone.**
- In the `setStyle` callback, at night on Trail Map, the app adds 61 night copies to the style as `night/<name>`.
  They come from bundled sheets in `app/src/main/assets/night-sprite/v5`: 1x and @2x, chosen as MapLibre chooses the
  day sprite.
- The app then switches the style's six image references to those copies:
  - the two `fill-pattern` literals (tree, tufts) and `roads_oneway`'s `arrow`, by name;
  - `roads_shields`, `pois` and `places_locality`, by wrapping their expression `E` as
    `["match", E, [names with a night copy], ["concat", "night/", E], E]`.
- No sprite image is called `night/...`, so the sprite's late load replaces nothing. That load comes about 220 ms after
  the callback and replaces same-named images (`style_impl.cpp:374-381`).
- The step reads nothing from the sprite, so the night images no longer depend on when the sprite arrives.
- One log line replaces the old one, for example: `Street night: 61 night images added, 6 image references switched,
  0 left in day images; no night copy missing.` The planner's pass condition reads this line.

**The fallback, decided by the app, not by `coalesce`/`image`.** The amendment asked me to prove that `coalesce` with
`image` falls back on 13.5.0, or stop and report. I did not use `coalesce`, for two reasons:
- It can't be proved here. The S22 is for the launch check only, and the production night set omits no image, so no
  device run can show the fallback path.
- MapLibre Native evaluates a feature-constant image property without the available images. There, `coalesce` returns
  its first requested image even when it is missing. Read at `android-v13.5.0`: `coalesce.cpp:13-29`;
  `image_expression.cpp:63`, where an image counts as available only when the evaluation is given the available
  images; and `pattern_layout.hpp:120-122`, where constant patterns are evaluated at layer level.

How the fallback works instead:
- **Literal names:** the app switches a literal only when it holds a night copy. Otherwise the literal keeps its day
  name, and the log line names it.
- **Expressions:** the `match` labels are exactly the names the app holds. A name without a copy evaluates to itself,
  the day image.
- Both are standard expressions with the same output types as the day ones, and both are unit-tested.
- **What a device must still show:** that MapLibre accepts the switched values. The first night load's log line, and
  the absence of MapLibre "Error setting" lines, show that (device checklist, step 1).

**Retired:**
- `applyStreetSpriteNight` and its "redrawn / left in day colours" line (`StreetStyleApply.kt`).
- `NightPattern.kt`.

**Moved, not changed:** the night rules now live with the tests, in `NightSpriteRules.kt` (test source set):
- `nightPixelsOf`, `nightIconPixelsOf`, `IconRingNight` and `STREET_NIGHT_ICON_RINGS` (out of `NightColour.kt`);
- `nightPatternPixelsOf` and `STREET_NIGHT_PATTERN_COLOURS`;
- `STREET_SPRITE_ICON_NAMES`, now in the test file `StreetSpriteIconNames.kt`.

They run at build time only, where `NightSpriteTest` applies them. The one rule change is the owner's: tufts is
painted `#2485ad` (`STREET_NIGHT_PATTERN_PAINTS`). The design's `forager:night-patterns`, which `NightExceptionsTest`
holds equal to `STREET_NIGHT_PATTERN_COLOURS`, is unchanged.

**The 61 images** follow the verify-first report's table, which the owner's -1566 settled:
- every point icon and road shield inverted;
- the light rings kept on toilets, drinking water and townspot;
- the tree painted `#5d804f`;
- tufts painted `#2485ad`.

**Not touched:**
- the colour-property recolour;
- the served styles and every file under `server/`;
- R2 and the Worker;
- `.github` (the diff against pre-main is empty);
- the offline style choice and the -1294 notice;
- the -1513 fallback;
- the style files under `map-style/`. Only two comments changed there (`build-sprite-v5.mjs`, `generate-street.mjs`);
  the generated JSON is not changed.

**Sizes:**
- New main files: `NightImageSwitch.kt` 168 lines, `NightImageApply.kt` 101.
- New test files: `NightImageSwitchTest.kt` 203, `RgbaPng.kt` 133, `NightSpriteTest.kt` 113, `NightSpriteRules.kt` 108.
- Touched files shrank: `NightColour.kt` 352 to 296, `StreetStyleApply.kt` 109 to 61.

## How the night sheets were made, and checked twice

**Made:** `NightSpriteTest` applies the rules to the committed day sheets (`app/src/test/resources/sprite-v5-day`). When
a committed night sheet is missing or differs, it writes the sheets the rules give into the CI test report, and fails.

**Checked against R2:** the day copies' sha256 equal Aspen's sums for R2 (RECORD -1564; Forager-cartography
`docs/audits/2026-10-22-sprite-v5-upload.md`). The live files gave the same sums on 2026-10-22, and a test holds them.

**First run:** run 39887583396 failed for the expected reason only: `light: no committed night sheet, light@2x: no
committed night sheet`. Its generated files were committed unchanged. Run 39888254674 then passes the test.

**Second, independent check:** because the reader and writer are this repository's own small PNG codec, the committed
sheets were also checked outside the build:
- decoded with PIL and compared with a Python port of the rules over the PIL-decoded day sheets;
- all 61 images matched at 1x (28,900 pixels) and @2x (115,600 pixels);
- a render of both sheets on the night forest colour looked as the table says. It stayed off GitHub.

## Evidence

**Unit tests.** Run [39888254674](https://github.com/slayer8366/Forager/actions/runs/39888254674) on the PR's merge
commit, against pre-main's run 39880409429 at `8b6f3572f`, compared by test ID:

| | Before | After |
|---|---|---|
| App unit tests | 5272, 0 failures, 24 skipped | 5282, 0 failures, 24 skipped (the same 24 IDs) |

- **Ten new tests:**
  - `NightImageSwitchTest`, 7: the bug test below; v2's six switches; v1's switches; a missing copy; no sheets at all;
    a layer the live style lacks; unreadable JSON.
  - `NightSpriteTest`, 3: the R2 sums; pixel equality at 1x and @2x; the 61-name set.
- **One test reworked:** `NightExceptionsTest`'s pattern test now holds tufts painted, including an off-colour edge
  pixel. No other ID changed.
- **Other jobs, all green:** lint, the release compile, the Worker, and the route cutter.

**The test that would have caught 2026-10-22-05.** `NightImageSwitchTest#night images survive the sprite arriving
after the style callback, and every reference draws its night copy`:
- It runs the night step against a fake style that holds no sprite image at the callback, which the S22 showed
  (`nonNull=0/61`).
- It then loads the sprite after the callback, replacing images of the same name, as MapLibre Native does.
- It asks what 13 sample references draw (tree, tufts, arrow, two shields, six point icons, capital, townspot), and
  asserts the night copy for each, and the log line "61 added, 6 switched, 0 left".
- R1 below shows it failing when the night copies take the sprite's own names, which is the overwrite that sank C.

**Revert checks.**
- Each was a one-line edit on its own branch `night-icon-set-revert-N`, cut from `89de78796`, never merged. Each ran on
  CI with the filter `*NightSpriteTest;*NightImageSwitchTest;*NightExceptionsTest` (22 tests).
- **Each run built from fresh:** the build log has no `e:` compile errors, and both "Build the debug APK" and the test
  compile ran. The results are each run's own artifact on a fresh runner, so none is stale.
- **The forward branch was never edited:** each revert lives on its own branch. `origin/night-icon-set` still holds
  the prefixed `nightImageName` (checked with `git grep`).

| # | Edit | Run | Failed with (only these) |
|---|---|---|---|
| R1 | `nightImageName` returns the day name | 39888341271 | the bug test, where references draw the sprite's day images; v2 switch: `Name(night/tree_dark_irregular)` was `Name(tree_dark_irregular)`; missing copy: `night pixels of peak` was `null` |
| R2 | references never switched (the set call replaced by `true`) | 39888343465 | the bug test; a missing layer: switched `5` was `6`; missing copy: `[night] pixels of peak` was `[day]` |
| R3 | tufts inverted, not painted | 39888346098 | `NightSpriteTest`: pixels differ from the rules for `[tufts]` in both sheets; `NightExceptionsTest`: painted patterns `[tree_dark_irregular, tufts]` was `[tree_dark_irregular]` |
| R4 | the light rings dropped | 39888350457 | `NightSpriteTest`: pixels differ for `[drinking_water, toilets, townspot]` in both sheets |
| R5 | a literal switched without a night copy | 39888353229 | missing copy: switched `5` was `6`; no sheets: switched `0` was `3` |

**A correction to the verify-first report.** It said painting tufts changes nothing at @2x. That is true of every
visible pixel: 156 of 156 at @2x. But R3 also failed at @2x. The difference is in fully transparent pixels: painting
leaves them as they are, while the inversion changes their (invisible) colour. Nothing on screen differs at @2x; the
sheet's bytes do.

**APK size.** The debug APK goes from 82,862,475 bytes (pre-main run 39880409429) to 82,918,391 (run 39888254674): up
55,916 bytes. The four bundled files are 66,137 bytes uncompressed: the PNGs 20,845 and 32,536, the indexes 6,361 and
6,395.

**The dex report** (`scripts/verify-dex-register-budget.sh`, report only), run on both APKs: the same five methods with
the same register counts. This change adds nothing there.

**The `.github` diff is empty.**

## The S22 launch check: not run

**Not run.** When the APK was ready, the S22 was no longer attached: `adb devices` listed nothing, twice. The launch
check and device step 1 below are open.

**Already checked for the install:**
- The PR's APK is versionCode 3828 (`1.0.3828+g711285bf`).
- The phone held 3823 at schema 22 when last read. This branch is still schema 22 (`ForagerDatabase` unchanged), so
  the install is an upgrade with no schema change.
- The S22 is to go back to the 1.0.3823 APK from run 39869410122 afterwards, as before. That is a version-code
  downgrade, with `install -r -d`, at the same schema.

## Not verified

- **On a device, nothing yet:** the launch check, the log line, and that MapLibre accepts the switched `match`
  expressions and night names.
  - Expectation: they are of the same kind as the day style's own expressions, which use `match` and `concat` outputs
    in `icon-image`. The colour recolour already sets `Expression.raw` values on this phone.
  - That is inference until the line is read.
- **The day-image fallback on a device:** it cannot be exercised with the production night set, which omits nothing.
  It is unit-tested at the expression level only.
- **No light frame at night:** by construction the night copies are in the style from the callback on. The owner's eye
  is the check (device step 2).
- **The 1x sheet:** chosen only on screens with density 1 or below. No such device has been tried.

## Device checklist (the night check)

Steps 1 and 3 need the S22 attached; step 2 is the owner's eye. The pass is the log line in step 1, plus the owner
seeing dark icons and shields at night.

1. **The log line (cheap, a log read).**
   - Install the PR's APK with `scripts/s22-launch-check.sh`, with Trail selected and Night Maps on (the S22's current
     settings), and open the map.
   - **Pass:** logcat tag `SightingsMap` shows `Street night: 61 night images added, 6 image references switched, 0 left
     in day images; no night copy missing.`, and logcat shows no MapLibre `Error setting` line for `icon-image` or
     `fill-pattern`.
   - **Fail:** any other count, or an error line naming one of the six layers. Either means MapLibre refused a switched
     value; capture the line.
2. **What the owner sees (the gate).** Forest Park around zoom 13, Trail, Night Maps on.
   - Parking, peak, viewpoint, trailhead, campsite and picnic icons are dark discs with light marks.
   - The highway shields are dark with light text.
   - Toilets and drinking water keep a light ring.
   - The tree marks are a muted green (`#5d804f`) on the dark forest, and the wetland tufts are blue.
   - **No light flash** while the map draws, including when Night Maps is switched off and on.
   - **Evidence:** the owner's word and a screenshot kept off GitHub.
3. **Day is unchanged.** Night Maps off: every icon and pattern as before. There is no "Street night" line for that
   load (the step runs only at night).

## Amendment 2: Sorrel's H1, M1, L1 and M2 (added 2026-10-23 UTC)

This section supersedes the parts of the report above that it names. Nothing above is edited.

**What was wrong.** Sorrel's review is `docs/audits/2026-10-22-review-pr380.md`, at `73630eb15`. It found that
MapLibre Native refused the switch's `places_locality` value. The S22 confirmed it on 1.0.3828:
`E/Mbgl: [JNI]: Error setting property: icon-image Only one zoom-based "step" or "interpolate" subexpression may be
used in an expression.`

In the same load the app's line still said "6 image references switched". So two claims above were wrong:
- **"Six references switched" was wrong for one of the six.** Place dots and capitals stayed in day colours below
  zoom 8 (H1).
- **The log line was not evidence.** It counted calls made, not values that took (M1). Step 1 of the checklist above
  could pass with H1 present.

The owner's S22 run of steps 1 to 7 on 1.0.3828 was otherwise clear: icons, shields, rings, tree marks, tufts, no
flash, and day unchanged (relayed by Rowan).

**What changed:**
- **H1 and L1.** The switch no longer wraps a value whole. It walks into the value's `match`, `case` and `step` outputs
  (`IMAGE_OUTPUT_OPERATORS`, `NightImageSwitch.kt`) and leaves every operator, input, label and zoom stop as the day
  style has them. Only the image names those outputs give are changed:
  - a literal output with a night copy takes the copy's name;
  - a computed output `E` becomes `["match", E, names, ["concat", "night/", E], E]`.
- **What that means for each reference:**
  - `places_locality` keeps its zoom `step` on top: `["step", ["zoom"], ["case", …, "night/capital", "night/townspot"],
    8, ""]`.
  - `pois`'s `["get", "kind"]` stays in an image position (the wrapper's `otherwise`). It keeps the day style's image
    coercion, which is L1. I kept the coercion rather than arguing it is not needed.
  - The shields' three `concat` outputs are each wrapped.
- **M1.** `NightImageTarget.readImageReference` reads each property back after setting it. On the phone that is
  `SymbolLayer.iconImage` or `FillLayer.fillPattern`. A reference counts as switched only if the value read back holds
  the night prefix; otherwise it is counted and named as refused. The log line now reads:
  `Street night: 61 night images added, 6 image references switched, 0 refused by MapLibre, 0 left in day images; no
  night copy missing.`
- **Missing night copies are named in sorted order,** so the line does not depend on the style's layer order. Run
  39892109877 failed one test on order alone; that is fixed in `fd08a531d`.

**Commits:**

| Commit | What it does |
|---|---|
| `19c5b2fee` | The new tests, failing first. The read-back method and the refused count are scaffolded but not yet used. The test fake now refuses a value that breaks the zoom rule, as MapLibre did. |
| `c87496f4b` | The fix: walk the outputs (H1, L1), and count only what reads back (M1). |
| `fd08a531d` | Missing names sorted. |

**Failing first.** Run 39891668441 on `19c5b2fee`, filtered to the three night classes. It had 6 failures of 26, no
compile errors, and each failure was the case it names:
- refused counted (`5` expected, `6`);
- the zoom rule ("v1 places_locality … zoom used under a top-level match");
- the structure (`["step", …]` expected, `["match", ["step", …]` given);
- capital drawn by day, in the bug test, the v1 test and the missing-copy test.

**Tests added.** All in `NightImageSwitchTest`, with a small expression evaluator in `StyleExpressionEval.kt`. The
tests now ask what a reference draws for a feature and zoom, not how its JSON is spelt.

| Test | What it holds |
|---|---|
| Every switched value keeps zoom as the input of a top-level step only, at v1 and v2 | Sorrel's parser-rule test (`zoomRuleViolations`, after `find_zoom_curve.cpp`). |
| The switch changes only the day expression's image outputs | The exact night values for `places_locality`, `pois` and `roads_shields`. |
| A value MapLibre refuses is counted as refused and named, never as switched | M1. |
| Places draw no icon from zoom 8 at night, as by day | Passes before and after the fix, so it guards the zoom-8 output only. It is not evidence for H1. |

Two existing tests were renamed and reworked, and the bug test now samples 14 features, including both place outputs.

**Revert checks.**
- Each was one line in `NightImageSwitch.kt`, on its own branch cut from `fd08a531d`, never merged, and run with the
  three night classes (26 tests).
- Each run built clean: "Build the debug APK" succeeded, there are 0 `e:` lines, and each run's own artifact was read.
- `origin/night-icon-set` still holds both fixed lines (checked with `git grep`).

| # | Edit | Run | Failed with (only these) |
|---|---|---|---|
| R6b | `step` dropped from `IMAGE_OUTPUT_OPERATORS` | 39895598132 | zoom rule (v1 `places_locality` under a top-level match); structure (`step` expected, `match` given); refused 1 in the bug test and the v1 test; missing-copy switched `5` was `4` |
| R7b | `match` dropped from `IMAGE_OUTPUT_OPERATORS` (`pois` and shields wrapped whole: L1) | 39895600274 | structure only: `pois` expected with its own `match` on `kind` |
| R8b | the read-back check replaced by `switched++` | 39895603297 | refused only: switched `5` expected, `6` |

R6 to R8 first ran on `c87496f4b` (runs 39892159712, 39892162553 and 39892164903). R7's and R8's runs then also failed
the missing-copy test, on the order problem `fd08a531d` fixed, so those failures were not specific to their edits. All
three were rerun as R6b to R8b on `fd08a531d`, and only the reruns are cited.

**Suite.** Run 39895576482 on `fd08a531d`, the PR's merge commit, all jobs green: 5286 tests, 0 failures, 24 skipped.
These are the same 24 IDs as pre-main's run 39880409429, which had 5272. The debug APK is 82,918,391 bytes, still
+55,916 over pre-main.

**S22 launch check** (1.0.3833+g90a91c5f, an upgrade from 3828; schema 22 on both):
- `scripts/s22-launch-check.sh` PASS: verified, launched, process alive after 15 s, crash buffer empty.
- The phone was dozing when it launched. After a wake key the Maps tab loaded Trail Map.
- **Night Maps was off on the phone** (the owner's last step was "day unchanged"), so no night load ran. There is no
  `Street night` line, and no `Error setting` line in the full logcat of the day load.
- The night check on this build is the owner's (below). The S22 is left on 1.0.3833 for it.

### Device checklist, widened (M2)

This replaces the checklist above. Steps 1 to 7 of the owner's run on 3828 hold, except for the place dots. The pass
is step 1's line, plus step 3's dark place dots, plus the owner's eye on steps 2, 4 and 5.

1. **Full logcat read on a night load.**
   - Clear the log (`adb logcat -c`), turn Night Maps on, and save `adb logcat -d` unfiltered.
   - **Pass:** tag `SightingsMap` has `Street night: 61 night images added, 6 image references switched, 0 refused by
     MapLibre, 0 left in day images; no night copy missing.`, and the whole log has no `Error setting property` line.
   - **Fail:** any refused reference is now named in the app's own line, as well as in MapLibre's `E/Mbgl` line.
2. **Forest Park, about zoom 13** (as before): point icons and shields dark, rings light, tree marks `#5d804f`, tufts
   blue, no light flash.
3. **Place dots below zoom 8** (H1).
   - Zoom out past 8 at night.
   - **Pass:** the town dots and capital markers are dark with light centres, not white.
   - Above 8 there are none, by day or by night.
4. **Offline night, with a v2 region and no signal.**
   - Put the phone in aeroplane mode, inside a downloaded Trail Map region (v2; a v1 one too, if any S22 region still
     holds v1), with Night Maps on.
   - **Pass:** the map draws, step 1's line appears, and the icons are dark.
5. **Topographical night over Trail Map.**
   - Topographical selected, Night Maps on, about zoom 9 to 9.5, where Trail Map shows under the topo.
   - **Pass:** step 1's line, with dark icons and shields.
6. **Day unchanged.** Night Maps off: no `Street night` line, and the icons as before.

### Not verified (added)

- **Everything at night on 1.0.3833:** the read-back on the phone, the place dots, offline and Topographical night.
  This is all pending the owner's steps above.
  - What `SymbolLayer.iconImage` returns as text after a set (an expression's `toString`, or a plain name) is read
    here only for the `night/` prefix. If MapLibre were ever to rename an image in that text, the line would say
    "refused" for a value that took, which is the safe direction.
- **L1:** the kept coercion matches the day style by construction. The two typings were never compared on a device.

## Amendment 3: the chosen chip grey while offline (added 2026-10-23 UTC)

**Background.** The owner's S22 night check passed on 1.0.3833:
- "6 switched, 0 refused" online;
- "4 switched, 0 refused" offline, where the S22's regions hold v1;
- no "Error setting" line;
- dark place dots, Topographical night right, and the aeroplane test right.

The owner then asked, with a screenshot of the Topo chip filled while offline: "Have the toggle color be grey to reflect
it's chosen, but offline."

**What changed.** The Layers sheet's "Map type" row moves to its own file, `MapModeChips.kt`. It moves unchanged, plus a
test tag per chip and a record of each chip's selected fill. While the no-signal rule draws Trail Map for a Topo or OSM
user, the chosen chip stays selected and is filled `colorScheme.outlineVariant`:
- light: `#D5CEBF`, in place of `secondaryContainer` `#F6DFC8`;
- dark: `#444444`, in place of `#6B421A`.

The label stays `onSurface`.

**Rules held:**
- The state is `LocalNoSignal`, after its hysteresis: the one the map reads. The chip turns grey when the map switches
  to Trail and back when the chosen map returns.
- The stored choice never changes.
- A tap is still a real choice.
- The Trail chip is not highlighted while Trail Map stands in.
- A Trail user's chip is unchanged.

**Why `outlineVariant`.** Contrast was measured against the sheet's own colour, `surfaceContainer` (`#F4EFE2` light,
`#202020` dark):

| | Light | Dark |
|---|---|---|
| Grey fill off the sheet | 1.36:1 | 1.67:1 |
| Normal selected fill off the sheet | 1.12:1 | 1.88:1 |
| Label on the grey | 8.4:1 | 7.7:1 |

- An unselected chip has no fill, only its outline, so a grey fill with no outline still reads as selected.
- Material's disabled colours were rejected: they dim the label to 38%, which reads as "can't tap".
- `surfaceContainerHighest` (1.19:1 and 1.37:1) and `surfaceVariant` (1.11:1 and 1.17:1) stand off the sheet less, and
  `surfaceVariant` is Cream in light, not grey.

**Commits:**
- `b64b89c7a`: the move, the tags, and the test, with the grey condition still `false`.
- `f67877619`: the condition wired.

**Failing first.** Run 39904490932, filtered to `MapLayersSheetOfflineChipTest`, `MapLayersSheetTest` and
`MapModeTest` (29 tests). No compile errors; 2 failures, both on the grey step: the drawn pixel was `#6b421a` (the normal
selected fill) where `#444444` was expected, dark theme, no signal. The signal-on steps passed, so the pixel read itself
works.

**The test.** `MapLayersSheetOfflineChipTest`, Robolectric with native graphics, 4 tests:
- It composes the sheet inside `ForagerThemeWithNoSignal`, the app's own root, with a no-signal flow.
- It reads each chip's **drawn** fill, a pixel 3 dp in from the chip's edge, alongside the chip's record of its fill.
- In the dark and the light theme, Topo chosen: normal with signal; selected and grey without; Trail not selected;
  normal again when the signal returns. No choice is written throughout.
- OSM chosen and offline: grey.
- Trail chosen and offline: unchanged.
- A tap while offline is a real choice. That is a semantic click, since the claim is wiring.

**Revert checks.** Each was a one-line edit to `MapModeChips.kt` on its own branch from `f67877619`, never merged, with
the same filter. No compile errors, and "Build the debug APK" succeeded.

| # | Edit | Run | Failed with (only these) |
|---|---|---|---|
| R9 | the Trail exclusion dropped (`noSignal` alone) | 39905102839 | Trail chosen, offline: drawn `#444444` where `#6b421a` |
| R10 | the condition back to `false` | 39905104988 | Topo and OSM offline: drawn `#6b421a` where `#444444` |

**Suite.** Run 39905056952 on `f67877619`, the PR's merge commit, all jobs green: 5290 tests, 0 failures, the same 24
skipped IDs. Before this amendment there were 5286; the 4 are the new class. The debug APK is 82,918,387 bytes.

**S22 launch check.** 1.0.3837+gbbbcb4d0, an upgrade from 3833; schema 22 on both.
- `scripts/s22-launch-check.sh` PASS. The full logcat holds no `Error setting` line.
- The phone was dozing, so no map was drawn, and the grey chip needs aeroplane mode, so it is the owner's (below).
- The S22 is left on 1.0.3837.

**Device step (the owner's):**
1. Choose Topo in Layers, with signal. **Pass:** Topo is in its normal selected colour.
2. Turn on aeroplane mode and wait about 5 s, until the map shows Trail Map. Open Layers. **Pass:** Topo is still
   selected and filled grey, and Trail is not highlighted.
3. Turn aeroplane mode off and wait about 30 s, until the map is Topo again. Open Layers. **Pass:** Topo is in its
   normal colour.
4. Optionally, the same with Night Maps on (the dark theme) and with OSM chosen. Trail chosen: unchanged throughout.

**Not verified, and not ruled.**
- The grey chip on the phone, which is the step above.
- The "Use offline tiles" setting, when it is on with signal, also draws Trail Map from the downloaded areas for a Topo
  or OSM user (`drawsFromDownloads`). The amendment rules only the no-signal case, so the chip stays in its normal colour
  there. Whether it should also be grey there is the owner's question; I did not decide it.
