# Report — Workstream L4b: Persisted Drafts and Save/Cancel

**Dispatch:** L4b — Persisted Drafts and Save/Cancel/Incidental-Exit (Planner, 2026-08-22)
**Branch:** `claude/l4b-persisted-drafts` @ `f824e4c` — created from `claude/task-hwj91a` @ `0e2198b`
**Date:** 2026-08-25

---

## Branch discrepancy (stop-and-ask, resolved before starting)

The dispatch names `claude/task-hwj91a`; this session's harness instructions designated
`claude/android-sdk-install-vh5iu1` (unrelated history off `main`). Per CLAUDE.md's own documented
pitfall, I stopped and asked rather than picking one silently. The owner chose: branch fresh from
`claude/task-hwj91a` and keep it separate pending review — done as `claude/l4b-persisted-drafts`.
Merging into `claude/task-hwj91a` is the owner's to do once satisfied.

**Diff:** 32 files, +1855/-94.

---

## Two mechanism decisions confirmed with the owner before writing code

The owner decisions in the dispatch settled *what* (a discriminator column, id-at-creation) but not
*how* Cancel's "revert to last saved state" is physically achieved with no second table. Confirmed
before implementation:

1. Edits keep writing to disk every field change, landing on the `isDraft = true` row rather than
   skipping persistence until an exit fires.
2. Cancel also reverts photo attach/detach made during the session, diffed against the pre-edit
   snapshot.

Both are load-bearing in the implementation below.

---

## The query audit

One raw SQL read of `mushroom_log_entries` exists in the whole codebase: `MushroomLogDao.getAllEntries()`,
consumed by exactly one repository method, `RoomMushroomLogRepository.getAll()`. `getAll()` itself
stays an unfiltered raw read — every repository-level test (`RoomMushroomLogRepositoryTest`, all five
pre-existing migration tests) calls it directly and expects every row regardless of draft state, so
filtering there would have touched ~20 tests for no reason. The draft/committed policy decision lives
at exactly two call sites instead:

- `GetMushroomLogEntriesUseCase` — now excludes `isDraft` rows (feeds `MushroomLogUiState.entries`,
  the journal list, the gallery's cover thumbnails).
- `GetOrphanedDraftEntriesUseCase` (new) — the complement, feeding `MushroomLogUiState.draftEntries`
  for crash recovery.

No other file reads this table. `PhotoGalleryScreen`'s own listing (a different table,
`log_photos`/`log_entry_photos`) is unaffected by draft state by design — a photo's gallery presence
never depended on any entry's commit state; gallery ownership already made that true before this
dispatch.

---

## The `loadEntries()` hazard

Resolved by no longer deriving `editingEntry` from a fresh committed-only read at all (that lookup
would always miss a currently-open draft). It now merges in only `photos` — the one field G3's
refresh existed to fix — from either `entries` or `draftEntries`, onto whatever `editingEntry`
currently holds, leaving every other field (in-progress typing) untouched. Covered by a new test,
`a refresh triggered mid-edit preserves uncommitted edits`, driving `loadEntries()` directly
mid-session.

---

## Crash recovery

Per-entry, not bulk: `draftEntries` — everything `isDraft = true` at a fresh `init` (the only way one
can exist there under normal operation is a crash, since every clean exit resolves its own draft) —
renders with a "Draft" badge in both `LogGalleryScreen` and `LogEntryListScreen`, and opening one
reinstates it directly into the edit form.

**No distinction is made between a crash-orphaned brand-new entry and a crash-orphaned re-edit of an
existing one** — both re-open with their *current* (crash-time) content as the new baseline, since the
pre-session snapshot for the latter was only ever held in the now-gone process's memory and was never
claimed to be recoverable. This is an inferred resolution, not something the dispatch specified; it's
non-destructive (nothing is ever deleted except via the existing explicit delete action) but means
Cancel on a reinstated draft can't revert further back than the resume point.

---

## Gate

- **Compile:** green (`compileDebugKotlin`, `compileDebugUnitTestKotlin`).
- **Full suite:** green, verified against JUnit XML directly (not the build log) — 97 test classes,
  678 tests, 0 failures, 0 errors.
- **`MIGRATION_8_9` against a real v8 fixture:** new `MushroomLogDraftMigrationTest`/
  `LegacyForagerDatabaseV8`, deliberately seeded with a leaked `isDraft = true` to prove the migration
  forces every pre-existing row to `false` regardless.
- **Legacy-fixture pitfall:** checked by running every `LegacyForagerDatabaseVn` (V4–V8) test with
  `MIGRATION_8_9` appended, not assumed — none needed the drop-index/drop-column treatment, because
  this migration follows the rebuild pattern (explicit column lists) rather than `ADD COLUMN`, and
  every V4–V7 fixture passes through `MIGRATION_6_7`'s own rebuild first, which drops the leaked
  column before this one ever runs.
- **Tapping "+" and leaving immediately:** covered by
  `leaving without answering an untouched brand-new entry deletes it`.

---

## What was decided that the dispatch didn't cover (disclosed, not silent)

- Field name `isDraft` (not "uncommitted") on both entity and domain model — deliberately documented
  as unrelated to `LogSyncState.Draft` to avoid the two being confused.
- Re-editing an *already-committed* entry also flips `isDraft = true` for the duration (per the
  owner's per-keystroke-flagged-draft answer), meaning it transiently disappears from the committed
  list while being edited. Not tested against a simultaneous list+form view because no such layout
  exists — `LogPanel`/`JournalTab` both show list *or* form, never both.
- Cancel always closes the form (returns to the gallery/list) whether it deleted or restored, rather
  than returning to a report view — kept the exit's two outcomes (discard vs. restore) uniform in
  navigation. Save is the only exit that returns to the report.
- The back arrow's behavior changed from "toggle to report" to "incidental-exit auto-save + close" —
  an intentional consequence of grouping it with tab-switch/backgrounding, not an oversight. Two
  pre-existing tests (`JournalTabTest`, `AvailabilityScreenBackNavigationTest`) asserted the old
  behavior and were updated with comments explaining why.
- `MushroomLogRepository.save()`'s doc comment claiming "autosaved on every keystroke never rewrites
  the photo set" turned out to **stay true** rather than going stale as the scoping pulse predicted —
  the design kept per-keystroke writes as infrastructure, just retargeted at the draft flag, so that
  specific sentence didn't need correcting.

---

## Confirmed vs. inferred

Confirmed by reading: schema/migration shape, DAO/repository/use-case wiring, every UI call site
touched, all pre-existing tests' assumptions. Inferred and disclosed above: crash-recovery baseline
semantics, Cancel's navigation target, back-arrow reclassification. No premise in the dispatch itself
turned out wrong.
