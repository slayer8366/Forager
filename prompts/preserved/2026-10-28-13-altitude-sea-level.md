# Dispatch 2026-10-28-13: altitude above sea level, steadied by the barometer (a coder, Opus)

Written by Rowan Waymark, 2026-10-28. RECORD -1792. Cut from **pre-main d100a4776**; verify that it is still the remote head. It builds on docs/audits/2026-10-28-altitude-report.md (branch altitude-report, 893c57a8c). Read that report first; its file:line references are the map for this work.

**Authorisation.** The owner, verbatim:
- "Sea level + barometer (Recommended)"
- "Keep raw, show sea level (Recommended)"
- "One: \"Alt unavailable\" (Recommended)"
- "Follow the barometer (Recommended)"
- "Yes, and measure it (Recommended)"
- "Yes, same as the strip (Recommended)"
- "Start a coder now"

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change
1. **Sea level.** A fix's height is converted with the platform's converter behind an app-owned interface, as the report proposes (`SeaLevelHeight`, `AndroidSeaLevelConverter`), at the point the location arrives.
   - Where the converter is unsupported (Android below 14) or fails, the height is "unsupported" and logged once. It is never the raw height.
2. **Steadied by the barometer.** The existing barometric anchoring is run live, with its existing settings and nothing new tuned.
   - With no barometer, the converted GPS height is used.
   - When GPS drops out, the height follows the barometer.
   - The barometer is read whenever the map is open as well as while recording.
3. **One height everywhere live.** The strip, the navigation card (NavigationReadout.kt:65) and the Return control's spoken height difference all read the one steadied sea-level height.
4. **"Alt unavailable"** replaces "Elevation unavailable", and is the one phrase for "no height" and "can't convert".
5. **Saved data stays raw** (stored points, Start/End markers). The journal's highest and lowest labels (EntryReportSummary.kt:126-127) and GPX export (GpxCodec.kt) convert to sea level when they show or write.
6. **GPX import (ImportGpxUseCase.kt:96): stop first.** A file's `<ele>` is probably already sea level, so storing it as raw and converting it again would read about 21 m high.
   - Propose how an imported track is kept right, and wait for the planner to relay the owner's answer before building this part.
   - Every other part may go ahead.
7. **Correct the wrong premise** in BarometricHeight.kt:11's comment ("GPS gives height above sea level").

## Tests
- The report's failing tests, through the real entry points: the live-location path and the composed strip, the navigation card agreeing with the strip, unsupported reading "Alt unavailable", the barometer read with no recording, no barometer, and a GPS gap following the barometer.
- Exports and the journal labels converted; stored points unchanged.
- Made-up walks only, never the owner's stored walk.
- Each fails first, then a revert check, with the build log checked; restore from a saved copy.
- CLAUDE.md's poll-loop entry: no test may spin virtual time on a sensor loop.
- **Modular rule:** the main screen ViewModel (797) and its layout file (1,440) are split first if they must change; say so before touching them.
- CI only. Open a PR into pre-main, with no merge.
- **S22 steps in the PR body:** the strip reads near the known ground height; the S26 if the owner allows; a battery measurement with the map open for 30 minutes with and without the barometer, as the owner asked.

## Rules
- Network: GitHub only.
- The Restricted Object: never read, name or quote it.
- Long conversation: past about 200k tokens, push and hand off.
- Ambiguity: stop and report.
- New words on screen are the owner's only.

## Amendment 1 (2026-10-28): GPX import and export answered (RECORD -1793)
Hazel's proposal (message to the planner) for item 6, and the owner's answers:
- **"Show as the file gave them (Recommended)"**, Hazel's P1. Import stores the file's `<ele>` unchanged. Every reader that converts to sea level (the journal's highest and lowest labels, and GPX export) skips the conversion for an imported track (Track.importedAtEpochMillis set) and for its waypoints. No migration.
  - Known gap, accepted: a file Forager exported before this change holds raw heights, and re-imported it reads about 21 m low. No detection is built.
- **"Leave heights out (Recommended)"**: on a phone that can't convert, a recorded track's GPX export omits `<ele>`.
- **"Keep it raw (Recommended)"**: the forager:point `ele=` (GpxCodec.kt:136) stays the literal stored value. Only the standard `<trkpt>` and `<wpt>` `<ele>` are converted.
- **The ViewModel split** Hazel announced (locateMe and onLocateMePermissionDenied into AvailabilityLocateMe.kt, a pure move, its own commit) is accepted. AvailabilityCompactScaffold.kt is not touched.
Each rule gets a test that fails first, then a revert check.

## Amendment 2 (2026-10-28): the height chart on phones that can't convert (RECORD -1794)
The owner: "Chart with no numbers (Recommended)". Below Android 14 the journal entry's and the Records track sheet's height chart (EntryReportSummary.kt:126-127) still draws its shape and length, with no height labels and no new words. Write a test that fails first, then a revert check.
