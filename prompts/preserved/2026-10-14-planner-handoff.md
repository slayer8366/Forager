# Planner handoff, 2026-10-14 (rewritten 21:58 UTC, 14:58 PDT)

Written by the outgoing planner session (Rowan Waymark) for the next one, at the owner's word ("Rewrite the handoff"). This replaces the version written at 21:25 UTC. Read first:
- `~/Zynergy/Forager/CLAUDE.md`;
- the memory index;
- `docs/process/planner-profile.md` on main;
- RECORD.md on `origin/records-after-173`, entries -945 to -1001.

**Next free RECORD ID: -1048** (-1047 PNW host-trees run complete, continental resumed; -1046 stage 3 questions, all recommended; -1045 PR #227 merged, main cb25b582, dev 4c278251; -1044 averaging automatic, stage 3 hidden, piece by piece; -1043 S22 daylight check; -1042 S26 stage 2 check; -1041 PNW run OOM, restarted 1 worker; -1040 RAW stage 3 = averaged RAW; -1039 host genera built, PNW run; -1038 S22 stage 2 check; -1037 W42 at ~Oct 18, host-genera build, author email; -1036 licence check; -1035 W42 stopped, archive lag; -1034 publish W42 pilot; -1033 host trees, all recommended; -1032 host-genera verify filed; -1031 stage 2 amendment 2 built; -1029 M7 add host trees now; -1030 M1-M6, M8 provisional; -1028 T7 plan rewritten; -1027 PNW full model first; -1026 T6b restarted on t6b-run-2026-10-15; -1023 habitat Level III; -1024 stage 2 amendment 2; -1025 habitat plan rewrite; -1022 disk clean-up; -1020 D124 lint exclusion; -1021 forecast main merged a79d667; -1018, -1019 RAW message wording and colour; -1017 forecast branch merges; -1016 part B merged into raw-stage2; -1015 stage 2 part A built; -1014 call count filed; -1012 stage 2 part B, the DNG writer; -1013 the API call count; -1009 Forager writes the DNG; -1010 focus stacking added; -1011 amendment 1 to stage 2; -1008 calibration kept, location is the key; -1007 dispatched stage 2; 2026-10-15: -1002 and -1003 restored two RAW decisions; -1004 to -1006 answered the stage 2 draft, 2026-10-15-01. -1006 gives PR #227 and raw-stage2 to the planner for stage 2, superseding "Don't touch the PR or the branch" below).

## The first rule: no build without the owner's explicit go

The owner, verbatim, 2026-10-14: **"Yes no build occurs until I explicitly say so."**
- No coder starts, and no build-producing work is dispatched, until the owner gives an explicit go for that build.
- "Write up X" means the document only.
- An answer to a design question is an answer, not a go.
- Before any coder, ask a separate "Start a coder on X now?" and wait for yes.

Write-ups, plans and read-only checks are not builds. See memory `dont-infer-authorization-to-build`.

**Why it's first:** on 2026-10-14 the outgoing planner read "Show it now", the owner's answer to whether the RAW format chip should appear, as a go, and dispatched a RAW stage 2 coder. The owner: "I didn't authorize anything. Undo everything done in stage 2", then "Delete everything. Redo the entire records branch if needed". So:
- **Code:** stage 2's code branches were deleted. The backup is `~/Zynergy/forager-repo-backups/2026-10-14-04-raw-stage2-undo/`.
- **Records:** the records branch was rewritten without stage 2's dispatch file and entries, and the stage 2 parts of the earlier handoff. The backup of the old branch is `2026-10-14-05-records-pre-stage2-erase/`.
- **Renumbering:** the handoff entry is now -1000, and the owner's coder arrangement is -1001. The removed IDs -1002 and -1003 were stage 2 entries written by a second planner session; their numbers are free again.

**PR #227 and branch `raw-stage2`:** after the deletion they were restored and the PR reopened (21:55 UTC). The owner, verbatim: "I'm having the entire PR rewritten and redone from scratch, history and all". That work is the owner's own and is not this planner's. Don't touch the PR or the branch.

**A second planner session** ("the bridge session that read the handoff") was active this afternoon. Its view of the records is stale: it wrote -1002 to -1004 before the rewrite. If it's still running, it should re-read the records branch before writing.

## Other standing rules (all in memory; read the files)

- **Clarify first, record once.** One entry per settled decision. Nothing for re-asks, misreadings or pending answers. A record-hygiene pass is pending, with no go yet.
- **Questions that depend on drawings** go in a plain message that ends the turn, with the drawings as text. The owner's phone hides question previews.
- **The camera screen never rotates on any device;** only its icons turn ("cardinal sin").
- **Credit reused work in the same change.** Alan Rockefeller's permission is RECORD -965; nothing of his is used yet.
- **Coders run as subagents on Opus,** each in its own worktree under `~/.cache/forager-wt/<branch>`, CI only, with no Gradle on the laptop. The laptop is for forecast work (-1001). Give each coder an explicit worktree; two coders this session worked in the planner's own folder.
- **Times to the owner in Pacific;** RECORD in UTC. Products described generically. Owner screenshots stay off GitHub.

## State

- **main is `4ade21f0`.** Today's merges, each with a backup and an entry:
  - #223, camera (-986);
  - #224, map outlines and rail (-991);
  - #225, Sources & credits (-992);
  - #226, RAW stage 1 probe (-999).
- **dev is `8bfb9eac`:** main plus one dev-tools commit, CI green. Rebase it after every merge to main (the dev rule, -930). The two most recent rebases needed conflict resolution; -994's went to a coder.
- **Beta:** build 3292 is on closed testing (-962). It predates today's work.
- **Running:** nothing. No coder is running from this session.

## Waiting on the owner

1. **The iNaturalist share-queue spec,** on branch `inat-share-spec` (`docs/plans/inaturalist-share-queue.md`, -963, -964): review. Not dispatched.
2. **Device items:**
   - the tablet camera hold (-985; API 37, full screen, all rotations, split screen);
   - the outlines and rail with real insets;
   - the Sources & credits links.

## Backlog for the production release (nothing here is started; each needs the owner's go)

- **iNaturalist and Open-Meteo call grouping and caching:** agreed in principle. Start with a read-only call count.
- **Lint fixes** (D4): survey on branch `lint-survey`. CI runs no lint, so the camera's ExperimentalCamera2Interop opt-in is unproven by lint.
- **Tidiness** (D5): many `revert-check/*`, `tests-first/*` and finished feature branches remain on the remote. Ask before deleting, with a bundle backup first.
- **RAW stages 2 to 6:** not written up or dispatched.
  - Design: -973 and -974; the stage list: -975.
  - Readings from both phones: -996 (S22 Ultra) and -998 (S26 Ultra), including the differing colour layouts, GRBG against GBRG.
  - Sources: Forager-app `docs/research/2026-10-13-raw-pipeline-plan-sources.md`.
- **Unmerged documentation branches:** `alan-repos-survey`, `sources-credits-inventory`, `inat-share-spec`. Docs only; each needs a PR or the owner's word.
- **Field measurement Phase 2** (Forager-app) and the top-down crosshair (B7d): not scheduled.
- **The future companion app** (identification plus iNaturalist upload): direction only.

## Corrections this session, for the record

- -957: the owner said "left side", not "port side".
- -971 withdrew -970's gear removal.
- -978: Settings had no save-location row.
- -980: "the camera always follows the phone" was the planner's mistaken framing.
- -994: a wrong expected test count.
- -997: an unchecked sensor claim withdrawn.
