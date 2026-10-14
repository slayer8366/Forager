# Planner handoff, 2026-10-14 21:25 UTC (Tuesday 14:25 PDT)

Written by the outgoing planner session (Rowan Waymark) for the next one. Read first:
- `~/Zynergy/Forager/CLAUDE.md`;
- the memory index;
- `docs/process/planner-profile.md` on main;
- RECORD.md on `origin/records-after-173`, entries -945 to -1000 (this session).

**Next free RECORD ID: -1001.** This handoff takes -1000. The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`.

## Rules learned or confirmed this session

All are in memory. Read the files, not this summary.
- **Clarify first, record once.** One entry per settled decision. No entries for re-asks, the planner's misreadings or "pending" answers (owner, verbatim: "Don't record the things you confused"). A future record-hygiene pass is pending, with no go yet (memory `record-hygiene-pending`).
- **The owner's phone hides question previews, and sometimes the text above a question box.** Ask questions that depend on drawings in a plain message that ends the turn, with the drawings as text (memory `question-previews-not-on-phone`).
- **The camera screen never rotates, on any device; only its icons turn in place.** "Cardinal sin" (memory `camera-never-rotates`). When restating a removal, separate "the setting goes" from "the behaviour changes". Conflating them caused -980.
- **Credit reused work in the same change, alongside the other credits.** Alan Rockefeller's permission to use his GitHub is RECORD -965; nothing of his is used yet (memory `credit-reused-work`).
- **Coders sometimes worked in the planner's own worktree.** Give each coder an explicit worktree under `~/.cache/forager-wt/<branch>` and tell it to work only there.
- **Coders run as subagents on opus, CI only, no Gradle on the laptop.** The owner upgraded their subscription on 2026-10-14, so usage limits shouldn't stop coders now.

## State

- **main is `4ade21f0`.** Today's merges, each with a backup and a merge entry:
  - #223, camera (-986);
  - #224, map outlines, rail, readout and steady strip (-991);
  - #225, Sources & credits (-992);
  - #226, RAW stage 1 probe (-999).
- **dev is `8bfb9eac`:** main plus one dev-tools commit, CI green (run 38630511032). Rebase it after every merge to main, following the dev rule (-930). The last two rebases needed small conflict resolutions; -994's was done by a coder because it was a real code merge.
- **Beta:** build 3292 (1.0.3292+g497423d6) is on closed testing, Alpha (-962). It predates today's camera, outline and credits work.

## Running now

Nothing. No coder is running.

## Waiting on the owner

1. **The iNaturalist share-queue spec review.** It is on branch `inat-share-spec` (`docs/plans/inaturalist-share-queue.md`, -963, -964). Not dispatched.
2. **Device items:**
   - the tablet camera hold (-985; API 37, full screen, all rotations, split screen);
   - outline and rail checks with real insets;
   - Sources & credits links;

## Backlog for the production release

- **iNaturalist and Open-Meteo call grouping and caching:** the owner agreed. A first call-count check is needed, and nothing is dispatched.
- **Lint fixes** (D4, "Everything reasonable"): survey on branch `lint-survey`. Also, CI runs no lint, so the camera's ExperimentalCamera2Interop opt-in is unproven by lint.
- **Tidiness** (D5): many `revert-check/*` and `tests-first/*` branches remain on the remote, plus `dev-rebase-candidate`, `coordinate-format`, `rounded-map-strip`, `remove-portrait-lock`, `map-chrome-outline`, `raw-stage1-probe` and `route-drawn`. Ask the owner before deleting, with a bundle backup first.
- **RAW stages 2 to 6:** not written up or dispatched. The stage list is in -974 and -975; the owner's design is in -973 and -974. Their sources: Forager-app `docs/research/2026-10-13-raw-pipeline-plan-sources.md`, and both phones' probe readings (-996, -998).
- **Unmerged documentation branches:**
  - `alan-repos-survey` (the Alan Rockefeller repo survey);
  - `sources-credits-inventory` (the inventory audit);
  - `inat-share-spec`.

  Each is docs only and needs a PR or the owner's word.
- **Field measurement Phase 2** (Forager-app) and the top-down crosshair (B7d): separate, not scheduled.
- **The future companion app** (identification plus iNaturalist upload): direction only (memory `future-id-system-direction`).

## Corrections this session, for the record

- -957: the owner said "left side", not "port side". -956 had misattributed it.
- -971 withdrew -970's gear removal.
- -978: Settings had no save-location row; -972 was wrong.
- -980: -968's "camera always follows the phone" was the planner's mistaken framing.
- -994: the expected test count in dispatch 2026-10-14-01 was wrong.
- -997: an unchecked sensor claim in -996 was withdrawn.
