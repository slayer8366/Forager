# Forager — privacy policy

**Last updated: 2026-09-09.** Applies to the Forager Android app (package `com.forager.app`),
including its Google Play closed test.

This is the document Play's Data safety declaration points at. It is written to match what the code
actually does; where a claim comes from a particular file, that file is named so the claim can be
checked rather than trusted.

## The short version

Forager has no account, no sign-in, no sync and no analytics. Nothing you record — tracks, journal
entries, photos, offline map regions, settings — is uploaded to the developer or to anyone else.

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

None of this is transmitted by the app. It is deleted when you uninstall the app or clear its data.

**Android's own backup is switched off for this app.** The manifest sets
`android:allowBackup="false"` (`app/src/main/AndroidManifest.xml`), which turns off both Android
Auto Backup and the device-to-device transfer that runs during a new phone's setup. Your phone
therefore does not copy Forager's data to your Google account, and does not hand it to another
device on your behalf. This is a permanent decision, not a setting for the closed test: moving your
data belongs to the app rather than to the operating system. The design is an export you trigger
inside Forager, writing a single file that goes only where you send it — a cable, Bluetooth, a
folder or cloud drive you pick — and an import that reads that file and rebuilds tracks, entries,
waypoints and photos from it. Nothing sends that file anywhere on its own.

**That export and import are not built yet**, and this policy will not describe them as if they
were. As of the date at the top of this document, data recorded in Forager stays on the device that
recorded it until you have an app version that can export it.

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

**It logs requests.** The Worker's own code writes no log lines of its own (`server/pmtiles-worker/src/`),
but Cloudflare, as the host, records request metadata for the requests it serves — the requested
URL, which for a tile request is the map square being viewed, together with the IP address that
asked, the time, and the user agent. This is stated plainly because a tile log is a record of where
someone was looking at the map, and that is worth knowing before you use it.

The logs are not linked to any account, because the app has none. They are not sold, not shared
with anyone else, and not used for advertising or profiling.

**TODO — the Worker's actual log retention period is not recorded anywhere in this repository and
has not been determined. Fill in the retention period Cloudflare applies to this account before
this policy is published.** Do not guess a number here.

## Photos and location metadata

Photos stay on the phone. What the app does with the location metadata embedded in a photo file is
narrower than it may sound, and is stated here as it actually is (`FilePhotoStore.kt`):

- Forager does not run an EXIF-stripping step over the photo it stores.
- For a **gallery import** on Android 10 (API 29) and later, the platform itself redacts GPS tags
  from the copy the app reads, because the app does not opt in to the original via
  `MediaStore.setRequireOriginal` for that read. On Android 9 and earlier (the app supports back to
  Android 8.0) there is no such redaction, and an imported photo's copy can keep its embedded GPS
  tags.
- For a **photo taken inside Forager**, the camera app writes into a file in Forager's own directory
  (`CameraCaptureFiles.kt`). That is not a MediaStore read, so the platform redaction above does not
  apply to it; whatever GPS tags the camera app wrote are in the stored file.

This matters only if you export or share a photo yourself — the app never transmits one. A find's
coordinate, when the app records one, is stored in the app's own database, not read out of a camera
capture's EXIF.

## Permissions and what they are used for

- **Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE_LOCATION`) —
  showing your position, recording a track, navigating back, and the coordinates a species or
  weather lookup searches on.
- **Camera** — taking a photo for a journal entry.
- **`ACCESS_MEDIA_LOCATION`** — reading the capture date and coordinate of a photo you import, so a
  find can be dated and placed. Read separately from the stored copy's bytes.
- **Notifications, vibrate, foreground service** — the off-track alert and the recording notification.
- **Internet** — the requests listed above.

## No analytics, no ads, no tracking

The app bundles no analytics, crash-reporting or advertising SDK. There is no telemetry of any
kind: no usage events, no session data, no device fingerprint, no third-party identifier. Nothing
about your use of the app is reported to the developer unless you write it in a message and send it
yourself.

## Children

Forager is not directed at children and collects nothing about anyone, including children, beyond
what is described above.

## Changes

Material changes to this policy will change the date at the top. The current version always lives
at this file's path in the app's public repository.

## Contact

**TODO — contact email address for privacy questions. Play requires one on the Data safety form and
in this policy; it has not been decided and must not be invented here.**
