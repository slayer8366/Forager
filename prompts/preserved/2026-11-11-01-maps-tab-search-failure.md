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

### E. The owner's rulings after Sorrel's review (2026-11-11T16:38:28Z)
Sorrel Tally (Fable) reviewed PR #516 at head 98dc401dc: **pass with items**. The planner asked the owner four questions. Each answer is the chosen option, verbatim; the line after it is what that option read.
- **E1. The changed assertion in SearchClearAndFrameTest: "Keep it, and prove it (Recommended)".** The changed assertion stays. Hazel renames the test (its name still says "spinner") and runs one revert on CI to show the test still fails if the bug it guards comes back. The breach is on record.
- **E2. When the search may retry by itself: "Only with Forager on screen (Recommended)".** Signal returns > Forager is open on any tab > the search runs again. Forager in the background > nothing runs until you come back, then it runs once. No data used behind your back.
- **E3. A limit: "3 tries, then wait for me (Recommended)".** Signal keeps flapping > the search tries by itself up to 3 times > after that the notice stays and only "Try again" or a new search runs it.
- **E4. During a retry: "Notice stays, shows it's trying (Recommended)".** Retry starts > the notice stays where it is and reads "Trying again…" > it goes only when the search works, or returns to "Search failed" if it doesn't. Nothing jumps, and fullscreen shows it too. The words are the owner's to change.

### F. Back to the coder from Sorrel's review (the planner's, not owner rulings)
- **F1. The notice while navigating, portrait.** Sorrel reads (CompactMapTopStrip.kt:138, :171; ScaffoldMapTabCall.kt) that the compass strip's height is forced to 0 while navigating, so the notice would sit under the navigation card, hidden or untouchable. From code only. Write a test through the real screen that navigates, fails the search, and takes real coordinate touches across "Try again". **If it is covered, stop and report: where it sits while navigating is the owner's.** Do not pick a place.
- **F2. A signal return during a hanging fetch is lost** (RetryFailedSearchOnSignalReturn reads `failed` at the instant of the return). A return that arrives while the fetch is in flight must still lead to one retry if that fetch then fails. Test it, with its own revert.
- **F3. E2 and E3 in code,** each with a test and its own revert: no retry goes out while the app is not on screen (resumed); on coming back with the notice showing and signal present, one retry; a count of 3 automatic tries per failed search, reset by "Try again", a new search or a success. The 10 s spacing stays as the coder's operating limit. Say what happens on rotation and on leaving and re-entering the screen (a return in between), and test it.
- **F4. E4 in code:** the notice keeps its place and its size through a retry (the text changes inside a fixed space; no shrinking text; the icon cluster does not move), in fullscreen too. The loading line of C2 stays as ruled.
- **F5. E1:** rename the test; one revert on CI that removes the searchFrameSerial comparison in CompactMapLocateFocus.kt and shows this test's unchanged assertions fail with a message specific to that edit.
- **F6. Tests weaker than their names:** long-press beside the notice also in landscape (width-capped notice) and beside the fullscreen notice, with a revert that fails for a swallowed touch (a notice made to fill the width); the notice clears the compass strip; the fullscreen notice is at the top of the map and inside the screen; opaque content colour asserted; the icon cluster's top limit follows the notice (no test today); chips do not overlap "Try again"; a revert for the removed spinner branch.
- **F7. "Both failed" depends on the list's message still being its failure text.** If another message overwrote it, only the map half retries. Report what a user sees then; change nothing unless it breaks C3.
- **F8. Tidy:** remove MapMessage if it has no caller, and the `when` with only an `else`, if that does not push a file's blame for no gain; say what you did. Narrow the two members widened to `internal` if the extension can live without it; if not, say why.
- **Left as they are, not ruled:** the existing banner's other messages can still show above the new notice; the brief overlap as the bar slides on entering fullscreen.

### G. The owner's rulings after the second hand-back (2026-11-11T19:38:37Z)

- **G1.** Where the notice goes while navigating (the navigation card covers it in portrait; F1). The owner: **"Under the navigation card"** (search fails while navigating > the navigation card stays where it is > notice shows just below the card; not navigating: under the search bar, as now). This is the owner's own change to C1 for the navigating case only.
- **G2.** The "Try again" button while Forager is trying again by itself. The owner: **"Shown, greyed out"** (retry starts > the button stays visible but grey and does nothing > it turns normal again if the retry fails). This replaces Hazel's reading (hidden, space kept).

### H. The planner's notes on G, for Hazel

- **H1.** G1 is proved with the F1 probe test brought onto the branch: real touches at several points across "Try again" while navigating and recording, portrait, all run the search; a long-press on the map beside and below the notice still reaches the map; the notice is 80% over the map and does not overlap the card, the compass strip or the icon cluster. Landscape and tablet while navigating: test where the notice lands; if it is covered there too, stop and report, do not place it.
- **H2.** The search-error banner that was there before this dispatch (covered the same way, from code only) is not moved. Report what a test shows; it is the owner's to rule.
- **H3.** G2: grey means the app's existing disabled look, no new colour. The button's space and the notice's size do not change between the two states; the large-font test keeps holding that.
- **H4.** Any existing test that fails is a stop, as before.

### I. Back to the coder from Sorrel's second review (2026-11-11T21:10:59Z; the planner's, not owner rulings)

- **I1. "Trying again…" can stick.** `retryFailedSearch` sets `isRetryingSearch` and then `onMapTabSelected()` can return early without starting a fetch (the showing search already loaded; an older fetch failed late). The notice then reads "Trying again…" for good with the button greyed, against E4 (the owner: "Notice stays, shows it's trying (Recommended)", whose option read that it returns to "Search failed" if the search doesn't work). Fix only this: the flag is set only when a fetch really starts, or is cleared when none does. First a test through the real screen that reaches the state (search A held, search B succeeds, A fails, then "Try again") and fails on the branch as it is. The late failure itself is audit M13 and stays out: do not cancel fetches, do not change what failure shows over a good map. Report what "Try again" does in that state after the fix; if the only honest answers are "nothing" or a change to M13's behaviour, stop and report.
- **I2.** A test that can fail for the icon cluster's top limit under the placed notice: cluster dragged to its top while navigating in portrait, and in fullscreen, with a revert.
- **I3.** The "beside the notice" revert failed on its positive control. Add a revert that fails on the swallowed long-press itself, or say why none can be made.
- **I4.** In the G2 test, "a touch on the greyed Try again ran the search" cannot fail. Make it able to fail (a real touch on the button at a moment when an enabled button would run the search), or remove the claim from the message and the report. No other assertion changes.
- **I5.** Add to the report's S22 steps: cluster dragged to the top under the placed notice; fullscreen while navigating; the fullscreen notice against the real status bar; large font, portrait and landscape.
- **Noted, not sent back:** a both-failed automatic try on another tab holds the flag until the Maps tab opens; a return of signal inside one frame can spend a try; a held return can run sooner than 10 s after the last try; the count and a held return are lost when the Activity is made afresh; the spare compass-strip spacer in the search column while navigating. Sorrel read these from code; none is tested.

### J. The owner's ruling on the old banner (2026-11-11T21:17:21Z)

- **J1.** The search-error banner from before this dispatch, hidden under the navigation card while navigating in portrait (H2). The owner: **"Under the navigation card too"** (list search fails while navigating > the card stays put > banner shows just below the card; done in this same fix). This replaces H2's "is not moved".
- **J2 (the planner's).** Proof as for G1: the H2 probe test brought onto the branch and passing; when the banner and the new notice both show while navigating, neither covers the other, the card, the compass strip or the icon cluster, and the order is the one they have under the search bar today; long-presses below still reach the map; 80% fill, opaque text. Landscape and tablet: test where the banner lands; if covered, stop and report. A revert for the placement.

### K. The owner's ruling on "Try again" over a loaded map (2026-11-11T23:15:41Z)

- **K1.** When an older search fails late over a newer search's loaded map, what a tap on "Try again" does. The owner: **"Fetch again (Recommended)"** (tap "Try again" > shows "Trying again…" > the showing search's sightings are fetched again > notice goes when that works, or returns to "Search failed" if it doesn't).
- **K2 (the planner's).** MapsTabSearchStaleFailureTest now pins this: after the tap, exactly one more sightings fetch for the showing search, "Trying again…" while it runs, the notice gone on success, "Search failed. Check your connection." back on failure, the map present throughout. The same for an automatic try on a return of signal in that state, within E2 and E3. Nothing is cancelled and the late failure itself (audit M13) is not changed. A revert that fails on the fetch count.

### L. The owner's rulings after Sorrel's third review (2026-11-12T00:39:47Z)

- **L1.** The old banner in fullscreen while navigating, where it is not shown at all today. The owner: **"Under the card in fullscreen too"** (fullscreen + navigating + list search fails > banner shows just below the navigation card, above the "Search failed" notice).
- **L2.** Order of work. The owner: **"Hazel first, then the S22"** (one more round > Sorrel glances at it > new build > then the phone; the phone check runs once, on the final build).

### M. Back to the coder (the planner's, not owner rulings)

- **M1.** L1 is proved as J1 was: through the real screen, fullscreen while navigating in portrait, banner just below the card and above the notice, neither covering the other, the card or the icon cluster; 80% fill, opaque text; several real touches across "Try again" with both showing; long-presses below and beside reach the map; a revert. Fullscreen when NOT navigating is not ruled: the banner stays not shown there, unchanged; say so in the report. Landscape and tablet in fullscreen while navigating: test where it lands; if covered or absent, stop and report.
- **M2.** Run Sorrel's one-edit revert for the notice under the bar (the swallowing prefix on the bar's column, ScaffoldMapTabCall.kt:335). If it fails on the long-press itself, cite it and withdraw the report's "none can be made"; if it does not, say what happened.
- **M3.** K2 gaps: a signal-return-then-fails case; the fetch check compares month and filter with the showing search's, not with themselves; assert the showing search's observations stay on the map during the refetch and after a failed one; correct the I1 test's message ("with nothing to fetch") without changing what it asserts.
- **M4.** J2 gaps: with banner and notice both showing while navigating, several real touches across "Try again" and more than one long-press point. Correct the report's "every revert was one edit" (the J1 revert is two lines).
- **M5.** A cluster dragged while the notice already shows: test what it does when the notice goes, and what the cluster does today under the bar's other notices in the same order. Report both; change nothing. If they differ, it is the owner's.
- **M6.** S22 steps: remove or correct step 8's out-of-date H2 note (by an appended correction, not a rewrite); add "Try again" with banner and notice both under the real card; the banner in fullscreen while navigating; the banner while navigating in landscape; large font with both stacked; the cluster dragged while the notice shows.
- **Noted, not sent back:** an older failure landing during a K1 refetch clears "Trying again…" early and ends correctly (audit M13 family).

### N. The owner's rulings after the sixth hand-back (2026-11-12T01:36:11Z)

- **N1.** The old banner in landscape and on a tablet, in fullscreen while navigating, where it is not shown at all. The owner: **"Show it there too"** (fullscreen + navigating + landscape or tablet + list search fails > banner shows on the map just above the "Search failed" notice, wherever that notice sits in that layout).
- **N2.** A cluster dragged up while a notice shows stays below it after the notice goes. The owner: **"Keep as it is"** (notice goes > the cluster stays where you left it; same as the old banner today; no change).

### O. Back to the coder (the planner's, not owner rulings)

- **O1.** N1 is proved as L1 was, in short landscape and landscape tablet: through the real screen, fullscreen while navigating, the banner just above the notice; neither covers the other, the navigation card or rail, the compass strip or the icon cluster; 80% fill, opaque text; several real touches across "Try again" with both showing; long-presses beside and below reach the map; a revert that fails in those two layouts. The probe test from mtsf-lm-probe comes onto the branch, passing.
- **O2.** Nothing else moves: fullscreen when not navigating stays as it is (banner not shown, not ruled); N2 changes nothing.
- **O3.** Add an S22 step for N1 (landscape fullscreen while navigating, both showing).

## Amendment P (2026-11-12T02:38Z, planner; from Sorrel's fourth review, pass with items)

No owner ruling in this amendment. Tests and the report only; no app behaviour changes. If a new test fails, stop and report: do not change app code or any other test to reach green.

- **P1.** Add a test through the real screen for the banner alone in fullscreen while navigating (the list search fails, the map's half loads, so no "Search failed" notice): portrait, short landscape and landscape tablet. The banner shows, placed where it is when both show; covers none of the compass strip, navigation card, rail or icon cluster; 80% fill, opaque text; long-presses below and beside reach the map. One-line revert, clean build log, edit-specific message, restore from a saved copy.
- **P2.** Show that a position assertion in `MapsTabSearchNavigatingFullscreenTest.kt` can fail: one revert that leaves the banner shown but in the wrong place (portrait "just below the card", landscape "just above the notice"), on its own `mtsf-*` branch. If no one-line edit does that, say so and name what you tried.
- **P3.** Report, by appending only: S22 step 19's "in fullscreen in landscape it is not shown at all" is superseded by N1; add an S22 step for the banner alone in fullscreen while navigating; add to step 22 a look at the slide into fullscreen in landscape (the banner may show twice for the slide's length; describe what is seen, no pass or fail).
- **P4.** Not in scope: the unused `fullscreen` parameter, the test name, any behaviour.


## Amendment Q (2026-11-13T05:25Z, planner; from the S22 check of head f025b3e16 and the owner's rulings on it)

The S22 check of build 1.0.5058+gc49b6ad1 is in RECORD -2204 ("S22 check of the final build"). Four findings came out of it. The owner, asked "Finding 1: leave the automatic retry as ruled, or change it?" and "Findings 2 to 4: do they go to Hazel before this build is carried into pre-main, or after?": **"1 change it"** and **"2 before"**. The S22 evidence holds private values and stays in the planner's scratch; it is not pushed and not sent. What was seen is described in words below.

### Q-A. The owner's rulings (2026-11-13)

- **QA1.** A failed automatic try. Asked "When an automatic try fails while the signal is up, what should happen next?", with the earlier rulings "At once (Recommended)" and "3 tries, then wait for me (Recommended)" quoted, the owner: **"3 tries, then wait for me"** (option text as shown: "As you ruled, now made true when a try fails: the app tries again by itself a few seconds later, up to 3 tries in all, then waits for \"Try again\".").
- **QA2.** The gap between automatic tries. Asked "How long should it wait between those automatic tries?", the owner: **"Growing: 3, then 10"** (option text as shown: "A fast second try, a slower third.").
- **QA3.** The largest font while navigating. Asked "What should it do when there is not room?", the owner: **"Notice keeps clear, words whole"** (option text as shown: "The notice narrows to stay clear of the cluster and never breaks inside a word; in landscape it moves below the navigation card when the gap beside it is too narrow.").

### Q-B. What was seen on the S22 (the planner's account, not owner rulings)

- **QB1 (owner's finding 1).** Search failed with the signal off. Signal back: the automatic try ran about 2 seconds after Wi-Fi came up and failed (the name lookup was not ready yet). Nothing tried again. The notice stayed for 45 seconds and more, signal up the whole time, until "Try again" was tapped by hand, which worked at once. Seen in 1 of 8 trials; the other 7 recovered by themselves.
- **QB2 (finding 2).** Font scale 2.0, navigating, portrait, not fullscreen: the icon cluster covers the end of "Try again". The uncovered part of the button works.
- **QB3 (finding 3).** Font scale 2.0, navigating, landscape: the notice is squeezed into a column about three letters wide between the icon cluster and the navigation card. Words break inside themselves and the notice runs off the bottom of the screen.
- **QB4 (finding 4).** Fullscreen while navigating, a coordinate search that fails: for about one frame "Request failed. Check your connection and try again." is drawn above "Trying again…". Seen twice in screenshot bursts 50 ms apart. Cause not diagnosed.

### Q-C. To the coder (the planner's reading of Q-A; where it goes past the owner's words it says so)

For each of QC1 to QC4: see it fail first, in a test through the real screen or the real entry point, for the reason expected; report if it will not fail. Then the fix, a one-line revert on its own `mtsf-*` branch with a clean build log and an edit-specific message, restored from a saved copy, and the forward change confirmed still present.

- **QC1.** From QA1 and QA2: signal returns > first automatic try at once (as today); that try fails > second automatic try 3 seconds later; that fails > third automatic try 10 seconds later; that fails > no more automatic tries, the notice waits for "Try again". Three automatic tries in all, as already capped. The planner's reading, not the owner's words: a timed try that falls due with the signal down does not run and waits for the signal's return as today; a manual "Try again" or a successful search cancels any pending timed try; the count resets where it resets today. If the code's existing cap or reset does not fit that reading, stop and report before building. Premise to re-check: `SearchAutoRetry.kt` and `SignalReturns.kt` on head f025b3e16, where the planner read that a failed automatic try causes no further try and that tries are set off only by `signalReturns(validated, 10_000)`. Time in tests is virtual; any waiting loop must end when the test ends (CLAUDE.md, the unstopped poll loop).
- **QC2.** From QA3, portrait: at font scale 2.0, navigating, the notice stays clear of the icon cluster wherever the cluster is, with all of "Try again" uncovered and no word broken inside. Text size does not change (CLAUDE.md: the window is never made by shrinking text). Proof: real touches sampled across the whole of "Try again", not its centre.
- **QC3.** From QA3, landscape: when the gap beside the navigation card is too narrow for the notice to keep its words whole, the notice goes below the navigation card. What "too narrow" is measured as is the coder's to propose in the Step-1-style report, before building. The notice never runs off the screen; if below the card there is not room either at font 2.0, report what happens there and do not pick silently. The old banner's place relative to the notice (N1) must still hold; check short landscape and landscape tablet at font 1.0 are unchanged.
- **QC4.** A bug, no ruling needed: no frame shows the "Request failed…" line above "Trying again…" when a coordinate search fails in fullscreen while navigating. Reproduce in a test first if it can be; if only a device shows it, say so, give the cause from the code with file and line, and say the fix is unverified off-device.
- **QC5.** Everything carried from A to P still holds: 80% fill over the map with opaque text, long-presses beside and below reach the map, nothing covers the compass strip, navigation card, rail or cluster. No test is weakened or skipped to reach green; an unrelated failure is reported, not touched.
- **QC6.** Report by appending to the completion report: what was done, what was verified and how, what was not; S22 steps for QC1 (signal back with the first try failing is hard to stage: say how it could be), QC2, QC3 and QC4. Push at every stopping point. Report before building if any premise above is wrong.

## Amendment R (2026-11-13T05:38Z, planner; the owner moves the notice into the search bar)

### R-A. The owner's rulings (2026-11-13)

- **RA1.** The owner, unprompted, after Amendment Q: **"The try again notice for search should go into the search bar itself, in place of the October text"**.
- **RA2.** Fullscreen, where the bar is off screen. Asked "Where does the notice go there?", with the standing ruling quoted, the owner: **"Under the card in fullscreen too"** (option text as shown: "As you ruled: in fullscreen the notice stays where it is today (under the navigation card, or at the top when not navigating). Only out of fullscreen does it go into the search bar.").
- **RA3.** Asked "With the notice in the search bar, what do you tap to try again?", the owner: **"A \"Try again\" button in the bar"** (option text as shown: "\"Search failed.\" sits where the October text is, with a \"Try again\" button at the bar's right. A tap on the rest of the bar opens the search panel as usual.").
- **RA4.** Asked "How much of the bar's line does the notice replace?", the owner: **"The whole line"** (option text as shown: "The notice replaces everything: species, month and distance. They return when the search works.").

### R-B. What the planner read on head f025b3e16 (premises to re-check)

- **RB1.** "The October text" is the bar's resting line from `activeSearchSummary` (`SearchSummaryText.kt:50-67`): species if one is chosen, the month, then the radius or "Search a location".
- **RB2.** The Maps tab's bar is not on screen in fullscreen (`ScaffoldMapTabCall.kt:332`, `visible = !isMapFullscreen()`).
- **RB3.** A tap on the bar focuses its field and opens the search panel (`onFieldFocused`, `ScaffoldMapTabCall.kt:357`). Clear shows at the bar's right while a search shows (RECORD -723).
- **RB4.** In landscape the bar's floor is measured from the resting line (`rememberLandscapeBarFloorPx`, `SearchSummaryText.kt:27`), so a different line changes that floor.

### R-C. To the coder (the planner's reading; where it goes past the owner's words it says so)

- **RC1.** Out of fullscreen, on the Maps tab: search fails > the bar's line reads "Search failed." in place of the whole resting line, with a "Try again" button at the bar's right; the floating notice is not shown. A tap on the rest of the bar opens the search panel as today. Search works > the resting line returns. A new path, not a conditional threaded through the bar's existing working code where that can be avoided.
- **RC2.** In fullscreen nothing moves: the notice is where it is on head f025b3e16, navigating or not.
- **RC3.** Every state the notice has today carries into the bar unchanged in meaning: "Trying again…" while a try runs; "Try again" shown, greyed out, with no signal (the owner: "Shown, greyed out"); the automatic tries of Q (QC1). Text size is the bar's own and does not shrink.
- **RC4.** Supersedes Q in part: QC2 and QC3 now apply in fullscreen only (out of fullscreen there is no floating notice to place). QA3, "Notice keeps clear, words whole", stands for fullscreen, and for the bar as far as it bears: no word broken, "Try again" wholly tappable at font 2.0. QC1, QC4, QC5 and QC6 stand.
- **RC5.** Report before building, with the Q report: (a) whether "Search failed." and "Try again" fit the bar in portrait and landscape at font 1.0 and 2.0, and what the landscape floor becomes; (b) what happens to Clear while the notice shows (both at the right end); (c) where the old banner goes out of fullscreen now that the notice it sat above (L1, N1) is in the bar; (d) what the bar shows while its field is focused and the search has failed; (e) which existing tests assert the floating notice out of fullscreen and so change with this ruling, by name: those are changed to the new ruling, not weakened, and listed. Each of (a) to (d) that is a real choice goes to the owner; do not pick.
- **RC6.** Proof as before: through the real screen; real touches sampled across the whole "Try again" button in the bar and on the rest of the bar (which must open the panel, not retry); 80% fill over the map with opaque text; one-line reverts on `mtsf-*` branches, clean build log, edit-specific message, restored from a saved copy. S22 steps appended to the report.
