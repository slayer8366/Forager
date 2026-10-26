# Re-check of PR #412 after Amendments 1 and 2: what changed since the review

Sorrel Tally (reviewer, Fable 5.1), fresh session, 2026-10-26, written 09:44 UTC. For Rowan Waymark.
Read-only on code; no build on the laptop, no device, nothing pushed but this file (branch `sorrel-412-recheck`).

**Scope:** `git diff 9d0b7f8f9 f497427c0` on branch `waypoint-route-domain` (PR #412): commits `799f1513c`,
`52e320eaf`, `5c090d885`, `f497427c0`, against dispatch `prompts/preserved/2026-10-26-10.md` Amendments 1 and 2 and
RECORD -1665, -1666, -1667 (`records-after-173` at `567fae0c4`, `RECORD.md:28238-28275`). The earlier review is
`prompts/preserved/2026-10-26-sorrel-412-review.md` (`sorrel-412`, `e53f3b3e9`), which reviewed `9d0b7f8f9`; nothing
it covered is re-opened here except where these four commits touch it.

Every claim is marked **read** (the file at the hash named), **computed** (from a run's artifacts or a command), or
**inferred** (reasoned from code, not seen running). Line numbers are at `f497427c0` unless another hash is named.

## 0. Baseline

| Claim | Checked | Result |
|---|---|---|
| PR head `f497427c0`, base pre-main, mergeable | `gh pr view 412` | `f497427c0e6f…`, `MERGEABLE`, `CLEAN`, open (read) |
| The four commits are all that changed since `9d0b7f8f9` | `git log 9d0b7f8f9..origin/waypoint-route-domain` | exactly those four (computed) |
| Nine files changed, 221 insertions, 72 deletions | `git diff --stat` | matched; six main files, three test files (computed) |
| pre-main still `7b388b599` | `git rev-parse origin/pre-main` | matched; the merge base is still `85f05c784` (computed) |
| The eight runs are on the commits named | `gh run view` × 8 | 40293756104 `799f1513c`; 40294678756/81851/86205 on revert-7/8/9 heads `cce3445b2`/`27a583038`/`6a901fd07`; 40297233314 `5c090d885`; 40298242241/45087 on revert-10/11 heads `d4588ac2d`/`e8d3e7ca0`; 40298236888 `f497427c0`, `pull_request`, success (read) |
| Tests unchanged between each fail-first and its build | `git diff -- app/src/test` for `799f1513c..52e320eaf` and `5c090d885..f497427c0` | both empty (computed): the tests seen failing are the tests that passed |
| Fail-first A1 ran over the old code | `git diff 9d0b7f8f9 799f1513c -- app/src/main` | empty (computed) |

No mismatch. One thing I could not confirm as named: the five `waypoint-route-domain-revert-*` branches are gone from
the remote (`git ls-remote` lists none), so the revert commits were read through `gh api repos/…/commits/<sha>` by the
runs' head SHAs. Each is one commit, one file, +1 −1, with the parent shown below (read).

## 1. Prediction (written before any run's XML or log was read; the file is in my scratch, timestamped before §4)

Fail-first A1: 6 failures (leaves-area text, wording pin, `FindWayToWaypointAreasTest` 1-4). R7: 2, carrying
"Download a larger area". R8 (`if (true) return endsApart`): compiles with a warning only; fails `AreasTest` 4 and the
"routes not usable" test on its first assert, both reading `WAYPOINT_IN_ANOTHER_AREA`. R9: 3, `AreasTest` 1-3. Fail-first
A2: 2 (fake and real no-file tests), `WAYPOINT_FAR_FROM_TRAIL` on the END assert. R10 (END producer line only): 1, the
real-engine test; the fake passes. R11: 2. PR run: 5,613 = 5,606 + 5 + 1 + 1, 0 failures, 24 skipped. Scored in §7.

## 2. Question (1): the leaves-area wording

- `WayToWaypoint.kt:61`: `LEAVES_AREA("This route leaves your downloaded area · straight line")` (read), RECORD -1665's
  words.
- The way home's sentence: `WayHome.kt:91` is unchanged, `"This route leaves your downloaded area. Download a larger area
  to route there."` (read); no file under `domain/wayhome/` is in the diff (computed, `--stat`).
- The short form: `NavigationHudText.kt:69`, `LEAVES_AREA_SHORT_TEXT = "Route leaves your downloaded area"` (RECORD
  -1613), untouched (read; not in the diff). PR 3 shows it, as the doc at `WayToWaypoint.kt:57-60` says.
- Pinned by `FindWayToWaypointUseCaseTest.kt:116-124` (the waypoint's text, and the way home's unchanged sentence asserted
  side by side) and the wording pin at `:220-233` (read). R7 fails exactly those two (§4).

**Answer:** done as -1665 states; the way home's sentence and the short form are unchanged.

## 3. Question (2): `routeDataForPoints` checks both ends first

### What the code does (read, `FindRouteDataForPoints.kt:26-53`)

`holdingBoth` = downloaded regions whose route rectangle holds **both** points, newest first (`:27-29`). Of those with
`Stored` routes (`:31`), the newest whose build lacks neither end's square is `Found` (`:32-35`). Otherwise: if nothing
holds both, `endsApart` (`:37`, `:50-53`): either end in any downloaded region → `InDifferentAreas`, else
`NoRegionCovers`. If something holds both and none is usable: `NotUsable` with, in order, `SquareMissing` from the newest
stored holding-both region (`:39-42`), else `NotStored` with the newest holding-both region's state (`:44`). `NotUsable`
no longer names an end (`:64`), and the use case's log lines drop "at $end end" (`FindWayToWaypointUseCase.kt:93-114`).

### The table, traced row by row (the coder's own table went to Rowan, not to me, so this is mine)

W = the walker's end, P = the waypoint's. "Note" is `WayToWaypointNote.text` without its "· straight line" tail.

| # | Regions | Result | Note | Right? | Pinned by |
|---|---|---|---|---|---|
| 1 | A stored holds W only; B stored holds P only | `InDifferentAreas` | The waypoint is in another area | yes | `UseCaseTest:101` |
| 2 | A stored holds W; B holds both, `NotFetched` | `NotUsable(NotStored(B))` | Trail routes not downloaded | yes, fetching B gives a route | `AreasTest` 4 (`:66-76`, with both small regions stored) |
| 3 | A stored holds W; B holds P only, `NotFetched` | `InDifferentAreas` | The waypoint is in another area | **yes: S-412-1 closed** | `AreasTest` 1 (`:46-52`) |
| 3b | A `NotFetched` holds W only; B stored holds P | `InDifferentAreas` | another area | yes | `AreasTest` 2 |
| 4 | no region holds W; B holds P (stored, `Damaged`, `Fetching`) | `InDifferentAreas` | another area | yes, as before (the approved notes have no "you are outside") | `UseCaseTest:111`, `AreasTest` 3 |
| 5 | neither end in any region | `NoRegionCovers` | No trail routes here | yes, -1667 "Confirm" | `UseCaseTest:152` last assert |
| 6 | one region holds both: `NotFetched`, `Missing(NETWORK)` / `Missing(FORBIDDEN)` / `Fetching` / `Damaged` / `NoneHere` / `Stored` lacking an end's square | `NotUsable` | not downloaded ×2 / need an app update / Getting… / couldn't be read / No trail routes here ×2 | yes, the way home's words for the same states (`FindWayHomeUseCase.kt:140-157`) | `UseCaseTest:152-169` |
| 7 | two hold both: newer stored usable, older anything | `Found(newer)` | route | yes | `UseCaseTest:137` |
| 7b | two hold both: older stored usable, newer stored lacking a square | `Found(older)` | route | yes (`firstOrNull` over newest-first, `:32`) | none; inferred |
| 7c | two hold both: one stored lacking a square, the other `NotFetched`, **either order of age** | `NotUsable(SquareMissing)` | No trail routes here | see below | none; inferred |
| 8 | regions holding both all unusable, plus a small stored region holding one end | the holding-both region's state, never "another area" | | yes | `AreasTest` 4, 5 |
| 9 | `isDownloaded == false` regions | ignored in `holdingBoth` and in `endsApart` alike (`:28`, `:51`) | | yes | none; read |

Every row I can produce comes out as -1666 asks: a route-state note only when a region holding both ends is in that
state; "another area" whenever none holds both and either end is covered. **Row 3 is the one S-412-1 named, and it now
reads "another area."**

### The precedence choice (row 7c)

Rowan's phrasing is "a region holding both with stored routes lacking a square beats an **older** region holding both
never fetched." The code is not about age: `storedHere.isNotEmpty()` (`:39`) puts `SquareMissing` ahead of `NotStored`
whichever of the two regions is newer (read). That is the one-point rule's own order, `routeDataForPoint`'s `:38-41`
(`FindRouteDataForPointUseCase.kt`), which the way home ships today, so the two finders agree. Is it right? "No trail
routes here" is a fact about the build the stored region came from; a never-fetched region's routes would come from the
current build, which may or may not have that square. When it does not, "No trail routes here" is the true answer and
"not downloaded" would point at a fetch that changes nothing; when it does, the reverse. Neither finder can know which,
and the stored region's fact is the one the phone actually has. Consistent with the way home and defensible; no test
pins it; **note, not a fix**, and the age wording in whatever the coder wrote should read "whatever its age".

**Answer to (2):** S-412-1 is closed by construction (`:37`: nothing holding both → `endsApart`, which never reads a
route state), every row traces right, and the precedence among regions holding both is the one-point rule's, by state
not by age.

## 4. Question (3): `squareHasNoFile`, declared in the body so `equals` ignores it

### The shape (read, `TrailRouter.kt:61-68`)

`data class EndOutsideData(val end: RouteEnd)` with a body `var squareHasNoFile: Boolean = false; private set` and a
secondary constructor `(end, squareHasNoFile)` that sets it. Kotlin's generated `equals`, `hashCode`, `toString` and
`copy` cover primary-constructor properties only, so the flag is outside all four (language rule, not inferred from a
run). The only mutation is inside the secondary constructor, before the instance is published; there is no public setter.

### Can it be lost silently? Every path, traced

| Path | What happens to the flag | Where |
|---|---|---|
| Producer → `EngineOutcome.Failed(it)` → `TrailRouteResult.Failed(outcome.failure)` / `TrailSnapResult.Failed(outcome.failure)` | the same reference is wrapped; kept | `BRouterTrailRouter.kt:124`, `:80`, `:91` (read) |
| `TrailSnapResult.Failed` → `JoinScan.Stopped(failure)` → `FindWayHomeUseCase.failedOn` | same reference; the way home never reads it | `TrailJoins.kt:91`, `:94`; `FindWayHomeUseCase.kt:177-183` (read) |
| `copy()` | would drop it (generated `copy` takes `end` only) | **no `.copy(` on any `TrailRouteFailure` in main**: the two `.copy(` in these packages are on a track and a part (`FindWayHomeUseCase.kt:118`, `WayHomeKeptPoints.kt:62`) (computed, grep) |
| A mapping that rebuilds an `EndOutsideData` from another | would drop it | none in main (grep: the only constructions are the two producers below and the test fakes) |
| A cache, `Set` or `Map` keyed on failures | two unequal meanings would collapse to one key | none: no `Map`/`Set`/cache of `TrailRoute*`/`TrailSnap*` in main (computed, grep) |
| Serialization, parcelling | would drop it | none: no `@Serializable`, `@Parcelize`, `Parcelable`, `Json`, `Gson` in `domain/routing`, `wayhome`, `wayto`, `routing` (computed, grep) |
| `toString()` | `EndOutsideData(end=END)`, flag invisible | nothing prints a failure's `toString` today: the way home logs `javaClass.simpleName` (`:182`), the waypoint use case appends its own words for the flag (`FindWayToWaypointUseCase.kt:129`), the router logs only `EngineFailed` (`BRouterTrailRouter.kt:139`) (read) |

So **today nothing loses it**: every hop passes the reference, and no copy, rebuild, cache, serializer or `toString`
reader exists. The loss paths are all latent, and all silent: a future `failure.copy(...)`, a `"$failure"` in a log, a
`Set<TrailRouteFailure>`, or an `assertEquals(EndOutsideData(END), x)` that passes against a flagged value (as
`BRouterTrailRouterTest.kt:103-108` now does, by design). Nothing in the compiler or a test would say so.

### Producers (read, grep at `f497427c0`)

- `RoutingDataFolder.kt:34-35`: both ends, `squareHasNoFile = true`. These should carry it, and do.
- `BRouterFailures.kt:21-22`: the engine's own "from/to-position not mapped", unflagged. Correct: the file was there and
  the matcher found no way within 250 m; that is the "far from any trail" case the dispatch keeps. The engine's own
  "datafile … not found" is classified `DataMissing` (`:31`) and cannot reach a user for a square file, because
  `RoutingDataFolder.check` runs first for both ends (`BRouterTrailRouter.kt:124`), as its doc says (`:19-20`).
- Tests: `WayToWaypointFakes.kt:33-34`, `WayHomeFakes.kt:70` build unflagged failures: fakes of the engine's matcher,
  correctly unflagged.
- No third producer. Nothing produces an `EndOutsideData` that should carry the flag and does not, or the reverse.

### Is the way home truly unaffected?

- The scan receives the flagged failure: `BRouterTrailSnapper.nearestTrail` → `firstRoutePoint(point, point|toward)` →
  `runEngine` → `check(routeData, start, end)` (`BRouterTrailSnapper.kt:27-31`, `BRouterTrailRouter.kt:89-90`, `:124`).
  With `SNAP`, a point in a no-file square comes back `EndOutsideData(START, true)`; `TrailJoins.kt:91` tests only
  `failure.end == RouteEnd.END`, so the scan goes on, exactly as it did for `EndOutsideData(START)` (read). With
  `FULL_ROUTE`, `toward` in a no-file square gives `END` → `Stopped` → `NO_ROUTE_FOUND`, as before (read).
- The note: `FindWayHomeUseCase.kt:180` maps every `EndOutsideData` to `NO_ROUTE_FOUND`, by type (read).
- No file under `domain/wayhome/` or its tests is in the diff (computed). In the PR run, `FindWayHomeUseCaseTest` 25,
  `FindWayHomeOnBRouterTest` 2, `WayHomeRoutingTest` 2, `BRouterTrailRouterTest` 14, `BRouterTrailSnapperTest` 6,
  `BRouterFailuresTest` 1 all pass (computed from the XML). `BRouterTrailRouterTest.kt:103-108` is byte-identical and
  passes, which it can only do because `equals` ignores the flag; that was the dispatch's requirement.

**Unaffected, by reading and by its tests.**

### Is a sealed variant or a separate field materially safer at small cost?

Yes to both, and the dispatch's own two constraints pull against each other here, which is why the coder landed where it
did (inferred from the constraints; the coder's reasoning is in its commit message `5c090d885`, read):

- **Amendment 2's own example** is "a distinct failure kind the scan treats exactly as `EndOutsideData` today", i.e. a
  sealed variant. That is the safest shape: three exhaustive `when`s (`FindWayHomeUseCase.kt:178-181`,
  `TrailJoins.kt:90-95`, `FindWayToWaypointUseCase.kt:120-128`) would each need a branch, so a reader that forgot the
  new kind would not compile, and it can never be lost by `copy`, `toString`, equality or a key. Cost: two way-home
  `when`s gain a branch mapping the new kind as they map `EndOutsideData` (code changed, behaviour not), and
  `BRouterTrailRouterTest.kt:106`'s expected value changes to the new kind.
- **A primary-constructor field** `val squareHasNoFile: Boolean = false` is nearly as safe (`copy` keeps it, `toString`
  shows it, equality distinguishes it) with no `when` changes. Cost: `BRouterTrailRouterTest.kt:106`'s expected value
  becomes `EndOutsideData(RouteEnd.END, squareHasNoFile = true)`, one line.
- Both are excluded by Amendment 2's "**`BRouterTrailRouterTest.kt:103-108` keeps passing unchanged**", read literally.
  The body property is the only shape that satisfies that line, and the coder chose it for that reason (`5c090d885`'s
  message). The same line excludes the amendment's own example.

My reading: the chosen shape is correct today and is the one shape where a future loss is silent. The one-line test
change is the whole cost of either safer shape. **Should-fix, for Rowan to place:** it reverses a dispatch constraint, so
it is not the coder's to take and not mine; it can ride PR 3 or a tidy, with the owner told that one test's expected
value would name the mark. If "unchanged" meant "still passes" rather than "byte-identical", the constructor-field shape
is the smallest honest one.

## 5. Question (4): the reverts and the fail-first runs bite as claimed

### Compiled, and ran (computed from each run's failed-job log and XML)

For all seven failing runs: no `e: file://` line, no `compile…Kotlin FAILED`; `Task :app:testDebugUnitTest FAILED`
present with a `N tests completed, M failed` line, so the build compiled and the test task ran. R8's `if (true) return`
leaves unreachable code and R11's `false ->` is a constant branch; both are warnings in Kotlin and no
`allWarningsAsErrors`/`-Werror` is set anywhere in the Gradle files (computed, grep), consistent with the tests having
run.

### The runs (computed from JUnit XML)

| Run | Commit, parent | Tests | Failed | Which, and the message | Specific? |
|---|---|---|---|---|---|
| 40293756104 fail-first A1 | `799f1513c` (tests over `9d0b7f8f9`'s code) | 19 (14 + 5) | 6 | leaves-area text (`[ · straight line]` vs `[. Download a larger area…]`); wording pin; `AreasTest` 1, 2, 3 (`ROUTES_NOT_DOWNLOADED` / `ROUTES_UNREADABLE` where `WAYPOINT_IN_ANOTHER_AREA` expected); `AreasTest` 4 (`WAYPOINT_IN_ANOTHER_AREA` where `ROUTES_NOT_DOWNLOADED` expected) | yes: the four area cases are exactly the old per-end order, in both directions |
| 40294678756 R7 | `cce3445b2` ← `52e320eaf`: `LEAVES_AREA` back to the way home's sentence | 19 | 2 | leaves-area text; wording pin, both showing "Download a larger area to route there." | yes |
| 40294681851 R8 | `27a583038` ← `52e320eaf`: `if (true) return endsApart` | 19 | 2 | "routes not usable at an end say why" (`WAYPOINT_IN_ANOTHER_AREA` for `NotFetched`); `AreasTest` 4, the same | yes: a holding-both region's state never said |
| 40294686205 R9 | `6a901fd07` ← `52e320eaf`: `endsApart` says a covered end's `NotStored` first | 19 | 3 | `AreasTest` 1, 2, 3: route-state notes where "another area" is expected | yes: S-412-1's own symptom, reproduced |
| 40297233314 fail-first A2 | `5c090d885` (flag declared, no producer, no reader; `git diff 5c090d885 f497427c0 -- app/src/main` is the producer and reader only, read) | 38 (15 + 14 + 5 + 4) | 2 | `UseCaseTest` no-file test; `OnBRouterTest` no-file test; both `WAYPOINT_FAR_FROM_TRAIL` | yes |
| 40298242241 R10 | `d4588ac2d` ← `f497427c0`: `RoutingDataFolder.kt:35` (the **END** line) unflagged | 38 | 1 | `OnBRouterTest` no-file test, `WAYPOINT_FAR_FROM_TRAIL` on its first assert (waypoint in W80_N40) | yes |
| 40298245087 R11 | `e8d3e7ca0` ← `f497427c0`: the use case ignores the flag | 38 | 2 | `UseCaseTest` and `OnBRouterTest` no-file tests, `WAYPOINT_FAR_FROM_TRAIL` | yes |
| 40298236888 PR run | `f497427c0` | **5,613** | **0** (0 errors, 24 skipped) | the 24 skips: `AvailabilityScreenMapIconStackTest` 19, `…TripPlanningFlowTest` 2, `…WaypointFlowTest` 1, `…OfflineCacheTest` 1, `GenerateFungiIndexDbAsset` 1, the standing allowlist | 5,606 + 5 + 1 + 1 = 5,613 |

Each revert is its own one-commit branch off the build commit, so the forward change was never touched by a restore
(the CLAUDE.md runner hazard cannot arise in this shape). Each failure is one its edit produces and none belongs to a
different edit. The A2 fail-first ran `BRouterTrailRouterTest` (14, all passing) over the stub, which is the first
evidence that `:103-108` holds with the flag declared; the PR run is the second, with the producer setting it.

**Two gaps, neither blocking:**
- **R10 reverts only the END producer line** (`RoutingDataFolder.kt:35`); the START line (`:34`) has no revert. The
  forward real-engine test does exercise START (`OnBRouterTest.kt:101`, `find(noFileSquare, ACROSS_FROM)`), and R11
  removes the reader for both, so the START line is covered by a passing test and by R11's reader revert, but not by a
  producer revert of its own. Note.
- -1667 cites PR run 40294571611 at 5,611. I did not download it; 5,606 + 5 = 5,611 is consistent with the two runs I did
  count (inferred).

## 6. Question (5): clean merge with current pre-main

`git merge-tree --write-tree origin/pre-main f497427c0` → tree `f04008ef1…`, exit 0, no conflict output (computed).
Merge base `85f05c784`; pre-main since then is `9bead6f4c` (`MushroomLogViewModelWiringTest.kt` only) and the merge
`7b388b599`; neither touches a file in this PR (computed). GitHub: `MERGEABLE`, `CLEAN` (read). **Clean.**

## 7. The prediction, scored

All eight lines matched the runs exactly: 6 / 2 / 2 / 3 / 2 / 1 / 2 and 5,613 / 0 / 24, with the messages named. The one
refinement made before reading (R8 at two tests, not "several", once I saw the not-usable cases share one test method)
is in the prediction file. Nothing in the results was unpredicted; the one thing I had not written down was that R8 and
R11's edits would leave compiler warnings, which I checked for after the fact and found harmless (no `-Werror`).

## 8. Verdict

**Blocking:** none.

**Should-fix** (for Rowan to place; nothing shows until PR 3):
- **S-412R-1** The no-file mark's shape (§4). Correct today with every path traced, but the one shape whose future loss
  is silent (`copy`, `toString`, equality, a `Set`). Either safer shape costs one expected value in
  `BRouterTrailRouterTest.kt:106`, which Amendment 2 forbade changing; the amendment's own example shape (a distinct
  kind) is excluded by the same line. The owner's call whether "unchanged" meant byte-identical.

**Notes:**
- **N-412R-1** Precedence among regions holding both ends is by state, not age (§3, row 7c): `SquareMissing` from a
  stored region beats `NotStored` of a never-fetched one whether that one is older or newer. The one-point rule's order;
  the age wording in the coder's account should say so. Untested; inferred.
- **N-412R-2** R10 covers the END producer line only (§5); the START line rests on the forward test and R11.
- **N-412R-3** Row 7b (older usable beats newer stored-lacking-a-square) is right by `firstOrNull` over newest-first and
  pinned by nothing.
- **N-412R-4** From the earlier review: S-412-1 closed (this PR); S-412-2 superseded by -1667 into "No trail routes
  here" (this PR); N-412-7 done (`FindRouteDataForPointUseCase.kt:18-19`, read); N-412-1 (`RouteHomeLayers.kt:149`)
  left for PR 3 as the amendment says; N-412-3 resolved by -1665.
- **N-412R-5** The real-engine no-file test (`OnBRouterTest.kt:94-103`) constructs its case with a 100 km region whose
  `missingSquares` is empty and a folder holding only W75_N40: the Worker-contract gap the earlier review's §6 could
  not close from the app. The test proves the reading of the failure, not that the case is reachable in production;
  that reachability question is unchanged from the earlier review.

**Mergeable:** yes, as it stands, into pre-main. The three owner rulings are built as recorded (-1665 wording, -1666
both-ends-first, -1667 "No trail routes here" with the way home untouched); every new rule has a test shown failing
without it, on a build that compiled; the way home's files are not in the diff and its tests pass on the shared engine;
the merge is clean against `7b388b599`.

**One-line answers:**
- (1) "This route leaves your downloaded area · straight line"; the way home's sentence and the short form unchanged.
- (2) Both ends first by construction; S-412-1 closed; every row right; precedence among holding-both regions is the
  one-point rule's, by state not age.
- (3) The flag cannot be lost on any path that exists today; the only other producer (the engine's "not mapped") is
  rightly unflagged; the way home is unaffected by reading and by tests; a constructor field or a sealed variant is
  materially safer for one test line, which the dispatch forbade: Rowan's to re-rule.
- (4) All seven reverts and fail-firsts compiled, ran, and failed on their own edit's symptom; 5,613 / 0 / 24 on the PR
  run; R10 covers one of the two producer lines.
- (5) Clean.

## 9. Disclosure

**Read:** every Forager file cited, at `f497427c0` (or `9d0b7f8f9` / `52e320eaf` / `5c090d885` where named); the four
commit messages; the five revert patches via the GitHub API; RECORD -1665 to -1667; Amendments 1 and 2; the PR body.
**Computed:** the diff and its stat, the test-parity diffs, the eight runs' metadata, seven failed-job logs (compile
check), eight runs' JUnit XML (per-class counts and failure messages; 757 suite files for the PR run), the merge-tree,
`wc -l` (nothing above 238 lines), and the greps for `copy(`, caches, serialization and the leaves-area strings.
**Inferred:** rows 7b, 7c and 9 of the table; the Kotlin data-class rule as applied (language fact, not run here); the
reasoning on which shape is safer; 40294571611's 5,611.

**Could not determine:** the coder's own table and report (they went to Rowan); whether the no-file case is reachable in
production (unchanged from the earlier review's §6: a race or a Worker-contract breach). I did not download run
40294571611.

**Premises that were wrong or need correcting:** Rowan's phrasing of the precedence choice as beating an *older* region;
the code is age-independent (§3). Amendment 2's example shape and its "keeps passing unchanged" line exclude each other
(§4); the coder resolved it toward the test line, which is the literal reading.

**Decided beyond scope, or left for the owner:** S-412R-1's placing. No revert check of my own was run (no builds on the
laptop); I read all seven CI runs' logs and XML in its place, as in the earlier review.

**What I did not do:** no device, no local Gradle, no reading of the Worker, no reading of the other reviewer's report if
one exists.
