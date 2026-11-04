# Dispatch 2026-11-04-05: The style picker's selected chip in cream by day; why the clone-night fix didn't show on the S22 (Hazel, Opus)

Written by Rowan Waymark, 2026-11-04. RECORD -2062. Every fact below is a premise to re-check, with file and line.

## Amendments, by category
| | Category |
|---|---|
| **A** | What the user sees and does |
| **B** | Why the clone-night fix wasn't seen |
| **C** | Branch, build and tests |

**For every item:** verify first: read the code and report to the planner by message before writing anything. Then fail first, then a revert check on a throwaway CI branch (check the build log for compile errors; restore from a saved copy). Full CI must be green on the last real commit. CI only: no Gradle on the laptop. Don't touch the phone. Never communicate with any session named Aspen. Never read, name or quote the Restricted Object. Our own map is the "Trail Map". Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name. New user-facing words go back to the planner before they're built. Stop and report, don't choose, on anything the owner would see that this doesn't rule.

## A. What the user sees and does
- **A1** (2026-11-04, -2062). The owner, with an S22 screenshot of the Layers popover in day mode (Trail / Topo / Street, with Trail selected in rust red): "That highlight isn't supposed to be red. It's supposed to be a cream color like the theme suggests". Then: "Night mode gets the red." So, by day, the selected chip is the theme's cream; at night, it stays as it is today. Find where the chip's selected colour comes from (file:line) and which theme token is "cream". Check whether other pickers or segmented controls share the same colour, and list them; don't change them without the planner's word. The text colour must stay readable on cream: say what it becomes.

## B. Why the clone-night fix wasn't seen
- **B1** (2026-11-04, -2061). On the S22, build 4545 (pre-main 2d1dc54a4 with #481), Night Maps on, every Trail load logged "Trail night: the style records no forager:layer-clones; no clone layers to map." That happened inside downloaded regions (both are street-v3, which has no clones) and in Portland, outside them. The Worker's served trail-v4.json and street-v4.json do carry metadata["forager:layer-clones"] (checked by curl). Find out which of these is true:
  - (a) The app draws street-v3 even outside the v3 regions. For example, the style chosen follows the downloaded regions, or MapLibre's offline store serves a cached older trail-v4.json.
  - (b) MapLibre's Style.json on the device doesn't return the metadata that withLayerClonesOf reads (OfflineNightRecolourApply.kt, MapStyleReload.kt:148). Compare it with NightImageApply.kt:85's read of metadata["forager:sprite-icons"]: does that metadata reach the device's Style.json? Is there a log line that would show it?
  - (c) Something else.
  Report from the code, with file:line. If it can't be settled from the code, propose one log line that names the loaded style URL and whether its metadata held the clone record, for the next phone session. If (b), propose how to get the record another way (for example, from the style the app fetched or bundled), and don't build it until the planner says go.

## C. Branch, build and tests
- **C1**. One branch, style-picker-cream, cut from origin/pre-main (2d1dc54a4 or later), for A1 and any B log line. The PR goes against pre-main when the planner says.
