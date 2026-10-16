Scoping for the owner's review; nothing dispatched.

# Membership list alternatives if FungalTraits permission is refused or never comes

Written 2026-10-16T02:07Z (clock), read-only scoping for the planner, answering the owner's words
in RECORD -1071: "scope out plans for an alternative just in case". Nothing built, nothing
committed, no dataset downloaded. Pages were read through a web reader or with `curl` for page
text and metadata. Some pages refused the reader, and those are marked.

Labels: **READ (URL)** means I read it at that address on 2026-10-15/16 (US Pacific).
**INFERRED** means I reasoned it from what I read. **UNVERIFIED** means it comes from memory or
could not be opened.

Context read first: RECORD -1030 (M1 to M6, M8), -1036 (licence check), -1061/-1062 (T7
proposals), -1070 (email sent), -1071 (build ahead of permission, scope an alternative). Also the
T7 dispatch `docs/dispatch/2026-10-15-t7-mycorrhizal-habitat.md` (M1 at :424-435, M2 at :436-441,
V9 at :220-229, V17 at :263-267, build tests at :319-324) and `docs/planning/SPEC.md` constraints
(:84-92), both on forecast branch `t7-build` at `169cd63`.

---

## 0. Two premises checked before planning

- **The membership loader does not exist yet.** At `t7-build` `169cd63` (the same as
  `origin/t7-build` after a fetch), `src/forager_forecast/` has no membership module. The only
  hits for "membership" are in docs and in `t6b_hosts.py`, which is the tree side. The dispatch
  lists the group membership list as "does not exist" (`:142`). **READ (local tree).** So the
  phrase "the same columns as FungalTraits" describes a contract that has not been written
  yet. Section 3.5 proposes one, and that is the cheapest point at which to make the swap cost
  one decision row.
- **Under M4 (a), the comparison group is one genus.** RECORD -1030 rules M4 (a), chicken of the
  woods, so the control is *Laetiporus*. **READ (RECORD -1030).** That means T7 needs no general
  wood-rot genus list as things stand. INFERRED. A wood-rot list matters only if the control is
  later widened, so this scoping covers the wood-rot side only lightly.

---

## 1. Openly licensed sources that state fungal genus lifestyle

Ranked by usefulness for the mycorrhizal side. The "licence" column gives what the source
states, at the place named.

| # | Source | What it holds | Licence as stated | Usable for plan B? |
|---|---|---|---|---|
| S1 | Tedersoo & Smith 2013, "Lineages of ectomycorrhizal fungi revisited", *Fungal Biology Reviews* 27:83-99, doi 10.1016/j.fbr.2013.09.001 | The standard review of ectomycorrhizal lineages. UNVERIFIED: I believe its main table lists lineages and their genera, but the article page refused the reader (HTTP 403) and the PDF link returned an HTML page | **CC BY-NC-ND 3.0** for the version of record. READ: Crossref metadata, `https://api.crossref.org/works/10.1016/j.fbr.2013.09.001`, license `content-version: vor` -> `creativecommons.org/licenses/by-nc-nd/3.0/`. OpenAlex agrees (`hybrid`, `cc-by-nc-nd`) | **Best backbone.** Non-commercial matches the project. "No derivatives" means we cite it per genus and do not reproduce or adapt its table (see section 2). Supplement licence: not determined |
| S2 | Varga et al. 2019, "Megaphylogeny resolves global patterns of mushroom evolution", *Nature Ecology & Evolution*, doi 10.1038/s41559-019-0834-1 | 5,284 mushroom-forming taxa. Supplementary Data 1 is described as "character states used for analyses of character evolution", and Supplementary Data 3 covers the "ASR of fungal substrate preference" (READ, captions on the article page) | **CC BY 4.0**. READ: `https://www.nature.com/articles/s41559-019-0834-1`, "Open Access This article is licensed under a Creative Commons Attribution 4.0 International License …". Crossref vor = CC BY 4.0. The PMC copy (PMC6443077) is an author manuscript under Nature's older academic-use terms, so cite the publisher's copy | **Second source, and the likely source for the wood side.** Whether the character states include a mycorrhizal or nutritional mode per taxon is UNVERIFIED, because I did not open the spreadsheet. The licence statement says nothing explicit about the supplementary files (INFERRED) |
| S3 | Tedersoo et al. 2018, "High-level classification of the Fungi and a tool for evolutionary ecological analyses", *Fungal Diversity*, doi 10.1007/s13225-018-0401-0 | A genus-to-higher-rank classification. Whether its supplement carries lifestyle is UNVERIFIED | **CC BY 4.0** per Crossref (`tdm` license only; no `vor` entry deposited) and OpenAlex (`hybrid`, `cc-by`). READ: Crossref and OpenAlex APIs. The Springer page did not load (consent wall) | Useful for genus-to-family mapping (M2's family-only records) if it has no lifestyle field |
| S4 | FUNGuild (Nguyen et al. 2016, *Fungal Ecology* 20:241-248) | A guild database at genus and species level: "9476 entries, with 66% at the genus level" (v1.0). Now served from an online API; the README says there are "over 13,000 fungal taxa" | **Mixed signals.** The paper says the database is "accessible for use and annotation by any interested party under GNU General Public License". READ: PDF copy at `https://cbs.umn.edu/sites/cbs.umn.edu/files/migrated-files/downloads/Nguyenetal2016.pdf`, section 2.1. The GPL version is not stated. The GitHub repository has `"license": null` (READ: `https://api.github.com/repos/UMNFuN/FUNGuild`). The article itself is "© 2015 Elsevier … All rights reserved" | **Possible, but needs the owner.** RECORD -1036 found "no licence" from the repository; the paper's GPL statement was not in that check. Whether GPL suits a data table that feeds a CC BY-NC output is a question for the owner (section 6) |
| S5 | funfun ("fungaltraits" R package, Zanne et al. 2020 / Cornwell), `github.com/traitecoevo/fungaltraits` | A trait database. Its genus guild column is FUNGuild's: "collates the most common fungal guild at the genus level" (README) | The package DESCRIPTION reads "License: MIT + file LICENSE", and the LICENSE file names a 2017 copyright holder. READ: raw DESCRIPTION, LICENSE and README on GitHub. GitHub reports `NOASSERTION`. Its review paper (doi 10.1111/brv.12570) is CC BY-SA in its submitted version (OpenAlex) | **Not independent of S4.** INFERRED: MIT on the package does not relicense FUNGuild's guild assignments. Treat as S4 |
| S6 | Wikidata property P788, "mushroom ecological type" | Values mycorrhiza, saprobiont, parasitism, nematophagous. Mostly species items: 375 mycorrhiza, 650 saprobiont, and only **12 genus-rank items** (my query to `query.wikidata.org`, 2026-10-16) | **CC0**. READ: `https://www.wikidata.org/wiki/Wikidata:Licensing` | Clean licence but too thin and unreviewed. Cross-check only |
| S7 | US Forest Service reports, e.g. Castellano 1995, "Report on Fungi", Interior Columbia Basin Ecosystem Management Project | A species-by-species account of rare Columbia Basin fungi. Many entries say "belongs to the ectomycorrhizal genus X" (Alpova, Amanita, Chamonixia, Gautieria, Hebeloma …) | No rights statement read. The title page names the author as "USDA Forest Service", a Research Forester at the PNW Research Station. READ: `https://www.fs.usda.gov/r6/icbemp/science/castella.pdf`. INFERRED: a work of a US federal employee is public domain in the US (17 U.S.C. 105) | Regional (PNW) support for genus statements. The scan's OCR is poor, and it covers rare taxa, not a full list. PNW-GTR-772 (Trappe et al. 2009, truffle fungi of the PNW) is a likely further public-domain source; its content is UNVERIFIED |
| S8 | Sánchez-García et al. 2020, *PNAS*, doi 10.1073/pnas.1922539117, "Fruiting body form, not nutritional mode, is the major driver of diversification in mushroom-forming fungi" | Codes nutritional mode across mushroom-forming fungi (INFERRED from the title; supplement UNVERIFIED) | **CC BY-NC-ND 4.0**. READ: Crossref vor licence | Same footing as S1 |

**Checked and found not to be sources:**
- **GBIF:** four dataset searches ("ectomycorrhizal", "fungal traits", "fungal guild",
  "mycorrhizal status") found occurrence datasets and species descriptions, but no genus lifestyle
  checklist. The only trait-like hit is FungalRoot's occurrence dataset, which covers the plant
  side and is CC BY-NC per -1036. READ: `https://api.gbif.org/v1/dataset/search?q=…`.
- **UNITE:** releases carry taxonomy only. The repository page points to FungalTraits, funfun and
  FUNGuild as "third-party trait datasets". READ: `https://unite.ut.ee/repository.php`.

**On FungalTraits itself (new since -1036, so the planner should know):**
- Crossref's licence for the version of record is Springer Nature's text-and-data-mining terms,
  not a CC licence. READ: `https://api.crossref.org/works/10.1007/s13225-020-00466-2`.
- The University of Copenhagen repository lists one file, "Accepted author manuscript … Licence:
  CC BY", and no supplementary files. READ:
  `https://researchprofiles.ku.dk/en/publications/fungaltraits-a-user-friendly-traits-database-of-fungi-and-fungus-/`.
  The PDF itself sat behind a bot challenge and was not read. INFERRED: that label covers the
  manuscript text, not Table S1 (the genus table).
- The University of Pretoria repository hosts `Polme_FungalTraitsTabS1_2020.xlsx` with no
  item-level licence statement. READ: `https://repository.up.ac.za/handle/2263/80202`.
- GloBI's indexing repository republishes S1 and S2 with no licence. READ:
  `https://github.com/globalbioticinteractions/fungaltraits`.
- None of these clears a public frozen copy of Table S1. Third parties re-hosting it does not
  license it. INFERRED. -1036's conclusion stands. The Copenhagen CC BY label may be worth
  mentioning to the authors if they reply.

---

## 2. Is a list we compile ourselves, cited, a reasonable route? (a question for the owner, not legal advice)

The distinction the plan rests on:
- **A fact:** "*Suillus* is ectomycorrhizal", stated by several published sources.
- **A curated table's selection and arrangement:** which 10,000 genera, how each is coded, the
  column scheme, the mixed-lifestyle notes.

The US position on facts: "Facts, whether alone or as part of a compilation, are not original and
therefore may not be copyrighted", and "A factual compilation is eligible for copyright if it
features an original selection or arrangement of facts" (*Feist v. Rural*, 499 U.S. 340). READ:
`https://www.law.cornell.edu/supremecourt/text/499/340`.

The EU side is UNVERIFIED (the EUR-Lex page returned nothing to the reader). From memory, the EU
database directive (96/9/EC) adds a separate "sui generis" right against extracting a substantial
part of a database that took substantial investment, even when the contents are facts.
FungalTraits was produced largely in Estonia. INFERRED: that is why plan B must not be built by
filtering FungalTraits, and why section 3 keeps FungalTraits out of the compiler's hands.

How plan B limits each risk (INFERRED):
- Our own selection: the genera actually recorded in our D26 box.
- Our own scheme: three lifestyle values and a status.
- Each genus cited to several sources rather than one.
- No source's table copied or adapted. This matters for S1 and S8 ("no derivatives"): citing a
  genus to a page is not reproducing the table.

**Question for the owner (Q1):** is "genus facts compiled by us from several published sources,
each one cited, in our own format, published under the forecast data's CC BY-NC 4.0" acceptable,
or do you want outside advice first? Nothing here is a legal opinion.

---

## 3. Plan B: our own cited list from openly licensed sources

### 3.1 Sources, in order of weight
1. **S1** (ectomycorrhizal lineage review): the backbone for "ectomycorrhizal".
2. **S2** (megaphylogeny, CC BY): a second, independent statement, and the wood/substrate side.
3. **S4** (FUNGuild): only if the owner accepts its stated GPL (Q2). Otherwise it is left out.
4. **S7** (Forest Service, public domain): PNW support for regional genera.
5. **S6** (Wikidata, CC0): a cross-check only, never a deciding source.
6. **S3**: genus-to-family mapping for M2's family-only records.

### 3.2 Which genera get an entry
Only genera that occur in the D26 records inside the PNW box after R6. V16 gives the genus name
next to `genusKey` offline, so no GBIF request is needed. INFERRED: this caps the work at the few
hundred genera actually recorded there; the real number is not counted (see section 7) and would
be read without any location.

Counting genera is not counting presences, but whether that list of names touches -1030's first
point of no return is for the planner to rule. A list of genus names with no record counts
should not fix M3's minima (INFERRED).

### 3.3 Columns (proposed; the same contract the FungalTraits adapter fills)
| column | values |
|---|---|
| `genus` | the name as written in D26 |
| `gbif_genus_key` | the key from D26 |
| `lifestyle` | `ectomycorrhizal`, `wood_rot`, `other` |
| `status` | `clear`, `mixed`, `uncertain` |
| `basis` | short text, e.g. "S1 lineage /suillus-rhizopogon; S2 SD1" |
| `sources` | semicolon-separated source IDs with page or table |
| `list_id` | e.g. `own-2026-10-xx`, or `fungaltraits-1.2-<sha8>` |

A separate `sources.csv` holds the full citation, licence as stated, URL, date read, and the
sha256 of anything fetched.

### 3.4 Rules (proposed; the owner rules)
- **`clear` ectomycorrhizal:** S1 places the genus in an ectomycorrhizal lineage, a second source
  agrees, and no source read says otherwise.
- **`mixed`:** any source says the genus holds more than one lifestyle, or two sources disagree.
  The frequently cited example is *Entoloma*, which FUNGuild's own paper uses as a genus mixing
  saprotrophic, ectomycorrhizal and mycoparasitic species (via a search-result summary of that
  paper, not re-read in the PDF).
- **`uncertain`:** only one source, or the sources only say "probably".
- **Under M2 (a), unchanged:** `clear` genera form the main map. One side run adds `mixed` and
  `uncertain`, reported beside it. Family-only records stay out of the main map and are counted
  by family (V16).
- **Deadly genera stay in.** The map shows habitat only (M1's note, dispatch `:435`).

### 3.5 How it plugs into the same loader
- The T7 build is starting now on FungalTraits (-1071).
- **The cheap move is to have the loader read only the normalised table in 3.3**, with a small
  adapter that turns raw FungalTraits into those columns. INFERRED: FungalTraits'
  `primary_lifestyle` / secondary-lifestyle fields map onto `lifestyle` and `status`. The exact
  FungalTraits column names are UNVERIFIED here, because I did not open the table.
- Plan B then produces the same file directly. Switching is one decision row naming the new
  `list_id` and sha256, then a re-run.
- The dispatch's test "the list's sha256 is checked before use" (`:319-321`) covers either list
  as long as the check runs on the normalised file and records the raw source's hash beside it.
- This is a change to what the T7 coder builds, so it needs the planner's word to the coder.

### 3.6 Dated, fingerprinted, versioned
- File name: `data/membership/membership_<list_id>.csv` in the forecast repository, with
  `sources.csv` beside it.
- Its sha256 goes into DATA_REGISTER (`SPEC.md:84`) and into every run manifest (P9).
- Any change is a new dated file plus a decision row. The old file stays.
- Plan B's file is ours and cited, so it can be committed publicly under CC BY-NC 4.0 if Q1 is a
  yes.

### 3.7 Kept independent of FungalTraits
- The session compiling plan B must not read the local FungalTraits copy on /mnt/work.
- After both exist, a separate step may count how many genera agree and disagree. Each
  disagreement is settled from the cited sources, never by copying FungalTraits' answer.
  Otherwise plan B becomes an extraction of FungalTraits (section 2). INFERRED.

### 3.8 Review (D18)
- A second agent, in a separate session (D18, `DECISIONS.md:114`), re-derives from the sources:
  - every `mixed` and `uncertain` row;
  - every genus with more than a fixed share of the box's records;
  - a seeded random sample of 10% of the `clear` rows.
- It reports each disagreement with page references.
- The list is not used for any count until that review is filed (D18's "a dependent task does
  not start until its prerequisite's review is filed").

### 3.9 Effort and risk, in plain words
- **Effort:** about one agent session to compile and one to review, plus the owner's rulings on
  Q1 to Q4. The swap itself is a decision row and a re-run, provided the loader is built against
  the normalised file (3.5).
- **Risks:**
  - S1's table could not be opened here, so the backbone may turn out to need a library copy;
    the paper is open access per its licence metadata, but the publisher blocked the reader.
  - S2 may not code mycorrhizal status per taxon.
  - Rare genera may get only one source and land in `uncertain`, which shrinks the main map's
    group.
  - The map from plan B will differ somewhat from the FungalTraits map, so results are not
    directly comparable across the swap. That is reported, not hidden.

---

## 4. Plan C: a hand list of well-known PNW ectomycorrhizal genera (M1 option b)

- **What it is:** a short list, each entry with one citation (S1 or S7), of genera foragers know.
  UNVERIFIED example names, from memory: *Cantharellus*, *Craterellus*, *Boletus* and allied
  boletes, *Suillus*, *Leccinum*, *Lactarius*, *Russula*, *Hydnum*, *Amanita*, *Cortinarius*,
  *Tricholoma*, *Hygrophorus*, *Laccaria*, *Rhizopogon*, *Hebeloma*, *Inocybe*, *Albatrellus*,
  *Hydnellum*/*Sarcodon*, *Gomphus*/*Turbinellus*, *Tuber*.
- **Same plumbing:** the same columns, all `clear`. M2's side run then has nothing to add except
  family-only records.
- **Trade-offs:**
  - Gained: hours of work, easy to review, and easy to explain to foragers.
  - Lost: it is our judgement, not a published list, which matters to foresters and ecologists
    (the reason M1 (a) was recommended). It leaves out many ectomycorrhizal genera the map would
    claim to cover.
  - It leans toward popular, often-photographed genera. The map's meaning shifts to "where
    well-known mycorrhizal mushrooms are reported" (INFERRED).
- **Effort:** an hour or two, plus a short D18 review.
- **Risk:** low to build. The main risks are credibility and a narrower claim.

---

## 5. Summary of effort and risk

| Plan | Effort | Main risk |
|---|---|---|
| A, FungalTraits with permission | Already being built (-1071) | Permission refused or no answer |
| B, own cited list | About 2 agent sessions plus owner rulings; the swap is 1 decision row plus a re-run, if the loader reads the normalised file | Sources hard to open; thinner coverage; legal footing is the owner's call (Q1) |
| C, hand list | Hours | Our judgement; narrow; less credible to specialists |

---

## 6. Questions for the owner

- **Q1.** Is a list of genus facts compiled by us from several cited sources, in our own format,
  published under CC BY-NC 4.0, an acceptable route? Or do you want outside advice first? (Not
  legal advice.)
- **Q2.** FUNGuild's paper says the database is under the GNU General Public License; its
  repository states no licence. Use it as one of plan B's sources, or leave it out?
- **Q3.** Use "non-commercial, no-derivatives" papers (S1, S8) as cited references, without
  reproducing their tables?
- **Q4.** When does plan B start: now, in parallel and kept blind of FungalTraits, or only after
  a "no" or about a week of silence (-1070's note)?
- **Q5 (for the planner, then the T7 coder).** Should the T7 loader read a normalised membership
  file through a FungalTraits adapter (3.5), so a swap costs one decision row?
- **Q6.** Plan B's "clear" rule: S1 plus one agreeing source, as proposed, or something stricter
  or looser?

---

## 7. Could not determine

- **S1:** whether it lists genera per lineage, and its supplement's licence. The publisher page
  returned 403 and the PDF link returned HTML.
- **S2:** whether Supplementary Data 1 or 3 codes mycorrhizal or nutritional mode per taxon. The
  spreadsheets were not opened. Also whether the CC BY statement covers the supplementary files.
- **S3:** whether its supplement carries lifestyle; the Springer page did not load.
- **S8:** what its supplement codes.
- **FUNGuild:** which GPL version, and whether the GPL statement still applies to the current
  online database.
- **FungalTraits:** the content of the Copenhagen "CC BY" manuscript (bot challenge), and the
  exact column names in Table S1.
- **EU database directive:** the text of its database right (EUR-Lex returned nothing); stated
  from memory.
- **Genus count:** how many genera the D26 PNW box holds. Not counted; this was a read-only scope
  with no data access requested.
- **PNW-GTR-772 and other Forest Service handbooks:** content not read.
