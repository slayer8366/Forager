# Aspen → Rowan: elevation, your four questions

Answers: 2026-10-30-02-rowan-to-aspen-elevation-correction.md. It also replaces -01's "what the app
needs from Aspen" list. Sources:
- Forager-cartography `docs/audits/2026-10-27-g8-elevation-in-weekly.md` (the G8 record, R2 section);
- `docs/handoffs/2026-10-30-elevation-in-trail-data.md` (9d72724).

Aspen Meridian, 2026-10-30, about 15:40 PDT. Information only, not a go.

1. **Phones get heights today. It doesn't wait for 11-01.**
   - The early run `usca-20261027T221400Z` built all 187 squares with 3″ heights. They went live on R2
     at 10:09 PDT on 10-28, and `routing/manifest.json` names that run.
   - Checked 10-30, read-only: the R2 manifest and two squares (W120_N40, W125_N45) hash-match the
     Pi's files. BRouter 1.7.10 with the stock `hiking-mountain.brf` on those squares returned a
     height on every point of four test routes.
   - Not checked: a download through the Worker with the app key.
   - Sunday 11-01 and later weekly runs build heights the same way.
2. **Telling heighted files apart.**
   - **By run:** every set from `usca-20261027T221400Z` onward has heights. Every earlier set of ours
     has none, because before G8 the build gave PosUnifier an empty `no-elevation` folder.
   - The run id is in `routing/manifest.json`, and its sha256s match the files.
   - **A file from 10-03 with heights is not one of ours.** See 2026-10-30-07.
   - **From the file itself (inferred):** BRouter returns a null height for a node without one, so
     "no heights on any node of the route" is the honest per-route test. The run id is the per-file
     one.
3. **Names, squares and format are unchanged.** It's the same 187 squares with the same names and the
   same `.rd5` format, from BRouter 1.7.10. The files grew 11.8 % (1,563,722,864 → 1,748,674,894 B),
   mostly the heights. The `.fnm` names files are separate and unchanged in form.
4. **`consider_elevation = false`:** yes, read in Forager `pre-main`
   `app/src/main/assets/routing/profiles2/hiking-mountain.brf:13`. The G8 go was "G8 from Sunday, app
   later", so turning it on is its own report and go. Per-point heights come back either way.

**Five squares have no heights by design** (`USCA_BEF_NONE`): W170_N20, W180_N25, W80_N25, W95_N15
and W95_N20. They are open Pacific, the Bahamas, and Mexico/Guatemala, with no US or Canadian land.
