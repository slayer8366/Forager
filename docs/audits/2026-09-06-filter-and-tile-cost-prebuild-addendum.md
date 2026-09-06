# Addendum: owner answers, the implied-speed question for Part A, and the Part B build

Follows `2026-09-06-filter-and-tile-cost-prebuild-report.md`. Same dispatch, same branch
(`claude/new-session-102gri` off `main` at `909c8ea`).

## What the owner decided

- **Part A, rule one as proposed:** not shipped. The owner checked the images: **the spokes are
  multi-point** — each is a chain of many dots, not a single excursion — so the triple rule
  ("beyond both neighbours while the neighbours agree") would pass its tests and leave the bug on
  screen. Before falling back to a duration cap, the owner asked for implied speed to be assessed
  (§ below). The bounded honest loss and the gap handling were accepted as framed.
- **GPX exports filtered points:** yes — the export should match what the user sees; raw export can
  come later.
- **A2:** recompute on next open with write-back before the first frame (the recommendation). The
  reopen rule exists so *authored* content is not rewritten; a cached distance is not authored.
  Record the exception at the rule itself (the entity doc comment), not only in the report.
  Deleted-track rows keeping the old figure is unavoidable — noted. The snapshot's `pointCount`
  having no reader is a dormant field already in the tree — reported, not fixed here.
- **Part B:** 24 km / "15 mi" (round in imperial where 25 is not, and 5 718 of 6 000 is too close
  for alignment variance); a separate offline maximum, search stays at 50; stated constant plus the
  guard test ("the guard is the valuable part"); keep the `min()` as the seam with a comment
  saying why. **B3:** nothing to build — one person has pre-deploy regions and can delete and
  re-download them; the finding and the three options stand recorded in the report for later.

Part A is therefore **held** pending the analysis below; A2 rides with A1 when A1 builds, since a
recompute that changes nothing has nothing to write. Part B is built in this commit.

## Part A — does implied speed catch the multi-point spoke?

**Do the timestamps support it?** Yes. Every stored point carries `timestampEpochMillis`
(`domain/model/TrackPoint.kt`), `getPointsForTrack` returns them ordered by it
(`data/local/TrackDao.kt:47`), and the sampler guarantees consecutive stored points within one
recording are at least the mode's minimum interval apart (`domain/LocationSampler.kt`, rule two:
`elapsedMillis < minIntervalMillis → reject`) — 5 s, 15 s or 60 s
(`domain/model/TrackRecordingMode.kt:27–29`). So `d / Δt` between consecutive points is always
defined and never divides by a near-zero interval, except across a process death and resume, where
the sampler's `lastAccepted` restarts at `null` and the first fix is accepted regardless of
elapsed time — a `Δt ≤ 0` pair would have to be treated as unevaluable and kept. GPX export
preserves the timestamps too (`domain/GpxCodec.kt:49`, `<time>`), which matters below.

**The 72 km/h fact is right for a single jump and only for a single jump.** 100 m in one 5 s
interval is 20 m/s and no walker does that. But the owner's finding is that the spokes are chains,
and a chain spreads its length over its dots — and the sampler's own floors then *bound* the
implied speed the chain can show. A spoke of length L drawn with N dots on the way out has a
per-step displacement of about L/N over at least one interval, so its per-step implied speed is at
most L / (N × interval). The ceiling for the shapes the images could plausibly hold:

| Mode (interval) | L = 40 m | L = 100 m | L = 150 m | L = 300 m |
|---|---|---|---|---|
| HIGH (5 s), N = 3 | 2.7 m/s | 6.7 m/s | 10 m/s | 20 m/s |
| HIGH (5 s), N = 10 | 0.8 m/s | 2.0 m/s | 3.0 m/s | 6.0 m/s |
| HIGH (5 s), N = 20 | 0.4 m/s | 1.0 m/s | 1.5 m/s | 3.0 m/s |
| BALANCED (15 s), N = 3 | 0.9 m/s | 2.2 m/s | 3.3 m/s | 6.7 m/s |
| BALANCED (15 s), N = 10 | 0.3 m/s | 0.7 m/s | 1.0 m/s | 2.0 m/s |
| SAVER (60 s), N = 3 | 0.2 m/s | 0.6 m/s | 0.8 m/s | 1.7 m/s |

Walking is 1.4 m/s; a brisk walk 2; a jog 3. **A "chain of many dots" — ten or more — out to
150 m on HIGH is 3 m/s per step at most, and on BALANCED 1 m/s: walking pace, by the sampler's own
construction.** Any speed bound that catches such a chain also catches a jogger, and on BALANCED a
walker. Only chains of a few long steps (N ≤ 3 at 100 m and beyond) exceed any human bound, and
whether the photographed spokes are three long steps or twenty short ones is exactly what I cannot
see from here.

**The failure mode the owner has not seen.** A speed rule is a rule about the *user*, not about the
fix. A bound that separates spokes from walkers is exceeded honestly by anyone on a bicycle (5–8
m/s) or in a vehicle — the drive between two foraging sites with the recording left running — and
implied speed alone has no return condition to protect that: a straight drive at 15 m/s would lose
every point after the first. Adding the return condition back (a fast run that comes back within
A's disc) restores the shape requirement, but a fast out-and-back is then an honest drive out and
back, erased whole. Both are the dangerous-direction error the dispatch ranked above the bug. And
the bound itself is a number — 2.5? 3? 5 m/s? — chosen without data, which is what rule two was
deferred to avoid.

**Recommendation: hold Part A, and get the one file that answers this.** The starburst tracks are
on the owner's device, and the app already exports a track as GPX with every point's position and
timestamp (Records → Recorded Tracks → export). One exported starburst track — ideally the venue
one — gives the per-step distances and intervals of the real spokes, from which it is a few minutes'
work to say which rule catches them: implied speed (if the steps are long and fast), a run-with-cap
rule (if they are many and slow), or neither. The filter's test would then be the real track with
its real spoke, not a hand-built guess about a shape nobody has measured. Accuracy is not in the
GPX, so the accuracy-relative clause cannot be checked from it — the fix-logging branch is the
source for that — but the speed question does not need accuracy. Until then, building any rule is
choosing a threshold from a photograph, which the dispatch already declined for rule two.

**Nothing in Part A is built.** No filter, no `TrackReadFilter`, no recompute, no doc-comment
exception at the entity — A2's decisions are recorded above for when A1 lands.

## Part B — built

### What changed

- `domain/OfflineMapRepository.kt`: `SERVED_MAX_ZOOM` **14.0 → 15.0**, its comment rewritten in its
  existing shape (what it encodes, where to check it, what changes if it moves, the owner's
  2026-09-06 verification of `us.json` and a zoom-15 tile, why the previous value was 14, that the
  `min` is now a no-op and why it stays). New **`MAX_RADIUS_KM = 24`** and `clampRadiusKm`, with the
  arithmetic table (24 → 5 246, 25 → 5 718, 26 → 6 075 worst-case at 49.60°N), the latitude sized
  for, the "15 mi" reading, why 25 was not taken, and why it is stated rather than derived.
  `TILE_COUNT_LIMIT`'s comment updated from "about nine 15 km regions" to the zoom-15 arithmetic
  (about three; defaults 9–17 % each) and the owner's decision that the budget moves with the
  Cloudflare upgrade.
- `domain/EstimateOfflineTileCount.kt`: `estimateServedOfflineTileCount`'s doc says the `min` is
  currently a no-op and why it is kept as the seam.
- `ui/availability/AvailabilityOfflineMapsUi.kt`: the slider's `valueRange`/`steps` read
  `OfflineMapRepository.MAX_RADIUS_KM`; the estimate comment updated.
- `ui/availability/AvailabilityViewModel.kt`: `onOfflineMapRadiusChanged` clamps with
  `OfflineMapRepository.clampRadiusKm`; `loadOfflineMapPreferences` clamps a restored last-picked
  radius (a pre-change 50 km pick restores at 24, still marked touched); the gate's comment updated.
- **Untouched by decision:** `Region.MAX_RADIUS_KM = 50` and everything that reads it (search
  radius sliders, `clampRadiusKm`, `GeoDistance`, `SavePlannedTripUseCase`); the "Ready to zoom"
  row label (B3, nothing to build); the worker.

### Tests

- `EstimateOfflineTileCountTest` — the served-ceiling test's literals move with the constant (the
  one existing assertion B1 necessarily changes, flagged in the report): **224 / 1 781 / 4 304 at
  5 / 15 / 24 km** at the owner's coordinates, all hand-derived in the report's Python; plus
  `MAX_RADIUS_KM == 24` pinned.
- **New `OfflineMapRadiusBudgetGuardTest`** (domain): at 49.60°N under the same 40 × 40 alignment
  sweep the report used, `MAX_RADIUS_KM` costs **5 246** and fits; `MAX_RADIUS_KM + 1` costs
  **5 718** and still fits (the true maximum, pinned as recorded headroom); `MAX_RADIUS_KM + 2`
  costs **6 075** and exceeds — the assertion that fails when the budget rises without the ceiling
  following. Plus `Region.MAX_RADIUS_KM == 50` and `MAX_RADIUS_KM == 24` as literals. The Kotlin
  sweep reproduced the Python literals exactly on the first run.
- `AvailabilityViewModelOfflineMapsTest` — the slider-maximum gate test moves to the worst case:
  **24 km at 49.60°N reaches the repository** through the real `onDownloadOfflineMaps` gate (5 120
  tiles, point estimate). The clamp test now pins 500 → 24, **25 → 24**, 24 → 24, −5 → 1, and that
  the search radius still clamps to 50. New: a persisted last-picked 50 km radius restores at 24
  and touched.
- `AvailabilityScreenSettingsPanelTest` — new: the offline radius slider's `ProgressBarRangeInfo`
  ends at **24f with 22 steps** and starts at the miles default 8 (asserted with a 0.001 tolerance,
  because Material3 snaps the thumb to its step grid in float and lands on 7.9999995). This is the
  "one step above the maximum is not reachable from the UI" assertion, on the closest thing to the
  thumb's reach a Robolectric test can read.
- `AvailabilityViewModelDistanceUnitTest` — unchanged and re-run: the per-unit defaults still apply
  and still read "10 km" / "5 mi".

### Verification

Forward: the five affected classes (`EstimateOfflineTileCountTest`, `OfflineMapRadiusBudgetGuardTest`,
`AvailabilityViewModelOfflineMapsTest`, `AvailabilityScreenSettingsPanelTest`,
`AvailabilityViewModelDistanceUnitTest`) — 50 tests, 0 failed, after one fix to my own new
assertion (the 7.9999995 tolerance above; the first run failed on it and on nothing else).

Reverted variants, each a one-line sed on the source, the build log checked for `e:` /
`compileDebugKotlin FAILED` before results were read (none in any of the four — every reverted
build compiled and ran), and the source restored from a saved copy afterwards:

| Revert | Predicted | Observed |
|---|---|---|
| `SERVED_MAX_ZOOM` back to 14.0 | served-ceiling literals and all three guard literals fail | 4 failures: `expected:<224> but was:<68>`; guard `5246→1402`, `5718→1493`, `6075→1586` |
| `MAX_RADIUS_KM` back to 50 | every test that pins 24 or gates 24 km fails | 9 failures: served `4304→18696`; guard `5246→22028`, `5718→22790`, `6075→23778`, `24→50`; gate test "expected the pre-flight gate to let a 50 km region through" (refused at 49.60°N); clamp `24→50`; restored radius `24→50`; slider test finds no 1–≠50 slider |
| the `loadOfflineMapPreferences` clamp removed | only the restored-radius test | 1 failure: `expected:<24> but was:<50>` |
| slider `valueRange` back to `Region.MAX_RADIUS_KM` | only the slider semantics test | 1 failure: no slider whose range ends other than 50 |

Every failure names a value only its own revert could produce; none is a stale result.

Full suite after Part B: **1 161 tests, 0 failed, 24 skipped** (1 155 + 4 guard tests + the
restored-radius test + the slider test). Skip set compared by (class, name) against the CI
`SKIPPED_TESTS_ALLOWLIST` parsed from `.github/workflows/ci.yml`: byte-identical, 24 = 24. The
`JournalTabTest` "From Album" flake did not fire.

One process note: the reverted-variant runner used `git checkout -- FILE` to restore the source,
which restores the *committed* version — on the first Part C revert this silently discarded the
uncommitted forward edit, caught only because the next diff was a file short. The runner now
restores from a copy saved before the sed. Recorded because the CLAUDE.md line on reverted variants
covers stale results, and this is a second way the same tool can lie: a restore that removes the
change under test.

### Device checks the owner must run for Part B

3. The tile estimate matches the actual download at the new 24 km maximum (the label's "~N tiles"
   against the region row's live count after download).
4. A newly downloaded region genuinely renders sharper zoomed in offline than a pre-deploy one —
   the only real test of whether 3.7× the tiles was worth it.

## Disclosure for this addendum

- **Confirmed:** the sampler's interval floor and its consequence for per-step implied speed
  (arithmetic from the mode constants); GPX carries `<time>`; the guard literals match between the
  independent Python and the Kotlin sweep; everything listed under Tests ran as stated.
- **Inferred:** that the photographed spokes are ten-plus dots ("many") rather than three — the
  owner's word, not a count; the human speed figures (1.4 / 2 / 3 m/s) are general knowledge, not
  measured here.
- **Could not determine:** the real per-step distances and intervals of the spokes — the GPX ask
  above is how to get them.
- **Premises corrected:** "72 km/h isn't a tuned threshold" holds for one jump; for a chain the
  sampler's floor makes the equivalent per-step figure a walking speed.
- **Decided without cover:** the slider test asserts the thumb's *semantic* range rather than
  dragging it — a coordinate drag on a Robolectric slider is not something this suite does anywhere,
  and the semantic range is what a drag is bounded by; said plainly so it is not mistaken for a
  touch test.
