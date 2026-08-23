# Planner handoff docs

Task specs written by the EGD planner role (root `CLAUDE.md`) for a coder
session to pick up cold. Each doc below is meant to be self-contained: a
fresh agent with no memory of the planning conversation should be able to
read one and act on it without needing anything else.

Status as of 2026-08-20 (PRs #24 and #25 merged to `main`; table below updated
to match — see the row edits for what changed):

| Plan | Status | Depends on |
|---|---|---|
| Mushroom Log Phase 1 (local field record) | **Merged to `main`** — PR [#15](https://github.com/slayer8366/Forager/pull/15), merge commit `dbd2c5d`. CI green, no open review threads at merge time. Coder session `session_01FdUcqbkxGbk2Qc9hYyZfpm`, branch `feature/mushroom-log`. Plan doc landed on `main` as `docs/plans/mushroom-log.md`. | none |
| [Mushroom Log Phase 2 — iNaturalist upload](mushroom-log-phase2-inaturalist-upload.md) | Scoped, **not dispatched**. | Phase 1 merged (done) · owner-registered iNaturalist OAuth app (not yet registered as of this writing — see that doc's "Owner action required") |
| [Map redesign](map-redesign.md) | **Merged to `main`** — PR [#17](https://github.com/slayer8366/Forager/pull/17), merge commit `6893df6`. CI green. | Phase 1 merged (done) |
| Forager Navigator plan + MapLibre migration spec | Vetted and resolved, but **kept off `main` deliberately** — open PR [#18](https://github.com/slayer8366/Forager/pull/18), branch `claude/forager-navigator-plan-b4oe3s`. Not a "not started" entry: every codebase claim in it is verified, and its two owner-level forks (trip entity, offline strategy) are resolved and recorded. It stays unmerged pending an explicit decision to actually schedule the phased work — see the branch for [`forager-navigator-plan.md`](https://github.com/slayer8366/Forager/blob/claude/forager-navigator-plan-b4oe3s/docs/plans/forager-navigator-plan.md) and [`maplibre-migration.md`](https://github.com/slayer8366/Forager/blob/claude/forager-navigator-plan-b4oe3s/docs/plans/maplibre-migration.md). | Phase 1a/1b/1.5: nothing outstanding, can start immediately. Phase 2: raster-DEM licensing/redistribution question, unresolved. Tile-endpoint activation (built in Phase 1b, dark at launch): gated on the app affording to run it. |
| Phase 1b step 3: MapLibre `OfflineManager` smoke test + glyph-crash finding | Hardware-verified, **still kept off `main` deliberately** — open PR [#23](https://github.com/slayer8366/Forager/pull/23), branch `claude/phase1b-offline-packages`. Confirms the offline-download mechanism works, and isolates a real native crash (offline downloads of styles with glyph/label layers) to glyph-range resolution specifically, via a controlled three-way hardware comparison — see the PR description and `maplibre-migration.md` §7. No production wiring; scaffolding-only (`MapLibreBasemapPreviewActivity`). Its *finding* is now acted on in production (PR #25, merged, below); this PR itself is still open on its own scaffolding branch — worth revisiting whether `MapLibreBasemapPreviewActivity` should be deleted now that step 4 is done for real, per the wiring doc's 2026-08-20 handoff section. | Phase 1b steps 1-2 (merged, hardware-confirmed) |
| Real PMTiles tile-serving infrastructure (Cloudflare Worker + R2) | **Merged to `main`** — PR [#24](https://github.com/slayer8366/Forager/pull/24), branch `claude/pmtiles-cloudflare-worker`. GitHub auto-marked it merged (`merged_at` 2026-08-20T04:48:02Z) because its commits landed on `main` via PR #25's merge commit `01c7710`, which was built directly on top of this branch. See `server/pmtiles-worker/README.md`. A continental-US PMTiles archive is deployed and confirmed serving real vector tiles. | none — self-contained infra |
| [PMTiles Worker → real app wiring](pmtiles-worker-android-wiring.md) | **Merged to `main`** — PR [#25](https://github.com/slayer8366/Forager/pull/25), merge commit `01c7710`, branch `claude/offline-maps-integration-21uez7`. `MapLibreOfflineMapRepository` replaces `OsmdroidOfflineMapRepository`/`PersistentTileWriter`/`OfflineMapStatusFile` (all deleted) in `AppContainer`; offline downloads on `main` now pull real vector tiles from the live Worker via MapLibre's `OfflineManager`. Hardware-confirmed on the owner's device before merge: download completes against the real glyph-stripped Protomaps style, and a completed region survives a genuine force-close/clear-recents/restart cycle. The one non-required CI check on the merge commit's PR ("Workers Builds: forager-pmtiles") showed `failure` — ruled out as this PR's problem before merging: the identical Worker code built `success` on PR #24's own branch, and `mergeable_state` was `unstable` (checks failing/pending but none required), not `blocked`; most likely Cloudflare's Git integration reacting to a non-production branch name, not a code defect — not independently root-caused further, flag if it recurs. Still open post-merge: airplane-mode offline replay (unverified), and steps 1-2 (a live, labeled vector basemap reachable from the real map screen) — deliberately deferred, see the doc's own "Handoff, 2026-08-20" section for the prioritized next-steps list. | PR #23 (mechanism + glyph finding, still unmerged but its finding is already applied) · PMTiles Worker infra (merged, above) · Phase 1b steps 1-2 wired into the *real* app — still not true, still explicitly deferred |
| [PR #26 rework: multi-region offline map management](pr26-rework.md) | Scoped and decided (2026-08-23), **not dispatched**. Seven sequenced workstreams (reapply · migration renumber · repository reconciliation · drift corrections · `OfflineRegionEntity` relational design + delete-block flow · entry-level tile capture (new) · error-presentation compliance), one dispatch each with a build-and-test gate between. Source PR #26 (`claude/plan-implementation-rjzmkr`) is open, unmerged, 51+ commits stale against `main`. Decisions recorded in `docs/audits/2026-08-23-pr26-rework-scoping-decisions.md`. | PR #34 (error-presentation spec, merged) · PR #26 as reference material only, not to be rebased or merged directly |

The Phase 1 spec now lives at `docs/plans/mushroom-log.md` on `main` (landed
with PR #15), corrected for the `main` it was actually built against — DB
version 4 not 3, Seasonal tab already merged, branch base `main@141de1e`. An
earlier, stale copy existed on the now-deleted
`claude/egd-framework-planning-fzgnpy` branch, which was never authoritative.
`claude/egd-framework-planning-hy8qpz` — the branch this table used to cite
as its source — is also deleted; its only content still relevant
(`mushroom-log-phase2-inaturalist-upload.md`) was carried onto `main` first
(PR #19), and its map-redesign doc was already superseded by `map-redesign.md`
above before deletion.

## Before dispatching a queued plan

1. Confirm the plan is actually still unmerged and unstarted (check the PR
   linked above, or `git log main --oneline -- docs/plans/`) — don't trust
   this table's status column on its own, it's a snapshot.
2. Re-read the target plan doc's own "verify this yourself" callouts — plan
   docs flag specific facts (DB migration version, current basemap/nav code
   shape, iNaturalist API behavior) that may have shifted between planning
   and dispatch.
