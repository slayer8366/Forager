# Planner handoff, 2026-10-24 (2) (written 00:05Z 2026-10-25, 5:05 PM PDT Saturday 2026-10-24)

Written by the outgoing planner (Rowan Waymark) at the owner's word. Earlier the owner said: "Keep an eye on the context window. Once it reaches 98% write a handoff and have everyone commit their work." Then, asked here: "Handoff now (Recommended)". This replaces `2026-10-24-planner-handoff.md` as the starting point.

**Next free RECORD ID: -1620.** This handoff is -1619. The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`.

## Read first
- `~/Zynergy/Forager/CLAUDE.md`, then the memory index.
- RECORD -1597 to -1619. They are short.
- `prompts/preserved/2026-10-24-10.md`: the way-home part 2 dispatch and its Amendments 1 to 3. This is the live spec.
- The 2026-10-23 handoff, for the keys and the release steps. They still hold, except where this file updates them.

## Check before acting
- **Who is still running?** At handoff nothing was running. A session usage limit stopped every helper at about 14:30 PDT, with a reset at 17:00. The planner checked every helper worktree: all committed and pushed, none dirty.
  - The worktrees are `way-home-cap`, `way-home-domain2` (branch way-home-part2-domain), `way-home-visible` (way-home-part2-visible), `way-home-timing`, `way-home-part2-report`, `sorrel-395`, `sorrel-396` and `dev-rebased-177215135`.
- **Has anything landed since?** Run ListAgents. Then run `git log` on origin/records-after-173, pre-main, and the three way-home part 2 branches.
- **Who carries on?** Ask the owner before dispatching.

## Branches
- **main:** 177215135. This is promotion #393, merged 2026-10-24 (-1608). The Worker now builds from main by itself: live version a5c79d5c, rollback 1a85a35e, checked live (-1609).
- **pre-main:** c83a06722. This is #389 (way home part 1) merged. CI green.
- **dev:** 82b55f06a, unchanged. See "Parked" below.
- **No promotion until step 7 lands.** The owner: "We won't promote until step 7 lands" (-1612).

## Way home part 2 (live work)
Three PRs, each built on the one before. Merge order: A, then B, then C. Each merge waits for the owner's word.

| PR | Branch | Head | Green run | Review |
|---|---|---|---|---|
| A #394, the 150 m cap | way-home-cap → pre-main | aff033375 | 40106889588 | Sorrel: merge-ready (`prompts/preserved/2026-10-24-sorrel-394-review.md`) |
| B #395, the domain logic | way-home-part2-domain → way-home-cap | 77963f8a4 | 40130538002 | First review: not merge-ready, B-395-1 Blocking (fixed since). **The re-check was cut off by the limit.** |
| C #396, what the walker sees | way-home-part2-visible → way-home-part2-domain | 44e28ba9b | 40130545975 | First review: merge-ready apart from M-396-1 (fixed since). **The re-check was cut off by the limit.** |

The planner verified each head and green run from the API. Hazel's evidence (fail-first runs and revert checks b1-b10, c1-c7) is in the comments on #395 and #396.

**The rulings in force:**
- **-1613:** the part 2 proposals.
- **-1617:** the review fixes.
- **-1618:** the distance home counts from the earliest point within 50 m anywhere on the line, plus 25 m, so it is never short for any accepted fix. It reads about 50 to 75 m long on ordinary trails.
- **-1617 (M-396-1):** the HUD falls back to the track once re-routing ends.
- **Hazel's own call:** the track also shows while a re-route is being worked out. The C re-check is asked to confirm it.

**Next, in order:**
1. **Resume the two Sorrel re-checks.** They are dispatched in messages, so re-send them as new Fable agents:
   - #395 at 77963f8a4: diff bedb6d809..77963f8a4. Try to construct a short case for any fix the 50 m gate admits; any short case is Blocking. Also check M-395-2, L-395-4 and L-395-9. The runs: fail-first 40128862012 and 40130073406; reverts b8 40129453676, b9 40129460933 and b10 40130536365. Write `prompts/preserved/2026-10-24-sorrel-395-recheck.md`.
   - #396 at 44e28ba9b: diff 18e038a9c..44e28ba9b, leaving out B's part. Check M-396-1 including Hazel's "track while re-routing" call, M-396-2 and L-396-6. The runs: fail-first 40128980207; reverts c6 40129468315 and c7 40129472920. Write `prompts/preserved/2026-10-24-sorrel-396-recheck.md`, with a list for the S22 device check.
2. **The S22 device check and timing run.**
   - **The phone isn't visible to the laptop.** adb sees no device and lsusb shows no Samsung, since about 13:00 PDT. Ask the owner to plug it in and accept the debugging prompt.
   - **The timing build:** `way-home-timing` at 70d0cd35f is cut from an old C head. Merge C's latest into it first. It is never to be merged itself.
   - **The plan:** Hazel's part 2 report sections 6 and 8, plus Sorrel's #396 device list:
     - the dotted layer day and night;
     - the swap and a re-route, frame by frame;
     - the off-line fallback;
     - screen-off sundown;
     - the expected false off-track alert.
   - **Phone rules:** check the database version first (22). Area 1 is the owner's. Use whole-second test fixes. Something keeps removing the test location providers (three times today, cause unknown); re-add them and log each time.
3. **The merges,** A, B, C, on the owner's word.
4. **The owner tests part 2 on a walk** on the S26. They wanted to today, but it wasn't ready (-1616). Before that walk:
   - re-download their area on the new build, so it carries trail routes;
   - expect the false off-track alert when following a trail away from the outbound track. Step 7 fixes it.
5. **Step 7:** the off-track and wrong-turn alerts. "Re-center" comes back to the owner as a step path first. Notes for step 7:
   - Sorrel's L-394-1: the note on a refused route is discontinuous at 100 m walked.
   - Hazel's part 2 report section 7: the false off-track alert, `ReturnWatch.kt:187`, `:255`, `OffTrackJudge.kt:8`.

## Parked or on hold
- **The release steps:** the first `release.yml` run, the website privacy policy and Play's Data safety form are on hold (-1612, "The release steps").
- **dev rebase:** `dev-rebased-177215135` at 7517c146c is correct, but CI went red. Three pixel-reading map tests ran out of memory (run 40104529178). Parked until after part 2 (-1615); then one re-run, and a diagnosis if it repeats. dev isn't moved until it is green and has had the S22 launch check.
- **Follow-ups ruled for later:**
  - known bugs rows 18, 19 and 20 (the pause line, the fast-fling patch, a deleted track staying on the map);
  - Elder's #386 items 1 and 3;
  - Sorrel's #386 Lows;
  - Elder's #391 item 2 (a failed end write leaves no notice);
  - Sorrel's #391 L4 (AvailabilityViewModel.kt 761 lines: split before the next feature);
  - Sorrel's #395 and #396 remaining Lows (in their reviews).
- **Still standing from the earlier handoffs:** R8 on hold (-1583); the Pi build Sunday 03:24 PDT (Aspen); WAF and a custom domain; native symbols; older Sorrel Lows; throwaway branches deleted only on the owner's word (today's adds `way-home-a3-*`, `way-home-a4-*`, `recording-line-clears-revert-*`, `way-home-timing`, and B and C's revert branches).

## People
Hazel (app coder, Opus); Sorrel (reviewer, Fable); Elder (UX auditor, Fable); Willow (Pi coder) and Aspen (Pi planner), not directed by this planner.

## Standing rules
The 2026-10-23 handoff's list stands:
- the planner writes no code;
- no build without the owner's go;
- merges and deploys only on the owner's word;
- ask decisions with the question tool, sketches in the text, step paths for behaviour;
- owner screenshots stay off GitHub;
- no schema downgrade on phones;
- times in Pacific in chat, UTC in RECORD.

Also: scratch files go under ~/.cache, not /tmp, which filled once today. Session usage limits stopped helpers twice today. On a stop, resume the helper with SendMessage after the reset rather than starting it fresh.
