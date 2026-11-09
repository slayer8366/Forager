# sundown-line: the sunset line in the strip and the HUD (T3), and Settings > Sundown (T4)

Dispatch 2026-09-28-592 (RECORD -592, `prompts/preserved/2026-10-06-11.md` on `records-after-173`),
with Amendment 1 (RECORD -593), Amendment 2 (RECORD -595) and the no-position ruling (RECORD -596).
Branch `sundown-line`, cut from `origin/main` at `ef269025` (still `origin/main` when this was
finished: `git merge-base HEAD origin/main` is `ef269025`, and `ef269025..origin/main` is empty).
Not merged; no pull request opened. Laptop coder, no phone, no adb.

**Three sessions.** The first built the code and was killed by a machine restart after pushing
`ea722ff7`; it left no report and no recorded red runs. The second added `33c6e322` and `59c4dd85`,
ran revert checks and drafted this report; it was killed by an out-of-memory restart (a 3.9 GB
Python job ran beside its Gradle suite), and the planner committed its draft as found (`c00ca917`).
The third (this one) treated the draft's claims as unverified: it re-ran every revert check with a
new runner, re-ran the alert-moment guards against `main`, ran the full suite on the final head, and
rewrote the sections below that depend on those runs. **Every revert result and count in this report
is from the third session's runs**; the second session's revert table is superseded, not merged in
(see "Revert checks"). Statements kept from the draft that this session did not re-derive are marked
as the draft's.

## Commits

| Commit | What |
|---|---|
| `a9ca61fc` | Nine tests that pin when each sundown alert fires (`SundownWatchAlertMomentsTest`). Re-run by this session against `main` `ef269025`: 9 tests, 0 failures (see Tests). |
| `ea722ff7` | The line, the settings, both amendments and -596. |
| `33c6e322` | Strip tests in landscape and fullscreen, and three repairs found when reading `ea722ff7` (see below). |
| `59c4dd85` | The strip tests read the laid-out line, not only its text; the 24-hour clock test can now fail. |
| `c00ca917` | The second session's report draft, committed by the planner as found. |
| `b55f3c80` | This session's revert table, replacing the draft's. |
| `ee5c44c9` | Two existing settings tests scroll to the explanation they assert (see Full suite). No app code changed by this session. |
| this report's last commit | The suite counts and the two index rows. |

## The uncommitted line the second session found (the draft's account, not re-verified)

The draft records that the worktree, as the first session left it, had one uncommitted line:
`.clickable {}` on the strip's sundown line in `AvailabilityMapControlsUi.kt`, a revert probe for
the touch test, and that `git diff` showed it as the only difference from `ea722ff7`, so restoring
that file from git lost no forward work. This session cannot see that worktree state any more and
did not verify it. It matters only for whether anything of the first session's was lost; the code at
`ea722ff7` is what every check below runs against, and the touch behaviour is re-checked here (rA).

## What the walker sees

App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

- **While recording**, the map's top strip has a second line under the compass readout:
  - "Sunset 6:42 PM · start back by 5:32" when the walk back is known.
  - "Sunset 6:42 PM · in 1 h 12 min · dark 7:13" when it is not.
  - "Sunset 6:42 PM · start back was 5:32" once that time has passed.
  - "Sun set 6:42 PM · dark in 21 min" after sunset.
  - "Dark since 7:13 PM" after dark, including a recording started after dark. It never counts down to tomorrow.
- **Hidden** until 2 h 30 min before sunset, or until the start-back time is less than 1 hour away, whichever comes first (-595).
- **Hidden** with no position (-596). "Sunset: finding your position…" never shows.
- **Return or Navigate tapped**: the strip goes, as it already did, and the line is the HUD's last row.
- **Navigating to a waypoint with no recording**: the HUD shows "Sunset 6:42 PM · in 1 h 12 min · dark 7:13". It is computed on screen and never has a start-back time (-593).
- **Settings > Sundown**:
  - A "Sundown alerts" checkbox row, on by default. Off stops the notifications only; the line stays.
  - "Dark under trees" with "Woods get dark before sunset. Alerts allow this much extra.", and radio rows for 30 min / 45 min / 1 h (default) / 1 h 30.
  - Changing the margin moves the start-back time on screen at once.
  - Both settings survive a recreated repository.
- **Times** follow the phone's 12/24-hour setting.
  - The first time on the line uses `DateFormat.getTimeFormat`, as the alerts do (`alert/AndroidAlertDelivery.kt:273`).
  - In 12-hour, later times drop AM/PM ("start back by 5:32"), as the owner wrote them.

## The verify items, answered from the code at `59c4dd85`

1. **Base.** `origin/main` is `ef269025`, the merge-base of `sundown-line`. Nothing has landed since.
2. **One source for the start-back time.**
   - `sundownLeaveByAt` (`domain/SundownLine.kt:66-67`) returns the turnaround minus the walk back, or the turnaround alone when the walk back is unknown.
   - The turnaround is sunset minus the margin (`domain/ComputeSundownCountdownUseCase.kt:67`).
   - The alert decision calls it at `domain/DecideSundownAlertUseCase.kt:72`. The watch's line calls it at `domain/SundownWatch.kt:304`, from the same tick and the same countdown.
   - The screen reads the watch's `shown` StateFlow. The service declares no `android:process` (none in `AndroidManifest.xml`), so the screen and the service share one process. `MainActivity` hands the flow to `TrackRecordingViewModel` (`sundownShown`), which copies it into `TrackRecordingUiState.sundownLine` for its own recording only.
   - One step re-derives instead of reading a tick. `SundownWatch.onMarginChanged` (`:158-162`) recomputes the turnaround as sunset minus the new margin, then calls the same `sundownLeaveByAt`. It decides and delivers nothing. This is the planner's acceptance in -593 ("A display-only watch method that re-derives the start-back time on a margin change with the same function"). It repeats the countdown's `sunset − margin` arithmetic in one line, and the report states that rather than calling it zero second computations.
   - The ViewModel's own unrendered countdown is removed.
3. **When "walk back known" is true.**
   - `SundownWatch.kt:254-257`: a `ReturnWalkingTime.Estimate` gives `About` or `AtLeast`. `Withheld` or no estimate gives `Unknown`.
   - The line has a start-back time exactly when the walk back is not `Unknown` (`:303-304`). The alerts read `walkBack.millisOrNull` from the same value (`:264`).
   - The walk back reads only a GPS fix, so a last known position gives a sunset and no start-back time. This is tested.
   - An "at least" walk back reads "start back by", per -593's "Just "start back by 5:32"".
4. **Civil dusk.**
   - `ComputeSundownCountdownUseCase.kt:55-66` finds the next −6° crossing after sunset.
   - After sunset, the countdown always looks for the *next* sunset. So the line:
     - takes a sunset the watch is holding, with its own dusk. This is the fix in `SundownWatch.kt:227`: the held copy used to keep tomorrow's dusk.
     - or searches backwards for today's sunset (`SunCrossing.previousDescendingCrossing`) and finds its dusk forwards (`civilDuskAfter`, `SundownLine.kt:114-122`).
   - A recording started between sunset and dark reads "Sun set … · dark in …". One started after dark reads "Dark since …".
5. **Strip and HUD.** See "Strip layouts" below.
6. **Settings.**
   - The section sits after Night Mode and before Photo Location (`ui/availability/AvailabilitySettingsUi.kt`, `SettingsContent`).
   - "Sundown alerts" is a checkbox row, the shape of that screen's other on/off settings. The owner said "switch". The planner chose the screen's own control and told the owner (-593).
   - The margin is a radio group, the shape `DistanceUnitSection` and `ThemeModeSection` use.
7. **Help.** There is no Help screen. The owner ruled on the horizon sentence: "Leave it out for now" (-593). It is not in this build.
8. **12/24-hour.** See "What the walker sees".
9. **Stops.**
   - None found that the build crosses.
   - "start back by" with no walk back cannot happen: the start-back time is `null` there and the words switch to the countdown.
   - Alert moments are pinned (see the revert checks below).

## Owner rulings this build follows, quoted

- Placement, -421: "Strip, then HUD (Recommended)".
- -592 (asked first: "Yes, confirm T3 and T4 with me"):
  - "Add "start back by" (Recommended)".
  - "30 min, 45 min, 1 h, 1 h 30 (Recommended)".
  - "Yes, write it (Recommended)".
- -593:
  - The watch change: "Yes, allow it (Recommended)".
  - Navigating without recording: "Sunset and dark only (Recommended)".
  - The horizon sentence: "Leave it out for now".
  - The "at least" walk back: "Just "start back by 5:32"".
- -595, the owner unprompted: "For sundown alerts, it wouldn't be useful in the morning or early noon so while navigating and tracking, those alerts can be clutter on the UI. Maybe have sundown alerts appear ~4 hours prior to sundown." The option chosen: "Yes; also show if start-back is near" ("The line appears 4 hours before sunset, or earlier if the start-back time is less than 1 hour away, so it's never hidden when it matters"), with: "Option 1, but change it from 4 hours to 2.5 hours before sunset".
- -596: "Yes hide it before a position is known".
- Gradle: "Go ahead and build it." This reached the second session from the planner; it was not read at its source. This session was told by the planner "The owner's Gradle go stands", also not read at its source.

Two points need the owner's word:

- **Polar night.** The path does not cover it. The build writes "Sun stays down today" (`ui/availability/SundownLineText.kt`) and shows it whatever the window. Polar day, which has no sunset to approach, shows no line.
- **Checkbox, not switch** (above). The planner told the owner; no answer is recorded.

## Strip layouts, and the 80% map-chrome rule

- **Read.**
  - The strip has one call site, `CompactMapTab` (`ui/availability/AvailabilityCompactMapUi.kt:885`).
  - **Portrait**: full width under the search bar (`railPortEdge == null`).
  - **Landscape**: content-width in the top corner on the rail side (`contentWidth = true`, `:900-903`).
  - **Fullscreen**: the same strip. Nothing gates it on fullscreen, and it follows `topInset` up as the search bar slides away.
  - It is removed while navigating, when the HUD (`:994`) takes the line.
- **Placed.**
  - The line is a `Text` in a `Column` under the readout row, inside the strip's existing `Box` (`ui/availability/AvailabilityMapControlsUi.kt:427-575`).
  - In portrait and fullscreen it fills the strip's width. In landscape it is content-width, so the strip grows to fit the line when the line is wider than the readout. That is visible only on a device.
  - In the HUD it is the last row inside the HUD's existing `Box` (`ui/availability/NavigationHud.kt:360-368`).
- **80%.**
  - No new surface. The line draws no background.
  - It sits on the strip's fill (`MapIconStackButtonColorDark`/`Light`, `ui/map/MapChrome.kt:253,265`, both at `MAP_CHROME_OVER_MAP_ALPHA`) or on the HUD's fill (the same colours, `NavigationHud.kt:254-259`).
  - The text colour is opaque, as on the rest of the strip.
- **Touches.** The strip stays a plain `Box` with a background. The `Column` and the `Text` take no pointer input.
- **Tests.** Portrait, fullscreen and landscape each have a test that:
  - finds the line inside the strip;
  - checks it is laid out whole: one line, not ellipsized, every character visible, under native graphics;
  - samples real long-presses across the line's bounds and in a row under the strip, and asserts each one reaches the map.

  Landscape also asserts the strip is content-width.

**Two traps in reading this, both found while doing it:**

- **Legacy graphics measure text as one pixel per character.** The first landscape run reported the line 12.33 dp wide and cut off. That is 37 px for a 37-character line: Robolectric's legacy graphics measure one character as one pixel. Under native graphics it is 216 dp with all 36 characters visible. A content-width line can only be checked under native graphics. Under legacy graphics no cap could cut it off, so the check could not fail there.
- **`hasVisualOverflow` reports a whole line as overflowing.** Under native graphics it said the landscape line overflowed, with every character visible and nothing ellipsized: the paragraph is a fraction of a pixel wider than the whole pixels it is given. The check reads the line's ellipsis and visible end instead.

## Tests

- **Red first.** No red run of `ea722ff7`'s tests is recorded anywhere: that commit carries tests and
  code together, and the first session's runs, if any, were not reported.
  - Running those tests against `main`'s code would not compile (`SundownLine`, `SundownShown` and the
    rest do not exist there), so that would show nothing.
  - Instead, the red half of each behaviour is shown by a revert check against the built code. Each
    one fails a named test with a message specific to its edit (below).
- **The alert-moment guards, re-run on `main`.** `SundownWatchAlertMomentsTest` from `a9ca61fc`
  copied into a detached worktree at `ef269025` (no other change), XML deleted first, `--rerun`:
  0 `e:` lines, 1 fresh XML, **9 tests, 0 failures**; the file was then removed and that worktree's
  status is clean. They are green at the final head too (forward run below), and rQ shows four of
  them fail when the alerts-off gate is removed, so they can fail.
- **Added.** 67 `@Test` annotations: `git grep -c @Test` over `app/src/test` sums to 3,618 at
  `ef269025` and 3,685 at the head. That count includes the tests in `TrackRecordingSundownTest`
  that replaced the retired countdown's.
- **Added by the second session** (`33c6e322`, `59c4dd85`, from their commit messages): two strip
  tests (landscape, fullscreen), the laid-out checks, and a 24-hour clock test that can fail.
- **Fixed by the second session** (from `33c6e322` and `59c4dd85`):
  - The 24-hour test used 18:42 UTC, which is 11:42 in this laptop's zone, where "h:mm" and 24-hour
    agree; the commit message says a revert of the 24-hour branch left it green. It now uses 18:42 in
    the JVM's own zone. This session did not re-run the old version; rR below shows the current one
    failing on that revert.
  - Three test strings wrote `${'$'}` where they meant `$`.
  - `ThemeModeSection`'s KDoc had been left orphaned above the new Sundown block. It is back above
    its function.
- No `@Ignore` was added (`git diff ef269025 HEAD -- app/src/test` adds none; 6 files carry one at both
  commits), and no assertion was weakened. This session changed no app code; its one test change is
  under Full suite.

## Revert checks

**The runner** (third session; `runner.py` in the session scratchpad, not committed). For each check
it:

1. copies every file it will edit to a saved folder and records each file's sha256, `HEAD`,
   `git status --porcelain` and a hash of `git diff HEAD`;
2. applies the edit only if its target text occurs exactly once, and prints the resulting `git diff`;
3. deletes every JUnit XML in `app/build/test-results/testDebugUnitTest`, then runs
   `./gradlew :app:testDebugUnitTest --tests '*Sundown*' --tests '*SunCrossing*' --rerun`
   (14 classes, 133 tests);
4. counts lines starting `e: ` in the build log, and marks the run not citable if there are any;
5. reads only XML written after the run started, and marks the run not citable if there is none;
6. restores each file from its saved copy, never from git, and checks its sha256 against the saved one;
7. checks `HEAD`, `git status --porcelain` and the `git diff HEAD` hash are what they were before.

**Every run below:** 0 compile-error lines, 14 fresh XML files and 0 stale, 133 tests run, sha256
match on restore, tree unchanged. The worktree was clean before and after every run (`git status
--porcelain` empty), so "the forward change is still present" means the files are byte-identical to
`HEAD` (`59c4dd85`'s code). A forward run with no edit, first: 133 tests, 0 failures.

Each failure was read for whether this edit could cause it. All of them can; none belongs to another
edit. Notes follow the table where the link is not obvious.

| # | Behaviour | Revert | Failed (test: message) |
|---|---|---|---|
| rA | Touches on the line reach the map | `.pointerInput(Unit) { detectTapGestures(onLongPress = {}) }` on the strip's line | 3: the portrait, fullscreen and landscape touch tests, "a long-press at (16.0.dp, 71.0.dp), line DpRect(left=8.0.dp, top=63.0.dp, right=376.0.dp, bottom=79.0.dp), must reach the map expected:<1> but was:<0>" (fullscreen at y 26 dp; landscape at (527.0.dp, 26.0.dp)) |
| rB | Window: 2 h 30 min before sunset | `SUNDOWN_LINE_SUNSET_WINDOW_MILLIS` 150 → 240 min (the owner's first 4 h) | 7: "the two thresholds are the owner's numbers" (expected:<9000000> but was:<14400000>); "hidden at 2 h 31 min …" in the domain, text ("expected null, but was:<Sunset 6:42 PM · in 2 h 31 min>"), screen, HUD and ViewModel tests; "shown early when the start-back time is 59 min away, hidden at 61 min …" (the 61-min case now inside 4 h) |
| rC | Window: start-back under 1 h | the start-back clause of `isShown` replaced by `false` | 4: "hidden outside both windows, written once either opens" (expected:<Sunset 6:42 PM · start back by 2:42> but was:<null>); "a start-back time already passed shows …"; "shown early when the start-back time is 59 min away …" (domain, and screen: assertExists) |
| rD | -596: no position, no line | `FindingPosition` shown, with "Sunset: finding your position…" restored as its words | 4: "no position has no line …" (expected null, but was:<Sunset: finding your position…>); "no position, and polar day, are hidden" (expected:<false> but was:<true>); both screen "… finding your position never renders" tests |
| rE | Margin re-derives the line at once (watch) | `onMarginChanged` keeps the last tick's turnaround | 2: watch "a margin changed in Settings moves the start-back time at once …" ("thirty minutes later, at once, with no tick expected:<1791075260741> but was:<1791073460741>"), and ViewModel "… on screen at once" (expected:<1789234324737> but was:<1789232524737>), each 30 min apart |
| rF | Margin re-derives the line at once (wiring) | Settings' ViewModel does not tell the watch once the margin is stored | 2: the ViewModel end-to-end test (the same 30 min message), and `SundownSettingsTest` "real touches across each row …" timing out at its wait for the watch to be told 45 (`SundownSettingsTest.kt:201`, "Condition still not satisfied after 5000 ms") |
| rG | Dusk fix: a held sunset keeps its own dusk | the held copy keeps the computed (tomorrow's) dusk | 1: "after sunset on a recording begun before it, sun set at today's sunset and dark at today's dusk": "1791166304354 within a second of 1791080015038", 86,289 s (a day) apart |
| rH | Alerts off: the line stays | `if (!enabled) return@withLock` restored before the line is published | 1: "with the alerts off nothing is delivered and the line stays …": ClassCastException, `FindingPosition` cannot be cast to `BeforeSunset` |
| rI | Arrived: the line stays | the arrived early return restored at the top of the tick | 1: "after arriving at the start the line is still published": "after sunset now: BeforeSunset(…)", the line frozen at the last pre-arrival tick |
| rJ | Start-back = the leave-by alert's time | the line's start-back time is the turnaround, without the walk back | 4: "the line's start-back time is the time the leave-by alert states" ("exactly the alert's own time expected:<1791073460271> but was:<1791074660272>", 20 min, the walk back), and the alerts-off, margin and at-least tests, each off by the walk back |
| rK | No walk back, no start-back time | the `Unknown` check removed from `startBackAt` | 2: "with no GPS fix the walk back is unknown and the line has no start-back time" and "the last known position gives the line before any live fix" ("never a walk back from a last known position expected null, but was:<1791074660742>") |
| rL | Return or Navigate moves the recording's line into the HUD | the HUD takes only the screen-computed line, never the recording's | 3: "tapping Return moves the line into the HUD …", "Navigate while recording shows the recording's line in the HUD" (assertExists), "the window holds in the HUD too" |
| rM | Navigate with no recording: sunset and dark only | the HUD's line dropped when there is no recording | 1: "Navigate with no recording shows sunset and dark only …": assertExists |
| rN | Recording: the strip shows the line | the strip is passed no line | 7: every strip test, e.g. "recording, the strip shows the line": assertExists; the portrait touch test: "Failed to retrieve bounds of the node" |
| rO | The margin survives (stored) | Settings' ViewModel does not store the margin | 2: "real touches across each row … survive a recreated repository" (expected:<45> but was:<60>); ViewModel end-to-end "stored expected:<30> but was:<60>" |
| rP | "Alerts off" survives (stored) | Settings' ViewModel does not store the alerts setting | 1: "real touches across each row … survive a recreated repository": expected:<false> but was:<true>, at the switch's own assertion |
| rQ | Alerts off stops the notifications | the alerts-off gate before deciding removed (`!enabled \|\| arrivedNow` → `arrivedNow`) | 7: `SundownWatchTest` "with the alerts turned off nothing fires" (expected:<[]> but was:<[HEADS_UP, LEAVE_BY, SUNSET]>); `TrackRecordingServiceSundownTest` "with the alerts turned off, the same recording posts nothing" ("alerts off: nothing on the sundown channel"); `SundownWatchLineTest` alerts-off; and four `SundownWatchAlertMomentsTest` tests |
| rR | 24-hour clock | the later time always "h:mm" | 1: "the phone's clock, 24-hour, gives both times in 24-hour": expected:<[18]:42> but was:<[6]:42> |
| rS | After sunset or dark: no countdown to tomorrow | the backward search skipped (`position != null && false && sunIsDown(…)`, so the smart cast stands and it compiles) | 5: "between sunset and dark with the countdown on tomorrow's sunset …", "after dark with the countdown on tomorrow's sunset …", "in the small hours, still dark since the evening's dusk …", "a recording started after dark says dark since today's dusk" (ClassCastException, `BeforeSunset` is not `AfterSunset`/`DarkSince`), and the arrived test (see notes) |
| rT | The line laid out whole (positive control) | a 60 dp width cap placed **after** `fillMaxWidth` | 1: landscape only, "not ellipsized: … visible end 8 of 36, ellipsized true". **Not a valid control for portrait and fullscreen**, see notes |
| rT2 | same, the cap placed before `fillMaxWidth` | `.widthIn(max = 60.dp)` before the fill | 3: portrait "recording, the strip shows the line", fullscreen and landscape: "not ellipsized: Sunset 1:46 AM · start back by 12:36: 1 line(s), visible end 8 of 36, ellipsized true, size 180 x 48" |
| rU | "start back was" once the time has passed | the "by" branch taken always | 2: "start back was, once it has passed, and at the moment itself" and "each state of the owner's path reads as confirmed in the strip": expected:<… start back [was] …> but was:<… start back [by] …> |
| rV | Stopping the recording takes the line away at once | `sundownLine = null` dropped from the stop's state update | 1: "stopping the recording takes the line away at once": expected null, but was:<DarkSince(…)> |
| rW | Another recording's line is not this screen's | the track-id match dropped from `copySundownLine` | 1: "another recording's line is not this screen's": expected null, but was:<DarkSince(…)> |

**Notes on the table.**

- **rT was a misplaced probe, this session's, kept because it was run.** A `widthIn(max = 60.dp)` after
  `fillMaxWidth()` cannot shrink the line: the fill has already fixed the minimum width, and
  `widthIn` only narrows inside incoming constraints. So in portrait and fullscreen the cap did
  nothing, and the two checks there stayed green because nothing was cut off, not because they cannot
  see it. rT2 places the cap first, and all three layouts fail. The landscape line is content-width
  (no fill), which is why rT bit there alone.
- **rS also fails the arrived test.** After arrival nothing is remembered for the alerts, the held
  sunset included, so after sunset the arrived recording's line depends on the backward search,
  which this revert removes. That failure belongs to this edit.
- **rQ's four alert-moment failures** are the guards from `a9ca61fc` doing their job: with the gate
  gone, alerts fire while off.
- **rF's timeout is this edit's own.** The settings test waits for the watch to be told 45; this
  revert is exactly the missing telling.
  *Later (RECORD -597, "The CI timeout" below):* that wait was replaced by one that runs the main
  looper; rF re-run against it fails with "the watch told 45: not within 5000 ms (stored [], errors
  [])". The timeout seen here was the right outcome for rF, but the old wait could also time out with
  the telling present.
- **The draft's r16 timeout did not recur.** The second session reported that its revert of the
  alerts-setting store (its r16) timed out at the margin wait (`SundownSettingsTest.kt:201`) in two of
  three runs, which it could not explain. This session's equivalent (rP) ran once and failed at the
  switch's own assertion with the margin wait passing. One run is not enough to say the timeout is
  gone; it is not explained, and is left recorded as the second session reported it.
  *Later (RECORD -597):* a timeout at that wait is now explained in general, by the race in "The CI
  timeout" below, and the second session's 2 of 3 is consistent with it. r16 itself was not
  re-run, so whether that race caused those two runs is inferred, not shown.
- **Not checked by a revert:** the Sundown section's labels and defaults ("the section reads as
  confirmed, alerts on and an hour selected by default"), and the polar cases. Their tests ran green
  in every run; no revert was made to show they can fail.

**Superseded.** The draft's revert table (its r01 to r20e, run by the second session with a runner
this session never saw) is replaced by the table above. Its results agree in direction with this
session's for every behaviour both covered; where messages differ (the touch probe, rT), the reason is
a different edit, stated above.

## Full suite

- **Before**: not run by this session. Taken from the arrived-alone report
  (`docs/navigation/2026-10-06-arrived-alone-report.md`, "Full suite", its "After" line): **3,823
  tests, 0 failures, 0 errors, 24 skipped**, measured on that branch's merged tree. That tree is the
  same as `ef269025`'s in `app/`: `git diff b1f888a5 ef269025 -- app` is empty (the merge `b1f888a5`
  to `ef269025` adds only docs, `428c6096`, and the PR merge).
- **First run on this branch** (`b55f3c80`, `--rerun`): **3,890 tests, 2 failures, 24 skipped.**
  Both in `AvailabilityScreenSettingsPanelTest`, which nothing in this dispatch had run before:
  "the photo-location checkbox starts on, explains itself, and toggles" and "the lock-camera checkbox
  starts off, explains that sideways photos save portrait, and toggles", each "… is not displayed!" on
  its explanation text (`:823`, `:842`).
  - **Cause, shown, not inferred.** Each test scrolls its *label* into view (`performScrollTo()`) and
    then asserts the *explanation* under it displayed, without scrolling to it. The Sundown section,
    placed above Photo Location, makes the panel taller, so at the class's `h640dp` the label lands at
    the bottom edge and its explanation below the fold. Through the saved-copy runner, the class alone:
    forward, 28 tests, 2 failures (the same two); with only `SundownSection(sundown)` and its divider
    removed from `SettingsContent`, 28 tests, 0 failures. Both runs 0 `e:` lines, fresh XML, restored
    and confirmed.
  - **Fix** (`ee5c44c9`, test only): the explanation is scrolled to before it is asserted displayed,
    as the label already is, with a comment saying why. The assertion is the same; what it no longer
    holds is that label and explanation fit on one screen together, which was never its claim.
    The class: 28 tests, 0 failures. **This is a change to existing tests that the dispatch did not
    list; the alternative was to move the Sundown section below the camera setting, a placement
    decision. The planner may prefer that; it is one revert of `ee5c44c9` plus moving two lines.**
- **After** (`ee5c44c9`, the final code head, `--rerun`): **3,890 tests, 0 failures, 0 errors, 24
  skipped.** 0 `e:` lines in the build log; the results directory was deleted before the run and all
  473 XML files were written after it started. 3,890 = 3,823 + 67, the `@Test` annotations this branch
  adds; 24 skipped, as before.
- **Memory.** Run with the defaults (`org.gradle.jvmargs=-Xmx2048m`, one test fork, no test heap
  setting in `app/build.gradle.kts`), not `--max-workers=2`: measured during the run, the Gradle daemon
  was about 2.0 GB resident, the Kotlin daemon 1.9 GB and the one test executor 1.4 GB, about 5.4 GB in
  all, under the 6 GB line. Nothing else heavy ran.

## The CI timeout (RECORD -597)

A fourth session, after PR #181's CI failed on `494a1754` (run 37498429829): 3,890 tests, 1 failed,
`SundownSettingsTest` "real touches across each row change it, and both settings survive a recreated
repository", `ComposeTimeoutException`. Passed locally in every earlier run.

**Where it failed.** The CI line `SundownSettingsTest.kt:186` is the test's `= runBlocking {` line,
not the wait: Gradle's short trace names the first frame in the test class itself, and the body is a
lambda class. Reproduced locally, the full trace shows the lambda frame at the
`waitUntil(5_000) { stored.lastOrNull() == 45 }` line (`:201` at `494a1754`), above
`AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:901)`. There is no other `waitUntil` in
the test.

**Cause: a wait that could not make progress, not a slow one.**
- *What `stored` records, and on which thread.* `stored` is filled by `onDarknessMarginStored`,
  which `AvailabilityViewModel.onDarknessMarginChanged` calls inside `viewModelScope.launch`
  (`Dispatchers.Main.immediate`) once `setDarknessMarginMinutes` returns
  (`AvailabilityViewModel.kt:1221-1228`). That write is `dataStore.edit`, running on the
  repository's scope, `Dispatchers.IO` in this test (`SundownSettingsTest.kt:83`). The coroutine
  suspends there and its resumption, which tells the watch, is posted to the main looper. Traced:
  `stored 45 on SDK 36 Main Thread`.
- *Whether `waitUntil` advances the main looper.* It does not. Disassembled from
  `ui-test-android` 1.12.1, the version in the CI trace (line 901 is its `new ComposeTimeoutException`):
  each pass evaluates the condition, calls `mainClock.advanceTimeByFrame()`, which advances
  Compose's `TestCoroutineScheduler` only, and sleeps 10 ms. Nothing runs Robolectric's paused main
  looper. Observed: with the margin's write delayed 1 s on IO, the post sat unrun for the whole 5 s;
  at the timeout `ShadowLooper.isIdle` was false; one `ShadowLooper.idle()` afterwards delivered
  both 30 and 45 within 40 ms.
- *Whether `runBlocking` blocks the dispatcher.* The test body runs on the main thread
  (`isMain=true`, thread `SDK 36 Main Thread @coroutine#6`), inside `runBlocking`. It holds the
  thread, but it is not what starves the post: the same post ran at once when the looper was idled
  from inside that same `runBlocking`. What was missing is a call that runs the looper.
- *So the race is:* the margin is delivered only if DataStore's write finishes before the last call
  that does idle the looper (the `waitForIdle` in `touchRow` and the four `selected()` reads). If it
  finishes after, no timeout is long enough. Raising the timeout would not have fixed it.
- *Why it passed locally.* This laptop's `/tmp` is tmpfs (`findmnt /tmp`), and Robolectric puts each
  test's files under `java.io.tmpdir`, so the write and its fsync cost almost nothing. CI runs on
  `ubuntu-24.04`, whose `/tmp` is on disk; that the runner's write is slower is inferred, not
  measured.

**Reproduction** (a temporary instrumented copy of the test, never committed: trace lines, and a
rule repeating the test in one JVM; `--max-workers=1`):

| Run | Test files on | Load | Result |
|---|---|---|---|
| 40 repeats, old wait | tmpfs | idle | 0 of 40 failed; margin from delivery to the wait as low as 1 ms |
| 40 repeats, old wait | tmpfs | 16 busy loops, 3 `dd` loops (onto tmpfs, so no real disk load) | 0 of 40 |
| 40 repeats, old wait | ext4 disk (`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=~/.cache/...`) | idle | **17 of 40 failed**, each `ComposeTimeoutException`; each entered the wait with `stored=[30]`, the looper idle; at timeout the looper held a post, and one `idle()` delivered 45 |
| 1 run, old wait, write delayed 1 s on IO | tmpfs | idle | failed: entered the wait with `stored=[]`; the post sat unrun for 5 s |

CPU contention alone did not reproduce it; a real disk did.

**Fix** (`dae48454`, test only). The wait is `awaitOnMainLooper`, which calls
`ShadowLooper.idle()` on every pass before checking, and fails with what was stored and logged. The
app code is correct: on a phone the main looper runs freely and the post is delivered. The 5 s bound
now waits only for a disk write, which is genuinely time-bound.

**Holding under the condition that broke it** (disk, the same instrumented copy with the fix):
- 100 repeats in one JVM: **0 of 100 failed.** 73 of the 100 entered the wait before 45 was
  delivered (4 with nothing stored, 69 with only 30), the state that failed every time with the old
  wait; so the sample includes the cases that could fail it.
- Write delayed 1 s, 3 repeats: 0 of 3 failed, each entering the wait with nothing stored.
- The committed test, 7 separate Gradle runs on disk: 2 tests, 0 failures each, 0 `e:` lines.
  These are weak evidence on their own: in the 40-repeat run the first 20 passed even with the old
  wait, so a cold single run rarely meets the race.

**Revert check.** With `onDarknessMarginStored(minutes)` removed from
`onDarknessMarginChanged` (saved-copy runner, 0 `e:` lines, results directory deleted first), the
class: 2 tests, 1 failure, "the watch told 45: not within 5000 ms (stored [], errors [])". Restored
from the saved copy; `git status` clean and the fix present afterwards.

**Not checked.** Twenty-one other test files use `waitUntil`; only this one waits on a real DataStore
write. Whether any of the others waits on something posted to the main looper was not checked.

**Full suite** (`dae48454`, on disk, `--rerun`): **3,890 tests, 0 failures, 0 errors, 24
skipped.** 0 `e:` lines; the results directory was deleted first and all 473 XML files were
written after the run started. Same count as `494a1754`, as expected for a change inside one test.

## Device-only (listed, not run)

- Where the line sits against the real top inset in portrait, landscape and fullscreen. Robolectric reports zero insets (CLAUDE.md).
- In landscape, how wide the strip grows to fit the line, and whether it reaches the search bar on a narrow phone.
- A real sunset on a walk: the line from 2 h 30 min out, "start back was", "Sun set … dark in", "Dark since".
- That the line moves at once on the phone when the margin changes in Settings mid-recording.

## Not done or not verified

- No phone, no adb.
- The full suite on `main` before was not re-run. The count comes from the arrived-alone report, as stated.
- No recorded red run for the tests in `ea722ff7`. The revert checks stand in for it, as stated under Tests.
- The second session's account of the uncommitted `.clickable {}` line was not verified.
- The draft's r16 timeout is unexplained; this session's one equivalent run did not show it.
  *Later (RECORD -597):* explained in general, not for r16's own runs; see "The CI timeout".
- The labels and defaults of the Sundown section, and the polar cases, have no revert check.
- `ee5c44c9` changes two existing tests; see Full suite for the alternative.
- Two points still need the owner's word (polar night's words, checkbox not switch); see "Owner rulings".
