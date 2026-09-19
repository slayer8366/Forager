# PR 102 device check, v4

**Status:** current. Supersedes [v3](2026-09-16-pr102-device-check-v3.md), which is kept intact and
marked superseded. Run this one.

**Written against:** `claude/new-session-vto65i` at `5536f59`, debug build containing `a9587d7` or
later. As of `5536f59`, no app code has changed since `1e7d97a`, which v3 was written against — the
commits between them are documentation only — so a build that satisfied v3 satisfies v4.

## Record note: why v1 and v2 are not in this repository

v1 and v2 were written in conversation and delivered as files to the owner. They were never
committed. That is the defect the v3 commit closed, and it had already cost something concrete: the
Diagnostics completion report at `docs/audits/2026-09-15-debug-diagnostics-instrument-completion-report.md`
cites "three of its nine steps" and names a step 5, referring to v1's numbering, and a later pulse
could not reconcile those references because no device-check document existed in the tree to read.
Per `record-and-supersede`, v1 and v2 are not reconstructed. They are recorded as superseded and
unavailable, and this document stands on its own.

## Record note: why v4 exists

v3 was written from dispatch reports for steps 3, 4, 7 and the noise list, and said so. Read against
the tree the same day (`2026-09-16-device-check-v3-tree-corrections.md`), four of its claims were
wrong. Two of them would have cost a run:

- **Step 7 told the runner to count `Shot:` entries in the panel.** That string is a logcat line.
  The panel's entries read `capture shot …` and `capture orientation …`. On a run whose entire
  premise is a phone with no logcat, a runner searching for `Shot:` finds nothing — and cannot
  distinguish "wrong string" from "invariant failed", which is the single reading step 7 exists to
  produce. A false failure report was the likely outcome.
- **Step 12 asked the log for a number nothing writes.** Inviting a scan of up to a megabyte of log
  for an entry that cannot be there.

The other two were a miscount of branch labels in step 7 and a section number cited twice from
memory. All four are carried in the body here rather than left to a companion note, because a device
check is an operational instrument as well as a record, and expecting the runner to read the
corrections before the procedure is the same class of assumption that produced the defect. v3 stays
in the tree, unedited apart from its status line, so the record of what was written survives.

**Numbering is unchanged from v3.** A v3 step number and a v4 step number mean the same step.

### Step mapping, v1 (nine steps) to v3/v4 (twelve steps)

Anything in the repository that cites a v1 step number should be read through this table. The
completion report's own references are correct as written for v1 and are not edited; this table is
the bridge.

| v1 step | v3/v4 step | Note |
|---------|------------|------|
| 1 | 1 | orientation of the saved photo, unchanged in intent |
| 2 | 2 | opens and reopens, plus the epoch case |
| 3 | 3 and 4 | split when the window lock became conditional on the portrait setting |
| 4 | 5 | photo orientation matrix, fourth row added for setting ON |
| **5** | **10** | **scrub content. The completion report's procedure for it is at §3, report line 83** |
| 6 | 6 | retention and routing |
| 7 | 8 | multi-shot, release, sweep |
| 8 | 9 | main thread |
| 9 | 11 and 12 | colour split from the trailer observation |
| new in v3 | 7 | capture-orientation diagnostics, the two-entry invariant |

Steps 3, 4 and 7 have no v1 ancestor in substance. They test behaviour that did not exist when v1
was written.

**Do not carry any result forward from a v1, v2 or v3 run.** Every change since v1 touches the
camera open path, which most steps exercise; and v3 was never run.

**Instrument:** Tools, Settings, Diagnostics. The Diagnostics store, the Diagnostics panel and
StrictMode are all debug-only by source set, so a release build produces nothing to read. This
assumption is load-bearing for steps 7 through 11.

Run in order. Steps needing no instrument come first, so a failure costs two minutes rather than
forty. A failure in steps 1 to 6 stops the run.

Record every step, including passes. A step not run is recorded as not run, with the reason.

## Before starting

Write down: device make and model, Android version, security patch, build commit, system
auto-rotate state, whether the display is wide gamut, and whether a desktop EXIF tool is
available. Step 10 needs one.

Three things in the Diagnostics log that are **not** findings:

- Framework and startup entries, one stack each.
- A disk-read violation from opening **Crash Logs** itself. That panel lists its files on the
  main thread via `crashFileStore.list()` during composition, predates the policy, and has its
  own record.
- **An absent early entry may be rotated rather than missing.** The log rotates at 1 MB and the
  panel reads only the current generation. After a long session, do not read a missing early
  entry as "never written".

---

## 1. Orientation of the saved photo

"Lock camera to portrait" **off** for all four — which is the shipped default, so this is the state
a fresh install is already in. Take one photo per case, persist it, then view it **in the app** and
**in the device gallery**. The gallery honours EXIF, so sideways there is a failure even if the app
looks right.

| Case | Auto-rotate | Phone held | Expected |
|------|-------------|-----------|----------|
| 1a | on | portrait | upright in both |
| 1b | on | landscape | upright in both |
| 1c | **off** | landscape | upright in both |
| 1d | off | portrait | upright in both |

1c is the case the `OrientationEventListener` exists for.

Evidence: which cases ran; for any failure, which viewer and which way.

## 2. Camera opens and reopens

1. Open the dialog: a live viewfinder, not a spinner.
2. Done, then reopen: live viewfinder again.
3. **Epoch case:** open, dismiss *while the spinner is showing*, immediately reopen. Camera must
   work. Run three times; the race is timing dependent.

Evidence: how many epoch runs, what happened each time.

## 3. Window behaviour, setting OFF — the strip, the bands, the hidden bar

The window pins to whatever it already was when the camera opened, and never moves after. Every
placement below is in **device anatomy**: hold the phone screen towards you, charger port down —
*bottom* is the port edge and *top* is the punch-hole edge, and those labels do not move when the
phone turns.

**What to look for.** No system status bar (the clock and icons are gone) but the navigation bar
or gesture handle still present. A **strip** along the punch-hole edge holding one thing: an
outlined, transparent box labelled "Strip" filling the row — the placeholder for PR #103's
controls, there so you can judge its size and position; say whether it should ship. **There is no
Done control**: the navigation bar's Back (or the back gesture) closes the camera, and is the only
way out. The **shutter** on the port edge, a white disc with a black ring. The
photo count beside or above it. **Every glyph has a black outline** and no control has a background;
the image runs edge to edge underneath.

1. App in **portrait**, open the camera: portrait frame, strip across the **top**, sitting **below
   the punch-hole** (not through it); shutter at the **bottom centre**, clear of the navigation
   bar. No status bar. **No flip animation.**
2. Rotate the phone both ways, and upside down: **nothing in the layout moves.** The count and the
   "Strip" label turn in place to read in the current hold; the shutter, a disc, does not.
3. App in **landscape with the charger port on your right**, open the camera: landscape frame,
   **shutter on the port edge** — the screen's right — vertically centred on the **true centre of
   the screen**, count inboard of it; the **strip runs down the punch-hole edge**, the screen's
   left, **inboard of the cut-out**. No status bar. No flip animation. At open the count and the
   label read upright, because the phone and the window agree.
4. **App in the other landscape, port on your left**, open the camera: the mirror of 3.3 — shutter
   on the screen's **left**, count inboard to its right, strip down the screen's **right**. No flip
   animation.
5. Rotate from either of those: **nothing in the layout moves, and the count and the label turn in
   place** — the same rule as 3.2. A label that stays sideways after the phone turns is a
   failure, in this arrangement exactly as in portrait.
6. **Swipe down from the screen's top edge** with the camera open: the status bar shows
   transiently and the shade can be pulled. **Record whether that took one swipe or two.**
7. Close the camera with **Back** — the navigation bar's button, and again with the back gesture
   if the phone uses gestures: the camera closes, **photos already taken are kept**, and the status
   bar is back each time. Then rotate the phone: the screen underneath follows again, proving the
   lock released.
8. Camera open, background the app for **over four minutes**, return: the camera has closed
   (step 6.2) **and the status bar is back**. This is the exit most likely to be missed by a
   design that restores the bar itself; here the bar returns because the dialog's window is gone.

Any flip animation on open in this state is a failure. The shutter along the bottom of a landscape
frame is a failure. **The shutter on the punch-hole edge is a failure. A strip that runs through
the cut-out, or a status bar that stays hidden after any exit, is a failure.**

> **Superseding note, 2026-09-18 (camera overlay spec), amended the same day.** This step was
> rewritten whole. What it used to say, and why each part changed:
> - **"Done top-left"** — Done was placed by a screen side, which put it on the punch-hole edge in
>   two arrangements and on the port edge in the third. The overlay build first moved it into the
>   strip as an outlined ✕; the owner then **removed it entirely**: the navigation bar's Back was
>   already wired to the same close as Done (both reached `onDismiss`), so Done was a second control
>   for one function. Back is now the only way out, and 3.7 tests it carries everything Done did —
>   the camera closes, the photos stand, the bar returns.
> - **"Done and the count upright, not turned"** (already superseded on 2026-09-18, above) — one
>   rotation rule, every arrangement; the strip's label obeys it too.
> - **No mention of the status bar** — it is now hidden on the dialog's own window while the camera
>   is open, the navigation bar kept, and the safe area collapses: the controls use the space the
>   bar occupied. Items 3.6–3.8 are new and test the bar's return on each exit.
> - **No mention of the strip** — new: the band on the punch-hole edge, inboard of the cut-out,
>   with the gated placeholder. Items 3.1, 3.3 and 3.4 now name it.
> - **"The layout does not re-arrange"** — still true and still asserted (3.2, 3.5); what was
>   withdrawn is the idea that not re-arranging meant not turning.
>
> Built at `ee59e81`+ on this branch; the emulator run is in
> `2026-09-18-camera-overlay-completion-report.md` with screenshots under
> `img/2026-09-18-camera-overlay/`.

Evidence: one line per item, **which physical edge the port was on** for 3.3 and 3.4, the swipe
count for 3.6, and a note on the placeholder's size and position for the owner's decision.

## 4. Window behaviour, setting ON

"Lock camera to portrait" on. The window is forced portrait always.

1. App in landscape, open the camera: **one flip to portrait is expected and accepted**, then the
   portrait arrangement with the shutter at the bottom.
2. Rotate the phone every way, including upside down: **nothing moves and nothing turns** — not
   the count, not the "Strip" label, not any control. The setting pins one rotation value that both
   the controls' angle and the photo's orientation read, which is the whole point of it: with it on
   the camera is a portrait camera, and a control that turned would be following a sensor the
   setting exists to ignore.
3. Close the camera: the lock releases as in 3.7.

> **Superseding note, 2026-09-18.** Item 4.2 read "nothing moves; controls turn in place". The
> second clause was the planner applying the general glyph-rotation rule without checking that the
> portrait lock makes it a special case — the setting's own doc says "nothing in the camera turns"
> and `effectiveDeviceRotation` pins the one value both glyphs and capture read. The step, the code
> and the test named for the case (`InAppCameraDialogLandscapeTest`, which drove the fake session
> past the gate and so tested only the wiring) disagreed from the day the setting shipped, and an
> emulator run of this step on 2026-09-18 was the first thing to notice: with the setting on and a
> portrait open, nothing turned in any hold. The test now goes through the gate. Owner's ruling:
> nothing turns with the setting on; with it off, glyphs rotate as before.

Evidence: one line per item, and for 4.2 **say which holds you tried** and that the label and the
count stayed put in each.

## 5. Photo orientation matrix

Four combinations, one photo each, checked in the gallery.

| Setting | Phone held | Expected saved photo |
|---------|-----------|----------------------|
| off | portrait | portrait, upright |
| off | landscape | landscape, upright |
| on | portrait | portrait, upright |
| on | landscape | **presented tall, scene on its side** |

The last row is the setting working, not a failure: same sensor frame, no crop, saved portrait.

Evidence: four confirmations.

## 6. Retention and routing

1. **Short absence.** Rotate with the camera open, then background the app and return **within a
   minute**: the camera survived the rotation and is **still open**, session intact.
2. **Long absence.** Camera open, background the app and leave it for **over four minutes**, then
   return: the camera is **closed**.
3. **Picker round trip.** Camera open, open the **gallery picker**, return promptly: the camera is
   **still open**. This is the case close-on-background would have broken, and the reason the
   behaviour is a threshold rather than a lifecycle hook.
4. Developer options, **Don't keep activities** on. Open the camera, background, return: camera
   must be **closed**, whatever the elapsed time. Turn the setting back off afterwards.
5. Open the camera from a **log entry**, take a photo, confirm it lands on that record.
6. Same from the **album**.
7. Same from a **cartography entry**.
8. **Return turns the window; the layout must follow.** Open the camera in **landscape**,
   background, return in **portrait** inside four minutes: **portrait window, portrait
   arrangement** — strip horizontal along the top, shutter at the bottom. Not a strip down one side.
9. The mirror: open in **portrait**, background, return in **landscape** inside four minutes:
   **landscape window, landscape arrangement**, shutter on the charger-port edge, strip on the
   punch-hole edge. Do it for **both** landscapes, so both arrangements are exercised.
10. Rotate while the camera is open and stays open: **nothing reflows**, as in step 3.
11. Setting **on**: open in landscape, background, return in portrait. Portrait throughout,
    unchanged.
12. After any of 6.8–6.9, the status bar is still hidden and still reveals on a swipe from the top.

A photo arriving on the wrong record is a quiet failure no other step catches. **A window of one
shape carrying the other shape's arrangement is a failure** — it was the 2026-09-18 device finding
that led to the arrangement following the window (`InAppCameraDialog`, the `remember` key).

> **Superseding note, 2026-09-17.** Item 6.1 previously read "background the app and return: the
> camera survived the rotation and is **closed** on return", asserting a close-on-background that
> **no code implemented and no decision had ever specified** — established by the finding report
> (`2026-09-17-shutter-port-edge-and-camera-retention.md`). The owner's ruling was that the
> behaviour should exist, and it now does, as a **four-minute absence threshold** rather than
> close-on-background: `702be42`, reasoning on `CameraAbsence.kt`. Items 6.1–6.3 replace the old
> 6.1 and test both sides of the threshold plus the picker case; the old items 6.2–6.5 are now
> 6.4–6.7, unchanged in substance.
>
> **Two things the old step implied that are not tested here, because they are not true.** It is
> not the rotation that matters — the rotation is irrelevant to the symptom, so 6.1 keeps it only
> as a combined check. And the timeout does **not** need to rescue unsaved captures: every capture
> is persisted to the in-app album on the shutter tap, so there is nothing for it to save. Nor does
> it return the user mid-edit: backgrounding already leaves journal editing and arms cartography's
> prompt, which is what the app does on a genuine departure and therefore agrees with the timeout
> rather than conflicting with it. Both were requirements in the commissioning dispatch and both
> were struck once the code was read.

Evidence: twelve confirmations, and for 6.1–6.3 and 6.8–6.9 **say roughly how long you were away**,
since that is the variable under test.

## 7. Capture diagnostics, the two-entry invariant

New in v3, and the reason the rest of the run is readable without a debugger. **Corrected in v4:
v3 named the wrong string and the wrong number of labels here.**

### What the two entries actually look like

**Every capture produces exactly two entries in the Diagnostics panel:**

1. **The shot entry**, reading
   `capture shot deviceRotation=… displayRotation=… targetRotation=… requestDegrees=… resolution=…`
   — **compare the first two.** `deviceRotation` is the sensor's snapped reading, `displayRotation`
   the window's; the controls turn by their difference and the shutter's edge follows the second
   alone. At a fresh open they should be equal. **An entry where they differ is a finding in its own
   right** (added 2026-09-18 for the S26 Ultra inverted-glyph regression): record both values and
   which physical side the port was on.
2. **Exactly one outcome entry**, reading `capture orientation '<filename>' <label>` followed by
   whichever of ` fromTag=`, ` toTag=`, ` degrees=`, ` reason=`, ` error=` apply. A failure carries
   its stack as the entry's indented detail.

**`Shot:` will not appear in the panel, and its absence means nothing.** There is a logcat line
beginning `Shot:` carrying the same four values, written alongside the panel entry and deliberately
never into the log the panel reads. This run has no logcat. Do not look for `Shot:`; v3 told you to
count it, which would have found nothing in a perfectly working build and been indistinguishable
from the invariant failing.

### Six paths, five labels, seven rows

| What happened | Label in the entry | What tells it apart |
|---|---|---|
| the capture failed, no file written | `not attempted` | `reason=the capture failed and no file was written`, plus a stack |
| no resolution info for the shot | `not attempted` | `reason=no resolution info for this shot; it keeps the HAL's tag` |
| tag rewritten to the request's | `rewritten` | `fromTag=` and `toTag=` |
| tag was already the request's | `kept` | `fromTag=` |
| the HAL rotated the pixels; CameraX's tag stands | `declined` | `reason=` naming the transposed case. **This is the mechanism working. Not a fault.** |
| orientation couldn't be determined | `declined` | `reason=` naming which: rotation not a right angle, pixel size unreadable, or size matching neither |
| reapply failed | `failed` | `error=` and a stack |

**Seven rows across six paths**, because `declined` is one code path carrying four reasons
(`IntendedOrientation.kt:114`, `:116`, `:135`, `:140`) — one refusal and three indeterminate — and
those two are opposite readings. A runner who files the refusal as a problem is filing a bug against
correct behaviour, which is why the split is in the table rather than in the prose.

**Record the `reason=` text in full, not just the label.** v3 asked for the reason only when the
outcome was `declined`. The reason is load-bearing on three of the six paths: both `not attempted`
paths, which share a label and are separated by nothing else, and `declined`, where it decides
whether you are reading correct behaviour or an indeterminate result. Record it whenever it appears.

### The invariant

Established by reading the seven call sites, not by a test, so this step is its only confirmation.
**If a capture produces one entry rather than two, that is the invariant failing, not a display
quirk.** Count them.

1. Diagnostics: note what is already logged.
2. Take **three** photos, setting off, phone held portrait.
3. Diagnostics: six new entries, three pairs.
4. Take **one** photo with the setting **on**, held landscape.
5. Diagnostics: two more entries. Record the outcome label and its full `reason=` text.

The outcome label is the finding here, either way. `rewritten` confirms the HAL-tag mechanism the
orientation fix was built on. `kept` means the tag was already what the shot asked for.
**`declined` on the transposed case — the HAL rotated the pixels, so CameraX's tag stands — is the
expected-correct reading, the mechanism working as designed; it is not a soft failure and not
something to file.** `declined` on any of the other three reasons is an indeterminate result and a
different finding entirely, which is exactly why the `reason=` text decides which of the two you are
looking at. `failed` is an error, with a stack. `not attempted` means the shot never reached the
reapply at all. Each is worth recording rather than treating as a fault.

Evidence: the entry counts, and the outcome label plus full `reason=` text for the setting-on
capture.

## 8. Multi-shot, capture release and sweep

**Corrected after the swallow fix.** The old 8.4 and 8.5 asked for three unsaved captures. There is
no longer any way to produce one: every capture persists the moment the shutter succeeds. The
sweep's remaining job is crash debris.

1. Open Diagnostics. Note what is listed under `photos` and under `captures`.
2. Open the camera. Take **ten** photos in one go, watching the counter. Save all ten.
3. Open Diagnostics:
   - Counter read 10?
   - `photos` gained exactly ten?
   - **`captures` empty?** It should be empty at every point you look. A file sitting there is
     either a capture caught mid-persist, which is a fraction of a second, or debris from a process
     that died. Neither should be waiting for you.
   - Twenty new capture entries, ten pairs, from step 7?
4. Force stop Forager: Settings, Apps, Forager, Force stop. Not the back button, not swiping it away
   from recents.
5. Open Forager, wait five seconds, open Diagnostics:
   - `captures` empty?
   - The sweep line. **`sweep deleted=0 orphaned capture file(s)` is the expected reading**, and it
     means nothing was left behind.

**A non-zero sweep count is a finding, not a failure.** It means a process died between a shutter
press and its persist. Record the number and what you were doing beforehand.

**Optional, and do not force it.** To see the sweep actually delete something you would have to kill
the app in the fraction of a second between shutter and persist. If you happen to catch it, record
it. Do not spend time trying.

> **Superseding note, 2026-09-17.** The old 8.4–8.5 ("three photos, **do not** persist. Back out." →
> "`captures` holds three") described a state the app could only reach through a **bug**: a capture
> arriving for a find no longer being edited was silently dropped, un-persisted and un-released,
> leaving its scratch file in `captures/`. The step was testing the swallow without knowing it.
> Fixing the swallow (`MushroomLogViewModel.rescueCaptureWithNoEditingEntry`) deletes the step's
> premise, so the step is replaced rather than adjusted. The sweep keeps its two-second mtime guard
> and its behaviour; what narrowed is its job, now genuine crash debris only — a cleaner definition
> than it had, and a consequence of the fix rather than a change made to it.
>
> **No step was added for the swallow path itself.** The commissioning dispatch ruled that it is not
> reachable by normal means and so cannot be produced on purpose, leaving Part 1's unit tests as its
> only evidence. **That premise is disputed by the code**: a sequence exists that a runner could
> perform — edit a find, open the camera, background the app, return inside four minutes, shoot —
> and it is written out on `rescueCaptureWithNoEditingEntry`. It was read from the tree, not
> reproduced on a device. If it holds, this step's companion is a short device step the owner has
> not yet written.

Evidence: the listings at 1, 3 and 5, the dialog count, and the sweep line.

## 9. Main thread

1. Diagnostics: note what is logged.
2. Take one photo and persist it.
3. Diagnostics again.

Pass: no new disk read or write violation naming `FilePhotoStore`, `PhotoMetadataScrub`,
`AddPhotoToLogEntryUseCase` or `AddPhotoToGalleryUseCase`. Framework, startup and the Crash Logs
panel's own violation do not count.

Evidence: the new entries, or a statement that there were none.

## 10. Scrub content, the privacy claim

Formerly v1 step 5. The completion report's own procedure for it is at **§3, "What you will see on
the phone", report line 83** (`2026-09-15-debug-diagnostics-instrument-completion-report.md:83`).
v3 cited §2.4 twice; §2.4 of that report is "The sweep's count, and the panel", which is what steps
7 and 8 exercise, not this step.

Do not sign this off on inference.

**Getting the photo off the device.** Two paths, both confirmed to exist:

- **Panel share.** Take a photo, open Settings, Diagnostics, tap the share icon on the top
  `photos/` row. Send by attachment (Gmail, Drive, Quick Share). **Not** a messenger that
  recompresses, which would rewrite the metadata and invalidate the whole step.
- **adb, on a debug build.** `adb pull` of the private directory does not work on an unrooted
  device; the shell user cannot traverse `/data/data/<pkg>`. The working form needs the filename,
  which the panel is what shows you:

  ```
  adb exec-out run-as com.zynergylabs.forager.app cat files/photos/<name> > out.jpg
  ```

**Then:**

1. `exiftool -a -G1 <file>`, or any viewer showing every tag.
2. Confirm the last two bytes are `FF D9` in a hex viewer.

Pass:
- `Orientation` present.
- **Absent:** every `GPS*`, `MakerNote*`, `ThumbnailImage`, `XMP*`, `IPTC*`, `DateTimeOriginal`,
  `CreateDate`, `ModifyDate`, `Make`, `Model`, `Software`, any serial number tag.
- `ICC_Profile` present on a wide-gamut device; absent on an sRGB device is recorded, not failed.
- Nothing after `FF D9`.

Evidence: the full tag dump, pasted, and the hex tail.

## 11. Colour

Wide gamut only; otherwise record as skipped. One subject, two photos under the same light:
in-app and the stock camera app, compared side by side. A clearly flatter in-app photo points at
a lost ICC profile; re-read step 10's `ICC_Profile` line.

Evidence: one line.

## 12. Trailer, observation rather than a gate

**Corrected in v4. Nothing in this build writes a truncation count, so the answer is known before
the run: "not observable in this build". Do not scan the log for it.** The scrub truncates at the
first EOI and counts nothing it drops; the only three things it logs are a read failure, a non-JPEG
input and a scrub failure. v3 said "if the log records bytes dropped after EOI", which invited a
search through up to a megabyte of log for an entry that cannot exist.

**The question is answered directly by step 10 item 2**, and better than a log line could: if the
file's last two bytes are `FF D9`, nothing follows EOI in what the scrub wrote — read off the real
artifact rather than off the app's own account of it.

Evidence: "not observable in this build", and the hex tail from step 10, which is where this
question is actually settled.

---

## Sign-off

- Steps run; steps not run and why.
- Anything surprising, including on steps that passed.
- Matrix row: model, OS version, 1c result, **both** step 3.3 and step 3.4 results (the two
  landscapes are separate answers), step 5 last row, the step 7 outcome
  label **and its `reason=` text**, `ICC_Profile` present, trailer observed, main thread clean,
  sweep count.
- Separately: confirm the Crash Logs panel's own main-thread disk read, so it keeps its own
  record rather than living in a report sentence.

**Known limitation, not tested here.** On screens 600dp and wider the platform ignores the
orientation request for API 36+ targets, so the window can move on a tablet or unfolded foldable
regardless of the setting. Recorded with its cause; out of scope for this device.

**Verification note.** Steps 3, 4, 7, 8, 9, 10, 12 and the noise list have been read against the
tree (`2026-09-16-device-check-v3-tree-corrections.md`, at `5536f59`) and match it as written here.
Step 7's label table additionally rests on `IntendedOrientation.kt:112-145`, read during the v4 edit
rather than during the `5536f59` check — that reading is what split `declined` into two rows.
Steps 1, 2, 5, 6 and 11 describe behaviour observable only on hardware — what a gallery shows,
whether a viewfinder is live, what "Don't keep activities" does — and nothing in the repository
could confirm or refute them; they are carried from v3 unchecked, which is stated rather than
implied. If a panel label, entry wording or arrangement detail differs from what is written here,
the tree is right and this document is stale. Say so and it gets corrected, as a superseding note
or a v5 rather than a silent edit.
