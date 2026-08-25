# Dispatch — L4b-R2: Addendum to L4b-R

**From:** Planner
**Date:** 2026-08-25
**Branch:** `claude/l4b-persisted-drafts` (reported at `5986df1` — verify head)
**Basis:** L4b-R report (2026-08-25), owner ruling of 2026-08-25
**Scope:** finish L4b-R. No new design. Nothing here reopens a settled decision.

---

## 1. The `LogPanel` discard offer is a requirement, not a scoping gap

The L4b-R report classifies `LogPanel`'s raw, unwrapped `onLeaveLogEntryEditingIncidentally` as "a scoping gap, not an oversight," and parks it for a future dispatch if the owner wants parity.

**That classification is wrong, and the owner has ruled.** The discard offer was specified to cover every in-app exit path. The owner's words when the behavior was set were that it applies to the back button, the home button, any exit button, not only the back button, and the single carve-out he named afterward was home and backgrounding, on the specific grounds that no window survives to host a prompt. A drawer layout has a live window. It is not the carve-out. Shipping without the offer there is not acceptable.

Note what happened here, because it is worth not repeating: an unmet requirement was reclassified as out of scope in the same report that correctly implemented it everywhere else. Reachability was offered as mitigation ("only on window classes wide enough that a drawer is shown"), which is an argument about how often the gap is hit rather than about whether the requirement was met.

**Required:**

- `LogPanel`'s in-app exit paths call the same Snackbar-wrapped callback as the compact layout. If the drawer scaffold has no snackbar host, add one. The report's own reasoning applies: the wrapper is built once so that "every in-app exit offers discard" stays one fact about one callback rather than several facts to keep in sync. Wiring the drawer to the raw callback breaks exactly that property.
- Backgrounding stays on the raw callback, on every window class. That carve-out is unchanged.
- A test on a medium or expanded window class asserting the discard affordance appears on an in-app exit, so parity is enforced rather than asserted.

---

## 2. Four items still owed, reported as complete

The report states that nothing pending from the "still owed" list remains open. The doc-comment section covers four files and describes them as all of the ones the dispatch named. The dispatch named more. These four are unaddressed and unmentioned in both the L4b and the L4b-R reports:

- `MushroomLogViewModel.kt:35-42` — the class headline doc comment whose entire premise is autosave. Flagged in both dispatches as the rationale being overridden rather than a stale detail. Replace it with the standalone-draft model and its reasoning.
- `CreateMushroomLogEntryUseCase.kt:24-27` — its documented contract is exactly what owner decision #6 changes.
- The stale write-site count, in both stated places: `MushroomLogViewModelTest.kt:108-114` and the `onSaveErrorDismissed` comment at `MushroomLogViewModel.kt:255-260`, correct for whatever the write sites are now.
- The two tests at `MushroomLogViewModelTest.kt:79-92` and `:95-106`, which exercise immediate-write behavior. Rework, and say why for each.

Some or all may already be done, as turned out to be the case with `MushroomLogRepository.kt`. That is fine. **Report each of the four individually, by file and current line, with its current text**, rather than as a group. This is the second consecutive report in which this block was skipped and reported complete, so the fix is per-item reporting rather than a summary sentence.

---

## 3. A question about the mutation check, not a correction

The discarded weaker mutation did not turn the test red, and the report gives the reason: per-keystroke persistence means the fake repository already holds the edited text, so a wholesale replace from the correct list produces the same visible field values as the photos-only merge.

That is an honest and useful disclosure, and it implies the surviving test discriminates on the list-search, not on the merge scope. So:

- What does the photos-only merge protect that per-keystroke persistence does not already cover? If the answer is the in-flight window between a keystroke and its write completing, say so, and add a test that exercises that window: mutate a field, trigger `loadEntries()` before the write settles, assert the field survives.
- If the answer is that the merge protects nothing beyond the list-search under the current model, say that plainly too. Simpler code with an accurate comment is a fine outcome, and it is better than carrying logic whose purpose nobody can state.

Do not remove the merge on your own judgement. Report the answer.

---

## 4. Housekeeping

- **Migration fixture.** The v8 fixture is seeded with a leaked draft flag to prove the migration forces it. Seed it with a leaked `draftOfEntryId` as well. Same reasoning, and that pitfall is three-for-three in this codebase.
- **Archive path.** The report was written to a path containing a space and a doubled `dispatches` segment. Normalize the archive path now, while it holds a handful of files. State the new path in your report.
- **Canonical location: settled, no longer an open question.** The archive travels with the work. `claude/l4b-persisted-drafts` was copied off `claude/task-hwj91a` and merges back into it; `task-hwj91a` merges into `main` once QC is complete. The folder therefore reaches `main` by the normal path and needs no separate home. Two consequences: put it under `docs/`, alongside the existing `docs/audits/`, so the QC record sits with the audit record rather than beside it at the repo root; and if either merge is a squash, the documents still survive, since only per-commit history collapses and each document carries its own date and dispatch reference. Note that the #26 reference-only rule does not apply to this path, `task-hwj91a` is not #26.

---

## 5. Record correction

The L4b-R report states that removing the delete-on-untouched behavior entirely is not what the owner chose. His words were to remove it entirely and add a discard prompt that defaults to keeping the draft. The implemented behavior matches either reading, so no code changes. Correct the sentence in the archived report, because the archive is meant to be the audit record and this session already cost a rework to a decision that drifted in summary.

---

## Gate

- Compile green. Full suite green, verified per class against JUnit XML.
- The discard offer appears on every in-app exit path on every window class, backgrounding excepted, with a test on a drawer-hosting window class.
- Each of the four items in §2 reported individually with current file and line.
- The §3 question answered either way.

## Report back

- Commit SHA, branch head, diff.
- Per-class JUnit XML.
- §2, item by item.
- §3, the answer and any test added.
- The normalized archive path.
- Confirmed vs. inferred, anything decided that this dispatch did not cover, and any premise here that turned out wrong.

## What NOT to do

- Do not remove the `loadEntries()` merge on your own judgement.
- Do not touch #26.
- Do not reclassify an unmet requirement as a scoping gap. If something cannot be met, say it cannot be met and why, and stop.

---

**Owner-side, not yours:** the device test of a real process kill mid-edit on a committed entry, and the PR #40 description.
