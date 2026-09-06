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
| 2026-08-31 | Investigation: a stub map-slot pan button's click stops registering under Robolectric once SearchEntryBar composes as a new sibling in the shared Map-tab tree (same shape as the chip-row-unmount case); seven of eight `@Ignore`d tests confirmed working on-device, one ("Log a find") flagged as having its own separate, confirmed-real bug the `@Ignore` does not resolve | `2026-08-31-search-bar-overlay-stub-pan-not-registering.md` |
| 2026-09-06 | Pre-build report, completion report, amendment pre-build and completion reports (duplicate distance, back asks and never exits): navigation chrome consolidation dispatch (strip hides while navigating, elevation/coordinates fold into the HUD, `DistanceArm` removal blocked by a CI allowlist entry, no-fix one-message premise gap, needle suppression threshold) | `2026-09-06-navigation-chrome-prebuild-report.md` |
| 2026-09-06 | Pre-build and completion reports: location accuracy dispatch (live-fix gate at 50 m with the held-fix aging consequence, honest distance precision via `within`/`≈`, fused provider costed from the artifacts and stopped on the build-variant requirement) | `2026-09-06-location-accuracy-prebuild-report.md` |
| 2026-09-06 | Pulse: light budget and turnaround alert — what exists (an unused solar-altitude function, retrospective track length, a HIGH-importance off-track alert fired from the Activity), what does not (sunset time, path-length home, pace, any background delivery), and what each would cost | `2026-09-06-light-budget-pulse.md` |
| 2026-09-06 | Pulse: track point sampling and a display-time filter — the sampler has no movement test, GPS and network share one untagged listener, accuracy is persisted and the provider is not, one bypass-proof repository seam already exists, recording drops rejected fixes on all four rules, and Cartography persists a distance snapshot that a filter would leave disagreeing | `2026-09-06-track-point-filter-pulse.md` |
| 2026-09-06 | Pulse: compass reliability — the accuracy signal is reachable from the existing rotation-vector registration (an empty `onAccuracyChanged`, an unread `SensorEvent.accuracy`, an unread `values[4]` heading accuracy); no `SENSOR_STATUS_*`, no calibration mention, no test of the real provider; the strip/HUD vocabulary and the needle suppression it would sit beside; Robolectric can drive both signals | `2026-09-06-compass-reliability-pulse.md` |
| 2026-09-06 | Pre-build report: compass reliability dispatch (values[4] zero cannot be distinguished from unpopulated, so it falls back; 15° threshold argued from the needle walking the user out of the fix's own error circle in 100 m; rotation-vector status is the only one reachable; smoother reset trade-off; "Compass unreliable"; precedence placement; a Robolectric inference from the pulse corrected) | `2026-09-06-compass-reliability-prebuild-report.md` |
