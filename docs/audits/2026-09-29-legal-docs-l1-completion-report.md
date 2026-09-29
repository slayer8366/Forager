# L1 report: the legal docs brought up to date (dispatch 2026-09-28-200)

**Coder session.** Configured model `claude-sonnet-5-5`: the session's system prompt names it ("The exact model ID is claude-sonnet-5-5"). The serving model was not read back from an API response inside the session.
**Branch and worktree:** `legal-drafts`, `/home/zynergy-labs/Zynergy/forager-wt/legal-drafts`.
**Base:** `legal-drafts` at `b6d6eca0` (the planner's first drafts), merged with `origin/journal-redesign` at `f645e8f9` by `git pull --no-rebase origin journal-redesign`, giving `acce3b99`. The merge had no conflict. (`git merge --no-rebase` is not an option of `git merge`, only of `git pull`; the pull form is the dispatch's own command.)
**Governing files, read in full:** `prompts/preserved/2026-09-29-41.md` (the dispatch); `prompts/preserved/2026-09-29-42.md` (the launch prompt); `prompts/preserved/2026-09-29-43.md` (the notes for the site agent, read for context); the three documents on `legal-drafts`; `docs/audits/2026-09-29-privacy-site-update-report.md`; `docs/plans/journal-redesign.md` from "Photo export and on-device journal backup (owner, 2026-09-29)" (line 842) to the end; `docs/audits/2026-09-29-kept-track-path-completion-report.md`; `CLAUDE.md`.

## Pre-registration (written and pushed before any code was read for a claim)

### Premises checked at the base, before observing

| Premise | Checked | Result |
|---|---|---|
| The worktree exists, is on `legal-drafts` at `b6d6eca0` and is clean | `git status`, `git log` in the worktree | holds |
| `origin/journal-redesign` merges cleanly | `git pull --no-rebase origin journal-redesign` | holds, no conflict |
| CLAUDE.md contains the line "a main session in this repository is the planner" (launch prompt, "Your role and rules") | `grep -n -i "planner\|main session\|roles and gates" CLAUDE.md`; the merge did not change CLAUDE.md (`git diff b6d6eca0 HEAD -- CLAUDE.md` is empty) | **wrong premise.** No such line exists at this base or on `journal-redesign`; the only hits for "planner" are the decayed-planner-picture entry (`CLAUDE.md:425-472`). The F3 report (`docs/audits/2026-09-29-kept-track-path-completion-report.md`, header) also mentions a trailing "Roles and gates" section that is not in this file. The launch prompt says the line "does not apply here", so nothing changes; recorded as a finding. |
| CLAUDE.md conflicts with the dispatch | read in full, 485 lines | no conflict found. The dispatch's rules (a claim names a file and line, or is unverified; push before you tidy; no fabricated capability) are CLAUDE.md's own. |
| D58's three phrases can be found | forager-forecast `docs/planning/DECISIONS.md` on `origin/main` (read with `git show`, no checkout changed) has the D58 row; the checkout at `review-protocol` does not carry it | holds. The phrases are in that row's Decision column. They are not written here or anywhere in this repository. |
| F3 (-195) has landed at my base | `git log`: `10fcd2f3` "Terminal 2026-09-28-205 closes -195 (F3)", `0df67ba1` "Merge ... into kept-track-path" | holds as history; **the code is read below before any track-path sentence is written** |

### Predictions and pass conditions

Each is a prediction about what the code at `acce3b99` will show. A prediction that fails is a finding, not an edit to the prediction.

| # | Dispatch item | Predicted at (file, from the reports; lines to be found) | Passes when |
|---|---|---|---|
| 1 | Track delete: a swipe in Records, Delete on a track's details, Undo, never while recording | `ui/records/RecordsTab.kt` (swipe), the track details sheet, `domain/DeleteTrackUseCase.kt:19-25` (the F3 report's line, before F3 changed it), wired at `AppContainer.kt` | a production caller exists for each of: swipe, details Delete, Undo, and a recording gate; each with a line |
| 2 | An entry keeps copies: waypoint and region coordinates today | `CartographyEntryEntity.kt` (ref and snapshot tables), `WaypointDecision`/`RegionDecision` snapshots | the snapshot fields and the delete path that leaves them are found with lines |
| 2b | After F3, a deleted track's path (lat/lng, no timestamps) stays in `cartography_entry_track_paths`, in every entry that has the track, until the entry is deleted, and is in backups | `Migrations.kt` (`MIGRATION_16_17`), `CartographyEntryEntity.kt`, `DeleteTrackUseCase.kt`, `CartographyEntryDao.deleteEntryAndRefs`, `JournalTables.kt` | each of the four halves of that sentence is read in code at the base. If any is not, the track-path sentence is written as a pending note and this report says so. |
| 2c | `delete-data.md:17` ("the track and every GPS point in it") is no longer the whole truth | F3 report "Flags outside scope" | the sentence is changed to say what is removed and what a kept copy leaves |
| 3 | Scheduled backups: the first run waits its interval; the app deletes older scheduled files it made itself, keeping the newest 5, and never touches manual backups or anything else in the folder | `data/backup/` (a scheduler and a worker) | read: the enqueue's initial delay; the prune's keep count; **how "made itself" is decided** (a file-name rule?); that a manual file and a foreign file are skipped. If the rule is by name, I will check whether a manual backup could match it, and report that rather than write "never" past what the code guarantees. |
| 3b | The first draft's "Forager never deletes old ones" is wrong | `privacy-policy.md:72`, `delete-data.md:34-35`, `privacy-policy.md:255`, `beta/README.md` | corrected in each place it appears (planner's outcome prediction 1) |
| 4 | A "Backups" notification channel; the permission is asked once when scheduled backups are first turned on; if declined, a message in the app at the next launch | `data/backup/` notifier, the Backup settings panel, a DataStore flag | channel id and name, the request site, the "asked once" flag and the next-launch message each found with a line. Which notifications exist (failure, partial, "saved with N photos left out") is read from the notifier, not from the plan. |
| 5 | A restore does not duplicate an offline region already on the phone | `RoomJournalBackup.kt` (regions) | the match rule is read (name, centre, radius); mentioned only in the sentences that already describe restore |
| 6 | Save to Gallery sets "date taken" from the photo's own time; never writes a location | `photo/PhotoExporter.kt` | the source of the time and the absence of any location column are read |
| 7 | The merged manifest's permissions match the policy's list | `:app:processDebugMainManifest`, then `app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` | the built manifest is diffed against the policy's list. Expected extras over the first draft: none. Expected to find or not find: a foreground-service type or a permission WorkManager adds beyond the four the site report names. |
| 8 | Imports are saved as imported | `PhotoExporter.kt`, `FilePhotoStore.kt` | the export is a byte copy; no scrub for imports |
| 9 | Every other factual claim in the three documents | code at the base | each is confirmed with a file, corrected, or removed and listed. Claims about things outside this repository's code (a dashboard, a platform's plan limits, the site's own Functions) are listed separately, see below. |

**Outcome predictions, mine:**
1. At least one first-draft sentence besides the "never deletes old ones" one is wrong or unconfirmable at this base. I expect the Cloudflare and Contact sections' dated attestations ("Verified ... on 2026-09-09") to be the unconfirmable ones, because they describe a dashboard and a mailbox, not code.
2. The first draft's permissions list is complete against the merged manifest, and the manifest adds nothing beyond `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` and one internal permission. If the manifest shows more, that is a finding.
3. "Never touches manual backups" holds only as far as a file-name rule allows. If the prune decides by name, I will say so rather than repeat the dispatch's "never".

**Not to be done:** no app code, no edit to `RECORD.md`, `docs/audits/README.md`, `CLAUDE.md`, `docs/plans/` or `prompts/`, nothing in zynergy-site, no merge into `journal-redesign`. One Gradle run, `:app:processDebugMainManifest`, after the machine checks.
