---
name: device-check-authoring
description: How to write a manual verification checklist that someone can actually follow on a device — ordered so cheap checks run first, with a named pass condition and named evidence per step, and observations marked as not gates. Use this whenever work can only be confirmed on real hardware, whenever a fix rests on an inference about a platform or OEM, and whenever you are about to write "needs a device" in a report. Use it even for a handful of steps, because an unordered list of vague checks produces vague results.
---

# Device check authoring

Some claims cannot be settled anywhere but on the hardware. A chain of reads through the code proves what the code does; it does not prove what a platform, an OEM layer, or a driver does with it. When that gap exists, the device check is the instrument, and it deserves the same care as a test.

The person running it may be away from a desk, working from a phone, with no debugger and no logs. Write for that.

## Order by cost, not by topic

Put the steps that need no instrument first. If the cheapest check fails, everything after it is wasted effort, and the tester finds out in two minutes instead of forty.

Then the steps needing an in-app surface. Then the ones needing a desktop tool or a file transfer. Group the steps that share setup — if three checks all need the camera open, do not scatter them across the document.

Say explicitly where a failure stops the run.

## Every step names its pass condition and its evidence

A step that says "check the photo looks right" produces an answer nobody can audit. A step that says "the orientation tag is present, no GPS tags, last two bytes are FF D9, paste the tag dump" produces a record.

Name what the tester should write down, including for steps that pass. Passes are data: a matrix row assembled from twelve steps is only as good as the steps that were boring.

## Mark observations that are not gates

Some steps exist to learn something, not to decide something. Whether a device's capture carries a trailer, what value the orientation tag actually takes, which extensions a vendor exposes — these are findings either way.

Say so in the step. "Either answer is acceptable; this is the open question from the report, not a pass condition" stops a tester treating an unexpected result as a failure, and stops them glossing over it as a pass.

When both branches of an outcome are informative, spell out what each would mean. A tester who knows that "rewritten confirms the mechanism, kept means the earlier result needs another explanation" cannot report it wrongly.

## Name the assumptions the check depends on

If the check requires a debug build, say so. If it requires a system setting in a particular state, say so and say how to set it. If some entries in an instrument are expected noise — framework logs, a panel's own known violation — list them before the step that reads that instrument, or the tester will file them as findings.

These assumptions accumulate silently. An implicit one that becomes load-bearing is how a check quietly stops meaning what it says.

## Write for the instrument that exists

A check that says "read logcat" is useless to someone without a debugger attached. If the readout the check depends on is not reachable on the device, the right move is to build the instrument first and say so, not to write a step nobody can run.

This is worth catching at authoring time. A fix whose only evidence lives somewhere the verifier cannot see is not verifiable.

## End with the row

The last section should assemble what was learned into a form the project can keep: device, OS version, the specific results that vary by hardware, and anything surprising, including on steps that passed.

Across devices this becomes a matrix, which is the only honest way to hold claims about behaviour that differs by vendor. A pass on one phone is evidence about that phone.

## Re-runs

When a build changes the path a check exercises, run from the top rather than resuming. A step that passed on the old build is evidence about the old build.

Say this in the document, so a tester picking it back up after a fix does not assume the earlier passes carry.
