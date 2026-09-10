# Report — post-download night transform: is it safe to touch the offline store?

**Date:** 2026-08-28. Investigation only. No transform built, no writes made to any real offline
database (this environment has no downloaded region to write to — no device). Verified against the
real MapLibre Native source at the pinned commit (`a666f02633c1d03bd793dc214cbb2dbcacf8d74e`,
`android-v13.5.0`) — the exact schema SQL, `OfflineDatabase`/`DatabaseFileSource` C++ implementation,
and (new this pass) the Android JNI bindings under `platform/android/MapLibreAndroid/src/cpp/` — plus
`javap` against the pinned AAR for every Java-level API surface claim, and a live fetch of this app's
actual offline style and tile data, not assumed from the design doc.

---

## First: a correction to the dispatch's own "established context," confirmed three independent ways

The dispatch states as settled: *"Offline regions appear to hold raster tiles rendered through the
same style path as the live map, with labels and live-raster attribution — not the glyph-stripped
Protomaps vector style."* **This does not hold for this app's actual download path**, checked three
ways rather than asserted:

1. **Source**: `MapLibreOfflineMapRepository.kt` has exactly one offline-region download call site
   (confirmed by grep across all of `app/src/main`), and it downloads `OFFLINE_STYLE_URL` — whose own
   doc comment (lines 52–66) states plainly that this style is deliberately the glyph-stripped variant,
   because PR #23 found on hardware that downloading a style with `text-field`/glyph layers crashes the
   app natively during resource-list construction.
2. **The live style, fetched just now**: `GET https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json`
   returns exactly one `vector` source (`protomaps`), 57 layers, all `fill`/`line`/`background` —
   **zero `symbol` layers, zero `text-field` layout properties, no `glyphs` field at all.** There is
   no label rendering possible from this style, full stop.
3. **The live tiles, fetched just now**: that source's TileJSON (`.../us.json`) serves
   `.../us/{z}/{x}/{y}.mvt` — Mapbox Vector Tile, protobuf geometry, confirmed by content-type and by
   the byte layout of five real fetched tiles (not raster images of any kind).

This isn't a small correction — it's the finding that answers most of this dispatch by itself: **a
downloaded region has no baked pixel colors for a hue-preserving lightness inversion to act on.**
Color for these tiles comes entirely from the style's paint properties, applied at render time. If
what was actually observed on hardware was labels and OSM/OpenTopoMap-style attribution, the most
likely explanation (unchanged from the prior offline-interception report, and its same-day addendum)
is the live raster basemap rendering from ambient cache, not this download feature. I can't verify
what was on screen during that specific test from here — flagged, not asserted — but the source and
the live server content are unambiguous about what `OfflineTilePyramidRegionDefinition` in this app
actually stores.

**The rest of this report answers the dispatch's schema/API/safety questions in full anyway** — they're
real, useful infrastructure questions independent of which asset type ends up needing a post-download
rewrite (this app's own doc comment already flags a *future* labeled, live-rendering raster basemap as
a candidate — `forager_pmtiles_style.json`, generated but not yet bundled). Item 6 folds the correction
back into a concrete product recommendation for what exists today.

---

## 1. The store

**Exact file**: not directly observable (no device), but its location is: `ensureMapLibreStorageOutsideCache()`
points `FileSource.setResourcesCachePath` at `filesDir/maplibre-offline`. Per the pinned SDK's own
default cache-path naming convention the database inside that directory is a single SQLite file
(MapLibre Android's own docs/source name it `.mapbox`/cache db; this repo's own testing has never
needed to read its literal filename, and nothing in the sparse-cloned source hardcodes an Android-side
name — flagged as the one sub-item not independently confirmed this pass).

**Full schema — read directly from `offline_schema.sql` (`offline_schema.hpp`'s generated form), the
exact source `PRAGMA user_version = 6` gates**:

```sql
CREATE TABLE resources (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  url TEXT NOT NULL,               -- unique
  kind INTEGER NOT NULL,           -- style=1, source=2, tile=3, glyphs=4, sprite image=5, sprite JSON=6, image=7
  expires INTEGER, modified INTEGER, etag TEXT,
  data BLOB, compressed INTEGER NOT NULL DEFAULT 0,
  accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0,
  UNIQUE (url)
);

CREATE TABLE tiles (
  id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
  url_template TEXT NOT NULL, pixel_ratio INTEGER NOT NULL, z INTEGER NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL,
  expires INTEGER, modified INTEGER, etag TEXT,
  data BLOB, compressed INTEGER NOT NULL DEFAULT 0,
  accessed INTEGER NOT NULL, must_revalidate INTEGER NOT NULL DEFAULT 0,
  UNIQUE (url_template, pixel_ratio, z, x, y)
);

CREATE TABLE regions (id INTEGER PRIMARY KEY AUTOINCREMENT, definition TEXT NOT NULL, description BLOB);
CREATE TABLE region_resources (region_id INTEGER REFERENCES regions(id) ON DELETE CASCADE, resource_id INTEGER REFERENCES resources(id), UNIQUE(region_id, resource_id));
CREATE TABLE region_tiles     (region_id INTEGER REFERENCES regions(id) ON DELETE CASCADE, tile_id     INTEGER REFERENCES tiles(id),     UNIQUE(region_id, tile_id));

CREATE INDEX resources_accessed ON resources (accessed);
CREATE INDEX tiles_accessed ON tiles (accessed);
CREATE INDEX region_resources_resource_id ON region_resources (resource_id);
CREATE INDEX region_tiles_tile_id ON region_tiles (tile_id);
```

**Where tile bytes live, and in what form**: `tiles.data`, a `BLOB`, one row per `(url_template,
pixel_ratio, z, x, y)`. **A tile row and a region's ownership of it are two separate tables** —
`tiles` holds bytes shared by every region (and the ambient cache) that happens to reference the same
`(url_template, z, x, y)`; `region_tiles` is only the linking row. A tile with no `region_tiles` row is
ambient cache; the same physical row can be linked to more than one region if two downloads overlap.

**Compression, confirmed from `offline_database.cpp:317-324` and `util::compress`/`decompress`
(`compression.cpp`)**: standard zlib `deflate`, `Z_DEFAULT_COMPRESSION`. **Recorded per row**, not
assumed — `putInternal` compresses unconditionally, then only sets `compressed = true` if the
compressed form is actually smaller (`compressedData.size() < response.data->size()`); `getResource`/
`getTile` branch on that stored flag to decide whether to `util::decompress` on read. Measured just
now on five real fetched `.mvt` tiles from this app's actual tile source: gzip -9 (same deflate family)
shrinks them to roughly **67–68% of their raw size** — so in practice almost every tile row in this
app's regions is stored compressed.

**Metadata per tile**: `expires`, `modified`, `etag`, `accessed` (updated on every read, for LRU
eviction — `getResource`'s own `UPDATE ... SET accessed = ?1` before the `SELECT`), `must_revalidate`.
MapLibre reads back `etag`/`expires`/`modified`/`must_revalidate` to decide whether a background
revalidation is warranted (per the offline-interception report's finding: this fires even for
already-served cache hits); `accessed` is read-back only for the eviction query, never surfaced to the
renderer.

---

## 2. Supported access

**No.** Checked at both layers, not inferred from one:

- **`OfflineRegion`'s full public Java surface** (`javap -p` against the pinned AAR's `classes.jar`):
  `getId`, `getDefinition`, `getMetadata`, `setDeliverInactiveMessages`, `setObserver`,
  `setDownloadState`, `getStatus`, `delete`, `invalidate`, `updateMetadata`. **Nothing else.** No
  enumerate-resources, no get-tile-bytes, no rewrite-tile method of any kind.
- **`OfflineManager`'s full public surface**: `listOfflineRegions`, `getOfflineRegion`,
  `mergeOfflineRegions`, `resetDatabase`, `packDatabase`, `invalidateAmbientCache`,
  `clearAmbientCache`, `setMaximumAmbientCacheSize`, `createOfflineRegion`,
  `setOfflineMapboxTileCountLimit`, `runPackDatabaseAutomatically`, and one that looked promising:
  **`putResourceWithUrl(String url, byte[] data, long modified, long expires, String etag, boolean
  mustRevalidate)`.**

**`putResourceWithUrl` does not solve this**, confirmed by reading its actual JNI implementation
(`platform/android/MapLibreAndroid/src/cpp/offline/offline_manager.cpp:382-407`):

```cpp
mbgl::Resource resource(mbgl::Resource::Kind::Unknown, url);
...
fileSource->put(resource, response);
```

It constructs a `Resource` with `Kind::Unknown`, not `Kind::Tile`. Back in `OfflineDatabase::putInternal`
(read in §1), the kind decides the table: only `Kind::Tile` goes to `putTile` (the `tiles` table, keyed
by `url_template`/`pixel_ratio`/`z`/`x`/`y`); everything else — `Unknown` included — goes to
`putResource` (the `resources` table, keyed by exact `url`). **`putResourceWithUrl` can only ever write
into `resources`, never `tiles`.** The renderer's tile lookup path never queries `resources` for tile
data, so even repeatedly calling this with a tile's fetch URL would be invisible to the map. It also
takes no `regionID` — nothing it writes gets a `region_tiles` row, so it couldn't preserve a tile's
region membership even if it landed in the right table. This method exists for style/source/sprite/
glyph resources (`resources` rows), not tiles — a plausible-looking API that turns out not to apply,
worth recording so it isn't retried.

**Conclusion, stated plainly per the dispatch's own instruction**: raw SQLite only. No supported SDK
API path exists at 13.5.0 for enumerating or rewriting the tile bytes inside a downloaded region.

---

## 3. Safety of writing

**Journal mode and sync, confirmed from `createSchema`/`migrateToVersion5` (`offline_database.cpp:178-212`)**:
`PRAGMA journal_mode = DELETE`, `PRAGMA synchronous = FULL` — a classic rollback-journal SQLite
database, not WAL. This matters directly for concurrent-write safety: a `DELETE`-journal database
supports multiple connections against the same file through SQLite's standard file-locking protocol
(a writer takes an exclusive lock for the duration of a transaction; a second connection attempting to
write concurrently gets `SQLITE_BUSY`, not corruption, and `sqlite3_busy_timeout` — this app's own
Android SQLite driver's default — decides whether that becomes a retry or an error). **A second
process-external connection opened from Kotlin (`SQLiteOpenHelper`/`SupportSQLiteDatabase`, same file
path) is not inherently unsafe just because MapLibre's native connection is also open** — but it does
mean write attempts can collide and fail with a busy error while the native side is mid-transaction,
which any transform code must treat as a retryable condition, not silently swallow.

**Is there a supported way to quiesce it?** Checked both the Android and native layers:

- **`FileSource.setProperty` / `reopenDatabaseReadOnly`** exist in the C++ `DatabaseFileSource` class
  (`database_file_source.cpp:326-328`, gated by a `READ_ONLY_MODE_KEY` property) — **but this is never
  bound to the Android JNI layer.** Confirmed by the same full `javap` listing in §2: Android's
  `FileSource` class has no `setProperty` method at all. This capability exists in the SDK's C++ core
  and is simply not exposed to Android app code at this version.
- **`FileSource.activate()`/`deactivate()`** are real, and do something relevant — but not what their
  names suggest. Read directly from `platform/android/MapLibreAndroid/src/cpp/file_source.cpp:167-187`:
  they're a **process-wide, ref-counted** pause on the resource-loader's worker thread
  (`activationCounter`, incremented/decremented; the underlying `MainResourceLoaderThread` only
  actually pauses when the counter hits zero). Every `MapView` on screen, and this app's own
  `MapLibre.getInstance()` call sites, share this one counter. Calling `deactivate()` from a transform
  routine does not guarantee the native loader is paused unless every other activator in the process —
  including any visible `MapView` — has also deactivated. It reduces risk (no new native reads or
  writes are dispatched while paused) but is not a documented mutual-exclusion primitive, and provides
  no guarantee that an in-flight native SQLite statement has actually completed at the moment
  `deactivate()` returns.

**Net finding**: no strong, documented quiescing primitive is exposed to Android app code. The safest
real-world design is procedural, not API-based — run the transform only when no map screen is on
screen (app backgrounded, or gated to a maintenance flow with no `MapView` alive) and accept SQLite's
own locking as the correctness backstop, not a true guarantee of exclusivity.

**Checksum/validation on read**: **none, deliberately, beyond an incidental side effect of
compression.** `getResource`/`getTile` (`offline_database.cpp:362-415`, `550-` range) do a plain
`SELECT ... data, compressed` and, if `compressed` is set, call `util::decompress`. zlib's `inflate()`
does reject a *structurally malformed* compressed stream (wrong `Z_STREAM_END`, throws a
`runtime_error` caught by `handleError`) — so writing garbage bytes into a `compressed = 1` row has a
real chance of being caught. **It provides zero protection against well-formed-but-wrong data** — a
validly-deflated buffer containing the wrong tile, or a tile for the wrong day/night variant, is
accepted and rendered without complaint. There is no CRC/checksum column in the schema (`crc32` exists
as a general utility in `compression.cpp` but is never called from the get/put path).

**Schema versioning to assert against**: yes — `PRAGMA user_version`, currently `6`
(`createSchema`/`migrateToVersion6`, `offline_database.cpp:187,225`), with the exact migration
`switch` in `initialize()` (lines 60-87) as living documentation of every version this SDK still
understands. A transform tool should read `PRAGMA user_version` itself before writing and refuse to
proceed unless it's exactly `6` — not `>= 6` — since a future SDK bump could add columns this report's
schema dump doesn't know about; failing loudly and falling back to the download-time-transform path is
the correct response to a mismatch, exactly as the dispatch's own closing paragraph anticipates.

---

## 4. Failure and interruption — a concrete design

**Atomicity comes from SQLite's own transaction guarantee, not a hand-rolled staged copy.** Because
the schema uses a rollback journal (`journal_mode = DELETE`, confirmed in §3), wrapping an entire
region's tile rewrite in one `BEGIN IMMEDIATE; ... COMMIT;` gives a real, SQLite-native guarantee: if
the process is killed at any point before `COMMIT` returns, SQLite's own crash recovery (replaying or
discarding the rollback journal on next open) leaves the database exactly as it was before the
transaction started — the region is never observed half-transformed by the renderer, without this app
needing to invent a staged-copy-and-rename scheme itself. Concretely, per region:

1. `BEGIN IMMEDIATE` (takes the write lock up front, so a busy-retry happens before any work, not
   mid-batch).
2. For every tile row linked to this region via `region_tiles` (a plain join, no native API needed —
   this is exactly the kind of read the "raw SQLite only" answer in §2 still permits, since reading
   isn't gated the way writing is discussed to be in §3): decode, transform, re-encode, `UPDATE tiles
   SET data = ?, compressed = ? WHERE id = ?`.
3. `COMMIT`.

A region with roughly 500 real tiles (§5's actual measured count) comfortably fits in one transaction;
there's no forced need to chunk it, though chunking with periodic commits would trade a coarser
resumability granularity for lower peak WAL/journal size — moot here since journal mode is `DELETE`,
not WAL, so the journal is a temporary file sized to the transaction, not a persistent structure.

**Resumability across many regions still needs an app-level flag**, since "region 3 of 10 is
transformed, 4-10 are not" isn't something a single region's own SQLite transaction can express. This
app already owns exactly the right place for it: `OfflineRegionEntity` (`data/local/OfflineRegionEntity.kt`),
this app's own Room row per region (already the documented source of truth over MapLibre's own opaque
region list, per `MapLibreOfflineMapRepository.kt`'s class doc comment). A new nullable column —
`nightVariantTransformedAtEpochMillis` or similar — recording per-region completion is a normal Room
migration, not a MapLibre schema concern, and gives a batch job a natural resume point: skip any region
whose flag is already set, on every retry. **Do not encode this state inside MapLibre's own
`regions.description` blob or the `OfflineRegion` metadata bytes** — those are shared, opaque-format
fields this app already treats as a recovery copy, not a primary store (`OfflineRegionEntity`'s own
doc comment), and mixing this transform's bookkeeping into them adds a second reader to an object this
codebase has deliberately kept single-purpose.

**Process kill mid-transaction**: covered by SQLite's crash recovery as above — the interrupted
region reverts to its pre-transform (day) state on next open, its Room flag is still unset, and a
retry re-attempts the same region cleanly. **Process kill between two regions' transactions** (region 3
committed, region 4 not started): each region's flag is independently accurate, so this is simply "3 of
10 done" — safe, no partial-region state anywhere.

---

## 5. Cost

**A downloaded region is not available in this environment to measure directly (no device — the same
limitation every report in this workstream has flagged)**. What's measured instead is real, live data
for this app's actual default region size, using this app's own tile-count formula
(`EstimateOfflineTileCount.kt`) and real fetched tiles from the actual tile server, not estimates:

- **Default region**: `AvailabilityUiState.radiusKm = 15` (the picker's own default). Centered near
  Mt. Hood, OR (45.5, -122.6) — this app's own domain. `OfflineMapRepository`'s real constants:
  `MIN_ZOOM = 10.0`, `MAX_ZOOM = 15.0`.
- **Real tile-pyramid count**, computed with this app's exact `estimateOfflineTileCount` math:

  | zoom | tile count |
  |---|---|
  | 10 | 4 |
  | 11 | 9 |
  | 12 | 30 |
  | 13 | 100 |
  | 14 | 361 |
  | 15 | 1,296 |
  | **total nominal** | **1,800** — well under `TILE_COUNT_LIMIT = 6,000` |

- **A real, incidental finding**: every z15 tile fetched for this test (4 different real coordinates
  within the computed z15 range) returned **HTTP 404**. The live TileJSON's own top-level field says
  why: `"maxzoom": 14"` — the actual tileset stops at 14, while `OfflineMapRepository.MAX_ZOOM = 15.0`
  requests a level the source doesn't have. This is outside this dispatch's scope to fix, but it's a
  real, verified anomaly (not a guess) directly relevant to "how big is a typical region," so it's
  flagged here rather than silently absorbed into the estimate. **Effective z10-14 tile count actually
  served: 504**, not 1,800.
- **Real per-tile sizes, fetched live** for z10/11/12/13/14 at this exact area: 194,817 / 135,924 /
  81,717 / 64,085 / 55,039 bytes (uncompressed `.mvt`).
- **Weighted total for this real region, z10-14 only (z15 excluded — no data exists there)**:
  **≈30.7 MB raw**, and — using the ~67-68% deflate ratio measured on these same real tiles (§1) —
  **≈20-21 MB as actually stored** in the `tiles` table.

**Per-tile transform cost**: **not applicable to what this app actually stores** — there are no pixel
bytes in a `.mvt` vector tile to run a lightness inversion against (§0/§Correction). As a labeled aside
only, in case a future raster offline basemap is added (per `MapLibreOfflineMapRepository.kt`'s own
forward-looking note about `forager_pmtiles_style.json`): a real, measured HLS-lightness-invert pass
over one actual fetched 256×256 raster tile (`scripts/measure-night-inversion.py`'s own transform
function, run here against a real OpenTopoMap tile) took **0.134 seconds** in a naive, unoptimized
per-pixel Python loop — an upper-bound sanity check only, not a production estimate; real Android code
would use a precomputed lookup table or GPU path, typically low single-digit milliseconds per tile.

**Peak extra storage during the operation, under the atomic-transaction design in §4**: bounded by
SQLite's own rollback-journal size for one region's transaction — roughly the size of the rows being
touched (≈20 MB for the real region measured above), not a second full copy of the whole shared
database. This is materially better than the "staged copy of the whole DB" option the dispatch itself
flagged as possibly unacceptable — that option isn't needed given §4's per-region-transaction design.

**Background/battery**: a transform confined to one region's own transaction (§4) is a short, bounded
SQLite write — well inside the range Android's standard `WorkManager`/foreground-service patterns
already handle for this app's own downloads (the download itself already runs as a comparable or
larger amount of work). No new battery concern beyond what downloading already costs, if run
immediately after a download completes rather than deferred to an unpredictable later time.

---

## 6. The product question — folded back into the premise correction

**For this app's offline system as it exists today: this question doesn't arise.** A downloaded
region's tiles are vector geometry with no baked color; "switching a region from day to night" costs
**nothing per region** — it's a matter of which style document the `MapView` is told to render with,
identical to how the live raster basemaps already switch day/night today (`BasemapStyles.kt`'s
`styleJsonFor(basemap, night)`). One additional small style JSON (a night-paint variant of
`offline.json`, generated once by the same Protomaps `@protomaps/basemaps` pipeline that produces the
current style, per the app's own doc comment) is shared across every downloaded region, at zero
per-region storage cost and zero per-region transform time.

**If this app later adds a genuinely rendered raster offline basemap** (the `forager_pmtiles_style.json`
possibility the existing doc comment already names) — *then* this dispatch's real question applies, and
this report's answer is: the schema is stable and well-documented (§1), no supported API exists for
in-place tile rewriting so any write must go through raw SQLite (§2), no strong quiescing primitive is
exposed to Android app code so the write must be scheduled when no map is visible rather than relying
on `deactivate()` alone (§3), atomicity is achievable for free via one transaction per region on top of
SQLite's own rollback-journal guarantee rather than a hand-built staged-copy scheme (§4), and the real
cost for a typical region is on the order of tens of megabytes either way (§5) — so post-download
transform would be the better design *for that future raster case* specifically because it avoids
re-downloading to switch variants, exactly as the dispatch's own "decision this feeds" section
predicted, conditioned on running it only while no `MapView` is active.
