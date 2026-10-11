# Land ownership and private property layer: research (dispatch 2026-10-11-05)

Read-only web research for RECORD -880 and -882. Nothing was built. Branch `land-ownership-research`, cut from origin/main at `fc8367cd`.

**The ask.** The owner, verbatim (-880): "I want to do property ownership and private properties to guide people away from those areas." In -882 the owner picked all four meanings of "guide away": show it on the map, warn on entering, routes avoid it, and let the research inform it. The owner added a caution partway through, relayed by the planner, verbatim: "Be careful on the warning upon entering item, look for more info that comes back. I can see that triggering by GPS being slightly off and walking on the sidewalk by someone's house." Section 5 answers that directly.

**How this was done, and how far to trust the quotes.** Four research agents and this session gathered the sources on 2026-10-11. Every cited page was opened, either with a web fetch or with `curl`. The web fetch tool passes each page through a summarising model, so quotes taken only from it may be close paraphrases. Where the exact wording carried weight, it was re-read from the raw page with `curl`/`pdftotext`; those quotes are exact. They include:

- the Oregon cadastral standard
- the Oregon DSL ownership PDF
- the Android geofencing page
- the Washington parcel licence
- the BLM Oregon/Washington ownership layer's fields and counts
- the BRouter source files
- the Siuslaw, ODF, Mount Rainier and Okanogan-Wenatchee PDFs

Anything that rests on a summariser quote alone should be re-opened before it goes into the app's own text. No sign-ups, no logins, and no large downloads were made; file sizes come from publisher pages or HTTP `Content-Length`.

---

## Summary

1. **Public land: the data is good, free and safe to ship.** PAD-US 4.1 (USGS) is public domain. It covers federal and most state and local public land, with a public-access field (Open / Restricted / Closed / Unknown). The national release is 1.42 GB; Oregon is 291 MB and Washington 247 MB, both zipped with KMZ copies included. It is released roughly once a year, so a weekly rebuild has nothing new to pick up. BLM's surface-management data, Forest Service ownership, BIA tribal trust land and Census tribal areas are likewise free to redistribute. BLM asks to be credited.
2. **Private land: in Oregon and Washington, mapping it without owner names is feasible; mapping it with names is not.**
   - BLM Oregon/Washington's "Management Ownership" layer classes every polygon in both states as federal, state, local or private. Private comes in four subtypes: individual, industrial, non-industrial, urban. The layer carries no owner names and has 269,446 polygons (checked against the live service).
   - Washington's public statewide parcel file has no owner fields. The version with owner fields is for state employees only, and some counties limit even the public file to state business.
   - Oregon has no public statewide parcel download yet.
   - The one national parcel dataset with names is commercial. Its licence forbids letting users obtain a copy, which an open-source offline download cannot prevent.
3. **Owner names: don't show them.** A Washington county strips them from its public data under state law. Neither state's address-confidentiality law automatically hides property records, so names can expose people who are hiding from someone. "Private, owner not shown" is the norm in government public datasets. The commercial outdoor apps do show names, as a paid feature.
4. **Picking rules can't honestly be read from land type.** National forests disagree on whether personal picking needs a permit, and on whether wilderness is closed. National parks range from banned (Crater Lake) to a gallon a day (Mount Rainier). Wildlife refuges are closed unless a refuge opens them. Private land in Oregon needs a written permit for any amount. An honest layer needs a lookup per unit, with source and date. Without that, it can say only the ownership and a cautious default.
5. **Show it: yes. Routes avoid it: yes, as a penalty and with care. Warn on entering: not as a simple "you are inside" alert. The owner's sidewalk case is real.**
   - In both states, a property line along a road usually runs to the road's centreline. The street is an easement over private land, so the sidewalk is legally inside the private lot.
   - Phone GPS beside buildings typically errs by 4 to 10 m, and one fix in three is outside the radius the phone reports.
   - County parcel lines carry their own error, up to about 15 m (50 ft) by a leading hunting app's own account.
   - A plain point-in-polygon test will fire on a sidewalk walk. The only honest version is narrower: it fires off roads and paths, deep inside private land for a sustained time, and admits "can't tell" near any line.
6. **BRouter can take avoid-areas.** It accepts polygons, and with a weight they become a penalty per metre travelled inside rather than a ban. Caveats: an area containing the start or a via point is silently dropped, and there is no spatial index, so thousands of polygons would be slow (inferred from the code). Its stock hiking profile already avoids OSM ways tagged `access=private`.
7. **Fit with the weekly build.** The layer is cheap to rebuild only when a source changes. A third party's national PAD-US vector-tile file is 3.6 GB (zoom 0 to 14). The Pacific Northwest share would be far smaller, but its size was not measured.

---

## 1. Data sources

### Federal: public domain or free to redistribute

| Source | What it says | Coverage, version | Format, size | Licence (quoted) | Accuracy (quoted) |
|---|---|---|---|---|---|
| **PAD-US 4.1**, USGS Gap Analysis Project ([download page](https://www.usgs.gov/programs/gap-analysis-project/science/pad-us-data-download), [national item](https://www.sciencebase.gov/catalog/item/652d4fc5d34e44db0e2ee45e), [state items](https://www.sciencebase.gov/catalog/item/6759abcfd34edfeb8710a004)) | Federal, state, local and voluntarily provided private protected areas. Layers: Proclamation, Fee, Easement, Designation, Combined, Marine. Has `Pub_Access`: "'Open' requires no special requirements…", "'Restricted' requires a special permit…", "'Closed' occurs where no public access is allowed…", "'Unknown'". Has GAP status 1 to 4 (degree of protection). | US and territories. Published 2025-03-31. Update cadence "Annually". Real gaps between releases were 1 to 4 years (version history). No 4.2 found. | File geodatabase. National 1,523,434,496 B (1.42 GB). OR 305,507,185 B; WA 258,522,551 B, each zip including a KMZ copy. | "These data are in the public domain." | Unsuitable for "mapping resolution at scale finer than 1:24,000, as boundary quality varies by data source", and for "Representing boundaries as a legal representation". Has "Boundary discrepancies (mostly small slivers)…". **Not a parcel map: a gap in PAD-US does not mean private.** |
| **BLM National Surface Management Agency (SMA)** ([item](https://www.arcgis.com/sharing/rest/content/items/6bf2e737c59d4111be92420ee5ab0b46?f=json), [service metadata](https://gis.blm.gov/arcgis/rest/services/lands/BLM_Natl_SMA_LimitedScale/MapServer/info/metadata)) | Which agency manages the surface; "Private or Unknown" for the rest. "The SMA data do not illustrate land status ownership pattern boundaries or contain land ownership attribute details." | US. Item modified 2026-06-30. "May be updated by the BLM without notification." | Geodatabase 1,113,064,445 B; also GeoJSON and shapefile. | "as is"; "The BLM should be cited as the data source in any products derived from these data. Any Users wishing to modify the data should describe the types of modifications…" | "neither legal documents nor land surveys"; "scale and accuracy may vary". |
| **BLM Oregon/Washington Management Ownership** ([layer](https://gis.blm.gov/orarcgis/rest/services/Land_Status/BLM_OR_Ownership/MapServer/0?f=pjson), [item info](https://gis.blm.gov/orarcgis/rest/services/Land_Status/BLM_OR_Ownership/MapServer/0/iteminfo?f=pjson)) | **Federal, state (forest, lands, parks, wildlife), local, and private (PV, PVI, PVN, PVU), with no owner names.** Read from the live service: 269,446 polygons, 117,516 of them private. Private polygons are present around Spokane, Tacoma and Salem. | OR and WA. Steward approval 2019-09-18; a newer metadata date was seen only in a search snippet. | Map service, 1,000 records per request. No bulk download page found. | Same BLM "as is" text. | Same BLM caveats. |
| **Forest Service, EDW** ([Basic Ownership metadata](https://data.fs.usda.gov/geodata/edw/edw_resources/meta/S_USA.BasicOwnership.xml), [downloads](https://data.fs.usda.gov/geodata/edw/datasets.php)) | Forest Service versus non-FS land inside national forest boundaries. | National forests. Basic Ownership is updated "Weekly". | Surface Ownership Parcels: geodatabase 69 MB. Forest boundaries: 40 MB. | Access constraints "None". No licence named; public domain is presumed (federal work) but not stated. | "may not be used to determine title, ownership, legal descriptions or boundaries…" |
| **BIA AIAN Land Area Representations** ([item info](https://biamaps.geoplatform.gov/server/rest/services/DivLTR/BIA_AIAN_National_LAR/MapServer/info/iteminfo)) | Trust and restricted-fee land of federally recognised tribes. | US. data.gov record modified 2026-08-05. | Services. Download size not found (the download page returned 404). | "These data are public information and may be used…"; "sole responsibility for ensuring the appropriate use… and republication". | "may contain errors… not intended for legal, survey, engineering or navigation purposes." |
| **Census TIGER/Line 2025 AIANNH** ([TIGER page](https://www.census.gov/geographies/mapping-files/time-series/geo/tiger-line-file.html), [files](https://www2.census.gov/geo/tiger/TIGER2025/AIANNH/)) | Reservation and off-reservation trust land, plus statistical areas that a type field separates. | US. Released 2025-09-23, yearly. | 8.8 MB national. | CC0 according to data.gov only. | Not found. |

### Oregon

- **No public statewide parcel download.**
  - data.gov ORMAP record: "To access the tax lot layer you will need to contact the county Assessor's office." ([data.gov](https://catalog.data.gov/dataset/ormap-the-oregon-property-tax-map))
  - The state's Statewide Parcels Initiative aims "to create and publish a geospatial dataset of parcels that is available for all to use and download" and lists five pilot counties ([GEO](https://www.oregon.gov/eis/geo/Pages/statewide-data-initiatives.aspx)).
- **Oregon cadastral standard v3.2** (2018, [PDF](https://ftp.gis.oregon.gov/framework/ZZ_SHARE/Standards/Cadastral/Cadastral%20Standard%20v3.2.pdf)), exact: "Cadastral taxlot line accuracy is not intended to represent positional accuracy. A licensed surveyor must be consulted if statements about positional accuracy need to be made." Polygons that are not taxlots can be coded "ROADS, RAILS, WATER".
- **DSL State Land Inventory** ([service](https://maps.dsl.state.or.us/arcgis/rest/services/SlisPublic/FeatureServer)) covers all state-agency land. Its report ([PDF](https://www.oregon.gov/dsl/lands/Documents/SLIS1_OwnershipStatewide.pdf), exact) says it "does not include public rights of way (highways, roads, and streets)". The service adds: "known to have gaps and inconsistencies". No licence is stated.
- **ODF state forests** (item 04ef00d43c26494492f0e5b54c182763): "There are no use constraints on these data… precision may be limited due to the lack of control points in certain areas."
- **OPRD state parks** ([service](https://maps.prd.state.or.us/arcgis/rest/services/Land_ownership/Oregon_State_Parks/FeatureServer/0)) carries a warranty disclaimer only, with no licence field.

### Washington

- **Statewide parcels, public version** ([item](https://www.arcgis.com/sharing/rest/content/items/0f4c2ca4b589499c8df7779a01bef002?f=json)): 527,446,726 B, modified 2026-04-08.
  - It has parcel ID, address, land use and value, and **no owner fields**.
  - Licence (exact): "for informational purposes only… nor is it suitable for legal, engineering, or surveying purposes…"
  - The licence also says (agent-read): "Some of the counties restrict the use of parcels for State of Washington business only. Refer any requests for the data to the appropriate county." **Redistribution is therefore not cleared for every county.**
- **The version with owners** (item 925b42856d2e4f288c35a065192a2dc9): "Category 2 - Published for use by State Agency employees and related efforts only." Not usable.
- **DNR managed land parcels** ([layer](https://gis.dnr.wa.gov/site3/rest/services/Public_Boundaries/WADNR_PUBLIC_Cadastre_OpenData/MapServer/6)): "as is". Only the ownership parcels are complete.
- **WDFW lands** ([layers](https://geodataservices.wdfw.wa.gov/arcgis/rest/services/WP_RealEstate/WildlifeAreas/MapServer/layers?f=pjson)): no licence found.
- **State Parks boundaries** ([service](https://services5.arcgis.com/4LKAHwqnBooVDUlX/arcgis/rest/services/WA_State_Park_Boundaries/FeatureServer)): "as is".

### National parcels and OpenStreetMap

- **The commercial national parcel dataset (Regrid)** ([licence](https://app.regrid.com/store/license)) forbids "otherwise making the Data available to third parties". It allows display only "in a manner that reasonably prevents the customers from obtaining a copy". That conflicts with an open-source offline download.
  - Its free non-profit programme ([page](https://regrid.com/purpose)) states no redistribution terms.
  - A free national parcel dataset does not exist; a 2024 state-GIS-council paper argues for one ([NSGIC](https://nsgic.org/wp-content/uploads/2024/04/The-Case-for-a-National-Parcels-Dataset.pdf)).
- **OpenStreetMap** has `boundary=protected_area`, `boundary=aboriginal_lands` and `ownership=*`. The [ownership wiki page](https://wiki.openstreetmap.org/wiki/Key:ownership) is flagged as contentious. Coverage in Oregon and Washington was not measured. The licence is ODbL, the same as the app's base map.

---

## 2. Owner names

- **Washington.**
  - RCW 42.56.070(8) bars agencies from providing "lists of individuals requested for commercial purposes" ([RCW](https://app.leg.wa.gov/rcw/default.aspx?cite=42.56.070)). AGO 2019 No. 3 says such lists include electronic assessment records, and that "agencies should redact the individuals' names, if possible" ([AGO](https://atg.wa.gov/ago-opinions/listings-taxable-property-lists-names-individuals-requested-commercial-purpose)).
  - Clark County's public taxlots: "All property owner-related information has been removed to ensure compliance with Washington State Law… and… AGO 2019 No. 3" ([item info](https://gis.clark.wa.gov/arcgisfed/rest/services/ClarkView_Public/TaxlotsPublic/MapServer/info/iteminfo)). The page cites the RCW as "46.52.070(8)", probably a typo for 42.56.070(8).
  - Whether a free, non-commercial app counts as a "commercial purpose" requester was not determined.
- **Confidentiality programmes.**
  - Washington's Address Confidentiality Program helps participants "to own property in the state without public disclosure of such ownership" (RCW 40.24.100, [RCW](https://app.leg.wa.gov/RCW/default.aspx?cite=40.24&full=true)). That is help to set up hidden ownership, not automatic redaction.
  - Oregon's personal-safety withholding law states: "This section does not apply to county property and lien records" ([ORS 192.368](https://oregon.public.law/statutes/ors_192.368)).
  - **So an owner-name layer can expose people those programmes exist to protect.**
- **Washington's anti-doxing law**, RCW 4.24.792 ([RCW](https://app.leg.wa.gov/RCW/default.aspx?cite=4.24.792)), requires intent or reckless disregard. An app is unlikely to be liable (inference), but the law treats publishing home addresses as a harm.
- **Oregon privacy law.** Its privacy act excludes data "lawfully available through… government records" ([ORS 646A.570](https://oregon.public.law/statutes/ors_646A.570)). Its data-broker law covers businesses that sell or license data ([ORS 646A.593](https://oregon.public.law/statutes/ors_646A.593)). A free app that sells nothing is unlikely to be a broker (inference).
- **"Private, owner not shown" is the norm in government public data** (Washington statewide, Clark County). The national commercial dataset also drops names where state law requires: "California state law effectively prohibits counties from sharing owner names" ([store page](https://app.regrid.com/store/us/ca/modoc)). No outdoor app was found that withholds names as a deliberate privacy choice. The commercial apps sell names as a premium feature.
- **No documented case was found of a landowner harassed through an app's owner display.**

---

## 3. Prior art (apps described by what they do)

- **The leading hunting-map app and its offroad and backcountry siblings.**
  - They show public and private land with "Landowner's name, tax address" ([features](https://onxmaps.com/hunt/app/features/property-lines)), on the paid tier.
  - FAQ: lines "typically accurate to within 5-10 feet", with "some users reporting discrepancies from 5 to 50 feet"; private data updated "every 1-2 years" ([FAQ](https://www.onxmaps.com/hunt/app/faq)).
  - A staff member says county data "can be off by as much as 50 feet" ([forum post](https://www.hunttalk.com/threads/ask-your-onx-hunt-questions-here.277109/post-3661740)).
  - Terms: "DOES NOT CONSTITUTE A LEGAL SURVEY… OWNERSHIP… MAY CHANGE AT ANY TIME… DO NOT USE THE SERVICE FOR ANY PURPOSE REQUIRING PRECISE DIRECTION, DISTANCE OR LOCATION… VISUALLY VERIFY BOUNDARIES" ([terms](https://www.onxmaps.com/terms-of-use)).
  - An entry alert could not be confirmed from the vendor's own pages. No routing around private land was found.
- **A second hunting app** sells "Nationwide Private Parcel Boundaries & Owner Names". Its release notes claim "Land Ownership maps are now guaranteed accurate" ([listing](https://apps.apple.com/us/app/basemap-3d-hunting-gps-maps/id1305237481)), which is a claim no data source supports.
- **A third hunting app's guide** describes a "trespass alert" in general terms and says apps "are not a replacement for a licensed survey" ([guide](https://huntwise.com/field-guide/hunting-tips/hunting-app-with-property-lines-features-that-matter)).
- **A general backcountry GPS app**: "These maps may be out of date or incorrect. Use of this Private Land map layer for any purpose is at your own risk" ([layer page](https://www.gaiagps.com/maps/source/usparcels/)).
- **A backcountry planning app** licenses commercial parcels and says "parcel data cannot be exported… due to licensing restrictions" ([blog](https://blog.caltopo.com/2020/02/18/parcel-data/)).
- **A hiking app with a PAD-US public-land-only layer**: "an informational planning tool, not a legal survey. Boundaries are approximate… Trespassing on private or restricted land is a legal offence regardless of what any map shows" ([support](https://support.hiiker.app/?p=3336)). This is the closest prior art to the app's likely shape.
- **OpenStreetMap routers** avoid ways tagged `access=private` ([wiki](https://wiki.openstreetmap.org/wiki/Tag:access=private)). A leading open-source offline map app reportedly offers to route "as close as the end of public access". That comes from a forum description, not the app's documentation.
- **Incidents.**
  - A magazine editor was convicted of trespass in Wyoming in August 2026. He wrote that he shot "Instead of ranging the bull and consulting my [hunting-map] app". He noted a "5-yard difference between the digital boundary shown by [the app] and the surveyed boundary" ([article](https://www.outdoorlife.com/conservation/wyoming-elk-hunt-trespassing/)).
  - A retired warden: an app is no defence ([article](https://cowboystatedaily.com/2025/08/25/popular-hunting-app-may-not-keep-you-from-trespassing-or-out-of-big-trouble)).
  - In the Wyoming corner-crossing case, the leading app's user waypoints were subpoenaed ([article](https://wyofile.com/?p=88759)). The app keeps data on the phone, which avoids that.

**The common thread:** every app disclaims accuracy, and none claims its lines are legal boundaries. The vendor's own staff put GPS at "about 16 feet" and county lines at up to 50 feet off.

---

## 4. Picking rules by land type, Oregon and Washington

All sources were opened on 2026-10-11. Pages were updated between 2025-05 and 2026-10-07.

| Land type | Personal mushroom picking | Source |
|---|---|---|
| National forest (federal rule) | Banned unless authorised; each forest's free-use allowance authorises it. | 36 CFR 261.6(h) ([Cornell](https://www.law.cornell.edu/cfr/text/36/261.6)) |
| No permit needed | Mt. Hood ("1 gallon per day and a total of 10 gallons per year"; truffles prohibited), Siuslaw, Colville (3 gal/day), Olympic NF ("one gallon per species per year"), Willamette and Umpqua (under 1 gal/day) | each forest's page on fs.usda.gov |
| **Free permit even for personal use** | Deschutes, Fremont-Winema, Ochoco, Gifford Pinchot (online only), Mt. Baker-Snoqualmie. Okanogan-Wenatchee requires a carried information sheet. | same |
| Wilderness | Closed on most forests; **open for personal picking** on Umatilla, Wallowa-Whitman and Malheur ("mushrooms can be harvested for free use within Wilderness areas") | [joint release](https://www.fs.usda.gov/r06/umatilla/newsroom/releases/2026-mushroom-picking-season-here-and-free-within-personal-use) |
| BLM | "You can collect small amounts for personal use in most areas." No OR/WA number found. | [BLM](https://www.blm.gov/Learn/Can-I-Keep-This) |
| National park (federal rule) | Banned unless the park's compendium allows it | 36 CFR 2.1 ([Cornell](https://www.law.cornell.edu/cfr/text/36/2.1)) |
| Olympic NP / Mount Rainier / North Cascades | 1 quart a day / 1 gallon a day / 1 quart, must be cut | park compendiums |
| **Crater Lake NP** | **Banned**: "mushroom harvesting is not permitted anywhere in Crater Lake National Park" | park release, 2016; compendium 2025-12-30 lists berries only |
| National wildlife refuge | **Banned by default** (50 CFR 27.51); a refuge can open it | Finley and Willapa refuges both prohibit it |
| Oregon state forests | No permit, 1 gallon per vehicle, **only in five named districts** | [ODF PDF](https://www.oregon.gov/odf/Documents/workingforests/special-forest-products-personal-use.pdf), revised 2026-06-11 |
| Oregon state parks | 1 gallon per person per day, effective 2025-03-12 (was 5) | OAR 736-010-0055 ([SOS](https://secure.sos.state.or.us/oard/viewSingleRule.action?ruleVrsnRsn=322970)) |
| Washington State Parks | 2 gallons a day; none in natural area preserves | WAC 352-28-030 ([WAC](https://app.leg.wa.gov/wac/default.aspx?cite=352-28-030)) |
| Washington DNR | 3 gallons of one species a day, 9 a year, per DNR's web page; banned in natural area preserves and campgrounds | [DNR](https://www.dnr.wa.gov/recreation/where-go-what-do); WAC 332-52-115 |
| WDFW land | **Not determined** | WAC 220-500 is silent on mushrooms |
| Tribal reservation | Treat as closed to non-members unless the tribe says otherwise. Colville requires a use pass "FOR ALL VISITORS". | tribal pass sheet |
| **Private, Oregon** | **Written permit for any amount** (fungi are special forest products; the one-gallon exemption is "on public lands" only); Class B misdemeanor | ORS 164.813 |
| Private, Washington | Landowner permission; a state permit is needed above 5 gallons | RCW 76.48.021, .031 |
| City parks | Often banned (Portland's code bans removing vegetation) | Portland City Code 20.12.100 |

**Can the layer say "allowed / permit / not allowed"? Not from land type.** Within one land type the answer flips: permit or none, wilderness open or closed. The limit units differ too: per day, per year, per species, per household, per vehicle.

What it can honestly do:

- Show the ownership.
- Give a cautious default per type: "National park: banned unless the park allows it"; "Wildlife refuge: banned unless the refuge allows it"; "Private: landowner permission needed (Oregon: written permit for any amount)".
- Link to the managing unit's page.

A true allowed/permit/banned layer needs a hand-kept lookup per forest, park, refuge and district. Each entry needs its sub-areas (wilderness, research natural areas, monuments), the source link, the source's own date, the date it was checked, and a review date. Any unit not yet checked must show as "unknown, check with the manager", never as "allowed".

---

## 5. Guiding away: what the data can honestly support

### The owner's sidewalk case is real, for two independent reasons

1. **The law puts the sidewalk inside the private lot.**
   - Oregon: "When a road… is the boundary, the rights of the grantor to the middle of the road… are included in the conveyance, except where the road… is held under another title" ([ORS 93.310](https://oregon.public.law/statutes/ors_93.310)).
   - Washington's municipal research service: "Usually, however, the property owners next to the street own the property, which usually goes to the centerline of the right-of-way, and the municipality has secured the easement for public travel" ([MRSC](https://mrsc.org/explore-topics/legal/property/easements)).
   - Portland: "The right-of-way strip begins at the curb and includes the sidewalk. The right-of-way boundary can extend past the sidewalk and towards private property by varying amounts" ([Portland](https://www.portland.gov/ppd/tree-permits/street-tree-or-private-tree)).
   - **How the map data draws it varies.** The Oregon cadastral standard has a "ROADS" polygon code, and Washington County, Oregon's taxlots include "right-of-way outlines" ([item info](https://gispub.co.washington.or.us/server/rest/services/Itaxlots_WMAS/MapServer/0/iteminfo?f=pjson)). Where the data has a road polygon, the lot ends at the right-of-way line. That line sits at the back of the sidewalk or some distance behind it, not at the curb. Where a county draws lots to the centreline, as is common for rural roads under the centreline rule, the road itself is "private". How each Oregon and Washington county draws this was not surveyed. **Either way, a sidewalk is within metres of a private line, and often inside one.**
2. **Phone GPS beside buildings is off by more than the width of a sidewalk.**
   - The Android accuracy figure is a 68% radius: "there is a 68% chance that the true location falls within this circle" ([Android](https://developer.android.com/reference/android/location/Location)). One fix in three is outside it, by design.
   - Open sky: "typically accurate to within a 4.9 m (16 ft.) radius… accuracy worsens near buildings, bridges, and trees" ([gps.gov](https://archive.gps.gov/systems/gps/performance/accuracy/)).
   - Urban campus, measured against survey monuments: "average horizontal position accuracy… (7–13 m)", RMSE about 9.9 m, maximum error 99.7 m ([Merry & Bettinger 2019](https://pmc.ncbi.nlm.nih.gov/articles/PMC6638960/)).
   - Next to a 3-storey building: median 8.4 m, against 4.07 m about 15 m away. These are the phones' own reported radii, not measured errors ([Palathinkal et al. 2026](https://arxiv.org/abs/2603.26706)).
   - Forest, static: multi-frequency phones averaged DRMS 6.99 m, single-frequency 9.13 m ([10 phones, 15 sites](https://pmc.ncbi.nlm.nih.gov/articles/PMC8838512/)).
   - The parcel line adds its own error: "5 to 50 feet" by the leading app's own account (above). Oregon's standard says taxlot lines are not positional accuracy at all.

Together: a person on a sidewalk is about 2 to 5 m from a line that may itself be several metres off. Their reported position wanders 5 to 10 m with outliers far beyond. **A simple "you are inside a private polygon" check will fire on sidewalk walks, and often.** The false alarm comes from the law as much as from the GPS, so better GPS would not cure it.

### How platforms avoid false entry alerts

- **Android geofencing** ([docs](https://developer.android.com/develop/sensors-and-location/location/geofencing), exact):
  - "For best results, the minimum radius of the geofence should be set between 100 - 150 meters."
  - To cut alerts when passing by, use `GEOFENCE_TRANSITION_DWELL` so "the dwelling alert is sent only when the user stops inside a geofence for a given period of time".
  - "Usually the latency is less than 2 minutes".
  - "On most devices, the geofence service uses only network location for geofence triggering". The system service is therefore unusable for GPS-only woods walks; the app would have to do its own checks.
- **Apple region monitoring** (archived guide, [Apple](https://developer.apple.com/library/archive/documentation/UserExperience/Conceptual/LocationAwarenessPG/RegionMonitoring/RegionMonitoring.html)): the user must "cross the region boundary, move away from the boundary by a minimum distance, and remain at that minimum distance for at least 20 seconds"; "approximately 200 meters".
- **Outdoor apps:** no app's documentation describing a buffer, dwell or accuracy gate was found. The hunting apps lean on "exercise caution when near boundary lines" ([article](https://www.onxmaps.com/insights/best-property-line-app-for-hunting)).

Both phone platforms solve false alerts the same way: a cushion past the line plus time spent there, at scales (100 to 200 m) far larger than a house lot.

### Entry-warning options and their false-alarm trade-offs

| Option | Fires on a sidewalk walk? | Misses real entries? | Honest? |
|---|---|---|---|
| A. Point inside private polygon | **Yes, often** | Rarely | No: wrong in exactly the owner's case |
| B. A plus a fixed buffer (alert only if more than X m inside, X ≈ GPS radius + 15 m) | Rarely on a sidewalk; still fires in back lanes and alleys and on lots drawn to the centreline | **Every small lot.** A typical city lot is about 30 m deep, so a 20+ m buffer covers most of it | Partly |
| C. B plus dwell (inside by more than X for N minutes, or N metres walked) | Very rarely | Short incursions, which are most real ones on small lots | Partly |
| D. **C, only off any mapped road, path or track, only with good accuracy (for example under 10 m), and only on rural or large parcels (for example over 2 ha)** | Practically never: sidewalks and roads are excluded outright | Small lots in towns (by design) and unmapped legal paths | **Yes, if worded as "you may be on private land"** |
| E. No entry alert; show the layer, plus "approaching" hints in route guidance only | Never | All of them, as an alert | Yes |

**Plainly: no version of "warn on entering" is reliable for town lots.** The data cannot tell a sidewalk from a front yard reliably. Option D is the only one supported by the evidence, and it should be described as "you look to be well inside private land" in the woods, not as a trespass alarm. It still has two failure modes:

- **Missed entries.** On a 2 ha woodland lot, D does nothing in the first 20 m.
- **False alarms where OSM lacks a legal path.** It can fire on a legal trail that crosses private land under an easement OSM has not tagged.

A "can't tell" state near any line is needed whatever is chosen. A wrong alert is worse than none, which the owner already noted.

### Show it on the map

Supported, with the same disclaimer every app uses. Public land from PAD-US and agency data is good enough at trail scale. Private land from BLM Oregon/Washington ownership is good enough to show "private" as an area. It should not be drawn as a crisp legal line: PAD-US warns against scales finer than 1:24,000, and the parcel sources disclaim survey accuracy. Suggested treatment:

- Hide or soften the layer at the closest zooms, and never present lines as legal boundaries.
- Show "Private land. Owner not shown. Lines are approximate".
- Treat a gap in the public-land data as "unknown", not "public".

### Routes avoid it

- **BRouter supports avoid-areas** ([nogo docs](https://brouter.de/brouter/vianogo.html); [service docs](https://raw.githubusercontent.com/abrensch/brouter/master/docs/developers/android_service.md)): "'nogos', 'polylines' and 'polygons'… when they have a parameter 'weight' the result is not an absolute nogo it is weighted to the other ways." In the code ([RoutingContext.java](https://raw.githubusercontent.com/abrensch/brouter/master/brouter-core/src/main/java/btools/router/RoutingContext.java)), a weighted polygon costs "metres inside × weight", and an unweighted one forbids.
- **Caveat 1: an avoid-area that contains a route point is silently dropped** (`cleanNogoList(waypoints)`). A walk starting from the user's own land, or a parking spot inside a "private" road polygon, would lose the avoidance with no sign.
- **Caveat 2: every avoid-area is checked in a plain loop**, with no spatial index (inferred from the code). Passing thousands of parcel polygons would be slow. No published limit or benchmark was found.
- **Caveat 3: BRouter's lookup table has no `landuse`, `ownership` or `boundary` tags** ([lookups.dat](https://raw.githubusercontent.com/abrensch/brouter/master/misc/profiles2/lookups.dat)). Ownership can reach routing only as request-time polygons, or by tagging ways during the routing-data build (a pseudo-tag, as BRouter already does for forest cover). That second route would mean changing the build.
- **Already working:** the stock hiking profile gives `access=private`/`no` ways a 100000 penalty, which effectively excludes them ([hiking-mountain.brf](https://raw.githubusercontent.com/abrensch/brouter/master/misc/profiles2/hiking-mountain.brf)). Official hiking routes get only 12.
- **Honest version:** penalise rather than forbid, so routes still use public roads and legal trails that cross private parcels. A forbidding polygon would also block the road, which under the centreline rule sits inside the parcel. Penalise only off-road travel inside private land, and say when a route could not avoid it.

---

## 6. Fit with the weekly build

| Input | Size | Changes |
|---|---|---|
| PAD-US 4.1 OR + WA | about 564 MB zipped (each with a KMZ copy) | Yearly at most |
| PAD-US 4.1 national | 1.42 GB | Yearly at most |
| BLM SMA national | 1.11 GB | Unannounced |
| BLM OR/WA ownership | 269,446 polygons; service only, about 270 pages of 1,000 records; byte size unknown | Unannounced |
| WA statewide parcels (if used) | 527 MB | Last modified 2026-04 |
| A third party's national PAD-US vector tiles, zoom 0 to 14 ([source.coop](https://data.source.coop/cboettig/padus/README.md)) | 3,636,293,548 B by HEAD | n/a |

- **Rebuild on change, not weekly.** The weekly OpenStreetMap build has no reason to re-process this layer. A check of each source's modified date, rebuilding only when one changes, is enough.
- **Tooling.** Polygon tiling suits tippecanoe, which "tries to keep the line or polygon within one tile unit"; `--no-simplification-of-shared-nodes` avoids gaps between neighbouring lots ([README](https://raw.githubusercontent.com/felt/tippecanoe/main/README.md)). Planetiler reads GeoPackage and GeoParquet directly ([README](https://raw.githubusercontent.com/onthegomap/planetiler/main/README.md)).
- **Not measured on the Pi:** the Pacific Northwest tile size and processing time. Inferred: tens to a few hundred MB for OR and WA together at zoom 14, a small share of the national 3.6 GB. That is a guess, not a measurement.
- **Offline region downloads** would grow by the layer's share of each region. Shown before download per spec R5.

---

## Options for the owner

**A. Public land only, no private layer.** Show PAD-US and agency public land with its access status; everything else is "not known to be public".
- For the user: the map shows where they can likely go, and makes no claims about private land, so nothing can be wrongly accused.
- Lowest risk and effort. Weakest at "guide away from private".

**B. Public land plus "Private, owner not shown" (Oregon and Washington), map only.** Add BLM Oregon/Washington private classes, softened at close zoom, with an approximate-lines disclaimer.
- For the user: they see where private land is, as a shaded area, without names.
- Coverage stops at the state lines until other states are sourced.

**C. B plus routing that penalises off-road travel through private land.**
- For the user: suggested walks stay on public land or public roads where they can. When a route can't, it says so.
- Needs care with BRouter's dropped-area and performance caveats above.

**D. B or C plus a narrow "well inside private land" notice (Option D in section 5).** Off roads and paths only, deep inside, with good accuracy, on large parcels.
- For the user: in the woods, a gentle "you may be on private land" after they've clearly crossed in. Never on a sidewalk or road. Nothing on small town lots.

**E. A picking-rules layer.** Per-unit lookup ("allowed / free permit / banned / unknown") with source and date.
- For the user: the most useful answer, but only as good as its upkeep. It needs a review routine before every season.

**Not recommended:**
- Owner names: privacy exposure, the Washington agency restriction, and the commercial licence conflict.
- A plain point-in-polygon entry alert: fires on sidewalks.

**Recommendation:** **B now, C next, D only after a walk test on real lots, and E as a separate decision.** The case for each:

- **B** answers "show it" honestly and cheaply from free data.
- **C** answers "routes avoid it" without stranding people on roads.
- **D** is the only version of "warn on entering" the evidence supports, so it should be tested on a real woods-edge walk and a town sidewalk walk before it ships.
- **E** is valuable but is a maintenance commitment more than a feature.

For B, C, D and the picking layer alike, the "don't make me think" principle argues for one plain label ("Private land") and no setting.

## Decisions this leaves open (for the owner)

1. Show private land at all (A versus B), knowing the lines are approximate.
2. Whether to try an entry notice (D) at all, and with what parcel-size floor.
3. Whether to start the per-unit picking lookup (E) and who keeps it current.
4. Beyond Oregon and Washington: no free private-land source covers other states the way BLM Oregon/Washington does, so the private part may stay regional.

## Could not determine

- Whether any leading app actually has a private-land entry alert, or routes around private land. Only secondary claims were found.
- How each Oregon and Washington county draws lots against roads: to the centreline or to the right-of-way line.
- Measured (not phone-reported) GPS error percentiles while walking in towns or under canopy. The canopy studies are static.
- BRouter avoid-area count limits or benchmarks. Whether the BRouter Android app's own interface supports weighted polygon avoid-areas, as opposed to its service interface.
- BLM Oregon/Washington ownership: bulk download location, byte size, current update date, and whether a public-domain variant exists. data.gov pages returned 404.
- An explicit Forest Service licence statement. BIA LAR download size and update rate. TIGER AIANNH use constraints.
- Whether Oregon has a public statewide parcel file today, and whether Oregon parcels carry owner names. Whether a state rule bars owner names on Oregon state sites (search snippet only).
- Which Washington counties restrict parcels to state business.
- Whether a free, non-commercial app is a "commercial purpose" requester under AGO 2019 No. 3.
- Whether county assessors redact confidentiality-programme participants. The statutes suggest not automatically.
- OSM coverage of `ownership=*` and protected areas in Oregon and Washington.
- Picking rules for WDFW land, BLM district limits, Rogue River-Siskiyou's personal limit, ODF districts outside the five named, other NPS units, most refuges, any tribe's statement on non-member picking, and county parks generally.
- Tile size and processing time for the layer on the Pi. The figure in section 6 is an estimate.
- Whether PAD-US 4.2 or 5.0 has been released since 4.1 (none found).

## Not done

No index row was added to `docs/audits/README.md`; the dispatch said to commit only the report. Nothing was built and no phone was used.
