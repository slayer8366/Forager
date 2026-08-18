# Forager Navigator — plan

Vetted against `slayer8366/Forager` (README + CLAUDE.md, read 2026-08-18)
and re-vetted for handoff on 2026-08-18. Companion document:
[`maplibre-migration.md`](./maplibre-migration.md), which specs the
renderer change this plan depends on.

**Status: ready to send to a coder.** The two decisions this plan
originally left open — the trip-entity fork (§1) and the offline-strategy
fork (`maplibre-migration.md` §3) — are resolved below, by the project
owner, on 2026-08-18. Both resolutions are recorded as decisions with their
rejected alternative, per CLAUDE.md's documentation rule, not applied
silently.

## 0. Verification status

Per the project owner (2026-08-18): the work described in the README's
**"Not yet verified"** section as pending hardware checks — rendering on a
device, the `clipToBounds()` clip, whether topo tiles render at all, and
the overlay-colour-legibility question — is confirmed verified on
hardware. Treated as fact per the owner's explicit instruction. **There is
no verification gate in front of Phase 1.**

One carry-over for the coder's first commit, not a formality: **update the
README's "Not yet verified" section** to drop the now-stale caveats listed
above. This plan does not draft that replacement text itself — doing so
would mean inventing specific on-device findings (what exactly was seen,
under what conditions) that weren't captured in this session, which is the
exact fabricated-plausible-value failure CLAUDE.md forbids. The coder (or
the owner) should replace each stale caveat with the real observation, not
just delete it. The overlay-colour question in particular had a decision
attached — colours deliberately not changed pending a look at hardware —
so record the actual outcome, including "they read fine, no change
needed," not just that the question is closed.

## 1. What already exists

Verified directly against the repository (`slayer8366/Forager`,
`claude/forager-navigator-plan-b4oe3s`, 2026-08-18) — every row below was
confirmed by reading the named file, not inferred from the README alone.

| Draft item | Already in repo |
|---|---|
| MGRS coordinate support | `domain/MgrsConverter.kt`, `domain/model/MgrsCoordinate.kt` |
| Substrate, host, associated taxa | `HostSubstrateSection`/`Association` (`domain/model/MushroomLogEntry`) |
| Planned search areas | `domain/model/PlannedTrip.kt`, `data/local/PlannedTripEntity.kt` + map long-press |
| USGS Topo offline download | `map/OsmdroidOfflineMapRepository`, `map/PersistentTileWriter` |
| Find / specimen pins | Mushroom log entries, same long-press dialog (`LongPressActionDialog`) |
| Offline result reuse | `domain/SearchCacheRepository`, five-entry LRU with age banner (`RoomSearchCacheRepository`) |

Specifying any of these as new work produces duplicate implementations.

The real logging gap is narrower than a first read suggests. It is not
"add habitat tags" — those exist (`HostSubstrateSection`). It is
geographic context the log has no field for: slope, aspect, elevation
band, canopy, moisture, disturbance, burn year, plus a link from an entry
to a track point or waypoint. Specify it as an **eighth section type**,
`SiteContextSection`, following the existing `Observed`/`Feature`
three-state discipline the other seven sections already use. Not a
rewrite of `HostSubstrateSection`.

### Decision: PlannedTrip vs. a richer trip object

`PlannedTrip` deliberately has no rename-after-creation flow — a recorded
decision (see `domain/model/MushroomLogEntry`'s doc comment, which
contrasts the mushroom log's real edit path against it). An earlier draft
proposed folding a richer trip object — party, emergency contact,
checklists, gear, weather snapshot, exportable brief — into the same
entity. Per CLAUDE.md, silently growing one into the other is the unmade
architectural decision that gets surfaced, not picked.

**Resolved: new `FieldTrip` entity.** `PlannedTrip` is left exactly as-is,
including its no-rename-after-creation behavior. `FieldTrip` is a new,
separate entity — different fields, different lifetime, own Room table and
migration — scheduled in Phase 4 (§7). Rejected alternative: expanding
`PlannedTrip` itself, which would have required accepting an editability
reversal on an entity whose immutability was a deliberate prior choice, for
no benefit over a second entity.

## 2. The renderer, and what it changes

osmdroid is the ceiling. It draws raster tiles. Land boundaries, styled
terrain, high-zoom offline basemaps, and queryable geometry all want
vector. The draft's "access and land context" work priced correctly as a
new subsystem — geometry storage, rendering, offline packaging,
point-in-polygon — but most of the engine half of that disappears on a
vector renderer.

See [`maplibre-migration.md`](./maplibre-migration.md) for the full spec,
now written and in `docs/plans/`, including:

- What survives (`MapSlot`, `Basemap`, `OfflineMapRepository`, all of
  `domain/`) and what gets spent (`SightingsMap` and its clip-to-bounds
  fix, the osmdroid-reading tests, the dashed connector, the overlay-colour
  decision — all of which must be **re-confirmed on the new renderer**,
  not assumed to carry over just because they were confirmed on osmdroid).
- The offline-strategy fork, now resolved (see below).
- The sequencing split that lets track recording proceed in parallel with
  the renderer swap rather than behind it (Phase 1a / 1b, §7).

### Decision: offline strategy

MapLibre's PMTiles support and its offline pack manager do not compose
(`maplibre-migration.md` §3). Three options were on the table:

- **Option A — pre-built regional extracts.** No server to run; the
  user-drawn arbitrary-region long-press picker regresses to picking among
  fixed pre-built regions.
- **Option B — own tile endpoint.** Keeps the arbitrary-region picker
  working; the project starts running and maintaining server
  infrastructure it does not have today.
- **Option C — hybrid.** Coarse extracts as an offline floor plus an
  online-time endpoint for custom regions; more capability, two offline
  code paths and failure states to maintain.

**Resolved: Option C, the hybrid.** Coarse pre-built regional extracts as
the always-available offline floor, plus a self-hosted tile endpoint for
the existing user-drawn arbitrary-region download picker — both built in
this migration, not staged into a later one.

Rationale, from the owner: deferring the custom-region path (Option A
alone) means re-touching the same set of files a second time later —
offline package manager, readiness-state UI, region picker — on a
codebase that has grown around the map layer in the meantime. The
custom-region capability isn't new scope; it's the long-press download
gesture the app already ships today, so building it now rather than
regressing it and rebuilding it later is judged cheaper end to end.
Rejected alternative: Option A alone, originally recommended for adding no
infrastructure; rejected because the region-picker regression it required
was judged a worse long-term cost than standing up the server now.

Accepted consequence, explicitly: this is the first standing
infrastructure the project operates (a live tile-serving endpoint with
its own uptime, cost, and abuse-surface considerations — see
`maplibre-migration.md` §7), and the offline readiness screen now needs
to distinguish two offline states (coarse regional extract vs. custom
downloaded pack) rather than one.

What the migration does not solve: licensing. See §8.

## 3. Layers, coverage, and scope

Cut: private parcel boundaries. "Where the data license permits
distribution" was doing impossible work in the draft. Nationwide parcel
data is a commercial product; competitors license it. There is no free
redistributable equivalent. If it returns, it returns as a budget
decision (§8), not an engineering task.

Phase 3 layer catalogue, restricted to public-domain redistributable
sources: PAD-US (public land ownership and management) · USFS roads,
trails, MVUM · NHD hydrography · slope and aspect derived from USGS 3DEP ·
wilderness, park, refuge, and special designations · NIFC/USFS fire
perimeters · LANDFIRE disturbance.

Every one of those is United States only. That is a coverage boundary,
not an objection. A US-only layer is worth building for the users it
serves; outside coverage the feature is simply unavailable. It is the
same trade already made deliberately with USGS Topo — kept as a
first-class basemap with its limit stated rather than hidden.

What the boundary does require is that unavailable reads as unavailable.
An empty boundary layer over real terrain is worse than a missing
feature, because it looks like an answer: no polygon here reads as no
public land here.

- Out-of-coverage layers show as **unavailable for the region** with the
  dataset's stated extent — not toggled on and rendered empty.
- The offline readiness screen gains **not covered** as a fifth state
  alongside available / stale / partial / not downloaded. Different fact,
  different user response. Following the Option C decision in §2, "not
  downloaded" itself now covers two distinct offline paths — a coarse
  pre-built regional extract and a custom user-drawn downloaded pack —
  and the screen must say which one a given area has, not just whether
  something is offline.
- Coverage extent goes on the layer card beside source agency and
  version. `Basemap`'s doc comment records the precedent: coverage limits
  are stated outright rather than detected and silently fallen back on —
  partly because the failure isn't reliably detectable anyway.

## 4. CLAUDE.md compliance corrections

Cut: estimated travel time on the compass screen. Permitting it "only if
the app clearly states its assumptions" is not a real defense — it is a
time estimate over a straight line across terrain the app has no data
for. That is the fabricated-plausible-value failure CLAUDE.md names.
Bearing, straight-line distance, elevation difference. No ETA, not with a
caveat.

Off-track alerts and the overdue check-in timer carry a reliability claim
the app cannot keep. Doze, app standby buckets, and OEM battery
management can silently suppress a local alarm. A check-in timer someone
relies on that does not fire is a safety failure, not a bug.
Field-verifying on one phone does not settle behaviour on other people's
devices. These ship only with:

- A setup-time check that the notification actually fires, routing the
  user to the battery-optimization exemption if it doesn't.
- Wording stating what it is: a local phone reminder, not a monitored
  service, and not a substitute for telling someone where you went. Same
  species of honesty as ownership is not permission.

Fire and smoke follow the `GetConditionsUseCase` precedent, not the
offline-package precedent. The project already decided that "as of today"
readings are deliberately not cached, because replaying a stored rainfall
total offline presents a days-old reading as current. A fire perimeter is
the same class of data with a far higher cost of being wrong. Online
only, timestamped, hidden entirely when offline, never bundled into a
trip package.

Extend `ForagingAreaLabels` into a single navigation-disclaimer source.
The project already solved this once — one file holds the "not a walking
route" wording so the info window and the caption cannot drift. Every new
disclaimer goes there. Small change; prevents the exact failure the
draft's honesty depends on.

## 5. Data layer

Track recording is a write pattern this database has never seen.
Everything stored today is low-frequency: a search result, a trip, a log
entry autosaved on field change. A track is a row every few seconds for
hours. Do not route it through the mushroom log's autosave path.
Dedicated DAO, batched inserts, and an explicit retention decision (how
many tracks, pruned how). Measure insert cost before shipping — this is
one new feature where a headless test establishes something real.

**Migrations.** `ForagerDatabase` is at v4 with the hand-written-migration
precedent set at 3→4 and `exportSchema` on (verified). Tracks, waypoints,
and `FieldTrip` are v5+. Recorded tracks and field notes are irreplaceable
in exactly the way log entries were — `fallbackToDestructiveMigration()`
is off the table permanently, and each migration gets a
`MushroomLogMigrationTest`-style round trip.

Point-in-polygon belongs in `domain/`, not the renderer (also specified in
`maplibre-migration.md` §5). Feature-query APIs answer questions about
rendered geometry — what's on screen, at this zoom, after style filtering.
"Which management unit contains this coordinate" is a data question whose
answer must not change because the user zoomed out. Hand-rolled alongside
`GeoDistance` and `Dbscan` (both verified present in `domain/`), for the
third time and the same reason.

## 6. Privacy

The draft's private/generalized coordinate split, sensitive-location
defaults, and deliberate-confirmation export are right and ship as
written. Two additions:

A privacy-preserving export that rounds coordinates but keeps timestamps
and the recorded track is not private. A track leading to a rounded point
still leads to the point. Rounding applies to the whole bundle, and a
find export must be able to omit the associated track entirely.

One rounding function, one place — the `ForagingAreaLabels` and
`findSoakingEvents` pattern (both verified present). Never a second copy
of the logic carrying the safety property.

## 7. Phasing

**Phase 1a — field capture (map-independent; start immediately)**
Foreground location service and notification · track sampling, accuracy
filtering, battery modes · track and waypoint schema plus migrations ·
track statistics · GPX import/export · coordinate formats and MGRS
display (extends existing `MgrsConverter`) · compass plumbing ·
return-to-start bearing computation. All domain, data, and service work;
all headless-testable; none of it cares which renderer draws the result.

**Phase 1b — renderer migration (parallel to 1a)**
Per `maplibre-migration.md`, offline strategy resolved to Option C (§2).
Basemap first and verified on hardware before anything lands on it;
overlays re-implemented and the dashed connector re-confirmed; offline
packages rebuilt as both the pre-built regional extract path and the
self-hosted tile endpoint behind the user-drawn region picker; osmdroid
deleted last.

**Phase 1c — converge**
Track breadcrumbs, waypoint markers, offline readiness screen, and the
return-to-vehicle screen — written once, on the new renderer. Off-track
alert and check-in timer per §4's delivery check.

**Phase 1.5 — the differentiator**
`SiteContextSection` · log entries linked to track points and waypoints ·
capture-position vs. corrected-position separation · sensitive-location
controls and privacy-safe export. Cheap, independent of the layer
pipeline, and the thing nobody else builds: Gaia doesn't care about
mycology, iNaturalist isn't a field navigator.

**Phase 2 — terrain and hydrography**
Hillshade, slope, aspect, elevation profile, NHD water. Blocked on a
finding `maplibre-migration.md` §9 flags as unresolved: where raster-DEM
data comes from and whether it may be redistributed offline. Establish
that before scheduling.

**Phase 3 — access and land context**
Vector pipeline (clip, simplify, version), PAD-US, USFS roads/trails/MVUM,
designations, layer cards with provenance and stated extent, the not
covered readiness state, point-in-polygon "what am I standing in." It
sits here because of what the data pipeline costs to build, not because
of who it serves. US coverage is not a reason to demote it.

**Phase 4 — trip and record**
`FieldTrip` entity per §1's resolved decision (party, emergency contact,
checklists, gear, weather snapshot, exportable brief — `PlannedTrip`
untouched) · checklists and regulation-reference cards · pre-trip weather
snapshot · exportable trip brief · fire perimeters and LANDFIRE
disturbance (online-only for current fire, per §4) · post-trip review
linking tracks, waypoints, finds, and photos into one field record.

**Deferred indefinitely** — unchanged from the draft: turn-by-turn or
"best/safe/legal/fastest route" claims · harvest-permission determination
from ownership overlays · AI identification or edibility advice ·
fruiting forecasts from uncalibrated weather and observation counts ·
automatic public sharing of exact productive sites · shared trip packages
and live location sharing (need accounts, a server, and a security
posture this project has not scoped).

## 8. Licensing, honestly scoped

Two separate questions:

- **Basemap data:** the data itself is free and openly licensed —
  OSM-derived vector tiles under ODbL, whose obligation is attribution,
  not money, and `Basemap` already owns attribution as data (verified in
  `ui/map/Basemap`). This is a licensing question with no negotiation
  attached, and it stays that way for the pre-built-extract half of §2's
  Option C decision. The self-hosted tile-endpoint half is a separate,
  real operating cost — not a licensing fee, but ongoing hosting/egress
  spend for the server this migration now stands up (see
  `maplibre-migration.md` §7's infrastructure risk). Small at this
  project's scale, but no longer zero, and worth stating as a number once
  a hosting choice is picked.
- **Parcel data:** a real spend, or nothing. Nationwide private-parcel
  boundaries are a commercial dataset. This is the one place where
  matching what a funded competitor offers requires funding. It is a
  budget decision with a vendor conversation attached, made deliberately
  if and when the rest of the app justifies it — not scheduled as
  engineering.

Everything else in the Phase 3 catalogue is public domain.

## 9. What the draft got right

Unchanged, and not to be softened:

- "Straight-line bearing; not a walking route," extended from the
  existing foraging-area language (`ForagingAreaLabels`, verified).
- Ownership boundary ≠ permission to enter or harvest, as non-negotiable
  wording.
- Layer cards carrying source agency, dataset version, coverage, last
  update, and known limitations.
- Never showing a stale or empty layer as authoritative.
- Raw GPS altitude retained separately from corrected display altitude.
- Refusing to build an identifier or a fruiting forecast.

These are why the app is worth building rather than installing something
else, and each is consistent with what the codebase already does.

## 10. Handoff checklist

- [x] `maplibre-migration.md` written and committed to `docs/plans/`.
- [x] Trip-entity fork resolved: `FieldTrip` as a new entity (§1).
- [x] Offline-strategy fork resolved: Option C, the hybrid — pre-built
      regional extracts as the offline floor plus a self-hosted tile
      endpoint keeping the user-drawn region picker intact, both built in
      this migration rather than staged (§2). Accepted: this is the
      project's first standing infrastructure.
- [x] Every "already exists" claim (§1 table, §5, §8, §9) verified against
      the actual repository, not inferred from the plan draft.
- [ ] **README's "Not yet verified" section update** — owner/coder to
      replace stale caveats with the real on-device findings (§0). Not
      drafted here to avoid inventing specifics not actually captured.
- [ ] Phase 2's raster-DEM licensing/availability question
      (`maplibre-migration.md` §9) — establish before scheduling Phase 2,
      not before starting Phase 1a/1b/1.5.

Phases 1a, 1b, and 1.5 have no remaining open decisions and can start
immediately in parallel. Phase 2 has one standing prerequisite (DEM
licensing). Phases 3 and 4 are unblocked in scope but sequenced after 1–2
by design (§7).
