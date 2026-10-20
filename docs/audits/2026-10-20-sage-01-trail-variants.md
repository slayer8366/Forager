# Sage Wayfinder: trail difficulty and visibility on Street v1 (brief 2026-10-20-sage-01)

Filed by the Pi planner, Aspen Meridian, as received. Sage ran on Fable, read-only, from the brief
`prompts/preserved/2026-10-20-sage-01.md` (pi-build `cdea920c`), and wrote no files. Started on the
owner's "Start Sage (Recommended)".

**The planner's spot-checks:**

- `forager-street.mjs` on `street-cloudflare`: `:37-39` (highway and major colours), `:102`
  (`PATH_MIN_ZOOM = 13`), `:104` (`FOOT_DETAILS` includes bridleway and cycleway) and `:222-229`
  (the track dash `[4, 1.5, 1, 1.5]`, the footpath dash `[3, 1.5]`, widths 13: 1.2, 15: 1.8,
  18: 3.5) all read as cited.
- Sage's V1 night colours were recomputed with the formula at `check-street-night.mjs:6`:
  - `#7a1e10` → `#ffad9f`. Sage gave `#ffada0`, one step off on blue;
  - `#b9876b` → `#9b694d`;
  - `#d4641f` → `#ee7e39`;
  - `#7f2a1a` → `#fca797`;
  - `#2b1a14` → `#efded8`.

  All match apart from that one step. The contrast ratios were **not** recomputed here; they stay
  inferred, as Sage marked them.
- Sage read tiles from the coder's running local server. That was a read, within its bounds; it
  started nothing.

The report follows, verbatim.

---

Read-only. No file was written anywhere; no server, browser, build or install was run. The only things touched were reads: `git show` on the five branches, the 28 renders in `~/street-preview-scratch/shots/`, the running local tile server on 127.0.0.1:8081 (tiles read with a stdin Python script, nothing saved), and five web pages. Claims are marked **read** (file:line), **observed** (a named render or tile), **inferred**, or **web** (URL, read 2026-10-20).

Bases checked: `origin/pre-main` `03bf2e420` (CLAUDE.md differs from this worktree's copy by three lines, none bearing on this; the first Known pitfall is obeyed), `origin/pi-build` `cdea920c5`, `origin/street-cloudflare` for `forager-street.mjs` (footpath dash at `:227`, track at `:223`, `PATH_MIN_ZOOM` at `:102`, `informalPath` at `:29`, as the brief says).

## Premises in the brief that need refining (found by reading the tiles)

1. **The trail keys are present from z13, not only z15.** The brief's amendment gives the coder's measurement at Mt Rainier z15. Decoding the local server's tiles at the four render centres (observed): Mt Rainier z13 carries 31 paths, 18 with `sac_scale` (hiking 13, mountain_hiking 5), 9 with `trail_visibility`, 2 `informal=yes`; z14 much the same. So a variant can act from z13; whether it *should* is a design question (below), not a tile limit.
2. **More values exist than the amendment lists** (observed): Lake Louise z13 has one `alpine_hiking` (T4) and one `informal=no`; Flattop z13/z14 has `trail_visibility=bad` and three `informal=yes`. Two consequences: the informal filter must be `informal == "yes"`, not "has `informal`"; and there is one T4 segment and one `bad` segment on the existing render places, so the variants can be judged on real hard and faint cases, not only on easy ones.
3. **Forest Park carries none of the three keys at any zoom** (observed, z12 to z15), confirming the amendment: untagged is the common case. Mt Rainier, the best-tagged place, still has 13 of 31 paths untagged at z13.
4. **z12 tiles do carry a few paths, all `track`** (observed: Lake Louise 2, Flattop 2, Forest Park 2 tracks; Mt Rainier none). Raising paths to z12 would add vehicle tracks only, no footpaths, at these places. I'd leave `PATH_MIN_ZOOM = 13` as it is: z13 is already dense (below), and z12 would gain nothing a walker needs.

## (a) What the renders show that bears on the trails

All 28 looked at. Trails are the one red-brown thing on the map, and that is their strength: at every zoom and in both modes they are found at a glance, on forest green, grass, the grey rock of Flattop and the grey of the Paradise car parks (observed: all four places at z14 day).

- **z13 is dense but readable** (observed: `day/mt-rainier-paradise-z13.png`, `day/lake-louise-z13.png`, `day/flattop-chugach-z13.png`). The dash `[3, 1.5]` at 1.2 px draws as a tight hatch; the web of trails north of Paradise is a tangle but still reads as trails. There is no room at z13 for a second dash grammar: a shorter dash would merge into a line and a longer one would look like a different kind of road. This is why I put the variants' differences at z14 and up.
- **Dash-dot tracks are hard to tell from footpaths at z13 and z14.** I could find a track only at Lake Louise (north-east of the village, z13) and Flattop (the straight line running north-east from the overlook, z13), and only by looking for it. Not in scope, but it means a *third* dash family for difficulty has to be more different from `[3, 1.5]` than `[4, 1.5, 1, 1.5]` is, or it will not be seen either.
- **z14 is the zoom where a dash pattern becomes legible** (observed: all z14). At 1.5 px the gaps are clear and a person can see "this line is broken differently from that one".
- **z15 adds trail names** (observed: `day/mt-rainier-paradise-z15.png`, `day/lake-louise-z15.png`); the dashes are 1.8 px and any pattern reads easily. At z15 the Mt Rainier view is almost all tagged trails drawn identically to untagged ones, which is the gap this brief fills.
- **Night** (observed: all 12 night renders). Footpaths invert to salmon `#ed8a6f`. On the night ground and the olive grass they are clear; on the mid-green night forest `#436d35` they are soft, which is the measured 2.43:1 (read: Part 1 report, round-1 table). The owner declined "trails a touch brighter" (−1345), so the base stays; but anything I add for a *warning* (hard, faint) must come out brighter than the base at night, not dimmer, or the warning is the first thing to vanish in the dark. That rules out opacity as a cue (below).
- **Flattop z15 is centred beside the trail** (observed: `day/flattop-chugach-z15.png` shows a corner of one trail top-right and nothing else). The re-centred render is requested in (f). Note Flattop z13/z14 is where the `bad`-visibility and informal segments are, so a re-centre on the trail would show them.
- **The horizontal seam** is at Mt Rainier z13/z14/z15 and Forest Park z15 (observed); attributed to the web engine (read: Part 1 report, round 2). Not a style matter.
- **The app's marks**: nothing on the map is magenta or purple, so finds `#DA02AF`, kept track `#A122F8` and breadcrumb `#650BB1` (read: `MapPalette.kt` DAY, lines 5 to 10 of the values block) stay apart from every colour below; the darkest colour I propose, `#7a1e10`, is a red-brown, not a magenta. A kept track drawn along a trail is wider and solid (Elder's review, "The app's own marks"), so a dash change on the trail does not touch that distinction.

## Prior art (described generically; each with its source)

- **The Swiss Alpine Club's scale, which `sac_scale` is named after** (web: `https://www.avegnogordevio.ch/files/Berg-Alpinwanderskala-EN.pdf`, the SAC's own table): T1 "no risk of falling with normal behaviour", marked yellow; T2 and T3 "surefootedness, proper boots", marked white-red-white; T4 to T6 "hands needed", marked white-blue-white. **Three bands, cut at T1 | T2–3 | T4+, and every hiker in that country already knows them.** Taginfo (web: `https://taginfo.openstreetmap.org/api/4/key/values?key=sac_scale`): hiking 56.6%, mountain_hiking 32.9%, demanding_mountain_hiking 6.4%, alpine_hiking 2.0%, strolling 0.8%, harder 1.0%.
- **`trail_visibility`** (web: `https://wiki.openstreetmap.org/wiki/Key:trail_visibility`, and taginfo `...key=trail_visibility`): excellent 24%, good 32%, intermediate 21%, bad 15%, horrible 4%, no 3%. "Bad" means "path sometimes invisible, route partly pathless".
- **An open topographic web map** (web: `https://wiki.openstreetmap.org/wiki/Compare_Hiking_Maps`): difficulty as three dot patterns, T1–2 *or untagged* / T3–4 / T5–6; visibility two groups, black vs grey. Note it merges untagged with easy, which is exactly what this brief forbids. **An open hiking web map** (same page): T1–2 plain, T3–4 dashed, T5–6 dotted; visibility two groups, red vs salmon. **An Android hiking-map theme** (same page, and web: `https://community.openstreetmap.org/t/exploring-outdoor-renderer-features/123868`): seven colours by T-grade (brown for unknown, yellow T1, blue T2, red T3, black T4+), dash by visibility. **A mobile offline map**: dotted black for expert, dotted brown for difficult, brown dash for normal.
- **What the conventions agree on**: difficulty and visibility are two axes drawn with two different cues; two or three groups per axis, never six; the hardest grade goes darkest or dotted; low visibility goes fainter or more broken. **Where they differ**: whether colour or dash carries difficulty, and where the bands cut (T1 | T2–3 | T4+ in the Swiss scale; T1–2 | T3–4 | T5–6 in the two web maps).
- The OSM wiki pages for both keys say nothing about rendering (web: `https://wiki.openstreetmap.org/wiki/Key:sac_scale`, `.../Key:trail_visibility`).

## The grouping, used by all three variants

**Difficulty, three groups on the Swiss cut**, because it is the scale's own signpost bands and because the forager's question is "can I walk this with a basket" rather than "which of six grades":

| Group | `sac_scale` values | Why |
|---|---|---|
| **easy** | `strolling`, `hiking` | T1: "no risk of falling"; 57% of all tags |
| **rough** | `mountain_hiking`, `demanding_mountain_hiking` | T2–3: boots and surefootedness; 39% |
| **hard** | `alpine_hiking`, `demanding_alpine_hiking`, `difficult_alpine_hiking` | T4+: hands needed; 3%, but the one group that is a warning |
| **unknown** | no tag, or any other value | the common case; drawn as Street v1 is today |

**Visibility, two groups**, as the two web maps do: **clear** = `excellent`, `good`, `intermediate` (77%; "mostly visible" is not a warning) and **faint** = `bad`, `horrible`, `no` (22%; "sometimes invisible, partly pathless"). Unknown = untagged, drawn as clear. The owner can move `intermediate` to the faint side (question 7).

**Informal**: `informal == "yes"` only, dotted and faint in `informalPath #b9876b`, in every variant (−1284; not reopened). Steps and tracks are untouched. Bridleway and cycleway stay in the footpath set as Street v1 has them (`forager-street.mjs:104`).

**Build note for the coder** (inferred, flagged): whether `line-dasharray` can be data-driven in MapLibre Native 13.5.0 is unverified; one line layer per group with a filter, as `pathBase` (`forager-street.mjs:212`) already does, needs no such support. Variant A is 4 difficulty × 2 visibility = 8 footpath layers plus informal; drawn in the order unknown, easy, rough, hard, informal on top? No: informal below hard, so a warning is never covered. Order: informal, unknown, easy, rough, hard.

## (b) The variants as specs

Common to all: base footpath colour `#a8452a` (night `#ed8a6f`), base dash `[3, 1.5]`, base width `13: 1.2, 15: 1.8, 18: 3.5` (read: `forager-street.mjs:225-229`). Dash units are multiples of line width, as in Street v1. **Differences appear from z14**; at z13 every footpath draws as unknown (reason: (a), z13 density), except Variant A's and C's *hard* colour, which shows from z13 because a warning is worth seeing early and colour costs no space.

### Variant A, "the line looks like the trail" (recommended)

One idea: **the more broken the line, the more broken the trail.** Dash *length* says how hard; dash *gap* says how easy to lose. Colour stays the one red-brown, except the hardest group, which also goes darker.

| | clear (gap 1.5) | faint (gap 4) |
|---|---|---|
| **easy** (long dash) | `[6, 1.5]` | `[6, 4]` |
| **unknown** (Street v1) | `[3, 1.5]` | `[3, 4]` |
| **rough** (short dash) | `[1.5, 1.5]` | `[1.5, 4]` |
| **hard** (short dash, dark, a little wider) | `[1.5, 1.5]`, colour `#7a1e10`, width base + 0.4 | `[1.5, 4]`, same colour and width |

- Colours: unknown, easy, rough `#a8452a` day, `#ed8a6f` night (V1, unchanged). Hard `#7a1e10` day; V1 night `#ffada0` (computed by the app's formula, `NightColour.kt:39-47`; inferred, for the coder to confirm). Contrast, inferred by hand with the WCAG formula: hard on day forest `#a9d39b` about 6.2:1; night `#ffada0` on night forest `#436d35` about 3.4:1, which is *brighter* than the base trail's 2.43, so the warning is the trail that survives night best.
- Informal: `#b9876b`, round-cap dots `[0.1, 2.2]` with `line-cap: round`, width base − 0.2, from z14. It is the only dotted line on the map and the only tan one, so it cannot be read as a difficulty.
- Zoom: dash differences from z14; hard colour from z13; informal from z14 (at z13 draw informal as a plain faint-colour dash `[3, 1.5]`, so it still exists but adds no pattern to the z13 tangle).
- Width and everything else: Street v1.

### Variant B, "only the warnings"

The map speaks only when it has something to warn about. Easy, rough and unknown all draw as Street v1. Two cues, both warnings:

| | clear | faint |
|---|---|---|
| **unknown, easy, rough** | `[3, 1.5]` (Street v1) | `[3, 4]` |
| **hard** | `[1.5, 1.5]`, `#7a1e10`, width + 0.4 | `[1.5, 4]`, same |

- Colours and night as A. Informal as A. Zooms as A.
- **This variant reads the brief's rule differently**: it has no "known easy" look at all, so an untagged trail cannot look like one; it looks like "a trail with nothing flagged". Whether that satisfies the owner is question 2, and B stands or falls on the answer.

### Variant C, "colour bands" (the Swiss signposts, in this map's colours)

Difficulty by **colour weight** inside the red-brown family, visibility by gap as in A. Dash length is not used for difficulty.

| Group | Day | V1 night (inferred) | Dash clear / faint |
|---|---|---|---|
| **easy** | `#d4641f` (warmer, lighter) | `#ee7e39` | `[3, 1.5]` / `[3, 4]` |
| **unknown** | `#a8452a` (Street v1) | `#ed8a6f` | same |
| **rough** | `#7f2a1a` (darker) | `#fca797` | same |
| **hard** | `#2b1a14` (near-black), width + 0.4 | `#efded8` | `[1.5, 1.5]` / `[1.5, 4]` |

- Informal as A. Zooms: colours from z13, gaps from z14.
- Risk I can see without a render: the easy colour `#d4641f` sits in the same warm family as the major road `#f6d68f` and highway `#f0b95c` (read: `forager-street.mjs:37-39`); at z13 where roads are thin it might read as a small road. And four steps of one hue is a ramp a person must learn; at night the ramp inverts so that *hard* is palest, which is right for a warning but is the opposite of "darker = harder" they learnt by day.

## (c) What a person sees at a glance, and what each asks them to learn

- **A.** At a glance: trails are still the red-brown dashes; some are long confident dashes, some are a dark short stutter, some have big gaps. Most people read "long smooth dash = easy going, short broken = rough, gappy = comes and goes" without being told, because the line's texture is the trail's texture; that is the whole bet. The one thing to learn is that **dark short dash is the real warning** ("hands needed"), and it is also the one line brighter than the rest at night. Nothing to learn for untagged: it is the line they already know. No legend needed; a one-line hint in the Layers sheet would do no harm (question 6).
- **B.** At a glance: the map looks exactly like Street v1 until a trail is hard or faint, and those two jump out. Nothing at all to learn for the common case. What it asks: that the person accepts "nothing flagged" as "nothing known", not "easy". That is a thought, and it is the thought the brief's rule is written against.
- **C.** At a glance: the trails are several shades of red-brown and a near-black. A hiker who knows the Swiss signposts will not see yellow and blue here, so the convention does not carry over by itself; the ramp "lighter = easier, darker = harder" has to be learnt, and at night it flips. A legend would be needed. The Swiss cut is still the right grouping; it is the *colour* that does not transfer onto a map whose roads are already warm.

## (d) Recommendation: Variant A, with the Swiss grouping

- It needs the least learning for the most common case (untagged: unchanged), and its two cues are the two cues every hiking map in the prior art uses, in the pairing (dash = difficulty, gap/strength = visibility) two of the three open maps chose.
- Its warning survives night by construction: `#7a1e10` inverts brighter than the base trail, and gap is not a colour, so the night recolour cannot dim it. Opacity, the obvious alternative for "faint", was rejected for exactly that reason: the app has one style recoloured for night, so an opacity set for day would also apply at night, where the base trail is already at 2.43:1 on forest.
- It keeps the one colour family the owner already liked ("Pretty slick", −1343) and adds a single darker member of it.
- It costs one dash grammar at z14 and up and nothing at z13, where the renders show there is no room.
- Not chosen: B, because it depends on a reading of the brief the owner has not ruled on; C, because colour is the scarce resource on this map (roads warm, water blue, forest green, marks magenta) and a four-step ramp spends it on the least common axis.

## (e) Questions for the owner, with options

1. **Which variant goes to the coder for a render?** (a) A only, recommended; (b) A and B side by side, so the "only warnings" reading can be judged on real trails; (c) all three.
2. **Must a known-easy trail look different from an untagged one?** (a) Yes: a tagged easy trail gets the long confident dash, so an untagged trail is never mistaken for a trail someone has graded, recommended; (b) No: "nothing flagged" is enough, which is Variant B.
3. **Which difficulty bands?** (a) The Swiss signpost bands, T1 | T2–3 | T4+, recommended, because that is the scale's own cut and the hard band is the one that is a warning; (b) the two web maps' bands, T1–2 | T3–4 | T5–6, which puts "mountain hiking" with "easy".
4. **From which zoom do the differences show?** (a) z14, with the hard colour from z13, recommended, because z13 is already a tangle; (b) everything from z13.
5. **Informal at night.** `#b9876b` inverts to about `#9b694d`, about 1.3:1 on the night forest and 1.85:1 by day (inferred by hand). "Faint" is ruled, but 1.3:1 is closer to gone than faint. (a) Allow a named night colour for informal under −1344, the coder to propose one at about 2:1 on forest, recommended; (b) keep the inversion and judge it on the phone; (c) lift the day colour a little as well.
6. **A legend?** (a) None; the line's texture is the legend, recommended for A; (b) one line of text in the Layers sheet ("long dash easy, short dash rough, dark hard, gaps faint"); (c) a small key. C needs (b) or (c).
7. **Where does `intermediate` visibility go?** (a) With clear, as the open web maps do, recommended; (b) with faint, so one in five tagged trails gets the gap.
8. **One ruling to confirm, not a choice**: the brief says "informal trails stay dotted and faint, in the reserved colour" and Elder's review has "dotted for informal/indistinct"; this design keeps dotted for informal *only* and gives indistinct (faint visibility) the gap instead, so the two never look alike. If the owner wanted faint-visibility trails dotted too, A changes.

## (f) Render requests for the coder (pi-18 Part B)

1. **Each chosen variant** at the four places, z13, z14, z15, day and night, by the Part A method; plus one **z16** at Mt Rainier day, so the dash grammar is seen at the walking zoom the app allows (max 18, −1289).
2. **Flattop re-centred on its trail** (the owner: "Fold into step 3 (Recommended)"), at z13, z14 and z15, day and night. Flattop's z13/z14 tile carries the `trail_visibility=bad` segment and three `informal=yes` segments, so this one render shows the faint cue and the informal dots on real data; say in the report which trail is which.
3. **Lake Louise, z14 and z15, framed to include the `alpine_hiking` segment** north-east of the village (one such path in the z13 tile, observed), so the hard cue is seen once on real data.
4. **A swatch strip**, not a map: every dash-and-colour combination of the variant at z14 and z15 width, drawn over forest `#a9d39b`, grass `#dde9c1`, the grey ground and, at night, `#436d35`, `#2d3911`, `#24211c`, side by side with the Street v1 base and the informal dots. This is how the owner tells the patterns apart without hunting for them, and it is the cheapest render in the set.
5. **The contrast check** (`check-street-night.mjs`) run on each variant with the new pairs added: hard `#7a1e10`/`#ffada0`, informal `#b9876b`/its inversion, on the three night grounds and the day forest; report the informal figures even though they will fail the floor, and do not raise or widen the floor to pass them.
6. **Report, measured not assumed**: whether `line-dasharray` is data-driven in the pinned engines or one layer per group is needed; the layer count; and the V1 night values of the new colours from the app's own `nightColorOf`, in place of my hand figures.
7. Keep every render in scratch, as before; they show place names.

## Confirmed vs inferred; could not determine

- **Confirmed (observed)**: all 28 renders; the tile contents at the four places z12 to z15 from the running local server, including every count above; the style values cited by line.
- **Inferred**: every night hex and contrast figure for the new colours (hand arithmetic with the app's published formula); that per-layer dash is needed; that `#d4641f` would compete with roads at z13.
- **Could not determine**: how any of this looks drawn, since I built nothing (by the owner's ruling); how Firefox's dash rendering compares with the phone's (the Part 1 report says the phone is the final word); whether the one `alpine_hiking` and one `bad` segment are long enough on screen to judge the cue from.
- **Not read**: `docs/plans/understory-design-system.md` and `qa-philosophy.md` (app-screen items on the reading list, not map work); the Part 1 report's Q2 to Q4, Q6, Q7 and Q9 (Worker, deploy, app), which do not bear on path styling.
