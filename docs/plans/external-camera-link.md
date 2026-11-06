# External camera link (OM System OM-1 first)

**Status: plan only. Nothing built, nothing dispatched.** The owner, 2026-10-12: "Do not
build, just plan for now." Written against `main` at `497423d6`; every claim about the tree
below is a premise to re-check against the base a dispatch is cut from.

## What it is for

A forager shooting on a dedicated camera, like an OM System OM-1, gets each photo into
Forager's album shortly after the shutter fires, with no manual transfer. Each photo gets its
location and is metadata-scrubbed the same way the in-app camera's photos are.

## Decisions the owner has made (2026-10-12, in the planning conversation)

1. **Scrubbing is allowed for these photos.** Verbatim: "Taking a photo and importing it
   directly to the app is permissible to offer to scrub the data." This narrows the earlier
   ruling that photos imported from outside the app stay untouched
   (`app/src/main/java/com/zynergylabs/forager/app/photo/FilePhotoStore.kt:126-133`). The
   narrowing applies only to photos arriving over the camera link. Gallery imports stay
   untouched.
2. **Location comes from the camera's own stamp, read before scrubbing.** Verbatim: "C, and then
   scrub it. Read the data and use it for the GPS addition."
3. **Both JPEG and RAW.** Verbatim: "both".
4. **The full camera link**, not "transfer with OM's app, then import". Verbatim: "full camera
   link".

## Still open, owner's call (nothing below is settled)

- **D1. "Offer" to scrub: ask for each photo, or scrub automatically with an off switch in
  Settings?** The planner recommends automatic with an off switch. It matches the in-app camera
  and asks nothing of the user mid-walk ("Don't make me think", CLAUDE.md UX defaults).
- **D2. Fallback when the camera's stamp is missing.** The OM-1 has no GPS of its own. Its stamp
  comes from a phone over Bluetooth, or from a phone track applied afterwards (OM System's
  manual; see Sources). Photos will therefore sometimes arrive without one. Proposed order:
  camera stamp → match the shot time against the walk the phone was recording → a phone fix when
  the photo arrives. Each step is logged when it fires (CLAUDE.md: no unlogged fallback). This
  follows the owner's location principle that every failure has a fallback (RECORD -519).
- **D3. What feeds the camera its location?** Either OM System's own app runs alongside Forager,
  or Forager sends the location to the camera itself over Bluetooth. Unverified: whether the two
  apps can share the camera, and whether the Bluetooth location message has been publicly worked
  out at all. Stage 0 answers both.
- **D4. How RAW shows up.** Android has no built-in decoder for OM's RAW (`.ORF`) files, and
  the album decodes images through the platform (`ui/log/DecodedPhoto.kt`). Options: (a) shoot
  RAW+JPEG; the album shows the JPEG and keeps the RAW alongside it for export; (b) teach the
  album to display RAW. The planner recommends (a). (b) is a much larger job.
- **D5. What "scrubbed" means for a RAW file.** *Inferred, not verified:* RAW converters rely on
  the camera maker's private metadata block (white balance, lens data, the processing settings)
  to develop the file. That same block also carries the camera's serial number. The JPEG scrub's
  approach, keeping only what is on a short list, would strip that block whole and could leave a
  RAW file that develops wrongly in desktop software. A RAW scrub probably has to keep the
  development data and remove the identifying items one by one. That is the weaker, denylist
  shape the JPEG scrub's own doc comment argues against
  (`photo/PhotoMetadataScrub.kt:12-20`). The owner chooses between: keep the development data
  and remove named identifiers; strip everything and accept a RAW that may develop wrongly; or
  keep RAW files unscrubbed and say so in the app.
- **D6. Say where a location came from?** Showing "location from camera / from your walk / from
  the phone at transfer" means a new stored field. A field is only added together with something
  that reads it (CLAUDE.md, Room/DataStore pitfall), so this is a yes/no on that display.

## What exists today (read on `497423d6`)

- **Two photo sources, both closed to new kinds by intent.** `PhotoSource`
  (`domain/model/PhotoSource.kt`) is the marker interface. `FilePhotoStore.persist` branches on
  `CameraCapturePhotoSource` and `GalleryImportPhotoSource` and errors on anything else
  (`FilePhotoStore.kt:116-117`). An external-camera photo is a third kind and gets its own
  implementation, not a flag on either existing one (CLAUDE.md: new capability is a new path).
- **The scrub is JPEG-only and fails open.** It leaves a non-JPEG alone, and on any parse
  failure it keeps the original and logs a warning (`photo/PhotoMetadataScrub.kt`, doc
  comment). Today it runs for captures only (`FilePhotoStore.kt:133`).
- **Location reading exists for imports.** `readExifData` reads coordinate and capture time
  through a stream separate from the byte copy, so the coordinate lands in `LogPhoto` and not
  in the file (`FilePhotoStore.kt:158-188`). The external-camera path wants the same pattern,
  with one difference: it reads the file Forager itself downloaded, so the platform's
  MediaStore location redaction is not involved.
- **Photos into the album:** `AddPhotoToGalleryUseCase` persists then adds
  (`domain/AddPhotoToGalleryUseCase.kt:23-27`). `LogPhoto` already has `latitude`, `longitude`
  and `createdAtEpochMillis` (`domain/model/LogPhoto.kt:30-36`), so D2's result fits without a
  schema change. D6 would need one.
- **Track points carry timestamps** (`data/local/TrackDao.kt:52`, ordered by
  `timestampEpochMillis`), but there is no query by time range across tracks. The walk-matching
  fallback in D2 needs one.
- **No radio code today.** Nothing in `app/src/main` uses Bluetooth or joins a Wi-Fi network.
  The only manifest mention is a comment about backups (`AndroidManifest.xml:105`). The one
  foreground service is type `location` (`AndroidManifest.xml:241`).
- **Platform floor:** `minSdk = 26`, `targetSdk = 37` (`app/build.gradle.kts:248-249`). The
  modern API for joining a specific Wi-Fi network only exists from Android 10 (API 29). On 8–9
  the feature should report itself as unsupported rather than half-work (CLAUDE.md: unsupported
  is said explicitly).

## What is known about the camera (outside sources, not verified on hardware)

- OM System publishes no developer kit for the OM-1. Its phone app talks to the camera over an
  HTTP interface on the camera's own Wi-Fi network. Hobbyists have documented that interface for
  earlier Olympus bodies: file list, thumbnails, downloads (0l.de, see Sources). **Whether the
  OM-1 matches those notes is unverified.** Because the interface is unofficial, a firmware
  update could change it without notice. The feature has to fail visibly, saying the camera
  isn't responding as expected, rather than silently stop delivering photos.
- Photos travel over Wi-Fi. Bluetooth is used for pairing, waking the camera and passing
  location. The camera does not push new files: the phone polls the file list and pulls new ones.
  "Immediately" in practice means a few seconds after the shot, while the phone is on the
  camera's network.

## Stages

Each stage is a separate dispatch, cut from the then-current `main`.

**Stage 0: hardware pulse, read-only, on a real OM-1.** No app code. A small script or test
harness on a laptop or phone joined to the camera's Wi-Fi. It answers:
- Does the OM-1 serve the documented file list and download endpoints? Record the exact
  requests and responses.
- How quickly does a new shot appear in the list?
- What arrives in RAW+JPEG mode: two files, and are they named so they can be paired?
- Where is the location stamp in each file? Is it a standard GPS block in both, or somewhere
  maker-specific in the RAW?
- D3: can a second app hold the Wi-Fi link while OM System's app holds Bluetooth? Has anyone
  published the Bluetooth location message?
- D5: which RAW metadata do two common desktop RAW converters need to develop the file
  correctly?

This stage needs the owner's camera, so the owner says when it can run. Its report closes D3
and D5's factual half, and may reopen decisions 2 and 3 above if the camera can't do what they
assume.

**Stage 1: the camera behind an interface Forager owns.** A domain interface, roughly: connect,
a stream of "new photo available", download one, disconnect, and an explicit "unsupported /
camera not responding" state. One OM implementation behind it, plus a fake for tests (CLAUDE.md:
external integrations behind an owned interface). Covered by unit tests against recorded Stage 0
responses. No UI.

**Stage 2: the third photo kind, with location then scrub.** A new photo source for "downloaded
from an external camera". Persisting it reads the stamp and the capture time first, stores them
on the photo record, then scrubs the file. Tests assert on the stored coordinate and on the
scrubbed file's actual bytes, the way `PhotoMetadataScrubTest` already does, including the case
where the stamp is missing.

**Stage 3: the location fallbacks (D2).** A track-points-by-time query and the walk-matching
step, then the phone-fix step. Each logged when it fires. Camera clock drift is a real risk for
walk matching. Stage 0 should record how far the camera's clock is from the phone's, and whether
the interface can set it.

**Stage 4: RAW (D4, D5).** RAW+JPEG pairing and the RAW scrub, in whichever shape the owner
picks. Not started until D5 is answered from Stage 0's evidence.

**Stage 5: connecting, on screen.** Pairing and connect UI; the Android permissions (Bluetooth
and nearby-Wi-Fi on newer Android versions, granted when the user first connects a camera);
joining the camera's network; a foreground service so transfers keep running with the screen off
on a walk; and the D1 behaviour. Placement and wording go through `prior-art-before-interface`
and the field-instrument rules before anything is drawn. The likely convention to start from is
OM System's own app and other camera-maker apps.

**Device checks.** Every stage from 1 on ends with a device-check list on the S22 Ultra and the
owner's OM-1 (`device-check-authoring`). Robolectric cannot exercise Wi-Fi joining, Bluetooth or
the camera. A green suite says nothing about the link itself.

## Rejected, and why

- **Transfer with OM System's app, then import into Forager, with a scrub added to that import.**
  Far smaller and could ship sooner. The owner chose the full link instead (decision 4).
- **Take a phone fix when the photo arrives, as the primary location.** Simple, but wrong
  whenever the walker has moved on since the shot. Kept only as the last fallback (D2).
- **A flag on the existing capture or import source instead of a third kind.** Both existing
  paths encode a different answer to "where did these bytes come from", and `persist` branches
  on that answer. A third answer is a third type.

## Sources

- Olympus OM-D Wi-Fi protocol notes: https://0l.de/projects/omd/ and https://dev.0l.de/docs/projects/omd/
- OM System, OM Image Share: https://software.omsystem.com/oishare/en/
- OM-1 Mark II manual, transferring images to a smartphone: https://learning.omsystem.com/OM-1MarkII/zz_html_manual/en/transferring_images_smartphone_277.html
