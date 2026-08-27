# Dispatch — L4c: Serialized editing state, photo race, and two form fixes

**From:** Planner
**Date:** 2026-08-25
**Basis:** Section 2 verification pulse (2026-08-25), owner device testing (2026-08-25), owner decisions of 2026-08-25
**Branch:** off `claude/task-hwj91a` **after** the L4b merge lands. Do not start this on `claude/l4b-persisted-drafts`.
**Not in scope:** the index and plan-doc updates. Those land in the merge commit itself, per the rule that an index row updates in the same commit as the thing it describes.

---

## Verify before relying

This dispatch is written from a read-only pulse, not from a fresh one, and the merge will have happened in between. Check these before starting. Any mismatch is a stop-and-report.

1. `onAddPhoto`, `onRemovePhoto`, and `onPullPhoto` in `MushroomLogViewModel.kt` still capture the entry, await the full persist, then set `editingEntry`, rather than updating state first.
2. `onDeleteGalleryPhoto`'s success path still calls `loadEntries()`.
3. `AddPhotoToLogEntryUseCase.invoke` still computes its result against the captured snapshot rather than a fresh read.
4. `docs/qc/pulses/reports/` still contains only a `.gitkeep`.

---

## 1. Serialize mutations of the editing entry

**The decision, and the reasoning that travels with it.** The pulse found a real race between a photo attach and a `loadEntries()` refresh. That race is an instance of a class: several `viewModelScope.launch` coroutines mutate one piece of state with real I/O suspension points in between and no ordering guarantee, so whichever writes last wins regardless of which the user asked for first. Patching the one pairing the pulse found leaves the family intact.

The owner's reason for taking the general fix, in his words: this is the kind of code that goes into the user experience and speaks to confidence in an app. Nobody notices when it is applied properly. When it is not, confidence is lost. **A future reader should not simplify this back into independent launches because the specific race that motivated it is no longer reproducible.**

**Required:**

- All mutations of the editing-entry state pass through one serializing path, FIFO with respect to the order the user's actions arrive. A `Mutex`, or a single-consumer channel, your call on shape.
- **The `loadEntries()` refresh participates in the same ordering.** If it stays outside, the race survives the fix. This is the single most important line in this section.
- Scope the serialization to editing-entry state. Do not put unrelated ViewModel work behind the same lock; a lock wide enough to cover everything will show up as input lag on a hot path.
- No nested acquisition. If any handler already calls another handler, that is a deadlock waiting to happen; report it rather than working around it locally.

**Flag it as it is: this is a behavior-preserving refactor on a hot path, and it is exactly the sort of change a green suite does not certify.** Timing changes under `runTest` can make an existing test pass for a new reason.

---

## 1a. The open-then-edit composability hazard (added 2026-08-25, after the first attempt surfaced it)

The first serialization attempt broke eight tests. Five share one shape, and it is a production hazard rather than a test artifact.

`onOpenEntry(id)` immediately followed by `onStartEditingEntry()`, with no yield between them, is what `LogPanel.kt` and `JournalTab.kt`'s gallery path both do today. `onStartEditingEntry`'s guard reads `_uiState.value.editingEntry` **outside the critical section**, so under contention it sees state from before `onOpenEntry`'s write landed and silently returns. This composition works today only because `Dispatchers.Main.immediate` runs an uncontended launch synchronously. Once the lock is genuinely contended by a slow write from any other editing-entry operation, a real user's tap no-ops with no error and no signal.

**Decision, owner-approved:** add one combined ViewModel method that opens and starts editing under a **single** lock acquisition, and change the two production call sites to use it instead of chaining.

**And, independently of that:** move the guard's state read inside the critical section. The combined method fixes the two call sites that exist; the guard fix covers the next one somebody adds. Both, not either.

**Rejected, with the reason recorded so it does not return as a simplification:** exempting `onOpenEntry`/`onCloseEntry` from the lock. It narrows the guarantee rather than closing the gap, and the residual race it accepts is a closed form resurrected by a slow Save's late write. Also rejected as a standalone fix: relying on FIFO enqueue order alone, since that restores correctness through the same implicit `Dispatchers.Main.immediate` ordering that concealed the defect.

**Kotlin's `Mutex` is not reentrant.** Implementing the combined method as `withLock { openEntry(); startEditing() }` where those also lock is a hard deadlock on first invocation. One critical section, calling unlocked internals.

The other three of the eight failures are the ordinary kind: newly async calls asserted against with no `advanceUntilIdle()`. Safe to fix, and **mutation-check each one after fixing it**, because adding `advanceUntilIdle()` to make an assertion pass is precisely how a test stops discriminating.

> **Correction (L4c, filed 2026-08-25):** the claim above that this composition "is what `LogPanel.kt` **and** `JournalTab.kt`'s gallery path both do today" is wrong about `JournalTab.kt`. A full recount, tracing every actual invocation of `onOpenEntry`/`onStartEditingEntry`/`onOpenEntryForEditing` from UI to ViewModel by file and line, plus `git log -p --follow` over `JournalTab.kt`'s entire history, found no version of that file — before, during, or after this dispatch — that ever calls the two in the same handler with no yield between them. `JournalTab.kt` opens a committed entry into `LogEntryReportScreen` (`onOpenEntry` alone), reopens a draft straight into edit (`onOpenEntry` alone — already a draft, so `onStartEditingEntry` would be a no-op, and the code's own comment says so), and only calls `onStartEditingEntry` alone, later, from the report screen's own "Edit" tap — a separate user action with a full screen render in between, not a chain. `LogPanel.kt` was the one real call site, and it is the one this dispatch's §1a fixed. The premise was wrong because both callbacks appearing in `JournalTab`'s parameter list was read as evidence the two calls were chained — the same failure as taking a `grep` hit for a confirmed consumer: a name present is not a call site confirmed. See the L4c report's own recount for the full file:line trace.

---

## 2. The photo race, tested

- A gated test reproducing the mirror race: hold a photo attach in flight, run `loadEntries()`, assert the open form's photo list still contains the attached photo. The `saveGate` mechanism added for the §3 test transfers directly; gate the photo path rather than `save()`.
- **Mutation-checked.** Revert the serialization, confirm this test goes red, restore, confirm green. Report the actual failure text, as the last two reports did.
- A second test asserting that two photo operations issued back to back apply in the order issued.

---

## 3. What serialization does to the existing `loadEntries()` merge

This needs an answer, not an assumption.

The photos-only merge exists because `loadEntries()` could otherwise clobber in-progress typing. The §3 test proves it protects the in-flight window between a keystroke's synchronous state update and its async write landing. **Once `loadEntries()` is serialized behind pending writes, that window may no longer exist**, which would mean the merge protects nothing and its test can no longer enter the race it was written for.

Report:

- Does the §3 in-flight test still pass, and does its mutation check still discriminate? Re-run the mutation check specifically. A test that now passes under both the real merge and the wholesale replace is a vacuous test, and this project has shipped three of those.
- Given the answer, does the photos-only merge still protect anything?

**Do not remove or simplify the merge on your own judgement.** Report the finding. If it turns out to be redundant, that is a decision with a rationale attached, made deliberately, not a cleanup.

---

## 4. Photo strip layout in the entry form

Owner-reported from the device. The attached thumbnail sits at the lower left of the Photos section with a large empty region above it.

**Required behavior:** the photo strip sits at the top of the Photos section, immediately under the Camera / Gallery / From Album row, with the empty space closed. One photo renders centered. Multiple photos run left to right as a centered group.

Two things first:

- **Report what the container currently is before changing it.** The screenshot shows what looks like a scrollbar track along the right edge of the empty region, which suggests a fixed-height scrollable container with the thumbnail sitting at its bottom rather than padding on the thumbnail itself. If that is what it is, the fix is the container, and adjusting padding would leave the real cause in place.
- **Name the file.** I have not seen where the Photos section is composed and will not guess at a path. State it in your report.

The owner judges layout in use, so a short description of the result at one, two, and four photos is worth more here than a long rationale.

---

## 5. Set Location in the entry form

The form currently renders "No location set." as static text with no affordance. The owner wants a Set Location button there, opening the same centre-pin picker the "+" flow uses.

**Standing rule, carried in full because it has a reason that must survive:** location is set by panning the map under a marker fixed at screen centre, with OK and Cancel. No long-press, and no draggable marker. This is an accessibility decision, not a style one. Long-press is unreliable for thicker fingers, and dragging a marker has the same defect, because the finger covers the target. Reuse the existing picker; do not build a second one.

**Interaction with the draft model, and it needs a test each way:**

- Setting a location during an edit writes to the **draft row**, never to the committed entry.
- Cancel reverts a location set during the session, the same as any other field.
- An incidental exit persists it into the draft.

**Stop and ask before building, one question for the owner:** was the absence of a location affordance in the edit form a deliberate L4a scope line, or an omission? L4a delivered the Add Location button and deleted the old picker, and scoped the button to creation. The answer decides whether this is a fix or a small feature, and the owner should get that framing rather than have it decided here.

**One forward consequence to record rather than to solve.** L6 fires tile capture when an entry acquires or changes a location. This adds a second place where that can happen. Do not build a signal for a piece that does not exist yet. **Do** enumerate, in your report, every place in the app where an entry's location can now be set or changed, so L6's scoping starts from a real list rather than an assumption.

---

## 6. Housekeeping

- `AvailabilityScreen.kt:628-629`'s doc comment on `drawerSheetContent` claims it is shared between the compact `ModalNavigationDrawer` and the `PermanentNavigationDrawer`. The pulse found it has exactly one call site, the permanent drawer, and that compact uses a different composable. Correct it.
- File the Section 2 pulse report into `docs/qc/pulses/reports/`, which currently holds only a `.gitkeep`. Dispatch reports have been archived and pulse reviews have not, which means the investigation that shapes a dispatch leaves no trace while its execution does. Going forward, a pulse report is archived the same way a dispatch report is.

---

## Gate

- Compile green. Full suite green, verified per class against JUnit XML.
- The mirror race test passes and is mutation-checked, with the failure text reported.
- The §3 mutation check re-run, and its result reported either way.
- No deadlock: report how you established that, not just that the suite passed.
- The two form fixes verified on a real layout, with the photo strip described at one, two, and four photos.
- Setting a location during an edit writes to the draft and is reverted by Cancel, both tested.

## Report back

- Commit SHA, branch head, diff.
- Per-class JUnit XML.
- §1: the serialization shape chosen and why, and confirmation that `loadEntries()` is inside it.
- §3: the answer, with the re-run mutation check.
- §4: the container's actual cause, and the file.
- §5: every place an entry's location can be set, enumerated.
- Confirmed vs. inferred, could-not-determine, any premise above that turned out wrong, anything decided that this dispatch did not cover.

## What NOT to do

- Do not remove or simplify the `loadEntries()` merge on your own judgement.
- Do not build a second location picker, and do not add a long-press or a draggable marker.
- Do not start §5's implementation before the owner answers the scope question.
- Do not put unrelated ViewModel work behind the serialization lock.
- Do not name a file path in your report that you have not opened.
- Do not touch #26.

**Standing invitation:** report anything structural this dispatch did not ask about. The last three pulses turned up the legacy-fixture pitfall in a shape the narrowed rule said was impossible, contract consumers reachable only through a parameter name, an empty pulse archive, and a doc comment describing sharing that does not exist. It is a real request.
