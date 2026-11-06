# Pre-main release sort (dispatch 2026-11-01-04)

Juniper Sounding, for Rowan Waymark. Read only: no code, no merges, no Gradle, no phone, no Pi. Started by an earlier Juniper session (method 6299b737e, notes b2e21fe25), stopped by the owner's "Stop everything", and finished by this session on the owner's "Finish what the last planner was doing" (RECORD -1939). The method and predictions committed at 6299b737e stand; they are scored in section 7.

Every claim is marked **[read]** (a file, page or record, named), **[computed]** (a command whose output is described) or **[inferred]**. The earlier notes' claims are kept only where this session re-checked them, and they carry this session's mark.

## 1. Bases: both moved, and the sort is against the new tips

- **The dispatch's bases:** origin/main 0a819b3d6, origin/pre-main 13bcd0ebf. **After `git fetch` on 2026-11-01:** origin/main **8d0eefdbc**, origin/pre-main **2bc8913f4**. Both are fast-forwards of the dispatch's bases [computed: `git merge-base --is-ancestor`].
  - **pre-main gained one merge: #462**, the app's switch to Street v4, merged 2026-11-02T05:45:03Z [read: `gh pr view 462`]. Eight files, +120/−33: `StreetStyle.kt`, `BasemapStyles.kt`, `MapStyleReload.kt`, `StreetNightTables.kt`, three tests, and a `map-style` CI job [computed: `git diff --stat 13bcd0ebf origin/pre-main`].
  - **main gained one merge: #463**, the Worker guard workflows, merged 05:45:09Z: two workflow files, nothing else [computed: `git diff --stat 0a819b3d6 origin/main`].
- **This report sorts against pre-main 2bc8913f4 and main 8d0eefdbc.** Code citations are at 2bc8913f4 unless another commit is named.
- **CI:**
  - pre-main 2bc8913f4: run 41395827457, green [read: `gh run view`].
  - main 0a819b3d6: run 41390918408 **failed**, on one test, `MushroomLogViewModelWiringTest` at `:80`; 5,450 tests, 1 failed [read: the failed log]. That is known bug 2, the parked flake [read: `docs/process/known-bugs.md` row 2]. The earlier notes did not mention it.
  - main 8d0eefdbc: run 41395837232 was still running when this was written [read].
- **The Play build is 3500** (1.0.3500+g19aa1058), from main 19aa10589, schema 22 [read: RECORD -1367; -1380 corrects "out" to "in Google's review"; later entries treat 3500 as production, e.g. the "production 3500 = main 19aa1058" diagnosis]. No later release is recorded [grep of RECORD after -1367 for Play releases]. release.yml has run once, a failed dry run on 2026-10-23 [read: `gh run list --workflow release.yml`].
- **pre-main does not contain 19aa10589.** main's promotions are merge commits that pre-main never took back [computed: `--is-ancestor` exits 1; merge-base of main and pre-main is bfbaca47c]. The content is the same: main's own commits are the promotion merges, Worker-folder-only releases and #463's two workflow files [computed: `git log origin/pre-main..origin/main`]. The Worker folder is equal on main and pre-main, and a trial merge of pre-main into main is clean [computed: `git diff --quiet`, `git merge-tree`].

### The premises about #461, #462 and #463

- **#461 merged to main at 0a819b3d6: right** [read: `git log --first-parent`]. The live Worker serves `/style/street-v4.json`: HTTP 200, 97,277 bytes [computed: one request this session]. Sorrel's #462 review also found it JSON-equal to pre-main's `map-style/forager-street-v4.json` [read].
- **#462 and #463 "open": wrong.** Both merged at 05:45Z, four minutes before RECORD -1939 (05:49:24Z) called them open [read: `gh pr view`, RECORD -1939]. The merging account is slayer8366, which the owner's and the agents' sessions both use, so who merged them can't be told from GitHub [inferred].
- **Sorrel's reviews:**
  - #462 (`docs/reviews/2026-11-01-sorrel-app-street-v4.md`, sorrel-app-street-v4 9577c3255): no blocking finding [read].
    - **S-1, should fix after the merge:** the night pass's call site is unguarded. With `MapStyleReload` reverted, 78 tests stayed green (run 41393871654).
    - **N-1:** hill shading's night and its hiding under Topo are device-only.
    - **N-2:** relief tiles answer 404 until uploaded. Inferred from MapLibre's behaviour: the region still completes, but has no relief until it is downloaded again.
  - #463 (`docs/reviews/2026-11-01-sorrel-worker-guards-to-main.md`, sorrel-worker-guards-to-main 016483214): no blocking finding, no should-fix [read].

### What the merges mean for the release

The map side owns these items. This report notes them and directs none of them.
- **pre-main now loads Street v4.** `STREET_STYLE_URL` is `/style/street-v4.json` (`StreetStyle.kt:23`), and new downloads are v4 (`offlineDownloadStyleUrl()`, `StreetStyle.kt:65`) [read]. The earlier notes' "Street v4 is dormant" is no longer true.
- **Relief answers 404 today.**
  - The v4 style's `dem` source is `/relief/{z}/{x}/{y}.png`, Terrarium, z5 to z11 [computed: the sources of `map-style/forager-street-v4.json` at pre-main].
  - One tile, `/relief/8/40/91.png`, answered **404** [computed: one request, named user agent].
  - Aspen expects the relief build "Monday after tonight's map upload … about 22 GB, estimated" [read: prompts/aspen/2026-11-01-09].
  - So any build of pre-main today draws v4 with no hill shading. Whether a v4 download completes with relief at 404 is not established; see Street v4 in section 2.
- **#463 closes the Worker-redeploy risk on main.** Any push to main redeploys the Worker from main's folder (prompts/aspen/2026-11-01-05) [read]. That folder now equals pre-main's [computed].

## 2. The sort, feature by feature

### What a Play user would get

Promoting pre-main gives a Play user everything since 3500: main's three unreleased promotions (#301, #357, #393: PRs #269 to #388), plus the 52 merges into pre-main since bfbaca47c (#389 to #462) [computed: `git log --first-parent`].

- **main's unreleased part** carries Forager Trail Map itself (#258, #377, #379, #380), trail routes stored with downloads (#383), the routing engine (#382), tile retry (#386) and the privacy-policy changes (#387) [read: PR titles]. The owner: "Current play release doesn't have trail maps, so this will all be very new" [read: prompts/aspen/2026-11-01-07].
  - These went through Elder's promotion audits: `prompts/preserved/2026-10-23-05-promotion-audit-report.md`, `2026-10-24-01-promotion-recheck-report.md`, `-02`, `-08` [read: RECORD and the known-bugs rows citing them].
  - **I did not re-sort them.** The dispatch's range starts at main's last promoted state. They have never been in a Play build, so the upgrade over 3500 is still untested (section 3).
- **The 52 new merges** are sorted below. Of them, #403, #405, #410, #411, #414, #441, #452, #453, #455, #457 and #458 are tests, CI, process documents or the Worker [read: titles]. The Worker items are already live through main [read: aspen -06, computed: folder equality]. A user meets none of them.

### Way home along the trails (plan step 5): #389, #394 to #396, #398, #404, #406, #407; trail heights #447

- **Sort: after small work: one walk on a pre-main build.** It is the headline navigation feature, and it states a time and a route, so it is the serious class (qa-philosophy section 4) [read]. Its figures are ruled provisional, and "a walk tests it" (the plan's carried constraints) [read].
- **Evidence:**
  - **S22 checks with simulated positions:**
    - `prompts/preserved/2026-10-25-hazel-s22-way-home-2-report.md` (s22-way-home-2-report 3c7b20237), build 1.0.3948+gadfcf75f, a timing branch at the 50 m reach, before the 60 m fix [read]. The swap at Return and both re-routes showed no empty-map frame. **F2:** a second stray re-routes again, which went to the owner. **F1:** the `MapForecastCells.kt:58` crash, fixed later by #397 [read: the PR title].
    - `prompts/preserved/2026-10-25-10-s22-check-report.md` (way-home-fresh-start-s22-report 7ac84c9dd) [located, not opened].
  - **One real walk:** `docs/audits/2026-10-28-s26-walk-read.md` (s26-walk-2026-10-28 b3877b0c6), on the owner's S26, build 1.0.4153+g86c99060, GitHub's test merge of #432 into pre-main e2969abe [read]. At 5:40 the line was the trail route: 425.7 m plus 6 m of joints, every point a node of the mapped trail, matching "0.3 mi by trail" on screen. The walker was on the trail throughout, so the "follow your track back" parts were zero length and **untested on real ground**. The app's own log was gone [read].
  - **The walk-back figure:** the reconcile (`docs/navigation/2026-10-26-walk-back-reconcile.md`, walk-back-reconcile ee943f549) [read].
    - On an out-and-back, the figure with the climb allowance read 9:33 against 9:37 walked to first arrival.
    - On a loop it reads long, growing as the loop closes: 57:37 at 349 m from the car against 5:54.
    - Ruled at -1699: keep it, and Return is the cover [read].
  - **#447's trail heights:**
    - The only phone record is the install in `docs/audits/2026-10-30-s22-tools-447-440.md` (tools/s22-447-440 05b19342a, build 4224) [read]. Nobody read a time figure on the phone.
    - The route data on that phone was cut 2026-10-27, "most likely the version without heights" [read: same file]. If so, the trail part falls back to the walker's track and logs it (`FindWayHomeUseCase.kt:201-211`) [read].
- **Reachable: yes.** The Return control (`MainActivity.kt:673`) calls `TrackRecordingViewModel.startReturn`. That calls `wayHomeWatch?.find(...)` at `TrackRecordingViewModel.kt:744`. The watch is built at `AppContainer.kt:459` and fed by the recording service at `TrackRecordingService.kt:240`, `:248`; Stop ends it at `:391` [read].
- **To hide it, if the owner chose to:**
  - The smallest cut is the one `find` call at `TrackRecordingViewModel.kt:744`. Return then shows the walked track, as on 3500 [inferred].
  - What depends on it: the off-track alert's "on course" line (#399), the walk-back time "along the route shown" (-1498 answer 4; `SundownWatch`), and -1699's ruling that Return covers the loop case [read].
  - Hiding it would leave the loop case uncovered, so it is not recommended [inferred].
- **What a user would have to think about:**
  - On a loop walk, "start back by" creeps earlier on the way home, and the leave-by alert can fire within sight of the car (-1699) [read]. That reads as a broken timer. Return is the remedy, and nothing on screen says so [inferred].
  - Off trail, the quiet off-trail dim can only show between about 40 and 50 m, and never at 10 m accuracy or worse (-1726). It is "judged on a walk" [read]. No record of that walk or the log read (-1729) was found [grep of RECORD after -1729].

### Off the trail and back (plan step 7, in part): #399, #400, #402

- **Sort: ships with the way home; ready as far as it goes.**
  - #399: the off-track alert counts the way home's trail line as on course.
  - #400: "Re-center", the owner's renaming (-1498 answer 5).
  - #402: the dotted line back along a stray counts as the track.
  - [read: titles; plan answers]
- **Not built:** the wrong-turn alert, slope and aspect. Nothing on pre-main draws or announces them [read: the earlier notes' grep, re-run in effect by this session's traces; no caller found]. They need no hiding.
- **Evidence:** CI and Sorrel's reviews only. No phone check names #399 to #402 [grep of RECORD for those numbers alongside "S22"].
- **Reachable:** through the way home above [inferred from the PR titles; the alert's call sites were not traced line by line].
- **What a user would have to think about:** nothing new found.

### The way to a waypoint along trails and its quiet off-trail state (plan step 6): #409, #412, #413, #425

- **Sort: after small work: the same walk.** It is navigation (the serious class), and the 40 to 50 m band was ruled to be judged on a walk (-1726, -1729) [read].
- **Evidence:**
  - S22 `prompts/preserved/2026-10-26-hazel-413-s22-check-report.md` (waypoint-route-s22-report bb016dd37), on #413's build [read: known bug 21's row, which cites it; the file itself was located, not opened].
  - Sorrel's #425 review (`docs/reviews/2026-10-27-sorrel-pr425.md`): no blocking finding [read: RECORD -1729].
  - The 10-28 walk's 5:42 line was "most likely a waypoint navigation, unverified" [read: s26 walk read].
- **Reachable: yes.** `AvailabilityViewModelFactory.kt:69` passes `container.findWayToWaypoint` (built at `AppContainer.kt:453`) into the ViewModel, whose `null` default "is today's straight line" (`AvailabilityViewModel.kt:184-185`) [read].
- **To hide it:** pass `null` at `AvailabilityViewModelFactory.kt:69`, and the straight dashed line returns [inferred from the comment at `:184`]. Nothing else depends on it [inferred]. The drop-off stack (#433 to #439) builds on it; see section 4.
- **What a user would have to think about:**
  - **Known bug 21:** the waypoint pop-up stays open while navigation runs. Back ends navigation, and a second Back closes it.
  - **-1763's unexplained Back:** while navigating from a bubble an earlier Back had left open, two Backs did nothing visible; a second run behaved normally (`docs/audits/2026-10-28-s22-session.md`) [read: RECORD -1763].
  - Both read as "Back is broken". Neither is serious by the table [read: row 21].

### Official trails, the reference layer (plan step 8): #416, #422, #423, #424

- **Sort: ready.**
- **Evidence:** S22 `docs/audits/2026-10-27-s22-check-v3-8d.md` (s22-check-v3-8d a5b7318f8), builds 4077 (a4264e26a) and 4094 (6064fef86), schema 22 [read]. The v3 steps 1, 2, 4, 6, 7, 8, 9 and 12 passed, as did the 8d card steps [read: RECORD -1736's summary of the same file; step 8's PASS row read in the file]. The archive was uploaded and read back by sha256 on the owner's go (-1700) [read].
- **Reachable:**
  - Its switch is in Tools > Map Layers (`MapLayersToolsSection.kt:128`, enabled by `controls.referenceTrailsShown`).
  - Tapping a trail opens its bubble: `MapListeners.kt:129` → `referenceTrailBubble` (`MapBubbles.kt:275`), with the line "Official record. Routes follow the trail map, not this layer." (`ReferenceTrailRecord.kt:65`) [read].
- **What a user would have to think about:** on Topo or OSM the switch is grey (`MapLayersToolsSection.kt:105`) [read]. Whether the grey state explains itself was not read [could not determine].

### Street v4 on Trail Map: #448, #460, #462

v4 adds creeks under the water, orchards, nurseries and vineyards, hill shading and sprite v6.
- **Sort: after small work.** Upload the relief tiles before the build ships, or show on a phone that a download completes with relief at 404, and give pre-main's own v4 one look on a phone.
- **Evidence:**
  - The owner approved hill shading on the S22: "It's good … Let's keep this" (aspen -09; trial build `hillshade-trial-s22`, run 41332802392) [read].
  - The combined builds (`docs/audits/2026-10-30-s22-combined-build.md`, `2026-11-01-s22-card-beside-bar.md` on tools/s22-combined) load v4 from `raw.githubusercontent.com/…/street-v4/…`, not the Worker (`StreetStyle.kt:24` at 441e1433e, 3709b6a15 and aef6f01ac) [read]. They carry #440 and #449, which pre-main does not.
  - **No phone has run pre-main's own v4**, which loads from the Worker [inferred: #462 merged at 05:45Z today, and no check is recorded after it].
- **The relief question:**
  - The repo's own comment says a 404 fails a download: "Everything else, 404 included, fails it as before" (`MapLibreOfflineMapRepository.kt:443-452`, `downloadErrorKind`) [read].
  - #462's body and Sorrel's N-2 both infer that MapLibre records a tile 404 as no content and carries on [read]. That rests on MapLibre's Android HTTP source treating a 404 on a tile as "no content", which I believe but did not read [inferred, unverified].
  - The one phone download on a v4 combined build, the "TEST area" at 08:41 PST on 11-01, came **before** the relief source entered that style: `/relief/` was added in 00aaf7753 at 12:40 PST [computed: `git log -S'/relief/'` on origin/street-v4]. So it is no evidence either way.
  - The live map is safe either way: `TileRetryInterceptor` holds only `.mvt` requests (`TileRetryInterceptor.kt:182`) and treats only 5xx as a failure (`:102`), so a 404 relief `.png` is let through with no retry or "no signal" [read].
- **Reachable:** every Trail Map user. `STREET_STYLE_URL` (`StreetStyle.kt:23`) is the default map type's style, and new downloads use it (`:65`) [read].
- **What else changes for a user:**
  - Every region downloaded on v3 shows "Older map style. Download again to update." (`StreetStyle.kt:92-104`, `OfflineRegionsList.kt:239`) [read].
  - Every region a 3500 user has was downloaded against `offline.json` (`OfflineStyle.kt:18` at 19aa10589), so those show it too. They keep drawing as before (`StreetStyle.kt:84-85`) [read].
- **To hide it:** put `STREET_STYLE_URL` back to v3 (one line, `StreetStyle.kt:23`) [read].
  - v3 stays served and keeps its night tables (Sorrel's #462 review, section 3) [read].
  - Any v4 region downloaded meanwhile would still load v4 offline (`offlineRenderStyleUrl`, `StreetStyle.kt:84-85`) [read].
  - Not recommended while the relief upload is a day away [inferred].
- **What a user would have to think about:**
  - A download made before relief is on R2 holds no relief tiles and never shows shading until it is downloaded again, with nothing telling the user (Sorrel N-2) [inferred].
  - If MapLibre does report the 404 as an error, the download fails outright with an error, the worst case on this list. It is unverified either way.

### Trail Map polish, crash fixes and the recording line: #397, #401, #408, #426, #427, #429

- **#426**: buildings solid, with an outline. **#427**: the fallback retries when the connection returns. **#429**: patterns redraw after a style reload. **#397**, **#401**: style-change crashes. **#408**: the recording line reaches the dot [read: titles].
- **Sort: ready.**
- **Evidence:**
  - `docs/audits/2026-10-28-s22-session.md` (s22-session-2026-10-28 307cb7ac0), builds 4120 and 4123: #429 night on and off 3 of 3; #427 steps 1 to 4 pass [read: RECORD -1763].
  - Sorrel on #426 found no blocking finding (`docs/reviews/2026-10-27-sorrel-pr426.md`) [read: RECORD].
  - #408: `prompts/preserved/2026-10-26-05-s22-check-report.md`, build 1.0.3994, line end never 10 m or more from the dot over 105 shots [read: RECORD].
- **Reachable:** every map user [inferred from their nature; no single caller].
- **What a user would have to think about:** nothing new found.

### Find photos off the map: #428

- **Sort: ready.**
- **Evidence:** the S22 session above, 9 of 9 [read: RECORD -1763].
- **Reachable:** `GetCartographyEntryMapDataUseCase.kt:111` hides a find's gallery through `FindLinkedPhotos.isHidden` [read].
- **What a user would have to think about:** nothing found.

### Crowded icons: zoom in then fan, or a list: #430, #431

- **Sort: ready.**
- **Evidence:** the S22 session above. #431 passed 14 of 15; step 12 was not run (no cold GPS). #430 passed steps 1 to 8 and 10 to 14, with 9 and 15 observed [read: RECORD -1763].
- **Reachable:**
  - The setting is under Settings > Map > "Crowded icons". `MainActivity.kt:510` routes it to `onCrowdedIconsModeChanged`.
  - The map reads the mode at each tap (`AvailabilityCompactMapUi.kt:305`) [read].

### Map Layers into Tools: #432

- **Sort: ready.**
- **Evidence:** `docs/audits/2026-10-28-s22-pr432.md`, three rounds; round 3 on 1.0.4153+g86c99060 [read: RECORD].
- **Reachable:** `MapLayersToolsSection.kt` [read].

### Live journal-entry counts and empty lines with a next step: #434

- **Sort: ready.**
- **Evidence:** `docs/audits/2026-10-28-s22-pr434.md`, steps on 46cc439d7 [read: RECORD titles; the file was located, not opened].
- **Reachable:** through the count sources wired at `AppContainer.kt` and three ViewModel factories (#434's file list) [read: `gh pr diff --name-only`; call lines not traced].

### A visible trash button on offline-map rows; Directions only where Navigate can't reach: #443, #444

- **Sort: ready.**
- **Evidence:** the S22 passed both **on pre-main 3c5e63468** itself [read: RECORD -1834's title; the file `docs/audits/2026-10-30-s22-pr443-pr444-check.md` was located, not opened]. This is the last check on a plain pre-main build found [grep].
- **Reachable:**
  - Trash: `OfflineRegionRowWithDelete` at `OfflineRegionsList.kt:149` and `RecordsLogbookList.kt:233`.
  - Directions: the bubble's action at `MapFeatureBubble.kt:95` [read].

### The Heights credits: #451

- **Sort: ready.**
- **Evidence:** S22 phone checks part 1, "the Heights credits (placement, link, readability)" passed (`docs/audits/2026-10-31-s22-phone-checks.md`, s22-phone-checks 2de1cced5) [read: RECORD -1901]. That was on a combined build [read: -1899, -1901].
- **Reachable:** `DataSourceCredits.kt:71-78` [read].
- **What a user would have to think about:**
  - The credits name height sources that today feed only route files and hill shading (#447, #448) [inferred].
  - Until relief is uploaded, the shading they partly credit is not drawn. That is minor under A7 (what Forager says in public stays true) [inferred].

### Battery: the compass stops while the screen is off (#450); WordSwap stops animating on numbers (#454)

- **Sort: ready, with A8's battery item stated** (qa-philosophy A8: a change states its battery cost).
- **Evidence:**
  - **450-1 passed:** 16 redraw lines in 3 minutes with the screen off, against about 940 a minute before.
  - **450-2 failed:** 29 to 39% of a core with the screen off while recording, against a target of about 4.7%.
  - [read: RECORD -1904, `docs/audits/2026-10-31-s22-phone-checks.md` part 2, c90507e71]
  - The cause was WordSwap's stuck transition, which is also in 3500 (-1906) [read].
  - With #454, a shortened trial measured 1.7% of a core over 83 s, against about 41% (`docs/audits/2026-11-01-s22-build-449-454.md`, build 4324) [read: RECORD -1918]. That was "not like for like": no GPS fix, 83 s rather than 3 minutes, a debug build [read].
- **Reachable:** `WordSwap(` at `AvailabilityMapControlsUi.kt:266`, `:280` [read]; the compass on the Maps tab [inferred from #450's title].
- **What a user would have to think about:** nothing visible. The release build's cost is unmeasured [read: -1906].

### Route plotting's storage and domain: #445 (schema 23), #446

- **Sort: hidden already; no work needed to hide it. Needs the upgrade check (A2).**
- **Reachable: no user path.**
  - `PlotRouteUseCase` is declared at `PlotRouteUseCase.kt:40` and referenced nowhere else in `app/src/main` [computed: git grep].
  - The only trip save takes a non-null date and no route: `SavePlannedTripUseCase.kt:30-36`, reached from `AvailabilityPlannedTrips.kt:46-48` [read].
  - So on pre-main the new tables `planned_trip_route_stops` and `planned_trip_route_legs` are written by nothing a user can reach [inferred from that trace; restore from a backup was not traced].
- **Evidence:** the 22 to 23 migration ran on the S22 under `adb install -r` of debug build 4224: `user_version` 23, the regions, tracks, points and waypoints rows hashed identical to the backup, and the two tables present with 0 rows (`docs/audits/2026-10-30-s22-tools-447-440.md`) [read]. That is a debug build over a debug build, not the signed Play build over 3500 that A2 requires (qa-philosophy section 3, "Which build") [read].
- **What a user would have to think about:** nothing visible.

## 3. What the promotion and the release need, besides features

- **A2, the upgrade check, applies.** Schema 22 to 23 (`ForagerDatabase.kt:214`) [read]. It installs the owner's signed Play build over 3500, holding test records, photos and settings (qa-philosophy section 3) [read]. No such check was found in RECORD [grep, not exhaustive]. It can only use a build from main, since release.yml is `workflow_dispatch` on main (`release.yml:19-20`) and uploads a draft to the internal track (`:214-224`) [read].
- **The owner's "Main does automatic Play build releases" is wrong** (aspen -07) [read]. release.yml is run by hand, and its upload is a draft that someone rolls out (`release.yml:5-6`, `:224`) [read].
- **No UX auditor's report exists for this range.** No promotion audit was added after 2026-10-24 [computed: `git log --all --since=2026-10-24 --diff-filter=A` for promotion or auditor files]. The last covers #391 [read: known-bugs row 20]. RECORD -1939: "No promotion until the sort and the auditor's report exist" [read].
- **Phone evidence on plain pre-main stops at 3c5e63468** (#443, #444; -1834) [read]. Everything since (#445 to #462) was seen only on combined builds that also carry #440 and #449 and load v4 from GitHub [read: combined build reports]. An S22 launch check on pre-main itself (A1) is the cheap first step [inferred].
- **The live site's privacy policy is behind the app.**
  - www.zynergy-labs.com/privacy/ reads "Last updated: 21 October 2026". It names the Worker for map tiles, but not trail routes, the app key, or route requests carrying an area's four edges [read, through the fetch tool's summary, not verbatim from the HTML].
  - The repo's policy, last updated 2026-10-23, does describe those requests (`docs/legal/privacy-policy.md`, the Worker row and the "Downloading an offline region" paragraph) [read].
  - Route requests are on main already (#381, #383), so the live page is behind main too [inferred from the PR titles].
  - A7 needs the site updated before the release. RECORD -1612 put the site and the Data safety form on hold, and no later record lifts it [grep].
- **The Data safety form: could not determine.** It is in Play Console, which isn't readable from here. The repo's policy says the form declares location "collected and shared" [read]. Whether route requests change the form, or are already covered by that declaration, is not determined [inferred: probably covered, since they are location; not legal advice].
- **The map side's needs** (Aspen's to schedule; noted, not directed):
  - relief on R2 before the build reaches phones (Sorrel N-2);
  - Sorrel's S-1 follow-up test for the night pass's call site;
  - nothing in the Worker folder: it is equal on main and pre-main [computed].
- **Battery (A8):** #450 and #454's costs, as above.

## 4. Open PRs that features depend on

- **#449, route plotting step 3:** open, head 985196468, 0 commits behind pre-main, trial merge clean [computed].
  - Its last run was cancelled (a5b20012c), and fb878627d was the last green [read: `gh run list`].
  - Amendment 17 is in progress with Hazel. -1938: it is not merged when green; it waits for the owner's word [read].
  - **Nothing on pre-main is unfinished without it.** Without it, #445's tables and #446's domain have no user path and are invisible (section 2) [read].
  - Its two open findings from -1936: Re-center lands on the search bar in landscape, and the width easing (Save missed in 3 tries) [read]. Amendment 17 addresses both [read: -1937].
- **#440, altitude above sea level:** open, 112 behind pre-main, trial merge clean [computed]. The verdict waits on a walk with the screen on (-1867) [read]. See known bugs, section 5.
- **The drop-off stack, #433 and #435 to #439:** all open, base pre-main, each 123 commits behind, mergeable state UNKNOWN on GitHub [computed, read].
  - Trial merges of #438's head (f282b1cd5) and #439's head (bc2cdd141) into pre-main are clean [computed: `git merge-tree`].
  - No Sorrel review branch and no RECORD ruling on them were found; the earlier notes' grep was not repeated in full.
  - #438's three notes need the owner's word [the earlier notes; not re-read].
  - **Nothing on pre-main is broken by their absence:** the way to a waypoint ends at the nearest trail and a straight leg, as it is today (the s26 walk read's description of `FindWayToWaypointUseCase`) [read]. A pin at a house stays reached by a straight leg from the trail [inferred].

## 5. Known bugs to weigh for this promotion

The rule (qa-philosophy section 4): a bug may ship if it is listed, was already in the live app, and is not serious. A new serious bug fails [read]. By the table, no row from 14 to 21 is marked serious [read]. The ones I would bring to the owner:

1. **Altitude shown about 24 m low (raw ellipsoid height), unlisted.**
   - The strip shows raw ellipsoid height (`AvailabilityUiState.kt:415`), a mean 23.8 m below the trail data's ground, about ±10 m (-1791) [read].
   - It is in 3500: "Alt 1352 ft" formatting at `NavigationHud.kt:837` at 19aa10589 [read], so it is "Live" [inferred].
   - It has no row in known-bugs.md [read]. A6 requires every bug a promotion ships with to be listed, so it holds the promotion **until it is listed**.
   - Whether a wrong height is a "wrong position" (the serious class) is the owner's call. The fix is #440, waiting on a walk [read].
2. **Row 14: a download south of 15.92°N says it succeeded and holds empty tiles.**
   - It is new, and the table marks it not serious [read].
   - It is a failed result presented as success, which CLAUDE.md's errors rule forbids [read]. It is also a "Don't make me think" dead end.
   - My reading: worth a hold, or Elder's picker line before release [inferred, advice only].
3. **Row 18: "Paused · no signal" on a server error.**
   - The ruled one-line fix ("Paused · continues by itself", -1594) is still not in: `DOWNLOAD_PAUSED` at `OfflineMapsSideBySide.kt:193` [read].
   - It is new, not serious, and misleads about signal. It is a cheap fix, ruled "after the promotion" [read], so it does not hold the promotion.
4. **Row 21 and -1763's unexplained Back.** New, not serious, cause undetermined [read]. Bring it to the owner; it does not hold under the rule.
5. **Row 2's flake** reddens CI runs, including main 0a819b3d6 today [read]. A red run on it is not a new bug (the row's own purpose) [read]. It does not hold.

**The relief 404 is not a bug today, but it can become one.** If the build reaches phones before relief is on R2, every download made in the meantime silently lacks shading (inferred, Sorrel N-2). In the unverified worst case, MapLibre reports the 404 and those downloads fail.

## 6. Disclosure

**Confirmed vs inferred.**
- *Read:* every file and line cited at pre-main 2bc8913f4; RECORD -1367, -1380, -1612, -1628, -1699, -1726, -1729, -1763, -1791, -1867, -1901, -1904, -1906, -1918, -1923 to -1939; known-bugs.md; qa-philosophy sections 2 to 4; the trail navigation plan update; Aspen's notes 2026-11-01-05 to -09; Sorrel's #462 and #463 reviews; the reconcile; the S26 walk read; the v3-8d, #447, combined-build and card-beside-bar audits; release.yml; both privacy policies.
- *Computed:* the bases, CI states, PR and merge lists, Worker-folder equality, trial merges, behind counts, one Worker style request (200) and one relief tile request (404), and the relief source's commit time.
- *Inferred, and marked where it appears:* sorts as recommendations; hide paths; that MapLibre treats a 404 tile as no content; that altitude in 3500 is the same raw value; anything about what a user would think.

**Could not determine.**
- Who merged #462 and #463 (one shared account).
- Whether a v4 download completes with relief at 404.
- The Data safety form's state.
- Whether the walks the 40 to 50 m band waits for, and the -1729 log read, happened off the record.
- Whether the grey official-trails switch explains itself.
- main 8d0eefdbc's CI result (still running).

**Opened only by name, through RECORD, not read in full:** the way-home fresh-start report, the #413 S22 report, the #434 and #443/#444 audits, Elder's promotion audits, and the drop-off PRs' bodies.

**Premises that were wrong.**
- "#462 and #463 are open": both merged at 05:45Z, before -1939 said so.
- The bases 0a819b3d6 and 13bcd0ebf: both moved.
- The earlier notes' "Street v4 is dormant": true at 13bcd0ebf, false at 2bc8913f4.
- "Main does automatic Play build releases" (the owner, via aspen -07).
- The researcher profile is not on origin/records-after-173 as the dispatch's reader message said. It was read from origin/main and pre-main.
- The trail navigation plan is not on records-after-173 either. It was read from pre-main.

**Decided beyond scope.**
- Checking one relief tile and the v4 style on the live Worker: two requests.
- Reading the live site's policy.
- Calling the altitude finding a procedural hold under A6.
- Giving my reading on row 14.
- Not re-sorting main's already-promoted range.

## 7. The committed predictions, scored

1. "Most trail navigation steps have a phone check on a combined tools build, not on a build of pre-main itself." **Held in part.** The way home and the waypoint route were checked on their own PR or timing builds with simulated positions. The later features (#445 to #462) were seen only on combined builds.
2. "At least one feature reachable on pre-main depends on an unmerged PR (route plotting, #449)." **Did not hold.** Route plotting is not reachable on pre-main at all, so nothing reachable depends on #449.
3. "The Street v4 app switch (#462) is not on pre-main." **Did not hold.** It merged at 05:45Z, after the prediction. The map side's remaining need is the relief upload, not the switch.

## 8. The closing lists

**What could ship today** (subject to the gate: CI, the auditor's report, the owner's approval, and the A2 upgrade check):
- official trails;
- buildings;
- the pattern, fallback and crash fixes;
- the recording line;
- find photos off the map;
- crowded icons;
- Map Layers in Tools;
- live counts;
- the trash button and Directions;
- the Heights credits;
- the compass and WordSwap battery fixes.

**What could ship after small work:**
- **Street v4:** relief uploaded first, or one phone download proving a 404 is harmless, plus one look on a pre-main build.
- **The way home, step 7's pieces, and the waypoint route along trails:** one walk on a pre-main build that leaves the trail, to judge the 40 to 50 m band (-1726) and see the track-back parts on real ground.
- **Before the release, whatever the features:**
  - an A1 launch check on pre-main itself;
  - the A2 upgrade check, because of schema 23;
  - the live privacy policy brought up to date;
  - the Data safety form confirmed.

**What must be hidden: nothing.** The unfinished work on pre-main (route plotting's storage and domain, #445, #446) has no user path. The unbuilt pieces (wrong-turn alert, slope, aspect, own topo) are not in the code.

**Known bugs that should hold a promotion:**
- Altitude about 24 m low: it holds until it is listed (A6), and the owner rules whether it is serious.
- Row 14 (false success on empty downloads): my reading only, worth a hold or a picker line.
- None of the other rows hold under the written rule.
