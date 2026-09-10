# Correction Patch — `planner-cold-start-handoff-2026-08-25.md`

**Date:** 2026-08-25
**Filed:** 2026-08-27, as a standalone record — see "Filing note" below.
**Reason:** the Standing Rules section carries the draft-storage design that L4b-R and L4b-R2 reversed. The same document lists L4b-R and L4b-R2 as landed, so the file contradicts itself, and the contradiction sits in the section a cold-start planner is told not to erode.

If these two files are not yet in `docs/qc/`, they should be. A cold-start document that lives only in chat uploads cannot be corrected in place, which is how this defect survived.

---

## 1. Replace the Save/Cancel standing rule

**Current text (Standing rules):**

> **Save/Cancel everywhere.** Drafts are persisted (a flag on the entry row), survive process death, and are offered for reinstatement per-entry. Cancel reverts to last saved state; an incidental exit auto-saves.

**Replacement:**

> **Save/Cancel everywhere.** A draft is a **separate row** in `mushroom_log_entries`, never the committed entry itself wearing a flag. A brand-new entry's draft has a null `draftOfEntryId` and commits in place under the same id. A re-edit's draft carries a fresh id and a pointer to its parent; the committed row is not written until Save, and Save repoints the draft's photo references onto the parent in one transaction.
>
> *Why the flag shape was rejected, and it was rejected twice:* it let an interrupted edit overwrite a committed entry in place, and it made a committed entry vanish from the log for as long as it was being edited. Both violate "nothing disappears from a mushroom log entry." A future reader who finds the single flagged row simpler is rediscovering a decided question.
>
> Drafts never appear in the log. They are reached through a Drafts filter on the entry list. Save commits. Cancel discards the draft and reverts to the last saved state, and is the only exit that throws anything away. **An incidental exit persists the draft; it does not commit to the log.** In-app exits (back arrow, tab switch) then offer a dismissible Discard, defaulting to keep-as-draft. Home and backgrounding get no prompt at all, because no window survives to host one, and save silently to Drafts. Crash recovery is offered per entry.

---

## 2. Record Workstream C as superseded

Not "unaccounted for," which is what an earlier draft of this patch said before the repo answered the question.

> **Workstream C is superseded, not missing.** The 2026-08-22 redraw proposed A, B, C (the delete-block flow) and D. A and B landed, and D became L6. C never landed under any name: its region delete-confirmation dialog shipped early inside Workstream B, and the owner's 2026-08-22 move of tile capture from delete-time to location-set-time removed the risk C existed to guard. Recorded in `docs/audits/2026-08-24-workstream-c-and-d-archive.md`.

---

## 3. Add lesson 14

> **14. A reversed decision has to reach the standing rules in the same pass.** L4b's storage shape was decided, reversed by an unlabelled multiple-choice question, implemented in the reversed form, then reworked back over two dispatches. The cold-start handoff was written after that rework and still carried the reversed shape in its Standing Rules section, while listing the corrective work as landed three sections above.
>
> This is lesson 12 (index rows outlive their files) applied to rules rather than to indexes, and it is worse, because a stale index row is visibly stale while a stale rule reads as authority. **When a decision is superseded, the standing-rules entry is updated in the same pass as the code, with the superseded option named and the reason it was rejected attached.** A rule stating only the conclusion gets helpfully re-derived back to the rejected option by the next reader in good faith.

---

## 4. One note on the arrangement, not a patch

The operating guide tells the planner never to name a file path not seen in a pulse result, and never to work from an unverified figure. Both are right. Neither covers the case that produced this defect, which is working from a **handed-over document** whose currency was never established. I made that error one turn before finding this one, treating the 2026-08-22 redraw as a live plan when Workstream B had already landed.

Worth adding to "Failure modes to watch in yourself": *a document someone hands you is a snapshot with a date on it, not a statement of current state. Check its date against what has landed since.*

---

## Filing note (added 2026-08-27, not part of the original patch)

Checked against the repo per the 2026-08-26 repo-state pulse: **neither `planner-cold-start-handoff-2026-08-25.md` nor `planner-operating-guide-2026-08-25.md` exists anywhere in `docs/qc/` or the rest of the tree** — both have only ever existed as chat uploads, confirmed by a full-tree name search on `origin/main`. This patch itself has only ever existed as a chat upload too, until now.

That means this patch cannot be applied "in place" the way it was written to be — there is no filed copy of the cold-start handoff for §1–§3 to replace text *in*. Filing the patch itself, standalone, is what's possible right now: it's a complete, self-contained correction (the replacement rule text, the Workstream C correction, and lesson 14 are each quoted in full above, not diffed against a base this repo doesn't have). If and when the two base documents are supplied, the corrections above should be folded into them directly, and this file's own status updated to say so rather than left implying the correction is still pending once it isn't.

**Current status of the two documents the patch describes: still not filed.** That part of this workstream's housekeeping remains open until they're supplied.

## Update (2026-08-27, same day): both base documents supplied and filed

The owner supplied both documents. They are now filed at `docs/qc/planner-cold-start-handoff-2026-08-25.md` and `docs/qc/planner-operating-guide-2026-08-25.md`, and this patch has been folded into them directly rather than staying a standalone correction:

- **§1** (Save/Cancel standing rule) — applied. The supplied handoff document's Standing Rules section did carry the exact "flag on the entry row" text §1 describes; it's now replaced in place with §1's text, with a short parenthetical noting the source.
- **§2** (Workstream C as superseded, not missing) — **did not apply.** The supplied handoff document does not contain an "unaccounted for" (or any other landing-status) claim about Workstream C anywhere — the only occurrence of "Workstream C" in the document is inside lesson 10's own example, which asserts nothing about its status. Rather than force §2's replacement text into a document that doesn't say what it's meant to correct, the filed handoff document instead carries a note, at the point lesson 10 mentions Workstream C, recording that the underlying fact is real and already correctly documented elsewhere (`docs/plans/README.md`'s PR #26 row; `docs/audits/2026-08-24-workstream-c-and-d-archive.md`). Whatever earlier draft of the handoff document had the line §2 describes, it was not the one supplied here.
- **§3** (lesson 14) — applied verbatim, appended after lesson 13.
- **§4** (the arrangement note) — applied to `planner-operating-guide-2026-08-25.md`'s "Failure modes to watch in yourself" list as a new bullet, since that's the section §4 names.

This file's own status: superseded as the working copy of the correction — the corrections it holds now live in the two documents above — but kept as the historical record of the patch as originally written and why §2 didn't transfer cleanly.
