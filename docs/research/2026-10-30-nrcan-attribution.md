# NRCan attribution for Canadian heights (Juniper Sounding, researcher)

Dispatch: `prompts/preserved/2026-10-30-17-juniper-nrcan-credit.md` (records-after-173, RECORD -1865).
Background: `prompts/aspen/2026-10-30-06-aspen-to-rowan-heighted-fixture.md`.

## Method (committed before any publisher page was read)

- Read the licence and the data product's pages at the publishers' own sites only: open.canada.ca
  (Open Government Licence – Canada), the NRCan / Open Maps record for the MRDEM-30 product, and
  usgs.gov for 3DEP citation guidance. GitHub only if a publisher links there.
- Quote licence terms verbatim with URL; mark each page as read in full or seen only in a search
  snippet.
- Read the app's credits at origin/pre-main (`ui/credits/`), file and line.
- Readings of what a term means for Forager are marked "inferred, not legal advice".

## Predictions (stated before reading)

1. OGL-Canada 2.0 requires an attribution statement naming the Information Provider and, where
   possible, a link to the licence; it gives a standard form "Contains information licensed under the
   Open Government Licence – Canada", and allows a multiple-provider form.
2. MRDEM's record names Natural Resources Canada as the provider; it may not have product-specific
   wording.
3. Derived data does not remove the attribution duty; the licence covers adapted information.
4. USGS asks for (does not require) a credit along the lines of "U.S. Geological Survey" plus product
   name, as public domain.

---

# Findings (written after reading)

Read 2026-10-30. Pages fetched with a named user agent, one request each; raw copies kept outside the
repository. "Read" = quoted from the page; "inferred" = my reading, **inferred, not legal advice**.

## Sources and how each was read

| Source | URL | How read |
|---|---|---|
| Open Government Licence – Canada, v2.0 (page dated 2022-12-02) | https://open.canada.ca/en/open-government-licence-canada | **In full** (whole page text) |
| MRDEM dataset record, Open Government Portal (record dated 2026-07-16) | https://open.canada.ca/data/en/dataset/18752265-bda3-498c-a4ba-9dfe68cb98da | **In full** |
| MRDEM Product Specifications, Edition 1.2, 2025-06-20 (NRCan PDF, linked from the record) | https://ftp.maps.canada.ca/pub/elevation/dem_mne/MRDEM_MNEMR/CanElevation-MRDEM-Product-Specifications.pdf | Whole text searched for licence, attribution, copyright, Copernicus, citation; the copyright page and §11.3 "Data Use and Restrictions" **read in full**; the rest not read |
| MRDEM Product access guide (NRCan PDF) | https://download-telecharger.services.geo.ca/pub/elevation/dem_mne/MRDEM_MNEMR/CanElevation-MRDEM-Usage-Guide.pdf | Whole text searched for the same terms; **no matches** (no attribution wording in it) |
| MRDEM-30 STAC collection (NRCan's machine record) | https://datacube.services.geo.ca/stac/api/collections/mrdem-30 | **In full**: `"license": "OGL-Canada-2.0"`, no attribution or citation field |
| Licence for the use of the Copernicus WorldDEM-30 (Copernicus Data Space Ecosystem PDF) | https://documentation.dataspace.copernicus.eu/APIs/SentinelHub/Data/DEM/resources/license/License-COPDEM-30.pdf | **In full** |
| USGS, "Acknowledging or Crediting USGS" | https://www.usgs.gov/information-policies-and-instructions/acknowledging-or-crediting-usgs | **In full** |
| USGS 3DEP tile metadata, 1/3 arc-second tile n48w104 (FGDC XML) | https://thor-f5.er.usgs.gov/ngtoc/metadata/waf/elevation/1-3_arc-second/ND_Phase11_C24/USGS_13_n48w104_20260722.xml | Citation, `accconst` and `useconst` fields **read in full**. This is a **1/3″** tile; the **1″** product Aspen names was not reached (the 1″ metadata directory listing did not lead to a file in two tries, and I stopped) |
| USGS, "About 3DEP Products & Services" | https://www.usgs.gov/3d-elevation-program/about-3dep-products-services | Through a summarising fetch only, **not** read verbatim by me. It reported the sentence "All 3DEP products are available, free of charge and without use restrictions." and no citation guidance |
| NRCan article on MRDEM replacing CDEM; Earth Engine catalogue pages; OSM talk-ca list (2015); third-party map publishers | (search results) | **Snippets only**; nothing below rests on them |

Not found: any NRCan page giving a product-specific attribution statement for MRDEM. I searched the
record, both PDFs and the STAC record. A page I did not find may exist; the two searches I ran for an
NRCan attribution template returned only secondary sources.

## 1. The wording OGL-Canada requires, and anything NRCan asks for MRDEM

**The licence (read, OGL-Canada 2.0):**

> You must, where you do any of the above:
> Acknowledge the source of the Information by including any attribution statement specified by the
> Information Provider(s) and, where possible, provide a link to this licence.
> If the Information Provider does not provide a specific attribution statement, or if you are using
> Information from several information providers and multiple attributions are not practical for your
> product or application, you must use the following attribution statement:
> Contains information licensed under the Open Government Licence – Canada.

"The above" is: "Copy, modify, publish, translate, adapt, distribute or otherwise use the Information in
any medium, mode or format for any lawful purpose." Version: "This is version 2.0 of the Open Government
Licence – Canada." The licence also says use is "governed by the terms of the licence in force as of the
date you accessed the information."

**NRCan for MRDEM (read):** the record names the publisher "Natural Resources Canada" and the licence
"Open Government Licence - Canada"; the product specification says at §11.3: "Information regarding the
use of the data is defined in the Open Government Licence - Canada
(http://open.canada.ca/en/open-government-licence-canada)." Its copyright page: "© His Majesty the King
in Right of Canada, Natural Resources Canada. All rights reserved." I found **no specific attribution
statement** for MRDEM.

**But the record and the specification both carry a second, non-NRCan notice (read)**, which the
dispatch did not anticipate and which matters more than the OGL line:

> This product was in part produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus
> Defence and Space GmbH 2014- 2018 provided under COPERNICUS by the European Union and ESA; all rights
> reserved.
> The organisations in charge of the Copernicus program by law or by delegation do not incur any
> liability for any use of the Copernicus WorldDEM-30.

(The specification's §11.3 heads this "Legal notice", and adds: "Natural Resources Canada retains
copyright for the portions of the product that were created using data from the HRDEM product.")

Why: MRDEM's terrain model uses lidar-derived HRDEM "where available" and "elsewhere ... applied to the
GLO-30 values" (record, read). So outside lidar coverage, Canadian heights are Copernicus-derived.

The Copernicus licence's Article 6 (read, in full):

> (b) Where the Copernicus WorldDEM-30 data have been adapted or modified, the User shall provide the
> following notice:
> "produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH
> 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved".
>
> (c) Users exercising the right of distribution or communication to the General Public of the
> Copernicus WorldDEM-30, modified or not, must ensure that the Subsequent Users understand that neither
> the Licensor nor any other legal entities in charge of the Copernicus programme [...] may be held
> liable [...]. The following sentence or its translation in any language shall be added by such Users
> in a licence or any legal warning or notice covering their distribution or communication to the
> General Public of the Copernicus WorldDEM-30: "The organisations in charge of the Copernicus
> programme by law or by delegation do not incur any liability for any use of the Copernicus
> WorldDEM-30".
>
> (d) User shall make sure not to convey the impression to the General Public that the user's
> activities are officially endorsed by the Provider, the Licensor or any other legal entities in charge
> of the Copernicus programme [...].
>
> (e) Where the user grants to any Subsequent User the rights to distribute or communicate to the
> General Public the Copernicus WorldDEM-30, modified or not, he shall ensure that its Subsequent Users
> are bound by the above obligations.

Inferred, not legal advice: Forager is a "Subsequent User" (it did not receive the data from the
Licensor). NRCan's record passing the notice and the disclaimer on is consistent with NRCan meeting
6(e), which binds Forager to 6(b) to 6(d). Forager distributes adapted heights (route files) to the
public, so the 6(b) notice and the 6(c) sentence both apply.

Spelling: NRCan writes "Copernicus program" and "2014- 2018"; the licence writes "programme" and
"2014-2018". The proposal uses the licence's own text.

## 2. Is a link required? Must "Contains information licensed under..." appear?

- **Link (read):** "where possible, provide a link to this licence." A credits row in an app can carry a
  link, so inferred: in Forager it is possible, and therefore expected. The licence's URL is
  https://open.canada.ca/en/open-government-licence-canada.
- **The sentence (read):** required whenever the provider gives no specific statement. None was found
  for MRDEM, so inferred: "Contains information licensed under the Open Government Licence – Canada."
  must appear, word for word (the licence writes an en dash in "Licence – Canada").
- **Copernicus:** no link is required by its licence (read: Article 6 names notices only).

## 3. Does derived data change it?

No, on both licences (inferred from read text, not legal advice):
- OGL's duty attaches "where you do any of the above", and "modify", "adapt" and "distribute" are in
  the list. Heights resampled into route files, then shipped to phones, are adapted and distributed.
- Copernicus 6(b) has its own notice specifically for adapted or modified data, and 6(c) applies to
  distribution "modified or not".
- Whether the app *shows* the heights does not change it: the duty follows distribution. For the
  record: at origin/pre-main `b493cffeb`, a grep of `domain/routing/`, `domain/route/` and
  `data/routedata/` for ascent, climb, height and elevation finds nothing, so I could not confirm the
  app displays route climb today. The bundled profile `assets/routing/profiles2/hiking-mountain.brf`
  has uphill cost settings (lines 13, 53-63), so heights can steer route choice. Both are read; what
  the router actually does with them is not checked.
- Non-endorsement (OGL "Non-endorsement" and Copernicus 6(d)): the wording should not suggest NRCan,
  the EU or ESA approve Forager. A plain source line does not (inferred).

## 4. USGS courtesy credit for 3DEP

- Not required (read, USGS "Acknowledging or Crediting USGS"): "Most U.S. Geological Survey (USGS)
  information resides in the Public Domain and may be used without restriction. When using information
  from USGS information products, publications, or websites, we ask that proper credit be given."
- Preferred form for use at no cost (read): "(Product or data name) courtesy of the U.S. Geological
  Survey". Optional addition: "Visit the USGS at https://usgs.gov."
- The 3DEP tile metadata (read, 1/3″ tile only) adds: "Acknowledgement of the originating agencies
  would be appreciated in products derived from these data. Any user who modifies the data is obligated
  to describe the types of modifications they perform. User specifically agrees not to misrepresent the
  data, nor to imply that changes made were approved or endorsed by the USGS." Inferred: a one-line
  note that heights were averaged meets the "describe the types of modifications" ask cheaply. Not
  confirmed that the 1″ product's metadata says the same.
- Aspen's suggested line ("Elevation: U.S. Geological Survey, 3D Elevation Program (3DEP), public
  domain.") is consistent with this; USGS's own template is the "courtesy of" form.

## 5. Where the app's credits live (origin/pre-main `b493cffeb`, read)

- Data: `app/src/main/java/com/zynergylabs/forager/app/ui/credits/DataSourceCredits.kt`.
  `SourceCredit(key, text, linkLabel, url, detail)` (lines 14-30); a row with a link needs both label
  and URL (line 28); `linkLabel = null, url = null` is allowed for public-domain sources (lines 18-21).
  `DATA_SOURCE_SECTIONS` (line 34) has three sections: "Maps" (lines 35-68), "Weather" (72-84),
  "Sightings and species names" (85-88).
- The OSM/ODbL-style rows: `"© OpenStreetMap contributors"`, link label `"licence"` to
  openstreetmap.org/copyright (line 39); the trail-routing row `"Trail routes: © OpenStreetMap
  contributors"` with link label `"ODbL"` (lines 43, 92). A derived source carries a `detail` line
  saying what was changed: USFS trails "Trail lines in Forager are simplified from this data." (line 63),
  Open-Meteo (line 81). Public domain with no link: `"Trails: Bureau of Land Management, public domain"`
  (line 66).
- Rendering: `SourcesAndCreditsPages.kt`: section title (line 65), linked row with the label at the end
  (lines 85-100), plain row for no link (lines 105-117), `detail` as small text under the row (lines
  74-75).

## Proposal for the owner

A new section, **"Heights"**, after "Maps" and before "Weather" in `DATA_SOURCE_SECTIONS`, with three
rows. (Alternative: the same three rows at the end of "Maps", next to "Trail routes", since they ride in
the same route files. I lean to a section of its own because the Copernicus notice is long and reads as
its own thing; the owner's call.)

**Row 1, Canada (NRCan, OGL-Canada):**
- text: `Heights in Canada: Natural Resources Canada. Contains information licensed under the Open Government Licence – Canada.`
- link label: `licence` → `https://open.canada.ca/en/open-government-licence-canada`
- detail: `Route heights in Forager are averaged from this data.`

**Row 2, Canada (Copernicus notice, carried by MRDEM):**
- text: `Heights in Canada: produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved.`
- no link (none required); optionally `licence` → the Copernicus licence PDF above, owner's choice
- detail: `The organisations in charge of the Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30.`

**Row 3, United States (USGS, courtesy):**
- text: `Heights in the United States: 3DEP elevation data courtesy of the U.S. Geological Survey, public domain`
- no link (as the BLM row)
- detail: `Route heights in Forager are averaged from this data.`

Notes for the coder's dispatch (not decisions):
- The quoted licence sentences are verbatim, including the en dash in "Licence – Canada"; a test should
  pin them exactly, as the existing credit tests pin theirs.
- The "averaged" detail rests on Aspen's description (3DEP 1″ averaged to 3″; MRDEM at 30 m into
  route files). I did not check the build. If the Canadian data is resampled rather than averaged, the
  word should match.
- Out of this dispatch's scope but the same duty: anywhere else the route files are published to the
  public (the download service, a README beside them) the Copernicus notice and sentence apply too
  (Article 6(c) names "a licence or any legal warning or notice covering their distribution"). Aspen's
  side.

## How sure I am, requirement by requirement

| Requirement | Sure? | Basis |
|---|---|---|
| OGL sentence "Contains information licensed under the Open Government Licence – Canada." must appear | High | Licence text read; no MRDEM-specific statement found (medium that none exists anywhere: searched record, PDFs, STAC) |
| Link to the OGL where possible | High | Licence text read; "possible" in an app is my reading |
| Naming Natural Resources Canada | Not required by the text I read; courtesy and clarity | Inferred |
| Copernicus 6(b) notice applies to Forager | Medium-high | Licence read in full; NRCan carries it as a "Legal notice"; Forager being bound as a Subsequent User is inferred |
| Copernicus 6(c) liability sentence must appear | Medium-high | Same; the licence says "in a licence or any legal warning or notice", and the credits page is the app's notice (inferred) |
| Derived/route-file data does not lift any of it | High | Both licences name modify/adapt and distribute |
| USGS credit not required, "courtesy of" form preferred | High | USGS page read in full |
| 3DEP "describe the modifications" ask | Medium | Read on a 1/3″ tile's metadata only; 1″ not read |
| US heights are 3DEP and Canadian heights MRDEM at the border | Unverified | Aspen's statement; MRDEM itself "extends into the USA, where needed" (record, read) |
| Placement in a new "Heights" section | Proposal only | Fits `DATA_SOURCE_SECTIONS` as read; owner decides |

## Predictions vs findings

1. OGL fallback sentence and link: **as predicted.** The multiple-provider form is the same sentence.
2. No MRDEM-specific wording: **as predicted for NRCan**, but **missed** the Copernicus notice, the
   largest finding here.
3. Derived data still attributed: **as predicted.**
4. USGS courtesy credit: **as predicted**; the "describe modifications" ask in 3DEP metadata was not
   predicted.
