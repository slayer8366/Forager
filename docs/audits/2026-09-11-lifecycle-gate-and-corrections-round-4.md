# Round 4: what survives a pocketed phone, and three figures that did not reproduce

**STALE BASE — RE-DERIVED 2026-09-12** by `2026-09-12-round-4-re-derived-against-5515adc.md`. This report was written against `4957675` while `origin/main` had moved to `5515adc`, two merges ahead: PR #95 (sundown countdown Phase 1) and PR #96 (recording-state resync). **§3a's "`CivilTwilight` has zero production callers" is withdrawn** — on current main it has two (`ComputeSundownCountdownUseCase`, `SunCrossing`), and every sundown file this report implied was missing already exists, `SundownPreferencesRepository.kt` included. The mechanism findings (§1, §1a, §2a) re-check clean on `5515adc`, and §1a's gap is confirmed **still open** there: #96 resyncs the stale-active direction only. Read the re-derivation first.

**Date:** 2026-09-11
**Type:** investigation + correction record. **One file edited** (`2026-09-08-data-inventory-for-privacy-policy.md`, owner-authorised supersession note). No code changed.
**Follows:** `2026-09-11-lifecycle-gate-and-corrections-round-3.md`.

The owner asked one question that outranked everything else: after `5967dd5`, does anything still
run with the phone in a pocket? **Answered: the off-track alert survives, and the reason it survives
is that a prior dispatch already fixed exactly this failure mode for exactly this reason.** A
different gap, with a different trigger, was found while tracing it.

---

## 1. The pocket question

**Screen-off does stop `MainActivity`.** `onLeftForeground()` runs every time the phone goes in a
pocket, as the owner says. But it cancels **only** `AvailabilityViewModel.liveFixJob`.

**The off-track alert does not read `liveFix`.** It runs off a second, independent subscription:
`TrackRecordingViewModel.beginLocationTracking()` (line 339) collects `locationTracker.fixes` on its
own `viewModelScope` job, and calls `returnToStart(point)` on every fix (line 359). `returnToStart`
(line 494) feeds `recentReturnDistancesMeters`, re-runs `DetectOffTrackUseCase`, and fires the alert.

`viewModelScope` cancels at `onCleared()` — Activity **destruction**, not stop. That was the whole
premise of `5967dd5`'s own commit message, and it cuts the other way here: a stopped Activity does
not clear its ViewModels, so this collector keeps running with the screen off.

**And the delivery hop was already removed, for this exact reason.** `TrackRecordingViewModel.kt:
473-479`:

> `[DetectOffTrackUseCase]`'s own output used to reach a user nowhere but an icon tint [...] nothing
> a forager with the phone pocketed on the return leg, **exactly the body state this alert exists
> for**, could ever perceive. Every call where the heuristic reads `true` hands an `[Alert]` to
> `[alertDelivery]` **directly from here** (alert-delivery dispatch: it used to bump a counter in UI
> state that a `LaunchedEffect` in `MainActivity` observed, and **that composed hop is what stopped a
> stopped Activity delivering anything**).

So the failure the owner was reaching for is real, was found here before, and was fixed before
`5967dd5` landed. **`5967dd5` did not turn the alerts off.**

**Return navigation requires a recording.** Asked and answered separately, because it is the second
half of the question. `startReturn()` (line 265) opens `val active = uiState.value.activeTrack ?:
return` — a no-op with nothing recording, and its doc says so. `returnToStart` needs an
`originWaypoint` or a first breadcrumb. The HUD appears only while `isReturning`, and
`TrackRecordingViewModel.kt:256-258` states that coupling as deliberate: "as of navigation HUD stage
one, **the only way the HUD appears**."

**So Phase 1's premise holds**: the durable path runs through the recording, and the sundown spec is
right to assume it.

### 1a. A real gap, with a different trigger than the one proposed

`beginLocationTracking()` has **one call site**: `startRecording`'s success path (line 189). The
ViewModel's `init` block is `{ loadWaypoints(); loadTracks() }` (line 155-158) — **it does not
re-adopt an active recording.**

Screen-off is survivable because the ViewModel is not cleared. **Activity destruction is not.** If
the process is killed under memory pressure mid-recording, `TrackRecordingService` keeps collecting
and persisting points as a foreground service, but on recreation `TrackRecordingViewModel` starts
with no `activeTrack` and never calls `beginLocationTracking()` again. For the rest of that
recording, off-track alerts and the return HUD are silently dead while the track itself keeps
recording correctly.

**Traced from the code, not observed on a device**, and stated at that confidence. It is the
`CLAUDE.md` "partial results are reported as such" case at the feature level: the recording succeeds
and the safety feature attached to it does not, with nothing saying so.

## 2. Three figures that did not reproduce

### 2a. "Never reads a cache" — mechanism wrong, conclusion intact

The owner is right. The absence of a `getLastKnownLocation` call does not establish that no cache is
consulted; Android's own `getCurrentLocation` contract permits returning a fix from the very recent
past. Round 3 proved less than it claimed and should have said "no *stale* fix" rather than "never
reads a cache." The conclusion — a find's coordinate is effectively fresh — survives, on the
contract rather than on my grep.

**Their better question has a concrete and worse answer.** `LogPhotoEntity` (lines 21-27) stores
`id`, `relativePath`, `createdAtEpochMillis`, `latitude`, `longitude`. **No accuracy. No provider.**
So a 2,000 m network fix and a 4 m GPS fix are indistinguishable in the journal, exactly as they
said.

Worse than a missing column: `LocationResult.Success` carries only `lat`, `lng`, `altitude`, so
**accuracy is discarded at the `LocationProvider` interface**, one level above the schema. And
`AndroidLocationProvider.awaitFirstLocation` races GPS against network and takes whichever answers
first, with the doc comment noting network "typically resolves in a second or two" against a cold
GPS lock — so the coarse fix is the *likely* winner indoors or under canopy, not an edge case.
Fixing this is an interface change, a migration and a backfill decision for existing rows, not a
column addition. Phase 4, as they said, and bigger than it looks.

### 2b. The PR count: 94 is right, 93 was mine to explain

Round 3 guessed merge refs. The owner's `ls-remote` shows 94 head refs, 1–94, no gaps, zero merge
refs, which disproves it.

Checked directly: **`/pulls/94` exists and is closed** — "Merge forager-bak's work into Forager".
`/pulls?state=all&per_page=100` returns **93**. So there are 94 pull requests and the list endpoint
under-reports by one.

**I cannot explain the discrepancy** and am not going to invent a second mechanism after the first
was disproven. The authoritative count is **94**, from the ref enumeration and confirmed by fetching
#94 directly.

### 2c. "Three regions rather than nine" does not survive its own budget

The owner is right, and the arithmetic is short. `OfflineMapRepository.kt:75-84` gives 1,781 tiles at
ceiling 15 and ~480 at ceiling 14, against `TILE_COUNT_LIMIT = 6000`:

| | Tiles | 6000 ÷ tiles |
|---|---|---|
| Ceiling 15 | 1,781 | **3.4** ✓ "about three" |
| Ceiling 14 | 480 | **12.5** ✗ not "about nine" |

The owner's 11–14 bracket is right. The ratio is 1,781 ÷ 480 = **3.71**, so the two tile figures are
mutually consistent for one extra zoom level; it is **"about nine" that does not belong to this
budget**, and their diagnosis that one figure predates the `MAX_RADIUS_KM` change is the likely
explanation.

This is the derived-figure family again, and this time it is inside the project's own source doc
comment, feeding a decision the audit put in front of the owner. **Re-derive both at one radius and
one budget before the maxzoom fork is decided.**

## 3. Two things round 3 surfaced without flagging

### 3a. Cartography is built, and the inventory needs three categories

`find app/src -iname "*artograph*"` returns **20 files under `main/`** — `CartographyScreen`,
`CartographyEntryListScreen`, `CartographyEntryEditScreen`, `CartographyEntryReportScreen`,
`CartographyViewModel`, `CartographyUiState`, a Room entity, DAO and repository, and eight domain use
cases — plus **11 test files**. The dispatch's "not found" is wrong, as the owner says, and round 3
walked past `CartographyScreenTest.kt` while quoting a different line from it.

**But file-existence is the wrong test, and the next item proves it.** `CivilTwilight.kt` exists in
`domain/` with a full test (`CivilTwilightTest.kt`) and a careful doc comment explaining why solar
altitude was chosen over a weather API's sunrise/sunset fields. It declares one symbol, `object
CivilTwilight`. **Searched `app/src/main` for every reference outside its own file: zero.** It is
built, tested, documented, and has no production caller.

So the inventory has **three** categories, not two:

1. **Built and wired** — Cartography.
2. **Built and dead** — `CivilTwilight`, reachable from tests only.
3. **Absent.**

Phase 0 therefore needs a reachability check per item, not a file listing. `CLAUDE.md`'s own entry
says it in one line: "**check reachability before measuring behaviour** [...] and it costs one grep."
Category 2 is invisible to any inventory that does not run it.

*Calibration*, for completeness, is a concept spread across `CompassTrustJudge`, `LiveFixGate`,
`MovingPace` and `CompassProvider` rather than one named feature; whether that constitutes "built"
depends on what the dispatch's row means by it, which this document cannot settle.

### 3b. The overflow hop is missing from the recipient table

Confirmed. `docs/legal/privacy-policy.md:59-66` lists six recipients. **Protomaps appears nowhere in
the file** — `grep -i "protomaps"` returns zero hits. The Worker's row reads:

> `forager-pmtiles.brandonlee1-894.workers.dev` — a Cloudflare Worker operated by the developer | The
> offline map style, and vector map tiles by `z/x/y` | The offline basemap, and downloading an
> offline region

which presents it as a first-party endpoint with no onward disclosure.

**The owner's analysis is right on both halves.** The section's closing sentence — "each of these
also reveals your IP address" — stays true, because Protomaps sees Cloudflare's egress address and
never the user's. But the table's job is to say who learns what, and a first request for any z15
tile makes the Worker range-read the daily build, with byte offsets that resolve to tile
coordinates. A third party learns which areas were fetched, and the table does not say so.

Offline regions depend on this for z15 specifically: `OfflineMapRepository.MAX_ZOOM = 15.0`, and that
constant's doc comment names the overflow path as its justification.

**Two ways out, as they say:** extract z15 into R2 and the hop disappears along with findings #2 and
#3; or keep the path and add a sentence. There is no third option in which the table stays as it is.

## 4. Small corrections

**The stale `CLAUDE.md` is the copy uploaded to this Claude project, not a local checkout.** Round 3
said "the fix is to pull, not to edit." Wrong: pulling a git checkout does nothing to a file uploaded
into a project's knowledge. The repository's copy is clean (verified again: one `CLAUDE.md`, zero
hits on three terms), and **the uploaded copy needs re-uploading**, as does the Data safety draft,
which still describes the pre-`5967dd5` behaviour.

Worth naming because it is a category this thread had not hit: a stale artifact in a place `git`
cannot see, where every check I can run reports clean.

**`androidTest` does not exist.** `ls app/src/androidTest` → no such directory, consistent with the
zero count taken at the start of this thread. So round 3's "nothing constructs `MainActivity`" grep
was complete over every source set that exists.

**`2026-09-08-data-inventory-for-privacy-policy.md` §4 is now superseded in place**, owner-authorised,
scoped to the first clause only: the whole-lifetime/entire-time-foregrounded claim is falsified;
"not gated on the Maps tab, not gated on a search, not gated on a recording" stands. The note records
that no submitted form answer changes, and that this is the second claim in that one document to
outlive its mechanism.

**Recorded as a requirement for whoever takes the `ActivityScenario<MainActivity>` test:** fix
`MainActivity.kt:215-218` in the same change. It currently cites `PhotoAcquisitionLaunchers.kt` as
support for a claim that file contradicts, and a reader sent there for confirmation finds the
opposite. Test and comment are one defect, not two.

## Disclosure

**Confirmed by reading this tree:** `beginLocationTracking`'s body, its single call site at 189, and
`init`'s two calls; `returnToStart`'s alert path and the alert-delivery doc comment; `startReturn`'s
early return; `LogPhotoEntity`'s five columns; `LocationResult.Success`'s three fields; the
Cartography file list; `CivilTwilight`'s single declaration and zero production references; the
privacy policy's recipient table and zero Protomaps hits; the absence of `app/src/androidTest`;
`CLAUDE.md` clean again.

**Confirmed against GitHub:** `/pulls/94` exists and is closed; the list endpoint returns 93.

**Computed here:** 6000 ÷ 1,781 = 3.4 and 6000 ÷ 480 = 12.5.

**Traced but not observed:** §1a's process-death gap. No device, no instrumented run.

**Not determined:** why the GitHub list endpoint under-reports by one; whether "calibration" in the
dispatch's row means the four files named in §3a or something else.
