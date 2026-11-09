# Planner handoff, 2026-10-05 20:35 UTC

**Written by** the planner session `session_01HQ3WaFp8HUfiN2ixfEVCi5`. It started as the -535 coder. The owner then made it planner, as the successor to `session_01Xv2hQteLSg8o3qTPX7Hqnd` ("Dispatch to a coder when ready"), which had gone offline mid-turn at 20:01 UTC.

**Why:** the owner, verbatim: "I'll end the session and start now one. Write a handoff for the next planner".

## Read first: two planners wrote to the records at once

The earlier planner was **not dead**. It reconnected around 20:21 UTC and kept working on `records-after-173` in parallel. As a result, **two RECORD IDs are each used twice.** Neither duplicate was deleted, and none should be (push before you tidy; a record is superseded, never rewritten).

| ID | This session's entry | The other planner's entry | Which to cite |
|---|---|---|---|
| -540 | *observation*, `f087f153`: screenshots and adb read; diagnosis inferred | *finding*, `91bd0936` + `cd78f44c`: the same diagnosis, **confirmed from both phones' `ForagerFix` logs** (GPS `time=` 249 min and 250 min behind the phone clock) | **The finding**: it is the confirmed one |
| -542 | continuation, `0a62d8c1` | continuation, `860d5ade` | Either: same owner words, same outcome (deferred) |

-541 (`faace456`) is this session's only entry, holding the owner's answers to -540. -543 (this handoff's correction entry) records the collision. **The next free ID is -544.** Before writing one, fetch, and check that no other planner window is still open on this branch.

## State at handoff

- **`origin/main`: `e1395fc9`** (PR #171, pi-origin). It includes PR #173 (`1d3d2bbb`, -535) and PR #172 (-510).
- **`records-after-173`:** the planner's records branch, not merged. Holds -537 to -543, plus this file and its index row.
- **Nothing is building.** No coder is working.
- **Open PRs:** #101, #104, #109 (all from September, untouched here).

## Work in flight

1. **-516, sundown alerts** (`sundown-alerts` at `d85b4ae2`, not merged; report `docs/navigation/2026-10-05-sundown-alerts-report.md`).
   - Installed on both phones as 1.0.2763+gbf34fc87.
   - **Today's walk** ran with both phone clocks set 4 h 09 min ahead, on purpose, for the sunset check (the owner: -541 "1 yes").
     - The heads-up fired on both phones at 5:13 PM by the phone clock: "Sunset at 6:42 PM / The walk back is unknown."
     - The walk back was unknown only because the clock made every GPS fix look lost (-540).
     - Whether the leave-by and sunset alerts fired later is **not known**. Both phones were still recording at 20:25 UTC.
     - The owner has set the S26's clock back (-541 "2 it's set back"). The S22's clock was not re-read; both phones were unplugged at 20:32 UTC.
   - **Still open:**
     - the alert notifications seen on a correct clock;
     - the report's release gate: **one 30-minute-plus out-and-back walk**. Today's walk does not count, because the clock was wrong.
   - Then -516 merges, on the owner's word.
2. **Queued behind -516's merge, on the owner's word then:**
   - **-527 fix-provider:** every fix carries its true source.
   - **-532 walk-logger,** with -533's Amendment 1 (both phones log; the S26 holds real data, so no uninstall or wipe there).
   - These two run side by side, then the woods walks with both phones.
3. **Deferred until more walks** (the owner: "I'd like to go on a few more walks before deciding that"):
   - **-538:** the EKF's four open questions.
   - **-540 to -542:** ageing live fixes on the since-boot clock, so that a wrong phone clock does not make GPS look lost.
     - The drawbacks are answered in -542.
     - It would be a small dispatch after -516 merges.
     - The sundown alerts would still follow the phone's clock. Correcting them from GPS time is a separate decision, not recommended without data.
   - Bring both questions back together, with what the walk logs show.

## Done today (for context)

- **-535 no-position-bubble:** merged via PR #173. Its terminal is -536. Report `docs/navigation/2026-10-05-no-position-bubble-report.md`.
  - The walk screenshots confirm no bubble under the dot on either phone.
  - The grey "Last seen" dot is still unconfirmed on a phone.
- **-539:** the S26 takes laptop debug builds as updates. It is sideloaded and signed with `app/debug.keystore`.

## Devices

- **S22 Ultra** `R5CT321008R`: the test phone; no SIM.
- **S26 Ultra** `R5GYC4CYJ3X`: SM-S948U, the owner's daily phone, with real records. Never uninstall, clear data or reinstall it.
- Both on 1.0.2763+gbf34fc87.
- **For the next walks, the owner should keep automatic time on.** A sunset test with a moved clock is a separate walk.

## Slips to carry

- **Model named in co-author lines.** This session's coder commits on `no-position-bubble` (`c7e04dde`, `3ca5b138`, `8ce82e7f`, `895104b5`) name a model in the co-author line, against the project's rule. They are pushed and merged, so they are disclosed, not amended.
- **Screenshots.** The walk screenshots this session pulled are in `/tmp/plan/` on the laptop only. They show street addresses and are not in the repository.
