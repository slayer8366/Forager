# Report — Workstream L4c: Serialized Editing State, Photo Race, Two Form Fixes

**Dispatch:** L4c (Planner, 2026-08-25) — `dispatches/2026-08-25-l4c-serialized-editing-state-dispatch.md`
**Branch:** `claude/l4c-serialized-editing-state`, off `claude/task-hwj91a` after the L4b merge (`1305a4b`)
**Commits:** `e97985f`, `008439b`, `a1072aa`, `bb2b59a`, `5c50368`, `641eb30`, `c2a291a`, `ab67032`, and this report's own commit
**Full suite:** 97 classes, 694 tests, 0 failures, 0 errors, verified per class against JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

---

## §1 / §1a — Serialization

One `Mutex` (`editingEntryMutex` in `MushroomLogViewModel`) serializes every mutation of `editingEntry`, FIFO. `loadEntries()`'s own critical section is inside it — confirmed by code inspection (`loadEntries()`'s `getEntries().fold(...)` body is wrapped in `editingEntryMutex.withLock`) and by the §3 tests below, which depend on `loadEntries()` genuinely queuing behind other holders. Scope is deliberately narrow: `onEntryEdited`'s immediate local reflection, `loadGalleryPhotos`, and `onSaveErrorDismissed` stay outside, since none of them re-touches `editingEntry` from a stale read.

The `onOpenEntry`+`onStartEditingEntry` composability hazard is fixed two ways, per the owner's decision: a new `onOpenEntryForEditing` opens and starts editing under one lock acquisition (no nested acquisition — it does not call the unlocked `onOpenEntry`/`onStartEditingEntry` internals), and `onStartEditingEntry`'s own guard was independently moved inside its lock. (a) — exempting `onOpenEntry`/`onCloseEntry` from the lock — stays rejected, reasoning recorded in the ViewModel's own class doc comment.

### Recount, corrected

A later review of this report's first pass questioned the call-site count: §1a's own dispatch text claims the hazard shape is "what `LogPanel.kt` **and** `JournalTab.kt`'s gallery path both do." Traced every actual invocation end to end (not inferred from parameter names):

| Route | Call | Traces to |
|---|---|---|
| `LogPanel` (drawer/wide window) | `LogEntryListScreen.kt:105` → `LogPanel.kt:172` (`onOpenEntry = onOpenEntryForEditing`) → `AvailabilityScreen.kt:757` → `MainActivity.kt:263` | `onOpenEntryForEditing`, one call, no chain |
| `JournalTab`, opening a committed entry | `LogGalleryScreen.kt:138` → `JournalTab.kt:189-191` (`{ id -> mode = REPORT; onOpenEntry(id) }`) → `AvailabilityScreen.kt:1067` → `MainActivity.kt:259` | `onOpenEntry` alone; routes to `LogEntryReportScreen`, not the edit form |
| `JournalTab`, reopening a draft | `LogGalleryScreen.kt:138` → `JournalTab.kt:193-199` (`{ id -> mode = EDIT; onOpenEntry(id) }`) | `onOpenEntry` alone — the code's own comment: already a draft, `onStartEditingEntry` would be a no-op |
| `JournalTab`, tapping Edit on the report | `JournalTab.kt:176-179` (`{ onStartEditingEntry(); mode = EDIT }`) → `AvailabilityScreen.kt:1071` → `MainActivity.kt:262` | `onStartEditingEntry` alone, from a separate, later user action |

`git log -p --follow` over `JournalTab.kt`'s complete history confirms no version — before, during, or after this dispatch — ever chained these two calls in one handler. **§1a's premise about `JournalTab.kt` was wrong.** `LogPanel.kt` was the one real call site, and it is fixed. The archived dispatch (`dispatches/2026-08-25-l4c-serialized-editing-state-dispatch.md`) carries a `**Correction (L4c)**` block on §1a recording this.

**Lesson-list candidate:** both callbacks appearing in `JournalTab`'s parameter list was read as evidence the two calls were chained. This is the same failure as taking a `grep` hit for a confirmed consumer — a name present is not a call site confirmed — and it has now cost two wrong counts on this project.

---

## §2 — The photo race, tested

A gated test (`a photo attach queued behind a loadEntries read is never lost to that read's stale snapshot`) holds a photo attach in flight via `readGate` while `loadEntries()` runs, and asserts the attach survives. Mutation-checked: reverting `loadEntries()`'s lock to a plain `run {}` block produced `AssertionError: "the attach must not have started yet — it's queued behind loadEntries' held lock" expected:<[]> but was:<[LogPhoto(id=new-photo,...)]>`; restored, confirmed green.

A second test (`two photo operations issued back to back apply in the order issued`) required fixing a real composability bug the test itself surfaced: `onAddPhoto`/`onRemovePhoto`/`onPullPhoto` captured `editingEntry` outside their lock, at call time, so two back-to-back calls both diffed from the same stale snapshot and the second silently discarded the first's result. Fixed by moving the capture inside each handler's own lock, the same fix already applied to `onStartEditingEntry`'s guard.

---

## §3 — What serialization does to the merge

**The answer:** the merge is now redundant for the ordering where a write acquires the lock first (serialization alone closes that — `saveGate` forces the write ahead, so `loadEntries()`'s critical section cannot start until the write has already landed), but still load-bearing for the ordering where `loadEntries()`'s own read wins the race to the lock (a new test, `loadEntries racing ahead of a keystroke's own write while loadEntries' own read wins the race to the lock`, mutation-checks correctly: reverting the merge to a wholesale replace produced `ComparisonFailure: expected:<typing, loadEntries got here first> but was:<original>`, restored, confirmed green). The merge was not removed.

**Correction to this report's first pass:** the original in-flight test (forcing the write ahead) was left in the suite with only its doc comment updated to record that it no longer discriminates — reverting the merge left it passing, identically, under both versions. A test that cannot fail is not coverage; it reads as some to a later reader regardless of what its comment says. Removed outright (commit `ab67032`) rather than left with a caveat. Its finding is folded into the surviving test's own doc comment. Also removed `FakeMushroomLogRepository`'s now-unused `saveGate` field — nothing in the suite exercised it once its one caller was gone.

---

## §4 / §5 — One bug, not two

These were investigated and fixed separately at first, then found to share a root cause on a second pass.

**Container, as first reported:** `LogEntryDetailScreen.kt`'s `PhotosSection` — a plain `Column` → `Row` (Camera/Gallery/From Album/Add Location) → conditional `FlowRow` (thumbnails), inside the form's own `Column.verticalScroll()`. No fixed-height or scroll-specific container. This environment has no Android emulator (no `/dev/kvm`, no `adb`, no `emulator` binary), so the reported "large empty region above the thumbnail" and "scrollbar track" weren't independently reproduced from source alone at that point. The thumbnail-centering bug found and fixed then (`FlowRow` missing `fillMaxWidth()`, so `horizontalArrangement` had nothing to center within) was real and is mutation-checked, but didn't explain the screenshot.

**Second pass, prompted by the recount above:** asked to measure the *button row's* width against root at 360dp and 412dp, the same way the photo-strip bounds were measured. That measurement failed on its own terms: Robolectric's text layout in this project's test setup does not reproduce real font metrics — an unmerged `onAllNodesWithText(..., useUnmergedTree = true)` probe measured "Camera" and "Add Location" text nodes at 7–13 *pixels* wide regardless of label length, and the outer `Button` nodes all clamped to Material3's 58dp `MinWidth` floor identically, which is not real glyph rendering. That specific check could not be performed reliably in this environment; the throwaway probe was discarded rather than kept as a test that couldn't discriminate.

What *can* be established without real text metrics: `ButtonDefaults.ContentPadding` (24dp horizontal per side, 48dp per button) and `labelLarge` typography are fixed Material3 constants, independent of any font-rendering limitation. Estimating average Roboto character width at 14sp (~7.8dp/char, a working approximation, not a measured fact) against the four labels' lengths puts the row's needed width at roughly 490dp — well over the ~328dp available at 360dp (or ~380dp at 412dp) after the form's own 32dp of horizontal padding. A plain, non-scrolling `Row` does not shrink or wrap children that don't fit; they render past the parent's bounds, off the physical screen, not clipped by Compose. This is consistent with, and very likely explains, both device reports as one bug: the "missing" Add Location button was the fourth, off-screen entirely, and the reported edge sliver in the pill's own green is the corner of that same off-screen button peeking into view — not a scrollbar. **This account is inferred from real, fixed Material3 constants plus an estimated character width, not confirmed by measurement** — the estimate could be off by a meaningful margin, and only a device rebuild settles it, per the note below.

**Fix (commit after `ab67032`):** moved the Add/Change Location button out of `PhotosSection`'s row entirely, to sit directly beside the "Found at .../No location set." text at `LogEntryDetailScreen.kt` it now answers (`Modifier.weight(1f)` on the text keeps a long coordinate string from reintroducing the same overflow). Not duplicated — the dispatch's own reasoning for reuse-not-duplication applies here too: two affordances for one action diverge eventually. The remaining three-button row (Camera/Gallery/From Album) was converted from `Row` to `FlowRow`, wrapping to a second line rather than running off-screen if it's still too wide on some device — a structural guarantee (`FlowRow` cannot lose a child off-screen the way `Row` can) that holds regardless of the text-metrics limitation above.

**What's still open:** the Robolectric limitation means no automated test in this suite can confirm or refute the overflow claim, or the fix's effect on it, the way the mutation-checked tests elsewhere in this report do. The centering fix from the first pass remains mutation-checked and verified; the button-row move and wrap do not have — and, in this environment, cannot get — the same kind of test-backed proof. **This closes only once the owner rebuilds and looks**, exactly as flagged after the first pass. If it doesn't fully resolve on-device, `/run-skill-generator`-style follow-up (a Paparazzi/Roborazzi screenshot-testing setup, which renders real fonts) is worth considering so this class of bug gets real coverage next time, rather than treating this specific gap as closed.

### §5 — Location enumeration and tests

The "Set Location" affordance the dispatch described as missing already existed in current source (`LogEntryDetailScreen.kt`, wired in both `LogPanel.kt` and `JournalTab.kt` to the same `CentrePinLocationPicker` the "+" flow uses, writing via `onEntryChanged` → always the draft row) — confirmed identical to `origin/main` before any change. No second picker was built. Every place an entry's location can be set:

1. Map's "Log a find" long-press → `onStartNewEntry(location, date)`.
2. Gallery's "+" tile → same path, `location = null`.
3. Edit form's Add/Change Location button — one mechanism, two call sites (`LogPanel.kt`, `JournalTab.kt`), now positioned beside the location text instead of buried in the Photos row.

Three tests added for the draft-model interaction, mirroring the existing photo-draft test shapes:

- `setting a location during a re-edit writes to the draft row only, never the committed entry` — mutation-checked (temporarily made `onEntryEdited` also patch the committed row's `foundAt`, the exact standalone-draft violation this test exists to catch; confirmed red with `expected:<45.326,-122.634> but was:<46.0,-123.0>`; restored, confirmed green). This test was only argued from a shared code path in this report's first pass, not verified directly — corrected here.
- `Cancelling an edit discards a location changed during the session, leaving the committed entry's location untouched` — mutation-checked (stubbed `onCancelEditing`'s `deleteEntry` call; confirmed red on `"the draft row itself must be gone"`; restored, confirmed green).
- `an incidental exit persists a location changed during a re-edit into the draft row` — mutation-checked (stubbed `onEntryEdited`'s `saveEntry` call; confirmed red on `"the new location must be durably on the draft row"`; restored, confirmed green).

---

## §6 — Housekeeping

Both items done in `e97985f`: `AvailabilityScreen.kt`'s `drawerSheetContent` doc comment corrected (one call site, the permanent drawer — compact renders a different composable), and the Section 2 verification pulse response filed to `docs/qc/pulses/reports/2026-08-25-l4-closeout-section2-verification-pulse.md`.

---

## Gate

- Compile green; full suite green, 97 classes / 694 tests / 0 failures / 0 errors, verified per class via JUnit XML.
- §2's mirror race test: mutation-checked, failure text reported above.
- §3's mutation check: re-run, result reported both ways above; the test that stopped discriminating was removed, not left in place.
- No deadlock: established by code inspection — only `onDeleteGalleryPhoto` calls another lock-acquiring function (`loadEntries()`), and it never holds the lock itself first, so there is no cycle — corroborated by zero hangs across every gated concurrent-operation test in this suite (`readGate`/`photoGate`/`saveGate`-style tests included).
- Photo strip: verified via real measured layout bounds (`getUnclippedBoundsInRoot`), mutation-checked, described at one/two/four photos in the prior report pass (three-then-one wrap at 320dp).
- Button-row/location-button fix: **not** verified this way — see §4/§5 above for why, and what closes it.
- Location draft/Cancel/incidental-persist: all three tested and mutation-checked, above.

## Confirmed vs. inferred vs. could not determine

- **Confirmed:** §1/§1a's serialization shape and scope; the call-site recount (file:line, traced end to end, plus full git history on `JournalTab.kt`); §2's and §3's mutation checks; §5's three mutation checks; §5's existing implementation and its three call sites; the Photos-section container structure and the centering bug/fix.
- **Inferred, not confirmed:** that the button-row overflow is the actual cause of the owner's original screenshot symptom, and that moving/wrapping the buttons resolves it on a real device. Based on real Material3 constants plus an estimated character width — reasoned carefully, not measured.
- **Could not determine:** the real rendered width of Material3 buttons at various label lengths in this test environment (Robolectric's text metrics here do not reflect real font rendering — confirmed by a probe showing text nodes measuring 7–13px regardless of label length); whether the original "large empty region above the thumbnail" symptom is now fully resolved (needs a device rebuild).

## Premises that turned out wrong

- §1a's claim that `JournalTab.kt`'s gallery path chains `onOpenEntry`+`onStartEditingEntry` — it doesn't, and never has. Corrected in the archived dispatch.
- This report's own first-pass treatment of the §3 in-flight test (leaving it in place with an updated comment instead of removing a test that no longer discriminates) — corrected here.
- This report's own first-pass treatment of §5's draft-only-write test (argued from a shared code path rather than mutation-checked directly) — corrected here.
- §4's original premise (a fixed-height/scrollable container) turned out to not be what's in source at all — the actual, more likely cause (an overflowing, non-wrapping button row) was found only on a second pass prompted by the call-site recount.

## Decided beyond the dispatch's own text

- Removed the non-discriminating §3 test and its now-dead `saveGate` support code, rather than leaving either in place.
- Moved the Add/Change Location button to sit beside the location text it answers, and converted the remaining button row to a wrapping `FlowRow` — the dispatch's §4/§5 didn't anticipate these were connected; both were done together once the connection was found, per instruction not to duplicate the affordance.
