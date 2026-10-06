# Dispatch: D26's shared download, all US and Canada fungi sightings in one Darwin Core Archive, and its acceptance check

**Verify first and report by message; then build and download unless something is a stop. One GBIF download only. No Climate Data Store or Open-Meteo pull, no model fit, no merge.**

Written by the Forager planner session on 2026-10-06 (UTC), against forager-forecast `origin/main` at `ad64fef` (the D32 follow-up and its review, merged on the owner's "Merge both (Recommended)"; Forager RECORD -574). This dispatch is the first commit on branch `d26-shared-download`. Every line cite below is a premise to re-check, and every cite comes from a read-only pulse of `abac2b2`, whose tree `ad64fef` carries.

**Writer (D38):** a coder session on the credentials machine (this laptop), on `d26-shared-download`, worktree `~/Zynergy/forager-forecast-d26`. No other session writes to this branch. **Review (D18):** a separate session reviews the finished branch under `docs/dispatch/2026-09-18-review-protocol.md`. **Merge (D40):** only on the owner's written authorisation naming the branch.

## Why

The owner, in the Forager planner session: "Yes, write the sightings download dispatch after review". The 2026-09-20 planner handoff (`docs/planning/handoffs/2026-09-20-planner-handoff.md:27`) puts D26's download next, after the D32 follow-up: "geometry-selected, United States and Canada (D47), using the unified gbif_download with a new geometry predicate passed to request_template". D27 in full, D28's day-of-month table and D29's dataset list come after it, in that order, and **are not in this dispatch**.

**The boundary is ruled.** D26 (`DECISIONS.md`, row D26) allowed "a simple bounding polygon or GBIF's coordinate-derived administrative filter for the United States and Canada, whichever the builder verifies first"; D47 later put Mexico out. Asked which edge to use, the owner chose **"GBIF country borders (Recommended)"**, worded as: GBIF tags each sighting with the country its coordinates fall in, using a standard world borders map (GADM); the download asks for US plus Canada by that tag, so no Mexico and no trimming; the coder first checks the tag on a small test request. The other options were "Simple outline, then trim" and "Let the coder pick" (Forager RECORD -572).

## First commits: records owed from the D32 merge

Before anything else, on this branch:

1. **Decision rows D68 to D71**, quoting the owner verbatim (D68 to D70 from Forager RECORD -574, D71 from -572, all 2026-10-06):
   - D68: `records/t1_record.py` stays frozen with the rest of the T1 evidence (review finding 3): "Keep it frozen (Recommended)".
   - D69: the frozen T1 evidence now gives East 447,163 against the published 447,164, because it shares D63's cell rule; the frozen code is not edited, and the published figure reproduces at its own commit (review finding 4): "Accept, note it (Recommended)".
   - D70: the loader's two refusal reasons beyond D66's list, no usable `acceptedTaxonKey` (`records/occurrence.py:172-173`) and out-of-range coordinates (`:178-179`), stay, counted by reason (review finding 6): "Keep, counted (Recommended)".
   - D71: the boundary ruling above, superseding D26's "whichever the builder verifies first" clause and quoting it.
2. **An appended, dated correction** for the test count: the D32 follow-up has 117 test functions, not the 109 at `docs/audits/2026-10-06-d32-followup-completion-report.md:87`, `docs/planning/START_HERE.md:84` and `docs/audits/README.md:103` (the review, finding 1). Appended rows or notes only (D41).

## Verify first, report by message

1. **Base.** Fetch; `origin/main` is still `ad64fef`. If it moved, re-check every cite there.
2. **The request path.** `request_template` (`records/gbif_download.py:100-114`) and `submit_download_request` (`:117-153`), with their tests in `tests/test_records_gbif_download.py`. Confirm nothing builds a D26 predicate today and nothing polls or fetches a finished download (the pulse found neither).
3. **The predicate.** Propose it in full: kingdom 5 against the backbone checklist (`:38`, `:87`), `BASIS_OF_RECORD` HUMAN_OBSERVATION, years 2015 to 2025, `HAS_COORDINATE` true (the T1 template, `:82-97`, D26: "The content of the predicate ... does not change"), plus the country-borders filter for the United States and Canada. **No `CONTINENT`** (D26) and **no licence filter** (D61).
   - Find GBIF's predicate key for the GADM tag from GBIF's own API documentation, and say where you read it. Do not guess the key name.
4. **The small test of the tag**, before any download. Use GBIF's occurrence search API (no download, no credentials) with the same filters plus the GADM tag, `limit=0`, and report the counts:
   - US alone, Canada alone, both, and the T1 boxes inside them;
   - the same query by GBIF's `country` field, for comparison, and an explanation of the difference;
   - a sample of up to 20 records the country-borders filter excludes that `country` includes, with why (offshore, on a border, missing GADM).
   - **Stop** if the tag leaves out records inside either T1 box, or if it is not available as a download predicate.
5. **Size.** Estimate the download from those counts against T2's continent-wide DWCA (2,549,508 records, 1,475,785,280 bytes zipped; `docs/pulls/gbif-fungi-north-america-2015-2025.doi.json`), and confirm disk space for it plus its extraction.
6. **The acceptance check's input is on disk.** T1's first download, 0005709, is at `~/Zynergy/forager-forecast-t1-calendar-smoke-test/data/t1/downloads/0005709-260916113435855.zip`. The planner checked on 2026-10-06 that its sha256 is `6468a431…1683` and matches `docs/pulls/t1-fungi-two-boxes-2015-2025.doi.json`. Re-check it, read only. Don't move or copy it unless the planner says so.
7. **Longitude ±180** (the review's finding 8, `cells.py:68-96`): say whether any US or Canada record can reach it (the Aleutians cross 180°), from the test counts.
8. **The count region.** `records/counts.py:44` and `:57-63` still call T2's region "rest of North America", which D47 narrows. Propose the change.

**Stops:** main moved; no country-borders predicate exists, or it drops T1-box records; a filter difference no D-row decides; a size beyond the free disk; anything that would need a second download.

## Build and download

- **Tests first**, run and seen failing, through `request_template`'s real entry: the D26 predicate holds exactly the filters above plus the country-borders filter, and no `CONTINENT` or licence filter. A fetch-and-verify step, if you add one, is tested with a stub opener, not the network.
- **Revert checks**, one per behaviour: revert by one edit, confirm the failure is specific to that edit, restore from a copy saved before editing (never from git), and confirm the forward change is present afterwards. A run with collection or import errors is not cited.
- **The download, once,** on the planner's go after the verify report. Credentials from `~/.config/forager-forecast/gbif.env` via `credentials_from_env`, never printed (D36), never with curl. Wait for it in **one background loop that exits when GBIF reports it finished** (a check every few minutes is plenty), not a tight poll in the conversation; report if it is still queued after 60 minutes. Fetch it once, to `data/d26/downloads/` (gitignored).
- **Its record:** `docs/pulls/<name>.doi.json` in the existing form (the 0005714 record is the model): the DOI, key, the full request, sizes, record count, this machine's sha256 of the zip, `status: provisional` (test account), and `gbif_erases_after`.

## The acceptance check (D26)

- The T1 box subset of the new download contains every record key of 0005709, or each missing key is explained. Report the count of keys in each and the reasons for each missing key, grouped.
- The unified pipeline's counts over the new download, both step lists (`t1_steps`, `r6_audit_steps`), against the same counts over 0005714 and 0005709. Explain every difference by the narrower geography, the later snapshot, or a named step.

## Do not touch

- **No second GBIF download** without asking. **No Climate Data Store or Open-Meteo pull, no model fit.**
- **No secret** in any report, commit, log or chat (D36).
- **Frozen files stay frozen:** the T1 evidence (D67, D68) and the T0b verify script (D30).
- **No edit to a filed dispatch, report or decision row** (D41).
- **No merge into main** (D40). **Nothing in the Forager app repository.**
- **D27, D28 and D29's tables are the next task.** Don't build them here.

## Report back

A report in `docs/audits/` with its index row: what landed with commit hashes, the verify items, test and revert evidence with counts, the download's record, the acceptance check's tables, and what was not verified, stated plainly. Update `docs/planning/TASKS.md` and add a `START_HERE.md` session-log row (START_HERE:59). Then message the Forager planner session, which arranges the review.
