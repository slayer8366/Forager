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
