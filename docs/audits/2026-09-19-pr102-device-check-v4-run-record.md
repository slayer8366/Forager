# Device check v4: run record, 2026-09-19

**This is a run record, not an audit and not a device check.** The check is
`docs/audits/2026-09-16-pr102-device-check-v4.md`. This is what happened when it was run.

**Device:** Samsung Galaxy S26 Ultra.
**Build:** `de78d304f59efbe79881960d514bdda6199014a8`, version code 732, CI run #575 on PR #102.
**Android version:** Android 16.
**Run by:** the owner, from readings taken off the device and off the shared diagnostics log.

This is the same commit `docs/audits/2026-09-19-pr102-description.md` describes, so the PR
description and this record are anchored to the same tree.

**On the version code.** `versionCode` is the commit count (`app/build.gradle.kts:56`), and
`git rev-list --count de78d30` is **731**, one less than the 732 the build reports. The likely
explanation is benign: a CI build on a pull request builds the merge ref rather than the head
commit, which adds one. Recorded because this repo has used versionCode-equals-commit-count as an
integrity check before, and a later reader seeing 732 against 731 would otherwise have no way to
tell this from a build off an uncommitted tree.

**Every reading below is quoted verbatim from the run.** Nothing is reconstructed. Steps with no
section here were not run in this session.

---

## Step 7: two entries per photo

### Baseline

Not recorded. The counts below are the entries themselves rather than a delta against a baseline,
so the invariant is established by pairing rather than by counting up from a known start.

### Three photos, setting off, held upright

```
2026-09-19T22:31:19.638Z capture shot deviceRotation=0 displayRotation=0 targetRotation=0 requestDegrees=90 resolution=4080x3060
2026-09-19T22:31:20.522Z capture shot deviceRotation=0 displayRotation=0 targetRotation=0 requestDegrees=90 resolution=4080x3060
2026-09-19T22:31:21.649Z capture shot deviceRotation=0 displayRotation=0 targetRotation=0 requestDegrees=90 resolution=4080x3060

2026-09-19T22:31:20.122Z capture orientation 'ec595528-caa0-4630-9ff1-64b8557995e7.jpg' kept fromTag=6 degrees=90
2026-09-19T22:31:20.949Z capture orientation '6e536d68-d3df-4240-8527-3c13697838a9.jpg' kept fromTag=6 degrees=90
2026-09-19T22:31:22.121Z capture orientation 'd5b243a0-d0db-4072-b9f8-71cd12a14831.jpg' kept fromTag=6 degrees=90
```

**Three photos, three shot entries, three outcome entries. The two-entry invariant holds for this
batch.**

### One photo, setting ON, phone held sideways

```
2026-09-19T22:51:50.413Z capture shot deviceRotation=0 displayRotation=0 targetRotation=0 requestDegrees=90 resolution=4080x3060
2026-09-19T22:51:50.971Z capture orientation 'f20bf21d-7f2a-49ae-ba56-32b31a3c091f.jpg' kept fromTag=6 degrees=90
```

**`deviceRotation=0` while the phone was held sideways.** The hold was confirmed by the runner
after the fact, and finding 4 below rests on it. This is the portrait-lock gate working:
`effectiveDeviceRotation` pins one value that both the glyphs and the capture read, so the log
records the effective rotation rather than the raw sensor reading.

### Two photos, setting off, one in each landscape

```
2026-09-19T22:56:59.305Z capture orientation '3c1a58d1-1192-4fb5-991d-896d24855d89.jpg' kept fromTag=1 degrees=0
2026-09-19T22:59:07.687Z capture orientation '6edc6fba-11f9-4185-a3e4-35e55e225057.jpg' kept fromTag=3 degrees=180
```

### Orientation tags across three holds

| Hold | fromTag | degrees | Outcome |
|---|---|---|---|
| portrait | 6 | 90 | kept |
| port-right | 1 | 0 | kept |
| port-left | 3 | 180 | kept |

Three distinct holds, three distinct EXIF tags, each matching its requested degrees.

---

## Step 8: captures and the sweep

From the Diagnostics panel after a force stop, reopen and five-second wait:

```
photos/ · 11 files
captures/ · 0 files
No files.
```

Sweep line:

```
2026-09-19T22:41:12.588Z sweep deleted=0 orphaned capture file(s)
```

**`deleted=0` is the pass.** Nothing was left behind.

The ten-photo single-session run specified in step 8 item 2 **was not done as written**. Photos
accumulated across several smaller batches instead.

---

## Step 10: the privacy scrub

Photo taken during this run. Transferred and inspected on a Linux host.

```
[ExifTool]      ExifTool Version Number         : 13.50
[System]        File Name                       : out.jpg
[System]        File Size                       : 4.4 MB
[File]          File Type                       : JPEG
[File]          File Type Extension             : jpg
[File]          MIME Type                       : image/jpeg
[File]          Exif Byte Order                 : Big-endian (Motorola, MM)
[File]          Image Width                     : 4080
[File]          Image Height                    : 3060
[File]          Encoding Process                : Baseline DCT, Huffman coding
[File]          Bits Per Sample                 : 8
[File]          Color Components                : 3
[File]          Y Cb Cr Sub Sampling            : YCbCr4:2:0 (2 2)
[IFD0]          Image Width                     : 4080
[IFD0]          Image Height                    : 3060
[IFD0]          Orientation                     : Rotate 90 CW
[ExifIFD]       Light Source                    : Unknown
[JFIF]          JFIF Version                    : 1.01
[JFIF]          Resolution Unit                 : cm
[JFIF]          X Resolution                    : 59
[JFIF]          Y Resolution                    : 59
[Composite]     Image Size                      : 4080x3060
[Composite]     Megapixels                      : 12.5
```

Hex tail:

```
00435c70: 5a2b 7b73 6970 8b1b ab17 557d f344 b210  Z+{sip....U}.D..
00435c80: 481f de07 3f8e 68a2 b22d fc27 ffd9       H...?.h..-.'..
```

**Pass, against every condition in the step:**

- `Orientation: Rotate 90 CW` present. EXIF 6, matching the `kept fromTag=6` entries.
- **Absent:** every `GPS*`, `MakerNote*`, `XMP*`, `IPTC*`, `ThumbnailImage`, `DateTimeOriginal`,
  `CreateDate`, `ModifyDate`, `Make`, `Model`, `Software`, and every serial number tag.
- File ends `ff d9` with nothing after it.

---

## Step 12: trailer

**Answered by step 10's hex tail rather than by the log**, as the step says it should be. Nothing
follows EOI in the file the scrub wrote.

---

## Findings from this run

### 1. `ExifIFD Light Source` survived an allowlist scrub

`Light Source : Unknown` is present in the scrubbed file.

It carries nothing identifying and `Unknown` is the null value, so this is not a privacy failure.
It is a scope question: the scrub is an allowlist and something got through it. Worth establishing
whether it is deliberately kept or incidentally surviving.

### 2. `ICC_Profile` is absent on a wide-gamut device

The device has an AMOLED wide-gamut screen and the scrubbed file carries no `ICC_Profile`.

Step 10 treats a missing profile on a wide-gamut screen as a finding rather than a pass. **Step 11
decides whether it matters visually**, and step 11 was not run.

### 3. `rewritten` may be unreachable on this hardware

Every capture in this run read `kept`. The HAL writes the correct orientation tag in all three
holds, so `IntendedOrientation` has nothing to correct.

**This is not a gap in the run.** The reapply path exists for devices whose HAL writes the wrong
tag, and this one does not. Recorded so that a later reader seeing no `rewritten` outcome does not
conclude the mechanism is untested rather than unexercised.

### 4. The portrait-lock gate corroborated on hardware

`deviceRotation=0` in a sideways hold with the setting on is the gate working:
`effectiveDeviceRotation` pins one value that both the glyphs and the capture read.

**No document change follows from this.** Step 7 item 4 asks for the capture, the entry counts and
the outcome label with its `reason=` text. It sets no expectation about `deviceRotation` at all;
the only mentions of that field in the check are step 7's description of the log format.

The setting-on expectation lives in step 4.2, which already reads "nothing moves and nothing
turns … The setting pins one rotation value that both the controls' angle and the photo's
orientation read." That wording carries its own superseding note dated 2026-09-18, correcting an
earlier version which had said controls turn in place. The misreading was caught by an emulator
run and fixed in the document before this run happened.

**So this is corroboration, not a finding against the check.** Step 4.2's claim was established
from the code and an emulator; this is the first time it has been read off a real capture on
hardware.

An earlier draft of this record claimed step 7 item 4 asked for a sensor reading that follows the
hand, and recommended correcting it. That was written from recollection rather than from the
document, and it is wrong in both halves: the step asks no such thing, and the correction it
proposed was already made. Recorded here rather than silently removed, because the error is the
one this project keeps catching.

---

## Not run in this session

Steps 1, 2, 3, 4, 5, 6, 9 and 11.

Steps 1, 2, 4 and 5 were run on earlier builds during the work and are **not carried forward here**:
the camera open path changed substantially afterwards.

Step 9, the main-thread StrictMode check, is the one that would settle the
`FilePhotoStore.persist` before-state that the PR description records as inferred rather than
measured.
