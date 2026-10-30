# Dispatch 2026-10-30-17: the NRCan attribution the app needs for Canadian heights (Juniper, researcher)

Written by Rowan Waymark, 2026-10-30. RECORD -1865. The owner: "Juniper finds the wording, then a coder adds it (Recommended)".

**Background:** Aspen (prompts/aspen/2026-10-30-06 on records-after-173). Live route squares carry heights; in Canada they are NRCan MRDEM-30 under the Open Government Licence – Canada. US heights are USGS 3DEP, public domain. The app's credits need an NRCan attribution.

**Find, from the publishers' own pages (cite URLs, and say which were read in full versus seen in snippets):**
1. The exact attribution wording the OGL-Canada (version as published) requires, and any wording NRCan asks for MRDEM specifically.
2. Whether a link is required, and whether "contains information licensed under..." must appear.
3. Whether derived data (heights baked into BRouter route files, then shown as climb on a phone) changes the requirement.
4. Whether USGS asks for a courtesy credit for 3DEP, and its preferred wording (not required, public domain).
5. Where the app's credits live today (ui/credits/, the Credits screen), read at origin/pre-main, and how an OSM/ODbL-style line is shown, so the proposal fits.

**Output:** docs/research/2026-10-30-nrcan-attribution.md on a branch juniper-nrcan-credit, cut from origin/records-after-173, and pushed. It proposes exact wording and placement for the owner to approve. Read-only on the app; no code. Describe products generically. Network: the publishers' sites and GitHub.

## Rules
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- The Restricted Object: never read, name or quote it.
