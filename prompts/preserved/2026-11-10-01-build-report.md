## Dispatch 2026-11-10-01, build step: PR #23 is open and unmerged, but the Cloudflare preview fails to deploy

The page, the 404 page, the three search files, the redirects, the home-page link and the README are built and pushed. **There is no preview address.** Every Pages preview of this branch fails to deploy. The likely cause is a stale binding that only the owner can remove in Cloudflare's dashboard, which is a stop for me. I sent the evidence to the main conversation mid-run; the full version is below.

The before-and-after table therefore uses a local Pages simulator for the "after" column, not the preview. The preview run is still owed.

### Branch, commits, PR
- **Branch:** `claude/forager-page` in `slayer8366/zynergy-site`, cut from main `fd77ab5`, in an independent clone at `~/.cache/forager-wt/forager-page`.
- **Commits:**
  - `70f1fbb`: groundwork (404 page, robots.txt, sitemap, redirects, home link, README)
  - `3b98bf0`: an empty commit, to redeploy the same tree as a measurement
  - `e39e451`: the Forager page and llms.txt
- **PR:** https://github.com/slayer8366/zynergy-site/pull/23. Its head is `e39e451`.
- After pushing, I confirmed the diff against main on the remote branch is 8 files, +414/−20, and the working tree is clean.

### Why there is no preview: the stop
- **What failed.** The Cloudflare Pages check failed on all three commits: `70f1fbb`, `3b98bf0` (the identical tree) and `e39e451`. The check carries only a dashboard log link, which I can't open.
- **What I ruled out.** Locally, `wrangler pages dev` 4.159.0 compiles the Functions and parses all 6 redirect rules as valid, so the content is unlikely to be the cause.
- **The likely cause, inferred because I haven't seen the log.** RECORD -2193 says the first deploy of `fd77ab5` "failed because the Pages project still bound the deleted D1 database". The owner then removed the binding ("Deleted and deployed"). The README said the binding was on "both Production and Preview" (`README.md:76-77` at `fd77ab5`). Production has deployed since, and every preview has failed, including `4110fa0` around the time of the deletion. That pattern fits the Preview environment still binding the deleted database.
- **The fix is the owner's.** In the dashboard: Workers & Pages, then zynergy-site, then Settings, then Bindings, under **Preview**: remove the BETA_DB binding.
- **Then:** any push to the branch redeploys. Resume me and I'll run `/tmp/hazel-fp-scripts/probe2.sh <preview>` and the page checks against the preview address.

### Mechanism prediction
My step-one prediction is filed unedited as `prompts/preserved/2026-11-10-01-step1-report.md` and recorded in RECORD -2196.

**Locally, every part of it held:**
- unmatched paths return the 404 page with status 404, through the middleware's headers;
- `/404` returns 200, which is why the not-found page carries noindex;
- `/404.html` returns 308 to `/404`;
- robots.txt is served as `text/plain`, sitemap.xml as `application/xml`;
- the paths that work today behave the same.

**Still to show on the preview:**
- the `x-robots-tag: noindex` header that Pages adds to every preview;
- no Cloudflare robots block in front of our file there.

### Before and after: live site versus the local simulator (preview column still owed)
Body markers are each page's own `<title>` or `<h1>`, or the first bytes.

| Path | Before: live, 2026-11-09 23:00 Pacific | After: local `wrangler pages dev`, e39e451 |
|---|---|---|
| `/Forager/` | 200, the home page ("Zynergy Labs") | **200, "Forager: a private foraging companion…", h1 "Forager", 15,743 B** |
| `/Forager` | 200, the home page | 308 to `/Forager/` |
| `/forager`, `/forager/` | 200, the home page | **301 to `/Forager/`** |
| `/FORAGER/`, `/no-such-page`, `/forager/gpx/1`, `/favicon.ico`, `/api/no-such` | 200, the home page | **404, "Page not found"** |
| out-of-bounds tile `soil-ph/5/0/0.png` | 200, the home page as text/html | 404, "Page not found" |
| `/404.html` | 200, the home page | 308 to `/404` |
| `/404` | 200, the home page | 200, "Page not found" |
| `/robots.txt` | 200 text/plain: Cloudflare's block plus the home page's HTML (6,659 B) | **200 text/plain, our 3 lines, no HTML (77 B)** |
| `/sitemap.xml` | 200 text/html, the home page | **200 application/xml, 4 addresses** |
| `/llms.txt` | 200 text/html, the home page | **200 text/plain, "# Zynergy Labs"** |
| `/`, `/privacy/`, `/delete-data/`, `/welcome/`, `/Forager/forecast/` | 200, each page's own title | same |
| `/privacy`, `/delete-data`, `/welcome`, `/Forager/forecast`, `/index.html` | 308 to the slash form | same |
| a real tile `soil-ph/5/4/10.png`, and `manifest.json` | 200, image/png and application/json | same |
| `/beta-signup`, `/beta-signup/` | 302 to Play | same |
| `/forager/forecast`, `/forager/forecast/` | 301 to `/Forager/forecast/` | same |
| `POST /api/beta-signup` | 410 `signup_closed` | same |
| `/functions/x`, `/db/x` | 404 "Not found" | same |
| `/api/beta-export` | 503 | same |

Other local checks:
- **Pages that should be unchanged are byte-identical, live against local** (SHA-256): `/privacy/`, `/delete-data/`, `/welcome/`, `/Forager/forecast/`, `app.js`, the tile and the manifest.
- The CSP and nosniff headers are present on `/Forager/` (200) and on a 404 response.

### Page checks
- **HTML parse:** html5lib 1.1 in strict mode, which raises on the first parse error. It parsed `Forager/index.html`, `404.html` and `index.html` (as committed and as served) with 0 errors. It was shown to catch a stray `</div>`.
- **JSON-LD:** one block, parsed by `json.loads`, with types Organization, MobileApplication, BreadcrumbList and FAQPage.
- **h1:** exactly one ("Forager").
- **FAQ comparison** (`/tmp/hazel-fp-scripts/check_page.py`, run on the served page): "visible pairs 10, JSON-LD pairs 10 … FAQ VERDICT: MATCH, word for word".
  - Positive control: changing one word in answer 4 made it print "4. question same, answer DIFFERENT … FAQ VERDICT: MISMATCH" and exit 1.
- **Sitemap:** parses as XML, with four addresses. `/` and `/Forager/` are dated 2026-11-10 (UTC, as the site dated the privacy page); `/privacy/` and `/delete-data/` are dated 2026-11-09.
- **Privacy scan.**
  - Control: a value from the private box, planted and piped straight in without printing, gave `{'lat': 1, 'lon': 1, 'pair': 1, 'grep': 2}`.
  - The pushed diff (`git diff origin/main origin/claude/forager-page`) gives `TOTAL` 0 in every category.
- **Render:** one 390-px-wide render of `/Forager/` in headless Firefox, from the local server, not the preview. I viewed it myself: only the page shows, with no overlap or overflow.

### Verify first 4 and 5: the trace, at Forager `f637fba17`
Forager main is `137b1427c`, but only workflow files changed after `f637fba17`. The Play listing was fetched 2026-11-10 06:54 UTC.

Three read-only agents did the tracing. I re-read the citations below myself, except those marked (agent).

| Claim | On the page? | Evidence |
|---|---|---|
| Record walks, screen off | Yes | Record button `TrailheadControls.kt:164`; foreground location service (agent: `AndroidManifest.xml:253-256`); notification `strings.xml:5-8` |
| Log finds: camera, gallery, notes, optional location | Yes | `LogEntryDetailScreen.kt:264, :269, :276`; `LogFieldEditors.kt:26` "Description Notes"; `foundAt` is nullable at `MushroomLogEntry.kt:40` (agent) |
| Journal keeps finds, tracks, waypoints, maps together | Yes | Records chips at `RecordsFilterChips.kt:173-177` (agent) |
| Trail Map with trails and hill shading; Topo and Street | Yes | Default Trail `MapMode.kt:64`; hillshade layer in `forager-trail-v4.json` (agent) |
| **Contours on the Trail Map** | **No: the listing and the code disagree** | "contour" appears only in Topo's description (`Basemap.kt:161`). The listing claims "relief shading and contours" twice. |
| Offline maps by area | Yes | Pin and radius; "Download Maps" (agent: `AvailabilityOfflineMapsUi.kt:223, :273`) |
| Coverage | Yes (FAQ) | `AvailabilityOfflineMapsUi.kt:408`: "Offline downloads cover the continental United States…"; Topo "worldwide" at `Basemap.kt:161` |
| Way back along trails, next turn, falls back to the walked track | Yes, "while you record" | Return disabled until recording (agent: `TrailheadControls.kt:180`); fallback notes `WayHome.kt:83-112`; regions read at `FindWayHomeUseCase.kt:27-60` |
| Off-track alert | Yes, inside "Find the way back" | Fires only on the way back (agent: `ReturnWatch.kt:276-283`); on by default |
| Waypoints | Yes | "Drop waypoint" (agent: `AvailabilityMapOverlaysUi.kt:236-249`); trail route or straight line (agent: `WayToWaypoint.kt`) |
| Plotted routes, per-leg distance and elevation | Yes, "as you plot" | Elevation shows only while plotting; a saved route shows none (agent: `CompactMapRoutePlotting.kt:149-158`) |
| Planned trips | Yes | Trip download opens the map download prefilled (agent: `TripDownloadMaps.kt:56-74`). **Not "in one tap", as the listing says.** |
| Sundown warning | Yes | Started only by the recording service (`TrackRecordingService.kt:186`); strings at `strings.xml:20-29`; on by default (`SundownPreferencesRepository.kt:43`) |
| Back by reminder | Yes | `TrackRecordingService.kt:187`; `strings.xml:14-18` |
| **Altitude above sea level** | **No: only partly true** | `AndroidSeaLevelConverter.kt:20-24` needs API 34. Below Android 14 the strip shows the raw GPS height (`LiveHeight.kt:26-38`, RECORD -2022). The listing claims it outright. |
| Seasonal and List tabs | Yes, as "what has been found nearby" | `AvailabilityResultsUi.kt:184` "…historically been found nearby this month"; `AvailabilitySeasonalUi.kt:114, :177, :213`; both need a connection |
| Night colours | Yes | `AvailabilitySettingsRows.kt:41` "Night Mode", `:57` "Night Maps" |
| **Units** | **No: true with small gaps, left off** | `AvailabilitySettingsRows.kt:115-122` (agent lists the gaps) |
| **Camera strips photo metadata** | **No: partly true** | Fails open; gallery imports are not stripped (`privacy/index.html:158-160`) |
| GPX export and import | Yes | "Import GPX" `ImportGpxButton.kt:36`; "Share track" `TrackExportPanel.kt:293`; route-only files refused (agent: `GpxImportReader.kt:75-81`) |
| Backup and restore | Yes | `BackupSection.kt:88, :93, :100, :146, :229-231` |
| Free; no ads or in-app purchases | Yes (FAQ) | Listing JSON-LD `"price":"0","priceCurrency":"USD"`, with no ads or purchases label; no billing, ads or analytics library (`app/build.gradle.kts:543-582`) |
| Android 8.0 or later | Yes (FAQ) | `app/build.gradle.kts:265` minSdk = 26 |
| No identification | Yes (FAQ) | `MushroomLogEntry.kt:8` "This type never identifies the mushroom."; no ML libraries |
| No account; nothing uploaded; what is sent | Yes (FAQ) | `privacy/index.html:57-59`; listing line "No account, no sign-in." |
| Deleting data | Yes (FAQ) | `delete-data/index.html:47-68` |
| What works offline | Yes (FAQ) | Searches and weather need a connection (agent: `AvailabilitySearchRuns.kt:151, :171`). **The offline Journal and waypoints are inferred** from their having no network calls; not run. |

### The page's text in full
**[L]** marks words from the Play listing. Everything else is **my wording**, drawn from the trace above, and is for the owner to judge.

- **Title** [mine]: Forager: a private foraging companion for Android · Zynergy Labs
- **Description and share text** [mine]: A free, private foraging app for Android. Record your walks, log your finds with photos and notes, and download maps for places with no signal. No account needed.

**Forager**

[L] A private foraging companion. Record your walks, log your finds, works offline.
Free on Google Play, for Android phones. [Get it on Google Play]

**The problem it solves**

[L] Privacy is the reason this project began. A good spot is worth keeping to yourself, and so is the record of where you walked to find it. Forager has no account and no sign-in, and nothing you record is uploaded.

Good foraging is often out of signal, and it is easy to stay out too late or lose the way back. Forager records your walk without a connection, leads you back along trails in the areas you have downloaded, and warns you before sunset while you record.

**Features**
- **Record your walks.** Forager draws your track as you walk, and keeps recording with the screen off.
- **Log your finds.** Add photos from Forager's own camera or your gallery, and notes. Saving a location is optional.
- **Keep a journal.** Your finds, tracks, waypoints and downloaded maps are kept together in the Journal.
- **The Trail Map.** Forager's own map shows trails, with hill shading. You can also switch to a topographic map or a street map.
- **Download maps by area.** Choose an area on the map and download it. In the areas you download, the map and trail routes work with no signal.
- **Find the way back.** While you record, the way back leads to where you started, along trails in the areas you have downloaded, and shows the distance and direction of your next turn. Where there is no trail data, it follows the path you walked. If you stray far from it, Forager alerts you.
- **Waypoints.** Drop a waypoint anywhere on the map, name it, and navigate to it.
- **Plot a route.** Tap waypoints to plot a route between them, along trails where there is trail data. See the distance and elevation change of each leg as you plot, and save the route to a trip.
- **Plan a trip.** Give a trip a place and a date, then download the maps for its area straight from the trip.
- **Sundown warning.** While you record, Forager tells you before sunset how long the walk back is and when to start it, and again when the sun sets.
- **Back by.** While you record, set the time you plan to be back, and Forager reminds you when it comes.
- **What has been found nearby.** With signal, see which species people have recorded near a place at this time of year, and the recent rain, soil moisture and forecast there.
- **Night colours.** Turn on darker colours for the app and the map in Settings.
- **GPX files.** Share a recorded track as a GPX file, and import tracks and waypoints from GPX files.
- **Backup and restore.** Back up to a file whenever you like, or daily, weekly or monthly to a folder you choose. Restore it later, replacing what is on the phone or merging with it.

**Questions**
- **Is Forager free?** Yes. Forager is free on Google Play. It has no ads and no in-app purchases.
- **Does Forager identify mushrooms or plants?** No. Forager does not identify species, and it cannot tell you whether anything is safe to eat. The species names it shows are records of what other people have found nearby, not a match for your find.
- **Which phones does Forager run on?** Android phones with Android 8.0 or later. Forager is for Android only.
- **Do I need an account?** No. There is no account and no sign-in.
- **Does Forager work without signal?** Yes, once you have downloaded maps. Recording a walk, the Journal and waypoints work anywhere. The map, trail routes and the way back along trails work offline in the areas you download, so download them before you go. Seeing what has been found nearby, and the weather, need a connection.
- **Which areas do the maps cover?** Map downloads cover the continental United States. The topographic map covers the whole world, while you have signal. Where a downloaded area has no trail routes, Forager says so, and the way back follows the path you walked.
- **Where is my data, and what does Forager send?** What you record stays on your phone: tracks, finds, photos, waypoints and trips. None of it is uploaded. While you are online, Forager sends where you are looking, such as the map area on screen and the place you search, to the map and data services it uses. The privacy policy lists each one.
- **How do I delete my data?** Delete any item in the app, or uninstall Forager to remove everything. Nothing you record is kept on a server, so there is nothing else to delete, apart from any backup files you saved yourself. Step by step: Deleting your Forager data.
- **Can I move my data to a new phone?** Yes. In Tools, then Settings, use Backup to save a file, then restore it on the new phone. Android's own backup is turned off for Forager, so make a backup yourself.
- **Can I use my tracks in other apps?** Yes. Share a recorded track as a GPX file. You can also import GPX files with tracks and waypoints. Routes in GPX files are not imported.

[Get it on Google Play] · Footer: Zynergy Labs · Privacy policy · Deleting your data · support@zynergy-labs.com · Home

**Elsewhere, all my wording:**
- The home page's link: "About Forager".
- The 404 page: "Page not found. There is no page at this address. It may have moved, or the address may be mistyped. [Go to the home page] Looking for the app? Read about Forager."
- llms.txt is built from the page's own sentences.

### Calls beyond the dispatch
- **Working copy:** a separate clone rather than `git worktree add`, so the shared clone isn't touched.
- **The empty commit `3b98bf0`:** a measurement of whether the failure was deterministic, not a fix.
- **The mid-run message:** I sent the binding evidence to the main conversation.
- **Local evidence:** Node 24.19.0 from PyPI (`nodejs-wheel-binaries`) and wrangler 4 from npm, both in `/tmp`, run on a `git archive` copy, never in the repo. The Firefox render used a throwaway profile in `~/snap/firefox/common`, which I have deleted. Snap Firefox refused a profile under a hidden directory, so I did not touch the owner's running profile.
- **`applicationCategory` is "Maps & Navigation",** the listing's visible label. Google's list has no maps category. Google's app result needs a rating anyway, so this affects only other parsers.
- **The Play link** is in llms.txt alongside the four site links.
- **Tracing help:** three read-only Explore agents traced the features, and I re-read the citations the page depends on.
- **The listing's own words** "Privacy is the reason this project began." open the problem section.

### Owner-level findings, not acted on
The Play listing claims three things the code doesn't fully support, and all three are left off the page:
- Trail Map "contours";
- "Altitude above sea level" (true only on Android 14 and later);
- trip maps "in one tap".

Whether to change the listing is the owner's decision.

### Follow-ups
- **Out of scope, as agreed:** `Forager/forecast/pilot.js:88`'s comment ("returns the home page with status 200") becomes false at merge.
- **Out-of-date comments in the Forager repo**, which I did not touch:
  - `NextTurnSpace.kt:42` and `PlotRouteUseCase.kt:22` (both say "not wired", but they are wired);
  - `AndroidSeaLevelConverter.kt:53`'s log text ("Alt unavailable"; RECORD -2022 changed that);
  - `Theme.kt:131` ("checkbox"; the setting is now radio buttons).

### Not verified
- The Cloudflare preview: blocked, as above.
- The live site after merge, including Cloudflare's managed block in front of our robots.txt until the owner's dashboard change.
- The owner's "Allow training too" dashboard change.
- How search engines and AI tools index the page; only time and the owner's own tools can show that.
- Feature behaviour on a device: the trace is from code only.
- Whether production Pages behaves exactly like local wrangler.
