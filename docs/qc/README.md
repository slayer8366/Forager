# QC — pulses and dispatches

Planner/coder dispatch-and-report cycles for work still active on a task branch, filed the same
way `docs/audits/` files a point-in-time record — created here so the investigation and the work it
led to survive the session that produced them, not left to live only in chat uploads or a session
transcript. Unlike `docs/audits/`, a report here is not itself a standing record of the codebase —
it documents one dispatch's own gate/tests/decisions, correcting an earlier report in place when a
later pulse finds the earlier one wrong (see the L4b-R2 report's own record-correction section for
an example), rather than always superseding via a new dated file.

Two subfolders, each with the same shape:

- `dispatches/` — a Planner dispatch document, and `dispatches/reports/` for the coder session's
  report responding to it.
- `pulses/` — a read-only scoping/verification pulse document, and `pulses/reports/` for the
  session's response to it.

This directory travels with the work: it merges into `main` by the same path the code it documents
does, and survives a squash merge (each document carries its own date and dispatch reference) —
it does not need a separate home the way `docs/audits/` sometimes does for cross-branch findings.

## L4b — persisted drafts (Workstream L4 of `docs/plans/pr26-rework.md`)

| Date | Document | File |
|---|---|---|
| 2026-08-22 | Scoping pulse response (read-only, no code changes) | `pulses/reports/2026-08-22-l4b-scoping-pulse-response.md` |
| 2026-08-22 | Dispatch: persisted drafts and Save/Cancel/incidental-exit | `dispatches/2026-08-22-l4b-persisted-drafts-and-save-cancel-dispatch.md` |
| 2026-08-25 | Report: persisted drafts and Save/Cancel/incidental-exit | `dispatches/reports/2026-08-25-l4b-persisted-drafts-report.md` |
| 2026-08-25 | Dispatch: L4b-R, correction to a standalone draft row | `dispatches/2026-08-25-l4b-r-standalone-drafts-dispatch.md` |
| 2026-08-25 | Report: L4b-R standalone-drafts correction | `dispatches/reports/2026-08-25-l4b-r-standalone-drafts-report.md` |
| 2026-08-25 | Dispatch: L4b-R2, addendum (LogPanel discard offer, four report gaps, photo-race question) | `dispatches/2026-08-25-l4b-r2-addendum-dispatch.md` |
| 2026-08-25 | Report: L4b-R2 addendum | `dispatches/reports/2026-08-25-l4b-r2-addendum-report.md` |
| 2026-08-25 | L4 close-out checklist §2 verification pulse response (read-only, no code changes) | `pulses/reports/2026-08-25-l4-closeout-section2-verification-pulse.md` |
| 2026-08-25 | Dispatch: L4c, serialized editing state, photo race, two form fixes (§1a corrected in place — see the block below its own text) | `dispatches/2026-08-25-l4c-serialized-editing-state-dispatch.md` |
| 2026-08-25 | Report: L4c serialized editing state (corrects its own first pass on the §3 vacuous test and the §5 draft-only-write mutation check — see that report's own "Premises that turned out wrong" section) | `dispatches/reports/2026-08-25-l4c-serialized-editing-state-report.md` |
| 2026-08-26 | Pulse response: repo state before L4 close-out housekeeping (read-only, no code changes) — ground truth, branch/PR inventory, records audit, the two device bugs re-measured on `main`, migration/beta-readiness findings | `pulses/reports/2026-08-26-repo-state-pulse-response.md` |

## Planner meta-documents

Corrections to the Planner's own standing reference material, as distinct from a dispatch or
pulse about the codebase itself.

| Date | Document | File |
|---|---|---|
| 2026-08-25 | Correction patch: `planner-cold-start-handoff-2026-08-25.md`'s Save/Cancel standing rule, Workstream C status, and a new lesson 14 — filed standalone (2026-08-27) since neither that handoff document nor `planner-operating-guide-2026-08-25.md` has ever been filed in this repo; see the patch's own "Filing note" | `dispatches/2026-08-25-planner-cold-start-handoff-correction-patch.md` |

**Still open:** the two documents this patch describes (`planner-cold-start-handoff-2026-08-25.md`,
`planner-operating-guide-2026-08-25.md`) have only ever existed as chat uploads — confirmed absent
from the full repo tree by the 2026-08-26 repo-state pulse. Filing the patch above does not close
that gap; it only means the correction itself has a durable home to be folded into those documents
once they're supplied.

## Understory — M3 Expressive design system (`docs/plans/understory-design-system.md`)

| Date | Document | File |
|---|---|---|
| 2026-08-26 | Scoping proposal: a layout phase after Understory (session-authored, unsolicited — no pulse prompted it; input to a planner dispatch, not a plan) | `pulses/reports/2026-08-26-layout-phase-scoping-proposal.md` |

Filed under `pulses/reports/` despite running the other direction from the usual
pulse cycle: the document is a session's read-only scoping output going *to* the
planner, rather than a session's response to a planner's pulse. It carries a
correction to a measurement cited in `docs/plans/understory-design-system.md`,
PR [#44](https://github.com/slayer8366/Forager/pull/44)'s description, and
several commit messages on `claude/forager-m3-expressive-design-l4c` — see its
§3.

Landed on `claude/task-hwj91a` via PR [#40](https://github.com/slayer8366/Forager/pull/40)
(`claude/l4b-persisted-drafts` → `claude/task-hwj91a`) — see `docs/plans/README.md`'s
`pr26-rework.md` row for the full landing status of L4 alongside the rest of that plan's
workstreams. **L4c has since landed on `main`** via PR [#42](https://github.com/slayer8366/Forager/pull/42)
(`claude/l4c-serialized-editing-state` → `main`, merged 2026-08-27) — that merge also carried
Understory (PR [#44](https://github.com/slayer8366/Forager/pull/44)'s content, merged into the
L4c branch first as `fea6f6c`) onto `main` in the same commit. Corrected following the 2026-08-26 repo-state pulse (`pulses/reports/2026-08-26-repo-state-pulse-response.md`)
— this paragraph previously called L4c "not-yet-landed."

### Layout phase — 5S applied to the UI

The layout-phase draft itself (owner-authored, "Layout phase — 5S applied to the UI") has not been
filed in this repo as of this writing — it exists only as a chat upload, marked "draft for owner
review." The two pulses verifying its claims against the tree are filed here since they're
independently useful and, per `CLAUDE.md`'s own "an audit that lives only in a session transcript is
not recorded" pitfall, worth a durable home regardless of whether the draft itself ever lands.

| Date | Document | File |
|---|---|---|
| 2026-08-28 | Pulse: map/icon claims in the layout-phase draft (read-only, no code changes) — relocates the draft's central 1S finding from the compass strip to `MapIconBar`, answers its five "decisions needed" questions | `pulses/reports/2026-08-28-map-icon-pulse.md` |
| 2026-08-28 | Pulse 2: `MapIconBar` touch-interception check, full row inventory, native-compass confirmation, Q5 provenance, full `map-redesign.md` decided-vs-provisional read, stale self-reference sweep (read-only, no code changes) | `pulses/reports/2026-08-28-mapiconbar-q5-provenance-pulse.md` |
