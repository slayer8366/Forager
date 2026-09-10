# Coder task: Mushroom Log (field record + iNaturalist upload)

Planning doc from the EGD planner session — a task spec for the coder, not a
replacement for the repo's real `CLAUDE.md`, whose standing principles govern
everything below.

Independent of the Seasonal Visualizer work (`feature/seasonal-visualizer`).

> **Coder's note (corrected before implementation):** this doc's original
> base-commit line said "branch off `main` (`ed81603`, PR #13 merged)". By
> the time this was implemented, the Seasonal Visualizer had also merged
> (PR #14), so implementation branched off current `main` directly rather
> than `ed81603`. See also the corrected "Version ordering" note under
> Persistence and the corrected Navigation section below — both were
> re-verified against the `main` this actually built on, not assumed from
> this doc's original text.

## What this is, and what it deliberately is not

A structured field record for mushroom finds: the forager writes down what
they observed, the app stores it faithfully, shows it back, and can upload it
to iNaturalist.

**The app never identifies the mushroom.** Decided explicitly by the project
owner. No species suggestion, no candidate list, no "likely," no confidence
score, no narrowing-by-elimination. The recorded features are exactly the
input a dichotomous key consumes, and the species this key separates include
ones that kill people — so the refusal is a safety property of this feature,
not a scope cut to revisit casually. Anything that reads as the app forming
an opinion about species is out of scope; if a future request asks for it, it
is a new decision with the owner, not an extension of this one.

The user can still name their own identification (see `ownIdentification`
below) — that is the forager's own claim, stored as theirs, never generated.

## Decisions already made (do not re-litigate; from the project owner)

1. **No identification.** Above.
2. **Photos: camera + gallery.** Take a photo in-app, or attach existing ones.
3. **Tiers in v1: field-observable + spore print.** Cap, hymenophore, stipe,
   veil, context/flesh, host & substrate, and spore print. **Microscopy
   (spore morphology, basidia/asci, cystidia) and chemical reagents (KOH,
   ammonia, FeSO4) are explicitly deferred** — they need a microscope and
   reagents. Design the model so an "advanced" tier can be added later
   without reshaping what exists, but do not build those fields now.
4. **iNaturalist upload is planned for now, not later** — the model carries
   sync state from the start rather than being retrofitted.

## The central modeling rule: three states, not two

**"No volva" and "I didn't look at the base" are different facts.** Absence
of a volva is a positive, diagnostic observation; not having checked is no
information at all. A nullable field collapses them, which is precisely the
fabricated-plausible-value failure `CLAUDE.md` forbids — and the one that
matters most in a record whose whole purpose is careful observation.

Two distinct types, because two distinct kinds of characteristic exist:

```kotlin
/** A characteristic that necessarily has some value if the part exists (cap shape, gill attachment). */
sealed interface Observed<out T> {
    data class Recorded<T>(val value: T) : Observed<T>
    data object NotObserved : Observed<Nothing>
}

/** A feature that may genuinely be absent, where absence is itself diagnostic
 *  (volva, annulus, latex, bruising, cap decorations). */
sealed interface Feature<out T> {
    data class Present<T>(val value: T) : Feature<T>
    data object Absent : Feature<Nothing>
    data object NotObserved : Feature<Nothing>
}
```

Using `Observed` where absence is meaningful (or vice versa) is a modeling
error — the split exists so the illegal state cannot be written down. Which
fields are which is listed per-section below.

**Inapplicability is a third distinct thing.** If the hymenophore is pores,
"gill attachment" is not unobserved — it does not apply. Model this by
nesting dependent fields under the choice that makes them applicable (e.g.
`HymenophoreDetails` as a sealed type with `Gills(attachment, spacing, edge)`
/ `Pores(...)` / `Teeth` / `SmoothOrWrinkled`), so an inapplicable field has
no slot to be wrongly filled in, rather than adding a fourth `NotApplicable`
case to every field. Same for a stipe recorded as absent — its interior and
base have no meaning and must not be storable.

Tests must cover this directly: an entry with pores must have no way to carry
a gill attachment, and `NotObserved` must never render or upload as absence.

## Field structure (v1)

All controlled vocabularies are Kotlin `enum class`es in `domain/model/`,
each carrying a human-readable `label`, exactly as `TaxonFilter` does. Every
section additionally carries a free-text `notes: String` — real specimens sit
between enum values ("between convex and flat"), and the enum plus a note is
honest where forcing a single enum value is not. The note supplements the
enum; it never replaces it.

- **Cap (Pileus)** — `shape` (conical, convex, flat, umbonate,
  infundibuliform) `Observed`; `surface` (viscid, dry, velvety, glabrous,
  fibrillose) `Observed`; `decorations` (warts, scales, patches) `Feature`;
  `margin` (striate, sulcate, appendiculate, inrolled, uplifted) `Observed`.
- **Hymenophore** — sealed `HymenophoreDetails`: `Gills(attachment: free /
  adnate / adnexed / decurrent / sinuate, spacing: crowded / close / distant,
  edge: smooth / serrated)`, `Pores`, `Teeth`, `SmoothOrWrinkled`. The whole
  thing is `Observed<HymenophoreDetails>`.
- **Stipe** — sealed: `Absent` is a real, recordable state (the owner's list
  includes it) vs. `Present(position: central / eccentric / lateral,
  interior: hollow / stuffed / solid / fibrous, base: bulbous / radicating /
  abrupt / pointed)`. Wrapped as `Observed`.
- **Veil remnants** — `annulus` (ring, skirt, cortina/hair-zone) `Feature`;
  `volva` (sack, cup, concentric rings) `Feature`. Both are presence-optional
  and both are diagnostic in their absence — these are the fields the
  three-state rule exists for.
- **Context / flesh** — `texture` (brittle, chalky, tough, woody, gelatinous)
  `Observed`; `colorChangeOnCutting` `Feature<String>` (free text for the
  colour, since the space of colour changes is not a closed set);
  `exudate` (latex) `Feature<String>`.
- **Spore print** — `Observed<SporePrint>` where `SporePrint` carries a
  colour (white, cream, pink/salmon, ochre, rust, chocolate brown, purple-
  brown, black, plus `Other(text)`) **and the date it was read**, which will
  usually differ from the find date. See "deferred observation" below.
- **Host & substrate** — one sealed `Association` covering the owner's
  mycorrhizal case and the saprotrophic case the original list did not:
  `Mycorrhizal(hostSpecies: String)`, `DeadWood(hostSpecies: String)`,
  `SoilOrLitter`, `Dung`, `Other(text)`. Plus `forestType` (coniferous,
  deciduous, mixed) and `hostHealth` (healthy, diseased, dying, dead) as
  `Observed`. Host species is free text, not an enum — tree taxonomy is not a
  closed list this app should own.
- **Entry-level** — `id: String` (assigned at creation, like `PlannedTrip`),
  `foundAt: LatLng`, `foundOn: LocalDate`, `notes: String`,
  `ownIdentification: String?` (the forager's own claim, explicitly theirs,
  never app-generated), `photos: List<LogPhoto>`, `syncState` (below).

## Deferred observation: the entry must be editable

Spore print is read overnight. That means an entry is created in the field and
**completed later** — so unlike `PlannedTrip` (which deliberately has no
rename-after-creation flow), log entries need a real edit path: open an
existing entry, fill in fields left `NotObserved`, save. The UI should make
"what's still unrecorded" visible on an entry, so a half-finished record reads
as half-finished rather than as a specimen with no volva.

## Photos

`CAMERA` is not in the manifest today; it will need adding, plus a
`FileProvider` (manifest `<provider>` + an `xml/file_paths.xml`).

**Recommended mechanism — no new heavy dependency**, consistent with this
project hand-rolling `Dbscan`/`GeoDistance` rather than pulling libraries:

- Camera: `ActivityResultContracts.TakePicture` with a `FileProvider` URI —
  uses the phone's own camera app, no CameraX dependency.
- Gallery: `ActivityResultContracts.PickVisualMedia` — the modern Android
  photo picker, which needs **no storage permission at all**.

Both sit behind an owned interface per `CLAUDE.md`'s wrap-external-
integrations rule, so the domain and the ViewModel never name an
`ActivityResultContract` or a `Uri`:

```kotlin
interface PhotoStore {
    suspend fun persist(source: PhotoSource): Result<LogPhoto>  // copies into app-private storage
    suspend fun delete(photo: LogPhoto): Result<Unit>
}
```

Store files in app-private storage (`context.filesDir`), DB rows holding
relative paths, not absolute ones — an absolute path breaks across app
reinstall/restore. Photos are user-created data and must survive; do not put
them in `cacheDir` (same reasoning the offline-maps work already applied to
downloaded tiles).

Note for the owner, not a blocker: photos plus the offline map tiles will
dominate this app's storage footprint. Worth surfacing total log-photo usage
somewhere in Settings eventually; not v1.

## Persistence — and a schema decision that must change

New Room entities in `data/local/`. The obvious shape is one
`mushroom_log_entries` table plus a `log_photos` table keyed by entry id; the
enum-heavy feature data is a judgment call between many columns and one
serialized blob — prefer real columns for anything that might later be
filtered or counted, since a blob cannot be queried.

**`fallbackToDestructiveMigration()` is not acceptable for this table.** The
existing `ForagerDatabase` uses it, and its doc comment justifies that by the
data being either disposable (the search cache) or trivially re-creatable
(a handful of test planned trips). Field notes are neither: losing a season of
records is unrecoverable and would be entirely the app's fault. This feature
must ship a real `Migration`, and **`exportSchema` must flip to `true`** so
future migrations have schema history to migrate from. The current doc
comment's stated reason for `exportSchema = false` ("a destructive fallback
never [reads a prior version]") inverts the moment real user data exists —
update that comment to say so rather than leaving it contradicting the code.

> **Coder's note (corrected before implementation):** this doc originally
> said "`main` is at DB version 2. PR #12 (offline search cache, still open)
> takes it to 3." — written while PR #12 was still open. It has since
> merged: `main` is at DB version 3 (`cached_searches`, from PR #12) as of
> the commit this branch was cut from. This feature's migration is
> therefore **version 4**, verified directly against `ForagerDatabase.kt`
> on `main` rather than assumed from this line.

Version ordering: verify the current `@Database(version = ...)` on `main`
directly rather than trusting this document — it was written before PR #12
merged. Take the next free number.

## iNaturalist upload

### What was verified live this session (do not re-derive; do extend)

Against `https://api.inaturalist.org/v1/swagger.json`, fetched successfully:

- Endpoints exist: `POST /v1/observations`, `POST /v1/observation_photos`,
  `POST /v1/photos`, `POST /v1/observation_field_values`.
- Auth is a `securityDefinition` of type `apiKey`, header `Authorization`.
- `POST /observation_field_values` takes `{observation_id,
  observation_field_id (int), value (string)}` — iNaturalist's structured
  annotation mechanism.
- iNaturalist supports OAuth2 **including PKCE** (per its API docs; the docs
  host `www.inaturalist.org` is blocked by this environment's egress proxy,
  so confirm directly). PKCE is the correct flow here: **no client secret
  may be shipped in the APK.**
- Rate limits: roughly 1 request/second, ~10k/day.

### Two findings that must shape the implementation

**1. The swagger spec is incomplete, and iNaturalist silently ignores
parameters it does not recognise.** `PostObservation` documents only
`species_guess`, `taxon_id`, `description` — no `observed_on`, no
`latitude`/`longitude`. The real Rails API accepts far more, but the spec
does not say so. Combined with the silent-ignore behaviour, **a wrong
parameter name produces a successful-looking upload that quietly drops the
date or the coordinates.** This repo already knows this failure mode — it is
exactly why `scripts/verify-lichen-exclusion.sh` exists.

Therefore: `scripts/verify-inaturalist-upload.sh` must POST a real
observation against a **test account**, read it back via `GET
/v1/observations/{id}`, and assert every field it sent actually persisted —
comparing values, not just checking for HTTP 200. Per `CLAUDE.md`: assert on
actual output, never on a proxy like a status code. An upload path without
this check is not verified, and must be reported as unverified if the script
cannot be run.

**2. Rate limits make upload a background job, not a button that spins.**
One entry = 1 observation + N photos + M field values. A find with 5 photos
and 15 recorded features is ~21 requests ≈ 21 seconds at 1 req/sec — and
that is one entry. Upload must therefore be a resumable background operation
with explicit per-entry progress, not a foreground call the user waits on.
Do not parallelise past the documented rate limit to make it feel faster.

**3. Partial failure must not duplicate observations.** Persist the returned
remote observation id **immediately** after the observation is created,
before any photo or field-value request. If photo 3 of 5 fails, the retry
must resume against the existing remote observation, never re-POST it. A
duplicate upload to a public citizen-science dataset is a real harm to other
people's data, not just a local bug.

### Observation fields: look up real IDs, never invent them

Mapping mushroom characteristics onto iNaturalist observation fields requires
real `observation_field_id` values, which are community-created. Search the
live API for established fields matching these characteristics and use only
those that genuinely exist and mean what we mean. **Where no well-established
field exists, put the characteristic in the observation `description` text
instead and say so** — never guess an id, and never map a characteristic onto
a field that means something adjacent-but-different. Record in a doc comment
which characteristics map to real fields and which fall back to prose.

### Sync state

Per entry: `Draft` / `Uploading(progress)` / `Uploaded(remoteObservationId,
uploadedAt)` / `Failed(reason, remoteObservationId?)` — the nullable remote id
on `Failed` is what makes resumption safe. A `Draft` is the default and is
never uploaded without an explicit user action; there is no auto-sync.

### Auth

OAuth2 + PKCE, browser-based (Custom Tabs), token stored via
`EncryptedSharedPreferences` or equivalent — **not** plain
`SharedPreferences`. Behind an owned interface (`INaturalistAuth`), so the
domain never sees a token. Signed-out is a normal state: the whole log works
locally with no account, and upload is the only thing that requires one.

**Owner action required, blocking upload only:** register an iNaturalist
application at iNaturalist's developer settings to obtain a **client ID** and
register a redirect URI. The coder cannot do this. The log itself (record,
photos, edit, browse) does not depend on it and should be built and
merge-ready without it — treat upload as the second, separately-verifiable
half of this feature.

## Navigation — flagged, recommendation made

> **Coder's note (corrected before implementation):** this section as
> originally written assumed a tab row of "List / Map / Trip Planner" with
> Seasonal landing as an in-flight fourth tab, and a separate correction
> handed to the coder claimed "Trip Planner is a `CollapsibleSection` inside
> the List tab, not its own tab." Both are superseded by what's actually on
> `main`: the Seasonal Visualizer has merged, and `ResultsTab` (in
> `AvailabilityScreen.kt`) is **List / Map / Seasonal** — three tabs, not
> four. Trip Planner is **not** in the List tab at all; it is a
> `CollapsibleSection` titled "Trip Planner" inside `SearchControls`, the
> content of the drawer's `DrawerPanel.Search` panel (verified directly by
> reading `AvailabilityScreen.kt`, not assumed). The drawer already holds
> search controls, recent searches, and trip planning behind a tune-icon
> entry point, alongside `DrawerPanel.Settings` and a nested
> `DrawerPanel.OfflineMaps`.
>
> This doesn't change the recommendation below — if anything it fits better:
> the drawer is already the home for "things you reach for less than once
> per search," and a new `DrawerPanel.Log` sits naturally alongside
> `Search`/`Settings`/`OfflineMaps` using the exact same panel-switch
> mechanism, rather than needing a fourth tab or a separate navigation
> concept.

The tab row is List / Map / Seasonal — three tabs, each already full.  A log
needs list + detail + create + edit, which is a substantial destination; a
fourth peer tab is too many for a phone.

**Recommendation:** give the Log its own top-level destination reached from
the drawer (the pattern Settings now uses), with "log a find here" also
available from the map's existing long-press gesture — which already opens
the planned-trip flow, so it becomes a two-option action rather than a new
gesture. This keeps the field workflow one gesture from the map while not
crowding the tab row.

This was the one genuinely open architectural item. Implemented as
recommended: a new `DrawerPanel.Log` reached the same way `DrawerPanel.Settings`
is (an entry row in the drawer), with its own list → detail/edit → create
sub-navigation nested inside the panel the same way `OfflineMaps` nests
under `Settings`; and the map's long-press now offers a two-option choice
("Plan a trip" / "Log a find") instead of going straight to the trip dialog.

## Tests required

- The three-state types and the inapplicability nesting — headless, pure.
  Specifically: `NotObserved` never renders or serialises as absence; an
  entry with pores cannot carry a gill attachment; a stipe recorded absent
  cannot carry an interior or base.
- Room round-trip for a fully-populated entry and a barely-populated one,
  against a real in-memory DB, mirroring `RoomPlannedTripRepositoryTest`.
- **The migration itself** — build a database at the prior version, insert
  rows, migrate, assert the rows survived intact. This is the test that
  protects irreplaceable user data; it is not optional.
- `PhotoStore` — persist copies the file into private storage and returns a
  relative path; delete removes it; a failed persist reports failure rather
  than returning a path to a file that is not there.
- Upload orchestration with a fake API — resumption after a mid-upload
  failure reuses the stored remote id and does not re-create the
  observation; rate limiting is respected; partial success is reported as
  partial, never as success.
- Compose/Robolectric: an entry showing `NotObserved` fields must visibly
  read as unrecorded, not as absent — the UI is where this distinction
  actually reaches the user, so assert it on screen, not just in state.
- **Not verifiable headlessly, report as such**: the live upload round-trip
  (covered by the verify script and a test account, not a unit test), camera
  capture on a real device, and how the entry form reads at a large font
  scale. Add to README's "Not yet verified".

## Delivery

> **Coder's note:** this PR implements Phase 1 only — the local log (record,
> photos, edit, browse, Room persistence + a real migration). The
> iNaturalist upload section above (PKCE auth, sync state, upload
> orchestration, rate limiting, the `observation_field_id` lookup
> requirement, the "assert on actual persisted values" verification rule)
> is left written here for Phase 2 and is **not implemented in this PR** —
> it is blocked on the app owner registering an iNaturalist OAuth
> application (client ID + redirect URI), which only they can do.

1. Branch off `main`. Land the local log first (record, photos, edit,
   browse, persistence + migration); upload can be a second PR, since it is
   blocked on the owner's app registration and is independently verifiable.
2. Update README: a numbered "How it works" item for the log, the "Project
   layout" section, and "Not yet verified".
3. `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`; report actual
   pass/fail counts.
4. Report what was verified against the live iNaturalist API and how, and
   what was left unverified — per `CLAUDE.md`, a report that only reassures
   has failed at its job.
