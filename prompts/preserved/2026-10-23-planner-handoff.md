# Planner handoff, 2026-10-23 (written 02:53Z 2026-10-24, 7:53 PM PDT Friday 2026-10-23)

Written by the outgoing planner (Rowan Waymark) at the owner's word: "Write a handoff for another session also". It replaces `2026-10-19-planner-handoff.md` as the start point.

**Next free RECORD ID: -1588.** This handoff is -1587. The records worktree is `~/.cache/forager-wt/records`, on branch `records-after-173`. Forager's IDs stay below 2000.

## Read first
- `~/Zynergy/Forager/CLAUDE.md`
- the memory index. New this week: "record-entries-short", "worker-builds-disabled-until-promotion", "aspen-pi-planner-in-cartography", "no-schema-downgrade-on-phones", "pr-numbering-offset".
- RECORD.md entries -1554 to -1587. Keep entries short: the decision, the owner's words, and the few facts behind it.

## Check before acting
The outgoing session may still be alive, with three helpers running in it. Their reports go to that session, not to you.

So start with ListAgents, then `git log` on `origin/records-after-173`, `origin/pre-main`, `tile-retry` and `way-home-report`, and look for commits newer than this file. Two planners collided once (2026-10-20, duplicate -1421). Ask the owner which session carries on before you dispatch anything.

## Where things stand

**Branches.**
- **main:** 381515c61.
- **pre-main:** adcbedb19, which is #387 merged into 32d381e45.

**The promotion of pre-main to main is under way** ("Start the promotion", -1578).
- **Elder's audit** (`preserved/2026-10-23-05-promotion-audit-report.md`): pass with items. Items 1–3 were fixed in #387, merged (-1585).
- **Privacy policy:** the owner approved the corrected text (-1584) and checked that no Logpush job exists.
- **The tile retry (#386) joins this promotion** (-1580).

**Steps left in the promotion:**
1. **#386:** Hazel builds dispatch 06 Amendment 3 (downloads pause instead of failing, and get their own lane; -1585), then the S22 download re-check. Sorrel reviews, and the owner gives the merge word.
2. **CI** on the new pre-main head. Elder re-checks, if needed. A phone check (offline row, day and night glance).
3. **Back up main** to `~/Zynergy/forager-repo-backups/` (a verified bundle), and read the diff for `@Ignore` or removed assertions.
4. **The promotion PR**, pre-main → main, and the owner's approval.
5. **After the merge:**
   - the owner switches the Worker's "Disable builds" off;
   - check the first main build live: `us.json` south bound -85.05, 8/75/124 from r2-world-south, routing 403 without the key;
   - the first `release.yml` run (the owner approves in GitHub) to internal testing as a draft, including one route download check;
   - update the website copy of the privacy policy and Play's Data safety form (Hazel and Sorrel flagged both).

**Helpers running at handoff** (their reports go to the outgoing session):
- **Hazel, tile retry:** dispatch `2026-10-23-06` Amendment 3, branch `tile-retry`, PR #386. Hazel holds the S22 until handing it back.
  - S22 steps 2 and 3 passed. Step 4 (download) failed, as predicted, and is the "before".
- **Hazel, way home report:** dispatch `2026-10-23-09`, branch `way-home-report`, report only (-1586).

**Decided and waiting:**
- **R8 (code shrinking):** on before the next production release (-1581). The report is on branch `r8-report` (c9d15825).
  - The build is on hold (-1583); `shrinkResources` stays out.
  - Revisit Hazel's three extra phone steps when the build starts (the owner's note in `2026-10-23-08.md`).
- **Navigation, after the way home:** waypoint along trails (plan step 6), then the off-trail and wrong-turn alerts (step 7). Owner rulings: -1498, -1546, -1586.
  - The renaming of "Return to Route" to "Re-center" comes back as a step path before step 7.
- **Sunday 2026-10-25 03:24 PDT:** the weekly Pi build (pi-30 Seaside forest-over-rock fix). Then Aspen asks the owner for the go to upload `us.pmtiles`.
  - Aspen is the Pi and map planner (memory: aspen-pi-planner-in-cartography).

**Later notes:**
- Sorrel's Lows: L4/L7 of #380, B1–B4 of #383, A2.
- WAF and a custom domain (-1570).
- Native symbols (-922).
- The remote throwaway branches (`revert-0723-r1..3`, `tile-retry-revert-1..3`, older revert and scratch branches) are deleted only on the owner's word.

## Keys and deploys (names only, never values)
- **The routing app key:** `~/.config/forager/routing-app-key` (mode 600), the GitHub secret `FORAGER_ROUTING_APP_KEY`, and the `release` environment.
- **Release signing:** the `release` environment secrets (`FORAGER_UPLOAD_KEYSTORE_B64`, `FORAGER_SIGNING_*`, `PLAY_SERVICE_ACCOUNT_JSON`).
- **The Worker:** live version 1a85a35e, rollback 03db9cb1. Deploy with wrangler, only on the owner's word. The steps are in memory: worker-builds-disabled-until-promotion.

## Standing rules this week
- The planner writes no code.
- No build without the owner's explicit go.
- Merges and deploys only on the owner's word.
- Ask decisions with the question tool, sketches in the text, step paths for behaviour.
- Owner screenshots stay off GitHub.
- No schema downgrade on phones.
- "Undo" means the last step.
- Times in Pacific in chat, UTC in RECORD.
