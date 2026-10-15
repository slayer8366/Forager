Investigation for the owner's review; nothing dispatched.

# 2026-10-15-10: Where the bounds are for "pure RAW, non binned" (RECORD -1065)

Written for the planner (Rowan Waymark), 2026-10-15 22:18 UTC (15:18 PDT), by a read-only investigator subagent. The owner, verbatim (RECORD -1065): "Pure RAW, non binned", then "See about digging further. If there's a real block then we should be familiar where the bounds are so we can work around it".

**What was done.** Nothing was built, installed, launched or changed on any phone, and nothing was committed. On the S22 Ultra (R5CT321008R) the only commands were `getprop`, `pm list packages` and `dumpsys package <pkg>` for three of the maker's camera packages. The `dumpsys media.camera` capture from the previous investigation was re-read rather than retaken: `/tmp/claude-1000/belowcx/s22-dumpsys.txt`, taken 13:31 PDT today with Forager's camera open. "dumpsys:N" below means a line of that file. **The S26 Ultra was not attached** (`adb devices` listed only R5CT321008R), so everything about it is from public reports and marked UNVERIFIED. Working files are in `/tmp/claude-1000/unbinned/` and are in no repository: the vendor-tag mapper `vmap.py`, plus web copies under `web/`.

**How claims are marked.**
- **READ**: from the named file, line, dumpsys line or URL.
- **MEASURED**: computed here; the method is stated.
- **INFERRED**: reasoning from what was read.
- **COULD NOT DETERMINE**.
- **SEARCH-ONLY**: the claim comes from a search engine's summary of a page that could not be opened (403, or a page that renders by script). It is weaker than READ.

Products and companies are described generically. Vendor tag namespaces are described by what they hold.

This builds on `prompts/preserved/2026-10-15-07-below-camerax-and-raw-authenticity.md` (Part B; "report -07" below). Report -07 found that the S22's public RAW is 4000x3000, and inferred 3x3 binning of a 108 MP array from vendor tags listing a 12000x9000 RAW10 stream.

---

## 0. The short version

1. **The S22's binning is now a reading, not only an inference.** The main camera's vendor sensor-name tag spells, in ASCII, the part number of a 108 MP sensor (READ, dumpsys:3520 to 3521; the name is withheld here). The sensor maker's launch material, as quoted by a trade site, says the part is "a 12,000 x 9,000 grid" whose "Nonapixel Bayer pattern allows for 9-to-1 pixel binning", giving "12 MP", and that "in good lighting conditions an enhanced remosaic algorithm can output full resolution 108 MP images" (READ, https://m.gsmarena.com/samsung_details_the_isocell_hm3__the_108_mp_sensor_used_in_the_galaxy_s21_ultra-news-47238.php). **So the 12 MP RAW Forager saves is the sensor's 9-in-1 binned mode.**
2. **The platform's own route to full resolution is closed on the S22, by the phone maker's camera driver, permanently.**
   - The driver speaks camera HAL interface **version 3.5** for every camera (READ, dumpsys:1464 and every "static information" header).
   - The platform's full-resolution features (the ultra-high-resolution capability, the maximum-resolution pixel mode, remosaic reprocessing) were defined in the 3.6 metadata and the 3.7 device interface, in API 31 (READ, AOSP and developer reference; §1).
   - The camera service drops the pixel-mode field when it talks to a 3.5 driver (READ, AOSP `HidlCamera3Device.cpp`).
   - The phone is on Android 16 with a vendor API level of 31, and the driver never moved past 3.5 (READ, getprop). Android 16 is reported to be its last major update (SEARCH-ONLY).
   - Nothing an app does can change this.
3. **A hidden route exists in the driver's own tables.**
   - The vendor tables list a 12000x9000 RAW10 stream, a 12000x9000 RAW_SENSOR (16-bit) entry in a vendor "picture" table, and 12000x9000 YUV, JPEG and private-format entries (READ, dumpsys:3303, 3409, 3457, 3529 to 3531).
   - The chipset layer defines a remosaic processing node and "quad CFA" tags (READ, vendor tag descriptors, dumpsys:16987 to 16988, 17185, 17279 to 17282).
   - The vendor session keys include a "shooting mode" and a "camera client" key (READ; mapped by `vmap.py`).
   - Any app may build a request key by name: `CaptureRequest.Key(String, Class)` has been public since API 29 (READ, developer reference).
   - **Whether the S22's driver will hand a third-party app the 12000x9000 RAW stream is COULD NOT DETERMINE without running code on the phone**, which this dispatch forbade.
   - One public precedent: an open-source underwater-housing camera app reports that on a later model from the same maker (S24-class), setting two vendor session keys by name let it record the 8K video size, which exists only in the vendor tables, through public camera 0. Its own docs say this was verified on a device (READ, §2.4). That is a different phone, a different stream and the app's own report.
4. **"Non-binned" on a 3x3-cell sensor does not mean "more raw".**
   - The sensor's native full-resolution readout has 3x3 blocks of one colour. Every full-resolution RAW a phone hands out is either that native pattern or a **remosaiced** (re-arranged, processed) Bayer image.
   - The platform says the native pattern is reachable only on devices advertising both ultra-high-resolution and remosaic reprocessing (READ). The S22 advertises neither.
   - The maker's own RAW app saves "Linear DNG 16-bit" (READ, the maker's support page), which is already demosaiced.
   - **No route to the native cell pattern before remosaic was found for any Play Store app on the S22** (INFERRED from everything above; not proven impossible for the vendor-tag route).
5. **On noise, unbinned is never better at the same output size, and is worse in low light.**
   - Each full-resolution photosite gets 1/9 of the light.
   - Summing 9 of them afterwards gives the same shot noise as the sensor's binning, but up to 9 read-noise contributions instead of 1 *if* the sensor bins in the charge domain. Whether this sensor does is UNVERIFIED.
   - The only gain is detail, and only in good light.
   - A 12000x9000 frame is **135 MB as RAW10 and 216 MB as 16-bit** (MEASURED arithmetic), against 24 MB for today's 12 MP frame.
6. **The realistic workaround is burst super-resolution from the binned 12 MP RAW.** This is the published method behind a major phone maker's "super-res zoom": merge a handheld burst of raw frames, using hand tremor as sub-pixel shifts, which "serves to both increase image resolution and boost signal to noise ratio" (READ, Wronski et al., SIGGRAPH 2019, arXiv 1905.03277). Forager's averaging work already captures bursts of this kind (report -07, Part A).

---

## 1. The platform route: ultra-high-resolution sensor, maximum-resolution pixel mode, remosaic

### 1.1 What the platform documentation says these deliver (READ)

**`REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR`** (added API 31):
- "It can operate in 'default' mode and 'max resolution' mode. It generally does this by binning pixels in 'default' mode and not binning them in 'max resolution' mode."
- Max-resolution streams are listed in `streamConfigurationMapMaximumResolution`, and the max-resolution pixel array "will be at least 24 megapixels".
- Source: https://developer.android.com/reference/android/hardware/camera2/CameraMetadata, saved copy `/tmp/claude-1000/belowcx/docs/hardware_camera2_CameraMetadata.txt`.

**`REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING`** (added API 31): "The device supports reprocessing from the RAW_SENSOR format with a bayer pattern given by android.sensor.info.binningFactor (m x n group of pixels with the same color filter) to a remosaiced regular bayer pattern."
- It is present only on ultra-high-resolution devices.
- "When REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR devices do not advertise this capability, ImageFormat.RAW_SENSOR images will already have a regular bayer pattern."
- "If a RAW_SENSOR stream is requested along with another non-RAW stream … the RAW_SENSOR stream will have a regular bayer pattern."
- Same page.

**`CaptureResult.SENSOR_RAW_BINNING_FACTOR_USED`** (added API 31): "Whether RAW images requested have their bayer pattern as described by android.sensor.info.binningFactor. This key will only be present in devices advertising [ultra high resolution] which also advertise REMOSAIC_REPROCESSING … On all other devices RAW targets will have a regular bayer pattern." (READ, https://developer.android.com/reference/android/hardware/camera2/CaptureResult.)

**What the platform therefore offers for a 3x3-cell sensor** (INFERRED from the three quotes):
- The **native cell pattern** (3x3 same-colour blocks) is reachable only on a device advertising **both** capabilities, as a maximum-resolution RAW_SENSOR stream on its own, with no other non-RAW stream in the request.
- With any other stream alongside, or on a device advertising only ultra-high-resolution, a full-resolution RAW is a **remosaiced Bayer**: the phone has re-arranged the cells into a normal Bayer layout by interpolation, which is a processing step.

### 1.2 Why the S22 lists none of it (READ, then INFERRED)

- **Capabilities.** The logical main camera lists `BACKWARD_COMPATIBLE CONSTRAINED_HIGH_SPEED_VIDEO RAW YUV_REPROCESSING PRIVATE_REPROCESSING READ_SENSOR_SETTINGS MANUAL_SENSOR BURST_CAPTURE MANUAL_POST_PROCESSING LOGICAL_MULTI_CAMERA` (dumpsys:1575). No camera on the phone, of the 13 driver cameras, lists ULTRA_HIGH_RESOLUTION_SENSOR or REMOSAIC_REPROCESSING. Checked by listing every "static information" block's capabilities, dumpsys:1464 to 15957.
- **Pixel array.** No camera on the phone has a public pixel array larger than 4000x3000. The 12000-wide size appears only in vendor tables, and in the deprecated, non-public `availableRawSizes` tag (dumpsys:1996, 3055), never in a public `availableStreamConfigurations` (READ, grep for "12000" over the whole capture).
- **Driver interface version.** Every camera is `device@3.5` (dumpsys:1464, 5256, … 15957). The provider is "legacy/0-0 (v2.6)" (dumpsys:1463).
- **Where the features live.**
  - The capability constants, `ANDROID_SENSOR_PIXEL_MODE` and `ANDROID_SENSOR_INFO_PIXEL_ARRAY_SIZE_MAXIMUM_RESOLUTION` are defined in the camera metadata **3.6** HAL file (READ, https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/camera/metadata/3.6/types.hal).
  - In the camera service, the per-stream pixel-mode field is passed to the driver only through the **3.7** `configureStreams_3_7` call. For 3.5 and 3.6 drivers the call "wrap[s] only the 3.4 structure, which has no pixel-mode field" (READ through a page summariser, https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/device3/hidl/HidlCamera3Device.cpp; quoted lines `requestedConfiguration3_5.v3_4 = requestedConfiguration3_4;` and `if (mHidlSession_3_7 != nullptr) {`).
  - The current documentation adds that features from Android 13 onward need the newer AIDL driver interface (SEARCH-ONLY, source.android.com camera HAL3 page).
- **The OS version.**
  - `ro.build.version.release` 16, SDK 36, build `…S908USQSAGZH3`, security patch 2026-08-05, `ro.build.version.oneui` 80000 (READ, getprop).
  - `ro.product.first_api_level` 31 and `ro.vendor.api_level` 31 (READ, getprop): the phone and its vendor partition (where the camera driver lives) date from Android 12.
  - Four OS updates have not moved the driver past 3.5.
- **Newer OS on this model.**
  - Several outlets report Android 16 / One UI 8 as the S22 series' final major OS update, closing a four-version promise; a point release was uncertain (SEARCH-ONLY; notebookcheck, androidauthority and phonearena pages returned 403).
  - So no future OS version is expected, and an OS update would not rewrite the vendor driver in any case (INFERRED).
  - **Bound: the platform route is closed on the S22 by the maker's driver; it is not a permission an app could ask for.**

**A trap in the readings, recorded so nobody repeats it.** The S22's live preview result carries `android.sensor.rawBinningFactorUsed [FALSE]` and `android.sensor.pixelMode [DEFAULT]` (READ, dumpsys:281 to 284, inside "Latest received frame" for Forager's session). "Binning factor used: false" reads like "not binned", but per the definition above it means only "the RAW has a regular Bayer layout, not an m x n cell layout". It says nothing about whether the sensor binned. The key is also documented as present only on ultra-high-resolution plus remosaic devices, yet the S22 reports it anyway (READ). Whether the S22's driver fills it or the framework adds a default is COULD NOT DETERMINE. **Do not cite this key as evidence of non-binned RAW.**

---

## 2. Vendor tags: can an app ask for the hidden 12000x9000 stream?

### 2.1 What the vendor tables hold (READ, dumpsys; namespaces withheld)

On physical main camera 5, and identically on driver camera 56, which is the same sensor by sensor name and physical size (dumpsys:14009 onward):

| Vendor table (described) | Entries at 12000-wide | Line |
|---|---|---|
| "preview stream configurations" | format 34 (private) 12000x9000, 12000x6752, 12000x5596 | dumpsys:3303 to 3305 |
| "picture stream configurations" | format 33 (JPEG) 12000x9000, 12000x6752, 12000x5596; **format 32 (RAW_SENSOR, 16-bit) 12000x9000**, then format 32 at 4000x3000 | dumpsys:3409 to 3411, 3457 to 3458 |
| "high-res RAW stream configurations" | `56 12000 9000 0 37`: format 37 is RAW10 | dumpsys:3529 to 3531 |
| "fusion high-res stream configurations" | five-field rows `56 W H flag 35`: flag 1 for 12000x9000, 12000x6752, 12000x5596 and 8992x8992; flag 0 for 4000-wide | dumpsys:3532 to 3542 |
| "binning size" | 4080x3060, 4080x2296, 4080x1904, 3056x3056 | dumpsys:3523 to 3525 |
| "mmf size" | 8160x6120, 8160x4592, 8160x3808, 6112x6112 | dumpsys:3526 to 3528 |
| "high resolution mode" | 0 | dumpsys:3558 to 3559 |
| "raw sensor info" | 10, 0 (10 bits; white level 1023 elsewhere) | dumpsys:3546 to 3547 |

The public table of the same camera stops at 4000x3000 for RAW10, RAW_SENSOR and RAW12 (formats 37, 32, 36) (READ, dumpsys:2789 to 2878).

**Readings (INFERRED, each with its alternative):**
- **The leading "56" in the high-res rows** is either the ID of hidden driver camera 56, which carries the same tables, or a mode code. A third-party developer reports that on an S24-class model "Samsung Camera uses hidden logical camera ID 56 for its own 8K path" (READ, §2.4), which favours the camera-ID reading. Not settled.
- **The flag field** (1 for 12000-wide, 0 for 4000-wide in the fusion rows) looks like "full-resolution sensor mode" against "binned mode". The RAW10 row has 0 there, which does not fit cleanly. COULD NOT DETERMINE.
- **The "binning size" and "mmf size" lists** (4080x3060 and 8160x6120) do not match this sensor's 4000x3000 and 12000x9000 geometry; they fit a 50 MP 2x2-cell part. They may be generic tables from the maker's shared camera layer. Do not read them as this sensor's modes (INFERRED).

**Chipset-layer vendor tag *definitions*** (dumpsys:16784 onward, 1022 entries; definitions only, with no values in any camera's static block) include:
- a "quadra CFA" section with `is_qcfa_sensor` and `qcfa_dimension` (dumpsys:16987 to 16988);
- a "QCFA snapshot" tag (17185);
- a **remosaic processing node** with SupportedFeature, CurrentMode and FrameDimension (17279 to 17282);
- a "sensor mode table" and "sensor mode index" (16998; 0x800d0002);
- a "CSID binning info" tag (17106);
- an "in-sensor zoom" session key and state (0x805e0016, 0x80850000);
- an "ideal RAW" session key (0x805e0023).

Reading (INFERRED): the chipset pipeline has a software or hardware **remosaic step on the phone's processor side**, so the 108 MP path runs native cell data into a remosaic node. Which side of that node the vendor RAW10 stream is tapped from is COULD NOT DETERMINE. That decides whether it is native cell pattern or remosaiced Bayer.

### 2.2 What an app can technically send (READ)

- **Request keys by name.** `CaptureRequest.Key(String name, Class<T> type)`, "Added in API level 29": "Normally, applications should use the existing Key definitions … However, they may be useful for testing purposes and for defining custom capture request fields." The key-naming note says "keys that are device/platform-specific are prefixed with 'com.'" (https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Key). This is public API, not a hidden interface.
- **Keys the S22 advertises as settable** (READ; mapped from dumpsys numeric IDs to the descriptor table by `/tmp/claude-1000/unbinned/vmap.py`):
  - The **public** request-key and session-key lists of logical camera 0 include 34 chipset-layer session parameters, among them "ideal RAW", "in-sensor zoom", "RAW callback source type", "MFNR" and "QHDR". These are advertised to every app through `getAvailableCaptureRequestKeys` (dumpsys:1576 to 1602, 1659 onward).
  - The maker's **own** vendor session-key list (a vendor tag, not the public list) includes "shooting mode", "camera client", "range sensor mode", "special image-quality policy" and "fast capture option" (dumpsys:2255 to 2261).
- **Stream sizes.**
  - In the camera service, an ImageReader output is passed to the driver at the app's requested size without checking it against the public table (`respectSurfaceSize = true`, so rounding is skipped).
  - On a device without ultra-high resolution, the pixel mode is forced to DEFAULT and the size is not compared with either map (READ through a page summariser, https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/utils/SessionConfigurationUtils.cpp).
  - **The platform does not block a 12000x9000 RAW request; the driver decides.**

### 2.3 What blocks it, as far as can be told

- **The driver.** It can refuse the stream combination (`onConfigureFailed`), silently deliver 4000x3000 into a 12000x9000 buffer, deliver it only when a vendor session key such as "shooting mode" names the maker's high-res mode, or deliver it only to packages it recognises. **COULD NOT DETERMINE which**: it needs a run on the phone.
- **Package recognition: possible, not shown.**
  - The maker's session-key list includes a "camera client" key, the camera service tracks the client package (dumpsys:12, 126), and the maker runs a privileged system "camera SDK service" (next item).
  - None of that proves a package allowlist in the driver. It is a candidate block, UNVERIFIED.
- **The maker's camera SDK service** (READ, `dumpsys package`):
  - `com.samsung.android.camerasdkservice`, a privileged system app (`/system/priv-app/SCameraSDKService`, `sharedUser=android.uid.system`, PRIVILEGED), exporting a bound service action "ICameraSDKService".
  - A second privileged system package, `com.samsung.android.cameraxservice`.
  - Who may bind to them could not be read with the allowed commands: COULD NOT DETERMINE. §3 covers what is public about the SDK.

### 2.4 Public evidence of apps trying this on the maker's phones

- **An open-source underwater-housing camera app** (GitHub `diveair/MobileDiveControl`, read 2026-10-15):
  - Its `Camera2EightKRecorder.kt` (lines 55 to 59, 330 to 344) sets two vendor session parameters by name, "camera client" = 0 and "shooting mode" = 35, through `CaptureRequest.Key(name, Int::class.javaObjectType)`, to obtain 7680x4320, which "Samsung's vendor table exposes" while "CameraX's highest public quality is UHD".
  - Its `docs/SAMSUNG_CAMERA_PARITY.md` (lines 101 to 118) records "8K | 7680×4320 | Verified HEVC through the Samsung vendor session on public camera ID 0 at 24/30 fps" on an S24 (SM-S921W) and "Device testing verified native 24 and 30 fps capture."
  - It also states that the maker's "HEIF/RAW vendor output" has no public third-party API, and that those features are clipped when support cannot be proved (lines 121 to 127).
  - What this shows (INFERRED): on at least one recent model, the maker's driver **does** honour vendor session keys from a third-party package and **does** deliver a vendor-table-only size. It is a video stream on a different phone, not RAW on the S22. It is the strongest public hint that the vendor-tag route is not dead by construction.
- **A camera app for a mid-range model from the same maker** (GitHub `Marque-source/A56CameraApp`, `SamsungVendorTagsExplorer.kt` lines 93 to 104, 161 to 350) probes the same "high-res RAW stream configurations" tag and tries RAW12, RAW10 and RAW_SENSOR streams on hidden camera "56". The repository records no outcome (READ). It is evidence that others are probing this, not of a result.
- **A developer-forum thread titled "Get Image has 12000x9000 with Camera2 API"** on the maker's developer forum: the page renders by script and was not readable. A search summary says a poster could get only 4000x3000 through Camera2, and that the maker offers no SDK to access the camera (SEARCH-ONLY, https://forum.developer.samsung.com/t/get-image-has-12000x9000-with-camera2-api/22240).
- **User-forum threads on S22/S24** say full-resolution capture is available only to the maker's own camera app (SEARCH-ONLY; us.community and eu.community pages returned 403).
- **Generic GitHub code search** for the high-res vendor tag name found only the probe app above (READ, `gh search code`, 2026-10-15). The "high resolution mode" tag name returned nothing. The "shooting mode" key appears mainly in custom-ROM work and decompiled vendor framework code, which is not usable by a Play app.

### 2.5 Risks of the vendor-tag route (INFERRED)

- **Fragile across updates.** Vendor tables and session-key meanings can change with any firmware. The "shooting mode" value 35 that worked for 8K on one model is a magic number. Nothing documents what value means "108 MP RAW" on the S22. Finding it would mean guessing values, or reading the maker's camera app, which is the maker's code and raises its own concerns.
- **Store policy.** Building a key by name uses public API, so the platform's non-SDK-interface restrictions do not apply (INFERRED from the reference page). No Play policy page was read on this. Not checked: COULD NOT DETERMINE.
- **Unsupported-capability rule (Forager CLAUDE.md, "Errors and failure paths").** Any such feature must verify what it got (size, CFA layout), as the 8K app verifies its encoded track. If it did not get it, it must say "unsupported", never label a 4000x3000 frame as full resolution.
- **Even if it works, the frame is probably remosaiced** (§1.1, §4). The owner's "pure RAW" and "non binned" would pull against each other.

---

## 3. The maker's own SDKs and RAW apps

- **A partner camera SDK exists, partner-only as far as public sources show.**
  - The maker announced in 2019 that it was "opening up its camera SDK to other partners", with a "first wave of partners" being three large social and messaging apps (READ, https://www.sammobile.com/?p=94626).
  - The maker's 2022 developer-conference session "Introducing Samsung Galaxy camera ecosystem" speaks of "partnerships Samsung has already formed with numerous app developers" and gives no public access path (READ, https://developer.samsung.com/conference/sdc22/sessions/introducing-samsung-galaxy-camera-ecosystem).
  - Later coverage describes S22- and S24-era integrations with large social apps (SEARCH-ONLY; phonearena 403, diyphotography body not loaded).
  - No public SDK download, documentation or application form was found on the maker's developer site (SEARCH-ONLY; a site-restricted search returned none).
  - On the phone, the service side is the privileged "camera SDK service" in §2.3 (READ).
  - **Bound: a partner agreement with the maker, not a technical step.** Whether partners get full-resolution RAW is COULD NOT DETERMINE; nothing public mentions RAW.
- **The maker's RAW app** (`com.samsung.android.app.galaxyraw`, version 5.0.02.17, installed from the maker's own store; READ, `dumpsys package`):
  - It is signed with the same certificate as the system's privileged camera services (`signatures:[b378e95c]` on all three packages). Being signed like the system is how it can use vendor paths that ordinary apps cannot (INFERRED).
  - Its output is "both JPEG files and RAW (Linear DNG 16-bit) files" (READ, https://www.samsung.com/ca/support/mobile-devices/professional-photography-with-galaxy-expert-raw-app/). A linear DNG is already demosaiced RGB, so it is further from the sensor than Forager's Bayer DNG.
  - The page says "Special photo options are only activated when set to 12MP resolution".
  - Whether the app offers 108 MP RAW on the S22: user reports conflict (SEARCH-ONLY).
  - It exposes no sharing interface for its capture path; its only declared permission is a signature-level read permission (READ, `dumpsys package`).
  - **Bound: not available to other apps.**
- **The maker's CameraX and Camera2 extension services** (`com.samsung.android.cameraxservice`, `com.android.cameraextensions`; READ, package list) provide processed modes such as night and bokeh. They deliver processed images, not RAW (INFERRED; extension reference pages not re-read here).

---

## 4. What "non-binned" can physically mean on a 3x3-cell sensor

**The pixel layout** (READ, sensor maker's launch material via the trade article cited in §0; S22 dumpsys for the lens):
- 12000x9000 photosites of 0.8 µm under a filter that repeats in 3x3 same-colour blocks.
- Binned 9-to-1 gives 4000x3000 at an effective 2.4 µm, which is the RAW Forager saves.
- The S22 lens is f/1.8 and 6.4 mm (READ, dumpsys:2663 to 2668).

**The three things a "full-resolution RAW" could be:**
1. **The native cell pattern** (3x3 blocks of one colour). This is the least processed. Only the platform route in §1.1 promises it, and the S22 does not offer it. No other public route was found.
   - If Forager got it, it would have to do its own remosaic or a direct demosaic from that pattern. That is a known-hard problem: a research challenge was set up for exactly this, "Quad Joint Remosaic and Denoise … interpolation of Quad CFA to Bayer at full resolution" (READ, https://arxiv.org/abs/2209.07060, the 2x2 case).
   - DNG readers and Forager's current DNG writer expect a 2x2 Bayer repeat (report -07, B.4). A 3x3 block pattern would need either a CFA-pattern tag of 6x6 repeat, whose acceptance by readers is UNVERIFIED, or Forager's own conversion.
2. **A remosaiced Bayer**, made by the phone (the sensor maker's "enhanced remosaic algorithm"). It is full size and DNG-compatible, but every pixel's colour neighbours are interpolated. **That is processed data, which is the opposite of "pure RAW".**
3. **The binned 12 MP Bayer** that Forager has today. Each value is the sum (or mean) of 9 real same-colour photosites, with no interpolation across colours (report -07 B.5: no demosaic-style cross-colour mixing, MEASURED).

**Noise** (INFERRED from the standard sensor noise model; read noise and binning domain UNVERIFIED for this sensor):
- Each 0.8 µm photosite collects about 1/9 of the light of the 2.4 µm binned pixel.
- Shot noise depends only on the total light, so 9 full-resolution values summed in software equal one binned value in shot noise.
- Read noise differs. If the sensor bins charge before reading, a binned pixel carries one read's noise, while 9 separate reads carry √9 = 3 times as much (in standard deviation).
- Illustration with **assumed** numbers (2 e⁻ read noise, 50 e⁻ per binned pixel, a dim scene):
  - binned SNR = 50/√(50+4) ≈ 6.8;
  - full resolution then summed: 50/√(50+36) ≈ 5.4, about 20% worse.
  - In bright light (5,000 e⁻) the two are the same to within 1%.
- So full resolution can win only on detail, only in good light, and the forest floor under canopy is often not good light.

**Detail the lens can carry** (MEASURED arithmetic, ideal lens; real lens performance not determined):
- At f/1.8 and 550 nm the diffraction cutoff is 1/(λN) ≈ 1,010 cycles/mm.
- The binned grid's Nyquist limit is 1/(2 × 2.4 µm) ≈ 208 cycles/mm, where an ideal lens still has about 74% contrast.
- The full-resolution grid's Nyquist limit is 625 cycles/mm, where an ideal lens has about 26% contrast.
- So the optics do not rule out detail beyond 12 MP, and the full resolution is not empty. How much real detail survives the actual lens, and the remosaic's colour interpolation, is COULD NOT DETERMINE without test images.

**Size and speed:**
- Per frame (MEASURED arithmetic):
  - 12000 × 9000 = 108,000,000 photosites;
  - RAW10 packed = 135 MB;
  - 16-bit = 216 MB, and a 16-bit DNG is about the same uncompressed;
  - today's 4000x3000 16-bit frame is 24 MB.
  - An 8-frame full-resolution burst would be about 1.7 GB of buffers, against about 192 MB today.
- Frame rate:
  - Binned RAW: the public minimum frame duration for 4000x3000 RAW10 and RAW_SENSOR is 33.3 ms, so 30 fps (READ, `availableMinFrameDurations`, dumpsys:319 onward).
  - The preview frame's rolling-shutter skew is 16.2 ms (READ, "Latest received frame"; that is the preview's sensor mode).
  - The vendor tables list no durations for 12000x9000: COULD NOT DETERMINE.
  - The sensor's 8K/30 video mode (READ, trade article) suggests a readout fast enough for roughly 33 MP at 30 fps. A full 108 MP readout rate was not found in anything readable; one search summary gave about 10 fps, from a truncated snippet (SEARCH-ONLY, unconfirmed). Full readout also takes about 3 times as many rows, so motion skew (the "jello" of a rolling shutter) would grow too (INFERRED).

---

## 5. The S26 Ultra

**Not attached; no dumpsys.**
- What is known from Forager's own files: its RAW is **4080x3060** in a GBRG Bayer layout (READ, report -07 B.6, from `~/Zynergy/device-evidence/2026-10-15-raw-stage2-s26/` DNG tags).
- Public reports, mostly pre-launch leaks (SEARCH-ONLY, UNVERIFIED):
  - a 200 MP main sensor, 1/1.3-inch, f/1.4;
  - a 0.6 µm pixel, from one source only;
  - a 4x4-cell layout binning to 12.5 MP, with 50 MP and 200 MP outputs.
- 16320 x 12240 / 4 = 4080 x 3060 (arithmetic), so Forager's 12.5 MP RAW fits **16-to-1 binning** of a 200 MP array (INFERRED, unverified).
- On a 4x4-cell part, "non-binned" has a middle option: 50 MP as 2x2 binning (four 4x4 cells read as 2x2 cells), still remosaiced to Bayer (INFERRED).
- Whether the S26 advertises ultra-high-resolution or remosaic reprocessing, and its driver interface version (it ships Android 16, so likely the newer AIDL interface, UNVERIFIED), is COULD NOT DETERMINE. **This is the one place the platform route might be open.** One read-only `dumpsys media.camera` with the S26 attached answers it.

---

## 6. The bounds

| Route to unbinned data | S22 Ultra | S26 Ultra | Blocked by | Play Store app? | Risks | Evidence |
|---|---|---|---|---|---|---|
| Platform full-resolution mode (ultra-high-resolution capability, max-resolution pixel mode) | **No** | Unknown | The maker's camera driver: interface 3.5, no capability; no OS update coming | Yes, where a phone offers it | Gives remosaiced Bayer unless remosaic reprocessing is also offered | READ (§1.2) |
| Platform native cell pattern (remosaic reprocessing) | **No** | Unknown | Same | Yes, where offered; rare | Forager must remosaic or demosaic a 3x3 or 4x4 pattern itself; DNG readers may not accept it | READ (§1.1) |
| Vendor tables plus vendor keys set by name (12000x9000 RAW10 or RAW_SENSOR) | **Listed in tables; whether it is delivered to a third-party app is unknown** | Unknown | Possibly the driver, possibly a package check, possibly magic values; unknown which | Technically uses public API; policy not checked | Undocumented magic values; can break with any firmware; probably remosaiced; 135 to 216 MB per frame | READ (tables, key API); one precedent on another model (§2.4); outcome COULD NOT DETERMINE |
| The maker's partner camera SDK | Service present | Likely | Partner agreement with the maker | Only as a partner | Business dependency; RAW not mentioned anywhere public | READ, partial (§3) |
| The maker's RAW app | Installed | Likely | Signed like the system; no sharing interface | No | Its RAW is linear (demosaiced) anyway | READ (§3) |
| Root or custom firmware | Possible in principle | Possible in principle | The user's own phone security; not a store app | No | Out of scope for a store app | INFERRED; not investigated |
| **Binned RAW (today)** | **Yes**, 4000x3000, 9-to-1 | **Yes**, 4080x3060, likely 16-to-1 | Nothing | **Yes** | Lowest noise per output pixel; capped at 12 MP of detail | READ / MEASURED (report -07) |
| **Binned RAW plus burst super-resolution** | **Yes** (Forager-side work) | **Yes** | Nothing but Forager's own processing | **Yes** | Needs a handheld burst and a still subject; processing time; it is a computed image, like any super-res | READ (Wronski et al. 2019; Bhat et al. 2021) |

### Workarounds, with trade-offs in plain words

1. **Keep the binned RAW and call it what it is.**
   - It is the most direct sensor data a store app gets on the S22: real Bayer, with no colour interpolation.
   - Trade-off: 12 MP of detail. Nothing new to build.
2. **Burst super-resolution from binned RAW.**
   - A handheld burst of 12 MP RAW frames, each slightly shifted by hand tremor, is aligned and merged straight onto a finer grid, with no separate demosaic step.
   - The published method "serves to both increase image resolution and boost signal to noise ratio" and runs "at 100 milliseconds per 12-megapixel RAW input burst frame on mass-produced mobile phones" (READ, https://arxiv.org/abs/1905.03277). A learned variant takes "multiple noisy RAW images as input, and generates a denoised, super-resolved RGB image" (READ, https://arxiv.org/abs/2101.10997).
   - Trade-offs:
     - The result is a computed RGB image, not a single raw frame. It can be saved alongside the raw frames, which stay pure.
     - It works only on a still subject; mushrooms are good subjects, wind-blown grass is not.
     - It needs the burst capture already being designed for averaging (report -07, Part A).
     - It is real engineering in Forager, but on data Forager fully controls, and on both phones the same way.
     - It does not depend on the maker's goodwill and does not break with firmware.
3. **A dev-build probe of the vendor-tag route on the S22, if the owner wants the bound pinned down rather than inferred.**
   - Request a 12000x9000 RAW10 or RAW_SENSOR stream on camera 0 and on camera 56, with and without the maker's "camera client" and "shooting mode" session keys.
   - Record exactly what comes back: refusal, wrong size or the right size. Check the CFA layout of any frame.
   - Trade-offs:
     - It needs the owner's go and a device session.
     - It needs guessed "shooting mode" values, since nothing public says which value means high-res RAW.
     - Even a success is likely a remosaiced frame of 135 to 216 MB, and fragile across firmware.
4. **Check the S26 with one read-only dumpsys.** If it advertises the platform full-resolution capability, a supported, documented route exists on that phone (50 MP or 200 MP, remosaiced or native depending on what it advertises). The S22 would then stay binned.

---

## 7. Questions for the owner (plain words)

1. **"Pure" or "non binned": which matters more when they pull apart?**
   - On these sensors the only full-resolution data a phone hands out has been re-arranged by the phone (it guesses colours between photosites).
   - The 12 MP binned file is the one with no guessing; each value is 9 (S22) or probably 16 (S26) real photosites added together.
   - Is the goal the most untouched data (keep binned), or the most detail (accept the phone's re-arranging, where it can be got at all)?
2. **Would a merged burst count?**
   - Forager could take several quick 12 MP RAW shots and combine them into one sharper and cleaner picture, keeping the original raw frames as well.
   - That is how a major phone maker gets its zoom detail.
   - Is a combined picture acceptable as the "detail" version, with the raw frames as the "pure" version?
3. **Is it worth one experiment on the S22's hidden setting?**
   - The phone lists a full 108 MP RAW mode that apps are not told about.
   - A dev-only test could try to switch it on and report honestly whether it worked.
   - It might be refused. If it works, the file is about 10 times larger (135 to 216 MB), it probably breaks with a future phone update, and it is still re-arranged by the phone.
   - Yes, or not worth it?
4. **May the S26 be plugged in for one read-only look at its camera tables?** It is the phone most likely to have an official full-resolution mode. Nothing on it changes.

---

## 8. What could not be determined

- Whether the S22's driver delivers the 12000x9000 RAW10 or RAW_SENSOR stream to a third-party app, with or without vendor session keys. This needs running code; it was forbidden here.
- Whether the maker's driver checks the calling package (an allowlist), or who may bind to its privileged camera SDK service.
- What the leading "56" and the flag field in the vendor high-res tables mean.
- Whether the vendor RAW10 stream is native cell pattern or remosaiced.
- Whether this sensor bins in the charge domain (one read) or after reading (nine reads), and its read noise. These set how much worse unbinned is in low light.
- The S22 sensor's full-resolution frame rate and readout time.
- Whether the S22's `rawBinningFactorUsed` value comes from the driver or a framework default.
- Everything about the S26's camera tables and driver version (not attached), and its sensor specs (leak-sourced only).
- Whether the S22 will get any further OS point release (SEARCH-ONLY says Android 16 is the last major one). It would not change the vendor driver regardless (INFERRED).
- Whether the maker's partner SDK exposes RAW or full resolution to partners.
- Google Play policy on vendor camera keys: not read.
- How much real detail the S22 lens delivers beyond 12 MP (needs test images).
- Whether common DNG readers accept a 3x3 or 4x4 cell CFA pattern.

## 9. Sources

**Device (S22 Ultra R5CT321008R; read-only):**
- `/tmp/claude-1000/belowcx/s22-dumpsys.txt`: dumpsys lines as cited.
- `getprop`: `ro.build.version.release`, `.sdk`, `ro.build.display.id`, `ro.build.version.security_patch`, `ro.build.version.oneui`, `ro.product.first_api_level`, `ro.vendor.api_level`, `ro.board.platform`, and camera-related properties.
- `pm list packages` (camera-related).
- `dumpsys package` for `com.samsung.android.camerasdkservice`, `com.samsung.android.cameraxservice` and `com.samsung.android.app.galaxyraw`.

**Platform reference and AOSP (READ):**
- https://developer.android.com/reference/android/hardware/camera2/CameraMetadata (saved: `/tmp/claude-1000/belowcx/docs/hardware_camera2_CameraMetadata.txt`)
- https://developer.android.com/reference/android/hardware/camera2/CaptureResult (SENSOR_RAW_BINNING_FACTOR_USED)
- https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.Key
- https://android.googlesource.com/platform/hardware/interfaces/+/refs/heads/main/camera/metadata/3.6/types.hal
- https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/device3/hidl/HidlCamera3Device.cpp
- https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/utils/SessionConfigurationUtils.cpp

The two AOSP C++ files were read through a page summariser that quoted the lines given above. They were not read line by line.

**Sensor and maker:**
- https://m.gsmarena.com/samsung_details_the_isocell_hm3__the_108_mp_sensor_used_in_the_galaxy_s21_ultra-news-47238.php (READ)
- https://www.samsung.com/ca/support/mobile-devices/professional-photography-with-galaxy-expert-raw-app/ (READ)
- https://www.sammobile.com/?p=94626 (READ)
- https://developer.samsung.com/conference/sdc22/sessions/introducing-samsung-galaxy-camera-ecosystem (READ)

**Open-source apps (READ via `gh api`):**
- `diveair/MobileDiveControl`: `app/src/main/kotlin/com/mobiledivecontrol/ui/camera/Camera2EightKRecorder.kt`, `docs/SAMSUNG_CAMERA_PARITY.md`; commit "feat: verify native 8K pro video capture", 2026-08-28.
- `Marque-source/A56CameraApp`: `app/src/main/java/com/a56camera/utils/SamsungVendorTagsExplorer.kt`.

**Literature (READ, abstracts only):**
- Wronski et al., "Handheld Multi-Frame Super-Resolution", SIGGRAPH 2019, https://arxiv.org/abs/1905.03277
- Bhat et al., "Deep Burst Super-Resolution", https://arxiv.org/abs/2101.10997
- Yang et al., "MIPI 2022 Challenge on Quad-Bayer Re-mosaic", https://arxiv.org/abs/2209.07060

**SEARCH-ONLY (pages not readable):**
- forum.developer.samsung.com thread 22240
- us.community.samsung.com (S22 108 MP RAW request)
- eu.community.samsung.com (S24 full-resolution access)
- notebookcheck, androidauthority and phonearena (S22 final OS update)
- S26 Ultra camera leak coverage (gsmarena, androidauthority)
- source.android.com camera HAL3 (AIDL note)

**Prior report:** `prompts/preserved/2026-10-15-07-below-camerax-and-raw-authenticity.md`, Part B and §B.6.
