# Ascomycete inclusion boundary and the four excluded classes

The index's taxonomic scope: all of Basidiomycota except four classes that
produce no foraged fruiting body, plus the Ascomycota that produce visible
fruiting bodies — now drawn structurally (an order, plus named genera
outside it) rather than as a hand-picked genus list.

This is the second revision of this boundary. The first revision (below,
"Revision history") named five genera by hand and flagged two Basidiomycota
classes for owner review instead of excluding them unilaterally. The owner
has since decided all three open questions; this file records what changed
and why, and which parts are owner judgment rather than a fact derivable
from the data.

## Basidiomycota: four excluded classes

All owner decisions, applying the same reasoning each time: a taxonomic
class that is entirely (or overwhelmingly) plant pathogens or parasites,
visible as lesions/galls/spore masses on a host rather than as a mushroom a
forager would pick.

| Class (iNat taxon ID) | US taxa excluded | Reasoning |
| --- | ---: | --- |
| Pucciniomycetes — rusts (69967) | (folded into the Basidiomycota pull's exclusion, not separately countable from this revision) | Plant pathogens, no foraged fruiting body. Named explicitly in the original dispatch. |
| Ustilaginomycetes — smuts (83712) | (same) | Same reasoning. Named explicitly in the original dispatch. |
| Exobasidiomycetes (130023) | 54 | Plant-gall pathogens (e.g. azalea/blueberry leaf galls). Flagged by the prior revision as a boundary case fitting the same reasoning as rusts/smuts but not named in that dispatch; **this revision's owner decision cuts it.** |
| Microbotryomycetes (152042) | 15 | Anther-smut-type parasites, small counts (max 11 observations in the prior pull). Same reasoning; **also an owner decision in this revision.** |

Exobasidiomycetes + Microbotryomycetes = 69 taxa cut in this revision,
identified against the live API and stripped from the existing cached
Basidiomycota pull (`data/species-index/_raw/merged_counts.json`) rather
than re-querying all of Basidiomycota — the exclusion query
(`taxon_id=130023,152042`) returned exactly 69 matches against the cache,
which is the number expected if every one of those taxa had, as the prior
revision's report said, been present and merely un-flagged.

**Not flagged for a third revision:** Tremellomycetes and Dacrymycetes
(jelly fungi with a real, visible, foraged fruiting body — *Tremella
mesenterica* "witch's butter", *Dacrymyces chrysospermus*) remain included,
unchanged from the prior revision's reasoning.

## Ascomycota: structural boundary, not a hand-picked list

**Old boundary (first revision):** five named genera — *Morchella*, *Tuber*,
*Sarcoscypha*, *Xylaria*, *Cordyceps*. Explicitly flagged at the time as a
maintenance liability: every future gap needed a code change, and gaps were
invisible until someone happened to search for one. *Gyromitra* — the false
morel, the most important lookalike for the genus this index already
covered best — was exactly such a gap.

**New boundary (this revision, owner decision):**

- **All of Pezizales** (order, iNat taxon ID **48717**) as an ancestor
  filter — one boundary instead of five names.
- **Plus, named individually** (confirmed outside Pezizales — see
  "Taxonomy verified" below): *Xylaria* (55268), *Cordyceps* (58707), and
  ***Hypomyces*** (48246, new).

*Hypomyces* was a genuine gap nobody had flagged: *Hypomyces lactifluorum*,
the lobster mushroom, is heavily foraged and was entirely absent from the
first-revision index. It is now present at 10,795 US research-grade
observations (taxon 48215) — a bigger number than *Hericium erinaceus*
(10,028), which says something about how much a five-genus hand list can
miss even for a well-known edible.

### Taxonomy verified against the live API, not assumed

The dispatch that ordered this widening called out Pezizales membership as
"planner assertion, not confirmed fact." Verified directly via
`api.inaturalist.org/v1/taxa` (checking `ancestor_ids` against Pezizales'
own ID, 48717, fetched the same way):

| Genus | iNat ID | Pezizales ancestor? |
| --- | ---: | :---: |
| Morchella | 56830 | yes |
| Tuber | 120278 | yes |
| Sarcoscypha | 49136 | yes |
| Gyromitra | 85118 | yes |
| Helvella | 49206 | yes |
| Verpa | 118001 | yes |
| Peziza | 57372 | yes |
| Disciotis | 126912 | yes |
| Xylaria | 55268 | **no** |
| Cordyceps | 58707 | **no** |
| Hypomyces | 48246 | **no** |

All eight Pezizales genera the dispatch expected to be subsumed are
confirmed subsumed; all three genera it expected to sit outside Pezizales
are confirmed outside it. (One taxonomic wrinkle found in passing: iNat
carries a second, unrelated *Verpa* — taxon 1072230, a mollusc genus,
homonym only. The fungal *Verpa*, 118001, is the one inside Pezizales.)

### Scope growth from the order boundary

Pezizales alone (independent of Xylaria/Cordyceps/Hypomyces) contributes
**611** US research-grade taxa. Broken down by genus among the 769 total
`ascomycota-included` taxa post-widening:

| Genus | Taxa | | Genus | Taxa |
| --- | ---: | --- | --- | ---: |
| Hypomyces | 81 | | Xylaria | 55 |
| Peziza | 64 | | Helvella | 49 |
| Morchella | 35 | | Tuber | 29 |
| Cordyceps | 14 | | Sarcoscypha | 9 |
| Verpa | 5 | | Gyromitra | 4 |
| Disciotis | 2 | | | |

The remaining **422 taxa (55% of the ascomycete-included set) come from 126
other Pezizales genera not individually named anywhere** — mostly small cup
fungi no forager searches for (*Scutellinia* 69, *Tarzetta* 20, *Ascobolus*
17, *Saccobolus* 11, *Otidea* 10, *Discina* 10, *Pseudoplectania* 10,
*Plectania* 9, *Balsamia* 9, and ~117 more genera at ≤9 taxa each). This is
exactly the Pyronemataceae-style scope growth the dispatch asked to watch
for.

**Flagged, not resolved here:** 611 taxa from one order is not the "drags
in thousands" scenario the dispatch treated as disqualifying, and every
named genus the dispatch cared about (*Gyromitra*, *Helvella*, *Verpa*, the
morel/truffle/cup-fungi core) is exactly what the order boundary was meant
to pick up — so the order boundary did what it was supposed to do. But
whether ~126 small non-foraged genera worth ~55% of the widened set is an
acceptable cost of "one boundary instead of five names" is a scope
judgment, not a fact this report can settle, and it's the owner's per the
dispatch's own framing ("naming genera individually is worse structurally
but may be right if the order drags in thousands"). Recorded here as
evidence for that call, not as a decision.

### No lichenized taxa entered the index

Checked directly: none of the 769 `ascomycota-included` taxa (scientific
name or any common name) contain the string "lichen". This is also
structurally guaranteed, not just an absence-of-evidence check: Pezizales
sits under class Pezizomycetes, and lichenization in Ascomycota is
concentrated in unrelated classes (principally Lecanoromycetes, taxon
54743 — the same class `scripts/verify-lichen-exclusion.sh` confirms the
live app's Fungi filter excludes). Since the pull uses Pezizales as an
ancestor filter (`taxon_id=48717`, "this taxon and its descendants"), a
Lecanoromycetes taxon cannot appear in the results regardless of common
name — its ancestry doesn't include Pezizales. Xylaria, Cordyceps, and
Hypomyces are non-lichenized genera by the same class-level separation.

## Revision history

**First revision** (prior dispatch): five named Ascomycete genera
(*Morchella*, *Tuber*, *Sarcoscypha*, *Xylaria*, *Cordyceps*), Basidiomycota
minus rusts and smuts. Flagged Exobasidiomycetes and Microbotryomycetes for
owner review rather than deciding unilaterally; flagged the five-genus list
itself as a maintenance liability with *Gyromitra*/*Helvella*/*Peziza*/*Otidea*
named as obvious candidates left out only because the dispatch named five
specific genera, not a criterion to apply broadly.

**This revision**: applies the owner's decisions on all three — cut
Exobasidiomycetes and Microbotryomycetes, no observation-count threshold
(see `observation-count-distribution.md`'s header note), and replace the
five-genus list with the Pezizales-plus-three-genera structural boundary
above.
