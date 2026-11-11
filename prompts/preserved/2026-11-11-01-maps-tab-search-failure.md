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

## Amendments

### A. Facts that changed after writing
- **A1 (2026-11-11T13:09:04Z).** The Rules line "The laptop's system disk is full (about 300 MB free on /)" is out of date: after the owner's "Delete cache and scratch", / has 4.1 GB free and /mnt/work 5.7 GB. The rest of that line stands: work and scratch under /mnt/work/scratch, never /tmp, never under the home directory; stop if a checkout fails for space.

### B. The owner's go
- **B1 (2026-11-11T13:09:04Z).** The owner read the dispatch in full and said: **"Go"**. Hazel starts at Step 1. The stop after Step 1's report stands.

### C. The owner's rulings after Step 1 (2026-11-11T14:19:58Z)
Step 1 reproduced the failure: 9 of 9 tests failed as predicted on CI (run 43027173891, commit e38cd5243), in portrait, short landscape and landscape tablet. The planner then asked the owner seven questions. Each answer is the owner's chosen option, verbatim; the line after it is what that option read.
- **C1. Where the notice sits: "Under the search bar (Recommended)".** Search fails > a one-line red-tinted banner appears just under the search bar (below the compass strip in portrait) > "Try again" at its right end. Same spot and look as the existing search-error banner. 80% fill over the map.
- **C2. The loading mark: "Thin line on the search bar (Recommended)".** Search runs > a thin moving line runs along the bottom edge of the search bar > it goes when sightings arrive. Nothing in the bar moves.
- **C3. The list search and the map search both fail: "One line (Recommended)".** Both fail > the Maps tab shows one notice with "Try again" > tapping it runs both again.
- **C4. Automatic retry: "At once (Recommended)".** Signal returns > the search runs again straight away. Needs the raw signal source, so the change reaches outside the search screen's own folder.
- **C5. The words: "\"Search failed. Check your connection.\" (Recommended)".** This replaces Step 2's "until then use today's text" for the Maps tab notice.
- **C6. The search bar is hidden (fullscreen; navigation if confirmed): "Top of the map (Recommended)".** Search fails while the bar is hidden > the same one-line notice shows at the top of the map, where the bar would be > "Try again" works the same. The failure is never silent.
- **C7. A failure with good signal: "Notice stays, no auto retry (Recommended)".** Search fails with signal > the notice stays with "Try again" > it runs again only when the user taps it or searches again.

### D. Scope changes that follow (the planner's, from C)
- **D1.** Because of C4, the stop "a change needed outside ui/availability and its tests" is lifted for one thing only: giving the search screen the raw `NetworkSignal.validated` flow (a provider wired where the existing one is built, or the smallest equivalent). Say which you chose and why. Once per return of signal still holds; a signal that flaps must not produce a burst of calls, and a test shows that.
- **D2.** The `sightingsFailure` switch in RealSearchScreenRig and the two landscape test classes, added in Step 1 beyond the dispatch, stand.
- **D3.** Not ruled, so not changed: what the Maps tab shows when only the list search fails and the map's sightings load (today's banner, as today); the text of the existing list-search banner on other tabs. If C3 cannot be built without changing either, stop and report.
- **D4.** Confirm from code and a test whether the search bar is hidden while navigating; C6 applies wherever it is hidden. Premise 6 was wrong as written (MapChromeOverMapTest refers to isLoadingSightings only); update its stale comment if the fix makes it false, and change no assertion.
