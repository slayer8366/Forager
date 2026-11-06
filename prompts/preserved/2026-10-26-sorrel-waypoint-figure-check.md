# Sorrel's check of Juniper's options for the waypoint route's HUD distance (RECORD -1670)

**Reviewer:** Sorrel Tally, on Claude Fable 5.1 (`claude-fable-5-1`, read from this session's environment). **Researcher checked:** Juniper Sounding (Fable). **Planner:** Rowan Waymark. **Owner's words this answers:** -1670, "Look into it first (Recommended)".
**Checked:** `prompts/preserved/2026-10-26-juniper-waypoint-figure-options.md` and `docs/navigation/evidence/2026-10-26-waypoint-figure-options/` (`00-method-and-predictions.md`, `waypoint_figure.py`, `waypoint_figure.out`, `waypoint_figure.before-o4-fallback.out`) on `origin/waypoint-figure-options` at `9bded1bae`; the method commit `ecdb2904f` precedes it, as the report says. Script sha256 `97707e24…354fe`, output `38a31da6…17a0d`. Branch base `c1deeef85` = `origin/pre-main`.
**Read from source:** `CLAUDE.md`; `docs/process/reviewer-profile.md` (pre-main); on `origin/records-after-173` RECORD -1613, -1618, -1620, -1623, -1630, -1634, -1636, -1670; my `prompts/preserved/2026-10-25-sorrel-rising-figure-options-review.md` (3598ae369); Hazel's `prompts/preserved/2026-10-26-hazel-413-s22-check-report.md` (bb016dd37). Code at `a00742ffb` (PR #413's head as reviewed; the branch has since moved one commit to `0662d533c`, Amendment 2's tests, and I re-checked the reader claim there), all under `app/src/main/java/com/zynergylabs/forager/app/`: `domain/wayhome/WayHomeProgress.kt` (whole), `WayHomeAhead.kt:1-50`, `domain/PathHome.kt:1-30, 100-173`, `domain/GeoDistance.kt`, `domain/NavigationReadout.kt:1-115`, `ui/availability/NavigationReadout.kt:80-217` (every use of `route`), `ui/availability/AvailabilityWaypointRoute.kt` (whole), `domain/wayto/FindWayToWaypointUseCase.kt:40-120`, `WayToWaypoint.kt`, `WayToStray.kt`, `CompactMapPortraitNavigationCard.kt:118-130`, `CompactMapLandscapeNavigationCard.kt:163-175`, `AppContainer.kt:476`, `SundownWatch.kt:126-130, 296-322`; the tests `WayHomeProgressTest.kt` (whole), `WayHomeFakes.kt:22-26`, `WayHomeLines.kt`. No Gradle, no phone, no app code touched. Clock at writing: 2026-10-26T13:06Z.
**My replica:** `/mnt/work/scratch/sorrel-waypoint-figure/mine/sorrel_check.py`, outside the repo (this check is my only write). Not a copy of Juniper's: geometry on a flat local plane in metres, own line fill, own place and figure function written from `WayHomeProgress.kt:30-67, 133-154, 170-180`, `PathHome.kt:123-134`, `domain/NavigationReadout.kt:67-71`. At under a kilometre the flat-plane and haversine distances differ by under a centimetre. Validated first against every literal `WayHomeProgressTest.kt` pins, the inequalities included (§2), and shown able to fail (§2, the controls). Juniper's script was also re-run in an isolated directory (`python3 -I`): its output is byte-identical to the committed `waypoint_figure.out`.

**Marks.** *Read* means read in the code or a document at the commit named; *computed* means from my replica or Juniper's output, re-derivable; *inferred* means reasoned from them.

## Mechanism prediction, written before my replica ran

1. Juniper's replica reproduces the pinned literals; its one fidelity gap will be in the place search's `previous` after an off-line fix, which touches O3 only.
2. O2 is never short for a walker **on the drawn line**, on any shape, by a triangle-inequality argument that does not use the geometry, so the sweeps confirm a theorem rather than probe one.
3. The 25 m's residual job, unstated in the code, is lateral: a walker who is truly beside the drawn trail, where "the node at or behind the walker is at most one step behind them" (`WayHomeProgress.kt:217-218`) stops being true. O2 will read short there by a few metres; O1 will not.
4. The headline figures (125/100/60 m at the trail's end; 115/90/40 m before "Arrived") reproduce.
5. The reader claim holds.

Scored in §9.

## Verdict

**Blocking:** none. The report is sound in shape and in its recommendation; every headline figure I recomputed matches within one node step; O3's rejection and O4's deferral are right and for the reasons given.

**Should-fix before the owner sees it:**
- **S-1.** "Never short for any fix the gate admits" is true of O1 and O2 alike only for a walker **on the drawn line**. The 25 m the report calls "a margin with no stated job" has a measurable job: on the trail part it keeps the figure never short for a walker up to about 33 m beside the drawn line (computed, §3), and it adds 25 m of tolerance to a true position error beyond the gate's 50 m (O1 first reads short at 80 m of forward error, O2 at 65 m; §3). Beside the trail O2 reads short from about 7 m out, by about half the offset (−2.5 m at 7.5 m, −5 at 10, −10 at 20, −20 at 40), in 0.1 to 2 % of admitted placements. Small, and the owner may well accept it, but it is the trade O2 makes and the report should name it rather than say there is none.
- **S-2.** The "Approaching" figures are read off the first *printed* row (s = 330), not the first row where the word holds (s ≈ 298). Correct: O1 **215 m (705 ft)**, O2 **190 m (623 ft)**, O4@4 **150 m (492 ft)**, not 175/150/120 (report §3, §4, §6; prediction P2 "held" on the misread). Hazel saw about 615 ft.
- **S-3.** On these synthetic shapes every 40 m stretch is 40.00000000003 m under the haversine, so Juniper's `ceil(g / 10)` fills it with 5 nodes at 8 m where exact arithmetic gives 4 at 10 m. That is why Juniper's U reads 494 m where mine reads 490, why the rows at s = 100, 200, 290 differ by one node (405/415, 305/315, 215/225), why the W sweep counts 60,843 placements against my 61,433, and why O4's worst short is −87 (node (350, 32) exists only in Juniper's line) against my −95. The app does the same `ceil` on its own haversine and could show either. No conclusion depends on it; the report should say its figures on these shapes carry ± one node step.

**Notes** (N-1 to N-8) are in §8.

**One-line answers.**
- Replica fidelity to `placeOnWayHome` at `a00742ffb`: **holds** (N-1, N-2, S-3 are the limits).
- O2's never-short claim: **holds with limits** — the walker on the drawn line, the true error within the gate's 50 m; beside the line, short by about half the offset from about 7 m out (S-1).
- Headline figures: **hold**, bar the "Approaching" figures (S-2) and one-node-step differences (S-3).
- O3: **does not hold** as a candidate — confirmed short in 1.4 % of W placements (−79 m), −479 m on U, and a 100 m cliff on a 100 m leg. Rejection right.
- O4: **holds as described** — never short only while the true error is within twice the reported accuracy; −95 on W and −480 on U in my sweep. Deferral to walk data right.
- Nothing but the HUD's large slot reads the waypoint figure: **holds**, read at `a00742ffb` and at `0662d533c`.
- Would I recommend O2 to the owner: **yes, for the waypoint route**, put as the trade in S-1. The way home: not on this evidence (N-8).

## 1. Baseline confirmed

Read. `origin/waypoint-figure-options` is `9bded1bae`, two commits on `c1deeef85` (= `origin/pre-main`): `ecdb2904f` (method and predictions, 41 lines) then `9bded1bae` (script, outputs, report). The code commit Juniper names, `a00742ffb`, is PR #413's head as I reviewed it; it is not in pre-main (#413 is unmerged) and the branch has one later commit, `0662d533c`. Juniper's script re-run from the committed source under `python3 -I` gives a byte-identical `waypoint_figure.out` (489 lines) in 76 s.

## 2. Juniper's replica against the app's code (read), and mine against the tests (computed)

**Line fill** (`WayHomeLine.init`, `:30-67`): identical in both replicas, including the detail that the last node of a part takes the next part's kind (`:54`), so the trail's last node is a STRAIGHT node and carries no allowance. **Place search** (`:137-143`): the four tiers, `<=`, and the hop band with hysteresis (`PathHome.kt:123-134`) identical. **Figure** (`:148-152`): `earliest(indices, 60) ?: index`, the allowance by the figure node's kind, `metersPastLineStart` when the figure node is node 0: identical. **Distance**: Juniper's haversine with the IUGG radius matches `GeoDistance.metersBetween` (`:34-47`); `metersPerDegree` matches `:177-180`; `Ground.at` matches `WayHomeFakes.kt:22-26`. **Arrival**: `max(15, 2 × accuracy)` matches `domain/NavigationReadout.kt:67-71`; "Approaching" 100 m matches `:88`.

**One fidelity gap, N-1.** After an off-line fix the app keeps `lastPlace` and sets the band FAR (`AvailabilityWaypointRoute.kt:140-141`); Juniper's `walk()` sets the previous index to `None`. That changes where the place search starts (`from`, `:137`) on the next fix, so it can move the needle's place; the figure under O1 and O2 ignores `previous` (`:148`), so only O3, which reads the place's kind, could be touched, and only after an off-line fix, which none of the on-line walks has. Immaterial to the verdict.

**A second, N-2.** The trail part's along-metres are the engine's `route.lengthMetres` spread over its points (`FindWayToWaypointUseCase.kt:84`; `WayHomeLine.init:44, :58`); both replicas use the geometric length (scale 1). Never short, for either option, is a statement relative to the line's own metres; neither covers a line shorter than the ground. The one measurement is -1636: the drawn trail 320 to 326 m against the map's 325.4 m, within half a node step over 325 m. Fine for both; noted because it is the kind of slack the 25 m silently covers.

**Juniper's validation** ran the sixteen equality literals and the five hand figures; all pass, and I confirm each (my replica: 570, 405, 605, 619.9, 575 to 615, 590, 570 ×4, OffLine at −50.1 and −60). It did not run the test file's **inequalities**, so I did: switchback (100, 12) ≥ 420 (495, and ≤ 420 + 60 + 25 + 10); place stays on leg 2 (index 35 ≥ 31); turned back ≥ 420; loop ≥ 785 (815); B-395-1 ≥ 420 (495); the double-back five (405, 455, 505, 550, 570 against 320 to 520); B-395-2 (a) 570 ≥ 568.9, (b) 60 ≥ 59, (e) 465 ≥ 419, (d) 515 ≥ 465. All pass.

**Controls, so the validation is known to bite.** A copy of my replica with the reach wrongly at 50 m fails five checks with the figures the test comments name: 395 against 405, 560 against 568.9, 50 against 59, 265 against 419, 275 against 465. A copy with the allowance wrongly 0 fails exactly one, 380 against 405. Both reported `VALIDATION FAILED`.

## 3. O2's never-short claim: the argument, what the 25 m does, and what the sweeps could not see

**The argument (read, `WayHomeProgress.kt:213-223`), made explicit.** Let the walker be on the line at along-metres s. Some node N has along ≤ s and lies within one node step (≤ 10 m, `:53`) of the walker along a straight stretch, so dist(walker, N) ≤ 10. A fix F the gate admits has dist(F, walker) ≤ 50. By the triangle inequality dist(F, N) ≤ 60 = the reach, so N is within reach, and the *earliest* node within reach has along ≤ along(N) ≤ s. The figure, total − along(that node), is ≥ total − s = the truth. The allowance is not used, so O2 inherits the result. **Nothing in this uses the shape** of the line: bends, junctions, a final leg at any angle, a trail passing near its own end, a re-route from any point, any node spacing up to 10 m. So "0 short in six sweeps" is a theorem checked, not a property found, and no shape left unswept can break it **under its two premises**: the walker on the drawn line, and the true error within 50 m. Those premises are where the 25 m has been working.

**Premise one: the walker on the drawn line (computed).** For a walker truly p metres beside the line, the node at or behind them is √(p² + 10²) away, not 10, and the reach needed is 50 + √(p² + 100). O1's extra 25 m on the trail part covers that up to p ≈ 33 m; O2 covers p = 0 and a little more from the node grid. My probe put the walker p metres square to W's trail, the fix anywhere the gate admits (0 to 50 m, every 10°), placements where the HUD says "Arrived" or the place is off the line left out, truth = the line's metres from the walker's foot:

| p beside the trail (away from the waypoint) | O1 short | O2 short | O2 worst |
|---|---|---|---|
| 0, 2.5, 5 m | 0 | 0 | — |
| 7.5 m (fine sweep, 398 k placements) | 0 | 195 (0.05 %) | −2.5 m |
| 10 m | 0 of 39,696 | 55 (0.14 %) | −5 m |
| 20 m | 0 | 275 (0.7 %) | −10 m |
| 30 m | 0 | 436 (1.3 %) | −15 m |
| 40 m | 0 | 595 (2.1 %) | −20 m |

On the lead-in and the final leg (STRAIGHT, no allowance in either option) O1 and O2 are identical and both show the same small shorts from 10 m out (−5 at 10 m, −13 at 20 m on the final leg). So the 25 m is doing on the trail part exactly what nothing does on the legs; O2 makes the trail part behave like the legs. How often a walker is 7 m or more from the engine's drawn trail is not measured here (an OSM centreline against a real tread; a wide forest road; a shortcut). -1630's real walk read 68 to 86 m long and never short with the 25 m in place; that cannot tell us about O2.

**Premise two: the true error within 50 m (computed).** The gate's 50 m is on *reported* accuracy (`LiveFixGate.kt:78`), and the S22 reports a constant 3.79 m, so on that phone the gate rejects nothing. With the fix e metres ahead of an on-line walker along a straight trail stretch: O1 reads long until e = 75 (+5) and short at 80 (−5); O2 reads 0 at e = 55 to 60 and short at 65 (−10), −20 at 75, −40 at 100; O4@4 short from e = 25. So the 25 m is also 25 m of tolerance beyond the gate's bound. How often a canopy fix is 60 to 80 m wrong is the same unmeasured quantity the owner's -1623 condition names for O4, at a less demanding level.

**A shared exposure, for the record (computed, N-6).** With the walker 10 to 30 m beside the trail *toward* the final leg near the trail's end, a fix 40 to 50 m further out can sit just over 60 m from every trail node and within 60 m of the final leg, and the figure counts from the leg: −75 m (walker (305, 10), fix (305, 60)), −90 at 20 m beside, −100 at 30 m. It is identical under O1 and O2 (the leg has no allowance), it is B-395-2's hairpin mechanism in a new place, and it needs the walker off the line; the report's "never short" for O1 shares it, so it is not a point against O2.

**What the 25 m was for (read).** -1613 ruled it in when the figure counted from the place, "the first point ahead within 25 m, plus 25 m added, so never short": it cancelled a node up to 25 m ahead. -1618 and -1620 moved the figure's node to the earliest within reach, which is never ahead, and the code says the allowance "is now a margin on top: the trail part is the engine's line, not the walker's own track" (`:206-209`). That sentence names premise one without quantifying it. The report's "no stated job left after -1636" is right about the bends and wrong as a conclusion: the job is the two premises above, and the owner should hear it as that.

## 4. Headline figures recomputed

All on the same cases (W, F, Bn, U, R as Juniper laid them out). Mine from `sorrel_check.py` at 0.1 m steps; Juniper's from `waypoint_figure.out`. Metres; ft where the report quotes them.

| Figure | Juniper | Sorrel | Match |
|---|---|---|---|
| W, end of the trail (40 m left): O1 / O2 / O4@4 | 125 (410 ft) / 100 (328) / 60 (197) | 125 / 100 / 60 | yes; Hazel's 412 ft |
| W, last before "Arrived" (16 m left): O1 / O2 / O3 / O4@4 | 115 (377 ft) / 90 (295) / 16 / 40 (131) | 115 / 90 / 15.1 / 40 | yes |
| W, long by over the walk: O1 / O2 / O4@4 | 60–99 / 60–84 / 0–35 | 0–99.9 / 0–84.7 / 0–34.9 | yes (the 0 is the first node step) |
| W, first "Approaching": O1 / O2 / O4@4 | 175 (574 ft) / 150 (492) / 120 (394) | **215 (705 ft) / 190 (623) / 150 (492)** at s = 298.4 | **no**, S-2 |
| F 10 m leg, before "Arrived": O1 / O2 / O4@4 | 105 / 80 / 40 | 105 / 80 / 40 | yes |
| F 25 m leg: O1 / O2 | 100 / 75 | 100 / 75 | yes |
| F 100 m leg: start 185/160; 30 left 80/80; before "Arrived" 70/70; O3 cliff 175 → 75 | as stated | 185 / 160; 80 / 80; 70 / 70; O3 at 30 left reads 30 | yes |
| F 250 m leg, before "Arrived" | 70 | 70 | yes |
| Bn, leg's start / before "Arrived": O1, O2 | 145 / 115; 120 / 90 | 145 / 115; 120 / 90 | yes; O1's max long 106.9 (report: 106) |
| U 30 m, return leg start / 110 left / leg's start / before "Arrived" | 355 / 475 / 510 / 494 (1621 ft) | 355 / 475 / 510 / **490** (1608 ft) | one node step, S-3 |
| U 30 m, O4@4 climb | none | none (0 rises) | yes |
| U 20 m, O4@4 climbs | yes | yes (432.9 long, 20 rises) | yes |
| R, hold / rise / before "Arrived": O1, O2 | 55 m / +6 / 114, 89 | holds to s = 55, 280.6 at 60 (+6 m rise once) / 113.6, 88.6 | yes |
| Sweep W placements; O1, O2 short | 60,843; 0, 0 | 61,433; 0, 0 | S-3; the 0s hold |
| Sweep W: O3 / O4@4 short, worst | 843 (−78.6) / 8,897 (−87) | 839 (−78.6) / 8,772 (−95) | S-3 on the −87 |
| Sweep U 30: O3 / O4@4 worst | −479.4 / −478 | −479.4 / −480 | yes |
| Sweep Bn: O3 / O4@4 worst | −71.7 / −90 | −71.7 / −90 | yes |
| §6 table, fix d north at the trail's end: O4@4 at d = 25 / 40 / 50 | 32 / 24 / 8 (−8 / −16 / −32) | not re-run; consistent with my e-table | — |

Hand arithmetic for the two W figures, as the report gives it, checked: at the trail's end the earliest trail node within 60 m of (350, 0) is (290, 0), along 330, so 430 − 330 = 100, +25 = 125; 25 m along the leg, (300, 0) is 55.9 m off and (290, 0) 64.6, so 430 − 340 = 90, +25 = 115. Juniper's "O4 corrected after the first run" (report §8): the diff between the two outputs touches only O4 columns in §4's displaced walks and the §2 wording; W sweep 9,987 → 8,897 short, worst unchanged. Read; the claim "moved the counts a little and no conclusion" holds.

## 5. Shapes the sweeps did not cover

Inferred from §3's argument, with the computed cases where there are any.

- **Bends and junctions.** A bend only brings earlier nodes nearer to later positions: longer, never shorter (Bn: O1's maximum long 106.9 against W's 99.9). A junction is nothing to the line; a wrong branch is a stray, re-routed at 50 m (`WayToStray.kt:24-33`).
- **A final leg leaving the trail at an angle.** The leg is STRAIGHT; O1 and O2 differ only while the figure's node is still a trail node, which an acute angle prolongs (longer readings) and an in-line leg ends after 60 m (F 100 m in line: O1 = O2 = 70 at the end). Never short under the premises.
- **A re-route mid-final-leg.** The new line starts at the *fix* that strayed (`AvailabilityWaypointRoute.kt:122, 152`), so if the stray was the phone's error the walker is truly beside node 0: premise one again, covered by -1630/-1634 for a walker beside or behind the start (the distance back is added) and uncovered for a walker ahead of it to one side. Not a new mechanism.
- **Node spacing against the reach.** The fill guarantees ≤ 10 m geometric spacing (`:53`); the argument uses geometric distance, so engine-length scaling (N-2) cannot break it; a scale below 1 would compress the figure for both options equally.
- **The walker beside the line** is the only shape class that changes O1 against O2, and it is §3's table.

## 6. The reader claim (read)

At `a00742ffb` and again at `0662d533c`, `uiState.waypointRoute` is read in exactly three places outside its own collaborator: `CompactMapPortraitNavigationCard.kt:124` and `CompactMapLandscapeNavigationCard.kt:169` (`.hud`, into the HUD's `route`) and `CompactMapMapSlot.kt:68` (`.line`, the drawn line). `WaypointRouteShown.hud` is a `ReturnRoute.Ahead(lookahead, routeMeters)` (`AvailabilityWaypointRoute.kt:145`); `routeMeters` is read once, `ui/availability/NavigationReadout.kt:114`, into the large slot; `:124` reads the route's *kind* for the "by trail" words, not the number. Sundown's way-home reader is `wayHomeWatch.progressFor(id)` (`AppContainer.kt:476` → `SundownWatch.kt:296-297`), the return's `WayHomeWatch`, which the waypoint route never touches: its placing lives in `AvailabilityWaypointRoute.Following` (`:93-184`). Arrival and "Approaching" are the straight distance's (`ui/availability/NavigationReadout.kt:92, 101-102`). Holds. `RouteHome.Ahead.pathHome` is built for the waypoint too (`WayHomeAhead.kt:43`) and discarded at `AvailabilityWaypointRoute.kt:143-147`, as `WayHomeAhead.kt:24-25` says.

## 7. Verdict per option

- **O1, today's rule: holds** as described (never short under the two premises; 60 to 99 m long; 115 m then "Arrived" on W). Its 25 m is the only slack either premise has.
- **O2, the reach alone on the waypoint route: holds with limits.** Never short for a walker on the drawn line for any fix within 50 m of them, on any shape (theorem, §3). Short by about half the lateral offset from about 7 m beside the trail, in 0.1 to 2 % of admitted placements; short from 65 m of true forward error rather than 80 (S-1). Reads 25 m under O1 wherever the figure's node is a trail node and the same elsewhere: 100 m at the trail's end and 90 m (295 ft) before "Arrived" on W. The climb beside a parallel leg is unchanged (the reach's, not the allowance's: U 510 then 490). Code cost as the report says: the allowance is one constant at `:149-151`; a line that carries its own allowance (0 for a `WayToWaypointResult`) is the clean form. **Recommend**, with S-1 in the owner's view.
- **O3, straight on the final leg: does not hold** as a candidate. Exact for 2 m of walking on a 40 m leg; a 100 m cliff on a 100 m leg; −79 m on W (1.4 %), −479 m on U, by the place rule's own exposure. Rejection confirmed.
- **O4, the reach from reported accuracy: holds as described, deferred rightly.** 40 m (131 ft) before "Arrived" at 4 m; no climb at 30 m legs; short by the skipped stretch whenever the true error exceeds twice the reported figure: −95 on W (14 % of placements), −480 on U (17 %), in my sweep. The three files advising against building on the field (`LiveFixGate.kt:73-75`, `domain/NavigationReadout.kt:29-39`, `ReturnWalkingTime.kt`) and the owner's -1623 condition stand. The report's own reversal (P8) is right.

## 8. Findings, graded

| ID | Grade | Where | Finding |
|---|---|---|---|
| S-1 | Should-fix | §0, §4 "Never short?", §9, §10 | "Never short for any fix the gate admits" needs its premise, the walker on the drawn line; the 25 m covers a walker up to ~33 m beside the trail and 25 m of true error beyond the gate's bound; O2 gives both up (short by ~p/2 from ~7 m beside; short from 65 m of forward error). Put to the owner as the trade. |
| S-2 | Should-fix | §3, §4, §6, §8 P2 | "Approaching" figures read from the first printed row: 215 / 190 / 150 m (705 / 623 / 492 ft), not 175 / 150 / 120. |
| S-3 | Should-fix | header, §3 U, §5 | The 40 m stretches fill to 5 nodes at 8 m by haversine rounding; U's 494 is 490 with 10 m nodes; several rows and the sweep counts differ by one node step between replicas; the app could show either. Say ± one node step. |
| N-1 | Note | script `walk()` | After an off-line fix the replica's previous index is `None`; the app keeps `lastPlace` with band FAR. Touches O3's place only. |
| N-2 | Note | §1, §4 | Engine-length scaling of the trail part not modelled; never short is relative to the line's metres for both options (-1636: within 5 m over 325 m). |
| N-3 | Note | §1 validation | The sixteen equalities were run; the test file's inequalities were not. I ran them (all pass) and two controls (both fail for the predicted reasons). |
| N-4 | Note | §3, §4 "Never short?" | "0 of 61 to 72 k placements" confirms a shape-free theorem (§3); state it as such, so the sweeps are not read as having searched for a counter-example they could not find. |
| N-5 | Note | §4 "Cost in code" | The suggested form (a `WayHomeLine` carrying its allowance) is sound and keeps the 405 literal; the trail's last node is already STRAIGHT (`:54`), so no edge case at the leg's start. |
| N-6 | Note | §3 here | A walker 10 to 30 m beside the trail toward the final leg can read −75 to −100 m under O1 and O2 alike (B-395-2's mechanism). Not a point for or against O2; for the record. |
| N-7 | Note | §7 | "A hand-made waypoint at the car" inference is right (`AvailabilityPureFunctions.kt:70` hides designated ones only); the waypoint figure still feeds no sundown path, so the consequence is a wrong expectation, not a wrong alert. |
| N-8 | Note | §9 (2) | Whether the way home drops its 25 m: not on this evidence. There the figure is a safety input, the 25 m is the only slack beyond the gate's bound on an uncalibrated field, and the cost is 25 m of reading against hundreds of metres. The owner's walk-data condition (-1623) is the right gate for both routes. |

## 9. Prediction scored

1. **Held.** Pinned literals reproduce; the one gap is the `previous` after off-line (N-1), O3 only.
2. **Held.** The argument is shape-free; 0 short on W, U, Bn in my sweep as in Juniper's.
3. **Held and sized.** The 25 m covers lateral offset to ~33 m; O2 reads short from about 7 m beside the trail, by about half the offset. I had not predicted the second premise, true error past the gate, which the same probe made visible (O1 to 75 m, O2 to 60 m).
4. **Held**, bar one node step on U and the exact-multiple rows (S-3), and the "Approaching" figures, which I had not predicted would be misread (S-2).
5. **Held**, at both commits.

## 10. Could not determine, and not done

- How often a walker is 7 m or more from the engine's drawn trail on the owner's ground, and how often a canopy fix is 60 to 80 m wrong: the two quantities S-1's trade turns on. Neither is in any record; -1630's real-walk figures were taken with the 25 m in place.
- What the HUD shows at exactly a multiple of 10 m along these synthetic shapes (S-3): a float coin-toss in the app as in both replicas, immaterial everywhere else.
- Juniper's §6 table (O4 against d at the trail's end) was not re-run row by row; it is consistent with my forward-error table and the O4 verdict does not rest on it.
- No revert check in the project's Gradle sense: nothing here is a test or a code change. The reviewer profile's step 5 is met by the two controls in §2, which fail my replica's validation for the reasons the test file names.
- My replica is outside the repo at `/mnt/work/scratch/sorrel-waypoint-figure/mine/sorrel_check.py` (221 lines of output in `sorrel_check.out`, the four probes beside it). This check is my only committed write, per the dispatch; if Rowan wants the script kept as Juniper's was, that is a line in a dispatch.
- Nothing run with Gradle; no phone; no code touched; `records-after-173` not touched.
