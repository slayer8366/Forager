# Pre-build report: the absence timeout. Two of the spec's three requirements do not survive the code

**Date:** 2026-09-17 · **Branch:** `claude/new-session-vto65i` · **Base:** `55f859b` · **Status:**
**stopped before building, on two of the dispatch's own stop-and-ask triggers.** No code was
written.

**Dispatch:** `2026-09-17-dispatch-camera-absence-timeout.md` — close the in-app camera after a
four-minute absence, return the user where they came from, persist unsaved captures to the in-app
album. Four verify-first questions, of which the dispatch said two could invalidate parts of the
spec. Both did, and not the two it expected.

**Summary.** Requirement 1 (the timeout) is sound and buildable as specified. **Requirement 3 has no
work to do**: every capture is already persisted to app-private storage and added to the in-app
album at the moment the shutter succeeds, so there is no "taken but not persisted" state to flush.
**Requirement 2 is a new capability and collides with two existing owner decisions**: backgrounding
with the camera open *already* leaves journal editing and *already* arms the cartography
"backgrounded while dirty" prompt, because the in-app camera deliberately no longer sets the
in-flight guard that used to suppress them.

---

## The four verify-first answers

### 1. Does the in-app album path write only to app-private storage? — Yes. No MediaStore write.

`AddPhotoToGalleryUseCase.kt:23-27` does exactly two things: `photoStore.persist(source)` and
`repository.addPhotoToGallery(photo)`.

The write is `FilePhotoStore.persistOnIo` (`FilePhotoStore.kt:113-147`): the destination is
`File(photosDir, "$id.jpg")` where `photosDir = File(context.filesDir, PHOTOS_SUBDIR)`
(`FilePhotoStore.kt:100`). App-private `filesDir/photos/`, and the row is a Room row. Nothing
reaches the shared collection.

**The one MediaStore reference in `main/` is a read, and not on this path.** `MediaStore.setRequireOriginal`
at `FilePhotoStore.kt:167` lives in `readExifData`, which `persistOnIo` calls only for a
`GalleryImportPhotoSource` (`:138`) — never for a capture, and never to write. `grep -rn MediaStore
app/src/main` returns that line and its import, and nothing else.

**The privacy claim holds.** No stop-and-ask on this one.

### 2. Does the scrub run on the path this feature would use? — Yes, and it cannot be bypassed.

`FilePhotoStore.kt:132`: `if (source is CameraCapturePhotoSource) scrubPhotoMetadata(destination)`,
inside `persistOnIo`, inside `persist`. The scrub is a property of **`PhotoStore.persist` itself**,
not of any caller, so every route that persists a capture is scrubbed by construction —
`AddPhotoToGalleryUseCase.kt:24` and `AddPhotoToLogEntryUseCase.kt:22` both go through it.

### 3. Where does "where they came from" live? — **It is a new capability, and it collides with two shipped decisions.**

This is the dispatch's own stop-and-ask trigger, and it fires.

**What the open target actually carries.** `InAppCameraTarget` (`InAppCameraHost.kt:18-27`) is a
three-value enum — `LOG_ENTRY`, `ALBUM`, `CARTOGRAPHY_ENTRY`. It identifies **which kind of surface
asked**, not which record. The dispatch's premise, "the open target already identifies the record
the camera was opened from", is not what the code holds; the record's identity lives in the
screen's own state, and the target exists only to route the photo to the right consumer
(`InAppCameraHost.kt:98-102`).

**Why that would not have mattered on its own.** The camera is a `Dialog` composed over the screen
that opened it (`InAppCameraDialog.kt:135`, and `PhotoAcquisitionLaunchers.kt:54`: *"No capture
leaves the Activity now: a `Dialog` composes over the screen that opened it"*). The screen
underneath is never navigated away from, so closing the dialog reveals it again with no navigation
at all. Requirement 2 would be satisfied by construction.

**Why it does matter.** Two lifecycle hooks fire on the way out, and the in-app camera no longer
suppresses either:

- **Journal.** `AvailabilityScreen.kt:1471-1479` — on `ON_STOP`, if a find is being edited and no
  photo acquisition is in flight, `onLeaveEditingIncidentally()` runs: the Gmail-drafts-style
  incidental-exit auto-save. The find is saved as a draft and **editing is left**.
- **Cartography.** `CartographyScreen.kt:204-206` — on `ON_STOP`, if the entry is dirty and no
  acquisition is in flight, `backgroundedWhileDirty = true`, which **prompts Continue
  editing / Commit / Save as draft on `ON_RESUME`**.

**And the guard that used to suppress them no longer covers the camera.** `acquisitionInFlight`
(`PhotoAcquisitionLaunchers.kt:88`) was deliberately narrowed when the in-app camera became a
dialog: *"True from the moment a launcher below hands control to an external Activity — now the
system permission dialog and the photo picker, no longer a camera app"* (`:77-78`), and *"The camera
half of that is now structural rather than guarded"* (`:85-86`). That reasoning is correct for
**opening** the camera, which no longer causes `ON_STOP` at all. It does not cover a **real**
backgrounding while the camera happens to be open — and that is precisely the event this feature is
about.

**So today, with no change at all:** background with the camera open over an edited find, come back,
and the camera is still open (the ViewModel held it) while the screen beneath it has already left
editing. Returning the user "where they came from" therefore requires *restoring* a state the app
has already deliberately exited, on both surfaces. That is new capability, and on cartography it
would mean suppressing or answering a prompt the owner specifically designed.

### 4. What happens to the timer on process death? — Exactly as the dispatch predicted.

No timer fires; the process is new. `InAppCameraViewModel` is gone with its store, so the camera is
closed on return regardless. Captures taken before the kill were already persisted (see below), and
any genuinely orphaned file is collected by `ForagerApplication.sweepOrphanedCaptures`
(`ForagerApplication.kt:63-68`) → `CameraCaptureFiles.sweepOrphans` (`CameraCaptureFiles.kt:67-75`),
which deletes capture files older than process start minus the 2 s guard
(`ORPHAN_MTIME_GUARD_MILLIS`, `:84`). **Recorded as a known gap, not built around**, per the
instruction.

---

## Finding A — requirement 3 has nothing to do

**Every successful capture is persisted at the moment it is taken, before the user does anything
else.** `InAppCameraDialog.kt:194-198`: on capture success, `photosTaken += 1` and
`onPhotoCaptured(CameraCapturePhotoSource(capture))` fires immediately. All three consumers persist:

| Target | Consumer | Reaches |
|---|---|---|
| `LOG_ENTRY` | `MushroomLogViewModel.onAddPhoto` (`:635`) | `AddPhotoToLogEntryUseCase` → `persist`, `addPhotoToGallery`, `attachPhotoToEntry` (`:22-24`) |
| `ALBUM` | `MushroomLogViewModel.onAddGalleryPhoto` (`:699`) | `AddPhotoToGalleryUseCase` → `persist`, `addPhotoToGallery` |
| `CARTOGRAPHY_ENTRY` | the same `onAddGalleryPhoto`, with a callback attaching the id (`MainActivity.kt:453-454`) | as above |

Note the log-entry path adds a **gallery row too** (`AddPhotoToLogEntryUseCase.kt:23`), before
attaching. So captures from every surface are already in the in-app album, continuously — not on
timeout, but on the shutter tap. The capture's scratch file is deleted by
`CameraCapturePhotoSource.release()` (`:34-41`) in `persist`'s `finally`.

**A timeout handler would therefore find nothing to persist.** Requirement 3 is already satisfied by
a stronger mechanism than the one it asks for: the captures are never in an unsaved state to begin
with. This is good news for the privacy argument — it holds, and holds earlier than the spec
assumed — but it means the requirement should be struck rather than built.

## Finding B — one real orphan path, pre-existing, and it is a silent swallow

`MushroomLogViewModel.kt:646`: `val entry = _uiState.value.editingEntry ?: return@withLock`.

If a `LOG_ENTRY` capture arrives when there is no editing entry, the source is **dropped without
being persisted and without `release()` being called** — no log line, no error state. The capture
file stays in `captures/` until the next startup sweep, and the photo is lost. That is the one way
the app produces a "taken but not persisted" capture, and it is an error path rather than a user
state.

Pre-existing, outside this dispatch, and reported rather than touched — but it is a silently
swallowed failure, which CLAUDE.md's "Errors and failure paths" rules out. It is also the only
reading I can find under which **v4 step 8.4-8.5** ("three photos, **do not** persist. Back out." →
"`captures` holds three") describes something the app actually does. Under the normal flow those
three captures would have been persisted and released, and `captures/` would be empty.

## Finding C — what v4 step 8 needs, which the dispatch asked me to read

The dispatch says step 8 "expects three unsaved captures to survive backing out and then be swept to
zero after a force stop" and asks what needs to change. **What needs to change is more than the
dispatch anticipated**: the step's premise does not match the code today, independent of this
feature. There is no user-reachable way to take three captures and leave them unpersisted, unless
the tester happens to hit the null-editing-entry path in Finding B. I have not changed the step,
because what it should say depends on the decisions below.

---

## Why I stopped

Two of the dispatch's four listed stop-and-ask triggers fired:

- *"If returning the user to the originating screen turns out to be a new capability rather than an
  existing one."* — It is, and it conflicts with two shipped, deliberate behaviours.
- The spirit of *"if persisting several captures at once needs a different path"* — there is nothing
  to persist at all.

Requirement 1 alone is buildable and unambiguous. I did not build it, because requirements 2 and 3
change what its handler does on fire, and a timeout that closes the camera while leaving the two
lifecycle behaviours unaddressed would ship the least useful third of the feature and make the
remainder harder to reason about.

## Options, priced, none chosen

**On requirement 2:**

1. **Strike it.** Close the camera on timeout and accept that the journal has already draft-saved
   and cartography will prompt. The user lands on a coherent screen — just not mid-edit. Smallest
   change; matches what backgrounding already means everywhere else in this app.
2. **Suppress the two hooks while the camera is open**, so backgrounding with the camera up does not
   count as leaving the edit. Restores the pre-2026-09-15 behaviour for this one case, and is a
   one-line condition on each hook — but it reverses part of the narrowing decision recorded in
   `PhotoAcquisitionLaunchers`, so it is the owner's to make, not mine.
3. **Restore the editing state on return.** Genuinely new capability on both surfaces. Most faithful
   to the spec, most expensive, and on cartography it means pre-answering a prompt the owner
   designed deliberately.

**On requirement 3:** strike it, and record in the dispatch's place that the privacy property it was
protecting is already held at capture time. Optionally fix Finding B separately, which is the only
case it was ever protecting against.

**On the four minutes:** unaffected by any of the above. The decision is a timestamp comparison on
return, no service, no alarm — `InAppCameraViewModel` is the natural holder, since it already
survives configuration change and dies with the process.
