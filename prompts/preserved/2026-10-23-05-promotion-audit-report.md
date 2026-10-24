# Elder's audit: promotion of pre-main 32d381e45 into main 381515c61

**Auditor:** Elder, Forager's UX auditor, a fresh session on Claude Fable 5.1 (`claude-fable-5-1`). The coders ran on Opus. **Planner:** Rowan. **Brief:** `prompts/preserved/2026-10-23-05-promotion-audit.md` (records-after-173 at 67652739d when read). **Written:** 2026-10-23, about 21:40 UTC (14:40 PDT).

Read-only: no code changed, nothing merged, nothing deployed, no phone touched, no Gradle. What I ran: `git` reads of origin/main, origin/pre-main, origin/records-after-173 and origin/review-pr376-pr377; `gh pr view` for #376 to #385; `gh run view` and the jobs API for the pre-main push runs; `date -u`. Every file:line below is at `origin/pre-main` 32d381e45 unless it says otherwise. "Read" means I read it; "relayed" means the record or a report says it and I did not see it; "inferred" is marked.

Yardstick: `docs/process/qa-philosophy.md` section 3, with the verdict definitions in section 4; `docs/process/planner-profile.md`, "Branches and the road to main"; CLAUDE.md's UX defaults.

**Range:** origin/main 381515c61 to origin/pre-main 32d381e45. 142 commits, 341 files, +129,581/−1,164 (counted; the brief's figures match). Merge base f0ca368a1. `app/src/main`: 89 files, +6,949/−961. Room schema: `version = 22` on both (`ForagerDatabase.kt:206` on each branch); `app/schemas/` identical between the two; so the upgrade check does not apply (-1133).

---

## Verdict: pass with items

The gate's first step, CI green at the exact commit, is met on attempt 2 of the pre-main run at 32d381e45; attempt 1 was red on one test, the parked flake (known bug 2; "What I verified" 1 below). Nothing found risks a user's records, safety or trust by the section 4 definitions: no crash in the record, no storage change, nothing that misleads about position, direction or time, nothing that reads as identification, no dead end. Eleven items follow, each with where it is and why it matters. Items 1, 2, 3 and 5 are the ones I would ask the owner to read before approving; the rest can ship listed.

What this promotion puts in front of the public, read against main: **Forager Trail Map is the default map for everyone.** On main the modes are `STREET` ("Street", OpenStreetMap's raster, the default) and `TOPOGRAPHIC` (`MapMode.kt:36-46` on main), and no own map exists there (`map/StreetStyle.kt` is absent on main). After this promotion every user who had "Street" opens on Trail Map, a vector map served by the owner's Worker (-1554, -1555, -1556), with three chips Trail · Topo · OSM, night icons, the no-signal switch to Trail Map, world tiles south of 16°N, trail routes stored with every offline download, and a GitHub release job. The record shows the owner ruled each of those; what follows is what a user meets.

---

## What I verified

1. **CI at the exact head.** Push run 40026293407 on 32d381e45, **attempt 1: failure**, in "Build, test, publish APK" at "Run the unit tests"; the other four jobs green. From the job log (job 120663225587, read through the API): 0 Kotlin `e:` lines, `5405 tests completed, 1 failed, 24 skipped`, the one failure `MushroomLogViewModelWiringTest > a find deleted through the Journal's ViewModel leaves the Maps tab's records`. That is the parked flake, known bug 2 (-348, -349, -1093; 3 of 27 runs on 2026-10-18), and the count equals Sorrel's head count for #383, so the suite that ran is the whole suite. **Attempt 2** was started under the owner's account at 21:39:02Z; **attempt 2 completed success on all five jobs at 21:54:21Z** (read from the API; the test job's log ends `BUILD SUCCESSFUL in 12m 20s` with no `e:` line and, since Gradle prints the tally only on failure, no count line). So CI is green at the exact commit, on the one re-run the branches rule allows for a failure that also happens on code the change does not touch; Rowan's note to me says the same. PR #385's own run 40024313029 on its head 75ddc746d (the one commit under the merge, same tree apart from the merge commit) completed success (read). Every earlier pre-main push run in the range is success (39846205013, 39880409429, 39932604711, 39939595848, 39939798014, 40018445100, 40018501219; read from `gh run list`).
2. **No test silenced.** No `@Ignore` or `@Disabled` added or removed in `app/src` across the range (grep of the diff: empty). The CI skip allowlist is unchanged: the `ci.yml` diff adds the routing key's `env` on the debug APK step and the two new jobs (`route-cutter`, `pmtiles-worker`); nothing in the existing allowlist block moves (read). Sorrel's reviews sum the JUnit XML at 5,252 (base) to 5,405 (#383's head), every added case accounted for by class, 24 skipped throughout (relayed; I did not re-parse).
3. **No dev tools.** The `app/src/main` diff adds no walk logger, diagnostics screen or dev-tool file (grep: empty).
4. **The promoted app code is the S22-checked tree.** `git diff 089bfabb6 32d381e45 -- app/ third_party/` is three lines in `app/build.gradle.kts` (the signing guard no longer prints the key alias, Sorrel's #385 L1); nothing under `app/src`. 089bfabb6 is #383's code head, whose merge ref the S22 launch check ran on (`1.0.3873+g7bede5bd`, `status=verify`, alive after 8 s, crash buffer empty; report part three, relayed).
5. **The live Worker is the promoted Worker.** `git diff 54f4d56c4 32d381e45 -- server/` is empty: the code the planner deployed with wrangler (-1572, bundle 84ee6dac… = CI 39939798014's artifact) is byte for byte what main will carry. So when the owner turns "Disable builds" off after the merge (-1563 checklist step 2), the first build of main redeploys the code that is already live. The -1562 caution (a build of main *before* the merge puts the old Worker back) still holds until the merge lands.
6. **The offline-routes row wording against the owner's draft** (`docs/navigation/2026-10-23-offline-area-routes-report.md:55-79`, "OK as drafted" at `:157`), against `ui/availability/OfflineRegionRouteLine.kt:62-66`, character for character, straight apostrophes on both sides (grep for a curly apostrophe: 0 in each):
   - A "Getting trail routes": matches (`ROUTES_GETTING`).
   - B "Map only. Trail routes didn't download." with "Try again": matches (`ROUTES_DIDNT_DOWNLOAD`, `ROUTES_TRY_AGAIN`).
   - C "Map only. No trail routes for this area yet.": matches (`ROUTES_NONE_HERE`).
   - E offline "Map only. Trail routes download next time you're online.": matches (`ROUTES_NEXT_TIME_ONLINE`).
   - D: not on the row, as the owner ruled ("Only when a route reaches that edge"); `Stored` gives `null` (`:30`).
   - **F does not match its own sentence.** See item 2.
7. **The S22 evidence in the record,** read entry by entry: -1557 (#377's nine steps passed, plus Street→Trail after the update), -1567 (#380's night check on 1.0.3833: "61 night images added, 6 image references switched, 0 refused", offline "4 switched, 0 refused" on v1 regions, town dots dark, Topo at night and aeroplane mode right; the grey chip on 1.0.3837 by Rowan), -1572 (#382 engine timing 0.68 s cold then 38–94 ms, about 26 MB peak), -1573 (#383's live route download stored W125_N45.rd5 for the owner's region). All relayed.
8. **Public text.** `docs/legal/privacy-policy.md` is unchanged in the range (diff: empty); `docs/beta/README.md` unchanged; the repository README gains the release-job paragraph Sorrel read as accurate. The app's credits add BRouter (MIT), "Trail routes: © OpenStreetMap contributors" (ODbL) and rename Protomaps' row to name Forager Trail Map (`DataSourceCredits.kt`, `software.json`; read). Item 1 is what the privacy policy now omits; item 7 is one credit line.
9. **Owner's rulings held, from the code:** three modes in the ruled order with the ruled labels (`MapMode.kt`); `DEFAULT = TRAIL`; a stored "street" opens on Trail with nothing rewritten; downloads always Trail Map's style; the offline picker on Trail Map; no signal → Trail Map from downloads after 5 s, back after 30 s; the chosen chip grey while Trail stands in, stored choice untouched (`MapModeChips.kt:41-50`); night icons as bundled `night/` copies; the route row as drafted (6 above). Camera files: none in the diff. Back handlers: none in the diff. No new map chrome: the chips live in the existing Layers sheet, whose container and scrim are as before (`MapLayersSheet.kt:250-254`); the grey fill replaces the old selected fill on the same chip, so the 80% treatment is whatever it was (unchanged by this range).

---

## Items

Each: where, what the user meets, why it matters, evidence.

### 1. The privacy policy no longer describes what the app sends the Worker (A7)

- **Where:** `docs/legal/privacy-policy.md:60` (the table row: the Worker serves "The offline map style, and vector map tiles … The offline basemap, and downloading an offline region"), `:15-16`, `:79-80` ("The Worker's own code writes no log lines (`server/pmtiles-worker/src/`)"). Unchanged in the range; whatever the website mirrors from it.
- **What the user meets:** after the update the Worker is the *live* map for everyone, not only offline, and a new request goes to it: `GET /routing/area.json?south=&west=&north=&east=` with the app key, the exact rectangle (six decimals, about 11 cm) of every area the user downloads, then the file URLs (`RouteAreaClient.kt`; the Worker's `handler.ts`). The policy's generic sentence ("map tiles for the area on screen … a Cloudflare Worker run by the developer. They carry a location and your IP") still covers the tiles; the table row is now stale on purpose, and the route request, a new kind of location (an area the user chose, not a tile they looked at), is not listed. "Writes no log lines" is no longer literally true: `server/pmtiles-worker/src` has 13 `console.warn`/`console.error` lines (world-fill skips, the `missing` squares, `not_configured`); none carries more than a five-degree square name, and `[observability] enabled = false` is still pinned (`wrangler.toml:12-13`), so nothing persists, which is what Sorrel's #381 L1 says too.
- **Why it matters:** "What Forager says in public stays true" (A7). Nothing is retained and nothing identifies anyone, so this is not a fail; it is a public document describing last month's data flow.
- **Recommendation:** a wording pass on the three places before or with the promotion (documents reach main by promotion too, -1086), and the Play Data safety form if it itemises the Worker. The route rectangle is the sentence I would add.

### 2. Path F's row text is not the owner's drafted sentence, and "an app update is needed" reads as a fact about the area

- **Where:** `OfflineRegionRouteLine.kt:30`, `:64`; `RegionRouteData.kt:137-151` (`NOT_RETRYABLE` = `NO_APP_KEY`, `FORBIDDEN`, `TOO_LARGE`, `OVER_PHONE_LIMIT`, `BAD_REQUEST`); the draft at `docs/navigation/2026-10-23-offline-area-routes-report.md:78-79`.
- **What the user meets:** when the Worker refuses the key (`FORBIDDEN`: a wrong key in a build, or a key rotated on the Worker after this app build ships), or the area is refused, the row says "Map only. No trail routes for this area yet." with no button. The draft for F reads: 'the row reads as C, "Map only. Trail routes aren't available for this area.", with no button'. The draft contradicts itself (reads as C, then a different sentence), and the build took C's sentence. The owner's "OK as drafted" did not settle which.
- **Why it matters:** the brief asks for the owner's draft exactly; and for the key-refused case the sentence states something false about the area and gives no next step ("don't make me think": the user wonders why their area has no trails; the fix is an app update, which neither sentence says). Reachable on a Play build only by a wrong key (item 5) or a later key rotation.
- **Recommendation:** the owner says which F sentence; and, for the key-refused case only, a line that says to update the app. Not a fail: no record, position or safety is involved.

### 3. An OSM or Topo user with no downloaded area loses the map five seconds after signal goes

- **Where:** `ui/map/NoSignalTrailMap.kt:45-46` (`drawsFromDownloads`), `SightingsMap.kt:590-596`, `MapStyleReload.kt:69` (`currentOfflineRenderStyleUrl`), `StreetStyle.kt:66-67` (no regions → `offline.json`).
- **What the user meets:** they chose OSM or Topo, downloaded nothing, and walk out of coverage. On main the raster tiles they had looked at stay on screen from MapLibre's own cache. After this promotion, five seconds after the validated-network flag drops, the map reloads to the offline branch; with no regions that is the `offline.json` style from the Worker, which cannot load, and the map is blank until signal has been back for 30 s. PR #377 states it ("No signal and no downloaded area: the map is blank") and made it S22 step 6 as an observation, not a gate; -1557 records the nine steps passed and not what step 6 showed. **Inferred from the code, not seen on a phone by me.**
- **Why it matters:** "every failure has a fallback" (-519): here the fallback for no-signal-with-no-download is nothing, where main had the cached tiles; and a map that goes blank in the woods is the state "the user could misread as broken". It does not affect Trail users (the default after the update), whose online style keeps its cached vector tiles, so it is limited to people who actively picked OSM or Topo and never downloaded.
- **Recommendation:** the owner decides whether -1554's rule ("An OpenStreetMap user who loses signal sees Trail Map until it returns") was meant to apply when there is nothing to show; the cheap variant is to switch only when a downloaded region exists. Until then, a known-bugs row (item 8).

### 4. South of 15.92°N the picker looks mapped and a download succeeds with empty tiles

- **Where:** `server/pmtiles-worker/src/index.ts:532-539` (z15 wholly south of the archive's `minLat` → 204, Amendment 4 "Stop it"), `:576` (z9-14 → 204); `MapLibreOfflineMapRepository.kt:114-120` and `OfflineMapRepository.kt:125-126` (downloads ask z10-15 only); the world fill to z8 (`WORLD_FILL_MAX_ZOOM`). Sorrel's follow-up on #376 section 3 and #378's review traced it.
- **What the user meets:** anyone outside the US-and-Canada archive (Mexico south of about 16°N, Central and South America) sees land and water in the live map and the area picker at zoom 8 and below, downloads, and gets "Succeeded" with a region that holds only empty tiles at every zoom it stores. On main z15 there came from the live overflow, so this is new in presentation. The Worker README's own line still says such downloads "get world tiles to z8" (`server/pmtiles-worker/README.md:59`), which -1558 left open.
- **Why it matters:** "partial or failed results are reported as such, never presented as success". Not a fail: the location dot is right, no records are touched, and the users affected are outside the app's stated coverage; but a success message over an empty download is the honest-copy rule unmet.
- **Recommendation:** a known-bugs row now; later, a picker line south of the archive's bound, or a refusal.

### 5. The route feature has never run on a release build, and the first release build goes through a job that has never run

- **Where:** `app/build.gradle.kts:503-519` (`verifyReleaseHasRoutingAppKey` checks presence, not validity: Sorrel's #383 follow-up B3); `.github/workflows/release.yml` (new, `workflow_dispatch` only, from main, after the owner's approval; never run: PR #385 "Not run", Sorrel's "Not verified"); -1573's open line ("the check should include one route download").
- **What the user meets:** nothing until the first Play build; then, if the key the release environment holds is not the Worker's, every region shows item 2's "No trail routes for this area yet." with no button, on every phone, with no crash and no log the user sees. The GitHub job fails closed at every step (jarsigner, fingerprints, version code), so a broken job uploads nothing; the risk is a delay, not a bad build.
- **Why it matters:** the only phone evidence of routes is a debug build with CI's key on the S22 (-1573). The Play build's key path (GitHub environment secret → `BuildConfig`) has been read, not run.
- **Recommendation:** before the draft is rolled out on the internal track, one route download on that build, read in the Offline maps row (no line = stored). Not seen on a phone.

### 6. The pre-download estimate says tiles only; the download now also pulls route data

- **Where:** `AvailabilityOfflineMapsUi.kt:230-232` ("~N tiles"), `:270` ("Download this area?"); `RouteAreaLimits.kt` (up to 64 MB and four files per region); the S22's 13 km region took 2.3 MB of routes (-1573, relayed).
- **What the user meets:** the confirmation names the radius and the tile count; the route bytes arrive unannounced, then show in the region's size afterwards. Over mobile data a dense area could add tens of megabytes the user did not agree to.
- **Why it matters:** honest copy; mobile data is a cost the field user notices. Low: the sizes seen so far are small, and the row's size is honest afterwards.

### 7. One credit line over-attributes the Trail Map icons

- **Where:** `app/src/main/assets/credits/software.json` ("Forager Trail Map's icons, from Mapzen's tangrams icon set … as Protomaps publishes them"); Sorrel's #379 L4; -1561 ("As the project's own art").
- **What the user meets:** the open-source credits page says all of Trail Map's icons are Mapzen's. Eight of the sixty-one (sprite v5's cut-outs, Sage's) are Forager's own.
- **Why it matters:** A7, cosmetic; MIT asks only that the notice be kept, which it is. One phrase ("Forager Trail Map's Protomaps icons" or "fifty-three of") fixes it; no gate.

### 8. The known-bugs list did not move with this range, and one row's "Where" is stale

- **Where:** `docs/process/known-bugs.md`, identical on main and pre-main (13 rows each). Row 13 says "New (pre-main, #337)"; #337 is on main (merge 03bf2e420 in `git log origin/main`), so it is live on main, though not yet in a Play build (Play holds 3292 and 3500, -1366).
- **What the user meets:** nothing directly; A6 is the rule that nothing is found first by users.
- **Why it matters:** A6 asks each promotion to list what it ships with. The rows I would add from this range, none serious: item 3 (blank map for an OSM/Topo user with no download), item 4 (empty southern downloads), Sorrel's #383 B2 (a late MapLibre progress callback can flip "Getting trail routes" back to "N / N tiles" for the rest of the route step; cosmetic; not seen), #377's rotation note (the signal monitor restarts with the Activity, so a rotation during a hand-over can switch the map to the downloads at once with no 5 s guard and hold it 30 s; not seen), and #381 L2 (phones behind one carrier address share the 30-a-minute limit, which at public scale means some route fetches answer 429 and the row says "didn't download" with Try again; `handler.ts:241-248`). The owner's own field list is still "to add" (the file's last line).

### 9. The grey chosen chip has not been seen in the light theme

- **Where:** `MapModeChips.kt:62-70` (fill `outlineVariant`, #D5CEBF light, #444444 dark; label `onSurface`; no outline on a selected chip); `MapLayersSheetOfflineChipTest` covers both themes by pixel (Sorrel's #380 follow-up).
- **What the user meets:** in the light theme the grey fill stands 1.36:1 off the sheet with no outline (Hazel's figures, read in the file's comment); the normal selected fill is fainter still (1.12:1), so "chosen but standing aside" and "chosen" are both near the sheet colour. The label reads 8.4:1, so the text passes Appendix B.1. The owner's S22 look was at night (-1567); Rowan's grey step on 1.0.3837 does not record the theme.
- **Why it matters:** a state the user is meant to read at a glance; "grey = chosen, but something else is shown" only works if the grey is seen. Low; not seen on a phone in light.

### 10. Every pre-Trail region shows a notice and starts a fetch the moment Offline maps opens

- **Where:** `OfflineRegionsList.kt:232-240` ("Older map style. Download again to update.", `StreetStyle.kt:84`, shown on every region downloaded before Trail Map); `AvailabilityViewModelOfflineMaps.kt:57-64` (the automatic route fetch on opening, with signal).
- **What the user meets:** after the update, every existing region carries the notice at once, and, with signal, "Getting trail routes" runs down the list by itself, then either nothing (stored) or a row line. Honest, with a next step, and the owner chose automatic over buttons; recorded so the first opening after the update is not read as something going wrong. Not an item against; an observation for the release note.

### 11. Battery and data (A8)

No new sensor, GPS or screen-on time. One network callback registered for the Activity's life (`AndroidNetworkSignal.kt`). The one new cost is data: the route bytes per region download (item 6). Nothing to fail on; stated as the rule asks.

---

## Sorrel's findings left open at merge, judged for public use

Read from the eleven reviews on review-pr376-pr377 (c56757319) and the PR bodies. "Matters" means a user could meet it in the promoted build.

| PR | Finding | Matters for public use? |
|---|---|---|
| #376 follow-up | Low: `worker.test.mjs:323` could not fail (fixed by #378); the z15 cut changes southern downloads (item 4); the cut applies to every archive's `minLat` | Item 4 only |
| #378 | README line on southern downloads wrong (`README.md:59`, still); the overflow's 404 branch untested; the put fake records keys only | Repo text only; item 4 names the README |
| #377 | Medium (closed by Amendment 3); rotation restarts the hysteresis; hand-over order unread (S26 only); caption and chip while no signal (ruled); Topo night offline (owner saw it right, -1567) | Rotation: item 8; hand-over: not seen on a phone |
| #379 | M1 night on a device (closed by -1567); L1 deploy order (superseded by -1562/-1563; the Worker is live ahead of the app, the safe order); L2 ambient 404 cache (moot in the right order); L3 v1 night log line reads like a failure (logcat only); L4 MIT credit (item 7); L5 407-line file | Item 7 only |
| #380 follow-up | L4 chip reads its own glue; L5 entry-map grey unreachable (owner: "Leave it"); L6 Topo night over OSM after a fallback shows a normal chip; L7 added count not read back | L6 is an edge no user has met; none |
| #381 | M1 comment overstates the key limit; M2 edge format (closed by #383's six decimals); L1 logs reach nobody; L2 IPv6/carrier NAT; L3 cache key; L5 one damaged square fails the area | L2 at public scale (item 8); L5 is "Try again", honest |
| #382 | M1 cancelling a route does not stop the engine (no production caller; -1573 keeps it open before one lands); L1-L8 | None today: nothing routes in production, by design |
| #383 and follow-up | M1, M2, L1, L2, L4, L6 closed by Amendment 1; B1 a stray that will not delete re-fetches everything (safe direction); B2 progress text can flip back (item 8); B3 guard checks presence not validity (item 5); B4; A1 `internal` members (planner accepted, -1573); L5 the debug APK artifact carries the key (accepted, -1571: "a deterrent, not secrecy"); L7 engine checksum failures not fed back (no caller) | Items 5 and 8 |
| #385 | L1-L3 fixed in 75ddc746d; L4 cmdline-tools fetched without a checksum (pre-existing, shared with ci.yml); I1 tag-object pin; I2 main has no branch protection and `can_admins_bypass`; I3 timeout mid-upload | Pipeline only; item 5 names the untested first run |

Nothing in the table is a fail case. The two that touch what a user reads are items 2 and 5 (a wrong or rotated key) and item 4 (southern downloads).

---

## Known bugs this promotion ships with (A6)

From `docs/process/known-bugs.md` at 32d381e45 (identical on main). Row 1 is resolved. Rows 2 and 6 are tests only; row 2 (`MushroomLogViewModelWiringTest`) fired again today on this very commit's first CI attempt, which is one more data point for its rate and nothing a user meets. The rest, each already live by its row:

- **3** Availability search summary's month name can lag a language switch (live, not serious).
- **4** Landscape search pill height until Maps is shown (live, not serious).
- **5** Tablet camera at ROTATION_0 through the turnable viewfinder, cost unmeasured (live, observation).
- **7** Offline maps landscape panel slides with the search bar (hold, accepted quirk).
- **8** MapLibre logo overlaps the Topo credit line in portrait (hold, accepted).
- **9** Topo night credit line probably wraps (estimated, not seen).
- **10** Back from Maps with a pending "Back to Tools" return, within a frame or two (live, inferred, not serious).
- **11** Night Maps cold start shows 2–3 cream frames before the night map draws (live, inferred). **Note for this promotion:** with Trail Map the default, this reaches every Night Maps user's first screen; it was last seen on pre-main builds, not on 3500.
- **12** First recording after install, started during the notifications prompt, shows no notice (live, inferred).
- **13** Spring-back edges, up to 36 dp for a quarter second (on main; "Where" says pre-main, item 8).

**To add, from this range (none serious):** items 3, 4, 8's three Sorrel notes. The owner's own field and closed-testing list is still owed to the file.

**New bugs of the fail kinds:** none found.

---

## Not seen on a phone

By the record's own account, and by mine (no phone):

- **The fallback-grey chip** (a Trail user whose Trail Map style failed to load): the owner skipped the device step (-1568 "Skip it"); unit test only.
- **v2 regions at night offline:** the S22's regions were v1, so the offline night check covered four references, not six (-1567; Sorrel's M2 status).
- **The signal-return fetch** (routes fetched when signal comes back while Offline maps is open): tests only (Hazel and Sorrel both say so).
- **The "Getting trail routes" bar moment** during a download's route step, and **"Try again" on a real screen**: the one live download stored its routes, so no row line and no button appeared; Sorrel lists the row on a real screen as Hazel's claim.
- **Item 3's blank map:** PR #377 step 6 was an observation; what it showed is not in the record.
- **The S26:** nothing in -1554 to -1576 names an S26 check; the Wi-Fi-to-cellular hand-over (open since -1557) can only be seen there.
- **The tablet and landscape:** nothing in the range was run on the tablet or in landscape; the Offline maps row gained a line and a button that the tablet's side-by-side layout also draws.
- **The light theme's grey chip** (item 9).
- **A phone south of 15.92°N** (item 4).
- **A release build, and the release job** (item 5).
- **The world fill on a phone after the TileJSON cache expired:** -1559 names the expiry (about 05:32 PDT today) and the curl checks; the Bogotá-at-z8 phone step (#376's step 6) has no record entry.

Everything else a phone can show (start, the chips on one row, Street→Trail after the update, no-signal on OSM and Topo over a download, Topo at night, the night icon set online and on v1 offline, the engine's timing, one live route download) is in the record as relayed above.

---

## What I could not check

- No phone, no emulator: every S22 result above is the record's or a report's.
- The JUnit XML counts, the dex report and the APK sizes: Sorrel's and Hazel's figures, not re-parsed.
- Sorrel's byte-identity and revert checks: read, not re-run.
- MapLibre's behaviour when a style fails to load on the offline branch (item 3's "blank"): inferred from `fallsBack` being false offline and the style being fetched by URL; the PR's own word is "blank".
- Whether the website's privacy page differs from `docs/legal/privacy-policy.md` (item 1): the site is in another repository.
- The Play Data safety form.
- Whether the Cloudflare build of main (after "Disable builds" is turned off) deploys cleanly: the code is identical to the live deploy, so the only thing that can differ is the build environment.

---

## Summary for the owner

Promote, with items 1 to 5 read first. The decisions that are the owner's: item 2 (which F sentence, and a line for "update the app"), item 3 (whether the no-signal switch should fire when there is nothing to show), and whether items 3 and 4 go on the known-bugs list before or with the promotion. Item 1 is a document fix that can ride the next promotion; item 5 is the first internal-track build's check.

After the merge, the -1563 order stands: turn the Worker's "Disable builds" off only after main holds the code, then check the first build live (us.json south bound −85.05, `/style/street-v2.json`, a routing request 403 without the key).
