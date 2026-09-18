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

## 3. Window behaviour, setting OFF

The window pins to whatever it already was when the camera opened, and never moves after.

1. App in **portrait**, open the camera: portrait frame, shutter at the bottom centre, Done
   top-left. **No flip animation.**
2. Rotate the phone both ways: **nothing in the layout moves.** Controls turn in place to stay
   readable.
3. App in **landscape with the charger port on your right**, open the camera: landscape frame,
   **shutter on the port edge** — the screen's right in this grip — vertically centred, count
   beside it (inboard, away from the edge), Done top-left. No flip animation. At open, Done and the
   count read upright, because the phone and the window agree.
4. **App in the other landscape, port on your left**, open the camera: the mirror of 3.3 — shutter
   on the screen's **left**, count inboard to its right, Done still top-left. No flip animation.
5. Rotate from either of those: **nothing in the layout moves, and Done and the count turn in place
   to stay readable** — the same rule as 3.2. A label that stays sideways after the phone turns is a
   failure, in this arrangement exactly as in portrait.
6. Done, then rotate the phone: the screen underneath follows again, proving the lock released.
7. **Status bar hidden, navigation bar kept.** With the camera open in each of 3.1, 3.3, 3.4 and
   inverted portrait: **no status bar**; the navigation bar (gesture handle or buttons) is still
   there. Done sits nearer the top edge than it did before 2026-09-18, and the landscape shutter on
   the screen's true vertical middle; both are the intended use of the freed space, not a failure.
   The shutter is still on the port edge (3.3, 3.4), which is the part that must not change.
8. **The bar comes back on every exit.** Done: back. Back (gesture or button) from the camera:
   back. After a long absence (6.2), once the camera has closed: back.
9. **It can still be pulled down.** Camera open, swipe down from the screen's top edge (the top as
   the screen is currently laid out): the bar shows briefly and the notification shade pulls down.
   **Say whether that took one swipe or two.** The controls do not move while the bar shows.

Any flip animation on open in this state is a failure. The shutter along the bottom of a
landscape frame is a failure. **The shutter on the punch-hole edge is a failure.**

> **Superseding note, 2026-09-17.** Items 3.3 and 3.4 above replace a single item that read
> "shutter on the **right edge**". That wording was true of one landscape and false of the other,
> and **as written it would have passed the broken branch** — it named a screen side where the rule
> is a physical edge. The rule: the shutter belongs on the device's **charger-port edge**, defined
> with the screen facing you, port down, punch-hole up. Rotating the phone never moves that edge
> physically, only in screen coordinates, and the reason is motor habit — a user learns where the
> shutter is *on the phone*. The original item also tested only one of the two landscapes, which is
> why the defect reached a device: the first run that opened in the other one found the shutter
> under the punch-hole cut-out. Fixed in `139727a`; the run that found it is recorded in
> `2026-09-17-shutter-port-edge-and-camera-retention.md`.
>
> **Superseding note, 2026-09-18.** Item 3.3 previously required "**Done and the count upright, not
> turned**" and treated a turned label as a failure. That clause had no source: the owner stated
> one rule for every orientation — controls rotate in place as the phone turns, so their text reads
> in the current hold — and the landscape exception was written into the spec by the planner. The
> landscape arrangement was built to it, and on an S26 Ultra it left Done and the count sideways as
> soon as the phone turned. Fixed by applying the portrait arrangement's own `rotateWithDevice` in
> landscape — one mechanism, not a second one. At open the angle is zero, so the labels still read
> upright the moment the camera opens; the difference is only once the phone moves, which is what
> 3.5 now checks. The shutter is a disc, has nothing to turn, and does not move.

> **Addition, 2026-09-18.** Items 3.7–3.9 are new: the status bar is hidden while the camera is
> open (hide-status-bar dispatch and addendum), and the controls take the space it leaves. Nothing
> above changes. 3.1–3.6 were read against the change and still hold as written; "vertically
> centred" in 3.3 now means the full screen's middle, where before it meant the middle of the area
> left between the status bar and the bottom inset. **Not tested anywhere but here:** Robolectric
> draws no status bar and reports zero insets, so the suite can show only that the bar is
> *requested* hidden on the camera's own window, not that it disappears or where the controls land.

Evidence: one line per item, and **say which physical edge the port was on** for 3.3 and 3.4, not
just which screen side the shutter appeared on.

## 4. Window behaviour, setting ON

"Lock camera to portrait" on. The window is forced portrait always.

1. App in landscape, open the camera: **one flip to portrait is expected and accepted**, then the
   portrait arrangement with the shutter at the bottom.
2. Rotate the phone: nothing moves; controls turn in place.
3. Done: the lock releases as in 3.6.

Evidence: one line per item.

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
8. **Short absence over a find, compact layout.** Edit a find, open the camera from it, background,
   return **within four minutes**, press the shutter: the photo **attaches to that find**. No
   "saved to the album" toast, nothing new in the album.
9. **Long absence over a find, compact layout.** The same, but return after **over four minutes**:
   the camera is **closed**, and the **find's edit form is still open** with its content intact.
   **Nothing else will ever check this path.** The four-minute close is driven by `MainActivity`,
   which the test suite does not run, so 6.9 is not confirming something the tests already cover:
   if it fails here, the tests did not catch it and could not have. Give a borderline result a
   second look, and report it as borderline, instead of assuming the suite has it covered.
10. **No camera, compact layout.** Edit a find, background **without** opening the camera, return:
    unchanged from before this change. The edit form has closed, as it did before 6.8–6.9 existed.
11. **Wide layout agrees.** In a window the app lays out wide (drawer on the left, no bottom nav;
    landscape on most phones), repeat 6.8 and 6.9: **the same results**. A confirmation that the two
    layouts now agree, not a regression check: the wide layout never ended an edit on backgrounding.

A photo arriving on the wrong record is a quiet failure no other step catches.

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
> were struck once the code was read. *(The "mid-edit" sentence is superseded for the journal by
> the 2026-09-18 note below; the rest of this note stands.)*

> **Superseding note, 2026-09-18.** The sentence above, "Nor does it return the user mid-edit:
> backgrounding already leaves journal editing", is **no longer true for a find**, and it was only
> ever partly true. Two findings removed its premises:
>
> - **It held on the compact layout only.** The `ON_STOP` observer that ends a find edit lives in
>   `compactMainScaffold` (`AvailabilityScreen.kt`); wide windows have no backgrounding hook that
>   ends an edit, so there a user was already returned mid-edit after any absence
>   (camera-open edit guard dispatch, verify-first item 2).
> - **"Leaves journal editing" was read as protecting the user's work, and it does not.** The
>   find's content is on disk from per-keystroke saves before `ON_STOP` runs;
>   `onLeaveEditingIncidentally` writes nothing and only closes the form (creation-snapshot pulse,
>   2026-09-17).
>
> The owner then ruled that backgrounding does not end a find edit while the in-app camera is open
> (`AvailabilityScreen`'s `latestInAppCameraOpen` guard; reasoning there and on `CameraAbsence.kt`).
> This fixes the short absence (the shutter used to fire over a find that was gone and the photo was
> rescued to the album) and, because `ON_STOP` cannot know how long the absence will be, also means
> a long absence returns to the still-open edit form. That is deliberate: it makes compact agree
> with wide. **Cartography is unchanged**: its backgrounded-while-dirty prompt still arms.
> Items 6.8–6.11 are new and test exactly this; 6.1–6.7 are unchanged. 6.2 still holds as written
> (the camera is closed after four minutes); 6.9 adds what lies beneath it on a find.

Evidence: eleven confirmations (seven before 2026-09-18, see the note above), and for 6.1–6.3 and
6.8–6.9 **say roughly how long you were away**, since that is the variable under test. For 6.11
say which window you used and confirm the bottom nav was absent.

## 7. Capture diagnostics, the two-entry invariant

New in v3, and the reason the rest of the run is readable without a debugger. **Corrected in v4:
v3 named the wrong string and the wrong number of labels here.**

### What the two entries actually look like

**Every capture produces exactly two entries in the Diagnostics panel:**

1. **The shot entry**, reading
   `capture shot deviceRotation=… targetRotation=… requestDegrees=… resolution=…`
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
