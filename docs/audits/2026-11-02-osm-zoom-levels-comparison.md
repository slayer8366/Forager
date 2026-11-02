# Our map at OSM's zoom levels: the comparison (dispatch 2026-11-02-03, step 1, read only)

Juniper, 2026-11-02, started 14:25 PST. Dispatch: `prompts/preserved/2026-11-02-03-osm-zoom-levels.md` on
`origin/records-after-173` (sha256 begins `9f8cfa6809d7`, read). Draft: sections fill in as they are read.

**Bases (read):** origin/main `8d0eefdbc` (2026-11-01 21:45 PST) and origin/pre-main `04746d8f0` (2026-11-02
10:58 PST), both exactly the dispatch's bases. Branch `osm-zoom-levels` cut from origin/records-after-173 `8900e8843`.

**What counts (the owner, to Aspen, 2026-11-02 between 14:06 and 14:13, relayed by Aspen verbatim):** "The style
to match is the zoom levels that objects, icons, names, etc begin appearing on the map when zooming in". So the
figure compared is the first zoom at which each thing appears. Colours, look and hill shading are out.

## A. What "OSM" and "our map" are

### A1. "OSM" is openstreetmap-carto v6.1.0

- **The app's map named "OpenStreetMap"** is `Basemap.OSM_STANDARD`, label `"OpenStreetMap"`, tiles
  `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, maxZoom 19
  (`app/src/main/java/com/zynergylabs/forager/app/ui/map/Basemap.kt:169-174`, pre-main `04746d8f0`, read).
- **Those tiles are drawn with openstreetmap-carto v6.1.0.** OSMF's server configuration pins
  `:revision => "v6.1.0"` for the default style at `roles/tile.rb:104` (openstreetmap/chef `049ef98`,
  2026-11-02 11:11 UTC, read), set by chef commit `e2b54e4` "Update to osm-carto v6.1.0", 2026-09-11 (read).
- **The tag read:** openstreetmap-carto `v6.1.0`, commit `0e52579` (2026-09-10), a released tag (read). It is
  also the newest tag: `v6.0.0` and `v6.1.0` follow `v5.9.0` (`git ls-remote --tags`, 14:26 PST, read).
- **A premise that was wrong:** Aspen's note gave `v5.9.0`. That is the newest `v5` tag, but the tile servers
  left 5.x on 2026-04-12 (chef `0c78316` "Drop support for carto 5.x and default to flex output", read) and
  run v6.1.0.

### A2. "Our map": the styles the app draws as its own

(Filling in.)

## B. The comparison

(Filling in.)

## C. What the change would need

(Filling in.)

## Disclosures

### Confirmed vs inferred

(Filling in.)

### Could not determine

(Filling in.)

### Premises that were wrong

- `v5.9.0` as OSM's style: the tile servers run `v6.1.0` (A1).

### Decided beyond scope

(Filling in.)
