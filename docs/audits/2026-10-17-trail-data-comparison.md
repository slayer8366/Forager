# Federal trail data beside OpenStreetMap trails, in sample areas, for Forager's trail navigation

Dispatch `prompts/preserved/2026-10-17-02.md` (Forager RECORD -1172). Research helper, read-only, 2026-10-17. Branch `trail-data-comparison` from pre-main 746d8489. Evidence (scripts, query logs, counts, small outputs) under `docs/audits/2026-10-17-trail-data-comparison/`.

What this is: a sampled comparison, in 7 boxes of about 10 x 10 km, of three federal trail layers against the volunteer-built world map database's trails (OpenStreetMap, named as its licence requires). Nothing national was downloaded; every query is a bounded box against a public query service, logged with its time. Each claim below is marked read (from a response or a licence page), computed (by a script in the evidence folder), or inferred.

## Method, written before any trail data was read

This section was committed (see git history of this file) before the first trail query ran.

### Sample areas

Boxes are 10 km on a side, centred as below; latitude span 0.0899 degrees, longitude span 10 km / (111.32 km x cos latitude). The reason for each is the dispatch's coverage list.

| # | Area | Centre (lat, lon) | Why |
|---|---|---|---|
| 1 | Mount Hood, Zigzag to Timberline (Mt. Hood National Forest) | 45.33, -121.78 | a busy national forest near a city (Portland) |
| 2 | Eagle Cap Wilderness, Lakes Basin (Wallowa-Whitman NF) | 45.20, -117.26 | the wilderness the dispatch names; the forest publishes almost no trail geometry (-1168) |
| 3 | Chamberlain Basin, Frank Church-River of No Return Wilderness (Payette NF) | 45.37, -115.20 | the Idaho interior, far from roads |
| 4 | Oregon Badlands Wilderness, east of Bend (BLM Prineville) | 43.97, -121.05 | a BLM-dominated area, with trails BLM manages |
| 5 | Cape Perpetua and Yachats (Siuslaw NF) | 44.30, -124.04 | a coastal forest; the box's west edge is near the shore |
| 6 | Tiger Mountain and Cougar Mountain, east of Seattle (state forest and county park) | 47.50, -121.98 | outside any national forest, heavily walked |
| 7 | Three Sisters Wilderness, Green Lakes (Deschutes and Willamette NFs) | 44.10, -121.80 | a control for area 2: a wilderness in forests that do publish geometry |

Boxes 2, 3 and 7 are wildernesses, so BLM data is expected absent there; box 4 is expected to have no Forest Service data; box 6 is expected to have no Forest Service or BLM data. Those expectations are predictions, recorded here so a result that contradicts them is visible.

### Sources and queries

- **OSM:** the public Overpass query service (`overpass-api.de`, or a public mirror if it is at capacity), one query per box for ways with `highway` in {path, footway, track, bridleway, steps}, plus any way with `sac_scale` or `trail_visibility`. Sidewalks and crossings (`footway=sidewalk`, `footway=crossing`) are excluded as urban, and counted separately so the exclusion is visible. Ways are clipped to the box.
- **Forest Service:** `EDW_TrailNFSPublish_01/MapServer/0` (National Forest System Trails), envelope query, geometry returned, paged at the service's 2,000-record limit.
- **BLM:** `transportation/BLM_Natl_GTLF_Public_Display/MapServer`, trail layers 2 to 7, envelope query, geometry returned.
- **USGS:** The National Map `transportation/MapServer/37` (Trails), envelope query, geometry returned. Federal segments only: rows whose `sourceoriginator` (or `primarytrailmaintainer`) names a federal agency are kept; state, local and volunteer rows are counted but excluded from the federal set, following -1168.
- **Federal merged set:** Forest Service plus BLM, plus those USGS federal rows that lie further than 15 m from both (the USGS layer republishes agency data, so counting it twice would double the length).

Every request carries a descriptive user agent, runs one at a time, with at least 2 seconds between requests and exponential back-off on a 429 or 5xx; a service that refuses is reported, not retried beyond three attempts.

### Measures (computed, scripts in the evidence folder)

All geometry is projected to the UTM zone of the box centre (metres) and clipped to the box before measuring.

1. **Length and count** of ways or segments per source.
2. **Overlap:** each set is buffered by 15 m (unioned). The share of a set's length lying inside the other's buffer is "near"; the rest is "only". Federal-only length is what federal data would add; OSM-only is what OSM has that federal data lacks. 15 m is wider than a trail and narrower than a switchback spacing; a second pass at 50 m shows how much of "only" is really offset rather than absent.
3. **Connectivity:** vertices rounded to 1 m are nodes; ways sharing a node are joined. Reported as the number of connected pieces and the largest piece's share of the set's length. The box clip breaks lines at the edge identically for every source.
4. **Attributes:** per source, the share of segments carrying a name, a trail number, an allowed-use statement, a closure or season field, and a difficulty or class field; and the most recent and median of any edit-date field the source carries.
5. **Offset:** a federal segment is "matched" when at least half its length lies within 50 m of an OSM way. Points are sampled every 10 m along each matched segment; the segment's offset is the median distance from those points to the nearest OSM way. 20 matched segments per box are drawn with seed 1172 (all of them if fewer than 20) and the median of their offsets is reported, with the quartiles.

### What this method cannot tell

- Whether a trail exists on the ground: both sources can be wrong the same way.
- Attribute correctness: a name or an allowed use is counted present, not checked.
- Anything about areas outside the seven boxes; the boxes were chosen for contrast, not at random, and the shares reported are per box, not regional estimates.

### Changes to the method after data was read

Two, both recorded here rather than silently folded in:

1. **BLM layer 7 is a republication.** In box 4 the "Trails Managed for Public" layer's 76 rows carry the same `FAMS_ID`s as layers 4 and 5 together (2 + 74), with different object ids. Merging all six layers would have doubled BLM's length (first run: 150 segments, 109.3 km; corrected: 75 segments, 54.6 km). Layer 7 is now counted but left out of the merge (`analyze.py`, `analyze_box`).
2. **Connectivity joins a line end to any line within 1 m,** not only to a vertex in the same 1 m grid cell. The first version split ends 0.1 m apart across a cell boundary and reported Mount Hood's Forest Service terra trails as 12 pieces when 48 of 68 segment ends lie within 1 m of another segment (`extra-checks.txt`). The replacement is a strictly more generous join, applied to every source alike; `results.md` was regenerated.

Every number below is from the regenerated `results.md`, `extra-checks.txt` and `named-gaps.txt` unless marked otherwise.

## Queries run

`query-log.tsv` lists every request with its UTC time, status, bytes and record count: 7 boxes x (1 Forest Service + 1 USGS + 6 BLM + 1 OSM) envelope or bbox queries, plus the OSM last-edit queries, and the licence and metadata pages. Nothing was paged beyond the first 2,000 records in any box. The main Overpass instance answered 502 ("at capacity") on first contact for most boxes and answered on the second or third attempt after a 5 to 10 s back-off; no mirror was needed for the geometry queries. OSM data timestamp (`osm3s.timestamp_osm_base`) for box 1 was 2026-10-17T08:07:36Z, i.e. live. The federal services answered first time, every time.

Raw responses (about 6 MB) stayed in the session's scratch area; they are untrusted downloaded data and reproducible from the logged URLs.

## Results per area

Lengths are kilometres inside the box after clipping. "Near" is the share of a source's length within 15 m of the other source (50 m in brackets). "FS" is the Forest Service layer, "USGS" The National Map trails layer, "federal" the merged set (FS + BLM + any USGS federal row more than 15 m from both: that added 0.0 km in every box).

| # | Area | OSM ways / km | FS segs / km | BLM segs / km | USGS segs / km | Fed near OSM | OSM near fed | Fed-only km | OSM-only km | Offset median m (n) |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Mount Hood | 185 / 160.6 | 70 / 142.5 (terra 88.7, snow 53.8) | 0 | 181 / 122.5 | 82% (89%) | 61% (69%) | 25.2 | 62.7 | 2.6 (20 of 60) |
| 2 | Eagle Cap | 100 / 99.5 | 0 | 0 | 0 | n/a | 0% | 0 | 99.5 | none |
| 3 | Chamberlain Basin | 8 / 50.3 | 10 / 59.3 | 0 | 18 / 59.3 | 47% (64%) | 55% (76%) | 31.6 | 22.5 | 11.0 (8 of 8) |
| 4 | Oregon Badlands | 101 / 150.0 | 0 | 75 / 54.6 | 75 / 54.7 | 100% (100%) | 37% (38%) | 0.3 | 94.4 | 3.0 (20 of 73) |
| 5 | Cape Perpetua | 155 / 89.7 | 8 / 21.7 | 0 | 11 / 21.8 | 69% (90%) | 17% (22%) | 6.8 | 74.8 | 3.9 (7 of 7) |
| 6 | Tiger Mountain | 1054 / 225.5 | 0 | 0 | 11 / 9.0 (state, not federal) | n/a | 0% | 0 | 225.5 | none |
| 7 | Three Sisters | 28 / 63.4 | 15 / 58.4 | 0 | 29 / 58.3 | 79% (86%) | 71% (78%) | 12.1 | 18.3 | 3.1 (13 of 13) |

Connectivity (pieces, largest piece's share of length): OSM 22 / 66%, FS 15 / 39% (terra only 8 / 40%) in box 1; OSM 7 / 97% in box 2; OSM 2 / 100%, FS 4 / 65% in box 3; OSM 15 / 85%, BLM 4 / 91% in box 4; OSM 79 / 20% (paths and footways only 37 / 62%), FS 5 / 42% in box 5; OSM 254 / 60% in box 6; OSM 4 / 70%, FS 10 / 72% in box 7.

### 1. Mount Hood, Zigzag to Timberline (busy forest near a city)

- **Length.** OSM 160.6 km (131.9 path, 25.5 track, 3.3 footway) against 142.5 km Forest Service, of which 53.8 km are **snow trails** (`trail_type` SNOW, 33 of 70 segments: nordic routes such as "WEST LEG NORDIC"). On terra trails alone the Forest Service has 88.7 km. The USGS layer's 181 segments are all "U.S. Forest Service / FS", source description "USFS Trails Update 03/2026", and lie a median 0.9 m from the Forest Service lines: the same data, republished, cut into more pieces.
- **Overlap.** Forest Service terra trails are 93% within 15 m of OSM (99% within 50 m): OSM has nearly every official summer trail here. The 25.2 km federal-only is mostly snow trails (the listing in `named-gaps.txt` is 12 snow segments and two short terra ones, 795A and 795C, at about half overlap). OSM-only is 62.7 km: 42.1 km of `path` (for example "Timberline To Town Trail #756", 7.6 km, which carries a Forest Service trail number but is not in the Forest Service layer; "Optimator", "Sunrise", "Gnar-Gnar", the mountain-bike trails at the ski area; 5% of OSM ways here are `informal=yes`), 17.4 km of `track` (old roads such as "Mt. Hood Loop Highway (1925)"), 3.2 km footway.
- **Connectivity.** Neither set is one network inside the box: OSM 22 pieces (largest 66% of length), Forest Service terra 8 pieces (largest 40%). Forest Service trails end at trailheads and roads, and roads are in neither set here, so a trail graph alone will not route between trailheads in either source (inferred from the piece counts and the segment-end check: 48 of 68 terra segment ends touch another segment within 1 m, the rest end at roads or the box edge).
- **Attributes.** Forest Service here publishes the `TrailNFS_Basic` subset: name and number on 100%, `trail_class` (1 to 5) and surface on 100%, but every allowed-use and restriction field reads `N/A` (`extra-checks.txt`). OSM: name 49%, `ref` 29%, an access or mode tag 66%, `sac_scale`/`mtb:scale`/`trail_visibility` 18%, surface 48%, seasonal or closure tags 1%.
- **Offset.** Median 2.6 m (quartiles 2.1 to 4.4) over 20 matched segments sampled from 60: where both have a trail, they draw it in the same place.

### 2. Eagle Cap Wilderness, Lakes Basin (a forest that publishes little)

- **Federal geometry: none.** Forest Service 0 rows, USGS 0 rows, BLM 0 rows in the box. This is the -1168 finding seen from the map side: Wallowa-Whitman publishes trail records almost without geometry, and the USGS layer, which republishes agency geometry, inherits the gap.
- **OSM** has 100 ways, 99.5 km, all `path` but one, in 7 pieces with 97% of length in the largest: a connected network of the Lakes Basin trails. Name 48%, `ref` 39% (the Forest Service numbers), surface 67%, `sac_scale` or visibility 14%.
- The planner's prediction for this box is confirmed exactly: OSM is the only usable source.

### 3. Chamberlain Basin, Frank Church-River of No Return (Idaho interior)

- **Length.** OSM 8 ways, 50.3 km; Forest Service 10 segments, 59.3 km (Payette, `TrailNFS_MGMT` subset). Every OSM way here is a named, numbered Forest Service trail (name and `ref` 100%).
- **Overlap.** Only 47% of federal length is within 15 m of OSM, 64% within 50 m; two trails are **absent from OSM altogether**: Three Blaze 017 (7.1 km) and Lower Lodgepole 342 (5.4 km). The other four are present but drawn elsewhere: Chamberlain 001 is 54% within 15 m and 78% within 50 m, Queen Creek 026 only 27% and 56%. The 22.5 km "OSM-only" is not extra trail, it is the same trails on a different line: no OSM way lacks a federal counterpart.
- **Offset.** Median 11.0 m (quartiles 4.5 to 15.5) over all 8 matched segments, the widest of any box. Which source is right is not knowable from this data; the BLM layer records a coordinate source and accuracy per row, the Forest Service layer does not (read, schema).
- **Attributes.** The `MGMT` subset carries use seasons: `pack_saddle_managed` "01/01-12/31" on 9 of 10, `hiker_pedestrian_managed` on 1 of 10, `allowed_terra_use` code "21" on all. `typical_trail_grade` holds the literal "TRAIL_GRADE" on every row, so the 100% "difficulty" count in `results.md` over-reads: only `trail_class` (1 to 3) is real here. The USGS rows for Forest Service-sourced trails carry no use fields at all (all `None`).
- **Connectivity.** OSM 2 pieces with effectively all length in one; Forest Service 4 pieces, 65%.

### 4. Oregon Badlands Wilderness, east of Bend (BLM)

- **Length.** BLM 75 segments, 54.6 km, from the public-display layers 4 (2 segments, "BIKE_HIK_ONLY") and 5 (73, "EQU_HIK_ONLY"); layer 7 repeats them. The USGS layer has the same 75 ("Bureau of Land Management / BLM", "BLM Trails Update 03/2026"). OSM has 150.0 km: 91.0 path, 32.4 track, 26.6 footway.
- **Overlap.** Every metre of BLM trail is within 15 m of an OSM way (100%); the BLM set adds 0.3 km. OSM has 94.4 km the BLM layer lacks: 55.1 km of path, 32.3 km of track, 7.0 km of footway. The box is 10 km wide and the wilderness does not fill it, so some of that is outside BLM land, and some is old two-track inside it that OSM mapped and BLM does not manage as trail; which share is which was not determined (not checked; the BLM boundary was not fetched).
- **Attributes.** The BLM layer is the richest of the federal three per row: name 100%, `FAMS_ID` 100%, allowed modes 100% (`PLAN_ALLOW_MODE_TRNSPRT`, `PLAN_MODE_TRNSPRT` "Non-Motorized"/"Non-Mechanized", OHV designation "Closed"), season restriction code "NO" and access restriction "NONE" on 100% (present and meaningful: "no restriction", not missing), surface "Natural", and a stated accuracy (`ACCURACY_FT` 98 ft on 64 rows, -1 on 10; coordinate source GPS 64, imagery 10). OSM: name 31%, `ref` 0%, access or mode tag 47%, surface 50%.
- **Connectivity and offset.** BLM 4 pieces, 91% in the largest; OSM 15 pieces, 85%. Offset median 3.0 m (1.9 to 4.8), 20 sampled of 73.

### 5. Cape Perpetua and Yachats (coastal forest)

- **Length.** Forest Service 8 segments, 21.7 km (Siuslaw, `MGMT` subset); OSM 89.7 km, but 61.1 km of it is `track`, nearly all named "United States Forest Service Road NNNN": forest roads tagged as tracks, not trails. OSM's path and footway length is 28.4 km, of which 11.1 km footway is Yachats' town paths (inferred from the highway mix and names in `named-gaps.txt`; not mapped).
- **Overlap.** Federal 69% within 15 m of OSM, 90% within 50 m; the two under-matched trails (Discovery Loop 1387, Giant Spruce 1365) are present in OSM but offset. Federal-only 6.8 km is offset, not absence. OSM near federal 17% (22%): the rest is roads and town.
- **Connectivity.** OSM 79 pieces (20% in the largest) because roads and town fragments abound; paths and footways alone 37 pieces, 62%. Forest Service 5 pieces, 42%.
- **Attributes.** `hiker_pedestrian_managed` "01/01-12/31" on all 8, `accessibility_status` "NOT ACCESSIBLE" on all 8, `allowed_terra_use` "321" on 7, `trail_class` 1 to 4. OSM: name 30%, `ref` 28%, `operator` 27%.
- **Offset.** Median 3.9 m (1.9 to 16.1), all 7 matched segments.

### 6. Tiger Mountain and Cougar Mountain (outside national forests)

- **Federal: nothing.** The 11 USGS rows (9.0 km) are "Washington State Parks and Recreation Commission / State", source "Washington State Trails 01/2020", on Squak Mountain: state data carried by the USGS layer, dropped from the federal set by the method, and six years old by its own label. Forest Service and BLM 0.
- **OSM** has 1,054 ways and 225.5 km after excluding 621 sidewalks and 333 crossings: 98.5 km path, 96.4 km footway, 25.2 km track, 4.9 km bridleway. 254 pieces, 60% in the largest; paths and footways alone 228 pieces, 65%. The fragmentation is the urban edge of the box (Issaquah) and roads being outside the set, not a defect of the trail mapping (inferred, not mapped). Name 23%, surface 65%, access or mode tag 38%.

### 7. Three Sisters Wilderness, Green Lakes (control for Eagle Cap)

- **Length.** Forest Service 15 segments, 58.4 km (`MGMT` subset, Deschutes and Willamette); USGS 29 segments, 58.3 km, 6 of them flagged "NST - Pacific Crest"; OSM 28 ways, 63.4 km, all `path`.
- **Overlap.** Federal 79% within 15 m (86% within 50 m); OSM 71% (78%). Federal-only 12.1 km: Separation Meadow 4331 (3.7 km) is **absent from OSM**; Louise Creek 3520 and South Sister Climber 36 are present but drawn apart. The climber trail is the clearest disagreement in the sample: OSM's "South Sister Climber Trail #36" and the Forest Service's "36 SOUTH SISTER CLIMBER" share a number and a name, yet only 23% of the federal line is within 15 m of OSM and 48% within 50 m. OSM-only 18.3 km: an unnamed 5.3 km path, an unnamed 2.8 km path, and nine short ones.
- **Attributes.** This is where the `MGMT` subset earns its name: `hiker_pedestrian_managed` carries seasons "06/15-10/31" on 3 segments and "06/01-10/31" on 1, the only closure or season information found in any source in any box beyond BLM's "no restriction" codes. `trail_class` 2 to 3 on all. OSM: name 64%, `ref` 32%, `sac_scale` or visibility 46%.
- **Connectivity and offset.** OSM 4 pieces, 70%; Forest Service 10 pieces, 72%. Offset median 3.1 m (2.1 to 8.1), all 13 matched.

## Across the areas

- **Coverage.** OSM had trail geometry in all seven boxes; the federal layers in four. Where a forest publishes (boxes 1, 3, 5, 7) the Forest Service layer covers the official trails fully; where it does not (box 2) the USGS layer has nothing either, because it republishes the agencies' geometry (read: every USGS row in boxes 1, 3, 5, 7 is sourced to the Forest Service with a March 2026 update label, and lies a median 0.8 to 1.0 m from the Forest Service line).
- **What federal data adds.** Five named trails absent from OSM in two wilderness boxes (Three Blaze, Lower Lodgepole, Separation Meadow, and the partial Queen Creek and Louise Creek), about 12.5 km absent plus about 20 km drawn on a different line; official names and numbers on 100% of rows against OSM's 23% to 64% names and 0% to 39% numbers; `trail_class`; and, in the `MGMT` subset only, allowed uses and seasons. BLM adds a per-row accuracy figure.
- **What OSM adds.** Everything in box 2 and box 6 (99.5 km and 225.5 km); the mountain-bike and informal trails near Mount Hood (42 km of path); 55 km of path around the Badlands; 11 of 28 ways in Three Sisters. Near towns OSM's extra is partly roads tagged `track` (Cape Perpetua: 61 km of forest roads) and town footways, which a trail layer would want to filter by tag.
- **Geometry agreement.** Where both have a trail, the median offset is 2.6 to 3.9 m in four boxes and 11 m in the Idaho interior; quartiles reach 15 to 16 m in the two remote boxes. The USGS and Forest Service lines are the same line (0.8 to 1.0 m).
- **Connectivity.** No source forms a single network inside any 10 km box except OSM in Eagle Cap (97%) and nearly so in Chamberlain Basin and the Badlands (BLM 91%). Trails join through roads and trailheads, and roads are in none of the trail layers, so navigation needs a road layer in the graph as well, whichever trail source is chosen (inferred from the piece counts; not tested with a router).
- **Attributes for navigation.** Names and numbers: federal 100%, OSM partial. Allowed use: BLM 100%; Forest Service only in the `MGMT` subset (boxes 3, 5, 7 yes, box 1 no); OSM 32% to 66% by tags. Closures and seasons: Forest Service `MGMT` seasons on 4 of 15 segments in Three Sisters, none elsewhere; BLM "no restriction" codes; OSM 0% to 7%. Difficulty: Forest Service `trail_class` on 100% (a maintenance class, not a hiker grade); OSM `sac_scale` or `trail_visibility` on 0% to 46%. Trail-level closures (a bridge out, a fire closure) are in no source read here.
- **Currency.** Forest Service: no per-row date in the layer; metadata publication 2025-05-14, catalogue entry 2022-08-25, and the USGS republication labels it "USFS Trails Update 03/2026". BLM: catalogue "Dataset Last Updated September 22, 2026"; USGS labels it "BLM Trails Update 03/2026". USGS: state file 2026-02-12, with the agency labels above and "Washington State Trails 01/2020" for the state rows. OSM: see the next section.

## When the OSM ways were last edited

One further small query per box (`fetch_osm_meta.py`, ids, tags and meta only, no geometry) for the same ways' last-edit timestamps; summarised by `osm_dates.py` in `osm-dates.txt`. The main Overpass instance refused boxes 1 and 6 three times each (502, then 500 twice) on the first pass and answered both on a retry a few minutes later; the retries are in `query-log.tsv`. Counts differ by one or two from the geometry tables because these ways are not clipped to the box.

| # | Area | Ways | Oldest edit | Median edit | Newest edit | Edited in the last two years |
|---|---|---|---|---|---|---|
| 1 | Mount Hood | 186 | 2013-09-11 | 2021-10-03 | 2026-10-04 | 10% |
| 2 | Eagle Cap | 101 | 2021-03-18 | 2021-09-18 | 2026-10-13 | 37% |
| 3 | Chamberlain Basin | 8 | 2020-08-27 | 2025-11-29 | 2025-11-29 | 62% |
| 4 | Oregon Badlands | 101 | 2012-11-10 | 2022-06-01 | 2026-03-29 | 36% |
| 5 | Cape Perpetua | 158 | 2016-02-02 | 2024-11-05 | 2026-08-01 | 73% |
| 6 | Tiger Mountain | 1,071 | 2010-03-18 | 2024-02-19 | 2026-10-12 | 45% |
| 7 | Three Sisters | 28 | 2017-11-27 | 2021-08-03 | 2026-07-13 | 18% |

A way's timestamp is its last edit of any kind (a tag change counts), so this is an upper bound on how recently its line was checked. Read against the federal labels (Forest Service and BLM republished March 2026; Forest Service metadata May 2025): the federal layers are refreshed as a whole on a schedule, OSM is refreshed way by way and unevenly, with the busy areas (Mount Hood, Three Sisters) having a median edit five years old and every box having edits from this year. Neither source states when a line was last verified on the ground.

## Licences, read at source

Full quotations with URLs are in `licence-quotes.md`. In short:

- **OSM: ODbL 1.0** (read, foundation licence page and `openstreetmap.org/copyright`). Attribution and share-alike. The foundation's attribution guideline (read) accepts "© OpenStreetMap contributors" or "Map data from OpenStreetMap", in a map corner or on a splash screen, with the licence findable afterwards from an "(i)" or an About entry; "Applications that incorporate such a routing engine must credit OpenStreetMap".
- **Forest Service trails: CC BY 4.0** (read, on every distribution of the catalogue entry including the map service queried here; the metadata record's use constraint is a warranty disclaimer only, access constraint "None"). Confirms the searches report.
- **BLM trails: public domain** (read, `http://www.usa.gov/publicdomain/label/1.0/` on the managed-trails catalogue entry; the service items carry an "as is" disclaimer). Confirms the searches report. The display map service queried here has no catalogue page of its own; that it shares the terms of the dataset it displays is inferred.
- **USGS NTD: "None"** for use and access constraints, "Acknowledgment of the U.S. Geological Survey would be appreciated for products derived from these data" (read, Idaho state metadata record). Confirms the searches report. Its trail rows name their originator per row, and the non-federal rows (state, in box 6) carry no terms of their own in the record read.

**Attribution text the app would need** (one line each, where the map and the data licences are shown):

- "Map data © OpenStreetMap contributors, ODbL" with a link to `openstreetmap.org/copyright` (required; the wording is the foundation's accepted form).
- "Trails: National Forest System Trails, USDA Forest Service, CC BY 4.0" with a link to the licence (required by CC BY: attribution, licence name and link, and a note if changes were made).
- "Trails: Bureau of Land Management, public domain" (not required; the owner's practice is to credit anyway).
- "Trails: USGS National Transportation Dataset" (not required; "appreciated"), only if the USGS layer is used at all.

### What share-alike would mean for the app (inferred, not legal advice)

This reading is from the licence text and the foundation's two board-endorsed guidelines quoted in `licence-quotes.md`; it is a reading for the owner, not a ruling, and a lawyer would be the next step if the app ships an OSM-derived database.

- **Tiles for display** (the own-map-tiles plan): the rendered map is a Produced Work (4.5 b, and the produced-work guideline). Attribution is owed; share-alike is not triggered by the display itself. But 4.6 says that if the Produced Work comes from a *Derivative* Database, the derivative database (or the alterations and method that make it) must be offered to recipients; a tile set made from an OSM extract plus a pipeline is the usual case, and publishing the extract date and the pipeline satisfies it.
- **A trail database derived from OSM alone,** shipped in the app for routing and search: that is a Derivative Database (extraction of a substantial part of the contents into a new database, 4.4 b) that is publicly used, so it must be offered under the ODbL, with the licence notice and, on request, a machine-readable copy or the alterations and method (4.2, 4.6). This is compatible with the owner's position that the app has no proprietary code or data (RECORD -397); it means the app's trail database is itself open data under ODbL, and the app must say so and be able to hand it over.
- **A merged database, OSM and federal trails conflated into one network** (one line per trail, attributes from both): the whole merged trail database becomes a Derivative Database under 4.4, so the federal contents inside it are redistributed under ODbL terms as well. CC BY 4.0 and public-domain contents can be placed in an ODbL database (4.4 d forbids only contents whose terms are *incompatible*; CC BY requires attribution, which the ODbL notice can carry), so this is lawful, but it means the merged product cannot be offered under CC BY or placed in the public domain, and the attribution for the Forest Service must be kept inside it.
- **A collective database, OSM and federal trails as separate layers** that do not reference each other, or where one source wholly replaces a feature type within a region: under the collective-database guideline (endorsed 2016-06-17) share-alike then applies only to the OSM part. "Two data sets need not be physically separated to qualify as independent." Showing the Forest Service layer over an OSM base, or using Forest Service trails *instead of* OSM trails inside the national forests that publish them and OSM trails elsewhere (a regional cut by forest), fits the guideline's own examples. Conflating lines or copying a name from one into the other does not.
- **Routing instructions** generated by a router over OSM data need not carry attribution (attribution guideline, routing engines), but the app must credit OSM.

## Recommendation

**Use both, kept as separate layers, with OSM as the base and the federal layers as an overlay: not one merged trail database.**

- **OSM for geometry and coverage.** It is the only source with trails in Eagle Cap (99.5 km against 0) and outside federal land (225.5 km against 0), it has the mountain-bike and informal trails near towns, and where both sources have a trail they agree to a median 3 m. Where the two disagree by more (Chamberlain Basin, 11 m; the South Sister climber trail) this sample cannot say which is right.
- **Forest Service and BLM for the official record.** Names and numbers on every row; `trail_class`; in the `MGMT` subset, seasons and allowed uses; a handful of backcountry trails OSM lacks (about 12.5 km absent in two of the four forested boxes); BLM's stated accuracy. The right use for this in the app is a labelled overlay and a lookup ("this is Forest Service trail 001, Chamberlain; stock allowed year-round"), not a replacement for OSM's lines, because its own network is more fragmented, it carries no roads, and its coverage depends on which forest published.
- **Drop the USGS layer for the app.** In every box it added 0.0 km beyond the Forest Service and BLM layers, lies within a metre of them, carries fewer attributes than either, and has nothing where they have nothing. Its one advantage, a single national feed, is outweighed by its state and volunteer rows whose terms are unconfirmed (-1168). It stays useful as a check, not a source.
- **Routing.** No trail layer forms a network on its own inside a 10 km box; trails meet at roads. Whatever routes the user needs a road layer in the same graph. Routing over OSM (trails and roads from one source, one licence) is the simpler case; a router over a conflated OSM-plus-federal graph makes the graph a derivative database. The navigation plan's choice of router is not made by this report.
- **Licence consequence of the recommendation.** Attribution for OSM in the map corner and the About screen, a CC BY line for the Forest Service layer; share-alike confined to the OSM-derived part, which the app can satisfy by publishing its OSM extract date and build method. If the owner would rather ship one conflated trail database, that is lawful too, with the whole trail database under ODbL.

## What could not be checked

- **Ground truth.** Nothing here says whether a trail exists, is passable, or is where either source draws it. The 11 m offsets in Idaho and the two South Sister climber lines are disagreements, not errors assigned to a side.
- **Which forests publish.** Only Mt. Hood (`Basic`), Payette, Siuslaw, Deschutes and Willamette (`MGMT`) and Wallowa-Whitman (nothing) were sampled. The subset each other forest publishes is in the layer's `attributesubset` field and could be tallied by forest with one count query per forest, which was out of this dispatch's scope.
- **The BLM boundary** in box 4, so the 94 km OSM-only there is not split between "outside BLM land" and "unmanaged two-track inside it".
- **Non-federal rows in the USGS layer** (state, county, volunteer): one set seen (Washington State Parks, 2020); their terms were not found in the record read.
- **Trail closures** (fire, bridge out): no source read here carries them; the Forest Service `MGMT` seasons are planned management seasons.
- **The catalogue's JSON API** returned 404 for both federal entries; the HTML pages were read instead, and the BLM display service's own catalogue page was not found.
- **Regional estimates.** Seven boxes chosen for contrast give shares per box, not a share for the Pacific Northwest.
- **A router** was not run; connectivity is piece counts at 1 m, with roads absent from every trail set.
