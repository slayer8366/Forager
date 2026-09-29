# Forager — privacy policy

**Last updated: DRAFT, date left for the owner to set on publishing, not yet published.** Applies to
the Forager Android app (package `com.zynergylabs.forager.app`), including its Google Play closed test.

> **Draft status (remove before publishing).** This draft describes the build that PR #140 ships (the
> Journal redesign, branch `journal-redesign`): track delete, Save to Gallery, and journal backup and
> restore. It must not be published before that build reaches testers. Sections marked *true today*
> already describe the app on `main` and could be published sooner. Every claim was re-read against
> the code at `journal-redesign` `f645e8f9`; the reasoning, item by item, is in
> `docs/audits/2026-09-29-privacy-site-update-report.md` (with its "Addendum after the follow-ups")
> and `docs/audits/2026-09-29-legal-docs-l1-completion-report.md`. For the owner's approval.

This is the document Play's Data safety declaration points at. It is written to match what the code
actually does; where a claim comes from a particular file, that file is named so the claim can be
checked rather than trusted.

## The short version

Forager has no account, no sign-in, no sync and no analytics. Nothing you record — tracks, journal
entries, photos, offline map regions, settings — is uploaded to the developer or to anyone else.

You can choose to save copies outside the app: a backup of your journal, a track as a GPX file, or a
photo into your Gallery. Each of those is a file you place yourself, and Forager never sends one
anywhere on its own. Once saved, a copy is outside Forager's control and outlives uninstalling it.

What is transmitted, while the phone is online, is **where you are looking**: the coordinates a
search runs on, and the map tiles for the area on screen. Those go to iNaturalist, Open-Meteo, the
map tile providers, and a Cloudflare Worker run by the developer. They carry a location and your IP
address, and they are logged by the services that receive them.

Because location leaves the device and reaches third parties, the Play Data safety declaration for
this app states that location is **collected and shared**. That is the accurate description, not a
conservative one.

## What stays on the device

Stored in the app's own private storage, readable by no other app:

- Recorded tracks and their individual GPS points (latitude, longitude, timestamp, elevation,
  accuracy, speed).
- Journal / log entries, including any coordinate attached to a find. An entry also keeps a copy of
  what it noted about each track, waypoint, offline map region, find and photo it lists, and that
  copy stays when the item itself is deleted; see *Deleting your data* below
  (`CartographyEntryEntity.kt`).
- Photos taken in the app or imported from the gallery (`filesDir/photos/`, `FilePhotoStore.kt`).
- Downloaded offline map regions.
- Your last five searches (*true today*): the place searched (latitude, longitude and radius), the month, the
  filter, and the species list iNaturalist returned, so that a search you have run before still
  shows an answer with no signal (`RoomSearchCacheRepository.kt`, `CachedSearchEntity.kt`). A sixth
  search replaces the oldest, and the app has no button to clear the list.
- Preferences (units, map mode, and similar).
- The file made when you share a track as GPX (*true today*): Forager writes it to its own cache
  folder first and hands that copy to the share sheet (`TrackGpxExporter.kt`, `cacheDir/tracks`).
  Nothing in Forager removes it afterwards; it goes when Android or you clear the app's cache, or
  when you uninstall.

None of this is transmitted by the app. It is deleted when you uninstall the app or clear its data,
**except for copies you have saved outside the app**, described in the next section.

**Crash traces are the one exception to "private" (*true today*).** When the app crashes it writes a
plain-text trace (the time, the thread's name, the Android version number and the stack trace) to a
folder of its own inside the phone's shared storage area, `getExternalFilesDir(null)/crashes`, on
purpose so that the phone's file manager can reach it (`CrashFileStore.kt`). It keeps the last ten. A
trace is shared only if you choose to share one from the app's crash screen
(`CrashLogPanel.kt`, `ACTION_SEND`). Android removes that folder when you uninstall.

**Android's own backup is switched off for this app.** The manifest sets
`android:allowBackup="false"` (`app/src/main/AndroidManifest.xml:134`), which turns off both Android
Auto Backup and the device-to-device transfer that runs during a new phone's setup. Your phone
therefore does not copy Forager's data to your Google account, and does not hand it to another
device on your behalf. This is a permanent decision, not a setting for the closed test: moving your
data belongs to the app rather than to the operating system, through the backup described below.

## Copies you save outside the app

Each of these happens only when you ask for it, and each writes an ordinary file that Forager does
not control afterwards. Uninstalling Forager or clearing its data does **not** remove them.

- **Journal backup** (`app/src/main/java/com/zynergylabs/forager/app/data/backup/`). One `.zip`
  file, saved where you choose through Android's file picker — the phone, an SD card, or a cloud
  folder if you pick one. It holds your journal entries and finds with their coordinates, your
  photos, your recorded tracks with every GPS point, your waypoints, your planned trips, and the
  details of your offline map regions (their name and area, not the map tiles). Because it is a
  copy of the app's whole database, it also carries the list of your last five searches described
  above, though a restore does not bring that list back (`RoomJournalBackup.kt`, `takeSnapshot`
  copies the database file; `JournalTables.kt`). It does not hold your settings
  (`BackupArchive.kt`). **The backup file is not encrypted**: nothing in the backup code encrypts
  it, so anyone who has the file can read what is in it; keep it somewhere you trust. If you save
  it to a cloud folder, that service stores a copy under its own terms; Forager does not upload it
  and has no copy.
- **Scheduled backups** write the same kind of file on a schedule you set (daily, weekly or monthly;
  weekly until you change it) into a folder you choose. They are **off unless you turn them on**, and
  turning them on does not run one straight away: the first backup runs after one full interval
  (`ScheduledBackup.kt`, `WorkManagerBackupScheduler`). **Forager keeps the newest five files a
  scheduled backup made and deletes the older ones it made itself.** It knows which files those are
  from a list it keeps in its own storage, so it never deletes a backup you made by hand or any
  other file in that folder (`BackupSchedule.kt`, `RunScheduledBackupUseCase`). That has two limits.
  If the list is lost, because you clear the app's data or reinstall, files made before then are no
  longer known to Forager and stay until you delete them. And if Android refuses a delete, the file
  stays: the failure is logged on the phone and not retried. A backup you start from a failed
  scheduled backup's "Try again" counts as one of the scheduled ones.
- **Restore** reads a backup file you pick and, each time you ask, either replaces the journal on the
  phone or merges it in. It does not add an offline map region the phone already has, meaning one
  with the same name and radius and a centre within one metre (`RegionMatch.kt`). Nothing is fetched
  from anywhere else.
- **Track export** (*true today*; `TrackExportPanel.kt`, `TrackGpxExporter.kt`). A recorded track
  can be exported as a GPX file through Android's share sheet, to wherever you send it. The file
  contains the track's coordinates, times and elevations, the waypoints dropped while it recorded
  (with their names and notes), and every point Forager stored for the track, including the ones
  the app leaves out of the track it shows, each marked as kept or excluded (`GpxCodec.kt`). Forager
  also leaves the file it wrote in its own cache folder, as described above.
- **Save to Gallery** (`PhotoExporter.kt`, `PhotoViewerDialog.kt`). A photo can be saved from the
  photo viewer into the phone's Gallery, in a "Forager" album (`Pictures/Forager`), on Android 10 and
  later; on Android 8 and 9 the control reads "Save to folder" and writes to a place and name you
  choose with Android's file picker. A copy in the Gallery is visible to other apps that can read
  your photos, because Forager adds it to Android's shared photo library. What a saved copy carries
  is described under *Photos and location metadata* below.

## What is transmitted, to whom, and why

Every request below is made only when the app is online and doing the thing that needs it. None of
them carries a user identifier, an advertising ID, a device ID, or an account — the app has none to
send.

| Recipient | What is sent | Why |
|---|---|---|
| iNaturalist (`api.inaturalist.org`) | Latitude and longitude of the searched area, a radius, a month, taxon filters (`INaturalistApi.kt`) | Which species have been observed near that place at that time of year |
| Open-Meteo (`api.open-meteo.com`) | Latitude and longitude, forecast/past day counts (`OpenMeteoApi.kt`) | Rain, soil moisture and soil temperature for the trip window |
| Open-Meteo archive (`archive-api.open-meteo.com`) | Latitude and longitude, a date range (`OpenMeteoArchiveApi.kt`) | Historical rainfall, for the fruiting-lag calculation |
| OpenStreetMap (`tile.openstreetmap.org`), OpenTopoMap (`a.tile.opentopomap.org`), USGS (`basemap.nationalmap.gov`) | Map tile coordinates — `z/x/y`, i.e. the map area being viewed (`Basemap.kt`) | Drawing the map |
| MapLibre demo tiles (`demotiles.maplibre.org`) | Font glyph ranges for map labels (`BasemapStyles.kt`) | Map label rendering |
| `forager-pmtiles.brandonlee1-894.workers.dev` — a Cloudflare Worker operated by the developer | The offline map style, and vector map tiles by `z/x/y` (`OfflineStyle.kt`, `server/pmtiles-worker/`) | The offline basemap, and downloading an offline region |

As with any HTTP request, each of these also reveals your IP address and ordinary request metadata
to the recipient. Each recipient's own privacy policy governs what it then does with that.

One thing the app does not request itself: tapping an observation to open it on iNaturalist hands a
`https://www.inaturalist.org/observations/<id>` link to your browser or to the iNaturalist app
(`AvailabilityPureFunctions.kt:198`). From that point it is an ordinary web visit made by that other
app, under its own terms, not a request Forager makes.

Downloading an offline region issues a large number of tile requests to the Cloudflare Worker in a
short time, covering the whole region — that is the download (`OfflineStyle.kt`,
`MapLibreOfflineMapRepository.kt`). Searching sends its lookups as described in the table above,
whether or not a region has been downloaded.

## The Cloudflare Worker

The map tile endpoint above is operated by the developer of this app, on Cloudflare's platform,
serving tiles out of a Cloudflare R2 bucket.

**We keep no request logs.** The Worker's own code writes no log lines
(`server/pmtiles-worker/src/index.ts` and `shared.ts` contain no logging call), and Workers Logs is
explicitly disabled rather than left to the platform default: `wrangler.toml` sets
`[observability] enabled = false`.

The setting is pinned deliberately, and `wrangler.toml` says why: invocation logs record the request
URL, and this Worker's URLs are tile coordinates, so a log there would be a record of which areas
people looked at.

Cloudflare, as the host, processes these requests under its own privacy policy. **No retention
figure is published here, because no setting of ours produces one**: `wrangler.toml` configures no
log retention.

One onward request (*true of the Worker's code in this repository today*): for map detail beyond
the zoom range of the Worker's own archive, the Worker itself asks Protomaps' public build server
(`build.protomaps.com`) for the tile, and keeps a copy in its own bucket so the next request for
that tile need not ask again (`server/pmtiles-worker/src/index.ts`). That request is the Worker's,
not your phone's, and it names a tile address.

Nothing about these requests is linked to any account, because the app has none. Nothing is sold or
used for advertising or profiling.

## Photos and location metadata

Photos stay on the phone unless you save or share one yourself; the app never transmits one. What
happens to the metadata inside a photo file depends on how the photo got into Forager:

- **Photos taken with Forager's camera** (*true today*). Forager has its own camera screen
  (`CameraCapturePhotoSource.kt`, `CameraXCaptureSession.kt`), and it never attaches a location to a
  capture. Each photo it takes is stripped of embedded metadata when it is stored — location, camera
  details, timestamps, thumbnails, and every other tag — and what is kept is only what the picture
  needs to display correctly: its orientation, its colour profile and two small technical headers
  (pixel density and colour transform) (`FilePhotoStore.kt`, `PhotoMetadataScrub.kt`,
  `isKeptSegment`). The strip keeps an allowlist rather than removing a list of known tags, so a tag
  nobody thought of is removed too. If the strip cannot complete, the photo is stored as it was and
  the failure is logged on the phone, rather than losing the photo (`PhotoMetadataScrub.kt`).
- **Photos imported from your gallery** are not stripped by Forager: it stores the file exactly as
  Android hands it over (`FilePhotoStore.kt`). On Android 10 and later Android normally withholds a
  photo's GPS tags from an app that has not asked for the original, and Forager does not ask for the
  original for that copy, so the stored copy normally has none; other metadata, such as the camera
  model and the time, can remain. On Android 8 and 9 there is no such step, and an imported photo's
  copy can keep its GPS tags. Separately, on Android 10 and later Forager reads an import's original
  date and location (with the `ACCESS_MEDIA_LOCATION` permission) so a find can be dated and placed;
  that goes into the app's own database, not into the photo.
- **Saving to the Gallery.** A saved copy is an exact copy of the photo as Forager stored it
  (`PhotoExporter.kt`: the stored file's bytes, unchanged). A photo taken with Forager's camera
  therefore has no location in it. An imported photo is saved as it was imported: if it carried a
  location or other details, so does the copy. Our position, in the owner's words: "Imported photos
  taken outside the app are not within our scope. They can use a scrubbing app to remove it if they
  want it removed. All photos taken inside the app are scrubbed either way and that's our scope".
  Forager writes the time it has on record for the photo as the copy's "date taken" — for a photo from
  Forager's camera, when it was taken; for an import, the capture time read from the file when
  Android allows it, otherwise the moment it was imported — and only when the app has one; it never
  writes a location (`PhotoExporter.kt`, `FilePhotoStore.kt`). The copy's file name carries the same
  time.

A find's coordinate is stored in the app's own database, not read out of a camera capture.

## Permissions and what they are used for

This list was checked against the merged manifest of the build (the app's own
`app/src/main/AndroidManifest.xml` together with what its libraries add), built with
`:app:processDebugMainManifest`. That manifest declares exactly these permissions: `INTERNET`,
`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`, `ACCESS_MEDIA_LOCATION`,
`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `VIBRATE`,
`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, and one
permission internal to the app, added by a support library and held by no other app
(`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`).

- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`) —
  showing your position, recording a track, navigating back, and the coordinates a species or
  weather lookup searches on. Location is not confined to recording: while the app is on screen it
  listens for your position on every tab, at most once a second (`AvailabilityViewModel.kt`,
  `onEnteredForeground`; `AndroidLocationTracker.kt`, `MIN_UPDATE_INTERVAL_MILLIS`), and on a
  phone-sized window it asks for a fix once as the Maps screen opens
  (`AvailabilityCompactScaffold.kt`). When you leave the app it releases that subscription rather
  than relying on Android to withhold it (`onLeftForeground`).
  `ACCESS_BACKGROUND_LOCATION` is **not** declared (`app/src/main/AndroidManifest.xml`). A recording
  continues with the screen off because it runs as a foreground service with an ongoing
  notification, not because the app holds background location access. Outside those two states,
  the app in the foreground or a recording running in the foreground service, the app receives no
  location at all.
- **Camera** (`CAMERA`, *true today*) — taking a photo for a journal entry with Forager's own camera.
  Photos can also be chosen from your gallery, which needs no permission. The photo is stored on
  your device.
- **`ACCESS_MEDIA_LOCATION`** — reading the capture date and coordinate of a photo you import, so a
  find can be dated and placed. Read separately from the stored copy's bytes, and asked for when you
  start an import, not at launch, within Android's own limits on repeat requests
  (`PhotoAcquisitionLaunchers.kt`).
- **Notifications, vibrate, foreground service** (`POST_NOTIFICATIONS`, `VIBRATE`,
  `FOREGROUND_SERVICE`) — the off-track alert (a notification and a vibration, only while you are
  navigating back), the ongoing recording notification, and the backup notifications below.
  Nothing is sent anywhere to produce any of them. On Android 13 and later Forager asks for the
  notification permission when you start a recording (`MainActivity.kt`), and once when you first
  turn scheduled backups on.
- **Notifications, for backups** — in a channel named "Backups" (`AndroidBackupNotifier.kt`). A
  scheduled backup posts a notification only when it could not finish ("Scheduled backup didn't
  finish", with a "Try again" button) or when it saved but had to leave some photos out ("Scheduled
  backup saved. N photos couldn't be backed up."). A backup that goes cleanly posts nothing
  (`ScheduledBackupNotice.kt`). Tapping a notification opens the Backup settings. **The permission
  is asked once** (on Android 13 and later, where notifications need permission): the first time you
  turn scheduled backups on with a folder chosen, and never again for backups
  (`BackupViewModel.kt`, `askNotificationPermissionOnce`; `BackupSection.kt`). If
  you decline, or notifications are off, the schedule still runs; when a scheduled backup then has
  one of those two things to report, the same words are shown in the app, once, the next time you
  open it.
- **Internet** — the requests listed above.
- **Network and Wi-Fi state** (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, *true today*) — added by
  the MapLibre map library (version 13.5.0, which `main` also uses) to know whether the phone is
  online; other bundled libraries, including WorkManager below, also declare network state. They
  read connection state only; nothing is sent because of them.
- **Run at startup and keep awake** (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) — declared by Android's
  WorkManager library (`androidx.work` 2.12.0), not by Forager's own manifest, and not used by
  Forager's own code. Forager uses WorkManager only to run scheduled backups and a "Try again"
  backup (`ScheduledBackup.kt`, `AndroidBackupNotifier.kt`). With scheduled backups off, Forager has
  no scheduled job (`WorkManagerBackupScheduler.apply` cancels it).

## No analytics, no ads, no tracking

The app bundles no analytics, crash-reporting or advertising SDK. There is no telemetry of any
kind: no usage events, no session data, no device fingerprint, no third-party identifier. Nothing
about your use of the app is reported to the developer unless you write it in a message and send it
yourself.

## Children

Forager is not directed at children and collects nothing about anyone, including children, beyond
what is described above.

## The beta signup list

Separate from the app, and the one place Zynergy Labs holds personal data about you: if you fill in
the form at <https://zynergy-labs.com/beta-signup>, what you type is stored. This is website data, not
app data. The app never sees it, and it is outside the scope of the Play Data safety declaration,
which covers the app.

What is stored:

- Your email address, both as you typed it and lower-cased, the second so that one person signing
  up twice is recognised as one person.
- Optionally, a name, a device type, an Android version, and whatever you write in the free-text
  box. All four can be left empty.
- A two-letter country code, derived from your connection by Cloudflare rather than asked for, and
  the date and time you signed up.
- Whether the notification to us about your signup succeeded, and the error if it did not.

Your IP address is not stored. To stop the form being flooded, a separate table keeps a salted
SHA-256 hash of the connecting address with a counter and a timestamp, which is used to recognise a
repeat submission within the hour.

The list lives in a Cloudflare D1 database we operate, on Cloudflare's infrastructure and under its
privacy policy as our processor. When you sign up, a notification carrying what you entered is
emailed to Zynergy Labs support through Resend, our mail provider, which processes it in order to
deliver it. Nobody else receives it. It is not sold, not shared and not used for any mailing beyond
the beta.

How long it is kept: until the beta ends or you ask for it to be removed, whichever comes first. We
publish no figure beyond that because there is no automatic expiry that would produce one. To have
your signup removed, email privacy@zynergy-labs.com. Removing it deletes the record; it does not
delete anything on your phone.

(The facts in this section are those of the site's own code, in the zynergy-site repository:
`functions/api/beta-signup.js` and `db/schema.sql`, read at `0688e4d`.)

## Deleting your data

Almost everything you create in Forager can be deleted from inside the app, item by item — journal
entries, finds, photos, recorded tracks, waypoints, offline map regions and planned trips. A recorded
track is deleted with a swipe on its row in Records, or with Delete on its details, and each has an
Undo; a track that is still recording cannot be deleted (`RecordsLogbookList.kt`,
`RecordDetailsSheet.kt`, `TrackRecordingViewModel.kt`). Two things have no delete button: the list of
your last five searches, which a newer search replaces, and the cache copy of an exported GPX file,
both described above.

**Deleting an item does not always remove every copy of it.** A journal entry keeps a copy of what it
noted about each track, waypoint, offline map region, find and photo it lists, and that copy stays
when the item is deleted. Deleting a track, waypoint or offline map region does not remove the copy a
journal entry kept; delete the entry to remove it. What an entry keeps, by kind
(`CartographyEntryEntity.kt`):

- a **waypoint**: its name and its position;
- an **offline map region**: its name, the position of its centre and its radius;
- a **track**: its name, distance, duration and number of points; and, once the track is deleted, its
  **path** — the latitude and longitude of each point, in order, without times — saved into every
  journal entry that has recorded a decision about the track, including an entry where you chose to
  leave it out (`DeleteTrackUseCase.kt`, `CartographyEntryDao.kt`);
- a **find**: its date and your own identification of it;
- a **photo**: the date you attached it.

The copies go when you delete the entry itself, and only then. They are also in the backups you make
(`JournalTables.kt`).

Uninstalling removes everything Forager stores in its own storage, including the database, the photo
files and any crash reports; with `allowBackup="false"` there is no Google backup copy to survive
and reappear.

**Files you saved outside the app are not removed by uninstalling**: backup files, exported GPX
tracks, and photos saved to your Gallery. Delete those yourself in your Files or Gallery app. In the
folder a scheduled backup writes to, Forager keeps the newest five files it made and deletes older
ones it made itself; anything else there is yours to remove, including your own backups and any
scheduled backup that Forager no longer knows about or could not delete.

Nothing is held on a server, so for the app's own data there is no deletion request to make. The one
exception is the beta signup list above. Full detail is at <https://www.zynergy-labs.com/delete-data/>,
generated from `docs/legal/delete-data.md`, and that page is the URL given in the Play Data safety
declaration.

## Changes

Material changes to this policy will change the date at the top.

This file is the source of truth: it carries the citations back to the code. The user-facing version
published for the Play listing is generated from it and lives at <https://zynergy-labs.com/privacy>.
If the two ever disagree, this file is correct and the page needs regenerating.

## Contact

privacy@zynergy-labs.com
