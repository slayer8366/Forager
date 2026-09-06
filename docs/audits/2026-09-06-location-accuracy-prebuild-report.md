# Pre-build report: location accuracy — a gate, honest precision, and an optional fused provider

**Dispatch:** "Location accuracy: a gate, honest precision, and an optional fused provider" (three related changes to one layer, owner-directed).
**Date:** 2026-09-06. **Status:** report before building — nothing built, no product code changed.
**Base:** `main` at `b79a3a6aae865b12fd1607d350d044ab40ffc5ef`, the merge commit of PR #67, which contains the navigation chrome work at `72e04cf` (confirmed: `git branch --contains 72e04cf` lists this branch).
**Branch:** `claude/new-session-102gri`, restarted from that commit (its previous PR was merged), zero commits ahead when this was written.

Every claim names a file and line on `b79a3a6`, or is marked inferred.

---

## 0. The layer as it stands

- `LocationTracker.fixes: Flow<LocationFix>` (`domain/LocationTracker.kt:11-33`) — raw, unfiltered; emits `PermissionDenied` once **and completes** when permission is not held at collection start. Three collectors exist, all on the one `AppContainer.locationTracker` (`AppContainer.kt:123`, an `AndroidLocationTracker`):
  1. `AvailabilityViewModel.collectLiveFixes` (`:140-148`) → `AvailabilityUiState.liveFix`, **no gate**; `onLocationPermissionGranted` (`:169-172`) restarts it only if the job is no longer active — it relies on the flow *completing* on denial.
  2. `TrackRecordingViewModel.beginLocationTracking` (`:272-296`) → `returnToStart` on every fix, and the origin waypoint on the first fix that clears the mode's gate.
  3. `TrackRecordingService.startRecording` (`:110-133`) → `LocationSampler.shouldAccept` → persisted points.
- `AndroidLocationTracker` (`location/AndroidLocationTracker.kt`) registers GPS and network at a 1 s floor; `accuracyMeters = if (hasAccuracy()) accuracy else null` (`:72`).
- The recording gate is `LocationSampler.shouldAccept` (`domain/LocationSampler.kt:22-36`): `if (accuracy != null && accuracy > mode.maxAcceptableAccuracyMeters) return false` — **a `null` accuracy passes**. BALANCED is 50 m, HIGH_ACCURACY 30 m, BATTERY_SAVER 100 m.
- The HUD's freshness policy (`domain/NavigationReadout.kt:53-62`): STALE at 30 s, LOST at 5 min, both off `liveFix.timestampEpochMillis`.
- Distance display: `formatDistanceMeters` (`domain/model/DistanceUnit.kt:70-77`) rounds to whole metres or whole feet below the km/mile switch — the source of "0 ft".

---

## ITEM 1 — a gate on the live fix

### Proposal

A pure domain function, applied once, in `AvailabilityViewModel.collectLiveFixes`, before `_uiState.update`:

```
fun acceptLiveFix(candidate: LocationFix.Update, maxAccuracyMeters: Float): Boolean =
    candidate.accuracyMeters == null || candidate.accuracyMeters <= maxAccuracyMeters
```

Rejected fixes are dropped; `liveFix` keeps the previous accepted one. No smoothing, no averaging, no "best of the last N" — a gate, as asked.

### Threshold: **50 m, the same number as BALANCED — but as its own constant, `LIVE_FIX_MAX_ACCURACY_METERS`, not a reference to `TrackRecordingMode.BALANCED`.**

Why the same number: the two gates answer different questions, but the *worst* fix worth showing is not obviously worse than the worst fix worth keeping. Below 50 m, everything the HUD derives is already degraded past use: the "Approaching" band is twice accuracy, so a 60 m fix puts it at 120 m; the needle at 100 m from the target with 60 m accuracy swings through most of a quadrant; and the coordinates row would show an MGRS 1 m square for a position known to 60 m. A tighter number (30 m, HIGH_ACCURACY's) would blank the HUD under the exact canopy conditions the owner is testing in — see the aging interaction below — so I would not go tighter without field data. A looser one (100 m) shows positions the track would refuse, which is the inconsistency the dispatch names.

Why a separate constant: the recording mode is the user's battery/density choice and can change per track; the display gate should not move when the user picks BATTERY_SAVER (100 m). Two constants with the same value, each with its own reason recorded, rather than one shared one that silently ties display to persistence.

### Held-fix aging — the interaction, stated

With the gate, a phone under canopy delivering only 60–80 m fixes for a minute shows: the last good fix, de-emphasised with "Last fix 45 s ago" from 30 s, then "No fix for 5 min" with the distance withheld from 5 min — **while the radio is alive and fixes are arriving.** That is correct on the dispatch's own terms: a 60 m fix is not information about where the user is to the precision the HUD implies, and "last usable fix 45 s ago" is the truth. But it means the stale display now has a second cause besides the radio going quiet, and the wording "Last fix" is slightly off for it — the fix arrived, it was refused.

**Decisions this raises — stop-and-flag:**

- **(1a)** Accept the aging consequence as-is (my recommendation: it is the honest reading, and the alternative below is exactly the kind of correction logic CLAUDE.md says not to build without data), **or** let a rejected fix through once the held one is itself STALE ("the best fix in the last 30 s"), which keeps the HUD moving under canopy at the cost of showing 60 m positions with no visible flag that they are worse.
- **(1b)** Whether the stale wording should distinguish "no fix" from "no usable fix". I would leave the wording alone in this dispatch and note it: the difference is real but a third status string is scope this dispatch did not open.

### `accuracyMeters == null`: **passes.**

Consistent with `LocationSampler` (null passes there too) and with `isApproaching` (null means "no basis for the word", not "bad"). Rejecting null would be treating "not reported" as "worse than 50 m", which is a fabricated judgement.

**What the device actually does — not established here (no device).** Inferred from the platform API: on API 26+ (this app's `minSdk`), fixes from `GPS_PROVIDER` and `NETWORK_PROVIDER` carry `hasAccuracy() == true` in practice; the false case is reachable through mock locations, some OEM passive-provider fixes, and `Location` objects constructed by hand. Under the pass rule a null-accuracy fix behaves exactly as today, so the choice is safe whichever the device does. The owner can confirm on hardware by watching whether the HUD's "Approaching" ever appears (it needs a non-null accuracy); it has, in the owner's own screenshots, so real fixes on that device do report accuracy.

### Tests (planned)

Pure: 49.9 m accepted, 50.1 m rejected, null accepted — literals. ViewModel, through `locationTracker.fixes`: a 40 m fix, then a 60 m fix → `liveFix` is still the 40 m one, and `ageMillis` against a later clock reads the *first* fix's age (the aging claim, asserted on the timestamp, not inferred). Screen: with a fake tracker emitting a good fix then a bad one, the HUD's distance is the good fix's. Each reverted (gate removed → the 60 m fix lands) and re-run.

---

## ITEM 2 — precision the fix does not have

### What "0 ft" is

`formatDistanceMeters(0.3, MILES)` → `"0 ft"`; the accuracy was several metres. The number is not wrong; its resolution is a lie.

### The two directions, on a 360dp screen

The distance slot is `titleMedium` bold in the HUD's first row, between the two compass columns and the ✕, roughly 140dp wide (inferred: Robolectric text widths are unusable, per the navigation-chrome report).

- **(B) Accuracy alongside** — `12 m ± 8 m`, `0.7 mi ± 30 ft`. Two numbers to parse, two units when miles and feet mix, and the ± reads as a spec sheet, not a glance. At arm's length in the woods the reader wants one quantity. I would not build this.
- **(A) Round to the accuracy** — one number, coarsened. Concretely:
  - **distance ≤ accuracy → `within 16 ft`** (accuracy formatted in the display unit). This is the "0 ft" case: the honest statement is "you are inside the error circle", and it dovetails with "Approaching" (which fires at twice accuracy) — the status says approaching, the number says within.
  - **otherwise → the distance rounded to a step no finer than the accuracy, prefixed `≈`**: steps 1, 5, 10, 50, 100 m (or 5, 10, 50, 100, 500 ft), the smallest step ≥ accuracy. 12 m with 8 m accuracy → `≈ 10 m`; 340 m with 15 m → `≈ 350 m`; at km/mile scale the existing one-decimal formatting is already coarser than any plausible accuracy, so it is left alone.
  - **no accuracy reported → today's formatting, unchanged**, no `≈`: there is no basis for coarsening, and inventing a resolution would be the same fabrication in the other direction.

**Recommendation: (A).** One number, honest, no new unit on screen, and the `within` form turns the worst case ("0 ft") into the most useful line the HUD can show near the target. **The `≈` glyph and the `within` wording are the owner's to change.**

**Decision this raises — stop-and-flag:** (A) is a new function (`formatDistanceWithAccuracy(distanceMeters, accuracyMeters, unit)`), not a change to `formatDistanceMeters`, so every other caller keeps its exact output. Confirm that the HUD's *target column* (empty while approaching, `Turn N°` otherwise) and the *distance slot* are the only places it applies.

### Anything else that displays a fix-derived distance?

One: the control pill's return row `contentDescription`, `returnToStartStripText` (`AvailabilityScreen.kt:4297`) — "Return: 180° S · 1.2 km · -45 m", built from `ReturnToStartInfo`, which `ComputeReturnToStartUseCase` derives from the recording's own fix. Same rounding, same false precision, TalkBack-only since the arm went. **Not trivially shared:** `ReturnToStartInfo` carries no accuracy, so the fix would need a field threaded from `TrackRecordingViewModel.returnToStart` through the use case. Reported; not fixed here. Nothing else: `accuracyLabel` (`AvailabilityPureFunctions.kt:256`) formats an iNaturalist observation's positional accuracy, not a fix; track statistics sum recorded points, not a live fix.

### Tests (planned)

Literals at the visible cases: (0.3 m, 5 m, MILES) → `within 16 ft`; (12 m, 8 m, KM) → `≈ 10 m`; (12 m, null, KM) → `12 m`; (340 m, 15 m, KM) → `≈ 350 m`; (1 200 m, 8 m, KM) → `1.2 km`. Readout: the HUD's distance text at the 10 m / 12.5 m fixture becomes `within 25 m`, so the existing `"10 m"` node-count test changes (reported). Each reverted and re-run.

---

## ITEM 3 — an optional fused provider

### 1. The dependency, measured from the artifacts' own POMs (fetched through the proxy)

| Artifact | Version | AAR size | Pulls in |
|---|---|---|---|
| `com.google.android.gms:play-services-location` | 21.4.0 (latest on `dl.google.com`, checked today) | 272 KB | base, basement, tasks, kotlin-stdlib 1.9.0, coroutines 1.7.3 |
| `play-services-base` | 18.9.0 | 615 KB | basement, tasks, androidx.fragment 1.1.0, core 1.9.0, collection 1.0.0 |
| `play-services-basement` | 18.9.0 | 439 KB | androidx.fragment, core, collection |
| `play-services-tasks` | 18.4.0 | 100 KB | basement |

About 1.4 MB of AARs. **The app does not shrink** (`isMinifyEnabled = false`, `app/build.gradle.kts:130`), so the whole of that dex ships in every APK, on every device, whether or not the setting is ever turned on — the owner should expect roughly 1–1.5 MB more APK (inferred; not built). The AndroidX transitives already exist in the graph (`androidx.fragment:fragment:1.8.9`, core, collection — `gradle :app:dependencies`), so they add nothing new. Kotlin/coroutines resolve to the app's newer pins.

**Beyond location:** `basement` and `base` are the GMS client core — `GoogleApiAvailability`, the `GoogleApi` connection plumbing, signature verification of the Play Services package, and the `Task` API. None of it makes a network call on its own; all of it exists to bind to the Play Services process. The license is Google's Android SDK terms, not an open-source license. **That is the cost that matters here, not the megabyte:** the first non-free dependency in an app whose character is no account, offline, EXIF-stripped.

### 2. Availability detection

- With the dependency: `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS` — the correct check (present, enabled, new enough). **It lives in `play-services-base`, so it requires the dependency.**
- Without it: `PackageManager.getPackageInfo("com.google.android.gms", 0)` plus `applicationInfo.enabled` — no dependency, answers "installed and enabled", not "new enough for this client". Good enough to grey out a setting; not a substitute for the real check before binding.

Proposal: the greyed-out state uses the PackageManager check (so a build without the dependency can still show the setting), and the provider selection uses the real check where the dependency exists.

### 3. Switching mid-session — the risky part, and a trap

The three collectors all hold `container.locationTracker` and collect `fixes` once, for the life of their job. Swapping the container's field does nothing to them. The only shape that does not drop fixes is a **`SwitchingLocationTracker : LocationTracker`** whose `fixes` is `preference.flatMapLatest { fused -> if (fused && available) fusedTracker.fixes else androidTracker.fixes }`: collectors never see a completion, the old listener is removed and the new one registered inside one `flatMapLatest` switch, and the only loss is a fix in flight during that sub-second window — reported as such, not silent.

**The trap:** `flatMapLatest` keeps the outer flow open when an inner one completes. Today `PermissionDenied` is followed by **completion**, and `AvailabilityViewModel.onLocationPermissionGranted` (`:170`) restarts collection *only because the job is no longer active*. A naive switching tracker would leave that job active after a denial, and the first-launch bug the tracker's own doc comment warns about (`LocationTracker.kt:20-31`) would return: no fixes until restart. The switching tracker must re-emit `PermissionDenied` **and complete the outer flow** when an inner one does. This is the one place item 3 can break items 1 and 2's consumers, and it needs its own test (denial through the switching tracker completes the collector).

**With a track recording:** the service's sampler keeps its `lastAccepted` across the swap, so the next fused fix is gated against the last `LocationManager` point as normal — no duplicate, no gap beyond the in-flight window. **With the HUD open:** `liveFix` keeps the last accepted fix through the swap; the age keeps ticking; nothing on screen changes until the first fix from the new provider passes the item-1 gate.

### 4. Where the preference lives

**Not `MapPreferencesRepository`** — it is not a map preference, and CLAUDE.md's rule is one namespaced DataStore per flat setting family. **A new `LocationPreferenceRepository`** (`domain/`) with `DataStoreLocationPreferenceRepository` (`data/repository/`), its own file `location_preferences`, key `location.fused_provider_enabled`, `Result`-returning `get`/`set`, per-instance `PreferenceDataStoreFactory.create` — the exact shape of `DataStoreDistanceUnitPreferenceRepository`. **One departure to flag:** the switching tracker needs to *observe* the value, so this repository also needs a `Flow<Boolean>`-returning `observeFusedProviderEnabled()`. That is the first Flow-returning method in this repository family; the alternative is the tracker polling `get()`, which is worse.

Settings placement: a `FusedLocationSection` beneath `NightModeMapsSection` (`AvailabilityScreen.kt:2494-2506`), same `Checkbox` + `bodyLarge` row, the owner's copy verbatim as a `bodyMedium` line under the label, and when Play Services is absent: the checkbox disabled, `onCheckedChange = null`, text at `alpha = 0.38`, and the note. **Proposed note wording:** *"Not available on this phone — it needs Google Play Services, which isn't installed."*

### 5. F-Droid / Play-Services-free builds — **this is the stop condition**

The repo has **no product flavours** (`app/build.gradle.kts` declares `buildTypes` only) and no F-Droid metadata. A single build with `play-services-location` on the classpath means:

- Every APK carries the GMS client code and its proprietary license, including on de-Googled devices where the setting is greyed out — the greyed-out setting is honest, the APK is not "Play-Services-free".
- **F-Droid will not build it as-is** (non-free dependency → the build is rejected, not merely flagged). Inclusion would need a `foss` product flavour that excludes the artifact, plus a flavour-specific factory so the switching tracker compiles without the fused class — **a build-variant requirement**, the exact thing the dispatch names as a reason to stop.

**So: item 3 costs a build variant or the app's Play-Services-free character, and I am stopping on it as instructed.** Two ways forward for the owner to choose between, neither built:

- **(3a)** Two flavours, `gms` and `foss`, the dependency only in `gms`, the setting greyed out in `foss` with a note that says the build lacks it rather than the phone. Real work in CI (two APKs published, two test runs or one with the flavour that has both paths), and a permanent second build to keep green.
- **(3b)** Do not add the dependency. Items 1 and 2 land alone; the fused provider is dropped or deferred until there is field evidence they were not enough.

### Verification the dispatch asks for, and how it would be asserted

"With the preference off, no Play Services code path is entered — assert it directly": the switching tracker takes the fused tracker as a **factory** (`() -> LocationTracker`), and the test's factory throws; with the preference off, collecting `fixes` must not throw and must deliver the `LocationManager` fake's fixes. With Play Services reported unavailable, the same, preference on or off. Those are direct assertions, not absence of effect.

---

## Kalman filter — the dispatch's question

Items 1 and 2 do not make a filter clearly unnecessary or clearly necessary. They remove the worst inputs (gate) and stop the display over-claiming (precision); they do nothing about the drift *within* a run of 8–15 m fixes, which is what a filter would smooth. Whether that residual drift is a problem at arm's length is a field question: after items 1 and 2 land, one recorded track under canopy with the raw fixes logged would answer it. Building a filter before that is the speculative-correction logic CLAUDE.md rules out.

**Do items 1 and 2 alone resolve what the owner is seeing?** They resolve the two things the owner can see — the readout yanking on a bad fix, and the false precision — and they cannot improve the fix itself. If "drift" means the puck and readout wandering between successive 10 m fixes, only a better source (fused) or a filter changes that, and fused is the one with a real cost attached. My honest read: land 1 and 2, test in the field, and decide 3 on what is left, because most users will never turn 3 on.

---

## Standing rules

Baseline stated: 1122 tests, 24 skipped; verified on the identical tree at the end of the previous dispatch (`72e04cf`, same content as `b79a3a6`), skip set byte-identical to the allowlist. The comparison runs again after any build. `JournalTabTest`'s "From Album" flake: known, will be reported if it fires, not touched.

## Environment

No `/dev/kvm`; device verification will be blocked. The Android SDK, Gradle and the Robolectric jars are already installed in this container from the previous dispatch.

## Required disclosure (pre-build)

**Confirmed:** every file/line citation; `LocationSampler` passes null; the three collectors and their reliance on completion; the artifact versions, sizes and POM dependencies (fetched today); `isMinifyEnabled = false`; no flavours; `androidx.fragment` already in the graph. **Inferred:** APK growth (~1–1.5 MB unshrunk); that GPS/network fixes report accuracy on API 26+ devices; the distance slot's width; F-Droid's treatment of GMS dependencies (their published inclusion policy, not tested against this app). **Could not determine:** what the owner's device does for `hasAccuracy()`; whether the residual drift is bad enough to need a filter. **Premises in the dispatch that were wrong or incomplete:** none found wrong; one incomplete — "detection itself requires the dependency" has a dependency-free answer good enough for the greyed-out setting but not for the real availability check. **Decided:** nothing built; the proposals above each name the decision they wait on — (1a) aging consequence vs. best-in-30-s, (1b) stale wording, item 2's `≈`/`within` wording and scope, item 3's (3a)/(3b).

---
---

# Completion report — items 1 and 2 built; item 3 deferred, not rejected

**Owner's answers** (`ANSWERS — Location accuracy`): item 1 as proposed, aging accepted, the refused alternative recorded at the gate; item 2 as proposed, a new formatter, the TalkBack sentence queued; **item 3 deferred to pre-release, after the beta — superseding the earlier "no dependency" answer.** Nothing of item 3 is built here; §ITEM 3 above stays as the bulk of its eventual report-before-building (the dependency measurement, availability detection with and without the dependency, the new location-preference repository, and **the `flatMapLatest` switching trap that would resurrect the first-launch bug**). Its first open question is R8: whether shrinking strips the unused paths with the preference off, which may make the flavour pair unnecessary. Kalman: not now; **behind the gate, never in front of it** — recorded at the gate's doc comment too.

**Commits on `claude/new-session-102gri`:** `e6cd929` (pre-build report), `01e7df5` (product), `cc7b97b` (tests), plus this one. Not merged; no PR opened (none was asked for).

## What was built

**Item 1 — `domain/LiveFixGate.kt`.** `LIVE_FIX_MAX_ACCURACY_METERS = 50f` (its own constant; the doc says why it is not a reference to BALANCED) and `acceptLiveFix(candidate, maxAccuracyMeters = LIVE_FIX_MAX_ACCURACY_METERS)`: null passes, `<= 50` passes. Applied once, in `AvailabilityViewModel.collectLiveFixes`, as `if (fix is LocationFix.Update && acceptLiveFix(fix))`. Rejected fixes are dropped; the previous fix is held and ages. The file's doc comment records the threshold's reasoning, the null rule, the accepted aging, **the refused alternative and why** (an honest silence traded for a confident lie — the pattern this app has been removing everywhere else), and that a future filter goes behind the gate. Recording's own gate is untouched.

**Item 2 — `formatDistanceWithAccuracy` in `domain/model/DistanceUnit.kt`.** Null accuracy → `formatDistanceMeters`, unchanged; `distance <= accuracy` → `"within <accuracy in the display unit>"`; otherwise, below the km / quarter-mile switch, the distance rounded to the first step ≥ accuracy from `1/5/10/50/100/500 m` or `1/5/10/50/100/500/1000 ft`, prefixed `"≈ "` unless the step is the unit's natural 1 m / 1 ft; at or above the switch, unchanged. The HUD's distance slot is its only reader (`NavigationHud.kt`, `distanceText`). `formatDistanceMeters` itself and all its other callers are byte-for-byte as they were.

## Tests

**Suite:** **1134 tests, 0 failures, 0 errors, 24 skipped** on the final tree (`./gradlew :app:testDebugUnitTest --continue`, summed from the JUnit XML); the skipped set is byte-identical to the CI `SKIPPED_TESTS_ALLOWLIST` (24 entries; no unallowed skip, no stale entry), checked by parsing the workflow against the report. `JournalTabTest`'s "From Album" flake did not fire in this run. Baseline was 1122 / 24; the new tests account for the difference and the skip count did not move.

**Reverted-and-rerun**, each a one-line `sed`, the affected classes run, the file restored with `git checkout`:

| Reverted | Red, and how |
|---|---|
| Gate not applied in the collector | screen: `a fix worse than 50 m does not reach the HUD` (`expected 1.1 km but was ≈ 600 m`); ViewModel: `…the previous fix is held, and the held fix ages` (`expected 40.0 but was 60.0`). 2 failures. |
| Gate boundary made exclusive (`<` for `<=`) | `LiveFixGateTest` (50.0 rejected); ViewModel `a fix at exactly 50 m passes…` (`expected 50.0 but was null`). 2 failures. |
| `within` branch disabled | formatter ×2 (`within 16 ft` → `≈ 0 ft`; `within 8 m` → `≈ 10 m`); screen node-count (`within 13 m` → `≈ 0 m`); readout approaching (`within 41 ft` → `≈ 50 ft`). 4 failures. |
| Rounding removed (outside branch returns plain formatting) | formatter ×2 (`≈ 10 m` → `12 m`, `8 m`); readout boundary (`≈ 50 ft` → `51 ft`). 3 failures. |
| Null accuracy treated as 5 m | formatter `no accuracy reported - exactly today's formatting` (`12 m` → `≈ 10 m`); readout `no reported accuracy…` (`33 ft` → `≈ 50 ft`). 2 failures. |

**New and changed tests.** `LiveFixGateTest` (new, 3). `AvailabilityViewModelLiveFixTest` +2: the 40 m-then-60 m case asserts the held fix's accuracy, latitude and timestamp, **and its age at a later clock reading** (60 000 ms, from the first fix's timestamp — the consequence the owner accepted, asserted directly); exactly-50 and null both land. `FormatDistanceMetersTest` +6, every string worked by hand from the pinned conversions and the step tables, never from the formatter (the "within" boundary at 8.0 m vs 8.01 m with 8 m accuracy is the case the answers named). `NavigationHudReadoutTest`: the approach cases now read `within 41 ft`; the 15.57 m / 16.46 m needle boundary reads `≈ 50 ft` on **both** sides — the rounding cannot tell them apart, which is its job, and the needle still does; the no-accuracy case pins `33 ft` unchanged. `AvailabilityScreenMapIconStackTest`: the approach node-count test's literal is `within 13 m` (12.5 rounded half up), asserted once and `10 m` asserted absent; a new test drives a 12.5 m fix then a 60 m fix through the real tracker flow and the HUD keeps `1.1 km`.

**Guard that the edit applied:** every revert above went red; the suite on the unedited tree was not the evidence.

## Device verification

Blocked: no `/dev/kvm`. For the owner, in order of what cannot be judged here:

1. **Whether the held-fix aging reads as honest or as broken under real canopy.** With only 60 m+ fixes arriving, the HUD will de-emphasise at 30 s ("Last fix 45 s ago") and withhold the distance at 5 min ("No fix for 5 min") while the map's own puck may still be moving on those rejected fixes (the map reads MapLibre's location component, not `liveFix`). That divergence is expected and is the thing to look at.
2. At the origin: the slot reads `within N ft` (or `m`), not `0 ft`.
3. Walking away from the origin: `≈ 50 ft`, `≈ 100 ft`… stepping coarsely, then plain tenths of a mile past a quarter mile.
4. Whether real fixes on the owner's device ever report a null accuracy (they would show today's fine-grained formatting with no `≈`).

## Queued, not lost

- **The control pill's TalkBack sentence** (`returnToStartStripText`): same false precision, no accuracy on `ReturnToStartInfo`; needs the field threaded from `TrackRecordingViewModel.returnToStart` through `ComputeReturnToStartUseCase`.
- **The stale wording** ("Last fix…") now has a second cause — a fix arrived and was refused. A third status string was not opened here.
- **Item 3**, per the owner's answers: R8 measurement first, then the flavour question; the findings above stand.
- **Kalman**: one canopy track with raw fixes logged, after this lands; behind the gate.

## Does landing 1 and 2 change the read on whether fused is needed?

Slightly, toward "wait and see": with the gate in, the *visible* drift the owner reported (the readout yanking on a bad fix) is gone by construction, and with the formatter in, the number will not pretend to know more than it does. What remains is real positional drift between fixes that all pass 50 m — the case fused would improve. My early signal from the code, not from the field: the gate's ceiling is generous enough that under moderate canopy most fixes will still pass, so the HUD will keep moving; what the beta should watch is whether testers see the *stale* states fire often under trees. If they do, fused (or a tighter ceiling) is solving a real problem; if they don't, the raw path is holding and the 1.4 MB is not worth it before release.

## Required disclosure

**Confirmed:** everything in the revert table and the suite line, by running; every literal in the formatter tests, by hand arithmetic recorded beside it. **Inferred:** that the map's puck will diverge from the gated `liveFix` under canopy (from MapLibre's location component being fed by the platform directly — read, not run). **Could not determine:** device behaviour, above; what the owner's device reports for `hasAccuracy()`. **Premises in the answers that were wrong:** none found. **Decided without cover:** (1) the step tables' exact values (1/5/10/50/100/500 m and their foot counterparts) and that the last entry exceeds the gate's ceiling so `first { }` cannot throw; (2) `≈` rather than `~`; (3) the `within` string formats the accuracy through `formatDistanceMeters`, so 12.5 m reads `within 13 m` (half-up) rather than `within 12 m`; (4) the screen gate test's rejected fix sits 500 m closer to the origin than the held one, so a missing gate shows as `≈ 600 m` — a visible, not subtle, failure.
