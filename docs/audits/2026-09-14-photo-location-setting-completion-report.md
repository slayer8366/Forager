# "Automatically Save Location to Photos": completion report

**Date:** 2026-09-14
**Request:** owner, in chat, with three scoping answers given before any code was written (see
"What was asked" below). No planner dispatch.
**Branch:** `claude/new-session-vto65i`, code commit `c5acbf4`. At the start: `origin/main` still
`175b050`, PR #102 open and not merged, local and remote both at `747c6bd`, every remote head
fetched, nothing to merge. Re-checked before the push; the remote had not moved.
**Nothing is pre-authorized to merge.** No schema change: the preference is a DataStore boolean.

---

## What was asked, and the three answers that shaped it

The request was a Settings checkbox labelled **"Automatically Save Location to Photos"**, with
the owner's own explanatory wording, plus "a way to remove location from the pictures after
they're set". Three questions were put before building, because each changed what would be built.
The answers, as given:

1. **Scope when off:** *"Every automatic location, photos and finds."*
2. **Retroactive removal:** *"No removing location from already saved photos. Photos imported from
   outside the app are to remain untouched. Photos from inside the app already have location
   removed by default. This closes the loop by allowing users an option to take the current user
   location, and apply it to the photo when it was taken. This enables/disables that."*
3. **Default for existing users:** *On.*

Answer 2 removed a whole half of the original request. Nothing retroactive was built, and no EXIF
is written or stripped anywhere by this change. **But answer 2 also contains a premise about the
current code that does not hold — see "The premise that does not hold" below, which is the one
finding in this report that matters more than the feature.**

---

## What was built

### One preference, its own repository, its own file

`PhotoLocationPreferenceRepository` (`domain/PhotoLocationPreferenceRepository.kt:34`) with
`getAutoSaveLocationToPhotos`/`setAutoSaveLocationToPhotos`, implemented by
`DataStorePhotoLocationPreferenceRepository` (`:18`) over its own `photo_location_preferences`
file, built with `PreferenceDataStoreFactory.create` rather than the process-wide delegate.

Its own repository rather than more keys on `MapPreferencesRepository`, following the rule
`SundownPreferencesRepository`'s own doc comment already states: one repository per concern, one
file each. DataStore rather than Room under the standing split: a flat scalar with nothing to
join against. Constructed once, in `AppContainer.kt:190`.

**Default on** (`DEFAULT_AUTO_SAVE_LOCATION_TO_PHOTOS`, `:39`), per answer 3, so an install that
predates the setting behaves exactly as it did.

### The checkbox

`PhotoLocationSection` (`AvailabilityScreen.kt:2585`), rendered in the Settings panel between the
Night Maps checkbox and the crash-logs row (`:2513`), reached by both the compact and the
drawer layouts. The label and the explanation are file-scope constants (`:2606`, `:2608`) so the
test asserts the exact strings the panel draws rather than a retyped copy.

The explanation is the owner's wording, verbatim, including the edit they sent:

> When saving photos, metadata is stripped of the location data. Enabling this option captures
> your current position, and saves it to the Journal entry instead. This allows you to share your
> photos outside the app without the location being revealed.

It is the only control in that panel carrying explanatory text. The reasoning is recorded on the
composable: every other setting announces itself when flipped (the map recolours, units change),
while this one changes what is written to a record you cannot see from that screen, and its point
is what happens to a photo after it leaves the app.

### What the flag gates — wider than its label, per answer 1

Three paths, all in `MushroomLogViewModel`, all reached through one borrowed suspend function
(`autoSaveLocationToPhotos`, `:212`), **re-read per capture and never cached**, so unchecking the
box applies to the very next photo rather than to whatever was read at construction:

- `requestAndPatchCaptureFix` (`:752`, gate at `:757`) — the single choke point for **both**
  camera paths. Gating here stops the fix reaching the photo row, and because the find variant
  promotes what this function returns, it stops the fix reaching the find's `foundAt` too. The
  check runs **before the provider is asked**, so off means no position is requested, not one
  requested and discarded.
- `freshDeviceLocation` (`:339`, gate at `:345`) — the held fix a find started from the Journal
  takes at creation. Not a photo path at all; gated because answer 1 said finds too.

Both log at INFO when they decline, naming the setting, so the fallback is never silent.

**What is deliberately not gated**, and is tested as such: a location the user supplied
themselves. The map's tapped or centred point reaches `onStartNewEntry` as an argument and never
touches `freshDeviceLocation`, so switching the setting off cannot override a point the user
chose. The Add/Change Location picker is untouched. An imported photo's own EXIF coordinate is
untouched, per answer 2.

### Wiring

`MainActivity.kt:73-74` hands `AvailabilityViewModel` the repository's two methods as function
references; `:108` hands `MushroomLogViewModel` a read. Both ViewModels read the same repository
**independently** — nothing is pushed from one to the other — so there is no window in which the
capture path acts on a value the Settings screen has already changed.

Both ViewModel parameters are defaulted (`AvailabilityViewModel`'s pair to a success-with-`true`,
`MushroomLogViewModel`'s to `{ true }`), the same "borrow the one capability" shape those classes
already use for `getOfflineRegionReferenceCount`, `getPhotoEntryReferenceCount` and `currentFix`.
That is a decision with a cost, recorded in "Decided beyond scope" below.

**A read failure fails closed, unlike the never-set default** (`MainActivity.kt:108-112`). The
preference defaults to *on* when it was never set; an unreadable preference is a different case
and is treated as *off*, logged through `androidErrorLog`. The worse of the two errors is
capturing a position the user switched off.

---

## The premise that does not hold

Answer 2 states, as the reason no stripping is needed: *"Photos from inside the app already have
location removed by default."* The explanation text makes the same claim to the user: *"When
saving photos, metadata is stripped of the location data."*

**Neither is supported by the code, for camera captures.** This is reported, not fixed, because
the owner scoped stripping out of this change explicitly.

Where the belief comes from: `FilePhotoStore`'s own doc comment says the platform's GPS-EXIF
redaction "keeps the stored copy free of embedded GPS EXIF, for `GalleryImportPhotoSource` **and
`CameraCapturePhotoSource` alike**." That claim is wrong for the camera half, and the reason is a
code-level fact, not a judgement:

- The platform's redaction (API 29+, bypassed only via `MediaStore.setRequireOriginal`) applies to
  **MediaStore** content URIs.
- A camera capture's URI is **not** one. `CameraCaptureFiles.newCapture()` returns
  `FileProvider.getUriForFile(...)` over `filesDir/captures/<uuid>.jpg`, this app's own provider
  and its own file (`CameraCaptureFiles.kt`, `newCapture`). `PhotoAcquisitionLaunchers.kt:84`
  wraps exactly that URI as the `CameraCapturePhotoSource`, and it is the only construction site.
- `FilePhotoStore.persist` copies bytes from that URI verbatim. No MediaStore is involved at any
  point, so no redaction can apply, on any API level.

So whatever GPS EXIF the camera app wrote into that file is still in the stored copy. Whether a
given camera app writes any is device-dependent and **was not verified here** — no such file
exists in this container. What is verified is that the mechanism the doc comment credits cannot
apply to camera captures.

For imports the doc comment is right on API 29+, and already records its own gap on API 26-28,
which is this app's `minSdk` range.

**Consequence, stated plainly:** the explanation text now shipping tells the user their photo
metadata is stripped of location. For an in-app camera capture, this change does nothing to make
that true. Closing it needs an actual strip on the persist path, which is a separate piece of
work. The `FilePhotoStore` doc comment's "alike" claim should be corrected whether or not that
work happens, since it is what the belief rests on; it was left alone here rather than edited in
passing, because a privacy claim is worth its own change and its own device check.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (this branch at `747c6bd`) | 176 | 1405 | 0 | 0 | 24 |
| after (this branch at `c5acbf4`) | 177 | 1416 | 0 | 0 | 24 |

Eleven tests added: `DataStorePhotoLocationPreferenceRepositoryTest` (5, new),
`MushroomLogViewModelTest` (+5), `AvailabilityScreenSettingsPanelTest` (+1). Skip count unchanged;
the CI allowlist untouched. `assembleDebug`: exit 0 on `c5acbf4`. 1405 + 11 = 1416, counted from the XML, not remembered. **Container runs on Linux, not
GitHub Actions.**

**The 12-failure Windows path-length pool is absent**, as it is on Linux: zero failures in either
run, so there was no pool to subtract. Reported as absent, not passed over.

### The tests

- `DataStorePhotoLocationPreferenceRepositoryTest:45` — an untouched install reads on, and the
  documented constant is on; `:53` round-trips both ways; `:70` an explicit `false` is not
  confused with never-set; `:106` the per-test file deletion actually resets the store.
- `MushroomLogViewModelTest:1335` — off, with a fresh fix in hand: the find is still created, with
  no location, no error, and a log line naming the setting.
- `:1358` — off: a caller-supplied location is still honoured.
- `:1370` — off: a camera capture inside a find attaches the photo, leaves both the photo row and
  the find without a location, **and the provider is never called** (`callCount == 0`).
- `:1391` — off: the Album's own camera path, with no find in sight, also asks for nothing.
- `:1410` — on: the provider is called exactly once, which is what makes the two zeros mean
  something rather than being satisfied by a path that never runs.
- `AvailabilityScreenSettingsPanelTest:731` — through the real checkbox row: the label and the
  full explanation render, nothing is written merely by opening Settings, the first tap turns it
  **off** (proving the default is on), and a second turns it back on.

The call-count assertions exist because a returned value alone cannot distinguish "not requested"
from "requested and discarded", and the claim being made is the former.

### Failing first

**The one test that failed was a test I had written wrong, and it found a production constraint.**
`an off value survives a fresh repository instance` constructed a second repository to read back
what the first wrote. It cannot work: DataStore throws
`IllegalStateException: There are multiple DataStores active for the same file` when a second
instance is created while the first is alive, and this version exposes no close. The test was
replaced by two — one making the value claim against a single instance, one asserting the
constraint itself (`:94`) — rather than dropped silently. What it means for production is that
this repository must be constructed exactly once, which `AppContainer` does as a single `val`; a
second construction would throw at first use rather than silently diverge.

The other three new behaviours passed on first run, so they were proved by revert instead. Three
one-line reverts through the standing runner (saved copy before editing, previous XML deleted,
compile-error guard, restore from the copy, byte compare); no compile errors, and the tree was
identical to the pushed commit afterwards:

| revert | failures produced |
|---|---|
| the capture gate removed | 2: "the find gets no location expected null, but was LatLng(45.5, -122.6)"; the Album case, "expected null, but was 45.5" |
| the find-creation gate removed | 1: "but with no location expected null, but was LatLng(45.5, -122.6)" |
| `PhotoLocationSection` removed from the panel | 1: "Action performScrollTo() failed" (no such node) |

---

## Disclosure

### Confirmed by observation

- Every file:line reference above, read in this tree.
- That a camera capture's URI is a `FileProvider` URI over this app's own file, and that
  `PhotoAcquisitionLaunchers.kt:84` is its only construction site — by reading both files.
- The DataStore multiple-instance constraint, from a real failure message in this container.
- Every count and failure message, from XML and build logs here.

### Inferred, not observed

- **That camera apps write GPS EXIF into the capture file.** The code makes it *possible*; whether
  a given camera app does it is device- and OEM-dependent and nothing here proves it happens. The
  finding above is about the mechanism, not about an observed leak.
- That the checkbox and its explanation read well on a real phone. Robolectric lays the text out;
  nobody looked at it.

### Could not be determined

- **Whether any stored photo in this project actually carries GPS EXIF.** No such file exists in
  this container, so the tag was not read from one, exactly as in the orientation dispatch.
- Whether the explanation's "metadata is stripped" claim is true in practice on the owner's
  device. That is one `exiftool` run on a photo the app stored, and it settles the finding above
  in either direction.

### Premises that were wrong

- **"Photos from inside the app already have location removed by default"** — not supported by the
  code for camera captures; see the section above. This is the report's main finding.
- **`FilePhotoStore`'s own doc comment**, which says the platform redaction covers
  `CameraCapturePhotoSource` "alike" — the same error, and the likely source of the belief. Left
  uncorrected here deliberately, flagged for its own change.
- My own opening premise, that this request needed an EXIF strip and a retroactive scrub, was
  wrong about what was wanted: the owner scoped both out. Asking first is what caught that, before
  any of it was built.

### Decided beyond scope

- **Defaulted ViewModel parameters rather than a fourth non-defaulted repository on
  `AvailabilityViewModel`.** Thirteen test files construct that ViewModel, each with its own
  per-file stubs and no shared helper; a non-defaulted parameter meant thirteen new stubs for a
  setting none of them exercise. The cost of the default is that a wiring mistake in
  `MainActivity` would leave the checkbox inert rather than failing loudly. Mitigated only by the
  default matching today's behaviour, and recorded here rather than left implicit.
- **A read failure fails closed** while a never-set preference defaults on. Not asked for; the
  asymmetry is deliberate and documented at both the interface and the wiring.
- **Declines are logged at INFO**, naming the setting, under CLAUDE.md's rule that no fallback
  fires silently.
- **The label and explanation are file-scope constants**, so the test cannot drift from the panel.

### Checks that did not fire, and empty results

- No CI run at the time of writing; both runs are container runs.
- No test asserts the `MainActivity` wiring of either ViewModel — `MainActivity` has no test, the
  same standing gap every earlier dispatch here records. Both lines are read, not exercised.
- No test covers the fail-closed read path, for the same reason: it lives in the `MainActivity`
  lambda. The behaviour is one `getOrElse`, read and not run.
- `verify-design-tokens.sh` not run; the new section uses `MaterialTheme` roles only, no colour
  literal and no palette import, but that is read rather than run.
