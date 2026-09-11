# Store assets

## `play-store-icon-512.png` — this is the one to upload

512x512, flattened over `#2E5339`, no alpha channel. Rendered from
`res/drawable/ic_launcher_foreground.xml` over `ic_launcher_background.xml`, then scaled by
**512 / 342 = 1.4971** and re-centred.

That scale factor is not a taste judgement. A launcher never shows the adaptive icon's full 108dp
canvas — it shows the central 72dp safe zone, which is 342px at this resolution. Scaling the safe
zone up to the full frame makes the store icon occupy the same proportion of its frame that the
installed icon occupies of the area a device actually displays: **66.6% x 81.8%**, against a target
of 67.0% x 82.2% measured off the device geometry.

The artwork was also re-centred. In the source vectors it hangs 45px low at this resolution, which
no user has ever seen, because the mask crops the bottom of the canvas away.

## `play-store-icon-512-device-faithful.png` — evidence, not for upload

The same composite with **no** scaling or re-centring: the raw 108dp canvas at 512x512. Artwork
occupies 44.7% x 54.9% of the frame and sits 45px low.

Kept because it is the reason the other file exists. Uploading this one would make the store icon
look *smaller* than the installed icon, which is the opposite of faithful — the intuition that an
unscaled render is the honest one is exactly the mistake this pair documents.

## Two known icon defects, deliberately not fixed here

Both are in `res/drawable/ic_launcher_foreground.xml` and both are cosmetic. Neither blocks the
listing, and mid-beta is the wrong time to change the app icon.

1. **The stem overflows the safe zone.** The artwork runs to y=441 of 512; the safe zone ends at
   427. Circular-mask launchers already clip the stem tip. `play-store-icon-512.png` *works around*
   this rather than fixing it — the store icon is unmasked, so the whole stem shows.
2. **No `<monochrome>` layer.** Themed icons on Android 13+ fall back instead of tinting.

## Not here

The 1024x500 feature graphic and the screenshots. Neither is derivable from the icon.
