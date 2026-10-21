# Elder Ford's audit: promotion of UI split Phase 2 (main 02a544cd → pre-main 02fe89f1)

Filed by the planner (Rowan Waymark), 2026-10-21. Elder ran on Fable; the coders ran on Opus. The audit was read-only: no repository written, no phone touched, no Gradle. This file records the audit as Elder delivered it by message, in summary, with Elder's citations.

**Range:** origin/main 02a544cd to origin/pre-main 02fe89f1.
- 136 commits and 56 first-parent merges (#293 to #354).
- 173 files, +14,627 / −8,581.
- `.github/` unchanged; `res/` unchanged; no Gradle, version catalog or manifest change.

**Verdict: Pass with items (promote with conditions).** Nothing found risks a user's records, safety or trust.

## What Elder verified
1. **CI at the exact head.** Push run 39562393147 on 02fe89f1 is green on all three jobs (observed).
2. **No test silenced.** No `@Ignore`, `assume` or `@Disabled` was added, no test was deleted, and the CI skip allowlist is unchanged (read).
3. **Every removed production line has a counterpart.** A multiset check covered every removed non-comment line in `app/src/main` against the added lines. The 641 lines without a direct counterpart are all renames, parameter lists turned into bundle fields, or launch and update heads whose bodies moved. No condition, default or literal was dropped (read, every one).
4. **The words on screen are unchanged.** The only new literals are #337's log lines (read).
5. **Chrome, touch and inset counts are identical:** `MAP_CHROME_OVER_MAP_ALPHA` 26/26, `pointerInput` 26/26, `IntrinsicSize` 3/3, `safeDrawing` 12/12, `windowInsets` 12/12, `contentWindowInsets` 4/4, `clickable` 97/97, `Surface(` 30/30 (read). A matching count rules out a dropped modifier, not a moved one.
6. **Back order.** The seven BackHandlers are in the same order with the same conditions in `AvailabilityScreenBackHandlers.kt:62-152` (read).
7. **No dev tools.** The only added logging is #337's three `Log.i` lines. `.claude/skills/` holds only `modular-code` (read).
8. **The three behaviour changes match the owner's rulings:**
   - #336 (-1468, -1470);
   - #337 (-1469, -1471, -1473): Maps tab only, with the `beforeLocate` gate;
   - #340 (-1475, -1476): the before and after screenshots were observed.
9. **Map chrome sits at 80% over the map** (`pr348-shots/2-maps-open.png`). The landscape layout matches the baseline apart from live readings (observed).
10. **The night cold-start flash** was observed (`pr346-shots/a346r1-cold-2.png`).

## Items
1. **Condition: the known-bugs rows from -1479, -1485 and -1489 hadn't landed.** `known-bugs.md` was identical between main and pre-main. Add the rows before or with the promotion, or the owner records that they ship unlisted.
   - **Done by the planner:** PR #356, rows 10-13, merged into pre-main at f0ca368a. Row 13 adds the spring-back edges (audit item 4).
2. **Whether those bugs are live or new is partly inferred.**
   - (a) The Back overlap is pre-existing, from the code.
   - (b) The night flash was seen only on pre-main builds. 3500 was not checked.
   - (c) The first-recording notice: the recording service is unchanged since 3500, so it is inferred live.

   The "Where" fields for (b) and (c) want a check against 3500.
3. **Not seen on a phone,** by the record's own account:
   - forecast opacity and the legend (no data on any build);
   - the background-run, network-fixes, settings-reset and backup notices;
   - any snackbar;
   - adding a photo;
   - navigation with the plain-map nudge;
   - the S26 and the tablet;
   - the owner's owed 2.3 walk items (-1465).

   The owner's last S26 walk was on build 3658, before #336, #337, #340, 2.5 and 2.6.
4. **Spring-back edges, left open by decision** (-1473): a search frame mid-spring, and a tab return after a cut-short spring, can be off by up to 36 dp for a quarter second.
5. **Test gaps carried, not changed:** the bubble-minY test stays `@Ignore`'d, and the touch guards cannot see `SightingsMapLayout` (-1457).
6. **Recomposition cost, inferred:** #353's bundles are built unremembered, so the frame recomposes with its host (-1493). Smoothness on the S26 has not been seen.
7. **Documentation notes:** the skill, the CLAUDE.md pointer (line 253 holds) and the designer profile are docs only. #338's owner approval is the record's word (-1472).

## What Elder could not check
- No phone; every S22 result is the record's unless it was observed on a screenshot.
- The JUnit XML was not re-parsed.
- Sorrel's per-PR byte-identity checks were not re-run; the multiset check agrees with them.
- Whether the Offline maps chip can fail to land (-1479).
