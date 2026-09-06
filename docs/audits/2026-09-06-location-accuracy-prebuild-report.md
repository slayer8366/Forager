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
