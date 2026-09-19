# Scrub a capture's EXIF, keep its orientation: completion report

**Date:** 2026-09-14
**Request:** the owner's, in their own words. First: *"The EXIF should be scrubbed entirely when
taking the photo using the in app camera. Is that not the case?"* Then the design: *"The idea I
had is to read the orientation, strip the metadata, and then apply the orientation."* Then the
refinement that changed the shape of it: *"Would it be terrible to just reapply it to the metadata
after stripping the metadata?"* Scope, on being asked: **captures only**, imports untouched.
**Branch:** `claude/new-session-vto65i`, PR #102, open and not merged. Base at §0 and again before
the push: `origin/main` at `175b050`, branch at `3bd3b21`; the remote had not moved either time.
No schema change, no migration, no new dependency.

This closes the finding the previous dispatch opened and could not fix under its own scope
(`2026-09-14-photo-location-setting-completion-report.md`, "The premise that does not hold").

---

## §1 — What was actually wrong

The finding, restated from the code rather than from the report that carried it: `FilePhotoStore`'s
doc comment claimed the platform GPS-EXIF redaction kept the stored copy GPS-free "for
`GalleryImportPhotoSource` **and `CameraCapturePhotoSource` alike**." It cannot. Redaction is a
`MediaStore` behaviour on `content://` MediaStore URIs; `CameraCaptureFiles.newCapture()` returns
`FileProvider.getUriForFile(...)` over `filesDir/captures/<uuid>.jpg`, this app's own provider over
its own file, and `PhotoAcquisitionLaunchers.kt:84` is the only site that wraps it as a
`CameraCapturePhotoSource`. No MediaStore is in that path on any API level, so nothing was ever
redacted on the capture half, and `persist` copied the camera app's bytes verbatim.

**Whether a given camera app writes GPS into that file was not verified and is not verifiable
here** — no such file exists in this container, and it is device- and app-dependent. It no longer
decides the outcome: the metadata goes either way. Stated so nobody later reads this report as
evidence that it did.

## §2 — The design, and why the owner's refinement is the stronger one

I had recommended keeping the orientation *tag* and removing the identifying ones. The owner's
question — reapply it after stripping — is better, and the difference is categorical rather than
a matter of degree:

- Removing the tags you don't want is a **denylist**. `ExifInterface` exposes 155 `TAG_` constants;
  it is as complete as the list someone remembered to write, it cannot reach a tag the library has
  no constant for, and it does not touch a non-EXIF segment at all — IPTC in APP13, XMP, a JFXX
  thumbnail.
- Rebuilding the file with only the segments you name is an **allowlist**. Whatever is not
  deliberately put back is gone, including whatever did not exist when this was written.

`PhotoMetadataScrubTest`'s "every other EXIF tag goes too" case exists to make that concrete: it
asserts on `TAG_MAKE`, `TAG_MODEL`, `TAG_DATETIME_ORIGINAL` and `TAG_USER_COMMENT`, four tags
deliberately not named anywhere in the production code. A denylist could only have caught them by
having thought of each one first.

## §3 — What is kept, and why each one

Three segment kinds survive (`PhotoMetadataScrub.kt`, `isKeptSegment`). None describes the
photographer.

| kept | why |
|---|---|
| APP0 `JFIF` | pixel density and aspect. Structural. |
| APP2 `ICC_PROFILE` | the colour profile. A Display P3 photo whose profile is discarded renders colour-shifted in any colour-managed viewer, and this app exists so a forager can judge cap and gill colour. Dropping it is a privacy win of zero and an accuracy loss that matters here. |
| APP14 `Adobe` | declares the colour transform for the scan data. Dropping it can change how the image is *decoded*, not merely how it is described. |

`JFXX` — the APP0 *extension* — is dropped even though `JFIF` is kept, because it can carry a
thumbnail, and a stale embedded thumbnail is the classic EXIF leak: a cropped photo whose thumbnail
still shows the original. Everything else goes: APP1 (EXIF with GPS, timestamps, maker notes and
the IFD1 thumbnail; and XMP), APP13 (IPTC), every other APPn, and COM comments.

## §4 — Lossless, and why that was the deciding constraint

Only the segment structure is rewritten. The entropy-coded scan data is copied through byte for
byte, so there is no decode, no re-encode, and no full-resolution bitmap. `PhotoMetadataScrubTest`
asserts the scan bytes are identical across a scrub rather than taking it on trust.

The rejected alternative was baking the rotation into the pixels and writing no orientation tag at
all. That costs one generation of JPEG quality and peaks around 98 MB for a 12 MP photo, 400 MB for
a 50 MP one (arithmetic from the earlier orientation dispatch, re-derived there, not quoted from
memory). At save time, on a phone, on every capture. Recorded here because the choice is not
obvious from the code.

## §5 — Failure is fail-*open* on privacy, deliberately

The rewrite goes to a sibling `.scrub` temp file and replaces the original only once written whole.
Any failure deletes the temp, leaves the original exactly as it was, and logs at WARN. A non-JPEG
is reported as `NotAJpeg` and left alone.

That is a deliberate choice against the privacy-safe direction, and it is worth stating as such: a
photo that keeps its metadata is the behaviour that existed before this function, whereas a lost or
truncated photo is unrecoverable field data. `ScrubOutcome` has three cases so a caller or a test
can tell them apart rather than infer them from the file.

## §6 — Scope: captures only

`FilePhotoStore.persist` calls the scrub only for `CameraCapturePhotoSource`, per the owner's
ruling: *"Photos imported from outside the app are to remain untouched."* An import's metadata is
the photographer's own, and `a persisted import is left exactly as it was, metadata and all`
asserts the persisted copy is byte-identical to its source, so the scope cannot drift silently.

---

## Evidence

### Suite counts, from JUnit XML (`app/build/test-results/testDebugUnitTest/TEST-*.xml`)

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`3bd3b21`) | 177 | 1416 | 0 | 0 | 24 |
| after (`48d9b86`) | 178 | 1429 | 0 | 0 | 24 |

1416 + 10 (`PhotoMetadataScrubTest`, a new suite) + 3 (`FilePhotoStoreTest`) = **1429**, counted
from the XML, not remembered. Skip count unchanged; the CI allowlist was not touched. `assembleDebug`
exit 0 at `48d9b86`. **Container runs on Linux, not GitHub Actions**; the 12-failure Windows
path-length pool is absent here, nothing to subtract.

### A probe that corrected a test, not the code

`a photo with no orientation tag is still scrubbed, and none is invented` first failed asserting
the tag would read `-1` after the scrub. It read `0`. A throwaway probe (run, read, deleted) showed
why: **`ExifInterface` answers for `TAG_ORIENTATION` on a JPEG that has no EXIF segment at all.**
The bare fixture reports `0` (`ORIENTATION_UNDEFINED`) with `hasAttribute` returning true, before
anything has been written to it, and with no APP1 anywhere in the file.

Per CLAUDE.md the expectation was wrong, not the code, so the expectation moved. It also matters
beyond the test: a tag read cannot distinguish "absent" from "present and undefined", which is
exactly why `scrubPhotoMetadata` reads orientation with `ORIENTATION_UNDEFINED` as its own default
and treats both as nothing to put back. The test now asserts on the **segment list** — no APP1 was
written back — because that is the claim that can actually fail. Recorded on the test itself.

### Revert checks

Six, through the runner from the earlier dispatches: saved copy taken before editing, previous XML
deleted, build log checked for compile errors before results are read, restore from the saved copy
and byte-compared. No compile errors in any of the six; `git status` clean against `48d9b86`
afterwards, so the forward change survived all of them.

| revert | failure produced |
|---|---|
| the scrub is never called (`if (false)`) | `a persisted capture carries no GPS EXIF` — "the stored copy must not carry the coordinate the camera app wrote"; and the orientation test's companion "and the coordinate still goes" |
| orientation is not put back | three: both scrub tests that assert it, plus `a persisted capture keeps the orientation the display path reads` — each "expected:&lt;6&gt; but was:&lt;0&gt;" |
| allowlist's `else` keeps everything | seven, including "the coordinate must be gone", "a comment segment carries free text and must not survive", and `TAG_MAKE` surviving as "ACME" |
| APP0 unfiltered (`-> true`) | `the JFIF header stays but a JFXX thumbnail extension is dropped` — "exactly the JFIF one survives expected:&lt;1&gt; but was:&lt;2&gt;" |
| APP2 `ICC_PROFILE` dropped | `an ICC colour profile survives both the strip and the orientation reapply` — "the colour profile must still be there" |
| the capture-only scope removed | `a persisted import is left exactly as it was` — "array lengths differed, expected.length=531 actual.length=287" |

Each message is specific to its own edit — a failure another of these reverts could have produced
would be the stale-XML signature CLAUDE.md records, and none appears here.

**The third revert taught something the first two did not.** I predicted the JFXX test would fail
under `else -> true` and it did not, because `APP0` is matched by its own `when` branch before
`else` ever runs. The prediction was wrong, the code was right, and the two APP-specific reverts
above exist because of it. A revert whose prediction misses is worth more than one that lands: it
found a test the earlier reverts did not exercise at all.

---

## Disclosure

### Confirmed by observation

- Every count and failure message above, from JUnit XML and build logs in this container.
- The `FileProvider`-not-MediaStore path, by reading `CameraCaptureFiles.newCapture()` and
  `PhotoAcquisitionLaunchers.kt:84`.
- `ExifInterface` reporting `0` for a JPEG with no EXIF segment: by probe, described above.
- The scan data being byte-identical across a scrub: asserted, not inferred.

### Inferred, not observed

- That the three kept segments are *sufficient* for correct rendering on every viewer. Reasoned
  from the JPEG spec's roles for JFIF, ICC and Adobe, and tested for survival, not for rendering.
  Nobody looked at a scrubbed photo on a screen.
- That real camera-app JPEGs parse under `stripJpegMetadataSegments`. The fixtures are real JPEGs
  with real EXIF, but they are small and uncomplicated. A file the walk cannot parse is refused and
  left whole, so the failure mode is "metadata kept", not "photo damaged" — which bounds the risk
  without removing it.

### Could not be determined

- **Whether any camera app on the owner's devices writes GPS into a capture.** Not verifiable in
  this container, and it does not change what ships.
- **Whether a real 12 MP capture round-trips visually unchanged.** Device check. The scan bytes are
  identical by assertion, which is the strongest claim available without a device.

### Premises that were wrong

- **Mine, in the recommendation the owner overruled**: that keeping the orientation tag and
  removing the identifying ones was the sensible version. It was the weaker one, for the reason in
  §2.
- **My prediction for the third revert**, corrected above.
- **The test's `-1`**, corrected above.

### Decided beyond scope

- **Correcting `FilePhotoStore`'s "alike" doc comment.** The previous report said it should be
  corrected whether or not this work happened. It is corrected here, in the change that makes the
  claim true for captures.
- **Rewriting `FilePhotoStoreTest`'s class doc** rather than deleting the paragraph that said the
  GPS-free claim could not be asserted. That paragraph's reasoning was sound about what it
  described, and the import half still rests on the platform, so both halves are recorded.
- **Keeping ICC and Adobe.** Nobody asked; dropping everything would have been the literal reading
  of "scrubbed entirely." Reasoned in §3, and it is the kind of call that should be visible rather
  than silent.

### Checks that did not fire, and empty results

- No CI run at the time of writing.
- `FilePhotoStoreTest`'s pre-existing capture tests persist non-JPEG bytes (`1, 2, 3, 4, 5`), which
  the scrub returns `NotAJpeg` for and leaves untouched; they passed unchanged, which is the
  intended no-op rather than a check of anything.
- No test asserts the log line a failed scrub writes.
