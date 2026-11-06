# Dispatch 2026-11-05-08: Download Maps from a trip in the Trip Planner, with the pin and name already set (Hazel, Opus)

Written by Rowan Waymark, 2026-11-05. RECORD -2167. Every fact below was read at pre-main 3a3d85089 while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim: **"Add an option to download the surrounding map from the trip planner so you don't need to do a separate search in the offline maps. Add a trip to the map, select the date. Then tap Download Maps button from the Trip Planner menu to be taken to the Offline Maps page with the coordinates already fixed into the Offline Maps, so all they have to do is adjust tile size and download."**

Then, to four questions:
- "Where should the Download Maps button go in the Trip Planner?": **"On each trip (Recommended)"**. The option read: "Every trip in the Trip Planner list gets its own \"Download Maps\" button, which opens Offline Maps with the pin on that trip. Works however many trips are planned."
- "Should the Offline Maps name box come filled in with the trip's name?": **"Yes, the trip's name (Recommended)"**. The option read: "The area is saved as, say, \"Test trip B\", so it's easy to recognise in the list later. The user can still change it. Only the radius is left to set."
- "A trip with a planned route has several stops. Where should Download Maps put the pin for one of those?": **"Cover the whole route (Recommended)"**. The option read: "The pin goes in the middle of the route's stops and the radius starts just big enough to cover all of them, up to the largest radius. The user can still change it."
- "When should this be built?": **"This release (Recommended)"**. The option read: "Build it before the promotion, alongside the trip bubble and edge shade. It touches the Trip Planner and Offline Maps, which nothing else in flight changes."
  - Correction to that option's last sentence, found after it was asked: PR #494, the trip bubble, changes AvailabilityScreen.kt, AvailabilityUiState.kt and AvailabilityViewModel.kt, which this may need. Hence the phases below.

"Tile size" is read as the radius slider, which sets the area and so its tile count. That reading is a premise: report it if the page offers something else the owner could have meant.

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name. No coordinates of the owner's real places in anything pushed; test fixtures use made-up points.

## Change
- **B1. A "Download Maps" button on every trip in the Trip Planner:** both the plain trip's row (`PlannedTripRow`, AvailabilityTripsWaypointsUi.kt:148 as read) and the route trip's row (`PlannedRouteRow.kt`). The words are the owner's: "Download Maps". It is a labelled button, not an icon alone. Its look and where it sits in the row come to the planner in Phase 1, for the owner.
- **B2. Tapping it opens the Offline Maps page** with:
  - the pin on the trip. For a plain trip, that is the trip's location. For a route trip, it is the middle of the route's stops (B3);
  - the Name box holding the trip's name;
  - the radius at today's starting value for a plain trip, or B3's for a route trip.
- **B3. A route trip:** the pin goes at the centre of the smallest circle holding every stop, and the radius starts at the smallest slider value that holds every stop, capped at the slider's largest. If the cap can't hold them all, the page opens at the cap; that case comes to the planner in Phase 1 before anything is built for it.
- **B4. Nothing downloads on the tap.** The user adjusts the radius, taps the page's own Download Maps, and sees today's "Download this area?" confirmation, unchanged.
- **B5. Offline Maps opened any other way behaves as today.** That covers the Tools drawer, Records and anything else, including where its pin opens. The pin and name a trip sets must not linger into a later, unrelated opening, unless Phase 1 shows today's page already keeps an entered point, in which case report it and wait.

## Scope boundary
- **May touch:** a new branch `trip-download-maps`; the Trip Planner's rows; the Offline Maps page's state and entry; new files for the new path (CLAUDE.md "Building": new capability is a new function or file, not a conditional threaded into working code); tests; a completion report.
- **Must not touch:** pre-main; main; RECORD.md; the audit index; the phone; `CentrePinLocationPicker.kt` (bug 14's Phase 2 changes it on `bug-14-picker-warning`). If this can't be done without it, that is a stop. The other Hazels' branches stay untouched: trip-bubble-actions, edge-shade and bug-14-picker-warning.

## Phase 1: verify first, read-only, and report to the planner by message
Build nothing in Phase 1. Answer each with file and line:
1. How the Tools drawer's "Offline Maps" opens the page today: the route or state, which tab it shows in, and what Back does from it.
2. How the page's pin is chosen when it opens. Is it `offlineMapLatText`/`offlineMapLngText` (AvailabilityOfflineMapsUi.kt:149-162 as read, and `rememberCentrePinState(pickerRegion)`)? Does setting them before opening put the pin there?
   - If the page is already composed, does a later change move the pin? `rememberCentrePinState` may keep the old one.
3. When those fields and `offlineMapNameText` are cleared today (AvailabilityOfflineMaps.kt:239-241 as read), and whether a point set by the user today survives leaving and reopening the page. B5 depends on this.
4. What a route trip stores, its stops and their order. The slider's steps and its largest radius. How the radius maps to tiles, and the tile budget's check.
   - Whether any real route could need more than the largest radius.
5. The two rows' layout today, and where a labelled button fits in each without crowding. Describe the options in words, for the owner; the planner asks.
6. Which files you'd change or add. Their line counts against the 400 and 800 lines in `.claude/skills/modular-code/SKILL.md`. Any overlap with trip-bubble-actions (PR #494), edge-shade (PR #495) and bug-14-picker-warning.
7. Any premise above that is wrong.

## Phase 2: on the planner's go, after PR #494 merges
- Cut `trip-download-maps` from pre-main as it is then. State the commit.
- **Tests first**, through the real entry point:
  - Open the Trip Planner on the real screen, and make real coordinate touches across the Download Maps button's own bounds, at several points, not only the centre (CLAUDE.md, "A semantic performClick asserts wiring, not routing").
  - Then assert that the Offline Maps page shows, that "Pin at: …" reads the trip's point, that the Name box reads the trip's name, and the radius.
  - For a route trip: assert every stop lies within the radius, and that one slider step smaller would leave one out, unless at the cap.
  - Assert that opening Offline Maps from Tools afterwards shows today's behaviour (B5), and that the tap starts no download (B4).
- **Revert checks**, one behaviour each, under the runner rules in CLAUDE.md "Testing": read the compile log first; restore from a saved copy, never from git; confirm the forward change afterwards. Cover: the pin set, the name set, the route's radius, and B5.
- Full CI green, with counts from the JUnit XML.
- A short device check for the S22, cheapest first.

## Stops
Stop and report on:
- anything that downloads without today's confirmation;
- any change to Offline Maps opened another way (B5);
- a need to touch `CentrePinLocationPicker.kt`;
- a route the largest radius can't cover;
- a wrong premise;
- a choice this dispatch doesn't settle, in particular anything the user would see differently;
- two failed attempts at one symptom.

## Finish line
A PR into pre-main, green, merged on the owner's word after an S22 check.
