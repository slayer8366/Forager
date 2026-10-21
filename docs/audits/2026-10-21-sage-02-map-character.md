Filed by the Pi planner, Aspen Meridian, as received. Sage ran read-only, from the brief
`prompts/preserved/2026-10-21-sage-02.md`, and wrote no repository files. Phase 1 only: nothing
was rendered. The owner chose "Previews first (Recommended)", so the owner's rulings wait for the
phase-2 previews after the running rebuild.

**The planner's spot-checks:** `forager-street.mjs:305` on `street-cloudflare` lists no `wetland`
or `bare_rock`; `:115-125` hold `OUTDOOR_KINDS`, `DOT_KINDS`, `TOWN_KINDS` and
`OUTDOOR_MIN_ZOOM = 12` as cited. A decoded Alpine Lakes tile, z13/1332/2866 of the live archive,
has a `pois` layer whose keys include `elevation`. Nothing else was re-checked.

# Sage Wayfinder: what would give the basemap character (brief 2026-10-21-sage-02, phase 1, no renders)

Read-only, proposal only. The brief is saved verbatim at `/tmp/sage02-brief.md` (36 lines). Scratch is `/tmp/sage02-scratch/` (a tile helper, a copy of `mvt_decode.py`, three classes unzipped from the basemap jar for `strings`, and the fetched style and RECORD files). Nothing was written anywhere else, nothing committed, nothing uploaded, no render, browser, build or map engine run. Memory held: single `pmtiles tile` reads and small Python, well under 300 MB. `/srv/forager-build/` was touched only by reading the archive named in the brief; the running build, its unit and logs were not. One thing outside my scratch: the harness's WebFetch saved the public-domain USGS symbol key PDF under its own tool-results folder; I did not put it there and cannot remove it.

Claims are marked **read** (file:line), **observed** (a tile I decoded, with its z/x/y), **inferred**, or **web** (URL, read 2026-10-21). "Not rendered" applies to every visual claim in this report.

Bases checked: `origin/pi-build` `b014eca2b` (the brief's base, unchanged at fetch); `origin/pre-main` `869273204` (CLAUDE.md there and the worktree copy agree on the first Known pitfall, which is obeyed); `origin/street-cloudflare` for `forager-street.mjs` (Street v1 with Sage-01's trail layers built in, 90 layers); `origin/records-after-173` `722c7e090` for RECORD.md and the elevation-sizing note.

## The one fact that reorders the brief

**The archive goes to z15 and carries the whole outdoor POI list itself** (observed: `pmtiles show` reports max zoom 15; Paradise z15/5303/11553 carries trailhead ×2, viewpoint, toilets, drinking_water, ranger_station). The Part 1 report's "z15 only from the live overflow" caveat (read: `part1-report.md:101`) no longer applies once this archive is served. Everything below that says "from z15" is from the owner's own bytes.

## Premises in the brief that needed refining

1. **Trail names are already designed in: Street v1 draws them from z14** (read: `forager-street.mjs:135-139`, `MINOR_ROAD_LABEL_MIN_ZOOM = 14`; the pinned `roads_labels_minor` filter includes `path`, read: `forager-light.json`, layer `roads_labels_minor`). The tiles carry path names only from z14 (read: `0001-roads-trail-attributes.patch`, "Names keep their zoom (minzoomName 14)"; observed: Forest Park interior z13/1303/2929 has 62 paths and 0 names; z14/2605/5857 has 14 paths, 13 named, "Wildwood Trail", "Maple Trail"; Paradise z14/2651/5776 32 paths, 19 named). So the owner's screenshot with no names on trails was either at z13, where no name exists in any tile, or at z14+ with the labels dropped by collision. **Could not determine which** without the screenshot; a phase-2 preview at Forest Park z14 and z15 answers it.
2. **Wetland and bare rock are not drawn at all today.** Street v1's `landuse_park` filter lists tree kinds, grass kinds, glacier, sand, military, naval_base, airfield (read: `forager-street.mjs:305`); `wetland` and `bare_rock` are not in it, and the pinned light style has no wetland layer either (observed: zero matches for "wetland" in `forager-light.json`). They draw as the ground colour. The data is dense where it matters (section C2).
3. **Park, forest and wilderness names are not drawn today.** They are in the tiles as `pois` points with kinds `nature_reserve`, `forest`, `national_park`, `protected_area`, `park` (observed: z7/20/45 carries "Mount Hood National Forest" min_zoom 8, "Mount Rainier National Park" 8; z9/82/180 "Mount Rainier Wilderness" 9, "Tatoosh Wilderness" 10). Street v1's `pois` filter admits only `OUTDOOR_KINDS` and `TOWN_KINDS` (read: `forager-street.mjs:115-123, 371-375`), none of those kinds, so the names are filtered out. Elder listed this as a follow-on (read: `elder-review.md:68`).
4. **Forest by leaf type: the data is not there.** The archive's `landuse` layer carries exactly `kind` and `sort_rank` (observed: `pmtiles show --metadata`, `vector_layers`), and the basemap profile's documented landuse fields are `kind` and `sport` (web: `https://docs.protomaps.com/basemaps/layers`). `strings` on the jar's `Landuse.class` shows `wood`, `forest`, `meadow`, `wetland`, `bare_rock`, `natural` and no `leaf_type` (observed: `/opt/forager-build/basemap/protomaps-basemap-HEAD-with-deps.jar`). Likewise `water` carries no `intermittent` field in this archive (observed: metadata; the profile documents one, web as above, so it was never emitted for this input, inferred).
5. **The sprite has 70 icons, not 53** (web: `raw.githubusercontent.com/protomaps/basemaps-assets/028c18f7/sprites/v4/light.json`, 70 entries, none SDF); the app's inversion list names 53 (read: `StreetStyle.kt:83-92`). Still none for trailhead, parking, camp_site, picnic_site, viewpoint, ranger_station, and none that is a tree-texture tile.
6. The OSM wiki was unreachable (503 on three tries), so anything about OSM tag usage below is inferred, not web.

## Prior art (generic; what each convention protects)

- **The national topographic series of this country** (web: `pubs.usgs.gov/gip/TopographicMapSymbols/topomapsymbols.pdf`, read from the saved copy, pages 1-4): woodland is a **flat green tint with no tree marks**; shrubland a fine dot pattern; orchard and vineyard regular dot grids; mangrove a blue scribble; marsh or swamp **small blue tufts on a pale blue ground**, "wooded marsh" the same tufts under the green tint; perennial streams solid blue, intermittent streams **dash-dot blue**; contours brown, index contours wider and labelled; spot elevation "× 7523"; trail a thin dashed black line; trailhead a boxed "TH" symbol on the Forest Service edition. What it protects: tint says *what covers the ground*, pattern says *how it is covered* (dense, sparse, wet), and the two never compete with linework. The absence of tree glyphs is deliberate: at topographic scale a glyph carries no information the tint does not, and it costs contrast under the contours.
- **The web street map the app's Street raster is today** draws pale green woods **with tree symbols** (read: `elder-review.md:17`). That is where the owner's eye has been trained, and "seems empty without those marks" is the honest reaction to losing them. What the glyph protects there: a street map has no contours and no hillshade, so the glyph is the only thing that says "trees" rather than "park lawn". That is exactly Street v1's case.
- **Outdoor and hiking apps** (read: `elder-review.md:89`): tree cover one solid mid green, a lighter tone for grass, **a pattern for wetland**; contours brown with labelled index lines; hillshade subtle; trailheads, parking, campsites, viewpoints and water sources the first POIs at z12-13; **peaks with spot heights** is the norm on every alpine sheet I know (inferred).
- **Swiss-style national sheets** draw woods as a tint with a scattered round-tree glyph at the larger scales only, dropping it at small scales (inferred from memory; not re-read today).

The conventions agree on: wetland gets a pattern; streams are blue and distinct from lakes; peaks carry a height; names of large areas appear at the zoom where the area fills the screen. They disagree only on tree glyphs, and the disagreement is by map type: street maps yes, topo sheets no.

## The ideas, each with what you would see, the data, the cost, and the question

### 1. Tree marks for forests (the owner's)

**What a person would see (not rendered).** From z12, the forest green `#a9d39b` carries a sparse scatter of small dark-green tree glyphs, about one 7-8 px mark per 28 px cell at 1×, screen-aligned, so the texture stays the same size as you zoom and the forest simply shows more of it. Below z12 (state and county views) the tint stays flat: a texture there is noise, and every topo sheet drops it at small scales. Grass, scrub and meadow carry no glyph, so "trees" and "open" read apart at a glance, which is the single distinction a forager wants from land cover. Trails, tracks, the purple track and the magenta find stay on top; the glyph is a mark *in* the fill, drawn below every line layer.

**Colour, day and night (inferred arithmetic, the app's formula, `NightColour.kt:39-47`).** Glyph `#3e7a3a`, the park-outline green already in the palette (read: `forager-street.mjs:46`): 3.08:1 on the day forest, 3.90 on ground. Night inverts to `#9cd898`: 3.65:1 on the night forest `#436d35`. A lighter glyph such as `#5f9a52` (2.00 by day, 2.70 at night) would read as a texture rather than a mark; the choice is a phase-2 swatch. Night needs the pattern image inverted the way the app inverts icons, by name (read: `NightColour.kt:73-74`, `StreetStyle.kt:80-83`), so the image's name joins that list: an app change of one line, inferred.

**Does our data carry it?** Yes, fully. `landuse` kinds `wood` and `forest` at every zoom from z2 (observed: z7/20/45 400 forest + 277 wood of 934; Forest Park z13/1303/2929 50 wood; Paradise z14/2651/5776 21 wood; Alpine Lakes z13/1332/2866 33 wood). `landcover` `forest` at z0-7 (observed). Nothing about leaf type (premise 4), so one glyph for all trees.

**What it would take (inferred).** A `fill-pattern` layer over the forest fill needs a pattern image in the sprite. Fonts and icons never change under one name on the Worker (read: `index.ts:272`, and the route pins `/sprites/v4/` at `:276`), so this is a **sprite v5**: a new image set uploaded to R2, the Worker's path regex widened or bumped, the style's `sprite` URL changed, and the app's inversion list extended. Hazel's and a Worker coder's work, a small dispatch each; no tile change, so it does not touch any rebuild. The style side is two layers: the existing green fill, then a `fill` with `fill-pattern` and `minzoom 12` above it.

**Degradation.** If the sprite fails, a `fill-pattern` layer draws nothing and the green fill underneath is exactly today's map: the owner's "empty" look, never a blank. That is the right failure. (A single-layer design with the pattern on the fill itself would lose the green too; rejected for that reason.)

**Attention cost.** Nothing to notice or decide; it is the mark the owner already reads as "forest". The one risk that could read as broken: a tiny wood polygon that gets no glyph at all, or one half-glyph at its edge, looks like a smudge. MapLibre clips the pattern to the polygon, so half-marks at edges are expected and are how every patterned map looks (inferred); the swatch in phase 2 shows it.

**Question 1 for the owner.** (a) Tree glyphs from z12 via a new sprite, recommended; it is the look you asked for and it fails safe to today's map. (b) Keep the flat tint, the topographic convention, and buy "not empty" with hillshade later (idea 8). (c) One tree icon per wood polygon (a symbol layer, no new sprite): rejected, because a 400-hectare forest and a back-garden copse would each get one tree.

### 2. Wetland and meadow marks (Rowan's; the forager's own)

**What you would see (not rendered).** Wetland gets its own ground: a pale blue-green fill, `#c5e3d6` or near it, under a scatter of small blue marsh tufts (the topographic symbol everyone has seen). Where the wetland is wooded in OSM it is usually a separate `wood` polygon on top, so you would see tufts beside trees, not under them (inferred). From z10, where the big marshes are the landscape (observed: Everglades z10/281/437 162 wetland of 169 landuse; Okefenokee z10/278/420 279 of 1234; Ridgefield refuge z13/1302/2920 31 of 87; Lake Louise z12/726/1363 27 of 131; Paradise z7/20/45 11). Meadow is already drawn: `meadow`, `grassland`, `grass`, `heath` are the lighter green (read: `forager-street.mjs:144`), so "open ground" already reads apart from trees; what it lacks is any mark, and under "don't make me think" a plain lighter tint is enough. Scrub is in the same lighter group (read: `:144`) and is dense in the Cascades (observed: Paradise z14 27 scrub of 55), so scrub and meadow are one colour today; the topo convention gives shrubland a dot pattern.

**Night.** The wetland fill inverts to about `#1b392c`, 2.08:1 on the night forest and near 1:1 on the night ground (inferred). It needs either the tuft pattern to carry it at night (inverted with the sprite) or its own night colour under −1344 ("we can go outside the purity for the UX"), a question for phase 2 swatches.

**Data.** `landuse` kind `wetland` at every zoom from z7 (observed above). No subtype (marsh, swamp, bog): `kind` only (observed: metadata).

**What it would take.** The fill: one layer edit in `forager-street.mjs`, no sprite, cheap. The tufts: one more image in the same sprite v5 as the tree glyph, so they share one sprite dispatch. A scrub dot pattern: a third image in the same sprite.

**Degradation.** Fill alone needs no sprite. Tufts fail to the fill; the fill fails to nothing worse than today.

**Attention.** A pale blue-green between water and grass asks the user to learn one colour; the tufts make it self-explaining. Risk of reading as broken: a wetland fill with no outline beside a lake fill could look like a mis-coloured pond; a fine dashed blue outline in the water colour is the cheap guard (inferred).

**Question 2.** (a) Wetland fill now (a style change) and tufts in the same sprite as the trees, recommended. (b) Fill only. (c) Nothing, as today. And a sub-question: (d) scrub as a dot pattern in that sprite too, or left as the lighter green (recommended left, until a render says scrub and meadow need telling apart).

### 3. Park, forest and wilderness names

**What you would see (not rendered).** "Mount Hood National Forest", "Tatoosh Wilderness", "Forest Park" set in the near-black label style (−1286), a step larger and letter-spaced, at the zoom the tiles carry each one: national forests and parks from z8, wildernesses from z9-10, city parks and nature reserves from z12-14 (observed: z7/20/45, z9/82/180, z11/331/722 min_zooms as listed in premise 3; Forest Park z13/1303/2928 "McCarthy Park" 12, "Waud Bluff" 12). This is the drive-to view: "a forager navigates by forest name" (read: `elder-review.md:68`).

**Night.** Text inverts with the other labels (−1288); no exception needed (inferred: `#262626` to `#d9d9d9`, 4.28:1 on the night forest).

**Data.** `pois` points, kinds `nature_reserve`, `forest`, `national_park`, `protected_area`, `park`, with `name` and `min_zoom` (observed; across twelve z7 tiles: 60 nature_reserve, 26 forest, 6 protected_area, 4 national_park, 4 park). Caveat: `forest` at z7 includes private timber parcels ("Weyerhaeuser Parcel" ×3, min_zoom 8, observed z7/20/45), which would label as prominently as a national forest. A filter on `nature_reserve`, `national_park`, `protected_area` first, `forest` and `park` from z11, avoids the parcel names at the state view (inferred).

**Cost.** One symbol layer (a `pois` text layer with no icon, or the existing `pois` filter widened with a text-only branch): a style change, no sprite, no tiles. Degradation: fonts fail, no names, as every other label.

**Attention.** None; names are what a person looks for first. Risk: label clutter at z8 in timber country if `forest` is admitted there (the caveat above).

**Question 3.** (a) Names on from z8 for parks, wildernesses and protected areas, with `forest` and `park` kinds from z11, recommended. (b) All five kinds from z8. (c) None.

### 4. Streams, earlier and bluer

**What you would see (not rendered).** Creeks as a thin but visible blue line from z13, where the tiles already carry them (observed: Paradise z13/1325/2888 6 streams; Mt Hood z13/1323/2934 6; Forest Park z13/1303/2929 3), named at z14 where OSM names them ("Dead Horse Creek", "Edith Creek", observed z14/2651/5776). Today the stream line starts at z14 at 0.5 px in the water-fill blue `#80deea` (read: `forager-light.json`, layer `water_stream`; Street v1 leaves water as Protomaps draws it, read: `part1-report.md:558`), and that blue is **1.09:1 on the day forest** (inferred arithmetic): by day on a wooded hillside, the creeks are effectively invisible, which is the one place a forager wants them.

**Night.** Already solved: the app paints every water line `#8ec5f0` at night (read: `NightColour.kt:84-99`), 3.28:1 on the night forest. Day is the weak side, the mirror of −1343's night finding.

**Proposal.** Stream line from z13 at 1 px (1.5 at z15), colour a deeper blue for lines only, e.g. `#3fa0c8` (1.76:1 on day forest, 2.23 on ground; inferred), the fill blue unchanged so lakes keep the colour the owner liked (−1343: "I like the blue of the water"). Intermittent streams dashed cannot be done: no field (premise 4).

**Cost.** Two numbers and a colour in `forager-street.mjs`; no sprite, no tiles. **Attention:** none; a blue line is a creek to everyone. Risk: at z13 a dense drainage could crowd the trail dashes; phase 2 judges Paradise z13.

**Question 4.** (a) Streams from z13 at 1 px in a deeper line blue, recommended. (b) z14 as today but the deeper blue and 1 px. (c) Leave as is.

### 5. Peaks with spot heights

**What you would see (not rendered).** The peak icon the sprite already has (read: `StreetStyle.kt:90`, "peak"; drawn from z12 by `OUTDOOR_KINDS`, read: `forager-street.mjs:115-125`) with the name, and under it the height: "Bryant Peak 5,801 ft" or "1768 m".

**Data.** `pois` kind `peak` carries `elevation`, in metres (observed: Alpine Lakes z13/1332/2866, 8 peaks, 7 with elevation: "Bryant Peak" 1768, "The Tooth" 1708; Paradise z14 "Alta Vista" 1817; `strings` on `Pois.class` shows `parseElevation`). Also `saddle` ×2 and `alpine_hut` ×2, `shelter` ×1 at z15 across eight trail tiles (observed), not in the outdoor list today.

**The unit question.** The style cannot read the app's unit setting. Options: a fixed unit in the style expression (`["round", ["*", ["get", "elevation"], 3.28084]]` for feet, inferred style-spec arithmetic), or the app rewriting the label expression at load time as it does night colours (an app change, inferred). Since the app's own distances default to imperial (read: CLAUDE.md, the legacy-miles note), feet in the style is the no-thinking default for this country, with the ruling that Canada's users see feet too until the app rewrites it.

**Cost.** A text-field change on the `pois` layer; nothing else. **Attention:** a number beside a peak is read, never puzzled over. Risk: none that could read as broken; an unnamed peak with only a height ("× 1682") is the topographic convention and fine.

**Question 5.** (a) Heights in feet, fixed in the style, recommended. (b) Metres. (c) Follow the app's unit setting (an app change, later). (d) No heights. And (e) add `saddle`, `shelter`, `alpine_hut`, `wilderness_hut` to the outdoor list as dot-and-name: recommended yes, cheap, and a shelter is a thing a forager caught in rain wants on the map.

### 6. Trailhead, parking, viewpoint, campsite, water and toilet icons

**Today.** Toilets and drinking water have icons; trailhead, parking, camp_site, picnic_site, viewpoint and ranger_station draw as the place dot with the name (read: `forager-street.mjs:118-119, 376`; −1300 (5)). Zooms, in this archive: parking and camp_site from z12, peak z13, picnic_site z12, trailhead, viewpoint, toilets, drinking_water, ranger_station at z15 (observed: Paradise z12/662/1444 camp_site, picnic_site; z13 parking, peak; z15 the rest; Mt Hood z15/5294/11737 trailhead; Alpine Lakes z15/5331/11465 trailhead).

**What you would see.** Six real icons instead of six dots, which is what every outdoor app shows and the one place the owner's screenshot had "no symbols". **Cost (inferred):** six icons drawn in the pinned set's style, in **the same sprite v5** as the tree glyph and tufts, plus six night-ring entries like the existing three (read: `forager-street.mjs:108-112`). One sprite dispatch carries ideas 1, 2 and 6 together; that is the main reason to do them as one.

**Degradation.** If the sprite fails, a symbol with a missing image still places its text in MapLibre (inferred, not checked against Native 13.5.0), so the names survive and the dots are gone: worse than today only by the dot.

**Attention.** Icons remove thinking ("what is this dot?"). Risk: an icon drawn in a style unlike the pinned set reads as a foreign mark; the swatch in phase 2 shows them beside the existing peak and toilets icons.

**Question 6.** (a) Six icons in the same new sprite as the tree marks, recommended. (b) Keep dots. (c) Icons for trailhead, parking and viewpoint only (the three that appear at a trailhead), dots for the rest.

### 7. Trail names along trails

Already in Street v1 from z14 (premise 1). What phase 2 should show is whether they survive placement on the dense dash web at Paradise z14 and in Forest Park z14/z15, and whether the near-black of −1286 is right beside a red-brown dash. The topographic convention keeps cultural names black; the one change I would put to the owner is none unless the render shows names lost. **Question 7**, only if the render says so: (a) near-black as ruled (−1286), recommended; (b) trail names in the trail's red-brown so the name and the line read as one thing.

### 8. Hillshade and contours on Street (Rowan's)

**What you would see.** A faint grey relief under the land colours, so a wooded park shows its gullies and ridges; later, brown contours with labelled index lines. This is the biggest "character" an outdoor map has, and the owner has already ruled on its timing: "Option 1", keep OpenTopoMap, "own contours and hillshade later" (−1291, verbatim "D9 is option 1 yes"; read: RECORD.md at line 22044).

**Data.** Not in the tiles and not a tile-build change: a second source. The elevation-sizing note (read: `docs/navigation/2026-10-21-elevation-sizing.md`) sizes 3DEP for the *routing* pipeline (128 GB one-off download, two days on Wi-Fi, a week on the tether) and says nothing about hillshade or contour tiles; Elder's figures for OR+WA were 0.7-2 GB of hillshade and low hundreds of MB of contours (read: `elder-review.md:77`), so US+Canada is tens of GB of raster-dem tiles, built and served beside the basemap: a new Pi pipeline through Aspen, days of work, and the Worker learning a second archive. **Cost inferred, large.** Night: a hillshade layer's shadow and highlight colours are paint properties the app's recolour does not walk (read: `NightColour.kt:76-81`, background/fill/line/symbol only), so it needs an app change too.

**Attention.** None for hillshade (nobody notices relief until it is gone). Contours ask the user to read them, which is why they belong on Topographical first.

**Question 8.** (a) Hold to −1291: later, after ideas 1-6, recommended. (b) Size a hillshade-only pipeline now, in parallel, as the elevation note did for routing.

### 9. Forest texture by leaf type (Rowan's)

Discard for now: no data (premise 4). To get it: a tile-build patch passing `leaf_type` on `wood`/`forest` the way the trail patch passes `sac_scale` (read: the patch's `FORAGER_TRAIL_KEYS` pattern), on a later rebuild, then two glyph images. Whether OSM's US woods carry `leaf_type` often enough to be worth it I could not check (wiki down); from memory it is sparse outside Europe (inferred). Worth revisiting once the one-glyph pattern exists and the owner has seen it.

### 10. Mine: bare rock and scree

Alpine places are grey, and Street v1 draws them as the cream ground (read: `:305`, `bare_rock` absent). The data is there (observed: Alpine Lakes z12/666/1433 23 bare_rock of 111; z13 17 of 65; Paradise z7 7). A pale warm grey fill, e.g. `#d9d4cc`, with no pattern, from z10, makes a mountain look like a mountain and sets the hard-trail colour on something besides green. Cost: one kind in the filter and one colour; no sprite. Night: inverts to a dark grey; needs checking against the night ground in phase 2. **Question 9.** (a) Yes, recommended. (b) No.

### 11. Mine, noted and not proposed

- `pois` kind `tree` (individual mapped trees, min_zoom 16; observed 121 across eight z15 tiles, 68 in one Seattle park tile) must stay filtered out: drawn, they would look like the forest glyph escaping onto streets.
- `surface` is on paths from z13 (observed: Paradise z14 asphalt 14, ground 8; Forest Park interior z14 ground 9). "Paved path in a park vs dirt trail" is a real forager distinction, but it reopens the trail design the owner has just ruled on; I note it for a later round, not now.

## How each degrades if the sprite or fonts fail (summary)

| Idea | Sprite fails | Fonts fail |
|---|---|---|
| Tree glyph | today's flat green | untouched |
| Wetland fill / tufts | fill stays, tufts go | untouched |
| Area names | untouched | names go, like every label |
| Streams | untouched | names go |
| Peak heights | peak icon goes, text stays (inferred) | text goes |
| Six icons | names stay, dots gone (inferred) | icons stay, names go |
| Bare rock | untouched | untouched |

The own-Street fallback catches only a failed style load (read: `OwnStreetFallback.kt`, "only a failed style load"), so none of these failures triggers it; each has to fail to something that still looks like a map, and the table says each does.

## Questions for the owner, ordered by character gained per attention spent

1. **Tree marks** (idea 1): (a) glyphs from z12 via a new sprite, **recommended**; (b) flat tint, topo convention; (c) one icon per wood, rejected.
2. **Streams** (idea 4): (a) from z13 at 1 px in a deeper line blue, **recommended**; (b) z14, deeper blue; (c) as is.
3. **Area names** (idea 3): (a) parks, wildernesses, protected areas from z8, forests and city parks from z11, **recommended**; (b) all from z8; (c) none.
4. **Peak heights** (idea 5): (a) feet, fixed in the style, **recommended**; (b) metres; (c) app-driven, later; (d) none. Plus (e) huts, shelters and saddles into the outdoor list: **recommended yes**.
5. **Wetland** (idea 2): (a) fill now, tufts in the same sprite, **recommended**; (b) fill only; (c) nothing; (d) scrub dots: recommended not yet.
6. **Icons** (idea 6): (a) six icons in the same new sprite, **recommended**; (b) dots; (c) three icons.
7. **Bare rock** (idea 10): (a) yes, **recommended**; (b) no.
8. **Trail names** (idea 7): only if phase 2 shows them lost; default is no change.
9. **Hillshade and contours** (idea 8): (a) hold to −1291, later, **recommended**; (b) size a hillshade pipeline in parallel.
10. **Leaf type** (idea 9): discard until data; no option offered.

Items 2, 3, 4, 7 are style-only changes with no sprite and no tiles, and could go to Hazel as one dispatch. Items 1, 5(a tufts), 6 share one sprite v5 dispatch. Nothing here needs a tile rebuild, so nothing holds the one running.

## Previews to render in phase 2 (after the rebuild finishes, by the Part A method, scratch only)

1. **Swatch strip, not a map:** the tree glyph at two darknesses (`#3e7a3a`, `#5f9a52`) and two densities over forest `#a9d39b`, and inverted over night forest `#436d35`; the wetland fill with and without tufts beside water `#80deea` and grass; the stream line at `#3fa0c8` 1 px and 1.5 px over forest and ground, day and night; the bare-rock grey beside glacier `#e7e7e7` and ground; the six proposed icons beside the existing peak and toilets icons. This is the cheapest render and decides most of the colours.
2. **Forest Park z12, z13, z14, z15, day and night**, centred on the Wildwood Trail (z14/2605/5857 has 13 named paths): the owner's own case, with and without glyphs, and the question of whether trail names appear at z14.
3. **Paradise z13 and z14, day and night:** glyphs under the dense trail web, streams from z13 crowding or not, "Alta Vista 5,961 ft".
4. **Alpine Lakes (Snow Lake) z12 and z13, day and night:** bare rock, eight peaks with heights, scrub beside wood.
5. **Ridgefield refuge z12 and z13, day and night:** the wetland fill and tufts beside a river and lakes, with the fill's night colour judged.
6. **Mount Rainier region z8 and z10, day:** area names on, checking clutter from `forest` parcels.
7. **Everglades z10, day and night:** a tile that is almost all wetland, to see the pattern at its densest.

## Confirmed vs inferred; could not determine; not read

- **Observed:** every tile count above, by z/x/y; the archive's layer and field lists; the jar's class strings; the sprite index count (web).
- **Read:** every file:line cited; the RECORD entries −1284 to −1294, −1300, −1342 to −1345, −1480, −1506, −1513.
- **Inferred:** every night hex and contrast figure (hand arithmetic with the app's formula; the planner has recomputed my figures before and they held to 0.05); that `fill-pattern`, missing-image text placement and hillshade paint behave as the style spec says in Native 13.5.0; every cost estimate; the OSM-tag-usage remarks.
- **Could not determine:** how any of this looks drawn (nothing was rendered, by the brief); why the owner's screenshot had no trail names (zoom or collision); whether the six icons exist in the MIT set's later commits; the Canadian provenance and size of hillshade tiles.
- **Not read:** `docs/plans/understory-design-system.md` and `qa-philosophy.md` (app screens, not map); the Part 1 report's Worker and deploy sections beyond what is cited; the Pi build scripts.
