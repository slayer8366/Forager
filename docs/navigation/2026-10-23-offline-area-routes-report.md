# Offline area downloads store their trail routes: storage and state, stopped at the step path (dispatch 2026-10-23-03)

**Coder:** Hazel (Opus 5.5). **Planner:** Rowan. **Base:** origin/pre-main `99ac33006` (it had #380), verified at the
start; pre-main `54f4d56c4` (#381 and #382 merged) merged in later, never rebased. **Branch:** `offline-area-routes`,
PR #383 (draft). **Status:** storage and state built and tested, then stopped at the dispatch's step path; the owner answered it,
and the region row and the automatic fetch are built (part two, at the end). An S22 launch check and one live route
download passed.

## What a region's download does now

1. The tiles download as before. Then `MapLibreOfflineMapRepository.download` asks `RegionRouteFolders.fetchFor`
   for the routes of the same rectangle (`routeAreaFor`, the box the tile bounds are built from).
2. `GET /routing/area.json` with the edges as six plain decimals, rounded outward (Sorrel's #381 review, M2), and
   the app key in `X-Forager-App-Key`. Then each listed file's URL, and only those (a missing square's URL is 404).
3. Each file streams to `<name>.download`, is checked against the list's size, the list's sha256 and the
   `X-Forager-Sha256` header, and only then is renamed to its `.rd5` name (Sorrel's #382 review, L3). The folder is
   `files/offline_routes/<region id>.partial`; a record, `routes.properties`, is written last; the folder is renamed
   to `<region id>`. A folder under an id is whole or absent.
4. Every outcome is a state on the region, logged where it happens; none undoes the map download.

**States** (`domain/routedata/RegionRouteData.kt`): `Stored` (run id, OSM timestamp, bytes, folder, and the
`missing` squares, if any), `NoneHere` (404 `outside_coverage`), `Missing(reason)`, `NotFetched` (no record: a region
from before this change, or a fetch the app was closed during), `Damaged` (the record can't be read, or a file it lists
is gone or another size). Reasons: every agreed error code has its own; an error code nobody agreed, or a known code
with a status it was not agreed with, is `UNEXPECTED_RESPONSE`, logged with the status, code and message. A 404 is
"no routes here" only with `outside_coverage`. `run_superseded` asks for the area again once.

**One unit:** the region's size is tiles plus route bytes; deleting a region deletes its routes (logged and left to
the next clean-up if it fails, since the region is already gone); `OfflineRegionReconciliation` removes route folders
no Room row, listed region or running download owns, `.partial` folders a killed process left, and unfinished deletes.

**For the engine** (item 5): `FindRouteDataForPointUseCase` answers `Found(regionId, folder, runId)`,
`SquareMissing(regionId, square)`, `NotStored(regionId, state)` or `NoRegionCovers`. Overlaps: the newest download
with stored routes wins (my choice; nothing calls this yet).

**The key:** `BuildConfig.ROUTING_APP_KEY` from `forager.routingAppKey` or `FORAGER_ROUTING_APP_KEY`, else empty. An
empty key sends nothing and records `NO_APP_KEY`. In CI the secret reaches only the "Build the debug APK" step's
environment; the unit-test step and a fork's PR build have none.

## The Room decision: no Room change

The route state lives in the region's own folder, read on every listing. Reasons, in `RegionRouteStore.kt`'s doc:
nothing joins on or filters by route state (CLAUDE.md's test for Room); a column would be a second record of what
is on disk and could disagree with it, for one when a journal backup copies `offline_regions` rows onto a phone without
these files (`data/backup/JournalTables.kt`); the tile sizes already follow "read live, never stored"; and no column
means no migration number to claim beside branches in flight, and no change to the backup's tables, which are outside
this dispatch's files. `ForagerDatabase` stays at version 22.

## The step path: proposal for the owner (the stop)

Nothing below is built. Each path is tap > shows > Back. "Routes" here means what the user would read as trail
routes; the wording is a draft.

**A. A new download, routes arrive (the usual case).**
Offline maps > Download > the bar fills > the bar says "Getting trail routes" for a moment > the region appears:
"Ridge · 12.4 MB". Nothing else on the row: map and routes are one thing. Back leaves Offline maps as today.

**B. A new download, routes fail (no signal, the server busy, a bad file).**
... > the region appears with a second line, "Map only. Trail routes didn't download." and a **Try again** button >
Try again > "Getting trail routes" > the line goes away. Back leaves as today.
Recommended with it: when Offline maps opens and the phone is online, regions in this state try again on their own
first, so the button is only for when that also failed ("don't make me think": an automatic retry beats a button).

**C. The area has no route data (outside coverage).**
... > the region appears with "Map only. No trail routes for this area yet." No button: trying again cannot help.

**D. Part of the area has no route data (`missing`).**
... > the region appears with "Trail routes stop at the edge of the covered area." Without this, a route near a
missing square stops at a five-degree line with no reason given, which reads as broken. Later, when routing is on
screen: a route that ends at that line could say the same thing there.

**E. A region downloaded before this change (no route record).**
Option 1 (recommended): Offline maps opens with a connection > those rows show "Getting trail routes" > done, as A.
Opened with no connection: "Map only. Trail routes download next time you're online."
Option 2: such rows show "Map only" and a **Get trail routes** button.
Option 3: nothing changes; routes only come with a new download.

**F. Reasons a retry can't fix** (the area too large, the server refusing the key, an app update needed): the row
reads as C, "Map only. Trail routes aren't available for this area.", with no button. Recorded and logged distinctly.

**Questions for the owner:** automatic retry (B, E1) or buttons only? Wording for B to F? Should D's notice show on
the row at all, or only when a route reaches the line?

## Evidence

**Forward:** the full suite on `883de54a3` (PR run 39973042573, every job green: build and tests, lint, release
compile, cutter, Worker): **5,373 tests, 0 failed, 24 skipped**, read from the JUnit XML. Pre-main `54f4d56c4` (run
39939798014): 5,313, 0 failed, 24 skipped. The 60 more are exactly this change's: `RegionRouteFetchTest` 37,
`RegionRouteStoreTest` 8, `FindRouteDataForPointUseCaseTest` 8, `OfflineRegionReconciliationRoutesTest` 7. No test was
skipped, ignored or weakened.

**Dex report** (`scripts/verify-dex-register-budget.sh` on that run's APK, report only): the same five methods over
256 registers, all in `CompactMainScaffold` and `JournalTab`; none in this change's files.

**`.github` diff:** four lines in `ci.yml`, the key's `env` on the "Build the debug APK" step, as the planner asked.

**Revert checks** (one edit each, its own branch and worktree cut from the forward commit, never restored from git; 0
compile errors in every build log; each JUnit XML read, 69 tests in each, the same count as the forward run). Each
failure named is one that edit can cause:

| # | Edit | Failed |
|---|---|---|
| 1 | body sha256 check off | bytes that do not hash to the list's sha256 |
| 2 | size leaves routes out | the shown size test |
| 3 | never-finished delete keeps routes | that test and its failing-delete twin |
| 4 | a failed fetch keeps its files | dropped connection on a file; error on a file |
| 5 | empty key still requests | no app key |
| 6 | key sent to any host | another host |
| 7 | no orphan clean-up | both clean-up keep-set tests |
| 8 | status not checked with code | code with the wrong status |
| 9 | file names unchecked | name that is not BRouter's |
| 10 | a running fetch's `.partial` removed | the clean-up test |
| 11 | stored files not checked on read | gone, other size |
| 12 | overlap not newest first | newest wins; newest state |
| 13 | `outside_coverage` not none-here | 404 outside_coverage; fetching again |
| 14 | summary drops the route state | shown state; missing state |
| 15 | no retry on `run_superseded` | retry once; twice |
| 16 | sha header may be absent | without its header |
| 17 | missing squares not recorded | partial coverage |
| 18 | south edge rounded inward | the rectangle test |
| 19 | lookup ignores missing squares | point in a missing square |

| 20 | edges not six decimals | the rectangle test (`'44.930067963'`); the six-decimal test |
| 21 | an `.rd5` written in place | cut off mid-file (`[W125_N45.rd5]` on disk at the drop) |

Reverts 1 to 19 ran on `0c27d4146` (runs 39938672543 to 39938843686, 69 tests each). Reverts 18, 20 and 21 ran on
`883de54a3` (runs 39973091970, 39973104169, 39973114769; 77 tests each, the five classes' count; 0 compile errors);
18 failed the rectangle test again. Each revert lived on its own branch, `offline-area-routes-revert-N`; the forward
branch was never edited by a revert, and its diff against pre-main still holds every change.

**A check that could not fail, caught:** the first version of the part-written-`.rd5` test looked after the fetch,
when the clean-up of a failed fetch had already removed any `.rd5`; it would have passed with the edit reverted. It
now records what is on disk at the moment the connection drops (`883de54a3`).

## Contract notes

- Built to #381 as merged and as the planner relayed it: `handler.ts` lines 1-8 read, `missing` required, the
  header read case-insensitively (the live Worker sends `x-forager-sha256`).
- The fake server is an OkHttp interceptor (no MockWebServer dependency, as `ClientUserAgentTest`). Its answers copy
  the live Worker's as the planner relayed them (403 with only `error`, `outside_coverage` with `squares`, the Boulder
  file names and sizes); the bytes are stand-ins.

## What is unverified

- **Anything against the real Worker.** No request was sent to it. One live check from a debug build on the S22 is
  allowed only after asking the planner; not done.
- **`MapLibreOfflineMapRepository`'s new lines** (the fetch after the tiles, the delete): the class needs MapLibre's
  native store, so no JVM test reaches it; the decisions it hands off are tested.
- **The S22 launch check:** not done; it needs the planner's go.
- **Route bytes in the shown size on screen:** the summary carries them; the row's label was not looked at on a phone.
- **The upload of the debug APK with the key in it:** the CI artifact now carries the key inside the APK, as any
  APK does (#381's report: a deterrent, not a secret).


## Part two: the owner's answers, built (head `b88238a55`)

**The answers, as the planner relayed them:** retry "Automatic, button as backup (Recommended)"; wording "OK as
drafted"; D "Only when a route reaches that edge (Recommended)", so not on the row (the `missing` squares stay stored
for the routing screens); E settled by the same automatic rule.

**What the row says** (`ui/availability/OfflineRegionRouteLine.kt`, pure `offlineRouteLine`):
- routes stored, partial or whole: nothing; the size counts them (A);
- being fetched: "Getting trail routes";
- no routes for the area, or a reason a retry can't fix (no key, key refused, too large, over the phone's limit, bad
  request): "Map only. No trail routes for this area yet.", no button (C, F);
- routes wanted and no signal (the maps' own `LocalNoSignal`): "Map only. Trail routes download next time you're
  online.", no button (E offline);
- routes wanted and an attempt this session failed: "Map only. Trail routes didn't download." with **Try again** (B);
- routes wanted, signal, no attempt yet: nothing, for the moment before the automatic fetch starts.

**The automatic trigger** (`AvailabilityViewModel.onOfflineMapsOpened`, run by `AvailabilityOfflineRoutes`): when
Offline maps opens with signal (the maps' hysteresis state, read at the opening), after the list is read, each listed
downloaded region that is never fetched, damaged, or missing for a retryable reason is fetched once, in turn, then the
list reloads. **Limits:** one attempt per opening, never a loop; nothing with no signal; a region already being
fetched is not fetched again (by a second opening or Try again). A download's own route fetch counts as an attempt, so
a fresh download whose routes failed offers Try again at once. New repository call: `fetchRoutes(id)`, which refuses a
region whose tiles are not on this phone.

**Files beyond part one:** `OfflineRegionsList.kt`, `AvailabilityOfflineMapsUi.kt`, `AvailabilityOfflineMaps.kt`,
`AvailabilityScreenOfflineMaps.kt`, `AvailabilityUiState.kt` (two fields), the new `AvailabilityOfflineRoutes.kt` and
`OfflineRegionRouteLine.kt`, and plumbing of one callback and one signal reader through `AvailabilityViewModel.kt`
(908 lines before), `AvailabilityCompactScaffold.kt`, `JournalTab.kt` (917 before), `RecordsTab.kt`, `MainActivity.kt`
and `AvailabilityViewModelFactory.kt`. The two big files took two lines each, a parameter and its pass-through; no
split was made for that.

**Tests** (failing first by revert, below): `OfflineRouteLineTest` 7, `OfflineRegionRouteRowTest` 5 (composed
through `OfflineRegionsSection`; Try again reached by five real touches across its bounds), and
`AvailabilityViewModelOfflineRoutesTest` 8, through the ViewModel's own entry points (opening Offline maps, Try again,
Download). Full suite on the PR (run 39978043612, every job green): **5,393 tests, 0 failed, 24 skipped**, 20 more than
part one.

**Revert checks 22 to 29** on `b88238a55` (runs 39978983733 to 39979093290; 126 tests each, the filtered classes' count;
0 compile errors):

| # | Edit | Failed |
|---|---|---|
| 22 | the automatic fetch ignores no signal | opening with no signal fetches nothing |
| 23 | Try again before any attempt | no Try again before an attempt has failed |
| 24 | a region fetched twice at once | not fetched twice (`[1, 1, 1]`) |
| 25 | a download's failed routes not noted | a download whose routes failed offers Try again |
| 26 | a failed fetch not marked failed | Try again; once per opening |
| 27 | stored routes get a line | A (pure); stored routes add no line (row) |
| 28 | the no-signal line dropped | E offline (pure and row) |
| 29 | the row ignores the maps' signal | the row's no-signal test |

**Dex report** on run 39978043612's APK: the same five methods; `JournalTab` 300 registers (291 in part one) and
the scaffold lambdas up 2, from the plumbing.

**The S22** (the planner's go): installed DB schema read first, `user_version` 22, the same as this build, so no
downgrade; installed build was `1.0.3844+g99ac3300`. `scripts/s22-launch-check.sh` on run 39978043612's APK: PASS
(`status=verify`, cold launch, alive after 8 s, crash buffer empty). Then **one live route download through the real
entry point**: Journal > Records > Offline maps, with validated Wi-Fi, on the phone's one region (id 2, 13 km near the
owner's area). The automatic fetch asked `area.json` for the six-decimal rectangle, got 200, fetched one file, and
stored `files/offline_routes/2/W125_N45.rd5` (2,279,871 bytes) with `routes.properties`: state stored, run
`usca-20261021T111429Z`, OSM timestamp 2026-10-17T20:20:51Z, no missing squares. The row shows no route line and
"24.5 MB". No `debug.txt`, `stacks.txt` or `storageconfig.txt` anywhere under `files`. The key appears in no log line
(`x-forager` matched 0 lines); it was never printed or stored by me.

**On the phone now:** the PR's debug build (`1.0.3868+g14becf6e`, the PR's merge commit), its data kept (install -r),
the one region with its routes stored. Note: the debug build's OkHttp logging writes each request's URL, so the
region's rectangle is in logcat on debug builds (headers are not logged); release builds do not log it.

**Still unverified:** a failed or no-signal row on the phone (tested on the JVM only); `fetchRoutes` and the download's
route step in `MapLibreOfflineMapRepository` beyond this one live success; the Boulder box (not needed: the owner's own
area was used).

## Part three: Amendment 1, after Sorrel's review (head `089bfabb6`)

**The split first, as its own PR: #384** (`split-availability-viewmodel`, `d13151ca6`, from pre-main `54f4d56c4`).
A pure move: the ViewModel's offline-maps functions, its Settings delegates and the waypoint navigation's start and
stop go verbatim into three files as extension functions on `AvailabilityViewModel`. They remain ViewModel functions
(RECORD -1450), and every caller keeps the name. Six members go from private to internal. Callers outside the package
import the moved names. Counts: `AvailabilityViewModel.kt` 908 to 739, `AvailabilityViewModelOfflineMaps.kt` 113,
`...Settings.kt` 50, `...WaypointNavigation.kt` 49. The 144 non-blank lines removed equal the 144 added (de-indented,
receiver added). Full CI on it: run 39984053598, 5,313 / 0 / 24, the same three numbers as pre-main.

**Into this branch:** #384 is not merged into pre-main yet. So I merged its branch here (`089bfabb6`, a merge, never a
rebase), and this work now lives in the split-out file. pre-main gets merged in once #384 lands there. After the
merge: `AvailabilityViewModel.kt` 751 lines, `AvailabilityViewModelOfflineMaps.kt` 142. `JournalTab.kt` (920) and the
scaffold (1,441) keep their pass-through lines under the few-lines allowance, as the ruling says.

**M1, one writer per region folder.** `RegionRouteStore.beginFetch` marks and tests in one step (`fetching.add`), and
answers `null` when a fetch for that id is already running. `fetchFor` then returns the new state
`RegionRouteData.Fetching` and touches nothing. A listing reads `Fetching` while any fetch runs, the download's own
included. That makes the automatic fetch skip it (`needsFetch` is false) and the row say "Getting trail routes". The
stored state is read only after the fetch has ended.

**M2, a release build refuses to build without the key.** The task is `verifyReleaseHasRoutingAppKey`, wired to
`assembleRelease` and `bundleRelease`, as the signing guard is. CI's release-compile job is **exempt by
construction**: it runs `packageRelease`/`packageReleaseBundle`, which stop short of both tasks, as it already does
for the signing guard. I did not give that job the key, because doing so would put the key in a second job and fail
every fork's pull request, which gets no secrets. **Where the release key comes from:** `forager.routingAppKey` in the
build machine's `~/.gradle/gradle.properties`, or `FORAGER_ROUTING_APP_KEY` in the environment, never the tracked
`gradle.properties`. **The step for the next Play build:** add that line on the machine that builds it before
`./gradlew bundleRelease`; the task logs that a key is set, never the key. Debug builds without a key keep `NO_APP_KEY`,
logged, and the row's line for that state stays C.
Proof on CI (branch `offline-area-routes-m2-proof`, run 39987326904, a workflow change not for merge):
- the check is in `bundleRelease`'s and `assembleRelease`'s task graphs, and not in `packageRelease`'s;
- with no key it refuses with its own message;
- with a stand-in key (not the real one) it passes, and the stand-in's value is not in the log.

**Signal return.** `OfflineRegionsSection` watches the maps' signal state while it is on screen. On each change from
no signal to signal it reports to the ViewModel once, and never at first composition. The ViewModel
(`onOfflineMapsSignalReturned`) checks the signal again and fetches the regions that want routes, once. The
one-writer guard and the in-flight set keep it from double-fetching. The callbacks travel as one
`OfflineRouteActions` (Try again, signal returned), replacing the single Try-again callback through the same files.

**L1:** a stored commit removes every file in the folder its record does not name, and a superseded retry logs every
file it cannot clear. **L2:** each `.rd5` is synced before its rename, and so is the record before the folder's
rename. The directory itself is not synced; Java offers no portable call for that, and `status()` still reads a short
file as `Damaged`. **L4:** during the download's route step the progress shows an indeterminate bar and "Getting trail
routes" (`OfflineMapStatus.Downloading.gettingRoutes`). It is set by a new `onRouteStep` overload of `download` that
defaults to the old call, so no fake changed. **L6:** the stale "not deployed" and "not built yet" comments are fixed.

**Tests:** `RegionRouteFetchOneWriterTest` 3 (Sorrel's M1 path with a real second thread stopped mid-file; refetch
after end; L1). There are 9 more across the line, row and ViewModel classes: `Fetching` on the row and in the line,
signal return once per return and not at first composition, opened offline then signal, the ViewModel's signal-return
fetch with and without signal, a running fetch not refetched on opening, and the download's route-step progress. The
filtered classes went from 126 to 138. **Full suite on the PR** (run 39987282978, every job green): **5,405 tests, 0
failed, 24 skipped**.

**Revert checks 30 to 37** on `089bfabb6` (runs 39987370070 to 39987470292; 138 tests each; 0 compile errors):

| # | Edit | Failed |
|---|---|---|
| 30 | a second fetch takes the folder | the M1 path (the running fetch's `.partial` was gone: a NullPointerException reading it) |
| 31 | a running fetch not listed as fetching | the M1 path, "a listing during the fetch reads it as running" |
| 32 | strays kept on a stored commit | "keeps only the files its record names" (`W130_N45.rd5` left) |
| 33 | signal return not reported | both signal-return row tests |
| 34 | signal return fetches with no signal | "reported while there is no signal fetches nothing" |
| 35 | progress keeps showing tiles | the route-step progress row test |
| 36 | route step not set in state | the ViewModel's route-step progress test |
| 37 | a running fetch not shown on the row | the line and row `Fetching` tests |

**Dex report** on run 39987282978's APK: the same five methods as part two; nothing new. **S22:** schema read
first, `user_version` 22, so no downgrade. Launch check on that APK passed (`1.0.3873+g7bede5bd`, `status=verify`,
cold launch, alive after 8 s, crash buffer empty). The phone was used for the launch check only. It now has that
build, with its data and the one region's stored routes kept.

**Unverified:** the signal-return and `Fetching` rows on a phone (JVM only); #384 on its own on the phone (its
versionCode is below the installed build, and installing it would need an uninstall); the directory fsync.
