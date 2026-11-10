# Dispatch 2026-11-10-01: A Forager page at zynergy-labs.com/Forager/, and the site's search files (helper, Opus)

Written by Rowan Waymark, 2026-11-10. RECORD -2194 (intent). Every fact below was read at zynergy-site main `fd77ab5` and Forager main `f637fba17` (the app live on Google Play as 1.0.5026) while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim: **"Let's get the website updated, add a page with on zynergy-labs.com/Forager/ and share about what its features are, what problem it solves, and an FAQ."** Then: **"Optimize the SEO so it can be found by AI and other search engines"**.

Their answers to the planner's questions:
- robots.txt, sitemap.xml and llms.txt: **"Add all three, no forecast (Recommended)"**. List Home, Forager, Privacy and Delete data. Leave the pilot forecast demo out. The tester welcome page stays out of search.
- Extras: **"Lowercase address"**, **"Link from home page"**, **"Real not-found page"**.
- AI training crawlers: **"Allow training too"**. That is a setting in Cloudflare's dashboard, the owner's click, and not part of this dispatch (see the last section).
- Starting: **"Yes, start now"**, then **"Dispatch first."** So this file was written and stopped there. The helper starts only on the owner's go after reading it, recorded by continuation of -2194.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Rules for the page's words
Forager's CLAUDE.md on pre-main governs. Read it yourself. In short:
- **True of what ships.** Every claim is true of the app on Forager main `f637fba17` or of the live Play listing. A feature not on main is not on the page. No promises of future features.
- **No identification.** Forager does not identify species. Nothing on the page may read as identification, or as advice on what is safe to eat. The FAQ says so plainly.
- **Names.** Our map is the "Trail Map". Name a data source only where its licence requires a credit, and only in the credit. Describe other apps and makers generically; never name them.
- **Honest, plus a next step.** Where something is missing, say what the user can do (for example, download maps before going where there is no signal).
- **Don't make me think.** Short, plain sentences.
- **Home location stays private.** No coordinates, grid references, place names near the owner's home, or phone screenshots. Images are the existing `assets/forager-icon.png` (512×512) and `assets/forager-banner.jpg` (1024×500) only.
- **The owner's words** in quotation marks stay exactly as given.

## Change (what a person sees)
1. `https://zynergy-labs.com/Forager/` is a page about Forager:
   - what it is, led by the Play listing's own line, "A private foraging companion. Record your walks, log your finds, works offline.";
   - the problem it solves;
   - its features;
   - an FAQ;
   - a "Get it on Google Play" link to `https://play.google.com/store/apps/details?id=com.zynergylabs.forager.app`.
2. `/forager` and `/forager/` send people to `/Forager/` with a 301, like the forecast demo's lowercase addresses (`_redirects:1-4`).
3. The home page's Forager section (`index.html:105-112`) links to `/Forager/`.
4. An address that does not exist shows a "not found" page with status 404, linking to the home page and the Forager page. Today it shows the home page with status 200 (README:46-50).
5. `/robots.txt`, `/sitemap.xml` and `/llms.txt` exist as real files:
   - robots.txt allows everything except `/api/` and names the sitemap. Add no rules for AI crawlers.
   - sitemap.xml lists `/`, `/Forager/`, `/privacy/` and `/delete-data/`, with dates.
   - llms.txt is a short plain-text summary of Zynergy Labs and Forager with those links, drawn from the page's own text.

**For search engines and AI tools,** the Forager page carries:
- a title and description of its own;
- the canonical address `https://zynergy-labs.com/Forager/`;
- share tags (Open Graph and card tags), with the banner given as a full address;
- JSON-LD for:
  - the app: MobileApplication, Android, price 0, publisher Zynergy Labs, the Play link and the icon;
  - the FAQ: FAQPage, with text identical to what is on the page;
  - the breadcrumb, Home › Forager;
  - the organisation;
- one h1 and plain section headings;
- no ratings or reviews in the markup.

**Self-contained.** The site's security headers allow scripts and images from the site itself only (`functions/_middleware.js:33-36`), so use no outside fonts, scripts or images. Match the look of the site's existing pages; `privacy/index.html` carries its own inline style.

## Scope boundary
- **Repo and branch:** `slayer8366/zynergy-site` only, on a new branch `claude/forager-page` from main `fd77ab5`, in your own worktree at `~/.cache/forager-wt/forager-page`. The laptop's clone at `/home/zynergy-labs/Zynergy/zynergy-site` is on another branch with an untracked `.claude/`. Don't touch it.
- **New files:** `Forager/index.html`, `404.html`, `robots.txt`, `sitemap.xml`, `llms.txt`.
- **Edits:**
  - `_redirects`: two lines, and the comment at :1-2 that reserved `/forager` for this page;
  - `index.html`: the link only;
  - `README.md`: the pages table's `/forager` row (:39), the not-found paragraph (:46-50), and rows for the new files.
- **Not:**
  - `Forager/forecast/*`, `privacy/`, `delete-data/`, `welcome/`, `beta-signup/`, `functions/`, `db/`, `_headers`, `_routes.json`;
  - the site's main branch: open a PR, don't merge;
  - Cloudflare's dashboard and settings;
  - the Forager repo, which is read only for you;
  - RECORD.md and the audit index, which are the planner's.

## Baseline
zynergy-site main `fd77ab5`. Forager main `f637fba17` (1.0.5026). Check both against the remote before starting.

## Verify first
Report each in your final message, with file and line.

1. **The live site before your change,** read with curl. The planner read on 2026-11-10:
   - `/robots.txt` is Cloudflare's own file. It disallows AI training crawlers and says `Content-Signal: search=yes,ai-train=no,...`, with the home page's HTML appended after it, because the repo has no robots.txt.
   - `/sitemap.xml` and any made-up path return the home page with 200.
   - A request whose user agent names an AI training crawler gets 403 at Cloudflare's edge. Search and answer fetchers and ordinary search crawlers get 200. That was by user agent only; Cloudflare may also check where a request comes from.

   This is the "before" column of your evidence.
2. **What `404.html` at the root does on Cloudflare Pages.** Unmatched paths should get it, with status 404. Confirm that on the branch preview, and confirm that nothing that works today breaks:
   - `/`, `/privacy/`, `/delete-data/` and `/welcome/`;
   - `/Forager/forecast/`, one tile and `manifest.json` under its `data/`;
   - `/beta-signup` (302) and `/forager/forecast` (301);
   - `/api/beta-signup` (POST, 410);
   - `/functions/x` and `/db/x` (404 from the middleware).

   The README says every forecast tile inside its bounds exists ("The pilot demo's input layers"). Check that `Forager/forecast/app.js` handles a 404 tile as it handles an empty one, or report it.
3. **The GPX namespace.** The app writes `https://zynergy-labs.com/forager/gpx/1` into exported files (Forager `GpxCodec.kt:66`). The decoder keys on the `forager:` prefix, not this address (:59-64). After the change the address returns 404. Confirm nothing in the app fetches it.
4. **Every feature traced** to Forager main `f637fba17` (file and line) or to the live Play listing, in a table in your report, not on the page. Where the code and the listing disagree, or something is only partly true, say so and leave it off the page. Candidates to check, not facts:
   - recording walks as tracks;
   - logging finds with photos and notes;
   - waypoints;
   - offline maps by area;
   - the Trail Map with trails, relief and contours, and the other map types;
   - navigation by trail, with the next turn and the way back;
   - plotted routes and planned trips;
   - altitude above sea level;
   - the Seasonal tab;
   - the sundown warning;
   - night colours;
   - units;
   - GPX import and export;
   - backup and restore.
5. **Facts for the FAQ,** each read, not assumed:
   - **Price, ads and in-app purchases.** The listing's data read `"price":"0"` today, and the planner found no ads or in-app purchases mentioned.
   - **Android only, and the minimum version.** Forager `app/build.gradle.kts:265` has minSdk 26, which is Android 8.0.
   - **Without signal:** what works, and what needs maps downloaded first.
   - **Data:** where it is stored and what is sent, from `privacy/index.html` as published, with a link to it.
   - **Deleting data:** link `/delete-data/`.
   - **Coverage:** which regions the maps cover.
   - **Identification:** that the app does not identify species.

## Predictions
- **Outcome (planner):** on the branch preview:
  - `/Forager/` returns 200, with one h1, the four sections and the Play link.
  - Its JSON-LD parses, and the FAQPage text matches the visible FAQ word for word.
  - `/forager` and `/forager/` return 301 to `/Forager/`.
  - `/no-such-page` returns 404 with the not-found page.
  - `/robots.txt`, `/sitemap.xml` (four addresses, valid XML) and `/llms.txt` return 200 with their own content and suitable types.
  - The forecast demo and the privacy and delete-data pages are unchanged.

  After publishing, the live robots.txt is ours, with Cloudflare's own rules still put in front of it until the owner changes the dashboard.
- **Mechanism (coder):** in your first message, before building: what you expect each change to do and why, and your file list.

## Evidence required
- **A before-and-after table from curl:** every path above, with status, content type and a body marker specific to the page. Status alone proves nothing here, because today every unknown address returns 200 (README:46).
- **The JSON-LD** from `/Forager/`, parsed by a JSON parser. The FAQ comparison is done by a script, with its output pasted.
- **The HTML** parsed without errors by a standard parser; say which.
- **The trace table** from Verify first 4 and 5.
- **The privacy scan of your diff:** `git diff origin/main | python3 ~/.forager-private/scan.py stdin-diff`. First run it against a planted value to show it catches one. The scanner prints counts only; never print a value it finds. The result must read 0.
- **A render, if a headless browser is available on the laptop:** one phone-width render of `/Forager/` from the preview, viewed by you only. It shows only the page.

## Stops
Stop and report on:
- anything needing Cloudflare's dashboard, `functions/`, `_routes.json` or `_headers`;
- the not-found page breaking a path that works today;
- a privacy scan hit;
- an owner-level choice, such as wording the owner gave elsewhere that conflicts with the page;
- a wrong premise in this file;
- two failed attempts at one symptom.

Sentences that are your wording rather than drawn from a source (the problem statement, most likely) are not a stop. Mark them in your report for the owner to read.

## Finish line
A PR into zynergy-site main, open and unmerged, with its Cloudflare preview address and your report. The planner brings the preview to the owner. The page goes live only when the owner says publish, and the planner merges then.

## Final message
- Branch, commits, PR and preview address.
- Your mechanism prediction, from your first message.
- The evidence above.
- The page's text in full, so the owner can read it in chat too, with your own wording marked.
- What is not verified:
  - how search engines and AI tools index the page, which only time and their own tools show (the owner's search-engine accounts are the owner's);
  - the live site after the merge;
  - the dashboard change.
- Every call beyond this dispatch.

## Not in this dispatch: the owner's Cloudflare change
"Allow training too" is the owner's click in Cloudflare's dashboard. The planner gives the owner the steps separately. Don't attempt it, and don't write robots.txt rules about AI crawlers that would undo it.
