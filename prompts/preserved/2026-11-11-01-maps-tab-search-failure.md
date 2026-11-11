# Dispatch 2026-11-11-01: a failed or loading sightings search no longer replaces the Maps tab (Hazel Cairnwright, Opus)

Written by Rowan Waymark, 2026-11-11T05:57:33Z. RECORD -2204. **Written, not started: no helper starts until the owner has read this and says go.**

Base: **origin/main** (137b1427c when written; check against the remote). This is an urgent fix for a live bug, so it starts from main, not pre-main (planner profile: "An urgent fix for a live bug starts from main and passes the same gate at the front of the queue, then is carried into pre-main").

You are Hazel Cairnwright, the app coder on Forager. Read CLAUDE.md and docs/process/coder-profile.md first.

## The owner's words
- To "H1: when a sightings search fails or is loading, what should the Maps tab show?": **"Map stays, auto retry (Recommended)"**. That option read: search fails > map, tabs and search bar stay > a line over the map says the search failed, with Try again > it also retries by itself when signal returns. Loading > map stays, small progress mark.
- To "How fast should its fix go?": **"Urgent, from main (Recommended)"**. That option read: Hazel first reproduces it in a test. If it is real, the fix starts from main, passes the usual gate at the front of the queue, and ships as its own release.
- Standing rules this touches (CLAUDE.md, UX defaults): "nothing should fully obstruct the map view. All map chrome gets 80% opacity as a result."; "Don't make me think."

## Where this comes from
The 2026-11-10 audit of main, finding H1 (pre-main: docs/audits/2026-11-10-main-engineering-audit.md), known bug 30. **Read from code only. Nobody has seen it fail, on a phone or in a test.**

## Premises to re-check (read by the planner at main 137b1427c; each is a claim about the past)
Paths are under app/src/main/java/com/zynergylabs/forager/app/.
1. ui/availability/AvailabilityCompactMapUi.kt:372-385: a `when` shows a centred spinner while `uiState.isLoadingSightings`, a `MapMessage` (AvailabilityMapOverlaysUi.kt:378) when `uiState.sightingsErrorMessage != null`, and the map only in the `else` branch.
2. The Maps tab's own bottom bar or rail and its search bar are composed only inside that `else` branch (the audit read them near :725 and :567), and the Scaffold's bottom nav is not shown on the Maps tab (AvailabilityCompactScaffold.kt:886-890, `shown = compactTab() != CompactTab.MAP && !showRail`).
3. The error is set at ui/availability/AvailabilitySearchRuns.kt:210-217 ("Couldn't load sightings for the map."), and the fetch runs again only when one of the keys at ui/availability/AvailabilityScreen.kt:507 changes, so nothing on the error screen can run it again.
4. Back from that screen leaves the app, or shows the leave prompt while navigating (AvailabilityScreenBackHandlers.kt:126-128, :152-159).
5. domain/NetworkSignal.kt is the project's own signal source (`validated: Flow<Boolean>`).
6. MapChromeOverMapTest.kt refers to these two state fields. The planner did not read what it asserts.
7. Not known: what the wide (rail or two-pane) layouts do in the same two states. Find out and report.

## Step 1: see it fail, and report before building
- Write a test through the real screen (the Availability screen on the Maps tab, a search whose sightings fetch fails) that shows the failure: no map, no search bar, no tab bar, no way to run the search again. A second for the loading state. If either does not fail as predicted, stop and report: the premise is wrong, and the fix is not built on a guess.
- Report by message, before writing any fix: each premise above confirmed or wrong, with file and line; what the wide layouts do; where you propose the notice and the progress mark sit, in plain terms a user would recognise; which files you will touch and their line counts; and the four disclosure sections (confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope).
- **Stop there.** The planner brings the placement and the wording to the owner. Build only after the planner relays the owner's go.

## Step 2: what to build (after the go)
- **Search fails:** the map, the tabs and the search bar stay exactly as they are. A line over the map says the search failed, with a "Try again" control that runs the same search again. The line's words come from the owner through the planner; until then use today's text, "Couldn't load sightings for the map."
- **Signal returns:** the failed search runs again by itself, using domain/NetworkSignal.kt. Once per return of signal, not a loop: a retry that fails again waits for the next return or for the user. Say in the report what you did about a failure that is not a signal failure (a server error with good signal).
- **Loading:** the map, the tabs and the search bar stay. A small progress mark shows that sightings are loading. A recording or navigation view is never taken off screen by a search.
- **Over a map:** the notice and the mark are map chrome, so their fill is `MAP_CHROME_OVER_MAP_ALPHA` with opaque content, and they do not take touches outside their own bounds (CLAUDE.md, the Surface pitfall). At least one coordinate-touch test that a long-press on the map beside the notice still reaches the map, and that a real touch on "Try again" runs the search; sample several points across the control, not its centre alone.
- **New code is a new function or file,** not a condition threaded through working code where that can be avoided. AvailabilityCompactMapUi.kt is 793 lines: if the fix would take it past about 800, put the new composables in their own file and say so.
- **Nothing else changes.** Not the search race (audit M13), not the cleared-search return, not Back's handlers, not the error text's words, not any other tab.

## Proof
- The step 1 tests go from red to green. One revert check per behaviour (the map branch, the retry, the auto retry), each run on CI with the build log checked for compile errors before its results are read, each failure message specific to its own edit, the forward change confirmed still present afterwards.
- Full CI by name against main: no test removed, skipped or weakened. A test unrelated to this that fails is reported, not touched.
- A completion report under docs/audits/ with the four disclosure sections and S22 steps for the planner (airplane mode on > search > the notice over the map > airplane mode off > the search runs again by itself; the same while recording and while navigating). Robolectric cannot show what a person sees here, so say plainly what only the phone can confirm.

## Branch and finish line
New branch `maps-tab-search-failure` from origin/main. A PR into main, ready and **not merged**. It goes to Sorrel Tally for review, then the S22, then a build to the owner, then the gate; the planner carries it into pre-main afterwards. The planner writes the audit-index row, the known-bugs change and the record entries; you do not touch docs/audits/README.md, docs/process/known-bugs.md or RECORD.md.

## Rules
No Gradle on the laptop; CI only. **The laptop's system disk is full (about 300 MB free on /):** work and scratch under /mnt/work/scratch, never /tmp and never under the home directory, and stop if a checkout fails for space. Never reset, rebase or amend; merge, never rebase. Commit and push each step. Commits end exactly `Co-Authored-By: Claude <noreply@anthropic.com>`, no model name. Scan every push with the private scanner; 0 required. One coder per file: check open PRs for the files you will touch before you start, and stop if another branch holds one.

**Stops:** a wrong premise; the failure not reproducing; any choice a user would see beyond what is written here (placement, wording, timing of the notice); a file shared with another open branch; a change needed outside ui/availability and its tests; two failed attempts at the same symptom.
