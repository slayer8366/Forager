# Dispatch 2026-10-30-19: a "Heights" section in the app's credits (a coder, Opus)

Written by Rowan Waymark, 2026-10-30. RECORD -1868. Cut from **pre-main b493cffeb**; verify that is still the remote head.

**Authorisation.** The owner, verbatim: "Approve as written (Recommended)"; "New \"Heights\" section (Recommended)"; "Yes to both (Recommended)".

**Read first:** Juniper's report docs/research/2026-10-30-nrcan-attribution.md on origin/juniper-nrcan-credit (355933b4d).

## Change
In ui/credits/DataSourceCredits.kt, add a new section **"Heights"** to DATA_SOURCE_SECTIONS, between Maps and Weather, with these rows **word for word**. Follow the existing row shape: credit text, link label, and a line underneath for changed data.
1. "Heights in Canada: Natural Resources Canada. Contains information licensed under the Open Government Licence – Canada." Link label "licence" → https://open.canada.ca/en/open-government-licence-canada. Line underneath: "Route heights in Forager are averaged from this data."
2. "Heights in Canada: produced using Copernicus WorldDEM-30 © DLR e.V. 2010-2014 and © Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved." No link. Line underneath: "The organisations in charge of the Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30."
3. "Heights in the United States: 3DEP elevation data courtesy of the U.S. Geological Survey, public domain" No link. Line underneath: "Route heights in Forager are averaged from this data."

Keep the en dash in "Licence – Canada" and the © signs exactly.

## Tests
- Through the real Credits screen, assert each row's exact text, the section heading and its order (after Maps, before Weather), and that the licence link opens the URL.
- Fail first, then a revert check. The build log must have no compile errors.
- CI only. A PR into pre-main, with no merge.

## Rules
- Branch `height-credits`, worktree ~/.cache/forager-wt/height-credits.
- No Gradle on the laptop, no adb. gh is logged in; run gh commands bare.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- Ambiguity: stop and report.
