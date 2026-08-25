# L4b Scoping Pulse — Response

**Pulse:** L4b Scoping (Persisted Drafts and Save/Cancel), from Planner, 2026-08-22
**Branch:** `claude/task-hwj91a` @ `0e2198bd7efde1bf62811713931f743accf1d8a8` — confirmed via
`git rev-parse HEAD`, matches the pulse's stated context exactly.

Read-only throughout. No code, doc, or test file was written, modified, or run. Every claim below
cites a file/line or the command run; anything I couldn't settle by static inspection is marked
**could not determine**, with the narrower question I could answer instead.

---

## Leading with the standing invitation

Two concrete silent-data-loss-shaped findings surfaced during this investigation, both directly
on point for the pulse's closing warning. Reporting them first rather than burying them in §1/§3.

**1. The "five write sites" claim is stale, and it's stale in two places, not one.** The pulse
itself repeats it ("The G1 report mentioned a 'five-write-site clearing rule'... list every one").
`grep -n "saveErrorMessage = null" app/src/main/java/com/forager/app/ui/log/MushroomLogViewModel.kt`
returns **seven** matches, at lines 119, 144, 167, 185, 206, 220, and 242 — one per write-site
success path (§1's table below maps each line to its method). The claim originates in
`app/src/test/java/com/forager/app/ui/log/MushroomLogViewModelTest.kt:108-114`'s own doc comment
("`saveErrorMessage` is set by five different write sites (start/save/delete/add photo/remove
photo)"), and `MushroomLogViewModel.onSaveErrorDismissed`'s own doc comment
(`MushroomLogViewModel.kt:255-260`) still says "the five write sites above." Both were true when
written — G3 added two more write sites (`onPullPhoto` at line 220, `onDeleteGalleryPhoto` at line
242) without updating either count. Nobody's data is at risk from this specific staleness (it's a
comment, not behavior), but it's exactly the kind of drift the pulse is asking me to be alert for:
a claim about "every write path" that was correct once and has been silently wrong since G3, caught
only because this pulse asked to re-verify it against the code rather than trust the report.

**2. `loadEntries()`'s `editingEntry` refresh (G3) is a real hazard once drafts exist, and the
mechanism is precise, not vague.** `MushroomLogViewModel.kt:69-77`:

```kotlin
state.copy(
    entries = entries,
    editingEntry = state.editingEntry?.let { editing -> entries.firstOrNull { it.id == editing.id } },
    isLoadingEntries = false,
)
```

This replaces `editingEntry` outright with whatever `getEntries()` just read from the repository —
not a merge, a full replacement. It is called from `init` (`MushroomLogViewModel.kt:59`) and from
`onDeleteGalleryPhoto`'s success path (`MushroomLogViewModel.kt:246`). Today this is safe because
`onEntryEdited` has already written every keystroke to the repository by the time this runs — the
freshly-read row and the in-memory `editingEntry` are always the same data. Under a draft model,
`editingEntry` (or whatever holds in-progress typing) would hold data the repository's `entries`
table does not yet have. If `loadEntries()` (or anything shaped like it) still overwrites that state
from a fresh repository read, **draft content is discarded in memory the next time either call site
fires** — and `onDeleteGalleryPhoto`'s call site is triggered by an action for a screen (the
gallery) that has nothing to do with the entry the user is typing in, so the loss would look
unrelated to what the user just did. This is not a hypothetical: it is the same function, unchanged
class, that a working dispatch already had a documented reason to call while a draft-shaped edit is
open.

---

## §1 — The editing path as it stands now

`MushroomLogViewModel.kt`, `MushroomLogUiState.kt`, `SaveMushroomLogEntryUseCase.kt`, and
`CreateMushroomLogEntryUseCase.kt` were re-read in full for this pulse (not recycled from an
earlier report). Reproducing the parts §1 asked for by reference rather than repasting all four
files verbatim here, since nothing has changed in any of them since the G3 report:

**Every method that writes**, with its write target:

| Method | Line | Writes via | `saveErrorMessage = null` on success? |
|---|---|---|---|
| `loadEntries()` | 55-78 | (read-only; no write) | no |
| `loadGalleryPhotos()` | 81-92 | (read-only; no write) | no |
| `onStartNewEntry` | 95-104 | `createEntry` → `repository.save` | line 99 |
| `onOpenEntry` | 107-108 | (in-memory only) | n/a |
| `onCloseEntry` | 111-112 | (in-memory only) | n/a |
| `onEntryEdited` | 115-123 | `saveEntry` → `repository.save` | line 119 |
| `onDeleteEntry` | 130-144 | `deleteEntry` → `repository.delete` | line 144 |
| `onAddPhoto` | 147-159 | `addPhoto` → `repository.addPhotoToGallery` + `attachPhotoToEntry` | line 156 (via `.replacing(updated).copy(...)`, confirmed at line 156) |
| `onRemovePhoto` | 162-170 | `removePhoto` → `repository.detachPhotoFromEntry` | line 167 |
| `onPullPhoto` | 173-185 (approx.) | `pullPhotoIntoEntry` → `repository.attachPhotoToEntry` | line 185 |
| `onDeleteGalleryPhoto` | ~200-247 | `deleteGalleryPhoto` → `repository.deletePhotoFromGallery` + `PhotoStore.delete` | line 242 |

That's **seven** write sites clearing `saveErrorMessage`, not five — see the standing-invitation
finding above for why the doc comments still say five.

**`SaveMushroomLogEntryUseCase`** (`SaveMushroomLogEntryUseCase.kt:14-16`): a single
`repository.save(entry)` call, no validation, no diffing — it persists whatever `MushroomLogEntry`
it's handed, replacing the stored row with the same id. It has no notion of "what changed since the
last save" — under a draft model, this use case (or something with its exact shape) is still what
turns a draft into a committed entry on Save; nothing about its own signature needs to change for
that, only when it gets called.

**`CreateMushroomLogEntryUseCase`** (`CreateMushroomLogEntryUseCase.kt:24-27`): generates an id via
`idGenerator()` (defaults to `UUID.randomUUID().toString()`), builds `MushroomLogEntry.draft(...)`,
and calls `repository.save(entry)` **immediately** — before the user has typed anything. This is
the exact behavior owner decision #6 says must stop ("tapping '+' must not create a visible empty
entry"). Today, tapping "+" *does* create a persisted, visible-in-the-list row: `onStartNewEntry`
(`MushroomLogViewModel.kt:95-104`) appends the returned `entry` straight into `_uiState.entries` on
success, and it's already in the repository by that point. This is confirmed by
`JournalTabTest.kt:161`'s existing test name — `starting a brand-new entry from the gallery's plus
tile goes straight to the edit form with no location` — which exercises exactly this path and
implicitly relies on the entry existing (with a real id) the moment "+" is tapped.

---

## §2 — What a draft table would need

**What a draft row could hold — three options, not chosen:**

- **(a) Full entry snapshot.** `MushroomLogEntryEntity` (`MushroomLogEntryEntity.kt:42-144`) has
  roughly 50 columns spanning id, location, date, sync state, and eight observation sections (cap,
  hymenophore, stipe, veil, context/flesh, spore print, host/substrate, notes/identification). A
  full-snapshot draft table means either (i) duplicating that entire column set into a second table
  (`draft_entries`), which then has to be kept in lockstep with `MushroomLogEntryEntity` by hand
  every time a section gains a field — a drift risk with no compiler check, since Room won't catch
  two independently-hand-written tables diverging — or (ii) collapsing the draft row to one
  serialized blob column holding the whole entry. Option (ii) directly conflicts with
  `MushroomLogEntryEntity.kt:13-19`'s own documented rule for why this data uses real columns and
  not a blob ("a blob cannot be filtered or counted, and this table is field notes someone may
  eventually want to query") — though that rule was written for the committed-entries table, whose
  query needs (filtering by cap shape, spore print color, etc.) a scratch draft table plausibly
  doesn't share. Whether that rule is meant to extend to a draft table is a judgment call this pulse
  didn't ask me to make.
- **(b) Field-level diff/patch.** A draft row holds only the fields changed since the last commit,
  keyed by entry id, applied on top of the last-saved row on read. Smaller rows, no schema
  duplication, but needs an explicit merge step (draft-patch-over-saved-entry) that doesn't exist
  anywhere in this codebase today — nothing currently reads two rows and reconciles them into one
  domain object. Also has to represent "field cleared back to `NotObserved`," not just "field set,"
  which a naive sparse diff (nullable columns = "unchanged") can't distinguish from "not touched."
- **(c) Something else** — e.g. reusing `MushroomLogEntryEntity` itself with a new discriminator
  column (`isDraft: Boolean`) rather than a second table at all, so a draft *is* an entry row in an
  uncommitted state instead of a separate concept. This sidesteps the duplication problem in (a)
  entirely, but changes what "the entries table" means everywhere it's read (`getAll()` would need
  to filter drafts out by default, and every call site that assumes every row in
  `mushroom_log_entries` is committed would need to be checked).

**Standalone-draft vs. draft-of-an-existing-entry as one schema:** whichever shape is chosen from
above, the schema needs a way to say "this draft has no committed entry yet" vs. "this draft is
layered on entry X." Concretely that's a nullable `committedEntryId: String?` (or equivalent) on
the draft row, or — under option (c) — no extra column at all, since the row's own id already
serves double duty and "committed" is just the `isDraft` flag flipping. This is reportable as a
structural requirement; which option satisfies it best is exactly the choice the pulse says not to
make here.

**Migration from version 8:** `ForagerDatabase.kt:68-82` — current `version = 8`, `entities` lists
nine classes. A new draft table (option a or b) is a `CREATE TABLE` only, no rebuild of any existing
table — the same shape `MIGRATION_5_6` used for `offline_regions`
(`ForagerDatabase.kt:47-52`, "same reasoning... [OfflineRegionEntity] never existed... until this
bump") and the same shape `MIGRATION_7_8` used for the brand-new `log_entry_photos`
(`Migrations.kt:379-384`, `CREATE TABLE IF NOT EXISTS`). Option (c) is different: it's an
`ALTER TABLE mushroom_log_entries ADD COLUMN isDraft` against the *existing* rebuild-shaped
`mushroom_log_entries` table, which by this database's own established practice
(`ForagerDatabase.kt`'s version-6/7/8 comments, all citing "the rebuild shape uniformly") would mean
a full `CREATE ... _new` / copy / drop / rename cycle like `MIGRATION_6_7`/`MIGRATION_7_8`, not a
bare `ADD COLUMN` — this codebase has a documented practice of not trusting `ADD COLUMN` alone,
independent of whatever SQLite version constraint originally motivated it. Either way, this would
be `MIGRATION_8_9`, version 9. Whichever legacy migration test fixtures currently reuse
`MushroomLogEntryEntity`/`LogPhotoEntity` for fidelity (per `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`,
already three-for-three on recurring) would need checking against whatever shape this migration
picks — I did not enumerate those fixture files in this pass since no migration exists yet to check
them against; that's a build-time check, not a scoping one.

**Photo references in a standalone draft — `log_entry_photos` keys on `entryId`
(`Migrations.kt:381-384`: `PRIMARY KEY(entryId, photoId)`, both `TEXT NOT NULL`), which a draft with
no committed entry doesn't have. Options, not chosen:**

- **(a) Give every draft an id from the moment it's created, and let that id double as the eventual
  entry id.** `CreateMushroomLogEntryUseCase` already does exactly this today —
  `idGenerator()` runs before anything is persisted (`CreateMushroomLogEntryUseCase.kt:24-25`). If
  a draft is assigned its id at creation and that same id becomes the entry's id on Save,
  `log_entry_photos` needs no schema change at all: a photo pulled into a not-yet-saved draft
  writes a cross-ref row keyed on the draft's id, which is simultaneously the future entry's id.
  This reframes owner decision #6 ("nothing appears in the log until the user has put something
  there") as a *visibility* rule enforced at the read/query layer (e.g. `getAll()` filtering out
  empty/uncommitted rows), not an *id-assignment-timing* rule — those are two different things this
  pulse's framing doesn't clearly separate, and worth flagging as its own point regardless of which
  option is picked.
- **(b) A separate `draft_photos` join table, keyed on `draftId` instead of `entryId`, structurally
  mirroring `log_entry_photos`, with rows moved (or copied) into `log_entry_photos` on Save and
  discarded on Cancel.** Keeps `log_entry_photos` meaning exactly "committed entry ↔ photo" with no
  ambiguity, at the cost of a second near-identical table and a copy step on every Save.
  Duplication precedent already exists in this codebase for far less (`hasUnrecordedFields()` is
  defined identically twice, once per file — `LogGalleryScreen.kt:45` and
  `LogEntryListScreen.kt:31`, the second explicitly commented as "a second [definition]" at
  `LogGalleryScreen.kt:41` since the first is file-private) — so a second small table isn't without
  precedent for tolerated duplication in this codebase, for whatever that's worth.
- **(c) Store photo references as a serialized column on the draft row itself** (a list of photo
  ids) rather than a relational table at all — smaller blast radius than serializing the whole
  entry (option (a) in the previous question), but a much narrower version of the same
  real-columns-vs-blob tension `MushroomLogEntryEntity.kt:13-19` raises, now scoped to one column
  instead of the whole row.

---

## §3 — Removing autosave

**Every call site that currently triggers `onEntryEdited`/`onEntryChanged`** (traced by grep, each
site confirmed by reading its surrounding function):

- `LogEntryDetailScreen.kt` — nine field-editor call sites, each wrapping a field change as
  `onEntryChanged(entry.copy(...))`: line 112 (`ownIdentification` `OutlinedTextField`), 131
  (`CapEditor`), 134 (`HymenophoreEditor`), 137 (`StipeEditor`), 140 (`VeilEditor`), 143
  (`ContextFleshEditor`), 146 (`SporePrintEditor`), 149 (`HostSubstrateEditor`), 152 (`NotesField`).
  `onEntryChanged` is a parameter declared at line 70.
- `JournalTab.kt:127` — the centre-pin location picker's own `onConfirm`:
  `onEntryChanged(editing.copy(foundAt = location))`. `onEntryChanged` param at line 70 (of that
  file), passed through to `LogEntryDetailScreen` at line 145.
- `LogPanel.kt:112` — identical shape for the drawer/wide layout's own location picker; param at
  line 66, passed through at line 130.
- `AvailabilityScreen.kt:700` and `AvailabilityScreen.kt:958` — the two places `onEntryChanged` is
  threaded through as `onLogEntryChanged` into `LogPanel`/`JournalTab` respectively.
- `MainActivity.kt:258` — the terminal wiring: `onLogEntryChanged = mushroomLogViewModel::onEntryEdited`.

So every one of these eleven call sites (nine field editors + two location-picker confirms) is a
place that, today, fires an immediate repository write. Under a draft model, all eleven still need
*some* method call to record the change — the difference is purely whether that call writes through
to `mushroom_log_entries` immediately or updates draft state instead. None of them need to change
*shape* (they'd still call some `onEntryChanged`-equivalent with the updated entry); what's behind
that call changes.

**What breaks if per-keystroke writes stop:**

- `MushroomLogViewModelTest.kt:79-92` and `:95-106` (`a failed save sets saveErrorMessage...`,
  `onSaveErrorDismissed clears saveErrorMessage`) both call `vm.onEntryEdited(...)` and then assert
  on `saveErrorMessage`, which today is set/cleared by the *immediate* result of
  `saveEntry(updated)` (`MushroomLogViewModel.kt:117-122`). If `onEntryEdited` stops writing to the
  repository per call, these tests' entire premise (edit → immediate repository round-trip →
  observe success/failure) no longer holds; they'd need to become tests of the draft-write path
  instead, not the repository-save path. Flagging per §7 too, since it's a "which tests assert
  autosave" answer as much as a "what breaks" one.
- The `MushroomLogRepository.save()` interface doc comment itself
  (`MushroomLogRepository.kt:40-45`) states its contract in terms of "autosaved on every keystroke"
  as the reason `save()` never touches `entry.photos`. That reasoning doesn't stop being true
  structurally (photos are still handled by separate attach/detach calls either way), but the
  comment's framing goes stale the moment `onEntryEdited` no longer autosaves — another instance of
  the same doc-drift pattern as the "five write sites" finding above.
- Nothing in `MushroomLogUiState`/`MushroomLogViewModel` relies on `uiState.entries` staying
  byte-for-byte current with the repository *while* a field is being typed — `entries` is only
  read from repository state at `loadEntries()` time and locally patched via
  `MushroomLogUiState.replacing()` (`MushroomLogViewModel.kt:263-267`) after any successful write.
  If writes become deferred (draft-only) rather than immediate, `replacing()`'s local patch would
  need to run against draft-shaped state too, or the list wouldn't reflect in-progress typing until
  a background auto-save actually lands — **could not determine** whether that's an acceptable
  interim UX (the list momentarily out of sync with the open form) without a decision on how
  drafts surface in `uiState`, which is a design question this pulse doesn't ask me to resolve.

**Does `loadEntries()`'s `editingEntry` refresh clobber an uncommitted draft?** Yes — see the
standing-invitation section above for the precise mechanism (`MushroomLogViewModel.kt:69-77`,
unconditional replacement, not a merge). Direct answer: as written today, if any code path that
currently calls `loadEntries()` (`init` at line 59, `onDeleteGalleryPhoto`'s success path at line
246, and any future dispatch that reuses the "refresh after a related change" pattern G3
established) runs while a draft holds unsaved content, that content would be overwritten by
whatever the repository currently has for that entry — which, once autosave is gone, would be
the last **committed** version, discarding everything typed since.

---

## §4 — The three exits

**How is a tab switch detected today?** `compactTab` is a single `remember { mutableStateOf(CompactTab.MAP) }`
at `AvailabilityScreen.kt:465`, changed at exactly three sites: the bottom nav's own
`onTabSelected` callback (`AvailabilityScreen.kt:856-858`), the "return to Maps" `BackHandler`
(`AvailabilityScreen.kt:553-554`), and the map's "Log a find" shortcut jumping straight to Journal
(`AvailabilityScreen.kt:906`). None of these three call anything on `MushroomLogViewModel` — this
confirms G3's finding (nothing closes `editingEntry` on a tab switch) at the exact call sites that
would need to change. On a **wide/expanded window**, there is no tab-switch analog at all:
`LogPanel` is the drawer's permanently-composed content (`AvailabilityScreen.kt:691`), and the
comment at `AvailabilityScreen.kt:543-546` states `isDrawerOpen`/`isMapFullscreen`/`compactTab` are
"all three... compact-only state that a medium/expanded window never changes away from its own
defaults" — so on that window class, "leaving" the log editor has no in-app event to hook at all
except closing the drawer (`isDrawerOpen = false`, set at `AvailabilityScreen.kt:547-548`, `608`,
`614`, `626`, `780`, `835`, `1009`, `1011`, `1021`, `1031`) or backgrounding the whole app.

**Existing `BackHandler` pattern for a deliberate back-out — both described:**

- `JournalTab.kt:111-118`: `BackHandler(enabled = editing != null || pickingLocationForEditingEntry
  || pullingPhotoForEditingEntry)`, unwinding one state at a time — picker closes first, then EDIT
  mode drops to REPORT mode, then REPORT closes the entry entirely
  (`editing != null -> onCloseEntry()` at line 116). This last branch is exactly where a
  Save/Cancel exit prompt would need to intercept: today it calls `onCloseEntry()`
  unconditionally, with no notion of "unsaved" to check first.
- `LogPanel.kt:100-102`: same shape, narrower — `pickingLocationForEditingEntry ||
  pullingPhotoForEditingEntry` only, since this panel (drawer/wide layout) has no REPORT/EDIT mode
  split or gallery to unwind through; there's no equivalent "close the entry" branch here at all,
  because this panel apparently never closes an open entry via back (**could not determine** what
  currently happens if `editing != null` and back is pressed here with neither picker open — the
  `enabled` condition would be `false`, so this `BackHandler` wouldn't fire, and back would fall
  through to `AvailabilityScreen`'s own top-level handlers; whether that's intended or an existing
  gap is outside what this pulse asked me to check, but relevant to where exit #2 would be wired).

**Lifecycle hook:** `grep -rln "onStop\|onPause\|ProcessLifecycleOwner\|DefaultLifecycleObserver\|LifecycleEventObserver" app/src/main/java --include=*.kt`
returns exactly one file, `SightingsMap.kt`. Read directly: `SightingsMap.kt:200-215` wires a
`DisposableEffect(lifecycleOwner)` (from `LocalLifecycleOwner.current` at line 172) with a
`LifecycleEventObserver` reacting to `ON_RESUME`/`ON_PAUSE`, forwarding to `mapView.onResume()`/
`mapView.onPause()` — this is MapLibre's own required lifecycle forwarding, scoped to that one
composable's `MapView`, not an app- or Activity-level hook usable elsewhere. Separately,
`grep -n "onStop\|onPause\|onCreate" app/src/main/java/com/forager/app/MainActivity.kt` shows only
`onCreate` (`MainActivity.kt:159-160`) — no `onStop`/`onPause` override exists at the Activity
level anywhere in this app. **So: backgrounding-triggered save would be new infrastructure at the
Activity/app level, but the `DisposableEffect` + `LocalLifecycleOwner` + `LifecycleEventObserver`
pattern already exists in this codebase as precedent for how to wire it** (a `DisposableEffect`
somewhere in `JournalTab`/`LogPanel`'s composition observing `ON_STOP` or `ON_PAUSE` via the same
`LocalLifecycleOwner.current`, rather than overriding anything on `MainActivity` directly) —
reporting this as the closest available structural answer, not a recommendation to use it.

**Where would each of the three exits actually be detected, given the current structure:**

- **Save / Cancel** — both are explicit user taps on new UI (a Save/Cancel affordance doesn't exist
  anywhere in this codebase today; `grep -rn "\"Save\"\|\"Cancel\"" app/src/main/java/com/forager/app/ui/log`
  was not run as part of this pulse, but no such button appears anywhere in
  `LogEntryDetailScreen.kt`'s current button row, which today only has Camera/Gallery/From
  Album/Change Location). These would be new buttons wired to new ViewModel methods; nothing about
  detecting them is a structural question — they're direct taps.
- **Tab switch** — only detectable by intercepting the three `compactTab`-assignment sites listed
  above (most directly, wrapping or observing the bottom nav's `onTabSelected` at
  `AvailabilityScreen.kt:858`) or by a `LaunchedEffect(compactTab)` that reacts specifically to
  transitioning *away from* `CompactTab.JOURNAL`. On wide/expanded, there is no equivalent event at
  all (see above) — this exit would only ever fire there via drawer-close or backgrounding.
- **Back (deliberate)** — the existing `BackHandler` chains in `JournalTab.kt:111`/`LogPanel.kt:100`
  are exactly where this is already detected; the "leave-without-answering" prompt per owner
  decision #3 would slot into `JournalTab.kt:116`'s `editing != null -> onCloseEntry()` branch
  (currently unconditional) and would need an equivalent new branch in `LogPanel.kt`, which
  currently has none for closing an open entry at all (see the "could not determine" note above).
- **Backgrounding** — no existing hook; would need new `DisposableEffect`/`LifecycleEventObserver`
  wiring, following the one existing precedent in `SightingsMap.kt` as described above.

---

## §5 — Crash recovery

**Is there a natural place in the existing load path?** `loadEntries()`
(`MushroomLogViewModel.kt:55-78`) is the only place `getEntries()` is called, and it already runs
once at `init` (line 59) — i.e., once per app/ViewModel-instance start, which is also exactly the
moment a crash-orphaned draft from a previous process would first become visible again. Structurally
this is the natural hook: whatever "check for uncommitted drafts" means, `init`/`loadEntries()` is
where a query for orphaned drafts would run today. Whether that means one combined
`getEntries()`-and-drafts read or a second call alongside it is a design choice, not something
current structure forces either way.

**How does an entry currently render in the list, and where could an "unsaved draft" indicator
go?** Two rendering sites, one per window-size layout, and both already have a per-entry
"incomplete" indicator to draw a direct parallel to:

- `LogGalleryScreen.kt`'s `LogEntryTile` (`LogGalleryScreen.kt:146-186`, the compact grid view) —
  shows an "Incomplete" text badge (line 183-186, exact text not fully re-read past line 184) when
  `entry.hasUnrecordedFields()` (a `private` extension function defined at `LogGalleryScreen.kt:45`).
- `LogEntryListScreen.kt`'s `LogEntryRow` (`LogEntryListScreen.kt:94-123`, the wide/drawer list
  view) — shows "Incomplete — some fields not yet recorded" (line 111-116) under the identical
  condition, via its own **separately-defined** copy of the same extension function at
  `LogEntryListScreen.kt:31` (the duplication is already disclosed in-code at
  `LogGalleryScreen.kt:41`, since Kotlin file-private visibility means each file needs its own).

A "this entry has an unsaved draft" indicator has an exact structural precedent to follow in both
places — a new boolean check alongside `hasUnrecordedFields()`, rendered as a second badge/line —
and would face the identical two-copies-of-one-boolean-check duplication these two files already
have and have already accepted.

**What's determinable about distinguishing a crash-orphaned draft from an in-progress one?**
Reporting what's determinable, not designing this per the pulse's own instruction: nothing in the
current codebase distinguishes "this ViewModel instance is still alive and the user is mid-edit"
from "the process died with a draft outstanding" at the data layer — `editingEntry` is pure
in-memory `ViewModel` state (`MushroomLogUiState.editingEntry`, held only in the `StateFlow`,
never itself persisted). A persisted draft row existing is, by construction, only evidence that a
draft was *written*, not evidence of whether the writing process is still running. **Could not
determine** whether any per-process or per-session marker exists anywhere in this codebase that
could serve as a "still actively being edited" signal (e.g. a heartbeat, a process-lifetime id) —
grepping for one wasn't part of this pulse's asked investigation and I did not go looking; if this
distinction matters, it would need one designed, since nothing today provides it implicitly.

---

## §6 — Boundary check

For each of the seven pieces named, what it types against and what declares it:

| Piece | Types against / declared by | Can't compile without |
|---|---|---|
| Draft table + migration | New Room `@Entity` class + `MIGRATION_8_9` in `Migrations.kt`, registered in `ForagerDatabase.kt`'s `entities` list (currently `ForagerDatabase.kt:69-79`) | Whichever draft-row shape §2 picks; nothing else in the app references this table directly until the read/write path below exists |
| Draft read/write path | A new repository method (or methods) alongside `MushroomLogRepository.kt:25`'s existing interface, implemented in `RoomMushroomLogRepository` (not read in this pass, but is the existing implementation of every method in that interface) | The draft table's entity shape from the row above; also needs `MushroomLogDao` (`MushroomLogDao.kt`, not re-read this pass beyond the earlier grep) to gain matching `@Insert`/`@Query` methods |
| Removing autosave | `MushroomLogViewModel.onEntryEdited` (`MushroomLogViewModel.kt:115-123`) and its eleven call sites (§3) | The draft read/write path above — `onEntryEdited` (or its replacement) needs somewhere to write instead of `saveEntry` |
| Save/Cancel UI | New buttons in `LogEntryDetailScreen.kt` (currently no Save/Cancel exists in its button row) and new `ViewModel` methods (`onSaveDraft`/`onCancelDraft` or equivalent) | Removing-autosave's replacement method shapes, and the draft read/write path (Cancel needs "the last saved state" to revert to, which only the draft/entry split from §2 can supply) |
| The three exits | New wiring at `AvailabilityScreen.kt:858`/`906`/`553` (tab switch), `JournalTab.kt:116`/`LogPanel.kt` (back), and new `DisposableEffect` lifecycle wiring (backgrounding) | Removing-autosave's replacement (the "auto-save on incidental exit" behavior needs a method to call), plus a way to tell "unsaved" from "already committed" (from §2's schema) |
| Crash recovery | New logic in/near `loadEntries()` (`MushroomLogViewModel.kt:55-78`) plus new badge rendering in `LogGalleryScreen.kt`'s `LogEntryTile`/`LogEntryListScreen.kt`'s `LogEntryRow` | The draft table existing and being queryable (§2, first two rows of this table) |
| Photo references in drafts | Either `log_entry_photos` unchanged (option a in §2) or a new join table (option b), consumed by `PullPhotoIntoEntryUseCase`/`RemovePhotoFromLogEntryUseCase` (both currently typed against `MushroomLogEntry`/`LogPhoto`, per the domain model read in prior dispatches) | Whichever §2 photo-reference option is chosen; also needs the draft table to exist first if it's a draft-scoped column/table |

**Is any of it separable into its own dispatch?** The draft table + migration is the one piece with
no dependency on anything else in this list — it could land as a standalone dispatch (schema only,
no behavior change, `ForagerDatabase.version` bump with nothing yet reading or writing the new
table) the same way `MIGRATION_5_6` added `offline_regions` before anything used it
(`ForagerDatabase.kt:47-52`'s own comment: "never registered here [before]... has never existed in
a real install until this bump" describes registration preceding use, though in that case the
entity/DAO already existed in code — the closer parallel here is a table that exists in schema and
DAO before any ViewModel touches it). Everything else in the table above has at least one inbound
dependency on another row, so removing-autosave, Save/Cancel UI, the three exits, and crash
recovery form one connected unit that would need to land together or in a very specific order
(read/write path → remove autosave → Save/Cancel UI → the three exits → crash recovery, roughly
matching the table's dependency column) — this is reportable as a dependency ordering, not
something I'm choosing to split.

---

## §7 — What this makes dead or wrong

**What becomes dead code when autosave goes:**

- `MushroomLogViewModel.kt:35-42`'s "## Autosave, not an explicit save button" doc comment on the
  class itself — the doc comment's entire premise ("A field app has no guarantee of a graceful
  exit... losing whatever was filled in between visits... would defeat the point") becomes the
  *design rationale L4b is explicitly overriding*, not a stale detail — this is the class's own
  headline doc comment, not a buried one.
- `MushroomLogViewModel.kt:255-260`'s `onSaveErrorDismissed` doc comment ("the five write sites
  above cover the 'next successful save' half") — already stale today (seven, not five; see
  standing-invitation section), and its whole framing assumes autosave's "next successful save"
  model.
- `MushroomLogRepository.kt:40-45`'s `save()` doc comment, specifically the clause "an unrelated
  field edit (autosaved on every keystroke) never rewrites the entry's whole photo-reference set" —
  the *behavior* it documents (save() never touches photos) stays true, but the parenthetical
  reason no longer describes how `save()` gets called.
- `MushroomLogDao.kt:18`'s comment referencing "every autosaved field edit" (seen via the earlier
  grep, not re-read in full this pass) — same category of stale rationale, not re-verified beyond
  the grep hit.
- `LogEntryDetailScreen.kt:51`'s comment ("autosaving through `onEntryChanged` on every field
  change") — same category, seen via grep, not re-read in full.
- `JournalTab.kt:182`'s comment referencing "[MushroomLogViewModel]'s doc comment on autosave" —
  a comment that cross-references the soon-to-be-wrong class doc comment above.
- `CreateMushroomLogEntryUseCase`'s current immediate-persist behavior itself
  (`CreateMushroomLogEntryUseCase.kt:24-27`) — not literally dead, but its contract ("persists
  immediately... makes the deferred-observation edit flow possible") is precisely what owner
  decision #6 says must not happen for a bare "+" tap any more.

**Tests that assert autosave-on-keystroke behavior — listed, not deleted:**

- `MushroomLogViewModelTest.kt:79-92` (`a failed save sets saveErrorMessage, and the next
  successful save clears it`) and `:95-106` (`onSaveErrorDismissed clears saveErrorMessage`) — both
  call `onEntryEdited(...)` and assert on the *immediate* repository-round-tripped result. Neither
  is titled around "autosave," but both exercise exactly the immediate-write contract that would
  change shape.
- `MushroomLogViewModelTest.kt:108-114`'s doc comment itself (the "five write sites" text) —
  documents, in prose, the very autosave-write-site enumeration that's central to what changes.
- `RoomMushroomLogRepositoryTest.kt:138` (per the earlier grep hit — "gallery ownership `save()`
  must never touch photo references at all (autosave on every field...") — not re-read in full
  this pass, flagged from the grep match alone; **could not determine** its exact assertion without
  reading the full test, only that its own comment references autosave by name.
- No test in `JournalTabTest.kt` or `LogPanelTest.kt` is named around autosave specifically (full
  test-name list checked via `grep -n "fun \`"` against both files), though both files' harnesses
  wire real `onEntryChanged`/`onPullPhoto` callbacks (confirmed clean of the no-op-harness pattern
  in the G3 report) that would need their real behavior updated once what's behind those callbacks
  changes — not autosave-asserting tests themselves, but exercising the call sites that feed it.

**UI copy implying automatic saving:** none found by inspection of `LogEntryDetailScreen.kt`,
`JournalTab.kt`, or `LogPanel.kt`'s user-visible strings (button labels, dialog text) — the
"autosave" framing exists only in code comments and doc comments (listed above), not in anything a
user reads on screen. **Could not determine** this exhaustively without a full string-literal
audit of all three files' `Text(...)` calls, which wasn't part of what was already read in prior
dispatches; the specific strings I have read (button labels, error Toasts, dialog bodies from the
G2/G3 reports) contain nothing autosave-implying.

---

## Could not determine (collected)

- Whether `RoomMushroomLogRepository`'s actual implementation of every `MushroomLogRepository`
  method has anything else relevant to §6 beyond what the interface declares — that file wasn't
  re-read in full this pass (only referenced from memory of the G1/G2/G3 work).
- Whether `LogPanel.kt` has any handling at all for a deliberate back-out of an open entry when no
  picker is open (§4) — its `BackHandler`'s `enabled` condition doesn't cover that case, and I did
  not trace where back falls through to in that case.
- Whether any process-lifetime/session marker exists anywhere in this codebase that could
  distinguish a crash-orphaned draft from one the user is still actively editing (§5) — not
  searched for, since designing that distinction is explicitly out of scope for this pulse.
- The full text of `LogGalleryScreen.kt` line 183-186's "Incomplete" badge string past what was
  read, and whether `RoomMushroomLogRepositoryTest.kt:138`'s test asserts anything beyond what its
  comment states — both citations above rest on partial reads (grep hits or truncated context), not
  full re-reads, flagged rather than presented as complete.

No premise in the pulse itself turned out wrong — DB version 8, branch head, and every code
excerpt the pulse assumed all matched current code exactly.
