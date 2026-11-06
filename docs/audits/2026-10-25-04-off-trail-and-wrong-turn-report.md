# Off the trail and back, and the wrong-turn alert: report before building (dispatch 2026-10-25-04, plan step 7)

**Status: a report. Nothing is built.** No app code, no Gradle, no phone. Nothing is built until the owner rules on this report (RECORD -1629).

**Written by:** an app coder (Opus), 2026-10-25 (UTC).

**Base, checked against the remote at 2026-10-25T06:31Z:**
- App code read at `origin/pre-main` `f5a0cfafc` (the dispatch's floor; `git ls-remote` gave the same head).
- This branch, `off-trail-report`, cut from `origin/records-after-173` at `2b536f576`. It was fast-forwarded to `293927219` (RECORD -1630, dispatches 2026-10-25-06 and -07) before writing, because -06 touches files this report's PRs share (section 6).

**Paths.** App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`. Every line number is at `f5a0cfafc`.

**No position is in this report.** Every figure is metres, seconds or counts.

---

## 0. The short version, for the owner

**Three things are wrong or missing today:**
1. **The false alarm.** On a way home along trails, following the app's own solid trail line can set off the off-track alarm. The alarm still measures against the path you walked out, not the line it now draws (section 1.2).
2. **Nothing quiet happens when you leave the trail.** There is no dimmed trail, no off-trail notice and no "Back on trail". None of -445's quiet behaviour is built (section 1.4).
3. **No wrong-turn alert.** Nothing knows which trail you are on. The route data on the phone carries no trail names at all (section 2.1).

**What I propose:**
- **First,** a small fix that stops the false alarm. It touches nothing step 6 touches except one wiring line, and it may be what lets pre-main be promoted (-1612).
- **Then** the rename to "Re-center".
- **Then** the quiet off-trail behaviour, after step 6 has built.
- **The wrong-turn alert last.** It needs a decision first: where the trail names come from (section 2.2, question Q1).

**The owner's decisions are collected in section 4.** Each comes with options and a recommendation. **The step paths to confirm are in section 3.**

---

## 1. Today's off-track and alert paths, read from the code

### 1.1 The one alert there is: the off-track alert during a return

**When it runs.** Only while a return is under way:
- `ReturnWatch.startReturn` sets `returning = true` (`domain/ReturnWatch.kt:185-195`).
- `onFix` judges only `else if (returning)` (`:254`).
- Nothing judges before Return, so **there are no alerts while foraging out today.** That half of -445 item 4 already holds.
- During a waypoint navigation the return is paused (`domain/WaypointNavigation.kt:15-16`, steps 6 and 7 of its step path), so nothing judges there either.

**What it measures against:**
- At Return, the kept points are copied as the path: `pathAtReturn = keptPoints.toList()` (`ReturnWatch.kt:187`).
- The judge is built at the first measurable fix, from the start plus that path: `OffTrackJudge(listOf(start) + pathAtReturn)` (`:255`).
- The path never changes for the rest of the return.

**The rule** (`domain/OffTrackJudge.kt`):
- **Off:** further from the path's line than `OFF_TRACK_LINE_METERS = 40.0` plus the reading's reported accuracy (`:8`, `:86`, `:88`).
- **Held:** for `OFF_TRACK_HOLD_MILLIS = 15_000` (`:11`), over at least `OFF_TRACK_MIN_READINGS = 3` GPS readings (`:14`, `:92`).
- **One alert per stray.** It re-arms after `OFF_TRACK_REARM_MILLIS = 10_000` back within the line (`:17`, `:101-104`).
- **Network readings are skipped** (`:78-79`).

**What the walker gets:**
- A notification and a vibration: `alertDelivery.deliverReporting(Alert(kind = AlertKind.OFF_TRACK, overridesSilence = false))` (`ReturnWatch.kt:267`). It respects a silenced phone.
- It is gated by Settings' "Off-track reminder" checkbox (`:262`, `AppContainer.kt:397`).
- The notification reads **"Off track" / "You're moving away from your start point, not toward it."** (`res/values/strings.xml:31-32`, posted at `alert/AndroidAlertDelivery.kt:225-232`). It is on channel `off_track_alert_v2` (`:187`), at `PRIORITY_HIGH` (`:229`).
- The Return control turns red while `isOffTrack` holds (`ui/availability/TrailheadControls.kt:181-182`). That flag comes through `TrackRecordingViewModel.kt:437`, then `MainActivity.kt:666`, then `CompactMapIconCluster.kt:47`.

**The notification's wording is stale.** "moving away from your start point" describes the rule that -425 retired (distance to the start rising by 25 m: `docs/navigation/2026-10-03-off-track-rule-report.md:16`, `:26`). The rule since then is distance from the walked path. The -425 report's list of changed files does not name `strings.xml` (`:68-87`). So the text was never updated: inferred from that list, not from git history.

### 1.2 The false alert on a trail way home (Hazel's part 2 report, section 7, re-read on pre-main)

Confirmed as Hazel described it, line for line, at `f5a0cfafc`:
- **The path is fixed at Return** (`ReturnWatch.kt:187`).
- **The judge measures against that path only** (`:255`), at 40 m plus accuracy (`OffTrackJudge.kt:7-8`), held 15 s (`:11`).
- **Nothing in `ReturnWatch` reads the way home.** Its constructor takes only `computeReturnToStart`, `alertDelivery` and `isReminderOn` (`ReturnWatch.kt:104-115`).
- **The dependency runs the other way.** `WayHomeWatch` reads `ReturnWatch`'s state for `isReturning` (`AppContainer.kt:431`).

So a walker who follows the solid trail part where it runs more than about 40 m from the way they walked out gets the alarm after 15 s. This was **seen on the S22**: the alarm posted at 01:26:07 UTC, about 80 m from the outbound track, on the way home's trail part (`prompts/preserved/2026-10-25-hazel-s22-way-home-2-report.md` section 3.6, branch `s22-way-home-2-report`).

**Where it would change:** `ReturnWatch.onFix` at `:255`, the judge's line. Section 5, PR 1.

### 1.3 The way home's own stray handling (built in part 2, and not an alert)

`WayHomeWatch` re-routes; it never alerts:
- **When it re-routes:** a GPS fix within the live-fix gate more than `WAY_HOME_STRAY_METERS` from the line shown (`domain/wayhome/WayHomeWatch.kt:148-168`) starts one re-route. That figure is the hop's far band, 50 m (`:247`).
- **Re-arming:** the next stray counts only after a fix within `WAY_HOME_STRAY_REARM_METERS`, 45 m (`:149-153`, `:250`). That is "re-route every stray", RECORD -1625.
- **A re-routed line starts at the walker's newest stored point.** It runs dotted back along their own track to where they left the trail, then solid along the trail (`domain/wayhome/WayHome.kt:269-290`; `FindWayHomeUseCase.kt:113`).

**What this means for step 7:** -445's "guidance follows their own track back to where they left the trail" **is already what a way-home re-route draws.** Step 7 does not need a second way of drawing the way back. What is missing is the quiet state around it (1.4), and an alert that does not measure against the wrong line (1.2).

**The HUD while the walker is over the far band.** It shows the track home and its distance (`ui/track/TrackRecordingRouteTicks.kt:103-114`, M-396-1). The line ahead is faded to `ROUTE_AHEAD_FADED_OPACITY = 0.4` only when the route is withheld (`ui/map/RouteHomeLayers.kt:76-77`, `:130-131`). That is the nearest thing to "dims the trail" built today. It fades the whole line, not the trail part.

### 1.4 What -445 item 4 asks for and does not exist

Searched in `app/src/main`:
- **"Off trail", "your track leads back", "Back on trail", "Wrong way":** no matches. The one off-track string is the notification's (`strings.xml:31-32`).
- **No state means "off the trail":** `isOffTrack` means "off the path at Return" (`ReturnWatch.kt:39`).
- **No code dims the trail part apart from the track parts,** or highlights the track.

### 1.5 "Return to Route" today

- **The pill:** `RETURN_TO_ROUTE_TEXT = "Return to Route"` (`ui/availability/ReturnToRoutePill.kt:31`), with the `MyLocation` icon (`:72`).
- **When it shows:** while navigating (a return or a waypoint), once the walker has moved the map away from the navigation view. A tap brings the view back, and it follows again (`:43-50`).
- **Its one test by text:** `AvailabilityScreenNavigationViewTest.kt:233` (`onNodeWithText("Return to Route")`). The other tests there name it in their titles and comments (`:220-252`, `:355-444`).
- Comments elsewhere name it by the owner's original words: `MapNavigationView.kt:24`, `NavigationView.kt:32`, `NudgeElastic.kt:16`, and others. They are history, and they would stay.

### 1.6 Sundown is already its own alert

`SundownWatch` (`domain/SundownWatch.kt`) delivers a recording's three sundown alerts: the heads-up, the leave-by and the sunset alert (`:15`). They override silence (`domain/AlertDelivery.kt:100-101`). They fire whether or not the walker is on a trail. This matters to -445's "or near dusk" (section 4, Q4).

---

## 2. Which path the walker is on, and "away from the route" without false alerts

### 2.1 What the phone holds today: no trail names

- **The route data has no names.** It is BRouter `.rd5` squares (`domain/routedata/RegionRouteData.kt:40-50`). BRouter v1.7.10 keeps a way's tags only through its lookup table, `app/src/main/assets/routing/profiles2/lookups.dat`. I read every key in that file: there is **no `name` key and no `ref` key**. There are `highway`, `surface`, `sac_scale`, `informal`, `trail_visibility` and the `route_hiking_*`/`route_foot_*` relation flags (`:599-608`). Each key's values are a fixed list, so a free-text name cannot be added as one more key. That is inferred from the file's format, not tested.
- **The engine returns only points.** `BRouterTrailRouter.foundRoute` keeps only the route's points and length (`routing/BRouterTrailRouter.kt:104-118`). The engine spike said the same: "BRouter has no matcher" (`docs/navigation/2026-10-03-engine-spike-report.md:147`).
- **The map tiles do carry names.** The Trail Map style labels paths and minor roads from the tiles' `name` property, from zoom 14 (`map-style/forager-street-v2.json`, layer `roads_labels_minor`). The tiles are Protomaps' basemap build, served by the Worker (`server/pmtiles-worker/README.md:48`). An offline region stores zoom 10 to 15 of them (`:59`) in MapLibre's own database, `files/mbgl-offline.db` (`map/MapLibreStorage.kt`, header).
  - Whether path names survive into the stored zoom-14 and zoom-15 tiles is **inferred from the style, not read from a tile.**
  - The tiles and the routes come from **different extracts**: Protomaps' build, and the Pi's build (`RegionRouteData.Stored.osmTimestamp`, `:42-44`).
- **The app reads map features only from the live map, on screen:** through `queryRenderedFeatures` (`ui/map/FanOutLayers.kt:354-370`, for taps). Nothing reads a stored tile.

**So the planner's premise needs correcting.** "The trail names in the new map data, which is readable on the phone" (plan update, question 8) is true of the tiles only through the map on screen. The alerts run in the recording service, with the screen off and the phone in a pocket (`ReturnWatch.kt:62-67`). From there the names are not readable today by any path in the code. Section 4, Q1 has the ways to make them readable.

The spike's own finding still stands: **compare trail and road names, not OpenStreetMap ways**, because one street is split into many ways (`engine-spike-report.md:142`).

### 2.2 How "away from the route" can be judged without false alerts

The rule I propose. Every figure is an existing named figure. No new number is added except Q3's, and that one is the owner's.

1. **Only along a trail part.** Only while a way home along trails is shown (later, also a waypoint route, step 6). The walker must have been placed on its trail part before. On the dotted track parts, and with no trail line, there is no route to turn away from.
2. **On another named way.** The fix is within `WAY_HOME_TRAIL_JOIN_METERS`, 25 m, of a named trail or road (`WayHome.kt:360`, the existing "on a trail" figure). That way's name differs from every name on the route's trail part near the walker. The same name must hold for the whole hold.
3. **Away from the route.** Further from the trail part than `OFF_TRACK_LINE_METERS` plus accuracy, for `OFF_TRACK_HOLD_MILLIS` and `OFF_TRACK_MIN_READINGS`. The distance at the end of the hold must be greater than at its start: moving away, not alongside.
4. **One alert per stray, shared with the off-track alert.** Whichever fires first is the stray's one alert. It re-arms as the off-track alert does.

**What each part stops:**

| Case | What stops a false alert |
|---|---|
| A parallel trail within 40 m | Rule 3: never further than the line. |
| A parallel trail further off, keeping its distance | Rule 3's "moving away". It falls to the quiet off-trail behaviour. |
| A crossing trail | Crossing takes seconds. Rule 3's 15 s hold, 3 readings and 40 m are not met. |
| A junction where both branches carry the route's name | Rule 2: the same name. Quiet off-trail instead. Accepted, not solved. |
| An unnamed path | Rule 2: no name. Quiet off-trail (-1498 answer 8). |
| GPS wander under canopy | The accuracy widens the line; network readings are skipped (as `OffTrackJudge` does). |

**The line shows the way back already.** Past 50 m from the line, the way home re-routes (1.3). The new line runs dotted back along the wrong trail to where the walker left the route. That is -447's "the line shows the way back", with nothing new drawn.

---

## 3. Step paths for the owner to confirm

Each path is: the walker does something > the app shows something > Back. "The trail line" is the solid part of the way home; "your track" is the dotted part (RECORD -1589). Every figure is provisional, for a walk to test (-445).

### 3.1 Re-center (the rename)

1. Navigating home or to a waypoint, drag the map > it stops following, and **"Re-center"** appears at the bottom centre, with the same locate icon as today.
2. Tap "Re-center" > the tilted, facing-up view comes back and follows you; "Re-center" goes.
3. Back > unchanged from today: Back does not act on the pill.

Only the words change. The place, the icon, the 80% fill and the touch area stay. Google Maps' word is "Re-center", the US spelling, as the owner asked. I did not re-check that against Google Maps this session.

### 3.2 Returning while off the trail (the common case: you left the trail to forage)

1. Tap Return > the way home appears: your track dotted back to where you left the trail, then the trail line home (as built in part 2).
2. About 15 s later, still off the trail > **quietly, with no sound and no vibration:**
   - the trail line dims;
   - your dotted track brightens;
   - the HUD's status line reads the off-trail notice (wording in 3.6).
3. Follow your track to the trail > once you are back on the trail line for about 10 s:
   - the trail line returns to full strength;
   - **"Back on trail"** shows for about 5 s, then the HUD's usual line returns.
4. Walk the trail line home > no alarm, even where the trail runs far from the way you came out. This is the false alarm fixed.
5. Back > unchanged: it leaves the navigation view as today.

### 3.3 Leaving the trail on the way home (to forage again)

1. On the trail line, step off it > for about 15 s nothing changes.
2. Still off it > the same quiet state as 3.2 step 2.
3. More than 50 m off the line > the way home re-routes, as today: dotted back along where you just went, then the trail. Still quiet.
4. Back on the trail > "Back on trail", as in 3.2 step 3.

### 3.4 Far from both the trail and your track (the one gentle alert)

1. Off the trail, and also more than about 40 m from any track you had walked when you tapped Return and from every trail line shown on this return, for about 15 s > **one** notification and vibration. It respects a silenced phone and the Off-track reminder setting, as today. Its wording is in 3.6.
2. Keep going > no repeat for this stray.
3. Come back within reach for about 10 s > it can alert once more on the next stray.

### 3.5 A wrong turn onto another named trail or road

Built only after Q1 is decided.
1. On the trail line, take a different named trail, moving away from your route > for about 15 s nothing changes.
2. Still on it and still moving away > **one** notification and vibration, with the wrong-turn wording (3.6). The quiet off-trail state shows too.
3. More than 50 m off the line > it re-routes: the line runs dotted back along the wrong trail to your route.
4. Turn back onto your route > "Back on trail".

An unnamed path, a crossing or a trail running alongside never gives this alert (2.2).

### 3.6 The wording, for the owner to choose (-1498 answer 5)

The owner: "use the Return to Route for the Off trail notice". The HUD's status line has a full form and a short form; the way-home notes already work this way (`ui/availability/NavigationHudText.kt:65-73`).

| | Full form | Short form | Note |
|---|---|---|---|
| **A (recommended)** | "Off trail · Return to Route along your track" | "Return to Route" | Says where you are and what to do, and uses the owner's words as the instruction. |
| B | "Return to Route · follow your track back" | "Return to Route" | Puts the instruction first; does not say "off trail". |
| C | "Off trail · your track leads back" | "Off trail" | -445's words, with "Return to Route" not used. Included because it is the earlier ruling's text. |

**Also to confirm:**
- **"Back on trail"** (-445's words), for about 5 s.
- **The gentle alert's notification.** It replaces today's stale text (1.1). Title "Off track", text "You're far from the trail and your track. The line on the map shows the way back."
- **The wrong-turn notification.** Title "Wrong way", text "Your route is back there. The line on the map shows the way back." This is the planner's provisional "Wrong way, your route is back there" (-447), with the line added.
- **The notice is not a button.** Tapping it does nothing. "Re-center" stays the one button. Recommended, so there is one control and one notice.

---

## 4. The owner's decisions

**Q1. Where the trail names come from** (for the wrong-turn alert only; nothing else needs them)

| Option | What it costs | Recommendation |
|---|---|---|
| **(a) A small names file per area, made on the Pi** from the same extract as the routes. The Worker cuts it to the area, and the app stores it beside the routes. | Pi and Worker work (Aspen's side), plus an app download step. A few hundred KB per area at a guess, not measured. Names and routes agree, because they come from one extract. It works with the screen off. Forager owns the format. | **Recommended.** |
| (b) Read the names from the map tiles already stored on the phone, in MapLibre's offline database. | No server work. But it reads MapLibre's private database layout, which an SDK upgrade can change. It needs a tile decoder added to the app. The tiles come from a different extract than the routes. | Workable, more fragile. |
| (c) Ask the map on screen. | Works only with the screen on and the map showing. The alert runs in the background. | Not recommended. |
| (d) Leave the wrong-turn alert out of step 7, and ship the rest. | Nothing now. A wrong turn falls to the quiet off-trail state and, if it goes far, the gentle alert. | The fallback if (a) waits on the Pi. |

**Q2. What "step 7 lands" means for promotion (-1612).** Is it PRs 1 to 3 (no false alarm, Re-center, the quiet off-trail state), with the wrong-turn alert after? Or all of step 7? **Recommended: PRs 1 to 3.** PR 1 alone stops the false alarm the S22 saw.

**Q3. How far "far from both" is.**
- **(a) 40 m plus accuracy, held 15 s. Recommended.** It is today's figure, the owner's "About 40 m". It is measured from every track walked before Return and every trail line shown on this return. Foraging near either never alerts.
- (b) A wider figure, such as 100 m, if the owner wants fewer alerts in the woods. Untested either way.

**Q4. "Or near dusk" (-445).**
- **(a) The sundown alerts already are the dusk alert. Recommended.** They fire on or off a trail and override silence (1.6). Nothing new is added.
- (b) One extra off-trail alert once the leave-by time passes while off the trail. It adds a fourth alert near sunset.

**Q5. The way home's note at 100 m walked (Sorrel's L-394-1).** A refused route at 100 m walked gives no note; at 101 m it gives "This route leaves your downloaded area…" (`domain/wayhome/FindWayHomeUseCase.kt:98-107`). The dispatch lists it as a known input, and it is not an off-trail behaviour.
- **(a) Leave it until a walk shows it. Recommended.** Both halves are owner rulings (-1589, -1613).
- (b) Silence the area note near the floor too, which needs a new figure.

**Q6. The wording,** 3.6: A, B or C, and the three notifications.

---

## 5. The proposed PRs, in order

**The modular-code skill's four questions** (`.claude/skills/modular-code/SKILL.md`, on pre-main), answered for each PR. Sizes are `wc -l` at `f5a0cfafc`.

**One file over ~800 lines, and not touched:** `ui/track/TrackRecordingViewModel.kt` is **864 lines**. That is down from the plan update's 1,029, and still over. No PR here touches it:
- The quiet state rides inside `WayHomeShown`.
- That is copied by `TrackRecordingWatchCopies.copyWayHome` (`ui/track/TrackRecordingWatchCopies.kt:89-92`).
- The ViewModel already collects it (`TrackRecordingViewModel.kt:368`).

If a build finds it must touch the ViewModel, that is a stop. `MainActivity.kt` is 736 lines and `ui/availability/AvailabilityScreen.kt` is 1,023; neither is touched.

**Nothing here must stay in place for order reasons:**
- no new `Box` child over the map: the notice is the HUD's existing status line;
- no Back handler;
- no new effect order. The map's way-home layers already exist (`ui/map/OverlayLayerSetup.kt:87-91`); only their paint properties change.

### PR 1: the return's alert judges against the trail too (stops the false alarm)

- **Behaviour:** 3.2 step 4, and 3.4. The alert's line becomes every track walked before Return plus every trail part shown on this return.
- **Files:**
  - `domain/OffTrackJudge.kt` (127): takes several lines, and can be given one more mid-return without losing its timing.
  - `domain/ReturnWatch.kt` (344): one new constructor input, the trail parts shown for a track. It reads `WayHomeWatch.shown.value`, a `StateFlow` read that takes no lock, so the two watches cannot deadlock. That is inferred from `WayHomeWatch.kt:79-80`; a test is the check.
  - `AppContainer.kt` (502): one argument.
  - `res/values/strings.xml` (36): the gentle alert's text, once Q6 is ruled.
- **New files:** none.
- **Tests:**
  - A new `ReturnWatchWayHomeTest`. A walker on the trail part 80 m from the outbound path for 20 s gets **no** alert. It **fails first** on today's code: Hazel's case, in a test.
  - A walker 60 m from both lines alerts once.
  - `OffTrackJudgeTest` gains several-line cases.
  - `AndroidAlertDeliveryTest.kt:77` reads the string resource, so the text change needs no test change.
  - Revert check: put `:255` back to the path alone; the new test must fail, naming its own assertion.
- **Shares with step 6:** `AppContainer.kt` only, one line. Step 6's waypoint routing is wired there too, I expect.

### PR 2: "Re-center"

- **Behaviour:** 3.1.
- **Files:** `ui/availability/ReturnToRoutePill.kt` (76): the text, and the constant's name and doc. The test `AvailabilityScreenNavigationViewTest.kt` changes too: the text at `:233`, and the titles that say "Return to Route".
- **Tests:** fail first by changing `:233` to "Re-center" before the code. The existing real-touch tests in that class (`:225`, `:252`) are kept, so the area a finger reaches is still tested by coordinate touches (CLAUDE.md).
- **Shares with step 6:** none expected.

### PR 3: the quiet off-trail state and "Back on trail"

- **Behaviour:** 3.2 steps 2 and 3, and 3.3.
- **New file:** `domain/wayhome/WayHomeOffTrail.kt`, about 120 lines. Its one job: from the fixes, whether the walker is off the trail line, and "Back on trail" for its few seconds.
  - **"Off"** is more than `OFF_TRACK_LINE_METERS` plus accuracy, for `OFF_TRACK_HOLD_MILLIS`, from both the trail part and the dotted track part from the trailhead to the start. That way, walking your own track from the trailhead back to the car never reads "off trail".
  - **"Back on"** is within the line for `OFF_TRACK_REARM_MILLIS`.
  - It is pure, and its time is the fixes' own.
- **Files:**
  - `domain/wayhome/WayHomeWatch.kt` (250): calls it in `onFix`, and publishes it in `WayHomeShown`.
  - `ui/track/TrackRecordingUiState.kt` (203): a derived getter.
  - `ui/availability/NavigationReadout.kt` (239) and `NavigationHudText.kt` (73): the status line and its words.
  - `ui/map/RouteHomeLayers.kt` (273): the trail part's and track part's opacity while off.
  - `ui/availability/CompactMapMapSlot.kt` (141): passes the flag into `RouteOnMap`.
- **Tests:**
  - `WayHomeOffTrailTest` (pure): on, off at 15 s, back on at 10 s, network readings skipped, the trailhead-to-start part counts as on.
  - A `WayHomeWatch` test that the state is published.
  - A readout test for the full and short forms.
  - A layer test for the opacities.
  - Each fails first; one revert check per rule.
- **Shares with step 6:** `RouteHomeLayers.kt`, `CompactMapMapSlot.kt`, `NavigationReadout.kt`, `NavigationHudText.kt`, `WayHomeWatch.kt` and `TrackRecordingUiState.kt`. These are all likely, because step 6 draws a trail line to a waypoint and its HUD. **Sequenced after step 6.**
- **Also shares with Hazel's 2026-10-25-06** (the re-route gap): `WayHomeWatch.kt` and possibly `WayHomeProgress.kt` (175). Sequenced after it.

### PR 4 (needs Q1): the wrong-turn alert

- **If Q1 (a):** first, Aspen's Pi output and a Worker cut beside the routes (`server/pmtiles-worker/src/routing/`, `src/rd5/`). Then the app stores the names file beside the routes:
  - `data/routedata/RegionRouteStore.kt` (181), `RegionRouteFolders.kt` (98);
  - `domain/routedata/RegionRouteData.kt` (193).
- **New files:**
  - `domain/routing/NamedWays.kt`: the interface Forager owns. Its one job: the named way nearest a point, and the names along a line.
  - `domain/wayhome/WrongTurnJudge.kt`: the rule in 2.2, pure.
  - An implementation under `routing/` or `data/routedata/`.
- **Files:**
  - `domain/AlertDelivery.kt` (243): a new `AlertKind.WRONG_TURN`, with `overridesSilence = false` as off-track.
  - `alert/AndroidAlertDelivery.kt` (488): its notification on the off-track channel.
  - `res/values/strings.xml`.
  - `domain/ReturnWatch.kt`: one alert per stray, shared.
- **Tests:** a synthetic named trail beside the route for each row of 2.2's table, each failing first.
- **Shares with step 6:** whatever step 6 adds to `RegionRouteData.kt`, if anything; `AppContainer.kt`.

### After step 6: the waypoint route gets the same treatment

The owner's answer 6 is "the same treatment as the way home". Today a waypoint navigation can run with no recording, and then the recording service is not running, so no watch receives a fix (`service/RecordingWatches.kt:15-35` is the service's). Who feeds the off-trail state a fix then is step 6's design. That treatment is a PR 3b, written once Hazel's report says where the waypoint line lives. `domain/WaypointNavigation.kt:16` ("The off-track alert and the walked-path line belong to the return; neither runs meanwhile") changes there.

### The audit index

I have not added a row to `docs/audits/README.md`. It is a serialization point (CLAUDE.md), and the dispatch did not ask for one. The planner can add it.

---

## 6. Files shared with step 6, and with the other open dispatches

**Hazel's step 6 report is not pushed** (no `waypoint-trails-report` branch on the remote at 06:31Z). This list is from reading today's waypoint code, not from Hazel's file list. When Hazel's report lands, compare the two lists.

| File | Lines | Step 7 PR | Step 6 (expected) | Also |
|---|---|---|---|---|
| `AppContainer.kt` | 502 | 1, 4 | wiring a waypoint route | |
| `ui/map/RouteHomeLayers.kt` | 273 | 3 | the waypoint's line (today the straight line, `:93-97`, `:203`) | |
| `ui/availability/CompactMapMapSlot.kt` | 141 | 3 | `RouteOnMap(... waypointStraightLine ...)` at `:84` | |
| `ui/availability/NavigationReadout.kt` | 239 | 3 | the waypoint's distance by trail | |
| `ui/availability/NavigationHudText.kt` | 73 | 3 | possibly | |
| `domain/wayhome/WayHomeWatch.kt` | 250 | 3 | if reused for a waypoint | **2026-10-25-06** |
| `domain/wayhome/WayHomeProgress.kt` | 175 | 3 (read) | if reused | **2026-10-25-06** |
| `ui/track/TrackRecordingUiState.kt` | 203 | 3 | possibly | |
| `domain/WaypointNavigation.kt` | 94 | 3b | its step path | |
| `domain/routedata/RegionRouteData.kt` | 193 | 4 | unlikely | |

**Sorrel's 2026-10-25-07 (the straightened bends) matters to PR 3.** If the drawn trail part cuts a bend by more than about 40 m, a walker on the real trail would read "off trail" there. The S22 saw 516 m drawn against 553 m walked (RECORD -1630). The bend's greatest offset is not reported. PR 3 should wait for that report's answer to its question 3.

**The order I propose:**
1. Hazel's -06 and step 6.
2. PR 1, which needs only one `AppContainer.kt` line, so it can go before step 6's build if the planner sequences that one line.
3. PR 2, any time.
4. PR 3 after step 6 and -07.
5. PR 4 after Q1.

---

## 7. Device-only items (a green suite proves nothing about these)

1. **The thresholds on a real walk:** 40 m plus accuracy, 15 s and 10 s, under canopy, with the phone in a pocket. How often the quiet state flickers at a trail's edge.
2. **The dimmed trail and brightened track, by day and at night,** at the map chrome's rules. Whether the difference reads at a glance.
3. **The full and short notice at 360 dp and in landscape.** Hazel saw "Return to Route" sit over the HUD's distance figure in landscape (`2026-10-25-hazel-s22-way-home-2-report.md` section 3.8). Whether "Re-center", which is shorter, still does is device-only.
4. **The alert with the screen off,** silenced and not, with the Off-track reminder on and off.
5. **For PR 4:** whether names match the walker's real trail at junctions and crossings, and how far the names file's geometry sits from the routes'.

---

## 8. The four disclosure sections

### 8.1 Confirmed vs inferred

**Confirmed by reading, at `f5a0cfafc`:**
- every line cited in sections 1 and 2.1;
- that BRouter's `lookups.dat` has no `name` or `ref` key;
- that no off-trail, "Back on trail" or wrong-way string exists in `app/src/main`;
- that the Trail Map style labels paths from a `name` property;
- that `TrackRecordingViewModel.kt` is 864 lines;
- that `RETURN_TO_ROUTE_TEXT` has one reader in production and one text-matching test.

**Inferred, not observed:**
- that the stored tiles carry path names (from the style);
- that `ReturnWatch` can read `WayHomeWatch.shown.value` without a deadlock (from the code; a test checks it);
- that a free-text name cannot be added to BRouter's lookups (from the file's format);
- the names file's size;
- that Google Maps' button reads "Re-center" (the owner's words; I did not check Google Maps).

### 8.2 Could not determine

- **Hazel's step 6 file list.** The report was not pushed when I wrote this.
- **Whether path names are present in stored zoom-14 and zoom-15 tiles.** Reading a tile would need a decoder and a place, and I ran neither.
- **The greatest offset of the straightened bend** (Sorrel's -07).
- **Whether the quiet state and the 50 m re-route feel like one behaviour on a walk.** Device-only.
- **How a waypoint navigation with no recording gets its fixes.** That is step 6's.

### 8.3 Premises that were wrong, or need correcting

1. **"The off-trail notice reads 'Off trail · your track leads back'"** (plan update, question 5, `docs/plans/2026-10-21-trail-navigation-plan-update.md:61`). No such notice exists in the app (1.4). The words are -445's ruling, never built. The rename leaves "Return to Route" free, but there is no notice yet for it to replace.
2. **The trail names are "readable on the phone"** (plan update, question 8, `:68`). They are readable only through the map on screen (2.1). The alerts run in the background, where nothing can read them today.
3. **The plan named `TrackRecordingViewModel.kt` at 1,029 lines** (`:33`). It is 864 today. That is still over the limit, so the plan's split-first rule still applies if it is touched. No PR here touches it.
4. **The dispatch's plan file is on pre-main, not on `records-after-173`.** `docs/plans/2026-10-21-trail-navigation-plan-update.md` is not in this branch's tree, so I read it at `origin/pre-main`. This does not change anything; it is noted so the next reader knows where to look.
5. **The off-track notification's text is stale** (1.1). It describes the rule -425 retired. No document had recorded this.

### 8.4 Decided beyond scope, or left for the owner

**Left for the owner:** Q1 to Q6 (section 4), and the wording (3.6).

**My choices, which shape the step paths and are stated so they can be overruled:**
- **What "far from your track" is measured against:** every track walked before Return and every trail line shown on this return. Not the re-routed line's dotted part, which starts where the walker is and would never read "far" (1.3).
- **The dotted track from the trailhead to the start counts as on the route.** Walking back to the car is never "off trail".
- **One alert per stray, shared** by the gentle alert and the wrong turn.
- **Existing figures reused rather than new ones:** 40 m, 15 s, 3 readings, 10 s, 25 m and 50 m. The one new figure is "Back on trail" for about 5 s.
- **The quiet state starts after the 15 s hold even at Return,** when the walker is already off the trail. This keeps one rule, rather than showing the state at once. It is the owner's to change.

**Ran on:** Opus (an app coder), per the dispatch.
