# Trip-window guidance text: code and tests written, nothing compiled yet

Dispatch 2026-09-28-695 (RECORD intent -695; preserved at `prompts/preserved/2026-10-07-16.md`). Branch `guidance-text`,
cut from `origin/main` at `b71c1569` (fetched immediately before the worktree was cut). No PR.

**Status: written, not run.** Gradle waits for the planner's go. Nothing on this branch has been compiled, no test has run,
and no revert check has been done. Every claim below about behaviour is what the code is written to do, not something observed.

Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/` unless they start with `app/` or `docs/`.

## Premises checked

| Dispatch premise | At `b71c1569` | Held? |
|---|---|---|
| The "No weather guidance" block is the `else` branch of `domain/ForagingWeatherGuidance.kt`, about :142 | `:142`, heading "No weather guidance for this selection" | Yes. The owner's quote says "section"; the on-screen word is "selection" |
| The italic note is `speciesCaveat`, about :154 | `:153`, rendered italic at `ui/availability/AvailabilityTripWindowsUi.kt:151-153` | Yes |
| `ForagingSelection.forChip` gives a `SpecificTaxon` a null group, about :45 | `:48-51` | Yes |
| A species loses its group when chosen "from a chip, a recent search or a restored state" | Only one of the three exists. `foragingSelection` is set at exactly two places: `AvailabilityViewModel.kt:427` (`fromSearchResult`, keeps the group) and `:906` (`onRecentSearchSelected`, `forChip`, loses it), plus the default `forChip(TaxonFilter.FUNGI)` at `AvailabilityUiState.kt:85`. No picker offers a species as a chip, and the filter is not restored from anywhere but a recent search (no `SavedStateHandle`, no DataStore key). So the one path that drops a species' group is the recent search | **Partly wrong**: "chip" and "restored state" are not separate paths here |
| No screen test reads the removed strings | `git grep` over `app/src/test` finds them only in `ForagingWeatherGuidanceTest` | Yes |

## Item 3 is stopped: it needs a Room change

A recent search is a row of the Room table `cached_searches` (`data/local/CachedSearchEntity.kt`), read back as
`CachedSearchSummary(region, month, filter, cachedAtEpochMillis)` (`domain/SearchCacheRepository.kt:51`). For a species the row
stores the taxon id and label only: `filterIconicTaxonName` is "non-null exactly for `IconicCategory`" and is the column
`toFilter()` uses to tell the two filter shapes apart (`data/repository/RoomSearchCacheRepository.kt:126-135`). The row is
written from `AvailabilityForecast.filter` (`RoomSearchCacheRepository.kt:75-95`), which carries no group either, and the ranked
entries (`SpeciesObservationCount`) carry no iconic name to derive it from. So the group cannot travel with a recent search
without storing it, which the dispatch says to stop and propose. The options:

- **A. A new nullable column on `cached_searches`** (for example `filterSpeciesIconicTaxonName`), a `ForagerDatabase`
  version bump from 18 with its migration, the group carried into `save` (on `AvailabilityForecast` or as a parameter, through
  `GetAvailabilityUseCase`), and `CachedSearchSummary` gaining the group so `onRecentSearchSelected` can build the selection
  with it. Rows saved before the change have no group, so a species reopened from an older recent search would still show no
  guidance until it is searched by name again. Nothing extra is fetched.
- **B. Reuse `filterIconicTaxonName` for species rows.** No schema change, but it changes what an existing column means and
  breaks the discriminant `toFilter()` relies on (it would need reordering to test the taxon id first). Older rows have the
  same gap as A. Not recommended: it is a Room change in substance without the migration that would make it visible.
- **C. Look the group up from iNaturalist when a recent search is reopened.** No storage change, but it changes what is
  fetched, and fails offline, which is the case recent searches exist for.
- **D. Keep a taxon-id-to-group map in memory for the session.** No storage or fetch change, but it is lost on restart, which
  is when a recent search is most often reopened. It would fix the screenshot's case only if the species had been searched by
  name earlier in the same session.

Until the owner picks one, a species reopened from a recent search has no group, and after items 1 and 2 that means its Trip
Windows card shows no guidance at all (no heading, no paragraph), rather than the old "No weather guidance" block. Lichens keep
their deliberate null group under every option.

## What was built

1. **The "No weather guidance for this selection" block is gone** (`domain/ForagingWeatherGuidance.kt`).
   `ForagingWeatherGuidance.forSelection` now returns `Guidance?`: the fungi or plants text for those groups, and `null` for
   any other group or an unknown one. `TripWindowsCard` (`ui/availability/AvailabilityTripWindowsUi.kt`) draws the divider and
   the guidance section only when there is guidance, so a selection with no group ends the card at its measurements.
2. **The italic "No species-specific data is available for ..." note is gone everywhere.** `Guidance.speciesDataCaveat` and
   `speciesCaveat` are deleted, and the italic `Text` with them. A species now gets exactly its group's guidance, unchanged
   (the fungi and plants texts themselves are untouched).
3. Not built: item 3, above.
4. **The KDoc rules** at the top of `ForagingWeatherGuidance` are now two: nothing here is species-specific (a species gets
   its group's text unchanged), and groups do not share text (a group with nothing written gets nothing). Rule 3, "a specific
   taxon always gets the caveat", is removed, and the comment records what was removed, the owner's words, and the rejected
   alternative (keeping the text, which was honest but read as a placeholder). `forChip`'s lichens note now says Forager
   "shows none" instead of "says so".

## Tests

### Changed assertions in `app/src/test/.../domain/ForagingWeatherGuidanceTest.kt`

| Test (old name, then new where renamed) | Old assertion | New assertion | Reason |
|---|---|---|---|
| `fungi guidance states the rain lag pattern...` | `assertNull(guidance.speciesDataCaveat)` | removed | The field no longer exists. The two text assertions stay |
| `plants guidance says plainly...` | read through `guidanceFor` | read through `writtenGuidanceFor` (fails with a message if null) | `forSelection` is now nullable; the assertions are unchanged |
| `a category with no written guidance says so rather than borrowing another's` → `...shows none rather than borrowing another's` | heading equals "No weather guidance for this selection"; text has no fungi lag; text has "nothing sourced for this selection" | `assertNull(guidanceFor(insects))` | Item 1: no block at all. Null also cannot borrow another group's text, so the old "no fungi text" check is subsumed |
| `a specific species falls back to its category's guidance` → `a specific species shows exactly its category's guidance` | paragraphs equal the Fungi category's | the whole `Guidance` (heading and paragraphs) equals the Fungi category's | Stronger: with no caveat field, the species' guidance should now be identical, not just share paragraphs |
| `a specific species states explicitly that no species-specific data exists` → `a specific species carries no species note and nothing names it` | caveat non-null, contains "No species-specific data is available for chanterelles", "not a claim about chanterelles", "general pattern for fungi in general" | for Fly Agaric (`Fungi`): heading is the fungi heading; heading plus paragraphs contain neither "No species-specific data" nor "Fly Agaric" | Item 2, inverted: the note must not appear |
| `the caveat is present even when the species has no category guidance to fall back on` → `a species with no known group shows no guidance at all` | heading "No weather guidance..."; caveat non-null, names "Some Beetle" | `assertNull(guidanceFor(unknownGroup))` | Items 1 and 2 together |
| `the lichens chip is not given the fungi fruiting pattern` → `..., or any other` | text has no fungi lag; caveat non-null, names "Lichens (approx.)" | `assertNull(guidanceFor(forChip(LICHENS)))` | Lichens' null group now means no guidance; stronger than "not fungi" |
| `every default selection produces guidance, and only a specific-taxon selection carries a caveat` → `of the default selections, fungi and plants have guidance and lichens has none` | caveat-present map `Fungi false, Plants false, Lichens true` | guidance-present map `Fungi true, Plants true, Lichens false` | The caveat is gone; what now differs between the defaults is whether any guidance shows |
| `no guidance text states a score, probability or best day` | scanned paragraphs plus caveat | scans heading plus paragraphs, and nothing for a null guidance | The caveat is gone; the heading is now scanned as well, which widens the check (none of the current headings contains a forbidden word) |

The two selection-plumbing tests and the three soil-band tests are unchanged apart from reading through the nullable helper.
`assertNotNull` was dropped from the imports as unused.

### New screen test: `app/src/test/.../ui/availability/TripWindowsGuidanceTextTest.kt`

The real `AvailabilityScreen` over the real `AvailabilityViewModel` (`mapLayersViewModel`, `MapLayersTestScreen`), portrait
`w384dp-h823dp-xxhdpi`. A region is searched through the coordinate callbacks, then Tools and Trip Planner are tapped, and the
rendered text is read. Each test first asserts the card rendered (its title and its "Couldn't load trip-window weather." line,
since the fixture's weather fetch fails), so an absent guidance block is the card's choice, not a card that never composed.

1. `a fungus species picked by name shows the fungi pattern and no species note`: Fly Agaric (`Fungi`) picked by
   `onTaxonSearchResultSelected`; the fungi heading and its lag sentence are shown, and neither removed text appears.
2. `the fungi category shows the fungi pattern and no species note`: the default selection (the Fungi category, which is
   what the dispatch's "from a chip" reaches at this base).
3. `a species in a group with no written guidance shows no guidance block at all`: an `Insecta` species picked by name; no
   fungi or plants heading and neither removed text.

The species is picked by calling the ViewModel callback a suggestion row's tap calls, not by typing and tapping a row,
because the shared fixture's species search returns no results, so there is no row to tap. **Not written**: the dispatch's
"from a recent search" case, because it needs item 3; it would fail at this commit, and a red or `@Ignore`d test is not an
option. With item 3 decided it is `pickByName(FLY_AGARIC)`, then `onRecentSearchSelected` with the species' recent-search
entry from `uiState.recentSearches`, then the same assertions as test 1.

## Unverified

- **Everything compiles and passes**: not run. The new test's "Tools" and "Trip Planner" taps follow `AvailabilityScreenLayoutTest`'s
  `openToolsDrawer`, at a slightly wider qualifier than that file's smallest; that they reach the section here is unrun.
- **Revert checks**: none done. Planned once Gradle is allowed, restoring from saved copies and reading the compile log first:
  (a) restore the `else -> Guidance("No weather guidance...")` branch, expect test 3 and the unit `...shows none...` and
  `...no known group...` tests to fail on the removed heading; (b) restore the caveat line in `TripWindowsCard`/`forSelection`,
  expect test 1 to fail on "No species-specific data is available for".
- **Full suite**: not run.
- Device: nothing here depends on insets or hardware; the owner's phone would confirm the card's look on Fly Agaric.

## Amendment 1 (RECORD -696): item 3 built, option A

*Supersedes "Item 3 is stopped" above, which stays as written: it was the state at `99a401a1`.* The owner chose option A,
"Save it with the search". Commit `5bc6abe4`. Still **not compiled or run**.

- `cached_searches` gains nullable `speciesIconicTaxonName` (`data/local/CachedSearchEntity.kt`), kept separate from
  `filterIconicTaxonName`, which still tells a category row from a species row. `ForagerDatabase` goes 18 to 19 with
  `MIGRATION_18_19`, `ALL_MIGRATIONS` and `SCHEMA_VERSION` updated, and `app/schemas/.../19.json` added.
- **A deviation from the amendment, accepted by the planner on 2026-10-08 (same result, the 12 to 13 and 17 to 18 precedent, no change to twelve fixtures):** `MIGRATION_18_19` rebuilds the table rather than using
  `ALTER TABLE ... ADD COLUMN`. Twelve `LegacyForagerDatabaseVn` fixtures declare `CachedSearchEntity` directly (for example
  `MushroomLogMigrationTest.kt:122`), so their tables are generated from the current class, already carry the new column,
  and an `ADD COLUMN` would fail on them with a duplicate column when they migrate through `ALL_MIGRATIONS`. This is the
  pitfall `MIGRATION_12_13` records, and 17 to 18 was a rebuild for the same reason. The result is the same: one nullable
  column and no backfill. Rejected: `ALTER TABLE ... ADD COLUMN`, because it would fail on those fixtures; the other way to use it, changing all twelve fixtures to stop declaring the entity, was more change for the same schema.
- `19.json` was written by hand, because no build may run. Its identity hash, `32bd752c...`, comes from a script that
  re-implements Room 2.8.5's `SchemaIdentityKey` (read from the compiler jar with `javap`). Run on `16.json`, `17.json` and
  `18.json`, that script reproduces each file's own hash exactly. The build's KSP pass rewrites the file, so after the
  build `git diff` on it should come back empty.
- The group is written for species rows only (`RoomSearchCacheRepository.toEntity`), handed back on `CachedSearchSummary`,
  and read by the new `ForagingSelection.fromRecentSearch` in `onRecentSearchSelected`. The ViewModel stores the current
  selection's group with a search only when the selection is that species (`speciesGroupToStore`, read before the search
  is launched). `SearchCacheRepository.save` takes the group with no default, so each caller has to pass it explicitly.
- Old rows and Lichens stay null.

**Tests added:** `SchemaMigrationTest` step `18 to 19` (every seeded value is carried, the new column is null, then it is
set); `RoomSearchCacheRepositoryTest` (a species keeps its group in its own column with `filterIconicTaxonName` still null;
a category, or Lichens, stores none); `ForagingWeatherGuidanceTest` `fromRecentSearch`; and the screen test `a fungus
species reopened from a recent search shows the fungi pattern and no species note`.

**Changed assertions:**
- The chain test `4 to 18` becomes `4 to 19`, because 19 is now the current version.
- `JournalBackupTestSupport.SCHEMA` goes 18 to 19.
- `JournalBackupTest` has four changes:
  - `:79` manifest `schemaVersion` 18 to 19 (the version bump).
  - `:98` snapshot `version` 18 to 19 (the version bump).
  - `:792` live version during restore 18 to 19 (the version bump).
  - `:874` `SCHEMA_VERSION` 18 to 19 (the version bump).
- The newer-backup refusal test's version goes 19 to 20 (manifest and logged text). At 19 it would no longer be newer
  than the app, and would have stopped testing what it names. Accepted by the planner with this reason.
- `MushroomLogMigrationTest`'s fixture constructor gains `speciesIconicTaxonName = null`. This is not an assertion.
- `RoomSearchCacheRepositoryTest`'s 18 `save` calls gain `speciesIconicTaxonName = null`. These are not assertions.

**Unverified:** everything, since nothing has been compiled or run. That includes whether `19.json` matches what KSP
exports. Version 19 must be re-checked against `origin/main` at build time (it was at 18, `b71c1569`, when this was
pushed).
