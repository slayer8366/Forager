# Completion report: the units-system preference, rainfall in inches, and the per-fix instrument log

**Type:** completion report for the two parts of the return-estimate dispatch the owner ruled buildable now. **Items 1–3 are not built**; their rulings and the three proposals they wait on are the pre-build report's addendum.
**Date:** 2026-09-07. **Base:** `main` at `bc72811`. **Branch:** `claude/new-session-b7z9bg`, restarted from that `main`.
**Rulings built here:** #7 (inch precision: tenths with a trace floor), #8 (introduce a units-system preference, derive distance from it, convert rainfall only, report the others), #9's first step (the per-fix log for the instrument walk; the speed columns wait on the walk's result).

---

## What was built

### The preference: `UnitSystem`, with `DistanceUnit` derived from it

| Change | File |
|---|---|
| `enum class UnitSystem(label, distanceUnit) { METRIC, IMPERIAL }` with `forDistanceUnit` (a bijection), and `formatRainfall(mm, unitSystem, metricDecimals)` | `domain/model/UnitSystem.kt` (new) |
| `UnitSystemPreferenceRepository` (`getUnitSystem` / `setUnitSystem`, default `IMPERIAL`) | `domain/UnitSystemPreferenceRepository.kt` (new) |
| `DataStoreUnitSystemPreferenceRepository` — **same DataStore file** as the repository it replaces, new key `unit_system.selected`, and a read-only fallback to the legacy `distance_unit.selected` key mapped through `forDistanceUnit`, logged when it fires | `data/repository/DataStoreUnitSystemPreferenceRepository.kt` (new) |
| `DistanceUnitPreferenceRepository` and `DataStoreDistanceUnitPreferenceRepository` deleted, with their test | — |
| `AvailabilityUiState.unitSystem: UnitSystem = IMPERIAL` replaces the `distanceUnit` constructor parameter; `distanceUnit` is now a derived property, so every existing reader compiles and reads exactly what it did | `ui/availability/AvailabilityUiState.kt` |
| `applyUnitSystem` / `loadUnitSystemPreference` / `onUnitSystemSelected`; `onDistanceUnitSelected(unit)` kept as a one-line adapter through `forDistanceUnit` | `ui/availability/AvailabilityViewModel.kt` |
| The Settings control offers "Units: Metric / Imperial (US)", iterating `UnitSystem.entries` and selecting by the derived distance unit | `ui/availability/AvailabilityScreen.kt`, `DistanceUnitSection` only |
| Container and activity wiring renamed | `AppContainer.kt`, `MainActivity.kt` |

**Why `DistanceUnit` survives at all:** twenty-four test files and every distance formatter take it, and it is the right type for a distance display. It is no longer a preference; `UnitSystem.distanceUnit` is the one place one becomes the other, and the doc comments on both say so.

**Why the Settings callback keeps its old name.** `AvailabilityScreen.kt` threads `onDistanceUnitSelected: (DistanceUnit) -> Unit` through nine sites, and the dispatch holds that file's split at seams F and G, whose extent is not recorded in the repository. The control therefore hands over the chosen system by its distance unit — a bijection, so nothing is lost — and the ViewModel's adapter maps it back. The name now says less than the call does; it is recorded at the adapter as the split's job to rename. **One function in that file changed** (`DistanceUnitSection`: the header text, the entries iterated, the labels); no signature did.

**The legacy-key fallback, and why it is read-only.** A tester who chose kilometres under the old preference has `distance_unit.selected = KILOMETERS` on disk. The new repository reads it when its own key is absent, maps it to `METRIC`, and logs that it did. It never writes or deletes the legacy key: a downgraded build would still find it, and nothing is gained by removing it. Once the user chooses in the new control, the new key wins.

### Rainfall in the user's units

`formatRainfall(mm, unitSystem, metricDecimals = 1)`:

- **Metric:** each site's existing precision and no-space form, byte for byte — "12.4mm", "12mm" — so no metric reader sees a change. (`Locale.US` for the decimal point, a deliberate small fix: the old inline `"%.1f".format` used the device locale and would print "12,4mm" on a comma-locale phone.)
- **Imperial:** tenths of an inch — "0.5 in" for 12.4 mm, "0.1 in" for 3.2 mm — with the trace floor: anything above zero that would round to "0.0 in" prints `"< 0.1 in"`; exactly zero prints "0.0 in", because no rain is not a trace.

Threaded to all seven sites the pre-build report listed (`AvailabilityResultsUi.kt` × 5 through `ConditionsCard`, `TripWindowReportContent` and `TripWindowRow`, now taking `unitSystem`; `AvailabilityPureFunctions.noTripWindowMessage(report, unitSystem)` × 2). The domain thresholds (`FruitingPatternAssumptions`, `SIGNIFICANT_RAIN_MM`) are untouched and still in millimetres.

### The per-fix instrument log

`AndroidLocationTracker.onLocationChanged` now logs, at debug level under tag `ForagerFix`, everything the platform reports per fix that the app discards: `provider`, `acc`, `hasSpeed`/`speed`, `hasSpeedAccuracy`/`speedAccuracy`, `hasBearing`, `time`. The fix delivered downstream is unchanged. **The walk:** record a track as usual with the phone on USB or with `adb logcat -s ForagerFix` captured afterwards from a bug report; the question it answers is whether GNSS fixes carry `hasSpeed=true` with a `speedAccuracy` under about half a metre per second, and whether `hasSpeed=false` lines up with `provider=network`. The pre-build report's §2.1 says what each answer leads to.

### Reported, not converted — the remaining unit misses

Still fixed metric, each now one call away from `uiState.unitSystem`:

| Quantity | Site | Shown as |
|---|---|---|
| Soil temperature | `AvailabilityResultsUi.kt`, `"Soil temperature: ${"%.1f".format(temp)}°C"` | °C |
| Elevation, live fix | `NavigationHud.kt`, `"${it.roundToInt()} m"` | m |
| Elevation, compass strip | `AvailabilityScreen.kt`, `"${it.roundToInt()} m"` | m |
| Elevation difference to start | `AvailabilityScreen.kt`, `"+${it.roundToInt()} m"` | m |

Two of the three elevation sites are in the held file. All wait on the owner's say-so, per the ruling.

---

## Tests

### New and changed

| Class | Cases | What they pin |
|---|---|---|
| `UnitSystemTest` (JVM, new) | 5 | the derivation both ways; imperial tenths (five literals worked by hand: 12.4 → "0.5 in", 3.2 → "0.1 in", 38.1 → "1.5 in", 25.4 → "1.0 in", 100 → "3.9 in"); the trace floor (1.0, 0.5, 0.01 mm → "< 0.1 in"; 0.0 → "0.0 in"); metric byte-identical at both precisions |
| `DataStoreUnitSystemPreferenceRepositoryTest` (Robolectric, new; replaces the distance-unit one) | 6 | default imperial; round trip; replace; **legacy KILOMETERS → METRIC; legacy MILES → IMPERIAL; a saved system outranks the legacy key**. The legacy key is planted through a separate DataStore handle on its own cancellable scope, joined before the repository opens the file — DataStore allows one active instance per file |
| `AvailabilityViewModelDistanceUnitTest` (adapted) | 7 | restores the persisted system and the derived unit; **selecting a system directly** updates the derived unit and the radius default; the existing radius-default and persistence cases on the new API |
| `AvailabilityScreenLayoutTest` (two concrete classes, +1 each) | 18 + 18 | the existing "12.4mm" case now pinned to `METRIC`; **a new imperial case: "0.5 in of rain in the last 14 days" displayed and the metric string absent**; the Settings header "Units" with both system labels |
| `AvailabilityScreenConditionsMonthTest` | 6 | fake pinned to `METRIC` with a comment, so its month-gating strings stay byte-identical |
| `AvailabilityScreenOfflineCacheTest` | 6 | state fixture `unitSystem = METRIC` where it was `distanceUnit = KILOMETERS` |
| `AndroidLocationTrackerTest` (+1) | 3 | the exact log line for a network fix with speed 1.2 and accuracy 0.3, via `ShadowLog`, and the delivered fix unchanged |
| thirteen other test files | — | mechanical: `distanceUnitPreferenceRepository =` → `unitSystemPreferenceRepository =`, fakes renamed onto the new interface, `IMPERIAL` where they returned `MILES` |

### Reverted-variant check

Copies of the three files saved to the scratchpad before editing; three one-line reverts applied together, each aimed at disjoint tests; build log checked for compile errors before the XML was read (0); XML confirmed fresh (0 s).

| Revert | Edit | Predicted | Observed |
|---|---|---|---|
| A | `formatRainfall` imperial branch prints the metric form | `UnitSystemTest` × 3 (tenths, trace floor, metric-decimals-ignored); the imperial layout case × 2 classes | all five |
| B | the legacy-key read is skipped | the two legacy migration cases | **one** — see below |
| C | the per-fix `Log.d` never runs | the tracker log case | that one |

**50 tests, 7 failed** against 8 predicted. The miss was **"a legacy miles choice is carried forward as imperial"**, which passed with the fallback removed because imperial is also the default: the test could not tell migration from defaulting, and only the revert showed it. Strengthened to also assert the repository's own migration log line (`ShadowLog`, tag `UnitSystemPreference`), then run green forward (6/6) and again under revert B alone: **2 of 2 predicted failures**, the strengthened case failing on the missing log line and the kilometres case on the value. Restored from the saved copies; `grep REVERT`: 0 across all three files; the only working-tree difference from `b6127b2` afterwards was the strengthened test itself.

This is the pattern CLAUDE.md warns about from the other side: a green test that would stay green with its behaviour removed. The revert runner found it because it reported one fewer failure than predicted and the discrepancy was chased rather than rounded.

### Full suite

<!-- FULL_SUITE -->

### What has no test, and why

- **The Settings control's radio selection** as a real touch. `AvailabilityScreenLayoutTest` asserts the header and labels are displayed; selecting one goes through `onDistanceUnitSelected`, which the ViewModel test covers at the adapter. A coordinate touch on the radio in the held file was not added.
- **The log line on a real GNSS fix.** `ShadowLog` proves the format; whether `hasSpeed` is true on the owner's phone is the walk.
- **The `Locale.US` fix** has no comma-locale test; Robolectric's default locale prints a point either way.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed:** the seven rainfall sites and their precisions; the one Settings control and its nine plumbing sites; the DataStore file name and legacy key; the twenty-four test files that construct with a `DistanceUnit`; the thirteen that construct the ViewModel with the repository; that `hasSpeedAccuracy` is API 26 (`javap`, pre-build report); the exact log line under `ShadowLog`; every count in the tables above.

**Inferred:** that DataStore's active-instance check is released on scope completion (it was, in the test — the three legacy cases failed with "multiple DataStores active for the same file" until the planting scope was cancelled and joined, then passed); that Open-Meteo's unit defaults are millimetres and Celsius (its documentation).

### What I could not determine

- What seams F and G of the `AvailabilityScreen.kt` split cover — not recorded in the repository. One function in that file was edited; if that function is inside a held seam, the owner should say so and the edit can move.
- Whether the owner's phone reports Doppler speed — the walk.

### Premises in this dispatch that were wrong

None new. The dispatch's Item 4 premise — no single place answers the unit question — was right, and is what this builds.

### Anything I decided that this dispatch did not cover

- **The Settings labels** "Units", "Metric", "Imperial (US)" — wording the ruling did not specify. "(US)" because the app's users are US foragers and "Imperial" alone reads British.
- **The legacy-key fallback** — the ruling said introduce the preference; carrying a tester's existing choice across was not asked for and not forbidden, and losing it silently would be a regression nobody chose.
- **`Locale.US`** in the formatter — a fix to a latent comma-locale bug in the inline formatting it replaces; noted, small, reversible.
- **Keeping `onDistanceUnitSelected` as the screen's callback name** — a seam-avoidance choice, recorded at the adapter.
- **Exposing the tracker's companion as `internal`** so the test can name the log tag — visibility only.
