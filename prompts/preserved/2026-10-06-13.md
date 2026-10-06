# Dispatch: T6, the observation layer (the effort surface), verify and propose first

**Verify first and report by message. This is the project's first model fit, so every modelling choice is proposed and ruled before anything is fitted. No fit until the planner says go. No download, no climate pull, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), on the owner's "Yes, write the date-rule task and T6", against forager-forecast `origin/main` at `41005c1`. This dispatch is the first commit on branch `t6-observation-layer`. Every cite is a premise to re-check.

**Writer (D38):** a coder session on this laptop, worktree `~/Zynergy/forager-forecast-t6`. **Review (D18).** **Merge (D40).** **Decision rows from D101.** **Runs after the D28 date-rule task** (`docs/dispatch/2026-10-06-d28-date-rule.md`), one heavy job at a time on this 11 GB laptop. **No fit until D28's final rule is filed** (D28: "The owner rules on the final rule before any model is fit"). Commit and push after every step.

## Why

TASKS.md, T6: "Depends on: T2 · Does: build an effort surface per weather cell and week from all fungal records, a benchmark taxon and a weekday term. · Verify: the effort model reproduces the weekend effect and beats a constant-effort model on held-out deviance." SPEC.md: "Habitat x trigger x observation, with a joint model as challenger." D11 and D12: sighting chance is the chance the group is reported in a weather cell and week, given at least one fungal observation of any kind there that week. T7 uses the effort covariate and predicts with effort held constant.

## Verify first, report by message

1. Base `41005c1` (or what moved). The data is D26's download, read in place, through the unified loader and filters (records/), with D28's final rule once filed.
2. **Propose every modelling choice, with reasons and a source, before any count of the outcome is read.** The owner rules on them.
   - The **unit**: a weather cell (ERA5-Land 0.1°, D46, D63, D64) × ISO week, over the US and Canada (D47, D72), or a first region. Say which, and why.
   - The **response**: what "effort" is measured as (count of all fungal records per cell-week? count of distinct observers? of distinct observer-days?), and which duplicate key (D27).
   - The **benchmark taxon** (EVIDENCE.md:53 cites temporal pseudo-absences with a benchmark taxon): which taxon, why it tracks effort and not fruiting, and the literature for it.
   - The **weekday term**: how weekday enters a weekly model (the share of the week's records on weekends? per-day records?), and how "the weekend effect" is tested.
   - The **model family** (for example a Poisson or negative-binomial GLM or GAM with offsets) and its covariates. Name the package, pinned by uv.
   - **Held-out deviance**: the split (D33's leave-one-year-out?), the seed (D31's 20260918), and the constant-effort comparison model. **D31's tuning budget and grid are written into the repo before any fit.**
   - The **output**: what T7 reads (an effort covariate per cell-week), and its file and format.
3. What D27's two keys, D28's rule, D48's per-record licence and D61 (all licences) mean for T6's input, and the record counts at each step.
4. Size and memory: the cell-week table's size, and the fit's expected memory against an about 6 GB ceiling.
5. **Stops:** any choice that needs an owner ruling (most will; send them as one list with recommendations); any fit-before-ruling temptation; memory beyond the ceiling.

## After the rulings (the planner will resume you)

Tests first, the fit, the Verify from TASKS.md, the strict revert runner and the full suite. A report with its index row, TASKS.md and START_HERE.

## Do not touch

No download, no climate pull, no secret, no edit to a filed record (D41), no merge, nothing in the Forager app repo. No forbidden term (D58), and no "probability" or "chance" outside D12's defined term.
