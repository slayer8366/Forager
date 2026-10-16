# Planner handoff, 2026-10-16 (written 11:43Z, 04:43 PDT Friday)

Written by the outgoing planner (Rowan Waymark) at the owner's word ("Let's hand off to a new session"). It replaces `2026-10-15-planner-handoff.md` as the start point.

**Next free RECORD ID: -1085** (this handoff is -1084). The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`.

**Read first:**
- `~/Zynergy/Forager/CLAUDE.md`;
- the memory index;
- `docs/process/planner-profile.md` on main;
- RECORD.md on `records-after-173`, entries -1044 to -1084.

## Standing rules (all in memory)

- **No build without the owner's explicit go for that build.**
- **Points of no return (-1030).** For T7 on the PNW, the owner passed the presence count and the fit (-1081). **Publishing anything from T7 is still closed.**
- **The camera.** Averaging is automatic, never a menu choice; HDR is the only processing choice (-1044). The camera screen never rotates. Phone noise reduction is always off for RAW (-1064). One piece at a time for the camera (-1044).
- **Times to the owner in Pacific.** Owner screenshots and device data stay off GitHub; they live in `~/Zynergy/device-evidence/`.
- **Coders are Opus subagents,** each in its own worktree, CI only for the app. The laptop is for forecast work.

## STILL RUNNING at handoff: three subagents of the old session

A new session cannot receive their reports. Check each on GitHub:

1. **T7 full run** (forecast repo, branch `t7-build`).
   - The V9 count is done: `46792fe`, `docs/audits/2026-10-16-t7-v9-counts.md`.
   - One fold was timed: `dccb1f4`, 646 s, projecting **18.7 h of fits** (under the 24 h stop), so the full fit is proceeding.
   - Outputs go to `/mnt/work/Zynergy/forecast-data/t7/`; the report will be `docs/audits/2026-10-16-t7-fit-report.md`.
   - **Checked at handoff:** the fit runs detached in its own systemd scope (`run-p2116775-…scope`, process 2116775, `scripts/t7_fit.py fit /mnt/work/Zynergy/forecast-data/t7/fit docs/audits/2026-10-16-t7-fit --threads 4`). Its parent is the user's systemd manager, not the coder's shell, so it should survive the old session closing. Check with `systemctl --user list-units | grep run-p2116775`. When it ends, read its evidence folder and write or finish the fit report if the coder could not. If it died, restart it from the same command; the fit script has a cache.
2. **Camera reach probe** (Forager branch `camera-reach`, dispatch `2026-10-16-01`, RECORD -1082).
   - Fixes the pin probe's stall and maps RAW reach for every physical camera.
   - Last seen head `d12c7bad`; a PR into dev comes when it is done.
3. **Camera engine A prototype** (Forager branch `camera-engine-a`, dispatch `2026-10-16-02`, RECORD -1083).
   - A platform-camera engine behind `CameraCaptureSession` on dev, with CameraX as the default and fallback, plus a feature inventory and a manageability assessment.
   - The branch was not yet pushed at handoff.

**Where a coder stopped without a report,** read its branch and the dispatch, then re-dispatch the remaining part.

## Scheduled and recurring

- **The continental job** (user unit `t6b-night`, from `~/Zynergy/forager-forecast-host-genera-build`, branch `t6b-host-genera`, SCANFI-layer stage only, two workers) stops starting tiles at **07:00 PDT daily** and must be restarted each morning:

  ```
  cd ~/Zynergy/forager-forecast-host-genera-build && systemctl --user reset-failed t6b-night; systemd-run --user --unit=t6b-night -p MemoryMax=5G -p MemorySwapMax=0 -p Nice=10 --working-directory=$PWD $PWD/.venv/bin/python scripts/t6b_run.py --until 07:00 --stages scanfi-layers --workers 2
  ```

  Check the PAUSE file is absent first. The rest-of-US tree rebuild is **not** authorised (-1048).
- **W42 publish reminder.** A reminder was bound to the old session for **Saturday 2026-10-17 17:00Z** (-1037); a new session will not receive it. On Saturday, check whether the climate archive holds W42's window (through 2026-10-12T00:00Z). If it does, run the -1034 route on forecast branch `pnw-pilot-w42`, then the site PR, then the merge.

## State

- **Forager.**
  - main `402489ae` carries RAW stage 2 and the corrected privacy policy.
  - dev `eec76f4e` is main plus the dev-tools commit and nine dev-only probe commits (both burst probes and the sensor watch).
  - Open old PRs: #101, #104 and #109 (tidiness).
- **Play.** Production access was applied for on 2026-10-15 (-1059). Awaiting Google, about 7 days.
- **The camera** (findings in -1053 to -1079):
  - **Bursts:** platform bursts run at 30 frames a second on both phones.
  - **The RAW is untouched:** the phone's NR, hot-pixel and shading modes don't change it.
  - **RAW is binned:** 9-in-1 on the S22, 2x2 on the S26. Neither phone offers apps more than 12 MP.
  - **The sensor is held:** with RAW bound, the logical camera stays on the main wide at every zoom.
  - **JPG follows the phone's switching.** It switches by zoom, and once at a boundary without a zoom change. Close focus and dim light don't switch it.
  - **The main wide can be pinned** with a physical stream. Unlisted physical IDs can't be opened directly; the S26 lists the ultra-wide as camera 2.
  - **Design:** the planner recommends A built beside CameraX (-1082). The owner is testing its manageability (-1083), with B as the backup.
  - **Lens chooser:** the owner wants one, like the phone maker's app (-1083). Design it with the owner after the reach results.
  - **The owner's S26 now runs the dev build** (dev tools included; the owner's data is untouched). The owner was told; offer a plain main build if they want one.
- **Forecast.**
  - main `b9127e5`; T7 is on `t7-build` (see above).
  - FungalTraits: Dr Põlme consented (-1080). The full table is committed as a lossless export (D143). The owner's thank-you reply was drafted for them to send.
  - The membership-list alternatives (plan B and C) are scoped but not needed now (-1073).

## Open items for the owner

1. **T7.** The fit verdict when it lands, against the P10 bar. Publishing stays closed.
2. **Camera.** Engine A's assessment, then A or B. The lens-chooser design. Then the stage 3 pieces in order: production burst capture, the noise model from tripod bursts, alignment, the plain average, the detail-preserving merge, and a measurement round. After those, debayering.
3. **The full-resolution questions** (-1067). The planner recommended pure binned RAW, burst merging later, and no hidden-setting experiment. Not formally answered.
4. **Play.** When approved: a fresh build from main to closed testing first, then promote it.
5. **Tidying** (inventory `prompts/preserved/2026-10-15-08-pre-production-tidying-inventory.md`):
   - lint fixes;
   - lint and a release build on CI;
   - the 325 safe branch deletions after a bundle;
   - the 56 unmerged branches with the owner;
   - PRs #101, #104 and #109;
   - the site delete-data page's stale "crash reports" line;
   - archive the stale root files;
   - check the store listing;
   - pull the laptop's main before the production build.
6. **Later:**
   - the rest-of-US tree rebuild after the PNW model;
   - the weather-lag source at T8;
   - the iNaturalist share spec, A to D;
   - the owed device checks, folded into the fresh closed-test build;
   - API call caching.

## Scratch branches to clean up (tidiness, owner's word and a bundle first)

- **Forager:**
  - `raw-stage2-revert-*`;
  - `raw-stage3-probe-revert-*`;
  - `scratch/raw-stage3-revert-r1` to `r4`;
  - `sensor-watch-revert-*`;
  - the merged `raw-stage2`, `raw-stage2-dng-writer`, `raw-stage3-probe`, `raw-stage3-platform-burst`, `sensor-watch` and `privacy-backup-raw`;
  - `snapshot-2026-10-15-*` (keep until the owner says).
- **Forecast:** the merged task branches; `snapshot-2026-10-15-*`.
