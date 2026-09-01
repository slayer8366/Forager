# Forager — Status Audit

**Date:** 2026-08-28
**Scope:** Full repository, read-only. No code changes were made to produce this document.
**Base checked:** `main` at `d432660` (2026-08-27), the branch this session started from; cross-checked
against GitHub for anything newer (open PRs, live CI). This document does not itself change any
code — it is a snapshot per the audit request.

A note on method, since it affects how to read this doc: this repo already writes an unusually
detailed, self-auditing README and keeps a `docs/audits/`/`docs/qc/` trail of point-in-time
records. Where this document repeats a claim from those sources, it was checked against the
current source or a live GitHub query, not merely relayed — and where a discrepancy turned up
(see §3), it's called out rather than smoothed over. Where something could not be independently
checked in this pass, it's marked unverified rather than asserted.

---

## 1. Stack & structure

**Two independent projects in one repo:**

- **The Android app** (root + `app/`) — the actual product. Kotlin, single Gradle module (`:app`),
  Jetpack Compose UI on Material3 Expressive (`androidx.compose.material3` pinned to the
  `1.5.0-alpha26` pre-release channel — deliberate, see `gradle/libs.versions.toml`'s own comment).
  `compileSdk`/`targetSdk` 37, `minSdk` 26 (`app/build.gradle.kts:118-123`). Kotlin 2.3.10, AGP
  9.3.1, Gradle wrapper 9.7.0, JDK 17 target / JDK 21 toolchain.
- **`server/pmtiles-worker/`** — a separate TypeScript Cloudflare Worker (own `package.json`,
  `wrangler.toml`, not part of the Gradle build) that serves offline map vector tiles out of a
  Protomaps PMTiles extract in Cloudflare R2. Deployed independently of the app's own CI.

**Entry points:**
- `ForagerApplication` (`app/src/main/java/com/forager/app/ForagerApplication.kt`) — owns the one
  `AppContainer` for the process's lifetime.
- `MainActivity` (`.../MainActivity.kt`) — the app's only Activity. A single Compose tree wires
  three ViewModels (`AvailabilityViewModel`, `MushroomLogViewModel`, `TrackRecordingViewModel`),
  all hand-constructed from `AppContainer` via `viewModelFactory { initializer { ... } }`
  (`MainActivity.kt:42-104`).
- `TrackRecordingService` (`.../service/TrackRecordingService.kt`) — a foreground service, started/
  stopped by Intent action from `MainActivity`, not by binding.
- `AppContainer` (`.../AppContainer.kt`) — **the only dependency graph.** No DI framework; a single
  hand-wired class, ~175 lines, doc-commented "the graph is small enough not to need one"
  (`AppContainer.kt:79`).

**Layering** (verified by reading the files, not just the README's own description of them):
`domain/` is pure Kotlin with zero Android imports (confirmed: no `android.*` import in any file
under `app/src/main/java/com/forager/app/domain/`), making it unit-testable headless. Every
external integration is wrapped behind an owned interface implemented in exactly one adjacent
package — `location/` (Android location APIs), `map/` (MapLibre's `OfflineManager`), `photo/`
(`ActivityResultContracts`/`FileProvider`), `sensor/` (`SensorManager`), `data/remote` + `data/repository`
(Retrofit/iNaturalist, Open-Meteo forecast, Open-Meteo historical archive) and `data/local` (Room).
This matches CLAUDE.md's own architecture rule and is consistently applied, not just stated.

**Size:** 196 Kotlin files under `app/src/main`, 102 under `app/src/test`.

**Persistence:** Room (`ForagerDatabase`, schema version **9**, see §3) for anything relational —
planned trips, the search-result cache, the mushroom log, tracks/waypoints, offline map regions.
DataStore Preferences for flat settings (map preferences, distance-unit preference). This split is
itself a documented, twice-independently-derived project convention (CLAUDE.md's "Room for data
that relates; DataStore for flat settings" pitfall).

**Networking:** Retrofit + OkHttp + kotlinx.serialization against three hosts: iNaturalist's public
API (species search, observations), Open-Meteo's forecast API (current conditions), and Open-Meteo's
separate historical-archive API (`archive-api.open-meteo.com`, its own DTOs — `HistoricalPrecipitationResponseDto`).

**Maps:** MapLibre GL Android SDK `13.5.0` (migrated off `osmdroid`, which is fully removed — no
`osmdroid` import found anywhere in `app/src/main`). Three live basemaps (Street, Topographical,
Satellite), all plain style-URL templates, no vendor SDK naming a tile source directly
(`ui/map/BasemapStyles.kt`). Offline tiles are a distinct fourth source: the self-hosted
`pmtiles-worker`.

---

## 2. What actually works

The README (`README.md:16-425`) is an unusually accurate, current, file-cited description of every
shipped flow, and reads more like a living spec than marketing copy. Rather than re-describing it
end to end, this section states what's **confirmed working today on `main`**, what's **stubbed or
partially built**, and — separately — what's sitting **unmerged** and therefore not actually live
yet, since that distinction matters and the README doesn't always make it explicit.

### Complete and shipped (on `main`, `d432660`)

- **Species availability search & ranking** — iNaturalist `species_counts` query, normalized
  relative-likelihood ranking (`PredictAvailabilityUseCase`), List tab.
- **Map tab** — per-observation dots plotted as MapLibre GeoJSON sources/style layers. (A
  DBSCAN-based "foraging areas" clustering layer shipped earlier in this project's history and was
  removed: it landed cluster centroids at unsafe locations — rivers, highways, schools, private
  property — with no trail/terrain/ownership data in this codebase to avoid that.)
- **Three basemaps** with per-basemap zoom ceilings enforced explicitly (`SightingsMap`'s
  `setMaxZoomPreference` calls), not trusted from a tile source's own claimed range — this is a
  repeat of a real bug the project already had once with `osmdroid`.
- **Offline map downloads** — self-hosted Cloudflare Worker + R2/Protomaps PMTiles, multi-region
  management. This specific piece has real hardware confirmation on record (README:187-219; PR #25's
  description, independently verified via the GitHub API: "download completes ... survives a genuine
  force-close/clear-recents/restart cycle").
- **Current Conditions card** (recent rainfall, Open-Meteo forecast API), current-month-only.
- **Seasonal tab** — fruiting-lag histogram tested against real historical rainfall
  (`GetSeasonalPatternUseCase`), explicitly informational: never writes back into the ranking.
- **Offline search cache** — 5-entry LRU in Room, "Offline — showing results saved N ago" banner,
  matched on exact region+month+filter equality.
- **Mushroom log, Phase 1 (local only)** — structured field-note entry, three-state fields
  (`Observed<T>`/`Feature<T>`) enforced by the type system (`ThreeStateModelTest` checks this at
  the compiled-class level, not just at compile time), camera/gallery photos, autosave-on-edit,
  persisted drafts. **Never identifies the mushroom** — a stated safety property, not a scope cut
  (README:359-365).
- **Track recording (Phase 1a)** — foreground service, GPS breadcrumb sampling
  (`LocationSampler`), waypoints, batched writes. Off-track *detection* is built
  (`DetectOffTrackUseCase`, 5 tests) and drives `TrackRecordingUiState.isOffTrack` correctly.
- **Responsive layout** — a real branch, not a restyle: `WindowWidthClass.COMPACT` gets a 5-tab
  bottom nav + full-bleed map (`CompactMapTab`, `CompactSearchDrawerContent`, etc.); medium/expanded
  keeps a permanent drawer + side-by-side List/Map. Verified by both existing and passing today
  (see §4) — not just described.

### Stubbed, partially built, or silently incomplete

- **Off-track push notification and check-in timer are not built**, despite the UI state existing.
  Confirmed by grep: `off_track_notification_*` string resources
  (`app/src/main/res/values/strings.xml`) are declared but referenced nowhere in `app/src/main`;
  no `AlarmManager` usage exists anywhere in the app. A user could reasonably believe this safety
  feature is live — the toggle and the `isOffTrack` state both work — but nothing ever posts the
  alert. This matches the README's own "Not yet built" note (README:988-996) and is still true
  against current `main`.
- **`GetTracksUseCase`, `DeleteTrackUseCase`, `ComputeTrackStatisticsUseCase` are wired but
  unconsumed.** All three are constructed in `AppContainer.kt:164-166`, and grepping
  `app/src/main/java` for each name finds no call site beyond that construction — there is no
  track-history or track-list screen yet to use them. This matches `TrackRecordingService`'s own
  doc comment: "Phase 1a builds this service and its domain logic only; the UI ... is Phase 1c"
  (`TrackRecordingService.kt:50-53`) — the plan accounts for this, but it's real, currently-dead
  code today. Only `ComputeTrackStatisticsUseCase` has a dedicated test
  (`ComputeTrackStatisticsUseCaseTest.kt`); the other two have none.
- **Mushroom log Phase 2 (iNaturalist upload) is entirely unbuilt.** `LogSyncState` models
  `Draft`/`Uploading`/`Uploaded`/`Failed`, but grepping the codebase confirms only `Draft` is ever
  constructed. Blocked on the app owner registering an iNaturalist OAuth application — nobody else
  can unblock it (README:416-425).
- **Nothing renders on real hardware from inside this development environment.** This session, like
  the ones before it per the README's own running log, has no `/dev/kvm` (confirmed: the emulator
  is not usable here). Every claim about how the MapLibre map actually looks, camera behavior,
  marker legibility, and gesture handling is reasoned-through and Robolectric/JVM-tested, not
  rendered. The README is careful to say the project owner *does* periodically verify specific
  pieces on a real device outside this loop (the live-location puck, the photo strip layout) — but
  that confirmation lags whatever's most recently merged, and this audit found no way to check
  which of `main`'s current map-rendering code has and hasn't had that hardware pass yet.

### Built but not yet merged to `main` — currently sitting in **open PR #50**

Checked live via the GitHub API (not relayed from a doc): PR #50, "L4 close-out: destructive-migration
fallback, photo/theme fixes, distance units, night mode," opened 2026-08-27, 12 commits, `+1942/-682`,
**not merged**. What this means concretely: as of `main`'s current head, a user running this app today
does **not** have:
- The `fallbackToDestructiveMigration` removal (see §3/§7 risk below — the fix is written, just unmerged).
- The persisted km/mi display-unit preference (defaults to miles once merged).
- Multi-photo gallery picking on a log entry (currently capped at one photo via `PickVisualMedia`;
  PR #50 replaces it with `PickMultipleVisualMedia`, capped at 10).
- PR #49's night-mode rework (two direct Settings checkboxes replacing the civil-twilight/long-press
  toggle) and the map icon bar/compass strip theme-reading fix.

PR #50's own CI (`Build, test, publish APK`) is green as of this check. Its `mergeable_state` reads
`unstable`, which — per the same pattern already documented in this repo for PR #25/#42 — reflects
the separate, non-required, currently-failing "Workers Builds: forager-pmtiles" Cloudflare check
(see §3), not a real merge conflict.

The other open PR, **#26** ("Add multi-region offline map management"), is a stale, **draft**,
137-commits-behind reference branch — explicitly *not* the implementation path forward per
`docs/plans/README.md`'s own row for it; the actual multi-region work landed piecemeal through
later, separate PRs (#39, #42).

---

## 3. Debt

**No literal `TODO`/`FIXME`/`HACK`/`XXX` markers exist anywhere in `app/src`** — grepped directly,
zero matches. Whatever debt this codebase carries is structural or a stated future-phase gap, not
marked-and-abandoned code.

- **`AvailabilityScreen.kt` is 5,109 lines** (`app/src/main/java/com/forager/app/ui/availability/AvailabilityScreen.kt`,
  measured directly). The project's own `docs/plans/understory-design-system.md` already names
  splitting this file as the prerequisite for a scoped-but-undispatched "layout phase"
  (`docs/plans/README.md` row for Understory, current as of the 2026-08-26 correction pass). It was
  cited at 4,830 lines when that note was written and has grown ~280 lines since; the split still
  hasn't started. This is the single highest blast-radius file in the repo for merge conflicts,
  given this project's habit of parallel branches touching the map/availability surface at once.
- **Dead-but-wired use cases** — see §2 (`GetTracksUseCase`/`DeleteTrackUseCase`/
  `ComputeTrackStatisticsUseCase`). Not urgent, but exactly the kind of code that bit-rots silently:
  nothing exercises two of the three, so nothing would catch a break until a future track-history
  screen tries to use them.
- **`fallbackToDestructiveMigration(true)` is still active on `main`**
  (`app/src/main/java/com/forager/app/data/local/ForagerDatabase.kt:112`, confirmed by reading the
  current file). Any device that reaches a schema jump Room has no registered migration for gets
  its local database **silently wiped**, no log, no warning — a real conflict with CLAUDE.md's own
  "no silently swallowed failure" rule. This was found and flagged by the project's own most recent
  standing record (`docs/qc/pulses/reports/2026-08-26-repo-state-pulse-response.md`, §7), and a fix
  is already written — it's the "B1" item in the currently-open, unmerged PR #50 (see §2). The debt
  today is entirely about merge lag, not an unsolved problem.
- **Deliberate, documented duplication:** `hasLocationPermission()` (identical two-permission check)
  is copied verbatim across five files — `MainActivity.kt`, `TrackRecordingService.kt`,
  `location/AndroidLocationProvider.kt`, `location/AndroidLocationTracker.kt`,
  `ui/map/SightingsMap.kt`. Each site's own doc comment explains why: no shared boundary owns both
  `Context` and the Manifest declaration across these layers. Not accidental — flagged here only so
  a future refactor doesn't "fix" it without reading why it's there.
- **`server/pmtiles-worker/package.json` uses caret version ranges** (`^4.3.0`, `^5.20260815.1`,
  `^5.6.3`, `^4.59.1`) rather than exact pins — the one dependency declaration in this repo that
  doesn't follow CLAUDE.md's "pin dependency versions ... so a build is reproducible" rule, and
  inconsistent with the Gradle side's exact-pin discipline in `gradle/libs.versions.toml`. It's also
  the one part of this project that deploys independently of a CI-gated build. `npm outdated`
  (run this session) confirms real drift already exists within the declared ranges (see §6).
- **A recurring, never-root-caused CI signal:** the "Workers Builds: forager-pmtiles" Cloudflare
  check has now failed on at least three unrelated PRs — #25 (2026-08-20, where it was first flagged
  "not this PR's problem, flag if it recurs"), #42 (2026-08-27), and #50 (2026-08-27, confirmed
  directly via the GitHub API this session) — none of which touch `server/`. Nobody has investigated
  past the original "probably Cloudflare's Git integration, not a code defect" guess. Not itself a
  bug, but it's training reviewers to treat a red check on this repo as safe to ignore, which is how
  a real Worker-side regression eventually ships unnoticed.
- **This audit itself found a stale claim in the repo's own history:** `docs/audits/2026-08-24-session-handoff.md`
  lists "the confirmed orphaned-photo-file bug on entry delete" as still open. Reading current source
  shows it isn't a bug at all any more — `DeleteMushroomLogEntryUseCase.kt`'s own doc comment records
  that photo-on-delete cleanup was **deliberately reversed** at `MIGRATION_7_8` once photos became
  gallery-owned (an intentional design change, not an unfixed leak). `docs/audits/README.md`'s own
  rule — audits are point-in-time, not maintained — is doing exactly the job it's meant to here:
  the fact that this needed checking against current source, rather than trusted at face value, is
  itself worth remembering for whoever reads that handoff next.
- **Not independently re-verified this pass** (flagged rather than guessed at, per CLAUDE.md): the
  day marker palette's WCAG 3:1 contrast gap against a modelled pale-topo reference, which
  `MapPaletteTest` pins as a ratchet rather than a passing bar (per PR #42's own merge message); and
  `MushroomLogViewModel`'s failure-path test coverage, called "thin" in the 2026-08-24 handoff and
  not re-audited here — checking it properly would need a dedicated pass through that ViewModel's
  tests, out of scope for this audit's time budget.

---

## 4. Tests

**Real output, this session**, `./gradlew --stacktrace assembleDebug testDebugUnitTest` (after
installing the Android SDK with `scripts/setup-android-sdk.sh`, the same script CI uses — this
environment had no SDK preinstalled). `BUILD SUCCESSFUL in 4m 46s`. Gradle's plain console doesn't
print a per-suite tally, so the real JUnit XML Gradle wrote was parsed directly, the same way
`ci.yml`'s own summary step does (`app/build/test-results/testDebugUnitTest/TEST-*.xml`):

```
suites=104
{'tests': 732, 'failures': 0, 'errors': 0, 'skipped': 0}
```

**732 tests across 104 suites, 0 failures, 0 errors, 0 skipped** — run fresh, from a clean SDK
install, in this session, not estimated. This is the same figure the PR #42 merge commit message
claimed for this exact commit (§2/§4 above call out that CI's number was *relayed* there; this one
is independently reproduced.) `assembleDebug` also succeeded — `app/build/outputs/apk/debug/app-debug.apk`
exists — and `verifyNothingTestOnlyReachesTheApk` (`app/build.gradle.kts:257-309`) passed: `Verified:
no test-only class or manifest entry in app-debug.apk.`

102 test files under `app/src/test/java`, spanning domain use cases, Room DAOs/migrations (six
hand-written migrations, `MIGRATION_3_4` through `MIGRATION_8_9`, each exercised against a real
in-memory database built from production entity classes — not hand-copied schema fixtures),
ViewModels, and Robolectric-driven Compose layout/interaction tests
(`AvailabilityScreenLayoutTest` et al.) that measure real Compose-assigned bounds rather than
asserting on intent.

**What the layout tests do and don't prove**, stated plainly rather than left implicit: they compose
the real screen and read back real bounds, catching real regressions before hardware (documented,
with specifics, at README:924-935 and :1023-1040 — two separate `Surface`-intercepts-touches bugs
caught green-to-failing by exactly this coverage, not by visual review). What they cannot prove:
anything about MapLibre's native rendering, since `LocationComponent`/`MapLibreMap`/`Style` are
native-backed types this JVM environment cannot construct at all (README:753-756, :944-946) — the
map itself is stubbed in every layout test.

**CI's own claim, relayed but independently spot-checked, not reproduced verbatim:** the PR #42
merge commit message claims "732 tests across 104 suites" on the exact commit merged to `main`
(`3783275`), with one `JournalTabTest` flake on a first attempt and a clean second run. This session
independently confirmed via the GitHub API that CI run `33031985132` on that commit completed with
conclusion `success` — the run itself is real and current, not stale; the specific 732/104 figure is
the merge author's own count, not re-derived here.

**Server side:** `server/pmtiles-worker` has no test suite (no test script beyond `typecheck`/`build`
in `package.json`). `npx tsc --noEmit` was run this session: clean, zero errors.

---

## 5. Build & run

**Does not build out of the box in a fresh environment** — not a defect, but worth stating plainly:
there is no bundled Android SDK, and the first `./gradlew testDebugUnitTest` attempt this session
failed cleanly with `SDK location not found. Define a valid SDK location with an ANDROID_HOME
environment variable or by setting the sdk.dir path...`. Running `scripts/setup-android-sdk.sh` (the
exact script `README.md` and CI both point to) resolved it. This is documented and expected, not a
surprise — flagged here only because "does it build clean" has to account for this one manual step.

**Real warnings observed** during this session's build (not estimated):
- `WARNING: The option setting 'android.builtInKotlin=false' is deprecated` and `'android.newDsl=false'
  is deprecated` — both **intentional**, documented workarounds in `gradle.properties:5-13` for a real
  AGP 9 / KSP incompatibility, not accidental drift. AGP's own message is the only other place this
  requirement is documented, per that file's own comment.
- `WARNING: API 'applicationVariants'/'testVariants'/'unitTestVariants' is obsolete` — legacy AGP
  variant API warnings, a consequence of the `android.newDsl=false` workaround above, same root
  cause.
- `w: ⚠️ Deprecated 'org.jetbrains.kotlin.android' plugin usage` — same root cause again (needed for
  Room's KSP compiler per `gradle.properties`'s own comment).
- `WARNING: provisional build identity — this is a shallow clone` — **this session's own checkout is
  shallow**, so the locally-built APK reports `versionCode=1`/`UNVERSIONED-shallow-clone-*`. This is
  the project's own deliberate `resolveBuildIdentity()` safety mechanism working as designed
  (`app/build.gradle.kts:39-105`) — it's a property of this session's clone depth, not a real build
  defect; CI's own `fetch-depth: 0` avoids it (`.github/workflows/ci.yml:56`).
- Several test files trigger `This declaration needs opt-in ... @kotlinx.coroutines.ExperimentalCoroutinesApi`
  warnings (e.g. `app/src/test/java/com/forager/app/ui/track/TrackRecordingViewModelTest.kt`, ~30
  occurrences) and a handful use the now-deprecated `createComposeRule()` overload instead of the v2
  API (`PhotoGalleryScreenTest.kt:34`, `CentrePinLocationPickerTest.kt:41`, `MotionTokensTest.kt:39`).
  Non-blocking, real, and not present in the project's own `gradle.properties` doc-comment list of
  known/accepted warnings — genuine minor hygiene debt.

**CI is real and green on `main`'s current head.** Checked live via the GitHub API, not relayed:
workflow run `33076179340` on commit `d432660` (2026-08-27), conclusion `success`. The workflow
(`.github/workflows/ci.yml`) provisions the SDK from the same script this session used, builds the
debug APK, verifies the built APK's own `versionCode`/`versionName` round-trip via `aapt2 dump
badging` (catching exactly the shallow-clone problem this session hit locally), fails on any skipped
test, and checks the built APK for leaked test-only classes (`verifyNothingTestOnlyReachesTheApk`,
`app/build.gradle.kts:257-309`) — a real, artifact-level check, not a build-script assumption.

**One consistently-failing, non-blocking check**, confirmed on three separate, unrelated recent
PRs (see §3): `Workers Builds: forager-pmtiles`, Cloudflare's own GitHub App check, not part of
`ci.yml` and not required for merge.

**Server side** (`server/pmtiles-worker`): `npm install` + `npx tsc --noEmit` both succeeded this
session with zero errors. `npm audit --omit=dev` reports **0 vulnerabilities**.

---

## 6. Dependencies

Checked against live Maven Central / Google Maven metadata this session (not estimated). "Current"
means the pinned version equals the latest available stable release as of 2026-08-28.

| Dependency | Pinned | Latest stable | Status |
|---|---|---|---|
| Android Gradle Plugin | 9.3.1 | 9.3.2 | One patch behind (9.4.0 is still `rc02`) |
| Kotlin | 2.3.10 | 2.4.10 | Several minor releases behind (2.4.20 itself still RC) |
| KSP | 2.3.10 | 2.3.11 | One patch behind — deliberately kept equal to the Kotlin pin above (`libs.versions.toml`'s own comment) |
| Compose BOM | 2026.08.00 | 2026.08.00 | Current |
| Compose Material3 | 1.5.0-alpha26 | 1.5.0-alpha27 | One pre-release build behind; alpha channel is a deliberate pin (documented: needed APIs not yet stable) |
| Room | 2.8.4 | 2.8.4 | Current |
| Retrofit | 2.12.0 | **3.0.0** | A full major version behind — not evaluated for breaking changes here |
| OkHttp | 4.12.0 | **5.5.0** | A full major version behind — not evaluated for breaking changes here |
| kotlinx-serialization-json | 1.11.0 | 1.11.0 | Current |
| kotlinx-coroutines-android | 1.11.0 | 1.11.0 | Current |
| Robolectric | 4.16.1 | 4.16.1 (stable) | Current — only pre-releases (`4.17-beta-*`) exist beyond it |
| MapLibre Android SDK | 13.5.0 | 13.5.1 | One patch behind |
| NGA `mgrs` | 2.1.3 | 2.1.3 | Current |
| DataStore Preferences | 1.2.1 | 1.2.1 (stable) | Current — latest overall release is `1.3.0-alpha10`, deliberately skipped per the project's own documented non-alpha policy |

All of the above are exact-pinned in `gradle/libs.versions.toml`, consistent with CLAUDE.md's
reproducibility rule.

**`server/pmtiles-worker/package.json`** — range-pinned, not exact (see §3):

| Dependency | Declared range | Installed | `npm outdated` shows available |
|---|---|---|---|
| `pmtiles` | `^4.3.0` | — | not flagged outdated |
| `@cloudflare/workers-types` | `^5.20260815.1` | `5.20260819.1` | `5.20260827.1` |
| `typescript` | `^5.6.3` | `5.9.3` | `5.9.3` is "wanted"; latest major is `7.0.2`, outside the declared range |
| `wrangler` | `^4.59.1` | `4.124.0` | `4.127.0` |

`npm audit --omit=dev`: **0 vulnerabilities.**

**Unused dependencies:** none found. Every declared library has at least one real call site (checked
by grep for each artifact's characteristic import/API across `app/src/main`). The one "unused"
finding in this repo is application code, not a dependency — see §2/§3's `GetTracksUseCase` et al.

---

## Top 10 risks

Ordered by how much each would hurt if left unaddressed, not by how easy each is to fix.

1. **`fallbackToDestructiveMigration(true)` is still live on `main`** (`ForagerDatabase.kt:112`).
   Any user who upgrades across a schema jump Room has no migration path for gets their local
   database silently wiped — no warning, no log, direct conflict with this project's own
   loud-failure-over-silent-data-loss standard. Lowest-effort item on this list to close: the fix is
   already written and sitting in unmerged PR #50.
2. **Mushroom log Phase 2 (upload) doesn't exist.** Every field note a user records today lives only
   on that one device, with no visible indication in the UI that it isn't backed up anywhere. Blocked
   on an owner action (registering an iNaturalist OAuth app) nobody in a coding session can perform.
3. **Nothing in this map-heavy app has been rendered on real hardware from inside this development
   loop.** The entire MapLibre visual surface — markers, line styles, camera behavior, gesture
   handling — is reasoned-through and JVM-tested only, from this environment's own vantage point.
   The project owner does check specific pieces on a real device periodically, but that check trails
   whatever most recently merged, and there's no way from the repo alone to tell which parts of
   today's map code have and haven't had that pass yet.
4. **The off-track safety alert is silently half-built.** The toggle and the underlying
   `isOffTrack` detection both work correctly, but nothing ever posts the notification a drifting
   user is supposed to see — a user has every reason to believe this safety feature is active when
   it isn't wired end to end.
5. **`AvailabilityScreen.kt` (5,109 lines) is this repo's single highest merge-conflict-risk file.**
   This project's own CLAUDE.md names the exact failure mode most likely here — two branches each
   individually correct, merging clean, still producing a broken result — and this file is the most
   frequently, most concurrently edited surface in the codebase by a wide margin.
6. **The recurring "Workers Builds: forager-pmtiles" CI failure, unroot-caused across three PRs,**
   is quietly training reviewers to treat a red check on this repo as safe to ignore — the exact
   precondition under which a real Worker-side regression eventually ships unnoticed.
7. **Retrofit and OkHttp are each a full major version behind** (2.12.0→3.0.0, 4.12.0→5.5.0). This
   is the network stack every core feature (the ranked species search itself) depends on; neither
   gap is urgent alone, but both widen the eventual migration and shrink the old majors' security-
   patch window the longer they sit.
8. **`GetTracksUseCase`/`DeleteTrackUseCase`/`ComputeTrackStatisticsUseCase` are fully wired with
   zero UI consumers and mostly zero tests.** Low risk today, but exactly the shape of code that
   silently breaks against a future interface change with nothing to catch it, until the day a
   track-history screen finally tries to call it.
9. **`server/pmtiles-worker`'s dependencies are the one range-pinned (not exact-pinned) declaration
   in this repo**, and the one part of the project that deploys independently of a CI-gated build —
   a `wrangler deploy` picking up a newer transitive dependency mid-range wouldn't be caught by
   anything here.
10. **Historical docs in this repo go stale fast and are easy to over-trust.** This audit itself
    found a doc (`docs/audits/2026-08-24-session-handoff.md`) asserting a bug as still-open that was
    actually fixed by a deliberate design reversal weeks ago. `docs/audits/README.md`'s own rule —
    point-in-time, not maintained — says not to trust a status column without checking current
    source or live GitHub state, and this is a concrete example of why that rule exists.
