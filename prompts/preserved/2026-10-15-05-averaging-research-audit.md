Research and audit for the owner's review; nothing dispatched.

# 2026-10-15-05: Frame averaging, research and an audit of draft 2026-10-15-04 against RECORD -1044

Written for the planner (Rowan Waymark), 2026-10-15 16:59 UTC (09:59 PDT), by a research subagent. Documents only: nothing was built, no code was changed, nothing was committed. Base read: records branch `records-after-173` at `81ea7bbe` (RECORD -1044 is its head); Forager `origin/raw-stage2` at `d8f0f944`.

How claims are marked: **READ** (read in writing this, from the named file, page or paper), **MEASURED** (computed here from the device files, method stated), **OBSERVED** (seen in a device log on disk), **INFERRED**, **UNVERIFIED** (from memory, not checked). Device files were read in place and are described in words only; nothing was copied out of `~/Zynergy/device-evidence/`. Throwaway scripts and downloaded papers are under `/tmp/claude-1000/` and are not part of any repository.

---

## 0. The short version

1. **-1044 removes the whole user-facing half of the draft** (menu row, chip, "Hold still" ring, new messages, sizes, five of its nine decisions). What is left is an engine with no visible change on main and a switch on `dev` only. That raises a question the draft never had to face: the engine lands on main with **no production caller** until debayering (section 2.3).
2. **The phones' own noise numbers can't be trusted for the merge.** Every DNG from both phones carries NoiseProfile with the read-noise term **O = 0** (MEASURED from the tags), which says there is no noise at black. The draft's motion weight uses that model, so it would refuse to merge any frame in the shadows, the very place averaging matters most. On the S22 in daylight the measured noise is also **about 3 to 6 times lower in variance** than its NoiseProfile says; on the S26 indoors the two roughly agree (section 3.1). The noise model has to be measured, not read from the tag.
3. **The CameraX burst rate is about 3 frames a second, not 2.** Five S22 daylight shots and three S26 indoor shots each took 0.29 to 0.32 s from "RAW shot" to both images in memory (OBSERVED). The draft's 0.5 s came from one dim-room shot. That is still about ten times slower than a sensor's streaming rate, and the published pipelines run at the streaming rate (section 3.2).
4. **The draft's motion handling (a per-cell weight) has a built-in trade-off that hurts exactly Forager's subjects.** Whole-cell alignment, which keeps the colour pattern, leaves up to one pixel of leftover misalignment. Fine texture (gills, moss, lichen, needles) then either blurs when averaged or gets thrown out as "motion". The published frequency-domain merge exists for this case: it merges the coarse detail and keeps the reference frame's fine detail where the frames disagree. It also reduces exactly to a plain average at one setting and to the reference frame at the other, so it can be tested as tightly as the draft wanted (section 3.4).
5. **The phone already underexposes RAW heavily in daylight.** The nine S22 daylight DNGs have a median value only 13 to 30 steps above black, out of 959 (about 2 to 3 percent of the range), yet 0.2 to 5.9 percent of their photosites are clipped (sky). Most of a nature photo sits in the bottom few percent of the range. Averaging is the tool for that, and there the read noise (the term the phones report as zero) dominates (section 3.5).
6. **Real bursts from the woods are the missing test data.** Synthetic tests can't show wind in leaves, focus breathing on a close-up, or this sensor's noise. The cheapest way to "get everything right" is for the **dev build** to save the raw frames of a burst on the test phones (kept off GitHub, never in release). That needs the owner's word, because -974 says "only keep the final product" (open question Q1).
7. **Several draft numbers rest on figures that change once measured.** Examples: the phone heap limit, the alignment cost (the draft would measure it on a JVM in CI, which says nothing about a phone), and the noise model. The proposed piece order measures each one before anything is built on it (section 5).

---

## 1. What -1044 changes in the draft

The owner, verbatim (RECORD -1044): "No this happens automatically. It's not a thing to select. The only option is HDR." and "Option A, do one at a time here. I want to be sure we get everything right." -974 stands: every format except RAW gets the averaging automatically; RAW stays one unprocessed frame; the HDR chip is the only processing choice. Stage 3 builds burst, alignment and averaging with **no visible change**. A **dev-branch-only** switch (-930) saves the averaged mosaic so the planner can measure it on the phones.

### 1.1 Withdrawn outright

| Draft 2026-10-15-04 | Why it goes |
|---|---|
| Title: "saved as an averaged RAW (mosaic) DNG"; §1's -1040 row "a new output kind"; the -974 row "Averaging is a new, separate kind" | -1044: no averaged-RAW kind exists. The averaged mosaic is an internal step, and on `dev` a measurement artifact. |
| §3 entirely: menu rows and sizes (3.1), the AVG chip, the "Hold still" ring and words, the "Saving…" thumbnail (3.2, 3.3) | No visible change in stage 3. |
| §3.4's two new messages ("frames couldn't be lined up…", "wasn't enough memory…") and its table | No user path reaches the engine in stage 3. On `dev`, each failure is logged and the dev file is simply not written (or the reference frame is written instead, labelled as such in the log). No new user-facing words. |
| §1 rows -975 (size in the menu), -1002 (row in the chip), -1005 (remembered), -1019 ("extends to the new fallback messages") | Each was about the menu row. -1004 (the JPG as placeholder) does not arise until the engine is connected. |
| §4.3's "new enum value… every exhaustive `when (CameraFormat)`"; Part 0 (d) | No new `CameraFormat` value. |
| §4.5's user wait budget, D5 (does the camera wait) | No user flow in stage 3. It comes back when the engine is connected (section 6, Q2). |
| §5.6's size estimate "for the menu", the `rawShotFits` reasoning | No menu. On `dev` the low-storage floor still applies to writing the dev file. |
| §7 Part 0 (b) "behind the existing chip with RAW chosen and a log flag" | The probe and every later piece is switched on only on `dev`. |
| §8 tests 10 (menu and chip) and 11 (shot outcomes with messages) | Nothing to test. Replaced by a test that main's capture is unchanged with the engine present (section 4, tests). |
| §11 device checks 3 (menu), 7's message, 12 (size against the menu) | As above. |
| §12 D1 (name), D2 (place), D6 (message wording), D7 (flash as a user rule) | Withdrawn by -1044. Flash still matters to the engine: a dev burst with flash set is refused and logged. |

### 1.2 Changed in meaning

- **D3 (frame count)** is no longer "how long the user holds still" today. In stage 3 it is an engineering constant on `dev`. It becomes a user-facing wait once averaging runs for every JPG (section 6, Q2).
- **D8 (reference frame)** stays a design choice. Recommendation below: first frame, with per-frame sharpness logged.
- **D9 (base)** stands. PR #227 still awaits the owner's merge word (-1042, -1043 notes).
- **§9 "Don't touch"** gets stronger. With the engine present but uncalled, main's JPG and RAW output must be byte for byte as on the base, and the camera must look and time exactly as before.
- **§10's abort "any visible change to JPG or RAW capture"** becomes "any change at all to main's capture behaviour".

### 1.3 New consequences the draft could not have had

- **The engine on main has no caller (CLAUDE.md, "check reachability before measuring behaviour").** Under -930, `dev` is main plus one commit restoring the dev tools "exactly as at 921aa9c7" (READ, RECORD -930), rebased on every main update and never merged. If the engine goes to main and the switch to `dev`, main carries code that no production path reaches until debayering lands. That is not wrong, but it has to be decided and stated, not discovered later. The options are in Q4.
- **"Automatic for every format except RAW" means that, eventually, every default JPG is a burst** on phones with RAW. At 3 frames a second (section 3.2), a 4-frame photo is about 1 s of capture and an 8-frame photo about 2.5 s. That is what stage 3's measurements are really for, and why the burst-rate probe comes first.

---

## 2. Research findings

### 2.1 What averaging buys, and where it stops paying

- **Noise model.** Raw noise variance is affine in the signal, σ²(x) = S·x + O: a shot-noise term proportional to signal plus a read-noise floor (READ: DNG 1.6 spec, NoiseProfile, "N(x) = √(S·x + O)", with x "a recorded linear signal in the range [0,1]"; Hasinoff et al. 2016 §5, "the noise variance σ² can be expressed as Ax + B"; Monod, Delon & Veit 2021 §4.1, equation 7). The DNG spec says this model ignores "fixed pattern effects and other sources of noise (e.g., pixel response non-uniformity…)" (READ).
- **Averaging N aligned frames divides both terms by N**, so the standard deviation falls by √N: 1.41× at 2 frames, 2× at 4, 2.83× at 8. Each doubling costs twice the frames for about 3 dB (READ, OSCam `frame_average.py` docstring, "SNR improves by about sqrt(N)"; the arithmetic is standard).
- **Diminishing returns in practice.** The published pipeline limits bursts to 2 to 8 frames, and "In bright scenes, capturing 1–2 images is usually sufficient, although more images are still generally beneficial in combating camera shake blur" (READ, Hasinoff et al. 2016 §3, "Burst size").
- **What averaging does not touch.** Fixed-pattern noise and pixel non-uniformity move with the sensor, not the scene. Averaging removes them only to the extent the hand moved the frames relative to each other (INFERRED from the spec's statement that NoiseProfile excludes them; OSCam says the same of flat-field structure, "Flat-field correction then removes the fixed structure that averaging cannot touch", READ). This matters for testing (section 2.6).
- **The quantisation floor.** "Averaging only recovers sub-LSB structure when the per-frame noise is at least ~1 LSB, so that quantisation is dithered. If a source is quieter than that, the extra bits are empty" (READ, OSCam `frame_average.py`). On the S22 in daylight the measured shadow noise is about 0.8 to 1 step (section 3.1). That is right at this limit, so averaging gains will look smaller in bright-day shadows than √N predicts (INFERRED).
- **Averaging N short frames is not the same as one N-times-longer exposure.** Each frame pays the read noise again. In read-noise-dominated shadows, N frames are noisier than one long exposure of the same total time. The pay-off is that each short frame is sharp handheld (INFERRED from the model; the reason the low-light paper measures motion before choosing exposure: Liba et al. 2019, "motion metering", READ abstract and §2 heading).

### 2.2 Merging: plain mean, per-pixel weights, or the frequency-domain merge

- **Plain mean after alignment** ghosts wherever alignment fails. The published figure shows "Averaging with alignment eliminates ghosts in some regions… but fails in others" (READ, Hasinoff et al. 2016, Figure 6 caption). OSCam's averaging assumes identically framed static frames, and its HDR merge says "Static specimens only: any motion between brackets ghosts the merge" (READ, `hdr_merge.py`).
- **The published phone merge** works per 16×16 tile of each Bayer colour plane, in the 2D Fourier domain, pairwise against the reference: T̃₀(ω) = (1/N) Σ_z [T_z(ω) + A_z(ω)(T₀(ω) − T_z(ω))], with A_z = |D_z|² / (|D_z|² + cσ²) (READ, Hasinoff et al. 2016 §5, equations 6 and 7; c includes "a further tuning factor (in our implementation, fixed to 8)"). "Since the contribution of each alternate frame is adjusted on a per-frequency basis, alignment failure can be partial, in that rejected image content for one spatial frequency will not corrupt other frequencies" (READ, same). The four colour planes, including both greens, are merged separately and reinterpreted as a Bayer image (READ, same, "Fourth…"; Monod et al. §4.1, "the two green channels are also treated independently"). Tiles overlap by half and are blended with a raised-cosine window whose overlapped copies sum to 1 (READ, Monod et al. §4.4).
- **Its two limits are the two simple methods.** "If τ → 0, T̃₀ → T₀: we stick to the reference frame… If τ → +∞… the result is equivalent to an average of all aligned frames in the spatial domain" (READ, Monod et al. §4.2). This gives the tests two exact oracles (section 4).
- **Why the frequency domain, for a mosaic.** Alignment is only to a multiple of 2 pixels, so colours never mix. "In effect, our approach defers the undersampling problem to our merge stage, where image mismatch due to aliasing is treated like any other form of misalignment" (READ, Hasinoff et al. 2016 §4). Sub-pixel motion vectors at the finest level "could imply having pixels aligned to pixels from different Bayer CFA channels, which would induce significant color shifts (which we verified in our experiments)" (READ, Monod et al. §3.3). So the leftover misalignment of up to ±1 pixel is handled in the merge, by letting low frequencies merge and high frequencies fall back to the reference.
- **Spatial per-pixel weights done carefully** compare the frame difference with the local spatial variance as well as the noise, so textured areas aren't mistaken for motion: "areas prone to aliasing have large spatial variance even within a single frame… Differences larger than this fraction most likely signify misalignments… and are discarded" (READ, Wronski et al. 2019 §5.2.1). But that paper uses the aliasing to place samples at their true sub-pixel positions while building an RGB image. It replaces demosaicing: "our method… creates the full RGB directly from a burst of raw images" (READ, Fig. 1 caption). An averaged *mosaic* has no such positions, so merging aliased texture into a mosaic softens it (INFERRED).
- **The merge also leaves the reference's motion blur as it is.** "The Wiener filter does not introduce additional blur from other images, but it does not remove blur either" (READ, Monod et al. §4.2 remark).

### 2.3 Alignment on Bayer data

- **Coarse-to-fine on a 2×2-averaged grey image, 4 levels, tiles, L2 at coarse levels and L1 at the finest, multiple hypotheses when upsampling** (READ, Hasinoff et al. 2016 §4; parameters in Monod et al. §3.2, §6.1, as the draft cites). The draft's description matches.
- **Speed reference.** "Our alignment algorithm runs at 24 milliseconds per Mpix on a mobile device", using frequency-domain acceleration "together with careful engineering" (READ, Hasinoff et al. 2016 §4). That is optimised code on 2016 hardware. A plain Kotlin port's speed on these phones is unknown and has to be measured on the phone (INFERRED).
- **Assumption: piecewise translation.** "We assume piecewise translation, which is true in the limit as the patch approaches a single pixel, but can be a limiting assumption for larger patches" (READ, same). Rotation, rolling-shutter skew and lens distortion near the edges all appear as different translations in different tiles. That is why tiles beat one global shift (INFERRED). Monod et al. report the method still working near the edges of a wide lens, "where these steps might perform worse because of image distortion induced by the wide lens" (READ, §5). Rolling shutter is not discussed in what was read. That tile translation absorbs it at handheld speeds is **INFERRED**.
- **Known failure modes:** local minima for large motion, noise-driven wrong matches in dark scenes, and occlusions (READ, Monod et al. §3, the bullet list after Algorithm 1). The merge must expect wrong vectors, which is the argument for a robust merge rather than better alignment.
- **Close-ups specifically:** if autofocus moves during a burst, magnification changes ("focus breathing"). A scale change is not a translation, so close-up fungi with hunting focus would fail alignment at the frame edges (INFERRED). The draft logs focus distance per frame; the probe should report whether it moves.

### 2.4 Motion, wind and ghosting

- **Wind-moved foliage at 3 frames a second** moves between every frame. With a robust merge the moving leaves come from the reference alone, sharp but unaveraged. With a plain mean they ghost (READ figure, Hasinoff Fig. 6; INFERRED for foliage).
- **Artifacts to test for** (READ unless marked): ghosting (Hasinoff Fig. 2a, Monod Fig. 4c); tile-edge discontinuities without overlapped windows (Monod Fig. 8); "red and green blocks in dark areas, especially in the case of low-light" (Monod §5, about their finishing pipeline); uneven noise, where merged regions sit beside unmerged moving regions (INFERRED); and, for a spatial weight, blotchy noise where tiles are partly rejected (INFERRED).
- **The reference frame's choice affects all of this.** The published method picks "the sharpest frame in a subset of the burst, according to a simple metric based on gradients in the green channel… from the first 3 frames" (READ, Hasinoff §4). The later low-light paper does not use ZSL frames "because their exposure time is limited by the viewfinder refresh rate" (READ, Liba et al. 2019 §6).

### 2.5 Exposure for bursts

- **"Expose to protect highlights."** "We deliberately down-expose" so fewer pixels saturate, then merge to recover the shadows (READ, Hasinoff §1 and §3). The amount is chosen per scene by an exposure algorithm aware of the tone mapping to come; it "is generally mild, and only 4% of scenes require dynamic range compression above 8" (READ, Fig. 4 caption).
- **It is tied to tone mapping.** Underexposure only works when a later stage brightens the shadows. In Forager that is HDR and debayering/tone mapping (-974), not stage 3 (INFERRED).
- **Locking.** `CONTROL_AE_LOCK` "is locked to its latest calculated values", but "since the camera device has a pipeline of in-flight requests, the settings that get locked do not necessarily correspond to the settings that were present in the latest capture result" (READ, Android `CaptureRequest` reference). The burst must wait for a result whose AE and AWB states read LOCKED before frame 1, and then check each frame's exposure. The draft checks each frame but does not wait for LOCKED. With flash ON or AUTO, the flash "may be fired" even when AE is locked (READ, same).
- **The sensor's own rate is published per phone:** `StreamConfigurationMap.getOutputMinFrameDuration(format, size)` is "the minimum frame duration (maximum frame rate) possible when only that stream is configured", and `getOutputStallDuration` is the extra per-frame stall (READ, Android reference). The probe can read both for RAW_SENSOR at 12 MP, to show the gap between what the sensor can do and what CameraX delivers.
- **A real burst API exists below CameraX:** camera2's `captureBurst`, "captured in the minimum amount of time possible, and… not… interleaved with requests submitted by other capture or repeat calls" (READ, Android `CameraCaptureSession` reference). Using it means a camera session outside CameraX, which is the draft's D3 option C. Not proposed. It is noted here so the owner knows the published speed is reachable on Android.
- **CameraX ZSL won't carry RAW (INFERRED).** CameraX's ZSL keeps "the three most recent capture frames" in a ring buffer and reprocesses one to JPEG, needs PRIVATE reprocessing, and does not work with flash ON or AUTO (READ, developer.android.com ZSL page). The page doesn't mention RAW, so RAW support is inferred absent, not read.

### 2.6 Numerics, the DNG side, and measuring noise

- **Black level per frame, per channel; white from the frame's own level, never 2^bits.** The draft is right. Black levels are taken "straight from the camera driver" in the low-light paper, which notes "a good sensor can get within 0.25 data numbers (DN) of a 10-bit signal" (READ, Liba et al. §6).
- **Float accumulation.** The draft is right. OSCam accumulates in float64 (READ, `frame_average.py`: `acc = np.zeros(..., dtype=np.float64)`). Float32 is enough for ≤8 values in [0, 1] (the draft's reasoning; INFERRED, sound).
- **Clipped samples.** OSCam excludes each sample at or above the white level from that photosite's sum and divisor, writes white where all clipped, and keeps the per-photosite counts (READ). OSCam's HDR merge then **reads those counts** to down-weight partly clipped pixels in the next stage: it "reads frame_average.py's own per-photosite clean-sample fraction" (READ, `hdr_merge.py`). So the count is a **data product for the HDR stage**, not just a log line. The draft only logs it.
- **NoiseProfile after averaging.** The model is normalised to [0, 1] (READ, DNG spec), so rescaling into 16 bits leaves it alone and averaging divides it by the effective N. The draft is right in principle, but see section 3.1: the phones' input values are themselves wrong.
- **Floating-point DNG exists**: "DNG 1.4.0.0 and later support floating point image data… 16, 24 and 32 bits" (READ, DNG 1.6 spec). That closes the draft's UNVERIFIED item. Rejecting it for stage 3 stays right: the writer is integer-only and 16 bits are enough (section 3.6).
- **BaselineExposure** "specifies by how much (in EV units) to move the zero point" of a raw converter's exposure control (READ, DNG spec). Forager writes it only from the post-RAW boost (READ, `RawFrameMapping.kt:164-166`). A mapping that keeps black→0 and white→1 in normalised terms does not change brightness, so it stays as the reference frame's (INFERRED; the spec text read gives no rule tying it to the stored scale).
- **Measuring noise on a device.** The robust method is the **temporal difference**: two frames of a static scene on a tripod, var(F₁ − F₂)/2 per signal level, which cancels texture and fixed pattern. Spatial methods need flat patches and pick up texture, vignetting and fixed pattern (INFERRED, standard practice; the read paper lists "pictures of a scene that features multiple uniform… areas" and regression, Monod et al. §4.1, READ). For the averaged output, the same check is two independent averages A₁ and A₂ of the same tripod scene: var(A₁ − A₂)/2 against var(F₁ − F₂)/2 should be 1/N.

---

## 3. Measurements on the device files (read in place, described in words)

Scripts: `/tmp/claude-1000/avgaudit/noise2.py`, `clip.py` (rawpy 0.x, numpy, run with `python -I`). Files: the nine S22 daylight DNGs, the three S26 indoor DNGs, and the S22 dim-room `shot1.dng` (-1038).

### 3.1 NoiseProfile against measured noise

- **Every file from both phones has NoiseProfile O = 0** for all three planes, and the same S in every plane (MEASURED, `exiftool -NoiseProfile`). S is 1.9 to 2.5 × 10⁻⁴ for the S22 daylight shots, 2.1 to 2.2 × 10⁻⁴ for the S26 indoors, and 3.4 × 10⁻³ for the S22 dim room. The writer passes the camera's values through unchanged (READ, `RawFrameMapping.kt:143-151`; `DngFileWriter.noiseProfile`). So the zeros come from the phones.
- **Method (spatial, single frame):** each Bayer plane is split into 16×16 blocks. A Laplacian residual (the pixel minus the mean of its four same-colour neighbours) is computed, and the 10th-percentile block variance is taken per brightness band, to favour flat blocks. The 10th percentile underestimates true variance by roughly 13 percent at this sample size (INFERRED). Texture inflates the estimate and any raw-domain smoothing deflates it.
- **S26 indoors (raw1, raw3):** the measured variance tracks S·x within about 10 to 25 percent across x = 0.006 to 0.4. Near black the measured standard deviation is about 1.1 to 1.3 steps. The phone's model gives a similar figure at x ≈ 0.006 only because S·x is not yet zero there; at true black it predicts zero (MEASURED).
- **S22 daylight (two files measured):** the measured variance is **about 3 to 6 times lower** than the profile says, for example 3.4 steps against 8.6 steps standard deviation at x ≈ 0.4. Near black it is about 0.8 to 1.0 step (MEASURED). Possible causes, none confirmed: the profile is simply wrong for this mode; the HAL smooths raw data; or binned or remosaiced pixels with spatially correlated noise make a spatial estimate read low (that the S22 Ultra's main sensor bins pixels is UNVERIFIED, from memory). Only a temporal measurement (two frames of one burst on a tripod) can tell these apart.
- **S22 dim room (shot1):** 15.8 percent of photosites are below the black level of 64 (minimum 29), so real read noise is present and symmetric about black. The measured standard deviation near black is about 3.5 steps, where the profile (O = 0) predicts about 3 steps at x ≈ 0.003 and zero at black (MEASURED).
- **Consequence for the draft's §5.4.** The weight w = σ²c / (d² + σ²c) with σ² = S·x + O and O = 0 gives σ² → 0 at black. Every frame would be rejected in the deep shadows, which is the region averaging exists to clean. On the S22 in daylight the weights would be too lenient everywhere else (too much noise assumed, so real motion gets merged as "noise"). CLAUDE.md applies directly: "A device- or API-reported capability… describes what's possible, not what's safe to use." **The merge must use a measured noise model** (a per-phone, per-ISO table from tripod bursts, or an estimate from each burst's own frame differences), with the reported profile logged beside it. That is piece 3 below.

### 3.2 Burst rate from the stage 2 logs

| Phone, light | Shots | "RAW shot" to both images in memory | Request submit to exposure start | Exposure start to "Issue the next TakePictureRequest" |
|---|---|---|---|---|
| S22, daylight | 5 | 0.29 to 0.32 s | 0.19 to 0.23 s | 0.08 to 0.09 s |
| S26, indoors | 3 | 0.31 to 0.32 s | 0.12 to 0.14 s | 0.14 to 0.16 s |
| S22, dim room (-1038, the draft's figure) | 1 | 0.52 s | 0.38 s | 0.11 s |

OBSERVED in `…-s22/daylight/logcat.txt` (09:34:53, 09:35:08, 09:35:47, 09:35:57, 09:44:53) and `…-s26/logcat.txt` (09:37:37, 09:38:10, 09:38:12). The queued next request is issued at "onImageCaptured", so a back-to-back burst should cycle at about submit latency plus capture time: **about 0.28 to 0.32 s a frame, 3 to 3.5 frames a second, in ordinary light** (INFERRED; the probe measures it). That makes 4 frames about 1.2 s and 8 frames about 2.5 s. One more observation: the S26's plain JPG shots (the on-disk path) took 0.55 to 0.6 s from exposure start to the next request, slower than RAW+JPEG in memory (OBSERVED, 09:38:19 and 09:38:20).

### 3.3 How a daylight nature RAW is exposed

| | S22 daylight (9) | S26 indoors (3) |
|---|---|---|
| Median raw value (black 64, white 1023) | 77 to 94 | 103 to 140 |
| Photosites at white (clipped) | 0.17 to 5.90 % | 0.00 to 0.17 % |
| At or below black | 0.01 to 2.15 % | 0.07 to 0.11 % |

MEASURED (`clip.py`). In daylight the phone already exposes so that half of all photosites sit within about 30 steps of black. Sky still clips on most frames. So the phone's own AE already "protects highlights" in RAW, and even so it can't hold a bright sky. That is HDR's job (-974), not averaging's (INFERRED). Averaging's job is the dark half of the picture, where the noise is the read noise the phones report as zero.

### 3.4 Whole-cell alignment and fine texture (why the merge choice matters for nature subjects)

Alignment to whole 2×2 cells (needed to keep the colour pattern) leaves each tile up to ±1 raw pixel off. INFERRED from 2.2 and 2.3:
- With the draft's per-cell weight (M2), a finely textured tile (gills, moss, needles) either **merges and softens**, since the average of copies offset by up to a pixel is a slight blur, or **reads as motion and is rejected**, so it gets no denoising. A threshold can choose one or the other, not both.
- With the frequency-domain merge (draft's M3), the same tile merges in its low frequencies and keeps the reference's own high frequencies wherever they disagree. That is the published reason for the design (Hasinoff §4, quoted in 2.2).
- The draft rejected M3 as "a denoiser rather than an average, and harder to test bit for bit". The first part is not quite right: with no spatial denoising step (which HDR+ adds separately and stage 3 needn't), M3 is a per-frequency weighted average of the frames. The second part is answered by its two exact limits (Monod §4.2). It costs more compute: two forward FFTs and one inverse per 16×16 tile, per plane, per alternate frame, which is roughly 1 million small FFTs for 4 frames at 12 MP (INFERRED arithmetic). The phone's time is unknown. M3 also fits the draft's streaming design, because the inverse FFT is linear and each frame's contribution can be added into one float output as it arrives (INFERRED).
- **Clipped samples are harder in M3.** A per-photosite "leave this sample out" has no direct equivalent per frequency. One workable rule: a tile in which an alternate frame has any clipped sample the reference does not, or the reverse, falls back to the reference for that frame (INFERRED; to be designed and tested in the merge piece, flagged here as unresolved).

### 3.5 The 16-bit mapping

The draft maps the float mean to WhiteLevel 65535 with one black level at about 6.3 percent (≈4,100 on the S22). **Improve:** scale by the largest power of two k with white × k ≤ 65535 (k = 64 for these 10-bit phones), and write BlackLevel = the reference's four levels × k and WhiteLevel = white × k (here 4096 and 65472). It is the same precision. In return:
- the per-channel black levels survive;
- a reader sees "the camera's levels × 64";
- **a one-frame "average" must equal the input × 64 exactly**, an identity test that catches any scaling slip (section 4).

Values below black still have 4,096 steps of room, as in the draft. Clamps are counted and logged as the draft says.

---

## 4. The draft's design choices, one by one

| Choice (draft section) | Verdict | How, and the source |
|---|---|---|
| Part 0 burst-rate probe first (§2, §7) | **Keep, and widen.** | Add: RAW-only output against RAW+JPEG (OBSERVED: the JPEG path cost differs, 3.2); `getOutputMinFrameDuration` and `getOutputStallDuration` for RAW_SENSOR at the bound size (READ, 2.5); waiting for AE/AWB LOCKED before frame 1 (READ, 2.5); per-frame focus distance, AF state and a green-gradient sharpness figure (for D8). Bright and dim, both phones. Switched on only on `dev`. |
| The 0.5 s per frame figure (§2) | **Improve.** | 0.29 to 0.32 s per shot in ordinary light (OBSERVED, 3.2). The probe replaces both figures. |
| Frame count N = 4, ceiling 8 (§4.1) | **Keep as the provisional number.** | Published 2 to 8 (READ). Bright scenes need fewer (READ). On `dev`, write the running average at N = 2, 4 and 8 from one 8-frame burst so diminishing returns are measured on these phones, not assumed (streaming makes those snapshots nearly free: INFERRED). |
| Lock AE and AWB, check every frame, exclude a mismatched frame (§4.2) | **Keep; improve.** | Wait for a LOCKED result before frame 1 (READ, Android reference, 2.5). A frame with different exposure is excluded and logged, as the draft says. The manual-exposure fallback stays a stop-and-ask. |
| Focus: no AF change, log focus distance (§4.2) | **Keep, and flag for close-ups.** | Focus change is a scale change that tiles can't follow (INFERRED, 2.3). If the probe shows focus moving during bursts, holding focus for the burst comes to the owner. |
| First frame as reference (§4.3, D8) | **Keep for stage 3.** | Log each frame's sharpness (green-channel gradient energy, the published metric, READ Hasinoff §4) so "sharpest of the first 3" can be judged on real bursts before anyone builds it. |
| RAW+JPEG for every frame (§4.3) | **Open until the probe.** | The JPG is only needed for the preview, once connected (-1004). If RAW-only frames are faster, a later piece decides. |
| Copy out and close each image at once (§4.3) | **Keep.** | As stage 2 (`copyRawPlane`, READ `CameraXRawCapture.kt:112-125`). |
| Await each frame's capture result before the next (§4.3) | **Keep.** | The collector holds 32 results, about 1 s of preview at 30 fps (READ `:78-101`; the rate is INFERRED). The probe logs how close it comes. |
| Streaming accumulation (§4.4) | **Keep.** | Matches OSCam (READ) and suits M3 too (3.4). |
| Memory table and the operating limit (§4.4) | **Keep the limit; improve the table.** | With M3 or k-scaling, the weight-sum array can go: tiles with half-overlapped raised-cosine windows sum to 1 (READ, Monod §4.4). That saves about 50 MB at the S26 size. The heap limit is still a device read (UNVERIFIED for both phones). |
| Alignment on a 2×2-averaged grey image, whole cells, coarse-to-fine tiles, method B (§5.1, §5.2) | **Keep.** | As published (READ, 2.3). No sub-pixel at the finest level (READ, Monod §3.3: colour shifts). |
| JVM CI benchmark of alignment time (§7 Part 0 c) | **Replace.** | A JVM time on a CI runner says nothing about a phone. That check would pass without ever seeing the case that matters (CLAUDE.md, "a check that passes because it never saw the data"). Measure on both phones in the alignment piece, logged per frame. |
| Normalise each frame by its own black and white; never 2^bits (§5.3) | **Keep.** | READ, `RawFrameMapping.kt:91-103`; OSCam PHILOSOPHY "White level derives from bit depth in the profile, never from container width" (READ). |
| Clip exclusion as OSCam; keep the counts (§5.3) | **Keep, and keep the counts as data, not just a log.** | The HDR stage reads them (READ, `hdr_merge.py`). Decide now where they live in memory when HDR comes (later stage); don't build the HDR use now. |
| Motion: spatial per-cell weight from NoiseProfile (§5.4, M2) | **Change.** | (a) NoiseProfile's O = 0 makes it unusable as is (3.1), so use a measured model. (b) Prefer the frequency-domain pairwise merge (M3) for fine texture (3.4), or at minimum a spatial weight that also allows for local texture (READ, Wronski §5.2.1). Recommended order: build the plain aligned average first (the τ → ∞ limit) as its own piece, then the robust merge, and compare on the phones. Open question Q3. |
| Drop frames over 50 % mismatch; fail below 2 frames (§5.5) | **Keep.** | No user message in stage 3. Logged on `dev`. |
| Write through `ForagerDngWriter` (§5.6) | **Keep.** | No contract change: `DngImage` holds 16-bit samples and float black levels (READ, `DngContract.kt:24-40`); limits allow white up to 65535 (READ, `DngImageLimits.kt:29-30`). |
| WhiteLevel 65535, one BlackLevel ≈ 4,100 (§5.6) | **Improve.** | Power-of-two scaling, per-channel black × k (3.5). |
| NoiseProfile S/N_eff, O/N_eff (§5.6) | **Keep the rule; fix the input.** | Write the **measured** (S, O) divided by N_eff, and log the phone's values beside it. Note the writer stores three planes, green from the first green (READ, `DngFileWriter.noiseProfile`). Fine for this use. |
| BaselineExposure unchanged (§5.6) | **Keep** (INFERRED, 2.6). | |
| Floating-point DNG rejected (§5.6 item 2) | **Keep the rejection.** The UNVERIFIED part is now READ: DNG 1.4+ supports float data. | |
| Privacy (§6) | **Keep.** The dev file goes through the same writer and check. | |
| Synthetic tests 1 to 9 and 12 (§8) | **Keep, and add:** | (i) **N = 1 identity**: one frame in gives exactly input × k out, every photosite. (ii) **The merge's two limits**: robust merge with c → 0 equals the reference; with c → ∞ equals the plain aligned average, within float rounding. (iii) **Fine texture**: a textured scene shifted by odd pixels keeps its high-frequency energy within a stated bound under the robust merge, while the plain average loses it. This is the test M2 cannot pass and M3 should. (iv) **Shadows merge**: with a noise model whose O is the measured value, a static dark scene is merged (noise ÷ √N). With O = 0 it is not, which documents why the measured model is needed. (v) **Main is unchanged**: with the engine present and the dev switch absent, a JPG and a RAW shot through the real camera entry point produce the same bytes as on the base. |
| Revert checks (§8) | **Keep exactly** (CLAUDE.md, copy-restore, build-log check). Add a revert for the k-scaling (identity test must fail) and for the measured-noise rule. | |
| Device checks (§11) | **Keep 1, 2, 4, 5, 6, 8, 9, 10, 11, 13; drop 3, 7's message, 12.** Change 4's "flat patch" to the **frame-difference** method on a tripod (2.6). Add a close-up fungus (or stand-in) with focus logged, and a bright-sky plus deep-shade scene. | |

---

## 5. Proposed one-piece-at-a-time order for stage 3

Each piece is small, ships to `dev` only (except where noted), is built CI-only and is checked on the S22 then the S26 before the next is dispatched. Nothing in any piece changes what main's camera does.

**Piece 1: burst-rate and lock probe (no files saved).**
Builds: on the dev switch, 8 back-to-back captures in each of four runs: RAW+JPEG and RAW-only, each with and without AE/AWB lock (waiting for LOCKED). Logs per frame: submit time, sensor timestamp, exposure, ISO, post-RAW boost, AE/AWB/AF states, focus distance, black and white levels, copy time, and a green-gradient sharpness figure. Once per run: `maxMemory` and in-use heap, `getOutputMinFrameDuration` and stall for RAW_SENSOR at the bound size. Frames are discarded.
Proves: the real rate, whether exposure and white balance hold, whether focus moves, memory headroom, and the sensor-versus-CameraX gap.
Checked by: logcat on both phones, bright and dim. The planner tabulates it. **The owner sees the rate before piece 2** (Q2).

**Piece 2: burst capture held in memory, and (if the owner agrees, Q1) the frames saved on `dev`.**
Builds: the burst function the engine will call, returning N frames with their capture results, AE/AWB locked and released in a `finally`. On `dev`, the frames are written as N single-frame DNGs through the existing writer.
Proves: frames and results pair correctly, the lock is released on every path, memory holds for N = 8, and the files are clean.
Checked by: exiftool and LibRaw on each file, the log, plus real bursts the owner takes: a tripod still scene, handheld still, leaves in wind, a close-up, sky with deep shade.

**Piece 3: the noise model, measured (analysis, no app code).**
The planner (or a research subagent) computes var(F₁ − F₂)/2 against signal from piece 2's tripod bursts, per phone and per ISO, and compares it with NoiseProfile.
Proves: what σ²(x) really is on each phone, and whether the S22's daylight frames are smoothed.
Output: a small table (or a rule) that piece 5 uses, recorded in `docs/audits/`. If piece 2's frames are not allowed, this piece needs its own dev measurement switch instead.

**Piece 4: alignment (pure Kotlin, `domain/raw/`).**
Builds: the coarse-to-fine whole-cell tile aligner with synthetic tests 1, 2 and 7. On `dev`, per-burst logs of the vectors, mismatch fractions and **time on the phone**.
Proves: known shifts are recovered, the CFA phase is kept, failure is explicit, and the cost on the real phones.
Checked by: the synthetic suite in CI; on the phones, timing and plausible vectors (still scene ≈ 0, handheld a few cells). With piece 2's files, alignment can also be run offline on real bursts.

**Piece 5a: plain aligned average and the mapping (the τ → ∞ limit).**
Builds: the float accumulation with per-frame black and white, clip exclusion with counts kept, k-scaling, NoiseProfile ÷ N_eff from the measured model, written through the writer on `dev` (running averages at N = 2, 4, 8).
Proves: the numbers are right. The N = 1 identity holds, noise falls as √N on a tripod (frame-difference method), the files are clean and decode, and the colours match the reference frame's JPG.
Checked by: tests 3, 5, 6, 8 and (i); on the phones, tripod bursts at N = 2, 4, 8. Handheld ghosting is expected here and recorded as the baseline for 5b.

**Piece 5b: the robust merge.**
Builds: the pairwise merge (recommended: frequency-domain per Bayer plane, published parameters, half-overlapped raised-cosine tiles, measured noise model), with the c → 0 and c → ∞ limit tests and the fine-texture and shadow-merge tests.
Proves: no ghosts on wind-moved leaves, fine texture kept, shadows still averaged, cost on the phone.
Checked by: the same scenes as 5a, side by side with 5a's output, plus the device checks of section 4.

**Piece 6: the measurement round and report (no new code).**
The planner runs §11's remaining checks with the dev switch on both phones and writes `docs/audits/…`. The owner sees noise ratios, timings, crops (described, kept off GitHub) and failure rates **before debayering is planned**.

Why this order: each piece measures what the next one depends on. The rate comes before any decision on N. The real frames come before any tuning. The noise model comes before the merge that needs it. Alignment is checked alone before averaging can hide its errors, and the plain average comes before the robust one so the robust one has a baseline to beat.

---

## 6. Open questions for the owner (plain words)

**Q1. May the test build save every frame of a burst, on the two test phones only?**
To tune the averaging for real woods conditions (wind in leaves, close-ups, shade and sky), the most useful thing is a few dozen real bursts on the laptop. -974 says "only keep the final product", which is about what users get. This would apply only to the dev build on your test phones, the files kept off GitHub like the other device evidence, and never in a release.
- **A (recommended):** yes, dev build only.
- B: no. Tuning uses synthetic tests and the averaged results only, which is slower to get right and blind to this camera's real noise.

**Q2. Is about three photos a second fast enough to start with?**
The camera library the app uses takes RAW frames about one every 0.3 seconds. Phone camera apps take them roughly ten times faster from a continuous stream. Once averaging is automatic for every JPG, 4 frames means holding still about 1 second, and 8 frames about 2.5 seconds. Nothing is decided now; piece 1 measures it first.
- **A (recommended):** build on the current library, measure, and come back to you with the numbers before connecting it to normal photos.
- B: plan the faster route (a second camera connection outside the library) now. That is a large change to the camera code.

**Q3. When frames don't line up perfectly, what should win: grain or fine detail?**
Hand shake leaves frames slightly offset. On fine texture (gills, moss, lichen, needles) a simple average softens the detail, and a simple "use the first frame where they disagree" leaves those areas grainy. The published phone method splits each small patch into coarse and fine detail, averages the coarse part and keeps the first frame's fine detail. It is more work for the phone.
- **A (recommended):** build the simple average first (piece 5a) as the yardstick, then the published method (5b), and compare them on real photos before choosing.
- B: only the simpler method for now.

**Q4. Where does the engine live until debayering arrives?**
Main would carry the averaging code with nothing in a release that uses it. The test switch lives on the dev branch, which today is "main plus one commit restoring the dev tools".
- **A (recommended):** engine on main (tested, unused), and the test switch as a second commit on dev, so the restore commit stays as you ruled.
- B: keep the engine on dev too, until debayering is ready to connect it.

(Also for the record, no answer needed unless you disagree: stage 3 keeps the phone's own exposure, so an averaged file can be compared directly with a single RAW. Deliberately darker captures to save bright skies belong with HDR.)

---

## 7. What the draft is missing (collected)

1. The phones' noise profile is unusable as reported (O = 0; S off by 3 to 6× on the S22 in daylight). A measured model is needed (3.1).
2. Waiting for AE/AWB LOCKED before frame 1 (2.5).
3. The sensor's own minimum frame duration, read for comparison (2.5).
4. The fine-texture trade-off of whole-cell alignment plus a spatial weight, and the frequency-domain merge's exact limits as test oracles (2.2, 3.4).
5. Clip counts kept as data for the HDR stage (2.6).
6. Exact power-of-two scaling and the N = 1 identity test (3.5).
7. Measuring noise by frame differences on a tripod, not flat patches (2.6).
8. Focus breathing on close-ups as an alignment failure that tiles can't fix (2.3).
9. The reachability question for an uncalled engine on main under -930 (1.3).
10. Real bursts as development data (Q1).
11. Phone-side, not JVM, timing for alignment and merge (section 4).

---

## 8. Sources

**Read in writing this:**
- RECORD (`records-after-173` `81ea7bbe`): -930, -973, -974, -975, -1008, -1009, -1010, -1040, -1042, -1043, -1044.
- Draft `prompts/preserved/2026-10-15-04.md` (the document audited).
- Forager `origin/raw-stage2` `d8f0f944`: `photo/CameraXRawCapture.kt`, `photo/RawShotSaver.kt`, `domain/RawFrameMapping.kt`, `domain/raw/DngContract.kt`, `data/raw/ForagerDngWriter.kt`, `data/raw/DngImageLimits.kt`, `data/raw/DngFileWriter.kt` (tag section).
- Forager `CLAUDE.md`.
- Device files, read in place: `~/Zynergy/device-evidence/2026-10-15-raw-stage2-s22/daylight/` (9 DNG, logcat), `…-s22/shot1.dng` and `logcat.txt` context from the draft, `…/2026-10-15-raw-stage2-s26/` (3 DNG, logcat).
- OSCam `4822c00` (github.com/slayer8366/OSCam): `frame_average.py` (docstring, `average_burst`), `hdr_merge.py` (docstring), `debayer.py` (function list, `--assume-linear`), `PHILOSOPHY.md` (strict rules).
- S. W. Hasinoff, D. Sharlet, R. Geiss, A. Adams, J. T. Barron, F. Kainz, J. Chen, M. Levoy, "Burst photography for high dynamic range and low-light imaging on mobile cameras", ACM TOG 35(6), 2016. https://people.csail.mit.edu/hasinoff/pubs/HasinoffEtAl16-hdrplus.pdf (§1, §3, §4, §5, §7 limitations). A phone maker's published burst pipeline.
- A. Monod, J. Delon, T. Veit, "An Analysis and Implementation of the HDR+ Burst Denoising Method", IPOL 11 (2021). https://www.ipol.im/pub/art/2021/336/ (article_lr.pdf: §3.2, §3.3, §3 limitations, §4.1 to §4.4, §5).
- B. Wronski et al., "Handheld Multi-Frame Super-Resolution", ACM TOG 38(4), 2019. https://arxiv.org/abs/1905.03277 (§3.1, §5.2.1, Fig. 1).
- O. Liba et al., "Handheld Mobile Photography in Very Low Light", ACM TOG 38(6), 2019. https://arxiv.org/abs/1910.11336 (§6: shutter lag and ZSL, black levels).
- Adobe, Digital Negative Specification 1.6.0.0 (2021), via a mirror: https://paulbourke.net/dataformats/dng/dng_spec_1_6_0_0.pdf (BaselineExposure, BaselineNoise, NoiseProfile, floating-point image data).
- Android reference: `CaptureRequest` (CONTROL_AE_LOCK, CONTROL_AWB_LOCK), https://developer.android.com/reference/android/hardware/camera2/CaptureRequest. `CameraCaptureSession` (captureBurst), https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession. `StreamConfigurationMap` (getOutputMinFrameDuration, getOutputStallDuration), https://developer.android.com/reference/android/hardware/camera2/params/StreamConfigurationMap.
- CameraX ZSL guide, https://developer.android.com/media/camera/camerax/take-photo/zsl.

**UNVERIFIED or not determined:**
- Whether CameraX 1.6.2's ZSL can carry RAW (inferred no: the page describes JPEG only and PRIVATE reprocessing).
- Why the S22's daylight noise reads lower than its profile (wrong profile, raw smoothing, or binning-correlated noise). That the S22 Ultra's main sensor bins pixels is from memory.
- The two phones' heap limits.
- Kotlin alignment and merge time on these phones.
- That tile translation absorbs rolling-shutter skew at handheld speeds.
- That BaselineExposure needs no change under rescaling (the spec text read has no rule on it; inferred).
- The burst cycle of 0.28 to 0.32 s per frame is inferred from single shots, not from a queued burst.
- The 10th-percentile noise estimator's bias (about 13 percent low) is a sampling-theory estimate, not calibrated here.
