# Planner handoff, 2026-10-19 (written 05:16Z, 10:16 PM PDT Sunday 2026-10-18)

Written by the outgoing planner (Rowan Waymark) at the owner's word: "Write a hand off for the next session and I'll meet you over there". It replaces `2026-10-18-planner-handoff.md` as the start point.

**Next free RECORD ID: -1360.** This handoff is -1359. The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`. Forager's IDs stay below 2000; the forecast's record (Alder) uses -2001 and up.

## Read first
- `~/Zynergy/Forager/CLAUDE.md`
- the memory index. New since the last handoff: "ask-with-the-question-tool".
- `docs/process/planner-profile.md` on main
- RECORD.md entries -1276 to -1359

## Where things stand
- **main = 19aa1058, the production promotion** (PR #268, -1358). Its tree equals pre-main's (adce07a1).
  - All -1086 gates were met:
    - CI 39201526634 green at 21dc4470;
    - S22 -1336, S26 -1338, tablet -1337, -1348, -1351;
    - Elder's three top-up audits, each pass with items: preserved/2026-10-18-06, -11, -12;
    - the owner's approval (-1356).
  - One owner override: #265 and #266 had no branch CI (-1354, -1355). The pre-main run at 21dc4470 covered them, green.
- **Next, on the owner's laptop:**
  1. The release build from main (the signing keys are only there).
  2. **T9, the store listing check:** the owner pastes the listing text, and the planner checks it against what ships.
  3. The owner uploads to production.
  - Elder's suggested release-note lines:
    - Maps opens on Street; Layers gives Topographical.
    - In landscape, the rail reads List at the bottom, up to Tools at the top.
    - On tablets, Journal, List and Seasonal use a wider centred column.
- **The Worker** redeploys from GitHub on main. Just after the merge, a fresh tile answered `x-forager-origin: r2` and `/style/offline.json` returned 200. Whether the Builds redeploy had finished wasn't checked.

## What shipped in tonight's pre-main (for the release notes)
- **Map launch fixes:**
  - no glyph requests;
  - no library User-Agent;
  - OpenTopoMap's full credit behind the "i";
  - "The map couldn't start." with Try again;
  - Street as the default;
  - PI_ORIGIN_URL out of wrangler.toml.
  - Records -1278, -1283, -1297, -1304.
- **Offline maps picker always on Street;** the MapLibre logo beside the "i" (-1309, -1315).
- **The landscape rail** reads List at the bottom, Tools at the top; TalkBack follows the drawn order. **The Tools drawer has no tint** (-1312, -1313, -1321).
- **Search:** an open Journal entry hides search on the Journal only. **The Journal's landscape reveal:** the switch line slides down with the bar, and a map under it never moves; the entry map's slide-in is kept (-1330 to -1334). The #261 strip was reverted (-1327, -1329).
- **Tablet:**
  - Journal, List and Seasonal sit in a centred 960 dp column;
  - square maps with the info beside them;
  - the search bar and its open panel sit on the column's edges.
  - Phones are unchanged. Records -1340, -1350, -1353, -1355.
- **Known-bugs:**
  - row 2's rate is about 11% tonight (3 of 27 runs);
  - row 7 corrected;
  - rows 8 and 9 new (the logo over the Topo caption; a probable two-line Topo night caption).
- **The Pi is out of the live tile path:** the dashboard variable was deleted (-1305), and the Pi's tile server is stopped (-1310).

## After the release (the owner's word needed for each)
1. **Revert checks for the List/Seasonal tablet tests** (Elder item 1). They have never been seen red for their behaviour.
2. **File Hazel's completion reports and index rows** for #264 to #267 (Elder item 4). The record has the history.
3. **The flaky test** `MushroomLogViewModelWiringTest` (known-bugs row 2) is at about 11% now. The flake hunt is parked by the owner.
4. **The own Street style (#258,** branch street-cloudflare, head 5b270ac4, green, **held**).
   - The design is decisions 3 to 8 (-1284 to -1289), as -1300 limits them. Night colours were added in rounds 1 and 2 (-1343 to -1345).
   - Night may depart from inversion where it reads better (-1344).
   - The previews are on the tablet: Pictures/Forager-street-preview-r2.
   - **The owner wants this styled before the next release** (-1342). The next step is more review rounds via the question tool.
   - **Deploy:** the owner deploys by hand (Hazel's handover in `~/Zynergy/device-evidence/2026-10-18-street-cloudflare/handover/`). Pause Workers Builds auto-deploy first. Then an S22 review copy with `STREET_SERVED_BY_OWN_MAP` on.
   - Older offline areas use option (a): all areas show the new style once one exists (-1316).
5. **The Pi's US and Canada z15 build**, by session raspberrypi-fizzy-lagoon (Remote Control):
   - estimated about a day, unmeasured;
   - the file will be named `forager-usca.pmtiles`, Protomaps v4.15.2, so it renames to `us` at upload (-1341);
   - nothing goes to R2 without the planner's schema check and the owner's go.
   - The website's forecast map also reads `/us/{z}/{x}/{y}.mvt` (zynergy-site `Forager/forecast/app.js:64`), with lower-48 bounds hard-coded at `:68`.
6. **UI split:**
   - the survey is on branch `ui-split-survey` (81db63cb, -1326);
   - the owner's goal is one part per file, so several agents can work in parallel;
   - write the plan when the owner asks;
   - it lands next release.
7. **The tablet portrait widths differ** between tabs (Elder item 3, pre-existing). Seasonal is about 928 dp wide in landscape (the owner's choice; not confirmed that the owner looked).

## People and sessions
- **Hazel** (Opus subagents) can't be moved between sessions. Start fresh ones from the new session.
- **Elder** (Fable) does the audits.
- **Alder** is the forecast planner, in session bridge-cse-01becytz4ulqzv9n1u8r42ht-c6 on this machine.
  - The owner answered Alder's three questions in Rowan's window: W42 published late, the 17-folder cleanup a go, the borderline records into both repos. Rowan relayed them verbatim (Alder may confirm with the owner).
  - The work drive now has 11 GB free.
- **The old Rowan session** ("Rowan prompts/pre...") is done; the owner can close it.

## How the owner worked tonight
- **Questions through the question tool** ("walk-through"), because it notifies them (memory "ask-with-the-question-tool").
- **Words mean exactly what they say:**
  - restate before building;
  - never record the owner as accepting something they didn't say. That was corrected in -1331.
- **Check the source before claiming.** Two planner slips were caught tonight:
  - a bisect that never saw an open entry (-1328, corrected in -1329);
  - a stale dispatch premise about a wide tree (-1340).
- **Fold same-topic follow-ups into one record entry** when the owner asks ("Record it as -1354, since it's still talking about that topic").
- **The owner can override CI for a case:** "Skip CI entirely" (-1354). It's a one-case override, not a rule change.
