# Forager's own basemap, for a public launch and iOS: a plan

**Status: a plan for the owner's decisions. Nothing is built or dispatched.** Written by the planner (Rowan Waymark) on 2026-10-18, at pre-main `e61e248a`. Forager RECORD -1267, -1271.

**The owner, verbatim (2026-10-18):**
- "Let's draft a plan for the maps to be set up so we can get on it sooner than later. I expect a lot of users to sign up. Plus the iOS rollout will double that. So better on it now than later."
- "Basemap first, trail map side by side and have it finish when it lands. Basemap can roll out fast without a problem. It's the design that we will need to work on, so have Elder take a look at what needs to be done".

**Built on three reports, each read in full:**
- **Where the map service stands:** a read-only survey, summarised in -1271.
- **Juniper Sounding's policy and cost research,** quoted at source: `docs/audits/2026-10-18-map-services-policy-and-cost-research.md`.
- **Elder Ford's design review:** `docs/audits/2026-10-18-basemap-design-review.md`.

It refines the ruled spec (`2026-10-04-own-map-service-spec.md`, -492/-493, amended by -879/-880) and splits it in two. Every claim about the repository below is a premise for the dispatches to re-check (CLAUDE.md).

## 1. Why now

On a phone today, every map a user looks at loads from someone else's server. Only offline downloads come from the owner's Cloudflare (`Basemap.kt:113-132`, `BasemapStyles.kt:341`, `OfflineStyle.kt:18`):

| Map | Server | What its operators say (Juniper, quoted) | Risk at launch |
|---|---|---|---|
| **Topographical, the default** | OpenTopoMap, volunteers | "keinerlei Ausfallsicherheit" (no reliability guaranteed); downsized to a smaller vServer on 2026-01-05; retiring their university machine; asks larger users to get in touch | **Highest**: most traffic, smallest server |
| Street | OpenStreetMap's tile server | "may block access, without notice"; donation-seeking services "should be especially aware that access may be withdrawn at any point"; library-default User-Agents "will be blocked" | Blocked at an unknown volume |
| Labels on the app's own markers | MapLibre's demo font server | a demo and CI host; MapLibre's docs say "Dev only" | Small, but every install depends on it |

**Two code-level gaps Juniper found, which could be fixed in this release:**
- **(a)** If the app's network client fails to install, it falls back to MapLibre's default User-Agent (`ForagerApplication.kt:98-100`). That is the traffic OpenStreetMap says it blocks.
- **(b)** OpenTopoMap's attribution in the app (`Basemap.kt:122`) is shortened from the notice its page asks for.

**What the owner's Cloudflare can carry** (Juniper, quoted, and computed):
- Workers Paid is $5 a month for 10 M requests, then $0.30 a million.
- R2 is $0.015 per GB-month, with free egress.
- Paid Workers and R2 traffic is exempt from the CDN's "pictures" clause.
- At about 300 M requests a month the cost is about $144 (computed). The earlier survey estimated about $5 to $7 a month for 10,000 users.
- The offline host `*.workers.dev` can be renamed on a week's notice, while a custom domain cannot.
- Tiles and fonts need a cache rule; `.pbf` and `.json` aren't cached by default.

## 2. What exists (the survey, -1271)

**Live:**
- The Worker on `workers.dev` serves offline downloads from R2's `us.pmtiles`. That's a one-off cut of Protomaps' 2026-08-19 build, never refreshed, with z15 read live from Protomaps.
- The Worker's code still tries the Pi first, though -879 ruled the Pi out of serving. Today everything comes from R2.

**Built but not used:**
- `map-style/forager-light.json`, which equals Protomaps' light style. The website uses it; the app doesn't.
- The Pi's own Oregon and Washington build with trail attributes and BRouter data (4 October). It was never uploaded.

**Not built:**
- `tiles.zynergy-labs.com`;
- anything in the zynergy-labs Cloudflare account, including its paid plan (-879 says it's needed at launch);
- a font or icon route on the Worker;
- a labelled offline style. Today's offline maps have no labels at all (Elder).

**Who builds:** -904 gave stage 4 onward (the data pipeline) to a Pi planner session. Its last record is -604 (4 October), and the 11 October relay has no reply.

## 3. The plan: two tracks

### Track A: the basemap (first)

Forager's own vector Street map, online and offline alike, served from the owner's Cloudflare under the owner's domain.

**A0. Before the production release (small; the owner's call which):**
- **(1) Fonts served from the owner's Worker,** not MapLibre's demo server. They're OFL, with the licence file alongside.
- **(2) The User-Agent fallback fixed,** so the app never sends a library-default User-Agent.
- **(3) OpenTopoMap's credit in full,** as its page asks.
- **(4) Write to OpenTopoMap's maintainers** before launch, saying who we are and our expected traffic. This is the owner's email; I can draft it.

**A1. Serving.**
- The zynergy-labs account goes on Workers Paid. Create the Worker and R2 bucket there, with the custom domain `tiles.zynergy-labs.com` and cache rules for tiles, fonts and styles.
- **Retire** the Pi-first code path (-879).
- Add rate limits per the spec's R3.
- Old offline regions keep working on the old host until the owner's migration step (Elder's decision 11).

**A2. Data.** A US tile set refreshed on a schedule, built either way:
- **(a)** Protomaps' daily build cut to the US and copied to R2, which Protomaps asks for. Fast, but no trail attributes.
- **(b)** The Pi's own build, which adds the trail attributes and forager POIs. Slower, and it needs the Pi side running again.

**Recommended:** (a) for the basemap now, (b) to replace it when Track B lands.

**A3. Design.** Elder's ten launch blockers, in the review's order:
1. coverage;
2. paths visible from z12 and classed by kind;
3. a day palette chosen with its night inversion beside it, with the recolour extended to labels;
4. labels sized and contrasted for sunlight;
5. a forager's points of interest instead of a city's;
6. the woodland gap closed between z7 and z11;
7. fonts and icons on the owner's domain, inside offline packs;
8. the marker palette re-measured;
9. a zoom limit (18);
10. the default map.

All of this is style work in `map-style/`, reviewed on the owner's phone against Elder's checks.

**A4. The app.** Street becomes the own vector style online and offline, with one style URL, the night recolour walking text and icons, a zoom limit, and the credits screen updated.

**A5. Checks.**
- On the S22: Elder's phone checks 1 to 11.
- On the server: the same tile from a cold cache and a warm one, a burst refused, and the cost readout after a week of beta use.

### Track B: the trail map (alongside; finishes when it lands)

The spec's stages 4 to 9, unchanged in scope:
- the own build with trail attributes and routing data, from one extract per area;
- routes along trails and roads;
- off-trail and back;
- the Forest Service and BLM reference layer;
- the owner's walks.

When it lands, the basemap's tiles switch to its build (A2 b), so the paths gain their real kinds (sac_scale, visibility, informal). Elder's design already leaves slots for those (review section 4).

## 4. Decisions for the owner

**On infrastructure and timing:**
1. **The A0 items before release:** fonts on your Worker, the User-Agent fix, the full OpenTopoMap credit, and an email to OpenTopoMap. *Recommended:* all four. The first three are small code changes; the fourth is your email, and I'll draft it.
2. **The on-screen map from your own server.** This replaces the ruling of 2026-08-22: "the PMTiles Worker serves offline *downloads* only and is never a live tile server … a cost ceiling" (`docs/plans/pr26-rework.md:395-401`; lifted for the website only, -860). *Recommended:* yes, on the paid plan, with rate limits and a monthly cost readout.
3. **Where it's served from.** *Recommended:* the zynergy-labs account at `tiles.zynergy-labs.com`, as -481 and -879 ruled. That means turning on Workers Paid there.
4. **The data source for the basemap.** *Recommended:* Protomaps' US cut now (A2 a), and your own build when the trail map lands.
5. **Who runs the build side.** -904 gave it to a Pi planner session that has been silent since 4 October. *Recommended:* say whether that session continues, or the work comes back to me with Hazel coding. (Planning and coding stay separate either way.)

**On design** (Elder's list, decision numbers as in the review, each with a phone check):
6. **Coverage** (Elder 1): your map where it's built, and the old street map automatically elsewhere. *Recommended,* until the whole US is built.
7. **The default map** (Elder 2): your own Street, once its design passes. *Recommended.*
8. **Paths** (Elder 3): dashed red-brown footpaths, dash-dot vehicle tracks, dotted informal paths, from trailhead zoom. *Recommended.*
9. **Forest** (Elder 4): one solid green from the state view down. *Recommended.*
10. **Labels** (Elder 5): larger and near-black. *Recommended.*
11. **Points of interest** (Elder 6): trailheads, parking, campgrounds, picnic sites, viewpoints, peaks, toilets, water, ranger stations; shops only in towns. *Recommended.*
12. **Night** (Elder 7): keep pure inversion, design the day colours for it, and invert the labels too. *Recommended.*
13. **Zoom limit** (Elder 8): 18. *Recommended.*
14. **Topo at launch** (Elder 9): stays on OpenTopoMap, with your email (decision 1). *Recommended.*
15. **Satellite** (Elder 10): not at launch; when it returns, as a hybrid. *Recommended.*
16. **Old offline downloads** (Elder 11): keep working, with a one-line notice. *Recommended.*

## 5. Order and cost (estimates; inferred)

1. **A0,** in the production release now: a short dispatch.
2. **Then A1 and A2 a** (serving and data, about a day of work), **and A3** (the design, the longest part, with several phone review rounds).
3. **Then A4** (the app switch) **and A5** (the checks).
4. **Track B** runs beside them with its own dispatches, and joins at A2 b.
5. **Cost:** $5 a month plus requests, from about $5 to $7 at 10,000 users (survey) to about $144 a month at 300 M requests (Juniper's computation). R2 storage for a US tile set (about 18 GB estimated for the own build) is under $0.30 a month.

## 6. Not settled here

- **What the tiles carry:** whether Protomaps' tiles have the path kinds and forager points of interest that A3 needs (`kind_detail`, `leaf_type`, trailhead and similar). The first A3 step reads the schema. If they're missing, A3's paths and POIs wait for Track B's build.
- **Real requests per user;** the cost figures are estimates.
- **Whether the shortened OpenTopoMap credit was ever enough.** That's a licence question, not answered here; decision 1 makes it moot.
- **iOS:** this plan serves any client. The iOS app reuses the same style URL and server.

---

## Amendment 1, 2026-10-18 (evening): the owner's rulings since this plan was written

Written by the planner (Rowan Waymark) at the owner's word, "Go on the two items". **The text above is left as written.** Where this amendment and the text above disagree, this amendment holds. Every ruling is quoted in Forager's RECORD on branch `records-after-173`, and each one is cited by its ID here.

### Infrastructure and timing (section 4, decisions 1 to 5)

- **Decision 1, the A0 items:** "Code fixes only" (-1272), so there is **no email** in A0.
  - **Fonts:** removed, not served. The app's raster maps draw no text of their own, so the font request went (-1278 A).
  - **User-Agent:** fixed. A failed client install is a failed map start-up (-1278 B).
  - **OpenTopoMap's credit:** the short form shows in the caption and the full form behind the tap (-1278 C).
  - **A failed map start-up** shows "The map couldn't start." with Try again (-1283, -1284).
  - All of this is in PR #257 (dispatch 2026-10-18-04 and its Amendment 1). The S22 check passed (-1295), and the PR is not merged yet.
  - The email came back in at decision 9 below. See "A conflict to settle".
- **Decision 2, on-screen tiles from your own server:** yes. The 2026-08-22 ruling is lifted for the app (-1272), because "Cost was a factor then, now it isn't" (-1276).
- **Decision 3, where it's served from:** **not zynergy-labs.** The owner: "Zynergy-labs is the website and I intentionally created that to keep it separate from the Forager mapping service" (-1272).
  - It's served from the existing mapping account, the current `forager-pmtiles` Worker on `workers.dev` (`server/pmtiles-worker/wrangler.toml`).
  - A1's "zynergy-labs account goes on Workers Paid" and `tiles.zynergy-labs.com` are withdrawn.
  - A custom domain on the mapping account is still open.
- **Decision 4, data source:** Protomaps' US cut (A2 a), the whole US first (-1274).
- **Decision 5, who runs the build side:** the owner's Pi planner downloads and builds the maps, and the owner says when that's ready (-1281).
  - The laptop side, meaning this planner with Hazel coding, does what the app needs "to have proper maps tonight before release" (-1281). The owner: "Cloudflare needs to be front and center for the maps serving, not OSM".
  - The route there went through -1272, -1276 and -1280, all superseded by -1281.

### Design (section 4, decisions 6 to 16; Elder's numbering in brackets)

| Plan | Elder | Ruling | RECORD |
|---|---|---|---|
| 6 | 1, coverage | The whole US first, from Protomaps' US cut, so there's no fallback inside the US. | -1274 |
| 7 | 2, default | Street by default. Street uses the Cloudflare map **if it is ready and passes its phone check before the release build**; otherwise it ships on OpenStreetMap's raster tiles and moves to Cloudflare in a later update. Nothing switches while the app is in use. | -1282 |
| 8 | 3, paths | Elder's three styles: dashed red-brown footpaths, dash-dot brown vehicle tracks, dotted faint informal paths, from trailhead zoom. | -1284 |
| 9 | 4, forest | One solid green from the state view down; a lighter tone for grass and scrub; parks as an outline. Conifer and broadleaf shades come later. | -1285 |
| 10 | 5, labels | A little bigger than `forager-light.json`, near-black on a light halo. | -1286 |
| 11 | 6, points of interest | Elder's outdoor list from mid zoom; shops and cafes only when zoomed into a town. | -1287 |
| 12 | 7, night | Pure inversion, labels included, with day colours chosen to survive it. | -1288 |
| 13 | 8, zoom | Stops at 18. | -1289 |
| 14 | 9, Topo | Stays on OpenTopoMap at launch; write to its maintainers before launch; own contours and hillshade later. | -1291 |
| 15 | 10, satellite | Not at launch; when it returns, trails and labels go over the imagery. | -1292 |
| 16 | 11, old offline downloads | Keep working until re-downloaded, with a one-line notice in the list; no forced or automatic re-download. | -1294 |

### A conflict to settle

-1272 ruled A0 "Code fixes only", with no email to OpenTopoMap. -1291 (decision 9, option 1) rules "write to its maintainers before launch". The planner didn't point out the conflict when it put decision 9. -1291 is the later word, but it's taken to the owner rather than assumed.

### Not built yet

- **Street as the default.** `MapMode.DEFAULT` is still `TOPOGRAPHIC` (`MapMode.kt:42` on pre-main). The S22 check found Topo opening first (-1295). The behaviour is restated to the owner and awaits a yes.
- **Track A's A1 (serving), A3 (style) and A4 (app switch).** For tonight, decision 7's condition applies: the Cloudflare Street map ships only if it is ready and passes its own phone check before the release build.

### Premises re-checked for this amendment

- **Read:**
  - `MapMode.kt:36-42` on pre-main 712ff277;
  - the Worker's tile content types, `server/pmtiles-worker/src/index.ts:125-129`, which serve vector or raster archives alike;
  - its edge cache, `:340-365`;
  - `wrangler.toml`'s `name = "forager-pmtiles"`.
- **Not re-read:** the plan's other file and line references. The dispatches re-check them.
