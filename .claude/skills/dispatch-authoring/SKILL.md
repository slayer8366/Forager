---
name: dispatch-authoring
description: How to write a task for a coding agent so it comes back with work you can trust — verify before building, constraints on what not to touch, the evidence required, and device-only items enumerated separately. Use this whenever you are about to hand off implementation work, whenever a fix rests on a diagnosis you have not confirmed, and whenever a previous dispatch came back with the right code built on a wrong premise. Use it especially when the task feels small, because small tasks are where the premise goes unchecked.
---

# Dispatch authoring

A dispatch is a task written to be executed without you in the room. Its job is to get back work that is both correct and checkable, which means specifying not only what to build but what to establish first, what not to disturb, and what evidence to return.

Most dispatches that go wrong do not go wrong in the code. They go wrong because a premise in the task was false and nobody checked it before building on it.

## Verify before build

Put the reconnaissance first, explicitly, and ask for it to be reported before any code changes.

State your diagnosis and ask for it to be confirmed or disproved rather than assumed. "Confirm this is the mechanism, or show me where it is wrong" is the single highest-value sentence in a dispatch. It has repeatedly come back with the premise corrected and the real cause named.

List the candidate explanations you have already considered, so the work is not redone, and so a wrong one you were leaning on gets challenged rather than inherited.

## Say what not to touch

An explicit do-not-touch list prevents scope creep and protects things confirmed working. Name the files, the functions, the decisions. "Do not change the sensor listener, the scrub, the sweep, or the retained flag" costs one line and saves a review.

Include decisions as well as code. If something was decided deliberately and would look wrong to a fresh reader, say it was decided and why, or it will be helpfully undone.

## Pre-empt the alternatives you have rejected

When you have already considered and rejected an approach, say so and say why. Otherwise it gets proposed, or built.

Invite disagreement explicitly: "if you think that reasoning is wrong, say why instead of implementing either." The point is to prevent silent substitution, not to close the question.

## Specify the evidence

Ask for what you will need to believe the work: tests that can fail, revert checks, suite counts before and after, the build result. Ask for what could not be verified to be stated plainly rather than omitted.

When a previous dispatch shipped something untested on a particular line, say so and ask for a test that can fail there. "A second untested fix on the same line is not acceptable" is a fair thing to write.

## Enumerate device-only items separately

Anything that needs hardware goes in its own list at the end, phrased as things a person can do: where to tap, what to look at, what each outcome would mean. This becomes the device check, so writing it here saves the work twice.

## One dispatch, one decision

If a task contains an unmade decision, resolve it before dispatching or ask for options first. A dispatch that silently contains a choice will have that choice made by whoever executes it, and the answer will be defensible and not yours.

When several changes go together but one supersedes another's cause, say so at the top: "read all three before building; the first two replace each other's cause." Otherwise they get built independently and the interaction is discovered afterwards.

## Keep the scope honest

A dispatch scoped to classify and record should say "do not fix." A dispatch scoped to investigate should say "report first, then stop."

Then respect it in review: work that stayed inside a narrow scope and reported something it deliberately did not fix has done the right thing, and treating that as incomplete teaches the opposite lesson.
