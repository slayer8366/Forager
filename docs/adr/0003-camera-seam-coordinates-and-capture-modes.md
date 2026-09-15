# ADR 0003: The camera seam, its coordinate convention, and how capture modes widen it

## Status

Accepted, from the owner's plan of 2026-09-14/15 following the eight-item verification pass on
PR #102. Records conventions for work that has not been built (tap-to-focus, torch, a YUV burst
mode, a RAW research mode, an OEM Night extension) so that none of it forces a signature change,
a fixture rewrite or a migration. The groundwork that makes these conventions cheap to follow
landed with this ADR: `CaptureOutcome`, one bind function, a shared observable fake.

Two items are deferred with their reasons, not decided: see "Deferred".

## Context

`CameraCaptureSession` (`app/src/main/java/com/zynergylabs/forager/app/photo/CameraCaptureSession.kt`)
is the interface this project owns over CameraX, per CLAUDE.md's rule that an external
integration is wrapped rather than depended on directly. It exists for a reason that is not
tidiness: CameraX cannot run under Robolectric at all, so the interface is what makes the camera
*screen* testable while the untested surface stays one file, `CameraXCaptureSession.kt`.

Today the seam has two members, `state` and `capture(File): Result<CaptureOutcome>`. Every planned
feature widens it. The verification pass found that the shape of the widening was undecided in
three places — where coordinates are expressed, how a feature reads a capability, and what a
non-JPEG capture writes back — and that deciding them late would mean deciding them inside a
feature PR. This ADR decides them first.

## Decision: coordinates cross the seam as fractions of the field

Any zone a feature names — a focus point, a metering region, later a crop — is expressed at the
seam as **fractions of the viewfinder field in `[0, 1]`**, x rightward and y downward, in the
viewfinder's own orientation as displayed. Conversion to CameraX's `MeteringPoint` happens
**inside** `CameraXCaptureSession`, using the `PreviewView`'s own `MeteringPointFactory`, which is
the only object that knows the view's size, scale type and sensor mapping. Nothing outside the
seam sees pixels, sensor coordinates or normalised device coordinates.

Rejected: passing `Offset` in view pixels through the interface. The fake would then have to
model a view size, the dialog would have to know one, and every test would carry a magic
dimension. Fractions make the fake trivial and the dialog's gesture math independent of the
device.

## Decision: the control surface pattern

Each feature adds **one method** to `CameraCaptureSession` (`enableTorch(Boolean)`,
`focusAt(x: Float, y: Float)`) and **one capability read** exposed by the session
(`hasFlashUnit`, `isFocusMeteringSupported`), sourced from the kept `Camera`'s `cameraInfo`
(`CameraXCaptureSession.camera`, kept since tranche one for exactly this). A capability the device
does not report is exposed as **unsupported**, never guessed and never defaulted on: CLAUDE.md,
lines 46–49, "A device- or API-reported capability range … describes what's possible, not what's
safe to use. Apply an explicit operating limit rather than trusting the reported range as-is."
The dialog renders an unsupported control absent or disabled with a reason, the same way
`CameraSessionState.Unavailable` carries one today.

The fake (`FakeCameraCaptureSession`, `app/src/test/.../photo/`) grows the same two members per
feature, scripted, so the screen's behaviour on a device without a flash is a Robolectric test
and not a device check.

## Decision: the tap gesture lives in the dialog

Tap-to-focus's gesture is detected in `InAppCameraDialog`, over the viewfinder slot, and reaches
the session as the fractions above. The slot `viewfinder: @Composable (Modifier) -> Unit`
(`InAppCameraDialog.kt`, the `viewfinder` parameter) gains **one callback** when tap-to-focus
lands, `onTap: (x: Float, y: Float) -> Unit` in fractions of the slot's own size, computed from
the slot's `onSizeChanged` bounds. The `CameraXCaptureSession.Viewfinder` implementation ignores
it; the dialog owns the gesture so the fake never has to.

Rejected: the session detecting taps on its own `PreviewView`. It would put a gesture in the one
file no test can reach, and the focus indicator the dialog draws would have no source of truth.

## Decision: the capture return type is a value, widened once

`capture` returns `Result<CaptureOutcome>` with `file` and `imageFormat`. `imageFormat` has **no
production reader**; it is the datum the RAW research mode reads, and it is the one thing
`ImageCapture.OutputFileResults` provides in 1.6.2 (confirmed by `javap` on the pinned artifact)
that the file does not already imply. Widening the return type is the change every capture mode
forces, and it was cheaper to make once, now, at five edit sites, than under a feature. A field
with no reader is otherwise the thing CLAUDE.md's reachability rule asks about, which is why this
paragraph exists.

## Decision: binding goes through one function

`CameraXCaptureSession.bindUseCases(provider, lifecycleOwner, selector, useCases)` is the one
place use cases meet a selector and a lifecycle. A burst adds `ImageAnalysis` to the list; OEM
Night passes an extension-enabled selector; neither edits the body. `SessionConfig` and the
`bindToLifecycle(LifecycleOwner, CameraSelector, SessionConfig)` overload exist in camera-core and
camera-lifecycle 1.6.2 (confirmed by `javap`) and are the path if the varargs bind becomes
awkward — for a required frame rate, say. Not adopted until something needs it.

## Decision: what non-JPEG captures write back

**Burst-produced images** (YUV frames merged in-app) are written as JPEG through the same
`FilePhotoStore.persist` path as a single capture, with EXIF `ORIENTATION_NORMAL` and the
rotation **baked into the pixels** at merge time. A merged image has no camera-written
orientation tag to preserve, the merge already holds every pixel in memory, and one baked
rotation costs nothing there — the opposite of the single-capture case, where baking would cost
a JPEG generation and a full-resolution bitmap (`PhotoMetadataScrub.kt`, "Lossless, and
streamed"). The display path (`readPhotoOrientation`, `DecodedPhoto`) needs no change to show
them.

**Research bundles** (RAW frames, metadata sidecars, anything the research mode keeps for
analysis) are **sessions in their own directory**, `filesDir/research/<session-id>/`, never
`LogPhoto` rows, never under `photos/`, and **never swept** by `CameraCaptureFiles.sweepOrphans`,
which walks `captures/` only. They bypass `persist`, so they are not scrubbed either; a research
bundle is the owner's own instrument data, not a photo being shared, and the privacy claim in
the scrub's header is about the Journal's photos.

## Decision: OEM Night over an in-app merge, when available

Where the device offers a Night extension through `camera-extensions`, it is preferred over the
in-app YUV burst merge, because the OEM pipeline has the sensor's own calibration and the
in-app merge does not. The burst mode remains the path on devices without one. The choice is
made per device from the extension's availability, exposed as a capability like any other
(above), not assumed.

## Records: the allowlist scrub reverses "photo-geodata decision 5", and why

`FilePhotoStore.kt`'s header used to end: *"Stripping GPS EXIF from the destination file directly
(rather than relying on per-API-level redaction) would close this gap but is explicitly out of
scope for this dispatch — see the photo-geodata amendment's decision 5."* `scrubPhotoMetadata`
does exactly that stripping, for captures, since 2026-09-14, so decision 5 is reversed for the
capture half and stands for imports.

**The amendment itself is not in this tree.** Five source files cite it (`MushroomLogViewModel.kt:159`,
`Migrations.kt:724`, `FilePhotoStore.kt`, `PhotoAcquisitionLaunchers.kt`, `LogPhotoEntity.kt`), and
no file under `docs/` contains the word "geodata" (grep, 2026-09-15). Decision 5's content is
known here only from that quoted header. Whether the amendment exists on another branch was not
checked; the same happened with a pulse document earlier in PR #102, which turned up at the head
of an unmerged branch. Recorded as unverified rather than cited as if read.

Why reversed: the redaction decision 5 relied on is a `MediaStore` behaviour and never applied to
a capture, whose URI is this app's own `FileProvider` (found 2026-09-14). The premise did not hold
for the half it was scoped out of, so the scope moved.

## Deferred, with the reason recorded

- **`camera-extensions` in the version catalog.** Not pinned until the Night stage starts. No
  signature impact; adding it is a catalog and `build.gradle.kts` change only.
- **`.jpg` hardcoded in `CameraCaptureFiles.kt:26` and in `FilePhotoStore.persistOnIo`.** Research
  bundles bypass `persist`, so `LogPhoto` stays JPEG by design and the hardcoding is correct for
  the one path that keeps it. The verification pass's finding was right; the answer is that this
  is not the path RAW will use.
- **`StateFlow` on the interface.** The interface property is already Compose-agnostic (a plain
  `val`); only the implementation is Compose-backed, and no non-Compose consumer exists. Revisit
  when a ViewModel or burst controller owns a session.

## Order, and what proves the groundwork was right

Torch first, then tap-to-focus (owner, 2026-09-15: "Do torch first"). Torch is the smaller
widening — `hasFlashUnit` and `enableTorch`, no coordinates — so it is the first exercise of the
control surface pattern. If both land without touching the fake's shape, the return type or the
bind function, this ADR's groundwork was the right groundwork. Torch waits for PR #102 to merge,
because it is the first feature to read the kept `Camera`, and that read should sit on top of
whatever orientation fix the device check settles, not beside it.
