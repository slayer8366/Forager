# Deleting your Forager data

**Last updated: 2026-10-15.**

Forager stores everything on your own device. There is no account, no server, and no copy of your
data anywhere except the phone in your hand and any backup files you choose to make. That shapes what deletion means here: there is nothing
to request, because there is no one holding a copy to ask.

## Delete individual items in the app

Anything you create can be deleted from inside Forager, item by item, and the deletion takes effect
immediately:

- **Journal entries** — including any coordinate attached to a find.
- **Photos** — whether taken with Forager's camera or imported from your gallery. Deleting a photo
  removes the stored file, not just the reference to it.
- **Recorded tracks** — the track and every GPS point in it.
- **Waypoints** — including vehicle and origin markers.
- **Downloaded offline map regions.**
- **Planned trips.**

## Uninstalling removes everything

Uninstalling Forager deletes all of it: the database, the photo files, downloaded map regions and saved
preferences. Nothing is left behind in the app's own storage.

Forager also sets `allowBackup="false"`, which means Android does **not** copy your Forager data into
Google's automatic backup. The only copies off the phone are backup files you make yourself with
Forager's Backup, saved where you choose. Uninstalling does not touch them; delete them wherever you
saved them.

## There is nothing stored on a server

Forager has no account system and uploads nothing you create. Your entries, photos, tracks and
waypoints never leave the device.

What does leave the device, while you are online, is **where you are looking** — the coordinates a
species or weather lookup runs on, and the map tiles for the area on screen. Those requests go to
iNaturalist, Open-Meteo, the map tile providers and a Cloudflare Worker run by the developer, and
they are logged by the services that receive them. They carry no name, no account and no device
identifier, because Forager has none to send. Full detail, including exactly which fields each
service receives, is in the [privacy policy](https://www.zynergy-labs.com/privacy/).

Because those requests are not stored by Forager and cannot be tied to you, there is no deletion
request to make. If you want to stop them entirely, denying or revoking the location permission ends
them; the app keeps working without it.

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
