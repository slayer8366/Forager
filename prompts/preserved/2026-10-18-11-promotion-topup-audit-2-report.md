# Elder Ford: second top-up audit of the promotion, main `969ecf0a` → pre-main `0b93dde9` (range `d942e022..0b93dde9`)

Filed by the planner as received (Forager RECORD -1349). Elder ran on Fable, read-only, so nothing was written to any repository and no Gradle or emulator was run. Temporary directories under `/tmp/elder-*` hold the downloaded CI logs and JUnit XML, outside every repository.

**Verdict for the whole promotion: Pass with items.** I found nothing that risks a user's records, safety or trust. There are seven items for the owner to ship or hold, and none of them is a block. Two are about the record rather than the app: the CI red at the promoted commit, and the known-bugs rows.

## Baseline, confirmed

- `git ls-remote`: main `969ecf0a`, pre-main `0b93dde9`. The first-parent log of `d942e022..origin/pre-main` shows the five merges #259, #260, #261, #262 and #263, 30 commits in all.
- **The range:** 27 files, +2518/−47. That is 9 app files, 13 test files, 4 completion reports, the audits index (7 rows added, none dropped) and known-bugs (row 7).
- **`.github/workflows/ci.yml` is untouched** (0 diff lines), so the skip allowlist can't have grown.
- **CI at `0b93dde9`, push run 39192861476.** I read it from the API, not from the planner's message.
  - **Attempt 1** (started 03:03Z): lint and release-compile green; **Build/test red**.
    - The failure: `MushroomLogViewModelWiringTest > an album photo deleted through the Journal's ViewModel leaves the Maps tab's records FAILED`, `AssertionError at MushroomLogViewModelWiringTest.kt:98`. The log reads "5149 tests completed, 1 failed, 24 skipped".
    - JUnit XML artifact 12161500265: 691 files, 5,149 tests, 1 failure, 24 skipped, the one failure in that class.
  - **Attempt 2** (the planner's re-run, -1347), finished 03:33Z: **all three jobs green**. Artifact 12161577471: 691 files, **5,149 tests, 0 failures, 0 errors, 24 skipped**.
  - `0b93dde9^{tree}` = `0f13928b^{tree}` = `1c88bf63…` (read with `git rev-parse`), and run 39191645892 at `0f13928b` is green. So the promoted tree has two green full runs and one red run on the parked test. See item 1.
- **Tests in the range:** 0 `@Test` removed and 55 added (grep of the test diff), with no `@Ignore` and no `assume`. Three tests were replaced in place, each quoting its old assertion:
  - `LandscapeEntryReportTest`, the tablet case: the old name and both old `assertEquals` are quoted in the KDoc (read).
  - `LandscapeOfflinePickerTest:246-249`: "Was `assertEquals("the map starts where the controls side does…`" (read).
  - `AvailabilityScreenSettingsPanelTest` (`src/testDebug`): the old name and `assertEquals(Basemap.OPEN_TOPO_MAP, …)` are quoted (read).
  - The -1244 slide test is unchanged overall across #261 and #262. There are no `-` lines for it in the range diff, and its "row follows the pill" claim stands, which matches -1333.
- **Read from source:**
  - RECORD -1309 to -1346, including -1329 and -1331;
  - dispatches 2026-10-18-07, -08 (Amendments 1 to 3) and -10 (Amendment 1);
  - the four completion reports on pre-main;
  - `auditor-profile.md`, `qa-philosophy.md` and CLAUDE.md;
  - the whole `app/src/main` diff of the range.

## Mechanism prediction (written before the diff) and what the diff showed

**Predicted:**
- one constant swap at the picker, and the logo's gravity and margins following the "i" in both placements;
- a single `reversed` on the rail list, and `scrimColor = Color.Transparent`;
- a `journalSearchHeld` gate on the Journal tab, plus a helper fixing the map's slot under the pill's reserved height;
- an `sw ≥ 600` gate raising 640 to 960, with square maps and a controls-width floor.

**Named failure points:**
- the short-landscape logo at bottom-left;
- tap-outside being undiscoverable without a dim;
- the reserved room reaching screens it shouldn't, or a large-font pill overrunning it;
- real insets shrinking the tablet square.

**Every shape held:**
- **Picker:** `Basemap.OSM_STANDARD` at `AvailabilityOfflineMapsUi.kt:214`.
- **Logo:** `logoBesideAttribution` (`SightingsMap.kt:1524-1530`), called from the one attribution effect (`:1502-1505`), with the END or START side chosen at `:1525-1526`.
- **Rail:** `RAIL_ORDER_TOP_TO_BOTTOM = CompactTab.entries.reversed()` (`AvailabilityNavigationUi.kt:1145`), the only order source (`:123`).
- **Scrim:** `scrimColor = Color.Transparent` at `AvailabilityScreen.kt:1294`. Hazel's reason for not using `Unspecified` (it drops tap-to-close) is confirmed by revert R3.
- **Journal:**
  - the gate `!(journalSearchHeld && compactTab() == CompactTab.JOURNAL)` is at `AvailabilityCompactScaffold.kt:1083`;
  - `JournalSearchPillRoom.kt` holds `measuresJournalSearchPill` (`:364`) and `journalSearchGap` (`:374-387`);
  - the room is provided only when `railBeside` (`AvailabilityCompactScaffold.kt:1100`), and read only by the Offline maps map (`AvailabilityOfflineMapsUi.kt:240-246`).
  - The large-font risk I named turns out to be a search *notice*, not a font. `coerceAtLeast(0)` at `:382` means a pill taller than its room shrinks the map by the excess. Hazel discloses this as an error-state case.
- **Tablet:**
  - `isLargeScreenWindow()` (`ShortWindow.kt:18-19`, smallest width ≥ 600 dp);
  - `TABLET_JOURNAL_CONTENT_MAX_WIDTH = 960.dp`;
  - `squareFromHeight(OFFLINE_CONTROLS_MIN_WIDTH)` (`AvailabilityOfflineMapsUi.kt:256-266`);
  - the entry map's `squareMap` (`CartographyEntryReportScreen.kt:462-463`).
- **Better than predicted:**
  - The real-inset regime, where the height decides the square, *is* tested, including the hold through the reveal: `TabletJournalLayoutTest:309-310`, "landscape foldable where the height decides, the square is as tall as the room under the line and holds through the reveal" (read).
  - The arithmetic holds by construction. The gap is `(fullHeight − shown) + 8`, so the map's height is the same whether the pill is hidden or shown (`JournalSearchPillRoom.kt:382-383`).

## Items (owner ships or holds each)

**1. The promoted commit's CI was red once, on the parked test, and the record understates how often that test fails tonight.**

- Attempt 1 at `0b93dde9` failed only `MushroomLogViewModelWiringTest` (above). The re-run is green, and the tree is identical to a green PR run. The code is fine; this item is about the gate's bookkeeping.
- **Tonight's rate:** of 27 completed full runs since 19:00Z (push and pull_request, `gh run list`), that class failed 3:
  - 39170164409 ("a find deleted…", `:80`);
  - 39172914080 ("a find deleted…", `:80`);
  - 39192861476 attempt 1 ("an album photo deleted…", `:98`).

  That is about 11%, against row 2's "about 6%". The planner's "3 of roughly 12" has the same numerator; my denominator counts every completed full run.
- **An unrecorded red:** 39172914080 is PR #261's final head `ed35e8b2`. -1325 says that run "was still in progress at the merge"; it finished red on this class. #261 is reverted, so no user meets anything from it, but the merge happened before its head's run was read.
- **Principle:** CI green at the exact commit (QA philosophy §3).
- **Recommendation:** record the red-then-green against `0b93dde9`, with the tree identity, and update row 2's rate.

**2. The landscape rail reads List at the bottom. That is an owner ruling, built as ruled; it departs from the convention most apps follow, and a screen reader's order flips with the phone.**

- **The path:** turn the phone → the rail on the right → Tools at the top down to List at the bottom, in both rotations.
  - Seen on the S22 at one rotation (-1336 step 4). The other rotation was not run on a phone.
  - Both rotations are held by `NavigationRailOrderTest` at ROTATION_90 and 270 (read).
- **Prior art:**
  - The convention I know is that a rail lists the same destinations in the same order as the bottom bar, read top to bottom. That is **background knowledge, not re-read**. The Material 3 and Material 2 guideline pages render only by script and returned their titles alone, and Android's developer page on the rail states no order rule (fetched, read).
  - The owner's model, the bar rotated with the phone, matches what Android's own three-button bar does in landscape on phones. That is also background knowledge, unverified.
- **TalkBack:**
  - Composition order and drawn order agree, so a screen reader should read Tools first and List last in landscape, and List first in portrait (`AvailabilityNavigationUi.kt:139-142`, read; the test "the rail's semantics order is the screen's", read).
  - Reading order following the drawn order is the accessibility convention. Appendix B item 3 is met; WCAG 1.3.2 is background knowledge.
  - The owner accepted this (-1321). **It has not been heard on a phone.**
- **Edge case:** in a rail too short for five items, List is now the one squeezed off the bottom (`:142-143`). No tested window is that short.
- **Principle:** "measured against what people already know." Not a bug. I list it so the release note or tester message can say one line about it.

**3. The Tools drawer opens over an undimmed app. Tap-outside and Back still close it, but nothing tells the user the rest of the app is inert.**

- **The path:** Tools → the drawer slides in → the app behind keeps its colours.
  - On the S22 (-1336 step 5), a tap outside closed it. Back was not tried on a phone.
  - Back is held by `ToolsDrawerNoTintTest`, "Back closes the Tools drawer", in both orientations (read).
- **What a user may meet:**
  - The rail or bottom bar behind the drawer looks live. A tap on Maps there closes the drawer and does not open Maps, so a second tap is needed.
  - The map behind can't be panned while the drawer is open, and nothing shows why.
  - With Material's default dim, both of these were signalled.
- **Principle:** "don't make me think", because this is a state that could read as a dead tap. The owner ruled the dim out ("non-sleek", -1313), so this is recorded, not raised. One tap is the whole cost.
- **Mechanism confirmed:** the transparent scrim keeps its full-screen tap target and its "Close drawer" semantics (`AvailabilityScreen.kt:1290-1294`, from Hazel's reading of material3 `Scrim.kt`; R3 shows `Unspecified` would break it). I did not read `Scrim.kt` myself, so this is inferred from R3's result.

**4. The Journal's landscape reveal: the map holds still, the switch line slides, and the right-hand panel moves (row 7). Row 7 is labelled "New"; in my reading the panel moving is already in the live build.**

- **The path:** Journal → Records → Offline maps, landscape → 🔍 → the bar slides in at the top, the switch line slides down under it, and the map's top edge does not move.
  - Seen on the S22 (-1336 step 6) and on the S26 (-1338, on build 3484).
  - The panel (Name, Radius) moves down with the line, pushing "Downloaded Maps" lower. It sits in a scrolling column (`OFFLINE_PICKER_CONTROLS_SCROLL_TAG`), so it stays reachable.
- **On main today:**
  - The pill is composed with a plain `if` and no slide (`AvailabilityCompactScaffold.kt:998` on main, read), and the Journal content sits in the weighted box under it. So revealing search makes everything below, panel and map, jump down.
  - The live build therefore already has the panel movement (as a jump) *and* the map resizing.
  - Row 7's "New (…whether the live build does it was not checked)" should read "already live, in a cruder form". That matters only for A6's "may ship" test, which it passes either way.
- **A related reading:** -1329 cites main's `:1271` for the open-entry hide on every tab. Main's `:1271` carries a Journal-tab gate on a different slot; the bare `!journalSearchHeld` that hides the pill on every tab is main's `:998`. The conclusion in -1329 is right; the line number is not.
- **The entry map's slide-in stays** (-1334; `CartographyEntryReportScreen`'s `GrowIn` is untouched in the range), and the map is then held still by the -1333 tests (read). Nothing here reads as broken to me; the slide reads as opening.
- **Phones after #263: not seen.**
  - R6 (the gate always true) failed all 10 phone cases, which shows the gate is what keeps phones unchanged.
  - The S22 and S26 passes are on `e431eba9`, one PR earlier. The only phone code added since then is the gate.

**5. The tablet layout: nothing seen on a tablet yet. What can be read looks intentional, with two things to look at.**

- **The path** (inferred from the code and Robolectric figures, not observed):
  - Journal in landscape → a 960 dp centred column (`AvailabilityCompactScaffold.kt:1060-1065`).
  - Offline maps → a square map on one side, with the controls beside it, at least 240 dp wide (`AvailabilityOfflineMapsUi.kt:241-242`, `:272`).
  - An entry → a square map with the details beside it (`CartographyEntryReportScreen.kt:462-463`).
  - In portrait the square is full width, 824 dp, with the info under it in a scrolling column (`AvailabilityOfflineMapsUi.kt:360`, `verticalScroll`, read).
- **Real insets:**
  - Under Robolectric the width decided the square (720 = 960 − 240).
  - On the tablet, the status and navigation bars take height, so the height should decide and the controls widen past 240 dp.
  - That regime is tested at the foldable size, reveal included (`TabletJournalLayoutTest:309`), so the change of regime shouldn't surprise. This is inferred; the owner's tablet check is the evidence.
- **To look at on the tablet:**
  - **(a) The search pill's width in the 960 column.** The pill is sized to Maps' pill (`landscapeTabPillFit`, previous audit) and sits outside the cap. Hazel says it was "not touched or re-checked" (tablet report, Not verified). A narrow pill over a wide top line could read as unfinished.
  - **(b) Space under the map.** When the width decides, the square sits at the top of its slot (`squareFromHeight`, `:265`). If the row is taller than the square, there is empty space under the map, beside the controls. In the measured tablet case the two matched, and with real insets the height decides, so there is none. An observation, not a gate.
- **Principle:** "polish is even", and the owner's "appear polished and intentional" (-1338). The owner's own choices (A, "Match content") set the form; the remaining questions are the two above.

**6. The MapLibre logo beside the "i": in the short-landscape case it sits at the bottom-left, and nobody has answered that question yet.**

- **The path:** every map → the logo just inboard of the "i", at the bottom-right, on one line.
  - Seen on the S22 (-1336 step 3): Street and Topo in portrait, and the landscape picker.
  - On Topo in portrait it overlaps the end of the caption, as accepted in -1315.
- **The Maps tab in a short landscape window** (the -954 case, `attributionBesideCredit`): the credit strip, then the "i", then the logo, at the **bottom-left** (`logoBesideAttribution`, `:1525-1526`; Hazel's table).
  - -1319 lists this as owner question 1, and -1336 step 3 says "the short-landscape Maps tab case was not looked at". Nothing since answers it.
  - The owner should see it once and say whether that is what "on the right" meant. Not a bug: the logo follows the "i", as the planner asked.
- **Two estimates, not seen** (both from Hazel's font-metric arithmetic, marked as estimates):
  - On Topo at night in portrait, the caption probably wraps to two lines, and the "i", and now the logo, sit over its second line. That is pre-existing for the "i".
  - While navigating at ROTATION_90 on Topo, the navigation card may cover the end of the logo.
- **What the test holds** is the settings handed to MapLibre, not the drawn logo (`LogoBesideAttributionTest`, read), because a real MapView can't run under Robolectric. The S22 pass is what shows the `setLogoGravity` and `setLogoMargins` calls work. I did not read MapLibre's `UiSettings`.
- **Licence:** Hazel read the BSD 2-Clause licence and the `UiSettings` docs and found no placement requirement. The website and trademark guidance are unread. I did not re-read this, so it is marked inferred.

**7. The known-bugs list for this promotion (A6): row 7 is added; there are three things I would add or correct.**

- **Row 7:** the label correction, as in item 4.
- **Row 2:** the rate, as in item 1.
- **Not listed,** and in my reading they should be, as accepted cosmetic defects a user meets:
  - the logo over the end of the Topo caption in portrait (-1315, seen on the S22);
  - the probable two-line Topo night caption, with the "i" and logo on its second line (an estimate, not seen).
- The short-landscape logo position is a question, not a bug, until the owner answers it.
- **A6's "may ship" test:** no listed row is a crash, lost records, a wrong position, direction or time, or identification. Pass.

## Not seen on a phone

- Anything from #263 on a tablet (the owner's check comes next). Phones after #263 are not seen either; they are held by the gate and R6.
- The other landscape rotation for the rail order; Back closing the drawer; what TalkBack actually reads. These S22 steps were not run (-1336).
- The short-landscape logo, the Topo night caption, and the navigation card over the logo.
- Step 8 of -1336 (every map opens): the entry, Records, Cartography and location-picker maps were not opened on the cleared S22. The logo and attribution effect runs on all of them (`SightingsMap.kt` is shared), so any failure there would be shared too, and the two maps that were opened worked.
- The S26 pass is on build 3484, which is `e431eba9` (owner, -1338): the state before #263.
- The largest font size, on any of the range's screens.

## Walked as step paths, clean

- **The Offline maps picker on Street:** Records → Offline maps → Street in both orientations while Maps is on Topographical → back to Maps, which is still on Topographical (S22, -1336 steps 1 and 2).
  - It is a hard-wired constant, not the Layers choice and not `MapMode.DEFAULT` (`:214`).
  - The test whose premise is Topographical covers "follows Layers" (read).
- **Open an entry, then List, then Seasonal:** both show the search bar. Back on the Journal, the entry shows no 🔍 and the switch line is at the top (S22, -1336 step 7; four -1330 tests, read).
  - The -1328 bisect was wrong for the reason -1329 gives: the check never saw an open entry.
  - The corrected cause is on main too (`:998`).
- **Back:** Back closes the drawer (test), and Back out of an entry is unchanged (-1330). Nothing in the range adds a screen.
- **The 80% rule, the camera rule, no identification, no dev tools:** untouched by this range. The drawer sheet itself is unchanged (80% over Maps, solid elsewhere).

## What a user meets in a production build from main after this promotion, on top of the previous two audits' lists

- In landscape the rail reads List at the bottom up to Tools at the top, both ways round.
- The Tools drawer opens with the app behind it at full colour. A tap outside or Back closes it.
- The Offline maps picker map is always the street map.
- The MapLibre logo sits beside the "i" at the bottom-right. On the Maps tab in a short landscape window it sits at the bottom-left, beside the credit.
- With a Journal entry open, List and Seasonal keep their search bar.
- On the Journal in landscape, tapping 🔍 slides the bar in and the Entries | Records line down. A map under it never moves.
- On a tablet, the Journal is a wide 960 dp column, and its maps are squares with their information beside them.

## Could not determine

- Everything under "Not seen on a phone".
- Whether material3's `Scrim` keeps its tap target when its colour is transparent. That is taken from Hazel's reading and R3, not read by me.
- Prior art for the rail order from the Material guidelines themselves, which can't be fetched here (script-rendered pages).
- The exact width of the Topo night caption on either phone.

## Premises in the dispatch I checked

- **"CI on pre-main at 0b93dde9 is a push run that started around 03:05Z":** true (03:03Z). It did not simply pass: attempt 1 was red on row 2's test and attempt 2 green (item 1).
- **The device results** match the record:
  - "S22 on the final build before #263: steps 1 to 7 pass, step 8 partial" matches -1336;
  - "S26 passed on build 3484" matches -1338, by the owner's account;
  - "Tablet passed before #263" matches -1337. The build isn't stated there, but the photos show tonight's changes.
- **"Several tests were replaced under owner rulings: each should quote its old assertion":** true for all three (above). The count is 0 removed and 55 added; the three are in-place renames.
- **The planner's message during the audit** ("1 of 5,149… row 2… tree identical… re-run"): I verified each part independently, above. The "3 of roughly 12" denominator is the one figure I count differently (27).

## Beyond scope

None. Nothing was written to any repository, and no code, PR, merge, Gradle or emulator was involved.
