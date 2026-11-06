# Sorrel review 419: step 8c, tapping an official-trails band (dormant until v3)

Sorrel Tally (Fable), 2026-10-26T23:28Z, fresh session. For Rowan Waymark. Read-only on code; this file is the only
write. Nothing run under Gradle here; Node 24.22.1 (CI's pin) run once, for the v3 regeneration. My own checks are in
`/mnt/work/scratch/sorrel-419/` (`shown_name.py`, `tap_table.py`, the twelve runs' JUnit XML and logs), not committed.

PR #419, branch `step8c-reference-tap` @ `8ca59fe17`, nine commits over merge-base `06fe0e3ae` (the #418 merge):
`e0a0b0519` tests over stubs, `0f9a019c3` build, `b3e28cc01` merge of pre-main, then Amendment 2 in three test/build
pairs (`fce76018f`/`19710b2f4`, `a0ee6a5a1`/`32bd34c22`, `7e3d39c57`/`8ca59fe17`). Reviewed against dispatch
`prompts/preserved/2026-10-26-16.md` with Amendments 1 and 2, RECORD -1680, -1681, -1688, -1690, -1692 (all on
`origin/records-after-173` @ `edab1ecf2`), and the step 8 report §3 and §8 row 8c (`later-steps-reports` @ `e4f9324c8`).

Every claim is marked **read** (seen in a file at a cited line), **computed** (run here, tool named) or **inferred**.

## Verdict

**Blocking:** none.

**Should-fix (one, in the tap path; fix before the app switches to v3, and most cheaply in this PR while the tests
are fresh):**
1. **A stopped band leaves nothing, where before 8c the same finger reached a track or a cell.** `MapTapHandler.kt:390`
   sets `winner` to `null` when the way home or the recording trail stops a band, so the tap is `Plain`. Pre-8c (the
   band not tappable) the same tap fell through `resolveTap`'s later stages to whatever else was there. Six of the 36
   finger configurations differ (computed, `tap_table.py`, my port of `resolveTap` + `bandStopped`; §4): with the route
   and the band both under the finger, (a) a **kept track within the finger's box** (3-24 dp off, as a GPS-offset track
   beside the trail would be) was the winner before and is lost now; (b) a **forecast cell under the finger** was the
   winner before and is lost now; and with the band only in the box and the route under or beside the finger, (c) the
   cell is lost the same way. The way home follows the trail map and the band is the agency's record of the same
   trail, so route-over-band is the common case along a way home; with a forecast layer on, tapping one's route reads
   nothing where it read the cell before. Amendment 2 says "it is a plain tap as today" and the owner's "Nothing" was
   about the band's bubble (RECORD -1688); neither asked for a track or a cell to be lost. The fix that matches "as
   today" exactly: when `bandStopped` is true, resolve again with the band ids removed from `tappable` (one more
   `resolveTap` call), and extend `ReferenceTrailTapPrecedenceTest` with the (a) and (b) rows. The existing test
   `the way home stops only a band, never a track, a marker or a cell` does not cover this: its track and cell are
   checked without the band stopped in the configuration that loses them.

**Notes** (nothing here needs a change before merge):
- The all-capitals rule's surprising outputs, enumerated in §3: three-plus-letter acronyms are lowercased after their
  first letter (`BLM ROUTE` → "Blm Route", `USFS` → "Usfs", `PCT` → "Pct"); `US` is on the owner's list as a pronoun so
  `US 101` → "Us 101"; letters after an inner apostrophe go lower (`O'BRIEN` → "O'brien", `D'ARCY` → "D'arcy");
  ordinal suffixes stay capital (`2ND LOOP` → "2ND Loop"); a two-letter compass code with a stop is treated as an
  abbreviation (`NE.` → "Ne.") while without one it is kept (`NE` → "NE"). Each is the rule as ruled, with no
  dictionary; listed so the owner sees them before a phone does.
- The Amendment 2 fail-first run's release job (40409914895) failed on **Java heap space** in `packageReleaseBundle`
  and `packageRelease` (read, `release-compile-gradle.log:153, 380`), a runner condition, not the PR; the unit job
  there ran and failed as a fail-first should, and the final run's release job is green.
- A band bubble is not restored by the return-to-map memory, as the coder recorded. The memory can only receive a
  find's id (`CompactMapBubble.kt:78`) or a bubble with a "kept in" line (`:87`), and `takeEntryBubble` keeps only a
  `FeatureTarget` (`MapReturnMemory.kt:109`); a band bubble has neither way in, so it is the same as every track,
  outline or cell bubble today: a tab change drops it (`AvailabilityCompactMapUi.kt:273`, a plain `remember`). Accurate
  note, no gap.
- The one new `MapTapSinks` method has no default, so the five test copies had to gain it to compile; the "five"
  holds (four `AvailabilityScreen*Test` sinks plus `FanOutTestScene.RecordingSinks`; read, the diff).
- `referenceAttributesOf` takes a numeric field's JSON text as published (`OverlayFeatures.kt:322-326`): an archive
  storing `trail_no` as a float would read "17.0". A premise for Aspen's archive, not a defect here.
- The dispatch's own step-8-report pointer named `FanOutLayers.kt`'s per-layer `queryRenderedFeatures` as a possible
  touch point; the coder did not need it (the band ids flow through `tappableLayerIds`), which is the right call.

**Mergeable: yes**, with should-fix 1 for the planner to place (this PR or a follow-up before the v3 switch). Nothing
changes on v2: the bands are queried only while drawn, and the stoppers only once a band has won, so neither query runs
on a style without the band.

## Prediction (written after the reading pass, before the computed checks)

Honest order: I read the whole diff first, then wrote these, then ran the three computations. (1) v3 regenerates
byte-identical: the two constants are exported and used nowhere. (2) The coder's 28 name cases hold under my port; the
surprising outputs are around acronyms of three or more letters, `US`, and inner apostrophes. (3) Nulling the winner
when a band is stopped loses a cell under the finger that pre-8c would have won; I did not expect the kept-track case
until the table showed it. (4) Every revert is a one-line edit whose failures name only what that line does.
**Scored:** (1) held; (2) held, plus the ordinal and `NE.` cases I had not predicted; (3) held and was wider than
predicted; (4) held.

## 1. Baseline (read)

- `origin/pre-main` `c445574da` (#420 merged); PR merge-base `06fe0e3ae`; `git merge-tree --write-tree` of the two
  gives a tree with no conflict (computed); #420 touched `BarometricHeight*` only, no file overlap (read, `git log`).
- Diff `06fe0e3ae..8ca59fe17`: 19 files, +1029/−13. Main: new `domain/referencetrails/ReferenceTrailRecord.kt` (126
  lines); `TapPrecedence.kt` (+58, now 119), `MapTapHandler.kt` (+38, now 278), `MapBubbles.kt` (+22, now 508),
  `OverlayFeatures.kt` (+14, 224), `MapRenderMode.kt` (+14, 346), `MapFeatureBubble.kt` (+9, 253), `MapListeners.kt`
  (+7, 248), `MapBubbleLayer.kt` (+4, 216); `map-style/overrides/forager-reference.mjs` (+9, 126). Tests: four new
  classes (144, 86, 203, 291 lines), four `AvailabilityScreen*Test` sinks (+3 each), `FanOutTestScene.kt` (+5). No
  file near 800 (computed, `wc -l`). The 8b files (`MapLayers.kt`, `ReferenceTrailsVisibility.kt`, `MapStyleReload.kt`,
  `MapLayersSheet.kt`, `DataSourceCredits.kt`) and `RouteHomeLayers.kt` are untouched (read, diff stat).
- Runs, all `workflow_dispatch` on scratch branches except the last (computed, `gh run view`): fail-first 40404270706
  on `e0a0b0519`; R1-R4 40405230943/42684/52028/61923 on `1d58d44e8`, `2cd55d933`, `7ab3bd97a`, `2dcceff49` (each
  parented on `0f9a019c3`); Amendment 2 fail-first 40409914895 on `fce76018f`; reverts 40410168353 `eef368e2b` and
  40410171599 `450f09e06` (parent `19710b2f4`); name-rule fail-first/revert 40413952638 `a0ee6a5a1` / 40414049356
  `698901dff` (parent `32bd34c22`), and 40415995186 `7e3d39c57` / 40416073223 `e1c74780a` (parent `8ca59fe17`);
  final 40416071610 on `8ca59fe17`, `pull_request`, all five jobs green. All thirteen commits are reachable locally
  (fetched by sha).
- Final run's count from its JUnit XML (computed): `testDebugUnitTest` 773 classes, **5,726** tests, 0 failures,
  0 errors, 24 skipped; `testDebugUnitTestOlderSdks` 6 classes, 24 tests. **5,750**, as the coder said. The four new
  classes: 13 + 6 + 15 + 7 = 41 tests.

## 2. The wording and the missing-part forms (read `ReferenceTrailRecord.kt:49-86`; computed where said)

- First line: `"Forest Service trail"` / `"BLM trail"` (`:59-60`), then `" $number"` when present (`:62`), then
  `" · $name"` when present (`:63`); the separator is U+00B7 with a space each side (computed, `ord`), matching the
  owner's text in RECORD -1680 byte for byte. Second line `"Official record. Routes follow the trail map, not this
  layer."` (`:71`), the one constant for both agencies. The four missing-part forms and the BLM forms are each asserted
  (`ReferenceTrailRecordTest:528-555`) and the screen test asserts two of them through a real touch (§5).
- Missing means absent, blank or whitespace: `fromAttributes` trims and drops the empty (`:79`); the number is never
  recased (`:47`, asserted `:627-629`). BLM reads only `ROUTE_PRMRY_NM` and never a number (`:82`), and ignores
  Forest Service fields on a BLM feature (asserted `:553`).
- Fields are read from one place: `ReferenceTrailFields` mirrors `forager-reference.mjs:40, 48-49`, and the test
  `the field names are the ones forager-reference_mjs writes` parses the `.mjs` with a regex on `export const X = "…"`
  (`:645-651`). In the fail-first run that test failed with `expected:<null> but was:<trail_name>` (the constant was
  missing from the `.mjs`), so it does read the file (read, JUnit XML).

## 3. The all-capitals rule, every edge (computed, `shown_name.py`, my port of `shownName` `:101-132`)

Port checked first against the coder's 28 asserted cases: 28/28 identical. The rule as read: a name with any lower-case
letter, or no letter, is as published (`:102`); otherwise each word (`[\p{L}\p{N}'’]+`, `:111`) is kept when it has a
digit, is in I-XX, or has one or two letters, is not on the 26-word common list and is not directly followed by `.`
(`:129-132`); every other word gets first letter title-case, rest lower (`:106`). `lowercase()` is locale-independent
in Kotlin (inferred from the stdlib's contract; no Turkish-İ trap).

Against RECORD -1690 and -1692, line by line: words with a digit kept (`SPUR 17A` → "Spur 17A", `4WD` kept) ✓;
numerals I-XX kept, XXI not (`CAMP XXI` → "Camp Xxi") ✓; `MIX`, `DIX`, `LIV`, `CIV` capitalised as words ✓; the 26-word
list is exactly the owner's (A, AN, AS, AT, BE, BY, DO, GO, HE, IF, IN, IS, IT, ME, MY, NO, OF, OH, ON, OR, OX, SO, TO,
UP, US, WE; `:120-123`) ✓; `MT.` → "Mt.", `ST.` → "St." ✓; `II.` stays (numeral checked first, `:98`) ✓.

Outputs worth the owner's eye (all are the rule working as ruled):

| Published | Shown | Why |
|---|---|---|
| `BLM ROUTE 12`, `USFS`, `PCT`, `NPS` | "Blm Route 12", "Usfs", "Pct", "Nps" | only 1-2 letter codes are protected |
| `US 101 CONNECTOR`, `US-26` | "Us 101 Connector", "Us-26" | US is on the list as the pronoun |
| `AT`, `OR` (state or trail codes) | "At", "Or" | on the list as words; `WA`, `ID`, `MT`, `CA` are kept |
| `MT HOOD TIE`, `ST HELENS` (no stop) | "MT Hood Tie", "ST Helens" | kept as codes; only the stop marks an abbreviation |
| `O'BRIEN CREEK`, `D'ARCY`, `MCDONALD` | "O'brien Creek", "D'arcy", "Mcdonald" | one word, one capital; `BOB'S` → "Bob's" is the trade |
| `2ND LOOP`, `1ST`, `4TH` | "2ND Loop", "1ST", "4TH" | a digit keeps the word |
| `NE.` / `NE`, `N. FORK` | "Ne." / "NE", "N. Fork" | a 2-letter code with a stop reads as an abbreviation |
| `TRAIL L`, `LOOP C`, `SEGMENT Z` | kept | single letters not on the list are codes (L, C, D, M not special-cased) |
| `A.T. SPUR`, `U.S.A.`, `D.C.` | unchanged | each letter is a one-letter word with a stop: title-case of a capital |
| `ÉCOLE`, `SEÑOR` | "École", "Señor" | `\p{L}`, so accented capitals recase |

Nothing here contradicts -1690/-1692; the first two rows are the ones I would show the owner, since a BLM band's own
agency name can appear lowercased inside a bubble that begins "BLM trail".

## 4. The tap path, the stoppers, the judging rule (read; the table computed)

**One path, attributes carried, no second mechanism.** `MapLibreProbe.hitsAt/hitsInBox` (`FanOutLayers.kt:353-358`)
→ `tapHitOf` adds `attributes` only for a band layer (`OverlayFeatures.kt:38`; empty map for every app layer, asserted
`ReferenceTrailTapTest:1097-1099`) → `resolveTap` unchanged (`TapPrecedence.kt:84-90`) → `mapTapOutcome` builds the
record from the hit's attributes before the unidentified case (`:106-113`) → `dispatch` → `MapTapSinks.onReferenceTrailTap`
(`MapTapHandler.kt:261`) → `MapListeners.kt:121-125` makes `MapFeatureTap.forReferenceTrail` and calls the same
`state.onFeatureTap` as every bubble → `tappedThingOf` reads `referenceTrail` first (`MapBubbles.kt:181-184`) →
`ReferenceTrailTarget` → `MapBubbleLayer.kt:86` → `MapFeatureBubble` `ReferenceTrail` branch (`:217-220`). Both hosts
that take a feature tap (`AvailabilityCompactMapUi.kt:294`, `CartographyEntryReportScreen.kt:330`) go through
`tappedThingOf`, so no host sees the empty `featureId` (read, grep of every `onFeatureTap` reader). `focusedFeature` is
`null` for the target (`MapBubbles.kt:123-126`; asserted), so the bubble keeps the tap point like a line's.

**Bands queried only while drawn.** `tappable` drops a band id unless `layerDrawn(id)` (`MapTapHandler.kt:383`);
the host's `layerDrawn` reads `layerPaintFor(spec, state.layersState)` (`MapListeners.kt:97`), and that `layersState`
is the **drawn** state: `SightingsMapState.layersStateState = currentLayersStateState` (`SightingsMap.kt:551`) =
`rememberUpdatedState(drawnLayersState)` (`:321`) = `withReferenceTrailsHiddenUnlessLoaded(…, referenceTrailsLoaded)`
(`:315-317`), set from `applyReferenceTrailsOnStyleLoad` on each style load (`MapStyleReload.kt:146`). The BLM band
follows the FS band's state (`stateOwnerId`, `MapLayers.kt:439`), so hiding the owner hides both; asserted for both in
`on a style without the band…` (`ReferenceTrailTapPrecedenceTest:1219-1228`, with the positive control). `MapTapHandler`
is built in one place (`MapListeners.kt:83`; grep), so every map host gets this. **On v2 nothing is tappable** and
the stopper query never runs, since it runs only after a band has resolved (`:390`).

**The stoppers.** `REFERENCE_TRAIL_BLOCKER_LAYER_IDS` (`TapPrecedence.kt:34-46`) = the eight `*_LAYER` constants of
`RouteHomeIds` (`RouteHomeLayers.kt:62-91`: straight, straight-offline, passed, ahead-casing, ahead, ahead-dotted,
ahead-straight, arrived; computed by grep, eight) + `BREADCRUMB_CASING` + `BREADCRUMB`. The test derives the eight by
reflection over `RouteHomeIds` with a count-of-eight guard against an empty reflection (`:1293-1306`) and holds the
code's list equal to reflection + breadcrumbs (`:1319-1321`). **No route is made tappable**: the stopper ids go only to
`probe.hitsAt/hitsInBox` inside `bandStopped` and are read as a boolean (`:405-409`); no `TapHit` from them reaches
`resolveTap` or `dispatch`; `BREADCRUMB*` and the route layers keep `TapGroup.NONE` / no registry entry (read,
`MapLayers.kt:445-447`). The test `the way home stops only a band, never a track, a marker or a cell` confirms a route
under a track, a marker or a cell leaves them the tap (`:1340-1348`). `queryRenderedFeatures` with a layer id the
style does not hold (the route layers before a way home is drawn) returns nothing (inferred from MapLibre's layer-id
filter; not read at the native source today).

**The point/box judging rule** (`:405-409`): a stopper exactly under the finger stops the band; a stopper only in the
box stops it only when the band too was only in the box. So **a band under the finger is never lost to a route beside
it** (asserted `:1324-1328`), and both-in-the-box goes to the route (asserted `:1331-1338`), as a tappable app line in
the box would have beaten the band. Consistent with `resolveTap`'s own two stages. **Can a band under the finger be
unreachable?** Only with a stopper also under the finger, which is the ruling. **What the null winner costs** is the
should-fix: my port's table (`tap_table.py`; band, route, kept track each at point/box/absent, cell at point/absent):

| band | route | track | cell | pre-8c | 8c |
|---|---|---|---|---|---|
| P | P | B | any | track | **plain** |
| P | P | – | P | cell | **plain** |
| B | P or B | – | P | cell | **plain** |

All other 30 configurations give the pre-8c winner or the band. In each lost row the band had won `resolveTap`, was
stopped, and nothing re-resolved.

## 5. Precedence below tracks, markers and outlines (read; asserted by the coder's tests)

Registry order within `ZGroup.LINES`: the three band specs first (`MapLayers.kt:438-440`), then offline outline, the
breadcrumbs, kept tracks; `orderedLayers` keeps registry order for non-reorderable layers and no line is reorderable
(`line()` sets `userReorderable = false`, `:267`; `referenceTrailLayer` too, `:353`), so `tapWinner`'s "higher layer
first" ranks every app line above both bands (`TapPrecedence.kt:64-74`). Tests: every `TapGroup.LINE` layer in the
registry beats either band, with a guard that kept tracks and the outline are in the set (`:1252-1261`); a marker beats
a band (`:1264-1266`); a band beats a cell by point and through the box (`:1276-1282`); a track in the box loses to a
band under the finger (`:1269-1273`, the same two-stage rule as any two lines). R2 (`2cd55d933`, FS band made a
`MARKER`) fails exactly the two precedence tests (§7).

## 6. The screen test: real coordinate touches (read `ReferenceTrailBubbleScreenTest.kt`)

`MapLayersTestScreen` composes the real `AvailabilityScreen` (`MapLayersUiFixtures.kt:272-294`). The map slot is a
`Box` with `detectTapGestures` feeding the **real** `MapTapHandler` over the **real** registry's draw order (`:873-893`);
touches are `performTouchInput { click(Offset(x·density, y·density)) }` on the map node (`:909-912`), not semantic
clicks. The first test touches 3 points along × 5 across a 10 dp band (edges at ±4 dp, quarter points, centre: 15 real
touches), asserting both lines by tag and text and Back closing each time (`:933-947`); a tap 200 dp away closes
(`:950-956`); the fill alpha is `MAP_CHROME_OVER_MAP_ALPHA` and no Details/Directions action exists (`:959-966`); BLM
and number-only forms through a touch (`:969-984`); and for each of the way home (`AHEAD_LAYER`) and the breadcrumb, 3×3
touches on the crossing open nothing while 3×3 beside it (15 dp either side, within the finger's reach of the line, and
60 dp) open the bubble (`:991-1021`). The band and the lines are the probe's geometry, so what is tested is routing and
wiring through the real screen, not a rendered band; a finger on a real 6-10 dp band is device-only, as the coder said.
Robolectric's zero insets are not in play here (no inset-dependent layout is asserted).

## 7. Fail-first and reverts: each bite (read, JUnit XML and logs of all twelve red runs, downloaded)

Compile check first: every red run's unit-test artifact holds JUnit XML for the new classes (the tests ran, so the build
compiled), and the four name-rule runs' logs show `12 tests completed, 1 failed` / `13 tests completed, 1 failed` with
no `e:` line (filtered to `ReferenceTrailRecordTest`). Each revert is a one-line edit (read, `git diff` by sha):

| Run | Edit (reverted or stubbed) | Failures | Specific to the edit? |
|---|---|---|---|
| fail-first 40404270706 | stubs | 21 of 27: record lines `""`, `attributes={}`, `UnidentifiedFeature(reference_fs_band)`, bubble never opens | yes |
| R1 `1d58d44e8` | `tappable` filter removed | the two drawn-state tests: `expected:<[plain]> but was:<[reference:…]>` | yes |
| R2 `2cd55d933` | FS band → `TapGroup.MARKER` | `a kept track over a band…`, `every tappable app line…` (first: outline over FS band) | yes |
| R3 `7ab3bd97a` | `ReferenceTrailTarget -> null` in `MapBubbleLayer` | all 5 screen tests: "positive control: the band's bubble opened" | yes |
| R4 `2dcceff49` | `attributes =` line removed from `tapHitOf` | 4 of 6 `ReferenceTrailTapTest`: `attributes={}`, record with null fields | yes |
| A2 fail-first 40409914895 | stubs | 3 precedence (stoppers), 2 screen (crossings), 3 record (`S[PUR]`, `W[EST LEG NORDIC]`) | yes |
| A2 blocker `eef368e2b` | `val winner = resolved` | 2 precedence + 2 screen crossing tests; the list test passes (list kept) | yes |
| A2 case `450f09e06` | `shownName(it)` → `it` | 3 record tests, `S[pur]` vs `S[PUR]` | yes |
| codes fail-first / revert `698901dff` | `keptAsPublished` not consulted | `…codes, numerals and words with digits…` at `:72` | yes |
| abbrev fail-first / revert `e1c74780a` | `abbreviated = false` | `…abbreviations with a full stop…` at `:104` | yes |

Each failure named is one its edit, and only that edit, produces. **All bite.** I did not run my own Gradle revert
(reviewer profile step 5): the laptop is the forecast machine and these are CI-only builds; the evidence above is the
runs' own XML read against the reverts' diffs, which is the check the profile's step 5 exists to make.

## 8. street-v3 regenerated with Node as pinned (computed)

Node 24.22.1 (`~/.local/opt/node-v24.22.1-linux-x64/bin/node`; CI pins 24.22.1 at `.github/workflows/ci.yml:655, 702`,
read) over a `git archive` of the PR's `map-style/` in `/mnt/work/scratch/sorrel-419/regen/`, with `node_modules`
(`@protomaps/basemaps` 5.7.2) and the fetched sprite assets symlinked from the 8a worktree (its `package-lock.json`
identical to the PR's, computed by `diff`). `node generate-street.mjs` → "96 layers". All three outputs **byte-identical**
to the committed files (`cmp` exit 0): `forager-street-v3.json` and `server/pmtiles-worker/src/street-style-v3.json`
sha256 `7ad55d46…1694`, `forager-street-v3.preview.json` `7ca2225f…7130`. The two constants are used by no layer
(`grep` over `map-style/` finds only their definitions, `forager-reference.mjs:48-49`), and no commit on the branch
touches the three JSONs (`git log` on them ends at `6cfed1aba`, 8a).

## 9. Against the dispatch, in order

1. Bubble at 80% chrome, first and second lines ✓ (§2, §6). 2. Markers and app lines first, band before colour fields
✓ (§5). 3. Back or a tap elsewhere closes ✓ (§6). 4. Missing parts read only what exists ✓ (§2). On v2 nothing is
tappable ✓ (§4). Amendment 1: fields in `forager-reference.mjs`, v3 byte-identical ✓ (§8); the wider file list used,
no second mechanism ✓ (§4); BLM wording and the four forms ✓. Amendment 2: routes block a band tap as a blocker, not
as tappable ✓, with the should-fix on what the blocked tap becomes (§4); normal case for all-capital names with the
rule stated ✓ and its edges listed (§3); the planner's three accepted items hold (§4, notes). Stops: no visible change
on v2 ✓; no wording beyond the confirmed lines ✓ (the only strings are the two lines and the agency prefixes); no file
near 800 ✓; other coders' files untouched ✓; no wrong premise found.

## 10. Disclosure

**Confirmed (read or computed today):** everything marked so, at `8ca59fe17`, `c445574da`, `origin/records-after-173`
@ `edab1ecf2`, `origin/later-steps-reports` @ `e4f9324c8`, the twelve runs' artifacts and the eight revert commits.

**Inferred:** `queryRenderedFeatures` with an absent layer id returns nothing; Kotlin's `lowercase()` locale
independence; how often a kept track lies 3-24 dp beside a trail the way home follows.

**Could not determine:** the coder's completion report (not on the branch; not needed for the above); whether the final
`pull_request` run's checkout included #420 (merged five minutes before; no overlapping file either way).

**Premises that were wrong or moved:** none in the dispatch or its amendments against the tree. The step 8 report §8
named `FanOutLayers.kt` as a possible touch point; it was not needed.

**Decided beyond scope:** nothing. No code, test, branch or PR touched; the scratch work lives under
`/mnt/work/scratch/sorrel-419/`; `docs/audits/README.md` not touched (the planner's serialization point).
