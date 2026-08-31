# US fungi species index

A curated, iNaturalist-derived index of the fungi taxa foragers in the US
actually look up — built to replace searching an over-broad, unfiltered
species source (see the dispatch this was built from for the motivating bug:
searching "lions mane" with Fungi selected returned a jellyfish and not
*Hericium erinaceus*). Expanded over time rather than pruned; not wired into
the app yet.

## Files

- **`fungi-us-species-index.json`** — the index. One JSON object per taxon,
  10,700 records as of the last generation, sorted by observation count
  descending.
- **`observation-count-distribution.md`** — the full count distribution,
  generated before any threshold was applied. The owner picks the cutoff;
  this file is the evidence to pick it from.
- **`ascomycete-inclusion.md`** — exactly which Ascomycota genera are
  included beyond Basidiomycota, and why; also flags a boundary case
  (Exobasidiomycetes) the dispatch didn't name but arguably fits the same
  exclusion rule as rusts and smuts.
- **`_raw/`** — cached API responses (`merged_counts.json`,
  `names_by_id.json`) so the report/index can be regenerated without
  re-querying iNaturalist. Not meant to be read directly.

## Record shape

```json
{
  "taxon_id": 49158,
  "scientific_name": "Hericium erinaceus",
  "common_names": ["lion's-mane mushroom", "lion's mane mushroom", "노루궁뎅이", "..."],
  "observation_count": 10028
}
```

- `taxon_id` — iNaturalist's taxon ID. The stable join key back to iNat for
  everything this index deliberately does *not* carry (habitat, range,
  season) — those are meant to be fetched live by this ID, not duplicated
  here where they'd drift.
- `scientific_name` — iNat's current accepted scientific name for the taxon.
- `common_names` — every common name iNat carries for the taxon, in any
  locale (via `all_names=true`), not just the single `preferred_common_name`
  a normal API response would default to. Deduplicated; scientific-name
  entries stripped out (that's `scientific_name` already).
- `observation_count` — US, research-grade observation count, as a
  relevance signal for ranking later. Not a normalized score, not a
  percentile — the raw count, so `observation-count-distribution.md`'s
  thresholds map onto it directly.

No edibility, habitat, range, or season fields — deliberate, see the
dispatch. This is a search index, not an identification authority, and iNat
already supplies the location/time-scoped fields live by taxon ID.

## Taxonomic scope

Basidiomycota (all of it) minus rusts (Pucciniomycetes) and smuts
(Ustilaginomycetes), plus five named Ascomycete genera that produce visible
fruiting bodies: *Morchella*, *Tuber*, *Sarcoscypha*, *Xylaria*,
*Cordyceps*. Full reasoning and a flagged boundary case in
`ascomycete-inclusion.md`.

Scope: United States, research-grade observations only (iNat place ID `1`).

## Why JSON, not CSV or SQLite

`common_names` is a variable-length list per record (anywhere from 0 to over
30 names for a well-documented species) — CSV has no native way to hold
that without inventing an in-cell delimiter (fragile: several common names
already contain commas) or a second joined file. A single JSON array holds
the real structure directly, is trivially parseable by whatever eventually
imports it (Kotlin via kotlinx.serialization/Moshi, a Python script, a
spreadsheet tool that flattens nested JSON), and needs no separate schema
document since the shape is self-describing. SQLite was the other real
option — better suited once this is actually queried at volume from the
app, but that's explicitly not this task's job ("do not wire the index into
the app"); JSON is the right format for a data deliverable that isn't a
live query target yet.

## Regenerating

```
python3 scripts/build_fungi_species_index.py            # full re-pull from the live API
python3 scripts/build_fungi_species_index.py --report-only  # rebuild index.json + the
                                                              # distribution report from
                                                              # the _raw/ cache, no API calls
```

Respects iNaturalist's own published rate-limit guidance (~55 requests/min,
paced 1.1s apart; a full run is well under 100 requests total) and
identifies itself with a descriptive `User-Agent`, per
`https://api.inaturalist.org/v1/swagger.json`'s own `info.description`.
