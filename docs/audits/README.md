# Audits

Dated, point-in-time reviews of the codebase. Each file is a historical record of
what was observed on its date, not a maintained document. Findings are not updated
as they are fixed — a later audit supersedes an earlier one rather than editing it.

Audits are committed here so they survive the session that produced them.

| Date | Scope | File |
|---|---|---|
| 2026-08-21 | Code quality and structure (Phase 1) | `2026-08-21-phase1-code-audit.md` |
| 2026-08-22 | Session handoff: error-presentation spec implementation (PR #34) | `2026-08-22-error-presentation-handoff.md` |
| 2026-08-23 | Scoping decisions: PR #26 rework (multi-region offline map management) | `2026-08-23-pr26-rework-scoping-decisions.md` |
| 2026-08-23 | Session handoff: PR #34/#35/#36 merged, PR #26 rework plan, phase-stack convention, PR #27 found stale | `2026-08-23-session-handoff.md` |
| 2026-08-23 | Session handoff: #37/#38/#31 housekeeping, WS1 Part A landed, dependency-graph pulse, boundary redraw proposal, render-path and local-only-tile-rendering pulses | `2026-08-23-ws1-and-render-path-handoff.md` |
| 2026-08-23 | Archive: PR #26 rework's original seven-workstream split, verbatim, and why it was superseded by the four-workstream (0/A/B/C/D) redraw | `2026-08-23-pr26-rework-seven-workstream-split-archive.md` |
| 2026-08-24 | Structural finding: migration test fixtures that reuse a production entity class break when a migration alters that entity rather than only adding tables (found via `MIGRATION_5_6`) | `2026-08-24-migration-fixture-entity-reuse-pitfall.md` |
| 2026-08-24 | Session handoff: Workstreams A and B landed, C/D rescoping pulse superseded by the Log & Location rework pulse, not yet dispatched | `2026-08-24-session-handoff.md` |
| 2026-08-24 | Archive: Workstreams C (delete-block flow) and D (entry-level tile capture), verbatim, and why they were superseded by the six-piece Log & Location rework split (L1–L6) | `2026-08-24-workstream-c-and-d-archive.md` |
| 2026-08-29 | Update report: PR #52's field-test dispatch and two-round hardware-feedback iteration (observation-tap feature, List-tab View on Map, bubble/compass-strip polish) | `2026-08-29-pr52-update-report.md` |
| 2026-08-30 | Investigation: return-to-vehicle Compose-semantics click no-op, ruled out at the merge-tree level, confirmed working on-device; open question left for follow-up | `2026-08-30-return-to-vehicle-semantics-click-noop.md` |
| 2026-08-31 | Session handoff: return-to-vehicle harness-vs-product conclusion, CI skip allowlist, JournalTabTest CI flake found (open), From Album photo attachment confirmed broken on-device (open), owner prompts verbatim | `2026-08-31-session-handoff.md` |
| 2026-08-31 | Investigation: search dropdown dismissal breaks under Robolectric specifically when SearchEntryBar's chip row is unmounted, ruled out at geometry/timing/touch-path level, confirmed working on-device; thirteen tests `@Ignore`d, root Compose mechanism left open | `2026-08-31-search-dropdown-dismiss-chip-unmount.md` |
