# Android and iOS from one codebase: feasibility report (pre-build; nothing built in the app)

**Asked by the owner, 2026-10-26:** "Can we figure out a way to write an interface for Android and iOS
development that bridges the two together into a unifying code?"

**Owner ruling, 2026-10-26, verbatim:** "Option B, file the report on pre-main". Option B is Kotlin
Multiplatform with Compose Multiplatform (below). Confirmed on 2026-10-27, after the owner asked what
Option C offers, verbatim: "Option B stands". Decisions 2 to 4 under "Options for the owner" are still
open.

**Base:** `pre-main` at `b2c4951e` (merge of PR #416). Every figure below was measured on that commit. A
first pass was measured on `main` at `1772151` and then re-run here, because `pre-main` had moved 29 files
in the measured areas (see "Premises that were wrong"). Nothing in `app/` was changed. All builds ran in a
scratch project outside the repository, with the domain sources copied in unchanged.

**Filing note, 2026-10-27:** by the time this was filed, `pre-main` was at `a9c37eb3`, 36 commits past
`b2c4951e`. Those commits change 17 files in the measured areas, all under `domain/`: 4 new
(`ClimbAllowance.kt`, `referencetrails/ReferenceTrailRecord.kt`, `wayhome/WayHomeHeights.kt`,
`wayhome/WayHomeOffTrail.kt`) and 13 edited. They change no build or version file and add no `java`,
`javax` or `android` import (counted from the diff). The compiler figures in Findings 1 and 2 were not
re-run on `a9c37eb3`. Finding 1 shows that imports miss JVM APIs such as `Math` and `String.format`, so
the 66-file and 100-file counts are true of `b2c4951e`, not necessarily of the filing commit.

Paths are under `app/src/main/java/com/zynergylabs/forager/app/` unless stated. Each claim is marked
**read** (file and line), **built** (compiler output, checked as described), **counted** (a script over
the tree), **external** (a cited page or Maven metadata read on 2026-10-26), or **not verified**.

---

## Short answer

Yes. The route that fits this codebase is **Kotlin Multiplatform (KMP)**. The "interface that bridges
the two" mostly exists already: the ports in `domain/` that CLAUDE.md's Architecture rule asked for
(`LocationProvider`, `LocationTracker`, `CompassProvider`, `PressureProvider`, `AlertDelivery`,
`WakeUpAlarms`, `PhotoSource`, `TrailRouter`, `OfflineMapRepository` and about 60 more; counted). Each has
an Android implementation outside `domain/` (`location/AndroidLocationProvider.kt`,
`sensor/AndroidCompassProvider.kt`, `alert/AndroidWakeUpAlarms.kt`, and so on). In KMP the interfaces move
to shared code and iOS gets a second set of implementations. Domain logic keeps its shape.

What an interface cannot bridge is **behaviour the two operating systems do not share**. The largest of
those are trail routing, background wake-ups, the map and the camera (Finding 3).

---

## Findings

### 1. The domain compiles as shared code once 66 files change (built)

All 227 files of `domain/` and its sub-packages (16,871 lines) were copied unchanged into a scratch KMP
project's `commonMain` and compiled with `compileCommonMainKotlinMetadata`. The probe used Kotlin 2.3.10,
coroutines 1.11.0 and serialization-json 1.11.0, the versions in `gradle/libs.versions.toml` (read), and
Forager's own Gradle wrapper, 9.7.0.

- **66 files fail** (522 errors). The cause in each file, by file count, is:
  - `java.time`: 27
  - `java.io` files and streams: 11
  - `Math.*`: 8
  - `java.util.UUID`: 8
  - `String.format`: 6
  - `System.*`: 5
  - `synchronized`: 4
  - `Locale`: 3
  - XML DOM parsing: 2 (`domain/GpxCodec.kt`, `domain/GpxImportReader.kt`)
  - `AtomicInteger` and `TimeUnit`: 2
  - the NGA MGRS Java library: 1 (`domain/MgrsConverter.kt`)
  - `java.text.Normalizer`: 1 (`domain/NormalizeSearchName.kt`)
  - `ByteBuffer`: 1 (`domain/TrackPathCodec.kt`)
  - `PriorityQueue`: 1 (`domain/TrackSelfJoin.kt`)
  - plus 5 that read as cascades or one-offs (`javaClass`, inference failures).
- **161 files compile with no change, but only while the 66 are present.** The failing files were then
  removed and the build recompiled until clean, which took six rounds. That left **100 files (5,752
  lines) that stand alone**. The other 61 depend on types declared in the 66, mostly `LocalDate`.
- So **66 files need edits, and the other 161 are expected to need none once those land.** That second
  part is a prediction, not a build result.
- **One port leaks a JVM type into its signature (read):** `domain/routing/TrailRouter.kt:27`,
  `suspend fun route(start: LatLng, end: LatLng, routeData: File)`, with `import java.io.File` at line 4.

### 2. Shared code compiles for iOS on Linux (built)

The 100-file stand-alone set was compiled with `compileKotlinIosArm64 compileKotlinJvm` on a Linux
container, with `kotlin.native.enableKlibsCrossCompilation=true` in the probe's `gradle.properties`. The
result was `BUILD SUCCESSFUL`, and it was checked against the output rather than the summary line:

- the log shows `> Task :compileKotlinIosArm64` ran (not skipped);
- the klib manifest reads `native_targets=ios_arm64` and `builtins_platform=NATIVE`;
- 103 of the klib's 110 files contain the `zynergylabs` package name;
- the JVM side produced 306 classes.

The only "disabled" text in the log is the proxy's `disabledSchemes` JVM option, not a disabled target.

What this shows: shared Forager code can be compiled for iOS without a Mac. The owner's Ubuntu host and
the existing `ubuntu-24.04` CI can therefore check that shared code still compiles for iOS. What it does
not show: linking an app, running it, or anything on a device. Those need macOS and Xcode (Finding 4).

### 3. What is Android-bound, and how hard each piece is

| Piece | Where (read or counted) | iOS path | Difficulty |
|---|---|---|---|
| Trail routing | `:brouter`, BRouter v1.7.10 vendored as 19,327 lines of Java (`third_party/brouter/README.md`), used by `routing/BRouterTrailRouter.kt` and `routing/RoutingDataFolder.kt` | Java does not run on iOS. Port BRouter to Kotlin, or ship iOS without trail routing at first | **Highest.** Owner decision 2 |
| Wake-up alarms (back-by, sundown) | `alert/AndroidWakeUpAlarms.kt:36`: `AlarmManager.setAndAllowWhileIdle` into a `BroadcastReceiver` that runs the watch | iOS has no general way to run app code at a chosen time. `BGTaskScheduler` timing is the system's choice (external, secondary sources). Needs its own design | **High** and safety-relevant. Owner decision 3 |
| Track recording | `service/TrackRecordingService.kt`, a foreground service with `foregroundServiceType="location"` (`AndroidManifest.xml`) | iOS background location mode (not verified) | High |
| Map | `org.maplibre.android` imported in 30 files (26 in `ui/map`, 4 in `map`); offline in `map/MapLibreOfflineMapRepository.kt` (494 lines) | `maplibre-compose` 0.20.0 has iOS artifacts and lists offline maps on Android and iOS, but says "The API is not yet stable" (external). Or host MapLibre iOS directly | High |
| Camera, RAW, DNG | CameraX in `photo/` (2,822 lines), `data/raw/ForagerDngWriter.kt`, `photo/RawShotSaver.kt` | AVFoundation, as a separate implementation (not verified) | High |
| Database | Room 2.8.4. `data/local/Migrations.kt` has 20 `SupportSQLiteDatabase` references; `data/local/fungiindex/FungiIndexDatabase.kt` uses `createFromAsset` | Room ships iOS artifacts for 2.8.4 and 2.8.5 (external, Google Maven). Migrations move to the `SQLiteConnection` API. How the asset database maps to iOS is not verified | Medium |
| Settings | DataStore 1.2.1 | DataStore ships iOS artifacts for 1.2.1 and 1.3.0 (external) | Low |
| Network | Retrofit and OkHttp in `data/remote/` (463 lines with DTOs) | Ktor (3.6.0 has a Darwin engine; external) behind the existing repositories | Low to medium |
| Logging, icons | `android.util.Log` in 80 files; 181 Material icon import lines. 58 files (13,858 lines) are bound by only these two (counted) | A logging port. JetBrains' `material-icons-extended` 1.7.3 has iOS variants, frozen since 2024 (external) | Low |
| Compose UI | 213 files import Compose (44,186 lines); 69 of them also import Android, MapLibre or CameraX APIs (counted, upper bound) | Compose Multiplatform: latest stable 1.12.2, iOS stable since 1.8.0 (external). Forager pins Jetpack Material3 `1.5.0-alpha26`; whether CMP's Material3 has the same APIs is not verified | Medium, file by file |

The UI counts come from an import scan. Finding 1 showed that scan misses JVM APIs that need no import,
so treat the UI figures as upper bounds until a compiler pass is run over them.

### 4. Build and tooling facts

- **AGP 9 (external):** the KMP plugin "stops being compatible with the `com.android.application` and the
  `com.android.library` plugins". Shared code goes in a module using
  `com.android.kotlin.multiplatform.library`, and the Android app stays its own module. Forager is one
  `:app` module today (`settings.gradle.kts`, read) with `android.builtInKotlin=false` for KSP
  (`gradle.properties`, read). A module split comes first.
- **A Mac is required** to build, link and run the iOS app (external, Kotlin quickstart: "you need a macOS
  host with Xcode installed"). CI is `ubuntu-24.04` only (`.github/workflows/ci.yml`, read). Local runs
  moved to native Ubuntu 26.04 (`docs/windows-host-test-baseline.tsv:2`, read).
- **Tests (counted):** there are 696 test files with 5,121 `@Test`. 365 files use Robolectric or
  `AndroidJUnit4`, which run on the JVM only. Of the 138 domain test files, 125 are plain JUnit 4, 5 run
  under Robolectric or `AndroidJUnit4`, and 8 are fakes or fixtures. The plain JUnit ones can keep
  running on the JVM against shared code. Running them on iOS too would need `kotlin.test`.
- **Google's position (external):** KMP "is officially supported by Google for sharing business logic
  between Android and iOS". The same page attributes Compose Multiplatform to JetBrains and does not claim
  Google support for it.

### 5. A prior mention of iOS (read)

`docs/plans/ideas-from-fusion-plan.md:219` lists "iOS, and a third-party SDK." under **EXPLICITLY
REJECTED**. Its scope is a planner's ideas register harvested from an outside location-fusion plan. The
register's header says "nothing here is authorised or scheduled", and it rejects that plan's iOS SDK, not
an iOS Forager. `RECORD.md` has no match for iOS, iPhone, multiplatform or KMP (counted). No earlier
owner ruling either way was found.

---

## Options for the owner

| | What is shared | iOS UI | Cost | Fit |
|---|---|---|---|---|
| A. KMP, logic only | domain, data, repositories | SwiftUI, written fresh | Up to ~44k lines of screens rewritten in Swift | Most native feel; two UIs kept in step forever |
| **B. KMP + Compose Multiplatform (ruled)** | domain, data, most screens | Shared Compose, with native map, camera, sensors and background pieces | Smallest total; map and camera still per platform | Best fit for a one-owner codebase already in Compose |
| C. Separate iOS app in Swift | nothing | Swift | Everything written twice | See below |
| D. Flutter or React Native | everything, after a rewrite | their own | All ~98k Kotlin lines rewritten | Throws away the codebase |

**Why C was not chosen (the owner asked what it offers).**

What it offers:
- Zero change to the Android app or its Play path. B needs the module split, the `java.time`
  replacement and the Room migration rewrite to pass the `pre-main` gate first.
- Fully native iOS controls. Compose Multiplatform draws its own controls and ships its own renderer.
- No pre-1.0 bridge such as `maplibre-compose`.
- Apple's tools end to end.

The hardest pieces (routing, wake-ups, map, camera) are per platform under B as well, so B's saving is
the domain and the ordinary screens.

What it costs:
- Every feature and fix is built twice.
- The safety logic (`ReturnWatch`, `SundownWatch`, `OffTrackJudge`, track sampling) is kept in two
  copies that drift, with the domain tests covering one.
- The evidence work doubles: failing-first tests, revert checks and device checks.
- Backup, GPX and journal formats have to be kept compatible by hand.

B does not rule out native iOS screens later: Compose Multiplatform can host Apple's own views, which is
how the map and camera would be done anyway.

**The first step under B** is a shared module holding `domain/`, with the 66 files ported and the
`TrailRouter` signature fixed. It is not free on Android: `kotlinx-datetime` types replace `java.time` in
domain signatures, and that reaches every caller.

**Decisions still open:**

1. ~~A or B~~: B (owner, 2026-10-26).
2. ~~Routing on iOS: port BRouter to Kotlin, or ship iOS without trail routing at first~~: port BRouter to
   Kotlin (owner, 2026-10-27). See "Update, 2026-10-27" at the end.
3. ~~What back-by and sundown alerts promise on iOS, since a wake-up at a set time is not available
   there~~: ruled 2026-10-27; see "Update, 2026-10-27: iOS alerts, and no Mac" at the end.
4. ~~Mac access (a Mac, or macOS CI runners), plus an Apple Developer account for TestFlight and the App
   Store~~: no Mac (owner, 2026-10-27); see the same update.

---

## Premises that were wrong

- **The first repository read was the wrong one.** The session started on `slayer8366/Forager-app` until
  the owner redirected it to `slayer8366/Forager`.
- **The first figures were measured on `main` (`1772151`), not `pre-main`.** `pre-main` had moved 29
  files in the measured areas, including new `domain/wayhome/` and `domain/wayto/` packages and an edit
  to `domain/routing/TrailRouter.kt`. Every figure here was re-run on `b2c4951e`. Those first figures were
  60 failing files, 96 stand-alone and 29 MapLibre files, and they appear in no other document.
- **The import scan overstated what was shareable.** It called 20 domain files platform-free that the
  compiler rejected (`Math`, `synchronized`, `String.format` and `System` need no import). The compiler
  result replaces it for the domain. The UI figures in Finding 3 still come from the scan.
- **"161 files compile" is true only with the 66 failing files present.** The stand-alone set is 100.
- **The first MapLibre figure (128) was import lines, not files.** It is 30 files on `pre-main`.

## Not verified

- No iOS app was linked or run. Finding 2 is a compile, not a binary.
- Whether Forager's Material3 `1.5.0-alpha26` APIs exist in Compose Multiplatform's Material3.
- Whether `maplibre-compose`'s offline API covers what `MapLibreOfflineMapRepository.kt` does, including
  the PMTiles basemap.
- iOS background location during a recording, and App Store review of "Always" location.
- iOS RAW capture and DNG output.
- How `createFromAsset` for the fungi index maps to Room on iOS.
- The 66-file port itself. The prediction that the other 161 then compile unchanged is untested.

## Sources (read 2026-10-26)

- Kotlin, AGP 9 migration for KMP: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- Kotlin, quickstart (macOS for iOS): https://kotlinlang.org/docs/multiplatform/quickstart.html
- Android, KMP and supported Jetpack libraries: https://developer.android.com/kotlin/multiplatform
- Android, Room for KMP: https://developer.android.com/kotlin/multiplatform/room
- JetBrains, Compose Multiplatform 1.8.0 (iOS stable): https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/
- MapLibre Compose: https://maplibre.org/maplibre-compose/
- Apple, background tasks: https://developer.apple.com/documentation/uikit/using-background-tasks-to-update-your-app
- Versions: Maven Central and Google Maven `maven-metadata.xml` for the artifacts named above.

---

## Update, 2026-10-27: routing ruled, and what the port involves

**Owner ruling, verbatim:** "Port BRouter to Kotlin". This answers decision 2. Nothing has been ported.

Measured on `pre-main` at `a4264e26`. `third_party/brouter/`, `routing/` and `domain/routing/` are
unchanged since `b2c4951e` (counted from the diff). Paths below are relative to
`third_party/brouter/src/main/java/btools/` for BRouter and to the app package for Forager.

### What Forager actually uses (read)

- **Seven BRouter classes**, imported by `routing/BRouterTrailRouter.kt` and `routing/RoutingDataFolder.kt`:
  `RoutingEngine`, `RoutingContext`, `RoutingParamCollector`, `OsmTrack`, `OsmNodeNamed`, `PhysicalFile`
  and `DataBuffers`.
- **Not BRouter's thread.** `router/RoutingEngine.java:32` declares `extends Thread`, but
  `BRouterTrailRouter.kt:170` calls `engine.doRun(maxRunningTimeMillis)` directly on a coroutine.
  Cancellation goes through `engine.terminate()` (`:174`), which sets a `volatile` flag
  (`RoutingEngine.java:69`) that the search loop reads.
- **Only the track's points.** `BRouterTrailRouter.kt:105-114` reads `track.nodes` and each node's
  `getILat()`/`getILon()`. It does not call BRouter's GPX, KML, JSON or CSV output.
- **No reflection in practice.** `router/RoutingContext.java:91` loads a path model with
  `Class.forName(className)`. The name comes from the profile (`expressions/BExpressionContext.java:979`),
  and the pinned `app/src/main/assets/routing/profiles2/hiking-mountain.brf` has no model line, so it gets
  the default `StdModel` (`RoutingContext.java:88`). Kotlin/Native has no `Class.forName`. The port would
  keep the default and replace the lookup with a fixed list.

### How much has to be ported (counted)

- **Reachable from those seven classes: 76 of 101 classes, 16,296 of 19,327 lines.** This is a
  class-level count (any reference pulls in a whole file) for the pinned profile, so it is an upper bound.
- It includes output formatting (`router/Format*.java`, 1,562 lines) and voice hints
  (`router/VoiceHint*.java`, 1,342 lines). Forager reads neither, so a Forager-only port is likely nearer
  13,400 lines. That is not verified method by method.
- Not reachable: the kinematic car model (`Kinematic*`), the rd5 diff tools (`Rd5Diff*`), and the
  `DiffCoder`/`MixCoder` streams, among 25 classes.
- **37 of the 76 reachable files import JDK APIs.** Collections (`List`, `Map`, `ArrayList`, `HashMap`)
  map directly onto Kotlin. What needs real work:
  - file I/O, including `RandomAccessFile` (2 files) for reading route data, which needs a multiplatform
    random-access file API (Okio's `FileHandle` is one candidate, not checked against BRouter's access
    pattern);
  - `TreeMap`, `TreeSet`, `NavigableMap` and `SortedSet`, which have no Kotlin common equivalent;
  - date and number formatting, mostly in the output code above.
- Implicit `java.lang` use in reachable files: `System.` 18, `Math.` 17, `Integer.` 9, `Double.` 6,
  `synchronized` 2, `Thread` 2, `String.format` 2.

### Proving the port routes the same (read, and a gap)

- **Today's check against BRouter is 2 routes on one tile.** `app/src/test/resources/routing/README.md`:
  `brouter-cli/*.geojson` holds BRouter 1.7.10's command-line output for `inside-across-the-degree-line`
  and `inside-long` on `segments/W75_N40.rd5` (152,665 bytes, part of Harriman and Bear Mountain State
  Parks). That is enough to show the vendored engine is wired up correctly. It cannot vouch for a
  rewrite of about 16,000 lines, because it never reaches most of the cases that could fail one (CLAUDE.md,
  "A check that passes because it never saw the data that could fail it").
- **The Java engine can be the oracle.** It can stay in the build as test-only code on the JVM. A
  differential test then runs the Java and Kotlin engines on the same inputs and requires identical
  output. The sample has to include the cases that could fail it: many route pairs, more than one tile, a
  degree line crossed, no route found, the time limit, and a cancel mid-search.
- **Not verified:** whether Kotlin/Native's floating-point maths matches the JVM's closely enough for
  identical routes on iOS. BRouter's coordinates are integers, but some cost terms are doubles.

### What the port costs beyond the code

- The "Unmodified" check in `third_party/brouter/README.md` stops applying, and taking an upstream fix
  becomes a hand port rather than replacing the tree from a new tag.
- BRouter is MIT-licensed; the port must carry its copyright notice, and the credits entry in
  `app/src/main/assets/credits/software.json` must stay accurate.

### Decisions this ruling left open, and how they were ruled

The owner, 2026-10-27, verbatim: "a recommended, b Forager-only, c download in CI".

- **a. Sequencing: the recommended order.** Port into shared Kotlin, prove it identical to the Java engine
  on the JVM, then switch Android to it, keeping the Java engine as a test-only oracle. Both platforms
  route with one engine. Rejected: keeping Java BRouter on Android and using the port only on iOS, which
  leaves two engines that can drift apart.
- **b. Scope: Forager-only.** Port only the path Forager reaches. Output formatting, voice hints, the
  kinematic model and the diff tools are left out. Rejected: porting the whole engine. Since the
  13,400-line estimate is not verified method by method, the port's first step is to establish what
  Forager's calls actually reach.
- **c. Test data: downloaded in CI.** The larger rd5 sample for the differential tests is fetched in CI,
  not committed. Rejected: more fixtures in the repository. Precedent for how: `server/pmtiles-worker/
  test/rd5/fetch-brouter.sh` downloads the BRouter release from a fixed URL and checks a pinned SHA-256
  (lines 11-12, 28). The usual tile source is `https://brouter.de/brouter/segments4/`
  (`docs/navigation/2026-10-03-trail-navigation-research.md:58`). **Not verified:** whether that site
  keeps a tile's bytes stable at one URL. If tiles are regenerated in place, a pinned hash fails when they
  change, so the dispatch has to say where a pinned copy lives. ODbL attribution applies to downloaded
  tiles as it does to the fixture.

Building any of it needs a dispatch from the planner, recorded as an intent in `RECORD.md`.

---

## Update, 2026-10-27: iOS alerts, and no Mac

### Decision 3: what the alerts promise on iOS

**Owner ruling, verbatim:** "be back by a certain time, and display when the sundown time is".

**What Android does today (read):**

- **Back by** (`domain/BackBy.kt`, `domain/BackByWatch.kt`): a check-in time set while recording. It
  alerts once if the walker is still out when the time passes. It ends only by the walker's choice (I'm
  back, or later), and it stays quiet if it sees they have arrived. It is driven by every fix, the
  service's 15 s timer, and an inexact wake-up alarm at the time (`BackByWatch.kt`'s header).
- **Sundown** (`domain/SundownWatch.kt`): three alerts (a heads-up, a leave-by timed from the walk home,
  and a sunset alert), plus the sundown line on the map strip and the navigation display, with its
  "start back by" time.

**The reading this report takes of the ruling.** On iOS, Back by keeps its promise: an alert at the
time the walker set. iOS can post a notification at a set time without the app running; what it cannot
do is run the watch's own code then (Finding 3). Sundown becomes **displayed, not alerted**: the
sundown time is shown, and the three sundown alerts are not promised on iOS. Under CLAUDE.md ("An
unsupported feature or capability returns an explicit 'unsupported'"), the iOS `WakeUpAlarms` returns
`false` for `SUNDOWN`, and nothing on the iOS screen suggests a sundown alert is coming.

**Two points the ruling does not settle (stop-and-ask; both ruled in "Update, 2026-10-27: the last three
answers" at the end):**

- **i. A Back by alert after the walker is home.** Android stays quiet once it sees arrival. On iOS a
  notification scheduled for the time fires unless the app is running to cancel it, so it can arrive
  after the walker is back. Is that acceptable, or should iOS say so where Back by is set?
- **ii. What "display" includes.** The sundown time only, or the sundown line as Android shows it, with
  "start back by" computed from the walk home? That line is worked out while the app is open, so it needs
  no background wake-up.

### Decision 4: no Mac

**Owner ruling, verbatim:** "I don't have a Mac and I don't care to get one".

What that leaves (external unless marked):

- **iOS builds run in CI on GitHub's macOS runners.** GitHub's docs: "Use of the standard GitHub-hosted
  runners is free and unlimited on public repositories", with macOS labels including `macos-15` and
  `macos-latest`. Forager is public. If it ever went private, macOS minutes would be billed. CI today is
  `ubuntu-24.04` only (`.github/workflows/ci.yml`, read), so an iOS job is new work.
- **Shared code is still checked on Linux.** Finding 2 compiled shared code for `ios_arm64` on Linux
  (built). Linking, running and signing the app happen only in the macOS job.
- **The iOS project files are edited without Xcode.** An iOS app needs an Xcode project; with no Mac, it
  would be generated in CI from a text description rather than edited in Xcode. XcodeGen and Tuist are
  two such tools; neither was tried here.
- **Signing and TestFlight** need a paid Apple Developer Program membership. Whether the whole chain
  (certificate request, profiles, upload with an App Store Connect API key) can be done from Linux plus CI
  is **not verified** in this session.
- **Slower iOS iteration.** Every iOS-only change (map, camera, location, notifications) is checked by a
  CI round trip, not on a local simulator. Most screens can still be worked on in the Android build,
  since they are shared under Option B.
- **Device checks.** This project checks behaviour on a phone (the S22). The iOS simulator has no real
  GPS track, compass, barometer or camera. **Open question:** is there an iPhone available for iOS device
  checks? If not, iOS device behaviour (background location, the Back by alert) stays unverified.
  (Ruled in the update below: no iPhone.)

Source: GitHub, "Supported runners and hardware resources",
https://docs.github.com/en/actions/reference/runners/github-hosted-runners (read 2026-10-27).

---

## Update, 2026-10-27: the last three answers

**Owner ruling, verbatim:** "1 acceptable, 2 full sundown line, 3 no iPhone".

- **i. A late Back by alert is acceptable.** On iOS the alert is scheduled for the walker's time and may
  arrive after they are home if the app was not running to cancel it. Nothing is added where Back by is
  set to say so. Rejected: a note on iOS explaining it.
- **ii. iOS shows the full sundown line,** as Android does on the map strip and the navigation display,
  including "start back by" computed from the walk home (`domain/SundownWatch.kt`, its `shown`). The
  computation is shared domain code under Option B. What drives it on iOS is a design item for the
  dispatch: on Android it is the recording service's tick, which iOS does not have. The three sundown
  *alerts* remain unpromised on iOS (decision 3). Rejected: the sundown time alone.
- **iii. No iPhone is available for device checks.**

### What "no iPhone" means for the evidence

Every iOS check is a simulator check, run in CI. These behaviours will be **unverified on real hardware**
for as long as that holds, and any report or release note about the iOS app has to say so:

- track recording with the app in the background, which the simulator cannot reproduce on a real walk;
- whether the Back by notification is delivered at its time on a locked phone;
- the compass, barometer and level, which the simulator does not have;
- the camera, RAW capture and DNG output;
- the map's offline tiles and touch handling on a real screen.

This is the safety-relevant part of the app. CLAUDE.md asks for partial results to be reported as such,
never as success, so an iOS build that passes CI is "simulator-checked", not "checked".

Options exist that need no iPhone of the owner's own. None was tried and none is chosen:

- a TestFlight tester with an iPhone who runs the device steps;
- a remote real-device service (cloud device farms offer real iPhones), which still cannot take a real
  walk.

Raising one of these is the owner's call, when an iOS build is close.
