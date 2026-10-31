# Review 2026-10-31: pi-36, orchards, plant nurseries and vineyards as farmland with `kind_detail` (patch 0006)

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-31-sorrel-pi36-orchard-kinds.md`
in Forager-cartography. Started about 09:40 PDT, finished about 10:30 PDT on 2026-10-31. Times are Pacific. Each claim
is marked **read**, **computed** or **inferred**. Nothing in `/opt` or `/srv` was touched; one Planetiler was not
needed, Willow's two Oregon archives were enough to recompute everything.

## Prediction (formed on reading the patch, before the build and the tile work)

1. The patch is minimal: one rule widened, one guarded attribute, `null` omitted by Planetiler; the matcher's
   last-rule-wins order keeps a national park's kind and the guard keeps `kind_detail` off it.
2. The 6 cases and the gate guard the change; dropping the guard fails only the national-park case, and `mvn` stops
   before the gate.
3. v1 to v3, light and the offline style draw no landuse farmland, so nothing changes on today's phones; v4 at
   `c2e06dff6` newly fills and (from z13) edges nurseries and vineyards as farmland, and draws orchards as before.
4. Merging happens only at z ≤ 6, and only a few dozen bytes move there; the tile growth at z7 and up is the new
   polygons and the attribute, not merging.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `pi-36-orchard-kinds` | `4decb59ea` | `4decb59eac5249e019264f00f696d45c75ac81d9`, one commit on `a5bcb0780` |
| `pi-build`, installed | `a5bcb0780` | `origin/pi-build` = `/opt/forager-build/map-build/COMMIT` = `a5bcb0780`, an ancestor of the branch: the merge is a fast-forward in content |
| Willow's jar | `8ab7a1be…5041b` | `~/scratch/willow-orchard/jars/pi36.jar` sha256 `8ab7a1be39539d2a6f94…`, 88,620,090 B |
| The archives | +0.14 %, 229,832 B | Oregon installed (0005) 457,906,252 B → pi-36 458,535,950 B, **+0.138 %** (computed); largest z10 tile 229,832 B (computed, below) |

No `build-usca` process; the service is inactive. The delta is four files: the patch (112 lines), nine lines in
`build-basemap-jar.sh`, `pins.env` (0006 appended), two README lines.

## 1. The patch (read)

- **The rule.** `with("landuse", "farmyard", "orchard")` → `with("landuse", "farmyard", "orchard", "plant_nursery",
  "vineyard")`, `use("kind", "farmland")`, in its existing place in the ordered rule list. `Matcher.getString` walks
  the matches **reversed**, so a later rule (the `boundary=national_park` rules that give `park` or `national_park`,
  the forest rule) still wins the kind, exactly as it did for orchards before.
- **The attribute.** In `processOsm`, after `kind` is resolved: `kindDetail = "farmland".equals(kind) &&
  FORAGER_FARMLAND_DETAILS.contains(landuse) ? landuse : null`, then `.setAttr("kind_detail", kindDetail)`. Planetiler
  drops a `null` attribute, so farmyard, plain farmland and every non-farmland kind carry no `kind_detail` key at all
  (the test asserts the key is absent; the tiles confirm it, below). Farmyard is in the rule but not in the set, so it
  stays plain farmland. Nothing else in `Landuse.java` changes; `postProcess`, the zoom range (2 to 15) and the
  min-pixel size are untouched.
- **Minimal:** yes. Two lines of behaviour and a constant.

**From the tiles (computed, Willow's Oregon archives decoded):**

| Tile | Landuse features | What changed |
|---|---|---|
| 13/1305/2937 (Haines Rd / Bremer Rd) | 30 → 34, 7,988 → 8,155 B | 4 new polygons (2 plant_nursery, 2 vineyard); 4 of the 16 farmland polygons gain `kind_detail: orchard`; with the new polygons set aside and `kind_detail` removed, the 30 features are identical to the base tile's; every other layer byte-identical |
| 12/652/1468 | 149 → 160 | 11 new (5 nurseries, 6 vineyards); 6 orchards keyed; the rest identical |
| 10/162/366 (the largest z10) | 3,078 → 3,160 | 82 new; 236 features keyed, all `farmland` with one of the three details; 413 farmland without the key |

In every tile the set of (kind, kind_detail) pairs with a key is exactly {farmland × orchard, plant_nursery,
vineyard}: no key on any other kind. So Willow's "gains only `kind_detail`" is short by one clause: the tiles also
gain the nursery and vineyard polygons themselves, which upstream never wrote. That is the point of the patch, and
the counts above are what the +0.14 % is made of.

**The Canby box (computed):** of the 25 z13 tiles with farmland in 45.17 to 45.30 N, 122.55 to 122.72 W, 23 carry a
`kind_detail`; 73 orchard, 41 plant-nursery and 9 vineyard features (merged linestrings count as one). Willow's
corrected figure, 23 of 25, holds.

## 2. The tests and the gate (read and computed)

- **The cases.** `foragerOrchardKindDetail` (3): each of the three gives `kind farmland, kind_detail <landuse>` at
  z15. `foragerNoKindDetail` (3): farmyard and farmland give `farmland` with no key; `orchard` + `boundary=national_park`
  gives `park` with no key. The second group is what guards the "never on an overridden kind" rule.
- **The gate.** `build-basemap-jar.sh` counts `foragerOrchardKindDetail|foragerNoKindDetail` cases in the LanduseTest
  report and requires exactly 6 with no failure in that report; the existing 4-case forest gate uses a different name
  and is unchanged. Without 0006 the count is 0 and the script dies there; with 0006 and a failing case, `mvn package`
  exits non-zero first.
- **Willow's revert (read, `revert.log`):** 0006 dropped, 30 LanduseTest cases run, 3 fail, all three
  `foragerOrchardKindDetail` (orchard loses its detail; nursery and vineyard produce no feature). The three
  `foragerNoKindDetail` cases pass without the patch, as they should: they pin what must not change.
- **My build (computed):** upstream `ca93fc06e` cloned locally, patches 0001 to 0006 from this branch applied with `git apply --check` (0006 applies cleanly on 0001 to 0005), `mvn package` with a copy of Willow's Maven cache: 512 tests, 0 failures, 52 s. The gates as the script computes them: roads 29 and 7, landuse forest 4 and orchard 6 of 30 LanduseTest cases, barriers 8, buildings 3, water 3, no failures. The jar is 88,620,090 B like Willow's; its sha256 (`3cfb5c99…`) differs from Willow's by build time (inferred, as at pi-35).
- **My revert (computed):** the `"farmland".equals(kind) &&` guard removed, everything else as merged. `mvn package` exits 1 with **1 failure and 36 errors**: the failure is `foragerNoKindDetail[3]`, the orchard in a national park, as predicted; the errors are `NullPointerException`s in 14 LanduseTest cases and 22 elsewhere, because `Set.of(...).contains(null)` throws, and without the guard every polygon with no `landuse` tag (a `natural=wood`, a `leisure=park`) reaches it. So the guard does two jobs: it keeps `kind_detail` off overridden kinds, and it is the only thing between a null `landuse` and a crash. Today the second job is never called on: the sole rule giving kind `farmland` is `with("landuse", …)` (line 171; the other mention of farmland is a comment), so `landuse` is non-null whenever the guard's first half is true (read). Should-fix 1, below.

## 3. Today's phones (read)

Every landuse-reading layer of `forager-street-v1.json`, `v2`, `v3`, `forager-light.json` and the Worker's
`offline-style.json` at `street-v4` (`c2e06dff6`) was listed: none has `farmland`, `orchard`, `vineyard`, `nursery` or
`kind_detail` in its filter or paint. The app bundles no map style JSON (its assets are the credits file and the night
sprite); its raster styles are built in code for OpenStreetMap and Topographical. So on v1 to v3, light and offline the
new polygons and the new attribute are not drawn, and nothing changes on today's phones, whether or not the archive is
later uploaded. Willow's claim holds.

## 4. v4 (read)

At `c2e06dff6` two layers read farmland: `landuse_settled` (fill `#e8eacd`, kind in residential, commercial, railway,
farmland) and `landuse_edges` (the farmland edge `#ccceb4` from z13). Neither reads `kind_detail`. So, after the
upload: orchards draw exactly as now (they were farmland already); plant nurseries and vineyards, which drew nothing,
take the farmland fill and, from z13, the farmland edge, until Sage's layer tells them apart. No label layer reads
landuse farmland. This is exactly Willow's statement, with the edge added.

## 5. Merging (computed and read)

`Landuse.postProcess` calls `FeatureMerge.mergeNearbyPolygons` only when `zoom <= 6`; at 7 to 15 it only filters by
area. Summing every tile's gzipped bytes over Oregon's bounding box, base → pi-36: z0 to z5 unchanged to the byte; z6
+25 B; z7 +231 B; z8 +3,046 B. The z6 figure is the merging cost Willow named (an orchard beside farmland no longer
merges into one polygon there); z7 and z8 grow because the new polygons and the attribute exist, not because of
merging. Harmless: tens of bytes at z6, and the landuse layer at z ≤ 5 is unchanged.

## 6. The install (read)

Coder-06's step 5 is coder-05's step 5 word for word: a `--no-ff` merge into `pi-build`, the jar built and installed
with its sha256 recorded, `install-map-build.sh` from a clean worktree recording `COMMIT` and `map-build.prev`, the
rollback steps written down, a 22:00 PDT stop. The pi-35 install left what that plan promises: `map-build.prev` holds
`COMMIT` `288ce80cf`, the installed jar is `8dd2a0f2…` (the script's own build; scratch jars differ by timestamp), and
the rollback is recorded in Willow's 23:10 report (steps 1 to 5: move the prev tree back, restore the jar). The same
plan applies; the prev tree will then hold `a5bcb0780`.

## Verdict

### Blocking

None.

### Should-fix

1. **Make the null case explicit (robustness, not behaviour).** `FORAGER_FARMLAND_DETAILS.contains(landuse)` throws on a null `landuse` because `Set.of` rejects nulls; today the `"farmland".equals(kind)` short-circuit shields it, by the accident that kind `farmland` only ever comes from a `landuse` tag. A future rule that gave farmland from another key would crash the whole build on the first untagged polygon. Write `landuse != null && …` or use a `HashSet`. One line, no test change; fine to do in the next pass rather than before Sunday.

### Notes

- "The tile gains only `kind_detail`" undersells it: it also gains the nursery and vineyard polygons, 4 in the named
  z13 tile, 82 in the largest z10 tile. Say so when telling the owner what the upload changes.
- The largest Oregon z10 tile grows 227,049 → 229,832 B, still well under 500 kB; the full-run worst case is the New
  York tile from pi-35, which this patch cannot move by more than farmland's share.
- The pi-35 rollback steps live in a report, not in the repository; a line in the map-build README would make them
  findable for the next reviewer.

### Prediction, scored

1 held. 2 held, and the revert showed more than predicted: the guard also prevents a null-pointer crash (should-fix 1). 3 held. 4 held: 25 bytes at z6, nothing at z ≤ 5.

**Is pi-36-orchard-kinds mergeable for install before Sunday's run? Yes.** The patch does exactly what was asked and
nothing else, `kind_detail` sits only on farmland and never on an overridden kind, the gate and the tests catch both an
absent patch and a dropped guard, today's phones cannot see the change, v4's effect is as stated plus the farmland
edge, and merging moves 25 bytes at z6. Install by Willow is the owner's go, by 22:00 PDT or not at all.
