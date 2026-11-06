# Review: Forager PR #464, the night pass picks Street tables in one place

Sorrel, 2026-11-01, about 23:40 PST. Dispatch: forager-cartography `prompts/preserved/2026-11-01-sorrel-464-night-tables.md`
(commit 4604513). Reviewed head **`eafc26a5e`** (`eafc26a5ef002b90a62875759f1e5ac0c68966d2`), branch
`night-tables-by-source`, base `pre-main` at `6522e5d1e`. Read-only; no local Gradle builds; evidence from CI.

## Verdict

- **Blocking:** none.
- **Should-fix (not blocking):** SF-1, the source-text check can be fooled (below).
- **Notes:** N-1, N-2.

**Is #464 mergeable into `pre-main` at `eafc26a5e`? Yes.**

## Answers to the dispatch's questions

### 1. Is every night-table choice routed through `streetNightTablesForSource`?

Yes, for every choice in production code today (read at `eafc26a5e`):

- `MapStyleReload.kt:147` `val tables = streetNightTablesForSource(source)` is the only call that chooses tables. Its
  result goes to `applyOfflineNightRecolour(..., streetTables = tables)` (`:148`) and `tables.hillshade` (`:150`).
- `streetNightTablesFor(` is called only inside `StreetNightTables.kt:91` (the new function). The other matches in
  `app/src/main` are KDoc links (`OfflineNightRecolourApply.kt:29`, `BasemapStyles.kt:464`, `NightColour.kt:157`).
- `applyOfflineNightRecolour(` has one caller (`MapStyleReload.kt:148`). `streetNightRecolourOf(` has one caller
  (`OfflineNightRecolourApply.kt:49`), and it passes the `streetTables` it was given.
- The default arguments `streetTables = STREET_NIGHT_TABLES` (`OfflineNightRecolourApply.kt:32`) and
  `tables = STREET_NIGHT_TABLES` (`NightColour.kt:163`) are still there, but no production call uses them today. See N-1.

### 2. Same tables as before #464 for every source? Any behaviour change?

No behaviour change. Before #464, `MapStyleReload` called `streetNightTablesFor(streetUriOf(source))`. After #464 it
calls `streetNightTablesForSource(source)`, whose body is exactly that expression (`StreetNightTables.kt:91`). So
every source gets the same tables:

| Source | `streetUriOf` (`BasemapStyles.kt:468-472`) | Tables |
|---|---|---|
| Street v4 `Uri(STREET_STYLE_URL)` | `…/street-v4.json` | v4 |
| Street v3/v2/v1 `Uri` | their URL | v1–v3 |
| Offline `Uri(OFFLINE_STYLE_URL)` | `…/offline.json` | v1–v3 |
| Topographical at night, `StreetWithTopoOver(STREET_STYLE_URL, …)` | `streetUri`, v4 | v4 |
| `Json` (raster OSM / Topographical day, Trail Map fallen back) | `null` | v1–v3, but the night pass skips `Json` (`MapStyleReload.kt:146`) |
| Satellite | — | No such source: removed (`Basemap.kt:44-52`) |

### 3. Is the source-text check robust?

It catches what it was written for: both revert checks fail (Willow's on the call, mine on the function body). But
it is easy to fool. See SF-1.

### 4. My revert check

- Throwaway branch **`sorrel-464-revert-check`**, commit `3296299e3`, on top of `eafc26a5e`. It changes one line:
  `StreetNightTables.kt:91` goes back to picking by a plain URI only, as before #462:
  `streetNightTablesFor((source as? MapStyleSource.Uri)?.uri)`. `MapStyleReload` is left alone, so the source-text check
  still passes. This exercises the two behaviour tests, which Willow's revert (`80bbc697e`, which changed
  `MapStyleReload` instead) did not reach.
- CI run **41405905516** (workflow_dispatch) failed in "Build, test, publish APK"; every other job was green.
  `5992 tests completed, 2 failed, 24 skipped`:
  - `NightTablesBySourceTest > each source takes the tables of the Street it draws` at **`NightTablesBySourceTest.kt:24`**
    (Topographical over v4)
  - `NightTablesBySourceTest > the app's own Street under the night topo takes v4's tables` at **`NightTablesBySourceTest.kt:31`**
  - The third test (source-text check) passed, as expected.
- I didn't delete the branch (that's outside my bounds); it's named here for whoever cleans up.

So both halves are now shown able to fail: the source-text check (Willow, run 41395977468, `:37`) and the behaviour
tests (this run, `:24` and `:31`). The head itself is green (run 41395980243 at `eafc26a5e`, all jobs success; I checked
the head SHA).

### 5. Is the diff confined to S-1?

Yes. Three files: one call changed in `MapStyleReload.kt`, the new 1-line function with KDoc in
`StreetNightTables.kt`, and the new `NightTablesBySourceTest.kt` (40 lines). Nothing else. Tests came first
(`094256be3`), then the change (`eafc26a5e`). It merges clean into `pre-main` `6522e5d1e` (`git merge-tree`), and
no other commit on `pre-main` since `2bc8913f4` touches `ui/map/`.

## SF-1 (should-fix, not blocking): the source-text check can be fooled

`NightTablesBySourceTest.kt:36-38` reads `MapStyleReload.kt` as text and counts regex matches. From reading it (I did
not run these):

- **Comments count.** A comment containing `streetNightTablesForSource(source)` satisfies the "exactly 1" assertion
  even if the code then does `val tables = STREET_V4_NIGHT_TABLES`.
- **Direct table picks aren't forbidden.** Only `streetNightTablesFor(` is banned. A literal `STREET_V4_NIGHT_TABLES` /
  `STREET_NIGHT_TABLES`, or leaving out `streetTables =` so the default (`OfflineNightRecolourApply.kt:32`) applies,
  passes the check.
- **Other files aren't checked.** A helper elsewhere that picks tables and is called from `MapStyleReload` passes. So
  does a second recolour call added in another file.
- **A rename** of the `source` variable fails the check, which is a false red. That's harmless but noisy.

Cheap tightening, if Willow wants it: strip `//` and `/* */` comments before matching, and also assert that the text
holds no `STREET_V4_NIGHT_TABLES|STREET_NIGHT_TABLES` and that `streetTables = ` appears in the
`applyOfflineNightRecolour(` call.

A behaviour test would be better. For example, pull out a pure `nightPassFor(source, night): NightPass?` (tables,
`streetWater`, hillshade) that `MapStyleReload` only applies, and test it per source. That would hold the whole night
decision, not just the table choice. **Not blocking.** S-1 asked for "a test that the night pass really uses
`streetUriOf`, *or* a pure `nightTablesFor(source)`". #464 gives the pure function with behaviour tests (shown able to
fail), plus a guard on the one call site. That meets S-1.

## Notes

- **N-1.** The default arguments `streetTables = STREET_NIGHT_TABLES` (`OfflineNightRecolourApply.kt:32`) and
  `tables = STREET_NIGHT_TABLES` (`NightColour.kt:163`) are a second, silent way to choose v1–v3 tables. Today nothing
  in production relies on them. Removing the default on `applyOfflineNightRecolour` would make any new caller choose
  on purpose. That belongs to a later change, not #464.
- **N-2.** The `Json("{}")` assertion (`NightTablesBySourceTest.kt:26`) pins a value the night pass never uses (it
  skips `Json`). That's fine as documentation, but it's not evidence about the night path.
- Not tested on a device (as Willow says). The change is a pure refactor with the same expression, so I expect none to
  be needed for this PR.
