# Dispatch 2026-10-24-04: does the recording line clear after Stop and delete? (S22, pre-main 073fc77bd)

**Helper:** Hazel (the session that ran 2026-10-24-03). **Planner:** Rowan. **Owner's word:** "Quick check first (Recommended)" (RECORD -1601).

**Why:** in the 2026-10-24-03 check, step 5, the recording line stayed on the map after the test tracks were stopped and deleted; whether it clears was not seen.

**Steps (S22 R5CT321008R, build 1.0.3901+g073fc77b, already installed):**
1. Record a short track with an adb test location provider (whole-second timestamps). Stop in the app. Watch the map for 30 s: does the line stay?
2. Leave the Maps tab and come back. Background the app and bring it back. Does the line stay?
3. Delete the track from Records, then repeat 1 and 2's watching.
4. Repeat once with Stop from the notification instead of the app.
5. Read the code that decides whether the map draws the recording line, and name the condition (file:line) that should clear it.

Say which of these the line survives, and whether that is a bug by the code's own intent. Don't fix anything. Leave the phone as found: no test tracks, test providers removed, location on, area 1 untouched.

**Deliver:** `prompts/preserved/2026-10-24-04-stale-recording-line-report.md` on records-after-173 (add only that file; merge, never rebase). Your final message gives the answer in one line, then the details. Screenshots stay off GitHub; no positions in the repository.
