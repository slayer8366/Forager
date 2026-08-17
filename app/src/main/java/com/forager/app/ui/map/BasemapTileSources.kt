package com.forager.app.ui.map

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory

/**
 * The one place a [Basemap] becomes an osmdroid tile source.
 *
 * All four come straight out of the pinned `TileSourceFactory` — no custom `XYTileSource`, no
 * hand-written URL template, so there is no URL of ours to get wrong. Verified against
 * `osmdroid-android-6.1.20.aar` itself rather than assumed from documentation:
 *
 * | [Basemap] | `TileSourceFactory` field | `name()` | declared max zoom |
 * |---|---|---|---|
 * | [Basemap.USGS_TOPO] | `USGS_TOPO` | `USGS National Map Topo` | 15 |
 * | [Basemap.USGS_IMAGERY_TOPO] | `USGS_SAT` | `USGS National Map Sat` | 15 |
 * | [Basemap.OPEN_TOPO_MAP] | `OpenTopo` | `OpenTopoMap` | 17 |
 * | [Basemap.OSM_STANDARD] | `MAPNIK` | `Mapnik` | 19 |
 *
 * `USGS_SAT` is the field name; the service behind it is `USGSImageryTopo`, which is imagery *with
 * topographic labels over it* rather than plain satellite — hence [Basemap.USGS_IMAGERY_TOPO]
 * naming it for what it draws instead of inheriting osmdroid's misleading `SAT`.
 *
 * Both USGS entries are anonymous `OnlineTileSourceBase` subclasses in the factory
 * (`TileSourceFactory$1` and `$2`) whose `getTileURLString` emits `base + z + "/" + y + "/" + x` —
 * ArcGIS's `tile/{level}/{row}/{col}` order, not the `z/x/y` an `XYTileSource` would produce.
 * That is worth knowing because getting it backwards yields plausible-looking tiles from the wrong
 * place rather than an error. `scripts/verify-usgs-basemap.sh` requests that exact URL shape
 * against the live service and checks the response body is really an image.
 *
 * ## Tile cache separation
 *
 * Different basemaps must not mix in one view, and they don't — checked in the artifact, not
 * assumed:
 *
 * - **On disk**, osmdroid 6's default cache (`SqlTileWriter`) stores every tile in one `tiles`
 *   table keyed by `key=? and provider=?`, where `provider` is `ITileSource.name()`. Both the write
 *   path (`saveFile`) and the read path (`exists`/lookup) pass that name, so a cached Mapnik tile
 *   is invisible to a USGS lookup for the same z/x/y. The four names above are distinct, so no two
 *   basemaps here can collide.
 * - **In memory**, `MapTileProviderBase.setTileSource` calls `clearTileCache()`, and
 *   `MapTileProviderArray.setTileSource` additionally propagates the new source to each module
 *   provider. So a runtime swap — this feature's whole point — drops the previous basemap's
 *   bitmaps rather than leaving them keyed by z/x/y alone for the new source to find.
 *
 * That is why [SightingsMap] swaps sources on the existing `MapView` instead of rebuilding it: the
 * flush is osmdroid's own, on the documented path, and it covers both caches.
 */
internal fun tileSourceFor(basemap: Basemap): ITileSource = when (basemap) {
    Basemap.USGS_TOPO -> TileSourceFactory.USGS_TOPO
    Basemap.USGS_IMAGERY_TOPO -> TileSourceFactory.USGS_SAT
    Basemap.OPEN_TOPO_MAP -> TileSourceFactory.OpenTopo
    Basemap.OSM_STANDARD -> TileSourceFactory.MAPNIK
}
