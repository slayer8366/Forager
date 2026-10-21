# RECORD-cartography.md

The cartography team's record of dispatched work, decisions and what happened. The team is Aspen
Meridian (planner), Willow Quadrat, Primrose Lookout, Larch Traverse, Holly Benchmark and Sage
Wayfinder. It is kept in the same style as Forager's `RECORD.md`, at the owner's word: "Same style as
forager. You start your own records." (2026-10-21, entry 2026-10-21-1).

**Entries.** Each piece of work opens with an intent entry and closes with exactly one closing entry.
The entry types are Forager's twelve: intent, terminal, continuation, decision, outcome, observation,
finding, device-note, correction, merge, dispatch-note and handoff. Every entry carries its Kind, its
ID and a Timestamp read from the clock. It ends by saying who wrote it and on which branch. A
decision quotes the owner verbatim and is recorded once.

**IDs.** IDs are `2026-10-21-N`: this store's start date, then a number counting up from 1. Outside
this file they are cited as "cartography -N", never as a bare "-N". A bare number means Forager's
record, and the Pi's earlier block inside it collided with that record (entry 2026-10-21-1).

**Append-only.** Nothing already committed is edited or removed. A correction is a new entry that
points at the one it corrects. The audit files in `docs/audits/` stay where they are and are not
migrated; `docs/audits/README.md` indexes them.

**Who writes it.** Aspen writes every entry ("that's your job to write records", 2026-10-21). Willow
commits and pushes it ("Go ahead and use coders to commit.", `e146ceade`). It lives on `pi-build`.
What makes an entry well formed is E-GD's RECORDING.md, checked by reading.

## Entries

---

**Kind:** decision
**ID:** 2026-10-21-1
**Timestamp:** 2026-10-21T22:08:28Z
**Title:** The cartography team keeps its own record, in Forager's style (owner)
**Observed:**
- The Pi planner was given `2026-09-28-600` to `-699` in Forager's `RECORD.md`
  (`records-pi-after-166`: "the Pi planner uses the block 2026-09-28-600 to -699").
- Its own entries stop at `-604` (`998917a8`, 2026-10-04).
- The main stream reused the block and runs on to `-1520` (`records-after-173` at `2605b9d6`), so
  `-603` names two different entries.
- Recorded in `docs/audits/2026-10-21-cartography-team-profiles.md`, "Found: the Pi's record IDs
  collide with the main stream".

**Decision:** the owner was asked how Pi records are numbered from now on. The owner, verbatim: "Same
style as forager. You start your own records."

**What Aspen decided under it** (technical; named to the owner in the next message):
- **The file:** `RECORD-cartography.md` at the root of `pi-build`. That branch already carries
  Forager's `RECORD.md`, so a separate name keeps the two stores apart and off each other's merge
  path (E-GD PROTOCOL 13).
- **The IDs:** `2026-10-21-N`. They cannot collide with Forager's `2026-09-22-NN` or `2026-09-28-N`.
- **Nothing is migrated.** The Pi's entries `2026-09-28-600` to `-604` and the Pi audit files stay
  where they are.

**Notes:** Written by the Pi planner, Aspen Meridian. Committed by Willow Quadrat on `pi-build`
(dispatch pi-27). Next free ID -2.

---

**Kind:** decision
**ID:** 2026-10-21-2
**Timestamp:** 2026-10-21T22:08:28Z
**Title:** Models for the cartography team: Fable for Primrose, Larch and Holly; Opus for Willow (owner)
**Observed:**
- Fable for Larch and Holly was ruled earlier the same day: "I'll take your recommendation".
- Asked next for Primrose and Willow, the owner answered "Fable".
- Willow on Fable would put Larch and Holly, which check Willow's work, on the coder's model. That is
  against RECORD -1134 in Forager's record ("It's useful because the same model shares blind
  spots."). Aspen asked before recording.

**Decision:** the owner chose "Willow on Opus (Recommended)" through the question tool. The option
read: "Primrose on Fable as you said; Willow stays on Opus as it ran pi-25 and pi-26. Larch and Holly
stay on Fable, so the checks run on a different model from the work they check."
- **Fable:** Primrose Lookout, Larch Traverse and Holly Benchmark.
- **Opus:** Willow Quadrat.
- **The session's own model:** Aspen, unchanged.
- **Fable:** Sage, per Forager's team table.

**Notes:** Written by the Pi planner, Aspen Meridian. Committed by Willow Quadrat on `pi-build`
(dispatch pi-27). Next free ID -3.

---

**Kind:** decision
**ID:** 2026-10-21-3
**Timestamp:** 2026-10-21T22:08:28Z
**Title:** Prep, ask, go: the owner's rule for Pi coders of 2026-10-04, entered in a record store for the first time
**Observed:**
- The rule was given on 2026-10-04 and has governed every Pi dispatch since. Until 2026-10-21 it
  lived only in the Pi planner's memory and in dispatch texts.
- A search of every remote branch's `RECORD.md` found no copy.
- It was first written into the repository in `docs/audits/2026-10-21-cartography-team-profiles.md`
  (`3039a658`).

**Decision:** the owner, 2026-10-04, verbatim: "Have the coder do the prep work and then have them
ask again before doing any non-read work, such as commits. Then I give my explicit go to send them
off to work. Not at any point do they start building until I say so."
- **Later rulings that stand beside it:**
  - "Unless it's mid run, then a simple pause and ask will work as that acts as my explicit go"
    (same day);
  - "Your relay counts (Recommended)" (2026-10-20). This supersedes the direct-go rule of Forager's
    Pi entry `2026-09-28-603` on `records-pi-after-166`.
- **The quotes' source:** the Pi planner's memory, as written on 2026-10-04 and 2026-10-20. It is not
  a transcript. These are the owner's words as the planner recorded them that day.

**Notes:** Written by the Pi planner, Aspen Meridian, 17 days after the decision. The entry is late,
and that is said rather than backdated. Committed by Willow Quadrat on `pi-build` (dispatch pi-27).
Next free ID -4.

---

**Kind:** correction
**ID:** 2026-10-21-4
**Timestamp:** 2026-10-21T22:09:49Z
**Corrects:** 2026-10-21-2
**Title:** Primrose Lookout runs on Sonnet, not Fable (owner)
**Observed:** cartography -2 put Primrose on Fable, from the owner's "Fable". Primrose judges nothing,
so its model does not bear on RECORD -1134.
**Decision:** the owner, verbatim: "The watcher can use  Sonnet" (quoted as typed, with the double
space).
- **Primrose Lookout:** Sonnet.
- **The other models in cartography -2 stand:** Larch and Holly on Fable, Willow on Opus.
**Notes:** Written by the Pi planner, Aspen Meridian. Committed by Willow Quadrat on `pi-build`
(dispatch pi-28). Next free ID -5.
