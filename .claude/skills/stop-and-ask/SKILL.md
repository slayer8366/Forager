---
name: stop-and-ask
description: How to recognise that a task contains an unmade decision rather than an ambiguity you can resolve, and how to surface the options without silently picking one. Use this whenever a task's premise turns out to be wrong, whenever the code has no path for what you were asked to do, whenever a fix would reverse an earlier decision, and whenever you find yourself about to choose between architectures. Use it especially when you can construct a coherent reading of the request, because a coherent reading is exactly what makes silent guessing feel safe.
---

# Stop and ask

Some tasks are underspecified in ways you can close yourself: a variable name, a log format, where a helper lives. Others contain a decision nobody has made, and the fact that you can infer a reasonable answer does not make it yours to make.

The distinguishing question is not "am I confident?" It is "if I choose wrong, does the work get thrown away or quietly build on a wrong foundation?" A wrong guess about formatting costs a line. A wrong guess about where a dependency enters the system costs every dispatch after it.

## What is a stop

- **The premise is wrong.** The task says a function reads the raw field; it reads the getter. Report it, show the line, stop before building on either reading.
- **The path does not exist.** You were asked to route X into Y and there is no route. How the route is made is an architectural decision, not an implementation detail.
- **The fix reverses an earlier decision.** Something was decided and recorded; your fix would undo it. That is the owner's call, with the reason for the original decision in hand.
- **Two designs both satisfy the request.** If they differ in what they cost later, the choice is not yours.
- **The request is self-contradictory.** Two constraints that cannot both hold. Say which two and what each would cost, rather than quietly satisfying the one you like.
- **Silencing or weakening a test.** Diagnosing a failure is the job. Deciding to reduce coverage is not, whether by ignore, by a wait, by a scroll, or by a config line that makes the failure not fire.

## What is not a stop

Do not stop for things you can check. If the question is answerable by reading the code, read the code. A stop that asks the owner something a grep would have settled wastes the same round trip it was meant to save.

Do not stop to confirm a decision already made. If the task says do X and X is clear, do X.

## How to surface options

Give two to four, each with what it costs, not just what it does. The reader is choosing between futures, and the costs are what distinguish them.

Name your recommendation and why, but do not bury the alternatives to make it look inevitable. When one option is genuinely worse, say so in one line rather than omitting it — the reader may know something you do not about why it was on the table.

Include the option you think is wrong if it is the one implied by the request. That is the case where the owner most needs to see the tradeoff stated.

State what you have already established, so the decision is made with the reconnaissance in hand rather than from scratch. A stop that arrives with file and line evidence for every option is worth far more than one that asks an abstract question.

## Reconnaissance first, then stop

Before stopping, find out what is actually true. The stop is more useful when it arrives with the constraints discovered: what exists, what does not, which seam is already established, what a release build would do.

Very often the reconnaissance changes the question. A task that looked like "add a log line" becomes "there is no path from this class to that store, and here are three ways to make one." The second question is the one worth asking.

## After the answer

Restate what you understood before building, in one or two lines. When the owner's answer differs from your recommendation, say so plainly and build what they chose. When their answer contains a correction to your reading, carry the correction forward rather than the reading.
