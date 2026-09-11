# Deleting your Forager data

**Last updated: 2026-09-10.**

Forager stores everything on your own device. There is no account, no server, and no copy of your
data anywhere except the phone in your hand. That shapes what deletion means here: there is nothing
to request, because there is no one holding a copy to ask.

## Delete individual items in the app

Anything you create can be deleted from inside Forager, item by item, and the deletion takes effect
immediately:

- **Journal entries** — including any coordinate attached to a find.
- **Photos** — whether taken with your camera app or imported from your gallery. Deleting a photo
  removes the stored file, not just the reference to it.
- **Recorded tracks** — the track and every GPS point in it.
- **Waypoints** — including vehicle and origin markers.
- **Downloaded offline map regions.**
- **Planned trips.**

## Uninstalling removes everything

Uninstalling Forager deletes all of it: the database, the photo files, downloaded map regions, saved
preferences and any crash reports. Nothing is left behind on the device.

Forager also sets `allowBackup="false"`, which means Android does **not** copy your Forager data into
Google's automatic backup. There is no cloud copy to survive the uninstall and reappear on your next
phone. That is a deliberate choice: it costs you the convenience of a restore, and it means
uninstalling really is the end of the data.

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

## Questions

privacy@zynergy-labs.com

---

*This file is the source of truth. The user-facing page published at
<https://www.zynergy-labs.com/delete-data/> is generated from it, in the same way
`privacy-policy.md` generates <https://www.zynergy-labs.com/privacy/>. If the two disagree, this
file is correct and the page needs regenerating.*
