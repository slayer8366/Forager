# The Diagnostics panel reads only the current log generation, so a rotation silently hides the previous one

**Date:** 2026-09-15 · **Found during:** the capture-orientation diagnostics dispatch (PR #102) ·
**Status:** recorded, **not fixed** — outside that dispatch, and its own decision

**One-paragraph outcome.** `DiagnosticsLog` caps itself at 1 MB by renaming the current file to
`<name>.1` and starting a new one, deleting whatever `.1` held before. The previous generation
therefore survives on disk. **The panel never reads it.** `DiagnosticsPanel` reads `log.read()`, sizes
it with `log.sizeBytes()` and shares `log.file`
(`app/src/debug/.../ui/diagnostics/DiagnosticsPanel.kt:160,226,271`); `rotatedFile` appears nowhere in
that file. So the moment a rotation happens, every entry written before it disappears from the panel
and from the shared file, while still existing on the device — and nothing in the panel says a
rotation occurred. **This belongs to no writer in particular**: the sweep line, the process-start
line, StrictMode violations and the new capture entries are all equally affected, and it has been true
since the panel was written.

## What is read, and what is not

- **Written and rotated:** `DiagnosticsLog.append` rotates when `file.length() > maxBytes`
  (`DiagnosticsLog.kt:63`), `maxBytes` = `1L shl 20` (`:106`). `rotate()` deletes the old `.1`, renames
  the current file onto it, and logs a warning **only if the rename fails** (`:83-90`). A successful
  rotation is silent by design — the class doc explains the rename was chosen over a head-truncate
  because it is atomic and does not rewrite the file on every append past the cap. That reasoning is
  sound; the gap is on the reading side, not the writing side.
- **Read:** `rotatedFile` is a public `val` on `DiagnosticsLog` (`:53`) with no reader in the panel.

## Why it matters, and why it is easy to misdiagnose

A reader who opens the panel and finds no `process started` line, or no sweep entry, has no way to
distinguish three different situations: the entry was never written; the entry was written and
rotated out; or the log was cleared. The first is a bug in whatever should have written it. The second
is the cap working as designed. **They look identical in the panel.**

That is the shape CLAUDE.md names repeatedly — a reading decoupled from the thing it describes by a
step in between, with nothing in the output saying so. It is recorded here so the next person does not
diagnose it from scratch from a panel with a missing early entry.

## What the capture-orientation dispatch does and does not change

It does not create this. It makes it **reachable sooner**, by adding two entries per capture (~245
bytes) to a file whose budget is otherwise dominated by StrictMode stacks (~3.5 KB each, roughly 300
to a generation). A twenty-shot session writes about 4.9 KB, under half a percent of one generation —
so the change to how soon a rotation arrives is small, and the consequence when one arrives is
unchanged. The device check for that dispatch carries a line saying an absent early entry may be
rotated rather than missing, which is the mitigation available without changing any code.

## Not fixed, and the options if it is taken up

Recorded rather than fixed: it is outside the dispatch that found it, and what the panel should do is
a decision rather than an implementation detail. The options, with what each costs:

1. **Show the rotated generation too** — the panel reads both and marks the boundary. Most useful to a
   reader, most work, and doubles what a long log renders on a phone.
2. **Say that a rotation happened** — a line in the panel when `rotatedFile` exists, naming its size
   and modified time. Cheap, and turns the three indistinguishable situations above into two.
3. **Share both files** — the share action already exists for `log.file`; extending it to the previous
   generation costs little and helps whoever receives it rather than whoever is holding the phone.
4. **Nothing, deliberately** — with this document as the record, so the next reader spends one search
   rather than an investigation.

No recommendation is offered here beyond noting that option 2 is the one that removes the
misdiagnosis, which is the actual harm, rather than the data loss, which is by design.
