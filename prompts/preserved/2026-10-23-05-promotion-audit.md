# Promotion audit 2026-10-23: pre-main 32d381e45 into main (UX auditor)

**Auditor:** Elder, the UX auditor. A fresh session, on Claude Fable (a different model from the coders, which are Opus; -1134). **Planner:** Rowan. **Owner's word:** "Start the promotion", 2026-10-23.

**The rules:** `docs/process/planner-profile.md`, "Branches and the road to main". Audit against `docs/process/qa-philosophy.md` section 3. Report **pass**, **pass with items** or **fail**, each item with where it is and why it matters. "Advises, you decide."

**Scope:** `origin/main` 381515c61 to `origin/pre-main` 32d381e45, 142 commits, 341 files. The Room schema is unchanged (22 on both), so the upgrade check doesn't apply (-1133). What it ships, by the record:
- **Trail Map** (own vector Street) as default, with three map types, Trail · Topo · OSM: #377, -1554 to -1556.
- **The world fill south of 16°N** (Worker) and the TileJSON bounds: #376, #378, deployed (-1559, -1564).
- **Trail Map character v2:** tree glyphs, streams, area names, peak heights, wetland and tufts, six icons, bare rock and night colours. Style street-v2 and sprite v5: #379, -1561.
- **The night icon set** shipped in the app, plus the grey chosen chip when Trail stands in: #380, -1566, -1567.
- **Trail routing:** the route-data Worker (#381, deployed), the BRouter engine (#382), and downloads storing routes (#384 split, #383). -1568 to -1573.
- **The GitHub release job:** #385, -1574 to -1576.

**What to read:** each PR's body and Sorrel's review in `docs/audits/2026-10-2[23]-review-pr*.md`, on branch review-pr376-pr377. List the review findings left open and judge whether any matters for public use.
- **Known bugs:** `docs/process/known-bugs.md`. Name the ones this promotion ships with (A6), and anything that should be added.
- **The S22 evidence is in the record.** The S22 runs a build with this code (night check -1567, routing timing -1572, live route download -1573).
- **Mark anything not seen on a phone as "not seen on a phone".**
- **Pay particular attention to:**
  - the owner's "don't make me think" (CLAUDE.md, UX defaults);
  - "nothing fully obstructs the map";
  - the offline-routes row wording, which must match the owner's draft exactly;
  - anything a user could misread as broken.

**Deliver:**
- your report at `prompts/preserved/2026-10-23-05-promotion-audit-report.md` on branch records-after-173 (fetch, append only that file, push; if the push races, merge, never rebase);
- the verdict, the items and your model in your final message.

Change no code, merge nothing, deploy nothing, and don't use the phones.
