# Dispatch — L4b-R: Standalone Drafts (correction to L4b)

**From:** Planner
**Date:** 2026-08-25
**Plan:** Log & location rework, piece L4b, second pass
**Branch:** `claude/l4b-persisted-drafts` (currently `f824e4c` — verify head), created off `claude/task-hwj91a` @ `0e2198b`
**Supersedes:** the storage-shape section of the L4b dispatch (2026-08-22)
**Basis:** L4b report (2026-08-25), owner review of the scoping record, owner decisions of 2026-08-25

---

## Read this first: what happened, and why it is not a bug in your work

The delivered L4b is competent work built on a decision that had already been reversed once before it reached you. This dispatch restores the original decision. Most of what you built survives.

The record: during scoping, the owner was asked whether a brand-new entry's draft should hang off an entry row created up front, or stand alone and become an entry on Save. He chose **standalone**, and gave the reason in his own words, that nothing appears in the log until the user has actually put something there. That choice created a real problem, which the scoping pulse correctly flagged in §2: `log_entry_photos` keys on `entryId`, and a standalone draft does not have one yet.

After the pulse returned options and chose none, the planner presented draft storage as a fresh three-way question and recommended the flag-on-entry shape, on the grounds that it dissolves the photo-reference problem. The owner accepted the recommendation. Nothing in that exchange said it was reversing a decision he had already made, and the accompanying reframing of decision #6, from a rule about when a row is created to a rule about what the log shows, is what made the reversal read as continuity.

**The photo-reference argument does not support the conclusion it was used for.** Two claims were fused. "A draft is a row in `mushroom_log_entries` with a real id from creation" is what solves photo references and avoids the drift that killed the separate-table option. "The draft is the same row as the committed entry" is a separate claim, and it is the one that lets an interrupted edit overwrite a committed entry in place. The first is kept. The second is dropped.

**Do not re-derive this.** A future reader who notices that a single flagged row is simpler is rediscovering an option that has now been rejected twice, once on its merits and once on its consequences. If that reasoning appears in a later piece, this section is the answer to it.

---

## Governing owner decisions (2026-08-25)

1. Tapping "+" creates a draft immediately. A draft is a row in `mushroom_log_entries` with `isDraft` set and a real id from creation.
2. **A draft never appears in the log.** Unsaved work is held in a **Drafts section**.
3. **Save** commits. The draft's content becomes the committed entry.
4. **Cancel** discards the draft and reverts to the last saved state. It remains the only exit that throws anything away. The owner's reason, in his words: the Save button is there to finish and confirm whatever was changed.
5. **Incidental exit** (tab switch, backgrounding, back arrow) **persists the draft. It does not commit to the log.** This corrects the L4b dispatch line stating that the half-finished entry sits in the log to finish or delete. That line was wrong.
6. **An interrupted edit must never destroy the last committed save.** After a process kill mid-edit, both the last saved version and the in-progress draft exist on disk.
7. Crash recovery is **per entry**, not one bulk prompt. The owner's reason: seeing entries survive one by one is reassuring.
8. Per-keystroke disk writes are **retained**, and they land on the **draft row only**. The committed row is not written until Save. This preserves the crash durability your mechanism decision 1 was reaching for and removes the overwrite it introduced.

Decisions 1 through 4 and 7 are the owner's original scoping answers. 5, 6, and 8 are his 2026-08-25 corrections.

---

## Storage shape

`mushroom_log_entries` carries the draft state plus **a nullable pointer to the entry being drafted** (`draftsOfEntryId` or equivalent). Still one table. Still no duplicated column set, so no drift, which is the property that killed the separate-table option and is preserved here.

**Brand-new entry.** One draft row, null parent. Save flips it to committed, same id. Photo joins never move. This is the common path and it is free.

**Re-edit of a committed entry.** The committed row is **not touched**. A second row is created, flagged draft, parent pointing at the committed entry, seeded as a copy of it. Editing writes to the draft row. Save copies the fields onto the parent, repoints the photo joins, and deletes the draft row, in one transaction. Cancel deletes the draft row and nothing else.

Consequences to implement deliberately:

- The committed entry **stays visible in the log, showing its last saved values, while it is being edited.** Under the delivered implementation it vanished from the log for the duration. That behavior goes away.
- Both the draft state and the parent pointer must be excluded from every committed read.
- A draft row must never be reachable from anything that treats it as an entry: the entry list, gallery cover thumbnails, seasonal, map or region relations, export, backup.

**Migration.** Amend `MIGRATION_8_9` rather than stacking a 9 to 10 on top of it. Nothing has merged, so there is no shipped version 9 to preserve. Keep the rebuild-cycle practice and keep the v8 fixture work described below.

---

## The one place this can silently drop data

**The Save-time photo repoint on a re-edit.** Photos attached during a draft session land on the draft row's id. Save must move those joins to the parent and resolve the parent's existing ones, transactionally, before the draft row is deleted. Get this wrong and a photo reference disappears with a green suite.

Required test: attach a photo to a draft of an existing entry, save, then assert the photo is on the committed entry, the draft row is gone, no orphaned join rows remain, and the photo is still in the album. Then the Cancel counterpart: attach, cancel, assert the reference is gone from both rows and the photo is still in the album.

---

## Keep from the delivered work

- The `loadEntries()` fix in shape. Merging only `photos` onto the open editing state rather than re-deriving it is correct. Re-examine it against the new model, since `editingEntry` now corresponds to a draft row rather than to a committed one.
- The migration fixture discipline, specifically seeding a v8 fixture with a leaked draft flag to prove the migration forces it. That is exactly the standard this codebase needs and it should carry into the amended migration.
- Running every `LegacyForagerDatabaseVn` fixture rather than reasoning from the narrowed rule.
- The policy-at-the-use-case placement from the audit. `GetMushroomLogEntriesUseCase` excludes drafts; `GetOrphanedDraftEntriesUseCase` becomes the feed for the Drafts section rather than a crash-recovery special case.
- The disclosure of what you decided beyond the dispatch. Keep doing that.

---

## Still owed from the first pass, unreported

None of these appeared in the L4b report. Undone and unreported are indistinguishable from here, so report each one individually.

- The stale write-site count, in **both** stated places: `MushroomLogViewModelTest.kt:108-114` and the `onSaveErrorDismissed` comment at `MushroomLogViewModel.kt:255-260`, correct for whatever the write sites become under this dispatch.
- The doc comments the pulse listed: `MushroomLogViewModel.kt:35-42` (the class headline, whose entire premise is autosave and which is the rationale being overridden, not a stale detail), `MushroomLogDao.kt:18`, `LogEntryDetailScreen.kt:51`, `JournalTab.kt:182`, `CreateMushroomLogEntryUseCase.kt:24-27`. Note that `MushroomLogRepository.kt:40-45` was reported as still true; recheck that under the new model.
- The two tests at `MushroomLogViewModelTest.kt:79-92` and `:95-106`. Rework, and say why for each.
- **Per-class** JUnit XML results, not aggregate totals.
- **The audit as evidence rather than as a conclusion.** The search command you ran and the enumerated call sites it returned, for the DAO and for `RoomMushroomLogRepository.getAll()`. "No other file reads this table" is the claim the gate exists to test, so show the work.
- **A mutation check on the mid-edit refresh test.** Revert the merge logic and confirm the test goes red. That test is the only artifact standing between us and the named worst hazard, and a test that passes either way is worse than no test.
- Confirmation that #26 was not touched.

---

## Stop and ask, do not decide these yourself

- **Where the Drafts section lives** in the UI. A section inside Journal, a filter on the entry list, or its own destination.
- **What counts as "untouched"** for the delete-on-immediate-exit behavior. Your test `leaving without answering an untouched brand-new entry deletes it` implements a deletion that no dispatch authorized and that sits against the rule that Cancel is the only exit that discards. Whether an attached photo, or an auto-captured location, makes an entry touched is an owner call and it needs writing down either way.

---

## Gate

- Full compile green.
- Full suite green, verified against **JUnit XML per class**.
- Amended `MIGRATION_8_9` runs against a real v8 fixture. No committed entry is lost, and none is marked draft.
- **A committed entry survives a process kill mid-edit with its last saved values intact**, alongside its draft.
- **No query returns drafts where it shouldn't.** Report the audit with evidence.
- Tapping "+" and leaving immediately puts nothing in the log.
- A committed entry stays visible in the log while it is being edited.

## Tests

- Save commits; Cancel reverts to last saved state; incidental exit persists the draft without committing.
- A draft survives process death and is offered on reopening that entry, per entry.
- After a kill mid-edit of a committed entry, both the saved version and the draft are present and the user can choose.
- Cancelling a brand-new entry leaves nothing in the log and nothing in Drafts.
- The photo repoint pair described above.
- A refresh triggered mid-edit preserves uncommitted edits, with the mutation check.
- Drafts do not appear in the entry list, gallery cover thumbnails, or anywhere else the audit identifies.

**Two standing cautions, both found the hard way in this codebase, and neither addressed in the first pass:**

1. Verify a failure fixture actually fails before writing the test around it. G2 found `BitmapFactory.decodeFile` succeeds under Robolectric on a missing file and on arbitrary bytes.
2. Watch for no-op harness wiring. L4a found harnesses whose callbacks went nowhere, letting tests pass while asserting nothing.

## What NOT to do

- Do not collapse the draft and the committed entry back onto one row. See the first section.
- Do not add a second table.
- Do not let Cancel delete anything from the album. Only the reference.
- Do not block an incidental exit on a dialog.
- Do not commit an unsaved entry to the log on an incidental exit.
- Do not touch #26.

## Report back

- Commit SHA, branch head, and the diff.
- Per-class JUnit XML results.
- The query audit, with the command and the enumerated call sites.
- The Save-time photo repoint: how it is transactional, and the test output for both directions.
- Each item in "Still owed", individually.
- Confirmed vs. inferred, could-not-determine, any premise here that turned out wrong, anything you decided that this dispatch did not cover.

**Standing invitation:** report anything structural this dispatch did not anticipate. The count stands at twelve finds. The reversal described in the first section was found by reading the scoping record against the delivered report, which is a check worth running again if something here feels like it contradicts an earlier decision.
