# Units follow: elevation, accuracy and soil temperature read in the Units setting (report, dispatch 2026-09-28-549)

Dispatch: `prompts/preserved/2026-10-06-02.md` on `records-after-173`. Owner's rulings: RECORD -547 ("Yes, both") and Amendment 1, RECORD -557 (accuracy in, the screen-reader rise converted with no phone check, `Locale.US` for soil temperature). Built on branch `units-follow` from `origin/main` at `4b72b25c`. Not merged.

## In plain terms

With Settings > Units on Imperial (US):

- **The compass strip's elevation** reads "689 ft", not "210 m".
- **The navigation HUD's elevation** reads "689 ft".
- **A sighting's bubble** reads "±39 ft accuracy", not "±12 m accuracy".
- **A trip window** reads "Soil temperature: 52.3°F", and the guidance text reads "roughly 50–68 °F".
- **The Return button's TalkBack sentence** reads "Return: 180° S · 0.7 mi · -148 ft". It used to mix miles and metres in one sentence. Nobody sees this sentence on screen; screen readers announce it.

On Metric every one of these reads exactly as before, with one exception the owner ruled on. On a phone set to a comma language (German, French and so on), soil temperature used to print "11,3°C" and now prints "11.3°C".

"Elevation unavailable" and "Accuracy not reported" are unchanged in both systems.

Nothing the app decides changed. Every stored value, model field, threshold and Open-Meteo request is still in metres and °C; only the text a person reads converts. That is the rule `formatRainfall` already followed.

## Verify before building (reported by message before building)

**1. The base.** `origin/main` was `4b72b25c`, 33 commits past the dispatch's `e1395fc9`. None of the five cited files, nor `domain/model/UnitSystem.kt`, changed between the two (`git diff --stat` empty). -516 had merged (PR #174), so the dispatch's "-516 unmerged" note was stale; it did not affect the work.

**2. The sweep.** The five sites were where the dispatch said:
- the HUD's elevation, `NavigationHud.kt:549`;
- the compass strip's elevation, `AvailabilityMapControlsUi.kt:530`;
- the rise from the start, `AvailabilityMapControlsUi.kt:582`;
- soil temperature, `AvailabilityResultsUi.kt:587`;
- the guidance band, `ForagingWeatherGuidance.kt:107-109`.

The sweep found one more, which stopped the build until the owner ruled: `accuracyLabel` (`AvailabilityPureFunctions.kt:259`, "±N m accuracy", shown in the sighting bubble at `AvailabilityMapOverlaysUi.kt:316`). The owner brought it in scope (-557).

Ruled out of scope (-557):
- **"Radius: N km"** (`SightingsMap.kt:1916`): written to a `"snippet"` property that nothing in `main` reads. That it is never shown is unverified on a device.
- **"each 11 km cell"** (`MapLegend.kt:31`): approved legend wording for a fixed grid size.
- **Soil moisture**, m³/m³: a ratio, with no units to convert.
- **Bearings, and the distance formatters' metric branches:** these already follow the setting.

**3. A wrong premise.** The rise at `:582` is not on the compass strip. It is only the Return button's `contentDescription` (`:235`, `:566`), since the strip's visible return readout was removed earlier (doc comment at `:572-577`). The owner's ruling: convert it, cover it by a test, and drop it from the phone check (-557).

**4. How the setting reaches each site.** No site needed more than one new parameter.
- **HUD:** already had `distanceUnit`, which `UnitSystem.forDistanceUnit` maps to a units system. No new parameter.
- **Compass strip:** `CompassElevationStrip` gained one `unitSystem` parameter, passed down to its inner composable. The caller passes `uiState.unitSystem`.
- **Rise:** already had `distanceUnit`.
- **Soil temperature:** `unitSystem` was already in scope.
- **Guidance:** `ForagingWeatherGuidance.forSelection` and `ForagingWeatherGuidanceSection` each gained `unitSystem`. The text is built on every draw, never cached or stored.
- **Bubble:** `ObservationBubble` gained `unitSystem`. `MapBubbleLayer` passes `UnitSystem.forDistanceUnit(sources.distanceUnit)`, the same unit `AvailabilityScreen` and `JournalTab` already put in `MapRecordSources`.

## What changed

Four new pure functions sit beside `formatRainfall` in `domain/model/UnitSystem.kt`:

- **`formatWholeLength`:** whole metres, or whole feet (metres ÷ 0.3048, `roundToInt`). Used for elevation and accuracy.
- **`formatElevationChange`:** the same, with the sign taken from the metre value as before.
- **`celsiusToFahrenheit`:** °F = °C × 9/5 + 32.
- **`formatSoilTemperature`:** one decimal and `Locale.US` in both systems.

The guidance band is whole °F under Imperial, from the one °C constant (10–20 °C becomes 50–68 °F).

Whole feet rather than tens of feet, as the dispatch proposed: metres show whole metres, so whole feet is like for like. I saw no case for coarser rounding in either system.

## Commits

- `a52fd87e`: plumbing. The setting is threaded to the six sites, and the formatters still print metric text in both systems, so the Imperial tests could compile and fail on their strings.
- `749ab711`: tests first, the Imperial and Metric cases.
- `00deb940`: the conversion.
- (this report): the report and its index rows.

## Tests, and how each failed first

Every new test goes through the site's real entry point:
- **The HUD and the compass strip:** through `AvailabilityScreen`, with an Imperial `UnitSystemPreferenceRepository` (`AvailabilityScreenMapIconStackTest`), and through `navigationReadout` (`NavigationHudReadoutTest`).
- **The Return sentence:** through `AvailabilityScreen`.
- **Soil temperature and the band:** through the real Tools drawer's Trip Planner section (`AvailabilityScreenLayoutTest`, which runs as three device-shape subclasses).
- **The bubble:** through `MapBubbleLayer` with the unit in `MapRecordSources`. The screen-level bubble tests in `AvailabilityScreenMapIconStackTest` are `@Ignore`d for the harness problem in `docs/audits/2026-08-31-search-dropdown-dismiss-chip-unmount.md`, so I drove the layer every map host composes instead.
- **The formatters and `accuracyLabel`:** directly.

Run against the plumbing commit (`749ab711`), every Imperial case failed, each naming its string:

| Test | Message |
|---|---|
| `UnitSystemFormatTest` (4) | `expected:<[374 ft]> but was:<[114 m]>`; `expected:<+[33 ft]> but was:<+[10 m]>`; `expected:<[52.3°F]> but was:<[11.3°C]>`; comma-locale `expected:<11[.]3°C> but was:<11[,]3°C>` |
| `AccuracyLabelTest` | `expected:<±[39 ft] accuracy> but was:<±[12 m] accuracy>` |
| `ObservationBubbleUnitsTest` | `expected:<±[39 ft] accuracy> but was:<±[12 m] accuracy>` |
| `NavigationHudReadoutTest` (2) | `expected:<[164 ft]> but was:<[50 m]>` |
| `ForagingWeatherGuidanceTest` | `expected "roughly 50–68 °F" in: …` |
| `AvailabilityScreenMapIconStackTest` (3) | HUD `expected:<[689 ft]> but was:<[210 m]>`; strip `… contains '689 ft' … is not displayed!`; Return `… ContentDescription = 'Return: 180° S · 0.7 mi · -148 ft' … is not displayed!` |
| `AvailabilityScreenLandscapeB2Test` | `… could not find any node that satisfies: (Text … contains '404 ft' …)` |
| `AvailabilityScreenLayout*Test` (×3 subclasses) | `… could not find any node that satisfies: (Text … contains 'Soil temperature: 52.3°F' …)` |

Every Metric case passed against the plumbing and passes after the conversion.

Three existing assertions ran under the Imperial default, so their expectations changed, as listed in -557:
- `NavigationHudReadoutTest`, two "50 m" assertions, now "164 ft".
- `AvailabilityScreenLandscapeB2Test`, "123 m", now "404 ft".

`NavigationHudReadoutTest` gained Metric assertions ("50 m", and "Elevation unavailable" under kilometres).

**One check that first did not run.** My first run filtered on `*AvailabilityScreenLayoutTest`. That class is an abstract base, run only through three subclasses, so the filter matched nothing, and the two soil-temperature screen tests were silently absent. I caught it by counting result files (7 classes, not 10) against the classes I had asked for. The re-run with `*AvailabilityScreenLayout*Test` is the one cited above, and the revert runner used the corrected filter from the start.

## Revert checks

Each site was reverted by one edit that passed `UnitSystem.METRIC` instead of the user's setting. Each run covered the ten affected classes.

The runner:
- deleted the old JUnit XML before every run;
- checked the build log for compile errors before reading any result;
- restored each file from a copy saved before editing, never from git, and compared it with that copy.

One revert, the strip's, would not have compiled as first written: `AvailabilityCompactMapUi.kt` does not import `UnitSystem`. I changed it to the fully qualified name before running.

| Revert | Compile errors | Failures (each names its own edit) | Restore |
|---|---|---|---|
| HUD (`NavigationHud.kt`) | none | 3: the screen HUD test `expected:<[689 ft]> but was:<[210 m]>`, and the two `NavigationHudReadoutTest` `expected:<[164 ft]> but was:<[50 m]>` | identical |
| Strip (`AvailabilityCompactMapUi.kt`) | none | 2: the strip test `'689 ft' … is not displayed!`, and B2 `'404 ft'` not found | identical |
| Rise (`AvailabilityMapControlsUi.kt`) | none | 1: `'Return: 180° S · 0.7 mi · -148 ft' … is not displayed!` | identical |
| Soil temperature (`AvailabilityResultsUi.kt`) | none | 3 (one per layout subclass): `'Soil temperature: 52.3°F'` not found | identical |
| Guidance band (`AvailabilityResultsUi.kt`) | none | 3 (one per layout subclass): `'roughly 50–68 °F'` not found. The soil row passed, as it should | identical |
| Accuracy (`MapBubble.kt`) | none | 1: `ObservationBubbleUnitsTest` `expected:<±[39 ft] accuracy> but was:<±[12 m] accuracy>` | identical |

Each revert ran 263 tests with 19 skipped (the existing `@Ignore`s in `AvailabilityScreenMapIconStackTest`). No failure belonged to another edit.

After all six, a grep found each forward call present exactly once, and `git diff HEAD` was empty.

## Full suite

- **Before**, `main` at `4b72b25c`: 3,719 tests, 0 failures, 0 errors, 24 skipped, in 457 classes. BUILD SUCCESSFUL in 7m 46s.
- **After**, `units-follow` at `00deb940`: 3,742 tests, 0 failures, 0 errors, 24 skipped, in 460 classes. BUILD SUCCESSFUL in 4m 46s, with no compile errors in the log. The difference is +23 tests (7 `UnitSystemFormatTest`, 3 `AccuracyLabelTest`, 2 `ObservationBubbleUnitsTest`, 2 `ForagingWeatherGuidanceTest`, 3 `AvailabilityScreenMapIconStackTest`, and 2 in `AvailabilityScreenLayoutTest` × 3 subclasses) and +3 classes, which is what was added; the skipped count is unchanged.

## Not verified

- **Nothing on a phone.** The device check below is for the owner's S22.
- **"Radius: N km" is never shown.** I read that nothing in `main` reads the `"snippet"` property; I have not seen it on a device.
- **The comma-locale change** is checked only by a unit test with `Locale.GERMANY` as the JVM default, not on a phone set to a comma language.
- **TalkBack** reading the converted rise aloud: covered by the `contentDescription` test only, with no phone check, per -557.
- **The bubble's unit on the Journal's maps.** `JournalTab` builds `MapRecordSources` with its own `distanceUnit`. I traced that value only as far as the scaffold's `distanceUnit` parameter (`AvailabilityCompactScaffold.kt:189`, passed at `:1229`), not to its caller, and no test drives the Journal's bubble.

## Device check, for the owner's S22 after this merges

Keep owner screenshots off GitHub.

1. Settings > Units: Imperial (US) > Maps.
   - **Pass:** the compass strip's elevation reads "N ft". (The rise is dropped from this step, per -557.)
2. Start navigating.
   - **Pass:** the HUD's elevation reads "N ft".
3. Availability results.
   - **Pass:** "Soil temperature: N.N°F", and the guidance text reads "roughly 50–68 °F".
4. Tap a sighting on the map.
   - **Pass:** its bubble reads "±N ft accuracy", or "Accuracy not reported".
5. Settings > Units: Metric > repeat steps 1 to 4.
   - **Pass:** metres and °C, exactly as before.
