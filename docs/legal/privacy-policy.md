# Forager — privacy policy

**Last updated: DRAFT, 2026-09-29, not yet published.** Applies to the Forager Android app (package
`com.zynergylabs.forager.app`), including its Google Play closed test.

> **Draft status (remove before publishing).** This draft describes the build that includes the
> Journal redesign (branch `journal-redesign`): Save to Gallery, and journal backup and restore. It
> must not be published before that build reaches testers. Sections marked *true today* already
> describe the app on `main` and could be published sooner. Planner's draft for the owner's
> approval; the reasoning, item by item, is in `docs/audits/2026-09-29-privacy-site-update-report.md`.

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
- Journal / log entries, including any coordinate attached to a find.
- Photos taken in the app or imported from the gallery (`filesDir/photos/`, `FilePhotoStore.kt`).
- Downloaded offline map regions.
- Preferences (units, map mode, and similar).
- Crash traces, written locally when the app crashes (`CrashFileStore.kt`). They stay on the phone
  and are only ever shared if you choose to share one from the app's crash screen.

None of this is transmitted by the app. It is deleted when you uninstall the app or clear its data,
**except for copies you have saved outside the app**, described in the next section.

**Android's own backup is switched off for this app.** The manifest sets
`android:allowBackup="false"` (`app/src/main/AndroidManifest.xml`), which turns off both Android
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
  details of your offline map regions (their name and area, not the map tiles). It does not hold
  your settings. **The backup file is not encrypted**: anyone who has the file can read what is
  in it, so keep it somewhere you trust. If you save it to a cloud folder, that service stores a copy
  under its own terms; Forager does not upload it and has no copy.
- **Scheduled backups** write the same kind of file on a schedule you set (daily, weekly or monthly)
  to a folder you choose. They are **off unless you turn them on**. Each run writes a new file and
  Forager never deletes old ones, so the folder keeps every backup until you remove them.
- **Restore** reads a backup file you pick and either replaces the journal on the phone or merges it
  in. Nothing is fetched from anywhere else.
- **Track export** (*true today*; `TrackExportPanel.kt`, `TrackGpxExporter.kt`). A recorded track
  can be exported as a GPX file through Android's share sheet, to wherever you send it. The file
  contains the track's coordinates and times.
- **Save to Gallery** (`PhotoExporter.kt`). A photo can be saved from the photo viewer into the
  phone's Gallery, in a "Forager" album, on Android 10 and later; on Android 8 and 9 into a folder you
  pick. Saved copies are visible to other apps that can read your photos. What a saved copy carries
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
short time, covering the whole region — that is the download. Once a region is downloaded, viewing
the map inside it makes no tile requests at all. Searching still does.

## The Cloudflare Worker

The map tile endpoint above is operated by the developer of this app, on Cloudflare's platform,
serving tiles out of a Cloudflare R2 bucket.

**We keep no request logs.** The Worker's own code writes no log lines
(`server/pmtiles-worker/src/`), and Workers Logs is explicitly disabled rather than left to the
platform default: `wrangler.toml` sets `[observability] enabled = false`. Verified in the Cloudflare
dashboard on 2026-09-09, where the Worker's Logs tab reported observability disabled.

The setting is pinned deliberately. Cloudflare documents `observability.enabled` as defaulting to
`true` for newly created Workers, so an absent block would have left this claim resting on a default
that can move.

No per-request log is available to us by any other route either. Logpush and Logpull are
Enterprise-only, Workers Trace Events Logpush requires the Workers Paid plan, and the zone-level
`httpRequestsAdaptive` dataset that retains 7 days on the Free plan covers `zynergy-labs.com`, not
`*.workers.dev`. Workers metrics show aggregate request counts, not individual requests.

Cloudflare, as the host, processes these requests under its own privacy policy. **No retention
figure is published here, because no setting of ours produces one.** That is the answer to the
retention question rather than a number, and it is the only accurate one available while Cloudflare
documents no figure for host-level records. This section previously carried an open question asking for a
retention figure, with an instruction not to guess one. The resolution turned out to be that no
figure applies.

Nothing about these requests is linked to any account, because the app has none. Nothing is sold,
shared with anyone else, or used for advertising or profiling.

## Photos and location metadata

Photos stay on the phone unless you save or share one yourself; the app never transmits one. What
happens to the metadata inside a photo file depends on how the photo got into Forager:

- **Photos taken with Forager's camera** (*true today*). Forager has its own camera screen
  (`CameraCapturePhotoSource.kt`). Each photo it takes is stripped of all embedded metadata when it
  is stored — location, camera details, timestamps, thumbnails, and every other tag — and only the
  orientation is put back so the photo displays the right way up (`FilePhotoStore.kt`,
  `PhotoMetadataScrub.kt`). The strip keeps an allowlist rather than removing a list of known tags,
  so a tag nobody thought of is removed too.
- **Photos imported from your gallery** are not stripped by Forager. On Android 10 and later the
  platform removes GPS tags from the copy the app reads, because Forager does not ask for the
  original for that copy; other metadata, such as the camera model and the time, can remain. On
  Android 8 and 9 there is no such removal, and an imported photo's copy can keep its GPS tags.
  Separately, Forager reads an import's original date and location (with the `ACCESS_MEDIA_LOCATION`
  permission) so a find can be dated and placed; that goes into the app's own database, not into
  the photo.
- **Saving to the Gallery.** A saved copy is an exact copy of the photo as Forager stored it. A
  photo taken with Forager's camera therefore has no location in it. An imported photo is saved as
  it was imported: if it carried a location or other details, so does the copy. If you want those
  removed, use a metadata-removal app. Forager adds the photo's time to the Gallery's "date taken"
  field and never writes a location.

A find's coordinate is stored in the app's own database, not read out of a camera capture.

## Permissions and what they are used for

- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`) —
  showing your position, recording a track, navigating back, and the coordinates a species or
  weather lookup searches on. Location is not confined to recording: `AvailabilityViewModel.init`
  subscribes to live fixes at a one-second floor for the ViewModel's lifetime on every tab, and
  `AvailabilityScreen` fires locate-me once per launch, so fixes flow whenever the app is in the
  foreground. When you leave the app, it releases its location subscription rather than relying on
  Android to withhold it.
  `ACCESS_BACKGROUND_LOCATION` is **not** declared (`app/src/main/AndroidManifest.xml`). A recording
  continues with the screen off because it runs as a foreground service with an ongoing
  notification, not because the app holds background location access. Outside those two states,
  the app in the foreground or a recording running in the foreground service, the app receives no
  location at all.
- **Camera** (`CAMERA`, *true today*) — taking a photo for a journal entry with Forager's own camera.
  Photos can also be chosen from your gallery. The photo is stored on your device.
- **`ACCESS_MEDIA_LOCATION`** — reading the capture date and coordinate of a photo you import, so a
  find can be dated and placed. Read separately from the stored copy's bytes.
- **Notifications, vibrate, foreground service** — the off-track alert, the sundown alerts
  (a turnaround warning and one at sunset, while a track is recording), and the recording
  notification. The sundown alerts are computed on the device from the clock and your
  position; nothing is sent anywhere to produce them.
- **Internet** — the requests listed above.
- **Network and Wi-Fi state** (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, *true today*) — added by
  the MapLibre map library to know whether the phone is online (WorkManager, below, also declares
  network state). They read connection state only; nothing is sent because of them.
- **Run at startup and keep awake** (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) — added by Android's
  WorkManager library, which runs scheduled backups. They let a scheduled backup you turned on keep
  its schedule after the phone restarts and finish once started. With scheduled backups off, Forager
  has nothing for them to run.
- **Notifications, for backups** — if a scheduled backup cannot finish, or has to leave some photos
  out, a notification says so; tapping it opens the Backup settings.

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
SHA-256 hash of the connecting address with a counter and a timestamp. That is enough to recognise a
repeat submission within the hour and not enough to recover the address it came from.

The list lives in a Cloudflare D1 database we operate, on Cloudflare's infrastructure and under its
privacy policy as our processor. When you sign up, a notification carrying what you entered is
emailed to Zynergy Labs support through Resend, our mail provider, which processes it in order to
deliver it. Nobody else receives it. It is not sold, not shared and not used for any mailing beyond
the beta.

How long it is kept: until the beta ends or you ask for it to be removed, whichever comes first. We
publish no figure beyond that because there is no automatic expiry that would produce one. To have
your signup removed, email privacy@zynergy-labs.com. Removing it deletes the record; it does not
delete anything on your phone.

*(This section was on the published page and missing from this file; it is copied from
zynergy-site `privacy/index.html` at `0688e4d`, so this file is the source of truth again.)*

## Deleting your data

Everything Forager stores is on your device and can be deleted from inside the app, item by item —
journal entries, photos, recorded tracks, waypoints, offline map regions and planned trips.
Uninstalling removes everything Forager stores in its own storage, including the database, the photo
files and any crash reports; with `allowBackup="false"` there is no Google backup copy to survive
and reappear.

**Files you saved outside the app are not removed by uninstalling**: backup files, exported GPX
tracks, and photos saved to your Gallery. Delete those yourself in your Files or Gallery app, and
check the folder a scheduled backup writes to, since it keeps every backup.

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

Verified as receiving mail on 2026-09-09.
