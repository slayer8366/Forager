# Orientation from the sensor, the bound Camera kept, scrub stops at EOI: completion report

**Date:** 2026-09-15 (work began late 2026-09-14).
**Request:** the owner's plan following the eight-item verification pass, tranche one, "into PR 102,
before the device check", in the plan's order; the plan's open question answered "Do torch first",
which orders the *later* tranches and changes nothing here.
**Branch:** `claude/new-session-vto65i`, PR #102, open and not merged. From `95ae9ac` to `035fc64`
for the code, docs following. No schema change, no dependency change.

---

## §1 — Orientation from the device's sensor (a shipped defect, so it went first)

`CameraXCaptureSession.capture` set `targetRotation` from `PreviewView.display.rotation`. The
verification pass found the case that breaks: with auto-rotate off and the phone held landscape, the
display never rotates, the read stays at `ROTATION_0`, and every landscape photo is tagged portrait.
CameraX's reference for `setTargetRotation`, fetched rather than recalled, names it: *"display
orientation may be locked by device default, user setting, or app configuration ... In these cases,
set target rotation dynamically according to the android.view.OrientationEventListener."*

Now an `OrientationEventListener` registered in `Viewfinder`'s `DisposableEffect`, snapped with
`UseCase.snapToSurfaceRotation` (confirmed present in camera-core 1.6.2 by `javap`), read per shot.
The display read remains as the fallback for a device whose listener reports it cannot detect
orientation, and for a shot before the first reading; each is logged once when it fires. The class
doc that argued *for* the display read is rewritten to record the correction.

**Untestable here by construction** — no line of `CameraXCaptureSession` runs under Robolectric —
and **the device check gains this case by name:** auto-rotate off, phone landscape, photo upright.

## §2 — The bound `Camera` is kept

`bindToLifecycle` returns a `Camera` and line 143 discarded it. Kept in a field now, cleared on
dispose. Nothing reads it. It is the object torch (`cameraControl.enableTorch`,
`cameraInfo.hasFlashUnit`) and tap-to-focus (`cameraControl.startFocusAndMetering`) both need, and
discarding it was the one line that would have forced the bind lambda to be restructured later.
Groundwork, and recorded on the class as exactly that, because a field with no reader is otherwise
the thing CLAUDE.md's reachability rule asks about.

## §3 — The scrub stops at the first EOI

The streaming walk copied everything after the first SOS verbatim, including anything after EOI.
The header said *"whatever is not deliberately kept is gone"*; a trailer was not kept deliberately
and was not gone. A Motion Photo's MP4, a depth map, an OEM trailer all live after EOI precisely
because a reader that stops there never sees them.

**Fixture and failing test first.** A trailer appended to the real fixture, carrying a decoy `FF D9`
*inside* it with bytes after. Assertions on the output's last two bytes, on the trailer's content
being absent, on the EOI count being one (the decoy went with the trailer, so the walk stopped at
the *first* EOI), and on byte identity from SOS through EOI. A second test does the same with an
orientation to reapply, since `saveAttributes` rewrites the file and must not resurrect the tail.

**The first fixture was wrong, and the failure said so.** Its trailer *ended* in the decoy `FF D9`.
Two consequences: the ends-at-EOI assertion was satisfied by the unfixed code, and the test's own
"exactly one EOI" precondition failed — "expected:<1> but was:<2>". That is not the failure the
test predicted, so per CLAUDE.md the test was corrected before the code: the decoy moved inside
the trailer with bytes after it, and the preconditions count the image's EOI and the whole file's
separately. Re-run against the unchanged scrub, both tests then failed at "the output ends at EOI".

**Progressive JPEGs.** Stopping at EOI correctly means not stopping at the first marker after the
first scan: a progressive file has DHT tables and further SOS segments between its first scan and
its single EOI, and the old verbatim copy carried them by accident. A ten-scan fixture was
generated with the JDK's `ImageIO` in progressive mode and embedded as base64 (`javax.imageio` is
not on the Android unit-test classpath, as `PhotoOrientationTest` already records). Its test passed
against the old code and passes against the new, which is the guard the parser change had to keep.
*"Decodes identically"* is not assertable here — Robolectric's `BitmapFactory` fakes a 100×100
bitmap for any bytes — so the claim is byte identity from the first SOS through EOI.

The walk after SOS: copy entropy data until a real marker (`FF 00` is stuffing, `FF D0`–`D7` a
restart, a run of `FF` is fill); write the marker; stop on EOI; otherwise copy that segment whole
and continue. No allowlist applies after the first SOS. Header comment corrected to say what the
scrub removes, trailer included.

---

## Evidence

### Suite counts, from JUnit XML

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`95ae9ac`) | 180 | 1449 | 0 | 0 | 24 |
| after (`035fc64`) | 180 | 1452 | 0 | 0 | 24 |

1449 + 3 (two trailer tests, one progressive) = **1452**, counted. Skip count unchanged; CI
allowlist untouched. `assembleDebug` exit 0. Zero `e:` lines in the build log.

### Revert checks

Two, through the runner. No compile errors; tree clean after both.

| revert | failure produced |
|---|---|
| tail copied verbatim again (`input.copyTo(output); return true`) | exactly the two trailer tests: "the output ends at EOI" |
| walk stops at the first marker after a scan (`return true` unconditionally) | exactly the progressive test: "every scan survived expected:<10> but was:<1>" |

Each isolates one claim and fails exactly the test that holds it. §1 and §2 have no revert check
and cannot: nothing under Robolectric reaches the file.

---

## Disclosure

### Confirmed by observation
- Every count and message above, from XML and build logs in this container.
- `snapToSurfaceRotation` and `Camera` in the pinned artifacts, by `javap`.
- The CameraX documentation quoted, by fetch.
- The progressive fixture's ten SOS markers and one EOI, asserted as preconditions in the test.

### Inferred, not observed
- That the listener reports within the first sensor tick after `enable()`, so the display fallback
  is rare. It is logged when it fires, so a device log would show it.
- That a real CameraX capture never places a coincidental `FF D9` inside a post-SOS table
  segment. The walk parses tables by length, so it would not stop on one; the test helper
  `sosToEoi` would, which is why every test using it asserts the EOI count first.

### Could not be determined
- Whether the auto-rotate-off landscape photo now comes out upright. Device check.
- Whether any capture on the owner's devices carries a trailer. Device check; it does not gate the
  fix, which makes the header's privacy claim true regardless.
- The cost of byte-at-a-time reads on a full-size capture. Off-main, buffered, unmeasured.

### Premises that were wrong
- **Mine, in the first trailer fixture** (§3). Caught by the failure not matching the prediction.

### Decided beyond scope
- The logged display fallback in §1, rather than failing the capture when no reading exists.
  A shot with a possibly-wrong tag beats no shot, and the log says which happened.
- Treating `0x01` and `D0`–`D8` after a scan as standalone rather than malformed. Not expected
  there; not reinterpreted.

### Checks that did not fire, and empty results
- No CI run at the time of writing.
- No test exercises the listener's fallback paths; they are device-only.
