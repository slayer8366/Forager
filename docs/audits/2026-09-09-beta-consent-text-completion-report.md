# Completion report: the beta consent text, and a privacy policy for Play

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited, and
> nothing else in the file was touched.

Dispatch B0. **Documentation only: no Kotlin, no tests, no build files touched.** The subject is the
text a beta tester reads before opting in, which was inaccurate in two independent ways, plus the
privacy policy Play's Data safety declaration needs a URL for.

**Base:** `origin/main` at `699efa3` (the PR #79 docs consolidation merge), confirmed by
`git ls-remote` against the remote — the container clone's `origin/main` and the remote's `main` are
the same commit. Branch `claude/beta-consent-text` cut from it.

**Suite:** not run and not affected. Nothing here is compiled or executed; the last recorded figure
(1295 tests, in `2026-09-08-path-home-monotonicity-amendment-completion-report.md`) is reported as
found, not re-derived.

**`docs/audits/README.md` deliberately not edited** — the planner is batching index rows to avoid
the serialization point CLAUDE.md names. The row this report would have added is at the end of this
document.

## The two problems

**1. "It sends nothing anywhere" was false.** `docs/beta/README.md` said it once and
`docs/beta/trip-report.md` and `docs/beta/device-report.md` repeated it in the block a tester reads.
The same sentence in the README also claimed the app "strips location metadata from stored photos",
which it does not do itself. This is consent text, so a false reassurance in it is worse than the
same sentence anywhere else in the repository.

**2. "Nobody is asked to ... create an account" was false as of the closed-testing ruling.** The
beta is Play closed testing only, no sideloading. Play closed testing requires a Google account per
tester, presence on the tester list, and an active click on an opt-in link — and the docs contained
no instruction for the one action a tester must actually perform.

## What was verified, and how

Every claim below was read in the source on this branch's base commit, not inferred from a comment.

- **Coordinates go to iNaturalist.** `INaturalistApi.kt:33-34` (`@Query("lat")`, `@Query("lng")` on
  `getSpeciesCounts`) and `:47-48` (the same pair on `getObservations`). Host confirmed separately:
  `INaturalistClient.kt:14`, `BASE_URL = "https://api.inaturalist.org/v1/"`.
- **Coordinates go to Open-Meteo, at two hosts.** `OpenMeteoApi.kt:32-33`
  (`@Query("latitude")`/`@Query("longitude")`) with `OpenMeteoClient.kt:14`
  (`https://api.open-meteo.com/`); `OpenMeteoArchiveApi.kt:25-26` with `OpenMeteoArchiveClient.kt:18`
  (`https://archive-api.open-meteo.com/`).
- **Tile requests describe the area viewed.** `Basemap.kt:162`
  (`https://a.tile.opentopomap.org/{z}/{x}/{y}.png`), `:171`
  (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`), and `:148`
  (`https://basemap.nationalmap.gov/.../{z}/{y}/{x}` — USGS imagery). All three are reachable:
  `MapMode.kt:29-31` pins Street to `OSM_STANDARD`, Topographical to `OPEN_TOPO_MAP` and Satellite
  to `USGS_IMAGERY_ONLY`, so the USGS host belongs in the list too — the dispatch named two tile
  hosts plus the Worker; it is four hosts.
- **A fourth network host the dispatch did not name:** `BasemapStyles.kt:154`,
  `GLYPHS_URL_TEMPLATE = "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf"`, used at
  `BasemapStyles.kt:80` in the style document. Map label fonts, not location — but it is an outbound
  request to a third party and is listed in the policy rather than omitted.
- **The Cloudflare Worker.** `OfflineStyle.kt:18`,
  `https://forager-pmtiles.brandonlee1-894.workers.dev/style/offline.json`; the style it serves
  points at `.../us.json` (`server/pmtiles-worker/src/offline-style.json:7`), and the Worker's own
  README documents `/us/{z}/{x}/{y}.mvt` as the live tile endpoint. So the Worker sees `z/x/y` for
  both live offline-style rendering and every region download.
- **The Worker writes no log of its own.** `grep -n "console\." server/pmtiles-worker/src/*.ts`
  returns nothing. `wrangler.toml` configured no `observability` block when this was written, which
  was read here as meaning no logs were kept. That inference was unsafe: Cloudflare documents
  `observability.enabled` as defaulting to `true` for newly created Workers, so an absent block
  means "whatever the platform default is", not "off". Checked in the dashboard on 2026-09-09: the
  Worker's Logs tab reported observability disabled, so the conclusion held. `[observability]
  enabled = false` is now set explicitly so it no longer rests on a default. What the
  policy states, therefore, is that *Cloudflare* records request metadata as the host — not that the
  Worker code does.
- **Photo EXIF.** `FilePhotoStore.kt:78-80` copies bytes from
  `contentResolver.openInputStream(uri)` with no EXIF pass over the destination; `:85` reads EXIF
  only for `GalleryImportPhotoSource`. The class's own doc comment (`:50-61`) already records the
  API 26-28 gap for gallery imports. The camera case is the one the README's claim actually broke
  on: `CameraCaptureFiles.kt:25-28` hands the camera app a `FileProvider` URI for a file in
  `filesDir/captures/`, which is not a MediaStore read, so the `setRequireOriginal` redaction the
  copy path relies on cannot apply to it.
- **No telemetry.** A case-insensitive grep for `firebase|crashlytics|analytics|sentry` across the
  Gradle files and version catalog (excluding `build/`) returns nothing. `CrashFileStore.kt` writes
  crash traces to a local file and `CrashLogPanel.kt` shares one only through an explicit user
  action, so "no telemetry" survives correction and is stated.
- **Track point contents**, for the policy's list of what is stored: `TrackPointEntity.kt:33-41` —
  lat, lng, altitude, accuracy, timestamp, Doppler speed and its accuracy.
- **Permissions**, for the policy: `AndroidManifest.xml:4-31` — internet, coarse and fine location,
  camera, `ACCESS_MEDIA_LOCATION`, foreground service (+ location), post notifications, vibrate.
  `minSdk = 26`, `targetSdk = 37` (`app/build.gradle.kts:192-193`).

### One finding neither the dispatch nor the old text accounted for

`AndroidManifest.xml:62` sets `android:allowBackup="true"`, and `app/src/main/res/xml/` contains
only `file_paths.xml` — there is no `dataExtractionRules` or `fullBackupContent` narrowing what is
backed up. So Android's own backup can copy the app's private data (tracks, entries, photos) to the
**user's** Google account. That is the user's phone backing up the user's data to the user's own
account, not the developer receiving anything, and it is off by default for a user with device
backup disabled — but "your photos never leave the phone", stated without qualification, is not
strictly true while that flag is set. Both the README and the policy now say so in one sentence
each. **The platform behaviour itself is documented Android behaviour, not something verified in
this repository** — what was verified is the manifest flag and the absence of exclusion rules.

## What was written

- **`docs/beta/README.md`** — three changes.
  - "How reports travel" no longer says nobody is asked to create an account; it says reports still
    travel as a pasted text block, and points at the new joining section for the account that *is*
    required.
  - New **"Joining the test"** section: Google account, the opt-in link, and staying opted in, each
    marked explicitly as Google's rule for closed testing rather than the owner's preference, with
    the point that leaving early is the one thing that actually costs the project something and that
    uninstalling is not leaving.
  - "Before anything else: what the app does not do" is now **"what leaves the phone and what does
    not"**: tracks, journal entries and photos stay put and there is no account, sync or telemetry;
    what leaves is the coordinates searched and the map area viewed, named recipient by recipient
    with the files to check; offline regions remove the tile half and nothing removes the search
    half; the photo-EXIF reality and the Android-backup caveat in one paragraph; the templates'
    own "we never ask where you were" kept unchanged.
- **`docs/beta/trip-report.md`** and **`docs/beta/device-report.md`** — the "Forager sends nothing
  anywhere" line inside each fenced block replaced with the accurate short form. **No question was
  added or removed in either template**, so the README's "24 questions ... five of them in the
  location block" count is untouched and still correct.
- **`docs/legal/privacy-policy.md`** (new) — short version; what stays on the device, with the
  `allowBackup` exception; a recipient-by-recipient table of what is transmitted and why, each row
  citing the file; the Cloudflare Worker section stating plainly that requests are logged and what a
  tile log is a record of; photo EXIF as it actually is, in three cases (no stripping step, gallery
  import on API 29+, camera capture); permissions and their purposes; no analytics/ads/tracking;
  children; changes; contact. It states outright that the Data safety declaration says location is
  collected *and shared*, and that this is the accurate description rather than a conservative one.

### The two TODOs, and only two

Left as marked TODO placeholders because they are facts, not judgement calls, and neither is
determinable from this repository:

1. **Contact email** for privacy questions (required on the Data safety form and in the policy).
2. **The Cloudflare Worker's log retention period.** Nothing in `server/pmtiles-worker/` records it,
   and it depends on the Cloudflare account's plan and settings, which are not in the repository.

Both placeholders say not to guess. Nothing else in the policy is a placeholder.

## On the Data safety declaration

Recorded here because the instinct it corrects is the one that produced the original error. The Play
Data safety form for this app should declare **location as collected and shared** — shared because
coordinates and tile requests reach iNaturalist, Open-Meteo, OpenStreetMap, OpenTopoMap, USGS,
MapLibre's demo tile host and the developer's own Cloudflare Worker, all third parties from Play's
point of view. That is consistent with the corrected README and with this policy. The earlier
instinct — to soften the declaration because the app "sends nothing anywhere" — was backwards: the
app's honest position is strong (no account, no telemetry, nothing you record is uploaded) and does
not need the one claim that is false to carry it.

## Verification of the text itself

- `grep -rn "sends nothing anywhere" docs/` returns nothing after the edit.
- `grep -rn "strips location metadata" docs/` returns nothing after the edit.
- `grep -rn "create an account" docs/beta/` returns nothing after the edit.
- `scripts/verify-policy-permissions.sh` reconciles the policy against the manifest in both
  directions and fails loudly on a mismatch. It exists because two defects that shipped in this
  policy were mechanically detectable and were not mechanically detected: a sentence claiming
  background location kept a recording alive, while `ACCESS_BACKGROUND_LOCATION` appears nowhere in
  `app/src/main/AndroidManifest.xml`, and a stale `applicationId`. Check 4 fails on `main` at the
  time of writing, correctly, because the policy names `com.zynergylabs.forager.app` while
  `app/build.gradle.kts` still builds `com.zynergylabs.forager.app`; it clears when the rename merges.
- Every host named in `docs/legal/privacy-policy.md` appears as a literal in the source at the line
  cited beside it; no host is named in the policy that is not in the source. The complete set of
  `https?://` literals in `app/src/main` and `server/pmtiles-worker/src` was enumerated
  (`grep -rhoE "https?://[a-zA-Z0-9._-]+" | sort -u`, 15 results) and reconciled one by one: eight
  are hosts the app actually requests and all eight are in the policy; `schemas.android.com` and
  `topografix.com` are XML namespaces, `github.com`/`osm.org`/`open-meteo.com` are attribution
  strings, `play.google.com` appears only inside a comment, and `www.inaturalist.org`
  (`AvailabilityPureFunctions.kt:198`) is a link handed to the browser or the iNaturalist app by a
  user tap — that last one is described in the policy as exactly that rather than omitted.

## Required disclosure

**Confirmed:** every source citation above, each read at `699efa3`; the absence of analytics
dependencies; the absence of logging calls and Logpush config in the Worker; the manifest's
`allowBackup` flag and the absence of backup-exclusion rules; that no template question was added
or removed.

**Inferred, and marked as inference in the text:** that Android's default Auto Backup applies given
`allowBackup="true"` with no exclusion rules (platform behaviour, not repository evidence); that the
platform's `setRequireOriginal` redaction covers the gallery-import copy path — this is the claim
`FilePhotoStore.kt`'s own doc comment makes, and it was not independently tested here.

**Could not verify:** the fourteen-day continuous-opt-in requirement and its tester-count threshold
come from the dispatch's statement of the owner ruling, not from anything checkable in this
repository or tested against Play. For that reason the README states the fourteen days but
deliberately gives **no tester-count number** — inventing "twelve" would have been exactly the kind
of plausible fabrication this policy is being written to avoid. If the owner wants the number in the
tester-facing text, it should be added from the Play console rather than from memory. Also
unverified: the Worker's log retention (TODO 2), the contact email (TODO 1), and whether Cloudflare's
request logging on this account is enabled beyond the default analytics.

**Premises in this dispatch that were wrong:** none. Two were incomplete — the tile-host list is
four hosts, not two (USGS is live via `MapMode.SATELLITE`), and there is a fifth outbound host for
map fonts (`demotiles.maplibre.org`). The `allowBackup` path was not in the dispatch at all.

**Decided without cover:** placing "Joining the test" ahead of the privacy section (a tester hits it
first chronologically); naming the Worker's hostname in tester-facing and public text, on the
grounds that it is already a public URL in a public repository; stating the Android-backup caveat in
the tester-facing README rather than only in the policy; not adding a tester-count number.

## Index row not added (planner is batching)

The row this dispatch would have appended to `docs/audits/README.md`:

```
| 2026-09-09 | Completion report: the beta consent text corrected and a privacy policy drafted — "it sends nothing anywhere" and "strips location metadata from stored photos" both false and both replaced (coordinates to iNaturalist and Open-Meteo, `z/x/y` tiles to OpenStreetMap/OpenTopoMap/USGS/MapLibre glyphs and the project's own Cloudflare Worker, each cited to its file; camera captures never pass through the MediaStore redaction the copy path relies on); a "Joining the test" section for Play closed testing (Google account, opt-in link, continuous opt-in, leaving early as the one real cost), marked as Google's rule not the owner's; `docs/legal/privacy-policy.md` written for the Data safety URL with the Worker's request logging stated plainly and exactly two TODOs (contact email, Worker log retention); `android:allowBackup="true"` with no exclusion rules found as an unaccounted-for off-device path and disclosed in both documents; Data safety should declare location collected **and** shared, and the instinct to soften it recorded as backwards; no template question added or removed, so the 24-question count stands | `2026-09-09-beta-consent-text-completion-report.md` |
```
