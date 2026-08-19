# Planner handoff docs

Task specs written by the EGD planner role (root `CLAUDE.md`) for a coder
session to pick up cold. Each doc below is meant to be self-contained: a
fresh agent with no memory of the planning conversation should be able to
read one and act on it without needing anything else.

Status as of 2026-08-18 (updated on `claude/index-navigator-plan`, following
the branch cleanup that retired the `egd-framework-planning-*` branches this
table used to point at):

| Plan | Status | Depends on |
|---|---|---|
| Mushroom Log Phase 1 (local field record) | **Merged to `main`** — PR [#15](https://github.com/slayer8366/Forager/pull/15), merge commit `dbd2c5d`. CI green, no open review threads at merge time. Coder session `session_01FdUcqbkxGbk2Qc9hYyZfpm`, branch `feature/mushroom-log`. Plan doc landed on `main` as `docs/plans/mushroom-log.md`. | none |
| [Mushroom Log Phase 2 — iNaturalist upload](mushroom-log-phase2-inaturalist-upload.md) | Scoped, **not dispatched**. | Phase 1 merged (done) · owner-registered iNaturalist OAuth app (not yet registered as of this writing — see that doc's "Owner action required") |
| [Map redesign](map-redesign.md) | **Merged to `main`** — PR [#17](https://github.com/slayer8366/Forager/pull/17), merge commit `6893df6`. CI green. | Phase 1 merged (done) |
| Forager Navigator plan + MapLibre migration spec | Vetted and resolved, but **kept off `main` deliberately** — open PR [#18](https://github.com/slayer8366/Forager/pull/18), branch `claude/forager-navigator-plan-b4oe3s`. Not a "not started" entry: every codebase claim in it is verified, and its two owner-level forks (trip entity, offline strategy) are resolved and recorded. It stays unmerged pending an explicit decision to actually schedule the phased work — see the branch for [`forager-navigator-plan.md`](https://github.com/slayer8366/Forager/blob/claude/forager-navigator-plan-b4oe3s/docs/plans/forager-navigator-plan.md) and [`maplibre-migration.md`](https://github.com/slayer8366/Forager/blob/claude/forager-navigator-plan-b4oe3s/docs/plans/maplibre-migration.md). | Phase 1a/1b/1.5: nothing outstanding, can start immediately. Phase 2: raster-DEM licensing/redistribution question, unresolved. Tile-endpoint activation (built in Phase 1b, dark at launch): gated on the app affording to run it. |
| Phase 1b step 3: MapLibre `OfflineManager` smoke test + glyph-crash finding | Hardware-verified, **kept off `main` deliberately** — open PR [#23](https://github.com/slayer8366/Forager/pull/23), branch `claude/phase1b-offline-packages`. Confirms the offline-download mechanism works, and isolates a real native crash (offline downloads of styles with glyph/label layers) to glyph-range resolution specifically, via a controlled three-way hardware comparison — see the PR description and `maplibre-migration.md` §7. No production wiring; scaffolding-only (`MapLibreBasemapPreviewActivity`). | Phase 1b steps 1-2 (merged, hardware-confirmed) |
| Real PMTiles tile-serving infrastructure (Cloudflare Worker + R2) | **Live and verified**, branch-only (no PR yet) — `claude/pmtiles-cloudflare-worker`, see `server/pmtiles-worker/README.md` on that branch. A continental-US PMTiles archive is deployed and confirmed serving real vector tiles. | none — self-contained infra |
| [PMTiles Worker → real app wiring](pmtiles-worker-android-wiring.md) | Handoff written, **not started**. Connects the two rows above: makes the real app's offline download actually use the live Worker instead of `OsmdroidOfflineMapRepository`, replacing `PersistentTileWriter`. Names three open decisions the next session should not pick silently. | PR #23 (mechanism + glyph finding) · PMTiles Worker infra (both above, done) · Phase 1b steps 1-2 wired into the *real* app (not yet true as of this writing — verify) |

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
