# Review of PR #415 (step 7 PR 3: the quiet off-trail state and "Back on trail"), Sorrel Tally, 2026-10-26

Written 2026-10-26T18:47Z on Fable (the reviewer's model; the coder is Opus). A fresh session; read-only on code; no device, and Hazel's S22 results were not read. Against dispatch `prompts/preserved/2026-10-26-13.md` with Amendment 1 (branch `records-after-173`), RECORD -1631, -1674, -1676, the step 7 report `docs/audits/2026-10-25-04-off-trail-and-wrong-turn-report.md` (branch `off-trail-report`) §3.2–3.3, §5 PR 3 and §8.4, and my reviews of #399/#400, #398/#401/#402, #402 Amendment 2 and #406.

Every claim below is marked **read** (from the file at the commit named), **computed** (by my own code or arithmetic, from the artifact named) or **inferred** (from the code, not exercised).

## Baseline (read, computed)

- PR #415: `step7-quiet-off-trail` → `pre-main`, head `cc9eeebd3`, three commits over `c91f34c49` (`779d79432` fail-first, `0a14357a5` build, `cc9eeebd3` rig fix). `git merge-base origin/pre-main origin/step7-quiet-off-trail` = `c91f34c49` = `origin/pre-main` at 18:1xZ, so the branch sits on the current base and the green run built exactly what would merge. GitHub: `MERGEABLE`, `CLEAN`. 15 files, +738/−34 (`git diff --stat`, matches the PR's figures).
- Runs: fail-first `40371798291` (filtered: 22 tests in the 5 new classes, 13 failed); first PR run `40372890957` (5,678 tests, 1 failure, 24 skipped); green `40375884163` (5,678 tests, 0 failures, 24 skipped). Counts are mine, parsed from each run's `unit-test-report` artifact (771 `TEST-*.xml` files in each full run), not the CI summary. The dispatch's 5,678 matches.
- The 15 revert runs: all on scratch branches `revert-s7p3-r*` (since deleted; the commits fetched by SHA and diffed against their parents). r1–r11 and r13 are one-line edits on `0a14357a5`; r12b and r14 on `cc9eeebd3`. Every one produced JUnit XML with executed tests, so every reverted build compiled (a compile failure in a fresh CI checkout leaves no XML).

## Mechanism prediction (written from the revert diffs before the XML was parsed; scored in §4)

For each revert I wrote the test and message I expected it to produce, and whether `TrackRecordingOffTrailTest` would also fail for the rig fault (its first-fix origin) on the `0a14357a5` parents: r1 "still on at 14 s" and "15 s from the new start"; r2 "still off at 9 s back on"; r3 "gone at 5 s"; r4 "network readings off the trail never turn it off"; r5 the watch's trailhead-to-start case; r6 the watch's "published, once" and the rig test failing at the state (`OFF`/`ON`), not the status line; r7 "still quiet on the new line"; r8 "no trail shown, so no off-trail state"; r9 "a second Return starts on"; r10 `0.4` vs `0.24`; r11 the dotted layer `1.0` vs `0.6`; r12 masked by the rig fault, hence r12b "the trail line dims"; r13 the forms list short one entry; r14 the notice `""` on both readout and rig tests, with the green run on the same parent as the control.

## 1. The step paths through the code (read)

**15 s off, at least 3 readings; 10 s back on; "Back on trail" 5 s; all on the fixes' clock.** `WayHomeOffTrail.next` (`domain/wayhome/WayHomeOffTrail.kt:65-96` at `cc9eeebd3`): `t = reading.timestampEpochMillis` (`:69`); off when further than `OFF_TRACK_LINE_METERS` plus accuracy from every line (`:74-76`), turning `OFF` once `t - since >= OFF_TRACK_HOLD_MILLIS && offReadings >= OFF_TRACK_MIN_READINGS` (`:80`); back to `BACK_ON` after `OFF_TRACK_REARM_MILLIS` within a line (`:89`), a reading off in between clearing `onSinceMillis` (`:77`); `BACK_ON` → `ON` on the first counting reading `BACK_ON_TRAIL_SHOW_MILLIS` after it began (`:70-73`). The figures are the alert's own (`OffTrackJudge.kt:8,11,14,17`: 40.0 m, 15 000, 3, 10 000) plus the one new provisional `BACK_ON_TRAIL_SHOW_MILLIS = 5_000L` (`WayHomeOffTrail.kt:31`), as Amendment 1 accepted. Network and unknown providers skipped (`:66-68`), as `OffTrackJudge.next` does (`:85-86`).

**trackToStart counts as on.** `WayHomeWatch.onFix` passes `listOf(wayHome.trail.points, wayHome.trackToStart.points)` (`WayHomeWatch.kt:171`); `trackToStart` is the walker's track from where they first joined the trail back to the first stored point and the origin (`WayHome.kt:27-29`). The dotted `trackBack` (the stray back to the trail) is deliberately not passed, so a walker following their own stray back reads "Off trail · Return to Route along your track" until they reach the trail — the notice's meaning.

**Quiet.** Neither `WayHomeOffTrail.kt` nor `WayHomeWatch.kt` references `AlertDelivery`, vibration or a notification (grep of every touched main file: the only hits are `MainActivity`'s unrelated notification-permission comments and two doc comments). The one alert producer, `ReturnWatch.kt`, is not in the diff. The rig test asserts `alerts == emptyList()` through a real `ReturnWatch` and shows the rig can see an alert (its positive control, 1 alert at 300 m from everything). See S-415-1 on what that assertion covers.

**Wording.** `OFF_TRAIL_TEXT = "Off trail · Return to Route along your track"`, `OFF_TRAIL_SHORT_TEXT = "Return to Route"`, `BACK_ON_TRAIL_TEXT = "Back on trail"` (`NavigationHudText.kt:80-84`): -1631's words exactly.

**Entry point.** `TrackRecordingOffTrailTest` goes through `TrackRecordingViewModel.startReturn` (`TrackRecordingViewModel.kt:736-760` → `wayHomeWatch?.find`, `:752`) with a real `ReturnWatch` and `WayHomeWatch`, fixes to both in the service's order (`TrackRecordingService.kt:224` return, `:240` way home), and asserts on `navigationReadout(returnRouteOf(...))` as `MainActivity.kt:674` builds it and on `routeLineShown` as `:675` passes it.

## 2. The alert (OffTrackJudge in ReturnWatch) and the quiet state side by side (read; the cases inferred)

Both use the same limit, hold, reading count and re-arm. They differ in what counts as "on":

| Counts as on | Alert (`ReturnWatch.onFix`, `:279-281`) | Quiet state (`WayHomeWatch.onFix:171`) |
|---|---|---|
| Path walked before Return | yes (`listOf(start) + pathAtReturn`) | only the part that is `trackToStart` |
| Trail parts | every trail part shown this return, kept (`trailsShown`, `:361-370`) | the current line's trail only |
| Dotted `trackBack` | while heading back along it (`isHeadingBackAlong`, #402/#406) | never |

What follows, each a design consequence rather than a defect, listed so the owner's walk can recognise them:

- **Consistent, and the designed reading.** On the outbound track off the trail: notice "Off trail", Return button not red, no alert. On the trail or `trackToStart`: both on. Far from both: notice at the same fix as the alert (same hold, when both are counting). Following the dotted stray back toward the trail: notice stays "Off trail", alert silent by the heading-back rule. Straying on past the re-route point: alert after 15 s, notice already showing.
- **N-415-1 (low).** After a re-route onto a *different* trail, a walker on the old trail part reads **on** to the alert (kept in `trailsShown`) and **off** to the quiet state (the old line is no longer shown): the HUD says "Off trail", the new trail dims, nothing alerts. Correct about what is drawn; recorded because it is the one place the two can disagree while the walker stands on something the app once drew.
- **N-415-2 (low).** The alert's judge is built at the first fix after Return (`ReturnWatch.kt:281`); the quiet state counts only once a line is shown (`WayHomeWatch.onFix:153` returns while `shownNow.line == null`). A walker far from both at Return can be alerted up to the routing time before "Off trail" appears. `WayHomeOffTrail.kt`'s "At Return too … after the same hold" is true from the line's publish, not from the tap.
- **N-415-3 (low).** The quiet state skips a fix over `LIVE_FIX_MAX_ACCURACY_METERS` (`WayHomeWatch.onFix:155`); the alert judges it with the limit widened by its accuracy (`OffTrackJudge.kt:93`). A poor fix can read "on" to the alert and restart its hold while the quiet hold runs on. The disagreement is quiet-OFF with the alert held, which costs nothing audible.

Lock order (read): `ReturnWatch.onFix` holds its lock and reads `WayHomeWatch.shown.value` (no lock) and `progressFor` (that watch's lock) via `shownTrailOf` (`WayHomeOnCourse.kt:60-63`); `WayHomeWatch.onFix` holds its lock and calls `isReturning`, a `StateFlow` read (`AppContainer.kt:459`). This PR adds under `WayHomeWatch`'s lock only `offTrail.next` (pure) and a `_shown.value` write. No new path takes the return watch's lock from inside the way-home watch's. Unchanged order, nothing new to deadlock.

## 3. State across events (read; process death and arrival inferred)

- **Re-route:** the job's end publishes `WayHomeShown(id, mine, result, line, offTrail.state)` (`WayHomeWatch.kt:230`); the state carries. Test: "a re-route carries the state onto the new line", bitten by r7.
- **Fallback to the track:** `if (line == null) offTrail.reset()` (`:229`); the UI getter also reads `ON` when `line == null` (`TrackRecordingUiState.kt:201`), so the fallback is double-guarded. Bitten by r8 at the watch.
- **Second Return:** `find` → `resetProgress` → `offTrail.reset()` (`:114-119`, `:249-255`); the first publish of a return is `WayHomeShown(id, mine, null)` with the default `ON` (`:203`). Bitten by r9.
- **Stop:** `stop`/`end` → `forget` → `resetProgress` and `_shown.value = null` (`:123-133`, `:258-267`). Not tested directly; with `shown` null nothing can read a state.
- **Process death (inferred):** both watches live in `AppContainer` for the process; a reopened screen does not adopt a running return (`TrackRecordingViewModel.kt:430-435`), so no way home is shown and `wayHomeOffTrail` is `ON` by `wayHome?.… ?: ON`. The state cannot outlive the return it belongs to. No test; none practical in a unit suite.
- **Arrival (inferred):** the return ends in `ReturnWatch` (`returning = false`), so `WayHomeWatch.onFix` returns at `:154` and the state freezes at its last value until `stopReturn` calls `wayHomeWatch.stop` (`TrackRecordingViewModel.kt:789`). Meanwhile the status line's `arrived -> ""` outranks (`NavigationReadout.kt:176`) and the line ahead is not drawn while `arrivedAt != null` (`RouteHomeLayers.kt:140`), so a frozen `OFF` has no visible effect. **Waypoint pause (inferred):** `WayHomeWatch`'s doc says a waypoint pause cancels its work (`:45`); I did not trace the ViewModel's pause path.
- **Publishing only on change:** `if (state != shownNow.offTrail) _shown.value = shownNow.copy(offTrail = state)` (`:172`). The ViewModel re-runs `copyWayHome()` and `routeTicks.updateRouteHome()` on every `shown` emission (`TrackRecordingViewModel.kt:372`), which is why this matters. Test: the identity-count assertion ("published on the change only, not once a fix", 2 distinct objects over 22 reads). r6 removes the publish wholesale (count 1); an *unconditional* publish was not tried (N-415-6).

## 4. The map: lowest wins, the dotted track unchanged (read, computed)

`routeTrailOpacity` = `minOf(routeAheadOpacity(route), ROUTE_PASSED_OPACITY)` when `trailDimmed` (`RouteHomeLayers.kt:302-305`); `routeHomeLayerOpacities` gives it to `AHEAD_LAYER` and `AHEAD_CASING_LAYER`, and the undimmed `ahead` to `AHEAD_DOTTED_LAYER` and `AHEAD_STRAIGHT_LAYER` (`:308-318`). The solid layers' filter is `notDotted` (not track, not straight, `:233-236`), so they draw the trail part — and the plain one-feature line of a track-only way home, which has no part property. That plain line is never dimmed because `routeLineShown` sets the flag only when `aheadParts.any { it.kind == TRAIL }` (`TrackRecordingUiState.kt:208-212`). The flag is derived on read and never stored: `nextRouteLine` builds a fresh `RouteLine` for `Ahead` and copies `it.routeLine` (the stored, undimmed one) for `Withheld` (`RouteLine.kt:58-67`), so the 5 s tick cannot hold a stale flag, and the rig asserts `routeLine.trailDimmed == false` beside `routeLineShown.trailDimmed == true`.

Computed: withheld (0.4) and dimmed → 0.4 not 0.24 (r10 produced `0.24000001`); dimmed alone → 0.6 on the two solid layers, 1.0 on the dotted (r11 produced `0.6` where `1.0` was expected).

**N-415-4 (low, inferred, device).** The casing (`ROUTE_AHEAD_WIDTH_DP + 2 * CASING_WIDTH_DP`, `:241`) and the line are two layers both at 0.6; where the line covers the casing the two composite denser than 0.6 over the map, while the passed line, which has no casing, is one layer at 0.6. "60%, like passed route" is therefore the per-layer figure, as the withheld fade already was; whether the dimmed trail reads like the passed line is the device item the dispatch lists. Not the 80% map-chrome rule: a route line is not chrome.

## 5. The status line (read)

Outer `when (freshness)`: `LOST` and `STALE` own the line before anything route-related (`NavigationReadout.kt:169-171`); within `FRESH`: `arrived`, `approaching`, then `Pending`/`Unavailable` (the straight-line note, while the large slot reads "Unable to calculate route"), then a track-only note, then `OFF`, then `BACK_ON`, then the waypoint `Straight` (`:172-190`). A withheld route reaches `returnRouteOf` as `RouteHome.Withheld` → `ReturnRoute.Unavailable`, which carries no `offTrail` (`NavigationHudReadout.kt:126-130`), so "Unable to calculate route" is kept and the notice is not shown then — Amendment 1's ruling. A note and the notice cannot both be present: `wayHomeNote` exists only for `TrackOnly` (`TrackRecordingUiState.kt:198`) and `wayHomeOffTrail` is `ON` without a line (`:201`). Short forms: `OFF` → `["Return to Route"]` with no empty form, so never dropped silently; `BACK_ON` → none, which `statusLineFit` treats as "keep its shortest form and the lead goes" (`NavigationHudFit.kt:128-131`). The test "a stale fix keeps the line over the notice" passed on the fail-first run too (N-415-7).

## 6. `metersToPolyline` lifted unchanged (read)

The body at `OffTrackJudge.kt:123-139` is the former private `metersToLine` line for line (the flat projection about `p`, the clamped parameter `s`, `hypot`); the only other change in that file is the call at `:96`. `WayHomeOffTrail` imports it (`:10`). Both judges measure the same way, which is what keeps §2's table honest.

## 7. The two `MainActivity` lines (read)

`:674` `returnRouteOf(trackUiState.routeHome, trackUiState.wayHomeNote, trackUiState.wayHomeOffTrail)` and `:675` `routeLine = trackUiState.routeLineShown`. `returnRouteOf` has no other production caller, and every other `routeLine` reader (`AvailabilityScreen.kt:225`, `AvailabilityCompactScaffold.kt:374`, `CompactMapMapSlot.kt:69`) is downstream of `:675`; `trackUiState.routeLine` is read nowhere else. The step 7 report had the flag going through `CompactMapMapSlot.kt`; Amendment 1 accepted the `RouteLine` field instead, and `CompactMapMapSlot.kt` is untouched. `MainActivity.kt` stays 738 lines.

## 8. The evidence: fail-first, reverts, and the rig fault (computed)

**Fail-first** (`40371798291`, on `779d79432`, filtered to the five classes): 13 of 22 failed, each with the message a never-changing stub gives (`expected:<OFF> but was:<ON>`, `expected:<[Off trail · …]> but was:<[]>`, `expected:<0.6> but was:<1.0>`). The nine that passed over the stubs are the ones a stub cannot fail: "on the trail it stays on", the pure trailhead-to-start case, the watch's network and second-Return cases, the stale-outranks case, `returnRouteOf` carrying the state, "every line at full strength", **"lower of the two wins" (the stub returned 0.4 either way; r10 is its evidence)**, and the positive control.

**The rig fault.** The rig test failed on `0a14357a5` at the status line, `""` for the notice. The commit message's diagnosis checks out: the first fix was at the walker's place `(0, −50)`, so the origin waypoint was made there, the HUD's `arrived` branch returned `""` (`NavigationReadout.kt:177`), and the assertion saw an empty line. `cc9eeebd3` gives a first fix at the walk's start `(0, 120)`, asserts the origin is there, and the test passes on the green run with the suite count unchanged (5,678 = 5,678: the rig fix added no test).

**Reverts r1–r11, r13, cut from `0a14357a5`:** each full-rig run carries the rig fault's failure *in addition* to its own, and the coder's attribution has to separate them. I checked each message against what the edit could produce:

| Revert | Own failure (message specific to the edit) | Rig test's message | Could the edit produce the rig message? |
|---|---|---|---|
| r1 hold | "still on at 14 s expected ON was OFF"; "15 s from the new start…"; watch "published, once" at 14 s; "the hold starts again" | `""` for the notice | No: the state is `OFF` at 25 s under r1 as well; `""` is the arrived branch |
| r2 back-on | "still off at 9 s back on expected OFF was ON"; watch `BACK_ON`→`ON` at 10 s | `""` | No |
| r3 show | "gone at 5 s expected ON was BACK_ON"; watch likewise | `""` | No |
| r4 network | "network readings off the trail never turn it off expected [ON] was [ON, OFF]" | not in the run (filtered to the pure class) | — |
| r5 toStart | watch "walking the track from the trailhead to the start never reads off trail" | `""` | No |
| r6 publish | watch "published, once" and "back on"; **rig `expected:<OFF> but was:<ON>`**, at the state, before the status line | (replaced by r6's own) | r6's own message, not the rig's |
| r7 carry | "still quiet on the new line expected OFF was ON" | `""` | No (no re-route in the rig test) |
| r8 fallback | "no trail shown, so no off-trail state expected ON was OFF" | `""` | No |
| r9 new return | "a second Return starts on expected ON was OFF" | `""` | No |
| r10 lowest | `expected:<0.4> but was:<0.24000001>` | not in the run | — |
| r11 dotted | `expected:<1.0> but was:<0.6>` | not in the run | — |
| r12 dim line | **none of its own**: the rig fails at the status line before reaching "the trail line dims" | `""` | Masked; the coder re-ran as r12b |
| r13 short | forms `[full, "Return to Route"]` expected, `[full]` was | not in the run | — |

**r12b and r14, cut from `cc9eeebd3`:** r12b fails the rig test at "the trail line dims" (its own assertion, past the status line). r14 fails the readout test and the rig test, both with `""` for the notice — the same text as the rig fault's, which is why its parent matters: on `cc9eeebd3` the green run passes that assertion, so on r14 the `""` is the removed branch's.

**Scored against §0's predictions:** 15 of 15 as predicted, including the masking of r12 and the control relation for r14. The one message I had not predicted is r1's extra "the hold starts again expected ON was OFF": with the time term gone, three readings at 30–32 s turn the second return `OFF` before the test's 44 s — r1's own, and consistent.

On "stale output": in CI each run is a fresh checkout, so the stale-XML failure CLAUDE.md records cannot occur here; the risk in this set was the *masking* one (a parent fault failing the same test earlier), and it was recognised and re-run. The reverts cut from the build commit rather than the head is my 398-4 again; here it is what made r12b necessary, and the habit of cutting from the head would have avoided it.

## 9. Behaviours shown by neither a fail-first stub nor a revert (read)

The dispatch asked for one revert per rule. These are the behaviours in the diff that no red run names (the coder's own list of three was not in front of me; Rowan can set the two side by side):

1. **`OFF_TRACK_MIN_READINGS` in the quiet state** (`WayHomeOffTrail.kt:80`): every test feeds one reading a second, so the ≥3 term never decides. A case with two readings 15 s apart staying `ON` would show it.
2. **The two guards:** `wayHomeOffTrail`'s `line != null` (`TrackRecordingUiState.kt:201`) and `routeLineShown`'s "only when a `TRAIL` part exists" (`:210`). r12b bites the flag, not the guard; the fallback case covers the first guard only indirectly (the watch resets as well).
3. **The casing's dim** (`AHEAD_CASING_LAYER to trail`): the fail-first stub covers both solid layers at once; no revert separates the casing.
4. **"Back on trail" in the status line:** fail-first only (the stub left both branches out); r14 removes the `OFF` line alone.
5. **`BACK_ON` → `OFF` again** (off during the 5 s): the first lines of `next` handle it (`:70-73` then `:76-83`); no test walks it.
6. **Publish only on change, as a rule distinct from publishing at all** (§3).
7. **"A stale fix keeps the line over the notice"** passed identically before and after the change (the order is older than this PR). It guards against a later re-ordering, and should be cited as that, not as evidence this PR changed anything.

None of these is a defect found; each is a place a later reader should not cite a green run as proof.

## 10. Scope, stops, sizes (read, computed)

- Within the dispatch and Amendment 1: no new figure but the provisional 5 s; no alert, sound or vibration; the way home's route, figures and re-route untouched (`WAY_HOME_STRAY_METERS`, `judgeStray` unchanged); `WayHomeOffTrail` takes plain `List<List<LatLng>>` (step 6 PR 4 can reuse it); the waypoint route not wired; none of the 21 "Return to Route" comments touched; none of the tidy PR's files touched (moot since #414 merged, and still true).
- Sizes (`wc -l` at `cc9eeebd3`): new `WayHomeOffTrail.kt` 107, tests 102/128/62/50/162; `WayHomeWatch.kt` 262→284; `RouteHomeLayers.kt` 337→360; `OffTrackJudge.kt` 135→139; `TrackRecordingUiState.kt` 218→234; `NavigationReadout.kt` 243→249; `NavigationHudReadout.kt` 123→130; `NavigationHudText.kt` 84→94; `RouteLine.kt` 63→68; `MainActivity.kt` 738→738. No new file over 400; no touched file over 800; `TrackRecordingViewModel.kt` (868) not in the diff, as the step 7 report required.
- Commits carry `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name (read from `git show`).

## Verdict

**Blocking:** none.

**Should-fix (non-blocking; a one-line test change or a doc line, Rowan's call whether before merge):**

- **S-415-1.** `TrackRecordingOffTrailTest` builds `ReturnWatch(ComputeReturnToStartUseCase(), AlertDelivery { alerts += it })` without the `wayHomeTrail` input `AppContainer.kt:409` gives it, so in the rig the alert judges against the pre-Return path alone, not "as the service does" (its class doc). The "no alert" assertion holds because the walk's points `(0, −50)`, `(20, 0)` and `(40, 0)` all lie within the 45 m limit of that path — the last at 40.0 m, a 5 m margin (computed on `Ground`'s flat metres). The production claim is still true by reading (the trail counts as on there too), but the test proves less than its doc says and would turn red on a 6 m change to the walk. Either wire `wayHomeTrail = { id -> shownTrailOf(watch, id) }` into the rig, or say in the doc that the alert here sees the path only.

**Notes:** N-415-1 to N-415-4 above; and:

- **N-415-5.** `RETURN_TO_ROUTE_TEXT` (`ReturnToRoutePill.kt`) still names the constant whose value is now "Re-center", while `OFF_TRAIL_SHORT_TEXT` is the one whose value is "Return to Route" (my 400-1, now realised). For the later tidy the planner has already assigned the 21 comments to.
- **N-415-6.** The identity-count assertion pins "published once" against *no* publish (r6); an unconditional `copy` on every fix was not reverted (it would give 22 distinct objects and fail). Worth one revert when the class is next touched.
- **N-415-7.** The fail-first run was a filtered run (22 tests). Correct for a fail-first; the first full run on `0a14357a5` is where the suite count (5,678, one failure) comes from.
- **N-415-8.** Device items the dispatch lists stand as listed; add to them from this review: whether the dimmed trail with its casing reads as the passed line does (N-415-4), and whether the notice appearing after the alert on a far-from-both Return (N-415-2) reads as one event or two.

**Mergeable:** yes. The step paths of -1631 as amended by -1676 are in the code and each rule has a red run naming it; the state is carried, cleared and published as Amendment 1 lists; the quiet claim holds by reading of every touched file; the branch is on the current base and the green run built it there.

## What I did not do, and why

The reviewer profile's step 5 asks for a revert check of my own. I ran none: the session is read-only on code, builds are CI-only and the planner's to schedule, and a local Gradle run on this laptop is not mine to start. In its place I recomputed every run's results from its JUnit XML artifact with my own parser, predicted each revert's failure from its one-line diff before parsing, and scored the predictions (15 of 15). I did not run the app, read Hazel's S22 results, or look at any device output. Process death, arrival and the waypoint pause are inferred from the code, not exercised.

## Files read, for the record

At `cc9eeebd3` unless noted: the full PR diff; `WayHomeOffTrail.kt`, `WayHomeWatch.kt`, `OffTrackJudge.kt`, `ReturnWatch.kt` (`:100-140`, `:255-300`, `:355-398`), `WayHomeOnCourse.kt`, `WayHome.kt` (fields), `RouteLine.kt`, `NavigationReadout.kt` (`:140-215`), `NavigationHudReadout.kt`, `NavigationHudText.kt`, `NavigationHudFit.kt` (grep), `RouteHomeLayers.kt` (`:92-100`, `:135-200`, `:221-318`), `TrackRecordingUiState.kt`, `TrackRecordingWatchCopies.kt` (`:80-113`), `TrackRecordingViewModel.kt` (`:425-445`, `:560-572`, `:736-796`), `TrackRecordingRouteTicks.kt` (grep), `TrackRecordingService.kt` (grep), `RecordingWatches.kt`, `AppContainer.kt` (grep), `MainActivity.kt` (`:671-678`); the five new tests, `WayHomeLines.kt`, `WayHomeFakes.kt` (`:1-99`), `WayHomeTestRecording.kt`; `.github/workflows/ci.yml` (`:176-232`, `:463-484`); the 15 revert commits' diffs; the 18 runs' `unit-test-report` artifacts; dispatch 2026-10-26-13 and Amendment 1; RECORD -1631, -1674, -1676; the step 7 report §1, §3, §5, §7, §8; my four earlier reviews' findings.
