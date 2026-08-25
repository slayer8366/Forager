# L4 Close-Out Checklist, Section 2 — Verification Pulse Response

**Pulse:** L4 Close-Out Checklist §2 ("Verification pulse — coder, read-only, no changes"), from
Planner, 2026-08-25.
**Branch at time of pulse:** `claude/l4b-persisted-drafts` @ `e169084` (pre-merge, pre-CI-change).
**Date:** 2026-08-25

Read-only throughout. No code, doc, or test file was written, modified, or run as part of
producing these findings; the fixes some of them describe below (§6's doc-comment item, the pulse
report archive) were applied afterward, in the L4c pre-work pass, and are noted as such rather than
folded silently into this record.

---

## 1. The mirror of the §3 race — real gap, found, not fixed here

`onAddPhoto`/`onRemovePhoto`/`onPullPhoto` in `MushroomLogViewModel.kt` do **not** update
`_uiState` before persisting, unlike `onEntryEdited`: each captures `entry` once, awaits the full
persist (`addPhoto`/`removePhoto`/`pullPhotoIntoEntry` — real I/O), and only then sets
`editingEntry = updated` in `onSuccess`. `AddPhotoToLogEntryUseCase.invoke` (its own file, line 25)
returns `entry.copy(photos = entry.photos + photo)` computed against that stale captured snapshot,
never a fresh read.

This is the mirror-image race: if `loadEntries()` (e.g. via `onDeleteGalleryPhoto`'s success path)
starts its disk read before a pending photo attach lands, but its own `_uiState.update{}` call
happens to apply *after* the attach's own success update, the merge's
`editing.copy(photos = freshPhotos)` overwrites the correctly-attached photo list with a stale one —
silently dropping the photo from the open form (still correct on disk; the UI regresses until the
next refresh). Real and reachable: two independent `viewModelScope.launch` coroutines with real I/O
suspension points have no ordering guarantee between them. The `saveGate` mechanism from the §3 test
transfers directly — gate `attachPhotoToEntry`/`addPhotoToGallery` in the fake repo instead of
`save()`. Not fixed or tested in this pulse, per its read-only scope — carried into L4c §2 as the
dispatch item it became.

## 2. Two hosts, one state — verified, exactly one per window class

`SnackbarHost(logDraftSnackbarHostState)` has exactly two call sites in `AvailabilityScreen.kt`:
inside `compactMainScaffold`'s `Scaffold` (COMPACT only) and inside `PermanentNavigationDrawer`'s
drawer sheet (medium/expanded only). Both are gated by the same mutually-exclusive
`if (windowWidthClass == COMPACT) { ...content = compactMainScaffold } else { PermanentNavigationDrawer(...) }`
branch — only one is ever composed for a given `windowWidthClass`, so exactly one host observes the
state at a time on every window class. Not a bug; the requirement holds. Noted, not tested further:
a live resize across the breakpoint (foldable unfold, resizable desktop window) tears down one host
and composes the other, which would dismiss an in-flight Snackbar — a possible UX rough edge, not a
double-host defect.

## 3. Archive integrity — clean

`git grep "pulses and dispatches"` across the whole tracked tree returned exactly two hits, both
inside the L4b-R2 report's own "Old path" code-fence example — explicit historical documentation of
what the path was renamed *from*, not a live reference. No stale link or path found anywhere else.

## 4. Index rows — stale, on two counts (since corrected as part of the L4b merge)

`docs/qc/` had no README/index file at all, unlike `docs/plans/` and `docs/audits/`.
`docs/plans/README.md`'s own row for `pr26-rework.md` read "Remaining work is the L1–L6 Log &
Location rework... scoped, **not yet dispatched**" — stale, since L4 (L4a, then L4b/L4b-R/L4b-R2)
had substantially landed by the time of this pulse. `pr26-rework.md`'s own `Sequencing` block never
got a status annotation for L4 the way `0`/`A`/`B` did. `docs/audits/README.md` had no comparable
gap — L4b's reports correctly live under `docs/qc/`, not `docs/audits/`.

**Since corrected**, in the L4b merge commit (`claude/task-hwj91a` @ `1305a4b`): `docs/plans/README.md`'s
row and `pr26-rework.md`'s Status/Sequencing block now reflect L1–L4/G1–G3 landed with commits and
PR numbers, and `docs/qc/README.md` now exists.

## 5. Workstream C — answered from the repo

It never landed under any name. `docs/audits/2026-08-24-workstream-c-and-d-archive.md` states
directly: C's actual deliverable (the region delete-confirmation dialog) shipped early, inside
Workstream B, before C was ever dispatched — verified against shipped `OfflineRegionsSection`
source. Separately, the owner's 2026-08-22 design change (tile capture moved from delete-time to
location-set-time) eliminated the risk C existed to guard against. Checked the L1–L6 breakdown in
`pr26-rework.md` too: none of L1–L6 is a region-delete-block flow — L6 mentions "a delete removes
both the entry's photos (L1) and its capture region," but that is entry-delete cascading cleanup,
not region-delete blocking. C is fully superseded, not hiding under a new name.

## 6. Standing invitation

- `docs/qc/pulses/reports/` held only `.gitkeep` — no pulse report had ever been filed there, even
  though at least one scoping-pulse review happened earlier in this workstream (the original L4b
  pulse, 2026-08-22). **Since corrected**: that response and this one are both now archived here,
  and the original `.gitkeep` was removed as no longer needed.
- `AvailabilityScreen.kt:628-629`'s own doc comment on `drawerSheetContent` — pre-existing, not
  introduced by this workstream, adjacent to what L4b-R2 touched for its own Snackbar-host fix —
  claims it is "shared between the compact `ModalNavigationDrawer` below and the
  `PermanentNavigationDrawer`." It is not: compact's `ModalNavigationDrawer` uses
  `CompactSearchDrawerContent`, a different composable; `drawerSheetContent` has exactly one call
  site, the permanent drawer. Flagged for L4c §6, which names the same file and lines independently.
