# Handoff 2026-10-30-04: elevation in the trail data (for Aspen Meridian; no go to download or build)

Written by Rowan Waymark, 2026-10-30. RECORD -1822, -1824, -1831. The owner chose "File in the repo (Recommended)" as the way this reaches Aspen, who now works on a separate account.

**This is information, not a dispatch.** The owner has not given a go for any download, conversion or rebuild. Bring any step that costs disk, bandwidth or a rebuild to the owner first.

## What the owner wants
- "Trail data should include elevation." The route files the app downloads should carry heights. Plotted routes can then show climb, descent, an elevation graph and the high and low point (RECORD -1822).
- "Have the elevation use the online info for now also. Until we get elevation done by Aspen" (RECORD -1824). Until then the app uses an online lookup, and **the release does not wait** for this work.

## What already exists
- **Juniper's sizing:** docs/navigation/2026-10-21-elevation-sizing.md (Forager, RECORD -1501).
  - Source: USGS 3DEP 1″, public domain, covering the US and almost all of Canada. Recommendation: build 3″ first.
  - Unmeasured: the `.bef` store size (2-6 GB at 3″, 20-45 GB at 1″), the one-off download of about 128 GB, and the provenance of the Canadian tiles (MRDEM as the fallback).
  - The shipped hiking-mountain.brf has `consider_elevation = false`. Banding is unaffected.
- **The build code** is on Forager origin/pi-build (map-build/). The rebuild list item for elevation is item 3 of docs/audits/2026-10-21-next-rebuild-list.md on pi-build (filed, not ruled).

## The app side (Forager's, not Aspen's)
- Once the route files carry heights, an app coder keeps the engine's per-point heights in routing/BRouterTrailRouter.kt and adds a field to TrailRoute. This is step 4a of docs/plans/2026-10-29-waypoint-route-plotting-plan.md on records-after-173.
- What the app needs from Aspen:
  - when a build with heights is live;
  - whether the file names, squares or format change;
  - how a phone can tell a file with heights from one without (for example a build run id).

## Suggested first step, for the owner's go
Measure the store size and the download volume at 3″ for one five-degree square, and report the figures before anything larger.
