# Planner handoff, 2026-10-11 07:37 UTC (Sunday 00:37 PDT)

Written by the outgoing planner session for the next one. Read ~/Zynergy/Forager/CLAUDE.md, the memory index, and RECORD.md on origin/records-after-173 (entries -802 to -853) first. Next free RECORD ID: -854. Records worktree: ~/.cache/forager-wt/records (branch records-after-173).

Standing writing rule (memory: describe-products-generically): other products, apps and device makers are described generically or by the behaviour itself, in every output, chat included. Don't discuss the rule; just write that way, and put it in every coder's dispatch.

## 1. The deadline

Monday 2026-10-12 morning PDT (about 15:00 UTC): the PNW pilot forecast map live on zynergy-labs.com/Forager/forecast/ with the weather model's scores (-814, -824). The owner: "Monday morning full finish is the deadline nonetheless" and "take care" (no cuts). Fallback ruled in -837: if the all-years model isn't fitted, checked and scored by then, the 2019-2025 model goes live, labelled "fitted on 2019–2025; the full test over 2015–2025 is pending". The headline T1 result is only ever the all-years run (D33).

## 2. What is running now (laptop, no session owns it any more)

The forecast builder's processes run on their own in ~/Zynergy/forager-forecast-pnw-pilot (branch pnw-pilot-t1, head ae4f1e9). Data is in ~/Zynergy/forecast-data-pnw-pilot/.
- The timeseries land-weather pull: 5 workers since 07:20 UTC, 698 of 1,870 points at 07:21, about 180 an hour, finishing about 13:30 to 14:30 UTC. Log: pull_ts.log. Rain is complete for 2014-09-01 to 2025-12-31 plus the 2026-01-01 00:00 stamp (read back by the builder; log pull_precip_hourly.log).
- The hedge watcher: `hedge_ready.sh && scripts/pnw_hedge_run.sh`, which then runs pnw_t1_run_all.sh main. It starts by itself when the data is in, then runs: the timeseries-vs-gridded check over all 1,870 points (-842); the weather build; the equivalence step with the hourly land-route check as the gate (-818, -834; exit 3 on a land fail); the calendar and full fits on one unit set, coastal cells by nearest land neighbour (-822); then the random-date comparison and D33 (3) and (4) (-828). Expected: models about 16:30 to 18:30 UTC Sunday. Outputs go under the data dir (fits/, models/); the builder's report is docs/audits/2026-10-10-pnw-pilot-t1-report.md on pnw-pilot-t1.
- The builder subagent belonged to the old session and can't be messaged. Watch the logs and the process list. If the chain stops or a gate fails, dispatch a new forecast coder from the report, which records every ruling, pace figure and correction. The builder has twice reported edits that hadn't applied (-834, -849): any new coder reads back each claim from the committed state.

## 3. What happens when the models exist

a. **Review pass 2** (the D18 review, not yet done; -816, -837): a new reviewer starts from the "Pass 2 / Pending" section of docs/audits/2026-10-10-pnw-pilot-t1-review.md (branch pnw-pilot-t1-review, 3e5aa7d). It checks the equivalence result, both fits, the headline and its interval, the per-fold table, the random-date comparison, and its own prediction of 52,718 units and 1,138 positives for the weather unit set.
b. **Scoring** (branch pnw-pilot-scoring, 60c4285, worktree ~/Zynergy/forager-forecast-pnw-scoring): the scoring coder has finished; dispatch a new one for step 4. Week 2026-W41 (week start 2026-10-05), Copernicus weather (-819), no Open-Meteo in any output. One command: `uv run python scripts/pnw_pilot_publish.py --kind full --repo-copy`, adding `--beats-calendar` and `--t1-result` from the builder's report. It needs `copernicus_sources` in models/primary_t1_1000m_full/model.json. The full file will pass 1 MB and stay outside the repo (scoring/out/). The calendar floor was re-published at 5d2ce36 (2,618 cells, 231 from a land neighbour); it was not dropped into the live site, on purpose, so the site changes only once.
c. **Site drop** (-817 approval: "put the map on a live site when ready", with -814's labels): copy manifest.json and cantharellus.geojson into zynergy-site Forager/forecast/data/pnw-pilot/current/ on a branch from main 734b2a6, push, wait for the preview, run ~/Zynergy/device-evidence/2026-10-10-pilot-map/check.py at 1366x860 and 390x844, read the banner by eye (pilot, validated false, reviewed per the review, beats_calendar, week, attribution: GBIF citation plus the Copernicus text for the products read), then merge with a backup bundle and a RECORD merge entry. A later week's data is a data drop under -817, recorded but not re-asked.
d. **The briefing** (branch pnw-pilot-plans, 8ee26f5, docs/briefings/2026-10-12-oms-briefing.md): fill every TO FILL from the builder's actual figures. It must not go to the owner with any TO FILL left.

## 4. Forager app state

- main bde42068 (PRs #211 to #215 merged on 2026-10-10/11; RECORD -836, -848, -851, -852, -853). CI is green.
- The beta (-830): the owner builds and signs it from main in their own checkout; it now includes Return-ends-on-arrival (#214).
- Still owed on a phone, from #214's report (docs/navigation/2026-10-11-return-ends-on-arrival-report.md): the S22 desk and walk steps (must leave first; Arrived held 10 s then closes; no off-track alert on a drive-off; Back by ends on arrival).
- The production release after this beta: tidiness and an Android lint round (-831), scope only, no go-ahead.
- Not dispatched: the sundown heads-up differing about 30 minutes between the two phones (-843; the owner: "They're probably different. I didn't check." about the settings); T20 after own tiles; track L next release.

## 5. Forecast, beyond the pilot

- T6b: the PNW box is complete and reviewed (-838: seam verdicts as T5's, nothing blocking). The continental SCANFI run stays stopped. Open for the owner: the one-pass speed-up (about 8 to 12 h against 114 h) and the manifest's size limit (-815).
- T7 dispatch drafted (pnw-pilot-plans, docs/dispatch/2026-10-11-t7-pnw-pilot-habitat.md). O4 (ecoregion level) and O7 (merge order and base branch) are open for the owner before T7 starts, after Monday.
- The Open-Meteo rain gap is explained (-833): an hour offset plus a 0.1 mm drizzle cut. It's an input for the T10 live route.

## 6. Owner items, not ours

- Ask the hosting site's support to purge old commits still reachable through pull-request pages, for Forager and zynergy-site.
- The backups under ~/Zynergy/forager-repo-backups/ (including the 2026-10-11-scrub mirrors and bundles) hold old history. The owner decides when to delete them.

## 7. Working rules this session confirmed

- One Gradle build at a time, and none while a forecast fit runs (check `pgrep -f 'scripts/[p]nw_t1_fit.py'`). The T6b run pauses with its PAUSE file for builds.
- Parallel tracks around the critical path (memory: parallel-tracks-under-deadline). The review runs alongside, not after.
- A pull request's merge always comes on the owner's word, pinned to the CI-green head, with a backup bundle first and a RECORD merge entry after.
