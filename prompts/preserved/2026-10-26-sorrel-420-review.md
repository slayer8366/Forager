# Sorrel's review of PR #420: the barometric climb bridges a mid-walk pressure gap

Reviewer: Sorrel Tally (Fable), fresh session, 2026-10-26T22:47Z. For Rowan Waymark. Read-only on code; no device; nothing run under Gradle here. The owner's walk copy was read locally with `python3 -I` from `/mnt/work/scratch/s26-hilly-walk/work/forager.db` (`mode=ro`); this report carries aggregates only. My port and checks are in `/mnt/work/scratch/sorrel-420/` (`recompute.py`, `hyst0.py`), not committed.

Each claim is marked **read** (from the file named), **computed** (by my own code or by hand) or **inferred**.

## Baseline (read)

- pre-main `06fe0e3ae` = merge-base of `climb-pressure-gaps`; branch head `ddc760233`; three commits: `40bb96fcd` tests, `68759e0bd` test fix, `ddc760233` fix. Diff: `domain/BarometricHeight.kt` (+31/−8) and `domain/BarometricHeightTest.kt` (+40/−2). Nothing else.
- Dispatch `prompts/preserved/2026-10-26-18.md` (on `records-after-173`, not on the PR branch); RECORD -1687, -1689, -1690 (`records-after-173` @ `15c2cc54e`); Juniper's report `docs/navigation/2026-10-26-hilly-walk-climbs.md` (`s26-hilly-walk-report` @ `e576021d8`).
- Runs: fail-first 40409531783 on `68759e0bd` (`BarometricHeightTest` filter: 14 tests, 2 failed); PR 40410563114 on `ddc760233` green, **5,709 tests across 775 suites, 0 failures, 0 errors, 24 skipped** (the run's own summary table); revert 40410567119 on `fbf534d2a` (parent `ddc760233`; 14 tests, 2 failed).

## Prediction, before computing

Bridging would reproduce Juniper's 53/43 and 72/69 m, and the whole rise over the old figures would equal the sum of the barometer's positive changes across the gaps. **Scored below: the first held; the second did not** (the rise is larger, for two reasons given at §3).

## 1. The bridging in `BarometricHeight.kt` (read, `ddc760233`)

- `bridgedAcrossGaps` (`:117-130`): a `null` height with a value on both sides becomes `before + (after − before) × (t − t_before)/(t_after − t_before)`, linear in time; a `null` before the first value or after the last stays `null` (`:124-125`). The ends are left without a height, as the dispatch required.
- It runs on *heights*, after the range check (`:51-54`): an out-of-range reading becomes `null` first, so a mid-walk out-of-range reading is bridged like a missing one. Juniper's diagnostic bridged *pressure*; the two agree to 0.1 m on the walk (computed, §3).
- The median, step limit and mean then run across the bridged values (`:56-84`); `runsOf` now only breaks at the leading and trailing `null`s.
- `trackHeights`' gate (`TrackHeights.kt:20`) still counts *raw* `pressureHpa`, so bridged points do not help a walk qualify as barometric. Unchanged and right.
- A degenerate `span == 0` is guarded (`:127`); a `null` at index 0 of the walk stays `null` (walk A's first point, §3).

**Does it invent climb?** No (computed and reasoned). Each bridged value lies between two measured heights, so no new extreme enters the series; the hysteresis banks at most the barometer's net change across a gap. Two second-order effects are real and intended: (a) the smoothing windows now span the gap, so heights at real points near a former run-end move (max 1.7 m on A, 4.6 m on B; 97 and 189 real points move by over 0.1 m) — the old run-ends had one-sided windows, so this is a correction, not an invention; (b) the reset no longer discards the unbanked remainder (under 4 m) on either side of every gap. Both raise the climb without any value leaving the measured range.

## 2. The unlimited gap length (computed on the walk, reasoned)

Nothing caps the bridge. With the barometer off for minutes (airplane mode does not stop the barometer or GPS; a sensor stall or `latestPressure` going stale would), every kept point in the span is `null` and gets the straight line between the last reading before and the first after. The step limit sees the line's slope, Δh/Δt over the whole span, so a walker's real change never trips it; the hysteresis banks the net change once (if ≥ 4 m). **The climb inside such a span is bounded by |net change|: anything up-and-down inside is lost, never over-counted.** Synthetic check on the walk: blanking 10 minutes of pressure mid-walk (18 and 65 points) moved Climb/Descent by −0.5/−0.4 m on A and +0.2/+0.5 m on B, where the real heights inside B's span fell 13.4 m with a net of −17 m.

What *does* change: the height chart (`EntryReport.kt:162-168`, `RecordDetailsFormat.kt:74`) draws bridged heights as samples, so a long outage is drawn as a straight line with no mark, and the `TooFewHeights` gate (`EntryReport.kt:151-153`) counts GPS altitude, not pressure, so it will not catch a barometer outage. For a one-point gap that is right; for a ten-minute one it is a plausible value presented as measured. This is a note, not a block: the dispatch forbade a new figure without a reason, and a cap is exactly that. It should be *recorded* as a decision (unbounded, because a straight line between two real readings adds no climb), and a cap or a chart mark priced separately if the owner wants one.

## 3. The walk figures (computed, my own port; `recompute.py`)

Kept points by the whole-second rule, as the read seam does. Hysteresis 4 m.

| | A (`9685146a`) | B (`cad94737`) |
|---|---|---|
| kept points / without pressure | 457 / 14 (3.1 %) | 677 / 20 (3.0 %) |
| gap runs, longest run | 14, all single points | 20, all single points |
| bridged span, median / max | 22 s / 131 s | 24 s / 158 s |
| **old pipeline, Climb / Descent** | **31.2 / 36.9** | **41.0 / 33.6** |
| **PR bridging (heights), Climb / Descent** | **52.8 / 43.1** | **72.4 / 68.7** |
| Juniper's bridging (pressure) | 52.8 / 43.1 | 72.4 / 68.7 |
| at 10 m hysteresis, old → PR | 18.0/21.1 → 34.9/34.2 | 20.5/22.2 → 76.1/72.9 |
| steps taken out by the 0.5 m/s limit | 0 | 0 |
| sum of positive changes across the gaps (old heights) | 14.0 m | 21.9 m |

**Matched**: 53/43 and 72/69 (Juniper, the PR body's "52.8 and 72.4", RECORD -1690). A's index 0 has no pressure and stays `null` (456/457 heights), matching Juniper's "13 of 14 inside".

**Prediction scored**: the rise in Climb (+21.6 m on A, +31.4 m on B) exceeds the positive change across the gaps (14.0, 21.9). At hysteresis 0 the rise is +12.2 and +25.0 m. The difference is §1 (a) and (b): symmetric windows at the former run-ends and the remainders the resets discarded. No value left the measured range.

## 4. The 2 s / 5 s mismatch (computed by hand, then against the walk)

`pressureForPoint` keeps a reading at most 2 s old (`PressureProvider.kt:30-37`); the sensor is registered with a 5 s maximum report latency at 1 Hz (`AndroidPressureProvider.kt:42`). If batching held at that maximum throughout, the newest delivered reading's age at a random instant is uniform on [0, 5 s), so P(age > 2 s) = 3/5 = **60 %**. The coder's figure is right. The observed 3 % rules out continuous batching as the cause. Whether *intermittent* batching (a dozing processor while the walker stands still) explains the 3 % cannot be settled from the copy: pressure-less points follow longer intervals on B (median 10 s against 7 s; 30 % follow an interval over 20 s against 10 % of all points) but not on A (15 % against 20 %). Inconclusive. One more candidate, **inferred and unverifiable here**: `age in 0..PRESSURE_MAX_AGE_NANOS` returns `null` for a *negative* age, i.e. a sensor timestamp ahead of `elapsedRealtimeNanos` by any amount; sensor clocks are not always on exactly that base. An instrument log of `event.timestamp − elapsedRealtimeNanos` on the S26 would decide it. The owner ruled "Leave it" (-1690); with bridging in, the 3 % costs nothing measurable.

## 5. The changed existing test (read, reasoned by hand)

`a reading outside the operating range is not used` (`BarometricHeightTest.kt:122-127`). Before: three points 5 s apart, 1200 hPa on the middle one, `assertNull(heights[1])`. After: (i) 1200 hPa on the *last* point, `assertNull(heights[2])`; (ii) 1200 hPa on the middle one, `heights[1] == heights[0]` to 1e-6.

- **Is the proof that the reading is not used still there? Yes, in (i).** With the range check removed, the last point would read h(1200) = −1,450 m, the 60 s window's median of [110.9, 110.9, −1450] is 110.9, and `heights[2]` would be a value, not `null`. So (i) fails iff the reading is used.
- **(ii) does not prove non-use, and cannot.** With the range check removed, the same median gives 110.9 at every index, so `heights[1] == heights[0]` holds whether or not 1200 hPa is used; spacing the points past the window would not help either, because the 0.5 m/s step limit removes a lone 1,560 m step and its step back. Mid-walk, a single out-of-range reading is unobservable after the pipeline. The old assertion was observable only because the old code turned non-use into a run break. So the range-check proof *had* to move to a run's edge, and it did. (ii) proves bridging (value, not `null`); its doc comment ("its height is theirs, not 1200 hPa's") is true but credits bridging for what the median would do anyway.
- **Net: not weaker.** One assertion proves non-use (at the edge), one proves bridging (mid-walk); the old test proved both with one assertion, in a code shape that no longer exists.

## 6. The new tests and the revert's bite (read: JUnit XML of both runs, downloaded)

- `a reading missed mid-walk does not drop the climb` (`:141-149`): the 50 m climb fixture with one point in 23 blank. Fail-first and revert both fail it at `:147` with `climb expected:<45.35415792556253> but was:<0.0>` — 0.0 because each 22-point run is 110 s, under 3.1 m of climb, below the 4 m hysteresis, so nothing banks anywhere. Harsher than the real walk; a fine failure.
- (ii) above fails in both runs at `:126` with `mid-walk, bridged from either side (NaN: no height) expected:<100.0> but was:<NaN>`.
- The revert (`fbf534d2a`, not for merge) adds one line, `if (values.isNotEmpty()) return values`, at the top of `bridgedAcrossGaps`; both runs compiled (14 tests completed; no `e:` lines), and both failures are ones that edit, and only that edit, produces. Messages are specific to this change. **Bites.**
- `a reading missed at a walk's start or end is not made up` (`:153-158`) passes before and after the fix (ends were `null` before too; points 1..8 all have pressure). CLAUDE.md flags such a check as suspect; here it is a guard against a future bridge that extrapolates, which is legitimate, but it has never been seen red. Say so if it is cited.
- Nothing else in the suite changed and the suite is green at 5,709; `S22WalkHeightTest`'s 69-point fixture has no pressure gaps (checked), so its bounds were not exercised by this change.

## 7. Who else reads barometric heights (read, `git grep` on pre-main)

`trackHeights` has two callers: `ComputeTrackStatisticsUseCase.kt:66` and `EntryReport.kt:162` (`heightProfileOf`). From there:

- **Climb / Descent tiles** on the Records track sheet (`RecordDetailsFormat.kt:64-65`) and the **Journal entry's Climb** sum (`EntryReport.kt:109-110`): both rise on a barometric walk with gaps, by this walk's measure 60 to 75 %. Read-time only; nothing stored changes (`TrackPoint.pressureHpa` and `altitude` untouched; GPX export unchanged). The **map bubble** computes the same statistics (`MapBubbles.kt:305`) but shows distance and duration only (`:310-312`), so it does not change.
- **The height chart** on the entry report and the track sheet (`EntryReport.kt:162-168`, `RecordDetailsFormat.kt:74`, `CartographyEntryReportScreen.kt:754`): gains samples at bridged points; invisible for single-point gaps, a straight line for a long one (§2).
- **Entry snapshots** (`CartographyDecisionMappers.kt:29-49`) carry distance, duration and point count only, so no stored climb figure exists to go stale.
- **Not read**: the HUD shows no climb (no reader of `elevationGainMeters` in `ui/map` or `ui/track`); the walk-back time has no climb term today (Juniper §1; -1689 builds it after this merges, and it will read the bridged figure, which is the intent).
- `TrackStatistics.elevationCoverage` and `pointsWithAltitude` count GPS altitude: unchanged.
- Stale doc, minor: `ComputeTrackStatisticsUseCase.kt:34-37` still says a gap gets "not a fabricated bridge across the missing readings". True at that layer for a `null` height; no longer the whole story for a pressure gap, which is bridged one layer up.

## Verdict

**Blocking:** none.

**Should-fix** (can ride a later PR; neither changes behaviour):
- S1. Reword the doc on `a reading outside the operating range is not used` (`:117-119`): the mid-walk assertion proves bridging; the median would make the height "theirs" regardless; the non-use proof is the end case.
- S2. One line on `ComputeTrackStatisticsUseCase.kt:34-37` (and the stale `:63-65` "smoothed GPS height today") so the gap note points at `barometricHeights` for pressure gaps.

**Notes:**
- N1. The bridge is unbounded in length. Climb stays bounded by the barometer's net change (§2), but the chart draws an outage as a straight line with no mark and the heights gate counts GPS, not pressure. Record "unbounded" as the decision with its reason; price a cap or a chart mark only on the owner's word.
- N2. The start/end test has never failed (§6).
- N3. The 2 s / 5 s mechanism: 60 % if batching were continuous, so it is not the whole cause; intermittent batching inconclusive from the copy; a negative-age `null` is a third candidate, unverifiable without an instrument log (§4). Window left as ruled.
- N4. The rise in Climb is larger than the sum of changes across the gaps, for the two reasons in §1 and §3; neither invents a value outside the measured range.

**Mergeable: yes.** The walk recomputes to the stated figures with my own port; the proof that an out-of-range reading is not used survived its move; the revert bites with messages specific to the edit; no stored data or schema changes; no device check was needed for a read-time arithmetic change, and none was run.

## Disclosure

**Confirmed (read or computed):** every file:line above on `06fe0e3ae`/`ddc760233`; the three runs' heads, totals and JUnit messages (artifacts `unit-test-report` downloaded for 40409531783 and 40410567119); the walk figures by my own port, independent of Juniper's `trackmath.py` (read afterwards for comparison; same figures).

**Inferred:** the negative-age candidate (§4); that the chart would look "broken" on a long outage (no device).

**Could not determine:** the cause of the 3 % gaps; whether any user's walk has a long mid-walk barometer outage today.

**Decided beyond scope:** the ten-minute outage simulation and the interval association test (§2, §4), neither asked for. No Gradle run here (laptop kept for forecast work). No ports of mine are committed.
