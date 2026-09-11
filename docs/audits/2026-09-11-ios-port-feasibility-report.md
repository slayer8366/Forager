# iOS port feasibility

**Date:** 2026-09-11
**Type:** pre-build report. **Not a dispatch. Nothing here is authorised or scheduled.**
**Base:** `claude/ios-port-feasibility-mvsjcr`, 2 commits ahead of `main`, 0 behind.
**Question asked:** "How hard would it be to port the app to iOS?"

Written under CLAUDE.md's pre-build-report rule: every claimed figure is traced to the command
that produced it, every claimed path to its caller, and the four disclosure sections at the end
say what is confirmed, what is inferred, what could not be determined, and which premises a
reader might arrive with are wrong.

---

## 1. The short answer

The architecture is unusually well positioned for this and the product is unusually badly
positioned for it. Those are different axes and they do not cancel.

The code that encodes what Forager knows how to do is 7,452 lines that import nothing from
Android. It sits behind 26 interfaces this project owns. That layer moves essentially intact.

The code that makes Forager work in a forest is the part with no iOS equivalent waiting for it:
a foreground service recording GPS for hours, a MapLibre `MapView` hosted inside Compose, twelve
Room migrations written against `SupportSQLiteDatabase`, and a test suite whose majority of lines
run under Robolectric. None of that is a translation exercise. Each is a rewrite against a
different platform contract.

## 2. What is actually in the tree

All counts from this tree at the base commit above.

| Layer | LOC | Files | Command |
|---|---|---|---|
| `ui/` (Compose) | 21,105 | 53 | `find ui -name '*.kt' \| xargs cat \| wc -l` |
| `domain/` | 7,452 | 144 | same, per package |
| `data/` | 4,968 | 49 | same |
| Platform layer (8 packages) | 1,882 | 18 | `location sensor service photo alert crash export map` |
| Root (`AppContainer`, `MainActivity`, `ForagerApplication`) | 760 | 3 | `wc -l app/.../app/*.kt` |
| **`src/main` total** | **36,167** | **267** | `find app/src/main -name '*.kt' \| xargs cat \| wc -l` |
| `src/test` total | 34,506 | 166 | `find app/src/test -name '*.kt' \| xargs cat \| wc -l` |

One Gradle module, `:app` (`settings.gradle.kts`). There is no separate domain module to lift;
the separation is by package and by discipline, not by build boundary. Creating the module split
is itself a prerequisite step and it is the cheap one.

`minSdk = 26`, `compileSdk`/`targetSdk = 37`, `applicationId = com.zynergylabs.forager.app`
(`app/build.gradle.kts:225-245`).

## 3. The part that moves cleanly

**`domain/` has zero Android imports.** Verified, not assumed:
`grep -rh "^import android" domain/` returns nothing across all 144 files. CLAUDE.md's
architecture rule has been kept.

**26 domain-owned interfaces** are the seams a port needs
(`grep -rn "^interface \|^fun interface " domain/`): `LocationTracker`, `LocationProvider`,
`CompassProvider`, `DeclinationProvider`, `PhotoStore`, `PhotoSource`, `AlertDelivery`,
`AlertAudibility`, `ErrorLog`, `OfflineMapRepository`, `MapPreferencesRepository`, and the
fifteen repositories and providers behind them. Every Android class in `location/`, `sensor/`,
`photo/`, `alert/`, `crash/` and `map/` is an implementation of one of these. That is the whole
reason this port is tractable at all.

**Dependency injection is manual.** `AppContainer(context: Context)` constructs everything by
hand (`AppContainer.kt:113`). No Hilt, no Dagger, no annotation processor to find an iOS
equivalent for. The `Context` parameter is the only Android type in the file's constructor, and
the seven call sites that use it (lines 126, 127, 128, 136, 234, 235) all pass
`context.applicationContext` into a platform implementation.

**DTOs are `kotlinx.serialization`.** The wire format layer needs no change.

**Android resource usage is nearly zero.** Across all of `src/main`: 11 `R.string` references,
4 `R.font`, 3 `R.drawable`, and `strings.xml` holds 10 strings. Almost all user-facing text is
Kotlin string literals. That removes a resource-system port from the work. It also means there is
no localisation layer to carry over, which is a separate problem and not this one.

### 3a. What the domain layer still has to give up

27 of 144 domain files import a JVM API with no Kotlin/Native implementation
(`grep -rl "^import java\.\|^import javax\.\|^import org.w3c\|^import mil.nga" domain/`):

- **`java.time`**, 23 imports, dominated by `LocalDate` (16). Replaced by `kotlinx-datetime`.
  Mechanical, wide, and every touched file needs its tests re-read rather than trusted.
- **`java.util.UUID`**, 7 imports. Needs a multiplatform UUID or `kotlin.uuid`.
- **`java.util.PriorityQueue`**, 1. Needs a replacement or a hand-written heap.
- **`javax.xml.parsers` + `org.w3c.dom`**, both in `domain/GpxCodec.kt`. GPX parsing on DOM. No
  Kotlin/Native DOM parser ships in the standard library, so this is a rewrite against a
  multiplatform XML library or a hand-rolled parser. GPX is the export format the project already
  ships, so correctness here is load-bearing.
- **`mil.nga:mgrs` 2.1.3**, in `domain/MgrsConverter.kt`. A pure-Java library, chosen deliberately
  over hand-rolling UTM/grid-zone math (recorded in that file's doc comment and in
  `gradle/libs.versions.toml`). Pure Java is exactly what does not cross to Kotlin/Native. There
  is no published KMP MGRS library that this report confirmed. The options are: port the needed
  subset, call an iOS-side equivalent through `expect`/`actual`, or hand-roll the math the file's
  own comment gives reasons not to hand-roll.
- **`java.text`**, in `domain/NormalizeSearchName.kt`.

Call this 27 files touched for platform-API reasons alone, before any behaviour changes.

## 4. The part that is a rewrite

### 4a. Room, and twelve migrations

`ForagerDatabase` is at `version = 15` with twelve real migrations, `MIGRATION_3_4` through
`MIGRATION_14_15` (`ForagerDatabase.kt:159,189`). `app/schemas/` holds `4.json` through `15.json`.
`Migrations.kt` is 934 lines and every migration signature is
`override fun migrate(db: SupportSQLiteDatabase)`.

Room supports iOS. Room 3.0 (Android Developers Blog, March 2026) is a full KMP library across
Android, iOS, JVM, native Mac and Linux, backed by the `androidx.sqlite` driver APIs. This repo is
on Room 2.8.4, which is also KMP-capable.

The catch is specific and it is in the migration API. Callbacks that took `SupportSQLiteDatabase`
are replaced by equivalents taking `SQLiteConnection`, including `Migration.onMigrate()`. So all
twelve migrations, 934 lines, move to a different API. A compatibility mode exists while a
`SupportSQLiteOpenHelper.Factory` is configured, which permits incremental migration on Android,
but the iOS target does not get that escape.

`ForagerDatabase.kt:184` and `FungiIndexDatabase.kt:45` both use `Room.databaseBuilder(context, ...)`,
the Android overload. The KMP builder is a different call.

`FungiIndexDatabase` additionally opens a 4.4 MB prebuilt SQLite asset via `.createFromAsset()`
(`app/src/main/assets/databases/fungi_index.db`). Whether the KMP builder offers a working
`createFromAsset` path on iOS **was not determined by this report** and should be checked before
anything is scheduled, because the alternative is bundling the file in the app bundle and copying
it to the documents directory by hand, which is more work but not hard work.

`android:allowBackup="false"` and the project's own export/import plan are Android-specific policy.
The equivalent iOS decision is whether the database and photo directory are excluded from iCloud
backup. That is a new decision, not a ported one, and it has the same reasoning available to it
(a Room database copied mid-write restores torn).

### 4b. Network

6 files, 512 lines (`data/remote/`), on Retrofit 2.12.0 and OkHttp 4.12.0. Both are JVM-only.
Ktor is the replacement. The DTOs underneath are already `kotlinx.serialization`, so the rewrite
is the three API interfaces and three clients, not the payload model. This is the smallest and
most predictable piece of the whole port.

### 4c. The map

This is the largest single risk and the most interesting finding.

MapLibre-coupled code, by file:

| File | LOC |
|---|---|
| `ui/map/SightingsMap.kt` | 1,163 |
| `ui/log/CartographyEntryReportScreen.kt` | 590 |
| `map/MapLibreOfflineMapRepository.kt` | 367 |
| `ui/map/MapSlot.kt` | 331 |
| `ui/map/CentrePinLocationPicker.kt` | 188 |
| `map/MapLibreStorage.kt` | 98 |
| **Total** | **2,737** |

The API surface used is not shallow: `MapView`, `MapLibreMap`, `Style`, `CameraUpdateFactory`,
`OfflineManager`, `OfflineRegion`, `OfflineTilePyramidRegionDefinition`, `OfflineRegionStatus`,
`OfflineRegionError`, `FileSource`, the location component, and six style-layer types
(`SymbolLayer`, `CircleLayer`, `LineLayer`, `FillLayer`, `GeoJsonSource`, `Expression`).
The view is hosted through Compose `AndroidView` (`SightingsMap.kt:38,524`), which is an
Android-only interop API.

The naive reading is that this is a full Swift rewrite against MapLibre Native iOS. That reading
is probably wrong, and the correction matters to the estimate.

**`maplibre-compose` exists** (`maplibre.org/maplibre-compose`, `github.com/maplibre/maplibre-compose`).
It is a Compose Multiplatform wrapper over the MapLibre SDKs, rendering with MapLibre Native on
Android, iOS and Desktop, and it supports offline map downloads on every platform except the
browser. That covers the two hardest requirements here at once: an embeddable map in Compose, and
the offline region manager that `MapLibreOfflineMapRepository`'s 367 lines exist to drive.

Its documentation states plainly that the API is not stable and to expect breaking changes between
minor releases. So the map layer is still 2,737 lines rewritten, but rewritten **once, shared
across both platforms**, against a library that will move under it, rather than twice against two
native SDKs. That is a materially better position and a materially riskier dependency, and the
project's own rule about pinning exact versions applies with more than usual force.

Not determined by this report: whether `maplibre-compose` exposes the specific style-layer and
expression API this code uses, and whether its offline API covers region status and error
reporting at the granularity `MapLibreOfflineMapRepository` reports. Both are answerable by
reading its source and should be answered before any estimate is committed to.

### 4d. Background track recording

`service/TrackRecordingService.kt`, 288 lines, an Android foreground service with
`foregroundServiceType="location"`, a persistent notification, and `ACTION_START`/`ACTION_STOP`
intent control.

The sampling logic is not in this file. `LocationSampler` and `RecordTrackPointsUseCase` are in
`domain/`, and the service collects `LocationTracker` fixes and hands them over. So the 288 lines
are genuinely the Android host and the port is a new iOS host of comparable size.

iOS has no foreground service. It has `CLLocationManager` with `allowsBackgroundLocationUpdates`
and the `location` background mode, which does deliver continuous updates while backgrounded, with
the system's own blue indicator instead of an app-owned notification. Functionally this reaches
the same place. Three things differ and they are product decisions, not engineering ones:

1. **The notification is the control surface.** The Android service's notification is where the
   user sees recording is live and stops it. On iOS that surface does not exist in the same form.
2. **Permission model.** iOS asks "While Using" before "Always", and the Always upgrade is a
   separate prompt the system may re-ask. The domain contract already handles denial explicitly
   (`LocationTracker.fixes` emits `LocationFix.PermissionDenied` once and completes), which is the
   right shape, but the iOS state machine has more states than the Android one this was written
   against.
3. **App Review.** Continuous background location is a category Apple scrutinises and requires a
   clear justification string for. A multi-hour forest track is a defensible case. It is still a
   review surface Android does not have.

### 4e. Declination, and one concrete gap

`sensor/AndroidDeclinationProvider.kt` calls Android's `GeomagneticField`, the World Magnetic
Model shipped inside the Android framework, evaluated on-device with no network.

iOS ships no equivalent public API. CoreLocation's `CLHeading` gives `trueHeading` and
`magneticHeading`, so their difference yields declination at the device's current position when a
heading is available.

The domain interface is wider than that. `DeclinationProvider.declinationDegrees(latitude,
longitude, altitudeMeters, epochMillis)` asks for declination at an arbitrary point and time.
Checked who calls it: the only production caller is `ComputeTrueHeadingUseCase.kt:65`, which
passes the current position and caches per position (`AppContainer.kt:133-134`). So in practice
the CoreLocation difference would serve, but it only works when a heading is live, and it cannot
answer the interface's actual contract. The honest options are a bundled WMM coefficient set on
the iOS side, or narrowing the interface to what both platforms can really answer. The second is
the smaller change and the larger decision.

### 4f. Photos and camera

`photo/` is 210 lines across 4 files, plus `ActivityResultContracts.TakePicture`, `PickVisualMedia`,
a `FileProvider`, and an `androidx.exifinterface` read for GPS EXIF. The manifest comment records
that `ACCESS_MEDIA_LOCATION` exists specifically so EXIF GPS is not silently redacted.

iOS equivalents all exist: `UIImagePickerController`/`PHPickerViewController`, and `PHAsset`
location, which carries its own permission wrinkle in that the picker returns location only under
certain authorisation. No FileProvider concept is needed. Small in lines, fiddly in behaviour, and
the EXIF-redaction class of bug has an iOS analogue that will need finding independently.

## 5. The UI, and why 21,105 lines is not the number people expect

The UI is 21,105 lines and it is essentially all Compose. Counting Android imports across `ui/`:
295 `androidx.compose.foundation`, 222 `androidx.compose.material3`, 187 `androidx.compose.ui`,
142 `androidx.compose.runtime`, 87 `androidx.compose.material`, 21 `androidx.compose.animation`.
Against roughly 40 hard-Android imports in total: `Toast` (5), `Log` (5), `Context` (5),
`Intent` (4), `ViewModel`/`viewModelScope` (8), `Uri` (3), `Manifest` (3), `activity.result` (3),
`Lifecycle` (5).

Compose Multiplatform for iOS went stable in 1.8.0 (May 2025) and is at 1.11.x as of 2026. So the
UI is, in principle, shared rather than rewritten. That is the single biggest reason this port is
worth taking seriously at all.

Four caveats, all real:

1. **`AndroidView` is Android-only.** Covered in §4c. Every map host goes through the CMP wrapper.
2. **Material3 version alignment.** This repo pins `material3 = "1.5.0-alpha26"` outside the
   Compose BOM, deliberately, for expressive components named in
   `docs/plans/understory-design-system.md`. Checked: **none of `VerticalFloatingToolbar`,
   `HorizontalFloatingToolbar`, `FloatingActionButtonMenu`, `SplitButton`, `ButtonGroup`,
   `LoadingIndicator` or `MaterialShapes` appears anywhere in `src/main` today**, so the current
   UI is on a conservative Material3 surface and the alignment risk is future, not present.
   Adopting them before a port decision would move that risk into the present.
3. **A Material app on iOS still looks like a Material app.** That is a product question. For a
   field tool with a map, a HUD and a journal, it may be the right trade. It is a question the
   port should answer deliberately rather than discover in review.
4. **The four ViewModels** (`AvailabilityViewModel` 1,067, `MushroomLogViewModel` 749,
   `CartographyViewModel` 565, `TrackRecordingViewModel` 548) extend `androidx.lifecycle.ViewModel`.
   `androidx.lifecycle` is itself KMP now, so this is likely a dependency swap rather than a
   rewrite, but it was not verified against these files' actual API usage.

`MainActivity.kt` is 466 lines and is Android-only by construction: `enableEdgeToEdge()`, activity
result launchers, permission requests. Its iOS counterpart is new code.

## 6. Tests, which is where the honest cost hides

166 test files, 34,506 lines. Split by whether the file imports anything from `android`:

- **99 files, 14,277 lines: no Android import.** These are the domain tests and they move with the
  domain.
- **67 files, 20,229 lines: Android-coupled.** 63 use `RobolectricTestRunner` or `AndroidJUnit4`,
  25 use `createComposeRule`/`createAndroidComposeRule`.

So **59% of test lines are on a harness with no iOS equivalent.** Robolectric is a JVM
Android-framework simulator; there is nothing to point it at on iOS. Compose Multiplatform does
provide `runComposeUiTest` for shared UI tests, so the 25 Compose test files have a migration path,
but it is a migration, not a recompile.

This matters more here than in most projects, because CLAUDE.md's Known pitfalls are, to a
striking degree, a record of tests that passed without testing anything: the reverted-variant
check that read a stale JUnit XML, the migration test that passed with the migration removed, the
log parser that confirmed a correlation on a sample missing every disconfirming case, the semantic
`performClick` that passed while the control was covered. A port that carries 20,229 lines of test
across a harness boundary is exactly the situation where a suite goes green for reasons unrelated
to the code. The repo's own rule applies directly: a check whose input you have not verified has
not been run yet.

There is a second, sharper instance already documented. CLAUDE.md records that **Robolectric
reports zero window insets**, so the fullscreen and navigation-bar layout bugs it describes were
invisible to 979 green tests and visible in two device screenshots. An iOS port inherits that
exact class of problem with a different set of real insets (the home indicator, the Dynamic Island,
the notch) and no harness that reports them. Every layout correctness claim on iOS is
device-or-simulator-only from day one.

## 7. What the shape of the work actually is

Three viable shapes. This report does not choose between them; that is an architectural decision
and CLAUDE.md makes it a stop-and-ask.

**A. Kotlin Multiplatform, shared domain and data, native SwiftUI on top.**
Shares roughly 12,400 lines (domain + data, after the §3a and §4a/§4b rewrites). Rewrites 21,105
lines of UI in SwiftUI. Highest platform fidelity, highest cost, and it permanently doubles the
cost of every future UI change.

**B. Kotlin Multiplatform with Compose Multiplatform UI.**
Shares domain, data and most of the UI. The rewrites are: §3a's 27 domain files, Room migrations to
the driver API, Retrofit to Ktor, the map layer to `maplibre-compose`, and new iOS hosts for
location tracking, sensors, photos, alerts and crash logging. Lowest total cost by a wide margin.
Buys a dependency on `maplibre-compose`'s unstable API and accepts a Material-shaped iOS app.

**C. Full native rewrite in Swift.**
Nothing shared. The 7,452 lines of domain logic, which are the part with real intellectual content
and the part covered by the 99 portable test files, get re-derived from scratch. Mentioned only to
be rejected: it discards the single asset that makes this port cheap.

Under B, the module split and the §3a work is the first movable step and it improves the Android
codebase whether or not iOS ever ships. That is the only part of this that is safe to start
speculatively.

## 8. A standing note that is not a ruling

`docs/plans/ideas-from-fusion-plan.md:219` lists "**iOS, and a third-party SDK**" among things
"recorded so they are not revisited."

Read in context, that list is rejecting elements of a **different** plan: a commercial positioning
service with 13 FTE, licensed Wi-Fi and cell databases, a backend, telemetry and crowdsourced
uploads, which that document's own header says was "not written for this project"
(`ideas-from-fusion-plan.md:20-23`). The line rejects iOS **as a target of that fusion-service
plan**, alongside BLE beacons and indoor wayfinding.

It does not obviously constitute a standing ruling against an iOS port of Forager as a product.
It is also not obviously *not* one. That document's header states "nothing here is authorised or
scheduled," which cuts both ways: it is a register of ideas, so its rejections are as
unauthoritative as its proposals.

**This is flagged, not resolved.** If a port is ever contemplated, the owner should say which
reading was intended, and the answer belongs in a ruling that supersedes the line rather than in
an edit to it, per this repo's convention that audits and registers are historical records.

## 9. Disclosure

### Confirmed, by reading this tree
Every figure in §2 through §6: file counts, line counts, import counts, the zero-Android-import
property of `domain/`, the 26 interfaces, the 27 JVM-API-dependent domain files, database version
15 and twelve migrations, all migration signatures taking `SupportSQLiteDatabase`, the 2,737
MapLibre-coupled lines and the API surface list, the 512-line Retrofit layer, the 1,882-line
platform layer behind owned interfaces, manual DI via `AppContainer`, the resource counts, the
test split at 99/67 files and 14,277/20,229 lines, the absence of every expressive Material3
component from `src/main`, and `ComputeTrueHeadingUseCase.kt:65` as the sole production caller of
`declinationDegrees`.

### Inferred or taken from external sources, not verified against this tree
- Room 3.0's KMP status and the `SupportSQLiteDatabase` to `SQLiteConnection` migration-callback
  change: Android Developers Blog and the Room KMP setup docs.
- Compose Multiplatform iOS stable since 1.8.0, currently 1.11.x: JetBrains and Kotlin docs.
- `maplibre-compose`'s platform support, iOS offline-download support, and unstable-API warning:
  its own documentation site, fetched.
- iOS `CLLocationManager` background behaviour, `CLHeading` fields, and photo-picker location
  authorisation: from general knowledge, **not checked against current Apple documentation**, and
  they should be before any of §4d or §4f is priced.

### Could not be determined
- Whether Room KMP's builder supports `createFromAsset` on iOS, which the 4.4 MB `fungi_index.db`
  depends on.
- Whether `maplibre-compose` covers the six style-layer types, `Expression`, and the offline
  region status and error reporting this code uses.
- Whether `androidx.lifecycle.ViewModel`'s KMP form covers the API the four ViewModels use.
- Any calendar or effort estimate. This report deliberately gives none. LOC is a measure of
  surface, not of difficulty, and the two hardest items here (the map's unstable dependency and
  the 20,229 Robolectric lines) are not proportional to their line counts in either direction.

### Premises a reader might arrive with that are wrong
- **"The UI is the expensive part because it is the biggest."** The UI is 21,105 lines and is the
  most likely layer to be *shared*, not rewritten. The tests are 34,506 lines and 59% of them are
  on a harness with no iOS counterpart. The test suite is the larger and the less visible cost.
- **"The map has to be rewritten twice in native code."** A Compose Multiplatform MapLibre wrapper
  exists with iOS offline support. One rewrite, shared, against an unstable API.
- **"`domain/` is Android-free so it is portable."** It is Android-free and JVM-bound. 27 of 144
  files import `java.time`, `java.util`, `javax.xml`, `org.w3c.dom`, `java.text` or `mil.nga`.
  Android-free and multiplatform-ready are not the same property, and the `mil.nga:mgrs` dependency
  was chosen precisely because it is pure Java.
- **"The project already decided against iOS."** See §8. The line exists; what it governs is
  ambiguous, and resolving it is the owner's.

### Decided beyond scope
Nothing. No code was changed. This report is the only artifact.
