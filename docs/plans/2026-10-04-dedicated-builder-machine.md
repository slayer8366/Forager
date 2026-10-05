# A dedicated builder machine: plan for later

**Status: a plan, nothing built, nothing dispatched.** Written by the planner on 2026-10-04 (Pacific),
at the owner's word: "Save that test for later, it's not ready for that yet. But save it with the
numbers we ran today as part of a plan for having a dedicated builder machine. It can also be used
to build the forecast system when it's implemented. I'll get it set up with an external fan and
vented surface for cooling when that happenz"

**Base:** `origin/pi-build` at `991bca8f`.

## Why

The Raspberry Pi 5 builds the Oregon + Washington map today, and also serves tiles. The owner's
long-run goal is a far larger coverage. The Pi cannot build at that scale (see the figures below).
The owner's current laptop becomes a spare when they upgrade. The plan is to use it as a dedicated
builder: it builds the maps, the finished files are copied to the Pi, and the Pi serves them.

The owner also intends it to build the forecast system (the separate `slayer8366/forager-forecast`
repository) once that system is implemented. This plan does not size the forecast work: nothing
here has read what that build will need.

## The machine (owner-stated, not measured)

| | |
|---|---|
| Storage | 256 GB SSD |
| Processor | Intel Core i7, 10th generation (the exact model, and so the core count, is not known) |
| Memory | 12 GB |
| Operating system | Ubuntu |
| Running time | unlimited; it is a spare. "Whether it survives is up to the quality of the laptop." |
| Cooling | the owner will add an external fan and a vented surface when it is set up |

A Claude bridge environment already exists for this laptop (`zynergy-labs-HP-Laptop-17t-by200`,
created 2026-10-01). Nothing has been run on it for this plan.

## The numbers from 2026-10-04

### Measured

**Today's Pi build** (run `20261004T214038Z`, `/srv/forager-build/out/current/manifest.json`):

| | |
|---|---|
| OSM input, Oregon + Washington (Geofabrik, 2026-10-03) | 618.1 MB (254.2 MB + 364.0 MB) |
| Map output, `forager-orwa.pmtiles`, z0–15 with trail details | 938.9 MB |
| Routing output, four `.rd5` squares | 76.2 MB |
| Auxiliary sources (land, water, land cover, Natural Earth, QRank, fonts) | 2.47 GB, fetched once, reused |
| Planetiler | 959 s, peak RSS 5.65 GiB (includes memory-mapped temporary files) |
| BRouter cut | 562 s, peak RSS 2.0 GiB |
| Whole run | about 28 minutes |

These give two ratios, used for every estimate below: **map ≈ 1.52 × OSM input**, and
**routing ≈ 0.123 × OSM input**. Both are from one rural-heavy region.

**Published source sizes**, read on 2026-10-04 from HTTP `Content-Length` headers:

| Source | Size |
|---|---|
| Planet OSM (`planet.openstreetmap.org`) | 95.1 GB |
| Geofabrik North America (includes Greenland) | 19.5 GB |
| Geofabrik US | 12.2 GB |
| Geofabrik Central America (includes the Caribbean) | 0.79 GB |
| Geofabrik South America | 4.13 GB |
| Geofabrik Europe | 35.1 GB |
| France 5.09 · Germany 4.85 · UK 2.27 · Italy 2.24 · Spain 1.49 · Netherlands 1.40 · Austria 0.81 · Belgium 0.70 · Switzerland 0.55 · Denmark 0.50 · Portugal 0.42 · Ireland and Northern Ireland 0.41 · Luxembourg 0.05 · Liechtenstein, Andorra, Monaco under 0.01 each | GB |
| Protomaps' own daily planet basemap, `20261004.pmtiles` (z0–15, no trail details) | 138.6 GB |

The Protomaps planet's ratio to the planet OSM file is 1.46, close to our 1.52. That is the only
outside check on the scaling.

### Estimated (scaled from the ratios above, untested)

| Coverage | OSM input | Map | Routing | Total |
|---|---|---|---|---|
| Oregon + Washington (measured) | 0.62 GB | 0.94 GB | 0.08 GB | ~1 GB |
| US | 12.2 GB | ~18 GB | ~1.5 GB | ~20 GB |
| North America | 19.5 GB | ~30 GB | ~2.4 GB | ~32 GB |
| Central + South America | 4.9 GB | ~7.5 GB | ~0.6 GB | ~8 GB |
| The Americas | 24.4 GB | ~37 GB | ~3 GB | ~40 GB |
| Western Europe, core (UK, Ireland, France, Benelux, Germany, Switzerland, Austria, Liechtenstein, Monaco) | 16.1 GB | ~25 GB | ~2 GB | ~27 GB |
| Western Europe, wide (core + Spain, Portugal, Italy, Denmark, Andorra) | 20.8 GB | ~32 GB | ~2.6 GB | ~34 GB |
| Europe | 35.1 GB | ~53 GB | ~4.3 GB | ~58 GB |
| The Americas + wide Western Europe | 45.2 GB | ~69 GB | ~5.6 GB | ~74 GB |
| Planet | 95.1 GB | ~140–150 GB | ~12 GB | ~150–160 GB |

Europe's data is denser than Oregon's and Washington's, so its figures could be off by about 5%
either way. None of these has been built.

**Cloudflare R2 storage, if served or backed up there:** about 1.5 cents per GB-month above the
free 10 GB. **That price is from the planner's knowledge, not read from Cloudflare on this date.**
Per-request charges depend on use and are not estimated. The owner has said no to going over the
free 10 GB for now, while they budget.

## The goal the owner named

The Americas and Western Europe, built in thirds so each build stays small:

| Third | Total, estimated |
|---|---|
| North America | ~32 GB |
| Central and South America | ~8 GB |
| Western Europe (wide) | ~34 GB |

The thirds are uneven: two are about four times the third.

## What the laptop can likely do (inferred, untested)

- **Disk:** about 180–200 GB usable after Ubuntu, not measured. One third at a time fits: the
  largest is likely about 100–130 GB at peak, being its source, Planetiler's temporary files and its
  finished output. Each third's source and temporary files are deleted after the build, and only the
  finished files are kept and copied to the Pi. Two thirds at once would be tight, and the planet
  would not fit.
- **Pi disk, for the finished files:** 154 GB free on 2026-10-04, 234 GB total. The three thirds come
  to about 74 GB, so they fit, alongside the 8.3 GB `us.pmtiles` served today.
- **Time:** several times faster than the Pi. If time grows in line with input, a large third is a
  few hours and Central + South America well under one. That is a guess.
- **Memory:** 12 GB against the Pi's 8 GB. One third at a time is expected to be fine. Planetiler's
  memory at 20 GB of input on 12 GB has not been checked.
- **Heat:** the risk on a laptop at full load for hours. Log temperature and throttling every 30 s,
  as the Pi's builds do now.

## Order of work, when the owner is ready

Each step needs the owner's go, and **any build needs the owner to say "build"**. These are the
standing rules for Pi coders, `no-build-without-explicit-go`, applied to the laptop.

1. **Read-only check on the laptop:**
   - free disk;
   - exact processor model and core count;
   - memory;
   - Java version;
   - whether temperature sensors can be read.
2. **Adapt the build scripts** (`map-build/`) to run on the laptop: paths, the build user, and no
   systemd timer at first. **Add a copy-to-the-Pi step** that checks each file's SHA-256 on arrival.
   Publishing on the Pi stays a by-hand swap, as the owner chose on 2026-10-04.
3. **Small test:** rebuild Oregon + Washington on the laptop. Compare it with the Pi's verified
   build of the same 2026-10-03 data, using `verify-orwa.sh`. Copy it to the Pi.
4. **Mid-size test:** Germany (4.85 GB input), measuring time, peak memory, peak disk and
   temperature. This checks the scaling above before a full third is tried.
5. **The thirds**, one at a time.

## Decisions left open

- **One map file or several.** Thirds produce several files: either the app and the Worker choose
  among them, or a join step merges them, which needs extra disk while it runs.
- **Where the weekly build runs** once the laptop builds. The Pi's timer was turned on 2026-10-04 for
  Oregon + Washington; its first run is 2026-10-11.
- **Cloudflare storage for anything over 10 GB**, held while the owner budgets.
- **The forecast system's needs** on the same machine, unread.
