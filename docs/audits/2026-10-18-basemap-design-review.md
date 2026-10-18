# Design review: Forager's own basemap (Elder Ford, read-only, 2026-10-18)

Filed by the planner as received (Forager RECORD -1267, -1271), with one change: in decisions 3 and 4, two named public places were replaced by "a known trail area" and "over a forest", to keep place names out of the public repository. Elder ran on Fable, read-only; no render was seen, so visual claims are from the style's values unless they cite the 2026-10-04 label check.

**What this is.** A design review, not a promotion audit, for RECORD -1267 ("Basemap first... It's the design that we will need to work on"). Read-only: no files written, no PR, no Gradle, nothing run on a phone. Everything below is read from style JSON, Kotlin and the plans; **no render was seen**, so every visual claim is "from the style's values, not seen on a phone" unless it cites the label-check report (which was seen on the S22 on 2026-10-04).

**Base verified.** `origin/pre-main` = `e61e248a`, equal to `origin/main` (both carry `RECORD.md` to -564, 12,247 lines). -1267 is not on pre-main; it was read from `origin/records-after-173` (`RECORD.md:21619-21635`), where the record runs to -1267. The plan (`docs/plans/trail-navigation-and-own-tiles.md:23,56`) cites -1172/-1176, so pre-main's docs already reference records pre-main's RECORD.md does not hold. Noted, not a finding against anyone.

## Premises in the dispatch that are wrong or stale

1. **"Satellite from the USGS National Map" does not exist today.** Satellite was removed by dispatch 2026-09-28-708 (`Basemap.kt:44-55`, `MapMode.kt:16-18`); anyone who had it is moved to Topographical. The map offers exactly two looks, Street and Topographical (`MapMode.kt:36-37`), opening on Topographical (`MapMode.kt:42`). The owner's words at removal: "Satellite loses quality fast and is less useful when zoomed in", "Remove now, revisit later (Recommended)", to be revisited once the forecast's habitat layers are on the map.
2. **"Label fonts from MapLibre's demo server" is true but narrower than it sounds:** the only text drawn from that server today is the app's own numbered area markers (`BasemapStyles.kt:18-24, 341-342`). The raster basemaps carry their labels baked into the tiles.
3. **Offline maps today have no labels at all.** `server/pmtiles-worker/src/offline-style.json` has 57 layers, all background/fill/line, no `glyphs`, no `sprite`, no symbol layer (compared layer-by-layer against `map-style/forager-light.json`, which has the same 57 plus 14 symbol layers, `forager-light.json:2232-11919`). The Worker serves no fonts or icons at all (`index.ts:109-122, 334-335`: only `/style/offline.json`, TileJSON and tiles). So "offline downloads use the owner's own vector tiles, drawn with a style" is true, and the style is a label-less one.

## 1. What the user sees today, and what changes

**Street (OpenStreetMap raster, `Basemap.kt:126-132`, zoom to 19).** The familiar web look: every shop, cafe and bus stop as an icon, house numbers from about z17, building footprints, bright yellow/orange/white roads with dark casings, pale green woods with tree symbols, footpaths as dashed red-brown, fonts baked in and fixed in size, labels that turn with the map when rotated. US-wide and worldwide.

**Topographical (OpenTopoMap raster, `Basemap.kt:113-124`, zoom to 17): the default.** Brown contour lines with elevations, grey-green hillshade, forest in green, paths dashed red in the Alpine-club style, peaks and huts. Sharpest around z13-15; at z16-17 it is resampled and soft. Worldwide. Below map zoom 9.5 the tiles change character to tinted lowland/hill colours (`BasemapStyles.kt:94-101`).

**Night (both looks).** Lightness inverted, hue kept, by the V1 raster paint (`BasemapStyles.kt:57-62`): pale ground goes dark, dark linework goes pale, greens stay green. At topo night below zoom 9.5 the map is the Street tiles instead, crossfading to topo over 9.5 to 9.7 (`BasemapStyles.kt:82-83, 137-138`); the owner chose this over a map that turns light when zoomed out.

**Offline (the "use offline tiles" switch on).** Whichever look was chosen, the map becomes Protomaps' light style with no text and no icons (`BasemapStyles.kt:239-240`, `OfflineStyle.kt:18`), zoom to 15 tiles, with night applied by recolouring fills and lines (`NightColour.kt:18-24`). Roads with no names, towns with no names, no POIs, no contours.

**What changes for the user when Street becomes Forager's own vector map (as `map-style/forager-light.json` stands, with `overrides/forager-colours.json` empty, so exactly Protomaps' light look):**

Gains: crisp at every zoom and rotation, labels stay upright, labels appear offline for the first time (proved on the S22, `docs/audits/2026-10-04-label-check-report.md` on branch `label-check`, lines 16-19, 70-72), online and offline look identical, and the app stops depending on OpenStreetMap's tile server, whose policy says access "may be withdrawn at any point" and requires an app-naming User-Agent (`docs/audits/data/2026-09-30-topo-night/renders/README.md:50`).

Losses, as the style stands:
- **The OpenStreetMap raster look.** Protomaps light is a quiet, grey-beige, low-contrast web style (earth `#e2dfda`, roads white or `#ebebeb`, `forager-light.json:25, 1283, 1518, 1621`). It is designed to sit under someone else's data. It is not an outdoor map.
- **Paths nearly vanish.** Every path, track, footway and service lane is one layer, `roads_other`, drawn from **z14 only**, at **0.5 px** at z14 rising to 12 at z20, colour `#ebebeb` on `#e2dfda` ground (`forager-light.json:1283-1327`). Street raster shows footpaths dashed red from about z13. For a forager the path is the map; this is the single biggest regression.
- **No path kinds.** Track vs footpath vs steps are not told apart (one filter, `kind in other, path`). Whether the tiles carry the `kind_detail` to do so is unverified (the style uses `kind_detail` for `pier`, `runway`, `service`, so the field exists).
- **POIs are a city list.** The `pois` filter admits 36 kinds (`forager-light.json:7790`): restaurants, cafes, bars, shops, museums, bus stops, plus forest, park, peak, toilets, drinking_water, bench. **Not in the list: trailhead, parking, campground/camp site, viewpoint, picnic site, ranger station, information board.** Whether the tiles carry those kinds is "could not determine" (below).
- **House numbers** stay, from z18 (`address_label`, `forager-light.json:2232`); raster Street shows them from about z17. Minor.
- **Forest cover thins between z7 and z11.** `landcover` fades out from z5 to z7 (`:39`, fill-opacity 1 to 0) and `landuse_park` fades in from z6 to z11 (`:80`); between them woodland is faint. Raster Street and topo both show forest solidly at those zooms.
- **Buildings** are a 50% grey wash, no outlines (`:952`), fine.
- **The topo's contours and hillshade** are not lost, because Topographical stays on OpenTopoMap (-466 item 6). Street never had them.
- **Zoom ceiling.** Own tiles are built to z15 (-466 item 4); the vector engine overzooms cleanly, but no new detail appears past 15. Street raster goes to 19. The app's operating limit (`Basemap.maxZoom`) must be chosen anew for the vector Street.
- **Coverage.** Own tiles start as Oregon and Washington (-492). Street raster is worldwide. A user in Idaho, California or anywhere else at launch would see **nothing** on Street unless the plan says what happens outside the built area. See Blocker 1.
- **Credits** change from "© OpenStreetMap contributors" to "Protomaps © OpenStreetMap" plus a licence line for Noto Sans (OFL) and the icon set (MIT) somewhere in the app (`map-style/README.md`, "Versions and licences"; the survey §3 notes the bundled fonts have no licence recorded).

## 2. The design work needed

Each item says **Blocker** (must be done before the own basemap replaces raster Street for everyone) or **Follow-on**.

**Colours, day.** Blocker. Protomaps light is tuned for a web dashboard, not sunlight: ground `#e2dfda`, forest `#9cd3b4`/`#a0d9a0`/`#99d2bb` (three greens within a few steps of each other, `forager-light.json:80-203`), roads white. Needed: a ground that reads as land outdoors (a warmer, slightly darker cream), one clear tree-cover green distinct from grass/scrub, park and protected-area boundaries as an outline rather than a competing fill, water a deeper blue than `#80deea` (`:386`). The override file gives 72 named colours (`README.md`), enough for this pass without touching generator code.

**Colours, night.** Blocker, and it constrains the day design. Night is a pure V1 inversion (-314, -322); I checked it against the style's actual values. Ground `#e2dfda` becomes `#24211c` (the same night ground `MapPalette.kt:129` already measures markers against), forest becomes `#2e6546`, water `#005864`: all good. But **white roads become `#000000` and `#ebebeb` paths become `#141414`, on a `#24211c` ground: roads and paths disappear at night.** The raster night survives this today only because OpenStreetMap's roads carry dark casings that invert to light. So the day palette must be designed with its inversion beside it: roads a mid tone with a casing that inverts to something visible, or coloured (the Street raster's yellow/orange inverts to blue/violet, which is what the owner accepted on raster). This is not a reason to touch the inversion ruling; it is the ruling's consequence for the day design.

Also night, blocker: the offline night recolour skips symbol layers (`NightColour.kt:70-72`, "Symbol layers (text, icons) ... are not walked"). With a labelled style, text would stay day grey with a **white halo on a near-black ground** at night, and icons would stay day colours. The recolour needs text-color and text-halo-color walked (and icon-color if the sprites are SDF; unverified). The survey already flagged this (`2026-10-03-own-tiles-survey.md:13, 35`).

**Roads, paths and trails as a forager reads them.** Blocker. The forager's hierarchy is the inverse of a driver's: footpath and track first, forest road second, highway last. Needed: paths from z12 (not 14), at least 1.5 px at z14, in a colour that reads on green and on cream (a red-brown or dark orange is what every outdoor map uses), **dashed for footpaths, dash-dot or solid thin for vehicle tracks, dotted for informal/indistinct**, steps as ticks. Highways de-emphasised: thinner, muted, no bright fills. Service roads and driveways faint. If `kind_detail` carries the highway value in today's tiles, the three-way split (path/footway vs track vs steps) can ship at launch; if not, it waits for the own build's attributes (`prompts/preserved/2026-10-04-06.md:19`: `sac_scale`, `trail_visibility`, `informal`, `tracktype`, `surface`, `foot`, `access`).

**Forest and land cover.** Blocker for the fade gap, follow-on for the rest. Close the z7-z11 gap so woodland is solid from z6 up (`landcover` and `landuse_park` opacity ramps). Follow-on: tell conifer from broadleaf if the tiles carry `leaf_type` (unverified; OSM has it, Protomaps v4 may not), which matters to a forager more than to anyone else; wetland and scrub as distinct patterns; clear-cuts (`landuse=logging`, rare in OSM) later.

**Water.** Follow-on, small. Streams only from z14 at 0.5 px (`:400`); a forager reads a stream line as a habitat clue. Show streams from z13 at 1 px; keep river width ramp; intermittent streams dashed if tagged. Water label italic blue on blue halo (`:2260`) is fine.

**Labels: font, size, outdoors, gloves.** Blocker for size and contrast, follow-on for typeface. Today: Noto Sans Regular 12 px for roads and paths, grey `#91888b` on a 1 px white halo (`:3177, :6891`); shields 8 px; neighbourhoods uppercase 8 px at z11 (`:8894`). In sun at arm's length 12 px light grey is marginal and 8 px is unreadable. Needed: path and road names at least 13-14 px, text near-black (`#3a3a3a`), halo 1.5-2 px; place names bolder with letter-spacing; shields 10 px or dropped (a forager rarely needs route numbers; they clutter forest roads). Keep Noto Sans (OFL, already licensed and bundled, and it renders offline, label-check line 62-64). "With gloves" is not a label matter; labels are not tapped. It is the markers' hit areas and the chrome's `MIN_TOUCH_TARGET`, unchanged by this.

**Points of interest.** Blocker for the list, follow-on for icon design. Replace the city list with the forager's: trailhead, parking (lots, not street), campground/camp site, picnic site, viewpoint, peak, toilets, drinking water, ranger station/visitor centre, information board, shelter, gate/barrier (a locked gate changes a walk). Keep towns' restaurants/fuel only at z16+ or drop them. Icons from the existing MIT set where they exist; a trailhead icon may need drawing. Whether the current tiles carry these kinds is "could not determine"; the own build can be told to emit them.

**The app's own marks on top.** Blocker for a re-measurement, not a redesign. `MapPalette` colours were chosen on a swatch board measured against the **raster** grounds of both basemaps in both modes (`MapPalette.kt:19-25`, `docs/audits/2026-09-27-marker-swatch-board.md`), and `MapPaletteTest` pins those figures as ratchets. A new ground changes every ratio. The magenta family (find `#DA02AF`, waypoint `#350560`, kept track `#A122F8`, breadcrumb `#650BB1`) was picked to sit apart from map greens and blues, and nothing in the new style uses magenta, so the choice should survive; but the figures need re-measuring against the designed ground, day and night, and any path colour chosen (red-brown) must stay well clear of the find pin's hue. Tracks also now compete with vector paths: a kept track drawn along a trail must be told from the trail (track wider, solid, cased; trail thinner, dashed). The journal-entry halo `#005577`/`#00DDFF` and selected ring `#2196F3` are blue; keep water out of their range.

**Map chrome at 80%.** Follow-on check, no redesign. Chrome composites to 0.8 (`MapChrome.kt:288`, `journal-redesign.md:601-651`); it is the ground that changes, not the chrome. A paler, flatter ground shows through chrome more evenly, which helps. Two checks on the phone: the icon bar and bottom tab bar over the new cream ground by day (text contrast on 80% surfaces over a light ground), and the attribution caption at 0.55 (ruling 4) over the new ground, since the credit line gets longer ("Protomaps © OpenStreetMap" plus any topo credit at topo night).

**Zoom ranges.** Blocker for the limits, follow-on for polish.
- z0-6: country/state view; fine as is.
- z7-11: the drive-to view. Woodland gap (above), national forest and park names should appear here (`landuse_park` has no label layer in the style at all; protected-area names are a follow-on worth doing early: a forager navigates by forest name).
- z12-14: the trailhead view. Paths must exist here; today they start at 14 at half a pixel. Campgrounds, parking, trailheads here.
- z15-17: the walking view. Path kinds, stream lines, gates, path names.
- z18+: overzoom of z15 tiles; nothing new appears. Set `Basemap.maxZoom` for the vector Street to 18 (operating limit, not reported range, per CLAUDE.md); past that the user sees nothing new and the raster Street's 19 was never useful for a walker.

## 3. Topo and satellite

**Topo.** Stay on OpenTopoMap at launch, as -466 item 6 rules, with two cautions the plan should carry. (a) **Topographical is the default basemap** (`MapMode.kt:42`), so at a public launch most traffic goes to OpenTopoMap, a volunteer server that publishes no limit, moved to a smaller vServer in January, asks "bigger projects" to get in touch, and says it will move to vector tiles (`renders/README.md:45`). Either the default moves to the own Street once its design passes, or someone writes to OpenTopoMap before launch, or both. (b) Topo night below zoom 9.5 draws Street underneath (`BasemapStyles.kt:137-138`); once Street is vector, that is a raster topo crossfading over a vector street with labels, and the vector's labels would show through the topo above 9.7 unless the symbol layers get `maxzoom` 9.7 for that composition. The survey (:33) already says the crossfade must be re-derived. Own contours and hillshade from 3DEP are a follow-on (survey §4: hillshade 0.7-2 GB for OR+WA, contours low hundreds of MB; contours must be pre-built as vector tiles since the contour plugin is web-only). Minimum topo design when it comes: brown contours at 40 ft/20 m with index lines labelled, hillshade at low opacity under land cover, and the same path/POI layers as Street so switching looks changes relief only.

**Satellite.** Not for launch; the owner removed it and tied its return to the habitat layers. What a forager wants from it is canopy: closed vs open, conifer dark vs broadleaf light, clearings, logging, riparian strips. Minimum design when it returns: the own vector paths, water lines, POIs and labels drawn over the imagery (a "hybrid"), labels with heavy dark halos, no night inversion (the owner's earlier exemption), US-only with a plain message at the edge, and operating zoom 16 as measured before (`Basemap.kt:68-81`).

## 4. Consistency

**Online and offline the same.** Achievable and the main prize. One style document at one URL, served by the Worker, with `glyphs` and `sprite` on the same domain (today's Worker serves neither; the label-check used the laptop and Protomaps' GitHub). The offline download then stores fonts and icons with the tiles (97 glyph ranges per font, about 6 MB for a 2 x 2 km pack, label-check lines 56-64). Existing regions downloaded against `OFFLINE_STYLE_URL` keep working only while that URL serves (constraint in the spec, line 44); a migration plan for old regions is needed, or they are re-downloaded. Night must then be the same transform both ways: today raster night is a shader and offline night is a recolour; with one vector style both become the recolour, which is simpler, provided it walks symbol colours (above).

**Night as a pure inversion (-314/-322).** Holds for ground, forest and water (figures in section 2). Fails for white roads and pale paths, and for unrecoloured labels. The remedy is in the day palette and in extending the recolour, not in bending the ruling.

**The trail map fitting on top later.** Design the path hierarchy now with empty slots: path/track/steps by `kind_detail` at launch; `sac_scale`, `trail_visibility`, `informal` as dash and colour variants when the own build lands; the Forest Service/BLM reference layer as a distinct thin dashed overlay in a colour reserved now (not red-brown, not magenta, not blue: a dark teal or olive). The route line (`RouteHomeLayers.kt`) and off-trail dimming (-445) then have known colours to sit against. Nothing in the chrome changes.

## 5. Prior art (described generically)

- **Outdoor/hiking map apps** draw footpaths as red or red-brown dashes with dash pattern by difficulty, vehicle tracks as dash-dot brown, highways muted and thin; tree cover as one solid mid green with a distinct lighter tone for grass and a pattern for wetland; contours brown with labelled index lines; hillshade subtle; labels dark with a 2 px light halo, 13-15 px on phones; trailheads, parking, campsites, viewpoints and water sources as the first POIs to appear (z12-13), shops only in towns at z16+. Forager's style as it stands differs on all of these (paths half a pixel, city POIs, 12 px grey labels).
- **Dark modes** in those apps are designed dark palettes, not inversions; roads stay light on a dark ground by design. Forager's pure-inversion ruling is unusual; it can work, but only if the day palette is chosen with the inversion alongside, which is the constraint in section 2.
- **Satellite** in outdoor apps is always "hybrid": vector trails and labels over imagery. Forager's removed satellite was bare imagery, which is part of why it was found less useful.
- **Foraging-specific apps** mostly default to satellite or hybrid with heat or habitat overlays and large, bright pins for records; their basemaps are a licensed third party's. Forager's finds-as-magenta and its own map are both uncommon and defensible.
- **Chrome** in those apps is opaque; Forager's 80% rule is its own and is unaffected by the ground change.
- **Default basemap:** outdoor apps default to their own outdoor/topo look; Forager defaults to a third party's topo. Moving the default to the own Street once designed matches the convention and the traffic concern.

## Blockers for launch (own basemap replacing raster Street for everyone)

1. **Coverage decision.** OR+WA own tiles vs a national launch: say what a user outside the built area sees (automatic fallback to the raster Street with its credit, or the whole US built before the switch). A blank map is the worst case under "Don't make me think".
2. **Paths visible and classed** from z12, at walking width, in an outdoor colour, dashed by kind.
3. **Day palette designed with its inversion beside it**, so roads and paths survive night; and the recolour extended to text, halos and icons.
4. **Label size and contrast** for sunlight: 13-14 px, dark text, 1.5-2 px halo; shields reduced or dropped.
5. **A forager's POI list** replacing the city list (at least trailhead, parking, campground, viewpoint, picnic, toilets, water, peak).
6. **Woodland gap closed** between z7 and z11.
7. **Fonts and icons served from the owner's domain** and included in offline packs; old offline regions' path stated.
8. **Marker palette re-measured** against the new ground, day and night (`MapPaletteTest` ratchets will move).
9. **Operating zoom limit** chosen for the vector Street (recommend 18).
10. **The default-basemap/OpenTopoMap traffic question** answered before a public launch (default to own Street, or contact OpenTopoMap, or both).

## Follow-ons

Protected-area and forest name labels; conifer/broadleaf distinction if the data carries it; stream lines earlier and intermittent dashes; wetland and scrub patterns; gates and barriers; topo-night crossfade re-derived for a vector street beneath; own contours and hillshade (3DEP); satellite as hybrid when habitat layers arrive; trail attributes and the reference layer's look; attribution caption length at 0.55 over the new ground; a licence screen listing Noto (OFL), the icon set (MIT), Protomaps (CC0 design, ODbL data), OpenTopoMap while it remains.

## Decisions for the owner (plain words, with my recommendation and the phone check)

1. **Where the own Street map is allowed to show.** Recommend: wherever Forager has tiles, and the old street map automatically everywhere else, until the whole US is built. Check: open Street in Portland, then drag to Boise; the map must never go blank, and the credit line must change.
2. **Which map the app opens on at launch.** Recommend: Forager's own Street, once its design passes; Topographical stays one tap away. Check: fresh install, open Maps, confirm which map shows and that the Layers sheet switches.
3. **How paths look.** Recommend: dashed red-brown footpaths, dash-dot brown vehicle tracks, dotted faint informal paths, visible from the zoom where a trailhead fits on screen. Check: a known trail area at three zooms, outdoors in daylight; you should pick out the trail from the forest roads without zooming in.
4. **Forest colour.** Recommend: one solid green for tree cover from the state view down, a lighter tone for grass and scrub, park boundaries as an outline. Check: zoom from state to town over a forest; the forest must never fade out part way.
5. **Label size and darkness.** Recommend: a little bigger than today's web style, near-black on a light halo. Check: outdoors in sun, phone at arm's length, read a path name without squinting.
6. **Which points of interest appear.** Recommend: trailheads, parking, campgrounds, picnic sites, viewpoints, peaks, toilets, drinking water, ranger stations; shops and cafes only when zoomed into a town. Check: a trailhead area at mid zoom shows parking and the trailhead before any cafe.
7. **Night.** Recommend: keep pure inversion; design the day colours so roads and paths still show after inversion, and invert the labels too. Check: Night Maps on, same spot as decision 3; roads, paths and names must all be readable on the dark ground.
8. **How far to zoom.** Recommend: stop at 18 (nothing new appears past the built detail). Check: pinch in until it stops; the map should still look sharp, not blurred or blank.
9. **Topo at launch.** Recommend: keep OpenTopoMap for now, and either move the default off it (decision 2) or write to its maintainers before launch; own contours later. Check: switch to Topographical; nothing changes from today.
10. **Satellite.** Recommend: not at launch; when it returns, trails and labels drawn over the imagery. No check now.
11. **Old offline downloads.** Recommend: keep them working until the user re-downloads, with a one-line notice in the offline regions sheet, not a forced re-download. Check: an existing region in aeroplane mode still draws after the update.

## Could not determine

- Any actual render of the own style on a phone or in the preview; this review reads the style's values. The only renders seen were the label-check's (S22, 2026-10-04, Protomaps light unchanged).
- Whether today's Protomaps tiles carry `kind_detail` values for path/footway/track/steps, `leaf_type`, or the POI kinds trailhead, parking, camp_site, viewpoint, picnic_site, information; the style's filters are the known limit, the tile schema was not read.
- Whether the own OR+WA build (pi-build part 1, -495) has run, or whether `tiles.zynergy-labs.com` serves anything; pre-main holds only the Pi-origin part 1 report and the dispatches.
- Whether the icon sprites are SDF (recolourable by paint) or fixed-colour PNG; this decides how icons get their night look.
- Whether `net/AppUserAgent` exists and is sent on tile requests (the plan says so at `trail-navigation-and-own-tiles.md:84`; not read).
- Juniper's usage-policy and Cloudflare-cost research (-1267 input); not on pre-main.
- The current contrast figures of `MapPalette` against the new ground; the swatch-board method exists but was not re-run here.
- Why pre-main's `RECORD.md` stops at -564 while records to -1267 exist on `records-after-173`; stated as observed, not diagnosed.
