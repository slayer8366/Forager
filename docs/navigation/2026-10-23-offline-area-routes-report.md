# Offline area downloads store their trail routes: storage and state, stopped at the step path (dispatch 2026-10-23-03)

**Coder:** Hazel (Opus 5.5). **Planner:** Rowan. **Base:** origin/pre-main `99ac33006` (it had #380), verified at the
start; pre-main `54f4d56c4` (#381 and #382 merged) merged in later, never rebased. **Branch:** `offline-area-routes`,
PR #383 (draft). **Status:** storage and state built and tested; **stopped** at the dispatch's built-in stop, the
user-facing step path, proposed below. No UI text is built.

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
