# Dispatch: D32's follow-up task, one filter pipeline, one Record type, one cell rule

**Verify first and report by message; then build unless something is a stop. No download, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Yes go ahead and do as you proposed", against forager-forecast `origin/main` at `1d5bd80`. This dispatch and D63 are the first commit on branch `d32-followup-unify-filters`. Every line cite below is a premise to re-check. The stage 2b report cites main `47204f7`; `git log 47204f7..1d5bd80 -- src tests scripts` is empty, so its code cites should still hold.

**Writer (D38):** the coder session on the credentials machine (this laptop), on `d32-followup-unify-filters`, worktree `~/Zynergy/forager-forecast-d32-followup`. No other session writes to this branch. **Review (D18):** a separate session reviews the finished branch under the standing review protocol (`docs/dispatch/2026-09-18-review-protocol.md`) before anything merges. **Merge (D40, D50):** only on the owner's written authorisation naming the branch; the merge message records it.

## Why

D32 ends: "A follow-up task unifies the two filter pipelines into one module." D42: "The two Record representations coexist until D32's follow-up task unifies the filter pipelines, and that task chooses one." The 2026-09-20 planner handoff puts this task first in "What is next", ahead of D26's download, which must be read by one loader (D32's reason). Its verify-first inputs are listed in `docs/audits/2026-09-20-d32-stage-2b-report.md`, section "Inputs for D32's follow-up task".

## What must be true when this is done

1. **One Record type.** T1's frozen dataclass (`records/t1_record.py:21-22`) or T2's `Mapping[str, str]` (`records/filters.py:32`), chosen by this task (D42). **Propose the choice with reasons in your verify report, before building.** The planner will bring it to the owner if it is a real trade-off.
2. **One filter pipeline, in one module**, used by both T1's and T2's callers and scripts. Every filter step both pipelines apply today is either kept, with one implementation, or retired on purpose with a reason written down.
3. **One cell rule: nearest grid point (D46), on two grids.**
   - ERA5-Land points at multiples of 0.1°, ERA5 at multiples of 0.25°, longitudes in −180 to 180, as confirmed from delivered files (D51; `docs/audits/2026-09-22-grid-positions-d51-report.md`). **The planner's research note said ERA5 delivers 0 to 360; the delivered files say −180 to 180.** Code to the delivered files, and say in the report which you relied on.
   - `cell_for` has no 0.25° mode today (same report). Add one.
   - **Exact ties go toward +∞ on each axis (D63, new on this branch).** `cell_for` today goes 0.1° further west at negative-longitude ties (ROUND_HALF_UP, `cells.py:39`; part 2 report). Fix it to match the sources.
   - T2's floor convention (`weather_cell`, `filters.py:102`, used by `duplicate_key` at `:116`) is retired (D46). The duplicate key uses the unified cell.
4. **One request builder for new downloads.** `build_download_request` with SIMPLE_CSV (`gbif_download.py:27`, `:96`) and `request_template` with DWCA (`:107`). D26's download uses DWCA (D45 carries the DWCA template). Retire one on purpose. **Say how T1's already-downloaded SIMPLE_CSV data is read afterwards**, or that it is no longer read, and why.
5. **Stale text corrected:**
   - the three docstrings the stage 2b report names (`records/__init__.py:1-13`, `records/t1_record.py:4-5`, `records/gbif_download.py:1`);
   - the three items step 1 left stale under D44's scope (the key's docstring, the step name `duplicate_observer_cell_day`, and `COLUMNS_READ` in `scripts/t2_count_table.py`);
   - the two handoff passages naming old paths (`docs/planning/handoffs/2026-09-20-coder-handoff.md:84-96`, `2026-09-19-planner-handoff.md:111`) get **dated appended corrections** under D41, never edits above them.
6. **The credential loader's missing-two-of-three case gets its assertion** (`credentials_from_env`, `gbif_download.py:52`), as the move review proposed.

## Verify first, report by message

1. Base: fetch; `origin/main` is still `1d5bd80`, and this branch is `1d5bd80` plus this commit. If main moved, re-check every cite there.
2. Every cite above, confirmed or corrected with file:line.
3. A table of every filter step in each pipeline today: what it drops, where it lives, whether the two agree, and what the unified pipeline does with it.
4. Your Record-type proposal (item 1), and your request-builder proposal (item 4), each with reasons.
5. **Who calls what:** every caller of both pipelines, both cell functions and both request builders, in `src`, `scripts` and `tests`. A function with no caller is reported, not unified.
6. **What changes for real data.** Run both old pipelines and the unified one over the data already on this machine (T1's and T2's downloads, read only), and report the counts per step for each, and how many records change cell under D46 and D63. A difference not explained by D46, D63 or a step retired on purpose is a stop.

**Stops:** main moved; a filter step the two pipelines implement differently, where neither D-row decides which is right; the Record-type choice turns out to be a trade-off the owner should rule on; any unexplained count difference in item 6.

## Build

- **Tests first**, run and seen failing before the code, through the unified module's real entry points: the tie direction on both axes at both grids, the 0.25° mode, the retired floor, and each filter step.
- **Revert checks**, one per behaviour above: revert by one edit, confirm the test fails with a message specific to that edit, restore from a copy saved before editing (never from git), and confirm the forward change is present afterwards.
- **The full suite** before and after, as test functions (one unit is one test function, as the stage 2b report counts), and ruff check and ruff format clean.
- Commit and push as you go.

## Do not touch

- **No GBIF download, no Climate Data Store or Open-Meteo pull, no model fit.** D26's download is the next task and waits for this one's review (D18).
- **No secret** in any report, commit, log or chat (D36). Account identifiers only where needed.
- **Frozen pack scripts and the T0b verify script** stay unchanged (D30).
- **No edit to a filed dispatch, report or decision row.** Corrections are appended or are new files (D41).
- **No merge into main** (D40).
- **Nothing in the Forager app repository.**

## Report back

A report in `docs/audits/` with its index row, answering this dispatch: what landed with commit hashes, the verify items, test and revert evidence with counts, item 6's count tables, and what was not verified, stated plainly. Then message the Forager planner session. The planner arranges the independent review.
