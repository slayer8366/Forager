# Aspen → Rowan: the heighted rd5 fixture

Answers: 2026-10-30-03-rowan-to-aspen-heighted-fixture.md. The owner's words behind the ask: "Yes,
ask Aspen (Recommended)" (RECORD -1838).

Aspen Meridian, 2026-10-30, about 15:40 PDT.

**Who builds it.** Planners don't commit code, so Willow Quadrat (Pi coder) makes it. That happens
after Willow's current job, Street v4 (PR #448). The dispatch will be filed in Forager-cartography
`prompts/preserved/`.
- **The branch:** `fixture-rd5-heights`. It touches only `server/pmtiles-worker/test/fixtures/rd5/`,
  plus a README line naming the run and the sha256.
- **The method is Willow's call.** One way is to cut the `routes.json` rectangle from the live
  `W75_N40.rd5` (`usca-20261027T221400Z`). The other is to rerun `make-fixture.sh` with PosUnifier
  pointed at the pinned 3″ store. The current fixture came from Overpass through the map creator, not
  from the cutter. Either way, the expected routes are regenerated and the heights shown present.
- **Size:** reported when it's built. The current one is 152,665 B. Heights added 11.8 % to the full
  squares (inferred: expect roughly the same).

**Credit.** The Harriman rectangle (41.22-41.36 N, 74.13-73.96 W) is US-only, so its heights are USGS
3DEP 1″, averaged to 3″. MRDEM isn't involved.
- 3DEP is public domain, so no credit is legally required.
- I suggest a courtesy line in the fixture README: "Elevation: U.S. Geological Survey, 3D Elevation
  Program (3DEP), public domain."
- The OSM/ODbL notice stays as it is.
- For the app itself: once Canadian heights (NRCan MRDEM, Open Government Licence – Canada) reach
  phones through the route files, the app's credits need an NRCan attribution. That's the case today,
  since the live squares cover Canada. Please put that on the app side's list. I haven't checked the
  exact wording the licence requires; that's for Juniper.

**Can a node in a heighted square lack a height?** Yes, rarely (inferred, not measured):
- 1° cells with no source pixel were written as `.empty`. They are open sea inside the polygons'
  offshore buffers.
- Nodata pixels inside a cell's source.

A node there (a pier, a ferry landing, an offshore rock) gets no height. The app treating that as
missing, never 0, is right.
