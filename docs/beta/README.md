# Beta

What Forager asks of a closed-beta tester, and the two report templates they fill in. Tester-facing
documents live here, apart from the engineering tree; this file is their index and the source of
truth for the text the owner sends out.

## How reports travel

**The template is a block of plain text.** The owner copies the block out of the file below and
pastes it into the invitation on whatever channel they already use with the tester; the tester
answers inline in their messaging app and sends it back the same way. Nobody is asked to open
GitHub or edit a file. Joining the test itself does need a Google account — see the next section.
This file and the templates are the canonical text — edit them here, send the block from here.

## Joining the test

The beta is a **Google Play closed test and nothing else**: there is no APK to sideload. Three
things follow that the informal version did not need, and all three are Google's rules for closed
testing rather than the owner's preference.

1. **A Google account.** The owner needs the address on it to put you on the tester list; Play will
   not show you the app otherwise. It is the only identifier the beta requires, and Google holds
   it, not the app.
2. **Clicking the opt-in link.** Being on the list does nothing by itself. The owner sends a link;
   you open it signed in to that account and accept, and only then can you install Forager.
3. **Staying opted in.** Google counts *continuous* opt-in days per tester, and a closed test has
   to hold enough testers opted in continuously for fourteen days before the app can be promoted to
   production. Opting out restarts that tester's count at zero.

So leaving early is the one thing here that actually costs the project something. Uninstalling the
app is not leaving — the opt-in is what counts, and installing, walking once and letting it sit
there is a perfectly good way to be a tester. If you want out, say so and opt out; that is fine,
and it is more useful than going quiet.

## Before anything else: what leaves the phone and what does not

> **Draft status (remove before publishing).** This section describes the build that PR #140 ships
> (the Journal redesign: track delete, backup and restore, Save to Gallery); it is not the build
> testers have today. Every claim was re-read against the code at `journal-redesign` `f645e8f9`;
> the file and line behind each is in `docs/audits/2026-09-29-legal-docs-l1-completion-report.md`.

Forager has no account, no sign-in, no sync and no telemetry. Your tracks, your journal entries and
your photos are written to the phone's own storage, and the app never uploads them.

What does leave the phone, whenever it has signal, is the ground you are asking about. A species or
weather lookup sends the coordinates it is searching on to iNaturalist and to Open-Meteo
(`INaturalistApi.kt`, `OpenMeteoApi.kt`, `OpenMeteoArchiveApi.kt`), and the map fetches its tiles
from OpenStreetMap, OpenTopoMap, USGS and a Cloudflare Worker this project runs — a tile request is
a `z/x/y` square, which is the patch of ground on your screen (`Basemap.kt`, `OfflineStyle.kt`).
Those requests carry no name and no account, because there is none, but they carry a place and your
IP address, and the servers answering them can log both. Denying the location permission does not
stop them: a place you type in or a map you move still goes out. A downloaded offline region is
stored on the phone, but nothing removes the search half of those requests except not searching.
Forager also keeps your last five searches (place, radius, month, filter
and the species list returned) on the phone, so a search you have run before still answers with no
signal (`RoomSearchCacheRepository.kt`).

Two more honest details. A photo taken with Forager's own camera has its embedded metadata,
location included, stripped when it is stored; only what the picture needs to display is kept — its
orientation, its colour profile and two small technical headers (`FilePhotoStore.kt`,
`PhotoMetadataScrub.kt`). A photo you import from your gallery is not stripped by Forager: Android
10 and up normally withholds its GPS tags from the copy the app reads, but on Android 8 and 9 it can
keep them, and "Save to Gallery" saves an import exactly as it was imported (`PhotoExporter.kt`). The
owner, verbatim: "Imported photos taken outside the app are not within our scope. They can use a
scrubbing app to remove it if they want it removed. All photos taken inside the app are scrubbed
either way and that's our scope". Save to Gallery writes the photo's own time as the copy's "date
taken", when the app has one, and never a location.

And Android's own backup is switched off for Forager (`android:allowBackup="false"`,
`app/src/main/AndroidManifest.xml`), permanently and on purpose. Nothing your phone does copies
Forager's tracks, entries or photos to a Google account, and nothing hands them across during a new
phone's setup. Moving your own data is the app's job instead: **Backup** in Tools, then Settings,
writes one `.zip` file to a place you choose, and **Restore** reads one back, replacing or merging
(it asks each time; it does not add an offline map region the phone already has, `RegionMatch.kt`).
Scheduled backups are off unless you turn them on, and the first one runs after a full interval
rather than at once. The app keeps the newest five files a scheduled backup made and deletes older
ones it made itself, never a backup you made by hand or anything else in the folder
(`BackupSchedule.kt`). A scheduled backup that fails, or leaves some photos out, posts a "Backups"
notification, and the notification permission is asked once, when you first turn scheduled backups
on; if you decline, the same words appear once in the app the next time you open it
(`ScheduledBackupNotice.kt`, `BackupViewModel.kt`). The backup file is not encrypted and holds your
entries, finds, photos, full GPS tracks, waypoints, planned trips and offline-region details, and
also the list of your last five searches, so put it somewhere you trust; and because it lives
outside the app, uninstalling Forager does not delete it.

Deleting a track (a swipe in Records, or Delete on its details; never while it is recording) does not
remove what a journal entry kept about it: an entry keeps its own copy of a track's name, distance
and, once the track is deleted, its path, and of a waypoint's or offline region's name and position,
until you delete the entry (`CartographyEntryEntity.kt`, `DeleteTrackUseCase.kt`).

The templates never ask where a tester was — foragers mark spots they do not want found — and ask
for terrain and sky instead: "dense fir canopy", "open ridge", "car park". Every field in them is
something a person chooses to type and send. `docs/legal/privacy-policy.md` is the long form of
this section and the URL Play's Data safety declaration points at.

## The two templates

| File | When | What it asks |
|---|---|---|
| `device-report.md` | Once | A handle the tester chooses, phone make and model, Android version, whether the phone lives near a magnet, whether Do Not Disturb is usual, whether notifications were allowed |
| `trip-report.md` | After each trip | Battery, location quality (including whether the arrow screen's "within …" number ever changes), compass, the off-track alert, offline maps, anything else — with a track file as an explicitly optional, explicitly explained extra |

Two templates rather than one because the device questions asked on every trip invite "same as
before", which is useless as data. The handle is the join: the tester picks it, it need not be a
name, and it appears on both.

**Why the trip report asks whether the track looked shorter than the walk (added 2026-09-08,
after the GPX full-record pre-build report):** the app counts a track point only when its timestamp
lands on a whole second (`NetworkProviderFix.kt`; every point is still stored, the rule applies when
a track is read). That is a proxy for "this fix came from GPS, not
the network provider" — it has held across five data sets, all from one phone. On a phone whose GPS
fixes carry milliseconds, the same rule throws away good fixes, and the symptom is not a spike or a
blank: it is a track that looks fine and is simply shorter than the ground walked, with corners cut
where the missing points were. Nothing in the app can see that; only the walker can, by comparing
the drawn track with the walk they remember. The line is joined to the device report's make and
model, which is what turns "shorter" into "shorter on this chipset family". The export now
carries every stored point with its kept/excluded verdict (the GPX full-record dispatch, landed
2026-09-08 — see the paragraph below and
`docs/audits/2026-09-08-gpx-export-full-record-completion-report.md`), so a tester's file answers
this directly: excluded points along a stretch the tester walked are the rule being wrong on that
hardware. The question stays in the template regardless — a tester who noticed a short-looking
track is exactly the signal that makes a track file worth asking for.

**Why the device report asks for make and model *and* Android version, and why neither should be
trimmed as boilerplate later:** together they identify the phone's GNSS chipset family, which is the
variable behind the app's network-fix rule (a GPS fix's timestamp is second-aligned on the owner's
device and is a property of the chipset, not a guarantee — see `NetworkProviderFix.kt`). A report of
a track drawn short or empty used to mean nothing without knowing which family produced it, and the
tester's prose ("spikes or a starburst", "track drawn short") was the only instrument for checking
the rule on hardware other than the owner's. It no longer is: the GPX file the trip report's last
block already asks for now carries every stored point, kept or excluded, tagged with the verdict the
rule gave it (`<trk><extensions>` — see `NetworkProviderFix.kt` and
`docs/audits/2026-09-08-gpx-export-full-record-completion-report.md`), so a track file settles what
the rule did on that device directly rather than through a description of the symptom. The device
make and model still matter — they're what turns one tester's file into a data point about a
chipset family, not just about one phone. The magnet question exists for the compass: a phone on a
magnetic car mount is a permanent distortion and would explain "Compass unreliable" reports that
have nothing to do with power lines.

**Why the trip report asks whether the "within …" number changes, and why that line is the
highest-value one it now carries:** on the owner's phone every GPS fix reports the same horizontal
accuracy — `3.7900925` m, 289 fixes out of 289 on one walk, identical to seven decimal places
(`docs/audits/2026-09-07-fix-log-walk-findings.md`). That field is what the app's 50 m live-fix
gate, its "Approaching" threshold and the arrow screen's "within 12 ft" all read, so on that device
none of them is tracking real fix quality. The "within" number is the only place a tester can see
the field: on a phone that reports a real accuracy it moves with the sky, and on one that reports a
placeholder it is one number forever. "Always the same — 12 ft" from a second make is the answer
that decides whether this is one phone or the platform; "it changed" is just as valuable. It is
gated on the arrow screen because that is the only surface that prints it, and it is joined to the
device report's make and model, which is what makes the answer usable.

## Why it is the length it is

The trip report asks 24 questions including its gate lines (the 22 it was written at, plus the
"within …" question and the "shorter than the walk" question), five of them in the location block —
each question counted once however it wraps, re-counted from this merged template on 2026-09-08. An
earlier figure of 26 counted the two new questions' wrapped lines and is superseded, not confirmed.
Four of its six blocks open with a line that
lets a tester skip the rest of the block ("never navigated back", "skip if you didn't use them",
"no crash", "no spikes"). A plain walk with recording on answers roughly thirteen short lines, most
of them one word. A trip where everything happened answers all of them, and that tester has
something to say.

## If it has to be shorter — the cut order

Decided when the template was written, so whoever shortens it later does not reinvent the
priorities:

1. **First cut:** move the offline-maps block out of the per-trip report into a one-off "the first
   time you use offline maps" mini-report. Most trips download nothing.
2. **Second cut:** fold the two compass questions into one line with two blanks.
3. **Never cut:** the battery block (the fields the owner asked for by name, and the "nothing
   noticeable" line that makes silence into data) and the five location questions — the fourth,
   "within …" line, added after the walk that found the accuracy constant, because the beta is
   the only thing that can say whether other phones do the same, and the fifth, "shorter than the
   walk", the one that can catch the network-fix rule discarding good fixes on a phone unlike the
   owner's, which no test in the repository can. They are the reason the template exists. Below
   about ten lines the report stops answering the questions that motivated it.

## What "no" means here

Every block accepts "no" and "didn't notice" as complete answers and says so at the top. A tester
who walked an hour under canopy and never saw a stale-fix message is the data point most likely to
go unreported; the template asks for it by name in the battery block and by shape everywhere else.
