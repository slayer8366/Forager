# Corrections to the MapLibre/PMTiles policy audit

**Date:** 2026-09-11
**Type:** correction record. **No code changed.**
**PARTLY CORRECTED 2026-09-11** by `2026-09-11-maplibre-pmtiles-policy-corrections-round-2.md`. Three claims below go past the evidence: **C2's OFL reserved-font-name note is withdrawn** (Open Sans in openmaptiles/fonts is Apache-2.0; the obligation is Apache, not OFL), **C4's "fall back to the z14 archive" is not a drop-in fix** (a z14 tile cannot answer a z15 request; the real option is to advertise `maxzoom: 14` and delete the overflow path), and **C1 lists three recommended items where the policy has four**, two of them code. C6's 29 components are an upper bound of 27 for Android. The prefetch item raised at the end is **settled and withdrawn** (`prefetchZoomDelta = 4` covers ground already on screen). C2's partial disagreement about the demotiles quote is **withdrawn by the owner** — the main-branch README matches verbatim.

**Corrects:** `2026-09-11-maplibre-pmtiles-policy-compliance.md`, same day.
**Origin:** the owner pulled the primary sources independently and returned six corrections. All
six checked here against the sources. **All six hold.** Two change a finding's severity; four
sharpen one without overturning it. Two new items surfaced during the checking, one of them a
defect the original audit missed entirely.

The original document is **superseded in place, not rewritten** — the two corrected findings carry
a pointer to this file and their original text is untouched. Same treatment the `CAMERA` row got in
`2026-09-08-data-inventory-for-privacy-policy.md`, and for the same reason: that document is an
action list someone will work from, and a wrong severity left sitting at the top of one is how it
comes back.

---

## C1. Finding #4 (User-Agent): downgraded from Medium to recommended-only

**The owner is right, and the way I was wrong is worth recording more than the fact of it.**

**What the source says.** `HttpIdentifier.java`, maplibre-native `main`:

```java
return String.format("%s/%s (%s)", context.getPackageName(), packageInfo.versionName, packageInfo.versionCode);
```

and `module/http/HttpRequestImpl.java:41-43, 76`:

```java
private static final String userAgentString = toHumanReadableAscii(
    ... HttpIdentifier.getIdentifier(), ...);
...
.addHeader("User-Agent", userAgentString);
```

So every MapLibre tile request already carries
`com.zynergylabs.forager.app/<versionName> (<versionCode>)`. The app is identified distinctly and
stably, which is what the policy asks for.

**Two errors, and the second is the serious one.**

1. I did not read the source.
2. I wrote, in the audit itself: "the finding does not depend on it." It did. That sentence is the
   whole defect — it let a Medium severity survive a gap I had *disclosed*, by asserting the
   conclusion was robust to a fact I had not established. Disclosing an unchecked assumption does
   not make the conclusion safe; it only makes the assumption look handled. This is `CLAUDE.md`'s
   "check reachability before measuring behaviour," applied to my own reasoning rather than to a
   code path: one `curl` of `HttpIdentifier.java` would have closed it, and I priced the finding
   instead.

**A third error, and this one was inside my own citation.** The text I quoted in the audit reads
"naming your app and **optionally** a contact URL or email." I quoted the word "optionally" and then
built the finding on the contact URL being the load-bearing requirement.

**The owner's section-location point, confirmed against the full policy text.** "Contactable" as a
*requirement* appears once, in §5 Caching proxies: "Set a clear, contactable User-Agent identifying
the organisation/service." §3.1 and §3.4 both make it optional for apps.

**Going further than the owner did, in the code's favour.** §3.4's prohibition is narrower than the
quick-summary bullet suggests. Its examples of what it targets are `okhttp/x.y`,
`Go-http-client/1.1`, `python-requests/x.y`, `Java/1.8`, `curl/x.y`, and its stated rationale is
"because we cannot identify or contact the actual application." MapLibre's default is neither
generic nor unidentifying, and §3.4 separately asks SDK publishers to "set a sensible default
User-Agent (naming the SDK and docs URL)" — MapLibre does better than asked, naming the *app*. The
strict-letter case rests entirely on the quick-summary bullet "rely on a library's default
User-Agent" read against §3.4's own definition, which narrows it.

**What actually survives**, none of it required, and all three missed by the original audit. From
the policy's own "You should (recommended)" list:

- "Avoid hard-coding the tile URL; allow switching without needing a software update."
  `Basemap.kt:171` hard-codes it as an enum constant.
- "Add a 'Report a map issue' link to `https://www.openstreetmap.org/fixthemap`."
- "Publish a contact email on your website or app store listing." A listing task, not a code one.

**Mechanical note, the owner's, confirmed.** Because `HttpRequestImpl` calls
`.addHeader("User-Agent", ...)` per request, any override must **replace** the header in an
interceptor (`Request.Builder.header()`, or `removeHeader` then add). `addHeader` would send two
User-Agent headers.

## C2. Finding #5 (glyphs): licence claim wrong, remedy unchanged

**The owner is right.** demotiles gh-pages README line 121:

> The font PBFs were generated using the scripts and source fonts from
> `https://github.com/openmaptiles/fonts`.

And that repository's README, under "## Font License":

> Please mind the license of the original fonts. All fonts are either licensed under OFL or Apache.

The audit said "the fonts are redistributable under the repo's BSD-3-Clause licence." Wrong.
demotiles' BSD-3 covers its own content; the font PBFs are third-party, OFL or Apache. Bundling
them therefore **adds** an obligation rather than being free of one, and it folds into finding #6
rather than sitting outside it.

Worth adding, since it bites on exactly this use: **OFL-1.1 carries a reserved-font-name
restriction** — a modified or renamed font may not keep the reserved name. Glyph PBFs are addressed
by fontstack name (`Open Sans Semibold`), so a bundling step that renames or re-generates them
needs checking against that clause, not just against the notice requirement.

**On the "not verbatim" half, a partial disagreement, stated because being accurate here is the
point.** The sentence I quoted is verbatim from the **main-branch** README, which reads in full:
"Demo vector tiles and map style for web, helloworld and CI tests @MapLibre. Hosted directly on
GitHub Pages, serverless, no keys." Two sentences in the source, presented as two. The **gh-pages**
README makes the same point in different words, split across a section. If the owner was reading
gh-pages, their read is correct about that file. Either way the substantive claim — the stated
purpose is demo, helloworld and CI — holds in both, and it is the licence half of this correction
that changes the work.

## C3. Finding #3 (no guard): framing corrected, severity unchanged

**The owner is right that "free tier: 1M" implied a wall.** It is a billing threshold. Cloudflare's
R2 pricing page: Class A `$4.50 / million requests`, Class B `$0.36 / million`, storage
`$0.015 / GB-month`, egress free; free tier 10 GB-month, 1M Class A, 10M Class B.

Recomputed, and their figures reproduce:

| Walk | Class A writes | Billable after 1M free | Cost |
|---|---|---|---|
| CONUS bbox | 15,427,594 | 14,427,594 | **$64.92** |
| Planet z15 | 1,073,741,824 | 1,072,741,824 | **$4,827** |

Plus Class B reads and storage: CONUS at a nominal 15 KB/tile is ~231 GB, about **$3.47/month**
ongoing once written.

**Their cache-key question, answered from the code: no, and the good news comes with a defect the
original audit missed.** `index.ts:193-195`:

```ts
function overflowCacheKey(name: string, z: number, x: number, y: number, ext: string): string {
  return `overflow/${name}/${z}/${x}/${y}.${ext}`;
}
```

Date-free. A new daily build does **not** re-pay the writes, so that cost concern does not bite.
But the same property means **an overflow tile is frozen at whatever build first served it, with no
invalidation path anywhere in the Worker.** Over time the z15 overflow layer silently diverges from
both the z14 base archive beneath it and from OSM itself, and nothing in the code or the response
says which build a given tile came from. That is a correctness finding, surfaced by a question
asked about cost.

**Their load point stands and is the operative one.** A bbox check removes most of the money
exposure and none of the load on Protomaps. #2 and #3 need the same real fix.

## C4. Finding #2 (hotlinking): strengthened by an independent failure mode

**The owner is right.** Protomaps' downloads page states the builds bucket keeps:

> All builds for the past week. The latest build for each patch version (e.g. `4.3.0`).

`BUILD_RESOLUTION_LOOKBACK_DAYS = 5` sits inside that week, so it resolves today. The failure is
concrete rather than theoretical: if publishing pauses past five days, or the retention window
tightens, `resolveRemoteBuildUrl` throws a plain `Error`. The handler at the bottom of `fetch`
catches only `KeyNotFoundError` before `throw e`, so the Worker returns a **500**, not a clean 404
and not a graceful fall back to the z14 archive it already has.

So the path has two independent ways to fail — the policy one and this one — and neither is
mitigated in code.

## C5. Finding #1 (BSD-3): scope condition accepted, and both conditions are met

**The owner's statement of the rule is correct**: clause 1 binds redistribution of source, and
deploying a Worker is not redistribution.

Both conditions hold here.

1. The files contain adapted upstream code, by their own admission — `index.ts:10` says "Adapted
   from protomaps/PMTiles serverless/cloudflare/src/index.ts", `shared.ts:2` says "Copied from".
2. **`slayer8366/Forager` is public.** Confirmed against the GitHub API this session:
   `"private": false, "visibility": "public"`.

So the source is shared with third parties every time someone clones or browses the repository.
Severity unchanged; the reasoning is corrected to rest on repository visibility rather than on
deployment, which was never the trigger.

## C6. Finding #6 (notices): the owner adds a real gap

**Confirmed.** `LICENSES.core.md` in maplibre-native is 62,955 bytes, 1,347 lines, and carries
**29 component sections** beyond MapLibre Native's own BSD-2: FreeType, HarfBuzz, Boost, RapidJSON,
protozero, earcut.hpp, wagyu, JSON for Modern C++, metal-cpp, FSST, FastPFOR, SIMD Everywhere and
others.

These are C++ dependencies compiled into the native library inside the APK. A Gradle licence plugin
reads the resolved Maven graph, where `org.maplibre.gl:android-sdk` appears as one artifact with
one licence, so it will miss all 29. FreeType in particular ships under the FTL, which carries its
own credit requirement distinct from BSD-2's.

The original audit's remedy — "Google's `oss-licenses` Gradle plugin, which generates one from the
resolved dependency graph" — is therefore **insufficient on its own**, exactly as the owner says.
`LICENSES.core.md` has to be carried into the notices screen by hand alongside whatever the plugin
produces.

## C7. ODbL: the owner is right that it is less open than I framed it

Protomaps' downloads page gives a stated position that can simply be adopted and cited: the basemap
is distributed as an ODbL **Produced Work**, OpenStreetMap attribution required. The audit's framing
("genuinely contested and this audit does not resolve it") overstated the difficulty of *acting*,
even if the underlying legal question is real.

The owner's second point is the practical one: under the Derivative Database reading, an unmodified
extract costs very little to comply with, because the source build and the exact extract command
are both already recorded in `server/pmtiles-worker/README.md`. Pointing at them discharges the
share-alike obligation without building anything.

---

## New item, raised at low confidence and explicitly not asserted

**Prefetch.** The full policy text names it where my original summary did not. §4 quick summary:
"You must not: Bulk download ('scrape') tiles **or offer prefetch features**."

MapLibre Native has a prefetch-zoom-delta mechanism that requests lower-zoom parent tiles. The app
configures none (searched `app/src/main` for `prefetch` and `PrefetchZoom`: no hits outside offline
tile-count comments), so a library default applies.

§4's permitted list allows "Normal interactive viewing by a human where the client requests only
the tiles needed for the current viewport (**with modest, short-range look-ahead typical of
browsers**)," which parent-tile prefetch for the current viewport plausibly is.

**I could not confirm the default value from the maplibre-native source this session and am not
asserting a violation.** Raised only because the policy names prefetch explicitly, the original
audit did not, and — per C1 — a fact I have not established is not one to build a severity on.

---

## Revised priorities

Adopting the owner's ordering, with the two new items placed:

1. **#3 bbox check** — same-day stopgap. Removes the money exposure. Does not touch the load.
2. **#2** — the decision point. Extract z15 into R2 and delete the overflow path, or get Protomaps'
   agreement. Carries the 500-on-resolution-failure fix (C4) and the frozen-tile invalidation
   question (C3) with it.
3. **#1** — correct three comments, add the notice. Reasoning now rests on the repo being public.
4. **#5 + #6 together** — bundle the glyphs, and carry OFL/Apache font notices plus
   `LICENSES.core.md`'s 29 components into a notices screen. The plugin alone will not do it.
5. **#4** — optional. Three recommended items, of which the hard-coded tile URL is the only code one.
6. **ODbL** — adopt Protomaps' stated Produced Work position and cite it. Low cost.

## Disclosure

**Confirmed by fetching the primary source this session:** `HttpIdentifier.java`'s format string and
`HttpRequestImpl.java`'s per-request `addHeader`; the full OSMF policy text including §3.1, §3.4 and
§5 and the "optionally"/"contactable" split; demotiles gh-pages README line 121 and openmaptiles/
fonts' "OFL or Apache" line; Cloudflare R2's four prices and three free-tier figures; Protomaps'
one-week retention sentence; `LICENSES.core.md` at 29 components; `slayer8366/Forager` public via
the GitHub API; and `overflowCacheKey`'s date-free key read from this tree.

**Computed here:** the two cost figures, from the tile counts in the original audit and R2's
published rates.

**Could not be determined:** MapLibre Native's default prefetch-zoom-delta value, which is why the
prefetch item is raised rather than filed as a finding.

**What this correction does not change:** findings #2, #3 and #6 keep their severity, and the
original audit's "what is already right" section stands unaltered — offline downloads still touch no
third-party raster host, and the attribution handling still exceeds what these policies require.
