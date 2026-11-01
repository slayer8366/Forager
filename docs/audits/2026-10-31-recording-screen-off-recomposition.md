# Why the screen recomposes while recording with the screen off (F-4)

Dispatch `prompts/preserved/2026-10-31-03-recording-screen-off-recomposition.md` (origin/records-after-173, RECORD -1905). Look
only: no code changed, no Gradle, no adb, no phone. Base origin/pre-main d54189479, checked against the remote. Compose 1.12.0
(BOM 2026.08.00). The phone ran 8e70a28ba; see "Confirmed" for how it compares. Library facts below
were read from the 1.12.0 jars in the Gradle cache (`javap`) and from androidx-main source, which agrees where both were read.
Nothing here carries coordinates or place names.

## In plain language

The cost is one stuck animation helper, spinning. When a number in a line changes (a minute off "in 1 h 8 min"), `WordSwap`
tells Compose's `AnimatedContent` "new text, same words, no fade". That normally ends within a frame or two, on the next tick of
the animation clock. Android stops that clock when the activity stops, but with this Compose version composition itself keeps
running. So a number that changes after the stop starts a transition that cannot finish, and `AnimatedContent`'s own
bookkeeping edits its list, which schedules another composition, which edits it again. It spins every frame, drawing nothing,
until the screen comes back.

It has little to do with recording as such, with the always-on display as such, or with GPS. It needs a `WordSwap` line whose
number changes while the activity is stopped. Recording matters because it is what keeps the sunset line on the strip and fed.

## Answers

### 1. What drives the recomposition

**A self-sustaining loop inside `AnimatedContent`, not repeated state changes from the app.** The app supplies one change; the
loop then feeds itself.

The code, `AnimatedContentImpl`, Compose animation 1.12.0 (order confirmed in bytecode, `AnimatedContentKt`):

1. `if (!currentlyVisible.contains(currentState)) { clear(); add(currentState) }`
2. `if (currentState != targetState) { id = indexOfFirst { contentKey(it) == contentKey(targetState) } ... removeAt(id); add(targetState) }`

`WordSwap` (`ui/motion/WordSwap.kt:44-51`) passes the full text as `targetState` and `wordsOf` as `contentKey`. For a number-only
change the old and new text differ but share a key, so step 2 *replaces* the current entry with the target. The current state
has now left the list. On the next composition step 1 puts it back and step 2 removes it again. Each pass writes a snapshot list
that the same scope reads, so each pass schedules the next. It ends only when the `Transition` makes `currentState` equal
`targetState`, and that happens on a `withFrameNanos` inside `Transition.animateTo`.

A change in **words** cannot do this: the keys differ, step 2 appends, the list `[old, new]` is stable, and composition goes
quiet (frozen, not spinning) until the clock returns.

The profile fits this and not the alternatives (`part2/logs/450-1-rec-front-off/perf-children.txt`):
- 42.6% is `AnimatedContentImpl`'s own restart lambda; `WordSwap` itself is 0.09% and `AvailabilityScreen` 1.2%. The screen is
  not recomposing; one scope is.
- `SnapshotStateList.clear` 1.63%, `.add` 1.46%, `.removeAt` 1.34% are on the path. These run on every pass here, and with
  nothing on screen changing there is no other reason for them.
- `CompositionImpl.addPendingInvalidationsLocked` 14.3%: self-invalidation being processed.
- `wordsOf` at 18.4% is `contentKey`, called repeatedly per pass. The regex is a multiplier, not the cause. The dispatch's
  `WordSwap.kt:23` is the `NUMBER` regex; the call is at `:49`.

**The app's feed**, from the state that changes to the screen:
- Service: `TrackRecordingService.kt:276-279` calls `tickSundown` every 15 s (`SUNDOWN_TICK_MILLIS`, `:559`).
- `TrackRecordingWatchCopies.kt:81-85` `copySundownLine` copies the watch's line into `TrackRecordingUiState.sundownLine`.
- `MainActivity.kt:404` `trackRecordingViewModel.uiState.collectAsState()`: **not** lifecycle-aware, so it keeps delivering while
  stopped.
- `CompactMapTopStrip.kt:146`, `sundownLineText` (`SundownLineText.kt:66-74`) gives "Sunset 5:59 PM · in 1 h 9 min · dark 6:29",
  into `WordSwap` at `AvailabilityMapControlsUi.kt:462`. Screenshots `part2/shots/01-rec-tap.png` and `02-after-450-1.png` show
  that line. The "in N" part changes once a minute: number-only.

Other number-only feeds that stay live while stopped, read in code, not observed in the loop:
- `PositionLabel.kt:57-67` `rememberPositionNote`: "Last seen 19 s ago, finding GPS…" ticks at 1 Hz (`AvailabilityMapControlsUi.kt:280`).
- `NavigationInfo.kt:88-93`: a 1 Hz age ticker into the navigation panel's `WordSwap`s. Unread whether each shown text moves.

### 2. Why it runs with the screen off, and whether anything is drawn

**The activity really is stopped, and that is the cause rather than an obstacle.** The Compose lifecycle observer
(`WindowRecomposer_androidKt$createLifecycleAwareWindowRecomposer$2`, 1.12.0, bytecode) calls only
`Recomposer.pauseCompositionFrameClock()` on `ON_STOP`, and never `PausableMonotonicFrameClock.pause()`. That pause holds back
`withFrameNanos` callers, animations included, while recomposition continues. So a stopped activity keeps a working
composition and a frozen animation clock: exactly what the loop needs. It is the frozen clock that prevents the normal exit.

**Nothing is drawn**, confirmed: `dumpsys gfxinfo` total frames unchanged across the run (phone report 450-1), and
`VRI: Not drawing due to not visible` plus `stopped(true)` in the logcat, `destroySurfaces: appStopped=true` from WindowManager.

**The always-on-display premise is wrong as a cause.** Nothing in the path depends on doze. The Choreographer delivers vsync
in any case, and what the phone report contrasted as "under always-on display" versus Home has a likelier explanation, next.

**Why Home reads 0.7%:** at Home the logcat has `BBA2: setIsFg isFg = false; delayValue 3999ms` at 16:52:30.659 and
`Choreographer: CoreRune.SYSPERF_ACTIVE_APP_BBA_ENABLE : stop animation in background states` at 16:52:38.835, in the app's
own process. Neither appears in that process in the screen-off runs. The 15 s layout beat, present through both screen-off runs,
is absent at Home. So this looks like a Samsung gate on the Choreographer for background apps. A plain Android phone may then
spin at Home too; that is inferred from two log lines and unknown off Samsung.

### 3. Old or new

**Old, and on main: in the released app, not only pre-main.**
- `WordSwap` arrived whole in cd394b467 (2026-10-07, RECORD -666), with `contentKey = { wordsOf(it) }`. On main.
- Compose BOM 2026.08.00 has been the pin since the first commit (827062d3f, 2026-08-15), the same on main.
- `WordSwap.kt`, `PositionLabel.kt`, `SundownLineText.kt` and the version catalog are identical on main, and
  `TrackRecordingWatchCopies.kt` differs only by 11 lines main lacks, see "Confirmed".

#450 did not cause it. It removed the old 11% redraw cost, which makes this easier to see but not larger. The released app is
not debuggable and has no CheckJNI, so the same loop should cost it less than 41%; how much less is unmeasured.

### 4. Does it depend on having no GPS fix

**Not in its mechanism. It depends on the time of day, and the data shows it.** Trial C on 10-30 is the natural control: it was
also on a desk indoors (the 10-30 report's words: "On the still desk (trial C)"), recording, app in front, screen off. It ran
about 13:40 PDT, more than 150 minutes before sunset. `SUNDOWN_LINE_SUNSET_WINDOW_MILLIS` is 150 minutes (`domain/SundownLine.kt:141`),
so the line was hidden and had no number to change, and trial C held about 16% with the compass still running. On 10-31 the
runs were about 75 minutes before sunset, line showing: 41%.

The cost with a fix, by condition. The figure is from this debug build, S22:

| Condition | Live feed | Expected main thread |
|---|---|---|
| More than 150 min before sunset, start-back window closed, no "Last seen" | none | about the 450-1 pre-onset level: r2's first 16 s was 1 to 3% |
| Sunset line shown with "in N min" (walk back unknown) | changes every minute | loop within a minute, about 41% until the screen wakes |
| Sunset line as "start back by 5:12", after a track has a walk back | only when that time moves | loop from the first move, then until wake |
| Evening: "dark in N min" | every minute | as the second row |
| No fix, strip on "Last seen" at the moment of stop | 1 Hz | loop within a second, at any time of day |

So a fix removes one trigger (the last row) and leaves the evening ones. The evening is when a forager most needs the battery.
Only the first row and the 41% are measured; the others are read from code. With a fix the strip's elevation and heading do not
appear to be fed while stopped (`AvailabilityViewModel.onLeftForeground` releases fixes; `rememberTrueHeading` stops), inferred
from those two read paths only.

**One correction to how the figures read.** 39.2% and 29.4% are not two levels. After onset both runs sit at about 330 ticks per
8 s, 41% of a core. Run 2 averages lower because it took about 22 s to start (per-interval ticks: 26, 9, 73, then 322 to 334).

### 5. Fixes

**Recommended: A.** B is a separate decision, and C and D are not recommended as fixes.

**A. `WordSwap` animates on its words, and numbers bypass the transition.** Make `targetState` the key (`wordsOf(text)`), and let
the content read the latest text for that key. The outgoing line keeps the text it last showed, so a crossfade looks as it does
now. A number-only change then alters no `Transition` state, so the list is never edited and the loop has nothing to start from.
- The user notices nothing on screen: numbers still change at once, words still crossfade. Battery while recording in the evening
  with the screen off improves, and it fixes all 14 call sites in one place. `wordsOf` also runs once per text change.
- Size: small, about 15 lines. `ui/motion/WordSwap.kt` (52 lines) and `WordSwapTest.kt`. Within the modular-code limits and no
  conditional added to working code.
- Fails first: a test that stops the activity (lifecycle at CREATED, so the real `pauseCompositionFrameClock` runs), changes a
  number, and counts compositions over several frames. Expected unfixed: the count climbs with every frame. Fixed: it settles.
  Unverified: whether the Robolectric compose rule uses the real lifecycle-aware recomposer. If it does not, the test needs a
  plain `ActivityScenario` and `setContent`, or its own `Recomposer` with `pauseCompositionFrameClock()`. The existing "numbers
  alone" test passes with the bug in place, since its clock runs. A revert check must therefore show the new test failing.

**B. Stop feeding stopped UI** (`collectAsStateWithLifecycle` at `MainActivity.kt:404`). It would also end the 15 s layout beat.
But it changes every reader of `trackUiState`, including the effects in `MainActivity` that act on recording state
(`:420-447`), so a large behavioural risk, and it does nothing for the 1 Hz `produceState` tickers, so it does not close the
loop on its own. A stop-and-ask for the owner, if wanted at all.

**C. A cheaper `wordsOf` (no regex).** Takes off at most 18%; the loop stays. Rejected as a fix.

**D. Report it to Compose, or change the Compose version.** Worth filing with a minimal reproduction, but there is nothing to wait
for, and whether another version behaves differently is unread. Separate from A.

**Worth checking with A.** `StateCrossfade`, `PageSlide` and `JournalTab.kt:622` pass a `contentKey` to the same library code,
so a same-key, different-value change there while stopped should spin alike. Each needs a value that changes while stopped.
None was traced.

## Measurements only the phone can give

Each on the S22, recording, Maps in front, sampled as in `scripts/sample.sh`:
1. A confirming run: more than 150 minutes before sunset, no "Last seen" showing. Prediction: stays near 0.4 to 3%.
2. Inside the window, a `simpleperf` run with the call graph kept (`-g`), plus a one-minute log of the strip's text. Prediction:
   onset at the 15 s tick where "in N min" changes.
3. After fix A, inside the window: the same run. Prediction: no onset.
4. Home, inside the window, on a non-Samsung phone, for the question in answer 2.
5. The release build, for the real size of the cost.

## Disclosure

### Confirmed vs inferred

**Confirmed, read or counted:**
- The Compose behaviour on `ON_STOP` (1.12.0 bytecode) and the order of `contains/clear/add` in `AnimatedContentImpl` (bytecode;
  the `removeAt` branch from androidx-main source and in the profile).
- The profile percentages, tick counts, log lines and the strip text in the screenshots.
- Every file and line named, at d54189479.
- The 150-minute window, and `collectAsState` at `MainActivity.kt:404`.
- Those four app files and the version catalog are identical on the phone's 8e70a28ba and the base. Against origin/main, only
  `TrackRecordingWatchCopies.kt` differs, by 11 added lines; which ones was not read line by line, so main's `copySundownLine`
  is taken from the base's, not read separately.

**Inferred, strongly, not run:**
- The loop itself. It is reasoned from the library code and matched against the profile. Nothing has been run that shows
  `currentState` leaving the list. Measurement 3 or the fail-first test would settle it.
- That `Transition`'s finish is held by the paused clock (androidx-main source, `Transition.kt`, not 1.12.0 bytecode).
- That run 2's onset at about 16:51:08, on the 15 s beat (16:50:53.7, 16:51:08.8, 16:51:23.8), was the minute changing in the
  sunset line. By elimination: with the strip on its position note, the note and the sunset line are the live `WordSwap`s. The
  beat before it did not start the loop; that one did. The sunset time's seconds are not known, so it is not proved.
- That the 15 s "surface is not valid" beat comes from the sundown tick or the 15 s poll. Both run at 15 s; which was not traced.
- That trial C's strip had no sunset line: from the 13:40 PDT time and the code, not from a capture. Trial C's fix state was
  not checked.

### Could not determine
- **What started run 1.** It was spinning within about a second of the stop (319 ticks in the first interval), before the first
  beat at 16:44:08. Candidates: a number-only change in flight when the clock paused, a 1 Hz "Last seen", or an extra sundown evaluation after the
  fix logged at 16:44:04.44 (`TrackRecordingService.kt:229`, which runs on the first fix only, so whether that fix qualified is unread). Run 1 also shows
  no rise when the minute next changed, which a second looping line might be expected to give. That is not explained.
- Whether the screen-on 27.7% contains any of this. There is no screen-on profile, and it has a live compass and a drawing map.
  The closeness of 27.7% to 29-39% looks coincidental; 29.4% is a mix of two states.
- The cost in the release build, whether a walker on a real walk hits every row of the table, and the battery figure.
- The Samsung gate's exact behaviour.
- Whether the walk logger contributes: absent from the profile, so unlikely, but on the tools build only.

### Premises that were wrong
- **"Trial C … was on a walk with a fix"** (the phone report's F-4, carried into the dispatch as "not like for like … no GPS
  fix"): the 10-30 report has trial C on a desk, indoors. The real difference between them that matters here is the hour.
- **"Recording state read in composition with the frame clock running under the always-on display":** the composition clock is
  the part that stopped. Vsync continues, and doze is not the condition.
- **"Recomposition running frame after frame":** true, but of one scope, not the screen.
- **`WordSwap.wordsOf` as a suspect:** a victim, not a driver.
- **The two CPU figures as a range:** one level, 41%, with different onset.
- The phone report's "activity `STOPPED`" did not come from its samples: `$st` in `sample.sh` is empty in every row of
  `samples.txt`. It is true anyway, from `stopped(true)`, `appStopped=true` and the compass stopping under `repeatOnLifecycle`.

### Decided beyond scope
- Fetched androidx-main source with `curl` and read the Gradle cache's jars with `javap`. Neither is a Gradle run; both stayed in
  `/tmp`. Main-branch source may differ from 1.12.0, which is why the two key facts were checked in bytecode.
- Read two owner-phone screenshots from the evidence folder for the strip's text. They stay off git; place names were left out.
- No row added to `docs/audits/README.md`: the dispatch limits changes to this report.
- The Restricted Object was not read, named or quoted.
