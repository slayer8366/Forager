# Project cleanup: a proposal (nothing deleted)

**Date:** 2026-09-29.

**Asked by the owner, verbatim:** "We will need a project cleanup. Select files that are no longer necessary for deletion. Do not delete until I authorize specifically".

**Measured by the planner** on the machine, at `origin/journal-redesign` 6af5b6da. **Nothing here has been deleted.** Each group is authorized, or not, on its own.

**Terms used below:**
- **"Merged"** means the branch tip is an ancestor of `origin/journal-redesign`, so everything in it is already in the journal branch.
- **"Clean"** means `git status` is empty.
- **Removing a worktree** means `git worktree remove`, which deletes the folder. Where the group says so, it also deletes the local branch with `git branch -d`, which refuses an unmerged branch. **Remote branches on GitHub are never touched by these groups.**

**Kept, and not proposed:**
- the main checkout `~/Zynergy/Forager`;
- the active worktrees: journal-redesign, planner-records, j6, landscape-l, device-part-2 (Session 3 next) and legal-drafts (the owner's site agent);
- `~/.gradle`'s downloaded libraries;
- `~/Zynergy/launch-prompts`, which is small.

## G1. Finished worktrees in forager-wt: merged, clean, nothing unpushed (~4410 MB)
There are 55 worktrees. Every one is merged and clean, so removing it loses nothing. The local branches go with them.

chrome-follow-ups (106 MB), ci-pre-main (24 MB), decorations (106 MB), device-backlog-a (91 MB), device-backlog-b (92 MB), device-chrome (0 MB), device-j8 (96 MB), device-l0a (91 MB), device-offline (0 MB), device-stage-1 (0 MB), device-tablet (0 MB), device-tablet-2 (93 MB), device-trips (94 MB), device-trips-tablet (94 MB), drawer-back (102 MB), j8 (104 MB), j8-follow-ups (103 MB), journal-backup (109 MB), journal-plan (90 MB), journal-redesign-plan (90 MB), l0b (106 MB), landscape-b1 (94 MB), landscape-b2 (86 MB), landscape-b2r (90 MB), landscape-capture (86 MB), landscape-design (86 MB), landscape-fixes (102 MB), layout-fixes (108 MB), leave-fixes (101 MB), m1 (101 MB), main-checkout-fix (50 MB), map-chrome (104 MB), marker-glyphs (89 MB), marker-palette (97 MB), marker-swatches (87 MB), night-evidence (0 MB), night-mode (96 MB), night-outline (101 MB), night-region (105 MB), night-region-capture (92 MB), offline-safety (104 MB), photo-export (104 MB), save-failure (101 MB), search-bar-fix (99 MB), sheet-alpha (98 MB), split-move (93 MB), split-scaffold (96 MB), strip-device-check (0 MB), strip-flash-timer-location (59 MB), strip-grid-level (50 MB), strip-housekeeping (24 MB), strip-torch (24 MB), tracks-frame (103 MB), trips-on-map (101 MB), understory-amend (88 MB).

## G2. Worktrees that need a look first (~370 MB)
- **agent-instructions** (branch agent-instructions-now, not merged, 90 MB). This is PR #141's branch. The owner closed the PR and deleted the remote branch, so this local copy is the last one. Proposed for deletion on the owner's word.
- **leave-journal** (leave-journal-investigation, not merged, 98 MB) and **navigator-audit** (not merged, 90 MB). Both are pushed to origin, so removing the worktree loses nothing; the branch stays on GitHub. Proposed: remove the worktrees and keep the branches.
- **landscape-b1-tfcheck** (detached at a merged commit, 91 MB). It has **1 uncommitted file**, which the planner looks at before anything else.

## G3. Claude Code session worktrees under ~/Zynergy/Forager/.claude/worktrees (~2.3 GB)
There are 35. They belong to Claude Code sessions, including this planner's and the running coders'. Removing one while its session is alive breaks that session.
- **Proposed:** the owner closes the sessions that are finished, then each worktree goes when its session is closed. Nothing is removed while a session is open.
- **Merged and clean** (14): accountability-phase-1 (23 MB), bridge-cse_012JNCAc2S4XRbyEsPXF4pDb (23 MB), bridge-cse_016ud6iSpE7PzdbwnmhuLqZe (431 MB), bridge-cse_017PL1ZbmpZHPq4brcKdDTT8 (23 MB), bridge-cse_019YUQsaENmgFYy9kT7Ck2VR (20 MB), bridge-cse_01BcqShzosraMo4pUXkqaqRp (90 MB), bridge-cse_01CGfaA9F8E3RCKg7Jr2Vdyo (24 MB), bridge-cse_01FzMHYPETK1jZkCP3qtDfbm (23 MB), bridge-cse_01Gr1TYristuK89ZaYVTYPRX (393 MB), bridge-cse_01Md1NYxk9qgG8y6g7CiSm3y (23 MB), kit-v0.2-install (24 MB), pulse-handback (24 MB), record-memory-and-protection (24 MB), record-pr114-merge (23 MB).
- **Not to touch without a look:**
  - 7 have uncommitted files;
  - 14 sit on main, not journal-redesign, and are probably idle sessions;
  - two carry a PR #103 branch (redo-pr103-camera-groundwork, pr103-rebase-89f53a4).

## G4. Device evidence in ~/Zynergy/device-evidence (2.7 GB)
Every folder is cited by a run record or report, so deleting one leaves those citations pointing at nothing, as B did for Part 1.
- **Proposed:** keep all of it until the Journal PR merges. Then delete the folders of closed checks, and keep what later work still needs:
  - 2026-09-28-tablet-sanity, for J6's tablet check;
  - 2026-09-29-part-2, for Part 2's re-checks;
  - the owner's own screenshots.
- **Sizes:**
  - offline-regions 510 MB;
  - night-region-candidates 503;
  - j8-check 365;
  - part-2 302;
  - tablet-sanity 294;
  - planned-trips-tablet 214;
  - planned-trips 203;
  - backlog-b 129;
  - l0a 114;
  - backlog-a 73.
- The 1.5 MB of write-protected database copies left in 2026-09-28-map-chrome-check go too, on the owner's word.

## G5. Scratch folders in ~/Zynergy from 2026-09-26 (~112 MB)
These are forager-night-spike (60 MB), forager-landscape-capture (36), forager-glyph-work (9), forager-swatch-work (4) and forager-split-after (3). None is a git repository, and each is cited by 2-7 files in the repo. Same proposal as G4.

## G6. /tmp, which is held in memory (tmpfs) (~480 MB)
- **Two Robolectric native-runtime folders,** 205 MB each, left by test runs. Proposed: delete whichever no running process has open, checked with lsof at the time.
- **Scratch files, about 100 MB:** do-analysis, do-after, layout-fixes-probe, j6-forward-src, j8fu, m3 and x.png. Proposed: delete once J6 and the L are done, since some may be theirs.
- **Kept:** /tmp/j6-app-build (J6's live build output) and /tmp/s1-app-debug.apk (Part 2's APK), until those finish.

## G7. Remote branches on GitHub
There are 135 remote branches, and 97 are merged into journal-redesign. Deleting them is outward-facing. Proposed: after the Journal PR merges, delete the merged ones, with each branch listed for the owner first.

## G8. Merge backups in ~/Zynergy/forager-repo-backups (1.1 GB, 23 backups)
They are the safety net for the merges into protected branches. Proposed: keep all until the Journal PR merges, then keep the newest three.

## G9. Another project: forager-forecast task worktrees in ~/Zynergy (~2 GB)
The largest is forager-forecast-t2-record-audit, at 1.4 GB. The Claude-kit folders are also here. They are outside Forager, so they are listed only for the owner. The planner proposes nothing for them.
