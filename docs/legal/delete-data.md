# Deleting your Forager data

**Last updated: DRAFT, date left for the owner to set on publishing, not yet published.**

> **Draft status (remove before publishing).** Describes the build that PR #140 ships (the Journal
> redesign: track delete, journal backup and restore, Save to Gallery). Not to be published before
> that build reaches testers. Every claim was re-read against the code at `journal-redesign`
> `f645e8f9`; the file and line behind each is in
> `docs/audits/2026-09-29-legal-docs-l1-completion-report.md`.

Forager stores everything on your own device. There is no account and no server, and the only copies
of your data outside the app are ones you saved yourself. That shapes what deletion means here:
there is nothing to request, because there is no one holding a copy to ask.

## Delete individual items in the app

Anything you create can be deleted from inside Forager, item by item. Deleting a waypoint, track,
offline map region, journal entry, find or photo shows an Undo for a few seconds first, and the item
is removed when the Undo closes; a planned trip is deleted at once.

- **Journal entries and finds** — including any coordinate attached to a find. Deleting an entry also
  removes the copies it kept, described below.
- **Photos** — whether taken with Forager's camera or imported from your gallery. Deleting a photo
  removes the stored file, not just the reference to it. It does not remove a copy you saved to your
  Gallery.
- **Recorded tracks** — a swipe on the track's row in Records, or Delete on the track's details.
  This removes the track and every GPS point in it. A track that is still recording cannot be
  deleted. It does not remove the copy a journal entry kept of the track, or a GPX file you shared
  from it; see below.
- **Waypoints** — including the start and end waypoints a recording drops (`WaypointDesignation.kt`). This does not remove the copy a journal
  entry kept of the waypoint; see below.
- **Downloaded offline map regions.** This does not remove the copy a journal entry kept of the
  region; see below.
- **Planned trips.**

## Copies that stay until you delete the entry

**A journal entry keeps a copy of what it noted about the tracks, waypoints, offline map regions,
finds and photos it lists.** Deleting a track, waypoint or offline map region does not remove the
copy a journal entry kept; delete the entry to remove it. What an entry keeps:

- a **waypoint**: its name and its position;
- an **offline map region**: its name, the position of its centre and its radius;
- a **track**: its name, distance, duration and number of points. Once the track is deleted, the
  entry also keeps its **path**: the latitude and longitude of each point, in order, without times.
  The path is saved at the moment you delete the track, into every journal entry that has recorded a
  decision about it, including an entry where you chose to leave the track out;
- a **find**: its date and your own identification of it;
- a **photo**: the date you attached it.

These copies are removed only when you delete the entry itself, and they are also in any journal
backup you make.

**Two more things stay inside the app after you delete what they came from:**

- **A shared GPX file.** When you share a track as GPX, Forager first writes the file to its own cache
  folder and hands that copy to the share sheet. Deleting the track does not remove it. It goes when
  you or Android clear the app's cache, or when you uninstall. The copy you sent to another app is
  that app's, and outside Forager's control.
- **Your last five searches.** Forager keeps the place, radius, month and filter of your last five
  searches, with the species list they returned, so that a search you have run before still shows an
  answer with no signal. There is no button to clear them; a sixth search replaces the oldest, and
  clearing the app's storage or uninstalling removes them.

## Uninstalling removes what Forager stores

Uninstalling Forager deletes everything in its own storage: the database, the photo files,
downloaded map regions, saved preferences, the cache and any crash reports.

**It does not delete files you saved outside the app**, because those are yours and Forager cannot
reach them once saved:

- **Journal backups** — `.zip` files in the place you chose. Backup files are not encrypted. A
  scheduled backup writes a new file each time; Forager keeps the newest five files it made in that
  folder and deletes older ones it made itself. It knows which files those are from a list it keeps
  in its own storage, so it never deletes a backup you made by hand or any other file in the folder.
  If you clear the app's data or reinstall, that list is gone, and scheduled backups made before then
  stay until you delete them; and if Android refuses a delete, the file stays. So check the folder
  you picked.
- **Photos saved to your Gallery** — in the "Forager" album, or wherever you saved it on Android 8
  and 9.
- **Exported GPX tracks** — wherever you sent them.

Delete those in your Files or Gallery app. If you saved a backup to a cloud folder, delete it there
too.

Forager also sets `allowBackup="false"`, which means Android does **not** copy your Forager data into
Google's automatic backup. There is no automatic cloud copy to survive the uninstall and reappear on
your next phone. Moving your journal to a new phone is done with Forager's own backup, which you make
and place yourself.

## There is nothing stored on a server

Forager has no account system and uploads nothing you create. Your entries, photos, tracks and
waypoints leave the device only as files you choose to save or share yourself.

What does leave the device, while you are online, is **where you are looking** — the coordinates a
species or weather lookup runs on, and the map tiles for the area on screen. Those requests go to
iNaturalist, Open-Meteo, the map tile providers and a Cloudflare Worker run by the developer, and
they are logged by the services that receive them. They carry no name, no account and no device
identifier, because Forager has none to send. Full detail, including exactly which fields each
service receives, is in the [privacy policy](https://www.zynergy-labs.com/privacy/).

Forager keeps no account and no record of these requests on any server of ours (the Worker keeps no
request logs, as the privacy policy explains), and on the phone only the list of your last five
searches records them, so there is no deletion request to make to us. The other services keep their
own logs under their own policies. Denying or revoking the location permission stops Forager
reading your position, and the app keeps working without it, but it does not stop these requests:
coordinates you type in or a map you move still go out as described in the privacy policy.

## The beta signup list is separate

Everything above is about the app. There is one piece of data Zynergy Labs does hold about a person,
and it does not come from the app at all.

If you filled in the form at <https://www.zynergy-labs.com/beta-signup/>, that signup is stored: your
email address, anything optional you chose to add (a name, a device type, an Android version, a note)
and a two-letter country code derived from your connection. Your IP address is not kept. The full
description is in the [privacy policy](https://www.zynergy-labs.com/privacy/#beta-signup-list).

**To have your signup record deleted, email privacy@zynergy-labs.com** and say which address to
remove. Unlike the app data above, this is a genuine request channel, because here there really is a
copy held by someone other than you.

Removing your signup does not affect anything on your phone, and deleting data on your phone does not
remove your signup. They are separate and each has to be asked for separately.

## Questions

privacy@zynergy-labs.com

---

*This file is the source of truth. The user-facing page published at
<https://www.zynergy-labs.com/delete-data/> is generated from it, in the same way
`privacy-policy.md` generates <https://www.zynergy-labs.com/privacy/>. If the two disagree, this
file is correct and the page needs regenerating.*
