# Review of PR #384 (the AvailabilityViewModel split) and PR #383's Amendment 1 changes

Reviewer: Sorrel, on Claude Fable 5.1. Asked by Rowan (planner). Written 2026-10-23, about 13:15 PDT (20:15 UTC).
Coder: Hazel (Claude Opus). Review only: no code changed, nothing merged, nothing posted, nothing deployed, no phone.

**Heads reviewed.** PR #384, branch `split-availability-viewmodel`, **`d13151ca6`**, one commit from pre-main
`54f4d56c4`. PR #383, branch `offline-area-routes`: code head **`089bfabb6`** (the merge of #384's branch into the
routes branch), report head `4d9e9570e` (`[skip ci]`, the report only: `git show --stat` is one file, +76). The
Amendment 1 code is two commits, `dfb91f910` and `e294cb178`, on top of the head I reviewed before (`2c3566977`,
`docs/audits/2026-10-23-review-pr383.md` at `201f587ab`), then the merge.

What I ran: `git diff` of every range named below; a Python comparison of my own (not Hazel's script) of the moved
code, in two forms; `gh run view` and the jobs API for eight CI runs, with `gh api .../jobs/<id>/logs` for five job
logs; `gh run download` of three `unit-test-report` artifacts into three empty directories and a `-I` Python sum
and case-by-case comparison over them; `git fetch` of `refs/pull/383/merge`, the M2 proof branch and four revert
branches. No Gradle build, no request to the Worker.

Sources read: dispatch `prompts/preserved/2026-10-23-03.md` Amendment 1 and RECORD -1450 and -1490
(`origin/records-after-173` at `a38109942`); CLAUDE.md and `.claude/skills/modular-code/SKILL.md` on pre-main;
Hazel's report part three (`docs/navigation/2026-10-23-offline-area-routes-report.md` at `4d9e9570e`); the three
new ViewModel files and the ViewModel diff in full; every non-test file in the Amendment 1 diff in full and every
test file's diff in full; `app/build.gradle.kts` (the key, the new guard, the signing guard, `buildTypes`),
`.github/workflows/ci.yml` (`release-compile`), README's CI section, `MapLibreOfflineMapRepository.kt:110-200` and
`:358-388`, `RegionRouteStore.kt:35-80`.

## Verdicts

**PR #384: mergeable into pre-main at `d13151ca6`.** It is a pure move. Every moved function's doc comment and
body is identical to the original, in order, after removing four spaces of indent and adding the receiver; no
function went missing and none was added; the only other edits are six `private` to `internal` and imports at the
four callers outside the package. CI on it runs the identical 5,313 cases with identical outcomes to pre-main
`54f4d56c4`. The -1450 decisions are unchanged in body and still reached by the same name on the ViewModel; what
changed is where they live (an extension file) and what enforced their position (`private`, now convention), and
that is the planner's to accept, with one Low on it below.

**PR #383: mergeable into pre-main at `089bfabb6`, after #384,** with #383's report head `4d9e9570e` carrying no
code. M1, M2, the signal-return fetch, L1, L2, L4 and L6 are each built as the amendment asked, each with a test that
a one-line revert fails on CI, and the green run is 5,405 / 0 / 0 / 24 with every one of the 92 cases added since
pre-main accounted for by class. The release-key guidance is correct and, for a Play build made the way this
project makes them (on one machine, with `signing.properties`), complete. Three Lows and two nits; no Medium, no
High.

## Findings

Severity: High (blocks merge), Medium (merge, settle before the next step depends on it), Low (note), nit.

| # | Severity | Where | One line |
|---|---|---|---|
| A1 | Low | `AvailabilityViewModel.kt:155,166,196,239,254,271` (at `d13151ca6`); `:163, :252` more at `089bfabb6` | The move trades `private` for `internal` on `_uiState`, `returnLeg` and the collaborators (six in #384, eight after the merge). Nothing in `main/` or `test/` reaches them from outside the four ViewModel files today (`git grep`, both trees), so the -1450 rule holds, but it now holds by convention: any file in the module can call `vm.offlineMaps.beginDownload(...)` past the tile-budget check or write `vm._uiState` directly. A planner's ruling to record, not a defect. |
| A2 | nit | `AvailabilityViewModel.kt` imports of `estimateServedOfflineTileCount`, `estimateOfflineTileCount`, `UnitSystem`, `DistanceUnit`, `AppThemeMode` (at `d13151ca6`) | Five imports whose only users moved out; each has zero uses left in the file. Harmless; lint is green. Leaving them keeps the move's diff minimal, which is a fair choice for a pure move. |
| B1 | Low | `RegionRouteStore.kt:59-62` (`commit`), `RegionRouteFolders.kt:62-66` | L1 as built: a stray that will not delete throws on the first one, so a *stored* fetch whose every file arrived and verified is turned into `Missing(STORAGE)` and nothing is recorded; the next opening fetches it all again. The direction is safe (no stray ever sits beside a stored record) and the failure is logged once by `fetchFor`, but the amendment's "log every delete failure" is met only on the superseded path (`RouteAreaFetch.kt:44`); on commit it is "log the first and give up". A choice to note, not a defect. |
| B2 | Low | `MapLibreOfflineMapRepository.kt:361-388` with `AvailabilityOfflineMaps.kt:192, :220` | L4's "Getting trail routes" is cleared by any `onProgress` that lands after `onRouteStep()`: `onProgress` rebuilds `Downloading(downloaded, total)` with `gettingRoutes` at its default. The observer is never removed and the region never set inactive after `isComplete`, so a late `onStatusChanged` from MapLibre would flip the bar back to "N / N tiles" for the rest of the route step. Whether MapLibre emits one after completion I did not verify; the S22 is the evidence, and Hazel lists the route-step moment on a phone as unverified. Cosmetic if it happens. |
| B3 | Low | `app/build.gradle.kts:503-516` (`verifyReleaseHasRoutingAppKey`) | The guard checks presence, not validity: a wrong key builds, ships, and every region shows F ("Map only. No trail routes for this area yet.", the Worker's `forbidden`). That is the owner's F by design and the planner's contract did not ask for more; said so the next Play build's check includes one download on the release build. |
| B4 | nit | `.github/workflows/ci.yml:588-591` | The `release-compile` tripwire asserts the signing guard did not run but not the new key guard. Not needed for safety: if the key guard ever ran in that job it would fail the job itself (no key there), so a rewiring cannot pass silently. Named so the asymmetry is known. |

## 1. PR #384, the pure move

**Method.** Two comparisons, both my own, over `git diff -U0 54f4d56c4 d13151ca6` of the ViewModel and the three
new files at `d13151ca6`:

1. *Multiset.* The 144 non-blank lines removed from the ViewModel (the six visibility lines excluded), with the
   `fun ` prefix given the receiver and all leading whitespace stripped, against the 144 non-blank lines of the three
   new files below their package, imports and header comment, the same way: identical under `LC_ALL=C` (`sort |
   uniq -c` on both, `diff` empty). (A first pass under the default locale showed spurious differences on
   punctuation-only lines, `}` and `*/`; that is collation, and it is why the C locale is stated.)
2. *Ordered, per function.* Each of the 23 functions found in the new files, taken as its doc comment plus its body
   from `/**` to the closing brace, de-indented by four, with `fun AvailabilityViewModel.` read as `fun `, against
   the same span in the base ViewModel: **23 of 23 identical, byte for byte.** The 23 names removed from the
   ViewModel equal the 23 names in the new files; none remains in the ViewModel; nothing was added to it.

**The rest of the diff** (`git diff --stat 54f4d56c4 d13151ca6`, eight files, +245/−175): six `private` to
`internal` (`returnLeg`, `_uiState`, `settingsPrefs`, `offlineMaps`, `offlineRegionDeletes`,
`waypointNavigationStore`); 21 imports in `MainActivity.kt`; three, one and two imports in
`JournalPendingDeleteTest`, `TrackRecordingSundownTest`, `TrackRecordingSwipeAwayTest`. No other line changed.
Callers inside `ui.availability` need no import, and CI compiling is the proof every caller resolved. Hazel's "144
removed equal 144 added" matches my count.

**Counts.** `AvailabilityViewModel.kt` 908 → 739; `AvailabilityViewModelOfflineMaps.kt` 113;
`AvailabilityViewModelSettings.kt` 50; `AvailabilityViewModelWaypointNavigation.kt` 49. All under the skill's
limits (new files under about 400; the big file under 800).

**RECORD -1450.** Its clarification (`-1450`, Observed, third bullet): "every condition and every call that starts
or stops a loop stays in the ViewModel function where it was ... at the same position, synchronously, on
viewModelScope. The collaborator may hold the literal `scope().launch` ... behind `begin()`". The decision functions
here are `onDownloadOfflineMaps` and `onDownloadAgain` (validate, tile budget, then `offlineMaps.beginDownload`),
`onNavigateToWaypoint` and `onStopWaypointNavigation` (`returnLeg.pause()` / `resume()`). Each is byte-identical,
each still runs synchronously on the caller's thread, each still calls the collaborator's `begin...`, and each is
still `viewModel.onX(...)` to every caller. The collaborators decide nothing new. What -1450 did not foresee is a
ViewModel function in a second file: by its letter ("in the ViewModel function where it was") this holds, since the
function is the same function; by its file it does not. The cost is A1: `private` was what made the rule
self-enforcing. I recommend accepting it, since the alternative (keeping the decision functions in the class) keeps
the file over 800 for exactly the code -1490 wants out of it.

**CI.** Run 39984053598 (`workflow_dispatch`, `d13151ca6`): five jobs, all success. Its `unit-test-report`,
summed by my script over 712 `testsuite` files: **5,313 tests, 0 failures, 0 errors, 24 skipped**. Pre-main
`54f4d56c4`'s push run 39939798014, same script, 712 files: **5,313 / 0 / 0 / 24**. Case by case: the same 5,313
`(class, name)` pairs with the same outcome in both. That is the strongest "no behaviour change" the suite can give;
it is also, as CLAUDE.md says of such checks, a check that passes identically before and after by design, so the
move comparison above is the evidence and the suite is the floor.

## 2. PR #383, Amendment 1

**The merge** (`089bfabb6`, parents `e294cb178` and `d13151ca6`). Against its first parent it touches only #384's
eight files; against `d13151ca6` it is #383's own 43 files. In the resolution, the three routes functions
(`onOfflineMapsOpened` as rewritten for routes, `onOfflineMapsSignalReturned`, `onTryAgainRoutes`) moved into
`AvailabilityViewModelOfflineMaps.kt` with their bodies as they were in `e294cb178`'s ViewModel, and `noSignal` and
`offlineRoutes` became `internal` for it. After the merge: `AvailabilityViewModel.kt` 751, the offline-maps file
142. Hazel merged #384's branch, not pre-main, because #384 was not in pre-main yet; disclosed in part three. The
tree is the same as the dispatch's sequence gives once #384 lands first, so: **merge #384, then #383.** If #383 went
first it would carry `d13151ca6` into pre-main itself, which loses nothing in content and only the "own PR" record.

### M1, one writer per region

`RegionRouteStore.fetching` is `ConcurrentHashMap.newKeySet()` (`:35`), so `fetching.add(regionId)` at `:45` is
the mark-and-test in one step across `Dispatchers.IO` threads (`LocalRegionRouteFolders` runs on `io =
Dispatchers.IO`, `RegionRouteFolders.kt:39-42`). A second `beginFetch` returns `null` before touching the
filesystem; `fetchFor` turns that into `RegionRouteData.Fetching` and logs (`RegionRouteFolders.kt:52-55`).
`status()` answers `Fetching` while the id is marked (`:79`), so the download's own fetch, which goes straight
through the repository and which the ViewModel's `offlineRouteFetchingIds` cannot see, is now "in flight until its
routes are in" to every listing; `needsFetch()` is false for it, so `regionsNeedingRoutes()` skips it and the row
says "Getting trail routes" (`OfflineRegionRouteLine.kt:28`). `e294cb178` is the fix that reads the stored state
only after `endFetch` in the `finally` (`:75-76`), without which `fetchFor` would have returned `Fetching` for its
own completed fetch. The second fetch, answered `Fetching`, is not marked failed by the ViewModel (not
`needsFetch`), its id leaves `offlineRouteFetchingIds`, and the reload reads the running fetch's state.

The test is the path I traced: `RegionRouteFetchOneWriterTest` runs the first fetch on a real second thread, parks
it inside its second file's request with a latch, takes the `.partial` listing, runs the second fetch, lists, and
releases; it asserts `Fetching` twice, the `.partial` set unchanged (`{W125_N45.rd5}`), the first fetch `Stored` at
8,000 bytes, three requests (one list, two files: the second fetch sent nothing) and one "already being fetched" log
line. Revert 30 (`fetching.add(regionId)` with the test dropped) fails exactly that case. The ViewModel test "a region
whose fetch is already running ... is not fetched again on opening" lists one region as `Fetching` and sees only the
other fetched.

### M2, the release guard and the key

`verifyReleaseHasRoutingAppKey` (`app/build.gradle.kts:503-516`): `hasKey` is read at configuration from the same
`routingAppKey` the `BuildConfig` field uses (`:125-127`, Gradle property `forager.routingAppKey`, else environment
`FORAGER_ROUTING_APP_KEY`, else empty); `doLast` errors with a message that names both homes and says why. Wired
with `tasks.matching { name == "assembleRelease" || name == "bundleRelease" }.configureEach { dependsOn }`
(`:518-519`), the same shape as the signing guard (`:487-488`). There are two build types and no product flavors
(`buildTypes { debug, release }`, `:302-312`; no `productFlavors` in the script), so those two task names are the
only shippable ones. `release-compile` runs `packageRelease packageReleaseBundle` (`ci.yml:577`), neither of which
depends on `assembleRelease` or `bundleRelease` (the dependency runs the other way), so the job is exempt by
construction, as the comment says, and its tripwire that the signing guard did not run (`:588-591`) is the same
reasoning already in force. No configuration cache is enabled (`gradle.properties` has no such flag), so the
configuration-time read is fresh on every build.

**Proof run 39987326904** (branch `offline-area-routes-m2-proof` at `ec0a1c8c3`, one commit on `089bfabb6`, the
only change `ci.yml` +20/−31, "not for merge"): the `release-compile` step replaced by three dry runs and two real
runs. Its job log (120529811519, read via the API) shows the three result lines once each as output, after the
echoed script: "Wiring: in bundleRelease and assembleRelease, not in packageRelease or packageReleaseBundle.", "No
key: refused with the key check's own message.", "With a key: passes, value not printed."; the run is success. The
stand-in value is grepped for and absent. That is the guard shown red and green with its own message, which is what
the amendment asked.

**The key guidance** (`app/build.gradle.kts:116-124` and `:503-516`; report part three): `forager.routingAppKey=<key>`
in the build machine's `~/.gradle/gradle.properties`, or `FORAGER_ROUTING_APP_KEY` exported, never the tracked
`gradle.properties`; then `./gradlew bundleRelease`. **Correct:** Gradle reads `~/.gradle/gradle.properties` for
`providers.gradleProperty`, the property name matches the script, and the tracked `gradle.properties` has no such key
(`git show 089bfabb6:gradle.properties`). **Complete for this project's Play builds:** there is no release workflow
on CI (`.github/workflows/` holds `ci.yml` alone; README: `release-compile` "stops short of `assembleRelease` and
`bundleRelease`"), so a Play build is made on a machine that holds `signing.properties`, and the key goes on that
same machine beside it. Two things it does not say, neither wrong: `-Pforager.routingAppKey=...` on the command line
and `ORG_GRADLE_PROJECT_forager.routingAppKey` in the environment also work; and the guard does not check the key is
the right one (B3). No `docs/process` release recipe exists to update (`git ls-tree` of `docs/process`, no file
named for release, Play or promotion), so the KDoc and the report are where the step lives; the planner may want it
in the promotion ruling (-1086) too.

### The signal-return fetch

`OfflineRegionsSection` (`OfflineRegionsList.kt:95-102`): `sawNoSignal` is a `remember`ed flag; `LaunchedEffect(noSignal)`
sets it on no-signal and, on signal with the flag set, clears it and calls `routeActions.onSignalReturned()` once.
First composition with signal: `noSignal` false, flag false, nothing. First composition with no signal: flag set,
nothing called. Each later no-signal → signal edge: one call. Signal → signal (no change) does not restart the effect.
The fetch changes nothing the effect keys on, so no loop. The section is composed from one place
(`AvailabilityOfflineMapsUi.kt:284`), so one instance is on screen. The `remember` resets when the section leaves
composition, which is the "opened" case `onOfflineMapsOpened` already covers. The ViewModel's
`onOfflineMapsSignalReturned` (`AvailabilityViewModelOfflineMaps.kt:71-74`) re-checks `noSignal()` and fetches
`regionsNeedingRoutes()`; the list was loaded at the opening regardless of signal (`onOfflineMapsOpened` lists first,
`:57-64`). Double fetch is barred twice: `offlineRouteFetchingIds` in the ViewModel and `beginFetch` in the store.
"Once per no-signal → signal transition, and never on first display": as built.

Tests: `OfflineRegionRouteRowTest` drives a real `MutableStateFlow` through `LocalNoSignal` as `MainActivity` provides
it: opened with signal → 0; true then false → 1; false again → still 1; true then false → 2; and opened with no
signal, first return → 1. Revert 33 (the `onSignalReturned()` call removed) fails both. `AvailabilityViewModelOfflineRoutesTest`
covers the ViewModel's two branches; revert 34 (a signal return reported with no signal) fails the no-signal case.

### L1, L2, L4, L6

- **L1** `RegionRouteStore.commit` (`:59-62`): keeps the names the `Stored` record lists and deletes everything else,
  so a superseded run's whole `.rd5` cannot sit beside a new record; a non-stored record keeps nothing, as before.
  `RouteAreaFetch.kt:44` logs each file it cannot clear before asking again. Test "a stored commit keeps only the
  files its record names"; revert 32 fails it. B1 is the shape of the failure path.
- **L2** `RouteAreaClient.kt:97-102` syncs the temp file's descriptor before its rename to `.rd5`;
  `RegionRouteStore.writeSynced` (`:157-162`) syncs the record before the folder rename. The directory is not synced,
  disclosed by Hazel; a lost directory entry reads as `NotFetched` or `Damaged`, both re-fetched, so the safe
  direction holds. No test, and none asked.
- **L4** `OfflineMapRepository.download` gains an `onRouteStep` overload with a default body that calls the old one
  (`OfflineMapRepository.kt:47-58`), so fakes compile unchanged; MapLibre's old overload delegates to the new with
  `{}` and the new one calls `onRouteStep()` after the row upsert and before `routes.fetchFor` (`:112-122`, `:180`).
  `AvailabilityOfflineMaps.showGettingTrailRoutes` sets `Downloading.gettingRoutes` (`:246-251`);
  `OfflineDownloadStatusContent` shows an indeterminate bar with "Getting trail routes" when it is set
  (`OfflineMapsSideBySide.kt:152-155`). Tests at both ends (the ViewModel reads the status during the route step;
  the composable shows the text and not "120 / 120 tiles"); reverts 35 and 36. B2 is the one way the text can go
  early.
- **L6** the three comments I named are rewritten (`RouteAreaClient.kt:27-29`, `:172-174`; `RegionRouteData.kt:15-16`;
  `RegionRouteFetchTest.kt:31`), each now citing the deployment (-1572) or the step-path file.

### CI

| Run | Branch, commit (parent) | Edit | Compiled | Ran | Failed |
|---|---|---|---|---|---|
| 39987370070 | revert-30 `3abfd366b` (`089bfabb6`) | `fetching.add(regionId)`, result dropped | 0 errors | 138 | 1: `RegionRouteFetchOneWriterTest > a second fetch for a region being fetched touches nothing, and the first one stores its routes` |
| 39987396273 | revert-32 `289f2e261` (`089bfabb6`) | strays kept on a stored commit | 0 errors | 138 | 1: `RegionRouteFetchOneWriterTest > a stored commit keeps only the files its record names` |
| 39987406591 | revert-33 `389725405` (`089bfabb6`) | `routeActions.onSignalReturned()` removed | 0 errors | 138 | 2: `OfflineRegionRouteRowTest > opened with no signal, the first return is told`; `... > signal coming back while the list is on screen is told once per return, and never at the first composition` |
| 39987458436 | revert-36 `ed206ff19` (`089bfabb6`) | `status.copy(gettingRoutes = true)` → `status` | 0 errors | 138 | 1: `AvailabilityViewModelOfflineRoutesTest > while a download fetches its routes, its progress says Getting trail routes` |

Each branch is one file, one line, on `089bfabb6`; each log has `BUILD FAILED` once and no `e: file://`; each
failure is one its edit reaches and no other edit in the table could produce. The other four reverts (31, 34, 35,
37; runs 39987384055, 39987420316, 39987436027, 39987470292) are all `failure` on `workflow_dispatch` in the same
minute, and Hazel's table is the record for which case each failed; I did not open their logs.

**Green run 39987282978** (`pull_request`, `089bfabb6`): five jobs success. `unit-test-report`, 720 files:
**5,405 tests, 0 failures, 0 errors, 24 skipped.** Against pre-main's 5,313: 92 added, 0 removed, no shared case
changing outcome. By class: `RegionRouteFetchTest` 37, `AvailabilityViewModelOfflineRoutesTest` 12 (8 + 4),
`OfflineRegionRouteRowTest` 9 (5 + 4), `FindRouteDataForPointUseCaseTest` 8, `RegionRouteStoreTest` 8,
`OfflineRouteLineTest` 8 (7 + 1), `OfflineRegionReconciliationRoutesTest` 7, `RegionRouteFetchOneWriterTest` 3:
80 from parts one and two, 12 from Amendment 1, which is the count the test diff predicts.

**The S22 build string.** Hazel's launch check names `1.0.3873+g7bede5bd`, a hash on no branch. My reading: a
`pull_request` run checks out GitHub's `refs/pull/383/merge`, whose hash is not the head's. That ref today is
`b134678de` with parents `54f4d56c4` and `4d9e9570e`, differing from `089bfabb6` by the 76-line report alone, and
`54f4d56c4` is an ancestor of `089bfabb6` (`git merge-base --is-ancestor`), so the then-merge of `089bfabb6` had
`089bfabb6`'s tree exactly. The `7bede5bd` object itself is gone (the ref was rewritten by the report push), so this
is inferred from the current ref, not read from that commit.

## 3. What is unverified

- **Anything on a phone.** The S22 launch check, the dex report, and the "Getting trail routes" moment on a real
  download are Hazel's; the signal-return row on a phone is unverified by anyone (Hazel says so).
- **B2's premise**, that MapLibre can deliver a status callback after `isComplete`: read from the observer never
  being cleared, not observed.
- **Reverts 31, 34, 35, 37**: conclusions read, logs not opened.
- **The M2 proof's dry runs** show the task graph; `bundleRelease` itself was not run through to the guard there
  (it cannot be, without the signing identity), and the guard was run by name. The wiring plus the by-name red and
  green cover the claim; a real `bundleRelease` with and without the key on the build machine is the remaining
  step, and it is the next Play build.
- **Gradle's own behaviour** for `providers.gradleProperty` reading `~/.gradle/gradle.properties`: stated from
  Gradle's documented precedence, not run here.
