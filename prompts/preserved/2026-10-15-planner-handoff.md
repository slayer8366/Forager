# Planner handoff, 2026-10-15 (written 2026-10-16T02:03Z, 19:03 PDT)

Written by the planner (Rowan Waymark) at the owner's word ("Back up the repo into a branch so we can restart here"). It replaces `2026-10-14-planner-handoff.md` as the starting point. That file's "Next free RECORD ID" line went stale after -1047; this file is right.

**Next free RECORD ID: -1084.** The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`.

**Read first:**
- `~/Zynergy/Forager/CLAUDE.md`;
- the memory index;
- `docs/process/planner-profile.md` on main;
- RECORD.md on `records-after-173`, entries -1000 to -1071.

## Restore points (snapshot branches, created 2026-10-16T02:02Z)

| Repository | Snapshot branch | Commit |
|---|---|---|
| Forager | `snapshot-2026-10-15-main` | `402489ae` |
| Forager | `snapshot-2026-10-15-dev` | `296a3420` |
| Forager | `snapshot-2026-10-15-records-after-173` | `57d8842f` (moved after writing, to include RECORD -1072 and this file) |
| forager-forecast | `snapshot-2026-10-15-main` | `b9127e58` |
| forager-forecast | `snapshot-2026-10-15-t7-build` | `169cd63e` |
| forager-forecast | `snapshot-2026-10-15-t6b-host-genera` | `b0a0e889` |
| zynergy-site | `snapshot-2026-10-15-main` | `b22cca5a` |

Bundle backups for each merge are in `~/Zynergy/forager-repo-backups/` (2026-10-15-01 to -04, and the dev-*.bundle files).

## Standing rules (unchanged; all in memory)

- **No build without the owner's explicit go for that build.** A design answer is not a go. Ask "start a coder on X now?".
- **Points of no return (-1030):** stop and ask the owner before:
  1. any presence or outcome count;
  2. any fit;
  3. anything published.
- **One piece at a time for the camera (-1044).** Averaging is automatic, never a menu choice; HDR is the only processing choice.
- **The camera screen never rotates.**
- **Times to the owner in Pacific.**
- **Owner screenshots and device photos stay off GitHub.** Device evidence lives in `~/Zynergy/device-evidence/`.
- **Coders are Opus subagents,** each in its own worktree, CI only for the app. The laptop is for forecast work.

## State

### Forager app

- **main `402489ae`.** RAW stage 2 is merged (PR #227, -1045). The privacy policy and delete-data page are corrected (PR #229, -1068).
- **dev `296a3420`:** main plus the dev-tools commit and six dev-only probe commits (piece 1's burst-rate probe and the platform fast-burst probe).
- **Play:**
  - closed test build 3292;
  - **production access applied for 2026-10-15 14:08 PDT (-1059), awaiting Google, about 7 days.** The answers are recorded there.
  - Recommendation when approved: a fresh build from main to closed testing first, then promote that build.

### RAW camera

- **Stage 2 is done on both phones** (-1038, -1042, -1043).
- **Stage 3 findings:**
  - CameraX bursts run at about 4 frames a second (-1053, -1055).
  - The platform camera interface bursts at 30 frames a second: 8 frames in 233 ms on the S22 (-1069).
  - The S22's noise-reduction, hot-pixel and shading modes do not change its RAW.
  - S22 RAW is the sensor's 9-in-1 binned mode; full resolution is blocked by the maker's camera driver (-1067).
  - Phone noise reduction is always off for RAW (-1064).
- **Pending for tomorrow's camera wrap-up:**
  - the S26 fast-burst run, plus a read-only dumpsys;
  - the design, B (borrow the camera for bursts, with exposure handed over) or A (Forager runs the whole camera);
  - the four full-resolution questions in -1067.

### Forecast (forager-forecast)

- **main `b9127e5`** (-1057).
- **T7 (PNW mycorrhizal habitat map):**
  - plan, verify, prior art and proposals are done; the owner ruled the proposals as recommended (-1062);
  - **T7 build in progress** on branch `t7-build` (-1071), up to the counting line. FungalTraits is fetched to `/mnt/work` only, never committed;
  - **an alternative-list scoping in progress** (-1071).
- **The permission email went out 2026-10-15** (-1070). It went to sergei.polme@gmail.com, copying Dr Tedersoo; the Leiden copy bounced. Current addresses: sergei.polme@ut.ee and nadia.Soudzilovskaia@uhasselt.be.
- **The continental job is running:**
  - user unit `t6b-night`, from `~/Zynergy/forager-forecast-host-genera-build` (branch `t6b-host-genera`);
  - SCANFI-layer stage only, two workers;
  - about 21,000 units left;
  - it stops starting tiles at 07:00 each day and needs restarting;
  - it pushes evidence to `t6b-host-genera`.
- **The rest-of-US tree rebuild is NOT authorised** (-1048).
- **The W42 pilot publish:** a reminder fires Saturday 2026-10-17 17:00Z to check the climate archive (-1037).

## Running at handoff (subagents of this session)

- **T7 build coder** (forecast `t7-build`).
- **Membership-list alternatives scoping** (writes `prompts/preserved/2026-10-15-11-membership-list-alternatives.md`, uncommitted until the planner commits it).

A new session cannot receive these agents' reports. If this session ends before they report:
- check `origin/t7-build` and the report file;
- re-dispatch from those if they are incomplete.

## Open items and recommendations

The full list was given to the owner on 2026-10-15. In short:
1. **Camera design:** B, with the exposure handoff.
2. **The S26 run:** first thing tomorrow.
3. **Pure binned RAW, not full resolution.**
4. **Merged-burst detail:** later.
5. **No hidden-setting experiment.**
6. **Stage 3 pieces in order.**
7. **Fresh closed-test build before production.**
8. **Tidying, from the inventory** `prompts/preserved/2026-10-15-08-pre-production-tidying-inventory.md`:
   - lint fixes;
   - lint and a release build on CI;
   - the 325 safe branch deletions after a bundle;
   - the 56 unmerged branches one by one;
   - PRs: merge #109, close #101 and #104;
   - the stale "crash reports" line on the site's delete-data page;
   - archive the stale root files;
   - check the store listing;
   - pull the laptop's main before the production build.
9. **The mushroom list:** wait about a week for a reply, then the alternative.
10. **T7:** the rest after the counting line, with the owner's word.
11. **The rest-of-US rebuild** after the PNW model.
12. **W42:** automatic on Saturday.
13. **The weather-lag source decision** at T8.
14. **The iNaturalist share spec A to D** after the camera.
15. **Owed device checks:** fold into the fresh closed-test build.
16. **API call caching** after release.

## Scratch branches to clean up (tidiness)

- `raw-stage2-revert-*` (17);
- `raw-stage3-probe-revert-*` (4);
- `scratch/raw-stage3-revert-r1` to `r4`;
- `raw-stage2`, `raw-stage2-dng-writer`, `raw-stage3-probe`, `raw-stage3-platform-burst`, `privacy-backup-raw`, all merged;
- forecast: the merged task branches.

Delete only after a bundle and the owner's word.
