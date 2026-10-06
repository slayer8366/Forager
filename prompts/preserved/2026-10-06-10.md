# Dispatch: D27 to D29, the data-quality tables over D26's download, and the four items the D26 review left

**Verify first and report by message; then build unless something is a stop. Reads D26's download in place. No GBIF download, no climate pull, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Yes, write the D27 to D29 dispatch", against forager-forecast `origin/main` at `74c7f3b`. This dispatch is the first commit on branch `dq-tables-d27-d29`. Every cite is a premise to re-check.

**Writer (D38):** a coder session on this laptop, on `dq-tables-d27-d29`, worktree `~/Zynergy/forager-forecast-dq`. **Review (D18).** **Merge (D40)** only on the owner's written word naming the branch.

**When it runs.** The planner offered this as running after T5 merges, and the owner agreed. It doesn't depend on T5's code, but both append to the same record files. Start when the planner says T5 has merged. **Decision rows from D95** (T5 uses D83 onward; ask before passing D99).

## Why

The 2026-09-20 planner handoff (`docs/planning/handoffs/2026-09-20-planner-handoff.md:27`), after D26's download: "Then D27 in full (taxon in the key is folded; the rest is not), D28's day-of-month table, D29's dataset list per DOI, in that order, since each waits on D26's data." D26 is merged (`614b262`). Its download is `data/d26/downloads/` in `~/Zynergy/forager-forecast-d26` (0012112-260928105237408, DOI 10.15468/dl.8jxmeb, `docs/pulls/gbif-fungi-us-canada-2015-2025.doi.json`). Re-check its sha256 and read it in place. Never extract it.

The rows (DECISIONS.md), each a premise:
- **D27:** taxon in every duplicate key (the accepted GBIF taxon key); two named keys, the observer-duplicate key (taxon, observer, cell, day) for audit counts and the event key (taxon, cell, day) for modelling; "Reports give both counts."
- **D28:** the stricter first-of-month rule stands provisionally; "The counts report adds a day-of-month table for date-only records, by dataset"; a dataset whose first-of-month share is close to one in thirty keeps those records, one with a clear excess loses them; **"The owner rules on the final rule before any model is fit."**
- **D29:** beside each DOI, the constituent dataset list with each dataset's licence and record count, plus the by-licence counts table, and the register's GBIF row pointing at it. Read with **D48** (the licence rule applies per record, by its own licence field) and **D61** (Forager is not sold; the full model on every licence ships; D29's two tracks are still reported).
- **D65 and D66** (the D32 follow-up) already put taxon in both keys and keep the lowest gbifID. Say what D27 "in full" still needs beyond that.

## Verify first, report by message

1. Base: `origin/main` is `74c7f3b` (or say what moved) and the download's sha256 matches its record.
2. **D27:** what the code produces today for both keys over D26's download, where (file:line), and what is missing for "Reports give both counts".
3. **D28:** whether the loader keeps `datasetKey` and the parsed date's precision. Propose the table's columns and the measure of "close to one in thirty", **stated before any count is read**: for example, a binomial test against 1/30 per dataset at a fixed alpha, with a minimum dataset size. The owner rules on the rule itself; your job is a table that lets them.
4. **D29:** where each `datasetKey`'s title and licence come from (GBIF's public dataset API, no credentials), how many distinct datasets are in the download, and the request count. Throttle politely; one request at a time is fine. Propose the filed list's form and path beside the DOI record. Say whether lists for the two older DOIs (0005709, 0005714; their zips are in `~/Zynergy/forecast-data/gbif/`) are wanted: the handoff says "per DOI". Propose yes or no, with a reason.
5. **The D26 review's four minor items** (`docs/audits/2026-10-06-d26-review.md`, findings 2 to 5): the two run scripts not saved (the wait loop and the fetch call), the fetch deleting a partial file it did not create (`records/gbif_download.py:221-236`), the renderer showing 0 for `outside_t1_boxes` on old CSVs (`scripts/t2_render_tables.py:66-71`), and the builder's revert runner. Propose a fix for each, or say why none is needed.

**Stops:** main moved and a cite fails; the download's hash differs; `datasetKey` or date precision not available from the loader; any table measure you could only fix after seeing counts.

## Build

- **Tests first** through the real entry points, **revert checks** with the strict runner (restore from a saved copy, clear `__pycache__`, `PYTHONDONTWRITEBYTECODE=1`, refuse a run with collection errors or where the interpreter does not see the edit), full suite before and after, ruff clean.
- **The three outputs**, committed as evidence, with the scripts that made them:
  - D27: both keys' counts over D26's download, overall and per T1 box, beside the D32 follow-up's figures.
  - D28: the day-of-month table by dataset, with the test fixed in item 3 and its result per dataset, and what each candidate rule (keep, drop, per-dataset) would do to the survivor counts. **No rule is applied beyond the provisional one.**
  - D29: the dataset list beside the DOI record, the by-licence counts, and the register row's pointer. Licences are quoted as GBIF reports them, with the read time.
- No forbidden term (D58); no "probability" or "chance" in any table.

## Do not touch

No GBIF download; no credentialed call (the dataset API needs none); no climate pull; no model fit; no secret (D36); no edit to a filed record (D41) beyond the register's pointer, which D29 asks for; no merge (D40); nothing in the Forager app; no other branch's worktree.

## Report back

A completion report in `docs/audits/` with its index row, TASKS.md and START_HERE updated. **The D28 table goes to the owner for the final date rule**, so its report section must read plainly: per dataset, how many date-only records, the first-of-month share against 1 in 30, and the test result. Then message the Forager planner session.
