# Handoff: Seasonal Visualizer (fruiting-lag validation)

Written by an EGD coder session that read the actual repo and did research/design
only — **no feature code has been written yet.** This file exists so a different
session (any account) can pick this up with git history-level detail rather than
re-deriving what's below. Delete this file in the PR that finally ships the
feature; it is scaffolding, not documentation of the shipped app.

Branch: `feature/seasonal-visualizer`, created off `main`, currently containing
only this file.

Full task spec: see the coder-task prompt this branch was created from (not
reproduced here — ask whoever resumes this for it, or check the session that
created the branch). The short version: build a "Seasonal" tab that tests the
existing `FruitingPatternAssumptions.FRUITING_LAG_DAYS` (7–21 day) rain-to-
fruiting-lag rule of thumb against real historical iNaturalist sightings and
real historical Open-Meteo rainfall, and reports what it finds as a labelled
estimate with sample size attached — explicitly not fed back into the ranked
list's `relativeLikelihood`.

## What was actually verified against a live API, and what was not

**iNaturalist ordering — verified live.** `GET
https://api.inaturalist.org/v1/observations?lat=45.5&lng=-122.6&radius=15&month=8&iconic_taxa=Fungi&per_page=5`
returned, in order:

```
391978607  observed_on=2026-08-16  created_at=2026-08-16T20:02:55-07:00
391934543  observed_on=2026-08-15  created_at=2026-08-16T16:53:55-07:00
391929053  observed_on=2026-08-16  created_at=2026-08-16T16:34:51-07:00
391928659  observed_on=2026-08-16  created_at=2026-08-16T16:33:03-07:00
391898832  observed_on=2026-08-16  created_at=2026-08-16T14:46:28-07:00
```

Default (unspecified `order_by`) ordering is by recency (`created_at`
descending, `id` descending) — not, e.g., relevance or an arbitrary default.
This supports the spec's requirement to phrase the 200-cap caveat as "based on
**up to 200** observations" rather than implying a most-recent-first guarantee
is false, or that the 200 are a random/unbiased sample.

**Open-Meteo historical archive API — NOT verified. This is the important
finding for whoever resumes this.** I did not confirm the endpoint path,
parameter names, response shape, rate limits, or per-request date-span limit
for `archive-api.open-meteo.com/v1/archive`, because this sandbox's egress
proxy blocks it outright:

```
$ curl https://archive-api.open-meteo.com/v1/archive?...
curl: (56) CONNECT tunnel failed, response 403
```

I also tried the **already-shipped** `api.open-meteo.com/v1/forecast` endpoint
(the one `OpenMeteoWeatherProvider` uses in production today) as a sanity
check, expecting it to work since the app already depends on it — it is
**also blocked** in this sandbox:

```
$ curl https://api.open-meteo.com/v1/forecast?latitude=45.5&longitude=-122.6&daily=precipitation_sum&forecast_days=1
curl: (56) CONNECT tunnel failed, response 403
```

`curl $HTTPS_PROXY/__agentproxy/status` confirms both as policy denials, not
transient failures:

```json
{"ts": "...", "kind": "connect_rejected",
 "detail": "gateway answered 403 to CONNECT (policy denial or upstream failure)",
 "host": "archive-api.open-meteo.com:443"},
{"ts": "...", "kind": "connect_rejected",
 "detail": "gateway answered 403 to CONNECT (policy denial or upstream failure)",
 "host": "api.open-meteo.com:443"}
```

This session's own environment instructions say not to retry or route around a
403/407 from this proxy, and to report the blocked host instead — so I stopped
there rather than trying workarounds. **Both `api.open-meteo.com` and
`archive-api.open-meteo.com` need to be reachable for this feature's
verification step to happen at all**; a session with only iNaturalist access
(like this one) cannot do it, and that's worth flagging up front rather than
discovering it again on the next attempt.

Consequence: `scripts/verify-open-meteo-historical-fields.sh` (required by the
spec, following `scripts/verify-open-meteo-fields.sh`'s shape) has not been
written yet, and once written it will need to be *run* somewhere with real
network access before anyone can trust the historical integration's assumed
shape (daily-block-shaped-like-the-forecast-API, `start_date`/`end_date`,
`daily=precipitation_sum`, `timezone=auto`, no documented single-request
date-span cap). All of that is currently unverified assumption carried over
from general knowledge of Open-Meteo's public docs, not a measurement.

## Three design decisions mapped — two settled, one open

### 1. Fruiting-lag bucket boundaries — settled

Add to `domain/FruitingPatternAssumptions.kt`:

```kotlin
val FRUITING_LAG_BUCKETS: List<IntRange> = listOf(0..6, FRUITING_LAG_DAYS, 22..35, 36..Int.MAX_VALUE)
```

Reuses `FRUITING_LAG_DAYS` by reference (not a copied `7..21`) so the
highlighted bucket can never drift from the rule of thumb it's testing —
same single-source-of-truth reasoning the file already uses for
`SIGNIFICANT_RAIN_MM`. The open (`Int.MAX_VALUE`) top bucket needs
`AvailabilityScreen`'s chart/label code to special-case rendering it as
"36+ days" rather than printing the literal upper bound — not written yet.

### 2. Historical API request chunking — settled (given the verification gap above)

Plan: implement a **single unchunked request per search** (fetch the full
padded date range in one call), which the spec calls the common case. Do
**not** invent a chunk-size threshold, since the real per-request span limit
(if any) is exactly the thing that couldn't be verified live (see above).
Flag this as a labelled, adjustable assumption in a doc comment on
`OpenMeteoHistoricalWeatherProvider`, and record in the PR's "Not yet
verified" section that chunking may need to be added once someone can run
the verify script for real. This is the honest version of "planned separate
work," not a silent gap.

### 3. `FruitingLagDistribution.possiblyIncompleteSampleSize` — OPEN, needs a decision before implementing

This is the one still worth a second pair of eyes. The spec's field doc:
"True if the sightings fetch plausibly hit `INaturalistApi`'s per_page cap."

**The data needed already exists in the DTO and is currently thrown away.**
`data/remote/dto/ObservationsResponseDto.kt` (read directly, not guessed):

```kotlin
data class ObservationsResponseDto(
    @SerialName("total_results") val totalResults: Int = 0,
    @SerialName("page") val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("results") val results: List<ObservationDto> = emptyList(),
)
```

`INaturalistMushroomRepository.getSightings` currently does
`.map { response -> response.results.mapNotNull(::toDomain) }` — it reads
`response.results` and drops `totalResults`/`perPage` on the floor. That
comparison (`totalResults > results.size`, or equivalently `>= perPage`) is
the *accurate* signal for "iNaturalist had more than we're seeing," and it's
computed **before** `toDomain`'s location-filtering drops observations
iNaturalist gave no mappable position for — so it doesn't have the undercount
problem below.

Options considered, in order of how far I got:

- **(a) Cheap heuristic, has a known flaw — this is what I was about to build
  when interrupted.** Move the literal `200` out of
  `INaturalistApi.getObservations`'s `perPage: Int = 200` default and into a
  new domain-owned constant (`MushroomRepository.SIGHTINGS_PER_PAGE_CAP =
  200`, with the API interface's default referencing it — same pattern as
  `FruitingPatternAssumptions.RAIN_DAY_MIN_MM` /
  `OpenMeteoWeatherProvider.SIGNIFICANT_RAIN_MM`). Then in
  `ComputeFruitingLagDistributionUseCase` (which only sees `List<Sighting>`,
  matching the spec's exact stated signature — no `ObservationsResponseDto` in
  scope there), flag `possiblyIncompleteSampleSize = sightings.size >=
  SIGHTINGS_PER_PAGE_CAP`.
  **Known flaw, not hidden:** this runs *after* `INaturalistMushroomRepository`
  has already dropped unmappable observations
  (`parseLocation`-returns-null → filtered out in `toDomain`). A fetch that
  hit the raw 200-result cap but had, say, 20 unmappable observations returns
  180 `Sighting`s, and `180 >= 200` is false — a silent false negative on
  `possiblyIncompleteSampleSize` exactly in the case it exists to catch.
- **(b) Accurate fix, bigger blast radius, not started.** Thread
  `totalResults`/`perPage` out of `INaturalistMushroomRepository.getSightings`
  somehow — e.g. widen `MushroomRepository.getSightings`'s return type to
  carry both the sighting list and a "was this list truncated by the API"
  flag, computed from the DTO fields that are already sitting right there.
  Rejected *for now* only because `MushroomRepository.getSightings` is called
  from `GetSightingsUseCase`, which is also the Map tab's data source
  (`AvailabilityViewModel.onMapTabSelected`) — widening its contract touches a
  call site this feature has no business touching, which is a bigger blast
  radius than the spec asked for. But per the DTO reading above, this is
  clearly the *correct* fix, not a nice-to-have; (a) is the compromise, not
  the good option.

**This wasn't resolved because I got interrupted before deciding, not because
it's unresolvable.** Whoever resumes should either pick (a) explicitly (and
carry the known-flaw doc comment into the code, per CLAUDE.md — no silent
gap) or take the slightly bigger scope of (b). I lean toward (b) now, having
actually read the DTO and seen the data is already there — cheap signal
that's silently wrong in exactly the case it matters most is worse than a
slightly wider interface change.

## What was ruled out, and why

- **Reusing `PrecipitationResponseDto`/`DailyPrecipitationDto` (the forecast
  DTO) for the historical archive response**, since the two response shapes
  will likely overlap on `daily.time`/`daily.precipitation_sum`. Rejected on
  purpose: the spec's "this interface is the only place that speaks the
  historical-archive endpoint's shape" rule, and CLAUDE.md's
  isolated-integration-layer rule, both argue for a dedicated
  `HistoricalPrecipitationResponseDto` so the forecast and archive wire
  formats can't silently couple and then drift when Open-Meteo changes one
  but not the other. Not implemented yet, just decided.
- **Retrying the blocked hosts through the proxy, or trying an alternate
  route.** Confirmed policy-level (403, `connect_rejected`, "policy denial")
  via the status endpoint, not a transient/DNS/timeout failure — retrying
  would just be noise, and the environment's own instructions say to report
  it instead.

## Codebase facts verified by reading the actual files (confidence: read directly, not guessed)

- `ComputeTripWindowsUseCase.findSoakingEvents` is `internal` in that class's
  companion object (`domain/ComputeTripWindowsUseCase.kt`) — callable
  app-wide without modification, confirmed by reading the file.
- `FruitingPatternAssumptions.FRUITING_LAG_DAYS = 7..21`,
  `RAIN_DAY_MIN_MM = 2.0`, `SOAKING_EVENT_MIN_TOTAL_MM = 10.0`,
  `OBSERVED_HISTORY_DAYS = 14` — all as the task spec stated, confirmed.
- `MushroomRepository.getSightings(region, month, filter)` already fetches
  all-years, single-month sightings; `GetSightingsUseCase` wraps it with
  most-recent-first sorting. Reusable as-is for this feature's species side.
- `INaturalistApi.getObservations` requests `per_page = 200`, no pagination —
  confirmed exactly one place, the interface's default parameter value.
- `TaxonFilter` is a sealed interface (`domain/model/TaxonFilter.kt`) with
  `IconicCategory` and `SpecificTaxon` cases — the spec's pooled-vs-scoped
  branch maps directly onto `is TaxonFilter.IconicCategory` /
  `is TaxonFilter.SpecificTaxon`, no new type needed.
- `AvailabilityScreen`'s tab switcher (`ResultsTab` enum, currently
  `LIST`/`MAP`, `ui/availability/AvailabilityScreen.kt`) and
  `AvailabilityViewModel`'s lazy-fetch-on-tab-open pattern
  (`onMapTabSelected`, `loadedSightingsQuery` keyed cache) are the patterns to
  mirror for a lazy Seasonal tab fetch.
- No existing `Canvas`-based chart anywhere in the codebase (only
  `SightingsMap.kt`'s unrelated `Canvas` use for the osmdroid overlay) — the
  hand-rolled bar chart this feature needs has no prior art to reuse from.
- `runCatchingCancellable` (`data/repository/RunCatchingCancellable.kt`) is
  the required wrapper for any new suspending network call.
- `OpenMeteoClient.kt` is the Retrofit-builder pattern to mirror for a new
  `OpenMeteoHistoricalClient` pointed at `archive-api.open-meteo.com`
  (different base URL from the existing `OpenMeteoClient`).
- `ObservationsResponseDto` already carries `totalResults`/`perPage` — see
  design decision 3 above.

## Files planned but not yet created (none of this exists on disk yet)

- `domain/HistoricalWeatherProvider.kt`
- `domain/model/FruitingLagDistribution.kt` (`FruitingLagDistribution` +
  `LagBucket`)
- `domain/ComputeFruitingLagDistributionUseCase.kt`
- `domain/GetSeasonalPatternUseCase.kt`
- `MushroomRepository.SIGHTINGS_PER_PAGE_CAP` addition (or the wider fix from
  decision 3b instead)
- `data/remote/OpenMeteoHistoricalApi.kt` + a dedicated
  `data/remote/dto/HistoricalPrecipitationResponseDto.kt` +
  `data/remote/OpenMeteoHistoricalClient.kt`
- `data/repository/OpenMeteoHistoricalWeatherProvider.kt`
- `AppContainer` wiring for all of the above
- `AvailabilityUiState` / `AvailabilityViewModel` additions: `seasonalPattern`,
  `isLoadingSeasonalPattern`, `seasonalPatternErrorMessage`, a lazy
  `onSeasonalTabSelected()` keyed the same way `onMapTabSelected` is
- `AvailabilityScreen`: new `SEASONAL` tab + hand-rolled Compose `Canvas` bar
  chart + sample-size/no-preceding-event/caveat text
- `scripts/verify-open-meteo-historical-fields.sh`
- Tests: `ComputeFruitingLagDistributionUseCaseTest`,
  `OpenMeteoHistoricalWeatherProvider` parsing/request tests,
  `GetSeasonalPatternUseCaseTest`, ViewModel lazy-fetch/caching/branch tests,
  a Compose/Robolectric test for the Seasonal tab's on-screen text
- README: new numbered "How it works" item + "Project layout" updates

## Recommended next step

Resolve design decision 3 (probably take option (b), now that the DTO is
confirmed to already carry what's needed), then implement top-down: domain
models → domain use cases (headless-testable without any network) → data
layer (blocked on verification, build it anyway against documented shape,
flag as unverified) → `AppContainer` wiring → ViewModel → UI → tests → README
→ the verify script (write it even though it can't be run here) → PR, with
the PR description explicitly repeating the "could not verify against
`archive-api.open-meteo.com` or `api.open-meteo.com` from this environment"
finding so it isn't lost between sessions.
