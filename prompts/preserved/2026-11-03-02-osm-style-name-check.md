# Dispatch 2026-11-03-02: where "OSM" is used as a style name in anything public (Juniper, Fable)

Written by Rowan Waymark, 2026-11-03. RECORD -1987. The owner, on the Trail Map name (-1985): "OSM refers to the style, but we don't want to publish that anywhere since it's an inspiration and not the target to copy directly." Asked whether Juniper should check, the owner chose "Yes, check (Recommended)", whose option read "Juniper only reads and reports where it appears. Nothing gets changed until you've seen the list."

Read-only. Change nothing, in any repository or service.

## A. Where to look
- **A1.** The Forager app's user-facing words on origin/main and origin/pre-main: string resources, Compose text literals, content descriptions, Settings and Tools labels, map layer and basemap names, the in-app credits (Tools > Map Layers), the onboarding text, and anything exported (GPX, backups, share text).
- **A2.** The store listing text kept in the Forager repository (fastlane, docs, or wherever the store description lives; say where you found it, or that you found none).
- **A3.** The site: ~/Zynergy/zynergy-site on its default branch (the local clone; fetch first, read only). Pages, Functions output, meta tags, privacy and credits pages.
- **A4.** Public files in Forager that ship or are served: the README, map style JSON served by the tile Worker (server/pmtiles-worker), style names and metadata fields in those styles.

## B. What to report
- **B1.** Every place "OSM" or "OpenStreetMap" appears, each with file:line (or URL), the exact text, and one of:
  - (i) the style named as ours or as a target (e.g. "OSM style", "OSM-like", "matches OSM");
  - (ii) the OpenStreetMap reference basemap, by name;
  - (iii) attribution or a licence credit for OSM data. Required by the licence; flag it, never propose removing it;
  - (iv) internal only (code identifiers, comments, tests, records), not public.
- **B2.** Group (i) first. For each, say whether a user, store visitor or site visitor would see it.
- **B3.** "Trail Map": where our own map is already called that, and where it's called something else.

**Rules:**
- Mark each claim read or inferred.
- Never read, name or quote the Restricted Object.
- Never communicate with any session named Aspen.
- Our own map is the "Trail Map".
- Commit the report to docs/audits/2026-11-03-osm-style-name-check.md on a new branch, juniper/osm-style-name-check, cut from origin/pre-main, with no PR. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
