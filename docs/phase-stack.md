# Phase stack

A running record of what work is nested inside what. Text, indented,
maintained in-place by whoever is working.

## Why

Correct descents compound. "Verify before fixing," "instrument before
diagnosing," and "pulse before dispatching" each say to go one layer
deeper first, so a session can end up many layers below the thing it
set out to do without any single step being wrong. Depth is invisible
one step at a time and obvious on a page.

## Format

    1. Top-level goal                         [in progress]
       2. Prerequisite                        [done → returns to 1]
       2. Second prerequisite                 [in progress]
          3. Its own prerequisite             [abandoned → see note]

- Number by depth, not sequence. Siblings share a number.
- Indentation carries the nesting. It is the part that makes depth visible.
- Status is one of: `pending`, `in progress`, `done`, `abandoned`.

## The rule that makes this work

**On descent, name what completing the layer returns you to.**
Write `[... → returns to N]` at the moment you descend, not afterward.

If you cannot say what a layer returns you to, the descent may not be
load-bearing — that is the check, and it is the whole reason to write
the arrow rather than just indent.

## Abandoning

A layer can be abandoned, not only completed. A prerequisite that turns
out to be someone else's problem, or not actually blocking, is popped
with `[abandoned]` and one line saying why.

Example from this project: a failing Cloudflare CI check was investigated,
found to be configured outside the repository with no in-repo fix
available, and downgraded to "owner checks the dashboard when convenient."
That is a legitimate pop, not a completion. Without a way to express it,
abandoned branches stay open forever and the chart stops being trusted.

## Depth

There is no maximum. The test is not how deep you are, it is whether the
top of the stack is still the thing you actually want. A chain that
descends far enough that its original goal has gone stale is the real
signal — depth is how you notice, not what is wrong.

## Where it lives

The active stack belongs in the current handoff document, since that is
already the session-continuity record and the stack is exactly the state
a new session needs. This file is the convention; the handoff carries the
instance.
