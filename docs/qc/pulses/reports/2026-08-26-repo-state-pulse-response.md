# Pulse response — repo state before L4 close-out housekeeping

**Pulse:** repo state before L4 close-out housekeeping (2026-08-26), read-only
**Response filed:** post-pulse (archiving deferred until the read-only constraint was explicitly lifted for this one act)

Read-only throughout the investigation itself: no commits, no edits, no branch/PR changes were made while gathering this. Two isolated investigative artifacts were created and removed before finishing — a `git worktree --detach` at `origin/main` (to measure layout without touching the working checkout) and a throwaway Compose test file inside it — both deleted; `git worktree list` showed only the normal checkout again afterward.

## 1. Ground truth

**`origin/main` HEAD:** `3783275`, 2026-08-27T02:01:46Z — a merge commit titled "Merge pull request #42: L4c serialized editing state + Understory design system into main," merged by the human owner (`slayer8366`) via GitHub's merge button.

**Everything merged since `5ba9fc5`** (49 commits):
- `e97985f`…`353d256` (8 commits) — L4c work (§1–§6), previously reported.
- `8ce962a`…`26f4d89` (~38 commits) — a separate, parallel workstream, "Understory" (M3 Expressive design system + map night mode), landed by merging into the L4c branch first (`fea6f6c`, "Merge Understory... into claude/l4c-serialized-editing-state," 2026-08-26T18:41), then PR #42 (already open, base `main`, head `claude/l4c-serialized-editing-state`) picked up that new head and was itself merged — so that PR ended up carrying both bodies of work into `main` together. The owner subsequently confirmed this was deliberate: work done on another account, consolidated and merged intentionally, not a process gap.
- `3783275` — the merge commit itself.

**Full suite on `main`:** confirmed via **GitHub Actions run 33031985132** ("CI"), triggered by this exact merge commit, status `completed`, conclusion `success`, run at 2026-08-27T02:01–02:05 — current, not stale. The merge commit's own message additionally claims a pre-merge run on `fea6f6c` passed "732 tests across 104 suites," with one `JournalTabTest` flake on attempt 1 and a clean attempt 2 — that specific number is the merge author's claim, relayed not independently reproduced. One CI check does currently fail on `main`'s head: "Workers Builds: forager-pmtiles" — per the merge commit message this is a second recurrence of the exact spurious failure `docs/plans/README.md` already documents for PR #25 (unrelated: neither merged branch touches `server/`), not independently re-verified (Cloudflare's build logs aren't reachable here).

## 2. Branch inventory

| Branch | Head | Ahead of main | Behind main | What it is |
|---|---|---|---|---|
| `claude/task-hwj91a` | `1305a4b` | 0 | 51 | Superseded integration branch for L1–L4b; fully contained in `main`. |
| `claude/l4c-serialized-editing-state` | `fea6f6c` | 0 | 2 | L4c branch; fully contained. |
| `claude/forager-m3-expressive-design-l4c` | `26f4d89` | 0 | 3 | Understory's working branch; fully contained. |
| `claude/l4b-persisted-drafts` | `d3c4b97` | 0 | 52 | Superseded; fully contained. |
| `claude/plan-implementation-rjzmkr` | `56cd764` | 10 | 137 | PR #26's own head — actively tracked, open, not orphaned. |
| `claude/android-foraging-m3-design-o6bu3u` | `79e5223` | 2 | 77 | Two doc-only commits ("M3 design direction and UI audit," "visual companion") — no code. Reads as one of the owner's design test-bed branches; left untouched, listed only. |
| `claude/surface-error-states` | `6937b5c` | 5 | 103 | Real feature commits (surfacing four error-state fields), tied to PR #31 — closed, unmerged. A genuine orphan: real work, nobody tracking it. |
| `claude/plan-execution-review-3zila4` | `9fd026e` | 1 | 137 | One old commit from 2026-08-20, superseded by later Phase 1a landings. Also an orphan, lower stakes. |
| `claude/claude-md-three-rules`, `claude/crash-handler-settings`, `claude/error-presentation-spec`, `claude/fix-fgs-location-crash`, `claude/handoff-addendum`, `claude/phase-stack`, `claude/phase1-combined`, `claude/pr26-rework-scoping` | — | 0 | 80–103 | All fully contained in `main` (merged PRs); stale branches, not orphans. |

Not found at all: `claude/android-sdk-install-vh5iu1` — no such branch on `origin`.

Could not definitively map either design branch to the literal names "Understory" and "M3 expressive" — neither branch name contains "understory."

## 3. Open PRs

Exactly one open PR: **#26**, "Add multi-region offline map management," base `main`, head `claude/plan-implementation-rjzmkr`, draft: false (confirmed, prior to this session's own action converting it — see the follow-up note at the end of this file). Every other PR in the repo's history is closed. No PR currently targets a base other than `main`.

## 4. Records

**`docs/qc/` on `main`:** `pulses/reports/` held three real files (no longer just `.gitkeep`): the L4b scoping response, the L4-closeout §2 verification pulse, and `2026-08-26-layout-phase-scoping-proposal.md` (filed by the Understory workstream). `dispatches/` and `dispatches/reports/` held the full L4b/L4b-R/L4b-R2/L4c pairs.

**Index gaps found** (corrected in the same action that archived this file — see the follow-up note):
- `docs/qc/README.md`'s closing paragraph read "L4c is a further, not-yet-landed correction on top of that merge" — wrong once PR #42 merged.
- `docs/plans/README.md`'s PR #26-rework row said PR #39 was "still open against `main`" — wrong, #39 merged 2026-08-25. The same row's Understory reference and the standalone Understory row both said PR #44 went "into `claude/l4c-serialized-editing-state`, not `main`" and that "`main` does not have that code yet" — both wrong once L4c (and Understory riding on it) landed. The same row also called PR #42 "open" — it merged.
- That row also said ".github/workflows/ci.yml carries two temporary trigger entries that must be reverted before merge" — checked directly: `origin/main`'s `ci.yml` triggers only on `main` pushes/PRs. The revert (`26f4d890`) happened; the doc note wasn't updated to say so.
- `docs/audits/README.md`: no gaps found — every file in `docs/audits/` had a matching index row.

**Plan docs describing an integration branch instead of main:** yes, per the two rows above, in `docs/plans/README.md` specifically.

**§1a in the archived L4c dispatch:** the original sentence claiming `JournalTab.kt`'s gallery path chains the two calls was still present verbatim (matching this project's established correction pattern — leave the wrong claim, append a correction rather than rewrite history), with the `**Correction (L4c, filed 2026-08-25):**` block immediately following it, on `main`, disproving that exact half. Confirmed by reading the file at `origin/main` directly.

**The two planner documents** (`planner-cold-start-handoff-2026-08-25.md`, `planner-operating-guide-2026-08-25.md`): searched the full `origin/main` tree by name — neither exists anywhere in the repo.

## 5. Housekeeping status (as found, before this session's own follow-up action)

1. §1a corrected in the archived dispatch — **done**. Confirmed on `main`.
2. Handoff correction patch (Save/Cancel rule, Workstream C, lesson 14) — **not done**. `git grep` for "lesson 14" and "handoff correction" across `main` returns nothing; no record of this patch's content exists in this session to verify against.
3. Two planner documents filed into `docs/qc/` — **not done**. Confirmed absent.
4. #26 converted to draft — **not done** at pulse time.
5. `claude/task-hwj91a` and `claude/android-sdk-install-vh5iu1` retired — **not done** at pulse time. `task-hwj91a` provably contained in `main` (0 ahead). `android-sdk-install-vh5iu1` doesn't exist on `origin` at all.
6. Plan docs updated to state main is the base going forward — **not done** at pulse time. Confirmed stale language in `docs/plans/README.md`.

## 6. The two device-reported bugs, on `main`

**Photo strip:** on `main`, unchanged in substance from the L4c fix — same `FlowRow` + `fillMaxWidth()` + `CenterHorizontally`, repointed from `LogSpacing.*` to Understory's newly-promoted `Spacing.*` (the only change to this section, confirmed via diff).

**Add/Change Location button, measured, not inferred:** `LogEntryDetailScreen.kt:168`, in its own `Row` beside the "Found at .../No location set." text, not under the Photos heading. The Photos heading (line 249) sits over a 3-button `FlowRow` (Camera/Gallery/From Album, line 255) — the location button isn't in that row.

An isolated worktree at `origin/main`'s exact commit, probed at both 360dp and 412dp wrapped in the real `ForagerTheme` (to account for Understory's font/theme changes), still showed every button's text measuring a uniform 58dp regardless of label length (unmerged-node bounds at 7–13 raw px) — Robolectric's text layout in this project still does not reflect real font metrics, even after the Noto Sans bundling change. That specific measurement remains unavailable here.

What the measurement did prove cleanly, independent of that limitation: at both widths, all three Photos-row buttons fit on one line with room to spare, and the Add/Change Location button's own bounds sit fully inside the root's width (286–344dp of 360, 338–396dp of 412) — not clipped, not off-screen. The mechanism that could produce an invisible off-screen button no longer exists: a `FlowRow` wraps rather than clips, and the location button lives in a two-child `Row` where the flexible sibling (the coordinate text, `Modifier.weight(1f)`) yields space rather than the button.

**Follow-up correction (post-pulse):** the theory that this fix explained the reported green sliver at the screen's right edge was withdrawn once the owner reviewed this response — the button now sits beside the location text and the Photos row holds three buttons that fit at both widths, so a clipped fourth pill can't be the explanation. That symptom remains unexplained; only a device rebuild closes it.

## 7. Beta readiness

DB version 9 on `main`, with a complete, continuous migration chain: `MIGRATION_3_4` through `MIGRATION_8_9`, all six registered via `.addMigrations(...)`. `MushroomLogMigrationTest.kt` builds a real version-3 database using the actual production version-3 entity classes (`LegacyForagerDatabaseV3`), seeds it with data, reopens it as the current `ForagerDatabase` with the entire 3→9 chain registered, and asserts the old data survived and the new tables work — the full chain, exercised together, against a faithful v3 fixture.

What that test does not prove, and what this pulse found no evidence for anywhere: the same thing running against real accumulated data on a physical device, as opposed to a synthetic seeded fixture.

A real risk found while checking this: `ForagerDatabase.create()` chains `.fallbackToDestructiveMigration(true)` after `.addMigrations(...)`. That fallback only fires when Room has no migration path for a given version jump. Two distinct concerns follow from it, per the owner's own follow-up: (a) any device on a pre-3 schema would have its database silently wiped on upgrade — a distribution-history question, not something this repo pulse can answer; (b) more structurally, the fallback is a standing hazard for every *future* schema change too — any migration someone forgets to register from here on wipes a user's database silently instead of failing loudly, which inverts this project's own standing preference for loud failure over quiet data loss. The owner's recommendation, recorded here rather than acted on during this read-only pulse: remove the destructive fallback before beta so a missing migration path throws instead of wiping data, as a separate decision from the pre-3-distribution question.

## 8. Standing invitation

- The Understory merge landing via L4c's PR was raised as a structural surprise in the original pulse; the owner has since confirmed it was deliberate (work consolidated across accounts before merging), not a process gap. Recorded here for the honest history of this pulse rather than left implying otherwise.
- This pulse response was originally left unarchived on the grounds that filing it would itself be an edit, which the pulse's own read-only instruction ruled out. The owner explicitly lifted that constraint for this one act; this file, and the docs/qc/README.md and docs/plans/README.md corrections listed in §4/§5 above, are the result.
