# Handover 2026-10-20: the trail design for Street v1, from the Pi planner to Rowan

From Aspen Meridian, the Pi planner, at `pi-build` `1dae0e30`. The owner, through the question tool,
verbatim: "Hand to Rowan (Recommended)". The option read: "I send Rowan the finished spec and record;
Hazel builds it into Street v1 (PR #258) and Rowan arranges a phone review build. The Pi preview
stays up until you say stop."

**Rowan takes the owner's go in Rowan's own window,** as Rowan did for #338. This document is the
material, not the go.

## What it is

Trail styling for `sac_scale`, `trail_visibility` and `informal`, filled into Street v1's reserved
slots. Designed by Sage Wayfinder, ruled by the owner, and drawn by a Pi coder on the new US +
Canada archive, locally only.

## The final spec

Every value below is set by the owner's rulings. The record is
`docs/audits/2026-10-20-cloudflare-upload-prep.md` on `pi-build`; see the sections from "The owner's
rulings on Sage's design" to "Revision 2 drawn". Sage's design and revision are in
`docs/audits/2026-10-20-sage-01-trail-variants.md`.

- **Groups,** read from `sac_scale`:
  - easy = strolling, hiking;
  - rough = mountain_hiking, demanding_mountain_hiking;
  - hard = alpine_hiking, demanding_alpine_hiking, difficult_alpine_hiking;
  - unknown = untagged or any other value.
- **Visibility:** faint = `trail_visibility` bad, horrible or no. Everything else, including
  untagged, is clear.
- **Informal:** `informal == "yes"`. It overrides every group except hard.
- **Dash**, as `[dash, gap]` in line widths with butt caps. Gap g is 1.5 for clear and 4 for faint.

  | Zoom | Unknown | Easy | Rough | Hard |
  |---|---|---|---|---|
  | z13 | `[4, 1.5]` | as unknown | as unknown | `#7a1e10`, base width |
  | z14 | `[4, g]` | as unknown | as unknown | `#7a1e10`, +0.4 width, `[4, g]` |
  | z15 and up | `[4, g]` | `[8, g]` | `[2, g]` | `#7a1e10`, +0.4 width, `[2, g]` |

  - Unknown moves from Street v1's `[3, 1.5]`. The owner: "Longer dash (Recommended)".
  - The z14 row is the owner's "Show from zoom 15 (Recommended)".
- **Informal:**
  - z13: a plain `[4, 1.5]` dash in the informal colour.
  - z14 and up: round-cap dots `[0.1, 2.2]` at base width.
  - Day colour `#a0735a`, which replaces the reserved `#b9876b`.
  - Its own night colour `#bb8a6f`. The owner: "Own night colour (Recommended)", under −1344.
- **Hard at night:** V1 `#ffad9f`.
- **Unchanged:** steps (Street v1 `[3, 1.5]`), tracks, and everything else in Street v1.
- **Contrast.** The owner approved a band for informal at night: "Accept the range (Recommended)".
  That is about 1.9 to 2.3 on the night forest `#436d35`, below the night footpath's 2.43, in place of
  the 2.5 exception floor for that one layer. It is a per-layer rule, not a widened floor.
  `check-street-night.mjs` needs to express it.
- **Engine:** `line-dasharray` is not data-driven in MapLibre Native, per style-spec 26.4.4's sdk
  support. So it needs one layer per group: 90 layers, against Street v1's 74. This was **not**
  checked against Native 13.5.0 itself.

## The exact change, as drawn

`docs/audits/assets/2026-10-20-trail-design/forager-street-trails-r2.diff` is the Pi coder's scratch
`forager-street.mjs` diffed against `street-cloudflare` `5b270ac4`. The scratch file's sha256 is
`c283d4a7…b076`. **It is a reference, not a patch to apply as-is.** Its comments say "SCRATCH", the
night-check band is not in it, and Hazel builds it the project's way, with tests and revert checks.

## Not seen, and for Rowan's side

- **On a phone, nothing has been seen.** These are Firefox web renders. Hazel's report says the
  phone is the final word, especially for labels.
- **The thin horizontal seam** in some renders is attributed to the web engine. That is not
  confirmed on a phone.
- **Informal and faint together:** informal wins, by Sage's ruling as built. The owner was not asked
  separately.
- **No app change is asked for.** There is no legend: "No legend (Recommended)". `SightingsMap.kt`
  is not touched.
- **The renders** are on the Pi in `~/street-preview-scratch/shots-b3/` and the earlier `shots*/`.
  They are not in any repository, because they show place names. The owner can see them in the Pi's
  preview.
