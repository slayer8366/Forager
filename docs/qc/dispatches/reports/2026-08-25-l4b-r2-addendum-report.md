# Report — Workstream L4b-R2: Addendum to L4b-R

**Dispatch:** L4b-R2 — addendum to L4b-R (Planner, 2026-08-25)
**Branch:** `claude/l4b-persisted-drafts` — continues from `5986df1` (the L4b-R commit this addendum
corrects), no new branch.
**Date:** 2026-08-25

---

## §1 — The `LogPanel` discard offer, made a real requirement

**Confirmed wrong, fixed, not re-litigated.** The L4b-R report classified `LogPanel`'s raw,
unwrapped `onLeaveLogEntryEditingIncidentally` as a disclosed scoping gap. The owner's ruling is
correct: the discard offer was specified for every in-app exit path, the medium/expanded drawer is
a live window (not the backgrounding carve-out), and "only reachable on wide window classes" is a
frequency argument, not a scope argument. Fixed, not re-scoped:

- The wrapped callback (`leaveLogEntryEditingOfferingDiscard`) and its `SnackbarHostState` are now
  built **once**, hoisted to `AvailabilityScreen`'s own top level (`AvailabilityScreen.kt`, just
  before `drawerSheetContent`'s declaration) instead of being declared a second time inside
  `compactMainScaffold`. `DrawerPanel.Log`'s `LogPanel` call site and the compact bottom nav's
  `JournalTab`/tab-switch call sites all reference this one instance — "every in-app exit offers
  discard" is one fact about one callback again, the property the original report's own reasoning
  named but didn't actually hold to for this caller.
- `PermanentNavigationDrawer`'s own drawer sheet (medium/expanded's only host for `LogPanel`) gained
  a `SnackbarHost` bound to that same state, docked to the bottom of the sheet itself — the drawer
  sheet is `DrawerPanel.Log`'s own visual area, so the offer surfaces where the edit session actually
  was, not in `mainScaffold`'s separate search/results pane beside it.
- Backgrounding is unchanged: `compactMainScaffold`'s `ON_STOP` `DisposableEffect` still calls the
  **raw** callback directly, on every window class — that carve-out was never in question.

**New test, medium/expanded window class, driving the real drawer:**
`AvailabilityScreenAdaptiveLayoutTest.kt`'s `AvailabilityScreenWideWindowLayoutTest` gained
`` `leaving a re-opened log entry's edit form incidentally on a medium window offers a Discard
Snackbar` ``: opens the drawer's "Mushroom Log" entry (shown permanently at this width), opens an
existing entry (a re-edit, not a brand-new draft, so the fix is proven against the harder case),
taps the form's own back arrow ("Back to your log"), and asserts "Saved to Drafts"/"Discard" appear.

**Mutation-checked**: reverted the `DrawerPanel.Log` call site back to the raw callback — the test
failed with `ComposeTimeoutException` (the Snackbar text never appeared, since nothing calls
`showSnackbar` on the raw path) — then restored the fix and reran to confirm green. This is the same
rigor bar this branch's earlier mutation checks used, applied to a fix this addendum itself required.

---

## §2 — The four items, individually, by file and current line

The instruction was explicit: report each individually, with current text, not as a summary
sentence — because a prior summary sentence is exactly what went wrong twice already. All four
turned out to already be fixed — not by a dedicated doc-comment pass, but as a side effect of the
full `MushroomLogViewModel`/`MushroomLogViewModelTest` rewrite that shipped in the L4b-R commit
(`5986df1`) itself. That rewrite touched these exact spots; the L4b-R report's own "doc comments
named by the dispatch" section simply never checked or mentioned them, which is the actual gap —
not incorrect code, an incomplete report. Verified here by diffing the pre-`5986df1` blob against
the current file for each one, not by re-reading the current file alone and assuming it was always
this way.

**1. `MushroomLogViewModel.kt:38-45`** (class-level doc, the "## Standalone drafts" section).
Before `5986df1` this read `## Persisted drafts, not autosave-always-commits (Workstream L4b, owner
decision 2026-08-22)`, opening with *"Autosave-on-every-keystroke does not go away as
*infrastructure* … every write while an entry is open for editing lands with
`MushroomLogEntry.isDraft` `true`, and the entry is only visible via `GetMushroomLogEntriesUseCase`
… once one of the three exits below resolves it. Storage shape is a single discriminator column,
not a second table…"* — exactly "the entire premise is autosave[-always-commits, single-flag
storage]" the addendum names. **Current text** (lines 38-45):

> `## Standalone drafts (Workstream L4b, owner decision 2026-08-22; corrected 2026-08-25, L4b-R)`
>
> A draft is a **separate row**, not the committed entry itself wearing a flag — the first L4b pass
> shipped the single-row shape and it was rejected on its consequences, not just its merits: it let
> an interrupted edit overwrite a committed entry in place, and made the committed entry vanish from
> the log for the entire time it was being edited. See `MushroomLogEntry.draftOfEntryId`'s own doc
> comment for the storage shape this drives.

Already corrected; the rationale itself (not just a detail) was replaced. No further change made.

**2. `CreateMushroomLogEntryUseCase.kt:19-25`** (its own doc, "id assigned here" paragraph). Before
`5986df1` this read: *"The id is assigned here, before the entry is ever committed — Workstream
L4b's own 'draft identity' decision — so `log_entry_photos` cross-references work unchanged for a
photo pulled into a still-uncommitted entry: the id a photo attaches to today is the same id the
entry commits under later, never reassigned."* — stated as a universal property, which owner
decision #6 (repointing a re-edit's draft's photo references onto its parent) directly contradicts
for the re-edit case. **Current text** (lines 19-25):

> The id is assigned here, before the entry is ever committed — Workstream L4b's own "draft
> identity" decision, kept by L4b-R's standalone-draft correction (2026-08-25) specifically for
> *this* case: a brand-new entry's `MushroomLogEntry.draftOfEntryId` is `null`, so
> `CommitDraftEntryUseCase` commits it in place, same id — a photo pulled into it here attaches
> under the id it will keep forever, no repoint ever needed. This is the one path where that holds;
> a *re-edit's* draft (see `StartEditingLogEntryUseCase`) gets a fresh id of its own, and Save
> repoints its photo references onto the parent it's a draft of.

Already corrected to scope the claim to the one case where it actually holds. No further change.

**3a. `MushroomLogViewModel.kt:430-432`** (`onSaveErrorDismissed`'s doc, the write-site count).
Before `5986df1`: *"the nine write sites above (start/edit/save/cancel/delete/add photo/remove
photo/pull photo/delete gallery photo)"* — nine, with no `onStartEditingEntry`, which didn't exist
as a write site before this dispatch. **Current text** (lines 430-432):

> …the ten write sites above (start/start-editing/edit/save/cancel/delete/add photo/remove
> photo/pull photo/delete gallery photo) cover the "next successful save" half themselves.

Verified by direct count against the current file: ten call sites clear `saveErrorMessage = null`
on success (`onStartNewEntry`, `onStartEditingEntry`, `onEntryEdited`, `onSaveEntry`,
`onCancelEditing`, `onDeleteEntry`, `onAddPhoto`, `onRemovePhoto`, `onPullPhoto`,
`onDeleteGalleryPhoto`) — `onLeaveEditingIncidentally` does **not** clear it, correctly excluded.
Ten matches ten; already correct.

**3b. `MushroomLogViewModelTest.kt:139`** (the matching count in the test file's own doc
comment). Same "nine" → "ten" correction, same reason, already present:

> …`saveErrorMessage` is set by ten different write sites (start/start-editing/edit/save/cancel/
> delete/add photo/remove photo/pull photo/delete gallery photo), not one…

Already correct, same verification as 3a.

**4. `MushroomLogViewModelTest.kt:88-94` and `:117`** (the two tests exercising immediate-write
behavior — `` `a failed save sets saveErrorMessage, and the next successful save clears it` `` and
`` `onSaveErrorDismissed clears saveErrorMessage` ``). Before `5986df1`, both called
`vm.onEntryEdited(entry.copy(...))` directly on the committed `entry` fixture, with no
`onOpenEntry`/`onStartEditingEntry` first — under the standalone-draft model this would write onto
(and thus mutate) the committed row in place, the exact bug L4b-R exists to fix, making these tests
assert a since-rejected behavior. **Current text and rework, reported individually as asked:**

- `` `a failed save sets saveErrorMessage, and the next successful save clears it` `` (line 96) now
  opens with `vm.onOpenEntry(entry.id); vm.onStartEditingEntry()`, asserts
  `` draft.id != entry.id `` explicitly, and calls `onEntryEdited` on the resulting `draft`, never
  on `entry` itself. Its own doc comment (lines 88-94) states why:
  > Reworked for Workstream L4b-R (was: called `onEntryEdited` directly on the committed `entry`
  > itself). Under the standalone-draft model that would overwrite the committed row in place —
  > exactly the bug this dispatch corrects — so every real edit session now goes through
  > `MushroomLogViewModel.onStartEditingEntry` first, which creates the separate draft row
  > `MushroomLogViewModel.onEntryEdited` actually writes to.
- `` `onSaveErrorDismissed clears saveErrorMessage` `` (line 119) has the identical rework, with its
  doc comment (line 117) pointing at the one above: `` Reworked for the same reason as the test
  above — see its own doc comment. ``

Both already reworked, both already say why (the second by reference rather than restating it,
which still satisfies "say why" — it names the actual reason, not just "see above" with nothing to
find there). No further change made.

---

## §3 — Does the photos-only merge protect anything the list-search fix doesn't?

**Answer: yes.** It protects the in-flight window between a keystroke's own local state update and
that same keystroke's write actually landing in the repository — exactly the hypothesis the
dispatch offered.

**Why the discarded weaker mutation didn't discriminate:** `MushroomLogViewModel.onEntryEdited`
updates `_uiState` (`editingEntry = updated`) **synchronously**, then launches a coroutine to
persist it. The discarded mutation's own test called `advanceUntilIdle()` after `onEntryEdited`,
which drains that coroutine to completion before `loadEntries()` ever runs — so by the time the
merge's list-search executes, the fake repository already holds the new text, and a wholesale
replace sourced from the *correct* list produces the same visible value as the photos-only merge.
That test could only ever prove the list-search half of the fix.

**The new test closes that gap by making the write actually still be in flight.** Added a
controllable `saveGate: CompletableDeferred<Unit>?` to `FakeMushroomLogRepository` (`null` by
default — every other test's `save()` is unaffected) that `save()` awaits before writing anything.
The new test — `` `loadEntries racing ahead of a keystroke's own not-yet-landed write still shows
the freshly typed field` `` — holds the gate closed, calls `onEntryEdited` (the synchronous local
update lands; its own write coroutine suspends on the gate, never reaching the repository), then
calls `loadEntries()` and `advanceUntilIdle()`. The repository is asserted to still hold the
*original* text (proving the race is real, not accidentally resolved), while `editingEntry` in the
UI state is asserted to hold the *freshly typed* text — which only the photos-only merge, not a
wholesale replace, can produce while the backing read is still stale.

**Mutation-checked**, same rigor as the merge's other test: reverted to the discarded wholesale
mutation (search both lists, replace `editingEntry` entirely) — this new test failed
(`expected:<typing, write not yet landed> but was:<original>`), confirming it discriminates on
merge *scope*, which the earlier test could not — then restored and reran green.

Per the dispatch's own instruction, the merge was not touched based on this finding — it was already
correct; this section only answers the question and adds the test that actually exercises the
window it protects.

---

## §4 — Housekeeping

**Migration fixture, third leaked-column instance.** `MushroomLogDraftMigrationTest`'s seeded
`legacyEntry` now also carries a leaked `draftOfEntryId = "leaked-parent-id"` (alongside the
existing leaked `isDraft = true`), and the test asserts the migrated row's `draftOfEntryId` is
`null` regardless. **Mutation-checked**: changed `MIGRATION_8_9`'s `INSERT … SELECT` to select the
leaked `isDraft`/`draftOfEntryId` columns from the source table instead of the literal `0, NULL` it
actually uses — the test failed (`AssertionError` at the `assertEquals` against
`legacyEntry.copy(isDraft = false, draftOfEntryId = null)`) — then restored the literal `0, NULL`
and reran green. `MushroomLogDraftMigrationTest`'s own class doc and `LegacyForagerDatabaseV8`'s doc
comment were both updated to describe both leaked columns, not just `isDraft`.

**Archive path, normalized.** Old path (had a space and a doubled "dispatches" segment — the
top-level folder's own name and its `dispatches/` child):

```
pulses and dispatches/dispatches/reports/
pulses and dispatches/pulses/reports/
```

New path (`git mv`, history preserved — confirmed as renames in `git status`, not delete+add):

```
docs/qc/dispatches/reports/
docs/qc/pulses/reports/
```

Under `docs/`, alongside `docs/audits/`, per the ruling that the archive travels with the work and
reaches `main` by the normal merge path with no separate home needed. No internal reference to the
old path existed in either archived report or elsewhere in the tracked tree (checked by grep before
and after the move) — nothing else needed updating as a result of the move itself.

---

## §5 — Record correction, applied to the archived report

Corrected in `docs/qc/dispatches/reports/2026-08-25-l4b-r-standalone-drafts-report.md` itself (the
audit record), not just here: the sentence reading "removed entirely is *not* what was chosen" is
replaced with the owner's actual words and an explanation that "remove it entirely" and "offer a
discard prompt that defaults to keeping the draft" are the same instruction (baseline behavior plus
its override), not two conflicting readings — so the correction is to that report's *prose*, not to
any code, since the implementation already matched either reading.

The same archived report's §1-adjacent misclassification (the `LogPanel` "scoping gap" framing) was
also corrected in place, cross-referencing this report's §1 fix, rather than left standing alongside
a contradicting current implementation.

---

## Gate

- **Compile:** green (`compileDebugKotlin`, `compileDebugUnitTestKotlin`).
- **Full suite, verified per class against JUnit XML directly:**

  ```
  TOTAL classes=97 tests=686 failures=0 errors=0 skipped=0
  ```

  (686 = L4b-R's own 684 plus two new tests this addendum adds: the wide-window discard-Snackbar
  test in §1, and the in-flight-write race test in §3.)
- **Discard offer on every in-app exit path, every window class, backgrounding excepted:** true by
  construction now (one shared callback, one shared host per window class) and covered by a
  mutation-checked test on the medium/expanded window class specifically (§1).
- **Each of the four §2 items reported individually, by file and current line:** done above.
- **§3 answered either way:** answered yes, with a mutation-checked test added.

---

## Confirmed vs. inferred vs. decided beyond this dispatch

**Confirmed by reading and diffing:** every file this addendum touches, against both its current
state and its pre-`5986df1` state (to establish what was and wasn't already fixed, rather than
assuming either way); the exact write-site count by direct enumeration; the archive move's
completeness by grepping the whole tracked tree for the old path both before and after.

**Inferred:** none new — every item in this addendum was either a direct owner ruling (§1, §5) or a
verifiable fact (§2's already-fixed status, §3's race, §4's leaked column and path move).

**Decided beyond this dispatch, disclosed:** the exact wording of §1's new test name and doc
comments, and the exact shape of §3's `saveGate` mechanism (a `CompletableDeferred` rather than some
other suspension primitive) — implementation detail, not a design decision the dispatch left open.

**No premise in this dispatch turned out wrong.** Every item it flagged was real: §1 was a genuine
unmet requirement, all four §2 items were genuinely unaddressed in both prior reports (even though
already fixed in code), §3's race is real and was previously untested, and the migration fixture's
leaked-column pattern really is three-for-three now.
