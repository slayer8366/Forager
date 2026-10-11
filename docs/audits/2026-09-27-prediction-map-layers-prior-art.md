# 2026-09-27: how prediction and outdoor maps present layers (prior art)

Research by a subagent in the cloud planner session, for the map layer framework the owner asked for ("Prepare the map for layering framework also. We are going to improve the forecast methods with layering based on several conditions, similar to how other prediction maps do."). Condensed by the planner. **Trust:** the backcountry map app, backcountry navigation app support, hiking app and HuntWise help centres returned HTTP 403 (a Cloudflare challenge) and the Wayback Machine was blocked, so facts from those pages come from **search-result snippets only** and are marked [snippet]. No Material 3 guidance on layer pickers was found (the M3 site rendered nothing without JavaScript). Everything else is from pages read, dated where the page showed a date.

## Layer controls in outdoor and map apps

| App | Control | Basemap vs overlay | How many at once | Opacity / order | Legend / explanation | Offline |
|---|---|---|---|---|---|---|
| Mainstream maps app A (Android) | "At the top right, tap Layers" | Map type pick-one; map details multi-select tiles | several details on mobile | none | not documented | not documented |
| Mainstream maps app B [snippet] | "Map Modes button at the lower right" | pick one mode | one | none | - | - |
| Backcountry map app [snippet] | "Layers menu in the lower left" | basemap vs overlay (transparent background) | many | per-layer transparency slider; drag to reorder | not found | downloads the sources toggled on; a layer shows only where downloaded |
| Backcountry navigation app, hunting edition (blog 2023-10-13) | "Map Layers button", ~120 layers by state and category | three basemaps separate from overlays | many; "Turn on only the Map Layers you need" | green toggles; opacity not documented | per-layer "Details" [snippet]; legends section [snippet] | saves all layers in the area whether on or off [snippet] |
| Topographic mapping app (blog 2026-01-07) | mobile: layers icon upper right | base layers and overlays; base layers stackable; 2026 "Layer Catalog" | no maximum stated | per-layer opacity | legends listed at the map's bottom; "Show Legend" on mobile | some layers cannot be downloaded |
| Hiking app [snippet] | icon at top right | map types vs overlays | multiple | - | separate legend article | - |
| Windy (tutorial 2024-09-28) | layer menu at the side | **one colour layer at a time**; isolines, particles as add-ons | one field; moderator: colours would mix | - | legend under the time slider; tap a point for its value | - |
| FishTrack | - | pick one primary image, line/vector overlays on top | one field plus lines | adjustable colour range | bullseye readout of the value | gaps handled by a separate gap-filled product |

## Foraging and hunting prediction maps

| App | Conditions | Separate or combined | Explanation and gaps |
|---|---|---|---|
| Forayz (PNW) | soil temperature and moisture, 7/14/30-day precipitation, normals, snow, smoke, burns, harvest, 22 tree species, ecoregions | **separate** colour-ramped layers, each with its own legend | habitat layer labelled as where mycelia probably live, not a forecast of fruiting; long-press drops a pin with the conditions at that point |
| GeoForager (US, ~2025-02) | burns and severity, snowpack, precipitation, logging, one morel probability layer | mostly separate, plus one combined layer and an A-D rating per fire | not documented |
| Backcountry map app's morel blog (2019-05-21) | burns, cuts, hillshade, precipitation | the user combines them by stacking and transparency | - |
| Waldschatzfinder (DE, 2026) | rain, soil temperature and moisture vs normal, pH, elevation, forest type, heat and dry days | **both**: a 0-100% score per species, and conditions "als eigene Kartenebenen" (as their own layers) | says where data is measured and where modelled; framed as "eine Orientierung, kein Versprechen" (a guide, not a promise) |
| FungiFind (EU) | temperature, rain, humidity, wind vs a species profile | one combined score as a colour gradient | method withheld; shows where conditions fit, not where a mushroom is |
| Pilz-Radar (DE) | soil temperature, rain, humidity, rain recency | combined, with **published weights** 30/35/20/15% | daily update; gaps not stated |
| SkyForest | rain, temperature, humidity, wind | a match % against the user's reference days | per-factor values beside the %; user-adjustable weights |
| HuntStand (2026-06-10) | 12+ variables | one 0-100% score, **as charts, not a map layer** | defined bands; per-factor hourly charts; weights not disclosed |

## Shared conventions (what a user would expect)

1. One layers button over the map, opening a list or sheet; its corner varies by app.
2. The basemap is a pick-one choice; overlays are separate multi-select toggles, "on" marked with colour.
3. Large catalogues are grouped into categories.
4. Tapping (or long-pressing) a point reads the value there.
5. Mushroom apps draw conditions as continuous colour ramps with a legend each; a combined result is a 0-100 score on a ramp, with a note that it describes conditions, not finds.

## Where apps differ, and the cost

- **Several colour fields at once** (the backcountry map, topographic mapping and backcountry navigation apps; colours mix, clutter) **vs one colour field plus line overlays** (Windy, FishTrack; comparing means flipping).
- **Opacity and drag reordering** (the backcountry map and topographic mapping apps; longer rows) vs none (mainstream maps apps A and B). The topographic mapping app's 2026 roomier redesign drew mostly negative feedback on density.
- **Combined score vs separate conditions**: a score is easy but opaque; separate layers are transparent but leave the combining to the user; Waldschatzfinder does both.
- **Explaining the score**: nothing, bands, published weights, per-factor values, or user weights.
- **Missing data**: almost no page says how gaps are drawn.
- **Offline**: the backcountry navigation app saves all layers, the backcountry map app only those on at download, the topographic mapping app some cannot be saved; no per-layer offline badge was found (unverified, not proven absent).

## The researcher's recommendations (inference, not fact)

A pick-one basemap section and multi-select overlays in one bottom sheet from one layers button, placed with Forager's existing button cluster; forecast colour fields pick-one within a "Forecast" group, with line or polygon overlays stacking on top; if a combined layer is built, each input condition as its own layer too, and a per-condition breakdown on tap; no-data drawn explicitly, never as a low score (which also follows this repo's "explicit unsupported, never a fabricated value" rule); offline badging per layer left to the owner.

## Sources

Mainstream maps app A help (layers, Android) https://support.google.com/maps/answer/3092439 ; 9to5google 2023-06-14 https://9to5google.com/2023/06/14/google-maps-layers/ ; Apple Support [snippet] https://support.apple.com/guide/iphone/set-your-location-and-map-view-iph10d7bdf26/ios ; Backcountry map app help [snippet], articles 115003525027, 4405007039511 and 360006420753 ; its blog 2019-05-21, the morel mushroom hunting map post ; Backcountry navigation app, hunting edition: its layers tutorial and its blog post on understanding and organizing layers, support [snippet] articles 360052334852 and 115007554328 ; Topographic mapping app: its training page on using layers and its blog 2026-01-07 on the improved map layers UI and the new layer catalog ; Hiking app [snippet] support article 37228180990228 ; Windy community https://community.windy.com/topic/15861/multiple-overlays-at-the-same-time and https://community.windy.com/topic/42168/multiple-layers-request-follow-up , tutorial https://www.francescogola.net/tutorial/how-to-use-windy-com-for-landscape-photography/ ; FishTrack https://www.fishtrack.com/fishing-charts ; Forayz https://salishmushrooms.com/forayz/ , https://salishmushrooms.com/free-mushroom-map/ ; GeoForager https://geoforager.com/burn-morel-maps/ ; Waldschatzfinder https://waldschatzfinder.de/ ; FungiFind https://fungifind.org/en/ ; Pilz-Radar https://p44gk0gg48gw484kkgsk8woo.hueatlas.com/ ; SkyForest https://skyforest.ai/ ; HuntStand https://www.huntstand.com/fieldnotes/deer/huntstands-whitetail-activity-forecast-tool/ ; HuntWise https://huntwise.com/features/huntcast . Not readable: the Modern Forager precipitation scorer (empty page), Mushring (no display details).
