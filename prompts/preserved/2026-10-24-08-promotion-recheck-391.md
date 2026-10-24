# Promotion re-check 2026-10-24 (3): #391, pre-main 073fc77bd to 79a01ae1b (UX auditor)

**Auditor:** Elder, the UX auditor. A fresh session on Claude Fable, a different model from the coders (-1134). **Planner:** Rowan. **Owner's word:** "Merge it (Recommended)", whose option text was "Then Elder's short look and the promotion PR" (RECORD -1606).

**Why:** your re-check at 073fc77bd passed (`prompts/preserved/2026-10-24-02-promotion-recheck-388-report.md`). Since then, #391 has merged (79a01ae1b) and joins this promotion. It does two things:
- the dotted recording line clears on Stop. Before, it stayed until the next Record. This is on main too, so it is a fix to live behaviour (-1602).
- the saved track's solid line shows on the map right after Stop. Before, it waited for a tab change (-1604, -1605).

**Read:** the dispatches `2026-10-24-05` and `2026-10-24-07` (with Amendment 1); the reports `2026-10-24-04`, `-06` and `-07`; and Sorrel's `2026-10-24-sorrel-391-review.md` and `-review-2.md`. All are in `prompts/preserved/` on records-after-173.

**Scope:**
- `git diff 073fc77bd 79a01ae1b`;
- what the user sees after Stop, as a step path, against "don't make me think";
- does anything else on the Maps tab change when a track ends?
- does the known-bugs list need a row? The deleted track that stays on the map until a tab change is open and inferred (-1605), not seen on a phone as such.
- CI on 79a01ae1b, from the API. If it hasn't finished, wait for it rather than cite the PR run.
- same rules: `docs/process/qa-philosophy.md` section 3; "Advises, you decide."

**The S22 evidence:** 1.0.3904 (-06): the dotted line clears on both Stop paths. 1.0.3906 (07 addendum): the solid line appears 1.2 to 1.7 s after both Stop paths, with no tab change. Mark anything not seen on a phone as "not seen on a phone".

**Deliver:** your report at `prompts/preserved/2026-10-24-08-promotion-recheck-391-report.md` on records-after-173. Fetch, add only that file, push; if the push races, merge, never rebase. Put scratch files under ~/.cache, not /tmp. Your final message gives the verdict (pass, pass with items or fail), the items, and your model. Change no code, merge nothing, deploy nothing, and don't use the phones.
