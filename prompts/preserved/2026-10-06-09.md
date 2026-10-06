# Dispatch: T5, host trees as genus fraction on the master grid, and the British Columbia and Washington border seam

**Two parts. Part 1 is a source survey with no data download and no code. Part 2 builds, and waits for T4's review and the owner's choice of US source. No GBIF request, no climate pull, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Write the T5 dispatch", against forager-forecast `origin/main` at `ad64fef`. This dispatch is the first commit on branch `t5-host-trees`. Every cite below is a premise to re-check.

**Writer (D38):** a coder session on this laptop, on `t5-host-trees`, worktree `~/Zynergy/forager-forecast-t5`. **Review (D18)** under `docs/dispatch/2026-09-18-review-protocol.md`. **Merge (D40)** only on the owner's written word naming the branch.

**When it runs.** The protocol (`:15-16`): "A task that depends on another does not start until that task's review is filed and the owner has seen any drift finding." T5 depends on T3 (done) and T4 (built on `t4-master-grid`, not yet reviewed). **Part 2 does not start until T4's review is filed, T4 has merged, and the owner has seen its findings.** Part 2 cuts its code from main as it is then. Part 1 runs only when the planner says so: whether a source survey with no code counts as starting T5 is the owner's call.

## Why

TASKS.md, the T5 block (about `:55-59`):

> **T5. Host trees and the border seam** · Depends on: T3, T4 · Does: collapse BIGMAP and SCANFI to genus fraction with a source flag along a strip across the British Columbia and Washington border. · Verify: transects across the border show no step larger than the variation inside each country, or the step is written up as a known artifact. · Device-only: no

SPEC.md (about `:46-47`): "Host trees collapse to genus fraction with a source flag, because the US and Canadian tree layers do not match." D8: one equal-area grid, with "Separate US and Canada grids (creates a second seam)" rejected.

**The US source is blocked.** DATA_REGISTER marks BIGMAP "verified, licence not stated at source: blocked from use" (D31; SPEC.md about `:90`). It also covers the coterminous US only, so Alaska is masked. Asked how T5 should handle the US side, the owner chose **"Find a cleared alternative (Recommended)"**, worded as: T5's first step looks for US tree-species layers whose licence is stated at the source, checks each against BIGMAP, and reports back; nothing is downloaded until the owner picks one; the owner can still write to the Forest Service in parallel (Forager RECORD -583).

**The Canadian source is cleared.** SCANFI v2: 30 m, five-year steps 1985 to 2025, crown closure percent for balsam fir, black spruce, Douglas-fir, jack pine, lodgepole pine, ponderosa pine, tamarack, white and red pine, **broadleaf (one class)** and other coniferous, under the Open Government Licence - Canada (DATA_REGISTER; RESEARCH_LOG `:40`). Its authors rate the species layers for regional scale.

## Part 1: the source survey (no download, no code)

1. **US candidates.** For each, from the producer's own pages, cited: licence as stated at the source (quote it; "not stated" is a result, not a gap to fill); coverage (CONUS, Alaska); resolution and year; the variable (biomass, basal area, crown cover, presence, plot list); species or genus list; access route and size for the T4 rectangle (45.5-49.0°N, 121-125°W, D82). At least:
   - the USDA Forest Service's TreeMap (2016 and any later release);
   - FIA plot data (FIADB) and whether a gridded product is allowed from it;
   - LEMMA GNN (Oregon State University) for the Pacific Northwest;
   - any other the search finds, including any that cover Alaska.
2. **Against BIGMAP:** what each loses or gains (species detail, resolution, year, the variable).
3. **Against SCANFI, at the seam:** the genera the habitat model needs on both sides. Chanterelles (*Cantharellus*) are mycorrhizal with conifers such as Douglas-fir and hemlock and with oaks; chicken of the woods (*Laetiporus*) grows on oak, conifers and other hosts. Check these against EVIDENCE.md and the RESEARCH_LOG rather than taking them from this line. For each genus, say whether both sides can supply it. SCANFI has no hemlock and no oak, only one broadleaf class. The one variable both sides can be converted to (genus fraction of what: crown cover, basal area, biomass?) is proposed with its conversion.
4. **Recommendation** with reasons, and anything the owner must decide (a licence reading, a genus that one side cannot supply, a variable that cannot be harmonised).

Report Part 1 as `docs/audits/2026-10-06-t5-source-survey.md` with its index row, push, and message the planner. Stop there.

## Part 2: build (after T4 merges and the owner picks the US source)

- **Genus fraction with a source flag** on T4's master grid (the lattice and cell id from `grid.py`, the exact regrid from `regrid.py`; re-check both on main), for a strip across the British Columbia and Washington border inside or beside the D82 rectangle. Propose the strip's width before building.
- **The Verify:** transects across the border. State the number and the placement of transects before looking at the values, and their spacing. A step at the border is compared with the variation inside each country, measured the same way on each side. A step larger than that is written up as a known artifact, with its size, not tuned away.
- **Tests first** through the real entry points, **revert checks** with saved-copy restore, no stale bytecode (clear `__pycache__`, `PYTHONDONTWRITEBYTECODE=1`, refuse a run whose interpreter does not see the edit), and the full suite before and after. Data under a gitignored `data/t5/`, with its request stored and copied to `docs/pulls/`, and attribution recorded verbatim.
- **Decision rows** this branch files start at **D83**; ask the planner before taking more than D83 to D89.

## Do not touch

- No layer whose licence is not stated at its source (D31). BIGMAP stays blocked unless the owner says otherwise in writing.
- No GBIF request, no climate pull, no model fit. No secret (D36). No edit to a filed record (D41). No merge (D40). Nothing in the Forager app repository.
- T4's branch and worktree, and D26's, are someone else's.

## Report back

Part 1 as above. Part 2: a completion report in `docs/audits/` with its index row, TASKS.md and START_HERE updated, then a message to the Forager planner session, which arranges the review.
