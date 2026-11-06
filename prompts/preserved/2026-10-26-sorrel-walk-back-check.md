# Sorrel's check of Juniper's walk-back reconcile (RECORD -1696)

Sorrel Tally (Fable), 2026-10-26, a fresh session. Read-only on code: no edits, no merges, no device, no Gradle, no network beyond GitHub. Branch `sorrel-walk-back` off pre-main a9c37eb3c; this file is its only change.

**Checked:** branch `walk-back-reconcile` @ ee943f549 (method committed first at c7e3250b3, 72 lines, no figure in it), `docs/navigation/2026-10-26-walk-back-reconcile.md` and its evidence folder (five scripts, four `.out`, two CSVs, three JSONs). Code read at pre-main a9c37eb3c: `SundownWatch.kt`, `ReturnWalkingTime.kt`, `PathHome.kt`, `TrackSelfJoin.kt`, `MovingPace.kt`, `ClimbAllowance.kt`, `DecideSundownAlertUseCase.kt`, `SundownLine.kt`, `NavigationReadout.kt`, `NetworkProviderFix.kt`. RECORD -1696, -1689, -1623, -1620, -1589, -1498 (records-after-173) and the 2026-09-07 retrace ruling (`docs/navigation/2026-09-07-ruling-path-home-self-intersection.md`). The owner's walk copy read with `python3 -I` from `/mnt/work/scratch/s26-hilly-walk/work/forager.db` (`mode=ro`); figures only here, no coordinates or times of day.

Each claim is marked **read** (file and line), **computed** (my own code, `/mnt/work/scratch/sorrel-walk-back/recompute.py`, `probe.py`, `fires39.py`, outputs beside them) or **inferred**. My ports are the ones I wrote for the #421 review (`/mnt/work/scratch/sorrel-421/recompute.py`), re-run here, not Juniper's `trackmath.py`; Juniper's positive controls were my #421 figures, so this is the independent side of those controls run again, plus everything the dispatch asks that neither of us had computed.

## 0. Baseline (read)

- `origin/walk-back-reconcile` = ee943f549; its two commits are c7e3250b3 (method and predictions, the `.md` only) then ee943f549 (results and the evidence folder). `origin/pre-main` = a9c37eb3c. Juniper's report names pre-main `cfef2f4cb` (#421 merged); the nine files read here are byte-identical between cfef2f4cb and a9c37eb3c (`git diff --stat` empty over them; 13 commits between the two touch other files), so the two reports read the same code.
- The copy: A `9685146a…` 457 stored / 457 kept / 0 excluded; B `cad94737…` 678 / 677 / 1, by the read seam's whole-second rule (`NetworkProviderFix.kt:54`). Both carry an origin waypoint. Juniper's counts are the same, so the check below ran on the same sample as the report, and that sample is the whole of both recordings.

## 1. Mechanism prediction, written before computing (`/mnt/work/scratch/sorrel-walk-back/prediction.md`), scored

- S1 far-point figures reproduce: **right**, every one (section 2).
- S2 the identity is algebraic, residual 0 says nothing about the walk: **right** (max residual 5.7e-14).
- S3 the 15 m arrival is the app's own constant and the ratio is insensitive to the radius; the whole 0.64 → 0.99 move is the reading "to arrival" against "to end": **right**, and the excursion after arrival is larger than the report says (section 3, Q3).
- S4 O1's detector fires at some kept point of A's homeward phase, or on another out-and-back in the copy: **wrong on A** (0 fires at 457 points: Juniper's "on A at none" holds at every point, not only at marks), **right on the copy** (25 fires on a 39.5 min out-and-back).
- S5 the straight-line floor reads short wherever k < walked ÷ straight: **right** (the river shape, section 3, Q5).
- S6 "never short" holds for the line, not for the walker's return; A's 459 m against 671 m walked is the counter-example, cushioned by the sub-floor lean: **right**.
- S7 on B the leave-by moment moves ~21 min earlier while the clock advances 39, so ~60 min of slack is consumed and LEAVE_BY can fire near the car: **right** (59.9 min; with 45 min of slack at the far point it fires 387 m from the car).

## 2. The headline figures, recomputed (computed)

| figure | Juniper | mine | |
|---|---|---|---|
| A far point: index, minutes in | 264, 88.5 | 264, 88.5 | match |
| A route home, joins; retrace; straight | 459 m, 35; 1,466; 297 | 458.8 m, 35; 1,466; 297 | match |
| A pace | 0.874 Doppler, about | 0.874 Doppler, governing 15.9 min, about | match |
| A pace-only; climb/descent along route; allowance; with | 8:45; 22+0; 48 s; 9:33 | 524.9 s; 21.9/0.0; 48.2 s; 573.1 s = 9:33 | match |
| A far → end: elapsed, moving, path | 51:20, 14:51, 1,121 m | 51:20, 14:51, 1,121 m | match |
| A far → first arrival within 15 m: point, minutes, elapsed, moving, path | 388, 119.8, 31:17, 9:37, 671 m | 388, 119.8, 31:17, 9:37, 671 m | match |
| A pace-only ÷ moving to arrival; with allowance ÷ moving | 0.91; 0.99 | 0.91; 1.00 (573.1/577 = 0.993) | match |
| A homeward points; share pace-only short to end; median | 192; 0.65; 0.765 | 192; 125/192 = 0.65; 0.76 | match |
| B far point: index, minutes in | 404, 91.5 | 404, 91.5 | match |
| B route home, joins; retrace; straight; origin leg | 1,956 m, 49; 2,206; 1,174; 9.2 m | 1,955.5 m, 49; 2,206; 1,174; 9.2 m | match |
| B pace-only (with origin leg); climb/descent; allowance; with | 37:56; 51+62; 247.4 s; 42:04 | 2,276.4 s = 37:56; 50.9/61.5; 247.4 s; 2,523.8 s = 42:04 | match |
| B far → end: elapsed, moving, path | 39:08, 24:49, 1,534 m | 39:08, 24:49, 1,534 m | match |
| B with ÷ moving; pace-only ÷ elapsed | 1.69; 0.97 | 1.69; 1.07 with, 0.97 pace-only | match |
| B at 349 m from the origin: point, route, with allowance; actual elapsed, moving | 124.7 min, 2,856 m, 57:37; 5:54, 5:14 | point 617, 124.7 min, 2,856 m (71 joins), 57:37; 5:54, 5:14 | match |
| B homeward points; share short; median | 272; 0.00; 3.80 | 272; 0/272; 3.80 | match |
| B nearest-outward: within 10 m, 50 m, median; A the same | 0.01, 0.17, 263 m; 0.29, 0.76, 26 m | 0.01, 0.17, 263 m; 0.29, 0.76, 26 m | match |
| ε sweep, B at the end: 10/15/20/30/50 m | 3,198/3,083/2,960/2,832/2,638 | the same five | match |
| ε sweep, A far point: 10/15/20/30/50 m | 459/421/396/327/308 | the same five | match |
| O4 pace = path so far ÷ moving time, A far; figure | 1.370 m/s; 5:35, 0.58 of 9:37 | 1,466 m / 1,070 s = 1.370; 459/1.370 = 335 s = 5:35 | arithmetic on reproduced figures |
| O5 A 9:33 × 4.96; B 42:04 × 2.66 | 47:23; 111:48 | 47:23; 111:52 (rounding) | arithmetic |

Every headline figure matches. **My controls:** ε = 0 gives the plain retrace on both (1,466 m and 2,206 m); the same route's heights banked the other way give a different total (A 24.6 m against 21.9; B 111.7 against 112.5), so the port is direction-sensitive. That control did its job: my first run banked the route in the outward order (a double reversal in my new code) and gave A 54.2 s, not 48.2; the Kotlin banks most-recent-first (`TrackSelfJoin.kt:153` returns the route `asReversed`, most recent first; `ClimbAllowance.kt:81` banks in that order), and the corrected run matches. Recorded here so the agreement above is read as a corrected run, not a first one.

## 3. The dispatch's questions, in order

**Q1. Recompute the headline figures.** All match (section 2). **Holds.**

**Q2. Test the identity.** `app ÷ moving = (route ÷ walked) × (walked ÷ moving metres) × ((moving metres ÷ moving s) ÷ pace)`: the right side cancels to `(route ÷ pace) ÷ moving s`, which is the left side, so it is exact for any route, any walk, and any pace; computed anyway at all 464 homeward points, max |residual| 5.7e-14 (computed). Juniper says it plainly ("an identity of ratios, so this is a check on the scripts, not on the walk", method §1). **Holds, and carries no evidence about the walk**; what carries evidence is the size of the three factors, which reproduce: A to arrival 0.68 × 1.50 × 0.89 = 0.91; B to the end 1.27 × 1.10 × 1.09 = 1.53 (computed). Note that the first factor includes the origin leg in mine and not in Juniper's (`reconcile.py:243` uses the route only, with the leg reported beside it); on A the leg is 0 m and on B 9 m, so the difference is in the third decimal.

**Q3. Is "first arrival within 15 m of the origin" chosen to fit?** No, with a limit. 15 m is `ARRIVAL_MIN_RADIUS_METERS` (`NavigationReadout.kt:71`), the app's own arrival radius, in the code before this report (read). The result is insensitive to it (computed): A far → first arrival within 10 / 15 / 20 / 25 / 30 / 40 / 50 m gives with-allowance ÷ moving of 0.99 / 1.00 / 1.04 / 1.05 / 1.06 / 1.08 / 1.11 (pace-only 0.90 to 1.00); the app's actual rule, `max(15, 2 × accuracy)` with the stored accuracy (≈ 9.9 m on this phone, so ≈ 20 m), lands at point 385, 119.5 min, 1.04; the point of closest approach (2 m, 123.7 min) gives 0.98. Any radius from 10 to 50 m says the same thing. **The one choice the result does depend on is "to arrival" against "to the end", and that is a reading of what the walker was doing, not a measurement.** After first arrival the walker did not potter "around the start": they went back out to **169 m** from the origin and came back inside 15 m at 131.1 min (computed; Juniper's 5-min table shows 84 m at the 130.0 mark, which is a sample, not the maximum). Measured to that last entry the ratio is 0.67 with the allowance; to the end 0.65. So: the figure was right as a walking time to the car the first time the walker reached it; whether the 169 m excursion afterwards was "the return" is not in the data. Juniper reports the before-and-after openly (§2 "Corrections", §6 "Premises that were wrong"), which is the right form. **Holds with limits:** the limit is the excursion's size, which the report understates.

**Q4. O1's loop detector: false fires on an out-and-back with a detour.** Juniper's detector (the straight line fell by more than 100 m over 10 min while the route home did not fall) at **every kept point**, not only at marks (computed, `recompute.py`, `fires39.py`, `probe.py`):

- A: 0 fires at 457 points. B: 219 fires, all homeward, from 97.7 min (6 min after the far point, 954 m out). Juniper's marks-only readings hold everywhere.
- The copy's other walks of 20 min or more with a homeward half: a **39.5 min out-and-back** (ends 14 m from its start; 27 % of homeward points within 10 m of the way out, median nearest 43 m) fires **25 times**, from 16.4 to 19.2 min in, at 160 to 230 m from the start and 130 to 145 m from any outward point. Not a loop: a walker approaching the car off their own track while still foraging (the recording ran 20 more minutes). A 20.3 min track fires 3 times, but it moves at 11 m/s (a vehicle, not a walk; Juniper's survey excluded it by duration). The 68.6, 24.7 and 24.5 min walks: 0 fires.
- Synthetic shapes (flat ground, 0.9 m/s, a point every 8 s): a plain out-and-back retrace fires 0 times (the negative control); an out-and-back whose way home runs **30 m beside the track for 800 m** (a hedge, the other side of a field) fires at 71 points for the whole detour. There the floor at k = 1.0 is right within 2 % and the as-built figure reads 1.7× to 3.7× long, so that false fire is harmless to safety and the floor even helps.

**Holds with limits for Juniper's claim** (A never fires), **does not hold for O1 as a loop detector:** it cannot tell a loop closing from a walker nearing the car off their track, and it fires on a real walk of the owner's that is not a loop.

**Q5. O1's straight-line floor reading short.** The river shape (computed, `probe.py`): out 1,000 m along a river's near bank, car at the start; back along the far bank 100 m away, having crossed at a bridge at the far end, the only crossing. The detector fires for the whole way back along the far bank. Opposite the car the walker is 100 m away in a straight line and 2,094 m on foot (back to the bridge, over, and home along the track). **As built the figure reads 38:35 against a true walk of 38:46 (1.11 at the start of the far bank, 1.00 by its end): right, by the retrace ruling's own construction.** The floor at k = 1.3 reads 17:28 → 8:58 → 2:25 where the true walk is 25:34 → 32:14 → 38:46: **0.68 → 0.28 → 0.06 of the truth.** No k guards it: the k that would is the walked ÷ straight ratio, 21 at the end, and it changes every step. On the owner's own data: at A's far point walked ÷ straight is 671 ÷ 297 = 2.26 on open ground; the straight-line figure at k = 1.3 is short of the actual moving time to first arrival at 73 of A's 124 homeward points before it, and at k = 2.3 at none (computed); on B at k = 1.3 it is short at 0 of 272 and at k = 1.0 at 100 of 218 fire points (worst 0.93). **Does not hold as safe:** Juniper's own words are right ("the retrace ruling's own case; k is the only guard"), and the probe shows the guard is not one.

**Q6. Does "never short" hold for a walk shape the app cannot see, a walker going home a longer way than the track?** No, and the data says so. "Never short" is a property of the figure against the recorded line (`PathHome.kt:15-24`, the most-recent-point rule; `TrackSelfJoin.kt:41-47`, no edge crosses more than ε of unwalked ground). Against the walk the walker then takes it is not a property: on A the stitched route was 459 m where the walker walked 671 m to the car (route ÷ walked 0.68), and the figure landed at 0.99 only because the walker's 671 m held 224 m inside intervals slower than 0.5 m/s (walked ÷ moving metres 1.50), which the path counts and the pace does not. That lean is a side-effect of the moving floor (`MovingPace.kt:41-48`), not a designed cushion; on B's brisker way home it is 1.06 (median over the homeward phase) and on a brisk straight walk home it is 1.0. So a walker who comes home a longer way than the stitched route **and walks it briskly** reads short by the route shortfall. Shapes that produce it: a patch worked and then walked home through (A, 0.68); an obstacle that has appeared since the way out (the track is the retrace, the walker must go round: the river shape run the other way); a walker who wanders home. The copy's 39.5 min walk shows the shape without the return: at 16.4 min the stitched route was 510 m and the walker walked 1,265 m more before the recording ended 14 m from the start (as-built figure 0.40 of that moving time), but that walker was still foraging, so it is not a return and not a counter-example in the strict sense. **"Never short on either" holds as a statement about these two returns (A 0.99 with the allowance, 0.91 pace-only, to first arrival; B 1.69) and does not hold as a property**; Juniper's §5 says as much ("the one thing not in the data"). What I would add: the one with-allowance figure that landed right (A) did so on a 1.50 lean over a 0.68 route, and the 0.91 pace-only figure (before #421) was 9 % short of the walking time to the car on the same return. Juniper's O0 line "Short risk: none seen" should read "none material", with the cushion named.

**Q7. Is O0's long reading near the car a state a walker could misread as broken?** Yes (computed). On B the with-allowance figure grows from 42:04 at the far point to 62:51 at the end (+20.8 min) while the clock advances 39.1 min, so the start-back-by moment moves 20.8 min earlier while the walker walks 39 min toward the car: 59.9 min of slack consumed by a walk home that actually took 39. The alerts are edge-triggered on that moving moment (`DecideSundownAlertUseCase.kt:72-84`, `SundownLine.kt:66-67`): with **45 min of slack** to start-back-by at the far point, LEAVE_BY fires 31 min later, **387 m from the car** (figure then 56:30), 8 min before the walker arrived; with 30 min of slack, 23 min later at 608 m; with 60 min or more it does not fire before the end. Return not tapped, nothing suppresses it (`SundownWatch.kt:294` needs `isReturning`). And the line's "start back by" recedes toward now the whole way home, which is the opposite of what a walker nearing the car expects. On A (the figure falls) the same test fires only with 30 min of slack, 216 m from the car, and that one is the stops the margin exists for. A walker told to start back when they can see their car, by a time that has been creeping earlier as they approached, has every reason to think the app is broken; it is "don't make me think" at its worst case (CLAUDE.md UX defaults). **Holds**, and it is a state, not a figure: it needs the walker to be within about an hour of start-back-by at the far point of a loop, which on a sundown walk is the ordinary case.

## 4. Verdict per finding and option

| | verdict | ground |
|---|---|---|
| A was right to first arrival: 9:33 against 9:37 over 671 m | **holds with limits** | reproduced exactly; insensitive to the radius 10 to 50 m (0.99 to 1.11); the limit is the 169 m excursion after arrival, under which the figure was 0.65 to 0.67 |
| The earlier "short" read was the end-of-recording artefact | **holds with limits** | same figures; "artefact" is a reading of the excursion, which the data cannot settle; both earlier findings did measure to the end |
| B is a loop; the route home grows as it closes; 42:04 at the far point against 24:49 walking; 57:37 at 349 m against 5:54 | **holds** | every figure reproduced; 1 % of homeward points within 10 m of the way out |
| The exact three-factor identity | **holds** | algebraic; residual 5.7e-14; evidence is in the factors, not the identity |
| Never short on either | **holds with limits** | true of these two returns with the allowance (0.99, 1.69); pace-only was 0.91 to arrival; the cushion is the slow-metres lean, accidental; not a property of the walk |
| O0 keep what is built | **holds** | figures right; the loop reading near the car is a real misread state (Q7), to be recorded, not a reason to change the figure |
| O1 loop detector + straight-line floor, labelled | **does not hold** | fires on a real non-loop walk of the owner's (25 times); the floor reads 0.68 → 0.06 of the true walk in the river shape and no k guards it; reopens 2026-09-07 and -1623 |
| O2 Return, the way home shown | **holds (read)** | `SundownWatch.kt:302-303`; whether trails exist on B's way home is not in the copy; it is the only built thing that covers the loop |
| O3 raise ε, ruled out | **holds** | ε sweep reproduced; no ε up to 50 m joins a loop |
| O4 remove the lean, ruled out | **holds** | 5:35 against 9:37 on A (0.58): confidently short |
| O5 stop allowance, ruled out | **holds** | +51 % and +186 % against elapsed |

**What I would recommend to Rowan:** O0 as built with O2 as the loop case's cover, and the Q7 state recorded as a known behaviour of the sundown line on a loop (a RECORD entry, and a line in `PathHome.kt`'s or `SundownWatch.kt`'s doc) so the next person who sees "start back by" creep earlier near the car finds the reason. Not O1. One observation beyond the dispatch, inferred and not priced: the detector is a sound signal of "the track is no longer this walker's way home" even though its floor is unsafe; the one use of it that keeps the never-short rule is to prompt Return (the way home shown, O2), never to change the figure.

## 5. Blocking, should-fix, notes

**Blocking:** none. The report's figures hold and its method was committed before them.

**Should-fix (in Juniper's report, wording only):**
- S1. §2 "Corrections" 1: "within 10 m of the start at 119.8 min" should be 15 m (first within 10 m is 123.6 min, computed); the table and `arrival.out` say 15 m.
- S2. "450 m of walking … around the start" understates: the walker went back out to 169 m from the origin before the last entry within 15 m at 131.1 min. State it, so a reader can weigh the to-end reading for themselves.
- S3. O0 "Short risk: none seen" → "none material": pace-only 0.91 and with-allowance 0.99 to first arrival, cushioned by the 1.50 slow-metres lean on a 0.68 route shortfall.

**Notes:**
- N1. O1's detector at every kept point of A: 0 fires (stronger than the marks-only claim); on B it first fires 6 min after the far point.
- N2. The 39.5 min walk's 25 fires are the false-fire case in the owner's own data; the 20.3 min track moves at 11 m/s and is not a walk.
- N3. Synthetic probes: plain retrace 0 fires; parallel detour fires, floor right, as-built 1.7 to 3.7× long; river shape fires, as-built right (1.00 to 1.11), floor 0.68 to 0.06.
- N4. My port's banking order was wrong on the first run and caught by the direction control (section 2); not a finding about Juniper's work.
- N5. The "at least" label: A's governing moving time at the far point is 15.9 min and B's 31.1 (computed), consistent with Juniper's switch times of 86.7 and 57.2 min (read, not recomputed).
- N6. Juniper's loop survey counts 7 walks with a homeward half; mine counts the same 7 plus the vehicle track; the 23.6 min walk's stored points span 273 min while its kept points span 23.6 (the difference lies in points the read seam excludes; not examined further).

## 6. One-line answers

- Do the headline figures hold? **Yes, every one, from independent code.**
- Does the identity hold? **Yes, by algebra; it is a check on the scripts, as Juniper says.**
- Is 15 m chosen to fit? **No: the app's own constant, and any radius 10 to 50 m gives 0.99 to 1.11; the fit-sensitive choice is arrival against end, which the report shows both ways but understates (169 m).**
- Does O1's detector false-fire? **Yes, on a real out-and-back of the owner's (25 fires) and on a parallel detour; not on A or a plain retrace.**
- Does O1's floor read short? **Yes: 0.68 to 0.06 of the true walk with a river between walker and car; no k guards it.**
- Does "never short" hold for shapes the app cannot see? **No; it holds for the line. A is the instance, saved by an accidental lean.**
- Is O0's reading near the car misreadable as broken? **Yes: on B, LEAVE_BY fires 387 m from the car with 45 min of slack at the far point, and the start-back time creeps earlier the whole way home.**
- Recommendation: **O0 + O2, record the loop state; not O1.** Which option, if any, is built is the owner's decision.

## 7. Disclosure

**Read.** Everything in section 0; `SundownWatch.kt:239-351, 294, 302-309, 425-426`; `ReturnWalkingTime.kt:62-117`; `PathHome.kt:15-24, 72-86, 106-125, 175`; `TrackSelfJoin.kt:32, 41-47, 78-154`; `MovingPace.kt:101-206, 255, 278, 290, 299`; `ClimbAllowance.kt:46, 60-63, 79-112`; `DecideSundownAlertUseCase.kt:1-84`; `SundownLine.kt:66-67`; `NavigationReadout.kt:57-71`; `NetworkProviderFix.kt:54`; Juniper's five scripts and four `.out` files; my #421 review's N5.

**Computed.** Sections 2 and 3 from `recompute.py` (both recordings, every kept point, the arrival sensitivity, the detector, the slack and alert timing, the loop survey), `probe.py` (three synthetic shapes, the ε sweep) and `fires39.py` (the 39.5 min walk's fire points), all under `/mnt/work/scratch/sorrel-walk-back/`, outputs `recompute.out`, `probe.out`, `fires39.out`, `recompute.json`. The sample each ran on is the whole of both recordings (457 and 677 kept points, 192 and 272 homeward), matching the source.

**Inferred.** That the 169 m excursion was or was not part of "the return"; that a walker reading "start back by" creeping earlier near the car would read it as broken (from CLAUDE.md's "don't make me think", not from a user); that the 20.3 min track is a vehicle (11 m/s); the detector-as-Return-prompt observation.

**Could not determine.** Whether Return was tapped on either recording; whether trails exist along B's way home; what the owner does when actually heading home under the alert; the phone's hop at a tick (0 by construction in both analyses).

**My own revert-shaped checks.** ε = 0 reproduces the retrace on both recordings; the banking direction control changes A's allowance (21.9 against 24.6 m) and caught my own first-run error; the plain-retrace synthetic shape gives the detector 0 fires. No Gradle test was run or reverted: this is a check of a research report, and the code under it was reviewed in `2026-10-26-sorrel-421-review.md`.

**Decided beyond scope.** Running the detector at every kept point and over the copy's other walks; the three synthetic shapes; the alert-timing computation (Q7) at four slack values; cutting the branch from pre-main a9c37eb3c rather than from `walk-back-reconcile`. Nothing on the phone or in the copy was changed; scratch outputs stay under `/mnt/work/scratch/sorrel-walk-back/`.
