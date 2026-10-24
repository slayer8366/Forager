# Planner handoff, 2026-10-24 (written 08:00Z, 1:00 AM PDT Saturday 2026-10-24)

Written by the outgoing planner (Rowan Waymark) at the owner's word: "Have everyone commit their work, and get ready for a session switch. Write a handoff for the next planner please." It replaces `2026-10-23-planner-handoff.md` as the starting point.

**Next free RECORD ID: -1597.** This handoff is -1596. The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`. Forager's IDs stay below 2000.

## Read first
- `~/Zynergy/Forager/CLAUDE.md`, then the memory index.
- RECORD.md entries -1588 to -1596. These are short: the decision, the owner's words, and the few facts behind them.
- The 2026-10-23 handoff, for the keys, the Worker deploy steps and the promotion's after-merge steps. All of it still holds, except where this file updates it.

## Check before acting
- **Is the outgoing session still running?** At handoff, no helper was running and every helper's work was committed and pushed. Their worktrees were clean: `tile-retry`, `way-home-report`, `way-home-split`, `way-home-domain`, `records`.
- **Has anything landed since this file?** Run ListAgents. Then `git log` on `origin/records-after-173`, `origin/pre-main`, `way-home-split` and `way-home-domain`, looking for commits newer than this file.
- **Who carries on?** Ask the owner which session carries on before you dispatch anything (-1588 is the last time).

## Branches
- **main:** 381515c61, unchanged.
- **pre-main:** fefc89129, which is #386 (the tile retry) merged into adcbedb19 (-1593). CI is green there (run 40072059837).

## The promotion of pre-main to main
The owner started it with "Start the promotion" (-1578). They then answered "Yes, carry on (Recommended)" (-1595).

Done so far:
- **Elder's audit of 32d381e45:** pass with items. Items 1–3 were fixed by #387.
- **Elder's re-check of the #386 range:** pass with items, all four of them non-blocking (`prompts/preserved/2026-10-24-01-promotion-recheck-report.md`, 9495c77b0).
  - Item 1: the five-minute give-up for a download ends in "Couldn't download offline maps." and deletes the partial area. This hasn't been seen on a phone.
  - Item 2: the known-bugs list needs two rows: the "no signal" reason shown on a server error (-1594) and the fast-fling blank patch (-1591).
  - Item 3: the screen reader isn't told of the pause. Low.
  - Item 4: battery and data notes. Nothing to fix.
- **Main backed up:** `~/Zynergy/forager-repo-backups/forager-main-381515c61-20261024.bundle`, with a `.sha256` beside it. It is verified, and a restore into an empty repo gave 381515c61.
- **The diff main..pre-main read for silenced tests:** no `@Ignore`, `assume` or `@Disabled` added, and no test file deleted. 35 assertion lines were removed and 692 added.
  - Four files were checked by hand. Each removal was a rewrite, mostly the Street → Trail Map rename.
  - The other 10 files with removals look like the same pattern, but that is inferred, not read line by line.

Left:
1. **Ask the owner** whether Elder's item 2, the two known-bugs rows, goes in before the promotion PR. It is a docs change on pre-main, and Elder advises it.
2. **The S22 phone check:** the offline row, plus a day and a night glance.
   - The S22 is back with the planner. It is on build 1.0.3897+g530cbf8a, which is the #386 head merged into adcbedb19, not fefc89129 itself.
   - Region 1 is the owner's, and must not be touched. The tile budget is at 1336 / 6000.
   - #387's fixes have not yet been seen on a phone.
3. **Open the promotion PR,** pre-main → main, and get the owner's approval.
4. **After the merge:** the steps in the 2026-10-23 handoff, "After the merge". In short:
   - the owner switches the Worker's "Disable builds" off;
   - check the first live build;
   - the first `release.yml` run to internal testing;
   - update the website privacy policy and Play's Data safety form.

## The way home along trails (plan step 5)
Dispatch `2026-10-23-09`, with Amendments 1 and 2. The owner's rulings are -1586, -1589, -1590, -1592 and -1595. -1589 and -1592 are the spec.
- **Shipping:** step 5 ships with step 7, the alerts ("Ship with the alerts step", -1589). Nothing visible goes out before then.
- **#388, part 0, the split** (`way-home-split`, 7629d5874): Sorrel finds it merge-ready, with 0 Blocking and 0 M. The file is now 852 lines, which the owner accepted.
- **#389, part 1, the domain code** (`way-home-domain`, ba205a0d1, built on #388): Sorrel found 0 Blocking, 2 M and 9 L (`prompts/preserved/2026-10-24-sorrel-388-389-review.md`, 932fdd0f1).
  - **M-389-1:** the cancel test doesn't pin waiting for the engine to stop. It is a test-only fix of about 8 lines, plus a revert check.
  - **M-389-2:** the 1.5× length check fires falsely when the trail part is short. The walker would be told "This route leaves your downloaded area…", which is false. This needs a ruling. Sorrel's options:
    - (a) leave the joints out of the ratio;
    - (b) a floor in metres;
    - (c) no trail part under M metres.
  - **L1:** FULL_ROUTE can fail beyond 250 m, so SNAP is likely the method, before the S22 timing.
  - **L2:** `NO_APP_KEY` and `FORBIDDEN` mean "need an app update" on the Offline maps row. Should they still read "Trail routes not downloaded"?
  - **L7:** two decisions Hazel made beyond the spec, for the owner to confirm:
    - a track touching a trail only at its entry gets no note;
    - a start outside every area, while the walker is inside one, reads as leaves-area.
  - The other Lows are tidy-ups.
- **Next:** ask the owner about M-389-2, L1, L2 and L7. Tell them Sorrel's view: #389 can either have both M fixes before merging, or merge now with both recorded as open against part 2, since nothing calls the new code yet. The merges of #388 and #389 wait for the owner's word; "Merge when ready" covered "#386 only" (-1592).
- **Still waiting for the phone:** the S22 timing of snap against full route. Include a long walk with no trail (Sorrel's L5).
- **Part 2** (the layers, sundown app-wide, re-routing, the silent swap) comes after part 1 is reviewed and merged. The dispatch for step 7, the alerts, is written next. "Re-center" comes back as a step path before step 7.

## Follow-ups after the promotion (ruled)
- The fast-fling blank patch: every tile that failed reloads when signal returns (-1591).
- The pause line becomes "Paused · continues by itself", for both causes (-1594).
- Sorrel's #386 Lows L-a to L-f (`prompts/preserved/2026-10-24-sorrel-386-review.md`).
- Elder's #386 items 1 and 3: a device step with a shortened give-up, and a screen-reader announcement for the pause.

## Still standing from 2026-10-23
- **R8:** on hold (-1583).
- **The weekly Pi build:** Sunday 2026-10-25 03:24 PDT. Aspen handles it.
- **Later notes:** Sorrel's earlier Lows, WAF and a custom domain, native symbols.
- **Throwaway branches:** delete them only on the owner's word. This includes tonight's: `tile-retry-a3-revert-*`, `tile-retry-a4-*`, `way-home-revert-1..9`, `way-home-split-check-a/-b`.

## Standing rules
The 2026-10-23 handoff's list stands:
- the planner writes no code;
- no build without the owner's go;
- merges and deploys only on the owner's word;
- ask decisions with the question tool, sketches in the text;
- owner screenshots stay off GitHub;
- no schema downgrade on phones;
- "undo" means the last step;
- times in Pacific in chat, UTC in RECORD.
