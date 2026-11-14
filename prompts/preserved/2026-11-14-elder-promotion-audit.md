Elder Ford's report, as handed back to the planner on 2026-11-14 (UTC), filed whole by the planner. The owner: "Have Elder run an audit for the promotion then".

# UX audit for the promotion of pre-main into main (Elder Ford, 2026-11-14)

Asked for by the owner: "Have Elder run an audit for the promotion then". I advise; the owner decides. I changed nothing, wrote no file in the repository, ran no Gradle. Scratch copies only, under /mnt/work/scratch/elder-2026-11-13/.

## Verdict: PASS WITH ITEMS

I found no crash, no lost records, no wrong position, direction or time, nothing that reads as identification, no broken owner ruling and nothing unfinished in what this promotion adds. The items below are for the owner. Three matter most:

1. CI did not run on the exact commit being promoted (item 1).
2. Accessibility of the new failure state is unproven, and from the code two of the seven checks are not met (items 2 and 3).
3. Thirteen serious known bugs are listed as already live; this promotion neither adds nor fixes them, but the shipping rule's wording needs the owner's reading (item 4).

## 1. Mechanism prediction (written before I read the diff)

One behaviour changes: the Maps tab's handling of a loading or failed sightings search. Before, the whole tab was replaced; now the map stays and the bar or a notice carries the state. I predicted it would fail, if at all, at:
1. status text sharing the bar's line and shifting its neighbours;
2. the fullscreen notice as map chrome (80% fill, touches beside it);
3. the automatic retry's lifecycle (stale search, race with a tap, count lost on recreation);
4. accessibility (failure not announced, small target, greyed contrast);
5. state across tab changes;
6. a loading mark so quiet it reads as broken;
7. tests using semantic clicks over map chrome;
8. documents saying "fixed" or "live" ahead of the fact;
9. CI not at the exact head.

Outcome: 1, 2, 3 and 7 held up in the code. 4, 5, 8 and 9 produced items.

## 2. Baseline (confirmed by me with git and gh)

- origin/main = 137b1427c; origin/pre-main = f29db48a2; pre-main is 59 ahead, 0 behind. A dry-run fetch showed both current. The planner's premises on heads and counts are correct.
- Diff: 51 files, +6356 −92. The app code is MainActivity.kt (+11) and files under `ui/availability/` only. No manifest, resource, Gradle, version-catalog or workflow change. The rest is about 23 test files, seven documents and one CLAUDE.md bullet.
- The only app change is PR #516 (merge a3c92ef6f). `git diff --stat e6da77a61 origin/pre-main -- app` is empty, so the app tree at the head is identical to the build the S22 check used.
- No storage change, so no upgrade check is due.
- No new permission: `AndroidNetworkSignal` is already on main (used by `NetworkWait.kt` and `NoSignalTrailMap.kt`).
- Tests: no `@Ignore` or `assume` added, 0 removed assertion lines, 16 added `performTouchInput` against 6 `performClick`. I did not judge each test's claim one by one.
- CI run 43565525524: push on pre-main at a3c92ef6f, success, all eight jobs success. I did not read its JUnit XML or test count; "7,679 tests, 0 failures, 24 skipped" is the report's figure for run 43465970907 on e6da77a61.

## 3. Items

**Item 1. CI is not at the exact commit promoted.**
- Where: `gh run list --commit f29db48a2…` returns no runs.
- Evidence: the head is two `[skip ci]` commits past a3c92ef6f. `git diff --stat a3c92ef6f f29db48a2` shows only `docs/audits/README.md` (+1) and `docs/process/known-bugs.md` (±1).
- Why: the gate reads "CI green on pre-main, at the exact commit being promoted". The app is byte-identical to what CI ran, so the risk is nil, but the rule as written is not met.
- For the owner: accept documents-only, or run CI on the head.

**Item 2. A failure is probably not announced to a screen reader (Appendix B, check 6). Not seen on a phone.**
- Where: `MapsSearchFailure.kt` (`SearchFailedNotice`, `NoticeLine`) and `SearchBarFailure.kt`.
- Evidence: there is no live-region semantics anywhere in the diff, and a case-insensitive grep of `app/src/main` on pre-main finds none in the whole app, so the older banner has none either. No TalkBack pass is recorded in the completion report or RECORD -2204.
- What the user meets: a screen-reader user whose search fails hears nothing. The bar's line changes to "Search failed." silently, and they find it only by exploring.
- Why: QA philosophy Appendix B check 6; "Don't make me think".
- This is inferred from the code and is not new in kind, but the new state leans on it more: the bar is now the only place the failure shows out of fullscreen.

**Item 3. "Try again" in the bar is below 48 dp tall (Appendix B, check 2). Not measured by me.**
- Where: `SearchBarFailure.kt`, `TryAgainInBar`.
- Evidence: the minimum interactive size is switched off, the height is the field's height and the minimum width is 48 dp. Its own comment reads "no 48 dp minimum touch height, as Clear (RECORD -728)". The bar's pill minimum is 40 dp according to the existing comment in `AvailabilitySearchUi.kt`.
- The ruling it follows: SB1, "\"Try again\" takes Clear's place (Recommended)". I report this beside the ruling and do not resolve it; Clear has the same size on main today.
- The fullscreen notice's "Try again" is a standard text button and keeps the default minimum.

**Item 4. Known bugs this promotion ships with (A6).**
- Fixed by it: bug 30, the audit's one High.
- Added by it: no new row. The three S22 observations in item 6 are not in the list.
- Already on main and not serious: rows 3 to 21, 23 to 29, 43 to 45, 47, 48.
- Already on main and marked serious, all "from source, not reproduced, not seen on a phone":
  - lost records: 31, 32, 33, 37, 38, 39, 41;
  - wrong position: 34, 46;
  - wrong time: 35;
  - wrong direction: 36;
  - crash: 40, 42.
- The rule: "A bug may ship if it is listed, was already in the live app, and is not a crash, lost records, a wrong position, direction or time… A new bug of those kinds is a fail."
- My reading: none of the thirteen is new, this promotion does not touch their code, and holding it would leave them in users' hands while also withholding the bug 30 fix. I have not counted them as a fail. The sentence can be read more strictly, and that reading is the owner's.
- Bug 43 (an older search's results replacing a newer one's) is slightly more reachable now. A new search does not cancel a fetch still running (only Clear does, `AvailabilitySearchRuns.kt:265`), and automatic tries add fetches. This is traced from the code, not reproduced; the completion report's own correction 3 says a new search during an automatic try has no test.

**Item 5. The known-bugs "Where" column is stale (A7).**
- Where: `docs/process/known-bugs.md`, rows 14 to 19, 21, 24 and 27 to 29, which read "New (pre-main)" or "New (#5xx)".
- Evidence: main and pre-main differ in app code only by #516, so all of those are on main already. Whether they are in the Play build I could not determine.
- Why: the shipping rule reads this field.
- Row 30 is accurate: "Resolved on pre-main… Not in a release yet". It needs updating after the promotion.

**Item 6. Three behaviours the planner saw on the S22 and left unruled (the planner's claims; I saw no screenshots).**
- (a) Entering fullscreen with a failure showing, the bar's "Search failed." and the notice's longer line are both drawn for about 0.2 s. Polish.
- (b) On the List, Seasonal and Journal tabs with both halves failed, the pill shows its resting line with Clear and the banner "Request failed. Check your connection and try again." Nothing says the map's half is waiting, there is no "Try again" there, and an automatic try from another tab fetches the list only (report section WA6, from code). The same failure reads two different ways depending on the tab. Principle: "Don't make me think".
- (c) Changing font size in landscape fullscreen left fullscreen. Cause not looked for, and whether it is new is unknown. If it is not new, it belongs in the bug list.

**Item 7. Two fullscreen states are unruled, and one conflicts with a ruling's option text.**
- A list-only failure in fullscreen while not navigating shows nothing. `ScaffoldMapTabCall.kt`, `searchBannerPlaced`: "Fullscreen and not navigating is not ruled: not placed, not shown, as before."
- The owner's C6 option text said the failure is never silent. That was about the map's half, which does show. I report it with the ruling and do not resolve it.
- There is no loading line in fullscreen, because the bar is off screen. A retry there does show "Trying again…".

**Item 8. Automatic-try state is lost if Android recreates the screen.**
- Where: `AvailabilityScreen.kt`, about line 720, `remember { SearchAutoRetry() }`.
- What happens: a rotation keeps it (the Activity handles rotation in place). After a background kill the count restarts at zero and a held signal return is dropped.
- Effect: at most three extra tries, or one missed until the user taps. It is disclosed in the class comment and is untested. Minor.

**Item 9. Copy.**
- The words are honest and each has a next step: "Search failed." / "Failed." / "Trying again…" / "Trying…" / "Try again", and in fullscreen "Search failed. Check your connection."
- At large font the bar shows "Try again" alone, with no word saying what failed. This is ruled: TB2, "Button only when nothing fits (Recommended)".
- "Trying…" is labelled in the code as "the planner's reading" of TB1/TB2, not the owner's words. For the owner to confirm.
- `sightingsErrorMessage = "Couldn't load sightings for the map."` is now only a flag. Its single reader is `MapsSearchFailure.kt:89`, a null check, so it is never shown. Harmless.

**Item 10. Process, for the record.**
- #516 went into pre-main directly, on the owner's "Merge 516 into pre main first". The dispatch's finish line and "Urgent, from main (Recommended)" described a route through main; the owner's later word governs.
- The planner disclosed that it missed the pre-merge backup bundle and the silenced-test read before merging, and did both afterwards.
- Sorrel's eight reviews of #516 exist only as summaries inside RECORD -2204. No review file is preserved, and no review covers the final S22 results.
- The release runbook's step 1 (signed build from pre-main as "the final gate, before the promotion") has not been run: `gh run list` for that workflow is empty. The runbook itself says the environment setting it needs is still open. Whether it is required for this promotion is the owner's call.
- `SIGNAL_RETURN_MIN_INTERVAL_MS` (10 s between signal-return tries) is "the coder's call… for the planner to accept or change". I found no owner ruling on it.

## 4. The changed behaviour, walked from the code

The S22 results are the planner's. I marked what was not seen on a phone.

**A. Search fails, bar showing.**
- Search, then failure: the map, bar and tabs stay.
- The bar's whole resting line becomes "Search failed." (RA4, "The whole line") and "Try again" takes Clear's place (SB1).
- Back behaves as on the map normally; the tab is no longer replaced.
- Changing info: the line is chosen by measured room, never by shrinking text. "Try again" keeps its place while the line switches to "Trying again…", so nothing beside it shifts. Good.
- 80% chrome: the bar's fill is unchanged. `MapChromeOverMapTest` was edited (±4); I did not re-derive it.
- Seen on the S22 by the planner.

**B. Tap "Try again".**
- The button greys (G2, "Shown, greyed out") and the line reads "Trying again…".
- On success the bar returns to its resting line with Clear; on failure it returns to "Search failed."
- A second tap during a try starts nothing.
- The contrast of the greyed word on the bar and on the 80% error fill was not measured by anyone (Appendix B check 1). Sorrel flagged it for the owner's eye.

**C. Signal returns.**
- Tried at once (C4), then 3 s and 10 s later (QA2, "Growing: 3, then 10"), three in all (E3), then it waits.
- Only with Forager resumed (E2).
- A new search, a tap or a success restarts the count.
- A failure while the network stays validated gets no automatic try (C7, "Notice stays, no auto retry (Recommended)").
- The logic in `SearchAutoRetry.kt` matches the rulings as I read them. The timed wait is cancelled by any state change, and I found no leak.
- A real weak signal: not seen on a phone.

**D. Fullscreen.**
- The notice "Search failed. Check your connection." with "Try again" sits at the top of the map (C6), or under the navigation card while navigating in portrait (L1).
- "Try again" drops below the words when a word would break (TB3).
- Fill: error container through `mapChromeFill`, content opaque. This meets the 80% rule.
- Both lines are laid out at once, so the notice does not change size between "failed" and "trying". Good.
- It is composed only while it shows, and the cluster is kept below its measured height.
- At font 2.0 while navigating in portrait, an earlier finding had the cluster covering the end of "Try again". Whether the last S22 check re-saw this at 2.0 is not stated.

**E. Loading.**
- A 2 dp moving line on the bar's bottom edge (C2). It takes no touches, and the map stays.
- It is quiet by ruling: "Nothing in the bar moves".

**F. User-set state.**
- Nothing the user set is reset by the change. Cluster position and fullscreen are untouched by the code I read.

**Appendix B summary**

| Check | State |
|---|---|
| 1. Contrast | Not measured |
| 2. 48 dp targets | Not met in the bar, per SB1 and Clear (item 3) |
| 3. Spoken labels | Text buttons carry their words |
| 4. Largest font | Handled by the ruled ladder; seen at 2.0 by the planner for the bar |
| 5. One-finger alternative | Yes, a tap |
| 6. Status messages reach a screen reader | Not met from the code (item 2) |
| 7. Portrait and landscape | Covered in tests and on the S22; portrait tablet not seen |

## 5. Documents and public text (A7)

- `docs/audits/README.md`, new rows: consistent with what I verified, except that I did not verify the test count.
- `docs/process/known-bugs.md`: see item 5.
- `docs/process/planner-profile.md` and CLAUDE.md, the two exceptions: quoted from the owner ("Both stay, written as exceptions"). The workflow files they name exist on both branches.
- `docs/process/release-runbook.md`: its workflow names exist. Its claims about GitHub environment settings and the Play Console cannot be checked from the repository.
- `docs/plans/main-audit-2026-11-10-follow-up.md`: a plan; it quotes the two H1 rulings correctly.
- `docs/audits/2026-11-10-main-engineering-audit.md`: I did not read it line by line, and I did not run the private-value scan on it or on the completion report (I was told not to touch the private patterns). The planner should confirm that scan before main carries them.
- Privacy policy and store text are not in this repository. Nothing in this change alters what data leaves the phone: the same search requests, sent more often. Bug 48 (unrounded search coordinates in the phone's log against the policy's "a coarse map position") is listed and already live; each automatic try writes that line again. I did not read the log call myself.

## 6. Gaps: what I did not or could not check

- No phone, no screenshots, no screen reader. Everything "seen on the S22" is the planner's claim.
- Not seen on a phone by anyone:
  - the map's half recovering first, then the list failing inside one try;
  - a new search during an automatic try (also untested in CI);
  - a failing search from bar coordinates;
  - the K1 stale-failure case;
  - the banner alone at large font;
  - a portrait tablet;
  - a real weak signal;
  - any contrast measurement.
- The completion report (1,818 lines): I read its structure, Addendum 12 and its corrections in full, and the rest by section headings and RECORD -2204's account.
- The diff of `app/src/main` I read in full. The test files I checked only by counts and patterns.
- The JUnit XML of run 43565525524 was not read.
