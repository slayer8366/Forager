# Dispatch 2026-11-04-04: The way-to street drop-off series, made ready for release (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2050. No dispatch file was ever written for this series (-2048). The PR bodies of #433 and #435 to #439 hold its design and evidence, and this file holds the owner's rulings from here on. The owner: "Release is gated by waypoint series".

Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees and does |
| **B** | Branch, build and tests |

**For every item:** verify first. Read the code and report to the planner by message before writing anything. Then fail first, then a revert check on a throwaway CI branch (check the build log for compile errors; restore from a saved copy). Full CI must be green on the last real commit. CI only: no Gradle on the laptop. Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Our own map is the "Trail Map". Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name. New user-facing words go back to the planner before they're built. Stop and report, don't choose, on anything the owner would see that this doesn't rule.

## A. What the user sees and does
- **A1** (2026-11-04, -2050). Old downloads, the owner's ruling on the question "Areas downloaded before the new fence data have no fence marks… What should happen for those areas?": "Trail route, no drop-off (Recommended)": "In areas without fence data, Navigate works exactly as it does today (by trail), with no street drop-off. The drop-off only appears where the map can check for houses, fences and water. Nothing goes dark." This replaces #438's point 1 (MAP_NOT_CHECKED for every way in an unmarked area). Where the fence mark is absent, the ends step passes the engine's ends through, and the line is logged once per reason. The fallback is never silent.
- **A2** (2026-11-04, -2050). The walker's end, the owner's ruling on the question "At your own end of the walk, the series checks the straight lead-in… but has no "pick-up" chooser… Is that needed for release?": "Needed for release": "Build the pick-up chooser at the walker's end before release. A bigger build." Verify first: what the earlier design said about the pick-up end (look in #437's and #438's bodies and commits). Then propose the mechanism, mirroring the drop-off chooser at the far end, and any new words, for the planner to bring to the owner.
- **A3** (2026-11-04, -2050). The three notes, the owner's ruling "Yes, as written (Recommended)": WAY_IN_BLOCKED "No clear way in to the waypoint · straight line"; LEAD_IN_BLOCKED "No clear way from you to the route · straight line"; MAP_NOT_CHECKED "Couldn't check the map for houses, fences or water · straight line". Under A1, say where MAP_NOT_CHECKED can still show, if anywhere.
- **A4** (2026-11-04, -2059). Hazel's verify-first, at wayto-dropoff-wiring 17188bdbe. All six PRs are green on their heads, and they dry-merge clean onto pre-main 2d1dc54a4. There is no written design for the pick-up end. The owner's rulings, each the option as read:
  - Which tiles: "Any nearby tile (Recommended)": "If any stored tile within the check's reach (about 350 m round the waypoint, or along your lead-in) has no fence data, that end goes by trail as today. Nothing goes dark, even at the edge between an old and a new download." The check goes before classify and the gates (Hazel's (ii)).
  - A store-read failure: "By trail, logged (Recommended)": "Same as an older map: Navigate goes by trail as today, with no drop-off, and the error is logged." MAP_NOT_CHECKED is then left for an end whose tiles are not stored at all. Hazel lists where it can still show.
  - The pick-up and GPS: "Need a good fix (Recommended)": "Only choose a pick-up point when the fix is accurate to 15 m or better; with a rougher fix, your end works as today (gated straight lead-in). Re-checked as you walk on."
  - The planner's calls: logged per call, as the existing fallbacks are; A2 uses Hazel's mechanism (classify and choose reused, settle both ends, then one two-ended re-run, so at most two engine runs). There are no new words: LEAD_IN_BLOCKED covers a pick-up refusal. No mixed-tile fixture for now; the gap is noted in the PR. The order is A1, then A2, each with its own fail-first, revert check and full CI, on wayto-dropoff-wiring.
- **A5** (2026-11-04, -2060). The owner: "1 yes" to the planner's question "Treat an unreadable .rd5 routing file like a map read error, so the waypoint goes by trail and the error is logged?". An end whose route ways (.rd5) can't be read goes by trail, logged, as a store-read failure does. The planner accepts Hazel's two readings: "re-checked as you walk on" uses each later search's own fix, with no new trigger; and on a good fix, the read round the walker reaches 350 m, so the tile check there does too.

## B. Branch, build and tests
- **B1** (2026-11-04, -2050). First, bring the series current: merge pre-main into the stack in order (fixtures; then tile-reader and closed-ways; then gates; then fence-mark; then wiring), with no rebase or force-push. Resolve AppContainer keeping both sides' behaviour. Full CI on #438's head.
- **B2**. A1 and A2 land on the series' branches (A1 on wayto-dropoff-wiring, or wherever the ends step lives; say which). The planner merges only on the owner's word, in stack order.
- **B3**. Still device-only, and recorded rather than assumed: what MapLibre 13.5.0 writes to mbgl-offline.db, whether a read-only open works alongside MapLibre (#435), and the second engine run's time on a phone (#438 point 4). Also whether basemap patch 0004's tiles are served (#439).
