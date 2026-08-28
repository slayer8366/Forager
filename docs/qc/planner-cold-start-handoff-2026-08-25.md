# Cold-Start Handoff — Forager Planner

**Written for:** a planner session with no memory of any prior conversation.
**Date:** 2026-08-25
**Filed:** 2026-08-27 (existed only as a chat upload before this — see `docs/qc/README.md`'s
"Planner meta-documents" section). Filed with the correction patch's §1 and §3 already applied in
place, and a note on §2 below; §2 covers text that does not appear anywhere in this document as
supplied — see the note after "Costly lessons."

**This is a dated snapshot, not current state — the "Landed"/"Remaining" tables below are as of
2026-08-25 and have not been refreshed at filing time.** Per the correction patch's own §4 (see
`planner-operating-guide-2026-08-25.md`'s "Failure modes to watch in yourself"): a handed-over
document is a snapshot with a date on it, and its currency has to be checked against what has
landed since, not assumed. As of filing (2026-08-27), at minimum: PR #39 has merged to `main`;
L4c has landed via PR #42; Understory (PR #44) has landed in the same merge; PR #26 has been
converted to draft; and the `docs/qc/` index row this document flags as "likely still missing" now
exists (`docs/qc/README.md`). A planner picking this file up cold should treat everything in it as
a starting point to re-verify through a pulse, not as today's ground truth.
**Read with:** `planner-operating-guide-2026-08-25.md` (the method) — this file is the state.

---

## Your role

You are the **planner**. You have no repo access and no editing ability. You investigate through read-only **coder pulses**, you plan, and you write **dispatches**. A coder agent executes and reports. The **owner gates every action**.

The owner does not read code. Translation and audit are yours and the coder's. The owner holds what the app should do and why; you turn that into something a coder can execute without guessing.

---

## The project

**Forager** — a native Kotlin/Compose Android app for mushroom foragers. A journal of finds, a map, offline map regions, and (designed but unbuilt) trip recording.

**Repo layout worth knowing:**
- `docs/plans/` — plan documents, with a `README.md` index
- `docs/audits/` — point-in-time records. **Audits are never edited; a later audit supersedes an earlier one.** Has a `README.md` index.
- `docs/qc/dispatches/reports/` and `docs/qc/pulses/reports/` — the QC archive, normalized 2026-08-25. **Check whether this has an index row; it did not as of this writing, and index drift in this repo has already been systematic once.**

**PR #26** (`claude/plan-implementation-rjzmkr`) is **reference-only, permanently**. Read from it. Never merge, rebase, or push to it. The rework reapplies from it.

---

## Where the work stands

### The arc
This began as a rework of PR #26's offline-maps feature under a seven-workstream plan. That plan's boundaries were wrong (see Lessons), were re-derived, and the scope then grew twice by owner decision — first into a log-and-location rework, then into a photo-ownership inversion.

### Landed

| Piece | What it did |
|---|---|
| **Workstream 0** | Ten foundation files ported from #26 + the datastore dependency |
| **Workstream A** | `MIGRATION_5_6`, `offline_regions` table, `isEntryCapture` flag, log-entry region reference |
| **Workstream B** | The contract migration — `OfflineMapRepository` rewritten atomically with all 20+ consumers |
| **L1** | Photo cleanup on entry delete *(later reversed by G1 — see below)* |
| **L2** | Centre-pin picker replacing long-press at five sites |
| **L3** | `MIGRATION_6_7`, `foundAt` becomes optional |
| **L4a** | Entry-creation routing, Add Location button, old picker deleted |
| **G1** | `MIGRATION_7_8`, photo ownership inverted, both deletion reversals |
| **G2** | Photo gallery screen, unified decoder, reference-aware read path |
| **G3** | Pull-into-entry, gallery deletion with warn-then-remove |
| **L4b / L4b-R / L4b-R2** | `MIGRATION_8_9`, persisted drafts, Save/Cancel replaces autosave |

Database went 5 → 9 across this work. Last reported: **686 tests, 0 failures, 97 classes.**

Branches: work ran on `claude/task-hwj91a`; L4b moved to `claude/l4b-persisted-drafts` (`5986df1`+), which merges back into `task-hwj91a`, which merges to `main`. PR #39 open; PR #40 mentioned as owner-side.

### Remaining

**L5 — Photo location capture.** Imported photos' EXIF coordinates get read and offered; in-app capture strips EXIF and uses the device location ping. Photo capture never blocks on a missing location.

Open at dispatch time: how a coordinate crosses the `PhotoStore` boundary (field on `LogPhoto` vs. new method). Also — the capture-vs-import distinction is currently *erased* in code; both funnel through the same `ContentUriPhotoSource`. Something must preserve it. And `androidx.exifinterface` needs pinning, with a real-artifact check on whether it reads GPS from a `content://` `InputStream` or only a `File`.

**L6 — Tile capture.** Each located entry gets its own ~1m map tile so region deletion never costs an entry its map. Fires when an entry **acquires or changes** a location, not at creation. On change, capture the new tile, confirm, then delete the old.

L6 needs: the bridge between the mushroom-log side and `OfflineMapRepository` (**they have no connection at all today** — and both capture-on-location-set and capture-cleanup-on-delete need that same missing edge, so **one piece must own it** or two half-bridges will merge cleanly and not work); `offlineRegionId` surfaced above the Room layer; a pending-capture signal (a single nullable id **cannot** distinguish "owed" from "not applicable"); a connectivity signal (**none exists**); a background mechanism (**WorkManager is not a dependency**).

**Blocking L6 — the tile budget.** Entry-captures share the same ~6000-tile budget as user regions, with no filter. The pre-flight warning exists only in the user-initiated UI path, so a background capture would never see it. The native SDK ceiling is confirmed non-enforcing. **This needs an owner decision before L6 ships.**

### Known open items
- Index row for `docs/qc/` — likely still missing.
- Dead `onLongPress` plumbing on `MapSlot`/`SightingsMap`: inert, uncalled, deliberately deferred. Removal touches 3 production call sites and 12 test fakes.
- **The payoff gap.** Downloaded region tiles are still not rendered anywhere in the app — live rendering uses four live raster basemaps, and the PMTiles archive is reachable only from the download path. All of L5 and L6 could land and this stays true. Scoped, not planned. Feasible via `FileSource.setResourceTransform`; `MapLibre.setConnected` is too blunt (process-wide).

---

## Owner preferences

**Wants options with a recommendation and the reasoning.** Not a menu, not a decision made for him. Say what you'd choose and why, then let him choose.

**Tappable multiple-choice works well** for decisions. Prose questions get answered less precisely.

**Explain in plain terms.** He doesn't read code. "A flag on the entry row" and "a separate table that could drift out of step" are the right level. Naming the *failure mode* of each option is what he actually decides on.

**Will rework rather than accommodate.** His words: *"We will change as much as necessary, this is for the user, not for me."* And: *"the code is young and it's less costly now than later."* When something is wrong, propose fixing it properly — he has consistently chosen that.

**No users yet.** Development phase. Migrations, interim gaps, and breaking changes are cheap. He has said so explicitly more than once.

**Wants the app to behave the way other apps do.** That reasoning decided the gallery inversion. It is a real design constraint, not a throwaway.

**Judges some things in use.** He accepted a default string with "running it live will give me the real experience on whether it stays." Don't over-deliberate copy.

**Will correct you, directly and usefully.** He wrote an addendum correcting a report's misclassification. Take it straight; check what's actually yours versus the coder's, and say so plainly either way.

---

## Standing rules — do not violate, do not let a dispatch erode

**Nothing disappears from a mushroom log entry indirectly.** Log data is removed only by direct deletion from the log itself. This rule decided: no `@ForeignKey` anywhere; capture moved from delete-time to create-time; the gallery ownership inversion; the delete confirmation's wording. The one sanctioned exception is replacing an entry's tile when the user themselves changes its location.

**Location is set by panning the map under a marker fixed at screen centre**, with OK/Cancel. No long-press. *Accessibility decision with a stated reason:* long-press is unreliable for thicker fingers, and dragging a marker has the same defect — the finger covers the target. **Never let this be re-described as a style preference**, or someone will helpfully make the marker draggable.

**The PMTiles Worker serves offline downloads only.** Never a live tile server. *This is a cost ceiling, not a technical preference* — free tier, and browsing traffic would exceed it. Revisitable when subscriptions cover the cost.

**Photos live in a gallery; entries reference them.** Deleting an entry drops references, not files. The "×" on a thumbnail detaches only.

**Save/Cancel everywhere.** A draft is a **separate row** in `mushroom_log_entries`, never the committed entry itself wearing a flag. A brand-new entry's draft has a null `draftOfEntryId` and commits in place under the same id. A re-edit's draft carries a fresh id and a pointer to its parent; the committed row is not written until Save, and Save repoints the draft's photo references onto the parent in one transaction.

*Why the flag shape was rejected, and it was rejected twice:* it let an interrupted edit overwrite a committed entry in place, and it made a committed entry vanish from the log for as long as it was being edited. Both violate "nothing disappears from a mushroom log entry." A future reader who finds the single flagged row simpler is rediscovering a decided question.

Drafts never appear in the log. They are reached through a Drafts filter on the entry list. Save commits. Cancel discards the draft and reverts to the last saved state, and is the only exit that throws anything away. **An incidental exit persists the draft; it does not commit to the log.** In-app exits (back arrow, tab switch) then offer a dismissible Discard, defaulting to keep-as-draft. Home and backgrounding get no prompt at all, because no window survives to host one, and save silently to Drafts. Crash recovery is offered per entry.

*(Replaces the original "Drafts are persisted (a flag on the entry row)..." text per the 2026-08-25 correction patch §1 — the flag shape had already been reversed by L4b-R/L4b-R2 by the time this document was written, and the original text left the contradiction standing.)*

**No `@ForeignKey` annotations anywhere.** Indexed columns only.

---

## Costly lessons

**1. Plan boundaries by the type graph, not by activity.** The original seven workstreams were cut by verb — reapply, renumber, reconcile, correct, redesign, capture, comply. The code is connected by *what types what*. The dependency graph turned out to be a **star**, with every edge terminating at one file that no workstream owned. Workstream 1 hit it three times and landed at a third of its scope.

**2. A pulse must ask the question the plan needs.** The original scoping pulse asked *what does #26 change* (file-level) when the plan needed *what types against what* (type-level). The answer looked complete and wasn't. This is the root cause of lesson 1 — the author had evidence, just the wrong evidence.

**3. Counts have been wrong five times**, in both directions, including one the coder reported and later corrected unprompted. 13 → 20 → 24 consumers. 4 → 3 call sites. 5 → 7 write sites. 13 files → 21 invocations. **Ask for a recount, never a confirmation** — "confirm it's 20" invites agreement.

**4. Some consumers are invisible to a type-name grep.** Four test files consumed a contract only through a *callback parameter name*. No grep on the three type names could find them; only a whole-module compile did. For an interface change, diff every call site's parameter list against the signature.

**5. Tests that pass while proving nothing.** Three instances: harnesses wiring callbacks to no-ops; Robolectric's `decodeFile` succeeding on missing files *and* arbitrary bytes, making the obvious failure test vacuous; a merge test that couldn't discriminate because `advanceUntilIdle()` drained the race before it mattered. **Mutation checking is the general fix** — break the implementation, confirm the test goes red. Make it standing practice for any subtle assertion.

**6. The legacy-fixture pitfall, three-for-three.** Migration test fixtures reuse *production* entity classes, so a migration altering a shared entity leaks the new shape into the "pre-migration" state. The rule was stated, narrowed once (correctly, to `ADD COLUMN`), then recurred in a mirror-image shape the narrowed rule predicted couldn't happen. **Run the fixtures; don't reason from the rule.** Recorded in `docs/audits/2026-08-24-migration-fixture-entity-reuse-pitfall.md`.

**7. Reporting granularity hides omissions.** A report said "all the doc comments the dispatch named" and four weren't done — twice in a row. The work was fine; the summary sentence concealed the gap. **When a dispatch names a list, require per-item reporting with current file and line.**

**8. An unmet requirement is not a scoping gap.** A report reclassified a requirement it hadn't met as out of scope, in the same report that met it everywhere else, offering low reachability as mitigation. That's an argument about *how often* the gap is hit, not whether the requirement was met. Owner ruling: **if something cannot be met, say it cannot be met and why, and stop.**

**9. Never name a file path you haven't seen in a pulse result.** The planner did this twice — a nonexistent handoff filename, and a "preserve this section" instruction for a section that didn't exist. If you need a file touched and can't cite where you saw it, that's a pulse question.

**10. Verification instructions can be too narrow.** A gate asked for a grep on `Workstream C`/`Workstream D`; the coder swept bare tokens instead and found three real dangling pointers the literal check would have passed. Similarly, a "grep for long-press handlers" gate would have shipped three UI strings telling users to long-press. **User-facing copy is part of the surface a change touches.**

**11. Dispatch, then build.** One piece was implemented before its dispatch existed, so the dispatch became a review checklist rather than a specification. It worked out, but a checklist only catches what it happens to ask about.

**12. Index rows outlive their files.** Five stale rows across two indexes, mechanism identified: nothing prompts an index update when a plan's status changes. **When a PR merges or a phase lands, the index row updates in the same commit.**

**13. Load-bearing numbers deserve a second count.** The planner passed a "15 files" figure from a pulse into a proposal as established fact; the coder recounted to 16/13 unprompted. The pulse discipline stops you working from *memory* — it does not stop you working from an *unverified pulse figure*.

**14. A reversed decision has to reach the standing rules in the same pass.** L4b's storage shape was decided, reversed by an unlabelled multiple-choice question, implemented in the reversed form, then reworked back over two dispatches. The cold-start handoff was written after that rework and still carried the reversed shape in its Standing Rules section, while listing the corrective work as landed three sections above.

This is lesson 12 (index rows outlive their files) applied to rules rather than to indexes, and it is worse, because a stale index row is visibly stale while a stale rule reads as authority. **When a decision is superseded, the standing-rules entry is updated in the same pass as the code, with the superseded option named and the reason it was rejected attached.** A rule stating only the conclusion gets helpfully re-derived back to the rejected option by the next reader in good faith.

*(Added per the 2026-08-25 correction patch §3.)*

**Note on the correction patch's §2 (added at filing, 2026-08-27):** the patch's §2 says to record "Workstream C is superseded, not missing" in place of an "unaccounted for" claim. No such claim exists anywhere in this document as supplied — "Workstream C" appears only once above, inside lesson 10's own example, which does not assert a landing status for it. Rather than force in text correcting something this copy doesn't say, this note records that the fact itself is real and already correctly recorded: `docs/plans/README.md`'s PR #26 row states "Workstream C never landed under any name," and `docs/audits/2026-08-24-workstream-c-and-d-archive.md` is the full record. If an earlier draft of this handoff document did carry the "unaccounted for" line the patch describes, that draft was never the one supplied here.

---

## Immediate next steps

1. **The tile budget decision** — owner. Blocks L6.
2. **L5** — scoping pulse first. The capture-vs-import erasure and the `PhotoStore` boundary are both unresolved.
3. **L6** — after L5. One piece must own the bridge.
4. **Housekeeping** — `docs/qc/` index row; `onLongPress` removal when something touches that surface anyway.
5. **The payoff gap** — the largest open question. Everything can land and the offline feature still shows the user nothing. Worth raising with the owner as a scope decision, not folding into a workstream.
