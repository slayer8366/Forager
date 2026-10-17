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
