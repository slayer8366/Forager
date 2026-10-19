# Elder Ford: third top-up audit of the promotion, main `969ecf0a` → pre-main `21dc4470` (range `0b93dde9..21dc4470`)

Filed by the planner as received (Forager RECORD -1357). Elder ran on Fable, read-only, with nothing written to any repository and no Gradle or emulator. Downloaded CI logs went to `/tmp/elder-3/`, outside every repository.

**Verdict for the whole promotion: Pass with items.** Nothing in the range risks a user's records, safety or trust. There are four items, none a block:
- two are about the record;
- one is the Seasonal reading width the owner has already chosen;
- one is that the List and Seasonal tests have never been seen red.

## Baseline, confirmed
- **Heads:** `git ls-remote` gives main `969ecf0a` and pre-main `21dc4470`. `git log 0b93dde9..21dc4470` shows eight commits: the four merges #264 (`e0975dec`), #265 (`019c339d`), #266 (`b05db019`) and #267 (`21dc4470`), and one commit each.
- **The range:** 4 files, +200/−5:
  - `AvailabilityCompactScaffold.kt` (+17/−3);
  - `AvailabilitySeasonalUi.kt` (+7/−1);
  - `TabletJournalLayoutTest.kt` (+171);
  - `docs/process/known-bugs.md` (+4/−2).
  
  No other app file and no `docs/audits` change.
- **`.github/`:** untouched, 0 diff lines.
- **Tests:** 0 `@Test` removed and 11 added, with no `@Ignore` and no `assume`. Each of the three in-range replacements quotes the assertion it replaced.
- **CI, read from the API:**
  - #264's PR run 39195879726 and the e0975dec push run 39197987289 are green.
  - 39198777067 (019c339d) was cancelled.
  - 39199565610 (b05db019) failed: 2 Seasonal tests, on a wrong premise.
  - **39201526634 at `21dc4470`: all three jobs green.**
    - The tests: 5,160, with 0 failures, 0 errors and 24 skipped, across 691 suites.
    - `TabletJournalLayoutTest` 29/0/0/0. `MushroomLogViewModelWiringTest` 5/0/0/0.
- **The owner's tablet build matches the promoted app code.** `git diff b05db019 21dc4470 -- app/src/main` is empty, and `git rev-list --count b05db019` is 3497, which matches 1.0.3497+gb05db019.

## Mechanism
- **The pill:** `tabLandscapePillPlace = if (railBeside && largeScreen) Modifier.fillMaxWidth().then(readableContentCap) else if (railBeside) {…unchanged…}` (`AvailabilityCompactScaffold.kt:1063-1066`). The panel reuses it at `:1829`.
- **The column:** `contentCapWidth = if (largeScreen) 960 else 640` (`:1009`), applied only when `railBeside` (`:1010-1016`).
- **Seasonal:** `seasonalMaxWidth = if (isLargeScreenWindow() && isLandscapeWindow()) 960 else 640` (`AvailabilitySeasonalUi.kt:82`).
- **The gate holds on phones, by reading:**
  - `largeScreen` is `smallestScreenWidthDp >= 600` (`ShortWindow.kt:56-57`), and the S22 Ultra's is 384 dp.
  - Every changed line is under that gate or under `railBeside`.
  - The phone branch has no `-` lines.
  - Three phone controls hold the bar at the left on Journal, List and Seasonal.
  - Not seen on a phone.
- **The two Seasonal gates agree:** inner and outer both give 960 beside the rail and 640 otherwise (inferred from the two expressions).

## Items (owner ships or holds each)
**1. The List and Seasonal tests have never been seen red for the behaviour they hold.**
- #265 and #266 had no revert check, by the owner's override (-1354, -1355).
- The only red these tests produced was on the test's own wrong premise. They share their mechanism with the Journal tests, which were revert-checked, so they are expected to bite, but that is inferred.
- Not a block: the owner's tablet look on b05db019 is the evidence for the behaviour itself.
- **Recommendation:** a revert check after the release.

**2. Seasonal's paragraphs run about 928 dp wide on the tablet in landscape.**
- The reason for the old 640 cap is still in the code (`AvailabilitySeasonalUi.kt:71-75`). The owner's override is recorded at `:79-81`.
- Recorded, not raised.
- Not confirmed seen: the record doesn't say whether the owner opened Seasonal during the b05db019 look.

**3. On the tablet in portrait, the three tabs are not the same width; the range changes nothing there.**
- Journal and List take the full width, and Seasonal keeps its 640 cap.
- This is pre-existing and inferred, not seen. "Make season and list the same" is answered for landscape only.

**4. Record: no completion report or audits-index row landed for #264 to #267.**
- RECORD -1352 to -1355 carry the history, but `docs/audits/` has nothing for them.
- **Recommendation:** file Hazel's reports after the release.

## Known-bugs (A6)
- The four edits match the second audit's item 7 exactly: row 2's rate, row 7's Where, and new rows 8 and 9.
- Nothing in the range adds a bug.
- **A6's "may ship" test:** pass.

## What a user meets after this promotion
- **On a tablet in landscape:** List, Seasonal and the Journal are each one centred 960 dp column. The search bar and its open panel sit on that column's edges, clear of the screen edge.
- **Seasonal on a tablet** is wider than before.
- **Phones:** unchanged.

## Not seen
- Any phone, for anything in this range.
- List and Seasonal on the tablet after #266, unless the owner's look included them.
- Both landscape rotations for the bar.
- The largest font.

## Could not determine
- Which tabs the owner opened during the b05db019 look.
- Compose's semantics merging for `SEASONAL_CONTENT_TAG` is inferred. The replacement assertion is sound either way.

## Beyond scope
None.
