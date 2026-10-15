Inventory for tomorrow's pre-production tidying; nothing changed.

Read-only inventory for the planner (Rowan Waymark), written 2026-10-15 by a subagent. The owner, verbatim: "Tomorrow, we will wrap up the camera project and begin pre-production tidying. Today, we get what we can done".

**Base read:** Forager `origin/main` at `cb25b582` (PR #227 merged, RECORD -1045), `origin/dev` at `7a2c0286`, after `git fetch --all --prune` on 2026-10-15. RECORD read from `~/.cache/forager-wt/records/RECORD.md` (branch records-after-173, `bef3f7e1`, next free ID -1060 per -1059). Nothing was edited, committed, pushed or deleted, and no Gradle ran. One local state change, undone: this session's own worktree (`.claude/worktrees/bridge-cse_01GEQ8BjUExHsmHzm82NgpVs`) was detached to `origin/main` to read it, then checked back out to its branch `worktree-bridge-cse_01GEQ8BjUExHsmHzm82NgpVs`. The file is not git-added.

How each claim is supported: file:line at `cb25b582` unless another ref is named; "RECORD -n" for record entries; "UNVERIFIED" where I could not check.

---

## 1. The production release task list, with each task's state

Source: `docs/plans/2026-10-11-production-release-tasks.md` on records-after-173 (it is **not on main**). Intent -906; rulings -917, -922, -926 to -930.

| Item | Ruling / state | Evidence |
|---|---|---|
| D1 the cut | **Decided:** one small release | -917 |
| D2 R8 | **Decided: off.** Still off | -917; `app/build.gradle.kts:291` `isMinifyEnabled = false` |
| D3 native symbols | **Decided, then reversed: out of this release.** Play's warning stays | -917, -921 (Phase A stopped), -922 |
| D4 lint policy | **Decided: "Everything reasonable"**, as a dispatch after the dev-tools work merged | -926 |
| D5 tidiness | **Decided in part:** "Delete scratch branches". The sundown gap and the product-name survey are off this release's list | -926 |
| T1 lint survey | **Done** | -925; branch `lint-survey` `ce75f629` (unmerged), CI run 38207222367 |
| T2 lint fixes | **Not started.** No dispatch in RECORD after -926. Every shipped-code error the survey named is still in main's source (§2) | RECORD titles -931 to -1059 (none is a lint dispatch); source sites in §2 |
| T3 lint on CI | **Not started.** CI runs `assembleDebug` and `testDebugUnitTest` only | `.github/workflows/ci.yml:116`, `:187`, `:219`; no `lint` in any workflow |
| T4 native symbols | **Dropped (-922).** The Phase A report sits on unmerged branch `native-symbols` `e4154aba` | -921, -922 |
| T5 R8 | **Not applicable** (D2 off) | -917 |
| T6 tidiness | **Partly done.** Done: the ten `records-tidy-revert-*` branches deleted after a backup (-926; none left on the remote); dev tools out of main (PR #220, -939). Not done: everything in §3 and §7 | -926, -939; branch list §3 |
| T7 owed device checks | **Met by the owner's account, except sudden elevation change** (-924). Since then main has gained camera, outline, credits and RAW work whose device items are partly open: the tablet camera hold, the outlines and rail with real insets, and the credits links (2026-10-14 handoff). RAW stage 2 was checked on both phones (-1038, -1042, -1043) | -924; handoff "Waiting on the owner" 2 |
| T8 release build | **Not started for production.** Tester build 3292 (1.0.3292+g497423d6) is on closed testing (-962). Production access was applied for on 2026-10-15 at 14:08 PDT; Play says "usually takes 7 days or less" (-1059). Main has moved about 130 commits past 497423d6, including RAW stage 2, which has not been through closed testing (-1059 notes) | -962, -1059 |
| T9 store listing check | **Not started (UNVERIFIED that nothing exists).** No RECORD entry after -906 mentions it. The listing text is **not in the repository**. The only related files are `docs/audits/2026-09-11-store-description-feature-audit.md`, a month old, and `docs/legal/forager-data-safety-2026-09-10.csv`, last changed 2026-09-10 | grep of RECORD after line 16100; repository file search |

Also on the release checklist, outside the app: D36's account swaps for the forecast data pulls (-831, task list "Not app work"). State UNVERIFIED.

---

## 2. Lint (D4)

**The survey** (`docs/audits/2026-10-12-lint-survey.md` on `lint-survey` `ce75f629`; RECORD -925) ran lint 9.3.1 on the debug variant at `921aa9c7`. It found 376 issues: 29 errors, 338 warnings, 9 hints. Three independent totals agree.

**What the survey called worth fixing now** (its "For the owner" section), and where each stands on main `cb25b582`:

| Finding | Survey's suggestion | On main now |
|---|---|---|
| NonObservableLocale (5 errors): month names via `Locale.getDefault()` inside composables | fix now | **Still present**, 5 sites: `AvailabilityResultsUi.kt:189`; `AvailabilitySearchUi.kt:684, 934, 1133, 1163` (lines have moved since the survey) |
| Range (1 error): `maxZoom: Double = Double.MAX_VALUE` default | fix now | **Still present**, `ui/map/NavigationView.kt:518`, used at `:539` |
| SuspiciousIndentation (1 error) | fix now | **Still present**: the survey's site is now `AvailabilityMapControlsUi.kt:725`, and the same pattern appears again at `:848`. Whether lint flags `:848` is UNVERIFIED |
| LocalContextResourcesRead (5), ConfigurationScreenWidthHeight (4): stale after rotate or resize | fix now, with a rotation check | Not re-checked site by site. No commit since `921aa9c7` mentions lint (`git log --grep=lint 921aa9c7..origin/main` returns only the RAW stage 1 commit, which does not touch these) |
| DataExtractionRules (1, Security): `allowBackup="false"` without `dataExtractionRules` | fix now (privacy) | **Still present**: `AndroidManifest.xml:135` `allowBackup="false"`; `res/xml/` holds only `file_paths.xml` |
| ClickableViewAccessibility (2): map `setOnTouchListener` | look now; fix only if a screen-reader check shows a problem | **Still present**: `ui/map/SightingsMap.kt:1398` |
| Test-only (UnrememberedMutableState 4, ComposableNaming 5), ModifierParameter 14, AutoboxingStateCreation 9 hints | fix later | Not re-checked |
| LogNotTimber (272), UseKtx (13), version warnings (17), ObsoleteSdkInt (`mipmap-anydpi-v26` still exists) | leave; switch LogNotTimber off when lint goes on CI | Unchanged. `Log.*` calls in `src/main` now number about 403 |

**Changed by removal, not by fixing:** the 18 NewApi errors, 41 LogNotTimber warnings, StaticFieldLeak and UsableSpace were all in `app/src/debug`. That folder left main in PR #220 (-939); `app/src` on main holds only `androidTest`, `main`, `test` and `testDebug`. They now live on `dev`. This is inferred from the sources, not from a lint run.

**Why a fresh lint run is needed before fixing:** about 130 commits since the survey, including the RAW stage 1 and 2 camera code. That code uses `@OptIn(ExperimentalCamera2Interop::class)` (`photo/CameraXRawCapture.kt:62`, `:143`), and only lint enforces that opt-in (RECORD around line 17372). It is unproven by lint, as the handoff says.

**Does CI run Android lint now? No.** `ci.yml` has one `build` job running `assembleDebug` and `testDebugUnitTest`. CI also never compiles the **release** variant; the coder found the same under -927. The release build has only ever been built on the owner's machine (-962). The scratch lint job is kept on `scratch-lint-survey` `2fb9b012` for T3 to reuse (-926).

Latest CI: main `cb25b582` success, run 38773648257; dev `7a2c0286` success, run 38800557773.

---

## 3. Remote branches on slayer8366/Forager

There are **391 remote branches**. Merge state uses `git rev-list --count origin/main..branch`, where 0 means every commit is reachable from main and deleting the branch loses no commit. Patch-equivalence (`git cherry`) was checked too. The full per-branch table (name, date, sha, commits ahead of main and of dev, patch-unique count, PRs) was generated to `/tmp/brtable.tsv`. That file is not preserved; regenerate it before acting.

| Category | Count | Deleting loses commits? |
|---|---|---|
| Core: `main`, `dev`, `pre-main`, `records-after-173` | 4 | keep. `pre-main` (2026-10-01, fully in main) is still in CI's push trigger (`ci.yml:13`); retiring it is the owner's call |
| Finished, fully merged into main (m=0) | **270** | **No.** 140 have a merged PR and 130 have none (WIP, device-check, records-after-1xx and `t*-merge` branches). Dated 2026-08-31 to 2026-10-14. Includes `raw-stage1-probe`, `raw-stage2` and `raw-stage2-dng-writer`, which -1045 leaves "for the tidiness pass" |
| Tests-first scratch, merged (`tests-first/held-portrait`, `tests-first/large-screen-hold`) | 2 | No |
| Revert-check / scratch, one throwaway commit each | **54**: 36 `revert-check/*` (2026-10-12 to 10-14), 17 `raw-stage2-revert-*`, plus `scratch-lint-survey` | Each loses its one revert commit, which is scratch by design. `scratch-lint-survey` should stay until T3 reuses its job (-926) |
| Active: `raw-stage3-probe` and 4 `raw-stage3-probe-revert-*` | 5 | `raw-stage3-probe`'s 3 commits are patch-equivalent to dev's (PR #228 merged into dev 2026-10-15T20:16Z). Each revert branch adds one revert commit. Leave until stage 3 piece 1 closes |
| Unmerged, documentation only | **34** | **Yes**: reports that exist nowhere on main (list below) |
| Unmerged, with code or tests | **22** | **Yes** (list below) |

**Safe to delete with no commit lost: 272** (270 merged + 2 tests-first). Adding the 54 revert/scratch branches, whose only loss is a throwaway revert commit, makes 326. Leave `scratch-lint-survey` until T3. The CLAUDE.md "push before you tidy" rule and -926's practice call for a verified bundle backup first.

**Unmerged documentation-only (34): deleting loses the document.**
- **Live or recent:** `alan-repos-survey`, `api-call-count`, `inat-share-spec`, `sources-credits-inventory`, `external-camera-plan`, `land-ownership-research`, `lint-survey`, `native-symbols`. The handoff names the first, third and fourth as needing a PR or the owner's word.
- **Older reports:** `data-scout`, `hygiene-scout`, `motion-scout`, `merged-device-check`, `s22-day-check`, `s22-followup`, `s22-session-check`, `s22-sundown-check`, `leaving-journal-flake` (parked, -349), `legal-drafts` (7 commits: L1's privacy drafts, -147, -200, relevant to §6), `records-pi-after-166`, `remove-claude-kit`, `claude/avd-boot-measurement`, `claude/flake-and-check-signal-notes` (PR #85 closed), `claude/ideas-fusion-plan`, `claude/normalize-repo-history-records`, `claude/store-icon-512`, `claude/exp-arm-a-present` (an empty commit).
- **README and audit edits from 2026-09-20:** `claude/new-session-{102gri,b7z9bg,bogh3s,ckz1xj,pb8ynb,pd5wfd,vto65i}`, `preserve/main-checkout-e0b74af`. Each has one README.md or audit commit not on main; they look superseded, but that is UNVERIFIED.

**Unmerged with code or tests (22): deleting loses commits.**
- **Open PRs:** `claude/ios-port-feasibility-mvsjcr` (29, PR #101), `claude/working-practice-skills` (4, PR #104), `gitignore-signing-dedupe` (1, PR #109).
- **Withdrawn or parked:**
  - `fan-tap` (4; its last commit says "WITHDRAWN BY THE OWNER");
  - `ci-flake` (18, -296);
  - `decoded-photo-thread` (2, -348, parked);
  - `leave-journal-investigation` (4);
  - `privacy-fixes-redo-sonnet` (5, F5 redo -216);
  - `engine-spike` (4, a spike app);
  - `label-check` (1, a spike app);
  - `pi-build` (27, map-build scripts, the Pi side).
- **Old August and September work:**
  - `claude/2026-09-18-camera-session-work` (16, WIP);
  - `claude/archive-backup-exclusion-implementation` (3);
  - `claude/apk-a-signed-8eacc91` (1, PR #78 closed);
  - `claude/exp-arm-b-substituted` (2, PR #90 closed);
  - `claude/mapicon-pill-anchor-oonnkj` (27);
  - `claude/new-session-3x1aba` (9);
  - `claude/plan-execution-review-3zila4` (1, PR #27 closed);
  - `claude/plan-implementation-rjzmkr` (10, PR #26 closed);
  - `claude/surface-error-states` (5, PR #31 closed);
  - `pr-140-wrap-up` (13 ahead, 1 patch-unique).
- **Superseded:** `dev-rebase-candidate` (1): an older copy of dev's dev-tools commit (`0ef73f3b`, the same 106 files and +7527/-156 as dev's `4c278251`, but on an older base, so not patch-equal).

---

## 4. Local worktrees

All 26 worktrees (`git worktree list` from `~/Zynergy/Forager`) have **no uncommitted or untracked changes** (`git status --porcelain` empty). Ignored files were not counted, except: the main checkout has ignored `signing.properties`, `.claude/` and `__pycache__/`, which stay. There are no stashes.

**Clean, and HEAD is on a remote branch or in main (safe to remove later):**
- `~/.cache/forager-wt/`:
  - `map-chrome-outline`, `raw-stage1-probe`, `raw-stage2`, `raw-stage2-dng-writer` and `scrub-names-main` are all in main;
  - `api-call-count` matches `origin/api-call-count`;
  - `dev-rebase` (detached `4c278251`) is contained in `origin/dev`.
- `~/.cache/forager-wt/raw-stage3-probe` (`be5466e4`, matches origin): **active**, keep.
- `~/.cache/forager-wt/records` (`records-after-173`, matches origin): **keep**; this is the planner's records tree.
- `~/.cache/forager-wt/dev-rebase-2026-10-14` (detached `0ef73f3b`): its HEAD is only on `origin/dev-rebase-candidate`. Safe while that branch exists.
- `.claude/worktrees/`, all with HEAD in main:
  - `agent-a3c1257dab0ec88ab` (local branch `rms-continue-08`, not on the remote);
  - `bridge-cse_013g4q…`, `bridge-cse_01QpPp…` and `bridge-cse_01YY9s…` (each detached `58fbc642`);
  - `bridge-cse_01JYrv…` (`rounded-map-strip` `4b8e5c6c`);
  - `bridge-cse_01Mxev…` (detached `5f16d769`, **locked**);
  - `bridge-cse_01Ri8E…` and `bridge-cse_01WPxB…` (local branches with no remote, at `4ade21f0`);
  - `pr-140-wrap-up-5049ce` (detached `58fbc642`);
  - `remove-portrait-lock`.
- `.claude/worktrees/agent-a3f93139476d4dba8` (`lint-survey`) and `bridge-cse_01USA2…` (`external-camera-plan`): clean, matching their unmerged remote branches.
- `.claude/worktrees/bridge-cse_01GEQ8…`: this session's own, **locked**.
- Outside the two named folders: `~/Zynergy/forager-wt/agent-instructions` and `~/Zynergy/forager-wt/landscape-b1-tfcheck` (both detached `58fbc642`, in main).

**Worktrees with uncommitted work: none.**

The main checkout `~/Zynergy/Forager` is on `main` at `497423d6`, 3292's build commit and well behind `origin/main` `cb25b582`. It needs a pull before the owner's production build. It is clean.

---

## 5. Open PRs on slayer8366/Forager

| PR | Head → base | Created | State |
|---|---|---|---|
| #101 | `claude/ios-port-feasibility-mvsjcr` → main | 2026-09-12 | open, **CONFLICTING**, 17 files +3039/-8 (map-tile Worker code and audits) |
| #104 | `claude/working-practice-skills` → main | 2026-09-16 | open, **CONFLICTING**, 11 files +527/-1 |
| #109 | `gitignore-signing-dedupe` → main | 2026-09-21 | open, **MERGEABLE**, a one-line removal. Still valid: main's `.gitignore` has `/signing/` at both `:27` and `:53` |

All three were last updated 2026-10-11, and none is a draft. #228 (`raw-stage3-probe` → dev) merged 2026-10-15T20:16Z.

---

## 6. Release readiness facts

- **Version scheme:** `versionCode` is the git commit count and `versionName` is `1.0.<count>+g<sha8>`, with `.dirty` added for an uncommitted tree. A shallow or missing history gives a marked `UNVERSIONED-…` name with code 1 (`app/build.gradle.kts:45-106`, applied at `:253-254`). CI checks the built APK's version (`ci.yml` "Verify the version on the built APK"). 3292 = 1.0.3292+g497423d6 (-962).
- **R8:** off, `app/build.gradle.kts:291`. The proguard files are named but inactive.
- **Native symbols:** no `ndk { debugSymbolLevel }` anywhere in `app/build.gradle.kts` (grep). Out by -922.
- **Dev tools absent from main:** `app/src/debug` and `app/src/release` don't exist on main. A grep of `app/src/main` for WalkLogger, walk log, Diagnostics, CrashLog, the alert record types and the synthetic forecast finds **0 hits**. The remaining `BuildConfig.DEBUG` uses are the HTTP clients' debug flag (`AppContainer.kt:185-187`) and the database's debug-only fallback (`ForagerDatabase.kt:180-230`); both are ordinary. `app/src/testDebug` still holds 4 tests on main. They are alert, service and settings-panel tests, not dev-tool tests; why they are debug-only is UNVERIFIED. `origin/main..origin/dev` touches 115 files.
- **Privacy policy:** `docs/legal/privacy-policy.md` ("Last updated: 2026-10-07"; last commit `7c9a6690`, 2026-10-11, the crash-line removal). The published page is zynergy-site `privacy/index.html` (origin/main, last changed `f1762c4` 2026-10-06, "Last updated: 7 October 2026"), linked at `privacy-policy.md:183`.
  - **RAW/DNG: not mentioned, in either copy** (grep for raw, dng). The photo section (`privacy-policy.md:107-127`) describes only the JPEG scrub, which keeps the photo with its metadata if the scrub fails (`:116-117`). Stage 2 changes three things the policy now leaves out:
    - a RAW shot stores a second file (DNG) beside the JPG;
    - the DNG is written by Forager with no make, model, software, time or location, but **keeps the sensor's calibration data**, which the owner ruled not identifying (-1008);
    - the DNG **fails closed**: one that fails its check is deleted and the JPG kept (`photo/RawShotSaver.kt:26-29`; `data/raw/DngFileWriter.kt:18-19`).

    Backup, restore and export also carry the DNG (-1045). **In my reading the policy needs a line.** The photo section's accuracy claim ("stated here as it actually is") no longer covers every file the camera writes. The wording is the owner's.
  - **A larger stale claim, found on the way:** both the repository policy (`privacy-policy.md:47-49`) and the live site page say export and import are **"not built yet"**. Main has a Settings "Backup" section with "Back up now", "Automatic backup", "Backup folder" and "Restore from backup" (`ui/backup/BackupSection.kt:88-146`), first added 2026-09-28 (`91c5d713`) and shipped since PR #140/#142. RECORD -147 recorded in September that backup and restore made the policy wrong. The drafts that fix it sit on unmerged `legal-drafts` (7 commits, last 2026-09-29). The manifest's long comment carries the same stale "NOT YET BUILT" and "twelve migrations; 4.json-15.json" claims (`AndroidManifest.xml:110-112`, `:91-93`); schemas now run to `22.json`. RECORD -1059's answer to Play says the policy "was checked against what the app actually does". On this reading, it has not been since backup shipped.
- **Play listing text:** not in the repository (see T9). The data-safety CSV is from 2026-09-10. It declares location collected and shared, and no photo data type, which is consistent with photos staying local.
- **Production access:** applied for 2026-10-15 (-1059).

---

## 7. Other untidiness (counts and examples)

- **TODO / FIXME / XXX / HACK in `app/src/main`: 0.**
- **Unused resources:** a cheap grep found none. All 28 strings, 5 drawables and 3 fonts are referenced. UI text is mostly hard-coded in Kotlin, so this check covers little.
- **Root-level leftovers on main:**
  - `DISPATCH-REPORT.md` (265 lines, last 2026-09-09);
  - `STATUS.md` (418, 2026-09-09);
  - `NAVIGATION-SHELL-DISPATCH.md`, `PANEL-CONTENTS-DISPATCH.md` and `STRIP-REVERT-AND-PILL-DISPATCH.md` (2026-08-29);
  - `TRACK-PULSE.md` (364, 2026-10-10);
  - `RECORD.md` (12,247 lines, last 2026-10-10): a stale copy, while the live record is on records-after-173 (18,184 lines).
- **Stale source comment:** `AndroidManifest.xml:91-112`, described in §6.
- **Docs naming removed code** (WalkLogger, CrashLogPanel, DiagnosticsPanel, "Lock camera to portrait"): 16 non-audit docs. Examples: `docs/navigation/2026-10-05-walk-logger-report.md`, `docs/plans/landscape-phone-design.md`, `docs/plans/understory-design-system.md`, `docs/ui/2026-10-08-settings-moves-report.md`. Most are dated reports, which are historical by design. `docs/archive/camera-portrait-lock.md` is intentional (-968). Plans that still describe dev tools would be the ones to look at. README has 0 hits.
- **Large files:** `SightingsMap.kt` 2,712 lines; `AvailabilityCompactMapUi.kt` 2,096; `AvailabilityScreen.kt` 2,065; `AvailabilityViewModel.kt` 1,950. These are for the owner's "codebase overhaul" item, not this release.
- **The task list itself** is only on records-after-173, not main. That is per practice, noted for findability.

---

## Top 5 tidying items, in the order I'd do them

1. **Privacy policy (repository and live site), owner's wording.** Replace the "export and import not built yet" claim with what Backup and Restore actually do. Add the RAW/DNG line. Reconcile with the `legal-drafts` branch. Correct the stale manifest comment. Production access is pending and Play reads this page.
2. **A fresh lint run, then the D4 fix dispatch.** The 7 shipped errors and the DataExtractionRules item are unchanged. Re-survey first, because the camera and RAW code arrived after the survey. Then T3: a lint job on CI, reusing `scratch-lint-survey`, with a baseline and LogNotTimber switched off with its reason.
3. **Branch clean-up with a bundle first.** Delete the 272 fully merged and tests-first branches, plus the 53 revert-check and raw-stage2-revert branches (keep `scratch-lint-survey` until T3, and the raw-stage3 reverts until piece 1 closes). Then ask the owner about the 34 docs-only branches (merge the live ones; archive or bundle the rest) and the 22 code branches. Settle the 3 stale PRs: #109 is a mergeable one-liner; #101 and #104 conflict.
4. **A release-variant compile on CI.** CI never builds release. That check was in -927's plan and fell with -930. It is the only automated guard that the build Play receives compiles, and that `src/main` has no debug-only leak. Then the owner's T8 build from a pulled main (the main checkout is at `497423d6`).
5. **Repository and worktree hygiene.** Move the six stale root dispatch and status files and the stale root `RECORD.md` copy into `docs/archive/`, or drop them. Remove the 20-odd clean worktrees listed in §4 (keep `records`, `raw-stage3-probe` and the locked ones). Decide whether `pre-main` stays in CI's push trigger.

## Not checked

- Lint's behaviour on any code changed since `921aa9c7`; the per-site state of the stale-configuration and test-only warnings.
- What the live Play listing currently says (it is not in the repository).
- Whether the 2026-09-20 `claude/new-session-*` README edits are fully superseded on main.
- Why four tests live in `app/src/testDebug` on main.
- D36's account swaps.
