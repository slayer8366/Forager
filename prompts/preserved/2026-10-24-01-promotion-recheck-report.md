# Elder's re-check: the #386 range, pre-main adcbedb19 to fefc89129

**Auditor:** Elder, Forager's UX auditor, a fresh session on Claude Fable 5.1 (`claude-fable-5-1`). The coder ran on Opus; Sorrel's review of #386 was on Fable. **Planner:** Rowan. **Brief:** `prompts/preserved/2026-10-24-01-promotion-recheck.md` (records-after-173 at 6cdccdca5 when read). **Written:** 2026-10-24, about 07:55 UTC (00:55 PDT).

Read-only: no code changed, nothing merged, nothing deployed, no phone touched, no Gradle. What I ran: `git` reads of origin/pre-main, origin/main and origin/records-after-173; `gh run list`, `gh run view` and the runs API for the pre-main and tile-retry runs; `javap` on MapLibre 13.5.0's `classes.jar` from the Gradle cache (a read of bytecode, not a build); `date -u`. Every file:line below is at `origin/pre-main` fefc89129 unless it says otherwise. "Read" means I read it; "relayed" means the record, a review or a report says it and I did not see it; "inferred" is marked.

Yardstick: `docs/process/qa-philosophy.md` section 3, verdicts in section 4; CLAUDE.md's UX defaults; my audit of 32d381e45 (`prompts/preserved/2026-10-23-05-promotion-audit-report.md`) for the items this closes.

**Range:** `git diff adcbedb19 fefc89129`: PR #386 only, 12 commits, 22 files, +1,434/−32. `app/src/main`: 13 files, +617/−27 (counted from the stat). Nothing under `docs/`, `server/`, `.github/` or `README.md` changes in the range (diff: empty). Room schema untouched (no file under `data/local` in the diff), so the upgrade check still does not apply (-1133). The earlier range, 32d381e45 to adcbedb19, is PR #387 and is covered under "Items 1 to 3" below.

---

## Verdict: pass with items

CI is green at fefc89129 on its first attempt, all five jobs. Nothing found risks a user's records, safety or trust by the section 4 definitions: no crash path, no storage change, nothing about position, direction or time, nothing that reads as identification, no owner ruling broken (the pause line is the owner's option text character for character, the two limits are as the planner named them, downloads are never held, as -1585 chose), nothing unfinished. Four items follow; items 1 and 2 are the ones I would ask the owner to read before approving. The two things the brief says are already known (the fast-fling blank patch, -1591; the "no signal" wording on a server error, -1594) are not re-found below, but both ship to main with this promotion, and item 2 is that they need rows.

What this range puts in front of the public: a Trail Map patch that failed to load fills in by itself when signal comes back, instead of staying coarse until the user zooms; and an offline-area download that loses signal waits, saying "Paused · no signal · continues by itself", and carries on by itself when signal returns. Both are the kind of thing the owner's "don't make me think" asks for: the user does nothing, and the one new line answers the three questions a frozen count raised (is it stuck, do I have to do something, will it go on).

---

## Items 1 to 3 of the 2026-10-23 audit, at fefc89129

All three are closed, and the fixes are present at fefc89129 itself (read there, not only at adcbedb19).

1. **Privacy policy (item 1): closed.** `docs/legal/privacy-policy.md:3` "Last updated: 2026-10-23"; `:15-16` adds "the edges of any area you download for offline use" to what is transmitted; the Worker's table row names the route request's four edges to six decimals and the shared app key "so it identifies the app, not you"; the "writes no log lines" sentence is gone, replaced at `:91-99` by what the warning and error lines can carry (file names, five-degree squares, never an IP, the key, a tile or an area's edges) and that nothing is stored with observability off; the per-IP and per-key rate limits and the tile, font and 30-day route caches are described. The owner approved the text twice (-1579, then the corrected text after Sorrel's M1 and M2, -1584). What is not in the repository: the website's copy and Play's Data safety form ("follow after the merge", -1579). Read.
2. **Path F's sentence (item 2): closed.** `OfflineRegionRouteLine.kt:34` routes a `Missing` reason that `needsAppUpdate()` to `ROUTES_NEED_APP_UPDATE`, `:66` = "Map only. Trail routes need an app update.", which is the owner's sentence in -1578 character for character. `RegionRouteData.kt` (`needsAppUpdate`) covers `FORBIDDEN` (a wrong or rotated key) and `NO_APP_KEY` (the owner's "App-update line" for a build with no key, -1579). The area reasons (too large, bad request, over the phone's limit) keep C's line, with no button, as F's draft intended. Tests `OfflineRouteLineTest` and `OfflineRegionRouteRowTest` gained cases (in the #387 diff; relayed as failing-first by -1579). Read.
3. **The blank map for an OSM or Topo user with nothing downloaded (item 3): closed.** `NoSignalTrailMap.kt:59` `drawsFromDownloads` now requires `hasDownloadedArea`; `:66` defines it as any region with `isDownloaded` ("Any download", -1579); `MainActivity.kt:387` provides it from the Offline maps list; the kept map is logged when it fires (`rememberDrawsFromDownloads`, the CLAUDE.md fallback rule); `MapModeChips.kt` takes the same flag so the chip stays normal when the chosen map is drawn. Read. **Not seen on a phone:** the record has no device step for an OSM or Topo user with no downloaded area losing signal on #387's build; the S22's regions were deleted only today (-1594), after the merge.

Also closed from that audit, read at fefc89129: item 4's Worker README line (`server/pmtiles-worker/README.md:59` now says southern downloads store only empty tiles), item 7's credit (`software.json:341` "53 of Forager Trail Map's 61 icons"), and item 8 (known-bugs row 13's "Where" corrected; rows 14 to 17 added for items 3, 4 and the three Sorrel notes; item 3 recorded as fixed before shipping).

---

## What I verified about #386

1. **CI at the exact head.** Push run 40072059837 on fefc89129, attempt 1, all five jobs success: "Build, test, publish APK", "Android lint (debug)", "Release compiles and packages (unsigned)", "Tile Worker serves what its tests say", "Route-data cutter routes the same as the full square" (read from the API; `run_attempt` = 1). The PR's own head c3d06b2f1 ran green (40064295038, read). The two pre-main runs before it, adcbedb19 (40054316928) and 32d381e45, are success. The suite count at head is not in the record: Hazel's 5,444/0 is at Amendment 3 (-1591), before Amendment 4's three tests; Sorrel did not re-parse (relayed).
2. **No test silenced.** My grep of the range's added and removed lines in `app/src`: no `@Ignore`, `@Disabled` or `assume*`; one removed `assertEquals` (`MapUserAgentTest`, the old `mapHttpClient("x")` signature), re-added with the new signature at `MapUserAgentTest.kt:68` (read). 35 `@Test` methods added across seven test classes (counted). Fail-first is visible from outside the reports: the Amendment 3 tests-first commit b56239b55 failed CI on exactly the seven new tests, and f0b087b5c went green; Amendment 4's two screen tests were red at c5cc442ea and f2c612b0c and green at c3d06b2f1 (Sorrel's reading of the runs; I read the run list, which shows f2c612b0c's run 40063844620 as failure and c3d06b2f1's as success).
3. **No dev tools, no public text beyond the one line.** The only new user-visible string in the range is `DOWNLOAD_PAUSED` = "Paused · no signal · continues by itself" (`OfflineMapsSideBySide.kt:185`), the option text the owner chose in -1591 character for character. Everything else is logging (`Log.i`/`Log.w` under `TileRetry` and the repository's tag). Privacy policy, store text and credits are unchanged in this range.
4. **The owner's rulings, from the code.** Downloads are never held by the interceptor: a request tagged `MapRequestUsage(offline = true)` passes straight through (`TileRetryInterceptor.kt:53-54`), which is -1585's "own lane". The map's limits are named: 8 held at once (`TileRetryPolicy.kt`, `TILE_RETRY_MAX_HELD`), 5 minutes (`TILE_RETRY_HOLD_LIMIT_MS`), a download's wait 5 minutes (`DownloadPause.kt`, `DOWNLOAD_PAUSE_LIMIT_MS`), backoff 1, 2, 4, 8, 15, then 30 s (`TILE_RETRY_BACKOFF_MS`, the coder's, named as unmeasured). No whole-style reload anywhere in the range, so no night flash. The 404 case is not a failure and is not held (`attempt()` treats only 5xx and `IOException`s as failures). Read.
5. **Fallbacks logged, nothing swallowed** (CLAUDE.md). An untagged tile falls back to the map's pool and is logged once per process (`TileRetryInterceptor.kt:56-58`); gate-full is logged up to ten lines, the tenth saying so (`:148-153`); hold start, load, give-up each log; a download's pause, continue and give-up each log (`MapLibreOfflineMapRepository.kt:421`, `:407`, `:392` via `fail()`). The `InterruptedException` from the network wait is caught and rethrown as OkHttp's `InterruptedIOException` with the thread's flag set again (`:115-119`, `:139-142`), Sorrel's L1 closed. Read.
6. **The step paths.**
   - *Map patch:* Trail Map on screen, signal goes, a tile request fails → the map draws that patch from a lower zoom (as before) → signal returns → the held request is resent → the patch fills in, nothing tapped. S22: about 10 s after Wi-Fi returned (build 3884, -1585; relayed). No visible change while held, which is what the owner asked ("with no visual change", -1577).
   - *Download:* Download Maps → Download → "N / M tiles" → signal goes → the first connection or server error pauses it → the row reads "Paused · no signal · continues by itself", the bar stays at N / M (`OfflineMapsSideBySide.kt:156-167`) → signal returns, the engine resumes → the first new tile clears the pause and the count comes back → "Succeeded" and the region in the list, nothing tapped. S22: paused and finished 7 s after Wi-Fi returned (3894, -1591); the line shown and the count back (3897, -1593); relayed. The order in the repository (count first, then `onPauseChanged(false)`, `MapLibreOfflineMapRepository.kt:402-408`) is the order `DownloadPausedShownTest` test 2 drives, through the real screen with coordinate touches on Tools, the drawer row and Download Maps (read).
   - *Download, no signal from the first tile:* `Downloading(0, 0)` → pause → bar at 0 with the line, not "Starting download…" moving (`:158-161`). Read; not seen on a phone.
   - *Download, 5 minutes with no progress:* item 1 below.
   - *Back and rotation:* the status lives in the ViewModel's state, so leaving the Offline maps section and returning in the session, or rotating, keeps the line; no Back handler changes in the range (grep: none). Read; rotation during a pause not seen on a phone.
7. **The 80% rule does not apply.** The status line sits in the picker's controls column after the tile estimate (`AvailabilityOfflineMapsUi.kt:238`), beside or below the picker map, not over it. Read.
8. **Whether a paused download hears signal return without the map on screen.** MapLibre's `OfflineRegion.setDownloadState(int)` itself calls `ConnectivityReceiver.instance(context).activate()` for the active state and `.deactivate()` otherwise (13.5.0 `classes.jar`, read with `javap`), so the engine's connectivity listener is live for the whole download whether or not a `MapView` is started. That the native side then restarts the waiting request on that signal (`online_file_source.cpp:587-593`, cited in `DownloadPause.kt`) is **unverified by me**: no native source on this machine. The S22's 1 to 2 minute outage resumed in 7 s (relayed); a longer one is item 3.

---

## Items

Each: where, what the user meets, why it matters, evidence.

### 1. After five minutes the line that promised "continues by itself" becomes the usual error, and the part already downloaded is thrown away

- **Where:** `MapLibreOfflineMapRepository.kt:396-398` (the give-up runnable), `:383-394` (`fail()` resumes with the fixed `IOException`), `:164-167` (any exception → `deleteSuspend()`, "Never leave a half-downloaded region looking like a complete one", pre-existing), `AvailabilityOfflineMaps.kt:237-241` ("Couldn't download offline maps.").
- **What the user meets:** "627 / 1608 tiles" → "Paused · no signal · continues by itself" → after five minutes with no progress, "Couldn't download offline maps." in red, and the 627 tiles are deleted. The next step is the Download Maps button right above, which starts from zero. A user who read "continues by itself", pocketed the phone and walked on returns to an error and nothing kept.
- **Why it matters:** this is the owner's decision ("Up to 5 minutes without signal, then the usual error. Nothing to tap if signal comes back.", Amendment 1), and the delete is the pre-existing rule, so I am not asking for it to change before promotion. It is listed because it is the one state in this range a user could misread as broken ("don't make me think"), and because the error line is honest without a next step (the button is near, but the line itself does not say "try again" or that the partial download is gone). **Not seen on a phone:** Sorrel: "The 5-minute give-up was not run on a phone"; the -1585 give-up that "gave up cleanly" on build 3884 was the map tile's hold, before the download pause existed.
- **Recommendation:** a device step with a shortened limit after the promotion, as Sorrel suggests; and, later and only if the owner wants it, keeping the partial region with a "Continue" rather than deleting it, which is a feature, not a fix. For now a release-note line is enough.

### 2. The known-bugs list did not move with this range, and two things that ship with it have no row

- **Where:** `docs/process/known-bugs.md` is byte-identical at adcbedb19 and fefc89129 (17 rows; main has 13).
- **What ships without a row:**
  - **The "no signal" reason on a server error** (Sorrel's L-b; the owner chose "Paused · continues by itself" as a fix *after* the promotion, -1594). `downloadErrorKind` makes `REASON_SERVER` pausable (`MapLibreOfflineMapRepository.kt:449-452`), so on Wi-Fi a single 5xx from the Worker shows "no signal" until the next tile lands. The same wording also disagrees with the status bar for the few seconds between signal returning and the first new tile (S22: 7 s), since the line is cleared by progress, not by the signal (`:404-408`); -1594's wording closes both. Not serious; New.
  - **The fast-fling blank patch** (-1591, follow-up after the promotion): with no signal, a hard fling can fail more than eight tiles, and the ninth onward are not held (`TileRetryInterceptor.kt:70-73`), so when signal returns those patches stay coarse until the user zooms, which is what every failed tile did before this range. Not a regression; a remaining case. Not serious; New.
- **Why it matters:** A6, "listed and judged before release, never found first by users". The owner has already judged both; the rows are the record of that.
- **Recommendation:** two rows, "New (pre-main)", "Serious: No", each citing its RECORD entry; they can land with the promotion PR or the next document promotion.

### 3. A screen reader is not told that the download paused

- **Where:** `OfflineMapsSideBySide.kt:164-167`: the `Text` that switches between the count and the pause line carries no `liveRegion` semantics; nor do the count, "Getting trail routes" (`:155`), "Starting download…" (`:170`) or the error (`:174-178`).
- **What the user meets:** a TalkBack user who started a download and moved focus elsewhere hears nothing when it pauses, continues or fails; they learn it only by focusing the row. The line's words are spoken when focused (it is plain `Text`; the "·" separators are punctuation TalkBack usually skips).
- **Why it matters:** Appendix B.6 (WCAG 4.1.3): status messages reach a screen reader without moving its focus. This is the row's existing pattern, not something #386 introduced; the new state makes it one message larger. Low. **Not seen on a phone.**
- **Recommendation:** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the status text, as a follow-up covering all five messages at once.

### 4. Battery and data (A8)

No new sensor, GPS or screen-on time. **CPU:** up to eight OkHttp threads asleep in one-second slices for up to five minutes each (`Thread.sleep` via `pause`, `TILE_RETRY_CANCEL_CHECK_MS`), and a condition wait woken only by network changes (`SignalNetworkWait`); negligible. **One more network callback** for the life of the process (`tileRetryInterceptor` → `AndroidNetworkSignal`, beside `noSignalState`'s), registered once because `MapLibreInitializer.initialize` is guarded by `initialized` (`MapLibreInitializer.kt:36-44`; a failed initialisation, an `UnsatisfiedLinkError` class of event where the map does not work at all, would register another on retry). **Data:** with a validated network and a Worker that stays down, at most 8 tiles × one request per 30 s after the first 30 s, about 16 requests a minute, for five minutes; nothing when there is no network. The download pause adds no requests of the app's own: the engine was already retrying. Stated as the rule asks; nothing to fail on.

---

## Not items, recorded so nobody looks for them

- **Trail Map only.** The interceptor holds `.mvt` requests (`TileRetryInterceptor.kt:182`), which is Trail Map's tile template (`Basemap.kt:148`). OSM's and OpenTopoMap's raster tiles keep the old behaviour. That is the dispatch's scope (-1577 was about Trail Map's tree glyphs), not an omission.
- **A held tile looks like a loaded coarser one.** While held, the patch is drawn from a lower zoom with no indicator; the same look as a failed tile before this range, and as mainstream map apps. "Measured against what people already know": no weaker.
- **Sorrel's L-a** (a late status post after the give-up could write `Downloading` over `Failed`): Sorrel reads it as benign by Handler ordering, inferred. If that inference were wrong the user would see the count return after the error and the row never finish, since the region is already deleted. Relayed; the one-line guard Sorrel names is cheap and is the follow-up I would take first.
- **Sorrel's L-d:** `DownloadPausedShownTest`'s third test ("a pause that runs out ends in the usual error") passed before the production commit and cannot fail on Amendment 4's code; CLAUDE.md calls that suspect. It is a regression guard on the existing `Failed` path, not coverage of the give-up; which is one more reason item 1 is "not seen on a phone" rather than "tested".
- **The repository interface gained a 5-argument `download`** with a default that delegates (`OfflineMapRepository.kt:59-70`), so the existing fakes compile. Amendment 4 step 1 allowed it with disclosure. Read.
- **Modular rule:** new files 72 to 185 lines; `MapLibreOfflineMapRepository.kt` at 494 (Sorrel's count, relayed).

---

## Known bugs this promotion ships with (A6)

From `docs/process/known-bugs.md` at fefc89129, 17 rows. Unchanged from my 2026-10-23 report for rows 1 to 13 (row 13's "Where" now corrected). Rows 14 to 17 are the four from that report (southern downloads; the progress text flipping back; rotation restarting the no-signal timing; the shared carrier address and the route rate limit), all "New (pre-main)", none serious. **To add, from this range (item 2):** the "no signal" reason on a server error (-1594), and the fast-fling blank patch (-1591). **New bugs of the fail kinds:** none found.

---

## Not seen on a phone

By the record's own account and by mine (no phone):

- **The five-minute give-up of a download** (item 1), and its deleted partial region.
- **A pause before any tile has arrived** (bar at 0 with the line).
- **A server-error pause with signal up** (the -1594 wording case).
- **An outage longer than 2 minutes** during a download: the S22 runs were 1 to 2 minutes (-1591). The engine's backoff doubles with no cap (`http_timeout.cpp`, per the dispatch); the connectivity listener is live for the download (verified in bytecode above), and whether the native side cuts the backoff short on that signal is unverified by me.
- **A map tile held to its five-minute limit on a phone with the download pause in place:** build 3884's give-up (-1585) predates Amendments 3 and 4.
- **Rotation, lock or tab change during a pause** (A4): read from the ViewModel state only.
- **The largest font size, landscape and the tablet:** the new line is 39 characters, the longest in that row; `Text` has no `maxLines`, so it wraps rather than truncates (read), but how it sits in the tablet's 240 dp-minimum controls column (`OFFLINE_CONTROLS_MIN_WIDTH`) is not in the record.
- **A screen reader** (item 3).
- **The S26:** nothing in -1577 to -1595 names an S26 check.
- **Items 1 to 3 of the earlier audit on a phone:** #387's fixes were read, not run; the owner's S22 check of the promotion build is the planner's next step (-1595).

Seen on the S22, relayed: the patch filling in about 10 s after Wi-Fi returned (3884, -1585); the download pausing and finishing by itself 7 s after Wi-Fi returned, nothing tapped (3894, -1591); the pause line shown, the count back, the download finished (3897, -1593).

---

## What I could not check

- No phone, no emulator: every S22 result above is the record's.
- JUnit XML counts and the revert checks (D, A1, A2, L4, -1591): Hazel's figures, read in the record and in Sorrel's review, not re-parsed.
- MapLibre's native behaviour (`online_file_source.cpp`, `offline_download.cpp`, `http_timeout.cpp`, `tile_loader_impl.hpp`): every `.cpp`/`.hpp` citation in the new files is the coder's; I verified the Java side (`OfflineRegion.setDownloadState` activating the connectivity receiver; `ConnectivityReceiver`'s methods) in bytecode and nothing below it.
- Hazel's Amendment 3 and 4 completion reports: not on GitHub or records-after-173 (Sorrel says the same); what they claim reaches me through the dispatch text and the RECORD.
- The website's privacy page and Play's Data safety form (item 1 of the earlier audit): other repository and the console.

---

## Summary for the owner

Promote. #386 does what you chose, in your words, and the one new line is the right line. Items 1 to 3 from yesterday are closed in the code that will go to main. Before approving, read item 1 (what happens at five minutes: the usual error and the partial download deleted, your Amendment 1 choice, never yet seen on a phone) and item 2 (two rows for the known-bugs list, both already ruled by you). Items 3 and 4 can ship listed. The S22 check the planner has planned next (-1595) is the first time #387's three fixes will be on a phone; the five-minute give-up needs its own step with a shortened limit, and could be after the promotion.
