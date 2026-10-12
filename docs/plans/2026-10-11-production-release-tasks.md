# Production release: task list (proposal)

Written by the planner (Rowan Waymark), 2026-10-11, on the owner's "Write a task list for production release". **This is a proposal, not a go-ahead:** nothing here is dispatched until the owner says so. Scope is the owner's (RECORD -831): "the actual Release build will focus on app tidiness and a round in android lint. Behind the scenes work mostly." The order is RECORD -905: production release first, then trail navigation and maps.

Base assumed: Forager main at 921aa9c7 (PR #219 merged 2026-10-11), which carries the journal entry page, barometer height, Records tidy and Navigate from entry maps. Every claim below about the tree is a premise to re-check.

## Decisions the owner makes first

| # | Decision | Options | Planner's recommendation |
| --- | --- | --- | --- |
| D1 | How the release is cut | One release with everything below; or a smaller one now (lint and owed checks) and tidiness after | One release, kept small: the scope is already behind the scenes ([[shorter-releases]] memory) |
| D2 | Code shrinking (R8) | Stay off (the deobfuscation warning stays); or turn it on, with rules, a mapping upload and a release-build device check | **Stay off this release.** Shrinking changes the release build and can cause release-only crashes; it deserves its own release and check |
| D3 | Native debug symbols | Add them to the bundle (a build setting); or leave the warning | **Add them.** It's a setting, not a behaviour change, and makes native crash reports readable |
| D4 | Lint policy | Fix errors only; or errors plus chosen warning categories; then whether lint runs on CI going forward, with a baseline for what's left | Fix all errors, triage warnings by category with the owner, and add lint to CI with a baseline so it can't regress |
| D5 | What "tidiness" means | The owner's own list; the candidates under T6 are the planner's, from the record | The owner names the items; T6 lists what the record already holds |

## Tasks, in order (each with how it's verified)

1. **T1. Lint survey (read-only).**
   - Run Android lint once on main and report counts by severity and category, with the top issues and their files.
   - No fixes.
   - The repository has no lint configuration or baseline today (checked: no `lint {}` block in app/build.gradle.kts, no lint file in the tree). Lint isn't part of CI (ci.yml runs assembleDebug and testDebugUnitTest).
   - Run it on CI with the run-this-branch trigger, or locally on the planner's go.
   - **Verified by:** the report's counts against lint's own XML/HTML output, attached.
2. **T2. Fix lint errors, and the warning categories the owner chooses (D4).**
   - Tests first wherever a fix changes behaviour; one revert check per behaviour-changing fix.
   - **Verified by:** lint rerun showing those categories at zero; full CI suite green.
3. **T3. Lint on CI (if D4 says so).**
   - A lint step in ci.yml, failing on new issues, with a baseline file for those deliberately left.
   - **Verified by:** a scratch branch with a planted lint error fails CI, and main passes.
4. **T4. Native debug symbols in the bundle (D3).**
   - **Verified by:** the bundle built from main contains the symbols. The owner confirms the Play Console warning is gone on upload.
5. **T5. R8 (only if D2 says turn it on).**
   - Rules, the mapping file in the release, and a full device check of a release build on the S22.
6. **T6. Tidiness: the owner's list (D5).** Candidates already in the record:
   - **The sundown heads-up differing by about 30 minutes between two phones** on the 2026-10-10 walk (RECORD -843; the owner thought the settings probably differed, unchecked). Investigate before calling it a bug.
   - **Leftover scratch branches** on GitHub: ten records-tidy-revert-* and others. Deleting them is the owner's call.
   - **Text that names other products** in the app, against the writing rule. Survey first.
   - Anything the owner adds.
7. **T7. Device checks owed before the release (gating).**
   - **Journal entry and barometer (RECORD -886, -888, -894):**
     - step 3: existing entries, on the S26;
     - step 7: battery, one hour with and without the barometer;
     - step 8: the flat pocket walk, at least 45 minutes on the S26, with swapped pockets if both phones go;
     - step 9: a climb of known height;
     - the Navigate tap from an entry map.
   - **Records tidy:** the nine-step S22 check (docs/audits/2026-10-11-records-tidy-completion-report.md).
   - **Return ends on arrival (PR #214):** the S22 desk and walk steps (docs/navigation/2026-10-11-return-ends-on-arrival-report.md).
   - **Verified by:** each step's named pass condition and evidence, recorded in RECORD.
8. **T8. The release build (the owner's).**
   - The owner builds and signs from main, as for the betas.
   - Release notes: the owner's wording, from a planner draft.
   - The owner checks Play Console's own requirements for moving to production on this account. Some account types require a closed test with a minimum number of testers for a minimum time first. That is unverified here and visible only in the console.
9. **T9. Store listing check.**
   - Read the listing text and data-safety answers against what the release actually does, and flag anything stale.
   - The owner edits the listing.

## Kept out of this release (by ruling)

- Trail navigation and our own maps: the next update (RECORD -904, -905).
- T20 (after own tiles) and track L, location fusion (the owner, -771).
- The land-ownership layer, with options pending (-882, -883).

## Not app work, but on the release checklist

- D36's account swaps for the forecast's data pulls (GBIF, Copernicus test accounts), in forager-forecast, recorded at -831 as on the release checklist. They are owner actions on accounts; nothing to build in the app.
