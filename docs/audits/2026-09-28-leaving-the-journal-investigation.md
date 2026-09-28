# Leaving the Journal with a find or a day entry open: investigation

Intent `2026-09-28-23`, dispatch `prompts/preserved/2026-09-28-23.md` (base `ef8cb38` on
`origin/journal-redesign`, verified against the remote before starting). Branch
`leave-journal-investigation`, worktree `forager-wt/leave-journal`. Investigation only: **nothing in
production code was changed.** Each fix below waits on the owner's ruling.

The three behaviours come from the search-bar fix's report,
`docs/audits/2026-09-28-maps-search-bar-after-entry-completion-report.md`, "Flags outside scope".
All three premises held.

## How it was exercised

`app/src/test/java/com/zynergylabs/forager/app/ui/availability/LeavingTheJournalInvestigationTest.kt`,
13 tests, all passing: they pin today's behaviour.

- **Screen.** The real `AvailabilityScreen`, which is the compact scaffold with its bottom nav and its
  one "Saved to Drafts" snackbar host. It is driven by the real `MushroomLogViewModel` and
  `CartographyViewModel`.
- **Storage.** An in-memory `ForagerDatabase` with the Room repositories `AppContainer` builds, using
  direct executors as `CartographyViewModelTest` does. Photos go through a real `FilePhotoStore`
  writing under the test's `filesDir`.
- **Seed data.**
  - A committed find `find-1` on 2026-08-02, with own identification "Chanterelle". It has one album
    photo `photo-1`, and that photo's real file is at `filesDir/photos/photo-1.jpg`.
  - A committed day entry `day-1` on 2026-08-01, reading "The original account." It keeps one waypoint
    ("Creek pin"), which is stored for that day.
- **Callbacks.** The ones `MainActivity` passes, in particular `onDiscardLogDraft =
  mushroomLogViewModel::onDeleteEntry` (`MainActivity.kt:477`).
  - **Not launched here: `MainActivity` itself.** Its `AppContainer` builds on-disk databases and
    platform providers. So the callbacks are copied from its `setContent` block, one by one.
- **Leaving and returning.** A real touch at the centre of the nav item: the bottom nav in portrait
  (`w360dp-h640dp`), and the rail in one short-landscape test (`w823dp-h384dp-land`). Each touch is
  checked to land (`assertIsSelected`).
- **Assertions.** Every data assertion reads the database through the repositories
  (`getAll`, `getAllPhotos().referencingEntryIds`, `getById`) or checks the file on disk. None of them
  rests on the snackbar text alone.

Nothing needed an emulator or a phone. Every case the dispatch asked for could be exercised headless.

## Behaviour 1: "Saved to Drafts" and Discard on a committed find

### What happens

| Case | Snackbar | Draft row | What Discard does | Tests |
|---|---|---|---|---|
| Committed find, **report view**, left for Maps | "Saved to Drafts" with Discard | **None.** Only `find-1` is stored before and after. Ignored until it times out, the snackbar changes nothing. | **Deletes the committed find.** Its row is gone from `mushroom_log_entries`. `photo-1`'s reference to it is gone too (`referencingEntryIds == []`). The album photo's row stays, and so does its file on disk. There is no Undo and no confirmation. Back on the Journal, the find's tile is gone. | `viewing a committed find then leaving for Maps shows Saved to Drafts with Discard, and no draft row is created`; `viewing a committed find then leaving shows Discard, and Discard deletes the committed find's row and photo reference but not the album photo or its file`; `short landscape, viewing a committed find then leaving on the rail shows Discard, and Discard deletes the committed find's row` |
| Committed find, **editor, no change**, left | "Saved to Drafts" with Discard | Opening the editor stored a draft copy, `draft-of-find-1`. Leaving deleted it, so **nothing is in Drafts, although the snackbar says it is.** | It deletes the id of the draft that is already gone. Nothing changes: `find-1`, its photo reference and the file are all intact. | `a committed find opened in its editor and left unchanged shows Saved to Drafts with Discard, though no draft is kept, and Discard leaves the committed find untouched` |
| Committed find, **editor, changed** (own identification typed), left | "Saved to Drafts" with Discard | Stored: `draft-of-find-1`, `isDraft = true`, `draftOfEntryId = find-1`, holding the typed value. The committed row is unchanged. | Deletes only the draft. The committed find, its photo reference and the file are all intact. | `a committed find changed in its editor then left shows Saved to Drafts, the draft row is stored, and Discard deletes only the draft` |

Answers to the dispatch's questions:

- **(a)** Yes. The snackbar appears for a committed find that was only viewed.
- **(b)** Discard deletes the committed find's row and the find's reference to its photos. It does not
  delete the photos' album rows or their files.
- **(c)** No draft row is created by only viewing.

### Code path

1. `JournalTab`'s tile tap calls `onOpenEntry` (`ui/log/JournalTab.kt:438-441`), which is
   `MushroomLogViewModel.onOpenEntry` (`ui/log/MushroomLogViewModel.kt:386-394`). That sets
   `editingEntry` to the **committed** entry itself. The name "editing" covers viewing too.
2. The tab switch fires on `editingEntry != null` alone
   (`ui/availability/AvailabilityCompactScaffold.kt:457-459`). It does not look at the mode, which is
   local to `JournalTab` (`JournalTab.kt:270`). For comparison, `JournalTab`'s own
   `leaveFindEditingIfNeeded` does check the mode (`JournalTab.kt:323-325`).
3. `leaveLogEntryEditingOfferingDiscard` (`ui/availability/AvailabilityScreen.kt:1124-1136`) captures
   `discardedId = logUiState.editingEntry?.id`, which is the committed id here. It calls the leave, then
   shows the snackbar whenever that id is non-null. It does not look at `isDraft`, or at whether a draft
   exists after the leave.
4. `onLeaveEditingIncidentally` (`MushroomLogViewModel.kt:582-640`) handles each case:
   - a committed entry that is not a draft: it only closes (the `!current.isDraft -> s.draftEntries`
     branch, `:614-615`);
   - an unchanged re-edit: it deletes the draft (`:589-593`).
5. Discard calls `onDiscardLogDraft(discardedId)`, which is `MushroomLogViewModel.onDeleteEntry`
   (`MainActivity.kt:477`, `MushroomLogViewModel.kt:760-781`). The chain continues through
   `DeleteMushroomLogEntryUseCase` (`domain/DeleteMushroomLogEntryUseCase.kt:21`) and
   `RoomMushroomLogRepository.delete` (`data/repository/RoomMushroomLogRepository.kt:99-101`) to
   `MushroomLogDao.deleteEntryAndCrossRefs` (`data/local/MushroomLogDao.kt:93-96`). That deletes the
   entry's photo cross-references and then the entry row. Nothing on this path calls `PhotoStore`, so
   photo files are never touched here.

### Can data be lost

**Yes.** Suppose the user views a committed find, changes tab, and taps Discard within the Short
duration (about 4 s), perhaps reading it as "discard the draft". The committed find is then deleted at
once, with no Undo. What goes:

- its row;
- every field recorded on it;
- its attachment to its photos.

Unlike the find's own Delete (J4), this does not go through the pending-delete path
(`requestDeleteEntry`), so there is no Undo. The photos stay in the album and on disk. I did not test
whether a day entry that kept this find (a `FindDecision`) is affected; that is unverified.

The unchanged-editor case loses nothing, but its message is untrue: it says "Saved to Drafts" when
nothing was saved.

### Options for a fix (none chosen)

- **O1.1** Offer the snackbar only when the entry left behind is a draft (`editingEntry.isDraft` at the
  moment of leaving). This covers a new find and a changed re-edit. The report-view case and the
  unchanged-editor case would then show nothing.
- **O1.2** Offer it only when a draft row actually remains after the leave. This needs the leave to
  report its result: today `onLeaveEditingIncidentally` returns `Unit` and decides inside its own
  coroutine.
- **O1.3** Make the snackbar's Discard a draft-only delete, one that refuses a non-draft id, instead of
  the general `onDeleteEntry`. This could stand alone as a guard or go with O1.1 or O1.2.
- **O1.4** Do not close a viewed find on a tab switch at all (Behaviour 3's O3.2). The report-view case
  then never reaches this path. The editor cases stay as they are.
- **O1.5** Route Discard through the pending delete with Undo, as J4 does for Delete. This makes the
  damage undoable but does not stop the wrong entry being offered.

## Behaviour 2: a day entry left open in its editor comes back in its report view

### What happens

- **A committed day entry, with text typed in its editor, then Maps and back to Journal.** The entry is
  still open, but **in its report view.** The report shows the **typed text**; the original text is not
  on screen. The store still holds "The original account.", and
  `CartographyUiState.hasUnsavedChanges` is still `true`. Nothing on screen says the text is unsaved.
  Test: `a committed day entry's editor left with typed text comes back in its report view showing the
  typed text, which is not stored`.
- **From that report, the back arrow.** It closes with **no leave prompt** (the Discard tag does not
  exist), and Entries shows. The in-memory list now carries the typed text, and **the Entries card
  shows it.** `hasUnsavedChanges` is now `false`, while the store still holds the original.
  Reopening the entry shows the typed text again. Test: `after that return, the report's back arrow
  closes with no leave prompt, Entries shows the typed text, and the store still holds the original`.
- **From that report, Edit entry.** The editor shows the typed text, still unsaved. Save, then its
  confirm, stores it. So there is a recovery path, but only if the user goes back into the editor.
  Test: `after that return, Edit entry again shows the typed text still unsaved, and Save stores it`.
- **Keep or withhold changed: "Creek pin" withheld in the editor.** The report showed the waypoint
  before the edit. After Maps and back, it comes back in the report view **without "Creek pin"**, while
  the store still has it kept (`waypointDecisions.map { it.kept } == [true]`), and `hasUnsavedChanges`
  is `true`. Test: `a committed day entry's editor left with a waypoint withheld comes back in its
  report view without that waypoint, and the store still keeps it`.
- **A new day entry (a draft) with typed text, then Maps and back.** It comes back in its **report
  view**, so "Finish entry" is not shown, with the typed text. The draft row stores the typed text,
  because drafts autosave. No data is lost, but an unfinished draft is shown as a report. Test:
  `a new day entry, a draft, left in its editor comes back in its report view, its typed text stored as
  the draft`.

To answer the dispatch's question, unsaved edits do survive the round trip, but only in memory. They
are shown in the report view as if they were the entry.

### Code path

- `CartographyScreen`'s `var mode by remember { mutableStateOf(CartographyEntryMode.VIEW) }`
  (`ui/log/CartographyScreen.kt:171`). The screen leaves composition on the tab change, so it comes
  back as `VIEW`. The report branch is `:333-348`, and the Edit branch sets the mode at `:345`.
- The pending edit lives in the ViewModel, not the screen. For a committed entry, `persist`
  (`ui/log/CartographyViewModel.kt:595-610`) only updates `editingEntry` and sets
  `hasUnsavedChanges = true`. So the open entry, now dirty, survives the tab change, and the report
  draws it.
- `requestLeaveEntry` (`CartographyScreen.kt:201-207`) prompts only when
  `mode == CartographyEntryMode.EDIT` and the entry is dirty. In `VIEW` it calls `onCloseEntry`
  directly.
- `CartographyViewModel.onCloseEntry` (`CartographyViewModel.kt:234-254`) merges the open entry into
  `entries` and clears `hasUnsavedChanges`. It does this unconditionally: it does not check
  `hasUnsavedChanges`. Its doc comment (`:225-232`) says it merges "only when the entry is not dirty by
  the time this runs". That holds on the paths it names, but not on this one, where a dirty entry
  reaches it from the report view.

### Can data be lost

**Yes, on the path above.** After the round trip, the user sees the edit in the report and then on the
Entries card, so it looks saved. Backing out raises no prompt and marks nothing unsaved, but the store
never received the edit. The in-memory list is refreshed from the store by `loadEntries`, which runs at
ViewModel creation (`CartographyViewModel.kt:87-89`) and after some actions. Once that happens, the
edit is gone.

- **Not exercised here:** a fresh ViewModel (an app restart) showing the original text. What the tests
  do show is that the store holds the original text after the close.
- The draft case loses nothing.

### Options for a fix (none chosen)

- **O2.1** Keep the entry's mode across the tab change, so the editor comes back as the editor, with
  its Save and its leave prompt. It could be held where `entriesViewState` already is
  (`JournalScreenState`, which survives a tab change), or saveable and keyed on the entry id.
- **O2.2** Make `requestLeaveEntry` prompt on a dirty committed entry whatever the mode, so that
  backing out of the report cannot close a dirty entry silently.
- **O2.3** Make `onCloseEntry` refuse to merge a dirty entry into `entries`, so the Entries list and
  cards keep showing the stored version. That is what its doc comment already claims.
- **O2.4** Have the report view show the stored version and mark that unsaved changes are pending,
  rather than drawing the pending edit as the entry.

These can be combined; O2.1 alone leaves the `onCloseEntry` gap open for any other path that reaches it
with a dirty entry in `VIEW`.

## Behaviour 3: an open find is closed by the tab switch, even in its report view

### What happens

- **Committed find in its report view, then Maps and Journal.** The report is closed: there is no
  "Entry options" and no "Back to your log". The Finds gallery shows, with "New log entry" and the
  find's tile. `MushroomLogUiState.editingEntry` is `null`. The user also saw "Saved to Drafts" on
  leaving (Behaviour 1). Test: `a committed find open in its report view is closed by the tab switch,
  and back on Journal the Finds gallery shows`.
- **Committed find in its editor with a change, then Maps and Journal.** The editor is closed and the
  Finds gallery shows. The change is kept as the draft `draft-of-find-1`, which is in `draftEntries` and
  in the store. Test: `a committed find open in its editor with a change is closed by the tab switch,
  and back on Journal the gallery shows with the change kept as a draft`.
- **For comparison, a committed day entry in its report view, then Maps and Journal.** It is still
  open in its report view, with its text, and no snackbar was shown. Test: `for comparison, a committed
  day entry open in its report view is still open back on Journal, with no snackbar`. This matches the
  existing pins in `AvailabilityScreenBackNavigationTest` (`checkEntryKeptOnReturn`) and the fix
  `35a99ad`.

### Code path

- The tab switch calls `leaveLogEntryEditingOfferingDiscard`
  (`AvailabilityCompactScaffold.kt:457-459`), which calls `onLeaveEditingIncidentally`. That sets
  `editingEntry = null` on every branch (`MushroomLogViewModel.kt:582-640`).
- The day entry has no such call on a tab switch. `CartographyViewModel`'s `editingEntry` is left alone.

### Can data be lost

Not by the close itself. A viewed find is unchanged, and an edited find's change is kept as a draft,
which the tests confirm. The loss risk on this path is the Discard offered with it (Behaviour 1). What
the user loses is their place: they come back to the gallery, not the find.

### Options for a fix (none chosen)

- **O3.1** Leave it as it is. Finds close on a tab switch and day entries do not; the owner's ruling
  "Keep entry, fix the bar" named the day entry and did not say whether it covers finds.
- **O3.2** Keep an open find across the tab change, in both modes, as the day entry is kept.
  - This needs the tab handler to stop calling the leave for a find (`:457-459`).
  - It also needs the Maps tab's search-bar gate to treat an open find as the search-bar fix treated an
    open day entry. `isEditingJournalEntry` includes `logUiState.editingEntry`
    (`AvailabilityCompactScaffold.kt:401`), so the Maps search-bar symptom `35a99ad` fixed for day
    entries could appear for finds; that is unverified.
  - The backgrounding observer (`:388`, `:431`) would still close it on `ON_STOP`.
- **O3.3** Keep a find open only in its report view, and keep closing the editor, with its draft, as
  today. The mode is local to `JournalTab` (`JournalTab.kt:270`), so the scaffold cannot see it today.
  It would have to be lifted into `JournalScreenState` or `MushroomLogUiState` for the tab handler to
  tell the two apart.

## Full-suite run

From a cleared results directory (`rm -rf app/build/test-results/testDebugUnitTest`), then
`LC_ALL=C.UTF-8 ./gradlew --offline :app:testDebugUnitTest --continue` on this branch.

Run on `9b828da`'s tree. The only later commits on this branch are this report, which is docs only. `BUILD SUCCESSFUL`, exit 0,
no compile errors in the log, no OOM.

- **Counts, read from the JUnit XML:** 268 result files, 2206 tests, 0 failures, 0 errors, 24 skipped.
  I did not examine the 24 skipped tests. They are `@Ignore`s and assumptions already on the base, not
  added by this branch, which only adds the new class.
- **The run was fresh.** Every XML file is timestamped between 10:44:40Z and 10:47:02Z, after the
  results directory was cleared.
- **The new class:** 13/0/0/0.
- **The held flaky classes passed on this run:** `JournalPendingDeleteTest` 52/0/0/0 and
  `JournalTabTest` 17/0/0/0. One green run says nothing about their flake rate.

## D58

Before each push, a case-insensitive grep over `git diff ef8cb38`, the staged and working trees
(untracked files included), and every commit message since `ef8cb38`. It used the three phrases from
the earlier coder's check script (`forager-wt/l0b/app/build/l0b/d58.sh`). Zero hits each time the
check ran.

**The first push (`f1996a1`) went out before I ran the check.** I ran it straight after, over that
commit: zero hits. Every later push was checked before it went out.

## Decisions I made

- **The fixture.** I used a new test class, not an extension of `AvailabilityScreenBackNavigationTest`,
  because that fixture fakes the log and Cartography state with local `mutableStateOf` lambdas. Its
  `onLeaveLogEntryEditingIncidentally` fake commits the entry, and its `onDiscardLogDraft` is the
  default no-op, so it cannot show what Discard does to stored data. Doing it properly needed real
  ViewModels and storage.
- **Storage.** For the finds and day entries I chose an in-memory Room database, not the fakes from
  `JournalPendingDeleteTest`, because the dispatch asked for database and file state. For the photo
  store I chose a real `FilePhotoStore`, so that any file delete on the path would be real.
- **MainActivity's wiring, reproduced.** `MainActivity` is not launched, as explained above. If
  `MainActivity`'s wiring changes, this class will not notice.
- **The field and the choice to change.** For "a change" in the find editor I picked the own
  identification field. For "a changed keep or withhold choice" I picked withholding a waypoint. Other
  fields and other record types (finds, tracks, offline regions) were not exercised.
- **Extra cases.** I added cases the dispatch did not list, because they bear on the same questions: the
  short-landscape rail case for Behaviour 1, the new-draft day entry for Behaviour 2, and Behaviour 2's
  follow-on paths (the back arrow from the report, and Edit again).
- **Which report-view test covers which behaviour.** The report-view "Saved to Drafts" test covers
  Behaviour 1, and a separate report-view test covers Behaviour 3. They share their setup.
- **No revert checks.** I did not revert production code to prove that these tests bite. The dispatch
  pins current behaviour and forbids production changes. Each test's assertions read stored state that
  the behaviour under test produces, and each named precondition was checked against its opposite (for
  example, the draft copy exists before the unchanged leave, and the waypoint shows before the edit).
  But no mutated build was run.
- **The D58 phrases.** I took them from a previous coder's script. I did not find them in a document in
  this repository.

## Flags outside scope

- **Backgrounding closes a find that was only viewed.** The `ON_STOP` observer fires on
  `logUiState.editingEntry != null` with the Journal tab showing
  (`AvailabilityCompactScaffold.kt:388`, `:431`), so it calls `onLeaveEditingIncidentally` for a find
  in its report view too, and closes it. It shows no snackbar, so there is no Discard risk. Not
  exercised.
- **Backgrounding a dirty committed day entry after it has come back in its report view.**
  `latestIsEntryDirty` is still true, but the return prompt is drawn only inside
  `CartographyEntryEditScreen` (`CartographyScreen.kt:300-331`). What the user sees on resume in the
  report view is unverified.
- **The wide layout.** Its `LogPanel` uses the same `leaveLogEntryEditingOfferingDiscard`
  (`AvailabilityScreen.kt:1262`). It opens finds through `onOpenEntryForEditing` (`LogPanel.kt:340`),
  so the report-view case may not arise there, but a committed find that fails to start editing is left
  open as a report (`MushroomLogViewModel.kt:453-471`). Not exercised.
- **Day entries that kept a find deleted by Behaviour 1's Discard.** What happens to them is unverified.
- **`CartographyViewModel.onCloseEntry`'s doc comment** (`:225-232`) describes a guarantee the code
  does not enforce (Behaviour 2).
- **The scratchpad is shared with other sessions.** Files from other sessions were already there when
  this session started (for example `R1.log`, `revert.sh`). This session's own files are prefixed
  `lj-`; `run1.log`–`run3.log` and `tree.txt` are also this session's.
- **The held flaky tests were not touched** (`JournalPendingDeleteTest`'s album tests, `JournalTabTest`'s
  photo pull). The suite run above reports them as they came out.
