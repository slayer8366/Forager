# Planner handoff, 2026-10-17 (written 09:31Z, 02:31 PDT Saturday)

Written by the outgoing planner (Rowan Waymark) at the owner's word: "Context window is getting full, so we will have to hand this off to another session window. I'll meet you over there". It replaces `2026-10-16-planner-handoff.md` as the start point.

**Next free RECORD ID: -1180** (this handoff is -1179). Records worktree: `~/.cache/forager-wt/records`, branch `records-after-173`.

**The record's standard has changed.** RECORD.md's header now points at E-GD's RECORDING.md (-1111):
- every intent carries Scope boundary, Baseline, both predictions, Finish line and Abort conditions;
- every intent is closed by exactly one terminal, or by an outcome carrying `Closes`;
- write entries with a **quoted** here-document (`<<'EOF'`) and fill placeholders with sed afterwards. Backticks in an unquoted here-document once ran as a command (-1151).

## Read first

- `~/Zynergy/Forager/CLAUDE.md`, which now includes the promotion rule;
- the memory index;
- on pre-main, in `docs/process/`:
  - `planner-profile.md`, the team table and "Branches and the road to main";
  - `qa-philosophy.md`;
  - `dispatch-format.md`;
  - `known-bugs.md`;
  - the five role profiles;
- RECORD.md on `records-after-173`, entries -1085 to -1179.

## The team (-1143, -1176)

| Name | Role | Model | Profile |
|---|---|---|---|
| Rowan Waymark | planner | the session's own | planner-profile.md |
| Hazel Cairnwright | coder | Opus | coder-profile.md |
| Elder Ford | UX auditor (promotions into main) | Fable | auditor-profile.md |
| Sorrel Tally | independent reviewer (D18) | Fable | reviewer-profile.md |
| Juniper Sounding | researcher (read-only) | Fable | researcher-profile.md |

- Audits and reviews never run on the coder's model (-1134).
- Give each helper its name and profile link in its prompt.

## Standing rules new since the last handoff

- **Main is production** (-1086, -1133). It changes only by a promotion from pre-main, through a gate:
  1. CI on pre-main;
  2. Elder's report;
  3. the owner's approval.

  Phone checks and field walks are proposed by the planner, and the owner decides. The upgrade check runs only when storage changes.
- **The branches:**
  - small tasks and big features go into **pre-main**;
  - **dev** is main plus the dev tools plus the experiments, rebased onto main after every main update (-1087).
- **Check from source, not memory.** At every handoff and whenever a report lands, list the open intents and close or continue each (planner profile; -1120).
- **Unchanged:** no build without the owner's go; times in Pacific; the owner's screenshots stay off GitHub; the camera never rotates.

## Reminders bound to the OLD session (they won't reach a new one)

Two one-shot reminders were set by the outgoing session with send_later. Re-create them in the new session, or do them by hand:
1. **Today 07:02 PDT (14:02Z): restart t6b-night.** Check it is inactive and that the PAUSE file at `/run/media/zynergy-labs/2ebd084f-5fdd-4730-8cef-96b267723190/forecast-data/t6b/PAUSE` is absent. Then, from `~/Zynergy/forager-forecast-host-genera-build`:

   ```
   systemctl --user reset-failed t6b-night; systemd-run --user --unit=t6b-night -p MemoryMax=5G -p MemorySwapMax=0 -p Nice=10 --working-directory=$PWD $PWD/.venv/bin/python scripts/t6b_run.py --until 07:00 --stages scanfi-layers --workers 2
   ```

   It made about 140 units an hour; roughly 15,900 units are left, so about 4 to 5 more nights (inferred).
2. **Today 10:00 PDT (17:00Z): W42.** Check whether the climate archive holds W42's window (through 2026-10-12T00:00Z). If it does, bring the -1034 route (forecast branch `pnw-pilot-w42`, then the site PR, then the merge) to the owner, step by step.

## State at handoff

- **Forager:**
  - main `969ecf0a`, the first gated promotion (-1135);
  - pre-main `b221e85b`, which holds the QA follow-up, the five profiles, the trail-plan evidence and Juniper's profile. These are documents waiting for the next promotion, which Elder audits on Fable;
  - dev `cbd8c40a`: main plus the dev tools, the probes, engine A, the lens chooser and Phase 2a's diagnostics (#242).
  - Open PRs #101, #104 and #109 are old tidiness items.
- **forager-forecast:**
  - main `b9127e58`;
  - t7-build `9ad04ba0`, with part A merged (-1170);
  - **PR #3** (part C, the saprotroph count) and **PR #4** (part B, the access layer) are green into t7-build and await the owner's word naming each (D40).
- **t6b-night** is running until 07:00 PDT. No T7 process is running.

## Open items, in order

1. **T7.** The owner's word on PRs #3 and #4. Then write the re-fit dispatch (dispatch format; Hazel; P10b with conditions 1 to 4; the road band; the saprotroph contrast; specimen records, CC0 and CC BY only, placed within 1 km). The re-fit is **-1030's second point of no return**: ask first. After it, two Sorrel reviews on different models, then any publish yes (the third point). The specimen fetch for condition 4 isn't built yet; it belongs in the re-fit dispatch or a small one before it. P10b is in metrics.py (D145, amended -1167).
2. **Camera, the lens chooser fixes (-1149).** Phase 2a (diagnostics, the zoom_form switch, OIS on the S22, log fixes) is merged into dev (-1154). The APK is at `~/Zynergy/device-evidence/2026-10-17-lens-fixes-2a-apk/`. Next: the owner's device run, about 15 minutes per phone, with Hazel's exact steps in her Phase 2a report (summarised in -1154). Then the item 1 fix from the evidence. Asked "now or tomorrow?" with no answer yet. A zoom cap at 8x would be an owner decision.
3. **Trail navigation.** The plan stands (-445): OSM as the routable base; Forest Service and BLM as a separate reference layer; USGS dropped (-1176). The evidence section is in pre-main. Nothing is dispatched.
4. **Earlier carry-overs:**
   - the known-bugs list awaits the owner's own field and closed-testing list (it has two seeded entries);
   - Play production access is still pending (applied 2026-10-15; about 7 days);
   - the tidying inventory (2026-10-15-08): lint, scratch branches (many new today: camera-reach-revert-*, lens-chooser-revert-*, lens-fixes-2a-revert-*, the forecast *-revert-* branches), old PRs;
   - Dr Põlme is owed a link when the first PNW forecast results are published (-1169).
5. **The 66 older open intents** found by the open-intents check (-1120). They are for the lookup tool or the new monthly record (E-GD is building both), not hand-closing.

## Things the next session should know

- **The old planner session** ("Closed", PID 1863475) may still be open. It forwarded all three of its helpers' reports and writes nothing.
- **An unexplained failure:** one run of test_t6b_host_genera.py and test_t6b_us_half_runner.py, under memory pressure; it didn't reproduce (-1178). Watch for it.
- **To re-check before publishing:** the Forest Service licences weren't re-read at source today (data.gov returned 404), and the forest-boundary licence is second-hand (-1178).
- **The owner's style today:** short answers, often "recommended". Asks "status?". Values accuracy over ambition ("I want to be accurate and have the best data than throwing anything possible").
