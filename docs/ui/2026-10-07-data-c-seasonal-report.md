# Data part C: Seasonal, trip windows and the forecast zoom note (pre-build report)

Dispatch 2026-09-28-668 (RECORD intent -668, `prompts/preserved/2026-10-07-12.md`). Written
2026-10-07 (UTC) on branch `data-c-seasonal`, cut from `origin/main` at `340bdc4a` (PR #190). The
data scout (`docs/ui/2026-10-07-data-scout.md` on branch `data-scout`) was written at `aa79f25a`;
the only change between the two is one line of `CLAUDE.md`, so every citation it makes into the app
held at this base. App paths are relative to `app/src/main/java/com/zynergylabs/forager/app/`.

**Status: code and tests written, not compiled, not run.** No Gradle, emulator or phone was used
(dispatch: Gradle only on the planner's go). Nothing below is evidence that the code compiles or
that a test passes. The revert checks are listed as planned, not done.

## The owner's choices this builds (RECORD -656, verbatim)

- "Plain scales + real charts (Recommended)". Soil moisture as Dry, Moist or Wet with the figure
  under it; the chart gets axes, labels and equal spans; daily rain is kept and drawn as a 14-day
  bar chart; conditions are a small table.
- "Fix both (Recommended)". "Zoom in to see the forecast" below the layer's zoom, and times follow
  the phone's 12- or 24-hour setting.
- "Use 'Oct 7, 2026' everywhere."

## Premises checked

| Premise (dispatch or scout) | Found |
|---|---|
| `OpenMeteoWeatherProvider.kt:149-161` sums the 14 daily values and discards them | True at this base (`toDomain`). |
| The Seasonal chart has no axes and unequal buckets (0–6, 7–21, 22–35, 36+) | True (`ComputeFruitingLagDistributionUseCase.kt:88-93`, chart was `AvailabilityResultsUi.kt:387-407`). |
| "Shallow soil moisture: %.2f m³/m³" in the trip windows | True (`AvailabilityResultsUi.kt:581-586` at base). |
| Below zoom 7 the forecast layer draws nothing and says nothing | True (`ui/map/ForecastCellLayer.kt:15-25,47`). |
| No chart or table library | True; none added. |
| Soil moisture shows "in the Seasonal tab" | **Partly wrong.** Only the trip windows card carries soil moisture. The Seasonal tab's conditions card has none. See stop 3. |
| Times appear in this part's screens | **Wrong.** The Seasonal tab, the trip windows card and the legend's zoom note show no clock time, so the 12/24-hour half has nothing to apply to here. Nothing was changed for it. |
| The conditions card's "days since last rain" | **A bug the scout did not name.** The figure counts from the newest *observed* day, which is yesterday at the searched location (`OpenMeteoWeatherProvider.kt` doc on `summariseObservedConditions`). The card printed 0 as "Rain today.", one day early. The new table reads it as "Yesterday" and N as "N+1 days ago". See stop 4. |

## What was written

Commits on `data-c-seasonal` (pushed): `f058dd54` (domain, the move, the chart helpers), and the
commit carrying this report (UI, the zoom note, the tests).

**Daily rain kept (no change to what is fetched).** `domain/model/ConditionsSummary.kt` gains
`dailyRain: List<DailyRain>`, filled in `toDomain` from the same `precipitation_sum` values the total
is summed from, paired with their dates. Defaulted to empty so the 22 test fixtures that build a
summary for other reasons need no edit; the one production builder always passes it.

**Equal spans.** `FruitingLagDistribution` gains a required `histogram: FruitingLagHistogram`:
the same lags in 7-day spans from day 0, six spans (days 0 to 41), and a count of lags past day 41
(`ComputeFruitingLagDistributionUseCase.equalSpanHistogram`). The four labelled buckets stay as the
counts listed under the chart, because "7–21 days (the rule of thumb)" is the range being tested.
The chart shades the rule's own days (7 to the end of 21) behind the bars, from
`FruitingPatternAssumptions.FRUITING_LAG_DAYS` by reference.

**Soil moisture scale.** `domain/SoilMoistureScale.kt`: Dry below 0.15, Moist to below 0.30, Wet
from 0.30 (proposed; stop 1).

**Screens.** The Seasonal and trip-window code moved out of `ui/availability/AvailabilityResultsUi.kt`
first, under "Piece by piece" (RECORD -655), into `AvailabilitySeasonalUi.kt` and
`AvailabilityTripWindowsUi.kt`, with no moved line changed in that commit. Then:
- Conditions card: a labelled table (Rain, last 14 days; Last rainy day; Rain forecast today), then
  "Daily rain, last 14 days" as a bar chart with a rain axis and the first and last dates under it.
  The "Today's Forecast" heading went; its figure is the table's last row.
- Fruiting-lag chart: count axis titled "Sightings", days axis ticked 0, 7 … 42 and titled "Days
  after a soaking rain", each bar's count above it, the shaded band and a key line under it.
- Trip windows: each window is a dated heading over a labelled table; soil moisture is the scale
  with the figure under it; dates read "Aug 16, 2025".
- The no-window message's dates read "Oct 7, 2026" (`AvailabilityPureFunctions.kt`).
- New drawing in `ui/availability/SeasonalBarChart.kt` (chart, table, scale); what they show is in
  `ui/availability/SeasonalChartModels.kt` (pure, tested headless); `ui/availability/DisplayDates.kt`
  holds the "Oct 7, 2026" format.

**Zoom note.** `ui/map/ForecastCellLayer.kt` gains `isBelowForecastZoom`; `MapForecastFeed` gains
`onZoomedOutChanged`; `SightingsMap` reports it at each camera idle; `AvailabilityScreen` holds it and
passes it through `MapLayersControls.forecastZoomedOut`; `mapLegendFor` marks the legend; the chip
says "Zoom in to see the forecast" under its label, collapsed or expanded. The limit (zoom 7) is
unchanged. The "No new copy" comment is kept and marked superseded.

## Stops for the owner

1. **Moisture thresholds.** Proposed: Dry below 0.15 m³/m³, Moist 0.15 to under 0.30, Wet 0.30 and
   up. Source: FAO Irrigation and Drainage Paper 56, Table 19, "Typical soil water characteristics
   for different soil types" (fao.org/4/x0490e/x0490e0c.htm, read 2026-10-07): 0.15 sits in the
   wilting-point range of loam (0.07–0.17) and silt loam (0.09–0.21); 0.30 is the top of loam's
   field-capacity range (0.20–0.30). Limit: Open-Meteo gives no soil texture, so one loam-centred
   scale is applied everywhere; in sand 0.15 is already moist, in clay 0.22 is still dry. The figure
   stays under the word for that reason. Alternatives: (b) a scale relative to the location's own
   readings over the 21 days fetched, which says "wetter or drier than lately" and not "wet"; (c)
   no word, the figure only.
2. **Chart spans.** 7-day spans, six of them (days 0 to 41). The rule of thumb's 7–21 days is 15 days
   wide, so with 7-day spans the shading ends one day into the 21–27 bar. Alternatives: 1-day bars
   (42 thin bars, exact edges, noisy with small samples), 5-day spans starting at day 2, or 3-day
   spans.
3. **Soil moisture on the Seasonal tab.** Today only the trip windows show it. Today's value is
   already fetched for the conditions card (`todaysForecast.shallowSoilMoistureM3M3`), so a "Soil
   moisture today" row needs no fetch change. Not added; yes or no.
4. **"Last rainy day" now reads one day later than the old sentence** ("Rain today." becomes
   "Yesterday", "2 days since last rain." becomes "3 days ago"), because the old wording was a day
   early. Confirm.
5. **New user-facing strings**, verbatim:
   - Conditions table: "Rain, last 14 days"; "Last rainy day"; "Rain forecast today"; values
     "Yesterday", "N days ago", "None in 14 days".
   - "Daily rain, last 14 days" (chart heading); "Rain" (axis title); "0" (axis zero).
   - TalkBack, rain chart: "Daily rain from Sep 23, 2026 to Oct 6, 2026. Wettest day Oct 1, 2026,
     6.0mm." or "… No rain on any day."
   - Lag chart: "Sightings"; "Days after a soaking rain"; "Shaded: 7–21 days, the rule of thumb";
     "N observation(s) came more than 41 days after a soaking rain and are past the end of the
     chart."; TalkBack: "Sightings by days after a soaking rain, in 7-day spans: 0–6 days, 4, …
     Shaded: 7–21 days, the rule of thumb."
   - Trip window rows: "Days after rain" ("10–12"); "Last soaking rain" ("30mm, ended Aug 6, 2025",
     with " (forecast)" when forecast); "More rain forecast"; "Soil moisture"; "Soil temperature";
     "Evaporated since rain" (was "… evapotranspiration since the rain").
   - Scale: "Dry", "Moist", "Wet" (the owner's words) with the figure "0.27 m³/m³"; TalkBack "Moist,
     0.27 m³/m³".
   - Legend: "Zoom in to see the forecast" (the owner's words).
   - Unchanged and reused: "Current Conditions", "Rainfall data unavailable.", "Forecast
     unavailable.", "No forecast available for today.".

## Decided here, inside the dispatch

- `TRIP_WINDOW_DATE_FORMAT` ("MMM d") is left as it is: its other users are the planned-trips row
  and its map bubble, which belong to the trip planner's part. The trip windows card no longer uses it.
- The legend's own dates ("Week of 2026-09-28, weather to 2026-09-26") and the forecast bubble's ISO
  dates are not changed here; they are the forecast layer's, and the dispatch named only the zoom
  note for it. Flagged for whichever part takes them.
- The soil figure is printed with `Locale.US` (as `formatRainfall` is); it used the phone's locale.
- The scale's steps have a minimum width and wrap, so a step's word is not cut at a large font scale.

## Tests written (not run)

New: `domain/FruitingLagHistogramTest` (span edges, beyond count, refused negative lag, histogram and
buckets from the same lags through the use case); `domain/SoilMoistureScaleTest` (each threshold
and either side); `ui/availability/SeasonalChartModelsTest` (rain chart slots, gaps, scale, axis
dates, imperial, the no-rain description; lag chart ticks, band, labels; last-rainy-day wording; the
"Oct 7, 2026" no-window message).

Added to existing classes: `OpenMeteoWeatherProviderParsingTest` (daily rain kept, observed days
only, through `summariseObservedConditions`); `MapLegendTest` (zoomed-out flag); `ForecastCellLayerTest`
(the zoom edge); `AvailabilityScreenMapLayersSheetTest` (the chip says the note when the map reports
zoomed out, and not otherwise, through the real screen and ViewModel; the stand-in map gained a
`zoomedOut` report); `AvailabilityScreenSeasonalTabTest` (chart description, axis titles, key; the
past-the-chart note); `AvailabilityScreenConditionsMonthTest` (the daily rain chart from the provider
through the real ViewModel); `AvailabilityScreenLayoutTest` (a trip window's dates, rows and scale).

Existing assertions changed to the new wording (the change is the subject of the dispatch, not a
weakening): the conditions card's three sentences and "Today's Forecast" heading in
`AvailabilityScreenLayoutTest` and `AvailabilityScreenConditionsMonthTest`; "Soil temperature: …"
in `AvailabilityScreenLayoutTest` (now label and value on one row). Fixtures that build a
`FruitingLagDistribution` gained its histogram (`AvailabilityScreenSeasonalTabTest`,
`AvailabilityScreenAdaptiveLayoutTest`). No test was ignored, skipped or removed.

**Revert checks planned for the go** (each restores from a saved copy, reads the build log for
compile errors first, then the JUnit XML): keep `toDomain`'s `dailyRain` empty (parsing and
conditions-month tests should fail); shift the span index by a day (`lag / spanDays` to
`(lag + 1) / spanDays`; histogram tests); swap the scale's `<` for `<=` (scale edge tests); drop the `onZoomedOutChanged`
call in the host (map-layers zoom test); drop the chip's note (same); use `TRIP_WINDOW_DATE_FORMAT` in
`TripWindowRow` (layout trip window test); print 0 as "Today" (chart models test).

## Unverified, and device-only

- Everything compiles: not run. Two spots most likely to need a fix on the first build: the
  `buildList<TableRow>` row lambdas being accepted as composable, and `drawText` overloads.
- The charts' look (bars, band, figures above bars, axis lines) is Canvas and not rendered under
  Robolectric: device-only. So is whether "Sep 23, 2026" and "Oct 6, 2026" fit side by side under
  the rain chart at the S22's width and at large font scales.
- The zoom note's trigger (the camera's zoom read at each idle in `SightingsMap`) is MapLibre and
  device-only; the tests drive the host from the report on. In a release build there is no forecast
  data, so no legend and no note shows at all until data ships.
- The 80% map-chrome fill is unchanged: the note sits inside the existing legend chip.
- Whether 7-day spans read well with real sample sizes is unjudged.

## Amendment 1 (RECORD -669), applied after the report above

The owner's answers to the stops, verbatim: "Loam scale, figure below (Recommended)" (thresholds kept;
`SoilMoistureScale`'s doc already cites FAO-56 Table 19); "7-day bars (Recommended)" (histogram kept);
"Yes, add it (Recommended)" (stop 3); "Approve (Recommended)" (the one-day correction and the rain
wording; the other strings stand). Stop 3 is now built: the Seasonal conditions table has a "Soil
moisture" row from `todaysForecast.shallowSoilMoistureM3M3`, on the same scale composable, and no
row when the model served none. Tested through the real screen and ViewModel in
`AvailabilityScreenConditionsMonthTest` (row shown as "Moist, 0.27 m³/m³"; no row without a
reading). `displayDate` stays alone in `ui/availability/DisplayDates.kt` so a merge with data part
A's formatter can unify them. Still not compiled or run.

## Build and test results (the planner's Gradle go, 2026-10-08T00:57Z)

**Merge.** `origin/main` at `bc85fd29` (failure-fixes, motion Parts 1 and 2) merged in as `a374ce87`. Three
conflicts, none where both sides changed the same logic:
- `AvailabilityCompactMapUi.kt`: main wrapped the legend chip in motion Part 2's `MapPopUp`; this branch added
  the zoomed-out flag to `mapLegendFor`. Kept main's pop-up and passed the flag to its `mapLegendFor` call.
- `AvailabilityResultsUi.kt` imports: kept main's `clickableWithShapedPress`; dropped `Canvas`, whose chart moved
  out on this branch. Main's SpeciesRow change (shaped press) merged cleanly and is untouched.
- `docs/audits/README.md`: every row kept from both sides.
No icon button was added in this part, so nothing needed to go through `BouncingIconButton`.

**Runs.** Each run was under `systemd-run --user --scope -p MemoryMax=5G -p MemorySwapMax=0`, with Gradle at `-Xmx1536m`,
the Kotlin daemon at `-Xmx2g`, and Java temp at `~/.cache/forager-test-tmp`. No daemon was running at the start;
free disk was 3.8 to 4.1 GB throughout.
- Compile (`:app:compileDebugUnitTestKotlin`): succeeded first time, with no compile errors. The Kotlin daemon was
  then stopped before the tests.
- New and changed classes: 160 tests in 14 classes, 0 failures.
- Full suite (`:app:testDebugUnitTest`): **4,110 tests in 513 classes, 0 failed, 24 skipped**. That is the
  JUnit XML tally. The source has 3,883 `@Test` methods; the difference comes from abstract classes run under
  several configurations (for example the three `AvailabilityScreenLayout*` variants).
- No existing test broke.
- `./gradlew --stop` was run at the end; no Gradle or Kotlin daemon is left.

**Revert checks (8).** Each one saved a copy of the file, made a one-line edit (refused if it matched other than once or changed
nothing), ran the affected classes, checked the build log for compile errors (none in any of the eight), read the
JUnit XML, and restored the file from the saved copy. Afterwards `git status` was clean, so the forward state is HEAD.
1. `toDomain` passes `dailyRain = emptyList()`: both new parsing tests fail ("but was:<[]>").
   `AvailabilityScreenConditionsMonthTest`'s chart test does **not** fail, and could not: its fake provider builds
   the `ConditionsSummary` itself, past `toDomain`. My prediction listed it; the prediction was wrong, not the
   check. The screen test holds the path from the summary to the chart; the parsing tests hold `toDomain`.
2. Span index `(lag + 1) / spanDays`: three histogram and chart tests fail (spans `[1, 2, 0, 1, 0, 0]`).
3. Scale `<=` at the dry threshold: "threshold itself is Moist" fails (`MOIST` expected, `DRY`).
4. Host drops `onZoomedOutChanged`: the zoomed-out legend screen test fails (note not displayed).
5. Chip never draws the note: the same test fails.
6. `TripWindowRow` back to "MMM d": the trip window screen test fails (no "Aug 16, 2025 – Aug 18, 2025").
7. "Last rainy day" without the +1: the chart-model test and the conditions-month screen test fail ("3 days ago").
8. The Seasonal soil moisture row never added: the amendment's screen test fails.

**Still device-only.** The charts' drawing and fit, the scale's look at large font sizes, and the camera zoom trigger
inside `SightingsMap`.
