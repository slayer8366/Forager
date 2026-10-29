# Dispatch 2026-10-28-12: altitude above sea level, look-only report (a coder, Opus)

Written by Rowan Waymark, 2026-10-28. RECORD -1791. Cut from **pre-main d100a4776**; verify that it is still the remote head.

**Background.** The 10-28 S26 walk read (docs/audits/2026-10-28-s26-walk-read.md, branch s26-walk-2026-10-28, b3877b0c):
- the strip's "Alt" is the raw fix altitude, `Location.getAltitude()` (AvailabilityUiState.kt:415, from AndroidLocationTracker.kt:130);
- that is height above the WGS84 ellipsoid, with no sea-level conversion;
- on the walk it read a mean 23.8 m below the trail data's ground heights, with about ±10 m of fix-to-fix noise;
- the barometer tracked the ground closely (correlation 0.82, against 0.23 for GPS).

**The owner's choice:** "Sea level + barometer (Recommended)". Convert GPS height to sea level with the phone's own converter, and smooth the jumps with the barometer. On a phone without the converter, it says "Alt unavailable" rather than show a wrong height. Then "Yes, report first (Recommended)".

**Look-only.** No app code, and no phone. Every claim needs a file:line, or is marked unverified. CLAUDE.md applies: check who calls a path before saying what it does.

## Report
1. **Every reader of a fix's altitude:**
   - the strip, the navigation card's elevation, the stored track points, GPX export, the climbing allowance (barometer only, -1689), the elevation-difference line on the Return control, finds or photos if any, and anything else;
   - for each, say whether it should read sea-level height after this change.
   - Stored data is a stop: say what changing stored altitude would mean for old tracks and GPX, and propose, don't decide.
2. **The converter:**
   - Android's own sea-level conversion (name the API and its minimum Android version), and what data it needs or downloads;
   - whether the S22 (SM-S908U) and S26 (SM-S948U) support it: inferred from their Android versions, marked so;
   - how it fails or reports "unsupported";
   - this is a vendor API, so propose the interface the app owns in front of it (CLAUDE.md).
3. **The barometer smoothing:** propose how barometer changes smooth GPS height (a GPS level anchored over time, barometer for short-term changes). Name what it does with no barometer, with a stale fix, and at a weather pressure drift. Use the 10-24 and 10-28 walks' stored data as the test inputs, and report the numbers your proposal would have shown against the trail data's ground heights. No speculative logic beyond what this data shows (CLAUDE.md).
4. **Words on screen:**
   - "Alt unavailable" is the owner's;
   - where the strip shows "Elevation unavailable" today (NavigationHudText.kt), say how the two would sit together;
   - any other new words are a stop.
5. **Tests:** what fails first, and through which real entry point.
6. **File sizes:** the files touched and their line counts, under the modular rule.

## Output
docs/audits/2026-10-28-altitude-report.md on a branch altitude-report, cut from pre-main, pushed. Your final message gives the findings, plus the owner's questions in plain behaviour.

## Rules
- CI only, and no Gradle on the laptop.
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
- The walk data stays in /home/zynergy-labs/s26-2026-10-28/ (laptop only); read it there and don't commit it.
- Ambiguity: stop and report.
