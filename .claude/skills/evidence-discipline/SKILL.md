---
name: evidence-discipline
description: How to make claims about a codebase so they can be checked — every claim carries a file and line, or is explicitly marked unverified, and read/observed/inferred are never conflated. Use this whenever you are investigating code, answering a question about how something works, writing a verification report, diagnosing a bug, or asserting that some behaviour does or does not exist. Use it even when the answer seems obvious, because the failure mode this prevents is confident prose that turns out to rest on a stale comment or a plausible assumption.
---

# Evidence discipline

A claim about code is worth exactly as much as the reader's ability to check it. "The temp file is deleted after capture" is unverifiable prose. "`deleteCapture` is called once, at `PhotoAcquisitionLaunchers.kt:86`, on the failure branch only" is a claim someone can open and confirm in ten seconds.

The cost of getting this wrong compounds. A stale doc comment that nobody challenged becomes an assumption, the assumption becomes a design decision, and the decision outlives everyone who could have questioned it. Most of the expensive bugs in a mature codebase are documented behaviour that stopped being true.

## Every claim carries its evidence

Name the file and the line. Not the file alone, not "in the camera code", not "somewhere in the persist path".

When you cannot name a line, say so in the same sentence as the claim. "Unverified" is a perfectly good word and it costs nothing. What costs something is a reader who cannot tell which of your claims you checked.

## Distinguish the three kinds of knowing

These get flattened into the same confident tone, and they should not be:

- **Read** — you opened the file and the line says this. The strongest kind for questions about what the code does.
- **Observed** — you ran it and watched it happen. The only kind that settles questions about what a device, a platform, or a library actually does at runtime.
- **Inferred** — you reasoned from things you read. Often correct, and never the same as observed.

A chain of reads with no gaps is a proof about code. It is not a proof about behaviour. Code that assigns a value and a platform that honours it are two different claims, and a library or an OEM sitting between them can make the second false while the first stays true.

State which kind each claim is when it matters. "Every hop delivers rotation 0, read from the source; whether the device honours it is unverified" tells the reader exactly where to point a device.

## Say when the task's premise is wrong

Tasks arrive with assumptions baked in. Some are wrong. When you find one, say so plainly and show the line that disproves it, rather than quietly building around it or answering the question that was meant instead of the one asked.

This is the highest-value thing in most reports. A task that says "confirm X reads the raw field" and gets back "it reads the getter, at line 144, and here is what the real divergence is" has saved more work than the fix itself.

Do it without hedging and without triumph. The premise was wrong, here is the line, here is what is actually true.

## Recall is not a source

Documentation you remember, API behaviour you are confident about, version numbers you are sure of — these are inferences wearing the clothes of reads. Fetch the source, run `javap` on the artifact, read the file. Then say you did.

When a report says "I read this from the sources jar fetched this session rather than from memory", that sentence is doing real work. It tells the reader the claim is checkable and was checked.

## What to write when you did not check

Three phrasings, each carrying different information:

- "Not verified: I did not read that file." — you could have, you did not.
- "Cannot be verified here: it needs a device." — the evidence does not exist in this environment.
- "Inferred from X and Y; not observed." — you have a reason, not a reading.

All three are better than silence, and all three are better than a confident sentence.

## Superseding, not overwriting

When a later finding contradicts an earlier recorded claim, preserve the withdrawn wording alongside the correction rather than editing it away. A record that says "this said WSL2; it is native Ubuntu, corrected on this date" teaches the next reader something. A record that silently says the right thing teaches them nothing and hides that the question was ever open.

This applies only to published claims. Something not yet committed can simply be edited; there is no published claim to withdraw.
