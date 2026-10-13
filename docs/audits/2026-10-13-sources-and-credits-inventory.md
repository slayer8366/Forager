# Data sources and attribution inventory (Forager main 497423d6)

Date: 2026-10-13. A read-only survey by a planner-dispatched agent: no file changed, nothing built. Asked for by the owner: "We need a part in the app that shows our sources … the data we have should be credited appropriately." Every row cites code on main or a provider page fetched 2026-10-13. A row marked could not determine was not reached.

## Summary

| Source | Used for | Licence | Attribution required | Shown today |
|---|---|---|---|---|
| OpenStreetMap raster tiles (`ui/map/Basemap.kt:131`) | Street basemap; topo at night below z9.7 | ODbL; OSMF tile policy | "© OpenStreetMap contributors" visible on the map, plus a route to the licence (a link to openstreetmap.org/copyright, or a "Data licences" menu): OSMF Attribution Guidelines | partly: named in the caption, no licence route |
| OpenTopoMap, incl. SRTM (`Basemap.kt:123`) | Topographical basemap | CC-BY-SA 3.0 + ODbL (opentopomap.org/about) | "Map data: © OpenStreetMap contributors, SRTM; map style: © OpenTopoMap (CC-BY-SA)" | partly: names present, no links |
| Protomaps vector basemap from OSM (`map/OfflineStyle.kt:18`) | Offline map packs | ODbL Produced Work; style BSD-3, design CC0; Natural Earth and Daylight landcover could not determine | OpenStreetMap attribution | partly to yes: caption, and HTML links in the style's attribution (`server/pmtiles-worker/src/offline-style.json:6`) |
| MapLibre demo glyphs, Open Sans (`BasemapStyles.kt:341`) | Map label font, online styles | BSD-3 repository; font could not determine | Software notice | no |
| **Open-Meteo forecast and archive** (`OpenMeteoClient.kt:6`, `OpenMeteoArchiveClient.kt:11`) | Rain, evapotranspiration, soil moisture and temperature | CC BY 4.0; free API for non-commercial use only (open-meteo.com/en/licence, /en/terms) | "You must include a link next to any location Open-Meteo data are displayed", e.g. "Weather data by Open-Meteo.com" | **no** (named only in an error note, `OpenMeteoWeatherProvider.kt:339`) |
| iNaturalist API (`INaturalistClient.kt:6`) | Species counts, observation points, names | iNaturalist Terms (HTTP 403, could not determine) | Could not determine; help guidance: cite iNaturalist, credit observers | partly: body text (`AvailabilityResultsUi.kt:187`), "View on iNaturalist" links |
| Fungi index, bundled, built from iNaturalist (`scripts/build_fungi_species_index.py`, `assets/databases/fungi_index.db`) | Offline species-name search | None recorded; could not determine | Could not determine | no |
| NOAA solar equations (`CivilTwilight.kt:32`) | Sunset and twilight | Algorithm, no data fetched | None (inferred) | n/a |
| World Magnetic Model, through Android's `GeomagneticField` | Declination | Platform-supplied | None (inferred) | n/a |
| forager-forecast / GBIF | Forecast map layers | CC BY-NC 4.0 (D110, not verified in this repo); GBIF citation terms could not determine | Yes; per-layer credit already flows into the caption (`MapLayerState.kt:80`) | not live (`AbsentForecastCellStore`, `AppContainer.kt:295`) |
| Own map tiles | Future basemap | n/a | n/a | not live (`docs/plans/own-map-tiles.md:3`) |
| Apache-2.0 libraries (AndroidX, Compose, Room, CameraX, Retrofit, OkHttp, kotlinx) | App code | Apache-2.0 | A copy of the licence to recipients (§4a) | no |
| MapLibre Native SDK 13.5.0 | Map rendering | BSD-2-Clause | The copyright notice reproduced in documentation or other materials | no |
| NGA mgrs 2.1.3, plus grid-java | Grid references | MIT | Notice in copies | no |
| Noto Sans, bundled (`res/font/`) | App typeface | SIL OFL 1.1 | Notice and licence with the font | partly: in the font files' metadata |

## Not found, or not live

- **Return route:** computed on the phone (`domain/RouteHome.kt:8-9`), no routing service.
- **Geocoding or place search:** none.
- **Pressure sensor and external elevation reference:** none.
- **Satellite imagery:** removed (`Basemap.kt:44-54`).

## Not verified

- What MapLibre's ⓘ dialog shows for plain-text attributions, as opposed to linked ones.
- The terms of iNaturalist (403), Natural Earth, Daylight landcover, SRTM, the Open Sans glyphs and GBIF.
- NOTICE files inside any library.
- Whether the owner informed OpenTopoMap of the app's use.
- Whether donations affect Open-Meteo's non-commercial terms. That is the owner's call.

Note: the build strips `/META-INF/{AL2.0,LGPL2.1}` licence files from the package (`app/build.gradle.kts:306-308`).
