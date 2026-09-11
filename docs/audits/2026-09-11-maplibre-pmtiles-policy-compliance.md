# MapLibre and PMTiles policy compliance

**Date:** 2026-09-11
**Type:** compliance audit. **Not a dispatch. No code changed.**
**Base:** `claude/ios-port-feasibility-mvsjcr`.
**Question asked:** whether the MapLibre and PMTiles coding structure is in line with those
projects' policies.

Scope: the licences and usage policies of MapLibre, PMTiles/Protomaps, and the tile and glyph
hosts this code actually fetches from. Every policy sentence quoted below was fetched during this
audit, not recalled. Every code claim names a file and line.

---

## Summary

Six defects, of which two are licence-compliance failures and two are usage-policy failures
against a named third party's published position. Against that: the single most serious violation
available here, bulk-downloading OSM raster tiles for offline use, **is not present**, and the
attribution handling is better than most shipped apps manage.

| # | Finding | Class | Severity |
|---|---|---|---|
| 1 | Upstream PMTiles code credited as MIT; it is BSD-3-Clause, and the required notice is absent | Licence | High |
| 2 | `build.protomaps.com` hotlinked per-tile from the production request path | Usage policy | High |
| 3 | The overflow path has no bbox, auth, rate or origin guard | Abuse surface | High |
| 4 | Library-default User-Agent against `tile.openstreetmap.org` | Usage policy | Medium |
| 5 | Glyphs served in production from `demotiles.maplibre.org` (GitHub Pages, CI/demo host) | Usage policy | Medium |
| 6 | No open-source licence notices anywhere in the shipped app | Licence | Medium |

---

## 1. The upstream PMTiles code is BSD-3-Clause, not MIT, and the notice is missing

**Where the claim is made.** Three places say MIT:

- `server/pmtiles-worker/src/index.ts:10` — "Adapted from protomaps/PMTiles
  serverless/cloudflare/src/index.ts (MIT licensed)"
- `server/pmtiles-worker/src/shared.ts:2` — "Copied from protomaps/PMTiles serverless/shared/index.ts
  (MIT licensed)"
- `server/pmtiles-worker/README.md` — "(`protomaps/PMTiles` repo, `serverless/cloudflare/`, MIT licensed)"

**What upstream actually says.** `https://raw.githubusercontent.com/protomaps/PMTiles/main/LICENSE`,
fetched 2026-09-11:

> The below license (BSD-3) applies to the reference implementations in this repository.
> The PMTiles specification itself is public domain, or CC0 where applicable.
> [...] Copyright 2021 Protomaps LLC

This project's own lockfile already records it. `server/pmtiles-worker/package-lock.json`, the
`node_modules/pmtiles` entry: `"version": "4.5.0", "license": "BSD-3-Clause"`. The correct answer
was sitting in the repository the whole time.

**Why it matters beyond the label.** BSD-3-Clause condition 1 requires that redistributions of
source code "retain the above copyright notice, this list of conditions and the following
disclaimer." `index.ts` and `shared.ts` are redistributions of source code, modified. Neither
carries the Protomaps copyright line, the three conditions, or the warranty disclaimer. MIT and
BSD-3 are both permissive, so the practical exposure is low, but the obligation is not discharged
and the stated licence is wrong.

Worth separating cleanly, because the two halves have different answers: **the PMTiles format is
public domain / CC0 and carries no obligation at all.** Only the reference implementation this
Worker was adapted from is BSD-3.

**Fix.** Correct the three comments, and add the Protomaps copyright notice, conditions and
disclaimer to both files or to a `NOTICE` beside them.

## 2. The z15 overflow path hotlinks Protomaps' daily builds, which Protomaps discourages

**What the code does.** `server/pmtiles-worker/src/index.ts`:

- `candidateBuildUrl` (line ~152) constructs `https://build.protomaps.com/YYYYMMDD.pmtiles`.
- `resolveRemoteBuildUrl` (line ~169) probes that host with `HEAD` across a five-day lookback,
  caching the resolved URL for six hours.
- `overflowTileResponse` (line ~200) opens a `FetchSource` against it and issues a **PMTiles range
  read per tile** on every R2 cache miss, then writes the tile into R2.

This runs in the live request path, not in a build step.

**What Protomaps says.** `https://docs.protomaps.com/basemaps/downloads`, fetched 2026-09-11:

> Please note that **URLs may change** and hotlinking to these downloads are discouraged. Instead,
> you should copy the tileset to your own Cloud Storage.

That is the exact pattern the overflow path implements, and the dated-filename probing exists
precisely because the URLs do change, which is the first half of the same sentence.

**The code already asked this question and did not get an answer.** `index.ts:139-146` says, in a
block headed NOT VERIFIED AGAINST REAL INFRASTRUCTURE:

> Protomaps' tolerance for *sustained per-tile production traffic* against their public daily
> build host [...] Worth confirming with them directly before this is relied on at real scale.

It is now confirmed, from their documentation rather than from correspondence: discouraged. The
honest reading is that this was flagged as an open question by whoever wrote it and the question
was never closed, not that it was overlooked.

**Not a finding, stated so it is not swept in with one:** the bulk `pmtiles extract` in
`server/pmtiles-worker/README.md` is the *approved* workflow. It copies the tileset to the
project's own R2, which is exactly what the quoted sentence asks for. The extract is fine. The
per-tile live fetch is the problem.

**Fix.** Either extend the local archive to z15 for the regions that need it and delete the
overflow path, or extract a z15 archive once into R2 by the documented route. If the live path is
kept at all, it needs Protomaps' explicit agreement first.

## 3. The overflow path has no guard of any kind

**The gate is zoom-only.** `index.ts:320`:

```ts
if (tile[0] > pHeader.maxZoom && tile[0] <= OVERFLOW_MAX_ZOOM) {
```

Searched for a bbox check, an auth token, a rate limit, a `Referer` check, a `cf-connecting-ip`
check: none present. `wrangler.toml` sets `ALLOWED_ORIGINS = "*"`.

**What that exposes.** The gate admits any z15 tile on Earth, not just the continental-US archive's
footprint:

- Planet at z15: **1,073,741,824** tiles.
- The CONUS bbox alone (`-124.85,24.40,-66.87,49.60`, from the README's own extract command):
  5,278 x 2,923 = **15,427,594** tiles.

Each uncached one is a range read against `build.protomaps.com` plus an `env.BUCKET.put`. R2's free
tier is 1,000,000 Class A operations per month; the CONUS bbox alone is 15.4 M writes, and at a
nominal 15 KB per tile roughly 231 GB of storage. A single scripted walk turns finding 2 into
sustained traffic against a third party and an unbounded bill on the owner's account at the same
time.

**The comment reasons about the intended caller, and the endpoint enforces none of it.**
`index.ts:135`:

> `OfflineTilePyramidRegionDefinition` only ever requests tiles inside the bbox a user downloaded,
> so that's exactly what ends up cached

True of this app's own well-behaved client. Not true of the endpoint, which is public, unauthenticated
and origin-open. This is the shape `CLAUDE.md`'s own pitfalls keep returning to: a property
established about one thing and relied on for another, with nothing in between to enforce it.

It also sits against a standing rule in `CLAUDE.md`: "A device- or API-reported capability range
describes what's possible, not what's safe to use. Apply an explicit operating limit rather than
trusting the reported range as-is." The overflow path trusts `remoteHeader.maxZoom` and applies no
operating limit of its own.

**Fix.** A bbox check against the archive's own header bounds is the cheapest and closes most of
it. A per-IP rate limit and a tightened `ALLOWED_ORIGINS` close the rest.

## 4. `tile.openstreetmap.org` is called with a library-default User-Agent

**Where.** `app/src/main/java/com/zynergylabs/forager/app/ui/map/Basemap.kt:171` sets
`tileUrlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"` for the `OSM_STANDARD` basemap.

**What the app sends.** Nothing of its own. Searched `app/src/main` for `User-Agent`, `userAgent`,
`HttpRequest`, `HttpRequestUtil`, `ModuleProvider` and `OkHttpClient` outside the iNaturalist and
Open-Meteo clients: zero occurrences. This matches the 2026-09-10 Data safety verification, which
independently counted `addHeader` and `User-Agent` at 0. So whatever MapLibre Native Android sends
by default is what the OSMF sees.

**What the policy requires.** `https://operations.osmfoundation.org/policies/tiles/`, fetched
2026-09-11:

> Apps must configure a distinct, stable User-Agent naming your app and optionally a contact URL or
> email. [...] **Do not use a library default User-Agent**, and never impersonate another app or a
> browser.

Example given: `User-Agent: MyTownMaps/1.4 (+https://example.org; contact: maps@example.org)`.

**What this audit could not confirm:** the exact string MapLibre Native Android sends by default.
An iOS example found in MapLibre's own discussions does include the host app's name, so Android's
may too. The finding does not depend on it. The policy asks the app to *configure* one, and no
library default carries a contact URL, which is the part a blocked app needs in order to be
unblocked.

**Fix.** `HttpRequestUtil.setOkHttpClient` is MapLibre Android's documented hook; an interceptor
setting a Forager-specific UA with a contact URL is a few lines and covers every raster host at
once, OpenTopoMap included.

## 5. Production glyphs come from MapLibre's CI and demo host

**Where.** `app/src/main/java/com/zynergylabs/forager/app/ui/map/BasemapStyles.kt:154`:

```kotlin
internal const val GLYPHS_URL_TEMPLATE = "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf"
```

That file's own class doc records that glyphs are set on **every** online style, because
`SightingsMap`'s numbered area markers use a `SymbolLayer` with a `text-field` and a style with no
glyphs URL renders it as nothing, silently. So this is on the live path for every raster basemap.

**What demotiles is for.** Its own README
(`https://raw.githubusercontent.com/maplibre/demotiles/main/README.md`, fetched 2026-09-11):

> Demo vector tiles and map style for **web, helloworld and CI tests** @MapLibre. Hosted directly on
> GitHub Pages, serverless, no keys.

A shipped Android app's font endpoint is none of those three, and GitHub Pages carries its own
bandwidth limits and terms that the MapLibre project, not this project, would answer for.

**No separate usage policy document exists**, so this is not a violation of a written rule the way
findings 2 and 4 are. It is a production dependency on infrastructure whose stated purpose excludes
it, with no availability commitment and a third party absorbing the cost.

**The remedy is in the same README**, which is why this is the cheapest of the six to fix:

> For offline use you can download the [.zip] including the font and viewer.

The fonts are redistributable under the repo's BSD-3-Clause licence
(`https://raw.githubusercontent.com/maplibre/demotiles/gh-pages/LICENSE`, Copyright (c) 2021
MapLibre). Bundle the `Open Sans Semibold` glyph range as an asset, or serve it from the Worker
already being run, and carry the BSD-3 notice with it. Bundling also removes a network dependency
from a field app that is otherwise carefully built to work without one, which is the reason to do
it even setting the policy question aside.

## 6. The app ships no open-source licence notices

Searched `app/src/main` for a licences screen or any BSD/Apache notice text: none.

MapLibre Native is **BSD-2-Clause** (`LICENSE.md`, "Copyright (c) 2021 MapLibre contributors,
Copyright (c) 2018-2021 MapTiler.com, Copyright (c) 2014-2020 Mapbox"). Condition 2 requires that
binary redistributions "reproduce the above copyright notice, this list of conditions and the
following disclaimer in the documentation and/or other materials provided with the distribution."
A Play-distributed APK bundling the SDK is such a redistribution.

The same obligation attaches to `mil.nga:mgrs` and, via Apache-2.0's own notice requirements, to
OkHttp, Retrofit, and the AndroidX stack.

This is ordinary and widely under-done, and the standard remedy is small: a licences screen, or
Google's `oss-licenses` Gradle plugin, which generates one from the resolved dependency graph.

---

## What is already right

Stated plainly, because an audit that only lists faults misrepresents the code.

**Offline downloads never touch a third-party raster host.** This is the finding that matters most,
because bulk-downloading OSM tiles is the single most serious thing an app in this shape can do,
and the OSMF policy names it first: "Bulk downloading is any pre-emptive fetching of tiles other
than those a user is actively viewing. Examples include pre-seeding areas, **building offline
archives**."

Traced rather than assumed: `MapLibreOfflineMapRepository.kt:97-98` constructs
`OfflineTilePyramidRegionDefinition(OFFLINE_STYLE_URL, ...)`, and `OfflineStyle.kt:18` points that
at this project's own Worker. The served style
(`server/pmtiles-worker/src/offline-style.json`) declares exactly one source, the project's own
`us.json`. Parsed it: no `glyphs` key, no `sprite`, no raster source. So an offline region download
reaches Protomaps-derived vector tiles in the project's own R2 and nothing else. No OSM raster, no
OpenTopoMap, no demotiles fonts.

**Attribution is handled deliberately and well.** `SightingsMap.kt:378` leaves MapLibre's own
attribution control enabled and repositions it to bottom-end; `SightingsMap.kt:533-537` adds an
always-visible Compose caption on top, and `Basemap.kt:103-112` records why the tap-to-reveal
control alone was judged insufficient. `OfflineStyle.kt:20-27` carries `Protomaps © OpenStreetMap`
for the offline style specifically, with the reasoning that a map drawing Protomaps geometry under
an OpenTopoMap credit would be crediting the wrong source. Against the OSMF requirement, "Show
OpenStreetMap licence attribution clearly on the map (typically bottom-right)" and "Do not hide
attribution beneath UI, behind toggles, or off-screen": met, in the harder of the two available
ways.

**The Worker keeps no tile logs**, and `wrangler.toml` sets `[observability] enabled = false`
explicitly rather than relying on a platform default, with a comment saying why. That is a privacy
commitment kept in code.

**The bulk extract workflow is the documented one**, as noted in finding 2.

---

## Open questions, not findings

These are raised because they are real and unresolved, not asserted as defects.

**ODbL share-alike on serving vector tiles.** Protomaps' downloads page says the basemap is
"distributed as an Open Database License Produced Work (OpenStreetMap attribution required)."
Serving rendered raster images is uncontroversially a Produced Work. Serving **MVT vector tiles**,
which is what this Worker does, is argued by some to be distribution of a Derivative Database
instead, which would trigger ODbL's share-alike clause and require offering the derived database
under ODbL. The interpretation is genuinely contested and this audit does not resolve it. If the
tile endpoint stays public, it is worth a stated position.

**OpenTopoMap's attribution is shortened.** Their about page requires "Kartendaten: ©
OpenStreetMap-Mitwirkende, SRTM | Kartendarstellung: © OpenTopoMap (CC-BY-SA)". `Basemap.kt:159-164`
uses "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)" and documents the reason: the full form wrapped
to two lines over the always-visible caption. It names all three required credits and the licence.
It collapses the data-versus-rendering distinction the German form draws. A defensible judgment
call, already recorded in the code, flagged here only so it was considered rather than missed.

**`a.tile.opentopomap.org` is pinned to one subdomain** (`Basemap.kt:162`), concentrating load on a
single host of a volunteer project whose policy asks only that the server not be overstrained.

**`pmtiles: "^4.3.0"`** in `server/pmtiles-worker/package.json` is a caret range against
`CLAUDE.md`'s own rule to "pin dependency versions [...] rather than open ranges, so a build is
reproducible." The lockfile resolves 4.5.0, and Cloudflare Workers Builds runs `npx wrangler deploy`
on push, so what actually deploys depends on whether that build honours the lockfile. The
devDependencies carry carets too.

---

## Disclosure

**Confirmed by fetching the policy and reading the code:** every quoted policy sentence (OSMF tile
usage policy, Protomaps downloads page, demotiles README and LICENSE, protomaps/PMTiles LICENSE,
maplibre-native LICENSE.md, OpenTopoMap about page), all file and line references, the absence of
User-Agent configuration, the absence of any bbox/auth/rate/origin guard on the overflow path, the
absence of a licences screen, the offline style's single source and missing `glyphs` key, and
`package-lock.json` recording `pmtiles@4.5.0` as BSD-3-Clause.

**Computed here:** the z15 tile counts (1,073,741,824 planet; 15,427,594 over the README's CONUS
bbox) and the ~231 GB figure, which assumes a nominal 15 KB per tile and is an order-of-magnitude
illustration, not a measurement of this archive.

**Could not be determined:** the exact default User-Agent MapLibre Native Android sends, which
finding 4 does not depend on; whether Cloudflare Workers Builds installs from `package-lock.json`
or re-resolves the caret ranges; and the correct ODbL treatment of served MVT.

**Premises worth correcting for a reader:** the most serious available violation, bulk offline
downloading of third-party raster tiles, is **not** present here, and the code anticipated the
Protomaps question in finding 2 before this audit did. Neither the format nor the attribution
handling is at fault anywhere in this document. The defects are concentrated in two places: how the
upstream code's licence is credited, and what the Worker's overflow path is allowed to reach.

**Decided beyond scope:** nothing. No code changed.
