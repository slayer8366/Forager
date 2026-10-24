# Promotion re-check 2026-10-24: the #386 range, pre-main adcbedb19 to fefc89129 (UX auditor)

**Auditor:** Elder, the UX auditor. A fresh session on Claude Fable, a different model from the coders (-1134). **Planner:** Rowan. **Owner's word:** "Yes, carry on (Recommended)", after being asked whether to carry on with the promotion with Elder re-checking #386 (RECORD -1595).

**Why:** your audit of 32d381e45 (`prompts/preserved/2026-10-23-05-promotion-audit-report.md`) passed with items. Since then, #387 fixed items 1 to 3 (-1585), and #386 joined the promotion (-1580), adding a visible line. This re-check covers only what changed after your audit.

**Scope:**
- `git diff adcbedb19 fefc89129`, which is PR #386, the tile retry.
- Confirm items 1 to 3 of your report are closed at fefc89129 (#387, merged at adcbedb19).
- Same rules: `docs/process/qa-philosophy.md` section 3; "Advises, you decide."

**What #386 ships** (dispatch `prompts/preserved/2026-10-23-06.md`, Amendments 1 to 4):
- A failed map tile is held and resent when signal returns. At most 8 at once, held up to 5 minutes.
- An offline-area download pauses at no signal and continues by itself. After 5 minutes with no progress, it shows the usual error.
- While a download is paused, its row reads "Paused · no signal · continues by itself" (Amendment 4, the owner's choice).

**Already known; don't re-find these:**
- **The fast-fling blank patch:** a follow-up after the promotion (-1591).
- **The pause line on a server error with signal up:** the owner chose "Paused · continues by itself" as a fix after the promotion (-1594).
- **Sorrel's review:** `prompts/preserved/2026-10-24-sorrel-386-review.md`, six Lows.

**The S22 evidence:**
- Build 1.0.3894: the download pause passes, nothing tapped (-1591).
- Build 1.0.3897: the pause line is shown, then the count comes back (-1593).

**Mark anything not seen on a phone as "not seen on a phone".**

**Pay attention to:**
- "don't make me think";
- anything a user could misread as broken;
- whether the known-bugs list needs a row for this range.

**Deliver:**
- your report at `prompts/preserved/2026-10-24-01-promotion-recheck-report.md` on branch records-after-173. Fetch, add only that file, push. If the push races, merge, never rebase;
- the verdict (**pass**, **pass with items** or **fail**), the items, and your model, in your final message.

Change no code, merge nothing, deploy nothing, and don't use the phones.
