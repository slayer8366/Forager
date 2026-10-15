Investigation for the owner's review; nothing dispatched.

# 2026-10-15-07: Going below CameraX for RAW bursts, and whether Forager's RAW is the sensor's real RAW (RECORD -1056)

Written for the planner (Rowan Waymark), 2026-10-15 20:44 UTC (13:44 PDT), by a read-only investigator subagent. Nothing was built, no code was changed, nothing was installed, launched or tapped on any phone, nothing was committed. One read-only `adb shell dumpsys media.camera` and three `getprop` reads were taken from the S22 Ultra (R5CT321008R). The S26 Ultra was **not attached** (`adb devices -l` listed only the S22), so it has no dumpsys here.

Base read: Forager `origin/main` `cb25b582` (PR #227 merge), `origin/dev` `7a2c0286` (burst probe). RECORD read-only from `/home/zynergy-labs/.cache/forager-wt/records/RECORD.md`: -973, -974, -975, -996, -997, -998, -1009, -1038, -1042, -1043, -1044, -1046, -1053, -1055, -1056. Audit `prompts/preserved/2026-10-15-05-averaging-research-audit.md` §0, §2.6, §3.1 to §3.5, §8.

How claims are marked: **READ** (from the named file, line, dumpsys line or URL), **MEASURED** (computed here, method stated), **OBSERVED** (seen in a device log on disk), **INFERRED**, **COULD NOT DETERMINE**. Device files were read in place and are described in words only; nothing was copied out of `~/Zynergy/device-evidence/`. Working files (the dumpsys capture, the platform reference pages as text, scripts) are under `/tmp/claude-1000/belowcx/` and are in no repository. The dumpsys capture is `/tmp/claude-1000/belowcx/s22-dumpsys.txt` (17,912 lines, taken 13:31 PDT while the owner had Forager's camera open on the S22); "dumpsys:N" below means a line of that file. Vendor tag prefixes and sensor part names in the dumpsys are withheld under the generic-description rule; they are described by what they hold.

---

## 0. The short version

**Part A, bursts.**
1. **The S22's sensor can deliver 12 MP RAW frames at 30 a second.** RAW_SENSOR 4000x3000 has a minimum frame duration of 33.3 ms and **no stall duration** (READ, dumpsys stream tables; the probe logged the same). The camera is the platform's highest hardware level, LEVEL_3, with BURST_CAPTURE, MANUAL_SENSOR, AE and AWB lock, per-frame control, and pipeline depth 8 (READ). Through the platform interface, a burst of 8 RAW frames would take about **0.23 s** of the photographer holding still in bright light and **0.47 s** in dim light. Through CameraX today it takes 1.6 s and about 1.9 s (-1055, -1053). These platform figures are INFERRED from the frame durations and not yet measured.
2. **CameraX cannot get there.** Its still-capture path issues one request at a time ("There is already a request in-flight.") and holds at most 4 images (READ, `TakePictureManagerImpl` and `CaptureNode.MAX_IMAGES = 4` in the pinned 1.6.2 artifact, javap). It has no burst or RAW-streaming API. The 1.7.0 alphas (latest alpha03, 2026-08-12) add none (READ, release notes).
3. **Options** (section A.2):
   - **(a)** Move the in-app camera to the platform interface.
   - **(b)** Keep CameraX for preview and single shots, and hand the camera to a platform session only for a burst.
   - **(c)** CameraX workarounds. None of these is sound.

   All of them fit behind the existing `CameraCaptureSession` interface (READ, `photo/CameraCaptureSession.kt:29`), so domain code is unaffected. The recommendation is a **dev-build platform burst probe first** (small; it answers both parts' open questions on the two phones), and then (a) or (b) on its numbers.
4. **The RAW+JPEG burst loss has a sharper shape than "7 of 8 lost".** In both probe runs every request got exactly one image, RAW and JPEG alternating, and only the last got both (OBSERVED, `probe-run2-bright.jsonl` frame lines). A platform session can carry RAW plus a small YUV or JPEG for the placeholder in one request; that combination is guaranteed on these devices (READ, section A.3).

**Part B, the RAW.**
5. **S22: the saved RAW is a real Bayer mosaic, not a demosaiced image.**
   - It is **binned 3x3** (INFERRED, strong): the physical main camera behind the logical camera carries vendor tags for a 12000x9000 RAW10 stream that the public API does not offer, and the public RAW is 4000x3000, one third per side (READ).
   - Measured noise is close to spatially white, with no demosaic-style cross-colour correlation (MEASURED, with positive and negative controls).
   - The camera reports **lens shading correction already applied to its RAW** (READ, `lensShadingApplied TRUE`). The noise at the corners shows no amplified digital gain, though (MEASURED). That contradiction is unresolved.
   - Whether the phone ran **raw-domain noise reduction** on these shots is **unclear**. The capture's noise-reduction mode was never logged. The platform documentation explicitly allows raw-domain noise reduction in every mode except OFF (READ). The only spatial signature found is very weak (correlation about 0.03).
6. **S26: also a real Bayer mosaic (GBRG).**
   - Whether it is binned is **not determined** (no dumpsys).
   - Its own noise profile matches the measured noise (MEASURED).
   - Its noise shows more spatial correlation than the S22's, but the indoor scenes have too much texture to tell whether that comes from processing (**unclear**).
7. **Getting closer to unprocessed:**
   - Request `NOISE_REDUCTION_MODE_OFF` and `HOT_PIXEL_MODE_OFF` (and possibly `SHADING_MODE_OFF`) on RAW captures. All three are offered on the S22 (READ).
   - On CameraX, options set on the RAW capture **also reach the live preview**: the S22's repeating preview request carries the lens-shading-map option that only the RAW capture sets (READ, dumpsys). So noise reduction OFF there would also make the viewfinder noisier (INFERRED). A platform session can set it per request.
   - Full-resolution RAW is **not offered** publicly on the S22. Even where offered, a quad- or nona-cell full-resolution RAW is documented as a re-arranged ("remosaiced") mosaic, which is more processed, not less (READ, INFERRED).

---

## Part A: going below CameraX for bursts

### A.1 What the S22 offers a platform burst (dumpsys, READ unless marked)

Taken from the logical back camera 0 and its physical main camera 5 (the main camera is identified by its 6.4 mm focal length and 9.6x7.2 mm sensor, the same as the logical camera, dumpsys:1550 to 1551 and 2089 to 2090 for the logical camera, physical 5 block from dumpsys:2593; `activePhysicalId` "5" in the live frame).

| Item | S22 value | Source |
|---|---|---|
| Hardware level | 3 = LEVEL_3 | dumpsys:2165 (`android.info.supportedHardwareLevel` byte 3) |
| Capabilities (logical 0) | BACKWARD_COMPATIBLE, CONSTRAINED_HIGH_SPEED_VIDEO, RAW, YUV_REPROCESSING, PRIVATE_REPROCESSING, READ_SENSOR_SETTINGS, MANUAL_SENSOR, BURST_CAPTURE, MANUAL_POST_PROCESSING, LOGICAL_MULTI_CAMERA. **No ULTRA_HIGH_RESOLUTION_SENSOR, no REMOSAIC_REPROCESSING.** | dumpsys:1574 to 1575 |
| RAW_SENSOR (format 32) output sizes | 4000x3000 only on the logical camera's stream map (the min-duration table also lists 3648x2736 for the telephotos) | stream configuration and min frame duration tables, logical camera 0 |
| RAW_SENSOR min frame duration | 33,333,333 ns at 4000x3000 (30 fps) | same |
| RAW_SENSOR stall duration | none listed (the stall table lists only JPEG; JPEG 4000x3000 stalls 78 ms) | `android.scaler.availableStallDurations` |
| RAW10 (37) and RAW_PRIVATE (36) | also 4000x3000 at 33.3 ms | same |
| Max output streams (raw, processed, stalling) | 1, 3, 2 | dumpsys:1567 |
| Pipeline max depth | 8 | dumpsys:1570 |
| Partial results | 2 | `android.request.partialResultCount` |
| Sync max latency | PER_FRAME_CONTROL | `android.sync.maxLatency` |
| AE lock / AWB lock | TRUE / TRUE | `aeLockAvailable`, `awbLockAvailable` |
| AE target FPS ranges | [7,10] [10,10] [7,15] [15,15] [7,24] [24,24] [7,30] [30,30] | `aeAvailableTargetFpsRanges` |
| Exposure range, ISO range, max analog ISO | 81.68 µs to 103.4 ms (logical); 81.68 µs to 164.1 ms (physical 5); ISO 50 to 3200; analog up to 800 | logical and physical 5 tags |
| Max frame duration | 164.1 ms | `sensor.info.maxFrameDuration` |
| Noise reduction modes offered | 0 to 4: OFF, FAST, HIGH_QUALITY, MINIMAL, ZERO_SHUTTER_LAG | dumpsys:1564 |
| Hot pixel modes offered | 0, 1, 2: OFF, FAST, HIGH_QUALITY | dumpsys:1536 |
| Shading modes offered | 0, 1, 2 | `android.shading.availableModes` |
| Lens shading map modes | OFF, ON (lens shading map size 17x13) | |
| Hot pixel map modes | OFF only | `statistics.info.availableHotPixelMapModes` |
| Lens shading applied to RAW | TRUE | dumpsys:2097 |
| Timestamp source | REALTIME | |

**From the live session Forager had open (READ, dumpsys device-0 dynamic section; "Stream configuration", dumpsys:1136 to 1198):**
- Three streams: preview PRIVATE 1440x1080, JPEG 4000x3000 (`ImageReader-4000x3000f21m4`), and RAW_SENSOR 4000x3000 (`ImageReader-4000x3000f20m4`). The `m4` is the ImageReader's maxImages of 4, which agrees with `CaptureNode.MAX_IMAGES = 4` read from the artifact.
- Each stream: "max HAL buffers: 8", "Total buffers: 12".
- The preview was producing frames at 33.3 ms (`sensor.frameDuration` 33,333,333 in the latest frame).

**Can a repeating request or `captureBurst` deliver RAW_SENSOR frames at or near the sensor rate?**
- The documentation says a burst is "captured in the minimum amount of time possible, and will not be interleaved with requests submitted by other capture or repeat calls" (READ, https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession, `captureBurst`).
- A repeating request's minimum frame interval is "the normal minimum frame duration corresponding to S, added with the maximum stall duration for S" (READ, https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap, `getOutputStallDuration`).
- With 33.3 ms and no RAW stall, a RAW-only burst or repeating request is bounded at 30 fps by the stream tables (READ arithmetic). The real interval is the larger of that and the exposure time: the dim probe's 66.7 ms exposure gives 15 fps (INFERRED).
- **The caveat that matters** (READ, same page): "Setting any of the processing modes to HIGH_QUALITY effectively results in an indeterminate stall duration for all streams in a request". A burst should set noise reduction and edge to OFF, FAST or MINIMAL, never HIGH_QUALITY. Whether CameraX's still-capture requests carry HIGH_QUALITY on this phone was **not determined**: the probe did not log the modes.
- BURST_CAPTURE guarantees ≥20 fps for maximum-size YUV and says "If the device supports the ImageFormat.RAW10, ImageFormat.RAW12, ImageFormat.RAW14 … then those can also be captured at the same rate as the maximum-size YUV_420_888 resolution is" (READ, https://developer.android.com/reference/android/hardware/camera2/CameraMetadata, `REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE`). RAW_SENSOR (16-bit) is not named in that sentence. The S22 also offers RAW10 at 4000x3000, which is the guaranteed format if RAW_SENSOR turns out to be slower (INFERRED; RAW10 is packed 10-bit, and Forager's copy routine accepts only 2-byte RAW_SENSOR, `CameraXRawCapture.kt:113-115`).
- **COULD NOT DETERMINE:** the actual sustained RAW_SENSOR burst rate on either phone. Only a device run can measure it.

**Pipeline depth 8:** "A pipeline depth of X stages is equivalent to a pipeline latency of X frame intervals" (READ, https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics, `REQUEST_PIPELINE_MAX_DEPTH`). That is up to about 267 ms at 30 fps between a frame's exposure and its arrival. This is latency, not throughput: the frames still arrive 33 ms apart (INFERRED).

**ImageReader for 8 x 12 MP RAW (READ arithmetic, INFERRED for the pool total):**
- One RAW_SENSOR frame is 4000 × 3000 × 2 B = 24.0 MB on the S22, and 4080 × 3060 × 2 B = 25.0 MB on the S26.
- Eight frames are 192 MB and 200 MB.
- ImageReader `maxImages` "determines the maximum number of Image objects that can be be acquired from the ImageReader simultaneously. Requesting more buffers will use up more memory" (READ, https://developer.android.com/reference/android/media/ImageReader, `newInstance`).
- With the HAL's own 8 buffers per stream (READ, dumpsys "max HAL buffers: 8"), an 8-image reader means a pool of about 16 buffers, about 384 MB of graphics memory, while the burst runs (INFERRED from the dumpsys pattern maxImages 4 → total 12).
- These buffers are native, not the Java heap. The Java heap limit is 256 MiB (OBSERVED, probe `maxMemoryBytes` 268,435,456), so copying 8 frames into `ShortArray`s as today's `copyRawPlane` does (`CameraXRawCapture.kt:118`) would not fit beside the app. The audit's streaming accumulation (fold each frame into one float buffer, then release the image) avoids that (INFERRED).
- The alternative is a reader of 3 or 4 images, with each image released within one frame interval. The probe measured the plane copy at 10 to 20 ms (OBSERVED), which fits in 33 ms only with little to spare.

**Per-frame CaptureResult:**
- "Each capture produces one CaptureResult and image buffers for one or more target surfaces" (READ, `captureBurst`).
- The result matches the image by sensor timestamp, as Forager already does (`CaptureResultCollector`, `CameraXRawCapture.kt:78-102`).
- The probe matched a result to all 8 of 8 RAW-only frames (OBSERVED, `framesWithResult` 8).

**AE/AWB lock and manual exposure for a burst:**
- Both locks are available, and the probe reached them in 220 to 240 ms (OBSERVED, -1053, -1055).
- MANUAL_SENSOR allows fixed exposure time, ISO and frame duration per request.
- PER_FRAME_CONTROL means each request's settings apply to exactly its own frame (READ, `android.sync.maxLatency`), which also serves the later HDR brackets (-974).
- MANUAL_POST_PROCESSING also allows fixing the white-balance gains and colour transform (READ, capability list).

**What else a burst session needs:**
- A preview surface is not required: "RAW MAXIMUM, No-preview DNG capture" is a guaranteed combination.
- But the user expects a live viewfinder. The guaranteed combinations for RAW-capable devices include (READ, https://developer.android.com/reference/android/hardware/camera2/CameraDevice, RAW-capability table):
  - PRIV PREVIEW + RAW MAXIMUM;
  - PRIV PREVIEW + YUV PREVIEW + RAW MAXIMUM;
  - PRIV PREVIEW + JPEG MAXIMUM + RAW MAXIMUM.
- LEVEL_3 adds PRIV PREVIEW + PRIV 640x480 + YUV/JPEG MAXIMUM + RAW MAXIMUM.

The S22 qualifies for all of them (READ, LEVEL_3 plus the RAW capability).

### A.2 How a platform burst coexists with the app's camera: options

What exists today:
- `CameraCaptureSession` (READ, `photo/CameraCaptureSession.kt:29-129`) is the project-owned interface. Its methods:
  - `open` and `close`;
  - `capture`;
  - `captureRaw`;
  - `requestOutputFormat`, `outputFormat`;
  - `capabilities`;
  - flash.
- The screen, dialog and tests use a fake behind it.
- `CameraXCaptureSession` (912 lines), `CameraXRawCapture.kt` (181) and `CameraXCapabilityReader.kt` (148) are the only files that import CameraX (READ, file headers).
- A burst is not yet on the interface. Any option adds something like `captureBurst(frames, settings): Result<Burst>` returning frames and results, or a streaming callback into the averaging engine. That is a domain-shaped addition the domain code can depend on, whichever implementation sits under it (INFERRED).

**Phone-only constraint common to all options.** Only one client can drive a camera device at a time. The live dumpsys shows Forager's client holding device 0, with devices 2, 4, 20, 21 and 23 listed as conflicting (READ, dumpsys:12). CameraX and a platform session cannot both have the back camera open at once (INFERRED from the conflict list and the single-client model).

#### (a) Move the whole in-app camera to the platform interface

- **What the user would experience** (INFERRED; the rates are from the frame tables, not measured):
  - A burst starts on the next frame after the tap, plus the pipeline latency, about 0.1 to 0.3 s.
  - 4 frames span about 0.10 s in bright light and about 0.20 s at a 66.7 ms dim exposure. 8 frames span about 0.23 s and about 0.47 s.
  - The preview never stops: the burst runs in the same session as the viewfinder, and the platform guarantees PRIV PREVIEW + RAW MAXIMUM.
  - A further step, if wanted later: keep a repeating RAW stream with a small ring buffer, so a burst can include frames from *before* the tap (zero shutter lag). This costs continuous 30 fps RAW traffic, with power and heat not measured.
- **Size: large.** A new `PlatformCaptureSession` beside `CameraXCaptureSession` (a new path, per CLAUDE.md "Building"), roughly the size of the three CameraX files together, about 1,200 to 2,000 lines (INFERRED). It re-implements what CameraX does today:
  - opening the device and handling errors and disconnects (another app taking the camera, the permission withdrawn);
  - choosing stream sizes against the guaranteed combinations;
  - the viewfinder: surface sizing, aspect, rotation transform, with the window locked and only icons turning (Forager's cardinal rule, unchanged; the transform has to be done by hand);
  - continuous autofocus;
  - the torch;
  - **flash on capture**, which needs the precapture metering sequence (CameraX does this today);
  - JPEG orientation (the HAL-tag finding at `CameraXCaptureSession.kt:125-140` would need re-checking);
  - device quirks CameraX's library carries for many phones.

  Robolectric cannot run it, the same as now (`CameraXCaptureSession.kt:79-85`). Its risk is all on-device.
- **Risks:**
  - regressions in shipped single-shot behaviour (flash, orientation, preview transform, focus);
  - phones other than the two test phones losing CameraX's quirk handling.

  Mitigation: both implementations behind the interface, chosen at the slot (`InAppCameraHost.kt` already chooses the production slot, READ `ui/log/InAppCameraHost.kt:53`), CameraX staying the fallback (INFERRED).
- **Fit:** clean. Domain code keeps depending on `CameraCaptureSession`. The CLAUDE.md integration rule is met as it is today.

#### (b) Keep CameraX for preview and single shots; hand the camera to a platform session only for a burst

- **What the user would experience** (INFERRED; no figure here is measured):
  1. Tap.
  2. CameraX unbinds and its camera closes.
  3. A platform session opens the device, configures preview + RAW, and converges.
  4. The burst.
  5. The platform session closes, and CameraX re-opens and rebinds.

  The closest measured figure: when the dev probe swapped CameraX's own use cases on the already-open device, AE settled **587 to 940 ms** after the new session started (OBSERVED, probe-run1 and probe-run2 run lines, notes "AE settled … ms after the probe's session started"). A full device close and re-open adds to that, by an amount not measured.

  Estimate: **about 0.7 to 1.5 s from tap to the first burst frame, then 0.1 to 0.5 s of burst, then about 0.5 to 1 s before the viewfinder is live again**. The preview freezes or blanks across the swap, unless the platform session draws into its own surface for the burst.

  Copying the last CameraX result's exposure, ISO, white-balance gains and focus distance into manual settings (MANUAL_SENSOR and MANUAL_POST_PROCESSING are offered) could skip most of the convergence wait (INFERRED). Focus distance calibration is only APPROXIMATE on this camera (READ, `lens.info.focusDistanceCalibration`), so a copied focus distance may not land exactly.

  For a still subject the hold-still window is short, but **the shutter lag is long**: the photo is taken about a second after the tap. For a forager framing a mushroom on a tripod or braced, that may be acceptable; handheld, it is the "did it take?" moment the owner's "don't make me think" principle warns against.
- **Size: medium.**
  - A `PlatformBurstCamera` (open, configure, burst, close) of a few hundred lines.
  - A coordination change in `CameraXCaptureSession` to release and re-take the camera (it already unbinds and rebinds for format changes, `rebindCapture`, `CameraXCaptureSession.kt:480-505`).
  - The dev probe already shows how to borrow the camera and hand it back (READ, `CameraXBurstProbeCamera.kt:62-82`), though inside CameraX, not across to the platform.
- **Risks:**
  - The hand-back can leave a dead viewfinder. -1053 checked the camera state for the CameraX-to-CameraX hand-back only.
  - CameraX's own camera-state observer (`watchForStreamConfigErrors`, `CameraXCaptureSession.kt:549-560`) reacting to the platform's interruption.
  - A third party grabbing the camera mid-swap.
  - The shutter lag above.
- **Fit:** clean. The burst becomes one more method on the interface, implemented by composing the two.

#### (c) Anything CameraX itself offers

| Route | What it would change | Verdict |
|---|---|---|
| `ImageCapture` queue (today) | One request in flight (READ, `TakePictureManagerImpl.issueNextRequest`: "There is already a request in-flight."), at most 4 images (READ, `CaptureNode.MAX_IMAGES = 4`). Measured 3.7 to 4.3 fps (-1053, -1055). | The ceiling found. |
| Camera2 interop request options (`Camera2Interop.Extender.setCaptureRequestOption`, `Camera2CameraControl`) | Can set noise reduction, hot pixel, shading, AE/AWB lock and manual exposure on CameraX's requests (READ, javap of `Camera2Interop$Extender` 1.6.2: `setCaptureRequestOption`, `setCaptureRequestTemplate`, `setSessionStateCallback`, `setSessionCaptureCallback`, `setPhysicalCameraId`). Does not change the one-at-a-time queue. | Useful for Part B, not for speed. |
| `setSessionStateCallback` (interop) | Hands the app the platform session object CameraX configured. The app could call `captureBurst` on it, but CameraX owns the RAW ImageReader's surface and routes its images through its own pipeline, so frames from an app-issued burst would arrive at CameraX's 4-image reader with no request to deliver them to. | INFERRED unworkable and undocumented. Not recommended. |
| A second `Preview` use case whose surface is an app-owned RAW ImageReader | Would put a RAW stream into CameraX's repeating request (a stream at preview rate). Preview resolution selection, and whether CameraX accepts a non-PRIVATE consumer, are not documented for this. | INFERRED hack. Not recommended. |
| `ImageAnalysis` | Formats YUV_420_888, RGBA_8888, and from 1.7.0-alpha02 PRIVATE (READ, release notes). No RAW. | No. |
| CameraX Extensions (`ExtensionSessionConfig`) | Vendor modes (night, HDR, bokeh) producing processed output. No RAW output is documented in the release notes read. | INFERRED no. |
| CameraX ZSL | The audit read it as JPEG with PRIVATE reprocessing only (audit §8, UNVERIFIED there). | No for RAW (inferred). |
| 1.7.0-alpha01 to alpha03 (2026-03-11 to 2026-08-12) | New interop configurator API and Kotlin DSL; the old `Camera2Interop.Extender`, `Camera2CameraControl`, `Camera2CameraInfo` and `CaptureRequestOptions` **deprecated** in alpha03; AE/AWB locking in `FocusMeteringAction` (alpha02); PRIVATE ImageAnalysis; night-mode indicator; a ZSL crash fix (READ, https://developer.android.com/jetpack/androidx/releases/camera). **No burst, no RAW streaming.** | Changes nothing for bursts. Note for later: the interop calls Forager uses today are deprecated in the next line. |
| 1.6.0's move to "CameraPipe" | READ in the 1.6.0 notes; an internal stack, not an app API. | No. |

**Recommendation (INFERRED, for the owner to decide):**
1. First, a **dev-build platform burst probe**, the same shape as piece 1. It hands the camera from CameraX to a platform session, takes RAW-only bursts of 8 by `captureBurst`, with and without locks, then hands back. It measures:
   - the real frame interval, the swap time and the hand-back on both phones;
   - one pair of bursts with noise reduction and hot-pixel correction OFF against the default, for Part B (section B.6).

   It is small (debug source set only) and answers every "not measured" above.
2. Then (a) or (b) on its numbers:
   - If the swap in (b) is near half a second and the hand-back is reliable, (b) is the smaller change.
   - If it is a second or more, (a) gives the owner's "frame average users can trust" without the lag, at the cost of re-owning the whole camera.

   This is an architecture decision for the owner (CLAUDE.md, "Working with ambiguity"), not taken here.

### A.3 A JPEG or YUV frame for the journal placeholder from a platform burst

- Guaranteed combinations for these devices include **PRIV PREVIEW + YUV PREVIEW + RAW MAXIMUM** and **PRIV PREVIEW + JPEG MAXIMUM + RAW MAXIMUM** (READ, CameraDevice RAW-capability table).
- In a platform burst, one request (the reference frame) can target RAW + JPEG and the others RAW only. The JPEG's stall applies only "on requests that actually reference a JPEG stream" and "will not cause a frame rate drop unless there are still outstanding buffers for that stream" (READ, `getOutputStallDuration`). On the S22, a 4000x3000 JPEG stalls 78 ms, and a preview-size JPEG 10 to 18 ms (READ, stall table). So one JPEG in a burst of 8 costs at most one frame's delay (INFERRED).
- Alternatively, a preview-size YUV on the reference request, encoded by the app, adds no stall (YUV "will never have a stall duration", READ).
- **The CameraX RAW+JPEG loss, re-read** (OBSERVED, `probe-run1.jsonl` and `probe-run2-bright.jsonl` frame lines):
  - In every RAW+JPEG run, each of the 8 requests received **exactly one** image, RAW and JPEG alternating (requests 0, 2, 4 got one format, 1, 3, 5, 6 the other), and the last request received both. That makes 9 images for 8 requests.
  - Images arrived about 300 ms apart, which is the RAW-only cadence. So it looks less like frames being dropped and more like each capture producing one of its two images (or the images being matched to the wrong requests).
  - The cause was **not determined**. The CameraX 4-image limit and its RAW+JPEG matching are the first suspects (INFERRED). A platform session sidesteps this, because the app matches images to results by timestamp itself.

---

## Part B: is the saved RAW the sensor's actual RAW?

### B.4 Geometry, binning, and what the platform says RAW may include

**S22 main camera (READ, dumpsys):**
- **Sizes.** Logical camera 0 and physical main camera 5 both report pixel array 4000x3000 and active array 0,0,4000,3000 (dumpsys:2091 and physical 5's block; matches -996). Physical size 9.6 x 7.2 mm, so the reported pixel is 2.4 µm (READ arithmetic).
- **A larger private RAW stream.** Physical 5 carries vendor tags the public API does not expose:
  - a "high-res RAW stream configuration" holding 12000 x 9000 with format 37 (RAW10) (dumpsys:3529 to 3531; the field grouping is INFERRED);
  - "fusion high-res" YUV configurations up to 12000 x 9000;
  - a "binning size" list (4080x3060, 4080x2296, 4080x1904, 3056x3056);
  - an "mmf size" list (8160x6120, …) (physical 5's block, about dumpsys:3523 to 3540).

  12000 / 4000 = 3, so the public 12 MP RAW is consistent with **3x3 binning** of a 108 MP array with 0.8 µm pixels (READ arithmetic, INFERRED interpretation). The vendor sensor-name tag of the logical camera differs from that of physical camera 5 (names withheld).
- **No public binning information.**
  - The capability list has **no ULTRA_HIGH_RESOLUTION_SENSOR** and no REMOSAIC_REPROCESSING (dumpsys:1574).
  - No `pixelArraySizeMaximumResolution`, `activeArraySizeMaximumResolution` or `binningFactor` value is present. Those names appear only inside the "available characteristics keys" lists (dumpsys:1648 to 1649), with no tag of their own; the only "MaximumResolution" tags present are lens distortion and intrinsic calibration (dumpsys:239, 241).
  - So the platform interface tells an app neither that the 12 MP RAW is binned nor how (READ).

**What the documentation says about binning and maximum resolution:**
- `SENSOR_PIXEL_MODE`: "When operating in SENSOR_PIXEL_MODE_DEFAULT mode, sensors would typically perform pixel binning in order to improve low light performance, noise reduction etc. However, in SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION mode, sensors typically operate in unbinned mode allowing for a larger image size." (READ, https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
- `SENSOR_INFO_BINNING_FACTOR`: "Sensors can have pixels grouped together under the same color filter … These groups can be of various sizes such as 2X2 (quad bayer), 3X3 (nona-bayer)." It also says that with the ultra-high-resolution capability, "This key will not be present if REMOSAIC_REPROCESSING is not supported, since RAW images will have a regular bayer pattern", and without the capability "RAW images may not necessarily have a regular bayer pattern when android.sensor.pixelMode is set to SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION." (READ, https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)
- Reading (INFERRED): a full-resolution RAW from a nona-cell sensor that is delivered as "a regular bayer pattern" has been **remosaiced** by the camera, re-arranged from 3x3 same-colour groups into a Bayer layout. That is a processing step. Delivered without it, it is a non-Bayer mosaic that DNG readers and Forager's writer do not expect. Either way, full resolution is not "more raw" than the binned 12 MP. And on the S22 it is not offered publicly at all.
- `REQUEST_AVAILABLE_CAPABILITIES_RAW`: "The maximum available resolution for RAW_SENSOR streams will match either the value in android.sensor.info.pixelArraySize or android.sensor.info.preCorrectionActiveArraySize." (READ, CameraMetadata page.) On the S22, pixelArraySize *is* 4000x3000. In the platform's own terms, the 12 MP binned frame is this camera's full RAW.
- `ImageFormat.RAW_SENSOR`: "General raw camera sensor image format, usually representing a single-channel Bayer-mosaic image. Each pixel color sample is stored with 16 bits of precision." (READ, https://developer.android.com/reference/android/graphics/ImageFormat#RAW_SENSOR.) It makes no promise about processing.

**Processing the documentation allows in RAW (READ, quoted):**
- `NOISE_REDUCTION_MODE`: "OFF means no noise reduction will be applied by the camera device, for both raw and YUV domain. MINIMAL means that only sensor raw domain basic noise reduction is enabled, to remove demosaicing or other processing artifacts." For FAST and HIGH_QUALITY, "camera device determined noise filtering will be applied." (READ, CaptureRequest page.) **Only OFF promises no raw-domain noise reduction.**
- `HOT_PIXEL_MODE`: "Hotpixel correction interpolates out, or otherwise removes, pixels that do not accurately measure the incoming light." (READ.) The page does not say whether it applies to RAW; DNG writers carry a separate bad-pixel list for that, which the S22 does not offer (hot pixel map modes: OFF only).
- `SENSOR_INFO_LENS_SHADING_APPLIED`: "If true, all images produced by the camera device in the RAW image formats will have at least some lens shading correction already applied to it. … When true, the lensShadingCorrectionMap key may still have values greater than 1.0, and those will need to be applied to any captured RAW frames for them to match the shading correction of processed buffers" (READ, CameraCharacteristics page). **The S22 reports TRUE for every back camera** (logical 0 and physicals 2, 5, 6, 7; dumpsys:2097, 3148 and the other blocks).
- `SHADING_MODE`: "When set to OFF mode, no lens shading correction will be applied by the camera device, and an identity lens shading map data will be provided" (READ, CaptureRequest page). Whether OFF also removes the correction the S22 applies to RAW is not stated (COULD NOT DETERMINE).
- The platform's own DNG writer writes the gain maps whenever the result carries the map, without checking `lensShadingApplied` (READ, AOSP `core/jni/android_hardware_camera2_DngCreator.cpp` lines 1936 to 1972, https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_hardware_camera2_DngCreator.cpp). Forager does the same (READ, `domain/RawFrameMapping.kt:152-157`). The documentation above says that is right for a residual map.

**What Forager does to the samples:** nothing. The samples are copied plane-wise from the image (`CameraXRawCapture.kt:112-125`) and passed through to the writer unchanged (`RawFrameMapping.kt:180`, `samples = r.samples`) (READ). Anything in the mosaic came from the phone.

**What mode the RAW shots were taken in: COULD NOT DETERMINE.**
- Forager's RAW capture sets only `STATISTICS_LENS_SHADING_MAP_MODE_ON` (READ, `CameraXRawCapture.kt:68-70`; `git grep` finds no other request option in `app/src/main`). The noise-reduction, hot-pixel and shading modes of the still requests are whatever CameraX's still template and the phone default to, and neither the stage 2 log nor the probe recorded them.
- The live preview request at 13:31 carried NOISE_REDUCTION FAST, HOT_PIXEL FAST, SHADING FAST, EDGE FAST (READ, dumpsys device-0 "Last request sent", dumpsys:1199 to 1320). That describes the preview, not the shots.
- **A side finding:** the same preview request carries `statistics.lensShadingMapMode ON` (READ, dumpsys:1317 to 1318, inside "Last request sent"). Only the RAW ImageCapture's interop sets that option. So **options set on the RAW capture through CameraX interop reach the repeating preview request too** (INFERRED from the one setter and the observed value). That matters for B.6.

### B.5 What the DNGs show (MEASURED; scripts in `/tmp/claude-1000/belowcx/scripts/`, run with `python -I`, rawpy 0.27.1, numpy 2.5.4)

**Files:**
- S22: the dim-room `shot1.dng` (-1038) and the nine daylight DNGs (-1043).
- S26: the three indoor DNGs (-1042).

All 13 were read; none was copied.

**Method common to the tests:**
- Each file is split into its four colour planes, taken from the file's own CFA tags. That gives 2000x1500 a plane on the S22 and 2040x1530 on the S26.
- Blocks of 32x32 same-colour samples with no clipped value are taken.
- Each block has a fitted plane removed (`detrend`).
- In each brightness band, the 20 percent of blocks with the lowest variance are kept as "flattest".

**What these tests can and cannot prove:**
- They detect processing that **mixes neighbouring photosites at the 12 MP scale**: demosaic then remosaic, spatial noise reduction, green balancing.
- They **cannot see processing inside a binned group** (anything done at 108 MP before the 3x3 sum), and cannot see a pure per-pixel gain.
- Scene texture mimics correlation. Where the "lag 2" and "lag 4" correlations are also positive, a band is texture-contaminated and is not read as processing.
- A single frame cannot separate noise from texture the way a tripod pair can (audit §2.6).

**Controls, so the tests are known to bite** (MEASURED, `rawbands.py --controls`, a synthetic 4000x3000 GRBG mosaic with shot and read noise):

| Control | Same-colour lag (0,1) | Lag (1,1) | Lag (0,2) | Green~green / green~red / red~blue | Noise spectrum low → high frequency |
|---|---|---|---|---|---|
| White noise (an unprocessed sensor model) | -0.003 | -0.004 to +0.001 | -0.003 | ≈ 0.00 / 0.00 / 0.00 | 0.98 → 1.01 (flat) |
| Mild same-colour smoothing (neighbour weight 0.1) | **+0.18** | +0.01 | 0.00 | ≈ 0.00 | **1.78 → 0.55** |
| Mild cross-colour mixing (mosaic neighbour weight 0.1) | +0.01 | 0.00 | 0.00 | **+0.19 / +0.10 / +0.19** | flat |

A real demosaic followed by a remosaic mixes far more strongly than the weight-0.1 control, so it would show cross-colour correlations well above 0.1 (INFERRED).

**Test 1, spatial correlation and noise power spectrum** (MEASURED, `rawbands.py`; figures for texture-free bands, those with lag (0,2) ≈ 0):

| | Same-colour lag (0,1) | Lag (1,1) | Lag (0,2) | Lag (0,4) | G~G' | G~R, G~B | R~B | Spectrum low → high |
|---|---|---|---|---|---|---|---|---|
| S22 dim, ISO about 1700 (bands 0 to 60 DN above black) | +0.026 to +0.030 | +0.024 to +0.026 | -0.003 to -0.002 | -0.002 | +0.022 to +0.032 | +0.002 to +0.005, -0.005 to +0.005 | ≈ 0.00 | 1.19 → 0.94 to 0.96 |
| S22 daylight, 9 files (clean bands) | +0.026 to +0.044 | +0.025 to +0.040 | 0.000 to +0.013 | about 0 to +0.01 | +0.017 to +0.06 | +0.014 to +0.06 | about 0 to +0.02 | 1.15 to 1.5 → 0.93 to 0.97 |
| S26 indoor, 3 files | +0.017 to +0.17 | +0.016 to +0.14 | +0.016 to +0.11 | up to +0.08 | +0.03 to +0.25 | +0.01 to +0.21 | +0.02 to +0.15 | 1.1 to 4 → 0.77 to 0.98 |

Reading:
- **S22:**
  - Cross-colour correlation is at most about 0.03 to 0.06, against 0.10 to 0.19 for the *mild* mixing control. **No sign of a demosaic → remosaic** (MEASURED against the control).
  - Same-colour noise is nearly white. There is a small, consistent signature, about +0.03 at both lag (0,1) and lag (1,1) and zero at lag 2, with greens slightly coupled (+0.02 to +0.03). That fits a **very weak** 3x3 same-colour filter (a neighbour weight of about 0.015, against the control's 0.1), or a weak green-balance step, or plain electrical or optical crosstalk. These single frames cannot tell those apart (INFERRED).
  - Whatever it is, it is far too weak to be the strong flat-area noise reduction phones run on processed images: that would show the control's +0.18 and a steep spectrum (INFERRED from the control).
- **S26:** the correlations are larger and **vary with band and with lag 2 and lag 4**, which is the texture signature. Indoor walls and surfaces are not flat at this noise level. The few near-black blocks (n of 8 to 14) are too few. **Unclear:** this neither shows nor rules out processing on the S26.

**Test 2, noise against signal** (MEASURED, `slope.py`): variance from a same-colour Laplacian residual (which removes texture), divided by 1.25 (white-noise gain), on the flattest 20 percent of 16x16 blocks per brightness band. It is fitted against the mean, and the slope compared with the file's NoiseProfile (S × (white − black)).

| Files | NoiseProfile slope (DN) | Measured slope (DN) | Profile ÷ measured | Measured read noise sd |
|---|---|---|---|---|
| S22 dim `shot1` | 3.26 | 0.85 | 3.8 | 3.3 DN |
| S22 daylight, 9 files | 0.18 to 0.24 | 0.019 to 0.052 | 4.4 to 9.9 (the cleanest files 8.2 to 9.9) | 0.4 to 0.7 DN |
| S26 indoor, 3 files | 0.20 to 0.21 | 0.18 to 0.24 | **0.9 to 1.1** | (fit intercept near 0; low-band noise about 1 to 1.3 DN) |

Reading:
- The S26 agrees with its own profile, so the method is not biased low by itself (MEASURED). This matches the audit's §3.1.
- On the S22 in daylight, the profile overstates the shot noise by up to about 10 times, more than the audit's spatial estimate of 3 to 6 times (§3.1). The cleanest files, the ones whose texture-contaminated bands are fewest, sit nearest 9.
  - **9 is exactly what averaging 3x3 = 9 photosites does to a shot-noise slope** when the result is scaled back to one photosite's range (INFERRED).
  - One explanation that fits both this and the vendor geometry: the S22 reports a NoiseProfile calibrated for a single 0.8 µm photosite, while delivering a 9-photosite average.
  - The dim shot's ratio of 3.8 does not fit the same factor. Its ISO (about 1700) is above the 800 maximum analog gain (READ, dumpsys), so digital gain may enter (INFERRED).
  - **Neither explanation is confirmed.** A strong noise reduction could also lower variance, but it would leave the strong correlation that test 1 did not find. Only noise reduction that works *inside* a binned group could hide from test 1, and inside a group it is indistinguishable from binning itself.

**Test 3, histogram combing** (MEASURED, `rawtests.py`, `hist.py`, `nearblack.py`):
- No empty codes inside the occupied range of any whole-image plane on either phone.
- No periodic depletion of codes (the ratio of each code's count to its neighbours' has median 1.00).
- The few depleted codes in small regions are scattered, with no period, as sampling noise in a sparse histogram is.
- Values below black are present and spread smoothly: 13 to 21 percent of the dim shot's photosites, minimum 29 against black 64; 0.01 to 2 percent in daylight. So black is not clipped and there is no stretched digital gain step.

Can and cannot: a digital gain greater than 1 applied after quantisation would comb the histogram. None is seen, so **no evidence of post-quantisation digital gain** on either phone. It cannot see gain applied before the final quantisation.

**Test 4, lens shading on the RAW** (MEASURED, `gainmap.py`, `regionnoise.py`):
- The residual gain maps Forager writes rise from 1.00 at the centre to **2.1 to 3.1 at the S22's corners** and **3.9 to 4.6 at the S26's**.
- Read noise near black, in rings from centre to corner, does **not** rise towards the corners on either phone:
  - S22 dim: 4.10 → 3.76 DN;
  - S22 daylight: 0.85 → 0.81 DN;
  - S26: 1.6 → 1.2 to 1.6 DN.
- A digital shading gain of g applied to the RAW after readout would multiply corner read noise by g. Even a 1.3x gain would show (INFERRED).
- So, despite the S22's `lensShadingApplied TRUE`, there is **no sign of a digital shading gain in its RAW**. Either the correction is applied before the noise is added (an analogue or in-sensor step), or it is small, or the flag overstates it. **Not resolved.**
- The S26's flag was not read (no dumpsys).

**Test 5, hot pixels** (MEASURED, `hotpix.py`): isolated bright photosites (more than 10 robust standard deviations above the median of their 8 same-colour neighbours, all neighbours dark):
- S22 dim: 124 in 10.6 million dark sites.
- S22 daylight: 16 and 175 in about 6 to 7 million.
- S26: 4 and 9 in 1.5 to 2.8 million.

For Gaussian noise the expected count is zero. So **outliers survive in the RAW**: either hot-pixel correction was not applied, or it is not complete. Small specular points in a dark scene can mimic a hot pixel, so this is weak evidence either way.

### B.6 Conclusions per phone, and options to get closer to unprocessed

**S22 Ultra (main camera, 4000x3000):**
- **Mosaic: yes.**
  - Single-channel GRBG (READ, CFA tags).
  - No demosaic-style cross-colour mixing (MEASURED against controls).
  - Not a debayered image, which was the owner's question.
- **Binned: yes, 3x3, INFERRED (strong).**
  - The public RAW is one third per side of a 12000x9000 RAW10 stream that only vendor tags list.
  - The vendor binning-size tags.
  - The NoiseProfile ratio of about 9.

  The platform interface itself states none of this.
- **Noise reduction: unclear.**
  - The mode used was never logged, and the documentation allows raw-domain noise reduction in FAST, HIGH_QUALITY and MINIMAL.
  - At the 12 MP scale there is at most a very weak spatial signature, about 0.03 correlation, which may be crosstalk.
  - Strong noise reduction is ruled out at that scale. Anything inside the binning is invisible.
- **Lens shading:** the camera says RAW is shading-corrected (READ). The noise shows no digital gain doing it (MEASURED). Unresolved.
- **Hot pixels:** outliers survive (MEASURED, weak).
- **NoiseProfile: not trustworthy.** It overstates noise by about 4 to 10 times (MEASURED; this confirms the audit's §3.1 conclusion, with a sharper figure).

**S26 Ultra (main camera, 4080x3060):**
- **Mosaic: yes.** Single-channel GBRG (READ, CFA tags). Cross-colour correlation is moderate and texture-contaminated: no clean evidence of mixing, and no clean evidence against it.
- **Binned: COULD NOT DETERMINE.** No dumpsys; that its main sensor bins pixels is from memory (UNVERIFIED).
- **Noise reduction: unclear.** It needs a flat-field or a tripod pair.
- **NoiseProfile:** consistent with the measured noise (MEASURED).
- **Lens shading:** residual map up to 4.6 in the corners, with no corner noise amplification (MEASURED). The `lensShadingApplied` flag was not read.

**Options to get closer to unprocessed, with costs:**

1. **Ask for noise reduction OFF and hot-pixel correction OFF on RAW captures** (both offered on the S22, READ dumpsys:1564, 1536). This is the only setting the platform documents as "no noise reduction … for both raw and YUV domain".
   - Cost on CameraX today: through interop (Forager already uses `setCaptureRequestOption`, `CameraXRawCapture.kt:68-70`). The dumpsys suggests the option would also reach the live preview, giving a noisier viewfinder while RAW is chosen (INFERRED). It is a small code change but needs the owner's word and a device check.
   - Cost on a platform session: none to the preview, because the setting goes on the burst requests only.
   - In both cases the RAW gets noisier *if* noise reduction was on. That is what the averaging is for.
2. **Measure the difference before deciding.** A tripod pair with default modes and a tripod pair with OFF, on each phone: temporal noise var(F1 − F2)/2 per brightness band, plus the correlation and spectrum above.
   - If OFF changes nothing, the RAW was not being noise-reduced at the 12 MP scale.
   - If OFF raises the noise and flattens the spectrum, it was.

   This also gives the measured noise model piece 3 needs (-1046). It belongs in the dev probe recommended in A.2.
3. **`SHADING_MODE_OFF`** (offered). Per the documentation it gives an identity map, but whether it un-does the S22's RAW-side correction is not stated. Measure the residual map and the corner noise under it.
   - Cost: possibly vignetted RAW that Forager must correct itself, which is what its own processing would do anyway (-1009).
4. **Full-resolution (108 MP) RAW on the S22: not available** through the public interface (no ULTRA_HIGH_RESOLUTION_SENSOR, no maximum-resolution maps, READ).
   - Where available, it is documented as possibly non-Bayer unless remosaiced (READ). That is more processed, not less.
   - It is 216 MB a frame in 16-bit (READ arithmetic) and impractical for bursts.
   - **The binned 12 MP RAW is the most direct sensor data these phones offer an app** (INFERRED).
5. **Log the modes.** Add NOISE_REDUCTION_MODE, HOT_PIXEL_MODE, SHADING_MODE, EDGE_MODE and the post-RAW boost from each RAW frame's CaptureResult to the RAW shot log. That turns "unclear" into a reading on every shot.
   - Cost: a few lines in the CameraX adapter or the probe.

---

## Questions for the owner (plain words)

1. **How should the burst camera work?**
   - (a) Forager runs the camera itself, below the camera library, for everything. The fastest bursts (about a quarter of a second for 8 frames in good light), and the viewfinder never stops. It is the biggest change, and it means re-owning flash, focus and rotation, which the library does today.
   - (b) Forager keeps the library for the viewfinder and normal photos, and takes the camera over only for a burst. A smaller change, but each averaged photo would have roughly a second's pause after the tap (the viewfinder freezes, then comes back). That is a "did it take?" moment.

   The suggestion is to measure (b)'s pause on both phones first, then choose.
2. **May a dev-build test take bursts with the phone's noise reduction switched off** (and hot-pixel fixing off) alongside normal ones, on a tripod, to see whether the phones were cleaning the RAW? That answers "is it the actual RAW" with a measurement instead of an inference.
3. **"Actual RAW" on these phones is the binned 12 MP sensor data.** On the S22, each saved photosite is the sum of 9 real photosites; that is the sensor's own low-light mode, not a debayered image. Full 108 MP RAW is not offered to apps on the S22, and where phones do offer it, it is re-arranged by the phone. Is the binned 12 MP RAW what you want "RAW" to mean?
4. If noise reduction OFF turns out to change the RAW: **should the RAW format chip always ask for it off** (the purest file, noisier), or only the averaging path?

---

## What could not be determined

- The S26's camera characteristics (not attached): its binning, lens-shading flag, stream tables, noise-reduction modes.
- The actual platform RAW burst rate, swap time and hand-back on either phone. Every platform-side timing above is inferred from the stream tables and documentation, not measured.
- The noise-reduction, hot-pixel and shading modes the stage 2 RAW shots were taken with (not logged).
- Whether the S22 applies any processing *within* each 3x3 binned group. It is invisible to these tests by construction.
- Why the S22 flags its RAW as shading-corrected while the corner noise shows no digital gain.
- What the weak S22 same-colour correlation (about 0.03) is: very mild filtering, green balancing or crosstalk.
- Whether the S26's larger correlations are processing or scene texture.
- Why CameraX's RAW+JPEG bursts deliver one image per request.
- Whether CameraX's still requests use HIGH_QUALITY noise reduction (which the documentation says makes stalls indeterminate) on these phones.
- Whether RAW_SENSOR (16-bit) on the S22 sustains the same rate as RAW10 under BURST_CAPTURE's guarantee. The documentation names only RAW10, RAW12, RAW14 and Y8.

## Sources

**Read in writing this:**
- Forager `origin/main` `cb25b582`:
  - `photo/CameraCaptureSession.kt` (lines 29 to 175);
  - `photo/CameraXCaptureSession.kt` (lines 64 to 140, 470 to 560);
  - `photo/CameraXRawCapture.kt` (whole);
  - `domain/RawFrameMapping.kt` (lines 30 to 191, by grep);
  - `photo/RawShotSaver.kt` (function list);
  - `ui/log/InAppCameraHost.kt` (lines 1 to 60);
  - `gradle/libs.versions.toml` (CameraX 1.6.2 pin).
- Forager `origin/dev` `7a2c0286`: `app/src/debug/.../diagnostics/burst/CameraXBurstProbeCamera.kt` (lines 1 to 200).
- RECORD and the audit as listed at the top.
- Probe logs (OBSERVED, read in place): `~/Zynergy/device-evidence/2026-10-15-stage3-probe-s22/probe-run1.jsonl`, `probe-run2-bright.jsonl`.
- DNGs (MEASURED, read in place):
  - `~/Zynergy/device-evidence/2026-10-15-raw-stage2-s22/shot1.dng`;
  - `…/2026-10-15-raw-stage2-s22/daylight/*.dng` (9);
  - `…/2026-10-15-raw-stage2-s26/raw1..3.dng`.
- S22 `dumpsys media.camera` and `getprop` (SM-S908U, Android 16, SDK 36, build BP2A.250605.031.A3), 2026-10-15 13:31 PDT, saved to `/tmp/claude-1000/belowcx/s22-dumpsys.txt`.
- CameraX 1.6.2 artifacts by javap (`/tmp/claude-probe/camx/camera-core-1.6.2.aar`, `camera-camera2-1.6.2.aar`): `TakePictureManagerImpl`, `CaptureNode.MAX_IMAGES`, `Camera2Interop$Extender`.
- Platform reference (fetched 2026-10-15):
  - https://developer.android.com/reference/android/graphics/ImageFormat (RAW_SENSOR);
  - https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics (SENSOR_INFO_LENS_SHADING_APPLIED, SENSOR_INFO_BINNING_FACTOR, REQUEST_PIPELINE_MAX_DEPTH);
  - https://developer.android.com/reference/android/hardware/camera2/CaptureRequest (NOISE_REDUCTION_MODE, HOT_PIXEL_MODE, SHADING_MODE, SENSOR_PIXEL_MODE);
  - https://developer.android.com/reference/android/hardware/camera2/CameraMetadata (REQUEST_AVAILABLE_CAPABILITIES_RAW, _BURST_CAPTURE, _ULTRA_HIGH_RESOLUTION_SENSOR);
  - https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap (getOutputStallDuration);
  - https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession (captureBurst);
  - https://developer.android.com/reference/android/hardware/camera2/CameraDevice (guaranteed stream combinations: RAW-capability, BURST, LEVEL-3 tables);
  - https://developer.android.com/reference/android/media/ImageReader (newInstance maxImages).
- CameraX release notes, https://developer.android.com/jetpack/androidx/releases/camera (1.6.0 to 1.7.0-alpha03).
- AOSP platform DNG writer, https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/jni/android_hardware_camera2_DngCreator.cpp (lines 1931 to 1990, lens shading and hot pixel opcodes).

**Scripts** (in no repository): `/tmp/claude-1000/belowcx/scripts/`:
- `tags.py` (dumpsys tag parser);
- `rawtests.py`, `rawbands.py` (correlation, spectrum, controls);
- `slope.py` (noise against signal);
- `hist.py`, `nearblack.py` (histograms);
- `gainmap.py` (OpcodeList2 GainMap parser);
- `regionnoise.py` (corner against centre read noise);
- `hotpix.py` (outliers).
