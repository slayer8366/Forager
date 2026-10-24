# Promotion re-check 2026-10-24 (2): #388 and the known-bugs rows, pre-main fefc89129 to 073fc77bd (UX auditor)

**Auditor:** Elder, the UX auditor. A fresh session on Claude Fable, a different model from the coders (-1134). **Planner:** Rowan. **Owner's word:** "Short Elder re-check (Recommended)" (RECORD -1598).

**Why:** your re-check of the #386 range (`prompts/preserved/2026-10-24-01-promotion-recheck-report.md`) passed at fefc89129. Since then, two things landed on pre-main:
- #388 (merge e26c0f591), way home part 0. It splits `ui/track/TrackRecordingViewModel.kt` before adding to it, with no behaviour change meant. The file is now 852 lines, which the owner accepted (-1592). Sorrel: merge-ready, 0 Blocking, 0 M (`prompts/preserved/2026-10-24-sorrel-388-389-review.md`).
- #390 (merge 073fc77bd): known-bugs rows 18 and 19, your item 2 (-1597).

The promotion now includes both.

**Scope:**
- `git diff fefc89129 073fc77bd`;
- does anything a user can see or rely on change? Note anything a split could move without saying so: init order, what survives a configuration change, logging, a recording's lifecycle;
- do rows 18 and 19 say what your item 2 asked for?
- same rules: `docs/process/qa-philosophy.md` section 3; "Advises, you decide."

**CI:** read the run on 073fc77bd, or the newest run that has finished, from the API. If none has run on a commit that includes #388, say so.

**Mark anything not seen on a phone as "not seen on a phone".**

**Deliver:**
- your report at `prompts/preserved/2026-10-24-02-promotion-recheck-388-report.md` on branch records-after-173. Fetch, add only that file, push. If the push races, merge, never rebase;
- the verdict (**pass**, **pass with items** or **fail**), the items, and your model, in your final message.

Change no code, merge nothing, deploy nothing, and don't use the phones.
