# Dispatch — L4b: Persisted Drafts and Save/Cancel

**From:** Planner
**Date:** 2026-08-22
**Plan:** Log & location rework, piece L4b
**Branch:** `claude/task-hwj91a` (currently `0e2198b` — verify head)
**Basis:** L4b scoping pulse (2026-08-22)

## What L4b changes
**Autosave-on-every-keystroke goes away.** Today `onEntryEdited` writes to disk on every field change. Under the new model, edits accumulate in a **persisted draft** and reach the committed entry only on Save.

This is the largest behavioral change in the rework and **the piece most likely to fail silently.** A draft that doesn't survive, or an edit that doesn't commit, looks exactly like working software until someone loses their field notes. Treat a green suite as necessary, not sufficient.

## Ground rules
- **L4b only.** No EXIF (L5), no tile capture (L6).
- **#26 is reference-only.** Never merge, rebase, or push to it.
- If a premise below turns out wrong once you start, **stop and report**.

---

## Owner decisions (2026-08-22)

### The storage shape
**A draft is an entry row marked uncommitted** — a discriminator column on `mushroom_log_entries`, not a second table and not a change-list.

**Reasoning that must survive into the code, because it is the whole justification:** the two rejected options fail *silently*. A duplicated draft table drifts from the entry table with no compiler check, so a field added later is silently dropped on save. A change-list can't distinguish "cleared" from "untouched." A flag fails *visibly* — a query that forgets to filter shows a stray entry, which is annoying and obvious rather than quiet and lossy. Given the standing rule that nothing disappears from the log, a loud failure beats a quiet one.

**The cost is real and must be handled deliberately:** every read of `mushroom_log_entries` now has to decide whether it wants drafts. **Audit every query and every call site, not just the obvious ones.** Say in your report how many you found and how each was resolved.

### Draft identity
A draft **has a real entry id from creation.** `CreateMushroomLogEntryUseCase` already generates the id before persisting, so photo references work unchanged — `log_entry_photos` needs no schema change and no second table.

**Owner decision #6 — "nothing appears in the log until the user has put something there" — is a rule about what the log *shows*, not about when a row is created.** These are different things and the pulse was right to separate them.

### The three exits
- **Save** — commit the draft onto the entry.
- **Cancel** — discard the draft. Reverts to the last saved state. **The only exit that throws anything away.**
- **Leaving without answering** — tab switch, backgrounding, back button — **auto-saves.** The half-finished entry sits in the log to finish or delete.

The exit prompt applies to a deliberate back-out where the user is present to answer. An incidental exit saves rather than blocking on a dialog nobody sees.

### Crash recovery
**Per-entry, not one bulk prompt.** On opening the journal, an uncommitted draft is offered for reinstatement as the user reaches that entry. Owner's reasoning: seeing entries survive one by one is reassuring.

Two existing "Incomplete" badge sites are precedent for a draft indicator in the list.

---

## The known hazard — handle this explicitly

`loadEntries()` **unconditionally replaces `editingEntry`** from a fresh repository read (`MushroomLogViewModel.kt:69-77`). G3 added that, correctly, because nothing uncommitted existed to lose.

Once autosave stops, **that same call would silently discard in-progress typing** — including when triggered by an unrelated action, since `onDeleteGalleryPhoto`'s success path calls it too.

This is the single most likely silent-data-loss path in L4b. Resolve it deliberately and say how. Test that a refresh during editing does not lose uncommitted edits.

---

## Also in scope

**The write-site count is stale.** Two places state five; there are seven (`MushroomLogViewModel.kt` lines 119, 144, 167, 185, 206, 220, 242). G3 added two without updating either. Fix both statements — `MushroomLogViewModelTest.kt:108-114` and `onSaveErrorDismissed`'s comment at `MushroomLogViewModel.kt:255-260` — and make the count correct for whatever the write sites become.

**Stale autosave documentation.** The pulse lists these; all describe a design L4b overrides:
- `MushroomLogViewModel.kt:35-42` — the class's headline doc comment, whose entire premise is autosave. **This is the rationale being overridden, not a stale detail.** Replace it with the new model and its reasoning.
- `MushroomLogRepository.kt:40-45` — the `save()` behavior stays true; the parenthetical reason no longer describes how it's called.
- `MushroomLogDao.kt:18`, `LogEntryDetailScreen.kt:51`, `JournalTab.kt:182` — stale rationale; the last cross-references the class comment above.
- `CreateMushroomLogEntryUseCase.kt:24-27` — its documented contract is exactly what decision #6 changes.

**Tests asserting immediate-write behavior** — `MushroomLogViewModelTest.kt:79-92` and `:95-106`. Neither is named around autosave but both exercise it. Rework, don't delete without saying why.

## Migration
`MIGRATION_8_9`, version 9. This codebase's documented practice is the full rebuild cycle rather than bare `ADD COLUMN`, even where SQLite would allow the latter — follow it.

**The legacy-fixture pitfall is three-for-three** and has recurred in a shape the narrowed rule predicted wouldn't happen. `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md` records it. **Run the fixtures rather than reasoning from the rule.**

---

## Gate
- Full compile green.
- Full test suite green, verified against **JUnit XML**, not the build log.
- `MIGRATION_8_9` runs against a real v8 fixture; no committed entry is lost or marked draft.
- **A refresh during editing does not discard uncommitted edits.**
- **No query returns drafts where it shouldn't** — report the audit.
- Tapping "+" and leaving immediately puts nothing visible in the log.

## Tests
- Save commits; Cancel reverts to last saved state; incidental exit auto-saves.
- A draft survives process death and is offered on reopening that entry.
- Cancelling a brand-new entry leaves nothing in the log.
- A photo pulled into a draft attaches on Save and does not on Cancel; the photo stays in the album either way.
- A refresh triggered mid-edit preserves uncommitted edits.
- Drafts don't appear in the entry list, the gallery's cover thumbnails, or anywhere else the audit identifies.

**Two standing cautions, both found the hard way here:**
1. **Verify a failure fixture actually fails before writing the test around it.** G2 found `BitmapFactory.decodeFile` succeeds under Robolectric on a missing file *and* on arbitrary bytes — the obvious failure test would have passed without entering the failure branch.
2. **Watch for no-op harness wiring.** L4a found harnesses whose callbacks went nowhere, letting tests pass while asserting nothing.

## What NOT to do
- Do not add a second table for drafts or draft photo references.
- Do not let Cancel delete anything from the album — only the reference.
- Do not block an incidental exit on a dialog.
- Do not touch #26.

## Report back
- Commit SHA, branch head, and the diff.
- Test results from JUnit XML, per class.
- **The query audit** — how many reads of `mushroom_log_entries` you found, and how each resolved the draft question.
- How you resolved the `loadEntries()` hazard.
- How a draft is distinguished from an in-progress edit for crash recovery, if at all.
- Per the disclosure norm: confirmed vs. inferred, could-not-determine, any premise here that turned out wrong, anything you decided that this dispatch didn't cover.

**Standing invitation:** report anything structural this dispatch didn't anticipate. Twelve finds so far — most recently the stale write-site count and the `loadEntries()` hazard, neither of which any dispatch asked about.
