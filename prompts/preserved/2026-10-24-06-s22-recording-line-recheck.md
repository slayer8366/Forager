# Dispatch 2026-10-24-06: S22 re-check of #391, the recording line clears on Stop

**Helper:** Hazel (the session that ran 2026-10-24-03 to -05). **Planner:** Rowan. **Owner's word:** "Fix it in this promotion (Recommended)", which includes this phone re-check (RECORD -1602). Sorrel: merge-ready, 0 Blocking, 0 M, 3 L (`prompts/preserved/2026-10-24-sorrel-391-review.md`, 8b45e0605).

**Build:** the `app-debug-apk` artifact of run 40086109829 (PR #391, head 3ad490b59, green). Download it into a new empty directory outside every repo. Read its database version against the installed build's (22) before installing; if lower, stop. Install with `adb -s R5CT321008R install -r`; never uninstall. Area 1 stays untouched.

**The step path to confirm, both ways:**
1. Record → walk (test provider, whole-second timestamps, long enough for at least two 15 s polls so the dotted line draws) → Stop **in the app** → the dotted recording line is gone from the map at once, with Tracks on and with Tracks off (Layers). The saved track's own solid line shows as today. A brief moment with neither line, before the saved track loads, is expected (Sorrel).
2. The same with Stop **from the notification**.
3. After each: change tab and come back, and background and reopen. The dotted line stays gone.

Note that the stale line from 2026-10-24-04 is on the map before you start; the first Record clears it either way, so it is not evidence.

**Leave the phone as found:** build 3ad490b59's APK may stay installed (same schema); no test tracks or test waypoints; test providers removed, mock location back to default; location on; Night Maps off; area 1 untouched. Screenshots stay off GitHub; no positions in the repository.

**Deliver:** `prompts/preserved/2026-10-24-06-s22-recording-line-recheck-report.md` on records-after-173 (add only that file; merge, never rebase). Your final message: pass or fail for each step, the installed version string, the two database versions, the report's commit, and that the phone is free.
