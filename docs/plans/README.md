# Planner handoff docs

Task specs written by the EGD planner role (root `CLAUDE.md`) for a coder
session to pick up cold. Each doc below is meant to be self-contained: a
fresh agent with no memory of the planning conversation should be able to
read one and act on it without needing anything else.

Status as of 2026-08-18 (planning session on
`claude/egd-framework-planning-hy8qpz`):

| Plan | Status | Depends on |
|---|---|---|
| Mushroom Log Phase 1 (local field record) | **Merged to `main`** — PR [#15](https://github.com/slayer8366/Forager/pull/15), merge commit `dbd2c5d`. CI green, no open review threads at merge time. Coder session `session_01FdUcqbkxGbk2Qc9hYyZfpm`, branch `feature/mushroom-log`. Plan doc landed on `main` as `docs/plans/mushroom-log.md`. | none |
| [Mushroom Log Phase 2 — iNaturalist upload](mushroom-log-phase2-inaturalist-upload.md) | Scoped, **not dispatched**. | Phase 1 merged (done) · owner-registered iNaturalist OAuth app (not yet registered as of this writing — see that doc's "Owner action required") |
| [Map redesign](map-redesign.md) | Scoped, **dispatched** — see that doc / the coder session for current status. | Phase 1 merged (done) |

The Phase 1 spec now lives at `docs/plans/mushroom-log.md` on `main` (landed
with PR #15), corrected for the `main` it was actually built against — DB
version 4 not 3, Seasonal tab already merged, branch base `main@141de1e`. An
earlier, stale copy also exists on the abandoned
`claude/egd-framework-planning-fzgnpy` branch — not authoritative, superseded
by the merged copy.

## Before dispatching either queued plan

1. Confirm Phase 1 actually merged (`git log main | grep mushroom`, or check
   the PR) — don't trust this table's status column, it's a snapshot.
2. Re-read the target plan doc's own "verify this yourself" callouts —
   both docs below flag specific facts (DB migration version, current
   basemap/nav code shape, iNaturalist API behavior) that may have shifted
   between planning and dispatch.
