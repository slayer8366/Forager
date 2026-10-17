# Device checks owed before the production release (T7): tablet camera hold, map outlines and rail, Sources & credits, sudden height change

Written by the planner (Rowan Waymark), 2026-10-17, for the owner. Forager RECORD -1212 (the owner: "Yes, they gate it"). It sits beside the lint check, `preserved/2026-10-17-06-phone-check.md`.

**Where the steps come from:**
- **Parts C and D** quote steps already written in `docs/audits/` on main, with the gaps they left filled.
- **Parts A and B** had only one-line device items in PR bodies (#223, #224, #221). Their steps and pass conditions are written here for the first time, by the planner, from those items and the record (-985, -986, -991, -959).

All of this code is on main already, and in this order of cost:
1. **Phone, minutes:** parts C and B.
2. **Tablet:** part A.
3. **Outdoors or a building:** part D.

**Build:** the debug APK from pre-main's latest green CI run (the "app-debug-apk" artifact), installed with `adb install -r`. The S22 is the phone, never the S26. Results go in words; screenshots stay off GitHub. A fail on a gate step holds the release until the owner rules.

## Part C: Sources & credits links (S22; needs data or Wi-Fi and a browser)

Source: `docs/audits/2026-10-13-sources-and-credits-report.md:155-167` (origin/main). Merged in PR #225 (-992).

**C1. The page, portrait then landscape.**
- **Steps:** Tools → Settings → scroll to the bottom → Sources & credits. Tap each row in turn: OpenStreetMap, OpenTopoMap, Protomaps, Open-Meteo, iNaturalist. Then Open-source software, and Back twice. **Then turn the phone to landscape and repeat** (the original steps never rotated).
- **Pass:** the browser opens, in turn, openstreetmap.org/copyright, opentopomap.org/about, github.com/protomaps/basemaps, open-meteo.com/en/licence and inaturalist.org. Back returns to Software, then Sources, then Settings, at the row. Both orientations.
- **Evidence:** the list of addresses seen.

**C2. The Open-Meteo credit.**
- **Steps:** search a region. Open Seasonal (the Current Conditions card, and the fruiting-lag section once it loads), and Tools → Trip Planner (Trip Windows). Tap the credit in each.
- **Pass:** the credit sits at the foot of each, and each tap opens open-meteo.com.
- **Observation, not a gate:** whether the three cards' slightly taller credit row (made 48 dp for touch) looks out of place.

## Part B: map outlines and the side rail, against the real system bars (S22)

From PR #224's device items (dispatch `2026-10-13-02.md:52-55`) and PR #221's (`docs/audits/2026-10-12-rounded-map-strip-report.md:279-284, :507-509`). Merged in -991 and -959.

**B1. Outlines in daylight.**
- **Steps:** open the Maps tab with a normal map in day mode, in portrait.
- **Pass:** the search pill, the coordinate strip, the bottom bar and any card each have a thin, even outline against the map, with no gaps or doubled lines. No line shows through the pill where the strip tucks under it.

**B2. Outlines at night.** As B1, with night mode on. **Pass:** the same, and the outlines are visible but not glaring.

**B3. Landscape, both ways.**
- **Steps:** turn the phone left (camera on one side), then right (camera on the other).
- **Pass:**
  - the side rail's icons are evenly spaced and none is cut by the camera cut-out;
  - the search bar, the rail and any card stay clear of the system navigation bar on whichever edge it sits;
  - the strip and the rail meet with no gap.

**B4. While navigating.** Start navigation to any waypoint so the card grows. **Pass:** the card's outline stays even as it grows, in portrait and in one landscape.

**B5. Fullscreen.** Enter the map's fullscreen mode in portrait and in one landscape. **Pass:** nothing sits under the cut-out or the gesture bar.

## Part A: the tablet camera hold (the owner's tablet)

From PR #223's device items (body, "Update: large screens hold the device's natural frame") and -985: "a large-screen camera holds the device's natural orientation … and never turns. The icons follow the accelerometer." Merged in -986.

**A0. Before starting.** Note the tablet's Android version, and which way it naturally sits: the way the maker's logo reads, or the way the home screen stands when rotation is locked. The record last saw it on Android 16 (-80). The check assumed Android 17 (API 37), so record what it is.

**A1. Full screen, all four rotations.**
- **Steps:** open the in-app camera full screen. Turn the tablet through all four orientations, slowly, pausing at each.
- **Pass:**
  - the picture is always the right way up;
  - the camera layout never turns: it stays in the tablet's natural frame;
  - the shutter and other icons turn to stay upright;
  - taps on the shutter and the controls land where touched.
- **Watch:** the system's rotation animation. Note whether it flashes the wrong way before settling.

**A2. A photo at each rotation.** Take one photo at each of the four orientations. **Pass:** each saved photo is upright in the Journal.

**A3. The bars.** In each rotation, **pass:** no control sits under the navigation or gesture bar. The tablet has no cut-out (-80), so the cut-out item doesn't apply.

**A4. Split screen (sanity).** Put Forager beside another app and open the camera. **Pass:** it lays out to its own window shape, the picture is upright and taps land. Per -985, split screen is not held.

## Part D: height change, steady and sudden (S22; it has a barometer)

Step D1 is `docs/audits/2026-10-11-journal-entry-height-final-report.md:118-121` (origin/main), journal step 9. The owner's own words for what is untested were "how well it handles sudden elevation" (-924), and step 9 is a steady climb, so D2 is added for the sudden case.

**D1. A steady climb of known height** (a known hill or stairwell).
- **Steps:** record a track from the bottom to the top at a walk; stop at the top.
- **Pass:** the entry's Climb is within ±10% of the known climb.
- **Evidence:** the Climb shown, the known height and where that figure came from.

**D2. A sudden climb of known height** (a lift/elevator of known floors, or a steep road in a car).
- **Steps:** record a track; ride up quickly; wait a minute at the top; stop.
- **Pass:** Climb within ±10% of the known climb.
- **Evidence:** the Climb shown, the known height and its source, and roughly how long the rise took.
- **The planner's prediction, inferred and not tested:** this may read low. The barometer smoothing treats a height change faster than 0.5 m/s as a pressure blip (`domain/BarometricHeight.kt:135`; `docs/audits/2026-10-11-barometer-end-drop-fix-report.md:24-26`), and a lift rises at about 1 to 2 m/s. A low reading here is a finding for the owner to rule on (ship as is, or fix first), not a broken phone.

**D3. Observation, not a gate.** At the end of D1 or D2, drive off with the vents on, still recording. Note whether the chart shows a step beyond the road's grade.

## Reporting

For each step: pass, fail or observation, in a sentence. Include the tablet's Android version and natural orientation, and any step skipped and why.
