# Ascomycete inclusion list and reasoning

The index's taxonomic scope (per the dispatch): all of Basidiomycota except
rusts and smuts, plus the Ascomycota that produce visible fruiting bodies.
"Fruits visibly" is a spectrum, not a boundary — this is the line actually
drawn, at genus rank, and why.

## Included

| Genus (iNat taxon ID) | Common examples |
| --- | --- |
| *Morchella* (56830) | Morels |
| *Tuber* (120278) | Truffles |
| *Sarcoscypha* (49136) | Scarlet cup fungi |
| *Xylaria* (55268) | Dead man's fingers, candlesnuff fungus |
| *Cordyceps* (58707) | Cordyceps (incl. *C. militaris*) |

These are exactly the five genera named in the dispatch. Included as whole
genera (`taxon_id=<genus>` covers all descendant species) rather than
hand-picking species within each — a genus named as "producing visible
fruiting bodies" applies to the genus's growth form, not to some species
within it and not others, and picking species-by-species inside an
already-named genus would be re-litigating a boundary the dispatch already
drew.

No other Ascomycota genera were added. Obvious candidates exist just outside
this list — *Otidea* (rabbit-ears), *Peziza* (cup fungi outside
Sarcoscypha), *Helvella* (elfin saddles), *Gyromitra* (false morels, also
dangerously toxic and frequently confused with true morels) — all fit
"produces a visible, foraged-relevant fruiting body" by the same reasoning
used to include the five above. They were left out because the dispatch
named five specific genera, not a criterion to apply broadly myself; adding
more is a scope call for the owner, not something to infer silently. Flagged
here as candidates for a future expansion, not added.

## Excluded

- **Rusts (Pucciniomycetes, class 69967)** and **smuts (Ustilaginomycetes,
  class 83712)** — named explicitly in the dispatch. Both are Basidiomycota;
  neither produces a foraged fruiting body (rusts and smuts are plant
  pathogens — visible as lesions, galls, or powdery spore masses on a host
  plant, not as a mushroom). Excluded via `without_taxon_id` on the
  Basidiomycota pull, which iNaturalist applies as "this taxon and its
  descendants" — confirmed against the live API (10557 leaf taxa returned
  with both classes excluded, vs. their being present in an unfiltered
  count).
- **Lichens (Lecanoromycetes and other lichenized Ascomycota)** — not
  explicitly named in the dispatch, but never entered the index in the first
  place: the Ascomycete pull only queried the five named genera above, not
  all of Ascomycota, so lichens were never candidates for inclusion. Worth
  noting explicitly because this codebase has already hit this exact
  boundary once before: `scripts/verify-lichen-exclusion.sh` exists to
  confirm the *live app's* existing Fungi search filter excludes
  Lecanoromycetes (class 54743) via `without_taxon_id`, for the same
  reason — lichens are technically fungi but not a foraged fruiting body,
  and three of the top five results for an unfiltered search were lichens.
  Same underlying rule, independently applied here by construction rather
  than by an explicit exclude.

## A boundary case the dispatch didn't name, flagged for review

Checked the classes that sit *inside* Basidiomycota alongside the excluded
rusts and smuts, to see if the same "produces no foraged fruiting body"
reasoning applies anywhere else the dispatch didn't call out. Two are worth
the owner's attention:

- **Exobasidiomycetes (class 130023)** — 54 species-level taxa in the
  current US research-grade data, top species (*Exobasidium symploci*) at
  1,087 observations. These are plant-gall pathogens (e.g. azalea/blueberry
  leaf galls) — visually distinctive and commonly photographed on
  iNaturalist, but not a fruiting body a forager picks, the same shape of
  exclusion reasoning as rusts and smuts. **Currently included** in the
  index (not named in the dispatch's exclude list, so left in rather than
  cut on my own judgment) but a strong candidate for the same treatment.
- **Microbotryomycetes (class 152042)** — 15 taxa, small counts (max 11
  observations), mostly anther-smut-type parasites. Same category, much
  smaller in scale. Also currently included.

Not flagged: Tremellomycetes (70 taxa, top species *Tremella mesenterica*
"witch's butter" at 9,862 observations) and Dacrymycetes (78 taxa, top
species *Dacrymyces chrysospermus* at 8,729 observations) — these are jelly
fungi that do produce a real, visible, foraged fruiting body, so their
inclusion is correct as-is, not a boundary question.
