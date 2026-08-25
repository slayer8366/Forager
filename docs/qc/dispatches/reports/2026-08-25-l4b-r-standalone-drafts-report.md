# Report — Workstream L4b-R: Standalone Drafts Correction

**Dispatch:** L4b-R — correction to Workstream L4b (Planner, 2026-08-25)
**Branch:** `claude/l4b-persisted-drafts` @ (this commit) — continues directly from L4b's own commit
`f824e4c`, no new branch (nothing from L4b shipped/merged, so there is one history to amend, not two).
**Date:** 2026-08-25

---

## What L4b-R corrects, and why

The first L4b pass shipped a single-row-with-a-flag design: an entry being edited flipped its own
`isDraft` column to `true` in place. That had two consequences the owner rejected outright rather
than accepting as a tradeoff:

1. A crash or interruption mid-edit left the *committed* entry itself sitting in the mutated,
   half-edited state — there was no separate "last saved" row to fall back to.
2. A committed entry vanished from the log/gallery for the entire time it was open for editing,
   since the same row it lived as was the one now flagged `isDraft = true` and filtered out.

L4b-R's fix is structural, not a patch: a draft is now a **separate row**, linked to the committed
entry it drafts (when there is one) via a nullable `draftOfEntryId` parent pointer. The committed
row is never touched during an edit session — it stays visible with its last-saved values in
`entries` throughout — and only [`commitDraft`](../../../app/src/main/java/com/forager/app/data/local/MushroomLogDao.kt)'s
one transaction ever writes the edited content onto it, on Save.

---

## Two stop-and-ask items, resolved by the owner before implementation

1. **Where the Drafts UI lives** — a filter/toggle on the existing entry list/gallery ("Log" /
   "Drafts (N)"), not a separate destination. Implemented identically in `LogGalleryScreen` and
   `LogEntryListScreen`.
2. **What happens to an untouched brand-new draft on incidental exit** — the owner's own words:
   remove it entirely, but offer a prompt to discard if hitting the back button; if the prompt
   isn't answered, default to saving it to Drafts, Gmail-drafts-style. **Correction (L4b-R2):** an
   earlier draft of this report characterized "removed entirely" as *not* what the owner chose,
   as if it conflicted with the discard-prompt answer below — it doesn't. Both are the same
   instruction read together: the *baseline* behavior is removal, and the discard-prompt/
   default-to-Drafts mechanism is the override that replaces it. The implementation matches
   either reading, so no code changed as a result of this correction — only this sentence, since
   the archive is the audit record and should state the decision as given, not as redescribed.
   - Clarifying follow-up: that offer must cover *every* exit path (back arrow, tab switch, any
     other in-app "leave" action) — **except** the home button / backgrounding, which must never
     attempt a prompt at all (a prompt can't survive the process being backgrounded reliably, and
     the owner was explicit it must "blow through" straight to a silent save).

Both are load-bearing in the implementation below, not just documented as intent.

---

## The standalone-row architecture

- `MushroomLogEntryEntity`/`MushroomLogEntry` gained `draftOfEntryId: String?` (`null` = a
  brand-new entry's own draft, or a fully committed row; non-null = a draft that will replace the
  entry with that id on Save).
- `MIGRATION_8_9` — amended in place, not superseded by a new `MIGRATION_9_10`. Nothing from L4b
  ever shipped or merged, so there is one migration history to get right, not two to reconcile;
  amending in place is correct here specifically because it hasn't left this branch, per CLAUDE.md's
  own migration-collision pitfall (a *merged* migration is never amended this way).
- `StartEditingLogEntryUseCase` (new) — the actual "begin editing" action. No-op if the row opened
  is already a draft; otherwise creates a new draft row (fresh id, `draftOfEntryId` = the committed
  row's id) and returns it as the one to edit. The committed row is never written to.
- `CommitDraftEntryUseCase` (new) + `MushroomLogDao.commitDraft` — Save. One `@Transaction`:
  upserts the committed content under the parent's id, and — only when the draft's id differs from
  the parent's — repoints the draft's own `log_entry_photos` cross-reference rows onto the parent
  (merging, never duplicating, via the cross-ref table's composite primary key) before deleting the
  now-empty draft row and its stale cross-refs.
- Cancel (`onCancelEditing`) — deletes the draft row and its cross-refs outright via the existing
  `delete()`/`deleteEntryAndCrossRefs` path. No snapshot-diffing needed anymore: a draft's photo
  references live only on its own row, so deleting the row discards them for free.

This is a substantial simplification versus L4b v1's in-memory `editingEntrySnapshot`/
`revertPhotosToSnapshot()` machinery, both removed entirely — the standalone row *is* the snapshot.

---

## Incidental exit and the discard offer

`onLeaveEditingIncidentally()` is synchronous and unconditional: it never commits, never deletes,
just closes the form — the draft (already durably persisted by per-keystroke writes) surfaces in
the Drafts filter until resolved. The dispatch's own constraint ("never block an incidental exit on
a dialog") and the owner's ask ("offer a discard prompt") are reconciled by ordering, not by a gate:
the exit always proceeds first, and only afterward does a dismissible Snackbar ("Saved to
Drafts" / "Discard") appear as a pure afterthought with no bearing on whether the exit already
happened.

That Snackbar-wrapping (`leaveLogEntryEditingOfferingDiscard` in `AvailabilityScreen.kt`) is built
once and shared across every in-app exit path — `JournalTab`'s `BackHandler`,
`LogEntryDetailScreen`'s own back arrow (routed through `JournalTab`'s `onBack`), and the compact
scaffold's tab-switch handler — rather than three separate implementations, so "every exit but
backgrounding offers it" is one fact about one callback, not three facts to keep in sync.
**Correction (L4b-R2):** this report originally classified `LogPanel`'s call site as a disclosed
exception — wired to the *raw*, unwrapped `onLeaveLogEntryEditingIncidentally`, with no Snackbar,
on the reasoning that the drawer layout (medium/expanded window) had no snackbar host of its own
convenient to this flow. The owner ruled that classification wrong: the discard offer was specified
to cover every in-app exit path, the drawer is a live window (not the backgrounding carve-out), and
reachability-based mitigation ("only on wide window classes") is an argument about how often a gap
is hit, not about whether the requirement was met. Fixed in L4b-R2: `DrawerPanel.Log`'s `LogPanel`
now shares the exact same `leaveLogEntryEditingOfferingDiscard` callback as the compact bottom
nav's `JournalTab` (hoisted to this function's own top level in `AvailabilityScreen.kt`, rather than
declared twice), and `PermanentNavigationDrawer`'s own drawer sheet gained a `SnackbarHost` docked
to its bottom to show it — see the L4b-R2 report for the mutation-checked test proving this.

Backgrounding is wired separately and deliberately to the **raw** callback: `AvailabilityScreen`'s
`ON_STOP` `DisposableEffect` calls `latestOnLeaveEditingIncidentally()` directly, never the
Snackbar-wrapped version, so home/backgrounding always resolves straight to a silent save with no
prompt attempt of any kind — exactly the owner's clarification.

---

## The query audit

```
$ grep -rn "getAllEntries()" app/src/main/java
app/src/main/java/com/forager/app/data/repository/RoomMushroomLogRepository.kt:59:        dao.getAllEntries().map { entity ->
app/src/main/java/com/forager/app/data/local/MushroomLogDao.kt:27:    abstract suspend fun getAllEntries(): List<MushroomLogEntryEntity>

$ grep -rn "\.getAll()" app/src/main/java | grep -i mushroom
app/src/main/java/com/forager/app/domain/GetDraftEntriesUseCase.kt:26:    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
app/src/main/java/com/forager/app/domain/GetMushroomLogEntriesUseCase.kt:23:    suspend operator fun invoke(): Result<List<MushroomLogEntry>> = repository.getAll().map { entries ->
```

Unchanged from L4b v1's own audit conclusion, re-verified for L4b-R's shape: exactly one raw,
unfiltered read of `mushroom_log_entries` exists (`MushroomLogDao.getAllEntries()`, via
`RoomMushroomLogRepository.getAll()`), and exactly two production call sites consume it —
`GetMushroomLogEntriesUseCase` (excludes `isDraft` rows: feeds `entries`, the journal list, gallery
cover thumbnails) and `GetDraftEntriesUseCase` (the complement: feeds `draftEntries`, the new
Drafts filter). No third call site exists that could leak a draft into the committed view or vice
versa.

The new DAO surface this dispatch added — `getCrossRefsForEntry`/`commitDraft` — has exactly one
call site each, both inside `MushroomLogDao.commitDraft` itself or its one caller
(`RoomMushroomLogRepository.commitDraft`, called only from `CommitDraftEntryUseCase`):

```
$ grep -rn "\.commitDraft(" app/src/main/java
app/src/main/java/com/forager/app/data/repository/RoomMushroomLogRepository.kt:81:        dao.commitDraft(committedEntity = committed.toEntity(), draftId = draftId)
app/src/main/java/com/forager/app/domain/CommitDraftEntryUseCase.kt:19:        return repository.commitDraft(draftId = draft.id, committed = committed).map { committed }

$ grep -rn "getCrossRefsForEntry(" app/src/main/java
app/src/main/java/com/forager/app/data/local/MushroomLogDao.kt:37:    abstract suspend fun getCrossRefsForEntry(entryId: String): List<LogEntryPhotoCrossRef>
app/src/main/java/com/forager/app/data/local/MushroomLogDao.kt:89:            getCrossRefsForEntry(draftId).forEach { crossRef ->
```

No query returns drafts where they shouldn't, and drafts do not appear in the entry list, gallery
cover thumbnails, or anywhere else this audit reaches.

---

## Save-time photo repoint — transactionality and both directions tested

`MushroomLogDao.commitDraft` is one `@Transaction`. When the draft's id differs from the committed
id (a re-edit, not a brand-new entry's first save), it: reads every cross-ref row keyed to the
draft's id, re-inserts each under the parent's id (a no-op for any the parent already had, since
the composite primary key makes a repeat insert idempotent), then deletes the draft's cross-refs and
the draft row itself — all inside the one transaction, so a crash partway through can't leave a
photo repointed without its draft gone, or vice versa.

Tested both directions:

- `a photo attached to a draft of an existing entry repoints onto the committed entry on Save` —
  proves the repoint actually happens and the draft row is gone afterward.
- `a photo attached to a draft of an existing entry is discarded on Cancel, never reaching the
  committed entry` — proves the counterfactual: the same setup, but Cancel instead of Save, and the
  parent's photo set is untouched.

Both pass per the JUnit XML run below.

---

## The mutation check, actually performed this time

The `loadEntries()` merge-preservation logic (the fix for the "a refresh must not discard
uncommitted edits" hazard) was mutated for real, not just asserted as done:

1. Backed up `MushroomLogViewModel.kt`.
2. Changed the merge from *"find the fresh entity in either `entries` or `draftEntries`, keep every
   field of `editingEntry` except merge in only its `photos`"* to *"find the fresh entity in
   `entries` only, and replace `editingEntry` with it wholesale (or `null` if not found)"* — the
   naive, pre-L4b-hazard-fix shape.
3. Ran only `a refresh triggered mid-edit preserves uncommitted edits` in isolation.
4. **Result: FAILED**, for the expected reason —
   `java.lang.AssertionError: a refresh must not discard uncommitted edits expected:<typing away, not saved yet> but was:<null>`.
   The mid-edit draft (searched for in `entries` only, but actually living in `draftEntries` since
   it's a re-edit's draft row) isn't found, so `editingEntry` collapses to `null` — reproducing
   exactly the "in-progress typing vanishes on refresh" hazard the real code exists to prevent.
5. Restored the file from the backup and re-ran `compileDebugKotlin`/`compileDebugUnitTestKotlin`/
   the full suite to confirm the restore was exact and nothing else regressed.

(An earlier, weaker mutation attempt — replacing the whole `editingEntry` with the fresh entity
including its photos, but still searching both lists — did *not* turn the test red: per-keystroke
autosave means the fake repository already holds the edited `notes` text by the time `loadEntries()`
re-reads it, so a wholesale replace from the *correct* list produces the same visible `notes` as the
photos-only merge. That mutation was discarded as non-discriminating and is not the one reported
above; the reported mutation attacks the actual list-search, which is the part a real regression
would plausibly get wrong.)

---

## Doc comments named by the dispatch — verified and fixed

- **`MushroomLogDao.kt`** (its class-level comment, originally flagged around line 18) — was stale
  in two ways, one pre-existing and one newly stale from L4b-R: it referenced `[attachPhoto]`/
  `[detachPhoto]`, methods that don't exist on this class (the real names are `insertCrossRef`/
  `deleteCrossRef`) — a broken doc link that predates this dispatch — and it no longer named
  `commitDraft` as a third writer to `log_entry_photos`. Rewrote to reference the real method names
  and add `commitDraft` as the bulk-repoint writer, distinct from the two per-action ones.
- **`LogEntryDetailScreen.kt`** (its class-level comment, originally flagged around line 51) — the
  "Persisted drafts" section described Cancel as sometimes "restoring an already-committed one to
  how it looked when opened" and described the back-arrow exit as "auto-saves" — both describe L4b
  v1's single-row model exactly, and both are wrong under L4b-R: Cancel never restores anything
  (the parent was never touched, so there's nothing to restore back to), and the incidental exit
  doesn't "auto-save" anything new (the draft was already durably persisted by prior keystrokes).
  Rewrote the whole section for the standalone-row model.
- **`JournalTab.kt`** (its `BackHandler` comment, originally flagged around line 182 — the file's
  line numbers shifted under the Drafts-toggle changes, so this is now a few lines earlier) — same
  "auto-save" phrasing issue as above, in a different comment describing the same exit. Reworded to
  "neither-commits-nor-discards."
- **`MushroomLogRepository.kt:40-45`**'s claim (`save()`'s doc comment on never touching
  `entry.photos`) — re-read in full for this pass: it already correctly describes the
  standalone-draft model (rewritten during the main implementation work, before this verification
  pass), explicitly noting that repointing photo references onto a parent is `commitDraft`'s job,
  never `save()`'s. No further change needed; confirmed accurate as written.

---

## PR #26 — confirmed untouched

```
$ git branch --show-current
claude/l4b-persisted-drafts
$ git status --short   # shows only this dispatch's own files
$ git log --all --oneline | grep -i "26 "
1726b90 PR #26 rework, Workstream 1 Part A: port clean-surface files onto main
...
```

This branch's own history and working tree touch none of PR #26's files or its branch; nothing was
checked out from it, merged from it, or rebased onto it during this dispatch.

---

## Gate

- **Compile:** green (`compileDebugKotlin`, `compileDebugUnitTestKotlin`), re-verified after every
  change including the doc-comment pass and the mutation-check restore.
- **Full suite, verified against JUnit XML directly, not the aggregate build log:**

  ```
  TOTAL classes=97 tests=684 failures=0 errors=0 skipped=0
  ```

  (684 = the 680 total from the main implementation pass, plus 4 new toggle-behavior tests added to
  `LogGalleryScreenTest`/`LogEntryListScreenTest` in this verification pass.)
- **The committed entry stays visible during an edit:** structural, not incidental — `entries`
  (via `GetMushroomLogEntriesUseCase`) reads only non-draft rows, and the committed row is never
  written to until `commitDraft` runs, so this is true by construction, backed by
  `Cancelling a reopened existing entry deletes only its draft row, and the committed entry was
  never touched`.
- **Photo repoint on Save, discard on Cancel:** both directions tested (see above).
- **Crash recovery for a mid-edit of a committed entry:** `after a crash mid-edit of a committed
  entry, both the committed version and its orphaned draft survive and are choosable` — seeds the
  fake repository directly with both rows (the shape a crash leaves behind) and asserts both are
  independently visible and the draft is resumable.
- **Drafts UI toggle:** `LogGalleryScreenTest`/`LogEntryListScreenTest` each gained two tests
  proving the Log tab shows only committed entries by default (no draft, no "Draft" badge) and the
  Drafts tab shows only drafts (no committed entry, no "+" add tile).
- **`loadEntries()` hazard:** mutation-checked for real this time (see above), not just asserted.
- **PR #26:** confirmed untouched (see above).

---

## Confirmed vs. inferred vs. decided beyond the dispatch

**Confirmed by reading:** the full standalone-row schema/migration diff, every DAO/repository/
use-case call site, every UI call site the toggle and discard-offer touch, the query audit's actual
grep output, both directions of the photo-repoint transaction via dedicated tests, the mutation
check's actual red/green transition, all four dispatch-named doc comments against the current code.

**Inferred, disclosed:** none new in this pass beyond what L4b v1 already disclosed (crash-recovery
baseline semantics, Cancel's navigation target) — L4b-R didn't revisit those, only the row model and
the two items the owner was asked about directly.

**Decided beyond the dispatch, disclosed:**
- The Drafts tab's empty-state copy ("No drafts. Unsaved edits show up here.") and badge/row wording
  ("Draft" / "Draft — not yet saved") — small UX text choices the dispatch left unspecified.
- ~~`LogPanel`'s discard-offer Snackbar gap, parked as a future scoping decision~~ — **corrected in
  L4b-R2**: this was an unmet requirement misclassified as a scoping gap, not a legitimate decision
  beyond the dispatch. See this file's own L4b-R2 correction above and the L4b-R2 report for the fix.

**No premise in the dispatch turned out wrong.** (The `LogPanel` item above was this report's own
misclassification, not a premise the dispatch itself got wrong — see the L4b-R2 correction.)
